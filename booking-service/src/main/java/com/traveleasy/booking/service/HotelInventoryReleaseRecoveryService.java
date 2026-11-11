package com.traveleasy.booking.service;

import com.traveleasy.booking.model.HotelInventoryReleaseTask;
import com.traveleasy.booking.repository.HotelInventoryReleaseTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordinates immediate and background durable hotel inventory release against hotel-service (WP-HC-01).
 * Uses atomic task leasing so concurrent cancel retries or background recovery sweeps never duplicate releases
 * or fall back to legacy count-based release for date-wise bookings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotelInventoryReleaseRecoveryService {

    private static final Pattern ROOMS_COUNT_PATTERN = Pattern.compile("Rooms?:\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_ROOMS_COUNT_PATTERN = Pattern.compile("\"(?:roomCount|rooms|numberOfRooms|quantity)\"\\s*:\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);

    private final HotelInventoryReleaseTaskRepository releaseTaskRepository;
    private final HotelCancellationStateService stateService;
    private final BookingInternalTokenService internalTokenService;

    @Value("${hotel.service.url:http://localhost:8083}")
    private String hotelServiceUrl;

    @Value("${booking.hotel.inventory-release.reconciliation.lease-seconds:60}")
    private int leaseSeconds = 60;

    private RestTemplate restTemplate = buildTimeoutRestTemplate();

    private static RestTemplate buildTimeoutRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Attempts an immediate release right after cancellation transition, using a unique claimer ID.
     */
    public boolean attemptImmediateRelease(HotelInventoryReleaseTask task) {
        if (task == null || task.getId() == null) {
            return false;
        }
        String claimer = "immediate-" + UUID.randomUUID().toString().substring(0, 8);
        return claimAndExecute(task.getId(), claimer, leaseSeconds);
    }

    /**
     * Triggered during idempotent cancel replay when inventory release is still pending or retryable.
     */
    public boolean retryIfPending(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) {
            return false;
        }
        Optional<HotelInventoryReleaseTask> optTask = releaseTaskRepository.findByBookingRef(bookingRef);
        if (optTask.isEmpty()) {
            return false;
        }
        HotelInventoryReleaseTask task = optTask.get();
        if (HotelCancellationStateService.RELEASE_RELEASED.equalsIgnoreCase(task.getStatus())) {
            return true;
        }
        String claimer = "replay-" + UUID.randomUUID().toString().substring(0, 8);
        return claimAndExecute(task.getId(), claimer, leaseSeconds);
    }

    public boolean claimAndExecute(Long taskId, String workerId, int leaseDurationSeconds) {
        Instant now = Instant.now();
        Instant leaseExpiry = now.plusSeconds(Math.max(10, leaseDurationSeconds));
        int claimed = releaseTaskRepository.claimReleaseTask(taskId, workerId, now, leaseExpiry);
        if (claimed == 0) {
            log.debug("Inventory release task {} already leased or terminal; skipping duplicate attempt", taskId);
            return false;
        }

        HotelInventoryReleaseTask task = releaseTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return false;
        }
        return executeClaimedRelease(task);
    }

    public boolean executeClaimedRelease(HotelInventoryReleaseTask task) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String internalToken = internalTokenService.generateInternalToken();
            headers.set("Authorization", "Bearer " + internalToken);
            headers.set("X-Internal-Token", internalToken);
            headers.set("X-Internal-Service", BookingInternalTokenService.SERVICE_NAME);

            ResponseEntity<Map> resp;
            if (task.isDateWiseBooking()) {
                if (task.getBookingRef() == null || task.getBookingRef().isBlank()) {
                    handleFailure(task, "Date-wise hotel booking missing bookingRef for inventory release", false);
                    return false;
                }
                String stayUrl = hotelServiceUrl + "/api/hotels/rooms/release-stay?bookingRef=" + task.getBookingRef();
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                resp = restTemplate.exchange(stayUrl, HttpMethod.POST, entity, Map.class);
            } else {
                // Legacy non-date-wise fallback ONLY when booking was genuinely legacy
                if (task.getServiceId() == null) {
                    handleFailure(task, "Legacy hotel booking missing serviceId for inventory release", false);
                    return false;
                }
                int count;
                try {
                    count = extractLegacyRoomCount(task.getDetailsJson());
                } catch (IllegalArgumentException metaEx) {
                    log.error("Refusing unsafe legacy room release for booking {} due to invalid/missing room count metadata: {}",
                            task.getBookingRef(), metaEx.getMessage());
                    handleFailure(task, metaEx.getMessage(), false);
                    return false;
                }
                String url = hotelServiceUrl + "/api/hotels/rooms/" + task.getServiceId() + "/release?count=" + count;
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                resp = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            }

            if (resp != null && resp.getStatusCode().is2xxSuccessful()) {
                stateService.recordInventoryReleaseSucceeded(task.getBookingId(), task.getBookingRef(), task.getId());
                log.info("Released hotel inventory for booking {} (taskId={})", task.getBookingRef(), task.getId());
                return true;
            }

            handleFailure(task, "Hotel service returned non-2xx status: " + (resp != null ? resp.getStatusCode() : "null"), true);
            return false;

        } catch (HttpClientErrorException clientEx) {
            int rawStatus = clientEx.getStatusCode().value();
            boolean transient4xx = rawStatus == 408 || rawStatus == 429;
            log.warn("Hotel service 4xx ({}) releasing inventory for booking {}: {}",
                    rawStatus, task.getBookingRef(), clientEx.getMessage());
            handleFailure(task, "HTTP " + rawStatus + ": " + clientEx.getMessage(), transient4xx);
            return false;

        } catch (Exception ex) {
            log.warn("Transient failure releasing hotel inventory for booking {}: {}",
                    task.getBookingRef(), ex.getMessage());
            handleFailure(task, ex.getMessage(), true);
            return false;
        }
    }

    private void handleFailure(HotelInventoryReleaseTask task, String errorMessage, boolean retryable) {
        int currentAttempts = task.getAttemptCount() != null ? task.getAttemptCount() : 1;
        int maxAttempts = task.getMaxAttempts() != null ? task.getMaxAttempts() : 10;

        boolean canRetry = retryable && currentAttempts < maxAttempts;
        String nextStatus = canRetry
                ? HotelCancellationStateService.RELEASE_FAILED_RETRYABLE
                : HotelCancellationStateService.RELEASE_FAILED_FINAL;

        long backoffSeconds = Math.min(600L, 15L * (1L << Math.min(6, Math.max(0, currentAttempts - 1))));
        Instant nextRetryAt = canRetry ? Instant.now().plusSeconds(backoffSeconds) : null;

        stateService.recordInventoryReleaseFailure(
                task.getBookingId(),
                task.getBookingRef(),
                task.getId(),
                nextStatus,
                errorMessage,
                nextRetryAt
        );
    }

    /**
     * Strictly extracts a positive room count for genuine legacy non-date-wise bookings.
     * Never silently defaults missing, malformed, or non-positive room counts to 1.
     */
    int extractLegacyRoomCount(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            throw new IllegalArgumentException("Legacy hotel booking missing detailsJson room count metadata");
        }
        Matcher textMatcher = ROOMS_COUNT_PATTERN.matcher(detailsJson);
        if (textMatcher.find()) {
            return parseStrictlyPositiveCount(textMatcher.group(1), detailsJson);
        }
        Matcher jsonMatcher = JSON_ROOMS_COUNT_PATTERN.matcher(detailsJson);
        if (jsonMatcher.find()) {
            return parseStrictlyPositiveCount(jsonMatcher.group(1), detailsJson);
        }
        throw new IllegalArgumentException("Malformed or missing room count in legacy hotel detailsJson");
    }

    private int parseStrictlyPositiveCount(String rawCount, String detailsJson) {
        try {
            int parsed = Integer.parseInt(rawCount);
            if (parsed <= 0) {
                throw new IllegalArgumentException("Legacy hotel booking has non-positive room count (" + parsed + ")");
            }
            return parsed;
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Unparseable room count in legacy hotel detailsJson: " + rawCount, nfe);
        }
    }
}
