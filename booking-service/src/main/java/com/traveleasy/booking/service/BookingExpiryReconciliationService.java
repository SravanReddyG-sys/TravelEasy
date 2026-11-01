package com.traveleasy.booking.service;

import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingExpiryReconciliationService {

    private final BookingRepository bookingRepository;
    private RestTemplate restTemplate = new RestTemplate();

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${bus.service.url:http://localhost:8082}")
    private String busServiceUrl;

    @Value("${hotel.service.url:http://localhost:8083}")
    private String hotelServiceUrl;

    @Value("${payment.service.url:http://localhost:8085}")
    private String paymentServiceUrl;

    /**
     * Periodic reconciliation job checking for expired bookings (every 30 seconds).
     * Authoritatively transitions unpaid bookings past the 15-minute hold to EXPIRED.
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void reconcileExpiredBookings() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        List<Booking> candidates = bookingRepository.findByStatusInAndBookingDateBefore(
                List.of("PENDING_PAYMENT", "INITIATED"), threshold);

        if (candidates.isEmpty()) {
            return;
        }

        log.info("Found {} pending booking(s) past expiration threshold", candidates.size());

        for (Booking booking : candidates) {
            expireSingleBooking(booking);
        }
    }

    @Transactional
    public boolean expireBookingByRef(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) {
            return false;
        }
        return bookingRepository.findByBookingRef(bookingRef)
                .map(this::expireSingleBooking)
                .orElse(false);
    }

    private boolean expireSingleBooking(Booking booking) {
        if ("CONFIRMED".equalsIgnoreCase(booking.getStatus()) || "CANCELLED".equalsIgnoreCase(booking.getStatus())) {
            return false;
        }

        // 1. Verify with Payment Service before expiring
        boolean paymentCaptured = isPaymentSuccessful(booking.getBookingRef());

        if (paymentCaptured) {
            log.info("Booking {} has confirmed payment. Attempting authoritative seat confirmation.", booking.getBookingRef());
            boolean seatsConfirmed = confirmSeatsInBusService(booking);
            if (seatsConfirmed) {
                booking.setStatus("CONFIRMED");
                bookingRepository.save(booking);
                log.info("Booking {} successfully recovered and marked CONFIRMED.", booking.getBookingRef());
                return false;
            } else {
                // Race condition: payment arrived late, but seats are already taken!
                log.warn("Late payment detected for booking {}, but seats are already allocated to another traveller. Initiating automatic refund.",
                        booking.getBookingRef());
                processRefund(booking.getBookingRef(), booking.getTotalAmount(), "Seat hold expired and seats allocated to another traveller");
                booking.setStatus("EXPIRED");
                bookingRepository.save(booking);
                return true;
            }
        }

        // 2. Unpaid: transition status to EXPIRED
        log.warn("Expiring unpaid booking {} (held for customer {})", booking.getBookingRef(), booking.getCustomerId());
        booking.setStatus("EXPIRED");
        bookingRepository.save(booking);

        // 3. Release any remaining held seats in bus-service
        releaseSeatsInBusService(booking);
        return true;
    }

    private boolean isPaymentSuccessful(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) {
            return false;
        }
        try {
            String url = paymentServiceUrl + "/api/payments/booking/" + bookingRef;
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object status = resp.getBody().get("status");
                return "SUCCESS".equalsIgnoreCase(String.valueOf(status));
            }
        } catch (Exception e) {
            log.debug("Payment status check for booking {}: {}", bookingRef, e.getMessage());
        }
        return false;
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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "booking-service");
            HttpEntity<List<String>> entity = new HttpEntity<>(seats, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Failed to confirm seats in bus-service for booking {}: {}", booking.getBookingRef(), e.getMessage());
            return false;
        }
    }

    private void releaseSeatsInBusService(Booking booking) {
        if (!"BUS".equalsIgnoreCase(booking.getServiceType()) || booking.getServiceId() == null) {
            return;
        }
        List<String> seats = extractSeatNumbers(booking.getDetailsJson());
        if (seats.isEmpty()) {
            return;
        }
        try {
            String url = busServiceUrl + "/api/buses/schedules/" + booking.getServiceId() + "/release-seats?bookingRef=" + booking.getBookingRef();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "booking-service");
            HttpEntity<List<String>> entity = new HttpEntity<>(seats, headers);
            restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            log.info("Released expired seats {} for schedule {} via bus-service", seats, booking.getServiceId());
        } catch (Exception e) {
            log.error("Failed to release seats in bus-service for booking {}: {}", booking.getBookingRef(), e.getMessage());
        }
    }

    private void processRefund(String bookingRef, Double totalAmount, String reason) {
        try {
            String url = paymentServiceUrl + "/api/payments/refund/booking/" + bookingRef;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "booking-service");
            Map<String, Object> reqBody = new java.util.HashMap<>();
            if (totalAmount != null) {
                reqBody.put("amount", totalAmount);
            }
            reqBody.put("reason", reason);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(reqBody, headers);
            restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            log.info("Automatic refund triggered for expired booking {}", bookingRef);
        } catch (Exception e) {
            log.error("Failed to process refund for booking {}: {}", bookingRef, e.getMessage());
        }
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
}
