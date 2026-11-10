package com.traveleasy.payment.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Refund;
import com.razorpay.Utils;
import com.traveleasy.payment.config.RazorpayConfig;
import com.traveleasy.payment.dto.*;
import com.traveleasy.payment.exception.PaymentException;
import com.traveleasy.payment.model.PaymentRefundTask;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.repository.PaymentRefundTaskRepository;
import com.traveleasy.payment.model.PaymentWebhookEvent;
import com.traveleasy.payment.repository.PaymentRepository;
import com.traveleasy.payment.repository.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final RazorpayConfig razorpayConfig;
    private final RazorpayClient razorpayClient;
    private RestTemplate restTemplate;
    private final PaymentStateTransitionService stateTransitionService;
    private final PaymentRecipientResolutionService recipientResolutionService;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentRefundTaskRepository refundTaskRepository;
    private final PaymentInternalTokenService internalTokenService;

    @org.springframework.beans.factory.annotation.Value("${booking.service.url:http://localhost:8084}")
    private String bookingServiceUrl = "http://localhost:8084";

    public void setBookingServiceUrl(String bookingServiceUrl) {
        this.bookingServiceUrl = bookingServiceUrl;
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private final java.util.concurrent.ConcurrentHashMap<String, Object> orderCreationLocks = new java.util.concurrent.ConcurrentHashMap<>();

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class PaymentConfirmationResult {
        private boolean confirmed;
        private boolean refunded;
        private String message;
        private PaymentTransaction transaction;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            RazorpayConfig razorpayConfig,
            RazorpayClient razorpayClient,
            RestTemplate restTemplate,
            PaymentStateTransitionService stateTransitionService,
            PaymentRecipientResolutionService recipientResolutionService,
            PaymentWebhookEventRepository webhookEventRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PaymentRefundTaskRepository refundTaskRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PaymentInternalTokenService internalTokenService) {
        this.paymentRepository = paymentRepository;
        this.razorpayConfig = razorpayConfig;
        this.razorpayClient = razorpayClient;
        this.restTemplate = restTemplate;
        this.stateTransitionService = stateTransitionService;
        this.recipientResolutionService = recipientResolutionService;
        this.webhookEventRepository = webhookEventRepository;
        this.refundTaskRepository = refundTaskRepository;
        this.internalTokenService = internalTokenService;
    }

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            RazorpayConfig razorpayConfig,
            RazorpayClient razorpayClient,
            RestTemplate restTemplate,
            PaymentStateTransitionService stateTransitionService,
            PaymentRecipientResolutionService recipientResolutionService,
            PaymentWebhookEventRepository webhookEventRepository,
            PaymentInternalTokenService internalTokenService) {
        this(paymentRepository, razorpayConfig, razorpayClient, restTemplate, stateTransitionService, recipientResolutionService, webhookEventRepository, null, internalTokenService);
    }

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            RazorpayConfig razorpayConfig,
            RazorpayClient razorpayClient,
            RestTemplate restTemplate,
            PaymentStateTransitionService stateTransitionService,
            PaymentRecipientResolutionService recipientResolutionService,
            PaymentWebhookEventRepository webhookEventRepository) {
        this(paymentRepository, razorpayConfig, razorpayClient, restTemplate, stateTransitionService, recipientResolutionService, webhookEventRepository, null, null);
    }

    @Override
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        if (!razorpayConfig.isConfigured() || razorpayClient == null) {
            log.error("Cannot create order: Razorpay credentials are not configured");
            throw new PaymentException("Razorpay payment gateway is not configured on the server", HttpStatus.SERVICE_UNAVAILABLE);
        }

        // 1. Fast-path check on existing transaction state (WP-HB-05 Section 6.2)
        Optional<PaymentTransaction> existingCheck = paymentRepository.findByBookingRef(request.getBookingRef());
        if (existingCheck.isPresent()) {
            String existingStatus = existingCheck.get().getStatus();
            if ("SUCCESS".equalsIgnoreCase(existingStatus)) {
                log.warn("Booking {} has already been paid successfully", request.getBookingRef());
                throw new PaymentException("Booking is already paid and confirmed", HttpStatus.CONFLICT);
            }
            if ("REFUNDED".equalsIgnoreCase(existingStatus) || "REFUND_INITIATED".equalsIgnoreCase(existingStatus)) {
                log.warn("Booking {} has been refunded and cannot be paid again", request.getBookingRef());
                throw new PaymentException("Booking payment has been refunded and cannot be reused", HttpStatus.CONFLICT);
            }
        }

        // 2. Fail-closed booking existence, eligibility, deadline & amount validation (WP-HB-05 Section 6.1)
        verifyBookingAmount(request.getBookingRef(), request.getAmount());

        // 2. Concurrency-safe per-booking order creation & idempotency check (WP-HB-05 Section 6.2)
        Object lock = orderCreationLocks.computeIfAbsent(request.getBookingRef(), k -> new Object());
        synchronized (lock) {
            Optional<PaymentTransaction> existingOpt = paymentRepository.findByBookingRef(request.getBookingRef());
            if (existingOpt.isPresent()) {
                PaymentTransaction existing = existingOpt.get();
                String status = existing.getStatus();

                // SUCCESS: Reject another payment attempt
                if ("SUCCESS".equalsIgnoreCase(status)) {
                    log.warn("Booking {} has already been paid successfully", request.getBookingRef());
                    throw new PaymentException("Booking is already paid and confirmed", HttpStatus.CONFLICT);
                }

                // REFUNDED: Do not reuse refunded transaction
                if ("REFUNDED".equalsIgnoreCase(status) || "REFUND_INITIATED".equalsIgnoreCase(status)) {
                    log.warn("Booking {} has been refunded and cannot be paid again", request.getBookingRef());
                    throw new PaymentException("Booking payment has been refunded and cannot be reused", HttpStatus.CONFLICT);
                }

                // CREATED with valid reusable order: Return existing order
                if ("CREATED".equalsIgnoreCase(status)) {
                    if (existing.getRazorpayOrderId() != null && !existing.getRazorpayOrderId().isBlank()) {
                        if (Math.abs(existing.getAmount() - request.getAmount()) < 0.01) {
                            log.info("Reusing existing valid Razorpay order {} for booking {}",
                                    existing.getRazorpayOrderId(), request.getBookingRef());
                            long amountInPaise = Math.round(existing.getAmount() * 100);
                            return CreateOrderResponse.builder()
                                    .orderId(existing.getRazorpayOrderId())
                                    .transactionRef(existing.getTransactionRef())
                                    .bookingRef(existing.getBookingRef())
                                    .amount(existing.getAmount())
                                    .amountInPaise(amountInPaise)
                                    .currency(existing.getCurrency() != null ? existing.getCurrency() : "INR")
                                    .keyId(razorpayConfig.getKeyId())
                                    .status("CREATED")
                                    .build();
                        }
                    }
                }

                // FAILED: Permit controlled retry if booking remains eligible
                if ("FAILED".equalsIgnoreCase(status)) {
                    log.info("Permitting controlled retry for previously failed payment on booking {}", request.getBookingRef());
                }
            }

            try {
                long amountInPaise = Math.round(request.getAmount() * 100);
                JSONObject orderRequest = new JSONObject();
                orderRequest.put("amount", amountInPaise);
                orderRequest.put("currency", "INR");
                orderRequest.put("receipt", request.getBookingRef());

                JSONObject notes = new JSONObject();
                notes.put("bookingRef", request.getBookingRef());
                if (request.getCustomerId() != null) {
                    notes.put("customerId", String.valueOf(request.getCustomerId()));
                }
                orderRequest.put("notes", notes);

                Order order = razorpayClient.orders.create(orderRequest);
                String orderId = order.get("id");
                String txnRef = "TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

                PaymentTransaction transaction = existingOpt.orElse(new PaymentTransaction());
                transaction.setTransactionRef(txnRef);
                transaction.setBookingRef(request.getBookingRef());
                transaction.setCustomerId(request.getCustomerId());
                transaction.setAmount(request.getAmount());
                transaction.setCurrency("INR");
                transaction.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "RAZORPAY");
                transaction.setStatus("CREATED");
                transaction.setRazorpayOrderId(orderId);
                transaction.setErrorCode(null);
                transaction.setErrorDescription(null);
                transaction.setTimestamp(LocalDateTime.now());

                paymentRepository.save(transaction);
                log.info("Created new Razorpay order {} for booking {}", orderId, request.getBookingRef());

                return CreateOrderResponse.builder()
                        .orderId(orderId)
                        .transactionRef(txnRef)
                        .bookingRef(request.getBookingRef())
                        .amount(request.getAmount())
                        .amountInPaise(amountInPaise)
                        .currency("INR")
                        .keyId(razorpayConfig.getKeyId())
                        .status("CREATED")
                        .build();

            } catch (PaymentException pe) {
                throw pe;
            } catch (Exception e) {
                log.error("Failed to create Razorpay order for booking {}: {}", request.getBookingRef(), e.getMessage(), e);
                throw new PaymentException("Payment order creation failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private Map<String, Object> verifyBookingAmount(String bookingRef, Double requestedAmount) {
        if (bookingRef == null || bookingRef.isBlank()) {
            throw new PaymentException("Booking reference is mandatory for payment order creation", HttpStatus.BAD_REQUEST);
        }
        if (requestedAmount == null || requestedAmount <= 0) {
            throw new PaymentException("Requested payment amount must be greater than zero", HttpStatus.BAD_REQUEST);
        }

        String bookingUrl = bookingServiceUrl + "/api/bookings/ref/" + bookingRef;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalTokenService != null) {
            try {
                String token = internalTokenService.generateInternalToken();
                headers.set("Authorization", "Bearer " + token);
                headers.set("X-Internal-Token", token);
                headers.set("X-Internal-Service", "payment-service");
            } catch (Exception ignored) {}
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = null;
        try {
            response = restTemplate.getForEntity(bookingUrl, Map.class);
        } catch (org.springframework.web.client.HttpClientErrorException hce) {
            if (hce.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.error("Booking reference {} does not exist in Booking Service", bookingRef);
                throw new PaymentException("Booking reference not found: " + bookingRef, HttpStatus.NOT_FOUND);
            }
        } catch (Exception ignored) {}

        if (response == null || response.getBody() == null) {
            try {
                response = restTemplate.exchange(bookingUrl, HttpMethod.GET, entity, Map.class);
            } catch (org.springframework.web.client.HttpClientErrorException hce) {
                if (hce.getStatusCode() == HttpStatus.NOT_FOUND) {
                    log.error("Booking reference {} does not exist in Booking Service", bookingRef);
                    throw new PaymentException("Booking reference not found: " + bookingRef, HttpStatus.NOT_FOUND);
                }
                throw new PaymentException("Booking Service returned error: " + hce.getMessage(), (HttpStatus) hce.getStatusCode());
            } catch (PaymentException pe) {
                throw pe;
            } catch (Exception e) {
                log.error("Failed to communicate with Booking Service for bookingRef {}: {}", bookingRef, e.getMessage());
                throw new PaymentException("Cannot verify booking: Booking Service is unavailable or unreachable", HttpStatus.SERVICE_UNAVAILABLE);
            }
        }

        if (response == null || response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            log.error("Booking Service returned non-OK status for bookingRef {}", bookingRef);
            throw new PaymentException("Unable to retrieve booking record from Booking Service", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> booking = (Map<String, Object>) response.getBody();

        // 1. Status eligibility
        String status = String.valueOf(booking.get("status"));
        if ("CONFIRMED".equalsIgnoreCase(status)) {
            log.warn("Booking {} is already CONFIRMED. Payment cannot be initiated.", bookingRef);
            throw new PaymentException("Booking is already confirmed", HttpStatus.CONFLICT);
        }
        if ("EXPIRED".equalsIgnoreCase(status)) {
            log.warn("Booking {} has status EXPIRED. Payment rejected.", bookingRef);
            throw new PaymentException("Booking has expired and is not eligible for payment", HttpStatus.BAD_REQUEST);
        }
        if ("CANCELLED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
            log.warn("Booking {} is in terminal state {}. Payment rejected.", bookingRef, status);
            throw new PaymentException("Booking is not eligible for payment (status: " + status + ")", HttpStatus.BAD_REQUEST);
        }

        // 2. Authoritative expiresAt deadline check
        Object expiresAtObj = booking.get("expiresAt");
        if (expiresAtObj != null) {
            try {
                String val = expiresAtObj.toString();
                java.time.Instant deadline;
                if (val.endsWith("Z") || val.contains("+")) {
                    deadline = java.time.Instant.parse(val);
                } else {
                    deadline = java.time.LocalDateTime.parse(val).atZone(java.time.ZoneId.systemDefault()).toInstant();
                }
                if (java.time.Instant.now().isAfter(deadline)) {
                    log.warn("Booking {} expiresAt deadline {} has passed. Payment rejected.", bookingRef, deadline);
                    throw new PaymentException("Booking reservation deadline has expired", HttpStatus.BAD_REQUEST);
                }
            } catch (PaymentException pe) {
                throw pe;
            } catch (Exception parseEx) {
                log.debug("Could not parse expiresAt '{}': {}", expiresAtObj, parseEx.getMessage());
            }
        }

        // 3. Amount validation (strict fail-closed against tampering)
        Object totalAmtObj = booking.get("totalAmount");
        if (totalAmtObj == null) {
            log.error("Authoritative totalAmount missing from booking record for {}", bookingRef);
            throw new PaymentException("Booking record does not have an authoritative amount", HttpStatus.BAD_REQUEST);
        }
        double expectedAmount = Double.parseDouble(totalAmtObj.toString());
        if (Math.abs(expectedAmount - requestedAmount) > 0.01) {
            log.error("Amount tampering detected for booking {}! Expected: {}, Received: {}", bookingRef, expectedAmount, requestedAmount);
            throw new PaymentException("Amount mismatch with booking record: expected " + expectedAmount + ", received " + requestedAmount, HttpStatus.BAD_REQUEST);
        }

        return booking;
    }

    @Override
    public PaymentVerificationResponse verifyPayment(VerifyPaymentRequest request) {
        if (!razorpayConfig.isConfigured()) {
            throw new PaymentException("Razorpay is not configured on the server", HttpStatus.SERVICE_UNAVAILABLE);
        }

        // Idempotency: check if already verified successfully
        PaymentTransaction transaction = paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new PaymentException("Order not found: " + request.getRazorpayOrderId(), HttpStatus.NOT_FOUND));

        if ("SUCCESS".equalsIgnoreCase(transaction.getStatus())) {
            log.info("Payment already verified for booking {}", transaction.getBookingRef());
            return PaymentVerificationResponse.builder()
                    .verified(true)
                    .message("Payment already verified and confirmed")
                    .bookingRef(transaction.getBookingRef())
                    .transactionRef(transaction.getTransactionRef())
                    .paymentId(transaction.getRazorpayPaymentId())
                    .status("SUCCESS")
                    .transaction(transaction)
                    .build();
        }

        // Verify HMAC-SHA256 signature
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", request.getRazorpayOrderId());
            attributes.put("razorpay_payment_id", request.getRazorpayPaymentId());
            attributes.put("razorpay_signature", request.getRazorpaySignature());

            boolean isValid = Utils.verifyPaymentSignature(attributes, razorpayConfig.getKeySecret());
            if (!isValid) {
                log.warn("Signature verification failed for order {}", request.getRazorpayOrderId());
                // Client signature mismatch is NOT an authoritative gateway failure signal.
                // Do NOT emit a customer-facing PAYMENT_FAILED outbox event!
                if (!"SUCCESS".equalsIgnoreCase(transaction.getStatus()) && !"REFUNDED".equalsIgnoreCase(transaction.getStatus())) {
                    transaction.setStatus("FAILED");
                    transaction.setErrorCode("SIGNATURE_MISMATCH");
                    transaction.setErrorDescription("Signature verification failed");
                    paymentRepository.save(transaction);
                }
                throw new PaymentException("Payment signature verification failed", HttpStatus.BAD_REQUEST);
            }

            // Unified authoritative payment success confirmation (WP-HB-05 Section 7)
            PaymentConfirmationResult result = processAuthoritativePaymentSuccess(
                    transaction, request.getRazorpayPaymentId(), request.getRazorpaySignature());

            PaymentTransaction saved = result.getTransaction();

            return PaymentVerificationResponse.builder()
                    .verified(result.isConfirmed())
                    .message(result.getMessage())
                    .bookingRef(saved.getBookingRef())
                    .transactionRef(saved.getTransactionRef())
                    .paymentId(saved.getRazorpayPaymentId())
                    .status(saved.getStatus())
                    .transaction(saved)
                    .build();

        } catch (PaymentException pe) {
            throw pe;
        } catch (Exception e) {
            log.error("Verification error: {}", e.getMessage(), e);
            if (!"SUCCESS".equalsIgnoreCase(transaction.getStatus()) && !"REFUNDED".equalsIgnoreCase(transaction.getStatus())) {
                transaction.setStatus("FAILED");
                transaction.setErrorCode("VERIFICATION_EXCEPTION");
                transaction.setErrorDescription(e.getMessage());
                paymentRepository.save(transaction);
            }
            throw new PaymentException("Payment verification encountered an error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void processWebhook(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new PaymentException("Missing X-Razorpay-Signature header", HttpStatus.BAD_REQUEST);
        }
        String webhookSecret = razorpayConfig.getWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("RAZORPAY_WEBHOOK_SECRET not configured. Rejecting webhook request.");
            throw new PaymentException("Webhook secret is not configured on server", HttpStatus.SERVICE_UNAVAILABLE);
        }

        try {
            boolean validSignature = Utils.verifyWebhookSignature(rawPayload, signatureHeader, webhookSecret);
            if (!validSignature) {
                log.error("Webhook signature verification failed");
                throw new PaymentException("Invalid webhook signature", HttpStatus.BAD_REQUEST);
            }

            JSONObject json = new JSONObject(rawPayload);
            String event = json.optString("event");
            log.info("Processing Razorpay webhook event: {}", event);

            String webhookEventId = extractWebhookEventId(json);

            // Webhook Inbox: check existing status
            Optional<PaymentWebhookEvent> optInbox = webhookEventRepository.findByWebhookEventId(webhookEventId);
            if (optInbox.isPresent()) {
                PaymentWebhookEvent existing = optInbox.get();
                if ("PROCESSED".equalsIgnoreCase(existing.getStatus())) {
                    log.info("Razorpay webhook event {} is already PROCESSED. Skipping duplicate delivery.", webhookEventId);
                    return;
                }
                if ("PROCESSING".equalsIgnoreCase(existing.getStatus())) {
                    Instant now = Instant.now();
                    if (existing.getLockedUntil() != null && existing.getLockedUntil().isAfter(now)) {
                        log.info("Razorpay webhook event {} is currently being processed by worker {}. Skipping duplicate.",
                                webhookEventId, existing.getLockedBy());
                        return;
                    }
                    log.warn("Stale PROCESSING lease detected for webhook event {}. Reclaiming.", webhookEventId);
                }
            } else {
                // Register new webhook in inbox as RECEIVED
                PaymentWebhookEvent inboxEvent = PaymentWebhookEvent.builder()
                        .webhookEventId(webhookEventId)
                        .eventType(event)
                        .payload(truncatePayload(rawPayload, 2000))
                        .status("RECEIVED")
                        .receivedAt(Instant.now())
                        .build();
                try {
                    webhookEventRepository.saveAndFlush(inboxEvent);
                } catch (DataIntegrityViolationException dive) {
                    log.info("Concurrent duplicate webhook {} registered on insert. Proceeding with lease claim.", webhookEventId);
                }
            }

            // Claim lease atomically
            String workerId = "wh-worker-" + UUID.randomUUID();
            Instant now = Instant.now();
            Instant leaseExpiry = now.plusSeconds(60);
            int claimed = webhookEventRepository.claimWebhookEvent(webhookEventId, workerId, now, leaseExpiry);
            if (claimed == 0) {
                log.info("Webhook event {} lease could not be claimed (already claimed or completed). Skipping.", webhookEventId);
                return;
            }

            try {
                JSONObject payloadObj = json.optJSONObject("payload");
                dispatchWebhookPayload(event, payloadObj);

                // Mark Inbox record as PROCESSED
                webhookEventRepository.markProcessed(webhookEventId, Instant.now());
                log.info("Successfully processed webhook event {}", webhookEventId);
            } catch (Exception ex) {
                webhookEventRepository.markFailed(webhookEventId, ex.getMessage());
                throw ex;
            }

        } catch (PaymentException pe) {
            throw pe;
        } catch (Exception e) {
            log.error("Failed to process webhook: {}", e.getMessage(), e);
            throw new PaymentException("Webhook processing error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void dispatchWebhookPayload(String event, JSONObject payloadObj) {
        if (payloadObj == null) return;
        if ("payment.captured".equalsIgnoreCase(event) || "order.paid".equalsIgnoreCase(event)) {
            handlePaymentCapturedWebhook(payloadObj);
        } else if ("payment.failed".equalsIgnoreCase(event)) {
            handlePaymentFailedWebhook(payloadObj);
        } else if ("refund.processed".equalsIgnoreCase(event)) {
            handleRefundProcessedWebhook(payloadObj);
        } else {
            log.info("Ignored unhandled Razorpay webhook event type: {}", event);
        }
    }

    public boolean recoverSingleWebhook(PaymentWebhookEvent event, int leaseSeconds) {
        String workerId = "recovery-" + UUID.randomUUID();
        Instant now = Instant.now();
        Instant leaseExpiry = now.plusSeconds(leaseSeconds);

        int claimed = webhookEventRepository.claimWebhookEvent(event.getWebhookEventId(), workerId, now, leaseExpiry);
        if (claimed == 0) {
            return false;
        }

        try {
            JSONObject json = new JSONObject(event.getPayload());
            String eventType = json.optString("event");
            JSONObject payloadObj = json.optJSONObject("payload");
            dispatchWebhookPayload(eventType, payloadObj);

            webhookEventRepository.markProcessed(event.getWebhookEventId(), Instant.now());
            log.info("Successfully recovered incomplete webhook event {}", event.getWebhookEventId());
            return true;
        } catch (Exception ex) {
            log.error("Failed to recover webhook event {}: {}", event.getWebhookEventId(), ex.getMessage(), ex);
            webhookEventRepository.markFailed(event.getWebhookEventId(), ex.getMessage());
            return false;
        }
    }

    private String extractWebhookEventId(JSONObject json) {
        if (json.has("id") && !json.optString("id").isBlank()) {
            return json.optString("id").trim();
        }
        if (json.has("event_id") && !json.optString("event_id").isBlank()) {
            return json.optString("event_id").trim();
        }
        JSONObject payload = json.optJSONObject("payload");
        if (payload != null) {
            for (String key : payload.keySet()) {
                JSONObject obj = payload.optJSONObject(key);
                if (obj != null) {
                    JSONObject entity = obj.optJSONObject("entity");
                    if (entity != null && entity.has("id")) {
                        return json.optString("event") + "_" + entity.optString("id");
                    }
                }
            }
        }
        return "whk_" + UUID.randomUUID().toString();
    }

    private String truncatePayload(String payload, int maxLength) {
        if (payload == null) return null;
        return payload.length() <= maxLength ? payload : payload.substring(0, maxLength);
    }

    private void handlePaymentCapturedWebhook(JSONObject payloadObj) {
        JSONObject paymentObj = payloadObj.optJSONObject("payment");
        JSONObject entity = paymentObj != null ? paymentObj.optJSONObject("entity") : null;
        if (entity == null) return;

        String orderId = entity.optString("order_id");
        String paymentId = entity.optString("id");

        Optional<PaymentTransaction> optTxn = paymentRepository.findByRazorpayOrderId(orderId);
        if (optTxn.isPresent()) {
            PaymentTransaction txn = optTxn.get();
            if ("SUCCESS".equalsIgnoreCase(txn.getStatus())) {
                log.info("Webhook duplicate payment.captured: transaction {} for order {} is already SUCCESS. Skipping duplicate confirmation.",
                        txn.getTransactionRef(), orderId);
                return;
            }
            // Shared authoritative payment success pathway (WP-HB-05 Section 7)
            processAuthoritativePaymentSuccess(txn, paymentId, null);
        }
    }

    private void handlePaymentFailedWebhook(JSONObject payloadObj) {
        JSONObject paymentObj = payloadObj.optJSONObject("payment");
        JSONObject entity = paymentObj != null ? paymentObj.optJSONObject("entity") : null;
        if (entity == null) return;

        String orderId = entity.optString("order_id");
        String paymentId = entity.optString("id");
        String errorCode = entity.optString("error_code", "GATEWAY_ERROR");
        String errorDesc = entity.optString("error_description");
        if (errorDesc == null || errorDesc.isBlank()) {
            errorDesc = entity.optString("error_reason", "Payment failed at gateway");
        }

        Optional<PaymentTransaction> optTxn = Optional.empty();
        if (orderId != null && !orderId.isBlank()) {
            optTxn = paymentRepository.findByRazorpayOrderId(orderId);
        }
        if (optTxn.isEmpty() && paymentId != null && !paymentId.isBlank()) {
            optTxn = paymentRepository.findByRazorpayPaymentId(paymentId);
        }

        if (optTxn.isEmpty()) {
            log.warn("No transaction found for payment.failed webhook (orderId: {}, paymentId: {})", orderId, paymentId);
            return;
        }

        PaymentTransaction txn = optTxn.get();
        if ("SUCCESS".equalsIgnoreCase(txn.getStatus()) || "REFUNDED".equalsIgnoreCase(txn.getStatus())) {
            log.warn("Received payment.failed for transaction {} which is already in {} status. Rejecting downgrade.",
                    txn.getTransactionRef(), txn.getStatus());
            return;
        }

        List<RecipientDto> recipients = recipientResolutionService.resolveTravellerRecipient(
                txn.getBookingRef(), txn.getCustomerId());
        String eventId = "PAY-EVT-FAILED-" + txn.getTransactionRef();

        stateTransitionService.recordPaymentFailed(txn, errorCode, errorDesc, eventId, recipients);
        log.info("Authoritative payment failure recorded for txn {}: {}", txn.getTransactionRef(), errorDesc);
    }

    private void handleRefundProcessedWebhook(JSONObject payloadObj) {
        JSONObject refundObj = payloadObj.optJSONObject("refund");
        JSONObject entity = refundObj != null ? refundObj.optJSONObject("entity") : null;
        if (entity == null) return;

        String refundId = entity.optString("id");
        String paymentId = entity.optString("payment_id");
        Double refundAmount = null;
        if (entity.has("amount")) {
            long paise = entity.optLong("amount");
            refundAmount = paise / 100.0;
        }

        final Double finalRefundAmount = refundAmount;
        paymentRepository.findByRazorpayPaymentId(paymentId).ifPresent(txn -> {
            Double effectiveAmount = finalRefundAmount != null ? finalRefundAmount : txn.getRefundAmount();
            if (effectiveAmount == null) effectiveAmount = txn.getAmount();

            List<RecipientDto> recipients = recipientResolutionService.resolveTravellerRecipient(txn.getBookingRef(), txn.getCustomerId());
            String eventId = "PAY-EVT-REFUND-COMP-" + refundId;

            stateTransitionService.recordRefundCompleted(txn, refundId, effectiveAmount, txn.getIdempotencyKey(), eventId, recipients);
            log.info("Webhook updated refund status to PROCESSED for transaction {}", txn.getTransactionRef());

            if (refundTaskRepository != null) {
                refundTaskRepository.findByBookingRef(txn.getBookingRef())
                        .ifPresent(t -> refundTaskRepository.markProcessed(t.getId(), refundId, Instant.now()));
            }
        });
    }

    @Override
    public RefundResponse processRefundByBooking(String bookingRef, RefundRequest request) {
        PaymentTransaction txn = paymentRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> new PaymentException("Transaction not found for booking: " + bookingRef, HttpStatus.NOT_FOUND));
        return processRefund(txn.getTransactionRef(), request);
    }

    @Override
    public RefundResponse processRefund(String transactionRef, RefundRequest request) {
        PaymentTransaction txn = paymentRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new PaymentException("Transaction not found for ref: " + transactionRef, HttpStatus.NOT_FOUND));

        String idempotencyKey = (request != null && request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                ? request.getIdempotencyKey().trim()
                : null;

        // Idempotency check 1: Already refunded
        if ("REFUNDED".equalsIgnoreCase(txn.getStatus())) {
            log.info("Transaction {} already refunded with refundId {}", transactionRef, txn.getRefundId());
            return RefundResponse.builder()
                    .message("Transaction has already been fully refunded")
                    .refundId(txn.getRefundId())
                    .refundStatus(txn.getRefundStatus())
                    .refundAmount(txn.getRefundAmount())
                    .transactionRef(txn.getTransactionRef())
                    .bookingRef(txn.getBookingRef())
                    .timestamp(txn.getRefundedAt())
                    .transaction(txn)
                    .build();
        }

        // Idempotency check 2: Same idempotency key already processed
        if (idempotencyKey != null && idempotencyKey.equals(txn.getIdempotencyKey()) && txn.getRefundId() != null) {
            log.info("Transaction {} matched idempotencyKey {} with refundId {}", transactionRef, idempotencyKey, txn.getRefundId());
            return RefundResponse.builder()
                    .message("Refund already processed for idempotency key: " + idempotencyKey)
                    .refundId(txn.getRefundId())
                    .refundStatus(txn.getRefundStatus())
                    .refundAmount(txn.getRefundAmount())
                    .transactionRef(txn.getTransactionRef())
                    .bookingRef(txn.getBookingRef())
                    .timestamp(txn.getRefundedAt())
                    .transaction(txn)
                    .build();
        }

        // Validate refund eligibility
        if (!"SUCCESS".equalsIgnoreCase(txn.getStatus()) && !"REFUND_INITIATED".equalsIgnoreCase(txn.getStatus())) {
            throw new PaymentException("Only successful payments can be refunded. Current status: " + txn.getStatus(), HttpStatus.BAD_REQUEST);
        }

        double refundAmount = (request != null && request.getAmount() != null && request.getAmount() > 0)
                ? request.getAmount()
                : txn.getAmount();

        if (refundAmount > txn.getAmount()) {
            throw new PaymentException("Refund amount (" + refundAmount + ") cannot exceed transaction amount (" + txn.getAmount() + ")", HttpStatus.BAD_REQUEST);
        }

        if (!razorpayConfig.isConfigured() || razorpayClient == null) {
            throw new PaymentException("Razorpay payment gateway is not configured on the server", HttpStatus.SERVICE_UNAVAILABLE);
        }

        long targetPaise = Math.round(refundAmount * 100);

        // Gateway Reconciliation Check (outside local DB transaction)
        try {
            List<Refund> existingRefunds = razorpayClient.payments.fetchAllRefunds(txn.getRazorpayPaymentId());
            if (existingRefunds != null && !existingRefunds.isEmpty()) {
                for (Refund ref : existingRefunds) {
                    long refPaise = ref.get("amount") instanceof Number ? ((Number) ref.get("amount")).longValue() : 0L;
                    String refStatus = ref.get("status");
                    String refId = ref.get("id");

                    if (refPaise == targetPaise) {
                        log.info("Gateway reconciliation: Found existing Razorpay refund {} with status {} for payment {}",
                                refId, refStatus, txn.getRazorpayPaymentId());

                        List<RecipientDto> recipients = recipientResolutionService.resolveTravellerRecipient(txn.getBookingRef(), txn.getCustomerId());
                        PaymentTransaction saved;
                        if ("processed".equalsIgnoreCase(refStatus)) {
                            String eventId = "PAY-EVT-REFUND-COMP-" + refId;
                            saved = stateTransitionService.recordRefundCompleted(txn, refId, refundAmount, idempotencyKey, eventId, recipients);
                            if (idempotencyKey != null && refundTaskRepository != null) {
                                refundTaskRepository.findByIdempotencyKey(idempotencyKey)
                                        .ifPresent(t -> refundTaskRepository.markProcessed(t.getId(), refId, Instant.now()));
                            }
                        } else {
                            String eventId = "PAY-EVT-REFUND-INIT-" + refId;
                            saved = stateTransitionService.recordRefundInitiated(txn, refId, refundAmount, refStatus, idempotencyKey, eventId, recipients);
                            if (idempotencyKey != null && refundTaskRepository != null) {
                                refundTaskRepository.findByIdempotencyKey(idempotencyKey)
                                        .ifPresent(t -> refundTaskRepository.markInitiated(t.getId(), refId, Instant.now()));
                            }
                        }
                        cancelBookingInBookingService(txn.getBookingRef());

                        return RefundResponse.builder()
                                .message("Refund reconciled with existing Razorpay refund")
                                .refundId(refId)
                                .refundStatus(saved.getRefundStatus())
                                .refundAmount(refundAmount)
                                .transactionRef(saved.getTransactionRef())
                                .bookingRef(saved.getBookingRef())
                                .timestamp(saved.getRefundedAt())
                                .transaction(saved)
                                .build();
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Gateway reconciliation lookup failed for payment {}: {}", txn.getRazorpayPaymentId(), ex.getMessage());
        }

        // Local recovery pre-commit (does not generate customer notification until gateway response)
        txn.setStatus("REFUND_INITIATED");
        if (idempotencyKey != null) {
            txn.setIdempotencyKey(idempotencyKey);
        }
        paymentRepository.save(txn);

        try {
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", targetPaise); // in paise
            JSONObject notes = new JSONObject();
            if (request != null && request.getReason() != null) {
                notes.put("reason", request.getReason());
            }
            notes.put("bookingRef", txn.getBookingRef());
            if (idempotencyKey != null) {
                notes.put("idempotencyKey", idempotencyKey);
            }
            refundRequest.put("notes", notes);

            Refund refund = razorpayClient.payments.refund(txn.getRazorpayPaymentId(), refundRequest);
            String refundId = refund.get("id");
            String refundStatus = refund.get("status");

            List<RecipientDto> recipients = recipientResolutionService.resolveTravellerRecipient(txn.getBookingRef(), txn.getCustomerId());
            PaymentTransaction saved;
            if ("processed".equalsIgnoreCase(refundStatus)) {
                String eventId = "PAY-EVT-REFUND-COMP-" + refundId;
                saved = stateTransitionService.recordRefundCompleted(txn, refundId, refundAmount, idempotencyKey, eventId, recipients);
                if (idempotencyKey != null && refundTaskRepository != null) {
                    refundTaskRepository.findByIdempotencyKey(idempotencyKey)
                            .ifPresent(t -> refundTaskRepository.markProcessed(t.getId(), refundId, Instant.now()));
                }
            } else {
                String eventId = "PAY-EVT-REFUND-INIT-" + refundId;
                saved = stateTransitionService.recordRefundInitiated(txn, refundId, refundAmount, refundStatus, idempotencyKey, eventId, recipients);
                if (idempotencyKey != null && refundTaskRepository != null) {
                    refundTaskRepository.findByIdempotencyKey(idempotencyKey)
                            .ifPresent(t -> refundTaskRepository.markInitiated(t.getId(), refundId, Instant.now()));
                }
            }
            log.info("Refund {} submitted for txn {}, status: {}", refundId, transactionRef, refundStatus);

            // Coordinate cancellation with Booking Service
            cancelBookingInBookingService(txn.getBookingRef());

            return RefundResponse.builder()
                    .message("Refund processed successfully via Razorpay")
                    .refundId(refundId)
                    .refundStatus(saved.getRefundStatus())
                    .refundAmount(refundAmount)
                    .transactionRef(saved.getTransactionRef())
                    .bookingRef(saved.getBookingRef())
                    .timestamp(saved.getRefundedAt())
                    .transaction(saved)
                    .build();

        } catch (Exception e) {
            log.error("Failed to process refund for txn {}: {}", transactionRef, e.getMessage(), e);
            throw new PaymentException("Gateway refund request failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public PaymentTransaction getPaymentByBooking(String bookingRef) {
        return paymentRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> new PaymentException("Payment not found for booking: " + bookingRef, HttpStatus.NOT_FOUND));
    }

    @Override
    public PaymentTransaction getPaymentByTransactionRef(String transactionRef) {
        return paymentRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new PaymentException("Payment not found for transaction: " + transactionRef, HttpStatus.NOT_FOUND));
    }

    @Override
    public Map<String, String> getPublicConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("keyId", razorpayConfig.getKeyId() != null ? razorpayConfig.getKeyId() : "");
        config.put("currency", "INR");
        return config;
    }

    @Override
    public PaymentTransaction processLegacyPayment(PaymentTransaction transaction) {
        transaction.setTransactionRef("TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        transaction.setStatus("SUCCESS");
        transaction.setTimestamp(LocalDateTime.now());
        PaymentTransaction saved = paymentRepository.save(transaction);
        confirmBookingInBookingService(saved.getBookingRef());
        return saved;
    }

    public PaymentConfirmationResult processAuthoritativePaymentSuccess(
            PaymentTransaction transaction, String paymentId, String signature) {

        // 1. Mark transaction as SUCCESS safely
        transaction.setStatus("SUCCESS");
        if (paymentId != null && !paymentId.isBlank()) {
            transaction.setRazorpayPaymentId(paymentId);
        }
        if (signature != null && !signature.isBlank()) {
            transaction.setRazorpaySignature(signature);
        }
        transaction.setTimestamp(LocalDateTime.now());
        PaymentTransaction savedTxn = paymentRepository.save(transaction);
        if (savedTxn == null) {
            savedTxn = transaction;
        }
        log.info("Payment transaction {} marked SUCCESS for bookingRef {}", savedTxn.getTransactionRef(), savedTxn.getBookingRef());

        // 2. Lookup booking in Booking Service to check status and authoritative expiresAt
        String bookingRef = savedTxn.getBookingRef();
        Map<String, Object> bookingRecord = fetchBookingRecord(bookingRef);

        if (bookingRecord != null) {
            String status = String.valueOf(bookingRecord.get("status"));
            // Idempotent: if already CONFIRMED
            if ("CONFIRMED".equalsIgnoreCase(status)) {
                log.info("Booking {} is already CONFIRMED in Booking Service", bookingRef);
                return new PaymentConfirmationResult(true, false, "Booking is already confirmed", savedTxn);
            }

            // Check if expired or cancelled or past expiresAt deadline
            boolean isExpired = "EXPIRED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status);
            if (!isExpired && bookingRecord.get("expiresAt") != null) {
                try {
                    LocalDateTime expiresAt = LocalDateTime.parse(bookingRecord.get("expiresAt").toString());
                    if (LocalDateTime.now().isAfter(expiresAt)) {
                        isExpired = true;
                    }
                } catch (Exception ignored) {}
            }

            if (isExpired) {
                log.warn("Payment succeeded for booking {} but booking is expired/cancelled (late payment). Initiating durable compensation refund.", bookingRef);
                String compIdempotencyKey = "REFUND-COMP-" + bookingRef;
                PaymentRefundTask refundTask = recordOrGetCompensationRefundTask(
                        savedTxn, "Payment captured after reservation expiry", compIdempotencyKey);
                if (refundTask != null) {
                    reconcileAndExecuteRefundTask(refundTask);
                } else {
                    try {
                        processRefund(savedTxn.getTransactionRef(), RefundRequest.builder().reason("Payment captured after reservation expiry").idempotencyKey(compIdempotencyKey).build());
                    } catch (Exception rfdEx) {
                        log.error("Failed to initiate automatic refund for late payment on txn {}: {}", savedTxn.getTransactionRef(), rfdEx.getMessage());
                    }
                }
                return new PaymentConfirmationResult(false, true, "Payment captured, but reservation expired. Automated refund initiated.", savedTxn);
            }
        }

        // 3. Invoke authoritative confirmation in Booking Service
        try {
            confirmBookingInBookingService(savedTxn.getBookingRef());
            log.info("Authoritatively confirmed booking {} in Booking Service", bookingRef);
            return new PaymentConfirmationResult(true, false, "Payment verified and booking confirmed", savedTxn);
        } catch (org.springframework.web.client.HttpStatusCodeException hsce) {
            log.warn("Booking Service confirmation returned status {} for bookingRef {}: {}",
                    hsce.getStatusCode(), bookingRef, hsce.getResponseBodyAsString());

            if (hsce.getStatusCode() == HttpStatus.CONFLICT || hsce.getStatusCode() == HttpStatus.BAD_REQUEST) {
                // Room hold expired / allocated to another traveller or booking expired!
                log.warn("Booking confirmation rejected by Booking Service (conflict/expired). Initiating durable compensation refund for txn {}", savedTxn.getTransactionRef());
                String compIdempotencyKey = "REFUND-COMP-" + bookingRef;
                PaymentRefundTask refundTask = recordOrGetCompensationRefundTask(
                        savedTxn, "Inventory unavailable or reservation expired", compIdempotencyKey);
                if (refundTask != null) {
                    reconcileAndExecuteRefundTask(refundTask);
                } else {
                    try {
                        processRefund(savedTxn.getTransactionRef(), RefundRequest.builder().reason("Inventory unavailable or reservation expired").idempotencyKey(compIdempotencyKey).build());
                    } catch (Exception rfdEx) {
                        log.error("Failed to initiate automatic refund for txn {}: {}", savedTxn.getTransactionRef(), rfdEx.getMessage());
                    }
                }
                return new PaymentConfirmationResult(false, true, "Room hold expired and inventory unavailable. Automated refund initiated.", savedTxn);
            }
            // 5xx is a transient server error: rethrow so callers (e.g. webhook inbox) can retry!
            throw hsce;
        } catch (Exception ex) {
            log.error("Unexpected error confirming booking {} in Booking Service: {}", bookingRef, ex.getMessage(), ex);
            throw ex;
        }
    }

    private Map<String, Object> fetchBookingRecord(String bookingRef) {
        String bookingLookupUrl = bookingServiceUrl + "/api/bookings/ref/" + bookingRef;
        try {
            ResponseEntity<Map> lookup = restTemplate.getForEntity(bookingLookupUrl, Map.class);
            if (lookup != null && lookup.getStatusCode() == HttpStatus.OK && lookup.getBody() != null) {
                return (Map<String, Object>) lookup.getBody();
            }
        } catch (Exception ignored) {}

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalTokenService != null) {
                try {
                    String token = internalTokenService.generateInternalToken();
                    headers.set("Authorization", "Bearer " + token);
                    headers.set("X-Internal-Token", token);
                    headers.set("X-Internal-Service", "payment-service");
                } catch (Exception ignored) {}
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> lookup = restTemplate.exchange(bookingLookupUrl, HttpMethod.GET, entity, Map.class);
            if (lookup != null && lookup.getStatusCode() == HttpStatus.OK && lookup.getBody() != null) {
                return (Map<String, Object>) lookup.getBody();
            }
        } catch (Exception e) {
            log.debug("Booking lookup for {} encountered: {}", bookingRef, e.getMessage());
        }
        return null;
    }

    private void confirmBookingInBookingService(String bookingRef) {
        String confirmUrl = bookingServiceUrl + "/api/bookings/ref/" + bookingRef + "/confirm";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (internalTokenService != null) {
            try {
                String internalToken = internalTokenService.generateInternalToken();
                headers.set("Authorization", "Bearer " + internalToken);
                headers.set("X-Internal-Token", internalToken);
                headers.set("X-Internal-Service", "payment-service");
            } catch (Exception ignored) {}
        }

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("reason", "Payment verified via Razorpay"), headers);
        try {
            restTemplate.exchange(confirmUrl, HttpMethod.PUT, request, Map.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nfe) {
            // Fall back to ID-based confirm if ref endpoint not matched
            try {
                Map<String, Object> record = fetchBookingRecord(bookingRef);
                if (record != null && record.get("id") != null) {
                    String idConfirmUrl = bookingServiceUrl + "/api/bookings/" + record.get("id") + "/confirm";
                    restTemplate.exchange(idConfirmUrl, HttpMethod.PUT, request, Map.class);
                    return;
                }
            } catch (Exception e) {
                log.error("Failed fallback confirmation for {}: {}", bookingRef, e.getMessage());
            }
            throw nfe;
        }
    }

    private void cancelBookingInBookingService(String bookingRef) {
        try {
            Map<String, Object> lookupBody = fetchBookingRecord(bookingRef);
            if (lookupBody != null && lookupBody.get("id") != null) {
                Long bookingId = Long.valueOf(lookupBody.get("id").toString());
                String status = (String) lookupBody.get("status");
                if ("CANCELLED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
                    log.info("Booking {} is already in terminal state {}, skipping cancel PUT", bookingRef, status);
                    return;
                }
                String cancelUrl = bookingServiceUrl + "/api/bookings/" + bookingId + "/cancel";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (internalTokenService != null) {
                    try {
                        String internalToken = internalTokenService.generateInternalToken();
                        headers.set("Authorization", "Bearer " + internalToken);
                        headers.set("X-Internal-Token", internalToken);
                        headers.set("X-Internal-Service", "payment-service");
                    } catch (Exception ignored) {}
                }
                HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("reason", "Refund processed via Razorpay"), headers);
                restTemplate.exchange(cancelUrl, HttpMethod.PUT, request, Map.class);
                log.info("Coordinated cancellation for booking {} in Booking Service", bookingRef);
            }
        } catch (Exception e) {
            log.error("Failed to cancel booking {} in booking-service: {}", bookingRef, e.getMessage());
        }
    }

    @Override
    public PaymentStatsResponse getAdminStats() {
        String currency = "INR";
        BigDecimal capturedBd = paymentRepository.sumSuccessfulCapturedCollections(currency);
        BigDecimal refundedBd = paymentRepository.sumProcessedRefunds(currency);

        if (capturedBd == null) {
            capturedBd = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            capturedBd = capturedBd.setScale(2, RoundingMode.HALF_UP);
        }

        if (refundedBd == null) {
            refundedBd = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            refundedBd = refundedBd.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal netBd = capturedBd.subtract(refundedBd).setScale(2, RoundingMode.HALF_UP);

        log.info("WP-04 Admin Stats: Captured={}, Refunded={}, Net={} ({})",
                capturedBd, refundedBd, netBd, currency);

        return PaymentStatsResponse.builder()
                .currency(currency)
                .successfulCapturedCollections(capturedBd)
                .processedRefunds(refundedBd)
                .netCollections(netBd)
                .build();
    }

    @Override
    public List<DailyRevenueTrendResponse> getAdminRevenueTrends(int days) {
        String currency = "INR";
        int boundedDays = Math.max(1, Math.min(90, days));
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(boundedDays - 1);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = today.atTime(23, 59, 59, 999999999);

        // Pre-populate continuous timeline maps with zero values
        Map<LocalDate, BigDecimal> capturedMap = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> refundedMap = new LinkedHashMap<>();
        for (int i = 0; i < boundedDays; i++) {
            LocalDate d = startDate.plusDays(i);
            capturedMap.put(d, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            refundedMap.put(d, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        List<Object[]> capturedResults = paymentRepository.countDailyCapturedNative(startDateTime, endDateTime, currency);
        for (Object[] row : capturedResults) {
            if (row != null && row.length >= 2 && row[0] != null) {
                LocalDate dt = parseLocalDate(row[0]);
                if (dt != null && capturedMap.containsKey(dt)) {
                    capturedMap.put(dt, parseBigDecimal(row[1]));
                }
            }
        }

        List<Object[]> refundedResults = paymentRepository.countDailyRefundedNative(startDateTime, endDateTime, currency);
        for (Object[] row : refundedResults) {
            if (row != null && row.length >= 2 && row[0] != null) {
                LocalDate dt = parseLocalDate(row[0]);
                if (dt != null && refundedMap.containsKey(dt)) {
                    refundedMap.put(dt, parseBigDecimal(row[1]));
                }
            }
        }

        List<DailyRevenueTrendResponse> response = new ArrayList<>();
        for (LocalDate date : capturedMap.keySet()) {
            BigDecimal capturedAmt = capturedMap.get(date);
            BigDecimal refundedAmt = refundedMap.get(date);
            BigDecimal netAmt = capturedAmt.subtract(refundedAmt).setScale(2, RoundingMode.HALF_UP);

            response.add(DailyRevenueTrendResponse.builder()
                    .date(date)
                    .capturedCollections(capturedAmt)
                    .processedRefunds(refundedAmt)
                    .netCollections(netAmt)
                    .currency(currency)
                    .build());
        }

        return response;
    }

    @Override
    public HotelRevenueReportResponse getHotelRevenueSummary(HotelRevenueReportRequest request, String callerRole, Long callerUserId) {
        if (request == null) {
            request = new HotelRevenueReportRequest();
        }
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now().minusDays(6);
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : LocalDate.now();
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        List<String> bookingRefs = request.getBookingRefs();
        Map<LocalDate, Double> dailyPayments = new LinkedHashMap<>();
        Map<LocalDate, Double> dailyRefunds = new LinkedHashMap<>();

        LocalDate cur = startDate;
        while (!cur.isAfter(endDate)) {
            dailyPayments.put(cur, 0.0);
            dailyRefunds.put(cur, 0.0);
            cur = cur.plusDays(1);
        }

        double totalCaptured = 0.0;
        int paymentsCount = 0;
        double totalRefunded = 0.0;
        int refundsCount = 0;

        if (bookingRefs != null && !bookingRefs.isEmpty()) {
            List<PaymentTransaction> txns = paymentRepository.findByBookingRefIn(bookingRefs);
            for (PaymentTransaction txn : txns) {
                if (txn == null) continue;

                // 1. Eligible captured payment (SUCCESS, REFUND_INITIATED, REFUNDED)
                String status = txn.getStatus() != null ? txn.getStatus().toUpperCase() : "";
                boolean isSuccessfulPayment = "SUCCESS".equals(status) || "REFUND_INITIATED".equals(status) || "REFUNDED".equals(status);
                if (isSuccessfulPayment && txn.getTimestamp() != null) {
                    LocalDate captureDate = txn.getTimestamp().toLocalDate();
                    if (!captureDate.isBefore(startDate) && !captureDate.isAfter(endDate)) {
                        double amount = txn.getAmount() != null ? txn.getAmount() : 0.0;
                        totalCaptured += amount;
                        paymentsCount++;
                        dailyPayments.put(captureDate, dailyPayments.getOrDefault(captureDate, 0.0) + amount);
                    }
                }

                // 2. Processed refund attributed strictly to refundedAt
                boolean isProcessedRefund = "PROCESSED".equalsIgnoreCase(txn.getRefundStatus()) || ("REFUNDED".equalsIgnoreCase(status) && txn.getRefundedAt() != null);
                if (isProcessedRefund && txn.getRefundedAt() != null) {
                    LocalDate refundDate = txn.getRefundedAt().toLocalDate();
                    if (!refundDate.isBefore(startDate) && !refundDate.isAfter(endDate)) {
                        double rAmt = txn.getRefundAmount() != null
                                ? txn.getRefundAmount()
                                : ("REFUNDED".equalsIgnoreCase(txn.getStatus()) && txn.getAmount() != null ? txn.getAmount() : 0.0);
                        totalRefunded += rAmt;
                        refundsCount++;
                        dailyRefunds.put(refundDate, dailyRefunds.getOrDefault(refundDate, 0.0) + rAmt);
                    }
                }
            }
        }

        List<HotelRevenueReportResponse.DailyRevenueItem> dailyBreakdown = new ArrayList<>();
        for (LocalDate date : dailyPayments.keySet()) {
            double pAmt = dailyPayments.get(date);
            double rAmt = dailyRefunds.get(date);
            double net = pAmt - rAmt;
            dailyBreakdown.add(HotelRevenueReportResponse.DailyRevenueItem.builder()
                    .date(date)
                    .paymentAmount(pAmt)
                    .refundAmount(rAmt)
                    .netRevenue(net)
                    .build());
        }

        double netRevenue = totalCaptured - totalRefunded;

        return HotelRevenueReportResponse.builder()
                .hotelId(request.getHotelId())
                .startDate(startDate)
                .endDate(endDate)
                .successfulPaymentsAmount(totalCaptured)
                .successfulPaymentsCount(paymentsCount)
                .processedRefundsAmount(totalRefunded)
                .processedRefundsCount(refundsCount)
                .netRevenue(netRevenue)
                .dailyBreakdown(dailyBreakdown)
                .build();
    }


    private BigDecimal parseBigDecimal(Object obj) {
        if (obj == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (obj instanceof BigDecimal) {
            return ((BigDecimal) obj).setScale(2, RoundingMode.HALF_UP);
        }
        if (obj instanceof Number) {
            return BigDecimal.valueOf(((Number) obj).doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(obj.toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private LocalDate parseLocalDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof java.sql.Date) {
            return ((java.sql.Date) obj).toLocalDate();
        } else if (obj instanceof LocalDate) {
            return (LocalDate) obj;
        } else {
            try {
                return LocalDate.parse(obj.toString().substring(0, 10));
            } catch (Exception e) {
                return null;
            }
        }
    }

    public PaymentRefundTask recordOrGetCompensationRefundTask(
            PaymentTransaction txn, String reason, String idempotencyKey) {

        if (refundTaskRepository == null) {
            return null;
        }

        return refundTaskRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> {
                    PaymentRefundTask newTask = PaymentRefundTask.builder()
                            .bookingRef(txn.getBookingRef())
                            .transactionRef(txn.getTransactionRef())
                            .razorpayPaymentId(txn.getRazorpayPaymentId())
                            .amount(txn.getAmount())
                            .reason(reason)
                            .idempotencyKey(idempotencyKey)
                            .status("PENDING")
                            .attemptCount(0)
                            .maxAttempts(5)
                            .build();
                    try {
                        return refundTaskRepository.save(newTask);
                    } catch (DataIntegrityViolationException dive) {
                        return refundTaskRepository.findByIdempotencyKey(idempotencyKey).orElse(newTask);
                    }
                });
    }

    public void reconcileAndExecuteRefundTask(PaymentRefundTask task) {
        if (task == null) {
            return;
        }

        String bookingRef = task.getBookingRef();
        String transactionRef = task.getTransactionRef();
        String paymentId = task.getRazorpayPaymentId();
        double amount = task.getAmount();
        String idempotencyKey = task.getIdempotencyKey();
        boolean alreadyInitiated = "INITIATED".equalsIgnoreCase(task.getStatus())
                || (task.getRazorpayRefundId() != null && !task.getRazorpayRefundId().isBlank());

        PaymentTransaction txn = paymentRepository.findByTransactionRef(transactionRef)
                .orElse(null);
        if (txn == null) {
            log.error("Transaction not found for refund task ID {}: {}", task.getId(), transactionRef);
            if (refundTaskRepository != null) {
                refundTaskRepository.markFailedFinal(task.getId(), "Transaction not found: " + transactionRef, Instant.now());
            }
            return;
        }

        if (paymentId == null || paymentId.isBlank()) {
            paymentId = txn.getRazorpayPaymentId();
        }

        if (txn.getRefundId() != null && !txn.getRefundId().isBlank()) {
            alreadyInitiated = true;
        }

        // 1. Local transaction status check
        if ("REFUNDED".equalsIgnoreCase(txn.getStatus()) && txn.getRefundId() != null) {
            log.info("Transaction {} already REFUNDED locally with refundId {}", transactionRef, txn.getRefundId());
            if (refundTaskRepository != null) {
                refundTaskRepository.markProcessed(task.getId(), txn.getRefundId(), Instant.now());
            }
            return;
        }

        long targetPaise = Math.round(amount * 100);

        // 2. Gateway reconciliation: check if Razorpay already processed or initiated this refund
        if (paymentId != null && !paymentId.isBlank() && razorpayClient != null && razorpayConfig != null && razorpayConfig.isConfigured()) {
            try {
                List<Refund> existingRefunds = razorpayClient.payments.fetchAllRefunds(paymentId);
                if (existingRefunds != null && !existingRefunds.isEmpty()) {
                    for (Refund ref : existingRefunds) {
                        long refPaise = ref.get("amount") instanceof Number ? ((Number) ref.get("amount")).longValue() : 0L;
                        String refId = ref.get("id");
                        String refStatus = ref.get("status");

                        boolean matches = (task.getRazorpayRefundId() != null && task.getRazorpayRefundId().equals(refId))
                                || (txn.getRefundId() != null && txn.getRefundId().equals(refId))
                                || (refPaise == targetPaise);

                        if (matches) {
                            log.info("Reconciled existing Razorpay refund {} with status {} for task ID {}",
                                    refId, refStatus, task.getId());

                            List<RecipientDto> recipients = recipientResolutionService.resolveTravellerRecipient(txn.getBookingRef(), txn.getCustomerId());
                            if ("processed".equalsIgnoreCase(refStatus)) {
                                stateTransitionService.recordRefundCompleted(txn, refId, amount, idempotencyKey, "PAY-EVT-REFUND-COMP-" + refId, recipients);
                                if (refundTaskRepository != null) {
                                    refundTaskRepository.markProcessed(task.getId(), refId, Instant.now());
                                }
                            } else {
                                stateTransitionService.recordRefundInitiated(txn, refId, amount, refStatus, idempotencyKey, "PAY-EVT-REFUND-INIT-" + refId, recipients);
                                if (refundTaskRepository != null) {
                                    refundTaskRepository.markInitiated(task.getId(), refId, Instant.now());
                                }
                            }
                            cancelBookingInBookingService(bookingRef);
                            return;
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Gateway reconciliation lookup failed for payment {} on task ID {}: {}",
                        paymentId, task.getId(), ex.getMessage());

                // If refund was already initiated, handle temporary status-retrieval failure without marking PROCESSED or creating another refund
                if (alreadyInitiated && refundTaskRepository != null) {
                    Instant nextRetry = Instant.now().plusSeconds(15);
                    refundTaskRepository.releaseInitiatedTask(
                            task.getId(),
                            "Temporary status retrieval error: " + ex.getMessage(),
                            nextRetry,
                            Instant.now()
                    );
                    return;
                }
            }
        }

        // If this refund was already initiated with Razorpay, do NOT call payments.refund()! Keep it in INITIATED.
        if (alreadyInitiated) {
            log.warn("Refund task ID {} was already initiated with Razorpay (refundId: {}), but matching refund was not found in active list. Preserving INITIATED status.",
                    task.getId(), task.getRazorpayRefundId() != null ? task.getRazorpayRefundId() : txn.getRefundId());
            if (refundTaskRepository != null) {
                String existingRefId = task.getRazorpayRefundId() != null ? task.getRazorpayRefundId() : txn.getRefundId();
                refundTaskRepository.markInitiated(task.getId(), existingRefId, Instant.now());
            }
            return;
        }

        // 3. Initiate refund with Razorpay (only for uninitiated tasks)
        if (razorpayClient == null || razorpayConfig == null || !razorpayConfig.isConfigured()) {
            String err = "Razorpay gateway is not configured on the server";
            log.error("Cannot execute refund task ID {}: {}", task.getId(), err);
            if (refundTaskRepository != null) {
                Instant nextRetry = Instant.now().plusSeconds(60);
                refundTaskRepository.markFailedRetryable(task.getId(), err, nextRetry, Instant.now());
            }
            return;
        }

        try {
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", targetPaise);
            JSONObject notes = new JSONObject();
            notes.put("bookingRef", bookingRef);
            notes.put("transactionRef", transactionRef);
            notes.put("idempotencyKey", idempotencyKey);
            if (task.getReason() != null) {
                notes.put("reason", task.getReason());
            }
            refundRequest.put("notes", notes);

            Refund refund = razorpayClient.payments.refund(paymentId, refundRequest);
            String refundId = refund.get("id");
            String refundStatus = refund.get("status");

            log.info("Successfully executed refund {} with status {} for task ID {}", refundId, refundStatus, task.getId());

            List<RecipientDto> recipients = recipientResolutionService.resolveTravellerRecipient(txn.getBookingRef(), txn.getCustomerId());
            if ("processed".equalsIgnoreCase(refundStatus)) {
                stateTransitionService.recordRefundCompleted(txn, refundId, amount, idempotencyKey, "PAY-EVT-REFUND-COMP-" + refundId, recipients);
                if (refundTaskRepository != null) {
                    refundTaskRepository.markProcessed(task.getId(), refundId, Instant.now());
                }
            } else {
                stateTransitionService.recordRefundInitiated(txn, refundId, amount, refundStatus, idempotencyKey, "PAY-EVT-REFUND-INIT-" + refundId, recipients);
                if (refundTaskRepository != null) {
                    refundTaskRepository.markInitiated(task.getId(), refundId, Instant.now());
                }
            }
            cancelBookingInBookingService(bookingRef);

        } catch (Exception rfdEx) {
            log.error("Failed to execute refund with Razorpay for task ID {}: {}", task.getId(), rfdEx.getMessage());

            // Post-exception gateway reconciliation: check if the refund was actually registered despite the timeout/error
            boolean reconciled = false;
            try {
                List<Refund> existingRefunds = razorpayClient.payments.fetchAllRefunds(paymentId);
                if (existingRefunds != null && !existingRefunds.isEmpty()) {
                    for (Refund ref : existingRefunds) {
                        long refPaise = ref.get("amount") instanceof Number ? ((Number) ref.get("amount")).longValue() : 0L;
                        if (refPaise == targetPaise) {
                            String refId = ref.get("id");
                            String refStatus = ref.get("status");
                            log.info("Post-exception reconciliation: Found matching refund {} on Razorpay for task ID {}", refId, task.getId());
                            List<RecipientDto> recipients = recipientResolutionService.resolveTravellerRecipient(txn.getBookingRef(), txn.getCustomerId());
                            if ("processed".equalsIgnoreCase(refStatus)) {
                                stateTransitionService.recordRefundCompleted(txn, refId, amount, idempotencyKey, "PAY-EVT-REFUND-COMP-" + refId, recipients);
                                if (refundTaskRepository != null) {
                                    refundTaskRepository.markProcessed(task.getId(), refId, Instant.now());
                                }
                            } else {
                                stateTransitionService.recordRefundInitiated(txn, refId, amount, refStatus, idempotencyKey, "PAY-EVT-REFUND-INIT-" + refId, recipients);
                                if (refundTaskRepository != null) {
                                    refundTaskRepository.markInitiated(task.getId(), refId, Instant.now());
                                }
                            }
                            cancelBookingInBookingService(bookingRef);
                            reconciled = true;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}

            if (!reconciled && refundTaskRepository != null) {
                int attempts = task.getAttemptCount() != null ? task.getAttemptCount() : 1;
                if (attempts >= task.getMaxAttempts()) {
                    refundTaskRepository.markFailedFinal(task.getId(), rfdEx.getMessage(), Instant.now());
                } else {
                    long backoffSec = (long) (15 * Math.pow(2, Math.min(attempts - 1, 5)));
                    Instant nextRetry = Instant.now().plusSeconds(backoffSec);
                    refundTaskRepository.markFailedRetryable(task.getId(), rfdEx.getMessage(), nextRetry, Instant.now());
                }
            }
        }
    }

}
