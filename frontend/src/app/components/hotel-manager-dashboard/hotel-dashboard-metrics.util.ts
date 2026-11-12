import { HotelRevenueReportResponse } from '../../services/booking.service';
import { HotelInventorySummary } from '../../services/hotel.service';

export interface KpiComparisonModel {
  currentValue: number;
  previousValue: number;
  diff: number;
  percentageChange: number | null;
  isPositive: boolean;
  isNegative: boolean;
  isNeutral: boolean;
  trendText: string;
  hasData: boolean;
}

export interface TrendDayModel {
  dateLabel: string;
  fullDate: string;
  count: number;
  revenue: number;
  heightPx: number;
  revenueHeightPx: number;
  heightPercent: number;
  revenueHeightPercent: number;
  showLabel: boolean;
}

export interface OperationalAlertsModel {
  arrivalsCount: number;
  departuresCount: number;
  activeRoomBlocksCount: number;
  lowAvailabilityRoomsCount: number;
}

const EXCLUDED_UNSUCCESSFUL_STATUSES = new Set([
  'PENDING_PAYMENT',
  'INITIATED',
  'FAILED',
  'EXPIRED'
]);

const CONFIRMED_LIFECYCLE_STATUSES = new Set([
  'CONFIRMED',
  'CHECKED_IN',
  'CHECKED_OUT',
  'COMPLETED',
  'NO_SHOW'
]);

const OCCUPIED_STAY_STATUSES = new Set([
  'CONFIRMED',
  'CHECKED_IN',
  'CHECKED_OUT',
  'COMPLETED'
]);

export function normalizeStatus(status: unknown): string {
  return typeof status === 'string' ? status.trim().toUpperCase() : '';
}

export function extractCalendarDateIso(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  const match = trimmed.match(/^(\d{4}-\d{2}-\d{2})/);
  if (match) {
    return match[1];
  }
  const parsed = new Date(trimmed);
  if (isNaN(parsed.getTime())) return null;
  const y = parsed.getFullYear();
  const m = String(parsed.getMonth() + 1).padStart(2, '0');
  const d = String(parsed.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function formatCalendarDateShort(dateStr: string): string {
  if (!dateStr) return '';
  const parts = dateStr.split('-');
  if (parts.length < 3) return dateStr;
  const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const monthIdx = parseInt(parts[1], 10) - 1;
  const day = parseInt(parts[2], 10);
  if (monthIdx < 0 || monthIdx > 11 || isNaN(day)) return dateStr;
  return `${monthNames[monthIdx]} ${day}`;
}

export function toUtcDayNumber(dateIso: string): number | null {
  const parts = dateIso.split('-');
  if (parts.length !== 3) return null;
  const year = Number(parts[0]);
  const month = Number(parts[1]);
  const day = Number(parts[2]);
  if (!Number.isInteger(year) || !Number.isInteger(month) || !Number.isInteger(day)) {
    return null;
  }
  return Math.floor(Date.UTC(year, month - 1, day) / 86_400_000);
}

export function fromUtcDayNumber(dayNum: number): string {
  const d = new Date(dayNum * 86_400_000);
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function parseBookingDetailsJson(booking: any): any {
  if (!booking?.detailsJson) return null;
  if (typeof booking.detailsJson === 'object') return booking.detailsJson;
  if (typeof booking.detailsJson !== 'string') return null;
  const raw = booking.detailsJson.trim();
  if (!raw || (!raw.startsWith('{') && !raw.startsWith('['))) {
    return null;
  }
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function extractStayDateFromBooking(booking: any, type: 'in' | 'out'): string | null {
  if (!booking) return null;
  const direct = type === 'in'
    ? (booking.checkInDate ?? booking.checkIn)
    : (booking.checkOutDate ?? booking.checkOut);
  const directIso = extractCalendarDateIso(direct);
  if (directIso) return directIso;

  const details = parseBookingDetailsJson(booking);
  if (details) {
    const nested = type === 'in'
      ? (details.checkInDate ?? details.checkIn)
      : (details.checkOutDate ?? details.checkOut);
    return extractCalendarDateIso(nested);
  }
  return null;
}

export function resolveBookingRoomCount(booking: any): number {
  if (!booking) return 1;
  if (Array.isArray(booking.allocations) && booking.allocations.length > 0) {
    const sum = booking.allocations.reduce((acc: number, item: any) => {
      const q = Number(item?.quantity);
      return acc + (Number.isFinite(q) && q > 0 ? q : 0);
    }, 0);
    if (sum > 0) return sum;
  }

  const directTotal = Number(booking.totalRoomsBooked);
  if (Number.isFinite(directTotal) && directTotal > 0) {
    return directTotal;
  }

  const details = parseBookingDetailsJson(booking);
  if (details) {
    if (Array.isArray(details.allocations) && details.allocations.length > 0) {
      const sum = details.allocations.reduce((acc: number, item: any) => {
        const q = Number(item?.quantity);
        return acc + (Number.isFinite(q) && q > 0 ? q : 0);
      }, 0);
      if (sum > 0) return sum;
    }
    const detailCount = Number(details.roomCount ?? details.roomsBooked ?? details.rooms ?? details.count);
    if (Number.isFinite(detailCount) && detailCount > 0) {
      return detailCount;
    }
  }
  return 1;
}

/**
 * Matches strictly on serviceType='HOTEL' and serviceId === hotelId,
 * never via fragile hotel-name substring checks, and deduplicates by ID/bookingRef.
 */
export function filterAndDedupeHotelBookings(bookings: any[] | null | undefined, hotelId: number | null | undefined): any[] {
  if (!Array.isArray(bookings) || hotelId == null || !Number.isFinite(Number(hotelId))) {
    return [];
  }
  const targetHotelId = Number(hotelId);
  const seen = new Set<string>();
  const result: any[] = [];

  for (const b of bookings) {
    if (!b) continue;
    const svcType = normalizeStatus(b.serviceType);
    if (svcType !== 'HOTEL') continue;
    if (b.serviceId == null || Number(b.serviceId) !== targetHotelId) continue;

    const dedupeKey = b.id != null
      ? `id:${b.id}`
      : (typeof b.bookingRef === 'string' && b.bookingRef.trim() ? `ref:${b.bookingRef.trim()}` : null);
    if (dedupeKey) {
      if (seen.has(dedupeKey)) continue;
      seen.add(dedupeKey);
    }
    result.push(b);
  }
  return result;
}

/**
 * Returns true if the booking reached confirmed/completed status (including confirmed bookings
 * that were later cancelled), while excluding holds/pending/failed/expired/abandoned bookings.
 */
export function wasBookingConfirmed(booking: any): boolean {
  if (!booking) return false;
  const status = normalizeStatus(booking.status);
  if (!status || EXCLUDED_UNSUCCESSFUL_STATUSES.has(status)) {
    return false;
  }
  if (CONFIRMED_LIFECYCLE_STATUSES.has(status)) {
    return true;
  }
  if (status === 'CANCELLED') {
    const refundStatus = normalizeStatus(booking.refundStatus);
    const hasRefundSignal =
      Boolean(booking.refundId) ||
      (refundStatus !== '' && refundStatus !== 'NOT_APPLICABLE') ||
      (Number(booking.refundAmount) || 0) > 0;
    return Boolean(booking.confirmedAt) || hasRefundSignal;
  }
  return false;
}

export function filterBookingsCreatedInPeriod(bookings: any[], start: string, end: string): any[] {
  if (!Array.isArray(bookings) || !start || !end) return [];
  return bookings.filter(b => {
    if (!wasBookingConfirmed(b)) return false;
    const createdIso = extractCalendarDateIso(b.bookingDate) || extractCalendarDateIso(b.confirmedAt);
    return Boolean(createdIso && createdIso >= start && createdIso <= end);
  });
}

export function countOverlappingNights(
  checkInIso: string,
  checkOutIso: string,
  periodStartIso: string,
  periodEndIso: string
): number {
  const stayStart = toUtcDayNumber(checkInIso);
  const stayEndExclusive = toUtcDayNumber(checkOutIso);
  const periodStart = toUtcDayNumber(periodStartIso);
  const periodEndInclusive = toUtcDayNumber(periodEndIso);

  if (
    stayStart === null ||
    stayEndExclusive === null ||
    periodStart === null ||
    periodEndInclusive === null ||
    stayEndExclusive <= stayStart ||
    periodEndInclusive < periodStart
  ) {
    return 0;
  }

  const periodEndExclusive = periodEndInclusive + 1;
  const overlapStart = Math.max(stayStart, periodStart);
  const overlapEnd = Math.min(stayEndExclusive, periodEndExclusive);
  return Math.max(0, overlapEnd - overlapStart);
}

/**
 * Sums confirmed room-nights using [checkIn, checkOut) half-open date overlap
 * multiplied by exact room allocation count. Excludes incomplete/expired holds,
 * NO_SHOW, and released/cancelled bookings.
 */
export function calculatePeriodRoomNights(bookings: any[], start: string, end: string): number {
  if (!Array.isArray(bookings) || !start || !end) return 0;
  let totalNights = 0;

  for (const b of bookings) {
    if (!b) continue;
    const status = normalizeStatus(b.status);
    if (!OCCUPIED_STAY_STATUSES.has(status)) {
      if (status === 'CANCELLED' && b.inventoryReleased === false) {
        // Explicit unreleased inventory edge case
      } else {
        continue;
      }
    }

    const checkInIso = extractStayDateFromBooking(b, 'in');
    const checkOutIso = extractStayDateFromBooking(b, 'out');
    if (!checkInIso || !checkOutIso) continue;

    const overlap = countOverlappingNights(checkInIso, checkOutIso, start, end);
    if (overlap > 0) {
      totalNights += overlap * resolveBookingRoomCount(b);
    }
  }
  return totalNights;
}

/**
 * Selects up to `limit` future/today confirmed check-ins sorted chronologically ascending,
 * independent of the dashboard reporting date range selector.
 */
export function selectUpcomingReservations(bookings: any[], todayIso: string, limit: number = 5): any[] {
  if (!Array.isArray(bookings) || !todayIso || limit <= 0) return [];
  return bookings
    .filter(b => {
      if (!b || normalizeStatus(b.status) !== 'CONFIRMED') return false;
      const checkIn = extractStayDateFromBooking(b, 'in');
      return Boolean(checkIn && checkIn >= todayIso);
    })
    .sort((a, b) => {
      const inA = extractStayDateFromBooking(a, 'in') || '';
      const inB = extractStayDateFromBooking(b, 'in') || '';
      if (inA !== inB) return inA.localeCompare(inB);
      const bookA = typeof a.bookingDate === 'string' ? a.bookingDate : '';
      const bookB = typeof b.bookingDate === 'string' ? b.bookingDate : '';
      return bookA.localeCompare(bookB);
    })
    .slice(0, limit);
}

export function computeOperationalAlerts(
  bookings: any[],
  start: string,
  end: string,
  currInv: HotelInventorySummary | null | undefined,
  roomTypes: any[],
  roomBlocks: any[]
): OperationalAlertsModel {
  let arrivalsCount = 0;
  let departuresCount = 0;

  for (const b of (bookings || [])) {
    if (!b) continue;
    const status = normalizeStatus(b.status);
    if (status === 'CONFIRMED') {
      const checkInIso = extractStayDateFromBooking(b, 'in');
      if (checkInIso && checkInIso >= start && checkInIso <= end) {
        arrivalsCount++;
      }
    } else if (status === 'CHECKED_IN') {
      const checkOutIso = extractStayDateFromBooking(b, 'out');
      if (checkOutIso && checkOutIso >= start && checkOutIso <= end) {
        departuresCount++;
      }
    }
  }

  let activeRoomBlocksCount = 0;
  if (currInv && typeof currInv.activeRoomBlocksCount === 'number') {
    activeRoomBlocksCount = currInv.activeRoomBlocksCount;
  } else {
    const archivedRoomIds = new Set(
      (roomTypes || [])
        .filter(rt => rt && normalizeStatus(rt.status) === 'ARCHIVED' && rt.id != null)
        .map(rt => Number(rt.id))
    );
    const seenBlocks = new Set<string>();
    for (const block of (roomBlocks || [])) {
      if (!block || normalizeStatus(block.status) !== 'ACTIVE') continue;
      if (block.roomId != null && archivedRoomIds.has(Number(block.roomId))) continue;
      const key = block.id != null ? `id:${block.id}` : `${block.roomId}|${block.startDate}|${block.endDate}`;
      if (seenBlocks.has(key)) continue;
      seenBlocks.add(key);

      const bStart = extractCalendarDateIso(block.startDate);
      const bEnd = extractCalendarDateIso(block.endDate);
      if (bStart && bEnd && bStart <= end && bEnd > start) {
        activeRoomBlocksCount++;
      }
    }
  }

  let lowAvailabilityRoomsCount = 0;
  if (currInv && typeof currInv.lowAvailabilityRoomsCount === 'number') {
    lowAvailabilityRoomsCount = currInv.lowAvailabilityRoomsCount;
  } else {
    lowAvailabilityRoomsCount = (roomTypes || []).filter(rt => {
      if (!rt || normalizeStatus(rt.status) === 'ARCHIVED') return false;
      return rt.availableRooms !== undefined && rt.availableRooms !== null && Number(rt.availableRooms) <= 2;
    }).length;
  }

  return {
    arrivalsCount,
    departuresCount,
    activeRoomBlocksCount,
    lowAvailabilityRoomsCount
  };
}

export function buildTrendsChartData(
  bookings: any[],
  start: string,
  end: string,
  currRev: HotelRevenueReportResponse | null | undefined,
  bookingsUnavailable: boolean = false,
  revenueUnavailable: boolean = false
): {
  trendDays: TrendDayModel[];
  maxChartValue: number;
  maxRevenueChartValue: number;
} {
  const startDay = toUtcDayNumber(start);
  const endDay = toUtcDayNumber(end);
  if (startDay === null || endDay === null || endDay < startDay) {
    return { trendDays: [], maxChartValue: 40, maxRevenueChartValue: 50000 };
  }

  const rawItems: { isoStr: string; dateLabel: string; count: number; revenue: number }[] = [];
  let maxCount = 0;
  let maxRev = 0;

  const revByDate = new Map<string, number>();
  if (!revenueUnavailable && currRev?.dailyBreakdown) {
    for (const d of currRev.dailyBreakdown) {
      if (d?.date) {
        revByDate.set(d.date, Number(d.netRevenue) || 0);
      }
    }
  }

  for (let dayNum = startDay; dayNum <= endDay; dayNum++) {
    const isoStr = fromUtcDayNumber(dayNum);
    const dateLabel = formatCalendarDateShort(isoStr);

    const dayCount = bookingsUnavailable
      ? 0
      : filterBookingsCreatedInPeriod(bookings, isoStr, isoStr).length;

    const dayRev = revenueUnavailable ? 0 : (revByDate.get(isoStr) ?? 0);

    if (dayCount > maxCount) maxCount = dayCount;
    if (dayRev > maxRev) maxRev = dayRev;

    rawItems.push({ isoStr, dateLabel, count: dayCount, revenue: dayRev });
  }

  const totalDays = rawItems.length;
  let step = 1;
  if (totalDays <= 8) {
    step = 1;
  } else if (totalDays <= 16) {
    step = 2;
  } else if (totalDays <= 24) {
    step = 3;
  } else if (totalDays <= 35) {
    step = 5;
  } else {
    step = Math.ceil(totalDays / 6);
  }

  const shouldShow = new Array<boolean>(totalDays).fill(false);
  if (totalDays <= 8) {
    shouldShow.fill(true);
  } else {
    let lastShown = -999;
    for (let i = 0; i < totalDays; i++) {
      const isStepMilestone = i % step === 0;
      const isLast = i === totalDays - 1;
      if (isStepMilestone && totalDays - 1 - i >= Math.max(2, Math.floor(step * 0.7))) {
        shouldShow[i] = true;
        lastShown = i;
      }
      if (isLast && i - lastShown >= 2) {
        shouldShow[i] = true;
      }
    }
  }

  const maxChartValue = Math.max(40, Math.ceil((maxCount + 5) / 10) * 10);
  const maxRevenueChartValue = Math.max(50000, Math.ceil((maxRev + 5000) / 10000) * 10000);

  const trendDays: TrendDayModel[] = rawItems.map((item, idx) => ({
    dateLabel: item.dateLabel,
    fullDate: item.isoStr,
    count: item.count,
    revenue: item.revenue,
    heightPercent: item.count > 0
      ? Math.min(100, Math.max(2, Math.round((item.count / maxChartValue) * 100)))
      : 0,
    revenueHeightPercent: item.revenue > 0
      ? Math.min(100, Math.max(2, Math.round((item.revenue / maxRevenueChartValue) * 100)))
      : 0,
    heightPx: Math.max(4, Math.round((item.count / maxChartValue) * 200)),
    revenueHeightPx: item.revenue > 0
      ? Math.max(4, Math.round((item.revenue / maxRevenueChartValue) * 200))
      : 4,
    showLabel: shouldShow[idx]
  }));

  return { trendDays, maxChartValue, maxRevenueChartValue };
}

export function buildKpiComparisonMetric(
  curr: number,
  prev: number,
  isPercentPoints: boolean = false,
  hasHistoricalData: boolean = true,
  unavailableLabel: string = 'No prior data'
): KpiComparisonModel {
  const diff = Math.round((curr - prev) * 10) / 10;
  if (!hasHistoricalData) {
    return {
      currentValue: curr,
      previousValue: prev,
      diff: 0,
      percentageChange: null,
      isPositive: false,
      isNegative: false,
      isNeutral: true,
      trendText: unavailableLabel,
      hasData: false
    };
  }

  if (isPercentPoints) {
    const isPos = diff > 0;
    const isNeg = diff < 0;
    const isNeut = diff === 0;
    const sign = diff > 0 ? '+' : '';
    return {
      currentValue: curr,
      previousValue: prev,
      diff,
      percentageChange: diff,
      isPositive: isPos,
      isNegative: isNeg,
      isNeutral: isNeut,
      trendText: `${isPos ? '↑ ' : isNeg ? '↓ ' : ''}${sign}${diff} pp`,
      hasData: true
    };
  }

  if (prev === 0) {
    if (curr === 0) {
      return {
        currentValue: curr,
        previousValue: 0,
        diff: 0,
        percentageChange: 0,
        isPositive: false,
        isNegative: false,
        isNeutral: true,
        trendText: '0%',
        hasData: true
      };
    }
    return {
      currentValue: curr,
      previousValue: 0,
      diff: curr,
      percentageChange: 100,
      isPositive: curr > 0,
      isNegative: curr < 0,
      isNeutral: false,
      trendText: curr > 0 ? `↑ +${curr} (new)` : `↓ ${curr}`,
      hasData: true
    };
  }

  const pct = Math.round(((curr - prev) / Math.abs(prev)) * 100);
  const isPos = curr > prev;
  const isNeg = curr < prev;
  const isNeut = curr === prev;
  const sign = pct > 0 ? '+' : '';
  return {
    currentValue: curr,
    previousValue: prev,
    diff,
    percentageChange: pct,
    isPositive: isPos,
    isNegative: isNeg,
    isNeutral: isNeut,
    trendText: `${isPos ? '↑ ' : isNeg ? '↓ ' : ''}${sign}${pct}%`,
    hasData: true
  };
}
