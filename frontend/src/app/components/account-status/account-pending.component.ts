import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ApplicationDetails } from '../../models/application-details.model';

@Component({
  selector: 'app-account-pending',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="pending-page-wrapper">
      <div class="pending-container">
        <!-- Main Card -->
        <div class="pending-card">
          <!-- Illustration -->
          <div class="illustration-container">
            <svg width="180" height="110" viewBox="0 0 180 110" fill="none" xmlns="http://www.w3.org/2000/svg">
              <!-- Clouds in background -->
              <path d="M42 90C36 90 30 85 30 78C30 72 34 67 40 66C42 56 50 48 60 48C67 48 73 52 76 58C79 56 83 55 87 55C96 55 104 62 105 71C110 72 114 76 114 81C114 86 110 90 105 90H42Z" fill="#e0f2fe" opacity="0.8"/>
              <path d="M95 90C90 90 85 86 85 81C85 76 89 72 94 71C96 64 102 59 110 59C115 59 120 62 122 66C125 64 128 63 132 63C139 63 145 68 146 75C150 76 153 79 153 83C153 87 150 90 146 90H95Z" fill="#bae6fd" opacity="0.6"/>

              <!-- Document Sheet -->
              <rect x="68" y="24" width="52" height="66" rx="6" fill="#ffffff" stroke="#0284c7" stroke-width="3"/>
              
              <!-- Folded corner effect -->
              <path d="M106 24L120 38H110C107.8 38 106 36.2 106 34V24Z" fill="#e0f2fe" stroke="#0284c7" stroke-width="2"/>
              
              <!-- Document Lines -->
              <rect x="76" y="38" width="22" height="3" rx="1.5" fill="#0284c7"/>
              <rect x="76" y="46" width="34" height="3" rx="1.5" fill="#0284c7"/>
              <rect x="76" y="54" width="34" height="3" rx="1.5" fill="#0284c7"/>
              <rect x="76" y="62" width="26" height="3" rx="1.5" fill="#0284c7"/>
              <rect x="76" y="70" width="18" height="3" rx="1.5" fill="#0284c7"/>

              <!-- Overlapping Clock Badge -->
              <circle cx="114" cy="74" r="16" fill="#f59e0b" stroke="#ffffff" stroke-width="2.5"/>
              <!-- Clock hands -->
              <path d="M114 65V74L119 78" stroke="#ffffff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>

              <!-- Sparkle / Accents -->
              <line x1="56" y1="26" x2="60" y2="30" stroke="#38bdf8" stroke-width="2.5" stroke-linecap="round"/>
              <line x1="50" y1="36" x2="56" y2="36" stroke="#38bdf8" stroke-width="2.5" stroke-linecap="round"/>
              <line x1="130" y1="26" x2="126" y2="30" stroke="#38bdf8" stroke-width="2.5" stroke-linecap="round"/>
              <line x1="134" y1="36" x2="128" y2="36" stroke="#38bdf8" stroke-width="2.5" stroke-linecap="round"/>
            </svg>
          </div>

          <!-- Main Heading -->
          <h1 class="main-title">Application Under Review</h1>
          
          <p class="subtitle">
            Thank you for registering as a partner with Travel Easy.<br>
            Your application has been submitted successfully and is currently under review<br>
            by our administrator team.
          </p>

          <!-- Loading State -->
          <div *ngIf="loading" class="text-center py-4">
            <div class="spinner-border text-primary" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
            <p class="text-muted mt-2 small">Loading application details...</p>
          </div>

          <!-- Error Alert (with retry) -->
          <div *ngIf="error && !loading" class="alert alert-warning py-2 px-3 mb-3 d-flex align-items-center justify-content-between small">
            <span><i class="fa-solid fa-triangle-exclamation me-1"></i> {{ error }}</span>
            <button class="btn btn-sm btn-outline-dark py-0 px-2" (click)="loadApplicationDetails()">Retry</button>
          </div>

          <!-- Application Details Summary Box -->
          <div *ngIf="!loading && appDetails" class="details-summary-card">
            <div class="card-header-row">
              <div class="doc-icon-box">
                <i class="fa-solid fa-file-lines"></i>
              </div>
              <h2 class="card-section-title">Application Details</h2>
            </div>

            <div class="key-value-list">
              <div class="kv-row">
                <div class="kv-key">Organization Name</div>
                <div class="kv-colon">:</div>
                <div class="kv-value fw-bold">{{ appDetails?.organizationName || 'Not provided' }}</div>
              </div>

              <div class="kv-row">
                <div class="kv-key">Application Type</div>
                <div class="kv-colon">:</div>
                <div class="kv-value">{{ appDetails?.applicationType || 'Not provided' }}</div>
              </div>

              <div class="kv-row">
                <div class="kv-key">Submitted On</div>
                <div class="kv-colon">:</div>
                <div class="kv-value">{{ formattedSubmittedOn }}</div>
              </div>

              <div class="kv-row">
                <div class="kv-key">Application Status</div>
                <div class="kv-colon">:</div>
                <div class="kv-value">
                  <span class="status-badge" [ngClass]="statusBadgeClass">
                    <i class="fa-regular fa-clock" *ngIf="isUnderReview"></i>
                    <i class="fa-solid fa-check" *ngIf="isApproved"></i>
                    <i class="fa-solid fa-xmark" *ngIf="isRejected"></i>
                    {{ displayStatus }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- Informational Notice (What happens next?) -->
          <div class="info-notice-box">
            <div class="info-icon-circle">
              <i class="fa-solid fa-info"></i>
            </div>
            <div class="info-notice-text">
              Our team will verify the details you have provided. This process usually takes 2–3 business days.
              You will be notified via email and SMS once the review is completed.
            </div>
          </div>

          <!-- 4-Stage Horizontal Progress Indicator -->
          <div class="stepper-horizontal">
            <!-- Step 1: Application Submitted -->
            <div class="stepper-step completed">
              <div class="step-circle">
                <i class="fa-solid fa-check"></i>
              </div>
              <div class="step-label">Application Submitted</div>
              <div class="step-sub">{{ formattedDateOnly }}</div>
            </div>

            <!-- Connector 1-2 -->
            <div class="stepper-line" [ngClass]="{'active': isUnderReview || isApproved}"></div>

            <!-- Step 2: Under Review -->
            <div class="stepper-step" [ngClass]="{'active': isUnderReview, 'completed': isApproved}">
              <div class="step-circle">
                <i class="fa-solid fa-clock" *ngIf="isUnderReview"></i>
                <i class="fa-solid fa-check" *ngIf="isApproved"></i>
                <i class="fa-solid fa-clock" *ngIf="!isUnderReview && !isApproved"></i>
              </div>
              <div class="step-label">Under Review</div>
              <div class="step-sub">{{ isUnderReview ? 'In Progress' : (isApproved ? 'Completed' : 'Pending') }}</div>
            </div>

            <!-- Connector 2-3 -->
            <div class="stepper-line" [ngClass]="{'active': isApproved}"></div>

            <!-- Step 3: Approved -->
            <div class="stepper-step" [ngClass]="{'completed': isApproved}">
              <div class="step-circle">
                <i class="fa-regular fa-file-lines" *ngIf="!isApproved"></i>
                <i class="fa-solid fa-check" *ngIf="isApproved"></i>
              </div>
              <div class="step-label">Approved</div>
              <div class="step-sub">{{ isApproved ? 'Approved' : 'Pending' }}</div>
            </div>

            <!-- Connector 3-4 -->
            <div class="stepper-line" [ngClass]="{'active': isAccountActive}"></div>

            <!-- Step 4: Account Active -->
            <div class="stepper-step" [ngClass]="{'completed': isAccountActive}">
              <div class="step-circle">
                <i class="fa-regular fa-user" *ngIf="!isAccountActive"></i>
                <i class="fa-solid fa-check" *ngIf="isAccountActive"></i>
              </div>
              <div class="step-label">Account Active</div>
              <div class="step-sub">{{ isAccountActive ? 'Active' : 'Pending' }}</div>
            </div>
          </div>

          <!-- Bottom Action Buttons -->
          <div class="action-buttons-row">
            <a routerLink="/home" class="btn-back-home">
              <i class="fa-solid fa-arrow-left"></i>
              <span>Back to Home</span>
            </a>

            <a routerLink="/application-details" class="btn-view-details">
              <i class="fa-regular fa-envelope"></i>
              <span>View Application Details</span>
            </a>
          </div>

          <!-- Subtle Sign Out Link -->
          <div class="signout-row">
            <button class="btn-signout" (click)="logout()">
              <i class="fa-solid fa-right-from-bracket me-1"></i> Login
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      font-family: 'Segoe UI', system-ui, -apple-system, BlinkMacSystemFont, Roboto, sans-serif;
    }

    .pending-page-wrapper {
      min-height: 100vh;
      background: linear-gradient(180deg, #e4f2fe 0%, #edf5fc 40%, #f4f9fd 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 40px 16px;
    }

    .pending-container {
      width: 100%;
      max-width: 720px;
      margin: 0 auto;
    }

    .pending-card {
      background: #ffffff;
      border-radius: 20px;
      padding: 44px 48px;
      box-shadow: 0 10px 40px rgba(12, 32, 68, 0.08);
      border: 1px solid rgba(226, 232, 240, 0.9);
      text-align: center;
    }

    @media (max-width: 576px) {
      .pending-card {
        padding: 28px 20px;
        border-radius: 16px;
      }
    }

    /* Top Illustration */
    .illustration-container {
      display: flex;
      justify-content: center;
      margin-bottom: 6px;
    }

    /* Headings */
    .main-title {
      font-size: 1.75rem;
      font-weight: 800;
      color: #0c2044;
      margin: 8px 0 10px;
      letter-spacing: -0.02em;
    }

    .subtitle {
      font-size: 0.95rem;
      color: #475569;
      line-height: 1.5;
      margin-bottom: 24px;
    }

    /* Details Summary Box */
    .details-summary-card {
      background: #f0f7ff;
      border: 1px solid #dbeafe;
      border-radius: 14px;
      padding: 20px 24px;
      margin-bottom: 20px;
      text-align: left;
    }

    .card-header-row {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;
    }

    .doc-icon-box {
      width: 34px;
      height: 34px;
      background: #0066f5;
      color: #ffffff;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1rem;
      flex-shrink: 0;
    }

    .card-section-title {
      font-size: 1.05rem;
      font-weight: 700;
      color: #0c2044;
      margin: 0;
    }

    .key-value-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .kv-row {
      display: flex;
      align-items: center;
      font-size: 0.92rem;
      line-height: 1.4;
    }

    .kv-key {
      width: 170px;
      color: #475569;
      font-weight: 500;
      flex-shrink: 0;
    }

    .kv-colon {
      width: 24px;
      color: #475569;
      text-align: center;
      flex-shrink: 0;
    }

    .kv-value {
      color: #0c2044;
      font-weight: 600;
      flex: 1;
    }

    @media (max-width: 576px) {
      .kv-key {
        width: 130px;
        font-size: 0.85rem;
      }
      .kv-colon {
        width: 16px;
      }
      .kv-value {
        font-size: 0.85rem;
      }
    }

    /* Status Badge */
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 12px;
      border-radius: 20px;
      font-size: 0.82rem;
      font-weight: 600;
    }

    .status-badge.under-review {
      background: #fef3c7;
      color: #b45309;
    }

    .status-badge.approved {
      background: #dcfce7;
      color: #15803d;
    }

    .status-badge.rejected {
      background: #fee2e2;
      color: #b91c1c;
    }

    /* Info Notice Box */
    .info-notice-box {
      background: #fff8eb;
      border: 1px solid #fed7aa;
      border-radius: 12px;
      padding: 14px 18px;
      margin-bottom: 28px;
      display: flex;
      align-items: flex-start;
      gap: 14px;
      text-align: left;
    }

    .info-icon-circle {
      width: 24px;
      height: 24px;
      background: #d97706;
      color: #ffffff;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.8rem;
      font-weight: 700;
      flex-shrink: 0;
      margin-top: 1px;
    }

    .info-notice-text {
      color: #4b5563;
      font-size: 0.88rem;
      line-height: 1.45;
    }

    /* 4-Stage Horizontal Stepper */
    .stepper-horizontal {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      position: relative;
      margin-bottom: 34px;
      padding: 0 6px;
    }

    .stepper-step {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      flex: 1;
      position: relative;
      z-index: 2;
    }

    .step-circle {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f1f5f9;
      border: 1.5px solid #cbd5e1;
      color: #94a3b8;
      font-size: 0.95rem;
      margin-bottom: 8px;
      transition: all 0.3s ease;
    }

    .stepper-step.completed .step-circle {
      background: #0066f5;
      border-color: #0066f5;
      color: #ffffff;
    }

    .stepper-step.active .step-circle {
      background: #0066f5;
      border-color: #0066f5;
      color: #ffffff;
      box-shadow: 0 4px 12px rgba(0, 102, 245, 0.35);
    }

    .step-label {
      font-size: 0.85rem;
      font-weight: 700;
      color: #0c2044;
      line-height: 1.25;
      margin-bottom: 3px;
    }

    .stepper-step:not(.active):not(.completed) .step-label {
      color: #475569;
      font-weight: 600;
    }

    .step-sub {
      font-size: 0.78rem;
      color: #64748b;
      font-weight: 400;
    }

    .stepper-step:not(.active):not(.completed) .step-sub {
      color: #94a3b8;
    }

    .stepper-line {
      flex: 1;
      height: 3px;
      background: #cbd5e1;
      margin-top: 20px;
      position: relative;
      z-index: 1;
    }

    .stepper-line.active {
      background: #0066f5;
    }

    @media (max-width: 576px) {
      .step-circle {
        width: 32px;
        height: 32px;
        font-size: 0.8rem;
      }
      .stepper-line {
        margin-top: 16px;
      }
      .step-label {
        font-size: 0.75rem;
      }
      .step-sub {
        font-size: 0.7rem;
      }
    }

    /* Action Buttons Row */
    .action-buttons-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 16px;
    }

    .btn-back-home {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 10px 22px;
      background: #ffffff;
      color: #0066f5;
      border: 1.5px solid #0066f5;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.95rem;
      text-decoration: none;
      transition: all 0.2s ease;
    }

    .btn-back-home:hover {
      background: #eff6ff;
      color: #0052cc;
    }

    .btn-view-details {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 10px 24px;
      background: #0066f5;
      color: #ffffff;
      border: 1.5px solid #0066f5;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.95rem;
      text-decoration: none;
      box-shadow: 0 4px 14px rgba(0, 102, 245, 0.25);
      transition: all 0.2s ease;
    }

    .btn-view-details:hover {
      background: #0052cc;
      border-color: #0052cc;
    }

    @media (max-width: 576px) {
      .action-buttons-row {
        flex-direction: column;
        gap: 12px;
      }
      .btn-back-home, .btn-view-details {
        width: 100%;
        justify-content: center;
      }
    }

    /* Sign out row */
    .signout-row {
      margin-top: 24px;
    }

    .btn-signout {
      background: transparent;
      border: none;
      color: #94a3b8;
      font-size: 0.85rem;
      cursor: pointer;
      transition: color 0.2s ease;
    }

    .btn-signout:hover {
      color: #64748b;
      text-decoration: underline;
    }
  `]
})
export class AccountPendingComponent implements OnInit {
  appDetails: ApplicationDetails | null = null;
  loading = true;
  error = '';

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    const cached = this.authService.getPendingApplicationDetails();
    if (cached) {
      this.appDetails = cached;
    }
    this.loadApplicationDetails();
  }

  loadApplicationDetails(): void {
    this.loading = true;
    this.error = '';

    this.authService.getApplicationDetails().subscribe({
      next: (details) => {
        this.appDetails = details;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err.error?.message || 'Unable to load application details. Please check your connection or sign in again.';
      }
    });
  }

  get isUnderReview(): boolean {
    const s = this.appDetails?.applicationStatus?.toUpperCase();
    return !s || s === 'UNDER_REVIEW';
  }

  get isApproved(): boolean {
    return this.appDetails?.applicationStatus?.toUpperCase() === 'APPROVED';
  }

  get isRejected(): boolean {
    return this.appDetails?.applicationStatus?.toUpperCase() === 'REJECTED';
  }

  get isAccountActive(): boolean {
    return this.appDetails?.accountStatus?.toUpperCase() === 'ACTIVE';
  }

  get displayStatus(): string {
    if (this.isUnderReview) return 'Under Review';
    if (this.isApproved) return 'Approved';
    if (this.isRejected) return 'Rejected';
    return this.appDetails?.applicationStatus || 'Under Review';
  }

  get statusBadgeClass(): string {
    if (this.isUnderReview) return 'under-review';
    if (this.isApproved) return 'approved';
    if (this.isRejected) return 'rejected';
    return 'under-review';
  }

  get formattedSubmittedOn(): string {
    if (!this.appDetails?.submittedOn) {
      return 'Not provided';
    }
    try {
      const d = new Date(this.appDetails.submittedOn);
      if (isNaN(d.getTime())) return this.appDetails.submittedOn;
      const day = d.getDate();
      const month = d.toLocaleString('en-US', { month: 'short' });
      const year = d.getFullYear();
      let hours = d.getHours();
      const minutes = d.getMinutes().toString().padStart(2, '0');
      const ampm = hours >= 12 ? 'PM' : 'AM';
      hours = hours % 12;
      hours = hours ? hours : 12;
      const strHours = hours.toString().padStart(2, '0');
      return `${day} ${month} ${year}, ${strHours}:${minutes} ${ampm}`;
    } catch {
      return this.appDetails.submittedOn;
    }
  }

  get formattedDateOnly(): string {
    if (!this.appDetails?.submittedOn) {
      return 'Not provided';
    }
    try {
      const d = new Date(this.appDetails.submittedOn);
      if (isNaN(d.getTime())) return this.appDetails.submittedOn;
      const day = d.getDate();
      const month = d.toLocaleString('en-US', { month: 'short' });
      const year = d.getFullYear();
      return `${day} ${month} ${year}`;
    } catch {
      return 'Not provided';
    }
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
