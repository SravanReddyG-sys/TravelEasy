package com.traveleasy.bus;

import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
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

import java.security.Key;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OperatorScheduleListingTest {

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

    private String createCustomTestToken(Map<String, Object> claims, String email) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .setClaims(claims)
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

    @Test
    @DisplayName("1. Server-side operatorId population: createSchedule authoritatively assigns bus.operatorId")
    public void testScheduleCreationPopulatesOperatorId() {
        // Register bus for operator 888
        Bus bus = Bus.builder()
                .operatorId(888L)
                .operatorName("VRL Logistics")
                .busNumber("KA-25-TEST-" + (System.currentTimeMillis() % 10000))
                .busType("AC Seater / Sleeper")
                .facilityType("AC")
                .busConfiguration("SINGLE_DECK")
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER)
                .build();
        seatLayoutService.validateAndAssignTemplate(bus, bus.getTemplateId(), bus.getBusConfiguration(), bus.getFacilityType());
        Bus savedBus = busRepository.save(bus);

        // Client submits schedule WITHOUT operatorId (or spoofed ID 9999L)
        BusSchedule request = BusSchedule.builder()
                .busId(savedBus.getId())
                .operatorId(9999L) // Spoofed by client; must be overwritten by server
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.now().plusDays(3))
                .departureTime("21:00")
                .arrivalTime("06:00")
                .fare(850.0)
                .availableSeats(savedBus.getTotalSeats())
                .build();

        String token888 = createTestToken(888L, "ROLE_BUS_OPERATOR", "operator888@traveleasy.com");
        HttpEntity<BusSchedule> entity = new HttpEntity<>(request, authHeaders(token888));
        ResponseEntity<BusSchedule> response = restTemplate.postForEntity(
                getBaseUrl() + "/schedules",
                entity,
                BusSchedule.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        BusSchedule created = response.getBody();
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(888L, created.getOperatorId(), "operatorId must be server-derived from bus.operatorId, ignoring client spoofing");
        assertEquals("VRL Logistics", created.getOperatorName());
    }

    @Test
    @DisplayName("2. Authorized operator schedule retrieval: Operator 888 views their own schedules")
    public void testAuthorizedOperatorScheduleListing() {
        Long opId = 888L;
        String token = createTestToken(opId, "ROLE_BUS_OPERATOR", "operator888@traveleasy.com");

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<List> response = restTemplate.exchange(
                getBaseUrl() + "/operator/" + opId + "/schedules",
                HttpMethod.GET,
                entity,
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("3. Cross-operator access denial: Operator 777 cannot view Operator 888's schedules")
    public void testCrossOperatorAccessDenied() {
        // Authenticated as Operator 777
        String token777 = createTestToken(777L, "ROLE_BUS_OPERATOR", "operator777@traveleasy.com");

        // Attempting to query Operator 888's schedules
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token777));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/operator/888/schedules",
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("message").toString().contains("Cannot access schedules for another operator"));
    }

    @Test
    @DisplayName("4. Customer role access denial: ROLE_CUSTOMER cannot access operator schedules")
    public void testCustomerRoleAccessDenied() {
        String customerToken = createTestToken(101L, "ROLE_CUSTOMER", "customer@traveleasy.com");

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(customerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/operator/888/schedules",
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("message").toString().contains("Bus Operator role required"));
    }

    @Test
    @DisplayName("5. Unauthenticated access denial: Missing Authorization header rejected")
    public void testUnauthenticatedAccessDenied() {
        HttpEntity<Void> entity = new HttpEntity<>(new HttpHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/operator/888/schedules",
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("6. Invalid token rejection: Corrupted or tampered JWT rejected")
    public void testInvalidTokenRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer invalid.jwt.tampered.token");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/operator/888/schedules",
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("7. Legacy schedule discoverability: Schedule with null operatorId is discovered via bus ownership")
    public void testLegacyScheduleDiscoverabilityViaBusOwnership() {
        Long opId = 654L;
        // Create bus owned by 654
        Bus bus = Bus.builder()
                .operatorId(opId)
                .operatorName("Legacy Test Travels")
                .busNumber("AP-Legacy-" + (System.currentTimeMillis() % 10000))
                .totalSeats(36)
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER)
                .build();
        seatLayoutService.validateAndAssignTemplate(bus, bus.getTemplateId(), SeatLayoutService.CONFIG_SINGLE_DECK, "NON_AC");
        Bus savedBus = busRepository.save(bus);

        // Manually insert schedule with operatorId = null (simulating legacy data)
        BusSchedule legacySched = BusSchedule.builder()
                .busId(savedBus.getId())
                .operatorId(null) // Legacy missing operatorId
                .operatorName(savedBus.getOperatorName())
                .busNumber(savedBus.getBusNumber())
                .origin("Vijayawada")
                .destination("Visakhapatnam")
                .travelDate(LocalDate.now().plusDays(5))
                .departureTime("18:00")
                .arrivalTime("02:00")
                .fare(600.0)
                .availableSeats(36)
                .build();
        BusSchedule savedLegacy = scheduleRepository.save(legacySched);
        assertNull(savedLegacy.getOperatorId());

        // Operator 654 queries their schedules
        String token = createTestToken(opId, "ROLE_BUS_OPERATOR", "op654@traveleasy.com");
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<List> response = restTemplate.exchange(
                getBaseUrl() + "/operator/" + opId + "/schedules",
                HttpMethod.GET,
                entity,
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<?> list = response.getBody();
        assertNotNull(list);
        assertFalse(list.isEmpty(), "Legacy schedule must be discovered via bus ownership");

        boolean found = list.stream().anyMatch(item -> {
            Map<?, ?> map = (Map<?, ?>) item;
            return savedLegacy.getId().toString().equals(map.get("id").toString());
        });
        assertTrue(found, "The legacy schedule with null operatorId must be returned for operator 654");
    }

    @Test
    @DisplayName("8. Admin elevated access: ROLE_ADMIN can inspect any operator's schedules")
    public void testAdminElevatedAccess() {
        String adminToken = createTestToken(1L, "ROLE_ADMIN", "admin@traveleasy.com");

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(adminToken));
        ResponseEntity<List> response = restTemplate.exchange(
                getBaseUrl() + "/operator/888/schedules",
                HttpMethod.GET,
                entity,
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("9. Fail-closed: Valid token with ROLE_BUS_OPERATOR but missing userId claim is denied")
    public void testMissingUserIdClaimDenied() {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("role", "ROLE_BUS_OPERATOR");
        claims.put("fullName", "Operator No ID");
        // Explicitly omit "userId" claim
        String token = createCustomTestToken(claims, "noid@traveleasy.com");

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/operator/888/schedules",
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("message").toString().contains("Missing or invalid user identity in token"));
    }

    @Test
    @DisplayName("10. Fail-closed: Valid token with malformed/non-numeric userId claim is denied")
    public void testMalformedUserIdClaimDenied() {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("role", "ROLE_BUS_OPERATOR");
        claims.put("userId", "malformed_string_identity");
        claims.put("fullName", "Operator Malformed ID");
        String token = createCustomTestToken(claims, "malformed@traveleasy.com");

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/operator/888/schedules",
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("message").toString().contains("Missing or invalid user identity in token"));
    }
}
