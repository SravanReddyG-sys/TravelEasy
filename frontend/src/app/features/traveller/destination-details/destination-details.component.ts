import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Destination } from '../../../models/destination.model';

@Component({
  selector: 'app-destination-details',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="destination-details-view" *ngIf="destination">
      <!-- Back Button -->
      <button class="btn btn-link text-decoration-none text-muted mb-3 p-0 fw-semibold d-inline-flex align-items-center gap-2" (click)="back.emit()">
        <i class="fa-solid fa-arrow-left"></i> Back to Dashboard
      </button>

      <!-- Destination Hero Banner -->
      <div class="dest-details-hero position-relative rounded-4 overflow-hidden mb-4">
        <img [src]="destination.heroImage" [alt]="destination.name" class="w-100 h-100 object-fit-cover position-absolute top-0 start-0">
        <div class="dest-hero-overlay position-relative p-4 p-md-5 d-flex flex-column justify-content-between h-100">
          <div class="d-flex justify-content-between align-items-start">
            <div>
              <h1 class="display-5 fw-bold text-white mb-2">{{ destination.name }}</h1>
              <div class="dest-tags text-white-50 fw-semibold mb-3 fs-5">{{ destination.category }}</div>
              <p class="dest-description text-white opacity-90 max-w-600 mb-0">
                {{ destination.description }}
              </p>
            </div>
            <!-- Save Destination Heart -->
            <button 
              class="btn-fav-large rounded-circle bg-white border-0 shadow d-flex align-items-center justify-content-center"
              (click)="toggleFavourite.emit(destination)"
              [title]="isFavourited ? 'Remove from Saved' : 'Save to Favourites'"
            >
              <i [class]="isFavourited ? 'fa-solid fa-heart text-danger fs-5' : 'fa-regular fa-heart text-secondary fs-5'"></i>
            </button>
          </div>
        </div>
      </div>

      <!-- Destination Sub-Tabs -->
      <div class="dest-subtabs-nav d-flex gap-2 mb-4 overflow-x-auto pb-2">
        <button 
          *ngFor="let t of ['Overview', 'Top Attractions', 'Travel Options', 'Best Time', 'Photos']"
          class="btn rounded-pill px-4 py-2 fw-semibold text-nowrap"
          [class.btn-primary]="selectedTab === t"
          [class.btn-light]="selectedTab !== t"
          (click)="selectedTab = t"
        >
          {{ t }}
        </button>
      </div>

      <!-- Tab Content: Overview / Top Attractions -->
      <div class="row g-4" *ngIf="selectedTab === 'Overview' || selectedTab === 'Top Attractions'">
        <!-- Left Column: Top Attractions Cards -->
        <div class="col-lg-8">
          <div class="d-flex justify-content-between align-items-center mb-3">
            <h4 class="fw-bold text-navy mb-0">Top Attractions</h4>
            <span class="text-muted small">Highlights in {{ destination.name }}</span>
          </div>

          <div class="row row-cols-1 row-cols-sm-2 g-3">
            <div *ngFor="let att of destination.attractions" class="col">
              <div class="attraction-card bg-white rounded-4 border overflow-hidden shadow-sm h-100">
                <div class="position-relative">
                  <img [src]="att.image" [alt]="att.name" class="w-100 object-fit-cover" style="height: 140px;">
                </div>
                <div class="p-3">
                  <h6 class="fw-bold text-navy mb-1">{{ att.name }}</h6>
                  <span class="badge bg-light text-secondary border small">{{ att.category }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Right Column: Plan Your Trip Action Card -->
        <div class="col-lg-4">
          <div class="plan-trip-card bg-white rounded-4 p-4 border shadow-sm sticky-top" style="top: 90px;">
            <h5 class="fw-bold text-navy mb-4">Plan Your Trip to {{ destination.name }}</h5>

            <!-- Find Buses Action -->
            <div class="plan-action-box d-flex align-items-center justify-content-between p-3 rounded-4 border mb-3 cursor-pointer" (click)="findBuses.emit(destination.name)">
              <div class="d-flex align-items-center gap-3">
                <div class="action-icon-squircle bg-primary text-white d-flex align-items-center justify-content-center">
                  <i class="fa-solid fa-bus fs-5"></i>
                </div>
                <div>
                  <div class="fw-bold text-navy">Find Buses</div>
                  <small class="text-muted">Search buses to {{ destination.name }}</small>
                </div>
              </div>
              <i class="fa-solid fa-chevron-right text-muted"></i>
            </div>

            <!-- Find Hotels Action -->
            <div class="plan-action-box d-flex align-items-center justify-content-between p-3 rounded-4 border cursor-pointer" (click)="findHotels.emit(destination.name)">
              <div class="d-flex align-items-center gap-3">
                <div class="action-icon-squircle bg-indigo text-white d-flex align-items-center justify-content-center">
                  <i class="fa-solid fa-hotel fs-5"></i>
                </div>
                <div>
                  <div class="fw-bold text-navy">Find Hotels</div>
                  <small class="text-muted">Search hotels in {{ destination.name }}</small>
                </div>
              </div>
              <i class="fa-solid fa-chevron-right text-muted"></i>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab Content: Travel Options -->
      <div *ngIf="selectedTab === 'Travel Options'" class="card p-4 rounded-4 border bg-white shadow-sm">
        <h5 class="fw-bold text-navy mb-3"><i class="fa-solid fa-route text-primary me-2"></i>How to Reach {{ destination.name }}</h5>
        <p class="text-muted">{{ destination.travelOptions }}</p>
        <div class="d-flex gap-3 mt-3">
          <button class="btn btn-primary rounded-pill px-4 fw-bold" (click)="findBuses.emit(destination.name)">
            <i class="fa-solid fa-bus me-2"></i>Check Bus Schedules
          </button>
          <button class="btn btn-outline-primary rounded-pill px-4 fw-bold" (click)="findHotels.emit(destination.name)">
            <i class="fa-solid fa-hotel me-2"></i>Explore Nearby Hotels
          </button>
        </div>
      </div>

      <!-- Tab Content: Best Time -->
      <div *ngIf="selectedTab === 'Best Time'" class="card p-4 rounded-4 border bg-white shadow-sm">
        <h5 class="fw-bold text-navy mb-3"><i class="fa-solid fa-cloud-sun text-warning me-2"></i>Best Time to Visit</h5>
        <p class="lead text-dark fw-semibold">{{ destination.bestTime }}</p>
        <p class="text-muted">Enjoy pleasant weather, outdoor excursions, sightseeing, and local cultural festivals during these peak months.</p>
      </div>

      <!-- Tab Content: Photos -->
      <div *ngIf="selectedTab === 'Photos'" class="row g-3">
        <div class="col-md-6">
          <img [src]="destination.heroImage" class="w-100 rounded-4 shadow-sm object-fit-cover" style="height: 300px;" alt="Photo 1">
        </div>
        <div *ngFor="let att of destination.attractions" class="col-md-3">
          <img [src]="att.image" class="w-100 rounded-4 shadow-sm object-fit-cover" style="height: 300px;" [alt]="att.name">
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dest-details-hero {
      height: 360px;
    }
    .dest-hero-overlay {
      background: linear-gradient(180deg, rgba(15, 23, 42, 0.4) 0%, rgba(15, 23, 42, 0.85) 100%);
    }
    .max-w-600 {
      max-width: 600px;
    }
    .btn-fav-large {
      width: 48px;
      height: 48px;
    }
    .text-navy {
      color: #0F172A;
    }
    .action-icon-squircle {
      width: 44px;
      height: 44px;
      border-radius: 12px;
    }
    .bg-indigo {
      background-color: #6366F1;
    }
    .plan-action-box {
      transition: all 0.2s ease;
    }
    .plan-action-box:hover {
      background-color: #F8FAFC;
      border-color: #0875E1 !important;
      transform: translateX(4px);
    }
    .cursor-pointer {
      cursor: pointer;
    }
  `]
})
export class DestinationDetailsComponent {
  @Input({ required: true }) destination!: Destination;
  @Input() isFavourited = false;

  @Output() back = new EventEmitter<void>();
  @Output() toggleFavourite = new EventEmitter<Destination>();
  @Output() findBuses = new EventEmitter<string>();
  @Output() findHotels = new EventEmitter<string>();

  selectedTab = 'Overview';
}
