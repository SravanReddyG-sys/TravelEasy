package com.traveleasy.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.booking.dto.CancellationPolicyResult;
import com.traveleasy.booking.model.Booking;
import com.traveleasy.booking.model.HotelCancellationRefundTask;
import com.traveleasy.booking.model.HotelInventoryReleaseTask;
import com.traveleasy.booking.model.HotelReservationSnapshot;
import com.traveleasy.booking.repository.BookingRepository;
import com.traveleasy.booking.repository.HotelCancellationRefundTaskRepository;
import com.traveleasy.booking.repository.HotelInventoryReleaseTaskRepository;
import com.traveleasy.booking.repository.HotelReservationSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Transactional state coordinator for the isolated HOTEL cancellation workflow (WP-HC-01).
 * Uses short PESSIMISTIC_WRITE DB transactions for state transitions and task creation,
 * never holding database row locks across remote HTTP calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotelCancellationStateService {

    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String RELEASE_PENDING = "PENDING";
    public static final String RELEASE_RELEASED = "RELEASED";
    public static final String RELEASE_FAILED_RETRYABLE = "FAILED_RETRYABLE";
    public static final String RELEASE_FAILED_FINAL = "FAILED_FINAL";

    public static final String REFUND_PENDING = "PENDING";
    public static final String REFUND_INITIATED = "INITIATED";
    public static final String REFUND_PROCESSED = "PROCESSED";
    public static final String REFUND_FAILED_RETRYABLE = "FAILED_RETRYABLE";
    public static final String REFUND_FAILED_FINAL = "FAILED_FINAL";
    public static final String REFUND_NON_REFUNDABLE = "NON_REFUNDABLE";

    private final BookingRepository bookingRepository;
    private final HotelReservationSnapshotRepository snapshotRepository;
    private final HotelInventoryReleaseTaskRepository releaseTaskRepository;
    private final HotelCancellationRefundTaskRepository refundTaskRepository;
    private final HotelCancellationPolicyService cancellationPolicyService;
    private final BookingStateTransitionService stateTransitionService;
    private final BookingSecurityService securityService;
    private final BookingInternalTokenService internalTokenService;
    private final ObjectMapper objectMapper;

    public record CallerContext(boolean internalService, Long userId, String actorRole) {
    }

    public record QuoteEvaluationContext(
            Booking booking,
            HotelReservationSnapshot snapshot,
            CancellationPolicyResult policyResult,
            boolean alreadyCancelled,
            boolean cancellable
    ) {
    }

    public record CancellationTransitionOutcome(
            Booking booking,
            CancellationPolicyResult policyResult,
            boolean alreadyCancelled,
            String previousStatus,
            HotelInventoryReleaseTask inventoryReleaseTask,
            HotelCancellationRefundTask refundTask
    ) {
    }

    @Transactional(readOnly = true)
    public QuoteEvaluationContext evaluateQuote(Long bookingId, String bookingRef,
                                                String authHeader, String internalToken, String internalService) {
        Booking booking = findBookingReadOnly(bookingId, bookingRef);
        resolveAndValidateCaller(booking, authHeader, internalToken, internalService);
        validateHotelServiceType(booking);

        HotelReservationSnapshot snapshot = snapshotRepository.findByBookingId(booking.getId()).orElse(null);

        if (STATUS_CANCELLED.equalsIgnoreCase(booking.getStatus())) {
            CancellationPolicyResult snapshotPolicy = reconstructPersistedPolicySnapshot(booking);
            return new QuoteEvaluationContext(booking, snapshot, snapshotPolicy, true, false);
        }

        if (!STATUS_CONFIRMED.equalsIgnoreCase(booking.getStatus())) {
            throw buildInvalidStatusException(booking.getStatus(), "quote");
        }

        CancellationPolicyResult policyResult = cancellationPolicyService.evaluateStrictHotelCancellationPolicy(
                booking, snapshot, LocalDateTime.now(HotelCancellationPolicyService.POLICY_ZONE_ID));

        return new QuoteEvaluationContext(booking, snapshot, policyResult, false, true);
    }

    @Transactional
    public CancellationTransitionOutcome beginCancellationTransition(
            Long bookingId,
            String bookingRef,
            String rawReason,
            String authHeader,
            String internalToken,
            String internalService) {

        Booking booking = findBookingForUpdate(bookingId, bookingRef);
        CallerContext caller = resolveAndValidateCaller(booking, authHeader, internalToken, internalService);
        validateHotelServiceType(booking);

        String currentStatus = booking.getStatus();
        if (STATUS_CANCELLED.equalsIgnoreCase(currentStatus)) {
            CancellationPolicyResult persistedPolicy = reconstructPersistedPolicySnapshot(booking);
            HotelInventoryReleaseTask existingReleaseTask = releaseTaskRepository.findByBookingId(booking.getId()).orElse(null);
            HotelCancellationRefundTask existingRefundTask = refundTaskRepository.findByBookingId(booking.getId()).orElse(null);
            log.info("Idempotent hotel cancellation replay detected for already-cancelled booking {}", booking.getBookingRef());
            return new CancellationTransitionOutcome(
                    booking,
                    persistedPolicy,
                    true,
                    STATUS_CANCELLED,
                    existingReleaseTask,
                    existingRefundTask
            );
        }

        if (!STATUS_CONFIRMED.equalsIgnoreCase(currentStatus)) {
            throw buildInvalidStatusException(currentStatus, "cancel");
        }

        HotelReservationSnapshot snapshot = snapshotRepository.findByBookingId(booking.getId()).orElse(null);
        LocalDateTime nowIst = LocalDateTime.now(HotelCancellationPolicyService.POLICY_ZONE_ID);
        CancellationPolicyResult policy = cancellationPolicyService.evaluateStrictHotelCancellationPolicy(
                booking, snapshot, nowIst);

        String effectiveReason = (rawReason != null && !rawReason.isBlank())
                ? rawReason.trim()
                : "Cancelled by customer";

        booking.setStatus(STATUS_CANCELLED);
        booking.setCancelledAt(nowIst);
        booking.setCancellationReason(effectiveReason);
        booking.setCancelledByUserId(caller.userId());
        booking.setCancelledByRole(caller.actorRole());
        booking.setCancellationPolicyTier(policy.getPolicyTier());
        booking.setCancellationFee(policy.getCancellationFee());
        booking.setCancellationPolicySnapshot(cancellationPolicyService.buildPolicySnapshotJson(policy));
        booking.setRefundAmount(policy.getRefundAmount());
        booking.setInventoryReleaseStatus(RELEASE_PENDING);

        if (policy.getRefundAmount() > 0.0) {
            if (booking.getRefundAttemptKey() == null || booking.getRefundAttemptKey().isBlank()) {
                booking.setRefundAttemptKey("htl-rfnd-" + booking.getBookingRef());
            }
            booking.setRefundStatus(REFUND_PENDING);
        } else {
            booking.setRefundAmount(0.0);
            booking.setRefundStatus(REFUND_NON_REFUNDABLE);
            booking.setRefundId(null);
        }

        Booking saved = bookingRepository.saveAndFlush(booking);
        if (saved == null) {
            saved = booking;
        }

        if (snapshot != null && !"RELEASED".equalsIgnoreCase(snapshot.getStatus())) {
            snapshot.setStatus(STATUS_CANCELLED);
            snapshot.setUpdatedAt(LocalDateTime.now());
            snapshotRepository.save(snapshot);
        }

        Long resolvedHotelId = (snapshot != null && snapshot.getHotelId() != null)
                ? snapshot.getHotelId()
                : saved.getServiceId();
        boolean isDateWise = saved.isDateWiseBooking() || (snapshot != null && snapshot.getCheckInDate() != null);

        Booking finalSaved = saved;
        HotelInventoryReleaseTask releaseTask = releaseTaskRepository.findByBookingId(saved.getId())
                .orElseGet(() -> {
                    HotelInventoryReleaseTask newTask = HotelInventoryReleaseTask.builder()
                            .bookingId(finalSaved.getId())
                            .bookingRef(finalSaved.getBookingRef())
                            .hotelId(resolvedHotelId)
                            .serviceId(finalSaved.getServiceId())
                            .dateWiseBooking(isDateWise)
                            .detailsJson(finalSaved.getDetailsJson())
                            .status(RELEASE_PENDING)
                            .attemptCount(0)
                            .maxAttempts(10)
                            .build();
                    return releaseTaskRepository.save(newTask);
                });

        HotelCancellationRefundTask refundTask = null;
        if (policy.getRefundAmount() > 0.0) {
            String attemptKey = saved.getRefundAttemptKey();
            double totalBookingAmt = saved.getTotalAmount() != null ? saved.getTotalAmount() : policy.getRefundAmount();
            refundTask = refundTaskRepository.findByBookingId(saved.getId())
                    .orElseGet(() -> {
                        HotelCancellationRefundTask newTask = HotelCancellationRefundTask.builder()
                                .bookingId(finalSaved.getId())
                                .bookingRef(finalSaved.getBookingRef())
                                .idempotencyKey(attemptKey)
                                .refundAmount(policy.getRefundAmount())
                                .totalBookingAmount(totalBookingAmt)
                                .cancellationFee(policy.getCancellationFee())
                                .policyTier(policy.getPolicyTier())
                                .reason(effectiveReason)
                                .status(REFUND_PENDING)
                                .attemptCount(0)
                                .maxAttempts(10)
                                .build();
                        return refundTaskRepository.save(newTask);
                    });
        }

        stateTransitionService.recordHotelCancellationOutboxEvent(
                saved, saved.getRefundStatus(), saved.getInventoryReleaseStatus());

        log.info("Committed hotel cancellation transition for booking {} (tier={}, refund={}, attemptKey={})",
                saved.getBookingRef(), policy.getPolicyTier(), saved.getRefundAmount(), saved.getRefundAttemptKey());

        return new CancellationTransitionOutcome(
                saved,
                policy,
                false,
                currentStatus,
                releaseTask,
                refundTask
        );
    }

    @Transactional
    public void recordInventoryReleaseSucceeded(Long bookingId, String bookingRef, Long taskId) {
        Instant now = Instant.now();
        LocalDateTime nowLocal = LocalDateTime.now(HotelCancellationPolicyService.POLICY_ZONE_ID);

        if (taskId != null) {
            releaseTaskRepository.markReleased(taskId, now);
        }

        findLockedOrPlainBooking(bookingId, bookingRef).ifPresent(b -> {
            b.setInventoryReleaseStatus(RELEASE_RELEASED);
            b.setInventoryReleasedAt(nowLocal);
            bookingRepository.save(b);
        });

        Optional<HotelReservationSnapshot> snapshotOpt = bookingRef != null
                ? snapshotRepository.findByBookingRef(bookingRef)
                : snapshotRepository.findByBookingId(bookingId);
        snapshotOpt.ifPresent(snapshot -> {
            snapshot.setStatus(RELEASE_RELEASED);
            snapshot.setUpdatedAt(LocalDateTime.now());
            if (snapshot.getAllocations() != null) {
                snapshot.getAllocations().forEach(a -> {
                    a.setStatus(RELEASE_RELEASED);
                    a.setUpdatedAt(LocalDateTime.now());
                });
            }
            snapshotRepository.save(snapshot);
        });
    }

    @Transactional
    public void recordInventoryReleaseFailure(Long bookingId, String bookingRef, Long taskId,
                                              String failureStatus, String error, Instant nextRetryAt) {
        Instant now = Instant.now();
        String safeError = truncate(error, 1950);

        if (taskId != null) {
            if (RELEASE_FAILED_FINAL.equals(failureStatus)) {
                releaseTaskRepository.markFailedFinal(taskId, safeError, now);
            } else {
                releaseTaskRepository.markFailedRetryable(taskId, safeError, nextRetryAt, now);
            }
        }

        findLockedOrPlainBooking(bookingId, bookingRef).ifPresent(b -> {
            if (!RELEASE_RELEASED.equalsIgnoreCase(b.getInventoryReleaseStatus())) {
                b.setInventoryReleaseStatus(failureStatus);
                bookingRepository.save(b);
            }
        });
    }

    @Transactional
    public void recordRefundSucceeded(Long bookingId, String bookingRef, Long taskId, String refundId) {
        Instant now = Instant.now();
        if (taskId != null) {
            refundTaskRepository.markProcessed(taskId, refundId, now);
        }

        findLockedOrPlainBooking(bookingId, bookingRef).ifPresent(b -> {
            b.setRefundStatus(REFUND_PROCESSED);
            if (refundId != null && !refundId.isBlank()) {
                b.setRefundId(refundId);
            }
            bookingRepository.save(b);
        });
    }

    @Transactional
    public void recordRefundInitiated(Long bookingId, String bookingRef, Long taskId,
                                      String refundId, Instant nextRetryAt, String note) {
        Instant now = Instant.now();
        if (taskId != null) {
            refundTaskRepository.markInitiated(taskId, refundId, truncate(note, 1950), nextRetryAt, now);
        }

        findLockedOrPlainBooking(bookingId, bookingRef).ifPresent(b -> {
            if (!REFUND_PROCESSED.equalsIgnoreCase(b.getRefundStatus())) {
                b.setRefundStatus(REFUND_INITIATED);
                if (refundId != null && !refundId.isBlank()) {
                    b.setRefundId(refundId);
                }
                bookingRepository.save(b);
            }
        });
    }

    @Transactional
    public void recordRefundFailure(Long bookingId, String bookingRef, Long taskId,
                                    String failureStatus, String error, Instant nextRetryAt) {
        Instant now = Instant.now();
        String safeError = truncate(error, 1950);
        if (taskId != null) {
            if (REFUND_FAILED_FINAL.equals(failureStatus)) {
                refundTaskRepository.markFailedFinal(taskId, safeError, now);
            } else {
                refundTaskRepository.markFailedRetryable(taskId, safeError, nextRetryAt, now);
            }
        }

        findLockedOrPlainBooking(bookingId, bookingRef).ifPresent(b -> {
            if (!REFUND_PROCESSED.equalsIgnoreCase(b.getRefundStatus())
                    && !REFUND_NON_REFUNDABLE.equalsIgnoreCase(b.getRefundStatus())) {
                b.setRefundStatus(failureStatus);
                bookingRepository.save(b);
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<Booking> findLatestBooking(Long bookingId, String bookingRef) {
        if (bookingId != null) {
            Optional<Booking> byId = bookingRepository.findById(bookingId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (bookingRef != null && !bookingRef.isBlank()) {
            return bookingRepository.findByBookingRef(bookingRef);
        }
        return Optional.empty();
    }

    public CallerContext resolveAndValidateCaller(Booking booking,
                                                  String authHeader,
                                                  String internalToken,
                                                  String internalService) {
        if (internalTokenService.verifyStrictInternalToken(authHeader, internalToken, internalService)) {
            return new CallerContext(true, null, "INTERNAL_SERVICE");
        }

        boolean suppliedInternalHeaders = (internalToken != null && !internalToken.isBlank())
                || (internalService != null && !internalService.isBlank());

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    suppliedInternalHeaders
                            ? "Unauthorized: Invalid or missing signed internal service token"
                            : "Authentication required for hotel booking cancellation");
        }

        String token = authHeader.substring(7).trim();
        if (token.isBlank() || !securityService.validateToken(token)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid or expired authorization token");
        }

        Long callerUserId = securityService.extractUserId(token);
        String rawRole = securityService.extractRole(token);
        String normalizedRole = rawRole != null ? rawRole.trim().toUpperCase() : "";

        if ("ROLE_ADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            return new CallerContext(false, callerUserId, normalizedRole);
        }

        if ("ROLE_CUSTOMER".equals(normalizedRole)
                || "CUSTOMER".equals(normalizedRole)
                || "ROLE_TRAVELLER".equals(normalizedRole)
                || "TRAVELLER".equals(normalizedRole)) {
            if (callerUserId == null || booking.getCustomerId() == null || !callerUserId.equals(booking.getCustomerId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Access denied: Cannot access or cancel another user's hotel booking");
            }
            return new CallerContext(false, callerUserId, normalizedRole);
        }

        if ("ROLE_HOTEL_MANAGER".equals(normalizedRole) || "HOTEL_MANAGER".equals(normalizedRole)) {
            if (callerUserId == null || booking.getProviderId() == null || !callerUserId.equals(booking.getProviderId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Access denied: Cannot cancel a reservation belonging to another hotel property");
            }
            return new CallerContext(false, callerUserId, normalizedRole);
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Access denied: Role is not permitted to cancel hotel bookings");
    }

    private void validateHotelServiceType(Booking booking) {
        if (!"HOTEL".equalsIgnoreCase(booking.getServiceType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dedicated hotel cancellation endpoints only support HOTEL bookings (found: " + booking.getServiceType() + ")");
        }
    }

    private ResponseStatusException buildInvalidStatusException(String status, String operation) {
        String normalized = status != null ? status.trim().toUpperCase() : "UNKNOWN";
        return switch (normalized) {
            case "CHECKED_IN" -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot " + operation + " booking: guest has already checked in");
            case "CHECKED_OUT", "COMPLETED" -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot " + operation + " booking: stay has already completed or checked out");
            case "NO_SHOW" -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot " + operation + " booking: reservation has been marked as NO_SHOW");
            case "EXPIRED" -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot " + operation + " booking: reservation hold has expired and inventory has been released");
            case "INITIATED", "PENDING_PAYMENT", "PENDING" -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot " + operation + " incomplete unpaid reservation (status: " + status + "). Only CONFIRMED hotel bookings can be cancelled.");
            default -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot " + operation + " hotel booking in status: " + status);
        };
    }

    public CancellationPolicyResult reconstructPersistedPolicySnapshot(Booking booking) {
        if (booking.getCancellationPolicySnapshot() != null && !booking.getCancellationPolicySnapshot().isBlank()) {
            try {
                return objectMapper.readValue(booking.getCancellationPolicySnapshot(), CancellationPolicyResult.class);
            } catch (Exception ex) {
                log.warn("Could not deserialize cancellationPolicySnapshot for booking {}: {}",
                        booking.getBookingRef(), ex.getMessage());
            }
        }
        double total = booking.getTotalAmount() != null ? booking.getTotalAmount() : 0.0;
        double refund = booking.getRefundAmount() != null ? booking.getRefundAmount() : 0.0;
        double fee = booking.getCancellationFee() != null ? booking.getCancellationFee() : Math.max(0.0, total - refund);
        double pct = total > 0.0 ? Math.round((refund / total) * 10000.0) / 100.0 : 0.0;
        return CancellationPolicyResult.builder()
                .policyTier(booking.getCancellationPolicyTier())
                .refundPercentage(pct)
                .refundAmount(refund)
                .cancellationFee(fee)
                .eligibleForRefund(refund > 0.0)
                .evaluationTime(booking.getCancelledAt())
                .scheduledCheckInTime(booking.resolveCheckInDate() != null
                        ? LocalDateTime.of(booking.resolveCheckInDate(), HotelCancellationPolicyService.STANDARD_CHECK_IN_TIME)
                        : null)
                .build();
    }

    private Booking findBookingReadOnly(Long bookingId, String bookingRef) {
        if (bookingId != null) {
            return bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with ID: " + bookingId));
        }
        if (bookingRef != null && !bookingRef.isBlank()) {
            return bookingRepository.findByBookingRef(bookingRef.trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with reference: " + bookingRef));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking ID or reference is required");
    }

    private Booking findBookingForUpdate(Long bookingId, String bookingRef) {
        if (bookingId != null) {
            return bookingRepository.findByIdForUpdate(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with ID: " + bookingId));
        }
        if (bookingRef != null && !bookingRef.isBlank()) {
            return bookingRepository.findByBookingRefForUpdate(bookingRef.trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with reference: " + bookingRef));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking ID or reference is required");
    }

    private Optional<Booking> findLockedOrPlainBooking(Long bookingId, String bookingRef) {
        if (bookingId != null) {
            Optional<Booking> locked = bookingRepository.findByIdForUpdate(bookingId);
            if (locked.isPresent()) {
                return locked;
            }
            return bookingRepository.findById(bookingId);
        }
        if (bookingRef != null && !bookingRef.isBlank()) {
            Optional<Booking> locked = bookingRepository.findByBookingRefForUpdate(bookingRef);
            if (locked.isPresent()) {
                return locked;
            }
            return bookingRepository.findByBookingRef(bookingRef);
        }
        return Optional.empty();
    }

    private String truncate(String val, int maxLen) {
        if (val == null) return null;
        return val.length() <= maxLen ? val : val.substring(0, maxLen);
    }
}
