import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-hotel-search-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="search-form-card bg-white p-4 rounded-4 shadow-sm border mb-4">
      <h4 class="fw-bold text-navy mb-3"><i class="fa-solid fa-hotel text-primary me-2"></i>Search Hotels</h4>
      <form (ngSubmit)="search.emit()" class="row g-3 align-items-end">
        <div class="col-lg-4 col-md-6">
          <label class="form-label fw-semibold text-muted small">Destination City</label>
          <input 
            type="text" 
            class="form-control rounded-3" 
            [(ngModel)]="city" 
            (ngModelChange)="cityChange.emit(city)"
            name="hotelCity" 
            placeholder="City or Hotel Name"
            required
          >
        </div>
        <div class="col-lg-3 col-md-6">
          <label class="form-label fw-semibold text-muted small">Check-in</label>
          <input 
            type="date" 
            class="form-control rounded-3" 
            [(ngModel)]="checkIn" 
            (ngModelChange)="checkInChange.emit(checkIn)"
            name="checkInDate"
            required
          >
        </div>
        <div class="col-lg-3 col-md-6">
          <label class="form-label fw-semibold text-muted small">Check-out</label>
          <input 
            type="date" 
            class="form-control rounded-3" 
            [(ngModel)]="checkOut" 
            (ngModelChange)="checkOutChange.emit(checkOut)"
            name="checkOutDate"
            required
          >
        </div>
        <div class="col-lg-2 col-md-6">
          <button type="submit" class="btn btn-primary w-100 rounded-3 py-2 fw-bold" [disabled]="isSearching">
            <span *ngIf="isSearching" class="spinner-border spinner-border-sm me-1"></span>
            <i *ngIf="!isSearching" class="fa-solid fa-magnifying-glass me-1"></i> 
            {{ isSearching ? 'Searching...' : 'Search Hotels' }}
          </button>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .text-navy {
      color: #0F172A;
    }
    .search-form-card {
      border-color: #E2E8F0;
    }
  `]
})
export class HotelSearchPanelComponent {
  @Input() city = '';
  @Input() checkIn = '';
  @Input() checkOut = '';
  @Input() isSearching = false;

  @Output() cityChange = new EventEmitter<string>();
  @Output() checkInChange = new EventEmitter<string>();
  @Output() checkOutChange = new EventEmitter<string>();
  @Output() search = new EventEmitter<void>();
}
