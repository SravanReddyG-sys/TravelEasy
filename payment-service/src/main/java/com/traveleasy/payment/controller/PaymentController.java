package com.traveleasy.payment.controller;

import com.traveleasy.payment.dto.*;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.service.PaymentSecurityService;
import com.traveleasy.payment.service.PaymentService;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentSecurityService securityService;

    /**
     * Public gateway configuration endpoint returning only the Razorpay Key ID.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getConfig() {
        return ResponseEntity.ok(paymentService.getPublicConfig());
    }

    /**
     * Create Razorpay server-side order with amount in paise.
     */
    @PostMapping("/create-order")
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("Received request to create Razorpay order for booking {}", request.getBookingRef());
        CreateOrderResponse response = paymentService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Server-side HMAC-SHA256 signature verification upon successful Checkout popup authorization.
     */
    @PostMapping("/verify")
    public ResponseEntity<PaymentVerificationResponse> verifyPayment(@Valid @RequestBody VerifyPaymentRequest request) {
        log.info("Received request to verify signature for order {}", request.getRazorpayOrderId());
        PaymentVerificationResponse response = paymentService.verifyPayment(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Asynchronous Razorpay server-to-server webhook callback.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader) {
        log.info("Received Razorpay webhook payload (length: {})", rawPayload != null ? rawPayload.length() : 0);
        paymentService.processWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    /**
     * Automated Razorpay refund initiation by transaction reference.
     */
    @PostMapping("/refund/{transactionRef}")
    public ResponseEntity<RefundResponse> processRefund(
            @PathVariable String transactionRef,
            @RequestBody(required = false) RefundRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader) {
        if (request == null) {
            request = new RefundRequest();
        }
        if (request.getIdempotencyKey() == null && idempotencyKeyHeader != null) {
            request.setIdempotencyKey(idempotencyKeyHeader);
        }
        log.info("Received refund request for transaction {} with idempotencyKey {}", transactionRef, request.getIdempotencyKey());
        RefundResponse response = paymentService.processRefund(transactionRef, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Automated Razorpay refund initiation by booking reference.
     */
    @PostMapping("/refund/booking/{bookingRef}")
    public ResponseEntity<RefundResponse> processRefundByBooking(
            @PathVariable String bookingRef,
            @RequestBody(required = false) RefundRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader) {
        if (request == null) {
            request = new RefundRequest();
        }
        if (request.getIdempotencyKey() == null && idempotencyKeyHeader != null) {
            request.setIdempotencyKey(idempotencyKeyHeader);
        }
        log.info("Received refund request for booking {} with idempotencyKey {}", bookingRef, request.getIdempotencyKey());
        RefundResponse response = paymentService.processRefundByBooking(bookingRef, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Authoritative internal refund status lookup & Razorpay reconciliation for a booking.
     * Never initiates a new refund; strictly inspects local state and Razorpay refunds for existing refunds.
     */
    @GetMapping("/refund/booking/{bookingRef}/reconcile")
    public ResponseEntity<?> reconcileRefundByBooking(
            @PathVariable String bookingRef,
            @RequestParam(required = false) String idempotencyKey,
            @RequestParam(required = false) Double expectedAmount,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader) {
        if (!securityService.verifyStrictInternalToken(authHeader, internalToken, internalService, "booking-service")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Unauthorized: Valid booking-service internal token required"));
        }
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : idempotencyKeyHeader;
        RefundResponse response = paymentService.reconcileRefundByBooking(bookingRef, effectiveKey, expectedAmount);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve payment details by booking reference.
     */
    @GetMapping("/booking/{bookingRef}")
    public ResponseEntity<PaymentTransaction> getPaymentByBooking(@PathVariable String bookingRef) {
        return ResponseEntity.ok(paymentService.getPaymentByBooking(bookingRef));
    }

    /**
     * Retrieve payment details by transaction reference.
     */
    @GetMapping("/ref/{transactionRef}")
    public ResponseEntity<PaymentTransaction> getPaymentByRef(@PathVariable String transactionRef) {
        return ResponseEntity.ok(paymentService.getPaymentByTransactionRef(transactionRef));
    }

    /**
     * Legacy payment processing endpoint (preserved for mock/backward compatibility).
     */
    @PostMapping("/process")
    public ResponseEntity<PaymentTransaction> processPayment(@RequestBody PaymentTransaction transaction) {
        log.info("Received legacy payment process request for booking {}", transaction.getBookingRef());
        PaymentTransaction saved = paymentService.processLegacyPayment(transaction);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Platform Net Collections statistics for Admin Dashboard Overview KPI.
     * Net Collections = Successful Captured Collections - Processed Refunds.
     */
    @GetMapping("/admin/stats")
    public ResponseEntity<?> getAdminStats(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }
        PaymentStatsResponse stats = paymentService.getAdminStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Daily Revenue Trends (Net Collections = Daily Captured - Daily Processed Refunds)
     * for Admin Dashboard Booking Trends line chart (Revenue toggle).
     */
    @GetMapping("/admin/revenue-trends")
    public ResponseEntity<?> getAdminRevenueTrends(
            @RequestParam(defaultValue = "7") int days,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }
        List<DailyRevenueTrendResponse> trends = paymentService.getAdminRevenueTrends(days);
        return ResponseEntity.ok(trends);
    }

    /**
     * Authoritative Net Revenue reporting for Hotel Manager Dashboard.
     * Net Revenue = Eligible Successful Payment Amounts - Successfully Processed Refund Amounts
     * Attributed strictly to actual financial transaction timestamps (timestamp for payments, refundedAt for refunds).
     */
    @PostMapping("/hotel/revenue-summary")
    public ResponseEntity<?> getHotelRevenueSummary(
            @RequestBody HotelRevenueReportRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authorization token required"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }
        String role = securityService.extractRole(token);
        boolean isAuthorized = "ROLE_HOTEL_MANAGER".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role);
        if (!isAuthorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Hotel Manager or Admin authorization required"));
        }

        Long userId = null;
        try {
            Object uid = securityService.extractClaims(token).get("userId");
            if (uid instanceof Number) {
                userId = ((Number) uid).longValue();
            }
        } catch (Exception ignored) {}

        HotelRevenueReportResponse response = paymentService.getHotelRevenueSummary(request, role, userId);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<?> validateAdminAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authorization token required"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }
        String role = securityService.extractRole(token);
        if (!"ROLE_ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Administrator authorization required"));
        }
        return null;
    }
}
