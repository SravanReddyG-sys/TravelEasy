import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HotelResultCardComponent } from './hotel-result-card.component';
import { HotelPublicDto } from '../../../../../models/hotel.model';

describe('HotelResultCardComponent', () => {
  let component: HotelResultCardComponent;
  let fixture: ComponentFixture<HotelResultCardComponent>;

  const mockHotelWithPrice: HotelPublicDto = {
    id: 1131,
    name: 'GSR Grand Premium',
    city: 'Vijayawada',
    address: '2-33/01, GSR Estate, MG Road, Vijayawada',
    starRating: 5,
    description: 'Offering comfortable accommodation and premium amenities.',
    amenities: 'Wi-Fi, Restaurant, Swimming Pool, Parking',
    imageUrl: '/api/hotels/images/hotel_m3_cover.jpg',
    minPrice: 3499.0,
    minTotalPricePerRoomForStay: 3499.0,
    selectedRoomTypeId: 1478,
    propertyType: 'Hotel'
  };

  const mockHotelWithoutPrice: HotelPublicDto = {
    id: 1132,
    name: 'Unpriced Inn',
    city: 'Vijayawada',
    address: '45 Station Road',
    imageUrl: '',
    minPrice: 0.0,
    minTotalPricePerRoomForStay: 0.0,
    propertyType: 'Hotel'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HotelResultCardComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(HotelResultCardComponent);
    component = fixture.componentInstance;
  });

  it('should create the component', () => {
    component.hotel = mockHotelWithPrice;
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should display valid starting nightly price and per-room stay total', () => {
    component.hotel = mockHotelWithPrice;
    component.nights = 1;
    component.roomsCount = 1;
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.price-val')?.textContent?.trim()).toBe('₹3,499');
    expect(compiled.querySelector('.price-stay-total')?.textContent).toContain('₹3,499 total for 1 night');
    expect(compiled.querySelector('.price-stay-total')?.textContent).toContain('(per room)');
  });

  it('should format multiple nights correctly for stay total', () => {
    component.hotel = {
      ...mockHotelWithPrice,
      minPrice: 3499.0,
      minTotalPricePerRoomForStay: 10497.0
    };
    component.nights = 3;
    component.roomsCount = 1;
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.price-val')?.textContent?.trim()).toBe('₹3,499');
    expect(compiled.querySelector('.price-stay-total')?.textContent).toContain('₹10,497 total for 3 nights');
    expect(compiled.querySelector('.price-stay-total')?.textContent).toContain('(per room)');
  });

  it('should display "Price unavailable" when minPrice is null or 0', () => {
    component.hotel = mockHotelWithoutPrice;
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.price-unavailable-badge')?.textContent?.trim()).toBe('Price unavailable');
    expect(compiled.querySelector('.price-val')).toBeNull();
    expect(compiled.querySelector('.price-stay-total')).toBeNull();
  });

  it('should update prices when a new hotel input is provided', () => {
    component.hotel = mockHotelWithoutPrice;
    fixture.detectChanges();
    let compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.price-unavailable-badge')?.textContent?.trim()).toBe('Price unavailable');

    // Update to hotel with price
    component.hotel = mockHotelWithPrice;
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.price-val')?.textContent?.trim()).toBe('₹3,499');
  });

  it('should preserve absolute image URLs', () => {
    const absoluteHotel: HotelPublicDto = {
      ...mockHotelWithPrice,
      imageUrl: 'https://images.unsplash.com/photo-hotel-123.jpg'
    };
    component.hotel = absoluteHotel;
    expect(component.resolvedImageUrl).toBe('https://images.unsplash.com/photo-hotel-123.jpg');
  });

  it('should resolve relative API image path against API Gateway URL', () => {
    component.hotel = mockHotelWithPrice;
    expect(component.resolvedImageUrl).toContain('/api/hotels/images/hotel_m3_cover.jpg');
  });
});
