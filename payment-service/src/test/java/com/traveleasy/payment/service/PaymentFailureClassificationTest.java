package com.traveleasy.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayClient;
import com.traveleasy.payment.config.RazorpayConfig;
import com.traveleasy.payment.dto.RecipientDto;
import com.traveleasy.payment.dto.RecipientRole;
import com.traveleasy.payment.dto.VerifyPaymentRequest;
import com.traveleasy.payment.exception.PaymentException;
import com.traveleasy.payment.model.PaymentOutboxEvent;
import com.traveleasy.payment.model.PaymentTransaction;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentFailureClassificationTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentOutboxEventRepository outboxRepository;

    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentRecipientResolutionService recipientResolutionService;

    @Mock
    private RazorpayConfig razorpayConfig;

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private RestTemplate restTemplate;

    private PaymentStateTransitionService stateTransitionService;
    private PaymentServiceImpl paymentService;

    private static final String TEST_KEY_SECRET = "mockSecretKey456789";
    private static final String TEST_WEBHOOK_SECRET = "mockWebhookSecret987";

    @BeforeEach
    void setUp() {
        lenient().when(razorpayConfig.getKeySecret()).thenReturn(TEST_KEY_SECRET);
        lenient().when(razorpayConfig.getWebhookSecret()).thenReturn(TEST_WEBHOOK_SECRET);
        lenient().when(razorpayConfig.isConfigured()).thenReturn(true);
        lenient().when(recipientResolutionService.resolveTravellerRecipient(anyString(), any())).thenReturn(
                List.of(RecipientDto.builder().userId(10L).name("Test User").role(RecipientRole.TRAVELLER).build()));
        lenient().when(webhookEventRepository.claimWebhookEvent(anyString(), anyString(), any(), any())).thenReturn(1);
        lenient().when(webhookEventRepository.findByWebhookEventId(anyString())).thenReturn(Optional.empty());

        stateTransitionService = spy(new PaymentStateTransitionService(paymentRepository, outboxRepository, new ObjectMapper()));
        paymentService = new PaymentServiceImpl(
                paymentRepository,
                razorpayConfig,
                razorpayClient,
                restTemplate,
                stateTransitionService,
                recipientResolutionService,
                webhookEventRepository
        );
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
    @DisplayName("Client signature mismatch updates local state to FAILED but does NOT generate a PAYMENT_FAILED outbox event")
    void testClientSignatureMismatch_DoesNotCreateOutboxEvent() {
        String orderId = "order_client_mismatch";
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(1L)
                .transactionRef("TXN-CLIENT-MISMATCH")
                .bookingRef("BK-MISMATCH")
                .customerId(10L)
                .razorpayOrderId(orderId)
                .status("CREATED")
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(txn));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        VerifyPaymentRequest request = VerifyPaymentRequest.builder()
                .razorpayOrderId(orderId)
                .razorpayPaymentId("pay_bogus_123")
                .razorpaySignature("invalid_tampered_signature_hex")
                .bookingRef("BK-MISMATCH")
                .build();

        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.verifyPayment(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());

        // Verify local transaction was updated to FAILED with SIGNATURE_MISMATCH
        assertEquals("FAILED", txn.getStatus());
        assertEquals("SIGNATURE_MISMATCH", txn.getErrorCode());

        // CRITICAL: Verify that outboxRepository was NEVER called to persist an event!
        verify(outboxRepository, never()).save(any());
        verify(outboxRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Internal verification exception updates local state but does NOT generate a customer-facing outbox event")
    void testInternalVerificationException_DoesNotCreateOutboxEvent() {
        String orderId = "order_internal_err";
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(2L)
                .transactionRef("TXN-INTERNAL-ERR")
                .bookingRef("BK-ERR")
                .customerId(10L)
                .razorpayOrderId(orderId)
                .status("CREATED")
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(txn));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        // Malformed request causing parsing exception inside verifyPayment
        VerifyPaymentRequest request = VerifyPaymentRequest.builder()
                .razorpayOrderId(orderId)
                .razorpayPaymentId(null) // null triggers NPE or error in Utils.verifyPaymentSignature
                .razorpaySignature("fake_sig")
                .bookingRef("BK-ERR")
                .build();

        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.verifyPayment(request));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());

        assertEquals("FAILED", txn.getStatus());
        assertEquals("VERIFICATION_EXCEPTION", txn.getErrorCode());

        // CRITICAL: Verify outbox is not called
        verify(outboxRepository, never()).save(any());
        verify(outboxRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Authoritative payment.failed webhook creates exactly ONE PAYMENT_FAILED outbox event")
    void testAuthoritativePaymentFailedWebhook_CreatesOutboxEvent() throws Exception {
        String orderId = "order_auth_failed";
        String eventId = "evt_auth_failed_001";
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(3L)
                .transactionRef("TXN-AUTH-FAILED")
                .bookingRef("BK-AUTH-FAILED")
                .customerId(10L)
                .amount(1200.0)
                .razorpayOrderId(orderId)
                .status("CREATED")
                .build();

        JSONObject entity = new JSONObject();
        entity.put("id", "pay_failed_gateway_01");
        entity.put("order_id", orderId);
        entity.put("error_code", "BAD_REQUEST_ERROR");
        entity.put("error_description", "Payment was declined by issuing bank");
        entity.put("error_reason", "payment_failed");

        JSONObject payment = new JSONObject();
        payment.put("entity", entity);

        JSONObject payload = new JSONObject();
        payload.put("payment", payment);

        JSONObject root = new JSONObject();
        root.put("event", "payment.failed");
        root.put("event_id", eventId);
        root.put("payload", payload);

        String rawPayload = root.toString();
        String validSignature = calculateHmac(rawPayload, TEST_WEBHOOK_SECRET);

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(txn));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.processWebhook(rawPayload, validSignature);

        // Verify status updated to FAILED
        assertEquals("FAILED", txn.getStatus());
        assertEquals("BAD_REQUEST_ERROR", txn.getErrorCode());
        assertEquals("Payment was declined by issuing bank", txn.getErrorDescription());

        // Verify exactly one outbox event created with deterministic ID
        ArgumentCaptor<PaymentOutboxEvent> captor = ArgumentCaptor.forClass(PaymentOutboxEvent.class);
        verify(outboxRepository, times(1)).saveAndFlush(captor.capture());
        PaymentOutboxEvent event = captor.getValue();

        assertEquals("PAY-EVT-FAILED-TXN-AUTH-FAILED", event.getEventId());
        assertEquals("PAYMENT_FAILED", event.getEventType());
        assertEquals("PAYMENT", event.getAggregateType());
        assertEquals("TXN-AUTH-FAILED", event.getAggregateId());
        assertTrue(event.getPayloadJson().contains("Payment was declined by issuing bank"));
    }

    @Test
    @DisplayName("Late payment.failed webhook received after successful capture does NOT downgrade payment")
    void testLatePaymentFailedWebhook_DoesNotDowngradeSuccess() throws Exception {
        String orderId = "order_already_success";
        String eventId = "evt_late_failed_002";
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(4L)
                .transactionRef("TXN-ALREADY-SUCCESS")
                .bookingRef("BK-SUCCESS")
                .customerId(10L)
                .amount(1500.0)
                .razorpayOrderId(orderId)
                .status("SUCCESS") // Already successful!
                .build();

        JSONObject entity = new JSONObject();
        entity.put("id", "pay_failed_late_02");
        entity.put("order_id", orderId);
        entity.put("error_code", "LATE_DECLINE");
        entity.put("error_description", "Late decline signal");

        JSONObject payment = new JSONObject();
        payment.put("entity", entity);

        JSONObject payload = new JSONObject();
        payload.put("payment", payment);

        JSONObject root = new JSONObject();
        root.put("event", "payment.failed");
        root.put("event_id", eventId);
        root.put("payload", payload);

        String rawPayload = root.toString();
        String validSignature = calculateHmac(rawPayload, TEST_WEBHOOK_SECRET);

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(txn));

        paymentService.processWebhook(rawPayload, validSignature);

        // Status must remain SUCCESS
        assertEquals("SUCCESS", txn.getStatus());

        // Zero outbox failure events written
        verify(outboxRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Late client signature mismatch on an already SUCCESS transaction does NOT overwrite SUCCESS state")
    void testLateClientSignatureMismatch_DoesNotOverwriteSuccess() {
        String orderId = "order_success_late_client";
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(5L)
                .transactionRef("TXN-SUCCESS-LATE")
                .bookingRef("BK-SUCCESS-LATE")
                .customerId(10L)
                .razorpayOrderId(orderId)
                .razorpayPaymentId("pay_success_123")
                .status("SUCCESS")
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(txn));

        VerifyPaymentRequest request = VerifyPaymentRequest.builder()
                .razorpayOrderId(orderId)
                .razorpayPaymentId("pay_success_123")
                .razorpaySignature("invalid_late_sig")
                .bookingRef("BK-SUCCESS-LATE")
                .build();

        // Already verified returns verified response idempotently without checking signature
        var response = paymentService.verifyPayment(request);
        assertTrue(response.isVerified());
        assertEquals("SUCCESS", txn.getStatus());
        verify(paymentRepository, never()).save(any());
        verify(outboxRepository, never()).saveAndFlush(any());
    }
}
