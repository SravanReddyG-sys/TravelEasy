import {
  HotelDetailsResponseDto,
  HotelReservationDetailResponse,
  HotelRoomOccupantPayload,
  PaymentTransactionDto
} from '../../../../models/hotel.model';
import { formatGenderLabel, formatStayGuestsSummary } from '../../../../shared/utils/guest-allocation.util';
import {
  formatAuthoritativeAmount,
  formatConfirmedHotelLocation,
  parseBackendExpiresAtMs
} from '../../../../shared/utils/hotel-payment.util';
import { DEFAULT_HOTEL_PLACEHOLDER, resolveImageUrl } from '../../../../shared/utils/image-url.util';
import {
  HotelBookingCardViewModel,
  HotelBookingDetailsViewModel,
  HotelBookingEnrichmentContext,
  HotelBookingGuestRow,
  HotelBookingPriceLineItem,
  HotelBookingRefundBadge,
  HotelBookingRefundViewModel,
  HotelBookingReviewSummary,
  HotelBookingRoomCardViewModel,
  HotelCancellationPolicyRule
} from '../models/hotel-booking-view.model';

export const STANDARD_HOTEL_CHECKIN_NOTE = '(From 2:00 PM IST)';
export const HOTEL_POLICY_META_NOTE = 'Standard Check-in Time: 14:00 IST (2:00 PM) • Timezone: Asia/Kolkata';

export const HOTEL_CANCELLATION_POLICY_RULES: ReadonlyArray<HotelCancellationPolicyRule> = [
  {
    windowLabel: '48+ hours before check-in',
    refundLabel: '100% Refund',
    badgeTone: 'full',
    description: 'Full refund of the total paid booking amount when cancelled at least 48 hours prior to 14:00 IST on check-in date.'
  },
  {
    windowLabel: '24 to 48 hours before check-in',
    refundLabel: '50% Refund',
    badgeTone: 'partial',
    description: 'Half (50%) of the total paid booking amount is refunded when cancelled between 24 and 48 hours before check-in.'
  },
  {
    windowLabel: 'Less than 24 hours before check-in',
    refundLabel: '0% Refund',
    badgeTone: 'none',
    description: 'Cancellations made less than 24 hours before 14:00 IST on the check-in date are non-refundable.'
  },
  {
    windowLabel: 'On or after 14:00 IST on check-in day',
    refundLabel: '0% Refund',
    badgeTone: 'none',
    description: 'No refund is applicable once standard check-in time (14:00 IST) on the check-in day has been reached.'
  }
];

interface ParsedLegacyDetails {
  hotelNameFromTitle: string | null;
  roomTypeFromTitle: string | null;
  checkInRaw: string | null;
  checkOutRaw: string | null;
  roomsCount: number | null;
  adultsCount: number | null;
  childrenCount: number | null;
}

/** Extracts normalized booking status label & CSS badge class for HOTEL bookings. */
export function resolveHotelBookingStatusPresentation(rawStatus?: string | null): {
  status: string;
  label: string;
  badgeClass: string;
} {
  const status = (rawStatus || 'CONFIRMED').toUpperCase().trim();
  switch (status) {
    case 'CONFIRMED':
      return { status, label: 'Confirmed', badgeClass: 'hotel-status-confirmed' };
    case 'CANCELLED':
      return { status, label: 'Cancelled', badgeClass: 'hotel-status-cancelled' };
    case 'COMPLETED':
      return { status, label: 'Completed', badgeClass: 'hotel-status-completed' };
    case 'CHECKED_IN':
      return { status, label: 'Checked In', badgeClass: 'hotel-status-checked-in' };
    case 'CHECKED_OUT':
      return { status, label: 'Checked Out', badgeClass: 'hotel-status-checked-out' };
    case 'NO_SHOW':
      return { status, label: 'No Show', badgeClass: 'hotel-status-no-show' };
    default:
      return { status, label: status.replace(/_/g, ' '), badgeClass: 'hotel-status-checked-out' };
  }
}

/** Resolves secondary refund pill displayed on cancelled HOTEL cards strictly from backend state. */
export function resolveHotelCardRefundBadge(
  rawStatus?: string | null,
  rawRefundStatus?: string | null
): HotelBookingRefundBadge | null {
  const status = (rawStatus || '').toUpperCase().trim();
  const refundStatus = (rawRefundStatus || '').toUpperCase().trim();
  if (status !== 'CANCELLED' || !refundStatus) {
    return null;
  }

  if (refundStatus === 'PROCESSED' || refundStatus === 'COMPLETED' || refundStatus === 'SUCCESS') {
    return {
      label: 'Refunded',
      badgeClass: 'hotel-refund-pill-processed',
      iconClass: 'fa-solid fa-check'
    };
  }
  if (refundStatus === 'INITIATED' || refundStatus === 'PENDING' || refundStatus === 'FAILED_RETRYABLE') {
    return {
      label: 'Refund Pending',
      badgeClass: 'hotel-refund-pill-pending',
      iconClass: 'fa-regular fa-clock'
    };
  }
  if (refundStatus === 'FAILED_FINAL' || refundStatus === 'FAILED') {
    return {
      label: 'Refund Failed',
      badgeClass: 'hotel-refund-pill-failed',
      iconClass: 'fa-solid fa-circle-exclamation'
    };
  }
  return null;
}

/** Formats an ISO calendar date (`YYYY-MM-DD`) or timestamp into `Wed, Nov 11, 2026`. */
export function formatHotelCalendarDate(rawDate?: string | null): string {
  if (!rawDate || typeof rawDate !== 'string') {
    return '';
  }
  const trimmed = rawDate.trim();
  if (!trimmed) {
    return '';
  }

  const ymdMatch = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (ymdMatch) {
    const year = parseInt(ymdMatch[1], 10);
    const month = parseInt(ymdMatch[2], 10) - 1;
    const day = parseInt(ymdMatch[3], 10);
    const localMidday = new Date(year, month, day, 12, 0, 0);
    if (!Number.isNaN(localMidday.getTime())) {
      return localMidday.toLocaleDateString('en-US', {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
        year: 'numeric'
      });
    }
  }

  const parsedMs = parseBackendExpiresAtMs(trimmed);
  if (parsedMs !== null) {
    return new Date(parsedMs).toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
  }

  return trimmed;
}

/** Formats a backend date-time string into `Wed, Nov 11, 2026, 10:24 AM`. */
export function formatHotelDateTime(rawDateTime?: string | null): string | null {
  if (!rawDateTime || typeof rawDateTime !== 'string') {
    return null;
  }
  const parsedMs = parseBackendExpiresAtMs(rawDateTime);
  if (parsedMs === null) {
    return null;
  }
  return new Date(parsedMs).toLocaleString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit'
  });
}

/** Computes nights between two YYYY-MM-DD dates if positive and valid. */
export function computeCalendarNights(
  checkInIso?: string | null,
  checkOutIso?: string | null,
  authoritativeNights?: number | null
): number | null {
  if (typeof authoritativeNights === 'number' && Number.isFinite(authoritativeNights) && authoritativeNights > 0) {
    return Math.floor(authoritativeNights);
  }
  if (!checkInIso || !checkOutIso) {
    return null;
  }
  const inMatch = checkInIso.trim().match(/^(\d{4})-(\d{2})-(\d{2})$/);
  const outMatch = checkOutIso.trim().match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!inMatch || !outMatch) {
    return null;
  }
  const inUtc = Date.UTC(Number(inMatch[1]), Number(inMatch[2]) - 1, Number(inMatch[3]));
  const outUtc = Date.UTC(Number(outMatch[1]), Number(outMatch[2]) - 1, Number(outMatch[3]));
  const diffDays = Math.round((outUtc - inUtc) / 86_400_000);
  return diffDays > 0 ? diffDays : null;
}

/** Parses optional metadata stored on legacy/simple hotel Booking records without inventing defaults. */
export function parseLegacyHotelDetails(
  serviceTitle?: string | null,
  detailsJson?: string | null
): ParsedLegacyDetails {
  const title = (serviceTitle || '').trim();
  const titleMatch = title.match(/^(.*?)\s*\(([^()]+)\)$/);
  const hotelNameFromTitle = titleMatch ? titleMatch[1].trim() || null : title || null;
  const roomTypeFromTitle = titleMatch ? titleMatch[2].trim() || null : null;

  const details = (detailsJson || '').trim();
  if (!details) {
    return {
      hotelNameFromTitle,
      roomTypeFromTitle,
      checkInRaw: null,
      checkOutRaw: null,
      roomsCount: null,
      adultsCount: null,
      childrenCount: null
    };
  }

  const checkInMatch = details.match(/Check-in:\s*([^|]+)/i);
  const checkOutMatch = details.match(/Check-out:\s*([^|]+)/i);
  const roomsMatch = details.match(/Rooms?:\s*(\d+)/i);
  const adultsMatch = details.match(/Adults?:\s*(\d+)/i);
  const childrenMatch = details.match(/Child(?:ren)?:\s*(\d+)/i);

  return {
    hotelNameFromTitle,
    roomTypeFromTitle,
    checkInRaw: checkInMatch ? checkInMatch[1].trim() : null,
    checkOutRaw: checkOutMatch ? checkOutMatch[1].trim() : null,
    roomsCount: roomsMatch ? parseInt(roomsMatch[1], 10) : null,
    adultsCount: adultsMatch ? parseInt(adultsMatch[1], 10) : null,
    childrenCount: childrenMatch ? parseInt(childrenMatch[1], 10) : null
  };
}

/** Picks cover hotel image URL using authoritative HotelDetailsResponseDto or neutral local fallback. */
export function resolveHotelCoverImageUrl(hotelDetails?: HotelDetailsResponseDto | null): string {
  const images = Array.isArray(hotelDetails?.images) ? hotelDetails!.images : [];
  const markedCover = images.find(img => img && (img.isCover || img.cover) && img.imageUrl)?.imageUrl;
  const firstImage = images.find(img => img && img.imageUrl)?.imageUrl;
  const raw = hotelDetails?.hotel?.imageUrl || markedCover || firstImage || null;
  return resolveImageUrl(raw, DEFAULT_HOTEL_PLACEHOLDER);
}

/** Picks per-room image URL matching roomId or roomTypeName from HotelDetailsResponseDto.rooms. */
export function resolveRoomTypeImageUrl(
  roomId: number | null | undefined,
  roomTypeName: string | null | undefined,
  hotelDetails?: HotelDetailsResponseDto | null,
  fallbackHotelCoverUrl?: string
): string {
  const rooms = Array.isArray(hotelDetails?.rooms) ? hotelDetails!.rooms : [];
  const normalizedTargetName = (roomTypeName || '').trim().toLowerCase();

  const matched = rooms.find(
    r =>
      (typeof roomId === 'number' && roomId > 0 && r.id === roomId) ||
      (normalizedTargetName.length > 0 && (r.roomTypeName || '').trim().toLowerCase() === normalizedTargetName)
  );

  if (matched?.imageUrl) {
    return resolveImageUrl(matched.imageUrl, fallbackHotelCoverUrl || DEFAULT_HOTEL_PLACEHOLDER);
  }
  return fallbackHotelCoverUrl || resolveHotelCoverImageUrl(hotelDetails);
}

/**
 * Computes real review summary from backend reviews.
 * Returns null when zero valid reviews exist so UI never fabricates ratings or review counts.
 */
export function buildHotelReviewSummary(reviews?: any[] | null): HotelBookingReviewSummary | null {
  if (!Array.isArray(reviews) || reviews.length === 0) {
    return null;
  }
  const ratings = reviews
    .map(r => Number(r?.rating))
    .filter(n => Number.isFinite(n) && n >= 1 && n <= 5);

  if (ratings.length === 0) {
    return null;
  }

  const avg = Math.round((ratings.reduce((acc, n) => acc + n, 0) / ratings.length) * 10) / 10;
  const stars: Array<'full' | 'half' | 'empty'> = [];
  for (let i = 1; i <= 5; i += 1) {
    if (avg >= i - 0.25) {
      stars.push('full');
    } else if (avg >= i - 0.75) {
      stars.push('half');
    } else {
      stars.push('empty');
    }
  }

  return {
    averageRating: avg,
    formattedRating: avg.toFixed(1),
    reviewCount: ratings.length,
    formattedReviewCount: ratings.length.toLocaleString('en-US'),
    stars
  };
}

/** Summarizes allocations (room types, total rooms, adults, children) from snapshot or legacy booking. */
function extractRoomAndGuestSummary(
  rawBooking: any,
  reservationDetails?: HotelReservationDetailResponse | null
): {
  roomTypesSummary: string | null;
  totalRooms: number | null;
  roomsCountLabel: string | null;
  guestsSummaryLabel: string | null;
} {
  const legacy = parseLegacyHotelDetails(rawBooking?.serviceTitle, rawBooking?.detailsJson);
  const allocations = Array.isArray(reservationDetails?.allocations)
    ? reservationDetails!.allocations
    : [];

  if (allocations.length > 0) {
    let totalRooms = 0;
    let totalAdults = 0;
    let totalChildren = 0;
    let hasGuestCounts = false;
    const roomTypeNames: string[] = [];
    const seenTypes = new Set<string>();

    for (const alloc of allocations) {
      if (!alloc) continue;
      const qty = Math.max(
        0,
        alloc.quantity || (Array.isArray(alloc.rooms) ? alloc.rooms.length : 0)
      );
      totalRooms += qty;

      const cleanType = (alloc.roomTypeName || '').trim();
      if (cleanType && !seenTypes.has(cleanType.toLowerCase())) {
        seenTypes.add(cleanType.toLowerCase());
        roomTypeNames.push(cleanType);
      }

      if (Array.isArray(alloc.rooms) && alloc.rooms.length > 0) {
        hasGuestCounts = true;
        for (const r of alloc.rooms) {
          if (!r) continue;
          const hasExplicitCounts = r.adults !== undefined && r.adults !== null;
          if (hasExplicitCounts) {
            totalAdults += Math.max(0, Number(r.adults || 0));
            totalChildren += Math.max(0, Number(r.children || 0));
          } else if (Array.isArray(r.occupants)) {
            for (const occ of r.occupants) {
              if (occ?.type === 'ADULT') totalAdults += 1;
              else if (occ?.type === 'CHILD') totalChildren += 1;
            }
          }
        }
      }
    }

    const roomTypesSummary =
      roomTypeNames.length === 0
        ? legacy.roomTypeFromTitle
        : roomTypeNames.length <= 2
          ? roomTypeNames.join(', ')
          : `${roomTypeNames[0]} +${roomTypeNames.length - 1} more`;

    const effectiveRooms = totalRooms > 0 ? totalRooms : legacy.roomsCount ?? rawBooking?.numberOfSeatsOrRooms ?? null;
    const roomsCountLabel =
      effectiveRooms && effectiveRooms > 0
        ? `${effectiveRooms} ${effectiveRooms === 1 ? 'Room' : 'Rooms'}`
        : null;

    const guestsSummaryLabel = hasGuestCounts && (totalAdults > 0 || totalChildren > 0)
      ? formatStayGuestsSummary(totalAdults, totalChildren)
      : legacy.adultsCount !== null
        ? formatStayGuestsSummary(legacy.adultsCount, legacy.childrenCount || 0)
        : null;

    return {
      roomTypesSummary,
      totalRooms: effectiveRooms,
      roomsCountLabel,
      guestsSummaryLabel
    };
  }

  const fallbackRooms = legacy.roomsCount ?? (rawBooking?.numberOfSeatsOrRooms > 0 ? Number(rawBooking.numberOfSeatsOrRooms) : null);
  const roomsCountLabel = fallbackRooms
    ? `${fallbackRooms} ${fallbackRooms === 1 ? 'Room' : 'Rooms'}`
    : null;
  const guestsSummaryLabel = legacy.adultsCount !== null
    ? formatStayGuestsSummary(legacy.adultsCount, legacy.childrenCount || 0)
    : null;

  return {
    roomTypesSummary: legacy.roomTypeFromTitle,
    totalRooms: fallbackRooms,
    roomsCountLabel,
    guestsSummaryLabel
  };
}

/** Builds a clean HOTEL booking card view model without mutating the source booking object. */
export function buildHotelBookingCardViewModel(
  rawBooking: any,
  hotelDetails?: HotelDetailsResponseDto | null,
  reservationDetails?: HotelReservationDetailResponse | null
): HotelBookingCardViewModel {
  const legacy = parseLegacyHotelDetails(rawBooking?.serviceTitle, rawBooking?.detailsJson);
  const statusInfo = resolveHotelBookingStatusPresentation(
    reservationDetails?.bookingStatus || rawBooking?.status
  );

  const hotelName = (
    hotelDetails?.hotel?.name ||
    reservationDetails?.hotel?.hotelName ||
    legacy.hotelNameFromTitle ||
    rawBooking?.serviceTitle ||
    'Hotel Booking'
  ).trim();

  const locationText = formatConfirmedHotelLocation(
    hotelDetails?.hotel?.address,
    hotelDetails?.hotel?.city
  );

  const checkInIso = (
    reservationDetails?.checkInDate ||
    rawBooking?.checkInDate ||
    legacy.checkInRaw ||
    null
  )?.trim() || null;

  const checkOutIso = (
    reservationDetails?.checkOutDate ||
    rawBooking?.checkOutDate ||
    legacy.checkOutRaw ||
    null
  )?.trim() || null;

  const checkInDisplay = formatHotelCalendarDate(checkInIso);
  const checkOutDisplay = formatHotelCalendarDate(checkOutIso);

  let stayDatesText = '';
  if (checkInDisplay && checkOutDisplay) {
    stayDatesText = `${checkInDisplay} – ${checkOutDisplay}`;
  } else if (checkInDisplay) {
    stayDatesText = `Check-in: ${checkInDisplay}`;
  } else if (rawBooking?.bookingDate) {
    stayDatesText = `Booked: ${formatHotelCalendarDate(rawBooking.bookingDate)}`;
  } else {
    stayDatesText = 'Stay dates unavailable';
  }

  const nightsCount = computeCalendarNights(
    checkInIso,
    checkOutIso,
    reservationDetails?.totalNights
  );
  const nightsText = nightsCount !== null
    ? `${nightsCount} ${nightsCount === 1 ? 'Night' : 'Nights'}`
    : null;

  const summary = extractRoomAndGuestSummary(rawBooking, reservationDetails);
  const bookingRef = (
    rawBooking?.bookingRef ||
    reservationDetails?.bookingRef ||
    rawBooking?.bookingReference ||
    `TEB${rawBooking?.id ?? ''}`
  ).trim();

  const currency = reservationDetails?.currency || 'INR';
  const totalAmount = Number(
    reservationDetails?.totalAmount ?? rawBooking?.totalAmount ?? 0
  );
  const formattedTotalPaid = formatAuthoritativeAmount(totalAmount, currency);

  const effectiveRefundStatus =
    reservationDetails?.cancellation?.refundStatus ??
    rawBooking?.refundStatus ??
    null;

  const refundBadge = resolveHotelCardRefundBadge(statusInfo.status, effectiveRefundStatus);

  const displaySubtitle = [summary.roomTypesSummary, summary.roomsCountLabel]
    .filter(Boolean)
    .join(' • ') || (locationText || 'Hotel Stay');

  return {
    raw: rawBooking,
    id: Number(rawBooking?.id ?? 0),
    bookingRef,
    serviceType: 'HOTEL',
    serviceId: Number(rawBooking?.serviceId ?? hotelDetails?.hotel?.id ?? reservationDetails?.hotel?.hotelId ?? 0),
    hotelName,
    displayTitle: hotelName,
    locationText,
    displaySubtitle,
    thumbnail: resolveHotelCoverImageUrl(hotelDetails),
    checkInIso,
    checkOutIso,
    stayDatesText,
    dateText: stayDatesText,
    timeText: '',
    nightsCount,
    nightsText,
    roomTypesSummary: summary.roomTypesSummary,
    roomsCountLabel: summary.roomsCountLabel,
    guestsSummaryLabel: summary.guestsSummaryLabel,
    capacityText: summary.guestsSummaryLabel || summary.roomsCountLabel || '',
    totalAmount,
    currency,
    formattedTotalPaid,
    bookingDateText: formatHotelDateTime(reservationDetails?.bookingDate || rawBooking?.bookingDate),
    status: statusInfo.status,
    statusDisplay: statusInfo.label,
    statusBadgeClass: statusInfo.badgeClass,
    refundBadge,
    refundStatus: effectiveRefundStatus,
    refundId: rawBooking?.refundId || null,
    refundAmount: reservationDetails?.cancellation?.refundAmount ?? rawBooking?.refundAmount ?? null,
    isCancellable: statusInfo.status === 'CONFIRMED',
    isCompleted: statusInfo.status === 'COMPLETED' || statusInfo.status === 'CHECKED_OUT',
    isConfirmed: statusInfo.status === 'CONFIRMED',
    isEnriched: Boolean(hotelDetails || reservationDetails),
    rawBookingDate: rawBooking?.bookingDate,
    detailsJson: rawBooking?.detailsJson
  };
}

/** Builds flat room-wise cards paired with `Guest Details (Room X)` tables from snapshot allocations. */
function buildRoomGuestCards(
  reservationDetails: HotelReservationDetailResponse | null | undefined,
  hotelDetails: HotelDetailsResponseDto | null | undefined,
  coverImageUrl: string
): HotelBookingRoomCardViewModel[] {
  const allocations = Array.isArray(reservationDetails?.allocations)
    ? reservationDetails!.allocations
    : [];

  if (allocations.length === 0) {
    return [];
  }

  const cards: HotelBookingRoomCardViewModel[] = [];
  let overallRoomSeq = 0;

  for (const alloc of allocations) {
    if (!alloc) continue;
    const roomImage = resolveRoomTypeImageUrl(
      alloc.roomId,
      alloc.roomTypeName,
      hotelDetails,
      coverImageUrl
    );
    const physicalRooms = Array.isArray(alloc.rooms) ? alloc.rooms : [];

    if (physicalRooms.length > 0) {
      const sortedRooms = [...physicalRooms].sort(
        (a, b) => Number(a?.roomSequence || 0) - Number(b?.roomSequence || 0)
      );
      for (const room of sortedRooms) {
        overallRoomSeq += 1;
        const occupants = Array.isArray(room?.occupants) ? room.occupants : [];
        const adults =
          room?.adults !== undefined && room?.adults !== null
            ? Math.max(0, Number(room.adults))
            : occupants.filter(o => o?.type === 'ADULT').length;
        const children =
          room?.children !== undefined && room?.children !== null
            ? Math.max(0, Number(room.children))
            : occupants.filter(o => o?.type === 'CHILD').length;

        const guests: HotelBookingGuestRow[] = occupants.map((occ: HotelRoomOccupantPayload, idx: number) => {
          const isChild = occ?.type === 'CHILD';
          const ageDisplay =
            occ?.age !== null && occ?.age !== undefined && Number.isFinite(Number(occ.age))
              ? String(Number(occ.age))
              : '—';
          const genderDisplay = isChild ? 'NA' : formatGenderLabel(occ?.gender);

          return {
            index: idx + 1,
            name: (occ?.name || '').trim() || '—',
            type: isChild ? 'CHILD' : 'ADULT',
            isChild,
            ageDisplay,
            genderDisplay
          };
        });

        const occupancySummary =
          adults > 0 || children > 0
            ? formatStayGuestsSummary(adults, children)
            : 'Occupancy unavailable';

        cards.push({
          roomSequenceOverall: overallRoomSeq,
          roomId: alloc.roomId,
          roomTypeName: alloc.roomTypeName || 'Hotel Room',
          imageUrl: roomImage,
          roomCountSummary: '1 Room',
          occupancySummary,
          adults,
          children,
          guests
        });
      }
    } else {
      const qty = Math.max(1, Number(alloc.quantity || 1));
      for (let i = 0; i < qty; i += 1) {
        overallRoomSeq += 1;
        cards.push({
          roomSequenceOverall: overallRoomSeq,
          roomId: alloc.roomId,
          roomTypeName: alloc.roomTypeName || 'Hotel Room',
          imageUrl: roomImage,
          roomCountSummary: '1 Room',
          occupancySummary: 'Guest details unavailable',
          adults: 0,
          children: 0,
          guests: []
        });
      }
    }
  }

  return cards;
}

/** Resolves authoritative refund presentation from Booking, HotelReservationDetailResponse, and PaymentTransaction. */
function buildRefundViewModel(
  rawBooking: any,
  reservationDetails: HotelReservationDetailResponse | null | undefined,
  paymentTx: PaymentTransactionDto | null | undefined,
  currency: string
): HotelBookingRefundViewModel {
  const status = (reservationDetails?.bookingStatus || rawBooking?.status || '').toUpperCase().trim();
  const isCancelled = status === 'CANCELLED';

  const bookingRefundStatus = (rawBooking?.refundStatus || '').toUpperCase().trim();
  const resRefundStatus = (reservationDetails?.cancellation?.refundStatus || '').toUpperCase().trim();
  const payRefundStatus = (paymentTx?.refundStatus || '').toUpperCase().trim();

  const refundAmountRaw =
    reservationDetails?.cancellation?.refundAmount ??
    rawBooking?.refundAmount ??
    paymentTx?.refundAmount ??
    null;
  const refundAmount =
    refundAmountRaw !== null && refundAmountRaw !== undefined && Number.isFinite(Number(refundAmountRaw))
      ? Number(refundAmountRaw)
      : null;

  const refundId =
    rawBooking?.refundId ||
    paymentTx?.refundId ||
    null;

  const cancelledAtText = formatHotelDateTime(
    reservationDetails?.cancellation?.cancelledAt || rawBooking?.cancelledAt
  );
  const refundedAtText = formatHotelDateTime(paymentTx?.refundedAt);
  const cancellationReason =
    reservationDetails?.cancellation?.cancellationReason ||
    rawBooking?.cancellationReason ||
    null;
  const cancellationPolicyTier = rawBooking?.cancellationPolicyTier || null;
  const cancellationFeeRaw = rawBooking?.cancellationFee;
  const cancellationFeeText =
    cancellationFeeRaw !== null && cancellationFeeRaw !== undefined && Number.isFinite(Number(cancellationFeeRaw))
      ? formatAuthoritativeAmount(Number(cancellationFeeRaw), currency)
      : null;

  if (!isCancelled) {
    return {
      stateKey: 'NONE',
      badgeLabel: 'No Refund Initiated',
      badgeClass: 'refund-state-neutral',
      iconClass: 'fa-solid fa-shield-check',
      headline: 'No Refund Initiated',
      description: 'This booking is not cancelled. If cancelled later, refund details under the hotel policy will appear here.',
      refundAmount: null,
      formattedRefundAmount: null,
      refundId: null,
      refundedAtText: null,
      cancelledAtText: null,
      cancellationReason: null,
      cancellationPolicyTier: null,
      cancellationFeeText: null
    };
  }

  // Merge status cleanly: authoritative PROCESSED / FAILED_RETRYABLE / FAILED_FINAL / INITIATED / NOT_APPLICABLE
  const effectiveStatus = bookingRefundStatus || resRefundStatus || payRefundStatus;

  if (effectiveStatus === 'PROCESSED' || payRefundStatus === 'PROCESSED' || payRefundStatus === 'COMPLETED') {
    return {
      stateKey: 'PROCESSED',
      badgeLabel: 'Refund Processed',
      badgeClass: 'refund-state-success',
      iconClass: 'fa-solid fa-circle-check',
      headline: 'Refund Processed',
      description: 'Your hotel cancellation refund has been processed to the original payment method.',
      refundAmount,
      formattedRefundAmount: refundAmount !== null ? formatAuthoritativeAmount(refundAmount, currency) : null,
      refundId,
      refundedAtText,
      cancelledAtText,
      cancellationReason,
      cancellationPolicyTier,
      cancellationFeeText
    };
  }

  if (effectiveStatus === 'FAILED_RETRYABLE') {
    return {
      stateKey: 'RETRY_QUEUED',
      badgeLabel: 'Refund Pending Retry',
      badgeClass: 'refund-state-warning',
      iconClass: 'fa-solid fa-clock-rotate-left',
      headline: 'Refund Initiated — Automatic Retry Queued',
      description: 'The cancellation is confirmed, and gateway refund settlement is temporarily queued for automatic retry.',
      refundAmount,
      formattedRefundAmount: refundAmount !== null ? formatAuthoritativeAmount(refundAmount, currency) : null,
      refundId,
      refundedAtText,
      cancelledAtText,
      cancellationReason,
      cancellationPolicyTier,
      cancellationFeeText
    };
  }

  if (effectiveStatus === 'FAILED_FINAL' || effectiveStatus === 'FAILED') {
    return {
      stateKey: 'FAILED',
      badgeLabel: 'Refund Failed',
      badgeClass: 'refund-state-danger',
      iconClass: 'fa-solid fa-circle-exclamation',
      headline: 'Refund Settlement Issue',
      description: paymentTx?.errorDescription || 'The gateway refund could not be completed automatically. Please contact TravelEasy Support with your Booking ID.',
      refundAmount,
      formattedRefundAmount: refundAmount !== null ? formatAuthoritativeAmount(refundAmount, currency) : null,
      refundId,
      refundedAtText,
      cancelledAtText,
      cancellationReason,
      cancellationPolicyTier,
      cancellationFeeText
    };
  }

  if (effectiveStatus === 'INITIATED' || effectiveStatus === 'PENDING') {
    return {
      stateKey: 'PENDING',
      badgeLabel: 'Refund Pending',
      badgeClass: 'refund-state-info',
      iconClass: 'fa-regular fa-clock',
      headline: 'Refund Pending Confirmation',
      description: 'Your refund request has been submitted to the payment gateway and is awaiting settlement confirmation.',
      refundAmount,
      formattedRefundAmount: refundAmount !== null ? formatAuthoritativeAmount(refundAmount, currency) : null,
      refundId,
      refundedAtText,
      cancelledAtText,
      cancellationReason,
      cancellationPolicyTier,
      cancellationFeeText
    };
  }

  if (
    effectiveStatus === 'NON_REFUNDABLE' ||
    effectiveStatus === 'NOT_APPLICABLE' ||
    effectiveStatus === 'NONE' ||
    effectiveStatus === 'ZERO_REFUND' ||
    refundAmount === 0
  ) {
    return {
      stateKey: 'NON_REFUNDABLE',
      badgeLabel: 'Non-Refundable',
      badgeClass: 'refund-state-neutral',
      iconClass: 'fa-solid fa-ban',
      headline: 'No Refund Applicable',
      description: 'This cancellation fell inside the non-refundable policy window (₹0 refundable).',
      refundAmount: 0,
      formattedRefundAmount: formatAuthoritativeAmount(0, currency),
      refundId: null,
      refundedAtText: null,
      cancelledAtText,
      cancellationReason,
      cancellationPolicyTier,
      cancellationFeeText
    };
  }

  return {
    stateKey: 'PENDING',
    badgeLabel: 'Cancellation Recorded',
    badgeClass: 'refund-state-info',
    iconClass: 'fa-solid fa-circle-info',
    headline: 'Refund Status Updating',
    description: 'This booking has been cancelled. Check back shortly or contact support if no refund update appears.',
    refundAmount,
    formattedRefundAmount: refundAmount !== null ? formatAuthoritativeAmount(refundAmount, currency) : null,
    refundId,
    refundedAtText,
    cancelledAtText,
    cancellationReason,
    cancellationPolicyTier,
    cancellationFeeText
  };
}

/** Builds complete view model for the Hotel Booking Details modal across all 3 tabs. */
export function buildHotelBookingDetailsViewModel(
  rawBooking: any,
  context: HotelBookingEnrichmentContext = {}
): HotelBookingDetailsViewModel {
  const hotelDetails = context.hotelDetails || null;
  const reservationDetails = context.reservationDetails || null;
  const paymentTx = context.paymentTransaction || null;
  const reviews = context.reviews || null;

  const card = buildHotelBookingCardViewModel(rawBooking, hotelDetails, reservationDetails);
  const legacy = parseLegacyHotelDetails(rawBooking?.serviceTitle, rawBooking?.detailsJson);

  const checkInDateText = formatHotelCalendarDate(card.checkInIso) || 'Not available';
  const checkOutDateText = formatHotelCalendarDate(card.checkOutIso) || 'Not available';
  const durationText = card.nightsText || 'Unavailable';

  const rooms = buildRoomGuestCards(reservationDetails, hotelDetails, card.thumbnail);
  const isLegacyWithoutAllocations = rooms.length === 0;
  const legacyRoomSummary = isLegacyWithoutAllocations
    ? [card.roomTypesSummary || legacy.roomTypeFromTitle, card.roomsCountLabel, card.guestsSummaryLabel]
        .filter(Boolean)
        .join(' • ') || null
    : null;

  const priceLineItems: HotelBookingPriceLineItem[] = (reservationDetails?.allocations || [])
    .filter(a => a && a.pricing?.allocationTotal !== undefined && a.pricing?.allocationTotal !== null)
    .map(a => {
      const amount = Number(a.pricing!.allocationTotal);
      return {
        roomTypeName: a.roomTypeName || 'Hotel Room',
        quantity: Math.max(1, Number(a.quantity || 1)),
        amount,
        formattedAmount: formatAuthoritativeAmount(amount, card.currency)
      };
    });

  const paymentStatusRaw = (
    paymentTx?.status ||
    reservationDetails?.paymentStatus ||
    (card.status === 'CONFIRMED' || card.status === 'COMPLETED' ? 'PAID' : card.status)
  ).toUpperCase().trim();

  const paymentStatusLabel =
    paymentStatusRaw === 'SUCCESS' || paymentStatusRaw === 'CAPTURED' || paymentStatusRaw === 'PAID'
      ? 'Paid'
      : paymentStatusRaw === 'REFUNDED'
        ? 'Refunded'
        : paymentStatusRaw.replace(/_/g, ' ');

  const paymentReference = paymentTx?.razorpayPaymentId || paymentTx?.transactionRef || null;

  const refund = buildRefundViewModel(rawBooking, reservationDetails, paymentTx, card.currency);

  return {
    bookingId: card.id,
    bookingRef: card.bookingRef,
    bookedOnText: formatHotelDateTime(
      reservationDetails?.bookingDate || rawBooking?.bookingDate || rawBooking?.confirmedAt || rawBooking?.createdAt
    ),
    status: card.status,
    statusLabel: card.statusDisplay,
    statusBadgeClass: card.statusBadgeClass,
    hotelId: card.serviceId,
    hotelName: card.hotelName,
    locationText: card.locationText,
    hotelImageUrl: card.thumbnail,
    reviewSummary: buildHotelReviewSummary(reviews),
    checkInDateText,
    checkInTimeNote: STANDARD_HOTEL_CHECKIN_NOTE,
    checkOutDateText,
    checkOutTimeNote: null,
    durationText,
    nightsCount: card.nightsCount,
    primaryGuest: reservationDetails?.primaryGuest || null,
    rooms,
    isLegacyWithoutAllocations,
    legacyRoomSummary,
    currency: card.currency,
    priceLineItems,
    taxesNote: 'Included in room charges',
    totalAmount: card.totalAmount,
    formattedTotalPaid: card.formattedTotalPaid,
    paymentStatusLabel,
    paymentReference,
    refund,
    policyRules: HOTEL_CANCELLATION_POLICY_RULES,
    policyMetaNote: HOTEL_POLICY_META_NOTE
  };
}
