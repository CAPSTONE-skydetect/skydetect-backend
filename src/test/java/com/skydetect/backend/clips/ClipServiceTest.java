package com.skydetect.backend.clips;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClipServiceTest {
    @TempDir Path directory;
    private ClipRepository jobs;
    private ClipMaterializer worker;
    private ClipService clips;

    @BeforeEach
    void setUp() {
        jobs = mock(ClipRepository.class);
        worker = mock(ClipMaterializer.class);
        when(worker.enabled()).thenReturn(true);
        clips = new ClipService(jobs, worker, new ObjectMapper(), directory.toString());
    }

    @AfterEach
    void stop() {
        clips.stopWorkerThreads();
    }

    @Test
    void persistsPendingThenReadyWithWorkerMetadata() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        when(jobs.save(any(ClipJob.class))).thenAnswer(call -> {
            ClipJob job = call.getArgument(0);
            when(jobs.findByIdAndOwnerId(job.id, job.ownerId)).thenReturn(Optional.of(job));
            if (job.status == ClipJob.Status.READY) finished.countDown();
            return job;
        });
        when(worker.materialize(anyString(), anyLong())).thenAnswer(call -> {
            String id = call.getArgument(0);
            Files.write(directory.resolve(id + ".mp4"), new byte[]{1, 2, 3});
            return new ClipMaterializer.Media(new ClipMaterializer.Metadata(1920,1080,30,2,66),
                    List.of(0.0, 0.033), Map.of("requestedSpanSeconds", 13), "a".repeat(64), 3);
        });
        var accepted = clips.create(7, new ClipController.Observation(null, 4.0));
        assertEquals(ClipJob.Status.PENDING, accepted.status());
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        var ready = clips.get(accepted.clipId(), 7);
        assertEquals(ClipJob.Status.READY, ready.status());
        assertEquals(2, ready.meta().frameCount());
        assertEquals(3, clips.video(accepted.clipId(), 7).contentLength());
    }

    @Test
    void workerFailureIsSavedAsFailed() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        when(jobs.save(any(ClipJob.class))).thenAnswer(call -> {
            ClipJob job = call.getArgument(0);
            if (job.status == ClipJob.Status.FAILED) failed.countDown();
            return job;
        });
        when(worker.materialize(anyString(), anyLong())).thenThrow(new IOException("offline"));
        assertEquals(ClipJob.Status.PENDING, clips.create(7, new ClipController.Observation(null,null)).status());
        assertTrue(failed.await(5, TimeUnit.SECONDS));
    }

    @Test
    void clipOfAnotherOwnerIsNotExposed() {
        when(jobs.findByIdAndOwnerId("some-id", 8)).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> clips.video("some-id", 8));
        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(worker);
    }

    @Test
    void noWorkerConfiguredDoesNotCreateJob() {
        when(worker.enabled()).thenReturn(false);
        var error = assertThrows(ResponseStatusException.class,
                () -> clips.create(7, new ClipController.Observation(null,null)));
        assertEquals(503, error.getStatusCode().value());
        verifyNoInteractions(jobs);
    }

    @Test
    void restartMarksPendingJobsWithoutRetrying() {
        clips.markInterruptedJobs();
        verify(jobs).failInterrupted(eq(ClipJob.Status.PENDING),eq(ClipJob.Status.FAILED),anyString());
        verifyNoInteractions(worker);
    }
}
