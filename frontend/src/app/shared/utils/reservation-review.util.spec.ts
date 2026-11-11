import { HotelStaySummary, PhysicalRoomAllocation } from '../../models/hotel.model';
import {
  BACKEND_EMAIL_REGEX,
  BACKEND_MOBILE_REGEX,
  clearReservationReviewState,
  computeReservationSummaryMetrics,
  createInitialPrimaryGuestForm,
  loadReservationReviewState,
  parsePhoneIntoDialCodeAndNumber,
  saveReservationReviewState,
  toPersistedRoomsSnapshot,
  validateGuestAllocationsForReview,
  validatePrimaryGuestEmail,
  validatePrimaryGuestForm,
  validatePrimaryGuestFullName,
  validatePrimaryGuestMobile
} from './reservation-review.util';

function createSampleStaySummary(): HotelStaySummary {
  return {
    hotelId: 101,
    hotelName: 'The Lakeside Grand Hotel',
    city: 'Hyderabad',
    propertyType: 'Hotel',
    address: 'Banjara Hills, Hyderabad',
    hotelImageUrl: '/uploads/hotels/lakeside.jpg',
    checkIn: '2026-12-10',
    checkOut: '2026-12-13',
    totalNights: 3,
    requestedRooms: 2,
    adults: 2,
    children: 1,
    selectedItems: [
      {
        roomTypeId: 11,
        roomTypeName: 'Deluxe Room',
        maxOccupancy: 3,
        quantity: 1,
        minNightlyPrice: 6499,
        stayTotalPricePerRoom: 19497,
        totalStayPrice: 19497,
        imageUrl: '/uploads/rooms/deluxe.jpg'
      },
      {
        roomTypeId: 12,
        roomTypeName: 'Standard Room',
        maxOccupancy: 2,
        quantity: 1,
        minNightlyPrice: 4899,
        stayTotalPricePerRoom: 14697,
        totalStayPrice: 14697,
        imageUrl: '/uploads/rooms/standard.jpg'
      }
    ],
    totalSelectedRooms: 2,
    estimatedTotalAmount: 34194
  };
}

function createSampleAllocatedRooms(): PhysicalRoomAllocation[] {
  return [
    {
      roomKey: '11-1',
      roomTypeId: 11,
      roomTypeName: 'Deluxe Room',
      roomSequenceOverall: 1,
      roomSequenceInType: 1,
      maxOccupancy: 3,
      nightlyPrice: 6499,
      stayTotalPricePerRoom: 19497,
      expanded: false,
      selectedAddGuestType: 'ADULT',
      occupants: [
        { id: 'g-1', type: 'ADULT', name: 'Rohit Sharma', age: 32, gender: 'MALE' },
        { id: 'g-2', type: 'CHILD', name: 'Aarav Sharma', age: 8, gender: null }
      ]
    },
    {
      roomKey: '12-1',
      roomTypeId: 12,
      roomTypeName: 'Standard Room',
      roomSequenceOverall: 2,
      roomSequenceInType: 1,
      maxOccupancy: 2,
      nightlyPrice: 4899,
      stayTotalPricePerRoom: 14697,
      expanded: false,
      selectedAddGuestType: 'ADULT',
      occupants: [
        { id: 'g-3', type: 'ADULT', name: 'Priya Sharma', age: 29, gender: 'FEMALE' }
      ]
    }
  ];
}

function createMemoryStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear: () => map.clear(),
    getItem: (key: string) => (map.has(key) ? map.get(key)! : null),
    key: (index: number) => Array.from(map.keys())[index] ?? null,
    removeItem: (key: string) => {
      map.delete(key);
    },
    setItem: (key: string, value: string) => {
      map.set(key, value);
    }
  };
}

describe('reservation-review.util (WP-HB-UI-04)', () => {
  it('parses Indian and international phone strings cleanly into dial code and national digits', () => {
    expect(parsePhoneIntoDialCodeAndNumber('+919876543210')).toEqual({
      dialCode: '+91',
      nationalNumber: '9876543210'
    });
    expect(parsePhoneIntoDialCodeAndNumber('+91 98765 43210')).toEqual({
      dialCode: '+91',
      nationalNumber: '9876543210'
    });
    expect(parsePhoneIntoDialCodeAndNumber('+44 7911123456')).toEqual({
      dialCode: '+44',
      nationalNumber: '7911123456'
    });
    expect(parsePhoneIntoDialCodeAndNumber('+1 4155550132')).toEqual({
      dialCode: '+1',
      nationalNumber: '4155550132'
    });
    expect(parsePhoneIntoDialCodeAndNumber('9876543210')).toEqual({
      dialCode: '+91',
      nationalNumber: '9876543210'
    });
    expect(parsePhoneIntoDialCodeAndNumber('')).toEqual({
      dialCode: '+91',
      nationalNumber: ''
    });
  });

  it('validates primary guest full name against whitespace, length and invalid symbol inputs', () => {
    expect(validatePrimaryGuestFullName('Sravan Reddy G')).toBeUndefined();
    expect(validatePrimaryGuestFullName("Dr. A. O'Connor")).toBeUndefined();
    expect(validatePrimaryGuestFullName('')).toBeDefined();
    expect(validatePrimaryGuestFullName('   \t ')).toBeDefined();
    expect(validatePrimaryGuestFullName('123456')).toBeDefined();
    expect(validatePrimaryGuestFullName('A'.repeat(256))).toBeDefined();
  });

  it('validates email addresses consistently with backend rules', () => {
    expect(validatePrimaryGuestEmail('sravan.reddy@example.com')).toBeUndefined();
    expect(validatePrimaryGuestEmail('travel.user+1@domain.co.in')).toBeUndefined();
    expect(validatePrimaryGuestEmail('')).toBeDefined();
    expect(validatePrimaryGuestEmail('invalid-email')).toBeDefined();
    expect(validatePrimaryGuestEmail('user@domain')).toBeDefined();
    expect(validatePrimaryGuestEmail('user..name@domain.com')).toBeDefined();
    expect(BACKEND_EMAIL_REGEX.test('sravan.reddy@example.com')).toBeTrue();
  });

  it('validates country-specific mobile numbers and emits backend-compatible mobile strings', () => {
    const inOk = validatePrimaryGuestMobile('+91', '98765 43210');
    expect(inOk.formattedMobile).toBe('+91 9876543210');
    expect(inOk.nationalNumberError).toBeUndefined();
    expect(BACKEND_MOBILE_REGEX.test(inOk.formattedMobile!)).toBeTrue();

    const inBadPrefix = validatePrimaryGuestMobile('+91', '1234567890');
    expect(inBadPrefix.nationalNumberError).toBeDefined();

    const inAlpha = validatePrimaryGuestMobile('+91', '98765abc10');
    expect(inAlpha.nationalNumberError).toBeDefined();

    const ukOk = validatePrimaryGuestMobile('+44', '7911123456');
    expect(ukOk.formattedMobile).toBe('+44 7911123456');
    expect(BACKEND_MOBILE_REGEX.test(ukOk.formattedMobile!)).toBeTrue();

    const sgOk = validatePrimaryGuestMobile('+65', '91234567');
    expect(sgOk.formattedMobile).toBe('+65 91234567');

    const res = validatePrimaryGuestForm({
      fullName: '  Sravan Reddy G ',
      email: ' sravan.reddy@example.com ',
      dialCode: '+91',
      nationalNumber: '98765 43210'
    });
    expect(res.valid).toBeTrue();
    expect(res.payload).toEqual({
      fullName: 'Sravan Reddy G',
      email: 'sravan.reddy@example.com',
      mobile: '+91 9876543210'
    });
  });

  it('derives summary counts strictly from allocated physical rooms without recalculating rates', () => {
    const summary = createSampleStaySummary();
    const rooms = createSampleAllocatedRooms();

    const metrics = computeReservationSummaryMetrics(summary, rooms);
    expect(metrics.pricingConsistent).toBeTrue();
    expect(metrics.estimatedTotalAmount).toBe(34194);
    expect(metrics.roomTypeRows.length).toBe(2);
    expect(metrics.totalRooms).toBe(2);
    expect(metrics.totalAdults).toBe(2);
    expect(metrics.totalChildren).toBe(1);
    expect(metrics.totalGuests).toBe(3);

    const check = validateGuestAllocationsForReview(summary, rooms);
    expect(check.valid).toBeTrue();
  });

  it('flags corrupted pricing or invalid guest allocations before payment handoff', () => {
    const summary = createSampleStaySummary();
    const rooms = createSampleAllocatedRooms();

    const tamperedSummary: HotelStaySummary = { ...summary, estimatedTotalAmount: 999999 };
    expect(validateGuestAllocationsForReview(tamperedSummary, rooms).valid).toBeFalse();

    const childWithGender: PhysicalRoomAllocation[] = [
      rooms[0],
      {
        ...rooms[1],
        occupants: [{ id: 'c-bad', type: 'CHILD', name: 'Kid', age: 8, gender: 'MALE' }]
      }
    ];
    expect(validateGuestAllocationsForReview(summary, childWithGender).valid).toBeFalse();
  });

  it('persists and restores review state matching stay context and user identity', () => {
    const storage = createMemoryStorage();
    const summary = createSampleStaySummary();
    const rooms = createSampleAllocatedRooms();
    const metrics = computeReservationSummaryMetrics(summary, rooms);
    const form = createInitialPrimaryGuestForm({
      userFullName: 'Edited Contact',
      userEmail: 'edited@example.com',
      userPhone: '+44 7911123456'
    });

    saveReservationReviewState(
      {
        userId: 7,
        staySummary: summary,
        rooms: toPersistedRoomsSnapshot(rooms),
        primaryGuest: form,
        primaryGuestPayload: {
          fullName: form.fullName,
          email: form.email,
          mobile: '+44 7911123456'
        },
        summaryMetrics: metrics,
        updatedAt: '2026-12-01T10:00:00Z'
      },
      storage
    );

    const restored = loadReservationReviewState(summary, storage, 7);
    expect(restored).not.toBeNull();
    expect(restored?.primaryGuest.dialCode).toBe('+44');
    expect(restored?.primaryGuest.nationalNumber).toBe('7911123456');

    expect(loadReservationReviewState(summary, storage, 999)).toBeNull();
    expect(loadReservationReviewState({ ...summary, hotelId: 202 }, storage, 7)).toBeNull();

    clearReservationReviewState(storage);
    expect(loadReservationReviewState(summary, storage, 7)).toBeNull();
  });
});
