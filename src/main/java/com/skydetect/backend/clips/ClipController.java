package com.skydetect.backend.clips;

import com.skydetect.backend.auth.UserPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clips")
public class ClipController {
    public record Observation(Double clientClickEpochMs, Double displayedMediaTimeS) {
        public Observation {
            if ((clientClickEpochMs != null && !Double.isFinite(clientClickEpochMs))
                    || (displayedMediaTimeS != null &&
                    (!Double.isFinite(displayedMediaTimeS) || displayedMediaTimeS < 0))) {
                throw new IllegalArgumentException("Invalid clip observation.");
            }
        }
    }

    private final ClipService clips;

    ClipController(ClipService clips) {
        this.clips = clips;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    ClipService.View create(@AuthenticationPrincipal UserPrincipal user,
                            @RequestBody(required = false) Observation observation) {
        return clips.create(user.getId(), observation == null ? new Observation(null, null) : observation);
    }

    @GetMapping
    Map<String, List<ClipService.View>> list(@AuthenticationPrincipal UserPrincipal user) {
        return Map.of("clips", clips.list(user.getId()));
    }

    @GetMapping("/{id}")
    ClipService.View get(@AuthenticationPrincipal UserPrincipal user, @PathVariable String id) {
        return clips.get(id, user.getId());
    }

    @GetMapping("/{id}/video")
    ResponseEntity<Resource> video(@AuthenticationPrincipal UserPrincipal user, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .contentType(MediaType.parseMediaType("video/mp4")).body(clips.video(id, user.getId()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> requestError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }
}
