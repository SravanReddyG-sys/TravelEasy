import { HotelActiveReservation, HotelStaySummary, PhysicalRoomAllocation } from '../../models/hotel.model';
import {
  HOTEL_PAYMENT_SESSION_KEY,
  buildAllocationsSignature,
  buildConfirmedHotelDisplayInfo,
  buildHotelReservationRequest,
  buildStayContextSignature,
  clearHotelPaymentCheckoutState,
  computeRemainingSeconds,
  extractApiErrorMessage,
  extractConfirmedStayBreakdown,
  formatAuthoritativeAmount,
  formatCountdownMmSs,
  fromDetailResponse,
  fromInitiationResponse,
  loadHotelPaymentCheckoutState,
  parseBackendExpiresAtMs,
  pickConfirmedAmenities,
  reconcileCheckoutState,
  saveHotelPaymentCheckoutState
} from './hotel-payment.util';

function createMemoryStorage(): Storage {
  const store: Record<string, string> = {};
  return {
    get length() {
      return Object.keys(store).length;
    },
    clear() {
      Object.keys(store).forEach(k => delete store[k]);
    },
    getItem(key: string) {
      return Object.prototype.hasOwnProperty.call(store, key) ? store[key] : null;
    },
    key(index: number) {
      return Object.keys(store)[index] ?? null;
    },
    removeItem(key: string) {
      delete store[key];
    },
    setItem(key: string, value: string) {
      store[key] = String(value);
    }
  };
}

function createSampleStaySummary(): HotelStaySummary {
  return {
    hotelId: 42,
    hotelName: 'GSR Grand Premium',
    city: 'Vijayawada',
    propertyType: 'Hotel',
    address: 'MG Road, Vijayawada',
    checkIn: '2026-12-10',
    checkOut: '2026-12-13',
    totalNights: 3,
    requestedRooms: 2,
    adults: 2,
    children: 1,
    selectedItems: [
      {
        roomTypeId: 101,
        roomTypeName: 'Deluxe Suite',
        maxOccupancy: 3,
        quantity: 2,
        minNightlyPrice: 4000,
        stayTotalPricePerRoom: 12000,
        totalStayPrice: 24000
      }
    ],
    totalSelectedRooms: 2,
    estimatedTotalAmount: 24000
  };
}

function createSampleRooms(): PhysicalRoomAllocation[] {
  return [
    {
      roomKey: '101-1',
      roomTypeId: 101,
      roomTypeName: 'Deluxe Suite',
      roomSequenceOverall: 1,
      roomSequenceInType: 1,
      maxOccupancy: 3,
      nightlyPrice: 4000,
      stayTotalPricePerRoom: 12000,
      expanded: false,
      selectedAddGuestType: 'ADULT',
      occupants: [
        { id: '1', type: 'ADULT', name: 'Sravan Reddy', age: 30, gender: 'MALE' },
        { id: '2', type: 'CHILD', name: 'Riya Reddy', age: 7, gender: null }
      ]
    },
    {
      roomKey: '101-2',
      roomTypeId: 101,
      roomTypeName: 'Deluxe Suite',
      roomSequenceOverall: 2,
      roomSequenceInType: 2,
      maxOccupancy: 3,
      nightlyPrice: 4000,
      stayTotalPricePerRoom: 12000,
      expanded: false,
      selectedAddGuestType: 'ADULT',
      occupants: [
        { id: '3', type: 'ADULT', name: 'Ananya Rao', age: 28, gender: 'FEMALE' }
      ]
    }
  ];
}

function createSampleActiveReservation(overrides: Partial<HotelActiveReservation> = {}): HotelActiveReservation {
  return {
    bookingId: 9001,
    bookingRef: 'HTL-BKG-20261210-ABCD',
    bookingStatus: 'PENDING_PAYMENT',
    totalAmount: 11700,
    currency: 'INR',
    expiresAt: '2099-12-10T15:15:00',
    hotelId: 42,
    hotelName: 'GSR Grand Premium',
    checkInDate: '2026-12-10',
    checkOutDate: '2026-12-13',
    totalNights: 3,
    paymentStatus: 'PENDING',
    primaryGuest: {
      fullName: 'Sravan Reddy',
      email: 'sravan@example.com',
      mobile: '+91 9876543210'
    },
    allocationsSummary: [{ roomId: 101, roomTypeName: 'Deluxe Suite', quantity: 2 }],
    cancellation: null,
    ...overrides
  };
}

describe('hotel-payment.util', () => {
  it('builds HotelReservationInitiationRequest accurately using per-type roomSequence and omits estimated totals', () => {
    const req = buildHotelReservationRequest(
      createSampleStaySummary(),
      createSampleRooms(),
      {
        fullName: '  Sravan Reddy  ',
        email: ' sravan@example.com ',
        mobile: ' +91 9876543210 '
      },
      { id: 17, fullName: 'Account Owner', email: 'owner@example.com' },
      'HTL-BKG-20261210-1234'
    );

    expect(req.bookingRef).toBe('HTL-BKG-20261210-1234');
    expect(req.customerId).toBe(17);
    expect(req.primaryGuest).toEqual({
      fullName: 'Sravan Reddy',
      email: 'sravan@example.com',
      mobile: '+91 9876543210'
    });
    expect(req.checkInDate).toBe('2026-12-10');
    expect(req.checkOutDate).toBe('2026-12-13');
    expect(req.allocations.length).toBe(1);
    expect(req.allocations[0].roomId).toBe(101);
    expect(req.allocations[0].quantity).toBe(2);
    expect(req.allocations[0].rooms[0].roomSequence).toBe(1);
    expect(req.allocations[0].rooms[1].roomSequence).toBe(2);
    expect(req.allocations[0].rooms[0].occupants[1].gender).toBeNull();
    expect((req as any).totalAmount).toBeUndefined();
    expect((req as any).estimatedTotalAmount).toBeUndefined();
  });

  it('normalizes both initiation and detail DTOs into HotelActiveReservation', () => {
    const initRes = fromInitiationResponse({
      bookingId: 1,
      bookingRef: 'HTL-BKG-1',
      status: 'pending_payment',
      totalAmount: 11700,
      currency: 'INR',
      expiresAt: '2026-12-10T12:15:00',
      hotelId: 42,
      hotelName: 'GSR Grand Premium',
      checkInDate: '2026-12-10',
      checkOutDate: '2026-12-13',
      totalNights: 3
    });
    expect(initRes.bookingStatus).toBe('PENDING_PAYMENT');
    expect(initRes.totalAmount).toBe(11700);

    const detailRes = fromDetailResponse({
      bookingId: 1,
      bookingRef: 'HTL-BKG-1',
      bookingStatus: 'CONFIRMED',
      hotel: { hotelId: 42, hotelName: 'GSR Grand Premium' },
      checkInDate: '2026-12-10',
      checkOutDate: '2026-12-13',
      totalNights: 3,
      totalAmount: 11700,
      currency: 'INR',
      expiresAt: '2026-12-10T12:15:00'
    });
    expect(detailRes.bookingStatus).toBe('CONFIRMED');
    expect(detailRes.hotelId).toBe(42);
  });

  it('parses LocalDateTime and ISO timestamps without fabricating fallback timers and formats MM:SS cleanly', () => {
    const naiveMs = parseBackendExpiresAtMs('2026-12-10T14:15:00.123456');
    expect(naiveMs).not.toBeNull();
    const sameWallClock = new Date(2026, 11, 10, 14, 15, 0, 123).getTime();
    expect(naiveMs).toBe(sameWallClock);

    const remainingSec = computeRemainingSeconds(naiveMs!, naiveMs! - 897000);
    expect(remainingSec).toBe(897);
    expect(formatCountdownMmSs(remainingSec)).toBe('14:57');
    expect(formatCountdownMmSs(computeRemainingSeconds(naiveMs!, naiveMs! + 100))).toBe('00:00');
    expect(parseBackendExpiresAtMs(null)).toBeNull();
  });

  it('formats authoritative INR amounts accurately', () => {
    expect(formatAuthoritativeAmount(11700, 'INR')).toBe('₹11,700');
    expect(formatAuthoritativeAmount(11700.5, 'INR')).toBe('₹11,700.50');
    expect(formatAuthoritativeAmount(undefined, 'INR')).toBe('—');
  });

  it('never equates payment SUCCESS alone with CONFIRMED when booking is still PENDING_PAYMENT', () => {
    const res = reconcileCheckoutState({
      reservation: createSampleActiveReservation({ bookingStatus: 'PENDING_PAYMENT' }),
      paymentTxn: {
        status: 'SUCCESS',
        razorpayPaymentId: 'pay_123',
        transactionRef: 'TXN-123'
      }
    });
    expect(res.uiState).toBe('CONFIRMATION_PENDING');
    expect(res.statusBadgeLabel).toBe('Confirmation Pending');
  });

  it('transitions to CONFIRMED only when verification verified=true or bookingStatus=CONFIRMED', () => {
    const res = reconcileCheckoutState({
      reservation: createSampleActiveReservation({ bookingStatus: 'CONFIRMED' }),
      verification: {
        verified: true,
        message: 'Confirmed',
        bookingRef: 'HTL-BKG-20261210-ABCD',
        paymentId: 'pay_ok',
        transactionRef: 'TXN-OK',
        status: 'SUCCESS'
      }
    });
    expect(res.uiState).toBe('CONFIRMED');
    expect(res.paymentRef).toBe('pay_ok');
  });

  it('transitions late captured payment on expired reservation to REFUND_INITIATED', () => {
    const res = reconcileCheckoutState({
      reservation: createSampleActiveReservation({ bookingStatus: 'EXPIRED' }),
      verification: {
        verified: false,
        message: 'Payment captured, but reservation expired. Automated refund initiated.',
        bookingRef: 'HTL-BKG-20261210-ABCD',
        paymentId: 'pay_late',
        transactionRef: 'TXN-LATE',
        status: 'REFUND_INITIATED',
        transaction: {
          status: 'REFUND_INITIATED',
          refundId: 'rfnd_999',
          refundStatus: 'INITIATED',
          refundAmount: 11700
        }
      }
    });
    expect(res.uiState).toBe('REFUND_INITIATED');
    expect(res.refundId).toBe('rfnd_999');
    expect(res.refundAmount).toBe(11700);
  });

  it('transitions unpaid lapsed hold to EXPIRED', () => {
    const pastExpires = '2020-01-01T10:00:00';
    const res = reconcileCheckoutState({
      reservation: createSampleActiveReservation({
        bookingStatus: 'PENDING_PAYMENT',
        expiresAt: pastExpires
      }),
      nowMs: new Date(2026, 10, 11, 12, 0, 0).getTime()
    });
    expect(res.uiState).toBe('EXPIRED');
    expect(res.statusBadgeLabel).toBe('Expired');
  });

  it('keeps active reservation retryable in PAYMENT_FAILED state when payment failed before expiry', () => {
    const res = reconcileCheckoutState({
      reservation: createSampleActiveReservation({ bookingStatus: 'PENDING_PAYMENT' }),
      lastPaymentError: 'Card authentication failed.',
      nowMs: new Date(2026, 10, 11, 12, 0, 0).getTime()
    });
    expect(res.uiState).toBe('PAYMENT_FAILED');
    expect(res.message).toContain('Card authentication failed');
  });

  it('distinguishes transient 5xx/network errors vs expired API messages', () => {
    expect(extractApiErrorMessage({ status: 503, error: { message: 'Service down' } }, 'Fallback').isTransient5xxOrNetwork).toBe(true);
    expect(extractApiErrorMessage({ status: 400, error: { message: 'Reservation has expired' } }, 'Fallback').isExpiredError).toBe(true);
  });

  it('isolates sessionStorage recovery state by userId, hotelId, and allocationsSignature', () => {
    const storage = createMemoryStorage();
    const stay = createSampleStaySummary();
    const rooms = createSampleRooms();
    const allocSig = buildAllocationsSignature(stay, rooms);

    saveHotelPaymentCheckoutState(
      {
        userId: 10,
        bookingRef: 'HTL-BKG-TEST-1',
        hotelId: 42,
        staySignature: buildStayContextSignature(42, '2026-12-10', '2026-12-13'),
        allocationsSignature: allocSig,
        lastKnownOrderId: 'order_abc',
        updatedAt: '2026-11-11T10:00:00Z'
      },
      storage
    );

    expect(loadHotelPaymentCheckoutState(10, 42, allocSig, storage)?.bookingRef).toBe('HTL-BKG-TEST-1');
    expect(loadHotelPaymentCheckoutState(99, 42, allocSig, storage)).toBeNull();
    expect(loadHotelPaymentCheckoutState(10, 88, allocSig, storage)).toBeNull();
    expect(loadHotelPaymentCheckoutState(10, 42, 'different-sig', storage)).toBeNull();

    clearHotelPaymentCheckoutState(storage);
    expect(storage.getItem(HOTEL_PAYMENT_SESSION_KEY)).toBeNull();
  });

  it('derives authoritative guest counts, room summary, and hotel metadata cleanly without fabricating missing fields', () => {
    const detailedRes = fromDetailResponse({
      bookingId: 505,
      bookingRef: 'HTL-BKG-20261111-BQFE',
      bookingStatus: 'CONFIRMED',
      hotel: { hotelId: 1131, hotelName: 'GSR Grand Premium' },
      checkInDate: '2026-11-11',
      checkOutDate: '2026-11-12',
      totalNights: 1,
      totalAmount: 11498,
      currency: 'INR',
      allocations: [
        {
          allocationId: 1,
          roomId: 1478,
          roomTypeName: 'Standard',
          quantity: 1,
          rooms: [
            {
              roomSequence: 1,
              adults: 1,
              children: 1,
              occupants: []
            }
          ]
        },
        {
          allocationId: 2,
          roomId: 1479,
          roomTypeName: 'Deluxe Twin',
          quantity: 1,
          rooms: [
            {
              roomSequence: 1,
              adults: 1,
              children: 0,
              occupants: []
            }
          ]
        }
      ]
    });

    const stay = extractConfirmedStayBreakdown(detailedRes, null);
    expect(stay.totalRooms).toBe(2);
    expect(stay.roomsCountLabel).toBe('2 Rooms');
    expect(stay.roomTypesLabel).toBe('1 × Standard, 1 × Deluxe Twin');
    expect(stay.adults).toBe(2);
    expect(stay.children).toBe(1);
    expect(stay.guestsLabel).toBe('2 Adults, 1 Child');

    const hotelInfo = buildConfirmedHotelDisplayInfo({
      hotelId: 1131,
      hotelName: 'GSR Grand Premium',
      hotelDetails: {
        hotel: {
          id: 1131,
          name: 'GSR Grand Premium',
          city: 'Vijayawada',
          address: 'MG Road, Vijayawada',
          starRating: 5,
          amenities: 'Wi-Fi, Restaurant, Swimming Pool, Spa, Gym',
          imageUrl: '/api/hotels/images/cover.jpg',
          propertyType: 'Hotel'
        },
        rooms: []
      },
      staySummary: null
    });

    expect(hotelInfo.locationLine).toBe('MG Road, Vijayawada');
    expect(hotelInfo.starRating).toBe(5);
    expect(hotelInfo.reviewCount).toBeNull();
    expect(hotelInfo.distanceLabel).toBeNull();
    expect(hotelInfo.amenities.length).toBe(4);
    expect(hotelInfo.amenities[0].label).toBe('Wi-Fi');
    expect(pickConfirmedAmenities(null).length).toBe(0);
  });
});
