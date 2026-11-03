package com.traveleasy.notification.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
    name = "in_app_notifications",
    indexes = {
        @Index(name = "idx_in_app_user_role_read", columnList = "recipient_user_id, recipient_role, is_read, created_at"),
        @Index(name = "idx_in_app_email_role", columnList = "recipient_email, recipient_role, created_at"),
        @Index(name = "idx_in_app_expires", columnList = "expires_at")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "recipient_email", nullable = false, length = 150)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", nullable = false, length = 50)
    private RecipientRole recipientRole;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "action_url", length = 255)
    private String actionUrl;

    @Column(name = "metadata_json", length = 2000)
    private String metadataJson;

    @JsonProperty("isRead")
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    @Builder.Default
    private Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
}
