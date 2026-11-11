package com.traveleasy.payment.publisher;

import com.traveleasy.payment.model.PaymentOutboxEvent;
import com.traveleasy.payment.repository.PaymentOutboxEventRepository;
import com.traveleasy.payment.service.PaymentInternalTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
public class PaymentOutboxPublisherTest {

    @Mock
    private PaymentOutboxEventRepository outboxRepository;

    @Mock
    private PaymentInternalTokenService tokenService;

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private PaymentOutboxPublisher publisher;

    private static final String NOTIF_URL = "http://localhost:8086";

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        publisher = new PaymentOutboxPublisher(outboxRepository, tokenService);
        publisher.setRestTemplate(restTemplate);
        publisher.setNotificationServiceUrl(NOTIF_URL);
        publisher.setPublisherEnabled(true);
    }

    private PaymentOutboxEvent createTestEvent(Long id, String eventId, String eventType, int retryCount, int maxRetries) {
        return PaymentOutboxEvent.builder()
                .id(id)
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType("PAYMENT")
                .aggregateId("TXN-TEST-100")
                .payloadJson("{\"eventId\":\"" + eventId + "\"}")
                .status("IN_FLIGHT")
                .retryCount(retryCount)
                .maxRetries(maxRetries)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("HTTP 202 Accepted marks outbox event as PUBLISHED")
    void testDispatch_Http202Accepted_MarksPublished() {
        PaymentOutboxEvent event = createTestEvent(1L, "PAY-EVT-FAILED-100", "PAYMENT_FAILED", 0, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer mock.jwt.token"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"INGESTED\"}"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        verify(outboxRepository).markPublished(eq(1L), eq("PUBLISHED"), any(), anyString());
    }

    @Test
    @DisplayName("HTTP 200 OK (ALREADY_INGESTED) marks outbox event as PUBLISHED")
    void testDispatch_Http200Ok_MarksPublished() {
        PaymentOutboxEvent event = createTestEvent(2L, "PAY-EVT-REFUND-INIT-200", "PAYMENT_REFUND_INITIATED", 0, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"ALREADY_INGESTED\"}"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        verify(outboxRepository).markPublished(eq(2L), eq("PUBLISHED"), any(), anyString());
    }

    @Test
    @DisplayName("HTTP 400 Bad Request marks outbox event as FAILED immediately (terminal)")
    void testDispatch_Http400BadRequest_MarksFailed() {
        PaymentOutboxEvent event = createTestEvent(3L, "PAY-EVT-FAILED-300", "PAYMENT_FAILED", 0, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().body("{\"message\":\"Invalid event payload schema\"}"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        verify(outboxRepository).recordFailureAndUnlock(eq(3L), eq("FAILED"), eq(0), isNull(), contains("Terminal HTTP 400"), anyString());
    }

    @Test
    @DisplayName("HTTP 401 Unauthorized marks outbox event as FAILED (security failure)")
    void testDispatch_Http401Unauthorized_SecurityFailure_MarksFailed() {
        PaymentOutboxEvent event = createTestEvent(4L, "PAY-EVT-REFUND-COMP-400", "PAYMENT_REFUND_COMPLETED", 0, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("invalid.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Invalid internal token\"}"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        verify(outboxRepository).recordFailureAndUnlock(eq(4L), eq("FAILED"), eq(0), isNull(), contains("Security failure HTTP 401"), anyString());
    }

    @Test
    @DisplayName("HTTP 429 Too Many Requests schedules retry with exponential backoff")
    void testDispatch_Http429TooManyRequests_RetriesWithBackoff() {
        PaymentOutboxEvent event = createTestEvent(5L, "PAY-EVT-FAILED-500", "PAYMENT_FAILED", 1, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("Rate limited"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        ArgumentCaptor<Instant> retryTimeCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(outboxRepository).recordFailureAndUnlock(eq(5L), eq("PENDING"), eq(2), retryTimeCaptor.capture(), contains("HTTP 429"), anyString());
        assertTrue(retryTimeCaptor.getValue().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("HTTP 500 Internal Server Error schedules retry with exponential backoff")
    void testDispatch_Http500Error_RetriesWithBackoff() {
        PaymentOutboxEvent event = createTestEvent(6L, "PAY-EVT-FAILED-600", "PAYMENT_FAILED", 0, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("Database unavailable"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        verify(outboxRepository).recordFailureAndUnlock(eq(6L), eq("PENDING"), eq(1), any(), contains("HTTP 500"), anyString());
    }

    @Test
    @DisplayName("Max retries exceeded marks outbox event as FAILED permanently")
    void testDispatch_MaxRetriesExceeded_MarksFailed() {
        PaymentOutboxEvent event = createTestEvent(7L, "PAY-EVT-FAILED-700", "PAYMENT_FAILED", 4, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("Persistent outage"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        verify(outboxRepository).recordFailureAndUnlock(eq(7L), eq("FAILED"), eq(5), isNull(), contains("Max retries exceeded"), anyString());
    }

    @Test
    @DisplayName("Disabled publisher does not query repository")
    void testPublisherDisabled_DoesNothing() {
        publisher.setPublisherEnabled(false);
        publisher.publishOutboxEvents();

        verify(outboxRepository, never()).claimPendingEvents(anyString(), any(), any(), anyInt());
    }
}
