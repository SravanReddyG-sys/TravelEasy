package com.traveleasy.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.dto.EventContentDto;
import com.traveleasy.booking.dto.NotificationEventRequest;
import com.traveleasy.booking.dto.RecipientDto;
import com.traveleasy.booking.dto.RecipientRole;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.model.BookingOutboxEvent;
import com.traveleasy.booking.repository.BookingOutboxEventRepository;
import com.traveleasy.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingStateTransitionService {

    private final BookingRepository bookingRepository;
    private final BookingOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @jakarta.annotation.PostConstruct
    public void init() {
        if (objectMapper != null) {
            objectMapper.findAndRegisterModules();
        }
    }

    private static final Pattern SEATS_PATTERN = Pattern.compile("Seats:\\s*([^|\\n]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Atomically transitions a booking to CONFIRMED and enqueues a BUS_BOOKING_CONFIRMED outbox event.
     */
    @Transactional
    public Booking confirmBooking(Booking booking) {
        if (booking.getConfirmedAt() == null) {
            booking.setConfirmedAt(LocalDateTime.now());
        }
        if ("CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
            log.info("Booking {} is already CONFIRMED. Skipping transition.", booking.getBookingRef());
            return booking;
        }

        booking.setStatus("CONFIRMED");
        Booking saved = bookingRepository.save(booking);

        boolean isHotel = "HOTEL".equalsIgnoreCase(saved.getServiceType());
        String eventType = isHotel ? "HOTEL_BOOKING_CONFIRMED" : "BUS_BOOKING_CONFIRMED";
        String eventId = "BKG-EVT-" + saved.getBookingRef() + "-CONFIRMED";
        NotificationEventRequest eventRequest = buildConfirmedEventRequest(saved, eventId);
        persistOutboxRecordSafely(saved, eventType, eventId, eventRequest);

        log.info("Booking {} transitioned to CONFIRMED with outbox event {}", saved.getBookingRef(), eventId);
        return saved;
    }

    /**
     * Atomically transitions a booking to CANCELLED.
     * Enqueues an outbox event ONLY if the booking was previously CONFIRMED or CHECKED_IN.
     */
    @Transactional
    public Booking cancelBooking(Booking booking, Double refundAmount, String reason) {
        if ("CANCELLED".equalsIgnoreCase(booking.getStatus())) {
            log.info("Booking {} is already CANCELLED. Skipping transition.", booking.getBookingRef());
            return booking;
        }

        boolean wasConfirmed = "CONFIRMED".equalsIgnoreCase(booking.getStatus()) || "CHECKED_IN".equalsIgnoreCase(booking.getStatus());
        booking.setStatus("CANCELLED");
        if (refundAmount != null) {
            booking.setRefundAmount(refundAmount);
        }
        Booking saved = bookingRepository.save(booking);

        if (wasConfirmed) {
            boolean isHotel = "HOTEL".equalsIgnoreCase(saved.getServiceType());
            String eventType = isHotel ? "HOTEL_BOOKING_CANCELLED" : "BUS_BOOKING_CANCELLED";
            String eventId = "BKG-EVT-" + saved.getBookingRef() + "-CANCELLED";
            NotificationEventRequest eventRequest = buildCancelledEventRequest(saved, eventId, refundAmount, reason);
            persistOutboxRecordSafely(saved, eventType, eventId, eventRequest);
            log.info("Confirmed booking {} transitioned to CANCELLED with outbox event {}", saved.getBookingRef(), eventId);
        } else {
            log.info("Unconfirmed booking {} transitioned to CANCELLED. Outbox event omitted.", saved.getBookingRef());
        }

        return saved;
    }

    /**
     * Records an operational guest lifecycle outbox event for hotel stays (BD-09).
     */
    @Transactional
    public void recordHotelGuestLifecycleEvent(Booking booking, String eventType) {
        String eventId = "BKG-EVT-" + booking.getBookingRef() + "-" + eventType + "-" + System.currentTimeMillis();
        List<RecipientDto> recipients = buildRecipients(booking);

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("bookingRef", booking.getBookingRef());
        templateData.put("hotelName", booking.getServiceTitle() != null ? booking.getServiceTitle() : "Hotel");
        templateData.put("guestName", booking.getCustomerName() != null ? booking.getCustomerName() : "Guest");
        templateData.put("status", booking.getStatus());
        templateData.put("checkInDate", booking.resolveCheckInDate() != null ? booking.resolveCheckInDate().toString() : "");
        templateData.put("checkOutDate", booking.resolveCheckOutDate() != null ? booking.resolveCheckOutDate().toString() : "");

        String summary = switch (eventType) {
            case "HOTEL_GUEST_CHECKED_IN" -> "Guest " + booking.getCustomerName() + " checked in for booking " + booking.getBookingRef();
            case "HOTEL_GUEST_CHECKED_OUT" -> "Guest " + booking.getCustomerName() + " checked out for booking " + booking.getBookingRef();
            case "HOTEL_BOOKING_NO_SHOW" -> "Booking " + booking.getBookingRef() + " marked as NO_SHOW";
            default -> "Booking status updated: " + booking.getStatus();
        };

        EventContentDto content = EventContentDto.builder()
                .title("Hotel Reservation " + booking.getBookingRef() + " - " + booking.getStatus())
                .summary(summary)
                .actionUrl("/my-bookings")
                .template("hotel-lifecycle-update")
                .templateData(templateData)
                .build();

        NotificationEventRequest eventRequest = NotificationEventRequest.builder()
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType("BOOKING")
                .aggregateId(booking.getBookingRef())
                .timestamp(Instant.now())
                .recipients(recipients)
                .content(content)
                .build();

        persistOutboxRecordSafely(booking, eventType, eventId, eventRequest);
    }

    private void persistOutboxRecordSafely(Booking booking, String eventType, String eventId, NotificationEventRequest eventRequest) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(eventRequest);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload for eventId {}: {}", eventId, e.getMessage(), e);
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }

        BookingOutboxEvent outboxEvent = BookingOutboxEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType("BOOKING")
                .aggregateId(booking.getBookingRef())
                .payloadJson(payloadJson)
                .status("PENDING")
                .retryCount(0)
                .maxRetries(5)
                .createdAt(Instant.now())
                .build();

        try {
            outboxRepository.saveAndFlush(outboxEvent);
        } catch (DataIntegrityViolationException dive) {
            log.warn("Concurrent duplicate outbox event detected for eventId {}. Skipping duplicate insertion.", eventId);
        }
    }

    private NotificationEventRequest buildConfirmedEventRequest(Booking booking, String eventId) {
        List<RecipientDto> recipients = buildRecipients(booking);
        boolean isHotel = "HOTEL".equalsIgnoreCase(booking.getServiceType());

        String title = isHotel ? "Hotel Booking Confirmed - " + booking.getBookingRef() : "Booking Confirmed - " + booking.getBookingRef();
        String summary = isHotel ? "Your hotel booking at " + booking.getServiceTitle() + " (" + booking.getBookingRef() + ") is confirmed."
                                 : "Your bus booking " + booking.getBookingRef() + " is confirmed.";
        String template = isHotel ? "hotel-booking-confirmation" : "bus-booking-confirmation";
        String eventType = isHotel ? "HOTEL_BOOKING_CONFIRMED" : "BUS_BOOKING_CONFIRMED";

        String[] route = parseRoute(booking.getServiceTitle());
        String origin = route[0];
        String destination = route[1];
        String seats = extractSeats(booking.getDetailsJson());
        String journeyDate = booking.getBookingDate() != null ? booking.getBookingDate().toLocalDate().toString() : "";
        String departureTime = "Scheduled Departure";
        String operatorBusinessName = resolveOperatorName(booking);
        String totalFare = booking.getTotalAmount() != null ? String.format("%.2f", booking.getTotalAmount()) : "0.00";

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("bookingRef", booking.getBookingRef());
        templateData.put("origin", origin);
        templateData.put("destination", destination);
        templateData.put("journeyDate", journeyDate);
        templateData.put("departureTime", departureTime);
        templateData.put("seats", seats);
        templateData.put("operatorBusinessName", operatorBusinessName);
        templateData.put("totalFare", totalFare);
        if (isHotel) {
            templateData.put("hotelName", booking.getServiceTitle());
            templateData.put("checkInDate", booking.resolveCheckInDate() != null ? booking.resolveCheckInDate().toString() : "");
            templateData.put("checkOutDate", booking.resolveCheckOutDate() != null ? booking.resolveCheckOutDate().toString() : "");
        }

        EventContentDto content = EventContentDto.builder()
                .title(title)
                .summary(summary)
                .actionUrl("/my-bookings")
                .template(template)
                .templateData(templateData)
                .build();

        return NotificationEventRequest.builder()
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType("BOOKING")
                .aggregateId(booking.getBookingRef())
                .timestamp(Instant.now())
                .recipients(recipients)
                .content(content)
                .build();
    }

    private NotificationEventRequest buildCancelledEventRequest(Booking booking, String eventId, Double refundAmount, String reason) {
        List<RecipientDto> recipients = buildRecipients(booking);
        boolean isHotel = "HOTEL".equalsIgnoreCase(booking.getServiceType());

        String title = isHotel ? "Hotel Booking Cancelled - " + booking.getBookingRef() : "Booking Cancelled - " + booking.getBookingRef();
        String summary = isHotel ? "Your hotel booking at " + booking.getServiceTitle() + " (" + booking.getBookingRef() + ") has been cancelled."
                                 : "Your bus booking " + booking.getBookingRef() + " has been cancelled.";
        String template = isHotel ? "hotel-booking-cancelled" : "bus-booking-cancelled";
        String eventType = isHotel ? "HOTEL_BOOKING_CANCELLED" : "BUS_BOOKING_CANCELLED";

        String[] route = parseRoute(booking.getServiceTitle());
        String origin = route[0];
        String destination = route[1];
        String seats = extractSeats(booking.getDetailsJson());
        Double effectiveRefund = refundAmount != null ? refundAmount : booking.getRefundAmount();
        String formattedRefund = effectiveRefund != null ? String.format("%.2f", effectiveRefund) : "";

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("bookingRef", booking.getBookingRef());
        templateData.put("origin", origin);
        templateData.put("destination", destination);
        templateData.put("seats", seats);
        templateData.put("refundAmount", formattedRefund);
        if (isHotel) {
            templateData.put("hotelName", booking.getServiceTitle());
            templateData.put("cancellationFee", booking.getCancellationFee() != null ? String.format("%.2f", booking.getCancellationFee()) : "0.00");
            templateData.put("cancellationTier", booking.getCancellationPolicyTier());
        }

        EventContentDto content = EventContentDto.builder()
                .title(title)
                .summary(summary)
                .actionUrl("/my-bookings")
                .template(template)
                .templateData(templateData)
                .build();

        return NotificationEventRequest.builder()
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType("BOOKING")
                .aggregateId(booking.getBookingRef())
                .timestamp(Instant.now())
                .recipients(recipients)
                .content(content)
                .build();
    }

    private List<RecipientDto> buildRecipients(Booking booking) {
        List<RecipientDto> recipients = new ArrayList<>();

        // 1. Traveller recipient
        String customerName = (booking.getCustomerName() != null && !booking.getCustomerName().isBlank())
                ? booking.getCustomerName()
                : "Traveller";
        recipients.add(RecipientDto.builder()
                .role(RecipientRole.TRAVELLER)
                .userId(booking.getCustomerId())
                .name(customerName)
                .email(booking.getCustomerEmail())
                .channels(List.of("IN_APP", "EMAIL"))
                .build());

        // 2. Provider recipient (Bus Operator or Hotel Partner)
        if (booking.getProviderId() != null && booking.getProviderId() > 0) {
            boolean isHotel = "HOTEL".equalsIgnoreCase(booking.getServiceType());
            RecipientRole role = isHotel ? RecipientRole.HOTEL_PARTNER : RecipientRole.BUS_OPERATOR;
            String providerName = isHotel
                    ? (booking.getServiceTitle() != null ? booking.getServiceTitle() : "Hotel Partner")
                    : resolveOperatorName(booking);

            recipients.add(RecipientDto.builder()
                    .role(role)
                    .userId(booking.getProviderId())
                    .name(providerName)
                    .email(null)
                    .channels(List.of("IN_APP"))
                    .build());
        }

        return recipients;
    }

    private String resolveOperatorName(Booking booking) {
        if (booking.getOperatorBusinessName() != null && !booking.getOperatorBusinessName().isBlank()) {
            return booking.getOperatorBusinessName();
        }
        return "Bus Operator";
    }

    private String[] parseRoute(String serviceTitle) {
        if (serviceTitle == null || serviceTitle.isBlank()) {
            return new String[]{"Scheduled Origin", "Scheduled Destination"};
        }
        String cleaned = serviceTitle.replace(" - Bus", "").replace(" - Hotel", "").trim();
        if (cleaned.contains(" to ")) {
            String[] parts = cleaned.split(" to ", 2);
            return new String[]{parts[0].trim(), parts[1].trim()};
        }
        return new String[]{cleaned, "Scheduled Destination"};
    }

    private String extractSeats(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return "";
        }
        Matcher matcher = SEATS_PATTERN.matcher(detailsJson);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }
}
