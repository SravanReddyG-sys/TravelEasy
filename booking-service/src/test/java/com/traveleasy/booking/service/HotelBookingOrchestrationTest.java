package com.traveleasy.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.traveleasy.booking.dto.*;
import com.traveleasy.booking.model.*;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.repository.HotelAllocationSnapshotRepository;
import com.traveleasy.booking.repository.HotelReservationSnapshotRepository;
import com.traveleasy.booking.repository.HotelRoomGuestAllocationRepository;
import com.traveleasy.booking.repository.HotelRoomGuestRepository;
import com.traveleasy.booking.validator.GuestAllocationValidationException;
import com.traveleasy.booking.validator.HotelGuestAllocationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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

@ExtendWith(MockitoExtension.class)
public class HotelBookingOrchestrationTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private HotelReservationSnapshotRepository snapshotRepository;

    @Mock
    private HotelAllocationSnapshotRepository allocationSnapshotRepository;

    @Mock
    private HotelRoomGuestAllocationRepository roomAllocationRepository;

    @Mock
    private HotelRoomGuestRepository guestRepository;

    @Mock
    private BookingInternalTokenService internalTokenService;

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private HotelGuestAllocationValidator validator;
    private HotelBookingOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        validator = new HotelGuestAllocationValidator();

        orchestrationService = new HotelBookingOrchestrationService(
                bookingRepository,
                snapshotRepository,
                allocationSnapshotRepository,
                roomAllocationRepository,
                guestRepository,
                validator,
                internalTokenService,
                objectMapper
        );
        orchestrationService.setRestTemplate(restTemplate);

        lenient().when(internalTokenService.generateInternalToken()).thenReturn("mock-token-xyz");
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(1001L);
            }
            return b;
        });

        // Default mock room capacity responses
        mockRoomType(101L, 501L, "ACTIVE", 3);
        mockRoomType(102L, 501L, "ACTIVE", 2);
    }

    private void mockRoomType(Long roomId, Long hotelId, String status, int maxOccupancy) {
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

    private HotelReservationInitiationRequest buildValidRequest() {
        RoomGuestAllocationDto r1 = RoomGuestAllocationDto.builder()
                .roomSequence(1)
                .adults(2)
                .children(0)
                .occupants(List.of(
                        RoomOccupantDto.builder().name("Adult One").type(GuestType.ADULT).build(),
                        RoomOccupantDto.builder().name("Adult Two").type(GuestType.ADULT).build()
                ))
                .build();

        RoomGuestAllocationDto r2 = RoomGuestAllocationDto.builder()
                .roomSequence(2)
                .adults(1)
                .children(1)
                .occupants(List.of(
                        RoomOccupantDto.builder().name("Adult Three").type(GuestType.ADULT).build(),
                        RoomOccupantDto.builder().name("Child One").type(GuestType.CHILD).age(7).build()
                ))
                .build();

        ReservationAllocationItemDto item1 = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(2)
                .rooms(List.of(r1, r2))
                .build();

        RoomGuestAllocationDto r3 = RoomGuestAllocationDto.builder()
                .roomSequence(1)
                .adults(1)
                .children(0)
                .occupants(List.of(
                        RoomOccupantDto.builder().name("Adult Four").type(GuestType.ADULT).build()
                ))
                .build();

        ReservationAllocationItemDto item2 = ReservationAllocationItemDto.builder()
                .roomId(102L)
                .quantity(1)
                .rooms(List.of(r3))
                .build();

        return HotelReservationInitiationRequest.builder()
                .hotelId(501L)
                .hotelName("Grand Heritage Hotel")
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .customerId(99L)
                .customerName("Rahul Sharma")
                .customerEmail("rahul@example.com")
                .primaryGuest(PrimaryGuestDto.builder()
                        .fullName("Rahul Sharma")
                        .email("rahul@example.com")
                        .mobile("+919876543210")
                        .build())
                .allocations(List.of(item1, item2))
                .build();
    }

    @Test
    @DisplayName("WP-HB-06.2: Multi-allocation reservation hold creates 1 booking, persists relational guest snapshots, and returns 15m expiresAt")
    void testInitiateHotelReservation_Success() {
        HotelReservationInitiationRequest request = buildValidRequest();

        // Mock hotel-service consolidated reservation response
        Map<String, Object> alloc1 = new HashMap<>();
        alloc1.put("roomId", 101L);
        alloc1.put("roomTypeName", "Deluxe Suite");
        alloc1.put("quantity", 2);
        alloc1.put("allocationTotal", 10000.0);

        Map<String, Object> alloc2 = new HashMap<>();
        alloc2.put("roomId", 102L);
        alloc2.put("roomTypeName", "Standard Double");
        alloc2.put("quantity", 1);
        alloc2.put("allocationTotal", 3000.0);

        Map<String, Object> hotelResponse = new HashMap<>();
        hotelResponse.put("hotelId", 501L);
        hotelResponse.put("status", "HELD");
        hotelResponse.put("totalPrice", 13000.0);
        hotelResponse.put("expiresAt", LocalDateTime.now().plusMinutes(15).toString());
        hotelResponse.put("allocations", List.of(alloc1, alloc2));

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(hotelResponse, HttpStatus.CREATED));

        when(snapshotRepository.save(any(HotelReservationSnapshot.class))).thenAnswer(inv -> {
            HotelReservationSnapshot s = inv.getArgument(0);
            s.setId(2001L);
            return s;
        });

        HotelReservationInitiationResponse response = orchestrationService.initiateHotelReservation(
                request, 99L, "rahul@example.com");

        assertNotNull(response);
        assertNotNull(response.getBookingRef());
        assertTrue(response.getBookingRef().startsWith("BKG-H-"));
        assertEquals("PENDING_PAYMENT", response.getStatus());
        assertEquals(13000.0, response.getTotalAmount());
        assertEquals(501L, response.getHotelId());
        assertEquals("Grand Heritage Hotel", response.getHotelName());
        assertNotNull(response.getExpiresAt());
        assertEquals(2, response.getAllocations().size());
        assertFalse(Boolean.TRUE.equals(response.getIdempotent()));

        // Verify primary guest returned in initiation response
        assertNotNull(response.getPrimaryGuest());
        assertEquals("Rahul Sharma", response.getPrimaryGuest().getFullName());
        assertEquals("rahul@example.com", response.getPrimaryGuest().getEmail());

        // Verify room allocations returned
        assertEquals(2, response.getAllocations().get(0).getRooms().size());
        assertEquals(1, response.getAllocations().get(1).getRooms().size());

        // Verify Hotel Service call has no guest identities
        ArgumentCaptor<HttpEntity> httpCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(1)).postForEntity(contains("/api/hotels/reservations"), httpCaptor.capture(), eq(Map.class));
        Map<String, Object> sentBody = (Map<String, Object>) httpCaptor.getValue().getBody();
        assertNotNull(sentBody.get("expiresAt"), "expiresAt must be passed in payload to Hotel Service");
        assertNull(sentBody.get("primaryGuest"), "Guest identity must NEVER be sent to Hotel Service");

        // Verify relational snapshot persisted with primary guest and cascaded rooms
        ArgumentCaptor<HotelReservationSnapshot> snapshotCaptor = ArgumentCaptor.forClass(HotelReservationSnapshot.class);
        verify(snapshotRepository, times(1)).save(snapshotCaptor.capture());
        HotelReservationSnapshot savedSnapshot = snapshotCaptor.getValue();
        assertEquals("Rahul Sharma", savedSnapshot.getPrimaryGuestName());
        assertEquals("rahul@example.com", savedSnapshot.getPrimaryGuestEmail());
        assertEquals("+919876543210", savedSnapshot.getPrimaryGuestMobile());
        assertEquals(2, savedSnapshot.getAllocations().size());
        assertEquals(2, savedSnapshot.getAllocations().get(0).getRoomAllocations().size());
    }

    @Test
    @DisplayName("WP-HB-06.2: Missing primary guest rejected with 400 Bad Request")
    void testInitiateHotelReservation_MissingPrimaryGuest_Rejected() {
        HotelReservationInitiationRequest request = buildValidRequest();
        request.setPrimaryGuest(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.initiateHotelReservation(request, 99L, "rahul@example.com")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Primary guest information is mandatory"));
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    @DisplayName("WP-HB-06.2: Room type belonging to another hotel rejected with 400 Bad Request")
    void testInitiateHotelReservation_RoomBelongsToOtherHotel_Rejected() {
        HotelReservationInitiationRequest request = buildValidRequest();
        // Room 101 belongs to hotel 999 instead of 501
        mockRoomType(101L, 999L, "ACTIVE", 3);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.initiateHotelReservation(request, 99L, "rahul@example.com")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("does not belong to hotel"));
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    @DisplayName("WP-HB-06.2: Inactive room type rejected with 400 Bad Request")
    void testInitiateHotelReservation_InactiveRoom_Rejected() {
        HotelReservationInitiationRequest request = buildValidRequest();
        mockRoomType(101L, 501L, "ARCHIVED", 3);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.initiateHotelReservation(request, 99L, "rahul@example.com")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("is not active"));
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    @DisplayName("WP-HB-06.2: Occupancy exceeding authoritative maxOccupancy rejected")
    void testInitiateHotelReservation_OccupancyExceedsCapacity_Rejected() {
        HotelReservationInitiationRequest request = buildValidRequest();
        // Set capacity of room 101 to 1, while request has room with 2 adults
        mockRoomType(101L, 501L, "ACTIVE", 1);

        GuestAllocationValidationException ex = assertThrows(GuestAllocationValidationException.class, () ->
                orchestrationService.initiateHotelReservation(request, 99L, "rahul@example.com")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("exceeds max occupancy limit"));
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    @DisplayName("WP-HB-05: Hotel Service inventory conflict rejects reservation initiation without creating booking")
    void testInitiateHotelReservation_HotelServiceConflict() {
        HotelReservationInitiationRequest request = buildValidRequest();

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "Rooms unavailable for selected dates"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.initiateHotelReservation(request, 99L, "rahul@example.com")
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());

        verify(snapshotRepository, never()).save(any(HotelReservationSnapshot.class));
    }

    @Test
    @DisplayName("WP-HB-05: Snapshot persistence failure invokes compensation release on Hotel Service")
    void testInitiateHotelReservation_CompensationReleaseOnPersistenceFailure() {
        HotelReservationInitiationRequest request = buildValidRequest();

        Map<String, Object> alloc = new HashMap<>();
        alloc.put("roomId", 101L);
        alloc.put("roomTypeName", "Deluxe Suite");
        alloc.put("quantity", 2);
        alloc.put("allocationTotal", 10000.0);

        Map<String, Object> hotelResponse = new HashMap<>();
        hotelResponse.put("hotelId", 501L);
        hotelResponse.put("status", "HELD");
        hotelResponse.put("totalPrice", 10000.0);
        hotelResponse.put("allocations", List.of(alloc));

        when(restTemplate.postForEntity(contains("/api/hotels/reservations"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(hotelResponse, HttpStatus.CREATED));

        // Snapshot persistence throws database error
        when(snapshotRepository.save(any(HotelReservationSnapshot.class)))
                .thenThrow(new DataIntegrityViolationException("Database constraint violation"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.initiateHotelReservation(request, 99L, "rahul@example.com")
        );
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());

        // Compensation release MUST be called immediately on hotel-service via exchange
        verify(restTemplate, atLeastOnce()).exchange(
                contains("/api/hotels/rooms/release-stay"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("WP-HB-05: Idempotent initiation returns existing booking and snapshot details")
    void testInitiateHotelReservation_Idempotent() {
        HotelReservationInitiationRequest request = buildValidRequest();
        request.setBookingRef("BK-HTL-EXISTING-123");

        Booking existing = Booking.builder()
                .id(50L)
                .bookingRef("BK-HTL-EXISTING-123")
                .serviceType("HOTEL")
                .status("PENDING_PAYMENT")
                .totalAmount(13000.0)
                .customerId(99L)
                .customerEmail("rahul@example.com")
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        HotelReservationSnapshot snapshot = HotelReservationSnapshot.builder()
                .id(10L)
                .bookingId(50L)
                .bookingRef("BK-HTL-EXISTING-123")
                .hotelId(501L)
                .hotelName("Grand Heritage Hotel")
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .totalNights(2)
                .totalAmount(13000.0)
                .currency("INR")
                .allocations(Collections.emptyList())
                .build();

        when(bookingRepository.findByBookingRef("BK-HTL-EXISTING-123")).thenReturn(Optional.of(existing));
        when(snapshotRepository.findByBookingId(50L)).thenReturn(Optional.of(snapshot));

        HotelReservationInitiationResponse response = orchestrationService.initiateHotelReservation(
                request, 99L, "rahul@example.com");

        assertNotNull(response);
        assertEquals("BK-HTL-EXISTING-123", response.getBookingRef());
        assertTrue(Boolean.TRUE.equals(response.getIdempotent()));
        assertEquals(13000.0, response.getTotalAmount());

        verify(restTemplate, never()).postForEntity(contains("/api/hotels/reservations"), any(), any());
    }

    // ==========================================
    // DETAIL RETRIEVAL TESTS (WP-HB-06.2)
    // ==========================================

    @Test
    @DisplayName("WP-HB-06.2 Retrieval: Authorized traveller retrieves full reservation details with guest allocations")
    void testGetReservationDetails_AuthorizedTraveller_Success() {
        String bookingRef = "BKG-H-TEST-001";
        Booking booking = Booking.builder()
                .id(100L)
                .bookingRef(bookingRef)
                .serviceType("HOTEL")
                .serviceId(501L)
                .serviceTitle("Grand Heritage Hotel")
                .customerId(99L)
                .totalAmount(9000.0)
                .status("CONFIRMED")
                .bookingDate(LocalDateTime.now().minusHours(2))
                .build();

        HotelRoomGuest occ1 = HotelRoomGuest.builder()
                .id(1L)
                .guestName("John Doe")
                .guestType(GuestType.ADULT)
                .build();
        HotelRoomGuest occ2 = HotelRoomGuest.builder()
                .id(2L)
                .guestName("Jane Doe")
                .guestType(GuestType.ADULT)
                .build();
        HotelRoomGuest occ3 = HotelRoomGuest.builder()
                .id(3L)
                .guestName("Child Doe")
                .guestType(GuestType.CHILD)
                .age(5)
                .build();

        HotelRoomGuestAllocation roomAlloc = HotelRoomGuestAllocation.builder()
                .id(10L)
                .roomSequence(1)
                .adultCount(2)
                .childCount(1)
                .occupants(List.of(occ1, occ2, occ3))
                .build();

        HotelAllocationSnapshot allocSnapshot = HotelAllocationSnapshot.builder()
                .id(20L)
                .roomId(101L)
                .roomTypeName("Deluxe Room")
                .quantity(1)
                .allocationTotal(9000.0)
                .nightlyRatesJson("[{\"night\":1,\"rate\":4500.0},{\"night\":2,\"rate\":4500.0}]")
                .status("CONFIRMED")
                .roomAllocations(List.of(roomAlloc))
                .build();

        HotelReservationSnapshot snapshot = HotelReservationSnapshot.builder()
                .id(30L)
                .bookingId(100L)
                .bookingRef(bookingRef)
                .hotelId(501L)
                .hotelName("Grand Heritage Hotel")
                .checkInDate(LocalDate.of(2026, 12, 10))
                .checkOutDate(LocalDate.of(2026, 12, 12))
                .totalNights(2)
                .totalAmount(9000.0)
                .currency("INR")
                .primaryGuestName("John Doe")
                .primaryGuestEmail("john@example.com")
                .primaryGuestMobile("+919876543210")
                .allocations(List.of(allocSnapshot))
                .build();

        when(bookingRepository.findByBookingRef(bookingRef)).thenReturn(Optional.of(booking));
        when(snapshotRepository.findByBookingRef(bookingRef)).thenReturn(Optional.of(snapshot));

        HotelReservationDetailResponse details = orchestrationService.getReservationDetails(
                bookingRef, 99L, "ROLE_TRAVELLER", false);

        assertNotNull(details);
        assertEquals(bookingRef, details.getBookingRef());
        assertEquals("CONFIRMED", details.getBookingStatus());
        assertEquals("PAID", details.getPaymentStatus());
        assertEquals(501L, details.getHotel().getHotelId());
        assertEquals("Grand Heritage Hotel", details.getHotel().getHotelName());
        assertEquals(LocalDate.of(2026, 12, 10), details.getCheckInDate());
        assertEquals(LocalDate.of(2026, 12, 12), details.getCheckOutDate());
        assertEquals(2, details.getTotalNights());
        assertEquals(9000.0, details.getTotalAmount());

        // Primary Guest
        assertNotNull(details.getPrimaryGuest());
        assertEquals("John Doe", details.getPrimaryGuest().getFullName());
        assertEquals("john@example.com", details.getPrimaryGuest().getEmail());
        assertEquals("+919876543210", details.getPrimaryGuest().getMobile());

        // Room allocations and occupants
        assertEquals(1, details.getAllocations().size());
        HotelReservationDetailResponse.AllocationDetailDto allocDto = details.getAllocations().get(0);
        assertEquals(101L, allocDto.getRoomId());
        assertEquals(9000.0, allocDto.getPricing().getAllocationTotal());
        assertEquals(1, allocDto.getRooms().size());

        RoomGuestAllocationDto roomDto = allocDto.getRooms().get(0);
        assertEquals(1, roomDto.getRoomSequence());
        assertEquals(2, roomDto.getAdults());
        assertEquals(1, roomDto.getChildren());
        assertEquals(3, roomDto.getOccupants().size());
        assertEquals("Child Doe", roomDto.getOccupants().get(2).getName());
        assertEquals(5, roomDto.getOccupants().get(2).getAge());
    }

    @Test
    @DisplayName("WP-HB-06.2 Retrieval: Unauthorized traveller accessing another's reservation receives 403 Forbidden")
    void testGetReservationDetails_UnauthorizedTraveller_Forbidden() {
        String bookingRef = "BKG-H-TEST-002";
        Booking booking = Booking.builder()
                .id(101L)
                .bookingRef(bookingRef)
                .serviceType("HOTEL")
                .customerId(99L) // Owned by user 99
                .build();

        when(bookingRepository.findByBookingRef(bookingRef)).thenReturn(Optional.of(booking));

        // User 88 attempts access
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.getReservationDetails(bookingRef, 88L, "ROLE_TRAVELLER", false)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("You do not own this reservation"));
    }

    @Test
    @DisplayName("WP-HB-06.2 Retrieval: Non-existent booking reference returns 404 Not Found")
    void testGetReservationDetails_NotFound() {
        String bookingRef = "BKG-H-NONEXISTENT";
        when(bookingRepository.findByBookingRef(bookingRef)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.getReservationDetails(bookingRef, 99L, "ROLE_TRAVELLER", false)
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("WP-HB-06.2 Retrieval: Historical booking without snapshots returns backward-compatible details without fabricated guest data")
    void testGetReservationDetails_HistoricalBooking_BackwardCompatible() {
        String bookingRef = "BKG-H-LEGACY-003";
        Booking booking = Booking.builder()
                .id(102L)
                .bookingRef(bookingRef)
                .serviceType("HOTEL")
                .serviceId(303L)
                .serviceTitle("Heritage Resort")
                .customerId(99L)
                .checkInDate(LocalDate.of(2025, 5, 1))
                .checkOutDate(LocalDate.of(2025, 5, 4))
                .totalAmount(4500.0)
                .status("CONFIRMED")
                .build();

        when(bookingRepository.findByBookingRef(bookingRef)).thenReturn(Optional.of(booking));
        when(snapshotRepository.findByBookingRef(bookingRef)).thenReturn(Optional.empty());

        HotelReservationDetailResponse details = orchestrationService.getReservationDetails(
                bookingRef, 99L, "ROLE_TRAVELLER", false);

        assertNotNull(details);
        assertEquals(bookingRef, details.getBookingRef());
        assertEquals("CONFIRMED", details.getBookingStatus());
        assertEquals(303L, details.getHotel().getHotelId());
        assertEquals("Heritage Resort", details.getHotel().getHotelName());
        assertEquals(3, details.getTotalNights());
        assertNull(details.getPrimaryGuest(), "Primary guest must be null (not fabricated)");
        assertTrue(details.getAllocations().isEmpty(), "Allocations must be empty for legacy booking");
        assertEquals(4500.0, details.getTotalAmount());
    }
}
