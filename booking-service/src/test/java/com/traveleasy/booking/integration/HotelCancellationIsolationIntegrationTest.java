package com.traveleasy.booking.integration;

import com.traveleasy.booking.dto.HotelCancellationRequest;
import com.traveleasy.booking.dto.HotelCancellationResponse;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.model.BookingOutboxEvent;
import com.traveleasy.booking.model.HotelAllocationSnapshot;
import com.traveleasy.booking.model.HotelCancellationRefundTask;
import com.traveleasy.booking.model.HotelInventoryReleaseTask;
import com.traveleasy.booking.model.HotelReservationSnapshot;
import com.traveleasy.booking.repository.BookingOutboxEventRepository;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.repository.HotelCancellationRefundTaskRepository;
import com.traveleasy.booking.repository.HotelInventoryReleaseTaskRepository;
import com.traveleasy.booking.repository.HotelReservationSnapshotRepository;
import com.traveleasy.booking.service.HotelCancellationOrchestrationService;
import com.traveleasy.booking.service.HotelCancellationPolicyService;
import com.traveleasy.booking.service.HotelCancellationRefundRecoveryService;
import com.traveleasy.booking.service.HotelInventoryReleaseRecoveryService;
import com.traveleasy.booking.worker.HotelCancellationRefundRecoveryWorker;
import com.traveleasy.booking.worker.HotelInventoryReleaseRecoveryWorker;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
public class HotelCancellationIsolationIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private HotelReservationSnapshotRepository snapshotRepository;

    @Autowired
    private HotelInventoryReleaseTaskRepository releaseTaskRepository;

    @Autowired
    private HotelCancellationRefundTaskRepository refundTaskRepository;

    @Autowired
    private BookingOutboxEventRepository outboxEventRepository;

    @Autowired
    private HotelCancellationOrchestrationService orchestrationService;

    @Autowired
    private HotelInventoryReleaseRecoveryService releaseRecoveryService;

    @Autowired
    private HotelCancellationRefundRecoveryService refundRecoveryService;

    @Autowired
    private HotelInventoryReleaseRecoveryWorker releaseWorker;

    @Autowired
    private HotelCancellationRefundRecoveryWorker refundWorker;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final List<String> createdBookingRefs = new ArrayList<>();
    private RestTemplate mockRestTemplate;

    @BeforeEach
    void setUp() {
        mockRestTemplate = mock(RestTemplate.class);
        releaseRecoveryService.setRestTemplate(mockRestTemplate);
        refundRecoveryService.setRestTemplate(mockRestTemplate);
    }

    @AfterEach
    void tearDown() {
        releaseWorker.setRecoveryEnabled(false);
        refundWorker.setRecoveryEnabled(false);
        releaseRecoveryService.setRestTemplate(new RestTemplate());
        refundRecoveryService.setRestTemplate(new RestTemplate());

        for (String ref : createdBookingRefs) {
            outboxEventRepository.findByEventId("BKG-EVT-" + ref + "-CANCELLED")
                    .ifPresent(outboxEventRepository::delete);
            refundTaskRepository.findByBookingRef(ref).ifPresent(refundTaskRepository::delete);
            releaseTaskRepository.findByBookingRef(ref).ifPresent(releaseTaskRepository::delete);
            snapshotRepository.findByBookingRef(ref).ifPresent(snapshotRepository::delete);
            bookingRepository.findByBookingRef(ref).ifPresent(bookingRepository::delete);
        }
        createdBookingRefs.clear();
    }

    private String uniqueRef(String prefix) {
        String ref = prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        createdBookingRefs.add(ref);
        return ref;
    }

    private String mintCustomerToken(Long customerId) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .setSubject("it-traveller@example.com")
                .claim("userId", customerId)
                .claim("role", "ROLE_CUSTOMER")
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 300_000L))
                .signWith(key)
                .compact();
    }

    private HotelReservationSnapshot persistSnapshot(Booking savedBooking, LocalDate checkIn, LocalDate checkOut, String status) {
        HotelReservationSnapshot snap = HotelReservationSnapshot.builder()
                .bookingId(savedBooking.getId())
                .bookingRef(savedBooking.getBookingRef())
                .hotelId(savedBooking.getServiceId())
                .hotelName("Integration Grand Hotel")
                .primaryGuestName("IT Guest")
                .primaryGuestEmail("it-guest@example.com")
                .primaryGuestMobile("+919876543210")
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .totalNights(2)
                .totalAmount(savedBooking.getTotalAmount())
                .currency("INR")
                .status(status)
                .build();

        HotelAllocationSnapshot alloc = HotelAllocationSnapshot.builder()
                .roomId(301L)
                .roomTypeName("Deluxe Suite")
                .quantity(1)
                .allocationTotal(savedBooking.getTotalAmount())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .status(status)
                .build();
        snap.addAllocation(alloc);
        return snapshotRepository.save(snap);
    }

    @Test
    @DisplayName("Simulated restart recovery: expired-lease release task and retryable refund task are claimed and completed in MySQL")
    @SuppressWarnings({"rawtypes"})
    void testPersistenceAndRestartRecovery_ExpiredLeaseAndPendingTasks() {
        String ref = uniqueRef("BKG-HTL-IT-REC");
        LocalDate checkIn = LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(3);
        LocalDate checkOut = checkIn.plusDays(2);

        Booking booking = bookingRepository.save(Booking.builder()
                .bookingRef(ref)
                .customerId(501L)
                .customerName("Restart Recovery Guest")
                .customerEmail("recovery@example.com")
                .serviceType("HOTEL")
                .serviceId(88L)
                .providerId(22L)
                .serviceTitle("Recovery Palace Hotel")
                .totalAmount(4800.0)
                .status("CANCELLED")
                .cancelledAt(LocalDateTime.now())
                .cancellationPolicyTier(HotelCancellationPolicyService.TIER_PARTIAL_50)
                .cancellationFee(2400.0)
                .refundAmount(2400.0)
                .refundStatus("FAILED_RETRYABLE")
                .refundAttemptKey("htl-rfnd-" + ref)
                .inventoryReleaseStatus("PENDING")
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .detailsJson("{\"checkInDate\":\"" + checkIn + "\",\"checkOutDate\":\"" + checkOut + "\"}")
                .build());

        persistSnapshot(booking, checkIn, checkOut, "CANCELLED");

        releaseTaskRepository.save(HotelInventoryReleaseTask.builder()
                .bookingId(booking.getId())
                .bookingRef(ref)
                .hotelId(88L)
                .serviceId(88L)
                .dateWiseBooking(true)
                .status("PROCESSING")
                .attemptCount(0)
                .maxAttempts(10)
                .lockedBy("crashed-instance-1")
                .lockedUntil(Instant.now().minusSeconds(120))
                .build());

        refundTaskRepository.save(HotelCancellationRefundTask.builder()
                .bookingId(booking.getId())
                .bookingRef(ref)
                .idempotencyKey("htl-rfnd-" + ref)
                .refundAmount(2400.0)
                .totalBookingAmount(4800.0)
                .cancellationFee(2400.0)
                .policyTier(HotelCancellationPolicyService.TIER_PARTIAL_50)
                .reason("Simulated restart recovery")
                .status("FAILED_RETRYABLE")
                .attemptCount(1)
                .maxAttempts(10)
                .nextRetryAt(Instant.now().minusSeconds(15))
                .build());

        when(mockRestTemplate.exchange(contains("/api/hotels/rooms/release-stay"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("released", true)));
        when(mockRestTemplate.exchange(contains("/api/payments/refund/booking/" + ref + "/reconcile"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "NOT_FOUND")));
        when(mockRestTemplate.exchange(contains("/api/payments/refund/booking/" + ref), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "PROCESSED", "refundId", "rfnd_restart_rec_1")));

        releaseWorker.setRecoveryEnabled(true);
        refundWorker.setRecoveryEnabled(true);
        releaseWorker.recoverPendingInventoryReleases();
        refundWorker.recoverPendingRefunds();

        Booking refreshed = bookingRepository.findByBookingRef(ref).orElseThrow();
        assertEquals("RELEASED", refreshed.getInventoryReleaseStatus());
        assertNotNull(refreshed.getInventoryReleasedAt());
        assertEquals("PROCESSED", refreshed.getRefundStatus());
        assertEquals("rfnd_restart_rec_1", refreshed.getRefundId());

        HotelReservationSnapshot refreshedSnap = snapshotRepository.findByBookingRef(ref).orElseThrow();
        assertEquals("RELEASED", refreshedSnap.getStatus());

        HotelInventoryReleaseTask refreshedReleaseTask = releaseTaskRepository.findByBookingRef(ref).orElseThrow();
        assertEquals("RELEASED", refreshedReleaseTask.getStatus());
        assertEquals(1, refreshedReleaseTask.getAttemptCount());
        assertNull(refreshedReleaseTask.getLockedBy());

        HotelCancellationRefundTask refreshedRefundTask = refundTaskRepository.findByBookingRef(ref).orElseThrow();
        assertEquals("PROCESSED", refreshedRefundTask.getStatus());
        assertEquals(2, refreshedRefundTask.getAttemptCount());
        assertEquals("rfnd_restart_rec_1", refreshedRefundTask.getRazorpayRefundId());
        assertNull(refreshedRefundTask.getLockedBy());
    }

    @Test
    @DisplayName("Concurrent double-cancel threads yield 1 transition, 1 task pair, 1 outbox event, and 1 external call each")
    @SuppressWarnings({"rawtypes"})
    void testConcurrentDoubleCancel_SingleTransitionSingleTasksSingleExternalCalls() throws Exception {
        String ref = uniqueRef("BKG-HTL-IT-CONC");
        LocalDate checkIn = LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(5);
        LocalDate checkOut = checkIn.plusDays(2);

        Booking booking = bookingRepository.save(Booking.builder()
                .bookingRef(ref)
                .customerId(44L)
                .customerName("Concurrent Guest")
                .customerEmail("concurrent@example.com")
                .serviceType("HOTEL")
                .serviceId(91L)
                .providerId(33L)
                .serviceTitle("Concurrent Suites")
                .totalAmount(6400.0)
                .status("CONFIRMED")
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .detailsJson("{\"checkInDate\":\"" + checkIn + "\",\"checkOutDate\":\"" + checkOut + "\"}")
                .build());

        persistSnapshot(booking, checkIn, checkOut, "HELD");

        AtomicInteger releaseCalls = new AtomicInteger(0);
        AtomicInteger refundCalls = new AtomicInteger(0);

        when(mockRestTemplate.exchange(contains("/api/hotels/rooms/release-stay?bookingRef=" + ref), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenAnswer(inv -> {
                    releaseCalls.incrementAndGet();
                    Thread.sleep(40);
                    return ResponseEntity.ok(Map.of("released", true));
                });
        when(mockRestTemplate.exchange(contains("/api/payments/refund/booking/" + ref), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenAnswer(inv -> {
                    refundCalls.incrementAndGet();
                    Thread.sleep(40);
                    return ResponseEntity.ok(Map.of("refundStatus", "PROCESSED", "refundId", "rfnd_conc_1"));
                });

        String customerJwt = mintCustomerToken(44L);
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        List<HotelCancellationResponse> responses = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    responses.add(orchestrationService.cancelHotelBookingByRef(
                            ref,
                            new HotelCancellationRequest("Concurrent user click"),
                            "Bearer " + customerJwt,
                            null,
                            null));
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(readyLatch.await(10, TimeUnit.SECONDS));
        startLatch.countDown();
        assertTrue(doneLatch.await(20, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Unexpected concurrent errors: " + errors);
        assertEquals(threads, responses.size());

        long nonIdempotentCount = responses.stream().filter(r -> !r.isIdempotent()).count();
        long idempotentCount = responses.stream().filter(HotelCancellationResponse::isIdempotent).count();
        assertEquals(1, nonIdempotentCount, "Exactly 1 thread should perform initial cancellation transition");
        assertEquals(threads - 1, idempotentCount, "Remaining threads must receive idempotent replay");

        assertEquals(1, releaseCalls.get(), "Exactly 1 inventory release call expected");
        assertEquals(1, refundCalls.get(), "Exactly 1 payment refund call expected");

        Booking finalBooking = bookingRepository.findByBookingRef(ref).orElseThrow();
        assertEquals("CANCELLED", finalBooking.getStatus());
        assertEquals("RELEASED", finalBooking.getInventoryReleaseStatus());
        assertEquals("PROCESSED", finalBooking.getRefundStatus());
        assertEquals("htl-rfnd-" + ref, finalBooking.getRefundAttemptKey());

        assertTrue(releaseTaskRepository.findByBookingRef(ref).isPresent());
        assertTrue(refundTaskRepository.findByBookingRef(ref).isPresent());
        BookingOutboxEvent outbox = outboxEventRepository.findByEventId("BKG-EVT-" + ref + "-CANCELLED").orElse(null);
        assertNotNull(outbox);
        assertEquals("HOTEL_BOOKING_CANCELLED", outbox.getEventType());
    }

    @Test
    @DisplayName("Simultaneous inventory-release and refund worker completions do not overwrite each other's Booking status fields")
    @SuppressWarnings({"rawtypes"})
    void testSimultaneousInventoryReleaseAndRefundCompletion_NoFieldOverwrite() throws Exception {
        String ref = uniqueRef("BKG-HTL-IT-SIMUL");
        LocalDate checkIn = LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(5);
        LocalDate checkOut = checkIn.plusDays(1);

        Booking booking = bookingRepository.save(Booking.builder()
                .bookingRef(ref)
                .customerId(77L)
                .customerName("Simultaneous Guest")
                .customerEmail("simul@example.com")
                .serviceType("HOTEL")
                .serviceId(99L)
                .providerId(44L)
                .serviceTitle("Simultaneous Plaza")
                .totalAmount(3500.0)
                .status("CANCELLED")
                .cancelledAt(LocalDateTime.now())
                .cancellationPolicyTier(HotelCancellationPolicyService.TIER_FREE_48H)
                .cancellationFee(0.0)
                .refundAmount(3500.0)
                .refundStatus("PENDING")
                .refundAttemptKey("htl-rfnd-" + ref)
                .inventoryReleaseStatus("PENDING")
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .detailsJson("{\"checkInDate\":\"" + checkIn + "\"}")
                .build());

        HotelInventoryReleaseTask relTask = releaseTaskRepository.save(HotelInventoryReleaseTask.builder()
                .bookingId(booking.getId())
                .bookingRef(ref)
                .hotelId(99L)
                .serviceId(99L)
                .dateWiseBooking(true)
                .status("PENDING")
                .attemptCount(0)
                .maxAttempts(10)
                .build());

        HotelCancellationRefundTask rfdTask = refundTaskRepository.save(HotelCancellationRefundTask.builder()
                .bookingId(booking.getId())
                .bookingRef(ref)
                .idempotencyKey("htl-rfnd-" + ref)
                .refundAmount(3500.0)
                .totalBookingAmount(3500.0)
                .cancellationFee(0.0)
                .policyTier(HotelCancellationPolicyService.TIER_FREE_48H)
                .reason("Simultaneous test")
                .status("PENDING")
                .attemptCount(0)
                .maxAttempts(10)
                .build());

        when(mockRestTemplate.exchange(contains("/api/hotels/rooms/release-stay?bookingRef=" + ref), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("released", true)));
        when(mockRestTemplate.exchange(contains("/api/payments/refund/booking/" + ref), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "PROCESSED", "refundId", "rfnd_concurrent_ok")));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        executor.submit(() -> {
            try {
                startLatch.await();
                releaseRecoveryService.claimAndExecute(relTask.getId(), "w-inv", 60);
            } catch (InterruptedException ignored) {
            } finally {
                doneLatch.countDown();
            }
        });
        executor.submit(() -> {
            try {
                startLatch.await();
                refundRecoveryService.claimAndExecute(rfdTask.getId(), "w-rfd", 60);
            } catch (InterruptedException ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        Booking finalBooking = bookingRepository.findByBookingRef(ref).orElseThrow();
        assertEquals("RELEASED", finalBooking.getInventoryReleaseStatus());
        assertNotNull(finalBooking.getInventoryReleasedAt());
        assertEquals("PROCESSED", finalBooking.getRefundStatus());
        assertEquals("rfnd_concurrent_ok", finalBooking.getRefundId());
    }

    @Test
    @DisplayName("Integration: INITIATED hotel refund reconciles via GET /reconcile in MySQL without duplicate POST")
    @SuppressWarnings({"rawtypes"})
    void testInitiatedRefundReconcilesViaGetInMySQLWithoutDuplicatePost() {
        String ref = uniqueRef("BKG-HTL-IT-REC-POLL");
        LocalDate checkIn = LocalDate.now(HotelCancellationPolicyService.POLICY_ZONE_ID).plusDays(4);
        LocalDate checkOut = checkIn.plusDays(1);

        Booking booking = bookingRepository.save(Booking.builder()
                .bookingRef(ref)
                .customerId(88L)
                .customerName("Reconcile Integration Guest")
                .customerEmail("reconcile.it@example.com")
                .serviceType("HOTEL")
                .serviceId(120L)
                .providerId(55L)
                .serviceTitle("Reconcile Grand")
                .totalAmount(4200.0)
                .status("CANCELLED")
                .cancelledAt(LocalDateTime.now())
                .cancellationPolicyTier(HotelCancellationPolicyService.TIER_FREE_48H)
                .cancellationFee(0.0)
                .refundAmount(4200.0)
                .refundStatus("PENDING")
                .refundAttemptKey("htl-rfnd-" + ref)
                .inventoryReleaseStatus("RELEASED")
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .detailsJson("{\"checkInDate\":\"" + checkIn + "\"}")
                .build());

        HotelCancellationRefundTask task = refundTaskRepository.save(HotelCancellationRefundTask.builder()
                .bookingId(booking.getId())
                .bookingRef(ref)
                .idempotencyKey("htl-rfnd-" + ref)
                .refundAmount(4200.0)
                .totalBookingAmount(4200.0)
                .cancellationFee(0.0)
                .policyTier(HotelCancellationPolicyService.TIER_FREE_48H)
                .reason("Integration reconcile test")
                .status("PENDING")
                .attemptCount(0)
                .maxAttempts(10)
                .build());

        when(mockRestTemplate.exchange(eq("http://localhost:8085/api/payments/refund/booking/" + ref), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "INITIATED", "refundId", "rfnd_it_poll_1")));
        when(mockRestTemplate.exchange(contains("/api/payments/refund/booking/" + ref + "/reconcile"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "PENDING", "refundId", "rfnd_it_poll_1")))
                .thenReturn(ResponseEntity.ok(Map.of("refundStatus", "PROCESSED", "refundId", "rfnd_it_poll_1")));

        // 1. Initial initiation -> INITIATED
        refundRecoveryService.attemptImmediateRefund(task);
        Booking afterInitBooking = bookingRepository.findByBookingRef(ref).orElseThrow();
        HotelCancellationRefundTask afterInitTask = refundTaskRepository.findByBookingRef(ref).orElseThrow();
        assertEquals("INITIATED", afterInitBooking.getRefundStatus());
        assertEquals("INITIATED", afterInitTask.getStatus());
        assertEquals("rfnd_it_poll_1", afterInitBooking.getRefundId());

        // 2. Make task due for poll and run claimAndExecute twice -> PENDING then PROCESSED
        refundRecoveryService.claimAndExecute(task.getId(), "w-poll-1", 60);
        Booking stillInitiated = bookingRepository.findByBookingRef(ref).orElseThrow();
        assertEquals("INITIATED", stillInitiated.getRefundStatus());

        refundRecoveryService.claimAndExecute(task.getId(), "w-poll-2", 60);
        Booking settledBooking = bookingRepository.findByBookingRef(ref).orElseThrow();
        HotelCancellationRefundTask settledTask = refundTaskRepository.findByBookingRef(ref).orElseThrow();
        assertEquals("PROCESSED", settledBooking.getRefundStatus());
        assertEquals("PROCESSED", settledTask.getStatus());
        assertEquals("rfnd_it_poll_1", settledBooking.getRefundId());

        verify(mockRestTemplate, times(1)).exchange(
                eq("http://localhost:8085/api/payments/refund/booking/" + ref),
                eq(HttpMethod.POST), any(), eq(Map.class));
        verify(mockRestTemplate, times(2)).exchange(
                contains("/api/payments/refund/booking/" + ref + "/reconcile"),
                eq(HttpMethod.GET), any(), eq(Map.class));
    }
}
