package com.traveleasy.bus;

import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.Seat;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.SeatRepository;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BusScheduleCancellationEndpointsTest {

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

    private BusSchedule createTestSchedule(Long operatorId) {
        Bus bus = busRepository.save(Bus.builder()
                .busNumber("AP09-TEST-" + System.currentTimeMillis() % 10000)
                .busType("AC Sleeper (2+1)")
                .totalSeats(30)
                .operatorName("Speed Travels")
                .operatorId(operatorId)
                .status("ACTIVE")
                .templateId("tpl-sleeper-2-1-30")
                .build());

        BusSchedule schedule = scheduleRepository.save(BusSchedule.builder()
                .busId(bus.getId())
                .operatorId(operatorId)
                .operatorName(bus.getOperatorName())
                .busNumber(bus.getBusNumber())
                .busType(bus.getBusType())
                .origin("Hyderabad")
                .destination("Vijayawada")
                .travelDate(LocalDate.now().plusDays(2))
                .departureTime("21:00")
                .arrivalTime("05:00")
                .fare(650.0)
                .availableSeats(30)
                .status("SCHEDULED")
                .build());

        seatLayoutService.generateTripSeats(schedule, bus);
        return schedule;
    }

    @Test
    @DisplayName("PUT /schedules/{id}/status - Internal booking-service can update schedule status to CANCELLED")
    void testUpdateScheduleStatus_InternalService_Success() {
        BusSchedule schedule = createTestSchedule(100L);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "booking-service");
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/status?status=CANCELLED",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CANCELLED", response.getBody().get("status"));

        BusSchedule updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertEquals("CANCELLED", updated.getStatus());
    }

    @Test
    @DisplayName("PUT /schedules/{id}/status - Owning operator can update status with valid token")
    void testUpdateScheduleStatus_OwningOperator_Success() {
        BusSchedule schedule = createTestSchedule(200L);
        String token = createTestToken(200L, "ROLE_BUS_OPERATOR", "op200@traveleasy.com");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/status?status=CANCELLED",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CANCELLED", response.getBody().get("status"));
    }

    @Test
    @DisplayName("PUT /schedules/{id}/status - Cross-operator request rejected with 403 Forbidden")
    void testUpdateScheduleStatus_CrossOperator_Forbidden() {
        BusSchedule schedule = createTestSchedule(200L);
        String token = createTestToken(999L, "ROLE_BUS_OPERATOR", "op999@traveleasy.com");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/status?status=CANCELLED",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("PUT /schedules/{id}/status - Unauthenticated request rejected with 401 Unauthorized")
    void testUpdateScheduleStatus_NoAuth_Unauthorized() {
        BusSchedule schedule = createTestSchedule(200L);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/status?status=CANCELLED",
                HttpMethod.PUT,
                null,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("PUT /schedules/{id}/release-all-seats - Releases all held and booked seats to AVAILABLE")
    void testReleaseAllSeats_InternalService_Success() {
        BusSchedule schedule = createTestSchedule(300L);
        List<Seat> seats = seatRepository.findByScheduleId(schedule.getId());
        assertFalse(seats.isEmpty());

        // Mark some seats as BOOKED or LOCKED
        seats.get(0).setStatus("BOOKED");
        seats.get(0).setHoldBookingRef("BKG-CONFIRMED-1");
        seats.get(1).setStatus("LOCKED");
        seats.get(1).setHoldBookingRef("BKG-HOLD-1");
        seatRepository.saveAll(seats);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "booking-service");
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/release-all-seats",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(((Number) response.getBody().get("releasedCount")).intValue() > 0);

        List<Seat> releasedSeats = seatRepository.findByScheduleId(schedule.getId());
        for (Seat s : releasedSeats) {
            assertEquals("AVAILABLE", s.getStatus());
            assertNull(s.getHoldBookingRef());
            assertNull(s.getHoldExpiresAt());
        }
    }

    @Test
    @DisplayName("Unit 3.4: PUT /schedules/{id}/release-all-seats - Second call is idempotent with 0 newly released seats")
    void testReleaseAllSeats_Idempotent_SecondCallNoOp() {
        BusSchedule schedule = createTestSchedule(301L);
        List<Seat> seats = seatRepository.findByScheduleId(schedule.getId());
        seats.get(0).setStatus("BOOKED");
        seats.get(0).setHoldBookingRef("BKG-IDEMP-1");
        seatRepository.saveAll(seats);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "booking-service");
        HttpEntity<?> entity = new HttpEntity<>(headers);

        // First call releases 1 seat
        ResponseEntity<Map> firstResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/release-all-seats",
                HttpMethod.PUT,
                entity,
                Map.class
        );
        assertEquals(HttpStatus.OK, firstResp.getStatusCode());
        assertEquals(1, ((Number) firstResp.getBody().get("releasedCount")).intValue());

        // Second call finds all seats already AVAILABLE -> releasedCount = 0
        ResponseEntity<Map> secondResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/release-all-seats",
                HttpMethod.PUT,
                entity,
                Map.class
        );
        assertEquals(HttpStatus.OK, secondResp.getStatusCode());
        assertEquals(0, ((Number) secondResp.getBody().get("releasedCount")).intValue());
        assertEquals(seats.size(), ((Number) secondResp.getBody().get("totalSeats")).intValue());

        BusSchedule reloaded = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertEquals(seats.size(), reloaded.getAvailableSeats());
    }

    @Test
    @DisplayName("Unit 3.4: PUT /schedules/{id}/release-all-seats - Owning operator can release all seats")
    void testReleaseAllSeats_OwningOperator_Success() {
        BusSchedule schedule = createTestSchedule(302L);
        String token = createTestToken(302L, "ROLE_BUS_OPERATOR", "op302@traveleasy.com");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/release-all-seats",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(schedule.getId().intValue(), ((Number) response.getBody().get("scheduleId")).intValue());
    }

    @Test
    @DisplayName("Unit 3.4: PUT /schedules/{id}/release-all-seats - Cross-operator request rejected with 403 Forbidden")
    void testReleaseAllSeats_CrossOperator_Forbidden() {
        BusSchedule schedule = createTestSchedule(303L);
        String token = createTestToken(999L, "ROLE_BUS_OPERATOR", "intruder@traveleasy.com");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/release-all-seats",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("Unit 3.4: PUT /schedules/{id}/release-all-seats - Unauthenticated request rejected with 401")
    void testReleaseAllSeats_NoAuth_Unauthorized() {
        BusSchedule schedule = createTestSchedule(304L);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/release-all-seats",
                HttpMethod.PUT,
                null,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Unit 3.4: Guard - Booking seats on CANCELLED schedule is rejected with 409 Conflict")
    void testBookSeats_CancelledSchedule_RejectedConflict() {
        BusSchedule schedule = createTestSchedule(305L);
        schedule.setStatus("CANCELLED");
        scheduleRepository.save(schedule);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> entity = new HttpEntity<>(List.of("1A"), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/book-seats",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("cancelled"));
    }

    @Test
    @DisplayName("Unit 3.4: Guard - Confirming seats on CANCELLED schedule is rejected with 409 Conflict")
    void testConfirmSeats_CancelledSchedule_RejectedConflict() {
        BusSchedule schedule = createTestSchedule(306L);
        schedule.setStatus("CANCELLED");
        scheduleRepository.save(schedule);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> entity = new HttpEntity<>(List.of("1A"), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/confirm-seats",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("cancelled"));
    }

    @Test
    @DisplayName("Unit 3.4: Invariant - Cancelled schedule is excluded from search results")
    void testSearchBuses_CancelledSchedule_ExcludedFromResults() {
        BusSchedule activeSchedule = createTestSchedule(307L);
        activeSchedule.setOrigin("Goa");
        activeSchedule.setDestination("Mumbai");
        activeSchedule.setTravelDate(LocalDate.now().plusDays(10));
        activeSchedule.setStatus("SCHEDULED");
        scheduleRepository.save(activeSchedule);

        BusSchedule cancelledSchedule = createTestSchedule(307L);
        cancelledSchedule.setOrigin("Goa");
        cancelledSchedule.setDestination("Mumbai");
        cancelledSchedule.setTravelDate(LocalDate.now().plusDays(10));
        cancelledSchedule.setStatus("CANCELLED");
        scheduleRepository.save(cancelledSchedule);

        ResponseEntity<List> response = restTemplate.getForEntity(
                getBaseUrl() + "/search?origin=Goa&destination=Mumbai&date=" + LocalDate.now().plusDays(10),
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody();
        boolean activeFound = false;
        boolean cancelledFound = false;
        for (Map<String, Object> item : results) {
            Long id = ((Number) item.get("id")).longValue();
            if (id.equals(activeSchedule.getId())) {
                activeFound = true;
            }
            if (id.equals(cancelledSchedule.getId())) {
                cancelledFound = true;
            }
        }

        assertTrue(activeFound, "Active schedule should be found in search");
        assertFalse(cancelledFound, "Cancelled schedule must be excluded from search");
    }
}
