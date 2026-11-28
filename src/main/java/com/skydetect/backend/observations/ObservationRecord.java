package com.skydetect.backend.observations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "observation_records", indexes = @Index(name = "idx_observation_owner_created", columnList = "owner_id,created_at"))
class ObservationRecord {
    protected ObservationRecord() {}

    @Id
    @Column(length = 140)
    String id;

    @Column(name = "owner_id", nullable = false)
    Long ownerId;

    @Column(nullable = false, length = 100)
    String clientId;

    @Column(nullable = false, length = 200)
    String location;

    @Column(nullable = false, length = 2000)
    String memo;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    String analysisJson;

    @Column(name = "created_at", nullable = false)
    long createdAt;

    @Column(nullable = false)
    long savedAt;

    @Version
    Long version;
}
