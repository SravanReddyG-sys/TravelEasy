import {
  HotelDetailsResponseDto,
  HotelReservationDetailResponse,
  PaymentTransactionDto
} from '../../../../models/hotel.model';
import { DEFAULT_HOTEL_PLACEHOLDER } from '../../../../shared/utils/image-url.util';
import {
  buildHotelBookingCardViewModel,
  buildHotelBookingDetailsViewModel,
  buildHotelReviewSummary,
  HOTEL_CANCELLATION_POLICY_RULES
} from './hotel-booking-presenter.util';

function sampleHotelDetails(): HotelDetailsResponseDto {
  return {
    hotel: {
      id: 42,
      name: 'GSR Grand Premium',
      city: 'Vijayawada',
      address: 'Benz Circle, Vijayawada, Andhra Pradesh',
      starRating: 5,
      imageUrl: '/api/hotels/images/hotel_cover.jpg',
      propertyType: 'Hotel'
    },
    rooms: [
      {
        id: 101,
        hotelId: 42,
        roomTypeName: 'Deluxe Room',
        maxOccupancy: 3,
        amenities: 'Wi-Fi, AC',
        imageUrl: '/api/hotels/images/deluxe_room.jpg',
        status: 'ACTIVE',
        availableAcrossStay: true,
        availableRoomsAcrossStay: 5
      },
      {
        id: 102,
        hotelId: 42,
        roomTypeName: 'Standard Room',
        maxOccupancy: 2,
        amenities: 'Wi-Fi',
        imageUrl: '/api/hotels/images/standard_room.jpg',
        status: 'ACTIVE',
        availableAcrossStay: true,
        availableRoomsAcrossStay: 4
      }
    ],
    images: []
  };
}

function sampleSingleRoomReservation(): HotelReservationDetailResponse {
  return {
    bookingId: 901,
    bookingRef: 'HOTEL-DKG-20261111-CA74',
    bookingStatus: 'CONFIRMED',
    hotel: { hotelId: 42, hotelName: 'GSR Grand Premium' },
    checkInDate: '2026-11-11',
    checkOutDate: '2026-11-12',
    totalNights: 1,
    primaryGuest: {
      fullName: 'Sravan Reddy G',
      email: 'sravan@example.com',
      mobile: '+91 9876543210'
    },
    allocations: [
      {
        allocationId: 1,
        roomId: 101,
        roomTypeName: 'Deluxe Room',
        quantity: 1,
        pricing: { allocationTotal: 2499 },
        rooms: [
          {
            roomSequence: 1,
            adults: 2,
            children: 0,
            occupants: [
              { name: 'Sravan Reddy G', type: 'ADULT', age: 21, gender: 'MALE' },
              { name: 'Nikhil Kumar', type: 'ADULT', age: 21, gender: 'MALE' }
            ]
          }
        ]
      }
    ],
    totalAmount: 2499,
    currency: 'INR',
    paymentStatus: 'PAID',
    bookingDate: '2026-11-11T10:24:00',
    cancellation: null
  };
}

function sampleMultiRoomReservation(): HotelReservationDetailResponse {
  return {
    bookingId: 902,
    bookingRef: 'HTL-BKG-20261111-MULT',
    bookingStatus: 'CONFIRMED',
    hotel: { hotelId: 42, hotelName: 'GSR Grand Premium' },
    checkInDate: '2026-12-10',
    checkOutDate: '2026-12-13',
    totalNights: 3,
    primaryGuest: {
      fullName: 'Sravan Reddy G',
      email: 'sravan@example.com',
      mobile: '+91 9876543210'
    },
    allocations: [
      {
        allocationId: 10,
        roomId: 101,
        roomTypeName: 'Deluxe Room',
        quantity: 2,
        pricing: { allocationTotal: 15000 },
        rooms: [
          {
            roomSequence: 1,
            adults: 2,
            children: 1,
            occupants: [
              { name: 'Rohit Sharma', type: 'ADULT', age: 32, gender: 'MALE' },
              { name: 'Ananya Sharma', type: 'ADULT', age: 29, gender: 'FEMALE' },
              { name: 'Aarav Sharma', type: 'CHILD', age: 8, gender: 'MALE' }
            ]
          },
          {
            roomSequence: 2,
            adults: 1,
            children: 0,
            occupants: [
              { name: 'Kiran Rao', type: 'ADULT', age: null, gender: null }
            ]
          }
        ]
      },
      {
        allocationId: 11,
        roomId: 102,
        roomTypeName: 'Standard Room',
        quantity: 1,
        pricing: { allocationTotal: 4497 },
        rooms: [
          {
            roomSequence: 1,
            adults: 1,
            children: 1,
            occupants: [
              { name: 'Priya Nair', type: 'ADULT', age: 27, gender: 'PREFER_NOT_TO_SAY' },
              { name: 'Diya Nair', type: 'CHILD', age: 5, gender: null }
            ]
          }
        ]
      }
    ],
    totalAmount: 19497,
    currency: 'INR',
    paymentStatus: 'PAID',
    bookingDate: '2026-11-11T10:24:00',
    cancellation: null
  };
}

describe('hotel-booking-presenter.util', () => {
  it('builds a confirmed single-room card view model with authoritative fields', () => {
    const raw = {
      id: 901,
      bookingRef: 'HOTEL-DKG-20261111-CA74',
      serviceType: 'HOTEL',
      serviceId: 42,
      serviceTitle: 'GSR Grand Premium',
      checkInDate: '2026-11-11',
      checkOutDate: '2026-11-12',
      totalAmount: 2499,
      status: 'CONFIRMED',
      bookingDate: '2026-11-11T10:24:00'
    };

    const card = buildHotelBookingCardViewModel(raw, sampleHotelDetails(), sampleSingleRoomReservation());
    expect(card.hotelName).toBe('GSR Grand Premium');
    expect(card.locationText).toBe('Benz Circle, Vijayawada, Andhra Pradesh');
    expect(card.stayDatesText).toBe('Wed, Nov 11, 2026 – Thu, Nov 12, 2026');
    expect(card.nightsText).toBe('1 Night');
    expect(card.roomTypesSummary).toBe('Deluxe Room');
    expect(card.roomsCountLabel).toBe('1 Room');
    expect(card.guestsSummaryLabel).toBe('2 Adults');
    expect(card.bookingRef).toBe('HOTEL-DKG-20261111-CA74');
    expect(card.formattedTotalPaid).toBe('₹2,499');
    expect(card.statusDisplay).toBe('Confirmed');
    expect(card.isCancellable).toBe(true);
    expect(card.refundBadge).toBeNull();
  });

  it('builds multi-allocation and multi-room modal details with sequential guest tables and child NA gender', () => {
    const raw = {
      id: 902,
      bookingRef: 'HTL-BKG-20261111-MULT',
      serviceType: 'HOTEL',
      serviceId: 42,
      serviceTitle: 'GSR Grand Premium',
      totalAmount: 19497,
      status: 'CONFIRMED',
      bookingDate: '2026-11-11T10:24:00'
    };

    const vm = buildHotelBookingDetailsViewModel(raw, {
      hotelDetails: sampleHotelDetails(),
      reservationDetails: sampleMultiRoomReservation(),
      reviews: []
    });

    expect(vm.reviewSummary).toBeNull();
    expect(vm.durationText).toBe('3 Nights');
    expect(vm.rooms.length).toBe(3);
    expect(vm.rooms[0].roomSequenceOverall).toBe(1);
    expect(vm.rooms[0].roomTypeName).toBe('Deluxe Room');
    expect(vm.rooms[0].imageUrl).toContain('/api/hotels/images/deluxe_room.jpg');
    expect(vm.rooms[0].guests.length).toBe(3);
    expect(vm.rooms[0].guests[2].isChild).toBe(true);
    expect(vm.rooms[0].guests[2].genderDisplay).toBe('NA');

    expect(vm.rooms[1].roomSequenceOverall).toBe(2);
    expect(vm.rooms[1].guests[0].ageDisplay).toBe('—');
    expect(vm.rooms[1].guests[0].genderDisplay).toBe('—');

    expect(vm.rooms[2].roomSequenceOverall).toBe(3);
    expect(vm.rooms[2].roomTypeName).toBe('Standard Room');
    expect(vm.rooms[2].imageUrl).toContain('/api/hotels/images/standard_room.jpg');
    expect(vm.rooms[2].guests[0].genderDisplay).toBe('Prefer not to say');
    expect(vm.rooms[2].guests[1].genderDisplay).toBe('NA');

    expect(vm.priceLineItems.length).toBe(2);
    expect(vm.formattedTotalPaid).toBe('₹19,497');
  });

  it('computes review summary strictly from real reviews without fabrication', () => {
    expect(buildHotelReviewSummary([])).toBeNull();
    expect(buildHotelReviewSummary(null)).toBeNull();

    const summary = buildHotelReviewSummary([{ rating: 4 }, { rating: 5 }, { rating: 4 }]);
    expect(summary).not.toBeNull();
    expect(summary!.formattedRating).toBe('4.3');
    expect(summary!.reviewCount).toBe(3);
  });

  it('handles legacy hotel bookings without allocations or hotel metadata without inventing data', () => {
    const raw = {
      id: 77,
      bookingRef: 'TE-LEGACY-77',
      serviceType: 'HOTEL',
      serviceId: 999,
      serviceTitle: 'Sea Breeze Resort (Ocean Suite)',
      totalAmount: 7800,
      status: 'COMPLETED',
      bookingDate: '2026-09-01T12:00:00',
      detailsJson: 'Check-in: 2026-09-05 | Check-out: 2026-09-07'
    };

    const card = buildHotelBookingCardViewModel(raw, null, null);
    expect(card.hotelName).toBe('Sea Breeze Resort');
    expect(card.locationText).toBeNull();
    expect(card.thumbnail).toBe(DEFAULT_HOTEL_PLACEHOLDER);
    expect(card.nightsText).toBe('2 Nights');
    expect(card.isCompleted).toBe(true);
    expect(card.isCancellable).toBe(false);

    const vm = buildHotelBookingDetailsViewModel(raw, {});
    expect(vm.isLegacyWithoutAllocations).toBe(true);
    expect(vm.rooms.length).toBe(0);
    expect(vm.reviewSummary).toBeNull();
    expect(vm.refund.stateKey).toBe('NONE');
  });

  it('maps all authoritative refund states accurately without frontend math', () => {
    const baseRaw = {
      id: 500,
      bookingRef: 'HOTEL-REF-500',
      serviceType: 'HOTEL',
      serviceId: 42,
      serviceTitle: 'GSR Grand Premium',
      totalAmount: 4899,
      status: 'CANCELLED',
      cancelledAt: '2026-11-10T15:00:00',
      cancellationReason: 'Change of plans'
    };

    // PROCESSED
    const processedTx: PaymentTransactionDto = {
      status: 'REFUNDED',
      refundStatus: 'PROCESSED',
      refundAmount: 4899,
      refundId: 'rfnd_XYZ123',
      refundedAt: '2026-11-10T15:02:00'
    };
    const processedVm = buildHotelBookingDetailsViewModel(
      { ...baseRaw, refundStatus: 'PROCESSED', refundAmount: 4899, refundId: 'rfnd_XYZ123' },
      { paymentTransaction: processedTx }
    );
    expect(processedVm.refund.stateKey).toBe('PROCESSED');
    expect(processedVm.refund.formattedRefundAmount).toBe('₹4,899');
    expect(processedVm.refund.refundId).toBe('rfnd_XYZ123');

    // FAILED_RETRYABLE
    const retryCard = buildHotelBookingCardViewModel({ ...baseRaw, refundStatus: 'FAILED_RETRYABLE' });
    expect(retryCard.refundBadge?.label).toBe('Refund Pending');
    const retryVm = buildHotelBookingDetailsViewModel({ ...baseRaw, refundStatus: 'FAILED_RETRYABLE', refundAmount: 2449.5 });
    expect(retryVm.refund.stateKey).toBe('RETRY_QUEUED');
    expect(retryVm.refund.formattedRefundAmount).toBe('₹2,449.50');

    // PENDING / INITIATED
    const pendingVm = buildHotelBookingDetailsViewModel({ ...baseRaw, refundStatus: 'INITIATED', refundAmount: 4899 });
    expect(pendingVm.refund.stateKey).toBe('PENDING');

    // FAILED_FINAL
    const failedCard = buildHotelBookingCardViewModel({ ...baseRaw, refundStatus: 'FAILED_FINAL' });
    expect(failedCard.refundBadge?.label).toBe('Refund Failed');
    const failedVm = buildHotelBookingDetailsViewModel({ ...baseRaw, refundStatus: 'FAILED_FINAL' });
    expect(failedVm.refund.stateKey).toBe('FAILED');

    // NON_REFUNDABLE (0 refund)
    const zeroVm = buildHotelBookingDetailsViewModel({ ...baseRaw, refundStatus: 'NOT_APPLICABLE', refundAmount: 0 });
    expect(zeroVm.refund.stateKey).toBe('NON_REFUNDABLE');
    expect(zeroVm.refund.formattedRefundAmount).toBe('₹0');
  });

  it('exposes the four platform hotel cancellation policy tiers', () => {
    expect(HOTEL_CANCELLATION_POLICY_RULES.length).toBe(4);
    expect(HOTEL_CANCELLATION_POLICY_RULES[0].refundLabel).toBe('100% Refund');
    expect(HOTEL_CANCELLATION_POLICY_RULES[1].refundLabel).toBe('50% Refund');
    expect(HOTEL_CANCELLATION_POLICY_RULES[2].refundLabel).toBe('0% Refund');
    expect(HOTEL_CANCELLATION_POLICY_RULES[3].refundLabel).toBe('0% Refund');
  });
});
