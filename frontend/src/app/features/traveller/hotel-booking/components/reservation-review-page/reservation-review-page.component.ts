import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription, take } from 'rxjs';
import {
  CountryDialCodeOption,
  GuestFormEntry,
  GuestGender,
  HotelStaySummary,
  PhysicalRoomAllocation,
  PrimaryGuestDtoPayload,
  PrimaryGuestFormState,
  PrimaryGuestValidationErrors,
  ReservationReviewJourneyState,
  ReservationReviewSummaryMetrics
} from '../../../../../models/hotel.model';
import { User } from '../../../../../models/user.model';
import { AuthService } from '../../../../../services/auth.service';
import { TravellerService } from '../../../../../services/traveller.service';
import { DEFAULT_HOTEL_PLACEHOLDER, resolveImageUrl } from '../../../../../shared/utils/image-url.util';
import {
  buildPhysicalRoomsFromStaySummary,
  formatGenderLabel,
  formatStayDisplayDate,
  isSameStayContextSignature,
  loadGuestAllocationState,
  loadStaySummaryFromSession,
  saveStaySummaryToSession
} from '../../../../../shared/utils/guest-allocation.util';
import {
  COUNTRY_DIAL_CODE_OPTIONS,
  DEFAULT_COUNTRY_DIAL_CODE,
  computeReservationSummaryMetrics,
  createInitialPrimaryGuestForm,
  findCountryByDialCode,
  getRoomGuestDisplayRole,
  loadReservationReviewState,
  parsePhoneIntoDialCodeAndNumber,
  saveReservationReviewState,
  toPersistedRoomsSnapshot,
  validateGuestAllocationsForReview,
  validatePrimaryGuestForm
} from '../../../../../shared/utils/reservation-review.util';
import { ReservationSummaryPanelComponent } from '../reservation-summary-panel/reservation-summary-panel.component';

type PrimaryGuestField = 'fullName' | 'email' | 'dialCode' | 'nationalNumber';

@Component({
  selector: 'app-reservation-review-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ReservationSummaryPanelComponent],
  template: `
    <div class="reservation-review-page-container">

      <!-- Polite live region for post-blur screen reader error announcements -->
      <div class="visually-hidden" aria-live="polite" aria-atomic="true">{{ liveErrorAnnouncement }}</div>

      <!-- Page Header (WP-HB-UI-04 Mockup Hierarchy) -->
      <div class="mb-3">
        <h1 class="page-heading fw-bold text-navy mb-1">Reservation Review</h1>
        <p class="text-muted small mb-0">
          Confirm your details and review your hotel booking before proceeding to payment.
        </p>
      </div>

      <!-- Recoverable Guard State: Missing or Incomplete Prerequisite Booking State -->
      <div
        *ngIf="!hasValidPrerequisites"
        class="card border-0 shadow-sm rounded-4 p-4 p-md-5 text-center my-4 bg-white"
        data-testid="review-missing-state-card"
      >
        <div class="mb-3">
          <span class="d-inline-flex align-items-center justify-content-center rounded-circle bg-soft-teal text-teal" style="width: 60px; height: 60px;">
            <i class="fa-solid fa-clipboard-list fs-4" aria-hidden="true"></i>
          </span>
        </div>
        <h2 class="h5 fw-bold text-navy mb-2">Reservation Details Incomplete</h2>
        <p class="text-muted small mx-auto mb-4" style="max-width: 500px;">
          {{ prerequisiteErrorMessage }}
        </p>
        <div class="d-flex flex-wrap justify-content-center gap-2">
          <button
            type="button"
            class="btn btn-teal rounded-pill px-4 py-2 fw-semibold"
            (click)="onBackToGuestDetails()"
            data-testid="review-recover-cta"
          >
            <i class="fa-solid fa-arrow-left me-2" aria-hidden="true"></i>
            {{ recoverActionLabel }}
          </button>
        </div>
      </div>

      <!-- Main Reservation Review Workflow -->
      <ng-container *ngIf="hasValidPrerequisites && activeStaySummary">

        <!-- TOP: Horizontal Hotel & Stay Information Header Card -->
        <section class="hotel-review-header bg-white rounded-4 border shadow-sm p-3 mb-4" aria-label="Hotel and Stay Summary">
          <div class="row g-3 align-items-center">
            <div class="col-lg-6 col-xl-7 min-w-0">
              <div class="d-flex flex-column flex-sm-row align-items-sm-center gap-3 min-w-0">
                <img
                  [src]="resolvedHotelImage"
                  [alt]="activeStaySummary.hotelName"
                  class="hotel-thumb rounded-3 flex-shrink-0"
                  (error)="onHotelImgError($event)"
                />
                <div class="hotel-info-copy min-w-0 flex-grow-1 overflow-hidden">
                  <div class="d-flex align-items-center gap-2 flex-wrap mb-1">
                    <h2 class="h5 fw-bold text-navy mb-0 text-truncate" style="max-width: 100%;">
                      {{ activeStaySummary.hotelName }}
                    </h2>
                    <span
                      *ngIf="activeStaySummary.propertyType"
                      class="badge property-type-pill rounded-pill fw-semibold px-2 py-1 flex-shrink-0"
                    >
                      {{ activeStaySummary.propertyType }}
                    </span>
                  </div>
                  <div class="text-muted small d-flex align-items-center gap-1 min-w-0 w-100">
                    <i class="fa-solid fa-location-dot text-navy-muted flex-shrink-0" aria-hidden="true"></i>
                    <span class="hotel-address-truncate" [title]="hotelLocationLabel">{{ hotelLocationLabel }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="col-lg-6 col-xl-5">
              <div class="row g-3 stay-meta-grid">
                <div class="col-12 col-sm-4 meta-col-divider">
                  <div class="d-flex align-items-center gap-2">
                    <i class="fa-regular fa-calendar text-teal fs-6" aria-hidden="true"></i>
                    <div>
                      <div class="text-muted smaller">Check-in</div>
                      <div class="fw-bold text-navy small text-nowrap">{{ formattedCheckIn }}</div>
                    </div>
                  </div>
                </div>

                <div class="col-12 col-sm-4 meta-col-divider">
                  <div class="d-flex align-items-center gap-2">
                    <i class="fa-regular fa-calendar text-teal fs-6" aria-hidden="true"></i>
                    <div>
                      <div class="text-muted smaller">Check-out</div>
                      <div class="fw-bold text-navy small text-nowrap">{{ formattedCheckOut }}</div>
                    </div>
                  </div>
                </div>

                <div class="col-sm-4 col-12 meta-col-divider">
                  <div class="d-flex align-items-center gap-2">
                    <i class="fa-regular fa-moon text-teal fs-6" aria-hidden="true"></i>
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
        </section>

        <!-- Validation Summary Banner (only after submit attempt with errors) -->
        <div
          *ngIf="submissionErrorBanner"
          class="alert alert-danger rounded-4 border-0 shadow-sm px-4 py-3 mb-4 d-flex align-items-center justify-content-between flex-wrap gap-2"
          role="alert"
          data-testid="review-validation-banner"
        >
          <div class="d-flex align-items-center gap-2 small fw-semibold">
            <i class="fa-solid fa-triangle-exclamation fs-6" aria-hidden="true"></i>
            <span>{{ submissionErrorBanner }}</span>
          </div>
          <button
            *ngIf="!allocationCheckValid"
            type="button"
            class="btn btn-sm btn-outline-danger rounded-pill px-3 fw-semibold"
            (click)="onBackToGuestDetails()"
          >
            Return to Guest Details
          </button>
        </div>

        <!-- MAIN TWO-COLUMN CONTENT GRID -->
        <div class="row g-4 align-items-start">

          <!-- LEFT COLUMN: Primary Guest Contact + Read-Only Guest Allocation -->
          <div class="col-lg-7 col-xl-8">

            <!-- CARD 1: Primary Guest (Booking Contact) -->
            <section class="bg-white rounded-4 border shadow-sm p-4 mb-4" aria-labelledby="primary-guest-heading">
              <div class="mb-3">
                <div class="d-flex align-items-center gap-2 mb-1">
                  <i class="fa-regular fa-user text-teal fs-5" aria-hidden="true"></i>
                  <h2 id="primary-guest-heading" class="section-heading fw-bold text-navy mb-0">
                    Primary Guest (Booking Contact)
                  </h2>
                </div>
                <p class="text-muted small mb-0">
                  This person will receive booking communications and is the main contact for this reservation.
                </p>
              </div>

              <form
                [id]="primaryGuestFormId"
                (ngSubmit)="onProceedToPayment()"
                novalidate
                aria-labelledby="primary-guest-heading"
              >
                <div class="row g-3 align-items-start">

                  <!-- Full Name -->
                  <div class="col-12 col-md-6 col-xl-4">
                    <label for="pg-full-name" class="form-label small fw-bold text-navy mb-1">
                      Full Name <span class="text-danger" aria-hidden="true">*</span>
                    </label>
                    <input
                      id="pg-full-name"
                      name="fullName"
                      type="text"
                      class="form-control review-input"
                      [class.is-field-invalid]="shouldShowError('fullName')"
                      placeholder="Enter full name"
                      maxlength="255"
                      required
                      autocomplete="name"
                      [ngModel]="primaryGuestForm.fullName"
                      (ngModelChange)="onFieldInput('fullName', $event)"
                      (blur)="onFieldBlur('fullName')"
                      [attr.aria-invalid]="shouldShowError('fullName') ? 'true' : 'false'"
                      [attr.aria-describedby]="shouldShowError('fullName') ? 'pg-full-name-error' : null"
                      data-testid="primary-guest-fullname"
                    />
                    <div
                      *ngIf="shouldShowError('fullName')"
                      id="pg-full-name-error"
                      class="field-error-text mt-1"
                    >
                      <i class="fa-solid fa-circle-exclamation me-1" aria-hidden="true"></i>
                      <span>{{ validationErrors.fullName }}</span>
                    </div>
                  </div>

                  <!-- Email Address -->
                  <div class="col-12 col-md-6 col-xl-4">
                    <label for="pg-email" class="form-label small fw-bold text-navy mb-1">
                      Email Address <span class="text-danger" aria-hidden="true">*</span>
                    </label>
                    <input
                      id="pg-email"
                      name="email"
                      type="email"
                      class="form-control review-input"
                      [class.is-field-invalid]="shouldShowError('email')"
                      placeholder="name@example.com"
                      maxlength="255"
                      required
                      autocomplete="email"
                      [ngModel]="primaryGuestForm.email"
                      (ngModelChange)="onFieldInput('email', $event)"
                      (blur)="onFieldBlur('email')"
                      [attr.aria-invalid]="shouldShowError('email') ? 'true' : 'false'"
                      [attr.aria-describedby]="shouldShowError('email') ? 'pg-email-error' : null"
                      data-testid="primary-guest-email"
                    />
                    <div
                      *ngIf="shouldShowError('email')"
                      id="pg-email-error"
                      class="field-error-text mt-1"
                    >
                      <i class="fa-solid fa-circle-exclamation me-1" aria-hidden="true"></i>
                      <span>{{ validationErrors.email }}</span>
                    </div>
                  </div>

                  <!-- Mobile Number with Country Dial Code Selector -->
                  <div class="col-12 col-xl-4">
                    <label for="pg-mobile-number" class="form-label small fw-bold text-navy mb-1">
                      Mobile Number <span class="text-danger" aria-hidden="true">*</span>
                    </label>

                    <div
                      class="mobile-composite-control d-flex align-items-stretch rounded-3"
                      [class.is-field-invalid]="shouldShowError('dialCode') || shouldShowError('nationalNumber')"
                    >
                      <!-- Country Dial Selector -->
                      <div class="country-code-wrap position-relative d-inline-flex align-items-center gap-1 px-2 border-end flex-shrink-0">
                        <span class="country-flag-badge flex-shrink-0" aria-hidden="true" [ngSwitch]="selectedCountry.code">
                          <!-- India Flag -->
                          <svg *ngSwitchCase="'IN'" viewBox="0 0 24 16" width="20" height="14" class="flag-svg">
                            <rect width="24" height="5.33" fill="#FF9933" />
                            <rect y="5.33" width="24" height="5.34" fill="#FFFFFF" />
                            <rect y="10.67" width="24" height="5.33" fill="#138808" />
                            <circle cx="12" cy="8" r="1.8" fill="none" stroke="#000080" stroke-width="0.9" />
                          </svg>
                          <!-- US / Canada Flag -->
                          <svg *ngSwitchCase="'US'" viewBox="0 0 24 16" width="20" height="14" class="flag-svg">
                            <rect width="24" height="16" fill="#FFFFFF" />
                            <rect width="24" height="2.6" y="0" fill="#B22234" />
                            <rect width="24" height="2.6" y="5.2" fill="#B22234" />
                            <rect width="24" height="2.6" y="10.4" fill="#B22234" />
                            <rect width="24" height="2.6" y="13.4" fill="#B22234" />
                            <rect width="10.5" height="8.5" fill="#3C3B6E" />
                          </svg>
                          <!-- UK Flag -->
                          <svg *ngSwitchCase="'GB'" viewBox="0 0 24 16" width="20" height="14" class="flag-svg">
                            <rect width="24" height="16" fill="#012169" />
                            <path d="M0 0 L24 16 M24 0 L0 16" stroke="#FFFFFF" stroke-width="2.5" />
                            <path d="M12 0 V16 M0 8 H24" stroke="#FFFFFF" stroke-width="4.5" />
                            <path d="M12 0 V16 M0 8 H24" stroke="#C8102E" stroke-width="2.5" />
                          </svg>
                          <!-- Generic International Globe / Flag Badge -->
                          <svg *ngSwitchDefault viewBox="0 0 24 16" width="20" height="14" class="flag-svg">
                            <rect width="24" height="16" rx="2" fill="#E0F2FE" />
                            <text x="12" y="11" text-anchor="middle" font-size="8" font-weight="700" fill="#0369A1">
                              {{ selectedCountry.code }}
                            </text>
                          </svg>
                        </span>

                        <span class="dial-code-label fw-semibold text-navy small text-nowrap">{{ primaryGuestForm.dialCode }}</span>
                        <i class="fa-solid fa-chevron-down text-muted chevron-sm" aria-hidden="true"></i>

                        <select
                          id="pg-dial-code"
                          name="dialCode"
                          class="country-native-select"
                          aria-label="Country dialing code"
                          [ngModel]="primaryGuestForm.dialCode"
                          (ngModelChange)="onDialCodeChange($event)"
                          (blur)="onFieldBlur('dialCode')"
                          [attr.aria-invalid]="shouldShowError('dialCode') ? 'true' : 'false'"
                          data-testid="primary-guest-dial-code"
                        >
                          <option *ngFor="let c of countryOptions" [value]="c.dialCode">
                            {{ c.name }} ({{ c.dialCode }})
                          </option>
                        </select>
                      </div>

                      <!-- National Number Input -->
                      <input
                        id="pg-mobile-number"
                        name="nationalNumber"
                        type="tel"
                        inputmode="tel"
                        autocomplete="tel-national"
                        maxlength="20"
                        required
                        class="mobile-number-input flex-grow-1 border-0 bg-transparent px-2 px-sm-3 text-navy"
                        [placeholder]="selectedCountry.placeholder"
                        [ngModel]="primaryGuestForm.nationalNumber"
                        (ngModelChange)="onFieldInput('nationalNumber', $event)"
                        (blur)="onFieldBlur('nationalNumber')"
                        [attr.aria-invalid]="shouldShowError('nationalNumber') ? 'true' : 'false'"
                        [attr.aria-describedby]="shouldShowError('nationalNumber') ? 'pg-mobile-error' : null"
                        data-testid="primary-guest-mobile"
                      />
                    </div>

                    <div
                      *ngIf="shouldShowError('dialCode') || shouldShowError('nationalNumber')"
                      id="pg-mobile-error"
                      class="field-error-text mt-1"
                    >
                      <i class="fa-solid fa-circle-exclamation me-1" aria-hidden="true"></i>
                      <span>{{ validationErrors.dialCode || validationErrors.nationalNumber }}</span>
                    </div>
                  </div>

                </div>
              </form>
            </section>

            <!-- CARD 2: Guest Allocation (Strictly Read-Only Review) -->
            <section class="bg-white rounded-4 border shadow-sm p-4" aria-labelledby="guest-allocation-review-heading">
              <div class="mb-3">
                <div class="d-flex align-items-center gap-2 mb-1">
                  <i class="fa-solid fa-users text-teal fs-5" aria-hidden="true"></i>
                  <h2 id="guest-allocation-review-heading" class="section-heading fw-bold text-navy mb-0">
                    Guest Allocation
                  </h2>
                </div>
                <p class="text-muted small mb-0">
                  Review the guests assigned to each room.
                </p>
              </div>

              <div class="d-flex flex-column gap-3">
                <article
                  *ngFor="let room of physicalRooms; trackBy: trackByRoomKey"
                  class="allocation-review-room-card border rounded-3 overflow-hidden"
                  [attr.data-testid]="'review-room-card-' + room.roomSequenceOverall"
                >
                  <!-- Room Header Bar -->
                  <div class="allocation-room-header d-flex flex-wrap align-items-center justify-content-between gap-2 px-3 py-2">
                    <div class="d-flex align-items-center gap-2 min-w-0">
                      <i class="fa-solid fa-bed text-teal flex-shrink-0" aria-hidden="true"></i>
                      <h3 class="h6 fw-bold text-navy mb-0 text-truncate">
                        Room {{ room.roomSequenceOverall }} &ndash; {{ room.roomTypeName }}
                      </h3>
                    </div>

                    <span class="guest-count-pill px-3 py-1 rounded-pill fw-semibold smaller flex-shrink-0">
                      {{ room.occupants.length }} {{ room.occupants.length === 1 ? 'Guest' : 'Guests' }}
                    </span>
                  </div>

                  <!-- Occupants Read-Only List -->
                  <div class="px-3 py-1 bg-white">
                    <div
                      *ngFor="let guest of room.occupants; let idx = index; let first = first; trackBy: trackByGuestId"
                      class="occupant-review-row d-flex flex-column flex-sm-row align-items-sm-center gap-1 gap-sm-3 py-2"
                      [class.border-top]="!first"
                      [attr.data-testid]="'review-guest-row-' + room.roomSequenceOverall + '-' + (idx + 1)"
                    >
                      <div class="occupant-role-label d-inline-flex align-items-center gap-2 fw-bold text-navy small flex-shrink-0">
                        <i
                          class="fa-regular fa-user"
                          [class.text-navy]="guest.type === 'ADULT'"
                          [class.text-teal]="guest.type === 'CHILD'"
                          aria-hidden="true"
                        ></i>
                        <span>{{ getGuestDisplayRole(room, idx) }}</span>
                      </div>

                      <div class="occupant-meta-strip d-flex flex-wrap align-items-center gap-2 small text-navy min-w-0">
                        <span class="fw-medium">{{ guest.name }}</span>
                        <span class="detail-sep text-muted" aria-hidden="true">|</span>
                        <span class="text-secondary">Age: <strong class="text-navy fw-medium">{{ guest.age }}</strong></span>
                        <ng-container *ngIf="guest.type === 'ADULT' && guest.gender">
                          <span class="detail-sep text-muted" aria-hidden="true">|</span>
                          <span class="text-secondary">Gender: <strong class="text-navy fw-medium">{{ formatGuestGender(guest.gender) }}</strong></span>
                        </ng-container>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
            </section>

          </div>

          <!-- RIGHT COLUMN: Sticky Reservation Summary -->
          <div class="col-lg-5 col-xl-4">
            <app-reservation-summary-panel
              [metrics]="summaryMetrics"
              [formattedCheckIn]="formattedCheckIn"
              [formattedCheckOut]="formattedCheckOut"
              [totalNights]="activeStaySummary.totalNights"
            ></app-reservation-summary-panel>
          </div>

        </div>

        <!-- FOOTER: Navigation Actions -->
        <div class="review-footer d-flex flex-wrap align-items-center justify-content-between gap-3 pt-4 pb-4">
          <button
            type="button"
            class="btn btn-back-guest rounded-3 px-4 py-2 fw-semibold d-inline-flex align-items-center gap-2"
            (click)="onBackToGuestDetails()"
            [disabled]="isSubmitting"
            data-testid="back-to-guest-details"
          >
            <i class="fa-solid fa-arrow-left small" aria-hidden="true"></i>
            <span>Back to Guest Details</span>
          </button>

          <button
            type="submit"
            [attr.form]="primaryGuestFormId"
            class="btn btn-proceed-payment rounded-3 px-4 py-2 fw-semibold d-inline-flex align-items-center gap-2"
            [disabled]="isSubmitting"
            data-testid="proceed-to-payment"
          >
            <span *ngIf="isSubmitting" class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
            <span>{{ isSubmitting ? 'Preparing Payment...' : 'Proceed to Payment' }}</span>
            <i *ngIf="!isSubmitting" class="fa-solid fa-arrow-right small" aria-hidden="true"></i>
          </button>
        </div>

      </ng-container>

    </div>
  `,
  styles: [`
    :host {
      display: block;
      min-width: 0;
      max-width: 100%;
    }
    .reservation-review-page-container {
      min-width: 0;
      max-width: 100%;
    }
    .min-w-0 {
      min-width: 0 !important;
    }
    @media (min-width: 576px) {
      .hotel-info-copy {
        flex: 1 1 0%;
        width: 0;
      }
    }
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
    .page-heading {
      font-size: 1.75rem;
      line-height: 1.25;
    }
    .section-heading {
      font-size: 1.12rem;
    }
    .hotel-thumb {
      width: 158px;
      height: 86px;
      object-fit: cover;
      background-color: #E2E8F0;
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
    .review-input {
      height: 42px;
      border: 1px solid #CBD5E1;
      border-radius: 0.5rem;
      font-size: 0.9rem;
      color: #0F172A;
      background-color: #FFFFFF;
    }
    .review-input:focus-visible {
      outline: 2px solid #0891B2;
      outline-offset: 1px;
      border-color: #0891B2;
      box-shadow: none;
    }
    .mobile-composite-control {
      height: 42px;
      border: 1px solid #CBD5E1;
      background-color: #FFFFFF;
      width: 100%;
      min-width: 0;
      overflow: hidden;
    }
    .mobile-composite-control:has(input:focus-visible),
    .mobile-composite-control:has(select:focus-visible) {
      outline: 2px solid #0891B2;
      outline-offset: 1px;
      border-color: #0891B2;
    }
    .is-field-invalid,
    .mobile-composite-control.is-field-invalid {
      border-color: #DC2626 !important;
      background-color: #FEF2F2;
    }
    .country-code-wrap {
      background-color: #F8FAFC;
      border-color: #E2E8F0 !important;
      user-select: none;
    }
    .country-code-wrap:hover {
      background-color: #F1F5F9;
    }
    .flag-svg {
      display: block;
      border-radius: 2px;
      box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.12);
    }
    .chevron-sm {
      font-size: 0.65rem;
    }
    .country-native-select {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      opacity: 0;
      cursor: pointer;
    }
    .mobile-number-input {
      min-width: 0;
      width: 100%;
      font-size: 0.9rem;
      outline: none;
    }
    .field-error-text {
      color: #B91C1C;
      font-size: 0.78rem;
      font-weight: 500;
      display: flex;
      align-items: flex-start;
      gap: 0.25rem;
      line-height: 1.3;
    }
    .field-error-text i {
      margin-top: 0.15rem;
      flex-shrink: 0;
    }
    .allocation-review-room-card {
      border-color: #E2E8F0 !important;
    }
    .allocation-room-header {
      background-color: #EEF6FF;
      border-bottom: 1px solid #DBEAFE;
    }
    .guest-count-pill {
      background-color: #DCFCE7;
      color: #166534;
    }
    .occupant-role-label {
      min-width: 105px;
    }
    .detail-sep {
      opacity: 0.45;
    }
    .btn-back-guest {
      background-color: #FFFFFF;
      color: #0F172A;
      border: 1px solid #CBD5E1;
    }
    .btn-back-guest:hover:not(:disabled) {
      background-color: #F8FAFC;
      border-color: #94A3B8;
    }
    .btn-proceed-payment {
      background-color: #0891B2;
      border: 1px solid #0891B2;
      color: #FFFFFF;
    }
    .btn-proceed-payment:hover:not(:disabled) {
      background-color: #0E7490;
      border-color: #0E7490;
    }
    .btn-proceed-payment:disabled {
      opacity: 0.75;
      cursor: not-allowed;
    }
    .visually-hidden {
      position: absolute !important;
      clip-path: inset(50%) !important;
      overflow: hidden !important;
      width: 1px !important;
      height: 1px !important;
      margin: -1px !important;
      padding: 0 !important;
      border: 0 !important;
      white-space: nowrap !important;
    }
  `]
})
export class ReservationReviewPageComponent implements OnInit, OnChanges, OnDestroy {
  @Input() staySummary: HotelStaySummary | null = null;
  @Input() allocatedRooms: PhysicalRoomAllocation[] = [];
  @Input() requestedHotelId: number | null = null;
  @Input() cachedProfileData: any = null;

  @Output() backToGuestDetails = new EventEmitter<void>();
  @Output() proceedToPayment = new EventEmitter<{
    staySummary: HotelStaySummary;
    rooms: PhysicalRoomAllocation[];
    primaryGuest: PrimaryGuestDtoPayload;
    summaryMetrics: ReservationReviewSummaryMetrics;
  }>();

  readonly primaryGuestFormId = 'primary-guest-contact-form';
  readonly countryOptions = COUNTRY_DIAL_CODE_OPTIONS;

  activeStaySummary: HotelStaySummary | null = null;
  physicalRooms: PhysicalRoomAllocation[] = [];
  summaryMetrics: ReservationReviewSummaryMetrics = {
    roomTypeRows: [],
    estimatedTotalAmount: 0,
    pricingConsistent: false,
    totalRooms: 0,
    totalAdults: 0,
    totalChildren: 0,
    totalGuests: 0
  };

  primaryGuestForm: PrimaryGuestFormState = {
    fullName: '',
    email: '',
    dialCode: DEFAULT_COUNTRY_DIAL_CODE,
    nationalNumber: ''
  };

  validationErrors: PrimaryGuestValidationErrors = {};
  touchedFields = new Set<PrimaryGuestField>();
  dirtyFields = new Set<PrimaryGuestField>();
  attemptedSubmit = false;
  isSubmitting = false;

  hasValidPrerequisites = false;
  allocationCheckValid = true;
  prerequisiteErrorMessage = 'Please select your rooms and assign guest details before reviewing your reservation.';
  recoverActionLabel = 'Return to Guest Details';
  submissionErrorBanner = '';
  liveErrorAnnouncement = '';

  private profileFetchSub: Subscription | null = null;
  private liveAnnounceTimer: ReturnType<typeof setTimeout> | null = null;
  private profileFetchAttempted = false;

  constructor(
    private authService: AuthService,
    private travellerService: TravellerService
  ) {}

  ngOnInit(): void {
    this.initializeReviewState();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (
      changes['staySummary'] ||
      changes['allocatedRooms'] ||
      changes['requestedHotelId'] ||
      changes['cachedProfileData']
    ) {
      this.initializeReviewState();
    }
  }

  ngOnDestroy(): void {
    this.profileFetchSub?.unsubscribe();
    if (this.liveAnnounceTimer) {
      clearTimeout(this.liveAnnounceTimer);
      this.liveAnnounceTimer = null;
    }
  }

  get selectedCountry(): CountryDialCodeOption {
    return findCountryByDialCode(this.primaryGuestForm.dialCode);
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

  trackByGuestId(_index: number, guest: GuestFormEntry): string {
    return guest.id;
  }

  getGuestDisplayRole(room: PhysicalRoomAllocation, index: number): string {
    return getRoomGuestDisplayRole(room, index);
  }

  formatGuestGender(gender: GuestGender | null | undefined): string {
    return formatGenderLabel(gender);
  }

  onHotelImgError(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (img && img.src !== DEFAULT_HOTEL_PLACEHOLDER) {
      img.src = DEFAULT_HOTEL_PLACEHOLDER;
    }
  }

  onFieldInput(field: 'fullName' | 'email' | 'nationalNumber', value: string): void {
    this.dirtyFields.add(field);
    this.primaryGuestForm = {
      ...this.primaryGuestForm,
      [field]: value ?? ''
    };
    this.recomputePrimaryGuestValidation();
    this.persistCurrentReviewState();

    // Clear live announcement once a field is corrected
    if (!this.validationErrors[field]) {
      this.clearLiveAnnouncement();
    }
  }

  onDialCodeChange(value: string): void {
    this.dirtyFields.add('dialCode');
    this.touchedFields.add('dialCode');
    this.primaryGuestForm = {
      ...this.primaryGuestForm,
      dialCode: value || DEFAULT_COUNTRY_DIAL_CODE
    };
    if (this.primaryGuestForm.nationalNumber.trim().length > 0) {
      this.touchedFields.add('nationalNumber');
    }
    this.recomputePrimaryGuestValidation();
    this.persistCurrentReviewState();
  }

  onFieldBlur(field: PrimaryGuestField): void {
    this.touchedFields.add(field);
    this.recomputePrimaryGuestValidation();
    const errorMsg = this.validationErrors[field];
    if (errorMsg) {
      this.scheduleLiveAnnouncement(this.buildSpokenFieldError(field, errorMsg));
    }
  }

  shouldShowError(field: PrimaryGuestField): boolean {
    if (!this.validationErrors[field]) {
      return false;
    }
    return this.attemptedSubmit || this.touchedFields.has(field);
  }

  onBackToGuestDetails(): void {
    this.persistCurrentReviewState();
    this.backToGuestDetails.emit();
  }

  onProceedToPayment(): void {
    if (this.isSubmitting) {
      return;
    }

    this.attemptedSubmit = true;
    this.touchedFields.add('fullName');
    this.touchedFields.add('email');
    this.touchedFields.add('dialCode');
    this.touchedFields.add('nationalNumber');

    const allocationCheck = validateGuestAllocationsForReview(this.activeStaySummary, this.physicalRooms);
    this.allocationCheckValid = allocationCheck.valid;

    const guestResult = validatePrimaryGuestForm(this.primaryGuestForm);
    this.validationErrors = guestResult.errors;

    if (!allocationCheck.valid) {
      this.submissionErrorBanner =
        allocationCheck.reason || 'Guest allocation details are incomplete. Please return to Guest Details.';
      this.scheduleLiveAnnouncement(this.submissionErrorBanner);
      return;
    }

    if (!guestResult.valid || !guestResult.payload) {
      this.submissionErrorBanner =
        'Please correct the highlighted Primary Guest contact details before proceeding to payment.';
      this.focusFirstInvalidField();
      return;
    }

    this.submissionErrorBanner = '';
    this.clearLiveAnnouncement();

    if (!this.activeStaySummary) {
      return;
    }

    this.isSubmitting = true;
    saveStaySummaryToSession(this.activeStaySummary);
    this.persistCurrentReviewState(guestResult.payload);

    try {
      this.proceedToPayment.emit({
        staySummary: this.activeStaySummary,
        rooms: this.physicalRooms,
        primaryGuest: guestResult.payload,
        summaryMetrics: this.summaryMetrics
      });
    } finally {
      this.isSubmitting = false;
    }
  }

  private initializeReviewState(): void {
    this.isSubmitting = false;
    this.attemptedSubmit = false;
    this.submissionErrorBanner = '';
    this.touchedFields.clear();
    this.dirtyFields.clear();

    const currentUser = this.authService.currentUserValue;
    const savedAllocation = loadGuestAllocationState();
    const storedSummary = loadStaySummaryFromSession();

    const resolvedSummary =
      this.staySummary ||
      savedAllocation?.staySummary ||
      storedSummary;

    if (
      !resolvedSummary ||
      !Array.isArray(resolvedSummary.selectedItems) ||
      resolvedSummary.selectedItems.length === 0 ||
      (this.requestedHotelId && resolvedSummary.hotelId !== this.requestedHotelId)
    ) {
      this.hasValidPrerequisites = false;
      this.allocationCheckValid = false;
      this.activeStaySummary = null;
      this.physicalRooms = [];
      this.prerequisiteErrorMessage =
        'Your hotel stay and room selection were not found or may have expired. Please choose your rooms again.';
      this.recoverActionLabel = this.requestedHotelId ? 'Return to Room Selection' : 'Back to Search Hotels';
      return;
    }

    let resolvedRooms: PhysicalRoomAllocation[] = [];
    if (this.allocatedRooms && this.allocatedRooms.length > 0) {
      resolvedRooms = this.allocatedRooms;
    } else if (savedAllocation && isSameStayContextSignature(savedAllocation.staySummary, resolvedSummary)) {
      resolvedRooms = buildPhysicalRoomsFromStaySummary(
        resolvedSummary,
        savedAllocation.rooms,
        savedAllocation.activeExpandedRoomKey
      );
    }

    const allocationCheck = validateGuestAllocationsForReview(resolvedSummary, resolvedRooms);
    if (!allocationCheck.valid) {
      this.hasValidPrerequisites = false;
      this.allocationCheckValid = false;
      this.activeStaySummary = resolvedSummary;
      this.physicalRooms = resolvedRooms;
      this.prerequisiteErrorMessage =
        allocationCheck.reason ||
        'Please complete all room-wise guest details before proceeding to reservation review.';
      this.recoverActionLabel = 'Back to Guest Details';
      return;
    }

    this.hasValidPrerequisites = true;
    this.allocationCheckValid = true;
    this.activeStaySummary = resolvedSummary;
    this.physicalRooms = resolvedRooms;
    this.summaryMetrics = computeReservationSummaryMetrics(resolvedSummary, resolvedRooms);

    this.restoreOrPrefillPrimaryGuest(resolvedSummary, currentUser);
    this.recomputePrimaryGuestValidation();
    this.persistCurrentReviewState();
  }

  private restoreOrPrefillPrimaryGuest(
    staySummary: HotelStaySummary,
    currentUser: User | null
  ): void {
    const savedReview = loadReservationReviewState(
      staySummary,
      undefined,
      currentUser?.id ?? null
    );

    if (savedReview && savedReview.primaryGuest) {
      this.primaryGuestForm = {
        fullName: savedReview.primaryGuest.fullName || '',
        email: savedReview.primaryGuest.email || '',
        dialCode: savedReview.primaryGuest.dialCode || DEFAULT_COUNTRY_DIAL_CODE,
        nationalNumber: savedReview.primaryGuest.nationalNumber || ''
      };
      return;
    }

    const initialForm = createInitialPrimaryGuestForm({
      userFullName: currentUser?.fullName || this.cachedProfileData?.fullName,
      userEmail: currentUser?.email || this.cachedProfileData?.email,
      userPhone: currentUser?.phone || this.cachedProfileData?.phone
    });
    this.primaryGuestForm = initialForm;

    const needsProfileFallback =
      !this.profileFetchAttempted &&
      !!currentUser?.token &&
      (!initialForm.fullName || !initialForm.email);

    if (needsProfileFallback) {
      this.profileFetchAttempted = true;
      this.profileFetchSub?.unsubscribe();
      this.profileFetchSub = this.travellerService
        .getProfile()
        .pipe(take(1))
        .subscribe({
          next: (profile) => {
            if (!profile) return;
            const nextForm = { ...this.primaryGuestForm };
            let updated = false;

            if (!this.dirtyFields.has('fullName') && !nextForm.fullName && profile.fullName) {
              nextForm.fullName = String(profile.fullName).trim();
              updated = true;
            }
            if (!this.dirtyFields.has('email') && !nextForm.email && profile.email) {
              nextForm.email = String(profile.email).trim();
              updated = true;
            }
            if (
              !this.dirtyFields.has('nationalNumber') &&
              !this.dirtyFields.has('dialCode') &&
              !nextForm.nationalNumber &&
              profile.phone
            ) {
              const parsed = parsePhoneIntoDialCodeAndNumber(profile.phone);
              if (parsed.nationalNumber) {
                nextForm.dialCode = parsed.dialCode;
                nextForm.nationalNumber = parsed.nationalNumber;
                updated = true;
              }
            }

            if (updated) {
              this.primaryGuestForm = nextForm;
              this.recomputePrimaryGuestValidation();
              this.persistCurrentReviewState();
            }
          },
          error: () => {
            // Silently keep editable primary guest form if profile lookup is unavailable
          }
        });
    }
  }

  private recomputePrimaryGuestValidation(): void {
    const result = validatePrimaryGuestForm(this.primaryGuestForm);
    this.validationErrors = result.errors;
    if (result.valid && this.allocationCheckValid) {
      this.submissionErrorBanner = '';
    }
  }

  private persistCurrentReviewState(explicitPayload?: PrimaryGuestDtoPayload): void {
    if (!this.activeStaySummary || this.physicalRooms.length === 0) {
      return;
    }
    const validation = validatePrimaryGuestForm(this.primaryGuestForm);
    const payload: PrimaryGuestDtoPayload =
      explicitPayload ||
      validation.payload || {
        fullName: this.primaryGuestForm.fullName.trim(),
        email: this.primaryGuestForm.email.trim(),
        mobile: `${this.primaryGuestForm.dialCode} ${this.primaryGuestForm.nationalNumber.replace(/\D/g, '')}`.trim()
      };

    const state: ReservationReviewJourneyState = {
      userId: this.authService.currentUserValue?.id ?? null,
      staySummary: this.activeStaySummary,
      rooms: toPersistedRoomsSnapshot(this.physicalRooms),
      primaryGuest: { ...this.primaryGuestForm },
      primaryGuestPayload: payload,
      summaryMetrics: this.summaryMetrics,
      updatedAt: new Date().toISOString()
    };
    saveReservationReviewState(state);
  }

  private focusFirstInvalidField(): void {
    const firstFieldId = this.validationErrors.fullName
      ? 'pg-full-name'
      : this.validationErrors.email
      ? 'pg-email'
      : this.validationErrors.dialCode
      ? 'pg-dial-code'
      : 'pg-mobile-number';

    setTimeout(() => {
      if (typeof document === 'undefined') return;
      const el = document.getElementById(firstFieldId) as HTMLElement | null;
      if (el) {
        if (typeof el.scrollIntoView === 'function') {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
        el.focus();
      }
    }, 30);
  }

  private buildSpokenFieldError(field: PrimaryGuestField, message: string): string {
    const labels: Record<PrimaryGuestField, string> = {
      fullName: 'Full Name',
      email: 'Email Address',
      dialCode: 'Country Code',
      nationalNumber: 'Mobile Number'
    };
    return `${labels[field]}: ${message}`;
  }

  private scheduleLiveAnnouncement(message: string): void {
    this.clearLiveAnnouncement();
    this.liveAnnounceTimer = setTimeout(() => {
      this.liveErrorAnnouncement = message;
    }, 350);
  }

  private clearLiveAnnouncement(): void {
    if (this.liveAnnounceTimer) {
      clearTimeout(this.liveAnnounceTimer);
      this.liveAnnounceTimer = null;
    }
    this.liveErrorAnnouncement = '';
  }
}
