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

    @org.springframework.beans.factory.annotation.Value("${bus.service.url:http://localhost:8082}")
    private String busServiceUrl;

    @org.springframework.beans.factory.annotation.Value("${payment.service.url:http://localhost:8085}")
    private String paymentServiceUrl;

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
            Booking saved = bookingRepository.save(bkg);
            confirmSeatsInBusService(saved);
            return ResponseEntity.ok(Map.of("message", "Booking confirmed", "booking", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id) {
        return bookingRepository.findById(id).map(bkg -> {
            if ("CANCELLED".equals(bkg.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Booking is already cancelled"));
            }
            bkg.setStatus("CANCELLED");
            Booking saved = bookingRepository.save(bkg);

            // 1. Authoritatively release physical seats in bus-service
            List<String> releasedSeats = releaseSeatsInBusService(saved);

            // 2. Trigger automated gateway refund via payment-service
            Map<String, Object> refund = processRefundInPaymentService(saved.getBookingRef(), saved.getTotalAmount());

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("message", "Booking cancelled successfully. Reserved inventory has been released.");
            response.put("booking", saved);
            response.put("releasedSeats", releasedSeats);
            if (refund != null) {
                response.put("refund", refund);
            }
            return ResponseEntity.ok(response);
        }).orElse(ResponseEntity.notFound().build());
    }

    private List<String> extractSeatNumbers(String detailsJson) {
        List<String> seats = new java.util.ArrayList<>();
        if (detailsJson == null || detailsJson.isBlank()) {
            return seats;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Seats:\\s*([^|\\n]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(detailsJson);
        if (matcher.find()) {
            String seatsStr = matcher.group(1).trim();
            for (String s : seatsStr.split(",")) {
                String clean = s.trim();
                if (!clean.isEmpty()) {
                    seats.add(clean);
                }
            }
        }
        return seats;
    }

    private void confirmSeatsInBusService(Booking booking) {
        if (!"BUS".equalsIgnoreCase(booking.getServiceType()) || booking.getServiceId() == null) {
            return;
        }
        List<String> seats = extractSeatNumbers(booking.getDetailsJson());
        if (seats.isEmpty()) {
            return;
        }
        try {
            String url = busServiceUrl + "/api/buses/schedules/" + booking.getServiceId() + "/confirm-seats";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "booking-service");
            org.springframework.http.HttpEntity<List<String>> entity = new org.springframework.http.HttpEntity<>(seats, headers);
            restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map.class);
            org.slf4j.LoggerFactory.getLogger(BookingController.class).info("Confirmed seats {} for schedule {} via bus-service", seats, booking.getServiceId());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class).warn("Failed to confirm seats in bus-service: {}", e.getMessage());
        }
    }

    private List<String> releaseSeatsInBusService(Booking booking) {
        if (!"BUS".equalsIgnoreCase(booking.getServiceType()) || booking.getServiceId() == null) {
            return java.util.Collections.emptyList();
        }
        List<String> seats = extractSeatNumbers(booking.getDetailsJson());
        if (seats.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        try {
            String url = busServiceUrl + "/api/buses/schedules/" + booking.getServiceId() + "/release-seats";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "booking-service");
            org.springframework.http.HttpEntity<List<String>> entity = new org.springframework.http.HttpEntity<>(seats, headers);
            restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map.class);
            org.slf4j.LoggerFactory.getLogger(BookingController.class).info("Released seats {} for schedule {} via bus-service", seats, booking.getServiceId());
            return seats;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class).error("Failed to release seats in bus-service: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private Map<String, Object> processRefundInPaymentService(String bookingRef, Double totalAmount) {
        if (bookingRef == null || bookingRef.isBlank()) {
            return null;
        }
        try {
            String url = paymentServiceUrl + "/api/payments/refund/booking/" + bookingRef;
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "booking-service");
            Map<String, Object> reqBody = new java.util.HashMap<>();
            if (totalAmount != null) {
                reqBody.put("amount", totalAmount);
            }
            reqBody.put("reason", "Cancelled by traveller");
            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(reqBody, headers);
            org.springframework.http.ResponseEntity<Map> resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, entity, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                org.slf4j.LoggerFactory.getLogger(BookingController.class).info("Refund processed successfully for booking {}: {}", bookingRef, resp.getBody());
                return (Map<String, Object>) resp.getBody();
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class).warn("Payment refund not applicable or service call returned: {}", e.getMessage());
        }
        return null;
    }
}
