import { HotelReservationDetailResponse, PaymentTransactionDto } from '../../models/hotel.model';
import {
  extractCalendarDateIso,
  extractStayDateFromBooking,
  normalizeStatus,
  parseBookingDetailsJson,
  resolveBookingRoomCount,
  toUtcDayNumber
} from './hotel-dashboard-metrics.util';

export type ReservationStatusFilterKey =
  | 'ALL'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'CHECKED_OUT'
  | 'NO_SHOW'
  | 'CANCELLED';

export interface ReservationKpiSummary {
  totalReservations: number;
  confirmedCount: number;
  currentlyStayingCount: number;
  completedCount: number;
}

export interface ReservationStatusCounts {
  ALL: number;
  CONFIRMED: number;
  CHECKED_IN: number;
  CHECKED_OUT: number;
  NO_SHOW: number;
  CANCELLED: number;
}

export interface ReservationRoomChip {
  roomTypeName: string;
  quantity: number;
}

export interface ReservationPaymentPresentation {
  rawStatus: string;
  label: string;
  badgeClass: string;
  iconClass: string;
}

export interface ReservationStatusPresentation {
  rawStatus: string;
  label: string;
  badgeClass: string;
  iconClass: string;
}

export interface ReservationGuestContact {
  name: string;
  email: string;
  phone: string;
}

export interface HotelReservationCardViewModel {
  raw: any;
  bookingRef: string;
  normalizedStatus: string;
  status: ReservationStatusPresentation;
  bookedOnText: string | null;
  guest: ReservationGuestContact;
  checkInText: string;
  checkOutText: string;
  nightsCount: number | null;
  nightsText: string;
  totalRoomsCount: number;
  totalRoomsText: string;
  roomTypes: ReservationRoomChip[];
  totalAmount: number;
  payment: ReservationPaymentPresentation;
  canCheckIn: boolean;
  canCheckOut: boolean;
  canMarkNoShow: boolean;
  isCompleted: boolean;
  isNoShow: boolean;
  isCancelled: boolean;
}

export interface ReservationModalRoomRow {
  roomTypeName: string;
  subtitle: string | null;
  quantity: number;
  quantityText: string;
  occupancyText: string;
  pricePerNightText: string;
  amountText: string;
}

export interface ReservationModalPaymentSummary {
  roomChargesAmount: number | null;
  taxesAmount: number | null;
  taxesLabel: string | null;
  otherChargesAmount: number | null;
  otherChargesLabel: string | null;
  totalAmount: number;
  taxesNote: string | null;
  payment: ReservationPaymentPresentation;
}

export interface HotelReservationModalViewModel {
  raw: any;
  bookingRef: string;
  normalizedStatus: string;
  status: ReservationStatusPresentation;
  bookedOnText: string | null;
  guest: ReservationGuestContact;
  checkInText: string;
  checkOutText: string;
  nightsCount: number | null;
  nightsText: string;
  totalGuestsText: string;
  roomRows: ReservationModalRoomRow[];
  paymentSummary: ReservationModalPaymentSummary;
  canCheckIn: boolean;
  canCheckOut: boolean;
  canMarkNoShow: boolean;
}

const MONTH_ABBR = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export const RESERVATION_FILTER_OPTIONS: ReadonlyArray<{
  key: ReservationStatusFilterKey;
  label: string;
}> = [
  { key: 'ALL', label: 'All' },
  { key: 'CONFIRMED', label: 'Confirmed' },
  { key: 'CHECKED_IN', label: 'Checked In' },
  { key: 'CHECKED_OUT', label: 'Checked Out' },
  { key: 'NO_SHOW', label: 'No Show' },
  { key: 'CANCELLED', label: 'Cancelled' }
];

/** Formats YYYY-MM-DD (or timestamp) safely without timezone shift as `18 Nov 2026`. */
export function formatReservationStayDate(value: unknown): string {
  const iso = extractCalendarDateIso(value);
  if (!iso) {
    return 'Not available';
  }
  const parts = iso.split('-');
  if (parts.length !== 3) {
    return iso;
  }
  const year = Number(parts[0]);
  const monthIdx = Number(parts[1]) - 1;
  const day = Number(parts[2]);
  if (!Number.isInteger(year) || !Number.isInteger(monthIdx) || !Number.isInteger(day) || monthIdx < 0 || monthIdx > 11) {
    return iso;
  }
  return `${day} ${MONTH_ABBR[monthIdx]} ${year}`;
}

/** Formats creation date/time as `16 Nov 2026, 10:24 AM`. */
export function formatReservationBookedDateTime(value: unknown): string | null {
  if (typeof value !== 'string' || !value.trim()) {
    return null;
  }
  const trimmed = value.trim();
  const parsed = new Date(trimmed);
  if (Number.isNaN(parsed.getTime())) {
    const fallbackDate = formatReservationStayDate(trimmed);
    return fallbackDate === 'Not available' ? null : fallbackDate;
  }
  const day = parsed.getDate();
  const month = MONTH_ABBR[parsed.getMonth()];
  const year = parsed.getFullYear();
  const rawHours = parsed.getHours();
  const minutes = String(parsed.getMinutes()).padStart(2, '0');
  const suffix = rawHours >= 12 ? 'PM' : 'AM';
  const hour12 = rawHours % 12 === 0 ? 12 : rawHours % 12;
  const hourStr = String(hour12).padStart(2, '0');
  return `${day} ${month} ${year}, ${hourStr}:${minutes} ${suffix}`;
}

/** Computes positive calendar nights between check-in and check-out without inventing defaults. */
export function computeReservationNights(
  checkInValue: unknown,
  checkOutValue: unknown,
  authoritativeNights?: number | null
): number | null {
  if (typeof authoritativeNights === 'number' && Number.isFinite(authoritativeNights) && authoritativeNights > 0) {
    return Math.floor(authoritativeNights);
  }
  const inIso = extractCalendarDateIso(checkInValue);
  const outIso = extractCalendarDateIso(checkOutValue);
  if (!inIso || !outIso) {
    return null;
  }
  const inDay = toUtcDayNumber(inIso);
  const outDay = toUtcDayNumber(outIso);
  if (inDay === null || outDay === null) {
    return null;
  }
  const diff = outDay - inDay;
  return diff > 0 ? diff : null;
}

function isCheckedOutStatus(status: string): boolean {
  return status === 'CHECKED_OUT' || status === 'COMPLETED';
}

export function matchesReservationStatusFilter(
  status: unknown,
  filterKey: ReservationStatusFilterKey
): boolean {
  if (filterKey === 'ALL') {
    return true;
  }
  const norm = normalizeStatus(status);
  if (filterKey === 'CHECKED_OUT') {
    return isCheckedOutStatus(norm);
  }
  return norm === filterKey;
}

/** Computes KPI cards strictly from the current property's reservations list. */
export function computeReservationKpis(reservations: any[] | null | undefined): ReservationKpiSummary {
  const list = Array.isArray(reservations) ? reservations : [];
  let confirmedCount = 0;
  let currentlyStayingCount = 0;
  let completedCount = 0;

  for (const r of list) {
    const s = normalizeStatus(r?.status);
    if (s === 'CONFIRMED') confirmedCount += 1;
    else if (s === 'CHECKED_IN') currentlyStayingCount += 1;
    else if (isCheckedOutStatus(s)) completedCount += 1;
  }

  return {
    totalReservations: list.length,
    confirmedCount,
    currentlyStayingCount,
    completedCount
  };
}

/** Computes synchronized counts for every filter tab from actual reservations matching the current search. */
export function computeReservationStatusCounts(reservations: any[] | null | undefined): ReservationStatusCounts {
  const list = Array.isArray(reservations) ? reservations : [];
  const counts: ReservationStatusCounts = {
    ALL: list.length,
    CONFIRMED: 0,
    CHECKED_IN: 0,
    CHECKED_OUT: 0,
    NO_SHOW: 0,
    CANCELLED: 0
  };

  for (const r of list) {
    const s = normalizeStatus(r?.status);
    if (s === 'CONFIRMED') counts.CONFIRMED += 1;
    else if (s === 'CHECKED_IN') counts.CHECKED_IN += 1;
    else if (isCheckedOutStatus(s)) counts.CHECKED_OUT += 1;
    else if (s === 'NO_SHOW') counts.NO_SHOW += 1;
    else if (s === 'CANCELLED') counts.CANCELLED += 1;
  }

  return counts;
}

export function filterReservationsBySearch(
  reservations: any[] | null | undefined,
  searchQuery: string
): any[] {
  const list = Array.isArray(reservations) ? reservations : [];
  const term = (searchQuery || '').trim().toLowerCase();
  if (!term) {
    return list;
  }

  return list.filter((res) => {
    const ref = String(res?.bookingRef || res?.bookingReference || '').toLowerCase();
    const guestName = String(res?.customerName || res?.primaryGuest?.fullName || '').toLowerCase();
    return ref.includes(term) || guestName.includes(term);
  });
}

export function filterReservationsList(
  reservations: any[] | null | undefined,
  statusFilter: ReservationStatusFilterKey,
  searchQuery: string
): any[] {
  const searched = filterReservationsBySearch(reservations, searchQuery);
  return searched.filter((res) => matchesReservationStatusFilter(res?.status, statusFilter));
}

export function resolveReservationStatusPresentation(statusValue: unknown): ReservationStatusPresentation {
  const norm = normalizeStatus(statusValue);
  switch (norm) {
    case 'CONFIRMED':
      return {
        rawStatus: norm,
        label: 'Confirmed',
        badgeClass: 'res-badge-confirmed',
        iconClass: 'fa-solid fa-check'
      };
    case 'CHECKED_IN':
      return {
        rawStatus: norm,
        label: 'Checked In',
        badgeClass: 'res-badge-checkedin',
        iconClass: 'fa-solid fa-check'
      };
    case 'CHECKED_OUT':
    case 'COMPLETED':
      return {
        rawStatus: norm,
        label: 'Checked Out',
        badgeClass: 'res-badge-checkedout',
        iconClass: 'fa-solid fa-flag-checkered'
      };
    case 'NO_SHOW':
      return {
        rawStatus: norm,
        label: 'No Show',
        badgeClass: 'res-badge-noshow',
        iconClass: 'fa-solid fa-ban'
      };
    case 'CANCELLED':
      return {
        rawStatus: norm,
        label: 'Cancelled',
        badgeClass: 'res-badge-cancelled',
        iconClass: 'fa-solid fa-xmark'
      };
    case 'PENDING_PAYMENT':
    case 'INITIATED':
      return {
        rawStatus: norm,
        label: 'Pending Payment',
        badgeClass: 'res-badge-pending',
        iconClass: 'fa-regular fa-clock'
      };
    case 'EXPIRED':
      return {
        rawStatus: norm,
        label: 'Expired',
        badgeClass: 'res-badge-neutral',
        iconClass: 'fa-regular fa-clock'
      };
    case 'FAILED':
      return {
        rawStatus: norm,
        label: 'Failed',
        badgeClass: 'res-badge-cancelled',
        iconClass: 'fa-solid fa-triangle-exclamation'
      };
    default:
      return {
        rawStatus: norm,
        label: norm ? norm.replace(/_/g, ' ') : 'Unknown',
        badgeClass: 'res-badge-neutral',
        iconClass: 'fa-solid fa-circle-info'
      };
  }
}

/** Resolves payment status distinctly from reservation lifecycle status without assuming payment success. */
export function resolveReservationPaymentPresentation(
  booking: any,
  detail?: HotelReservationDetailResponse | null,
  paymentTx?: PaymentTransactionDto | null
): ReservationPaymentPresentation {
  const detailsJsonObj = parseBookingDetailsJson(booking);
  const explicitRaw = normalizeStatus(
    paymentTx?.status ??
      detail?.paymentStatus ??
      booking?.paymentStatus ??
      detailsJsonObj?.paymentStatus ??
      detailsJsonObj?.payment?.status
  );

  const refundRaw = normalizeStatus(
    paymentTx?.refundStatus ?? detail?.cancellation?.refundStatus ?? booking?.refundStatus
  );

  if (explicitRaw) {
    if (explicitRaw === 'SUCCESS' || explicitRaw === 'CAPTURED' || explicitRaw === 'PAID') {
      if (refundRaw === 'PROCESSED' || refundRaw === 'COMPLETED') {
        return {
          rawStatus: 'REFUNDED',
          label: 'Refunded',
          badgeClass: 'pay-badge-refunded',
          iconClass: 'fa-solid fa-rotate-left'
        };
      }
      if (refundRaw === 'INITIATED' || refundRaw === 'PENDING') {
        return {
          rawStatus: 'REFUND_INITIATED',
          label: 'Refund Pending',
          badgeClass: 'pay-badge-pending',
          iconClass: 'fa-regular fa-clock'
        };
      }
      return {
        rawStatus: explicitRaw,
        label: 'Paid',
        badgeClass: 'pay-badge-paid',
        iconClass: 'fa-solid fa-circle-check'
      };
    }
    if (explicitRaw === 'REFUNDED') {
      return {
        rawStatus: explicitRaw,
        label: 'Refunded',
        badgeClass: 'pay-badge-refunded',
        iconClass: 'fa-solid fa-rotate-left'
      };
    }
    if (explicitRaw === 'REFUND_INITIATED') {
      return {
        rawStatus: explicitRaw,
        label: 'Refund Initiated',
        badgeClass: 'pay-badge-pending',
        iconClass: 'fa-regular fa-clock'
      };
    }
    if (explicitRaw === 'FAILED') {
      return {
        rawStatus: explicitRaw,
        label: 'Payment Failed',
        badgeClass: 'pay-badge-failed',
        iconClass: 'fa-solid fa-circle-xmark'
      };
    }
    if (explicitRaw === 'PENDING' || explicitRaw === 'INITIATED' || explicitRaw === 'CREATED') {
      return {
        rawStatus: explicitRaw,
        label: 'Pending',
        badgeClass: 'pay-badge-pending',
        iconClass: 'fa-regular fa-clock'
      };
    }
    if (explicitRaw === 'UNPAID' || explicitRaw === 'NOT_PAID') {
      return {
        rawStatus: explicitRaw,
        label: 'Unpaid',
        badgeClass: 'pay-badge-neutral',
        iconClass: 'fa-solid fa-circle-exclamation'
      };
    }
    if (explicitRaw === 'CANCELLED') {
      return {
        rawStatus: explicitRaw,
        label: refundRaw === 'PROCESSED' || refundRaw === 'COMPLETED' ? 'Refunded' : 'Cancelled',
        badgeClass: refundRaw === 'PROCESSED' || refundRaw === 'COMPLETED' ? 'pay-badge-refunded' : 'pay-badge-neutral',
        iconClass: 'fa-solid fa-circle-info'
      };
    }
  }

  // Fallback derivation when no payment transaction endpoint record is present on list item
  if (refundRaw === 'PROCESSED' || refundRaw === 'COMPLETED') {
    return {
      rawStatus: 'REFUNDED',
      label: 'Refunded',
      badgeClass: 'pay-badge-refunded',
      iconClass: 'fa-solid fa-rotate-left'
    };
  }
  if (refundRaw === 'INITIATED' || refundRaw === 'PENDING') {
    return {
      rawStatus: 'REFUND_INITIATED',
      label: 'Refund Pending',
      badgeClass: 'pay-badge-pending',
      iconClass: 'fa-regular fa-clock'
    };
  }
  if (refundRaw === 'FAILED') {
    return {
      rawStatus: 'REFUND_FAILED',
      label: 'Refund Failed',
      badgeClass: 'pay-badge-failed',
      iconClass: 'fa-solid fa-triangle-exclamation'
    };
  }

  const bookingStatus = normalizeStatus(detail?.bookingStatus ?? booking?.status);
  if (
    bookingStatus === 'CONFIRMED' ||
    bookingStatus === 'CHECKED_IN' ||
    bookingStatus === 'CHECKED_OUT' ||
    bookingStatus === 'COMPLETED' ||
    bookingStatus === 'NO_SHOW'
  ) {
    return {
      rawStatus: 'PAID',
      label: 'Paid',
      badgeClass: 'pay-badge-paid',
      iconClass: 'fa-solid fa-circle-check'
    };
  }
  if (bookingStatus === 'CANCELLED') {
    const hadRefundOrConfirm =
      Boolean(booking?.confirmedAt) ||
      Boolean(booking?.refundId) ||
      (Number(booking?.refundAmount) || 0) > 0 ||
      refundRaw === 'NON_REFUNDABLE';
    return hadRefundOrConfirm
      ? {
          rawStatus: refundRaw || 'PAID',
          label: refundRaw === 'NON_REFUNDABLE' ? 'Non-Refundable' : 'Paid',
          badgeClass: 'pay-badge-neutral',
          iconClass: 'fa-solid fa-circle-info'
        }
      : {
          rawStatus: 'UNPAID',
          label: 'Unpaid',
          badgeClass: 'pay-badge-neutral',
          iconClass: 'fa-solid fa-circle-exclamation'
        };
  }
  if (bookingStatus === 'PENDING_PAYMENT' || bookingStatus === 'INITIATED') {
    return {
      rawStatus: bookingStatus,
      label: 'Pending',
      badgeClass: 'pay-badge-pending',
      iconClass: 'fa-regular fa-clock'
    };
  }
  if (bookingStatus === 'FAILED') {
    return {
      rawStatus: bookingStatus,
      label: 'Payment Failed',
      badgeClass: 'pay-badge-failed',
      iconClass: 'fa-solid fa-circle-xmark'
    };
  }
  if (bookingStatus === 'EXPIRED') {
    return {
      rawStatus: bookingStatus,
      label: 'Unpaid (Expired)',
      badgeClass: 'pay-badge-neutral',
      iconClass: 'fa-regular fa-clock'
    };
  }

  return {
    rawStatus: 'UNKNOWN',
    label: 'Unavailable',
    badgeClass: 'pay-badge-neutral',
    iconClass: 'fa-solid fa-circle-info'
  };
}

function extractPipeField(rawDetails: unknown, pattern: RegExp): string | null {
  if (typeof rawDetails !== 'string' || !rawDetails.trim() || rawDetails.trim().startsWith('{')) {
    return null;
  }
  for (const segment of rawDetails.split('|')) {
    const trimmed = segment.trim();
    const match = trimmed.match(pattern);
    if (match && match[1]) {
      return match[1].trim();
    }
  }
  return null;
}

export function extractReservationGuestContact(
  booking: any,
  detail?: HotelReservationDetailResponse | null
): ReservationGuestContact {
  const parsed = parseBookingDetailsJson(booking);

  const name =
    String(
      detail?.primaryGuest?.fullName ||
        booking?.customerName ||
        booking?.primaryGuest?.fullName ||
        parsed?.primaryGuest?.fullName ||
        parsed?.guestName ||
        parsed?.fullName ||
        ''
    ).trim() || 'Guest Name Unavailable';

  const email = String(
    detail?.primaryGuest?.email ||
      booking?.customerEmail ||
      booking?.primaryGuest?.email ||
      parsed?.primaryGuest?.email ||
      parsed?.email ||
      extractPipeField(booking?.detailsJson, /^(?:email|e-mail)\s*:\s*(.+)$/i) ||
      ''
  ).trim();

  const phone = String(
    detail?.primaryGuest?.mobile ||
      booking?.customerPhone ||
      booking?.primaryGuestMobile ||
      booking?.phone ||
      booking?.mobile ||
      booking?.contactNumber ||
      booking?.primaryGuest?.mobile ||
      parsed?.primaryGuest?.mobile ||
      parsed?.primaryGuest?.phone ||
      parsed?.customerPhone ||
      parsed?.mobile ||
      parsed?.phone ||
      parsed?.contactNumber ||
      extractPipeField(booking?.detailsJson, /^(?:phone|mobile|contact(?:\s*number)?)\s*:\s*(.+)$/i) ||
      ''
  ).trim();

  return { name, email, phone };
}

function parseRoomTypeSummaryTokens(summary: string): ReservationRoomChip[] {
  const chips: ReservationRoomChip[] = [];
  for (const rawToken of summary.split(',')) {
    const token = rawToken.trim();
    if (!token) continue;
    const match = token.match(/^(.*?)\s*\(x(\d+)\)$/i);
    if (match) {
      const name = match[1].trim() || 'Room';
      const qty = Math.max(1, Number(match[2]) || 1);
      chips.push({ roomTypeName: name, quantity: qty });
    } else {
      chips.push({ roomTypeName: token, quantity: 1 });
    }
  }
  return chips;
}

/** Extracts multi-room type allocations from snapshot details, allocations, roomTypeSummary, or legacy fields. */
export function extractReservationRoomChips(
  booking: any,
  roomTypesCatalog?: any[] | null,
  detail?: HotelReservationDetailResponse | null
): ReservationRoomChip[] {
  const roomCatalogMap = new Map<number, any>();
  if (Array.isArray(roomTypesCatalog)) {
    for (const rt of roomTypesCatalog) {
      if (rt?.id != null) {
        roomCatalogMap.set(Number(rt.id), rt);
      }
    }
  }

  // 1. Authoritative snapshot allocations (modal details)
  if (Array.isArray(detail?.allocations) && detail!.allocations.length > 0) {
    return detail!.allocations.map((a) => ({
      roomTypeName: (a?.roomTypeName || roomCatalogMap.get(Number(a?.roomId))?.roomTypeName || 'Room').trim(),
      quantity: Math.max(1, Number(a?.quantity || (Array.isArray(a?.rooms) ? a.rooms.length : 1)) || 1)
    }));
  }

  // 2. JSON details allocations with roomTypeName
  const parsed = parseBookingDetailsJson(booking);
  if (parsed && Array.isArray(parsed.allocations) && parsed.allocations.length > 0) {
    const hasNames = parsed.allocations.some((a: any) => typeof a?.roomTypeName === 'string' && a.roomTypeName.trim());
    if (hasNames) {
      return parsed.allocations.map((a: any) => ({
        roomTypeName: String(
          a?.roomTypeName || roomCatalogMap.get(Number(a?.roomId))?.roomTypeName || 'Room'
        ).trim(),
        quantity: Math.max(1, Number(a?.quantity) || 1)
      }));
    }
  }

  // 3. Backend Booking.allocations paired with roomTypeCatalog & roomTypeSummary
  const summaryTokens =
    typeof booking?.roomTypeSummary === 'string' && booking.roomTypeSummary.trim()
      ? parseRoomTypeSummaryTokens(booking.roomTypeSummary)
      : [];

  if (Array.isArray(booking?.allocations) && booking.allocations.length > 0) {
    const fromAllocs: ReservationRoomChip[] = booking.allocations.map((alloc: any, idx: number) => {
      const explicitName = typeof alloc?.roomTypeName === 'string' ? alloc.roomTypeName.trim() : '';
      const catalogName = alloc?.roomId != null ? roomCatalogMap.get(Number(alloc.roomId))?.roomTypeName : '';
      const summaryName = summaryTokens.length === booking.allocations.length ? summaryTokens[idx]?.roomTypeName : '';
      const qty = Math.max(1, Number(alloc?.quantity) || summaryTokens[idx]?.quantity || 1);
      return {
        roomTypeName: (explicitName || catalogName || summaryName || 'Assigned Room').trim(),
        quantity: qty
      };
    });
    return fromAllocs;
  }

  if (summaryTokens.length > 0) {
    return summaryTokens;
  }

  // 4. Legacy single room fallback
  let fallbackName = String(
    parsed?.roomTypeName ||
      parsed?.roomType ||
      parsed?.category ||
      ''
  ).trim();

  if (!fallbackName && typeof booking?.serviceTitle === 'string') {
    const title = booking.serviceTitle.trim();
    const parenMatch = title.match(/\(([^()]+)\)$/);
    if (parenMatch) {
      fallbackName = parenMatch[1].trim();
    } else if (title.includes(' - ')) {
      const parts = title.split(' - ');
      fallbackName = parts[parts.length - 1].trim();
    }
  }

  const totalRooms = Math.max(1, resolveBookingRoomCount(booking));
  return [
    {
      roomTypeName: fallbackName || 'Assigned Room',
      quantity: totalRooms
    }
  ];
}

export function formatNumberedLabel(count: number, singular: string, plural: string): string {
  return `${count} ${count === 1 ? singular : plural}`;
}

/** Builds card view model for the single-column Reservations listing page. */
export function buildHotelReservationCardViewModel(
  res: any,
  roomTypesCatalog?: any[] | null
): HotelReservationCardViewModel {
  const normStatus = normalizeStatus(res?.status);
  const status = resolveReservationStatusPresentation(normStatus);
  const checkInRaw = extractStayDateFromBooking(res, 'in');
  const checkOutRaw = extractStayDateFromBooking(res, 'out');
  const nightsCount = computeReservationNights(checkInRaw, checkOutRaw);
  const roomTypes = extractReservationRoomChips(res, roomTypesCatalog);
  const totalRoomsCount =
    Number(res?.totalRoomsBooked) > 0
      ? Number(res.totalRoomsBooked)
      : roomTypes.reduce((sum, item) => sum + item.quantity, 0) || resolveBookingRoomCount(res);

  return {
    raw: res,
    bookingRef: String(res?.bookingRef || res?.bookingReference || '').trim() || `BKG-${res?.id ?? ''}`,
    normalizedStatus: normStatus,
    status,
    bookedOnText: formatReservationBookedDateTime(res?.bookingDate || res?.createdAt || res?.confirmedAt),
    guest: extractReservationGuestContact(res),
    checkInText: formatReservationStayDate(checkInRaw),
    checkOutText: formatReservationStayDate(checkOutRaw),
    nightsCount,
    nightsText: nightsCount !== null ? formatNumberedLabel(nightsCount, 'Night', 'Nights') : '—',
    totalRoomsCount,
    totalRoomsText: formatNumberedLabel(totalRoomsCount, 'Room', 'Rooms'),
    roomTypes,
    totalAmount: Number(res?.totalAmount ?? 0),
    payment: resolveReservationPaymentPresentation(res),
    canCheckIn: normStatus === 'CONFIRMED',
    canCheckOut: normStatus === 'CHECKED_IN',
    canMarkNoShow: normStatus === 'CONFIRMED',
    isCompleted: isCheckedOutStatus(normStatus),
    isNoShow: normStatus === 'NO_SHOW',
    isCancelled: normStatus === 'CANCELLED'
  };
}

function formatGuestSplitLabel(adults: number, children: number): string {
  const parts: string[] = [];
  if (adults > 0 || children === 0) {
    parts.push(formatNumberedLabel(adults, 'Adult', 'Adults'));
  }
  if (children > 0) {
    parts.push(formatNumberedLabel(children, 'Child', 'Children'));
  }
  return parts.join(', ');
}

function summarizeRoomAllocationOccupancy(rooms: any[] | null | undefined): {
  occupancyText: string;
  adultsSum: number;
  childrenSum: number;
  hasData: boolean;
} {
  if (!Array.isArray(rooms) || rooms.length === 0) {
    return { occupancyText: 'Unavailable', adultsSum: 0, childrenSum: 0, hasData: false };
  }

  let adultsSum = 0;
  let childrenSum = 0;
  const perRoomLabels: string[] = [];

  for (const room of rooms) {
    if (!room) continue;
    const occupants = Array.isArray(room.occupants) ? room.occupants : [];
    const adults =
      room.adults !== undefined && room.adults !== null
        ? Math.max(0, Number(room.adults) || 0)
        : occupants.filter((o: any) => normalizeStatus(o?.type) === 'ADULT').length;
    const children =
      room.children !== undefined && room.children !== null
        ? Math.max(0, Number(room.children) || 0)
        : occupants.filter((o: any) => normalizeStatus(o?.type) === 'CHILD').length;

    if (adults === 0 && children === 0 && occupants.length === 0) {
      continue;
    }
    adultsSum += adults;
    childrenSum += children;
    perRoomLabels.push(formatGuestSplitLabel(adults, children));
  }

  if (perRoomLabels.length === 0) {
    return { occupancyText: 'Unavailable', adultsSum: 0, childrenSum: 0, hasData: false };
  }

  const allSame = perRoomLabels.every((lbl) => lbl === perRoomLabels[0]);
  if (allSame) {
    return { occupancyText: perRoomLabels[0], adultsSum, childrenSum, hasData: true };
  }

  const sortedRooms = [...rooms].filter(Boolean);
  const compactLabels = sortedRooms.map((r: any, idx: number) => {
    const seq = Number(r?.roomSequence) > 0 ? Number(r.roomSequence) : idx + 1;
    const occs = Array.isArray(r?.occupants) ? r.occupants : [];
    const a = r?.adults != null ? Math.max(0, Number(r.adults) || 0) : occs.filter((o: any) => normalizeStatus(o?.type) === 'ADULT').length;
    const c = r?.children != null ? Math.max(0, Number(r.children) || 0) : occs.filter((o: any) => normalizeStatus(o?.type) === 'CHILD').length;
    return `R${seq}: ${formatGuestSplitLabel(a, c)}`;
  });

  return {
    occupancyText: compactLabels.join(' • '),
    adultsSum,
    childrenSum,
    hasData: true
  };
}

function parseHistoricalNightlyRates(nightlyRatesJson: string | null | undefined): number[] {
  if (!nightlyRatesJson || typeof nightlyRatesJson !== 'string') {
    return [];
  }
  try {
    const parsed = JSON.parse(nightlyRatesJson);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .map((item: any) => Number(item?.rate ?? item?.pricePerNight ?? item?.price))
      .filter((n: number) => Number.isFinite(n) && n >= 0);
  } catch {
    return [];
  }
}

function extractTotalGuestsSummary(
  booking: any,
  detail: HotelReservationDetailResponse | null | undefined,
  roomAdultsTotal: number,
  roomChildrenTotal: number,
  hasRoomOccupancyData: boolean
): string {
  if (hasRoomOccupancyData && (roomAdultsTotal > 0 || roomChildrenTotal > 0)) {
    const total = roomAdultsTotal + roomChildrenTotal;
    return `${total} (${formatGuestSplitLabel(roomAdultsTotal, roomChildrenTotal)})`;
  }

  const parsed = parseBookingDetailsJson(booking);
  const adultsRaw =
    parsed?.adultsCount ??
    parsed?.adults ??
    extractPipeField(booking?.detailsJson, /^Adults?\s*:\s*(\d+)$/i);
  const childrenRaw =
    parsed?.childrenCount ??
    parsed?.children ??
    extractPipeField(booking?.detailsJson, /^Child(?:ren)?\s*:\s*(\d+)$/i);

  const adultsNum = adultsRaw != null && Number.isFinite(Number(adultsRaw)) ? Math.max(0, Number(adultsRaw)) : null;
  const childrenNum = childrenRaw != null && Number.isFinite(Number(childrenRaw)) ? Math.max(0, Number(childrenRaw)) : 0;

  if (adultsNum !== null && (adultsNum > 0 || childrenNum > 0)) {
    const total = adultsNum + childrenNum;
    return `${total} (${formatGuestSplitLabel(adultsNum, childrenNum)})`;
  }

  const rawGuestTotal = parsed?.guests ?? parsed?.guestCount ?? parsed?.totalGuests ?? booking?.totalGuests ?? null;
  if (rawGuestTotal != null && Number.isFinite(Number(rawGuestTotal)) && Number(rawGuestTotal) > 0) {
    const total = Number(rawGuestTotal);
    return formatNumberedLabel(total, 'Guest', 'Guests');
  }

  return 'Unavailable';
}

function readOptionalFiniteNumber(obj: any, keys: string[]): number | null {
  if (!obj || typeof obj !== 'object') return null;
  for (const key of keys) {
    const raw = obj[key];
    if (raw !== undefined && raw !== null && raw !== '' && Number.isFinite(Number(raw))) {
      return Number(raw);
    }
  }
  return null;
}

/** Builds complete Booking Details modal view model strictly from authoritative booking/snapshot/payment payloads. */
export function buildHotelReservationModalViewModel(
  booking: any,
  detail: HotelReservationDetailResponse | null | undefined,
  paymentTx: PaymentTransactionDto | null | undefined,
  roomTypesCatalog: any[] | null | undefined,
  formatCurrencyFn: (val: number) => string
): HotelReservationModalViewModel {
  const effectiveStatus = normalizeStatus(detail?.bookingStatus || booking?.status);
  const status = resolveReservationStatusPresentation(effectiveStatus);
  const payment = resolveReservationPaymentPresentation(booking, detail, paymentTx);

  const checkInRaw = detail?.checkInDate || extractStayDateFromBooking(booking, 'in');
  const checkOutRaw = detail?.checkOutDate || extractStayDateFromBooking(booking, 'out');
  const nightsCount = computeReservationNights(checkInRaw, checkOutRaw, detail?.totalNights);

  const guest = extractReservationGuestContact(booking, detail);
  const bookedOnText = formatReservationBookedDateTime(
    detail?.bookingDate || booking?.bookingDate || booking?.createdAt || booking?.confirmedAt
  );

  let totalAdults = 0;
  let totalChildren = 0;
  let hasAnyOccupancy = false;
  let roomChargesSum = 0;
  let allAllocationsHaveAmounts = true;

  const roomRows: ReservationModalRoomRow[] = [];
  const authoritativeAllocs = Array.isArray(detail?.allocations) ? detail!.allocations : [];

  if (authoritativeAllocs.length > 0) {
    for (const alloc of authoritativeAllocs) {
      if (!alloc) continue;
      const qty = Math.max(1, Number(alloc.quantity || (Array.isArray(alloc.rooms) ? alloc.rooms.length : 1)) || 1);
      const occ = summarizeRoomAllocationOccupancy(alloc.rooms);
      if (occ.hasData) {
        hasAnyOccupancy = true;
        totalAdults += occ.adultsSum;
        totalChildren += occ.childrenSum;
      }

      const allocTotal =
        alloc.pricing?.allocationTotal !== undefined &&
        alloc.pricing?.allocationTotal !== null &&
        Number.isFinite(Number(alloc.pricing.allocationTotal))
          ? Number(alloc.pricing.allocationTotal)
          : null;

      if (allocTotal !== null) {
        roomChargesSum += allocTotal;
      } else {
        allAllocationsHaveAmounts = false;
      }

      const rates = parseHistoricalNightlyRates(alloc.pricing?.nightlyRatesJson);
      let pricePerNightText = 'Unavailable';
      if (rates.length > 0) {
        const minRate = Math.min(...rates);
        const maxRate = Math.max(...rates);
        pricePerNightText =
          minRate === maxRate
            ? formatCurrencyFn(minRate)
            : `${formatCurrencyFn(minRate)} – ${formatCurrencyFn(maxRate)}`;
      } else if (allocTotal !== null && qty > 0 && nightsCount !== null && nightsCount > 0) {
        pricePerNightText = formatCurrencyFn(Math.round((allocTotal / (qty * nightsCount)) * 100) / 100);
      }

      roomRows.push({
        roomTypeName: (alloc.roomTypeName || 'Room').trim(),
        subtitle: null,
        quantity: qty,
        quantityText: formatNumberedLabel(qty, 'Room', 'Rooms'),
        occupancyText: occ.occupancyText,
        pricePerNightText,
        amountText: allocTotal !== null ? formatCurrencyFn(allocTotal) : 'Unavailable'
      });
    }
  } else {
    allAllocationsHaveAmounts = false;
    const chips = extractReservationRoomChips(booking, roomTypesCatalog, detail);
    for (const chip of chips) {
      roomRows.push({
        roomTypeName: chip.roomTypeName,
        subtitle: null,
        quantity: chip.quantity,
        quantityText: formatNumberedLabel(chip.quantity, 'Room', 'Rooms'),
        occupancyText: 'Unavailable',
        pricePerNightText: 'Unavailable',
        amountText: 'Unavailable'
      });
    }
  }

  const authoritativeTotal = Number(detail?.totalAmount ?? booking?.totalAmount ?? 0);
  const parsedDetails = parseBookingDetailsJson(booking);

  const taxesAmount =
    readOptionalFiniteNumber(detail, ['taxesAmount', 'taxAmount', 'gstAmount', 'taxes']) ??
    readOptionalFiniteNumber(booking, ['taxesAmount', 'taxAmount', 'gstAmount']) ??
    readOptionalFiniteNumber(parsedDetails, ['taxesAmount', 'taxAmount', 'gstAmount']);

  const otherChargesAmount =
    readOptionalFiniteNumber(detail, ['otherChargesAmount', 'otherCharges', 'serviceFee', 'convenienceFee']) ??
    readOptionalFiniteNumber(booking, ['otherChargesAmount', 'otherCharges', 'serviceFee']) ??
    readOptionalFiniteNumber(parsedDetails, ['otherChargesAmount', 'otherCharges', 'serviceFee']);

  const roomChargesAmount = allAllocationsHaveAmounts
    ? roomChargesSum
    : readOptionalFiniteNumber(detail, ['roomChargesAmount', 'roomCharges', 'baseAmount', 'subtotal']) ??
      readOptionalFiniteNumber(booking, ['roomChargesAmount', 'baseAmount', 'subtotal']) ??
      authoritativeTotal;

  const taxesNote =
    taxesAmount === null && otherChargesAmount === null
      ? 'Included in room charges (where applicable)'
      : null;

  return {
    raw: booking,
    bookingRef: String(detail?.bookingRef || booking?.bookingRef || booking?.bookingReference || '').trim(),
    normalizedStatus: effectiveStatus,
    status,
    bookedOnText,
    guest,
    checkInText: formatReservationStayDate(checkInRaw),
    checkOutText: formatReservationStayDate(checkOutRaw),
    nightsCount,
    nightsText: nightsCount !== null ? formatNumberedLabel(nightsCount, 'Night', 'Nights') : 'Unavailable',
    totalGuestsText: extractTotalGuestsSummary(booking, detail, totalAdults, totalChildren, hasAnyOccupancy),
    roomRows,
    paymentSummary: {
      roomChargesAmount,
      taxesAmount,
      taxesLabel: taxesAmount !== null ? 'Taxes' : null,
      otherChargesAmount,
      otherChargesLabel: otherChargesAmount !== null ? 'Other Charges' : null,
      totalAmount: authoritativeTotal,
      taxesNote,
      payment
    },
    canCheckIn: effectiveStatus === 'CONFIRMED',
    canCheckOut: effectiveStatus === 'CHECKED_IN',
    canMarkNoShow: effectiveStatus === 'CONFIRMED'
  };
}
