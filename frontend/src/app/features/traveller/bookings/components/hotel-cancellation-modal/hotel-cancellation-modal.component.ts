import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, Subscription, of } from 'rxjs';
import { catchError, finalize, timeout } from 'rxjs/operators';
import { BookingService } from '../../../../../services/booking.service';
import { DEFAULT_HOTEL_PLACEHOLDER } from '../../../../../shared/utils/image-url.util';
import { HotelBookingCardViewModel } from '../../models/hotel-booking-view.model';
import {
  HotelCancellationPolicyTierDisplay,
  HotelCancellationQuoteResponse,
  HotelCancellationRequest,
  HotelCancellationResponse,
  HotelCancellationResultViewModel,
  HotelCancellationStep
} from '../../models/hotel-cancellation.model';
import {
  HOTEL_CANCELLATION_POLICY_SUMMARY_LINES,
  HOTEL_CANCELLATION_REASON_MAX_LENGTH,
  HOTEL_CANCELLATION_REASON_OPTIONS,
  HOTEL_STANDARD_CHECKIN_SUMMARY,
  ParsedCancellationApiError,
  buildHotelCancellationResultFromBooking,
  buildHotelCancellationResultViewModel,
  extractSafeCancellationApiError,
  formatCancellationStaySummaryText,
  formatHotelCancellationCurrency,
  formatHotelCancellationPercentage,
  formatPolicyTierDisplay,
  formatQuoteEvaluationHint,
  resolveSubmittedCancellationReason
} from '../../utils/hotel-cancellation-presenter.util';

const CANCELLATION_SUBMIT_TIMEOUT_MS = 20000;

@Component({
  selector: 'app-hotel-cancellation-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <dialog
      #cancelDialog
      class="hotel-cancel-dialog"
      [attr.aria-labelledby]="'hotelCancelModalTitle'"
      [attr.closedby]="isActionLocked ? 'none' : 'any'"
      (cancel)="onNativeDialogCancel($event)"
      (close)="onNativeDialogClose()"
      (click)="onDialogBackdropClick($event)"
    >
      <div class="hotel-cancel-shell">
        <!-- Modal Header -->
        <header class="hotel-cancel-header">
          <div class="hotel-cancel-title-group">
            <div class="hotel-cancel-header-icon" aria-hidden="true">
              <i class="fa-solid fa-bed"></i>
            </div>
            <h2 id="hotelCancelModalTitle" class="hotel-cancel-title">
              {{ currentStep === 3 ? 'Cancellation Result' : 'Cancel Hotel Booking' }}
            </h2>
          </div>

          <button
            type="button"
            class="hotel-cancel-close-btn"
            [disabled]="isActionLocked"
            aria-label="Close modal"
            (click)="requestClose()"
          >
            <i class="fa-solid fa-xmark" aria-hidden="true"></i>
          </button>
        </header>

        <!-- In-dialog polite live region (inside top-layer dialog so screen readers announce updates) -->
        <div class="sr-only" aria-live="polite" aria-atomic="true">
          {{ liveAnnouncement }}
        </div>

        <div class="hotel-cancel-body">
          <!-- Booking Summary Card + 3-Step Indicator (Steps 1 & 2) -->
          @if (currentStep !== 3 && bookingCard) {
            <section class="hotel-cancel-summary-card" aria-label="Hotel booking summary">
              <div class="hotel-cancel-summary-left">
                <img
                  class="hotel-cancel-thumb"
                  [src]="bookingCard.thumbnail"
                  [alt]="bookingCard.hotelName"
                  (error)="onThumbnailError($event)"
                />
                <div class="hotel-cancel-summary-info">
                  <span class="hotel-cancel-service-pill">Hotel</span>
                  <div class="hotel-cancel-hotel-name">{{ bookingCard.hotelName }}</div>
                  <div class="hotel-cancel-hotel-meta">
                    {{ bookingCard.roomTypesSummary }} • {{ bookingCard.roomsCountLabel }} •
                    {{ bookingCard.guestsSummaryLabel }}
                  </div>
                </div>
              </div>

              <div class="hotel-cancel-summary-right">
                <div class="hotel-cancel-ref-label">Booking ID</div>
                <div class="hotel-cancel-ref-value">{{ bookingCard.bookingRef }}</div>
                <div class="hotel-cancel-stay-line">{{ staySummaryText }}</div>
              </div>
            </section>

            <nav class="hotel-cancel-stepper" aria-label="Cancellation progress">
              <ol class="hotel-cancel-stepper-list">
                <li
                  class="hotel-cancel-step-item"
                  [class.is-active]="currentStep === 1"
                  [class.is-completed]="currentStep > 1"
                  [attr.aria-current]="currentStep === 1 ? 'step' : null"
                >
                  <span class="hotel-cancel-step-badge" aria-hidden="true">
                    @if (currentStep > 1) {
                      <i class="fa-solid fa-check"></i>
                    } @else {
                      1
                    }
                  </span>
                  <span class="hotel-cancel-step-label">Cancellation Quote</span>
                </li>

                <li
                  class="hotel-cancel-step-item"
                  [class.is-active]="currentStep === 2"
                  [class.is-completed]="currentStep > 2"
                  [attr.aria-current]="currentStep === 2 ? 'step' : null"
                >
                  <span class="hotel-cancel-step-badge" aria-hidden="true">2</span>
                  <span class="hotel-cancel-step-label">Confirm Cancellation</span>
                </li>

                <li class="hotel-cancel-step-item" [class.is-active]="currentStep === 3">
                  <span class="hotel-cancel-step-badge" aria-hidden="true">3</span>
                  <span class="hotel-cancel-step-label">Result</span>
                </li>
              </ol>
            </nav>
          }

          <!-- ==================== STEP 1: CANCELLATION QUOTE ==================== -->
          @if (currentStep === 1) {
            <div class="hotel-cancel-step-pane" data-testid="cancel-step-1">
              @if (isLoadingQuote) {
                <div class="hotel-cancel-loading-box" role="status">
                  <div class="hotel-cancel-spinner" aria-hidden="true"></div>
                  <div>
                    <div class="hotel-cancel-loading-title">Calculating authoritative cancellation quote...</div>
                    <div class="hotel-cancel-loading-sub">
                      Checking policy tier, cutoff window, and eligible refund amount from the server.
                    </div>
                  </div>
                </div>
              } @else if (quoteError) {
                <div class="hotel-cancel-alert alert-danger" role="alert">
                  <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
                  <div class="hotel-cancel-alert-content">
                    <strong>Unable to load cancellation quote</strong>
                    <p>{{ quoteError.message }}</p>
                  </div>
                </div>

                <div class="hotel-cancel-footer">
                  <button
                    #step1SafeBackBtn
                    type="button"
                    class="hotel-cancel-btn hotel-cancel-btn-secondary"
                    autofocus
                    (click)="requestClose()"
                  >
                    Back
                  </button>
                  @if (!quoteError.isAuthError && !quoteError.isNotFound) {
                    <button
                      type="button"
                      class="hotel-cancel-btn hotel-cancel-btn-primary"
                      (click)="loadCancellationQuote()"
                    >
                      <i class="fa-solid fa-rotate-right" aria-hidden="true"></i>
                      Retry Quote
                    </button>
                  }
                </div>
              } @else if (quote) {
                @if (!quote.cancellable) {
                  <div class="hotel-cancel-alert alert-warning" role="alert">
                    <i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i>
                    <div class="hotel-cancel-alert-content">
                      <strong>
                        {{
                          quote.alreadyCancelled
                            ? 'Booking Already Cancelled'
                            : 'Booking Ineligible for Cancellation'
                        }}
                      </strong>
                      <p>
                        {{
                          quote.alreadyCancelled
                            ? 'This hotel reservation has already been cancelled. Check My Bookings for recorded refund and status details.'
                            : (quote.description || 'This booking cannot be cancelled in its current status.')
                        }}
                      </p>
                    </div>
                  </div>
                }

                <div class="hotel-cancel-quote-grid">
                  <!-- Left Panel: Quote Breakdown -->
                  <section class="hotel-cancel-panel hotel-cancel-quote-card">
                    <h3 #step1Heading tabindex="-1" class="hotel-cancel-panel-title">
                      <i class="fa-regular fa-file-lines" aria-hidden="true"></i>
                      Cancellation Quote
                    </h3>

                    <div class="hotel-cancel-quote-rows">
                      <div class="hotel-cancel-quote-row">
                        <span class="hotel-cancel-row-label">Applicable Policy</span>
                        <span class="hotel-cancel-row-value">{{ policyTierDisplay?.label || '—' }}</span>
                      </div>

                      <div class="hotel-cancel-quote-row">
                        <span class="hotel-cancel-row-label">Refund Percentage</span>
                        <span class="hotel-cancel-row-value">{{ formattedRefundPercentage }}</span>
                      </div>

                      <div class="hotel-cancel-quote-row">
                        <span class="hotel-cancel-row-label">Cancellation Fee</span>
                        <span class="hotel-cancel-row-value">{{ formattedCancellationFee }}</span>
                      </div>

                      <div
                        class="hotel-cancel-estimated-row"
                        [class.is-zero-refund]="quote.refundAmount <= 0"
                      >
                        <span class="hotel-cancel-estimated-label">Estimated Refund</span>
                        <span class="hotel-cancel-estimated-amount">{{ formattedRefundAmount }}</span>
                      </div>
                    </div>

                    @if (quoteEvaluationHint) {
                      <p class="hotel-cancel-quote-footnote">
                        <i class="fa-regular fa-clock" aria-hidden="true"></i>
                        {{ quoteEvaluationHint }}
                      </p>
                    }
                  </section>

                  <!-- Right Panel: Policy Summary -->
                  <aside class="hotel-cancel-panel hotel-cancel-policy-card" aria-label="Policy Summary">
                    <h3 class="hotel-cancel-policy-title">
                      <i class="fa-solid fa-circle-info" aria-hidden="true"></i>
                      Policy Summary
                    </h3>
                    <ul class="hotel-cancel-policy-list">
                      @for (rule of policySummaryLines; track rule) {
                        <li>{{ rule }}</li>
                      }
                    </ul>
                    <div class="hotel-cancel-policy-checkin-note">
                      {{ standardCheckInSummary }}
                    </div>
                  </aside>
                </div>

                <div class="hotel-cancel-footer">
                  <button
                    #step1SafeBackBtn
                    type="button"
                    class="hotel-cancel-btn hotel-cancel-btn-secondary"
                    autofocus
                    (click)="requestClose()"
                  >
                    Back
                  </button>

                  @if (quote.alreadyCancelled) {
                    <button
                      type="button"
                      class="hotel-cancel-btn hotel-cancel-btn-primary"
                      (click)="onViewBookingFromIneligibleQuote()"
                    >
                      View Booking
                    </button>
                  } @else {
                    <button
                      type="button"
                      class="hotel-cancel-btn hotel-cancel-btn-primary"
                      [disabled]="!quote.cancellable"
                      (click)="proceedToConfirmStep()"
                    >
                      Continue
                      <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>
                    </button>
                  }
                </div>
              }
            </div>
          }

          <!-- ==================== STEP 2: CONFIRM CANCELLATION ==================== -->
          @if (currentStep === 2 && quote) {
            <div class="hotel-cancel-step-pane" data-testid="cancel-step-2">
              <section class="hotel-cancel-panel hotel-cancel-confirm-card">
                <h3 #step2Heading tabindex="-1" class="hotel-cancel-panel-title">
                  <i class="fa-regular fa-file-lines" aria-hidden="true"></i>
                  Confirm Cancellation
                </h3>
                <p class="hotel-cancel-confirm-sub">
                  Please review the details before cancelling your booking.
                </p>

                <div class="hotel-cancel-metrics-grid">
                  <div class="hotel-cancel-metric-item">
                    <div class="hotel-cancel-metric-label">Estimated Refund</div>
                    <div
                      class="hotel-cancel-metric-value is-refund"
                      [class.is-zero]="quote.refundAmount <= 0"
                    >
                      {{ formattedRefundAmount }}
                    </div>
                  </div>

                  <div class="hotel-cancel-metric-item">
                    <div class="hotel-cancel-metric-label">Cancellation Fee</div>
                    <div class="hotel-cancel-metric-value">{{ formattedCancellationFee }}</div>
                  </div>

                  <div class="hotel-cancel-metric-item">
                    <div class="hotel-cancel-metric-label">Refund Percentage</div>
                    <div class="hotel-cancel-metric-value">{{ formattedRefundPercentage }}</div>
                  </div>

                  <div class="hotel-cancel-metric-item">
                    <div class="hotel-cancel-metric-label">Policy Tier</div>
                    <div class="hotel-cancel-metric-value">
                      <span
                        class="hotel-cancel-tier-pill"
                        [ngClass]="'tier-' + (policyTierDisplay?.tone || 'none')"
                      >
                        {{ policyTierDisplay?.shortBadgeLabel || 'Standard Policy' }}
                      </span>
                    </div>
                  </div>
                </div>
              </section>

              @if (quote.refundAmount <= 0 || !quote.eligibleForRefund) {
                <div class="hotel-cancel-alert alert-warning" role="note">
                  <i class="fa-solid fa-circle-info" aria-hidden="true"></i>
                  <div class="hotel-cancel-alert-content">
                    <strong>Non-refundable cancellation (₹0 refund)</strong>
                    <p>
                      Under the active cancellation policy tier, cancelling now yields no refund. Confirming
                      will still cancel your hotel reservation and release the rooms.
                    </p>
                  </div>
                </div>
              }

              <div class="hotel-cancel-reason-group">
                <label class="hotel-cancel-field-label" for="hotelCancelReasonSelect">
                  Cancellation Reason (Optional)
                </label>
                <div class="hotel-cancel-select-wrap">
                  <select
                    id="hotelCancelReasonSelect"
                    class="hotel-cancel-select"
                    [(ngModel)]="selectedReasonOption"
                    [disabled]="isSubmitting || isReconcilingTimeout"
                  >
                    @for (option of reasonOptions; track option.value) {
                      <option [value]="option.value">{{ option.label }}</option>
                    }
                  </select>
                </div>

                @if (selectedReasonOption === 'OTHER') {
                  <div class="hotel-cancel-custom-reason-wrap">
                    <label class="hotel-cancel-field-label" for="hotelCancelCustomReason">
                      Specify Reason (Optional)
                    </label>
                    <textarea
                      id="hotelCancelCustomReason"
                      class="hotel-cancel-textarea"
                      rows="2"
                      [maxlength]="maxReasonLength"
                      [(ngModel)]="customReasonText"
                      [disabled]="isSubmitting || isReconcilingTimeout"
                      placeholder="Enter reason (optional, up to 250 characters)..."
                    ></textarea>
                    <div class="hotel-cancel-char-count">
                      {{ customReasonText.length }} / {{ maxReasonLength }}
                    </div>
                  </div>
                }
              </div>

              @if (isReconcilingTimeout) {
                <div class="hotel-cancel-alert alert-info" role="status">
                  <div class="hotel-cancel-spinner sm" aria-hidden="true"></div>
                  <div class="hotel-cancel-alert-content">
                    <strong>Verifying booking status with the server...</strong>
                    <p>
                      The request took longer than expected. Checking whether your cancellation was recorded
                      so duplicate requests are not sent.
                    </p>
                  </div>
                </div>
              } @else if (submitError) {
                <div class="hotel-cancel-alert alert-danger" role="alert">
                  <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
                  <div class="hotel-cancel-alert-content">
                    <strong>Cancellation did not complete</strong>
                    <p>{{ submitError.message }}</p>
                    @if (submitError.isStatusChanged) {
                      <button
                        type="button"
                        class="hotel-cancel-inline-link-btn"
                        (click)="returnToQuoteAndRefresh()"
                      >
                        Refresh Booking &amp; Quote
                      </button>
                    }
                  </div>
                </div>
              }

              <div class="hotel-cancel-footer">
                <button
                  #step2SafeBackBtn
                  type="button"
                  class="hotel-cancel-btn hotel-cancel-btn-secondary"
                  [disabled]="isActionLocked"
                  (click)="backToQuoteStep()"
                >
                  Back
                </button>

                <button
                  type="button"
                  class="hotel-cancel-btn hotel-cancel-btn-danger"
                  [disabled]="!canSubmitCancellation"
                  (click)="submitCancellation()"
                >
                  @if (isSubmitting || isReconcilingTimeout) {
                    <span class="hotel-cancel-btn-spinner" aria-hidden="true"></span>
                    <span>Cancelling Booking...</span>
                  } @else {
                    <i class="fa-regular fa-circle-check" aria-hidden="true"></i>
                    <span>Confirm Cancellation</span>
                  }
                </button>
              </div>
            </div>
          }

          <!-- ==================== STEP 3: CANCELLATION RESULT ==================== -->
          @if (currentStep === 3 && result) {
            <div class="hotel-cancel-step-pane hotel-cancel-result-pane" data-testid="cancel-step-3">
              <div class="hotel-cancel-result-hero">
                <div class="hotel-cancel-result-circle" [ngClass]="'tone-' + result.headerTone" aria-hidden="true">
                  <i [ngClass]="result.headerIconClass"></i>
                </div>
                <h3 #step3Heading tabindex="-1" class="hotel-cancel-result-headline">
                  {{ result.headline }}
                </h3>
                <p class="hotel-cancel-result-subtitle">{{ result.subtitle }}</p>
              </div>

              <section class="hotel-cancel-result-card" aria-label="Cancellation outcome summary">
                <div class="hotel-cancel-result-card-top">
                  <div>
                    <div class="hotel-cancel-ref-label">Booking ID</div>
                    <div class="hotel-cancel-result-ref-row">
                      <span class="hotel-cancel-result-ref">{{ result.bookingRef }}</span>
                      <button
                        type="button"
                        class="hotel-cancel-copy-btn"
                        (click)="copyBookingReference(result.bookingRef)"
                      >
                        @if (copiedBookingRef) {
                          <i class="fa-solid fa-check" aria-hidden="true"></i>
                          <span>Copied</span>
                        } @else {
                          <i class="fa-regular fa-copy" aria-hidden="true"></i>
                          <span class="sr-only">Copy Booking ID</span>
                        }
                      </button>
                    </div>
                  </div>

                  <div class="hotel-cancel-result-refund-col">
                    <div class="hotel-cancel-ref-label">Refund Status</div>
                    <span class="hotel-cancel-status-pill" [ngClass]="'tone-' + result.refund.tone">
                      <i [ngClass]="result.refund.iconClass" aria-hidden="true"></i>
                      {{ result.refund.badgeLabel }}
                    </span>
                  </div>
                </div>

                <div class="hotel-cancel-result-rows">
                  <div class="hotel-cancel-result-row">
                    <span class="hotel-cancel-result-key">
                      <i class="fa-solid fa-users" aria-hidden="true"></i>
                      Refund Amount
                    </span>
                    <span class="hotel-cancel-result-val strong">{{ result.formattedRefundAmount }}</span>
                  </div>

                  @if (result.formattedCancellationFee && result.cancellationFee !== null && result.cancellationFee > 0) {
                    <div class="hotel-cancel-result-row">
                      <span class="hotel-cancel-result-key">
                        <i class="fa-solid fa-receipt" aria-hidden="true"></i>
                        Cancellation Fee
                      </span>
                      <span class="hotel-cancel-result-val">{{ result.formattedCancellationFee }}</span>
                    </div>
                  }

                  <div class="hotel-cancel-result-row">
                    <span class="hotel-cancel-result-key">
                      <i class="fa-regular fa-calendar" aria-hidden="true"></i>
                      Cancelled At
                    </span>
                    <span class="hotel-cancel-result-val">{{ result.cancelledAtText || 'Recorded by server' }}</span>
                  </div>

                  <div class="hotel-cancel-result-row">
                    <span class="hotel-cancel-result-key">
                      <i class="fa-solid fa-cube" aria-hidden="true"></i>
                      Room Inventory
                    </span>
                    <span
                      class="hotel-cancel-result-val inventory-val"
                      [ngClass]="'tone-' + result.inventory.tone"
                    >
                      {{ result.inventory.label }}
                    </span>
                  </div>

                  @if (result.refundId) {
                    <div class="hotel-cancel-result-row">
                      <span class="hotel-cancel-result-key">
                        <i class="fa-solid fa-hashtag" aria-hidden="true"></i>
                        Refund Reference
                      </span>
                      <span class="hotel-cancel-result-val mono">{{ result.refundId }}</span>
                    </div>
                  }

                  @if (result.cancellationReason) {
                    <div class="hotel-cancel-result-row">
                      <span class="hotel-cancel-result-key">
                        <i class="fa-regular fa-comment-dots" aria-hidden="true"></i>
                        Reason
                      </span>
                      <span class="hotel-cancel-result-val">{{ result.cancellationReason }}</span>
                    </div>
                  }
                </div>
              </section>

              <div
                class="hotel-cancel-banner"
                [ngClass]="'banner-' + result.refund.tone"
                role="status"
              >
                <i [ngClass]="result.refund.iconClass" aria-hidden="true"></i>
                <div>
                  {{ result.refund.bannerMessage }}
                </div>
              </div>

              @if (result.inventory.bannerMessage) {
                <div
                  class="hotel-cancel-banner"
                  [ngClass]="'banner-' + result.inventory.tone"
                  role="status"
                >
                  <i [ngClass]="result.inventory.iconClass" aria-hidden="true"></i>
                  <div>{{ result.inventory.bannerMessage }}</div>
                </div>
              }

              <div class="hotel-cancel-footer end-only">
                <button
                  type="button"
                  class="hotel-cancel-btn hotel-cancel-btn-primary"
                  (click)="onViewBookingsClicked()"
                >
                  <i class="fa-solid fa-plus" aria-hidden="true"></i>
                  <span>View Booking</span>
                </button>
              </div>
            </div>
          }
        </div>
      </div>
    </dialog>
  `,
  styles: [
    `
      ::ng-deep html:has(dialog.hotel-cancel-dialog[open]) {
        overflow: hidden;
      }

      .hotel-cancel-dialog {
        width: min(740px, calc(100vw - 24px));
        max-width: 740px;
        max-height: calc(100dvh - 32px);
        margin: auto;
        padding: 0;
        border: 1px solid #dbe4f0;
        border-radius: 16px;
        background: #ffffff;
        color: #0f172a;
        box-shadow: 0 24px 60px -12px rgba(15, 23, 42, 0.25);
        overscroll-behavior: contain;
        overflow: hidden;
      }

      .hotel-cancel-dialog::backdrop {
        background: rgba(15, 23, 42, 0.45);
        backdrop-filter: blur(2px);
      }

      .hotel-cancel-shell {
        display: flex;
        flex-direction: column;
        max-height: calc(100dvh - 34px);
      }

      .hotel-cancel-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 16px 24px;
        border-bottom: 1px solid #e2e8f0;
        background: #ffffff;
        flex-shrink: 0;
      }

      .hotel-cancel-title-group {
        display: flex;
        align-items: center;
        gap: 12px;
      }

      .hotel-cancel-header-icon {
        width: 34px;
        height: 34px;
        border-radius: 10px;
        background: #eff6ff;
        color: #1d4ed8;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 0.95rem;
      }

      .hotel-cancel-title {
        margin: 0;
        font-size: 1.15rem;
        font-weight: 700;
        color: #0f172a;
        letter-spacing: -0.01em;
      }

      .hotel-cancel-close-btn {
        width: 34px;
        height: 34px;
        border: none;
        border-radius: 8px;
        background: transparent;
        color: #64748b;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        font-size: 1.05rem;
        transition: background-color 0.15s ease, color 0.15s ease;
      }

      .hotel-cancel-close-btn:hover:not(:disabled) {
        background: #f1f5f9;
        color: #0f172a;
      }

      .hotel-cancel-close-btn:disabled {
        opacity: 0.45;
        cursor: not-allowed;
      }

      .hotel-cancel-body {
        padding: 20px 24px 22px;
        overflow-y: auto;
        overscroll-behavior: contain;
        display: flex;
        flex-direction: column;
        gap: 18px;
      }

      .hotel-cancel-summary-card {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 16px;
        padding: 14px 16px;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        background: #ffffff;
      }

      .hotel-cancel-summary-left {
        display: flex;
        align-items: center;
        gap: 14px;
        min-width: 0;
      }

      .hotel-cancel-thumb {
        width: 96px;
        height: 68px;
        border-radius: 8px;
        object-fit: cover;
        flex-shrink: 0;
        background: #f1f5f9;
        border: 1px solid #e2e8f0;
      }

      .hotel-cancel-summary-info {
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 4px;
      }

      .hotel-cancel-service-pill {
        align-self: flex-start;
        padding: 2px 9px;
        border-radius: 999px;
        font-size: 0.72rem;
        font-weight: 700;
        color: #4338ca;
        background: #e0e7ff;
      }

      .hotel-cancel-hotel-name {
        font-size: 1.02rem;
        font-weight: 700;
        color: #0f172a;
        line-height: 1.25;
      }

      .hotel-cancel-hotel-meta {
        font-size: 0.82rem;
        color: #475569;
      }

      .hotel-cancel-summary-right {
        text-align: right;
        flex-shrink: 0;
        padding-left: 14px;
        border-left: 1px solid #f1f5f9;
      }

      .hotel-cancel-ref-label {
        font-size: 0.74rem;
        color: #64748b;
        font-weight: 600;
      }

      .hotel-cancel-ref-value {
        font-size: 0.9rem;
        font-weight: 700;
        color: #0f172a;
        margin-top: 2px;
      }

      .hotel-cancel-stay-line {
        font-size: 0.8rem;
        color: #475569;
        margin-top: 4px;
      }

      .hotel-cancel-stepper {
        border-bottom: 1px solid #e2e8f0;
      }

      .hotel-cancel-stepper-list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 8px;
      }

      .hotel-cancel-step-item {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        padding: 8px 6px 11px;
        font-size: 0.85rem;
        font-weight: 600;
        color: #64748b;
        border-bottom: 2px solid transparent;
        margin-bottom: -1px;
      }

      .hotel-cancel-step-badge {
        width: 22px;
        height: 22px;
        border-radius: 999px;
        background: #e2e8f0;
        color: #475569;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 0.75rem;
        font-weight: 700;
        flex-shrink: 0;
      }

      .hotel-cancel-step-item.is-active {
        color: #1d4ed8;
        border-bottom-color: #2563eb;
      }

      .hotel-cancel-step-item.is-active .hotel-cancel-step-badge {
        background: #2563eb;
        color: #ffffff;
      }

      .hotel-cancel-step-item.is-completed {
        color: #475569;
      }

      .hotel-cancel-step-item.is-completed .hotel-cancel-step-badge {
        background: #dcfce7;
        color: #15803d;
        border: 1px solid #86efac;
      }

      .hotel-cancel-step-pane {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }

      .hotel-cancel-loading-box {
        display: flex;
        align-items: center;
        gap: 14px;
        padding: 24px 20px;
        border-radius: 12px;
        border: 1px solid #dbeafe;
        background: #f8fafc;
      }

      .hotel-cancel-loading-title {
        font-weight: 700;
        font-size: 0.95rem;
        color: #0f172a;
      }

      .hotel-cancel-loading-sub {
        font-size: 0.84rem;
        color: #475569;
        margin-top: 2px;
      }

      .hotel-cancel-spinner {
        width: 28px;
        height: 28px;
        border: 3px solid #dbeafe;
        border-top-color: #2563eb;
        border-radius: 50%;
        animation: hotel-cancel-spin 0.8s linear infinite;
        flex-shrink: 0;
      }

      .hotel-cancel-spinner.sm {
        width: 20px;
        height: 20px;
        border-width: 2.5px;
      }

      .hotel-cancel-btn-spinner {
        width: 14px;
        height: 14px;
        border: 2px solid rgba(255, 255, 255, 0.45);
        border-top-color: #ffffff;
        border-radius: 50%;
        animation: hotel-cancel-spin 0.8s linear infinite;
      }

      @keyframes hotel-cancel-spin {
        to {
          transform: rotate(360deg);
        }
      }

      .hotel-cancel-quote-grid {
        display: grid;
        grid-template-columns: 1.08fr 0.92fr;
        gap: 16px;
        align-items: stretch;
      }

      .hotel-cancel-panel {
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        padding: 16px;
        background: #ffffff;
      }

      .hotel-cancel-panel-title {
        margin: 0 0 12px;
        font-size: 0.96rem;
        font-weight: 700;
        color: #0f172a;
        display: flex;
        align-items: center;
        gap: 8px;
        outline: none;
      }

      .hotel-cancel-panel-title i {
        color: #475569;
      }

      .hotel-cancel-quote-rows {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }

      .hotel-cancel-quote-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 12px;
        padding: 9px 4px;
        border-bottom: 1px solid #f1f5f9;
        font-size: 0.88rem;
      }

      .hotel-cancel-row-label {
        color: #475569;
        font-weight: 500;
      }

      .hotel-cancel-row-value {
        color: #0f172a;
        font-weight: 700;
        text-align: right;
      }

      .hotel-cancel-estimated-row {
        margin-top: 10px;
        padding: 11px 14px;
        border-radius: 10px;
        background: #eff6ff;
        display: flex;
        justify-content: space-between;
        align-items: center;
      }

      .hotel-cancel-estimated-row.is-zero-refund {
        background: #f8fafc;
        border: 1px solid #e2e8f0;
      }

      .hotel-cancel-estimated-label {
        font-size: 0.92rem;
        font-weight: 700;
        color: #1d4ed8;
      }

      .hotel-cancel-estimated-row.is-zero-refund .hotel-cancel-estimated-label {
        color: #334155;
      }

      .hotel-cancel-estimated-amount {
        font-size: 1.15rem;
        font-weight: 800;
        color: #1d4ed8;
      }

      .hotel-cancel-estimated-row.is-zero-refund .hotel-cancel-estimated-amount {
        color: #334155;
      }

      .hotel-cancel-quote-footnote {
        margin: 10px 0 0;
        font-size: 0.78rem;
        color: #64748b;
        display: flex;
        align-items: center;
        gap: 6px;
      }

      .hotel-cancel-policy-card {
        background: #f4f8ff;
        border-color: #dbeafe;
        display: flex;
        flex-direction: column;
      }

      .hotel-cancel-policy-title {
        margin: 0 0 10px;
        font-size: 0.94rem;
        font-weight: 700;
        color: #1e3a8a;
        display: flex;
        align-items: center;
        gap: 8px;
      }

      .hotel-cancel-policy-title i {
        color: #2563eb;
      }

      .hotel-cancel-policy-list {
        margin: 0;
        padding-left: 18px;
        font-size: 0.84rem;
        line-height: 1.65;
        color: #1e293b;
      }

      .hotel-cancel-policy-checkin-note {
        margin-top: auto;
        padding-top: 12px;
        font-size: 0.82rem;
        font-weight: 600;
        color: #334155;
      }

      .hotel-cancel-confirm-sub {
        margin: -4px 0 14px;
        font-size: 0.86rem;
        color: #475569;
      }

      .hotel-cancel-metrics-grid {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 12px;
        padding: 14px;
        border-radius: 10px;
        background: #f8fafc;
        border: 1px solid #eef2f6;
      }

      .hotel-cancel-metric-label {
        font-size: 0.76rem;
        color: #475569;
        font-weight: 600;
        margin-bottom: 4px;
      }

      .hotel-cancel-metric-value {
        font-size: 1rem;
        font-weight: 700;
        color: #0f172a;
      }

      .hotel-cancel-metric-value.is-refund {
        color: #1d4ed8;
        font-size: 1.08rem;
      }

      .hotel-cancel-metric-value.is-refund.is-zero {
        color: #475569;
      }

      .hotel-cancel-tier-pill {
        display: inline-block;
        padding: 3px 10px;
        border-radius: 999px;
        font-size: 0.76rem;
        font-weight: 700;
      }

      .hotel-cancel-tier-pill.tier-full {
        background: #dcfce7;
        color: #15803d;
      }

      .hotel-cancel-tier-pill.tier-partial {
        background: #fef3c7;
        color: #92400e;
      }

      .hotel-cancel-tier-pill.tier-none {
        background: #fee2e2;
        color: #991b1b;
      }

      .hotel-cancel-reason-group {
        display: flex;
        flex-direction: column;
        gap: 8px;
      }

      .hotel-cancel-field-label {
        font-size: 0.85rem;
        font-weight: 700;
        color: #1e293b;
      }

      .hotel-cancel-select {
        width: 100%;
        padding: 10px 12px;
        border: 1px solid #cbd5e1;
        border-radius: 10px;
        font-size: 0.88rem;
        color: #0f172a;
        background: #ffffff;
        cursor: pointer;
      }

      .hotel-cancel-select:focus,
      .hotel-cancel-textarea:focus {
        outline: 2px solid #2563eb;
        outline-offset: 1px;
        border-color: #2563eb;
      }

      .hotel-cancel-custom-reason-wrap {
        margin-top: 6px;
        display: flex;
        flex-direction: column;
        gap: 6px;
      }

      .hotel-cancel-textarea {
        width: 100%;
        padding: 10px 12px;
        border: 1px solid #cbd5e1;
        border-radius: 10px;
        font-size: 0.86rem;
        color: #0f172a;
        resize: vertical;
        font-family: inherit;
      }

      .hotel-cancel-char-count {
        align-self: flex-end;
        font-size: 0.75rem;
        color: #64748b;
      }

      .hotel-cancel-alert,
      .hotel-cancel-banner {
        display: flex;
        align-items: flex-start;
        gap: 10px;
        padding: 12px 14px;
        border-radius: 10px;
        font-size: 0.84rem;
        line-height: 1.45;
      }

      .hotel-cancel-alert i,
      .hotel-cancel-banner i {
        margin-top: 2px;
        font-size: 0.95rem;
        flex-shrink: 0;
      }

      .hotel-cancel-alert-content p {
        margin: 3px 0 0;
      }

      .hotel-cancel-alert.alert-danger,
      .hotel-cancel-banner.banner-danger {
        background: #fef2f2;
        border: 1px solid #fecaca;
        color: #991b1b;
      }

      .hotel-cancel-alert.alert-warning,
      .hotel-cancel-banner.banner-warning {
        background: #fffbeb;
        border: 1px solid #fde68a;
        color: #92400e;
      }

      .hotel-cancel-alert.alert-info,
      .hotel-cancel-banner.banner-info {
        background: #eff6ff;
        border: 1px solid #bfdbfe;
        color: #1e40af;
      }

      .hotel-cancel-banner.banner-success {
        background: #f0fdf4;
        border: 1px solid #bbf7d0;
        color: #166534;
      }

      .hotel-cancel-banner.banner-neutral {
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        color: #334155;
      }

      .hotel-cancel-inline-link-btn {
        margin-top: 8px;
        padding: 0;
        border: none;
        background: none;
        color: #991b1b;
        font-weight: 700;
        font-size: 0.82rem;
        text-decoration: underline;
        cursor: pointer;
      }

      .hotel-cancel-result-hero {
        text-align: center;
        padding: 6px 0 2px;
      }

      .hotel-cancel-result-circle {
        width: 54px;
        height: 54px;
        border-radius: 999px;
        margin: 0 auto 12px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 1.45rem;
      }

      .hotel-cancel-result-circle.tone-success {
        background: #ecfdf5;
        color: #16a34a;
        border: 2px solid #86efac;
      }

      .hotel-cancel-result-circle.tone-warning {
        background: #fffbeb;
        color: #d97706;
        border: 2px solid #fcd34d;
      }

      .hotel-cancel-result-headline {
        margin: 0;
        font-size: 1.32rem;
        font-weight: 800;
        color: #0f172a;
        outline: none;
      }

      .hotel-cancel-result-subtitle {
        margin: 4px 0 0;
        font-size: 0.88rem;
        color: #475569;
      }

      .hotel-cancel-result-card {
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        padding: 16px 18px;
        background: #ffffff;
      }

      .hotel-cancel-result-card-top {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 14px;
        padding-bottom: 12px;
        border-bottom: 1px solid #eef2f6;
      }

      .hotel-cancel-result-ref-row {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-top: 2px;
      }

      .hotel-cancel-result-ref {
        font-size: 0.95rem;
        font-weight: 800;
        color: #0f172a;
      }

      .hotel-cancel-copy-btn {
        border: none;
        background: transparent;
        color: #475569;
        padding: 2px 6px;
        border-radius: 6px;
        font-size: 0.78rem;
        font-weight: 600;
        display: inline-flex;
        align-items: center;
        gap: 4px;
        cursor: pointer;
      }

      .hotel-cancel-copy-btn:hover {
        background: #f1f5f9;
        color: #0f172a;
      }

      .hotel-cancel-result-refund-col {
        text-align: right;
      }

      .hotel-cancel-status-pill {
        margin-top: 4px;
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 4px 11px;
        border-radius: 999px;
        font-size: 0.78rem;
        font-weight: 700;
      }

      .hotel-cancel-status-pill.tone-info {
        background: #eff6ff;
        color: #1d4ed8;
        border: 1px solid #bfdbfe;
      }

      .hotel-cancel-status-pill.tone-success {
        background: #ecfdf5;
        color: #15803d;
        border: 1px solid #a7f3d0;
      }

      .hotel-cancel-status-pill.tone-warning {
        background: #fffbeb;
        color: #b45309;
        border: 1px solid #fde68a;
      }

      .hotel-cancel-status-pill.tone-danger {
        background: #fef2f2;
        color: #b91c1c;
        border: 1px solid #fecaca;
      }

      .hotel-cancel-status-pill.tone-neutral {
        background: #f1f5f9;
        color: #334155;
        border: 1px solid #cbd5e1;
      }

      .hotel-cancel-result-rows {
        display: flex;
        flex-direction: column;
      }

      .hotel-cancel-result-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 12px;
        padding: 10px 0;
        border-bottom: 1px solid #f1f5f9;
        font-size: 0.87rem;
      }

      .hotel-cancel-result-row:last-child {
        border-bottom: none;
        padding-bottom: 2px;
      }

      .hotel-cancel-result-key {
        color: #475569;
        display: inline-flex;
        align-items: center;
        gap: 10px;
      }

      .hotel-cancel-result-key i {
        width: 16px;
        text-align: center;
        color: #64748b;
      }

      .hotel-cancel-result-val {
        color: #0f172a;
        font-weight: 600;
        text-align: right;
      }

      .hotel-cancel-result-val.strong {
        font-weight: 800;
        font-size: 0.95rem;
      }

      .hotel-cancel-result-val.mono {
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        font-size: 0.82rem;
      }

      .hotel-cancel-result-val.inventory-val.tone-warning {
        color: #b45309;
      }

      .hotel-cancel-footer {
        display: flex;
        justify-content: flex-end;
        align-items: center;
        gap: 10px;
        padding-top: 6px;
      }

      .hotel-cancel-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        min-height: 40px;
        padding: 9px 20px;
        border-radius: 10px;
        font-size: 0.88rem;
        font-weight: 700;
        cursor: pointer;
        border: 1px solid transparent;
        transition: background-color 0.15s ease, border-color 0.15s ease, opacity 0.15s ease;
      }

      .hotel-cancel-btn:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }

      .hotel-cancel-btn-secondary {
        background: #ffffff;
        border-color: #cbd5e1;
        color: #0f172a;
      }

      .hotel-cancel-btn-secondary:hover:not(:disabled) {
        background: #f8fafc;
        border-color: #94a3b8;
      }

      .hotel-cancel-btn-primary {
        background: #2563eb;
        color: #ffffff;
      }

      .hotel-cancel-btn-primary:hover:not(:disabled) {
        background: #1d4ed8;
      }

      .hotel-cancel-btn-danger {
        background: #dc2626;
        color: #ffffff;
      }

      .hotel-cancel-btn-danger:hover:not(:disabled) {
        background: #b91c1c;
      }

      .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        white-space: nowrap;
        border: 0;
      }

      @media (max-width: 680px) {
        .hotel-cancel-dialog {
          width: calc(100vw - 16px);
          max-height: calc(100dvh - 16px);
          border-radius: 14px;
        }

        .hotel-cancel-header,
        .hotel-cancel-body {
          padding-left: 16px;
          padding-right: 16px;
        }

        .hotel-cancel-summary-card {
          flex-direction: column;
          align-items: stretch;
        }

        .hotel-cancel-summary-right {
          text-align: left;
          padding-left: 0;
          padding-top: 10px;
          border-left: none;
          border-top: 1px solid #f1f5f9;
        }

        .hotel-cancel-quote-grid {
          grid-template-columns: 1fr;
        }

        .hotel-cancel-metrics-grid {
          grid-template-columns: repeat(2, minmax(0, 1fr));
        }

        .hotel-cancel-stepper-list {
          gap: 4px;
        }

        .hotel-cancel-step-item {
          font-size: 0.76rem;
        }

        .hotel-cancel-result-card-top {
          flex-direction: column;
          align-items: flex-start;
        }

        .hotel-cancel-result-refund-col {
          text-align: left;
        }

        .hotel-cancel-footer {
          flex-direction: column-reverse;
          align-items: stretch;
        }

        .hotel-cancel-btn {
          width: 100%;
        }
      }
    `
  ]
})
export class HotelCancellationModalComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input() showModal: boolean = false;
  @Input() bookingCard: HotelBookingCardViewModel | null = null;

  @Output() closeModal = new EventEmitter<void>();
  @Output() bookingUpdated = new EventEmitter<HotelCancellationResultViewModel>();
  @Output() viewBookingAfterCancel = new EventEmitter<HotelCancellationResultViewModel | null>();
  @Output() refreshBookings = new EventEmitter<void>();

  @ViewChild('cancelDialog') dialogRef?: ElementRef<HTMLDialogElement>;
  @ViewChild('step1Heading') step1HeadingRef?: ElementRef<HTMLElement>;
  @ViewChild('step2Heading') step2HeadingRef?: ElementRef<HTMLElement>;
  @ViewChild('step3Heading') step3HeadingRef?: ElementRef<HTMLElement>;

  readonly policySummaryLines = HOTEL_CANCELLATION_POLICY_SUMMARY_LINES;
  readonly standardCheckInSummary = HOTEL_STANDARD_CHECKIN_SUMMARY;
  readonly reasonOptions = HOTEL_CANCELLATION_REASON_OPTIONS;
  readonly maxReasonLength = HOTEL_CANCELLATION_REASON_MAX_LENGTH;

  currentStep: HotelCancellationStep = 1;
  quote: HotelCancellationQuoteResponse | null = null;
  quoteError: ParsedCancellationApiError | null = null;
  submitError: ParsedCancellationApiError | null = null;
  result: HotelCancellationResultViewModel | null = null;

  isLoadingQuote: boolean = false;
  isSubmitting: boolean = false;
  isReconcilingTimeout: boolean = false;

  selectedReasonOption: string = '';
  customReasonText: string = '';
  copiedBookingRef: boolean = false;
  liveAnnouncement: string = '';

  private viewReady = false;
  private activeSubscription?: Subscription;
  private copiedTimer?: ReturnType<typeof setTimeout>;
  private activeBookingInstanceKey: string | null = null;

  constructor(private bookingService: BookingService) {}

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.syncDialogVisibility();
  }

  ngOnChanges(changes: SimpleChanges): void {
    const showChanged = Boolean(changes['showModal']);
    const cardChanged = Boolean(changes['bookingCard']);

    if (!this.showModal) {
      this.activeSubscription?.unsubscribe();
      this.activeBookingInstanceKey = null;
      this.closeNativeDialog();
      this.resetInternalState();
      return;
    }

    const nextKey = this.bookingCard
      ? `${this.bookingCard.id ?? ''}|${this.bookingCard.bookingRef}`
      : null;

    if ((showChanged && this.showModal) || (cardChanged && nextKey && nextKey !== this.activeBookingInstanceKey)) {
      this.activeBookingInstanceKey = nextKey;
      this.resetInternalState();
      this.syncDialogVisibility();
      if (this.bookingCard) {
        this.loadCancellationQuote();
      }
    }
  }

  ngOnDestroy(): void {
    this.activeSubscription?.unsubscribe();
    if (this.copiedTimer) {
      clearTimeout(this.copiedTimer);
    }
    this.closeNativeDialog();
  }

  get isActionLocked(): boolean {
    return this.isSubmitting || this.isReconcilingTimeout;
  }

  get canSubmitCancellation(): boolean {
    return Boolean(
      this.quote &&
        this.quote.cancellable &&
        !this.quote.alreadyCancelled &&
        !this.isSubmitting &&
        !this.isReconcilingTimeout &&
        !this.submitError?.isStatusChanged
    );
  }

  get staySummaryText(): string {
    return formatCancellationStaySummaryText(this.bookingCard, this.quote);
  }

  get policyTierDisplay(): HotelCancellationPolicyTierDisplay | null {
    return this.quote ? formatPolicyTierDisplay(this.quote.policyTier) : null;
  }

  get formattedRefundPercentage(): string {
    return formatHotelCancellationPercentage(this.quote?.refundPercentage);
  }

  get formattedCancellationFee(): string {
    return formatHotelCancellationCurrency(
      this.quote?.cancellationFee,
      this.bookingCard?.currency || 'INR'
    );
  }

  get formattedRefundAmount(): string {
    return formatHotelCancellationCurrency(
      this.quote?.refundAmount,
      this.bookingCard?.currency || 'INR'
    );
  }

  get quoteEvaluationHint(): string | null {
    return formatQuoteEvaluationHint(this.quote);
  }

  loadCancellationQuote(): void {
    if (!this.bookingCard) {
      return;
    }

    this.activeSubscription?.unsubscribe();
    this.isLoadingQuote = true;
    this.quoteError = null;
    this.submitError = null;
    this.liveAnnouncement = 'Loading hotel cancellation quote.';

    this.activeSubscription = this.fetchAuthoritativeQuote$()
      .pipe(
        timeout(CANCELLATION_SUBMIT_TIMEOUT_MS),
        finalize(() => {
          this.isLoadingQuote = false;
        })
      )
      .subscribe({
        next: quote => {
          this.quote = quote;
          this.quoteError = null;
          this.liveAnnouncement = quote.cancellable
            ? `Cancellation quote loaded. Estimated refund is ${this.formattedRefundAmount}.`
            : quote.alreadyCancelled
              ? 'This booking has already been cancelled.'
              : 'This booking is not eligible for cancellation.';
        },
        error: err => {
          this.quote = null;
          this.quoteError = extractSafeCancellationApiError(err);
          this.liveAnnouncement = this.quoteError.message;
        }
      });
  }

  proceedToConfirmStep(): void {
    if (!this.quote || !this.quote.cancellable || this.quote.alreadyCancelled) {
      return;
    }
    this.submitError = null;
    this.currentStep = 2;
    this.liveAnnouncement = 'Step 2 of 3: Confirm Cancellation.';
    this.focusElementSoon(() => this.step2HeadingRef?.nativeElement);
  }

  backToQuoteStep(): void {
    if (this.isActionLocked) {
      return;
    }
    this.submitError = null;
    this.currentStep = 1;
    this.liveAnnouncement = 'Returned to Step 1 of 3: Cancellation Quote.';
    this.focusElementSoon(() => this.step1HeadingRef?.nativeElement);
  }

  returnToQuoteAndRefresh(): void {
    if (this.isActionLocked) {
      return;
    }
    this.submitError = null;
    this.currentStep = 1;
    this.loadCancellationQuote();
  }

  submitCancellation(): void {
    if (!this.canSubmitCancellation || !this.bookingCard || !this.quote) {
      return;
    }

    this.isSubmitting = true;
    this.submitError = null;
    this.liveAnnouncement = 'Submitting hotel cancellation request.';

    const reason = resolveSubmittedCancellationReason(
      this.selectedReasonOption,
      this.customReasonText
    );
    const payload: HotelCancellationRequest = reason ? { reason } : {};

    this.activeSubscription?.unsubscribe();
    this.activeSubscription = this.executeCancellation$(payload)
      .pipe(timeout(CANCELLATION_SUBMIT_TIMEOUT_MS))
      .subscribe({
        next: response => {
          this.isSubmitting = false;
          const resultVm = buildHotelCancellationResultViewModel(
            response,
            this.bookingCard?.currency || 'INR'
          );
          this.transitionToResultStep(resultVm);
        },
        error: err => {
          this.isSubmitting = false;
          const parsedError = extractSafeCancellationApiError(err);
          if (parsedError.isTimeoutOrUncertain) {
            this.reconcileAfterUncertainSubmitOutcome(parsedError);
            return;
          }

          this.submitError = parsedError;
          this.liveAnnouncement = parsedError.message;
          if (parsedError.isStatusChanged) {
            // Ask dashboard to reload authoritative booking statuses instead of guessing state
            this.refreshBookings.emit();
          }
        }
      });
  }

  requestClose(): void {
    if (this.isActionLocked) {
      return;
    }
    this.closeNativeDialog();
    this.closeModal.emit();
  }

  onNativeDialogCancel(event: Event): void {
    if (this.isActionLocked) {
      event.preventDefault();
    }
  }

  onNativeDialogClose(): void {
    if (this.showModal) {
      this.closeModal.emit();
    }
  }

  onDialogBackdropClick(event: MouseEvent): void {
    if (this.isActionLocked) {
      return;
    }
    const dialog = this.dialogRef?.nativeElement;
    // Ignore child element clicks and keyboard/synthetic activations where clientX/clientY are (0, 0)
    if (!dialog || event.target !== dialog || (event.clientX === 0 && event.clientY === 0)) {
      return;
    }
    const rect = dialog.getBoundingClientRect();
    const clickedInside =
      event.clientX >= rect.left &&
      event.clientX <= rect.right &&
      event.clientY >= rect.top &&
      event.clientY <= rect.bottom;
    if (!clickedInside) {
      this.requestClose();
    }
  }

  onViewBookingsClicked(): void {
    const completedResult = this.result;
    this.closeNativeDialog();
    this.viewBookingAfterCancel.emit(completedResult);
  }

  onViewBookingFromIneligibleQuote(): void {
    this.closeNativeDialog();
    this.viewBookingAfterCancel.emit(null);
  }

  copyBookingReference(bookingRef: string): void {
    if (!bookingRef || bookingRef === '—') {
      return;
    }
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      navigator.clipboard
        .writeText(bookingRef)
        .then(() => {
          this.markBookingRefCopied();
        })
        .catch(() => {
          this.fallbackCopyText(bookingRef);
        });
      return;
    }
    this.fallbackCopyText(bookingRef);
  }

  onThumbnailError(event: Event): void {
    const img = event.target as HTMLImageElement | null;
    if (img && img.src !== DEFAULT_HOTEL_PLACEHOLDER) {
      img.src = DEFAULT_HOTEL_PLACEHOLDER;
    }
  }

  private reconcileAfterUncertainSubmitOutcome(initialError: ParsedCancellationApiError): void {
    if (!this.bookingCard) {
      this.submitError = initialError;
      return;
    }

    this.isReconcilingTimeout = true;
    this.liveAnnouncement = 'Checking booking status with the server.';

    const probe$ =
      typeof this.bookingCard.id === 'number' && this.bookingCard.id > 0
        ? this.bookingService.getBookingById(this.bookingCard.id)
        : this.bookingService.getBookingByRef(this.bookingCard.bookingRef);

    this.activeSubscription = probe$
      .pipe(
        timeout(12000),
        catchError(() => of(null)),
        finalize(() => {
          this.isReconcilingTimeout = false;
        })
      )
      .subscribe(verifiedBooking => {
        const verifiedStatus = (verifiedBooking?.status || '').toUpperCase();
        if (verifiedStatus === 'CANCELLED') {
          const reconciledVm = buildHotelCancellationResultFromBooking(
            verifiedBooking,
            this.bookingCard?.bookingRef || '',
            this.bookingCard?.currency || 'INR'
          );
          this.transitionToResultStep(reconciledVm);
          return;
        }

        if (verifiedBooking && verifiedStatus && verifiedStatus !== 'CONFIRMED') {
          this.submitError = {
            message: `This booking is now in ${verifiedStatus.replace(/_/g, ' ')} status and can no longer be cancelled.`,
            httpStatus: initialError.httpStatus,
            isTimeoutOrUncertain: false,
            isAuthError: false,
            isStatusChanged: true,
            isNotFound: false
          };
          this.liveAnnouncement = this.submitError.message;
          this.refreshBookings.emit();
          return;
        }

        if (verifiedBooking && verifiedStatus === 'CONFIRMED') {
          this.submitError = {
            message:
              'Your booking cancellation did not complete before timing out. We verified with the server that your booking is still confirmed—review the refreshed quote if you wish to try again.',
            httpStatus: initialError.httpStatus,
            isTimeoutOrUncertain: false,
            isAuthError: false,
            isStatusChanged: false,
            isNotFound: false
          };
          this.liveAnnouncement = this.submitError.message;
          this.refreshQuoteSilently();
          return;
        }

        // Probe itself could not reach server: report uncertainty honestly without guessing state
        this.submitError = {
          message:
            'We could not verify whether the cancellation completed due to a connection interruption. Please check My Bookings before retrying.',
          httpStatus: initialError.httpStatus,
          isTimeoutOrUncertain: true,
          isAuthError: false,
          isStatusChanged: false,
          isNotFound: false
        };
        this.liveAnnouncement = this.submitError.message;
      });
  }

  private refreshQuoteSilently(): void {
    if (!this.bookingCard) {
      return;
    }
    this.fetchAuthoritativeQuote$()
      .pipe(
        timeout(10000),
        catchError(() => of(null))
      )
      .subscribe(freshQuote => {
        if (freshQuote) {
          this.quote = freshQuote;
          if (freshQuote.alreadyCancelled || !freshQuote.cancellable) {
            this.submitError = {
              message: freshQuote.alreadyCancelled
                ? 'This booking has already been cancelled on the server.'
                : freshQuote.description || 'This booking is no longer eligible for cancellation.',
              httpStatus: 400,
              isTimeoutOrUncertain: false,
              isAuthError: false,
              isStatusChanged: true,
              isNotFound: false
            };
            this.refreshBookings.emit();
          }
        }
      });
  }

  private transitionToResultStep(resultVm: HotelCancellationResultViewModel): void {
    this.result = resultVm;
    this.currentStep = 3;
    this.submitError = null;
    this.liveAnnouncement = `${resultVm.headline}. Refund status: ${resultVm.refund.badgeLabel}. Room inventory: ${resultVm.inventory.label}.`;
    this.bookingUpdated.emit(resultVm);
    this.focusElementSoon(() => this.step3HeadingRef?.nativeElement);
  }

  private fetchAuthoritativeQuote$(): Observable<HotelCancellationQuoteResponse> {
    const card = this.bookingCard!;
    if (typeof card.id === 'number' && card.id > 0) {
      return this.bookingService.getHotelCancellationQuoteById(card.id);
    }
    return this.bookingService.getHotelCancellationQuoteByRef(card.bookingRef);
  }

  private executeCancellation$(
    payload: HotelCancellationRequest
  ): Observable<HotelCancellationResponse> {
    const card = this.bookingCard!;
    if (typeof card.id === 'number' && card.id > 0) {
      return this.bookingService.cancelHotelBookingById(card.id, payload);
    }
    return this.bookingService.cancelHotelBookingByRef(card.bookingRef, payload);
  }

  private syncDialogVisibility(): void {
    if (!this.viewReady || !this.showModal) {
      return;
    }
    const dialog = this.dialogRef?.nativeElement;
    if (dialog && !dialog.open) {
      try {
        dialog.showModal();
      } catch {
        // Guard against duplicate open errors in fast change-detection cycles
      }
    }
  }

  private closeNativeDialog(): void {
    const dialog = this.dialogRef?.nativeElement;
    if (dialog && dialog.open) {
      try {
        dialog.close();
      } catch {
        // Ignore already-closed dialog states
      }
    }
  }

  private resetInternalState(): void {
    this.currentStep = 1;
    this.quote = null;
    this.quoteError = null;
    this.submitError = null;
    this.result = null;
    this.isLoadingQuote = false;
    this.isSubmitting = false;
    this.isReconcilingTimeout = false;
    this.selectedReasonOption = '';
    this.customReasonText = '';
    this.copiedBookingRef = false;
    this.liveAnnouncement = '';
  }

  private markBookingRefCopied(): void {
    this.copiedBookingRef = true;
    this.liveAnnouncement = 'Booking ID copied to clipboard.';
    if (this.copiedTimer) {
      clearTimeout(this.copiedTimer);
    }
    this.copiedTimer = setTimeout(() => {
      this.copiedBookingRef = false;
    }, 2000);
  }

  private fallbackCopyText(value: string): void {
    if (typeof document === 'undefined') {
      return;
    }
    const input = document.createElement('textarea');
    input.value = value;
    input.style.position = 'fixed';
    input.style.opacity = '0';
    document.body.appendChild(input);
    input.select();
    try {
      document.execCommand('copy');
      this.markBookingRefCopied();
    } finally {
      document.body.removeChild(input);
    }
  }

  private focusElementSoon(getter: () => HTMLElement | undefined): void {
    queueMicrotask(() => {
      const target = getter();
      target?.focus();
    });
  }
}
