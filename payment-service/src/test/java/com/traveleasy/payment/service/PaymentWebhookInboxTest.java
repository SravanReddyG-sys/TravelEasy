package com.traveleasy.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayClient;
import com.traveleasy.payment.config.RazorpayConfig;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.model.PaymentWebhookEvent;
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
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentWebhookInboxTest {

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

    private static final String TEST_WEBHOOK_SECRET = "mockWebhookSecret987";

    @BeforeEach
    void setUp() {
        lenient().when(razorpayConfig.getWebhookSecret()).thenReturn(TEST_WEBHOOK_SECRET);
        lenient().when(razorpayConfig.isConfigured()).thenReturn(true);
        lenient().when(recipientResolutionService.resolveTravellerRecipient(anyString(), any())).thenReturn(Collections.emptyList());
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
    @DisplayName("Webhook Inbox: New payment.captured event is registered as RECEIVED, claimed, processed, and marked PROCESSED")
    void testProcessWebhook_NewEvent_RecordedAndProcessed() throws Exception {
        String eventId = "evt_razorpay_12345";
        String orderId = "order_wh_new_001";
        String paymentId = "pay_wh_new_001";

        JSONObject entity = new JSONObject();
        entity.put("id", paymentId);
        entity.put("order_id", orderId);

        JSONObject payment = new JSONObject();
        payment.put("entity", entity);

        JSONObject payload = new JSONObject();
        payload.put("payment", payment);

        JSONObject root = new JSONObject();
        root.put("event", "payment.captured");
        root.put("event_id", eventId);
        root.put("payload", payload);

        String rawPayload = root.toString();
        String validSignature = calculateHmac(rawPayload, TEST_WEBHOOK_SECRET);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionRef("TXN-WH-NEW-1")
                .bookingRef("BK-WH-NEW-1")
                .razorpayOrderId(orderId)
                .status("CREATED")
                .build();

        when(webhookEventRepository.findByWebhookEventId(eventId)).thenReturn(Optional.empty());
        when(webhookEventRepository.claimWebhookEvent(eq(eventId), anyString(), any(), any())).thenReturn(1);
        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(transaction));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.processWebhook(rawPayload, validSignature);

        // Verify registered in inbox as RECEIVED
        ArgumentCaptor<PaymentWebhookEvent> captor = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
        verify(webhookEventRepository).saveAndFlush(captor.capture());
        PaymentWebhookEvent savedInbox = captor.getValue();

        assertEquals(eventId, savedInbox.getWebhookEventId());
        assertEquals("payment.captured", savedInbox.getEventType());
        assertEquals("RECEIVED", savedInbox.getStatus());

        // Verify lease was claimed
        verify(webhookEventRepository).claimWebhookEvent(eq(eventId), anyString(), any(), any());

        // Verify marked PROCESSED after completion
        verify(webhookEventRepository).markProcessed(eq(eventId), any());
    }

    @Test
    @DisplayName("Webhook Inbox: Duplicate webhook event with status PROCESSED is acknowledged without reprocessing")
    void testProcessWebhook_DuplicateProcessedEvent_AcknowledgedWithoutReprocessing() throws Exception {
        String eventId = "evt_razorpay_duplicate_999";

        JSONObject root = new JSONObject();
        root.put("event", "payment.captured");
        root.put("event_id", eventId);
        root.put("payload", new JSONObject());

        String rawPayload = root.toString();
        String validSignature = calculateHmac(rawPayload, TEST_WEBHOOK_SECRET);

        PaymentWebhookEvent processedEvent = PaymentWebhookEvent.builder()
                .webhookEventId(eventId)
                .status("PROCESSED")
                .build();

        when(webhookEventRepository.findByWebhookEventId(eventId)).thenReturn(Optional.of(processedEvent));

        paymentService.processWebhook(rawPayload, validSignature);

        // Verify no business logic ran, no database lookup on transaction repository, no lease claim
        verify(paymentRepository, never()).findByRazorpayOrderId(anyString());
        verify(paymentRepository, never()).save(any());
        verify(webhookEventRepository, never()).claimWebhookEvent(anyString(), anyString(), any(), any());
        verify(webhookEventRepository, never()).markProcessed(anyString(), any());
    }

    @Test
    @DisplayName("Webhook Inbox: Incomplete webhook event (status RECEIVED) is reclaimed and processed upon retry")
    void testProcessWebhook_IncompleteEventReceived_ReclaimedAndProcessed() throws Exception {
        String eventId = "evt_razorpay_received_retry";
        String orderId = "order_wh_retry_001";
        String paymentId = "pay_wh_retry_001";

        JSONObject entity = new JSONObject();
        entity.put("id", paymentId);
        entity.put("order_id", orderId);

        JSONObject payment = new JSONObject();
        payment.put("entity", entity);

        JSONObject payload = new JSONObject();
        payload.put("payment", payment);

        JSONObject root = new JSONObject();
        root.put("event", "payment.captured");
        root.put("event_id", eventId);
        root.put("payload", payload);

        String rawPayload = root.toString();
        String validSignature = calculateHmac(rawPayload, TEST_WEBHOOK_SECRET);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionRef("TXN-WH-RETRY-1")
                .bookingRef("BK-WH-RETRY-1")
                .razorpayOrderId(orderId)
                .status("CREATED")
                .build();

        // Existing event stuck in RECEIVED state from previous crash
        PaymentWebhookEvent stuckEvent = PaymentWebhookEvent.builder()
                .webhookEventId(eventId)
                .status("RECEIVED")
                .build();

        when(webhookEventRepository.findByWebhookEventId(eventId)).thenReturn(Optional.of(stuckEvent));
        when(webhookEventRepository.claimWebhookEvent(eq(eventId), anyString(), any(), any())).thenReturn(1);
        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(transaction));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.processWebhook(rawPayload, validSignature);

        // Verify business logic executed
        verify(paymentRepository).save(any(PaymentTransaction.class));
        // Verify lease was claimed
        verify(webhookEventRepository).claimWebhookEvent(eq(eventId), anyString(), any(), any());
        // Verify marked PROCESSED
        verify(webhookEventRepository).markProcessed(eq(eventId), any());
    }

    @Test
    @DisplayName("Webhook Inbox: refund.processed event transitions transaction and records outbox event")
    void testProcessWebhook_RefundProcessed_TransitionsAndRecordsOutbox() throws Exception {
        String eventId = "evt_refund_processed_555";
        String refundId = "rfnd_wh_555";
        String paymentId = "pay_wh_orig_555";

        JSONObject entity = new JSONObject();
        entity.put("id", refundId);
        entity.put("payment_id", paymentId);
        entity.put("amount", 200000L); // 2000 INR
        entity.put("status", "processed");

        JSONObject refund = new JSONObject();
        refund.put("entity", entity);

        JSONObject payload = new JSONObject();
        payload.put("refund", refund);

        JSONObject root = new JSONObject();
        root.put("event", "refund.processed");
        root.put("event_id", eventId);
        root.put("payload", payload);

        String rawPayload = root.toString();
        String validSignature = calculateHmac(rawPayload, TEST_WEBHOOK_SECRET);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionRef("TXN-WH-REFUND-5")
                .bookingRef("BK-WH-REFUND-5")
                .razorpayPaymentId(paymentId)
                .amount(2000.0)
                .status("REFUND_INITIATED")
                .build();

        when(webhookEventRepository.findByWebhookEventId(eventId)).thenReturn(Optional.empty());
        when(webhookEventRepository.claimWebhookEvent(eq(eventId), anyString(), any(), any())).thenReturn(1);
        when(paymentRepository.findByRazorpayPaymentId(paymentId)).thenReturn(Optional.of(transaction));
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.processWebhook(rawPayload, validSignature);

        // Verify transaction transitioned to REFUNDED
        assertEquals("REFUNDED", transaction.getStatus());
        assertEquals(refundId, transaction.getRefundId());

        // Verify outbox completed event was recorded
        verify(outboxRepository).saveAndFlush(any());

        // Verify inbox event was marked PROCESSED
        verify(webhookEventRepository).markProcessed(eq(eventId), any());
    }
}
