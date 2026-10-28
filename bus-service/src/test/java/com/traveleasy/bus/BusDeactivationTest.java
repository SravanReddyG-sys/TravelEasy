package com.traveleasy.bus;

import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
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
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.security.Key;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BusDeactivationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private BusScheduleRepository scheduleRepository;

    @Autowired
    private SeatLayoutService seatLayoutService;

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
                .busNumber("KA-01-DEACT-" + (System.currentTimeMillis() % 100000))
                .busType("AC Sleeper")
                .facilityType("AC")
                .busConfiguration("SINGLE_DECK")
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER)
                .status(status != null ? status : "ACTIVE")
                .build();
        seatLayoutService.validateAndAssignTemplate(bus, bus.getTemplateId(), bus.getBusConfiguration(), bus.getFacilityType());
        return busRepository.save(bus);
    }

    private BusSchedule createAndSaveSchedule(Long busId, Long operatorId, LocalDate travelDate, String status) {
        BusSchedule schedule = BusSchedule.builder()
                .busId(busId)
                .operatorId(operatorId)
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(travelDate)
                .departureTime("21:00")
                .arrivalTime("06:00")
                .fare(600.0)
                .availableSeats(36)
                .status(status != null ? status : "SCHEDULED")
                .build();
        return scheduleRepository.save(schedule);
    }

    // ==================== UNIT 3.1 DEACTIVATION TESTS ====================

    @Test
    @DisplayName("Unit 3.1: PUT /api/buses/{id}/deactivate - Unauthenticated request rejected with 401")
    public void testDeactivateBus_Unauthenticated_Returns401() {
        ResponseEntity<Map> res = restTemplate.exchange(
                getBaseUrl() + "/1/deactivate",
                HttpMethod.PUT,
                new HttpEntity<>(new HttpHeaders()),
                Map.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    @Test
    @DisplayName("Unit 3.1: PUT /api/buses/{id}/deactivate - Invalid token rejected with 401")
    public void testDeactivateBus_InvalidToken_Returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer invalid.jwt.token");

        ResponseEntity<Map> res = restTemplate.exchange(
                getBaseUrl() + "/1/deactivate",
                HttpMethod.PUT,
                new HttpEntity<>(headers),
                Map.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    @Test
    @DisplayName("Unit 3.1: PUT /api/buses/{id}/deactivate - Customer role rejected with 403")
    public void testDeactivateBus_CustomerRole_Returns403() {
        String token = createTestToken(101L, "ROLE_CUSTOMER", "customer@traveleasy.com");

        ResponseEntity<Map> res = restTemplate.exchange(
                getBaseUrl() + "/1/deactivate",
                HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token)),
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    @DisplayName("Unit 3.1: PUT /api/buses/{id}/deactivate - Bus not found returns 404")
    public void testDeactivateBus_BusNotFound_Returns404() {
        String token = createTestToken(888L, "ROLE_BUS_OPERATOR", "op888@traveleasy.com");

        ResponseEntity<Map> res = restTemplate.exchange(
                getBaseUrl() + "/999999/deactivate",
                HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token)),
                Map.class
        );
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    @DisplayName("Unit 3.1 / TEST-03: Bus Ownership Enforcement - Operator #777 attempts deactivation on #888's bus")
    public void testDeactivateBus_TEST03_CrossOperatorOwnership_Returns403() {
        Bus busOf888 = createAndSaveBus(888L, "ACTIVE");

        // Operator 777 attempts to deactivate Operator 888's bus
        String token777 = createTestToken(777L, "ROLE_BUS_OPERATOR", "op777@traveleasy.com");

        ResponseEntity<Map> res = restTemplate.exchange(
                getBaseUrl() + "/" + busOf888.getId() + "/deactivate",
                HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token777)),
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("You do not have permission to modify this bus.", res.getBody().get("message"));

        // Status remains unchanged
        Bus checkBus = busRepository.findById(busOf888.getId()).orElseThrow();
        assertEquals("ACTIVE", checkBus.getStatus());
    }

    @Test
    @DisplayName("Unit 3.1 / TEST-04: Deactivation Guard on Future Trips - Bus has 2 active departures next week")
    public void testDeactivateBus_TEST04_ActiveUpcomingDeparturesExist_Returns409() {
        Bus bus = createAndSaveBus(888L, "ACTIVE");

        // Create 2 future departures scheduled next week
        createAndSaveSchedule(bus.getId(), 888L, LocalDate.now().plusDays(3), "SCHEDULED");
        createAndSaveSchedule(bus.getId(), 888L, LocalDate.now().plusDays(5), "SCHEDULED");

        String token888 = createTestToken(888L, "ROLE_BUS_OPERATOR", "op888@traveleasy.com");

        ResponseEntity<Map> res = restTemplate.exchange(
                getBaseUrl() + "/" + bus.getId() + "/deactivate",
                HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token888)),
                Map.class
        );

        assertEquals(HttpStatus.CONFLICT, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("Cannot deactivate bus: 2 active upcoming departures exist.", res.getBody().get("message"));

        // Status remains ACTIVE
        Bus checkBus = busRepository.findById(bus.getId()).orElseThrow();
        assertEquals("ACTIVE", checkBus.getStatus());
    }

    @Test
    @DisplayName("Unit 3.1: Cancelled departures do not block deactivation")
    public void testDeactivateBus_CancelledUpcomingDepartures_DoesNotBlock_Success() {
        Bus bus = createAndSaveBus(888L, "ACTIVE");

        // Create 2 future departures that were CANCELLED
        createAndSaveSchedule(bus.getId(), 888L, LocalDate.now().plusDays(3), "CANCELLED");
        createAndSaveSchedule(bus.getId(), 888L, LocalDate.now().plusDays(5), "CANCELLED");

        String token888 = createTestToken(888L, "ROLE_BUS_OPERATOR", "op888@traveleasy.com");

        ResponseEntity<Bus> res = restTemplate.exchange(
                getBaseUrl() + "/" + bus.getId() + "/deactivate",
                HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token888)),
                Bus.class
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("INACTIVE", res.getBody().getStatus());

        // Verify in DB
        Bus checkBus = busRepository.findById(bus.getId()).orElseThrow();
        assertEquals("INACTIVE", checkBus.getStatus());
    }

    @Test
    @DisplayName("Unit 3.1: Past departures only do not block deactivation")
    public void testDeactivateBus_PastDeparturesOnly_Success() {
        Bus bus = createAndSaveBus(888L, "ACTIVE");

        // Create past departures
        createAndSaveSchedule(bus.getId(), 888L, LocalDate.now().minusDays(3), "COMPLETED");
        createAndSaveSchedule(bus.getId(), 888L, LocalDate.now().minusDays(7), "COMPLETED");

        String token888 = createTestToken(888L, "ROLE_BUS_OPERATOR", "op888@traveleasy.com");

        ResponseEntity<Bus> res = restTemplate.exchange(
                getBaseUrl() + "/" + bus.getId() + "/deactivate",
                HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token888)),
                Bus.class
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("INACTIVE", res.getBody().getStatus());
    }

    @Test
    @DisplayName("Unit 3.1: No departures allows immediate deactivation")
    public void testDeactivateBus_NoDepartures_Success() {
        Bus bus = createAndSaveBus(888L, "ACTIVE");

        String token888 = createTestToken(888L, "ROLE_BUS_OPERATOR", "op888@traveleasy.com");

        ResponseEntity<Bus> res = restTemplate.exchange(
                getBaseUrl() + "/" + bus.getId() + "/deactivate",
                HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token888)),
                Bus.class
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("INACTIVE", res.getBody().getStatus());
    }

    @Test
    @DisplayName("Unit 3.1: Admin can deactivate bus with no future trips")
    public void testDeactivateBus_Admin_Success() {
        Bus bus = createAndSaveBus(888L, "ACTIVE");
        String adminToken = createTestToken(1L, "ROLE_ADMIN", "admin@traveleasy.com");

        ResponseEntity<Bus> res = restTemplate.exchange(
                getBaseUrl() + "/" + bus.getId() + "/deactivate",
                HttpMethod.PUT,
                new HttpEntity<>(authHeaders(adminToken)),
                Bus.class
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("INACTIVE", res.getBody().getStatus());
    }

    @Test
    @DisplayName("Unit 3.1: Reactivate bus returns status ACTIVE")
    public void testActivateBus_AuthorizedOperator_Success() {
        Bus bus = createAndSaveBus(888L, "INACTIVE");
        String token888 = createTestToken(888L, "ROLE_BUS_OPERATOR", "op888@traveleasy.com");

        ResponseEntity<Bus> res = restTemplate.exchange(
                getBaseUrl() + "/" + bus.getId() + "/activate",
                HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token888)),
                Bus.class
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("ACTIVE", res.getBody().getStatus());

        Bus checkBus = busRepository.findById(bus.getId()).orElseThrow();
        assertEquals("ACTIVE", checkBus.getStatus());
    }

    @Test
    @DisplayName("Unit 3.1 / TEST-05: Scheduling trips on deactivated bus is rejected with 400")
    public void testScheduleCreation_TEST05_BlockedOnDeactivatedBus() {
        Bus bus = createAndSaveBus(888L, "INACTIVE");
        String token888 = createTestToken(888L, "ROLE_BUS_OPERATOR", "op888@traveleasy.com");

        BusSchedule req = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.now().plusDays(5))
                .fare(500.0)
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(req, authHeaders(token888)),
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().get("message").toString().contains("Cannot schedule trips on an inactive bus"));
    }
}
