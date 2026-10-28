package com.traveleasy.booking.service;

import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.model.ScheduleCancellationBatch;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.repository.ScheduleCancellationBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ScheduleCancellationCoordinatorServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ScheduleCancellationBatchRepository batchRepository;

    @Mock
    private BookingSecurityService securityService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ScheduleCancellationCoordinatorService coordinatorService;

    private static final String AUTH_HEADER = "Bearer test.jwt.token";
    private static final String TOKEN = "test.jwt.token";
    private static final Long OPERATOR_ID = 5L;
    private static final Long SCHEDULE_ID = 10L;

    @BeforeEach
    void setUp() {
        coordinatorService.setRestTemplate(restTemplate);
        coordinatorService.setBusServiceUrl("http://localhost:8082");
        coordinatorService.setPaymentServiceUrl("http://localhost:8085");
    }

    private void setupSecurityMocks(Long userId, String role) {
        when(securityService.validateToken(TOKEN)).thenReturn(true);
        when(securityService.extractRole(TOKEN)).thenReturn(role);
        when(securityService.extractUserId(TOKEN)).thenReturn(userId);
    }

    private void setupScheduleDetailsMock(Long scheduleId, Long operatorId) {
        Map<String, Object> scheduleMap = new HashMap<>();
        scheduleMap.put("id", scheduleId);
        scheduleMap.put("operatorId", operatorId);

        Map<String, Object> busMap = new HashMap<>();
        busMap.put("id", 1L);
        busMap.put("operatorId", operatorId);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("schedule", scheduleMap);
        responseBody.put("bus", busMap);

        ResponseEntity<Map> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(
                eq("http://localhost:8082/api/buses/schedules/" + scheduleId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(responseEntity);
    }

    private void setupBusServiceCancellationMocks(Long scheduleId) {
        lenient().when(restTemplate.exchange(
                eq("http://localhost:8082/api/buses/schedules/" + scheduleId + "/status?status=CANCELLED"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(new ResponseEntity<>(Map.of("status", "CANCELLED"), HttpStatus.OK));

        lenient().when(restTemplate.exchange(
                eq("http://localhost:8082/api/buses/schedules/" + scheduleId + "/release-all-seats"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(new ResponseEntity<>(Map.of("releasedCount", 40), HttpStatus.OK));
    }

    @Test
    @DisplayName("cancelSchedule - Successfully cancels schedule with 2 confirmed bookings and 1 pending booking")
    void testCancelSchedule_Success_ConfirmedAndPending() {
        setupSecurityMocks(OPERATOR_ID, "ROLE_BUS_OPERATOR");
        setupScheduleDetailsMock(SCHEDULE_ID, OPERATOR_ID);
        setupBusServiceCancellationMocks(SCHEDULE_ID);

        when(batchRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.empty());
        when(batchRepository.saveAndFlush(any(ScheduleCancellationBatch.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Booking bkg1 = Booking.builder()
                .id(1L).bookingRef("BKG-001").status("CONFIRMED").serviceType("BUS").serviceId(SCHEDULE_ID).totalAmount(500.0).build();
        Booking bkg2 = Booking.builder()
                .id(2L).bookingRef("BKG-002").status("CONFIRMED").serviceType("BUS").serviceId(SCHEDULE_ID).totalAmount(750.0).build();
        Booking bkg3 = Booking.builder()
                .id(3L).bookingRef("BKG-003").status("PENDING_PAYMENT").serviceType("BUS").serviceId(SCHEDULE_ID).totalAmount(300.0).build();

        List<Booking> bookings = Arrays.asList(bkg1, bkg2, bkg3);
        when(bookingRepository.findByServiceTypeAndServiceId("BUS", SCHEDULE_ID)).thenReturn(bookings);

        // payment-service refund endpoints
        Map<String, Object> refundResp1 = Map.of("refundId", "rfnd_001", "refundAmount", 500.0, "refundStatus", "PROCESSED");
        when(restTemplate.exchange(contains("/refund/booking/BKG-001"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(refundResp1, HttpStatus.OK));

        Map<String, Object> refundResp2 = Map.of("refundId", "rfnd_002", "refundAmount", 750.0, "refundStatus", "PROCESSED");
        when(restTemplate.exchange(contains("/refund/booking/BKG-002"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(refundResp2, HttpStatus.OK));

        ScheduleCancellationBatch result = coordinatorService.cancelSchedule(SCHEDULE_ID, AUTH_HEADER);

        assertNotNull(result);
        assertEquals("COMPLETED", result.getBatchStatus());
        assertEquals(3, result.getTotalBookings());
        assertEquals(2, result.getRefundsProcessed());
        assertEquals(0, result.getRefundsFailed());
        assertEquals(1, result.getUnpaidCancelled());
        assertEquals(1250.0, result.getTotalRefundAmount());

        assertEquals("CANCELLED", bkg1.getStatus());
        assertEquals("PROCESSED", bkg1.getRefundStatus());
        assertEquals("rfnd_001", bkg1.getRefundId());

        assertEquals("CANCELLED", bkg2.getStatus());
        assertEquals("PROCESSED", bkg2.getRefundStatus());
        assertEquals("rfnd_002", bkg2.getRefundId());

        assertEquals("CANCELLED", bkg3.getStatus());
        assertEquals("NOT_APPLICABLE", bkg3.getRefundStatus());

        verify(bookingRepository, atLeastOnce()).save(bkg1);
        verify(bookingRepository, atLeastOnce()).save(bkg2);
        verify(bookingRepository, atLeastOnce()).save(bkg3);
    }

    @Test
    @DisplayName("cancelSchedule - Rejects concurrent cancellation when batch is already CLAIMED")
    void testCancelSchedule_Conflict_AlreadyInProgress() {
        setupSecurityMocks(OPERATOR_ID, "ROLE_BUS_OPERATOR");
        setupScheduleDetailsMock(SCHEDULE_ID, OPERATOR_ID);

        ScheduleCancellationBatch existing = ScheduleCancellationBatch.builder()
                .scheduleId(SCHEDULE_ID)
                .operatorId(OPERATOR_ID)
                .batchStatus("CLAIMED")
                .build();
        when(batchRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                coordinatorService.cancelSchedule(SCHEDULE_ID, AUTH_HEADER));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already in progress"));
    }

    @Test
    @DisplayName("cancelSchedule - Rejects cancellation when batch is already COMPLETED")
    void testCancelSchedule_Conflict_AlreadyCompleted() {
        setupSecurityMocks(OPERATOR_ID, "ROLE_BUS_OPERATOR");
        setupScheduleDetailsMock(SCHEDULE_ID, OPERATOR_ID);

        ScheduleCancellationBatch existing = ScheduleCancellationBatch.builder()
                .scheduleId(SCHEDULE_ID)
                .operatorId(OPERATOR_ID)
                .batchStatus("COMPLETED")
                .build();
        when(batchRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                coordinatorService.cancelSchedule(SCHEDULE_ID, AUTH_HEADER));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already completed"));
    }

    @Test
    @DisplayName("cancelSchedule - Rejects cross-operator cancellation with 403 Forbidden")
    void testCancelSchedule_Forbidden_CrossOperator() {
        setupSecurityMocks(OPERATOR_ID, "ROLE_BUS_OPERATOR");
        setupScheduleDetailsMock(SCHEDULE_ID, 999L); // Schedule owned by operator 999

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                coordinatorService.cancelSchedule(SCHEDULE_ID, AUTH_HEADER));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("permission"));
    }

    @Test
    @DisplayName("cancelSchedule - Admin can cancel any operator's schedule")
    void testCancelSchedule_AdminSuccess() {
        setupSecurityMocks(1L, "ROLE_ADMIN");
        setupScheduleDetailsMock(SCHEDULE_ID, 999L); // Belongs to operator 999
        setupBusServiceCancellationMocks(SCHEDULE_ID);

        when(batchRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.empty());
        when(batchRepository.saveAndFlush(any(ScheduleCancellationBatch.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(bookingRepository.findByServiceTypeAndServiceId("BUS", SCHEDULE_ID)).thenReturn(Collections.emptyList());

        ScheduleCancellationBatch result = coordinatorService.cancelSchedule(SCHEDULE_ID, AUTH_HEADER);

        assertNotNull(result);
        assertEquals("COMPLETED", result.getBatchStatus());
        assertEquals(0, result.getTotalBookings());
    }

    @Test
    @DisplayName("cancelSchedule - Rejects request with invalid or missing token with 401")
    void testCancelSchedule_Unauthorized() {
        when(securityService.validateToken(TOKEN)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                coordinatorService.cancelSchedule(SCHEDULE_ID, AUTH_HEADER));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("cancelSchedule - Marks batch PARTIALLY_FAILED and booking FAILED_RETRYABLE on gateway timeout")
    void testCancelSchedule_PartialFailure_GatewayTimeout() {
        setupSecurityMocks(OPERATOR_ID, "ROLE_BUS_OPERATOR");
        setupScheduleDetailsMock(SCHEDULE_ID, OPERATOR_ID);
        setupBusServiceCancellationMocks(SCHEDULE_ID);

        when(batchRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.empty());
        when(batchRepository.saveAndFlush(any(ScheduleCancellationBatch.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Booking bkg1 = Booking.builder()
                .id(1L).bookingRef("BKG-OK").status("CONFIRMED").serviceType("BUS").serviceId(SCHEDULE_ID).totalAmount(400.0).build();
        Booking bkg2 = Booking.builder()
                .id(2L).bookingRef("BKG-TIMEOUT").status("CONFIRMED").serviceType("BUS").serviceId(SCHEDULE_ID).totalAmount(600.0).build();

        when(bookingRepository.findByServiceTypeAndServiceId("BUS", SCHEDULE_ID)).thenReturn(Arrays.asList(bkg1, bkg2));

        // bkg1 refund succeeds
        Map<String, Object> refundResp = Map.of("refundId", "rfnd_OK", "refundAmount", 400.0, "refundStatus", "PROCESSED");
        when(restTemplate.exchange(contains("/refund/booking/BKG-OK"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(refundResp, HttpStatus.OK));

        // bkg2 refund fails with RestClientException (timeout)
        when(restTemplate.exchange(contains("/refund/booking/BKG-TIMEOUT"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("504 Gateway Timeout"));

        ScheduleCancellationBatch result = coordinatorService.cancelSchedule(SCHEDULE_ID, AUTH_HEADER);

        assertNotNull(result);
        assertEquals("PARTIALLY_FAILED", result.getBatchStatus());
        assertEquals(2, result.getTotalBookings());
        assertEquals(1, result.getRefundsProcessed());
        assertEquals(1, result.getRefundsFailed());
        assertEquals(400.0, result.getTotalRefundAmount());

        assertEquals("PROCESSED", bkg1.getRefundStatus());
        assertEquals("FAILED_RETRYABLE", bkg2.getRefundStatus());
    }

    @Test
    @DisplayName("cancelSchedule - Retrying PARTIALLY_FAILED batch skips already refunded bookings and completes")
    void testCancelSchedule_Retry_SkipsAlreadyRefunded() {
        setupSecurityMocks(OPERATOR_ID, "ROLE_BUS_OPERATOR");
        setupScheduleDetailsMock(SCHEDULE_ID, OPERATOR_ID);
        setupBusServiceCancellationMocks(SCHEDULE_ID);

        ScheduleCancellationBatch existingBatch = ScheduleCancellationBatch.builder()
                .id(55L)
                .scheduleId(SCHEDULE_ID)
                .operatorId(OPERATOR_ID)
                .batchStatus("PARTIALLY_FAILED")
                .totalBookings(2)
                .refundsProcessed(1)
                .refundsFailed(1)
                .totalRefundAmount(400.0)
                .build();
        when(batchRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.of(existingBatch));
        when(batchRepository.saveAndFlush(any(ScheduleCancellationBatch.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Booking bkg1 = Booking.builder()
                .id(1L).bookingRef("BKG-OK").status("CANCELLED").refundStatus("PROCESSED").refundId("rfnd_OK")
                .serviceType("BUS").serviceId(SCHEDULE_ID).totalAmount(400.0).refundAmount(400.0).build();
        Booking bkg2 = Booking.builder()
                .id(2L).bookingRef("BKG-RETRY").status("CONFIRMED").refundStatus("FAILED_RETRYABLE")
                .serviceType("BUS").serviceId(SCHEDULE_ID).totalAmount(600.0).build();

        when(bookingRepository.findByServiceTypeAndServiceId("BUS", SCHEDULE_ID)).thenReturn(Arrays.asList(bkg1, bkg2));

        // bkg2 refund now succeeds
        Map<String, Object> refundResp = Map.of("refundId", "rfnd_RETRY_OK", "refundAmount", 600.0, "refundStatus", "PROCESSED");
        when(restTemplate.exchange(contains("/refund/booking/BKG-RETRY"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(refundResp, HttpStatus.OK));

        ScheduleCancellationBatch result = coordinatorService.cancelSchedule(SCHEDULE_ID, AUTH_HEADER);

        assertNotNull(result);
        assertEquals("COMPLETED", result.getBatchStatus());
        assertEquals(2, result.getRefundsProcessed());
        assertEquals(0, result.getRefundsFailed());
        assertEquals(1000.0, result.getTotalRefundAmount());

        // Verify that BKG-OK was NEVER called for refund again
        verify(restTemplate, never()).exchange(contains("/refund/booking/BKG-OK"), any(), any(), eq(Map.class));
        // Verify that BKG-RETRY was called
        verify(restTemplate, times(1)).exchange(contains("/refund/booking/BKG-RETRY"), any(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("getCancellationStatus - Returns batch details for owning operator")
    void testGetCancellationStatus_Success() {
        setupSecurityMocks(OPERATOR_ID, "ROLE_BUS_OPERATOR");

        ScheduleCancellationBatch batch = ScheduleCancellationBatch.builder()
                .id(1L)
                .scheduleId(SCHEDULE_ID)
                .operatorId(OPERATOR_ID)
                .batchStatus("COMPLETED")
                .totalBookings(3)
                .refundsProcessed(2)
                .refundsFailed(0)
                .unpaidCancelled(1)
                .totalRefundAmount(1250.0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(batchRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.of(batch));

        ScheduleCancellationBatch result = coordinatorService.getCancellationStatus(SCHEDULE_ID, AUTH_HEADER);

        assertNotNull(result);
        assertEquals(SCHEDULE_ID, result.getScheduleId());
        assertEquals("COMPLETED", result.getBatchStatus());
        assertEquals(2, result.getRefundsProcessed());
    }

    @Test
    @DisplayName("getCancellationStatus - Rejects cross-operator status check with 403 Forbidden")
    void testGetCancellationStatus_Forbidden_CrossOperator() {
        setupSecurityMocks(OPERATOR_ID, "ROLE_BUS_OPERATOR");

        ScheduleCancellationBatch batch = ScheduleCancellationBatch.builder()
                .id(1L)
                .scheduleId(SCHEDULE_ID)
                .operatorId(999L) // Owned by different operator
                .batchStatus("COMPLETED")
                .build();
        when(batchRepository.findByScheduleId(SCHEDULE_ID)).thenReturn(Optional.of(batch));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                coordinatorService.getCancellationStatus(SCHEDULE_ID, AUTH_HEADER));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("getCancellationStatus - Returns 404 when no batch exists")
    void testGetCancellationStatus_NotFound() {
        setupSecurityMocks(OPERATOR_ID, "ROLE_BUS_OPERATOR");
        when(batchRepository.findByScheduleId(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                coordinatorService.getCancellationStatus(999L, AUTH_HEADER));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
