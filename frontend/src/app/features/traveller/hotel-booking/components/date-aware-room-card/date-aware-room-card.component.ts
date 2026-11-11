import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DateAwareRoomTypeDto } from '../../../../../models/hotel.model';
import { resolveImageUrl, DEFAULT_HOTEL_PLACEHOLDER } from '../../../../../shared/utils/image-url.util';

@Component({
  selector: 'app-date-aware-room-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      class="room-card bg-white rounded-4 border shadow-sm mb-3 transition-card"
      [class.selected-card]="quantity > 0"
      [class.unavailable-card]="!isAvailable"
    >
      <div class="row g-0 align-items-stretch">

        <!-- Column 1: Room Thumbnail -->
        <div class="col-lg-3 col-md-4 col-sm-12 room-img-col position-relative">
          <img
            [src]="resolvedImageUrl"
            (error)="onImgError($event)"
            class="room-photo w-100 h-100 object-fit-cover rounded-start-4"
            [alt]="room?.roomTypeName || 'Room photo'"
          >
          <div *ngIf="quantity > 0" class="selected-badge position-absolute top-0 start-0 m-2 px-2 py-1 rounded-pill smaller fw-bold shadow-sm">
            <i class="fa-solid fa-check me-1"></i> {{ quantity }} Selected
          </div>
        </div>

        <!-- Column 2: Room Details & Amenities -->
        <div class="col-lg-6 col-md-5 col-sm-12 p-3 p-md-4 d-flex flex-column justify-content-between">
          <div>
            <!-- Room Type Name & Availability Badge -->
            <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
              <h5 class="room-title fw-bold text-navy mb-0">{{ room?.roomTypeName || 'Room' }}</h5>

              <span
                *ngIf="isAvailable"
                class="badge availability-badge rounded-pill fw-semibold"
              >
                {{ room.availableRoomsAcrossStay }} rooms available
              </span>

              <span
                *ngIf="!isAvailable"
                class="badge bg-danger-subtle text-danger rounded-pill fw-semibold"
              >
                Not available for these dates
              </span>
            </div>

            <!-- Maximum Occupancy Capacity (Strictly authentic data) -->
            <div class="room-capacity text-muted small mb-2 d-flex align-items-center">
              <i class="fa-solid fa-user-group text-primary me-2"></i>
              <span class="fw-semibold text-navy">Up to {{ room.maxOccupancy }} guests</span>
            </div>

            <!-- Room Amenities -->
            <div *ngIf="parsedAmenities.length > 0" class="room-amenities d-flex flex-wrap gap-1 mb-2">
              <span
                *ngFor="let am of visibleAmenities"
                class="badge room-amenity-badge rounded-pill fw-normal"
              >
                {{ am }}
              </span>
              <span *ngIf="remainingAmenitiesCount > 0" class="badge bg-light text-muted border rounded-pill smaller">
                +{{ remainingAmenitiesCount }} more
              </span>
            </div>
          </div>
        </div>

        <!-- Column 3: Pricing & Quantity Selector -->
        <div class="col-lg-3 col-md-3 col-sm-12 p-3 p-md-4 d-flex flex-column justify-content-between align-items-lg-end align-items-start border-start-lg border-top-md bg-light-subtle rounded-end-4">
          <div class="price-section text-lg-end text-start mb-3 mb-lg-0 w-100">
            <ng-container *ngIf="hasValidPrice; else noPriceTpl">
              <span class="price-prefix text-muted smaller d-block">From</span>
              <div class="room-price-display">
                <span class="price-val fw-bold text-navy fs-4">₹{{ displayNightlyRate | number }}</span>
                <span class="price-unit text-muted small"> / night</span>
              </div>
              <div class="price-stay-total text-muted smaller mt-1">
                <div>₹{{ displayStayTotalPerRoom | number }} total for {{ nightsLabel }}</div>
                <div>(per room)</div>
              </div>
            </ng-container>

            <ng-template #noPriceTpl>
              <div class="price-unavailable-badge text-muted small fw-semibold">
                Price unavailable
              </div>
            </ng-template>
          </div>

          <!-- Quantity Stepper Controls -->
          <div class="quantity-section w-100 mt-2">
            <div class="d-flex align-items-center justify-content-lg-end justify-content-start gap-2">
              <button
                type="button"
                class="btn-stepper"
                [disabled]="quantity <= 0"
                (click)="decrement()"
                title="Decrease rooms"
                aria-label="Decrease rooms"
              >
                <i class="fa-solid fa-minus"></i>
              </button>

              <span class="stepper-val fw-bold text-navy fs-5 px-2">{{ quantity }}</span>

              <button
                type="button"
                class="btn-stepper"
                [disabled]="!canAddMore"
                (click)="increment()"
                title="Increase rooms"
                aria-label="Increase rooms"
              >
                <i class="fa-solid fa-plus"></i>
              </button>
            </div>

            <!-- Subtle limit feedback -->
            <div *ngIf="reachedInventoryLimit" class="text-danger smaller text-lg-end text-start mt-1">
              Max available: {{ room.availableRoomsAcrossStay }}
            </div>
          </div>
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

    .room-card {
      transition: all 0.2s ease;
    }

    .room-card:hover {
      box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08) !important;
      border-color: #CBD5E1;
    }

    .selected-card {
      border-color: #0891B2 !important;
      box-shadow: 0 4px 16px rgba(8, 145, 178, 0.15) !important;
    }

    .unavailable-card {
      opacity: 0.65;
      background-color: #F8FAFC !important;
    }

    .room-img-col {
      min-height: 140px;
      background-color: #F1F5F9;
    }

    .room-photo {
      min-height: 140px;
    }

    @media (max-width: 767.98px) {
      .room-photo {
        border-radius: 1rem 1rem 0 0 !important;
        max-height: 180px;
      }
    }

    .selected-badge {
      background-color: #0891B2;
      color: #FFFFFF;
    }

    .availability-badge {
      background-color: #DCFCE7;
      color: #166534;
      font-size: 0.78rem;
      padding: 4px 10px;
      border: 1px solid #BBF7D0;
    }

    .room-title {
      font-size: 1.15rem;
      color: #0F172A;
    }

    .room-amenity-badge {
      font-size: 0.75rem;
      background-color: #F1F5F9;
      color: #334155;
      border: 1px solid #E2E8F0;
      padding: 3px 8px;
    }

    .price-stay-total {
      font-size: 0.78rem;
      line-height: 1.35;
      color: #64748B;
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

    .btn-stepper {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      border: 1px solid #CBD5E1;
      background-color: #FFFFFF;
      color: #0F172A;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      font-size: 0.75rem;
      transition: all 0.15s ease;
    }

    .btn-stepper:hover:not(:disabled) {
      background-color: #0891B2;
      border-color: #0891B2;
      color: #FFFFFF;
    }

    .btn-stepper:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }

    @media (min-width: 992px) {
      .border-start-lg {
        border-left: 1px solid #F1F5F9 !important;
      }
    }
  `]
})
export class DateAwareRoomCardComponent {
  @Input({ required: true }) room!: DateAwareRoomTypeDto;
  @Input() quantity = 0;
  @Input() nights = 1;
  @Input() canIncrement = true;
  @Input() requestedRooms = 1;

  @Output() quantityChange = new EventEmitter<number>();

  fallbackImage = DEFAULT_HOTEL_PLACEHOLDER;

  get resolvedImageUrl(): string {
    return resolveImageUrl(this.room?.imageUrl, this.fallbackImage);
  }

  get isAvailable(): boolean {
    return !!(this.room?.availableAcrossStay && (this.room?.availableRoomsAcrossStay || 0) > 0);
  }

  get hasValidPrice(): boolean {
    return typeof this.room?.minNightlyPrice === 'number' && this.room.minNightlyPrice > 0;
  }

  get displayNightlyRate(): number {
    return this.hasValidPrice ? (this.room.minNightlyPrice as number) : (this.room.basePricePerNight || 0);
  }

  get displayStayTotalPerRoom(): number {
    // If nightlyBreakdown exists, sum the nightly prices for 1 physical room
    if (this.room?.nightlyBreakdown && this.room.nightlyBreakdown.length > 0) {
      const sum = this.room.nightlyBreakdown.reduce((acc, nb) => acc + (nb.price || 0), 0);
      if (sum > 0) return sum;
    }

    // Fallback: totalPriceForStay / requestedRooms if positive
    if (typeof this.room?.totalPriceForStay === 'number' && this.room.totalPriceForStay > 0) {
      const req = this.requestedRooms > 0 ? this.requestedRooms : 1;
      return Math.round((this.room.totalPriceForStay / req) * 100) / 100;
    }

    // Multiply nightly price by nights
    const n = this.nights > 0 ? this.nights : 1;
    return this.displayNightlyRate * n;
  }

  get nightsLabel(): string {
    const n = this.nights > 0 ? this.nights : 1;
    return n === 1 ? '1 night' : `${n} nights`;
  }

  get parsedAmenities(): string[] {
    if (!this.room?.amenities || typeof this.room.amenities !== 'string') return [];
    return this.room.amenities
      .split(',')
      .map(s => s.trim())
      .filter(s => s.length > 0);
  }

  get visibleAmenities(): string[] {
    return this.parsedAmenities.slice(0, 5);
  }

  get remainingAmenitiesCount(): number {
    return Math.max(0, this.parsedAmenities.length - 5);
  }

  get canAddMore(): boolean {
    if (!this.isAvailable || !this.hasValidPrice) return false;
    if (this.quantity >= (this.room?.availableRoomsAcrossStay || 0)) return false;
    return this.canIncrement;
  }

  get reachedInventoryLimit(): boolean {
    return this.isAvailable && this.quantity >= (this.room?.availableRoomsAcrossStay || 0);
  }

  increment(): void {
    if (this.canAddMore) {
      this.quantityChange.emit(this.quantity + 1);
    }
  }

  decrement(): void {
    if (this.quantity > 0) {
      this.quantityChange.emit(this.quantity - 1);
    }
  }

  onImgError(e: Event): void {
    const target = e.target as HTMLImageElement;
    if (target && target.src !== this.fallbackImage) {
      target.src = this.fallbackImage;
    }
  }
}
