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
  QueryList,
  SimpleChanges,
  ViewChild,
  ViewChildren
} from '@angular/core';
import {
  HotelDetailsResponseDto,
  HotelReservationDetailResponse,
  PaymentTransactionDto
} from '../../../../../models/hotel.model';
import { DEFAULT_HOTEL_PLACEHOLDER } from '../../../../../shared/utils/image-url.util';
import {
  HotelBookingCardViewModel,
  HotelBookingDetailsViewModel,
  HotelModalTabId
} from '../../models/hotel-booking-view.model';
import { buildHotelBookingDetailsViewModel } from '../../utils/hotel-booking-presenter.util';

interface HotelModalTabDefinition {
  id: HotelModalTabId;
  label: string;
  iconClass: string;
}

@Component({
  selector: 'app-hotel-booking-details-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <dialog
      #hotelDialog
      class="hotel-details-dialog"
      aria-labelledby="hotel-modal-title"
      [attr.closedby]="'any'"
      (cancel)="onNativeDialogCancel($event)"
      (close)="onNativeDialogClose()"
    >
      <div *ngIf="detailsVm as vm" class="hotel-modal-shell">
        <!-- Fixed Modal Header -->
        <header class="hotel-modal-header">
          <div class="hotel-modal-title-group">
            <span class="hotel-modal-icon-circle" aria-hidden="true">
              <i class="fa-solid fa-bed"></i>
            </span>
            <h2 id="hotel-modal-title" class="hotel-modal-title">Hotel Booking Details</h2>
          </div>

          <button
            type="button"
            class="hotel-modal-close-btn"
            autofocus
            aria-label="Close hotel booking details"
            (click)="requestClose()"
          >
            <i class="fa-solid fa-xmark" aria-hidden="true"></i>
          </button>
        </header>

        <!-- Fixed Hotel Summary Card & Tab Strip -->
        <div class="hotel-modal-pinned-top">
          <section class="hotel-summary-card" aria-label="Hotel reservation summary">
            <div class="summary-left">
              <div class="summary-photo-wrap">
                <img
                  [src]="vm.hotelImageUrl"
                  (error)="onImgError($event)"
                  class="summary-photo"
                  [alt]="vm.hotelName"
                />
              </div>

              <div class="summary-hotel-meta">
                <div class="mb-1">
                  <span class="modal-hotel-pill">Hotel</span>
                </div>
                <h3 class="summary-hotel-name">{{ vm.hotelName }}</h3>
                <div *ngIf="vm.locationText" class="summary-hotel-location">
                  <i class="fa-solid fa-location-dot" aria-hidden="true"></i>
                  <span>{{ vm.locationText }}</span>
                </div>
                <div *ngIf="vm.reviewSummary as rs" class="summary-hotel-rating" [attr.aria-label]="rs.formattedRating + ' out of 5 stars from ' + rs.formattedReviewCount + ' reviews'">
                  <span class="rating-stars" aria-hidden="true">
                    <i
                      *ngFor="let star of rs.stars"
                      class="fa-star"
                      [class.fa-solid]="star === 'full' || star === 'half'"
                      [class.fa-star-half-stroke]="star === 'half'"
                      [class.fa-regular]="star === 'empty'"
                    ></i>
                  </span>
                  <span class="rating-caption">({{ rs.formattedRating }} • {{ rs.formattedReviewCount }} reviews)</span>
                </div>
              </div>
            </div>

            <div class="summary-right">
              <div class="summary-ref-stack">
                <div class="summary-kv-row">
                  <i class="fa-regular fa-calendar-check summary-kv-icon" aria-hidden="true"></i>
                  <div class="summary-kv-body">
                    <span class="summary-kv-label">Booking ID</span>
                    <div class="booking-id-copy-row">
                      <span class="summary-kv-value booking-id-value">{{ vm.bookingRef }}</span>
                      <button
                        type="button"
                        class="btn-copy-booking-id"
                        [attr.aria-label]="copiedBookingRef ? 'Booking ID copied' : 'Copy booking ID'"
                        [title]="copiedBookingRef ? 'Copied!' : 'Copy Booking ID'"
                        (click)="copyBookingRef(vm.bookingRef)"
                      >
                        <i
                          [class]="copiedBookingRef ? 'fa-solid fa-check text-success' : 'fa-regular fa-copy'"
                          aria-hidden="true"
                        ></i>
                        <span *ngIf="copiedBookingRef" class="copied-feedback-text">Copied</span>
                      </button>
                    </div>
                  </div>
                </div>

                <div *ngIf="vm.bookedOnText" class="summary-kv-row">
                  <i class="fa-regular fa-calendar summary-kv-icon" aria-hidden="true"></i>
                  <div class="summary-kv-body">
                    <span class="summary-kv-label">Booked On</span>
                    <span class="summary-kv-value">{{ vm.bookedOnText }}</span>
                  </div>
                </div>
              </div>

              <div class="summary-status-slot">
                <span class="modal-status-pill" [ngClass]="vm.statusBadgeClass">
                  {{ vm.statusLabel }}
                </span>
              </div>
            </div>
          </section>

          <!-- Non-blocking Enrichment Status / Partial Error Notice -->
          <div *ngIf="isLoadingDetails" class="hotel-enrichment-banner" role="status" aria-live="polite">
            <span class="spinner-border spinner-border-sm text-primary" aria-hidden="true"></span>
            <span>Fetching latest room allocations, payment, and hotel details...</span>
          </div>

          <div *ngIf="detailsError && !isLoadingDetails" class="hotel-error-banner" role="alert">
            <i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i>
            <span>{{ detailsError }}</span>
          </div>

          <!-- 3-Tab Navigation -->
          <nav class="hotel-tabs-nav">
            <div role="tablist" aria-label="Hotel booking detail sections" class="hotel-tablist">
              <button
                *ngFor="let tab of tabs; let i = index"
                #tabBtn
                type="button"
                role="tab"
                class="hotel-tab-btn"
                [id]="'hotel-tab-' + tab.id"
                [attr.aria-selected]="activeTab === tab.id ? 'true' : 'false'"
                [attr.tabindex]="activeTab === tab.id ? 0 : -1"
                (click)="selectTab(tab.id)"
                (keydown)="onTabKeyDown($event, i)"
              >
                <i [class]="tab.iconClass" aria-hidden="true"></i>
                <span>{{ tab.label }}</span>
              </button>
            </div>
          </nav>
        </div>

        <!-- Scrollable Tab Content Area -->
        <div class="hotel-modal-scroll-area">
          <!-- TAB 1: BOOKING DETAILS -->
          <div
            role="tabpanel"
            aria-labelledby="hotel-tab-details"
            [hidden]="activeTab !== 'details'"
            [attr.tabindex]="activeTab === 'details' ? 0 : null"
          >
            <div class="hotel-tabpanel-content">
              <!-- Stay Details Card -->
              <section class="modal-section-card" aria-labelledby="stay-details-heading">
                <div class="modal-section-header">
                  <i class="fa-regular fa-calendar-check" aria-hidden="true"></i>
                  <h3 id="stay-details-heading" class="modal-section-title">Stay Details</h3>
                </div>

                <div class="stay-details-grid">
                  <div class="stay-metric-cell">
                    <i class="fa-regular fa-calendar stay-cell-icon" aria-hidden="true"></i>
                    <div>
                      <div class="stay-cell-label">Check-in</div>
                      <div class="stay-cell-value-row">
                        <span class="stay-cell-value">{{ vm.checkInDateText }}</span>
                        <span class="stay-cell-note">{{ vm.checkInTimeNote }}</span>
                      </div>
                    </div>
                  </div>

                  <div class="stay-metric-cell stay-metric-divider">
                    <i class="fa-regular fa-calendar stay-cell-icon" aria-hidden="true"></i>
                    <div>
                      <div class="stay-cell-label">Check-out</div>
                      <div class="stay-cell-value-row">
                        <span class="stay-cell-value">{{ vm.checkOutDateText }}</span>
                        <span *ngIf="vm.checkOutTimeNote" class="stay-cell-note">{{ vm.checkOutTimeNote }}</span>
                      </div>
                    </div>
                  </div>

                  <div class="stay-metric-cell stay-metric-divider">
                    <i class="fa-regular fa-moon stay-cell-icon" aria-hidden="true"></i>
                    <div>
                      <div class="stay-cell-label">Duration</div>
                      <div class="stay-cell-value">{{ vm.durationText }}</div>
                    </div>
                  </div>
                </div>
              </section>

              <!-- Primary Guest Contact (if available from snapshot) -->
              <section
                *ngIf="vm.primaryGuest as pg"
                class="modal-section-card"
                aria-labelledby="primary-contact-heading"
              >
                <div class="modal-section-header">
                  <i class="fa-regular fa-address-card" aria-hidden="true"></i>
                  <h3 id="primary-contact-heading" class="modal-section-title">Primary Guest (Booking Contact)</h3>
                </div>

                <div class="stay-details-grid">
                  <div class="stay-metric-cell">
                    <i class="fa-regular fa-user stay-cell-icon" aria-hidden="true"></i>
                    <div>
                      <div class="stay-cell-label">Full Name</div>
                      <div class="stay-cell-value">{{ pg.fullName || '—' }}</div>
                    </div>
                  </div>

                  <div class="stay-metric-cell stay-metric-divider">
                    <i class="fa-regular fa-envelope stay-cell-icon" aria-hidden="true"></i>
                    <div>
                      <div class="stay-cell-label">Email Address</div>
                      <div class="stay-cell-value text-break">{{ pg.email || '—' }}</div>
                    </div>
                  </div>

                  <div class="stay-metric-cell stay-metric-divider">
                    <i class="fa-solid fa-phone stay-cell-icon" aria-hidden="true"></i>
                    <div>
                      <div class="stay-cell-label">Mobile Number</div>
                      <div class="stay-cell-value">{{ pg.mobile || '—' }}</div>
                    </div>
                  </div>
                </div>
              </section>

              <!-- Room Details Card (Multi-Room Vertically Stacked) -->
              <section class="modal-section-card" aria-labelledby="room-details-heading">
                <div class="modal-section-header modal-section-header-between">
                  <div class="d-flex align-items-center gap-2">
                    <i class="fa-solid fa-bed" aria-hidden="true"></i>
                    <h3 id="room-details-heading" class="modal-section-title">Room Details</h3>
                  </div>
                  <span *ngIf="vm.rooms.length > 1" class="room-total-pill">
                    {{ vm.rooms.length }} Rooms Allocated
                  </span>
                </div>

                <ng-container *ngIf="!vm.isLegacyWithoutAllocations; else legacyRoomsFallback">
                  <div class="rooms-stack">
                    <article
                      *ngFor="let room of vm.rooms; let lastRoom = last"
                      class="room-allocation-row"
                      [class.room-allocation-row-bordered]="!lastRoom"
                    >
                      <!-- Left Room Summary -->
                      <div class="room-left-summary">
                        <div class="room-photo-wrap">
                          <img
                            [src]="room.imageUrl"
                            (error)="onImgError($event)"
                            class="room-photo"
                            [alt]="room.roomTypeName"
                          />
                        </div>

                        <div class="room-title-meta">
                          <div class="room-type-title-row">
                            <span class="room-type-heading">{{ room.roomTypeName }}</span>
                            <span *ngIf="vm.rooms.length > 1" class="room-seq-badge">
                              Room {{ room.roomSequenceOverall }}
                            </span>
                          </div>
                          <div class="room-occupancy-summary">
                            {{ room.roomCountSummary }} • {{ room.occupancySummary }}
                          </div>
                        </div>
                      </div>

                      <!-- Right Guest Details Table -->
                      <div class="room-guests-panel">
                        <h4
                          [id]="'room-guests-heading-' + room.roomSequenceOverall"
                          class="room-guests-heading"
                        >
                          <i class="fa-solid fa-user-group" aria-hidden="true"></i>
                          <span>Guest Details (Room {{ room.roomSequenceOverall }})</span>
                        </h4>

                        <div
                          *ngIf="room.guests.length > 0; else emptyRoomGuests"
                          tabindex="0"
                          role="region"
                          [attr.aria-labelledby]="'room-guests-heading-' + room.roomSequenceOverall"
                          class="table-wrapper guest-table-wrapper"
                        >
                          <table class="guest-details-table">
                            <thead>
                              <tr>
                                <th scope="col" class="col-num">#</th>
                                <th scope="col" class="col-name">Name</th>
                                <th scope="col" class="col-age">Age</th>
                                <th scope="col" class="col-gender">Gender</th>
                              </tr>
                            </thead>
                            <tbody>
                              <tr *ngFor="let guest of room.guests">
                                <td class="col-num">{{ guest.index }}</td>
                                <td class="col-name">
                                  <span class="guest-name-text">{{ guest.name }}</span>
                                  <span *ngIf="guest.isChild" class="guest-child-tag">Child</span>
                                </td>
                                <td class="col-age">{{ guest.ageDisplay }}</td>
                                <td class="col-gender">{{ guest.genderDisplay }}</td>
                              </tr>
                            </tbody>
                          </table>
                        </div>

                        <ng-template #emptyRoomGuests>
                          <p class="empty-guests-note">
                            Individual guest details were not recorded for this room.
                          </p>
                        </ng-template>
                      </div>
                    </article>
                  </div>
                </ng-container>

                <ng-template #legacyRoomsFallback>
                  <div class="legacy-rooms-fallback">
                    <div *ngIf="vm.legacyRoomSummary" class="fw-semibold text-navy mb-1">
                      {{ vm.legacyRoomSummary }}
                    </div>
                    <p class="text-secondary small mb-0">
                      Detailed room-wise guest allocation is not available for this booking.
                    </p>
                  </div>
                </ng-template>
              </section>
            </div>
          </div>

          <!-- TAB 2: PAYMENT & REFUND -->
          <div
            role="tabpanel"
            aria-labelledby="hotel-tab-payment"
            [hidden]="activeTab !== 'payment'"
            [attr.tabindex]="activeTab === 'payment' ? 0 : null"
          >
            <div class="hotel-tabpanel-content">
              <!-- Price Summary Card -->
              <section class="modal-section-card" aria-labelledby="price-summary-heading">
                <div class="modal-section-header modal-section-header-between">
                  <div class="d-flex align-items-center gap-2">
                    <i class="fa-solid fa-receipt" aria-hidden="true"></i>
                    <h3 id="price-summary-heading" class="modal-section-title">Price Summary</h3>
                  </div>
                  <span class="currency-pill">{{ vm.currency }}</span>
                </div>

                <div class="price-summary-body">
                  <div *ngIf="vm.priceLineItems.length > 0" class="price-lines-list">
                    <div *ngFor="let item of vm.priceLineItems" class="price-line-row">
                      <span class="price-line-label">
                        {{ item.roomTypeName }} × {{ item.quantity }}
                      </span>
                      <span class="price-line-value">{{ item.formattedAmount }}</span>
                    </div>
                  </div>

                  <div class="price-line-row price-tax-row">
                    <span class="price-line-label">Taxes &amp; Fees</span>
                    <span class="price-tax-note">{{ vm.taxesNote }}</span>
                  </div>

                  <div class="price-total-divider"></div>

                  <div class="price-total-row">
                    <div>
                      <div class="price-total-label">Total Amount Paid</div>
                      <div *ngIf="vm.paymentReference" class="payment-ref-subline">
                        Payment Ref: <span>{{ vm.paymentReference }}</span>
                      </div>
                    </div>
                    <div class="price-total-right">
                      <span class="price-total-amount">{{ vm.formattedTotalPaid }}</span>
                      <span class="payment-status-chip">{{ vm.paymentStatusLabel }}</span>
                    </div>
                  </div>
                </div>
              </section>

              <!-- Refund Information Card -->
              <section class="modal-section-card" aria-labelledby="refund-info-heading">
                <div class="modal-section-header modal-section-header-between">
                  <div class="d-flex align-items-center gap-2">
                    <i class="fa-solid fa-rotate-left" aria-hidden="true"></i>
                    <h3 id="refund-info-heading" class="modal-section-title">Refund Information</h3>
                  </div>
                  <span class="refund-state-pill" [ngClass]="vm.refund.badgeClass">
                    <i [class]="vm.refund.iconClass" aria-hidden="true"></i>
                    <span>{{ vm.refund.badgeLabel }}</span>
                  </span>
                </div>

                <div class="refund-info-body">
                  <div class="refund-headline">{{ vm.refund.headline }}</div>
                  <p class="refund-description">{{ vm.refund.description }}</p>

                  <dl
                    *ngIf="
                      vm.refund.formattedRefundAmount !== null ||
                      vm.refund.refundId ||
                      vm.refund.refundedAtText ||
                      vm.refund.cancelledAtText ||
                      vm.refund.cancellationPolicyTier ||
                      vm.refund.cancellationReason
                    "
                    class="refund-details-grid"
                  >
                    <div *ngIf="vm.refund.formattedRefundAmount !== null" class="refund-detail-item">
                      <dt>Refund Amount</dt>
                      <dd class="refund-amount-strong">{{ vm.refund.formattedRefundAmount }}</dd>
                    </div>

                    <div *ngIf="vm.refund.refundId" class="refund-detail-item">
                      <dt>Refund Reference ID</dt>
                      <dd class="text-break">{{ vm.refund.refundId }}</dd>
                    </div>

                    <div *ngIf="vm.refund.refundedAtText" class="refund-detail-item">
                      <dt>Refunded On</dt>
                      <dd>{{ vm.refund.refundedAtText }}</dd>
                    </div>

                    <div *ngIf="vm.refund.cancelledAtText" class="refund-detail-item">
                      <dt>Cancelled On</dt>
                      <dd>{{ vm.refund.cancelledAtText }}</dd>
                    </div>

                    <div *ngIf="vm.refund.cancellationPolicyTier" class="refund-detail-item">
                      <dt>Applied Policy Tier</dt>
                      <dd>{{ vm.refund.cancellationPolicyTier }}</dd>
                    </div>

                    <div *ngIf="vm.refund.cancellationFeeText" class="refund-detail-item">
                      <dt>Cancellation Fee</dt>
                      <dd>{{ vm.refund.cancellationFeeText }}</dd>
                    </div>

                    <div *ngIf="vm.refund.cancellationReason" class="refund-detail-item refund-detail-wide">
                      <dt>Cancellation Reason</dt>
                      <dd>{{ vm.refund.cancellationReason }}</dd>
                    </div>
                  </dl>
                </div>
              </section>
            </div>
          </div>

          <!-- TAB 3: CANCELLATION POLICY -->
          <div
            role="tabpanel"
            aria-labelledby="hotel-tab-policy"
            [hidden]="activeTab !== 'policy'"
            [attr.tabindex]="activeTab === 'policy' ? 0 : null"
          >
            <div class="hotel-tabpanel-content">
              <section class="modal-section-card" aria-labelledby="policy-heading">
                <div class="modal-section-header">
                  <i class="fa-solid fa-circle-info" aria-hidden="true"></i>
                  <h3 id="policy-heading" class="modal-section-title">Hotel Cancellation Policy</h3>
                </div>

                <div class="policy-body">
                  <div class="policy-timezone-banner">
                    <i class="fa-regular fa-clock" aria-hidden="true"></i>
                    <span>{{ vm.policyMetaNote }}</span>
                  </div>

                  <div class="policy-rules-list">
                    <div *ngFor="let rule of vm.policyRules; let lastRule = last" class="policy-rule-row" [class.policy-rule-bordered]="!lastRule">
                      <div class="policy-rule-main">
                        <div class="policy-window-title">{{ rule.windowLabel }}</div>
                        <p class="policy-window-desc">{{ rule.description }}</p>
                      </div>

                      <div class="policy-rule-badge-col">
                        <span
                          class="policy-refund-pill"
                          [class.policy-pill-full]="rule.badgeTone === 'full'"
                          [class.policy-pill-partial]="rule.badgeTone === 'partial'"
                          [class.policy-pill-none]="rule.badgeTone === 'none'"
                        >
                          {{ rule.refundLabel }}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </section>
            </div>
          </div>
        </div>
      </div>
    </dialog>
  `,
  styles: [`
    dialog.hotel-details-dialog {
      border: 0;
      padding: 0;
      border-radius: 18px;
      background: #ffffff;
      color: #0f172a;
      inline-size: min(920px, calc(100vw - 32px));
      max-inline-size: 920px;
      max-block-size: min(90dvb, 820px);
      margin: auto;
      box-shadow: 0 24px 60px rgba(15, 23, 42, 0.25);
      overflow: hidden;
    }

    dialog.hotel-details-dialog[open] {
      display: flex;
      flex-direction: column;
    }

    /* MANDATORY scroll-containment rules from dialog guide */
    dialog.hotel-details-dialog:modal {
      overscroll-behavior: contain;
    }

    dialog.hotel-details-dialog::backdrop {
      background: rgba(15, 23, 42, 0.56);
      backdrop-filter: blur(2px);
      overflow: hidden;
      overscroll-behavior: contain;
    }

    .hotel-modal-shell {
      display: flex;
      flex-direction: column;
      min-block-size: 0;
      block-size: 100%;
      overflow: hidden;
    }

    /* Header */
    .hotel-modal-header {
      flex: 0 0 auto;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 16px 24px 12px;
      border-block-end: 1px solid #f1f5f9;
      background: #ffffff;
    }

    .hotel-modal-title-group {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .hotel-modal-icon-circle {
      inline-size: 32px;
      block-size: 32px;
      border-radius: 999px;
      background: #eff6ff;
      color: #0875e1;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 0.9rem;
    }

    .hotel-modal-title {
      font-size: 1.15rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
    }

    .hotel-modal-close-btn {
      appearance: none;
      background: transparent;
      border: 0;
      inline-size: 34px;
      block-size: 34px;
      border-radius: 999px;
      color: #475569;
      font-size: 1.05rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: background-color 0.15s ease, color 0.15s ease;
    }

    .hotel-modal-close-btn:hover {
      background: #f1f5f9;
      color: #0f172a;
    }

    /* Pinned Top Summary + Tabs */
    .hotel-modal-pinned-top {
      flex: 0 0 auto;
      background: #ffffff;
      border-block-end: 1px solid #e2e8f0;
    }

    .hotel-summary-card {
      container-type: inline-size;
      margin: 12px 24px 0;
      padding: 16px 20px;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      background: #ffffff;
      display: grid;
      grid-template-columns: minmax(0, 1.05fr) minmax(0, 1.05fr);
      gap: 20px;
      align-items: center;
    }

    .summary-left {
      display: flex;
      align-items: center;
      gap: 16px;
      min-inline-size: 0;
    }

    .summary-photo-wrap {
      flex: 0 0 auto;
      inline-size: 144px;
      block-size: 92px;
      border-radius: 10px;
      overflow: hidden;
      border: 1px solid #e2e8f0;
      background: #f1f5f9;
    }

    .summary-photo {
      display: block;
      inline-size: 100%;
      block-size: 100%;
      object-fit: cover;
    }

    .summary-hotel-meta {
      min-inline-size: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .modal-hotel-pill {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 6px;
      background: #e0e7ff;
      color: #4338ca;
      font-size: 0.72rem;
      font-weight: 700;
    }

    .summary-hotel-name {
      font-size: 1.15rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
      line-height: 1.25;
    }

    .summary-hotel-location {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 0.82rem;
      color: #475569;
    }

    .summary-hotel-rating {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 6px;
      margin-top: 2px;
      font-size: 0.8rem;
    }

    .rating-stars {
      color: #f59e0b;
      display: inline-flex;
      gap: 2px;
      font-size: 0.76rem;
    }

    .rating-caption {
      color: #475569;
      font-weight: 500;
    }

    .summary-right {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: start;
      gap: 12px;
      border-inline-start: 1px solid #e2e8f0;
      padding-inline-start: 20px;
    }

    .summary-ref-stack {
      display: flex;
      flex-direction: column;
      gap: 10px;
      min-inline-size: 0;
    }

    .summary-kv-row {
      display: flex;
      align-items: flex-start;
      gap: 9px;
    }

    .summary-kv-icon {
      color: #64748b;
      margin-top: 3px;
      font-size: 0.86rem;
    }

    .summary-kv-body {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-inline-size: 0;
    }

    .summary-kv-label {
      font-size: 0.72rem;
      color: #64748b;
      font-weight: 500;
    }

    .summary-kv-value {
      font-size: 0.84rem;
      font-weight: 700;
      color: #0f172a;
    }

    .booking-id-copy-row {
      display: inline-flex;
      align-items: center;
      flex-wrap: nowrap;
      gap: 6px;
      min-inline-size: 0;
    }

    .booking-id-value {
      word-break: break-word;
    }

    .btn-copy-booking-id {
      flex: 0 0 auto;
      appearance: none;
      background: transparent;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 2px 6px;
      font-size: 0.75rem;
      color: #475569;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 4px;
      transition: background-color 0.15s ease, color 0.15s ease;
    }

    .btn-copy-booking-id:hover {
      background: #f1f5f9;
      color: #0875e1;
    }

    .copied-feedback-text {
      font-size: 0.7rem;
      font-weight: 700;
      color: #15803d;
    }

    .modal-status-pill {
      display: inline-flex;
      align-items: center;
      padding: 4px 12px;
      border-radius: 999px;
      font-size: 0.76rem;
      font-weight: 700;
      white-space: nowrap;
    }

    .hotel-status-confirmed { background: #dcfce7; color: #15803d; }
    .hotel-status-cancelled { background: #fee2e2; color: #dc2626; }
    .hotel-status-completed { background: #dbeafe; color: #1d4ed8; }
    .hotel-status-checked-in { background: #ccfbf1; color: #0f766e; }
    .hotel-status-checked-out { background: #f1f5f9; color: #334155; }
    .hotel-status-no-show { background: #fef3c7; color: #b45309; }

    /* Non-blocking Enrichment Banners */
    .hotel-enrichment-banner,
    .hotel-error-banner {
      margin: 10px 24px 0;
      padding: 8px 14px;
      border-radius: 8px;
      font-size: 0.8rem;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .hotel-enrichment-banner {
      background: #f8fafc;
      color: #475569;
      border: 1px solid #e2e8f0;
    }

    .hotel-error-banner {
      background: #fffbeb;
      color: #92400e;
      border: 1px solid #fde68a;
    }

    /* Accessible Horizontal Tabs (per tabs guide) */
    .hotel-tabs-nav {
      margin-top: 10px;
      padding: 0 24px;
    }

    .hotel-tablist {
      display: flex;
      align-items: stretch;
      justify-content: space-around;
      gap: 8px;
      overflow-x: auto;
      scrollbar-width: none;
    }

    .hotel-tablist::-webkit-scrollbar {
      display: none;
    }

    .hotel-tab-btn {
      position: relative;
      flex: 0 0 auto;
      appearance: none;
      background: transparent;
      border: 0;
      padding: 12px 16px;
      font-size: 0.88rem;
      font-weight: 600;
      color: #475569;
      display: inline-flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      white-space: nowrap;
      transition: color 0.15s ease;
    }

    .hotel-tab-btn:hover {
      color: #0875e1;
    }

    .hotel-tab-btn:focus-visible {
      outline: 2px solid #0875e1;
      outline-offset: -3px;
      border-radius: 6px;
    }

    /* Active indicator styled from aria-selected with border underline (WCAG 1.4.1 safe) */
    .hotel-tab-btn[aria-selected="true"] {
      color: #0875e1;
    }

    .hotel-tab-btn[aria-selected="true"]::after {
      content: "";
      position: absolute;
      inset: auto 0 0;
      border-block-end: 3px solid #0875e1;
    }

    /* Scrollable Panel Container */
    .hotel-modal-scroll-area {
      flex: 1 1 auto;
      overflow-y: auto;
      min-block-size: 0;
      background: #f8fafc;
    }

    /* Never put padding/borders on [role="tabpanel"] directly (tabs guide); style inner wrapper */
    .hotel-tabpanel-content {
      padding: 18px 24px 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    /* Section Cards */
    .modal-section-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      overflow: hidden;
    }

    .modal-section-header {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 11px 18px;
      background: #f1f6fc;
      border-block-end: 1px solid #e2e8f0;
      color: #0f172a;
    }

    .modal-section-header-between {
      justify-content: space-between;
    }

    .modal-section-title {
      font-size: 0.94rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
    }

    .room-total-pill,
    .currency-pill {
      padding: 2px 10px;
      border-radius: 999px;
      background: #e2e8f0;
      color: #334155;
      font-size: 0.72rem;
      font-weight: 700;
    }

    /* Stay Details */
    .stay-details-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      padding: 16px 18px;
      gap: 16px;
    }

    .stay-metric-cell {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      min-inline-size: 0;
    }

    .stay-metric-divider {
      border-inline-start: 1px solid #e2e8f0;
      padding-inline-start: 18px;
    }

    .stay-cell-icon {
      color: #64748b;
      margin-top: 3px;
      font-size: 0.9rem;
    }

    .stay-cell-label {
      font-size: 0.76rem;
      color: #64748b;
      margin-bottom: 2px;
    }

    .stay-cell-value-row {
      display: flex;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 5px;
    }

    .stay-cell-value {
      font-size: 0.9rem;
      font-weight: 700;
      color: #0f172a;
    }

    .stay-cell-note {
      font-size: 0.76rem;
      color: #475569;
    }

    /* Room Details */
    .rooms-stack {
      display: flex;
      flex-direction: column;
    }

    .room-allocation-row {
      display: grid;
      grid-template-columns: minmax(220px, 0.9fr) minmax(0, 1.45fr);
      gap: 20px;
      padding: 18px;
      align-items: start;
    }

    .room-allocation-row-bordered {
      border-block-end: 1px solid #e2e8f0;
    }

    .room-left-summary {
      display: flex;
      align-items: center;
      gap: 14px;
      min-inline-size: 0;
    }

    .room-photo-wrap {
      flex: 0 0 auto;
      inline-size: 98px;
      block-size: 72px;
      border-radius: 10px;
      overflow: hidden;
      border: 1px solid #e2e8f0;
      background: #f1f5f9;
    }

    .room-photo {
      display: block;
      inline-size: 100%;
      block-size: 100%;
      object-fit: cover;
    }

    .room-title-meta {
      min-inline-size: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .room-type-title-row {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 6px;
    }

    .room-type-heading {
      font-size: 0.96rem;
      font-weight: 700;
      color: #0f172a;
    }

    .room-seq-badge {
      padding: 1px 8px;
      border-radius: 999px;
      background: #eff6ff;
      color: #0875e1;
      font-size: 0.7rem;
      font-weight: 700;
    }

    .room-occupancy-summary {
      font-size: 0.8rem;
      color: #475569;
    }

    .room-guests-panel {
      border-inline-start: 1px solid #e2e8f0;
      padding-inline-start: 20px;
      min-inline-size: 0;
    }

    .room-guests-heading {
      display: flex;
      align-items: center;
      gap: 7px;
      font-size: 0.88rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0 0 10px;
    }

    /* Responsive Guest Table (per responsive-table guide) */
    .guest-table-wrapper {
      container-type: inline-size;
      overflow: auto;
      max-inline-size: 100%;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
    }

    .guest-details-table {
      inline-size: 100%;
      border-collapse: separate;
      border-spacing: 0;
      font-size: 0.84rem;
      background: #ffffff;
    }

    .guest-details-table thead th {
      position: sticky;
      inset-block-start: 0;
      z-index: 1;
      background: #f1f6fc;
      color: #334155;
      font-weight: 700;
      text-align: start;
      padding: 8px 12px;
      border-block-end: 1px solid #e2e8f0;
      white-space: nowrap;
    }

    .guest-details-table tbody td {
      padding: 8px 12px;
      color: #0f172a;
      border-block-end: 1px solid #f1f5f9;
      vertical-align: middle;
    }

    .guest-details-table tbody tr:last-child td {
      border-block-end: 0;
    }

    .guest-child-tag {
      display: inline-block;
      margin-inline-start: 6px;
      padding: 1px 6px;
      border-radius: 4px;
      background: #f1f5f9;
      color: #475569;
      font-size: 0.68rem;
      font-weight: 600;
    }

    .empty-guests-note,
    .legacy-rooms-fallback {
      padding: 14px 18px;
      font-size: 0.84rem;
      color: #64748b;
      margin: 0;
    }

    /* Payment & Refund Tab */
    .price-summary-body,
    .refund-info-body,
    .policy-body {
      padding: 16px 18px;
    }

    .price-lines-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin-bottom: 10px;
    }

    .price-line-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      font-size: 0.88rem;
    }

    .price-line-label {
      color: #334155;
      font-weight: 500;
    }

    .price-line-value {
      color: #0f172a;
      font-weight: 600;
    }

    .price-tax-row {
      color: #64748b;
      font-size: 0.83rem;
    }

    .price-tax-note {
      color: #64748b;
    }

    .price-total-divider {
      margin: 12px 0;
      border-block-start: 1px solid #e2e8f0;
    }

    .price-total-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 12px;
    }

    .price-total-label {
      font-size: 0.96rem;
      font-weight: 700;
      color: #0f172a;
    }

    .payment-ref-subline {
      font-size: 0.76rem;
      color: #64748b;
      margin-top: 2px;
    }

    .price-total-right {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .price-total-amount {
      font-size: 1.35rem;
      font-weight: 800;
      color: #0875e1;
    }

    .payment-status-chip {
      padding: 3px 10px;
      border-radius: 999px;
      background: #dcfce7;
      color: #15803d;
      font-size: 0.74rem;
      font-weight: 700;
    }

    .refund-state-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 3px 11px;
      border-radius: 999px;
      font-size: 0.74rem;
      font-weight: 700;
    }

    .refund-state-neutral { background: #f1f5f9; color: #475569; }
    .refund-state-success { background: #dcfce7; color: #15803d; }
    .refund-state-info { background: #dbeafe; color: #1d4ed8; }
    .refund-state-warning { background: #fef3c7; color: #b45309; }
    .refund-state-danger { background: #fee2e2; color: #dc2626; }

    .refund-headline {
      font-size: 0.96rem;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 4px;
    }

    .refund-description {
      font-size: 0.85rem;
      color: #475569;
      margin-bottom: 0;
    }

    .refund-details-grid {
      margin: 14px 0 0;
      padding-top: 14px;
      border-block-start: 1px solid #e2e8f0;
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px 16px;
    }

    .refund-detail-item dt {
      font-size: 0.74rem;
      font-weight: 500;
      color: #64748b;
      margin-bottom: 2px;
    }

    .refund-detail-item dd {
      margin: 0;
      font-size: 0.86rem;
      font-weight: 600;
      color: #0f172a;
    }

    .refund-amount-strong {
      color: #0875e1 !important;
      font-size: 1rem !important;
      font-weight: 800 !important;
    }

    .refund-detail-wide {
      grid-column: 1 / -1;
    }

    /* Cancellation Policy Tab */
    .policy-timezone-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 14px;
      border-radius: 10px;
      background: #f1f6fc;
      color: #1e293b;
      font-size: 0.82rem;
      font-weight: 600;
      margin-bottom: 14px;
    }

    .policy-rules-list {
      display: flex;
      flex-direction: column;
    }

    .policy-rule-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding: 12px 2px;
    }

    .policy-rule-bordered {
      border-block-end: 1px solid #f1f5f9;
    }

    .policy-window-title {
      font-size: 0.9rem;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 2px;
    }

    .policy-window-desc {
      margin: 0;
      font-size: 0.8rem;
      color: #475569;
    }

    .policy-refund-pill {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 999px;
      font-size: 0.76rem;
      font-weight: 700;
      white-space: nowrap;
    }

    .policy-pill-full { background: #dcfce7; color: #15803d; }
    .policy-pill-partial { background: #fef3c7; color: #b45309; }
    .policy-pill-none { background: #fee2e2; color: #dc2626; }

    /* Container query for narrow guest tables (responsive-table guide) */
    @container (width < 400px) {
      .guest-details-table,
      .guest-details-table thead,
      .guest-details-table tbody,
      .guest-details-table tr,
      .guest-details-table th,
      .guest-details-table td {
        display: block;
      }

      .guest-details-table thead {
        display: none;
      }

      .guest-details-table tbody tr {
        border-block-end: 1px solid #e2e8f0;
        padding: 8px 10px;
      }

      .guest-details-table tbody td {
        border-block-end: 0;
        padding: 2px 0;
        display: flex;
        justify-content: space-between;
        gap: 10px;
      }

      .guest-details-table {
        --label-1: "#";
        --label-2: "Name";
        --label-3: "Age";
        --label-4: "Gender";
      }

      .guest-details-table td:nth-child(1)::before { content: var(--label-1) ": "; font-weight: 600; color: #64748b; }
      .guest-details-table td:nth-child(2)::before { content: var(--label-2) ": "; font-weight: 600; color: #64748b; }
      .guest-details-table td:nth-child(3)::before { content: var(--label-3) ": "; font-weight: 600; color: #64748b; }
      .guest-details-table td:nth-child(4)::before { content: var(--label-4) ": "; font-weight: 600; color: #64748b; }
    }

    /* Responsive Dialog Breakpoints */
    @container (width < 660px) {
      .hotel-summary-card {
        grid-template-columns: minmax(0, 1fr);
      }

      .summary-right {
        border-inline-start: 0;
        border-block-start: 1px solid #f1f5f9;
        padding-inline-start: 0;
        padding-block-start: 12px;
      }
    }

    @media (max-width: 760px) {
      dialog.hotel-details-dialog {
        inline-size: calc(100vw - 16px);
        max-block-size: 94dvb;
        border-radius: 14px;
      }

      .hotel-modal-header {
        padding: 14px 16px 10px;
      }

      .hotel-summary-card {
        margin: 10px 16px 0;
        padding: 12px 14px;
        grid-template-columns: minmax(0, 1fr);
        gap: 10px;
      }

      .summary-left {
        flex-direction: row;
        align-items: center;
        gap: 12px;
      }

      .summary-photo-wrap {
        inline-size: 76px;
        block-size: 62px;
      }

      .summary-right {
        border-inline-start: 0;
        border-block-start: 1px solid #f1f5f9;
        padding-inline-start: 0;
        padding-block-start: 10px;
      }

      .hotel-tabs-nav {
        padding: 0 10px;
      }

      .hotel-tablist {
        justify-content: space-between;
        gap: 2px;
      }

      .hotel-tab-btn {
        padding: 10px 6px;
        font-size: 0.73rem;
        gap: 4px;
      }

      .hotel-tabpanel-content {
        padding: 14px 16px 20px;
      }

      .stay-details-grid {
        grid-template-columns: minmax(0, 1fr);
        gap: 12px;
      }

      .stay-metric-divider {
        border-inline-start: 0;
        border-block-start: 1px solid #f1f5f9;
        padding-inline-start: 0;
        padding-block-start: 12px;
      }

      .room-allocation-row {
        grid-template-columns: minmax(0, 1fr);
        gap: 14px;
      }

      .room-guests-panel {
        border-inline-start: 0;
        border-block-start: 1px solid #f1f5f9;
        padding-inline-start: 0;
        padding-block-start: 14px;
      }

      .policy-rule-row {
        flex-direction: column;
        align-items: flex-start;
      }
    }

    @media (max-width: 440px) {
      .hotel-tabs-nav {
        padding: 0 8px;
      }

      .hotel-tab-btn {
        padding: 9px 3px;
        font-size: 0.67rem;
        gap: 3px;
      }

      .hotel-tab-btn i {
        font-size: 0.72rem;
      }

      .booking-id-copy-row {
        flex-wrap: wrap;
      }
    }
  `]
})
export class HotelBookingDetailsModalComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() showDetailsModal = false;
  @Input() bookingCard: HotelBookingCardViewModel | null = null;
  @Input() hotelDetails: HotelDetailsResponseDto | null = null;
  @Input() reservationDetails: HotelReservationDetailResponse | null = null;
  @Input() paymentTransaction: PaymentTransactionDto | null = null;
  @Input() reviews: any[] | null = null;
  @Input() isLoadingDetails = false;
  @Input() detailsError: string | null = null;

  @Output() closeDetails = new EventEmitter<void>();

  @ViewChild('hotelDialog') dialogRef?: ElementRef<HTMLDialogElement>;
  @ViewChildren('tabBtn') tabButtons?: QueryList<ElementRef<HTMLButtonElement>>;

  readonly tabs: ReadonlyArray<HotelModalTabDefinition> = [
    { id: 'details', label: 'Booking Details', iconClass: 'fa-regular fa-calendar-check' },
    { id: 'payment', label: 'Payment & Refund', iconClass: 'fa-regular fa-credit-card' },
    { id: 'policy', label: 'Cancellation Policy', iconClass: 'fa-solid fa-circle-info' }
  ];

  activeTab: HotelModalTabId = 'details';
  detailsVm: HotelBookingDetailsViewModel | null = null;
  copiedBookingRef = false;

  private lastOpenedBookingRef: string | null = null;
  private isProgrammaticClose = false;
  private copyTimerId: ReturnType<typeof setTimeout> | null = null;
  private removeBackdropListeners: (() => void) | null = null;

  ngAfterViewInit(): void {
    this.installBackdropFallbackIfNeeded();
    this.syncDialogVisibility();
  }

  ngOnChanges(changes: SimpleChanges): void {
    const prevWasOpen = Boolean(changes['showDetailsModal']?.previousValue);
    const nextRef = this.bookingCard?.bookingRef || null;

    if (
      (this.showDetailsModal && !prevWasOpen) ||
      (this.showDetailsModal && nextRef && nextRef !== this.lastOpenedBookingRef)
    ) {
      this.activeTab = 'details';
      this.copiedBookingRef = false;
      this.lastOpenedBookingRef = nextRef;
    }

    if (!this.showDetailsModal) {
      this.lastOpenedBookingRef = null;
    }

    this.rebuildViewModel();
    this.syncDialogVisibility();
  }

  ngOnDestroy(): void {
    if (this.copyTimerId !== null) {
      clearTimeout(this.copyTimerId);
      this.copyTimerId = null;
    }
    if (this.removeBackdropListeners) {
      this.removeBackdropListeners();
      this.removeBackdropListeners = null;
    }
  }

  selectTab(tabId: HotelModalTabId): void {
    this.activeTab = tabId;
  }

  onTabKeyDown(event: KeyboardEvent, currentIndex: number): void {
    let targetIndex: number | null = null;
    if (event.key === 'ArrowRight') {
      targetIndex = (currentIndex + 1) % this.tabs.length;
    } else if (event.key === 'ArrowLeft') {
      targetIndex = (currentIndex - 1 + this.tabs.length) % this.tabs.length;
    } else if (event.key === 'Home') {
      targetIndex = 0;
    } else if (event.key === 'End') {
      targetIndex = this.tabs.length - 1;
    }

    if (targetIndex === null) {
      return;
    }

    event.preventDefault();
    const nextTab = this.tabs[targetIndex];
    this.activeTab = nextTab.id;
    const targetBtn = this.tabButtons?.get(targetIndex)?.nativeElement;
    if (targetBtn) {
      targetBtn.focus();
      targetBtn.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }
  }

  copyBookingRef(bookingRef: string): void {
    if (!bookingRef) return;
    if (typeof navigator !== 'undefined' && navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
      navigator.clipboard.writeText(bookingRef).catch(() => {});
    }
    this.copiedBookingRef = true;
    if (this.copyTimerId !== null) {
      clearTimeout(this.copyTimerId);
    }
    this.copyTimerId = setTimeout(() => {
      this.copiedBookingRef = false;
      this.copyTimerId = null;
    }, 2000);
  }

  requestClose(): void {
    const dialog = this.dialogRef?.nativeElement;
    if (dialog && dialog.open) {
      if (typeof dialog.close === 'function') {
        dialog.close();
        return;
      }
      dialog.removeAttribute('open');
    }
    this.closeDetails.emit();
  }

  onNativeDialogCancel(event: Event): void {
    event.preventDefault();
    this.requestClose();
  }

  onNativeDialogClose(): void {
    if (this.isProgrammaticClose) {
      this.isProgrammaticClose = false;
      return;
    }
    if (this.showDetailsModal) {
      this.closeDetails.emit();
    }
  }

  onImgError(event: Event): void {
    const img = event.target as HTMLImageElement | null;
    if (!img || img.dataset['fallbackApplied'] === '1') {
      return;
    }
    img.dataset['fallbackApplied'] = '1';
    img.src = DEFAULT_HOTEL_PLACEHOLDER;
  }

  private rebuildViewModel(): void {
    if (!this.bookingCard) {
      this.detailsVm = null;
      return;
    }
    const rawBooking = this.bookingCard.raw || {
      id: this.bookingCard.id,
      bookingRef: this.bookingCard.bookingRef,
      serviceType: 'HOTEL',
      serviceId: this.bookingCard.serviceId,
      serviceTitle: this.bookingCard.hotelName,
      checkInDate: this.bookingCard.checkInIso,
      checkOutDate: this.bookingCard.checkOutIso,
      totalAmount: this.bookingCard.totalAmount,
      status: this.bookingCard.status,
      refundStatus: this.bookingCard.refundStatus,
      refundId: this.bookingCard.refundId,
      refundAmount: this.bookingCard.refundAmount,
      bookingDate: this.bookingCard.rawBookingDate,
      detailsJson: this.bookingCard.detailsJson
    };

    this.detailsVm = buildHotelBookingDetailsViewModel(rawBooking, {
      hotelDetails: this.hotelDetails,
      reservationDetails: this.reservationDetails,
      paymentTransaction: this.paymentTransaction,
      reviews: this.reviews
    });
  }

  private syncDialogVisibility(): void {
    const dialog = this.dialogRef?.nativeElement;
    if (!dialog) {
      return;
    }
    const shouldBeOpen = Boolean(this.showDetailsModal && this.detailsVm);
    if (shouldBeOpen && !dialog.open) {
      if (typeof dialog.showModal === 'function') {
        try {
          dialog.showModal();
        } catch {
          dialog.setAttribute('open', '');
        }
      } else {
        dialog.setAttribute('open', '');
      }
    } else if (!shouldBeOpen && dialog.open) {
      this.isProgrammaticClose = true;
      if (typeof dialog.close === 'function') {
        dialog.close();
      } else {
        dialog.removeAttribute('open');
        this.isProgrammaticClose = false;
      }
    }
  }

  /** Light-dismiss fallback when browser lacks HTMLDialogElement.prototype.closedBy (e.g. Safari). */
  private installBackdropFallbackIfNeeded(): void {
    const dialog = this.dialogRef?.nativeElement;
    if (!dialog || typeof HTMLDialogElement === 'undefined') {
      return;
    }
    if ('closedBy' in HTMLDialogElement.prototype) {
      return;
    }

    const isOutside = (e: PointerEvent): boolean => {
      const rect = dialog.getBoundingClientRect();
      return (
        e.clientX < rect.left ||
        e.clientX > rect.right ||
        e.clientY < rect.top ||
        e.clientY > rect.bottom
      );
    };

    let pressedOutside = false;
    const onPointerDown = (e: PointerEvent) => {
      pressedOutside = dialog.open && isOutside(e);
    };
    const onPointerUp = (e: PointerEvent) => {
      if (pressedOutside && dialog.open && isOutside(e)) {
        this.requestClose();
      }
      pressedOutside = false;
    };

    dialog.addEventListener('pointerdown', onPointerDown);
    dialog.addEventListener('pointerup', onPointerUp);

    this.removeBackdropListeners = () => {
      dialog.removeEventListener('pointerdown', onPointerDown);
      dialog.removeEventListener('pointerup', onPointerUp);
    };
  }
}
