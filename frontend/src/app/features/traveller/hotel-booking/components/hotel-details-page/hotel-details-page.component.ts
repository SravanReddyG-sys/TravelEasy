import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription, finalize } from 'rxjs';
import {
  HotelPublicDto,
  DateAwareRoomTypeDto,
  HotelDetailsResponseDto,
  HotelSearchCriteria,
  SelectedRoomItem,
  HotelStaySummary
} from '../../../../../models/hotel.model';
import { HotelService } from '../../../../../services/hotel.service';
import { loadStaySummaryFromSession } from '../../../../../shared/utils/guest-allocation.util';
import { HotelDetailsOverviewComponent } from '../hotel-details-overview/hotel-details-overview.component';
import { HotelSearchContextStripComponent } from '../hotel-search-context-strip/hotel-search-context-strip.component';
import { DateAwareRoomCardComponent } from '../date-aware-room-card/date-aware-room-card.component';
import { StaySummaryPanelComponent } from '../stay-summary-panel/stay-summary-panel.component';

@Component({
  selector: 'app-hotel-details-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    HotelDetailsOverviewComponent,
    HotelSearchContextStripComponent,
    DateAwareRoomCardComponent,
    StaySummaryPanelComponent
  ],
  template: `
    <div class="hotel-details-page-container">

      <!-- Back to Search Results Breadcrumb / Button -->
      <div class="mb-3 d-flex align-items-center justify-content-between">
        <button
          type="button"
          class="btn btn-sm btn-link text-decoration-none text-muted px-0 d-flex align-items-center gap-2 hover-text-teal"
          (click)="onBackToResults()"
        >
          <i class="fa-solid fa-arrow-left"></i>
          <span class="fw-semibold">Back to hotels in {{ city || 'results' }}</span>
        </button>

        <span class="text-muted smaller">
          Stage 2 of 4: Room Discovery & Selection
        </span>
      </div>

      <!-- Loading Skeleton State -->
      <div *ngIf="isLoading" class="hotel-details-loading py-5 text-center">
        <div class="spinner-border mb-3" role="status" style="color: #0891B2;"></div>
        <h6 class="fw-bold text-navy">Loading hotel details & date-aware room availability...</h6>
        <p class="text-muted small">Checking live rates for {{ checkIn }} to {{ checkOut }}</p>
      </div>

      <!-- Error State with Retry -->
      <div *ngIf="!isLoading && errorMessage" class="alert alert-danger rounded-4 border-0 shadow-sm p-4 text-center my-4">
        <i class="fa-solid fa-triangle-exclamation fs-3 text-danger mb-2"></i>
        <h5 class="fw-bold text-navy mb-1">{{ errorMessage }}</h5>
        <p class="text-secondary small mb-3">Unable to retrieve hotel details or room inventory at this time.</p>
        <div class="d-flex justify-content-center gap-2">
          <button type="button" class="btn btn-outline-danger btn-sm rounded-pill px-4 fw-bold" (click)="loadHotelDetails()">
            <i class="fa-solid fa-rotate-right me-1"></i> Retry
          </button>
          <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-4" (click)="onBackToResults()">
            Back to Results
          </button>
        </div>
      </div>

      <!-- Main Content (when hotel details loaded successfully) -->
      <div *ngIf="!isLoading && !errorMessage && hotelDetails">

        <!-- SECTION A: Hotel Overview Card -->
        <app-hotel-details-overview
          [hotel]="hotelDetails.hotel"
          [images]="hotelDetails.images || []"
          [isSaved]="isHotelSaved(hotelDetails.hotel.id)"
          (toggleFavourite)="toggleFavourite.emit($event)"
        ></app-hotel-details-overview>

        <!-- SECTION B: Search Context Strip with Inline Modify Search -->
        <app-hotel-search-context-strip
          [city]="city"
          [checkIn]="checkIn"
          [checkOut]="checkOut"
          [rooms]="rooms"
          [adults]="adults"
          [children]="children"
          (searchCriteriaChange)="onSearchCriteriaModified($event)"
        ></app-hotel-search-context-strip>

        <!-- SECTION C: Available Rooms Discovery & Stay Summary (Two-Column Layout) -->
        <div class="row g-4 align-items-start">

          <!-- LEFT COLUMN: Available Rooms List (7 Cols on desktop) -->
          <div class="col-xl-8 col-lg-7">
            <div class="available-rooms-header mb-3 d-flex flex-wrap align-items-center justify-content-between gap-2">
              <div>
                <h4 class="fw-bold text-navy mb-1">Available Rooms</h4>
                <div class="text-muted smaller">
                  Room options for {{ formattedStayDates }} ({{ totalNights }} {{ totalNights === 1 ? 'night' : 'nights' }})
                </div>
              </div>

              <!-- Quick Capacity Filter -->
              <div *ngIf="availableOccupancyFilters.length > 1" class="d-flex align-items-center gap-1">
                <span class="text-muted smaller me-1">Capacity:</span>
                <button
                  type="button"
                  class="btn btn-xs rounded-pill px-2 py-1 smaller fw-semibold"
                  [class.btn-teal]="capacityFilter === 'ALL'"
                  [class.btn-outline-secondary]="capacityFilter !== 'ALL'"
                  (click)="capacityFilter = 'ALL'"
                >
                  All
                </button>
                <button
                  *ngFor="let cap of availableOccupancyFilters"
                  type="button"
                  class="btn btn-xs rounded-pill px-2 py-1 smaller fw-semibold"
                  [class.btn-teal]="capacityFilter === cap.toString()"
                  [class.btn-outline-secondary]="capacityFilter !== cap.toString()"
                  (click)="capacityFilter = cap.toString()"
                >
                  {{ cap }} Guests
                </button>
              </div>
            </div>

            <!-- Empty State: No active rooms listed for this hotel -->
            <div *ngIf="activeRooms.length === 0" class="empty-state-card bg-white rounded-4 border p-5 text-center shadow-sm">
              <i class="fa-solid fa-bed fs-2 text-muted mb-2"></i>
              <h5 class="fw-bold text-navy mb-1">No room types currently available for this property</h5>
              <p class="text-secondary small mb-3">This hotel does not have active room categories configured.</p>
              <button type="button" class="btn btn-outline-teal btn-sm rounded-pill px-4 fw-bold" (click)="onBackToResults()">
                Explore Other Hotels
              </button>
            </div>

            <!-- Empty Filter State: Filter matched 0 rooms -->
            <div *ngIf="activeRooms.length > 0 && filteredRooms.length === 0" class="empty-state-card bg-white rounded-4 border p-4 text-center shadow-sm">
              <i class="fa-solid fa-filter-circle-xmark fs-3 text-muted mb-2"></i>
              <h6 class="fw-bold text-navy mb-1">No rooms match the selected capacity filter</h6>
              <p class="text-secondary smaller mb-3">Try clearing the capacity filter to view all rooms.</p>
              <button type="button" class="btn btn-outline-teal btn-sm rounded-pill px-3" (click)="capacityFilter = 'ALL'">
                Show All Rooms
              </button>
            </div>

            <!-- Room Cards List -->
            <div *ngFor="let room of filteredRooms" class="mb-3">
              <app-date-aware-room-card
                [room]="room"
                [quantity]="getRoomQuantity(room.id)"
                [nights]="totalNights"
                [requestedRooms]="rooms"
                [canIncrement]="canIncrementRoom(room.id)"
                (quantityChange)="onRoomQuantityChange(room, $event)"
              ></app-date-aware-room-card>
            </div>
          </div>

          <!-- RIGHT COLUMN: Stay Summary Panel (Sticky, 5 Cols on desktop) -->
          <div class="col-xl-4 col-lg-5">
            <app-stay-summary-panel
              [hotelId]="hotelDetails.hotel.id"
              [hotelName]="hotelDetails.hotel.name"
              [city]="city || hotelDetails.hotel.city"
              [propertyType]="hotelDetails.hotel.propertyType"
              [address]="hotelDetails.hotel.address"
              [hotelImageUrl]="coverHotelImageUrl"
              [checkIn]="checkIn"
              [checkOut]="checkOut"
              [totalNights]="totalNights"
              [requestedRooms]="rooms"
              [adults]="adults"
              [children]="children"
              [selectedItems]="selectedRoomItems"
              (continue)="onContinueToGuestAllocation($event)"
            ></app-stay-summary-panel>
          </div>

        </div>

      </div>

    </div>
  `,
  styles: [`
    .text-navy {
      color: #0F172A;
    }
    .text-teal {
      color: #0891B2;
    }
    .smaller {
      font-size: 0.78rem;
    }

    .hover-text-teal:hover {
      color: #0891B2 !important;
    }

    .btn-teal {
      background-color: #0891B2;
      border-color: #0891B2;
      color: #FFFFFF;
    }

    .btn-outline-teal {
      color: #0891B2;
      border-color: #0891B2;
      background-color: transparent;
    }

    .btn-outline-teal:hover {
      background-color: #0891B2;
      color: #FFFFFF;
    }
  `]
})
export class HotelDetailsPageComponent implements OnInit, OnChanges, OnDestroy {
  @Input({ required: true }) hotelId!: number;
  @Input() city = '';
  @Input() checkIn = '';
  @Input() checkOut = '';
  @Input() rooms = 1;
  @Input() adults = 1;
  @Input() children = 0;
  @Input() savedHotelIds: number[] = [];

  @Output() backToResults = new EventEmitter<void>();
  @Output() toggleFavourite = new EventEmitter<HotelPublicDto>();
  @Output() proceedToGuestAllocation = new EventEmitter<HotelStaySummary>();
  @Output() searchModified = new EventEmitter<HotelSearchCriteria>();

  isLoading = false;
  errorMessage = '';
  hotelDetails: HotelDetailsResponseDto | null = null;
  capacityFilter = 'ALL';

  // Map of roomTypeId -> selected quantity
  selectedQuantities = new Map<number, number>();

  private loadSub?: Subscription;

  constructor(private hotelService: HotelService) {}

  ngOnInit(): void {
    this.loadHotelDetails();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['hotelId'] || changes['checkIn'] || changes['checkOut'] || changes['rooms']) {
      this.loadHotelDetails();
    }
  }

  ngOnDestroy(): void {
    if (this.loadSub) {
      this.loadSub.unsubscribe();
    }
  }

  loadHotelDetails(): void {
    if (!this.hotelId) {
      this.errorMessage = 'Invalid or missing hotel identification.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    if (this.loadSub) {
      this.loadSub.unsubscribe();
    }

    this.loadSub = this.hotelService
      .getHotelDetails(this.hotelId, this.checkIn, this.checkOut, this.rooms || 1)
      .pipe(
        finalize(() => {
          this.isLoading = false;
        })
      )
      .subscribe({
        next: (res: HotelDetailsResponseDto) => {
          if (!res || !res.hotel) {
            this.errorMessage = 'The requested hotel property was not found.';
            return;
          }
          this.hotelDetails = res;
          if (this.selectedQuantities.size === 0) {
            this.restoreSavedRoomSelectionsIfMatching();
          }
          this.validateAndAdjustSelections();
        },
        error: (err: any) => {
          console.error('[Hotel Details] Failed to load hotel:', this.hotelId, err);
          if (err?.status === 404) {
            this.errorMessage = 'Hotel property not found or is currently inactive.';
          } else {
            this.errorMessage = 'Unable to load hotel details and room availability. Please verify your connection or try again.';
          }
        }
      });
  }

  get activeRooms(): DateAwareRoomTypeDto[] {
    if (!this.hotelDetails?.rooms) return [];
    return this.hotelDetails.rooms.filter(r => (r.status || 'ACTIVE').toUpperCase() === 'ACTIVE');
  }

  get filteredRooms(): DateAwareRoomTypeDto[] {
    if (this.capacityFilter === 'ALL') {
      return this.activeRooms;
    }
    const cap = parseInt(this.capacityFilter, 10);
    return this.activeRooms.filter(r => r.maxOccupancy === cap);
  }

  get availableOccupancyFilters(): number[] {
    const set = new Set<number>();
    this.activeRooms.forEach(r => {
      if (r.maxOccupancy) set.add(r.maxOccupancy);
    });
    return Array.from(set).sort((a, b) => a - b);
  }

  get totalNights(): number {
    if (!this.checkIn || !this.checkOut) return 1;
    try {
      const d1 = new Date(this.checkIn);
      const d2 = new Date(this.checkOut);
      const diffTime = d2.getTime() - d1.getTime();
      const nights = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
      return nights > 0 ? nights : 1;
    } catch {
      return 1;
    }
  }

  get formattedStayDates(): string {
    return `${this.checkIn} – ${this.checkOut}`;
  }

  get totalSelectedRooms(): number {
    let sum = 0;
    this.selectedQuantities.forEach(qty => {
      sum += qty;
    });
    return sum;
  }

  getRoomQuantity(roomId: number): number {
    return this.selectedQuantities.get(roomId) || 0;
  }

  canIncrementRoom(roomId: number): boolean {
    const room = this.activeRooms.find(r => r.id === roomId);
    if (!room) return false;

    // Room must be available across entire stay
    if (!room.availableAcrossStay || !room.availableRoomsAcrossStay) return false;

    // Room must have valid positive price
    if (!room.minNightlyPrice || room.minNightlyPrice <= 0) return false;

    // Combined selection cannot exceed requested total rooms
    if (this.totalSelectedRooms >= (this.rooms || 1)) return false;

    // Quantity cannot exceed room type's inventory across stay
    const currentQty = this.getRoomQuantity(roomId);
    if (currentQty >= room.availableRoomsAcrossStay) return false;

    return true;
  }

  onRoomQuantityChange(room: DateAwareRoomTypeDto, newQuantity: number): void {
    if (newQuantity <= 0) {
      this.selectedQuantities.delete(room.id);
    } else {
      // Calculate how many more rooms can be added overall
      const currentQty = this.getRoomQuantity(room.id);
      const otherRoomsTotal = this.totalSelectedRooms - currentQty;
      const maxAllowedByRequested = Math.max(0, (this.rooms || 1) - otherRoomsTotal);
      const maxAllowedByInventory = room.availableRoomsAcrossStay || 0;
      const safeQty = Math.min(newQuantity, maxAllowedByRequested, maxAllowedByInventory);

      if (safeQty > 0) {
        this.selectedQuantities.set(room.id, safeQty);
      } else {
        this.selectedQuantities.delete(room.id);
      }
    }
  }

  get coverHotelImageUrl(): string | null {
    if (!this.hotelDetails) return null;
    const images = this.hotelDetails.images || [];
    const cover = images.find(img => img.isCover || img.cover);
    if (cover?.imageUrl) return cover.imageUrl;
    if (images.length > 0 && images[0]?.imageUrl) return images[0].imageUrl;
    return this.hotelDetails.hotel?.imageUrl || null;
  }

  get selectedRoomItems(): SelectedRoomItem[] {
    const items: SelectedRoomItem[] = [];
    this.selectedQuantities.forEach((qty, roomId) => {
      if (qty > 0) {
        const room = this.activeRooms.find(r => r.id === roomId);
        if (room) {
          // Derive stay total per physical room
          let perRoomStay = 0;
          if (room.nightlyBreakdown && room.nightlyBreakdown.length > 0) {
            perRoomStay = room.nightlyBreakdown.reduce((sum, n) => sum + (n.price || 0), 0);
          } else if (room.totalPriceForStay && room.totalPriceForStay > 0) {
            perRoomStay = Math.round((room.totalPriceForStay / (this.rooms || 1)) * 100) / 100;
          } else {
            perRoomStay = (room.minNightlyPrice || 0) * this.totalNights;
          }

          items.push({
            roomTypeId: room.id,
            roomTypeName: room.roomTypeName,
            maxOccupancy: room.maxOccupancy,
            quantity: qty,
            minNightlyPrice: room.minNightlyPrice || 0,
            stayTotalPricePerRoom: perRoomStay,
            totalStayPrice: perRoomStay * qty,
            imageUrl: room.imageUrl || null
          });
        }
      }
    });
    return items;
  }

  private validateAndAdjustSelections(): void {
    // When room types or dates reload, verify that previously selected rooms remain valid
    const toDelete: number[] = [];
    this.selectedQuantities.forEach((qty, roomId) => {
      const room = this.activeRooms.find(r => r.id === roomId);
      if (!room || !room.availableAcrossStay || !room.minNightlyPrice || room.minNightlyPrice <= 0) {
        toDelete.push(roomId);
      } else if (qty > (room.availableRoomsAcrossStay || 0)) {
        this.selectedQuantities.set(roomId, room.availableRoomsAcrossStay || 0);
      }
    });
    toDelete.forEach(id => this.selectedQuantities.delete(id));
  }

  private restoreSavedRoomSelectionsIfMatching(): void {
    const saved = loadStaySummaryFromSession();
    if (!saved || !saved.hotelId || saved.hotelId !== this.hotelId) {
      return;
    }
    if (saved.checkIn !== this.checkIn || saved.checkOut !== this.checkOut) {
      return;
    }
    if ((saved.requestedRooms || 1) !== (this.rooms || 1)) {
      return;
    }
    if (Array.isArray(saved.selectedItems)) {
      saved.selectedItems.forEach(item => {
        if (item.roomTypeId && item.quantity > 0) {
          this.selectedQuantities.set(item.roomTypeId, item.quantity);
        }
      });
    }
  }

  onSearchCriteriaModified(criteria: HotelSearchCriteria): void {
    this.checkIn = criteria.checkIn;
    this.checkOut = criteria.checkOut;
    this.rooms = criteria.rooms || 1;
    this.adults = criteria.adults || 1;
    this.children = criteria.children || 0;

    this.searchModified.emit(criteria);
    this.loadHotelDetails();
  }

  isHotelSaved(id: number): boolean {
    return Array.isArray(this.savedHotelIds) && this.savedHotelIds.includes(id);
  }

  onBackToResults(): void {
    this.backToResults.emit();
  }

  onContinueToGuestAllocation(summary: HotelStaySummary): void {
    this.proceedToGuestAllocation.emit(summary);
  }
}
