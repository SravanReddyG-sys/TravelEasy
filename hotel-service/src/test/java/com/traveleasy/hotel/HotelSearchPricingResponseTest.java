package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.HotelPublicDto;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomPriceOverride;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.RoomPriceOverrideRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HotelSearchPricingResponseTest {

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

    private String testCity;

    @BeforeEach
    void setUp() {
        testCity = "TestCity_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private HotelProperty createHotel(String name, String city) {
        HotelProperty hotel = new HotelProperty();
        hotel.setName(name);
        hotel.setCity(city);
        hotel.setAddress("123 Test Street");
        hotel.setStatus("ACTIVE");
        hotel.setPropertyType("Hotel");
        hotel.setStarRating(4);
        return hotelRepository.save(hotel);
    }

    private RoomType createRoom(Long hotelId, String name, Double basePrice, int totalRooms) {
        RoomType rt = new RoomType();
        rt.setHotelId(hotelId);
        rt.setRoomTypeName(name);
        rt.setPricePerNight(basePrice);
        rt.setTotalRooms(totalRooms);
        rt.setAvailableRooms(totalRooms);
        rt.setMaxOccupancy(2);
        rt.setStatus("ACTIVE");
        return roomTypeRepository.save(rt);
    }

    private RoomPriceOverride createOverride(Long hotelId, Long roomId, LocalDate date, Double price) {
        RoomPriceOverride override = RoomPriceOverride.builder()
                .hotelId(hotelId)
                .roomId(roomId)
                .overrideDate(date)
                .pricePerNight(price)
                .build();
        return priceOverrideRepository.save(override);
    }

    @Test
    @DisplayName("Scenario A: Base price only - no override, returns base price")
    void testSearch_BasePriceOnly() {
        HotelProperty hotel = createHotel("Base Price Hotel", testCity);
        createRoom(hotel.getId(), "Standard", 3500.0, 5);

        LocalDate checkIn = LocalDate.of(2026, 12, 1);
        LocalDate checkOut = LocalDate.of(2026, 12, 2);

        String url = "http://localhost:" + port + "/api/hotels/search?city=" + testCity
                + "&checkIn=" + checkIn + "&checkOut=" + checkOut + "&rooms=1";

        ResponseEntity<List<HotelPublicDto>> resp = restTemplate.exchange(
                url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<HotelPublicDto> list = resp.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());

        HotelPublicDto dto = list.get(0);
        assertEquals(3500.0, dto.getMinPrice());
        assertEquals(3500.0, dto.getMinTotalPricePerRoomForStay());
    }

    @Test
    @DisplayName("Scenario B: Date-specific override takes precedence over base price")
    void testSearch_DateSpecificOverridePrecedence() {
        HotelProperty hotel = createHotel("Override Hotel", testCity);
        RoomType room = createRoom(hotel.getId(), "Deluxe", 4000.0, 5);

        LocalDate checkIn = LocalDate.of(2026, 12, 10);
        LocalDate checkOut = LocalDate.of(2026, 12, 11);

        createOverride(hotel.getId(), room.getId(), checkIn, 5200.0);

        String url = "http://localhost:" + port + "/api/hotels/search?city=" + testCity
                + "&checkIn=" + checkIn + "&checkOut=" + checkOut + "&rooms=1";

        ResponseEntity<List<HotelPublicDto>> resp = restTemplate.exchange(
                url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<HotelPublicDto> list = resp.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());

        HotelPublicDto dto = list.get(0);
        assertEquals(5200.0, dto.getMinPrice());
        assertEquals(5200.0, dto.getMinTotalPricePerRoomForStay());
    }

    @Test
    @DisplayName("Scenario C: Multiple nightly overrides - starting nightly price reflects minimum rate, stay total equals sum per room")
    void testSearch_MultipleNightOverrides() {
        HotelProperty hotel = createHotel("Multi Night Hotel", testCity);
        RoomType room = createRoom(hotel.getId(), "Executive", 3000.0, 5);

        LocalDate d1 = LocalDate.of(2026, 12, 15);
        LocalDate d2 = LocalDate.of(2026, 12, 16);
        LocalDate d3 = LocalDate.of(2026, 12, 17);
        LocalDate dOut = LocalDate.of(2026, 12, 18);

        createOverride(hotel.getId(), room.getId(), d1, 3200.0);
        createOverride(hotel.getId(), room.getId(), d2, 2800.0); // Minimum rate
        createOverride(hotel.getId(), room.getId(), d3, 3500.0);

        String url = "http://localhost:" + port + "/api/hotels/search?city=" + testCity
                + "&checkIn=" + d1 + "&checkOut=" + dOut + "&rooms=1";

        ResponseEntity<List<HotelPublicDto>> resp = restTemplate.exchange(
                url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<HotelPublicDto> list = resp.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());

        HotelPublicDto dto = list.get(0);
        // Minimum nightly rate is 2800.0
        assertEquals(2800.0, dto.getMinPrice());
        // Sum of nightly rates is 3200 + 2800 + 3500 = 9500.0
        assertEquals(9500.0, dto.getMinTotalPricePerRoomForStay());
    }

    @Test
    @DisplayName("Scenario D: Multiple active room types - selects room type with lowest per-room stay total")
    void testSearch_MultipleActiveRoomTypes_SelectsLowestStayTotal() {
        HotelProperty hotel = createHotel("Multi Room Hotel", testCity);

        // Room A: Base 2000, but night 2 override is 5000 -> 2 nights total = 7000 (starting rate 2000)
        RoomType roomA = createRoom(hotel.getId(), "Room A", 2000.0, 5);
        // Room B: Base 3100 -> 2 nights total = 6200 (starting rate 3100)
        RoomType roomB = createRoom(hotel.getId(), "Room B", 3100.0, 5);

        LocalDate checkIn = LocalDate.of(2026, 12, 20);
        LocalDate night2 = LocalDate.of(2026, 12, 21);
        LocalDate checkOut = LocalDate.of(2026, 12, 22);

        createOverride(hotel.getId(), roomA.getId(), night2, 5000.0);

        String url = "http://localhost:" + port + "/api/hotels/search?city=" + testCity
                + "&checkIn=" + checkIn + "&checkOut=" + checkOut + "&rooms=1";

        ResponseEntity<List<HotelPublicDto>> resp = restTemplate.exchange(
                url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<HotelPublicDto> list = resp.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());

        HotelPublicDto dto = list.get(0);
        // Room B should be selected because 6200 < 7000
        assertEquals(roomB.getId(), dto.getSelectedRoomTypeId());
        assertEquals(3100.0, dto.getMinPrice());
        assertEquals(6200.0, dto.getMinTotalPricePerRoomForStay());
    }

    @Test
    @DisplayName("Scenario E: Multiple requested rooms - stay total remains explicitly per room")
    void testSearch_MultipleRequestedRooms_StayTotalIsPerRoom() {
        HotelProperty hotel = createHotel("Multi Room Request Hotel", testCity);
        RoomType room = createRoom(hotel.getId(), "Standard Twin", 2500.0, 10);

        LocalDate checkIn = LocalDate.of(2026, 12, 25);
        LocalDate checkOut = LocalDate.of(2026, 12, 28); // 3 nights

        // Request 3 rooms
        String url = "http://localhost:" + port + "/api/hotels/search?city=" + testCity
                + "&checkIn=" + checkIn + "&checkOut=" + checkOut + "&rooms=3";

        ResponseEntity<List<HotelPublicDto>> resp = restTemplate.exchange(
                url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<HotelPublicDto> list = resp.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());

        HotelPublicDto dto = list.get(0);
        assertEquals(2500.0, dto.getMinPrice());
        // Per-room total for 3 nights = 2500 * 3 = 7500.0 (NOT 7500 * 3 = 22500)
        assertEquals(7500.0, dto.getMinTotalPricePerRoomForStay());
    }

    @Test
    @DisplayName("Scenario F: Room with null price is ignored, positive room is selected")
    void testSearch_ZeroOrMissingPriceRoom_Ignored() {
        HotelProperty hotel = createHotel("Mixed Pricing Hotel", testCity);
        // Room 1: null price (eff 0.0)
        createRoom(hotel.getId(), "Superior Unpriced", null, 10);
        // Room 2: positive price
        RoomType room2 = createRoom(hotel.getId(), "Standard Priced", 3499.0, 5);

        LocalDate checkIn = LocalDate.of(2026, 11, 11);
        LocalDate checkOut = LocalDate.of(2026, 11, 12);

        String url = "http://localhost:" + port + "/api/hotels/search?city=" + testCity
                + "&checkIn=" + checkIn + "&checkOut=" + checkOut + "&rooms=1";

        ResponseEntity<List<HotelPublicDto>> resp = restTemplate.exchange(
                url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<HotelPublicDto> list = resp.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());

        HotelPublicDto dto = list.get(0);
        // Must select room2 with positive price 3499.0, ignoring 0.0
        assertEquals(room2.getId(), dto.getSelectedRoomTypeId());
        assertEquals(3499.0, dto.getMinPrice());
        assertEquals(3499.0, dto.getMinTotalPricePerRoomForStay());
    }

    @Test
    @DisplayName("Scenario G: Non-date search returns base price backward compatibility")
    void testSearch_NonDateSearch_BackwardCompatibility() {
        HotelProperty hotel = createHotel("Non Date Hotel", testCity);
        createRoom(hotel.getId(), "Economy", 2200.0, 5);
        createRoom(hotel.getId(), "Luxury", 6500.0, 5);

        String url = "http://localhost:" + port + "/api/hotels/search?city=" + testCity;

        ResponseEntity<List<HotelPublicDto>> resp = restTemplate.exchange(
                url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<HotelPublicDto> list = resp.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());

        HotelPublicDto dto = list.get(0);
        assertEquals(2200.0, dto.getMinPrice());
        assertNull(dto.getMinTotalPricePerRoomForStay()); // No dates requested
    }
}
