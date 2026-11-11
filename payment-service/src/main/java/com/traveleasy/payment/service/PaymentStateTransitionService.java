package com.traveleasy.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.payment.dto.*;
import com.traveleasy.payment.model.PaymentOutboxEvent;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.repository.PaymentOutboxEventRepository;
import com.traveleasy.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentStateTransitionService {

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxEventRepository outboxRepository;
    private ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentStateTransitionService(PaymentRepository paymentRepository,
                                         PaymentOutboxEventRepository outboxRepository,
                                         @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Atomically transitions a payment transaction to FAILED and creates a PAYMENT_FAILED outbox event.
     */
    @Transactional
    public PaymentTransaction recordPaymentFailed(PaymentTransaction txn, String errorCode, String errorDesc,
                                                  String eventId, List<RecipientDto> recipients) {
        if ("SUCCESS".equalsIgnoreCase(txn.getStatus()) || "REFUNDED".equalsIgnoreCase(txn.getStatus())) {
            log.warn("Cannot mark transaction {} in status {} as FAILED. Conflicting failure event rejected.",
                    txn.getTransactionRef(), txn.getStatus());
            return txn;
        }

        txn.setStatus("FAILED");
        txn.setErrorCode(errorCode);
        txn.setErrorDescription(errorDesc);
        PaymentTransaction saved = paymentRepository.save(txn);

        NotificationEventRequest eventRequest = buildPaymentFailedEventRequest(saved, eventId, errorDesc, recipients);
        persistOutboxRecordSafely("PAYMENT", saved.getTransactionRef(), "PAYMENT_FAILED", eventId, eventRequest);

        log.info("Recorded PAYMENT_FAILED state transition for txn {} with outbox event {}", saved.getTransactionRef(), eventId);
        return saved;
    }

    /**
     * Atomically transitions a payment transaction to REFUND_INITIATED and creates a PAYMENT_REFUND_INITIATED outbox event.
     * Called ONLY after Razorpay gateway confirms acceptance of the refund.
     */
    @Transactional
    public PaymentTransaction recordRefundInitiated(PaymentTransaction txn, String refundId, Double refundAmount,
                                                    String refundStatus, String idempotencyKey,
                                                    String eventId, List<RecipientDto> recipients) {
        if ("REFUNDED".equalsIgnoreCase(txn.getStatus())) {
            log.info("Transaction {} is already fully REFUNDED. Skipping REFUND_INITIATED transition.", txn.getTransactionRef());
            return txn;
        }

        txn.setStatus("REFUND_INITIATED");
        txn.setRefundId(refundId);
        txn.setRefundAmount(refundAmount);
        txn.setRefundStatus(refundStatus != null ? refundStatus.toUpperCase() : "INITIATED");
        txn.setRefundedAt(LocalDateTime.now());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            txn.setIdempotencyKey(idempotencyKey);
        }
        PaymentTransaction saved = paymentRepository.save(txn);
        if (saved == null) {
            saved = txn;
        }

        NotificationEventRequest eventRequest = buildRefundInitiatedEventRequest(saved, refundId, refundAmount, eventId, recipients);
        persistOutboxRecordSafely("REFUND", refundId, "PAYMENT_REFUND_INITIATED", eventId, eventRequest);

        log.info("Recorded PAYMENT_REFUND_INITIATED for txn {} (refundId: {}) with outbox event {}",
                saved.getTransactionRef(), refundId, eventId);
        return saved;
    }

    /**
     * Atomically transitions a payment transaction to REFUNDED and creates a PAYMENT_REFUND_COMPLETED outbox event.
     */
    @Transactional
    public PaymentTransaction recordRefundCompleted(PaymentTransaction txn, String refundId, Double refundAmount,
                                                    String idempotencyKey, String eventId, List<RecipientDto> recipients) {
        txn.setStatus("REFUNDED");
        txn.setRefundId(refundId);
        txn.setRefundAmount(refundAmount);
        txn.setRefundStatus("PROCESSED");
        txn.setRefundedAt(LocalDateTime.now());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            txn.setIdempotencyKey(idempotencyKey);
        }
        PaymentTransaction saved = paymentRepository.save(txn);
        if (saved == null) {
            saved = txn;
        }

        NotificationEventRequest eventRequest = buildRefundCompletedEventRequest(saved, refundId, refundAmount, eventId, recipients);
        persistOutboxRecordSafely("REFUND", refundId, "PAYMENT_REFUND_COMPLETED", eventId, eventRequest);

        log.info("Recorded PAYMENT_REFUND_COMPLETED for txn {} (refundId: {}) with outbox event {}",
                saved.getTransactionRef(), refundId, eventId);
        return saved;
    }

    /**
     * Records an unsuccessful or unresolved gateway refund outcome without transitioning
     * the transaction to REFUNDED or emitting a refund-completed event.
     */
    @Transactional
    public PaymentTransaction recordRefundFailed(PaymentTransaction txn, String refundId, Double refundAmount,
                                                 String refundStatus, String errorDescription, String idempotencyKey) {
        if ("REFUNDED".equalsIgnoreCase(txn.getStatus())
                && "PROCESSED".equalsIgnoreCase(txn.getRefundStatus())) {
            log.info("Transaction {} is already terminally REFUNDED/PROCESSED. Ignoring refund failure status {}.",
                    txn.getTransactionRef(), refundStatus);
            return txn;
        }

        String normalizedStatus = (refundStatus != null && !refundStatus.isBlank())
                ? refundStatus.trim().toUpperCase()
                : "FAILED";
        // Preserve captured collection eligibility (SUCCESS) while marking refundStatus as non-processed failure
        if ("REFUND_INITIATED".equalsIgnoreCase(txn.getStatus()) || txn.getStatus() == null || txn.getStatus().isBlank()) {
            txn.setStatus("SUCCESS");
        }
        if (refundId != null && !refundId.isBlank()) {
            txn.setRefundId(refundId);
        }
        if (refundAmount != null && refundAmount > 0) {
            txn.setRefundAmount(refundAmount);
        }
        txn.setRefundStatus(normalizedStatus);
        txn.setErrorCode("REFUND_" + normalizedStatus);
        if (errorDescription != null && !errorDescription.isBlank()) {
            txn.setErrorDescription(errorDescription.length() <= 500
                    ? errorDescription
                    : errorDescription.substring(0, 500));
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            txn.setIdempotencyKey(idempotencyKey);
        }

        PaymentTransaction saved = paymentRepository.save(txn);
        if (saved == null) {
            saved = txn;
        }
        log.warn("Recorded refund failure state {} for txn {} (refundId={})",
                normalizedStatus, saved.getTransactionRef(), refundId);
        return saved;
    }

    private void persistOutboxRecordSafely(String aggregateType, String aggregateId, String eventType,
                                          String eventId, NotificationEventRequest eventRequest) {
        // Idempotency: verify if event with this specific event ID has already been persisted
        if (outboxRepository.existsByEventId(eventId)) {
            log.info("Outbox event with eventId {} already exists. Skipping duplicate insertion.", eventId);
            return;
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(eventRequest);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload for eventId {}: {}", eventId, e.getMessage(), e);
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }

        PaymentOutboxEvent outboxEvent = PaymentOutboxEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .payloadJson(payloadJson)
                .status("PENDING")
                .retryCount(0)
                .maxRetries(5)
                .createdAt(Instant.now())
                .build();

        outboxRepository.saveAndFlush(outboxEvent);
    }

    private NotificationEventRequest buildPaymentFailedEventRequest(PaymentTransaction txn, String eventId,
                                                                    String errorDesc, List<RecipientDto> recipients) {
        String bookingRef = txn.getBookingRef() != null ? txn.getBookingRef() : "UNKNOWN";
        String formattedAmount = txn.getAmount() != null ? String.format("%.2f", txn.getAmount()) : "0.00";
        String reason = (errorDesc != null && !errorDesc.isBlank()) ? errorDesc : "Transaction declined by gateway";

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("bookingRef", bookingRef);
        templateData.put("amount", formattedAmount);
        templateData.put("reason", reason);

        EventContentDto content = EventContentDto.builder()
                .title("Payment Failed - " + bookingRef)
                .summary("Your payment for booking " + bookingRef + " has failed.")
                .actionUrl("/my-bookings")
                .template("payment-failed")
                .templateData(templateData)
                .build();

        return NotificationEventRequest.builder()
                .eventId(eventId)
                .eventType("PAYMENT_FAILED")
                .aggregateType("PAYMENT")
                .aggregateId(txn.getTransactionRef())
                .timestamp(Instant.now())
                .recipients(recipients)
                .content(content)
                .build();
    }

    private NotificationEventRequest buildRefundInitiatedEventRequest(PaymentTransaction txn, String refundId,
                                                                      Double refundAmount, String eventId,
                                                                      List<RecipientDto> recipients) {
        String bookingRef = txn.getBookingRef() != null ? txn.getBookingRef() : "UNKNOWN";
        String formattedRefund = refundAmount != null ? String.format("%.2f", refundAmount) : "0.00";

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("bookingRef", bookingRef);
        templateData.put("refundId", refundId);
        templateData.put("refundAmount", formattedRefund);

        EventContentDto content = EventContentDto.builder()
                .title("Refund Initiated - " + bookingRef)
                .summary("Refund of ₹" + formattedRefund + " initiated for booking " + bookingRef + ".")
                .actionUrl("/my-bookings")
                .template("payment-refund-initiated")
                .templateData(templateData)
                .build();

        return NotificationEventRequest.builder()
                .eventId(eventId)
                .eventType("PAYMENT_REFUND_INITIATED")
                .aggregateType("REFUND")
                .aggregateId(refundId)
                .timestamp(Instant.now())
                .recipients(recipients)
                .content(content)
                .build();
    }

    private NotificationEventRequest buildRefundCompletedEventRequest(PaymentTransaction txn, String refundId,
                                                                      Double refundAmount, String eventId,
                                                                      List<RecipientDto> recipients) {
        String bookingRef = txn.getBookingRef() != null ? txn.getBookingRef() : "UNKNOWN";
        String formattedRefund = refundAmount != null ? String.format("%.2f", refundAmount) : "0.00";

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("bookingRef", bookingRef);
        templateData.put("refundId", refundId);
        templateData.put("refundAmount", formattedRefund);

        EventContentDto content = EventContentDto.builder()
                .title("Refund Completed - " + bookingRef)
                .summary("Refund of ₹" + formattedRefund + " completed for booking " + bookingRef + ".")
                .actionUrl("/my-bookings")
                .template("payment-refund-completed")
                .templateData(templateData)
                .build();

        return NotificationEventRequest.builder()
                .eventId(eventId)
                .eventType("PAYMENT_REFUND_COMPLETED")
                .aggregateType("REFUND")
                .aggregateId(refundId)
                .timestamp(Instant.now())
                .recipients(recipients)
                .content(content)
                .build();
    }
}
