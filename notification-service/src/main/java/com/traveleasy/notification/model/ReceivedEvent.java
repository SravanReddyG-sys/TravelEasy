package com.traveleasy.notification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "received_events",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_received_event_id", columnNames = {"event_id"})
    },
    indexes = {
        @Index(name = "idx_received_events_type", columnList = "event_type, received_at")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "source_service", nullable = false, length = 50)
    private String sourceService;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "sanitized_payload_json", nullable = false, length = 4000)
    private String sanitizedPayloadJson;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "INGESTED";

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();
}
