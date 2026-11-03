package com.traveleasy.bus.publisher;

import com.traveleasy.bus.model.BusOutboxEvent;
import com.traveleasy.bus.repository.BusOutboxEventRepository;
import com.traveleasy.bus.service.BusInternalTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BusOutboxPublisher {

    private final BusOutboxEventRepository outboxRepository;
    private final BusInternalTokenService tokenService;

    private RestTemplate restTemplate = new RestTemplate();

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${notification.service.url:http://localhost:8086}")
    private String notificationServiceUrl = "http://localhost:8086";

    public void setNotificationServiceUrl(String notificationServiceUrl) {
        this.notificationServiceUrl = notificationServiceUrl;
    }

    @Value("${bus.outbox.publisher.enabled:true}")
    private boolean publisherEnabled = true;

    public void setPublisherEnabled(boolean publisherEnabled) {
        this.publisherEnabled = publisherEnabled;
    }

    @Value("${bus.outbox.publisher.batch-size:20}")
    private int batchSize = 20;

    @Value("${bus.outbox.publisher.lease-seconds:60}")
    private int leaseSeconds = 60;

    @Scheduled(fixedDelayString = "${bus.outbox.publisher.fixed-delay:2000}")
    public void publishOutboxEvents() {
        if (!publisherEnabled) {
            return;
        }

        String workerId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant leaseExpiry = now.plusSeconds(leaseSeconds);

        int claimedCount = outboxRepository.claimPendingEvents(workerId, now, leaseExpiry, batchSize);
        if (claimedCount == 0) {
            return;
        }

        List<BusOutboxEvent> claimedEvents = outboxRepository.findByLockedByAndStatus(workerId, "IN_FLIGHT");
        log.info("Bus Worker {} claimed {} outbox event(s) for dispatch", workerId, claimedEvents.size());

        for (BusOutboxEvent event : claimedEvents) {
            dispatchSingleEvent(event, workerId);
        }
    }

    public void dispatchSingleEvent(BusOutboxEvent event, String workerId) {
        String url = notificationServiceUrl + "/api/notifications/events";

        try {
            String internalToken = tokenService.generateInternalToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken);

            HttpEntity<String> entity = new HttpEntity<>(event.getPayloadJson(), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
            if (status == HttpStatus.ACCEPTED) {
                // HTTP 202: New event accepted by Notification Service
                outboxRepository.markPublished(event.getId(), "PUBLISHED", Instant.now(), workerId);
                log.info("Successfully published bus outbox event {} (HTTP 202 ACCEPTED)", event.getEventId());
            } else if (status == HttpStatus.OK) {
                // HTTP 200: Duplicate event already ingested
                outboxRepository.markPublished(event.getId(), "PUBLISHED", Instant.now(), workerId);
                log.info("Successfully published bus outbox event {} (HTTP 200 OK - ALREADY_INGESTED)", event.getEventId());
            } else {
                handleUnexpectedStatus(event, workerId, response.getStatusCode().value(), response.getBody());
            }

        } catch (HttpStatusCodeException hse) {
            handleHttpStatusException(event, workerId, hse);
        } catch (ResourceAccessException rae) {
            handleTransientFailure(event, workerId, "Network I/O error or timeout: " + rae.getMessage());
        } catch (Exception ex) {
            handleTransientFailure(event, workerId, "Unexpected dispatch error: " + ex.getMessage());
        }
    }

    private void handleHttpStatusException(BusOutboxEvent event, String workerId, HttpStatusCodeException hse) {
        int statusCode = hse.getStatusCode().value();
        String responseBody = hse.getResponseBodyAsString();

        if (statusCode == 400) {
            // Terminal validation failure: payload contract violated
            log.error("Terminal failure: Notification Service rejected bus event {} with HTTP 400 Bad Request: {}",
                    event.getEventId(), responseBody);
            outboxRepository.recordFailureAndUnlock(event.getId(), "FAILED", event.getRetryCount(), null,
                    "Terminal HTTP 400: " + responseBody, workerId);

        } else if (statusCode == 401 || statusCode == 403) {
            // Operational security failure: invalid secret or disallowed event type
            log.error("SECURITY FAILURE: Notification Service rejected bus event {} with HTTP {} {}. Response: {}",
                    event.getEventId(), statusCode, hse.getStatusText(), responseBody);
            outboxRepository.recordFailureAndUnlock(event.getId(), "FAILED", event.getRetryCount(), null,
                    "Security failure HTTP " + statusCode + ": " + responseBody, workerId);

        } else if (statusCode == 429 || statusCode >= 500) {
            // Transient rate limit or server error: retry with backoff
            handleTransientFailure(event, workerId, "HTTP " + statusCode + " error from Notification Service: " + responseBody);

        } else {
            handleUnexpectedStatus(event, workerId, statusCode, responseBody);
        }
    }

    private void handleUnexpectedStatus(BusOutboxEvent event, String workerId, int statusCode, String responseBody) {
        log.warn("Unexpected status code {} from Notification Service for bus event {}: {}",
                statusCode, event.getEventId(), responseBody);
        handleTransientFailure(event, workerId, "Unexpected HTTP " + statusCode + ": " + responseBody);
    }

    private void handleTransientFailure(BusOutboxEvent event, String workerId, String errorMessage) {
        int newRetryCount = event.getRetryCount() + 1;
        if (newRetryCount >= event.getMaxRetries()) {
            log.error("Bus outbox event {} exceeded maximum retries ({}). Marking as FAILED. Last error: {}",
                    event.getEventId(), event.getMaxRetries(), errorMessage);
            outboxRepository.recordFailureAndUnlock(event.getId(), "FAILED", newRetryCount, null,
                    "Max retries exceeded: " + truncate(errorMessage, 900), workerId);
        } else {
            long delaySec = (long) (10 * Math.pow(2, newRetryCount)) + (long) (Math.random() * 5);
            Instant nextRetryAt = Instant.now().plusSeconds(delaySec);
            log.warn("Bus outbox event {} failed (attempt {}/{}). Retrying at {}. Error: {}",
                    event.getEventId(), newRetryCount, event.getMaxRetries(), nextRetryAt, errorMessage);
            outboxRepository.recordFailureAndUnlock(event.getId(), "PENDING", newRetryCount, nextRetryAt,
                    truncate(errorMessage, 900), workerId);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
