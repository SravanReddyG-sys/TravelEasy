package com.traveleasy.bus;

import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.SeatRepository;
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
public class BusOwnershipSecurityTest {

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

    @org.junit.jupiter.api.BeforeEach
    public void setup() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
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
                .busNumber("KA-01-SEC-" + (System.currentTimeMillis() % 100000))
                .busType("AC Sleeper")
                .facilityType("AC")
                .busConfiguration("SINGLE_DECK")
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER)
                .status(status != null ? status : "ACTIVE")
                .build();
        seatLayoutService.validateAndAssignTemplate(bus, bus.getTemplateId(), bus.getBusConfiguration(), bus.getFacilityType());
        return busRepository.save(bus);
    }

    // ==================== 1. POST /api/buses TESTS ====================

    @Test
    @DisplayName("Unit 2.2: POST /api/buses - Unauthenticated request rejected with 401")
    public void testAddBus_Unauthenticated_Returns401() {
        Bus busReq = Bus.builder().busNumber("KA-01-UNAUTH").build();
        ResponseEntity<Map> res = restTemplate.postForEntity(getBaseUrl(), busReq, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    @Test
    @DisplayName("Unit 2.2: POST /api/buses - Invalid token rejected with 401")
    public void testAddBus_InvalidToken_Returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer invalid.jwt.token");
        HttpEntity<Bus> entity = new HttpEntity<>(Bus.builder().busNumber("KA-01-INV").build(), headers);

        ResponseEntity<Map> res = restTemplate.postForEntity(getBaseUrl(), entity, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    @Test
    @DisplayName("Unit 2.2: POST /api/buses - Customer role rejected with 403")
    public void testAddBus_CustomerRole_Returns403() {
        String token = createTestToken(101L, "ROLE_CUSTOMER", "customer@traveleasy.com");
        HttpEntity<Bus> entity = new HttpEntity<>(Bus.builder().busNumber("KA-01-CUST").build(), authHeaders(token));

        ResponseEntity<Map> res = restTemplate.postForEntity(getBaseUrl(), entity, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    @DisplayName("Unit 2.2: POST /api/buses - Operator authoritatively sets operatorId & default status ACTIVE")
    public void testAddBus_Operator_AuthoritativeAssignment() {
        Long operatorUserId = 888L;
        String token = createTestToken(operatorUserId, "ROLE_BUS_OPERATOR", "operator888@traveleasy.com");

        Bus busReq = Bus.builder()
                .operatorId(9999L) // Spoofed by client; must be overwritten by server
                .operatorName("VRL Logistics")
                .busNumber("KA-25-SEC-" + (System.currentTimeMillis() % 100000))
                .busType("AC Sleeper")
                .facilityType("AC")
                .busConfiguration("SINGLE_DECK")
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER)
                .build();

        HttpEntity<Bus> entity = new HttpEntity<>(busReq, authHeaders(token));
        ResponseEntity<Bus> res = restTemplate.postForEntity(getBaseUrl(), entity, Bus.class);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        Bus created = res.getBody();
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(operatorUserId, created.getOperatorId(), "operatorId must be server-derived from caller JWT");
        assertEquals("ACTIVE", created.getStatus(), "status must default to ACTIVE");
    }

    @Test
    @DisplayName("Unit 2.2: POST /api/buses - Admin can register bus for an operator")
    public void testAddBus_Admin_Success() {
        String adminToken = createTestToken(1L, "ROLE_ADMIN", "admin@traveleasy.com");

        Bus busReq = Bus.builder()
                .operatorId(777L)
                .operatorName("Orange Tours")
                .busNumber("AP-09-ADM-" + (System.currentTimeMillis() % 100000))
                .busType("AC Seater")
                .facilityType("AC")
                .busConfiguration("SINGLE_DECK")
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_2_SEATER)
                .build();

        HttpEntity<Bus> entity = new HttpEntity<>(busReq, authHeaders(adminToken));
        ResponseEntity<Bus> res = restTemplate.postForEntity(getBaseUrl(), entity, Bus.class);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(777L, res.getBody().getOperatorId());
    }

    // ==================== 2. GET /api/buses/operator/{operatorId} TESTS ====================

    @Test
    @DisplayName("Unit 2.2: GET /api/buses/operator/{id} - Unauthenticated request rejected with 401")
    public void testGetBusesByOperator_Unauthenticated_Returns401() {
        ResponseEntity<Map> res = restTemplate.getForEntity(getBaseUrl() + "/operator/888", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    @Test
    @DisplayName("Unit 2.2: GET /api/buses/operator/{id} - Customer role rejected with 403")
    public void testGetBusesByOperator_CustomerRole_Returns403() {
        String token = createTestToken(101L, "ROLE_CUSTOMER", "customer@traveleasy.com");
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));

        ResponseEntity<Map> res = restTemplate.exchange(
                getBaseUrl() + "/operator/888",
                HttpMethod.GET,
                entity,
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    @DisplayName("Unit 2.2: GET /api/buses/operator/{id} - Cross-operator access denied with 403")
    public void testGetBusesByOperator_CrossOperatorAccess_Returns403() {
        String token777 = createTestToken(777L, "ROLE_BUS_OPERATOR", "op777@traveleasy.com");
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token777));

        ResponseEntity<Map> res = restTemplate.exchange(
                getBaseUrl() + "/operator/888",
                HttpMethod.GET,
                entity,
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    @DisplayName("Unit 2.2: GET /api/buses/operator/{id} - Authorized operator views own fleet")
    public void testGetBusesByOperator_AuthorizedOperator_Returns200() {
        createAndSaveBus(888L, "ACTIVE");
        String token888 = createTestToken(888L, "ROLE_BUS_OPERATOR", "op888@traveleasy.com");
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token888));

        ResponseEntity<List> res = restTemplate.exchange(
                getBaseUrl() + "/operator/888",
                HttpMethod.GET,
                entity,
                List.class
        );
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertFalse(res.getBody().isEmpty());
    }

    @Test
    @DisplayName("Unit 2.2: GET /api/buses/operator/{id} - Admin can view any operator's fleet")
    public void testGetBusesByOperator_Admin_Returns200() {
        createAndSaveBus(888L, "ACTIVE");
        String adminToken = createTestToken(1L, "ROLE_ADMIN", "admin@traveleasy.com");
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(adminToken));

        ResponseEntity<List> res = restTemplate.exchange(
                getBaseUrl() + "/operator/888",
                HttpMethod.GET,
                entity,
                List.class
        );
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
    }

    // ==================== 3. POST /api/buses/schedules TESTS ====================

    @Test
    @DisplayName("Unit 2.2: POST /api/buses/schedules - Unauthenticated request rejected with 401")
    public void testCreateSchedule_Unauthenticated_Returns401() {
        BusSchedule req = BusSchedule.builder().busId(1L).origin("A").destination("B").build();
        ResponseEntity<Map> res = restTemplate.postForEntity(getBaseUrl() + "/schedules", req, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    @Test
    @DisplayName("Unit 2.2: POST /api/buses/schedules - Customer role rejected with 403")
    public void testCreateSchedule_CustomerRole_Returns403() {
        String token = createTestToken(101L, "ROLE_CUSTOMER", "customer@traveleasy.com");
        BusSchedule req = BusSchedule.builder().busId(1L).origin("A").destination("B").build();
        HttpEntity<BusSchedule> entity = new HttpEntity<>(req, authHeaders(token));

        ResponseEntity<Map> res = restTemplate.postForEntity(getBaseUrl() + "/schedules", entity, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    @DisplayName("Unit 2.2: POST /api/buses/schedules - Operator scheduling on another operator's bus rejected with 403")
    public void testCreateSchedule_CrossOperatorBus_Returns403() {
        Bus busOf888 = createAndSaveBus(888L, "ACTIVE");

        // Operator 777 attempts to schedule trip on Bus owned by Operator 888
        String token777 = createTestToken(777L, "ROLE_BUS_OPERATOR", "op777@traveleasy.com");
        BusSchedule req = BusSchedule.builder()
                .busId(busOf888.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.now().plusDays(5))
                .fare(500.0)
                .build();
        HttpEntity<BusSchedule> entity = new HttpEntity<>(req, authHeaders(token777));

        ResponseEntity<Map> res = restTemplate.postForEntity(getBaseUrl() + "/schedules", entity, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
        assertTrue(res.getBody().get("message").toString().contains("You do not own this bus"));
    }

    @Test
    @DisplayName("Unit 2.2 / TEST-05: POST /api/buses/schedules - Scheduling on INACTIVE bus rejected with 400")
    public void testCreateSchedule_InactiveBus_Returns400() {
        Bus inactiveBus = createAndSaveBus(888L, "INACTIVE");

        String token888 = createTestToken(888L, "ROLE_BUS_OPERATOR", "op888@traveleasy.com");
        BusSchedule req = BusSchedule.builder()
                .busId(inactiveBus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.now().plusDays(5))
                .fare(500.0)
                .build();
        HttpEntity<BusSchedule> entity = new HttpEntity<>(req, authHeaders(token888));

        ResponseEntity<Map> res = restTemplate.postForEntity(getBaseUrl() + "/schedules", entity, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().get("message").toString().contains("Cannot schedule trips on an inactive bus"));
    }

    @Test
    @DisplayName("Unit 2.2: POST /api/buses/schedules - Authorized operator successfully creates trip")
    public void testCreateSchedule_AuthorizedOperator_Success() {
        Bus activeBus = createAndSaveBus(888L, "ACTIVE");

        String token888 = createTestToken(888L, "ROLE_BUS_OPERATOR", "op888@traveleasy.com");
        BusSchedule req = BusSchedule.builder()
                .busId(activeBus.getId())
                .operatorId(9999L) // Spoofed; server must override
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.now().plusDays(5))
                .fare(500.0)
                .build();
        HttpEntity<BusSchedule> entity = new HttpEntity<>(req, authHeaders(token888));

        ResponseEntity<BusSchedule> res = restTemplate.postForEntity(getBaseUrl() + "/schedules", entity, BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        BusSchedule created = res.getBody();
        assertNotNull(created);
        assertEquals(888L, created.getOperatorId());
    }

    @Test
    @DisplayName("Unit 2.2: POST /api/buses/schedules - Admin can schedule trips on any active bus")
    public void testCreateSchedule_Admin_Success() {
        Bus activeBus = createAndSaveBus(888L, "ACTIVE");

        String adminToken = createTestToken(1L, "ROLE_ADMIN", "admin@traveleasy.com");
        BusSchedule req = BusSchedule.builder()
                .busId(activeBus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.now().plusDays(5))
                .fare(500.0)
                .build();
        HttpEntity<BusSchedule> entity = new HttpEntity<>(req, authHeaders(adminToken));

        ResponseEntity<BusSchedule> res = restTemplate.postForEntity(getBaseUrl() + "/schedules", entity, BusSchedule.class);
        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        BusSchedule created = res.getBody();
        assertNotNull(created);
        assertEquals(888L, created.getOperatorId());
    }
}
