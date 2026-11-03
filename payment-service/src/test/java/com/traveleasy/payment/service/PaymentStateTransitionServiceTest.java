package com.traveleasy.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.payment.dto.RecipientDto;
import com.traveleasy.payment.dto.RecipientRole;
import com.traveleasy.payment.model.PaymentOutboxEvent;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.repository.PaymentOutboxEventRepository;
import com.traveleasy.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentStateTransitionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentOutboxEventRepository outboxRepository;

    private PaymentStateTransitionService stateTransitionService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        stateTransitionService = new PaymentStateTransitionService(paymentRepository, outboxRepository, objectMapper);
    }

    private PaymentTransaction createSampleTransaction(String status) {
        return PaymentTransaction.builder()
                .id(1L)
                .transactionRef("TXN-TEST-100")
                .bookingRef("BK-1001")
                .customerId(42L)
                .amount(1500.0)
                .currency("INR")
                .paymentMethod("RAZORPAY")
                .razorpayOrderId("order_test_100")
                .razorpayPaymentId("pay_test_100")
                .status(status)
                .build();
    }

    private List<RecipientDto> createSampleRecipients() {
        return List.of(RecipientDto.builder()
                .userId(42L)
                .email("traveller@example.com")
                .name("John Doe")
                .role(RecipientRole.TRAVELLER)
                .build());
    }

    @Test
    @DisplayName("recordPaymentFailed transitions transaction to FAILED and creates PAYMENT_FAILED outbox event")
    void testRecordPaymentFailed_Success() {
        PaymentTransaction txn = createSampleTransaction("CREATED");
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        String eventId = "PAY-EVT-FAILED-TXN-TEST-100";
        PaymentTransaction result = stateTransitionService.recordPaymentFailed(
                txn, "SIGNATURE_MISMATCH", "Invalid HMAC signature", eventId, createSampleRecipients());

        assertEquals("FAILED", result.getStatus());
        assertEquals("SIGNATURE_MISMATCH", result.getErrorCode());
        assertEquals("Invalid HMAC signature", result.getErrorDescription());

        ArgumentCaptor<PaymentOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(PaymentOutboxEvent.class);
        verify(outboxRepository).saveAndFlush(outboxCaptor.capture());
        PaymentOutboxEvent outboxEvent = outboxCaptor.getValue();

        assertEquals(eventId, outboxEvent.getEventId());
        assertEquals("PAYMENT_FAILED", outboxEvent.getEventType());
        assertEquals("PAYMENT", outboxEvent.getAggregateType());
        assertEquals("TXN-TEST-100", outboxEvent.getAggregateId());
        assertEquals("PENDING", outboxEvent.getStatus());
        assertEquals(0, outboxEvent.getRetryCount());
        assertNotNull(outboxEvent.getPayloadJson());
        assertTrue(outboxEvent.getPayloadJson().contains("PAYMENT_FAILED"));
        assertTrue(outboxEvent.getPayloadJson().contains("traveller@example.com"));
    }

    @Test
    @DisplayName("recordPaymentFailed rejects failure transition if transaction is already SUCCESS")
    void testRecordPaymentFailed_AlreadySuccess_RejectsTransition() {
        PaymentTransaction txn = createSampleTransaction("SUCCESS");

        PaymentTransaction result = stateTransitionService.recordPaymentFailed(
                txn, "LATE_FAILURE", "Late error", "PAY-EVT-FAILED-LATE", createSampleRecipients());

        assertEquals("SUCCESS", result.getStatus());
        verify(paymentRepository, never()).save(any());
        verify(outboxRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("recordRefundInitiated transitions transaction to REFUND_INITIATED and creates PAYMENT_REFUND_INITIATED outbox event")
    void testRecordRefundInitiated_Success() {
        PaymentTransaction txn = createSampleTransaction("SUCCESS");
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        String refundId = "rfnd_test_200";
        String eventId = "PAY-EVT-REFUND-INIT-" + refundId;

        PaymentTransaction result = stateTransitionService.recordRefundInitiated(
                txn, refundId, 1500.0, "initiated", "idemp-key-1", eventId, createSampleRecipients());

        assertEquals("REFUND_INITIATED", result.getStatus());
        assertEquals(refundId, result.getRefundId());
        assertEquals(1500.0, result.getRefundAmount());
        assertEquals("INITIATED", result.getRefundStatus());
        assertNotNull(result.getRefundedAt());

        ArgumentCaptor<PaymentOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(PaymentOutboxEvent.class);
        verify(outboxRepository).saveAndFlush(outboxCaptor.capture());
        PaymentOutboxEvent outboxEvent = outboxCaptor.getValue();

        assertEquals(eventId, outboxEvent.getEventId());
        assertEquals("PAYMENT_REFUND_INITIATED", outboxEvent.getEventType());
        assertEquals("REFUND", outboxEvent.getAggregateType());
        assertEquals(refundId, outboxEvent.getAggregateId());
        assertEquals("PENDING", outboxEvent.getStatus());
    }

    @Test
    @DisplayName("recordRefundInitiated skips transition if transaction is already fully REFUNDED")
    void testRecordRefundInitiated_AlreadyRefunded_Skipped() {
        PaymentTransaction txn = createSampleTransaction("REFUNDED");

        PaymentTransaction result = stateTransitionService.recordRefundInitiated(
                txn, "rfnd_late", 1500.0, "initiated", "idemp-key-2", "PAY-EVT-LATE", createSampleRecipients());

        assertEquals("REFUNDED", result.getStatus());
        verify(paymentRepository, never()).save(any());
        verify(outboxRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("recordRefundCompleted transitions transaction to REFUNDED and creates PAYMENT_REFUND_COMPLETED outbox event")
    void testRecordRefundCompleted_Success() {
        PaymentTransaction txn = createSampleTransaction("REFUND_INITIATED");
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        String refundId = "rfnd_test_300";
        String eventId = "PAY-EVT-REFUND-COMP-" + refundId;

        PaymentTransaction result = stateTransitionService.recordRefundCompleted(
                txn, refundId, 1500.0, "idemp-key-3", eventId, createSampleRecipients());

        assertEquals("REFUNDED", result.getStatus());
        assertEquals(refundId, result.getRefundId());
        assertEquals("PROCESSED", result.getRefundStatus());
        assertEquals(1500.0, result.getRefundAmount());

        ArgumentCaptor<PaymentOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(PaymentOutboxEvent.class);
        verify(outboxRepository).saveAndFlush(outboxCaptor.capture());
        PaymentOutboxEvent outboxEvent = outboxCaptor.getValue();

        assertEquals(eventId, outboxEvent.getEventId());
        assertEquals("PAYMENT_REFUND_COMPLETED", outboxEvent.getEventType());
        assertEquals("REFUND", outboxEvent.getAggregateType());
        assertEquals(refundId, outboxEvent.getAggregateId());
    }

    @Test
    @DisplayName("Duplicate outbox event: existsByEventId check safely skips duplicate insertion without throwing or rolling back")
    void testDuplicateOutboxEvent_SafelyIgnored() {
        PaymentTransaction txn = createSampleTransaction("CREATED");
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.existsByEventId("PAY-EVT-DUP-1")).thenReturn(true);

        assertDoesNotThrow(() -> stateTransitionService.recordPaymentFailed(
                txn, "ERR_DUP", "Duplicate attempt", "PAY-EVT-DUP-1", createSampleRecipients()));

        verify(outboxRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Unrelated database error during outbox insertion is NOT swallowed and fails the transaction")
    void testUnrelatedIntegrityViolation_ThrowsAndDoesNotSwallow() {
        PaymentTransaction txn = createSampleTransaction("CREATED");
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.existsByEventId("PAY-EVT-FAIL-1")).thenReturn(false);
        when(outboxRepository.saveAndFlush(any(PaymentOutboxEvent.class)))
                .thenThrow(new DataIntegrityViolationException("Column 'payload_json' cannot be null"));

        assertThrows(DataIntegrityViolationException.class, () ->
                stateTransitionService.recordPaymentFailed(
                        txn, "ERR_FAIL", "Fatal error", "PAY-EVT-FAIL-1", createSampleRecipients()));
    }
}
