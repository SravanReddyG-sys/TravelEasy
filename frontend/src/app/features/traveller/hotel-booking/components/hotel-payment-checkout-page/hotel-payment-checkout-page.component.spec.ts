import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HotelPaymentCheckoutPageComponent } from './hotel-payment-checkout-page.component';
import { BookingService } from '../../../../../services/booking.service';
import { HotelService } from '../../../../../services/hotel.service';
import { HotelStaySummary, PhysicalRoomAllocation } from '../../../../../models/hotel.model';
import {
  clearHotelPaymentCheckoutState,
  HOTEL_PAYMENT_SESSION_KEY,
  saveHotelPaymentCheckoutState
} from '../../../../../shared/utils/hotel-payment.util';

function sampleStaySummary(): HotelStaySummary {
  return {
    hotelId: 42,
    hotelName: 'GSR Grand Premium',
    city: 'Vijayawada',
    propertyType: 'Hotel',
    address: 'MG Road, Vijayawada',
    checkIn: '2026-12-10',
    checkOut: '2026-12-12',
    totalNights: 2,
    requestedRooms: 1,
    adults: 2,
    children: 0,
    selectedItems: [
      {
        roomTypeId: 10,
        roomTypeName: 'Deluxe Room',
        maxOccupancy: 2,
        quantity: 1,
        minNightlyPrice: 5000,
        stayTotalPricePerRoom: 10000,
        totalStayPrice: 10000
      }
    ],
    totalSelectedRooms: 1,
    estimatedTotalAmount: 10000 // Intentionally distinct from authoritative 11700
  };
}

function sampleRooms(): PhysicalRoomAllocation[] {
  return [
    {
      roomKey: '10-1',
      roomTypeId: 10,
      roomTypeName: 'Deluxe Room',
      roomSequenceOverall: 1,
      roomSequenceInType: 1,
      maxOccupancy: 2,
      nightlyPrice: 5000,
      stayTotalPricePerRoom: 10000,
      expanded: false,
      selectedAddGuestType: 'ADULT',
      occupants: [
        { id: '1', type: 'ADULT', name: 'Sravan Reddy', age: 30, gender: 'MALE' }
      ]
    }
  ];
}

describe('HotelPaymentCheckoutPageComponent', () => {
  let fixture: ComponentFixture<HotelPaymentCheckoutPageComponent>;
  let component: HotelPaymentCheckoutPageComponent;
  let bookingServiceSpy: jasmine.SpyObj<BookingService>;
  let hotelServiceSpy: jasmine.SpyObj<HotelService>;

  beforeEach(async () => {
    clearHotelPaymentCheckoutState();
    bookingServiceSpy = jasmine.createSpyObj<BookingService>('BookingService', [
      'initiateHotelReservation',
      'getHotelReservationDetails',
      'getPaymentConfig',
      'createPaymentOrder',
      'verifyPaymentSignature',
      'getPaymentByBookingRef'
    ]);
    hotelServiceSpy = jasmine.createSpyObj<HotelService>('HotelService', ['getHotelDetails']);

    bookingServiceSpy.getPaymentConfig.and.returnValue(of({ keyId: 'rzp_test_123', currency: 'INR' }));
    hotelServiceSpy.getHotelDetails.and.returnValue(
      of({
        hotel: {
          id: 42,
          name: 'GSR Grand Premium',
          city: 'Vijayawada',
          address: 'MG Road, Vijayawada',
          starRating: 5,
          amenities: 'Free Wi-Fi, Restaurant, Swimming Pool, Spa, Gym',
          imageUrl: '/api/hotels/images/hotel_42.jpg',
          propertyType: 'Hotel'
        },
        rooms: []
      })
    );

    await TestBed.configureTestingModule({
      imports: [HotelPaymentCheckoutPageComponent],
      providers: [
        { provide: BookingService, useValue: bookingServiceSpy },
        { provide: HotelService, useValue: hotelServiceSpy }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    clearHotelPaymentCheckoutState();
    delete (window as any).Razorpay;
  });

  it('initiates reservation once and displays authoritative totalAmount instead of Stage 4 estimate', () => {
    const futureExpiry = new Date(Date.now() + 14 * 60 * 1000 + 57 * 1000).toISOString();
    bookingServiceSpy.initiateHotelReservation.and.returnValue(
      of({
        bookingId: 1001,
        bookingRef: 'HTL-BKG-20261210-9K2L',
        status: 'PENDING_PAYMENT',
        totalAmount: 11700,
        currency: 'INR',
        expiresAt: futureExpiry,
        hotelId: 42,
        hotelName: 'GSR Grand Premium',
        checkInDate: '2026-12-10',
        checkOutDate: '2026-12-12',
        totalNights: 2
      })
    );

    fixture = TestBed.createComponent(HotelPaymentCheckoutPageComponent);
    component = fixture.componentInstance;
    component.staySummary = sampleStaySummary();
    component.allocatedRooms = sampleRooms();
    component.primaryGuest = { fullName: 'Sravan Reddy', email: 'sravan@example.com', mobile: '+91 9876543210' };
    component.currentUser = { id: 7, fullName: 'Sravan Reddy', email: 'sravan@example.com', role: 'ROLE_CUSTOMER' };
    component.initiationToken = 1;
    fixture.detectChanges();

    expect(bookingServiceSpy.initiateHotelReservation).toHaveBeenCalledTimes(1);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="booking-ref-value"]')?.textContent?.trim()).toBe('HTL-BKG-20261210-9K2L');
    expect(el.querySelector('[data-testid="pay-with-razorpay-btn"]')?.textContent).toContain('₹11,700');
  });

  it('completes Razorpay checkout handler and transitions to CONFIRMED only when verified=true', () => {
    const futureExpiry = new Date(Date.now() + 14 * 60 * 1000).toISOString();
    bookingServiceSpy.initiateHotelReservation.and.returnValue(
      of({
        bookingId: 1002,
        bookingRef: 'HTL-BKG-CONF-1',
        status: 'PENDING_PAYMENT',
        totalAmount: 11700,
        currency: 'INR',
        expiresAt: futureExpiry,
        hotelId: 42,
        hotelName: 'GSR Grand Premium',
        checkInDate: '2026-12-10',
        checkOutDate: '2026-12-12',
        totalNights: 2
      })
    );
    bookingServiceSpy.createPaymentOrder.and.returnValue(
      of({
        orderId: 'order_rzp_1',
        transactionRef: 'TXN-1',
        bookingRef: 'HTL-BKG-CONF-1',
        amount: 11700,
        amountInPaise: 1170000,
        currency: 'INR',
        keyId: 'rzp_test_123',
        status: 'CREATED'
      })
    );
    bookingServiceSpy.verifyPaymentSignature.and.returnValue(
      of({
        verified: true,
        message: 'Confirmed',
        bookingRef: 'HTL-BKG-CONF-1',
        transactionRef: 'TXN-1',
        paymentId: 'pay_ok_1',
        status: 'SUCCESS'
      })
    );
    bookingServiceSpy.getHotelReservationDetails.and.returnValue(
      of({
        bookingId: 1002,
        bookingRef: 'HTL-BKG-CONF-1',
        bookingStatus: 'CONFIRMED',
        hotel: { hotelId: 42, hotelName: 'GSR Grand Premium' },
        checkInDate: '2026-12-10',
        checkOutDate: '2026-12-12',
        totalNights: 2,
        totalAmount: 11700,
        currency: 'INR',
        expiresAt: futureExpiry
      })
    );

    (window as any).Razorpay = function (opts: any) {
      return {
        on() {},
        open() {
          opts.handler({
            razorpay_order_id: opts.order_id,
            razorpay_payment_id: 'pay_ok_1',
            razorpay_signature: 'sig_ok_1'
          });
        }
      };
    };

    fixture = TestBed.createComponent(HotelPaymentCheckoutPageComponent);
    component = fixture.componentInstance;
    component.staySummary = sampleStaySummary();
    component.allocatedRooms = sampleRooms();
    component.primaryGuest = { fullName: 'Sravan Reddy', email: 'sravan@example.com', mobile: '+91 9876543210' };
    component.initiationToken = 1;
    fixture.detectChanges();

    component.onPayWithRazorpay();
    fixture.detectChanges();

    expect(bookingServiceSpy.createPaymentOrder).toHaveBeenCalledWith(
      jasmine.objectContaining({ bookingRef: 'HTL-BKG-CONF-1', amount: 11700 })
    );
    expect(component.uiState).toBe('CONFIRMED');
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="payment-confirmed-state"]')).not.toBeNull();
    expect(el.querySelector('#confirmed-card-heading')?.textContent?.trim()).toBe('Booking Confirmed!');
    expect(el.querySelector('[data-testid="confirmed-status-badge"]')?.textContent?.trim()).toBe('Paid');
    expect(el.querySelector('[data-testid="confirmed-hotel-name"]')?.textContent?.trim()).toBe('GSR Grand Premium');
    expect(el.querySelector('[data-testid="confirmed-hotel-amenities"]')?.textContent).toContain('Free Wi-Fi');
    expect(hotelServiceSpy.getHotelDetails).toHaveBeenCalledTimes(1);

    const buttons = Array.from(el.querySelectorAll('.confirmed-action-row button')).map(b =>
      b.getAttribute('data-testid')
    );
    expect(buttons).toEqual(['view-my-bookings-btn', 'confirmed-book-another-btn']);
  });

  it('transitions to CONFIRMATION_PENDING when verify throws 5xx after capture rather than showing false failure', () => {
    const futureExpiry = new Date(Date.now() + 14 * 60 * 1000).toISOString();
    saveHotelPaymentCheckoutState({
      userId: 7,
      bookingRef: 'HTL-BKG-PEND-1',
      hotelId: 42,
      staySignature: '42|2026-12-10|2026-12-12',
      allocationsSignature: '',
      updatedAt: new Date().toISOString()
    });

    bookingServiceSpy.getHotelReservationDetails.and.returnValue(
      of({
        bookingId: 1003,
        bookingRef: 'HTL-BKG-PEND-1',
        bookingStatus: 'PENDING_PAYMENT',
        hotel: { hotelId: 42, hotelName: 'GSR Grand Premium' },
        checkInDate: '2026-12-10',
        checkOutDate: '2026-12-12',
        totalNights: 2,
        totalAmount: 11700,
        currency: 'INR',
        expiresAt: futureExpiry
      })
    );
    bookingServiceSpy.getPaymentByBookingRef.and.returnValue(
      of({ status: 'SUCCESS', razorpayPaymentId: 'pay_captured', transactionRef: 'TXN-CAP' })
    );

    fixture = TestBed.createComponent(HotelPaymentCheckoutPageComponent);
    component = fixture.componentInstance;
    component.currentUser = { id: 7, fullName: 'Sravan', email: 's@example.com', role: 'ROLE_CUSTOMER' };
    component.requestedHotelId = 42;
    component.initiationToken = 0; // Page reload recovery path
    fixture.detectChanges();

    expect(bookingServiceSpy.initiateHotelReservation).not.toHaveBeenCalled();
    expect(component.uiState).toBe('CONFIRMATION_PENDING');
    expect(fixture.nativeElement.querySelector('[data-testid="payment-confirmation-pending-state"]')).not.toBeNull();
    expect(sessionStorage.getItem(HOTEL_PAYMENT_SESSION_KEY)).not.toBeNull();
  });

  it('transitions to REFUND_INITIATED on verified=false with late payment expiry refund', () => {
    const futureExpiry = new Date(Date.now() + 14 * 60 * 1000).toISOString();
    bookingServiceSpy.initiateHotelReservation.and.returnValue(
      of({
        bookingId: 1004,
        bookingRef: 'HTL-BKG-RFND-1',
        status: 'PENDING_PAYMENT',
        totalAmount: 11700,
        currency: 'INR',
        expiresAt: futureExpiry,
        hotelId: 42,
        hotelName: 'GSR Grand Premium',
        checkInDate: '2026-12-10',
        checkOutDate: '2026-12-12',
        totalNights: 2
      })
    );
    bookingServiceSpy.createPaymentOrder.and.returnValue(
      of({
        orderId: 'order_late',
        transactionRef: 'TXN-LATE',
        bookingRef: 'HTL-BKG-RFND-1',
        amount: 11700,
        amountInPaise: 1170000,
        currency: 'INR',
        keyId: 'rzp_test_123',
        status: 'CREATED'
      })
    );
    bookingServiceSpy.verifyPaymentSignature.and.returnValue(
      of({
        verified: false,
        message: 'Payment captured, but reservation expired. Automated refund initiated.',
        bookingRef: 'HTL-BKG-RFND-1',
        transactionRef: 'TXN-LATE',
        paymentId: 'pay_late_1',
        status: 'REFUND_INITIATED',
        transaction: {
          status: 'REFUND_INITIATED',
          refundId: 'rfnd_late_1',
          refundStatus: 'INITIATED',
          refundAmount: 11700
        }
      })
    );
    bookingServiceSpy.getHotelReservationDetails.and.returnValue(
      throwError(() => ({ status: 404 }))
    );

    (window as any).Razorpay = function (opts: any) {
      return {
        on() {},
        open() {
          opts.handler({
            razorpay_order_id: opts.order_id,
            razorpay_payment_id: 'pay_late_1',
            razorpay_signature: 'sig_late_1'
          });
        }
      };
    };

    fixture = TestBed.createComponent(HotelPaymentCheckoutPageComponent);
    component = fixture.componentInstance;
    component.staySummary = sampleStaySummary();
    component.allocatedRooms = sampleRooms();
    component.primaryGuest = { fullName: 'Sravan Reddy', email: 'sravan@example.com', mobile: '+91 9876543210' };
    component.initiationToken = 1;
    fixture.detectChanges();

    component.onPayWithRazorpay();
    fixture.detectChanges();

    expect(component.uiState).toBe('REFUND_INITIATED');
    expect(fixture.nativeElement.querySelector('[data-testid="payment-refund-state"]')).not.toBeNull();
  });
});
