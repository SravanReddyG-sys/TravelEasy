package com.traveleasy.payment.integration;

import com.traveleasy.payment.dto.RecipientDto;
import com.traveleasy.payment.dto.RecipientRole;
import com.traveleasy.payment.model.PaymentOutboxEvent;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.publisher.PaymentOutboxPublisher;
import com.traveleasy.payment.repository.PaymentOutboxEventRepository;
import com.traveleasy.payment.repository.PaymentRepository;
import com.traveleasy.payment.service.PaymentInternalTokenService;
import com.traveleasy.payment.service.PaymentStateTransitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@SpringBootTest
public class PaymentLifecycleOutboxIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentOutboxEventRepository outboxRepository;

    @Autowired
    private PaymentStateTransitionService stateTransitionService;

    @Autowired
    private PaymentOutboxPublisher publisher;

    @MockBean
    private PaymentInternalTokenService tokenService;

    private MockRestServiceServer mockServer;
    private RestTemplate publisherRestTemplate;

    private static final String NOTIF_URL = "http://localhost:8086";

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();

        publisherRestTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(publisherRestTemplate);
        publisher.setRestTemplate(publisherRestTemplate);
        publisher.setNotificationServiceUrl(NOTIF_URL);
        publisher.setPublisherEnabled(true);

        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.integration.token");
    }

    private List<RecipientDto> createSampleRecipients() {
        return List.of(RecipientDto.builder()
                .userId(101L)
                .email("user101@example.com")
                .name("Alice Smith")
                .role(RecipientRole.TRAVELLER)
                .build());
    }

    @Test
    @DisplayName("End-to-End: Payment failure records transaction state and publishes PAYMENT_FAILED event")
    void testPaymentFailure_EndToEnd_RecordedAndPublished() {
        // 1. Create initial transaction in CREATED status
        PaymentTransaction txn = paymentRepository.save(PaymentTransaction.builder()
                .transactionRef("TXN-E2E-FAIL-01")
                .bookingRef("BK-E2E-001")
                .customerId(101L)
                .amount(850.0)
                .currency("INR")
                .paymentMethod("RAZORPAY")
                .razorpayOrderId("order_e2e_001")
                .status("CREATED")
                .build());

        // 2. Perform atomic state transition to FAILED
        String eventId = "PAY-EVT-FAILED-" + txn.getTransactionRef();
        stateTransitionService.recordPaymentFailed(
                txn, "SIGNATURE_MISMATCH", "HMAC verification failed", eventId, createSampleRecipients());

        // 3. Verify database state
        PaymentTransaction updatedTxn = paymentRepository.findByTransactionRef(txn.getTransactionRef()).orElseThrow();
        assertEquals("FAILED", updatedTxn.getStatus());
        assertEquals("SIGNATURE_MISMATCH", updatedTxn.getErrorCode());

        PaymentOutboxEvent outboxEvent = outboxRepository.findByEventId(eventId).orElseThrow();
        assertEquals("PENDING", outboxEvent.getStatus());
        assertEquals("PAYMENT_FAILED", outboxEvent.getEventType());
        assertEquals("PAYMENT", outboxEvent.getAggregateType());
        assertEquals(txn.getTransactionRef(), outboxEvent.getAggregateId());

        // 4. Mock Notification Service ingestion response
        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer mock.jwt.integration.token"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"INGESTED\"}"));

        // 5. Trigger publisher
        publisher.publishOutboxEvents();
        mockServer.verify();

        // 6. Verify event is now PUBLISHED in database
        PaymentOutboxEvent publishedEvent = outboxRepository.findByEventId(eventId).orElseThrow();
        assertEquals("PUBLISHED", publishedEvent.getStatus());
        assertNotNull(publishedEvent.getPublishedAt());
        assertNull(publishedEvent.getLockedBy());
    }

    @Test
    @DisplayName("End-to-End: Refund initiation records REFUND_INITIATED and publishes event")
    void testRefundInitiation_EndToEnd_RecordedAndPublished() {
        PaymentTransaction txn = paymentRepository.save(PaymentTransaction.builder()
                .transactionRef("TXN-E2E-REFUND-02")
                .bookingRef("BK-E2E-002")
                .customerId(102L)
                .amount(1200.0)
                .currency("INR")
                .paymentMethod("RAZORPAY")
                .razorpayOrderId("order_e2e_002")
                .razorpayPaymentId("pay_e2e_002")
                .status("SUCCESS")
                .build());

        String refundId = "rfnd_e2e_002";
        String eventId = "PAY-EVT-REFUND-INIT-" + refundId;

        stateTransitionService.recordRefundInitiated(
                txn, refundId, 1200.0, "initiated", "idemp-e2e-2", eventId, createSampleRecipients());

        PaymentTransaction updatedTxn = paymentRepository.findByTransactionRef(txn.getTransactionRef()).orElseThrow();
        assertEquals("REFUND_INITIATED", updatedTxn.getStatus());
        assertEquals(refundId, updatedTxn.getRefundId());
        assertEquals(1200.0, updatedTxn.getRefundAmount());

        PaymentOutboxEvent outboxEvent = outboxRepository.findByEventId(eventId).orElseThrow();
        assertEquals("PENDING", outboxEvent.getStatus());
        assertEquals("PAYMENT_REFUND_INITIATED", outboxEvent.getEventType());
        assertEquals("REFUND", outboxEvent.getAggregateType());
        assertEquals(refundId, outboxEvent.getAggregateId());

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer mock.jwt.integration.token"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"INGESTED\"}"));

        publisher.publishOutboxEvents();
        mockServer.verify();

        PaymentOutboxEvent publishedEvent = outboxRepository.findByEventId(eventId).orElseThrow();
        assertEquals("PUBLISHED", publishedEvent.getStatus());
        assertNotNull(publishedEvent.getPublishedAt());
    }

    @Test
    @DisplayName("End-to-End: Refund completion records REFUNDED and publishes event")
    void testRefundCompletion_EndToEnd_RecordedAndPublished() {
        PaymentTransaction txn = paymentRepository.save(PaymentTransaction.builder()
                .transactionRef("TXN-E2E-REFUND-03")
                .bookingRef("BK-E2E-003")
                .customerId(103L)
                .amount(2100.0)
                .currency("INR")
                .paymentMethod("RAZORPAY")
                .razorpayOrderId("order_e2e_003")
                .razorpayPaymentId("pay_e2e_003")
                .status("REFUND_INITIATED")
                .build());

        String refundId = "rfnd_e2e_003";
        String eventId = "PAY-EVT-REFUND-COMP-" + refundId;

        stateTransitionService.recordRefundCompleted(
                txn, refundId, 2100.0, "idemp-e2e-3", eventId, createSampleRecipients());

        PaymentTransaction updatedTxn = paymentRepository.findByTransactionRef(txn.getTransactionRef()).orElseThrow();
        assertEquals("REFUNDED", updatedTxn.getStatus());
        assertEquals(refundId, updatedTxn.getRefundId());
        assertEquals("PROCESSED", updatedTxn.getRefundStatus());

        PaymentOutboxEvent outboxEvent = outboxRepository.findByEventId(eventId).orElseThrow();
        assertEquals("PENDING", outboxEvent.getStatus());
        assertEquals("PAYMENT_REFUND_COMPLETED", outboxEvent.getEventType());
        assertEquals("REFUND", outboxEvent.getAggregateType());
        assertEquals(refundId, outboxEvent.getAggregateId());

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"ALREADY_INGESTED\"}"));

        publisher.publishOutboxEvents();
        mockServer.verify();

        PaymentOutboxEvent publishedEvent = outboxRepository.findByEventId(eventId).orElseThrow();
        assertEquals("PUBLISHED", publishedEvent.getStatus());
        assertNotNull(publishedEvent.getPublishedAt());
    }
}
