package com.skydetect.backend.clips;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

interface ClipRepository extends JpaRepository<ClipJob, String> {
    Optional<ClipJob> findByIdAndOwnerId(String id, long ownerId);
    List<ClipJob> findTop100ByOwnerIdOrderByTriggerEpochMsDesc(long ownerId);

    @Transactional
    @Modifying
    @Query("update ClipJob c set c.status = :failed, c.error = :reason where c.status = :pending")
    void failInterrupted(ClipJob.Status pending, ClipJob.Status failed, String reason);
}
