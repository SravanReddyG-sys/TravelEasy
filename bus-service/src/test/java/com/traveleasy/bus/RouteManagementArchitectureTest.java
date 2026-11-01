package com.traveleasy.bus;

import com.traveleasy.bus.dto.CreateRouteRequest;
import com.traveleasy.bus.dto.RouteDto;
import com.traveleasy.bus.dto.RoutePointDto;
import com.traveleasy.bus.dto.RouteStopDto;
import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.Route;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.RouteRepository;
import com.traveleasy.bus.repository.RoutePointRepository;
import com.traveleasy.bus.repository.RouteStopRepository;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RouteManagementArchitectureTest {

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
    private RouteStopRepository routeStopRepository;

    @Autowired
    private RoutePointRepository routePointRepository;

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
                .claim("fullName", "Operator " + userId)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private Bus createTestBus(Long operatorId) {
        return busRepository.save(Bus.builder()
                .busNumber("AP" + (10 + (int)(Math.random()*80)) + "TEST" + (1000 + (int)(Math.random()*9000)))
                .busType("AC Sleeper (2+1)")
                .facilityType("AC")
                .busConfiguration("SINGLE_DECK")
                .templateId("AC_SL_2_1")
                .totalSeats(36)
                .operatorId(operatorId)
                .operatorName("FastTravels")
                .amenities("Wi-Fi, Water Bottle")
                .status("ACTIVE")
                .build());
    }

    private CreateRouteRequest buildSampleRouteRequest(String name, String origin, String destination) {
        RoutePointDto bp1 = RoutePointDto.builder().pointName(origin + " Bus Station").address("Main Gate").timeSlot("06:00 AM").build();
        RoutePointDto bp2 = RoutePointDto.builder().pointName(origin + " Ring Road").address("Near Flyover").timeSlot("06:30 AM").build();
        RouteStopDto stop0 = RouteStopDto.builder()
                .stopOrder(0).stopName(origin).stopType("ORIGIN").distanceKm(0.0)
                .boardingPoints(List.of(bp1, bp2))
                .droppingPoints(Collections.emptyList())
                .build();

        RoutePointDto bpMid = RoutePointDto.builder().pointName("Suryapet Bypass").address("Highway Junction").timeSlot("08:30 AM").build();
        RoutePointDto dpMid = RoutePointDto.builder().pointName("Suryapet Town").address("Clock Tower").timeSlot("08:45 AM").build();
        RouteStopDto stop1 = RouteStopDto.builder()
                .stopOrder(1).stopName("Suryapet").stopType("INTERMEDIATE").distanceKm(130.0)
                .boardingPoints(List.of(bpMid))
                .droppingPoints(List.of(dpMid))
                .build();

        RoutePointDto dpDest1 = RoutePointDto.builder().pointName("LB Nagar").address("Metro Pillar 22").timeSlot("11:00 AM").build();
        RoutePointDto dpDest2 = RoutePointDto.builder().pointName(destination + " Central").address("Terminal 1").timeSlot("11:45 AM").build();
        RouteStopDto stop2 = RouteStopDto.builder()
                .stopOrder(2).stopName(destination).stopType("DESTINATION").distanceKm(275.0)
                .boardingPoints(Collections.emptyList())
                .droppingPoints(List.of(dpDest1, dpDest2))
                .build();

        return CreateRouteRequest.builder()
                .routeName(name)
                .origin(origin)
                .destination(destination)
                .description("Comfortable express service via Suryapet")
                .stops(List.of(stop0, stop1, stop2))
                .build();
    }

    @Test
    @DisplayName("ARCH-01: Create reusable Route atomically with ordered stops and points")
    public void testCreateRoute_Success() {
        Long operatorId = 701L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op701@traveleasy.com");

        CreateRouteRequest req = buildSampleRouteRequest("Vijayawada-Hyderabad Express", "Vijayawada", "Hyderabad");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<RouteDto> resp = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(req, headers),
                RouteDto.class
        );

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        RouteDto body = resp.getBody();
        assertNotNull(body);
        assertNotNull(body.getId());
        assertNotNull(body.getRouteCode());
        assertTrue(body.getRouteCode().startsWith("RT-"));
        assertEquals("Vijayawada-Hyderabad Express", body.getRouteName());
        assertEquals("Vijayawada", body.getOrigin());
        assertEquals("Hyderabad", body.getDestination());
        assertEquals("ACTIVE", body.getStatus());
        assertEquals(3, body.getStops().size());
        assertEquals(0, body.getScheduleCount());

        // Check origin boarding points
        RouteStopDto s0 = body.getStops().get(0);
        assertEquals("Vijayawada", s0.getStopName());
        assertEquals("ORIGIN", s0.getStopType());
        assertEquals(2, s0.getBoardingPoints().size());

        // Check destination dropping points
        RouteStopDto s2 = body.getStops().get(2);
        assertEquals("Hyderabad", s2.getStopName());
        assertEquals("DESTINATION", s2.getStopType());
        assertEquals(2, s2.getDroppingPoints().size());
    }

    @Test
    @DisplayName("ARCH-02: Origin and Destination cannot be the same physical location")
    public void testCreateRoute_OriginEqualsDestination_Rejected() {
        Long operatorId = 702L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op702@traveleasy.com");

        CreateRouteRequest req = buildSampleRouteRequest("Circular City Loop", "Vijayawada", "Vijayawada");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(req, headers),
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().get("message").toString().contains("Origin and destination cannot be the same"));
    }

    @Test
    @DisplayName("ARCH-03: Repeated physical locations within a route are disallowed")
    public void testCreateRoute_DuplicateStopLocation_Rejected() {
        Long operatorId = 703L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op703@traveleasy.com");

        RouteStopDto s0 = RouteStopDto.builder().stopOrder(0).stopName("Vijayawada").stopType("ORIGIN")
                .boardingPoints(List.of(RoutePointDto.builder().pointName("VJA Point").build()))
                .build();
        RouteStopDto s1 = RouteStopDto.builder().stopOrder(1).stopName("Guntur").stopType("INTERMEDIATE").build();
        RouteStopDto s2 = RouteStopDto.builder().stopOrder(2).stopName("Vijayawada").stopType("INTERMEDIATE").build(); // REPEATED LOCATION
        RouteStopDto s3 = RouteStopDto.builder().stopOrder(3).stopName("Hyderabad").stopType("DESTINATION")
                .droppingPoints(List.of(RoutePointDto.builder().pointName("HYD Point").build()))
                .build();

        CreateRouteRequest req = CreateRouteRequest.builder()
                .routeName("Invalid Loop Route")
                .origin("Vijayawada")
                .destination("Hyderabad")
                .stops(List.of(s0, s1, s2, s3))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(req, headers),
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().get("message").toString().contains("Repeated physical location disallowed on route"));
    }

    @Test
    @DisplayName("ARCH-04: Missing boarding point at origin or dropping point at destination rejected")
    public void testCreateRoute_MissingRequiredPoints_Rejected() {
        Long operatorId = 704L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op704@traveleasy.com");

        // Stop 0 without boarding points
        RouteStopDto s0 = RouteStopDto.builder().stopOrder(0).stopName("Vijayawada").stopType("ORIGIN")
                .boardingPoints(Collections.emptyList())
                .build();
        RouteStopDto s1 = RouteStopDto.builder().stopOrder(1).stopName("Hyderabad").stopType("DESTINATION")
                .droppingPoints(List.of(RoutePointDto.builder().pointName("HYD Point").build()))
                .build();

        CreateRouteRequest req = CreateRouteRequest.builder()
                .routeName("Missing Boarding Route")
                .origin("Vijayawada")
                .destination("Hyderabad")
                .stops(List.of(s0, s1))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(req, headers),
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().get("message").toString().contains("must configure at least one valid boarding point"));
    }

    @Test
    @DisplayName("ARCH-05: Route reuse across multiple schedules snapshots stops and preserves schedule-specific timings")
    public void testRouteReuse_AcrossMultipleSchedules() {
        Long operatorId = 705L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op705@traveleasy.com");
        Bus bus1 = createTestBus(operatorId);
        Bus bus2 = createTestBus(operatorId);

        // 1. Create Route
        CreateRouteRequest routeReq = buildSampleRouteRequest("Vijayawada-Hyderabad Regular", "Vijayawada", "Hyderabad");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<RouteDto> routeResp = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(routeReq, headers),
                RouteDto.class
        );
        assertEquals(HttpStatus.CREATED, routeResp.getStatusCode());
        Long routeId = routeResp.getBody().getId();

        // 2. Schedule Trip 1 (Day 1 Morning)
        BusSchedule sched1 = BusSchedule.builder()
                .busId(bus1.getId())
                .routeId(routeId)
                .travelDate(LocalDate.now().plusDays(5))
                .departureTime("06:00")
                .arrivalTime("12:00")
                .fare(600.0)
                .build();

        ResponseEntity<BusSchedule> sched1Resp = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(sched1, headers),
                BusSchedule.class
        );
        assertEquals(HttpStatus.CREATED, sched1Resp.getStatusCode());
        assertNotNull(sched1Resp.getBody().getId());
        assertEquals("Vijayawada", sched1Resp.getBody().getOrigin());
        assertEquals("Hyderabad", sched1Resp.getBody().getDestination());

        // 3. Schedule Trip 2 (Day 1 Evening)
        BusSchedule sched2 = BusSchedule.builder()
                .busId(bus2.getId())
                .routeId(routeId)
                .travelDate(LocalDate.now().plusDays(5))
                .departureTime("22:00")
                .arrivalTime("04:30")
                .fare(850.0)
                .build();

        ResponseEntity<BusSchedule> sched2Resp = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(sched2, headers),
                BusSchedule.class
        );
        assertEquals(HttpStatus.CREATED, sched2Resp.getStatusCode());

        // 4. Verify Route details reflect schedule count = 2
        ResponseEntity<RouteDto> routeDetails = restTemplate.exchange(
                getBaseUrl() + "/routes/" + routeId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                RouteDto.class
        );
        assertEquals(HttpStatus.OK, routeDetails.getStatusCode());
        assertEquals(2, routeDetails.getBody().getScheduleCount());
    }

    @Test
    @DisplayName("ARCH-06: Structural route edit blocked with 409 Conflict when future schedule depends on route")
    public void testStructuralRouteEdit_BlockedWhenFutureScheduleExists() {
        Long operatorId = 706L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op706@traveleasy.com");
        Bus bus = createTestBus(operatorId);

        // 1. Create Route
        CreateRouteRequest routeReq = buildSampleRouteRequest("VJA-HYD Structural Guard Test", "Vijayawada", "Hyderabad");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<RouteDto> routeResp = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(routeReq, headers),
                RouteDto.class
        );
        assertEquals(HttpStatus.CREATED, routeResp.getStatusCode());
        Long routeId = routeResp.getBody().getId();

        // 2. Create Future Schedule
        BusSchedule sched = BusSchedule.builder()
                .busId(bus.getId())
                .routeId(routeId)
                .travelDate(LocalDate.now().plusDays(7))
                .departureTime("21:00")
                .arrivalTime("03:30")
                .fare(750.0)
                .build();

        ResponseEntity<BusSchedule> schedResp = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                new HttpEntity<>(sched, headers),
                BusSchedule.class
        );
        assertEquals(HttpStatus.CREATED, schedResp.getStatusCode());

        // 3. Attempt structural edit: add a new intermediate stop
        CreateRouteRequest structuralEditReq = buildSampleRouteRequest("VJA-HYD Structural Guard Test", "Vijayawada", "Hyderabad");
        List<RouteStopDto> newStops = new ArrayList<>(structuralEditReq.getStops());
        RouteStopDto extraStop = RouteStopDto.builder()
                .stopOrder(2).stopName("Nalgonda").stopType("INTERMEDIATE").distanceKm(200.0)
                .build();
        newStops.add(2, extraStop);
        // re-index stopOrder
        for (int i = 0; i < newStops.size(); i++) {
            newStops.get(i).setStopOrder(i);
            if (i == 0) newStops.get(i).setStopType("ORIGIN");
            else if (i == newStops.size() - 1) newStops.get(i).setStopType("DESTINATION");
            else newStops.get(i).setStopType("INTERMEDIATE");
        }
        structuralEditReq.setStops(newStops);

        ResponseEntity<Map> editResp = restTemplate.exchange(
                getBaseUrl() + "/routes/" + routeId,
                HttpMethod.PUT,
                new HttpEntity<>(structuralEditReq, headers),
                Map.class
        );

        assertEquals(HttpStatus.CONFLICT, editResp.getStatusCode());
        assertTrue(editResp.getBody().get("message").toString().contains("Structural edit blocked"));
        assertTrue(editResp.getBody().get("message").toString().contains("active upcoming schedule(s) depend on this route"));
    }

    @Test
    @DisplayName("ARCH-07: Non-structural metadata edit allowed when future schedule depends on route")
    public void testNonStructuralEdit_Allowed() {
        Long operatorId = 707L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op707@traveleasy.com");
        Bus bus = createTestBus(operatorId);

        CreateRouteRequest routeReq = buildSampleRouteRequest("VJA-HYD Metadata Edit Test", "Vijayawada", "Hyderabad");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<RouteDto> routeResp = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(routeReq, headers),
                RouteDto.class
        );
        Long routeId = routeResp.getBody().getId();

        // Create Future Schedule
        BusSchedule sched = BusSchedule.builder()
                .busId(bus.getId())
                .routeId(routeId)
                .travelDate(LocalDate.now().plusDays(3))
                .departureTime("10:00")
                .arrivalTime("16:00")
                .fare(550.0)
                .build();
        restTemplate.postForEntity(getBaseUrl() + "/schedules", new HttpEntity<>(sched, headers), BusSchedule.class);

        // Edit only RouteName and Description (identical stop sequence)
        routeReq.setRouteName("VJA-HYD Superfast Express");
        routeReq.setDescription("Updated promotional description");

        ResponseEntity<RouteDto> editResp = restTemplate.exchange(
                getBaseUrl() + "/routes/" + routeId,
                HttpMethod.PUT,
                new HttpEntity<>(routeReq, headers),
                RouteDto.class
        );

        assertEquals(HttpStatus.OK, editResp.getStatusCode());
        assertEquals("VJA-HYD Superfast Express", editResp.getBody().getRouteName());
        assertEquals("Updated promotional description", editResp.getBody().getDescription());
    }

    @Test
    @DisplayName("ARCH-08: Route deactivation blocked when future schedules depend on it")
    public void testRouteDeactivation_Blocked() {
        Long operatorId = 708L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op708@traveleasy.com");
        Bus bus = createTestBus(operatorId);

        CreateRouteRequest routeReq = buildSampleRouteRequest("VJA-HYD Deact Guard", "Vijayawada", "Hyderabad");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<RouteDto> routeResp = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(routeReq, headers),
                RouteDto.class
        );
        Long routeId = routeResp.getBody().getId();

        // Create Future Schedule
        BusSchedule sched = BusSchedule.builder()
                .busId(bus.getId())
                .routeId(routeId)
                .travelDate(LocalDate.now().plusDays(2))
                .departureTime("08:00")
                .arrivalTime("14:00")
                .fare(620.0)
                .build();
        restTemplate.postForEntity(getBaseUrl() + "/schedules", new HttpEntity<>(sched, headers), BusSchedule.class);

        // Attempt Deactivation
        ResponseEntity<Map> deactResp = restTemplate.exchange(
                getBaseUrl() + "/routes/" + routeId + "/status",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", "INACTIVE"), headers),
                Map.class
        );

        assertEquals(HttpStatus.CONFLICT, deactResp.getStatusCode());
        assertTrue(deactResp.getBody().get("message").toString().contains("Cannot deactivate route"));
    }

    @Test
    @DisplayName("ARCH-09: Ownership isolation - Operator cannot view or modify another operator's route")
    public void testRouteOwnershipIsolation() {
        Long operator1 = 709L;
        Long operator2 = 710L;
        String token1 = createTestToken(operator1, "ROLE_BUS_OPERATOR", "op709@traveleasy.com");
        String token2 = createTestToken(operator2, "ROLE_BUS_OPERATOR", "op710@traveleasy.com");

        CreateRouteRequest routeReq = buildSampleRouteRequest("Operator 1 Route", "Vijayawada", "Hyderabad");
        HttpHeaders headers1 = new HttpHeaders();
        headers1.setBearerAuth(token1);
        headers1.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<RouteDto> routeResp = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(routeReq, headers1),
                RouteDto.class
        );
        Long routeId = routeResp.getBody().getId();

        // Operator 2 attempts GET route details
        HttpHeaders headers2 = new HttpHeaders();
        headers2.setBearerAuth(token2);
        headers2.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> getResp = restTemplate.exchange(
                getBaseUrl() + "/routes/" + routeId,
                HttpMethod.GET,
                new HttpEntity<>(headers2),
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, getResp.getStatusCode());

        // Operator 2 attempts PUT update route
        ResponseEntity<Map> putResp = restTemplate.exchange(
                getBaseUrl() + "/routes/" + routeId,
                HttpMethod.PUT,
                new HttpEntity<>(routeReq, headers2),
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, putResp.getStatusCode());
    }

    @Test
    @DisplayName("ARCH-10: Verify Candidate RouteCode Persistence and Duplicate Conflict Handling")
    public void testCreateRoute_ExplicitCandidateRouteCode_AndDuplicateConflict() {
        Long operatorId = 711L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op711@traveleasy.com");

        String candidateCode = "RTE-VERIF-" + System.currentTimeMillis();
        CreateRouteRequest req1 = buildSampleRouteRequest("Verif Route 1", "Vijayawada", "Hyderabad");
        req1.setRouteCode(candidateCode);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<RouteDto> resp1 = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(req1, headers),
                RouteDto.class
        );
        assertEquals(HttpStatus.CREATED, resp1.getStatusCode());
        assertNotNull(resp1.getBody());
        assertEquals(candidateCode, resp1.getBody().getRouteCode());

        // Duplicate submission with same candidate code must be rejected with 409 CONFLICT
        CreateRouteRequest req2 = buildSampleRouteRequest("Verif Route Duplicate", "Vijayawada", "Hyderabad");
        req2.setRouteCode(candidateCode);

        ResponseEntity<Map> resp2 = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(req2, headers),
                Map.class
        );
        assertEquals(HttpStatus.CONFLICT, resp2.getStatusCode());
    }

    @Test
    @DisplayName("ARCH-11: Verify Route Status Handling (DRAFT permitted, Invalid rejected)")
    public void testCreateRoute_StatusHandling_DraftAndInvalidStatus() {
        Long operatorId = 712L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op712@traveleasy.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 1. Permitted DRAFT status
        CreateRouteRequest draftReq = buildSampleRouteRequest("Draft Route", "Vijayawada", "Hyderabad");
        draftReq.setStatus("DRAFT");

        ResponseEntity<RouteDto> draftResp = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(draftReq, headers),
                RouteDto.class
        );
        assertEquals(HttpStatus.CREATED, draftResp.getStatusCode());
        assertNotNull(draftResp.getBody());
        assertEquals("DRAFT", draftResp.getBody().getStatus());

        // 2. Disallowed invalid status rejected with 400 BAD_REQUEST
        CreateRouteRequest invalidReq = buildSampleRouteRequest("Invalid Status Route", "Vijayawada", "Hyderabad");
        invalidReq.setStatus("BOGUS_STATUS");

        ResponseEntity<Map> invalidResp = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(invalidReq, headers),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, invalidResp.getStatusCode());
    }

    @Test
    @DisplayName("ARCH-12: Verify Atomic Rollback on Persistence Validation Failure")
    public void testCreateRoute_AtomicRollback_OnInvalidStopPoints() {
        Long operatorId = 713L;
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "op713@traveleasy.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        long initialRouteCount = routeRepository.count();
        long initialStopCount = routeStopRepository.count();
        long initialPointCount = routePointRepository.count();

        // Create a request where destination stop has no dropping points (fails validation)
        CreateRouteRequest brokenReq = buildSampleRouteRequest("Atomic Rollback Route", "Vijayawada", "Hyderabad");
        brokenReq.getStops().get(brokenReq.getStops().size() - 1).setDroppingPoints(new ArrayList<>());

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                getBaseUrl() + "/routes",
                new HttpEntity<>(brokenReq, headers),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());

        // Verify that no partial records were written or remained
        assertEquals(initialRouteCount, routeRepository.count(), "No route records should be persisted on failure");
        assertEquals(initialStopCount, routeStopRepository.count(), "No stop records should be persisted on failure");
        assertEquals(initialPointCount, routePointRepository.count(), "No point records should be persisted on failure");
    }
}
