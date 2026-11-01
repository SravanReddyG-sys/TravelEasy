package com.traveleasy.booking.service;

import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.model.ScheduleCancellationBatch;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.repository.ScheduleCancellationBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleCancellationCoordinatorService {

    private final BookingRepository bookingRepository;
    private final ScheduleCancellationBatchRepository batchRepository;
    private final BookingSecurityService securityService;

    private RestTemplate restTemplate = new RestTemplate();

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${bus.service.url:http://localhost:8082}")
    private String busServiceUrl = "http://localhost:8082";

    public void setBusServiceUrl(String busServiceUrl) {
        this.busServiceUrl = busServiceUrl;
    }

    @Value("${payment.service.url:http://localhost:8085}")
    private String paymentServiceUrl = "http://localhost:8085";

    public void setPaymentServiceUrl(String paymentServiceUrl) {
        this.paymentServiceUrl = paymentServiceUrl;
    }

    /**
     * Coordinates end-to-end durable schedule cancellation:
     * 1. Validates operator/admin identity and resource ownership.
     * 2. Atomically claims cancellation batch (rejecting concurrent requests with 409).
     * 3. Invalidates schedule and releases seat inventory in bus-service.
     * 4. Idempotently executes refunds for confirmed bookings via payment-service.
     * 5. Cancels unpaid holds with NOT_APPLICABLE refund status.
     * 6. Finalizes batch status (COMPLETED or PARTIALLY_FAILED).
     */
    public ScheduleCancellationBatch cancelSchedule(Long scheduleId, String authHeader) {
        // Step 1: Authentication & Identity Validation
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to cancel schedule");
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired authorization token");
        }

        String callerRole = securityService.extractRole(token);
        if (callerRole == null || callerRole.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Missing or invalid role claim");
        }

        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);
        if (!isAdmin && !isOperator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Bus Operator or Admin role required");
        }

        Long callerUserId = securityService.extractUserId(token);
        if (callerUserId == null || callerUserId <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Missing or invalid user identity in token");
        }

        // Authoritative Schedule & Bus Ownership Check via bus-service
        Map<String, Object> scheduleDetails;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.set("X-Internal-Service", "booking-service");
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    busServiceUrl + "/api/buses/schedules/" + scheduleId,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found with id: " + scheduleId);
            }
            scheduleDetails = (Map<String, Object>) resp.getBody();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to query bus-service for schedule {}: {}", scheduleId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found with id: " + scheduleId);
        }

        Map<String, Object> scheduleMap = (Map<String, Object>) scheduleDetails.get("schedule");
        if (scheduleMap == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found with id: " + scheduleId);
        }

        Long scheduleOperatorId = null;
        if (scheduleMap.get("operatorId") != null) {
            scheduleOperatorId = Long.valueOf(scheduleMap.get("operatorId").toString());
        } else if (scheduleDetails.get("bus") instanceof Map) {
            Map<String, Object> busMap = (Map<String, Object>) scheduleDetails.get("bus");
            if (busMap.get("operatorId") != null) {
                scheduleOperatorId = Long.valueOf(busMap.get("operatorId").toString());
            }
        }

        if (isOperator && (scheduleOperatorId == null || !scheduleOperatorId.equals(callerUserId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to cancel this schedule.");
        }

        // Step 2: Atomic Claiming / Idempotent Retry Handling
        Optional<ScheduleCancellationBatch> optBatch = batchRepository.findByScheduleId(scheduleId);
        ScheduleCancellationBatch batch;

        if (optBatch.isPresent()) {
            batch = optBatch.get();
            String currentStatus = batch.getBatchStatus();
            if ("CLAIMED".equalsIgnoreCase(currentStatus)
                    || "INVENTORY_LOCKED".equalsIgnoreCase(currentStatus)
                    || "PROCESSING_REFUNDS".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancellation already in progress for schedule " + scheduleId);
            } else if ("COMPLETED".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Schedule cancellation has already completed for schedule " + scheduleId);
            } else if ("PARTIALLY_FAILED".equalsIgnoreCase(currentStatus)) {
                log.info("Retrying partially failed cancellation batch {} for schedule {}", batch.getId(), scheduleId);
                batch.setBatchStatus("PROCESSING_REFUNDS");
                batch.setUpdatedAt(LocalDateTime.now());
                batch = batchRepository.saveAndFlush(batch);
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancellation in unexpected state: " + currentStatus);
            }
        } else {
            batch = ScheduleCancellationBatch.builder()
                    .scheduleId(scheduleId)
                    .operatorId(scheduleOperatorId != null ? scheduleOperatorId : callerUserId)
                    .batchStatus("CLAIMED")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            try {
                batch = batchRepository.saveAndFlush(batch);
            } catch (DataIntegrityViolationException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent cancellation attempt detected for schedule " + scheduleId);
            }
        }

        // Step 3: Invalidate & Close Schedule in bus-service
        try {
            HttpHeaders busHeaders = new HttpHeaders();
            busHeaders.set("Authorization", authHeader);
            busHeaders.set("X-Internal-Service", "booking-service");
            HttpEntity<?> busReq = new HttpEntity<>(busHeaders);

            // Invalidate schedule to CANCELLED
            restTemplate.exchange(
                    busServiceUrl + "/api/buses/schedules/" + scheduleId + "/status?status=CANCELLED",
                    HttpMethod.PUT,
                    busReq,
                    Map.class
            );

            // Release all seats
            restTemplate.exchange(
                    busServiceUrl + "/api/buses/schedules/" + scheduleId + "/release-all-seats",
                    HttpMethod.PUT,
                    busReq,
                    Map.class
            );

            batch.setBatchStatus("INVENTORY_LOCKED");
            batch.setUpdatedAt(LocalDateTime.now());
            batch = batchRepository.saveAndFlush(batch);
        } catch (Exception e) {
            log.warn("Bus-service inventory lock/release notification encountered warning: {}", e.getMessage());
        }

        // Step 4: Discover & Process Bookings
        List<Booking> bookings = bookingRepository.findByServiceTypeAndServiceId("BUS", scheduleId);
        batch.setTotalBookings(bookings.size());
        batch.setBatchStatus("PROCESSING_REFUNDS");
        batch.setUpdatedAt(LocalDateTime.now());
        batch = batchRepository.saveAndFlush(batch);

        for (Booking booking : bookings) {
            if ("CONFIRMED".equalsIgnoreCase(booking.getStatus()) || "FAILED_RETRYABLE".equalsIgnoreCase(booking.getRefundStatus())) {
                if ("PROCESSED".equalsIgnoreCase(booking.getRefundStatus())) {
                    continue; // Already successfully refunded in prior attempt
                }

                String idempotencyKey = "rfnd_" + booking.getBookingRef() + "_" + scheduleId;
                booking.setRefundAttemptKey(idempotencyKey);
                bookingRepository.save(booking);

                try {
                    String url = paymentServiceUrl + "/api/payments/refund/booking/" + booking.getBookingRef();
                    HttpHeaders payHeaders = new HttpHeaders();
                    payHeaders.setContentType(MediaType.APPLICATION_JSON);
                    payHeaders.set("X-Internal-Service", "booking-service");
                    payHeaders.set("X-Idempotency-Key", idempotencyKey);

                    Map<String, Object> reqBody = new HashMap<>();
                    if (booking.getTotalAmount() != null) {
                        reqBody.put("amount", booking.getTotalAmount());
                    }
                    reqBody.put("reason", "Operator cancelled schedule " + scheduleId);

                    HttpEntity<Map<String, Object>> payEntity = new HttpEntity<>(reqBody, payHeaders);
                    ResponseEntity<Map> payResp = restTemplate.exchange(url, HttpMethod.POST, payEntity, Map.class);

                    if (payResp.getStatusCode().is2xxSuccessful() && payResp.getBody() != null) {
                        Map<String, Object> body = payResp.getBody();
                        String refundId = body.get("refundId") != null ? body.get("refundId").toString() : "rfnd_" + UUID.randomUUID().toString().substring(0, 8);
                        Double refAmount = booking.getTotalAmount();
                        if (body.get("refundAmount") != null) {
                            refAmount = Double.valueOf(body.get("refundAmount").toString());
                        }

                        booking.setStatus("CANCELLED");
                        booking.setRefundStatus("PROCESSED");
                        booking.setRefundId(refundId);
                        booking.setRefundAmount(refAmount);
                        bookingRepository.save(booking);
                        log.info("Booking {} successfully refunded with refundId {}", booking.getBookingRef(), refundId);
                    } else {
                        booking.setRefundStatus("FAILED_RETRYABLE");
                        bookingRepository.save(booking);
                        log.warn("Refund call for booking {} returned non-2xx status: {}", booking.getBookingRef(), payResp.getStatusCode());
                    }
                } catch (Exception ex) {
                    log.error("Refund failed for booking {}: {}", booking.getBookingRef(), ex.getMessage());
                    booking.setRefundStatus("FAILED_RETRYABLE");
                    bookingRepository.save(booking);
                }
            } else if ("PENDING".equalsIgnoreCase(booking.getStatus())
                    || "PENDING_PAYMENT".equalsIgnoreCase(booking.getStatus())
                    || "INITIATED".equalsIgnoreCase(booking.getStatus())) {
                booking.setStatus("CANCELLED");
                booking.setRefundStatus("NOT_APPLICABLE");
                bookingRepository.save(booking);
                log.info("Unpaid booking {} marked CANCELLED with NOT_APPLICABLE refund", booking.getBookingRef());
            } else {
                if (booking.getRefundStatus() == null || booking.getRefundStatus().isBlank()) {
                    booking.setRefundStatus("NOT_APPLICABLE");
                    bookingRepository.save(booking);
                }
            }
        }

        // Step 5: Finalize Batch Metrics
        List<Booking> updatedBookings = bookingRepository.findByServiceTypeAndServiceId("BUS", scheduleId);
        int countProcessed = 0;
        int countFailed = 0;
        int countUnpaid = 0;
        double totalRefunded = 0.0;

        for (Booking b : updatedBookings) {
            if ("PROCESSED".equalsIgnoreCase(b.getRefundStatus())) {
                countProcessed++;
                if (b.getRefundAmount() != null) {
                    totalRefunded += b.getRefundAmount();
                } else if (b.getTotalAmount() != null) {
                    totalRefunded += b.getTotalAmount();
                }
            } else if ("FAILED_RETRYABLE".equalsIgnoreCase(b.getRefundStatus())) {
                countFailed++;
            } else if ("NOT_APPLICABLE".equalsIgnoreCase(b.getRefundStatus())) {
                countUnpaid++;
            }
        }

        batch.setTotalBookings(updatedBookings.size());
        batch.setRefundsProcessed(countProcessed);
        batch.setRefundsFailed(countFailed);
        batch.setUnpaidCancelled(countUnpaid);
        batch.setTotalRefundAmount(totalRefunded);
        batch.setUpdatedAt(LocalDateTime.now());

        if (countFailed > 0) {
            batch.setBatchStatus("PARTIALLY_FAILED");
        } else {
            batch.setBatchStatus("COMPLETED");
        }

        return batchRepository.saveAndFlush(batch);
    }

    /**
     * Retrieve status of durable cancellation batch for a schedule.
     */
    public ScheduleCancellationBatch getCancellationStatus(Long scheduleId, String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired authorization token");
        }

        String callerRole = securityService.extractRole(token);
        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(callerRole);
        boolean isOperator = "ROLE_BUS_OPERATOR".equalsIgnoreCase(callerRole);
        if (!isAdmin && !isOperator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Bus Operator or Admin role required");
        }

        Long callerUserId = securityService.extractUserId(token);
        if (callerUserId == null || callerUserId <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Missing or invalid user identity in token");
        }

        ScheduleCancellationBatch batch = batchRepository.findByScheduleId(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No cancellation batch found for schedule " + scheduleId));

        if (isOperator && (batch.getOperatorId() == null || !batch.getOperatorId().equals(callerUserId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view cancellation status for this schedule.");
        }

        return batch;
    }
}
