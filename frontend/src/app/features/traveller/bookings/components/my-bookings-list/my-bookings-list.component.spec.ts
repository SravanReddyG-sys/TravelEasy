import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MyBookingsListComponent } from './my-bookings-list.component';
import { buildHotelBookingCardViewModel } from '../../utils/hotel-booking-presenter.util';

describe('MyBookingsListComponent', () => {
  let fixture: ComponentFixture<MyBookingsListComponent>;
  let component: MyBookingsListComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyBookingsListComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(MyBookingsListComponent);
    component = fixture.componentInstance;
  });

  it('renders dedicated hotel card for HOTEL bookings while preserving original bus card for BUS bookings', () => {
    const busBooking = {
      id: 10,
      bookingRef: 'BUS-1001',
      serviceType: 'BUS',
      displayTitle: 'Hyderabad → Vijayawada',
      displaySubtitle: 'Morning Star • AC Sleeper',
      dateText: 'Wed, 11 Nov 2026',
      timeText: '08:00 PM',
      capacityText: '2 Seats',
      thumbnail: 'assets/images/travel-easy-bus.png',
      totalAmount: 1200,
      status: 'CONFIRMED',
      statusBadgeClass: 'badge-confirmed',
      statusDisplay: 'Confirmed',
      isCancellable: true,
      isCompleted: false
    };

    const hotelBooking = buildHotelBookingCardViewModel({
      id: 11,
      bookingRef: 'HOTEL-DKG-11',
      serviceType: 'HOTEL',
      serviceId: 42,
      serviceTitle: 'GSR Grand Premium',
      totalAmount: 2499,
      status: 'CONFIRMED'
    });

    component.bookings = [busBooking, hotelBooking];
    component.allBookingsCount = 2;
    component.busBookingsCount = 1;
    component.hotelBookingsCount = 1;
    component.totalBookingsCount = 2;
    component.totalFilteredCount = 2;
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.booking-card-item').length).toBe(1);
    expect(el.querySelectorAll('app-hotel-booking-card').length).toBe(1);
    expect(el.textContent).toContain('Hyderabad → Vijayawada');
    expect(el.textContent).toContain('GSR Grand Premium');
  });
});
