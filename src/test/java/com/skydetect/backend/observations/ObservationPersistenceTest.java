package com.skydetect.backend.observations;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ObservationPersistenceTest {
    @Autowired ObservationService service;
    @Autowired EntityManager entities;

    @Test
    void roundTripsUnicodeNotesAndScoresAcrossPersistenceContexts() {
        var request = new ObservationController.Request("본관 옥상", "북쪽 방향\n관측", Map.of(
                "source", "upload", "label", "drone", "createdAt", 1000,
                "windowScores", List.of(4, 2, -1)));
        service.save(991, "h_persistence", request);
        entities.flush();
        entities.clear();
        var saved = service.list(991).getFirst();
        assertEquals("본관 옥상", saved.get("observationLocation"));
        assertEquals("북쪽 방향\n관측", saved.get("observationMemo"));
        assertEquals(List.of(4, 2, -1), saved.get("windowScores"));
        assertTrue(service.list(992).isEmpty());
        service.save(991, "h_persistence", new ObservationController.Request("정문", "수정", request.analysis()));
        entities.flush();
        entities.clear();
        assertEquals(1, service.list(991).size());
        assertEquals("정문", service.list(991).getFirst().get("observationLocation"));
        service.delete(992, "h_persistence");
        assertEquals(1, service.list(991).size());
        service.delete(991, "h_persistence");
        entities.flush();
        assertTrue(service.list(991).isEmpty());
    }
}
