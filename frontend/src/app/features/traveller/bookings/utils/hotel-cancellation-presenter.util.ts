import {
  HotelBookingCardViewModel
} from '../models/hotel-booking-view.model';
import {
  HotelCancellationPolicyTierDisplay,
  HotelCancellationQuoteResponse,
  HotelCancellationRefundPresentation,
  HotelCancellationResponse,
  HotelCancellationResultViewModel,
  HotelCancellationUiTone,
  HotelInventoryReleasePresentation
} from '../models/hotel-cancellation.model';
import { formatAuthoritativeAmount } from '../../../../shared/utils/hotel-payment.util';
import {
  computeCalendarNights,
  formatHotelCalendarDate,
  formatHotelDateTime
} from './hotel-booking-presenter.util';

export const HOTEL_CANCELLATION_REASON_MAX_LENGTH = 250;

export const HOTEL_STANDARD_CHECKIN_SUMMARY = 'Standard check-in time: 2:00 PM';

export const HOTEL_CANCELLATION_POLICY_SUMMARY_LINES: ReadonlyArray<string> = [
  '≥ 48 hours before check-in: 100% refund',
  '24 – 48 hours before check-in: 50% refund',
  '< 24 hours before check-in: 0% refund',
  'After 2:00 PM on check-in day: 0% refund'
];

export const HOTEL_CANCELLATION_REASON_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: '', label: 'Select a reason (optional)' },
  { value: 'Change in travel plans', label: 'Change in travel plans' },
  { value: 'Found a better hotel option', label: 'Found a better hotel option' },
  { value: 'Flight or transport schedule changed', label: 'Flight or transport schedule changed' },
  { value: 'Booked wrong dates or room type', label: 'Booked wrong dates or room type' },
  { value: 'Personal or family emergency', label: 'Personal or family emergency' },
  { value: 'Weather or travel disruption', label: 'Weather or travel disruption' },
  { value: 'OTHER', label: 'Other reason' }
];

export interface ParsedCancellationApiError {
  message: string;
  httpStatus: number | null;
  isTimeoutOrUncertain: boolean;
  isAuthError: boolean;
  isStatusChanged: boolean;
  isNotFound: boolean;
}

/** Parses optional numeric input without throwing on stringified BigDecimals. */
function parseNumericAmount(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

/** Formats monetary values using TravelEasy's shared currency formatter. */
export function formatHotelCancellationCurrency(
  amount: number | string | null | undefined,
  currency: string = 'INR'
): string {
  return formatAuthoritativeAmount(parseNumericAmount(amount), currency);
}

/** Formats authoritative backend refund percentages (e.g. `50%`). */
export function formatHotelCancellationPercentage(percentage: number | string | null | undefined): string {
  const n = parseNumericAmount(percentage);
  if (n === null) {
    return '—';
  }
  return `${Math.round(n * 100) / 100}%`;
}

/** Formats the cancellation modal stay subtitle cleanly from booking card or quote dates. */
export function formatCancellationStaySummaryText(
  card: HotelBookingCardViewModel | null | undefined,
  quote: HotelCancellationQuoteResponse | null | undefined
): string {
  if (quote?.checkInDate && quote?.checkOutDate) {
    const checkInShort = formatShortStayCalendarDate(quote.checkInDate);
    const checkOutShort = formatShortStayCalendarDate(quote.checkOutDate);
    if (checkInShort && checkOutShort) {
      const nights = computeCalendarNights(quote.checkInDate, quote.checkOutDate, card?.nightsCount);
      if (nights && nights > 0) {
        const unit = nights === 1 ? 'Night' : 'Nights';
        return `Stay: ${checkInShort} – ${checkOutShort} (${nights} ${unit})`;
      }
      return `Stay: ${checkInShort} – ${checkOutShort}`;
    }
  }

  if (card?.stayDatesText && card.stayDatesText !== 'Dates unavailable') {
    const nightsSuffix = card.nightsText ? ` (${card.nightsText})` : '';
    return `Stay: ${card.stayDatesText}${nightsSuffix}`;
  }

  return 'Stay dates unavailable';
}

function formatShortStayCalendarDate(rawDate: string): string {
  const formatted = formatHotelCalendarDate(rawDate);
  // formatHotelCalendarDate returns e.g. "Wed, Nov 11, 2026" -> keep compact "Nov 11, 2026" if weekday prefix present
  const parts = formatted.split(', ');
  if (parts.length === 3) {
    return `${parts[1]}, ${parts[2]}`;
  }
  return formatted;
}

/** Maps backend policy tier enums into crisp display labels matching the approved modal UI. */
export function formatPolicyTierDisplay(rawTier?: string | null): HotelCancellationPolicyTierDisplay {
  const normalized = (rawTier || '').toUpperCase().trim();
  switch (normalized) {
    case 'FREE_CANCELLATION_48H':
      return {
        rawTier: normalized,
        label: '≥ 48 Hours',
        shortBadgeLabel: '≥ 48 Hours',
        tone: 'full'
      };
    case 'PARTIAL_REFUND_50_24H_48H':
      return {
        rawTier: normalized,
        label: '24 – 48 Hours',
        shortBadgeLabel: '24 – 48 Hours',
        tone: 'partial'
      };
    case 'NON_REFUNDABLE_LESS_THAN_24H':
      return {
        rawTier: normalized,
        label: '< 24 Hours',
        shortBadgeLabel: '< 24 Hours',
        tone: 'none'
      };
    case 'PAST_CHECKIN_NON_REFUNDABLE':
      return {
        rawTier: normalized,
        label: 'After Check-in Cutoff',
        shortBadgeLabel: 'Past Check-in',
        tone: 'none'
      };
    default: {
      const fallback = normalized
        ? normalized
            .toLowerCase()
            .replace(/_/g, ' ')
            .replace(/\b\w/g, char => char.toUpperCase())
        : 'Standard Policy';
      return {
        rawTier: normalized || 'UNKNOWN',
        label: fallback,
        shortBadgeLabel: fallback,
        tone: 'none'
      };
    }
  }
}

/** Resolves and sanitizes optional cancellation reason so blank inputs are omitted. */
export function resolveSubmittedCancellationReason(
  selectedOption: string | null | undefined,
  customReason: string | null | undefined
): string | undefined {
  const selected = (selectedOption || '').trim();
  if (!selected) {
    return undefined;
  }
  const rawValue = selected === 'OTHER' ? (customReason || '').trim() : selected;
  if (!rawValue) {
    return undefined;
  }
  return rawValue.slice(0, HOTEL_CANCELLATION_REASON_MAX_LENGTH);
}

/** Maps backend `refundStatus` values honestly without optimism. */
export function mapHotelCancellationRefundStatus(
  rawStatus: string | null | undefined,
  refundAmount: number | null | undefined,
  currency: string = 'INR'
): HotelCancellationRefundPresentation {
  const normalized = (rawStatus || '').toUpperCase().trim();
  const numericAmount = parseNumericAmount(refundAmount);

  if (normalized === 'PROCESSED' || normalized === 'REFUNDED' || normalized === 'COMPLETED') {
    const formatted = numericAmount !== null ? formatHotelCancellationCurrency(numericAmount, currency) : null;
    return {
      normalizedStatus: 'PROCESSED',
      badgeLabel: 'Refund Processed',
      tone: 'success',
      iconClass: 'fa-solid fa-circle-check',
      bannerTitle: 'Refund Processed',
      bannerMessage: formatted
        ? `Your refund of ${formatted} has been processed to your original payment method.`
        : 'Your refund has been processed to your original payment method.'
    };
  }

  if (normalized === 'INITIATED' || normalized === 'PENDING' || normalized === 'CREATED') {
    const isInitiated = normalized === 'INITIATED';
    return {
      normalizedStatus: normalized,
      badgeLabel: isInitiated ? 'Refund Initiated' : 'Refund Pending',
      tone: 'info',
      iconClass: 'fa-regular fa-clock',
      bannerTitle: isInitiated ? 'Refund Initiated' : 'Refund Pending',
      bannerMessage:
        'Your refund has been initiated. The amount will be credited to your original payment method within 5–7 business days.'
    };
  }

  if (normalized === 'FAILED_RETRYABLE') {
    return {
      normalizedStatus: 'FAILED_RETRYABLE',
      badgeLabel: 'Refund Pending Retry',
      tone: 'warning',
      iconClass: 'fa-solid fa-clock-rotate-left',
      bannerTitle: 'Refund Queued for Automatic Retry',
      bannerMessage:
        'Your booking is cancelled, and your refund request is temporarily queued for automatic retry. Check My Bookings shortly for updates.'
    };
  }

  if (normalized === 'FAILED_FINAL' || normalized === 'FAILED') {
    return {
      normalizedStatus: 'FAILED_FINAL',
      badgeLabel: 'Refund Failed — Support Required',
      tone: 'danger',
      iconClass: 'fa-solid fa-triangle-exclamation',
      bannerTitle: 'Automatic Refund Failed',
      bannerMessage:
        'Your booking is cancelled, but the automatic gateway refund could not be completed. Please contact TravelEasy Support with your Booking ID.'
    };
  }

  if (
    normalized === 'NON_REFUNDABLE' ||
    normalized === 'NOT_APPLICABLE' ||
    normalized === 'NONE' ||
    normalized === 'ZERO_REFUND' ||
    (!normalized && numericAmount === 0)
  ) {
    return {
      normalizedStatus: 'NON_REFUNDABLE',
      badgeLabel: 'No Refund Applicable',
      tone: 'neutral',
      iconClass: 'fa-solid fa-ban',
      bannerTitle: 'No Refund Applicable',
      bannerMessage:
        'This cancellation fell within the non-refundable window (₹0 refundable per the hotel cancellation policy).'
    };
  }

  return {
    normalizedStatus: normalized || 'UNKNOWN',
    badgeLabel: 'Refund Status Updating',
    tone: 'info',
    iconClass: 'fa-solid fa-circle-info',
    bannerTitle: 'Cancellation Recorded',
    bannerMessage:
      'Your hotel booking is cancelled. Check My Bookings shortly or contact support for the latest refund status.'
  };
}

/** Maps backend `inventoryReleaseStatus` independently from cancellation and refund outcomes. */
export function mapHotelInventoryReleaseStatus(
  rawStatus: string | null | undefined
): HotelInventoryReleasePresentation {
  const normalized = (rawStatus || '').toUpperCase().trim();

  if (normalized === 'RELEASED') {
    return {
      normalizedStatus: 'RELEASED',
      label: 'Released',
      tone: 'success',
      iconClass: 'fa-solid fa-circle-check',
      bannerMessage: null
    };
  }

  if (normalized === 'PENDING' || normalized === 'PROCESSING') {
    return {
      normalizedStatus: 'PENDING',
      label: 'Release Pending',
      tone: 'info',
      iconClass: 'fa-regular fa-clock',
      bannerMessage: 'Room inventory release is still being finalized in the background.'
    };
  }

  if (normalized === 'FAILED_RETRYABLE') {
    return {
      normalizedStatus: 'FAILED_RETRYABLE',
      label: 'Release Retrying',
      tone: 'warning',
      iconClass: 'fa-solid fa-clock-rotate-left',
      bannerMessage:
        'Your booking is cancelled; room inventory release is queued for automatic background retry.'
    };
  }

  if (normalized === 'FAILED_FINAL' || normalized === 'FAILED') {
    return {
      normalizedStatus: 'FAILED_FINAL',
      label: 'Release Attention Needed',
      tone: 'warning',
      iconClass: 'fa-solid fa-triangle-exclamation',
      bannerMessage:
        'Your booking is cancelled, while background room inventory release requires internal reconciliation and will not affect your cancellation.'
    };
  }

  return {
    normalizedStatus: normalized || 'UNKNOWN',
    label: normalized ? normalized.replace(/_/g, ' ') : 'Updating',
    tone: 'neutral',
    iconClass: 'fa-solid fa-circle-info',
    bannerMessage: null
  };
}

/** Builds the Step 3 result view-model directly from the authoritative `PUT /cancel` response. */
export function buildHotelCancellationResultViewModel(
  response: HotelCancellationResponse,
  currency: string = 'INR',
  options?: { reconciledFromServerProbe?: boolean }
): HotelCancellationResultViewModel {
  const bookingFallback = response.booking || null;
  const refundAmount =
    parseNumericAmount(response.refundAmount) ??
    parseNumericAmount(response.cancellationPolicy?.refundAmount) ??
    parseNumericAmount(bookingFallback?.refundAmount) ??
    0;

  const cancellationFee =
    parseNumericAmount(response.cancellationFee) ??
    parseNumericAmount(response.cancellationPolicy?.cancellationFee) ??
    parseNumericAmount(bookingFallback?.cancellationFee);

  const rawTier =
    response.cancellationPolicyTier ??
    response.cancellationPolicy?.policyTier ??
    bookingFallback?.cancellationPolicyTier ??
    null;

  const refundPresentation = mapHotelCancellationRefundStatus(
    response.refundStatus ?? bookingFallback?.refundStatus ?? null,
    refundAmount,
    currency
  );

  const inventoryPresentation = mapHotelInventoryReleaseStatus(
    response.inventoryReleaseStatus ?? bookingFallback?.inventoryReleaseStatus ?? null
  );

  const cancelledAtRaw = response.cancelledAt ?? bookingFallback?.cancelledAt ?? null;
  const cancelledAtText = formatHotelDateTime(cancelledAtRaw);
  const reconciledFromServerProbe = Boolean(options?.reconciledFromServerProbe);
  const idempotent = Boolean(response.idempotent);

  const { headline, subtitle } = resolveResultHeadline(
    idempotent,
    reconciledFromServerProbe,
    response.message
  );

  const headerTone: HotelCancellationUiTone =
    refundPresentation.tone === 'danger' || refundPresentation.tone === 'warning'
      ? 'warning'
      : 'success';

  const headerIconClass =
    headerTone === 'warning' ? 'fa-solid fa-circle-exclamation' : 'fa-solid fa-check';

  return {
    bookingId: response.bookingId ?? bookingFallback?.id ?? 0,
    bookingRef: response.bookingRef || bookingFallback?.bookingRef || '—',
    bookingStatus: (response.status || bookingFallback?.status || 'CANCELLED').toUpperCase(),
    idempotent,
    headline,
    subtitle,
    headerIconClass,
    headerTone,
    cancelledAtText,
    cancellationReason: response.cancellationReason ?? bookingFallback?.cancellationReason ?? null,
    refundAmount,
    formattedRefundAmount: formatHotelCancellationCurrency(refundAmount, currency),
    cancellationFee,
    formattedCancellationFee:
      cancellationFee !== null ? formatHotelCancellationCurrency(cancellationFee, currency) : null,
    policyTier: rawTier ? formatPolicyTierDisplay(rawTier) : null,
    refundId: response.refundId ?? bookingFallback?.refundId ?? null,
    refund: refundPresentation,
    inventory: inventoryPresentation,
    reconciledFromServerProbe
  };
}

/**
 * Reconstructs the Step 3 view-model strictly from a server-verified `GET /api/bookings/{id}`
 * payload after a timeout or transient 5xx error on `PUT /cancel` confirmed the booking is CANCELLED.
 */
export function buildHotelCancellationResultFromBooking(
  bookingRecord: any,
  fallbackBookingRef: string,
  currency: string = 'INR'
): HotelCancellationResultViewModel {
  const refundAmount = parseNumericAmount(bookingRecord?.refundAmount) ?? 0;
  const cancellationFee = parseNumericAmount(bookingRecord?.cancellationFee);
  const rawTier = bookingRecord?.cancellationPolicyTier || null;
  const refundPresentation = mapHotelCancellationRefundStatus(
    bookingRecord?.refundStatus,
    refundAmount,
    currency
  );
  const inventoryPresentation = mapHotelInventoryReleaseStatus(bookingRecord?.inventoryReleaseStatus);

  return {
    bookingId: Number(bookingRecord?.id) || 0,
    bookingRef: bookingRecord?.bookingRef || fallbackBookingRef || '—',
    bookingStatus: (bookingRecord?.status || 'CANCELLED').toUpperCase(),
    idempotent: true,
    headline: 'Booking Cancelled — Status Verified',
    subtitle: 'We confirmed with the server that your hotel booking was cancelled. Here are the recorded details.',
    headerIconClass:
      refundPresentation.tone === 'danger' || refundPresentation.tone === 'warning'
        ? 'fa-solid fa-circle-exclamation'
        : 'fa-solid fa-check',
    headerTone:
      refundPresentation.tone === 'danger' || refundPresentation.tone === 'warning'
        ? 'warning'
        : 'success',
    cancelledAtText: formatHotelDateTime(bookingRecord?.cancelledAt),
    cancellationReason: bookingRecord?.cancellationReason || null,
    refundAmount,
    formattedRefundAmount: formatHotelCancellationCurrency(refundAmount, currency),
    cancellationFee,
    formattedCancellationFee:
      cancellationFee !== null ? formatHotelCancellationCurrency(cancellationFee, currency) : null,
    policyTier: rawTier ? formatPolicyTierDisplay(rawTier) : null,
    refundId: bookingRecord?.refundId || null,
    refund: refundPresentation,
    inventory: inventoryPresentation,
    reconciledFromServerProbe: true
  };
}

function resolveResultHeadline(
  idempotent: boolean,
  reconciledFromServerProbe: boolean,
  backendMessage?: string | null
): { headline: string; subtitle: string } {
  if (reconciledFromServerProbe) {
    return {
      headline: 'Booking Cancelled — Status Verified',
      subtitle:
        'We confirmed with the server that your cancellation completed. Here are your recorded details.'
    };
  }
  if (idempotent) {
    return {
      headline: 'Booking Already Cancelled',
      subtitle:
        backendMessage && !backendMessage.includes('{')
          ? backendMessage
          : 'This booking was already cancelled earlier. Showing your recorded refund and inventory status.'
    };
  }
  return {
    headline: 'Booking Cancelled Successfully',
    subtitle: 'Your hotel booking has been cancelled.'
  };
}

/** Extracts safe user-facing API errors without exposing raw stack traces or technical payloads. */
export function extractSafeCancellationApiError(err: any): ParsedCancellationApiError {
  const isTimeout = err?.name === 'TimeoutError';
  const httpStatus = typeof err?.status === 'number' ? err.status : null;
  const isUncertain =
    isTimeout || httpStatus === 0 || httpStatus === 502 || httpStatus === 503 || httpStatus === 504;
  const isAuthError = httpStatus === 401 || httpStatus === 403;
  const isStatusChanged = httpStatus === 400 || httpStatus === 409;
  const isNotFound = httpStatus === 404;

  const rawBackendMsg = extractRawBackendErrorMessage(err);

  if (isTimeout) {
    return {
      message:
        'The cancellation request timed out before confirmation was received. Checking the latest booking status from the server...',
      httpStatus,
      isTimeoutOrUncertain: true,
      isAuthError: false,
      isStatusChanged: false,
      isNotFound: false
    };
  }

  if (httpStatus === 401) {
    return {
      message: rawBackendMsg || 'Your session has expired. Please sign in again to cancel this booking.',
      httpStatus,
      isTimeoutOrUncertain: false,
      isAuthError: true,
      isStatusChanged: false,
      isNotFound: false
    };
  }

  if (httpStatus === 403) {
    return {
      message: rawBackendMsg || 'You are not authorized to access or cancel this hotel booking.',
      httpStatus,
      isTimeoutOrUncertain: false,
      isAuthError: true,
      isStatusChanged: false,
      isNotFound: false
    };
  }

  if (httpStatus === 404) {
    return {
      message: rawBackendMsg || 'We could not find this hotel booking. Please refresh My Bookings.',
      httpStatus,
      isTimeoutOrUncertain: false,
      isAuthError: false,
      isStatusChanged: false,
      isNotFound: true
    };
  }

  if (httpStatus === 0) {
    return {
      message: 'Unable to reach TravelEasy right now. Please check your connection and try again.',
      httpStatus,
      isTimeoutOrUncertain: true,
      isAuthError: false,
      isStatusChanged: false,
      isNotFound: false
    };
  }

  if (httpStatus !== null && httpStatus >= 500) {
    return {
      message:
        rawBackendMsg ||
        'TravelEasy is experiencing a temporary server issue. Please try again in a moment.',
      httpStatus,
      isTimeoutOrUncertain: isUncertain,
      isAuthError: false,
      isStatusChanged: false,
      isNotFound: false
    };
  }

  return {
    message:
      rawBackendMsg || 'Unable to process your hotel cancellation request right now. Please try again.',
    httpStatus,
    isTimeoutOrUncertain: isUncertain,
    isAuthError,
    isStatusChanged,
    isNotFound
  };
}

function extractRawBackendErrorMessage(err: any): string | null {
  const candidates = [
    err?.error?.message,
    typeof err?.error === 'string' ? err.error : null,
    err?.message
  ];

  for (const candidate of candidates) {
    if (typeof candidate !== 'string') continue;
    const trimmed = candidate.trim();
    if (!trimmed) continue;
    // Guard against Angular's technical default "Http failure response for ..." or JSON/HTML dumps
    if (
      trimmed.startsWith('Http failure response') ||
      trimmed.startsWith('<!DOCTYPE') ||
      trimmed.startsWith('<html') ||
      trimmed.startsWith('{')
    ) {
      continue;
    }
    return trimmed.length > 260 ? `${trimmed.slice(0, 257)}...` : trimmed;
  }
  return null;
}

/** Formats concise authoritative evaluation countdown note when hoursUntilCheckIn is present. */
export function formatQuoteEvaluationHint(
  quote: HotelCancellationQuoteResponse | null | undefined
): string | null {
  if (!quote || typeof quote.hoursUntilCheckIn !== 'number' || !Number.isFinite(quote.hoursUntilCheckIn)) {
    return null;
  }
  if (quote.hoursUntilCheckIn <= 0) {
    return 'Check-in cutoff time (2:00 PM IST on check-in date) has already passed.';
  }
  const roundedHours = Math.floor(quote.hoursUntilCheckIn);
  return `${roundedHours} ${roundedHours === 1 ? 'hour' : 'hours'} remaining until 2:00 PM IST check-in.`;
}
