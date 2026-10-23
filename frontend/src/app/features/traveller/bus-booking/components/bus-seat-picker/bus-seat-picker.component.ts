import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TripSeatLayout, DeckLayout, SeatPosition } from '../../../../../models/bus.model';

export interface PassengerInfo {
  seatNumber: string;
  name: string;
  age: number | null;
  gender: string;
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
            <!-- Back to bus results (Strictly ABOVE title) -->
            <button class="btn btn-link btn-back-results p-0 text-decoration-none d-inline-flex align-items-center mb-1" (click)="onBackToResults()">
              <i class="fa-solid fa-arrow-left me-1 small"></i>
              <span>Back to bus results</span>
            </button>
            <div class="d-flex align-items-baseline gap-2">
              <h5 class="fw-bold text-navy mb-0 page-title">Select Your Seats</h5>
              <span class="text-secondary smaller page-subtitle d-none d-md-inline">
                • Choose your preferred seats and boarding/dropping points
              </span>
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
            <div class="step-item d-flex align-items-center" [class.active]="currentStage === 3">
              <span class="step-circle me-1">3</span>
              <span class="step-label">Payment</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ================= SELECTED BUS & JOURNEY SUMMARY CARD ================= -->
      <!-- Explicit Rule: Do NOT display seat count or boarding/dropping points here -->
      <div class="bus-journey-summary-card bg-white rounded-3 border p-2 px-3 shadow-xs mb-2">
        <div class="row align-items-center g-2">
          
          <!-- Bus & Operator info -->
          <div class="col-xl-4 col-lg-4 col-md-12">
            <div class="d-flex align-items-center gap-2">
              <img [src]="busThumbnail" class="bus-summary-thumb shadow-xs" alt="Bus photo" (error)="onThumbError($event)">
              <div class="min-w-0">
                <div class="d-flex align-items-center gap-2">
                  <h6 class="fw-bold text-navy mb-0 operator-name text-truncate">{{ displayOperatorName }}</h6>
                  <span class="badge-ac-facility" [class.badge-non-ac]="isNonAc">
                    {{ displayFacilityType }}
                  </span>
                </div>
                <div class="bus-model-text text-muted text-micro text-truncate mt-0">
                  {{ displayBusModel }} • {{ displayConfigurationText }}
                </div>
              </div>
            </div>
          </div>

          <!-- Journey Timeline Track -->
          <div class="col-xl-8 col-lg-8 col-md-12">
            <div class="d-flex justify-content-between align-items-center px-lg-2">
              
              <!-- Departure -->
              <div class="text-start">
                <div class="timeline-time">{{ formatTime12(departureTime) }}</div>
                <div class="timeline-city fw-bold text-navy smaller">{{ displayOrigin }}</div>
                <div class="timeline-date text-muted text-micro">{{ departureDateFormatted }}</div>
              </div>

              <!-- Route Duration Track with Bus Icon -->
              <div class="timeline-track-wrap flex-grow-1 text-center px-3">
                <div class="duration-badge-pill">
                  <i class="fa-regular fa-clock me-1"></i>{{ durationText }}
                </div>
                <div class="timeline-line-graphic position-relative my-1">
                  <div class="timeline-line-bg"></div>
                  <div class="timeline-bus-icon-wrap">
                    <i class="fa-solid fa-bus text-primary" style="font-size: 0.75rem;"></i>
                  </div>
                </div>
                <div class="text-muted text-micro">Direct Journey</div>
              </div>

              <!-- Arrival -->
              <div class="text-end">
                <div class="timeline-time">
                  {{ formatTime12(arrivalTime) }}
                  <span *ngIf="isOvernight" class="badge-overnight" title="Arrives on the next calendar day">+1 Day</span>
                </div>
                <div class="timeline-city fw-bold text-navy smaller">{{ displayDestination }}</div>
                <div class="timeline-date text-muted text-micro">{{ arrivalDateFormatted }}</div>
              </div>

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
      <div *ngIf="!isLoading && displayDecks.length > 0 && currentStage === 1" class="row g-2 g-xl-3 align-items-stretch">
        
        <!-- ================= COLUMN 1: SEAT LAYOUT ================= -->
        <div class="col-xl-6 col-lg-6 col-md-12">
          <div class="seat-layout-card bg-white rounded-3 border p-2 p-md-3 shadow-xs h-100 d-flex flex-column">
            <!-- Header matching Screenshot 1 -->
            <div class="d-flex justify-content-between align-items-center mb-2">
              <h6 class="fw-bold text-dark mb-0">Select Seats</h6>
              <span class="fw-semibold text-secondary smaller cursor-pointer" (click)="toggleLegend()">Know your seats</span>
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
                  
                  <!-- Deck Header matching Screenshot 1 (e.g. LOWER BERTH(19), UPPER BERTH(4)) -->
                  <div class="deck-title-header mb-1 text-center fw-bold smaller text-uppercase">
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

                    <!-- Seat Matrix Rows -->
                    <div class="seats-grid-table">
                      <div *ngFor="let rowNum of getDeckRowNumbers(deck)" class="seat-row-track d-flex align-items-center justify-content-between">
                        
                        <!-- Left Side Seats -->
                        <div class="d-flex gap-1">
                          <ng-container *ngFor="let p of getRowSeats(deck, rowNum, 'LEFT')">
                            <ng-container *ngTemplateOutlet="seatTemplate; context: { pos: p }"></ng-container>
                          </ng-container>
                          <div *ngIf="getRowSeats(deck, rowNum, 'LEFT').length === 0" class="seat-empty-spacer"></div>
                        </div>

                        <!-- Clean Aisle Corridor (Blank corridor, no border) -->
                        <div class="bus-aisle-corridor"></div>

                        <!-- Right Side Seats -->
                        <div class="d-flex gap-1">
                          <ng-container *ngFor="let p of getRowSeats(deck, rowNum, 'RIGHT')">
                            <ng-container *ngTemplateOutlet="seatTemplate; context: { pos: p }"></ng-container>
                          </ng-container>
                          <div *ngIf="getRowSeats(deck, rowNum, 'RIGHT').length === 0" class="seat-empty-spacer"></div>
                        </div>

                      </div>
                    </div>

                  </div>

                </div>
              </ng-container>

            </div>

          </div>
        </div>

        <!-- ================= COLUMN 2: LEGEND & BOARDING/DROPPING POINTS ================= -->
        <div class="col-xl-3 col-lg-3 col-md-6">
          <div class="d-flex flex-column gap-2 h-100">
            
            <!-- Card A: Seat Legend matching Screenshot 2 (Seater / Sleeper info) -->
            <div class="bg-white rounded-3 border p-2 px-3 shadow-xs collapsible-card">
              <div class="d-flex justify-content-between align-items-center cursor-pointer mb-1" (click)="legendExpanded = !legendExpanded">
                <span class="fw-bold text-dark text-micro-heading mb-0">Seater / Sleeper info</span>
                <button class="btn btn-sm btn-link p-0 text-secondary">
                  <i class="fa-solid fa-xs" [class.fa-chevron-up]="legendExpanded" [class.fa-chevron-down]="!legendExpanded"></i>
                </button>
              </div>

              <!-- Expanded Legend Content matching Screenshot 2 -->
              <div *ngIf="legendExpanded" class="legend-matrix-table mt-1 pt-1 border-top">
                
                <!-- Column Headers -->
                <div class="legend-row d-flex align-items-center pb-1">
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
            <div class="bg-white rounded-3 border p-2 px-3 shadow-xs collapsible-card flex-grow-1">
              <div class="d-flex justify-content-between align-items-center cursor-pointer mb-1" (click)="pointsExpanded = !pointsExpanded">
                <span class="fw-bold text-dark text-micro-heading mb-0">Boarding & Dropping Points</span>
                <button class="btn btn-sm btn-link p-0 text-secondary">
                  <i class="fa-solid fa-xs" [class.fa-chevron-up]="pointsExpanded" [class.fa-chevron-down]="!pointsExpanded"></i>
                </button>
              </div>

              <!-- Collapsed Summary Chips -->
              <div *ngIf="!pointsExpanded" class="mt-1 pt-1 border-top">
                <div class="text-micro text-secondary d-flex flex-column gap-1">
                  <div class="text-truncate">
                    <i class="fa-solid fa-location-dot text-success me-1"></i>
                    <span class="fw-semibold">{{ selectedBoardingPoint || 'Boarding not chosen' }}</span>
                  </div>
                  <div class="text-truncate">
                    <i class="fa-solid fa-location-dot text-danger me-1"></i>
                    <span class="fw-semibold">{{ selectedDroppingPoint || 'Dropping not chosen' }}</span>
                  </div>
                </div>
              </div>

              <!-- Expanded Points Form -->
              <div *ngIf="pointsExpanded" class="mt-2 pt-1 border-top">
                
                <!-- Boarding Point -->
                <div class="mb-2">
                  <label class="form-label text-micro fw-bold text-navy mb-1 d-flex align-items-center">
                    <i class="fa-solid fa-location-dot text-success me-1"></i>Boarding Point <span class="text-danger ms-1">*</span>
                  </label>
                  <select class="form-select form-select-sm rounded-2 text-micro py-1" 
                          [ngModel]="selectedBoardingPoint" 
                          (ngModelChange)="onBoardingChange($event)">
                    <option value="" disabled>Select pickup location</option>
                    <option *ngFor="let bp of boardingList" [value]="bp.name">
                      {{ bp.name }} {{ bp.time ? '(' + bp.time + ')' : '' }}
                    </option>
                  </select>
                </div>

                <!-- Dropping Point -->
                <div class="mb-1">
                  <label class="form-label text-micro fw-bold text-navy mb-1 d-flex align-items-center">
                    <i class="fa-solid fa-location-dot text-danger me-1"></i>Dropping Point <span class="text-danger ms-1">*</span>
                  </label>
                  <select class="form-select form-select-sm rounded-2 text-micro py-1" 
                          [ngModel]="selectedDroppingPoint" 
                          (ngModelChange)="onDroppingChange($event)">
                    <option value="" disabled>Select drop-off location</option>
                    <option *ngFor="let dp of droppingList" [value]="dp.name">
                      {{ dp.name }} {{ dp.time ? '(' + dp.time + ')' : '' }}
                    </option>
                  </select>
                </div>

                <div class="text-muted text-micro mt-1">
                  <i class="fa-solid fa-circle-exclamation me-1 text-primary"></i>Selection required before checkout
                </div>

              </div>
            </div>

          </div>
        </div>

        <!-- ================= COLUMN 3: YOUR SELECTION & FARE SUMMARY ================= -->
        <div class="col-xl-3 col-lg-3 col-md-12">
          <div class="bg-white rounded-3 border p-2 px-3 shadow-xs h-100 d-flex flex-column justify-content-between">
            
            <div>
              <!-- Header: Your Selection with Count and Reset / Remove All -->
              <div class="d-flex justify-content-between align-items-center mb-2 pb-1 border-bottom">
                <div class="d-flex align-items-center gap-2">
                  <h6 class="fw-bold text-navy mb-0 smaller">Your Selection</h6>
                  <span class="badge rounded-pill" [class.bg-primary]="selectedSeats.length > 0" [class.bg-secondary]="selectedSeats.length === 0" style="font-size: 0.68rem;">
                    {{ selectedSeats.length }} {{ selectedSeats.length === 1 ? 'seat' : 'seats' }}
                  </span>
                </div>
                
                <div *ngIf="selectedSeats.length > 0" class="d-flex gap-2">
                  <button class="btn btn-link p-0 text-micro text-secondary text-decoration-none" (click)="onResetSeats()">
                    Reset
                  </button>
                  <span class="text-muted text-micro">•</span>
                  <button class="btn btn-link p-0 text-micro text-danger text-decoration-none" (click)="onRemoveAllSeats()">
                    Remove All
                  </button>
                </div>
              </div>

              <!-- Selected Seats List / Empty State -->
              <div class="selected-seats-chip-container mb-2" style="max-height: 80px; overflow-y: auto;">
                <div *ngIf="selectedSeats.length === 0" class="empty-selection-box py-2 text-center border rounded-2 bg-light">
                  <i class="fa-solid fa-chair text-muted small mb-1"></i>
                  <div class="fw-bold text-navy text-micro">No seats selected yet</div>
                  <div class="text-muted text-micro px-2">
                    Click available seats in the layout to add.
                  </div>
                </div>

                <div *ngIf="selectedSeats.length > 0" class="selected-seats-list d-flex flex-wrap gap-1">
                  <div *ngFor="let sn of selectedSeats" class="selected-seat-chip-item d-inline-flex align-items-center gap-1 bg-light border rounded-pill px-2 py-0">
                    <span class="fw-bold text-primary text-micro">{{ sn }}</span>
                    <span class="text-secondary text-micro">₹{{ getSeatPrice(sn) | number:'1.0-0' }}</span>
                    <button class="btn btn-link p-0 text-muted ms-1 text-micro hover-danger border-0" (click)="onRemoveSeat(sn)" title="Remove seat">
                      <i class="fa-solid fa-xmark"></i>
                    </button>
                  </div>
                </div>
              </div>

              <!-- Fare Breakdown Details -->
              <div class="fare-breakdown-box bg-light rounded-2 p-2 mb-2 border text-micro">
                <div class="d-flex justify-content-between align-items-center mb-1 text-secondary">
                  <span>Base Fare ({{ selectedSeats.length }} seat{{ selectedSeats.length !== 1 ? 's' : '' }})</span>
                  <span class="fw-semibold text-navy">₹{{ calculatedBaseFare | number:'1.2-2' }}</span>
                </div>

                <div class="d-flex justify-content-between align-items-center mb-1 text-secondary">
                  <span class="d-flex align-items-center gap-1">
                    Taxes & Fees 
                    <i class="fa-solid fa-circle-info text-muted cursor-pointer" title="Includes 5% GST and platform fee"></i>
                  </span>
                  <span class="fw-semibold text-navy">₹{{ calculatedTaxes | number:'1.2-2' }}</span>
                </div>

                <hr class="my-1">

                <div class="d-flex justify-content-between align-items-center pt-1">
                  <span class="fw-bold text-navy smaller">Total Amount</span>
                  <span class="fw-bold text-primary fs-5">₹{{ calculatedTotalAmount | number:'1.2-2' }}</span>
                </div>
              </div>

              <!-- Continue Button -->
              <button class="btn btn-primary w-100 py-2 rounded-pill fw-bold btn-continue-booking shadow-xs"
                      [disabled]="!canProceedToPassengerDetails"
                      (click)="onContinueToPassengerDetails()"
                      style="font-size: 0.88rem;">
                <span>Continue to Passenger Details</span>
                <i class="fa-solid fa-arrow-right ms-2 small"></i>
              </button>

              <!-- Validation message if disabled -->
              <div *ngIf="validationNotice" class="text-danger text-center text-micro mt-1">
                <i class="fa-solid fa-circle-exclamation me-1"></i>{{ validationNotice }}
              </div>
            </div>

            <!-- Hold Time Notice Box -->
            <div class="hold-time-notice-box p-2 rounded-2 mt-2">
              <div class="d-flex align-items-start gap-2">
                <i class="fa-solid fa-circle-info text-primary mt-1 text-micro"></i>
                <div>
                  <div class="fw-bold text-navy text-micro">Hold time: 10 minutes</div>
                  <div class="text-muted text-micro" style="line-height: 1.2;">
                    Your selected seats will be temporarily held for 10 minutes while you complete your booking.
                  </div>
                </div>
              </div>
            </div>

          </div>
        </div>

      </div>

      <!-- ================= STAGE 2: PASSENGER DETAILS VIEW ================= -->
      <div *ngIf="currentStage === 2" class="passenger-details-stage-wrap">
        <div class="mb-3">
          <button class="btn btn-outline-secondary btn-sm rounded-pill px-3" (click)="currentStage = 1">
            <i class="fa-solid fa-arrow-left me-1"></i> Back to Seat Selection
          </button>
        </div>

        <div class="row g-4">
          <!-- Passenger Forms Column -->
          <div class="col-xl-8 col-lg-8">
            <div class="bg-white rounded-4 border p-4 shadow-sm mb-4">
              <h5 class="fw-bold text-navy mb-3">
                <i class="fa-solid fa-user-group text-primary me-2"></i>Passenger Information
              </h5>
              <p class="text-muted small mb-4">Please enter legal names matching official government photo IDs.</p>

              <!-- Passenger details for each selected seat -->
              <div *ngFor="let p of passengers; let idx = index" class="passenger-seat-card p-3 rounded-3 border bg-light mb-3">
                <div class="d-flex align-items-center justify-content-between mb-3 border-bottom pb-2">
                  <span class="fw-bold text-navy">
                    <span class="badge bg-primary me-2">Seat {{ p.seatNumber }}</span> Passenger {{ idx + 1 }}
                  </span>
                  <span class="smaller text-muted">Primary Seat</span>
                </div>

                <div class="row g-3">
                  <div class="col-md-6">
                    <label class="form-label smaller fw-bold text-navy">Full Name *</label>
                    <input type="text" class="form-control form-control-sm rounded-3" 
                           [(ngModel)]="p.name" placeholder="Enter passenger full name">
                  </div>
                  <div class="col-md-3">
                    <label class="form-label smaller fw-bold text-navy">Age *</label>
                    <input type="number" min="1" max="120" class="form-control form-control-sm rounded-3" 
                           [(ngModel)]="p.age" placeholder="Age">
                  </div>
                  <div class="col-md-3">
                    <label class="form-label smaller fw-bold text-navy">Gender *</label>
                    <select class="form-select form-select-sm rounded-3" [(ngModel)]="p.gender">
                      <option value="MALE">Male</option>
                      <option value="FEMALE">Female</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </div>
                </div>
              </div>

              <!-- Contact Information -->
              <h6 class="fw-bold text-navy mt-4 mb-3">
                <i class="fa-solid fa-envelope text-primary me-2"></i>Contact Information (For Tickets & SMS updates)
              </h6>
              <div class="row g-3">
                <div class="col-md-6">
                  <label class="form-label smaller fw-bold text-navy">Email Address *</label>
                  <input type="email" class="form-control form-control-sm rounded-3" 
                         [(ngModel)]="contactEmail" placeholder="traveller@example.com">
                </div>
                <div class="col-md-6">
                  <label class="form-label smaller fw-bold text-navy">Mobile Number *</label>
                  <input type="tel" class="form-control form-control-sm rounded-3" 
                         [(ngModel)]="contactPhone" placeholder="10-digit mobile number">
                </div>
              </div>
            </div>
          </div>

          <!-- Trip & Fare Summary Column in Stage 2 -->
          <div class="col-xl-4 col-lg-4">
            <div class="bg-white rounded-4 border p-4 shadow-sm">
              <h5 class="fw-bold text-navy mb-3">Trip Summary</h5>
              
              <div class="summary-route-item mb-3 p-3 bg-light rounded-3 border">
                <div class="fw-bold text-navy">{{ displayOperatorName }}</div>
                <div class="text-secondary smaller">{{ displayOrigin }} to {{ displayDestination }}</div>
                <div class="text-muted smaller">{{ departureDateFormatted }} • {{ formatTime12(departureTime) }}</div>
                <hr class="my-2">
                <div class="smaller">
                  <span class="text-muted">Seats:</span> 
                  <strong class="text-navy ms-1">{{ selectedSeats.join(', ') }}</strong>
                </div>
                <div class="smaller mt-1">
                  <span class="text-muted">Pickup:</span> 
                  <strong class="text-success ms-1">{{ selectedBoardingPoint }}</strong>
                </div>
                <div class="smaller mt-1">
                  <span class="text-muted">Drop:</span> 
                  <strong class="text-danger ms-1">{{ selectedDroppingPoint }}</strong>
                </div>
              </div>

              <div class="d-flex justify-content-between mb-2 small text-secondary">
                <span>Base Fare</span>
                <span class="fw-bold text-navy">₹{{ calculatedBaseFare | number:'1.2-2' }}</span>
              </div>
              <div class="d-flex justify-content-between mb-2 small text-secondary">
                <span>Taxes & Fees</span>
                <span class="fw-bold text-navy">₹{{ calculatedTaxes | number:'1.2-2' }}</span>
              </div>
              <hr class="my-2">
              <div class="d-flex justify-content-between mb-3">
                <span class="fw-bold text-navy fs-6">Payable Amount</span>
                <span class="fw-bold fs-4 text-primary">₹{{ calculatedTotalAmount | number:'1.2-2' }}</span>
              </div>

              <button class="btn btn-success w-100 py-3 rounded-pill fw-bold shadow-sm"
                      [disabled]="!isPassengerFormValid"
                      (click)="onProceedToPaymentStage()">
                <span>Proceed to Payment</span>
                <i class="fa-solid fa-lock ms-2"></i>
              </button>

              <div *ngIf="!isPassengerFormValid" class="text-danger smaller text-center mt-2">
                Please complete all passenger full names, ages, and contact details.
              </div>
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
      color: #059669;
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
      background: #10B981;
      color: #FFFFFF;
    }
    .step-connector {
      width: 20px;
      height: 2px;
      background: #E2E8F0;
      margin: 0 6px;
    }
    .step-connector.completed {
      background: #10B981;
    }

    /* Bus Summary Card */
    .bus-summary-thumb {
      width: 44px;
      height: 44px;
      border-radius: 8px;
      object-fit: cover;
      flex-shrink: 0;
    }
    .operator-name {
      font-size: 0.95rem;
      letter-spacing: -0.2px;
    }
    .badge-ac-facility {
      background: #ECFDF5;
      color: #059669;
      border: 1px solid #A7F3D0;
      font-size: 0.65rem;
      font-weight: 700;
      padding: 2px 6px;
      border-radius: 12px;
    }
    .badge-non-ac {
      background: #F1F5F9;
      color: #64748B;
      border-color: #CBD5E1;
    }
    .timeline-time {
      font-size: 1.05rem;
      font-weight: 800;
      color: var(--dark-navy);
      line-height: 1.1;
    }
    .timeline-city {
      font-size: 0.78rem;
      line-height: 1.1;
    }
    .duration-badge-pill {
      display: inline-block;
      font-size: 0.65rem;
      font-weight: 600;
      color: #475569;
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      padding: 1px 8px;
      border-radius: 10px;
    }
    .timeline-line-graphic {
      height: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .timeline-line-bg {
      position: absolute;
      width: 100%;
      height: 2px;
      background: #CBD5E1;
    }
    .timeline-bus-icon-wrap {
      position: relative;
      background: #FFFFFF;
      padding: 0 4px;
      font-size: 0.75rem;
    }
    .badge-overnight {
      font-size: 0.60rem;
      background: #FEF3C7;
      color: #92400E;
      padding: 1px 4px;
      border-radius: 4px;
      font-weight: 700;
      margin-left: 2px;
      vertical-align: middle;
    }

    /* Bus Outline & Deck Container matching Screenshot 1 */
    .decks-container {
      display: flex;
      flex-direction: row;
      justify-content: center;
      align-items: stretch; /* Guarantees equal height/length for both Upper and Lower decks */
      gap: 16px;
      width: 100%;
    }
    .deck-column {
      display: flex;
      flex-direction: column;
      flex: 1 1 0;
      min-width: 180px;
      max-width: 260px;
    }
    .deck-title-header {
      color: #555555;
      letter-spacing: 0.5px;
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
    }
    .bus-body-outline {
      background: #FFFFFF;
      border: 1px solid #DCDCDC;
      border-radius: 14px;
      flex: 1 1 auto;
      display: flex;
      flex-direction: column;
      justify-content: flex-start;
      padding: 4px 8px 6px 8px;
      box-shadow: none;
    }
    .deck-top-cab {
      height: 16px;
    }
    .steering-wheel-svg {
      stroke: #9E9E9E;
    }
    .bus-aisle-corridor {
      width: 16px;
      height: 100%;
    }
    .seat-row-track {
      margin-bottom: 2px;
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
    }
    .seat-cell-item.seater {
      width: 26px;
      min-height: 33px;
    }
    .seat-cell-item.sleeper {
      width: 26px;
      min-height: 56px;
    }
    .seat-empty-spacer {
      width: 26px;
      height: 33px;
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

    /* Right column & Summary */
    .hold-time-notice-box {
      background: #F0F7FF;
      border: 1px solid #BAE6FD;
    }
    .btn-continue-booking {
      background: var(--primary-blue);
      border: none;
      letter-spacing: -0.2px;
      transition: all 0.2s ease;
    }
    .btn-continue-booking:hover:not(:disabled) {
      background: #0662be;
      box-shadow: 0 3px 8px rgba(8, 117, 225, 0.25) !important;
    }

    /* Mobile / Small screen natural scrolling fallback */
    @media (max-width: 991.98px) {
      .seat-cell-item.seater {
        width: 36px;
        min-height: 46px;
      }
      .seat-cell-item.seater .seater-svg {
        width: 28px;
        height: 28px;
      }
      .seat-cell-item.sleeper {
        width: 36px;
        min-height: 72px;
      }
      .seat-cell-item.sleeper .sleeper-svg {
        width: 28px;
        height: 60px;
      }
      .legend-icon-box {
        width: 30px;
        height: 30px;
      }
      .seat-row-track {
        margin-bottom: 8px;
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

  currentStage = 1; // 1: Select Seats, 2: Passenger Details, 3: Payment
  legendExpanded = true;
  pointsExpanded = true;
  activeDeckView: 'ALL' | 'LOWER' | 'UPPER' = 'ALL';

  passengers: PassengerInfo[] = [];
  contactEmail = '';
  contactPhone = '';

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
      if (!this.contactPhone) this.contactPhone = this.currentUser.phone || '9876543210';
    }
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

    this.passengers = this.selectedSeats.map(seatNo => {
      if (oldMap.has(seatNo)) {
        return oldMap.get(seatNo)!;
      }
      return {
        seatNumber: seatNo,
        name: this.currentUser?.fullName && this.passengers.length === 0 ? this.currentUser.fullName : '',
        age: null,
        gender: 'MALE'
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

  get busThumbnail(): string {
    return 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=300&q=80';
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
    if (!rawDate) return 'Wed, 22 Oct';
    try {
      const d = new Date(rawDate);
      return d.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric', month: 'short' });
    } catch {
      return rawDate;
    }
  }

  get arrivalDateFormatted(): string {
    const rawDate = this.layout?.travelDate || this.schedule?.travelDate || this.travelDate;
    if (!rawDate) return 'Thu, 23 Oct';
    try {
      const d = new Date(rawDate);
      if (this.isOvernight) {
        d.setDate(d.getDate() + 1);
      }
      return d.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric', month: 'short' });
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
    if (deck.deck === 'UPPER') {
      return `UPPER BERTH(${deck.availableCount})`;
    } else if (this.isDoubleDeck) {
      return `LOWER BERTH(${deck.availableCount})`;
    } else {
      return `SEATS(${deck.availableCount})`;
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
    this.seatToggled.emit(seatNumber);
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
    if (this.passengers.length === 0) return false;
    for (const p of this.passengers) {
      if (!p.name || p.name.trim().length < 2) return false;
      if (!p.age || p.age < 1 || p.age > 120) return false;
    }
    if (!this.contactEmail || !this.contactEmail.includes('@')) return false;
    if (!this.contactPhone || this.contactPhone.length < 7) return false;
    return true;
  }

  onContinueToPassengerDetails() {
    if (!this.canProceedToPassengerDetails) return;
    this.syncPassengers();
    this.currentStage = 2;
  }

  onProceedToPaymentStage() {
    if (!this.isPassengerFormValid) return;
    this.currentStage = 3;
    
    // Construct rich booking payload for payment checkout
    const checkoutPayload = {
      schedule: this.schedule,
      selectedSeats: [...this.selectedSeats],
      boardingPoint: this.selectedBoardingPoint,
      droppingPoint: this.selectedDroppingPoint,
      passengers: this.passengers,
      contactEmail: this.contactEmail,
      contactPhone: this.contactPhone,
      baseFare: this.calculatedBaseFare,
      taxes: this.calculatedTaxes,
      totalAmount: this.calculatedTotalAmount
    };

    this.proceedToPayment.emit(checkoutPayload);
  }
}
