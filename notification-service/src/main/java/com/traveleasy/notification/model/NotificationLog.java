package com.traveleasy.notification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String recipientEmail;
    private String subject;
    @Column(length = 2000)
    private String message;
    private String channel; // EMAIL, SMS, IN_APP
    private String status; // SENT, QUEUED, FAILED

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
