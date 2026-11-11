package com.traveleasy.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.notification.model.NotificationLog;
import com.traveleasy.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class NotificationControllerLegacyRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void testLegacySendNotification_DefaultChannelEmail_StatusSent_Success() throws Exception {
        String testEmail = "legacy_" + UUID.randomUUID() + "@traveleasy.com";

        NotificationLog log = NotificationLog.builder()
                .recipientEmail(testEmail)
                .subject("Booking Confirmed: Legacy Flow")
                .message("Your booking has been recorded via legacy notification endpoint.")
                .channel(null) // Should default to EMAIL
                .build();

        mockMvc.perform(post("/api/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(log)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientEmail").value(testEmail))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.id").exists());

        // Verify in repository
        var logs = notificationRepository.findByRecipientEmailOrderByTimestampDesc(testEmail);
        assertEquals(1, logs.size());
        assertEquals("SENT", logs.get(0).getStatus());
        assertEquals("EMAIL", logs.get(0).getChannel());
    }

    @Test
    void testLegacyGetUserNotifications_ReturnsOrderedList() throws Exception {
        String testEmail = "history_" + UUID.randomUUID() + "@traveleasy.com";

        NotificationLog log1 = NotificationLog.builder()
                .recipientEmail(testEmail)
                .subject("First Notification")
                .message("Message 1")
                .channel("EMAIL")
                .status("SENT")
                .timestamp(LocalDateTime.now().minusHours(2))
                .build();
        notificationRepository.save(log1);

        NotificationLog log2 = NotificationLog.builder()
                .recipientEmail(testEmail)
                .subject("Second Notification")
                .message("Message 2")
                .channel("EMAIL")
                .status("SENT")
                .timestamp(LocalDateTime.now().minusHours(1))
                .build();
        notificationRepository.save(log2);

        mockMvc.perform(get("/api/notifications/user/" + testEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].subject").value("Second Notification"))
                .andExpect(jsonPath("$[1].subject").value("First Notification"));
    }
}
