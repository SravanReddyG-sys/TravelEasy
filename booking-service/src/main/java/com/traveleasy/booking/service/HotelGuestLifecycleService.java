package com.traveleasy.booking.service;

import com.traveleasy.booking.dto.BookingLifecycleOperationResponse;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelGuestLifecycleService {

    private final BookingRepository bookingRepository;
    private final BookingStateTransitionService stateTransitionService;
    private final BookingInternalTokenService internalTokenService;

    @Value("${hotel.service.url:http://localhost:8083}")
    private String hotelServiceUrl;

    private RestTemplate restTemplate = new RestTemplate();

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Checks in a guest for a hotel reservation (BD-09).
     */
    @Transactional
    public BookingLifecycleOperationResponse checkInGuest(String bookingRef, Long callerUserId, String callerRole, boolean isInternal) {
        Booking booking = findAndValidateHotelBooking(bookingRef);
        validateManagerOwnership(booking, callerUserId, callerRole, isInternal);

        String currentStatus = booking.getStatus();
        if (!"CONFIRMED".equalsIgnoreCase(currentStatus)) {
            if ("PENDING_PAYMENT".equalsIgnoreCase(currentStatus) || "INITIATED".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot check in guest: Reservation has not been paid or confirmed (Status: " + currentStatus +
                        "). Manual confirmation of unpaid reservations is strictly prohibited (BD-09).");
            } else if ("CHECKED_IN".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guest is already checked in.");
            } else if ("CHECKED_OUT".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot check in guest: Reservation is already checked out.");
            } else if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot check in guest: Reservation was cancelled.");
            } else if ("NO_SHOW".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot check in guest: Reservation was marked as no-show.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid reservation status for check-in: " + currentStatus + " (Must be CONFIRMED).");
        }

        LocalDate checkInDate = booking.resolveCheckInDate();
        LocalDate today = LocalDate.now();
        if (checkInDate != null && today.isBefore(checkInDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot check in guest prior to scheduled check-in arrival date: " + checkInDate + ". Current date: " + today + ".");
        }

        booking.setStatus("CHECKED_IN");
        booking.setCheckedInAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);

        stateTransitionService.recordHotelGuestLifecycleEvent(saved, "HOTEL_GUEST_CHECKED_IN");

        log.info("Guest successfully checked in for bookingRef {}", bookingRef);
        return BookingLifecycleOperationResponse.builder()
                .message("Guest successfully checked in.")
                .bookingRef(bookingRef)
                .previousStatus(currentStatus)
                .newStatus("CHECKED_IN")
                .timestamp(saved.getCheckedInAt())
                .booking(saved)
                .build();
    }

    /**
     * Checks out a guest for an active hotel stay (BD-09).
     */
    @Transactional
    public BookingLifecycleOperationResponse checkOutGuest(String bookingRef, Long callerUserId, String callerRole, boolean isInternal) {
        Booking booking = findAndValidateHotelBooking(bookingRef);
        validateManagerOwnership(booking, callerUserId, callerRole, isInternal);

        String currentStatus = booking.getStatus();
        if (!"CHECKED_IN".equalsIgnoreCase(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot check out guest: Reservation must be in CHECKED_IN status (Current status: " + currentStatus + ").");
        }

        booking.setStatus("CHECKED_OUT");
        booking.setCheckedOutAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);

        stateTransitionService.recordHotelGuestLifecycleEvent(saved, "HOTEL_GUEST_CHECKED_OUT");

        log.info("Guest successfully checked out for bookingRef {}", bookingRef);
        return BookingLifecycleOperationResponse.builder()
                .message("Guest successfully checked out.")
                .bookingRef(bookingRef)
                .previousStatus(currentStatus)
                .newStatus("CHECKED_OUT")
                .timestamp(saved.getCheckedOutAt())
                .booking(saved)
                .build();
    }

    /**
     * Marks an eligible reservation as NO_SHOW (BD-09).
     */
    @Transactional
    public BookingLifecycleOperationResponse markNoShow(String bookingRef, Long callerUserId, String callerRole, boolean isInternal) {
        Booking booking = findAndValidateHotelBooking(bookingRef);
        validateManagerOwnership(booking, callerUserId, callerRole, isInternal);

        String currentStatus = booking.getStatus();
        if (!"CONFIRMED".equalsIgnoreCase(currentStatus)) {
            if ("CHECKED_IN".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot mark as no-show: Guest has already checked in.");
            } else if ("CHECKED_OUT".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot mark as no-show: Guest has already completed check-out.");
            } else if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot mark as no-show: Reservation is already cancelled.");
            } else if ("NO_SHOW".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation is already marked as no-show.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot mark as no-show: Reservation must be in CONFIRMED status (Current status: " + currentStatus + ").");
        }

        LocalDate checkInDate = booking.resolveCheckInDate();
        LocalDate today = LocalDate.now();
        if (checkInDate != null && today.isBefore(checkInDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot mark reservation as no-show prior to scheduled check-in arrival date: " + checkInDate + ".");
        }

        booking.setStatus("NO_SHOW");
        booking.setNoShowAt(LocalDateTime.now());
        booking.setRefundStatus("NON_REFUNDABLE");
        booking.setRefundAmount(0.0);
        Booking saved = bookingRepository.save(booking);

        // Authoritatively release remaining stay inventory in hotel-service so rooms can be resold
        releaseStayInventoryInHotelService(bookingRef);

        stateTransitionService.recordHotelGuestLifecycleEvent(saved, "HOTEL_BOOKING_NO_SHOW");

        log.info("Reservation {} marked as NO_SHOW by manager {}", bookingRef, callerUserId);
        return BookingLifecycleOperationResponse.builder()
                .message("Reservation successfully marked as no-show. Remaining inventory restored.")
                .bookingRef(bookingRef)
                .previousStatus(currentStatus)
                .newStatus("NO_SHOW")
                .timestamp(saved.getNoShowAt())
                .booking(saved)
                .build();
    }

    private Booking findAndValidateHotelBooking(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking reference is required.");
        }
        Booking booking = bookingRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with reference: " + bookingRef));

        if (!"HOTEL".equalsIgnoreCase(booking.getServiceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Guest operational lifecycle actions apply only to hotel reservations (Service type is: " + booking.getServiceType() + ").");
        }
        return booking;
    }

    private void validateManagerOwnership(Booking booking, Long callerUserId, String callerRole, boolean isInternal) {
        if (isInternal) {
            return;
        }
        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole) || "ADMIN".equalsIgnoreCase(callerRole);
        if (isAdmin) {
            return;
        }

        boolean isHotelManager = "ROLE_HOTEL_MANAGER".equalsIgnoreCase(callerRole) || "HOTEL_MANAGER".equalsIgnoreCase(callerRole);
        if (!isHotelManager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Hotel Manager or Administrator role required.");
        }

        if (callerUserId == null || booking.getProviderId() == null || !callerUserId.equals(booking.getProviderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: Cannot perform lifecycle operations on a reservation belonging to another hotel property.");
        }
    }

    private void releaseStayInventoryInHotelService(String bookingRef) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String internalToken = internalTokenService.generateInternalToken();
            headers.set("Authorization", "Bearer " + internalToken);
            headers.set("X-Internal-Token", internalToken);
            headers.set("X-Internal-Service", "booking-service");

            String url = hotelServiceUrl + "/api/hotels/rooms/release-stay?bookingRef=" + bookingRef;
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("Released stay inventory in hotel-service for no-show booking {}", bookingRef);
            }
        } catch (Exception e) {
            log.warn("Could not release stay inventory in hotel-service for no-show booking {}: {}", bookingRef, e.getMessage());
        }
    }
}
