package com.traveleasy.bus;

import com.traveleasy.bus.dto.FareOverrideItemDto;
import com.traveleasy.bus.dto.FareMatrixEntryDto;
import com.traveleasy.bus.dto.ScheduleFareMatrixResponse;
import com.traveleasy.bus.dto.ScheduleStopDto;
import com.traveleasy.bus.dto.FareMatrixPreviewRequest;
import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.ScheduleFareOverrideRepository;
import com.traveleasy.bus.repository.ScheduleStopRepository;
import com.traveleasy.bus.service.RouteStopsService;
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
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FareMatrixEngineTest {

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
    private ScheduleFareOverrideRepository fareOverrideRepository;

    @Autowired
    private RouteStopsService routeStopsService;

    @Autowired
    private com.traveleasy.bus.service.ScheduleService scheduleService;

    @Autowired
    private com.traveleasy.bus.service.FareMatrixService fareMatrixService;

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
                .busNumber("AP09-MATRIX-" + System.currentTimeMillis() % 10000)
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
                .fare(1200.0)
                .availableSeats(30)
                .status("SCHEDULED")
                .build());

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
    @DisplayName("Unit 4.2.1: 4-Stop route computes complete 6-pair Origin-Destination fare matrix with automatic summation")
    public void testFareMatrixCalculation_FourStops_CalculatesAllSixPairs() {
        Long operatorId = 701L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);

        ResponseEntity<ScheduleFareMatrixResponse> response = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare-matrix",
                ScheduleFareMatrixResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        ScheduleFareMatrixResponse matrixResponse = response.getBody();

        assertEquals(4, matrixResponse.getTotalStops());
        assertEquals(6, matrixResponse.getTotalPairs()); // 4*(3)/2 = 6
        assertEquals(6, matrixResponse.getMatrix().size());

        // Verify each pair in order:
        // (0, 1): 300.0, 210km
        FareMatrixEntryDto p01 = matrixResponse.getMatrix().get(0);
        assertEquals(0, p01.getFromStopOrder());
        assertEquals(1, p01.getToStopOrder());
        assertEquals(300.0, p01.getCalculatedFare());
        assertEquals(300.0, p01.getEffectiveFare());
        assertNull(p01.getCustomFare());
        assertFalse(p01.getIsOverridden());
        assertEquals(210.0, p01.getDistanceKm());

        // (0, 2): 300 + 400 = 700.0, 360km
        FareMatrixEntryDto p02 = matrixResponse.getMatrix().get(1);
        assertEquals(0, p02.getFromStopOrder());
        assertEquals(2, p02.getToStopOrder());
        assertEquals(700.0, p02.getCalculatedFare());
        assertEquals(700.0, p02.getEffectiveFare());
        assertFalse(p02.getIsOverridden());
        assertEquals(360.0, p02.getDistanceKm());

        // (0, 3): 300 + 400 + 500 = 1200.0, 570km
        FareMatrixEntryDto p03 = matrixResponse.getMatrix().get(2);
        assertEquals(0, p03.getFromStopOrder());
        assertEquals(3, p03.getToStopOrder());
        assertEquals(1200.0, p03.getCalculatedFare());
        assertEquals(1200.0, p03.getEffectiveFare());
        assertEquals(570.0, p03.getDistanceKm());

        // (1, 2): 400.0, 150km (360 - 210)
        FareMatrixEntryDto p12 = matrixResponse.getMatrix().get(3);
        assertEquals(1, p12.getFromStopOrder());
        assertEquals(2, p12.getToStopOrder());
        assertEquals(400.0, p12.getCalculatedFare());
        assertEquals(400.0, p12.getEffectiveFare());
        assertEquals(150.0, p12.getDistanceKm());

        // (1, 3): 400 + 500 = 900.0, 360km (570 - 210)
        FareMatrixEntryDto p13 = matrixResponse.getMatrix().get(4);
        assertEquals(1, p13.getFromStopOrder());
        assertEquals(3, p13.getToStopOrder());
        assertEquals(900.0, p13.getCalculatedFare());
        assertEquals(900.0, p13.getEffectiveFare());
        assertEquals(360.0, p13.getDistanceKm());

        // (2, 3): 500.0, 210km (570 - 360)
        FareMatrixEntryDto p23 = matrixResponse.getMatrix().get(5);
        assertEquals(2, p23.getFromStopOrder());
        assertEquals(3, p23.getToStopOrder());
        assertEquals(500.0, p23.getCalculatedFare());
        assertEquals(500.0, p23.getEffectiveFare());
        assertEquals(210.0, p23.getDistanceKm());
    }

    @Test
    @DisplayName("Unit 4.2.2: Registering manual override updates effective fare while leaving calculated fare intact")
    public void testSaveFareOverrides_IntermediatePair_UpdatesEffectiveFare() {
        Long operatorId = 702L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "operator702@travel.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // Override Hyd -> Anantapur (0 -> 2) from calculated 700.0 to discounted 650.0
        List<FareOverrideItemDto> overrides = List.of(
                FareOverrideItemDto.builder()
                        .fromStopOrder(0)
                        .toStopOrder(2)
                        .customFare(650.0)
                        .build()
        );

        HttpEntity<List<FareOverrideItemDto>> request = new HttpEntity<>(overrides, headers);
        ResponseEntity<ScheduleFareMatrixResponse> putResponse = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare-matrix/overrides",
                HttpMethod.PUT,
                request,
                ScheduleFareMatrixResponse.class
        );

        assertEquals(HttpStatus.OK, putResponse.getStatusCode());
        assertNotNull(putResponse.getBody());

        // Check matrix entry for (0, 2)
        FareMatrixEntryDto entry02 = putResponse.getBody().getMatrix().stream()
                .filter(m -> m.getFromStopOrder() == 0 && m.getToStopOrder() == 2)
                .findFirst().orElseThrow();

        assertEquals(700.0, entry02.getCalculatedFare());
        assertEquals(650.0, entry02.getCustomFare());
        assertEquals(650.0, entry02.getEffectiveFare());
        assertTrue(entry02.getIsOverridden());

        // Test GET /fare endpoint
        ResponseEntity<Map> fareQuery = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare?fromOrder=0&toOrder=2",
                Map.class
        );
        assertEquals(HttpStatus.OK, fareQuery.getStatusCode());
        assertNotNull(fareQuery.getBody());
        assertEquals(650.0, ((Number) fareQuery.getBody().get("effectiveFare")).doubleValue());
    }

    @Test
    @DisplayName("Unit 4.2.3: Overriding full trip (0, N-1) synchronizes parent BusSchedule.fare")
    public void testSaveFareOverrides_FullTrip_SynchronizesBusScheduleFare() {
        Long operatorId = 703L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "operator703@travel.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // Override full trip (0 -> 3) from 1200.0 to 1100.0
        List<FareOverrideItemDto> overrides = List.of(
                FareOverrideItemDto.builder()
                        .fromStopOrder(0)
                        .toStopOrder(3)
                        .customFare(1100.0)
                        .build()
        );

        HttpEntity<List<FareOverrideItemDto>> request = new HttpEntity<>(overrides, headers);
        ResponseEntity<ScheduleFareMatrixResponse> putResponse = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare-matrix/overrides",
                HttpMethod.PUT,
                request,
                ScheduleFareMatrixResponse.class
        );

        assertEquals(HttpStatus.OK, putResponse.getStatusCode());

        // Verify parent BusSchedule in database has updated fare to 1100.0
        BusSchedule updatedSchedule = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertEquals(1100.0, updatedSchedule.getFare());
    }

    @Test
    @DisplayName("Unit 4.2.4: Clearing overrides restores cumulative elementary fare and synchronizes BusSchedule")
    public void testClearFareOverrides_RevertsToCalculatedSum() {
        Long operatorId = 704L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "operator704@travel.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // 1. Set override on full trip (0 -> 3) to 1100.0 and intermediate (1 -> 3) to 850.0
        List<FareOverrideItemDto> overrides = List.of(
                FareOverrideItemDto.builder().fromStopOrder(0).toStopOrder(3).customFare(1100.0).build(),
                FareOverrideItemDto.builder().fromStopOrder(1).toStopOrder(3).customFare(850.0).build()
        );
        restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare-matrix/overrides",
                HttpMethod.PUT,
                new HttpEntity<>(overrides, headers),
                ScheduleFareMatrixResponse.class
        );

        BusSchedule schAfterOverride = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertEquals(1100.0, schAfterOverride.getFare());

        // 2. Clear all overrides via DELETE /schedules/{id}/fare-matrix/overrides
        ResponseEntity<ScheduleFareMatrixResponse> deleteResponse = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare-matrix/overrides",
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                ScheduleFareMatrixResponse.class
        );

        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
        assertNotNull(deleteResponse.getBody());

        // Verify all pairs now have customFare = null, isOverridden = false
        for (FareMatrixEntryDto entry : deleteResponse.getBody().getMatrix()) {
            assertNull(entry.getCustomFare());
            assertFalse(entry.getIsOverridden());
            assertEquals(entry.getCalculatedFare(), entry.getEffectiveFare());
        }

        // Verify BusSchedule.fare reverted to calculated full sum 1200.0
        BusSchedule schAfterClear = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertEquals(1200.0, schAfterClear.getFare());
    }

    @Test
    @DisplayName("Unit 4.2.5: Query effective fare by stop names (case-insensitive) resolves correctly")
    public void testGetEffectiveFare_ByStopNames_CaseInsensitive() {
        Long operatorId = 705L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);

        // Stop 1: "Kurnool", Stop 3: "Bengaluru (Majestic)" -> fare should be 400 + 500 = 900.0
        ResponseEntity<Map> queryResponse = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare?fromStop=kurnool&toStop=bengaluru (majestic)",
                Map.class
        );

        assertEquals(HttpStatus.OK, queryResponse.getStatusCode());
        assertNotNull(queryResponse.getBody());
        assertEquals(900.0, ((Number) queryResponse.getBody().get("effectiveFare")).doubleValue());

        // Negative check: Reverse order (Bengaluru -> Kurnool) returns 400 Bad Request
        ResponseEntity<Map> reverseResponse = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare?fromStop=bengaluru (majestic)&toStop=kurnool",
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, reverseResponse.getStatusCode());

        // Non-existent stop
        ResponseEntity<Map> unknownResponse = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare?fromStop=Hyderabad&toStop=Chennai",
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, unknownResponse.getStatusCode());
    }

    @Test
    @DisplayName("Unit 4.2.6: Validation rejects invalid stop orders or out-of-bounds pairs")
    public void testSaveFareOverrides_Validation_OutOfBoundsOrInvalidPair() {
        Long operatorId = 706L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);
        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "operator706@travel.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // fromOrder >= toOrder (e.g. 2 -> 1)
        List<FareOverrideItemDto> invalidOrder = List.of(
                FareOverrideItemDto.builder().fromStopOrder(2).toStopOrder(1).customFare(350.0).build()
        );
        ResponseEntity<Map> resp1 = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare-matrix/overrides",
                HttpMethod.PUT,
                new HttpEntity<>(invalidOrder, headers),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, resp1.getStatusCode());

        // toOrder out of bounds (e.g. 0 -> 4 when max is 3)
        List<FareOverrideItemDto> outOfBounds = List.of(
                FareOverrideItemDto.builder().fromStopOrder(0).toStopOrder(4).customFare(1500.0).build()
        );
        ResponseEntity<Map> resp2 = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare-matrix/overrides",
                HttpMethod.PUT,
                new HttpEntity<>(outOfBounds, headers),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, resp2.getStatusCode());
    }

    @Test
    @DisplayName("Unit 4.2.7: Security guards reject cross-operator modification with 403 Forbidden")
    public void testSaveFareOverrides_Security_CrossOperator_Forbidden() {
        Long operatorA = 707L;
        Long operatorB = 708L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorA);

        // Operator B tries to modify Operator A's fare overrides
        String tokenB = createTestToken(operatorB, "ROLE_BUS_OPERATOR", "operator708@travel.com");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenB);

        List<FareOverrideItemDto> overrides = List.of(
                FareOverrideItemDto.builder().fromStopOrder(0).toStopOrder(2).customFare(600.0).build()
        );

        ResponseEntity<Map> putResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare-matrix/overrides",
                HttpMethod.PUT,
                new HttpEntity<>(overrides, headers),
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, putResp.getStatusCode());

        ResponseEntity<Map> delResp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare-matrix/overrides",
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, delResp.getStatusCode());
    }

    @Test
    @DisplayName("Unit 4.2.8: Modifying fare overrides on CANCELLED schedule is rejected with 409 Conflict")
    public void testSaveFareOverrides_CancelledSchedule_RejectedConflict() {
        Long operatorId = 709L;
        BusSchedule schedule = createTestScheduleWith4Stops(operatorId);
        schedule.setStatus("CANCELLED");
        scheduleRepository.save(schedule);

        String token = createTestToken(operatorId, "ROLE_BUS_OPERATOR", "operator709@travel.com");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        List<FareOverrideItemDto> overrides = List.of(
                FareOverrideItemDto.builder().fromStopOrder(0).toStopOrder(2).customFare(600.0).build()
        );

        ResponseEntity<Map> resp = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + schedule.getId() + "/fare-matrix/overrides",
                HttpMethod.PUT,
                new HttpEntity<>(overrides, headers),
                Map.class
        );
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
    }

    @Test
    @DisplayName("Unit 4.2.9: Preview fare matrix endpoint dynamically calculates N*(N-1)/2 pairs and applies overrides")
    public void testPreviewFareMatrix_DynamicPairsAndOverrides() {
        List<ScheduleStopDto> stops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Bengaluru (Origin)").distanceKm(0.0).consecutiveFare(450.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Kurnool").distanceKm(210.0).consecutiveFare(350.0).build(),
                ScheduleStopDto.builder().stopOrder(2).stopName("Anantapur").distanceKm(320.0).consecutiveFare(400.0).build(),
                ScheduleStopDto.builder().stopOrder(3).stopName("Hyderabad (Destination)").distanceKm(420.0).consecutiveFare(0.0).build()
        );

        List<FareOverrideItemDto> overrides = List.of(
                FareOverrideItemDto.builder().fromStopOrder(0).toStopOrder(2).customFare(750.0).build(),
                FareOverrideItemDto.builder().fromStopOrder(0).toStopOrder(3).customFare(1100.0).build(),
                FareOverrideItemDto.builder().fromStopOrder(1).toStopOrder(3).customFare(700.0).build()
        );

        FareMatrixPreviewRequest req = FareMatrixPreviewRequest.builder()
                .stops(stops)
                .overrides(overrides)
                .build();

        ResponseEntity<ScheduleFareMatrixResponse> resp = restTemplate.postForEntity(
                getBaseUrl() + "/schedules/fare-matrix/preview",
                req,
                ScheduleFareMatrixResponse.class
        );

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        ScheduleFareMatrixResponse body = resp.getBody();
        assertEquals(4, body.getTotalStops());
        assertEquals(6, body.getTotalPairs());

        List<FareMatrixEntryDto> matrix = body.getMatrix();
        assertEquals(6, matrix.size());

        // Pair 0->1: Bengaluru -> Kurnool (dist 210, calc 450, eff 450, not overridden)
        FareMatrixEntryDto p01 = matrix.stream().filter(e -> e.getFromStopOrder() == 0 && e.getToStopOrder() == 1).findFirst().orElseThrow();
        assertEquals(210.0, p01.getDistanceKm());
        assertEquals(450.0, p01.getCalculatedFare());
        assertEquals(450.0, p01.getEffectiveFare());
        assertFalse(p01.getIsOverridden());

        // Pair 0->2: Bengaluru -> Anantapur (dist 320, calc 800, eff 750, overridden)
        FareMatrixEntryDto p02 = matrix.stream().filter(e -> e.getFromStopOrder() == 0 && e.getToStopOrder() == 2).findFirst().orElseThrow();
        assertEquals(320.0, p02.getDistanceKm());
        assertEquals(800.0, p02.getCalculatedFare());
        assertEquals(750.0, p02.getEffectiveFare());
        assertTrue(p02.getIsOverridden());

        // Pair 0->3: Bengaluru -> Hyderabad (dist 420, calc 1200, eff 1100, overridden)
        FareMatrixEntryDto p03 = matrix.stream().filter(e -> e.getFromStopOrder() == 0 && e.getToStopOrder() == 3).findFirst().orElseThrow();
        assertEquals(420.0, p03.getDistanceKm());
        assertEquals(1200.0, p03.getCalculatedFare());
        assertEquals(1100.0, p03.getEffectiveFare());
        assertTrue(p03.getIsOverridden());

        // Pair 1->2: Kurnool -> Anantapur (dist 110, calc 350, eff 350, not overridden)
        FareMatrixEntryDto p12 = matrix.stream().filter(e -> e.getFromStopOrder() == 1 && e.getToStopOrder() == 2).findFirst().orElseThrow();
        assertEquals(110.0, p12.getDistanceKm());
        assertEquals(350.0, p12.getCalculatedFare());
        assertEquals(350.0, p12.getEffectiveFare());
        assertFalse(p12.getIsOverridden());

        // Pair 1->3: Kurnool -> Hyderabad (dist 210, calc 750, eff 700, overridden)
        FareMatrixEntryDto p13 = matrix.stream().filter(e -> e.getFromStopOrder() == 1 && e.getToStopOrder() == 3).findFirst().orElseThrow();
        assertEquals(210.0, p13.getDistanceKm());
        assertEquals(750.0, p13.getCalculatedFare());
        assertEquals(700.0, p13.getEffectiveFare());
        assertTrue(p13.getIsOverridden());

        // Pair 2->3: Anantapur -> Hyderabad (dist 100, calc 400, eff 400, not overridden)
        FareMatrixEntryDto p23 = matrix.stream().filter(e -> e.getFromStopOrder() == 2 && e.getToStopOrder() == 3).findFirst().orElseThrow();
        assertEquals(100.0, p23.getDistanceKm());
        assertEquals(400.0, p23.getCalculatedFare());
        assertEquals(400.0, p23.getEffectiveFare());
        assertFalse(p23.getIsOverridden());
    }

    @Test
    @DisplayName("Unit 4.2.10: Create schedule with fareOverrides persists overrides and synchronizes full-trip fare")
    public void testCreateSchedule_WithFareOverrides_PersistsAndSynchronizes() {
        Long operatorId = 710L;
        Bus bus = busRepository.save(Bus.builder()
                .busNumber("AP09-MATRIX-CREAT-" + System.currentTimeMillis() % 10000)
                .busType("AC Sleeper (2+1)")
                .totalSeats(30)
                .operatorName("Orange Tours & Travels")
                .operatorId(operatorId)
                .status("ACTIVE")
                .templateId("tpl-sleeper-2-1-30")
                .build());

        List<ScheduleStopDto> stops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Bengaluru").departureTime("06:00").distanceKm(0.0).consecutiveFare(450.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Kurnool").arrivalTime("10:00").departureTime("10:20").distanceKm(210.0).consecutiveFare(350.0).build(),
                ScheduleStopDto.builder().stopOrder(2).stopName("Anantapur").arrivalTime("14:15").departureTime("14:35").distanceKm(320.0).consecutiveFare(400.0).build(),
                ScheduleStopDto.builder().stopOrder(3).stopName("Hyderabad").arrivalTime("20:00").distanceKm(420.0).consecutiveFare(0.0).build()
        );

        List<FareOverrideItemDto> overrides = List.of(
                FareOverrideItemDto.builder().fromStopOrder(0).toStopOrder(2).fromStopName("Bengaluru").toStopName("Anantapur").customFare(750.0).build(),
                FareOverrideItemDto.builder().fromStopOrder(0).toStopOrder(3).fromStopName("Bengaluru").toStopName("Hyderabad").customFare(1100.0).build()
        );

        BusSchedule scheduleReq = BusSchedule.builder()
                .busId(bus.getId())
                .travelDate(LocalDate.now().plusDays(5))
                .departureTime("06:00")
                .arrivalTime("20:00")
                .availableSeats(30)
                .stops(stops)
                .fareOverrides(overrides)
                .build();

        BusSchedule created = scheduleService.createSchedule(operatorId, false, scheduleReq);
        assertNotNull(created.getId());

        // Full trip fare should be synchronized to overridden fare (1100.0)
        assertEquals(1100.0, created.getFare());

        // Check overrides persisted in DB
        List<com.traveleasy.bus.model.ScheduleFareOverride> savedOverrides = fareOverrideRepository.findByScheduleId(created.getId());
        assertEquals(2, savedOverrides.size());

        // Verify effective fare queries
        Double eff02 = fareMatrixService.getEffectiveFare(created.getId(), 0, 2);
        assertEquals(750.0, eff02);

        Double eff01 = fareMatrixService.getEffectiveFare(created.getId(), 0, 1);
        assertEquals(450.0, eff01);
    }

    @Test
    @DisplayName("Unit 4.2.11: Create schedule with invalid fareOverrides is rejected")
    public void testCreateSchedule_WithInvalidFareOverrides_Rejection() {
        Long operatorId = 711L;
        Bus bus = busRepository.save(Bus.builder()
                .busNumber("AP09-MATRIX-ERR-" + System.currentTimeMillis() % 10000)
                .busType("AC Sleeper (2+1)")
                .totalSeats(30)
                .operatorName("Orange Tours & Travels")
                .operatorId(operatorId)
                .status("ACTIVE")
                .templateId("tpl-sleeper-2-1-30")
                .build());

        List<ScheduleStopDto> stops = List.of(
                ScheduleStopDto.builder().stopOrder(0).stopName("Origin").departureTime("06:00").distanceKm(0.0).consecutiveFare(400.0).build(),
                ScheduleStopDto.builder().stopOrder(1).stopName("Destination").arrivalTime("12:00").distanceKm(200.0).consecutiveFare(0.0).build()
        );

        // Invalid: customFare <= 0
        BusSchedule scheduleReq = BusSchedule.builder()
                .busId(bus.getId())
                .travelDate(LocalDate.now().plusDays(5))
                .departureTime("06:00")
                .arrivalTime("12:00")
                .availableSeats(30)
                .stops(stops)
                .fareOverrides(List.of(
                        FareOverrideItemDto.builder().fromStopOrder(0).toStopOrder(1).customFare(-50.0).build()
                ))
                .build();

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> {
            scheduleService.createSchedule(operatorId, false, scheduleReq);
        });
    }
}
