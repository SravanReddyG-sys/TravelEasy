import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HotelBookingCardComponent } from './hotel-booking-card.component';
import { buildHotelBookingCardViewModel } from '../../utils/hotel-booking-presenter.util';

describe('HotelBookingCardComponent', () => {
  let fixture: ComponentFixture<HotelBookingCardComponent>;
  let component: HotelBookingCardComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HotelBookingCardComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(HotelBookingCardComponent);
    component = fixture.componentInstance;
  });

  it('renders hotel card layout with View Details and Cancel Booking for confirmed bookings', () => {
    component.booking = buildHotelBookingCardViewModel(
      {
        id: 1,
        bookingRef: 'HOTEL-DKG-20261111-CA74',
        serviceType: 'HOTEL',
        serviceId: 42,
        serviceTitle: 'GSR Grand Premium',
        checkInDate: '2026-11-11',
        checkOutDate: '2026-11-12',
        totalAmount: 2499,
        status: 'CONFIRMED'
      },
      {
        hotel: {
          id: 42,
          name: 'GSR Grand Premium',
          city: 'Vijayawada',
          address: 'Benz Circle, Vijayawada, Andhra Pradesh',
          imageUrl: '/api/hotels/images/cover.jpg'
        },
        rooms: []
      },
      null
    );
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('GSR Grand Premium');
    expect(el.textContent).toContain('Benz Circle, Vijayawada, Andhra Pradesh');
    expect(el.textContent).toContain('HOTEL-DKG-20261111-CA74');
    expect(el.textContent).toContain('₹2,499');
    expect(el.querySelector('.btn-hotel-view-details')).not.toBeNull();
    expect(el.querySelector('.btn-hotel-cancel')).not.toBeNull();
    expect(el.querySelector('.btn-hotel-review')).toBeNull();
  });

  it('shows Rate & Review without Cancel Booking for completed bookings', () => {
    component.booking = buildHotelBookingCardViewModel({
      id: 2,
      bookingRef: 'HOTEL-COMP-2',
      serviceType: 'HOTEL',
      serviceId: 42,
      serviceTitle: 'GSR Grand Premium',
      totalAmount: 4899,
      status: 'COMPLETED'
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.hotel-status-completed')?.textContent?.trim()).toBe('Completed');
    expect(el.querySelector('.btn-hotel-cancel')).toBeNull();
    expect(el.querySelector('.btn-hotel-review')).not.toBeNull();
  });

  it('renders Refund Pending indicator on cancelled bookings with pending refund', () => {
    component.booking = buildHotelBookingCardViewModel({
      id: 3,
      bookingRef: 'HOTEL-9A1B2C3D',
      serviceType: 'HOTEL',
      serviceId: 42,
      serviceTitle: 'GSR Grand Premium',
      totalAmount: 19497,
      status: 'CANCELLED',
      refundStatus: 'FAILED_RETRYABLE'
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.hotel-status-cancelled')?.textContent?.trim()).toBe('Cancelled');
    expect(el.querySelector('.hotel-refund-pill')?.textContent).toContain('Refund Pending');
  });
});
