import { HotelBookingCardViewModel } from '../models/hotel-booking-view.model';
import { buildHotelBookingCardViewModel } from './hotel-booking-presenter.util';
import {
  HotelCancellationQuoteResponse,
  HotelCancellationResponse
} from '../models/hotel-cancellation.model';
import {
  HOTEL_CANCELLATION_REASON_MAX_LENGTH,
  buildHotelCancellationResultFromBooking,
  buildHotelCancellationResultViewModel,
  extractSafeCancellationApiError,
  formatCancellationStaySummaryText,
  formatHotelCancellationCurrency,
  formatHotelCancellationPercentage,
  formatPolicyTierDisplay,
  formatQuoteEvaluationHint,
  mapHotelCancellationRefundStatus,
  mapHotelInventoryReleaseStatus,
  resolveSubmittedCancellationReason
} from './hotel-cancellation-presenter.util';

function sampleCard(overrides: Partial<HotelBookingCardViewModel> = {}): HotelBookingCardViewModel {
  const built = buildHotelBookingCardViewModel({
    id: 901,
    bookingRef: 'HOTEL-DKG-20261111-CA74',
    serviceType: 'HOTEL',
    serviceId: 42,
    serviceTitle: 'GSR Grand Premium (Deluxe Room)',
    totalAmount: 2499,
    status: 'CONFIRMED',
    bookingDate: '2026-11-11T10:24:00',
    detailsJson: JSON.stringify({
      checkIn: '2026-11-11',
      checkOut: '2026-11-12',
      rooms: 1,
      adults: 2,
      children: 0
    })
  });
  return { ...built, ...overrides };
}

function sampleQuote(overrides: Partial<HotelCancellationQuoteResponse> = {}): HotelCancellationQuoteResponse {
  return {
    bookingId: 901,
    bookingRef: 'HOTEL-DKG-20261111-CA74',
    serviceType: 'HOTEL',
    currentStatus: 'CONFIRMED',
    cancellable: true,
    alreadyCancelled: false,
    policyTier: 'PARTIAL_REFUND_50_24H_48H',
    totalAmount: 2499,
    refundPercentage: 50,
    refundAmount: 1249,
    cancellationFee: 1250,
    eligibleForRefund: true,
    description: 'Cancelling between 24 and 48 hours before check-in provides a 50% refund.',
    checkInDate: '2026-11-11',
    checkOutDate: '2026-11-12',
    scheduledCheckInTime: '2026-11-11T14:00:00+05:30',
    evaluationTime: '2026-11-10T10:00:00+05:30',
    policyTimeZone: 'Asia/Kolkata',
    hoursUntilCheckIn: 28,
    ...overrides
  };
}

function sampleCancelResponse(
  overrides: Partial<HotelCancellationResponse> = {}
): HotelCancellationResponse {
  return {
    message: 'Hotel booking cancelled successfully.',
    bookingId: 901,
    bookingRef: 'HOTEL-DKG-20261111-CA74',
    serviceType: 'HOTEL',
    status: 'CANCELLED',
    idempotent: false,
    previousStatus: 'CONFIRMED',
    cancelledAt: '2026-11-11T11:02:00',
    cancellationReason: 'Change in travel plans',
    cancellationPolicy: {
      policyTier: 'PARTIAL_REFUND_50_24H_48H',
      refundPercentage: 50,
      refundAmount: 1249,
      cancellationFee: 1250,
      eligibleForRefund: true
    },
    refundAmount: 1249,
    cancellationFee: 1250,
    cancellationPolicyTier: 'PARTIAL_REFUND_50_24H_48H',
    inventoryReleaseStatus: 'RELEASED',
    refundStatus: 'INITIATED',
    refundId: 'rfnd_901_abc',
    refundAttemptKey: 'key_901',
    ...overrides
  };
}

describe('HotelCancellationPresenterUtil', () => {
  it('formats authoritative currency values and percentages cleanly', () => {
    expect(formatHotelCancellationCurrency(1249)).toBe('₹1,249');
    expect(formatHotelCancellationCurrency(0)).toBe('₹0');
    expect(formatHotelCancellationCurrency(null)).toBe('—');
    expect(formatHotelCancellationPercentage(50)).toBe('50%');
    expect(formatHotelCancellationPercentage(100)).toBe('100%');
    expect(formatHotelCancellationPercentage(0)).toBe('0%');
  });

  it('formats stay summary text cleanly from quote dates and card nights', () => {
    const text = formatCancellationStaySummaryText(sampleCard(), sampleQuote());
    expect(text).toBe('Stay: Nov 11, 2026 – Nov 12, 2026 (1 Night)');
    expect(formatQuoteEvaluationHint(sampleQuote())).toContain('28 hours remaining');
    expect(formatQuoteEvaluationHint(sampleQuote({ hoursUntilCheckIn: 0 }))).toContain('already passed');
  });

  it('maps all four backend policy tiers and unknown fallback', () => {
    expect(formatPolicyTierDisplay('FREE_CANCELLATION_48H').tone).toBe('full');
    expect(formatPolicyTierDisplay('FREE_CANCELLATION_48H').label).toBe('≥ 48 Hours');

    expect(formatPolicyTierDisplay('PARTIAL_REFUND_50_24H_48H').tone).toBe('partial');
    expect(formatPolicyTierDisplay('PARTIAL_REFUND_50_24H_48H').shortBadgeLabel).toBe('24 – 48 Hours');

    expect(formatPolicyTierDisplay('NON_REFUNDABLE_LESS_THAN_24H').tone).toBe('none');
    expect(formatPolicyTierDisplay('PAST_CHECKIN_NON_REFUNDABLE').label).toBe('After Check-in Cutoff');
    expect(formatPolicyTierDisplay('CUSTOM_SPECIAL_TIER').label).toBe('Custom Special Tier');
  });

  it('sanitizes optional cancellation reasons and omits empty input', () => {
    expect(resolveSubmittedCancellationReason('', '')).toBe(undefined);
    expect(resolveSubmittedCancellationReason('   ', '   ')).toBe(undefined);
    expect(resolveSubmittedCancellationReason('OTHER', '   ')).toBe(undefined);
    expect(resolveSubmittedCancellationReason('Change in travel plans', '')).toBe('Change in travel plans');

    const oversized = 'A'.repeat(HOTEL_CANCELLATION_REASON_MAX_LENGTH + 40);
    const trimmed = resolveSubmittedCancellationReason('OTHER', `  ${oversized}  `);
    expect(trimmed?.length).toBe(HOTEL_CANCELLATION_REASON_MAX_LENGTH);
  });

  it('maps every backend refund status accurately without over-claiming completion', () => {
    const processed = mapHotelCancellationRefundStatus('PROCESSED', 2499);
    expect(processed.badgeLabel).toBe('Refund Processed');
    expect(processed.tone).toBe('success');
    expect(processed.bannerMessage).toContain('has been processed');

    const initiated = mapHotelCancellationRefundStatus('INITIATED', 1249);
    expect(initiated.badgeLabel).toBe('Refund Initiated');
    expect(initiated.tone).toBe('info');
    expect(initiated.bannerMessage).toContain('5–7 business days');

    const pending = mapHotelCancellationRefundStatus('PENDING', 1249);
    expect(pending.badgeLabel).toBe('Refund Pending');
    expect(pending.tone).toBe('info');

    const retryable = mapHotelCancellationRefundStatus('FAILED_RETRYABLE', 1249);
    expect(retryable.badgeLabel).toBe('Refund Pending Retry');
    expect(retryable.tone).toBe('warning');

    const failedFinal = mapHotelCancellationRefundStatus('FAILED_FINAL', 1249);
    expect(failedFinal.badgeLabel).toBe('Refund Failed — Support Required');
    expect(failedFinal.tone).toBe('danger');
    expect(failedFinal.bannerMessage).toContain('TravelEasy Support');

    const nonRefundable = mapHotelCancellationRefundStatus('NON_REFUNDABLE', 0);
    expect(nonRefundable.badgeLabel).toBe('No Refund Applicable');
    expect(nonRefundable.tone).toBe('neutral');
  });

  it('maps inventory release statuses independently from refund states', () => {
    expect(mapHotelInventoryReleaseStatus('RELEASED').label).toBe('Released');
    expect(mapHotelInventoryReleaseStatus('RELEASED').bannerMessage).toBeNull();

    expect(mapHotelInventoryReleaseStatus('PENDING').label).toBe('Release Pending');
    expect(mapHotelInventoryReleaseStatus('FAILED_RETRYABLE').tone).toBe('warning');
    expect(mapHotelInventoryReleaseStatus('FAILED_RETRYABLE').bannerMessage).toContain('automatic background retry');
    expect(mapHotelInventoryReleaseStatus('FAILED_FINAL').label).toBe('Release Attention Needed');
  });

  it('builds Step 3 result view models for normal, mixed recovery, zero-refund, and idempotent outcomes', () => {
    const normal = buildHotelCancellationResultViewModel(sampleCancelResponse());
    expect(normal.headline).toBe('Booking Cancelled Successfully');
    expect(normal.formattedRefundAmount).toBe('₹1,249');
    expect(normal.formattedCancellationFee).toBe('₹1,250');
    expect(normal.refund.badgeLabel).toBe('Refund Initiated');
    expect(normal.inventory.label).toBe('Released');

    const retryInventory = buildHotelCancellationResultViewModel(
      sampleCancelResponse({
        refundStatus: 'PROCESSED',
        inventoryReleaseStatus: 'FAILED_RETRYABLE'
      })
    );
    expect(retryInventory.refund.badgeLabel).toBe('Refund Processed');
    expect(retryInventory.inventory.label).toBe('Release Retrying');
    expect(retryInventory.inventory.bannerMessage).toBeTruthy();

    const failedRefundReleasedRooms = buildHotelCancellationResultViewModel(
      sampleCancelResponse({
        refundStatus: 'FAILED_FINAL',
        inventoryReleaseStatus: 'RELEASED'
      })
    );
    expect(failedRefundReleasedRooms.headerTone).toBe('warning');
    expect(failedRefundReleasedRooms.refund.tone).toBe('danger');
    expect(failedRefundReleasedRooms.inventory.label).toBe('Released');

    const zeroRefund = buildHotelCancellationResultViewModel(
      sampleCancelResponse({
        refundStatus: 'NON_REFUNDABLE',
        refundAmount: 0,
        cancellationFee: 2499,
        cancellationPolicyTier: 'NON_REFUNDABLE_LESS_THAN_24H'
      })
    );
    expect(zeroRefund.formattedRefundAmount).toBe('₹0');
    expect(zeroRefund.refund.badgeLabel).toBe('No Refund Applicable');

    const idempotent = buildHotelCancellationResultViewModel(
      sampleCancelResponse({ idempotent: true, message: 'Hotel booking was already cancelled.' })
    );
    expect(idempotent.idempotent).toBe(true);
    expect(idempotent.headline).toBe('Booking Already Cancelled');
    expect(idempotent.subtitle).toBe('Hotel booking was already cancelled.');
  });

  it('reconstructs Step 3 result from GET booking reconciliation after timeout', () => {
    const reconciled = buildHotelCancellationResultFromBooking(
      {
        id: 901,
        bookingRef: 'HOTEL-DKG-20261111-CA74',
        status: 'CANCELLED',
        refundStatus: 'INITIATED',
        refundAmount: 1249,
        cancellationFee: 1250,
        cancellationPolicyTier: 'PARTIAL_REFUND_50_24H_48H',
        inventoryReleaseStatus: 'RELEASED',
        cancelledAt: '2026-11-11T11:02:00'
      },
      'HOTEL-DKG-20261111-CA74'
    );
    expect(reconciled.reconciledFromServerProbe).toBe(true);
    expect(reconciled.headline).toBe('Booking Cancelled — Status Verified');
    expect(reconciled.refund.badgeLabel).toBe('Refund Initiated');
    expect(reconciled.inventory.label).toBe('Released');
  });

  it('extracts safe user-facing errors without leaking technical payloads', () => {
    const timeoutErr = extractSafeCancellationApiError({ name: 'TimeoutError' });
    expect(timeoutErr.isTimeoutOrUncertain).toBe(true);
    expect(timeoutErr.message).toContain('timed out');

    const unauthErr = extractSafeCancellationApiError({ status: 401, error: {} });
    expect(unauthErr.isAuthError).toBe(true);
    expect(unauthErr.message).toContain('session has expired');

    const forbiddenErr = extractSafeCancellationApiError({
      status: 403,
      error: { message: 'Only the booking owner can cancel this reservation.' }
    });
    expect(forbiddenErr.isAuthError).toBe(true);
    expect(forbiddenErr.message).toContain('Only the booking owner');

    const statusChangedErr = extractSafeCancellationApiError({
      status: 400,
      error: { message: 'Booking cannot be cancelled after check-in.' }
    });
    expect(statusChangedErr.isStatusChanged).toBe(true);
    expect(statusChangedErr.message).toBe('Booking cannot be cancelled after check-in.');

    const htmlDumpErr = extractSafeCancellationApiError({
      status: 500,
      error: '<html><body>500 Stacktrace</body></html>',
      message: 'Http failure response for /api/bookings/hotel/1/cancel: 500 Internal Server Error'
    });
    expect(htmlDumpErr.message).not.toContain('<html>');
    expect(htmlDumpErr.message).not.toContain('Http failure response');
  });
});
