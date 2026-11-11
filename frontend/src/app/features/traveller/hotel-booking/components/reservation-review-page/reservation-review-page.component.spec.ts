import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { ReservationReviewPageComponent } from './reservation-review-page.component';
import { AuthService } from '../../../../../services/auth.service';
import { TravellerService } from '../../../../../services/traveller.service';
import { HotelStaySummary, PhysicalRoomAllocation } from '../../../../../models/hotel.model';
import { RESERVATION_REVIEW_STORAGE_KEY } from '../../../../../shared/utils/reservation-review.util';
import { User } from '../../../../../models/user.model';

function createSampleSummary(): HotelStaySummary {
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
        totalStayPrice: 19497
      },
      {
        roomTypeId: 12,
        roomTypeName: 'Standard Room',
        maxOccupancy: 2,
        quantity: 1,
        minNightlyPrice: 4899,
        stayTotalPricePerRoom: 14697,
        totalStayPrice: 14697
      }
    ],
    totalSelectedRooms: 2,
    estimatedTotalAmount: 34194
  };
}

function createSampleRooms(): PhysicalRoomAllocation[] {
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
        { id: 'r1-a1', type: 'ADULT', name: 'Rohit Sharma', age: 32, gender: 'MALE' },
        { id: 'r1-c1', type: 'CHILD', name: 'Aarav Sharma', age: 8, gender: null }
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
        { id: 'r2-a1', type: 'ADULT', name: 'Priya Sharma', age: 29, gender: 'FEMALE' }
      ]
    }
  ];
}

describe('ReservationReviewPageComponent (WP-HB-UI-04)', () => {
  let mockUser: User | null;
  let profileSubject: Subject<any>;
  let authServiceStub: Partial<AuthService>;
  let travellerServiceStub: Partial<TravellerService>;

  beforeEach(async () => {
    sessionStorage.clear();
    profileSubject = new Subject<any>();
    mockUser = {
      id: 10,
      email: 'sravan.reddy@example.com',
      fullName: 'Sravan Reddy G',
      phone: '+919876543210',
      role: 'ROLE_CUSTOMER',
      token: 'jwt-token'
    };

    authServiceStub = {
      get currentUserValue() {
        return mockUser;
      }
    };
    travellerServiceStub = {
      getProfile: jasmine.createSpy('getProfile').and.returnValue(profileSubject.asObservable())
    };

    await TestBed.configureTestingModule({
      imports: [ReservationReviewPageComponent],
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: TravellerService, useValue: travellerServiceStub }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('prefills primary guest contact from auth context without calling getProfile unnecessarily', () => {
    const fixture: ComponentFixture<ReservationReviewPageComponent> = TestBed.createComponent(ReservationReviewPageComponent);
    const component = fixture.componentInstance;
    component.staySummary = createSampleSummary();
    component.allocatedRooms = createSampleRooms();
    component.requestedHotelId = 101;
    fixture.detectChanges();

    expect(component.primaryGuestForm.fullName).toBe('Sravan Reddy G');
    expect(component.primaryGuestForm.email).toBe('sravan.reddy@example.com');
    expect(component.primaryGuestForm.dialCode).toBe('+91');
    expect(component.primaryGuestForm.nationalNumber).toBe('9876543210');
    expect(travellerServiceStub.getProfile).not.toHaveBeenCalled();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Reservation Review');
    expect(text).toContain('The Lakeside Grand Hotel');
    expect(text).toContain('Room 1');
    expect(text).toContain('Rohit Sharma');
    expect(text).toContain('Gender: Male');
    expect(text).toContain('Aarav Sharma');
    expect(text).toContain('Total Amount');
    expect(text).toContain('34,194');
  });

  it('never overwrites user-edited fields when delayed profile fetch completes', () => {
    mockUser = {
      id: 10,
      email: '',
      fullName: '',
      phone: '',
      role: 'ROLE_CUSTOMER',
      token: 'jwt-token'
    };

    const fixture = TestBed.createComponent(ReservationReviewPageComponent);
    const component = fixture.componentInstance;
    component.staySummary = createSampleSummary();
    component.allocatedRooms = createSampleRooms();
    component.requestedHotelId = 101;
    fixture.detectChanges();

    expect(travellerServiceStub.getProfile).toHaveBeenCalledTimes(1);

    component.onFieldInput('fullName', 'Custom Booking Contact');
    profileSubject.next({
      fullName: 'Profile Name',
      email: 'profile@example.com',
      phone: '+91 9876543210'
    });

    expect(component.primaryGuestForm.fullName).toBe('Custom Booking Contact');
    expect(component.primaryGuestForm.email).toBe('profile@example.com');
    expect(component.primaryGuestForm.nationalNumber).toBe('9876543210');
  });

  it('defers errors until blur/submit, validates primary guest contact, and emits once on valid submit', () => {
    const fixture = TestBed.createComponent(ReservationReviewPageComponent);
    const component = fixture.componentInstance;
    component.staySummary = createSampleSummary();
    component.allocatedRooms = createSampleRooms();
    component.requestedHotelId = 101;
    fixture.detectChanges();

    component.onFieldInput('nationalNumber', '1234');
    expect(component.shouldShowError('nationalNumber')).toBeFalse();

    component.onFieldBlur('nationalNumber');
    expect(component.shouldShowError('nationalNumber')).toBeTrue();

    const emitSpy = jasmine.createSpy('proceedToPayment');
    component.proceedToPayment.subscribe(emitSpy);

    component.onProceedToPayment();
    expect(emitSpy).not.toHaveBeenCalled();

    component.onFieldInput('nationalNumber', '98765 43210');
    component.onProceedToPayment();

    expect(emitSpy).toHaveBeenCalledTimes(1);
    const emitted = emitSpy.calls.mostRecent().args[0];
    expect(emitted.primaryGuest).toEqual({
      fullName: 'Sravan Reddy G',
      email: 'sravan.reddy@example.com',
      mobile: '+91 9876543210'
    });
    expect(sessionStorage.getItem(RESERVATION_REVIEW_STORAGE_KEY)).toBeTruthy();
  });

  it('renders recoverable guard screen when prerequisite stay summary is missing', () => {
    const fixture = TestBed.createComponent(ReservationReviewPageComponent);
    const component = fixture.componentInstance;
    component.staySummary = null;
    component.allocatedRooms = [];
    component.requestedHotelId = 101;
    fixture.detectChanges();

    expect(component.hasValidPrerequisites).toBeFalse();
    expect(fixture.nativeElement.querySelector('[data-testid="review-missing-state-card"]')).toBeTruthy();
  });
});
