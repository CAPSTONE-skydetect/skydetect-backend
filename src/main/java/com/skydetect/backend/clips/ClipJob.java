package com.skydetect.backend.clips;

import jakarta.persistence.*;

@Entity
@Table(name = "clip_jobs", indexes = @Index(columnList = "owner_id,trigger_epoch_ms"))
class ClipJob {
    enum Status { PENDING, READY, FAILED }

    @Id
    @Column(length = 44)
    String id;

    @Column(name = "owner_id", nullable = false)
    long ownerId;

    @Column(name = "trigger_epoch_ms", nullable = false)
    long triggerEpochMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    Status status;

    Double clientClickEpochMs;
    Double displayedMediaTimeS;

    @Lob
    String mediaJson;

    @Column(length = 300)
    String error;

    protected ClipJob() {}

    ClipJob(String id, long ownerId, long triggerEpochMs, ClipController.Observation observation) {
        this.id = id;
        this.ownerId = ownerId;
        this.triggerEpochMs = triggerEpochMs;
        this.status = Status.PENDING;
        this.clientClickEpochMs = observation.clientClickEpochMs();
        this.displayedMediaTimeS = observation.displayedMediaTimeS();
    }
}
