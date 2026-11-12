import {
  buildKpiComparisonMetric,
  buildTrendsChartData,
  calculatePeriodRoomNights,
  computeOperationalAlerts,
  filterAndDedupeHotelBookings,
  filterBookingsCreatedInPeriod,
  resolveBookingRoomCount,
  selectUpcomingReservations
} from './hotel-dashboard-metrics.util';

declare function describe(name: string, fn: () => void): void;
declare function it(name: string, fn: () => void): void;
declare function expect<T>(actual: T): {
  toBe(expected: unknown): void;
  toEqual(expected: unknown): void;
};

describe('Hotel Dashboard Metrics Utilities', () => {
  it('filters strictly by serviceType=HOTEL and exact serviceId without name-matching collisions or duplicates', () => {
    const raw = [
      { id: 1, bookingRef: 'HTL-1', serviceType: 'HOTEL', serviceId: 10, serviceTitle: 'Grand Palace' },
      { id: 1, bookingRef: 'HTL-1', serviceType: 'HOTEL', serviceId: 10, serviceTitle: 'Grand Palace' },
      { id: 2, bookingRef: 'HTL-2', serviceType: 'HOTEL', serviceId: 11, serviceTitle: 'Grand Palace' },
      { id: 3, bookingRef: 'BUS-3', serviceType: 'BUS', serviceId: 10, serviceTitle: 'Grand Palace Express' }
    ];

    const filtered = filterAndDedupeHotelBookings(raw, 10);
    expect(filtered.length).toBe(1);
    expect(filtered[0].bookingRef).toBe('HTL-1');
  });

  it('excludes PENDING_PAYMENT, INITIATED, FAILED, EXPIRED, and unconfirmed CANCELLED while retaining confirmed+cancelled bookings in period count', () => {
    const bookings = [
      { id: 1, status: 'CONFIRMED', bookingDate: '2026-11-05T10:00:00' },
      { id: 2, status: 'CHECKED_IN', bookingDate: '2026-11-06T09:00:00' },
      { id: 3, status: 'CHECKED_OUT', bookingDate: '2026-11-06T12:00:00' },
      { id: 4, status: 'NO_SHOW', bookingDate: '2026-11-06T15:00:00' },
      { id: 5, status: 'CANCELLED', confirmedAt: '2026-11-05T11:00:00', refundStatus: 'PROCESSED', refundAmount: 2000, bookingDate: '2026-11-05T11:00:00' },
      { id: 6, status: 'PENDING_PAYMENT', bookingDate: '2026-11-06T11:00:00' },
      { id: 7, status: 'INITIATED', bookingDate: '2026-11-06T11:00:00' },
      { id: 8, status: 'FAILED', bookingDate: '2026-11-06T11:00:00' },
      { id: 9, status: 'EXPIRED', bookingDate: '2026-11-06T11:00:00' },
      { id: 10, status: 'CANCELLED', bookingDate: '2026-11-06T11:00:00', refundStatus: 'NOT_APPLICABLE' }
    ];

    const inPeriod = filterBookingsCreatedInPeriod(bookings, '2026-11-05', '2026-11-07');
    expect(inPeriod.map(b => b.id)).toEqual([1, 2, 3, 4, 5]);
  });

  it('calculates room nights using [checkIn, checkOut) overlap times multi-allocation room counts and excludes NO_SHOW and cancelled bookings', () => {
    const multiAllocBooking = {
      id: 101,
      status: 'CONFIRMED',
      checkInDate: '2026-11-10',
      checkOutDate: '2026-11-14',
      allocations: [
        { roomId: 1, quantity: 2 },
        { roomId: 2, quantity: 1 }
      ]
    };
    expect(resolveBookingRoomCount(multiAllocBooking)).toBe(3);

    const bookings = [
      multiAllocBooking, // Nov 10-12 overlap = 3 nights * 3 rooms = 9
      { id: 102, status: 'CHECKED_IN', checkInDate: '2026-11-12', checkOutDate: '2026-11-15', totalRoomsBooked: 2 }, // Nov 12 overlap = 1 night * 2 = 2
      { id: 103, status: 'NO_SHOW', checkInDate: '2026-11-11', checkOutDate: '2026-11-13', totalRoomsBooked: 2 }, // Excluded
      { id: 104, status: 'CANCELLED', refundStatus: 'PROCESSED', checkInDate: '2026-11-11', checkOutDate: '2026-11-13', totalRoomsBooked: 4 }, // Released/cancelled excluded
      { id: 105, status: 'PENDING_PAYMENT', checkInDate: '2026-11-11', checkOutDate: '2026-11-13', totalRoomsBooked: 5 } // Excluded
    ];

    const nights = calculatePeriodRoomNights(bookings, '2026-11-10', '2026-11-12');
    expect(nights).toBe(11);
  });

  it('selects upcoming confirmed reservations independently of reporting period sorted ascending and capped at 5', () => {
    const bookings = [
      { id: 1, bookingRef: 'PAST-CONF', status: 'CONFIRMED', checkInDate: '2026-11-09' },
      { id: 2, bookingRef: 'INHOUSE', status: 'CHECKED_IN', checkInDate: '2026-11-15' },
      { id: 3, bookingRef: 'PENDING', status: 'PENDING_PAYMENT', checkInDate: '2026-11-15' },
      { id: 4, bookingRef: 'CANCELLED', status: 'CANCELLED', checkInDate: '2026-11-15' },
      { id: 5, bookingRef: 'FUT-6', status: 'CONFIRMED', checkInDate: '2026-11-28' },
      { id: 6, bookingRef: 'FUT-2', status: 'CONFIRMED', checkInDate: '2026-11-14' },
      { id: 7, bookingRef: 'FUT-1', status: 'CONFIRMED', checkInDate: '2026-11-12' },
      { id: 8, bookingRef: 'FUT-4', status: 'CONFIRMED', checkInDate: '2026-11-20' },
      { id: 9, bookingRef: 'FUT-3', status: 'CONFIRMED', checkInDate: '2026-11-18' },
      { id: 10, bookingRef: 'FUT-5', status: 'CONFIRMED', checkInDate: '2026-11-25' }
    ];

    const upcoming = selectUpcomingReservations(bookings, '2026-11-12', 5);
    expect(upcoming.map(b => b.bookingRef)).toEqual(['FUT-1', 'FUT-2', 'FUT-3', 'FUT-4', 'FUT-5']);
  });

  it('uses authoritative date-wise lowAvailabilityRoomsCount (>2, exhausted included) and overlapping non-archived blocks in operational alerts', () => {
    const bookings = [
      { status: 'CONFIRMED', checkInDate: '2026-11-10', checkOutDate: '2026-11-12' },
      { status: 'PENDING_PAYMENT', checkInDate: '2026-11-10', checkOutDate: '2026-11-12' },
      { status: 'CHECKED_IN', checkInDate: '2026-11-08', checkOutDate: '2026-11-11' },
      { status: 'CHECKED_OUT', checkInDate: '2026-11-08', checkOutDate: '2026-11-11' }
    ];

    const alerts = computeOperationalAlerts(
      bookings,
      '2026-11-10',
      '2026-11-12',
      {
        hotelId: 10,
        startDate: '2026-11-10',
        endDate: '2026-11-12',
        totalDays: 3,
        totalPhysicalRoomNights: 30,
        occupiedRoomNights: 5,
        heldRoomNights: 0,
        blockedRoomNights: 3,
        applicableRoomNightCapacity: 27,
        availableRoomNights: 22,
        occupancyRate: 18.5,
        activeRoomBlocksCount: 2,
        lowAvailabilityRoomsCount: 4,
        exhaustedRoomsCount: 1
      },
      [],
      []
    );

    expect(alerts.arrivalsCount).toBe(1);
    expect(alerts.departuresCount).toBe(1);
    expect(alerts.activeRoomBlocksCount).toBe(2);
    expect(alerts.lowAvailabilityRoomsCount).toBe(4);
  });

  it('never fabricates trend revenue from bookings when revenue service is unavailable and computes positive delta from negative prior base', () => {
    const bookings = [
      { id: 1, status: 'CONFIRMED', bookingDate: '2026-11-10T10:00:00', confirmedAt: '2026-11-10T10:05:00', totalAmount: 9000 }
    ];
    const chartWhenRevUnavailable = buildTrendsChartData(bookings, '2026-11-10', '2026-11-11', null, false, true);
    expect(chartWhenRevUnavailable.trendDays.length).toBe(2);
    expect(chartWhenRevUnavailable.trendDays[0].count).toBe(1);
    expect(chartWhenRevUnavailable.trendDays[0].revenue).toBe(0);

    const revComparisonFromNegative = buildKpiComparisonMetric(5000, -5000);
    expect(revComparisonFromNegative.isPositive).toBe(true);
    expect(revComparisonFromNegative.percentageChange).toBe(200);
  });
});
