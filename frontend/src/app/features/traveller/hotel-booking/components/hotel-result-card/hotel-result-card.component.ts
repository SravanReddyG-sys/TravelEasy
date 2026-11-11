import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HotelPublicDto } from '../../../../../models/hotel.model';
import { resolveImageUrl, DEFAULT_HOTEL_PLACEHOLDER } from '../../../../../shared/utils/image-url.util';

export interface AmenityBadge {
  label: string;
  icon: string;
}

@Component({
  selector: 'app-hotel-result-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="hotel-result-card bg-white rounded-4 border shadow-sm mb-3">
      <div class="row g-0 align-items-stretch">

        <!-- Column 1: Hotel Photo Thumbnail -->
        <div class="col-lg-4 col-md-5 position-relative hotel-img-container">
          <img
            [src]="resolvedImageUrl"
            (error)="onImgError($event)"
            class="hotel-photo w-100 h-100 object-fit-cover"
            [alt]="hotel.name || 'Hotel thumbnail'"
          >
          
          <!-- Favourite Heart Button Overlay -->
          <button
            type="button"
            class="btn-fav-card shadow-sm"
            (click)="onToggleFavourite($event)"
            [title]="isSaved ? 'Remove from Saved' : 'Save to Favourites'"
            [attr.aria-label]="isSaved ? 'Remove from Saved' : 'Save to Favourites'"
          >
            <i [class]="isSaved ? 'fa-solid fa-heart text-danger' : 'fa-regular fa-heart text-secondary'"></i>
          </button>

          <!-- Carousel Indicator Dots (Matching Mockup) -->
          <div class="carousel-dots-overlay d-flex align-items-center justify-content-center gap-1">
            <span class="carousel-dot active"></span>
            <span class="carousel-dot"></span>
            <span class="carousel-dot"></span>
          </div>
        </div>

        <!-- Column 2: Hotel Details & Amenities -->
        <div class="col-lg-5 col-md-7 p-3 p-md-4 d-flex flex-column justify-content-between">
          <div>
            <!-- Hotel Name -->
            <h5 class="hotel-title fw-bold text-navy mb-1">{{ hotel.name || 'Unnamed Hotel' }}</h5>

            <!-- Location -->
            <div class="hotel-location text-muted small mb-2 d-flex align-items-center">
              <i class="fa-solid fa-location-dot text-primary me-2 flex-shrink-0"></i>
              <span class="text-truncate" [title]="locationText">{{ locationText }}</span>
            </div>

            <!-- Description -->
            <p class="hotel-description text-secondary small mb-3">
              {{ hotel.description || 'Description unavailable' }}
            </p>

            <!-- Amenities Badges with Icons -->
            <div *ngIf="visibleAmenities.length > 0" class="d-flex flex-wrap align-items-center gap-2 mb-2">
              <div *ngFor="let am of visibleAmenities" class="amenity-badge d-flex align-items-center gap-1">
                <i [class]="am.icon"></i>
                <span>{{ am.label }}</span>
              </div>
              <span *ngIf="remainingAmenitiesCount > 0" class="badge-more-amenities">
                +{{ remainingAmenitiesCount }} more
              </span>
            </div>
          </div>
        </div>

        <!-- Column 3: Price and View Rooms Action -->
        <div class="col-lg-3 col-md-12 p-3 p-md-4 d-flex flex-column justify-content-between align-items-lg-end align-items-start border-start-lg border-top-md">
          <div class="price-section text-lg-end text-start mb-3 mb-lg-0">
            <ng-container *ngIf="hasValidPrice; else noPriceTpl">
              <span class="price-prefix text-muted smaller d-block">From</span>
              <div class="hotel-price-display">
                <span class="price-val fw-bold text-navy fs-4">₹{{ displayNightlyPrice | number }}</span>
                <span class="price-unit text-muted small"> / night</span>
              </div>
              <div *ngIf="hasStayTotal" class="price-stay-total text-muted smaller mt-1">
                <div>₹{{ displayStayTotal | number }} total for {{ nightsLabel }}</div>
                <div>(per room)</div>
              </div>
            </ng-container>
            <ng-template #noPriceTpl>
              <div class="price-unavailable-badge text-muted small fw-semibold">
                Price unavailable
              </div>
            </ng-template>
          </div>

          <!-- View Rooms Action Button -->
          <button
            type="button"
            class="btn-view-rooms fw-bold text-nowrap"
            (click)="onViewRooms()"
          >
            <span>View Rooms</span>
            <i class="fa-solid fa-chevron-right ms-2 small"></i>
          </button>
        </div>

      </div>
    </div>
  `,
  styles: [`
    .text-navy {
      color: #0F172A;
    }
    .smaller {
      font-size: 0.75rem;
    }

    .hotel-result-card {
      border-color: #E2E8F0;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
      overflow: hidden;
    }

    .hotel-result-card:hover {
      box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08) !important;
      border-color: #CBD5E1;
    }

    .hotel-img-container {
      min-height: 190px;
      background-color: #F1F5F9;
    }

    .hotel-photo {
      border-top-left-radius: 1rem;
      border-bottom-left-radius: 1rem;
    }

    @media (max-width: 767.98px) {
      .hotel-photo {
        border-bottom-left-radius: 0;
        border-top-right-radius: 1rem;
        max-height: 200px;
      }
    }

    /* Favourite Heart Button */
    .btn-fav-card {
      position: absolute;
      top: 12px;
      right: 12px;
      width: 34px;
      height: 34px;
      border-radius: 50%;
      background: #FFFFFF;
      border: none;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: transform 0.15s ease, background-color 0.15s ease;
      z-index: 2;
    }

    .btn-fav-card:hover {
      transform: scale(1.1);
      background-color: #F8FAFC;
    }

    /* Carousel Indicator Dots */
    .carousel-dots-overlay {
      position: absolute;
      bottom: 12px;
      left: 0;
      right: 0;
      pointer-events: none;
      z-index: 2;
    }

    .carousel-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: rgba(255, 255, 255, 0.6);
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
      transition: all 0.2s ease;
    }

    .carousel-dot.active {
      width: 7px;
      height: 7px;
      background-color: #FFFFFF;
    }

    .hotel-title {
      font-size: 1.15rem;
      color: #0F172A;
    }

    .hotel-description {
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      line-height: 1.45;
    }

    /* Amenity Badges with Icons */
    .amenity-badge {
      font-size: 0.78rem;
      font-weight: 500;
      color: #334155;
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      border-radius: 8px;
      padding: 4px 10px;
    }

    .amenity-badge i {
      color: #0891B2;
      font-size: 0.8rem;
    }

    .badge-more-amenities {
      font-size: 0.75rem;
      font-weight: 600;
      color: #0891B2;
      background: #ECFEFF;
      border: 1px solid #CFFAFE;
      border-radius: 8px;
      padding: 4px 8px;
    }

    /* View Rooms Action Button (Teal / Cyan) */
    .btn-view-rooms {
      background-color: #0891B2;
      color: #FFFFFF;
      border: none;
      border-radius: 12px;
      padding: 10px 22px;
      font-size: 0.90rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: background-color 0.18s ease, transform 0.1s ease;
      box-shadow: 0 4px 12px rgba(8, 145, 178, 0.2);
    }

    .btn-view-rooms:hover {
      background-color: #0E7490;
      transform: translateY(-1px);
    }

    .price-unavailable-badge {
      display: inline-block;
      padding: 4px 10px;
      background-color: #F1F5F9;
      border: 1px solid #E2E8F0;
      border-radius: 8px;
      color: #64748B;
      font-size: 0.82rem;
    }

    .price-stay-total {
      font-size: 0.78rem;
      line-height: 1.35;
      color: #64748B;
    }

    @media (min-width: 992px) {
      .border-start-lg {
        border-left: 1px solid #F1F5F9 !important;
      }
    }

    @media (max-width: 767.98px) {
      .border-top-md {
        border-top: 1px solid #F1F5F9 !important;
      }
      .btn-view-rooms {
        width: 100%;
      }
    }
  `]
})
export class HotelResultCardComponent {
  @Input({ required: true }) hotel!: HotelPublicDto;
  @Input() isSaved = false;
  @Input() nights = 1;
  @Input() roomsCount = 1;

  @Output() viewRooms = new EventEmitter<HotelPublicDto>();
  @Output() toggleFavourite = new EventEmitter<HotelPublicDto>();

  fallbackImage = DEFAULT_HOTEL_PLACEHOLDER;
  hasImageError = false;

  get resolvedImageUrl(): string {
    return resolveImageUrl(this.hotel?.imageUrl, this.fallbackImage);
  }

  get locationText(): string {
    if (!this.hotel) return '';
    const city = (this.hotel.city || '').trim();
    const addr = (this.hotel.address || '').trim();
    if (addr && city) {
      if (addr.toLowerCase().includes(city.toLowerCase())) {
        return addr;
      }
      return `${addr}, ${city}`;
    }
    return addr || city || 'Location details unavailable';
  }

  get hasValidPrice(): boolean {
    return typeof this.hotel?.minPrice === 'number' && this.hotel.minPrice > 0;
  }

  get displayNightlyPrice(): number {
    return this.hasValidPrice ? (this.hotel.minPrice as number) : 0;
  }

  get hasStayTotal(): boolean {
    return typeof this.hotel?.minTotalPricePerRoomForStay === 'number' && this.hotel.minTotalPricePerRoomForStay > 0;
  }

  get displayStayTotal(): number {
    return this.hasStayTotal ? (this.hotel.minTotalPricePerRoomForStay as number) : 0;
  }

  get nightsLabel(): string {
    const n = this.nights && this.nights > 0 ? this.nights : 1;
    return n === 1 ? '1 night' : `${n} nights`;
  }

  get parsedAmenities(): string[] {
    if (!this.hotel?.amenities || typeof this.hotel.amenities !== 'string') return [];
    const unique = new Set(
      this.hotel.amenities
        .split(',')
        .map(s => s.trim())
        .filter(s => s.length > 0)
    );
    return Array.from(unique);
  }

  get visibleAmenities(): AmenityBadge[] {
    const list = this.parsedAmenities.slice(0, 4);
    return list.map(item => ({
      label: item,
      icon: this.getAmenityIcon(item)
    }));
  }

  get remainingAmenitiesCount(): number {
    return Math.max(0, this.parsedAmenities.length - 4);
  }

  getAmenityIcon(name: string): string {
    const n = name.toLowerCase();
    if (n.includes('wi-fi') || n.includes('wifi') || n.includes('internet')) return 'fa-solid fa-wifi';
    if (n.includes('restaurant') || n.includes('dining') || n.includes('food')) return 'fa-solid fa-utensils';
    if (n.includes('pool') || n.includes('swimming')) return 'fa-solid fa-person-swimming';
    if (n.includes('spa') || n.includes('wellness') || n.includes('massage')) return 'fa-solid fa-spa';
    if (n.includes('gym') || n.includes('fitness')) return 'fa-solid fa-dumbbell';
    if (n.includes('parking')) return 'fa-solid fa-square-parking';
    if (n.includes('breakfast')) return 'fa-solid fa-mug-saucer';
    if (n.includes('ac') || n.includes('air conditioning')) return 'fa-solid fa-snowflake';
    if (n.includes('bar')) return 'fa-solid fa-martini-glass';
    return 'fa-solid fa-check';
  }

  onImgError(e: Event) {
    if (!this.hasImageError) {
      this.hasImageError = true;
      const target = e.target as HTMLImageElement;
      if (target) {
        target.src = this.fallbackImage;
      }
    }
  }

  onToggleFavourite(event: Event) {
    event.stopPropagation();
    this.toggleFavourite.emit(this.hotel);
  }

  onViewRooms() {
    this.viewRooms.emit(this.hotel);
  }
}
