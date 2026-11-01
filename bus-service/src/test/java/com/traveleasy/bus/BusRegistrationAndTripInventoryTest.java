package com.traveleasy.bus;

import com.traveleasy.bus.dto.TripSeatLayoutResponse;
import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.Seat;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.SeatRepository;
import com.traveleasy.bus.service.SeatLayoutService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BusRegistrationAndTripInventoryTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private BusScheduleRepository scheduleRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatLayoutService seatLayoutService;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/buses";
    }

    private static final String SECRET = "TravelEasySuperSecretKeyForJWTSigning12345678901234567890";

    private String createTestToken(Long userId, String role, String email) {
        java.security.Key key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes());
        return io.jsonwebtoken.Jwts.builder()
                .claim("userId", userId)
                .claim("role", role)
                .claim("fullName", "User " + userId)
                .setSubject(email)
                .setIssuedAt(new java.util.Date())
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 3600000))
                .signWith(key, io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();
    }

    private <T> HttpEntity<T> createAuthorizedEntity(T body, Long operatorId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + createTestToken(operatorId, "ROLE_BUS_OPERATOR", "operator" + operatorId + "@test.com"));
        return new HttpEntity<>(body, headers);
    }


    @Test
    @DisplayName("Test operator bus registration: derives capacity from template and respects AC/Non-AC")
    public void testBusRegistrationWithTemplate() {
        Bus busReq = Bus.builder()
                .operatorId(999L)
                .operatorName("Royal Express")
                .busNumber("KA-01-EQ-" + System.currentTimeMillis() % 10000)
                .busType("Super Deluxe AC Sleeper")
                .facilityType("AC")
                .busConfiguration("DOUBLE_DECK")
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SLEEPER)
                .amenities("Wi-Fi, Water Bottle, Blanket")
                .build();

        ResponseEntity<Bus> response = restTemplate.postForEntity(getBaseUrl(), createAuthorizedEntity(busReq, 999L), Bus.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Bus savedBus = response.getBody();
        assertNotNull(savedBus);
        assertNotNull(savedBus.getId());
        assertEquals(36, savedBus.getTotalSeats(), "Bus capacity must be derived from 2+1 SLEEPER template (36 berths)");
        assertEquals("AC", savedBus.getFacilityType());
        assertEquals("DOUBLE_DECK", savedBus.getBusConfiguration());
        assertEquals(SeatLayoutService.TEMPLATE_2_PLUS_1_SLEEPER, savedBus.getTemplateId());
    }

    @Test
    @DisplayName("Test schedule seat generation: creates physical seats matching the template")
    public void testScheduleGeneratesPhysicalSeatsFromTemplate() {
        // Register bus with 2+1 Seater cum Sleeper (54 positions)
        Bus bus = Bus.builder()
                .operatorId(888L)
                .operatorName("VRL Logistics")
                .busNumber("KA-25-TR-" + System.currentTimeMillis() % 10000)
                .busType("AC Seater / Sleeper")
                .facilityType("AC")
                .busConfiguration("DOUBLE_DECK")
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER_CUM_SLEEPER)
                .build();
        seatLayoutService.validateAndAssignTemplate(bus, bus.getTemplateId(), bus.getBusConfiguration(), bus.getFacilityType());
        Bus savedBus = busRepository.save(bus);

        // Create Schedule
        BusSchedule schedule = BusSchedule.builder()
                .busId(savedBus.getId())
                .origin("Bengaluru")
                .destination("Hyderabad")
                .travelDate(LocalDate.now().plusDays(2))
                .departureTime("21:00")
                .arrivalTime("06:00")
                .fare(750.0)
                .build();

        ResponseEntity<BusSchedule> schedRes = restTemplate.postForEntity(getBaseUrl() + "/schedules", createAuthorizedEntity(schedule, 888L), BusSchedule.class);
        assertEquals(HttpStatus.CREATED, schedRes.getStatusCode());
        BusSchedule savedSched = schedRes.getBody();
        assertNotNull(savedSched);
        assertEquals(54, savedSched.getAvailableSeats(), "Available seats must be initialized to 54");

        // Verify seats in database
        List<Seat> seats = seatRepository.findByScheduleId(savedSched.getId());
        assertEquals(54, seats.size(), "Schedule must contain exactly 54 physical seats/berths");

        long lowerSeaters = seats.stream().filter(s -> "LOWER".equals(s.getDeck()) && "Seater".equalsIgnoreCase(s.getSeatType())).count();
        assertEquals(36, lowerSeaters, "Lower deck must have 36 Seaters");

        long upperSleepers = seats.stream().filter(s -> "UPPER".equals(s.getDeck()) && "Sleeper".equalsIgnoreCase(s.getSeatType())).count();
        assertEquals(18, upperSleepers, "Upper deck must have 18 Sleepers");

        assertTrue(seats.stream().allMatch(s -> "AVAILABLE".equals(s.getStatus())), "All seats must initially be AVAILABLE");
    }

    @Test
    @DisplayName("Test independent inventory: booking a seat on Trip A does not affect Trip B for the same bus")
    public void testIndependentTripInventory() {
        Bus bus = Bus.builder()
                .operatorId(777L)
                .operatorName("Orange Tours")
                .busNumber("AP-09-XY-" + System.currentTimeMillis() % 10000)
                .busType("Volvo Multi-Axle")
                .busConfiguration("SINGLE_DECK")
                .templateId(SeatLayoutService.TEMPLATE_1_PLUS_1_SEATER)
                .build();
        seatLayoutService.validateAndAssignTemplate(bus, bus.getTemplateId(), bus.getBusConfiguration(), bus.getFacilityType());
        Bus savedBus = busRepository.save(bus);

        // Schedule 1: Today
        BusSchedule sched1 = BusSchedule.builder()
                .busId(savedBus.getId())
                .origin("Hyderabad")
                .destination("Bengaluru")
                .travelDate(LocalDate.now().plusDays(1))
                .fare(600.0)
                .build();
        BusSchedule savedSched1 = restTemplate.postForEntity(getBaseUrl() + "/schedules", createAuthorizedEntity(sched1, 777L), BusSchedule.class).getBody();

        // Schedule 2: Tomorrow
        BusSchedule sched2 = BusSchedule.builder()
                .busId(savedBus.getId())
                .origin("Hyderabad")
                .destination("Bengaluru")
                .travelDate(LocalDate.now().plusDays(2))
                .fare(600.0)
                .build();
        BusSchedule savedSched2 = restTemplate.postForEntity(getBaseUrl() + "/schedules", createAuthorizedEntity(sched2, 777L), BusSchedule.class).getBody();

        assertNotNull(savedSched1);
        assertNotNull(savedSched2);

        // Book seat "1A" on Trip 1
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> bookReq = new HttpEntity<>(List.of("1A"), headers);

        ResponseEntity<Map> bookRes = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + savedSched1.getId() + "/book-seats",
                HttpMethod.PUT, bookReq, Map.class);
        assertEquals(HttpStatus.OK, bookRes.getStatusCode());

        // Verify Seat 1A is BOOKED on Trip 1
        Seat trip1Seat = seatRepository.findByScheduleIdAndSeatNumber(savedSched1.getId(), "1A").orElseThrow();
        assertTrue("LOCKED".equals(trip1Seat.getStatus()) || "BOOKED".equals(trip1Seat.getStatus()));

        // Verify Seat 1A is STILL AVAILABLE on Trip 2
        Seat trip2Seat = seatRepository.findByScheduleIdAndSeatNumber(savedSched2.getId(), "1A").orElseThrow();
        assertEquals("AVAILABLE", trip2Seat.getStatus(), "Trip 2 seat must remain AVAILABLE independently");
    }

    @Test
    @DisplayName("Test booking prevention: cannot double book an already booked seat")
    public void testPreventDoubleBooking() {
        Bus bus = Bus.builder()
                .operatorId(666L)
                .operatorName("Kaveri Travels")
                .busNumber("TS-07-KT-" + System.currentTimeMillis() % 10000)
                .busConfiguration("SINGLE_DECK")
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER)
                .build();
        seatLayoutService.validateAndAssignTemplate(bus, bus.getTemplateId(), bus.getBusConfiguration(), bus.getFacilityType());
        Bus savedBus = busRepository.save(bus);

        BusSchedule sched = BusSchedule.builder()
                .busId(savedBus.getId())
                .origin("Vijayawada")
                .destination("Hyderabad")
                .travelDate(LocalDate.now().plusDays(3))
                .fare(500.0)
                .build();
        BusSchedule savedSched = restTemplate.postForEntity(getBaseUrl() + "/schedules", createAuthorizedEntity(sched, bus.getOperatorId()), BusSchedule.class).getBody();
        assertNotNull(savedSched);

        // First booking succeeds
        HttpEntity<List<String>> bookReq = new HttpEntity<>(List.of("1A"), new HttpHeaders());
        ResponseEntity<Map> res1 = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + savedSched.getId() + "/book-seats",
                HttpMethod.PUT, bookReq, Map.class);
        assertEquals(HttpStatus.OK, res1.getStatusCode());

        // Second booking of same seat fails with BAD_REQUEST
        ResponseEntity<Map> res2 = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + savedSched.getId() + "/book-seats",
                HttpMethod.PUT, bookReq, Map.class);
        assertTrue(res2.getStatusCode() == HttpStatus.BAD_REQUEST || res2.getStatusCode() == HttpStatus.CONFLICT);
        assertTrue(res2.getBody().get("message").toString().contains("not available"));
    }

    @Test
    @DisplayName("Test seat release: releasing booked seats makes them available again")
    public void testSeatRelease() {
        Bus bus = Bus.builder()
                .operatorId(555L)
                .operatorName("SRS Travels")
                .busNumber("KA-01-SR-" + System.currentTimeMillis() % 10000)
                .busConfiguration("SINGLE_DECK")
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER)
                .build();
        seatLayoutService.validateAndAssignTemplate(bus, bus.getTemplateId(), bus.getBusConfiguration(), bus.getFacilityType());
        Bus savedBus = busRepository.save(bus);

        BusSchedule sched = BusSchedule.builder()
                .busId(savedBus.getId())
                .origin("Chennai")
                .destination("Bengaluru")
                .travelDate(LocalDate.now().plusDays(4))
                .fare(550.0)
                .build();
        BusSchedule savedSched = restTemplate.postForEntity(getBaseUrl() + "/schedules", createAuthorizedEntity(sched, bus.getOperatorId()), BusSchedule.class).getBody();
        assertNotNull(savedSched);

        // Book 2A and 2B
        HttpEntity<List<String>> bookReq = new HttpEntity<>(List.of("2A", "2B"), new HttpHeaders());
        restTemplate.exchange(getBaseUrl() + "/schedules/" + savedSched.getId() + "/book-seats", HttpMethod.PUT, bookReq, Map.class);

        // Release 2A
        HttpEntity<List<String>> relReq = new HttpEntity<>(List.of("2A"), new HttpHeaders());
        ResponseEntity<Map> relRes = restTemplate.exchange(
                getBaseUrl() + "/schedules/" + savedSched.getId() + "/release-seats",
                HttpMethod.PUT, relReq, Map.class);
        assertEquals(HttpStatus.OK, relRes.getStatusCode());

        Seat seat2A = seatRepository.findByScheduleIdAndSeatNumber(savedSched.getId(), "2A").orElseThrow();
        assertEquals("AVAILABLE", seat2A.getStatus());

        Seat seat2B = seatRepository.findByScheduleIdAndSeatNumber(savedSched.getId(), "2B").orElseThrow();
        assertTrue("LOCKED".equals(seat2B.getStatus()) || "BOOKED".equals(seat2B.getStatus()));
    }

    @Test
    @DisplayName("Test /api/buses/schedules/{scheduleId}/layout contract: returns structured decks and positions for future UI")
    public void testTripLayoutApiContract() {
        // Register Double-deck Hybrid Sleeper-1
        Bus bus = Bus.builder()
                .operatorId(444L)
                .operatorName("Morning Star Travels")
                .busNumber("AP-16-MS-" + System.currentTimeMillis() % 10000)
                .busType("Hybrid AC Sleeper")
                .busConfiguration("DOUBLE_DECK")
                .templateId(SeatLayoutService.TEMPLATE_2_PLUS_1_HYBRID_SLEEPER_1)
                .facilityType("AC")
                .amenities("Blanket, Charging Point")
                .build();
        seatLayoutService.validateAndAssignTemplate(bus, bus.getTemplateId(), bus.getBusConfiguration(), bus.getFacilityType());
        Bus savedBus = busRepository.save(bus);

        BusSchedule sched = BusSchedule.builder()
                .busId(savedBus.getId())
                .origin("Vijayawada")
                .destination("Hyderabad")
                .travelDate(LocalDate.now().plusDays(5))
                .departureTime("22:30")
                .arrivalTime("05:30")
                .fare(650.0)
                .build();
        BusSchedule savedSched = restTemplate.postForEntity(getBaseUrl() + "/schedules", createAuthorizedEntity(sched, bus.getOperatorId()), BusSchedule.class).getBody();
        assertNotNull(savedSched);

        // Request Layout API Contract
        ResponseEntity<TripSeatLayoutResponse> layoutRes = restTemplate.getForEntity(
                getBaseUrl() + "/schedules/" + savedSched.getId() + "/layout",
                TripSeatLayoutResponse.class);

        assertEquals(HttpStatus.OK, layoutRes.getStatusCode());
        TripSeatLayoutResponse layout = layoutRes.getBody();
        assertNotNull(layout);

        assertEquals(SeatLayoutService.TEMPLATE_2_PLUS_1_HYBRID_SLEEPER_1, layout.getTemplateId());
        assertEquals("2+1 HYBRID_SLEEPER-1.PNG", layout.getReferenceImage());
        assertEquals(48, layout.getTotalInventory());
        assertEquals(48, layout.getAvailableSeats());
        assertEquals("DOUBLE_DECK", layout.getBusConfiguration());
        assertEquals("AC", layout.getFacilityType());
        assertEquals(2, layout.getDecks().size(), "Must have Lower Deck and Upper Deck");

        // Verify Lower Deck
        var lowerDeck = layout.getDecks().stream().filter(d -> "LOWER".equals(d.getDeck())).findFirst().orElseThrow();
        assertEquals("Lower Deck", lowerDeck.getDeckName());
        assertEquals(30, lowerDeck.getInventory(), "Lower deck must have 30 positions (6 berths + 24 seats)");

        // Verify Upper Deck
        var upperDeck = layout.getDecks().stream().filter(d -> "UPPER".equals(d.getDeck())).findFirst().orElseThrow();
        assertEquals("Upper Deck", upperDeck.getDeckName());
        assertEquals(18, upperDeck.getInventory(), "Upper deck must have 18 berths");
    }
}
