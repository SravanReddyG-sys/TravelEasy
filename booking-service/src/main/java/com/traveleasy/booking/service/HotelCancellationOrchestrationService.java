package com.traveleasy.booking.service;

import com.traveleasy.booking.dto.CancellationPolicyResult;
import com.traveleasy.booking.dto.HotelCancellationQuoteResponse;
import com.traveleasy.booking.dto.HotelCancellationRequest;
import com.traveleasy.booking.dto.HotelCancellationResponse;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.service.HotelCancellationStateService.CancellationTransitionOutcome;
import com.traveleasy.booking.service.HotelCancellationStateService.QuoteEvaluationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * Orchestrates the dedicated WP-HC-01 HOTEL booking cancellation quote and execution workflow.
 * Guarantees strict BUS isolation, auth/ownership enforcement, fail-closed policy evaluation,
 * independent booking/inventory/refund state tracking, and fault-tolerant durable recovery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotelCancellationOrchestrationService {

    private final HotelCancellationStateService stateService;
    private final HotelCancellationPolicyService cancellationPolicyService;
    private final HotelInventoryReleaseRecoveryService inventoryReleaseRecoveryService;
    private final HotelCancellationRefundRecoveryService refundRecoveryService;

    public HotelCancellationQuoteResponse getCancellationQuoteById(
            Long bookingId, String authHeader, String internalToken, String internalService) {
        QuoteEvaluationContext ctx = stateService.evaluateQuote(bookingId, null, authHeader, internalToken, internalService);
        return toQuoteResponse(ctx);
    }

    public HotelCancellationQuoteResponse getCancellationQuoteByRef(
            String bookingRef, String authHeader, String internalToken, String internalService) {
        QuoteEvaluationContext ctx = stateService.evaluateQuote(null, bookingRef, authHeader, internalToken, internalService);
        return toQuoteResponse(ctx);
    }

    public HotelCancellationResponse cancelHotelBookingById(
            Long bookingId,
            HotelCancellationRequest request,
            String authHeader,
            String internalToken,
            String internalService) {
        return executeCancellation(bookingId, null, request, authHeader, internalToken, internalService);
    }

    public HotelCancellationResponse cancelHotelBookingByRef(
            String bookingRef,
            HotelCancellationRequest request,
            String authHeader,
            String internalToken,
            String internalService) {
        return executeCancellation(null, bookingRef, request, authHeader, internalToken, internalService);
    }

    private HotelCancellationResponse executeCancellation(
            Long bookingId,
            String bookingRef,
            HotelCancellationRequest request,
            String authHeader,
            String internalToken,
            String internalService) {

        String reason = request != null ? request.getReason() : null;
        CancellationTransitionOutcome outcome = stateService.beginCancellationTransition(
                bookingId, bookingRef, reason, authHeader, internalToken, internalService);

        Booking booking = outcome.booking();
        Map<String, Object> refundPayload = null;

        if (outcome.alreadyCancelled()) {
            // Replay: never recalculate policy or mutate original amounts.
            // Safely coordinate pending/retryable inventory release or refund using durable tasks.
            String invStatus = booking.getInventoryReleaseStatus();
            if (HotelCancellationStateService.RELEASE_PENDING.equalsIgnoreCase(invStatus)
                    || HotelCancellationStateService.RELEASE_FAILED_RETRYABLE.equalsIgnoreCase(invStatus)) {
                inventoryReleaseRecoveryService.retryIfPending(booking.getBookingRef());
            }

            Double persistedRefundAmount = booking.getRefundAmount();
            String rfdStatus = booking.getRefundStatus();
            if (persistedRefundAmount != null && persistedRefundAmount > 0.0
                    && (HotelCancellationStateService.REFUND_PENDING.equalsIgnoreCase(rfdStatus)
                    || HotelCancellationStateService.REFUND_FAILED_RETRYABLE.equalsIgnoreCase(rfdStatus)
                    || HotelCancellationStateService.REFUND_INITIATED.equalsIgnoreCase(rfdStatus))) {
                refundPayload = refundRecoveryService.retryIfPending(booking.getBookingRef());
            }
        } else {
            // Initial cancellation: execute immediate downstream calls outside the DB row lock.
            if (outcome.inventoryReleaseTask() != null) {
                inventoryReleaseRecoveryService.attemptImmediateRelease(outcome.inventoryReleaseTask());
            }
            if (outcome.refundTask() != null && outcome.policyResult().getRefundAmount() > 0.0) {
                refundPayload = refundRecoveryService.attemptImmediateRefund(outcome.refundTask());
            }
        }

        Booking latest = stateService.findLatestBooking(booking.getId(), booking.getBookingRef())
                .orElse(booking);

        return toCancellationResponse(latest, outcome, refundPayload);
    }

    private HotelCancellationQuoteResponse toQuoteResponse(QuoteEvaluationContext ctx) {
        Booking booking = ctx.booking();
        CancellationPolicyResult policy = ctx.policyResult();
        LocalDate checkInDate = cancellationPolicyService.resolveAuthoritativeCheckInDate(booking, ctx.snapshot());
        LocalDate checkOutDate = cancellationPolicyService.resolveAuthoritativeCheckOutDate(booking, ctx.snapshot());

        return HotelCancellationQuoteResponse.builder()
                .bookingId(booking.getId())
                .bookingRef(booking.getBookingRef())
                .serviceType(booking.getServiceType())
                .currentStatus(booking.getStatus())
                .cancellable(ctx.cancellable())
                .alreadyCancelled(ctx.alreadyCancelled())
                .policyTier(policy.getPolicyTier())
                .totalAmount(booking.getTotalAmount() != null ? booking.getTotalAmount() : 0.0)
                .refundPercentage(policy.getRefundPercentage())
                .refundAmount(policy.getRefundAmount())
                .cancellationFee(policy.getCancellationFee())
                .eligibleForRefund(policy.isEligibleForRefund())
                .description(policy.getDescription())
                .checkInDate(checkInDate)
                .checkOutDate(checkOutDate)
                .scheduledCheckInTime(policy.getScheduledCheckInTime())
                .evaluationTime(policy.getEvaluationTime())
                .policyTimeZone(HotelCancellationPolicyService.POLICY_ZONE_ID.getId())
                .hoursUntilCheckIn(policy.getHoursUntilCheckIn())
                .build();
    }

    private HotelCancellationResponse toCancellationResponse(
            Booking latest,
            CancellationTransitionOutcome outcome,
            Map<String, Object> refundPayload) {

        String message = buildResponseMessage(latest, outcome.alreadyCancelled());

        return HotelCancellationResponse.builder()
                .message(message)
                .bookingId(latest.getId())
                .bookingRef(latest.getBookingRef())
                .serviceType(latest.getServiceType())
                .status(latest.getStatus())
                .idempotent(outcome.alreadyCancelled())
                .previousStatus(outcome.previousStatus())
                .cancelledAt(latest.getCancelledAt())
                .cancellationReason(latest.getCancellationReason())
                .cancellationPolicy(outcome.policyResult())
                .refundAmount(latest.getRefundAmount())
                .cancellationFee(latest.getCancellationFee())
                .cancellationPolicyTier(latest.getCancellationPolicyTier())
                .inventoryReleaseStatus(latest.getInventoryReleaseStatus())
                .refundStatus(latest.getRefundStatus())
                .refundId(latest.getRefundId())
                .refundAttemptKey(latest.getRefundAttemptKey())
                .booking(latest)
                .refund(refundPayload)
                .build();
    }

    private String buildResponseMessage(Booking booking, boolean idempotent) {
        StringBuilder sb = new StringBuilder();
        sb.append(idempotent
                ? "Hotel booking is already cancelled."
                : "Hotel booking cancelled successfully.");

        if (HotelCancellationStateService.RELEASE_RELEASED.equalsIgnoreCase(booking.getInventoryReleaseStatus())) {
            sb.append(" Room inventory has been released.");
        } else if (HotelCancellationStateService.RELEASE_FAILED_FINAL.equalsIgnoreCase(booking.getInventoryReleaseStatus())) {
            sb.append(" Room inventory release failed permanently and requires manual review.");
        } else {
            sb.append(" Room inventory release is queued for recovery.");
        }

        if (HotelCancellationStateService.REFUND_NON_REFUNDABLE.equalsIgnoreCase(booking.getRefundStatus())) {
            sb.append(" Booking is non-refundable under cancellation policy.");
        } else if (HotelCancellationStateService.REFUND_PROCESSED.equalsIgnoreCase(booking.getRefundStatus())) {
            sb.append(" Refund has been processed.");
        } else if (HotelCancellationStateService.REFUND_INITIATED.equalsIgnoreCase(booking.getRefundStatus())) {
            sb.append(" Refund has been initiated with payment gateway.");
        } else if (HotelCancellationStateService.REFUND_FAILED_FINAL.equalsIgnoreCase(booking.getRefundStatus())) {
            sb.append(" Refund failed permanently and requires manual intervention.");
        } else if (booking.getRefundAmount() != null && booking.getRefundAmount() > 0.0) {
            sb.append(" Refund is queued for recovery.");
        }
        return sb.toString();
    }
}
