package com.traveleasy.bus.service;

import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.Seat;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldReconciliationService {

    private final SeatRepository seatRepository;
    private final BusScheduleRepository scheduleRepository;
    private final RestTemplate restTemplate;

    @Value("${booking.service.url:http://localhost:8084}")
    private String bookingServiceUrl;

    @Value("${payment.service.url:http://localhost:8085}")
    private String paymentServiceUrl;

    /**
     * Periodic reconciliation job checking for expired seat holds (every 30 seconds).
     * Authoritative server-side release prevents abandoned holds from blocking inventory.
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void reconcileExpiredHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<Seat> expiredSeats = seatRepository.findByStatusInAndHoldExpiresAtBefore(List.of("LOCKED", "BOOKED"), now);

        if (expiredSeats.isEmpty()) {
            return;
        }

        log.info("Found {} candidate seat holds past expiration threshold", expiredSeats.size());

        for (Seat seat : expiredSeats) {
            String bookingRef = seat.getHoldBookingRef();

            // Reconcile with Payment & Booking Service before releasing to avoid race conditions
            boolean paymentConfirmed = isPaymentOrBookingConfirmed(bookingRef);

            if (paymentConfirmed) {
                log.info("Seat {} on schedule {} has confirmed payment for booking {}. Preserving as BOOKED.",
                        seat.getSeatNumber(), seat.getScheduleId(), bookingRef);
                seat.setStatus("BOOKED");
                seat.setHoldExpiresAt(null);
                seatRepository.save(seat);
            } else {
                log.warn("Releasing expired seat hold on seat {} for schedule {} (bookingRef: {})",
                        seat.getSeatNumber(), seat.getScheduleId(), bookingRef);
                seat.setStatus("AVAILABLE");
                seat.setHoldExpiresAt(null);
                seat.setHoldBookingRef(null);
                seatRepository.save(seat);

                // Increment schedule inventory
                BusSchedule schedule = scheduleRepository.findById(seat.getScheduleId()).orElse(null);
                if (schedule != null) {
                    schedule.setAvailableSeats(schedule.getAvailableSeats() + 1);
                    scheduleRepository.save(schedule);
                }

                // Authoritatively expire the booking in booking-service
                notifyBookingServiceExpired(bookingRef);
            }
        }
    }

    private void notifyBookingServiceExpired(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) {
            return;
        }
        try {
            String url = bookingServiceUrl + "/api/bookings/ref/" + bookingRef + "/expire";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "bus-service");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            log.info("Notified booking-service to expire booking {}", bookingRef);
        } catch (Exception e) {
            log.debug("Notice to booking-service for expired booking {} returned: {}", bookingRef, e.getMessage());
        }
    }

    private boolean isPaymentOrBookingConfirmed(String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) {
            return false;
        }

        // 1. Check Payment Service
        try {
            String payUrl = paymentServiceUrl + "/api/payments/booking/" + bookingRef;
            ResponseEntity<Map> payRes = restTemplate.getForEntity(payUrl, Map.class);
            if (payRes.getStatusCode().is2xxSuccessful() && payRes.getBody() != null) {
                Object status = payRes.getBody().get("status");
                if ("SUCCESS".equalsIgnoreCase(String.valueOf(status))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Check Booking Service
        try {
            String bkgUrl = bookingServiceUrl + "/api/bookings/ref/" + bookingRef;
            ResponseEntity<Map> bkgRes = restTemplate.getForEntity(bkgUrl, Map.class);
            if (bkgRes.getStatusCode().is2xxSuccessful() && bkgRes.getBody() != null) {
                Object status = bkgRes.getBody().get("status");
                if ("CONFIRMED".equalsIgnoreCase(String.valueOf(status))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }
}
