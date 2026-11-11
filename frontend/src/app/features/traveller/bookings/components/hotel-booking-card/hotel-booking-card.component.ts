import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { DEFAULT_HOTEL_PLACEHOLDER } from '../../../../../shared/utils/image-url.util';
import { HotelBookingCardViewModel } from '../../models/hotel-booking-view.model';

@Component({
  selector: 'app-hotel-booking-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <article
      *ngIf="booking"
      class="hotel-booking-card mb-3"
      [attr.data-booking-ref]="booking.bookingRef"
      [attr.data-status]="booking.status"
    >
      <div class="hotel-card-grid">
        <!-- Left Cluster: Hotel Photo + Core Stay Details -->
        <div class="hotel-card-main">
          <div class="hotel-thumb-wrap">
            <img
              [src]="booking.thumbnail"
              (error)="onImgError($event)"
              class="hotel-card-thumb"
              [alt]="booking.hotelName + ' thumbnail'"
              loading="lazy"
            />
          </div>

          <div class="hotel-card-info">
            <div class="mb-1">
              <span class="hotel-type-badge">
                <i class="fa-solid fa-bed" aria-hidden="true"></i>
                <span>Hotel</span>
              </span>
            </div>

            <h5 class="hotel-card-title mb-1">
              <button
                type="button"
                class="hotel-title-trigger"
                (click)="openDetails.emit(booking)"
              >
                {{ booking.hotelName }}
              </button>
            </h5>

            <div *ngIf="booking.locationText" class="hotel-meta-line hotel-location-line">
              <i class="fa-solid fa-location-dot hotel-meta-icon hotel-icon-pin" aria-hidden="true"></i>
              <span class="text-truncate" [title]="booking.locationText">{{ booking.locationText }}</span>
            </div>

            <div class="hotel-meta-line hotel-dates-line">
              <i class="fa-regular fa-calendar hotel-meta-icon" aria-hidden="true"></i>
              <span class="hotel-dates-text">{{ booking.stayDatesText }}</span>
              <span *ngIf="booking.nightsText" class="hotel-nights-text">({{ booking.nightsText }})</span>
            </div>

            <div
              *ngIf="booking.roomTypesSummary || booking.roomsCountLabel || booking.guestsSummaryLabel"
              class="hotel-meta-line hotel-occupancy-line"
            >
              <span *ngIf="booking.roomTypesSummary || booking.roomsCountLabel" class="hotel-occupancy-group">
                <i class="fa-solid fa-bed hotel-meta-icon" aria-hidden="true"></i>
                <span *ngIf="booking.roomTypesSummary" class="fw-medium">{{ booking.roomTypesSummary }}</span>
                <span *ngIf="booking.roomTypesSummary && booking.roomsCountLabel" class="hotel-dot-sep" aria-hidden="true">•</span>
                <span *ngIf="booking.roomsCountLabel">{{ booking.roomsCountLabel }}</span>
              </span>

              <span *ngIf="booking.guestsSummaryLabel" class="hotel-occupancy-group">
                <i class="fa-solid fa-user-group hotel-meta-icon" aria-hidden="true"></i>
                <span>{{ booking.guestsSummaryLabel }}</span>
              </span>
            </div>
          </div>
        </div>

        <!-- Middle Cluster: Vertical Divider + Booking ID & Total Paid -->
        <div class="hotel-card-middle">
          <div class="hotel-summary-pill-box">
            <div class="summary-metric">
              <span class="summary-metric-label">Booking ID</span>
              <span class="summary-metric-code">{{ booking.bookingRef }}</span>
            </div>
            <div class="summary-metric">
              <span class="summary-metric-label">Total Paid</span>
              <span class="summary-metric-amount">{{ booking.formattedTotalPaid }}</span>
            </div>
          </div>
        </div>

        <!-- Right Cluster: Status Badge, Refund Indicator & Actions -->
        <div class="hotel-card-actions">
          <div class="status-chevron-row">
            <div class="status-badges-group">
              <span class="hotel-status-pill" [ngClass]="booking.statusBadgeClass">
                {{ booking.statusDisplay }}
              </span>
              <span
                *ngIf="booking.refundBadge as rb"
                class="hotel-refund-pill"
                [ngClass]="rb.badgeClass"
              >
                <i [class]="rb.iconClass" aria-hidden="true"></i>
                <span>{{ rb.label }}</span>
              </span>
            </div>

            <button
              type="button"
              class="hotel-chevron-btn"
              (click)="openDetails.emit(booking)"
              aria-label="Open hotel booking details"
              title="View Details"
            >
              <i class="fa-solid fa-chevron-right" aria-hidden="true"></i>
            </button>
          </div>

          <div class="card-cta-row">
            <button
              type="button"
              class="btn-hotel-view-details"
              (click)="openDetails.emit(booking)"
            >
              View Details
            </button>

            <button
              *ngIf="booking.isCancellable"
              type="button"
              class="btn-hotel-cancel"
              (click)="cancelBooking.emit(booking)"
            >
              Cancel Booking
            </button>

            <button
              *ngIf="booking.isCompleted"
              type="button"
              class="btn-hotel-review"
              (click)="rateReview.emit(booking.raw)"
            >
              Rate &amp; Review
            </button>
          </div>
        </div>
      </div>
    </article>
  `,
  styles: [`
    .hotel-booking-card {
      container-type: inline-size;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      padding: 16px 20px;
      box-shadow: 0 2px 10px rgba(15, 23, 42, 0.04);
      transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
    }

    .hotel-booking-card:hover {
      border-color: #cbd5e1;
      box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
    }

    .hotel-card-grid {
      display: grid;
      grid-template-columns: minmax(0, 1.45fr) minmax(290px, 0.9fr) minmax(230px, auto);
      align-items: center;
      gap: 20px;
    }

    /* Left Cluster */
    .hotel-card-main {
      display: flex;
      align-items: center;
      gap: 16px;
      min-inline-size: 0;
    }

    .hotel-thumb-wrap {
      flex: 0 0 auto;
      inline-size: 126px;
      block-size: 94px;
      border-radius: 10px;
      overflow: hidden;
      background: #f1f5f9;
      border: 1px solid #e2e8f0;
    }

    .hotel-card-thumb {
      display: block;
      inline-size: 100%;
      block-size: 100%;
      object-fit: cover;
    }

    .hotel-card-info {
      flex: 1 1 auto;
      min-inline-size: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .hotel-type-badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 2px 10px;
      border-radius: 999px;
      background: #dbeafe;
      color: #0875e1;
      font-size: 0.72rem;
      font-weight: 700;
      line-height: 1.4;
    }

    .hotel-card-title {
      font-size: 1.08rem;
      font-weight: 700;
      color: #0f172a;
      line-height: 1.3;
      margin: 0;
    }

    .hotel-title-trigger {
      appearance: none;
      background: transparent;
      border: 0;
      padding: 0;
      color: inherit;
      font: inherit;
      text-align: start;
      cursor: pointer;
      max-inline-size: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      display: inline-block;
    }

    .hotel-title-trigger:hover {
      color: #0875e1;
      text-decoration: underline;
    }

    .hotel-meta-line {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 6px;
      font-size: 0.83rem;
      color: #475569;
      min-inline-size: 0;
    }

    .hotel-meta-icon {
      color: #64748b;
      font-size: 0.78rem;
      flex-shrink: 0;
    }

    .hotel-icon-pin {
      color: #475569;
    }

    .hotel-dates-text {
      font-weight: 600;
      color: #1e293b;
    }

    .hotel-nights-text {
      color: #64748b;
      font-weight: 500;
    }

    .hotel-occupancy-line {
      gap: 14px;
      font-size: 0.82rem;
    }

    .hotel-occupancy-group {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }

    .hotel-dot-sep {
      color: #94a3b8;
    }

    /* Middle Cluster */
    .hotel-card-middle {
      display: flex;
      align-items: center;
      border-inline-start: 1px solid #e2e8f0;
      padding-inline-start: 20px;
    }

    .hotel-summary-pill-box {
      inline-size: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 18px;
      background: #f8fafc;
      border: 1px solid #f1f5f9;
      border-radius: 10px;
      padding: 10px 16px;
    }

    .summary-metric {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .summary-metric-label {
      font-size: 0.72rem;
      font-weight: 500;
      color: #64748b;
    }

    .summary-metric-code {
      font-size: 0.84rem;
      font-weight: 700;
      color: #0f172a;
      letter-spacing: 0.01em;
      white-space: nowrap;
    }

    .summary-metric-amount {
      font-size: 1.18rem;
      font-weight: 800;
      color: #0875e1;
      line-height: 1.2;
      white-space: nowrap;
    }

    /* Right Cluster */
    .hotel-card-actions {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      justify-content: space-between;
      gap: 12px;
    }

    .status-chevron-row {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 10px;
    }

    .status-badges-group {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 6px;
    }

    .hotel-status-pill,
    .hotel-refund-pill {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 4px 12px;
      border-radius: 999px;
      font-size: 0.76rem;
      font-weight: 700;
      line-height: 1.3;
      white-space: nowrap;
    }

    .hotel-status-confirmed {
      background: #dcfce7;
      color: #15803d;
    }

    .hotel-status-cancelled {
      background: #fee2e2;
      color: #dc2626;
    }

    .hotel-status-completed {
      background: #dbeafe;
      color: #1d4ed8;
    }

    .hotel-status-checked-in {
      background: #ccfbf1;
      color: #0f766e;
    }

    .hotel-status-checked-out {
      background: #f1f5f9;
      color: #334155;
    }

    .hotel-status-no-show {
      background: #fef3c7;
      color: #b45309;
    }

    .hotel-refund-pill-pending {
      background: #e0f2fe;
      color: #0369a1;
    }

    .hotel-refund-pill-processed {
      background: #ecfdf5;
      color: #047857;
    }

    .hotel-refund-pill-failed {
      background: #fff1f2;
      color: #be123c;
    }

    .hotel-chevron-btn {
      appearance: none;
      background: transparent;
      border: 0;
      color: #334155;
      padding: 4px 6px;
      border-radius: 6px;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 0.85rem;
      transition: background-color 0.15s ease, color 0.15s ease;
    }

    .hotel-chevron-btn:hover {
      background: #f1f5f9;
      color: #0875e1;
    }

    .card-cta-row {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 8px;
    }

    .btn-hotel-view-details {
      background: #eff6ff;
      color: #0f172a;
      border: 1px solid #dbeafe;
      border-radius: 8px;
      padding: 6px 14px;
      font-size: 0.82rem;
      font-weight: 600;
      cursor: pointer;
      transition: background-color 0.15s ease, border-color 0.15s ease;
    }

    .btn-hotel-view-details:hover {
      background: #dbeafe;
      border-color: #93c5fd;
    }

    .btn-hotel-cancel {
      background: #fff5f5;
      color: #dc2626;
      border: 1px solid #fca5a5;
      border-radius: 999px;
      padding: 6px 14px;
      font-size: 0.82rem;
      font-weight: 600;
      cursor: pointer;
      transition: background-color 0.15s ease, color 0.15s ease;
    }

    .btn-hotel-cancel:hover {
      background: #fee2e2;
    }

    .btn-hotel-review {
      background: #ffffff;
      color: #0875e1;
      border: 1px solid #bfdbfe;
      border-radius: 8px;
      padding: 6px 14px;
      font-size: 0.82rem;
      font-weight: 600;
      cursor: pointer;
    }

    .btn-hotel-review:hover {
      background: #eff6ff;
    }

    @container (width < 880px) {
      .hotel-card-grid {
        grid-template-columns: minmax(0, 1fr);
        gap: 14px;
      }

      .hotel-card-middle {
        border-inline-start: 0;
        border-block-start: 1px solid #f1f5f9;
        padding-inline-start: 0;
        padding-block-start: 12px;
      }

      .hotel-summary-pill-box {
        inline-size: 100%;
        justify-content: space-between;
      }

      .hotel-card-actions {
        flex-direction: row;
        align-items: center;
        flex-wrap: wrap;
        inline-size: 100%;
        border-block-start: 1px solid #f1f5f9;
        padding-block-start: 12px;
      }

      .status-chevron-row {
        justify-content: flex-start;
      }

      .hotel-chevron-btn {
        display: none;
      }
    }

    @container (width < 520px) {
      .hotel-card-main {
        flex-direction: column;
        align-items: flex-start;
      }

      .hotel-thumb-wrap {
        inline-size: 100%;
        block-size: 148px;
      }

      .hotel-summary-pill-box {
        flex-direction: column;
        align-items: flex-start;
        gap: 10px;
      }

      .card-cta-row {
        inline-size: 100%;
        justify-content: flex-start;
      }
    }
  `]
})
export class HotelBookingCardComponent {
  @Input() booking: HotelBookingCardViewModel | null = null;

  @Output() openDetails = new EventEmitter<HotelBookingCardViewModel>();
  @Output() cancelBooking = new EventEmitter<HotelBookingCardViewModel>();
  @Output() rateReview = new EventEmitter<any>();

  onImgError(event: Event): void {
    const img = event.target as HTMLImageElement | null;
    if (!img || img.dataset['fallbackApplied'] === '1') {
      return;
    }
    img.dataset['fallbackApplied'] = '1';
    img.src = DEFAULT_HOTEL_PLACEHOLDER;
  }
}
