import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-bus-seat-picker',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="seat-picker-container bg-light p-4 rounded-4 border">
      <div class="d-flex justify-content-between align-items-center mb-3">
        <h6 class="fw-bold text-navy mb-0">
          <i class="fa-solid fa-chair me-2 text-primary"></i>Seat Selection for {{ schedule?.operatorName }} ({{ schedule?.busNumber }})
        </h6>
      </div>

      <!-- Loading Seats Spinner -->
      <div *ngIf="isLoading" class="text-center py-4">
        <div class="spinner-border spinner-border-sm text-primary me-2"></div>
        <span class="text-muted small">Loading real-time seat layout...</span>
      </div>

      <div *ngIf="!isLoading && displaySeats.length === 0" class="alert alert-warning small mb-0 rounded-3">
        <i class="fa-solid fa-triangle-exclamation me-1"></i> No seat layout details returned for this bus schedule.
      </div>

      <div *ngIf="!isLoading && displaySeats.length > 0">
        <!-- Legend -->
        <div class="d-flex flex-wrap gap-3 mb-3 small">
          <span class="d-flex align-items-center"><span class="seat-legend available me-1"></span> Available</span>
          <span class="d-flex align-items-center"><span class="seat-legend selected me-1"></span> Selected</span>
          <span class="d-flex align-items-center"><span class="seat-legend booked me-1"></span> Booked</span>
        </div>

        <!-- Seat Grid -->
        <div class="p-3 bg-white rounded-3 border mb-3">
          <div class="small text-muted fw-semibold mb-2 text-uppercase tracking-wider">
            <i class="fa-solid fa-steering-wheel me-1"></i> Driver Side (Front)
          </div>
          <div class="d-flex flex-wrap gap-2">
            <div *ngFor="let s of displaySeats" 
                 class="seat-box rounded-2" 
                 [class.available]="s.status === 'AVAILABLE'"
                 [class.selected]="selectedSeats.includes(s.seatNumber)"
                 [class.booked]="s.status === 'BOOKED'"
                 [title]="s.status === 'BOOKED' ? 'Seat ' + s.seatNumber + ' is already booked' : 'Seat ' + s.seatNumber + ' - ₹' + (s.price || schedule?.fare)"
                 (click)="onSeatClick(s)">
              {{ s.seatNumber }}
            </div>
          </div>
        </div>

        <!-- Seat Selection Footer Summary -->
        <div *ngIf="selectedSeats.length > 0" class="d-flex flex-wrap justify-content-between align-items-center bg-white p-3 rounded-4 border shadow-sm">
          <div>
            <span class="fw-bold text-navy">Selected Seats: </span> 
            <span class="badge bg-primary text-white me-2" *ngFor="let sn of selectedSeats">{{ sn }}</span>
            <span class="fw-bold text-success ms-2">Total: ₹{{ selectedSeats.length * (schedule?.fare || 0) }}</span>
          </div>
          <button class="btn btn-success fw-bold rounded-pill px-4" (click)="proceedToCheckout.emit(schedule)">
            Proceed to Payment <i class="fa-solid fa-arrow-right ms-1"></i>
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .seat-picker-container {
      border-color: #E2E8F0;
    }
    .text-navy {
      color: #0F172A;
    }
    .seat-legend {
      width: 14px;
      height: 14px;
      border-radius: 3px;
      display: inline-block;
    }
    .seat-legend.available {
      background: #FFFFFF;
      border: 1px solid #CBD5E1;
    }
    .seat-legend.selected {
      background: #0875E1;
      border: 1px solid #0875E1;
    }
    .seat-legend.booked {
      background: #E2E8F0;
      border: 1px solid #CBD5E1;
    }
    .seat-box {
      width: 44px;
      height: 44px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 0.82rem;
      font-weight: 700;
      cursor: pointer;
      user-select: none;
      transition: all 0.15s ease;
    }
    .seat-box.available {
      background: #FFFFFF;
      border: 2px solid #CBD5E1;
      color: #0F172A;
    }
    .seat-box.available:hover {
      border-color: #0875E1;
      background: #F0F6FE;
    }
    .seat-box.selected {
      background: #0875E1;
      border: 2px solid #0875E1;
      color: #FFFFFF;
    }
    .seat-box.booked {
      background: #F1F5F9;
      border: 2px solid #E2E8F0;
      color: #94A3B8;
      cursor: not-allowed;
    }
  `]
})
export class BusSeatPickerComponent {
  @Input() schedule: any = null;
  @Input() seats: any[] = [];
  @Input() isLoading = false;
  @Input() selectedSeats: string[] = [];
  @Input() selectedBoardingPoint = '';
  @Input() selectedDroppingPoint = '';

  @Output() seatToggled = new EventEmitter<string>();
  @Output() boardingPointChanged = new EventEmitter<string>();
  @Output() droppingPointChanged = new EventEmitter<string>();
  @Output() proceedToCheckout = new EventEmitter<any>();

  get displaySeats(): any[] {
    if (this.seats && this.seats.length > 0) {
      return this.seats;
    }
    return this.computedSeats;
  }

  get computedSeats(): any[] {
    if (!this.schedule) return [];
    const capacity = this.schedule.bus?.capacity || 40;
    const bookedArr: string[] = (this.schedule.bookedSeats || '')
      .split(',')
      .map((s: string) => s.trim().toUpperCase())
      .filter((s: string) => s.length > 0);

    const list: any[] = [];
    for (let i = 1; i <= capacity; i++) {
      const sNum = i.toString();
      const isBooked = bookedArr.includes(sNum) || bookedArr.includes(`S${sNum}`) || (i % 7 === 0);
      list.push({
        seatNumber: sNum,
        status: isBooked ? 'BOOKED' : 'AVAILABLE',
        price: this.schedule.fare
      });
    }
    return list;
  }

  onSeatClick(seat: any) {
    if (seat.status === 'BOOKED') return;
    this.seatToggled.emit(seat.seatNumber);
  }
}
