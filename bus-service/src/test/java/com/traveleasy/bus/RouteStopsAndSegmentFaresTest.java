package com.traveleasy.bus;

import com.traveleasy.bus.dto.ScheduleStopDto;
import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.ScheduleStop;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.ScheduleStopRepository;
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
public class RouteStopsAndSegmentFaresTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private BusScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleStopRepository stopRepository;

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
                .busNumber("AP09-ROUTE-" + System.currentTimeMillis() % 10000)
                .busType("AC Sleeper (2+1)")
                .totalSeats(30)
                .operatorName("Orange Tours & Travels")
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
                .destination("Bengaluru")
                .travelDate(LocalDate.now().plusDays(3))
                .departureTime("20:00")
                .arrivalTime("06:00")
                .fare(1000.0)
                .availableSeats(30)
                .status("SCHEDULED")
                .build());

        seatLayoutService.generateTripSeats(schedule, bus);
        return schedule;
    }

    @Test
    @DisplayName("Unit 4.1: PUT /schedules/{id}/stops - Successfully configures 4 ordered stops and sums consecutive segment fares")
    void testConfigureRouteStops_FourStops_Success() {
        BusSchedule schedule = createTestSchedule(500L);
        String token = createTestToken(500L, "ROLE_BUS_OPERATOR", "op500@traveleasy.com");

        List<ScheduleStopDto> stops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Hyderabad").departureTime("20:00").consecutiveFare(400.0).distanceKm(0.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Kurnool").arrivalTime("23:30").departureTime("23:45").consecutiveFare(300.0).distanceKm(210.0).build(),
                ScheduleStopDto.builder().stopOrder(2).stopName("Anantapur").arrivalTime("02:30").departureTime("02:40").consecutiveFare(350.0).distanceKm(360.0).build(),
                ScheduleStopDto.builder().stopOrder(3).stopName("Bengaluru").arrivalTime("06:00").consecutiveFare(0.0).distanceKm(570.0).build()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<List<ScheduleStopDto>> entity = new HttpEntity<>(stops, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/stops",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> returnedStops = (List<Map<String, Object>>) response.getBody().get("stops");
        assertEquals(4, returnedStops.size());

        // Verify stop types and consecutive segment fares
        assertEquals("ORIGIN", returnedStops.get(0).get("stopType"));
        assertEquals(400.0, ((Number) returnedStops.get(0).get("consecutiveFare")).doubleValue());
        assertEquals("INTERMEDIATE", returnedStops.get(1).get("stopType"));
        assertEquals(300.0, ((Number) returnedStops.get(1).get("consecutiveFare")).doubleValue());
        assertEquals("INTERMEDIATE", returnedStops.get(2).get("stopType"));
        assertEquals(350.0, ((Number) returnedStops.get(2).get("consecutiveFare")).doubleValue());
        assertEquals("DESTINATION", returnedStops.get(3).get("stopType"));
        assertEquals(0.0, ((Number) returnedStops.get(3).get("consecutiveFare")).doubleValue());

        // Verify parent schedule fare updated to sum: 400 + 300 + 350 = 1050.0
        BusSchedule updatedSchedule = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertEquals(1050.0, updatedSchedule.getFare());
        assertEquals("Hyderabad", updatedSchedule.getOrigin());
        assertEquals("Bengaluru", updatedSchedule.getDestination());
        assertEquals("20:00", updatedSchedule.getDepartureTime());
        assertEquals("06:00", updatedSchedule.getArrivalTime());
    }

    @Test
    @DisplayName("Unit 4.1: GET /schedules/{id}/stops - Retrieves configured stops in ascending order")
    void testGetStopsForSchedule_ReturnsConfiguredStops() {
        BusSchedule schedule = createTestSchedule(501L);
        String token = createTestToken(501L, "ROLE_BUS_OPERATOR", "op501@traveleasy.com");

        List<ScheduleStopDto> stops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Chennai").departureTime("18:00").consecutiveFare(300.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Vellore").arrivalTime("20:30").departureTime("20:45").consecutiveFare(350.0).build(),
                ScheduleStopDto.builder().stopOrder(2).stopName("Bengaluru").arrivalTime("01:00").consecutiveFare(0.0).build()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        restTemplate.exchange(getBaseUrl() + "/schedules/" + schedule.getId() + "/stops", HttpMethod.PUT, new HttpEntity<>(stops, headers), Map.class);

        ResponseEntity<List> getResp = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/stops",
                List.class
        );

        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        List<Map<String, Object>> list = (List<Map<String, Object>>) getResp.getBody();
        assertEquals(3, list.size());
        assertEquals("Chennai", list.get(0).get("stopName"));
        assertEquals("Vellore", list.get(1).get("stopName"));
        assertEquals("Bengaluru", list.get(2).get("stopName"));
    }

    @Test
    @DisplayName("Unit 4.1: POST /schedules - Automatically creates route stops from schedule stops payload")
    void testCreateSchedule_WithStopsPayload_AutoConfiguresStops() {
        Bus bus = busRepository.save(Bus.builder()
                .busNumber("AP09-CR-" + System.currentTimeMillis() % 10000)
                .busType("AC Sleeper (2+1)")
                .totalSeats(30)
                .operatorName("Orange Tours")
                .operatorId(502L)
                .status("ACTIVE")
                .templateId("tpl-sleeper-2-1-30")
                .build());

        String token = createTestToken(502L, "ROLE_BUS_OPERATOR", "op502@traveleasy.com");

        List<ScheduleStopDto> stops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Vijayawada").departureTime("21:00").consecutiveFare(250.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Guntur").arrivalTime("22:00").departureTime("22:15").consecutiveFare(600.0).build(),
                ScheduleStopDto.builder().stopOrder(2).stopName("Hyderabad").arrivalTime("05:00").consecutiveFare(0.0).build()
        );

        BusSchedule scheduleReq = BusSchedule.builder()
                .busId(bus.getId())
                .travelDate(LocalDate.now().plusDays(4))
                .stops(stops)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);

        ResponseEntity<BusSchedule> response = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(scheduleReq, headers),
                BusSchedule.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        BusSchedule created = response.getBody();
        assertNotNull(created);
        assertEquals(850.0, created.getFare()); // 250 + 600 = 850.0
        assertEquals("Vijayawada", created.getOrigin());
        assertEquals("Hyderabad", created.getDestination());

        List<ScheduleStop> savedStops = stopRepository.findByScheduleIdOrderByStopOrderAsc(created.getId());
        assertEquals(3, savedStops.size());
        assertEquals("Guntur", savedStops.get(1).getStopName());
    }

    @Test
    @DisplayName("Unit 4.1: Backward Compatibility - Legacy schedule without custom stops returns synthetic 2-stop route")
    void testGetStopsForSchedule_LegacyScheduleFallback_ReturnsSyntheticTwoStops() {
        BusSchedule legacySchedule = createTestSchedule(503L);

        ResponseEntity<List> response = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + legacySchedule.getId() + "/stops",
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> stops = (List<Map<String, Object>>) response.getBody();
        assertEquals(2, stops.size());
        assertEquals("ORIGIN", stops.get(0).get("stopType"));
        assertEquals(legacySchedule.getOrigin(), stops.get(0).get("stopName"));
        assertEquals("DESTINATION", stops.get(1).get("stopType"));
        assertEquals(legacySchedule.getDestination(), stops.get(1).get("stopName"));
        assertEquals(legacySchedule.getFare(), ((Number) stops.get(0).get("consecutiveFare")).doubleValue());
    }

    @Test
    @DisplayName("Unit 4.1: Validation - Route with less than 2 stops is rejected with 400 Bad Request")
    void testConfigureRouteStops_Validation_FailsIfLessThanTwoStops() {
        BusSchedule schedule = createTestSchedule(504L);
        String token = createTestToken(504L, "ROLE_BUS_OPERATOR", "op504@traveleasy.com");

        List<ScheduleStopDto> singleStop = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Hyderabad").departureTime("20:00").consecutiveFare(400.0).build()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/stops",
                HttpMethod.PUT,
                new HttpEntity<>(singleStop, headers),
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Unit 4.1: Validation - Duplicate stop name is rejected with 400 Bad Request")
    void testConfigureRouteStops_Validation_FailsOnDuplicateStopNames() {
        BusSchedule schedule = createTestSchedule(505L);
        String token = createTestToken(505L, "ROLE_BUS_OPERATOR", "op505@traveleasy.com");

        List<ScheduleStopDto> duplicateStops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Hyderabad").departureTime("20:00").consecutiveFare(400.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Kurnool").consecutiveFare(300.0).build(),
                ScheduleStopDto.builder().stopOrder(2).stopName("Hyderabad").consecutiveFare(0.0).build()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/stops",
                HttpMethod.PUT,
                new HttpEntity<>(duplicateStops, headers),
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Unit 4.1: Validation - Non-positive consecutive fare on intermediate segment rejected with 400")
    void testConfigureRouteStops_Validation_FailsOnNonPositiveConsecutiveFare() {
        BusSchedule schedule = createTestSchedule(506L);
        String token = createTestToken(506L, "ROLE_BUS_OPERATOR", "op506@traveleasy.com");

        List<ScheduleStopDto> invalidFareStops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Hyderabad").departureTime("20:00").consecutiveFare(400.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Kurnool").consecutiveFare(0.0).build(), // Invalid 0 fare
                ScheduleStopDto.builder().stopOrder(2).stopName("Bengaluru").consecutiveFare(0.0).build()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/stops",
                HttpMethod.PUT,
                new HttpEntity<>(invalidFareStops, headers),
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Unit 4.1: Security - Cross-operator modification rejected with 403 Forbidden")
    void testConfigureRouteStops_Security_CrossOperator_Forbidden() {
        BusSchedule schedule = createTestSchedule(507L);
        String intruderToken = createTestToken(999L, "ROLE_BUS_OPERATOR", "intruder@traveleasy.com");

        List<ScheduleStopDto> stops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Hyderabad").consecutiveFare(400.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Bengaluru").consecutiveFare(0.0).build()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + intruderToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/stops",
                HttpMethod.PUT,
                new HttpEntity<>(stops, headers),
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("Unit 4.1: Security - Unauthenticated request rejected with 401 Unauthorized")
    void testConfigureRouteStops_Security_NoAuth_Unauthorized() {
        BusSchedule schedule = createTestSchedule(508L);

        List<ScheduleStopDto> stops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Hyderabad").consecutiveFare(400.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Bengaluru").consecutiveFare(0.0).build()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/stops",
                HttpMethod.PUT,
                new HttpEntity<>(stops, headers),
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Unit 4.1: Guard - Modifying route stops on CANCELLED schedule rejected with 409 Conflict")
    void testConfigureRouteStops_CancelledSchedule_RejectedConflict() {
        BusSchedule schedule = createTestSchedule(509L);
        schedule.setStatus("CANCELLED");
        scheduleRepository.save(schedule);

        String token = createTestToken(509L, "ROLE_BUS_OPERATOR", "op509@traveleasy.com");

        List<ScheduleStopDto> stops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Hyderabad").consecutiveFare(400.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Bengaluru").consecutiveFare(0.0).build()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/stops",
                HttpMethod.PUT,
                new HttpEntity<>(stops, headers),
                Map.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    @DisplayName("Unit 4.1: Atomic Replacement - Second PUT replaces previous stops cleanly")
    void testConfigureRouteStops_AtomicReplacement_OldStopsReplaced() {
        BusSchedule schedule = createTestSchedule(510L);
        String token = createTestToken(510L, "ROLE_BUS_OPERATOR", "op510@traveleasy.com");

        List<ScheduleStopDto> initialStops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Hyderabad").consecutiveFare(400.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Kurnool").consecutiveFare(300.0).build(),
                ScheduleStopDto.builder().stopOrder(2).stopName("Bengaluru").consecutiveFare(0.0).build()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);

        restTemplate.exchange(getBaseUrl() + "/schedules/" + schedule.getId() + "/stops", HttpMethod.PUT, new HttpEntity<>(initialStops, headers), Map.class);
        assertEquals(3, stopRepository.findByScheduleIdOrderByStopOrderAsc(schedule.getId()).size());

        // Reconfigure with 2 stops
        List<ScheduleStopDto> newStops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Hyderabad").consecutiveFare(700.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Bengaluru").consecutiveFare(0.0).build()
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/stops",
                HttpMethod.PUT,
                new HttpEntity<>(newStops, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<ScheduleStop> currentStops = stopRepository.findByScheduleIdOrderByStopOrderAsc(schedule.getId());
        assertEquals(2, currentStops.size());
        assertEquals(700.0, currentStops.get(0).getConsecutiveFare());
        assertEquals(700.0, scheduleRepository.findById(schedule.getId()).orElseThrow().getFare());
    }
}
