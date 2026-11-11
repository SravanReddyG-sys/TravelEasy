import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  GuestFormEntry,
  GuestType,
  HotelStaySummary,
  PhysicalRoomAllocation,
  RoomValidationResult
} from '../../../../../models/hotel.model';
import {
  DEFAULT_HOTEL_PLACEHOLDER,
  resolveImageUrl
} from '../../../../../shared/utils/image-url.util';
import {
  addGuestToRoom,
  buildPhysicalRoomsFromStaySummary,
  formatStayDisplayDate,
  isSameStayContextSignature,
  loadGuestAllocationState,
  loadStaySummaryFromSession,
  removeGuestFromRoom,
  saveGuestAllocationState,
  saveStaySummaryToSession,
  validateAllRooms,
  validatePhysicalRoom
} from '../../../../../shared/utils/guest-allocation.util';
import { PhysicalRoomGuestCardComponent } from '../physical-room-guest-card/physical-room-guest-card.component';

@Component({
  selector: 'app-guest-allocation-page',
  standalone: true,
  imports: [CommonModule, PhysicalRoomGuestCardComponent],
  template: `
    <div class="guest-allocation-page-container">

      <!-- Page Header (Exact WP-HB-UI-03 Mockup Hierarchy) -->
      <div class="mb-3">
        <h2 class="fw-bold text-navy mb-1">Guest Details</h2>
        <p class="text-muted small mb-0">
          Provide guest information for each selected room to continue with your booking.
        </p>
      </div>

      <!-- Missing Selection / Invalid Entry Guard State -->
      <div *ngIf="!activeStaySummary || physicalRooms.length === 0" class="card border-0 shadow-sm rounded-4 p-5 text-center my-4 bg-white">
        <div class="mb-3">
          <span class="d-inline-flex align-items-center justify-content-center rounded-circle bg-soft-teal text-teal" style="width: 60px; height: 60px;">
            <i class="fa-solid fa-bed fs-4"></i>
          </span>
        </div>
        <h5 class="fw-bold text-navy mb-2">No Room Selection Found</h5>
        <p class="text-muted small mx-auto mb-4" style="max-width: 460px;">
          Please select your rooms on the Hotel Details page before assigning guest information. Your previous selection may have expired or been cleared.
        </p>
        <div class="d-flex justify-content-center">
          <button
            type="button"
            class="btn btn-teal rounded-pill px-4 py-2 fw-semibold"
            (click)="onBackToRoomSelection()"
          >
            <i class="fa-solid fa-arrow-left me-2"></i>
            {{ requestedHotelId ? 'Return to Room Selection' : 'Back to Search Hotels' }}
          </button>
        </div>
      </div>

      <!-- Main Guest Allocation Workflow -->
      <ng-container *ngIf="activeStaySummary && physicalRooms.length > 0">

        <!-- TOP: Compact Hotel Information Card -->
        <div class="hotel-compact-card bg-white rounded-4 border shadow-sm p-3 mb-4">
          <div class="row g-3 align-items-center">
            <div class="col-lg-6 col-xl-7 min-w-0">
              <div class="d-flex flex-column flex-sm-row align-items-sm-center gap-3 min-w-0">
                <img
                  [src]="resolvedHotelImage"
                  [alt]="activeStaySummary.hotelName"
                  class="hotel-thumb rounded-3 flex-shrink-0"
                  (error)="onHotelImgError($event)"
                />
                <div class="hotel-card-info min-w-0 flex-grow-1 overflow-hidden">
                  <div class="d-flex align-items-center gap-2 flex-wrap mb-1">
                    <h5 class="fw-bold text-navy mb-0 text-truncate" style="max-width: 100%;">{{ activeStaySummary.hotelName }}</h5>
                    <span *ngIf="activeStaySummary.propertyType" class="badge property-type-pill rounded-pill fw-semibold px-2 py-1 flex-shrink-0">
                      {{ activeStaySummary.propertyType }}
                    </span>
                  </div>
                  <div class="text-muted small d-flex align-items-center gap-1 min-w-0 w-100">
                    <i class="fa-solid fa-location-dot text-navy-muted flex-shrink-0"></i>
                    <span class="hotel-address-truncate" [title]="hotelLocationLabel">{{ hotelLocationLabel }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="col-lg-6 col-xl-5">
              <div class="row g-3 stay-meta-grid">
                <div class="col-sm-4 col-6 meta-col-divider">
                  <div class="d-flex align-items-center gap-2">
                    <i class="fa-regular fa-calendar text-teal fs-6"></i>
                    <div>
                      <div class="text-muted smaller">Check-in</div>
                      <div class="fw-bold text-navy small text-nowrap">{{ formattedCheckIn }}</div>
                    </div>
                  </div>
                </div>

                <div class="col-sm-4 col-6 meta-col-divider">
                  <div class="d-flex align-items-center gap-2">
                    <i class="fa-regular fa-calendar text-teal fs-6"></i>
                    <div>
                      <div class="text-muted smaller">Check-out</div>
                      <div class="fw-bold text-navy small text-nowrap">{{ formattedCheckOut }}</div>
                    </div>
                  </div>
                </div>

                <div class="col-sm-4 col-12 meta-col-divider">
                  <div class="d-flex align-items-center gap-2">
                    <i class="fa-regular fa-moon text-teal fs-6"></i>
                    <div>
                      <div class="text-muted smaller">Nights</div>
                      <div class="fw-bold text-navy small text-nowrap">
                        {{ activeStaySummary.totalNights }} {{ activeStaySummary.totalNights === 1 ? 'Night' : 'Nights' }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- MIDDLE: Two-Column Selected Rooms & Sticky Stay Summary -->
        <div class="row g-4 mb-4 align-items-stretch">

          <!-- Left Column: Selected Rooms Overview -->
          <div class="col-lg-7">
            <div class="bg-white rounded-4 border shadow-sm p-4 h-100">
              <div class="d-flex align-items-center gap-2 mb-3 pb-2 border-bottom">
                <i class="fa-regular fa-calendar-check text-teal fs-5"></i>
                <h5 class="fw-bold text-navy mb-0">
                  Selected Rooms ({{ activeStaySummary.totalSelectedRooms }})
                </h5>
              </div>

              <div class="d-flex flex-column gap-3">
                <div
                  *ngFor="let item of activeStaySummary.selectedItems; let last = last"
                  class="selected-room-summary-row d-flex align-items-center justify-content-between gap-3"
                  [class.pb-3]="!last"
                  [class.border-bottom]="!last"
                >
                  <div class="d-flex align-items-center gap-3 min-w-0">
                    <img
                      [src]="getRoomImageUrl(item.imageUrl)"
                      [alt]="item.roomTypeName"
                      class="selected-room-thumb rounded-3 flex-shrink-0"
                      (error)="onRoomImgError($event)"
                    />
                    <div class="min-w-0">
                      <div class="fw-bold text-navy text-truncate">{{ item.roomTypeName }}</div>
                      <div class="text-muted small">
                        {{ item.quantity }} {{ item.quantity === 1 ? 'Room' : 'Rooms' }} • Up to {{ item.maxOccupancy }}
                        {{ item.maxOccupancy === 1 ? 'guest' : 'guests' }}{{ item.quantity > 1 ? ' per room' : '' }}
                      </div>
                    </div>
                  </div>

                  <div class="text-end flex-shrink-0">
                    <div class="fw-bold text-navy">
                      ₹{{ item.minNightlyPrice | number:'1.0-0' }} <span class="fw-normal small text-muted">/ night</span>
                    </div>
                    <div class="text-muted smaller">
                      ₹{{ item.stayTotalPricePerRoom | number:'1.0-0' }} total
                      ({{ activeStaySummary.totalNights }} {{ activeStaySummary.totalNights === 1 ? 'night' : 'nights' }}{{ item.quantity > 1 ? ', per room' : '' }})
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Right Column: Sticky Stay Summary Panel -->
          <div class="col-lg-5">
            <div class="stay-summary-sticky bg-white rounded-4 border shadow-sm p-4 h-100 d-flex flex-column justify-content-between sticky-summary-card">
              <div>
                <div class="d-flex align-items-center gap-2 mb-3 pb-2 border-bottom">
                  <i class="fa-regular fa-calendar-check text-teal fs-5"></i>
                  <h5 class="fw-bold text-navy mb-0">Stay Summary</h5>
                </div>

                <div class="d-flex flex-column gap-2 mb-3">
                  <div
                    *ngFor="let item of activeStaySummary.selectedItems"
                    class="d-flex justify-content-between align-items-center small"
                  >
                    <span class="text-navy fw-medium">
                      {{ item.roomTypeName }} × {{ item.quantity }}
                    </span>
                    <span class="fw-bold text-navy">
                      ₹{{ item.totalStayPrice | number:'1.0-0' }}
                    </span>
                  </div>
                </div>
              </div>

              <div>
                <div class="total-amount-pill rounded-3 px-3 py-3 d-flex justify-content-between align-items-center">
                  <div class="text-navy fw-bold">
                    Total Amount <span class="fw-normal text-muted small">(Estimated)</span>
                  </div>
                  <div class="fs-4 fw-bold text-navy">
                    ₹{{ activeStaySummary.estimatedTotalAmount | number:'1.0-0' }}
                  </div>
                </div>
              </div>
            </div>
          </div>

        </div>

        <!-- BOTTOM: Room-Wise Guest Information Accordion -->
        <div class="guest-info-section mb-4">
          <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-3">
            <div class="d-flex align-items-center gap-2">
              <i class="fa-regular fa-user text-teal fs-5"></i>
              <h5 class="fw-bold text-navy mb-0">Guest Information</h5>
            </div>

            <span *ngIf="showValidationBanner && invalidRoomsCount > 0" class="text-danger small fw-semibold">
              <i class="fa-solid fa-circle-exclamation me-1"></i>
              {{ invalidRoomsCount }} {{ invalidRoomsCount === 1 ? 'room requires' : 'rooms require' }} valid guest information before continuing.
            </span>
          </div>

          <div *ngFor="let room of physicalRooms; trackBy: trackByRoomKey">
            <app-physical-room-guest-card
              [room]="room"
              [validationResult]="getRoomValidation(room.roomKey)"
              [showValidation]="showValidationBanner || attemptedContinue"
              (toggleExpand)="onToggleRoomExpand($event)"
              (addGuestTypeChange)="onRoomAddGuestTypeChange($event)"
              (addGuest)="onAddGuestToRoom($event)"
              (removeGuest)="onRemoveGuestFromRoom($event)"
              (guestChange)="onGuestFieldChange($event)"
            ></app-physical-room-guest-card>
          </div>
        </div>

        <!-- FOOTER: Navigation Actions -->
        <div class="guest-allocation-footer d-flex flex-wrap align-items-center justify-content-between gap-3 pt-2 pb-4">
          <button
            type="button"
            class="btn btn-back-rooms rounded-3 px-4 py-2 fw-semibold d-inline-flex align-items-center gap-2"
            (click)="onBackToRoomSelection()"
          >
            <i class="fa-solid fa-arrow-left small"></i>
            <span>Back to Room Selection</span>
          </button>

          <button
            type="button"
            class="btn btn-continue-review rounded-3 px-4 py-2 fw-semibold d-inline-flex align-items-center gap-2"
            (click)="onContinueToReview()"
            data-testid="continue-to-reservation-review"
          >
            <span>Continue to Reservation Review</span>
            <i class="fa-solid fa-arrow-right small"></i>
          </button>
        </div>

      </ng-container>

    </div>
  `,
  styles: [`
    .text-navy {
      color: #0F172A;
    }
    .text-navy-muted {
      color: #475569;
    }
    .text-teal {
      color: #0891B2;
    }
    .smaller {
      font-size: 0.78rem;
    }
    .bg-soft-teal {
      background-color: #ECFEFF;
    }
    .btn-teal {
      background-color: #0891B2;
      border-color: #0891B2;
      color: #FFFFFF;
    }
    .btn-teal:hover {
      background-color: #0E7490;
      border-color: #0E7490;
    }
    .hotel-thumb {
      width: 150px;
      height: 82px;
      object-fit: cover;
      background-color: #E2E8F0;
    }
    .hotel-card-info {
      min-width: 0;
    }
    .hotel-address-truncate {
      display: block;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    @media (max-width: 575.98px) {
      .hotel-thumb {
        width: 100%;
        height: 140px;
      }
    }
    .selected-room-thumb {
      width: 84px;
      height: 54px;
      object-fit: cover;
      background-color: #E2E8F0;
    }
    .property-type-pill {
      background-color: #E0F2FE;
      color: #0369A1;
      font-size: 0.72rem;
    }
    @media (min-width: 576px) {
      .meta-col-divider {
        border-left: 1px solid #E2E8F0;
        padding-left: 1rem;
      }
    }
    .sticky-summary-card {
      top: 84px;
    }
    .total-amount-pill {
      background-color: #E6F7F9;
      border: 1px solid #CFFAFE;
    }
    .btn-back-rooms {
      background-color: #FFFFFF;
      color: #0F172A;
      border: 1px solid #CBD5E1;
    }
    .btn-back-rooms:hover {
      background-color: #F8FAFC;
      border-color: #94A3B8;
    }
    .btn-continue-review {
      background-color: #0891B2;
      border: 1px solid #0891B2;
      color: #FFFFFF;
    }
    .btn-continue-review:hover {
      background-color: #0E7490;
      border-color: #0E7490;
    }
  `]
})
export class GuestAllocationPageComponent implements OnInit, OnChanges {
  @Input() staySummary: HotelStaySummary | null = null;
  @Input() requestedHotelId: number | null = null;

  @Output() backToRoomSelection = new EventEmitter<void>();
  @Output() continueToReservationReview = new EventEmitter<{
    staySummary: HotelStaySummary;
    rooms: PhysicalRoomAllocation[];
  }>();

  activeStaySummary: HotelStaySummary | null = null;
  physicalRooms: PhysicalRoomAllocation[] = [];
  validationMap: Record<string, RoomValidationResult> = {};
  invalidRoomsCount = 0;
  attemptedContinue = false;
  showValidationBanner = false;

  ngOnInit(): void {
    this.initializeAllocationState();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['staySummary'] || changes['requestedHotelId']) {
      this.initializeAllocationState();
    }
  }

  get resolvedHotelImage(): string {
    return resolveImageUrl(this.activeStaySummary?.hotelImageUrl, DEFAULT_HOTEL_PLACEHOLDER);
  }

  get hotelLocationLabel(): string {
    if (!this.activeStaySummary) return '';
    if (this.activeStaySummary.address && this.activeStaySummary.city) {
      if (this.activeStaySummary.address.toLowerCase().includes(this.activeStaySummary.city.toLowerCase())) {
        return this.activeStaySummary.address;
      }
      return `${this.activeStaySummary.address}, ${this.activeStaySummary.city}`;
    }
    return this.activeStaySummary.address || this.activeStaySummary.city || '';
  }

  get formattedCheckIn(): string {
    return formatStayDisplayDate(this.activeStaySummary?.checkIn || '');
  }

  get formattedCheckOut(): string {
    return formatStayDisplayDate(this.activeStaySummary?.checkOut || '');
  }

  trackByRoomKey(_index: number, room: PhysicalRoomAllocation): string {
    return room.roomKey;
  }

  getRoomImageUrl(imageUrl?: string | null): string {
    return resolveImageUrl(imageUrl, DEFAULT_HOTEL_PLACEHOLDER);
  }

  onHotelImgError(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (img && img.src !== DEFAULT_HOTEL_PLACEHOLDER) {
      img.src = DEFAULT_HOTEL_PLACEHOLDER;
    }
  }

  onRoomImgError(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (img && img.src !== DEFAULT_HOTEL_PLACEHOLDER) {
      img.src = DEFAULT_HOTEL_PLACEHOLDER;
    }
  }

  getRoomValidation(roomKey: string): RoomValidationResult {
    return (
      this.validationMap[roomKey] || {
        roomKey,
        roomLabel: roomKey,
        valid: false,
        totalGuests: 0,
        adultCount: 0,
        childCount: 0,
        atMaxCapacity: false,
        roomErrors: ['At least 1 guest must be added.'],
        guestErrors: {}
      }
    );
  }

  onToggleRoomExpand(roomKey: string): void {
    this.physicalRooms = this.physicalRooms.map(room =>
      room.roomKey === roomKey ? { ...room, expanded: !room.expanded } : room
    );
    this.persistCurrentState();
  }

  onRoomAddGuestTypeChange(event: { roomKey: string; type: GuestType }): void {
    this.physicalRooms = this.physicalRooms.map(room =>
      room.roomKey === event.roomKey ? { ...room, selectedAddGuestType: event.type } : room
    );
    this.persistCurrentState();
  }

  onAddGuestToRoom(roomKey: string): void {
    this.physicalRooms = addGuestToRoom(this.physicalRooms, roomKey);
    this.recomputeValidation();
    this.persistCurrentState();
  }

  onRemoveGuestFromRoom(event: { roomKey: string; guestId: string }): void {
    this.physicalRooms = removeGuestFromRoom(this.physicalRooms, event.roomKey, event.guestId);
    this.recomputeValidation();
    this.persistCurrentState();
  }

  onGuestFieldChange(event: { roomKey: string; guest: GuestFormEntry }): void {
    this.physicalRooms = this.physicalRooms.map(room => {
      if (room.roomKey !== event.roomKey) return room;
      return {
        ...room,
        occupants: room.occupants.map(occ =>
          occ.id === event.guest.id ? event.guest : occ
        )
      };
    });
    this.recomputeValidation();
    this.persistCurrentState();
  }

  onBackToRoomSelection(): void {
    this.persistCurrentState();
    this.backToRoomSelection.emit();
  }

  onContinueToReview(): void {
    this.attemptedContinue = true;
    const summaryCheck = validateAllRooms(this.physicalRooms);
    this.validationMap = summaryCheck.results;
    this.invalidRoomsCount = summaryCheck.invalidRoomKeys.length;

    if (!summaryCheck.allValid) {
      this.showValidationBanner = true;
      const firstInvalidKey = summaryCheck.invalidRoomKeys[0];
      if (firstInvalidKey) {
        this.physicalRooms = this.physicalRooms.map(room =>
          room.roomKey === firstInvalidKey ? { ...room, expanded: true } : room
        );
        this.persistCurrentState();
        setTimeout(() => {
          const el = document.querySelector(`[data-room-key="${firstInvalidKey}"]`);
          if (el && typeof el.scrollIntoView === 'function') {
            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        }, 50);
      }
      return;
    }

    this.showValidationBanner = false;
    if (this.activeStaySummary) {
      saveStaySummaryToSession(this.activeStaySummary);
      this.persistCurrentState();
      this.continueToReservationReview.emit({
        staySummary: this.activeStaySummary,
        rooms: this.physicalRooms
      });
    }
  }

  private initializeAllocationState(): void {
    const incoming = this.staySummary;
    const storedSummary = loadStaySummaryFromSession();
    const resolvedSummary = incoming || storedSummary;

    if (
      !resolvedSummary ||
      !Array.isArray(resolvedSummary.selectedItems) ||
      resolvedSummary.selectedItems.length === 0 ||
      (this.requestedHotelId && resolvedSummary.hotelId !== this.requestedHotelId)
    ) {
      this.activeStaySummary = null;
      this.physicalRooms = [];
      this.validationMap = {};
      this.invalidRoomsCount = 0;
      return;
    }

    this.activeStaySummary = resolvedSummary;
    saveStaySummaryToSession(resolvedSummary);

    const savedAllocation = loadGuestAllocationState();
    const canRestoreRooms =
      savedAllocation !== null &&
      isSameStayContextSignature(savedAllocation.staySummary, resolvedSummary);

    this.physicalRooms = buildPhysicalRoomsFromStaySummary(
      resolvedSummary,
      canRestoreRooms ? savedAllocation!.rooms : null,
      canRestoreRooms ? savedAllocation!.activeExpandedRoomKey : null
    );

    this.attemptedContinue = false;
    this.showValidationBanner = false;
    this.recomputeValidation();
    this.persistCurrentState();
  }

  private recomputeValidation(): void {
    const nextMap: Record<string, RoomValidationResult> = {};
    let invalidCount = 0;
    for (const room of this.physicalRooms) {
      const result = validatePhysicalRoom(room);
      nextMap[room.roomKey] = result;
      if (!result.valid) {
        invalidCount += 1;
      }
    }
    this.validationMap = nextMap;
    this.invalidRoomsCount = invalidCount;
  }

  private persistCurrentState(): void {
    if (this.activeStaySummary && this.physicalRooms.length > 0) {
      saveGuestAllocationState(this.activeStaySummary, this.physicalRooms);
    }
  }
}
