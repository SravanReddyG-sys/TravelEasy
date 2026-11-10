package com.traveleasy.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.traveleasy.booking.dto.HotelReservationInitiationRequest;
import com.traveleasy.booking.dto.HotelReservationInitiationResponse;
import com.traveleasy.booking.dto.ReservationAllocationItemDto;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.model.HotelAllocationSnapshot;
import com.traveleasy.booking.model.HotelReservationSnapshot;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.repository.HotelAllocationSnapshotRepository;
import com.traveleasy.booking.repository.HotelReservationSnapshotRepository;
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
    private BookingInternalTokenService internalTokenService;

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private HotelBookingOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        orchestrationService = new HotelBookingOrchestrationService(
                bookingRepository,
                snapshotRepository,
                allocationSnapshotRepository,
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
    }

    private HotelReservationInitiationRequest buildValidRequest() {
        ReservationAllocationItemDto item1 = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(2)
                .build();

        ReservationAllocationItemDto item2 = ReservationAllocationItemDto.builder()
                .roomId(102L)
                .quantity(1)
                .build();

        return HotelReservationInitiationRequest.builder()
                .hotelId(501L)
                .hotelName("Grand Heritage Hotel")
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .customerId(99L)
                .customerName("Rahul Sharma")
                .customerEmail("rahul@example.com")
                .allocations(List.of(item1, item2))
                .build();
    }

    @Test
    @DisplayName("WP-HB-05: Multi-allocation reservation hold creates 1 booking, persists snapshots, and returns 15m expiresAt")
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

        // Verify Hotel Service call
        ArgumentCaptor<HttpEntity> httpCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(1)).postForEntity(contains("/api/hotels/reservations"), httpCaptor.capture(), eq(Map.class));
        Map<String, Object> sentBody = (Map<String, Object>) httpCaptor.getValue().getBody();
        assertNotNull(sentBody.get("expiresAt"), "expiresAt must be passed in payload to Hotel Service");

        // Verify Booking entity persisted with expiresAt
        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository, atLeastOnce()).save(bookingCaptor.capture());
        Booking savedBooking = bookingCaptor.getValue();
        assertEquals("HOTEL", savedBooking.getServiceType());
        assertEquals("PENDING_PAYMENT", savedBooking.getStatus());
        assertEquals(13000.0, savedBooking.getTotalAmount());
        assertNotNull(savedBooking.getExpiresAt());

        // Verify relational snapshot persistence with cascaded allocations
        ArgumentCaptor<HotelReservationSnapshot> snapshotCaptor = ArgumentCaptor.forClass(HotelReservationSnapshot.class);
        verify(snapshotRepository, times(1)).save(snapshotCaptor.capture());
        HotelReservationSnapshot savedSnapshot = snapshotCaptor.getValue();
        assertEquals(2, savedSnapshot.getAllocations().size());
        assertEquals(13000.0, savedSnapshot.getTotalAmount());
    }

    @Test
    @DisplayName("WP-HB-05: Hotel Service inventory conflict rejects reservation initiation without creating booking")
    void testInitiateHotelReservation_HotelServiceConflict() {
        HotelReservationInitiationRequest request = buildValidRequest();

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT, "Rooms unavailable for selected dates"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            orchestrationService.initiateHotelReservation(request, 99L, "rahul@example.com");
        });
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

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            orchestrationService.initiateHotelReservation(request, 99L, "rahul@example.com");
        });
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
                .totalAmount(13000.0)
                .status("HELD")
                .allocations(new ArrayList<>())
                .build();

        when(bookingRepository.findByBookingRef("BK-HTL-EXISTING-123")).thenReturn(Optional.of(existing));
        when(snapshotRepository.findByBookingId(50L)).thenReturn(Optional.of(snapshot));

        HotelReservationInitiationResponse response = orchestrationService.initiateHotelReservation(
                request, 99L, "rahul@example.com");

        assertNotNull(response);
        assertEquals("BK-HTL-EXISTING-123", response.getBookingRef());
        assertTrue(Boolean.TRUE.equals(response.getIdempotent()));
        assertEquals("PENDING_PAYMENT", response.getStatus());

        // Hotel Service consolidated hold must NOT be called for duplicate
        verify(restTemplate, never()).postForEntity(contains("/api/hotels/reservations"), any(), any());
    }

    @Test
    @DisplayName("WP-HB-05: Validation rejects invalid hotel reservation inputs")
    void testInitiateHotelReservation_ValidationFailures() {
        // Missing hotelId
        HotelReservationInitiationRequest req1 = buildValidRequest();
        req1.setHotelId(null);
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.initiateHotelReservation(req1, 99L, "test@test.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        // Empty allocations
        HotelReservationInitiationRequest req2 = buildValidRequest();
        req2.setAllocations(Collections.emptyList());
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.initiateHotelReservation(req2, 99L, "test@test.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());

        // CheckOut before CheckIn
        HotelReservationInitiationRequest req3 = buildValidRequest();
        req3.setCheckInDate(LocalDate.now().plusDays(5));
        req3.setCheckOutDate(LocalDate.now().plusDays(3));
        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.initiateHotelReservation(req3, 99L, "test@test.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex3.getStatusCode());

        // Allocation with non-positive quantity
        ReservationAllocationItemDto badAlloc = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(0)
                .build();
        HotelReservationInitiationRequest req4 = buildValidRequest();
        req4.setAllocations(List.of(badAlloc));
        ResponseStatusException ex4 = assertThrows(ResponseStatusException.class, () ->
                orchestrationService.initiateHotelReservation(req4, 99L, "test@test.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex4.getStatusCode());
    }
}
