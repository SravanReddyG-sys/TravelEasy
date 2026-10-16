package com.traveleasy.notification.controller;

import com.traveleasy.notification.model.NewsletterSubscription;
import com.traveleasy.notification.repository.NewsletterSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequestMapping({"/api/newsletter", "/api/notifications/newsletter"})
@RequiredArgsConstructor
public class NewsletterController {

    private final NewsletterSubscriptionRepository subscriptionRepository;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody(required = false) Map<String, String> request) {
        if (request == null || !request.containsKey("email")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "INVALID_EMAIL",
                    "message", "Please enter a valid email address."
            ));
        }

        String rawEmail = request.get("email");
        if (rawEmail == null || rawEmail.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "INVALID_EMAIL",
                    "message", "Please enter a valid email address."
            ));
        }

        String normalizedEmail = rawEmail.trim().toLowerCase();

        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches() || normalizedEmail.length() > 150) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "INVALID_EMAIL",
                    "message", "Please enter a valid email address."
            ));
        }

        Optional<NewsletterSubscription> existingOpt = subscriptionRepository.findByEmail(normalizedEmail);
        if (existingOpt.isPresent()) {
            NewsletterSubscription existing = existingOpt.get();
            if ("UNSUBSCRIBED".equalsIgnoreCase(existing.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "UNSUBSCRIBED",
                        "message", "This email was previously unsubscribed. Please contact support to reactivate your subscription.",
                        "email", normalizedEmail
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "status", "ALREADY_SUBSCRIBED",
                    "message", "This email is already subscribed.",
                    "email", normalizedEmail
            ));
        }

        NewsletterSubscription newSub = NewsletterSubscription.builder()
                .email(normalizedEmail)
                .status("ACTIVE")
                .subscribedAt(LocalDateTime.now())
                .build();

        try {
            NewsletterSubscription saved = subscriptionRepository.save(newSub);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "SUCCESS",
                    "message", "You're subscribed to Travel Easy updates!",
                    "email", saved.getEmail()
            ));
        } catch (DataIntegrityViolationException ex) {
            // Concurrent submission protection
            return ResponseEntity.ok(Map.of(
                    "status", "ALREADY_SUBSCRIBED",
                    "message", "This email is already subscribed.",
                    "email", normalizedEmail
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getSubscriptionStatus(@RequestParam String email) {
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email parameter is required."));
        }
        String normalized = email.trim().toLowerCase();
        return subscriptionRepository.findByEmail(normalized)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "status", "NOT_FOUND",
                        "message", "No subscription found for " + normalized
                )));
    }
}
