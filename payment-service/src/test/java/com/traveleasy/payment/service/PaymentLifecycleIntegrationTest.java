package com.traveleasy.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.traveleasy.payment.config.RazorpayConfig;
import com.traveleasy.payment.dto.CreateOrderRequest;
import com.traveleasy.payment.dto.CreateOrderResponse;
import com.traveleasy.payment.dto.VerifyPaymentRequest;
import com.traveleasy.payment.dto.PaymentVerificationResponse;
import com.traveleasy.payment.exception.PaymentException;
import com.traveleasy.payment.model.PaymentRefundTask;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.repository.PaymentRefundTaskRepository;
import com.razorpay.Refund;
import com.traveleasy.payment.repository.PaymentOutboxEventRepository;
import com.traveleasy.payment.repository.PaymentRepository;
import com.traveleasy.payment.repository.PaymentWebhookEventRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentLifecycleIntegrationTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RazorpayConfig razorpayConfig;

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private PaymentOutboxEventRepository outboxRepository;

    @Mock
    private PaymentRecipientResolutionService recipientResolutionService;

    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentRefundTaskRepository refundTaskRepository;

    private PaymentStateTransitionService stateTransitionService;
    private PaymentServiceImpl paymentService;

    private static final String TEST_KEY_ID = "rzp_test_key123";
    private static final String TEST_KEY_SECRET = "rzp_test_secret456";
    private static final String TEST_BOOKING_REF = "BKG-HTL-TEST-999";

    @BeforeEach
    void setUp() {
        lenient().when(razorpayConfig.getKeyId()).thenReturn(TEST_KEY_ID);
        lenient().when(razorpayConfig.getKeySecret()).thenReturn(TEST_KEY_SECRET);
        lenient().when(razorpayConfig.isConfigured()).thenReturn(true);

        stateTransitionService = spy(new PaymentStateTransitionService(paymentRepository, outboxRepository, new ObjectMapper()));
        lenient().when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(refundTaskRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        lenient().when(refundTaskRepository.save(any(PaymentRefundTask.class))).thenAnswer(i -> {
            PaymentRefundTask t = i.getArgument(0);
            if (t.getId() == null) t.setId(100L);
            return t;
        });

        paymentService = new PaymentServiceImpl(
                paymentRepository,
                razorpayConfig,
                razorpayClient,
                restTemplate,
                stateTransitionService,
                recipientResolutionService,
                webhookEventRepository,
                refundTaskRepository,
                null
        );
    }

    private Map<String, Object> buildValidBookingRecord(Double amount, String status, Instant expiresAt) {
        Map<String, Object> map = new HashMap<>();
        map.put("bookingRef", TEST_BOOKING_REF);
        map.put("totalAmount", amount);
        map.put("status", status);
        if (expiresAt != null) {
            map.put("expiresAt", expiresAt.toString());
        }
        return map;
    }

    // ==========================================
    // 1. FAIL-CLOSED AMOUNT VALIDATION (WP-HB-05 Section 6.1)
    // ==========================================

    @Test
    @DisplayName("WP-HB-05: 404 NOT_FOUND when Booking Service reports booking does not exist")
    void testFailClosed_BookingNotFound() {
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef(TEST_BOOKING_REF)
                .amount(5000.0)
                .build();

        PaymentException pe = assertThrows(PaymentException.class, () -> paymentService.createOrder(request));
        assertEquals(HttpStatus.NOT_FOUND, pe.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("WP-HB-05: 503 SERVICE_UNAVAILABLE when Booking Service is unreachable")
    void testFailClosed_BookingServiceUnreachable() {
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));
        when(restTemplate.exchange(contains("/api/bookings/ref/"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef(TEST_BOOKING_REF)
                .amount(5000.0)
                .build();

        PaymentException pe = assertThrows(PaymentException.class, () -> paymentService.createOrder(request));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, pe.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("WP-HB-05: 409 CONFLICT when booking is already CONFIRMED")
    void testFailClosed_BookingAlreadyConfirmed() {
        Map<String, Object> booking = buildValidBookingRecord(5000.0, "CONFIRMED", Instant.now().plusSeconds(900));
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(booking));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef(TEST_BOOKING_REF)
                .amount(5000.0)
                .build();

        PaymentException pe = assertThrows(PaymentException.class, () -> paymentService.createOrder(request));
        assertEquals(HttpStatus.CONFLICT, pe.getStatus());
    }

    @Test
    @DisplayName("WP-HB-05: 400 BAD_REQUEST when booking status is EXPIRED")
    void testFailClosed_BookingExpiredStatus() {
        Map<String, Object> booking = buildValidBookingRecord(5000.0, "EXPIRED", Instant.now().minusSeconds(60));
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(booking));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef(TEST_BOOKING_REF)
                .amount(5000.0)
                .build();

        PaymentException pe = assertThrows(PaymentException.class, () -> paymentService.createOrder(request));
        assertEquals(HttpStatus.BAD_REQUEST, pe.getStatus());
        assertTrue(pe.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("WP-HB-05: 400 BAD_REQUEST when booking deadline (expiresAt) has elapsed")
    void testFailClosed_BookingPastExpiresAtDeadline() {
        // Status is still PENDING_PAYMENT, but expiresAt has elapsed
        Map<String, Object> booking = buildValidBookingRecord(5000.0, "PENDING_PAYMENT", Instant.now().minusSeconds(120));
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(booking));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef(TEST_BOOKING_REF)
                .amount(5000.0)
                .build();

        PaymentException pe = assertThrows(PaymentException.class, () -> paymentService.createOrder(request));
        assertEquals(HttpStatus.BAD_REQUEST, pe.getStatus());
        assertTrue(pe.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("WP-HB-05: 400 BAD_REQUEST on amount tampering (requested amount != authoritative booking totalAmount)")
    void testFailClosed_AmountTamperingMismatch() {
        Map<String, Object> booking = buildValidBookingRecord(5000.0, "PENDING_PAYMENT", Instant.now().plusSeconds(900));
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(booking));

        // Client attempts to pay 1000.0 instead of 5000.0
        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef(TEST_BOOKING_REF)
                .amount(1000.0)
                .build();

        PaymentException pe = assertThrows(PaymentException.class, () -> paymentService.createOrder(request));
        assertEquals(HttpStatus.BAD_REQUEST, pe.getStatus());
        assertTrue(pe.getMessage().contains("Amount mismatch with booking record"));
    }

    // ==========================================
    // 2. RAZORPAY ORDER IDEMPOTENCY (WP-HB-05 Section 6.2)
    // ==========================================

    @Test
    @DisplayName("WP-HB-05: Idempotency reuses valid CREATED order with matching amount without calling Razorpay")
    void testOrderReuse_ExistingCreatedOrder() throws Exception {
        Map<String, Object> booking = buildValidBookingRecord(5000.0, "PENDING_PAYMENT", Instant.now().plusSeconds(900));
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(booking));

        PaymentTransaction existingTxn = PaymentTransaction.builder()
                .id(1L)
                .transactionRef("TXN-EXISTING-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayOrderId("order_existing_12345")
                .amount(5000.0)
                .status("CREATED")
                .currency("INR")
                .build();

        when(paymentRepository.findByBookingRef(TEST_BOOKING_REF)).thenReturn(Optional.of(existingTxn));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef(TEST_BOOKING_REF)
                .amount(5000.0)
                .build();

        CreateOrderResponse response = paymentService.createOrder(request);

        assertNotNull(response);
        assertEquals("order_existing_12345", response.getOrderId());
        assertEquals("TXN-EXISTING-001", response.getTransactionRef());
        assertEquals(5000.0, response.getAmount());
        assertEquals(500000L, response.getAmountInPaise());
        assertEquals("CREATED", response.getStatus());

        // Razorpay client must NOT be called
        verifyNoInteractions(razorpayClient);
    }

    @Test
    @DisplayName("WP-HB-05: Fast-path 409 CONFLICT if booking was already paid (SUCCESS transaction)")
    void testOrderCreation_FastPathRejection_AlreadyPaid() {
        PaymentTransaction existing = PaymentTransaction.builder()
                .bookingRef(TEST_BOOKING_REF)
                .status("SUCCESS")
                .amount(5000.0)
                .build();

        when(paymentRepository.findByBookingRef(TEST_BOOKING_REF)).thenReturn(Optional.of(existing));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef(TEST_BOOKING_REF)
                .amount(5000.0)
                .build();

        PaymentException pe = assertThrows(PaymentException.class, () -> paymentService.createOrder(request));
        assertEquals(HttpStatus.CONFLICT, pe.getStatus());
        assertTrue(pe.getMessage().contains("already paid"));

        // No network calls to booking-service or razorpay
        verify(restTemplate, never()).getForEntity(anyString(), any());
        verifyNoInteractions(razorpayClient);
    }

    @Test
    @DisplayName("WP-HB-05: Fast-path 409 CONFLICT if booking was already refunded")
    void testOrderCreation_FastPathRejection_AlreadyRefunded() {
        PaymentTransaction existing = PaymentTransaction.builder()
                .bookingRef(TEST_BOOKING_REF)
                .status("REFUNDED")
                .amount(5000.0)
                .build();

        when(paymentRepository.findByBookingRef(TEST_BOOKING_REF)).thenReturn(Optional.of(existing));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef(TEST_BOOKING_REF)
                .amount(5000.0)
                .build();

        PaymentException pe = assertThrows(PaymentException.class, () -> paymentService.createOrder(request));
        assertEquals(HttpStatus.CONFLICT, pe.getStatus());
    }

    @Test
    @DisplayName("WP-HB-05: Controlled retry permitted for previously FAILED transaction")
    void testOrderCreation_RetryPermitted_ForFailedTransaction() throws Exception {
        Map<String, Object> booking = buildValidBookingRecord(5000.0, "PENDING_PAYMENT", Instant.now().plusSeconds(900));
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(booking));

        PaymentTransaction existingFailed = PaymentTransaction.builder()
                .id(99L)
                .transactionRef("TXN-FAILED-001")
                .bookingRef(TEST_BOOKING_REF)
                .amount(5000.0)
                .status("FAILED")
                .build();

        when(paymentRepository.findByBookingRef(TEST_BOOKING_REF)).thenReturn(Optional.of(existingFailed));

        OrderClient mockOrderClient = mock(OrderClient.class);
        razorpayClient.orders = mockOrderClient;

        JSONObject orderJson = new JSONObject();
        orderJson.put("id", "order_retry_9999");
        orderJson.put("amount", 500000L);
        orderJson.put("currency", "INR");
        orderJson.put("status", "created");
        Order mockOrder = new Order(orderJson);

        when(mockOrderClient.create(any(JSONObject.class))).thenReturn(mockOrder);
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef(TEST_BOOKING_REF)
                .amount(5000.0)
                .build();

        CreateOrderResponse response = paymentService.createOrder(request);

        assertNotNull(response);
        assertEquals("order_retry_9999", response.getOrderId());
        assertEquals("CREATED", response.getStatus());
        verify(mockOrderClient, times(1)).create(any(JSONObject.class));
    }

    // ==========================================
    // 3. UNIFIED SUCCESS CONFIRMATION & COMPENSATION (WP-HB-05 Section 7)
    // ==========================================

    @Test
    @DisplayName("WP-HB-05: Authoritative confirmation path confirms booking in Booking Service")
    void testPaymentSuccess_ConfirmsBookingService() throws Exception {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(10L)
                .transactionRef("TXN-SUCCESS-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayOrderId("order_success_001")
                .amount(5000.0)
                .status("CREATED")
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_success_001")).thenReturn(Optional.of(txn));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> bookingRecord = buildValidBookingRecord(5000.0, "PENDING_PAYMENT", Instant.now().plusSeconds(900));
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(bookingRecord));

        when(restTemplate.exchange(contains("/api/bookings/ref/"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Collections.singletonMap("status", "CONFIRMED")));

        // Valid signature
        String secret = TEST_KEY_SECRET;
        String data = "order_success_001|pay_test_123";
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        String validSignature = hex.toString();

        VerifyPaymentRequest req = VerifyPaymentRequest.builder()
                .bookingRef(TEST_BOOKING_REF)
                .razorpayOrderId("order_success_001")
                .razorpayPaymentId("pay_test_123")
                .razorpaySignature(validSignature)
                .build();

        PaymentVerificationResponse resp = paymentService.verifyPayment(req);

        assertNotNull(resp);
        assertTrue(resp.isVerified());
        assertEquals("SUCCESS", resp.getStatus());

        // Booking Service confirmation was invoked
        verify(restTemplate, times(1)).exchange(
                contains("/api/bookings/ref/" + TEST_BOOKING_REF + "/confirm"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Map.class)
        );
    }

    @Test
    @DisplayName("WP-HB-05: Late payment arrives after booking expired -> initiates automated refund")
    void testLatePayment_InitiatesAutomatedRefund() throws Exception {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(10L)
                .transactionRef("TXN-LATE-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayOrderId("order_late_001")
                .razorpayPaymentId("pay_late_123")
                .amount(5000.0)
                .status("CREATED")
                .build();

        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        // Booking record is EXPIRED
        Map<String, Object> expiredBooking = buildValidBookingRecord(5000.0, "EXPIRED", Instant.now().minusSeconds(300));
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(expiredBooking));

        // Mock refund call
        when(paymentRepository.findByTransactionRef("TXN-LATE-001")).thenReturn(Optional.of(txn));

        PaymentServiceImpl.PaymentConfirmationResult result = paymentService.processAuthoritativePaymentSuccess(
                txn, "pay_late_123", "dummy_sig");

        assertNotNull(result);
        assertFalse(result.isConfirmed());
        assertTrue(result.isRefunded());
        assertTrue(result.getMessage().contains("Automated refund initiated"));

        // Booking Service confirmation must NOT be called for expired booking
        verify(restTemplate, never()).exchange(
                contains("/confirm"), eq(HttpMethod.PUT), any(), eq(Map.class));
    }

    @Test
    @DisplayName("WP-HB-05: Transient 5xx in Booking Service confirmation rethrows 5xx for webhook retry")
    void testBookingServiceTransientFailure_RethrowsForWebhookRetry() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(10L)
                .transactionRef("TXN-5XX-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayOrderId("order_5xx_001")
                .amount(5000.0)
                .status("CREATED")
                .build();

        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> bookingRecord = buildValidBookingRecord(5000.0, "PENDING_PAYMENT", Instant.now().plusSeconds(900));
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(bookingRecord));

        // Booking Service confirmation returns transient 503 SERVICE_UNAVAILABLE
        when(restTemplate.exchange(contains("/api/bookings/ref/"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Booking service temporarily down"));

        // Must rethrow so webhook inbox does NOT mark the webhook event as PROCESSED
        assertThrows(HttpServerErrorException.class, () -> {
            paymentService.processAuthoritativePaymentSuccess(txn, "pay_5xx_123", "dummy_sig");
        });
    }

    // =========================================================================
    // WP-HB-05 Targeted Corrections: Refund Recovery & Retryability Tests (Part B)
    // =========================================================================

    @Test
    @DisplayName("WP-HB-05 Part B 1: Late successful payment creates a durable compensation task")
    void testLatePayment_CreatesDurableCompensationTask() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(20L)
                .transactionRef("TXN-DURABLE-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayOrderId("order_durable_001")
                .razorpayPaymentId("pay_durable_123")
                .amount(8000.0)
                .status("CREATED")
                .build();

        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> expiredBooking = buildValidBookingRecord(8000.0, "EXPIRED", Instant.now().minusSeconds(600));
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(expiredBooking));

        when(paymentRepository.findByTransactionRef("TXN-DURABLE-001")).thenReturn(Optional.of(txn));

        PaymentServiceImpl.PaymentConfirmationResult result = paymentService.processAuthoritativePaymentSuccess(
                txn, "pay_durable_123", "dummy_sig");

        assertNotNull(result);
        assertFalse(result.isConfirmed());
        assertTrue(result.isRefunded());

        // Verify durable refund task is persisted
        ArgumentCaptor<PaymentRefundTask> taskCaptor = ArgumentCaptor.forClass(PaymentRefundTask.class);
        verify(refundTaskRepository, atLeastOnce()).save(taskCaptor.capture());
        PaymentRefundTask task = taskCaptor.getValue();
        assertEquals(TEST_BOOKING_REF, task.getBookingRef());
        assertEquals("TXN-DURABLE-001", task.getTransactionRef());
        assertEquals(8000.0, task.getAmount());
        assertEquals("REFUND-COMP-" + TEST_BOOKING_REF, task.getIdempotencyKey());
    }

    @Test
    @DisplayName("WP-HB-05 Part B 2: Definitive inventory confirmation failure (409 Conflict) creates durable compensation task")
    void testInventoryConflict_CreatesDurableCompensationTask() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(21L)
                .transactionRef("TXN-CONFLICT-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayOrderId("order_conf_001")
                .razorpayPaymentId("pay_conf_123")
                .amount(4500.0)
                .status("CREATED")
                .build();

        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> bookingRecord = buildValidBookingRecord(4500.0, "PENDING_PAYMENT", Instant.now().plusSeconds(300));
        when(restTemplate.getForEntity(contains("/api/bookings/ref/"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(bookingRecord));

        // Booking Service returns 409 Conflict (e.g. room hold expired / taken)
        when(restTemplate.exchange(contains("/confirm"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "Room inventory no longer available"));

        when(paymentRepository.findByTransactionRef("TXN-CONFLICT-001")).thenReturn(Optional.of(txn));

        PaymentServiceImpl.PaymentConfirmationResult result = paymentService.processAuthoritativePaymentSuccess(
                txn, "pay_conf_123", "dummy_sig");

        assertNotNull(result);
        assertFalse(result.isConfirmed());
        assertTrue(result.isRefunded());
        assertTrue(result.getMessage().contains("Room hold expired"));

        // Verify durable compensation task was created
        ArgumentCaptor<PaymentRefundTask> taskCaptor = ArgumentCaptor.forClass(PaymentRefundTask.class);
        verify(refundTaskRepository, atLeastOnce()).save(taskCaptor.capture());
        assertEquals("REFUND-COMP-" + TEST_BOOKING_REF, taskCaptor.getValue().getIdempotencyKey());
    }

    @Test
    @DisplayName("WP-HB-05 Part B 3: Transient Razorpay failure leaves compensation task retryable with backoff")
    void testTransientRazorpayFailure_LeavesTaskRetryable() throws Exception {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(22L)
                .transactionRef("TXN-RETRY-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayPaymentId("pay_retry_123")
                .amount(3000.0)
                .status("SUCCESS")
                .build();

        PaymentRefundTask task = PaymentRefundTask.builder()
                .id(501L)
                .bookingRef(TEST_BOOKING_REF)
                .transactionRef("TXN-RETRY-001")
                .razorpayPaymentId("pay_retry_123")
                .amount(3000.0)
                .idempotencyKey("REFUND-COMP-" + TEST_BOOKING_REF)
                .status("PROCESSING")
                .attemptCount(1)
                .maxAttempts(5)
                .build();

        when(paymentRepository.findByTransactionRef("TXN-RETRY-001")).thenReturn(Optional.of(txn));

        // Razorpay fetchAllRefunds returns empty
        razorpayClient.payments = mock(com.razorpay.PaymentClient.class);
        when(razorpayClient.payments.fetchAllRefunds("pay_retry_123")).thenReturn(Collections.emptyList());

        // Razorpay refund throws transient network timeout
        when(razorpayClient.payments.refund(eq("pay_retry_123"), any(JSONObject.class)))
                .thenThrow(new RuntimeException("Connection timed out to razorpay"));

        paymentService.reconcileAndExecuteRefundTask(task);

        // Verify task was marked FAILED_RETRYABLE with future nextRetryAt
        verify(refundTaskRepository, times(1)).markFailedRetryable(
                eq(501L), contains("Connection timed out"), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("WP-HB-05 Part B 4: Gateway reconciliation discovers existing refund and reconciles without duplicate refund call")
    void testReconciliationWorker_ReconcilesExistingRefundWithoutDuplicateCharge() throws Exception {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(23L)
                .transactionRef("TXN-RECON-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayPaymentId("pay_recon_123")
                .amount(5000.0)
                .status("SUCCESS")
                .build();

        PaymentRefundTask task = PaymentRefundTask.builder()
                .id(502L)
                .bookingRef(TEST_BOOKING_REF)
                .transactionRef("TXN-RECON-001")
                .razorpayPaymentId("pay_recon_123")
                .amount(5000.0)
                .idempotencyKey("REFUND-COMP-" + TEST_BOOKING_REF)
                .status("PROCESSING")
                .attemptCount(2)
                .maxAttempts(5)
                .build();

        when(paymentRepository.findByTransactionRef("TXN-RECON-001")).thenReturn(Optional.of(txn));

        // Mock existing refund found in Razorpay
        Refund mockRefund = mock(Refund.class);
        when(mockRefund.get("id")).thenReturn("rfnd_existing_999");
        when(mockRefund.get("status")).thenReturn("processed");
        when(mockRefund.get("amount")).thenReturn(500000L); // 5000 INR = 500000 paise

        razorpayClient.payments = mock(com.razorpay.PaymentClient.class);
        when(razorpayClient.payments.fetchAllRefunds("pay_recon_123")).thenReturn(List.of(mockRefund));

        paymentService.reconcileAndExecuteRefundTask(task);

        // razorpay.refund must NEVER be called (avoiding duplicate refund!)
        verify(razorpayClient.payments, never()).refund(anyString(), any(JSONObject.class));

        // Task must be marked PROCESSED with existing refund ID
        verify(refundTaskRepository, times(1)).markProcessed(eq(502L), eq("rfnd_existing_999"), any(Instant.class));
    }

    @Test
    @DisplayName("WP-HB-05 Part B 5: Duplicate checkout and webhook calls resolve to the identical durable refund task")
    void testDuplicateCheckoutAndWebhook_ReuseSameDurableRefundTask() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(24L)
                .transactionRef("TXN-DEDUP-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayPaymentId("pay_dedup_123")
                .amount(6000.0)
                .status("SUCCESS")
                .build();

        PaymentRefundTask existingTask = PaymentRefundTask.builder()
                .id(777L)
                .bookingRef(TEST_BOOKING_REF)
                .transactionRef("TXN-DEDUP-001")
                .idempotencyKey("REFUND-COMP-" + TEST_BOOKING_REF)
                .amount(6000.0)
                .status("PENDING")
                .build();

        // When looked up, existing task is found
        when(refundTaskRepository.findByIdempotencyKey("REFUND-COMP-" + TEST_BOOKING_REF))
                .thenReturn(Optional.of(existingTask));

        PaymentRefundTask task1 = paymentService.recordOrGetCompensationRefundTask(
                txn, "Test duplicate reason", "REFUND-COMP-" + TEST_BOOKING_REF);
        PaymentRefundTask task2 = paymentService.recordOrGetCompensationRefundTask(
                txn, "Another attempt reason", "REFUND-COMP-" + TEST_BOOKING_REF);

        assertSame(existingTask, task1);
        assertSame(existingTask, task2);
        // Verify save was never called because it was retrieved idempotently
        verify(refundTaskRepository, never()).save(any(PaymentRefundTask.class));
    }

    @Test
    @DisplayName("WP-HB-05 Part B 6: Max retry attempts exhausted marks task FAILED_FINAL")
    void testReconciliationWorker_ExhaustsMaxAttempts_MarksFailedFinal() throws Exception {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(25L)
                .transactionRef("TXN-FINAL-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayPaymentId("pay_final_123")
                .amount(2000.0)
                .status("SUCCESS")
                .build();

        PaymentRefundTask task = PaymentRefundTask.builder()
                .id(505L)
                .bookingRef(TEST_BOOKING_REF)
                .transactionRef("TXN-FINAL-001")
                .razorpayPaymentId("pay_final_123")
                .amount(2000.0)
                .idempotencyKey("REFUND-COMP-" + TEST_BOOKING_REF)
                .status("PROCESSING")
                .attemptCount(5)
                .maxAttempts(5)
                .build();

        when(paymentRepository.findByTransactionRef("TXN-FINAL-001")).thenReturn(Optional.of(txn));

        razorpayClient.payments = mock(com.razorpay.PaymentClient.class);
        when(razorpayClient.payments.fetchAllRefunds("pay_final_123")).thenReturn(Collections.emptyList());
        when(razorpayClient.payments.refund(eq("pay_final_123"), any(JSONObject.class)))
                .thenThrow(new RuntimeException("Permanent gateway failure"));

        paymentService.reconcileAndExecuteRefundTask(task);

        // Verify task was marked FAILED_FINAL
        verify(refundTaskRepository, times(1)).markFailedFinal(
                eq(505L), contains("Permanent gateway failure"), any(Instant.class));
    }


    @Test
    @DisplayName("WP-HB-05 Part B 7: Razorpay initiated status keeps task in INITIATED without premature PROCESSED")
    void testRazorpayInitiatedStatus_KeepsLocalTaskInInitiated() throws Exception {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(26L)
                .transactionRef("TXN-INIT-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayPaymentId("pay_init_123")
                .amount(4500.0)
                .status("SUCCESS")
                .build();

        PaymentRefundTask task = PaymentRefundTask.builder()
                .id(506L)
                .bookingRef(TEST_BOOKING_REF)
                .transactionRef("TXN-INIT-001")
                .razorpayPaymentId("pay_init_123")
                .amount(4500.0)
                .idempotencyKey("REFUND-COMP-" + TEST_BOOKING_REF)
                .status("PROCESSING")
                .attemptCount(1)
                .build();

        when(paymentRepository.findByTransactionRef("TXN-INIT-001")).thenReturn(Optional.of(txn));

        Refund mockRefund = mock(Refund.class);
        when(mockRefund.get("id")).thenReturn("rfnd_in_flight_111");
        when(mockRefund.get("status")).thenReturn("initiated");
        when(mockRefund.get("amount")).thenReturn(450000L); // 4500 INR in paise

        razorpayClient.payments = mock(com.razorpay.PaymentClient.class);
        when(razorpayClient.payments.fetchAllRefunds("pay_init_123")).thenReturn(List.of(mockRefund));

        paymentService.reconcileAndExecuteRefundTask(task);

        // Task must be kept in INITIATED
        verify(refundTaskRepository, times(1)).markInitiated(eq(506L), eq("rfnd_in_flight_111"), any(Instant.class));
        // Must NEVER be marked PROCESSED prematurely
        verify(refundTaskRepository, never()).markProcessed(anyLong(), anyString(), any(Instant.class));
        // Must NOT attempt to create another refund
        verify(razorpayClient.payments, never()).refund(anyString(), any(JSONObject.class));
    }

    @Test
    @DisplayName("WP-HB-05 Part B 8: Later Razorpay processed response transitions task to PROCESSED")
    void testLaterRazorpayProcessedResponse_TransitionsTaskToProcessed() throws Exception {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(27L)
                .transactionRef("TXN-COMP-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayPaymentId("pay_comp_123")
                .refundId("rfnd_in_flight_111")
                .amount(4500.0)
                .status("REFUND_INITIATED")
                .build();

        PaymentRefundTask task = PaymentRefundTask.builder()
                .id(507L)
                .bookingRef(TEST_BOOKING_REF)
                .transactionRef("TXN-COMP-001")
                .razorpayPaymentId("pay_comp_123")
                .razorpayRefundId("rfnd_in_flight_111")
                .amount(4500.0)
                .idempotencyKey("REFUND-COMP-" + TEST_BOOKING_REF)
                .status("INITIATED")
                .attemptCount(1)
                .build();

        when(paymentRepository.findByTransactionRef("TXN-COMP-001")).thenReturn(Optional.of(txn));

        Refund mockRefund = mock(Refund.class);
        when(mockRefund.get("id")).thenReturn("rfnd_in_flight_111");
        when(mockRefund.get("status")).thenReturn("processed");
        when(mockRefund.get("amount")).thenReturn(450000L);

        razorpayClient.payments = mock(com.razorpay.PaymentClient.class);
        when(razorpayClient.payments.fetchAllRefunds("pay_comp_123")).thenReturn(List.of(mockRefund));

        paymentService.reconcileAndExecuteRefundTask(task);

        // Task must transition to PROCESSED
        verify(refundTaskRepository, times(1)).markProcessed(eq(507L), eq("rfnd_in_flight_111"), any(Instant.class));
        verify(razorpayClient.payments, never()).refund(anyString(), any(JSONObject.class));
    }

    @Test
    @DisplayName("WP-HB-05 Part B 9: Temporary status retrieval failure does not mark task PROCESSED or create duplicate refund")
    void testTemporaryStatusRetrievalFailure_DoesNotMarkTaskProcessed() throws Exception {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(28L)
                .transactionRef("TXN-TIMEOUT-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayPaymentId("pay_timeout_123")
                .refundId("rfnd_pending_555")
                .amount(3500.0)
                .status("REFUND_INITIATED")
                .build();

        PaymentRefundTask task = PaymentRefundTask.builder()
                .id(508L)
                .bookingRef(TEST_BOOKING_REF)
                .transactionRef("TXN-TIMEOUT-001")
                .razorpayPaymentId("pay_timeout_123")
                .razorpayRefundId("rfnd_pending_555")
                .amount(3500.0)
                .idempotencyKey("REFUND-COMP-" + TEST_BOOKING_REF)
                .status("INITIATED")
                .attemptCount(2)
                .build();

        when(paymentRepository.findByTransactionRef("TXN-TIMEOUT-001")).thenReturn(Optional.of(txn));

        razorpayClient.payments = mock(com.razorpay.PaymentClient.class);
        when(razorpayClient.payments.fetchAllRefunds("pay_timeout_123"))
                .thenThrow(new RuntimeException("504 Gateway Timeout connecting to Razorpay"));

        paymentService.reconcileAndExecuteRefundTask(task);

        // Must NOT mark as PROCESSED
        verify(refundTaskRepository, never()).markProcessed(anyLong(), anyString(), any(Instant.class));
        // Must NOT create another refund
        verify(razorpayClient.payments, never()).refund(anyString(), any(JSONObject.class));
        // Must release lease so it remains eligible for subsequent reconciliation
        verify(refundTaskRepository, times(1)).releaseInitiatedTask(
                eq(508L), contains("504 Gateway Timeout"), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("WP-HB-05 Part B 10: Repeated reconciliation cycles do not create duplicate refunds")
    void testRepeatedReconciliation_DoesNotCreateDuplicateRefunds() throws Exception {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(29L)
                .transactionRef("TXN-REPEAT-001")
                .bookingRef(TEST_BOOKING_REF)
                .razorpayPaymentId("pay_repeat_123")
                .refundId("rfnd_active_777")
                .amount(5000.0)
                .status("REFUND_INITIATED")
                .build();

        PaymentRefundTask task = PaymentRefundTask.builder()
                .id(509L)
                .bookingRef(TEST_BOOKING_REF)
                .transactionRef("TXN-REPEAT-001")
                .razorpayPaymentId("pay_repeat_123")
                .razorpayRefundId("rfnd_active_777")
                .amount(5000.0)
                .idempotencyKey("REFUND-COMP-" + TEST_BOOKING_REF)
                .status("INITIATED")
                .attemptCount(1)
                .build();

        when(paymentRepository.findByTransactionRef("TXN-REPEAT-001")).thenReturn(Optional.of(txn));

        Refund mockRefund = mock(Refund.class);
        when(mockRefund.get("id")).thenReturn("rfnd_active_777");
        when(mockRefund.get("status")).thenReturn("initiated");
        when(mockRefund.get("amount")).thenReturn(500000L);

        razorpayClient.payments = mock(com.razorpay.PaymentClient.class);
        when(razorpayClient.payments.fetchAllRefunds("pay_repeat_123")).thenReturn(List.of(mockRefund));

        // Execute reconciliation 3 times repeatedly
        for (int i = 0; i < 3; i++) {
            paymentService.reconcileAndExecuteRefundTask(task);
        }

        // razorpay.payments.refund must NEVER be invoked across all cycles
        verify(razorpayClient.payments, never()).refund(anyString(), any(JSONObject.class));
        // Task must be kept in INITIATED and never marked PROCESSED
        verify(refundTaskRepository, times(3)).markInitiated(eq(509L), eq("rfnd_active_777"), any(Instant.class));
        verify(refundTaskRepository, never()).markProcessed(anyLong(), anyString(), any(Instant.class));
    }
}
