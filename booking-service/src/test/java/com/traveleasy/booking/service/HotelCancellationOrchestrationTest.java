package com.traveleasy.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.dto.HotelCancellationRequest;
import com.traveleasy.booking.dto.HotelCancellationResponse;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.model.HotelAllocationSnapshot;
import com.traveleasy.booking.model.HotelCancellationRefundTask;
import com.traveleasy.booking.model.HotelInventoryReleaseTask;
import com.traveleasy.booking.model.HotelReservationSnapshot;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.repository.HotelCancellationRefundTaskRepository;
import com.traveleasy.booking.repository.HotelInventoryReleaseTaskRepository;
import com.traveleasy.booking.repository.HotelReservationSnapshotRepository;
import com.traveleasy.booking.worker.HotelCancellationRefundRecoveryWorker;
import com.traveleasy.booking.worker.HotelInventoryReleaseRecoveryWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HotelCancellationOrchestrationTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private HotelReservationSnapshotRepository snapshotRepository;
    @Mock
    private HotelInventoryReleaseTaskRepository releaseTaskRepository;
    @Mock
    private HotelCancellationRefundTaskRepository refundTaskRepository;
    @Mock
    private BookingStateTransitionService stateTransitionService;
    @Mock
    private BookingSecurityService securityService;
    @Mock
    private RestTemplate restTemplate;

    private BookingInternalTokenService internalTokenService;
    private HotelCancellationPolicyService policyService;
    private HotelCancellationStateService stateService;
    private HotelInventoryReleaseRecoveryService releaseRecoveryService;
    private HotelCancellationRefundRecoveryService refundRecoveryService;
    private HotelInventoryReleaseRecoveryWorker releaseWorker;
    private HotelCancellationRefundRecoveryWorker refundWorker;
    private HotelCancellationOrchestrationService orchestrationService;

    private final AtomicReference<Booking> storedBooking = new AtomicReference<>();
    private final AtomicReference<HotelReservationSnapshot> storedSnapshot = new AtomicReference<>();
    private final AtomicReference<HotelInventoryReleaseTask> storedReleaseTask = new AtomicReference<>();
    private final AtomicReference<HotelCancellationRefundTask> storedRefundTask = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        internalTokenService = new BookingInternalTokenService();
        ReflectionTestUtils.setField(internalTokenService, "jwtSecret", "TestSecretKeyForBookingServiceTesting1234567890");
        internalTokenService.validateSecret();

        policyService = new HotelCancellationPolicyService(new ObjectMapper().findAndRegisterModules());
        stateService = new HotelCancellationStateService(
                bookingRepository,
                snapshotRepository,
                releaseTaskRepository,
                refundTaskRepository,
                policyService,
                stateTransitionService,
                securityService,
                internalTokenService,
                new ObjectMapper().findAndRegisterModules()
        );

        releaseRecoveryService = new HotelInventoryReleaseRecoveryService(
                releaseTaskRepository, stateService, internalTokenService);
        ReflectionTestUtils.setField(releaseRecoveryService, "hotelServiceUrl", "http://localhost:8083");
        releaseRecoveryService.setRestTemplate(restTemplate);

        refundRecoveryService = new HotelCancellationRefundRecoveryService(
                refundTaskRepository, stateService, internalTokenService);
        ReflectionTestUtils.setField(refundRecoveryService, "paymentServiceUrl", "http://localhost:8085");
        refundRecoveryService.setRestTemplate(restTemplate);

        releaseWorker = new HotelInventoryReleaseRecoveryWorker(releaseTaskRepository, releaseRecoveryService);
        releaseWorker.setRecoveryEnabled(true);
        refundWorker = new HotelCancellationRefundRecoveryWorker(refundTaskRepository, refundRecoveryService);
        refundWorker.setRecoveryEnabled(true);

        orchestrationService = new HotelCancellationOrchestrationService(
                stateService, policyService, releaseRecoveryService, refundRecoveryService);

        lenient().when(securityService.validateToken("cust-jwt")).thenReturn(true);
        lenient().when(securityService.extractUserId("cust-jwt")).thenReturn(10L);
        lenient().when(securityService.extractRole("cust-jwt")).thenReturn("ROLE_CUSTOMER");

        lenient().when(bookingRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(storedBooking.get()));
        lenient().when(bookingRepository.findByIdForUpdate(anyLong())).thenAnswer(inv -> Optional.ofNullable(storedBooking.get()));
        lenient().when(bookingRepository.findByBookingRef(anyString())).thenAnswer(inv -> Optional.ofNullable(storedBooking.get()));
        lenient().when(bookingRepository.findByBookingRefForUpdate(anyString())).thenAnswer(inv -> Optional.ofNullable(storedBooking.get()));
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            storedBooking.set(b);
            return b;
        });
        lenient().when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            storedBooking.set(b);
            return b;
        });

        lenient().when(snapshotRepository.findByBookingId(anyLong())).thenAnswer(inv -> Optional.ofNullable(storedSnapshot.get()));
        lenient().when(snapshotRepository.findByBookingRef(anyString())).thenAnswer(inv -> Optional.ofNullable(storedSnapshot.get()));
        lenient().when(snapshotRepository.save(any(HotelReservationSnapshot.class))).thenAnswer(inv -> {
            HotelReservationSnapshot s = inv.getArgument(0);
            storedSnapshot.set(s);
            return s;
        });

        lenient().when(releaseTaskRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(storedReleaseTask.get()));
        lenient().when(releaseTaskRepository.findByBookingId(anyLong())).thenAnswer(inv -> Optional.ofNullable(storedReleaseTask.get()));
        lenient().when(releaseTaskRepository.findByBookingRef(anyString())).thenAnswer(inv -> Optional.ofNullable(storedReleaseTask.get()));
        lenient().when(releaseTaskRepository.save(any(HotelInventoryReleaseTask.class))).thenAnswer(inv -> {
            HotelInventoryReleaseTask t = inv.getArgument(0);
            if (t.getId() == null) t.setId(501L);
            storedReleaseTask.set(t);
            return t;
        });
        lenient().when(releaseTaskRepository.claimReleaseTask(anyLong(), anyString(), any(), any())).thenAnswer(inv -> {
            HotelInventoryReleaseTask t = storedReleaseTask.get();
            if (t == null) return 0;
            if ("RELEASED".equals(t.getStatus()) || "FAILED_FINAL".equals(t.getStatus())) return 0;
            t.setAttemptCount((t.getAttemptCount() == null ? 0 : t.getAttemptCount()) + 1);
            t.setStatus("PROCESSING");
            t.setLockedBy(inv.getArgument(1));
            t.setLockedUntil(inv.getArgument(3));
            return 1;
        });
        lenient().when(releaseTaskRepository.markReleased(anyLong(), any())).thenAnswer(inv -> {
            HotelInventoryReleaseTask t = storedReleaseTask.get();
            if (t != null) {
                t.setStatus("RELEASED");
                t.setLockedBy(null);
                t.setLockedUntil(null);
                t.setLastError(null);
            }
            return 1;
        });
        lenient().when(releaseTaskRepository.markFailedRetryable(anyLong(), anyString(), any(), any())).thenAnswer(inv -> {
            HotelInventoryReleaseTask t = storedReleaseTask.get();
            if (t != null) {
                t.setStatus("FAILED_RETRYABLE");
                t.setLastError(inv.getArgument(1));
                t.setNextRetryAt(inv.getArgument(2));
                t.setLockedBy(null);
                t.setLockedUntil(null);
            }
            return 1;
        });
        lenient().when(releaseTaskRepository.markFailedFinal(anyLong(), anyString(), any())).thenAnswer(inv -> {
            HotelInventoryReleaseTask t = storedReleaseTask.get();
            if (t != null) {
                t.setStatus("FAILED_FINAL");
                t.setLastError(inv.getArgument(1));
                t.setLockedBy(null);
                t.setLockedUntil(null);
            }
            return 1;
        });

        lenient().when(refundTaskRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(storedRefundTask.get()));
        lenient().when(refundTaskRepository.findByBookingId(anyLong())).thenAnswer(inv -> Optional.ofNullable(storedRefundTask.get()));
        lenient().when(refundTaskRepository.findByBookingRef(anyString())).thenAnswer(inv -> Optional.ofNullable(storedRefundTask.get()));
        lenient().when(refundTaskRepository.save(any(HotelCancellationRefundTask.class))).thenAnswer(inv -> {
            HotelCancellationRefundTask t = inv.getArgument(0);
            if (t.getId() == null) t.setId(601L);
            storedRefundTask.set(t);
            return t;
        });
        lenient().when(refundTaskRepository.claimRefundTask(anyLong(), anyString(), any(), any())).thenAnswer(inv -> {
            HotelCancellationRefundTask t = storedRefundTask.get();
            if (t == null) return 0;
            if ("PROCESSED".equals(t.getStatus()) || "FAILED_FINAL".equals(t.getStatus())) return 0;
            if (!"INITIATED".equals(t.getStatus())) {
                t.setAttemptCount((t.getAttemptCount() == null ? 0 : t.getAttemptCount()) + 1);
            }
            t.setStatus("PROCESSING");
            t.setLockedBy(inv.getArgument(1));
            t.setLockedUntil(inv.getArgument(3));
            return 1;
        });
        lenient().when(refundTaskRepository.markProcessed(anyLong(), any(), any())).thenAnswer(inv -> {
            HotelCancellationRefundTask t = storedRefundTask.get();
            if (t != null) {
                t.setStatus("PROCESSED");
                if (inv.getArgument(1) != null) t.setRazorpayRefundId(inv.getArgument(1));
                t.setLockedBy(null);
                t.setLockedUntil(null);
                t.setLastError(null);
            }
            return 1;
        });
        lenient().when(refundTaskRepository.markInitiated(anyLong(), any(), anyString(), any(), any())).thenAnswer(inv -> {
            HotelCancellationRefundTask t = storedRefundTask.get();
            if (t != null) {
                t.setStatus("INITIATED");
                if (inv.getArgument(1) != null) t.setRazorpayRefundId(inv.getArgument(1));
                t.setLastError(inv.getArgument(2));
                t.setNextRetryAt(inv.getArgument(3));
                t.setLockedBy(null);
                t.setLockedUntil(null);
            }
            return 1;
        });
        lenient().when(refundTaskRepository.markFailedRetryable(anyLong(), anyString(), any(), any())).thenAnswer(inv -> {
            HotelCancellationRefundTask t = storedRefundTask.get();
            if (t != null) {
                t.setStatus("FAILED_RETRYABLE");
                t.setLastError(inv.getArgument(1));
                t.setNextRetryAt(inv.getArgument(2));
                t.setLockedBy(null);
                t.setLockedUntil(null);
            }
            return 1;
        });
        lenient().when(refundTaskRepository.markFailedFinal(anyLong(), anyString(), any())).thenAnswer(inv -> {
            HotelCancellationRefundTask t = storedRefundTask.get();
            if (t != null) {
                t.setStatus("FAILED_FINAL");
                t.setLastError(inv.getArgument(1));
                t.setLockedBy(null);
                t.setLockedUntil(null);
            }
            return 1;
        });
    }

    private LocalDate findCheckInDateForWindow(long minHoursInclusive, long maxHoursExclusive) {
        LocalDateTime nowIst = LocalDateTime.now(HotelCancellationPolicyService.POLICY_ZONE_ID);
        for (int offset = 0; offset <= 4; offset++) {
            LocalDate candidate = nowIst.toLocalDate().plusDays(offset);
            LocalDateTime checkInDt = LocalDateTime.of(candidate, HotelCancellationPolicyService.STANDARD_CHECK_IN_TIME);
            long hours = Duration.between(
                    nowIst.atZone(HotelCancellationPolicyService.POLICY_ZONE_ID),
                    checkInDt.atZone(HotelCancellationPolicyService.POLICY_ZONE_ID)).toHours();
            if (hours >= minHoursInclusive && hours < maxHoursExclusive) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find check-in date for window [" + minHoursInclusive + ", " + maxHoursExclusive + ")");
    }

    @Test
    @DisplayName("Full 100% cancellation releases date-wise stay inventory, calls payment refund with signed internal headers, and writes single outbox event")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testFullCancellationWorkflow() {
        LocalDate checkIn = LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(5);
        Booking b = Booking.builder()
                .id(1L).bookingRef("BKG-HTL-FULL-1").customerId(10L).serviceType("HOTEL").serviceId(55L)
                .totalAmount(6000.0).status("CONFIRMED")
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(2))
                .detailsJson("{\"checkInDate\":\"" + checkIn + "\",\"checkOutDate\":\"" + checkIn.plusDays(2) + "\"}")
                .build();
        storedBooking.set(b);
        HotelReservationSnapshot snap = HotelReservationSnapshot.builder()
                .id(70L).bookingId(1L).bookingRef("BKG-HTL-FULL-1").hotelId(55L)
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(2)).status("HELD")
                .allocations(List.of(HotelAllocationSnapshot.builder().roomId(5L).status("HELD").build()))
                .build();
        storedSnapshot.set(snap);

        when(restTemplate.exchange(contains("/api/hotels/rooms/release-stay"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("released", true)));
        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-HTL-FULL-1"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "PROCESSED", "refundId", "rfnd_full_01")));

        HotelCancellationResponse resp = orchestrationService.cancelHotelBookingById(
                1L, new HotelCancellationRequest("Change of plans"), "Bearer cust-jwt", null, null);

        assertFalse(resp.isIdempotent());
        assertEquals("CANCELLED", resp.getStatus());
        assertEquals("RELEASED", resp.getInventoryReleaseStatus());
        assertEquals("PROCESSED", resp.getRefundStatus());
        assertEquals("rfnd_full_01", resp.getRefundId());
        assertEquals(6000.0, resp.getRefundAmount());
        assertEquals("htl-rfnd-BKG-HTL-FULL-1", resp.getRefundAttemptKey());
        assertEquals("RELEASED", storedSnapshot.get().getStatus());
        assertEquals("RELEASED", storedSnapshot.get().getAllocations().get(0).getStatus());

        // Verify signed internal headers used on downstream calls
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:8083/api/hotels/rooms/release-stay?bookingRef=BKG-HTL-FULL-1"),
                eq(HttpMethod.POST), entityCaptor.capture(), eq(Map.class));
        HttpHeaders releaseHeaders = entityCaptor.getValue().getHeaders();
        assertNotNull(releaseHeaders.getFirst("X-Internal-Token"));
        assertEquals("booking-service", releaseHeaders.getFirst("X-Internal-Service"));
        verify(restTemplate, never()).exchange(contains("/release?count="), any(), any(), eq(Map.class));
        verify(stateTransitionService, times(1)).recordHotelCancellationOutboxEvent(any(), any(), any());
    }

    @Test
    @DisplayName("Partial 50% cancellation retry & worker recovery preserve persisted 50% refundAmount and idempotencyKey without substituting totalAmount")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testPartialRefundRetryAndWorkerRecoveryPreserveAmount() {
        LocalDate checkIn = findCheckInDateForWindow(24, 48);
        Booking b = Booking.builder()
                .id(2L).bookingRef("BKG-HTL-PARTIAL-2").customerId(10L).serviceType("HOTEL").serviceId(66L)
                .totalAmount(5001.0).status("CONFIRMED")
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(1))
                .detailsJson("{\"checkInDate\":\"" + checkIn + "\",\"checkOutDate\":\"" + checkIn.plusDays(1) + "\"}")
                .build();
        storedBooking.set(b);

        when(restTemplate.exchange(contains("/api/hotels/rooms/release-stay"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("released", true)));
        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-HTL-PARTIAL-2/reconcile"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "NOT_FOUND")));
        when(restTemplate.exchange(eq("http://localhost:8085/api/payments/refund/booking/BKG-HTL-PARTIAL-2"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("504 payment timeout"))
                .thenThrow(new ResourceAccessException("504 second timeout"))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "PROCESSED", "refundId", "rfnd_partial_50")));

        // Initial cancel: inventory released, refund FAILED_RETRYABLE
        HotelCancellationResponse first = orchestrationService.cancelHotelBookingById(
                2L, new HotelCancellationRequest("Late reschedule"), "Bearer cust-jwt", null, null);
        assertEquals("CANCELLED", first.getStatus());
        assertEquals("RELEASED", first.getInventoryReleaseStatus());
        assertEquals("FAILED_RETRYABLE", first.getRefundStatus());
        assertEquals(2500.50, first.getRefundAmount());
        assertEquals(2500.50, storedRefundTask.get().getRefundAmount());

        // Manual replay while still FAILED_RETRYABLE -> must send 2500.50, never 5001.00, and must NOT duplicate outbox
        HotelCancellationResponse replay = orchestrationService.cancelHotelBookingById(
                2L, null, "Bearer cust-jwt", null, null);
        assertTrue(replay.isIdempotent());
        assertEquals("FAILED_RETRYABLE", replay.getRefundStatus());
        assertEquals(2500.50, replay.getRefundAmount());

        // Background worker recovers cleanly on 3rd attempt
        when(refundTaskRepository.findTasksForRetry(any(), any(Pageable.class)))
                .thenReturn(List.of(storedRefundTask.get()));
        refundWorker.recoverPendingRefunds();

        assertEquals("PROCESSED", storedBooking.get().getRefundStatus());
        assertEquals("rfnd_partial_50", storedBooking.get().getRefundId());

        ArgumentCaptor<HttpEntity> paymentCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(3)).exchange(
                eq("http://localhost:8085/api/payments/refund/booking/BKG-HTL-PARTIAL-2"),
                eq(HttpMethod.POST), paymentCaptor.capture(), eq(Map.class));
        for (HttpEntity<?> sent : paymentCaptor.getAllValues()) {
            Map<String, Object> body = (Map<String, Object>) sent.getBody();
            assertEquals(2500.50, ((Number) body.get("amount")).doubleValue(), 0.0001);
            assertEquals("htl-rfnd-BKG-HTL-PARTIAL-2", body.get("idempotencyKey"));
            assertEquals("htl-rfnd-BKG-HTL-PARTIAL-2", sent.getHeaders().getFirst("X-Idempotency-Key"));
        }
        verify(stateTransitionService, times(1)).recordHotelCancellationOutboxEvent(any(), any(), any());
    }

    @Test
    @DisplayName("Zero refund (<24h or past check-in) never creates refund task or invokes payment-service even on replays")
    @SuppressWarnings({"rawtypes"})
    void testZeroRefundNeverCallsPaymentService() {
        LocalDate checkIn = LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).minusDays(1);
        Booking b = Booking.builder()
                .id(3L).bookingRef("BKG-HTL-ZERO-3").customerId(10L).serviceType("HOTEL").serviceId(77L)
                .totalAmount(3200.0).status("CONFIRMED")
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(2))
                .detailsJson("{\"checkInDate\":\"" + checkIn + "\"}")
                .build();
        storedBooking.set(b);

        when(restTemplate.exchange(contains("/api/hotels/rooms/release-stay"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("released", true)));

        HotelCancellationResponse first = orchestrationService.cancelHotelBookingByRef(
                "BKG-HTL-ZERO-3", null, "Bearer cust-jwt", null, null);
        assertEquals("CANCELLED", first.getStatus());
        assertEquals(0.0, first.getRefundAmount());
        assertEquals("NON_REFUNDABLE", first.getRefundStatus());

        HotelCancellationResponse replay = orchestrationService.cancelHotelBookingByRef(
                "BKG-HTL-ZERO-3", null, "Bearer cust-jwt", null, null);
        assertTrue(replay.isIdempotent());
        assertEquals(0.0, replay.getRefundAmount());
        assertEquals("NON_REFUNDABLE", replay.getRefundStatus());

        verify(refundTaskRepository, never()).save(any());
        verify(restTemplate, never()).exchange(contains("/api/payments/refund"), any(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("Inventory release failure leaves refund independent and worker later recovers release + updates snapshot without legacy fallback")
    @SuppressWarnings({"rawtypes"})
    void testInventoryReleaseFailureRecoveredIndependentlyFromRefund() {
        LocalDate checkIn = LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(4);
        Booking b = Booking.builder()
                .id(4L).bookingRef("BKG-HTL-INV-4").customerId(10L).serviceType("HOTEL").serviceId(88L)
                .totalAmount(4500.0).status("CONFIRMED")
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(1))
                .detailsJson("{\"checkInDate\":\"" + checkIn + "\"}")
                .build();
        storedBooking.set(b);
        HotelReservationSnapshot snap = HotelReservationSnapshot.builder()
                .id(71L).bookingId(4L).bookingRef("BKG-HTL-INV-4").hotelId(88L)
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(1)).status("HELD")
                .allocations(List.of(HotelAllocationSnapshot.builder().roomId(9L).status("HELD").build()))
                .build();
        storedSnapshot.set(snap);

        when(restTemplate.exchange(contains("/api/hotels/rooms/release-stay"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY, "Hotel service down"))
                .thenReturn(ResponseEntity.ok(Map.of("released", true)));
        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-HTL-INV-4"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "PROCESSED", "refundId", "rfnd_indep_04")));

        HotelCancellationResponse first = orchestrationService.cancelHotelBookingById(
                4L, null, "Bearer cust-jwt", null, null);
        assertEquals("CANCELLED", first.getStatus());
        assertEquals("FAILED_RETRYABLE", first.getInventoryReleaseStatus());
        assertEquals("PROCESSED", first.getRefundStatus());
        assertEquals("CANCELLED", storedSnapshot.get().getStatus());

        // Inventory recovery worker executes later
        when(releaseTaskRepository.findTasksForRetry(any(), any(Pageable.class)))
                .thenReturn(List.of(storedReleaseTask.get()));
        releaseWorker.recoverPendingInventoryReleases();

        assertEquals("RELEASED", storedBooking.get().getInventoryReleaseStatus());
        assertNotNull(storedBooking.get().getInventoryReleasedAt());
        assertEquals("RELEASED", storedSnapshot.get().getStatus());
        assertEquals("RELEASED", storedSnapshot.get().getAllocations().get(0).getStatus());
        verify(restTemplate, never()).exchange(contains("/release?count="), any(), any(), eq(Map.class));
    }

    @Test
    @DisplayName("Refund RETURNING INITIATED polls authoritative /reconcile endpoint without duplicate POST and transitions cleanly to PROCESSED")
    @SuppressWarnings({"rawtypes"})
    void testRefundInitiatedPollLifecycleAndNoDowngradeOnTransientError() {
        LocalDate checkIn = LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(5);
        Booking b = Booking.builder()
                .id(5L).bookingRef("BKG-HTL-INIT-5").customerId(10L).serviceType("HOTEL").serviceId(99L)
                .totalAmount(7000.0).status("CONFIRMED")
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(2))
                .detailsJson("{\"checkInDate\":\"" + checkIn + "\"}")
                .build();
        storedBooking.set(b);

        when(restTemplate.exchange(contains("/api/hotels/rooms/release-stay"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("released", true)));
        when(restTemplate.exchange(eq("http://localhost:8085/api/payments/refund/booking/BKG-HTL-INIT-5"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "INITIATED", "refundId", "rfnd_pending_gw")));
        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-HTL-INIT-5/reconcile"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Transient poll failure"))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "PROCESSED", "refundId", "rfnd_pending_gw")));

        HotelCancellationResponse first = orchestrationService.cancelHotelBookingById(
                5L, null, "Bearer cust-jwt", null, null);
        assertEquals("INITIATED", first.getRefundStatus());
        assertEquals("rfnd_pending_gw", first.getRefundId());

        // Transient poll error must NOT downgrade INITIATED -> FAILED_RETRYABLE and must NOT issue second POST
        when(refundTaskRepository.findTasksForRetry(any(), any(Pageable.class)))
                .thenReturn(List.of(storedRefundTask.get()));
        refundWorker.recoverPendingRefunds();
        assertEquals("INITIATED", storedBooking.get().getRefundStatus());
        assertEquals("INITIATED", storedRefundTask.get().getStatus());

        // Subsequent poll via GET /reconcile confirms PROCESSED
        refundWorker.recoverPendingRefunds();
        assertEquals("PROCESSED", storedBooking.get().getRefundStatus());
        assertEquals("PROCESSED", storedRefundTask.get().getStatus());

        verify(restTemplate, times(1)).exchange(
                eq("http://localhost:8085/api/payments/refund/booking/BKG-HTL-INIT-5"),
                eq(HttpMethod.POST), any(), eq(Map.class));
        verify(restTemplate, times(2)).exchange(
                contains("/api/payments/refund/booking/BKG-HTL-INIT-5/reconcile"),
                eq(HttpMethod.GET), any(), eq(Map.class));
    }

    @Test
    @DisplayName("Finding 1: FAILED, REJECTED, UNKNOWN, and missing 2xx statuses NEVER mark refund PROCESSED and keep Booking & Task consistent")
    @SuppressWarnings({"rawtypes"})
    void testNonSuccessAndUnrecognizedStatuses_NeverMarkedProcessed() {
        LocalDate checkIn = LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(5);
        when(restTemplate.exchange(contains("/api/hotels/rooms/release-stay"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("released", true)));

        Map<String, String> cases = Map.of(
                "BKG-HTL-FAIL-10", "FAILED",
                "BKG-HTL-REJ-11", "REJECTED",
                "BKG-HTL-UNK-12", "UNKNOWN",
                "BKG-HTL-EMPTY-13", ""
        );
        long nextId = 10L;
        for (Map.Entry<String, String> entry : cases.entrySet()) {
            String ref = entry.getKey();
            String statusVal = entry.getValue();
            storedSnapshot.set(null);
            storedReleaseTask.set(null);
            storedRefundTask.set(null);
            Booking b = Booking.builder()
                    .id(nextId++).bookingRef(ref).customerId(10L).serviceType("HOTEL").serviceId(101L)
                    .totalAmount(4000.0).status("CONFIRMED")
                    .checkInDate(checkIn).checkOutDate(checkIn.plusDays(1))
                    .detailsJson("{\"checkInDate\":\"" + checkIn + "\"}")
                    .build();
            storedBooking.set(b);

            when(restTemplate.exchange(eq("http://localhost:8085/api/payments/refund/booking/" + ref), eq(HttpMethod.POST), any(), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(Map.of("refundStatus", statusVal, "refundId", "rfnd_" + ref)));

            HotelCancellationResponse resp = orchestrationService.cancelHotelBookingById(
                    b.getId(), null, "Bearer cust-jwt", null, null);

            String expectedState = "REJECTED".equals(statusVal) ? "FAILED_FINAL" : "FAILED_RETRYABLE";
            assertEquals(expectedState, resp.getRefundStatus(), "Unexpected refundStatus for case " + statusVal);
            assertEquals(expectedState, storedBooking.get().getRefundStatus());
            assertEquals(expectedState, storedRefundTask.get().getStatus());
            assertNotEquals("PROCESSED", storedBooking.get().getRefundStatus());
        }

        // Also test missing status key completely in 200 response body
        storedSnapshot.set(null);
        storedReleaseTask.set(null);
        storedRefundTask.set(null);
        Booking bMissing = Booking.builder()
                .id(19L).bookingRef("BKG-HTL-MISSING-19").customerId(10L).serviceType("HOTEL").serviceId(101L)
                .totalAmount(4000.0).status("CONFIRMED")
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(1))
                .detailsJson("{\"checkInDate\":\"" + checkIn + "\"}")
                .build();
        storedBooking.set(bMissing);
        when(restTemplate.exchange(eq("http://localhost:8085/api/payments/refund/booking/BKG-HTL-MISSING-19"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("message", "ok without status")));

        HotelCancellationResponse missingResp = orchestrationService.cancelHotelBookingById(
                19L, null, "Bearer cust-jwt", null, null);
        assertEquals("FAILED_RETRYABLE", missingResp.getRefundStatus());
        assertEquals("FAILED_RETRYABLE", storedBooking.get().getRefundStatus());
        assertEquals("FAILED_RETRYABLE", storedRefundTask.get().getStatus());
    }

    @Test
    @DisplayName("Finding 5: Timeout after initiation reconciles via GET /reconcile and never triggers duplicate POST refund")
    @SuppressWarnings({"rawtypes"})
    void testTimeoutOnInitiation_ReconcilesWithoutDuplicatePost() {
        LocalDate checkIn = LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(5);
        Booking b = Booking.builder()
                .id(25L).bookingRef("BKG-HTL-TIMEOUT-REC").customerId(10L).serviceType("HOTEL").serviceId(105L)
                .totalAmount(5500.0).status("CONFIRMED")
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(1))
                .detailsJson("{\"checkInDate\":\"" + checkIn + "\"}")
                .build();
        storedBooking.set(b);

        when(restTemplate.exchange(contains("/api/hotels/rooms/release-stay"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("released", true)));
        when(restTemplate.exchange(eq("http://localhost:8085/api/payments/refund/booking/BKG-HTL-TIMEOUT-REC"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Read timed out after Razorpay accepted request"));
        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-HTL-TIMEOUT-REC/reconcile"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "PENDING", "refundId", "rfnd_timeout_recovered")))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "PROCESSED", "refundId", "rfnd_timeout_recovered")));

        HotelCancellationResponse initial = orchestrationService.cancelHotelBookingById(
                25L, null, "Bearer cust-jwt", null, null);
        assertEquals("FAILED_RETRYABLE", initial.getRefundStatus());

        when(refundTaskRepository.findTasksForRetry(any(), any(Pageable.class)))
                .thenReturn(List.of(storedRefundTask.get()));

        // Worker sweep 1: /reconcile adopts PENDING -> keeps INITIATED without second POST
        refundWorker.recoverPendingRefunds();
        assertEquals("INITIATED", storedBooking.get().getRefundStatus());
        assertEquals("INITIATED", storedRefundTask.get().getStatus());
        assertEquals("rfnd_timeout_recovered", storedBooking.get().getRefundId());

        // Worker sweep 2: /reconcile sees PROCESSED -> settles cleanly
        refundWorker.recoverPendingRefunds();
        assertEquals("PROCESSED", storedBooking.get().getRefundStatus());
        assertEquals("PROCESSED", storedRefundTask.get().getStatus());

        verify(restTemplate, times(1)).exchange(
                eq("http://localhost:8085/api/payments/refund/booking/BKG-HTL-TIMEOUT-REC"),
                eq(HttpMethod.POST), any(), eq(Map.class));
    }

    @Test
    @DisplayName("Finding 1 & 5: Explicit gateway FAILED during poll of INITIATED refund moves Booking & Task consistently out of INITIATED")
    @SuppressWarnings({"rawtypes"})
    void testInitiatedRefundFailsLaterAtGateway_UpdatesBookingAndTaskConsistently() {
        LocalDate checkIn = LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(5);
        Booking b = Booking.builder()
                .id(26L).bookingRef("BKG-HTL-POLL-FAIL").customerId(10L).serviceType("HOTEL").serviceId(106L)
                .totalAmount(3000.0).status("CONFIRMED")
                .checkInDate(checkIn).checkOutDate(checkIn.plusDays(1))
                .detailsJson("{\"checkInDate\":\"" + checkIn + "\"}")
                .build();
        storedBooking.set(b);

        when(restTemplate.exchange(contains("/api/hotels/rooms/release-stay"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("released", true)));
        when(restTemplate.exchange(eq("http://localhost:8085/api/payments/refund/booking/BKG-HTL-POLL-FAIL"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "INITIATED", "refundId", "rfnd_poll_fail_1")));
        when(restTemplate.exchange(contains("/api/payments/refund/booking/BKG-HTL-POLL-FAIL/reconcile"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "FAILED", "refundId", "rfnd_poll_fail_1")));

        orchestrationService.cancelHotelBookingById(26L, null, "Bearer cust-jwt", null, null);
        assertEquals("INITIATED", storedBooking.get().getRefundStatus());

        when(refundTaskRepository.findTasksForRetry(any(), any(Pageable.class)))
                .thenReturn(List.of(storedRefundTask.get()));
        refundWorker.recoverPendingRefunds();

        assertEquals("FAILED_RETRYABLE", storedBooking.get().getRefundStatus());
        assertEquals("FAILED_RETRYABLE", storedRefundTask.get().getStatus());
        verify(restTemplate, times(1)).exchange(
                eq("http://localhost:8085/api/payments/refund/booking/BKG-HTL-POLL-FAIL"),
                eq(HttpMethod.POST), any(), eq(Map.class));
    }

    @Test
    @DisplayName("Additional Inventory Check: Legacy non-date-wise release extracts explicit room counts and fails closed on missing/malformed metadata")
    @SuppressWarnings({"rawtypes"})
    void testLegacyInventoryRelease_ValidCountsAndFailClosedOnMalformed() {
        // 1. Valid legacy booking with Rooms: 3
        Booking validLegacy = Booking.builder()
                .id(30L).bookingRef("BKG-LEG-VALID-3").customerId(10L).serviceType("HOTEL").serviceId(77L)
                .totalAmount(3000.0).status("CONFIRMED").detailsJson("Standard Room | Rooms: 3")
                .build();
        storedBooking.set(validLegacy);
        when(restTemplate.exchange(eq("http://localhost:8083/api/hotels/rooms/77/release?count=3"), eq(HttpMethod.PUT), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("released", true)));

        HotelInventoryReleaseTask validTask = HotelInventoryReleaseTask.builder()
                .id(701L).bookingId(30L).bookingRef("BKG-LEG-VALID-3").serviceId(77L).hotelId(77L)
                .dateWiseBooking(false).detailsJson("Standard Room | Rooms: 3").status("PENDING").attemptCount(1).maxAttempts(10).build();
        storedReleaseTask.set(validTask);
        assertTrue(releaseRecoveryService.executeClaimedRelease(validTask));
        assertEquals("RELEASED", storedReleaseTask.get().getStatus());
        verify(restTemplate).exchange(eq("http://localhost:8083/api/hotels/rooms/77/release?count=3"), eq(HttpMethod.PUT), any(), eq(Map.class));

        // 2. Malformed / missing / zero metadata MUST fail FINAL and never default silently to 1
        for (String badDetails : java.util.Arrays.asList(null, "", "   ", "{corruptedJson}", "{\"roomCount\":0}", "Rooms: 0", "Rooms: -2")) {
            storedSnapshot.set(null);
            Booking badLegacy = Booking.builder()
                    .id(31L).bookingRef("BKG-LEG-BAD").customerId(10L).serviceType("HOTEL").serviceId(77L)
                    .totalAmount(3000.0).status("CANCELLED").detailsJson(badDetails)
                    .build();
            storedBooking.set(badLegacy);
            HotelInventoryReleaseTask badTask = HotelInventoryReleaseTask.builder()
                    .id(702L).bookingId(31L).bookingRef("BKG-LEG-BAD").serviceId(77L).hotelId(77L)
                    .dateWiseBooking(false).detailsJson(badDetails).status("PROCESSING").attemptCount(1).maxAttempts(10).build();
            storedReleaseTask.set(badTask);

            assertFalse(releaseRecoveryService.executeClaimedRelease(badTask));
            assertEquals("FAILED_FINAL", storedReleaseTask.get().getStatus());
            assertEquals("FAILED_FINAL", storedBooking.get().getInventoryReleaseStatus());
        }
        verify(restTemplate, never()).exchange(contains("/release?count=1"), any(), any(), eq(Map.class));
    }
}
