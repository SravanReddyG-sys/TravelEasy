import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HotelPublicDto } from '../../../../../models/hotel.model';
import { HotelResultCardComponent } from '../hotel-result-card/hotel-result-card.component';

@Component({
  selector: 'app-hotel-results-list',
  standalone: true,
  imports: [CommonModule, FormsModule, HotelResultCardComponent],
  template: `
    <div class="hotel-results-container">
      <!-- Results Overview Header -->
      <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
        <div>
          <h5 class="fw-bold text-navy mb-0">
            Showing {{ displayStartIndex }}–{{ displayEndIndex }} of {{ totalCount }} hotels in {{ city }}
          </h5>
          <div class="text-muted smaller mt-1">
            {{ searchSummaryText }}
          </div>
        </div>

        <!-- Sort By Dropdown -->
        <div class="d-flex align-items-center gap-2">
          <label class="text-muted smaller fw-semibold text-nowrap mb-0">Sort by:</label>
          <select
            class="form-select form-select-sm sort-select rounded-pill w-auto fw-semibold text-navy bg-white"
            [(ngModel)]="sortBy"
            (ngModelChange)="onSortChange($event)"
          >
            <option value="price_asc">Price: Low to High</option>
            <option value="price_desc">Price: High to Low</option>
            <option value="name_asc">Hotel Name (A–Z)</option>
            <option value="name_desc">Hotel Name (Z–A)</option>
          </select>
        </div>
      </div>

      <!-- Filtered Empty State (0 matching after filter applied) -->
      <div *ngIf="hotels.length === 0" class="empty-state-card bg-white rounded-4 border p-5 text-center shadow-sm">
        <div class="empty-icon-wrap mx-auto mb-3">
          <i class="fa-solid fa-filter-circle-xmark text-teal fs-2"></i>
        </div>
        <h5 class="fw-bold text-navy mb-2">No hotels match your selected filters</h5>
        <p class="text-secondary small mb-4" style="max-width: 480px; margin: 0 auto;">
          None of the hotels in {{ city }} match all your current filter options. Try loosening your price range or clearing selected amenities.
        </p>
        <button type="button" class="btn btn-teal rounded-pill px-4 fw-bold" (click)="clearFilters.emit()">
          <i class="fa-solid fa-rotate-left me-1"></i> Clear All Filters
        </button>
      </div>

      <!-- Hotel Result Cards List -->
      <div *ngFor="let h of pagedHotels" class="mb-3">
        <app-hotel-result-card
          [hotel]="h"
          [isSaved]="isHotelSaved(h.id)"
          [nights]="nights"
          [roomsCount]="roomsCount"
          (viewRooms)="viewRooms.emit($event)"
          (toggleFavourite)="toggleFavourite.emit($event)"
        ></app-hotel-result-card>
      </div>

      <!-- Pagination Controls (if totalCount > pageSize) -->
      <div *ngIf="totalPages > 1" class="d-flex justify-content-between align-items-center mt-4 pt-2">
        <div class="text-muted smaller">
          Page {{ currentPage }} of {{ totalPages }}
        </div>
        <div class="d-flex gap-1">
          <button
            class="btn btn-sm btn-outline-secondary rounded-pill px-3"
            [disabled]="currentPage === 1"
            (click)="onPageChange(currentPage - 1)"
          >
            Previous
          </button>
          
          <button
            *ngFor="let p of pageNumbers"
            class="btn btn-sm rounded-circle pagination-num-btn"
            [class.btn-teal]="p === currentPage"
            [class.btn-outline-light]="p !== currentPage"
            [class.text-dark]="p !== currentPage"
            (click)="onPageChange(p)"
          >
            {{ p }}
          </button>

          <button
            class="btn btn-sm btn-outline-secondary rounded-pill px-3"
            [disabled]="currentPage === totalPages"
            (click)="onPageChange(currentPage + 1)"
          >
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
    .text-teal {
      color: #0891B2;
    }

    .sort-select {
      border-color: #CBD5E1;
      padding-left: 14px;
      padding-right: 32px;
      cursor: pointer;
    }

    .sort-select:focus {
      border-color: #0891B2;
      box-shadow: 0 0 0 0.2rem rgba(8, 145, 178, 0.15);
    }

    .empty-state-card {
      border-color: #E2E8F0;
    }

    .empty-icon-wrap {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background-color: #ECFEFF;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .btn-teal {
      background-color: #0891B2;
      color: #FFFFFF;
      border: none;
    }

    .btn-teal:hover {
      background-color: #0E7490;
      color: #FFFFFF;
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
export class HotelResultsListComponent {
  @Input() city = 'Hyderabad';
  @Input() hotels: HotelPublicDto[] = [];
  @Input() totalCount = 0;
  @Input() savedHotelIds: number[] = [];
  @Input() nights = 1;
  @Input() roomsCount = 1;
  @Input() adultsCount = 1;
  @Input() childrenCount = 0;
  @Input() checkInDate = '';
  @Input() checkOutDate = '';
  @Input() sortBy = 'price_asc';
  @Input() pageSize = 6;
  @Input() currentPage = 1;

  @Output() sortByChange = new EventEmitter<string>();
  @Output() pageChange = new EventEmitter<number>();
  @Output() clearFilters = new EventEmitter<void>();
  @Output() viewRooms = new EventEmitter<HotelPublicDto>();
  @Output() toggleFavourite = new EventEmitter<HotelPublicDto>();

  get displayStartIndex(): number {
    if (this.totalCount === 0 || this.hotels.length === 0) return 0;
    return (this.currentPage - 1) * this.pageSize + 1;
  }

  get displayEndIndex(): number {
    if (this.totalCount === 0 || this.hotels.length === 0) return 0;
    return Math.min(this.currentPage * this.pageSize, this.hotels.length);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.hotels.length / this.pageSize));
  }

  get pagedHotels(): HotelPublicDto[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.hotels.slice(start, start + this.pageSize);
  }

  get pageNumbers(): number[] {
    const pages: number[] = [];
    for (let i = 1; i <= this.totalPages; i++) {
      pages.push(i);
    }
    return pages;
  }

  get searchSummaryText(): string {
    const inFormatted = this.formatShortDate(this.checkInDate);
    const outFormatted = this.formatShortDate(this.checkOutDate);
    const nightLabel = this.nights === 1 ? '1 night' : `${this.nights} nights`;
    const roomLabel = this.roomsCount === 1 ? '1 Room' : `${this.roomsCount} Rooms`;
    const adultLabel = this.adultsCount === 1 ? '1 Adult' : `${this.adultsCount} Adults`;
    const childText = this.childrenCount === 1 ? '1 Child' : `${this.childrenCount} Children`;
    const guestLabel = this.childrenCount > 0 ? `${adultLabel}, ${childText}` : adultLabel;

    if (inFormatted && outFormatted) {
      return `${inFormatted} – ${outFormatted} (${nightLabel}) • ${roomLabel} • ${guestLabel}`;
    }
    return `${roomLabel} • ${guestLabel}`;
  }

  isHotelSaved(hotelId: number): boolean {
    return this.savedHotelIds.includes(hotelId);
  }

  onSortChange(newSort: string) {
    this.sortBy = newSort;
    this.sortByChange.emit(newSort);
  }

  onPageChange(p: number) {
    if (p >= 1 && p <= this.totalPages) {
      this.currentPage = p;
      this.pageChange.emit(p);
    }
  }

  private formatShortDate(isoDate: string): string {
    if (!isoDate) return '';
    try {
      const parts = isoDate.split('-');
      if (parts.length === 3) {
        const d = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
        return d.toLocaleDateString('en-GB', {
          day: 'numeric',
          month: 'short',
          year: 'numeric'
        });
      }
    } catch {
      return isoDate;
    }
    return isoDate;
  }
}
