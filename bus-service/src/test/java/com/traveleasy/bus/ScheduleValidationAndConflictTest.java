package com.traveleasy.bus;

import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.SeatRepository;
import com.traveleasy.bus.service.ScheduleService;
import com.traveleasy.bus.service.SeatLayoutService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.security.Key;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ScheduleValidationAndConflictTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private BusScheduleRepository scheduleRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatLayoutService seatLayoutService;

    @Autowired
    private ScheduleService scheduleService;

    private static final String SECRET = "TravelEasySuperSecretKeyForJWTSigning12345678901234567890";
    private static final ZoneId OPERATIONAL_ZONE = ZoneId.of("Asia/Kolkata");

    private Clock originalClock;

    @BeforeEach
    public void setup() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        originalClock = scheduleService.getClock();
        // Default fixed clock at 2026-11-01 10:00:00 AM IST for deterministic time tests
        Instant fixedInstant = LocalDateTime.of(2026, 11, 1, 10, 0, 0)
                .atZone(OPERATIONAL_ZONE).toInstant();
        scheduleService.setClock(Clock.fixed(fixedInstant, OPERATIONAL_ZONE));
        scheduleService.setTurnaroundBufferMinutes(60);
    }

    @AfterEach
    public void tearDown() {
        if (originalClock != null) {
            scheduleService.setClock(originalClock);
        }
        scheduleService.setTurnaroundBufferMinutes(60);
    }

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/buses";
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

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return headers;
    }

    private Bus createAndSaveBus(Long operatorId, String status) {
        Bus bus = Bus.builder()
                .operatorId(operatorId)
                .operatorName("Operator " + operatorId)
                .busNumber("KA-01-SCHED-" + (System.currentTimeMillis() % 1000000) + "-" + UUID.randomUUID().toString().substring(0, 4))
                .busType("AC Sleeper")
                .facilityType("AC")
                .busConfiguration("SINGLE_DECK")
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER)
                .status(status != null ? status : "ACTIVE")
                .build();
        seatLayoutService.validateAndAssignTemplate(bus, bus.getTemplateId(), bus.getBusConfiguration(), bus.getFacilityType());
        return busRepository.save(bus);
    }

    // ==================== 1. REQUIREMENT A: BUS OVERLAP & TURNAROUND BUFFER TESTS ====================

    @Test
    @DisplayName("Req A.1: Schedule creation on bus with identical time rejected with 409 Conflict")
    public void testIdenticalTripConflict_RejectedWith409() {
        Long operatorId = 7001L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7001@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 5);

        BusSchedule trip1 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Chennai")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("14:00")
                .fare(500.0)
                .build();

        ResponseEntity<BusSchedule> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip1, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        // Attempt identical trip on the same bus
        BusSchedule trip2 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("14:00")
                .fare(600.0)
                .build();

        ResponseEntity<Map> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip2, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.CONFLICT, res2.getStatusCode());
        assertTrue(res2.getBody().get("message").toString().contains("already scheduled"));
    }

    @Test
    @DisplayName("Req A.2: Overlapping operating intervals during active trip rejected with 409 Conflict")
    public void testIntervalOverlapDuringTrip_RejectedWith409() {
        Long operatorId = 7002L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7002@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 5);

        BusSchedule trip1 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Chennai")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("16:00")
                .fare(500.0)
                .build();

        ResponseEntity<BusSchedule> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip1, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        // Trip 2 departs at 12:00, while Trip 1 is still en route
        BusSchedule trip2 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Chennai")
                .destination("Coimbatore")
                .travelDate(travelDate)
                .departureTime("12:00")
                .arrivalTime("18:00")
                .fare(450.0)
                .build();

        ResponseEntity<Map> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip2, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.CONFLICT, res2.getStatusCode());
        assertTrue(res2.getBody().get("message").toString().contains("already scheduled"));
    }

    @Test
    @DisplayName("Req A.3: Trip departing within 60-min turnaround buffer rejected with 409 Conflict")
    public void testTurnaroundBufferViolation_RejectedWith409() {
        Long operatorId = 7003L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7003@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 6);

        // Trip 1 arrives at 14:00. Turnaround buffer is 60 min -> bus available at 15:00
        BusSchedule trip1 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Mysuru")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("14:00")
                .fare(300.0)
                .build();

        ResponseEntity<BusSchedule> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip1, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        // Trip 2 departs at 14:45 (45 mins after arrival, violating 60-min buffer)
        BusSchedule trip2 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Mysuru")
                .destination("Bengaluru")
                .travelDate(travelDate)
                .departureTime("14:45")
                .arrivalTime("18:30")
                .fare(300.0)
                .build();

        ResponseEntity<Map> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip2, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.CONFLICT, res2.getStatusCode());
        assertTrue(res2.getBody().get("message").toString().contains("60 min turnaround buffer"));
    }

    @Test
    @DisplayName("Req A.4: Trip departing exactly at 60-min turnaround boundary accepted with 201 Created")
    public void testTurnaroundBufferTouchingBoundary_AllowedWith201() {
        Long operatorId = 7004L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7004@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 7);

        // Trip 1 arrives at 14:00. 60-min buffer ends at 15:00
        BusSchedule trip1 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Mysuru")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("14:00")
                .fare(300.0)
                .build();

        ResponseEntity<BusSchedule> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip1, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        // Trip 2 departs at exactly 15:00 (touching boundary)
        BusSchedule trip2 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Mysuru")
                .destination("Bengaluru")
                .travelDate(travelDate)
                .departureTime("15:00")
                .arrivalTime("19:00")
                .fare(300.0)
                .build();

        ResponseEntity<BusSchedule> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip2, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res2.getStatusCode());
        assertNotNull(res2.getBody());
        assertEquals("15:00", res2.getBody().getDepartureTime());
    }

    @Test
    @DisplayName("Req A.5: Preceding proposed trip whose buffer overlaps existing trip rejected with 409")
    public void testPrecedingTripTurnaroundBufferOverlap_RejectedWith409() {
        Long operatorId = 7005L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7005@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 8);

        // Existing trip departs at 10:00
        BusSchedule trip1 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Chennai")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("14:00")
                .fare(500.0)
                .build();

        ResponseEntity<BusSchedule> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip1, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        // Preceding trip arrives at 09:30. With 60m buffer, bus needs until 10:30, violating trip1's 10:00 departure
        BusSchedule tripPreceding = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Tumkur")
                .destination("Bengaluru")
                .travelDate(travelDate)
                .departureTime("07:00")
                .arrivalTime("09:30")
                .fare(150.0)
                .build();

        ResponseEntity<Map> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(tripPreceding, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.CONFLICT, res2.getStatusCode());
        assertTrue(res2.getBody().get("message").toString().contains("already scheduled"));
    }

    @Test
    @DisplayName("Req A.6: Cancelled schedules are ignored during conflict checks")
    public void testCancelledScheduleIgnoredInConflictCheck_AllowedWith201() {
        Long operatorId = 7006L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7006@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 9);

        // Trip 1 created
        BusSchedule trip1 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Chennai")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("14:00")
                .fare(500.0)
                .build();

        ResponseEntity<BusSchedule> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip1, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());
        Long schedId1 = res1.getBody().getId();

        // Mark Trip 1 as CANCELLED directly in repository
        BusSchedule saved1 = scheduleRepository.findById(schedId1).orElseThrow();
        saved1.setStatus("CANCELLED");
        scheduleRepository.save(saved1);

        // Schedule new trip at the exact same time window on the same bus
        BusSchedule trip2 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("14:00")
                .fare(600.0)
                .build();

        ResponseEntity<BusSchedule> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip2, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res2.getStatusCode());
        assertEquals("SCHEDULED", res2.getBody().getStatus());
    }

    @Test
    @DisplayName("Req A.7: Same time window on different buses creates successfully")
    public void testDifferentBuses_NoConflictWith201() {
        Long operatorId = 7007L;
        Bus bus1 = createAndSaveBus(operatorId, "ACTIVE");
        Bus bus2 = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7007@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 10);

        BusSchedule tripBus1 = BusSchedule.builder()
                .busId(bus1.getId())
                .origin("Bengaluru")
                .destination("Goa")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("18:00")
                .fare(800.0)
                .build();

        ResponseEntity<BusSchedule> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(tripBus1, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        BusSchedule tripBus2 = BusSchedule.builder()
                .busId(bus2.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("18:00")
                .fare(850.0)
                .build();

        ResponseEntity<BusSchedule> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(tripBus2, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res2.getStatusCode());
    }

    // ==================== 2. REQUIREMENT B: DATE-TIME & FARE VALIDATION TESTS ====================

    @Test
    @DisplayName("Req B.1: Past departure date-time rejected with 400 Bad Request")
    public void testPastDepartureDateTime_RejectedWith400() {
        Long operatorId = 7008L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7008@traveleasy.com");

        // Fixed clock is 2026-11-01 10:00 AM.
        // Test departure yesterday: 2026-10-31
        BusSchedule pastTrip = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Chennai")
                .travelDate(LocalDate.of(2026, 10, 31))
                .departureTime("10:00")
                .arrivalTime("14:00")
                .fare(500.0)
                .build();

        ResponseEntity<Map> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(pastTrip, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res1.getStatusCode());
        assertTrue(res1.getBody().get("message").toString().contains("cannot be in the past"));

        // Test same day earlier time: 2026-11-01 at 09:30 (current is 10:00)
        BusSchedule sameDayPastTrip = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Chennai")
                .travelDate(LocalDate.of(2026, 11, 1))
                .departureTime("09:30")
                .arrivalTime("13:30")
                .fare(500.0)
                .build();

        ResponseEntity<Map> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(sameDayPastTrip, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res2.getStatusCode());
        assertTrue(res2.getBody().get("message").toString().contains("cannot be in the past"));
    }

    @Test
    @DisplayName("Req B.2: Same-day future departure accepted with 201 Created")
    public void testSameDayFutureDeparture_AllowedWith201() {
        Long operatorId = 7009L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7009@traveleasy.com");

        // Current clock is 2026-11-01 10:00 AM. Departure at 11:30 AM is allowed
        BusSchedule trip = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Mysuru")
                .travelDate(LocalDate.of(2026, 11, 1))
                .departureTime("11:30")
                .arrivalTime("14:30")
                .fare(350.0)
                .build();

        ResponseEntity<BusSchedule> res = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody().getDepartureDateTime());
        assertEquals(LocalDateTime.of(2026, 11, 1, 11, 30), res.getBody().getDepartureDateTime());
    }

    @Test
    @DisplayName("Req B.3: Malformed time string formats rejected with 400 Bad Request")
    public void testMalformedTimeFormats_RejectedWith400() {
        Long operatorId = 7010L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7010@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 15);

        // Malformed departureTime: "25:30"
        BusSchedule trip1 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("CityA")
                .destination("CityB")
                .travelDate(travelDate)
                .departureTime("25:30")
                .arrivalTime("14:00")
                .fare(400.0)
                .build();
        ResponseEntity<Map> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip1, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res1.getStatusCode());
        assertTrue(res1.getBody().get("message").toString().contains("Invalid time format for departureTime"));

        // Malformed arrivalTime: "10:75"
        BusSchedule trip2 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("CityA")
                .destination("CityB")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("10:75")
                .fare(400.0)
                .build();
        ResponseEntity<Map> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip2, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res2.getStatusCode());
        assertTrue(res2.getBody().get("message").toString().contains("Invalid time format for arrivalTime"));
    }

    @Test
    @DisplayName("Req B.4: Missing travelDate rejected with 400 Bad Request")
    public void testMissingTravelDate_RejectedWith400() {
        Long operatorId = 7011L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7011@traveleasy.com");

        BusSchedule trip = BusSchedule.builder()
                .busId(bus.getId())
                .origin("CityA")
                .destination("CityB")
                .travelDate(null)
                .departureTime("10:00")
                .arrivalTime("14:00")
                .fare(400.0)
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().get("message").toString().contains("Travel date is required"));
    }

    @Test
    @DisplayName("Req B.5: Non-positive fare rejected with 400 Bad Request")
    public void testZeroOrNegativeFare_RejectedWith400() {
        Long operatorId = 7012L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7012@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 15);

        // Zero fare
        BusSchedule tripZero = BusSchedule.builder()
                .busId(bus.getId())
                .origin("CityA")
                .destination("CityB")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("14:00")
                .fare(0.0)
                .build();

        ResponseEntity<Map> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(tripZero, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res1.getStatusCode());
        assertTrue(res1.getBody().get("message").toString().contains("Fare must be greater than zero"));

        // Negative fare
        BusSchedule tripNeg = BusSchedule.builder()
                .busId(bus.getId())
                .origin("CityA")
                .destination("CityB")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("14:00")
                .fare(-150.0)
                .build();

        ResponseEntity<Map> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(tripNeg, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res2.getStatusCode());
        assertTrue(res2.getBody().get("message").toString().contains("Fare must be greater than zero"));
    }

    // ==================== 3. REQUIREMENT C: CROSS-MIDNIGHT SUPPORT TESTS ====================

    @Test
    @DisplayName("Req C.1: Cross-midnight trip automatically sets arrivalDate to next day with valid timestamps")
    public void testCrossMidnightTripCreation_SetsNextDayArrival_Success() {
        Long operatorId = 7013L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7013@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 20);

        // Departure 22:30, Arrival 06:15 next morning
        BusSchedule trip = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(travelDate)
                .departureTime("22:30")
                .arrivalTime("06:15")
                .fare(1100.0)
                .build();

        ResponseEntity<BusSchedule> res = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res.getStatusCode());

        BusSchedule saved = res.getBody();
        assertNotNull(saved);
        assertEquals(LocalDate.of(2026, 11, 21), saved.getArrivalDate());
        assertEquals(LocalDateTime.of(2026, 11, 20, 22, 30), saved.getDepartureDateTime());
        assertEquals(LocalDateTime.of(2026, 11, 21, 6, 15), saved.getArrivalDateTime());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("Req C.2: Cross-midnight trip interval overlap detection across date boundaries")
    public void testCrossMidnightTripOverlap_RejectedWith409() {
        Long operatorId = 7014L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7014@traveleasy.com");

        // Trip 1 departs Nov 20 at 22:00, arrives Nov 21 at 06:00.
        // Turnaround buffer (60 min) means bus is busy until Nov 21 07:00!
        BusSchedule trip1 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.of(2026, 11, 20))
                .departureTime("22:00")
                .arrivalTime("06:00")
                .fare(1000.0)
                .build();

        ResponseEntity<BusSchedule> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip1, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        // Trip 2 proposed on Nov 21 departing at 06:30 (violates Nov 21 07:00 buffer)
        BusSchedule trip2 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Hyderabad")
                .destination("Vijayawada")
                .travelDate(LocalDate.of(2026, 11, 21))
                .departureTime("06:30")
                .arrivalTime("12:00")
                .fare(600.0)
                .build();

        ResponseEntity<Map> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip2, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.CONFLICT, res2.getStatusCode());
        assertTrue(res2.getBody().get("message").toString().contains("already scheduled"));

        // Trip 3 proposed on Nov 21 departing at 07:00 (exact touching boundary, valid!)
        BusSchedule trip3 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Hyderabad")
                .destination("Vijayawada")
                .travelDate(LocalDate.of(2026, 11, 21))
                .departureTime("07:00")
                .arrivalTime("12:00")
                .fare(600.0)
                .build();

        ResponseEntity<BusSchedule> res3 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip3, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res3.getStatusCode());
    }

    // ==================== 4. REQUIREMENT D: CONCURRENCY PROTECTION TESTS ====================

    @Test
    @DisplayName("Req D.1: Concurrent conflicting schedule requests on same bus are serialized; only one succeeds")
    public void testConcurrentConflictingScheduleCreation_OnlyOneSucceeds() throws Exception {
        Long operatorId = 7015L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7015@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 25);
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await(); // Synchronize thread starts

                    BusSchedule trip = BusSchedule.builder()
                            .busId(bus.getId())
                            .origin("City" + index)
                            .destination("Destination" + index)
                            .travelDate(travelDate)
                            .departureTime("10:00")
                            .arrivalTime("15:00")
                            .fare(500.0 + index * 10)
                            .build();

                    ResponseEntity<Map> res = restTemplate.postForEntity(
                            getBaseUrl() + "/schedules", new HttpEntity<>(trip, authHeaders(token)), Map.class);

                    if (res.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else if (res.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignored
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release both threads simultaneously

        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly one request must succeed, and the other must be rejected with 409 Conflict
        assertEquals(1, successCount.get(), "Exactly one concurrent schedule creation should succeed");
        assertEquals(1, conflictCount.get(), "Conflicting concurrent schedule creation must be rejected with 409");

        // Verify database state: bus has exactly 1 schedule for that window
        List<BusSchedule> activeSchedules = scheduleRepository.findActiveSchedulesByBusId(bus.getId());
        assertEquals(1, activeSchedules.size());
    }

    // ==================== 5. REQUIREMENT E: ATOMIC TRANSACTION ROLLBACK TESTS ====================

    @Test
    @DisplayName("Req E.1: Conflict failure rolls back transaction atomically; zero orphan records created")
    public void testAtomicRollbackOnConflict_LeavesZeroOrphanRecords() {
        Long operatorId = 7016L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7016@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 28);

        // First trip succeeds
        BusSchedule trip1 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("OriginA")
                .destination("DestA")
                .travelDate(travelDate)
                .departureTime("08:00")
                .arrivalTime("12:00")
                .fare(400.0)
                .build();

        ResponseEntity<BusSchedule> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(trip1, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        long initialScheduleCount = scheduleRepository.count();
        long initialSeatCount = seatRepository.count();

        // Second trip conflicts
        BusSchedule tripConflict = BusSchedule.builder()
                .busId(bus.getId())
                .origin("OriginB")
                .destination("DestB")
                .travelDate(travelDate)
                .departureTime("10:00")
                .arrivalTime("14:00")
                .fare(400.0)
                .build();

        ResponseEntity<Map> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(tripConflict, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.CONFLICT, res2.getStatusCode());

        // Assert no new schedule or seat records were persisted
        assertEquals(initialScheduleCount, scheduleRepository.count(), "Schedule count must not increase after rollback");
        assertEquals(initialSeatCount, seatRepository.count(), "Seat count must not increase after rollback");
    }

    // ==================== 6. VERIFICATION PASS TESTS (POINTS 2 & 4) ====================

    @Test
    @DisplayName("Verification Point 2: Cross-midnight intermediate stops, fare matrix, and segment booking consistency")
    public void testCrossMidnightIntermediateStopsAndSegmentFares_Consistent() {
        Long operatorId = 7017L;
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op7017@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 11, 29);

        // 4 stops crossing midnight:
        // Stop 0: Hyderabad (Dep 22:00, consecutive fare 300)
        // Stop 1: Kurnool   (Arr 23:45, Dep 23:55, consecutive fare 250)
        // --- Midnight Crossing ---
        // Stop 2: Anantapur (Arr 01:45, Dep 01:55, consecutive fare 350)
        // Stop 3: Bengaluru (Arr 05:30)
        List<com.traveleasy.bus.dto.ScheduleStopDto> stops = new ArrayList<>();

        com.traveleasy.bus.dto.ScheduleStopDto s0 = new com.traveleasy.bus.dto.ScheduleStopDto();
        s0.setStopOrder(0);
        s0.setStopName("Hyderabad");
        s0.setStopType("ORIGIN");
        s0.setDepartureTime("22:00");
        s0.setDistanceKm(0.0);
        s0.setConsecutiveFare(300.0);
        stops.add(s0);

        com.traveleasy.bus.dto.ScheduleStopDto s1 = new com.traveleasy.bus.dto.ScheduleStopDto();
        s1.setStopOrder(1);
        s1.setStopName("Kurnool");
        s1.setStopType("INTERMEDIATE");
        s1.setArrivalTime("23:45");
        s1.setDepartureTime("23:55");
        s1.setDistanceKm(210.0);
        s1.setConsecutiveFare(250.0);
        stops.add(s1);

        com.traveleasy.bus.dto.ScheduleStopDto s2 = new com.traveleasy.bus.dto.ScheduleStopDto();
        s2.setStopOrder(2);
        s2.setStopName("Anantapur");
        s2.setStopType("INTERMEDIATE");
        s2.setArrivalTime("01:45");
        s2.setDepartureTime("01:55");
        s2.setDistanceKm(360.0);
        s2.setConsecutiveFare(350.0);
        stops.add(s2);

        com.traveleasy.bus.dto.ScheduleStopDto s3 = new com.traveleasy.bus.dto.ScheduleStopDto();
        s3.setStopOrder(3);
        s3.setStopName("Bengaluru");
        s3.setStopType("DESTINATION");
        s3.setArrivalTime("05:30");
        s3.setDistanceKm(570.0);
        s3.setConsecutiveFare(0.0);
        stops.add(s3);

        BusSchedule crossMidnightSchedule = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Hyderabad")
                .destination("Bengaluru")
                .travelDate(travelDate)
                .departureTime("22:00")
                .arrivalTime("05:30")
                .fare(900.0)
                .stops(stops)
                .build();

        ResponseEntity<BusSchedule> res = restTemplate.postForEntity(
                getBaseUrl() + "/schedules", new HttpEntity<>(crossMidnightSchedule, authHeaders(token)), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res.getStatusCode());

        BusSchedule created = res.getBody();
        assertNotNull(created);
        assertEquals(LocalDate.of(2026, 11, 30), created.getArrivalDate(), "Arrival date must be the next day");

        // Verify Fare Matrix across midnight
        ResponseEntity<com.traveleasy.bus.dto.ScheduleFareMatrixResponse> matrixRes = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + created.getId() + "/fare-matrix",
                com.traveleasy.bus.dto.ScheduleFareMatrixResponse.class);
        assertEquals(HttpStatus.OK, matrixRes.getStatusCode());
        assertNotNull(matrixRes.getBody());
        assertEquals(6, matrixRes.getBody().getTotalPairs(), "4 stops should yield 6 OD pairs");

        // Verify that segment crossing midnight (Kurnool -> Anantapur) is 250.0
        var kurnoolToAnantapur = matrixRes.getBody().getMatrix().stream()
                .filter(e -> e.getFromStopOrder() == 1 && e.getToStopOrder() == 2)
                .findFirst().orElseThrow();
        assertEquals(250.0, kurnoolToAnantapur.getEffectiveFare());

        // Verify that multi-segment spanning across midnight (Kurnool -> Bengaluru) is 250 + 350 = 600.0
        var kurnoolToBlr = matrixRes.getBody().getMatrix().stream()
                .filter(e -> e.getFromStopOrder() == 1 && e.getToStopOrder() == 3)
                .findFirst().orElseThrow();
        assertEquals(600.0, kurnoolToBlr.getEffectiveFare());

        // Verify segment booking across midnight: book 1 seat "1A" from Kurnool to Bengaluru (segments 1->2 and 2->3)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);

        ResponseEntity<Map> bookRes = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + created.getId() + "/book-seats?bookingRef=BK-MIDNIGHT-1&fromOrder=1&toOrder=3",
                HttpMethod.PUT,
                new HttpEntity<>(List.of("1A"), headers),
                Map.class);
        assertEquals(HttpStatus.OK, bookRes.getStatusCode());

        // Verify layout: seat 1A is booked for segment 1->3, but available for segment 0->1
        ResponseEntity<com.traveleasy.bus.dto.TripSeatLayoutResponse> layoutSeg01 = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + created.getId() + "/layout?fromOrder=0&toOrder=1",
                com.traveleasy.bus.dto.TripSeatLayoutResponse.class);
        assertEquals(HttpStatus.OK, layoutSeg01.getStatusCode());
        var seat1Seg01 = layoutSeg01.getBody().getDecks().stream()
                .flatMap(d -> d.getPositions().stream())
                .filter(p -> "1A".equals(p.getSeatNumber())).findFirst().orElseThrow();
        assertEquals("AVAILABLE", seat1Seg01.getStatus(), "Seat 1A should be available on segment 0->1");

        ResponseEntity<com.traveleasy.bus.dto.TripSeatLayoutResponse> layoutSeg13 = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + created.getId() + "/layout?fromOrder=1&toOrder=3",
                com.traveleasy.bus.dto.TripSeatLayoutResponse.class);
        assertEquals(HttpStatus.OK, layoutSeg13.getStatusCode());
        var seat1Seg13 = layoutSeg13.getBody().getDecks().stream()
                .flatMap(d -> d.getPositions().stream())
                .filter(p -> "1A".equals(p.getSeatNumber())).findFirst().orElseThrow();
        assertEquals("BOOKED", seat1Seg13.getStatus(), "Seat 1A should be BOOKED on segment 1->3");
    }

    @Test
    @DisplayName("Verification Point 4: Historical schedule 808 remains accessible, and new conflicts against it are prevented")
    public void testHistoricalScheduleAccessAndProtection_Schedules808to810() {
        // 1. Verify schedule 808 is accessible via standard details endpoint without error
        ResponseEntity<Map> res808 = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/808", Map.class);
        if (res808.getStatusCode() == HttpStatus.OK) {
            Map body = res808.getBody();
            assertNotNull(body);
            Map s808 = (Map) body.get("schedule");
            assertNotNull(s808);
            assertEquals(808, ((Number) s808.get("id")).intValue());
            assertEquals("Hyderabad", s808.get("origin"));
            assertEquals("Guntur", s808.get("destination"));
            assertEquals("SCHEDULED", s808.get("status"));

            // 2. Set clock to 2026-10-27 10:00:00 so 2026-10-27 21:00 is a future departure
            Instant histInstant = LocalDateTime.of(2026, 10, 27, 10, 0, 0)
                    .atZone(OPERATIONAL_ZONE).toInstant();
            scheduleService.setClock(Clock.fixed(histInstant, OPERATIONAL_ZONE));

            // Verify that attempting to create a NEW overlapping schedule for Bus 1 on that travel date is rejected with 409
            String tokenAdmin = createTestToken(1L, "ROLE_ADMIN", "admin@traveleasy.com");
            Long busId = ((Number) s808.get("busId")).longValue();
            LocalDate travelDate = LocalDate.parse(s808.get("travelDate").toString());

            BusSchedule conflictingNew = BusSchedule.builder()
                    .busId(busId) // Bus 1
                    .origin("Hyderabad")
                    .destination("Guntur")
                    .travelDate(travelDate)
                    .departureTime("21:00") // Overlaps with 808 operating window [20:00 to 02:45 + buffer]
                    .arrivalTime("04:00")
                    .fare(500.0)
                    .build();

            ResponseEntity<Map> conflictRes = restTemplate.postForEntity(
                    getBaseUrl() + "/schedules", new HttpEntity<>(conflictingNew, authHeaders(tokenAdmin)), Map.class);
            assertEquals(HttpStatus.CONFLICT, conflictRes.getStatusCode(), "New schedule overlapping with historical 808 must be rejected");
            assertTrue(conflictRes.getBody().get("message").toString().contains("already scheduled"));
        }
    }

    // ==================== PHASE 6.3.1 FOCUSED VERIFICATION TESTS ====================

    @Test
    @DisplayName("Phase 6.3.1 - 1. Past departure rejected and valid future departure accepted with injectable Clock")
    public void testPastDepartureRejectedAndValidFutureDepartureAccepted_WithInjectableClock() {
        Long operatorId = 2001L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op2001@traveleasy.com");

        Bus bus = createAndSaveBus(operatorId, "ACTIVE");

        // Fixed clock at 2026-11-01 10:00:00 IST
        Instant fixedInstant = LocalDateTime.of(2026, 11, 1, 10, 0, 0)
                .atZone(OPERATIONAL_ZONE).toInstant();
        scheduleService.setClock(Clock.fixed(fixedInstant, OPERATIONAL_ZONE));

        // 1. Attempt schedule with departure in the past (2026-10-31 22:00)
        BusSchedule pastSched = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.of(2026, 10, 31))
                .departureTime("22:00")
                .arrivalTime("06:00")
                .fare(800.0)
                .build();

        ResponseEntity<Map> pastRes = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(pastSched, authHeaders(token)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, pastRes.getStatusCode());
        assertNotNull(pastRes.getBody());
        assertTrue(pastRes.getBody().get("message").toString().contains("Departure time cannot be in the past"));

        // 2. Attempt schedule earlier on same day (2026-11-01 09:30: current clock is 10:00)
        BusSchedule pastTimeToday = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.of(2026, 11, 1))
                .departureTime("09:30")
                .arrivalTime("17:30")
                .fare(800.0)
                .build();

        ResponseEntity<Map> pastTodayRes = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(pastTimeToday, authHeaders(token)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, pastTodayRes.getStatusCode());
        assertTrue(pastTodayRes.getBody().get("message").toString().contains("Departure time cannot be in the past"));

        // 3. Valid future departure on same day (2026-11-01 11:30: current clock is 10:00)
        BusSchedule futureToday = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.of(2026, 11, 1))
                .departureTime("11:30")
                .arrivalTime("19:30")
                .fare(800.0)
                .build();

        ResponseEntity<BusSchedule> validRes = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(futureToday, authHeaders(token)),
                BusSchedule.class
        );
        assertEquals(HttpStatus.CREATED, validRes.getStatusCode());
        assertNotNull(validRes.getBody());
        assertNotNull(validRes.getBody().getId());
        assertEquals("SCHEDULED", validRes.getBody().getStatus());
    }

    @Test
    @DisplayName("Phase 6.3.1 - 2. Stop and point timing persistence roundtrip without data loss")
    public void testStopAndPointTimingPersistence_Roundtrip() {
        Long operatorId = 2001L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op2001@traveleasy.com");
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");

        LocalDate tripDate = LocalDate.of(2026, 11, 10);

        // Define intermediate stop with individual boarding & dropping points
        com.traveleasy.bus.dto.SchedulePointDto pt1 = com.traveleasy.bus.dto.SchedulePointDto.builder()
                .pointName("Kurnool Bus Stand")
                .pointType("BOARDING")
                .pointBadge("Boarding only")
                .departureDate(tripDate)
                .departureTime("10:00")
                .build();

        com.traveleasy.bus.dto.SchedulePointDto pt2 = com.traveleasy.bus.dto.SchedulePointDto.builder()
                .pointName("Orvakal Junction")
                .pointType("DROPPING")
                .pointBadge("Dropping only")
                .arrivalDate(tripDate)
                .arrivalTime("10:45")
                .build();

        List<com.traveleasy.bus.dto.ScheduleStopDto> stops = List.of(
                com.traveleasy.bus.dto.ScheduleStopDto.builder()
                        .stopOrder(0)
                        .stopName("Bengaluru")
                        .stopType("ORIGIN")
                        .departureDate(tripDate)
                        .departureTime("06:00")
                        .consecutiveFare(300.0)
                        .build(),
                com.traveleasy.bus.dto.ScheduleStopDto.builder()
                        .stopOrder(1)
                        .stopName("Kurnool")
                        .stopType("INTERMEDIATE")
                        .arrivalDate(tripDate)
                        .arrivalTime("10:00")
                        .departureDate(tripDate)
                        .departureTime("10:50")
                        .haltMinutes(50)
                        .consecutiveFare(400.0)
                        .points(List.of(pt1, pt2))
                        .build(),
                com.traveleasy.bus.dto.ScheduleStopDto.builder()
                        .stopOrder(2)
                        .stopName("Hyderabad")
                        .stopType("DESTINATION")
                        .arrivalDate(tripDate)
                        .arrivalTime("16:00")
                        .consecutiveFare(0.0)
                        .build()
        );

        BusSchedule schedReq = BusSchedule.builder()
                .busId(bus.getId())
                .travelDate(tripDate)
                .departureTime("06:00")
                .arrivalDate(tripDate)
                .arrivalTime("16:00")
                .fare(700.0)
                .stops(stops)
                .build();

        ResponseEntity<BusSchedule> createRes = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(schedReq, authHeaders(token)),
                BusSchedule.class
        );
        assertEquals(HttpStatus.CREATED, createRes.getStatusCode());
        assertNotNull(createRes.getBody());
        Long schedId = createRes.getBody().getId();

        // Retrieve stops and verify point persistence and deserialization
        ResponseEntity<List> stopsRes = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + schedId + "/stops",
                List.class
        );
        assertEquals(HttpStatus.OK, stopsRes.getStatusCode());
        List<Map<String, Object>> retrievedStops = (List<Map<String, Object>>) stopsRes.getBody();
        assertEquals(3, retrievedStops.size());

        Map<String, Object> kurnoolStop = retrievedStops.get(1);
        assertEquals("Kurnool", kurnoolStop.get("stopName"));
        assertEquals("10:00", kurnoolStop.get("arrivalTime"));
        assertEquals("10:50", kurnoolStop.get("departureTime"));
        assertEquals(50, ((Number) kurnoolStop.get("haltMinutes")).intValue());

        List<Map<String, Object>> pts = (List<Map<String, Object>>) kurnoolStop.get("points");
        assertNotNull(pts, "Points must be preserved in JSON and deserialized");
        assertEquals(2, pts.size());
        assertEquals("Kurnool Bus Stand", pts.get(0).get("pointName"));
        assertEquals("BOARDING", pts.get(0).get("pointType"));
        assertEquals("10:00", pts.get(0).get("departureTime"));

        assertEquals("Orvakal Junction", pts.get(1).get("pointName"));
        assertEquals("DROPPING", pts.get(1).get("pointType"));
        assertEquals("10:45", pts.get(1).get("arrivalTime"));
    }

    @Test
    @DisplayName("Phase 6.3.1 - 3. Chronological validation detects stop and point timing inversions")
    public void testChronologicalValidation_StopAndPointViolations() {
        Long operatorId = 2001L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op2001@traveleasy.com");
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");

        LocalDate tripDate = LocalDate.of(2026, 11, 12);

        // Case A: Intermediate stop departure precedes its arrival (negative halt duration)
        List<com.traveleasy.bus.dto.ScheduleStopDto> invalidHaltStops = List.of(
                com.traveleasy.bus.dto.ScheduleStopDto.builder().stopOrder(0).stopName("Bengaluru").departureTime("06:00").consecutiveFare(300.0).build(),
                com.traveleasy.bus.dto.ScheduleStopDto.builder().stopOrder(1).stopName("Kurnool").arrivalTime("11:00").departureTime("10:30").consecutiveFare(400.0).build(),
                com.traveleasy.bus.dto.ScheduleStopDto.builder().stopOrder(2).stopName("Hyderabad").arrivalTime("16:00").consecutiveFare(0.0).build()
        );
        BusSchedule badHaltSched = BusSchedule.builder().busId(bus.getId()).travelDate(tripDate).departureTime("06:00").arrivalTime("16:00").fare(700.0).stops(invalidHaltStops).build();

        ResponseEntity<Map> resA = restTemplate.postForEntity(getBaseUrl() + "/schedules", new HttpEntity<>(badHaltSched, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resA.getStatusCode());
        assertTrue(resA.getBody().get("message").toString().contains("cannot precede its arrival"));

        // Case B: Stop 2 arrival precedes Stop 1 departure
        List<com.traveleasy.bus.dto.ScheduleStopDto> outOfOrderStops = List.of(
                com.traveleasy.bus.dto.ScheduleStopDto.builder().stopOrder(0).stopName("Bengaluru").departureTime("06:00").consecutiveFare(200.0).build(),
                com.traveleasy.bus.dto.ScheduleStopDto.builder().stopOrder(1).stopName("Anantapur").arrivalTime("10:00").departureTime("10:30").consecutiveFare(200.0).build(),
                com.traveleasy.bus.dto.ScheduleStopDto.builder().stopOrder(2).stopName("Kurnool").arrivalTime("09:30").departureTime("10:00").consecutiveFare(300.0).build(),
                com.traveleasy.bus.dto.ScheduleStopDto.builder().stopOrder(3).stopName("Hyderabad").arrivalTime("16:00").consecutiveFare(0.0).build()
        );
        BusSchedule badSequenceSched = BusSchedule.builder().busId(bus.getId()).travelDate(tripDate).departureTime("06:00").arrivalTime("16:00").fare(700.0).stops(outOfOrderStops).build();

        ResponseEntity<Map> resB = restTemplate.postForEntity(getBaseUrl() + "/schedules", new HttpEntity<>(badSequenceSched, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resB.getStatusCode());
        assertTrue(resB.getBody().get("message").toString().contains("cannot precede previous stop departure"));

        // Case C: Point departure precedes point arrival
        com.traveleasy.bus.dto.SchedulePointDto badPoint = com.traveleasy.bus.dto.SchedulePointDto.builder()
                .pointName("Bad Point")
                .arrivalTime("10:30")
                .departureTime("10:15")
                .build();
        List<com.traveleasy.bus.dto.ScheduleStopDto> badPointStops = List.of(
                com.traveleasy.bus.dto.ScheduleStopDto.builder().stopOrder(0).stopName("Bengaluru").departureTime("06:00").consecutiveFare(300.0).build(),
                com.traveleasy.bus.dto.ScheduleStopDto.builder().stopOrder(1).stopName("Kurnool").arrivalTime("10:00").departureTime("11:00").consecutiveFare(400.0).points(List.of(badPoint)).build(),
                com.traveleasy.bus.dto.ScheduleStopDto.builder().stopOrder(2).stopName("Hyderabad").arrivalTime("16:00").consecutiveFare(0.0).build()
        );
        BusSchedule badPointSched = BusSchedule.builder().busId(bus.getId()).travelDate(tripDate).departureTime("06:00").arrivalTime("16:00").fare(700.0).stops(badPointStops).build();

        ResponseEntity<Map> resC = restTemplate.postForEntity(getBaseUrl() + "/schedules", new HttpEntity<>(badPointSched, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resC.getStatusCode());
        assertTrue(resC.getBody().get("message").toString().contains("cannot precede its arrival"));

        // Case D: Destination arrival does not match scheduled trip arrival
        List<com.traveleasy.bus.dto.ScheduleStopDto> destMismatchStops = List.of(
                com.traveleasy.bus.dto.ScheduleStopDto.builder().stopOrder(0).stopName("Bengaluru").departureTime("06:00").consecutiveFare(300.0).build(),
                com.traveleasy.bus.dto.ScheduleStopDto.builder().stopOrder(1).stopName("Hyderabad").arrivalDate(tripDate).arrivalTime("18:00").consecutiveFare(0.0).build()
        );
        BusSchedule destMismatchSched = BusSchedule.builder().busId(bus.getId()).travelDate(tripDate).departureTime("06:00").arrivalDate(tripDate).arrivalTime("16:00").fare(300.0).stops(destMismatchStops).build();

        ResponseEntity<Map> resD = restTemplate.postForEntity(getBaseUrl() + "/schedules", new HttpEntity<>(destMismatchSched, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resD.getStatusCode());
        assertTrue(resD.getBody().get("message").toString().contains("does not match scheduled trip arrival"));
    }

    @Test
    @DisplayName("Phase 6.3.1 - 4. Conflict check endpoint enforces authorization and bus access")
    public void testScheduleConflictEndpoint_AuthorizationAndBusAccess() {
        Long opOwner = 2001L;
        Long opOther = 2002L;
        Bus bus = createAndSaveBus(opOwner, "ACTIVE");

        com.traveleasy.bus.dto.ScheduleConflictCheckRequest req = com.traveleasy.bus.dto.ScheduleConflictCheckRequest.builder()
                .busId(bus.getId())
                .travelDate(LocalDate.of(2026, 11, 15))
                .departureTime("06:00")
                .arrivalDate(LocalDate.of(2026, 11, 15))
                .arrivalTime("14:00")
                .build();

        // 1. Missing Authorization header -> 401 UNAUTHORIZED
        ResponseEntity<Map> unauthRes = restTemplate.postForEntity(
                getBaseUrl() + "/schedules/check-conflict",
                new HttpEntity<>(req),
                Map.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, unauthRes.getStatusCode());

        // 2. Different operator attempting check on bus they do not own -> 403 FORBIDDEN
        String otherToken = createTestToken(opOther, "ROLE_BUS_OPERATOR", "other@traveleasy.com");
        ResponseEntity<Map> forbiddenRes = restTemplate.postForEntity(
                getBaseUrl() + "/schedules/check-conflict",
                new HttpEntity<>(req, authHeaders(otherToken)),
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, forbiddenRes.getStatusCode());

        // 3. Legitimate bus owner -> 200 OK
        String ownerToken = createTestToken(opOwner, "ROLE_BUS_OPERATOR", "owner@traveleasy.com");
        ResponseEntity<com.traveleasy.bus.dto.ScheduleConflictCheckResponse> okRes = restTemplate.postForEntity(
                getBaseUrl() + "/schedules/check-conflict",
                new HttpEntity<>(req, authHeaders(ownerToken)),
                com.traveleasy.bus.dto.ScheduleConflictCheckResponse.class
        );
        assertEquals(HttpStatus.OK, okRes.getStatusCode());
        assertNotNull(okRes.getBody());
        assertFalse(okRes.getBody().isHasConflict());
    }

    @Test
    @DisplayName("Phase 6.3.1 - 5. Conflict check rejects invalid inputs, past dates, and inverted arrival times")
    public void testScheduleConflictEndpoint_InvalidInputsAndValidation() {
        Long operatorId = 2001L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op2001@traveleasy.com");
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");

        // Fixed clock at 2026-11-01 10:00:00 IST
        Instant fixedInstant = LocalDateTime.of(2026, 11, 1, 10, 0, 0)
                .atZone(OPERATIONAL_ZONE).toInstant();
        scheduleService.setClock(Clock.fixed(fixedInstant, OPERATIONAL_ZONE));

        // 1. Missing travelDate -> 400 BAD_REQUEST
        com.traveleasy.bus.dto.ScheduleConflictCheckRequest missingDateReq = com.traveleasy.bus.dto.ScheduleConflictCheckRequest.builder()
                .busId(bus.getId())
                .departureTime("12:00")
                .build();
        ResponseEntity<Map> res1 = restTemplate.postForEntity(getBaseUrl() + "/schedules/check-conflict", new HttpEntity<>(missingDateReq, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res1.getStatusCode());

        // 2. Past departure (2026-10-31 12:00) -> 400 BAD_REQUEST
        com.traveleasy.bus.dto.ScheduleConflictCheckRequest pastReq = com.traveleasy.bus.dto.ScheduleConflictCheckRequest.builder()
                .busId(bus.getId())
                .travelDate(LocalDate.of(2026, 10, 31))
                .departureTime("12:00")
                .build();
        ResponseEntity<Map> res2 = restTemplate.postForEntity(getBaseUrl() + "/schedules/check-conflict", new HttpEntity<>(pastReq, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res2.getStatusCode());
        assertTrue(res2.getBody().get("message").toString().contains("Departure time cannot be in the past"));

        // 3. Inverted arrival before departure (departure 14:00, arrival 12:00 on same date) -> 400 BAD_REQUEST
        com.traveleasy.bus.dto.ScheduleConflictCheckRequest invertedReq = com.traveleasy.bus.dto.ScheduleConflictCheckRequest.builder()
                .busId(bus.getId())
                .travelDate(LocalDate.of(2026, 11, 5))
                .departureTime("14:00")
                .arrivalDate(LocalDate.of(2026, 11, 5))
                .arrivalTime("12:00")
                .build();
        ResponseEntity<Map> res3 = restTemplate.postForEntity(getBaseUrl() + "/schedules/check-conflict", new HttpEntity<>(invertedReq, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res3.getStatusCode());
        assertTrue(res3.getBody().get("message").toString().contains("must be strictly after departure"));
    }

    @Test
    @DisplayName("Phase 6.3.1 - 6. Turnaround buffer exact boundary and cancelled trip exclusion")
    public void testScheduleConflictEndpoint_TurnaroundBufferBoundaryAndCancelledSchedules() {
        Long operatorId = 2001L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op2001@traveleasy.com");
        Bus bus = createAndSaveBus(operatorId, "ACTIVE");

        LocalDate baseDate = LocalDate.of(2026, 11, 20);

        // 1. Create trip 1: 10:00 to 14:00. With 60-min buffer, it occupies bus until 15:00.
        BusSchedule trip1 = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Chennai")
                .travelDate(baseDate)
                .departureTime("10:00")
                .arrivalDate(baseDate)
                .arrivalTime("14:00")
                .fare(600.0)
                .status("SCHEDULED")
                .build();
        BusSchedule savedTrip1 = scheduleService.createSchedule(operatorId, false, trip1);
        assertNotNull(savedTrip1.getId());

        // Boundary Candidate A: Departs at 14:59 (within 60-min turnaround buffer of trip1) -> CONFLICT
        com.traveleasy.bus.dto.ScheduleConflictCheckRequest reqInsideBuffer = com.traveleasy.bus.dto.ScheduleConflictCheckRequest.builder()
                .busId(bus.getId())
                .travelDate(baseDate)
                .departureTime("14:59")
                .arrivalDate(baseDate)
                .arrivalTime("20:00")
                .build();
        ResponseEntity<com.traveleasy.bus.dto.ScheduleConflictCheckResponse> respInside = restTemplate.postForEntity(
                getBaseUrl() + "/schedules/check-conflict",
                new HttpEntity<>(reqInsideBuffer, authHeaders(token)),
                com.traveleasy.bus.dto.ScheduleConflictCheckResponse.class
        );
        assertEquals(HttpStatus.OK, respInside.getStatusCode());
        assertTrue(respInside.getBody().isHasConflict(), "Departure at 14:59 must conflict with trip ending at 14:00 with 60m buffer");
        assertEquals(savedTrip1.getId(), respInside.getBody().getConflictingScheduleId());

        // Boundary Candidate B: Departs at exactly 15:00 (exact boundary of 60-min buffer) -> NO CONFLICT
        com.traveleasy.bus.dto.ScheduleConflictCheckRequest reqExactBoundary = com.traveleasy.bus.dto.ScheduleConflictCheckRequest.builder()
                .busId(bus.getId())
                .travelDate(baseDate)
                .departureTime("15:00")
                .arrivalDate(baseDate)
                .arrivalTime("21:00")
                .build();
        ResponseEntity<com.traveleasy.bus.dto.ScheduleConflictCheckResponse> respExact = restTemplate.postForEntity(
                getBaseUrl() + "/schedules/check-conflict",
                new HttpEntity<>(reqExactBoundary, authHeaders(token)),
                com.traveleasy.bus.dto.ScheduleConflictCheckResponse.class
        );
        assertEquals(HttpStatus.OK, respExact.getStatusCode());
        assertFalse(respExact.getBody().isHasConflict(), "Departure at exact turnaround boundary (15:00) must be conflict-free");

        // 2. Cancel trip 1 and verify that former conflicting window becomes available
        savedTrip1.setStatus("CANCELLED");
        scheduleRepository.save(savedTrip1);

        ResponseEntity<com.traveleasy.bus.dto.ScheduleConflictCheckResponse> respAfterCancel = restTemplate.postForEntity(
                getBaseUrl() + "/schedules/check-conflict",
                new HttpEntity<>(reqInsideBuffer, authHeaders(token)),
                com.traveleasy.bus.dto.ScheduleConflictCheckResponse.class
        );
        assertEquals(HttpStatus.OK, respAfterCancel.getStatusCode());
        assertFalse(respAfterCancel.getBody().isHasConflict(), "Cancelled schedule must be excluded from conflict detection");
    }
}
