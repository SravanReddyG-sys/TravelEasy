import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HotelSearchCriteria } from '../../../../../models/hotel.model';

@Component({
  selector: 'app-hotel-search-context-strip',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="search-context-strip bg-white rounded-4 border shadow-sm p-3 mb-4">
      <!-- Main Horizontal Row (Matching Mockup) -->
      <div class="d-flex flex-wrap align-items-center justify-content-between gap-3">

        <!-- Search Summary Items -->
        <div class="d-flex flex-wrap align-items-center gap-3 gap-md-4">

          <!-- Label: Your Search -->
          <div class="d-flex align-items-center gap-2 text-teal fw-bold">
            <i class="fa-regular fa-calendar-check fs-5"></i>
            <span class="text-navy">Your Search</span>
          </div>

          <!-- Divider -->
          <div class="strip-divider d-none d-md-block"></div>

          <!-- Destination -->
          <div class="d-flex align-items-center gap-2">
            <i class="fa-solid fa-location-dot text-primary"></i>
            <span class="fw-semibold text-navy">{{ city || 'Select city' }}</span>
          </div>

          <!-- Divider -->
          <div class="strip-divider d-none d-md-block"></div>

          <!-- Check-in -->
          <div class="d-flex align-items-center gap-2">
            <i class="fa-regular fa-calendar text-primary"></i>
            <div>
              <div class="text-muted smaller lh-1">Check-in</div>
              <div class="fw-semibold text-navy small">{{ formattedCheckIn || 'Select date' }}</div>
            </div>
          </div>

          <!-- Divider -->
          <div class="strip-divider d-none d-md-block"></div>

          <!-- Check-out -->
          <div class="d-flex align-items-center gap-2">
            <i class="fa-regular fa-calendar text-primary"></i>
            <div>
              <div class="text-muted smaller lh-1">Check-out</div>
              <div class="fw-semibold text-navy small">{{ formattedCheckOut || 'Select date' }}</div>
            </div>
          </div>

          <!-- Divider -->
          <div class="strip-divider d-none d-md-block"></div>

          <!-- Guests -->
          <div class="d-flex align-items-center gap-2">
            <i class="fa-solid fa-user-group text-primary"></i>
            <div>
              <div class="text-muted smaller lh-1">Guests</div>
              <div class="fw-semibold text-navy small">{{ guestsSummary }}</div>
            </div>
          </div>

          <!-- Divider -->
          <div class="strip-divider d-none d-md-block"></div>

          <!-- Rooms -->
          <div class="d-flex align-items-center gap-2">
            <i class="fa-solid fa-bed text-primary"></i>
            <div>
              <div class="text-muted smaller lh-1">Rooms</div>
              <div class="fw-semibold text-navy small">{{ roomsSummary }}</div>
            </div>
          </div>

        </div>

        <!-- Modify Search Action Button -->
        <div>
          <button
            type="button"
            class="btn btn-outline-teal btn-modify-search rounded-pill px-3 py-1 fw-bold d-flex align-items-center gap-2"
            (click)="toggleModify()"
          >
            <i class="fa-solid fa-pen small"></i>
            <span>{{ isModifying ? 'Close' : 'Modify Search' }}</span>
          </button>
        </div>

      </div>

      <!-- Expandable Inline Modify Search Form -->
      <div *ngIf="isModifying" class="modify-search-panel mt-3 pt-3 border-top">
        <form (ngSubmit)="onApplyChanges()" class="row g-3 align-items-end">

          <!-- Check-in Date -->
          <div class="col-md-3 col-sm-6">
            <label class="form-label text-muted smaller fw-bold mb-1">CHECK-IN</label>
            <input
              type="date"
              class="form-control form-control-sm rounded-3"
              [(ngModel)]="editCheckIn"
              name="editCheckIn"
              [min]="todayStr"
              (ngModelChange)="onCheckInChange($event)"
              required
            >
          </div>

          <!-- Check-out Date -->
          <div class="col-md-3 col-sm-6">
            <label class="form-label text-muted smaller fw-bold mb-1">CHECK-OUT</label>
            <input
              type="date"
              class="form-control form-control-sm rounded-3"
              [(ngModel)]="editCheckOut"
              name="editCheckOut"
              [min]="minCheckOutStr"
              required
            >
          </div>

          <!-- Guests Steppers -->
          <div class="col-md-3 col-sm-6">
            <label class="form-label text-muted smaller fw-bold mb-1">GUESTS</label>
            <div class="d-flex align-items-center gap-2">
              <div class="input-group input-group-sm">
                <button type="button" class="btn btn-outline-secondary" [disabled]="editAdults <= 1" (click)="editAdults = editAdults - 1">-</button>
                <span class="input-group-text bg-white px-2 small">{{ editAdults }} Ad</span>
                <button type="button" class="btn btn-outline-secondary" [disabled]="editAdults >= 16" (click)="editAdults = editAdults + 1">+</button>
              </div>
              <div class="input-group input-group-sm">
                <button type="button" class="btn btn-outline-secondary" [disabled]="editChildren <= 0" (click)="editChildren = editChildren - 1">-</button>
                <span class="input-group-text bg-white px-2 small">{{ editChildren }} Ch</span>
                <button type="button" class="btn btn-outline-secondary" [disabled]="editChildren >= 10" (click)="editChildren = editChildren + 1">+</button>
              </div>
            </div>
          </div>

          <!-- Rooms Stepper & Apply -->
          <div class="col-md-3 col-sm-6 d-flex align-items-end gap-2">
            <div class="flex-grow-1">
              <label class="form-label text-muted smaller fw-bold mb-1">ROOMS</label>
              <div class="input-group input-group-sm">
                <button type="button" class="btn btn-outline-secondary" [disabled]="editRooms <= 1" (click)="editRooms = editRooms - 1">-</button>
                <span class="input-group-text bg-white px-2 small flex-grow-1 text-center justify-content-center">{{ editRooms }}</span>
                <button type="button" class="btn btn-outline-secondary" [disabled]="editRooms >= 10" (click)="editRooms = editRooms + 1">+</button>
              </div>
            </div>

            <button type="submit" class="btn btn-sm btn-teal rounded-pill px-3 fw-bold text-nowrap">
              Update
            </button>
          </div>

        </form>

        <div *ngIf="validationError" class="alert alert-warning py-1 px-3 mt-2 mb-0 smaller rounded-3">
          <i class="fa-solid fa-circle-exclamation me-1"></i> {{ validationError }}
        </div>
      </div>
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
      font-size: 0.75rem;
    }

    .strip-divider {
      width: 1px;
      height: 28px;
      background-color: #E2E8F0;
    }

    .btn-outline-teal {
      color: #0891B2;
      border-color: #0891B2;
      background-color: transparent;
      font-size: 0.85rem;
      transition: all 0.2s ease;
    }

    .btn-outline-teal:hover {
      background-color: #0891B2;
      color: #FFFFFF;
    }

    .btn-teal {
      background-color: #0891B2;
      border-color: #0891B2;
      color: #FFFFFF;
    }

    .btn-teal:hover {
      background-color: #0E7490;
      border-color: #0E7490;
      color: #FFFFFF;
    }
  `]
})
export class HotelSearchContextStripComponent {
  @Input() city = '';
  @Input() checkIn = '';
  @Input() checkOut = '';
  @Input() rooms = 1;
  @Input() adults = 1;
  @Input() children = 0;

  @Output() searchCriteriaChange = new EventEmitter<HotelSearchCriteria>();

  isModifying = false;
  editCheckIn = '';
  editCheckOut = '';
  editRooms = 1;
  editAdults = 1;
  editChildren = 0;
  todayStr = '';
  minCheckOutStr = '';
  validationError = '';

  constructor() {
    const today = new Date();
    this.todayStr = this.formatDateISO(today);
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

  get roomsSummary(): string {
    return this.rooms === 1 ? '1 Room' : `${this.rooms} Rooms`;
  }

  toggleModify(): void {
    this.isModifying = !this.isModifying;
    if (this.isModifying) {
      this.editCheckIn = this.checkIn;
      this.editCheckOut = this.checkOut;
      this.editRooms = this.rooms || 1;
      this.editAdults = this.adults || 1;
      this.editChildren = this.children || 0;
      this.updateMinCheckOut();
      this.validationError = '';
    }
  }

  onCheckInChange(newCheckIn: string): void {
    this.editCheckIn = newCheckIn;
    this.updateMinCheckOut();
    if (this.editCheckIn && (!this.editCheckOut || this.editCheckOut <= this.editCheckIn)) {
      const next = this.parseISODate(this.editCheckIn);
      next.setDate(next.getDate() + 1);
      this.editCheckOut = this.formatDateISO(next);
    }
  }

  private updateMinCheckOut(): void {
    if (this.editCheckIn) {
      const d = this.parseISODate(this.editCheckIn);
      d.setDate(d.getDate() + 1);
      this.minCheckOutStr = this.formatDateISO(d);
    } else {
      this.minCheckOutStr = this.todayStr;
    }
  }

  onApplyChanges(): void {
    this.validationError = '';
    if (!this.editCheckIn || !this.editCheckOut) {
      this.validationError = 'Please select valid check-in and check-out dates.';
      return;
    }
    if (this.editCheckOut <= this.editCheckIn) {
      this.validationError = 'Check-out date must be after check-in date.';
      return;
    }
    if (this.editAdults < 1) {
      this.validationError = 'At least 1 adult is required.';
      return;
    }
    if (this.editRooms < 1) {
      this.validationError = 'At least 1 room is required.';
      return;
    }

    this.isModifying = false;
    this.searchCriteriaChange.emit({
      city: this.city,
      checkIn: this.editCheckIn,
      checkOut: this.editCheckOut,
      rooms: this.editRooms,
      adults: this.editAdults,
      children: this.editChildren,
      guests: this.editAdults + this.editChildren
    });
  }

  private formatDisplayDate(isoDate: string): string {
    if (!isoDate) return '';
    try {
      const d = this.parseISODate(isoDate);
      return d.toLocaleDateString('en-GB', {
        weekday: 'short',
        day: 'numeric',
        month: 'short',
        year: 'numeric'
      });
    } catch {
      return isoDate;
    }
  }

  private parseISODate(isoStr: string): Date {
    const parts = (isoStr || '').split('-');
    if (parts.length === 3) {
      return new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10), 12, 0, 0);
    }
    return new Date();
  }

  private formatDateISO(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
