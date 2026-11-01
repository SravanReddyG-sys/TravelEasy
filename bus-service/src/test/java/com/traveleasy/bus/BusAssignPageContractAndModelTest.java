package com.traveleasy.bus;

import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.Route;
import com.traveleasy.bus.model.Seat;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.RouteRepository;
import com.traveleasy.bus.repository.SeatRepository;
import com.traveleasy.bus.dto.ScheduleConflictCheckRequest;
import com.traveleasy.bus.dto.ScheduleConflictCheckResponse;
import com.traveleasy.bus.service.ScheduleService;
import com.traveleasy.bus.service.SeatLayoutService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Key;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BusAssignPageContractAndModelTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private BusScheduleRepository scheduleRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private com.traveleasy.bus.repository.RouteStopRepository routeStopRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private SeatLayoutService seatLayoutService;

    private static final String SECRET_STRING = "TravelEasySuperSecretKeyForJWTSigning12345678901234567890";
    private static final Key SIGNING_KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes());

    private String createTestToken(Long userId, String role, String username) {
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(SIGNING_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("BUS-ASSIGN-01: Bus entity persists and retrieves extended attributes cleanly")
    public void testExtendedBusAttributesPersistence() {
        String uniqueReg = "KA-01-EXP-" + UUID.randomUUID().toString().substring(0, 6);
        Bus bus = Bus.builder()
                .operatorId(2001L)
                .operatorName("Test Express Travels")
                .busNumber(uniqueReg)
                .busName("Test Luxury Liner")
                .busType("AC Sleeper")
                .totalSeats(36)
                .facilityType("AC")
                .busConfiguration(SeatLayoutService.CONFIG_DOUBLE_DECK)
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SLEEPER)
                .status("ACTIVE")
                .manufacturer("Volvo")
                .model("9600")
                .yearOfManufacturing(2023)
                .fuelType("Diesel")
                .amenities("AC, Wi-Fi, Charging, Blanket")
                .imageUrl("assets/images/travel-easy-bus.png")
                .build();

        Bus saved = busRepository.save(bus);
        assertNotNull(saved.getId());

        Bus retrieved = busRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Test Luxury Liner", retrieved.getBusName());
        assertEquals("Volvo", retrieved.getManufacturer());
        assertEquals("9600", retrieved.getModel());
        assertEquals(2023, retrieved.getYearOfManufacturing());
        assertEquals("Diesel", retrieved.getFuelType());
        assertEquals("assets/images/travel-easy-bus.png", retrieved.getImageUrl());
        assertEquals("ACTIVE", retrieved.getStatus());
    }

    @Test
    @DisplayName("BUS-ASSIGN-02: GET /api/buses/operator/{id} returns extended bus attributes")
    public void testOperatorEndpointReturnsExtendedAttributes() {
        Long operatorId = 2002L;
        String uniqueReg = "KA-02-API-" + UUID.randomUUID().toString().substring(0, 6);
        Bus bus = Bus.builder()
                .operatorId(operatorId)
                .operatorName("API Express")
                .busNumber(uniqueReg)
                .busName("API Premier Cruiser")
                .busType("AC Seater")
                .totalSeats(40)
                .facilityType("AC")
                .busConfiguration(SeatLayoutService.CONFIG_SINGLE_DECK)
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_2_SEATER)
                .status("ACTIVE")
                .manufacturer("Scania")
                .model("Metrolink")
                .yearOfManufacturing(2022)
                .fuelType("Diesel")
                .amenities("AC, Charging, Water Bottle")
                .imageUrl("assets/images/travel-easy-bus.png")
                .build();
        busRepository.save(bus);

        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "operator2002");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = "http://localhost:" + port + "/api/buses/operator/" + operatorId;
        ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() >= 1);

        Map<?, ?> foundBus = (Map<?, ?>) response.getBody().stream()
                .filter(b -> uniqueReg.equals(((Map<?, ?>) b).get("busNumber")))
                .findFirst()
                .orElse(null);

        assertNotNull(foundBus);
        assertEquals("API Premier Cruiser", foundBus.get("busName"));
        assertEquals("Scania", foundBus.get("manufacturer"));
        assertEquals("Metrolink", foundBus.get("model"));
        assertEquals(2022, foundBus.get("yearOfManufacturing"));
        assertEquals("Diesel", foundBus.get("fuelType"));
        assertEquals("ACTIVE", foundBus.get("status"));
    }

    @Test
    @DisplayName("BUS-ASSIGN-03: Inactive bus cannot be scheduled and throws ResponseStatusException")
    public void testInactiveBusCannotBeScheduled() {
        Long operatorId = 2003L;
        Bus inactiveBus = Bus.builder()
                .operatorId(operatorId)
                .operatorName("Inactive Fleet")
                .busNumber("KA-03-INACT-" + UUID.randomUUID().toString().substring(0, 4))
                .busType("Non-AC Seater")
                .totalSeats(30)
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER)
                .status("INACTIVE")
                .build();
        Bus savedBus = busRepository.save(inactiveBus);

        Route route = Route.builder()
                .operatorId(operatorId)
                .routeName("Inactive Bus Test Route")
                .routeCode("RTE-INACT-" + UUID.randomUUID().toString().substring(0, 4))
                .origin("Bengaluru")
                .destination("Hyderabad")
                .status("ACTIVE")
                .build();
        Route savedRoute = routeRepository.save(route);

        BusSchedule req = BusSchedule.builder()
                .busId(savedBus.getId())
                .routeId(savedRoute.getId())
                .travelDate(LocalDate.now().plusDays(5))
                .departureTime("10:00")
                .arrivalTime("18:00")
                .fare(600.0)
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            scheduleService.createSchedule(operatorId, false, req);
        });

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Cannot schedule trips on an inactive bus"));
    }

    @Test
    @DisplayName("BUS-ASSIGN-04: Active bus schedule creation populates physical trip seat inventory")
    public void testActiveBusScheduleGeneratesTripInventory() {
        Long operatorId = 2004L;
        Bus activeBus = Bus.builder()
                .operatorId(operatorId)
                .operatorName("Active Fleet")
                .busNumber("KA-04-ACT-" + UUID.randomUUID().toString().substring(0, 4))
                .busType("AC Sleeper")
                .totalSeats(36)
                .facilityType("AC")
                .busConfiguration(SeatLayoutService.CONFIG_DOUBLE_DECK)
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SLEEPER)
                .status("ACTIVE")
                .build();
        Bus savedBus = busRepository.save(activeBus);

        Route route = Route.builder()
                .operatorId(operatorId)
                .routeName("Active Bus Test Route")
                .routeCode("RTE-ACT-" + UUID.randomUUID().toString().substring(0, 4))
                .origin("Bengaluru")
                .destination("Chennai")
                .status("ACTIVE")
                .build();
        Route savedRoute = routeRepository.save(route);

        com.traveleasy.bus.model.RouteStop originStop = com.traveleasy.bus.model.RouteStop.builder()
                .routeId(savedRoute.getId())
                .stopName("Bengaluru")
                .stopType("ORIGIN")
                .stopOrder(0)
                .distanceKm(0.0)
                .build();
        routeStopRepository.save(originStop);

        com.traveleasy.bus.model.RouteStop destStop = com.traveleasy.bus.model.RouteStop.builder()
                .routeId(savedRoute.getId())
                .stopName("Chennai")
                .stopType("DESTINATION")
                .stopOrder(1)
                .distanceKm(350.0)
                .build();
        routeStopRepository.save(destStop);

        BusSchedule req = BusSchedule.builder()
                .busId(savedBus.getId())
                .routeId(savedRoute.getId())
                .travelDate(LocalDate.now().plusDays(6))
                .departureTime("21:00")
                .arrivalTime("06:00")
                .fare(850.0)
                .build();

        BusSchedule schedule = scheduleService.createSchedule(operatorId, false, req);
        assertNotNull(schedule.getId());
        assertEquals(36, schedule.getAvailableSeats());

        List<Seat> generatedSeats = seatRepository.findByScheduleId(schedule.getId());
        assertEquals(36, generatedSeats.size());
        assertTrue(generatedSeats.stream().anyMatch(s -> "LOWER".equalsIgnoreCase(s.getDeck())));
        assertTrue(generatedSeats.stream().anyMatch(s -> "UPPER".equalsIgnoreCase(s.getDeck())));
    }

    @Test
    @DisplayName("Verify check-conflict endpoint detects bus scheduling overlap including turnaround buffer")
    public void testCheckScheduleConflictEndpoint() {
        Long operatorId = 2002L;
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        Bus bus = Bus.builder()
                .operatorId(operatorId)
                .busNumber("KA" + uniqueSuffix)
                .busName("Conflict Test Bus")
                .busType("AC Sleeper")
                .totalSeats(40)
                .status("ACTIVE")
                .build();
        Bus savedBus = busRepository.save(bus);

        Route route = Route.builder()
                .operatorId(operatorId)
                .routeCode("RTE-CONF-" + uniqueSuffix)
                .routeName("Conflict Test Route")
                .origin("Hyderabad")
                .destination("Chennai")
                .status("ACTIVE")
                .build();
        Route savedRoute = routeRepository.save(route);

        com.traveleasy.bus.model.RouteStop originStop = com.traveleasy.bus.model.RouteStop.builder()
                .routeId(savedRoute.getId())
                .stopName("Hyderabad")
                .stopType("ORIGIN")
                .stopOrder(0)
                .distanceKm(0.0)
                .build();
        routeStopRepository.save(originStop);

        com.traveleasy.bus.model.RouteStop destStop = com.traveleasy.bus.model.RouteStop.builder()
                .routeId(savedRoute.getId())
                .stopName("Chennai")
                .stopType("DESTINATION")
                .stopOrder(1)
                .distanceKm(650.0)
                .build();
        routeStopRepository.save(destStop);

        // Create an existing schedule: baseDate 13:00 to baseDate+1 08:00
        LocalDate baseDate = LocalDate.now().plusDays(2);
        BusSchedule existingSched = BusSchedule.builder()
                .busId(savedBus.getId())
                .routeId(savedRoute.getId())
                .travelDate(baseDate)
                .departureTime("13:00")
                .arrivalDate(baseDate.plusDays(1))
                .arrivalTime("08:00")
                .fare(900.0)
                .status("SCHEDULED")
                .build();
        BusSchedule created = scheduleService.createSchedule(operatorId, false, existingSched);
        assertNotNull(created.getId());

        // 1. Check overlapping candidate: baseDate 06:00 to baseDate+1 01:30
        ScheduleConflictCheckRequest overlapReq = ScheduleConflictCheckRequest.builder()
                .busId(savedBus.getId())
                .travelDate(baseDate)
                .departureTime("06:00")
                .arrivalDate(baseDate.plusDays(1))
                .arrivalTime("01:30")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + createTestToken(operatorId, "ROLE_BUS_OPERATOR", "test-operator"));

        HttpEntity<ScheduleConflictCheckRequest> entity = new HttpEntity<>(overlapReq, headers);
        ResponseEntity<ScheduleConflictCheckResponse> overlapResp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/buses/schedules/check-conflict",
                entity,
                ScheduleConflictCheckResponse.class
        );

        assertEquals(HttpStatus.OK, overlapResp.getStatusCode());
        assertNotNull(overlapResp.getBody());
        assertTrue(overlapResp.getBody().isHasConflict());
        assertEquals(created.getId(), overlapResp.getBody().getConflictingScheduleId());
        assertTrue(overlapResp.getBody().getMessage().contains("already has a schedule"));

        // 2. Check non-overlapping candidate two days after baseDate
        LocalDate noConflictDate = baseDate.plusDays(2);
        ScheduleConflictCheckRequest noConflictReq = ScheduleConflictCheckRequest.builder()
                .busId(savedBus.getId())
                .travelDate(noConflictDate)
                .departureTime("10:00")
                .arrivalDate(noConflictDate)
                .arrivalTime("16:00")
                .build();

        HttpEntity<ScheduleConflictCheckRequest> noConflictEntity = new HttpEntity<>(noConflictReq, headers);
        ResponseEntity<ScheduleConflictCheckResponse> noConflictResp = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/buses/schedules/check-conflict",
                noConflictEntity,
                ScheduleConflictCheckResponse.class
        );

        assertEquals(HttpStatus.OK, noConflictResp.getStatusCode());
        assertNotNull(noConflictResp.getBody());
        assertFalse(noConflictResp.getBody().isHasConflict());
    }
}
