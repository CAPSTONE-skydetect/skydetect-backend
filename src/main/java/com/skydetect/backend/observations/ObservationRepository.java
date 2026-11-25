package com.skydetect.backend.observations;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface ObservationRepository extends JpaRepository<ObservationRecord, String> {
    List<ObservationRecord> findByOwnerIdOrderByCreatedAtDesc(long ownerId);
    void deleteByOwnerId(long ownerId);
}
