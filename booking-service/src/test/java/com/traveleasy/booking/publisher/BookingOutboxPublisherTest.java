package com.traveleasy.booking.publisher;

import com.traveleasy.booking.model.BookingOutboxEvent;
import com.traveleasy.booking.repository.BookingOutboxEventRepository;
import com.traveleasy.booking.service.BookingInternalTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
public class BookingOutboxPublisherTest {

    @Mock
    private BookingOutboxEventRepository outboxRepository;

    @Mock
    private BookingInternalTokenService tokenService;

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private BookingOutboxPublisher publisher;

    private static final String NOTIF_URL = "http://localhost:8086";

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        publisher = new BookingOutboxPublisher(outboxRepository, tokenService);
        publisher.setRestTemplate(restTemplate);
        publisher.setNotificationServiceUrl(NOTIF_URL);
        publisher.setPublisherEnabled(true);
    }

    private BookingOutboxEvent createTestEvent(Long id, String eventId, int retryCount, int maxRetries) {
        return BookingOutboxEvent.builder()
                .id(id)
                .eventId(eventId)
                .eventType("BUS_BOOKING_CONFIRMED")
                .aggregateType("BOOKING")
                .aggregateId("BKG-BUS-1001")
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
        BookingOutboxEvent event = createTestEvent(1L, "BKG-EVT-1-CONFIRMED", 0, 5);

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
        BookingOutboxEvent event = createTestEvent(2L, "BKG-EVT-2-CONFIRMED", 0, 5);

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
        BookingOutboxEvent event = createTestEvent(3L, "BKG-EVT-3-CONFIRMED", 0, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().body("{\"message\":\"Invalid event payload\"}"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        verify(outboxRepository).recordFailureAndUnlock(eq(3L), eq("FAILED"), eq(0), isNull(), contains("Terminal HTTP 400"), anyString());
    }

    @Test
    @DisplayName("HTTP 401 / 403 Security Failure marks outbox event as FAILED and records security error")
    void testDispatch_Http401Unauthorized_MarksFailed() {
        BookingOutboxEvent event = createTestEvent(4L, "BKG-EVT-4-CONFIRMED", 0, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"message\":\"Forbidden event ownership\"}"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        verify(outboxRepository).recordFailureAndUnlock(eq(4L), eq("FAILED"), eq(0), isNull(), contains("Security failure HTTP 403"), anyString());
    }

    @Test
    @DisplayName("HTTP 500 Server Error schedules retry with backoff")
    void testDispatch_Http500InternalError_SchedulesRetry() {
        BookingOutboxEvent event = createTestEvent(5L, "BKG-EVT-5-CONFIRMED", 1, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("{\"message\":\"Internal database down\"}"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        verify(outboxRepository).recordFailureAndUnlock(eq(5L), eq("PENDING"), eq(2), notNull(), contains("HTTP 500 error"), anyString());
    }

    @Test
    @DisplayName("Exceeding max retries transitions outbox event to FAILED")
    void testDispatch_MaxRetriesExceeded_TransitionsToFailed() {
        // Event currently at attempt 4 with maxRetries 5
        BookingOutboxEvent event = createTestEvent(6L, "BKG-EVT-6-CONFIRMED", 4, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("{\"message\":\"Repeated 500\"}"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        // 4 + 1 = 5 >= maxRetries -> FAILED
        verify(outboxRepository).recordFailureAndUnlock(eq(6L), eq("FAILED"), eq(5), isNull(), contains("Max retries exceeded"), anyString());
    }
}
