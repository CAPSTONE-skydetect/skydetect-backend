package com.skydetect.backend.observations;

import com.skydetect.backend.auth.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/observations")
public class ObservationController {
    public record Request(@Size(max = 200) String location, @Size(max = 2000) String memo,
                          @NotNull @Size(max = 100) Map<String, Object> analysis) {}

    private final ObservationService observations;

    ObservationController(ObservationService observations) {
        this.observations = observations;
    }

    @PutMapping("/{id}")
    Map<String, Object> save(@AuthenticationPrincipal UserPrincipal user, @PathVariable String id,
                            @Valid @RequestBody Request request) {
        return observations.save(user.getId(), id, request);
    }

    @GetMapping
    List<Map<String, Object>> list(@AuthenticationPrincipal UserPrincipal user) {
        return observations.list(user.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable String id) {
        observations.delete(user.getId(), id);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clear(@AuthenticationPrincipal UserPrincipal user) {
        observations.clear(user.getId());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> requestError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<Map<String, String>> conflictingSave() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "다른 저장 요청과 겹쳤습니다. 다시 저장해 주세요."));
    }
}
