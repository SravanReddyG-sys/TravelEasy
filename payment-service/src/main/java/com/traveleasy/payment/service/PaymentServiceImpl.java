package com.traveleasy.payment.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Refund;
import com.razorpay.Utils;
import com.traveleasy.payment.config.RazorpayConfig;
import com.traveleasy.payment.dto.*;
import com.traveleasy.payment.exception.PaymentException;
import com.traveleasy.payment.model.PaymentTransaction;
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
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final RazorpayConfig razorpayConfig;
    private final RazorpayClient razorpayClient;
    private final RestTemplate restTemplate;
    private final PaymentStateTransitionService stateTransitionService;
    private final PaymentRecipientResolutionService recipientResolutionService;
    private final PaymentWebhookEventRepository webhookEventRepository;

    @Override
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        if (!razorpayConfig.isConfigured() || razorpayClient == null) {
            log.error("Cannot create order: Razorpay credentials are not configured");
            throw new PaymentException("Razorpay payment gateway is not configured on the server", HttpStatus.SERVICE_UNAVAILABLE);
        }

        // 1. Verify booking existence & cross-check total amount to prevent tampering
        verifyBookingAmount(request.getBookingRef(), request.getAmount());

        // 2. Check for existing successful payment on this booking
        Optional<PaymentTransaction> existingSuccess = paymentRepository.findByBookingRef(request.getBookingRef());
        if (existingSuccess.isPresent() && "SUCCESS".equalsIgnoreCase(existingSuccess.get().getStatus())) {
            log.warn("Booking {} has already been paid successfully", request.getBookingRef());
            throw new PaymentException("Booking is already paid and confirmed", HttpStatus.CONFLICT);
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

            PaymentTransaction transaction = existingSuccess.orElse(new PaymentTransaction());
            transaction.setTransactionRef(txnRef);
            transaction.setBookingRef(request.getBookingRef());
            transaction.setCustomerId(request.getCustomerId());
            transaction.setAmount(request.getAmount());
            transaction.setCurrency("INR");
            transaction.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "RAZORPAY");
            transaction.setStatus("CREATED");
            transaction.setRazorpayOrderId(orderId);
            transaction.setTimestamp(LocalDateTime.now());

            paymentRepository.save(transaction);
            log.info("Created Razorpay order {} for booking {}", orderId, request.getBookingRef());

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

    private void verifyBookingAmount(String bookingRef, Double requestedAmount) {
        try {
            String bookingUrl = "http://localhost:8084/api/bookings/ref/" + bookingRef;
            ResponseEntity<Map> response = restTemplate.getForEntity(bookingUrl, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map booking = response.getBody();
                Object totalAmtObj = booking.get("totalAmount");
                if (totalAmtObj != null) {
                    double expectedAmount = Double.parseDouble(totalAmtObj.toString());
                    if (Math.abs(expectedAmount - requestedAmount) > 0.01) {
                        log.error("Amount tampering detected! Expected: {}, Received: {}", expectedAmount, requestedAmount);
                        throw new PaymentException("Amount mismatch with booking record", HttpStatus.BAD_REQUEST);
                    }
                }
            }
        } catch (PaymentException pe) {
            throw pe;
        } catch (Exception e) {
            log.warn("Could not verify booking amount with Booking Service: {}", e.getMessage());
        }
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

            // Update status to SUCCESS in short local transaction
            transaction.setStatus("SUCCESS");
            transaction.setRazorpayPaymentId(request.getRazorpayPaymentId());
            transaction.setRazorpaySignature(request.getRazorpaySignature());
            PaymentTransaction saved = paymentRepository.save(transaction);
            log.info("Payment successfully verified for booking {} with paymentId {}", request.getBookingRef(), request.getRazorpayPaymentId());

            // Server-to-server authoritative booking confirmation (outside local DB transaction)
            confirmBookingInBookingService(saved.getBookingRef());

            return PaymentVerificationResponse.builder()
                    .verified(true)
                    .message("Payment verified successfully")
                    .bookingRef(saved.getBookingRef())
                    .transactionRef(saved.getTransactionRef())
                    .paymentId(saved.getRazorpayPaymentId())
                    .status("SUCCESS")
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
            if (!"SUCCESS".equalsIgnoreCase(txn.getStatus())) {
                txn.setStatus("SUCCESS");
                txn.setRazorpayPaymentId(paymentId);
                paymentRepository.save(txn);
                log.info("Webhook updated transaction {} to SUCCESS", txn.getTransactionRef());
                confirmBookingInBookingService(txn.getBookingRef());
            }
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
                        } else {
                            String eventId = "PAY-EVT-REFUND-INIT-" + refId;
                            saved = stateTransitionService.recordRefundInitiated(txn, refId, refundAmount, refStatus, idempotencyKey, eventId, recipients);
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
            } else {
                String eventId = "PAY-EVT-REFUND-INIT-" + refundId;
                saved = stateTransitionService.recordRefundInitiated(txn, refundId, refundAmount, refundStatus, idempotencyKey, eventId, recipients);
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

    private void confirmBookingInBookingService(String bookingRef) {
        try {
            String bookingLookupUrl = "http://localhost:8084/api/bookings/ref/" + bookingRef;
            ResponseEntity<Map> lookup = restTemplate.getForEntity(bookingLookupUrl, Map.class);
            if (lookup.getStatusCode() == HttpStatus.OK && lookup.getBody() != null) {
                Long bookingId = Long.valueOf(lookup.getBody().get("id").toString());
                String status = (String) lookup.getBody().get("status");
                if ("CONFIRMED".equalsIgnoreCase(status)) {
                    log.info("Booking {} is already CONFIRMED in booking-service", bookingRef);
                    return;
                }
                String confirmUrl = "http://localhost:8084/api/bookings/" + bookingId + "/confirm";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("reason", "Payment verified via Razorpay"), headers);
                restTemplate.exchange(confirmUrl, HttpMethod.PUT, request, Map.class);
                log.info("Confirmed booking {} via Booking Service", bookingRef);
            }
        } catch (Exception e) {
            log.error("Failed to automatically confirm booking {} in booking-service: {}", bookingRef, e.getMessage());
        }
    }

    private void cancelBookingInBookingService(String bookingRef) {
        try {
            String bookingLookupUrl = "http://localhost:8084/api/bookings/ref/" + bookingRef;
            ResponseEntity<Map> lookup = restTemplate.getForEntity(bookingLookupUrl, Map.class);
            if (lookup.getStatusCode() == HttpStatus.OK && lookup.getBody() != null) {
                Long bookingId = Long.valueOf(lookup.getBody().get("id").toString());
                String status = (String) lookup.getBody().get("status");
                if ("CANCELLED".equalsIgnoreCase(status)) {
                    log.info("Booking {} is already CANCELLED in booking-service, skipping cancel PUT", bookingRef);
                    return;
                }
                String cancelUrl = "http://localhost:8084/api/bookings/" + bookingId + "/cancel";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
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
}
