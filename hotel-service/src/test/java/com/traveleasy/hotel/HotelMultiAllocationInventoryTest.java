package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.ConsolidatedReservationRequestDto;
import com.traveleasy.hotel.dto.DateWiseReserveRequestDto;
import com.traveleasy.hotel.dto.HotelReservationResponseDto;
import com.traveleasy.hotel.dto.PriceOverrideRequestDto;
import com.traveleasy.hotel.dto.ReservationAllocationRequestDto;
import com.traveleasy.hotel.model.HotelBookingAllocation;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.HotelReservation;
import com.traveleasy.hotel.model.RoomDailyInventory;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelBookingAllocationRepository;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.HotelReservationRepository;
import com.traveleasy.hotel.repository.RoomDailyInventoryRepository;
import com.traveleasy.hotel.repository.RoomPriceOverrideRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
import com.traveleasy.hotel.security.HotelSecurityService;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HotelMultiAllocationInventoryTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HotelPropertyRepository hotelRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomDailyInventoryRepository dailyInventoryRepository;

    @Autowired
    private HotelReservationRepository reservationRepository;

    @Autowired
    private HotelBookingAllocationRepository allocationRepository;

    @Autowired
    private RoomPriceOverrideRepository priceOverrideRepository;

    @Autowired
    private HotelPricingService pricingService;

    @Autowired
    private com.traveleasy.hotel.service.HotelReservationSchemaInitializer schemaInitializer;

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

    private String createInternalServiceToken() {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject("booking-service")
                .setIssuer("traveleasy-internal")
                .claim("role", "INTERNAL_SERVICE")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 300000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private String createManagerToken(Long userId) {
        return createTestToken(userId, "ROLE_HOTEL_MANAGER", "manager" + userId + "@hotel.com", "Manager " + userId, 3600000);
    }

    private String createAdminToken() {
        return createTestToken(1L, "ROLE_ADMIN", "admin@traveleasy.com", "Platform Administrator", 3600000);
    }

    private HttpHeaders internalHeaders() {
        String token = createInternalServiceToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("X-Internal-Token", token);
        headers.set("X-Internal-Service", "booking-service");
        return headers;
    }

    private HttpHeaders userHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private HotelProperty createProperty(Long managerId, String name, String city, String status) {
        HotelProperty property = HotelProperty.builder()
                .managerId(managerId)
                .managerName("Manager " + managerId)
                .name(name + " " + UUID.randomUUID().toString().substring(0, 8))
                .city(city)
                .address("123 Grand Boulevard")
                .starRating(5)
                .description("Luxury multi-allocation hotel")
                .status(status != null ? status : "ACTIVE")
                .build();
        return hotelRepository.save(property);
    }

    private RoomType createRoom(Long hotelId, String name, double price, int totalRooms, String status) {
        RoomType room = RoomType.builder()
                .hotelId(hotelId)
                .roomTypeName(name + " " + UUID.randomUUID().toString().substring(0, 6))
                .pricePerNight(price)
                .totalRooms(totalRooms)
                .availableRooms(totalRooms)
                .maxOccupancy(2)
                .status(status != null ? status : "ACTIVE")
                .build();
        return roomTypeRepository.save(room);
    }

    // =========================================================================
    // 1. RESERVATION CREATION TESTS (WP-HB-04)
    // =========================================================================

    @Test
    @DisplayName("WP-HB-04 1.1: Successful reservation with single allocation via consolidated endpoint")
    public void testSuccessfulReservation_SingleAllocation() {
        HotelProperty hotel = createProperty(10L, "SingleAllocHotel", "Hyderabad", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Deluxe Room", 2500.0, 5, "ACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(8); // 3 nights

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 2)))
                .build();

        ResponseEntity<HotelReservationResponseDto> response = restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, internalHeaders()),
                HotelReservationResponseDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        HotelReservationResponseDto body = response.getBody();
        assertNotNull(body);
        assertEquals(bookingRef, body.getBookingRef());
        assertEquals("HELD", body.getStatus());
        assertEquals(3, body.getTotalNights());
        assertEquals(1, body.getAllocations().size());
        assertEquals(2, body.getAllocations().get(0).getQuantity());
        assertEquals(15000.0, body.getTotalPrice()); // 2 rooms * 3 nights * 2500 = 15000.0
        assertFalse(body.getIdempotent());

        // Verify parent in DB
        HotelReservation parent = reservationRepository.findByBookingRefWithAllocations(bookingRef).orElse(null);
        assertNotNull(parent);
        assertEquals("HELD", parent.getStatus());
        assertEquals(1, parent.getAllocations().size());

        // Verify held inventory in DB
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), d).orElse(null);
            assertNotNull(daily);
            assertEquals(2, daily.getHeldRooms());
            assertEquals(3, daily.getAvailableRooms());
        }
    }

    @Test
    @DisplayName("WP-HB-04 1.2: Successful reservation with multiple distinct room types")
    public void testSuccessfulReservation_MultipleDistinctRoomTypes() {
        HotelProperty hotel = createProperty(11L, "MultiRoomHotel", "Bangalore", "ACTIVE");
        RoomType room1 = createRoom(hotel.getId(), "Standard Suite", 3000.0, 5, "ACTIVE");
        RoomType room2 = createRoom(hotel.getId(), "Executive Suite", 5000.0, 4, "ACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = LocalDate.now().plusDays(12); // 2 nights

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(
                        new ReservationAllocationRequestDto(room1.getId(), 2),
                        new ReservationAllocationRequestDto(room2.getId(), 1)
                ))
                .build();

        ResponseEntity<HotelReservationResponseDto> response = restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, internalHeaders()),
                HotelReservationResponseDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        HotelReservationResponseDto body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.getAllocations().size());

        // Total: (2 rooms * 2 nights * 3000) + (1 room * 2 nights * 5000) = 12000 + 10000 = 22000
        assertEquals(22000.0, body.getTotalPrice());

        // Verify allocations in DB
        List<HotelBookingAllocation> allocs = allocationRepository.findByReservationBookingRefOrderByIdAsc(bookingRef);
        assertEquals(2, allocs.size());
        assertEquals(bookingRef, allocs.get(0).getBookingRef());
        assertEquals(bookingRef, allocs.get(1).getBookingRef());

        // Verify daily inventory for room 1 (held = 2) and room 2 (held = 1)
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomDailyInventory daily1 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room1.getId(), d).orElseThrow();
            RoomDailyInventory daily2 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room2.getId(), d).orElseThrow();
            assertEquals(2, daily1.getHeldRooms());
            assertEquals(1, daily2.getHeldRooms());
        }
    }

    @Test
    @DisplayName("WP-HB-04 1.3: Duplicate room IDs are normalized and quantities aggregated")
    public void testDuplicateRoomIds_NormalizedAndAggregated() {
        HotelProperty hotel = createProperty(12L, "DuplicateRoomHotel", "Mumbai", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "City View King", 4000.0, 10, "ACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate checkIn = LocalDate.now().plusDays(15);
        LocalDate checkOut = LocalDate.now().plusDays(17); // 2 nights

        // Send two allocations with same roomId
        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(
                        new ReservationAllocationRequestDto(room.getId(), 2),
                        new ReservationAllocationRequestDto(room.getId(), 1)
                ))
                .build();

        ResponseEntity<HotelReservationResponseDto> response = restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, internalHeaders()),
                HotelReservationResponseDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        HotelReservationResponseDto body = response.getBody();
        assertNotNull(body);
        // Normalized into 1 allocation with quantity = 3
        assertEquals(1, body.getAllocations().size());
        assertEquals(3, body.getAllocations().get(0).getQuantity());

        // Price: 3 rooms * 2 nights * 4000 = 24000.0
        assertEquals(24000.0, body.getTotalPrice());

        // Verify held rooms = 3 in inventory
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), d).orElseThrow();
            assertEquals(3, daily.getHeldRooms());
        }
    }

    @Test
    @DisplayName("WP-HB-04 1.4: Inactive hotel is rejected")
    public void testInactiveHotel_Rejected() {
        HotelProperty hotel = createProperty(13L, "InactiveHotel", "Kochi", "INACTIVE");
        RoomType room = createRoom(hotel.getId(), "Standard", 1500.0, 5, "ACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("inactive hotel property"));
    }

    @Test
    @DisplayName("WP-HB-04 1.5: Inactive room type is rejected")
    public void testInactiveRoomType_Rejected() {
        HotelProperty hotel = createProperty(14L, "ActiveHotelInactiveRoom", "Chennai", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "UnderRenovation", 2000.0, 5, "INACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("not active"));
    }

    @Test
    @DisplayName("WP-HB-04 1.6: Invalid stay dates are rejected")
    public void testInvalidStayDates_Rejected() {
        HotelProperty hotel = createProperty(15L, "DatesHotel", "Goa", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Beach Villa", 6000.0, 5, "ACTIVE");

        // 1. Checkout before checkin
        String ref1 = "TE-HB-INV1";
        ConsolidatedReservationRequestDto req1 = ConsolidatedReservationRequestDto.builder()
                .bookingRef(ref1)
                .hotelId(hotel.getId())
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(3))
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        ResponseEntity<Map> resp1 = restTemplate.exchange(
                getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req1, internalHeaders()), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp1.getStatusCode());

        // 2. Past checkin date
        String ref2 = "TE-HB-INV2";
        ConsolidatedReservationRequestDto req2 = ConsolidatedReservationRequestDto.builder()
                .bookingRef(ref2)
                .hotelId(hotel.getId())
                .checkInDate(LocalDate.now().minusDays(2))
                .checkOutDate(LocalDate.now().plusDays(2))
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        ResponseEntity<Map> resp2 = restTemplate.exchange(
                getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req2, internalHeaders()), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp2.getStatusCode());
    }

    @Test
    @DisplayName("WP-HB-04 1.7: Correct nightly price breakdown and price override snapshots")
    public void testPriceSnapshots_WithOverride() {
        HotelProperty hotel = createProperty(16L, "PricingHotel", "Pune", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Studio Suite", 2000.0, 5, "ACTIVE");

        LocalDate checkIn = LocalDate.now().plusDays(20);
        LocalDate checkOut = LocalDate.now().plusDays(22); // 2 nights

        // Set date-specific override for night 1: 3500.0
        pricingService.setPriceOverride(room.getId(),
                PriceOverrideRequestDto.builder().overrideDate(checkIn).pricePerNight(3500.0).build(),
                null);

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        ResponseEntity<HotelReservationResponseDto> response = restTemplate.exchange(
                getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        HotelReservationResponseDto body = response.getBody();
        assertNotNull(body);
        // Total = 3500 (override on day 1) + 2000 (base on day 2) = 5500.0
        assertEquals(5500.0, body.getTotalPrice());
        assertEquals(2, body.getAllocations().get(0).getNightlyPriceBreakdown().size());
        assertEquals(3500.0, body.getAllocations().get(0).getNightlyPriceBreakdown().get(0).getRate());
        assertTrue(body.getAllocations().get(0).getNightlyPriceBreakdown().get(0).getIsOverride());
        assertEquals(2000.0, body.getAllocations().get(0).getNightlyPriceBreakdown().get(1).getRate());
        assertFalse(body.getAllocations().get(0).getNightlyPriceBreakdown().get(1).getIsOverride());
    }

    // =========================================================================
    // 2. ATOMICITY & ALL-OR-NOTHING INVENTORY TESTS
    // =========================================================================

    @Test
    @DisplayName("WP-HB-04 2.1: Insufficient availability on second allocation rolls back all allocations atomically")
    public void testInsufficientAvailability_AtomicallyRollsBack() {
        HotelProperty hotel = createProperty(20L, "AtomicityHotel", "Jaipur", "ACTIVE");
        RoomType roomAvailable = createRoom(hotel.getId(), "Room With Plenty", 1500.0, 5, "ACTIVE");
        RoomType roomScarce = createRoom(hotel.getId(), "Room Almost Full", 3000.0, 1, "ACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate checkIn = LocalDate.now().plusDays(25);
        LocalDate checkOut = LocalDate.now().plusDays(27); // 2 nights

        // roomAvailable needs 2 (available is 5 -> OK)
        // roomScarce needs 2 (available is 1 -> FAILS)
        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(
                        new ReservationAllocationRequestDto(roomAvailable.getId(), 2),
                        new ReservationAllocationRequestDto(roomScarce.getId(), 2)
                ))
                .build();

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Insufficient room inventory"));

        // Verify ATOMICITY: No parent or child reservation records were committed
        assertFalse(reservationRepository.existsByBookingRef(bookingRef));
        assertEquals(0, allocationRepository.findByReservationBookingRef(bookingRef).size());

        // Verify inventory for roomAvailable: heldRooms must be 0 (no partial hold!)
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(roomAvailable.getId(), d).orElse(null);
            if (daily != null) {
                assertEquals(0, daily.getHeldRooms(), "Partial hold detected! Inventory for roomAvailable was not rolled back.");
            }
        }
    }

    // =========================================================================
    // 3. IDEMPOTENCY TESTS (PARENT LEVEL)
    // =========================================================================

    @Test
    @DisplayName("WP-HB-04 3.1: Idempotency with identical request returns existing reservation without re-incrementing")
    public void testIdempotency_SameBookingRefSameRequest() {
        HotelProperty hotel = createProperty(30L, "IdempotencyHotel", "Delhi", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Executive Room", 3500.0, 10, "ACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate checkIn = LocalDate.now().plusDays(30);
        LocalDate checkOut = LocalDate.now().plusDays(32);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 2)))
                .build();

        // Call 1
        ResponseEntity<HotelReservationResponseDto> resp1 = restTemplate.exchange(
                getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);
        assertEquals(HttpStatus.OK, resp1.getStatusCode());
        assertFalse(resp1.getBody().getIdempotent());

        // Call 2 (identical request)
        ResponseEntity<HotelReservationResponseDto> resp2 = restTemplate.exchange(
                getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);
        assertEquals(HttpStatus.OK, resp2.getStatusCode());
        assertTrue(resp2.getBody().getIdempotent());
        assertEquals(resp1.getBody().getTotalPrice(), resp2.getBody().getTotalPrice());

        // Inventory must ONLY be held once (held = 2, not 4!)
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), d).orElseThrow();
            assertEquals(2, daily.getHeldRooms(), "Held rooms should be exactly 2 for idempotent retry");
        }
    }

    @Test
    @DisplayName("WP-HB-04 3.2: Reusing bookingRef with different parameters is rejected with conflict")
    public void testIdempotency_SameBookingRefDifferentRequest_Rejected() {
        HotelProperty hotel = createProperty(31L, "ConflictHotel", "Delhi", "ACTIVE");
        RoomType room1 = createRoom(hotel.getId(), "Standard", 2000.0, 10, "ACTIVE");
        RoomType room2 = createRoom(hotel.getId(), "Deluxe", 4000.0, 10, "ACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate checkIn = LocalDate.now().plusDays(35);
        LocalDate checkOut = LocalDate.now().plusDays(37);

        ConsolidatedReservationRequestDto req1 = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room1.getId(), 1)))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req1, internalHeaders()), HotelReservationResponseDto.class);

        // Call 2 with same bookingRef but room2 instead of room1
        ConsolidatedReservationRequestDto req2 = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room2.getId(), 1)))
                .build();

        ResponseEntity<Map> resp2 = restTemplate.exchange(
                getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req2, internalHeaders()), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp2.getStatusCode());
        assertTrue(resp2.getBody().get("message").toString().contains("already in use with different reservation parameters"));
    }

    @Test
    @DisplayName("WP-HB-04 3.3: Released reservation cannot be re-reserved")
    public void testIdempotency_ReleasedReservationCannotBeReheld() {
        HotelProperty hotel = createProperty(32L, "ReholdHotel", "Agra", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Taj View", 5000.0, 5, "ACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate checkIn = LocalDate.now().plusDays(40);
        LocalDate checkOut = LocalDate.now().plusDays(42);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        // 1. Reserve
        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // 2. Release
        restTemplate.exchange(getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef, HttpMethod.POST, new HttpEntity<>(internalHeaders()), Map.class);

        // 3. Try re-holding with same bookingRef
        ResponseEntity<Map> reholdResp = restTemplate.exchange(
                getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, reholdResp.getStatusCode());
        assertTrue(reholdResp.getBody().get("message").toString().contains("previously released"));
    }

    // =========================================================================
    // 4. CONCURRENCY & LOCKING TESTS
    // =========================================================================

    @Test
    @DisplayName("WP-HB-04 4.1: Concurrent reservations for overlapping capacity cannot oversell")
    public void testConcurrency_CannotOversellInventory() throws Exception {
        HotelProperty hotel = createProperty(40L, "ConcurrencyHotel", "Udaipur", "ACTIVE");
        int initialCapacity = 3;
        RoomType room = createRoom(hotel.getId(), "Palace Room", 8000.0, initialCapacity, "ACTIVE");

        LocalDate checkIn = LocalDate.now().plusDays(45);
        LocalDate checkOut = LocalDate.now().plusDays(48); // 3 nights

        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final String bRef = "TE-CONC-" + i + "-" + UUID.randomUUID().toString().substring(0, 6);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                            .bookingRef(bRef)
                            .hotelId(hotel.getId())
                            .checkInDate(checkIn)
                            .checkOutDate(checkOut)
                            .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                            .build();

                    ResponseEntity<String> resp = restTemplate.exchange(
                            getBaseUrl() + "/reservations",
                            HttpMethod.POST,
                            new HttpEntity<>(req, internalHeaders()),
                            String.class);
                    statusCodes.add(resp.getStatusCode().value());
                } catch (Exception e) {
                    statusCodes.add(500);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long successCount = statusCodes.stream().filter(s -> s == 200).count();
        long rejectedCount = statusCodes.stream().filter(s -> s == 400).count();

        assertEquals(3, successCount, "Exactly 3 reservations should succeed since totalRooms = 3");
        assertEquals(3, rejectedCount, "Exactly 3 reservations should be rejected due to insufficient inventory");

        // Verify heldRooms in DB is exactly 3 (never 4, 5, or 6!)
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), d).orElseThrow();
            assertEquals(3, daily.getHeldRooms(), "Inventory must not be oversold");
            assertEquals(0, daily.getAvailableRooms());
        }
    }

    @Test
    @DisplayName("WP-HB-04 4.2: Concurrent requests with same bookingRef do not create duplicate holds")
    public void testConcurrency_ConcurrentSameBookingRef_NoDuplicateHolds() throws Exception {
        HotelProperty hotel = createProperty(41L, "RaceIdempHotel", "Udaipur", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Lake View", 7000.0, 5, "ACTIVE");

        String bookingRef = "TE-RACE-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate checkIn = LocalDate.now().plusDays(50);
        LocalDate checkOut = LocalDate.now().plusDays(52);

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                            .bookingRef(bookingRef)
                            .hotelId(hotel.getId())
                            .checkInDate(checkIn)
                            .checkOutDate(checkOut)
                            .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 2)))
                            .build();

                    ResponseEntity<HotelReservationResponseDto> resp = restTemplate.exchange(
                            getBaseUrl() + "/reservations",
                            HttpMethod.POST,
                            new HttpEntity<>(req, internalHeaders()),
                            HotelReservationResponseDto.class);
                    statusCodes.add(resp.getStatusCode().value());
                } catch (Exception e) {
                    statusCodes.add(500);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // All calls should return 200 (1 created, others idempotent)
        long successCount = statusCodes.stream().filter(s -> s == 200).count();
        assertEquals(4, successCount);

        // Exactly one parent reservation in DB
        HotelReservation res = reservationRepository.findByBookingRef(bookingRef).orElse(null);
        assertNotNull(res);

        // Exactly 2 rooms held in inventory, NOT 8!
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), d).orElseThrow();
            assertEquals(2, daily.getHeldRooms(), "Inventory must not be multiplied by concurrent retries");
        }
    }

    // =========================================================================
    // 5. CONFIRMATION & RELEASE LIFECYCLE TESTS
    // =========================================================================

    @Test
    @DisplayName("WP-HB-04 5.1: Confirmation moves heldRooms to bookedRooms for all child allocations")
    public void testConfirmation_TransitionsAllAllocationsHeldToBooked() {
        HotelProperty hotel = createProperty(50L, "ConfirmHotel", "Varanasi", "ACTIVE");
        RoomType room1 = createRoom(hotel.getId(), "Ghat View Standard", 3000.0, 5, "ACTIVE");
        RoomType room2 = createRoom(hotel.getId(), "Ghat View Deluxe", 6000.0, 5, "ACTIVE");

        String bookingRef = "TE-CONF-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate checkIn = LocalDate.now().plusDays(55);
        LocalDate checkOut = LocalDate.now().plusDays(58); // 3 nights

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(
                        new ReservationAllocationRequestDto(room1.getId(), 2),
                        new ReservationAllocationRequestDto(room2.getId(), 1)
                ))
                .build();

        // 1. Reserve
        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // 2. Confirm
        ResponseEntity<Map> confirmResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.OK, confirmResp.getStatusCode());
        assertEquals("CONFIRMED", confirmResp.getBody().get("status"));

        // Verify parent in DB is CONFIRMED
        HotelReservation parent = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        assertEquals("CONFIRMED", parent.getStatus());

        // Verify all children in DB are CONFIRMED
        List<HotelBookingAllocation> allocs = allocationRepository.findByReservationBookingRef(bookingRef);
        assertEquals(2, allocs.size());
        assertTrue(allocs.stream().allMatch(a -> "CONFIRMED".equals(a.getStatus())));

        // Verify daily inventory: heldRooms = 0, bookedRooms = requested
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomDailyInventory daily1 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room1.getId(), d).orElseThrow();
            RoomDailyInventory daily2 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room2.getId(), d).orElseThrow();
            assertEquals(0, daily1.getHeldRooms());
            assertEquals(2, daily1.getBookedRooms());
            assertEquals(0, daily2.getHeldRooms());
            assertEquals(1, daily2.getBookedRooms());
        }

        // 3. Repeated confirmation is idempotent
        ResponseEntity<Map> repeatResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);
        assertEquals(HttpStatus.OK, repeatResp.getStatusCode());
        assertTrue((Boolean) repeatResp.getBody().get("idempotent"));
    }

    @Test
    @DisplayName("WP-HB-04 5.2: Release restores inventory for both HELD and CONFIRMED multi-allocations")
    public void testRelease_RestoresInventory() {
        HotelProperty hotel = createProperty(51L, "ReleaseHotel", "Mysore", "ACTIVE");
        RoomType room1 = createRoom(hotel.getId(), "Heritage Room 1", 2000.0, 5, "ACTIVE");
        RoomType room2 = createRoom(hotel.getId(), "Heritage Room 2", 3000.0, 5, "ACTIVE");

        String bookingRef = "TE-REL-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate checkIn = LocalDate.now().plusDays(60);
        LocalDate checkOut = LocalDate.now().plusDays(62); // 2 nights

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(
                        new ReservationAllocationRequestDto(room1.getId(), 2),
                        new ReservationAllocationRequestDto(room2.getId(), 1)
                ))
                .build();

        // 1. Reserve
        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // 2. Release while HELD
        ResponseEntity<Map> releaseResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef,
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.OK, releaseResp.getStatusCode());
        assertEquals("RELEASED", releaseResp.getBody().get("status"));

        // Verify DB
        HotelReservation parent = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        assertEquals("RELEASED", parent.getStatus());
        List<HotelBookingAllocation> allocs = allocationRepository.findByReservationBookingRef(bookingRef);
        assertTrue(allocs.stream().allMatch(a -> "RELEASED".equals(a.getStatus())));

        // Inventory must be fully restored: held = 0, booked = 0
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomDailyInventory daily1 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room1.getId(), d).orElseThrow();
            RoomDailyInventory daily2 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room2.getId(), d).orElseThrow();
            assertEquals(0, daily1.getHeldRooms());
            assertEquals(5, daily1.getAvailableRooms());
            assertEquals(0, daily2.getHeldRooms());
            assertEquals(5, daily2.getAvailableRooms());
        }

        // 3. Repeated release is idempotent
        ResponseEntity<Map> repeatRelease = restTemplate.exchange(
                getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef,
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Map.class);
        assertEquals(HttpStatus.OK, repeatRelease.getStatusCode());
        assertTrue((Boolean) repeatRelease.getBody().get("idempotent"));
    }

    // =========================================================================
    // 6. BACKWARD COMPATIBILITY & SECURITY TESTS
    // =========================================================================

    @Test
    @DisplayName("WP-HB-04 6.1: Existing single-room reservation API works and links parent reservation")
    public void testBackwardCompatibility_SingleRoomEndpoint() {
        HotelProperty hotel = createProperty(60L, "LegacyHotel", "Shimla", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Valley View", 4500.0, 5, "ACTIVE");

        String bookingRef = "TE-LEGACY-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate checkIn = LocalDate.now().plusDays(65);
        LocalDate checkOut = LocalDate.now().plusDays(67);

        DateWiseReserveRequestDto dto = DateWiseReserveRequestDto.builder()
                .bookingRef(bookingRef)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .count(2)
                .build();

        ResponseEntity<Map> resp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/reserve-stay",
                HttpMethod.POST,
                new HttpEntity<>(dto, internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map body = resp.getBody();
        assertNotNull(body);
        assertEquals(bookingRef, body.get("bookingRef"));
        assertEquals("HELD", body.get("status"));

        // Verify parent reservation was created
        HotelReservation parent = reservationRepository.findByBookingRef(bookingRef).orElse(null);
        assertNotNull(parent);
        assertEquals("HELD", parent.getStatus());

        // Confirming via /rooms/confirm-stay works seamlessly
        ResponseEntity<Map> confirmResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);
        assertEquals(HttpStatus.OK, confirmResp.getStatusCode());
        assertEquals("CONFIRMED", confirmResp.getBody().get("status"));
    }

    @Test
    @DisplayName("WP-HB-04 6.2: External caller without internal credentials is forbidden on /reservations")
    public void testSecurity_ExternalCallerForbiddenOnConsolidatedEndpoint() {
        HotelProperty hotel = createProperty(61L, "SecurityHotel", "Manali", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Snow Suite", 5000.0, 5, "ACTIVE");

        String managerToken = createManagerToken(hotel.getManagerId());
        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef("TE-SEC-101")
                .hotelId(hotel.getId())
                .checkInDate(LocalDate.now().plusDays(70))
                .checkOutDate(LocalDate.now().plusDays(72))
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        // Manager token is NOT an internal service token -> 403 Forbidden
        ResponseEntity<Map> resp = restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, userHeaders(managerToken)),
                Map.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    @DisplayName("WP-HB-04 6.3: Unauthenticated request on /rooms/confirm-stay is rejected with 401")
    public void testSecurity_UnauthenticatedConfirmStay_RejectedWith401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> resp = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=TE-DUMMY",
                HttpMethod.PUT,
                new HttpEntity<>(headers),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    @DisplayName("WP-HB-04 6.4: Child bookingRef is non-unique; multiple allocations reference same parent")
    public void testDataModel_ChildNonUniqueAndParentReferenced() {
        HotelProperty hotel = createProperty(62L, "DataModelHotel", "Rishikesh", "ACTIVE");
        RoomType room1 = createRoom(hotel.getId(), "Ganga View", 2500.0, 5, "ACTIVE");
        RoomType room2 = createRoom(hotel.getId(), "Mountain View", 3500.0, 5, "ACTIVE");

        String bookingRef = "TE-DM-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate checkIn = LocalDate.now().plusDays(75);
        LocalDate checkOut = LocalDate.now().plusDays(77);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(
                        new ReservationAllocationRequestDto(room1.getId(), 1),
                        new ReservationAllocationRequestDto(room2.getId(), 1)
                ))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // Fetch parent and children
        HotelReservation parent = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        List<HotelBookingAllocation> children = allocationRepository.findByReservationBookingRefOrderByIdAsc(bookingRef);

        assertEquals(2, children.size());
        assertEquals(children.get(0).getBookingRef(), children.get(1).getBookingRef(), "Both children share same bookingRef");
        assertNotNull(children.get(0).getReservation());
        assertEquals(parent.getId(), children.get(0).getReservation().getId());
        assertEquals(parent.getId(), children.get(1).getReservation().getId());
    }

    // =========================================================================
    // 7. TARGETED CORRECTION & VERIFICATION TESTS (WP-HB-04 ROUND 2)
    // =========================================================================

    @Test
    @DisplayName("WP-HB-04 7.1: Repeated confirmation is idempotent and does not double-increment booked rooms")
    public void testConfirmation_RepeatedConfirmation_IsIdempotent() {
        HotelProperty hotel = createProperty(71L, "ConfirmIdempHotel", "Goa", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Beach Villa", 5000.0, 5, "ACTIVE");

        String bookingRef = "TE-CONF-IDEMP-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(80);
        LocalDate checkOut = LocalDate.now().plusDays(82);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 2)))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // First confirmation
        ResponseEntity<Map> confirm1 = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);
        assertEquals(HttpStatus.OK, confirm1.getStatusCode());
        assertEquals("CONFIRMED", confirm1.getBody().get("status"));

        // Second confirmation (idempotent replay)
        ResponseEntity<Map> confirm2 = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);
        assertEquals(HttpStatus.OK, confirm2.getStatusCode());
        assertEquals("CONFIRMED", confirm2.getBody().get("status"));
        assertEquals(Boolean.TRUE, confirm2.getBody().get("idempotent"));

        // Verify daily inventory bookedRooms is exactly 2, not 4
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), d).orElseThrow();
            assertEquals(2, daily.getBookedRooms(), "Booked rooms must not be double incremented");
            assertEquals(0, daily.getHeldRooms());
        }
    }

    @Test
    @DisplayName("WP-HB-04 7.2: Confirmation after RELEASED is rejected with 400")
    public void testConfirmation_AfterReleased_Rejected() {
        HotelProperty hotel = createProperty(72L, "ConfirmAfterRelHotel", "Kochi", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Backwater Room", 4000.0, 5, "ACTIVE");

        String bookingRef = "TE-CONF-REL-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(85);
        LocalDate checkOut = LocalDate.now().plusDays(87);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // Release stay
        restTemplate.exchange(
                getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef,
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        // Confirmation attempt after release must fail
        ResponseEntity<Map> confirmResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, confirmResp.getStatusCode());
    }

    @Test
    @DisplayName("WP-HB-04 7.3: Confirmation after CANCELLED is rejected with 400")
    public void testConfirmation_AfterCancelled_Rejected() {
        HotelProperty hotel = createProperty(73L, "ConfirmAfterCancHotel", "Munnar", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Tea Garden View", 4500.0, 5, "ACTIVE");

        String bookingRef = "TE-CONF-CANC-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(90);
        LocalDate checkOut = LocalDate.now().plusDays(92);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // Set status to CANCELLED in DB
        HotelReservation parent = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        parent.setStatus("CANCELLED");
        reservationRepository.save(parent);

        // Confirmation attempt after cancellation must fail
        ResponseEntity<Map> confirmResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, confirmResp.getStatusCode());
    }

    @Test
    @DisplayName("WP-HB-04 7.4: Confirmation with inconsistent child state is rejected")
    public void testConfirmation_InconsistentParentChildState_Rejected() {
        HotelProperty hotel = createProperty(74L, "InconsistentChildHotel", "Ooty", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Pine Suite", 3500.0, 5, "ACTIVE");

        String bookingRef = "TE-CONF-INCON-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(95);
        LocalDate checkOut = LocalDate.now().plusDays(97);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // Break child state to RELEASED while parent is still HELD
        List<HotelBookingAllocation> children = allocationRepository.findByReservationBookingRefOrderByIdAsc(bookingRef);
        children.get(0).setStatus("RELEASED");
        allocationRepository.save(children.get(0));

        // Confirmation attempt must fail due to state inconsistency
        ResponseEntity<Map> confirmResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, confirmResp.getStatusCode());
    }

    @Test
    @DisplayName("WP-HB-04 7.5: Confirmation fails and rolls back when held counter is insufficient")
    public void testConfirmation_InsufficientHeldCounter_CausesFailure() {
        HotelProperty hotel = createProperty(75L, "InsufficientHeldConfirmHotel", "Shimla", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Snow View", 6000.0, 5, "ACTIVE");

        String bookingRef = "TE-CONF-INSUFF-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(100);
        LocalDate checkOut = LocalDate.now().plusDays(102);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 2)))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // Artificially corrupt held inventory counter to 1 in DB
        RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), checkIn).orElseThrow();
        daily.setHeldRooms(1);
        dailyInventoryRepository.save(daily);

        // Confirmation must fail
        ResponseEntity<Map> confirmResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, confirmResp.getStatusCode());

        // Verify daily bookedRooms is still 0 (whole transaction rolled back)
        RoomDailyInventory dailyAfter = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), checkIn).orElseThrow();
        assertEquals(0, dailyAfter.getBookedRooms());
    }

    @Test
    @DisplayName("WP-HB-04 7.6: Strict release fails and rolls back when held counter is insufficient")
    public void testRelease_InsufficientHeldCounter_CausesFailure() {
        HotelProperty hotel = createProperty(76L, "StrictReleaseHeldHotel", "Jaipur", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Haveli Room", 5500.0, 5, "ACTIVE");

        String bookingRef = "TE-REL-INSUFF-HELD-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(105);
        LocalDate checkOut = LocalDate.now().plusDays(107);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 2)))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // Corrupt held counter to 1
        RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), checkIn).orElseThrow();
        daily.setHeldRooms(1);
        dailyInventoryRepository.save(daily);

        // Release must fail
        ResponseEntity<Map> relResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef,
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, relResp.getStatusCode());

        // Verify parent is still HELD (not released)
        HotelReservation parent = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        assertEquals("HELD", parent.getStatus());
    }

    @Test
    @DisplayName("WP-HB-04 7.7: Strict release fails and rolls back when booked counter is insufficient")
    public void testRelease_InsufficientBookedCounter_CausesFailure() {
        HotelProperty hotel = createProperty(77L, "StrictReleaseBookedHotel", "Varanasi", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Ghat View", 4200.0, 5, "ACTIVE");

        String bookingRef = "TE-REL-INSUFF-BKD-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(110);
        LocalDate checkOut = LocalDate.now().plusDays(112);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 2)))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // Confirm reservation
        restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        // Corrupt booked counter to 1
        RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), checkIn).orElseThrow();
        daily.setBookedRooms(1);
        dailyInventoryRepository.save(daily);

        // Release must fail
        ResponseEntity<Map> relResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef,
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, relResp.getStatusCode());

        // Verify parent is still CONFIRMED
        HotelReservation parent = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        assertEquals("CONFIRMED", parent.getStatus());
    }

    @Test
    @DisplayName("WP-HB-04 7.8: Multi-allocation release with one failing allocation rolls back ALL allocations")
    public void testRelease_MultiAllocation_OneInconsistentRollsBackAll() {
        HotelProperty hotel = createProperty(78L, "MultiAllocRollbackHotel", "Agra", "ACTIVE");
        RoomType room1 = createRoom(hotel.getId(), "Taj View", 6000.0, 5, "ACTIVE");
        RoomType room2 = createRoom(hotel.getId(), "City View", 3000.0, 5, "ACTIVE");

        String bookingRef = "TE-REL-ROLLBACK-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(115);
        LocalDate checkOut = LocalDate.now().plusDays(117);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(
                        new ReservationAllocationRequestDto(room1.getId(), 2),
                        new ReservationAllocationRequestDto(room2.getId(), 2)
                ))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // Corrupt Room 2 held counter to 0 (insufficient)
        RoomDailyInventory daily2 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room2.getId(), checkIn).orElseThrow();
        daily2.setHeldRooms(0);
        dailyInventoryRepository.save(daily2);

        // Release must fail
        ResponseEntity<Map> relResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef,
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, relResp.getStatusCode());

        // Verify Room 1 heldRooms was NOT decremented (entire aggregate release rolled back)
        RoomDailyInventory daily1 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room1.getId(), checkIn).orElseThrow();
        assertEquals(2, daily1.getHeldRooms(), "First allocation must not be partially released");

        HotelReservation parent = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        assertEquals("HELD", parent.getStatus());
    }

    @Test
    @DisplayName("WP-HB-04 7.9: Repeated release is idempotent and does not decrement counters twice")
    public void testRelease_RepeatedRelease_DoesNotDecrementTwice() {
        HotelProperty hotel = createProperty(79L, "RepeatedRelHotel", "Pune", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Hilltop Room", 4000.0, 5, "ACTIVE");

        String bookingRef = "TE-REL-REPEAT-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(120);
        LocalDate checkOut = LocalDate.now().plusDays(122);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 2)))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // First release
        ResponseEntity<Map> rel1 = restTemplate.exchange(
                getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef,
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Map.class);
        assertEquals(HttpStatus.OK, rel1.getStatusCode());

        // Check held rooms is 0
        RoomDailyInventory dailyAfter1 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), checkIn).orElseThrow();
        assertEquals(0, dailyAfter1.getHeldRooms());

        // Second release
        ResponseEntity<Map> rel2 = restTemplate.exchange(
                getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef,
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Map.class);
        assertEquals(HttpStatus.OK, rel2.getStatusCode());
        assertEquals(Boolean.TRUE, rel2.getBody().get("idempotent"));

        // Check held rooms is still 0 (never negative)
        RoomDailyInventory dailyAfter2 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), checkIn).orElseThrow();
        assertEquals(0, dailyAfter2.getHeldRooms(), "Inventory counters must never become negative");
    }

    @Test
    @DisplayName("WP-HB-04 7.10: Release on CANCELLED reservation is rejected")
    public void testRelease_CancelledReservation_Rejected() {
        HotelProperty hotel = createProperty(80L, "RelCancelledHotel", "Nagpur", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Orange City Room", 3000.0, 5, "ACTIVE");

        String bookingRef = "TE-REL-CANC-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(125);
        LocalDate checkOut = LocalDate.now().plusDays(127);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // Set status to CANCELLED in DB
        HotelReservation parent = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        parent.setStatus("CANCELLED");
        reservationRepository.save(parent);

        // Release must fail
        ResponseEntity<Map> relResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef,
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, relResp.getStatusCode());
    }

    @Test
    @DisplayName("WP-HB-04 7.11: Cancellation state is persisted and serialized cleanly")
    public void testCancellation_StatusPersistedAndSerialized() {
        HotelProperty hotel = createProperty(81L, "CancelPersistHotel", "Mysore", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Palace Suite", 6500.0, 5, "ACTIVE");

        String bookingRef = "TE-CANC-STATE-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(130);
        LocalDate checkOut = LocalDate.now().plusDays(132);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // Set status to CANCELLED
        HotelReservation parent = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        parent.setStatus("CANCELLED");
        reservationRepository.save(parent);

        // Fetch via GET /api/hotels/reservations/{bookingRef}
        ResponseEntity<HotelReservationResponseDto> getResp = restTemplate.exchange(
                getBaseUrl() + "/reservations/" + bookingRef,
                HttpMethod.GET,
                new HttpEntity<>(internalHeaders()),
                HotelReservationResponseDto.class);

        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertNotNull(getResp.getBody());
        assertEquals("CANCELLED", getResp.getBody().getStatus(), "Reservation serialization must represent CANCELLED");
    }

    @Test
    @DisplayName("WP-HB-04 7.12: Reservation in CANCELLED state cannot be re-reserved with same bookingRef")
    public void testCancellation_CannotBeReReserved() {
        HotelProperty hotel = createProperty(82L, "ReReserveCancelledHotel", "Coorg", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Coffee Estate Cottage", 5000.0, 5, "ACTIVE");

        String bookingRef = "TE-RE-RESERVE-CANC-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(135);
        LocalDate checkOut = LocalDate.now().plusDays(137);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        restTemplate.exchange(getBaseUrl() + "/reservations", HttpMethod.POST, new HttpEntity<>(req, internalHeaders()), HotelReservationResponseDto.class);

        // Set status to CANCELLED
        HotelReservation parent = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        parent.setStatus("CANCELLED");
        reservationRepository.save(parent);

        // Submitting same reservation again must be rejected
        ResponseEntity<Map> resp = restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    @DisplayName("WP-HB-04 7.13: Historical pricing snapshot is unaffected by subsequent base price or override changes")
    public void testPricingSnapshot_UnaffectedBySubsequentBasePriceOrOverrideChanges() {
        HotelProperty hotel = createProperty(83L, "PriceSnapshotHotel", "Chandigarh", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Rose Suite", 3000.0, 5, "ACTIVE");

        String bookingRef = "TE-SNAP-PRICE-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(140);
        LocalDate checkOut = LocalDate.now().plusDays(142); // 2 nights

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 2))) // 2 rooms * 2 nights * 3000 = 12000.0
                .build();

        ResponseEntity<HotelReservationResponseDto> initialResp = restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, internalHeaders()),
                HotelReservationResponseDto.class);

        assertEquals(HttpStatus.OK, initialResp.getStatusCode());
        assertEquals(12000.0, initialResp.getBody().getTotalPrice());

        // Subsequenty alter base price on RoomType to 9999.0
        room.setPricePerNight(9999.0);
        roomTypeRepository.save(room);

        // Set an override on checkIn date to 8888.0
        PriceOverrideRequestDto overrideDto = PriceOverrideRequestDto.builder()
                .overrideDate(checkIn)
                .pricePerNight(8888.0)
                .build();
        pricingService.setPriceOverride(room.getId(), overrideDto, new HotelSecurityService.CallerPrincipal(1L, "ROLE_ADMIN", "admin@traveleasy.com", "Admin", true, false, false));

        // Retrieve the reservation via GET /reservations/{bookingRef}
        ResponseEntity<HotelReservationResponseDto> getResp = restTemplate.exchange(
                getBaseUrl() + "/reservations/" + bookingRef,
                HttpMethod.GET,
                new HttpEntity<>(internalHeaders()),
                HotelReservationResponseDto.class);

        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        HotelReservationResponseDto body = getResp.getBody();
        assertNotNull(body);

        // Total price and nightly breakdown must remain 100% frozen
        assertEquals(12000.0, body.getTotalPrice(), "Historical total price must remain frozen");
        assertEquals(2, body.getAllocations().get(0).getNightlyPriceBreakdown().size());
        assertEquals(3000.0, body.getAllocations().get(0).getNightlyPriceBreakdown().get(0).getRate());
        assertEquals(3000.0, body.getAllocations().get(0).getNightlyPriceBreakdown().get(1).getRate());
    }

    @Test
    @DisplayName("WP-HB-04 7.14: Mandatory parent relationship rejects orphan allocation without reservation")
    public void testMandatoryParent_OrphanAllocationRejected() {
        HotelProperty hotel = createProperty(84L, "MandatoryParentHotel", "Surat", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Diamond Suite", 4500.0, 5, "ACTIVE");

        HotelBookingAllocation orphan = HotelBookingAllocation.builder()
                .reservation(null) // Violation: non-null parent is mandatory
                .hotelId(hotel.getId())
                .roomId(room.getId())
                .checkInDate(LocalDate.now().plusDays(150))
                .checkOutDate(LocalDate.now().plusDays(152))
                .roomCount(1)
                .status("HELD")
                .totalPrice(9000.0)
                .build();

        assertThrows(Exception.class, () -> allocationRepository.saveAndFlush(orphan),
                "Database constraint must reject orphan allocation without parent HotelReservation");
    }

    @Test
    @DisplayName("WP-HB-04 7.15: Concurrent requests with same bookingRef but different payloads produce one winner and one conflict")
    public void testConcurrency_SameBookingRefDifferentPayload_OneWinnerOneConflict() throws Exception {
        HotelProperty hotel = createProperty(85L, "PayloadRaceHotel", "Kolkata", "ACTIVE");
        RoomType room1 = createRoom(hotel.getId(), "Howrah Suite", 3000.0, 5, "ACTIVE");
        RoomType room2 = createRoom(hotel.getId(), "Park Street Deluxe", 4000.0, 5, "ACTIVE");

        String bRef = "TE-PAYLOAD-RACE-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(160);
        LocalDate checkOut = LocalDate.now().plusDays(162);

        ConsolidatedReservationRequestDto req1 = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room1.getId(), 1)))
                .build();

        ConsolidatedReservationRequestDto req2 = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .allocations(List.of(new ReservationAllocationRequestDto(room2.getId(), 2)))
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                startLatch.await();
                ResponseEntity<String> resp = restTemplate.exchange(
                        getBaseUrl() + "/reservations",
                        HttpMethod.POST,
                        new HttpEntity<>(req1, internalHeaders()),
                        String.class);
                statusCodes.add(resp.getStatusCode().value());
            } catch (Exception e) {
                statusCodes.add(500);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                ResponseEntity<String> resp = restTemplate.exchange(
                        getBaseUrl() + "/reservations",
                        HttpMethod.POST,
                        new HttpEntity<>(req2, internalHeaders()),
                        String.class);
                statusCodes.add(resp.getStatusCode().value());
            } catch (Exception e) {
                statusCodes.add(500);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        long successCount = statusCodes.stream().filter(s -> s == 200).count();
        long conflictCount = statusCodes.stream().filter(s -> s == 400 || s == 409).count();

        assertEquals(1, successCount, "Exactly one request should win the race with 200 OK");
        assertEquals(1, conflictCount, "Losing request with differing payload must be rejected with 400/409 Conflict");

        // Verify the winning reservation was committed
        HotelReservation winner = reservationRepository.findByBookingRefWithAllocations(bRef).orElseThrow();
        assertNotNull(winner);

        // Verify that the losing room did NOT hold any inventory
        if (winner.getAllocations().get(0).getRoomId().equals(room1.getId())) {
            // Room 1 won: Room 2 must have 0 held rooms
            RoomDailyInventory daily2 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room2.getId(), checkIn).orElse(null);
            assertTrue(daily2 == null || daily2.getHeldRooms() == 0, "Losing request must leave no inventory holds");
        } else {
            // Room 2 won: Room 1 must have 0 held rooms
            RoomDailyInventory daily1 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room1.getId(), checkIn).orElse(null);
            assertTrue(daily1 == null || daily1.getHeldRooms() == 0, "Losing request must leave no inventory holds");
        }
    }

    @Test
    @DisplayName("WP-HB-04 7.16: Schema initializer runs safely and idempotently multiple times without deleting active records")
    public void testSchemaInitializer_MultipleRuns_SafeIdempotentAndNonDestructive() {
        HotelProperty hotel = createProperty(86L, "InitIdempHotel", "Ahmedabad", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Heritage Suite", 4000.0, 5, "ACTIVE");

        String bookingRef = "TE-INIT-IDEMP-" + UUID.randomUUID().toString().substring(0, 6);
        LocalDate checkIn = LocalDate.now().plusDays(170);
        LocalDate checkOut = LocalDate.now().plusDays(172);

        HotelReservation parent = HotelReservation.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .status("CONFIRMED")
                .totalPrice(8000.0)
                .build();
        parent = reservationRepository.save(parent);

        HotelBookingAllocation alloc = HotelBookingAllocation.builder()
                .reservation(parent)
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .roomId(room.getId())
                .roomCount(1)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .status("CONFIRMED")
                .totalPrice(8000.0)
                .build();
        allocationRepository.save(alloc);

        long countBefore = allocationRepository.count();
        long propertyCountBefore = hotelRepository.count();
        long roomCountBefore = roomTypeRepository.count();

        // Run schema initializer multiple times
        assertDoesNotThrow(() -> schemaInitializer.run(null));
        assertDoesNotThrow(() -> schemaInitializer.run(null));
        assertDoesNotThrow(() -> schemaInitializer.run(null));

        // Verify zero destructive side effects
        assertEquals(countBefore, allocationRepository.count(), "Allocations must not be deleted on subsequent runs");
        assertEquals(propertyCountBefore, hotelRepository.count(), "Hotel properties must not be modified");
        assertEquals(roomCountBefore, roomTypeRepository.count(), "Room types must not be modified");

        // Verify child record still exists and references parent
        HotelBookingAllocation reloaded = allocationRepository.findById(alloc.getId()).orElseThrow();
        assertEquals(parent.getId(), reloaded.getReservation().getId());
    }


    // =========================================================================

    // =========================================================================
    // WP-HB-05 Targeted Corrections: Expiry Synchronization Tests
    // =========================================================================

    @Test
    @DisplayName("WP-HB-05: Authoritative expiresAt passed by Booking Service is persisted and returned in response")
    public void testSuppliedExpiresAt_PersistedAndReturnedInResponse() {
        HotelProperty hotel = createProperty(10L, "ExpiryPersistHotel", "Hyderabad", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Deluxe Room", 2500.0, 5, "ACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = LocalDate.now().plusDays(12);
        LocalDateTime explicitExpiresAt = LocalDateTime.now().plusMinutes(14).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .expiresAt(explicitExpiresAt)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 2)))
                .build();

        ResponseEntity<HotelReservationResponseDto> response = restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, internalHeaders()),
                HotelReservationResponseDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getExpiresAt(), "Response must contain expiresAt deadline");
        assertEquals(explicitExpiresAt, response.getBody().getExpiresAt(), "Response expiresAt must match supplied Booking Service deadline");

        HotelReservation persisted = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        assertEquals(explicitExpiresAt, persisted.getExpiresAt(), "Persisted HotelReservation must match supplied deadline without recalculation");
    }

    @Test
    @DisplayName("WP-HB-05: Confirmation attempt at or after expiresAt deadline is rejected and auto-releases held rooms")
    public void testConfirmation_AfterExpiresAtDeadline_RejectedAndAutoReleasesHeldRooms() {
        HotelProperty hotel = createProperty(10L, "ExpiryRejectHotel", "Hyderabad", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Deluxe Room", 2500.0, 5, "ACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate checkIn = LocalDate.now().plusDays(20);
        LocalDate checkOut = LocalDate.now().plusDays(22);
        LocalDateTime pastExpiresAt = LocalDateTime.now().minusSeconds(5);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .expiresAt(pastExpiresAt)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 2)))
                .build();

        ResponseEntity<HotelReservationResponseDto> resResp = restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, internalHeaders()),
                HotelReservationResponseDto.class);
        assertEquals(HttpStatus.OK, resResp.getStatusCode());
        assertEquals("HELD", resResp.getBody().getStatus());

        // Verify daily inventory held rooms is 2
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), d).orElseThrow();
            assertEquals(2, daily.getHeldRooms(), "Inventory should have 2 held rooms before confirmation attempt");
        }

        // Attempt confirmation after deadline -> Expect 400 Bad Request
        ResponseEntity<Map> confirmResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, confirmResp.getStatusCode(), "Expired confirmation must return 400 Bad Request");
        assertTrue(confirmResp.getBody().get("message").toString().contains("deadline has expired"),
                "Error message must indicate deadline has expired");

        // Verify parent reservation is authoritatively RELEASED
        HotelReservation parentAfter = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        assertEquals("RELEASED", parentAfter.getStatus(), "Parent reservation must be transitioned to RELEASED upon expired confirmation attempt");

        // Verify child allocations are RELEASED
        List<HotelBookingAllocation> allocsAfter = allocationRepository.findByReservationBookingRefOrderByIdAsc(bookingRef);
        for (HotelBookingAllocation a : allocsAfter) {
            assertEquals("RELEASED", a.getStatus(), "Child allocations must be transitioned to RELEASED");
        }

        // Verify inventory was freed (heldRooms back to 0, bookedRooms is 0)
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), d).orElseThrow();
            assertEquals(0, daily.getHeldRooms(), "Held rooms must be returned to 0 upon expiry auto-release");
            assertEquals(0, daily.getBookedRooms(), "Booked rooms must remain 0");
        }
    }

    @Test
    @DisplayName("WP-HB-05: Subsequent confirmation attempt cannot revive an expired and released reservation")
    public void testConfirmation_AfterExpiryRelease_CannotBeRevived() {
        HotelProperty hotel = createProperty(10L, "ReviveRejectHotel", "Hyderabad", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Deluxe Room", 2500.0, 5, "ACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate checkIn = LocalDate.now().plusDays(30);
        LocalDate checkOut = LocalDate.now().plusDays(32);
        LocalDateTime pastExpiresAt = LocalDateTime.now().minusSeconds(10);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .expiresAt(pastExpiresAt)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, internalHeaders()),
                HotelReservationResponseDto.class);

        // First attempt rejects and auto-releases
        ResponseEntity<Map> confirm1 = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, confirm1.getStatusCode());

        // Subsequent attempt must also reject and cannot revive
        ResponseEntity<Map> confirm2 = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, confirm2.getStatusCode());
        assertTrue(confirm2.getBody().get("message").toString().contains("released") ||
                   confirm2.getBody().get("message").toString().contains("expired"));

        HotelReservation parent = reservationRepository.findByBookingRef(bookingRef).orElseThrow();
        assertEquals("RELEASED", parent.getStatus(), "Reservation must remain RELEASED and cannot be revived");
    }

    @Test
    @DisplayName("WP-HB-05: Release on an expired/released reservation is idempotent")
    public void testRelease_ExpiredReservation_IsIdempotent() {
        HotelProperty hotel = createProperty(10L, "IdempotentReleaseHotel", "Hyderabad", "ACTIVE");
        RoomType room = createRoom(hotel.getId(), "Deluxe Room", 2500.0, 5, "ACTIVE");

        String bookingRef = "TE-HB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate checkIn = LocalDate.now().plusDays(40);
        LocalDate checkOut = LocalDate.now().plusDays(42);
        LocalDateTime pastExpiresAt = LocalDateTime.now().minusSeconds(10);

        ConsolidatedReservationRequestDto req = ConsolidatedReservationRequestDto.builder()
                .bookingRef(bookingRef)
                .hotelId(hotel.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .expiresAt(pastExpiresAt)
                .allocations(List.of(new ReservationAllocationRequestDto(room.getId(), 1)))
                .build();

        restTemplate.exchange(
                getBaseUrl() + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>(req, internalHeaders()),
                HotelReservationResponseDto.class);

        // Confirmation attempt auto-releases
        restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        // Booking Service expiry reconciliation calls release
        ResponseEntity<Map> releaseResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef,
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Map.class);

        assertEquals(HttpStatus.OK, releaseResp.getStatusCode());
        assertEquals("RELEASED", releaseResp.getBody().get("status"));
        assertEquals(Boolean.TRUE, releaseResp.getBody().get("idempotent"));
    }

}
