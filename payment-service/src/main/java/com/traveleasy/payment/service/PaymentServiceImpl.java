package com.traveleasy.payment.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Refund;
import com.razorpay.Utils;
import com.traveleasy.payment.config.RazorpayConfig;
import com.traveleasy.payment.dto.*;
import com.traveleasy.payment.exception.PaymentException;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final RazorpayConfig razorpayConfig;
    private final RazorpayClient razorpayClient;
    private final RestTemplate restTemplate;

    @Override
    @Transactional
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

        } catch (Exception e) {
            log.error("Failed to create Razorpay order: {}", e.getMessage(), e);
            throw new PaymentException("Failed to initiate gateway order: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    @Transactional
    public PaymentVerificationResponse verifyPayment(VerifyPaymentRequest request) {
        if (!razorpayConfig.isConfigured()) {
            throw new PaymentException("Razorpay gateway is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }

        PaymentTransaction transaction = paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .or(() -> paymentRepository.findByBookingRef(request.getBookingRef()))
                .orElseThrow(() -> new PaymentException("No transaction found for order ID: " + request.getRazorpayOrderId(), HttpStatus.NOT_FOUND));

        // Idempotency: If already verified, return verified state immediately
        if ("SUCCESS".equalsIgnoreCase(transaction.getStatus())) {
            log.info("Payment already verified for booking {}", request.getBookingRef());
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
                log.error("Signature verification failed for order {}", request.getRazorpayOrderId());
                transaction.setStatus("FAILED");
                transaction.setErrorCode("SIGNATURE_MISMATCH");
                transaction.setErrorDescription("Computed HMAC signature does not match gateway signature");
                paymentRepository.save(transaction);
                throw new PaymentException("Payment signature verification failed", HttpStatus.BAD_REQUEST);
            }

            // Update status to SUCCESS
            transaction.setStatus("SUCCESS");
            transaction.setRazorpayPaymentId(request.getRazorpayPaymentId());
            transaction.setRazorpaySignature(request.getRazorpaySignature());
            PaymentTransaction saved = paymentRepository.save(transaction);
            log.info("Payment successfully verified for booking {} with paymentId {}", request.getBookingRef(), request.getRazorpayPaymentId());

            // Server-to-server authoritative booking confirmation
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
            transaction.setStatus("FAILED");
            transaction.setErrorCode("VERIFICATION_EXCEPTION");
            transaction.setErrorDescription(e.getMessage());
            paymentRepository.save(transaction);
            throw new PaymentException("Payment verification encountered an error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
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

            JSONObject payloadObj = json.optJSONObject("payload");
            if (payloadObj == null) return;

            if ("payment.captured".equalsIgnoreCase(event) || "order.paid".equalsIgnoreCase(event)) {
                handlePaymentCapturedWebhook(payloadObj);
            } else if ("payment.failed".equalsIgnoreCase(event)) {
                handlePaymentFailedWebhook(payloadObj);
            } else if ("refund.processed".equalsIgnoreCase(event)) {
                handleRefundProcessedWebhook(payloadObj);
            }

        } catch (PaymentException pe) {
            throw pe;
        } catch (Exception e) {
            log.error("Failed to process webhook: {}", e.getMessage(), e);
            throw new PaymentException("Webhook processing error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
        String errorCode = entity.optString("error_code");
        String errorDesc = entity.optString("error_description");

        paymentRepository.findByRazorpayOrderId(orderId).ifPresent(txn -> {
            if (!"SUCCESS".equalsIgnoreCase(txn.getStatus())) {
                txn.setStatus("FAILED");
                txn.setErrorCode(errorCode);
                txn.setErrorDescription(errorDesc);
                paymentRepository.save(txn);
                log.info("Webhook recorded payment failure for order {}: {}", orderId, errorDesc);
            }
        });
    }

    private void handleRefundProcessedWebhook(JSONObject payloadObj) {
        JSONObject refundObj = payloadObj.optJSONObject("refund");
        JSONObject entity = refundObj != null ? refundObj.optJSONObject("entity") : null;
        if (entity == null) return;

        String refundId = entity.optString("id");
        String paymentId = entity.optString("payment_id");

        paymentRepository.findByRazorpayPaymentId(paymentId).ifPresent(txn -> {
            txn.setStatus("REFUNDED");
            txn.setRefundId(refundId);
            txn.setRefundStatus("PROCESSED");
            txn.setRefundedAt(LocalDateTime.now());
            paymentRepository.save(txn);
            log.info("Webhook updated refund status to PROCESSED for transaction {}", txn.getTransactionRef());
        });
    }

    @Override
    @Transactional
    public RefundResponse processRefundByBooking(String bookingRef, RefundRequest request) {
        PaymentTransaction txn = paymentRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> new PaymentException("Transaction not found for booking: " + bookingRef, HttpStatus.NOT_FOUND));
        return processRefund(txn.getTransactionRef(), request);
    }

    @Override
    @Transactional
    public RefundResponse processRefund(String transactionRef, RefundRequest request) {
        PaymentTransaction txn = paymentRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new PaymentException("Transaction not found for ref: " + transactionRef, HttpStatus.NOT_FOUND));

        // Idempotency check: Already refunded
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

        if (!"SUCCESS".equalsIgnoreCase(txn.getStatus()) && !"REFUND_INITIATED".equalsIgnoreCase(txn.getStatus())) {
            throw new PaymentException("Transaction cannot be refunded because status is " + txn.getStatus() + ". Only successful payments can be refunded.", HttpStatus.BAD_REQUEST);
        }

        double refundAmount = (request != null && request.getAmount() != null && request.getAmount() > 0)
                ? request.getAmount()
                : txn.getAmount();

        if (refundAmount > txn.getAmount()) {
            throw new PaymentException("Refund amount cannot exceed original transaction amount", HttpStatus.BAD_REQUEST);
        }

        // Support non-gateway/mock transactions gracefully
        if (txn.getRazorpayPaymentId() == null || txn.getRazorpayPaymentId().isBlank()) {
            log.info("Processing non-gateway refund for transaction {}", transactionRef);
            String mockRefundId = "rfd_mock_" + UUID.randomUUID().toString().substring(0, 8);
            txn.setRefundId(mockRefundId);
            txn.setRefundAmount(refundAmount);
            txn.setRefundStatus("PROCESSED");
            txn.setStatus("REFUNDED");
            txn.setRefundedAt(LocalDateTime.now());
            PaymentTransaction saved = paymentRepository.save(txn);
            cancelBookingInBookingService(txn.getBookingRef());
            return RefundResponse.builder()
                    .message("Refund processed successfully")
                    .refundId(mockRefundId)
                    .refundStatus("PROCESSED")
                    .refundAmount(refundAmount)
                    .transactionRef(saved.getTransactionRef())
                    .bookingRef(saved.getBookingRef())
                    .timestamp(saved.getRefundedAt())
                    .transaction(saved)
                    .build();
        }

        try {
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", Math.round(refundAmount * 100)); // in paise
            if (request != null && request.getReason() != null) {
                JSONObject notes = new JSONObject();
                notes.put("reason", request.getReason());
                notes.put("bookingRef", txn.getBookingRef());
                refundRequest.put("notes", notes);
            }

            Refund refund = razorpayClient.payments.refund(txn.getRazorpayPaymentId(), refundRequest);
            String refundId = refund.get("id");
            String refundStatus = refund.get("status");

            txn.setRefundId(refundId);
            txn.setRefundAmount(refundAmount);
            txn.setRefundStatus(refundStatus != null ? refundStatus.toUpperCase() : "PROCESSED");
            txn.setStatus("processed".equalsIgnoreCase(refundStatus) ? "REFUNDED" : "REFUND_INITIATED");
            txn.setRefundedAt(LocalDateTime.now());
            PaymentTransaction saved = paymentRepository.save(txn);
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
            log.error("Failed to process refund: {}", e.getMessage(), e);
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
    @Transactional
    public PaymentTransaction processLegacyPayment(PaymentTransaction transaction) {
        transaction.setTransactionRef("TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        transaction.setStatus("SUCCESS");
        transaction.setTimestamp(LocalDateTime.now());
        PaymentTransaction saved = paymentRepository.save(transaction);
        confirmBookingInBookingService(saved.getBookingRef());
        return saved;
    }

    private void verifyBookingAmount(String bookingRef, Double requestedAmount) {
        try {
            String url = razorpayConfig.getBookingServiceUrl() + "/api/bookings/ref/" + bookingRef;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object totalAmountObj = response.getBody().get("totalAmount");
                if (totalAmountObj != null) {
                    double expectedAmount = Double.parseDouble(totalAmountObj.toString());
                    if (Math.abs(expectedAmount - requestedAmount) > 0.01) {
                        log.warn("Amount mismatch for {}: expected {}, received {}", bookingRef, expectedAmount, requestedAmount);
                        throw new PaymentException("Amount mismatch: expected " + expectedAmount + " INR, got " + requestedAmount + " INR", HttpStatus.BAD_REQUEST);
                    }
                }
            }
        } catch (PaymentException pe) {
            throw pe;
        } catch (Exception e) {
            log.warn("Could not verify booking amount with Booking Service: {}", e.getMessage());
            // Allow if booking-service is temporarily unreachable in dev/test, but log warning
        }
    }

    private void confirmBookingInBookingService(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) return;
        try {
            String lookupUrl = razorpayConfig.getBookingServiceUrl() + "/api/bookings/ref/" + bookingRef;
            ResponseEntity<Map> lookup = restTemplate.getForEntity(lookupUrl, Map.class);
            if (lookup.getStatusCode().is2xxSuccessful() && lookup.getBody() != null) {
                Object idObj = lookup.getBody().get("id");
                if (idObj != null) {
                    Long bookingId = Long.parseLong(idObj.toString());
                    String confirmUrl = razorpayConfig.getBookingServiceUrl() + "/api/bookings/" + bookingId + "/confirm";
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-Internal-Service", "payment-service");
                    HttpEntity<Void> entity = new HttpEntity<>(headers);
                    restTemplate.exchange(confirmUrl, HttpMethod.PUT, entity, Map.class);
                    log.info("Booking {} (ID {}) confirmed via internal service call", bookingRef, bookingId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to automatically confirm booking {} in booking-service: {}", bookingRef, e.getMessage());
        }
    }

    private void cancelBookingInBookingService(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) return;
        try {
            String lookupUrl = razorpayConfig.getBookingServiceUrl() + "/api/bookings/ref/" + bookingRef;
            ResponseEntity<Map> lookup = restTemplate.getForEntity(lookupUrl, Map.class);
            if (lookup.getStatusCode().is2xxSuccessful() && lookup.getBody() != null) {
                Object idObj = lookup.getBody().get("id");
                if (idObj != null) {
                    Long bookingId = Long.parseLong(idObj.toString());
                    String cancelUrl = razorpayConfig.getBookingServiceUrl() + "/api/bookings/" + bookingId + "/cancel";
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-Internal-Service", "payment-service");
                    HttpEntity<Void> entity = new HttpEntity<>(headers);
                    restTemplate.exchange(cancelUrl, HttpMethod.PUT, entity, Map.class);
                    log.info("Booking {} (ID {}) cancelled via internal service call due to refund", bookingRef, bookingId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to cancel booking {} in booking-service: {}", bookingRef, e.getMessage());
        }
    }
}
