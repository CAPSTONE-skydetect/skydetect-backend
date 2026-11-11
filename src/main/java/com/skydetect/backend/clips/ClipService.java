package com.skydetect.backend.clips;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.RejectedExecutionException;

@Service
class ClipService {
    record VideoMeta(String videoUrl, int width, int height, double fps, int frameCount,
                     long durationMs, List<Double> framePts) {}
    record View(String clipId, ClipJob.Status status, Instant triggeredAt, Instant readyAt,
                long serverReceivedEpochMs, Double clientClickEpochMs, Double displayedMediaTimeS,
                String videoUrl, VideoMeta meta, Object timing, String error) {}

    private final ClipRepository jobs;
    private final ClipMaterializer worker;
    private final ObjectMapper json;
    private final Path clips;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Semaphore slots = new Semaphore(2);

    ClipService(ClipRepository jobs, ClipMaterializer worker, ObjectMapper json,
                @Value("${skydetect.clips.directory:/pi-clips}") String directory) {
        this.jobs = jobs;
        this.worker = worker;
        this.json = json;
        this.clips = Path.of(directory);
    }

    @PostConstruct
    void markInterruptedJobs() {
        jobs.failInterrupted(ClipJob.Status.PENDING, ClipJob.Status.FAILED,
                "Clip interrupted by backend restart. Start a new capture.");
    }

    @PreDestroy
    void stopWorkerThreads() {
        executor.shutdownNow();
    }

    View create(long ownerId, ClipController.Observation observation) {
        if (!worker.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Clip worker is not configured.");
        }
        if (!slots.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Clip workers are busy.");
        }
        ClipJob job = new ClipJob("spring_clip_" + UUID.randomUUID().toString().replace("-", ""),
                ownerId, System.currentTimeMillis(), observation);
        try {
            jobs.save(job);
        } catch (RuntimeException e) {
            slots.release();
            throw e;
        }
        View accepted = view(job);
        try {
            executor.execute(() -> finish(job));
        } catch (RejectedExecutionException e) {
            slots.release();
            fail(job, "Clip worker stopped before accepting this job.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Clip worker is stopping.");
        }
        return accepted;
    }

    private void finish(ClipJob job) {
        try {
            ClipMaterializer.Media media = worker.materialize(job.id, job.triggerEpochMs);
            if (Files.size(videoPath(job.id)) != media.bytes()) {
                throw new IOException("Clip file size does not match worker result.");
            }
            job.mediaJson = json.writeValueAsString(media);
            job.status = ClipJob.Status.READY;
            jobs.save(job);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(job, "Clip job interrupted.");
        } catch (IOException e) {
            fail(job, "Clip could not be saved. Check the camera and try again.");
        } catch (RuntimeException e) {
            fail(job, "Clip job failed.");
        } finally {
            slots.release();
        }
    }

    private void fail(ClipJob job, String reason) {
        job.status = ClipJob.Status.FAILED;
        job.mediaJson = null;
        job.error = reason;
        jobs.save(job);
    }

    View get(String id, long ownerId) {
        return view(owned(id, ownerId));
    }

    List<View> list(long ownerId) {
        return jobs.findTop100ByOwnerIdOrderByTriggerEpochMsDesc(ownerId).stream().map(this::view).toList();
    }

    Resource video(String id, long ownerId) {
        ClipJob job = owned(id, ownerId);
        if (job.status != ClipJob.Status.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Clip is not ready.");
        }
        try {
            return new FileSystemResource(videoPath(job.id));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Clip file not found.");
        }
    }

    private ClipJob owned(String id, long ownerId) {
        return jobs.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clip not found."));
    }

    private Path videoPath(String id) throws IOException {
        if (!id.matches("spring_clip_[0-9a-f]{32}")) {
            throw new IOException("Invalid clip ID.");
        }
        Path directory = clips.toRealPath();
        Path file = directory.resolve(id + ".mp4");
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new IOException("Clip file not found.");
        }
        return file;
    }

    private View view(ClipJob job) {
        Instant received = Instant.ofEpochMilli(job.triggerEpochMs);
        ClipMaterializer.Media media = job.mediaJson == null ? null :
                json.readValue(job.mediaJson, ClipMaterializer.Media.class);
        String url = media == null ? null : "/api/clips/" + job.id + "/video";
        VideoMeta meta = media == null ? null : new VideoMeta(url, media.metadata().width(),
                media.metadata().height(), media.metadata().fps(), media.metadata().frameCount(),
                media.metadata().durationMs(), media.framePts());
        return new View(job.id, job.status, received, received.plusSeconds(23), job.triggerEpochMs,
                job.clientClickEpochMs, job.displayedMediaTimeS, url, meta,
                media == null ? null : media.timing(), job.error);
    }
}
