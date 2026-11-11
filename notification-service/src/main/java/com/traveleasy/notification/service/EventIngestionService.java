package com.traveleasy.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.notification.dto.EventContentDto;
import com.traveleasy.notification.dto.EventIngestionResponse;
import com.traveleasy.notification.dto.NotificationEventRequest;
import com.traveleasy.notification.dto.RecipientDto;
import com.traveleasy.notification.model.InAppNotification;
import com.traveleasy.notification.model.NotificationDelivery;
import com.traveleasy.notification.model.ReceivedEvent;
import com.traveleasy.notification.repository.InAppNotificationRepository;
import com.traveleasy.notification.repository.NotificationDeliveryRepository;
import com.traveleasy.notification.repository.ReceivedEventRepository;
import com.traveleasy.notification.security.NotificationSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventIngestionService {

    private final ReceivedEventRepository receivedEventRepository;
    private final InAppNotificationRepository inAppNotificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final ObjectMapper objectMapper;

    public static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "BUS_BOOKING_CONFIRMED",
            "BUS_BOOKING_CANCELLED",
            "HOTEL_BOOKING_CANCELLED",
            "PAYMENT_FAILED",
            "PAYMENT_REFUND_INITIATED",
            "PAYMENT_REFUND_COMPLETED",
            "BUS_SCHEDULE_CREATED",
            "BUS_SCHEDULE_CANCELLED",
            "BUS_DEACTIVATED",
            "BUS_ACTIVATED"
    );

    public static final Set<String> SUPPORTED_CHANNELS = Set.of(
            "IN_APP",
            "EMAIL"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    /**
     * Validates incoming event request against approved schema, roles, channels, and security bounds.
     */
    public void validateRequest(NotificationEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("NotificationEventRequest cannot be null.");
        }
        if (request.getEventId() == null || request.getEventId().isBlank() || request.getEventId().length() > 64) {
            throw new IllegalArgumentException("eventId is required and must be between 1 and 64 characters.");
        }
        if (request.getEventType() == null || !SUPPORTED_EVENT_TYPES.contains(request.getEventType().trim())) {
            throw new IllegalArgumentException("Unsupported or missing eventType: " + request.getEventType() +
                    ". Supported types: " + SUPPORTED_EVENT_TYPES);
        }
        if (request.getAggregateType() == null || request.getAggregateType().isBlank() || request.getAggregateType().length() > 50) {
            throw new IllegalArgumentException("aggregateType is required and must be under 50 characters.");
        }
        if (request.getAggregateId() == null || request.getAggregateId().isBlank() || request.getAggregateId().length() > 100) {
            throw new IllegalArgumentException("aggregateId is required and must be under 100 characters.");
        }
        if (request.getRecipients() == null || request.getRecipients().isEmpty()) {
            throw new IllegalArgumentException("recipients list cannot be empty.");
        }

        for (RecipientDto recipient : request.getRecipients()) {
            if (recipient == null) {
                throw new IllegalArgumentException("Recipient entry cannot be null.");
            }
            if (recipient.getRole() == null) {
                throw new IllegalArgumentException("Recipient role cannot be null.");
            }
            if (recipient.getChannels() == null || recipient.getChannels().isEmpty()) {
                throw new IllegalArgumentException("Recipient channels list cannot be empty for role " + recipient.getRole());
            }

            for (String channel : recipient.getChannels()) {
                if (channel == null || !SUPPORTED_CHANNELS.contains(channel.trim().toUpperCase())) {
                    throw new IllegalArgumentException("Unsupported delivery channel: '" + channel +
                            "'. Supported channels are: " + SUPPORTED_CHANNELS);
                }
            }

            if (recipient.getChannels().contains("IN_APP")) {
                if (recipient.getUserId() == null || recipient.getUserId() <= 0) {
                    throw new IllegalArgumentException("Recipient userId must be a valid positive ID for IN_APP channel.");
                }
            }

            if (recipient.getChannels().contains("EMAIL")) {
                if (recipient.getEmail() == null || recipient.getEmail().isBlank() ||
                        !EMAIL_PATTERN.matcher(recipient.getEmail().trim().toLowerCase()).matches() ||
                        recipient.getEmail().length() > 150) {
                    throw new IllegalArgumentException("Valid email address is required for EMAIL channel.");
                }
            }
        }

        if (request.getContent() != null) {
            EventContentDto content = request.getContent();
            if (content.getTitle() != null && content.getTitle().length() > 255) {
                throw new IllegalArgumentException("Content title exceeds maximum length of 255 characters.");
            }
            if (content.getSummary() != null && content.getSummary().length() > 1000) {
                throw new IllegalArgumentException("Content summary exceeds maximum length of 1000 characters.");
            }
            if (content.getActionUrl() != null && !content.getActionUrl().isBlank()) {
                String actionUrl = content.getActionUrl().trim();
                if (!actionUrl.startsWith("/") || actionUrl.contains("://") || actionUrl.contains(" ") || actionUrl.length() > 255) {
                    throw new IllegalArgumentException("actionUrl must be a safe relative path starting with '/' without scheme or whitespace.");
                }
            }
        }
    }

    /**
     * Atomically ingests an incoming business event, records the event in received_events
     * for deduplication, persists in-app notifications, and enqueues external delivery tasks.
     *
     * @param request the validated event request
     * @param authenticatedSourceService the verified service identity from the JWT
     * @return EventIngestionResponse indicating whether the event was ingested or previously processed
     */
    @Transactional
    public EventIngestionResponse ingestEvent(NotificationEventRequest request, String authenticatedSourceService) {
        validateRequest(request);

        String eventId = request.getEventId().trim();

        // 1. Sequential Idempotency Fast-Path
        if (receivedEventRepository.existsByEventId(eventId)) {
            log.info("Idempotent event duplicate detected for eventId {}. Returning ALREADY_INGESTED.", eventId);
            return EventIngestionResponse.builder()
                    .status("ALREADY_INGESTED")
                    .eventId(eventId)
                    .message("Event has already been ingested and recorded.")
                    .ingestedAt(Instant.now())
                    .build();
        }

        // 2. Build Sanitized Payload JSON
        String sanitizedPayloadJson;
        try {
            sanitizedPayloadJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize sanitized event payload for eventId {}: {}", eventId, e.getMessage());
            sanitizedPayloadJson = "{}";
        }

        // 3. Persist Ingestion Record using saveAndFlush to trigger unique constraint check immediately
        ReceivedEvent receivedEvent = ReceivedEvent.builder()
                .eventId(eventId)
                .sourceService(authenticatedSourceService)
                .eventType(request.getEventType().trim())
                .aggregateId(request.getAggregateId() != null ? request.getAggregateId().trim() : "")
                .sanitizedPayloadJson(sanitizedPayloadJson)
                .status("INGESTED")
                .receivedAt(Instant.now())
                .build();
        receivedEventRepository.saveAndFlush(receivedEvent);

        EventContentDto content = request.getContent() != null ? request.getContent() : new EventContentDto();
        String title = content.getTitle() != null ? content.getTitle() : "TravelEasy Notification";
        String summary = content.getSummary() != null ? content.getSummary() : "";
        String actionUrl = content.getActionUrl() != null ? content.getActionUrl().trim() : null;
        String templateName = content.getTemplate() != null ? content.getTemplate() : "default";

        String templateDataJson;
        try {
            templateDataJson = objectMapper.writeValueAsString(content.getTemplateData());
        } catch (JsonProcessingException e) {
            templateDataJson = "{}";
        }

        // 4. Process Recipient Actions
        for (RecipientDto recipient : request.getRecipients()) {
            // In-App Notification Creation
            if (recipient.getChannels().contains("IN_APP")) {
                InAppNotification inApp = InAppNotification.builder()
                        .eventId(eventId)
                        .recipientUserId(recipient.getUserId())
                        .recipientEmail(recipient.getEmail() != null ? recipient.getEmail().trim().toLowerCase() : "")
                        .recipientRole(recipient.getRole())
                        .eventType(request.getEventType())
                        .title(title)
                        .message(summary)
                        .actionUrl(actionUrl)
                        .metadataJson(templateDataJson)
                        .isRead(false)
                        .createdAt(Instant.now())
                        .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                        .build();
                inAppNotificationRepository.save(inApp);
                log.info("Created IN_APP notification for recipient {} (role: {}) for eventId {}",
                        recipient.getUserId(), recipient.getRole(), eventId);
            }

            // External Channel (EMAIL) Delivery Task Creation
            if (recipient.getChannels().contains("EMAIL")) {
                NotificationDelivery delivery = NotificationDelivery.builder()
                        .deliveryRef(UUID.randomUUID().toString())
                        .eventId(eventId)
                        .channel("EMAIL")
                        .recipientEmail(recipient.getEmail().trim().toLowerCase())
                        .recipientUserId(recipient.getUserId())
                        .recipientRole(recipient.getRole())
                        .subject(title)
                        .templateName(templateName)
                        .templateDataJson(templateDataJson)
                        .status("PENDING")
                        .provider("RESEND")
                        .retryCount(0)
                        .maxRetries(4)
                        .createdAt(Instant.now())
                        .build();
                notificationDeliveryRepository.save(delivery);
                log.info("Enqueued EMAIL delivery task (ref: {}) for recipient {} for eventId {}",
                        delivery.getDeliveryRef(), NotificationSecurityService.maskEmail(recipient.getEmail()), eventId);
            }
        }

        return EventIngestionResponse.builder()
                .status("INGESTED")
                .eventId(eventId)
                .message("Event successfully ingested and queued for delivery.")
                .ingestedAt(Instant.now())
                .build();
    }
}
