import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges, HostListener, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HotelSearchCriteria } from '../../../../../models/hotel.model';

@Component({
  selector: 'app-hotel-search-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="hotel-search-banner mb-4 shadow-sm">
      <div class="hotel-banner-overlay"></div>
      
      <div class="hotel-banner-content">
        <!-- Banner Header -->
        <div class="hotel-banner-header mb-4">
          <h2 class="hotel-banner-title mb-1">Book Hotels</h2>
          <p class="hotel-banner-subtitle mb-0">
            Find and book your perfect stay at great hotels and comfortable properties.
          </p>
        </div>

        <!-- Floating White Search Card -->
        <div class="hotel-search-card" (click)="$event.stopPropagation()">
          <form (ngSubmit)="onSearchSubmit()" class="hotel-search-form" #searchForm="ngForm">
            <div class="hotel-search-row">

              <!-- 1. DESTINATION -->
              <div class="search-field destination-field" [class.field-error]="validationErrors.destination">
                <div class="field-icon">
                  <i class="fa-solid fa-location-dot text-primary"></i>
                </div>
                <div class="field-body">
                  <span class="field-label">DESTINATION</span>
                  <input
                    type="text"
                    class="field-input"
                    [(ngModel)]="city"
                    (ngModelChange)="onCityChange($event)"
                    name="destinationCity"
                    placeholder="Where are you going?"
                    autocomplete="off"
                    required
                  >
                </div>
                <button
                  type="button"
                  *ngIf="city"
                  class="btn-clear-input"
                  (click)="clearCity()"
                  title="Clear destination"
                  aria-label="Clear destination"
                >
                  <i class="fa-solid fa-xmark"></i>
                </button>
              </div>

              <div class="field-divider"></div>

              <!-- 2. CHECK-IN -->
              <div
                class="search-field date-field"
                (click)="openDatePicker(checkInInput, $event)"
                [class.field-error]="validationErrors.checkIn"
                role="button"
                tabindex="0"
                (keydown.enter)="openDatePicker(checkInInput, $event)"
                (keydown.space)="openDatePicker(checkInInput, $event)"
                aria-label="Select Check-in Date"
              >
                <div class="field-icon">
                  <i class="fa-regular fa-calendar text-primary"></i>
                </div>
                <div class="field-body position-relative">
                  <span class="field-label">CHECK-IN</span>
                  <div class="field-display-text" [class.text-muted]="!checkIn">{{ formatDisplayDate(checkIn) || 'Select date' }}</div>
                  <input
                    #checkInInput
                    id="hotelCheckInInput"
                    type="date"
                    class="native-date-picker"
                    [(ngModel)]="checkIn"
                    (ngModelChange)="onCheckInChange($event)"
                    [min]="todayStr"
                    name="checkInDate"
                    (click)="onDateInputClick(checkInInput, $event)"
                    tabindex="-1"
                    required
                  >
                </div>
              </div>

              <div class="field-divider"></div>

              <!-- 3. CHECK-OUT -->
              <div
                class="search-field date-field"
                (click)="openDatePicker(checkOutInput, $event)"
                [class.field-error]="validationErrors.checkOut"
                role="button"
                tabindex="0"
                (keydown.enter)="openDatePicker(checkOutInput, $event)"
                (keydown.space)="openDatePicker(checkOutInput, $event)"
                aria-label="Select Check-out Date"
              >
                <div class="field-icon">
                  <i class="fa-regular fa-calendar text-primary"></i>
                </div>
                <div class="field-body position-relative">
                  <span class="field-label">CHECK-OUT</span>
                  <div class="field-display-text" [class.text-muted]="!checkOut">{{ formatDisplayDate(checkOut) || 'Select date' }}</div>
                  <input
                    #checkOutInput
                    id="hotelCheckOutInput"
                    type="date"
                    class="native-date-picker"
                    [(ngModel)]="checkOut"
                    (ngModelChange)="onCheckOutChange($event)"
                    [min]="minCheckOutStr"
                    name="checkOutDate"
                    (click)="onDateInputClick(checkOutInput, $event)"
                    tabindex="-1"
                    required
                  >
                </div>
              </div>

              <div class="field-divider"></div>

              <!-- 4. GUESTS POPOVER TRIGGER -->
              <div class="search-field guests-field position-relative" (click)="toggleGuestsPopover($event)">
                <div class="field-icon">
                  <i class="fa-solid fa-user-group text-primary"></i>
                </div>
                <div class="field-body">
                  <span class="field-label">GUESTS</span>
                  <div class="field-display-text text-truncate">{{ guestsSummary }}</div>
                </div>
                <div class="field-arrow">
                  <i class="fa-solid fa-chevron-down text-muted small" [class.rotate-arrow]="isGuestsOpen"></i>
                </div>

                <!-- Guests Popover Dropdown -->
                <div *ngIf="isGuestsOpen" class="popover-dropdown guests-dropdown shadow" (click)="$event.stopPropagation()">
                  <div class="popover-header pb-2 mb-2 border-bottom">
                    <span class="fw-bold text-navy small">Guests & Occupants</span>
                  </div>

                  <!-- Adults -->
                  <div class="stepper-row d-flex justify-content-between align-items-center py-2">
                    <div>
                      <div class="fw-semibold text-navy small">Adults</div>
                      <div class="text-muted smaller">Ages 18 or above</div>
                    </div>
                    <div class="stepper-controls d-flex align-items-center gap-2">
                      <button
                        type="button"
                        class="btn-stepper"
                        [disabled]="adults <= 1"
                        (click)="decrementAdults()"
                      >-</button>
                      <span class="stepper-val fw-bold text-navy">{{ adults }}</span>
                      <button
                        type="button"
                        class="btn-stepper"
                        [disabled]="adults >= 16"
                        (click)="incrementAdults()"
                      >+</button>
                    </div>
                  </div>

                  <!-- Children -->
                  <div class="stepper-row d-flex justify-content-between align-items-center py-2 border-top">
                    <div>
                      <div class="fw-semibold text-navy small">Children</div>
                      <div class="text-muted smaller">Ages 0 to 17</div>
                    </div>
                    <div class="stepper-controls d-flex align-items-center gap-2">
                      <button
                        type="button"
                        class="btn-stepper"
                        [disabled]="children <= 0"
                        (click)="decrementChildren()"
                      >-</button>
                      <span class="stepper-val fw-bold text-navy">{{ children }}</span>
                      <button
                        type="button"
                        class="btn-stepper"
                        [disabled]="children >= 10"
                        (click)="incrementChildren()"
                      >+</button>
                    </div>
                  </div>

                  <div class="popover-footer pt-2 mt-2 border-top text-end">
                    <button type="button" class="btn btn-sm btn-teal rounded-pill px-3 py-1" (click)="isGuestsOpen = false">
                      Done
                    </button>
                  </div>
                </div>
              </div>

              <div class="field-divider"></div>

              <!-- 5. ROOMS POPOVER TRIGGER -->
              <div class="search-field rooms-field position-relative" (click)="toggleRoomsPopover($event)">
                <div class="field-icon">
                  <i class="fa-solid fa-bed text-primary"></i>
                </div>
                <div class="field-body">
                  <span class="field-label">ROOMS</span>
                  <div class="field-display-text">{{ roomsSummary }}</div>
                </div>
                <div class="field-arrow">
                  <i class="fa-solid fa-chevron-down text-muted small" [class.rotate-arrow]="isRoomsOpen"></i>
                </div>

                <!-- Rooms Popover Dropdown -->
                <div *ngIf="isRoomsOpen" class="popover-dropdown rooms-dropdown shadow" (click)="$event.stopPropagation()">
                  <div class="popover-header pb-2 mb-2 border-bottom">
                    <span class="fw-bold text-navy small">Rooms</span>
                  </div>

                  <div class="stepper-row d-flex justify-content-between align-items-center py-2">
                    <div>
                      <div class="fw-semibold text-navy small">Rooms</div>
                      <div class="text-muted smaller">Minimum 1 room</div>
                    </div>
                    <div class="stepper-controls d-flex align-items-center gap-2">
                      <button
                        type="button"
                        class="btn-stepper"
                        [disabled]="rooms <= 1"
                        (click)="decrementRooms()"
                      >-</button>
                      <span class="stepper-val fw-bold text-navy">{{ rooms }}</span>
                      <button
                        type="button"
                        class="btn-stepper"
                        [disabled]="rooms >= 10"
                        (click)="incrementRooms()"
                      >+</button>
                    </div>
                  </div>

                  <div class="popover-footer pt-2 mt-2 border-top text-end">
                    <button type="button" class="btn btn-sm btn-teal rounded-pill px-3 py-1" (click)="isRoomsOpen = false">
                      Done
                    </button>
                  </div>
                </div>
              </div>

              <!-- 6. SEARCH HOTELS ACTION BUTTON -->
              <div class="search-action">
                <button
                  type="submit"
                  class="btn-search-hotels"
                  [disabled]="isSearching"
                >
                  <span *ngIf="isSearching" class="spinner-border spinner-border-sm me-2"></span>
                  <i *ngIf="!isSearching" class="fa-solid fa-magnifying-glass me-2"></i>
                  <span>{{ isSearching ? 'Searching...' : 'Search Hotels' }}</span>
                </button>
              </div>

            </div>
          </form>
        </div>

        <!-- Inline Validation Alert (if any) -->
        <div *ngIf="validationMessage" class="validation-banner-alert mt-2 d-inline-flex align-items-center gap-2 px-3 py-1 rounded-pill">
          <i class="fa-solid fa-circle-exclamation text-warning"></i>
          <span class="small fw-semibold text-white">{{ validationMessage }}</span>
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

    .hotel-search-banner {
      position: relative;
      background-color: #F0F6FE;
      background-image: 
        linear-gradient(90deg, rgba(240, 246, 254, 0.96) 0%, rgba(240, 246, 254, 0.90) 35%, rgba(240, 246, 254, 0.40) 65%, rgba(240, 246, 254, 0.10) 90%),
        url('/assets/images/hotel_search_banner_background.png');
      background-position: right center;
      background-size: cover;
      background-repeat: no-repeat;
      border: 1px solid #D4E5F9;
      border-radius: 20px;
      overflow: visible;
      padding: 32px 36px 36px 36px;
      min-height: 220px;
    }

    .hotel-banner-overlay {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      border-radius: 20px;
      pointer-events: none;
    }

    .hotel-banner-content {
      position: relative;
      z-index: 10;
    }

    .hotel-banner-title {
      font-size: 1.85rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.5px;
    }

    .hotel-banner-subtitle {
      font-size: 0.95rem;
      color: #334155;
      font-weight: 500;
    }

    /* Floating White Search Card */
    .hotel-search-card {
      background: #FFFFFF;
      border-radius: 16px;
      padding: 8px 10px;
      border: 1px solid #E2E8F0;
      box-shadow: 0 8px 30px rgba(15, 23, 42, 0.08);
      margin-top: 18px;
    }

    .hotel-search-row {
      display: flex;
      align-items: center;
      gap: 4px;
      width: 100%;
    }

    /* Search Field Styles */
    .search-field {
      flex: 1 1 0;
      min-width: 0;
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 14px;
      border-radius: 10px;
      cursor: pointer;
      transition: background-color 0.15s ease;
      user-select: none;
    }

    .search-field:hover {
      background-color: #F8FAFC;
    }

    .destination-field {
      flex: 1.25 1 0;
      cursor: text;
    }

    .field-error {
      background-color: #FEF2F2 !important;
      border: 1px solid #FCA5A5;
    }

    .field-icon {
      font-size: 1.05rem;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 20px;
    }

    .field-body {
      flex: 1 1 0;
      min-width: 0;
      display: flex;
      flex-direction: column;
      justify-content: center;
    }

    .field-label {
      font-size: 0.68rem;
      text-transform: uppercase;
      font-weight: 700;
      letter-spacing: 0.6px;
      color: #64748B;
      margin-bottom: 2px;
      line-height: 1;
    }

    .field-input {
      border: none;
      background: transparent;
      padding: 0;
      font-size: 0.92rem;
      font-weight: 600;
      color: #0F172A;
      width: 100%;
      line-height: 1.2;
    }

    .field-input:focus {
      outline: none;
    }

    .field-display-text {
      font-size: 0.92rem;
      font-weight: 600;
      color: #0F172A;
      line-height: 1.2;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .date-field {
      position: relative;
    }

    .date-field:focus-visible {
      outline: 2px solid #0891B2;
      outline-offset: -2px;
    }

    .native-date-picker {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      opacity: 0;
      cursor: pointer;
      z-index: 2;
    }

    .btn-clear-input {
      background: transparent;
      border: none;
      color: #94A3B8;
      padding: 4px;
      font-size: 0.8rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
    }

    .btn-clear-input:hover {
      color: #0F172A;
      background-color: #E2E8F0;
    }

    .field-arrow {
      font-size: 0.75rem;
      transition: transform 0.2s ease;
    }

    .rotate-arrow {
      transform: rotate(180deg);
    }

    .field-divider {
      width: 1px;
      height: 36px;
      background-color: #E2E8F0;
      flex-shrink: 0;
    }

    /* Popover Dropdown */
    .popover-dropdown {
      position: absolute;
      top: calc(100% + 12px);
      left: 0;
      min-width: 240px;
      background: #FFFFFF;
      border-radius: 14px;
      border: 1px solid #E2E8F0;
      padding: 14px;
      z-index: 1050;
      animation: popoverFadeIn 0.15s ease-out;
    }

    @keyframes popoverFadeIn {
      from { opacity: 0; transform: translateY(-6px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .btn-stepper {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      border: 1px solid #CBD5E1;
      background: #FFFFFF;
      color: #0F172A;
      font-weight: bold;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .btn-stepper:hover:not(:disabled) {
      border-color: #0891B2;
      color: #0891B2;
      background: #F0FDFA;
    }

    .btn-stepper:disabled {
      opacity: 0.35;
      cursor: not-allowed;
    }

    .stepper-val {
      min-width: 24px;
      text-align: center;
      font-size: 0.95rem;
    }

    .btn-teal {
      background-color: #0891B2;
      color: #FFFFFF;
      font-weight: 600;
      border: none;
    }

    .btn-teal:hover {
      background-color: #0E7490;
      color: #FFFFFF;
    }

    /* Search Action Button */
    .search-action {
      flex: 0 0 auto;
      padding-left: 6px;
    }

    .btn-search-hotels {
      background-color: #0891B2;
      color: #FFFFFF;
      border: none;
      border-radius: 12px;
      padding: 14px 24px;
      font-weight: 700;
      font-size: 0.92rem;
      display: flex;
      align-items: center;
      justify-content: center;
      white-space: nowrap;
      cursor: pointer;
      transition: background-color 0.18s ease, transform 0.1s ease;
      box-shadow: 0 4px 12px rgba(8, 145, 178, 0.25);
    }

    .btn-search-hotels:hover:not(:disabled) {
      background-color: #0E7490;
      transform: translateY(-1px);
    }

    .btn-search-hotels:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }

    .validation-banner-alert {
      background-color: rgba(15, 23, 42, 0.85);
      backdrop-filter: blur(4px);
    }

    /* Responsive */
    @media (max-width: 1199.98px) {
      .hotel-search-banner {
        padding: 24px 20px;
      }
      .hotel-search-row {
        flex-wrap: wrap;
        gap: 8px;
      }
      .field-divider {
        display: none;
      }
      .search-field {
        background-color: #F8FAFC;
        border: 1px solid #E2E8F0;
        flex: 1 1 calc(50% - 10px);
      }
      .destination-field {
        flex: 1 1 100%;
      }
      .search-action {
        flex: 1 1 100%;
        padding-left: 0;
      }
      .btn-search-hotels {
        width: 100%;
      }
    }

    @media (max-width: 575.98px) {
      .hotel-banner-title {
        font-size: 1.4rem;
      }
      .search-field {
        flex: 1 1 100%;
      }
    }
  `]
})
export class HotelSearchPanelComponent implements OnInit, OnChanges {
  @Input() city = '';
  @Input() checkIn = '';
  @Input() checkOut = '';
  @Input() rooms = 1;
  @Input() adults = 1;
  @Input() children = 0;
  @Input() isSearching = false;

  @Output() cityChange = new EventEmitter<string>();
  @Output() checkInChange = new EventEmitter<string>();
  @Output() checkOutChange = new EventEmitter<string>();
  @Output() roomsChange = new EventEmitter<number>();
  @Output() adultsChange = new EventEmitter<number>();
  @Output() childrenChange = new EventEmitter<number>();
  @Output() search = new EventEmitter<HotelSearchCriteria>();

  todayStr = '';
  minCheckOutStr = '';
  isGuestsOpen = false;
  isRoomsOpen = false;
  validationMessage = '';
  validationErrors = {
    destination: false,
    checkIn: false,
    checkOut: false
  };

  constructor(private elementRef: ElementRef) {}

  ngOnInit() {
    const today = new Date();
    this.todayStr = this.formatDateISO(today);
    this.updateMinCheckOut();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['checkIn']) {
      this.updateMinCheckOut();
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.isGuestsOpen = false;
      this.isRoomsOpen = false;
    }
  }

  openDatePicker(input: HTMLInputElement, event?: Event) {
    if (event && event.type === 'keydown') {
      event.preventDefault();
    }
    if (!input) return;
    try {
      if (typeof input.showPicker === 'function') {
        input.showPicker();
        return;
      }
    } catch (e) {
      // Browser already opened picker or threw
    }
    input.focus();
  }

  onDateInputClick(input: HTMLInputElement, event: MouseEvent) {
    event.stopPropagation();
    this.openDatePicker(input, event);
  }

  get guestsSummary(): string {
    const adultLabel = this.adults === 1 ? '1 Adult' : `${this.adults} Adults`;
    if (this.children === 0) {
      return adultLabel;
    }
    const childLabel = this.children === 1 ? '1 Child' : `${this.children} Children`;
    return `${adultLabel}, ${childLabel}`;
  }

  get roomsSummary(): string {
    return this.rooms === 1 ? '1 Room' : `${this.rooms} Rooms`;
  }

  onCityChange(newCity: string) {
    this.validationErrors.destination = false;
    this.validationMessage = '';
    this.cityChange.emit(newCity);
  }

  clearCity() {
    this.city = '';
    this.cityChange.emit('');
  }

  onCheckInChange(newCheckIn: string) {
    this.validationErrors.checkIn = false;
    this.validationMessage = '';
    this.checkIn = newCheckIn;
    this.checkInChange.emit(newCheckIn);
    this.updateMinCheckOut();

    // Auto-advance check-out if check-in exists and (check-out is empty or check-out <= check-in)
    if (this.checkIn) {
      if (!this.checkOut || this.checkOut <= this.checkIn) {
        const nextDay = this.parseISODate(this.checkIn);
        nextDay.setDate(nextDay.getDate() + 1);
        this.checkOut = this.formatDateISO(nextDay);
        this.checkOutChange.emit(this.checkOut);
      }
    }
  }

  onCheckOutChange(newCheckOut: string) {
    this.validationErrors.checkOut = false;
    this.validationMessage = '';
    this.checkOut = newCheckOut;
    this.checkOutChange.emit(newCheckOut);
  }

  private updateMinCheckOut() {
    if (this.checkIn) {
      const checkInDate = this.parseISODate(this.checkIn);
      checkInDate.setDate(checkInDate.getDate() + 1);
      this.minCheckOutStr = this.formatDateISO(checkInDate);
    } else {
      this.minCheckOutStr = this.todayStr;
    }
  }

  private parseISODate(isoStr: string): Date {
    const parts = (isoStr || '').split('-');
    if (parts.length === 3) {
      return new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10), 12, 0, 0);
    }
    return new Date();
  }

  toggleGuestsPopover(event: Event) {
    event.stopPropagation();
    this.isRoomsOpen = false;
    this.isGuestsOpen = !this.isGuestsOpen;
  }

  toggleRoomsPopover(event: Event) {
    event.stopPropagation();
    this.isGuestsOpen = false;
    this.isRoomsOpen = !this.isRoomsOpen;
  }

  incrementAdults() {
    if (this.adults < 16) {
      this.adults++;
      this.adultsChange.emit(this.adults);
    }
  }

  decrementAdults() {
    if (this.adults > 1) {
      this.adults--;
      this.adultsChange.emit(this.adults);
    }
  }

  incrementChildren() {
    if (this.children < 10) {
      this.children++;
      this.childrenChange.emit(this.children);
    }
  }

  decrementChildren() {
    if (this.children > 0) {
      this.children--;
      this.childrenChange.emit(this.children);
    }
  }

  incrementRooms() {
    if (this.rooms < 10) {
      this.rooms++;
      this.roomsChange.emit(this.rooms);
    }
  }

  decrementRooms() {
    if (this.rooms > 1) {
      this.rooms--;
      this.roomsChange.emit(this.rooms);
    }
  }

  onSearchSubmit() {
    this.validationMessage = '';
    this.validationErrors = { destination: false, checkIn: false, checkOut: false };

    const trimmedCity = (this.city || '').trim();
    if (!trimmedCity) {
      this.validationErrors.destination = true;
      this.validationMessage = 'Please enter a destination city to search hotels.';
      return;
    }

    if (!this.checkIn) {
      this.validationErrors.checkIn = true;
      this.validationMessage = 'Please select a valid check-in date.';
      return;
    }

    if (!this.checkOut) {
      this.validationErrors.checkOut = true;
      this.validationMessage = 'Please select a valid check-out date.';
      return;
    }

    if (this.checkOut <= this.checkIn) {
      this.validationErrors.checkOut = true;
      this.validationMessage = 'Check-out date must be after check-in date.';
      return;
    }

    if (this.adults < 1) {
      this.validationMessage = 'At least 1 adult is required for the reservation.';
      return;
    }

    if (this.rooms < 1) {
      this.validationMessage = 'At least 1 room is required for the reservation.';
      return;
    }

    // Close any open popover
    this.isGuestsOpen = false;
    this.isRoomsOpen = false;

    this.search.emit({
      city: trimmedCity,
      checkIn: this.checkIn,
      checkOut: this.checkOut,
      rooms: this.rooms,
      adults: this.adults,
      children: this.children,
      guests: this.adults + this.children
    });
  }

  formatDisplayDate(isoDate: string): string {
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

  private formatDateISO(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
