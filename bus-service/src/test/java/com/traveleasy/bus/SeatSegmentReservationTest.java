package com.traveleasy.bus;

import com.traveleasy.bus.dto.ScheduleStopDto;
import com.traveleasy.bus.dto.SeatSegmentReservationDto;
import com.traveleasy.bus.dto.TripSeatLayoutResponse;
import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.SeatSegmentReservation;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.SeatSegmentReservationRepository;
import com.traveleasy.bus.service.RouteStopsService;
import com.traveleasy.bus.service.SeatHoldReconciliationService;
import com.traveleasy.bus.service.SeatLayoutService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.security.Key;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SeatSegmentReservationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private BusScheduleRepository scheduleRepository;

    @Autowired
    private SeatSegmentReservationRepository segmentReservationRepository;

    @Autowired
    private RouteStopsService routeStopsService;

    @Autowired
    private SeatLayoutService seatLayoutService;

    @Autowired
    private SeatHoldReconciliationService holdReconciliationService;

    private static final String SECRET = "TravelEasySuperSecretKeyForJWTSigning12345678901234567890";

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/buses";
    }

    @BeforeEach
    public void setup() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    private String createTestToken(Long userId, String role, String email) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .claim("userId", userId)
                .claim("role", role)
                .claim("fullName", "User " + userId)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private BusSchedule createTestScheduleWith4Stops(Long operatorId) {
        Bus bus = busRepository.save(Bus.builder()
                .busNumber("AP09-SEG-" + System.currentTimeMillis() % 10000)
                .busType("AC Sleeper (2+1)")
                .totalSeats(36)
                .operatorName("Orange Tours & Travels")
                .operatorId(operatorId)
                .status("ACTIVE")
                .busConfiguration("DOUBLE_DECK")
                .facilityType("AC")
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SLEEPER)
                .build());

        BusSchedule schedule = scheduleRepository.save(BusSchedule.builder()
                .busId(bus.getId())
                .operatorId(operatorId)
                .operatorName(bus.getOperatorName())
                .busNumber(bus.getBusNumber())
                .busType(bus.getBusType())
                .origin("Hyderabad")
                .destination("Bengaluru")
                .travelDate(LocalDate.now().plusDays(3))
                .departureTime("20:00")
                .arrivalTime("06:00")
                .fare(1200.0)
                .availableSeats(36)
                .status("SCHEDULED")
                .build());

        // Generate physical seats in bus_seats from template
        seatLayoutService.generateTripSeats(schedule, bus);

        // Configure 4 stops: Hyd (0) -> Kurnool (1) -> Anantapur (2) -> Bengaluru (3)
        List<ScheduleStopDto> stops = List.of(
                ScheduleStopDto.builder()
                        .stopOrder(0)
                        .stopName("Hyderabad (MGBS)")
                        .stopType("ORIGIN")
                        .departureTime("20:00")
                        .distanceKm(0.0)
                        .consecutiveFare(300.0)
                        .build(),
                ScheduleStopDto.builder()
                        .stopOrder(1)
                        .stopName("Kurnool")
                        .stopType("BOARDING_AND_DROPPING")
                        .arrivalTime("23:30")
                        .departureTime("23:45")
                        .distanceKm(210.0)
                        .consecutiveFare(400.0)
                        .build(),
                ScheduleStopDto.builder()
                        .stopOrder(2)
                        .stopName("Anantapur")
                        .stopType("BOARDING_AND_DROPPING")
                        .arrivalTime("02:30")
                        .departureTime("02:40")
                        .distanceKm(360.0)
                        .consecutiveFare(500.0)
                        .build(),
                ScheduleStopDto.builder()
                        .stopOrder(3)
                        .stopName("Bengaluru (Majestic)")
                        .stopType("DESTINATION")
                        .arrivalTime("06:00")
                        .distanceKm(570.0)
                        .consecutiveFare(0.0)
                        .build()
        );

        routeStopsService.configureRouteStops(schedule.getId(), stops);
        return scheduleRepository.findById(schedule.getId()).orElse(schedule);
    }

    @Test
    @DisplayName("Unit 4.3.1: Non-overlapping segment bookings on the SAME physical seat succeed")
    public void testReserveNonOverlappingSegments_SameSeat_Success() {
        Long operatorId = 801L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);

        // 1. Passenger A books seat "L1A" for Stop 0 -> Stop 1 (segment 0)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> respA = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/book-seats?bookingRef=BK-PASS-A&fromOrder=0&toOrder=1",
                HttpMethod.PUT,
                new HttpEntity<>(List.of("L1A"), headers),
                Map.class
        );
        assertEquals(HttpStatus.OK, respA.getStatusCode());

        // 2. Passenger B books SAME seat "L1A" for Stop 1 -> Stop 3 (segments 1 and 2)
        ResponseEntity<Map> respB = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/book-seats?bookingRef=BK-PASS-B&fromOrder=1&toOrder=3",
                HttpMethod.PUT,
                new HttpEntity<>(List.of("L1A"), headers),
                Map.class
        );
        assertEquals(HttpStatus.OK, respB.getStatusCode());

        // Verify segment reservations in DB:
        // Seat L1A has 3 total segment reservations:
        // - seg 0 -> BK-PASS-A
        // - seg 1 -> BK-PASS-B
        // - seg 2 -> BK-PASS-B
        List<SeatSegmentReservation> reservations = segmentReservationRepository
                .findByScheduleIdOrderBySeatNumberAscSegmentIndexAsc(schedule.getId());

        List<SeatSegmentReservation> l1Res = reservations.stream()
                .filter(r -> "L1A".equals(r.getSeatNumber()))
                .toList();

        assertEquals(3, l1Res.size());
        assertEquals(0, l1Res.get(0).getSegmentIndex());
        assertEquals("BK-PASS-A", l1Res.get(0).getBookingRef());

        assertEquals(1, l1Res.get(1).getSegmentIndex());
        assertEquals("BK-PASS-B", l1Res.get(1).getBookingRef());

        assertEquals(2, l1Res.get(2).getSegmentIndex());
        assertEquals("BK-PASS-B", l1Res.get(2).getBookingRef());
    }

    @Test
    @DisplayName("Unit 4.3.2: Overlapping segment booking on an occupied seat is rejected with 409 Conflict")
    public void testReserveOverlappingSegments_SameSeat_RejectedConflict() {
        Long operatorId = 802L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 1. Passenger A books "L2A" for Stop 0 -> Stop 2 (segments 0 and 1)
        ResponseEntity<Map> respA = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/book-seats?bookingRef=BK-PASS-A&fromOrder=0&toOrder=2",
                HttpMethod.PUT,
                new HttpEntity<>(List.of("L2A"), headers),
                Map.class
        );
        assertEquals(HttpStatus.OK, respA.getStatusCode());

        // 2. Passenger C attempts to book "L2A" for Stop 1 -> Stop 3 (segments 1 and 2) -> Overlaps at segment 1!
        ResponseEntity<Map> respC = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/book-seats?bookingRef=BK-PASS-C&fromOrder=1&toOrder=3",
                HttpMethod.PUT,
                new HttpEntity<>(List.of("L2A"), headers),
                Map.class
        );
        assertEquals(HttpStatus.CONFLICT, respC.getStatusCode());
        assertNotNull(respC.getBody());
    }

    @Test
    @DisplayName("Unit 4.3.3: Confirming seats permanently commits segment reservations to status BOOKED")
    public void testConfirmSeats_PermanentlyCommitsSegmentReservations() {
        Long operatorId = 803L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);
        String bookingRef = "BK-CONFIRM-TEST";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 1. Hold seat "L3A" for Stop 0 -> Stop 2
        ResponseEntity<Map> bookResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/book-seats?bookingRef=" + bookingRef + "&fromOrder=0&toOrder=2",
                HttpMethod.PUT,
                new HttpEntity<>(List.of("L3A"), headers),
                Map.class
        );
        assertEquals(HttpStatus.OK, bookResp.getStatusCode());

        // Check held status before confirmation
        List<SeatSegmentReservation> heldRes = segmentReservationRepository.findByScheduleIdAndBookingRef(schedule.getId(), bookingRef);
        assertEquals(2, heldRes.size());
        assertEquals("HELD", heldRes.get(0).getStatus());
        assertNotNull(heldRes.get(0).getHoldExpiresAt());

        // 2. Confirm seats
        ResponseEntity<Map> confResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/confirm-seats?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(List.of("L3A"), headers),
                Map.class
        );
        assertEquals(HttpStatus.OK, confResp.getStatusCode());

        // 3. Verify confirmed status in DB
        List<SeatSegmentReservation> confirmedRes = segmentReservationRepository.findByScheduleIdAndBookingRef(schedule.getId(), bookingRef);
        assertEquals(2, confirmedRes.size());
        for (SeatSegmentReservation r : confirmedRes) {
            assertEquals("BOOKED", r.getStatus());
            assertNull(r.getHoldExpiresAt());
        }
    }

    @Test
    @DisplayName("Unit 4.3.4: Releasing seats purges segment reservations and frees seat for subsequent booking")
    public void testReleaseSeats_FreesSegmentsForSubsequentBooking() {
        Long operatorId = 804L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);
        String bookingRef = "BK-RELEASE-TEST";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 1. Hold seat "L4A" for Stop 0 -> Stop 3 (full trip)
        ResponseEntity<Map> bookResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/book-seats?bookingRef=" + bookingRef + "&fromOrder=0&toOrder=3",
                HttpMethod.PUT,
                new HttpEntity<>(List.of("L4A"), headers),
                Map.class
        );
        assertEquals(HttpStatus.OK, bookResp.getStatusCode());

        // Verify another booking on "L4A" is blocked
        ResponseEntity<Map> blockedResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/book-seats?bookingRef=BK-OTHER&fromOrder=0&toOrder=1",
                HttpMethod.PUT,
                new HttpEntity<>(List.of("L4A"), headers),
                Map.class
        );
        assertEquals(HttpStatus.CONFLICT, blockedResp.getStatusCode());

        // 2. Release seat "L4A" via bookingRef
        ResponseEntity<Map> releaseResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/release-seats?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(headers),
                Map.class
        );
        assertEquals(HttpStatus.OK, releaseResp.getStatusCode());

        // 3. Now subsequent booking for "L4A" succeeds!
        ResponseEntity<Map> successResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/book-seats?bookingRef=BK-SUCCESS&fromOrder=0&toOrder=1",
                HttpMethod.PUT,
                new HttpEntity<>(List.of("L4A"), headers),
                Map.class
        );
        assertEquals(HttpStatus.OK, successResp.getStatusCode());
    }

    @Test
    @DisplayName("Unit 4.3.5: Layout endpoint dynamically reflects segment-specific availability")
    public void testSegmentAwareLayout_ReflectsDynamicAvailability() {
        Long operatorId = 805L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Book "L5A" only for Stop 0 -> Stop 1 (Hyd -> Kurnool)
        ResponseEntity<Map> bookResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/book-seats?bookingRef=BK-HYD-KRNL&fromOrder=0&toOrder=1",
                HttpMethod.PUT,
                new HttpEntity<>(List.of("L5A"), headers),
                Map.class
        );
        assertEquals(HttpStatus.OK, bookResp.getStatusCode());

        // Query layout for Hyd -> Kurnool (0 -> 1)
        ResponseEntity<TripSeatLayoutResponse> layoutSeg1 = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/layout?fromOrder=0&toOrder=1",
                TripSeatLayoutResponse.class
        );
        assertEquals(HttpStatus.OK, layoutSeg1.getStatusCode());
        assertNotNull(layoutSeg1.getBody());
        assertEquals(300.0, layoutSeg1.getBody().getBaseFare()); // Segment fare 300.0

        // Find seat L5A in layoutSeg1: MUST BE BOOKED
        String statusSeg1 = layoutSeg1.getBody().getDecks().get(0).getPositions().stream()
                .filter(p -> "L5A".equals(p.getSeatNumber()))
                .map(p -> p.getStatus())
                .findFirst().orElseThrow();
        assertEquals("BOOKED", statusSeg1);

        // Query layout for Kurnool -> Bengaluru (1 -> 3)
        ResponseEntity<TripSeatLayoutResponse> layoutSeg23 = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/layout?fromOrder=1&toOrder=3",
                TripSeatLayoutResponse.class
        );
        assertEquals(HttpStatus.OK, layoutSeg23.getStatusCode());
        assertNotNull(layoutSeg23.getBody());
        assertEquals(900.0, layoutSeg23.getBody().getBaseFare()); // Kurnool -> Blr fare: 400 + 500 = 900.0

        // Find seat L5A in layoutSeg23: MUST BE AVAILABLE!
        String statusSeg23 = layoutSeg23.getBody().getDecks().get(0).getPositions().stream()
                .filter(p -> "L5A".equals(p.getSeatNumber()))
                .map(p -> p.getStatus())
                .findFirst().orElseThrow();
        assertEquals("AVAILABLE", statusSeg23);
    }

    @Test
    @DisplayName("Unit 4.3.6: Schedule cancellation release-all-seats purges all segment reservations")
    public void testScheduleCancellation_PurgesAllSegmentReservations() {
        Long operatorId = 806L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "operator806@travel.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // Hold multiple seats on different segments
        ResponseEntity<Map> bookResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/book-seats?bookingRef=BK-1&fromOrder=0&toOrder=2",
                HttpMethod.PUT,
                new HttpEntity<>(List.of("L1A", "L2A"), headers),
                Map.class
        );
        assertEquals(HttpStatus.OK, bookResp.getStatusCode());

        // Verify segment reservations exist
        List<SeatSegmentReservation> beforeCancel = segmentReservationRepository.findByScheduleIdOrderBySeatNumberAscSegmentIndexAsc(schedule.getId());
        assertEquals(4, beforeCancel.size());

        // Cancel and release all seats
        ResponseEntity<Map> releaseAllResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/release-all-seats",
                HttpMethod.PUT,
                new HttpEntity<>(headers),
                Map.class
        );
        assertEquals(HttpStatus.OK, releaseAllResp.getStatusCode());

        // Verify segment reservations table is completely empty for this schedule
        List<SeatSegmentReservation> afterCancel = segmentReservationRepository.findByScheduleIdOrderBySeatNumberAscSegmentIndexAsc(schedule.getId());
        assertTrue(afterCancel.isEmpty());
    }

    @Test
    @DisplayName("Unit 4.3.7: Hold reaper automatically reaps expired HELD segment reservations")
    public void testHoldReconciliation_ReapsExpiredSegmentHolds() {
        Long operatorId = 807L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);

        // Manually insert an expired segment reservation
        SeatSegmentReservation expired = segmentReservationRepository.save(SeatSegmentReservation.builder()
                .scheduleId(schedule.getId())
                .seatNumber("L6A")
                .segmentIndex(0)
                .fromStopOrder(0)
                .toStopOrder(1)
                .bookingRef("BK-EXPIRED-SEG")
                .status("HELD")
                .holdExpiresAt(LocalDateTime.now().minusMinutes(5)) // Expired 5 mins ago
                .createdAt(LocalDateTime.now().minusMinutes(20))
                .updatedAt(LocalDateTime.now().minusMinutes(20))
                .build());

        assertNotNull(expired.getId());

        // Run reconciliation
        holdReconciliationService.reconcileExpiredHolds();

        // Verify expired segment reservation was deleted from database
        assertTrue(segmentReservationRepository.findById(expired.getId()).isEmpty());
    }

    @Test
    @DisplayName("Unit 4.3.8: Attempting to reserve seats on a CANCELLED schedule is rejected with 409 Conflict")
    public void testReserveSeats_CancelledSchedule_RejectedConflict() {
        Long operatorId = 808L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);
        schedule.setStatus("CANCELLED");
        scheduleRepository.save(schedule);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> resp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/book-seats?bookingRef=BK-CANCELLED&fromOrder=0&toOrder=1",
                HttpMethod.PUT,
                new HttpEntity<>(List.of("L1A"), headers),
                Map.class
        );
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
    }
}
