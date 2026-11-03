package com.traveleasy.notification.controller;

import com.traveleasy.notification.model.InAppNotification;
import com.traveleasy.notification.model.RecipientRole;
import com.traveleasy.notification.repository.InAppNotificationRepository;
import com.traveleasy.notification.security.NotificationSecurityService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/notifications/in-app")
@RequiredArgsConstructor
public class InAppNotificationController {

    private final InAppNotificationRepository inAppRepository;
    private final NotificationSecurityService securityService;

    @GetMapping
    public ResponseEntity<?> getUserNotifications(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        Claims claims;
        try {
            claims = securityService.validateUserToken(authHeader);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "UNAUTHORIZED",
                    "message", se.getMessage()
            ));
        }

        Long userId = securityService.extractUserId(claims);
        RecipientRole role = securityService.extractRole(claims);

        if (userId == null || role == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "status", "FORBIDDEN",
                    "message", "Token must contain a valid userId and recognized user role."
            ));
        }

        List<InAppNotification> notifications = inAppRepository
                .findByRecipientUserIdAndRecipientRoleOrderByCreatedAtDesc(userId, role);

        if (notifications.size() > limit) {
            notifications = notifications.subList(0, limit);
        }

        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Claims claims;
        try {
            claims = securityService.validateUserToken(authHeader);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "UNAUTHORIZED",
                    "message", se.getMessage()
            ));
        }

        Long userId = securityService.extractUserId(claims);
        RecipientRole role = securityService.extractRole(claims);

        if (userId == null || role == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "status", "FORBIDDEN",
                    "message", "Token must contain a valid userId and recognized user role."
            ));
        }

        long unreadCount = inAppRepository.countByRecipientUserIdAndRecipientRoleAndIsReadFalse(userId, role);
        return ResponseEntity.ok(Map.of("unreadCount", unreadCount));
    }

    @PutMapping("/{id}/read")
    @Transactional
    public ResponseEntity<?> markAsRead(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Claims claims;
        try {
            claims = securityService.validateUserToken(authHeader);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "UNAUTHORIZED",
                    "message", se.getMessage()
            ));
        }

        Long userId = securityService.extractUserId(claims);
        RecipientRole role = securityService.extractRole(claims);

        if (userId == null || role == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "status", "FORBIDDEN",
                    "message", "Token must contain a valid userId and recognized user role."
            ));
        }

        Optional<InAppNotification> notifOpt = inAppRepository.findById(id);
        if (notifOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", "Notification not found with ID " + id
            ));
        }

        InAppNotification notif = notifOpt.get();

        // Enforce Recipient Ownership
        if (!userId.equals(notif.getRecipientUserId()) || role != notif.getRecipientRole()) {
            log.warn("Ownership violation: User {} with role {} attempted to read notification {} owned by user {} with role {}",
                    userId, role, id, notif.getRecipientUserId(), notif.getRecipientRole());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "status", "FORBIDDEN",
                    "message", "You are not authorized to access or modify this notification."
            ));
        }

        if (!notif.isRead()) {
            notif.setRead(true);
            notif.setReadAt(Instant.now());
            inAppRepository.save(notif);
        }

        return ResponseEntity.ok(notif);
    }

    @PutMapping("/read-all")
    @Transactional
    public ResponseEntity<?> markAllAsRead(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        Claims claims;
        try {
            claims = securityService.validateUserToken(authHeader);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "UNAUTHORIZED",
                    "message", se.getMessage()
            ));
        }

        Long userId = securityService.extractUserId(claims);
        RecipientRole role = securityService.extractRole(claims);

        if (userId == null || role == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "status", "FORBIDDEN",
                    "message", "Token must contain a valid userId and recognized user role."
            ));
        }

        int updated = inAppRepository.markAllAsReadForUser(userId, role, Instant.now());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "markedRead", updated
        ));
    }
}
