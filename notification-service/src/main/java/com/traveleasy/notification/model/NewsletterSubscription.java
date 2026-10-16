package com.traveleasy.notification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "newsletter_subscriptions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_newsletter_email", columnNames = {"email"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsletterSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, UNSUBSCRIBED

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime subscribedAt = LocalDateTime.now();

    private LocalDateTime unsubscribedAt;
}
