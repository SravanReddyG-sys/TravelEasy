package com.traveleasy.bus;

import com.traveleasy.bus.dto.FareOverrideItemDto;
import com.traveleasy.bus.dto.SchedulePointDto;
import com.traveleasy.bus.dto.ScheduleStopDto;
import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.Route;
import com.traveleasy.bus.model.RouteStop;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.RouteRepository;
import com.traveleasy.bus.repository.RouteStopRepository;
import com.traveleasy.bus.repository.SeatRepository;
import com.traveleasy.bus.service.FareMatrixService;
import com.traveleasy.bus.service.ScheduleService;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ScheduleReviewAndCreationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private RouteStopRepository routeStopRepository;

    @Autowired
    private BusScheduleRepository scheduleRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private FareMatrixService fareMatrixService;

    private static final String SECRET = "TravelEasySuperSecretKeyForJWTSigning12345678901234567890";
    private static final ZoneId OPERATIONAL_ZONE = ZoneId.of("Asia/Kolkata");

    private Clock originalClock;
    private Bus testBus;
    private Route testRoute;
    private Long operatorId = 201L;

    @BeforeEach
    public void setup() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        originalClock = scheduleService.getClock();
        Instant fixedInstant = LocalDateTime.of(2026, 10, 20, 10, 0, 0)
                .atZone(OPERATIONAL_ZONE).toInstant();
        scheduleService.setClock(Clock.fixed(fixedInstant, OPERATIONAL_ZONE));
        scheduleService.setTurnaroundBufferMinutes(60);

        // Provision a test active bus
        testBus = busRepository.save(Bus.builder()
                .operatorId(operatorId)
                .operatorName("Rajesh Travels")
                .busNumber("AP39AB1234-" + System.currentTimeMillis())
                .busType("AC Sleeper")
                .totalSeats(36)
                .manufacturer("Volvo")
                .model("9600")
                .fuelType("Diesel")
                .status("ACTIVE")
                .build());

        // Provision a test active route with 4 stops
        testRoute = routeRepository.save(Route.builder()
                .operatorId(operatorId)
                .routeCode("RTE-TEST-" + System.currentTimeMillis())
                .routeName("Bengaluru - Hyderabad Express")
                .origin("Bengaluru")
                .destination("Hyderabad")
                .status("ACTIVE")
                .build());

        routeStopRepository.save(RouteStop.builder().routeId(testRoute.getId()).stopOrder(0).stopName("Bengaluru (Origin)").stopType("ORIGIN").distanceKm(0.0).build());
        routeStopRepository.save(RouteStop.builder().routeId(testRoute.getId()).stopOrder(1).stopName("Kurnool").stopType("INTERMEDIATE").distanceKm(210.0).build());
        routeStopRepository.save(RouteStop.builder().routeId(testRoute.getId()).stopOrder(2).stopName("Anantapur").stopType("INTERMEDIATE").distanceKm(320.0).build());
        routeStopRepository.save(RouteStop.builder().routeId(testRoute.getId()).stopOrder(3).stopName("Hyderabad (Destination)").stopType("DESTINATION").distanceKm(420.0).build());
    }

    @AfterEach
    public void tearDown() {
        if (originalClock != null) {
            scheduleService.setClock(originalClock);
        }
    }

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/buses";
    }

    private String createToken(Long userId, String role, String email) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .claim("userId", userId)
                .claim("role", role)
                .claim("fullName", "Operator " + userId)
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
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("Phase 6.5: Complete Schedule Creation with Stops, Points, Segment Fares, and Overrides")
    public void testCompleteScheduleCreation_Success() {
        String token = createToken(operatorId, "ROLE_BUS_OPERATOR", "operator201@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 10, 24);
        LocalDate arrivalDate = LocalDate.of(2026, 10, 25);

        // Build 4 stops with points and consecutive segment fares
        List<ScheduleStopDto> stops = new ArrayList<>();

        // Stop 0: Bengaluru (Origin)
        List<SchedulePointDto> stop0Points = List.of(
                SchedulePointDto.builder().pointName("Majestic Bus Stand").pointType("BOARDING").pointBadge("Boarding only").departureDate(travelDate).departureTime("06:00").build(),
                SchedulePointDto.builder().pointName("Yeshwanthpur").pointType("BOARDING").pointBadge("Boarding only").departureDate(travelDate).departureTime("06:30").build()
        );
        stops.add(ScheduleStopDto.builder()
                .stopOrder(0)
                .stopName("Bengaluru (Origin)")
                .stopType("ORIGIN")
                .distanceKm(0.0)
                .departureDate(travelDate)
                .departureTime("06:00")
                .consecutiveFare(450.0)
                .points(stop0Points)
                .build());

        // Stop 1: Kurnool
        List<SchedulePointDto> stop1Points = List.of(
                SchedulePointDto.builder().pointName("Kurnool Bus Stand").pointType("BOTH").pointBadge("Boarding & Dropping").arrivalDate(travelDate).arrivalTime("10:00").departureDate(travelDate).departureTime("10:20").haltMinutes(20).build(),
                SchedulePointDto.builder().pointName("Orvakal").pointType("BOTH").pointBadge("Boarding & Dropping").arrivalDate(travelDate).arrivalTime("10:45").departureDate(travelDate).departureTime("10:55").haltMinutes(10).build()
        );
        stops.add(ScheduleStopDto.builder()
                .stopOrder(1)
                .stopName("Kurnool")
                .stopType("INTERMEDIATE")
                .distanceKm(210.0)
                .arrivalDate(travelDate)
                .arrivalTime("10:00")
                .departureDate(travelDate)
                .departureTime("10:55")
                .haltMinutes(20)
                .consecutiveFare(350.0)
                .points(stop1Points)
                .build());

        // Stop 2: Anantapur
        List<SchedulePointDto> stop2Points = List.of(
                SchedulePointDto.builder().pointName("Anantapur Bus Stand").pointType("BOTH").pointBadge("Boarding & Dropping").arrivalDate(travelDate).arrivalTime("14:15").departureDate(travelDate).departureTime("14:35").haltMinutes(20).build(),
                SchedulePointDto.builder().pointName("Gooty").pointType("BOTH").pointBadge("Boarding & Dropping").arrivalDate(travelDate).arrivalTime("15:00").departureDate(travelDate).departureTime("15:10").haltMinutes(10).build()
        );
        stops.add(ScheduleStopDto.builder()
                .stopOrder(2)
                .stopName("Anantapur")
                .stopType("INTERMEDIATE")
                .distanceKm(320.0)
                .arrivalDate(travelDate)
                .arrivalTime("14:15")
                .departureDate(travelDate)
                .departureTime("15:10")
                .haltMinutes(20)
                .consecutiveFare(400.0)
                .points(stop2Points)
                .build());

        // Stop 3: Hyderabad (Destination)
        List<SchedulePointDto> stop3Points = List.of(
                SchedulePointDto.builder().pointName("MGBS (Jubilee Bus Station)").pointType("DROPPING").pointBadge("Dropping only").arrivalDate(arrivalDate).arrivalTime("01:00").build(),
                SchedulePointDto.builder().pointName("Uppal").pointType("DROPPING").pointBadge("Dropping only").arrivalDate(arrivalDate).arrivalTime("01:30").build()
        );
        stops.add(ScheduleStopDto.builder()
                .stopOrder(3)
                .stopName("Hyderabad (Destination)")
                .stopType("DESTINATION")
                .distanceKm(420.0)
                .arrivalDate(arrivalDate)
                .arrivalTime("01:30")
                .consecutiveFare(0.0)
                .points(stop3Points)
                .build());

        // Manual fare overrides (e.g. override Bengalore->Anantapur from 800 to 750, Bangalore->Hyderabad from 1200 to 1100)
        List<FareOverrideItemDto> overrides = List.of(
                FareOverrideItemDto.builder().fromStopOrder(0).toStopOrder(2).fromStopName("Bengaluru (Origin)").toStopName("Anantapur").customFare(750.0).build(),
                FareOverrideItemDto.builder().fromStopOrder(0).toStopOrder(3).fromStopName("Bengaluru (Origin)").toStopName("Hyderabad (Destination)").customFare(1100.0).build()
        );

        BusSchedule requestPayload = BusSchedule.builder()
                .busId(testBus.getId())
                .routeId(testRoute.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(travelDate)
                .departureTime("06:00")
                .arrivalDate(arrivalDate)
                .arrivalTime("01:30")
                .fare(1100.0)
                .availableSeats(40)
                .status("SCHEDULED")
                .stops(stops)
                .fareOverrides(overrides)
                .build();

        HttpEntity<BusSchedule> entity = new HttpEntity<>(requestPayload, authHeaders(token));
        ResponseEntity<BusSchedule> response = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                entity,
                BusSchedule.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        Long createdId = response.getBody().getId();
        assertNotNull(createdId);
        assertEquals("SCHEDULED", response.getBody().getStatus());
        assertTrue(response.getBody().getOrigin().contains("Bengaluru"));
        assertTrue(response.getBody().getDestination().contains("Hyderabad"));

        // 1. Verify seat inventory generation
        var seats = seatRepository.findByScheduleId(createdId);
        assertFalse(seats.isEmpty(), "Seat inventory should be generated for the created schedule");
        assertEquals(36, seats.size(), "Total seats generated should match bus capacity");

        // 2. Verify stops persistence
        ResponseEntity<ScheduleStopDto[]> stopsResponse = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + createdId + "/stops",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                ScheduleStopDto[].class
        );
        assertEquals(HttpStatus.OK, stopsResponse.getStatusCode());
        assertNotNull(stopsResponse.getBody());
        assertEquals(4, stopsResponse.getBody().length);
        assertEquals("Bengaluru (Origin)", stopsResponse.getBody()[0].getStopName());
        assertEquals(2, stopsResponse.getBody()[0].getPoints().size());

        // 3. Verify journey fare overrides persistence and query
        var fareMatrix = fareMatrixService.getFareMatrix(createdId);
        assertNotNull(fareMatrix);
        assertEquals(6, fareMatrix.getMatrix().size());

        var bgHyd = fareMatrix.getMatrix().stream()
                .filter(j -> j.getFromStopOrder() == 0 && j.getToStopOrder() == 3)
                .findFirst().orElse(null);
        assertNotNull(bgHyd);
        assertTrue(bgHyd.getIsOverridden());
        assertEquals(1100.0, bgHyd.getEffectiveFare());
        assertEquals(1200.0, bgHyd.getCalculatedFare());
    }

    @Test
    @DisplayName("Phase 6.5: Conflicting Schedule on Same Bus is Rejected with 409 Conflict")
    public void testScheduleCreation_TurnaroundConflict_Rejected() {
        String token = createToken(operatorId, "ROLE_BUS_OPERATOR", "operator201@traveleasy.com");

        LocalDate travelDate = LocalDate.of(2026, 10, 24);

        // Schedule 1: 06:00 to 12:00
        BusSchedule sched1 = BusSchedule.builder()
                .busId(testBus.getId())
                .origin("Bengaluru")
                .destination("Kurnool")
                .travelDate(travelDate)
                .departureTime("06:00")
                .arrivalDate(travelDate)
                .arrivalTime("12:00")
                .fare(500.0)
                .availableSeats(40)
                .build();
        restTemplate.postForEntity(getBaseUrl() + "/schedules", new HttpEntity<>(sched1, authHeaders(token)), BusSchedule.class);

        // Schedule 2: 12:30 departure (only 30 mins after 12:00 arrival, violates 60-min turnaround buffer)
        BusSchedule sched2 = BusSchedule.builder()
                .busId(testBus.getId())
                .origin("Kurnool")
                .destination("Hyderabad")
                .travelDate(travelDate)
                .departureTime("12:30")
                .arrivalDate(travelDate)
                .arrivalTime("16:00")
                .fare(400.0)
                .availableSeats(40)
                .build();

        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(sched2, authHeaders(token)),
                Map.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("buffer") ||
                response.getBody().get("message").toString().contains("turnaround") ||
                response.getBody().get("message").toString().contains("Conflict"));
    }

    @Test
    @DisplayName("Phase 6.5: Inactive Bus Rejection")
    public void testScheduleCreation_InactiveBus_Rejected() {
        String token = createToken(operatorId, "ROLE_BUS_OPERATOR", "operator201@traveleasy.com");

        testBus.setStatus("INACTIVE");
        busRepository.save(testBus);

        BusSchedule sched = BusSchedule.builder()
                .busId(testBus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.of(2026, 10, 26))
                .departureTime("06:00")
                .arrivalDate(LocalDate.of(2026, 10, 26))
                .arrivalTime("14:00")
                .fare(600.0)
                .build();

        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(sched, authHeaders(token)),
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("inactive"));
    }

    @Test
    @DisplayName("Phase 6.5: Unauthorized Operator Rejection (403 Forbidden)")
    public void testScheduleCreation_CrossOperator_Forbidden() {
        Long otherOperatorId = 999L;
        String otherToken = createToken(otherOperatorId, "ROLE_BUS_OPERATOR", "other@traveleasy.com");

        BusSchedule sched = BusSchedule.builder()
                .busId(testBus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.of(2026, 10, 27))
                .departureTime("06:00")
                .arrivalDate(LocalDate.of(2026, 10, 27))
                .arrivalTime("14:00")
                .fare(600.0)
                .build();

        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(sched, authHeaders(otherToken)),
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("Phase 6.5 BugFix: Destination Stop Arrival Synchronization With Configured Dropping Points")
    public void testScheduleCreation_DestinationStopArrivalSynchronization() {
        String token = createToken(operatorId, "ROLE_BUS_OPERATOR", "operator201@traveleasy.com");
        LocalDate travelDate = LocalDate.of(2026, 10, 31);
        LocalDate arrivalDate = LocalDate.of(2026, 11, 1);

        // Case 1: Exact user bug scenario - destination dropping points configured at 01:30 AM cross-midnight
        List<ScheduleStopDto> stops = new ArrayList<>();
        stops.add(ScheduleStopDto.builder()
                .stopOrder(0).stopName("Bengaluru (Origin)").stopType("ORIGIN")
                .distanceKm(0.0).departureDate(travelDate).departureTime("06:00").consecutiveFare(450.0)
                .points(List.of(
                        SchedulePointDto.builder().pointName("Majestic Bus Stand").pointType("BOARDING").departureDate(travelDate).departureTime("06:00").build(),
                        SchedulePointDto.builder().pointName("Yeshwanthpur").pointType("BOARDING").departureDate(travelDate).departureTime("06:30").build()
                )).build());

        stops.add(ScheduleStopDto.builder()
                .stopOrder(1).stopName("Kurnool").stopType("INTERMEDIATE")
                .distanceKm(210.0).arrivalDate(travelDate).arrivalTime("10:00").departureDate(travelDate).departureTime("10:20").haltMinutes(20).consecutiveFare(350.0)
                .points(List.of(
                        SchedulePointDto.builder().pointName("Kurnool Bus Stand").pointType("BOTH").arrivalDate(travelDate).arrivalTime("10:00").departureDate(travelDate).departureTime("10:20").haltMinutes(20).build(),
                        SchedulePointDto.builder().pointName("Orvakal").pointType("BOTH").arrivalDate(travelDate).arrivalTime("10:20").departureDate(travelDate).departureTime("10:40").haltMinutes(20).build()
                )).build());

        stops.add(ScheduleStopDto.builder()
                .stopOrder(2).stopName("Anantapur").stopType("INTERMEDIATE")
                .distanceKm(320.0).arrivalDate(travelDate).arrivalTime("12:00").departureDate(travelDate).departureTime("12:20").haltMinutes(20).consecutiveFare(400.0)
                .points(List.of(
                        SchedulePointDto.builder().pointName("Anantapur Bus Stand").pointType("BOTH").arrivalDate(travelDate).arrivalTime("12:00").departureDate(travelDate).departureTime("12:20").haltMinutes(20).build(),
                        SchedulePointDto.builder().pointName("Gooty").pointType("BOTH").arrivalDate(travelDate).arrivalTime("12:20").departureDate(travelDate).departureTime("12:40").haltMinutes(20).build()
                )).build());

        stops.add(ScheduleStopDto.builder()
                .stopOrder(3).stopName("Hyderabad (Destination)").stopType("DESTINATION")
                .distanceKm(420.0).arrivalDate(arrivalDate).arrivalTime("01:30").consecutiveFare(0.0)
                .points(List.of(
                        SchedulePointDto.builder().pointName("MGBS (Jubilee Bus Station)").pointType("DROPPING").arrivalDate(arrivalDate).arrivalTime("01:30").build(),
                        SchedulePointDto.builder().pointName("Uppal").pointType("DROPPING").arrivalDate(arrivalDate).arrivalTime("01:30").build()
                )).build());

        BusSchedule sched = BusSchedule.builder()
                .busId(testBus.getId())
                .routeId(testRoute.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(travelDate)
                .departureTime("06:00")
                .arrivalDate(arrivalDate)
                .arrivalTime("01:30")
                .fare(1200.0)
                .availableSeats(40)
                .status("SCHEDULED")
                .stops(stops)
                .build();

        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(sched, authHeaders(token)),
                Map.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SCHEDULED", response.getBody().get("status"));
        assertEquals("01:30", response.getBody().get("arrivalTime"));

        // Case 2: Destination stop arrival omitted in stop payload, backend synchronizes with trip arrival
        LocalDate travelDate2 = LocalDate.of(2026, 11, 10);
        LocalDate arrivalDate2 = LocalDate.of(2026, 11, 11);

        List<ScheduleStopDto> stopsWithNullArrival = new ArrayList<>();
        stopsWithNullArrival.add(ScheduleStopDto.builder()
                .stopOrder(0).stopName("Bengaluru (Origin)").stopType("ORIGIN")
                .distanceKm(0.0).departureDate(travelDate2).departureTime("06:00").consecutiveFare(450.0).build());
        stopsWithNullArrival.add(ScheduleStopDto.builder()
                .stopOrder(1).stopName("Kurnool").stopType("INTERMEDIATE")
                .distanceKm(210.0).arrivalDate(travelDate2).arrivalTime("10:00").departureDate(travelDate2).departureTime("10:20").haltMinutes(20).consecutiveFare(350.0).build());
        stopsWithNullArrival.add(ScheduleStopDto.builder()
                .stopOrder(2).stopName("Anantapur").stopType("INTERMEDIATE")
                .distanceKm(320.0).arrivalDate(travelDate2).arrivalTime("12:00").departureDate(travelDate2).departureTime("12:20").haltMinutes(20).consecutiveFare(400.0).build());
        stopsWithNullArrival.add(ScheduleStopDto.builder()
                .stopOrder(3).stopName("Hyderabad (Destination)").stopType("DESTINATION")
                .distanceKm(420.0).arrivalDate(null).arrivalTime(null).consecutiveFare(0.0).build());

        BusSchedule sched2 = BusSchedule.builder()
                .busId(testBus.getId())
                .routeId(testRoute.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(travelDate2)
                .departureTime("06:00")
                .arrivalDate(arrivalDate2)
                .arrivalTime("01:30")
                .fare(1200.0)
                .availableSeats(40)
                .status("SCHEDULED")
                .stops(stopsWithNullArrival)
                .build();

        ResponseEntity<Map> response2 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(sched2, authHeaders(token)),
                Map.class
        );

        assertEquals(HttpStatus.CREATED, response2.getStatusCode());
        assertNotNull(response2.getBody());

        // Case 3: Route has canonical stop names ("Bengaluru", "Hyderabad"), caller sends UI formatted names ("Bengaluru (Start)", "Hyderabad (End)")
        LocalDate travelDate3 = LocalDate.of(2026, 11, 20);
        LocalDate arrivalDate3 = LocalDate.of(2026, 11, 21);

        Route routeWithCleanNames = routeRepository.save(Route.builder()
                .operatorId(operatorId)
                .routeCode("RTE-CLEAN-" + System.currentTimeMillis())
                .routeName("Bengaluru - Hyderabad Direct")
                .origin("Bengaluru")
                .destination("Hyderabad")
                .status("ACTIVE")
                .build());

        routeStopRepository.save(RouteStop.builder().routeId(routeWithCleanNames.getId()).stopOrder(0).stopName("Bengaluru").stopType("ORIGIN").distanceKm(0.0).build());
        routeStopRepository.save(RouteStop.builder().routeId(routeWithCleanNames.getId()).stopOrder(1).stopName("Hyderabad").stopType("DESTINATION").distanceKm(420.0).build());

        List<ScheduleStopDto> stopsWithUiNames = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Bengaluru (Start)").stopType("ORIGIN").distanceKm(0.0).departureDate(travelDate3).departureTime("06:00").consecutiveFare(450.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Hyderabad (End)").stopType("DESTINATION").distanceKm(420.0).arrivalDate(arrivalDate3).arrivalTime("01:30").consecutiveFare(0.0).build()
        );

        BusSchedule sched3 = BusSchedule.builder()
                .busId(testBus.getId())
                .routeId(routeWithCleanNames.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(travelDate3)
                .departureTime("06:00")
                .arrivalDate(arrivalDate3)
                .arrivalTime("01:30")
                .fare(450.0)
                .availableSeats(40)
                .status("SCHEDULED")
                .stops(stopsWithUiNames)
                .build();

        ResponseEntity<Map> response3 = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(sched3, authHeaders(token)),
                Map.class
        );

        assertEquals(HttpStatus.CREATED, response3.getStatusCode());
        assertNotNull(response3.getBody());
    }
}
