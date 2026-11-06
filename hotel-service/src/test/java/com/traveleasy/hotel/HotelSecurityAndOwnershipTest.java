package com.traveleasy.hotel;

import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
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

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HotelSecurityAndOwnershipTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HotelPropertyRepository hotelRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    private static final String SECRET = "TravelEasySuperSecretKeyForJWTSigning12345678901234567890";

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/hotels";
    }

    @BeforeEach
    public void setup() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
    }

    private String createTestToken(Long userId, String role, String email, String fullName, long ttlMillis) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .claim("userId", userId)
                .claim("role", role)
                .claim("fullName", fullName != null ? fullName : "User " + userId)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private String createValidToken(Long userId, String role) {
        return createTestToken(userId, role, "user" + userId + "@example.com", "User " + userId, 3600000);
    }

    private String createInternalToken(String serviceName) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(serviceName)
                .setIssuer("traveleasy-internal")
                .claim("role", "INTERNAL_SERVICE")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 300000))
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

    // =========================================================================
    // 1. AUTHENTICATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Auth 1.1: Missing token on POST /api/hotels returns 401")
    public void testAddHotel_MissingToken_Returns401() {
        HotelProperty property = HotelProperty.builder()
                .name("Grand Luxury Hotel")
                .city("Hyderabad")
                .address("Banjara Hills")
                .starRating(5)
                .build();

        HttpEntity<HotelProperty> entity = new HttpEntity<>(property, new HttpHeaders());
        ResponseEntity<Map> response = restTemplate.postForEntity(getBaseUrl(), entity, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("message").toString().contains("Authentication required"));
    }

    @Test
    @DisplayName("Auth 1.2: Malformed / Invalid token on POST /api/hotels returns 401")
    public void testAddHotel_InvalidToken_Returns401() {
        HotelProperty property = HotelProperty.builder()
                .name("Grand Palace")
                .city("Goa")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer invalid.malformed.jwt.token");

        HttpEntity<HotelProperty> entity = new HttpEntity<>(property, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(getBaseUrl(), entity, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Invalid or expired"));
    }

    @Test
    @DisplayName("Auth 1.3: Expired token on POST /api/hotels returns 401")
    public void testAddHotel_ExpiredToken_Returns401() {
        String expiredToken = createTestToken(100L, "ROLE_HOTEL_MANAGER", "expired@test.com", "Expired User", -10000);
        HotelProperty property = HotelProperty.builder()
                .name("Expired Palace")
                .city("Goa")
                .build();

        HttpEntity<HotelProperty> entity = new HttpEntity<>(property, authHeaders(expiredToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(getBaseUrl(), entity, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Invalid or expired"));
    }

    @Test
    @DisplayName("Auth 1.4: Tampered signature token on POST /api/hotels returns 401")
    public void testAddHotel_TamperedToken_Returns401() {
        String validToken = createValidToken(100L, "ROLE_HOTEL_MANAGER");
        // Tamper with the signature portion
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "XYZ12";

        HotelProperty property = HotelProperty.builder()
                .name("Tampered Hotel")
                .city("Goa")
                .build();

        HttpEntity<HotelProperty> entity = new HttpEntity<>(property, authHeaders(tamperedToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(getBaseUrl(), entity, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // =========================================================================
    // 2. ROLE AUTHORIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Role 2.1: ROLE_CUSTOMER attempting POST /api/hotels returns 403 Forbidden")
    public void testAddHotel_CustomerRole_Returns403() {
        String customerToken = createValidToken(200L, "ROLE_CUSTOMER");
        HotelProperty property = HotelProperty.builder()
                .name("Customer Hotel")
                .city("Bangalore")
                .build();

        HttpEntity<HotelProperty> entity = new HttpEntity<>(property, authHeaders(customerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(getBaseUrl(), entity, Map.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Hotel Manager or Admin role required"));
    }

    @Test
    @DisplayName("Role 2.2: ROLE_HOTEL_MANAGER can register property and receives ACTIVE status (BD-03)")
    public void testAddHotel_HotelManagerRole_Returns201AndActiveStatus() {
        Long managerId = 301L;
        String managerToken = createTestToken(managerId, "ROLE_HOTEL_MANAGER", "mgr301@test.com", "Sarah Manager", 3600000);

        HotelProperty property = HotelProperty.builder()
                .name("Seaside Haven Resort " + System.currentTimeMillis())
                .city("Goa")
                .address("Calangute Beach")
                .starRating(4)
                .description("Luxury beachfront stay")
                .amenities("Pool, Spa, Wi-Fi")
                .build();

        HttpEntity<HotelProperty> entity = new HttpEntity<>(property, authHeaders(managerToken));
        ResponseEntity<HotelProperty> response = restTemplate.postForEntity(getBaseUrl(), entity, HotelProperty.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        HotelProperty created = response.getBody();
        assertNotNull(created);
        assertNotNull(created.getId());
        // Authoritative assignment: managerId and managerName bound to authenticated token
        assertEquals(managerId, created.getManagerId());
        assertEquals("Sarah Manager", created.getManagerName());
        // BD-03: Newly registered property is ACTIVE immediately
        assertEquals("ACTIVE", created.getStatus());
    }

    @Test
    @DisplayName("Role 2.3: ROLE_ADMIN can register property with 201 Created")
    public void testAddHotel_AdminRole_Returns201() {
        Long adminId = 1L;
        String adminToken = createTestToken(adminId, "ROLE_ADMIN", "admin@traveleasy.com", "Platform Administrator", 3600000);

        HotelProperty property = HotelProperty.builder()
                .name("Admin Grand Hotel " + System.currentTimeMillis())
                .city("Delhi")
                .address("Connaught Place")
                .starRating(5)
                .build();

        HttpEntity<HotelProperty> entity = new HttpEntity<>(property, authHeaders(adminToken));
        ResponseEntity<HotelProperty> response = restTemplate.postForEntity(getBaseUrl(), entity, HotelProperty.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        HotelProperty created = response.getBody();
        assertNotNull(created);
        assertEquals("ACTIVE", created.getStatus());
    }

    // =========================================================================
    // 3. PROPERTY OWNERSHIP TESTS
    // =========================================================================

    @Test
    @DisplayName("Ownership 3.1: Manager cannot access another manager's properties (returns 403)")
    public void testGetHotelsByManager_OtherManager_Returns403() {
        Long managerA = 401L;
        Long managerB = 402L;
        String tokenManagerA = createValidToken(managerA, "ROLE_HOTEL_MANAGER");

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(tokenManagerA));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/manager/" + managerB,
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Cannot access properties belonging to another hotel manager"));
    }

    @Test
    @DisplayName("Ownership 3.2: Manager can access their own properties (returns 200)")
    public void testGetHotelsByManager_OwnProperties_Returns200() {
        Long managerA = 403L;
        String tokenManagerA = createValidToken(managerA, "ROLE_HOTEL_MANAGER");

        // Save a test hotel for manager A
        HotelProperty hotelA = HotelProperty.builder()
                .name("Manager A Hotel")
                .city("Vijayawada")
                .managerId(managerA)
                .status("ACTIVE")
                .build();
        hotelRepository.save(hotelA);

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(tokenManagerA));
        ResponseEntity<List> response = restTemplate.exchange(
                getBaseUrl() + "/manager/" + managerA,
                HttpMethod.GET,
                entity,
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
    }

    @Test
    @DisplayName("Ownership 3.3: Admin can access any manager's properties (returns 200)")
    public void testGetHotelsByManager_AdminAccess_Returns200() {
        Long targetManagerId = 403L;
        String adminToken = createValidToken(1L, "ROLE_ADMIN");

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(adminToken));
        ResponseEntity<List> response = restTemplate.exchange(
                getBaseUrl() + "/manager/" + targetManagerId,
                HttpMethod.GET,
                entity,
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // =========================================================================
    // 4. ROOM OWNERSHIP TESTS
    // =========================================================================

    @Test
    @DisplayName("Room 4.1: Manager cannot add rooms to a property owned by another manager (returns 403)")
    public void testAddRoomType_AnotherManagersHotel_Returns403() {
        Long ownerManager = 501L;
        Long attackerManager = 502L;

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Owner Hotel")
                .city("Goa")
                .managerId(ownerManager)
                .status("ACTIVE")
                .build());

        String attackerToken = createValidToken(attackerManager, "ROLE_HOTEL_MANAGER");

        RoomType room = RoomType.builder()
                .roomTypeName("Unauthorized Suite")
                .pricePerNight(5000.0)
                .totalRooms(10)
                .availableRooms(10)
                .build();

        HttpEntity<RoomType> entity = new HttpEntity<>(room, authHeaders(attackerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Cannot add room categories to a property you do not own"));
    }

    @Test
    @DisplayName("Room 4.2: Manager can add rooms to their own property (returns 201)")
    public void testAddRoomType_OwnHotel_Returns201() {
        Long manager = 503L;
        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("My Own Hotel")
                .city("Chennai")
                .managerId(manager)
                .status("ACTIVE")
                .build());

        String token = createValidToken(manager, "ROLE_HOTEL_MANAGER");

        RoomType room = RoomType.builder()
                .roomTypeName("Deluxe Sea View")
                .pricePerNight(6500.0)
                .totalRooms(15)
                .availableRooms(15)
                .maxOccupancy(2)
                .build();

        HttpEntity<RoomType> entity = new HttpEntity<>(room, authHeaders(token));
        ResponseEntity<RoomType> response = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms",
                entity,
                RoomType.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        RoomType created = response.getBody();
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(hotel.getId(), created.getHotelId());
        assertEquals(15, created.getAvailableRooms());
    }

    // =========================================================================
    // 5. INVENTORY & SERVICE-TO-SERVICE AUTHORIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Inventory 5.1: Unauthenticated room reservation is rejected with 401")
    public void testReserveRooms_Unauthenticated_Returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/1/reserve?count=1",
                HttpMethod.PUT,
                new HttpEntity<>(new HttpHeaders()),
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Inventory 5.2: Authenticated customer can reserve available rooms (returns 200)")
    public void testReserveRooms_AuthenticatedCustomer_Returns200() {
        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Booking Hotel")
                .city("Munnar")
                .managerId(601L)
                .status("ACTIVE")
                .build());

        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Standard Double")
                .pricePerNight(3000.0)
                .totalRooms(5)
                .availableRooms(5)
                .build());

        String customerToken = createValidToken(888L, "ROLE_CUSTOMER");

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(customerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/reserve?count=2",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, ((Number) response.getBody().get("remainingAvailable")).intValue());
    }

    @Test
    @DisplayName("Inventory 5.2b: Reservation rejected for inactive hotel property (returns 400)")
    public void testReserveRooms_InactiveProperty_Returns400() {
        HotelProperty inactiveHotel = hotelRepository.save(HotelProperty.builder()
                .name("Inactive Hill Resort")
                .city("Munnar")
                .managerId(602L)
                .status("INACTIVE")
                .build());

        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(inactiveHotel.getId())
                .roomTypeName("Cottage")
                .pricePerNight(4000.0)
                .totalRooms(5)
                .availableRooms(5)
                .build());

        String customerToken = createValidToken(889L, "ROLE_CUSTOMER");
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(customerToken));

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/reserve?count=1",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("message").toString().contains("inactive"));
    }

    @Test
    @DisplayName("Inventory 5.3: Unauthenticated room release is rejected with 401")
    public void testReleaseRooms_Unauthenticated_Returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/1/release?count=1",
                HttpMethod.PUT,
                new HttpEntity<>(new HttpHeaders()),
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Inventory 5.4: Customer cannot arbitrarily release inventory (returns 403)")
    public void testReleaseRooms_CustomerRole_Returns403() {
        String customerToken = createValidToken(888L, "ROLE_CUSTOMER");

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(customerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/1/release?count=1",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Only trusted internal services"));
    }

    @Test
    @DisplayName("Inventory 5.5: Trusted internal service (booking-service) can release inventory (returns 200)")
    public void testReleaseRooms_InternalService_Returns200() {
        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Internal Test Hotel")
                .city("Ooty")
                .managerId(701L)
                .status("ACTIVE")
                .build());

        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Suite")
                .pricePerNight(7000.0)
                .totalRooms(10)
                .availableRooms(7)
                .build());

        String internalToken = createInternalToken("booking-service");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + internalToken);
        headers.set("X-Internal-Token", internalToken);
        headers.set("X-Internal-Service", "booking-service");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/release?count=2",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(9, ((Number) response.getBody().get("availableRooms")).intValue());
    }

    @Test
    @DisplayName("Inventory 5.5b: Direct HTTP call with raw X-Internal-Service header but NO cryptographically signed token is rejected with 401")
    public void testReleaseRooms_DirectHeaderSpoofingWithoutToken_Returns401() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(1L)
                .roomTypeName("Standard")
                .totalRooms(10)
                .availableRooms(5)
                .build());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "booking-service");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/release?count=1",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Inventory 5.5c: Repeated releases cannot inflate inventory beyond totalRooms")
    public void testReleaseRooms_RepeatedRelease_DoesNotExceedTotalRooms() {
        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Capped Hotel")
                .city("Chennai")
                .managerId(705L)
                .status("ACTIVE")
                .build());

        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Executive")
                .totalRooms(10)
                .availableRooms(9)
                .build());

        String internalToken = createInternalToken("booking-service");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + internalToken);
        headers.set("X-Internal-Service", "booking-service");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        // Release 5 rooms when only 1 is reserved (totalRooms=10, available=9)
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/release?count=5",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Inventory must be capped at totalRooms (10), never 14
        assertEquals(10, ((Number) response.getBody().get("availableRooms")).intValue());

        RoomType updated = roomTypeRepository.findById(room.getId()).orElseThrow();
        assertEquals(10, updated.getAvailableRooms());
    }

    @Test
    @DisplayName("Inventory 5.6: Owning hotel manager can release inventory for own room (returns 200)")
    public void testReleaseRooms_OwningManager_Returns200() {
        Long managerId = 702L;
        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Manager Owned Hotel")
                .city("Ooty")
                .managerId(managerId)
                .status("ACTIVE")
                .build());

        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Deluxe")
                .totalRooms(10)
                .availableRooms(5)
                .build());

        String managerToken = createValidToken(managerId, "ROLE_HOTEL_MANAGER");

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/release?count=3",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(8, ((Number) response.getBody().get("availableRooms")).intValue());
    }

    @Test
    @DisplayName("Inventory 5.7: Another hotel manager CANNOT release inventory for rooms they do not own (returns 403)")
    public void testReleaseRooms_AnotherManager_Returns403() {
        Long ownerManager = 703L;
        Long otherManager = 704L;

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Hotel of Owner")
                .city("Goa")
                .managerId(ownerManager)
                .status("ACTIVE")
                .build());

        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Deluxe")
                .totalRooms(10)
                .availableRooms(5)
                .build());

        String otherManagerToken = createValidToken(otherManager, "ROLE_HOTEL_MANAGER");

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(otherManagerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/release?count=1",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Cannot release inventory for a property you do not own"));
    }

    // =========================================================================
    // 6. PROPERTY STATUS & SOFT DEACTIVATION TESTS (BD-08)
    // =========================================================================

    @Test
    @DisplayName("Status 6.1: Owning manager can deactivate and reactivate property (returns 200)")
    public void testUpdatePropertyStatus_OwningManager_Returns200() {
        Long managerId = 801L;
        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Deactivatable Hotel")
                .city("Hyderabad")
                .managerId(managerId)
                .status("ACTIVE")
                .build());

        String managerToken = createValidToken(managerId, "ROLE_HOTEL_MANAGER");

        // Deactivate
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/" + hotel.getId() + "/status?status=INACTIVE",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("INACTIVE", response.getBody().get("status"));

        HotelProperty updated = hotelRepository.findById(hotel.getId()).orElseThrow();
        assertEquals("INACTIVE", updated.getStatus());

        // Reactivate
        ResponseEntity<Map> reactivateResp = restTemplate.exchange(
                getBaseUrl() + "/" + hotel.getId() + "/status?status=ACTIVE",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, reactivateResp.getStatusCode());
        assertEquals("ACTIVE", reactivateResp.getBody().get("status"));
    }

    @Test
    @DisplayName("Status 6.2: Other manager cannot modify status of property they do not own (returns 403)")
    public void testUpdatePropertyStatus_OtherManager_Returns403() {
        Long ownerManager = 802L;
        Long intruderManager = 803L;

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Protected Hotel")
                .city("Goa")
                .managerId(ownerManager)
                .status("ACTIVE")
                .build());

        String intruderToken = createValidToken(intruderManager, "ROLE_HOTEL_MANAGER");

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(intruderToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/" + hotel.getId() + "/status?status=INACTIVE",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // =========================================================================
    // 7. PUBLIC SEARCH & DETAILS REGRESSION TESTS
    // =========================================================================

    @Test
    @DisplayName("Public 7.1: Public traveller search requires NO authentication (returns 200)")
    public void testSearchHotels_Unauthenticated_Returns200() {
        ResponseEntity<List> response = restTemplate.getForEntity(
                getBaseUrl() + "/search?city=Goa",
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("Public 7.2: Public hotel details requires NO authentication (returns 200)")
    public void testGetHotelDetails_Unauthenticated_Returns200() {
        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Public Details Hotel")
                .city("Goa")
                .managerId(999L)
                .status("ACTIVE")
                .build());

        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/" + hotel.getId(),
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().get("hotel"));
    }

    @Test
    @DisplayName("Public 7.3: Search omits sensitive internal fields (managerId, managerName, status) and excludes inactive properties")
    public void testSearchHotels_OmitsInternalFieldsAndInactiveHotels() {
        Long managerId = 910L;
        hotelRepository.save(HotelProperty.builder()
                .name("Active Seaside Retreat")
                .city("Pondicherry")
                .managerId(managerId)
                .managerName("Secret Manager A")
                .status("ACTIVE")
                .starRating(4)
                .build());

        hotelRepository.save(HotelProperty.builder()
                .name("Inactive Ghost Resort")
                .city("Pondicherry")
                .managerId(managerId)
                .managerName("Secret Manager B")
                .status("INACTIVE")
                .starRating(3)
                .build());

        ResponseEntity<List> response = restTemplate.getForEntity(
                getBaseUrl() + "/search?city=Pondicherry",
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> hotels = response.getBody();
        assertNotNull(hotels);

        // Inactive hotel must be excluded
        boolean hasInactive = hotels.stream().anyMatch(h -> "Inactive Ghost Resort".equals(h.get("name")));
        assertFalse(hasInactive, "Inactive hotel should not be returned in public search");

        // Active hotel must be present
        Map<String, Object> found = hotels.stream()
                .filter(h -> "Active Seaside Retreat".equals(h.get("name")))
                .findFirst()
                .orElse(null);
        assertNotNull(found, "Active hotel should be present in public search");

        // Internal fields must be stripped (null or not present)
        assertNull(found.get("managerId"), "managerId must NOT be exposed in public search");
        assertNull(found.get("managerName"), "managerName must NOT be exposed in public search");
        assertNull(found.get("status"), "status must NOT be exposed in public search");
    }

    @Test
    @DisplayName("Public 7.4: Public hotel details endpoint omits sensitive internal fields")
    public void testGetHotelDetails_OmitsInternalFields() {
        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Public Confidential Hotel")
                .city("Jaipur")
                .managerId(920L)
                .managerName("Confidential Manager")
                .status("ACTIVE")
                .build());

        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/" + hotel.getId(),
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> hotelDto = (Map<String, Object>) response.getBody().get("hotel");
        assertNotNull(hotelDto);
        assertNull(hotelDto.get("managerId"), "managerId must NOT be exposed in public details");
        assertNull(hotelDto.get("managerName"), "managerName must NOT be exposed in public details");
        assertNull(hotelDto.get("status"), "status must NOT be exposed in public details");
    }

    // =========================================================================
    // 8. INTERNAL SERVICE LOOKUP & PROTECTION TESTS
    // =========================================================================

    @Test
    @DisplayName("Internal 8.1: Authenticated internal service can retrieve full hotel entity with managerId and status")
    public void testGetHotelInternalDetails_InternalService_ReturnsFullEntity() {
        Long managerId = 930L;
        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Internal Lookup Hotel")
                .city("Shimla")
                .managerId(managerId)
                .managerName("Known Manager")
                .status("ACTIVE")
                .build());

        String internalToken = createInternalToken("booking-service");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + internalToken);
        headers.set("X-Internal-Service", "booking-service");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<HotelProperty> response = restTemplate.exchange(
                getBaseUrl() + "/internal/" + hotel.getId(),
                HttpMethod.GET,
                entity,
                HotelProperty.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(managerId, response.getBody().getManagerId());
        assertEquals("ACTIVE", response.getBody().getStatus());
    }

    @Test
    @DisplayName("Internal 8.2: Unauthenticated caller cannot access internal hotel details (returns 401)")
    public void testGetHotelInternalDetails_Unauthenticated_Returns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/internal/1",
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Internal 8.3: Customer or manager cannot access internal hotel details (returns 403)")
    public void testGetHotelInternalDetails_CustomerOrManager_Returns403() {
        String customerToken = createValidToken(940L, "ROLE_CUSTOMER");
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(customerToken));

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/internal/1",
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
