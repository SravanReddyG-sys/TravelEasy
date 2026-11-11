package com.traveleasy.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.dto.*;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.model.GuestType;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
public class HotelReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private BookingSecurityService securityService;

    @MockBean
    private BookingExpiryReconciliationService expiryService;

    @MockBean
    private ScheduleCancellationCoordinatorService coordinatorService;

    @MockBean
    private BookingStateTransitionService stateTransitionService;

    @MockBean
    private BookingInternalTokenService internalTokenService;

    @MockBean
    private HotelCancellationPolicyService cancellationPolicyService;

    @MockBean
    private HotelGuestLifecycleService guestLifecycleService;

    @MockBean
    private HotelBookingOrchestrationService hotelOrchestrationService;

    @MockBean
    private com.traveleasy.booking.repository.HotelReservationSnapshotRepository snapshotRepository;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        when(securityService.validateToken("valid-traveller-token")).thenReturn(true);
        when(securityService.extractUserId("valid-traveller-token")).thenReturn(99L);
        when(securityService.extractRole("valid-traveller-token")).thenReturn("ROLE_TRAVELLER");
        when(securityService.extractEmail("valid-traveller-token")).thenReturn("traveller@example.com");

        when(securityService.validateToken("manager-token")).thenReturn(true);
        when(securityService.extractUserId("manager-token")).thenReturn(200L);
        when(securityService.extractRole("manager-token")).thenReturn("ROLE_HOTEL_MANAGER");

        when(securityService.validateToken("invalid-token")).thenReturn(false);
    }

    private HotelReservationInitiationRequest buildSampleRequest() {
        RoomGuestAllocationDto r1 = RoomGuestAllocationDto.builder()
                .roomSequence(1)
                .adults(2)
                .children(0)
                .occupants(List.of(
                        RoomOccupantDto.builder().name("Adult One").type(GuestType.ADULT).age(33).gender(com.traveleasy.booking.model.GuestGender.MALE).build(),
                        RoomOccupantDto.builder().name("Adult Two").type(GuestType.ADULT).age(29).gender(com.traveleasy.booking.model.GuestGender.FEMALE).build()
                ))
                .build();

        ReservationAllocationItemDto item = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(r1))
                .build();

        return HotelReservationInitiationRequest.builder()
                .hotelId(501L)
                .hotelName("Grand Heritage Hotel")
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .primaryGuest(PrimaryGuestDto.builder()
                        .fullName("Rahul Sharma")
                        .email("rahul@example.com")
                        .mobile("+919876543210")
                        .build())
                .allocations(List.of(item))
                .build();
    }

    @Test
    @DisplayName("POST /api/bookings/hotel-reservations: Successful initiation returns 201 Created")
    void testInitiateHotelReservation_Success() throws Exception {
        HotelReservationInitiationRequest request = buildSampleRequest();

        HotelReservationInitiationResponse response = HotelReservationInitiationResponse.builder()
                .bookingId(1001L)
                .bookingRef("BKG-H-SUCCESS1")
                .status("PENDING_PAYMENT")
                .totalAmount(9000.0)
                .currency("INR")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .hotelId(501L)
                .hotelName("Grand Heritage Hotel")
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .totalNights(2)
                .idempotent(false)
                .primaryGuest(request.getPrimaryGuest())
                .allocations(List.of(
                        HotelReservationInitiationResponse.AllocationSnapshotDto.builder()
                                .allocationId(50L)
                                .roomId(101L)
                                .roomTypeName("Deluxe King")
                                .quantity(1)
                                .allocationTotal(9000.0)
                                .status("HELD")
                                .rooms(request.getAllocations().get(0).getRooms())
                                .build()
                ))
                .build();

        when(hotelOrchestrationService.initiateHotelReservation(any(), eq(99L), eq("traveller@example.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/bookings/hotel-reservations")
                        .header("Authorization", "Bearer valid-traveller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingRef").value("BKG-H-SUCCESS1"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.primaryGuest.fullName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.allocations[0].rooms[0].occupants[0].name").value("Adult One"));
    }

    @Test
    @DisplayName("POST /api/bookings/hotel-reservations: Hotel manager prohibited from booking customer tickets (403)")
    void testInitiateHotelReservation_ManagerAccount_Forbidden() throws Exception {
        HotelReservationInitiationRequest request = buildSampleRequest();

        mockMvc.perform(post("/api/bookings/hotel-reservations")
                        .header("Authorization", "Bearer manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Management accounts are prohibited from booking customer tickets."));
    }

    @Test
    @DisplayName("POST /api/bookings/hotel-reservations: Invalid token returns 401 Unauthorized")
    void testInitiateHotelReservation_InvalidToken_Unauthorized() throws Exception {
        HotelReservationInitiationRequest request = buildSampleRequest();

        mockMvc.perform(post("/api/bookings/hotel-reservations")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired authorization token"));
    }

    @Test
    @DisplayName("POST /api/bookings/hotel-reservations: Validation failure returns 400 Bad Request")
    void testInitiateHotelReservation_ValidationFailure_BadRequest() throws Exception {
        HotelReservationInitiationRequest request = buildSampleRequest();

        when(hotelOrchestrationService.initiateHotelReservation(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total occupants exceeds max occupancy limit"));

        mockMvc.perform(post("/api/bookings/hotel-reservations")
                        .header("Authorization", "Bearer valid-traveller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Total occupants exceeds max occupancy limit"));
    }

    @Test
    @DisplayName("GET /api/bookings/ref/{bookingRef}/hotel-reservation-details: Authorized traveller returns 200 OK")
    void testGetHotelReservationDetails_Authorized_Success() throws Exception {
        String bookingRef = "BKG-H-DETAIL-001";

        HotelReservationDetailResponse detailResponse = HotelReservationDetailResponse.builder()
                .bookingId(101L)
                .bookingRef(bookingRef)
                .bookingStatus("CONFIRMED")
                .hotel(HotelReservationDetailResponse.HotelSummaryDto.builder()
                        .hotelId(501L)
                        .hotelName("Grand Heritage Hotel")
                        .build())
                .checkInDate(LocalDate.of(2026, 12, 10))
                .checkOutDate(LocalDate.of(2026, 12, 12))
                .totalNights(2)
                .totalAmount(9000.0)
                .currency("INR")
                .paymentStatus("PAID")
                .primaryGuest(PrimaryGuestDto.builder()
                        .fullName("Rahul Sharma")
                        .email("rahul@example.com")
                        .mobile("+919876543210")
                        .build())
                .allocations(List.of(
                        HotelReservationDetailResponse.AllocationDetailDto.builder()
                                .allocationId(10L)
                                .roomId(101L)
                                .roomTypeName("Deluxe King")
                                .quantity(1)
                                .pricing(HotelReservationDetailResponse.AllocationPricingDto.builder()
                                        .allocationTotal(9000.0)
                                        .build())
                                .status("CONFIRMED")
                                .rooms(List.of(
                                        RoomGuestAllocationDto.builder()
                                                .roomSequence(1)
                                                .adults(2)
                                                .children(0)
                                                .occupants(List.of(
                                                        RoomOccupantDto.builder().name("Adult One").type(GuestType.ADULT).age(33).gender(com.traveleasy.booking.model.GuestGender.MALE).build(),
                                                        RoomOccupantDto.builder().name("Adult Two").type(GuestType.ADULT).age(29).gender(com.traveleasy.booking.model.GuestGender.FEMALE).build()
                                                ))
                                                .build()
                                ))
                                .build()
                ))
                .build();

        when(hotelOrchestrationService.getReservationDetails(eq(bookingRef), eq(99L), eq("ROLE_TRAVELLER"), eq(false)))
                .thenReturn(detailResponse);

        mockMvc.perform(get("/api/bookings/ref/{bookingRef}/hotel-reservation-details", bookingRef)
                        .header("Authorization", "Bearer valid-traveller-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingRef").value(bookingRef))
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.hotel.hotelName").value("Grand Heritage Hotel"))
                .andExpect(jsonPath("$.primaryGuest.fullName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.allocations[0].rooms[0].occupants[0].name").value("Adult One"))
                .andExpect(jsonPath("$.allocations[0].rooms[0].occupants[0].age").value(33))
                .andExpect(jsonPath("$.allocations[0].rooms[0].occupants[0].gender").value("MALE"));
    }

    @Test
    @DisplayName("GET /api/bookings/ref/{bookingRef}/hotel-reservation-details: Unauthorized caller receives 403 Forbidden")
    void testGetHotelReservationDetails_UnauthorizedTraveller_Forbidden() throws Exception {
        String bookingRef = "BKG-H-DETAIL-002";

        when(hotelOrchestrationService.getReservationDetails(eq(bookingRef), eq(99L), eq("ROLE_TRAVELLER"), eq(false)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You do not own this reservation"));

        mockMvc.perform(get("/api/bookings/ref/{bookingRef}/hotel-reservation-details", bookingRef)
                        .header("Authorization", "Bearer valid-traveller-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: You do not own this reservation"));
    }

    @Test
    @DisplayName("GET /api/bookings/ref/{bookingRef}/hotel-reservation-details: Missing token returns 401 Unauthorized")
    void testGetHotelReservationDetails_MissingAuth_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/bookings/ref/{bookingRef}/hotel-reservation-details", "BKG-H-ANY"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authorization token required"));
    }

    @Test
    @DisplayName("GET /api/bookings/ref/{bookingRef}/hotel-reservation-details: Not found returns 404")
    void testGetHotelReservationDetails_NotFound() throws Exception {
        String bookingRef = "BKG-H-UNKNOWN";

        when(hotelOrchestrationService.getReservationDetails(eq(bookingRef), eq(99L), eq("ROLE_TRAVELLER"), eq(false)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found for reference: " + bookingRef));

        mockMvc.perform(get("/api/bookings/ref/{bookingRef}/hotel-reservation-details", bookingRef)
                        .header("Authorization", "Bearer valid-traveller-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Booking not found for reference: " + bookingRef));
    }

    @Test
    @DisplayName("POST /api/bookings/hotel-reservations: Validation exception returns structured errors and fieldErrors")
    void testInitiateHotelReservation_ValidationFailure_ReturnsStructuredFieldErrors() throws Exception {
        HotelReservationInitiationRequest request = buildSampleRequest();

        when(hotelOrchestrationService.initiateHotelReservation(any(), eq(99L), eq("traveller@example.com")))
                .thenThrow(new com.traveleasy.booking.validator.GuestAllocationValidationException(
                        List.of("Room 101 - Room 1, Guest 1: Gender is mandatory for ADULT occupants."),
                        List.of(new com.traveleasy.booking.validator.GuestAllocationValidationException.ValidationErrorDetail(
                                0, 101L, 1, 0, "gender", "Gender is mandatory for ADULT occupants."))));

        mockMvc.perform(post("/api/bookings/hotel-reservations")
                        .header("Authorization", "Bearer valid-traveller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("Room 101 - Room 1, Guest 1: Gender is mandatory for ADULT occupants."))
                .andExpect(jsonPath("$.fieldErrors[0].roomSequence").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].guestIndex").value(0))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("gender"));
    }

    @Test
    @DisplayName("POST /api/bookings/hotel-reservations: Invalid enum gender value returns 400 Bad Request")
    void testInitiateHotelReservation_InvalidGenderJson_ReturnsBadRequest() throws Exception {
        String rawJson = objectMapper.writeValueAsString(buildSampleRequest())
                .replace("\"MALE\"", "\"INVALID_GENDER_VALUE\"");

        mockMvc.perform(post("/api/bookings/hotel-reservations")
                        .header("Authorization", "Bearer valid-traveller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest());
    }
}
