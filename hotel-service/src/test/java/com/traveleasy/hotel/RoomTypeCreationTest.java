package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.RoomTypeRequestDto;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomDailyInventory;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.RoomDailyInventoryRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RoomTypeCreationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HotelPropertyRepository hotelRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomDailyInventoryRepository inventoryRepository;

    @Value("${jwt.secret:TravelEasySuperSecretKeyForJWTSigning12345678901234567890}")
    private String jwtSecret;

    private String managerToken;
    private String manager2Token;
    private String travellerToken;
    private HotelProperty testHotel;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/hotels";
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return headers;
    }

    private String createTestToken(Long userId, String role, String email, String fullName, long ttlMillis) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("role", role)
                .claim("fullName", fullName)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @BeforeEach
    public void setup() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        managerToken = createTestToken(1001L, "ROLE_HOTEL_MANAGER", "manager1@traveleasy.com", "Manager One", 3600000);
        manager2Token = createTestToken(1002L, "ROLE_HOTEL_MANAGER", "manager2@traveleasy.com", "Manager Two", 3600000);
        travellerToken = createTestToken(2001L, "ROLE_TRAVELLER", "traveller@traveleasy.com", "Traveller", 3600000);

        testHotel = hotelRepository.save(HotelProperty.builder()
                .name("Grand Residency Hotel")
                .propertyType("HOTEL")
                .propertyClassification("5 Star")
                .propertySegment("LUXURY")
                .address("100 MG Road")
                .city("Bengaluru")
                .state("Karnataka")
                .country("India")
                .postalCode("560001")
                .managerId(1001L)
                .managerName("Manager One")
                .status("ACTIVE")
                .build());
    }

    @Test
    @DisplayName("WP-06 1.1: Create room type successfully without price (separated pricing rule)")
    public void testCreateRoomType_Success_WithoutPrice() {
        RoomTypeRequestDto request = RoomTypeRequestDto.builder()
                .roomTypeName("Deluxe Double Room")
                .maxOccupancy(2)
                .totalRooms(10)
                .amenities("Wi-Fi, AC, TV, Balcony")
                .imageUrl("/api/hotels/images/deluxe-double.jpg")
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(request, authHeaders(managerToken));
        ResponseEntity<RoomType> response = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                entity,
                RoomType.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        RoomType created = response.getBody();
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("Deluxe Double Room", created.getRoomTypeName());
        assertEquals(2, created.getMaxOccupancy());
        assertEquals(10, created.getTotalRooms());
        assertEquals(10, created.getAvailableRooms());
        assertEquals("Wi-Fi, AC, TV, Balcony", created.getAmenities());
        assertEquals("/api/hotels/images/deluxe-double.jpg", created.getImageUrl());
        assertNull(created.getPricePerNight(), "Pricing must be null when created from Add Room Type");
        assertEquals("ACTIVE", created.getStatus());
    }

    @Test
    @DisplayName("WP-06 1.2: Default max occupancy to 2 when null")
    public void testCreateRoomType_DefaultMaxOccupancy_WhenNull() {
        RoomTypeRequestDto request = RoomTypeRequestDto.builder()
                .roomTypeName("Superior Single Room")
                .totalRooms(5)
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(request, authHeaders(managerToken));
        ResponseEntity<RoomType> response = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                entity,
                RoomType.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getMaxOccupancy());
    }

    @Test
    @DisplayName("WP-06 1.3: Occupancy boundaries 1 to 20")
    public void testCreateRoomType_OccupancyBoundaries() {
        // Minimum valid occupancy = 1
        RoomTypeRequestDto minValid = RoomTypeRequestDto.builder()
                .roomTypeName("Single Pod")
                .maxOccupancy(1)
                .totalRooms(3)
                .build();
        ResponseEntity<RoomType> resMin = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(minValid, authHeaders(managerToken)),
                RoomType.class
        );
        assertEquals(HttpStatus.CREATED, resMin.getStatusCode());

        // Maximum valid occupancy = 20
        RoomTypeRequestDto maxValid = RoomTypeRequestDto.builder()
                .roomTypeName("Dormitory Room")
                .maxOccupancy(20)
                .totalRooms(2)
                .build();
        ResponseEntity<RoomType> resMax = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(maxValid, authHeaders(managerToken)),
                RoomType.class
        );
        assertEquals(HttpStatus.CREATED, resMax.getStatusCode());

        // Invalid occupancy < 1
        RoomTypeRequestDto invalidLow = RoomTypeRequestDto.builder()
                .roomTypeName("Zero Pod")
                .maxOccupancy(0)
                .totalRooms(2)
                .build();
        ResponseEntity<Map> resLow = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(invalidLow, authHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, resLow.getStatusCode());

        // Invalid occupancy > 20
        RoomTypeRequestDto invalidHigh = RoomTypeRequestDto.builder()
                .roomTypeName("Excess Pod")
                .maxOccupancy(21)
                .totalRooms(2)
                .build();
        ResponseEntity<Map> resHigh = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(invalidHigh, authHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, resHigh.getStatusCode());
    }

    @Test
    @DisplayName("WP-06 1.4: Total rooms validation (>= 1 required)")
    public void testCreateRoomType_TotalRoomsValidation() {
        RoomTypeRequestDto zeroRooms = RoomTypeRequestDto.builder()
                .roomTypeName("Empty Hall")
                .maxOccupancy(2)
                .totalRooms(0)
                .build();
        ResponseEntity<Map> resZero = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(zeroRooms, authHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, resZero.getStatusCode());

        RoomTypeRequestDto nullRooms = RoomTypeRequestDto.builder()
                .roomTypeName("Null Rooms")
                .maxOccupancy(2)
                .totalRooms(null)
                .build();
        ResponseEntity<Map> resNull = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(nullRooms, authHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, resNull.getStatusCode());
    }

    @Test
    @DisplayName("WP-06 1.5: Room type name validation (required, max 100 characters)")
    public void testCreateRoomType_NameValidation() {
        // Blank name
        RoomTypeRequestDto blankName = RoomTypeRequestDto.builder()
                .roomTypeName("   ")
                .maxOccupancy(2)
                .totalRooms(5)
                .build();
        ResponseEntity<Map> resBlank = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(blankName, authHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, resBlank.getStatusCode());

        // Over 100 characters
        RoomTypeRequestDto tooLong = RoomTypeRequestDto.builder()
                .roomTypeName("A".repeat(101))
                .maxOccupancy(2)
                .totalRooms(5)
                .build();
        ResponseEntity<Map> resLong = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(tooLong, authHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, resLong.getStatusCode());

        // Exactly 100 characters succeeds
        RoomTypeRequestDto exact100 = RoomTypeRequestDto.builder()
                .roomTypeName("A".repeat(100))
                .maxOccupancy(2)
                .totalRooms(5)
                .build();
        ResponseEntity<RoomType> resExact = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(exact100, authHeaders(managerToken)),
                RoomType.class
        );
        assertEquals(HttpStatus.CREATED, resExact.getStatusCode());
    }

    @Test
    @DisplayName("WP-06 1.6: Case-insensitive active duplicate name rejected in same property")
    public void testCreateRoomType_DuplicateActiveName_Rejected() {
        RoomTypeRequestDto first = RoomTypeRequestDto.builder()
                .roomTypeName("Executive Suite")
                .maxOccupancy(2)
                .totalRooms(5)
                .build();
        ResponseEntity<RoomType> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(first, authHeaders(managerToken)),
                RoomType.class
        );
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        // Attempt second with case difference
        RoomTypeRequestDto second = RoomTypeRequestDto.builder()
                .roomTypeName("executive suite")
                .maxOccupancy(3)
                .totalRooms(4)
                .build();
        ResponseEntity<Map> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(second, authHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res2.getStatusCode());
        assertTrue(res2.getBody().get("message").toString().contains("already exists"));
    }

    @Test
    @DisplayName("WP-06 1.7: Same room type name in a different property is permitted")
    public void testCreateRoomType_SameNameDifferentHotel_Allowed() {
        HotelProperty secondHotel = hotelRepository.save(HotelProperty.builder()
                .name("Second Hotel")
                .propertyType("HOTEL")
                .address("200 MG Road")
                .city("Bengaluru")
                .managerId(1001L)
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto req = RoomTypeRequestDto.builder()
                .roomTypeName("Presidential Suite")
                .maxOccupancy(4)
                .totalRooms(2)
                .build();

        // Hotel 1
        ResponseEntity<RoomType> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(req, authHeaders(managerToken)),
                RoomType.class
        );
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        // Hotel 2
        ResponseEntity<RoomType> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/" + secondHotel.getId() + "/rooms",
                new HttpEntity<>(req, authHeaders(managerToken)),
                RoomType.class
        );
        assertEquals(HttpStatus.CREATED, res2.getStatusCode());
    }

    @Test
    @DisplayName("WP-06 1.8: Security ownership enforcement - non-owner gets 403 Forbidden")
    public void testCreateRoomType_NonOwner_Forbidden() {
        RoomTypeRequestDto req = RoomTypeRequestDto.builder()
                .roomTypeName("Intruder Room")
                .maxOccupancy(2)
                .totalRooms(5)
                .build();

        // Manager 2 tries to create room on Manager 1's hotel
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(req, authHeaders(manager2Token)),
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("WP-06 1.9: Security unauthenticated gets 401 Unauthorized")
    public void testCreateRoomType_Unauthenticated_Returns401() {
        RoomTypeRequestDto req = RoomTypeRequestDto.builder()
                .roomTypeName("Anonymous Room")
                .maxOccupancy(2)
                .totalRooms(5)
                .build();

        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(req, authHeaders(null)),
                Map.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("WP-06 1.10: Daily inventory auto-initialized on creation")
    public void testCreateRoomType_DailyInventoryAutoInitialized() {
        RoomTypeRequestDto req = RoomTypeRequestDto.builder()
                .roomTypeName("Auto Inventory Room")
                .maxOccupancy(2)
                .totalRooms(8)
                .build();

        ResponseEntity<RoomType> response = restTemplate.postForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                new HttpEntity<>(req, authHeaders(managerToken)),
                RoomType.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        RoomType created = response.getBody();
        assertNotNull(created);

        // Verify daily inventory for today
        Optional<RoomDailyInventory> invOpt = inventoryRepository.findByRoomIdAndInventoryDate(
                created.getId(), LocalDate.now());
        assertTrue(invOpt.isPresent(), "Daily inventory record should be created for today");
        assertEquals(8, invOpt.get().getTotalRooms());
    }

    @Test
    @DisplayName("WP-06 2.1: Owner manager receives both ACTIVE and ARCHIVED room types")
    public void testManagerGetRoomTypes_IncludesActiveAndArchived() {
        RoomType activeRoom = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Active Deluxe Room")
                .maxOccupancy(2)
                .totalRooms(5)
                .availableRooms(5)
                .status("ACTIVE")
                .build());

        RoomType archivedRoom = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Archived Suite")
                .maxOccupancy(4)
                .totalRooms(2)
                .availableRooms(0)
                .status("ARCHIVED")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(managerToken));
        ResponseEntity<RoomType[]> response = restTemplate.exchange(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                HttpMethod.GET,
                entity,
                RoomType[].class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoomType[] rooms = response.getBody();
        assertNotNull(rooms);
        assertTrue(rooms.length >= 2);
        boolean hasActive = false;
        boolean hasArchived = false;
        for (RoomType r : rooms) {
            if ("Active Deluxe Room".equals(r.getRoomTypeName())) hasActive = true;
            if ("Archived Suite".equals(r.getRoomTypeName())) hasArchived = true;
        }
        assertTrue(hasActive, "Active room must be present in manager listing");
        assertTrue(hasArchived, "Archived room must be present in manager listing");
    }

    @Test
    @DisplayName("WP-06 2.2: Public discovery excludes ARCHIVED room types")
    public void testPublicGetRoomTypes_OnlyActive() {
        RoomType activeRoom = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Public Visible Room")
                .maxOccupancy(2)
                .totalRooms(4)
                .availableRooms(4)
                .status("ACTIVE")
                .build());

        RoomType archivedRoom = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Hidden Archived Room")
                .maxOccupancy(3)
                .totalRooms(3)
                .availableRooms(0)
                .status("ARCHIVED")
                .build());

        // Without Authorization header (public)
        ResponseEntity<RoomType[]> response = restTemplate.getForEntity(
                getBaseUrl() + "/" + testHotel.getId() + "/rooms",
                RoomType[].class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoomType[] rooms = response.getBody();
        assertNotNull(rooms);
        for (RoomType r : rooms) {
            assertNotEquals("Hidden Archived Room", r.getRoomTypeName(), "Archived rooms must be excluded from public view");
            assertEquals("ACTIVE", r.getStatus(), "Only ACTIVE rooms may be returned publicly");
        }
    }

    @Test
    @DisplayName("WP-06 2.3: Soft archive room type preserves historical record and sets status to ARCHIVED")
    public void testArchiveRoomType_Success_PreservesRecord() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Room To Archive")
                .maxOccupancy(2)
                .totalRooms(6)
                .availableRooms(6)
                .status("ACTIVE")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.DELETE,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ARCHIVED", response.getBody().get("status"));

        // Verify record is preserved in database with status ARCHIVED (no physical deletion)
        Optional<RoomType> dbRoom = roomTypeRepository.findById(room.getId());
        assertTrue(dbRoom.isPresent(), "Room type record must remain in database");
        assertEquals("ARCHIVED", dbRoom.get().getStatus());
        assertEquals(0, dbRoom.get().getAvailableRooms());
    }

    @Test
    @DisplayName("WP-06 2.4: Non-owner manager cannot archive room type (returns 403)")
    public void testArchiveRoomType_NonOwner_Forbidden() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Protected Room")
                .maxOccupancy(2)
                .totalRooms(5)
                .availableRooms(5)
                .status("ACTIVE")
                .build());

        // Manager 2 attempts to archive Manager 1's room
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(manager2Token));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.DELETE,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("ACTIVE", roomTypeRepository.findById(room.getId()).get().getStatus());
    }

    // =========================================================================
    // WP-06 EDIT ROOM TYPE TESTS
    // =========================================================================

    @Test
    @DisplayName("WP-06 Edit 1: Non-structural update (name, amenities, image) succeeds and preserves existing price")
    public void testUpdateRoomType_Success_NonStructural() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Standard Room")
                .maxOccupancy(2)
                .pricePerNight(3500.0)
                .totalRooms(10)
                .availableRooms(10)
                .amenities("Wi-Fi, AC")
                .imageUrl("http://example.com/original.jpg")
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto updateReq = RoomTypeRequestDto.builder()
                .roomTypeName("Superior Deluxe Room")
                .maxOccupancy(2)
                .totalRooms(10)
                .amenities("Wi-Fi, AC, TV, Balcony")
                .imageUrl("http://example.com/updated.jpg")
                // pricePerNight omitted / null: must preserve 3500.0
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(updateReq, authHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Superior Deluxe Room", response.getBody().get("roomTypeName"));
        assertEquals("Wi-Fi, AC, TV, Balcony", response.getBody().get("amenities"));
        assertEquals("http://example.com/updated.jpg", response.getBody().get("imageUrl"));

        // Verify DB persistence & price preservation
        RoomType dbRoom = roomTypeRepository.findById(room.getId()).orElseThrow();
        assertEquals("Superior Deluxe Room", dbRoom.getRoomTypeName());
        assertEquals("Wi-Fi, AC, TV, Balcony", dbRoom.getAmenities());
        assertEquals("http://example.com/updated.jpg", dbRoom.getImageUrl());
        assertEquals(3500.0, dbRoom.getPricePerNight());
    }

    @Test
    @DisplayName("WP-06 Edit 2: Duplicate active room type name rejection (excluding self)")
    public void testUpdateRoomType_DuplicateActiveName_Rejected() {
        RoomType room1 = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Executive Suite")
                .maxOccupancy(4)
                .pricePerNight(7000.0)
                .totalRooms(5)
                .availableRooms(5)
                .status("ACTIVE")
                .build());

        RoomType room2 = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Junior Suite")
                .maxOccupancy(3)
                .pricePerNight(5500.0)
                .totalRooms(4)
                .availableRooms(4)
                .status("ACTIVE")
                .build());

        // Attempt to rename room2 to "Executive Suite" (conflicts with room1)
        RoomTypeRequestDto conflictReq = RoomTypeRequestDto.builder()
                .roomTypeName("Executive Suite")
                .maxOccupancy(3)
                .totalRooms(4)
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(conflictReq, authHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room2.getId(),
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("already exists"));

        // Renaming room1 to its own name should succeed (self-conflict excluded)
        RoomTypeRequestDto selfReq = RoomTypeRequestDto.builder()
                .roomTypeName("Executive Suite")
                .maxOccupancy(4)
                .totalRooms(5)
                .build();

        ResponseEntity<Map> selfResponse = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room1.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(selfReq, authHeaders(managerToken)),
                Map.class
        );

        assertEquals(HttpStatus.OK, selfResponse.getStatusCode());
    }

    @Test
    @DisplayName("WP-06 Edit 3: Structural occupancy modification permitted when NO active bookings exist")
    public void testUpdateRoomType_OccupancyModification_AllowedWithoutActiveBookings() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Family Room")
                .maxOccupancy(2)
                .pricePerNight(4000.0)
                .totalRooms(4)
                .availableRooms(4) // 0 allocated
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto req = RoomTypeRequestDto.builder()
                .roomTypeName("Family Room")
                .maxOccupancy(5) // changed from 2 to 5
                .totalRooms(4)
                .build();

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(req, authHeaders(managerToken)),
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoomType dbRoom = roomTypeRepository.findById(room.getId()).orElseThrow();
        assertEquals(5, dbRoom.getMaxOccupancy());
    }

    @Test
    @DisplayName("WP-06 Edit 4: Structural occupancy decrease rejected when active reservations exist")
    public void testUpdateRoomType_OccupancyDecrease_RejectedWithActiveBookings() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Occupancy Restrict Room 1")
                .maxOccupancy(3)
                .pricePerNight(4500.0)
                .totalRooms(5)
                .availableRooms(3) // 2 allocated (active reservation)
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto req = RoomTypeRequestDto.builder()
                .roomTypeName("Occupancy Restrict Room 1")
                .maxOccupancy(2) // decrease attempt from 3 to 2
                .totalRooms(5)
                .build();

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(req, authHeaders(managerToken)),
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("active bookings")
                || response.getBody().get("message").toString().contains("occupancy"));
    }

    @Test
    @DisplayName("WP-06 Edit 5: Structural occupancy increase rejected when active reservations exist")
    public void testUpdateRoomType_OccupancyIncrease_RejectedWithActiveBookings() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Occupancy Restrict Room 2")
                .maxOccupancy(2)
                .pricePerNight(4500.0)
                .totalRooms(5)
                .availableRooms(4) // 1 allocated (active reservation)
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto req = RoomTypeRequestDto.builder()
                .roomTypeName("Occupancy Restrict Room 2")
                .maxOccupancy(4) // increase attempt from 2 to 4
                .totalRooms(5)
                .build();

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(req, authHeaders(managerToken)),
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("active bookings")
                || response.getBody().get("message").toString().contains("occupancy"));
    }

    @Test
    @DisplayName("WP-06 Edit 6: Capacity increase is always permitted and updates inventory")
    public void testUpdateRoomType_CapacityIncrease_Success() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Capacity Increase Room")
                .maxOccupancy(2)
                .pricePerNight(3000.0)
                .totalRooms(5)
                .availableRooms(5)
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto req = RoomTypeRequestDto.builder()
                .roomTypeName("Capacity Increase Room")
                .maxOccupancy(2)
                .totalRooms(12) // increased from 5 to 12
                .build();

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(req, authHeaders(managerToken)),
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoomType dbRoom = roomTypeRepository.findById(room.getId()).orElseThrow();
        assertEquals(12, dbRoom.getTotalRooms());
        assertEquals(12, dbRoom.getAvailableRooms());
    }

    @Test
    @DisplayName("WP-06 Edit 7: Capacity decrease rejected when new total is less than allocated rooms")
    public void testUpdateRoomType_CapacityDecrease_RejectedWhenExceedingCommitted() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Capacity Restrict Room")
                .maxOccupancy(2)
                .pricePerNight(3000.0)
                .totalRooms(10)
                .availableRooms(6) // 4 rooms allocated
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto req = RoomTypeRequestDto.builder()
                .roomTypeName("Capacity Restrict Room")
                .maxOccupancy(2)
                .totalRooms(3) // attempt to set total to 3 (< 4 allocated)
                .build();

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(req, authHeaders(managerToken)),
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("reserved or allocated")
                || response.getBody().get("message").toString().contains("inventory"));
    }

    @Test
    @DisplayName("WP-06 Edit 8: Image removal via empty string sets imageUrl to null in database")
    public void testUpdateRoomType_ImageRemoval_ClearsDatabaseImage() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Image Test Room")
                .maxOccupancy(2)
                .pricePerNight(3000.0)
                .totalRooms(5)
                .availableRooms(5)
                .imageUrl("http://example.com/existing.jpg")
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto req = RoomTypeRequestDto.builder()
                .roomTypeName("Image Test Room")
                .maxOccupancy(2)
                .totalRooms(5)
                .imageUrl("") // empty string signals removal
                .build();

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(req, authHeaders(managerToken)),
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoomType dbRoom = roomTypeRepository.findById(room.getId()).orElseThrow();
        assertNull(dbRoom.getImageUrl(), "Room imageUrl must be cleared to null in DB");
    }

    @Test
    @DisplayName("WP-06 Edit 9: Non-owner manager cannot edit room type (returns 403)")
    public void testUpdateRoomType_NonOwner_Forbidden() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Manager 1 Room")
                .maxOccupancy(2)
                .pricePerNight(3000.0)
                .totalRooms(5)
                .availableRooms(5)
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto req = RoomTypeRequestDto.builder()
                .roomTypeName("Hacked Name")
                .maxOccupancy(2)
                .totalRooms(5)
                .build();

        // Manager 2 attempts to edit Manager 1's room
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(req, authHeaders(manager2Token)),
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        RoomType dbRoom = roomTypeRepository.findById(room.getId()).orElseThrow();
        assertEquals("Manager 1 Room", dbRoom.getRoomTypeName(), "Room name must not be altered");
    }

    // =========================================================================
    // WP-06 RESTORE ARCHIVED ROOM TYPE TESTS
    // =========================================================================

    @Test
    @DisplayName("WP-06 Restore 1: Successful restoration of archived room type preserves ID, attributes, and sets status ACTIVE")
    public void testRestoreRoomType_Success() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Penthouse Suite")
                .maxOccupancy(4)
                .pricePerNight(15000.0)
                .totalRooms(2)
                .availableRooms(0)
                .amenities("Private Pool, Butler Service")
                .imageUrl("http://example.com/penthouse.jpg")
                .status("ARCHIVED")
                .build());

        Long originalId = room.getId();

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + originalId + "/restore",
                HttpMethod.PATCH,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ACTIVE", response.getBody().get("status"));
        assertEquals("Penthouse Suite", response.getBody().get("roomTypeName"));

        // Verify entity preservation in DB (same ID, all attributes intact, status ACTIVE, availableRooms = totalRooms)
        RoomType dbRoom = roomTypeRepository.findById(originalId).orElseThrow();
        assertEquals(originalId, dbRoom.getId());
        assertEquals(testHotel.getId(), dbRoom.getHotelId());
        assertEquals("Penthouse Suite", dbRoom.getRoomTypeName());
        assertEquals(4, dbRoom.getMaxOccupancy());
        assertEquals(15000.0, dbRoom.getPricePerNight());
        assertEquals(2, dbRoom.getTotalRooms());
        assertEquals(2, dbRoom.getAvailableRooms());
        assertEquals("Private Pool, Butler Service", dbRoom.getAmenities());
        assertEquals("http://example.com/penthouse.jpg", dbRoom.getImageUrl());
        assertEquals("ACTIVE", dbRoom.getStatus());
    }

    @Test
    @DisplayName("WP-06 Restore 2: Attempt to restore an already active room type is rejected with 400")
    public void testRestoreRoomType_AlreadyActive_Rejected() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Already Active Room")
                .maxOccupancy(2)
                .totalRooms(5)
                .availableRooms(5)
                .status("ACTIVE")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/restore",
                HttpMethod.PATCH,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("not archived"));
    }

    @Test
    @DisplayName("WP-06 Restore 3: Nonexistent room type returns 404 Not Found")
    public void testRestoreRoomType_Nonexistent_NotFound() {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/999999/restore",
                HttpMethod.PATCH,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("WP-06 Restore 4: Manager attempting to restore another manager's property room is rejected with 403")
    public void testRestoreRoomType_NonOwner_Forbidden() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Manager 1 Archived Room")
                .maxOccupancy(2)
                .totalRooms(3)
                .availableRooms(0)
                .status("ARCHIVED")
                .build());

        // Manager 2 attempts to restore Manager 1's archived room
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(manager2Token));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/restore",
                HttpMethod.PATCH,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("ARCHIVED", roomTypeRepository.findById(room.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("WP-06 Restore 5: Unauthorized request without valid JWT token is rejected with 401")
    public void testRestoreRoomType_Unauthorized_Rejected() {
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Unauth Restored Room")
                .maxOccupancy(2)
                .totalRooms(3)
                .availableRooms(0)
                .status("ARCHIVED")
                .build());

        // Call without Authorization header
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(null));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/restore",
                HttpMethod.PATCH,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("WP-06 Restore 6: Admin can restore an archived room type across any property")
    public void testRestoreRoomType_Admin_Success() {
        String adminToken = createTestToken(9999L, "ROLE_ADMIN", "admin@traveleasy.com", "System Admin", 3600000);

        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Admin Restored Room")
                .maxOccupancy(2)
                .totalRooms(4)
                .availableRooms(0)
                .status("ARCHIVED")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(adminToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/restore",
                HttpMethod.PATCH,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ACTIVE", response.getBody().get("status"));
        assertEquals("ACTIVE", roomTypeRepository.findById(room.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("WP-06 Restore 7: Reject restore if another active room type in property already has the same name")
    public void testRestoreRoomType_DuplicateActiveNameConflict_Rejected() {
        // Create an existing ACTIVE room named "Royal Suite"
        roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Royal Suite")
                .maxOccupancy(4)
                .totalRooms(2)
                .availableRooms(2)
                .status("ACTIVE")
                .build());

        // Create an ARCHIVED room also named "Royal Suite"
        RoomType archivedRoom = roomTypeRepository.save(RoomType.builder()
                .hotelId(testHotel.getId())
                .roomTypeName("Royal Suite")
                .maxOccupancy(3)
                .totalRooms(2)
                .availableRooms(0)
                .status("ARCHIVED")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + archivedRoom.getId() + "/restore",
                HttpMethod.PATCH,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("already exists"));
        assertEquals("ARCHIVED", roomTypeRepository.findById(archivedRoom.getId()).orElseThrow().getStatus());
    }
}
