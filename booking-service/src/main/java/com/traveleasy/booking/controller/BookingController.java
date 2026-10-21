package com.traveleasy.booking.controller;

import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingRepository bookingRepository;
    private final com.traveleasy.booking.service.BookingSecurityService securityService;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody Booking booking,
                                           @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (securityService.validateToken(token)) {
                Long callerUserId = securityService.extractUserId(token);
                String callerEmail = securityService.extractEmail(token);
                if (callerUserId != null) {
                    booking.setCustomerId(callerUserId);
                }
                if (callerEmail != null && (booking.getCustomerEmail() == null || booking.getCustomerEmail().isEmpty())) {
                    booking.setCustomerEmail(callerEmail);
                }
            }
        }

        if (booking.getBookingRef() == null) {
            booking.setBookingRef("BKG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (booking.getStatus() == null) {
            booking.setStatus("PENDING_PAYMENT");
        }
        Booking saved = bookingRepository.save(booking);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getCustomerBookings(@PathVariable Long customerId,
                                                @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid or expired token"));
            }
            Long callerUserId = securityService.extractUserId(token);
            String callerRole = securityService.extractRole(token);
            boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
            boolean isInternal = "traveller-service".equalsIgnoreCase(internalService);

            if (!isAdmin && !isInternal && callerUserId != null && !callerUserId.equals(customerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied to customer bookings"));
            }
        }
        return ResponseEntity.ok(bookingRepository.findByCustomerIdOrderByBookingDateDesc(customerId));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<Booking>> getProviderBookings(@PathVariable Long providerId) {
        return ResponseEntity.ok(bookingRepository.findByProviderIdOrderByBookingDateDesc(providerId));
    }

    @GetMapping("/admin/all")
    public ResponseEntity<List<Booking>> getAllBookings() {
        return ResponseEntity.ok(bookingRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBookingById(@PathVariable Long id) {
        return bookingRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/ref/{bookingRef}")
    public ResponseEntity<?> getBookingByRef(@PathVariable String bookingRef) {
        return bookingRepository.findByBookingRef(bookingRef)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<?> confirmBooking(@PathVariable Long id) {
        return bookingRepository.findById(id).map(bkg -> {
            bkg.setStatus("CONFIRMED");
            bookingRepository.save(bkg);
            return ResponseEntity.ok(Map.of("message", "Booking confirmed", "booking", bkg));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id) {
        return bookingRepository.findById(id).map(bkg -> {
            if ("CANCELLED".equals(bkg.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Booking is already cancelled"));
            }
            bkg.setStatus("CANCELLED");
            bookingRepository.save(bkg);
            return ResponseEntity.ok(Map.of("message", "Booking cancelled successfully", "booking", bkg));
        }).orElse(ResponseEntity.notFound().build());
    }
}
