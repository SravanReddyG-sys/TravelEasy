import {
  buildHotelReservationCardViewModel,
  buildHotelReservationModalViewModel,
  computeReservationKpis,
  computeReservationNights,
  computeReservationStatusCounts,
  extractReservationGuestContact,
  filterReservationsList,
  formatReservationBookedDateTime,
  formatReservationStayDate,
  resolveReservationPaymentPresentation
} from './hotel-reservations-presenter.util';

declare function describe(name: string, fn: () => void): void;
declare function it(name: string, fn: () => void): void;
declare function expect<T>(actual: T): {
  toBe(expected: unknown): void;
  toEqual(expected: unknown): void;
};

describe('Hotel Reservations Presenter Utilities', () => {
  const formatInr = (val: number): string =>
    `₹${Number(val || 0).toLocaleString('en-IN')}`;

  it('computes KPI summary cards and synchronized status tab counts accurately', () => {
    const bookings = [
      { bookingRef: 'TEH-1', status: 'CONFIRMED' },
      { bookingRef: 'TEH-2', status: 'CONFIRMED' },
      { bookingRef: 'TEH-3', status: 'CHECKED_IN' },
      { bookingRef: 'TEH-4', status: 'CHECKED_OUT' },
      { bookingRef: 'TEH-5', status: 'COMPLETED' },
      { bookingRef: 'TEH-6', status: 'NO_SHOW' },
      { bookingRef: 'TEH-7', status: 'CANCELLED' },
      { bookingRef: 'TEH-8', status: 'PENDING_PAYMENT' }
    ];

    const kpis = computeReservationKpis(bookings);
    expect(kpis).toEqual({
      totalReservations: 8,
      confirmedCount: 2,
      currentlyStayingCount: 1,
      completedCount: 2
    });

    const counts = computeReservationStatusCounts(bookings);
    expect(counts).toEqual({
      ALL: 8,
      CONFIRMED: 2,
      CHECKED_IN: 1,
      CHECKED_OUT: 2,
      NO_SHOW: 1,
      CANCELLED: 1
    });
  });

  it('filters reservations consistently by search query (bookingRef or guest name) and status filter', () => {
    const bookings = [
      { bookingRef: 'TEH-2026-1042', customerName: 'Arjun Kumar', status: 'CONFIRMED' },
      { bookingRef: 'TEH-2026-1039', customerName: 'Priya Sharma', status: 'CHECKED_IN' },
      { bookingRef: 'TEH-2026-1015', customerName: 'Arjun Nair', status: 'CHECKED_OUT' },
      { bookingRef: 'TEH-2026-1008', customerName: 'Deepa Menon', status: 'NO_SHOW' }
    ];

    expect(filterReservationsList(bookings, 'ALL', 'arjun').map((b) => b.bookingRef)).toEqual([
      'TEH-2026-1042',
      'TEH-2026-1015'
    ]);

    expect(filterReservationsList(bookings, 'CONFIRMED', 'arjun').map((b) => b.bookingRef)).toEqual([
      'TEH-2026-1042'
    ]);

    expect(filterReservationsList(bookings, 'ALL', '1039').map((b) => b.bookingRef)).toEqual([
      'TEH-2026-1039'
    ]);

    expect(filterReservationsList(bookings, 'NO_SHOW', '   ').map((b) => b.bookingRef)).toEqual([
      'TEH-2026-1008'
    ]);
  });

  it('formats stay dates without timezone day-shift and calculates night duration accurately', () => {
    expect(formatReservationStayDate('2026-11-18')).toBe('18 Nov 2026');
    expect(formatReservationStayDate('2026-11-21')).toBe('21 Nov 2026');
    expect(computeReservationNights('2026-11-18', '2026-11-21')).toBe(3);

    const bookedFormatted = formatReservationBookedDateTime('2026-11-16T10:24:00');
    expect(bookedFormatted).toBe('16 Nov 2026, 10:24 AM');
  });

  it('builds listing card view models with multi-room allocations and strict status action flags', () => {
    const roomCatalog = [
      { id: 101, roomTypeName: 'Deluxe Double Room', pricePerNight: 4000 },
      { id: 102, roomTypeName: 'Premium Suite', pricePerNight: 6000 }
    ];

    const multiRoomConfirmed = {
      bookingRef: 'TEH-2026-1042',
      status: 'CONFIRMED',
      customerName: 'Arjun Kumar',
      customerEmail: 'arjun@example.com',
      bookingDate: '2026-11-16T10:24:00',
      checkInDate: '2026-11-18',
      checkOutDate: '2026-11-21',
      totalAmount: 42000,
      allocations: [
        { roomId: 101, quantity: 2 },
        { roomId: 102, quantity: 1 }
      ],
      totalRoomsBooked: 3,
      roomTypeSummary: 'Deluxe Double Room (x2), Premium Suite (x1)'
    };

    const vm = buildHotelReservationCardViewModel(multiRoomConfirmed, roomCatalog);
    expect(vm.bookingRef).toBe('TEH-2026-1042');
    expect(vm.status.label).toBe('Confirmed');
    expect(vm.nightsText).toBe('3 Nights');
    expect(vm.totalRoomsText).toBe('3 Rooms');
    expect(vm.roomTypes).toEqual([
      { roomTypeName: 'Deluxe Double Room', quantity: 2 },
      { roomTypeName: 'Premium Suite', quantity: 1 }
    ]);
    expect(vm.payment.label).toBe('Paid');
    expect(vm.canCheckIn).toBe(true);
    expect(vm.canMarkNoShow).toBe(true);
    expect(vm.canCheckOut).toBe(false);

    const checkedInVm = buildHotelReservationCardViewModel({ ...multiRoomConfirmed, status: 'CHECKED_IN' }, roomCatalog);
    expect(checkedInVm.canCheckIn).toBe(false);
    expect(checkedInVm.canMarkNoShow).toBe(false);
    expect(checkedInVm.canCheckOut).toBe(true);

    const cancelledVm = buildHotelReservationCardViewModel(
      { bookingRef: 'TEH-9', status: 'CANCELLED', refundStatus: 'PROCESSED', confirmedAt: '2026-11-01T09:00:00', totalAmount: 5000 },
      roomCatalog
    );
    expect(cancelledVm.canCheckIn).toBe(false);
    expect(cancelledVm.canCheckOut).toBe(false);
    expect(cancelledVm.canMarkNoShow).toBe(false);
    expect(cancelledVm.isCancelled).toBe(true);
    expect(cancelledVm.payment.label).toBe('Refunded');
  });

  it('resolves payment status distinctly from booking status including Pending, Failed, Refunded, and Unpaid', () => {
    expect(
      resolveReservationPaymentPresentation(
        { status: 'CONFIRMED' },
        null,
        { bookingRef: 'B1', status: 'SUCCESS', refundStatus: 'NONE', amount: 4000 }
      ).label
    ).toBe('Paid');

    expect(
      resolveReservationPaymentPresentation(
        { status: 'CONFIRMED' },
        null,
        { bookingRef: 'B2', status: 'SUCCESS', refundStatus: 'PROCESSED', amount: 4000 }
      ).label
    ).toBe('Refunded');

    expect(
      resolveReservationPaymentPresentation(
        { status: 'CANCELLED' },
        null,
        { bookingRef: 'B3', status: 'SUCCESS', refundStatus: 'INITIATED', amount: 4000 }
      ).label
    ).toBe('Refund Pending');

    expect(
      resolveReservationPaymentPresentation(
        { status: 'FAILED' },
        null,
        { bookingRef: 'B4', status: 'FAILED', amount: 4000 }
      ).label
    ).toBe('Payment Failed');

    expect(
      resolveReservationPaymentPresentation({ status: 'PENDING_PAYMENT' }, null, null).label
    ).toBe('Pending');

    expect(
      resolveReservationPaymentPresentation({ status: 'CANCELLED', refundStatus: 'NOT_APPLICABLE' }, null, null).label
    ).toBe('Unpaid');
  });

  it('never invents missing phone, occupancy, or taxes in card and modal view models', () => {
    const sparseBooking = {
      bookingRef: 'TEH-SPARSE-1',
      status: 'CONFIRMED',
      customerName: 'Neha Verma',
      customerEmail: 'neha@example.com',
      checkInDate: '2026-11-20',
      checkOutDate: '2026-11-21',
      totalAmount: 4500,
      serviceTitle: 'Grand Hotel - Superior Room',
      detailsJson: ''
    };

    const contact = extractReservationGuestContact(sparseBooking);
    expect(contact).toEqual({
      name: 'Neha Verma',
      email: 'neha@example.com',
      phone: ''
    });

    const modalVm = buildHotelReservationModalViewModel(sparseBooking, null, null, [], formatInr);
    expect(modalVm.guest.phone).toBe('');
    expect(modalVm.totalGuestsText).toBe('Unavailable');
    expect(modalVm.roomRows[0].occupancyText).toBe('Unavailable');
    expect(modalVm.roomRows[0].pricePerNightText).toBe('Unavailable');
    expect(modalVm.paymentSummary.taxesAmount).toBe(null);
    expect(modalVm.paymentSummary.otherChargesAmount).toBe(null);
    expect(modalVm.paymentSummary.taxesNote).toBe('Included in room charges (where applicable)');
    expect(modalVm.paymentSummary.totalAmount).toBe(4500);
  });

  it('uses authoritative historical allocation snapshot pricing and per-room occupancy instead of live catalog rates', () => {
    const staleOrChangedLiveCatalog = [
      { id: 101, roomTypeName: 'Deluxe Double Room', pricePerNight: 9999 },
      { id: 102, roomTypeName: 'Premium Suite', pricePerNight: 12999 }
    ];

    const booking = {
      bookingRef: 'TEH-2026-1042',
      status: 'CONFIRMED',
      customerName: 'Arjun Kumar',
      customerEmail: 'arjun@example.com',
      checkInDate: '2026-11-18',
      checkOutDate: '2026-11-21',
      bookingDate: '2026-11-16T10:24:00',
      totalAmount: 42000
    };

    const detail = {
      bookingId: 1042,
      bookingRef: 'TEH-2026-1042',
      bookingStatus: 'CONFIRMED',
      hotelId: 10,
      hotelName: 'GSR Grand Premium',
      bookingDate: '2026-11-16T10:24:00',
      checkInDate: '2026-11-18',
      checkOutDate: '2026-11-21',
      totalNights: 3,
      totalRooms: 3,
      totalAmount: 42000,
      currency: 'INR',
      paymentStatus: 'SUCCESS',
      primaryGuest: {
        fullName: 'Arjun Kumar',
        email: 'arjun@example.com',
        mobile: '+91 98765 43210'
      },
      allocations: [
        {
          allocationId: 1,
          roomId: 101,
          roomTypeName: 'Deluxe Double Room',
          quantity: 2,
          rooms: [
            { roomSequence: 1, adults: 2, children: 0, occupants: [] },
            { roomSequence: 2, adults: 2, children: 0, occupants: [] }
          ],
          pricing: {
            allocationTotal: 24000,
            nightlyRatesJson: JSON.stringify([{ rate: 4000 }, { rate: 4000 }, { rate: 4000 }])
          }
        },
        {
          allocationId: 2,
          roomId: 102,
          roomTypeName: 'Premium Suite',
          quantity: 1,
          rooms: [{ roomSequence: 1, adults: 2, children: 2, occupants: [] }],
          pricing: {
            allocationTotal: 18000,
            nightlyRatesJson: JSON.stringify([{ rate: 6000 }, { rate: 6000 }, { rate: 6000 }])
          }
        }
      ],
      cancellation: { refundStatus: 'NONE' }
    };

    const modalVm = buildHotelReservationModalViewModel(
      booking,
      detail,
      { bookingRef: 'TEH-2026-1042', status: 'SUCCESS', amount: 42000 },
      staleOrChangedLiveCatalog,
      formatInr
    );

    expect(modalVm.guest).toEqual({
      name: 'Arjun Kumar',
      email: 'arjun@example.com',
      phone: '+91 98765 43210'
    });
    expect(modalVm.totalGuestsText).toBe('8 (6 Adults, 2 Children)');
    expect(modalVm.roomRows.length).toBe(2);
    expect(modalVm.roomRows[0].pricePerNightText).toBe('₹4,000');
    expect(modalVm.roomRows[0].occupancyText).toBe('2 Adults');
    expect(modalVm.roomRows[0].amountText).toBe('₹24,000');
    expect(modalVm.roomRows[1].pricePerNightText).toBe('₹6,000');
    expect(modalVm.roomRows[1].occupancyText).toBe('2 Adults, 2 Children');
    expect(modalVm.roomRows[1].amountText).toBe('₹18,000');
    expect(modalVm.paymentSummary.roomChargesAmount).toBe(42000);
    expect(modalVm.paymentSummary.totalAmount).toBe(42000);
    expect(modalVm.paymentSummary.payment.label).toBe('Paid');
  });
});
