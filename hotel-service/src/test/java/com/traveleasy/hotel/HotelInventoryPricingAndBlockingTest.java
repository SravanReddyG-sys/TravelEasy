package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.*;
import com.traveleasy.hotel.model.*;
import com.traveleasy.hotel.repository.*;
import com.traveleasy.hotel.service.HotelInventoryService;
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
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HotelInventoryPricingAndBlockingTest {

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
    private RoomPriceOverrideRepository priceOverrideRepository;

    @Autowired
    private RoomInventoryBlockRepository blockRepository;

    @Autowired
    private HotelBookingAllocationRepository allocationRepository;

    @Autowired
    private HotelInventoryService inventoryService;

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
                .name(name + " " + System.currentTimeMillis())
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
    // SECTION A: DATE-WISE INVENTORY (BD-01 & BD-06)
    // =========================================================================

    @Test
    @DisplayName("Inv 1.1: Automatic inventory initialization derives daily capacity from room type (BD-06)")
    public void testInventoryInitialization_BD06() {
        HotelProperty hotel = createProperty(2001L, "Ocean Palms Resort", "Goa");
        RoomType room = createRoom(hotel.getId(), "Deluxe Ocean View", 3500.0, 10);

        LocalDate targetDate = LocalDate.now().plusDays(5);
        RoomDailyInventory daily = inventoryService.getOrCreateDailyInventory(room, targetDate);

        assertNotNull(daily.getId(), "Daily inventory must be persisted");
        assertEquals(10, daily.getTotalRooms(), "Total rooms must be derived from room type");
        assertEquals(0, daily.getBookedRooms(), "Initial booked rooms must be 0");
        assertEquals(0, daily.getHeldRooms(), "Initial held rooms must be 0");
        assertEquals(0, daily.getBlockedRooms(), "Initial blocked rooms must be 0");
        assertEquals(10, daily.getAvailableRooms(), "Available rooms must equal total capacity");
    }

    @Test
    @DisplayName("Inv 1.2: Multi-night availability calculation follows check-in inclusive, checkout exclusive semantics (BD-01)")
    public void testMultiNightAvailability_CheckInInclusive_CheckOutExclusive() {
        HotelProperty hotel = createProperty(2002L, "Mountain View Retreat", "Manali");
        RoomType room = createRoom(hotel.getId(), "Pine Suite", 4000.0, 5);

        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = checkIn.plusDays(3); // 3 nights: checkIn, checkIn+1, checkIn+2

        StayAvailabilityResponseDto res = inventoryService.checkStayAvailability(room.getId(), checkIn, checkOut, 2);

        assertTrue(res.isAvailable(), "Stay must be available");
        assertEquals(3, res.getTotalNights(), "Stay must be exactly 3 nights");
        assertEquals(5, res.getAvailableRoomsAcrossStay(), "Minimum available across stay must be 5");
        assertEquals(24000.0, res.getTotalPrice(), "Total price must be 3 nights * 4000 * 2 rooms = 24000");
        assertEquals(3, res.getNightlyBreakdown().size(), "Nightly breakdown must contain 3 dates");
        assertEquals(checkIn, res.getNightlyBreakdown().get(0).getDate());
        assertEquals(checkIn.plusDays(2), res.getNightlyBreakdown().get(2).getDate());
    }

    @Test
    @DisplayName("Inv 1.3: Insufficient inventory on a single night invalidates multi-night availability")
    public void testInsufficientInventory_SingleNightFailsWholeStay() {
        HotelProperty hotel = createProperty(2003L, "City Center Hotel", "Bengaluru");
        RoomType room = createRoom(hotel.getId(), "Executive King", 5000.0, 4);

        LocalDate checkIn = LocalDate.now().plusDays(20);
        LocalDate checkOut = checkIn.plusDays(3);

        // Consume all inventory on night 2 (checkIn + 1)
        RoomDailyInventory night2 = inventoryService.getOrCreateDailyInventory(room, checkIn.plusDays(1));
        night2.setBookedRooms(4);
        dailyInventoryRepository.save(night2);

        StayAvailabilityResponseDto res = inventoryService.checkStayAvailability(room.getId(), checkIn, checkOut, 1);

        assertFalse(res.isAvailable(), "Stay must NOT be available because night 2 has 0 inventory");
        assertEquals(0, res.getAvailableRoomsAcrossStay(), "Min available across stay must be 0");
    }

    @Test
    @DisplayName("Inv 1.4: Multi-night atomic stay reservation successfully allocates all nights")
    public void testAtomicMultiNightStayReservation_Success() {
        HotelProperty hotel = createProperty(2004L, "Lakeside Haven", "Udaipur");
        RoomType room = createRoom(hotel.getId(), "Heritage Room", 6000.0, 8);

        String token = createManagerToken(2004L);
        LocalDate checkIn = LocalDate.now().plusDays(15);
        LocalDate checkOut = checkIn.plusDays(2);
        String bookingRef = "BKG-STAY-" + UUID.randomUUID().toString().substring(0, 8);

        DateWiseReserveRequestDto req = DateWiseReserveRequestDto.builder()
                .bookingRef(bookingRef)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .count(3)
                .build();

        HttpEntity<DateWiseReserveRequestDto> entity = new HttpEntity<>(req, authHeaders(token));
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                getBaseUrl() + "/rooms/" + room.getId() + "/reserve-stay", entity, Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(bookingRef, resp.getBody().get("bookingRef"));
        assertEquals("HELD", resp.getBody().get("status"));
        assertEquals(36000.0, ((Number) resp.getBody().get("totalPrice")).doubleValue(), "2 nights * 6000 * 3 rooms = 36000");

        // Verify daily inventory held rooms incremented for both nights
        RoomDailyInventory night1 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), checkIn).orElseThrow();
        RoomDailyInventory night2 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), checkIn.plusDays(1)).orElseThrow();

        assertEquals(3, night1.getHeldRooms(), "Night 1 must hold 3 rooms");
        assertEquals(5, night1.getAvailableRooms(), "Night 1 available must be 8 - 3 = 5");
        assertEquals(3, night2.getHeldRooms(), "Night 2 must hold 3 rooms");
        assertEquals(5, night2.getAvailableRooms(), "Night 2 available must be 8 - 3 = 5");
    }

    @Test
    @DisplayName("Inv 1.5: Concurrent reservation attempts prevent overbooking under high contention")
    public void testConcurrentReservationAttempts_NoOverbooking() throws Exception {
        HotelProperty hotel = createProperty(2005L, "Summit Palace", "Shimla");
        RoomType room = createRoom(hotel.getId(), "Valley View Deluxe", 4500.0, 5); // Total 5 rooms

        LocalDate checkIn = LocalDate.now().plusDays(30);
        LocalDate checkOut = checkIn.plusDays(2);

        // Pre-initialize daily records so threads contend strictly on pessimistic lock allocation
        inventoryService.getOrCreateDailyInventory(room, checkIn);
        inventoryService.getOrCreateDailyInventory(room, checkIn.plusDays(1));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String bRef = "BKG-RACE-" + i + "-" + UUID.randomUUID().toString().substring(0, 6);
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    DateWiseReserveRequestDto dto = DateWiseReserveRequestDto.builder()
                            .bookingRef(bRef)
                            .checkInDate(checkIn)
                            .checkOutDate(checkOut)
                            .count(1)
                            .build();
                    inventoryService.reserveStayInventory(room.getId(), dto, null, true);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        int successes = 0;
        int failures = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) successes++;
            else failures++;
        }

        assertEquals(5, successes, "Exactly 5 reservations must succeed for capacity of 5");
        assertEquals(5, failures, "Remaining 5 concurrent attempts must be rejected");

        RoomDailyInventory night1 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), checkIn).orElseThrow();
        assertEquals(5, night1.getHeldRooms(), "Held rooms must be exactly 5");
        assertEquals(0, night1.getAvailableRooms(), "Available rooms must be 0");
    }

    @Test
    @DisplayName("Inv 1.6: Inventory reduction rejected when violating committed allocations (BD-07)")
    public void testCapacityReduction_BlockedByCommittedAllocations_BD07() {
        HotelProperty hotel = createProperty(2006L, "Grand Heritage", "Jaipur");
        RoomType room = createRoom(hotel.getId(), "Royal Suite", 8000.0, 10);

        String token = createManagerToken(2006L);
        LocalDate checkIn = LocalDate.now().plusDays(40);
        LocalDate checkOut = checkIn.plusDays(2);

        // Reserve 4 rooms
        DateWiseReserveRequestDto reserveReq = DateWiseReserveRequestDto.builder()
                .bookingRef("BKG-REDUCE-" + UUID.randomUUID())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .count(4)
                .build();
        inventoryService.reserveStayInventory(room.getId(), reserveReq, null, true);

        // Attempt to reduce totalRooms from 10 to 3 (which is < 4 committed)
        RoomTypeRequestDto updateReq = RoomTypeRequestDto.builder()
                .roomTypeName(room.getRoomTypeName())
                .pricePerNight(8000.0)
                .totalRooms(3) // Less than 4 reserved!
                .maxOccupancy(2)
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(updateReq, authHeaders(token));
        ResponseEntity<Map> resp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(), HttpMethod.PUT, entity, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().get("message").toString().contains("BD-07")
                || resp.getBody().get("message").toString().contains("allocated")
                || resp.getBody().get("message").toString().contains("reserved"));
    }

    @Test
    @DisplayName("Inv 1.7: Inventory reduction succeeds when no allocations violate the new limit")
    public void testCapacityReduction_AllowedWhenNoConflictingAllocations() {
        HotelProperty hotel = createProperty(2007L, "Seaside Cottages", "Goa");
        RoomType room = createRoom(hotel.getId(), "Beach Cottage", 5000.0, 10);

        String token = createManagerToken(2007L);

        // Reduce totalRooms from 10 to 7 (no allocations exist)
        RoomTypeRequestDto updateReq = RoomTypeRequestDto.builder()
                .roomTypeName(room.getRoomTypeName())
                .pricePerNight(5000.0)
                .totalRooms(7)
                .maxOccupancy(2)
                .build();

        HttpEntity<RoomTypeRequestDto> entity = new HttpEntity<>(updateReq, authHeaders(token));
        ResponseEntity<RoomType> resp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId(), HttpMethod.PUT, entity, RoomType.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(7, resp.getBody().getTotalRooms());
        assertEquals(7, resp.getBody().getAvailableRooms());
    }

    // =========================================================================
    // SECTION B: DATE-SPECIFIC PRICING
    // =========================================================================

    @Test
    @DisplayName("Price 2.1: Default room-type price resolved when no date override exists")
    public void testDefaultPriceResolution() {
        HotelProperty hotel = createProperty(2101L, "Riverfront Resort", "Rishikesh");
        RoomType room = createRoom(hotel.getId(), "Standard Garden", 2500.0, 10);

        LocalDate date = LocalDate.now().plusDays(10);
        double effective = pricingService.resolveEffectivePrice(room, date);
        assertEquals(2500.0, effective, "Must resolve base room price");
        assertFalse(pricingService.isOverridePresent(room.getId(), date));
    }

    @Test
    @DisplayName("Price 2.2: Single date price override takes precedence over base price")
    public void testSingleDatePriceOverride() {
        HotelProperty hotel = createProperty(2102L, "Hilltop Manor", "Ooty");
        RoomType room = createRoom(hotel.getId(), "Manor Suite", 3000.0, 5);

        String token = createManagerToken(2102L);
        LocalDate targetDate = LocalDate.now().plusDays(12);

        PriceOverrideRequestDto req = PriceOverrideRequestDto.builder()
                .overrideDate(targetDate)
                .pricePerNight(4500.0)
                .build();

        HttpEntity<PriceOverrideRequestDto> entity = new HttpEntity<>(req, authHeaders(token));
        ResponseEntity<PriceCalendarEntryDto> resp = restTemplate.postForEntity(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override", entity, PriceCalendarEntryDto.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(4500.0, resp.getBody().getEffectivePrice());
        assertEquals(3000.0, resp.getBody().getBasePrice());
        assertTrue(resp.getBody().isOverride());

        double effective = pricingService.resolveEffectivePrice(room, targetDate);
        assertEquals(4500.0, effective, "PricingService must resolve override price");
    }

    @Test
    @DisplayName("Price 2.3: Range price override sets overrides across all dates in range")
    public void testRangePriceOverride() {
        HotelProperty hotel = createProperty(2103L, "Coastal Sands", "Mangalore");
        RoomType room = createRoom(hotel.getId(), "Sea Facing", 3500.0, 5);

        String token = createManagerToken(2103L);
        LocalDate start = LocalDate.now().plusDays(20);
        LocalDate end = start.plusDays(4); // 5 days

        PriceRangeOverrideRequestDto req = PriceRangeOverrideRequestDto.builder()
                .startDate(start)
                .endDate(end)
                .pricePerNight(5500.0)
                .build();

        HttpEntity<PriceRangeOverrideRequestDto> entity = new HttpEntity<>(req, authHeaders(token));
        ResponseEntity<List> resp = restTemplate.postForEntity(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override-range", entity, List.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(5, resp.getBody().size(), "Must create 5 override entries");

        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            assertEquals(5500.0, pricingService.resolveEffectivePrice(room, cur));
            cur = cur.plusDays(1);
        }
    }

    @Test
    @DisplayName("Price 2.4: Override deletion restores fallback to base price")
    public void testPriceOverrideDelete_RevertsToBasePrice() {
        HotelProperty hotel = createProperty(2104L, "Emerald Bay", "Kochi");
        RoomType room = createRoom(hotel.getId(), "Club Room", 4000.0, 5);

        String token = createManagerToken(2104L);
        LocalDate date = LocalDate.now().plusDays(15);

        // Set override
        pricingService.setPriceOverride(room.getId(),
                PriceOverrideRequestDto.builder().overrideDate(date).pricePerNight(6000.0).build(),
                null);
        assertEquals(6000.0, pricingService.resolveEffectivePrice(room, date));

        // Delete override via endpoint
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Map> delResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override?date=" + date,
                HttpMethod.DELETE, entity, Map.class);

        assertEquals(HttpStatus.OK, delResp.getStatusCode());
        assertEquals(4000.0, ((Number) delResp.getBody().get("effectivePrice")).doubleValue());

        double afterDelete = pricingService.resolveEffectivePrice(room, date);
        assertEquals(4000.0, afterDelete, "Effective price must revert to base price");
    }

    @Test
    @DisplayName("Price 2.5: Multi-night pricing calculates exact sum with mixed overrides")
    public void testMultiNightPricingCalculation_WithMixedOverrides() {
        HotelProperty hotel = createProperty(2105L, "Desert Oasis", "Jaisalmer");
        RoomType room = createRoom(hotel.getId(), "Desert Tent", 2000.0, 5);

        LocalDate checkIn = LocalDate.now().plusDays(25);
        LocalDate checkOut = checkIn.plusDays(3); // 3 nights: night 1 = 2000, night 2 = 3500 (override), night 3 = 2000

        // Set override for night 2
        pricingService.setPriceOverride(room.getId(),
                PriceOverrideRequestDto.builder().overrideDate(checkIn.plusDays(1)).pricePerNight(3500.0).build(),
                null);

        StayAvailabilityResponseDto stay = inventoryService.checkStayAvailability(room.getId(), checkIn, checkOut, 2);

        // Total price for 2 rooms = (2000 + 3500 + 2000) * 2 = 7500 * 2 = 15000
        assertEquals(15000.0, stay.getTotalPrice());
        assertEquals(2000.0, stay.getMinNightlyPrice());
        assertEquals(3500.0, stay.getMaxNightlyPrice());
        assertEquals(3, stay.getNightlyBreakdown().size());
        assertFalse(stay.getNightlyBreakdown().get(0).isOverride());
        assertTrue(stay.getNightlyBreakdown().get(1).isOverride());
        assertFalse(stay.getNightlyBreakdown().get(2).isOverride());
    }

    @Test
    @DisplayName("Price 2.6: Non-positive price overrides are strictly rejected")
    public void testInvalidPriceOverride_NegativeOrZero_Rejected() {
        HotelProperty hotel = createProperty(2106L, "Park View", "Chennai");
        RoomType room = createRoom(hotel.getId(), "Single Room", 1500.0, 5);

        String token = createManagerToken(2106L);
        LocalDate date = LocalDate.now().plusDays(5);

        // Zero price
        PriceOverrideRequestDto reqZero = PriceOverrideRequestDto.builder().overrideDate(date).pricePerNight(0.0).build();
        ResponseEntity<Map> respZero = restTemplate.postForEntity(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override",
                new HttpEntity<>(reqZero, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, respZero.getStatusCode());

        // Negative price
        PriceOverrideRequestDto reqNeg = PriceOverrideRequestDto.builder().overrideDate(date).pricePerNight(-500.0).build();
        ResponseEntity<Map> respNeg = restTemplate.postForEntity(
                getBaseUrl() + "/rooms/" + room.getId() + "/pricing/override",
                new HttpEntity<>(reqNeg, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, respNeg.getStatusCode());
    }

    @Test
    @DisplayName("Price 2.7: Historical booking prices remain immutable when price override changes (BD-07)")
    public void testHistoricalBookingPricePreservation_AfterOverrideChange() {
        HotelProperty hotel = createProperty(2107L, "Fort View Heritage", "Jodhpur");
        RoomType room = createRoom(hotel.getId(), "Heritage Deluxe", 5000.0, 5);

        LocalDate checkIn = LocalDate.now().plusDays(35);
        LocalDate checkOut = checkIn.plusDays(2);
        String bookingRef = "BKG-HIST-" + UUID.randomUUID().toString().substring(0, 8);

        // Reserve at base price (2 nights * 5000 * 1 room = 10000)
        DateWiseReserveRequestDto req = DateWiseReserveRequestDto.builder()
                .bookingRef(bookingRef)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .count(1)
                .build();
        Map<String, Object> allocResult = inventoryService.reserveStayInventory(room.getId(), req, null, true);
        assertEquals(10000.0, ((Number) allocResult.get("totalPrice")).doubleValue());

        // Now manager increases price override to 9000 for checkIn date
        pricingService.setPriceOverride(room.getId(),
                PriceOverrideRequestDto.builder().overrideDate(checkIn).pricePerNight(9000.0).build(),
                null);

        // The existing allocation's stored price must remain 10000.0 (BD-07)
        HotelBookingAllocation savedAlloc = allocationRepository.findByBookingRef(bookingRef).orElseThrow();
        assertEquals(10000.0, savedAlloc.getTotalPrice(), "Committed booking price must remain immutable");
    }

    // =========================================================================
    // SECTION C: OPERATIONAL ROOM BLOCKING (BD-10)
    // =========================================================================

    @Test
    @DisplayName("Block 3.1: Valid room block creation decrements available inventory across stay (BD-10)")
    public void testOperationalBlock_CreationSuccess_BD10() {
        HotelProperty hotel = createProperty(2201L, "Highland Resort", "Munnar");
        RoomType room = createRoom(hotel.getId(), "Tea Garden View", 4000.0, 8);

        String token = createManagerToken(2201L);
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(3); // 3 nights

        RoomBlockRequestDto blockReq = RoomBlockRequestDto.builder()
                .blockedCount(2)
                .startDate(start)
                .endDate(end)
                .reason("MAINTENANCE")
                .notes("Air conditioning overhaul")
                .build();

        HttpEntity<RoomBlockRequestDto> entity = new HttpEntity<>(blockReq, authHeaders(token));
        ResponseEntity<RoomBlockResponseDto> resp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                entity, RoomBlockResponseDto.class);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertNotNull(resp.getBody().getId());
        assertEquals("ACTIVE", resp.getBody().getStatus());
        assertEquals(2, resp.getBody().getBlockedCount());
        assertEquals("MAINTENANCE", resp.getBody().getReason());

        // Check daily inventory: blockedRooms = 2, available = 6 for each of the 3 nights
        LocalDate cur = start;
        while (cur.isBefore(end)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), cur).orElseThrow();
            assertEquals(2, daily.getBlockedRooms());
            assertEquals(6, daily.getAvailableRooms());
            cur = cur.plusDays(1);
        }
    }

    @Test
    @DisplayName("Block 3.2: Mandatory reason validation rejects empty or invalid reason (BD-10)")
    public void testOperationalBlock_MandatoryReasonValidation() {
        HotelProperty hotel = createProperty(2202L, "Pine Woods", "Kodaikanal");
        RoomType room = createRoom(hotel.getId(), "Cottage Room", 3000.0, 5);

        String token = createManagerToken(2202L);
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = start.plusDays(2);

        // Missing reason
        RoomBlockRequestDto emptyReason = RoomBlockRequestDto.builder()
                .blockedCount(1).startDate(start).endDate(end).reason("").build();
        ResponseEntity<Map> respEmpty = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(emptyReason, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, respEmpty.getStatusCode());

        // Invalid reason
        RoomBlockRequestDto invalidReason = RoomBlockRequestDto.builder()
                .blockedCount(1).startDate(start).endDate(end).reason("VACATION").build();
        ResponseEntity<Map> respInvalid = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(invalidReason, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, respInvalid.getStatusCode());
    }

    @Test
    @DisplayName("Block 3.3: Invalid date range or quantity rejected")
    public void testOperationalBlock_InvalidDatesOrQuantity_Rejected() {
        HotelProperty hotel = createProperty(2203L, "Sun & Sand", "Puri");
        RoomType room = createRoom(hotel.getId(), "Deluxe Beach", 3500.0, 5);

        String token = createManagerToken(2203L);

        // End date before start date
        RoomBlockRequestDto endBeforeStart = RoomBlockRequestDto.builder()
                .blockedCount(1)
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(8))
                .reason("MAINTENANCE")
                .build();
        ResponseEntity<Map> respDates = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(endBeforeStart, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, respDates.getStatusCode());

        // Non-positive quantity
        RoomBlockRequestDto zeroCount = RoomBlockRequestDto.builder()
                .blockedCount(0)
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(7))
                .reason("OPERATIONAL")
                .build();
        ResponseEntity<Map> respZero = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(zeroCount, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, respZero.getStatusCode());
    }

    @Test
    @DisplayName("Block 3.4: Block rejected when exceeding available inventory")
    public void testOperationalBlock_ExceedsAvailability_Rejected() {
        HotelProperty hotel = createProperty(2204L, "Grand Palace", "Mysuru");
        RoomType room = createRoom(hotel.getId(), "Palace View", 5000.0, 4);

        String token = createManagerToken(2204L);
        LocalDate start = LocalDate.now().plusDays(15);
        LocalDate end = start.plusDays(2);

        // Attempt to block 5 rooms when capacity is only 4
        RoomBlockRequestDto blockReq = RoomBlockRequestDto.builder()
                .blockedCount(5)
                .startDate(start)
                .endDate(end)
                .reason("RENOVATION")
                .build();

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(blockReq, authHeaders(token)), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().get("message").toString().contains("Insufficient available inventory"));
    }

    @Test
    @DisplayName("Block 3.5: Block release restores inventory and preserves historical record (BD-10)")
    public void testOperationalBlock_ReleaseRestoresInventory() {
        HotelProperty hotel = createProperty(2205L, "Valley Retreat", "Coorg");
        RoomType room = createRoom(hotel.getId(), "Estate Cottage", 4200.0, 6);

        String token = createManagerToken(2205L);
        LocalDate start = LocalDate.now().plusDays(12);
        LocalDate end = start.plusDays(2);

        // Create block of 3 rooms
        RoomBlockRequestDto blockReq = RoomBlockRequestDto.builder()
                .blockedCount(3).startDate(start).endDate(end).reason("OTHER").notes("Deep plumbing inspection").build();
        ResponseEntity<RoomBlockResponseDto> createResp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(blockReq, authHeaders(token)), RoomBlockResponseDto.class);
        Long blockId = createResp.getBody().getId();

        // Release block
        ResponseEntity<RoomBlockResponseDto> relResp = restTemplate.exchange(
                getBaseUrl() + "/blocks/" + blockId + "/release",
                HttpMethod.PUT, new HttpEntity<>(authHeaders(token)), RoomBlockResponseDto.class);

        assertEquals(HttpStatus.OK, relResp.getStatusCode());
        assertEquals("RELEASED", relResp.getBody().getStatus());
        assertNotNull(relResp.getBody().getReleasedAt());

        // Verify inventory restored to 6 available
        LocalDate cur = start;
        while (cur.isBefore(end)) {
            RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), cur).orElseThrow();
            assertEquals(0, daily.getBlockedRooms());
            assertEquals(6, daily.getAvailableRooms());
            cur = cur.plusDays(1);
        }

        // Verify historical record preserved in database
        RoomInventoryBlock saved = blockRepository.findById(blockId).orElseThrow();
        assertEquals("RELEASED", saved.getStatus());
    }

    @Test
    @DisplayName("Block 3.6: Active operational block prevents subsequent booking conflict")
    public void testOperationalBlock_PreventsBookingConflict() {
        HotelProperty hotel = createProperty(2206L, "Lighthouse Suites", "Kovalam");
        RoomType room = createRoom(hotel.getId(), "Ocean Panorama", 5500.0, 3); // 3 rooms total

        String token = createManagerToken(2206L);
        LocalDate start = LocalDate.now().plusDays(20);
        LocalDate end = start.plusDays(3);

        // Block 2 rooms
        RoomBlockRequestDto blockReq = RoomBlockRequestDto.builder()
                .blockedCount(2).startDate(start).endDate(end).reason("MAINTENANCE").build();
        restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel.getId() + "/rooms/" + room.getId() + "/blocks",
                new HttpEntity<>(blockReq, authHeaders(token)), RoomBlockResponseDto.class);

        // Now attempt to reserve 2 rooms (only 1 room available!)
        DateWiseReserveRequestDto reserveReq = DateWiseReserveRequestDto.builder()
                .bookingRef("BKG-CONFLICT-" + UUID.randomUUID())
                .checkInDate(start)
                .checkOutDate(end)
                .count(2)
                .build();

        ResponseEntity<Map> reserveResp = restTemplate.postForEntity(
                getBaseUrl() + "/rooms/" + room.getId() + "/reserve-stay",
                new HttpEntity<>(reserveReq, authHeaders(token)), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, reserveResp.getStatusCode());
        assertTrue(reserveResp.getBody().get("message").toString().contains("Insufficient room inventory"));
    }

    // =========================================================================
    // SECTION D: BOOKING INTEGRATION, LIFECYCLE & SECURITY
    // =========================================================================

    @Test
    @DisplayName("Book 4.1: Idempotent stay reservation with same bookingRef returns existing allocation")
    public void testIdempotentReservation_SameBookingRef() {
        HotelProperty hotel = createProperty(2301L, "Tea Valley Inn", "Darjeeling");
        RoomType room = createRoom(hotel.getId(), "Kanchenjunga Suite", 6500.0, 5);

        String token = createManagerToken(2301L);
        LocalDate checkIn = LocalDate.now().plusDays(18);
        LocalDate checkOut = checkIn.plusDays(2);
        String bookingRef = "BKG-IDEMP-" + UUID.randomUUID().toString().substring(0, 8);

        DateWiseReserveRequestDto req = DateWiseReserveRequestDto.builder()
                .bookingRef(bookingRef)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .count(2)
                .build();

        // Call 1
        ResponseEntity<Map> resp1 = restTemplate.postForEntity(
                getBaseUrl() + "/rooms/" + room.getId() + "/reserve-stay",
                new HttpEntity<>(req, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.OK, resp1.getStatusCode());

        // Call 2 with identical bookingRef
        ResponseEntity<Map> resp2 = restTemplate.postForEntity(
                getBaseUrl() + "/rooms/" + room.getId() + "/reserve-stay",
                new HttpEntity<>(req, authHeaders(token)), Map.class);
        assertEquals(HttpStatus.OK, resp2.getStatusCode());
        assertEquals(bookingRef, resp2.getBody().get("bookingRef"));

        // Only 2 rooms held, NOT 4!
        RoomDailyInventory night1 = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), checkIn).orElseThrow();
        assertEquals(2, night1.getHeldRooms(), "Must not double allocate on duplicate request");
    }

    @Test
    @DisplayName("Book 4.2: Idempotent stay release by bookingRef safely handles multiple calls")
    public void testIdempotentStayRelease_ByBookingRef() {
        HotelProperty hotel = createProperty(2302L, "Snow Crest", "Gulmarg");
        RoomType room = createRoom(hotel.getId(), "Snow Suite", 7000.0, 5);

        String internalToken = createInternalServiceToken();
        LocalDate checkIn = LocalDate.now().plusDays(22);
        LocalDate checkOut = checkIn.plusDays(2);
        String bookingRef = "BKG-REL-" + UUID.randomUUID().toString().substring(0, 8);

        // Hold 2 rooms
        DateWiseReserveRequestDto reserveReq = DateWiseReserveRequestDto.builder()
                .bookingRef(bookingRef).checkInDate(checkIn).checkOutDate(checkOut).count(2).build();
        inventoryService.reserveStayInventory(room.getId(), reserveReq, null, true);

        HttpHeaders internalHeaders = new HttpHeaders();
        internalHeaders.setContentType(MediaType.APPLICATION_JSON);
        internalHeaders.set("X-Internal-Token", internalToken);
        internalHeaders.set("X-Internal-Service", "booking-service");

        // Release call 1
        ResponseEntity<Map> rel1 = restTemplate.postForEntity(
                getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef,
                new HttpEntity<>(internalHeaders), Map.class);
        assertEquals(HttpStatus.OK, rel1.getStatusCode());
        assertEquals("RELEASED", rel1.getBody().get("status"));

        // Release call 2 (idempotent duplicate)
        ResponseEntity<Map> rel2 = restTemplate.postForEntity(
                getBaseUrl() + "/rooms/release-stay?bookingRef=" + bookingRef,
                new HttpEntity<>(internalHeaders), Map.class);
        assertEquals(HttpStatus.OK, rel2.getStatusCode());
        assertEquals("RELEASED", rel2.getBody().get("status"));

        // Inventory restored correctly, not over-restored!
        RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), checkIn).orElseThrow();
        assertEquals(0, daily.getHeldRooms());
        assertEquals(5, daily.getAvailableRooms());
    }

    @Test
    @DisplayName("Book 4.3: Confirmation transitions allocation from HELD to CONFIRMED and shifts daily inventory")
    public void testConfirmStay_TransitionsHeldToConfirmed() {
        HotelProperty hotel = createProperty(2303L, "Pine View", "Nainital");
        RoomType room = createRoom(hotel.getId(), "Lake Deluxe", 3800.0, 6);

        String internalToken = createInternalServiceToken();
        LocalDate checkIn = LocalDate.now().plusDays(25);
        LocalDate checkOut = checkIn.plusDays(2);
        String bookingRef = "BKG-CONF-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Hold
        inventoryService.reserveStayInventory(room.getId(),
                DateWiseReserveRequestDto.builder().bookingRef(bookingRef).checkInDate(checkIn).checkOutDate(checkOut).count(2).build(),
                null, true);

        // 2. Confirm
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        headers.set("X-Internal-Service", "booking-service");

        ResponseEntity<Map> confResp = restTemplate.exchange(
                getBaseUrl() + "/rooms/confirm-stay?bookingRef=" + bookingRef,
                HttpMethod.PUT, new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.OK, confResp.getStatusCode());
        assertEquals("CONFIRMED", confResp.getBody().get("status"));

        // Verify daily inventory shifted from held to booked
        RoomDailyInventory daily = dailyInventoryRepository.findByRoomIdAndInventoryDate(room.getId(), checkIn).orElseThrow();
        assertEquals(0, daily.getHeldRooms(), "Held rooms must be 0 after confirmation");
        assertEquals(2, daily.getBookedRooms(), "Booked rooms must be 2 after confirmation");
        assertEquals(4, daily.getAvailableRooms(), "Available must remain 6 - 2 = 4");
    }

    @Test
    @DisplayName("Sec 4.4: Cross-manager access rejected for price overrides and operational blocks (403)")
    public void testManagerOwnershipEnforcement_PriceOverrideAndBlocks() {
        HotelProperty hotel1 = createProperty(2401L, "Manager One Hotel", "Goa");
        RoomType room1 = createRoom(hotel1.getId(), "Room One", 3000.0, 5);

        String manager2Token = createManagerToken(2402L); // Different manager!

        // Attempt price override on Hotel 1 by Manager 2 -> 403 Forbidden
        PriceOverrideRequestDto priceDto = PriceOverrideRequestDto.builder()
                .overrideDate(LocalDate.now().plusDays(5))
                .pricePerNight(9999.0)
                .build();
        ResponseEntity<Map> priceResp = restTemplate.postForEntity(
                getBaseUrl() + "/rooms/" + room1.getId() + "/pricing/override",
                new HttpEntity<>(priceDto, authHeaders(manager2Token)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, priceResp.getStatusCode());

        // Attempt room block on Hotel 1 by Manager 2 -> 403 Forbidden
        RoomBlockRequestDto blockDto = RoomBlockRequestDto.builder()
                .blockedCount(1)
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(7))
                .reason("MAINTENANCE")
                .build();
        ResponseEntity<Map> blockResp = restTemplate.postForEntity(
                getBaseUrl() + "/" + hotel1.getId() + "/rooms/" + room1.getId() + "/blocks",
                new HttpEntity<>(blockDto, authHeaders(manager2Token)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, blockResp.getStatusCode());
    }

    @Test
    @DisplayName("Pub 4.5: Public hotel search filters properties by stay date availability (BD-01)")
    public void testPublicSearch_FilterByStayDatesAndAvailability() {
        String uniqueCity = "AvailCity" + System.currentTimeMillis();
        HotelProperty hotelFull = createProperty(2501L, "Fully Booked Inn", uniqueCity);
        RoomType roomFull = createRoom(hotelFull.getId(), "Only Room", 2000.0, 2);

        HotelProperty hotelAvail = createProperty(2502L, "Available Suites", uniqueCity);
        RoomType roomAvail = createRoom(hotelAvail.getId(), "Spacious Suite", 4500.0, 5);

        LocalDate checkIn = LocalDate.now().plusDays(14);
        LocalDate checkOut = checkIn.plusDays(2);

        // Fully book hotelFull for checkIn date
        RoomDailyInventory fullDaily = inventoryService.getOrCreateDailyInventory(roomFull, checkIn);
        fullDaily.setBookedRooms(2);
        dailyInventoryRepository.save(fullDaily);

        // Public search for that city with checkIn and checkOut
        String searchUrl = getBaseUrl() + "/search?city=" + uniqueCity + "&checkIn=" + checkIn + "&checkOut=" + checkOut + "&rooms=1";
        ResponseEntity<List> searchResp = restTemplate.getForEntity(searchUrl, List.class);

        assertEquals(HttpStatus.OK, searchResp.getStatusCode());
        List<?> results = searchResp.getBody();
        assertNotNull(results);

        boolean hasAvailableHotel = results.stream().anyMatch(h -> ((Map<?, ?>) h).get("id").toString().equals(hotelAvail.getId().toString()));
        boolean hasFullHotel = results.stream().anyMatch(h -> ((Map<?, ?>) h).get("id").toString().equals(hotelFull.getId().toString()));

        assertTrue(hasAvailableHotel, "Available hotel must be returned in search");
        assertFalse(hasFullHotel, "Fully booked hotel must NOT be returned in date-filtered search");
    }

    @Test
    @DisplayName("WP-05 4.6: Hotel inventory summary aggregates date-wise capacity, occupancy, and room nights")
    public void testHotelInventorySummary_ReportingPeriodAggregation() {
        Long managerId = 2501L;
        String token = createManagerToken(managerId);
        HotelProperty hotel = createProperty(managerId, "WP05 Summary Resort", "Goa");
        RoomType rt1 = createRoom(hotel.getId(), "Deluxe Room", 4000.0, 10);
        RoomType rt2 = createRoom(hotel.getId(), "Executive Suite", 8000.0, 5);

        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = start.plusDays(2); // 3 nights: start, start+1, start+2

        // Reserve 2 rooms in rt1 for 2 nights via HTTP
        DateWiseReserveRequestDto resDto = DateWiseReserveRequestDto.builder()
                .bookingRef("BKG-WP05-SUMMARY-" + UUID.randomUUID().toString().substring(0, 8))
                .checkInDate(start)
                .checkOutDate(start.plusDays(2))
                .count(2)
                .build();
        restTemplate.exchange("/api/hotels/rooms/" + rt1.getId() + "/reserve-stay",
                HttpMethod.POST, new HttpEntity<>(resDto, authHeaders(token)), Map.class);
        restTemplate.exchange("/api/hotels/rooms/confirm-stay?bookingRef=" + resDto.getBookingRef(),
                HttpMethod.PUT, new HttpEntity<>(authHeaders(token)), Map.class);

        // Block 1 room in rt2 across start to end+1 via HTTP
        RoomBlockRequestDto blockDto = RoomBlockRequestDto.builder()
                .blockedCount(1)
                .startDate(start)
                .endDate(end.plusDays(1))
                .reason("MAINTENANCE")
                .notes("AC Repair")
                .build();
        restTemplate.exchange("/api/hotels/" + hotel.getId() + "/rooms/" + rt2.getId() + "/blocks",
                HttpMethod.POST, new HttpEntity<>(blockDto, authHeaders(token)), Map.class);

        // Call GET /api/hotels/{hotelId}/inventory/summary
        ResponseEntity<HotelInventorySummaryDto> resp = restTemplate.exchange(
                "/api/hotels/" + hotel.getId() + "/inventory/summary?startDate=" + start + "&endDate=" + end,
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)), HotelInventorySummaryDto.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        HotelInventorySummaryDto summary = resp.getBody();
        assertNotNull(summary);
        assertEquals(hotel.getId(), summary.getHotelId());
        // 3 days * 15 total rooms = 45 total physical room nights
        assertEquals(45, summary.getTotalPhysicalRoomNights());
        // 2 rooms * 2 nights = 4 occupied room nights
        assertEquals(4, summary.getOccupiedRoomNights());
        // 1 room blocked * 3 nights = 3 blocked room nights
        assertEquals(3, summary.getBlockedRoomNights());
        // Applicable capacity = 45 - 3 = 42
        assertEquals(42, summary.getApplicableRoomNightCapacity());
        // Occupancy rate = (4 / 42) * 100 = ~9.5%
        assertTrue(summary.getOccupancyRate() > 0.0);
        assertEquals(3, summary.getDailySummaries().size());
    }
}
