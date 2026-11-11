import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  OnInit,
  Optional,
  Output,
  SimpleChanges
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin, of, Subscription, interval } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  HotelActiveReservation,
  HotelDetailsResponseDto,
  HotelPaymentUiState,
  HotelReservationInitiationResponse,
  HotelStaySummary,
  PaymentConfigResponse,
  PaymentCreateOrderResponse,
  PaymentTransactionDto,
  PaymentVerificationResponse,
  PhysicalRoomAllocation,
  PrimaryGuestDtoPayload
} from '../../../../../models/hotel.model';
import { User } from '../../../../../models/user.model';
import { BookingService } from '../../../../../services/booking.service';
import { HotelService } from '../../../../../services/hotel.service';
import {
  clearGuestAllocationState,
  formatStayDisplayDate
} from '../../../../../shared/utils/guest-allocation.util';
import { DEFAULT_HOTEL_PLACEHOLDER } from '../../../../../shared/utils/image-url.util';
import { clearReservationReviewState } from '../../../../../shared/utils/reservation-review.util';
import {
  buildAllocationsSignature,
  buildConfirmedHotelDisplayInfo,
  buildHotelReservationRequest,
  buildStayContextSignature,
  clearHotelPaymentCheckoutState,
  computeRemainingSeconds,
  ConfirmedHotelDisplayInfo,
  ConfirmedStayBreakdown,
  extractApiErrorMessage,
  extractConfirmedStayBreakdown,
  formatAccessibleCountdown,
  formatAuthoritativeAmount,
  formatCountdownMmSs,
  fromDetailResponse,
  fromInitiationResponse,
  generateHotelBookingRef,
  loadHotelPaymentCheckoutState,
  parseBackendExpiresAtMs,
  reconcileCheckoutState,
  saveHotelPaymentCheckoutState
} from '../../../../../shared/utils/hotel-payment.util';

@Component({
  selector: 'app-hotel-payment-checkout-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="hotel-payment-page-container" data-testid="hotel-payment-checkout-page">
      <!-- Accessible polite live region for state & milestone announcements (never per-second timer spam) -->
      <div class="visually-hidden" aria-live="polite" aria-atomic="true">
        {{ politeAnnouncement }}
      </div>

      <!-- Top Banner matching WP-HB-UI-05 approved mockup -->
      <section
        class="payment-hero-banner mb-4"
        aria-labelledby="payment-hero-heading"
        data-testid="payment-hero-banner"
      >
        <div class="payment-hero-content">
          <h1 id="payment-hero-heading" class="hero-title fw-bold text-navy mb-1">
            {{ bannerTitle }}
          </h1>
          <p class="hero-subtitle text-slate mb-0">
            {{ bannerSubtitle }}
          </p>
        </div>
      </section>

      <!-- Missing Prerequisites Guard State (No active reservation & no Stage 4 submission) -->
      <section
        *ngIf="missingPrerequisites"
        class="checkout-surface-card p-4 p-md-5 text-center"
        data-testid="payment-missing-prerequisites-card"
        aria-label="Reservation session unavailable"
      >
        <div class="state-icon-circle bg-soft-amber text-amber mx-auto mb-3">
          <i class="fa-solid fa-circle-exclamation fs-4" aria-hidden="true"></i>
        </div>
        <h2 class="h5 fw-bold text-navy mb-2">No Active Hotel Reservation Found</h2>
        <p class="text-muted small mx-auto mb-4" style="max-width: 520px;">
          Your checkout session could not be restored. Please return to hotel search or check your bookings list if you already completed payment.
        </p>
        <div class="d-flex flex-column flex-sm-row justify-content-center gap-3">
          <button
            type="button"
            class="btn-outline-action"
            (click)="onViewMyBookingsClick()"
            data-testid="missing-view-bookings-btn"
          >
            View My Bookings
          </button>
          <button
            type="button"
            class="btn-primary-teal"
            (click)="onBackToSearchClick()"
            data-testid="missing-back-to-search-btn"
          >
            Back to Search Hotels
          </button>
        </div>
      </section>

      <!-- Initiation Loading State -->
      <section
        *ngIf="!missingPrerequisites && uiState === 'INITIATING'"
        class="checkout-surface-card p-4 p-md-5 text-center"
        data-testid="payment-initiating-card"
        aria-label="Creating hotel reservation"
      >
        <div class="spinner-border text-teal mb-3" role="status" style="width: 2.75rem; height: 2.75rem;">
          <span class="visually-hidden">Creating your hotel reservation</span>
        </div>
        <h2 class="h5 fw-bold text-navy mb-2">Creating Your Reservation</h2>
        <p class="text-muted small mb-0">
          Securing your selected rooms and calculating authoritative stay pricing...
        </p>
      </section>

      <!-- Initiation Failure State (Retryable / Back to Review) -->
      <section
        *ngIf="!missingPrerequisites && uiState === 'INITIATION_FAILED'"
        class="checkout-surface-card p-4 p-md-5"
        data-testid="payment-initiation-failed-card"
        aria-label="Reservation initiation failed"
      >
        <div class="d-flex align-items-start gap-3 mb-4">
          <div class="state-icon-square bg-soft-red text-danger flex-shrink-0">
            <i class="fa-solid fa-triangle-exclamation fs-5" aria-hidden="true"></i>
          </div>
          <div>
            <h2 class="h5 fw-bold text-navy mb-1">Unable to Create Reservation</h2>
            <p class="text-muted small mb-0" data-testid="initiation-error-message">
              {{ errorBannerMessage || 'We could not lock inventory for this reservation.' }}
            </p>
          </div>
        </div>
        <div class="action-bar-row">
          <button
            type="button"
            class="btn-outline-action"
            (click)="onBackToReviewClick()"
            data-testid="init-failed-back-review-btn"
          >
            <i class="fa-solid fa-arrow-left" aria-hidden="true"></i>
            <span>Back to Review</span>
          </button>
          <button
            type="button"
            class="btn-primary-teal"
            (click)="retryInitiation()"
            data-testid="retry-initiation-btn"
          >
            <i class="fa-solid fa-rotate-right" aria-hidden="true"></i>
            <span>Retry Reservation</span>
          </button>
        </div>
      </section>

      <!-- MAIN MOCKUP CHECKOUT SURFACE: State 1 (Pending), State 2 (Processing), State 4 (Retryable Payment Failed) -->
      <section
        *ngIf="!missingPrerequisites && reservation && (uiState === 'PENDING_PAYMENT' || uiState === 'PROCESSING' || uiState === 'PAYMENT_FAILED')"
        class="checkout-surface-card p-3 p-md-4"
        data-testid="payment-active-checkout-card"
        aria-label="Hotel reservation payment details"
      >
        <!-- Inline Dismissible Notice / Payment Failure Alert when applicable -->
        <div
          *ngIf="errorBannerMessage"
          class="checkout-alert mb-4"
          [class.checkout-alert-danger]="uiState === 'PAYMENT_FAILED'"
          [class.checkout-alert-warning]="uiState !== 'PAYMENT_FAILED'"
          role="alert"
          data-testid="payment-inline-alert"
        >
          <div class="d-flex align-items-start gap-2">
            <i
              class="fa-solid mt-1 flex-shrink-0"
              [class.fa-circle-xmark]="uiState === 'PAYMENT_FAILED'"
              [class.fa-circle-info]="uiState !== 'PAYMENT_FAILED'"
              aria-hidden="true"
            ></i>
            <div class="small fw-medium">{{ errorBannerMessage }}</div>
          </div>
        </div>

        <!-- Section 1: Reservation Created (Strict Mockup Fidelity) -->
        <div class="section-block mb-4">
          <div class="d-flex align-items-center gap-3 mb-3">
            <div class="badge-icon-circle bg-soft-teal text-teal flex-shrink-0">
              <i class="fa-solid fa-circle-check fs-5" aria-hidden="true"></i>
            </div>
            <div>
              <h2 class="section-heading fw-bold text-navy mb-0">Reservation Created</h2>
              <p class="section-subtext text-muted mb-0">
                Your rooms are temporarily reserved and will be released if the payment is not completed in time.
              </p>
            </div>
          </div>

          <div class="meta-strip-box">
            <div class="meta-strip-grid">
              <!-- Booking Reference -->
              <div class="meta-cell">
                <div class="meta-icon-wrap text-teal">
                  <i class="fa-regular fa-calendar" aria-hidden="true"></i>
                </div>
                <div class="min-w-0">
                  <div class="meta-label">Booking Reference</div>
                  <div class="meta-value text-navy text-break" data-testid="booking-ref-value">
                    {{ reservation.bookingRef }}
                  </div>
                </div>
              </div>

              <!-- Payment Expires In -->
              <div class="meta-cell meta-cell-divider">
                <div class="meta-icon-wrap text-teal">
                  <i class="fa-regular fa-hourglass-half" aria-hidden="true"></i>
                </div>
                <div class="min-w-0">
                  <div class="meta-label">Payment Expires In</div>
                  <div>
                    <time
                      class="countdown-value tabular-nums"
                      [attr.datetime]="expiresAtIsoAttr"
                      [attr.aria-label]="accessibleCountdownText"
                      data-testid="payment-countdown"
                    >
                      {{ countdownDisplay }}
                    </time>
                  </div>
                </div>
              </div>

              <!-- Reservation Status -->
              <div class="meta-cell meta-cell-divider">
                <div class="meta-icon-wrap text-teal">
                  <i class="fa-solid fa-lock" aria-hidden="true"></i>
                </div>
                <div class="min-w-0">
                  <div class="meta-label">Reservation Status</div>
                  <div>
                    <span
                      class="status-pill"
                      [ngClass]="statusBadgeClass"
                      data-testid="reservation-status-badge"
                    >
                      {{ statusBadgeLabel }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Section 2: Pay Securely with Razorpay (Strict Mockup Fidelity) -->
        <div class="section-block mb-4">
          <div class="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
            <div class="d-flex align-items-center gap-3">
              <div class="badge-icon-square bg-soft-teal text-teal flex-shrink-0">
                <i class="fa-solid fa-bolt fs-5" aria-hidden="true"></i>
              </div>
              <div>
                <h2 class="section-heading fw-bold text-navy mb-0">Pay Securely with Razorpay</h2>
                <p class="section-subtext text-muted mb-0">
                  Complete your payment using Razorpay's secure checkout.
                </p>
              </div>
            </div>

            <!-- Inline Crisp Razorpay Wordmark SVG -->
            <div class="razorpay-wordmark-wrap" aria-label="Razorpay" title="Razorpay Secure Checkout">
              <svg width="118" height="26" viewBox="0 0 118 26" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Razorpay">
                <path d="M10.7 2L6.6 17.3L12.1 13.7L15.6 2H10.7Z" fill="#3395FF"/>
                <path d="M1.5 23.5H7.2L10.3 12.1L5.2 15.5L1.5 23.5Z" fill="#072654"/>
                <path d="M13.6 8.2L9.4 23.5H14.4L17.9 10.6L13.6 8.2Z" fill="#072654"/>
                <text x="23" y="19" fill="#072654" font-family="system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif" font-size="16" font-weight="800" font-style="italic" letter-spacing="-0.3">Razorpay</text>
              </svg>
            </div>
          </div>

          <div class="meta-strip-box">
            <div class="meta-strip-grid">
              <div class="meta-cell">
                <div class="meta-icon-wrap text-teal">
                  <i class="fa-solid fa-shield-halved" aria-hidden="true"></i>
                </div>
                <div class="min-w-0">
                  <div class="trust-title">Secure &amp; Encrypted</div>
                  <div class="trust-subtext">PCI-DSS Compliant</div>
                </div>
              </div>

              <div class="meta-cell meta-cell-divider">
                <div class="meta-icon-wrap text-teal">
                  <i class="fa-solid fa-bolt" aria-hidden="true"></i>
                </div>
                <div class="min-w-0">
                  <div class="trust-title">Multiple Payment Options</div>
                  <div class="trust-subtext">UPI, Cards, Net Banking &amp; Wallets</div>
                </div>
              </div>

              <div class="meta-cell meta-cell-divider">
                <div class="meta-icon-wrap text-teal">
                  <i class="fa-solid fa-certificate" aria-hidden="true"></i>
                </div>
                <div class="min-w-0">
                  <div class="trust-title">Instant Confirmation</div>
                  <div class="trust-subtext">Booking confirmed after successful payment</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Config warning if gateway key missing -->
        <div
          *ngIf="configUnavailableWarning"
          class="small text-danger fw-medium mb-3 d-flex align-items-center gap-2"
          role="alert"
          data-testid="payment-config-warning"
        >
          <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
          <span>{{ configUnavailableWarning }}</span>
        </div>

        <!-- Section 3: Bottom Action Bar (Back to Review | Pay ₹Amount with Razorpay) -->
        <div class="action-bar-row">
          <button
            type="button"
            class="btn-outline-action"
            [disabled]="uiState === 'PROCESSING'"
            (click)="onBackToReviewClick()"
            data-testid="back-to-review-btn"
          >
            <i class="fa-solid fa-arrow-left" aria-hidden="true"></i>
            <span>Back to Review</span>
          </button>

          <button
            type="button"
            class="btn-pay-razorpay"
            [disabled]="!canPayNow"
            (click)="onPayWithRazorpay()"
            data-testid="pay-with-razorpay-btn"
          >
            <ng-container *ngIf="uiState === 'PROCESSING'; else idlePayLabel">
              <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
              <span>{{ processingLabel }}</span>
            </ng-container>
            <ng-template #idlePayLabel>
              <i class="fa-regular fa-credit-card" aria-hidden="true"></i>
              <span>
                {{ uiState === 'PAYMENT_FAILED' ? 'Retry Payment (' + formattedPayAmount + ') with Razorpay' : 'Pay ' + formattedPayAmount + ' with Razorpay' }}
              </span>
              <i class="fa-solid fa-chevron-right chevron-icon" aria-hidden="true"></i>
            </ng-template>
          </button>
        </div>
      </section>

      <!-- STATE 3: AUTHORITATIVELY CONFIRMED -->
      <section
        *ngIf="!missingPrerequisites && reservation && uiState === 'CONFIRMED'"
        class="confirmed-card-wrapper"
        data-testid="payment-confirmed-state"
        aria-labelledby="confirmed-card-heading"
      >
        <article class="confirmed-main-card">
          <!-- Centered Celebration Checkmark & Confirmation Header -->
          <header class="confirmed-card-header">
            <div class="confirmed-celebration-wrap" aria-hidden="true">
              <svg
                class="confirmed-celebration-svg"
                viewBox="0 0 132 76"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
              >
                <!-- Subtle radial confetti bursts matching approved mockup -->
                <path d="M18 14L27 21" stroke="#F59E0B" stroke-width="3" stroke-linecap="round" />
                <circle cx="15" cy="37" r="3" fill="#0D9488" />
                <path d="M25 60L17 65" stroke="#16A34A" stroke-width="2.5" stroke-linecap="round" />
                <path d="M35 8L37 16" stroke="#22C55E" stroke-width="2.5" stroke-linecap="round" />
                <path d="M97 8L95 16" stroke="#0D9488" stroke-width="2.5" stroke-linecap="round" />
                <path d="M114 15L105 22" stroke="#F59E0B" stroke-width="3" stroke-linecap="round" />
                <circle cx="117" cy="39" r="3" fill="#F59E0B" />
                <path d="M107 60L115 65" stroke="#16A34A" stroke-width="2.5" stroke-linecap="round" />
                <!-- Green confirmation check emblem -->
                <circle cx="66" cy="38" r="29" fill="#DCFCE7" />
                <circle cx="66" cy="38" r="24" fill="#65D031" />
                <path
                  d="M55.5 38.5L62.5 45.5L77 31"
                  stroke="#FFFFFF"
                  stroke-width="4"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </div>
            <h2 id="confirmed-card-heading" class="confirmed-heading fw-bold text-navy mb-2">
              Booking Confirmed!
            </h2>
            <p class="confirmed-subtext text-slate mb-0">
              Thank you for booking with TravelEasy. Your hotel reservation is confirmed.
            </p>
          </header>

          <!-- 4-Column Payment & Booking Reference Strip -->
          <div
            class="confirmed-mint-strip confirmed-payment-strip mb-3"
            role="group"
            aria-label="Booking reference and payment summary"
          >
            <div class="confirmed-strip-cell">
              <div class="confirmed-strip-icon" aria-hidden="true">
                <i class="fa-regular fa-calendar-check"></i>
              </div>
              <div class="confirmed-strip-copy">
                <div class="confirmed-strip-label">Booking Reference</div>
                <div class="confirmed-strip-value text-navy text-break" data-testid="confirmed-booking-ref">
                  {{ reservation.bookingRef }}
                </div>
              </div>
            </div>

            <div class="confirmed-strip-cell confirmed-strip-divider">
              <div class="confirmed-strip-icon" aria-hidden="true">
                <i class="fa-regular fa-credit-card"></i>
              </div>
              <div class="confirmed-strip-copy">
                <div class="confirmed-strip-label">Payment Reference</div>
                <div class="confirmed-strip-value text-navy text-break" data-testid="confirmed-payment-ref">
                  {{ resolvedPaymentRef || resolvedTransactionRef || 'Verified' }}
                </div>
              </div>
            </div>

            <div class="confirmed-strip-cell confirmed-strip-divider">
              <div class="confirmed-strip-icon" aria-hidden="true">
                <i class="fa-solid fa-coins"></i>
              </div>
              <div class="confirmed-strip-copy">
                <div class="confirmed-strip-label">Amount Paid</div>
                <div class="confirmed-amount-value text-teal" data-testid="confirmed-amount-paid">
                  {{ formattedPayAmount }}
                </div>
              </div>
            </div>

            <div class="confirmed-strip-cell confirmed-strip-divider">
              <div class="confirmed-strip-icon" aria-hidden="true">
                <i class="fa-solid fa-shield-halved"></i>
              </div>
              <div class="confirmed-strip-copy">
                <div class="confirmed-strip-label">Payment Status</div>
                <div>
                  <span class="status-pill status-pill-paid" data-testid="confirmed-status-badge">
                    Paid
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- Hotel Information Card -->
          <article class="confirmed-hotel-card mb-3" data-testid="confirmed-hotel-card">
            <img
              [src]="confirmedHotelInfo.imageUrl"
              (error)="onConfirmedHotelImgError($event)"
              class="confirmed-hotel-photo"
              [alt]="confirmedHotelInfo.hotelName"
            >
            <div class="confirmed-hotel-body">
              <h3 class="confirmed-hotel-name fw-bold text-navy mb-2" data-testid="confirmed-hotel-name">
                {{ confirmedHotelInfo.hotelName }}
              </h3>

              <div
                *ngIf="confirmedHotelInfo.starRating != null || confirmedHotelInfo.reviewCount != null"
                class="confirmed-rating-row mb-2"
                data-testid="confirmed-hotel-rating"
              >
                <i class="fa-solid fa-star confirmed-star-icon" aria-hidden="true"></i>
                <span *ngIf="confirmedHotelInfo.starRating != null" class="fw-bold text-navy">
                  {{ confirmedHotelInfo.starRating }} Star
                </span>
                <span *ngIf="confirmedHotelInfo.propertyType" class="text-slate">
                  &bull; {{ confirmedHotelInfo.propertyType }}
                </span>
                <span *ngIf="confirmedHotelInfo.reviewCount != null" class="text-slate">
                  ({{ confirmedHotelInfo.reviewCount | number }} reviews)
                </span>
              </div>

              <div
                *ngIf="confirmedHotelInfo.locationLine"
                class="confirmed-location-row text-slate"
                data-testid="confirmed-hotel-location"
              >
                <i class="fa-solid fa-location-dot confirmed-location-icon flex-shrink-0" aria-hidden="true"></i>
                <span>
                  {{ confirmedHotelInfo.locationLine }}<ng-container *ngIf="confirmedHotelInfo.distanceLabel"> &bull; {{ confirmedHotelInfo.distanceLabel }}</ng-container>
                </span>
              </div>

              <div
                *ngIf="confirmedHotelInfo.amenities.length > 0"
                class="confirmed-amenities-divider"
              >
                <ul class="confirmed-amenities-list" role="list" data-testid="confirmed-hotel-amenities">
                  <li *ngFor="let amenity of confirmedHotelInfo.amenities" class="confirmed-amenity-item">
                    <i [class]="amenity.icon + ' confirmed-amenity-icon'" aria-hidden="true"></i>
                    <span>{{ amenity.label }}</span>
                  </li>
                </ul>
              </div>
            </div>
          </article>

          <!-- 3-Column Stay Information Strip -->
          <div
            class="confirmed-mint-strip confirmed-stay-strip mb-4"
            role="group"
            aria-label="Confirmed stay details"
            data-testid="confirmed-stay-strip"
          >
            <div class="confirmed-strip-cell">
              <div class="confirmed-strip-icon" aria-hidden="true">
                <i class="fa-regular fa-calendar-check"></i>
              </div>
              <div class="confirmed-strip-copy">
                <div class="confirmed-strip-label">Stay Window</div>
                <div class="confirmed-strip-value text-navy" data-testid="confirmed-stay-window">
                  {{ formatDate(reservation.checkInDate) }} &ndash; {{ formatDate(reservation.checkOutDate) }}
                </div>
                <div class="confirmed-strip-subvalue text-slate">
                  ({{ reservation.totalNights }} {{ reservation.totalNights === 1 ? 'night' : 'nights' }})
                </div>
              </div>
            </div>

            <div class="confirmed-strip-cell confirmed-strip-divider">
              <div class="confirmed-strip-icon" aria-hidden="true">
                <i class="fa-solid fa-user-group"></i>
              </div>
              <div class="confirmed-strip-copy">
                <div class="confirmed-strip-label">Guests</div>
                <div class="confirmed-strip-value text-navy" data-testid="confirmed-guests-summary">
                  {{ confirmedStayBreakdown.guestsLabel || 'Details on file' }}
                </div>
              </div>
            </div>

            <div class="confirmed-strip-cell confirmed-strip-divider">
              <div class="confirmed-strip-icon" aria-hidden="true">
                <i class="fa-solid fa-bed"></i>
              </div>
              <div class="confirmed-strip-copy">
                <div class="confirmed-strip-label">Rooms</div>
                <div class="confirmed-strip-value text-navy" data-testid="confirmed-rooms-summary">
                  {{ confirmedStayBreakdown.roomsCountLabel || 'See booking details' }}
                </div>
                <div
                  *ngIf="confirmedStayBreakdown.roomTypesLabel"
                  class="confirmed-strip-subvalue text-slate"
                  data-testid="confirmed-room-types-summary"
                >
                  {{ confirmedStayBreakdown.roomTypesLabel }}
                </div>
              </div>
            </div>
          </div>

          <!-- Bottom Action Buttons -->
          <footer class="confirmed-action-row">
            <button
              type="button"
              class="btn-primary-teal confirmed-cta-btn"
              (click)="onViewMyBookingsClick()"
              data-testid="view-my-bookings-btn"
            >
              <i class="fa-solid fa-suitcase-rolling" aria-hidden="true"></i>
              <span>View in My Bookings</span>
              <i class="fa-solid fa-chevron-right chevron-icon" aria-hidden="true"></i>
            </button>
            <button
              type="button"
              class="btn-outline-action confirmed-cta-btn"
              (click)="onBackToSearchClick()"
              data-testid="confirmed-book-another-btn"
            >
              <i class="fa-solid fa-hotel" aria-hidden="true"></i>
              <span>Book Another Hotel</span>
            </button>
          </footer>
        </article>
      </section>

      <!-- STATE 5: RESERVATION EXPIRED -->
      <section
        *ngIf="!missingPrerequisites && reservation && uiState === 'EXPIRED'"
        class="checkout-surface-card p-4 p-md-5"
        data-testid="payment-expired-state"
        aria-label="Reservation hold expired"
      >
        <div class="d-flex align-items-start gap-3 mb-4">
          <div class="state-icon-circle bg-soft-red text-danger flex-shrink-0">
            <i class="fa-regular fa-clock fs-4" aria-hidden="true"></i>
          </div>
          <div>
            <span class="status-pill status-pill-expired mb-2 d-inline-block" data-testid="expired-status-badge">
              {{ statusBadgeLabel }}
            </span>
            <h2 class="h4 fw-bold text-navy mb-1">Reservation Hold Expired</h2>
            <p class="text-muted small mb-0">
              Your 15-minute payment window elapsed before payment was completed. Held rooms have been automatically released.
            </p>
          </div>
        </div>

        <div class="meta-strip-box mb-4">
          <div class="meta-strip-grid">
            <div class="meta-cell">
              <div class="meta-icon-wrap text-teal">
                <i class="fa-regular fa-calendar" aria-hidden="true"></i>
              </div>
              <div>
                <div class="meta-label">Booking Reference</div>
                <div class="meta-value text-navy" data-testid="expired-booking-ref">{{ reservation.bookingRef }}</div>
              </div>
            </div>
            <div class="meta-cell meta-cell-divider">
              <div class="meta-icon-wrap text-danger">
                <i class="fa-regular fa-hourglass" aria-hidden="true"></i>
              </div>
              <div>
                <div class="meta-label">Payment Window</div>
                <div class="countdown-value tabular-nums">00:00</div>
              </div>
            </div>
            <div class="meta-cell meta-cell-divider">
              <div class="meta-icon-wrap text-teal">
                <i class="fa-solid fa-lock-open" aria-hidden="true"></i>
              </div>
              <div>
                <div class="meta-label">Reservation Status</div>
                <span class="status-pill status-pill-expired">{{ statusBadgeLabel }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="action-bar-row">
          <button
            type="button"
            class="btn-outline-action"
            *ngIf="reservation.hotelId > 0"
            (click)="onSelectRoomsAgainClick()"
            data-testid="expired-select-rooms-btn"
          >
            <i class="fa-solid fa-bed" aria-hidden="true"></i>
            <span>Select Rooms Again</span>
          </button>
          <button
            type="button"
            class="btn-primary-teal"
            (click)="onBackToSearchClick()"
            data-testid="expired-back-to-search-btn"
          >
            <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
            <span>Back to Search Hotels</span>
          </button>
        </div>
      </section>

      <!-- STATE 6: CAPTURED LATE PAYMENT + EXPIRED / AUTOMATED REFUND INITIATED -->
      <section
        *ngIf="!missingPrerequisites && reservation && uiState === 'REFUND_INITIATED'"
        class="checkout-surface-card p-4 p-md-5"
        data-testid="payment-refund-state"
        aria-label="Late payment refund initiated"
      >
        <div class="d-flex align-items-start gap-3 mb-4">
          <div class="state-icon-circle bg-soft-amber text-amber flex-shrink-0">
            <i class="fa-solid fa-rotate-left fs-4" aria-hidden="true"></i>
          </div>
          <div>
            <span class="status-pill status-pill-amber mb-2 d-inline-block" data-testid="refund-status-badge">
              {{ statusBadgeLabel }}
            </span>
            <h2 class="h4 fw-bold text-navy mb-1">{{ statusTitle }}</h2>
            <p class="text-muted small mb-0" data-testid="refund-explanation">
              {{ statusDescription }}
            </p>
          </div>
        </div>

        <div class="meta-strip-box mb-4">
          <div class="outcome-details-grid">
            <div>
              <div class="meta-label">Booking Reference</div>
              <div class="meta-value text-navy" data-testid="refund-booking-ref">{{ reservation.bookingRef }}</div>
            </div>
            <div>
              <div class="meta-label">Payment Reference</div>
              <div class="meta-value text-navy text-break">{{ resolvedPaymentRef || resolvedTransactionRef || 'Captured' }}</div>
            </div>
            <div>
              <div class="meta-label">Refund Status</div>
              <div class="meta-value text-amber" data-testid="refund-status-text">{{ resolvedRefundStatus || 'INITIATED' }}</div>
            </div>
            <div>
              <div class="meta-label">Refund Reference &amp; Amount</div>
              <div class="meta-value text-navy" data-testid="refund-amount-ref">
                {{ formattedRefundAmount }}
                <span *ngIf="resolvedRefundId" class="small text-muted fw-normal">({{ resolvedRefundId }})</span>
              </div>
            </div>
          </div>
        </div>

        <div class="action-bar-row">
          <button
            type="button"
            class="btn-outline-action"
            (click)="onViewMyBookingsClick()"
            data-testid="refund-view-bookings-btn"
          >
            <span>View My Bookings</span>
          </button>
          <button
            type="button"
            class="btn-primary-teal"
            (click)="onBackToSearchClick()"
            data-testid="refund-back-to-search-btn"
          >
            <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
            <span>Back to Search Hotels</span>
          </button>
        </div>
      </section>

      <!-- STATE 7: PAYMENT RECEIVED / CONFIRMATION PENDING -->
      <section
        *ngIf="!missingPrerequisites && reservation && uiState === 'CONFIRMATION_PENDING'"
        class="checkout-surface-card p-4 p-md-5"
        data-testid="payment-confirmation-pending-state"
        aria-label="Payment received confirmation pending"
      >
        <div class="d-flex align-items-start gap-3 mb-4">
          <div class="state-icon-circle bg-soft-teal text-teal flex-shrink-0">
            <span *ngIf="isReconcilingStatus" class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
            <i *ngIf="!isReconcilingStatus" class="fa-solid fa-hourglass-half fs-4" aria-hidden="true"></i>
          </div>
          <div>
            <span class="status-pill status-pill-info mb-2 d-inline-block" data-testid="confirmation-pending-badge">
              Confirmation Pending
            </span>
            <h2 class="h4 fw-bold text-navy mb-1">Payment Received — Finalizing Booking</h2>
            <p class="text-muted small mb-0">
              Your payment has been received by our gateway. Please do not make a second payment while we finalize your reservation status.
            </p>
          </div>
        </div>

        <div class="meta-strip-box mb-4">
          <div class="outcome-details-grid">
            <div>
              <div class="meta-label">Booking Reference</div>
              <div class="meta-value text-navy">{{ reservation.bookingRef }}</div>
            </div>
            <div>
              <div class="meta-label">Payment / Transaction Reference</div>
              <div class="meta-value text-navy text-break">{{ resolvedPaymentRef || resolvedTransactionRef || 'Recorded' }}</div>
            </div>
            <div>
              <div class="meta-label">Amount Paid</div>
              <div class="meta-value text-teal">{{ formattedPayAmount }}</div>
            </div>
          </div>
        </div>

        <div class="action-bar-row">
          <button
            type="button"
            class="btn-outline-action"
            (click)="onViewMyBookingsClick()"
            data-testid="pending-view-bookings-btn"
          >
            <span>Go to My Bookings</span>
          </button>
          <button
            type="button"
            class="btn-primary-teal"
            [disabled]="isReconcilingStatus"
            (click)="checkStatusNow()"
            data-testid="check-confirmation-status-btn"
          >
            <i class="fa-solid fa-arrows-rotate" aria-hidden="true"></i>
            <span>{{ isReconcilingStatus ? 'Checking Status...' : 'Check Confirmation Status' }}</span>
          </button>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .hotel-payment-page-container {
      width: 100%;
      max-width: 100%;
      overflow-x: clip;
    }
    .text-navy {
      color: #0F172A;
    }
    .text-slate {
      color: #475569;
    }
    .text-teal {
      color: #0D9488;
    }
    .text-amber {
      color: #B45309;
    }

    /* Hero Banner aligned with WP-HB-UI-05 approved mockup */
    .payment-hero-banner {
      position: relative;
      border-radius: 18px;
      border: 1px solid #D4E5F9;
      background-color: #EFF7FE;
      background-image:
        linear-gradient(
          90deg,
          rgba(239, 247, 254, 0.98) 0%,
          rgba(239, 247, 254, 0.94) 50%,
          rgba(239, 247, 254, 0.45) 74%,
          rgba(239, 247, 254, 0.08) 100%
        ),
        url('/assets/images/hotel_search_banner_background.png');
      background-position: right center;
      background-size: cover;
      background-repeat: no-repeat;
      padding: 24px 28px;
      min-height: 98px;
      display: flex;
      align-items: center;
    }
    .payment-hero-content {
      position: relative;
      z-index: 1;
      max-width: 68%;
    }
    .hero-title {
      font-size: 1.45rem;
      line-height: 1.25;
      letter-spacing: -0.015em;
    }
    .hero-subtitle {
      font-size: 0.875rem;
      line-height: 1.45;
    }
    @media (max-width: 767.98px) {
      .payment-hero-banner {
        padding: 18px 20px;
        background-image:
          linear-gradient(
            180deg,
            rgba(239, 247, 254, 0.96) 0%,
            rgba(239, 247, 254, 0.92) 70%,
            rgba(239, 247, 254, 0.82) 100%
          ),
          url('/assets/images/hotel_search_banner_background.png');
      }
      .payment-hero-content {
        max-width: 100%;
      }
      .hero-title {
        font-size: 1.25rem;
      }
    }

    /* Unified white card */
    .checkout-surface-card {
      background-color: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 18px;
      box-shadow: 0 2px 12px rgba(15, 23, 42, 0.04);
    }

    .badge-icon-circle,
    .state-icon-circle {
      width: 44px;
      height: 44px;
      border-radius: 9999px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .badge-icon-square,
    .state-icon-square {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .bg-soft-teal {
      background-color: #E6F7F7;
    }
    .bg-soft-green {
      background-color: #DCFCE7;
    }
    .bg-soft-amber {
      background-color: #FEF3C7;
    }
    .bg-soft-red {
      background-color: #FEE2E2;
    }

    .section-heading {
      font-size: 1.15rem;
      line-height: 1.3;
    }
    .section-subtext {
      font-size: 0.85rem;
      line-height: 1.4;
    }

    /* Inner light ice/slate strips from mockup */
    .meta-strip-box {
      background-color: #F8FAFC;
      border: 1px solid #E2E8F0;
      border-radius: 14px;
      padding: 16px 20px;
    }
    .meta-strip-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 16px;
      align-items: center;
    }
    .meta-cell {
      display: flex;
      align-items: center;
      gap: 12px;
      min-width: 0;
    }
    .meta-cell-divider {
      border-left: 1px solid #E2E8F0;
      padding-left: 20px;
    }
    .meta-icon-wrap {
      font-size: 1.15rem;
      width: 24px;
      display: inline-flex;
      justify-content: center;
      flex-shrink: 0;
    }
    .meta-label {
      font-size: 0.78rem;
      color: #64748B;
      font-weight: 500;
      margin-bottom: 2px;
    }
    .meta-value {
      font-size: 0.98rem;
      font-weight: 700;
      line-height: 1.3;
    }
    .countdown-value {
      display: inline-block;
      font-size: 1.35rem;
      font-weight: 800;
      line-height: 1.2;
      color: #DC2626;
      letter-spacing: 0.01em;
    }
    .tabular-nums {
      font-variant-numeric: tabular-nums;
    }

    .trust-title {
      font-size: 0.92rem;
      font-weight: 700;
      color: #0F172A;
      line-height: 1.3;
    }
    .trust-subtext {
      font-size: 0.78rem;
      color: #64748B;
      line-height: 1.35;
    }

    .outcome-details-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px 24px;
    }

    @media (max-width: 767.98px) {
      .meta-strip-grid,
      .outcome-details-grid {
        grid-template-columns: 1fr;
        gap: 14px;
      }
      .meta-cell-divider {
        border-left: none;
        padding-left: 0;
        border-top: 1px solid #E2E8F0;
        padding-top: 14px;
      }
    }

    /* Status Badges */
    .status-pill {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 9999px;
      font-size: 0.8rem;
      font-weight: 700;
      line-height: 1.3;
    }
    .status-pill-pending {
      background-color: #FEF3C7;
      color: #92400E;
    }
    .status-pill-processing,
    .status-pill-info {
      background-color: #DBEAFE;
      color: #1E40AF;
    }
    .status-pill-confirmed {
      background-color: #DCFCE7;
      color: #166534;
    }
    .status-pill-expired {
      background-color: #FEE2E2;
      color: #991B1B;
    }
    .status-pill-amber {
      background-color: #FEF3C7;
      color: #92400E;
    }

    .checkout-alert {
      border-radius: 12px;
      padding: 12px 16px;
      border: 1px solid transparent;
    }
    .checkout-alert-danger {
      background-color: #FEF2F2;
      border-color: #FECACA;
      color: #991B1B;
    }
    .checkout-alert-warning {
      background-color: #FFFBEB;
      border-color: #FDE68A;
      color: #92400E;
    }

    .razorpay-wordmark-wrap {
      display: inline-flex;
      align-items: center;
      padding: 4px 6px;
    }

    /* Bottom Action Bar */
    .action-bar-row {
      display: flex;
      align-items: center;
      gap: 16px;
      width: 100%;
    }
    .btn-outline-action {
      flex: 1 1 46%;
      min-height: 48px;
      border-radius: 12px;
      border: 1px solid #CBD5E1;
      background-color: #FFFFFF;
      color: #0F172A;
      font-weight: 700;
      font-size: 0.95rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 10px;
      padding: 12px 20px;
      cursor: pointer;
      transition: background-color 0.16s ease, border-color 0.16s ease;
    }
    .btn-outline-action:hover:not(:disabled) {
      background-color: #F8FAFC;
      border-color: #94A3B8;
    }
    .btn-pay-razorpay,
    .btn-primary-teal {
      flex: 1.15 1 54%;
      min-height: 48px;
      border-radius: 12px;
      border: none;
      background-color: #0D9488;
      color: #FFFFFF;
      font-weight: 700;
      font-size: 0.98rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 10px;
      padding: 12px 24px;
      cursor: pointer;
      transition: background-color 0.16s ease, opacity 0.16s ease;
    }
    .btn-pay-razorpay:hover:not(:disabled),
    .btn-primary-teal:hover:not(:disabled) {
      background-color: #0F766E;
    }
    .btn-pay-razorpay:disabled,
    .btn-primary-teal:disabled,
    .btn-outline-action:disabled {
      opacity: 0.58;
      cursor: not-allowed;
    }
    .btn-pay-razorpay:focus-visible,
    .btn-primary-teal:focus-visible,
    .btn-outline-action:focus-visible {
      outline: 2px solid #0891B2;
      outline-offset: 2px;
    }
    .chevron-icon {
      font-size: 0.85rem;
    }

    @media (max-width: 767.98px) {
      .action-bar-row {
        flex-direction: column-reverse;
      }
      .btn-outline-action,
      .btn-pay-razorpay,
      .btn-primary-teal {
        width: 100%;
        flex: none;
      }
    }

    /* ==============================================================
       Hotel Booking Confirmation Card (Centered Confirmation State)
       ============================================================== */
    .confirmed-card-wrapper {
      width: 100%;
      display: flex;
      justify-content: center;
      padding: 4px 0 12px;
    }

    .confirmed-main-card {
      width: 100%;
      max-width: 880px;
      background-color: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 20px;
      box-shadow: 0 8px 28px rgba(15, 23, 42, 0.06);
      padding: 34px 32px 30px;
      container-type: inline-size;
    }

    .confirmed-card-header {
      text-align: center;
      margin-bottom: 24px;
    }

    .confirmed-celebration-wrap {
      display: flex;
      justify-content: center;
      margin-bottom: 10px;
    }

    .confirmed-celebration-svg {
      width: 124px;
      height: auto;
      display: block;
    }

    .confirmed-heading {
      font-size: clamp(1.45rem, 1.15rem + 1.1vw, 1.85rem);
      line-height: 1.2;
      letter-spacing: -0.02em;
    }

    .confirmed-subtext {
      font-size: 0.93rem;
      line-height: 1.45;
    }

    /* Light Mint/Ice Information Strips */
    .confirmed-mint-strip {
      background-color: #F3FAF9;
      border: 1px solid #DCEEEB;
      border-radius: 14px;
      padding: 16px 20px;
      display: grid;
      gap: 16px;
      align-items: start;
    }

    .confirmed-payment-strip {
      grid-template-columns: minmax(0, 1.38fr) minmax(0, 1.25fr) minmax(0, 0.78fr) minmax(0, 0.78fr);
    }

    .confirmed-stay-strip {
      grid-template-columns: minmax(0, 1.45fr) minmax(0, 0.85fr) minmax(0, 0.95fr);
    }

    .confirmed-strip-cell {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      min-width: 0;
    }

    .confirmed-strip-divider {
      border-left: 1px solid #D5EAE6;
      padding-left: 14px;
    }

    .confirmed-strip-icon {
      color: #0D9488;
      font-size: 1rem;
      width: 20px;
      padding-top: 2px;
      display: inline-flex;
      justify-content: center;
      flex-shrink: 0;
    }

    .confirmed-strip-copy {
      min-width: 0;
      flex: 1;
    }

    .confirmed-strip-label {
      font-size: 0.76rem;
      font-weight: 500;
      color: #64748B;
      margin-bottom: 3px;
      line-height: 1.3;
    }

    .confirmed-strip-value {
      font-size: 0.87rem;
      font-weight: 700;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .confirmed-strip-subvalue {
      font-size: 0.82rem;
      font-weight: 500;
      line-height: 1.35;
      margin-top: 2px;
    }

    .confirmed-amount-value {
      font-size: 1.05rem;
      font-weight: 800;
      line-height: 1.25;
      letter-spacing: -0.01em;
    }

    .status-pill-paid {
      background-color: #DDF7E8;
      color: #15803D;
      padding: 3px 12px;
      font-size: 0.8rem;
      font-weight: 700;
    }

    /* Inner Hotel Information Card (container-query adaptive) */
    .confirmed-hotel-card {
      background-color: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 14px;
      padding: 14px 16px;
      display: grid;
      grid-template-columns: 235px minmax(0, 1fr);
      gap: 18px;
      align-items: center;
      container-type: inline-size;
    }

    .confirmed-hotel-photo {
      width: 100%;
      aspect-ratio: 16 / 10;
      border-radius: 10px;
      object-fit: cover;
      display: block;
      background-color: #F1F5F9;
    }

    .confirmed-hotel-body {
      min-width: 0;
    }

    .confirmed-hotel-name {
      font-size: 1.15rem;
      line-height: 1.25;
      letter-spacing: -0.01em;
    }

    .confirmed-rating-row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
      font-size: 0.85rem;
    }

    .confirmed-star-icon {
      color: #F59E0B;
      font-size: 0.88rem;
    }

    .confirmed-location-row {
      display: flex;
      align-items: flex-start;
      gap: 7px;
      font-size: 0.85rem;
      line-height: 1.4;
    }

    .confirmed-location-icon {
      color: #0D9488;
      margin-top: 3px;
    }

    .confirmed-amenities-divider {
      margin-top: 12px;
      padding-top: 10px;
      border-top: 1px solid #EEF2F7;
    }

    .confirmed-amenities-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 8px 18px;
    }

    .confirmed-amenity-item {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      font-size: 0.83rem;
      color: #334155;
      font-weight: 500;
    }

    .confirmed-amenity-icon {
      color: #0D9488;
      font-size: 0.88rem;
    }

    /* Bottom Action Buttons */
    .confirmed-action-row {
      display: flex;
      align-items: stretch;
      justify-content: center;
      gap: 16px;
    }

    .confirmed-cta-btn {
      flex: 1 1 0;
      min-height: 48px;
      border-radius: 10px;
      padding: 12px 16px;
      font-size: 0.94rem;
    }

    @container (max-width: 700px) {
      .confirmed-payment-strip {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
      .confirmed-payment-strip .confirmed-strip-cell:nth-child(3) {
        border-left: none;
        padding-left: 0;
        border-top: 1px solid #D5EAE6;
        padding-top: 14px;
      }
      .confirmed-payment-strip .confirmed-strip-cell:nth-child(4) {
        border-top: 1px solid #D5EAE6;
        padding-top: 14px;
      }
      .confirmed-stay-strip {
        grid-template-columns: 1fr;
        gap: 14px;
      }
      .confirmed-stay-strip .confirmed-strip-divider {
        border-left: none;
        padding-left: 0;
        border-top: 1px solid #D5EAE6;
        padding-top: 14px;
      }
    }

    @container (max-width: 560px) {
      .confirmed-hotel-card {
        grid-template-columns: 1fr;
        gap: 14px;
      }
      .confirmed-hotel-photo {
        aspect-ratio: 16 / 9;
        max-height: 210px;
      }
    }

    @media (max-width: 767.98px) {
      .confirmed-main-card {
        padding: 24px 18px 22px;
        border-radius: 16px;
      }
      .confirmed-payment-strip {
        grid-template-columns: 1fr;
        gap: 14px;
      }
      .confirmed-payment-strip .confirmed-strip-divider,
      .confirmed-payment-strip .confirmed-strip-cell:nth-child(3),
      .confirmed-payment-strip .confirmed-strip-cell:nth-child(4) {
        border-left: none;
        padding-left: 0;
        border-top: 1px solid #D5EAE6;
        padding-top: 14px;
      }
      .confirmed-stay-strip {
        grid-template-columns: 1fr;
        gap: 14px;
      }
      .confirmed-stay-strip .confirmed-strip-divider {
        border-left: none;
        padding-left: 0;
        border-top: 1px solid #D5EAE6;
        padding-top: 14px;
      }
      .confirmed-hotel-card {
        grid-template-columns: 1fr;
        gap: 14px;
      }
      .confirmed-action-row {
        flex-direction: column;
      }
      .confirmed-cta-btn {
        width: 100%;
        flex: none;
      }
    }
  `]
})
export class HotelPaymentCheckoutPageComponent implements OnInit, OnChanges, OnDestroy {
  @Input() staySummary: HotelStaySummary | null = null;
  @Input() allocatedRooms: PhysicalRoomAllocation[] = [];
  @Input() primaryGuest: PrimaryGuestDtoPayload | null = null;
  @Input() currentUser: User | null = null;
  @Input() requestedHotelId: number | null = null;
  @Input() initiationToken: number = 0;

  @Output() backToReview = new EventEmitter<void>();
  @Output() backToSearch = new EventEmitter<void>();
  @Output() backToHotelDetails = new EventEmitter<number>();
  @Output() viewMyBookings = new EventEmitter<void>();
  @Output() reservationConfirmed = new EventEmitter<HotelActiveReservation>();

  uiState: HotelPaymentUiState = 'INITIATING';
  reservation: HotelActiveReservation | null = null;
  missingPrerequisites = false;
  errorBannerMessage = '';
  configUnavailableWarning = '';
  processingLabel = 'Preparing payment...';
  politeAnnouncement = '';

  statusBadgeLabel = 'Pending Payment';
  statusTitle = 'Reservation Created';
  statusDescription = '';
  resolvedPaymentRef: string | null = null;
  resolvedTransactionRef: string | null = null;
  resolvedRefundId: string | null = null;
  resolvedRefundStatus: string | null = null;
  resolvedRefundAmount: number | null = null;
  isReconcilingStatus = false;

  countdownDisplay = '--:--';
  remainingSeconds = 0;
  expiresAtIsoAttr: string | null = null;
  accessibleCountdownText = 'Reservation countdown active';

  private lastHandledInitiationToken = -1;
  private activeCandidateBookingRef: string | null = null;
  private isInitiatingRequest = false;
  private isPaymentFlowActive = false;
  private paymentConfig: PaymentConfigResponse | null = null;
  private hotelDetails: HotelDetailsResponseDto | null = null;
  private loadedHotelDetailsId: number | null = null;
  private fetchingHotelDetailsId: number | null = null;
  private countdownSub: Subscription | null = null;
  private pendingAutoPollSub: Subscription | null = null;
  private pendingPollAttempts = 0;
  private isDestroyed = false;

  constructor(
    private bookingService: BookingService,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef,
    @Optional() private hotelService?: HotelService
  ) {}

  ngOnInit(): void {
    this.lastHandledInitiationToken = this.initiationToken;
    this.bootstrapCheckout();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (
      changes['initiationToken'] &&
      !changes['initiationToken'].firstChange &&
      this.initiationToken !== this.lastHandledInitiationToken
    ) {
      this.lastHandledInitiationToken = this.initiationToken;
      this.bootstrapCheckout();
    }
  }

  ngOnDestroy(): void {
    this.isDestroyed = true;
    this.stopCountdownTimer();
    this.stopPendingConfirmationPoll();
  }

  get bannerTitle(): string {
    switch (this.uiState) {
      case 'CONFIRMED':
        return 'Reservation Confirmed';
      case 'REFUND_INITIATED':
        return 'Reservation Refund Update';
      case 'CONFIRMATION_PENDING':
        return 'Confirming Your Reservation';
      case 'EXPIRED':
        return 'Reservation Hold Expired';
      default:
        return 'Secure Your Reservation';
    }
  }

  get bannerSubtitle(): string {
    switch (this.uiState) {
      case 'CONFIRMED':
        return 'Your payment has been verified and your hotel stay is officially confirmed.';
      case 'REFUND_INITIATED':
        return 'Payment was received after the hold elapsed; an automated refund is underway.';
      case 'CONFIRMATION_PENDING':
        return 'Your payment succeeded and we are completing final confirmation with the booking service.';
      case 'EXPIRED':
        return 'The temporary room reservation window expired before payment could be completed.';
      default:
        return 'Your hotel reservation has been created. Complete the payment within the given time to confirm your booking.';
    }
  }

  get statusBadgeClass(): string {
    if (this.uiState === 'PROCESSING') return 'status-pill-processing';
    if (this.uiState === 'CONFIRMED') return 'status-pill-confirmed';
    if (this.uiState === 'EXPIRED') return 'status-pill-expired';
    if (this.uiState === 'REFUND_INITIATED') return 'status-pill-amber';
    if (this.uiState === 'CONFIRMATION_PENDING') return 'status-pill-info';
    return 'status-pill-pending';
  }

  get formattedPayAmount(): string {
    return formatAuthoritativeAmount(this.reservation?.totalAmount, this.reservation?.currency || 'INR');
  }

  get formattedRefundAmount(): string {
    return formatAuthoritativeAmount(this.resolvedRefundAmount, this.reservation?.currency || 'INR');
  }

  get confirmedHotelInfo(): ConfirmedHotelDisplayInfo {
    return buildConfirmedHotelDisplayInfo({
      hotelId: this.reservation?.hotelId || this.staySummary?.hotelId || this.requestedHotelId || 0,
      hotelName: this.reservation?.hotelName || this.staySummary?.hotelName || 'Hotel Reservation',
      hotelDetails: this.hotelDetails,
      staySummary: this.staySummary
    });
  }

  get confirmedStayBreakdown(): ConfirmedStayBreakdown {
    return extractConfirmedStayBreakdown(this.reservation, this.allocatedRooms);
  }

  onConfirmedHotelImgError(event: Event): void {
    const imgEl = event.target as HTMLImageElement | null;
    if (imgEl && !imgEl.src.endsWith(DEFAULT_HOTEL_PLACEHOLDER)) {
      imgEl.src = DEFAULT_HOTEL_PLACEHOLDER;
    }
  }

  get canPayNow(): boolean {
    if (!this.reservation) return false;
    if (this.uiState === 'PROCESSING' || this.isPaymentFlowActive) return false;
    if (this.uiState !== 'PENDING_PAYMENT' && this.uiState !== 'PAYMENT_FAILED') return false;
    const expiresAtMs = parseBackendExpiresAtMs(this.reservation.expiresAt);
    if (expiresAtMs !== null && Date.now() >= expiresAtMs) return false;
    return true;
  }

  formatDate(isoDate: string): string {
    return formatStayDisplayDate(isoDate);
  }

  private bootstrapCheckout(): void {
    this.stopCountdownTimer();
    this.stopPendingConfirmationPoll();
    this.missingPrerequisites = false;
    this.errorBannerMessage = '';
    this.configUnavailableWarning = '';

    const hotelIdScope = this.staySummary?.hotelId ?? this.requestedHotelId ?? null;
    const allocationsSig = this.staySummary
      ? buildAllocationsSignature(this.staySummary, this.allocatedRooms)
      : null;

    const saved = loadHotelPaymentCheckoutState(
      this.currentUser?.id ?? null,
      hotelIdScope,
      null
    );

    const hasStage4Inputs =
      !!this.staySummary &&
      Array.isArray(this.allocatedRooms) &&
      this.allocatedRooms.length > 0 &&
      !!this.primaryGuest;

    // Decide whether we can recover an existing bookingRef matching this context
    if (saved && saved.bookingRef) {
      const sameSelection =
        !allocationsSig ||
        !saved.allocationsSignature ||
        saved.allocationsSignature === allocationsSig;

      if (sameSelection) {
        this.activeCandidateBookingRef = saved.bookingRef;
        this.reconcileExistingReservation(saved.bookingRef, hasStage4Inputs && this.initiationToken > 0);
        return;
      }
    }

    if (!hasStage4Inputs) {
      this.missingPrerequisites = true;
      this.uiState = 'INITIATION_FAILED';
      this.politeAnnouncement = 'No active hotel reservation could be found.';
      return;
    }

    this.initiateFreshReservation();
  }

  private reconcileExistingReservation(bookingRef: string, allowReinitIfExpired: boolean): void {
    this.uiState = 'INITIATING';
    this.isInitiatingRequest = true;

    forkJoin({
      detail: this.bookingService.getHotelReservationDetails(bookingRef).pipe(
        catchError(err => of({ __error: err } as any))
      ),
      payment: this.bookingService.getPaymentByBookingRef(bookingRef).pipe(
        catchError(() => of(null))
      )
    }).subscribe(({ detail, payment }) => {
      if (this.isDestroyed) return;
      this.isInitiatingRequest = false;

      if (!detail || (detail as any).__error) {
        const apiErr = (detail as any)?.__error;
        const parsed = extractApiErrorMessage(apiErr, 'Unable to load reservation status.');
        if (parsed.status === 404 && this.hasCompleteStage4Inputs()) {
          clearHotelPaymentCheckoutState();
          this.activeCandidateBookingRef = null;
          this.initiateFreshReservation();
          return;
        }
        this.uiState = 'INITIATION_FAILED';
        this.errorBannerMessage = parsed.message;
        this.politeAnnouncement = parsed.message;
        return;
      }

      const activeRes = fromDetailResponse(detail);
      const txn = (payment as PaymentTransactionDto | null) || null;
      const outcome = reconcileCheckoutState({
        reservation: activeRes,
        paymentTxn: txn
      });

      // If user explicitly navigated from Stage 4 after an unpaid hold had previously expired or completed, start a new hold
      if (
        allowReinitIfExpired &&
        (outcome.uiState === 'EXPIRED' || outcome.uiState === 'CONFIRMED') &&
        this.hasCompleteStage4Inputs()
      ) {
        clearHotelPaymentCheckoutState();
        this.activeCandidateBookingRef = null;
        this.initiateFreshReservation();
        return;
      }

      this.reservation = activeRes;
      this.applyReconciledOutcome(outcome, null);
      this.prefetchPaymentConfig();
    });
  }

  private hasCompleteStage4Inputs(): boolean {
    return (
      !!this.staySummary &&
      Array.isArray(this.allocatedRooms) &&
      this.allocatedRooms.length > 0 &&
      !!this.primaryGuest
    );
  }

  private initiateFreshReservation(): void {
    if (this.isInitiatingRequest) return;
    if (!this.staySummary || !this.primaryGuest || this.allocatedRooms.length === 0) {
      this.missingPrerequisites = true;
      return;
    }

    this.isInitiatingRequest = true;
    this.uiState = 'INITIATING';
    this.errorBannerMessage = '';
    this.politeAnnouncement = 'Creating your hotel reservation and locking room availability.';

    if (!this.activeCandidateBookingRef) {
      this.activeCandidateBookingRef = generateHotelBookingRef();
    }

    const request = buildHotelReservationRequest(
      this.staySummary,
      this.allocatedRooms,
      this.primaryGuest,
      this.currentUser,
      this.activeCandidateBookingRef
    );

    const staySignature = buildStayContextSignature(
      this.staySummary.hotelId,
      this.staySummary.checkIn,
      this.staySummary.checkOut
    );
    const allocationsSignature = buildAllocationsSignature(this.staySummary, this.allocatedRooms);

    this.bookingService.initiateHotelReservation(request).subscribe({
      next: (res: HotelReservationInitiationResponse) => {
        if (this.isDestroyed) return;
        this.isInitiatingRequest = false;
        const activeRes = fromInitiationResponse(res);
        this.reservation = activeRes;
        this.activeCandidateBookingRef = activeRes.bookingRef;

        saveHotelPaymentCheckoutState({
          userId: this.currentUser?.id ?? null,
          bookingRef: activeRes.bookingRef,
          hotelId: activeRes.hotelId || this.staySummary!.hotelId,
          staySignature,
          allocationsSignature,
          lastKnownOrderId: null,
          updatedAt: new Date().toISOString()
        });

        const outcome = reconcileCheckoutState({ reservation: activeRes });
        this.applyReconciledOutcome(outcome, null);
        this.prefetchPaymentConfig();
      },
      error: (err) => {
        if (this.isDestroyed) return;
        this.isInitiatingRequest = false;
        const parsed = extractApiErrorMessage(err, 'Unable to initiate your hotel reservation.');

        // If transient timeout/5xx, first verify whether backend actually persisted the reservation
        if (parsed.isTransient5xxOrNetwork && this.activeCandidateBookingRef) {
          this.bookingService.getHotelReservationDetails(this.activeCandidateBookingRef).subscribe({
            next: (detail) => {
              if (this.isDestroyed) return;
              const recovered = fromDetailResponse(detail);
              this.reservation = recovered;
              saveHotelPaymentCheckoutState({
                userId: this.currentUser?.id ?? null,
                bookingRef: recovered.bookingRef,
                hotelId: recovered.hotelId || this.staySummary!.hotelId,
                staySignature,
                allocationsSignature,
                lastKnownOrderId: null,
                updatedAt: new Date().toISOString()
              });
              const outcome = reconcileCheckoutState({ reservation: recovered });
              this.applyReconciledOutcome(outcome, null);
              this.prefetchPaymentConfig();
            },
            error: () => {
              if (this.isDestroyed) return;
              this.uiState = 'INITIATION_FAILED';
              this.errorBannerMessage = parsed.message;
              this.politeAnnouncement = parsed.message;
              this.activeCandidateBookingRef = null;
            }
          });
          return;
        }

        // Backend rejected initiation cleanly (validation/conflict); rotate candidate ref for manual retry
        this.activeCandidateBookingRef = null;
        this.uiState = 'INITIATION_FAILED';
        this.errorBannerMessage = parsed.message;
        this.politeAnnouncement = parsed.message;
      }
    });
  }

  retryInitiation(): void {
    this.activeCandidateBookingRef = null;
    this.initiateFreshReservation();
  }

  private prefetchPaymentConfig(): void {
    if (this.uiState !== 'PENDING_PAYMENT' && this.uiState !== 'PAYMENT_FAILED') {
      return;
    }
    this.bookingService
      .getPaymentConfig()
      .pipe(catchError(() => of(null)))
      .subscribe((cfg: PaymentConfigResponse | null) => {
        if (this.isDestroyed) return;
        if (cfg && cfg.keyId) {
          this.paymentConfig = cfg;
          this.configUnavailableWarning = '';
        }
      });
  }

  onPayWithRazorpay(): void {
    if (!this.canPayNow || !this.reservation) {
      return;
    }

    const expiresAtMs = parseBackendExpiresAtMs(this.reservation.expiresAt);
    if (expiresAtMs !== null && Date.now() >= expiresAtMs) {
      this.handleLocalCountdownExpired();
      return;
    }

    if (typeof (window as any).Razorpay !== 'function') {
      this.errorBannerMessage =
        'Razorpay Checkout script is unavailable. Please disable any script blockers or refresh and retry.';
      this.uiState = 'PAYMENT_FAILED';
      this.politeAnnouncement = this.errorBannerMessage;
      return;
    }

    this.isPaymentFlowActive = true;
    this.uiState = 'PROCESSING';
    this.statusBadgeLabel = 'Processing';
    this.processingLabel = 'Creating payment order...';
    this.errorBannerMessage = '';
    this.configUnavailableWarning = '';

    const bookingRef = this.reservation.bookingRef;
    const authoritativeAmount = Number(this.reservation.totalAmount);

    if (!Number.isFinite(authoritativeAmount) || authoritativeAmount <= 0) {
      this.isPaymentFlowActive = false;
      this.uiState = 'PAYMENT_FAILED';
      this.statusBadgeLabel = 'Pending Payment';
      this.errorBannerMessage = 'Authoritative reservation amount is invalid. Please refresh or contact support.';
      return;
    }

    const customerName =
      this.reservation.primaryGuest?.fullName ||
      this.primaryGuest?.fullName ||
      this.currentUser?.fullName ||
      '';
    const customerEmail =
      this.reservation.primaryGuest?.email ||
      this.primaryGuest?.email ||
      this.currentUser?.email ||
      '';
    const customerPhone =
      this.reservation.primaryGuest?.mobile ||
      this.primaryGuest?.mobile ||
      '';

    this.bookingService
      .createPaymentOrder({
        bookingRef,
        amount: authoritativeAmount,
        customerId: this.currentUser?.id,
        customerName,
        customerEmail,
        customerPhone,
        paymentMethod: 'RAZORPAY'
      })
      .subscribe({
        next: (orderRes: PaymentCreateOrderResponse) => {
          if (this.isDestroyed) return;
          if (!orderRes || !orderRes.orderId) {
            this.handleCreateOrderError({
              message: 'Payment gateway did not return a valid order identifier.'
            });
            return;
          }

          const existingSession = loadHotelPaymentCheckoutState();
          if (existingSession && existingSession.bookingRef === bookingRef) {
            saveHotelPaymentCheckoutState({
              ...existingSession,
              lastKnownOrderId: orderRes.orderId,
              updatedAt: new Date().toISOString()
            });
          }

          this.openRazorpayModal(orderRes, {
            name: customerName,
            email: customerEmail,
            contact: customerPhone
          });
        },
        error: (err) => {
          if (this.isDestroyed) return;
          this.handleCreateOrderError(err);
        }
      });
  }

  private handleCreateOrderError(err: any): void {
    this.isPaymentFlowActive = false;
    const parsed = extractApiErrorMessage(err, 'Unable to initialize Razorpay payment order.');

    if (parsed.isExpiredError && this.reservation) {
      this.reconcileAfterUnexpectedOutcome(this.reservation.bookingRef, parsed.message);
      return;
    }

    if (parsed.status === 503 || /not configured/i.test(parsed.message)) {
      this.configUnavailableWarning = parsed.message;
    }

    this.uiState = 'PAYMENT_FAILED';
    this.statusBadgeLabel = 'Pending Payment';
    this.errorBannerMessage = parsed.message;
    this.politeAnnouncement = parsed.message;
  }

  private openRazorpayModal(
    orderRes: PaymentCreateOrderResponse,
    prefill: { name: string; email: string; contact: string }
  ): void {
    const bookingRef = this.reservation!.bookingRef;
    const keyId = orderRes.keyId || this.paymentConfig?.keyId;
    if (!keyId) {
      this.isPaymentFlowActive = false;
      this.uiState = 'PAYMENT_FAILED';
      this.statusBadgeLabel = 'Pending Payment';
      this.errorBannerMessage = 'Payment gateway key configuration is missing. Please try again later.';
      return;
    }

    this.processingLabel = 'Awaiting Razorpay payment...';
    let callbackSettled = false;

    const options = {
      key: keyId,
      amount: orderRes.amountInPaise,
      currency: orderRes.currency || this.reservation?.currency || 'INR',
      name: 'Travel Easy',
      description: `${this.reservation?.hotelName || 'Hotel Reservation'} (${bookingRef})`,
      order_id: orderRes.orderId,
      prefill: {
        name: prefill.name,
        email: prefill.email,
        contact: prefill.contact
      },
      theme: {
        color: '#0D9488'
      },
      handler: (rzpResponse: any) => {
        callbackSettled = true;
        this.ngZone.run(() => {
          this.handleRazorpaySuccessCallback(bookingRef, orderRes.orderId, rzpResponse);
          this.cdr.markForCheck();
        });
      },
      modal: {
        ondismiss: () => {
          setTimeout(() => {
            if (callbackSettled || this.isDestroyed) return;
            this.ngZone.run(() => {
              this.handleModalDismissed(bookingRef);
              this.cdr.markForCheck();
            });
          }, 800);
        }
      }
    };

    try {
      const RazorpayCtor = (window as any).Razorpay;
      const rzp = new RazorpayCtor(options);
      if (typeof rzp.on === 'function') {
        rzp.on('payment.failed', (failRes: any) => {
          callbackSettled = true;
          this.ngZone.run(() => {
            this.isPaymentFlowActive = false;
            const errDesc =
              failRes?.error?.description ||
              failRes?.error?.reason ||
              'Payment could not be completed. You may retry before your reservation expires.';
            this.reconcileAfterUnexpectedOutcome(bookingRef, errDesc);
            this.cdr.markForCheck();
          });
        });
      }
      rzp.open();
    } catch (sdkErr: any) {
      this.isPaymentFlowActive = false;
      this.uiState = 'PAYMENT_FAILED';
      this.statusBadgeLabel = 'Pending Payment';
      this.errorBannerMessage = sdkErr?.message || 'Failed to launch Razorpay Checkout window.';
    }
  }

  private handleRazorpaySuccessCallback(
    bookingRef: string,
    fallbackOrderId: string,
    rzpResponse: any
  ): void {
    this.uiState = 'PROCESSING';
    this.statusBadgeLabel = 'Verifying Payment';
    this.processingLabel = 'Verifying payment & confirming rooms...';
    this.politeAnnouncement = 'Verifying payment signature and confirming your hotel reservation.';

    this.bookingService
      .verifyPaymentSignature({
        bookingRef,
        razorpayOrderId: rzpResponse?.razorpay_order_id || fallbackOrderId,
        razorpayPaymentId: rzpResponse?.razorpay_payment_id || '',
        razorpaySignature: rzpResponse?.razorpay_signature || ''
      })
      .subscribe({
        next: (verifyRes: PaymentVerificationResponse) => {
          if (this.isDestroyed) return;
          this.isPaymentFlowActive = false;

          // Fetch authoritative booking detail after verify response (graceful fallback if detail read is momentarily delayed)
          this.bookingService
            .getHotelReservationDetails(bookingRef)
            .pipe(catchError(() => of(null)))
            .subscribe((detailRes) => {
              if (this.isDestroyed) return;
              if (detailRes) {
                this.reservation = fromDetailResponse(detailRes, this.reservation);
              }
              const outcome = reconcileCheckoutState({
                reservation: this.reservation,
                verification: verifyRes,
                paymentTxn: verifyRes?.transaction || null
              });
              this.applyReconciledOutcome(outcome, verifyRes);
              this.cdr.markForCheck();
            });
        },
        error: (verifyErr) => {
          if (this.isDestroyed) return;
          this.isPaymentFlowActive = false;
          const parsed = extractApiErrorMessage(verifyErr, 'Payment verification encountered an issue.');

          // Always reconcile with Payment + Booking services before concluding failure vs pending confirmation
          this.reconcileAfterUnexpectedOutcome(
            bookingRef,
            parsed.isTransient5xxOrNetwork ? null : parsed.message,
            parsed.isTransient5xxOrNetwork
          );
        }
      });
  }

  private handleModalDismissed(bookingRef: string): void {
    this.isPaymentFlowActive = false;
    forkJoin({
      detail: this.bookingService.getHotelReservationDetails(bookingRef).pipe(catchError(() => of(null))),
      payment: this.bookingService.getPaymentByBookingRef(bookingRef).pipe(catchError(() => of(null)))
    }).subscribe(({ detail, payment }) => {
      if (this.isDestroyed) return;
      if (detail) {
        this.reservation = fromDetailResponse(detail, this.reservation);
      }
      const outcome = reconcileCheckoutState({
        reservation: this.reservation,
        paymentTxn: payment as PaymentTransactionDto | null
      });
      this.applyReconciledOutcome(outcome, null);
      if (outcome.uiState === 'PENDING_PAYMENT') {
        this.errorBannerMessage =
          'Razorpay checkout was closed before payment was completed. You can retry below before your hold expires.';
        this.politeAnnouncement = this.errorBannerMessage;
      }
      this.cdr.markForCheck();
    });
  }

  private reconcileAfterUnexpectedOutcome(
    bookingRef: string,
    fallbackErrorMessage: string | null,
    wasTransientVerifyError: boolean = false
  ): void {
    forkJoin({
      detail: this.bookingService.getHotelReservationDetails(bookingRef).pipe(catchError(() => of(null))),
      payment: this.bookingService.getPaymentByBookingRef(bookingRef).pipe(catchError(() => of(null)))
    }).subscribe(({ detail, payment }) => {
      if (this.isDestroyed) return;
      if (detail) {
        this.reservation = fromDetailResponse(detail, this.reservation);
      }
      const txn = (payment as PaymentTransactionDto | null) || null;
      const outcome = reconcileCheckoutState({
        reservation: this.reservation,
        paymentTxn: txn,
        lastPaymentError: fallbackErrorMessage
      });

      // If /verify timed out or 5xx'd and payment status wasn't immediately readable, still enter Confirmation Pending rather than false failure
      if (wasTransientVerifyError && outcome.uiState === 'PENDING_PAYMENT') {
        outcome.uiState = 'CONFIRMATION_PENDING';
        outcome.statusBadgeLabel = 'Confirmation Pending';
        outcome.title = 'Payment Received — Confirming Reservation';
        outcome.message =
          'We are checking your payment and booking status with our servers. Please check status below without paying again.';
      }

      this.applyReconciledOutcome(outcome, null);
      this.cdr.markForCheck();
    });
  }

  checkStatusNow(): void {
    if (!this.reservation || this.isReconcilingStatus) return;
    this.isReconcilingStatus = true;
    const bookingRef = this.reservation.bookingRef;

    forkJoin({
      detail: this.bookingService.getHotelReservationDetails(bookingRef).pipe(catchError(() => of(null))),
      payment: this.bookingService.getPaymentByBookingRef(bookingRef).pipe(catchError(() => of(null)))
    }).subscribe(({ detail, payment }) => {
      if (this.isDestroyed) return;
      this.isReconcilingStatus = false;
      if (detail) {
        this.reservation = fromDetailResponse(detail, this.reservation);
      }
      const outcome = reconcileCheckoutState({
        reservation: this.reservation,
        paymentTxn: payment as PaymentTransactionDto | null
      });
      this.applyReconciledOutcome(outcome, null);
      this.cdr.markForCheck();
    });
  }

  private applyReconciledOutcome(
    outcome: ReturnType<typeof reconcileCheckoutState>,
    verifyRes: PaymentVerificationResponse | null
  ): void {
    this.uiState = outcome.uiState;
    this.statusBadgeLabel = outcome.statusBadgeLabel;
    this.statusTitle = outcome.title;
    this.statusDescription = outcome.message;
    this.resolvedPaymentRef = outcome.paymentRef;
    this.resolvedTransactionRef = outcome.transactionRef;
    this.resolvedRefundId = outcome.refundId;
    this.resolvedRefundStatus = outcome.refundStatus;
    this.resolvedRefundAmount = outcome.refundAmount;

    if (outcome.uiState === 'PAYMENT_FAILED') {
      this.errorBannerMessage = outcome.message;
    } else if (outcome.uiState === 'CONFIRMED' || outcome.uiState === 'REFUND_INITIATED' || outcome.uiState === 'EXPIRED') {
      this.errorBannerMessage = '';
    }

    this.politeAnnouncement = `${outcome.title}. ${outcome.message}`;

    if (outcome.uiState === 'CONFIRMED') {
      this.stopCountdownTimer();
      this.stopPendingConfirmationPoll();
      clearReservationReviewState();
      clearGuestAllocationState();
      this.ensureConfirmedHotelDetailsLoaded();
      if (this.reservation && verifyRes?.verified !== false) {
        this.reservationConfirmed.emit(this.reservation);
      }
      return;
    }

    if (outcome.uiState === 'EXPIRED' || outcome.uiState === 'REFUND_INITIATED') {
      this.stopCountdownTimer();
      this.stopPendingConfirmationPoll();
      this.remainingSeconds = 0;
      this.countdownDisplay = '00:00';
      clearHotelPaymentCheckoutState();
      return;
    }

    if (outcome.uiState === 'CONFIRMATION_PENDING') {
      this.stopCountdownTimer();
      this.startPendingConfirmationPoll();
      return;
    }

    // Active hold states: PENDING_PAYMENT / PAYMENT_FAILED
    this.stopPendingConfirmationPoll();
    this.startCountdownTimer();
  }

  private startCountdownTimer(): void {
    this.stopCountdownTimer();
    this.updateCountdownTick();

    if (this.remainingSeconds <= 0) {
      return;
    }

    this.countdownSub = interval(1000).subscribe(() => {
      if (this.isDestroyed) return;
      this.updateCountdownTick();
    });
  }

  private updateCountdownTick(): void {
    if (!this.reservation) {
      this.countdownDisplay = '--:--';
      this.remainingSeconds = 0;
      return;
    }

    const expiresAtMs = parseBackendExpiresAtMs(this.reservation.expiresAt);
    this.expiresAtIsoAttr = expiresAtMs ? new Date(expiresAtMs).toISOString() : this.reservation.expiresAt;
    const prevSec = this.remainingSeconds;
    const nextSec = computeRemainingSeconds(expiresAtMs, Date.now());
    this.remainingSeconds = nextSec;
    this.countdownDisplay = formatCountdownMmSs(nextSec);
    this.accessibleCountdownText = formatAccessibleCountdown(nextSec);

    if (prevSec > 60 && nextSec <= 60 && nextSec > 0) {
      this.politeAnnouncement = 'One minute remaining to complete your reservation payment.';
    }

    if (
      nextSec <= 0 &&
      (this.uiState === 'PENDING_PAYMENT' || this.uiState === 'PAYMENT_FAILED') &&
      !this.isPaymentFlowActive
    ) {
      this.handleLocalCountdownExpired();
    }
  }

  private handleLocalCountdownExpired(): void {
    this.stopCountdownTimer();
    this.remainingSeconds = 0;
    this.countdownDisplay = '00:00';

    if (!this.reservation) {
      this.uiState = 'EXPIRED';
      return;
    }

    // Check authoritative backend status before finalizing expiry in case webhook just confirmed payment
    const bookingRef = this.reservation.bookingRef;
    forkJoin({
      detail: this.bookingService.getHotelReservationDetails(bookingRef).pipe(catchError(() => of(null))),
      payment: this.bookingService.getPaymentByBookingRef(bookingRef).pipe(catchError(() => of(null)))
    }).subscribe(({ detail, payment }) => {
      if (this.isDestroyed) return;
      if (detail) {
        this.reservation = fromDetailResponse(detail, this.reservation);
      }
      const outcome = reconcileCheckoutState({
        reservation: this.reservation,
        paymentTxn: payment as PaymentTransactionDto | null,
        nowMs: Date.now()
      });
      this.applyReconciledOutcome(outcome, null);
      this.cdr.markForCheck();
    });
  }

  private stopCountdownTimer(): void {
    if (this.countdownSub) {
      this.countdownSub.unsubscribe();
      this.countdownSub = null;
    }
  }

  private startPendingConfirmationPoll(): void {
    if (this.pendingAutoPollSub) return;
    this.pendingPollAttempts = 0;
    this.pendingAutoPollSub = interval(4000).subscribe(() => {
      if (this.isDestroyed || this.uiState !== 'CONFIRMATION_PENDING') {
        this.stopPendingConfirmationPoll();
        return;
      }
      this.pendingPollAttempts += 1;
      this.checkStatusNow();
      if (this.pendingPollAttempts >= 6) {
        this.stopPendingConfirmationPoll();
      }
    });
  }

  private ensureConfirmedHotelDetailsLoaded(): void {
    const targetHotelId = this.reservation?.hotelId;
    if (
      !targetHotelId ||
      targetHotelId <= 0 ||
      !this.hotelService ||
      this.loadedHotelDetailsId === targetHotelId ||
      this.fetchingHotelDetailsId === targetHotelId
    ) {
      return;
    }

    this.fetchingHotelDetailsId = targetHotelId;
    this.hotelService
      .getHotelDetails(targetHotelId)
      .pipe(catchError(() => of(null)))
      .subscribe((details) => {
        if (this.isDestroyed) return;
        this.fetchingHotelDetailsId = null;
        this.loadedHotelDetailsId = targetHotelId;
        if (details?.hotel) {
          this.hotelDetails = details;
        }
        this.cdr.markForCheck();
      });
  }

  private stopPendingConfirmationPoll(): void {
    if (this.pendingAutoPollSub) {
      this.pendingAutoPollSub.unsubscribe();
      this.pendingAutoPollSub = null;
    }
  }

  onBackToReviewClick(): void {
    if (this.uiState === 'PROCESSING') return;
    this.backToReview.emit();
  }

  onBackToSearchClick(): void {
    if (this.uiState === 'CONFIRMED' || this.uiState === 'EXPIRED' || this.uiState === 'REFUND_INITIATED') {
      clearHotelPaymentCheckoutState();
    }
    this.backToSearch.emit();
  }

  onSelectRoomsAgainClick(): void {
    if (this.uiState === 'CONFIRMED' || this.uiState === 'EXPIRED' || this.uiState === 'REFUND_INITIATED') {
      clearHotelPaymentCheckoutState();
    }
    const hotelId = this.reservation?.hotelId || this.staySummary?.hotelId || this.requestedHotelId;
    if (hotelId && hotelId > 0) {
      this.backToHotelDetails.emit(hotelId);
    } else {
      this.backToSearch.emit();
    }
  }

  onViewMyBookingsClick(): void {
    if (this.uiState === 'CONFIRMED' || this.uiState === 'EXPIRED' || this.uiState === 'REFUND_INITIATED') {
      clearHotelPaymentCheckoutState();
    }
    this.viewMyBookings.emit();
  }
}
