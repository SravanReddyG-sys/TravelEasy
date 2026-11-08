package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.PriceCalendarEntryDto;
import com.traveleasy.hotel.dto.PriceOverrideRequestDto;
import com.traveleasy.hotel.dto.PriceRangeOverrideRequestDto;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomPriceOverride;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
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
public class PricingCalendarOverrideTest {

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

    private RoomType createRoom(Long hotelId, String name, Double price, Integer totalRooms) {
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

    @Test
    @DisplayName("WP-07.2 Test 1: Get pricing calendar for active room type")
    public void test1_getPricingCalendarActiveRoom() {
        Long managerId = 9101L;
        HotelProperty hotel = createProperty(managerId, "Grand Palace", "Mumbai");
        RoomType room = createRoom(hotel.getId(), "Deluxe Room", 4500.0, 20);

        LocalDate start = LocalDate.of(2026, 12, 1);
        LocalDate end = LocalDate.of(2026, 12, 10);

        String token = createManagerToken(managerId);
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));

        ResponseEntity<String> raw = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/calendar?startDate=" + start + "&endDate=" + end,
                HttpMethod.GET,
                entity,
                String.class
        );
        assertTrue(raw.getBody().contains("\"isOverride\":false"));

        ResponseEntity<List<PriceCalendarEntryDto>> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/calendar?startDate=" + start + "&endDate=" + end,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<PriceCalendarEntryDto>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(10, response.getBody().size());

        PriceCalendarEntryDto firstDay = response.getBody().get(0);
        assertEquals(start, firstDay.getDate());
        assertEquals(4500.0, firstDay.getBasePrice());
        assertNull(firstDay.getOverridePrice());
        assertEquals(4500.0, firstDay.getEffectivePrice());
        assertFalse(firstDay.isOverride());
    }

    @Test
    @DisplayName("WP-07.2 Test 2: Set single-date price override")
    public void test2_setSingleDatePriceOverride() {
        Long managerId = 9102L;
        HotelProperty hotel = createProperty(managerId, "Seaside Resort", "Goa");
        RoomType room = createRoom(hotel.getId(), "Ocean Suite", 5000.0, 10);

        LocalDate overrideDate = LocalDate.of(2026, 12, 25);
        PriceOverrideRequestDto request = PriceOverrideRequestDto.builder()
                .overrideDate(overrideDate)
                .pricePerNight(7500.0)
                .build();

        String token = createManagerToken(managerId);
        HttpEntity<PriceOverrideRequestDto> entity = new HttpEntity<>(request, authHeaders(token));

        ResponseEntity<String> rawOver = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override",
                HttpMethod.POST,
                entity,
                String.class
        );
        assertTrue(rawOver.getBody().contains("\"isOverride\":true"));

        ResponseEntity<PriceCalendarEntryDto> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override",
                HttpMethod.POST,
                entity,
                PriceCalendarEntryDto.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(overrideDate, response.getBody().getDate());
        assertEquals(5000.0, response.getBody().getBasePrice());
        assertEquals(7500.0, response.getBody().getOverridePrice());
        assertEquals(7500.0, response.getBody().getEffectivePrice());
        assertTrue(response.getBody().isOverride());

        // Verify effective price resolution via service
        assertEquals(7500.0, pricingService.resolveEffectivePrice(room, overrideDate));
    }

    @Test
    @DisplayName("WP-07.2 Test 3: Set date-range price override")
    public void test3_setRangePriceOverride() {
        Long managerId = 9103L;
        HotelProperty hotel = createProperty(managerId, "Hilltop Haven", "Shimla");
        RoomType room = createRoom(hotel.getId(), "Valley View", 4000.0, 15);

        LocalDate start = LocalDate.of(2026, 12, 20);
        LocalDate end = LocalDate.of(2026, 12, 24);

        PriceRangeOverrideRequestDto request = PriceRangeOverrideRequestDto.builder()
                .startDate(start)
                .endDate(end)
                .pricePerNight(6000.0)
                .build();

        String token = createManagerToken(managerId);
        HttpEntity<PriceRangeOverrideRequestDto> entity = new HttpEntity<>(request, authHeaders(token));

        ResponseEntity<List<PriceCalendarEntryDto>> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override-range",
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<List<PriceCalendarEntryDto>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(5, response.getBody().size());

        for (PriceCalendarEntryDto dto : response.getBody()) {
            assertEquals(4000.0, dto.getBasePrice());
            assertEquals(6000.0, dto.getOverridePrice());
            assertEquals(6000.0, dto.getEffectivePrice());
            assertTrue(dto.isOverride());
        }
    }

    @Test
    @DisplayName("WP-07.2 Test 4: Delete price override and restore base price")
    public void test4_deletePriceOverride() {
        Long managerId = 9104L;
        HotelProperty hotel = createProperty(managerId, "Royal Orchid", "Bangalore");
        RoomType room = createRoom(hotel.getId(), "Club Room", 4200.0, 12);

        LocalDate date = LocalDate.of(2026, 12, 15);
        priceOverrideRepository.save(RoomPriceOverride.builder()
                .hotelId(hotel.getId())
                .roomId(room.getId())
                .overrideDate(date)
                .pricePerNight(5500.0)
                .build());

        assertEquals(5500.0, pricingService.resolveEffectivePrice(room, date));

        String token = createManagerToken(managerId);
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override?date=" + date,
                HttpMethod.DELETE,
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("message").toString().contains("Price override removed"));

        // Verify price reverted to base price
        assertEquals(4200.0, pricingService.resolveEffectivePrice(room, date));
    }

    @Test
    @DisplayName("WP-07.2 Test 5: Reject non-positive price override")
    public void test5_rejectNonPositivePrice() {
        Long managerId = 9105L;
        HotelProperty hotel = createProperty(managerId, "City Inn", "Delhi");
        RoomType room = createRoom(hotel.getId(), "Standard", 3000.0, 8);

        String token = createManagerToken(managerId);

        // Zero price
        PriceOverrideRequestDto zeroReq = PriceOverrideRequestDto.builder()
                .overrideDate(LocalDate.of(2026, 12, 10))
                .pricePerNight(0.0)
                .build();
        ResponseEntity<Map> zeroResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override",
                HttpMethod.POST,
                new HttpEntity<>(zeroReq, authHeaders(token)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, zeroResp.getStatusCode());

        // Negative price
        PriceOverrideRequestDto negReq = PriceOverrideRequestDto.builder()
                .overrideDate(LocalDate.of(2026, 12, 10))
                .pricePerNight(-500.0)
                .build();
        ResponseEntity<Map> negResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override",
                HttpMethod.POST,
                new HttpEntity<>(negReq, authHeaders(token)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, negResp.getStatusCode());
    }

    @Test
    @DisplayName("WP-07.2 Test 6: Reject inverted date range (startDate > endDate)")
    public void test6_rejectInvertedDateRange() {
        Long managerId = 9106L;
        HotelProperty hotel = createProperty(managerId, "Valley Lodge", "Manali");
        RoomType room = createRoom(hotel.getId(), "Cottage", 3500.0, 5);

        String token = createManagerToken(managerId);

        PriceRangeOverrideRequestDto invertedReq = PriceRangeOverrideRequestDto.builder()
                .startDate(LocalDate.of(2026, 12, 20))
                .endDate(LocalDate.of(2026, 12, 10))
                .pricePerNight(4500.0)
                .build();

        ResponseEntity<Map> resp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override-range",
                HttpMethod.POST,
                new HttpEntity<>(invertedReq, authHeaders(token)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().get("message").toString().contains("Start date must not be after end date"));
    }

    @Test
    @DisplayName("WP-07.2 Test 7: Reject pricing actions on ARCHIVED room category")
    public void test7_rejectArchivedRoomCategory() {
        Long managerId = 9107L;
        HotelProperty hotel = createProperty(managerId, "Heritage Palace", "Jaipur");
        RoomType room = createRoom(hotel.getId(), "Maharaja Suite", 10000.0, 2);
        room.setStatus("ARCHIVED");
        roomTypeRepository.save(room);

        String token = createManagerToken(managerId);

        // 1. Get calendar on archived room
        ResponseEntity<Map> calResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/calendar?startDate=2026-12-01&endDate=2026-12-10",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, calResp.getStatusCode());
        assertTrue(calResp.getBody().get("message").toString().toLowerCase().contains("archived"));

        // 2. Set override on archived room
        PriceOverrideRequestDto overrideReq = PriceOverrideRequestDto.builder()
                .overrideDate(LocalDate.of(2026, 12, 05))
                .pricePerNight(12000.0)
                .build();
        ResponseEntity<Map> overResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override",
                HttpMethod.POST,
                new HttpEntity<>(overrideReq, authHeaders(token)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, overResp.getStatusCode());
        assertTrue(overResp.getBody().get("message").toString().toLowerCase().contains("archived"));

        // 3. Delete override on archived room
        ResponseEntity<Map> delResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override?date=2026-12-05",
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, delResp.getStatusCode());
        assertTrue(delResp.getBody().get("message").toString().toLowerCase().contains("archived"));
    }

    @Test
    @DisplayName("WP-07.2 Test 8: Cross-manager access control (403 Forbidden)")
    public void test8_crossManagerAccessForbidden() {
        Long manager1 = 9108L;
        Long manager2 = 9109L;

        HotelProperty hotel = createProperty(manager1, "Owner 1 Hotel", "Kolkata");
        RoomType room = createRoom(hotel.getId(), "Studio", 2500.0, 6);

        String token2 = createManagerToken(manager2);

        // Get calendar cross-manager
        ResponseEntity<Map> calResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/calendar?startDate=2026-12-01&endDate=2026-12-10",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token2)),
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, calResp.getStatusCode());

        // Set override cross-manager
        PriceOverrideRequestDto req = PriceOverrideRequestDto.builder()
                .overrideDate(LocalDate.of(2026, 12, 05))
                .pricePerNight(3000.0)
                .build();
        ResponseEntity<Map> overResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeaders(token2)),
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, overResp.getStatusCode());
    }

    @Test
    @DisplayName("WP-07.2 Test 9: Effective price calculation matches base vs override")
    public void test9_effectivePriceResolution() {
        Long managerId = 9110L;
        HotelProperty hotel = createProperty(managerId, "Pine Grove", "Ooty");
        RoomType room = createRoom(hotel.getId(), "Pine Villa", 5000.0, 4);

        LocalDate d1 = LocalDate.of(2026, 12, 1);
        LocalDate d2 = LocalDate.of(2026, 12, 2);

        priceOverrideRepository.save(RoomPriceOverride.builder()
                .hotelId(hotel.getId())
                .roomId(room.getId())
                .overrideDate(d1)
                .pricePerNight(6200.0)
                .build());

        assertEquals(6200.0, pricingService.resolveEffectivePrice(room, d1));
        assertEquals(5000.0, pricingService.resolveEffectivePrice(room, d2));

        Map<LocalDate, Double> map = pricingService.resolveEffectivePricesForRange(room, d1, d2);
        assertEquals(6200.0, map.get(d1));
        assertEquals(5000.0, map.get(d2));
    }

    @Test
    @DisplayName("WP-07.2 Test 10: Sample dataset override classification & KPI calculation")
    public void test10_sampleDataOverrideClassification() {
        Long managerId = 9111L;
        HotelProperty hotel = createProperty(managerId, "Grand Residency Hotel", "Bengaluru");
        RoomType room = createRoom(hotel.getId(), "Deluxe", 6499.0, 10);

        String token = createManagerToken(managerId);

        // Add overrides for Nov 22, Nov 23, Nov 25 at 7,499
        LocalDate nov22 = LocalDate.of(2026, 11, 22);
        LocalDate nov23 = LocalDate.of(2026, 11, 23);
        LocalDate nov25 = LocalDate.of(2026, 11, 25);

        for (LocalDate d : List.of(nov22, nov23, nov25)) {
            PriceOverrideRequestDto req = PriceOverrideRequestDto.builder()
                    .overrideDate(d)
                    .pricePerNight(7499.0)
                    .build();
            ResponseEntity<PriceCalendarEntryDto> overResp = restTemplate.exchange(
                    getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override",
                    HttpMethod.POST,
                    new HttpEntity<>(req, authHeaders(token)),
                    PriceCalendarEntryDto.class
            );
            assertEquals(HttpStatus.OK, overResp.getStatusCode());
            assertTrue(overResp.getBody().isOverride());
            assertEquals(7499.0, overResp.getBody().getOverridePrice());
            assertEquals(7499.0, overResp.getBody().getEffectivePrice());
            assertEquals(6499.0, overResp.getBody().getBasePrice());
        }

        // Query calendar for 5-day sample window Nov 21 to Nov 25
        LocalDate start = LocalDate.of(2026, 11, 21);
        LocalDate end = LocalDate.of(2026, 11, 25);

        // 1. Check raw JSON for explicit "isOverride": true/false
        ResponseEntity<String> rawResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/calendar?startDate=" + start + "&endDate=" + end,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                String.class
        );
        assertEquals(HttpStatus.OK, rawResp.getStatusCode());
        String json = rawResp.getBody();
        assertNotNull(json);
        assertTrue(json.contains("\"isOverride\":true"), "JSON must explicitly contain isOverride:true");
        assertTrue(json.contains("\"isOverride\":false"), "JSON must explicitly contain isOverride:false");

        // 2. Check typed DTO deserialization
        ResponseEntity<List<PriceCalendarEntryDto>> listResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/calendar?startDate=" + start + "&endDate=" + end,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<List<PriceCalendarEntryDto>>() {}
        );
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        List<PriceCalendarEntryDto> entries = listResp.getBody();
        assertNotNull(entries);
        assertEquals(5, entries.size());

        // Nov 21: Base
        PriceCalendarEntryDto d21 = entries.get(0);
        assertEquals(LocalDate.of(2026, 11, 21), d21.getDate());
        assertEquals(6499.0, d21.getBasePrice());
        assertNull(d21.getOverridePrice());
        assertEquals(6499.0, d21.getEffectivePrice());
        assertFalse(d21.isOverride());

        // Nov 22: Override
        PriceCalendarEntryDto d22 = entries.get(1);
        assertEquals(LocalDate.of(2026, 11, 22), d22.getDate());
        assertEquals(6499.0, d22.getBasePrice());
        assertEquals(7499.0, d22.getOverridePrice());
        assertEquals(7499.0, d22.getEffectivePrice());
        assertTrue(d22.isOverride());

        // Nov 23: Override
        PriceCalendarEntryDto d23 = entries.get(2);
        assertEquals(LocalDate.of(2026, 11, 23), d23.getDate());
        assertEquals(6499.0, d23.getBasePrice());
        assertEquals(7499.0, d23.getOverridePrice());
        assertEquals(7499.0, d23.getEffectivePrice());
        assertTrue(d23.isOverride());

        // Nov 24: Base
        PriceCalendarEntryDto d24 = entries.get(3);
        assertEquals(LocalDate.of(2026, 11, 24), d24.getDate());
        assertEquals(6499.0, d24.getBasePrice());
        assertNull(d24.getOverridePrice());
        assertEquals(6499.0, d24.getEffectivePrice());
        assertFalse(d24.isOverride());

        // Nov 25: Override
        PriceCalendarEntryDto d25 = entries.get(4);
        assertEquals(LocalDate.of(2026, 11, 25), d25.getDate());
        assertEquals(6499.0, d25.getBasePrice());
        assertEquals(7499.0, d25.getOverridePrice());
        assertEquals(7499.0, d25.getEffectivePrice());
        assertTrue(d25.isOverride());

        // 3. Verify KPI calculations
        long totalNights = entries.size();
        long overrideNights = entries.stream().filter(PriceCalendarEntryDto::isOverride).count();
        long basePriceNights = entries.stream().filter(e -> !e.isOverride()).count();

        assertEquals(5, totalNights);
        assertEquals(3, overrideNights, "Override nights should be exactly 3");
        assertEquals(2, basePriceNights, "Base price nights should be exactly 2");
        assertEquals(totalNights, overrideNights + basePriceNights);
    }

    @Test
    @DisplayName("WP-07.2 Test 11: Override price equal to base price preserves override classification")
    public void test11_overridePriceEqualsBasePrice() {
        Long managerId = 9112L;
        HotelProperty hotel = createProperty(managerId, "Hilltop Grand", "Manali");
        RoomType room = createRoom(hotel.getId(), "Standard Suite", 6499.0, 5);

        String token = createManagerToken(managerId);
        LocalDate testDate = LocalDate.of(2026, 12, 10);

        // Explicitly set override with price equal to base price
        PriceOverrideRequestDto req = PriceOverrideRequestDto.builder()
                .overrideDate(testDate)
                .pricePerNight(6499.0)
                .build();

        ResponseEntity<PriceCalendarEntryDto> overResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override",
                HttpMethod.POST,
                new HttpEntity<>(req, authHeaders(token)),
                PriceCalendarEntryDto.class
        );
        assertEquals(HttpStatus.OK, overResp.getStatusCode());
        assertTrue(overResp.getBody().isOverride());
        assertEquals(6499.0, overResp.getBody().getOverridePrice());
        assertEquals(6499.0, overResp.getBody().getEffectivePrice());
        assertEquals(6499.0, overResp.getBody().getBasePrice());

        // Query single-day calendar
        ResponseEntity<List<PriceCalendarEntryDto>> calResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/calendar?startDate=" + testDate + "&endDate=" + testDate,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<List<PriceCalendarEntryDto>>() {}
        );
        assertEquals(HttpStatus.OK, calResp.getStatusCode());
        assertNotNull(calResp.getBody());
        assertEquals(1, calResp.getBody().size());

        PriceCalendarEntryDto entry = calResp.getBody().get(0);
        assertTrue(entry.isOverride(), "Explicit override must be classified as isOverride=true even if price equals base price");
        assertEquals(6499.0, entry.getOverridePrice());
        assertEquals(6499.0, entry.getBasePrice());
        assertEquals(6499.0, entry.getEffectivePrice());
    }
}
