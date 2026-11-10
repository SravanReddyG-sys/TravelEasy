package com.traveleasy.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.dto.*;
import com.traveleasy.booking.model.*;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.repository.HotelAllocationSnapshotRepository;
import com.traveleasy.booking.repository.HotelReservationSnapshotRepository;
import com.traveleasy.booking.repository.HotelRoomGuestAllocationRepository;
import com.traveleasy.booking.repository.HotelRoomGuestRepository;
import com.traveleasy.booking.validator.GuestAllocationValidationException;
import com.traveleasy.booking.validator.HotelGuestAllocationValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Orchestrates the complete consolidated hotel reservation initiation flow (WP-HB-05, WP-HB-06.2).
 * Coordinates parent booking creation, Hotel Service atomic inventory holds, room capacity validation,
 * relational guest allocation persistence, compensation on failure, and authoritative reservation retrieval.
 */
@Slf4j
@Service
public class HotelBookingOrchestrationService {

    private final BookingRepository bookingRepository;
    private final HotelReservationSnapshotRepository snapshotRepository;
    private final HotelAllocationSnapshotRepository allocationSnapshotRepository;
    private final HotelRoomGuestAllocationRepository roomAllocationRepository;
    private final HotelRoomGuestRepository guestRepository;
    private final HotelGuestAllocationValidator guestAllocationValidator;
    private final BookingInternalTokenService internalTokenService;
    private final ObjectMapper objectMapper;

    private RestTemplate restTemplate = new RestTemplate();

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${hotel.service.url:http://localhost:8083}")
    private String hotelServiceUrl;

    public void setHotelServiceUrl(String hotelServiceUrl) {
        this.hotelServiceUrl = hotelServiceUrl;
    }

    @Autowired
    public HotelBookingOrchestrationService(
            BookingRepository bookingRepository,
            HotelReservationSnapshotRepository snapshotRepository,
            HotelAllocationSnapshotRepository allocationSnapshotRepository,
            @Autowired(required = false) HotelRoomGuestAllocationRepository roomAllocationRepository,
            @Autowired(required = false) HotelRoomGuestRepository guestRepository,
            @Autowired(required = false) HotelGuestAllocationValidator guestAllocationValidator,
            BookingInternalTokenService internalTokenService,
            ObjectMapper objectMapper) {
        this.bookingRepository = bookingRepository;
        this.snapshotRepository = snapshotRepository;
        this.allocationSnapshotRepository = allocationSnapshotRepository;
        this.roomAllocationRepository = roomAllocationRepository;
        this.guestRepository = guestRepository;
        this.guestAllocationValidator = guestAllocationValidator != null ? guestAllocationValidator : new HotelGuestAllocationValidator();
        this.internalTokenService = internalTokenService;
        this.objectMapper = objectMapper;
    }

    /**
     * Backward-compatible constructor for testing and legacy instantiation.
     */
    public HotelBookingOrchestrationService(
            BookingRepository bookingRepository,
            HotelReservationSnapshotRepository snapshotRepository,
            HotelAllocationSnapshotRepository allocationSnapshotRepository,
            BookingInternalTokenService internalTokenService,
            ObjectMapper objectMapper) {
        this(bookingRepository, snapshotRepository, allocationSnapshotRepository,
                null, null, new HotelGuestAllocationValidator(), internalTokenService, objectMapper);
    }

    /**
     * Initiates a multi-allocation hotel reservation with structured guest allocation (WP-HB-06.2).
     */
    @Transactional
    public HotelReservationInitiationResponse initiateHotelReservation(
            HotelReservationInitiationRequest request,
            Long authenticatedUserId,
            String authenticatedEmail) {

        // 1. Basic Request Validation
        validateInitiationRequest(request);

        // 2. Validate Primary Guest
        if (request.getPrimaryGuest() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Primary guest information is mandatory");
        }
        guestAllocationValidator.validatePrimaryGuest(request.getPrimaryGuest());

        // Authenticated customer identity binding
        if (authenticatedUserId != null) {
            request.setCustomerId(authenticatedUserId);
        }
        if (authenticatedEmail != null && (request.getCustomerEmail() == null || request.getCustomerEmail().isBlank())) {
            request.setCustomerEmail(authenticatedEmail);
        }

        // 3. Authoritative Capacity Validation via Hotel Service
        Map<Long, Integer> roomCapacities = fetchAndValidateAuthoritativeCapacities(
                request.getHotelId(), request.getAllocations());

        // Validate physical room allocations, sequencing, and occupancy against room capacities
        guestAllocationValidator.validateAllocations(request.getAllocations(), roomCapacities);

        // 4. Idempotency Check
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

        // 5. Generate Booking Reference and Authoritative Expiry Deadline
        String bookingRef = (request.getBookingRef() != null && !request.getBookingRef().isBlank())
                ? request.getBookingRef()
                : "BKG-H-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);

        // 6. Create Initial Parent Booking with status INITIATED
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
                .totalAmount(0.0) // Populated authoritatively from Hotel Service response
                .status("INITIATED")
                .expiresAt(expiresAt)
                .bookingDate(LocalDateTime.now())
                .detailsJson(request.getGuestDetailsJson())
                .build();

        booking = bookingRepository.save(booking);
        log.info("Created initial INITIATED booking record {} for hotel {}", bookingRef, request.getHotelId());

        // 7. Call Hotel Service Consolidated Reservation Endpoint (Zero-Knowledge of Guest Identities)
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

        // 8. Process Authoritative Hotel Service Result
        Double authoritativeTotal = extractAuthoritativeTotal(hotelResponseMap);
        List<Map<String, Object>> allocationMaps = extractAllocationList(hotelResponseMap);

        booking.setTotalAmount(authoritativeTotal);
        booking.setStatus("PENDING_PAYMENT");

        // 9. Persist Relational Reservation and Structured Guest Allocation Snapshots
        HotelReservationSnapshot snapshot;
        try {
            snapshot = buildAndSaveSnapshot(booking, request, authoritativeTotal, allocationMaps);
        } catch (Exception e) {
            log.error("Failed to persist relational reservation snapshot for bookingRef {}. Executing compensation release: {}",
                    bookingRef, e.getMessage());
            // Compensation action: Release hold from Hotel Service so inventory is never held without a valid snapshot
            releaseHotelInventoryHold(bookingRef);
            booking.setStatus("FAILED");
            bookingRepository.save(booking);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to record reservation snapshot. Inventory hold has been safely released.");
        }

        Booking savedBooking = bookingRepository.save(booking);
        log.info("Successfully persisted hotel reservation snapshot with guest allocations for booking {}", bookingRef);

        return toInitiationResponse(savedBooking, snapshot, false);
    }

    /**
     * Retrieves detailed reservation information with guest allocations and immutable pricing (WP-HB-06.2).
     */
    @Transactional(readOnly = true)
    public HotelReservationDetailResponse getReservationDetails(
            String bookingRef, Long callerUserId, String callerRole, boolean isInternal) {

        if (bookingRef == null || bookingRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking reference is required");
        }

        Booking booking = bookingRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Booking not found for reference: " + bookingRef));

        if (!"HOTEL".equalsIgnoreCase(booking.getServiceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Booking reference is not a hotel reservation: " + bookingRef);
        }

        // Authorization check
        if (!isInternal) {
            boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole) || "ADMIN".equalsIgnoreCase(callerRole);
            boolean isHotelManager = "ROLE_HOTEL_MANAGER".equalsIgnoreCase(callerRole) || "HOTEL_MANAGER".equalsIgnoreCase(callerRole);

            if (!isAdmin) {
                if (isHotelManager) {
                    if (callerUserId == null || booking.getProviderId() == null || !callerUserId.equals(booking.getProviderId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Access denied: You are not authorized to view reservations for this hotel");
                    }
                } else {
                    // Traveller authorization: must own booking
                    if (callerUserId == null || booking.getCustomerId() == null || !callerUserId.equals(booking.getCustomerId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Access denied: You do not own this reservation");
                    }
                }
            }
        }

        Optional<HotelReservationSnapshot> snapshotOpt = snapshotRepository.findByBookingRef(bookingRef);
        if (snapshotOpt.isEmpty() && booking.getId() != null) {
            snapshotOpt = snapshotRepository.findByBookingId(booking.getId());
        }

        if (snapshotOpt.isPresent()) {
            return mapSnapshotToDetailResponse(booking, snapshotOpt.get());
        } else {
            // Historical fallback: Return valid response using Booking attributes without fabricating guest data
            return mapLegacyBookingToDetailResponse(booking);
        }
    }

    /**
     * Authoritatively fetches room type details from Hotel Service, validates ownership and active status,
     * and extracts current maxOccupancy limits.
     */
    private Map<Long, Integer> fetchAndValidateAuthoritativeCapacities(
            Long expectedHotelId, List<ReservationAllocationItemDto> allocations) {

        Set<Long> distinctRoomIds = allocations.stream()
                .map(ReservationAllocationItemDto::getRoomId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Integer> capacityMap = new HashMap<>();

        for (Long roomId : distinctRoomIds) {
            String url = hotelServiceUrl + "/api/hotels/rooms/" + roomId;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String internalToken = internalTokenService.generateInternalToken();
            headers.set("Authorization", "Bearer " + internalToken);
            headers.set("X-Internal-Token", internalToken);
            headers.set("X-Internal-Service", "booking-service");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response;
            try {
                response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            } catch (HttpStatusCodeException hsce) {
                if (hsce.getStatusCode() == HttpStatus.NOT_FOUND) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room category not found with ID: " + roomId);
                }
                log.warn("Failed to fetch room type {} from hotel service: {}", roomId, hsce.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to verify room category ID: " + roomId);
            } catch (Exception e) {
                log.error("Network or unexpected error while fetching room type {}: {}", roomId, e.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to verify room category ID: " + roomId);
            }

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room category not found with ID: " + roomId);
            }

            Map<String, Object> roomData = (Map<String, Object>) response.getBody();

            // 1. Verify room type belongs to selected hotel
            Object hotelIdObj = roomData.get("hotelId");
            Long hotelId = null;
            if (hotelIdObj instanceof Number) {
                hotelId = ((Number) hotelIdObj).longValue();
            } else if (hotelIdObj != null) {
                try {
                    hotelId = Long.parseLong(hotelIdObj.toString());
                } catch (NumberFormatException ignored) {}
            }

            if (hotelId == null || !hotelId.equals(expectedHotelId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Room type ID " + roomId + " does not belong to hotel ID " + expectedHotelId);
            }

            // 2. Verify room type is active and bookable
            Object statusObj = roomData.get("status");
            String status = statusObj != null ? statusObj.toString() : "ACTIVE";
            if (!"ACTIVE".equalsIgnoreCase(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Room type ID " + roomId + " is not active (current status: " + status + ")");
            }

            // 3. Obtain authoritative maxOccupancy
            Object maxOccObj = roomData.get("maxOccupancy");
            Integer maxOccupancy = null;
            if (maxOccObj instanceof Number) {
                maxOccupancy = ((Number) maxOccObj).intValue();
            } else if (maxOccObj != null) {
                try {
                    maxOccupancy = Integer.parseInt(maxOccObj.toString());
                } catch (NumberFormatException ignored) {}
            }

            if (maxOccupancy == null || maxOccupancy < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid capacity definition for room type ID: " + roomId);
            }

            capacityMap.put(roomId, maxOccupancy);
        }

        return capacityMap;
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

        // Forward strictly room types and quantities — NO guest identities to hotel-service
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

        PrimaryGuestDto primaryGuest = request.getPrimaryGuest();
        String primaryName = primaryGuest != null ? primaryGuest.getFullName() : null;
        String primaryEmail = primaryGuest != null ? primaryGuest.getEmail() : null;
        String primaryMobile = primaryGuest != null ? primaryGuest.getMobile() : null;

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
                .primaryGuestName(primaryName)
                .primaryGuestEmail(primaryEmail)
                .primaryGuestMobile(primaryMobile)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .allocations(new ArrayList<>())
                .build();

        Map<Long, ReservationAllocationItemDto> requestAllocMap = new HashMap<>();
        if (request.getAllocations() != null) {
            for (ReservationAllocationItemDto reqItem : request.getAllocations()) {
                if (reqItem.getRoomId() != null) {
                    requestAllocMap.put(reqItem.getRoomId(), reqItem);
                }
            }
        }

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
                    .roomAllocations(new ArrayList<>())
                    .build();

            // Map physical rooms and occupants from request DTO
            ReservationAllocationItemDto reqItem = requestAllocMap.get(roomId);
            if (reqItem != null && reqItem.getRooms() != null) {
                for (RoomGuestAllocationDto roomDto : reqItem.getRooms()) {
                    HotelRoomGuestAllocation roomAlloc = HotelRoomGuestAllocation.builder()
                            .roomSequence(roomDto.getRoomSequence())
                            .adultCount(roomDto.getAdults() != null ? roomDto.getAdults() : 0)
                            .childCount(roomDto.getChildren() != null ? roomDto.getChildren() : 0)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .occupants(new ArrayList<>())
                            .build();

                    if (roomDto.getOccupants() != null) {
                        for (RoomOccupantDto occDto : roomDto.getOccupants()) {
                            HotelRoomGuest occ = HotelRoomGuest.builder()
                                    .guestName(occDto.getName())
                                    .guestType(occDto.getType())
                                    .age(occDto.getType() == GuestType.CHILD ? occDto.getAge() : null)
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build();
                            roomAlloc.addOccupant(occ);
                        }
                    }

                    childAlloc.addRoomAllocation(roomAlloc);
                }
            }

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

        PrimaryGuestDto primaryGuest = null;
        if (snapshot.getPrimaryGuestName() != null || snapshot.getPrimaryGuestEmail() != null || snapshot.getPrimaryGuestMobile() != null) {
            primaryGuest = PrimaryGuestDto.builder()
                    .fullName(snapshot.getPrimaryGuestName())
                    .email(snapshot.getPrimaryGuestEmail())
                    .mobile(snapshot.getPrimaryGuestMobile())
                    .build();
        }

        List<HotelReservationInitiationResponse.AllocationSnapshotDto> allocDtos = new ArrayList<>();
        if (snapshot.getAllocations() != null) {
            for (HotelAllocationSnapshot a : snapshot.getAllocations()) {
                List<RoomGuestAllocationDto> roomDtos = new ArrayList<>();
                List<HotelRoomGuestAllocation> rAllocs = a.getRoomAllocations();
                if ((rAllocs == null || rAllocs.isEmpty()) && a.getId() != null && roomAllocationRepository != null) {
                    rAllocs = roomAllocationRepository.findByAllocationSnapshotIdOrderByRoomSequenceAsc(a.getId());
                }

                if (rAllocs != null) {
                    for (HotelRoomGuestAllocation r : rAllocs) {
                        List<RoomOccupantDto> occDtos = new ArrayList<>();
                        List<HotelRoomGuest> occupants = r.getOccupants();
                        if ((occupants == null || occupants.isEmpty()) && r.getId() != null && guestRepository != null) {
                            occupants = guestRepository.findByRoomGuestAllocationIdOrderByIdAsc(r.getId());
                        }

                        if (occupants != null) {
                            for (HotelRoomGuest occ : occupants) {
                                occDtos.add(RoomOccupantDto.builder()
                                        .name(occ.getGuestName())
                                        .type(occ.getGuestType())
                                        .age(occ.getAge())
                                        .build());
                            }
                        }

                        roomDtos.add(RoomGuestAllocationDto.builder()
                                .roomSequence(r.getRoomSequence())
                                .adults(r.getAdultCount())
                                .children(r.getChildCount())
                                .occupants(occDtos)
                                .build());
                    }
                }

                allocDtos.add(HotelReservationInitiationResponse.AllocationSnapshotDto.builder()
                        .allocationId(a.getId())
                        .roomId(a.getRoomId())
                        .roomTypeName(a.getRoomTypeName())
                        .quantity(a.getQuantity())
                        .allocationTotal(a.getAllocationTotal())
                        .nightlyRatesJson(a.getNightlyRatesJson())
                        .status(a.getStatus())
                        .rooms(roomDtos)
                        .build());
            }
        }

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
                .primaryGuest(primaryGuest)
                .allocations(allocDtos)
                .build();
    }

    private HotelReservationDetailResponse mapSnapshotToDetailResponse(
            Booking booking, HotelReservationSnapshot snapshot) {

        PrimaryGuestDto primaryGuest = null;
        if (snapshot.getPrimaryGuestName() != null || snapshot.getPrimaryGuestEmail() != null || snapshot.getPrimaryGuestMobile() != null) {
            primaryGuest = PrimaryGuestDto.builder()
                    .fullName(snapshot.getPrimaryGuestName())
                    .email(snapshot.getPrimaryGuestEmail())
                    .mobile(snapshot.getPrimaryGuestMobile())
                    .build();
        }

        List<HotelReservationDetailResponse.AllocationDetailDto> allocDtos = new ArrayList<>();
        List<HotelAllocationSnapshot> allocSnapshots = snapshot.getAllocations();
        if ((allocSnapshots == null || allocSnapshots.isEmpty()) && snapshot.getId() != null) {
            allocSnapshots = allocationSnapshotRepository.findByReservationSnapshotId(snapshot.getId());
        }

        if (allocSnapshots != null) {
            for (HotelAllocationSnapshot a : allocSnapshots) {
                List<RoomGuestAllocationDto> roomDtos = new ArrayList<>();
                List<HotelRoomGuestAllocation> rAllocs = a.getRoomAllocations();
                if ((rAllocs == null || rAllocs.isEmpty()) && a.getId() != null && roomAllocationRepository != null) {
                    rAllocs = roomAllocationRepository.findByAllocationSnapshotIdOrderByRoomSequenceAsc(a.getId());
                }

                if (rAllocs != null) {
                    for (HotelRoomGuestAllocation r : rAllocs) {
                        List<RoomOccupantDto> occDtos = new ArrayList<>();
                        List<HotelRoomGuest> occupants = r.getOccupants();
                        if ((occupants == null || occupants.isEmpty()) && r.getId() != null && guestRepository != null) {
                            occupants = guestRepository.findByRoomGuestAllocationIdOrderByIdAsc(r.getId());
                        }

                        if (occupants != null) {
                            for (HotelRoomGuest occ : occupants) {
                                occDtos.add(RoomOccupantDto.builder()
                                        .name(occ.getGuestName())
                                        .type(occ.getGuestType())
                                        .age(occ.getAge())
                                        .build());
                            }
                        }

                        roomDtos.add(RoomGuestAllocationDto.builder()
                                .roomSequence(r.getRoomSequence())
                                .adults(r.getAdultCount())
                                .children(r.getChildCount())
                                .occupants(occDtos)
                                .build());
                    }
                }

                allocDtos.add(HotelReservationDetailResponse.AllocationDetailDto.builder()
                        .allocationId(a.getId())
                        .roomId(a.getRoomId())
                        .roomTypeName(a.getRoomTypeName())
                        .quantity(a.getQuantity())
                        .rooms(roomDtos)
                        .pricing(HotelReservationDetailResponse.AllocationPricingDto.builder()
                                .allocationTotal(a.getAllocationTotal())
                                .nightlyRatesJson(a.getNightlyRatesJson())
                                .build())
                        .status(a.getStatus())
                        .guestAllocationJson(a.getGuestAllocationJson())
                        .build());
            }
        }

        return HotelReservationDetailResponse.builder()
                .bookingId(booking.getId())
                .bookingRef(booking.getBookingRef())
                .bookingStatus(booking.getStatus())
                .hotel(HotelReservationDetailResponse.HotelSummaryDto.builder()
                        .hotelId(snapshot.getHotelId())
                        .hotelName(snapshot.getHotelName())
                        .build())
                .checkInDate(snapshot.getCheckInDate())
                .checkOutDate(snapshot.getCheckOutDate())
                .totalNights(snapshot.getTotalNights())
                .primaryGuest(primaryGuest)
                .allocations(allocDtos)
                .totalAmount(snapshot.getTotalAmount())
                .currency(snapshot.getCurrency() != null ? snapshot.getCurrency() : "INR")
                .paymentStatus(derivePaymentStatus(booking))
                .expiresAt(booking.getExpiresAt())
                .bookingDate(booking.getBookingDate())
                .cancellation(deriveCancellationDetails(booking))
                .build();
    }

    private HotelReservationDetailResponse mapLegacyBookingToDetailResponse(Booking booking) {
        LocalDate checkIn = booking.resolveCheckInDate();
        LocalDate checkOut = booking.resolveCheckOutDate();
        Integer nights = (checkIn != null && checkOut != null && checkOut.isAfter(checkIn))
                ? (int) ChronoUnit.DAYS.between(checkIn, checkOut)
                : 1;

        return HotelReservationDetailResponse.builder()
                .bookingId(booking.getId())
                .bookingRef(booking.getBookingRef())
                .bookingStatus(booking.getStatus())
                .hotel(HotelReservationDetailResponse.HotelSummaryDto.builder()
                        .hotelId(booking.getServiceId())
                        .hotelName(booking.getServiceTitle())
                        .build())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .totalNights(nights)
                .primaryGuest(null) // Not fabricated for historical records without structured guest allocation
                .allocations(Collections.emptyList())
                .totalAmount(booking.getTotalAmount())
                .currency("INR")
                .paymentStatus(derivePaymentStatus(booking))
                .expiresAt(booking.getExpiresAt())
                .bookingDate(booking.getBookingDate())
                .cancellation(deriveCancellationDetails(booking))
                .build();
    }

    private String derivePaymentStatus(Booking booking) {
        String status = booking.getStatus();
        if ("CONFIRMED".equalsIgnoreCase(status) || "CHECKED_IN".equalsIgnoreCase(status) ||
            "CHECKED_OUT".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)) {
            return "PAID";
        }
        if ("PENDING_PAYMENT".equalsIgnoreCase(status) || "INITIATED".equalsIgnoreCase(status)) {
            return "PENDING";
        }
        if ("CANCELLED".equalsIgnoreCase(status)) {
            if ("COMPLETED".equalsIgnoreCase(booking.getRefundStatus()) || "SUCCESS".equalsIgnoreCase(booking.getRefundStatus())) {
                return "REFUNDED";
            }
            return "CANCELLED";
        }
        if ("FAILED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
            return "UNPAID";
        }
        return status != null ? status : "UNKNOWN";
    }

    private HotelReservationDetailResponse.CancellationDetailDto deriveCancellationDetails(Booking booking) {
        if ("CANCELLED".equalsIgnoreCase(booking.getStatus()) || booking.getCancelledAt() != null) {
            return HotelReservationDetailResponse.CancellationDetailDto.builder()
                    .cancelled(true)
                    .cancelledAt(booking.getCancelledAt())
                    .cancellationReason(booking.getCancellationReason())
                    .refundAmount(booking.getRefundAmount())
                    .refundStatus(booking.getRefundStatus())
                    .build();
        }
        return null;
    }

    private String extractErrorMessage(Exception e) {
        if (e.getCause() != null) {
            return e.getCause().getMessage();
        }
        return e.getMessage();
    }
}
