package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.HotelManageDto;
import com.traveleasy.hotel.dto.HotelRequestDto;
import com.traveleasy.hotel.dto.RoomTypeDto;
import com.traveleasy.hotel.dto.RoomTypeRequestDto;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HotelDomainAndBusinessLogicTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HotelPropertyRepository hotelRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

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

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return headers;
    }

    // =========================================================================
    // 1. HOTEL PROPERTY REGISTRATION & VALIDATION (BD-03)
    // =========================================================================

    @Test
    @DisplayName("WP02-Prop-1.1: Register property with valid data initializes ACTIVE status and authoritatively binds manager")
    public void testRegisterProperty_ValidData_Returns201AndActive() {
        Long managerId = 1101L;
        String token = createManagerToken(managerId);

        HotelRequestDto request = HotelRequestDto.builder()
                .name("Grand Bay Resort " + System.currentTimeMillis())
                .city("Visakhapatnam")
                .address("Beach Road, Pandurangapuram")
                .starRating(5)
                .description("Luxury beach resort overlooking the Bay of Bengal")
                .amenities("Pool, Spa, Beach View, Breakfast")
                .imageUrl("https://images.unsplash.com/photo-1566073771259-6a8506099945")
                .build();

        HttpEntity<HotelRequestDto> entity = new HttpEntity<>(request, authHeaders(token));
        ResponseEntity<HotelProperty> response = restTemplate.postForEntity(getBaseUrl(), entity, HotelProperty.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        HotelProperty created = response.getBody();
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(managerId, created.getManagerId());
        assertEquals("Manager " + managerId, created.getManagerName());
        assertEquals(5, created.getStarRating());
    }

    @Test
    @DisplayName("WP02-Prop-1.2: Registration fails when property name is blank (returns 400)")
    public void testRegisterProperty_MissingName_Returns400() {
        String token = createManagerToken(1102L);

        HotelRequestDto request = HotelRequestDto.builder()
                .name("   ")
                .city("Hyderabad")
                .address("Hitec City")
                .build();

        HttpEntity<HotelRequestDto> entity = new HttpEntity<>(request, authHeaders(token));
        ResponseEntity<Map> response = restTemplate.postForEntity(getBaseUrl(), entity, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("mandatory"));
    }

    @Test
    @DisplayName("WP02-Prop-1.3: Registration fails when city is blank (returns 400)")
    public void testRegisterProperty_MissingCity_Returns400() {
        String token = createManagerToken(1103L);

        HotelRequestDto request = HotelRequestDto.builder()
                .name("Cityless Hotel")
                .city("")
                .address("Main Street")
                .build();

        HttpEntity<HotelRequestDto> entity = new HttpEntity<>(request, authHeaders(token));
        ResponseEntity<Map> response = restTemplate.postForEntity(getBaseUrl(), entity, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("City"));
    }

    @Test
    @DisplayName("WP02-Prop-1.4: Registration fails when address is blank (returns 400)")
    public void testRegisterProperty_MissingAddress_Returns400() {
        String token = createManagerToken(1104L);

        HotelRequestDto request = HotelRequestDto.builder()
                .name("Addressless Hotel")
                .city("Goa")
                .address(null)
                .build();

        HttpEntity<HotelRequestDto> entity = new HttpEntity<>(request, authHeaders(token));
        ResponseEntity<Map> response = restTemplate.postForEntity(getBaseUrl(), entity, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Address"));
    }

    @Test
    @DisplayName("WP02-Prop-1.5: Registration fails when star rating is out of bounds (returns 400)")
    public void testRegisterProperty_InvalidStarRating_Returns400() {
        String token = createManagerToken(1105L);

        HotelRequestDto request = HotelRequestDto.builder()
                .name("Seven Star Palace")
                .city("Bengaluru")
                .address("MG Road")
                .starRating(7) // Invalid: max 5
                .build();

        HttpEntity<HotelRequestDto> entity = new HttpEntity<>(request, authHeaders(token));
        ResponseEntity<Map> response = restTemplate.postForEntity(getBaseUrl(), entity, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Star rating"));
    }

    // =========================================================================
    // 2. MULTIPLE PROPERTIES UNDER ONE HOTEL MANAGER (BD-02)
    // =========================================================================

    @Test
    @DisplayName("WP02-Prop-2.1: Single hotel manager can manage multiple distinct hotel properties (BD-02)")
    public void testMultipleProperties_SameManager_BothRegisteredAndRetrieved() {
        Long managerId = 1201L;
        String token = createManagerToken(managerId);

        HotelRequestDto prop1 = HotelRequestDto.builder()
                .name("Vijayawada Grand Inn " + System.currentTimeMillis())
                .city("Vijayawada")
                .address("MG Road")
                .starRating(3)
                .build();

        HotelRequestDto prop2 = HotelRequestDto.builder()
                .name("Hyderabad Pearl Suites " + System.currentTimeMillis())
                .city("Hyderabad")
                .address("Banjara Hills")
                .starRating(4)
                .build();

        // Register Property 1
        ResponseEntity<HotelProperty> res1 = restTemplate.postForEntity(getBaseUrl(), new HttpEntity<>(prop1, authHeaders(token)), HotelProperty.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        // Register Property 2
        ResponseEntity<HotelProperty> res2 = restTemplate.postForEntity(getBaseUrl(), new HttpEntity<>(prop2, authHeaders(token)), HotelProperty.class);
        assertEquals(HttpStatus.CREATED, res2.getStatusCode());

        // Retrieve properties via /api/hotels/manager/{managerId}
        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<List> listResp = restTemplate.exchange(
                getBaseUrl() + "/manager/" + managerId,
                HttpMethod.GET,
                getEntity,
                List.class
        );

        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        List<Map<String, Object>> properties = listResp.getBody();
        assertNotNull(properties);
        assertTrue(properties.size() >= 2);

        // Retrieve via /api/hotels/my-hotels
        ResponseEntity<List> myHotelsResp = restTemplate.exchange(
                getBaseUrl() + "/my-hotels",
                HttpMethod.GET,
                getEntity,
                List.class
        );
        assertEquals(HttpStatus.OK, myHotelsResp.getStatusCode());
        assertTrue(myHotelsResp.getBody().size() >= 2);
    }

    // =========================================================================
    // 3. PROPERTY RETRIEVAL FOR MANAGEMENT CONSOLE
    // =========================================================================

    @Test
    @DisplayName("WP02-Prop-3.1: Owning manager can retrieve management property details with attached rooms")
    public void testGetHotelForManagement_OwningManager_ReturnsFullDetailsAndRooms() {
        Long managerId = 1301L;
        String token = createManagerToken(managerId);

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Royal Orchid")
                .city("Mysore")
                .address("KRS Road")
                .managerId(managerId)
                .managerName("Manager 1301")
                .status("ACTIVE")
                .starRating(4)
                .build());

        roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Royal Club")
                .pricePerNight(6000.0)
                .totalRooms(8)
                .availableRooms(8)
                .maxOccupancy(2)
                .status("ACTIVE")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<HotelManageDto> response = restTemplate.exchange(
                getBaseUrl() + "/manage/" + hotel.getId(),
                HttpMethod.GET,
                entity,
                HotelManageDto.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        HotelManageDto dto = response.getBody();
        assertNotNull(dto);
        assertEquals("Royal Orchid", dto.getName());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals(managerId, dto.getManagerId());
        assertEquals(1, dto.getRooms().size());
        assertEquals("Royal Club", dto.getRooms().get(0).getRoomTypeName());
    }

    @Test
    @DisplayName("WP02-Prop-3.2: Other manager cannot access management details of a property they do not own (returns 403)")
    public void testGetHotelForManagement_OtherManager_Returns403() {
        Long ownerId = 1302L;
        Long intruderId = 1303L;

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Private Sanctuary")
                .city("Coorg")
                .address("Madikeri Hills")
                .managerId(ownerId)
                .status("ACTIVE")
                .build());

        String intruderToken = createManagerToken(intruderId);
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(intruderToken));

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/manage/" + hotel.getId(),
                HttpMethod.GET,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // =========================================================================
    // 4. PROPERTY UPDATES & IMMUTABILITY
    // =========================================================================

    @Test
    @DisplayName("WP02-Prop-4.1: Owning manager can update property information successfully (returns 200)")
    public void testUpdateProperty_OwningManager_UpdatesFieldsSuccessfully() {
        Long managerId = 1401L;
        String token = createManagerToken(managerId);

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Old Beach Inn")
                .city("Goa")
                .address("Old Lane")
                .starRating(3)
                .managerId(managerId)
                .status("ACTIVE")
                .build());

        HotelRequestDto updateReq = HotelRequestDto.builder()
                .name("Renovated Beach Resort")
                .city("Goa")
                .address("New Beach Boulevard")
                .starRating(4)
                .description("Newly renovated luxury resort")
                .amenities("Pool, Free Wi-Fi, Gym")
                .build();

        HttpEntity<HotelRequestDto> entity = new HttpEntity<>(updateReq, authHeaders(token));
        ResponseEntity<HotelProperty> response = restTemplate.exchange(
                getBaseUrl() + "/" + hotel.getId(),
                HttpMethod.PUT,
                entity,
                HotelProperty.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        HotelProperty updated = response.getBody();
        assertNotNull(updated);
        assertEquals("Renovated Beach Resort", updated.getName());
        assertEquals("New Beach Boulevard", updated.getAddress());
        assertEquals(4, updated.getStarRating());
        assertEquals(managerId, updated.getManagerId());
    }

    @Test
    @DisplayName("WP02-Prop-4.2: Another manager cannot update a property they do not own (returns 403)")
    public void testUpdateProperty_OtherManager_Returns403() {
        Long ownerId = 1402L;
        Long intruderId = 1403L;

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Protected Manor")
                .city("Ooty")
                .address("Hilltop")
                .managerId(ownerId)
                .status("ACTIVE")
                .build());

        HotelRequestDto updateReq = HotelRequestDto.builder()
                .name("Hijacked Manor")
                .city("Ooty")
                .address("Hilltop")
                .build();

        String intruderToken = createManagerToken(intruderId);
        HttpEntity<HotelRequestDto> entity = new HttpEntity<>(updateReq, authHeaders(intruderToken));

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/" + hotel.getId(),
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // =========================================================================
    // 5. ROOM-TYPE CREATION & VALIDATIONS
    // =========================================================================

    @Test
    @DisplayName("WP02-Room-5.1: Create room category with valid attributes initializes availableRooms equal to totalRooms")
    public void testCreateRoomType_ValidData_Returns201() {
        Long managerId = 1501L;
        String token = createManagerToken(managerId);

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Hill View Lodge")
                .city("Kodaikanal")
                .address("Lake Road")
                .managerId(managerId)
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto request = RoomTypeRequestDto.builder()
                .roomTypeName("Lakeview Premium Suite")
                .maxOccupancy(3)
                .pricePerNight(5500.0)
                .totalRooms(12)
                .amenities("Lake View, Balcony, Heater, Wi-Fi")
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(request, authHeaders(token));
        ResponseEntity<RoomType> response = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms",
                entity,
                RoomType.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        RoomType created = response.getBody();
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("Lakeview Premium Suite", created.getRoomTypeName());
        assertEquals(12, created.getTotalRooms());
        assertEquals(12, created.getAvailableRooms());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(3, created.getMaxOccupancy());
    }

    @Test
    @DisplayName("WP02-Room-5.2: Duplicate room category name in the same property is rejected (returns 400)")
    public void testCreateRoomType_DuplicateNameInSameHotel_Returns400() {
        Long managerId = 1502L;
        String token = createManagerToken(managerId);

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Coastal Haven")
                .city("Kochi")
                .address("Marine Drive")
                .managerId(managerId)
                .status("ACTIVE")
                .build());

        roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Deluxe Suite")
                .pricePerNight(4000.0)
                .totalRooms(10)
                .availableRooms(10)
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto duplicate = RoomTypeRequestDto.builder()
                .roomTypeName("deluxe suite") // Case-insensitive duplicate
                .maxOccupancy(2)
                .pricePerNight(4200.0)
                .totalRooms(5)
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(duplicate, authHeaders(token));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("already exists"));
    }

    @Test
    @DisplayName("WP02-Room-5.3: Creation fails when price per night is non-positive (returns 400)")
    public void testCreateRoomType_InvalidPrice_Returns400() {
        Long managerId = 1503L;
        String token = createManagerToken(managerId);

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Budget Motel")
                .city("Chennai")
                .address("GST Road")
                .managerId(managerId)
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto invalid = RoomTypeRequestDto.builder()
                .roomTypeName("Free Room")
                .pricePerNight(0.0) // Invalid
                .totalRooms(5)
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(invalid, authHeaders(token));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Price per night"));
    }

    // =========================================================================
    // 6. ROOM-TYPE MODIFICATIONS & BD-07 RESTRICTIONS
    // =========================================================================

    @Test
    @DisplayName("WP02-BD07-6.1: Non-structural modifications (amenities, name, price) allowed even with active allocations")
    public void testUpdateRoomType_NonStructuralEdit_AllowedEvenWithAllocations() {
        Long managerId = 1601L;
        String token = createManagerToken(managerId);

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Puri Beach Inn")
                .city("Puri")
                .address("Sea Beach")
                .managerId(managerId)
                .status("ACTIVE")
                .build());

        // 10 total rooms, 6 available -> 4 allocated
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Super Deluxe")
                .pricePerNight(3500.0)
                .totalRooms(10)
                .availableRooms(6)
                .maxOccupancy(2)
                .amenities("Fan, TV")
                .status("ACTIVE")
                .build());

        RoomTypeRequestDto updateReq = RoomTypeRequestDto.builder()
                .roomTypeName("Super Deluxe Sea Facing")
                .amenities("AC, Smart TV, Free Breakfast, Balcony")
                .pricePerNight(4000.0)
                .totalRooms(10) // Same total
                .maxOccupancy(2) // Same occupancy
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(updateReq, authHeaders(token));
        ResponseEntity<RoomType> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.PUT,
                entity,
                RoomType.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoomType updated = response.getBody();
        assertNotNull(updated);
        assertEquals("Super Deluxe Sea Facing", updated.getRoomTypeName());
        assertEquals("AC, Smart TV, Free Breakfast, Balcony", updated.getAmenities());
        assertEquals(4000.0, updated.getPricePerNight());
        assertEquals(6, updated.getAvailableRooms()); // Allocations preserved!
    }

    @Test
    @DisplayName("WP02-BD07-6.2: Increasing inventory is permitted and expands availableRooms proportionally")
    public void testUpdateRoomType_InventoryIncrease_AllowedAndAdjustsAvailable() {
        Long managerId = 1602L;
        String token = createManagerToken(managerId);

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Expansion Hotel")
                .city("Jaipur")
                .address("MI Road")
                .managerId(managerId)
                .status("ACTIVE")
                .build());

        // 10 total, 7 available -> 3 allocated
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Heritage Room")
                .pricePerNight(5000.0)
                .totalRooms(10)
                .availableRooms(7)
                .maxOccupancy(2)
                .status("ACTIVE")
                .build());

        // Increase total rooms to 15
        RoomTypeRequestDto updateReq = RoomTypeRequestDto.builder()
                .roomTypeName("Heritage Room")
                .pricePerNight(5000.0)
                .totalRooms(15) // +5 rooms
                .maxOccupancy(2)
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(updateReq, authHeaders(token));
        ResponseEntity<RoomType> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.PUT,
                entity,
                RoomType.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoomType updated = response.getBody();
        assertNotNull(updated);
        assertEquals(15, updated.getTotalRooms());
        assertEquals(12, updated.getAvailableRooms()); // 7 + 5 = 12
    }

    @Test
    @DisplayName("WP02-BD07-6.3: Inventory reduction below currently allocated rooms is rejected (returns 400)")
    public void testUpdateRoomType_InventoryReduction_ViolatingAllocations_Returns400() {
        Long managerId = 1603L;
        String token = createManagerToken(managerId);

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Capped Resort")
                .city("Shimla")
                .address("Mall Road")
                .managerId(managerId)
                .status("ACTIVE")
                .build());

        // 10 total, 3 available -> 7 allocated/reserved rooms
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Pine Cottage")
                .pricePerNight(6000.0)
                .totalRooms(10)
                .availableRooms(3)
                .maxOccupancy(2)
                .status("ACTIVE")
                .build());

        // Attempt to reduce total rooms to 5 (less than 7 allocated)
        RoomTypeRequestDto invalidReduction = RoomTypeRequestDto.builder()
                .roomTypeName("Pine Cottage")
                .pricePerNight(6000.0)
                .totalRooms(5)
                .maxOccupancy(2)
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(invalidReduction, authHeaders(token));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("currently reserved"));
    }

    @Test
    @DisplayName("WP02-BD07-6.4: Reducing maximum guest occupancy while active reservations exist is rejected (returns 400)")
    public void testUpdateRoomType_OccupancyReduction_WithActiveReservations_Returns400() {
        Long managerId = 1604L;
        String token = createManagerToken(managerId);

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Family Resort")
                .city("Agra")
                .address("Taj Road")
                .managerId(managerId)
                .status("ACTIVE")
                .build());

        // 5 total, 3 available -> 2 rooms currently booked
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Family Suite")
                .pricePerNight(7000.0)
                .totalRooms(5)
                .availableRooms(3)
                .maxOccupancy(4) // Configured for 4 guests
                .status("ACTIVE")
                .build());

        // Attempt to reduce capacity from 4 to 2 while bookings exist
        RoomTypeRequestDto invalidOccupancy = RoomTypeRequestDto.builder()
                .roomTypeName("Family Suite")
                .pricePerNight(7000.0)
                .totalRooms(5)
                .maxOccupancy(2) // Reduced occupancy
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(invalidOccupancy, authHeaders(token));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.PUT,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("maximum occupancy"));
    }

    // =========================================================================
    // 7. SOFT-DELETION & ARCHIVAL (BD-07)
    // =========================================================================

    @Test
    @DisplayName("WP02-BD07-7.1: Archiving room category with active allocations is rejected (returns 400)")
    public void testArchiveRoomType_WithActiveAllocations_Returns400() {
        Long managerId = 1701L;
        String token = createManagerToken(managerId);

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Archival Test Inn")
                .city("Pune")
                .address("FC Road")
                .managerId(managerId)
                .status("ACTIVE")
                .build());

        // 10 total, 9 available -> 1 allocated
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Executive Room")
                .pricePerNight(3000.0)
                .totalRooms(10)
                .availableRooms(9)
                .maxOccupancy(2)
                .status("ACTIVE")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.DELETE,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("active reservations exist"));
    }

    @Test
    @DisplayName("WP02-BD07-7.2: Archiving room category with zero allocations sets status ARCHIVED without deleting record")
    public void testArchiveRoomType_WithZeroAllocations_SoftArchivedAndPreservedInDb() {
        Long managerId = 1702L;
        String token = createManagerToken(managerId);

        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Archival Success Inn")
                .city("Nagpur")
                .address("Wardha Road")
                .managerId(managerId)
                .status("ACTIVE")
                .build());

        // 10 total, 10 available -> 0 allocated
        RoomType room = roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Standard Double")
                .pricePerNight(2500.0)
                .totalRooms(10)
                .availableRooms(10)
                .maxOccupancy(2)
                .status("ACTIVE")
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(),
                HttpMethod.DELETE,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ARCHIVED", response.getBody().get("status"));

        // Verify entity remains in database for historical booking reference integrity
        RoomType archived = roomTypeRepository.findById(room.getId()).orElse(null);
        assertNotNull(archived, "Historical room category must NOT be deleted from database");
        assertEquals("ARCHIVED", archived.getStatus());
        assertEquals(0, archived.getAvailableRooms());
    }

    @Test
    @DisplayName("WP02-BD07-7.3: Archived room categories are excluded from public traveller details")
    public void testPublicHotelDetails_ExcludesArchivedRoomTypes() {
        HotelProperty hotel = hotelRepository.save(HotelProperty.builder()
                .name("Public Explorer Hotel")
                .city("Varanasi")
                .address("Ghat Road")
                .status("ACTIVE")
                .build());

        roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Active Ghat View")
                .pricePerNight(3000.0)
                .totalRooms(5)
                .availableRooms(5)
                .status("ACTIVE")
                .build());

        roomTypeRepository.save(RoomType.builder()
                .hotelId(hotel.getId())
                .roomTypeName("Retired Ancient Suite")
                .pricePerNight(5000.0)
                .totalRooms(2)
                .availableRooms(0)
                .status("ARCHIVED")
                .build());

        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/" + hotel.getId(),
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Map<String, Object>> rooms = (List<Map<String, Object>>) response.getBody().get("rooms");
        assertNotNull(rooms);
        assertEquals(1, rooms.size());
        assertEquals("Active Ghat View", rooms.get(0).get("roomTypeName"));
    }
}
