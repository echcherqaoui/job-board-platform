package com.echcherqaoui.jobboard.commonoutbox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
      @Index(name = "idx_outbox_event_aggregate", columnList = "aggregate_type, aggregate_id"),
})
@IdClass(OutboxEventId.class)
@Getter
@Setter
@Accessors(chain = true)
public class OutboxEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(nullable = false, updatable = false, columnDefinition = "BYTEA")
    private byte[] payload;

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;
}