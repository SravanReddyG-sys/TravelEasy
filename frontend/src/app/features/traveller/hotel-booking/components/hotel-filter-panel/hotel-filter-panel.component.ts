import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface PropertyTypeOption {
  type: string;
  label: string;
  count: number;
}

export interface AmenityOption {
  key: string;
  label: string;
  count: number;
}

@Component({
  selector: 'app-hotel-filter-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="hotel-filter-card bg-white rounded-4 border p-4 shadow-sm">
      <!-- Filters Header -->
      <div class="d-flex justify-content-between align-items-center mb-3 pb-2 border-bottom">
        <h6 class="fw-bold text-navy mb-0 d-flex align-items-center gap-2">
          <i class="fa-solid fa-sliders text-teal"></i>
          <span>Filters</span>
        </h6>
        <button
          type="button"
          class="btn btn-link btn-clear-all p-0 text-decoration-none fw-semibold"
          (click)="onClearAll()"
          [disabled]="!hasActiveFilters"
        >
          Clear All
        </button>
      </div>

      <!-- 1. Price Per Night -->
      <div class="filter-section border-bottom py-3">
        <div class="section-toggle d-flex justify-content-between align-items-center mb-2" (click)="sectionsOpen.price = !sectionsOpen.price">
          <span class="section-title fw-bold text-navy">Price per night (per room)</span>
          <i class="fa-solid fa-chevron-up text-muted smaller transition-transform" [class.rotate-180]="!sectionsOpen.price"></i>
        </div>

        <div *ngIf="sectionsOpen.price" class="section-content mt-2">
          <div class="price-range-label fw-bold text-navy mb-2">
            ₹{{ minPriceSelected | number }} – ₹{{ maxPriceSelected >= maxPriceLimit ? (maxPriceLimit | number) + '+' : (maxPriceSelected | number) }}
          </div>
          
          <input
            type="range"
            class="form-range custom-range"
            [min]="minPriceLimit"
            [max]="maxPriceLimit"
            [step]="100"
            [(ngModel)]="maxPriceSelected"
            (ngModelChange)="onPriceSliderChange($event)"
          >
          
          <div class="d-flex justify-content-between text-muted smaller mt-1">
            <span>₹{{ minPriceLimit | number }}</span>
            <span>₹{{ maxPriceLimit | number }}+</span>
          </div>
        </div>
      </div>

      <!-- 2. Property Type -->
      <div class="filter-section border-bottom py-3">
        <div class="section-toggle d-flex justify-content-between align-items-center mb-2" (click)="sectionsOpen.propertyType = !sectionsOpen.propertyType">
          <span class="section-title fw-bold text-navy">Property Type</span>
          <i class="fa-solid fa-chevron-up text-muted smaller transition-transform" [class.rotate-180]="!sectionsOpen.propertyType"></i>
        </div>

        <div *ngIf="sectionsOpen.propertyType" class="section-content mt-2">
          <div *ngFor="let pt of availablePropertyTypes" class="form-check custom-checkbox d-flex align-items-center justify-content-between mb-2">
            <div class="d-flex align-items-center gap-2">
              <input
                type="checkbox"
                class="form-check-input"
                [id]="'pt_' + pt.type"
                [checked]="selectedPropertyTypes.includes(pt.type)"
                (change)="togglePropertyType(pt.type)"
              >
              <label class="form-check-label text-dark small cursor-pointer" [for]="'pt_' + pt.type">
                {{ pt.label }}
              </label>
            </div>
            <span class="text-muted smaller">({{ pt.count }})</span>
          </div>

          <div *ngIf="availablePropertyTypes.length === 0" class="text-muted smaller fst-italic">
            No property types available
          </div>
        </div>
      </div>

      <!-- 3. Amenities -->
      <div class="filter-section py-3">
        <div class="section-toggle d-flex justify-content-between align-items-center mb-2" (click)="sectionsOpen.amenities = !sectionsOpen.amenities">
          <span class="section-title fw-bold text-navy">Amenities</span>
          <i class="fa-solid fa-chevron-up text-muted smaller transition-transform" [class.rotate-180]="!sectionsOpen.amenities"></i>
        </div>

        <div *ngIf="sectionsOpen.amenities" class="section-content mt-2">
          <div *ngFor="let am of availableAmenities" class="form-check custom-checkbox d-flex align-items-center justify-content-between mb-2">
            <div class="d-flex align-items-center gap-2">
              <input
                type="checkbox"
                class="form-check-input"
                [id]="'am_' + am.key"
                [checked]="selectedAmenities.includes(am.key)"
                (change)="toggleAmenity(am.key)"
              >
              <label class="form-check-label text-dark small cursor-pointer" [for]="'am_' + am.key">
                {{ am.label }}
              </label>
            </div>
            <span class="text-muted smaller">({{ am.count }})</span>
          </div>

          <div *ngIf="availableAmenities.length === 0" class="text-muted smaller fst-italic">
            No amenities available
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
    .text-teal {
      color: #0891B2;
    }
    .cursor-pointer {
      cursor: pointer;
    }

    .hotel-filter-card {
      border-color: #E2E8F0;
    }

    .btn-clear-all {
      color: #0891B2;
      font-size: 0.82rem;
    }

    .btn-clear-all:hover:not(:disabled) {
      color: #0E7490;
      text-decoration: underline !important;
    }

    .btn-clear-all:disabled {
      color: #94A3B8;
      cursor: not-allowed;
    }

    .section-toggle {
      cursor: pointer;
      user-select: none;
    }

    .section-title {
      font-size: 0.90rem;
    }

    .transition-transform {
      transition: transform 0.2s ease;
    }

    .rotate-180 {
      transform: rotate(180deg);
    }

    .price-range-label {
      font-size: 0.88rem;
      color: #0891B2;
    }

    /* Range slider custom styling */
    .custom-range {
      accent-color: #0891B2;
      cursor: pointer;
    }

    .custom-range::-webkit-slider-runnable-track {
      background-color: #E2E8F0;
      height: 6px;
      border-radius: 4px;
    }

    .custom-range::-webkit-slider-thumb {
      background-color: #0891B2;
      border: 2px solid #FFFFFF;
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.25);
    }

    /* Custom Checkbox */
    .custom-checkbox .form-check-input {
      cursor: pointer;
      border-color: #CBD5E1;
      border-radius: 4px;
    }

    .custom-checkbox .form-check-input:checked {
      background-color: #0891B2;
      border-color: #0891B2;
    }

    .custom-checkbox .form-check-input:focus {
      border-color: #0891B2;
      box-shadow: 0 0 0 0.25rem rgba(8, 145, 178, 0.15);
    }
  `]
})
export class HotelFilterPanelComponent {
  @Input() minPriceLimit = 1000;
  @Input() maxPriceLimit = 10000;
  @Input() minPriceSelected = 1000;
  @Input() maxPriceSelected = 10000;
  @Input() availablePropertyTypes: PropertyTypeOption[] = [];
  @Input() selectedPropertyTypes: string[] = [];
  @Input() availableAmenities: AmenityOption[] = [];
  @Input() selectedAmenities: string[] = [];

  @Output() priceChange = new EventEmitter<{ min: number; max: number }>();
  @Output() propertyTypeChange = new EventEmitter<string[]>();
  @Output() amenityChange = new EventEmitter<string[]>();
  @Output() clearAll = new EventEmitter<void>();

  sectionsOpen = {
    price: true,
    propertyType: true,
    amenities: true
  };

  get hasActiveFilters(): boolean {
    const isPriceFiltered = this.maxPriceSelected < this.maxPriceLimit || this.minPriceSelected > this.minPriceLimit;
    const isPropertyFiltered = this.selectedPropertyTypes.length > 0;
    const isAmenityFiltered = this.selectedAmenities.length > 0;
    return isPriceFiltered || isPropertyFiltered || isAmenityFiltered;
  }

  onPriceSliderChange(val: number) {
    this.maxPriceSelected = Number(val);
    this.priceChange.emit({
      min: this.minPriceSelected,
      max: this.maxPriceSelected
    });
  }

  togglePropertyType(type: string) {
    const idx = this.selectedPropertyTypes.indexOf(type);
    if (idx >= 0) {
      this.selectedPropertyTypes.splice(idx, 1);
    } else {
      this.selectedPropertyTypes.push(type);
    }
    this.propertyTypeChange.emit([...this.selectedPropertyTypes]);
  }

  toggleAmenity(key: string) {
    const idx = this.selectedAmenities.indexOf(key);
    if (idx >= 0) {
      this.selectedAmenities.splice(idx, 1);
    } else {
      this.selectedAmenities.push(key);
    }
    this.amenityChange.emit([...this.selectedAmenities]);
  }

  onClearAll() {
    this.minPriceSelected = this.minPriceLimit;
    this.maxPriceSelected = this.maxPriceLimit;
    this.selectedPropertyTypes = [];
    this.selectedAmenities = [];
    this.clearAll.emit();
  }
}
