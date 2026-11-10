package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.BasePriceUpdateRequestDto;
import com.traveleasy.hotel.dto.PriceOverrideRequestDto;
import com.traveleasy.hotel.dto.RoomTypeDto;
import com.traveleasy.hotel.model.HotelBookingAllocation;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.HotelReservation;
import com.traveleasy.hotel.model.RoomPriceOverride;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelBookingAllocationRepository;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.HotelReservationRepository;
import com.traveleasy.hotel.repository.RoomPriceOverrideRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
import com.traveleasy.hotel.service.HotelPricingService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BasePriceManagementTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HotelPropertyRepository hotelRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomPriceOverrideRepository priceOverrideRepository;

    @Autowired
    private HotelBookingAllocationRepository allocationRepository;

    @Autowired
    private HotelReservationRepository reservationRepository;

    @Autowired
    private HotelPricingService pricingService;

    @Value("${jwt.secret:TravelEasySuperSecretKeyForJWTSigning12345678901234567890}")
    private String jwtSecret;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/hotels";
    }

    @BeforeEach
    public void setup() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
    }

    private String createTestToken(Long userId, String role, String email, String fullName, long ttlMillis) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
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

    private String createManagerToken(Long userId) {
        return createTestToken(userId, "ROLE_HOTEL_MANAGER", "manager" + userId + "@hotel.com", "Manager " + userId, 3600000);
    }

    private String createAdminToken() {
        return createTestToken(1L, "ROLE_ADMIN", "admin@traveleasy.com", "Platform Administrator", 3600000);
    }

    private String createCustomerToken(Long userId) {
        return createTestToken(userId, "ROLE_CUSTOMER", "customer" + userId + "@user.com", "Customer " + userId, 3600000);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private HotelProperty createProperty(Long managerId, String name, String city) {
        HotelProperty property = HotelProperty.builder()
                .managerId(managerId)
                .managerName("Manager " + managerId)
                .name(name + " " + UUID.randomUUID().toString().substring(0, 8))
                .city(city)
                .address("100 Shore Drive")
                .starRating(4)
                .description("Luxury property")
                .status("ACTIVE")
                .build();
        return hotelRepository.save(property);
    }

    private RoomType createRoom(Long hotelId, String name, double price, int totalRooms) {
        RoomType room = RoomType.builder()
                .hotelId(hotelId)
                .roomTypeName(name + " " + UUID.randomUUID().toString().substring(0, 6))
                .pricePerNight(price)
                .totalRooms(totalRooms)
                .availableRooms(totalRooms)
                .maxOccupancy(2)
                .status("ACTIVE")
                .build();
        return roomTypeRepository.save(room);
    }

    // =========================================================================
    // 13 REQUIRED TEST SCENARIOS (WP-07.2 SECTION 9)
    // =========================================================================

    @Test
    @DisplayName("WP-07.2 Test 1: Successful base-price update returns 200 OK and updated room type")
    public void test1_successfulBasePriceUpdate() {
        Long managerId = 8801L;
        HotelProperty hotel = createProperty(managerId, "Grand Resort", "Goa");
        RoomType room = createRoom(hotel.getId(), "Deluxe King", 4500.0, 10);

        BasePriceUpdateRequestDto dto = BasePriceUpdateRequestDto.builder()
                .basePrice(5500.0)
                .build();

        HttpEntity<BasePriceUpdateRequestDto> entity = new HttpEntity<>(dto, authHeaders(createManagerToken(managerId)));
        ResponseEntity<RoomTypeDto> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/base-price",
                HttpMethod.PUT,
                entity,
                RoomTypeDto.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(5500.0, response.getBody().getPricePerNight());
        assertEquals("ACTIVE", response.getBody().getStatus());

        // Verify DB update
        RoomType updatedInDb = roomTypeRepository.findById(room.getId()).orElseThrow();
        assertEquals(5500.0, updatedInDb.getPricePerNight());
    }

    @Test
    @DisplayName("WP-07.2 Test 2: Missing base price rejected with 400 Bad Request")
    public void test2_missingBasePriceRejected() {
        Long managerId = 8802L;
        HotelProperty hotel = createProperty(managerId, "Seaside Inn", "Mumbai");
        RoomType room = createRoom(hotel.getId(), "Standard Room", 3000.0, 5);

        String jsonPayload = "{}";
        HttpHeaders headers = authHeaders(createManagerToken(managerId));
        HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/base-price",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("WP-07.2 Test 3: Zero base price rejected with 400 Bad Request")
    public void test3_zeroPriceRejected() {
        Long managerId = 8803L;
        HotelProperty hotel = createProperty(managerId, "Hilltop Lodge", "Shimla");
        RoomType room = createRoom(hotel.getId(), "Cozy Room", 2500.0, 4);

        BasePriceUpdateRequestDto dto = BasePriceUpdateRequestDto.builder()
                .basePrice(0.0)
                .build();

        HttpEntity<BasePriceUpdateRequestDto> entity = new HttpEntity<>(dto, authHeaders(createManagerToken(managerId)));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/base-price",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("WP-07.2 Test 4: Negative base price rejected with 400 Bad Request")
    public void test4_negativePriceRejected() {
        Long managerId = 8804L;
        HotelProperty hotel = createProperty(managerId, "Valley View", "Manali");
        RoomType room = createRoom(hotel.getId(), "Family Suite", 4000.0, 6);

        BasePriceUpdateRequestDto dto = BasePriceUpdateRequestDto.builder()
                .basePrice(-500.0)
                .build();

        HttpEntity<BasePriceUpdateRequestDto> entity = new HttpEntity<>(dto, authHeaders(createManagerToken(managerId)));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/base-price",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("WP-07.2 Test 5: Invalid non-numeric monetary values rejected with 400 Bad Request")
    public void test5_invalidMonetaryValuesRejected() {
        Long managerId = 8805L;
        HotelProperty hotel = createProperty(managerId, "City Hotel", "Delhi");
        RoomType room = createRoom(hotel.getId(), "Executive Room", 3500.0, 8);

        String invalidJson = "{\"basePrice\": \"invalid-text\"}";
        HttpHeaders headers = authHeaders(createManagerToken(managerId));
        HttpEntity<String> entity = new HttpEntity<>(invalidJson, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/base-price",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("WP-07.2 Test 6: Nonexistent room type rejected with 404 Not Found")
    public void test6_nonexistentRoomTypeRejected() {
        Long managerId = 8806L;
        BasePriceUpdateRequestDto dto = BasePriceUpdateRequestDto.builder()
                .basePrice(5000.0)
                .build();

        HttpEntity<BasePriceUpdateRequestDto> entity = new HttpEntity<>(dto, authHeaders(createManagerToken(managerId)));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/9999999/base-price",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("WP-07.2 Test 7: Archived room type rejected with 400 Bad Request")
    public void test7_archivedRoomTypeRejected() {
        Long managerId = 8807L;
        HotelProperty hotel = createProperty(managerId, "Old Heritage", "Jaipur");
        RoomType room = createRoom(hotel.getId(), "Heritage Suite", 6000.0, 4);
        room.setStatus("ARCHIVED");
        roomTypeRepository.save(room);

        BasePriceUpdateRequestDto dto = BasePriceUpdateRequestDto.builder()
                .basePrice(7000.0)
                .build();

        HttpEntity<BasePriceUpdateRequestDto> entity = new HttpEntity<>(dto, authHeaders(createManagerToken(managerId)));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/base-price",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("archived"));
    }

    @Test
    @DisplayName("WP-07.2 Test 8: Unauthorized user (unauthenticated or customer) rejected (401/403)")
    public void test8_unauthorizedUserRejected() {
        Long managerId = 8808L;
        HotelProperty hotel = createProperty(managerId, "Boutique Hotel", "Bangalore");
        RoomType room = createRoom(hotel.getId(), "Studio", 3200.0, 5);

        BasePriceUpdateRequestDto dto = BasePriceUpdateRequestDto.builder()
                .basePrice(4000.0)
                .build();

        // 1. Unauthenticated request -> 401 Unauthorized
        HttpEntity<BasePriceUpdateRequestDto> unauthEntity = new HttpEntity<>(dto, authHeaders(null));
        ResponseEntity<Map> unauthResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/base-price",
                HttpMethod.PUT,
                unauthEntity,
                Map.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, unauthResp.getStatusCode());

        // 2. Customer token -> 403 Forbidden
        HttpEntity<BasePriceUpdateRequestDto> customerEntity = new HttpEntity<>(dto, authHeaders(createCustomerToken(9999L)));
        ResponseEntity<Map> customerResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/base-price",
                HttpMethod.PUT,
                customerEntity,
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, customerResp.getStatusCode());
    }

    @Test
    @DisplayName("WP-07.2 Test 9: Cross-property ownership violation rejected with 403 Forbidden")
    public void test9_crossPropertyOwnershipViolationRejected() {
        Long manager1Id = 8809L;
        Long manager2Id = 8810L;

        HotelProperty hotel1 = createProperty(manager1Id, "Manager 1 Palace", "Udaipur");
        RoomType room1 = createRoom(hotel1.getId(), "Royal Suite", 12000.0, 2);

        BasePriceUpdateRequestDto dto = BasePriceUpdateRequestDto.builder()
                .basePrice(15000.0)
                .build();

        // Manager 2 tries to update Manager 1's room
        HttpEntity<BasePriceUpdateRequestDto> entity = new HttpEntity<>(dto, authHeaders(createManagerToken(manager2Id)));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room1.getId() + "/base-price",
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("WP-07.2 Test 10: Existing price overrides remain completely unchanged after base price update")
    public void test10_existingPriceOverridesRemainUnchanged() {
        Long managerId = 8811L;
        HotelProperty hotel = createProperty(managerId, "Beachfront Resort", "Kochi");
        RoomType room = createRoom(hotel.getId(), "Ocean Villa", 8000.0, 3);

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        RoomPriceOverride override = RoomPriceOverride.builder()
                .hotelId(hotel.getId())
                .roomId(room.getId())
                .overrideDate(tomorrow)
                .pricePerNight(11000.0)
                .build();
        priceOverrideRepository.save(override);

        // Update base price to 9500
        BasePriceUpdateRequestDto dto = BasePriceUpdateRequestDto.builder()
                .basePrice(9500.0)
                .build();

        HttpEntity<BasePriceUpdateRequestDto> entity = new HttpEntity<>(dto, authHeaders(createManagerToken(managerId)));
        ResponseEntity<RoomTypeDto> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/base-price",
                HttpMethod.PUT,
                entity,
                RoomTypeDto.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify override record was NOT rewritten or modified
        RoomPriceOverride existingOverride = priceOverrideRepository
                .findByRoomIdAndOverrideDate(room.getId(), tomorrow)
                .orElseThrow();
        assertEquals(11000.0, existingOverride.getPricePerNight(), "Date-specific override must be preserved untouched");
    }

    @Test
    @DisplayName("WP-07.2 Test 11: Effective price uses updated base price when no override exists")
    public void test11_effectivePriceUsesUpdatedBasePriceWhenNoOverride() {
        Long managerId = 8812L;
        HotelProperty hotel = createProperty(managerId, "Mountain Lodge", "Ooty");
        RoomType room = createRoom(hotel.getId(), "Pine Cottage", 4000.0, 5);

        // Update base price to 5200
        BasePriceUpdateRequestDto dto = BasePriceUpdateRequestDto.builder()
                .basePrice(5200.0)
                .build();

        HttpEntity<BasePriceUpdateRequestDto> entity = new HttpEntity<>(dto, authHeaders(createManagerToken(managerId)));
        ResponseEntity<RoomTypeDto> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/base-price",
                HttpMethod.PUT,
                entity,
                RoomTypeDto.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Check effective price for next week (no override)
        LocalDate targetDate = LocalDate.now().plusDays(7);
        RoomType freshRoom = roomTypeRepository.findById(room.getId()).orElseThrow();
        double effectivePrice = pricingService.resolveEffectivePrice(freshRoom, targetDate);

        assertEquals(5200.0, effectivePrice, "Date without override must use updated base price");
    }

    @Test
    @DisplayName("WP-07.2 Test 12: Effective price continues using existing override even after base price update")
    public void test12_effectivePriceContinuesUsingExistingOverride() {
        Long managerId = 8813L;
        HotelProperty hotel = createProperty(managerId, "Desert Oasis", "Jaisalmer");
        RoomType room = createRoom(hotel.getId(), "Luxury Tent", 6000.0, 8);

        LocalDate festivalDate = LocalDate.now().plusDays(3);
        RoomPriceOverride override = RoomPriceOverride.builder()
                .hotelId(hotel.getId())
                .roomId(room.getId())
                .overrideDate(festivalDate)
                .pricePerNight(14000.0)
                .build();
        priceOverrideRepository.save(override);

        // Update base price to 7500
        BasePriceUpdateRequestDto dto = BasePriceUpdateRequestDto.builder()
                .basePrice(7500.0)
                .build();

        HttpEntity<BasePriceUpdateRequestDto> entity = new HttpEntity<>(dto, authHeaders(createManagerToken(managerId)));
        ResponseEntity<RoomTypeDto> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/base-price",
                HttpMethod.PUT,
                entity,
                RoomTypeDto.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Check effective price for festivalDate (has override)
        RoomType freshRoom = roomTypeRepository.findById(room.getId()).orElseThrow();
        double effectivePrice = pricingService.resolveEffectivePrice(freshRoom, festivalDate);

        assertEquals(14000.0, effectivePrice, "Date with override must continue using the override price");
    }

    @Test
    @DisplayName("WP-07.2 Test 13: Historical booking prices remain completely unchanged after base price update")
    public void test13_historicalBookingPricesRemainUnchanged() {
        Long managerId = 8814L;
        HotelProperty hotel = createProperty(managerId, "Lakeview Grand", "Nainital");
        RoomType room = createRoom(hotel.getId(), "Balcony Room", 5000.0, 5);

        String bookingRef = "BK-HIST-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate checkIn = LocalDate.now().plusDays(2);
        LocalDate checkOut = LocalDate.now().plusDays(4);

        HotelReservation parent = HotelReservation.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .status("CONFIRMED")
                .totalPrice(20000.0)
                .build();
        parent = reservationRepository.save(parent);

        HotelBookingAllocation allocation = HotelBookingAllocation.builder()
                .reservation(parent)
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .roomId(room.getId())
                .roomCount(2)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .status("CONFIRMED")
                .totalPrice(20000.0) // 2 nights * 5000 * 2 rooms
                .build();
        allocationRepository.save(allocation);

        // Update base price to 9000
        BasePriceUpdateRequestDto dto = BasePriceUpdateRequestDto.builder()
                .basePrice(9000.0)
                .build();

        HttpEntity<BasePriceUpdateRequestDto> entity = new HttpEntity<>(dto, authHeaders(createManagerToken(managerId)));
        ResponseEntity<RoomTypeDto> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/base-price",
                HttpMethod.PUT,
                entity,
                RoomTypeDto.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify stored allocation totalPrice is unchanged
        HotelBookingAllocation savedAlloc = allocationRepository.findByBookingRef(bookingRef).orElseThrow();
        assertEquals(20000.0, savedAlloc.getTotalPrice(), "Committed historical booking price must remain immutable");
    }
}
