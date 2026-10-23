import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TripSeatLayout, DeckLayout, SeatPosition } from '../../../../../models/bus.model';
import { BookingService } from '../../../../../services/booking.service';
import { BusService } from '../../../../../services/bus.service';

export interface PassengerInfo {
  seatNumber: string;
  name: string;
  age: number | null;
  gender: string;
  idProof?: string;
}

@Component({
  selector: 'app-bus-seat-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="seat-selection-page-wrapper py-0">
      
      <!-- ================= HEADER NAVIGATION & BREADCRUMBS ================= -->
      <div class="header-nav-container mb-2">
        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2">
          <div>
            <!-- Back button: dynamic based on current stage -->
            <button class="btn btn-link btn-back-results p-0 text-decoration-none d-inline-flex align-items-center mb-1" 
                    (click)="onHeaderBack()">
              <i class="fa-solid fa-arrow-left me-1 small"></i>
              <span>{{ backButtonText }}</span>
            </button>
            <div class="d-flex align-items-baseline gap-2">
              <h5 class="fw-bold text-navy mb-0 page-title">
                {{ pageTitleText }}
              </h5>
            </div>
          </div>

          <!-- 3-Stage Booking Progress Indicator -->
          <div class="stepper-progress-wrap d-flex align-items-center">
            <!-- Stage 1 -->
            <div class="step-item d-flex align-items-center" [class.active]="currentStage === 1" [class.completed]="currentStage > 1">
              <span class="step-circle me-1">
                <i *ngIf="currentStage > 1" class="fa-solid fa-check"></i>
                <span *ngIf="currentStage <= 1">1</span>
              </span>
              <span class="step-label">Select Seats</span>
            </div>

            <div class="step-connector" [class.completed]="currentStage > 1"></div>

            <!-- Stage 2 -->
            <div class="step-item d-flex align-items-center" [class.active]="currentStage === 2" [class.completed]="currentStage > 2">
              <span class="step-circle me-1">
                <i *ngIf="currentStage > 2" class="fa-solid fa-check"></i>
                <span *ngIf="currentStage <= 2">2</span>
              </span>
              <span class="step-label">Passenger Details</span>
            </div>

            <div class="step-connector" [class.completed]="currentStage > 2"></div>

            <!-- Stage 3 -->
            <div class="step-item d-flex align-items-center" [class.active]="currentStage === 3" [class.completed]="paymentSuccess">
              <span class="step-circle me-1">
                <i *ngIf="paymentSuccess" class="fa-solid fa-check"></i>
                <span *ngIf="!paymentSuccess">3</span>
              </span>
              <span class="step-label">Payment</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ================= SELECTED BUS & JOURNEY SUMMARY CARD ================= -->
      <!-- Approved 4-section layout matching reference mockup: Image | Operator | Journey Details | Duration -->
      <div class="selected-bus-summary-card bg-white rounded-3 mb-2">
        <div class="summary-card-inner">
          
          <!-- Section 1: Bus Image (17-19%) -->
          <div class="summary-section-image">
            <img [src]="busThumbnail" class="summary-bus-img" alt="Bus photo" (error)="onThumbError($event)">
          </div>

          <!-- Section 2: Operator & Bus Information (26-29%) -->
          <div class="summary-section-operator">
            <div class="d-flex align-items-center gap-2 mb-1">
              <span class="operator-heading text-truncate" [title]="displayOperatorName">{{ displayOperatorName }}</span>
              <span class="badge-ac-status" [class.badge-non-ac]="isNonAc">
                {{ displayFacilityType }}
              </span>
            </div>
            <div class="bus-info-line text-truncate" [title]="displayBusModel">{{ displayBusModel }}</div>
            <div class="bus-info-line text-truncate" [title]="displayConfigurationText">{{ displayConfigurationText }}</div>
          </div>

          <!-- Vertical Divider 1 -->
          <div class="summary-vertical-divider d-none d-lg-block"></div>

          <!-- Section 3: Source to Destination Journey Details (40-42%) -->
          <div class="summary-section-journey">
            
            <!-- Source Endpoint -->
            <div class="journey-endpoint text-start">
              <div class="endpoint-location-row d-flex align-items-center gap-1 mb-1">
                <svg class="endpoint-pin-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#64748B" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                  <circle cx="12" cy="10" r="3"></circle>
                </svg>
                <span class="endpoint-city-name text-truncate" [title]="displayOrigin">{{ displayOrigin }}</span>
              </div>
              <div class="endpoint-time-val">{{ departureTimeFormatted }}</div>
              <div class="endpoint-date-val">{{ departureDateFormatted }}</div>
            </div>

            <!-- Connector Graphic -->
            <div class="journey-connector-track d-flex align-items-center flex-grow-1 mx-2 mx-xl-3">
              <span class="connector-end-dot"></span>
              <div class="connector-dotted-segment"></div>
              <div class="connector-bus-icon-wrap">
                <i class="fa-solid fa-bus text-primary"></i>
              </div>
              <div class="connector-dotted-segment"></div>
              <span class="connector-end-dot"></span>
            </div>

            <!-- Destination Endpoint -->
            <div class="journey-endpoint text-start">
              <div class="endpoint-location-row d-flex align-items-center gap-1 mb-1">
                <svg class="endpoint-pin-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#64748B" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                  <circle cx="12" cy="10" r="3"></circle>
                </svg>
                <span class="endpoint-city-name text-truncate" [title]="displayDestination">{{ displayDestination }}</span>
              </div>
              <div class="endpoint-time-val">{{ arrivalTimeFormatted }}</div>
              <div class="endpoint-date-val">{{ arrivalDateFormatted }}</div>
            </div>

          </div>

          <!-- Vertical Divider 2 -->
          <div class="summary-vertical-divider d-none d-lg-block"></div>

          <!-- Section 4: Journey Duration (13-15%) -->
          <div class="summary-section-duration d-flex align-items-center gap-2">
            <svg class="duration-clock-svg flex-shrink-0" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#64748B" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"></circle>
              <polyline points="12 6 12 12 16 14"></polyline>
            </svg>
            <div class="duration-text-group">
              <div class="duration-meta-label">Duration</div>
              <div class="duration-time-val">{{ durationText }}</div>
            </div>
          </div>

        </div>
      </div>

      <!-- ================= LOADING STATE ================= -->
      <div *ngIf="isLoading" class="bg-white rounded-4 border p-5 text-center shadow-sm my-4">
        <div class="spinner-border text-primary mb-3" style="width: 3rem; height: 3rem;" role="status"></div>
        <h5 class="fw-bold text-navy">Loading Physical Seat Layout...</h5>
        <p class="text-muted small mb-0">Retrieving real-time seat inventory and vehicle configuration</p>
      </div>

      <!-- ================= ERROR / EMPTY STATE ================= -->
      <div *ngIf="!isLoading && displayDecks.length === 0" class="alert alert-warning rounded-4 p-4 shadow-sm my-4">
        <div class="d-flex align-items-center gap-3">
          <i class="fa-solid fa-triangle-exclamation fs-3 text-warning"></i>
          <div>
            <h6 class="fw-bold mb-1">Seat Layout Unavailable</h6>
            <p class="mb-0 small">
              Unable to load the physical seat layout for this schedule. Please try refreshing or select another bus.
            </p>
          </div>
        </div>
      </div>

      <!-- ================= STAGE 1: SEAT SELECTION 3-COLUMN CONTENT ================= -->
      <div *ngIf="!isLoading && displayDecks.length > 0 && currentStage === 1" class="seat-selection-three-columns-grid">
        
        <!-- ================= COLUMN 1: SEAT LAYOUT ================= -->
        <div class="seat-layout-card bg-white rounded-3 border p-3 shadow-xs">
          <!-- Card Header matching Reference Screenshot -->
          <div class="mb-3">
            <h5 class="fw-bold text-navy mb-0 card-main-heading">Seat Layout</h5>
            <div class="text-secondary small mt-0">Click on a seat to select or deselect</div>
          </div>

            <!-- Deck selector toggle for mobile/tablets -->
            <div *ngIf="isDoubleDeck" class="d-xl-none d-lg-none d-flex gap-1 mb-2">
              <button class="btn btn-xs rounded-pill px-2" 
                      [class.btn-primary]="activeDeckView === 'ALL'" 
                      [class.btn-outline-secondary]="activeDeckView !== 'ALL'"
                      (click)="activeDeckView = 'ALL'">Both</button>
              <button class="btn btn-xs rounded-pill px-2" 
                      [class.btn-primary]="activeDeckView === 'LOWER'" 
                      [class.btn-outline-secondary]="activeDeckView !== 'LOWER'"
                      (click)="activeDeckView = 'LOWER'">Lower</button>
              <button class="btn btn-xs rounded-pill px-2" 
                      [class.btn-primary]="activeDeckView === 'UPPER'" 
                      [class.btn-outline-secondary]="activeDeckView !== 'UPPER'"
                      (click)="activeDeckView = 'UPPER'">Upper</button>
            </div>

            <!-- Decks Container (Side by side on desktop, equal length) -->
            <div class="decks-container d-flex flex-wrap gap-2 gap-xl-3 justify-content-center align-items-stretch flex-grow-1">
              
              <ng-container *ngFor="let deck of displayDecks">
                <div *ngIf="activeDeckView === 'ALL' || activeDeckView === deck.deck" 
                     class="deck-column flex-grow-1">
                  
                  <!-- Deck Header Title (e.g. Lower Deck (23), Upper Deck (15)) -->
                  <div class="deck-title-header mb-2 text-center fw-semibold text-secondary">
                    {{ getDeckHeaderTitle(deck) }}
                  </div>

                  <!-- Bus Outline Container (Equal Length) -->
                  <div class="bus-body-outline p-2 rounded-3">
                    
                    <!-- Top Cabin Header: Steering Wheel on Lower Deck (top right), Spacer on Upper Deck -->
                    <div class="deck-top-cab d-flex justify-content-end mb-1">
                      <svg *ngIf="deck.deck === 'LOWER' || !isDoubleDeck" 
                           class="steering-wheel-svg" width="18" height="18" viewBox="0 0 24 24" 
                           fill="none" stroke="#9e9e9e" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"
                           title="Driver Position">
                        <circle cx="12" cy="12" r="9"/>
                        <circle cx="12" cy="12" r="2.5"/>
                        <line x1="3" y1="12" x2="9.5" y2="12"/>
                        <line x1="14.5" y1="12" x2="21" y2="12"/>
                        <line x1="12" y1="14.5" x2="12" y2="21"/>
                      </svg>
                      <!-- Invisible placeholder for Upper Deck to align seats horizontally -->
                      <div *ngIf="deck.deck === 'UPPER' && isDoubleDeck" style="width: 18px; height: 18px;"></div>
                    </div>

                    <!-- Unified Bus Deck CSS Grid -->
                    <div class="bus-deck-grid" 
                         [style.gridTemplateColumns]="getDeckGridTemplateColumns(deck)"
                         [style.gridTemplateRows]="'repeat(' + getDeckTotalGridRows(deck) + ', var(--row-unit-h))'">
                      <div *ngFor="let pos of deck.positions" 
                           class="grid-seat-slot d-flex align-items-center justify-content-center"
                           [style.gridColumn]="getGridColumn(deck, pos)"
                           [style.gridRow]="getGridRow(deck, pos)">
                        <ng-container *ngTemplateOutlet="seatTemplate; context: { pos: pos }"></ng-container>
                      </div>
                    </div>

                  </div>

                </div>
              </ng-container>

            </div>

          </div>

        <!-- ================= COLUMN 2: LEGEND & BOARDING/DROPPING POINTS ================= -->
        <div class="middle-column-stack d-flex flex-column gap-3">
          
          <!-- Card A: Seat Legend (Collapsible) -->
          <div class="bg-white rounded-3 border p-3 shadow-xs collapsible-card seat-legend-card">
            <div class="d-flex justify-content-between align-items-center cursor-pointer" 
                 (click)="legendExpanded = !legendExpanded">
              <h6 class="fw-bold text-navy mb-0" style="font-size: 0.95rem;">Seat Legend</h6>
              <button class="btn btn-sm btn-link p-0 text-secondary">
                <i class="fa-solid fa-xs" [class.fa-chevron-up]="legendExpanded" [class.fa-chevron-down]="!legendExpanded"></i>
              </button>
            </div>

            <!-- Subheading -->
            <div class="text-secondary small fw-medium mt-1 mb-2">Seater / Sleeper info</div>

            <!-- Expanded Legend Content matching Screenshot 2 -->
            <div *ngIf="legendExpanded" class="legend-matrix-table pt-1">
              
              <!-- Column Headers -->
              <div class="legend-row d-flex align-items-center pb-2">
                <div class="legend-col-label"></div>
                <div class="legend-col-head">Unisex</div>
                <div class="legend-col-head">Male</div>
                <div class="legend-col-head">Female</div>
              </div>

              <!-- Row 1: Available -->
              <div class="legend-row d-flex align-items-center my-1">
                <div class="legend-col-label">Available</div>
                
                <!-- Unisex Available -->
                <div class="legend-col-cell">
                  <div class="legend-icon-box avail-unisex"></div>
                </div>
                
                <!-- Male Available -->
                <div class="legend-col-cell">
                  <div class="legend-icon-box avail-male">
                    <svg width="14" height="14" viewBox="0 0 20 20" fill="none" stroke="#3b82f6" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="10" cy="6.5" r="3.2"/>
                      <path d="M5 16 C5 12.5, 7.5 11.5, 10 11.5 C12.5 11.5, 15 12.5, 15 16"/>
                    </svg>
                  </div>
                </div>
                
                <!-- Female Available -->
                <div class="legend-col-cell">
                  <div class="legend-icon-box avail-female">
                    <svg width="14" height="14" viewBox="0 0 20 20" fill="none" stroke="#b854d4" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="10" cy="6.5" r="3.2"/>
                      <path d="M7 6.5 C7 4.5, 13 4.5, 13 6.5"/>
                      <path d="M5 16 C5 13, 7.5 12, 10 12 C12.5 12, 15 13, 15 16"/>
                    </svg>
                  </div>
                </div>
              </div>

              <!-- Row 2: Selected -->
              <div class="legend-row d-flex align-items-center my-1">
                <div class="legend-col-label">Selected</div>
                
                <!-- Unisex Selected -->
                <div class="legend-col-cell">
                  <div class="legend-icon-box sel-unisex"></div>
                </div>
                
                <!-- Male Selected -->
                <div class="legend-col-cell">
                  <div class="legend-icon-box sel-male">
                    <svg width="14" height="14" viewBox="0 0 20 20" fill="none" stroke="#2563eb" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="10" cy="6.5" r="3.2"/>
                      <path d="M5 16 C5 12.5, 7.5 11.5, 10 11.5 C12.5 11.5, 15 12.5, 15 16"/>
                    </svg>
                  </div>
                </div>
                
                <!-- Female Selected -->
                <div class="legend-col-cell">
                  <div class="legend-icon-box sel-female">
                    <svg width="14" height="14" viewBox="0 0 20 20" fill="none" stroke="#b854d4" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="10" cy="6.5" r="3.2"/>
                      <path d="M7 6.5 C7 4.5, 13 4.5, 13 6.5"/>
                      <path d="M5 16 C5 13, 7.5 12, 10 12 C12.5 12, 15 13, 15 16"/>
                    </svg>
                  </div>
                </div>
              </div>

              <!-- Row 3: Booked -->
              <div class="legend-row d-flex align-items-center my-1">
                <div class="legend-col-label">Booked</div>
                
                <!-- Unisex Booked -->
                <div class="legend-col-cell">
                  <div class="legend-icon-box book-unisex"></div>
                </div>
                
                <!-- Male Booked -->
                <div class="legend-col-cell">
                  <div class="legend-icon-box book-male">
                    <svg width="14" height="14" viewBox="0 0 20 20" fill="none" stroke="#b0b0b0" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="10" cy="6.5" r="3.2"/>
                      <path d="M5 16 C5 12.5, 7.5 11.5, 10 11.5 C12.5 11.5, 15 12.5, 15 16"/>
                    </svg>
                  </div>
                </div>
                
                <!-- Female Booked -->
                <div class="legend-col-cell">
                  <div class="legend-icon-box book-female">
                    <svg width="14" height="14" viewBox="0 0 20 20" fill="none" stroke="#b0b0b0" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="10" cy="6.5" r="3.2"/>
                      <path d="M7 6.5 C7 4.5, 13 4.5, 13 6.5"/>
                      <path d="M5 16 C5 13, 7.5 12, 10 12 C12.5 12, 15 13, 15 16"/>
                    </svg>
                  </div>
                </div>
              </div>

            </div>
          </div>

          <!-- Card B: Boarding and Dropping Points (Collapsible) -->
          <div class="bg-white rounded-3 border p-3 shadow-xs collapsible-card points-selector-card">
            <div class="d-flex justify-content-between align-items-center cursor-pointer mb-2" 
                 (click)="pointsExpanded = !pointsExpanded">
              <h6 class="fw-bold text-navy mb-0" style="font-size: 0.95rem;">Boarding and Dropping Points</h6>
              <button class="btn btn-sm btn-link p-0 text-secondary">
                <i class="fa-solid fa-xs" [class.fa-chevron-up]="pointsExpanded" [class.fa-chevron-down]="!pointsExpanded"></i>
              </button>
            </div>

            <!-- Collapsed Summary Chips -->
            <div *ngIf="!pointsExpanded" class="mt-2 pt-2 border-top">
              <div class="text-secondary small d-flex flex-column gap-1">
                <div class="text-truncate">
                  <i class="fa-solid fa-location-dot text-success me-1"></i>
                  <span class="fw-semibold text-navy">{{ selectedBoardingPoint || 'Boarding not chosen' }}</span>
                </div>
                <div class="text-truncate">
                  <i class="fa-solid fa-location-dot text-danger me-1"></i>
                  <span class="fw-semibold text-navy">{{ selectedDroppingPoint || 'Dropping not chosen' }}</span>
                </div>
              </div>
            </div>

            <!-- Expanded Points Timeline Layout -->
            <div *ngIf="pointsExpanded" class="mt-3">
              <div class="points-timeline-container position-relative ps-4">
                
                <!-- Vertical Connecting Line between icons -->
                <div class="timeline-vertical-connector"></div>

                <!-- Boarding Point Block -->
                <div class="timeline-item mb-3 position-relative">
                  <span class="timeline-indicator-icon boarding-icon">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#10B981" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                    </svg>
                  </span>
                  <label class="fw-bold text-navy small mb-1 d-block">Boarding Point</label>
                  <select class="form-select form-select-sm custom-points-select" 
                          [ngModel]="selectedBoardingPoint" 
                          (ngModelChange)="onBoardingChange($event)">
                    <option value="" disabled>Select pickup location</option>
                    <option *ngFor="let bp of boardingList" [value]="bp.name">
                      {{ bp.name }} {{ bp.time ? '(' + bp.time + ')' : '' }}
                    </option>
                  </select>
                </div>

                <!-- Dropping Point Block -->
                <div class="timeline-item position-relative">
                  <span class="timeline-indicator-icon dropping-icon">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="#EF4444" stroke="#EF4444" stroke-width="2">
                      <circle cx="12" cy="12" r="7"/>
                    </svg>
                  </span>
                  <label class="fw-bold text-navy small mb-1 d-block">Dropping Point</label>
                  <select class="form-select form-select-sm custom-points-select" 
                          [ngModel]="selectedDroppingPoint" 
                          (ngModelChange)="onDroppingChange($event)">
                    <option value="" disabled>Select drop-off location</option>
                    <option *ngFor="let dp of droppingList" [value]="dp.name">
                      {{ dp.name }} {{ dp.time ? '(' + dp.time + ')' : '' }}
                    </option>
                  </select>
                </div>

              </div>
            </div>

          </div>

        </div>

        <!-- ================= COLUMN 3: YOUR SELECTION & FARE SUMMARY ================= -->
        <div class="bg-white rounded-3 border p-3 shadow-xs your-selection-card d-flex flex-column justify-content-between">
          
          <div>
            <!-- Header: Your Selection with Reset action -->
            <div class="d-flex justify-content-between align-items-center mb-2">
              <h5 class="fw-bold text-navy mb-0 card-main-heading">Your Selection</h5>
              
              <button class="btn btn-link p-0 text-primary text-decoration-none fw-semibold small d-flex align-items-center gap-1 btn-reset-selection" 
                      (click)="onResetSeats()">
                <i class="fa-solid fa-rotate-left small"></i>
                <span>Reset</span>
              </button>
            </div>

            <!-- Selected Seats Row with Count and Remove All -->
            <div class="d-flex justify-content-between align-items-center mb-2">
              <span class="fw-bold text-navy small selected-seats-heading-label">
                Selected Seats ({{ selectedSeats.length }})
              </span>
              
              <button *ngIf="selectedSeats.length > 0" 
                      class="btn btn-link p-0 text-primary text-decoration-none small fw-semibold" 
                      (click)="onRemoveAllSeats()">
                Remove All
              </button>
            </div>

            <!-- Selected Seats List / Empty State -->
            <div class="selected-seats-container mb-3">
              <div *ngIf="selectedSeats.length === 0" class="empty-selection-box py-3 text-center border rounded-2 bg-light">
                <i class="fa-solid fa-chair text-muted mb-1"></i>
                <div class="fw-semibold text-navy small">No seats selected yet</div>
                <div class="text-secondary text-micro px-2">
                  Click available seats in the layout to add.
                </div>
              </div>

              <!-- List of Individual Selected Seats matching Screenshot 3 -->
              <div *ngIf="selectedSeats.length > 0" class="selected-seats-vertical-list d-flex flex-column gap-2" style="max-height: 140px; overflow-y: auto;">
                <div *ngFor="let sn of selectedSeats" class="d-flex align-items-center justify-content-between py-1">
                  <!-- Seat Pill with X button -->
                  <span class="selected-seat-badge-pill d-inline-flex align-items-center gap-2">
                    <span class="badge-seat-name">{{ sn }}</span>
                    <button type="button" class="btn-remove-seat-x p-0 border-0 bg-transparent text-secondary" (click)="onRemoveSeat(sn)" title="Remove seat {{ sn }}">
                      <i class="fa-solid fa-xmark"></i>
                    </button>
                  </span>
                  <!-- Individual Fare on the Right -->
                  <span class="fw-bold text-navy individual-seat-fare">
                    ₹ {{ getSeatPrice(sn) | number:'1.0-0' }}
                  </span>
                </div>
              </div>
            </div>

            <div class="border-top my-2"></div>

            <!-- Fare Breakdown Details matching Screenshot 3 -->
            <div class="fare-breakdown-section mb-2">
              <div class="d-flex justify-content-between align-items-center mb-1">
                <span class="text-navy fw-semibold" style="font-size: 0.88rem;">Fare per seat</span>
                <span class="fw-bold text-navy" style="font-size: 0.95rem;">₹ {{ farePerSeat | number:'1.0-0' }}</span>
              </div>

              <div class="d-flex justify-content-between align-items-center mb-1 text-secondary" style="font-size: 0.82rem;">
                <span>Base Fare</span>
                <span class="fw-medium text-secondary">₹ {{ calculatedBaseFare | number:'1.0-0' }}</span>
              </div>

              <div class="d-flex justify-content-between align-items-center mb-1 text-secondary" style="font-size: 0.82rem;">
                <span class="d-flex align-items-center gap-1">
                  Taxes & Fees 
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#94A3B8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="cursor-pointer" title="Includes 5% GST and platform processing">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="12" y1="16" x2="12" y2="12"></line>
                    <line x1="12" y1="8" x2="12.01" y2="8"></line>
                  </svg>
                </span>
                <span class="fw-medium text-secondary">₹ {{ calculatedTaxes | number:'1.0-0' }}</span>
              </div>
            </div>

            <div class="border-top my-2"></div>

            <!-- Total Amount Row -->
            <div class="d-flex justify-content-between align-items-baseline mb-3">
              <span class="fw-bold text-navy" style="font-size: 1.05rem;">Total Amount</span>
              <span class="fw-bold text-navy total-highlight-price" style="font-size: 1.30rem;">
                ₹ {{ calculatedTotalAmount | number:'1.0-0' }}
              </span>
            </div>

            <!-- Continue Button -->
            <button class="btn btn-primary w-100 py-2 fw-semibold btn-continue-booking d-flex align-items-center justify-content-center gap-2 mb-2"
                    [disabled]="!canProceedToPassengerDetails"
                    (click)="onContinueToPassengerDetails()">
              <span>Continue to Passenger Details</span>
              <i class="fa-solid fa-arrow-right small"></i>
            </button>

            <!-- Validation message if disabled -->
            <div *ngIf="validationNotice" class="text-danger text-center text-micro mb-2">
              <i class="fa-solid fa-circle-exclamation me-1"></i>{{ validationNotice }}
            </div>
          </div>

          <!-- Seat-Hold Information Card Immediately Below Button -->
          <div class="hold-time-notice-box p-2 px-3 rounded-2">
            <div class="d-flex align-items-start gap-2">
              <svg class="hold-info-icon flex-shrink-0 mt-1" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#0284C7" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="16" x2="12" y2="12"></line>
                <line x1="12" y1="8" x2="12.01" y2="8"></line>
              </svg>
              <div>
                <div class="fw-bold text-navy text-micro" style="font-size: 0.80rem;">Hold time: 10 minutes</div>
                <div class="text-secondary text-micro" style="font-size: 0.74rem; line-height: 1.3;">
                  Your selected seats will be held for 10 minutes while you complete your booking.
                </div>
              </div>
            </div>
          </div>

        </div>

      </div>

      <!-- ================= STAGE 2: PASSENGER DETAILS VIEW (APPROVED MOCKUP) ================= -->
      <div *ngIf="currentStage === 2" class="passenger-details-stage-grid">
        
        <!-- ================= LEFT COLUMN: PASSENGER & CONTACT INFORMATION (68-70%) ================= -->
        <div class="col-passenger-forms">
          
          <!-- Card 1: Passenger Information -->
          <div class="passenger-info-card bg-white rounded-3 border p-3 mb-2 mb-xl-3">
            <div class="mb-2">
              <h5 class="fw-bold text-navy mb-0 card-main-heading">Passenger Information</h5>
              <div class="card-sub-text">Enter traveller details for each selected seat</div>
            </div>

            <!-- List of Passenger Cards for each selected seat -->
            <div class="d-flex flex-column gap-2">
              <div *ngFor="let p of passengers; let idx = index" class="passenger-seat-item-card p-2 p-md-2 p-lg-2 px-3 rounded-2 border">
                
                <!-- Passenger Item Header -->
                <div class="d-flex justify-content-between align-items-center mb-1">
                  <span class="fw-bold text-navy passenger-card-title">
                    Passenger {{ idx + 1 }} · Seat {{ p.seatNumber }}
                  </span>
                  
                  <!-- Lead Passenger Badge for Passenger 1 -->
                  <span *ngIf="idx === 0" class="badge badge-lead-passenger">
                    Lead Passenger
                  </span>

                  <!-- Remove Action for Passenger 2+ -->
                  <button *ngIf="idx > 0" 
                          class="btn btn-link text-danger p-0 text-decoration-none d-flex align-items-center gap-1 btn-remove-passenger"
                          (click)="onRemovePassenger(idx)"
                          title="Remove passenger and seat {{ p.seatNumber }}">
                    <i class="fa-regular fa-trash-can small"></i>
                    <span>Remove</span>
                  </button>
                </div>

                <!-- Form Fields in a Single Horizontal Row on Desktop -->
                <div class="passenger-fields-row d-flex flex-wrap gap-2 gap-md-2 align-items-flex-start">
                  
                  <!-- Full Name -->
                  <div class="field-item field-full-name">
                    <label class="form-label field-label mb-1">Full Name <span class="text-danger">*</span></label>
                    <input type="text" 
                           class="form-control form-control-sm custom-passenger-input" 
                           [(ngModel)]="p.name" 
                           placeholder="Enter full name"
                           required>
                  </div>

                  <!-- Age -->
                  <div class="field-item field-age">
                    <label class="form-label field-label mb-1">Age <span class="text-danger">*</span></label>
                    <input type="number" 
                           min="1" 
                           max="120" 
                           class="form-control form-control-sm custom-passenger-input" 
                           [(ngModel)]="p.age" 
                           placeholder="Age"
                           required>
                  </div>

                  <!-- Gender -->
                  <div class="field-item field-gender">
                    <label class="form-label field-label mb-1">Gender <span class="text-danger">*</span></label>
                    <select class="form-select form-select-sm custom-passenger-input" [(ngModel)]="p.gender" required>
                      <option value="Male">Male</option>
                      <option value="Female">Female</option>
                      <option value="Other">Other</option>
                    </select>
                  </div>

                  <!-- ID Proof (Optional) -->
                  <div class="field-item field-id-proof">
                    <label class="form-label field-label mb-1">ID Proof (Optional)</label>
                    <select class="form-select form-select-sm custom-passenger-input" [(ngModel)]="p.idProof">
                      <option value="Aadhaar Card">Aadhaar Card</option>
                      <option value="PAN Card">PAN Card</option>
                      <option value="Passport">Passport</option>
                      <option value="Driving License">Driving License</option>
                      <option value="Voter ID">Voter ID</option>
                      <option value="None">None</option>
                    </select>
                  </div>

                </div>

              </div>
            </div>

          </div>

          <!-- Card 2: Contact Information -->
          <div class="contact-info-card bg-white rounded-3 border p-3">
            <div class="mb-2">
              <h5 class="fw-bold text-navy mb-0 card-main-heading">Contact Information</h5>
              <div class="card-sub-text">Enter contact details for booking confirmation and communication</div>
            </div>

            <!-- Contact Inputs Row -->
            <div class="contact-inputs-row d-flex flex-wrap gap-2 gap-md-3">
              <!-- Mobile Number -->
              <div class="contact-field flex-grow-1">
                <label class="form-label field-label mb-1">Mobile Number <span class="text-danger">*</span></label>
                <input type="tel" 
                       class="form-control form-control-sm custom-passenger-input" 
                       [(ngModel)]="contactPhone" 
                       placeholder="+91 98765 43210"
                       required>
              </div>

              <!-- Email Address -->
              <div class="contact-field flex-grow-1">
                <label class="form-label field-label mb-1">Email Address <span class="text-danger">*</span></label>
                <input type="email" 
                       class="form-control form-control-sm custom-passenger-input" 
                       [(ngModel)]="contactEmail" 
                       placeholder="john.doe@example.com"
                       required>
              </div>
            </div>

            <!-- Informational Notice Strip -->
            <div class="contact-notice-strip p-2 px-3 rounded-2 mt-2 d-flex align-items-center gap-2">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#0284C7" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="flex-shrink-0">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="16" x2="12" y2="12"></line>
                <line x1="12" y1="8" x2="12.01" y2="8"></line>
              </svg>
              <span class="notice-text">
                Your ticket and booking details will be sent to this mobile number and email address.
              </span>
            </div>

          </div>

        </div>

        <!-- ================= RIGHT COLUMN: BOOKING SUMMARY (30-32%) ================= -->
        <div class="col-booking-summary">
          <div class="booking-summary-card bg-white rounded-3 border p-3">
            
            <!-- Summary Heading & Journey Details -->
            <div class="summary-top-header mb-2">
              <h5 class="fw-bold text-navy mb-1 card-main-heading">Booking Summary</h5>
              <div class="fw-bold text-navy route-heading">
                {{ displayOrigin }} → {{ displayDestination }}
              </div>
              <div class="text-secondary small mt-0" style="font-size: 0.82rem;">
                {{ departureDateFormatted }}
              </div>
            </div>

            <!-- Selected Seats Section with Edit Seats Action -->
            <div class="summary-section pt-2 border-top">
              <div class="d-flex justify-content-between align-items-center mb-1">
                <span class="fw-bold text-navy" style="font-size: 0.90rem;">
                  Selected Seats ({{ selectedSeats.length }})
                </span>
                <button class="btn btn-link p-0 text-decoration-none text-primary fw-semibold small d-flex align-items-center gap-1 btn-edit-seats"
                        (click)="onEditSeats()">
                  <i class="fa-solid fa-pencil" style="font-size: 0.70rem;"></i>
                  <span>Edit Seats</span>
                </button>
              </div>

              <!-- Selected Seats List -->
              <div class="d-flex flex-column gap-1 mb-1">
                <div *ngFor="let sn of selectedSeats" class="selected-seat-summary-row d-flex align-items-center justify-content-between py-1">
                  <span class="selected-seat-badge-clean">
                    {{ sn }}
                  </span>
                  <span class="fw-bold text-navy" style="font-size: 0.90rem;">
                    ₹ {{ getSeatPrice(sn) | number:'1.0-0' }}
                  </span>
                </div>
              </div>
            </div>

            <!-- Boarding and Dropping Points Timeline Display -->
            <div class="summary-section pt-2 border-top">
              <!-- Boarding Point -->
              <div class="d-flex align-items-start gap-2 mb-1">
                <span class="timeline-point-dot green-dot mt-1"></span>
                <div class="d-flex justify-content-between align-items-center flex-grow-1">
                  <div>
                    <div class="text-secondary text-micro">Boarding Point</div>
                    <div class="fw-semibold text-navy small text-truncate" style="max-width: 170px;" [title]="selectedBoardingPoint">
                      {{ selectedBoardingPoint }}
                    </div>
                  </div>
                  <div class="fw-bold text-navy small">
                    {{ selectedBoardingTime }}
                  </div>
                </div>
              </div>

              <!-- Vertical Connector Line -->
              <div class="timeline-connector-segment ms-1"></div>

              <!-- Dropping Point -->
              <div class="d-flex align-items-start gap-2 mt-1">
                <span class="timeline-point-dot red-dot mt-1"></span>
                <div class="d-flex justify-content-between align-items-center flex-grow-1">
                  <div>
                    <div class="text-secondary text-micro">Dropping Point</div>
                    <div class="fw-semibold text-navy small text-truncate" style="max-width: 170px;" [title]="selectedDroppingPoint">
                      {{ selectedDroppingPoint }}
                    </div>
                  </div>
                  <div class="fw-bold text-navy small">
                    {{ selectedDroppingTime }}
                  </div>
                </div>
              </div>
            </div>

            <!-- Fare Details Section -->
            <div class="summary-section pt-2 border-top">
              <div class="fw-bold text-navy mb-1" style="font-size: 0.90rem;">
                Fare Details
              </div>
              <div class="d-flex justify-content-between align-items-center mb-1 text-secondary" style="font-size: 0.82rem;">
                <span>Base Fare</span>
                <span class="fw-medium text-secondary">₹ {{ calculatedBaseFare | number:'1.0-0' }}</span>
              </div>
              <div class="d-flex justify-content-between align-items-center mb-1 text-secondary" style="font-size: 0.82rem;">
                <span class="d-flex align-items-center gap-1">
                  Taxes & Fees
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#94A3B8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="cursor-pointer" title="Includes 5% GST and platform processing">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="12" y1="16" x2="12" y2="12"></line>
                    <line x1="12" y1="8" x2="12.01" y2="8"></line>
                  </svg>
                </span>
                <span class="fw-medium text-secondary">₹ {{ calculatedTaxes | number:'1.0-0' }}</span>
              </div>
            </div>

            <!-- Total Amount Row -->
            <div class="total-amount-row d-flex justify-content-between align-items-center pt-2 border-top mb-2">
              <span class="total-label text-navy fw-bold" style="font-size: 1.00rem;">Total Amount</span>
              <span class="total-value text-navy fw-bold" style="font-size: 1.25rem;">₹ {{ calculatedTotalAmount | number:'1.0-0' }}</span>
            </div>

            <!-- Continue to Payment Button -->
            <button class="btn btn-primary w-100 py-2 d-flex align-items-center justify-content-center gap-2 mb-2 btn-continue-payment"
                    [disabled]="!isPassengerFormValid"
                    (click)="onProceedToPaymentStage()">
              <span>Continue to Payment</span>
              <i class="fa-solid fa-arrow-right small"></i>
            </button>

            <!-- Validation message if disabled -->
            <div *ngIf="!isPassengerFormValid" class="text-danger text-center text-micro mb-1">
              <i class="fa-solid fa-circle-exclamation me-1"></i>
              Please fill all passenger names, valid ages (1-120), and contact details.
            </div>

            <!-- Seat-Hold Information Card Immediately Below Button -->
            <div class="hold-time-card p-2 px-3 rounded-2">
              <div class="d-flex align-items-start gap-2">
                <svg class="hold-info-icon flex-shrink-0 mt-1" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#0284C7" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="12" y1="16" x2="12" y2="12"></line>
                  <line x1="12" y1="8" x2="12.01" y2="8"></line>
                </svg>
                <div class="text-secondary" style="font-size: 0.78rem; line-height: 1.35;">
                  Your selected seats are temporarily held for 10 minutes while you complete your booking.
                </div>
              </div>
            </div>

          </div>
        </div>

      </div>

      <!-- ================= STAGE 3: PAYMENT PAGE (APPROVED MOCKUP) ================= -->
      <div *ngIf="currentStage === 3" class="payment-stage-container d-flex flex-column gap-3">
        
        <!-- Booking Confirmation Success Banner (if completed) -->
        <div *ngIf="paymentSuccess" class="payment-success-card bg-white rounded-3 border p-4 text-center">
          <div class="success-icon-wrap mx-auto mb-3">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#10B981" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
              <polyline points="22 4 12 14.01 9 11.01"></polyline>
            </svg>
          </div>
          <h4 class="fw-bold text-navy mb-1">Booking Confirmed Successfully!</h4>
          <p class="text-secondary small mb-3">
            Your payment of <strong>₹ {{ calculatedTotalAmount | number:'1.0-0' }}</strong> has been verified and your seats are reserved.
          </p>
          <div class="confirmation-details-box p-3 rounded-2 bg-light d-inline-block text-start mb-4" style="min-width: 320px;">
            <div class="d-flex justify-content-between mb-2">
              <span class="text-secondary small">Booking Ref:</span>
              <span class="fw-bold text-navy">{{ confirmedBookingRef }}</span>
            </div>
            <div class="d-flex justify-content-between mb-2">
              <span class="text-secondary small">Transaction Ref:</span>
              <span class="fw-bold text-navy">{{ confirmedTransactionRef }}</span>
            </div>
            <div class="d-flex justify-content-between mb-2">
              <span class="text-secondary small">Route:</span>
              <span class="fw-semibold text-navy">{{ displayOrigin }} → {{ displayDestination }}</span>
            </div>
            <div class="d-flex justify-content-between mb-2">
              <span class="text-secondary small">Date:</span>
              <span class="fw-semibold text-navy">{{ departureDateFormatted }}</span>
            </div>
            <div class="d-flex justify-content-between mb-0">
              <span class="text-secondary small">Seats Booked:</span>
              <span class="fw-bold text-primary">{{ selectedSeats.join(', ') }}</span>
            </div>
          </div>
          <div class="d-flex justify-content-center gap-3">
            <button class="btn btn-primary px-4 py-2" (click)="onViewMyBookings()">
              <i class="fa-solid fa-ticket me-2"></i>View in My Bookings
            </button>
            <button class="btn btn-outline-secondary px-4 py-2" (click)="onBookAnotherBus()">
              Book Another Bus
            </button>
          </div>
        </div>

        <!-- Normal Payment View (when not yet confirmed) -->
        <ng-container *ngIf="!paymentSuccess">
          
          <!-- ================= CARD 1: FULL-WIDTH BOOKING SUMMARY ================= -->
          <div class="booking-summary-full-card bg-white rounded-3 border p-3 p-md-4">
            
            <!-- Journey Header -->
            <div class="journey-header mb-3">
              <h5 class="fw-bold text-navy mb-1 card-main-heading">Booking Summary</h5>
              <div class="d-flex align-items-center gap-2 fw-bold text-navy route-title" style="font-size: 1.05rem;">
                <span>{{ displayOrigin }}</span>
                <i class="fa-solid fa-arrow-right text-secondary small"></i>
                <span>{{ displayDestination }}</span>
              </div>
              <div class="text-secondary small mt-1" style="font-size: 0.82rem;">
                {{ departureDateFormatted }}
              </div>
            </div>

            <div class="border-top mb-3"></div>

            <!-- Four Horizontal Sections on Desktop -->
            <div class="summary-four-sections-grid">
              
              <!-- Section A: Selected Seats -->
              <div class="summary-grid-col col-seats pe-lg-3">
                <div class="d-flex justify-content-between align-items-center mb-2">
                  <span class="fw-bold text-navy section-header-title">
                    Selected Seats ({{ selectedSeats.length }})
                  </span>
                  <button class="btn btn-link p-0 text-decoration-none text-primary fw-semibold small d-flex align-items-center gap-1 btn-edit-seats"
                          (click)="onEditSeats()">
                    <i class="fa-solid fa-pencil" style="font-size: 0.70rem;"></i>
                    <span>Edit Seats</span>
                  </button>
                </div>

                <div class="d-flex flex-column gap-2">
                  <div *ngFor="let sn of selectedSeats" class="d-flex align-items-center justify-content-between py-1">
                    <span class="selected-seat-chip">
                      {{ sn }}
                    </span>
                    <span class="fw-bold text-navy seat-fare-val">
                      ₹ {{ getSeatPrice(sn) | number:'1.0-0' }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- Vertical Divider 1 -->
              <div class="summary-col-divider d-none d-lg-block"></div>

              <!-- Section B: Boarding & Dropping Points -->
              <div class="summary-grid-col col-points px-lg-3">
                <div class="fw-bold text-navy section-header-title mb-3">
                  Boarding & Dropping Points
                </div>

                <!-- Boarding Point -->
                <div class="d-flex align-items-start gap-2 mb-3">
                  <span class="timeline-point-dot green-dot mt-1 flex-shrink-0"></span>
                  <div class="d-flex justify-content-between align-items-start flex-grow-1">
                    <div>
                      <div class="text-secondary text-micro mb-0">Boarding Point</div>
                      <div class="fw-semibold text-navy small point-name text-truncate" style="max-width: 170px;" [title]="selectedBoardingPoint">
                        {{ selectedBoardingPoint }}
                      </div>
                    </div>
                    <div class="fw-bold text-navy small ms-2 flex-shrink-0">
                      {{ selectedBoardingTime }}
                    </div>
                  </div>
                </div>

                <!-- Dropping Point -->
                <div class="d-flex align-items-start gap-2">
                  <span class="timeline-point-dot red-dot mt-1 flex-shrink-0"></span>
                  <div class="d-flex justify-content-between align-items-start flex-grow-1">
                    <div>
                      <div class="text-secondary text-micro mb-0">Dropping Point</div>
                      <div class="fw-semibold text-navy small point-name text-truncate" style="max-width: 170px;" [title]="selectedDroppingPoint">
                        {{ selectedDroppingPoint }}
                      </div>
                    </div>
                    <div class="fw-bold text-navy small ms-2 flex-shrink-0">
                      {{ selectedDroppingTime }}
                    </div>
                  </div>
                </div>
              </div>

              <!-- Vertical Divider 2 -->
              <div class="summary-col-divider d-none d-lg-block"></div>

              <!-- Section C: Passengers -->
              <div class="summary-grid-col col-passengers px-lg-3">
                <div class="fw-bold text-navy section-header-title mb-2">
                  Passengers ({{ passengers.length }})
                </div>

                <div class="d-flex flex-column gap-2">
                  <div *ngFor="let p of passengers; let idx = index" class="d-flex align-items-center justify-content-between py-1">
                    <div class="d-flex align-items-center gap-1 text-truncate pe-2">
                      <span class="passenger-item-idx text-navy fw-semibold">{{ idx + 1 }}.</span>
                      <span class="passenger-item-name text-navy fw-semibold text-truncate" [title]="p.name">{{ p.name }}</span>
                      <span class="passenger-item-seat text-secondary small">({{ p.seatNumber }})</span>
                    </div>
                    <div class="passenger-item-meta text-secondary small flex-shrink-0">
                      {{ p.age ? p.age + ', ' : '' }}{{ p.gender }}
                    </div>
                  </div>
                </div>
              </div>

              <!-- Vertical Divider 3 -->
              <div class="summary-col-divider d-none d-lg-block"></div>

              <!-- Section D: Fare Details -->
              <div class="summary-grid-col col-fare ps-lg-3">
                <div class="fw-bold text-navy section-header-title mb-2">
                  Fare Details
                </div>

                <div class="d-flex justify-content-between align-items-center mb-1 text-secondary" style="font-size: 0.85rem;">
                  <span>Base Fare</span>
                  <span class="fw-bold text-navy">₹ {{ calculatedBaseFare | number:'1.0-0' }}</span>
                </div>

                <div class="d-flex justify-content-between align-items-center mb-2 text-secondary" style="font-size: 0.85rem;">
                  <span class="d-flex align-items-center gap-1">
                    Taxes & Fees
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#94A3B8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="cursor-pointer" title="Includes 5% GST and platform processing">
                      <circle cx="12" cy="12" r="10"></circle>
                      <line x1="12" y1="16" x2="12" y2="12"></line>
                      <line x1="12" y1="8" x2="12.01" y2="8"></line>
                    </svg>
                  </span>
                  <span class="fw-bold text-navy">₹ {{ calculatedTaxes | number:'1.0-0' }}</span>
                </div>

                <div class="border-top my-2"></div>

                <div class="d-flex justify-content-between align-items-baseline mt-1">
                  <span class="fw-bold text-navy" style="font-size: 1.05rem;">Total Amount</span>
                  <span class="fw-bold text-navy total-highlight-price">
                    ₹ {{ calculatedTotalAmount | number:'1.0-0' }}
                  </span>
                </div>
              </div>

            </div>

          </div>

          <!-- ================= CARD 2: FULL-WIDTH PAYMENT METHOD ================= -->
          <div class="payment-method-card bg-white rounded-3 border p-3 p-md-4">
            
            <!-- Header -->
            <div class="mb-3">
              <h5 class="fw-bold text-navy mb-1 card-main-heading">Payment Method</h5>
              <div class="card-sub-text">
                You will be redirected to our secure payment gateway to complete the payment.
              </div>
            </div>

            <!-- Gateway Selection Panel with Blue Selected Border -->
            <div class="gateway-option-box p-3 rounded-2 mb-3 d-flex flex-wrap justify-content-between align-items-center gap-3">
              <div class="d-flex align-items-center gap-3">
                <!-- Radio Indicator (Selected) -->
                <div class="custom-radio-selected flex-shrink-0">
                  <div class="custom-radio-inner-dot"></div>
                </div>

                <!-- Credit Card Outline Icon -->
                <svg class="payment-card-icon flex-shrink-0" width="30" height="22" viewBox="0 0 32 24" fill="none" stroke="#0F172A" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="2" y="3" width="28" height="18" rx="3" />
                  <line x1="2" y1="8" x2="30" y2="8" stroke-width="2" />
                  <rect x="6" y="14" width="5" height="3" rx="0.5" fill="#0F172A" stroke="none" />
                </svg>

                <!-- Gateway Description -->
                <div>
                  <div class="fw-bold text-navy" style="font-size: 0.95rem;">
                    Pay via Secure Payment Gateway
                  </div>
                  <div class="text-secondary small">
                    You can pay using UPI, Debit/Credit Cards, Net Banking and other <span class="fw-semibold text-navy">available options</span>.
                  </div>
                </div>
              </div>

              <!-- Supported Provider Badges (UPI, Visa, Mastercard, RuPay, NetBanking) -->
              <div class="gateway-method-badges d-flex align-items-center gap-2 flex-wrap">
                <!-- UPI Badge -->
                <span class="badge-brand badge-upi" title="UPI">
                  <span class="upi-text">UPI</span>
                </span>

                <!-- VISA Badge -->
                <span class="badge-brand badge-visa" title="Visa">
                  <span class="visa-text">VISA</span>
                </span>

                <!-- Mastercard Badge -->
                <span class="badge-brand badge-mc" title="Mastercard">
                  <span class="mc-circle mc-red"></span>
                  <span class="mc-circle mc-orange"></span>
                </span>

                <!-- RuPay Badge -->
                <span class="badge-brand badge-rupay" title="RuPay">
                  <span class="rupay-text">RuPay<span class="rupay-arrow">▶</span></span>
                </span>

                <!-- Net Banking Icon -->
                <span class="badge-brand badge-netbanking" title="Net Banking">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#0F172A" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M3 21h18M3 10h18M5 10v11M9 10v11M15 10v11M19 10v11M12 2L2 7h20L12 2z" />
                  </svg>
                </span>
              </div>
            </div>

            <!-- Gateway Redirection Notice Strip -->
            <div class="redirect-notice-strip p-3 rounded-2 mb-4 d-flex align-items-start gap-2">
              <svg class="redirect-info-icon flex-shrink-0 mt-1" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#0284C7" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="16" x2="12" y2="12"></line>
                <line x1="12" y1="8" x2="12.01" y2="8"></line>
              </svg>
              <div>
                <div class="fw-bold text-navy" style="font-size: 0.90rem;">
                  You will be redirected
                </div>
                <div class="redirect-desc-text mt-1">
                  After you click on "Pay Now", you will be securely redirected to our partnered payment gateway. Once the payment is successful, you will be automatically redirected back to Travel Easy.
                </div>
              </div>
            </div>

            <!-- Important Information Section -->
            <div class="important-info-section mb-4">
              <div class="fw-bold text-navy mb-3" style="font-size: 0.95rem;">
                Important Information
              </div>

              <div class="important-info-grid">
                <!-- Item 1: Secure Payment -->
                <div class="important-info-item pe-lg-3">
                  <div class="d-flex align-items-start gap-2">
                    <svg class="info-item-icon flex-shrink-0 mt-1" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#334155" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                    </svg>
                    <div>
                      <div class="fw-bold text-navy" style="font-size: 0.88rem;">Secure Payment</div>
                      <div class="info-item-text text-secondary mt-1">
                        Your payment information is processed securely by our trusted payment partner.
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Divider 1 -->
                <div class="important-col-divider d-none d-lg-block"></div>

                <!-- Item 2: No extra charges -->
                <div class="important-info-item px-lg-3">
                  <div class="d-flex align-items-start gap-2">
                    <svg class="info-item-icon flex-shrink-0 mt-1" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#334155" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                      <polyline points="9 12 11 14 15 10"></polyline>
                    </svg>
                    <div>
                      <div class="fw-bold text-navy" style="font-size: 0.88rem;">No extra charges</div>
                      <div class="info-item-text text-secondary mt-1">
                        You will only be charged the total amount shown. We do not store your card or UPI details.
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Divider 2 -->
                <div class="important-col-divider d-none d-lg-block"></div>

                <!-- Item 3: Booking confirmation -->
                <div class="important-info-item ps-lg-3">
                  <div class="d-flex align-items-start gap-2">
                    <svg class="info-item-icon flex-shrink-0 mt-1" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#334155" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                      <polyline points="14 2 14 8 20 8"></polyline>
                      <line x1="16" y1="13" x2="8" y2="13"></line>
                      <line x1="16" y1="17" x2="8" y2="17"></line>
                      <polyline points="10 9 9 9 8 9"></polyline>
                    </svg>
                    <div>
                      <div class="fw-bold text-navy" style="font-size: 0.88rem;">Booking confirmation</div>
                      <div class="info-item-text text-secondary mt-1">
                        Your ticket will be confirmed once the payment is successful. You will receive the confirmation on your registered mobile number and email address.
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Error Alert if Payment Failed -->
            <div *ngIf="paymentError" class="alert alert-danger d-flex align-items-center gap-2 py-2 px-3 rounded-2 mb-3">
              <i class="fa-solid fa-triangle-exclamation"></i>
              <div class="small flex-grow-1">{{ paymentError }}</div>
              <button class="btn btn-sm btn-outline-danger" (click)="paymentError = null">Dismiss</button>
            </div>

            <!-- Pay Now Button -->
            <button class="btn btn-primary w-100 py-3 d-flex align-items-center justify-content-center gap-2 btn-pay-now"
                    [disabled]="isProcessingPayment"
                    (click)="onPayNow()">
              <span *ngIf="!isProcessingPayment" class="d-flex align-items-center justify-content-center gap-2 w-100">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
                <span class="pay-btn-label">Pay Now</span>
                <span class="pay-btn-amount">₹ {{ calculatedTotalAmount | number:'1.0-0' }}</span>
                <i class="fa-solid fa-arrow-right ms-1"></i>
              </span>
              <span *ngIf="isProcessingPayment" class="d-flex align-items-center gap-2">
                <span class="spinner-border spinner-border-sm" role="status"></span>
                <span>Processing Payment via Secure Gateway...</span>
              </span>
            </button>

            <!-- Terms and Privacy Notice -->
            <div class="terms-privacy-notice text-center mt-3">
              <span class="text-secondary small">
                By proceeding, you agree to our 
                <a href="javascript:void(0)" (click)="openTerms()" class="text-primary text-decoration-underline">Terms & Conditions</a> 
                and 
                <a href="javascript:void(0)" (click)="openPrivacy()" class="text-primary text-decoration-underline">Privacy Policy</a>.
              </span>
            </div>

          </div>

        </ng-container>

      </div>

      <!-- Terms & Conditions Modal -->
      <div *ngIf="showTermsModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
          <div class="modal-content rounded-3 border-0 shadow">
            <div class="modal-header border-bottom py-3">
              <h5 class="modal-title fw-bold text-navy">Terms & Conditions</h5>
              <button type="button" class="btn-close" (click)="showTermsModal = false"></button>
            </div>
            <div class="modal-body p-4 text-secondary small">
              <h6 class="fw-bold text-navy">1. Booking & Ticket Policy</h6>
              <p>Tickets booked on Travel Easy are subject to bus operator terms and seat availability. Please carry a valid government-issued photo ID during your journey.</p>
              <h6 class="fw-bold text-navy">2. Cancellation & Refunds</h6>
              <p>Cancellations made more than 24 hours prior to departure are eligible for refunds minus applicable platform convenience fees. Refund amounts are credited within 3-5 business days to the original payment source.</p>
              <h6 class="fw-bold text-navy">3. Passenger Safety & Conduct</h6>
              <p>Passengers must arrive at boarding points at least 15 minutes before the scheduled departure time.</p>
            </div>
            <div class="modal-footer border-top py-2">
              <button type="button" class="btn btn-primary btn-sm px-3" (click)="showTermsModal = false">I Understand</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Privacy Policy Modal -->
      <div *ngIf="showPrivacyModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
          <div class="modal-content rounded-3 border-0 shadow">
            <div class="modal-header border-bottom py-3">
              <h5 class="modal-title fw-bold text-navy">Privacy Policy</h5>
              <button type="button" class="btn-close" (click)="showPrivacyModal = false"></button>
            </div>
            <div class="modal-body p-4 text-secondary small">
              <h6 class="fw-bold text-navy">1. Data Protection</h6>
              <p>Travel Easy values your privacy. We collect personal details strictly for reservation dispatch, verification, and statutory passenger manifests.</p>
              <h6 class="fw-bold text-navy">2. Payment Security</h6>
              <p>All online payment transactions are processed over 256-bit SSL encrypted channels by authorized and PCI-DSS compliant payment gateways. Travel Easy never stores card numbers or UPI PINs on its servers.</p>
            </div>
            <div class="modal-footer border-top py-2">
              <button type="button" class="btn btn-primary btn-sm px-3" (click)="showPrivacyModal = false">Close</button>
            </div>
          </div>
        </div>
      </div>

    </div>

    <!-- ================= SEAT CELL TEMPLATE ================= -->
    <ng-template #seatTemplate let-pos="pos">
      <div *ngIf="pos" 
           class="seat-cell-item d-flex flex-column align-items-center justify-content-center position-relative"
           [class.seater]="pos.seatType === 'SEATER'"
           [class.sleeper]="pos.seatType === 'SLEEPER'"
           [class.available]="pos.status === 'AVAILABLE'"
           [class.selected]="isSeatSelected(pos.seatNumber)"
           [class.booked]="pos.status === 'BOOKED'"
           [class.male-seat]="isMale(pos)"
           [class.female-seat]="isFemale(pos)"
           [class.unisex-seat]="!isMale(pos) && !isFemale(pos)"
           [class.cursor-pointer]="pos.status === 'AVAILABLE'"
           [class.cursor-not-allowed]="pos.status === 'BOOKED'"
           [title]="'Seat ' + pos.seatNumber + ' - ₹' + pos.price + ' (' + (pos.status === 'BOOKED' ? 'Booked' : (isSeatSelected(pos.seatNumber) ? 'Selected' : 'Available')) + ')'"
           (click)="onSeatCellClick(pos)">
        
        <!-- Exact Seater Icon (Armchair from Screenshot 1) -->
        <svg *ngIf="pos.seatType === 'SEATER'" class="seat-svg seater-svg" viewBox="0 0 32 32" width="22" height="22" fill="none">
          <!-- Backrest / Inner Cushion -->
          <path class="seat-cushion" d="M6 3 C6 1.5, 26 1.5, 26 3 L26 23 C26 24.5, 6 24.5, 6 23 Z"/>
          <!-- Left Armrest -->
          <rect class="seat-armrest" x="1.5" y="7" width="4.5" height="17" rx="2.25"/>
          <!-- Right Armrest -->
          <rect class="seat-armrest" x="26" y="7" width="4.5" height="17" rx="2.25"/>
          <!-- Bottom Base Bar -->
          <line class="seat-base" x1="1.5" y1="25" x2="30.5" y2="25" stroke-linecap="round"/>
          
          <!-- Male Avatar inside seat -->
          <g *ngIf="isMale(pos)" class="seat-avatar avatar-male">
            <circle cx="16" cy="10.5" r="3.2"/>
            <path d="M11 19.5 C11 16, 13 15, 16 15 C19 15, 21 16, 21 19.5" stroke-linecap="round"/>
          </g>
          <!-- Female Avatar inside seat -->
          <g *ngIf="isFemale(pos)" class="seat-avatar avatar-female">
            <circle cx="16" cy="10.5" r="3.2"/>
            <path d="M12.5 10 C12.5 8, 19.5 8, 19.5 10"/>
            <path d="M11 19.5 C11 16.5, 13.5 15.5, 16 15.5 C18.5 15.5, 21 16.5, 21 19.5" stroke-linecap="round"/>
          </g>
          <!-- Booked generic avatar inside seat -->
          <g *ngIf="pos.status === 'BOOKED' && !isMale(pos) && !isFemale(pos)" class="seat-avatar avatar-booked">
            <circle cx="16" cy="10.5" r="3.2"/>
            <path d="M11 19.5 C11 16.5, 13.5 15.5, 16 15.5 C18.5 15.5, 21 16.5, 21 19.5" stroke-linecap="round"/>
          </g>
        </svg>

        <!-- Exact Sleeper Berth Icon (Capsule with Pillow from Screenshot 1) -->
        <svg *ngIf="pos.seatType === 'SLEEPER'" class="seat-svg sleeper-svg" viewBox="0 0 32 76" width="22" height="46" fill="none">
          <!-- Outer Berth Frame -->
          <rect class="sleeper-frame" x="1.5" y="1.5" width="29" height="73" rx="6" stroke-width="1.5"/>
          <!-- Inner Pillow near bottom -->
          <rect class="sleeper-pillow" x="8" y="60" width="16" height="5" rx="2" stroke-width="1.2"/>

          <!-- Male Avatar inside near top -->
          <g *ngIf="isMale(pos)" class="seat-avatar avatar-male">
            <circle cx="16" cy="18" r="3.5"/>
            <path d="M10 28 C10 24, 12.5 23, 16 23 C19.5 23, 22 24, 22 28" stroke-linecap="round"/>
          </g>
          <!-- Female Avatar inside near top -->
          <g *ngIf="isFemale(pos)" class="seat-avatar avatar-female">
            <circle cx="16" cy="17.5" r="3.5"/>
            <path d="M12 17 C12 14.5, 20 14.5, 20 17"/>
            <path d="M10 28 C10 24.5, 12.5 23.5, 16 23.5 C19.5 23.5, 22 24.5, 22 28" stroke-linecap="round"/>
          </g>
          <!-- Booked generic avatar inside near top -->
          <g *ngIf="pos.status === 'BOOKED' && !isMale(pos) && !isFemale(pos)" class="seat-avatar avatar-booked">
            <circle cx="16" cy="18" r="3.5"/>
            <path d="M10 28 C10 24.5, 12.5 23.5, 16 23.5 C19.5 23.5, 22 24.5, 22 28" stroke-linecap="round"/>
          </g>
        </svg>

        <!-- Individual Price Tag directly below icon -->
        <span class="seat-price-tag" [class.booked-price]="pos.status === 'BOOKED'">
          ₹{{ pos.price | number:'1.0-0' }}
        </span>

      </div>

      <!-- Empty Space placeholder for missing cell -->
      <div *ngIf="!pos" class="seat-empty-spacer"></div>
    </ng-template>
  `,
  styles: [`
    :host {
      --primary-blue: #0875E1;
      --dark-navy: #102A56;
      --light-blue-bg: #F5F9FE;
      --card-border: #E2E8F0;

      /* Shared Layout Tokens for Bus Deck Renderer */
      --seat-w: 26px;
      --row-unit-h: 31px;
      --aisle-w: 20px;
      --col-gap: 5px;
      --row-gap: 2px;
      --bus-padding: 6px 12px 10px 12px;
      --decks-gap: 20px;

      display: block;
    }

    .text-navy { color: var(--dark-navy) !important; }
    .smaller { font-size: 0.75rem; }
    .text-micro { font-size: 0.70rem !important; }
    .text-micro-heading { font-size: 0.78rem !important; }
    .cursor-pointer { cursor: pointer; }
    .cursor-not-allowed { cursor: not-allowed; }
    .hover-danger:hover { color: #DC2626 !important; }
    .shadow-xs { box-shadow: 0 1px 3px rgba(0,0,0,0.04) !important; }

    /* Header Nav */
    .btn-back-results {
      color: #0875E1;
      font-weight: 600;
      font-size: 0.78rem;
      transition: all 0.2s ease;
    }
    .btn-back-results:hover {
      color: #065cb3;
      transform: translateX(-2px);
    }

    .page-title {
      font-size: 1.15rem;
      letter-spacing: -0.2px;
    }

    /* Stepper */
    .stepper-progress-wrap {
      background: #FFFFFF;
      padding: 4px 12px;
      border-radius: 24px;
      border: 1px solid var(--card-border);
      box-shadow: 0 1px 3px rgba(0,0,0,0.03);
    }
    .step-item {
      color: #94A3B8;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .step-item.active {
      color: var(--primary-blue);
      font-weight: 700;
    }
    .step-item.completed {
      color: var(--primary-blue);
      font-weight: 700;
    }
    .step-circle {
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: #F1F5F9;
      color: #64748B;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 0.68rem;
      font-weight: 700;
    }
    .step-item.active .step-circle {
      background: var(--primary-blue);
      color: #FFFFFF;
    }
    .step-item.completed .step-circle {
      background: var(--primary-blue);
      color: #FFFFFF;
    }
    .step-connector {
      width: 20px;
      height: 2px;
      background: #E2E8F0;
      margin: 0 6px;
    }
    .step-connector.completed {
      background: var(--primary-blue);
    }

    /* ================= SELECTED BUS SUMMARY CARD ================= */
    .selected-bus-summary-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      padding: 12px 20px;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    }
    .summary-card-inner {
      display: flex;
      align-items: center;
      width: 100%;
    }
    
    /* Section 1: Bus Image (17-19%) */
    .summary-section-image {
      flex: 0 0 18%;
      max-width: 18%;
      display: flex;
      align-items: center;
      justify-content: center;
      padding-right: 14px;
    }
    .summary-bus-img {
      max-width: 100%;
      height: auto;
      max-height: 72px;
      object-fit: contain;
      display: block;
    }

    /* Section 2: Operator & Bus Info (26-29%) */
    .summary-section-operator {
      flex: 0 0 27%;
      max-width: 27%;
      min-width: 0;
      padding-right: 8px;
    }
    .operator-heading {
      font-size: 1.18rem;
      font-weight: 700;
      color: #0F172A;
      line-height: 1.2;
      display: inline-block;
    }
    .badge-ac-status {
      background-color: #10B981;
      color: #FFFFFF;
      font-size: 0.68rem;
      font-weight: 700;
      padding: 2px 7px;
      border-radius: 4px;
      display: inline-flex;
      align-items: center;
      vertical-align: middle;
      line-height: 1.2;
      flex-shrink: 0;
    }
    .badge-ac-status.badge-non-ac {
      background-color: #64748B;
    }
    .bus-info-line {
      font-size: 0.88rem;
      color: #64748B;
      font-weight: 400;
      line-height: 1.35;
      margin-top: 1px;
    }

    /* Vertical Dividers */
    .summary-vertical-divider {
      width: 1px;
      height: 56px;
      background-color: #E2E8F0;
      flex-shrink: 0;
      margin: 0 16px;
      align-self: center;
    }

    /* Section 3: Journey Details (40-42%) */
    .summary-section-journey {
      flex: 1 1 auto;
      display: flex;
      align-items: center;
      justify-content: space-between;
      min-width: 0;
      padding: 0 4px;
    }
    .journey-endpoint {
      min-width: 90px;
      flex-shrink: 0;
    }
    .endpoint-location-row {
      line-height: 1.2;
    }
    .endpoint-pin-icon {
      flex-shrink: 0;
    }
    .endpoint-city-name {
      font-size: 0.90rem;
      font-weight: 600;
      color: #0F172A;
    }
    .endpoint-time-val {
      font-size: 1.25rem;
      font-weight: 700;
      color: #0F172A;
      line-height: 1.15;
      margin: 2px 0;
    }
    .endpoint-date-val {
      font-size: 0.82rem;
      color: #64748B;
      font-weight: 400;
      line-height: 1.2;
    }

    /* Connector Graphic */
    .journey-connector-track {
      min-width: 80px;
    }
    .connector-end-dot {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background-color: #94A3B8;
      flex-shrink: 0;
    }
    .connector-dotted-segment {
      flex: 1;
      height: 0;
      border-top: 2px dotted #CBD5E1;
      margin: 0 6px;
    }
    .connector-bus-icon-wrap {
      padding: 0 4px;
      color: #0284C7;
      font-size: 1.05rem;
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    /* Section 4: Journey Duration (13-15%) */
    .summary-section-duration {
      flex: 0 0 14%;
      max-width: 14%;
      min-width: 0;
      padding-left: 4px;
    }
    .duration-clock-svg {
      flex-shrink: 0;
    }
    .duration-text-group {
      min-width: 0;
    }
    .duration-meta-label {
      font-size: 0.80rem;
      color: #64748B;
      font-weight: 500;
      line-height: 1.1;
    }
    .duration-time-val {
      font-size: 1.15rem;
      font-weight: 700;
      color: #0F172A;
      line-height: 1.2;
      margin-top: 2px;
      white-space: nowrap;
    }

    /* Responsive Adaptation */
    @media (max-width: 991px) {
      .summary-card-inner {
        flex-wrap: wrap;
        gap: 12px;
      }
      .summary-section-image {
        flex: 0 0 20%;
        max-width: 20%;
      }
      .summary-section-operator {
        flex: 1 1 70%;
        max-width: 70%;
      }
      .summary-vertical-divider {
        display: none;
      }
      .summary-section-journey {
        flex: 1 1 65%;
      }
      .summary-section-duration {
        flex: 0 0 30%;
        max-width: 30%;
        justify-content: flex-end;
      }
    }
    @media (max-width: 576px) {
      .summary-card-inner {
        flex-direction: column;
        align-items: flex-start;
      }
      .summary-section-image {
        flex: 0 0 100%;
        max-width: 100%;
        justify-content: flex-start;
      }
      .summary-section-operator {
        flex: 0 0 100%;
        max-width: 100%;
      }
      .summary-section-journey {
        width: 100%;
      }
      .summary-section-duration {
        flex: 0 0 100%;
        max-width: 100%;
        justify-content: flex-start;
      }
    }

    /* Bus Outline & Deck Container */
    .decks-container {
      display: flex;
      flex-direction: row;
      justify-content: center;
      align-items: stretch;
      gap: var(--decks-gap);
      width: 100%;
    }
    .deck-column {
      display: flex;
      flex-direction: column;
      align-items: center;
      flex: 0 0 auto;
    }
    .deck-title-header {
      color: #334155;
      font-size: 0.82rem;
      font-weight: 600;
      text-transform: none;
    }
    .bus-body-outline {
      background: #FFFFFF;
      border: 1px solid #DCDCDC;
      border-radius: 14px;
      display: flex;
      flex-direction: column;
      justify-content: flex-start;
      align-items: center;
      padding: var(--bus-padding);
      box-shadow: none;
      width: fit-content;
      margin: 0 auto;
    }
    .deck-top-cab {
      height: 16px;
      width: 100%;
      display: flex;
      justify-content: flex-end;
      align-items: center;
      padding-right: 4px;
    }
    .steering-wheel-svg {
      stroke: #9E9E9E;
    }

    /* Unified Bus Deck CSS Grid */
    .bus-deck-grid {
      display: grid;
      column-gap: var(--col-gap);
      row-gap: var(--row-gap);
      justify-content: center;
      align-content: start;
    }
    .grid-seat-slot {
      width: var(--seat-w);
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    /* Seat / Berth Cells */
    .seat-cell-item {
      font-family: inherit;
      user-select: none;
      transition: transform 0.15s ease-in-out;
      background: transparent;
      border: none;
      box-shadow: none;
      padding: 0;
      width: 100%;
      height: 100%;
    }
    .seat-cell-item.seater {
      height: 100%;
    }
    .seat-cell-item.sleeper {
      height: 100%;
    }
    .seat-svg {
      display: block;
      transition: all 0.2s ease;
    }
    .seat-price-tag {
      font-size: 0.60rem;
      font-weight: 700;
      color: #222222;
      margin-top: 1px;
      line-height: 1;
      text-align: center;
      white-space: nowrap;
    }
    .seat-price-tag.booked-price {
      color: #8C8C8C !important;
      font-weight: 500;
    }

    /* Available Unisex */
    .seat-cell-item.available.unisex-seat .seat-cushion,
    .seat-cell-item.available.unisex-seat .seat-armrest,
    .seat-cell-item.available.unisex-seat .sleeper-frame {
      fill: #FFFFFF;
      stroke: #555555;
      stroke-width: 1.5;
    }
    .seat-cell-item.available.unisex-seat .seat-base,
    .seat-cell-item.available.unisex-seat .sleeper-pillow {
      stroke: #555555;
      stroke-width: 1.2;
      fill: none;
    }

    /* Available Male */
    .seat-cell-item.available.male-seat .seat-cushion,
    .seat-cell-item.available.male-seat .seat-armrest,
    .seat-cell-item.available.male-seat .sleeper-frame {
      fill: #FFFFFF;
      stroke: #3B82F6;
      stroke-width: 1.5;
    }
    .seat-cell-item.available.male-seat .seat-base,
    .seat-cell-item.available.male-seat .sleeper-pillow,
    .seat-cell-item.available.male-seat .avatar-male {
      stroke: #3B82F6;
      stroke-width: 1.5;
      fill: none;
    }

    /* Available Female */
    .seat-cell-item.available.female-seat .seat-cushion,
    .seat-cell-item.available.female-seat .seat-armrest,
    .seat-cell-item.available.female-seat .sleeper-frame {
      fill: #FFFFFF;
      stroke: #B854D4;
      stroke-width: 1.5;
    }
    .seat-cell-item.available.female-seat .seat-base,
    .seat-cell-item.available.female-seat .sleeper-pillow,
    .seat-cell-item.available.female-seat .avatar-female {
      stroke: #B854D4;
      stroke-width: 1.5;
      fill: none;
    }

    /* Hover on Available */
    .seat-cell-item.available:hover {
      transform: translateY(-2px);
    }
    .seat-cell-item.available:hover .seat-svg {
      filter: drop-shadow(0 2px 4px rgba(0,0,0,0.12));
    }

    /* Selected State */
    .seat-cell-item.selected .seat-cushion,
    .seat-cell-item.selected .seat-armrest,
    .seat-cell-item.selected .sleeper-frame {
      fill: #DBEAFE !important;
      stroke: #2563EB !important;
      stroke-width: 1.8;
    }
    .seat-cell-item.selected .seat-base,
    .seat-cell-item.selected .sleeper-pillow {
      stroke: #2563EB !important;
      fill: #BFDBFE !important;
      stroke-width: 1.5;
    }
    .seat-cell-item.selected .avatar-male,
    .seat-cell-item.selected .avatar-female,
    .seat-cell-item.selected .avatar-booked {
      stroke: #2563EB !important;
      stroke-width: 1.5;
      fill: none;
    }
    .seat-cell-item.selected.female-seat .seat-cushion,
    .seat-cell-item.selected.female-seat .seat-armrest,
    .seat-cell-item.selected.female-seat .sleeper-frame {
      fill: #FAE8FF !important;
      stroke: #B854D4 !important;
    }
    .seat-cell-item.selected.female-seat .seat-base,
    .seat-cell-item.selected.female-seat .sleeper-pillow,
    .seat-cell-item.selected.female-seat .avatar-female {
      stroke: #B854D4 !important;
    }
    .seat-cell-item.selected .seat-price-tag {
      color: #2563EB !important;
      font-weight: 800;
    }

    /* Booked State */
    .seat-cell-item.booked {
      cursor: not-allowed !important;
    }
    .seat-cell-item.booked .seat-cushion,
    .seat-cell-item.booked .seat-armrest,
    .seat-cell-item.booked .sleeper-frame {
      fill: #F2F2F2 !important;
      stroke: #D8D8D8 !important;
      stroke-width: 1.5;
    }
    .seat-cell-item.booked .seat-base,
    .seat-cell-item.booked .sleeper-pillow {
      stroke: #D0D0D0 !important;
      fill: #E5E5E5 !important;
      stroke-width: 1.2;
    }
    .seat-cell-item.booked .avatar-male,
    .seat-cell-item.booked .avatar-female,
    .seat-cell-item.booked .avatar-booked {
      stroke: #B0B0B0 !important;
      stroke-width: 1.5;
      fill: none;
    }

    /* ================= PASSENGER DETAILS STAGE 2 STYLING ================= */
    .passenger-details-stage-grid {
      display: grid;
      grid-template-columns: 69% 29%;
      gap: 20px;
      align-items: start;
      width: 100%;
    }

    .col-passenger-forms {
      min-width: 0;
    }

    .passenger-info-card,
    .contact-info-card,
    .booking-summary-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    }

    .passenger-seat-item-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 8px;
    }

    .passenger-card-title {
      font-size: 0.88rem;
    }

    .badge-lead-passenger {
      background-color: #E0F2FE;
      color: #0284C7;
      font-size: 0.72rem;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 6px;
    }

    .btn-remove-passenger {
      color: #EF4444;
      font-size: 0.80rem;
      font-weight: 600;
      cursor: pointer;
      transition: color 0.15s ease;
    }
    .btn-remove-passenger:hover {
      color: #DC2626;
    }

    .field-label {
      font-size: 0.76rem;
      font-weight: 600;
      color: #475569;
    }

    .custom-passenger-input {
      background-color: #FFFFFF;
      border: 1px solid #CBD5E1;
      border-radius: 6px;
      font-size: 0.82rem;
      color: #0F172A;
      padding: 4px 8px;
      height: 33px;
      transition: all 0.2s ease;
    }
    .custom-passenger-input:focus {
      border-color: #0875E1;
      box-shadow: 0 0 0 2px rgba(8, 117, 225, 0.15);
      outline: none;
    }

    .field-full-name {
      flex: 1 1 34%;
      min-width: 160px;
    }
    .field-age {
      flex: 0 0 13%;
      min-width: 70px;
      max-width: 90px;
    }
    .field-gender {
      flex: 0 0 20%;
      min-width: 105px;
    }
    .field-id-proof {
      flex: 0 0 24%;
      min-width: 125px;
    }

    /* Contact Strip */
    .contact-notice-strip {
      background-color: #F0F9FF;
      border: 1px solid #BAE6FD;
    }
    .notice-text {
      color: #0369A1;
      font-size: 0.80rem;
      line-height: 1.35;
    }

    /* Right Column: Booking Summary */
    .col-booking-summary {
      min-width: 0;
    }
    .route-heading {
      font-size: 0.98rem;
    }
    .btn-edit-seats {
      font-size: 0.82rem;
      transition: color 0.15s ease;
    }
    .btn-edit-seats:hover {
      color: #0662BE;
    }

    .selected-seat-badge-clean {
      background-color: #EFF6FF;
      border: 1px solid #BFDBFE;
      color: #1D4ED8;
      font-size: 0.85rem;
      font-weight: 700;
      padding: 3px 10px;
      border-radius: 6px;
      display: inline-block;
    }

    .selected-seat-summary-row {
      border-bottom: 1px solid #F1F5F9;
    }
    .selected-seat-summary-row:last-child {
      border-bottom: none;
    }

    .timeline-connector-segment {
      width: 0;
      min-height: 18px;
      border-left: 2px dotted #CBD5E1;
      margin: 2px 0 2px 4px;
    }

    .btn-continue-payment {
      background-color: #0875E1;
      border: none;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.95rem;
      color: #FFFFFF;
      padding: 10px 16px;
      transition: all 0.2s ease;
    }
    .btn-continue-payment:hover:not(:disabled) {
      background-color: #0662BE;
      box-shadow: 0 4px 12px rgba(8, 117, 225, 0.25);
    }
    .btn-continue-payment:disabled {
      background-color: #94A3B8;
      opacity: 0.7;
      cursor: not-allowed;
    }

    /* Stage 2 Responsive */
    @media (max-width: 1199px) {
      .passenger-details-stage-grid {
        grid-template-columns: 1fr;
        gap: 16px;
      }
    }

    /* ================= PAYMENT STAGE 3 STYLING (APPROVED MOCKUP) ================= */
    .payment-stage-container {
      width: 100%;
    }

    .booking-summary-full-card,
    .payment-method-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    }

    .summary-four-sections-grid {
      display: grid;
      grid-template-columns: 22% 1px 26% 1px 26% 1px 26%;
      gap: 14px;
      align-items: start;
    }

    .summary-col-divider {
      width: 1px;
      background-color: #E2E8F0;
      align-self: stretch;
      min-height: 90px;
    }

    .section-header-title {
      font-size: 0.95rem;
    }

    .selected-seat-chip {
      background-color: #EFF6FF;
      border: 1px solid #BFDBFE;
      color: #1D4ED8;
      font-size: 0.82rem;
      font-weight: 700;
      padding: 3px 10px;
      border-radius: 6px;
      display: inline-block;
    }

    .seat-fare-val {
      font-size: 0.90rem;
    }

    .passenger-item-idx {
      font-size: 0.88rem;
    }
    .passenger-item-name {
      font-size: 0.88rem;
      max-width: 135px;
    }
    .passenger-item-seat {
      font-size: 0.80rem;
    }
    .passenger-item-meta {
      font-size: 0.82rem;
    }

    .total-highlight-price {
      font-size: 1.60rem;
      font-weight: 800;
      color: #0F172A;
    }

    /* Gateway Option Box */
    .gateway-option-box {
      border: 1.5px solid #0875E1;
      background-color: #FFFFFF;
      border-radius: 8px;
      transition: all 0.2s ease;
    }

    .custom-radio-selected {
      width: 20px;
      height: 20px;
      border-radius: 50%;
      border: 2px solid #0875E1;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .custom-radio-inner-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background-color: #0875E1;
    }

    /* Brand Badges */
    .badge-brand {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 2px 7px;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 700;
      height: 25px;
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
    }
    .badge-upi {
      color: #334155;
      font-style: italic;
    }
    .upi-text {
      font-weight: 800;
      letter-spacing: 0.5px;
    }
    .badge-visa {
      color: #1A1F71;
      font-weight: 900;
      letter-spacing: 0.5px;
    }
    .badge-mc {
      position: relative;
      width: 42px;
      padding: 0;
    }
    .mc-circle {
      width: 15px;
      height: 15px;
      border-radius: 50%;
      display: inline-block;
    }
    .mc-red {
      background: #EB001B;
      margin-right: -5px;
      z-index: 1;
    }
    .mc-orange {
      background: #F79E1B;
    }
    .badge-rupay {
      color: #003366;
      font-weight: 800;
    }
    .rupay-arrow {
      color: #009944;
      font-size: 0.60rem;
      margin-left: 2px;
    }
    .badge-netbanking {
      padding: 2px 6px;
    }

    /* Redirection notice */
    .redirect-notice-strip {
      background-color: #F0F9FF;
      border: 1px solid #BAE6FD;
    }
    .redirect-desc-text {
      color: #0369A1;
      font-size: 0.82rem;
      line-height: 1.45;
    }

    /* Important Information */
    .important-info-grid {
      display: grid;
      grid-template-columns: 1fr 1px 1fr 1px 1fr;
      gap: 16px;
      align-items: start;
    }
    .important-col-divider {
      width: 1px;
      background-color: #E2E8F0;
      align-self: stretch;
      min-height: 48px;
    }
    .info-item-text {
      font-size: 0.80rem;
      line-height: 1.4;
    }

    /* Pay Now Button */
    .btn-pay-now {
      background-color: #0875E1;
      border: none;
      border-radius: 8px;
      font-weight: 600;
      font-size: 1rem;
      color: #FFFFFF;
      height: 48px;
      transition: all 0.2s ease;
    }
    .btn-pay-now:hover:not(:disabled) {
      background-color: #0662BE;
      box-shadow: 0 4px 12px rgba(8, 117, 225, 0.25);
    }
    .btn-pay-now:disabled {
      background-color: #94A3B8;
      opacity: 0.75;
      cursor: not-allowed;
    }
    .pay-btn-label {
      font-weight: 700;
    }
    .pay-btn-amount {
      font-weight: 700;
      margin-left: 4px;
    }

    /* Success Card */
    .payment-success-card {
      border: 1px solid #A7F3D0 !important;
      background: #FFFFFF !important;
    }
    .success-icon-wrap {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: #ECFDF5;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    /* Stage 3 Responsive */
    @media (max-width: 991px) {
      .summary-four-sections-grid {
        grid-template-columns: 1fr;
        gap: 20px;
      }
      .summary-col-divider {
        display: none !important;
      }
      .important-info-grid {
        grid-template-columns: 1fr;
        gap: 16px;
      }
      .important-col-divider {
        display: none !important;
      }
    }

    /* ================= STAGE 1: 3-COLUMN SEAT SELECTION CSS ================= */
    .seat-selection-three-columns-grid {
      display: grid;
      grid-template-columns: minmax(0, 1.45fr) minmax(0, 1fr) minmax(0, 1fr);
      gap: 16px;
      align-items: start;
      width: 100%;
    }
    @media (max-width: 1199.98px) {
      .seat-selection-three-columns-grid {
        grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr) minmax(0, 1fr);
        gap: 12px;
      }
    }
    @media (max-width: 991.98px) {
      .seat-selection-three-columns-grid {
        grid-template-columns: 1fr;
        gap: 16px;
      }
    }

    .seat-layout-card,
    .seat-legend-card,
    .points-selector-card,
    .your-selection-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    }

    /* Legend Matrix Table (Screenshot 2) */
    .legend-matrix-table {
      width: 100%;
    }
    .legend-col-label {
      width: 76px;
      font-size: 0.74rem;
      font-weight: 700;
      color: #333333;
    }
    .legend-col-head {
      flex: 1;
      text-align: center;
      font-size: 0.68rem;
      font-weight: 700;
      color: #444444;
    }
    .legend-col-cell {
      flex: 1;
      display: flex;
      justify-content: center;
      align-items: center;
    }
    .legend-icon-box {
      width: 26px;
      height: 26px;
      border-radius: 5px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s ease;
    }
    .legend-icon-box.avail-unisex {
      border: 1.5px solid #666666;
      background: #FFFFFF;
    }
    .legend-icon-box.avail-male {
      border: 1.5px solid #3B82F6;
      background: #FFFFFF;
    }
    .legend-icon-box.avail-female {
      border: 1.5px solid #B854D4;
      background: #FFFFFF;
    }
    .legend-icon-box.sel-unisex {
      border: 1.5px solid #2563EB;
      background: #DBEAFE;
    }
    .legend-icon-box.sel-male {
      border: 1.5px solid #2563EB;
      background: #DBEAFE;
    }
    .legend-icon-box.sel-female {
      border: 1.5px solid #B854D4;
      background: #FAE8FF;
    }
    .legend-icon-box.book-unisex {
      border: 1.5px solid #E0E0E0;
      background: #F0F0F0;
    }
    .legend-icon-box.book-male {
      border: 1.5px solid #E0E0E0;
      background: #F0F0F0;
    }
    .legend-icon-box.book-female {
      border: 1.5px solid #E0E0E0;
      background: #F0F0F0;
    }

    /* Boarding & Dropping Timeline */
    .points-timeline-container {
      position: relative;
    }
    .timeline-vertical-connector {
      position: absolute;
      left: 7px;
      top: 14px;
      bottom: 24px;
      width: 2px;
      background: #CBD5E1;
      z-index: 1;
    }
    .timeline-indicator-icon {
      position: absolute;
      left: -24px;
      top: 2px;
      z-index: 2;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #FFFFFF;
    }
    .custom-points-select {
      border-color: #CBD5E1;
      border-radius: 6px;
      font-size: 0.82rem;
      color: #0F172A;
      padding: 5px 10px;
    }
    .custom-points-select:focus {
      border-color: #0875E1;
      box-shadow: 0 0 0 2px rgba(8, 117, 225, 0.15);
    }

    /* Right column & Summary */
    .selected-seat-badge-pill {
      background-color: #EFF6FF;
      border: 1px solid #DBEAFE;
      border-radius: 6px;
      padding: 3px 8px;
      font-size: 0.82rem;
      font-weight: 600;
      color: #1E3A8A;
    }
    .btn-remove-seat-x {
      color: #64748B;
      font-size: 0.76rem;
      cursor: pointer;
      transition: color 0.15s ease;
      line-height: 1;
    }
    .btn-remove-seat-x:hover {
      color: #EF4444;
    }
    .individual-seat-fare {
      font-size: 0.90rem;
    }
    .btn-reset-selection {
      color: #0875E1;
      font-size: 0.82rem;
    }
    .btn-reset-selection:hover {
      color: #0662be;
    }
    .hold-time-notice-box {
      background: #F0F9FF;
      border: 1px solid #BAE6FD;
      border-radius: 8px;
    }
    .btn-continue-booking {
      background: var(--primary-blue);
      border: none;
      letter-spacing: -0.2px;
      border-radius: 8px;
      transition: all 0.2s ease;
    }
    .btn-continue-booking:hover:not(:disabled) {
      background: #0662be;
      box-shadow: 0 3px 8px rgba(8, 117, 225, 0.25) !important;
    }

    /* Mobile / Small screen natural scrolling fallback */
    @media (max-width: 991.98px) {
      :host {
        --seat-w: 36px;
        --row-unit-h: 44px;
        --aisle-w: 24px;
        --col-gap: 8px;
        --row-gap: 6px;
        --bus-padding: 8px 14px 14px 14px;
        --decks-gap: 16px;
      }
      .seat-cell-item.seater .seater-svg {
        width: 28px;
        height: 26px;
      }
      .seat-cell-item.sleeper .sleeper-svg {
        width: 28px;
        height: 64px;
      }
      .legend-icon-box {
        width: 30px;
        height: 30px;
      }
    }
  `]
})
export class BusSeatPickerComponent implements OnInit, OnChanges {
  @Input() schedule: any = null;
  @Input() layout: TripSeatLayout | null = null;
  @Input() isLoading = false;
  @Input() origin = '';
  @Input() destination = '';
  @Input() travelDate = '';
  @Input() selectedSeats: string[] = [];
  @Input() selectedBoardingPoint = '';
  @Input() selectedDroppingPoint = '';
  @Input() currentUser: any = null;

  @Output() backToResults = new EventEmitter<void>();
  @Output() seatToggled = new EventEmitter<string>();
  @Output() seatRemoved = new EventEmitter<string>();
  @Output() seatsReset = new EventEmitter<void>();
  @Output() boardingPointChanged = new EventEmitter<string>();
  @Output() droppingPointChanged = new EventEmitter<string>();
  @Output() proceedToPayment = new EventEmitter<any>();
  @Output() bookingConfirmed = new EventEmitter<any>();
  @Output() viewMyBookings = new EventEmitter<void>();

  currentStage = 1; // 1: Select Seats, 2: Passenger Details, 3: Payment
  legendExpanded = true;
  pointsExpanded = true;
  activeDeckView: 'ALL' | 'LOWER' | 'UPPER' = 'ALL';

  passengers: PassengerInfo[] = [];
  contactEmail = '';
  contactPhone = '';

  isProcessingPayment = false;
  paymentError: string | null = null;
  paymentSuccess = false;
  confirmedBookingRef = '';
  confirmedTransactionRef = '';
  showTermsModal = false;
  showPrivacyModal = false;

  constructor(
    private bookingService: BookingService,
    private busService: BusService
  ) {}

  ngOnInit() {
    this.initContactDefaults();
    this.syncPassengers();
    this.ensureDefaultPoints();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['selectedSeats']) {
      this.syncPassengers();
    }
    if (changes['schedule'] || changes['layout']) {
      this.ensureDefaultPoints();
    }
    if (changes['currentUser']) {
      this.initContactDefaults();
    }
  }

  private initContactDefaults() {
    if (this.currentUser) {
      if (!this.contactEmail) this.contactEmail = this.currentUser.email || '';
      if (!this.contactPhone) {
        const p = this.currentUser.phone || '';
        this.contactPhone = p.startsWith('+') ? p : (p ? `+91 ${p}` : '+91 98765 43210');
      }
    } else {
      if (!this.contactEmail) this.contactEmail = 'john.doe@example.com';
      if (!this.contactPhone) this.contactPhone = '+91 98765 43210';
    }
  }

  private calculateAge(dob: string): number | null {
    if (!dob) return null;
    const birth = new Date(dob);
    if (isNaN(birth.getTime())) return null;
    const diff = Date.now() - birth.getTime();
    const ageDate = new Date(diff);
    return Math.abs(ageDate.getUTCFullYear() - 1970);
  }

  private ensureDefaultPoints() {
    queueMicrotask(() => {
      if (!this.selectedBoardingPoint && this.boardingList.length > 0) {
        this.selectedBoardingPoint = this.boardingList[0].name;
        this.boardingPointChanged.emit(this.selectedBoardingPoint);
      }
      if (!this.selectedDroppingPoint && this.droppingList.length > 0) {
        this.selectedDroppingPoint = this.droppingList[0].name;
        this.droppingPointChanged.emit(this.selectedDroppingPoint);
      }
    });
  }

  private syncPassengers() {
    // Preserve existing filled names for retained seats
    const oldMap = new Map<string, PassengerInfo>();
    this.passengers.forEach(p => oldMap.set(p.seatNumber, p));

    this.passengers = this.selectedSeats.map((seatNo, idx) => {
      if (oldMap.has(seatNo)) {
        return oldMap.get(seatNo)!;
      }
      const leadName = (idx === 0 && this.currentUser?.fullName) ? this.currentUser.fullName : (idx === 0 ? 'John Doe' : '');
      const leadAge = (idx === 0 && this.currentUser?.age) ? this.currentUser.age : (idx === 0 && this.currentUser?.dateOfBirth ? this.calculateAge(this.currentUser.dateOfBirth) : (idx === 0 ? 28 : null));
      const leadGender = (idx === 0 && this.currentUser?.gender) ? this.currentUser.gender : 'Male';

      return {
        seatNumber: seatNo,
        name: leadName,
        age: leadAge,
        gender: leadGender,
        idProof: 'Aadhaar Card'
      };
    });
  }

  // --- Display Getters ---

  get displayOperatorName(): string {
    return this.layout?.operatorName || this.schedule?.operatorName || 'Travel Easy Express';
  }

  get displayFacilityType(): string {
    return this.layout?.facilityType || (this.schedule?.hasAc !== false ? 'AC' : 'Non-AC');
  }

  get isNonAc(): boolean {
    return this.displayFacilityType.toUpperCase().includes('NON');
  }

  get displayBusModel(): string {
    return this.layout?.busType || this.schedule?.busType || this.schedule?.busNumber || 'Volvo 9600 Multi-Axle';
  }

  get displayConfigurationText(): string {
    if (this.layout?.templateName) return this.layout.templateName;
    if (this.layout?.busConfiguration === 'DOUBLE_DECK') return '2+1 Sleeper (Double Deck)';
    return '2+2 Seater (Push Back)';
  }

  get displayOrigin(): string {
    return this.layout?.origin || this.schedule?.origin || this.origin || 'Hyderabad';
  }

  get displayDestination(): string {
    return this.layout?.destination || this.schedule?.destination || this.destination || 'Vijayawada';
  }

  get departureTime(): string {
    return this.layout?.departureTime || this.schedule?.departureTime || '21:30';
  }

  get arrivalTime(): string {
    return this.layout?.arrivalTime || this.schedule?.arrivalTime || '05:45';
  }

  get departureTimeFormatted(): string {
    return this.formatTime24(this.departureTime);
  }

  get arrivalTimeFormatted(): string {
    return this.formatTime24(this.arrivalTime);
  }

  formatTime24(timeStr: string): string {
    if (!timeStr) return '';
    const parts = timeStr.split(':');
    const h = String(parts[0] || '0').padStart(2, '0');
    const m = String(parts[1] || '00').padStart(2, '0');
    return `${h}:${m}`;
  }

  get busThumbnail(): string {
    if (this.schedule?.busImage) return this.schedule.busImage;
    if (this.schedule?.thumbnailUrl) return this.schedule.thumbnailUrl;
    return 'assets/images/travel-easy-bus.png';
  }

  onThumbError(e: any) {
    e.target.src = 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=300&q=80';
  }

  get isOvernight(): boolean {
    const [depH, depM] = this.departureTime.split(':').map(Number);
    const [arrH, arrM] = this.arrivalTime.split(':').map(Number);
    const depMinutes = depH * 60 + (depM || 0);
    const arrMinutes = arrH * 60 + (arrM || 0);
    return arrMinutes < depMinutes;
  }

  get departureDateFormatted(): string {
    const rawDate = this.layout?.travelDate || this.schedule?.travelDate || this.travelDate;
    if (!rawDate) return 'Wed, 16 Apr 2025';
    try {
      const dateStr = rawDate.length === 10 ? `${rawDate}T12:00:00` : rawDate;
      const d = new Date(dateStr);
      return d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
    } catch {
      return rawDate;
    }
  }

  get arrivalDateFormatted(): string {
    const rawDate = this.layout?.travelDate || this.schedule?.travelDate || this.travelDate;
    if (!rawDate) return 'Thu, 17 Apr 2025';
    try {
      const dateStr = rawDate.length === 10 ? `${rawDate}T12:00:00` : rawDate;
      const d = new Date(dateStr);
      if (this.isOvernight) {
        d.setDate(d.getDate() + 1);
      }
      return d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
    } catch {
      return rawDate;
    }
  }

  get durationText(): string {
    const [depH, depM] = this.departureTime.split(':').map(Number);
    const [arrH, arrM] = this.arrivalTime.split(':').map(Number);
    let diff = (arrH * 60 + (arrM || 0)) - (depH * 60 + (depM || 0));
    if (diff < 0) diff += 24 * 60;
    const h = Math.floor(diff / 60);
    const m = diff % 60;
    return `${h}h ${m > 0 ? m + 'm' : ''}`;
  }

  formatTime12(time24: string): string {
    if (!time24) return '';
    const [h, m] = time24.split(':').map(Number);
    const period = h >= 12 ? 'PM' : 'AM';
    const hour12 = h % 12 || 12;
    return `${hour12}:${String(m || 0).padStart(2, '0')} ${period}`;
  }

  // --- Decks & Layout ---

  get displayDecks(): DeckLayout[] {
    if (this.layout?.decks && this.layout.decks.length > 0) {
      return this.layout.decks;
    }
    return [];
  }

  get isDoubleDeck(): boolean {
    return this.displayDecks.length > 1;
  }

  getDeckHeaderTitle(deck: DeckLayout): string {
    const totalInDeck = deck.positions?.length || deck.availableCount || 0;
    if (deck.deck === 'UPPER') {
      return `Upper Deck (${totalInDeck})`;
    } else if (this.isDoubleDeck) {
      return `Lower Deck (${totalInDeck})`;
    } else {
      return `Lower Deck (${totalInDeck})`;
    }
  }

  toggleLegend(): void {
    this.legendExpanded = !this.legendExpanded;
  }

  isMale(pos: SeatPosition): boolean {
    return pos.gender === 'MALE' || pos.seatNumber === 'L2B' || pos.seatNumber === 'U2B';
  }

  isFemale(pos: SeatPosition): boolean {
    return pos.gender === 'FEMALE' || pos.seatNumber === 'L3A' || pos.seatNumber === 'U3A';
  }

  getDeckRowNumbers(deck: DeckLayout): number[] {
    if (!deck.positions || deck.positions.length === 0) {
      const rows = deck.rows || 12;
      const arr: number[] = [];
      for (let i = 1; i <= rows; i++) arr.push(i);
      return arr;
    }
    const set = new Set<number>();
    deck.positions.forEach(p => set.add(p.row));
    return Array.from(set).sort((a, b) => a - b);
  }

  getRowSeats(deck: DeckLayout, row: number, side: 'LEFT' | 'RIGHT'): SeatPosition[] {
    if (!deck.positions) return [];
    return deck.positions
      .filter(p => p.row === row && p.side === side)
      .sort((a, b) => a.column - b.column);
  }

  // --- Bus Deck CSS Grid Helpers for Physically Coherent Layout ---

  isDeck2x2(deck: DeckLayout): boolean {
    return deck.positions?.some(p => p.column === 1 || p.side === 'CENTER') || false;
  }

  isDeck1x1(deck: DeckLayout): boolean {
    const hasCol3 = deck.positions?.some(p => p.column >= 3);
    const hasLeftCol1 = deck.positions?.some(p => p.column === 1);
    const maxRightCol = Math.max(...(deck.positions?.filter(p => p.side === 'RIGHT').map(p => p.column) || [0]));
    return !hasCol3 && !hasLeftCol1 && maxRightCol <= 2 && (deck.columns === 3 || deck.columns === 2);
  }

  getDeckTotalGridRows(deck: DeckLayout): number {
    return 12;
  }

  getDeckGridTemplateColumns(deck: DeckLayout): string {
    if (this.isDeck2x2(deck)) {
      return 'var(--seat-w) var(--seat-w) var(--aisle-w) var(--seat-w) var(--seat-w)';
    } else if (this.isDeck1x1(deck)) {
      return 'var(--seat-w) var(--aisle-w) var(--seat-w)';
    } else {
      return 'var(--seat-w) var(--aisle-w) var(--seat-w) var(--seat-w)';
    }
  }

  getGridColumn(deck: DeckLayout, pos: SeatPosition): string {
    const is2x2 = this.isDeck2x2(deck);
    const is1x1 = this.isDeck1x1(deck);

    if (is2x2) {
      if (pos.side === 'LEFT') {
        return pos.column === 0 ? '1' : '2';
      } else if (pos.side === 'CENTER') {
        return '3';
      } else {
        return (pos.column === 2 || (pos.row === 12 && pos.column === 3)) ? '4' : '5';
      }
    } else if (is1x1) {
      if (pos.side === 'LEFT') {
        return '1';
      } else {
        return '3';
      }
    } else {
      // 2+1 Layouts: col 1 = Left, col 2 = Aisle, col 3 = Right Aisle, col 4 = Right Window
      if (pos.side === 'LEFT') {
        return '1';
      } else {
        return pos.column === 2 ? '3' : '4';
      }
    }
  }

  getGridRow(deck: DeckLayout, pos: SeatPosition): string {
    const isSleeper = pos.seatType === 'SLEEPER';

    if (isSleeper) {
      let startRow = pos.row;
      // If sleeper rows are 1..6 (sequential) rather than 1, 3, 5, 7, 9, 11
      const hasEvenSleeperRows = deck.positions?.some(p => p.seatType === 'SLEEPER' && (p.row === 2 || p.row === 4 || p.row === 6));
      if (hasEvenSleeperRows && pos.row <= 6) {
        startRow = (pos.row - 1) * 2 + 1;
      }
      return `${startRow} / span 2`;
    } else {
      return `${pos.row} / span 1`;
    }
  }

  isSeatSelected(seatNumber: string): boolean {
    return this.selectedSeats.includes(seatNumber);
  }

  onSeatCellClick(pos: SeatPosition) {
    if (pos.status === 'BOOKED' || !pos.isBookable) return;
    this.seatToggled.emit(pos.seatNumber);
  }

  getSeatPrice(seatNumber: string): number {
    if (this.displayDecks.length > 0) {
      for (const deck of this.displayDecks) {
        const found = deck.positions?.find(p => p.seatNumber === seatNumber);
        if (found && found.price) return found.price;
      }
    }
    return this.layout?.baseFare || this.schedule?.fare || 650;
  }

  // --- Boarding & Dropping Points ---

  get boardingList(): { name: string; time: string }[] {
    const raw = this.layout?.boardingPoints || this.schedule?.boardingPoints || '';
    if (raw && raw.trim().length > 0) {
      const parts = raw.split(',').map(s => s.trim()).filter(s => s.length > 0);
      return parts.map((name, idx) => ({
        name,
        time: this.computeOffsetTime(this.departureTime, idx * 15)
      }));
    }
    return [
      { name: `${this.displayOrigin} Central Bus Station`, time: this.departureTime },
      { name: `${this.displayOrigin} City Bypass`, time: this.computeOffsetTime(this.departureTime, 20) },
      { name: `${this.displayOrigin} Toll Gate`, time: this.computeOffsetTime(this.departureTime, 40) }
    ];
  }

  get droppingList(): { name: string; time: string }[] {
    const raw = this.layout?.droppingPoints || this.schedule?.droppingPoints || '';
    if (raw && raw.trim().length > 0) {
      const parts = raw.split(',').map(s => s.trim()).filter(s => s.length > 0);
      return parts.map((name, idx) => ({
        name,
        time: this.computeOffsetTime(this.arrivalTime, idx * 20)
      }));
    }
    return [
      { name: `${this.displayDestination} Toll Gate`, time: this.computeOffsetTime(this.arrivalTime, -20) },
      { name: `${this.displayDestination} Main Terminal`, time: this.arrivalTime },
      { name: `${this.displayDestination} City Center`, time: this.computeOffsetTime(this.arrivalTime, 20) }
    ];
  }

  private computeOffsetTime(baseTime: string, offsetMins: number): string {
    if (!baseTime) return '';
    const [h, m] = baseTime.split(':').map(Number);
    let total = h * 60 + (m || 0) + offsetMins;
    if (total < 0) total += 24 * 60;
    total = total % (24 * 60);
    const newH = Math.floor(total / 60);
    const newM = total % 60;
    return this.formatTime12(`${newH}:${newM}`);
  }

  onBoardingChange(val: string) {
    this.selectedBoardingPoint = val;
    this.boardingPointChanged.emit(val);
  }

  onDroppingChange(val: string) {
    this.selectedDroppingPoint = val;
    this.droppingPointChanged.emit(val);
  }

  // --- Selection Actions ---

  onRemoveSeat(seatNumber: string) {
    this.seatRemoved.emit(seatNumber);
  }

  onResetSeats() {
    this.seatsReset.emit();
  }

  onRemoveAllSeats() {
    this.seatsReset.emit();
  }

  onBackToResults() {
    this.backToResults.emit();
  }

  // --- Financial Calculations ---

  get farePerSeat(): number {
    if (this.selectedSeats.length === 0) return 0;
    return Math.round(this.calculatedBaseFare / this.selectedSeats.length);
  }

  get calculatedBaseFare(): number {
    return this.selectedSeats.reduce((sum, s) => sum + this.getSeatPrice(s), 0);
  }

  get calculatedTaxes(): number {
    // 5% GST
    return Math.round(this.calculatedBaseFare * 0.05 * 100) / 100;
  }

  get calculatedTotalAmount(): number {
    return this.calculatedBaseFare + this.calculatedTaxes;
  }

  // --- Validation ---

  get canProceedToPassengerDetails(): boolean {
    return this.selectedSeats.length > 0 &&
           !!this.selectedBoardingPoint &&
           !!this.selectedDroppingPoint;
  }

  get validationNotice(): string | null {
    if (this.selectedSeats.length === 0) return 'Please select at least 1 seat to proceed';
    if (!this.selectedBoardingPoint) return 'Please select your boarding pickup point';
    if (!this.selectedDroppingPoint) return 'Please select your dropping destination point';
    return null;
  }

  get isPassengerFormValid(): boolean {
    if (this.selectedSeats.length === 0 || this.passengers.length === 0) return false;
    for (const p of this.passengers) {
      if (!p.name || p.name.trim().length < 2) return false;
      if (!p.age || p.age < 1 || p.age > 120) return false;
      if (!p.gender) return false;
    }
    if (!this.contactEmail || !this.contactEmail.includes('@') || !this.contactEmail.includes('.')) return false;
    if (!this.contactPhone || this.contactPhone.trim().length < 7) return false;
    return true;
  }

  get selectedBoardingTime(): string {
    const found = this.boardingList.find(b => b.name === this.selectedBoardingPoint);
    return found ? found.time : this.departureTimeFormatted;
  }

  get selectedDroppingTime(): string {
    const found = this.droppingList.find(d => d.name === this.selectedDroppingPoint);
    return found ? found.time : this.arrivalTimeFormatted;
  }

  onRemovePassenger(idx: number) {
    if (idx <= 0 || idx >= this.passengers.length) return;
    const removedSeat = this.passengers[idx].seatNumber;
    this.passengers.splice(idx, 1);
    const seatIdx = this.selectedSeats.indexOf(removedSeat);
    if (seatIdx >= 0) {
      this.selectedSeats.splice(seatIdx, 1);
      this.seatRemoved.emit(removedSeat);
    }
  }

  get backButtonText(): string {
    if (this.currentStage === 3) return 'Back to passenger details';
    if (this.currentStage === 2) return 'Back to seat selection';
    return 'Back to bus results';
  }

  get pageTitleText(): string {
    if (this.currentStage === 3) return 'Payment';
    if (this.currentStage === 2) return 'Passenger Details';
    return 'Select Your Seats';
  }

  onHeaderBack() {
    if (this.currentStage === 3) {
      this.currentStage = 2;
    } else if (this.currentStage === 2) {
      this.currentStage = 1;
    } else {
      this.backToResults.emit();
    }
  }

  onEditSeats() {
    this.currentStage = 1;
  }

  onContinueToPassengerDetails() {
    if (!this.canProceedToPassengerDetails) return;
    this.syncPassengers();
    this.currentStage = 2;
  }

  onProceedToPaymentStage() {
    if (!this.isPassengerFormValid) return;
    this.currentStage = 3;
    this.paymentError = null;
  }

  openTerms() {
    this.showTermsModal = true;
  }

  openPrivacy() {
    this.showPrivacyModal = true;
  }

  onViewMyBookings() {
    this.viewMyBookings.emit();
  }

  onBookAnotherBus() {
    this.backToResults.emit();
  }

  onPayNow() {
    if (this.isProcessingPayment || this.selectedSeats.length === 0) return;
    this.isProcessingPayment = true;
    this.paymentError = null;

    const passList = (this.passengers || []).map((p: any) => `${p.name} (Seat ${p.seatNumber}, Age: ${p.age}, ${p.gender})`).join('; ');
    const detailsJson = `Seats: ${this.selectedSeats.join(', ')} | Boarding: ${this.selectedBoardingPoint} | Dropping: ${this.selectedDroppingPoint} | Passengers: ${passList} | Date: ${this.schedule?.travelDate || this.travelDate}`;
    
    const customerId = this.currentUser?.id || 1;
    const customerName = this.currentUser?.fullName || (this.passengers[0]?.name) || 'Traveller';
    const customerEmail = this.contactEmail || this.currentUser?.email || 'traveller@traveleasy.com';

    // Step 1: Pre-booking inventory hold in BUS
    this.busService.bookSeats(this.schedule.id, this.selectedSeats).subscribe({
      next: () => {
        // Step 2: Create Booking in BKG
        const bookingPayload = {
          customerId: customerId,
          customerName: customerName,
          customerEmail: customerEmail,
          serviceType: 'BUS',
          serviceId: this.schedule.id,
          providerId: this.schedule.busId || 1,
          serviceTitle: `${this.schedule.operatorName} (${this.displayOrigin} to ${this.displayDestination})`,
          totalAmount: this.calculatedTotalAmount,
          detailsJson: detailsJson
        };

        this.bookingService.createBooking(bookingPayload).subscribe({
          next: (booking: any) => {
            // Step 3: Process Payment in PAY
            this.bookingService.processPayment({
              bookingRef: booking.bookingRef,
              customerId: customerId,
              amount: this.calculatedTotalAmount,
              paymentMethod: 'GATEWAY'
            }).subscribe({
              next: () => {
                // Step 4: Confirm Booking in BKG
                this.bookingService.confirmBooking(booking.id).subscribe({
                  next: () => {
                    this.isProcessingPayment = false;
                    this.paymentSuccess = true;
                    this.confirmedBookingRef = booking.bookingRef;
                    this.confirmedTransactionRef = `TXN-${Date.now().toString(36).toUpperCase()}-${Math.floor(1000 + Math.random() * 9000)}`;
                    this.bookingConfirmed.emit({
                      booking,
                      passengers: this.passengers,
                      seats: this.selectedSeats,
                      schedule: this.schedule
                    });
                  },
                  error: () => {
                    this.compensatePaymentFailure(booking.id);
                  }
                });
              },
              error: () => {
                this.compensatePaymentFailure(booking.id);
              }
            });
          },
          error: () => {
            this.busService.releaseSeats(this.schedule.id, this.selectedSeats).subscribe();
            this.isProcessingPayment = false;
            this.paymentError = 'Unable to create booking reservation. Your temporary seat hold has been released. Please try again.';
          }
        });
      },
      error: () => {
        this.isProcessingPayment = false;
        this.paymentError = 'Selected seat(s) could not be reserved or are no longer available. Please choose another seat.';
      }
    });
  }

  private compensatePaymentFailure(bookingId: number) {
    this.isProcessingPayment = false;
    this.busService.releaseSeats(this.schedule.id, this.selectedSeats).subscribe();
    this.bookingService.cancelBooking(bookingId).subscribe();
    this.paymentError = 'Payment authorization failed. Your temporary seat hold has been released. Please try again.';
  }
}
