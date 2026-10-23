import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-bus-filter-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="bus-filter-card sticky-top" style="top: 1rem; z-index: 10;">
      <!-- Filters Header -->
      <div class="d-flex justify-content-between align-items-center mb-3">
        <h6 class="fw-bold text-navy mb-0">
          <i class="fa-solid fa-sliders text-primary me-2"></i>Filters
        </h6>
        <button *ngIf="hasActiveFilters" 
                class="btn btn-link btn-sm text-primary p-0 text-decoration-none fw-semibold small" 
                (click)="onClearAll()">
          Clear All
        </button>
      </div>

      <!-- A. Bus Type -->
      <div class="filter-section-header d-flex justify-content-between align-items-center cursor-pointer" (click)="toggleSection.emit('busType')">
        <span class="filter-section-title mb-0">Bus Type</span>
        <i class="fa-solid smaller text-muted" [class.fa-chevron-up]="filterSectionsOpen['busType']" [class.fa-chevron-down]="!filterSectionsOpen['busType']"></i>
      </div>
      <div *ngIf="filterSectionsOpen['busType']" class="mt-2">
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="filterBusTypeAC" (change)="onFilterChange()"> AC</span>
          <span class="filter-count-badge">{{ countBusTypeAC }}</span>
        </label>
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="filterBusTypeNonAC" (change)="onFilterChange()"> Non-AC</span>
          <span class="filter-count-badge">{{ countBusTypeNonAC }}</span>
        </label>
      </div>

      <div class="filter-divider"></div>

      <!-- B. Seat Type -->
      <div class="filter-section-header d-flex justify-content-between align-items-center cursor-pointer" (click)="toggleSection.emit('seatType')">
        <span class="filter-section-title mb-0">Seat Type</span>
        <i class="fa-solid smaller text-muted" [class.fa-chevron-up]="filterSectionsOpen['seatType']" [class.fa-chevron-down]="!filterSectionsOpen['seatType']"></i>
      </div>
      <div *ngIf="filterSectionsOpen['seatType']" class="mt-2">
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="filterSeatTypeSeater" (change)="onFilterChange()"> Seater</span>
          <span class="filter-count-badge">{{ countSeatTypeSeater }}</span>
        </label>
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="filterSeatTypeSleeper" (change)="onFilterChange()"> Sleeper</span>
          <span class="filter-count-badge">{{ countSeatTypeSleeper }}</span>
        </label>
      </div>

      <div class="filter-divider"></div>

      <!-- C. Single Seat Only -->
      <div class="filter-section-header d-flex justify-content-between align-items-center cursor-pointer" (click)="toggleSection.emit('singleSeat')">
        <div class="d-flex align-items-center gap-1">
          <span class="filter-section-title mb-0">Single Seat</span>
          <i class="fa-solid fa-circle-info text-muted smaller" title="Show buses with single seats or single berth layout"></i>
        </div>
        <i class="fa-solid smaller text-muted" [class.fa-chevron-up]="filterSectionsOpen['singleSeat']" [class.fa-chevron-down]="!filterSectionsOpen['singleSeat']"></i>
      </div>
      <div *ngIf="filterSectionsOpen['singleSeat']" class="mt-2">
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="filterSingleSeatOnly" (change)="onFilterChange()"> Single Seat Only</span>
        </label>
      </div>

      <div class="filter-divider"></div>

      <!-- D. Departure Time -->
      <div class="filter-section-header d-flex justify-content-between align-items-center cursor-pointer" (click)="toggleSection.emit('depTime')">
        <span class="filter-section-title mb-0">
          Departure Time <span class="text-muted fw-normal smaller text-capitalize">({{ busOrigin }})</span>
        </span>
        <i class="fa-solid smaller text-muted" [class.fa-chevron-up]="filterSectionsOpen['depTime']" [class.fa-chevron-down]="!filterSectionsOpen['depTime']"></i>
      </div>
      <div *ngIf="filterSectionsOpen['depTime']" class="mt-2">
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="depBands.before6am" (change)="onFilterChange()"> Before 6:00 AM</span>
          <span class="filter-count-badge">{{ countDepBefore6am }}</span>
        </label>
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="depBands.from6to12" (change)="onFilterChange()"> 6:00 AM – 12:00 PM</span>
          <span class="filter-count-badge">{{ countDep6to12 }}</span>
        </label>
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="depBands.from12to18" (change)="onFilterChange()"> 12:00 PM – 6:00 PM</span>
          <span class="filter-count-badge">{{ countDep12to18 }}</span>
        </label>
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="depBands.after6pm" (change)="onFilterChange()"> After 6:00 PM</span>
          <span class="filter-count-badge">{{ countDepAfter6pm }}</span>
        </label>
      </div>

      <div class="filter-divider"></div>

      <!-- E. Arrival Time -->
      <div class="filter-section-header d-flex justify-content-between align-items-center cursor-pointer" (click)="toggleSection.emit('arrTime')">
        <span class="filter-section-title mb-0">
          Arrival Time <span class="text-muted fw-normal smaller text-capitalize">({{ busDestination }})</span>
        </span>
        <i class="fa-solid smaller text-muted" [class.fa-chevron-up]="filterSectionsOpen['arrTime']" [class.fa-chevron-down]="!filterSectionsOpen['arrTime']"></i>
      </div>
      <div *ngIf="filterSectionsOpen['arrTime']" class="mt-2">
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="arrBands.before6am" (change)="onFilterChange()"> Before 6:00 AM</span>
          <span class="filter-count-badge">{{ countArrBefore6am }}</span>
        </label>
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="arrBands.from6to12" (change)="onFilterChange()"> 6:00 AM – 12:00 PM</span>
          <span class="filter-count-badge">{{ countArr6to12 }}</span>
        </label>
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="arrBands.from12to18" (change)="onFilterChange()"> 12:00 PM – 6:00 PM</span>
          <span class="filter-count-badge">{{ countArr12to18 }}</span>
        </label>
        <label class="filter-checkbox-label">
          <span><input type="checkbox" class="form-check-input me-2" [(ngModel)]="arrBands.after6pm" (change)="onFilterChange()"> After 6:00 PM</span>
          <span class="filter-count-badge">{{ countArrAfter6pm }}</span>
        </label>
      </div>

      <div class="filter-divider"></div>

      <!-- F. Pickup Points -->
      <div *ngIf="availablePickupPoints.length > 0">
        <div class="filter-section-header d-flex justify-content-between align-items-center cursor-pointer" (click)="toggleSection.emit('boarding')">
          <span class="filter-section-title mb-0">Boarding Points</span>
          <i class="fa-solid smaller text-muted" [class.fa-chevron-up]="filterSectionsOpen['boarding']" [class.fa-chevron-down]="!filterSectionsOpen['boarding']"></i>
        </div>
        <div *ngIf="filterSectionsOpen['boarding']" class="mt-2">
          <input type="text" class="form-control filter-search-input" placeholder="Search pick-up points..." [(ngModel)]="pickupSearchQuery" (input)="onFilterChange()">
          <div class="filter-points-list mb-2">
            <label *ngFor="let p of displayedPickupPoints" class="filter-checkbox-label">
              <span class="text-truncate" style="max-width: 170px;">
                <input type="checkbox" class="form-check-input me-2" [checked]="selectedPickupPoints.includes(p.name)" (change)="togglePickupPoint.emit(p.name)">
                {{ p.name }}
              </span>
              <span class="filter-count-badge">{{ p.count }}</span>
            </label>
          </div>
          <button *ngIf="availablePickupPoints.length > 5" class="btn btn-link btn-sm text-primary p-0 text-decoration-none smaller fw-semibold mb-2" (click)="showAllPickupPoints = !showAllPickupPoints">
            {{ showAllPickupPoints ? 'Show Less' : '+ Show ' + (availablePickupPoints.length - 5) + ' More' }}
          </button>
        </div>
        <div class="filter-divider"></div>
      </div>

      <!-- G. Dropping Points -->
      <div *ngIf="availableDroppingPoints.length > 0">
        <div class="filter-section-header d-flex justify-content-between align-items-center cursor-pointer" (click)="toggleSection.emit('dropping')">
          <span class="filter-section-title mb-0">Dropping Points</span>
          <i class="fa-solid smaller text-muted" [class.fa-chevron-up]="filterSectionsOpen['dropping']" [class.fa-chevron-down]="!filterSectionsOpen['dropping']"></i>
        </div>
        <div *ngIf="filterSectionsOpen['dropping']" class="mt-2">
          <input type="text" class="form-control filter-search-input" placeholder="Search dropping points..." [(ngModel)]="droppingSearchQuery" (input)="onFilterChange()">
          <div class="filter-points-list mb-2">
            <label *ngFor="let p of displayedDroppingPoints" class="filter-checkbox-label">
              <span class="text-truncate" style="max-width: 170px;">
                <input type="checkbox" class="form-check-input me-2" [checked]="selectedDroppingPoints.includes(p.name)" (change)="toggleDroppingPoint.emit(p.name)">
                {{ p.name }}
              </span>
              <span class="filter-count-badge">{{ p.count }}</span>
            </label>
          </div>
          <button *ngIf="availableDroppingPoints.length > 5" class="btn btn-link btn-sm text-primary p-0 text-decoration-none smaller fw-semibold mb-2" (click)="showAllDroppingPoints = !showAllDroppingPoints">
            {{ showAllDroppingPoints ? 'Show Less' : '+ Show ' + (availableDroppingPoints.length - 5) + ' More' }}
          </button>
        </div>
        <div class="filter-divider"></div>
      </div>

      <!-- H. Amenities -->
      <div class="filter-section-header d-flex justify-content-between align-items-center cursor-pointer" (click)="toggleSection.emit('amenities')">
        <span class="filter-section-title mb-0">Amenities</span>
        <i class="fa-solid smaller text-muted" [class.fa-chevron-up]="filterSectionsOpen['amenities']" [class.fa-chevron-down]="!filterSectionsOpen['amenities']"></i>
      </div>
      <div *ngIf="filterSectionsOpen['amenities']" class="mt-2">
        <label *ngFor="let am of amenityDefinitions" class="filter-checkbox-label">
          <span>
            <input type="checkbox" class="form-check-input me-2" [checked]="selectedAmenities.includes(am.name)" (change)="toggleAmenity.emit(am.name)">
            <i [class]="am.icon + ' me-1 text-muted smaller'"></i>
            {{ am.name }}
          </span>
          <span class="filter-count-badge">{{ getAmenityCount(am.name) }}</span>
        </label>
      </div>

      <div class="filter-divider"></div>

      <!-- I. Price Range -->
      <div class="filter-section-header d-flex justify-content-between align-items-center cursor-pointer" (click)="toggleSection.emit('price')">
        <div class="d-flex justify-content-between align-items-center flex-grow-1 me-2">
          <span class="filter-section-title mb-0">Price Range</span>
          <span class="text-primary fw-bold smaller">₹{{ filterMinPrice }} – ₹{{ filterMaxPrice }}</span>
        </div>
        <i class="fa-solid smaller text-muted" [class.fa-chevron-up]="filterSectionsOpen['price']" [class.fa-chevron-down]="!filterSectionsOpen['price']"></i>
      </div>
      <div *ngIf="filterSectionsOpen['price']" class="mt-2">
        <div class="d-flex align-items-center gap-2 mb-2">
          <input type="range" class="form-range" [min]="defaultMinPrice" [max]="defaultMaxPrice" step="50" [(ngModel)]="filterMaxPrice" (input)="onPriceChange()">
        </div>
        <div class="d-flex justify-content-between smaller text-muted">
          <span>₹{{ defaultMinPrice }}</span>
          <span>₹{{ defaultMaxPrice }}</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .bus-filter-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 16px;
      padding: 18px;
    }
    .filter-section-header {
      cursor: pointer;
      user-select: none;
      padding: 3px 0;
    }
    .filter-section-title {
      font-size: 0.82rem;
      font-weight: 700;
      color: #0F172A;
    }
    .filter-divider {
      height: 1px;
      background: #F1F5F9;
      margin: 10px 0;
    }
    .filter-checkbox-label {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 5px 0;
      font-size: 0.82rem;
      color: #475569;
      cursor: pointer;
      margin-bottom: 0;
    }
    .filter-checkbox-label:hover {
      color: #0875E1;
    }
    .filter-count-badge {
      font-size: 0.72rem;
      background: #F1F5F9;
      color: #64748B;
      border-radius: 12px;
      padding: 2px 7px;
      font-weight: 600;
    }
    .filter-search-input {
      font-size: 0.78rem;
      padding: 5px 10px;
      border-radius: 8px;
      margin-bottom: 8px;
      border: 1px solid #E2E8F0;
    }
    .filter-points-list {
      max-height: 160px;
      overflow-y: auto;
    }
    .smaller {
      font-size: 0.75rem;
    }
    .cursor-pointer {
      cursor: pointer;
    }
    .text-navy {
      color: #0F172A;
    }
  `]
})
export class BusFilterPanelComponent {
  @Input() busOrigin = '';
  @Input() busDestination = '';
  @Input() filterBusTypeAC = false;
  @Input() filterBusTypeNonAC = false;
  @Input() filterSeatTypeSeater = false;
  @Input() filterSeatTypeSleeper = false;
  @Input() filterSingleSeatOnly = false;
  @Input() depBands: any = { before6am: false, from6to12: false, from12to18: false, after6pm: false };
  @Input() arrBands: any = { before6am: false, from6to12: false, from12to18: false, after6pm: false };
  @Input() countBusTypeAC = 0;
  @Input() countBusTypeNonAC = 0;
  @Input() countSeatTypeSeater = 0;
  @Input() countSeatTypeSleeper = 0;
  @Input() countDepBefore6am = 0;
  @Input() countDep6to12 = 0;
  @Input() countDep12to18 = 0;
  @Input() countDepAfter6pm = 0;
  @Input() countArrBefore6am = 0;
  @Input() countArr6to12 = 0;
  @Input() countArr12to18 = 0;
  @Input() countArrAfter6pm = 0;
  @Input() availablePickupPoints: any[] = [];
  @Input() displayedPickupPoints: any[] = [];
  @Input() selectedPickupPoints: string[] = [];
  @Input() showAllPickupPoints = false;
  @Input() pickupSearchQuery = '';
  @Input() availableDroppingPoints: any[] = [];
  @Input() displayedDroppingPoints: any[] = [];
  @Input() selectedDroppingPoints: string[] = [];
  @Input() showAllDroppingPoints = false;
  @Input() droppingSearchQuery = '';
  @Input() amenityDefinitions: any[] = [];
  @Input() selectedAmenities: string[] = [];
  @Input() filterMinPrice = 0;
  @Input() filterMaxPrice = 3000;
  @Input() defaultMinPrice = 0;
  @Input() defaultMaxPrice = 3000;
  @Input() hasActiveFilters = false;
  @Input() filterSectionsOpen: Record<string, boolean> = {};
  @Input() amenityCounts: Record<string, number> = {};

  @Output() filterChange = new EventEmitter<any>();
  @Output() clearAll = new EventEmitter<void>();
  @Output() togglePickupPoint = new EventEmitter<string>();
  @Output() toggleDroppingPoint = new EventEmitter<string>();
  @Output() toggleAmenity = new EventEmitter<string>();
  @Output() toggleSection = new EventEmitter<string>();
  @Output() priceChange = new EventEmitter<number>();

  onFilterChange() {
    this.filterChange.emit({
      filterBusTypeAC: this.filterBusTypeAC,
      filterBusTypeNonAC: this.filterBusTypeNonAC,
      filterSeatTypeSeater: this.filterSeatTypeSeater,
      filterSeatTypeSleeper: this.filterSeatTypeSleeper,
      filterSingleSeatOnly: this.filterSingleSeatOnly,
      depBands: this.depBands,
      arrBands: this.arrBands,
      pickupSearchQuery: this.pickupSearchQuery,
      droppingSearchQuery: this.droppingSearchQuery
    });
  }

  onPriceChange() {
    this.priceChange.emit(this.filterMaxPrice);
  }

  onClearAll() {
    this.clearAll.emit();
  }

  getAmenityCount(name: string): number {
    return (this.amenityCounts && this.amenityCounts[name]) || 0;
  }
}
