import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SelectedRoomItem, HotelStaySummary } from '../../../../../models/hotel.model';

@Component({
  selector: 'app-stay-summary-panel',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="stay-summary-card bg-white rounded-4 border shadow-sm p-4 sticky-top">

      <!-- Header Row -->
      <div class="d-flex align-items-center gap-2 mb-3 pb-2 border-bottom">
        <i class="fa-regular fa-calendar-check text-teal fs-5"></i>
        <h5 class="fw-bold text-navy mb-0">Stay Summary</h5>
      </div>

      <!-- Selected Rooms Header -->
      <div class="mb-3">
        <div class="fw-bold text-navy">
          Selected Rooms ({{ totalSelectedRooms }})
        </div>
        <div *ngIf="totalSelectedRooms === 0" class="text-muted smaller mt-1">
          Add one or more rooms to continue.
        </div>
      </div>

      <!-- Stay Criteria List -->
      <div class="stay-details-list smaller text-muted mb-3 d-flex flex-column gap-2">
        <div class="d-flex justify-content-between align-items-center">
          <span class="d-flex align-items-center gap-2">
            <i class="fa-regular fa-calendar text-teal"></i> Check-in
          </span>
          <span class="fw-semibold text-navy">{{ formattedCheckIn }}</span>
        </div>

        <div class="d-flex justify-content-between align-items-center">
          <span class="d-flex align-items-center gap-2">
            <i class="fa-regular fa-calendar text-teal"></i> Check-out
          </span>
          <span class="fw-semibold text-navy">{{ formattedCheckOut }}</span>
        </div>

        <div class="d-flex justify-content-between align-items-center">
          <span class="d-flex align-items-center gap-2">
            <i class="fa-regular fa-moon text-teal"></i> Nights
          </span>
          <span class="fw-semibold text-navy">{{ totalNights }} {{ totalNights === 1 ? 'Night' : 'Nights' }}</span>
        </div>

        <div class="d-flex justify-content-between align-items-center">
          <span class="d-flex align-items-center gap-2">
            <i class="fa-solid fa-user-group text-teal"></i> Guests
          </span>
          <span class="fw-semibold text-navy">{{ guestsSummary }}</span>
        </div>

        <div class="d-flex justify-content-between align-items-center">
          <span class="d-flex align-items-center gap-2">
            <i class="fa-solid fa-bed text-teal"></i> Total Rooms
          </span>
          <span class="fw-semibold" [class.text-success]="isSelectionComplete" [class.text-navy]="!isSelectionComplete">
            {{ totalSelectedRooms }} / {{ requestedRooms }}
          </span>
        </div>
      </div>

      <!-- Selected Room Types Breakdown -->
      <div *ngIf="selectedItems.length > 0" class="selected-breakdown-box bg-light rounded-3 p-3 mb-3 border">
        <div class="smaller fw-bold text-navy mb-2">Room Selection Breakdown:</div>
        <div *ngFor="let item of selectedItems" class="d-flex justify-content-between align-items-center smaller mb-1">
          <span class="text-truncate me-2">
            <strong>{{ item.roomTypeName }}</strong> × {{ item.quantity }}
          </span>
          <span class="fw-semibold text-navy text-nowrap">
            ₹{{ item.totalStayPrice | number }}
          </span>
        </div>
      </div>

      <!-- Divider -->
      <hr class="my-3 text-muted">

      <!-- Total Amount Box -->
      <div class="total-amount-box bg-light-subtle rounded-3 p-3 mb-3 border">
        <div class="d-flex justify-content-between align-items-baseline">
          <span class="fw-bold text-navy">Total Amount</span>
          <span class="fs-4 fw-bold text-navy">₹{{ totalAmount | number }}</span>
        </div>
        <div class="text-muted smaller mt-1">
          Estimated total for selected rooms across {{ totalNights }} nights.
        </div>
      </div>

      <!-- Selection Guidance Message (when incomplete) -->
      <div *ngIf="!isSelectionComplete && remainingRoomsToSelect > 0" class="alert alert-info py-2 px-3 smaller rounded-3 mb-3">
        <i class="fa-solid fa-circle-info me-1 text-primary"></i>
        Select {{ remainingRoomsToSelect }} more {{ remainingRoomsToSelect === 1 ? 'room' : 'rooms' }} to complete reservation criteria.
      </div>

      <div *ngIf="totalSelectedRooms > requestedRooms" class="alert alert-warning py-2 px-3 smaller rounded-3 mb-3">
        <i class="fa-solid fa-triangle-exclamation me-1 text-warning"></i>
        Selected rooms ({{ totalSelectedRooms }}) exceed requested count ({{ requestedRooms }}).
      </div>

      <!-- Primary Action CTA Button (Matching Mockup) -->
      <button
        type="button"
        class="btn btn-continue-checkout w-100 py-2 rounded-pill fw-bold text-white d-flex align-items-center justify-content-center gap-2"
        [disabled]="!isSelectionComplete"
        (click)="onContinue()"
      >
        <span>Continue to Guest Details</span>
        <i class="fa-solid fa-arrow-right small"></i>
      </button>

    </div>
  `,
  styles: [`
    .text-navy {
      color: #0F172A;
    }
    .text-teal {
      color: #0891B2;
    }
    .smaller {
      font-size: 0.78rem;
    }

    .stay-summary-card {
      top: 24px;
      z-index: 10;
    }

    .btn-continue-checkout {
      background-color: #0891B2;
      border: none;
      font-size: 0.95rem;
      transition: background-color 0.2s ease, transform 0.15s ease;
    }

    .btn-continue-checkout:hover:not(:disabled) {
      background-color: #0E7490;
      transform: translateY(-1px);
    }

    .btn-continue-checkout:disabled {
      background-color: #94A3B8;
      cursor: not-allowed;
      opacity: 0.7;
    }
  `]
})
export class StaySummaryPanelComponent {
  @Input() hotelId = 0;
  @Input() hotelName = '';
  @Input() city = '';
  @Input() propertyType: string | null | undefined = null;
  @Input() address: string | null | undefined = null;
  @Input() hotelImageUrl: string | null | undefined = null;
  @Input() checkIn = '';
  @Input() checkOut = '';
  @Input() totalNights = 1;
  @Input() requestedRooms = 1;
  @Input() adults = 1;
  @Input() children = 0;
  @Input() selectedItems: SelectedRoomItem[] = [];

  @Output() continue = new EventEmitter<HotelStaySummary>();

  get totalSelectedRooms(): number {
    return this.selectedItems.reduce((acc, it) => acc + it.quantity, 0);
  }

  get remainingRoomsToSelect(): number {
    return Math.max(0, this.requestedRooms - this.totalSelectedRooms);
  }

  get isSelectionComplete(): boolean {
    return this.totalSelectedRooms === this.requestedRooms && this.totalSelectedRooms > 0;
  }

  get totalAmount(): number {
    return this.selectedItems.reduce((acc, it) => acc + it.totalStayPrice, 0);
  }

  get formattedCheckIn(): string {
    return this.formatDisplayDate(this.checkIn);
  }

  get formattedCheckOut(): string {
    return this.formatDisplayDate(this.checkOut);
  }

  get guestsSummary(): string {
    const adultLabel = this.adults === 1 ? '1 Adult' : `${this.adults} Adults`;
    if (this.children === 0) return adultLabel;
    const childLabel = this.children === 1 ? '1 Child' : `${this.children} Children`;
    return `${adultLabel}, ${childLabel}`;
  }

  onContinue(): void {
    if (!this.isSelectionComplete) return;

    const summary: HotelStaySummary = {
      hotelId: this.hotelId,
      hotelName: this.hotelName,
      city: this.city,
      propertyType: this.propertyType || null,
      address: this.address || null,
      hotelImageUrl: this.hotelImageUrl || null,
      checkIn: this.checkIn,
      checkOut: this.checkOut,
      totalNights: this.totalNights,
      requestedRooms: this.requestedRooms,
      adults: this.adults,
      children: this.children,
      selectedItems: this.selectedItems,
      totalSelectedRooms: this.totalSelectedRooms,
      estimatedTotalAmount: this.totalAmount
    };

    this.continue.emit(summary);
  }

  private formatDisplayDate(isoDate: string): string {
    if (!isoDate) return '';
    try {
      const parts = isoDate.split('-');
      if (parts.length === 3) {
        const d = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10), 12, 0, 0);
        return d.toLocaleDateString('en-GB', {
          weekday: 'short',
          day: 'numeric',
          month: 'short',
          year: 'numeric'
        });
      }
      return isoDate;
    } catch {
      return isoDate;
    }
  }
}
