package com.traveleasy.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.dto.*;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.model.HotelAllocationSnapshot;
import com.traveleasy.booking.model.HotelReservationSnapshot;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.repository.HotelAllocationSnapshotRepository;
import com.traveleasy.booking.repository.HotelReservationSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the complete consolidated hotel reservation initiation flow (WP-HB-05).
 * Coordinates parent booking creation, Hotel Service atomic inventory holds, relational
 * snapshot persistence, compensation on failure, and authoritative 15-minute expiry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotelBookingOrchestrationService {

    private final BookingRepository bookingRepository;
    private final HotelReservationSnapshotRepository snapshotRepository;
    private final HotelAllocationSnapshotRepository allocationSnapshotRepository;
    private final BookingInternalTokenService internalTokenService;
    private final ObjectMapper objectMapper;

    private RestTemplate restTemplate = new RestTemplate();

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${hotel.service.url:http://localhost:8083}")
    private String hotelServiceUrl;

    /**
     * Initiates a multi-allocation hotel reservation.
     */
    @Transactional
    public HotelReservationInitiationResponse initiateHotelReservation(
            HotelReservationInitiationRequest request,
            Long authenticatedUserId,
            String authenticatedEmail) {

        // 1. Validation
        validateInitiationRequest(request);

        if (authenticatedUserId != null) {
            request.setCustomerId(authenticatedUserId);
        }
        if (authenticatedEmail != null && (request.getCustomerEmail() == null || request.getCustomerEmail().isBlank())) {
            request.setCustomerEmail(authenticatedEmail);
        }

        // 2. Idempotency Check
        if (request.getBookingRef() != null && !request.getBookingRef().isBlank()) {
            Optional<Booking> existingOpt = bookingRepository.findByBookingRef(request.getBookingRef());
            if (existingOpt.isPresent()) {
                Booking existing = existingOpt.get();
                if ("PENDING_PAYMENT".equalsIgnoreCase(existing.getStatus()) || "CONFIRMED".equalsIgnoreCase(existing.getStatus())) {
                    Optional<HotelReservationSnapshot> snapshotOpt = snapshotRepository.findByBookingId(existing.getId());
                    if (snapshotOpt.isPresent()) {
                        log.info("Idempotent hotel reservation request for existing bookingRef {}", existing.getBookingRef());
                        return toInitiationResponse(existing, snapshotOpt.get(), true);
                    }
                }
            }
        }

        // 3. Generate Booking Reference and Authoritative Expiry Deadline
        String bookingRef = (request.getBookingRef() != null && !request.getBookingRef().isBlank())
                ? request.getBookingRef()
                : "BKG-H-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);

        // 4. Create Initial Parent Booking with status INITIATED
        Booking booking = Booking.builder()
                .bookingRef(bookingRef)
                .customerId(request.getCustomerId())
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .serviceType("HOTEL")
                .serviceId(request.getHotelId())
                .serviceTitle(request.getHotelName() != null ? request.getHotelName() : "Hotel Reservation")
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .totalAmount(0.0) // Authoritative total populated after Hotel Service confirmation
                .status("INITIATED")
                .expiresAt(expiresAt)
                .bookingDate(LocalDateTime.now())
                .detailsJson(request.getGuestDetailsJson())
                .build();

        booking = bookingRepository.save(booking);
        log.info("Created initial INITIATED booking record {} for hotel {}", bookingRef, request.getHotelId());

        // 5. Call Hotel Service Consolidated Reservation Endpoint
        Map<String, Object> hotelResponseMap;
        try {
            hotelResponseMap = callHotelServiceConsolidatedReservation(bookingRef, request, expiresAt);
        } catch (Exception e) {
            log.error("Hotel service consolidated reservation failed for bookingRef {}: {}", bookingRef, e.getMessage());
            booking.setStatus("FAILED");
            bookingRepository.save(booking);

            if (e instanceof ResponseStatusException rse) {
                throw rse;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hotel inventory hold failed: " + extractErrorMessage(e));
        }

        // 6. Process Authoritative Hotel Service Result
        Double authoritativeTotal = extractAuthoritativeTotal(hotelResponseMap);
        List<Map<String, Object>> allocationMaps = extractAllocationList(hotelResponseMap);

        booking.setTotalAmount(authoritativeTotal);
        booking.setStatus("PENDING_PAYMENT");

        // 7. Persist Relational Reservation and Allocation Snapshots
        HotelReservationSnapshot snapshot;
        try {
            snapshot = buildAndSaveSnapshot(booking, request, authoritativeTotal, allocationMaps);
        } catch (Exception e) {
            log.error("Failed to persist relational reservation snapshot for bookingRef {}. Executing compensation release: {}",
                    bookingRef, e.getMessage());
            // Compensation action: Release hold from Hotel Service so inventory is not locked indefinitely
            releaseHotelInventoryHold(bookingRef);
            booking.setStatus("FAILED");
            bookingRepository.save(booking);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to record reservation snapshot. Inventory hold has been safely released.");
        }

        Booking savedBooking = bookingRepository.save(booking);
        log.info("Successfully persisted hotel reservation snapshot and transitioned booking {} to PENDING_PAYMENT", bookingRef);

        return toInitiationResponse(savedBooking, snapshot, false);
    }

    private void validateInitiationRequest(HotelReservationInitiationRequest request) {
        if (request.getHotelId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hotel ID is mandatory");
        }
        if (request.getCheckInDate() == null || request.getCheckOutDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-in and check-out dates are mandatory");
        }
        if (!request.getCheckInDate().isBefore(request.getCheckOutDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-in date must be strictly before check-out date");
        }
        if (request.getAllocations() == null || request.getAllocations().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one room allocation is required");
        }
        for (ReservationAllocationItemDto alloc : request.getAllocations()) {
            if (alloc.getRoomId() == null || alloc.getQuantity() == null || alloc.getQuantity() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room allocation: roomId and positive quantity required");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callHotelServiceConsolidatedReservation(
            String bookingRef, HotelReservationInitiationRequest request, LocalDateTime expiresAt) {

        String url = hotelServiceUrl + "/api/hotels/reservations";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String internalToken = internalTokenService.generateInternalToken();
        headers.set("Authorization", "Bearer " + internalToken);
        headers.set("X-Internal-Token", internalToken);
        headers.set("X-Internal-Service", "booking-service");

        Map<String, Object> body = new HashMap<>();
        body.put("bookingRef", bookingRef);
        body.put("hotelId", request.getHotelId());
        body.put("checkInDate", request.getCheckInDate().toString());
        body.put("checkOutDate", request.getCheckOutDate().toString());
        if (expiresAt != null) {
            body.put("expiresAt", expiresAt.toString());
        }

        List<Map<String, Object>> allocList = request.getAllocations().stream()
                .map(a -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("roomId", a.getRoomId());
                    m.put("quantity", a.getQuantity());
                    return m;
                })
                .collect(Collectors.toList());
        body.put("allocations", allocList);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hotel Service returned status: " + response.getStatusCode());
            }
            return (Map<String, Object>) response.getBody();
        } catch (HttpStatusCodeException hsce) {
            String errorBody = hsce.getResponseBodyAsString();
            log.warn("Hotel service rejected reservation for bookingRef {}: {} - {}", bookingRef, hsce.getStatusCode(), errorBody);
            throw new ResponseStatusException(hsce.getStatusCode(), "Hotel Service reservation failed: " + errorBody);
        }
    }

    private Double extractAuthoritativeTotal(Map<String, Object> hotelResponseMap) {
        Object totalObj = hotelResponseMap.get("totalPrice");
        if (totalObj instanceof Number) {
            return ((Number) totalObj).doubleValue();
        } else if (totalObj != null) {
            try {
                return Double.parseDouble(totalObj.toString());
            } catch (Exception ignored) {}
        }
        throw new IllegalStateException("Authoritative total price missing from Hotel Service reservation response");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractAllocationList(Map<String, Object> hotelResponseMap) {
        Object allocsObj = hotelResponseMap.get("allocations");
        if (allocsObj instanceof List<?>) {
            return (List<Map<String, Object>>) allocsObj;
        }
        return Collections.emptyList();
    }

    private HotelReservationSnapshot buildAndSaveSnapshot(
            Booking booking,
            HotelReservationInitiationRequest request,
            Double authoritativeTotal,
            List<Map<String, Object>> allocationMaps) {

        int nights = (int) ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());

        HotelReservationSnapshot snapshot = HotelReservationSnapshot.builder()
                .bookingId(booking.getId())
                .bookingRef(booking.getBookingRef())
                .hotelId(request.getHotelId())
                .hotelName(request.getHotelName())
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .totalNights(nights)
                .totalAmount(authoritativeTotal)
                .currency("INR")
                .status("HELD")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .allocations(new ArrayList<>())
                .build();

        for (Map<String, Object> allocMap : allocationMaps) {
            Long roomId = allocMap.get("roomId") != null ? Long.valueOf(allocMap.get("roomId").toString()) : null;
            String roomTypeName = allocMap.get("roomTypeName") != null ? allocMap.get("roomTypeName").toString() : "Room";
            Integer quantity = allocMap.get("quantity") != null ? Integer.valueOf(allocMap.get("quantity").toString()) : 1;
            Double allocTotal = allocMap.get("allocationTotal") != null ? Double.valueOf(allocMap.get("allocationTotal").toString()) : 0.0;

            String nightlyRatesJson = null;
            Object ratesObj = allocMap.get("nightlyPriceBreakdown");
            if (ratesObj != null) {
                try {
                    nightlyRatesJson = objectMapper.writeValueAsString(ratesObj);
                } catch (JsonProcessingException e) {
                    nightlyRatesJson = ratesObj.toString();
                }
            }

            HotelAllocationSnapshot childAlloc = HotelAllocationSnapshot.builder()
                    .reservationSnapshot(snapshot)
                    .roomId(roomId)
                    .roomTypeName(roomTypeName)
                    .quantity(quantity)
                    .allocationTotal(allocTotal)
                    .nightlyRatesJson(nightlyRatesJson)
                    .checkInDate(request.getCheckInDate())
                    .checkOutDate(request.getCheckOutDate())
                    .status("HELD")
                    .guestAllocationJson(request.getGuestDetailsJson())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            snapshot.addAllocation(childAlloc);
        }

        return snapshotRepository.save(snapshot);
    }

    public void releaseHotelInventoryHold(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) {
            return;
        }
        try {
            String stayUrl = hotelServiceUrl + "/api/hotels/rooms/release-stay?bookingRef=" + bookingRef;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String internalToken = internalTokenService.generateInternalToken();
            headers.set("Authorization", "Bearer " + internalToken);
            headers.set("X-Internal-Token", internalToken);
            headers.set("X-Internal-Service", "booking-service");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.exchange(stayUrl, HttpMethod.POST, entity, Map.class);
            log.info("Compensated and released hotel stay inventory for bookingRef {}", bookingRef);
        } catch (Exception ex) {
            log.warn("Failed to release hotel inventory hold during compensation for bookingRef {}: {}",
                    bookingRef, ex.getMessage());
        }
    }

    public HotelReservationInitiationResponse toInitiationResponse(
            Booking booking, HotelReservationSnapshot snapshot, boolean isIdempotent) {

        List<HotelReservationInitiationResponse.AllocationSnapshotDto> allocDtos = snapshot.getAllocations() != null
                ? snapshot.getAllocations().stream().map(a -> HotelReservationInitiationResponse.AllocationSnapshotDto.builder()
                        .allocationId(a.getId())
                        .roomId(a.getRoomId())
                        .roomTypeName(a.getRoomTypeName())
                        .quantity(a.getQuantity())
                        .allocationTotal(a.getAllocationTotal())
                        .nightlyRatesJson(a.getNightlyRatesJson())
                        .status(a.getStatus())
                        .build())
                .collect(Collectors.toList())
                : Collections.emptyList();

        return HotelReservationInitiationResponse.builder()
                .bookingId(booking.getId())
                .bookingRef(booking.getBookingRef())
                .status(booking.getStatus())
                .totalAmount(booking.getTotalAmount())
                .currency(snapshot.getCurrency())
                .expiresAt(booking.getExpiresAt())
                .hotelId(snapshot.getHotelId())
                .hotelName(snapshot.getHotelName())
                .checkInDate(snapshot.getCheckInDate())
                .checkOutDate(snapshot.getCheckOutDate())
                .totalNights(snapshot.getTotalNights())
                .idempotent(isIdempotent)
                .allocations(allocDtos)
                .build();
    }

    private String extractErrorMessage(Exception e) {
        if (e.getCause() != null) {
            return e.getCause().getMessage();
        }
        return e.getMessage();
    }
}
