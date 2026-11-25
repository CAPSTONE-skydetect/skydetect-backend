package com.skydetect.backend.observations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ObservationServiceTest {
    private ObservationRepository repository;
    private ObservationService service;
    private final Map<String, ObservationRecord> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        repository = mock(ObservationRepository.class);
        when(repository.findById(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(repository.save(any())).thenAnswer(call -> {
            ObservationRecord record = call.getArgument(0);
            stored.put(record.id, record);
            return record;
        });
        service = new ObservationService(repository, new ObjectMapper());
    }

    private ObservationController.Request request(String location, String memo) {
        return new ObservationController.Request(location, memo,
                Map.of("label", "drone", "source", "upload", "createdAt", 1000L,
                        "windowScores", List.of(4, 2, -1)));
    }

    @Test
    void savesAnalysisWithNotesAndUpdatesSameRecordOnRetry() {
        var first = service.save(7, "h_one", request("  옥상  ", "북쪽\n이동"));
        assertEquals("옥상", first.get("observationLocation"));
        assertEquals("북쪽\n이동", first.get("observationMemo"));
        assertEquals(List.of(4, 2, -1), first.get("windowScores"));
        assertEquals(true, first.get("serverSaved"));
        assertEquals(1000L, first.get("createdAt"));
        service.save(7, "h_one", request("정문", "수정"));
        assertEquals(1, stored.size());
        assertEquals("정문", stored.get("7_h_one").location);
    }

    @Test
    void sameClientIdBelongsToSeparateOwnersAndDeletionIsScoped() {
        service.save(7, "h_one", request("옥상", ""));
        service.save(8, "h_one", request("정문", ""));
        assertEquals(2, stored.size());
        service.delete(9, "h_one");
        verify(repository, never()).delete(any(ObservationRecord.class));
        service.delete(7, "h_one");
        verify(repository).delete(stored.get("7_h_one"));
        service.list(8);
        verify(repository).findByOwnerIdOrderByCreatedAtDesc(8);
        service.clear(8);
        verify(repository).deleteByOwnerId(8);
    }

    @Test
    void rejectsMalformedOrOversizedSnapshotsAndSupportsOptionalNotes() {
        assertThrows(ResponseStatusException.class, () -> service.save(7, "bad/id", request("", "")));
        assertThrows(ResponseStatusException.class, () -> service.save(7, "h_one",
                new ObservationController.Request("", "", Map.of())));
        assertThrows(ResponseStatusException.class, () -> service.save(7, "h_one",
                new ObservationController.Request("", "", Map.of("label", "bird", "source", "live", "thumbnail", "x".repeat(1_000_001)))));
        var saved = service.save(7, "h_one", request(null, null));
        assertEquals("", saved.get("observationLocation"));
        assertEquals("", saved.get("observationMemo"));
    }
}
