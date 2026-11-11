package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.DailyInventoryDto;
import com.traveleasy.hotel.dto.HotelInventorySummaryDto;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HotelInventoryCalendarTest {

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
    @DisplayName("Calendar 1.1: Authorized manager retrieves inclusive daily calendar with auto-initialization")
    public void testCalendarRetrieval_Success() {
        Long managerId = 801L;
        HotelProperty hotel = createProperty(managerId, "Seaside Grand", "Goa");
        RoomType room = createRoom(hotel.getId(), "Deluxe Ocean View", 5000.0, 20, "ACTIVE");

        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(4); // 5 inclusive days: start, start+1, start+2, start+3, start+4

        String url = getBaseUrl() + "/rooms/" + room.getId() + "/inventory/calendar?startDate=" + start + "&endDate=" + end;
        ResponseEntity<List<DailyInventoryDto>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(createManagerToken(managerId))),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<DailyInventoryDto> days = response.getBody();
        assertNotNull(days);
        assertEquals(5, days.size(), "Date range must be inclusive: 5 calendar days");

        for (DailyInventoryDto day : days) {
            assertEquals(20, day.getTotalRooms());
            assertEquals(0, day.getBookedRooms());
            assertEquals(0, day.getHeldRooms());
            assertEquals(0, day.getBlockedRooms());
            assertEquals(20, day.getAvailableRooms());
            assertEquals(5000.0, day.getEffectivePrice());
        }
    }

    @Test
    @DisplayName("Calendar 1.2: Calendar reflects existing booked, held, and blocked daily room counts")
    public void testCalendarRetrieval_ReflectsBookedAndBlockedRooms() {
        Long managerId = 802L;
        HotelProperty hotel = createProperty(managerId, "Hilltop Manor", "Shimla");
        RoomType room = createRoom(hotel.getId(), "Valley Suite", 7500.0, 10, "ACTIVE");

        LocalDate date = LocalDate.now().plusDays(20);
        RoomDailyInventory inv = RoomDailyInventory.builder()
                .hotelId(hotel.getId())
                .roomId(room.getId())
                .inventoryDate(date)
                .totalRooms(10)
                .bookedRooms(4)
                .heldRooms(1)
                .blockedRooms(2)
                .build();
        dailyInventoryRepository.saveAndFlush(inv);

        String url = getBaseUrl() + "/rooms/" + room.getId() + "/inventory/calendar?startDate=" + date + "&endDate=" + date;
        ResponseEntity<List<DailyInventoryDto>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(createManagerToken(managerId))),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<DailyInventoryDto> days = response.getBody();
        assertNotNull(days);
        assertEquals(1, days.size());

        DailyInventoryDto item = days.get(0);
        assertEquals(10, item.getTotalRooms());
        assertEquals(4, item.getBookedRooms());
        assertEquals(1, item.getHeldRooms());
        assertEquals(2, item.getBlockedRooms());
        assertEquals(3, item.getAvailableRooms(), "Available must be 10 - 4 - 1 - 2 = 3");
    }

    @Test
    @DisplayName("Calendar 1.3: Cross-manager access is rejected (403 Forbidden)")
    public void testCalendarRetrieval_CrossManager_Forbidden() {
        Long ownerManagerId = 803L;
        Long attackerManagerId = 804L;

        HotelProperty hotel = createProperty(ownerManagerId, "Palace Royal", "Jaipur");
        RoomType room = createRoom(hotel.getId(), "Royal Suite", 12000.0, 5, "ACTIVE");

        String url = getBaseUrl() + "/rooms/" + room.getId() + "/inventory/calendar";
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(createManagerToken(attackerManagerId))),
                String.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().contains("Access denied") || response.getBody().contains("Forbidden"));
    }

    @Test
    @DisplayName("Calendar 1.4: Admin access is permitted across all hotel properties")
    public void testCalendarRetrieval_Admin_Success() {
        Long ownerManagerId = 805L;
        HotelProperty hotel = createProperty(ownerManagerId, "Metropolitan Inn", "Bangalore");
        RoomType room = createRoom(hotel.getId(), "Executive Studio", 4000.0, 15, "ACTIVE");

        String url = getBaseUrl() + "/rooms/" + room.getId() + "/inventory/calendar";
        ResponseEntity<List<DailyInventoryDto>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(createAdminToken())),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("Calendar 1.5: Calendar query on an ARCHIVED room type is rejected (400 Bad Request)")
    public void testCalendarRetrieval_ArchivedRoom_Rejected() {
        Long managerId = 806L;
        HotelProperty hotel = createProperty(managerId, "Sunset Bay", "Kochi");
        RoomType room = createRoom(hotel.getId(), "Old Standard Room", 3000.0, 8, "ARCHIVED");

        String url = getBaseUrl() + "/rooms/" + room.getId() + "/inventory/calendar";
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(createManagerToken(managerId))),
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("ARCHIVED"));
    }

    @Test
    @DisplayName("Calendar 1.6: Start date strictly after end date is rejected (400 Bad Request)")
    public void testCalendarRetrieval_InvalidDateRange_Rejected() {
        Long managerId = 807L;
        HotelProperty hotel = createProperty(managerId, "City Center Hotel", "Delhi");
        RoomType room = createRoom(hotel.getId(), "Comfort Single", 2500.0, 12, "ACTIVE");

        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = LocalDate.now().plusDays(5);

        String url = getBaseUrl() + "/rooms/" + room.getId() + "/inventory/calendar?startDate=" + start + "&endDate=" + end;
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(createManagerToken(managerId))),
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Start date must not be after end date"));
    }

    @Test
    @DisplayName("Calendar 1.7: Inventory summary excludes archived room types from aggregated metrics")
    public void testInventorySummary_ExcludesArchivedRoomTypes() {
        Long managerId = 808L;
        HotelProperty hotel = createProperty(managerId, "Grand Vista", "Pune");
        RoomType activeRoom = createRoom(hotel.getId(), "Active Premier Room", 6000.0, 10, "ACTIVE");
        RoomType archivedRoom = createRoom(hotel.getId(), "Archived Basic Room", 2000.0, 50, "ARCHIVED");

        LocalDate today = LocalDate.now().plusDays(2);
        String url = getBaseUrl() + "/" + hotel.getId() + "/inventory/summary?startDate=" + today + "&endDate=" + today;
        ResponseEntity<HotelInventorySummaryDto> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(createManagerToken(managerId))),
                HotelInventorySummaryDto.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        HotelInventorySummaryDto summary = response.getBody();
        assertNotNull(summary);

        // Daily total should reflect ONLY the active room (10), NOT the archived room (50)
        assertEquals(10, summary.getTotalPhysicalRoomNights(), "Archived rooms must not be counted in summary");
        assertEquals(10, summary.getAvailableRoomNights());
    }
}
