package com.skydetect.backend.clips;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
class ClipMaterializer {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Metadata(int width, int height, double fps, int frameCount, long durationMs) {}
    record Media(Metadata metadata, List<Double> framePts, Map<String, Object> timing,
                 String sha256, long bytes) {
        boolean valid() {
            if (metadata == null || metadata.width() <= 0 || metadata.height() <= 0
                    || !Double.isFinite(metadata.fps()) || metadata.fps() <= 0
                    || metadata.durationMs() <= 0 || framePts == null || framePts.isEmpty()
                    || framePts.size() != metadata.frameCount() || bytes <= 0
                    || sha256 == null || !sha256.matches("[0-9a-f]{64}") || timing == null) {
                return false;
            }
            double previous = -1;
            for (Double timestamp : framePts) {
                if (timestamp == null || !Double.isFinite(timestamp) || timestamp < 0 || timestamp <= previous) {
                    return false;
                }
                previous = timestamp;
            }
            return true;
        }
    }

    private final String baseUrl;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    ClipMaterializer(@Value("${skydetect.clips.worker-url:}") String baseUrl, ObjectMapper json) {
        this.baseUrl = baseUrl.replaceFirst("/+$", "");
        this.json = json;
    }

    boolean enabled() {
        return !baseUrl.isBlank();
    }

    Media materialize(String clipId, long triggerEpochMs) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/clips/" + clipId))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(Map.of("triggerEpoch", triggerEpochMs / 1000.0))))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 503) {
            throw new IOException("Camera or clip worker is temporarily unavailable.");
        }
        if (response.statusCode() != 200) {
            throw new IOException("Recording extraction failed (worker HTTP " + response.statusCode() + ").");
        }
        Media result = json.readValue(response.body(), Media.class);
        if (result == null || !result.valid()) {
            throw new IOException("Invalid clip metadata returned by worker.");
        }
        return result;
    }
}
