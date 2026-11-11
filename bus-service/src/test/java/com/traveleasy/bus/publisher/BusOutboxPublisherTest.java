package com.traveleasy.bus.publisher;

import com.traveleasy.bus.model.BusOutboxEvent;
import com.traveleasy.bus.repository.BusOutboxEventRepository;
import com.traveleasy.bus.service.BusInternalTokenService;
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
public class BusOutboxPublisherTest {

    @Mock
    private BusOutboxEventRepository outboxRepository;

    @Mock
    private BusInternalTokenService tokenService;

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private BusOutboxPublisher publisher;

    private static final String NOTIF_URL = "http://localhost:8086";

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        publisher = new BusOutboxPublisher(outboxRepository, tokenService);
        publisher.setRestTemplate(restTemplate);
        publisher.setNotificationServiceUrl(NOTIF_URL);
        publisher.setPublisherEnabled(true);
    }

    private BusOutboxEvent createTestEvent(Long id, String eventId, String eventType, int retryCount, int maxRetries) {
        return BusOutboxEvent.builder()
                .id(id)
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType("SCHEDULE")
                .aggregateId("SCHED-100")
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
        BusOutboxEvent event = createTestEvent(1L, "BUS-EVT-SCHED-CREATED-100", "BUS_SCHEDULE_CREATED", 0, 5);

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
        BusOutboxEvent event = createTestEvent(2L, "BUS-EVT-SCHED-CANCELLED-200", "BUS_SCHEDULE_CANCELLED", 0, 5);

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
    @DisplayName("HTTP 400 Bad Request marks outbox event as terminal FAILED")
    void testDispatch_Http400BadRequest_MarksFailed() {
        BusOutboxEvent event = createTestEvent(3L, "BUS-EVT-BUS-DEACT-300", "BUS_DEACTIVATED", 0, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"Invalid payload schema\"}"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        verify(outboxRepository).recordFailureAndUnlock(eq(3L), eq("FAILED"), eq(0), isNull(), contains("Terminal HTTP 400"), anyString());
    }

    @Test
    @DisplayName("HTTP 403 Forbidden marks outbox event as terminal FAILED")
    void testDispatch_Http403Forbidden_MarksFailed() {
        BusOutboxEvent event = createTestEvent(4L, "BUS-EVT-BUS-ACT-400", "BUS_ACTIVATED", 0, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"error\":\"Service not authorized\"}"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        verify(outboxRepository).recordFailureAndUnlock(eq(4L), eq("FAILED"), eq(0), isNull(), contains("Security failure HTTP 403"), anyString());
    }

    @Test
    @DisplayName("HTTP 500 Server Error schedules retry with backoff")
    void testDispatch_Http500ServerError_SchedulesRetry() {
        BusOutboxEvent event = createTestEvent(5L, "BUS-EVT-SCHED-CREATED-500", "BUS_SCHEDULE_CREATED", 1, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"error\":\"Database down\"}"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        ArgumentCaptor<Instant> nextRetryCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(outboxRepository).recordFailureAndUnlock(eq(5L), eq("PENDING"), eq(2), nextRetryCaptor.capture(), contains("HTTP 500"), anyString());
        assertNotNull(nextRetryCaptor.getValue());
        assertTrue(nextRetryCaptor.getValue().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("Max retries exceeded marks event as FAILED")
    void testDispatch_MaxRetriesExceeded_MarksFailed() {
        BusOutboxEvent event = createTestEvent(6L, "BUS-EVT-SCHED-CREATED-600", "BUS_SCHEDULE_CREATED", 4, 5);

        when(outboxRepository.claimPendingEvents(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(outboxRepository.findByLockedByAndStatus(anyString(), eq("IN_FLIGHT"))).thenReturn(List.of(event));
        when(tokenService.generateInternalToken()).thenReturn("mock.jwt.token");

        mockServer.expect(requestTo(NOTIF_URL + "/api/notifications/events"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("{\"error\":\"Gateway timeout\"}"));

        publisher.publishOutboxEvents();

        mockServer.verify();
        verify(outboxRepository).recordFailureAndUnlock(eq(6L), eq("FAILED"), eq(5), isNull(), contains("Max retries exceeded"), anyString());
    }

    @Test
    @DisplayName("Publisher disabled skips claim and dispatch")
    void testPublish_WhenDisabled_DoesNotClaim() {
        publisher.setPublisherEnabled(false);
        publisher.publishOutboxEvents();
        verifyNoInteractions(outboxRepository);
    }
}
