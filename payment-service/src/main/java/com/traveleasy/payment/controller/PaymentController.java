package com.traveleasy.payment.controller;

import com.traveleasy.payment.dto.*;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.service.PaymentService;
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
     * Automated Razorpay refund initiation.
     */
    @PostMapping("/refund/{transactionRef}")
    public ResponseEntity<RefundResponse> processRefund(
            @PathVariable String transactionRef,
            @RequestBody(required = false) RefundRequest request) {
        log.info("Received refund request for transaction {}", transactionRef);
        RefundResponse response = paymentService.processRefund(transactionRef, request);
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
}
