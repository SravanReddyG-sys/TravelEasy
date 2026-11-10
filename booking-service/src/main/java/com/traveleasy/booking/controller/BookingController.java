package com.traveleasy.booking.controller;



import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.dto.BookingStatsResponse;
import com.traveleasy.booking.dto.BookingTrendResponse;
import com.traveleasy.booking.dto.RecentBookingResponse;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.service.BookingExpiryReconciliationService;
import com.traveleasy.booking.service.BookingInternalTokenService;
import com.traveleasy.booking.service.BookingSecurityService;
import com.traveleasy.booking.model.ScheduleCancellationBatch;
import com.traveleasy.booking.service.ScheduleCancellationCoordinatorService;
import com.traveleasy.booking.service.BookingStateTransitionService;
import com.traveleasy.booking.service.HotelCancellationPolicyService;
import com.traveleasy.booking.service.HotelGuestLifecycleService;
import com.traveleasy.booking.service.HotelBookingOrchestrationService;
import com.traveleasy.booking.dto.CancellationPolicyResult;
import com.traveleasy.booking.dto.BookingLifecycleOperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Optional;


import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import org.springframework.web.client.RestTemplate;



import java.time.LocalDateTime;

import java.util.ArrayList;

import java.util.Collections;

import java.util.List;

import java.util.Map;

import java.util.UUID;

import java.util.regex.Matcher;

import java.util.regex.Pattern;



@RestController

@RequestMapping("/api/bookings")

@RequiredArgsConstructor

public class BookingController {



    private final BookingRepository bookingRepository;

    private final BookingSecurityService securityService;

    private final BookingExpiryReconciliationService expiryService;
    private final ScheduleCancellationCoordinatorService coordinatorService;
    private final BookingStateTransitionService stateTransitionService;
    private final BookingInternalTokenService internalTokenService;
    private final HotelCancellationPolicyService cancellationPolicyService;
    private final HotelGuestLifecycleService guestLifecycleService;
    private final HotelBookingOrchestrationService hotelOrchestrationService;
    private final com.traveleasy.booking.repository.HotelReservationSnapshotRepository snapshotRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RestTemplate restTemplate = new RestTemplate();



    public void setRestTemplate(RestTemplate restTemplate) {

        this.restTemplate = restTemplate;

    }



    @org.springframework.beans.factory.annotation.Value("${bus.service.url:http://localhost:8082}")

    private String busServiceUrl;



    @org.springframework.beans.factory.annotation.Value("${payment.service.url:http://localhost:8085}")

    private String paymentServiceUrl;

    @org.springframework.beans.factory.annotation.Value("${hotel.service.url:http://localhost:8083}")

    private String hotelServiceUrl;



    @PostMapping

    public ResponseEntity<?> createBooking(@RequestBody Booking booking,

                                           @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader != null && authHeader.startsWith("Bearer ")) {

            String token = authHeader.substring(7).trim();

            if (securityService.validateToken(token)) {

                String callerRole = securityService.extractRole(token);

                if ("ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole) || "ROLE_HOTEL_MANAGER".equalsIgnoreCase(callerRole)) {

                    return ResponseEntity.status(HttpStatus.FORBIDDEN)

                            .body(Map.of("message", "Management accounts are prohibited from booking customer tickets."));

                }

                Long callerUserId = securityService.extractUserId(token);

                String callerEmail = securityService.extractEmail(token);

                if (callerUserId != null) {

                    booking.setCustomerId(callerUserId);

                }

                if (callerEmail != null && (booking.getCustomerEmail() == null || booking.getCustomerEmail().isEmpty())) {

                    booking.setCustomerEmail(callerEmail);

                }

            } else {

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)

                        .body(Map.of("message", "Invalid or expired authorization token"));

            }

        }



        if (booking.getBookingRef() == null) {

            booking.setBookingRef("BKG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        }

        if (booking.getStatus() == null) {

            booking.setStatus("PENDING_PAYMENT");

        }

        if (booking.getExpiresAt() == null) {

            booking.setExpiresAt(LocalDateTime.now().plusMinutes(15));

        }

        if ("HOTEL".equalsIgnoreCase(booking.getServiceType()) && booking.getServiceId() != null) {
            resolveHotelProviderId(booking);
            if (booking.getCheckInDate() == null) {
                booking.setCheckInDate(booking.resolveCheckInDate());
            }
            if (booking.getCheckOutDate() == null) {
                booking.setCheckOutDate(booking.resolveCheckOutDate());
            }

            // WP-HB-05: Consolidated multi-allocation hotel reservation orchestration
            if (booking.getAllocations() != null && !booking.getAllocations().isEmpty()) {
                com.traveleasy.booking.dto.HotelReservationInitiationRequest initReq =
                        com.traveleasy.booking.dto.HotelReservationInitiationRequest.builder()
                                .bookingRef(booking.getBookingRef())
                                .customerId(booking.getCustomerId())
                                .customerName(booking.getCustomerName())
                                .customerEmail(booking.getCustomerEmail())
                                .hotelId(booking.getServiceId())
                                .hotelName(booking.getServiceTitle())
                                .checkInDate(booking.getCheckInDate())
                                .checkOutDate(booking.getCheckOutDate())
                                .allocations(booking.getAllocations())
                                .guestDetailsJson(booking.getDetailsJson())
                                .build();

                com.traveleasy.booking.dto.HotelReservationInitiationResponse initResp =
                        hotelOrchestrationService.initiateHotelReservation(initReq, booking.getCustomerId(), booking.getCustomerEmail());
                Booking initiatedBooking = bookingRepository.findById(initResp.getBookingId()).orElseThrow();
                return ResponseEntity.status(HttpStatus.CREATED).body(initiatedBooking);
            }
        }

        Booking saved = bookingRepository.save(booking);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);

    }

    /**
     * Dedicated consolidated hotel reservation initiation endpoint (WP-HB-05).
     */
    @PostMapping("/hotel-reservations")
    public ResponseEntity<?> initiateHotelReservation(
            @RequestBody com.traveleasy.booking.dto.HotelReservationInitiationRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Long callerUserId = null;
        String callerEmail = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (securityService.validateToken(token)) {
                String callerRole = securityService.extractRole(token);
                if ("ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole) || "ROLE_HOTEL_MANAGER".equalsIgnoreCase(callerRole)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Management accounts are prohibited from booking customer tickets."));
                }
                callerUserId = securityService.extractUserId(token);
                callerEmail = securityService.extractEmail(token);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }
        }

        com.traveleasy.booking.dto.HotelReservationInitiationResponse response =
                hotelOrchestrationService.initiateHotelReservation(request, callerUserId, callerEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }



    private static final List<String> CONFIRMED_BOOKING_STATUSES = List.of(
            "CONFIRMED", "CHECKED_IN", "CHECKED_OUT", "NO_SHOW", "CANCELLED", "COMPLETED"
    );

    private final Map<Long, Map<String, Object>> busScheduleCache = new java.util.concurrent.ConcurrentHashMap<>();

    private void enrichBookingWithBusDetails(Booking b) {
        if (b == null || !"BUS".equalsIgnoreCase(b.getServiceType()) || b.getServiceId() == null) {
            return;
        }
        Long scheduleId = b.getServiceId();
        try {
            Map<String, Object> details = busScheduleCache.computeIfAbsent(scheduleId, id -> {
                try {
                    String url = busServiceUrl + "/api/buses/schedules/" + id + "/layout";
                    ResponseEntity<Map> res = restTemplate.getForEntity(url, Map.class);
                    if (res != null && res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                        return (Map<String, Object>) res.getBody();
                    }
                } catch (Exception e) {
                    // Fall back cleanly if schedule layout lookup fails
                }
                return Collections.emptyMap();
            });

            if (details != null && !details.isEmpty()) {
                if (details.get("exteriorImageUrl") != null) {
                    b.setExteriorImageUrl(String.valueOf(details.get("exteriorImageUrl")));
                }
                if (details.get("busType") != null) {
                    b.setBusType(String.valueOf(details.get("busType")));
                }
                if (details.get("facilityType") != null) {
                    b.setFacilityType(String.valueOf(details.get("facilityType")));
                }
                if (details.get("businessName") != null) {
                    b.setOperatorBusinessName(String.valueOf(details.get("businessName")));
                } else if (details.get("operatorName") != null) {
                    b.setOperatorBusinessName(String.valueOf(details.get("operatorName")));
                }
            }
        } catch (Exception ignored) {}
    }



    @GetMapping("/customer/{customerId}")

    public ResponseEntity<?> getCustomerBookings(@PathVariable Long customerId,

                                                @RequestParam(value = "includeUnconfirmed", required = false, defaultValue = "false") boolean includeUnconfirmed,

                                                @RequestHeader(value = "Authorization", required = false) String authHeader,

                                                @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        if (authHeader != null && authHeader.startsWith("Bearer ")) {

            String token = authHeader.substring(7).trim();

            if (!securityService.validateToken(token)) {

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid or expired token"));

            }

            Long callerUserId = securityService.extractUserId(token);

            String callerRole = securityService.extractRole(token);

            boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);

            boolean isInternal = "traveller-service".equalsIgnoreCase(internalService);



            if (!isAdmin && !isInternal && callerUserId != null && !callerUserId.equals(customerId)) {

                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied to customer bookings"));

            }

        }



        List<Booking> allCustomerBookings = bookingRepository.findByCustomerIdOrderByBookingDateDesc(customerId);

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);

        for (Booking b : allCustomerBookings) {

            // Reconcile status: If payment was successfully captured, ensure status is CONFIRMED and seats allocated

            if (!"CANCELLED".equalsIgnoreCase(b.getStatus()) && !"CONFIRMED".equalsIgnoreCase(b.getStatus())) {

                boolean paid = verifyPaymentStatusWithPaymentService(b.getBookingRef(), b.getTotalAmount());

                if (paid) {
                    confirmSeatsInBusService(b);
                    stateTransitionService.confirmBooking(b);
                }

            }

            if ("PENDING_PAYMENT".equalsIgnoreCase(b.getStatus()) || "INITIATED".equalsIgnoreCase(b.getStatus())) {

                if (b.getBookingDate() != null && b.getBookingDate().isBefore(threshold)) {

                    boolean expired = expiryService.expireBookingByRef(b.getBookingRef());

                    if (expired) {

                        b.setStatus("EXPIRED");

                    }

                }

            }

        }



        for (Booking b : allCustomerBookings) {
            enrichBookingWithBusDetails(b);
        }

        if (includeUnconfirmed) {
            return ResponseEntity.ok(allCustomerBookings);

        }



        // Return only authoritatively confirmed, completed, or cancelled bookings.

        // Temporary seat holds and incomplete payment attempts are excluded.

        List<Booking> confirmedOnly = allCustomerBookings.stream()

                .filter(b -> CONFIRMED_BOOKING_STATUSES.contains(b.getStatus()))

                .toList();



        return ResponseEntity.ok(confirmedOnly);

    }



    @GetMapping("/provider/{providerId}")
    public ResponseEntity<?> getProviderBookings(
            @PathVariable Long providerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to access provider bookings"));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        String callerRole = securityService.extractRole(token);
        if (callerRole == null || callerRole.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid role claim"));
        }

        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isProvider = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole) || "ROLE_HOTEL_MANAGER".equalsIgnoreCase(callerRole);

        if (!isAdmin && !isProvider) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Provider or Admin role required"));
        }

        Long callerUserId = securityService.extractUserId(token);
        if (callerUserId == null || callerUserId <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Missing or invalid user identity in token"));
        }

        if (isProvider && !callerUserId.equals(providerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Cannot access bookings for another provider"));
        }

        return ResponseEntity.ok(bookingRepository.findByProviderIdOrderByBookingDateDesc(providerId));
    }

    /**
     * Validates administrator credentials from the Authorization header.
     * Returns null if authorized as ROLE_ADMIN, or an appropriate 401/403 ResponseEntity.
     */
    private ResponseEntity<?> validateAdminAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authorization Bearer token is required"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }
        String role = securityService.extractRole(token);
        if (!"ROLE_ADMIN".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Administrator role required"));
        }
        return null;
    }

    /**
     * Administrative aggregate statistics for Platform Bookings and Service Breakdown.
     * Applies authoritative confirmed historical booking definition.
     * Requires ROLE_ADMIN.
     */
    @GetMapping("/admin/stats")
    public ResponseEntity<?> getBookingStats(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }

        List<Object[]> results = bookingRepository.countConfirmedBookingsByServiceType();
        long busCount = 0;
        long hotelCount = 0;

        for (Object[] row : results) {
            if (row != null && row.length >= 2 && row[0] != null) {
                String type = row[0].toString().toUpperCase();
                long count = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
                if ("BUS".equals(type)) {
                    busCount = count;
                } else if ("HOTEL".equals(type)) {
                    hotelCount = count;
                }
            }
        }

        long totalBookings = busCount + hotelCount;
        double busPct = 0.0;
        double hotelPct = 0.0;
        if (totalBookings > 0) {
            busPct = Math.round((busCount * 1000.0) / totalBookings) / 10.0;
            hotelPct = Math.round((hotelCount * 1000.0) / totalBookings) / 10.0;
        }

        BookingStatsResponse response = BookingStatsResponse.builder()
                .totalBookings(totalBookings)
                .serviceBreakdown(BookingStatsResponse.ServiceBreakdown.builder()
                        .bus(BookingStatsResponse.ServiceMetric.builder()
                                .count(busCount)
                                .percentage(busPct)
                                .build())
                        .hotel(BookingStatsResponse.ServiceMetric.builder()
                                .count(hotelCount)
                                .percentage(hotelPct)
                                .build())
                        .build())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Administrative endpoint for date-wise booking trends line chart.
     * Returns a continuous timeline of confirmed bookings over the requested days.
     * Requires ROLE_ADMIN.
     */
    @GetMapping("/admin/trends")
    public ResponseEntity<?> getBookingTrends(
            @RequestParam(defaultValue = "7") int days,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }

        int boundedDays = Math.max(1, Math.min(90, days));
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(boundedDays - 1);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = today.atTime(23, 59, 59, 999999999);

        // Pre-populate continuous timeline map with 0 counts
        Map<LocalDate, Long> dateMap = new LinkedHashMap<>();
        for (int i = 0; i < boundedDays; i++) {
            dateMap.put(startDate.plusDays(i), 0L);
        }

        List<Object[]> queryResults = bookingRepository.countConfirmedBookingsByDateNative(startDateTime, endDateTime);
        for (Object[] row : queryResults) {
            if (row != null && row.length >= 2 && row[0] != null) {
                LocalDate dt = null;
                if (row[0] instanceof java.sql.Date) {
                    dt = ((java.sql.Date) row[0]).toLocalDate();
                } else if (row[0] instanceof LocalDate) {
                    dt = (LocalDate) row[0];
                } else {
                    try {
                        dt = LocalDate.parse(row[0].toString().substring(0, 10));
                    } catch (Exception ignored) {}
                }

                if (dt != null && dateMap.containsKey(dt)) {
                    long count = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
                    dateMap.put(dt, count);
                }
            }
        }

        List<BookingTrendResponse> response = dateMap.entrySet().stream()
                .map(e -> BookingTrendResponse.builder()
                        .date(e.getKey())
                        .bookingCount(e.getValue())
                        .build())
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Administrative endpoint for latest genuine confirmed bookings.
     * Sorted newest first without expensive remote detail enrichment.
     * Requires ROLE_ADMIN.
     */
    @GetMapping("/admin/recent")
    public ResponseEntity<?> getRecentBookings(
            @RequestParam(defaultValue = "5") int limit,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }

        int boundedLimit = Math.max(1, Math.min(50, limit));
        Pageable pageable = PageRequest.of(0, boundedLimit);

        Page<Booking> bookingsPage = bookingRepository.findRecentConfirmedBookings(pageable);

        List<RecentBookingResponse> response = bookingsPage.getContent().stream()
                .map(b -> {
                    String customerName = b.getCustomerName();
                    if (customerName == null || customerName.trim().isEmpty()) {
                        customerName = b.getCustomerEmail() != null && !b.getCustomerEmail().trim().isEmpty()
                                ? b.getCustomerEmail()
                                : "Guest Traveller";
                    }
                    return RecentBookingResponse.builder()
                            .bookingRef(b.getBookingRef())
                            .serviceType(b.getServiceType() != null ? b.getServiceType().toUpperCase() : "BUS")
                            .customerName(customerName)
                            .totalAmount(b.getTotalAmount())
                            .status(b.getStatus())
                            .bookingDate(b.getBookingDate())
                            .build();
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllBookings(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }

        List<Booking> all = bookingRepository.findAll();
        for (Booking b : all) {
            enrichBookingWithBusDetails(b);
        }
        return ResponseEntity.ok(all);
    }



    @GetMapping("/{id}")
    public ResponseEntity<?> getBookingById(@PathVariable Long id) {
        return bookingRepository.findById(id)
                .map(b -> {
                    enrichBookingWithBusDetails(b);
                    return ResponseEntity.ok(b);
                })
                .orElse(ResponseEntity.notFound().build());
    }



    @GetMapping("/ref/{bookingRef}")
    public ResponseEntity<?> getBookingByRef(@PathVariable String bookingRef) {
        return bookingRepository.findByBookingRef(bookingRef)
                .map(b -> {
                    enrichBookingWithBusDetails(b);
                    return ResponseEntity.ok(b);
                })
                .orElse(ResponseEntity.notFound().build());
    }



    @PutMapping("/ref/{bookingRef}/expire")

    public ResponseEntity<?> expireBookingByRef(@PathVariable String bookingRef) {

        boolean expired = expiryService.expireBookingByRef(bookingRef);

        return ResponseEntity.ok(Map.of(

                "bookingRef", bookingRef,

                "status", "EXPIRED",

                "expired", expired

        ));

    }



    @PutMapping("/{id}/confirm")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> confirmBooking(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        Optional<Booking> opt = bookingRepository.findByIdForUpdate(id);
        if (opt.isEmpty()) {
            opt = bookingRepository.findById(id);
        }

        return opt.map(bkg -> executeConfirmBooking(bkg, authHeader, internalToken, internalService))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/ref/{bookingRef}/confirm")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> confirmBookingByRef(
            @PathVariable String bookingRef,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        Optional<Booking> opt = bookingRepository.findByBookingRefForUpdate(bookingRef);
        if (opt.isEmpty()) {
            opt = bookingRepository.findByBookingRef(bookingRef);
        }

        return opt.map(bkg -> executeConfirmBooking(bkg, authHeader, internalToken, internalService))
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<?> executeConfirmBooking(
            Booking bkg, String authHeader, String internalToken, String internalService) {

        // 1. Idempotent check
        if ("CONFIRMED".equalsIgnoreCase(bkg.getStatus())) {
            return ResponseEntity.ok(Map.of("message", "Booking is already confirmed", "booking", bkg));
        }

        // 2. Terminal state check
        if ("CANCELLED".equalsIgnoreCase(bkg.getStatus()) || "EXPIRED".equalsIgnoreCase(bkg.getStatus())) {
            if ("EXPIRED".equalsIgnoreCase(bkg.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Cannot confirm an expired booking"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", "Cannot confirm a cancelled booking"));
        }

        // 3. Authoritative expiry check (persisted expiresAt deadline)
        if (bkg.getExpiresAt() != null && LocalDateTime.now().isAfter(bkg.getExpiresAt())) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class)
                    .warn("Booking {} rejected confirmation: expiresAt deadline has passed", bkg.getBookingRef());
            bkg.setStatus("EXPIRED");
            bookingRepository.save(bkg);
            releaseRoomsInHotelService(bkg);
            releaseSeatsInBusService(bkg);
            snapshotRepository.findByBookingId(bkg.getId()).ifPresent(s -> {
                s.setStatus("EXPIRED");
                if (s.getAllocations() != null) {
                    s.getAllocations().forEach(a -> a.setStatus("EXPIRED"));
                }
                snapshotRepository.save(s);
            });

            // Refund if payment was captured late
            boolean paymentCaptured = verifyPaymentStatusWithPaymentService(bkg.getBookingRef(), bkg.getTotalAmount());
            if (paymentCaptured) {
                processRefundInPaymentService(bkg.getBookingRef(), bkg.getTotalAmount());
            }
            return ResponseEntity.badRequest().body(Map.of("message", "Cannot confirm an expired booking"));
        }

        // 4. Authorization check: internal service or owning customer / admin
        boolean isInternal = securityService.authenticateInternalService(authHeader, internalToken, internalService);

        if (!isInternal && authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (securityService.validateToken(token)) {
                Long callerUserId = securityService.extractUserId(token);
                String callerRole = securityService.extractRole(token);
                boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);

                if (!isAdmin && callerUserId != null && bkg.getCustomerId() != null && !callerUserId.equals(bkg.getCustomerId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Access denied: Cannot confirm another user's booking"));
                }
            }
        }

        // 5. Authoritative server-side verification with Payment Service
        boolean paymentValid = verifyPaymentStatusWithPaymentService(bkg.getBookingRef(), bkg.getTotalAmount());
        if (!paymentValid && !isInternal) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Cannot confirm booking: Authoritative payment verification failed or payment is not captured"));
        }

        // 6. Attempt to confirm seats in bus-service
        boolean seatsConfirmed = confirmSeatsInBusService(bkg);
        if (!seatsConfirmed) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class)
                    .warn("Seats for booking {} are no longer available. Initiating automatic refund.", bkg.getBookingRef());
            processRefundInPaymentService(bkg.getBookingRef(), bkg.getTotalAmount());
            bkg.setStatus("EXPIRED");
            Booking saved = bookingRepository.save(bkg);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Cannot confirm booking: Seat hold has expired and seats have been allocated to another traveller. An automated refund has been initiated to your original payment method.",
                    "booking", saved
            ));
        }

        // 7. Attempt to confirm rooms in hotel-service (WP-04 & WP-HB-05 consolidated reservation)
        boolean roomsConfirmed = confirmRoomsInHotelService(bkg);
        if (!roomsConfirmed) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class)
                    .warn("Rooms for booking {} are no longer available or hold expired. Initiating automatic refund.", bkg.getBookingRef());
            processRefundInPaymentService(bkg.getBookingRef(), bkg.getTotalAmount());
            bkg.setStatus("EXPIRED");
            Booking saved = bookingRepository.save(bkg);
            snapshotRepository.findByBookingId(bkg.getId()).ifPresent(s -> {
                s.setStatus("EXPIRED");
                if (s.getAllocations() != null) {
                    s.getAllocations().forEach(a -> a.setStatus("EXPIRED"));
                }
                snapshotRepository.save(s);
            });
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Cannot confirm booking: Room hold has expired and room inventory is no longer available. An automated refund has been initiated to your original payment method.",
                    "booking", saved
            ));
        }

        // 8. Update relational snapshot status to CONFIRMED
        snapshotRepository.findByBookingId(bkg.getId()).ifPresent(s -> {
            s.setStatus("CONFIRMED");
            if (s.getAllocations() != null) {
                s.getAllocations().forEach(a -> a.setStatus("CONFIRMED"));
            }
            snapshotRepository.save(s);
        });

        Booking saved = stateTransitionService.confirmBooking(bkg);
        return ResponseEntity.ok(Map.of("message", "Booking confirmed", "booking", saved));
    }

    @GetMapping("/ref/{bookingRef}/snapshot")
    public ResponseEntity<?> getHotelReservationSnapshotByRef(@PathVariable String bookingRef) {
        return snapshotRepository.findByBookingRef(bookingRef)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/snapshot")
    public ResponseEntity<?> getHotelReservationSnapshotById(@PathVariable Long id) {
        return snapshotRepository.findByBookingId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }



    @PutMapping("/{id}/cancel")

    public ResponseEntity<?> cancelBooking(@PathVariable Long id,

                                           @RequestHeader(value = "Authorization", required = false) String authHeader,

                                           @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        return bookingRepository.findById(id).map(bkg -> {
            // Authorization check
            boolean isInternal = "payment-service".equalsIgnoreCase(internalService) || "traveller-service".equalsIgnoreCase(internalService);
            if (!isInternal && authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (securityService.validateToken(token)) {
                    Long callerUserId = securityService.extractUserId(token);
                    String callerRole = securityService.extractRole(token);
                    boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
                    if (!isAdmin && callerUserId != null && bkg.getCustomerId() != null && !callerUserId.equals(bkg.getCustomerId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("message", "Access denied: Cannot cancel another user's booking"));
                    }
                }
            }

            if ("CANCELLED".equalsIgnoreCase(bkg.getStatus()) || "EXPIRED".equalsIgnoreCase(bkg.getStatus())) {
                if ("EXPIRED".equalsIgnoreCase(bkg.getStatus())) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Booking has already expired and inventory has been released"));
                }
                if ("PROCESSED".equalsIgnoreCase(bkg.getRefundStatus())) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Booking is already cancelled and refund has already been processed (Refund ID: " + bkg.getRefundId() + ")"));
                }
                // Allow refund retry for cancelled bookings whose refund was pending or failed
                Map<String, Object> retryRefund = processRefundInPaymentService(bkg.getBookingRef(), bkg.getTotalAmount());
                if (retryRefund != null) {
                    String rfdStatus = retryRefund.get("refundStatus") != null ? retryRefund.get("refundStatus").toString()
                            : (retryRefund.get("status") != null ? retryRefund.get("status").toString() : "PROCESSED");
                    String rfdId = retryRefund.get("refundId") != null ? retryRefund.get("refundId").toString()
                            : (retryRefund.get("id") != null ? retryRefund.get("id").toString() : null);
                    Double rfdAmt = null;
                    Object amtObj = retryRefund.get("refundAmount") != null ? retryRefund.get("refundAmount") : retryRefund.get("amount");
                    if (amtObj != null) {
                        try {
                            rfdAmt = Double.valueOf(amtObj.toString());
                        } catch (NumberFormatException ignored) {}
                    }
                    bkg.setRefundStatus(rfdStatus);
                    bkg.setRefundId(rfdId);
                    bkg.setRefundAmount(rfdAmt != null ? rfdAmt : bkg.getTotalAmount());
                    bkg = bookingRepository.save(bkg);
                    return ResponseEntity.ok(Map.of(
                            "message", "Refund processed successfully via Razorpay",
                            "booking", bkg,
                            "refund", retryRefund
                    ));
                } else {
                    return ResponseEntity.badRequest().body(Map.of("message", "Booking is already cancelled"));
                }
            }

            String previousStatus = bkg.getStatus();
            boolean wasPaidBooking = "CONFIRMED".equalsIgnoreCase(previousStatus) || "COMPLETED".equalsIgnoreCase(previousStatus);

            boolean isHotel = "HOTEL".equalsIgnoreCase(bkg.getServiceType());
            Double targetRefundAmount = bkg.getTotalAmount();
            CancellationPolicyResult policyResult = null;

            if (isHotel) {
                policyResult = cancellationPolicyService.evaluateCancellationPolicy(bkg, LocalDateTime.now());
                targetRefundAmount = policyResult.getRefundAmount();
                bkg.setCancellationPolicyTier(policyResult.getPolicyTier());
                bkg.setCancellationFee(policyResult.getCancellationFee());
                bkg.setCancellationPolicySnapshot(cancellationPolicyService.buildPolicySnapshotJson(policyResult));
                bkg.setCancelledAt(LocalDateTime.now());
                bkg.setCancellationReason("Cancelled by customer");
            }

            Booking saved = stateTransitionService.cancelBooking(bkg, targetRefundAmount, "Cancelled by customer");

            // 1. Authoritatively release physical seats in bus-service or rooms in hotel-service
            List<String> releasedSeats = releaseSeatsInBusService(saved);
            releaseRoomsInHotelService(saved);

            // 2. Trigger automated gateway refund via payment-service if eligible for refund > 0
            Map<String, Object> refund = null;
            if (targetRefundAmount != null && targetRefundAmount > 0.0) {
                refund = processRefundInPaymentService(saved.getBookingRef(), targetRefundAmount);
            }

            if (refund != null) {
                String rfdStatus = refund.get("refundStatus") != null ? refund.get("refundStatus").toString()
                        : (refund.get("status") != null ? refund.get("status").toString() : "PROCESSED");
                String rfdId = refund.get("refundId") != null ? refund.get("refundId").toString()
                        : (refund.get("id") != null ? refund.get("id").toString() : null);
                Double rfdAmt = null;
                Object amtObj = refund.get("refundAmount") != null ? refund.get("refundAmount") : refund.get("amount");
                if (amtObj != null) {
                    try {
                        rfdAmt = Double.valueOf(amtObj.toString());
                    } catch (NumberFormatException ignored) {}
                }
                saved.setRefundStatus(rfdStatus);
                saved.setRefundId(rfdId);
                saved.setRefundAmount(rfdAmt != null ? rfdAmt : targetRefundAmount);
                saved = bookingRepository.save(saved);
            } else if (targetRefundAmount != null && targetRefundAmount > 0.0 && wasPaidBooking) {
                saved.setRefundStatus("FAILED_RETRYABLE");
                saved = bookingRepository.save(saved);
            } else if (isHotel && (targetRefundAmount == null || targetRefundAmount == 0.0)) {
                saved.setRefundStatus("NON_REFUNDABLE");
                saved.setRefundAmount(0.0);
                saved = bookingRepository.save(saved);
            } else {
                saved.setRefundStatus("NOT_APPLICABLE");
                saved = bookingRepository.save(saved);
            }

            Map<String, Object> response = new java.util.HashMap<>();

            response.put("message", "Booking cancelled successfully. Reserved inventory has been released.");

            response.put("booking", saved);

            response.put("releasedSeats", releasedSeats);

            if (policyResult != null) {
                response.put("cancellationPolicy", policyResult);
            }

            if (refund != null) {

                response.put("refund", refund);

            }

            return ResponseEntity.ok(response);

        }).orElse(ResponseEntity.notFound().build());

    }



    private boolean verifyPaymentStatusWithPaymentService(String bookingRef, Double expectedAmount) {

        if (bookingRef == null || bookingRef.isBlank()) {

            return false;

        }

        try {

            String url = paymentServiceUrl + "/api/payments/booking/" + bookingRef;

            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {

                String status = (String) resp.getBody().get("status");

                if ("SUCCESS".equalsIgnoreCase(status)) {

                    Object amtObj = resp.getBody().get("amount");

                    if (amtObj != null && expectedAmount != null) {

                        double paidAmt = Double.parseDouble(amtObj.toString());

                        if (Math.abs(paidAmt - expectedAmount) > 0.01) {

                            org.slf4j.LoggerFactory.getLogger(BookingController.class)

                                    .warn("Payment amount {} does not match booking amount {}", paidAmt, expectedAmount);

                            return false;

                        }

                    }

                    return true;

                }

            }

        } catch (Exception e) {

            org.slf4j.LoggerFactory.getLogger(BookingController.class)

                    .warn("Payment verification lookup failed for booking {}: {}", bookingRef, e.getMessage());

        }

        return false;

    }



    private List<String> extractSeatNumbers(String detailsJson) {

        List<String> seats = new ArrayList<>();

        if (detailsJson == null || detailsJson.isBlank()) {

            return seats;

        }

        Pattern pattern = Pattern.compile("Seats:\\s*([^|\\n]+)", Pattern.CASE_INSENSITIVE);

        Matcher matcher = pattern.matcher(detailsJson);

        if (matcher.find()) {

            String seatsStr = matcher.group(1).trim();

            for (String s : seatsStr.split(",")) {

                String clean = s.trim();

                if (!clean.isEmpty()) {

                    seats.add(clean);

                }

            }

        }

        return seats;

    }



    private boolean confirmSeatsInBusService(Booking booking) {

        if (!"BUS".equalsIgnoreCase(booking.getServiceType()) || booking.getServiceId() == null) {

            return true;

        }

        List<String> seats = extractSeatNumbers(booking.getDetailsJson());

        if (seats.isEmpty()) {

            return true;

        }

        try {

            String url = busServiceUrl + "/api/buses/schedules/" + booking.getServiceId() + "/confirm-seats?bookingRef=" + booking.getBookingRef();

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();

            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            headers.set("X-Internal-Service", "booking-service");

            org.springframework.http.HttpEntity<List<String>> entity = new org.springframework.http.HttpEntity<>(seats, headers);

            ResponseEntity<Map> resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map.class);

            boolean success = resp.getStatusCode().is2xxSuccessful();

            if (success) {

                org.slf4j.LoggerFactory.getLogger(BookingController.class).info("Confirmed seats {} for schedule {} via bus-service", seats, booking.getServiceId());

            }

            return success;

        } catch (Exception e) {

            org.slf4j.LoggerFactory.getLogger(BookingController.class).warn("Failed to confirm seats in bus-service: {}", e.getMessage());

            return false;

        }

    }



    private List<String> releaseSeatsInBusService(Booking booking) {

        if (!"BUS".equalsIgnoreCase(booking.getServiceType()) || booking.getServiceId() == null) {

            return Collections.emptyList();

        }

        List<String> seats = extractSeatNumbers(booking.getDetailsJson());

        if (seats.isEmpty()) {

            return Collections.emptyList();

        }

        try {

            String url = busServiceUrl + "/api/buses/schedules/" + booking.getServiceId() + "/release-seats?bookingRef=" + booking.getBookingRef();

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();

            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            headers.set("X-Internal-Service", "booking-service");

            org.springframework.http.HttpEntity<List<String>> entity = new org.springframework.http.HttpEntity<>(seats, headers);

            restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map.class);

            org.slf4j.LoggerFactory.getLogger(BookingController.class).info("Released seats {} for schedule {} via bus-service", seats, booking.getServiceId());

            return seats;

        } catch (Exception e) {

            org.slf4j.LoggerFactory.getLogger(BookingController.class).error("Failed to release seats in bus-service: {}", e.getMessage());

            return Collections.emptyList();

        }

    }

    private boolean confirmRoomsInHotelService(Booking booking) {
        if (!"HOTEL".equalsIgnoreCase(booking.getServiceType()) || booking.getServiceId() == null) {
            return true;
        }
        if (booking.getBookingRef() == null || booking.getBookingRef().isBlank()) {
            return true;
        }
        // Only date-wise bookings with an allocation in hotel-service require confirm-stay
        if (!booking.isDateWiseBooking()) {
            return true;
        }
        try {
            String url = hotelServiceUrl + "/api/hotels/rooms/confirm-stay?bookingRef=" + booking.getBookingRef();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            String internalToken = internalTokenService.generateInternalToken();
            headers.set("Authorization", "Bearer " + internalToken);
            headers.set("X-Internal-Token", internalToken);
            headers.set("X-Internal-Service", "booking-service");
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map.class);
            boolean success = resp.getStatusCode().is2xxSuccessful();
            if (success) {
                org.slf4j.LoggerFactory.getLogger(BookingController.class)
                        .info("Confirmed stay inventory for bookingRef {} via hotel-service", booking.getBookingRef());
            }
            return success;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class)
                    .warn("Stay allocation in hotel-service rejected confirmation for bookingRef {}: {}", booking.getBookingRef(), e.getMessage());
            return false;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class)
                    .warn("Failed to confirm rooms in hotel-service for bookingRef {}: {}", booking.getBookingRef(), e.getMessage());
            return false;
        }
    }

    private boolean releaseRoomsInHotelService(Booking booking) {
        if (!"HOTEL".equalsIgnoreCase(booking.getServiceType())) {
            return false;
        }
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            String internalToken = internalTokenService.generateInternalToken();
            headers.set("Authorization", "Bearer " + internalToken);
            headers.set("X-Internal-Token", internalToken);
            headers.set("X-Internal-Service", "booking-service");

            // 1. Authoritative date-wise release for all date-wise bookings (WP-03)
            if (booking.isDateWiseBooking()) {
                if (booking.getBookingRef() != null && !booking.getBookingRef().isBlank()) {
                    try {
                        String stayUrl = hotelServiceUrl + "/api/hotels/rooms/release-stay?bookingRef=" + booking.getBookingRef();
                        org.springframework.http.HttpEntity<Void> stayEntity = new org.springframework.http.HttpEntity<>(headers);
                        ResponseEntity<Map> stayResp = restTemplate.exchange(stayUrl, org.springframework.http.HttpMethod.POST, stayEntity, Map.class);
                        if (stayResp.getStatusCode().is2xxSuccessful()) {
                            snapshotRepository.findByBookingRef(booking.getBookingRef()).ifPresent(s -> {
                                s.setStatus("RELEASED");
                                if (s.getAllocations() != null) {
                                    s.getAllocations().forEach(a -> a.setStatus("RELEASED"));
                                }
                                snapshotRepository.save(s);
                            });
                            org.slf4j.LoggerFactory.getLogger(BookingController.class)
                                    .info("Authoritatively released stay inventory for bookingRef {} via hotel-service", booking.getBookingRef());
                            return true;
                        }
                    } catch (Exception e) {
                        org.slf4j.LoggerFactory.getLogger(BookingController.class)
                                .warn("Failed to release date-wise stay inventory for bookingRef {}: {}", booking.getBookingRef(), e.getMessage());
                    }
                }
                // Date-wise bookings must NEVER fall back to legacy count-based release
                return false;
            }

            // 2. Genuine legacy bookings fallback to count-based release
            if (booking.getServiceId() == null) {
                return false;
            }
            int count = 1;
            if (booking.getDetailsJson() != null) {
                Pattern pattern = Pattern.compile("Rooms?:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(booking.getDetailsJson());
                if (matcher.find()) {
                    count = Integer.parseInt(matcher.group(1));
                }
            }
            String url = hotelServiceUrl + "/api/hotels/rooms/" + booking.getServiceId() + "/release?count=" + count;
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map.class);
            boolean success = resp.getStatusCode().is2xxSuccessful();
            if (success) {
                org.slf4j.LoggerFactory.getLogger(BookingController.class)
                        .info("Released {} room(s) for legacy booking {} via hotel-service", count, booking.getBookingRef());
            }
            return success;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class)
                    .warn("Failed to release rooms in hotel-service: {}", e.getMessage());
            return false;
        }
    }

    private void resolveHotelProviderId(Booking booking) {
        try {
            String url = hotelServiceUrl + "/api/hotels/internal/" + booking.getServiceId();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            String token = internalTokenService.generateInternalToken();
            headers.set("Authorization", "Bearer " + token);
            headers.set("X-Internal-Token", token);
            headers.set("X-Internal-Service", "booking-service");
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object mgrIdObj = resp.getBody().get("managerId");
                if (mgrIdObj instanceof Number) {
                    booking.setProviderId(((Number) mgrIdObj).longValue());
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class)
                    .debug("Could not authoritatively resolve managerId for hotel {}: {}", booking.getServiceId(), e.getMessage());
        }
    }



    private Map<String, Object> processRefundInPaymentService(String bookingRef, Double totalAmount) {

        if (bookingRef == null || bookingRef.isBlank()) {

            return null;

        }

        try {

            String url = paymentServiceUrl + "/api/payments/refund/booking/" + bookingRef;

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();

            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            headers.set("X-Internal-Service", "booking-service");

            Map<String, Object> reqBody = new java.util.HashMap<>();

            if (totalAmount != null) {

                reqBody.put("amount", totalAmount);

            }

            reqBody.put("reason", "Cancelled by traveller or hold expired");

            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(reqBody, headers);

            org.springframework.http.ResponseEntity<Map> resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, entity, Map.class);

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {

                org.slf4j.LoggerFactory.getLogger(BookingController.class).info("Refund processed successfully for booking {}: {}", bookingRef, resp.getBody());

                return (Map<String, Object>) resp.getBody();

            }

        } catch (Exception e) {

            org.slf4j.LoggerFactory.getLogger(BookingController.class).warn("Payment refund not applicable or service call returned: {}", e.getMessage());

        }

        return null;

    }


    @PutMapping("/operator/schedules/{scheduleId}/cancel")
    public ResponseEntity<?> cancelSchedule(
            @PathVariable Long scheduleId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            ScheduleCancellationBatch batch = coordinatorService.cancelSchedule(scheduleId, authHeader);
            if ("PARTIALLY_FAILED".equalsIgnoreCase(batch.getBatchStatus())) {
                return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(batch);
            }
            return ResponseEntity.ok(batch);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() != null ? e.getReason() : e.getMessage()));
        }
    }

    @GetMapping("/operator/schedules/{scheduleId}/cancellation-status")
    public ResponseEntity<?> getCancellationStatus(
            @PathVariable Long scheduleId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            ScheduleCancellationBatch batch = coordinatorService.getCancellationStatus(scheduleId, authHeader);
            return ResponseEntity.ok(batch);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() != null ? e.getReason() : e.getMessage()));
        }
    }

    // =========================================================================
    // WP-04: Central Cancellation Policy Quotes & Operational Lifecycle (BD-04, BD-09)
    // =========================================================================

    @GetMapping("/{id}/cancellation-quote")
    public ResponseEntity<?> getCancellationQuoteById(@PathVariable Long id,
                                                      @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return bookingRepository.findById(id).map(bkg -> evaluateQuoteForBooking(bkg, authHeader))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/ref/{bookingRef}/cancellation-quote")
    public ResponseEntity<?> getCancellationQuoteByRef(@PathVariable String bookingRef,
                                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return bookingRepository.findByBookingRef(bookingRef).map(bkg -> evaluateQuoteForBooking(bkg, authHeader))
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<?> evaluateQuoteForBooking(Booking bkg, String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (securityService.validateToken(token)) {
                Long callerUserId = securityService.extractUserId(token);
                String callerRole = securityService.extractRole(token);
                boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
                boolean isProvider = "ROLE_HOTEL_MANAGER".equalsIgnoreCase(callerRole) && callerUserId != null && callerUserId.equals(bkg.getProviderId());
                boolean isCustomer = callerUserId != null && callerUserId.equals(bkg.getCustomerId());
                if (!isAdmin && !isProvider && !isCustomer) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied"));
                }
            }
        }
        if (!"HOTEL".equalsIgnoreCase(bkg.getServiceType())) {
            return ResponseEntity.ok(Map.of(
                    "bookingRef", bkg.getBookingRef(),
                    "serviceType", bkg.getServiceType(),
                    "totalAmount", bkg.getTotalAmount() != null ? bkg.getTotalAmount() : 0.0,
                    "refundPercentage", 100.0,
                    "refundAmount", bkg.getTotalAmount() != null ? bkg.getTotalAmount() : 0.0,
                    "cancellationFee", 0.0,
                    "description", "Standard full refund policy for bus bookings."
            ));
        }
        CancellationPolicyResult quote = cancellationPolicyService.evaluateCancellationPolicy(bkg, LocalDateTime.now());
        return ResponseEntity.ok(quote);
    }

    @PutMapping("/ref/{bookingRef}/check-in")
    public ResponseEntity<?> checkInGuestByRef(@PathVariable String bookingRef,
                                               @RequestHeader(value = "Authorization", required = false) String authHeader,
                                               @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
                                               @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        boolean isInternal = securityService.authenticateInternalService(authHeader, internalToken, internalService);
        Long callerUserId = null;
        String callerRole = null;
        if (!isInternal && authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (securityService.validateToken(token)) {
                callerUserId = securityService.extractUserId(token);
                callerRole = securityService.extractRole(token);
            }
        }
        try {
            BookingLifecycleOperationResponse resp = guestLifecycleService.checkInGuest(bookingRef, callerUserId, callerRole, isInternal);
            return ResponseEntity.ok(resp);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() != null ? e.getReason() : e.getMessage()));
        }
    }

    @PutMapping("/ref/{bookingRef}/check-out")
    public ResponseEntity<?> checkOutGuestByRef(@PathVariable String bookingRef,
                                                @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
                                                @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        boolean isInternal = securityService.authenticateInternalService(authHeader, internalToken, internalService);
        Long callerUserId = null;
        String callerRole = null;
        if (!isInternal && authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (securityService.validateToken(token)) {
                callerUserId = securityService.extractUserId(token);
                callerRole = securityService.extractRole(token);
            }
        }
        try {
            BookingLifecycleOperationResponse resp = guestLifecycleService.checkOutGuest(bookingRef, callerUserId, callerRole, isInternal);
            return ResponseEntity.ok(resp);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() != null ? e.getReason() : e.getMessage()));
        }
    }

    @PutMapping("/ref/{bookingRef}/no-show")
    public ResponseEntity<?> markNoShowByRef(@PathVariable String bookingRef,
                                             @RequestHeader(value = "Authorization", required = false) String authHeader,
                                             @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
                                             @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        boolean isInternal = securityService.authenticateInternalService(authHeader, internalToken, internalService);
        Long callerUserId = null;
        String callerRole = null;
        if (!isInternal && authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (securityService.validateToken(token)) {
                callerUserId = securityService.extractUserId(token);
                callerRole = securityService.extractRole(token);
            }
        }
        try {
            BookingLifecycleOperationResponse resp = guestLifecycleService.markNoShow(bookingRef, callerUserId, callerRole, isInternal);
            return ResponseEntity.ok(resp);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() != null ? e.getReason() : e.getMessage()));
        }
    }

    @PutMapping("/{id}/check-in")
    public ResponseEntity<?> checkInGuestById(@PathVariable Long id,
                                              @RequestHeader(value = "Authorization", required = false) String authHeader,
                                              @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
                                              @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        return bookingRepository.findById(id)
                .map(bkg -> checkInGuestByRef(bkg.getBookingRef(), authHeader, internalToken, internalService))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/check-out")
    public ResponseEntity<?> checkOutGuestById(@PathVariable Long id,
                                               @RequestHeader(value = "Authorization", required = false) String authHeader,
                                               @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
                                               @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        return bookingRepository.findById(id)
                .map(bkg -> checkOutGuestByRef(bkg.getBookingRef(), authHeader, internalToken, internalService))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/no-show")
    public ResponseEntity<?> markNoShowById(@PathVariable Long id,
                                            @RequestHeader(value = "Authorization", required = false) String authHeader,
                                            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
                                            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        return bookingRepository.findById(id)
                .map(bkg -> markNoShowByRef(bkg.getBookingRef(), authHeader, internalToken, internalService))
                .orElse(ResponseEntity.notFound().build());
    }
}
