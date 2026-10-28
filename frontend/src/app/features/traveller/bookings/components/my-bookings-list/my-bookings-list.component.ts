import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-my-bookings-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="my-bookings-view">
      <!-- Page Header Row -->
      <div class="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-3">
        <div>
          <h3 class="fw-bold text-navy mb-1">My Bookings</h3>
          <p class="text-secondary small mb-0">View, filter, inspect and manage your bus and hotel reservations</p>
        </div>
        <div class="d-flex align-items-center gap-2 position-relative">
          <button class="btn btn-outline-secondary btn-sm rounded-3 px-3 py-2" (click)="refresh.emit()" title="Refresh Bookings">
            <i class="fa-solid fa-rotate-right" [class.fa-spin]="isLoading"></i>
          </button>
          <button class="btn btn-primary d-flex align-items-center gap-2 px-3 py-2 rounded-3 fw-semibold shadow-sm" (click)="showNewBookingMenu = !showNewBookingMenu">
            <i class="fa-solid fa-plus"></i>
            <span>New Booking</span>
          </button>
          <!-- New Booking Dropdown -->
          <div *ngIf="showNewBookingMenu" class="new-booking-dropdown bg-white shadow-lg rounded-3 border py-1 position-absolute end-0 top-100 mt-1" style="z-index: 1050; min-width: 160px;">
            <button class="dropdown-item py-2 px-3 d-flex align-items-center gap-2 text-dark small" (click)="newBooking.emit('bus'); showNewBookingMenu = false;">
              <i class="fa-solid fa-bus text-primary"></i> Book a Bus
            </button>
            <button class="dropdown-item py-2 px-3 d-flex align-items-center gap-2 text-dark small" (click)="newBooking.emit('hotel'); showNewBookingMenu = false;">
              <i class="fa-solid fa-hotel text-success"></i> Book a Hotel
            </button>
          </div>
        </div>
      </div>

      <!-- Booking Category Tabs Pill Bar -->
      <div class="booking-tabs-nav d-flex align-items-center gap-2 mb-4">
        <button 
          class="tab-pill-btn" 
          [class.active]="categoryTab === 'ALL'" 
          (click)="categoryTabChange.emit('ALL')"
        >
          All Bookings ({{ allBookingsCount }})
        </button>
        <button 
          class="tab-pill-btn" 
          [class.active]="categoryTab === 'BUS'" 
          (click)="categoryTabChange.emit('BUS')"
        >
          Bus Bookings ({{ busBookingsCount }})
        </button>
        <button 
          class="tab-pill-btn" 
          [class.active]="categoryTab === 'HOTEL'" 
          (click)="categoryTabChange.emit('HOTEL')"
        >
          Hotel Bookings ({{ hotelBookingsCount }})
        </button>
      </div>

      <!-- Search & Filter Toolbar -->
      <div class="booking-filter-bar d-flex align-items-center gap-3 mb-4 flex-wrap">
        <!-- Search Input -->
        <div class="booking-search-box d-flex align-items-center px-3">
          <i class="fa-solid fa-magnifying-glass text-muted me-2"></i>
          <input 
            type="text" 
            class="form-control border-0 bg-transparent p-0 shadow-none small" 
            placeholder="Search by booking ID, destination or hotel name..."
            [(ngModel)]="searchQuery"
            (ngModelChange)="searchQueryChange.emit(searchQuery)"
          >
          <button *ngIf="searchQuery" class="btn btn-link btn-sm text-muted p-0 ms-1 border-0" (click)="searchQuery = ''; searchQueryChange.emit('')">
            <i class="fa-solid fa-xmark"></i>
          </button>
        </div>

        <!-- From Date Picker (Left) -->
        <div class="date-filter-box d-flex align-items-center gap-2" (click)="openDatePicker(fromDateInput)">
          <i class="fa-regular fa-calendar text-muted"></i>
          <input 
            #fromDateInput
            [type]="isFromDateFocused || fromDate ? 'date' : 'text'" 
            class="form-control border-0 bg-transparent p-0 shadow-none small date-input-control" 
            placeholder="From Date"
            title="From Date"
            [(ngModel)]="fromDate" 
            (focus)="isFromDateFocused = true"
            (blur)="isFromDateFocused = false"
            (change)="fromDateChange.emit(fromDate)"
          >
          <button *ngIf="fromDate" class="btn btn-link btn-sm text-muted p-0 border-0 ms-auto" (click)="$event.stopPropagation(); fromDate = ''; fromDateChange.emit('')" title="Clear From Date">
            <i class="fa-solid fa-xmark smaller"></i>
          </button>
        </div>

        <!-- To Date Picker (Right) -->
        <div class="date-filter-box d-flex align-items-center gap-2" (click)="openDatePicker(toDateInput)">
          <input 
            #toDateInput
            [type]="isToDateFocused || toDate ? 'date' : 'text'" 
            class="form-control border-0 bg-transparent p-0 shadow-none small date-input-control" 
            placeholder="To Date"
            title="To Date"
            [min]="fromDate"
            [(ngModel)]="toDate" 
            (focus)="isToDateFocused = true"
            (blur)="isToDateFocused = false"
            (change)="toDateChange.emit(toDate)"
          >
          <i class="fa-regular fa-calendar text-muted"></i>
          <button *ngIf="toDate" class="btn btn-link btn-sm text-muted p-0 border-0 ms-auto" (click)="$event.stopPropagation(); toDate = ''; toDateChange.emit('')" title="Clear To Date">
            <i class="fa-solid fa-xmark smaller"></i>
          </button>
        </div>

        <!-- Status Select -->
        <div class="d-flex align-items-center gap-2">
          <select 
            class="form-select status-filter-select" 
            [(ngModel)]="statusFilter"
            (change)="statusFilterChange.emit(statusFilter)"
          >
            <option value="ALL">All Statuses</option>
            <option value="CONFIRMED">Confirmed</option>
            <option value="UPCOMING">Upcoming</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>

        <!-- Clear Filters -->
        <button 
          *ngIf="hasActiveFilters" 
          class="btn btn-link btn-sm text-primary text-decoration-none p-0 d-flex align-items-center gap-1 ms-auto"
          (click)="clearFilters.emit()"
        >
          <i class="fa-solid fa-rotate-left"></i> Clear Filters
        </button>
      </div>

      <!-- Loading State -->
      <div *ngIf="isLoading" class="card bg-white rounded-4 border p-5 text-center my-4 shadow-sm">
        <div class="spinner-border text-primary mb-3" role="status"></div>
        <div class="fw-semibold text-dark">Loading your bookings...</div>
      </div>

      <!-- Error State -->
      <div *ngIf="errorMessage && !isLoading" class="alert alert-danger rounded-4 border-0 p-4 text-center shadow-sm my-4">
        <i class="fa-solid fa-circle-exclamation fs-3 mb-2 d-block"></i>
        <div class="fw-bold mb-1">Failed to load bookings</div>
        <p class="mb-3 small">{{ errorMessage }}</p>
        <button class="btn btn-primary btn-sm rounded-pill px-4" (click)="retryLoad.emit()">Try Again</button>
      </div>

      <!-- Strict Empty State: User has 0 bookings -->
      <div *ngIf="!isLoading && !errorMessage && totalBookingsCount === 0" class="card bg-white rounded-4 border p-5 text-center my-4 shadow-sm">
        <div class="d-inline-flex align-items-center justify-content-center rounded-circle bg-light text-primary mx-auto mb-3" style="width: 72px; height: 72px;">
          <i class="fa-solid fa-ticket fs-2 opacity-75"></i>
        </div>
        <h5 class="fw-bold text-navy mb-2">No bookings yet</h5>
        <p class="text-secondary small max-w-600 mx-auto mb-4">
          You haven't made any bus or hotel bookings yet. Explore routes and destinations to plan your next journey!
        </p>
        <div class="d-flex justify-content-center gap-3">
          <button class="btn btn-primary rounded-pill px-4 fw-semibold" (click)="switchTab.emit('bus')">
            <i class="fa-solid fa-bus me-2"></i>Book Buses
          </button>
          <button class="btn btn-outline-primary rounded-pill px-4 fw-semibold" (click)="switchTab.emit('hotel')">
            <i class="fa-solid fa-hotel me-2"></i>Book Hotels
          </button>
        </div>
      </div>

      <!-- Strict Empty State: Filters yielded 0 results -->
      <div *ngIf="!isLoading && !errorMessage && totalBookingsCount > 0 && bookings.length === 0" class="card bg-white rounded-4 border p-5 text-center my-4 shadow-sm">
        <div class="d-inline-flex align-items-center justify-content-center rounded-circle bg-light text-secondary mx-auto mb-3" style="width: 72px; height: 72px;">
          <i class="fa-solid fa-filter-circle-xmark fs-2 opacity-75"></i>
        </div>
        <h5 class="fw-bold text-navy mb-2">No bookings match your selected filters</h5>
        <p class="text-secondary small max-w-600 mx-auto mb-4">
          Try adjusting your search terms, date range, or status filter to find your bookings.
        </p>
        <button class="btn btn-outline-primary rounded-pill px-4 fw-semibold" (click)="clearFilters.emit()">
          <i class="fa-solid fa-rotate-left me-2"></i>Clear All Filters
        </button>
      </div>

      <!-- Booking Cards List -->
      <div *ngIf="!isLoading && !errorMessage && bookings.length > 0" class="booking-cards-list">
        <div *ngFor="let b of bookings" class="booking-card-item mb-3">
          <div class="d-flex align-items-center gap-3 flex-column flex-md-row">
            <!-- Thumbnail -->
            <img [src]="b.thumbnail" (error)="onImgError($event)" class="booking-card-img" alt="Booking Thumbnail">
            
            <!-- Content Details -->
            <div class="flex-grow-1 w-100">
              <div class="mb-1">
                <span [class]="b.serviceType === 'BUS' ? 'badge-type-bus' : 'badge-type-hotel'">
                  {{ b.serviceType === 'BUS' ? 'Bus' : 'Hotel' }}
                </span>
              </div>
              <h5 class="fw-bold text-navy mb-1 cursor-pointer" (click)="openDetails.emit(b)">{{ b.displayTitle }}</h5>
              <div class="text-secondary small mb-2">{{ b.displaySubtitle }}</div>
              
              <!-- Metadata icons row -->
              <div class="d-flex align-items-center gap-3 text-secondary smaller flex-wrap">
                <span *ngIf="b.dateText">
                  <i class="fa-regular fa-calendar-days text-muted me-1"></i>{{ b.dateText }}
                </span>
                <span *ngIf="b.serviceType === 'BUS' && b.timeText">
                  <i class="fa-regular fa-clock text-muted me-1"></i>{{ b.timeText }}
                </span>
                <span *ngIf="b.capacityText">
                  <i class="fa-solid fa-user-group text-muted me-1"></i>{{ b.capacityText }}
                </span>
                <span>
                  <i class="fa-solid fa-ticket text-muted me-1"></i>{{ b.bookingRef }}
                </span>
              </div>
            </div>

            <!-- Price, Status & Actions -->
            <div class="d-flex flex-column align-items-md-end justify-content-between gap-3 w-100 w-md-auto text-md-end">
              <div class="d-flex align-items-center gap-3 justify-content-between justify-content-md-end w-100">
                <span class="badge-status-pill" [ngClass]="b.statusBadgeClass">
                  {{ b.statusDisplay }}
                </span>
                <span *ngIf="b.status === 'CANCELLED' && b.refundStatus === 'PROCESSED'" class="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25 smaller px-2 py-1 rounded-pill">
                  <i class="fa-solid fa-check me-1"></i>Refunded
                </span>
                <span *ngIf="b.status === 'CANCELLED' && b.refundStatus === 'FAILED_RETRYABLE'" class="badge bg-warning bg-opacity-10 text-warning border border-warning border-opacity-25 smaller px-2 py-1 rounded-pill">
                  <i class="fa-solid fa-clock-rotate-left me-1"></i>Refund Pending
                </span>
                <div class="booking-card-price">
                  ₹{{ b.totalAmount | number }}
                </div>
                <i class="fa-solid fa-chevron-right text-primary cursor-pointer fs-6 d-none d-md-inline-block" (click)="openDetails.emit(b)" title="View Details"></i>
              </div>

              <div class="d-flex align-items-center gap-2 justify-content-end w-100">
                <button class="btn btn-view-details" (click)="openDetails.emit(b)">
                  View Details
                </button>
                <button *ngIf="b.isCancellable" class="btn btn-cancel-booking" (click)="cancelBooking.emit(b)">
                  Cancel Booking
                </button>
                <button *ngIf="b.isCompleted" class="btn btn-outline-primary btn-sm rounded-3 px-3 py-1 fw-semibold" (click)="rateReview.emit(b.raw)">
                  Rate & Review
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Pagination Footer -->
        <div *ngIf="totalPages > 1" class="d-flex justify-content-between align-items-center pt-3 pb-4 flex-wrap gap-3">
          <div class="text-secondary small">
            Showing {{ paginationStartItem }} – {{ paginationEndItem }} of {{ totalFilteredCount }} bookings
          </div>
          <div class="d-flex align-items-center gap-1">
            <button class="pagination-arrow-btn" [disabled]="currentPage === 1" (click)="pageChange.emit(currentPage - 1)" title="Previous Page">
              <i class="fa-solid fa-chevron-left smaller"></i>
            </button>
            <button 
              *ngFor="let p of pageNumbers" 
              class="pagination-page-btn" 
              [class.active]="p === currentPage"
              (click)="pageChange.emit(p)"
            >
              {{ p }}
            </button>
            <button class="pagination-arrow-btn" [disabled]="currentPage === totalPages" (click)="pageChange.emit(currentPage + 1)" title="Next Page">
              <i class="fa-solid fa-chevron-right smaller"></i>
            </button>
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
    .cursor-pointer {
      cursor: pointer;
    }
    .max-w-600 {
      max-width: 600px;
    }
    .booking-tabs-nav {
      overflow-x: auto;
    }
    .tab-pill-btn {
      background: transparent;
      border: 1px solid #E2E8F0;
      color: #64748B;
      font-weight: 600;
      font-size: 0.88rem;
      padding: 8px 18px;
      border-radius: 50px;
      transition: all 0.2s ease;
      white-space: nowrap;
    }
    .tab-pill-btn:hover {
      background: #F8FAFC;
      color: #0F172A;
    }
    .tab-pill-btn.active {
      background: #0875E1;
      border-color: #0875E1;
      color: #FFFFFF;
      box-shadow: 0 4px 12px rgba(8, 117, 225, 0.25);
    }
    .booking-filter-bar {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 14px;
      padding: 10px 16px;
    }
    .booking-search-box {
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      height: 42px;
      flex: 1 1 260px;
    }
    .date-filter-box {
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      padding: 8px 14px;
      height: 42px;
      cursor: pointer;
      min-width: 150px;
    }
    .date-input-control {
      font-size: 0.82rem;
      color: #0F172A;
      cursor: pointer;
    }
    .status-filter-select {
      height: 42px;
      border-radius: 10px;
      border-color: #E2E8F0;
      font-size: 0.82rem;
      color: #0F172A;
      font-weight: 600;
      padding-left: 12px;
    }
    .booking-card-item {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 16px;
      padding: 20px;
      transition: all 0.2s ease;
    }
    .booking-card-item:hover {
      box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
      border-color: #CBD5E1;
    }
    .booking-card-img {
      width: 100px;
      height: 100px;
      border-radius: 12px;
      object-fit: cover;
      flex-shrink: 0;
    }
    .badge-type-bus {
      background: #E0F2FE;
      color: #0284C7;
      font-size: 0.72rem;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 6px;
      display: inline-block;
    }
    .badge-type-hotel {
      background: #EDE9FE;
      color: #7C3AED;
      font-size: 0.72rem;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 6px;
      display: inline-block;
    }
    .badge-status-pill {
      font-size: 0.75rem;
      font-weight: 700;
      padding: 4px 10px;
      border-radius: 50px;
      display: inline-block;
    }
    .badge-status-confirmed {
      background: #ECFDF5;
      color: #059669;
      border: 1px solid #A7F3D0;
    }
    .badge-status-pending {
      background: #FFFBEB;
      color: #D97706;
      border: 1px solid #FDE68A;
    }
    .badge-status-cancelled {
      background: #FEF2F2;
      color: #DC2626;
      border: 1px solid #FECACA;
    }
    .badge-status-expired {
      background: #F1F5F9;
      color: #64748B;
      border: 1px solid #CBD5E1;
    }
    .badge-status-completed {
      background: #F0FDF4;
      color: #16A34A;
      border: 1px solid #BBF7D0;
    }
    .badge-status-failed {
      background: #FEF2F2;
      color: #DC2626;
      border: 1px solid #FECACA;
    }
    .booking-card-price {
      font-size: 1.35rem;
      font-weight: 800;
      color: #0875E1;
    }
    .btn-view-details {
      background: #F1F5F9;
      color: #0F172A;
      font-size: 0.82rem;
      font-weight: 600;
      padding: 6px 14px;
      border-radius: 8px;
      border: none;
      transition: all 0.15s ease;
    }
    .btn-view-details:hover {
      background: #E2E8F0;
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
    .pagination-arrow-btn, .pagination-page-btn {
      width: 34px;
      height: 34px;
      border-radius: 8px;
      border: 1px solid #E2E8F0;
      background: #FFFFFF;
      color: #64748B;
      font-weight: 600;
      font-size: 0.82rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      transition: all 0.15s ease;
    }
    .pagination-page-btn.active {
      background: #0875E1;
      color: #FFFFFF;
      border-color: #0875E1;
    }
  `]
})
export class MyBookingsListComponent {
  @Input() categoryTab: 'ALL' | 'BUS' | 'HOTEL' = 'ALL';
  @Input() allBookingsCount = 0;
  @Input() busBookingsCount = 0;
  @Input() hotelBookingsCount = 0;
  @Input() searchQuery = '';
  @Input() fromDate = '';
  @Input() toDate = '';
  @Input() statusFilter = 'ALL';
  @Input() hasActiveFilters = false;
  @Input() isLoading = false;
  @Input() errorMessage: string | null = null;
  @Input() totalBookingsCount = 0;
  @Input() totalFilteredCount = 0;
  @Input() bookings: any[] = [];
  @Input() currentPage = 1;
  @Input() totalPages = 1;
  @Input() paginationStartItem = 0;
  @Input() paginationEndItem = 0;
  @Input() pageNumbers: number[] = [];

  @Output() categoryTabChange = new EventEmitter<'ALL' | 'BUS' | 'HOTEL'>();
  @Output() searchQueryChange = new EventEmitter<string>();
  @Output() fromDateChange = new EventEmitter<string>();
  @Output() toDateChange = new EventEmitter<string>();
  @Output() statusFilterChange = new EventEmitter<string>();
  @Output() clearFilters = new EventEmitter<void>();
  @Output() pageChange = new EventEmitter<number>();
  @Output() openDetails = new EventEmitter<any>();
  @Output() cancelBooking = new EventEmitter<any>();
  @Output() rateReview = new EventEmitter<any>();
  @Output() switchTab = new EventEmitter<string>();
  @Output() retryLoad = new EventEmitter<void>();
  @Output() refresh = new EventEmitter<void>();
  @Output() newBooking = new EventEmitter<string>();

  showNewBookingMenu = false;
  isFromDateFocused = false;
  isToDateFocused = false;

  openDatePicker(input: HTMLInputElement) {
    if (typeof (input as any).showPicker === 'function') {
      try {
        (input as any).showPicker();
      } catch {
        input.focus();
      }
    } else {
      input.focus();
    }
  }

  onImgError(e: any) {
    e.target.src = 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=400&q=80';
  }
}
