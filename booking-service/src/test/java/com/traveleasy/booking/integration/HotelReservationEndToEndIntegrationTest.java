package com.traveleasy.booking.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.dto.*;
import com.traveleasy.booking.model.*;
import com.traveleasy.booking.repository.*;
import com.traveleasy.booking.service.BookingInternalTokenService;
import com.traveleasy.booking.service.HotelBookingOrchestrationService;
import com.traveleasy.booking.validator.GuestAllocationValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
public class HotelReservationEndToEndIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private HotelReservationSnapshotRepository snapshotRepository;

    @Autowired
    private HotelAllocationSnapshotRepository allocationSnapshotRepository;

    @Autowired
    private HotelRoomGuestAllocationRepository roomAllocationRepository;

    @Autowired
    private HotelRoomGuestRepository guestRepository;

    @Autowired
    private HotelBookingOrchestrationService orchestrationService;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private BookingInternalTokenService internalTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<String> createdBookingRefs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        orchestrationService.setRestTemplate(restTemplate);
        lenient().when(internalTokenService.generateInternalToken()).thenReturn("mock-internal-jwt");
    }

    @AfterEach
    void tearDown() {
        for (String ref : createdBookingRefs) {
            Optional<Booking> bOpt = bookingRepository.findByBookingRef(ref);
            if (bOpt.isPresent()) {
                Booking b = bOpt.get();
                Optional<HotelReservationSnapshot> sOpt = snapshotRepository.findByBookingId(b.getId());
                sOpt.ifPresent(s -> snapshotRepository.delete(s));
                bookingRepository.delete(b);
            }
        }
        createdBookingRefs.clear();
    }

    private void mockRoomCapacity(Long roomId, Long hotelId, String status, int maxOccupancy) {
        Map<String, Object> roomData = new HashMap<>();
        roomData.put("id", roomId);
        roomData.put("hotelId", hotelId);
        roomData.put("status", status);
        roomData.put("maxOccupancy", maxOccupancy);

        lenient().when(restTemplate.exchange(
                contains("/api/hotels/rooms/" + roomId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(new ResponseEntity<>(roomData, HttpStatus.OK));
    }

    private HotelReservationInitiationRequest buildMultiRoomRequest() {
        String testRef = "BKG-H-E2E-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        createdBookingRefs.add(testRef);

        RoomGuestAllocationDto r1 = RoomGuestAllocationDto.builder()
                .roomSequence(1)
                .adults(2)
                .children(1)
                .occupants(List.of(
                        RoomOccupantDto.builder().name("Primary Guest").type(GuestType.ADULT).age(34).gender(com.traveleasy.booking.model.GuestGender.MALE).build(),
                        RoomOccupantDto.builder().name("Adult Companion").type(GuestType.ADULT).age(31).gender(com.traveleasy.booking.model.GuestGender.FEMALE).build(),
                        RoomOccupantDto.builder().name("Child One").type(GuestType.CHILD).age(6).build()
                ))
                .build();

        RoomGuestAllocationDto r2 = RoomGuestAllocationDto.builder()
                .roomSequence(2)
                .adults(1)
                .children(0)
                .occupants(List.of(
                        RoomOccupantDto.builder().name("Solo Adult").type(GuestType.ADULT).age(40).gender(com.traveleasy.booking.model.GuestGender.OTHER).build()
                ))
                .build();

        ReservationAllocationItemDto item1 = ReservationAllocationItemDto.builder()
                .roomId(201L)
                .quantity(2)
                .rooms(List.of(r1, r2))
                .build();

        RoomGuestAllocationDto r3 = RoomGuestAllocationDto.builder()
                .roomSequence(1)
                .adults(2)
                .children(0)
                .occupants(List.of(
                        RoomOccupantDto.builder().name("Guest A").type(GuestType.ADULT).age(28).gender(com.traveleasy.booking.model.GuestGender.FEMALE).build(),
                        RoomOccupantDto.builder().name("Guest B").type(GuestType.ADULT).age(30).gender(com.traveleasy.booking.model.GuestGender.PREFER_NOT_TO_SAY).build()
                ))
                .build();

        ReservationAllocationItemDto item2 = ReservationAllocationItemDto.builder()
                .roomId(202L)
                .quantity(1)
                .rooms(List.of(r3))
                .build();

        return HotelReservationInitiationRequest.builder()
                .bookingRef(testRef)
                .hotelId(501L)
                .hotelName("ITC Kohenur Luxury Hotel")
                .checkInDate(LocalDate.now().plusDays(10))
                .checkOutDate(LocalDate.now().plusDays(13))
                .customerId(99L)
                .customerName("Vikram Reddy")
                .customerEmail("vikram@example.com")
                .primaryGuest(PrimaryGuestDto.builder()
                        .fullName("Vikram Reddy")
                        .email("vikram@example.com")
                        .mobile("+919876543210")
                        .build())
                .allocations(List.of(item1, item2))
                .build();
    }

    @Test
    @DisplayName("WP-HB-06.2 E2E: Full lifecycle multi-room reservation initiation, relational persistence, and detail retrieval")
    void testEndToEndInitiationAndRetrieval_Success() {
        mockRoomCapacity(201L, 501L, "ACTIVE", 3);
        mockRoomCapacity(202L, 501L, "ACTIVE", 2);

        Map<String, Object> alloc1 = Map.of(
                "roomId", 201L,
                "roomTypeName", "Executive Club Room",
                "quantity", 2,
                "allocationTotal", 18000.0,
                "nightlyPriceBreakdown", List.of(Map.of("date", "2026-12-10", "price", 3000.0))
        );
        Map<String, Object> alloc2 = Map.of(
                "roomId", 202L,
                "roomTypeName", "Grand Deluxe Suite",
                "quantity", 1,
                "allocationTotal", 12000.0,
                "nightlyPriceBreakdown", List.of(Map.of("date", "2026-12-10", "price", 4000.0))
        );

        Map<String, Object> hotelResponse = new HashMap<>();
        hotelResponse.put("hotelId", 501L);
        hotelResponse.put("status", "HELD");
        hotelResponse.put("totalPrice", 30000.0);
        hotelResponse.put("expiresAt", LocalDateTime.now().plusMinutes(15).toString());
        hotelResponse.put("allocations", List.of(alloc1, alloc2));

        when(restTemplate.postForEntity(contains("/api/hotels/reservations"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(hotelResponse, HttpStatus.CREATED));

        HotelReservationInitiationRequest request = buildMultiRoomRequest();
        String bookingRef = request.getBookingRef();

        // 1. Initiate Reservation
        HotelReservationInitiationResponse initResponse = orchestrationService.initiateHotelReservation(
                request, 99L, "vikram@example.com");

        assertNotNull(initResponse);
        assertEquals(bookingRef, initResponse.getBookingRef());
        assertEquals("PENDING_PAYMENT", initResponse.getStatus());
        assertEquals(30000.0, initResponse.getTotalAmount());
        assertNotNull(initResponse.getPrimaryGuest());
        assertEquals("Vikram Reddy", initResponse.getPrimaryGuest().getFullName());
        assertEquals(2, initResponse.getAllocations().size());

        // 2. Verify Database Persistence
        Booking savedBooking = bookingRepository.findByBookingRef(bookingRef).orElseThrow();
        assertEquals("HOTEL", savedBooking.getServiceType());
        assertEquals("PENDING_PAYMENT", savedBooking.getStatus());
        assertEquals(30000.0, savedBooking.getTotalAmount());

        HotelReservationSnapshot snapshot = snapshotRepository.findByBookingRef(bookingRef).orElseThrow();
        assertEquals("Vikram Reddy", snapshot.getPrimaryGuestName());
        assertEquals("vikram@example.com", snapshot.getPrimaryGuestEmail());
        assertEquals("+919876543210", snapshot.getPrimaryGuestMobile());
        assertEquals(2, snapshot.getAllocations().size());

        // Check room allocations in relational DB
        List<HotelAllocationSnapshot> allocs = snapshot.getAllocations();
        HotelAllocationSnapshot allocSnapshot1 = allocs.stream()
                .filter(a -> a.getRoomId().equals(201L)).findFirst().orElseThrow();
        List<HotelRoomGuestAllocation> rooms1 = roomAllocationRepository
                .findByAllocationSnapshotIdOrderByRoomSequenceAsc(allocSnapshot1.getId());
        assertEquals(2, rooms1.size(), "Should have 2 physical rooms for allocation 201");
        assertEquals(1, rooms1.get(0).getRoomSequence());
        assertEquals(2, rooms1.get(0).getAdultCount());
        assertEquals(1, rooms1.get(0).getChildCount());

        List<HotelRoomGuest> occupantsRoom1 = guestRepository
                .findByRoomGuestAllocationIdOrderByIdAsc(rooms1.get(0).getId());
        assertEquals(3, occupantsRoom1.size());
        assertEquals("Primary Guest", occupantsRoom1.get(0).getGuestName());
        assertEquals(34, occupantsRoom1.get(0).getAge());
        assertEquals(com.traveleasy.booking.model.GuestGender.MALE, occupantsRoom1.get(0).getGender());
        assertEquals("Child One", occupantsRoom1.get(2).getGuestName());
        assertEquals(GuestType.CHILD, occupantsRoom1.get(2).getGuestType());
        assertEquals(6, occupantsRoom1.get(2).getAge());
        assertNull(occupantsRoom1.get(2).getGender());

        // 3. Verify Detail Retrieval API by Authorized Traveller
        HotelReservationDetailResponse details = orchestrationService.getReservationDetails(
                bookingRef, 99L, "ROLE_TRAVELLER", false);

        assertNotNull(details);
        assertEquals(bookingRef, details.getBookingRef());
        assertEquals("PENDING_PAYMENT", details.getBookingStatus());
        assertEquals("PENDING", details.getPaymentStatus());
        assertEquals(501L, details.getHotel().getHotelId());
        assertEquals("ITC Kohenur Luxury Hotel", details.getHotel().getHotelName());
        assertEquals(30000.0, details.getTotalAmount());
        assertEquals("Vikram Reddy", details.getPrimaryGuest().getFullName());
        assertEquals(2, details.getAllocations().size());

        // 4. Verify Unauthorized Access Protection
        ResponseStatusException forbiddenEx = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.getReservationDetails(bookingRef, 88L, "ROLE_TRAVELLER", false)
        );
        assertEquals(HttpStatus.FORBIDDEN, forbiddenEx.getStatusCode());
    }

    @Test
    @DisplayName("WP-HB-06.2 E2E: Room capacity violation fails closed without persisting booking or snapshot")
    void testInitiation_ExceedsRoomCapacity_Rejected() {
        // Room 201 allows max 2 occupants
        mockRoomCapacity(201L, 501L, "ACTIVE", 2);
        mockRoomCapacity(202L, 501L, "ACTIVE", 2);

        HotelReservationInitiationRequest request = buildMultiRoomRequest();
        // Request has room 1 with 3 occupants (2 adults, 1 child) -> exceeds capacity 2

        GuestAllocationValidationException ex = assertThrows(GuestAllocationValidationException.class, () ->
                orchestrationService.initiateHotelReservation(request, 99L, "vikram@example.com")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("exceeds max occupancy limit (2)"));

        // Verify nothing was persisted
        assertFalse(bookingRepository.findByBookingRef(request.getBookingRef()).isPresent());
        assertFalse(snapshotRepository.findByBookingRef(request.getBookingRef()).isPresent());
        verify(restTemplate, never()).postForEntity(contains("/api/hotels/reservations"), any(), any());
    }

    @Test
    @DisplayName("WP-HB-06.2 E2E: Room type belonging to another hotel rejected")
    void testInitiation_RoomBelongsToDifferentHotel_Rejected() {
        mockRoomCapacity(201L, 999L, "ACTIVE", 3); // Belongs to hotel 999!
        mockRoomCapacity(202L, 501L, "ACTIVE", 2);

        HotelReservationInitiationRequest request = buildMultiRoomRequest();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.initiateHotelReservation(request, 99L, "vikram@example.com")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("does not belong to hotel ID 501"));

        assertFalse(bookingRepository.findByBookingRef(request.getBookingRef()).isPresent());
    }

    @Test
    @DisplayName("WP-HB-06.2 E2E: Inactive room type rejected")
    void testInitiation_InactiveRoomType_Rejected() {
        mockRoomCapacity(201L, 501L, "ARCHIVED", 3); // Inactive!
        mockRoomCapacity(202L, 501L, "ACTIVE", 2);

        HotelReservationInitiationRequest request = buildMultiRoomRequest();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.initiateHotelReservation(request, 99L, "vikram@example.com")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("is not active"));

        assertFalse(bookingRepository.findByBookingRef(request.getBookingRef()).isPresent());
    }

    @Test
    @DisplayName("WP-HB-06.2 E2E: Capacity lookup network failure fails closed")
    void testInitiation_CapacityLookupFailsClosed() {
        when(restTemplate.exchange(
                contains("/api/hotels/rooms/201"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Room category not found"));

        HotelReservationInitiationRequest request = buildMultiRoomRequest();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.initiateHotelReservation(request, 99L, "vikram@example.com")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Room category not found with ID: 201"));

        assertFalse(bookingRepository.findByBookingRef(request.getBookingRef()).isPresent());
    }

    @Test
    @DisplayName("WP-HB-06.2 E2E: Distinct room type deduplication prevents redundant HTTP requests")
    void testInitiation_CapacityLookupDeduplication() {
        mockRoomCapacity(201L, 501L, "ACTIVE", 3);
        mockRoomCapacity(202L, 501L, "ACTIVE", 2);

        Map<String, Object> alloc1 = Map.of(
                "roomId", 201L,
                "roomTypeName", "Club Room",
                "quantity", 2,
                "allocationTotal", 18000.0
        );
        Map<String, Object> alloc2 = Map.of(
                "roomId", 202L,
                "roomTypeName", "Deluxe Room",
                "quantity", 1,
                "allocationTotal", 12000.0
        );

        Map<String, Object> hotelResponse = new HashMap<>();
        hotelResponse.put("hotelId", 501L);
        hotelResponse.put("status", "HELD");
        hotelResponse.put("totalPrice", 30000.0);
        hotelResponse.put("allocations", List.of(alloc1, alloc2));

        when(restTemplate.postForEntity(contains("/api/hotels/reservations"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(hotelResponse, HttpStatus.CREATED));

        HotelReservationInitiationRequest request = buildMultiRoomRequest();
        orchestrationService.initiateHotelReservation(request, 99L, "vikram@example.com");

        // Verify roomId 201 was fetched exactly once despite having 2 physical rooms
        verify(restTemplate, times(1)).exchange(
                contains("/api/hotels/rooms/201"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
        verify(restTemplate, times(1)).exchange(
                contains("/api/hotels/rooms/202"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("WP-HB-06.2 E2E: Historical booking without snapshots is retrievable without fabricating guest data")
    void testRetrieval_HistoricalBookingWithoutSnapshots() {
        String legacyRef = "BKG-H-LEGACY-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        createdBookingRefs.add(legacyRef);

        Booking legacyBooking = Booking.builder()
                .bookingRef(legacyRef)
                .serviceType("HOTEL")
                .serviceId(701L)
                .serviceTitle("Taj Lake Palace")
                .customerId(99L)
                .customerName("Historical Traveller")
                .checkInDate(LocalDate.of(2025, 4, 1))
                .checkOutDate(LocalDate.of(2025, 4, 3))
                .totalAmount(25000.0)
                .status("CONFIRMED")
                .build();
        bookingRepository.save(legacyBooking);

        HotelReservationDetailResponse response = orchestrationService.getReservationDetails(
                legacyRef, 99L, "ROLE_TRAVELLER", false);

        assertNotNull(response);
        assertEquals(legacyRef, response.getBookingRef());
        assertEquals("CONFIRMED", response.getBookingStatus());
        assertEquals("PAID", response.getPaymentStatus());
        assertEquals(701L, response.getHotel().getHotelId());
        assertEquals("Taj Lake Palace", response.getHotel().getHotelName());
        assertEquals(2, response.getTotalNights());
        assertNull(response.getPrimaryGuest());
        assertTrue(response.getAllocations().isEmpty());
    }

    @Test
    @DisplayName("WP-HB-UI-03 E2E: Missing adult gender or underage adult fails validation without persisting booking")
    void testInitiation_MissingAdultGenderOrUnderage_RejectedWithoutPersisting() {
        mockRoomCapacity(201L, 501L, "ACTIVE", 3);
        mockRoomCapacity(202L, 501L, "ACTIVE", 2);

        HotelReservationInitiationRequest request = buildMultiRoomRequest();
        request.getAllocations().get(0).getRooms().get(0).getOccupants().get(0).setGender(null);
        request.getAllocations().get(0).getRooms().get(0).getOccupants().get(1).setAge(17);

        com.traveleasy.booking.validator.GuestAllocationValidationException ex = assertThrows(
                com.traveleasy.booking.validator.GuestAllocationValidationException.class,
                () -> orchestrationService.initiateHotelReservation(request, 99L, "vikram@example.com")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertFalse(ex.getErrorDetails().isEmpty());
        assertFalse(bookingRepository.findByBookingRef(request.getBookingRef()).isPresent());
        verify(restTemplate, never()).postForEntity(contains("/api/hotels/reservations"), any(), any());
    }
}
