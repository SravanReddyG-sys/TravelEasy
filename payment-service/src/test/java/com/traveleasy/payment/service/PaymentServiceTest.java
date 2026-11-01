package com.traveleasy.payment.service;

import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.razorpay.Refund;
import com.razorpay.RefundClient;
import com.traveleasy.payment.config.RazorpayConfig;
import com.traveleasy.payment.dto.*;
import com.traveleasy.payment.exception.PaymentException;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.repository.PaymentRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RazorpayConfig razorpayConfig;

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static final String TEST_KEY_ID = "rzp_test_mockKeyId123";
    private static final String TEST_KEY_SECRET = "mockSecretKey456789";
    private static final String TEST_WEBHOOK_SECRET = "mockWebhookSecret987";

    @BeforeEach
    void setUp() {
        lenient().when(razorpayConfig.getKeyId()).thenReturn(TEST_KEY_ID);
        lenient().when(razorpayConfig.getKeySecret()).thenReturn(TEST_KEY_SECRET);
        lenient().when(razorpayConfig.getWebhookSecret()).thenReturn(TEST_WEBHOOK_SECRET);
        lenient().when(razorpayConfig.isConfigured()).thenReturn(true);
    }

    private String calculateHmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : rawHmac) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    @DisplayName("Create Order: Successfully creates Razorpay order and saves transaction as CREATED")
    void testCreateOrder_Success() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef("BK-1001")
                .customerId(55L)
                .amount(1250.00)
                .paymentMethod("RAZORPAY")
                .build();

        when(paymentRepository.findByBookingRef("BK-1001")).thenReturn(Optional.empty());

        // Razorpay Order client mock
        OrderClient mockOrderClient = mock(OrderClient.class);
        razorpayClient.orders = mockOrderClient;

        JSONObject orderJson = new JSONObject();
        orderJson.put("id", "order_test_9999");
        orderJson.put("amount", 125000L);
        orderJson.put("currency", "INR");
        orderJson.put("status", "created");
        Order mockOrder = new Order(orderJson);

        when(mockOrderClient.create(any(JSONObject.class))).thenReturn(mockOrder);
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        CreateOrderResponse response = paymentService.createOrder(request);

        assertNotNull(response);
        assertEquals("order_test_9999", response.getOrderId());
        assertEquals("BK-1001", response.getBookingRef());
        assertEquals(1250.00, response.getAmount());
        assertEquals(125000L, response.getAmountInPaise());
        assertEquals("INR", response.getCurrency());
        assertEquals(TEST_KEY_ID, response.getKeyId());
        assertEquals("CREATED", response.getStatus());

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        PaymentTransaction saved = captor.getValue();
        assertEquals("BK-1001", saved.getBookingRef());
        assertEquals("order_test_9999", saved.getRazorpayOrderId());
        assertEquals("CREATED", saved.getStatus());
        assertEquals(1250.00, saved.getAmount());
    }

    @Test
    @DisplayName("Create Order: Fails if Razorpay credentials are not configured")
    void testCreateOrder_NotConfigured() {
        when(razorpayConfig.isConfigured()).thenReturn(false);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef("BK-1002")
                .amount(500.0)
                .build();

        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.createOrder(request));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertTrue(ex.getMessage().contains("not configured"));
    }

    @Test
    @DisplayName("Create Order: Rejects order creation if booking is already paid")
    void testCreateOrder_AlreadyPaid() {
        PaymentTransaction existing = PaymentTransaction.builder()
                .bookingRef("BK-ALREADY-PAID")
                .status("SUCCESS")
                .build();
        when(paymentRepository.findByBookingRef("BK-ALREADY-PAID")).thenReturn(Optional.of(existing));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef("BK-ALREADY-PAID")
                .amount(800.0)
                .build();

        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.createOrder(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("already paid"));
    }

    @Test
    @DisplayName("Verify Payment: Valid HMAC-SHA256 signature updates transaction to SUCCESS and confirms booking")
    void testVerifyPayment_ValidSignature() throws Exception {
        String orderId = "order_test_12345";
        String paymentId = "pay_test_67890";
        String payload = orderId + "|" + paymentId;
        String validSignature = calculateHmac(payload, TEST_KEY_SECRET);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(1L)
                .transactionRef("TXN-TEST-1")
                .bookingRef("BK-5001")
                .razorpayOrderId(orderId)
                .status("CREATED")
                .amount(1500.0)
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(transaction));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        VerifyPaymentRequest request = VerifyPaymentRequest.builder()
                .razorpayOrderId(orderId)
                .razorpayPaymentId(paymentId)
                .razorpaySignature(validSignature)
                .bookingRef("BK-5001")
                .build();

        PaymentVerificationResponse response = paymentService.verifyPayment(request);

        assertTrue(response.isVerified());
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("BK-5001", response.getBookingRef());
        assertEquals(paymentId, response.getPaymentId());

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        PaymentTransaction saved = captor.getValue();
        assertEquals("SUCCESS", saved.getStatus());
        assertEquals(paymentId, saved.getRazorpayPaymentId());
        assertEquals(validSignature, saved.getRazorpaySignature());
    }

    @Test
    @DisplayName("Verify Payment: Invalid HMAC-SHA256 signature records FAILED transaction and throws 400")
    void testVerifyPayment_InvalidSignature() {
        String orderId = "order_test_12345";
        String paymentId = "pay_test_67890";
        String fakeSignature = "invalid_signature_hex_value_00000000";

        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(2L)
                .transactionRef("TXN-TEST-2")
                .bookingRef("BK-5002")
                .razorpayOrderId(orderId)
                .status("CREATED")
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(transaction));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        VerifyPaymentRequest request = VerifyPaymentRequest.builder()
                .razorpayOrderId(orderId)
                .razorpayPaymentId(paymentId)
                .razorpaySignature(fakeSignature)
                .bookingRef("BK-5002")
                .build();

        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.verifyPayment(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("signature verification failed"));

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        PaymentTransaction saved = captor.getValue();
        assertEquals("FAILED", saved.getStatus());
        assertEquals("SIGNATURE_MISMATCH", saved.getErrorCode());
    }

    @Test
    @DisplayName("Verify Payment: Idempotent - returns verified if transaction is already in SUCCESS state")
    void testVerifyPayment_IdempotentAlreadySuccess() {
        String orderId = "order_test_already_done";
        String paymentId = "pay_test_already_done";

        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(3L)
                .transactionRef("TXN-TEST-3")
                .bookingRef("BK-5003")
                .razorpayOrderId(orderId)
                .razorpayPaymentId(paymentId)
                .status("SUCCESS")
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(transaction));

        VerifyPaymentRequest request = VerifyPaymentRequest.builder()
                .razorpayOrderId(orderId)
                .razorpayPaymentId(paymentId)
                .razorpaySignature("any_sig")
                .bookingRef("BK-5003")
                .build();

        PaymentVerificationResponse response = paymentService.verifyPayment(request);

        assertTrue(response.isVerified());
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("BK-5003", response.getBookingRef());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Webhook: Valid signature for payment.captured reconciles transaction to SUCCESS")
    void testProcessWebhook_PaymentCaptured_Success() throws Exception {
        String orderId = "order_webhook_001";
        String paymentId = "pay_webhook_001";

        JSONObject entity = new JSONObject();
        entity.put("id", paymentId);
        entity.put("order_id", orderId);
        entity.put("amount", 200000);
        entity.put("status", "captured");

        JSONObject payment = new JSONObject();
        payment.put("entity", entity);

        JSONObject payload = new JSONObject();
        payload.put("payment", payment);

        JSONObject root = new JSONObject();
        root.put("event", "payment.captured");
        root.put("payload", payload);

        String rawPayload = root.toString();
        String validWebhookSignature = calculateHmac(rawPayload, TEST_WEBHOOK_SECRET);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionRef("TXN-WH-1")
                .bookingRef("BK-WH-001")
                .razorpayOrderId(orderId)
                .status("CREATED")
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(transaction));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.processWebhook(rawPayload, validWebhookSignature);

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        PaymentTransaction saved = captor.getValue();
        assertEquals("SUCCESS", saved.getStatus());
        assertEquals(paymentId, saved.getRazorpayPaymentId());
    }

    @Test
    @DisplayName("Webhook: Invalid signature throws 400 Bad Request")
    void testProcessWebhook_InvalidSignature() {
        String rawPayload = "{\"event\":\"payment.captured\"}";
        String invalidSignature = "invalid_signature_hex";

        PaymentException ex = assertThrows(PaymentException.class, () ->
                paymentService.processWebhook(rawPayload, invalidSignature));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Invalid webhook signature"));
    }

    @Test
    @DisplayName("Webhook: Idempotent - duplicate payment.captured does not re-confirm already SUCCESS payment")
    void testProcessWebhook_DuplicateEvent_Idempotent() throws Exception {
        String orderId = "order_wh_dup";
        String paymentId = "pay_wh_dup";

        JSONObject entity = new JSONObject();
        entity.put("id", paymentId);
        entity.put("order_id", orderId);

        JSONObject payment = new JSONObject();
        payment.put("entity", entity);

        JSONObject payload = new JSONObject();
        payload.put("payment", payment);

        JSONObject root = new JSONObject();
        root.put("event", "payment.captured");
        root.put("payload", payload);

        String rawPayload = root.toString();
        String validWebhookSignature = calculateHmac(rawPayload, TEST_WEBHOOK_SECRET);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionRef("TXN-WH-DUP")
                .bookingRef("BK-WH-DUP")
                .razorpayOrderId(orderId)
                .razorpayPaymentId(paymentId)
                .status("SUCCESS")
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(transaction));

        paymentService.processWebhook(rawPayload, validWebhookSignature);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Process Refund: Calls Razorpay refund API, records audit details and coordinates booking cancellation")
    void testProcessRefund_Success() throws Exception {
        String txnRef = "TXN-REFUND-001";
        String paymentId = "pay_to_refund_123";

        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionRef(txnRef)
                .bookingRef("BK-REFUND-001")
                .razorpayPaymentId(paymentId)
                .amount(1800.0)
                .currency("INR")
                .status("SUCCESS")
                .build();

        when(paymentRepository.findByTransactionRef(txnRef)).thenReturn(Optional.of(transaction));

        // Mock Razorpay client payments & refund
        com.razorpay.PaymentClient mockPaymentClient = mock(com.razorpay.PaymentClient.class);
        razorpayClient.payments = mockPaymentClient;

        JSONObject refundJson = new JSONObject();
        refundJson.put("id", "rfnd_test_999");
        refundJson.put("payment_id", paymentId);
        refundJson.put("amount", 180000L);
        refundJson.put("status", "processed");
        Refund mockRefund = new Refund(refundJson);

        when(mockPaymentClient.refund(eq(paymentId), any(JSONObject.class))).thenReturn(mockRefund);
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        RefundRequest request = RefundRequest.builder()
                .amount(1800.0)
                .reason("User cancelled trip")
                .build();

        RefundResponse response = paymentService.processRefund(txnRef, request);

        assertNotNull(response);
        assertEquals("rfnd_test_999", response.getRefundId());
        assertEquals("PROCESSED", response.getRefundStatus());
        assertEquals(1800.0, response.getRefundAmount());
        assertEquals(txnRef, response.getTransactionRef());

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        PaymentTransaction saved = captor.getValue();
        assertEquals("REFUNDED", saved.getStatus());
        assertEquals("rfnd_test_999", saved.getRefundId());
        assertEquals("PROCESSED", saved.getRefundStatus());
        assertEquals(1800.0, saved.getRefundAmount());
        assertNotNull(saved.getRefundedAt());
    }

    @Test
    @DisplayName("Process Refund: Idempotent - returns existing refund if transaction is already REFUNDED")
    void testProcessRefund_DuplicateIdempotent() {
        String txnRef = "TXN-ALREADY-REFUNDED";
        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionRef(txnRef)
                .bookingRef("BK-ALREADY-REFUNDED")
                .status("REFUNDED")
                .refundId("rfnd_existing_111")
                .refundStatus("PROCESSED")
                .refundAmount(1000.0)
                .build();

        when(paymentRepository.findByTransactionRef(txnRef)).thenReturn(Optional.of(transaction));

        RefundRequest request = RefundRequest.builder()
                .amount(1000.0)
                .reason("Duplicate request")
                .build();

        RefundResponse response = paymentService.processRefund(txnRef, request);

        assertNotNull(response);
        assertEquals("rfnd_existing_111", response.getRefundId());
        assertEquals("PROCESSED", response.getRefundStatus());
        assertEquals(1000.0, response.getRefundAmount());
        assertTrue(response.getMessage().contains("already been fully refunded"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Process Refund: Rejects refund if payment status is not SUCCESS")
    void testProcessRefund_NotSuccessfulPayment() {
        String txnRef = "TXN-FAILED-PAYMENT";
        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionRef(txnRef)
                .bookingRef("BK-FAILED")
                .status("FAILED")
                .build();

        when(paymentRepository.findByTransactionRef(txnRef)).thenReturn(Optional.of(transaction));

        RefundRequest request = RefundRequest.builder().reason("Failed payment").build();

        PaymentException ex = assertThrows(PaymentException.class, () ->
                paymentService.processRefund(txnRef, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Only successful payments can be refunded"));
    }

    // ==================== UNIT 3.3: RECONCILIATION & IDEMPOTENCY TESTS ====================

    @Test
    @DisplayName("Unit 3.3: Gateway Reconciliation - Adopts existing Razorpay refund without duplicate gateway execution")
    void testProcessRefund_GatewayReconciliation_AdoptsExistingRefund() throws Exception {
        String txnRef = "TXN-TIMEOUT-RECOVER";
        String paymentId = "pay_timeout_recover_123";
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(99L)
                .transactionRef(txnRef)
                .bookingRef("BK-RECOVER-1")
                .amount(2500.0)
                .razorpayPaymentId(paymentId)
                .status("REFUND_INITIATED")
                .idempotencyKey("rfnd_BK-RECOVER-1_10")
                .build();

        when(paymentRepository.findByTransactionRef(txnRef)).thenReturn(Optional.of(transaction));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        com.razorpay.PaymentClient mockPaymentClient = mock(com.razorpay.PaymentClient.class);
        razorpayClient.payments = mockPaymentClient;

        JSONObject existingRefundJson = new JSONObject();
        existingRefundJson.put("id", "rfnd_already_created_001");
        existingRefundJson.put("payment_id", paymentId);
        existingRefundJson.put("amount", 250000L); // 2500 INR in paise
        existingRefundJson.put("status", "processed");
        Refund mockExistingRefund = new Refund(existingRefundJson);

        when(mockPaymentClient.fetchAllRefunds(eq(paymentId))).thenReturn(List.of(mockExistingRefund));

        RefundRequest request = RefundRequest.builder()
                .amount(2500.0)
                .idempotencyKey("rfnd_BK-RECOVER-1_10")
                .reason("Retry after socket timeout")
                .build();

        RefundResponse response = paymentService.processRefund(txnRef, request);

        assertNotNull(response);
        assertEquals("rfnd_already_created_001", response.getRefundId());
        assertEquals("PROCESSED", response.getRefundStatus());
        assertEquals(2500.0, response.getRefundAmount());
        assertTrue(response.getMessage().contains("reconciled with existing Razorpay refund"));

        // Critical: Verify that refund() was NEVER called again! Zero duplicate refund created!
        verify(mockPaymentClient, never()).refund(anyString(), any(JSONObject.class));

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());
        PaymentTransaction saved = captor.getValue();
        assertEquals("REFUNDED", saved.getStatus());
        assertEquals("rfnd_already_created_001", saved.getRefundId());
    }

    @Test
    @DisplayName("Unit 3.3: Idempotency Key - Matching key on already completed refund returns existing response")
    void testProcessRefund_IdempotencyKey_AlreadyProcessed() {
        String txnRef = "TXN-IDEMP-TEST";
        String idempKey = "rfnd_BK-IDEMP_123";
        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionRef(txnRef)
                .bookingRef("BK-IDEMP")
                .status("REFUND_INITIATED")
                .idempotencyKey(idempKey)
                .refundId("rfnd_idemp_999")
                .refundStatus("PROCESSED")
                .refundAmount(500.0)
                .build();

        when(paymentRepository.findByTransactionRef(txnRef)).thenReturn(Optional.of(transaction));

        RefundRequest request = RefundRequest.builder()
                .amount(500.0)
                .idempotencyKey(idempKey)
                .build();

        RefundResponse response = paymentService.processRefund(txnRef, request);

        assertNotNull(response);
        assertEquals("rfnd_idemp_999", response.getRefundId());
        assertEquals("PROCESSED", response.getRefundStatus());
        assertTrue(response.getMessage().contains("already processed for idempotency key"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Unit 3.3: Gateway Timeout - Pre-commits REFUND_INITIATED state and throws 502 Bad Gateway")
    void testProcessRefund_GatewayTimeout_PreCommitPersisted() throws Exception {
        String txnRef = "TXN-GATEWAY-FAIL";
        String paymentId = "pay_fail_123";
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(101L)
                .transactionRef(txnRef)
                .bookingRef("BK-GATEWAY-FAIL")
                .amount(1500.0)
                .razorpayPaymentId(paymentId)
                .status("SUCCESS")
                .build();

        when(paymentRepository.findByTransactionRef(txnRef)).thenReturn(Optional.of(transaction));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        com.razorpay.PaymentClient mockPaymentClient = mock(com.razorpay.PaymentClient.class);
        razorpayClient.payments = mockPaymentClient;

        // No existing refund found on gateway
        when(mockPaymentClient.fetchAllRefunds(eq(paymentId))).thenReturn(Collections.emptyList());
        // Gateway throws exception during refund attempt
        when(mockPaymentClient.refund(eq(paymentId), any(JSONObject.class)))
                .thenThrow(new com.razorpay.RazorpayException("Socket timeout talking to gateway"));

        RefundRequest request = RefundRequest.builder()
                .amount(1500.0)
                .idempotencyKey("rfnd_BK-GATEWAY-FAIL_10")
                .reason("Gateway timeout test")
                .build();

        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.processRefund(txnRef, request));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());

        // Verify pre-commit attempt was saved as REFUND_INITIATED with the idempotency key!
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository).save(captor.capture());
        PaymentTransaction saved = captor.getValue();
        assertEquals("REFUND_INITIATED", saved.getStatus());
        assertEquals("rfnd_BK-GATEWAY-FAIL_10", saved.getIdempotencyKey());
    }

    @Test
    @DisplayName("Process Refund: Does not make cyclical cancel PUT call when booking is already CANCELLED in booking-service")
    void testProcessRefund_AlreadyCancelledInBookingService_NoCyclicCall() throws Exception {
        String txnRef = "TXN-ALREADY-CANCELLED";
        String paymentId = "pay_already_cancel_123";
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(202L)
                .transactionRef(txnRef)
                .bookingRef("BK-ALREADY-CANCELLED")
                .amount(1200.0)
                .razorpayPaymentId(paymentId)
                .status("SUCCESS")
                .build();

        when(paymentRepository.findByTransactionRef(txnRef)).thenReturn(Optional.of(transaction));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        com.razorpay.PaymentClient mockPaymentClient = mock(com.razorpay.PaymentClient.class);
        razorpayClient.payments = mockPaymentClient;

        when(mockPaymentClient.fetchAllRefunds(eq(paymentId))).thenReturn(Collections.emptyList());

        JSONObject refundJson = new JSONObject();
        refundJson.put("id", "rfnd_already_cancel_999");
        refundJson.put("payment_id", paymentId);
        refundJson.put("amount", 120000L);
        refundJson.put("status", "processed");
        Refund mockRefund = new Refund(refundJson);
        when(mockPaymentClient.refund(eq(paymentId), any(JSONObject.class))).thenReturn(mockRefund);

        // Mock booking-service returning already CANCELLED
        when(restTemplate.getForEntity(contains("/api/bookings/ref/BK-ALREADY-CANCELLED"), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("id", 10L, "status", "CANCELLED"), HttpStatus.OK));

        RefundRequest request = RefundRequest.builder()
                .amount(1200.0)
                .build();

        RefundResponse response = paymentService.processRefund(txnRef, request);
        assertNotNull(response);
        assertEquals("rfnd_already_cancel_999", response.getRefundId());

        // Verify restTemplate.exchange (the cancel PUT) was NEVER called because booking was already CANCELLED
        verify(restTemplate, never()).exchange(contains("/api/bookings/"), eq(HttpMethod.PUT), any(), eq(Map.class));
    }
}
