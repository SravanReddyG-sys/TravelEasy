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
import java.util.Set;
import java.util.UUID;
import org.springframework.web.util.UriComponentsBuilder;

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

    private static final Set<String> TERMINAL_SUCCESS_STATUSES = Set.of("PROCESSED", "REFUNDED", "COMPLETED");
    private static final Set<String> NON_TERMINAL_STATUSES = Set.of("INITIATED", "PENDING", "CREATED");
    private static final Set<String> NON_RETRYABLE_REJECTION_STATUSES = Set.of("REJECTED", "CANCELLED", "CANCELED", "DENIED");

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

        HttpHeaders headers = buildInternalHeaders(task.getIdempotencyKey());
        boolean hasKnownRefundId = task.getRazorpayRefundId() != null && !task.getRazorpayRefundId().isBlank();
        boolean hasPriorDispatch = hasKnownRefundId
                || (task.getAttemptCount() != null && task.getAttemptCount() > 1)
                || (task.getLastError() != null && !task.getLastError().isBlank());

        // Finding 5: Authoritatively reconcile with payment-service/Razorpay BEFORE any re-initiation attempt
        if (hasPriorDispatch) {
            ReconcileStepOutcome reconcileOutcome = reconcileExistingGatewayRefund(task, headers, hasKnownRefundId);
            if (!reconcileOutcome.shouldProceedToInitiate()) {
                return reconcileOutcome.body();
            }
        }

        try {
            String url = paymentServiceUrl + "/api/payments/refund/booking/" + task.getBookingRef();

            Map<String, Object> body = new HashMap<>();
            body.put("amount", task.getRefundAmount());
            body.put("idempotencyKey", task.getIdempotencyKey());
            body.put("reason", task.getReason() != null && !task.getReason().isBlank()
                    ? task.getReason()
                    : "Hotel booking cancellation (" + task.getPolicyTier() + ")");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            return handleGatewayRefundResponse(task, resp, hasKnownRefundId);

        } catch (HttpClientErrorException clientEx) {
            int rawStatus = clientEx.getStatusCode().value();
            boolean retryable = rawStatus == 408 || rawStatus == 429;
            log.warn("Payment service 4xx ({}) during hotel refund for booking {}: {}",
                    rawStatus, task.getBookingRef(), clientEx.getMessage());
            handleFailure(task, "HTTP " + rawStatus + ": " + clientEx.getMessage(), retryable, true);
            return null;

        } catch (Exception ex) {
            log.warn("Transient failure calling payment-service for hotel refund booking {} (idempotencyKey={}): {}",
                    task.getBookingRef(), task.getIdempotencyKey(), ex.getMessage());
            handleFailure(task, ex.getMessage(), true, true);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private ReconcileStepOutcome reconcileExistingGatewayRefund(HotelCancellationRefundTask task,
                                                                 HttpHeaders headers,
                                                                 boolean hasKnownRefundId) {
        try {
            String reconcileUrl = UriComponentsBuilder
                    .fromHttpUrl(paymentServiceUrl + "/api/payments/refund/booking/" + task.getBookingRef() + "/reconcile")
                    .queryParam("idempotencyKey", task.getIdempotencyKey())
                    .queryParam("expectedAmount", task.getRefundAmount())
                    .toUriString();

            ResponseEntity<Map> reconcileResp = restTemplate.exchange(
                    reconcileUrl, HttpMethod.GET, new HttpEntity<Void>(headers), Map.class);

            if (reconcileResp == null || !reconcileResp.getStatusCode().is2xxSuccessful() || reconcileResp.getBody() == null) {
                handleFailure(task, "Reconciliation endpoint returned empty or non-2xx response", true, true);
                return new ReconcileStepOutcome(false, null);
            }

            Map<String, Object> body = reconcileResp.getBody();
            String rawStatus = extractString(body, "refundStatus", "status");
            String normalizedStatus = rawStatus != null ? rawStatus.trim().toUpperCase() : "";

            // Allow fresh initiation ONLY if payment-service confirms no refund exists yet and no refundId was issued
            if ("NOT_FOUND".equals(normalizedStatus)) {
                if (hasKnownRefundId) {
                    handleFailure(task, "Initiated refund " + task.getRazorpayRefundId() + " not yet visible during reconciliation", true, true);
                    return new ReconcileStepOutcome(false, body);
                }
                return new ReconcileStepOutcome(true, null);
            }

            // If a previous non-initiated attempt explicitly failed before receiving a refundId,
            // allow clean re-initiation only if gateway confirmed FAILED without a live pending refund
            if ("FAILED".equals(normalizedStatus) && !hasKnownRefundId) {
                log.info("Prior refund for booking {} confirmed FAILED on reconciliation; proceeding with retry initiation", task.getBookingRef());
                return new ReconcileStepOutcome(true, null);
            }

            Map<String, Object> handled = handleGatewayRefundResponse(task, reconcileResp, hasKnownRefundId);
            return new ReconcileStepOutcome(false, handled);

        } catch (HttpClientErrorException clientEx) {
            int rawStatus = clientEx.getStatusCode().value();
            boolean retryable = rawStatus == 408 || rawStatus == 429 || rawStatus == 404;
            log.warn("Reconcile 4xx ({}) for hotel refund booking {}: {}", rawStatus, task.getBookingRef(), clientEx.getMessage());
            handleFailure(task, "Reconcile HTTP " + rawStatus + ": " + clientEx.getMessage(), retryable, true);
            return new ReconcileStepOutcome(false, null);
        } catch (Exception ex) {
            log.warn("Transient failure reconciling hotel refund for booking {}: {}", task.getBookingRef(), ex.getMessage());
            handleFailure(task, ex.getMessage(), true, true);
            return new ReconcileStepOutcome(false, null);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleGatewayRefundResponse(HotelCancellationRefundTask task,
                                                            ResponseEntity<Map> resp,
                                                            boolean hasKnownRefundId) {
        if (resp == null || !resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            handleFailure(task, "Payment service returned non-2xx or empty response", true, true);
            return null;
        }

        Map<String, Object> respBody = resp.getBody();
        String rfdStatus = extractString(respBody, "refundStatus", "status");
        String rfdId = extractString(respBody, "refundId", "id");
        String normalizedStatus = rfdStatus != null ? rfdStatus.trim().toUpperCase() : "";

        // Explicit terminal success ONLY
        if (TERMINAL_SUCCESS_STATUSES.contains(normalizedStatus)) {
            stateService.recordRefundSucceeded(
                    task.getBookingId(),
                    task.getBookingRef(),
                    task.getId(),
                    rfdId
            );
            log.info("Confirmed PROCESSED hotel refund for booking {} (amount={}, idempotencyKey={}, refundId={}, rawStatus={})",
                    task.getBookingRef(), task.getRefundAmount(), task.getIdempotencyKey(), rfdId, rfdStatus);
            return respBody;
        }

        // Explicit non-terminal gateway acceptance
        if (NON_TERMINAL_STATUSES.contains(normalizedStatus)) {
            Instant nextPoll = Instant.now().plusSeconds(30);
            stateService.recordRefundInitiated(
                    task.getBookingId(),
                    task.getBookingRef(),
                    task.getId(),
                    rfdId,
                    nextPoll,
                    "Refund initiated at gateway (status=" + normalizedStatus + ")"
            );
            log.info("Recorded non-terminal {} hotel refund for booking {} (refundId={})",
                    normalizedStatus, task.getBookingRef(), rfdId);
            return respBody;
        }

        // Explicit FAILED, REJECTED, UNKNOWN, or missing status MUST NEVER be treated as PROCESSED
        boolean retryable = !NON_RETRYABLE_REJECTION_STATUSES.contains(normalizedStatus);
        String outcomeDesc = normalizedStatus.isBlank()
                ? "Payment service returned 2xx with missing refundStatus"
                : "Payment service returned unsuccessful/unresolved refundStatus: " + normalizedStatus;
        log.warn("Hotel refund unsuccessful/unresolved for booking {} (refundId={}, status={}, knownRefundId={}): {}",
                task.getBookingRef(), rfdId, normalizedStatus, hasKnownRefundId, outcomeDesc);

        // Pass preserveInitiatedOnTransientError=false so explicit FAILED/REJECTED/UNKNOWN outcomes reflect on Booking & Task consistently
        handleFailure(task, outcomeDesc, retryable, false);
        return respBody;
    }

    private HttpHeaders buildInternalHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String internalToken = internalTokenService.generateInternalToken();
        headers.set("Authorization", "Bearer " + internalToken);
        headers.set("X-Internal-Token", internalToken);
        headers.set("X-Internal-Service", BookingInternalTokenService.SERVICE_NAME);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("X-Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    private void handleFailure(HotelCancellationRefundTask task,
                               String errorMessage,
                               boolean retryable,
                               boolean preserveInitiatedOnTransientError) {
        // Keep active INITIATED state ONLY during transient poll/network errors when gateway already holds our refundId
        if (preserveInitiatedOnTransientError
                && task.getRazorpayRefundId() != null
                && !task.getRazorpayRefundId().isBlank()) {
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

    private record ReconcileStepOutcome(boolean shouldProceedToInitiate, Map<String, Object> body) {
    }

    private String extractString(Map<String, Object> body, String primaryKey, String fallbackKey) {
        Object val = body.get(primaryKey);
        if (val == null && fallbackKey != null) {
            val = body.get(fallbackKey);
        }
        return val != null ? val.toString() : null;
    }
}
