import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HotelBookingDetailsModalComponent } from './hotel-booking-details-modal.component';
import { buildHotelBookingCardViewModel } from '../../utils/hotel-booking-presenter.util';

describe('HotelBookingDetailsModalComponent', () => {
  let fixture: ComponentFixture<HotelBookingDetailsModalComponent>;
  let component: HotelBookingDetailsModalComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HotelBookingDetailsModalComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(HotelBookingDetailsModalComponent);
    component = fixture.componentInstance;
  });

  it('renders 3 tabs, switches tab panels cleanly, and preserves tab selection across async enrichment updates', () => {
    const rawBooking = {
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

    component.showDetailsModal = true;
    component.bookingCard = buildHotelBookingCardViewModel(rawBooking);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const tabs = el.querySelectorAll('[role="tab"]');
    expect(tabs.length).toBe(3);
    expect(tabs[0].textContent).toContain('Booking Details');
    expect(tabs[1].textContent).toContain('Payment & Refund');
    expect(tabs[2].textContent).toContain('Cancellation Policy');
    expect(el.textContent).not.toContain('Booking History');

    // Switch to Payment & Refund tab
    (tabs[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(component.activeTab).toBe('payment');

    // Simulate async payment details arrival for same open booking
    component.paymentTransaction = {
      status: 'SUCCESS',
      amount: 2499,
      currency: 'INR',
      razorpayPaymentId: 'pay_TEST123'
    };
    fixture.detectChanges();
    expect(component.activeTab).toBe('payment');
    expect(el.textContent).toContain('pay_TEST123');
    expect(el.textContent).toContain('No Refund Initiated');
  });

  it('renders multi-room guest allocation tables with NA gender for children', () => {
    const rawBooking = {
      id: 902,
      bookingRef: 'HTL-MULTI-1',
      serviceType: 'HOTEL',
      serviceId: 42,
      serviceTitle: 'GSR Grand Premium',
      totalAmount: 15000,
      status: 'CONFIRMED'
    };

    component.showDetailsModal = true;
    component.bookingCard = buildHotelBookingCardViewModel(rawBooking);
    component.reservationDetails = {
      bookingId: 902,
      bookingRef: 'HTL-MULTI-1',
      bookingStatus: 'CONFIRMED',
      hotel: { hotelId: 42, hotelName: 'GSR Grand Premium' },
      checkInDate: '2026-11-11',
      checkOutDate: '2026-11-13',
      totalNights: 2,
      allocations: [
        {
          allocationId: 1,
          roomId: 101,
          roomTypeName: 'Deluxe Room',
          quantity: 1,
          rooms: [
            {
              roomSequence: 1,
              adults: 1,
              children: 1,
              occupants: [
                { name: 'Rohit Sharma', type: 'ADULT', age: 32, gender: 'MALE' },
                { name: 'Aarav Sharma', type: 'CHILD', age: 8, gender: null }
              ]
            }
          ]
        }
      ],
      totalAmount: 15000,
      currency: 'INR'
    };
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Guest Details (Room 1)');
    expect(el.textContent).toContain('Rohit Sharma');
    expect(el.textContent).toContain('Aarav Sharma');
    expect(el.textContent).toContain('NA');
    expect(el.querySelector('.summary-hotel-rating')).toBeNull();
  });
});
