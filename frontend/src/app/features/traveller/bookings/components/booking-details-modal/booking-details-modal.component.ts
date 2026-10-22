import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-booking-details-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <!-- VIEW BOOKING DETAILS MODAL -->
    <div *ngIf="showDetailsModal && booking" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
      <div class="modal-dialog modal-dialog-centered modal-lg">
        <div class="modal-content rounded-4 border-0 shadow-lg">
          <div class="modal-header bg-white border-bottom px-4 py-3">
            <div class="d-flex align-items-center gap-2">
              <span [class]="booking.serviceType === 'BUS' ? 'badge-type-bus' : 'badge-type-hotel'">
                {{ booking.serviceType === 'BUS' ? 'Bus' : 'Hotel' }}
              </span>
              <h5 class="modal-title fw-bold text-navy mb-0">Booking Details</h5>
            </div>
            <button type="button" class="btn-close shadow-none" (click)="closeDetails.emit()"></button>
          </div>
          <div class="modal-body p-4">
            <div class="d-flex justify-content-between align-items-start mb-4 pb-3 border-bottom flex-wrap gap-2">
              <div>
                <h4 class="fw-bold text-navy mb-1">{{ booking.displayTitle }}</h4>
                <div class="text-secondary small mb-2">{{ booking.displaySubtitle }}</div>
                <div class="badge-status-pill" [ngClass]="booking.statusBadgeClass">
                  {{ booking.statusDisplay }}
                </div>
              </div>
              <div class="text-end">
                <div class="text-secondary small">Total Fare Paid</div>
                <div class="fs-3 fw-bold text-primary">₹{{ booking.totalAmount | number }}</div>
              </div>
            </div>

            <div class="row g-3 mb-4">
              <div class="col-sm-6">
                <div class="p-3 bg-light rounded-3">
                  <div class="text-secondary smaller text-uppercase fw-bold mb-1">Booking Reference</div>
                  <div class="fw-bold text-dark fs-6">{{ booking.bookingRef }}</div>
                </div>
              </div>
              <div class="col-sm-6">
                <div class="p-3 bg-light rounded-3">
                  <div class="text-secondary smaller text-uppercase fw-bold mb-1">Date & Schedule</div>
                  <div class="fw-bold text-dark fs-6">{{ booking.dateText || 'Standard Booking' }}</div>
                </div>
              </div>
              <div class="col-sm-6">
                <div class="p-3 bg-light rounded-3">
                  <div class="text-secondary smaller text-uppercase fw-bold mb-1">Capacity / Reserved</div>
                  <div class="fw-bold text-dark fs-6">{{ booking.capacityText }}</div>
                </div>
              </div>
              <div class="col-sm-6">
                <div class="p-3 bg-light rounded-3">
                  <div class="text-secondary smaller text-uppercase fw-bold mb-1">Booked On</div>
                  <div class="fw-bold text-dark fs-6">{{ booking.rawBookingDate | date:'medium' }}</div>
                </div>
              </div>
            </div>

            <div class="p-3 bg-light rounded-3 mb-2" *ngIf="booking.detailsJson">
              <div class="text-secondary smaller text-uppercase fw-bold mb-1">Reservation Specs & Notes</div>
              <div class="text-dark small">{{ booking.detailsJson }}</div>
            </div>
          </div>
          <div class="modal-footer bg-light border-top px-4 py-3 d-flex justify-content-between">
            <div>
              <button 
                *ngIf="booking.isCancellable" 
                class="btn btn-cancel-booking"
                (click)="promptCancel.emit(booking)"
              >
                <i class="fa-solid fa-ban me-1"></i> Cancel Booking
              </button>
              <button 
                *ngIf="booking.isCompleted" 
                class="btn btn-outline-primary btn-sm rounded-3 px-3 py-1 fw-semibold"
                (click)="rateReview.emit(booking.raw)"
              >
                <i class="fa-solid fa-star me-1"></i> Rate & Review
              </button>
            </div>
            <button type="button" class="btn btn-secondary rounded-3 px-4" (click)="closeDetails.emit()">Close</button>
          </div>
        </div>
      </div>
    </div>

    <!-- CANCEL BOOKING CONFIRMATION MODAL -->
    <div *ngIf="showCancelModal && bookingToCancel" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content rounded-4 border-0 shadow-lg">
          <div class="modal-header border-0 pb-0">
            <button type="button" class="btn-close shadow-none" [disabled]="isCancelling" (click)="closeCancel.emit()"></button>
          </div>
          <div class="modal-body p-4 pt-0 text-center">
            <div class="d-inline-flex align-items-center justify-content-center rounded-circle bg-danger bg-opacity-10 text-danger mx-auto mb-3" style="width: 60px; height: 60px;">
              <i class="fa-solid fa-triangle-exclamation fs-3"></i>
            </div>
            <h5 class="fw-bold text-navy mb-2">Cancel This Booking?</h5>
            <p class="text-secondary small mb-3">
              Are you sure you want to cancel booking <strong>{{ bookingToCancel.bookingRef }}</strong> for <strong>{{ bookingToCancel.displayTitle || bookingToCancel.serviceTitle }}</strong>?
            </p>
            <div class="alert alert-warning py-2 px-3 rounded-3 text-start smaller text-dark mb-4">
              <i class="fa-solid fa-circle-info text-warning me-2"></i>
              This action will release your reserved seats/rooms immediately and set this booking to cancelled. This cannot be undone.
            </div>
            <div class="d-flex justify-content-center gap-3">
              <button 
                type="button" 
                class="btn btn-light rounded-pill px-4 fw-semibold" 
                [disabled]="isCancelling" 
                (click)="closeCancel.emit()"
              >
                Keep Booking
              </button>
              <button 
                type="button" 
                class="btn btn-danger rounded-pill px-4 fw-bold d-flex align-items-center gap-2" 
                [disabled]="isCancelling" 
                (click)="confirmCancel.emit(bookingToCancel)"
              >
                <span *ngIf="isCancelling" class="spinner-border spinner-border-sm"></span>
                <span>{{ isCancelling ? 'Cancelling...' : 'Yes, Cancel Booking' }}</span>
              </button>
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
    .badge-type-bus {
      background: #E0F2FE;
      color: #0284C7;
      font-size: 0.72rem;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 6px;
    }
    .badge-type-hotel {
      background: #EDE9FE;
      color: #7C3AED;
      font-size: 0.72rem;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 6px;
    }
    .badge-status-pill {
      font-size: 0.75rem;
      font-weight: 700;
      padding: 4px 10px;
      border-radius: 50px;
      display: inline-block;
    }
    .btn-cancel-booking {
      background: transparent;
      color: #DC2626;
      font-size: 0.82rem;
      font-weight: 600;
      padding: 6px 12px;
      border-radius: 8px;
      border: 1px solid #FCA5A5;
      transition: all 0.15s ease;
    }
    .btn-cancel-booking:hover {
      background: #FEF2F2;
    }
  `]
})
export class BookingDetailsModalComponent {
  @Input() showDetailsModal = false;
  @Input() booking: any = null;
  @Input() showCancelModal = false;
  @Input() bookingToCancel: any = null;
  @Input() isCancelling = false;

  @Output() closeDetails = new EventEmitter<void>();
  @Output() promptCancel = new EventEmitter<any>();
  @Output() closeCancel = new EventEmitter<void>();
  @Output() confirmCancel = new EventEmitter<any>();
  @Output() rateReview = new EventEmitter<any>();
}
