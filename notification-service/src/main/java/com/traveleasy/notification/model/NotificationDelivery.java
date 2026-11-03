package com.traveleasy.notification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "notification_deliveries",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_delivery_ref", columnNames = {"delivery_ref"})
    },
    indexes = {
        @Index(name = "idx_deliveries_poller", columnList = "channel, status, next_retry_at, last_attempt_at, created_at"),
        @Index(name = "idx_deliveries_event", columnList = "event_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_ref", nullable = false, unique = true, length = 64)
    private String deliveryRef;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(nullable = false, length = 30)
    private String channel; // EMAIL

    @Column(name = "recipient_email", nullable = false, length = 150)
    private String recipientEmail;

    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", nullable = false, length = 50)
    private RecipientRole recipientRole;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(name = "template_data_json", nullable = false, length = 4000)
    private String templateDataJson;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING"; // PENDING, IN_FLIGHT, DELIVERED, FAILED

    @Column(name = "locked_by", length = 64)
    private String lockedBy;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String provider = "RESEND";

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 4;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;
}
