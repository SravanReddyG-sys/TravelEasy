package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.RoomBlockRequestDto;
import com.traveleasy.hotel.dto.RoomBlockResponseDto;
import com.traveleasy.hotel.model.*;
import com.traveleasy.hotel.repository.*;
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RoomBlockManagementTest {

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
    private RoomInventoryBlockRepository blockRepository;

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
        return createTestToken(9999L, "ROLE_ADMIN", "admin@traveleasy.com", "Admin User", 3600000);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HotelProperty createProperty(Long managerId, String name, String city) {
        HotelProperty hotel = HotelProperty.builder()
                .managerId(managerId)
                .managerName("Manager " + managerId)
                .name(name + " " + UUID.randomUUID().toString().substring(0, 6))
                .city(city)
                .address("100 Scenic Road")
                .starRating(4)
                .status("ACTIVE")
                .description("Luxury retreat")
                .build();
        return hotelRepository.save(hotel);
    }

    private RoomType createRoom(Long hotelId, String name, double price, int totalRooms, String status) {
        RoomType room = RoomType.builder()
                .hotelId(hotelId)
                .roomTypeName(name)
                .pricePerNight(price)
                .totalRooms(totalRooms)
                .availableRooms(totalRooms)
                .maxOccupancy(2)
                .status(status != null ? status : "ACTIVE")
                .imageUrl("https://images.unsplash.com/photo-1590490360182-c33d57733427")
                .build();
        return roomTypeRepository.save(room);
    }

    @Test
    @DisplayName("Block 1.1: Successful block creation validates active room, updates inventory, and enriches DTO")
    public void testCreateRoomBlock_Success() {
        HotelProperty hotel = createProperty(701L, "Ocean Palms", "Goa");
        RoomType room = createRoom(hotel.getId(), "Ocean Deluxe", 5000.0, 10, "ACTIVE");

        String token = createManagerToken(701L);
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = start.plusDays(3); // 3 nights

        RoomBlockRequestDto req = RoomBlockRequestDto.builder()
                .blockedCount(3)
                .startDate(start)
                .endDate(end)
                .reason("OPERATIONAL")
                .notes(" Corporate group booking ")
                .build();

        ResponseEntity<RoomBlockResponseDto> resp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(req, authHeaders(token)), RoomBlockResponseDto.class);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertNotNull(resp.getBody());
        RoomBlockResponseDto dto = resp.getBody();

        assertEquals("ACTIVE", dto.getStatus());
        assertEquals(3, dto.getBlockedCount());
        assertEquals("OPERATIONAL", dto.getReason());
        assertEquals("Corporate group booking", dto.getNotes());
        assertEquals(room.getId(), dto.getRoomId());
        assertEquals("Ocean Deluxe", dto.getRoomTypeName());
        assertEquals(10, dto.getTotalRooms());
        assertEquals(2, dto.getMaxOccupancy());
        assertNotNull(dto.getBlockCode());
        assertTrue(dto.getBlockCode().startsWith("BLK-"));

        // Verify inventory for all 3 nights
        LocalDate cur = start;
        while (cur.isBefore(end)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), cur).orElseThrow();
            assertEquals(3, daily.getBlockedRooms());
            assertEquals(7, daily.getAvailableRooms());
            cur = cur.plusDays(1);
        }
    }

    @Test
    @DisplayName("Block 1.2: Creating block on an ARCHIVED room type is rejected (400 Bad Request)")
    public void testCreateRoomBlock_ArchivedRoomType_Rejected() {
        HotelProperty hotel = createProperty(702L, "Mountain Crest", "Manali");
        RoomType archivedRoom = createRoom(hotel.getId(), "Old Chalet", 3500.0, 5, "ARCHIVED");

        String token = createManagerToken(702L);
        LocalDate start = LocalDate.now().plusDays(3);
        LocalDate end = start.plusDays(2);

        RoomBlockRequestDto req = RoomBlockRequestDto.builder()
                .blockedCount(2)
                .startDate(start)
                .endDate(end)
                .reason("MAINTENANCE")
                .notes("Roof repair")
                .build();

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + archivedRoom.getId() + "/blocks",
                new HttpEntity<>(req, authHeaders(token)), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().get("message").toString().contains("archived"));
    }

    @Test
    @DisplayName("Block 1.3: Notes exceeding 250 characters are rejected (400 Bad Request)")
    public void testCreateRoomBlock_NotesExceeding250_Rejected() {
        HotelProperty hotel = createProperty(703L, "Urban Stay", "Bangalore");
        RoomType room = createRoom(hotel.getId(), "Studio Pod", 2000.0, 8, "ACTIVE");

        String token = createManagerToken(703L);
        LocalDate start = LocalDate.now().plusDays(4);
        LocalDate end = start.plusDays(2);

        String longNotes = "A".repeat(251);

        RoomBlockRequestDto req = RoomBlockRequestDto.builder()
                .blockedCount(1)
                .startDate(start)
                .endDate(end)
                .reason("MAINTENANCE")
                .notes(longNotes)
                .build();

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(req, authHeaders(token)), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().get("message").toString().contains("250"));
    }

    @Test
    @DisplayName("Block 1.4: Cross-property room blocking is rejected (400 Bad Request)")
    public void testCreateRoomBlock_CrossProperty_Rejected() {
        HotelProperty hotel1 = createProperty(704L, "City Inn", "Delhi");
        HotelProperty hotel2 = createProperty(704L, "Metro Lodge", "Delhi");
        RoomType roomHotel2 = createRoom(hotel2.getId(), "Metro Standard", 2500.0, 6, "ACTIVE");

        String token = createManagerToken(704L);
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = start.plusDays(2);

        RoomBlockRequestDto req = RoomBlockRequestDto.builder()
                .blockedCount(1)
                .startDate(start)
                .endDate(end)
                .reason("OTHER")
                .build();

        // Attempt to create block for hotel1 using room from hotel2
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel1.getId() + "/rooms/" + roomHotel2.getId() + "/blocks",
                new HttpEntity<>(req, authHeaders(token)), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().get("message").toString().contains("does not belong"));
    }

    @Test
    @DisplayName("Block 1.5: Cross-manager access is forbidden (403 Forbidden)")
    public void testCreateRoomBlock_CrossManager_Forbidden() {
        HotelProperty hotel = createProperty(705L, "Sunrise Suites", "Chennai");
        RoomType room = createRoom(hotel.getId(), "Executive Room", 4500.0, 5, "ACTIVE");

        String tokenManagerB = createManagerToken(706L); // Different manager
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = start.plusDays(2);

        RoomBlockRequestDto req = RoomBlockRequestDto.builder()
                .blockedCount(1)
                .startDate(start)
                .endDate(end)
                .reason("OPERATIONAL")
                .build();

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(req, authHeaders(tokenManagerB)), Map.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    @DisplayName("Block 2.1: Successful block release restores inventory and sets status to RELEASED")
    public void testReleaseRoomBlock_Success() {
        HotelProperty hotel = createProperty(707L, "Palm Breeze", "Kochi");
        RoomType room = createRoom(hotel.getId(), "Lagoon Villa", 7000.0, 6, "ACTIVE");

        String token = createManagerToken(707L);
        LocalDate start = LocalDate.now().plusDays(6);
        LocalDate end = start.plusDays(3);

        RoomBlockRequestDto req = RoomBlockRequestDto.builder()
                .blockedCount(2)
                .startDate(start)
                .endDate(end)
                .reason("RENOVATION")
                .notes("Balcony tiling")
                .build();

        ResponseEntity<RoomBlockResponseDto> createResp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(req, authHeaders(token)), RoomBlockResponseDto.class);
        Long blockId = createResp.getBody().getId();

        // Release the block
        ResponseEntity<RoomBlockResponseDto> releaseResp = restTemplate.exchange(
                getBaseUrl() + "/blocks/" + blockId + "/release",
                HttpMethod.PUT, new HttpEntity<>(authHeaders(token)), RoomBlockResponseDto.class);

        assertEquals(HttpStatus.OK, releaseResp.getStatusCode());
        assertEquals("RELEASED", releaseResp.getBody().getStatus());
        assertNotNull(releaseResp.getBody().getReleasedAt());

        // Inventory restored for all 3 nights
        LocalDate cur = start;
        while (cur.isBefore(end)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), cur).orElseThrow();
            assertEquals(0, daily.getBlockedRooms());
            assertEquals(6, daily.getAvailableRooms());
            cur = cur.plusDays(1);
        }

        // Verify block remains preserved in repository
        RoomInventoryBlock saved = blockRepository.findById(blockId).orElseThrow();
        assertEquals("RELEASED", saved.getStatus());
    }

    @Test
    @DisplayName("Block 2.2: Repeated release of an already released block is rejected (400 Bad Request)")
    public void testReleaseRoomBlock_RepeatedRelease_Rejected() {
        HotelProperty hotel = createProperty(708L, "Grand Oasis", "Jaipur");
        RoomType room = createRoom(hotel.getId(), "Heritage Suite", 6000.0, 4, "ACTIVE");

        String token = createManagerToken(708L);
        LocalDate start = LocalDate.now().plusDays(8);
        LocalDate end = start.plusDays(2);

        RoomBlockRequestDto req = RoomBlockRequestDto.builder()
                .blockedCount(1)
                .startDate(start)
                .endDate(end)
                .reason("MAINTENANCE")
                .build();

        ResponseEntity<RoomBlockResponseDto> createResp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(req, authHeaders(token)), RoomBlockResponseDto.class);
        Long blockId = createResp.getBody().getId();

        // First release succeeds
        restTemplate.exchange(
                getBaseUrl() + "/blocks/" + blockId + "/release",
                HttpMethod.PUT, new HttpEntity<>(authHeaders(token)), RoomBlockResponseDto.class);

        // Second release must fail
        ResponseEntity<Map> secondReleaseResp = restTemplate.exchange(
                getBaseUrl() + "/blocks/" + blockId + "/release",
                HttpMethod.PUT, new HttpEntity<>(authHeaders(token)), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, secondReleaseResp.getStatusCode());
        assertTrue(secondReleaseResp.getBody().get("message").toString().contains("already in RELEASED status"));
    }

    @Test
    @DisplayName("Block 3.1: Listing hotel blocks returns active and released records with historical room preservation")
    public void testGetBlocksByHotel_PreservesHistoricalArchivedRooms() {
        HotelProperty hotel = createProperty(709L, "Lake Palace", "Udaipur");
        RoomType room = createRoom(hotel.getId(), "Lake View Room", 8000.0, 5, "ACTIVE");

        String token = createManagerToken(709L);
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(2);

        // Create block
        RoomBlockRequestDto req = RoomBlockRequestDto.builder()
                .blockedCount(2)
                .startDate(start)
                .endDate(end)
                .reason("OPERATIONAL")
                .notes("VIP State Visit")
                .build();

        ResponseEntity<RoomBlockResponseDto> createResp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(req, authHeaders(token)), RoomBlockResponseDto.class);
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());

        // Now archive the room type
        room.setStatus("ARCHIVED");
        roomTypeRepository.save(room);

        // Fetch blocks for hotel
        ResponseEntity<List<RoomBlockResponseDto>> listResp = restTemplate.exchange(
                getBaseUrl() + "/" + hotel.getId() + "/blocks",
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<List<RoomBlockResponseDto>>() {});

        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        assertNotNull(listResp.getBody());
        assertFalse(listResp.getBody().isEmpty());

        RoomBlockResponseDto item = listResp.getBody().get(0);
        assertEquals("Lake View Room", item.getRoomTypeName());
        assertEquals("VIP State Visit", item.getNotes());
        assertEquals(5, item.getTotalRooms());
    }

    @Test
    @DisplayName("Block 3.2: Single block retrieval by ID GET /blocks/{blockId} succeeds for authorized manager")
    public void testGetBlockById_Success() {
        HotelProperty hotel = createProperty(710L, "Highland Resort", "Munnar");
        RoomType room = createRoom(hotel.getId(), "Tea Garden Cottage", 3800.0, 4, "ACTIVE");

        String token = createManagerToken(710L);
        LocalDate start = LocalDate.now().plusDays(15);
        LocalDate end = start.plusDays(4);

        RoomBlockRequestDto req = RoomBlockRequestDto.builder()
                .blockedCount(1)
                .startDate(start)
                .endDate(end)
                .reason("OTHER")
                .notes("Annual inspection")
                .build();

        ResponseEntity<RoomBlockResponseDto> createResp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(req, authHeaders(token)), RoomBlockResponseDto.class);
        Long blockId = createResp.getBody().getId();

        ResponseEntity<RoomBlockResponseDto> getResp = restTemplate.exchange(
                getBaseUrl() + "/blocks/" + blockId,
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), RoomBlockResponseDto.class);

        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertNotNull(getResp.getBody());
        assertEquals(blockId, getResp.getBody().getId());
        assertEquals("Tea Garden Cottage", getResp.getBody().getRoomTypeName());
        assertEquals("Annual inspection", getResp.getBody().getNotes());
    }
}
