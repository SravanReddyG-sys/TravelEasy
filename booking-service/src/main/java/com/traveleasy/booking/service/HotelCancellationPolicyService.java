package com.traveleasy.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.dto.CancellationPolicyResult;
import com.traveleasy.booking.model.Booking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelCancellationPolicyService {

    public static final LocalTime STANDARD_CHECK_IN_TIME = LocalTime.of(14, 0); // 2:00 PM standard check-in

    public static final String TIER_FREE_48H = "FREE_CANCELLATION_48H";
    public static final String TIER_PARTIAL_50 = "PARTIAL_REFUND_50_24H_48H";
    public static final String TIER_NON_REFUNDABLE_24H = "NON_REFUNDABLE_LESS_THAN_24H";
    public static final String TIER_PAST_CHECKIN = "PAST_CHECKIN_NON_REFUNDABLE";

    private final ObjectMapper objectMapper;

    /**
     * Evaluates platform-wide central cancellation policy rules (BD-04).
     */
    public static final java.time.ZoneId POLICY_ZONE_ID = java.time.ZoneId.of("Asia/Kolkata");

    /**
     * Strict fail-closed policy evaluation for the dedicated WP-HC-01 HOTEL cancellation workflow.
     * Enforces IST (Asia/Kolkata) 14:00 check-in reference time, sub-second Duration boundaries,
     * 2-decimal BigDecimal currency rounding, and fails closed if check-in date or booking total
     * is missing or invalid.
     */
    public CancellationPolicyResult evaluateStrictHotelCancellationPolicy(
            Booking booking,
            com.traveleasy.booking.model.HotelReservationSnapshot snapshot,
            LocalDateTime evaluationTime) {

        if (booking == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Booking record is required for hotel cancellation policy evaluation");
        }

        LocalDate checkInDate = resolveAuthoritativeCheckInDate(booking, snapshot);
        if (checkInDate == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Cannot evaluate hotel cancellation policy: missing or unresolvable check-in date for booking "
                            + booking.getBookingRef());
        }

        LocalDate checkOutDate = resolveAuthoritativeCheckOutDate(booking, snapshot);
        if (checkOutDate != null && !checkOutDate.isAfter(checkInDate)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Cannot evaluate hotel cancellation policy: invalid stay window (" + checkInDate + " to " + checkOutDate + ")");
        }

        if (booking.getTotalAmount() == null || booking.getTotalAmount() < 0.0 || !Double.isFinite(booking.getTotalAmount())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Cannot evaluate hotel cancellation policy: invalid booking total amount for booking " + booking.getBookingRef());
        }

        LocalDateTime effectiveEvalLocal = evaluationTime != null
                ? evaluationTime
                : LocalDateTime.now(POLICY_ZONE_ID);

        java.time.ZonedDateTime evalZdt = effectiveEvalLocal.atZone(POLICY_ZONE_ID);
        LocalDateTime scheduledCheckInLocal = LocalDateTime.of(checkInDate, STANDARD_CHECK_IN_TIME);
        java.time.ZonedDateTime scheduledCheckInZdt = scheduledCheckInLocal.atZone(POLICY_ZONE_ID);

        java.time.Duration remaining = java.time.Duration.between(evalZdt, scheduledCheckInZdt);
        long hoursUntilCheckIn = remaining.isNegative()
                ? remaining.toHours()
                : remaining.toHours();

        String tier;
        double refundPercentage;
        String description;

        if (remaining.isZero() || remaining.isNegative()) {
            tier = TIER_PAST_CHECKIN;
            refundPercentage = 0.0;
            description = "Cancellation is past scheduled check-in time (14:00 IST on check-in date). Non-refundable.";
        } else if (remaining.compareTo(java.time.Duration.ofHours(48)) >= 0) {
            tier = TIER_FREE_48H;
            refundPercentage = 100.0;
            description = "Free cancellation: >= 48 hours prior to scheduled 14:00 IST check-in. 100% refund.";
        } else if (remaining.compareTo(java.time.Duration.ofHours(24)) >= 0) {
            tier = TIER_PARTIAL_50;
            refundPercentage = 50.0;
            description = "Partial refund cancellation: between 24 and 48 hours prior to scheduled 14:00 IST check-in. 50% refund, 50% cancellation fee.";
        } else {
            tier = TIER_NON_REFUNDABLE_24H;
            refundPercentage = 0.0;
            description = "Late cancellation: less than 24 hours prior to scheduled 14:00 IST check-in. Non-refundable.";
        }

        java.math.BigDecimal totalBd = java.math.BigDecimal.valueOf(booking.getTotalAmount())
                .setScale(2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal refundBd = totalBd
                .multiply(java.math.BigDecimal.valueOf(refundPercentage))
                .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal feeBd = totalBd.subtract(refundBd)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        double refundAmount = refundBd.doubleValue();
        double cancellationFee = feeBd.doubleValue();

        return CancellationPolicyResult.builder()
                .policyTier(tier)
                .refundPercentage(refundPercentage)
                .refundAmount(refundAmount)
                .cancellationFee(cancellationFee)
                .description(description)
                .evaluationTime(effectiveEvalLocal)
                .scheduledCheckInTime(scheduledCheckInLocal)
                .hoursUntilCheckIn(hoursUntilCheckIn)
                .eligibleForRefund(refundAmount > 0.0)
                .build();
    }

    public LocalDate resolveAuthoritativeCheckInDate(
            Booking booking,
            com.traveleasy.booking.model.HotelReservationSnapshot snapshot) {
        if (booking != null && booking.getCheckInDate() != null) {
            return booking.getCheckInDate();
        }
        if (snapshot != null && snapshot.getCheckInDate() != null) {
            return snapshot.getCheckInDate();
        }
        return booking != null ? booking.resolveCheckInDate() : null;
    }

    public LocalDate resolveAuthoritativeCheckOutDate(
            Booking booking,
            com.traveleasy.booking.model.HotelReservationSnapshot snapshot) {
        if (booking != null && booking.getCheckOutDate() != null) {
            return booking.getCheckOutDate();
        }
        if (snapshot != null && snapshot.getCheckOutDate() != null) {
            return snapshot.getCheckOutDate();
        }
        return booking != null ? booking.resolveCheckOutDate() : null;
    }

    public CancellationPolicyResult evaluateCancellationPolicy(Booking booking, LocalDateTime evaluationTime) {
        if (evaluationTime == null) {
            evaluationTime = LocalDateTime.now();
        }

        double totalAmount = booking.getTotalAmount() != null ? booking.getTotalAmount() : 0.0;
        LocalDate checkInDate = booking.resolveCheckInDate();

        if (checkInDate == null) {
            log.warn("Booking {} has no resolvable check-in date. Defaulting to full refund.", booking.getBookingRef());
            return CancellationPolicyResult.builder()
                    .policyTier(TIER_FREE_48H)
                    .refundPercentage(100.0)
                    .refundAmount(totalAmount)
                    .cancellationFee(0.0)
                    .description("Default full refund applied: check-in date could not be established.")
                    .evaluationTime(evaluationTime)
                    .scheduledCheckInTime(evaluationTime.plusHours(72))
                    .hoursUntilCheckIn(72)
                    .eligibleForRefund(true)
                    .build();
        }

        LocalDateTime scheduledCheckInTime = LocalDateTime.of(checkInDate, STANDARD_CHECK_IN_TIME);

        if (evaluationTime.isAfter(scheduledCheckInTime)) {
            return CancellationPolicyResult.builder()
                    .policyTier(TIER_PAST_CHECKIN)
                    .refundPercentage(0.0)
                    .refundAmount(0.0)
                    .cancellationFee(totalAmount)
                    .description("Cancellation requested after scheduled check-in time (14:00 on " + checkInDate + "). Non-refundable.")
                    .evaluationTime(evaluationTime)
                    .scheduledCheckInTime(scheduledCheckInTime)
                    .hoursUntilCheckIn(0)
                    .eligibleForRefund(false)
                    .build();
        }

        long hoursUntilCheckIn = ChronoUnit.HOURS.between(evaluationTime, scheduledCheckInTime);

        if (hoursUntilCheckIn >= 48) {
            return CancellationPolicyResult.builder()
                    .policyTier(TIER_FREE_48H)
                    .refundPercentage(100.0)
                    .refundAmount(totalAmount)
                    .cancellationFee(0.0)
                    .description("Free cancellation: 100% refund permitted when cancelled at least 48 hours prior to check-in.")
                    .evaluationTime(evaluationTime)
                    .scheduledCheckInTime(scheduledCheckInTime)
                    .hoursUntilCheckIn(hoursUntilCheckIn)
                    .eligibleForRefund(true)
                    .build();
        } else if (hoursUntilCheckIn >= 24) {
            double fee = Math.round(totalAmount * 0.5 * 100.0) / 100.0;
            double refund = Math.round((totalAmount - fee) * 100.0) / 100.0;
            return CancellationPolicyResult.builder()
                    .policyTier(TIER_PARTIAL_50)
                    .refundPercentage(50.0)
                    .refundAmount(refund)
                    .cancellationFee(fee)
                    .description("Standard cancellation: 50% refund applied when cancelled between 24 and 48 hours prior to check-in.")
                    .evaluationTime(evaluationTime)
                    .scheduledCheckInTime(scheduledCheckInTime)
                    .hoursUntilCheckIn(hoursUntilCheckIn)
                    .eligibleForRefund(true)
                    .build();
        } else {
            return CancellationPolicyResult.builder()
                    .policyTier(TIER_NON_REFUNDABLE_24H)
                    .refundPercentage(0.0)
                    .refundAmount(0.0)
                    .cancellationFee(totalAmount)
                    .description("Late cancellation: cancellations within 24 hours of scheduled check-in are non-refundable.")
                    .evaluationTime(evaluationTime)
                    .scheduledCheckInTime(scheduledCheckInTime)
                    .hoursUntilCheckIn(hoursUntilCheckIn)
                    .eligibleForRefund(false)
                    .build();
        }
    }

    /**
     * Serializes policy evaluation into JSON snapshot for historical persistence (BD-07).
     */
    public String buildPolicySnapshotJson(CancellationPolicyResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Failed to serialize cancellation policy result: {}", e.getMessage());
            return "{\"policyTier\":\"" + result.getPolicyTier() + "\",\"refundPercentage\":" + result.getRefundPercentage() + "}";
        }
    }
}
