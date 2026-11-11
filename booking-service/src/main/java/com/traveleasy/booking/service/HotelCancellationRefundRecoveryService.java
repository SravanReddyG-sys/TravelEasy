package com.traveleasy.booking.service;

import com.traveleasy.booking.model.HotelCancellationRefundTask;
import com.traveleasy.booking.repository.HotelCancellationRefundTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates immediate and background durable refunds for cancelled HOTEL bookings (WP-HC-01).
 * Never recalculates policy or substitutes booking totalAmount on retries; strictly reuses
 * the persisted authoritative refundAmount and immutable idempotencyKey.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotelCancellationRefundRecoveryService {

    private final HotelCancellationRefundTaskRepository refundTaskRepository;
    private final HotelCancellationStateService stateService;
    private final BookingInternalTokenService internalTokenService;

    @Value("${payment.service.url:http://localhost:8085}")
    private String paymentServiceUrl;

    @Value("${booking.hotel.refund.reconciliation.lease-seconds:60}")
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
     * Executes immediate refund attempt right after cancellation commit, returning payment response map if available.
     */
    public Map<String, Object> attemptImmediateRefund(HotelCancellationRefundTask task) {
        if (task == null || task.getId() == null) {
            return null;
        }
        String claimer = "immediate-" + UUID.randomUUID().toString().substring(0, 8);
        return claimAndExecute(task.getId(), claimer, leaseSeconds);
    }

    /**
     * Retries refund ONLY when persisted refundAmount > 0 and refund is not already PROCESSED/FAILED_FINAL.
     */
    public Map<String, Object> retryIfPending(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) {
            return null;
        }
        Optional<HotelCancellationRefundTask> optTask = refundTaskRepository.findByBookingRef(bookingRef);
        if (optTask.isEmpty()) {
            return null;
        }
        HotelCancellationRefundTask task = optTask.get();
        if (task.getRefundAmount() == null || task.getRefundAmount() <= 0.0) {
            return null;
        }
        if (HotelCancellationStateService.REFUND_PROCESSED.equalsIgnoreCase(task.getStatus())
                || HotelCancellationStateService.REFUND_FAILED_FINAL.equalsIgnoreCase(task.getStatus())) {
            return null;
        }
        String claimer = "replay-" + UUID.randomUUID().toString().substring(0, 8);
        return claimAndExecute(task.getId(), claimer, leaseSeconds);
    }

    public Map<String, Object> claimAndExecute(Long taskId, String workerId, int leaseDurationSeconds) {
        Instant now = Instant.now();
        Instant leaseExpiry = now.plusSeconds(Math.max(10, leaseDurationSeconds));
        int claimed = refundTaskRepository.claimRefundTask(taskId, workerId, now, leaseExpiry);
        if (claimed == 0) {
            log.debug("Hotel refund task {} already leased or completed; skipping duplicate attempt", taskId);
            return null;
        }

        HotelCancellationRefundTask task = refundTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return null;
        }
        return executeClaimedRefund(task);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> executeClaimedRefund(HotelCancellationRefundTask task) {
        // Strict safety guard: never invoke payment-service for non-positive persisted refund amounts
        if (task.getRefundAmount() == null || task.getRefundAmount() <= 0.0) {
            log.warn("Refusing payment-service call for refund task {} (booking {}) with non-positive persisted amount {}",
                    task.getId(), task.getBookingRef(), task.getRefundAmount());
            stateService.recordRefundFailure(
                    task.getBookingId(),
                    task.getBookingRef(),
                    task.getId(),
                    HotelCancellationStateService.REFUND_FAILED_FINAL,
                    "Non-positive persisted refund amount",
                    null
            );
            return null;
        }

        try {
            String url = paymentServiceUrl + "/api/payments/refund/booking/" + task.getBookingRef();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String internalToken = internalTokenService.generateInternalToken();
            headers.set("Authorization", "Bearer " + internalToken);
            headers.set("X-Internal-Token", internalToken);
            headers.set("X-Internal-Service", BookingInternalTokenService.SERVICE_NAME);
            headers.set("X-Idempotency-Key", task.getIdempotencyKey());

            Map<String, Object> body = new HashMap<>();
            body.put("amount", task.getRefundAmount());
            body.put("idempotencyKey", task.getIdempotencyKey());
            body.put("reason", task.getReason() != null && !task.getReason().isBlank()
                    ? task.getReason()
                    : "Hotel booking cancellation (" + task.getPolicyTier() + ")");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Map<String, Object> respBody = resp.getBody();
                String rfdStatus = extractString(respBody, "refundStatus", "status");
                String rfdId = extractString(respBody, "refundId", "id");

                if ("INITIATED".equalsIgnoreCase(rfdStatus) || "PENDING".equalsIgnoreCase(rfdStatus)) {
                    Instant nextPoll = Instant.now().plusSeconds(30);
                    stateService.recordRefundInitiated(
                            task.getBookingId(),
                            task.getBookingRef(),
                            task.getId(),
                            rfdId,
                            nextPoll,
                            "Refund initiated at gateway (status=" + rfdStatus + ")"
                    );
                } else {
                    stateService.recordRefundSucceeded(
                            task.getBookingId(),
                            task.getBookingRef(),
                            task.getId(),
                            rfdId
                    );
                }
                log.info("Executed hotel cancellation refund for booking {} (amount={}, idempotencyKey={}, refundId={}, status={})",
                        task.getBookingRef(), task.getRefundAmount(), task.getIdempotencyKey(), rfdId, rfdStatus);
                return respBody;
            }

            handleFailure(task, "Payment service returned non-2xx or empty response", true);
            return null;

        } catch (HttpClientErrorException clientEx) {
            int rawStatus = clientEx.getStatusCode().value();
            // Retry 408/429 and 404 (in case webhook/verify commit race), reject invalid 400/409 after diagnostics or fail final
            boolean retryable = rawStatus == 408 || rawStatus == 429;
            log.warn("Payment service 4xx ({}) during hotel refund for booking {}: {}",
                    rawStatus, task.getBookingRef(), clientEx.getMessage());
            handleFailure(task, "HTTP " + rawStatus + ": " + clientEx.getMessage(), retryable);
            return null;

        } catch (Exception ex) {
            log.warn("Transient failure calling payment-service for hotel refund booking {} (idempotencyKey={}): {}",
                    task.getBookingRef(), task.getIdempotencyKey(), ex.getMessage());
            handleFailure(task, ex.getMessage(), true);
            return null;
        }
    }

    private void handleFailure(HotelCancellationRefundTask task, String errorMessage, boolean retryable) {
        // Never overwrite an already-initiated refund back to FAILED_RETRYABLE if gateway already accepted it
        if (task.getRazorpayRefundId() != null && !task.getRazorpayRefundId().isBlank()) {
            Instant nextPoll = Instant.now().plusSeconds(30);
            stateService.recordRefundInitiated(
                    task.getBookingId(),
                    task.getBookingRef(),
                    task.getId(),
                    task.getRazorpayRefundId(),
                    nextPoll,
                    "Temporary reconciliation error: " + errorMessage
            );
            return;
        }

        int currentAttempts = task.getAttemptCount() != null ? task.getAttemptCount() : 1;
        int maxAttempts = task.getMaxAttempts() != null ? task.getMaxAttempts() : 10;

        boolean canRetry = retryable && currentAttempts < maxAttempts;
        String nextStatus = canRetry
                ? HotelCancellationStateService.REFUND_FAILED_RETRYABLE
                : HotelCancellationStateService.REFUND_FAILED_FINAL;

        long backoffSeconds = Math.min(600L, 15L * (1L << Math.min(6, Math.max(0, currentAttempts - 1))));
        Instant nextRetryAt = canRetry ? Instant.now().plusSeconds(backoffSeconds) : null;

        stateService.recordRefundFailure(
                task.getBookingId(),
                task.getBookingRef(),
                task.getId(),
                nextStatus,
                errorMessage,
                nextRetryAt
        );
    }

    private String extractString(Map<String, Object> body, String primaryKey, String fallbackKey) {
        Object val = body.get(primaryKey);
        if (val == null && fallbackKey != null) {
            val = body.get(fallbackKey);
        }
        return val != null ? val.toString() : null;
    }
}
