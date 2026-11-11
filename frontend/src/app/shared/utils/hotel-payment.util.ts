import {
  HotelActiveReservation,
  HotelDetailsResponseDto,
  HotelPaymentCheckoutSessionState,
  HotelPaymentUiState,
  HotelReservationDetailResponse,
  HotelReservationInitiationRequest,
  HotelReservationInitiationResponse,
  HotelRoomGuestAllocationPayload,
  HotelStaySummary,
  PaymentTransactionDto,
  PaymentVerificationResponse,
  PhysicalRoomAllocation,
  PrimaryGuestDtoPayload
} from '../../models/hotel.model';
import {
  buildReservationAllocationsPayload,
  formatStayGuestsSummary
} from './guest-allocation.util';
import { DEFAULT_HOTEL_PLACEHOLDER, resolveImageUrl } from './image-url.util';

export const HOTEL_PAYMENT_SESSION_KEY = 'traveleasy_hotel_payment_state';

const REF_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/** Generates a unique, client-supplied idempotency bookingRef matching TravelEasy conventions. */
export function generateHotelBookingRef(now: Date = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  let suffix = '';
  for (let i = 0; i < 4; i += 1) {
    const idx = Math.floor(Math.random() * REF_ALPHABET.length);
    suffix += REF_ALPHABET[idx];
  }
  return `HTL-BKG-${year}${month}${day}-${suffix}`;
}

/** Builds a canonical signature for stay criteria (hotel + check-in + check-out). */
export function buildStayContextSignature(
  hotelId: number | null | undefined,
  checkIn: string | null | undefined,
  checkOut: string | null | undefined
): string {
  return `${hotelId ?? ''}|${checkIn ?? ''}|${checkOut ?? ''}`;
}

/** Builds a canonical signature for selected room types, quantities, and physical room allocations. */
export function buildAllocationsSignature(
  staySummary: HotelStaySummary | null | undefined,
  rooms: PhysicalRoomAllocation[] | null | undefined
): string {
  if (!staySummary) return '';
  const stayPart = buildStayContextSignature(
    staySummary.hotelId,
    staySummary.checkIn,
    staySummary.checkOut
  );

  if (Array.isArray(rooms) && rooms.length > 0) {
    const counts = new Map<number, number>();
    for (const r of rooms) {
      counts.set(r.roomTypeId, (counts.get(r.roomTypeId) || 0) + 1);
    }
    const itemsPart = Array.from(counts.entries())
      .sort((a, b) => a[0] - b[0])
      .map(([roomId, qty]) => `${roomId}:${qty}`)
      .join(',');
    return `${stayPart}#${itemsPart}`;
  }

  const itemsPart = (staySummary.selectedItems || [])
    .slice()
    .sort((a, b) => a.roomTypeId - b.roomTypeId)
    .map(item => `${item.roomTypeId}:${item.quantity}`)
    .join(',');
  return `${stayPart}#${itemsPart}`;
}

/**
 * Builds the authoritative POST /api/bookings/hotel-reservations payload from Stage 4 state.
 * Never submits client-estimated totals; Booking Service computes authoritative pricing.
 */
export function buildHotelReservationRequest(
  staySummary: HotelStaySummary,
  rooms: PhysicalRoomAllocation[],
  primaryGuest: PrimaryGuestDtoPayload,
  currentUser?: { id?: number; fullName?: string; email?: string } | null,
  bookingRef?: string
): HotelReservationInitiationRequest {
  const trimmedFullName = (primaryGuest.fullName || '').trim();
  const trimmedEmail = (primaryGuest.email || '').trim();
  const trimmedMobile = (primaryGuest.mobile || '').trim();

  const request: HotelReservationInitiationRequest = {
    primaryGuest: {
      fullName: trimmedFullName,
      email: trimmedEmail,
      mobile: trimmedMobile
    },
    hotelId: staySummary.hotelId,
    hotelName: staySummary.hotelName,
    checkInDate: staySummary.checkIn,
    checkOutDate: staySummary.checkOut,
    allocations: buildReservationAllocationsPayload(rooms)
  };

  if (bookingRef && bookingRef.trim()) {
    request.bookingRef = bookingRef.trim();
  }
  if (currentUser?.id != null) {
    request.customerId = currentUser.id;
  }
  if (trimmedFullName || currentUser?.fullName) {
    request.customerName = trimmedFullName || currentUser?.fullName;
  }
  if (trimmedEmail || currentUser?.email) {
    request.customerEmail = trimmedEmail || currentUser?.email;
  }

  return request;
}

/** Summarizes adult and child occupant counts from physical room allocation payloads without inventing missing values. */
export function summarizeAllocationGuestCounts(
  rooms?: HotelRoomGuestAllocationPayload[] | null
): { adultsCount: number | null; childrenCount: number | null } {
  if (!Array.isArray(rooms) || rooms.length === 0) {
    return { adultsCount: null, childrenCount: null };
  }

  let adultsCount = 0;
  let childrenCount = 0;
  for (const room of rooms) {
    if (!room) continue;
    if (typeof room.adults === 'number' && !Number.isNaN(room.adults)) {
      adultsCount += Math.max(0, Math.floor(room.adults));
    } else if (Array.isArray(room.occupants)) {
      adultsCount += room.occupants.filter(o => o?.type === 'ADULT').length;
    }

    if (typeof room.children === 'number' && !Number.isNaN(room.children)) {
      childrenCount += Math.max(0, Math.floor(room.children));
    } else if (Array.isArray(room.occupants)) {
      childrenCount += room.occupants.filter(o => o?.type === 'CHILD').length;
    }
  }

  return { adultsCount, childrenCount };
}

/** Normalizes initiation response into the shared active reservation checkout view model. */
export function fromInitiationResponse(
  res: HotelReservationInitiationResponse
): HotelActiveReservation {
  return {
    bookingId: res.bookingId,
    bookingRef: res.bookingRef,
    bookingStatus: (res.status || 'PENDING_PAYMENT').toUpperCase(),
    totalAmount: Number(res.totalAmount),
    currency: res.currency || 'INR',
    expiresAt: res.expiresAt || null,
    hotelId: res.hotelId,
    hotelName: res.hotelName,
    checkInDate: res.checkInDate,
    checkOutDate: res.checkOutDate,
    totalNights: res.totalNights || 1,
    paymentStatus: null,
    primaryGuest: res.primaryGuest || null,
    allocationsSummary: (res.allocations || []).map(a => {
      const guestCounts = summarizeAllocationGuestCounts(a.rooms);
      return {
        roomId: a.roomId,
        roomTypeName: a.roomTypeName,
        quantity: a.quantity,
        adultsCount: guestCounts.adultsCount,
        childrenCount: guestCounts.childrenCount
      };
    }),
    cancellation: null
  };
}

/** Normalizes reservation detail response (from session recovery / status polling). */
export function fromDetailResponse(
  res: HotelReservationDetailResponse,
  previousSameBooking?: HotelActiveReservation | null
): HotelActiveReservation {
  const mappedAllocations = (res.allocations || []).map(a => {
    const guestCounts = summarizeAllocationGuestCounts(a.rooms);
    return {
      roomId: a.roomId,
      roomTypeName: a.roomTypeName,
      quantity: a.quantity,
      adultsCount: guestCounts.adultsCount,
      childrenCount: guestCounts.childrenCount
    };
  });

  const canPreservePreviousSummary =
    mappedAllocations.length === 0 &&
    !!previousSameBooking &&
    previousSameBooking.bookingRef === res.bookingRef &&
    Array.isArray(previousSameBooking.allocationsSummary) &&
    previousSameBooking.allocationsSummary.length > 0;

  return {
    bookingId: res.bookingId,
    bookingRef: res.bookingRef,
    bookingStatus: (res.bookingStatus || 'PENDING_PAYMENT').toUpperCase(),
    totalAmount: Number(res.totalAmount),
    currency: res.currency || 'INR',
    expiresAt: res.expiresAt || null,
    hotelId: res.hotel?.hotelId ?? 0,
    hotelName: res.hotel?.hotelName || 'Hotel Reservation',
    checkInDate: res.checkInDate || '',
    checkOutDate: res.checkOutDate || '',
    totalNights: res.totalNights ?? 1,
    paymentStatus: res.paymentStatus || null,
    primaryGuest: res.primaryGuest || null,
    allocationsSummary: canPreservePreviousSummary
      ? previousSameBooking!.allocationsSummary
      : mappedAllocations,
    cancellation: res.cancellation || null
  };
}

/**
 * Parses a backend LocalDateTime or ISO-8601 timestamp into epoch milliseconds safely across browsers.
 * Treats timezone-naive Java LocalDateTime strings as local wall-clock timestamps without hardcoded resets.
 */
export function parseBackendExpiresAtMs(expiresAt?: string | null): number | null {
  if (!expiresAt || typeof expiresAt !== 'string') {
    return null;
  }
  const trimmed = expiresAt.trim();
  if (!trimmed) {
    return null;
  }

  // If explicit timezone offset is present (Z or +/-HH:MM or +/-HHMM), normalize >3 fractional digits to ms
  const hasOffset = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(trimmed);
  if (hasOffset) {
    const normalized = trimmed.replace(/\.(\d{3})\d+/, '.$1');
    const parsed = Date.parse(normalized);
    return Number.isNaN(parsed) ? null : parsed;
  }

  // Naive Java LocalDateTime: "YYYY-MM-DDTHH:mm:ss(.fffffffff)" or space-separated
  const match = trimmed.match(
    /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?$/
  );
  if (match) {
    const year = parseInt(match[1], 10);
    const month = parseInt(match[2], 10) - 1;
    const day = parseInt(match[3], 10);
    const hour = parseInt(match[4], 10);
    const minute = parseInt(match[5], 10);
    const second = match[6] ? parseInt(match[6], 10) : 0;
    const frac = match[7] ? parseInt(match[7].slice(0, 3).padEnd(3, '0'), 10) : 0;
    const date = new Date(year, month, day, hour, minute, second, frac);
    const ms = date.getTime();
    return Number.isNaN(ms) ? null : ms;
  }

  const fallbackParsed = Date.parse(trimmed);
  return Number.isNaN(fallbackParsed) ? null : fallbackParsed;
}

/** Computes remaining whole seconds from an authoritative expiresAt timestamp (0 when expired). */
export function computeRemainingSeconds(expiresAtMs: number | null, nowMs: number = Date.now()): number {
  if (expiresAtMs === null) {
    return 0;
  }
  const diffMs = expiresAtMs - nowMs;
  if (diffMs <= 0) {
    return 0;
  }
  return Math.ceil(diffMs / 1000);
}

/** Formats countdown seconds as zero-padded MM:SS (or HH:MM:SS if >= 60 minutes). */
export function formatCountdownMmSs(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds || 0));
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const seconds = safe % 60;

  const mm = String(minutes).padStart(2, '0');
  const ss = String(seconds).padStart(2, '0');
  if (hours > 0) {
    return `${String(hours).padStart(2, '0')}:${mm}:${ss}`;
  }
  return `${mm}:${ss}`;
}

/** Produces an accessible duration description using Intl.DurationFormat when available. */
export function formatAccessibleCountdown(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds || 0));
  if (safe === 0) {
    return 'Reservation hold expired';
  }
  const minutes = Math.floor(safe / 60);
  const seconds = safe % 60;
  try {
    const DurationFmt = (Intl as any)?.DurationFormat;
    if (typeof DurationFmt === 'function') {
      const formatted = new DurationFmt('en', { style: 'long' }).format({
        minutes,
        seconds
      });
      return `${formatted} remaining to complete payment`;
    }
  } catch {
    // Fallback below if browser lacks Intl.DurationFormat
  }
  const minPart = minutes > 0 ? `${minutes} ${minutes === 1 ? 'minute' : 'minutes'}` : '';
  const secPart = `${seconds} ${seconds === 1 ? 'second' : 'seconds'}`;
  return `${[minPart, secPart].filter(Boolean).join(', ')} remaining to complete payment`;
}

/** Formats authoritative monetary amount without substituting fake zero or fallback values. */
export function formatAuthoritativeAmount(
  amount: number | null | undefined,
  currency: string = 'INR'
): string {
  if (amount === null || amount === undefined || !Number.isFinite(Number(amount))) {
    return '—';
  }
  const numeric = Number(amount);
  const hasFraction = Math.round(numeric * 100) % 100 !== 0;
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: currency || 'INR',
      minimumFractionDigits: hasFraction ? 2 : 0,
      maximumFractionDigits: hasFraction ? 2 : 0
    }).format(numeric);
  } catch {
    return `₹${hasFraction ? numeric.toFixed(2) : Math.round(numeric).toLocaleString('en-IN')}`;
  }
}

export interface ReconciledCheckoutResult {
  uiState: HotelPaymentUiState;
  statusBadgeLabel: string;
  title: string;
  message: string;
  paymentRef: string | null;
  transactionRef: string | null;
  refundId: string | null;
  refundStatus: string | null;
  refundAmount: number | null;
}

/**
 * Authoritatively reconciles UI checkout state from reservation + verification + payment transaction.
 * Never treats payment SUCCESS alone as booking confirmation; verifies confirmed booking status
 * or handles late-capture refund and transient pending-confirmation outcomes safely.
 */
export function reconcileCheckoutState(params: {
  reservation: HotelActiveReservation | null;
  verification?: PaymentVerificationResponse | null;
  paymentTxn?: PaymentTransactionDto | null;
  lastPaymentError?: string | null;
  nowMs?: number;
}): ReconciledCheckoutResult {
  const { reservation, verification, paymentTxn, lastPaymentError, nowMs = Date.now() } = params;

  const txn = verification?.transaction || paymentTxn || null;
  const bkgStatus = (reservation?.bookingStatus || '').toUpperCase();
  const payStatus = (verification?.status || txn?.status || '').toUpperCase();

  const paymentRef = verification?.paymentId || txn?.razorpayPaymentId || null;
  const transactionRef = verification?.transactionRef || txn?.transactionRef || null;
  const refundId = txn?.refundId || null;
  const refundStatus = txn?.refundStatus || reservation?.cancellation?.refundStatus || null;
  const refundAmount =
    txn?.refundAmount ??
    reservation?.cancellation?.refundAmount ??
    reservation?.totalAmount ??
    null;

  // 1. Authoritatively CONFIRMED only if verifyRes.verified === true OR bookingStatus === 'CONFIRMED'
  if (verification?.verified === true || bkgStatus === 'CONFIRMED') {
    return {
      uiState: 'CONFIRMED',
      statusBadgeLabel: 'Confirmed',
      title: 'Booking Confirmed',
      message:
        verification?.message ||
        'Your payment has been verified and your hotel reservation is confirmed.',
      paymentRef,
      transactionRef,
      refundId,
      refundStatus,
      refundAmount
    };
  }

  // 2. Captured payment when reservation expired / cancelled / verification returned verified === false
  const hasCapturedPayment =
    payStatus === 'SUCCESS' ||
    payStatus === 'REFUND_INITIATED' ||
    payStatus === 'REFUNDED' ||
    !!paymentRef;
  const verificationRejectedWithRefund =
    verification != null &&
    verification.verified === false &&
    (hasCapturedPayment || /refund|expired/i.test(verification.message || ''));

  if (
    payStatus === 'REFUND_INITIATED' ||
    payStatus === 'REFUNDED' ||
    verificationRejectedWithRefund ||
    ((bkgStatus === 'EXPIRED' || bkgStatus === 'CANCELLED') &&
      (hasCapturedPayment || (!!refundStatus && refundStatus.toUpperCase() !== 'NOT_APPLICABLE')))
  ) {
    const isCompletedRefund =
      payStatus === 'REFUNDED' ||
      (refundStatus || '').toUpperCase() === 'COMPLETED' ||
      (refundStatus || '').toUpperCase() === 'PROCESSED' ||
      (refundStatus || '').toUpperCase() === 'SUCCESS';
    return {
      uiState: 'REFUND_INITIATED',
      statusBadgeLabel: isCompletedRefund ? 'Refunded' : 'Refund Initiated',
      title: 'Reservation Hold Expired — Refund Initiated',
      message:
        verification?.message ||
        'Your payment was captured after the reservation hold expired. Your rooms could not be confirmed, and an automated refund has been initiated.',
      paymentRef,
      transactionRef,
      refundId,
      refundStatus: refundStatus || (isCompletedRefund ? 'PROCESSED' : 'INITIATED'),
      refundAmount
    };
  }

  // 3. Payment captured in Payment Service while Booking Service confirmation is still pending
  if (hasCapturedPayment && (bkgStatus === 'PENDING_PAYMENT' || bkgStatus === 'INITIATED' || !bkgStatus)) {
    return {
      uiState: 'CONFIRMATION_PENDING',
      statusBadgeLabel: 'Confirmation Pending',
      title: 'Payment Received — Confirming Reservation',
      message:
        'Your payment has been received, and we are finalizing confirmation with our booking service. Please wait or check status below without paying again.',
      paymentRef,
      transactionRef,
      refundId,
      refundStatus,
      refundAmount
    };
  }

  // 4. Expired / Cancelled / Failed unpaid reservation
  const expiresAtMs = parseBackendExpiresAtMs(reservation?.expiresAt);
  if (
    bkgStatus === 'EXPIRED' ||
    bkgStatus === 'CANCELLED' ||
    bkgStatus === 'FAILED' ||
    (expiresAtMs !== null && expiresAtMs <= nowMs)
  ) {
    return {
      uiState: 'EXPIRED',
      statusBadgeLabel: bkgStatus === 'CANCELLED' ? 'Cancelled' : 'Expired',
      title: 'Reservation Hold Expired',
      message:
        'Your 15-minute payment window has expired and the temporary room hold has been released. Please search again to start a new booking.',
      paymentRef,
      transactionRef,
      refundId,
      refundStatus,
      refundAmount
    };
  }

  // 5. Retryable payment failure while reservation hold is still active
  if (lastPaymentError) {
    return {
      uiState: 'PAYMENT_FAILED',
      statusBadgeLabel: 'Pending Payment',
      title: 'Payment Not Completed',
      message: lastPaymentError,
      paymentRef,
      transactionRef,
      refundId,
      refundStatus,
      refundAmount
    };
  }

  // 6. Standard active pending payment state
  return {
    uiState: 'PENDING_PAYMENT',
    statusBadgeLabel: 'Pending Payment',
    title: 'Reservation Created',
    message:
      'Your rooms are temporarily reserved and will be released if the payment is not completed in time.',
    paymentRef,
    transactionRef,
    refundId,
    refundStatus,
    refundAmount
  };
}

/** Extracts actionable error metadata and distinguishes transient 5xx/timeouts from hard rejections. */
export function extractApiErrorMessage(
  err: any,
  fallback: string
): {
  status: number;
  message: string;
  isTransient5xxOrNetwork: boolean;
  isExpiredError: boolean;
} {
  const status = typeof err?.status === 'number' ? err.status : 0;
  const isTimeout = err?.name === 'TimeoutError';
  let rawMessage = '';

  if (err?.error) {
    if (typeof err.error === 'string') {
      try {
        const parsed = JSON.parse(err.error);
        rawMessage =
          parsed?.message ||
          (Array.isArray(parsed?.errors) && parsed.errors[0]) ||
          parsed?.error ||
          err.error;
      } catch {
        rawMessage = err.error;
      }
    } else if (typeof err.error === 'object') {
      rawMessage =
        err.error.message ||
        (Array.isArray(err.error.errors) && err.error.errors[0]) ||
        err.error.error ||
        '';
    }
  }

  if (!rawMessage && typeof err?.message === 'string' && !err.message.startsWith('Http failure response')) {
    rawMessage = err.message;
  }

  const message = (rawMessage || '').trim() || fallback;
  const isTransient5xxOrNetwork = isTimeout || status === 0 || status >= 500;
  const isExpiredError = /\bexpired\b|\bcancelled\b|no longer valid/i.test(message);

  return {
    status,
    message,
    isTransient5xxOrNetwork,
    isExpiredError
  };
}

function resolveSessionStorage(customStorage?: Storage): Storage | null {
  if (customStorage) return customStorage;
  if (typeof window !== 'undefined' && window.sessionStorage) {
    return window.sessionStorage;
  }
  return null;
}

/**
 * Persists only non-sensitive reservation recovery metadata (never credentials or Razorpay secrets).
 */
export function saveHotelPaymentCheckoutState(
  state: HotelPaymentCheckoutSessionState,
  customStorage?: Storage
): void {
  const storage = resolveSessionStorage(customStorage);
  if (!storage) return;
  try {
    const safeState: HotelPaymentCheckoutSessionState = {
      userId: state.userId ?? null,
      bookingRef: state.bookingRef,
      hotelId: state.hotelId,
      staySignature: state.staySignature,
      allocationsSignature: state.allocationsSignature,
      lastKnownOrderId: state.lastKnownOrderId ?? null,
      updatedAt: state.updatedAt || new Date().toISOString()
    };
    storage.setItem(HOTEL_PAYMENT_SESSION_KEY, JSON.stringify(safeState));
  } catch {
    // Ignore storage quota errors
  }
}

/** Loads active reservation recovery metadata scoped by userId, hotelId, and optional selection signature. */
export function loadHotelPaymentCheckoutState(
  expectedUserId?: number | null,
  expectedHotelId?: number | null,
  expectedAllocationsSignature?: string | null,
  customStorage?: Storage
): HotelPaymentCheckoutSessionState | null {
  const storage = resolveSessionStorage(customStorage);
  if (!storage) return null;
  try {
    const raw = storage.getItem(HOTEL_PAYMENT_SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as HotelPaymentCheckoutSessionState;
    if (!parsed || !parsed.bookingRef) {
      return null;
    }
    if (
      expectedUserId != null &&
      parsed.userId != null &&
      parsed.userId !== expectedUserId
    ) {
      return null;
    }
    if (
      expectedHotelId != null &&
      parsed.hotelId != null &&
      parsed.hotelId !== expectedHotelId
    ) {
      return null;
    }
    if (
      expectedAllocationsSignature &&
      parsed.allocationsSignature &&
      parsed.allocationsSignature !== expectedAllocationsSignature
    ) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function clearHotelPaymentCheckoutState(customStorage?: Storage): void {
  const storage = resolveSessionStorage(customStorage);
  if (!storage) return;
  try {
    storage.removeItem(HOTEL_PAYMENT_SESSION_KEY);
  } catch {
    // Ignore storage errors
  }
}

export interface ConfirmedAmenityItem {
  label: string;
  icon: string;
}

export interface ConfirmedHotelDisplayInfo {
  hotelId: number;
  hotelName: string;
  imageUrl: string;
  locationLine: string | null;
  starRating: number | null;
  propertyType: string | null;
  reviewCount: number | null;
  distanceLabel: string | null;
  amenities: ConfirmedAmenityItem[];
}

export interface ConfirmedStayBreakdown {
  totalRooms: number | null;
  roomsCountLabel: string | null;
  roomTypesLabel: string | null;
  adults: number | null;
  children: number | null;
  guestsLabel: string | null;
}

/** Maps a hotel amenity label to a consistent FontAwesome icon class. */
export function resolveHotelAmenityIcon(name: string): string {
  const n = (name || '').toLowerCase();
  if (n.includes('wi-fi') || n.includes('wifi') || n.includes('internet')) return 'fa-solid fa-wifi';
  if (n.includes('restaurant') || n.includes('dining') || n.includes('food')) return 'fa-solid fa-utensils';
  if (n.includes('pool') || n.includes('swimming')) return 'fa-solid fa-person-swimming';
  if (n.includes('spa') || n.includes('wellness') || n.includes('massage')) return 'fa-solid fa-spa';
  if (n.includes('gym') || n.includes('fitness')) return 'fa-solid fa-dumbbell';
  if (n.includes('parking')) return 'fa-solid fa-square-parking';
  if (n.includes('breakfast') || n.includes('coffee') || n.includes('cafe')) return 'fa-solid fa-mug-saucer';
  if (n.includes('ac') || n.includes('air condition')) return 'fa-solid fa-snowflake';
  if (n.includes('bar') || n.includes('lounge')) return 'fa-solid fa-martini-glass';
  if (n.includes('room service') || n.includes('concierge')) return 'fa-solid fa-bell-concierge';
  if (n.includes('business') || n.includes('conference') || n.includes('convention')) return 'fa-solid fa-briefcase';
  if (n.includes('laundry')) return 'fa-solid fa-shirt';
  if (n.includes('elevator') || n.includes('lift')) return 'fa-solid fa-elevator';
  if (n.includes('security') || n.includes('cctv')) return 'fa-solid fa-shield-halved';
  if (n.includes('tv')) return 'fa-solid fa-tv';
  return 'fa-solid fa-check';
}

/** Parses and caps real comma-separated hotel amenities cleanly with icons. */
export function pickConfirmedAmenities(
  amenitiesRaw?: string | null,
  maxItems: number = 4
): ConfirmedAmenityItem[] {
  if (!amenitiesRaw || typeof amenitiesRaw !== 'string' || maxItems <= 0) {
    return [];
  }
  const seen = new Set<string>();
  const picked: ConfirmedAmenityItem[] = [];
  for (const part of amenitiesRaw.split(',')) {
    const label = part.trim();
    if (!label) continue;
    const key = label.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    picked.push({ label, icon: resolveHotelAmenityIcon(label) });
    if (picked.length >= maxItems) break;
  }
  return picked;
}

/** Formats hotel location cleanly from address and city without duplicating city. */
export function formatConfirmedHotelLocation(
  address?: string | null,
  city?: string | null
): string | null {
  const cleanAddr = (address || '').trim();
  const cleanCity = (city || '').trim();
  if (cleanAddr && cleanCity) {
    if (cleanAddr.toLowerCase().includes(cleanCity.toLowerCase())) {
      return cleanAddr;
    }
    return `${cleanAddr}, ${cleanCity}`;
  }
  return cleanAddr || cleanCity || null;
}

/**
 * Builds confirmed hotel display metadata strictly from authoritative hotel details and matching stay context.
 * Unavailable optional fields (e.g., review counts or distances) remain null so the UI never fabricates data.
 */
export function buildConfirmedHotelDisplayInfo(params: {
  hotelId: number;
  hotelName: string;
  hotelDetails?: HotelDetailsResponseDto | null;
  staySummary?: HotelStaySummary | null;
}): ConfirmedHotelDisplayInfo {
  const detailsHotel =
    params.hotelDetails?.hotel &&
    (!params.hotelId || params.hotelDetails.hotel.id === params.hotelId)
      ? params.hotelDetails.hotel
      : null;

  const matchingStaySummary =
    params.staySummary &&
    (!params.hotelId || params.staySummary.hotelId === params.hotelId)
      ? params.staySummary
      : null;

  const coverGalleryImage =
    params.hotelDetails?.images && detailsHotel
      ? (params.hotelDetails.images.find(img => img?.cover)?.imageUrl ||
          params.hotelDetails.images[0]?.imageUrl ||
          null)
      : null;

  const rawImageUrl =
    detailsHotel?.imageUrl ||
    coverGalleryImage ||
    matchingStaySummary?.hotelImageUrl ||
    null;

  const starRating =
    typeof detailsHotel?.starRating === 'number' && detailsHotel.starRating > 0
      ? detailsHotel.starRating
      : null;

  return {
    hotelId: params.hotelId,
    hotelName: (detailsHotel?.name || params.hotelName || matchingStaySummary?.hotelName || 'Hotel Reservation').trim(),
    imageUrl: resolveImageUrl(rawImageUrl, DEFAULT_HOTEL_PLACEHOLDER),
    locationLine: formatConfirmedHotelLocation(
      detailsHotel?.address ?? matchingStaySummary?.address,
      detailsHotel?.city ?? matchingStaySummary?.city
    ),
    starRating,
    propertyType: detailsHotel?.propertyType || matchingStaySummary?.propertyType || null,
    reviewCount: null,
    distanceLabel: null,
    amenities: pickConfirmedAmenities(detailsHotel?.amenities, 4)
  };
}

/**
 * Derives confirmed room and guest summary strictly from the confirmed reservation snapshot,
 * falling back to active allocated physical rooms only when snapshot allocations are absent (e.g., minimal mocks).
 */
export function extractConfirmedStayBreakdown(
  reservation: HotelActiveReservation | null | undefined,
  fallbackAllocatedRooms?: PhysicalRoomAllocation[] | null
): ConfirmedStayBreakdown {
  const summaryRows = Array.isArray(reservation?.allocationsSummary)
    ? reservation!.allocationsSummary
    : [];

  let totalRooms = 0;
  let adultsSum = 0;
  let childrenSum = 0;
  let hasSnapshotGuestCounts = false;
  const groupedTypes = new Map<string, number>();

  for (const row of summaryRows) {
    if (!row) continue;
    const qty = Math.max(0, Math.floor(row.quantity || 0));
    totalRooms += qty;
    const typeName = (row.roomTypeName || '').trim();
    if (typeName && qty > 0) {
      groupedTypes.set(typeName, (groupedTypes.get(typeName) || 0) + qty);
    }
    if (row.adultsCount != null || row.childrenCount != null) {
      hasSnapshotGuestCounts = true;
      adultsSum += Math.max(0, row.adultsCount ?? 0);
      childrenSum += Math.max(0, row.childrenCount ?? 0);
    }
  }

  const hasFallbackRooms = Array.isArray(fallbackAllocatedRooms) && fallbackAllocatedRooms.length > 0;

  if (totalRooms === 0 && hasFallbackRooms) {
    for (const room of fallbackAllocatedRooms!) {
      if (!room) continue;
      totalRooms += 1;
      const typeName = (room.roomTypeName || '').trim();
      if (typeName) {
        groupedTypes.set(typeName, (groupedTypes.get(typeName) || 0) + 1);
      }
    }
  }

  if (!hasSnapshotGuestCounts && hasFallbackRooms) {
    hasSnapshotGuestCounts = true;
    for (const room of fallbackAllocatedRooms!) {
      for (const occ of room?.occupants || []) {
        if (occ?.type === 'ADULT') adultsSum += 1;
        else if (occ?.type === 'CHILD') childrenSum += 1;
      }
    }
  }

  const roomsCountLabel =
    totalRooms > 0 ? (totalRooms === 1 ? '1 Room' : `${totalRooms} Rooms`) : null;

  const roomTypeEntries = Array.from(groupedTypes.entries());
  let roomTypesLabel: string | null = null;
  if (roomTypeEntries.length === 1) {
    roomTypesLabel = roomTypeEntries[0][0];
  } else if (roomTypeEntries.length > 1) {
    roomTypesLabel = roomTypeEntries.map(([name, qty]) => `${qty} × ${name}`).join(', ');
  }

  const finalAdults = hasSnapshotGuestCounts ? adultsSum : null;
  const finalChildren = hasSnapshotGuestCounts ? childrenSum : null;
  const guestsLabel =
    hasSnapshotGuestCounts && (adultsSum > 0 || childrenSum > 0)
      ? formatStayGuestsSummary(adultsSum, childrenSum)
      : null;

  return {
    totalRooms: totalRooms > 0 ? totalRooms : null,
    roomsCountLabel,
    roomTypesLabel,
    adults: finalAdults,
    children: finalChildren,
    guestsLabel
  };
}
