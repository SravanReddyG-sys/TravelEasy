package com.traveleasy.booking.controller;

import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.service.BookingExpiryReconciliationService;
import com.traveleasy.booking.service.BookingSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingRepository bookingRepository;
    private final BookingSecurityService securityService;
    private final BookingExpiryReconciliationService expiryService;
    private RestTemplate restTemplate = new RestTemplate();

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

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
        if (booking.getExpiresAt() == null) {
            booking.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        }
        Booking saved = bookingRepository.save(booking);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    private static final List<String> CONFIRMED_BOOKING_STATUSES = List.of("CONFIRMED", "CANCELLED", "COMPLETED");

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getCustomerBookings(@PathVariable Long customerId,
                                                @RequestParam(value = "includeUnconfirmed", required = false, defaultValue = "false") boolean includeUnconfirmed,
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

        List<Booking> allCustomerBookings = bookingRepository.findByCustomerIdOrderByBookingDateDesc(customerId);
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        for (Booking b : allCustomerBookings) {
            // Reconcile status: If payment was successfully captured, ensure status is CONFIRMED and seats allocated
            if (!"CANCELLED".equalsIgnoreCase(b.getStatus()) && !"CONFIRMED".equalsIgnoreCase(b.getStatus())) {
                boolean paid = verifyPaymentStatusWithPaymentService(b.getBookingRef(), b.getTotalAmount());
                if (paid) {
                    confirmSeatsInBusService(b);
                    b.setStatus("CONFIRMED");
                    bookingRepository.save(b);
                }
            }
            if ("PENDING_PAYMENT".equalsIgnoreCase(b.getStatus()) || "INITIATED".equalsIgnoreCase(b.getStatus())) {
                if (b.getBookingDate() != null && b.getBookingDate().isBefore(threshold)) {
                    boolean expired = expiryService.expireBookingByRef(b.getBookingRef());
                    if (expired) {
                        b.setStatus("EXPIRED");
                    }
                }
            }
        }

        if (includeUnconfirmed) {
            return ResponseEntity.ok(allCustomerBookings);
        }

        // Return only authoritatively confirmed, completed, or cancelled bookings.
        // Temporary seat holds and incomplete payment attempts are excluded.
        List<Booking> confirmedOnly = allCustomerBookings.stream()
                .filter(b -> CONFIRMED_BOOKING_STATUSES.contains(b.getStatus()))
                .toList();

        return ResponseEntity.ok(confirmedOnly);
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

    @PutMapping("/ref/{bookingRef}/expire")
    public ResponseEntity<?> expireBookingByRef(@PathVariable String bookingRef) {
        boolean expired = expiryService.expireBookingByRef(bookingRef);
        return ResponseEntity.ok(Map.of(
                "bookingRef", bookingRef,
                "status", "EXPIRED",
                "expired", expired
        ));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<?> confirmBooking(@PathVariable Long id,
                                            @RequestHeader(value = "Authorization", required = false) String authHeader,
                                            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        return bookingRepository.findById(id).map(bkg -> {
            // Idempotent check
            if ("CONFIRMED".equalsIgnoreCase(bkg.getStatus())) {
                return ResponseEntity.ok(Map.of("message", "Booking is already confirmed", "booking", bkg));
            }
            if ("CANCELLED".equalsIgnoreCase(bkg.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Cannot confirm a cancelled booking"));
            }

            // Authorization check
            boolean isInternal = "payment-service".equalsIgnoreCase(internalService);
            if (!isInternal && authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (securityService.validateToken(token)) {
                    Long callerUserId = securityService.extractUserId(token);
                    String callerRole = securityService.extractRole(token);
                    boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
                    if (!isAdmin && callerUserId != null && bkg.getCustomerId() != null && !callerUserId.equals(bkg.getCustomerId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("message", "Access denied: Cannot confirm another user's booking"));
                    }
                }
            }

            // Authoritative server-side verification with Payment Service
            boolean paymentValid = verifyPaymentStatusWithPaymentService(bkg.getBookingRef(), bkg.getTotalAmount());
            if (!paymentValid && !isInternal) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Cannot confirm booking: Authoritative payment verification failed or payment is not captured"));
            }

            // Attempt to confirm seats in bus-service
            boolean seatsConfirmed = confirmSeatsInBusService(bkg);
            if (!seatsConfirmed) {
                // Hold expired and seats allocated to another passenger - trigger automatic refund!
                org.slf4j.LoggerFactory.getLogger(BookingController.class)
                        .warn("Seats for booking {} are no longer available. Initiating automatic refund.", bkg.getBookingRef());
                processRefundInPaymentService(bkg.getBookingRef(), bkg.getTotalAmount());
                bkg.setStatus("EXPIRED");
                Booking saved = bookingRepository.save(bkg);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "message", "Cannot confirm booking: Seat hold has expired and seats have been allocated to another traveller. An automated refund has been initiated to your original payment method.",
                        "booking", saved
                ));
            }

            bkg.setStatus("CONFIRMED");
            Booking saved = bookingRepository.save(bkg);
            return ResponseEntity.ok(Map.of("message", "Booking confirmed", "booking", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id,
                                           @RequestHeader(value = "Authorization", required = false) String authHeader,
                                           @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {
        return bookingRepository.findById(id).map(bkg -> {
            if ("CANCELLED".equals(bkg.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Booking is already cancelled"));
            }

            // Authorization check
            boolean isInternal = "payment-service".equalsIgnoreCase(internalService) || "traveller-service".equalsIgnoreCase(internalService);
            if (!isInternal && authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (securityService.validateToken(token)) {
                    Long callerUserId = securityService.extractUserId(token);
                    String callerRole = securityService.extractRole(token);
                    boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
                    if (!isAdmin && callerUserId != null && bkg.getCustomerId() != null && !callerUserId.equals(bkg.getCustomerId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("message", "Access denied: Cannot cancel another user's booking"));
                    }
                }
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

    private boolean verifyPaymentStatusWithPaymentService(String bookingRef, Double expectedAmount) {
        if (bookingRef == null || bookingRef.isBlank()) {
            return false;
        }
        try {
            String url = paymentServiceUrl + "/api/payments/booking/" + bookingRef;
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                String status = (String) resp.getBody().get("status");
                if ("SUCCESS".equalsIgnoreCase(status)) {
                    Object amtObj = resp.getBody().get("amount");
                    if (amtObj != null && expectedAmount != null) {
                        double paidAmt = Double.parseDouble(amtObj.toString());
                        if (Math.abs(paidAmt - expectedAmount) > 0.01) {
                            org.slf4j.LoggerFactory.getLogger(BookingController.class)
                                    .warn("Payment amount {} does not match booking amount {}", paidAmt, expectedAmount);
                            return false;
                        }
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class)
                    .warn("Payment verification lookup failed for booking {}: {}", bookingRef, e.getMessage());
        }
        return false;
    }

    private List<String> extractSeatNumbers(String detailsJson) {
        List<String> seats = new ArrayList<>();
        if (detailsJson == null || detailsJson.isBlank()) {
            return seats;
        }
        Pattern pattern = Pattern.compile("Seats:\\s*([^|\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(detailsJson);
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

    private boolean confirmSeatsInBusService(Booking booking) {
        if (!"BUS".equalsIgnoreCase(booking.getServiceType()) || booking.getServiceId() == null) {
            return true;
        }
        List<String> seats = extractSeatNumbers(booking.getDetailsJson());
        if (seats.isEmpty()) {
            return true;
        }
        try {
            String url = busServiceUrl + "/api/buses/schedules/" + booking.getServiceId() + "/confirm-seats?bookingRef=" + booking.getBookingRef();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "booking-service");
            org.springframework.http.HttpEntity<List<String>> entity = new org.springframework.http.HttpEntity<>(seats, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map.class);
            boolean success = resp.getStatusCode().is2xxSuccessful();
            if (success) {
                org.slf4j.LoggerFactory.getLogger(BookingController.class).info("Confirmed seats {} for schedule {} via bus-service", seats, booking.getServiceId());
            }
            return success;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class).warn("Failed to confirm seats in bus-service: {}", e.getMessage());
            return false;
        }
    }

    private List<String> releaseSeatsInBusService(Booking booking) {
        if (!"BUS".equalsIgnoreCase(booking.getServiceType()) || booking.getServiceId() == null) {
            return Collections.emptyList();
        }
        List<String> seats = extractSeatNumbers(booking.getDetailsJson());
        if (seats.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String url = busServiceUrl + "/api/buses/schedules/" + booking.getServiceId() + "/release-seats?bookingRef=" + booking.getBookingRef();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "booking-service");
            org.springframework.http.HttpEntity<List<String>> entity = new org.springframework.http.HttpEntity<>(seats, headers);
            restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map.class);
            org.slf4j.LoggerFactory.getLogger(BookingController.class).info("Released seats {} for schedule {} via bus-service", seats, booking.getServiceId());
            return seats;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BookingController.class).error("Failed to release seats in bus-service: {}", e.getMessage());
            return Collections.emptyList();
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
            reqBody.put("reason", "Cancelled by traveller or hold expired");
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
