import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReservationReviewSummaryMetrics } from '../../../../../models/hotel.model';

@Component({
  selector: 'app-reservation-summary-panel',
  standalone: true,
  imports: [CommonModule],
  template: `
    <aside class="reservation-summary-card bg-white rounded-4 border shadow-sm p-4 sticky-summary-card" aria-label="Reservation Summary">
      <!-- Card Header -->
      <div class="d-flex align-items-center gap-2 mb-3">
        <i class="fa-regular fa-calendar-check text-teal fs-5" aria-hidden="true"></i>
        <h2 class="summary-card-title fw-bold text-navy mb-0">Reservation Summary</h2>
      </div>

      <!-- SECTION 1: Room Details -->
      <div class="mb-3">
        <div class="summary-section-bar rounded-3 px-3 py-2 d-flex align-items-center gap-2 mb-2">
          <i class="fa-regular fa-calendar text-teal small" aria-hidden="true"></i>
          <h3 class="summary-section-heading fw-bold text-navy mb-0">Room Details</h3>
        </div>

        <div class="d-flex flex-column gap-2 px-1 mb-3">
          <div
            *ngFor="let row of metrics.roomTypeRows"
            class="d-flex justify-content-between align-items-center gap-2 small"
          >
            <span class="text-navy fw-medium text-truncate">
              {{ row.roomTypeName }} &times; {{ row.quantity }}
            </span>
            <span class="fw-bold text-navy text-nowrap" [attr.data-testid]="'summary-room-subtotal-' + row.roomTypeId">
              ₹{{ row.subtotalAmount | number:'1.0-0' }}
            </span>
          </div>
        </div>

        <div class="estimated-total-box rounded-3 px-3 py-3 d-flex justify-content-between align-items-center gap-2">
          <div class="text-navy fw-bold small-md">
            Total Amount <span class="fw-normal text-muted small">(Estimated)</span>
          </div>
          <div class="estimated-total-value fw-bold text-navy text-nowrap" data-testid="summary-estimated-total">
            ₹{{ metrics.estimatedTotalAmount | number:'1.0-0' }}
          </div>
        </div>
      </div>

      <!-- SECTION 2: Stay Details -->
      <div class="mb-3">
        <div class="summary-section-bar rounded-3 px-3 py-2 d-flex align-items-center gap-2 mb-2">
          <i class="fa-regular fa-calendar text-teal small" aria-hidden="true"></i>
          <h3 class="summary-section-heading fw-bold text-navy mb-0">Stay Details</h3>
        </div>

        <div class="d-flex flex-column gap-2 px-1 small">
          <div class="d-flex justify-content-between align-items-center">
            <span class="text-muted">Check-in</span>
            <span class="fw-semibold text-navy" data-testid="summary-checkin">{{ formattedCheckIn }}</span>
          </div>
          <div class="d-flex justify-content-between align-items-center">
            <span class="text-muted">Check-out</span>
            <span class="fw-semibold text-navy" data-testid="summary-checkout">{{ formattedCheckOut }}</span>
          </div>
          <div class="d-flex justify-content-between align-items-center">
            <span class="text-muted">Total Nights</span>
            <span class="fw-semibold text-navy" data-testid="summary-nights">
              {{ totalNights }} {{ totalNights === 1 ? 'Night' : 'Nights' }}
            </span>
          </div>
        </div>
      </div>

      <!-- SECTION 3: Guests & Rooms -->
      <div>
        <div class="summary-section-bar rounded-3 px-3 py-2 d-flex align-items-center gap-2 mb-2">
          <i class="fa-solid fa-user-group text-teal small" aria-hidden="true"></i>
          <h3 class="summary-section-heading fw-bold text-navy mb-0">Guests &amp; Rooms</h3>
        </div>

        <div class="d-flex flex-column gap-2 px-1 small">
          <div class="d-flex justify-content-between align-items-center">
            <span class="text-muted">Total Rooms</span>
            <span class="fw-semibold text-navy" data-testid="summary-total-rooms">{{ metrics.totalRooms }}</span>
          </div>
          <div class="d-flex justify-content-between align-items-center">
            <span class="text-muted">Adults</span>
            <span class="fw-semibold text-navy" data-testid="summary-total-adults">{{ metrics.totalAdults }}</span>
          </div>
          <div class="d-flex justify-content-between align-items-center">
            <span class="text-muted">Children</span>
            <span class="fw-semibold text-navy" data-testid="summary-total-children">{{ metrics.totalChildren }}</span>
          </div>
          <div class="d-flex justify-content-between align-items-center pt-1 border-top">
            <span class="text-navy fw-semibold">Total Guests</span>
            <span class="fw-bold text-navy" data-testid="summary-total-guests">{{ metrics.totalGuests }}</span>
          </div>
        </div>
      </div>
    </aside>
  `,
  styles: [`
    :host {
      display: block;
      min-width: 0;
      max-width: 100%;
    }
    .reservation-summary-card {
      min-width: 0;
      max-width: 100%;
    }
    .text-navy {
      color: #0F172A;
    }
    .text-teal {
      color: #0891B2;
    }
    .sticky-summary-card {
      top: 84px;
    }
    .summary-card-title {
      font-size: 1.15rem;
    }
    .summary-section-bar {
      background-color: #EEF6FF;
    }
    .summary-section-heading {
      font-size: 0.9rem;
    }
    .estimated-total-box {
      background: linear-gradient(90deg, #DDF6EF 0%, #E6F7F9 100%);
      border: 1px solid #BFECE1;
    }
    .estimated-total-value {
      font-size: 1.4rem;
      line-height: 1.2;
    }
    .small-md {
      font-size: 0.95rem;
    }
  `]
})
export class ReservationSummaryPanelComponent {
  @Input({ required: true }) metrics!: ReservationReviewSummaryMetrics;
  @Input({ required: true }) formattedCheckIn = '';
  @Input({ required: true }) formattedCheckOut = '';
  @Input({ required: true }) totalNights = 1;
}
