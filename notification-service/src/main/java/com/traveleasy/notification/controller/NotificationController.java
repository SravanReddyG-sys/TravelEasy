package com.traveleasy.notification.controller;

import com.traveleasy.notification.model.NotificationLog;
import com.traveleasy.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @PostMapping("/send")
    public ResponseEntity<NotificationLog> sendNotification(@RequestBody NotificationLog notification) {
        if (notification.getChannel() == null) {
            notification.setChannel("EMAIL");
        }
        notification.setStatus("SENT");
        NotificationLog saved = notificationRepository.save(notification);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/user/{email}")
    public ResponseEntity<List<NotificationLog>> getUserNotifications(@PathVariable String email) {
        return ResponseEntity.ok(notificationRepository.findByRecipientEmailOrderByTimestampDesc(email));
    }
}
