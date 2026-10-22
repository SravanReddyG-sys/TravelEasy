import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BusResultCardComponent } from '../bus-result-card/bus-result-card.component';
import { BusSeatPickerComponent } from '../bus-seat-picker/bus-seat-picker.component';

@Component({
  selector: 'app-bus-results-list',
  standalone: true,
  imports: [CommonModule, FormsModule, BusResultCardComponent, BusSeatPickerComponent],
  template: `
    <div class="bus-results-container">
      <!-- Results Header: Count, Route, Date & Sort By -->
      <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
        <div>
          <div class="fw-bold text-navy fs-6">
            Showing {{ totalFilteredBuses === 0 ? 0 : (currentPage - 1) * pageSize + 1 }}–{{ Math.min(currentPage * pageSize, totalFilteredBuses) }} of {{ totalFilteredBuses }} buses
          </div>
          <div class="text-muted smaller">
            {{ origin }} to {{ destination }} • {{ formattedDate }}
          </div>
        </div>

        <!-- Sort By Control -->
        <div class="d-flex align-items-center gap-2">
          <label class="text-muted smaller fw-semibold text-nowrap mb-0">Sort by:</label>
          <select class="form-select form-select-sm rounded-pill w-auto fw-semibold text-navy bg-white" [(ngModel)]="sortBy" (change)="onSortChange()">
            <option value="depEarliest">Departure: Earliest First</option>
            <option value="depLatest">Departure: Latest First</option>
            <option value="fareLow">Price: Low to High</option>
            <option value="fareHigh">Price: High to Low</option>
            <option value="durationShort">Duration: Shortest First</option>
          </select>
        </div>
      </div>

      <!-- Filtered Empty State -->
      <div *ngIf="buses.length === 0" class="empty-state-card bg-white rounded-4 border p-5 text-center shadow-sm">
        <div class="empty-icon-wrap mx-auto mb-3">
          <i class="fa-solid fa-filter-circle-xmark text-warning fs-2"></i>
        </div>
        <h5 class="fw-bold text-navy mb-2">No buses match your selected filters</h5>
        <p class="text-secondary small mb-4">
          None of the available buses match all your current filter criteria.
        </p>
        <button class="btn btn-primary rounded-pill px-4 fw-bold" (click)="clearFilters.emit()">
          <i class="fa-solid fa-rotate-left me-1"></i> Clear All Filters
        </button>
      </div>

      <!-- Bus Cards List with Seat Picker Expansion -->
      <div *ngFor="let b of buses" class="mb-3">
        <app-bus-result-card 
          [bus]="b" 
          [isSelected]="selectedSchedule?.id === b.id"
          [isSaved]="isBusSaved(b.id)"
          (selectSeats)="onToggleSeats(b)"
          (saveToFavourites)="saveToFavourites.emit($event)"
        ></app-bus-result-card>

        <!-- Seat Picker Accordion -->
        <div *ngIf="selectedSchedule?.id === b.id" class="mt-2">
          <app-bus-seat-picker
            [schedule]="selectedSchedule"
            [seats]="seatList"
            [isLoading]="isLoadingSeats"
            [selectedSeats]="selectedSeatNumbers"
            [selectedBoardingPoint]="selectedBoardingPoint"
            [selectedDroppingPoint]="selectedDroppingPoint"
            (seatToggled)="seatToggled.emit($event)"
            (boardingPointChanged)="boardingPointChanged.emit($event)"
            (droppingPointChanged)="droppingPointChanged.emit($event)"
            (proceedToCheckout)="proceedToCheckout.emit(selectedSchedule)"
          ></app-bus-seat-picker>
        </div>
      </div>

      <!-- Pagination -->
      <div *ngIf="totalPages > 1" class="d-flex justify-content-between align-items-center mt-4 pt-2">
        <div class="text-muted smaller">
          Page {{ currentPage }} of {{ totalPages }}
        </div>
        <div class="d-flex gap-1">
          <button class="btn btn-sm btn-outline-secondary rounded-pill px-3" 
                  [disabled]="currentPage === 1" 
                  (click)="pageChange.emit(currentPage - 1)">
            Previous
          </button>
          <button *ngFor="let p of pageNumbers" 
                  class="btn btn-sm rounded-circle pagination-num-btn" 
                  [class.btn-primary]="p === currentPage"
                  [class.btn-outline-light]="p !== currentPage"
                  [class.text-dark]="p !== currentPage"
                  (click)="pageChange.emit(p)">
            {{ p }}
          </button>
          <button class="btn btn-sm btn-outline-secondary rounded-pill px-3" 
                  [disabled]="currentPage === totalPages" 
                  (click)="pageChange.emit(currentPage + 1)">
            Next
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
    .empty-state-card {
      border-color: #E2E8F0;
    }
    .empty-icon-wrap {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: #FEF3C7;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .pagination-num-btn {
      width: 32px;
      height: 32px;
      padding: 0;
      font-size: 0.8rem;
      font-weight: 600;
    }
  `]
})
export class BusResultsListComponent {
  Math = Math;

  @Input() buses: any[] = [];
  @Input() totalFilteredBuses = 0;
  @Input() origin = '';
  @Input() destination = '';
  @Input() formattedDate = '';
  @Input() sortBy = 'depEarliest';
  @Input() currentPage = 1;
  @Input() pageSize = 5;
  @Input() selectedSchedule: any = null;
  @Input() selectedSeatNumbers: string[] = [];
  @Input() selectedBoardingPoint = '';
  @Input() selectedDroppingPoint = '';
  @Input() savedBusIds: number[] = [];
  @Input() seatList: any[] = [];
  @Input() isLoadingSeats = false;

  @Output() sortChange = new EventEmitter<string>();
  @Output() pageChange = new EventEmitter<number>();
  @Output() clearFilters = new EventEmitter<void>();
  @Output() toggleSeats = new EventEmitter<any>();
  @Output() saveToFavourites = new EventEmitter<any>();
  @Output() seatToggled = new EventEmitter<string>();
  @Output() boardingPointChanged = new EventEmitter<string>();
  @Output() droppingPointChanged = new EventEmitter<string>();
  @Output() proceedToCheckout = new EventEmitter<any>();

  get totalPages(): number {
    return Math.ceil(this.totalFilteredBuses / this.pageSize) || 1;
  }

  get pageNumbers(): number[] {
    const pages: number[] = [];
    for (let i = 1; i <= this.totalPages; i++) {
      pages.push(i);
    }
    return pages;
  }

  onSortChange() {
    this.sortChange.emit(this.sortBy);
  }

  onToggleSeats(bus: any) {
    this.toggleSeats.emit(bus);
  }

  isBusSaved(id: number): boolean {
    return this.savedBusIds.includes(id);
  }
}
