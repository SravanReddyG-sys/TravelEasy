import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ApplicationDetails } from '../../models/application-details.model';

@Component({
  selector: 'app-application-details',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="app-details-page">
      <div class="app-details-layout">
        <!-- ================= LEFT BRANDING PANEL ================= -->
        <!-- ================= RIGHT CONTENT CARD ================= -->
        <div class="right-content-card">
          <!-- Top Header & Status Badge -->
          <div class="header-status-row">
            <div class="header-left">
              <h1 class="details-main-heading">Application Details</h1>
              <p class="details-subheading">
                View the details of your partner registration application.
              </p>
            </div>

            <div class="header-right">
              <div class="status-badge" [ngClass]="statusBadgeClass">
                <i class="fa-regular fa-clock" *ngIf="isUnderReview"></i>
                <i class="fa-solid fa-check" *ngIf="isApproved"></i>
                <i class="fa-solid fa-xmark" *ngIf="isRejected"></i>
                <span>{{ displayStatus }}</span>
              </div>
              <div class="submitted-timestamp" *ngIf="appDetails?.submittedOn">
                Submitted on {{ formattedSubmittedOn }}
              </div>
            </div>
          </div>

          <!-- Loading State -->
          <div *ngIf="loading" class="text-center py-5">
            <div class="spinner-border text-primary" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
            <p class="text-muted mt-2">Retrieving application details...</p>
          </div>

          <!-- Error State -->
          <div *ngIf="error && !loading" class="alert alert-warning py-3 px-4 mb-4 d-flex align-items-center justify-content-between">
            <div>
              <i class="fa-solid fa-circle-exclamation me-2"></i>
              <span>{{ error }}</span>
            </div>
            <button class="btn btn-sm btn-outline-dark" (click)="loadDetails()">Retry</button>
          </div>

          <!-- Main Content Cards -->
          <div *ngIf="!loading && appDetails" class="sections-container">
            <!-- Section 1: Application Information -->
            <div class="section-card">
              <div class="section-card-header">
                <div class="section-icon-box">
                  <i class="fa-solid fa-file-lines"></i>
                </div>
                <h2 class="section-title">Application Information</h2>
              </div>

              <div class="kv-grid">
                <div class="kv-row">
                  <div class="kv-label">Organization Name</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ appDetails.organizationName || 'Not provided' }}</div>
                </div>

                <div class="kv-row">
                  <div class="kv-label">Application Type</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ appDetails.applicationType || 'Not provided' }}</div>
                </div>

                <div class="kv-row">
                  <div class="kv-label">Submitted On</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ formattedSubmittedOn }}</div>
                </div>

                <div class="kv-row">
                  <div class="kv-label">Application Status</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">
                    <span class="status-badge status-badge-inline" [ngClass]="statusBadgeClass">
                      <i class="fa-regular fa-clock" *ngIf="isUnderReview"></i>
                      <i class="fa-solid fa-check" *ngIf="isApproved"></i>
                      <i class="fa-solid fa-xmark" *ngIf="isRejected"></i>
                      <span>{{ displayStatus }}</span>
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Section 2: Representative / Account Details -->
            <div class="section-card">
              <div class="section-card-header">
                <div class="section-icon-box">
                  <i class="fa-solid fa-user"></i>
                </div>
                <h2 class="section-title">Representative / Account Details</h2>
              </div>

              <div class="kv-grid">
                <div class="kv-row">
                  <div class="kv-label">First Name</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ appDetails.firstName || 'Not provided' }}</div>
                </div>

                <div class="kv-row">
                  <div class="kv-label">Last Name</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ appDetails.lastName || 'Not provided' }}</div>
                </div>

                <div class="kv-row">
                  <div class="kv-label">Email Address</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ appDetails.email || 'Not provided' }}</div>
                </div>

                <div class="kv-row">
                  <div class="kv-label">Mobile Phone</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ appDetails.phone || 'Not provided' }}</div>
                </div>

                <div class="kv-row">
                  <div class="kv-label">Representative Designation</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ formatDesignation(appDetails.representativeDesignation) }}</div>
                </div>
              </div>
            </div>

            <!-- Section 3: Business Details -->
            <div class="section-card">
              <div class="section-card-header">
                <div class="section-icon-box">
                  <i class="fa-solid fa-building"></i>
                </div>
                <h2 class="section-title">Business Details</h2>
              </div>

              <div class="kv-grid">
                <!-- Legal Organization Name -->
                <div class="kv-row">
                  <div class="kv-label">Legal Organization Name</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ appDetails.legalOrganizationName || appDetails.organizationName || 'Not provided' }}</div>
                </div>

                <!-- Business Structure -->
                <div class="kv-row">
                  <div class="kv-label">Business Structure</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ formatBusinessStructure(appDetails.businessStructure) }}</div>
                </div>

                <!-- Passenger Transport Permit Number (for Bus Operator) -->
                <div *ngIf="isBusOperator" class="kv-row">
                  <div class="kv-label">Passenger Transport Permit Number</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ appDetails.licensePermitNumber || 'Not provided' }}</div>
                </div>

                <!-- Hotel License Number (for Hotel Partner) -->
                <div *ngIf="isHotelPartner" class="kv-row">
                  <div class="kv-label">Hotel License Number</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ appDetails.licensePermitNumber || 'Not provided' }}</div>
                </div>

                <!-- License / Permit Number fallback if role is neither -->
                <div *ngIf="!isBusOperator && !isHotelPartner && appDetails.licensePermitNumber" class="kv-row">
                  <div class="kv-label">License / Permit Number</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ appDetails.licensePermitNumber }}</div>
                </div>

                <!-- Business PAN Number -->
                <div class="kv-row">
                  <div class="kv-label">Business PAN Number</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val font-monospace">{{ appDetails.businessPanNumber || 'Not provided' }}</div>
                </div>

                <!-- GSTIN Number -->
                <div class="kv-row">
                  <div class="kv-label">GSTIN Number</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val font-monospace">{{ appDetails.gstinNumber || 'Not provided' }}</div>
                </div>

                <!-- Registered Business Address -->
                <div class="kv-row align-items-start">
                  <div class="kv-label">Registered Business Address</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val multiline-val">{{ formattedAddress }}</div>
                </div>

                <!-- Business Contact Email -->
                <div class="kv-row">
                  <div class="kv-label">Business Contact Email</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ appDetails.businessContactEmail || 'Not provided' }}</div>
                </div>

                <!-- Business Contact Phone -->
                <div class="kv-row">
                  <div class="kv-label">Business Contact Phone</div>
                  <div class="kv-sep">:</div>
                  <div class="kv-val">{{ appDetails.businessContactPhone || 'Not provided' }}</div>
                </div>
              </div>
            </div>
          </div>

          <!-- Bottom Navigation Button -->
          <div class="bottom-actions-row">
            <a routerLink="/account-pending" class="btn-back-status">
              <i class="fa-solid fa-arrow-left"></i>
              <span>Back to Application Status</span>
            </a>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      font-family: 'Segoe UI', system-ui, -apple-system, BlinkMacSystemFont, Roboto, sans-serif;
      color: #0f172a;
    }

    .app-details-page {
      min-height: 100vh;
      background: linear-gradient(180deg, #e4f2fe 0%, #edf5fc 40%, #f4f9fd 100%);
      padding: 32px 40px;
      display: flex;
      justify-content: center;
      align-items: center;
    }

    .app-details-layout {
      display: flex;
      width: 100%;
      max-width: 1380px;
      gap: 36px;
      align-items: stretch;
      min-height: calc(100vh - 64px);
    }

    /* ================= LEFT BRANDING PANEL ================= */
    .left-branding-panel {
      flex: 0 0 31%;
      max-width: 400px;
      min-width: 290px;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      position: relative;
    }

    .branding-top-content {
      padding: 16px 8px 16px 4px;
    }

    .brand-logo {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      text-decoration: none;
      margin-bottom: 24px;
    }

    .brand-icon {
      font-size: 1.45rem;
      color: #0066f5;
    }

    .brand-text {
      font-size: 1.4rem;
      font-weight: 800;
      color: #0c2044;
      letter-spacing: -0.02em;
    }

    .brand-blue {
      color: #0066f5;
    }

    .panel-main-title {
      font-size: 2rem;
      font-weight: 800;
      color: #0c2044;
      line-height: 1.2;
      margin: 0 0 10px;
      letter-spacing: -0.02em;
    }

    .panel-subtitle {
      font-size: 0.95rem;
      color: #475569;
      line-height: 1.5;
      margin: 0;
      max-width: 320px;
    }

    .scenic-image-container {
      flex: 1;
      min-height: 380px;
      border-radius: 20px;
      overflow: hidden;
      margin-top: 24px;
      box-shadow: 0 12px 36px rgba(12, 32, 68, 0.1);
      position: relative;
    }

    .scenic-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      object-position: 15% bottom;
      display: block;
    }

    /* ================= RIGHT CONTENT CARD ================= */
    .right-content-card {
      flex: 1;
      background: #ffffff;
      border-radius: 24px;
      padding: 36px 44px;
      box-shadow: 0 10px 40px rgba(12, 32, 68, 0.08);
      border: 1px solid rgba(226, 232, 240, 0.9);
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      overflow-y: auto;
    }

    /* Header & Status Row */
    .header-status-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 20px;
      margin-bottom: 24px;
    }

    .details-main-heading {
      font-size: 1.75rem;
      font-weight: 800;
      color: #0c2044;
      margin: 0 0 6px;
      letter-spacing: -0.02em;
    }

    .details-subheading {
      font-size: 0.95rem;
      color: #64748b;
      margin: 0;
    }

    .header-right {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      flex-shrink: 0;
    }

    .submitted-timestamp {
      font-size: 0.8rem;
      color: #64748b;
      margin-top: 6px;
      text-align: right;
    }

    /* Status Badge */
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      border-radius: 9999px;
      font-size: 0.85rem;
      font-weight: 600;
    }

    .status-badge-inline {
      padding: 4px 12px;
      font-size: 0.82rem;
    }

    .status-badge.under-review {
      background: #fef3c7;
      color: #b45309;
      border: 1px solid #fde68a;
    }

    .status-badge.approved {
      background: #dcfce7;
      color: #15803d;
      border: 1px solid #bbf7d0;
    }

    .status-badge.rejected {
      background: #fee2e2;
      color: #b91c1c;
      border: 1px solid #fecaca;
    }

    /* Section Cards */
    .sections-container {
      display: flex;
      flex-direction: column;
      gap: 16px;
      margin-bottom: 28px;
    }

    .section-card {
      background: #f0f7fe;
      border: 1px solid #dbeafe;
      border-radius: 12px;
      padding: 20px 24px;
      transition: box-shadow 0.2s ease;
    }

    .section-card-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 14px;
    }

    .section-icon-box {
      width: 28px;
      height: 28px;
      background: #0066f5;
      color: #ffffff;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.88rem;
      flex-shrink: 0;
    }

    .section-title {
      font-size: 1rem;
      font-weight: 700;
      color: #0c2044;
      margin: 0;
    }

    /* Key-Value Grid */
    .kv-grid {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .kv-row {
      display: flex;
      align-items: center;
      font-size: 0.9rem;
      line-height: 1.45;
    }

    .kv-label {
      width: 185px;
      color: #475569;
      font-weight: 500;
      flex-shrink: 0;
    }

    .kv-sep {
      width: 24px;
      color: #64748b;
      text-align: center;
      flex-shrink: 0;
    }

    .kv-val {
      color: #0c2044;
      font-weight: 500;
      flex: 1;
    }

    .multiline-val {
      white-space: pre-line;
      line-height: 1.5;
    }

    /* Bottom Actions Row */
    .bottom-actions-row {
      padding-top: 4px;
    }

    .btn-back-status {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 10px 20px;
      background: #ffffff;
      color: #0066f5;
      border: 1.5px solid #0066f5;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.9rem;
      text-decoration: none;
      transition: all 0.2s ease;
    }

    .btn-back-status:hover {
      background: #eff6ff;
      color: #0052cc;
      border-color: #0052cc;
    }

    /* Responsive */
    @media (max-width: 1100px) {
      .app-details-page {
        padding: 24px 20px;
      }
      .app-details-layout {
        gap: 24px;
      }
      .right-content-card {
        padding: 28px 30px;
      }
      .kv-label {
        width: 160px;
      }
    }

    @media (max-width: 900px) {
      .app-details-layout {
        flex-direction: column;
        min-height: auto;
      }
      .left-branding-panel {
        flex: none;
        max-width: 100%;
        min-width: 100%;
        padding: 8px 4px 16px;
      }
      .scenic-image-container {
        min-height: 220px;
        max-height: 260px;
        margin-top: 16px;
      }
      .right-content-card {
        padding: 24px 20px;
      }
    }

    @media (max-width: 576px) {
      .app-details-page {
        padding: 12px;
      }
      .right-content-card {
        padding: 20px 16px;
        border-radius: 18px;
      }
      .header-status-row {
        flex-direction: column;
        align-items: flex-start;
        gap: 12px;
      }
      .header-right {
        align-items: flex-start;
      }
      .kv-row {
        flex-direction: column;
        align-items: flex-start;
        gap: 2px;
      }
      .kv-label {
        width: 100%;
        color: #64748b;
        font-size: 0.82rem;
      }
      .kv-sep {
        display: none;
      }
      .kv-val {
        width: 100%;
      }
      .section-card {
        padding: 16px 14px;
      }
    }
  `]
})
export class ApplicationDetailsComponent implements OnInit {
  appDetails: ApplicationDetails | null = null;
  loading = true;
  error = '';

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    const cached = this.authService.getPendingApplicationDetails();
    if (cached) {
      this.appDetails = cached;
    }
    this.loadDetails();
  }

  loadDetails(): void {
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

  get scenicImage(): string {
    if (this.isBusOperator) {
      return 'assets/images/bus-operator-registration-background.png';
    }
    return 'assets/images/hotel-partner-registration-background.png';
  }

  get isBusOperator(): boolean {
    return this.appDetails?.role === 'ROLE_BUS_OPERATOR';
  }

  get isHotelPartner(): boolean {
    return this.appDetails?.role === 'ROLE_HOTEL_MANAGER';
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

  get formattedAddress(): string {
    const raw = this.appDetails?.registeredAddress;
    if (!raw) {
      return 'Not provided';
    }
    // If it contains newlines already, return as is
    if (raw.includes('\n')) {
      return raw;
    }
    // If comma-separated with multiple parts, split into readable lines
    const parts = raw.split(',').map(p => p.trim()).filter(p => p.length > 0);
    if (parts.length >= 4) {
      const line1 = parts.slice(0, 2).join(', ') + ',';
      const line2 = parts.slice(2, 4).join(', ') + ',';
      const line3 = parts.slice(4).join(', ');
      return `${line1}\n${line2}\n${line3}`;
    }
    return raw;
  }

  formatDesignation(desig?: string): string {
    if (!desig) return 'Not provided';
    const map: { [key: string]: string } = {
      'OWNER_PROPRIETOR': 'Owner / Proprietor',
      'MANAGING_DIRECTOR': 'Managing Director',
      'DIRECTOR': 'Director',
      'TRANSPORT_MANAGER': 'Transport Manager',
      'OPERATIONS_MANAGER': 'Operations Manager',
      'GENERAL_MANAGER': 'General Manager',
      'HOTEL_OWNER': 'Hotel Owner',
      'HOTEL_MANAGER': 'Hotel Manager',
      'PROPERTY_MANAGER': 'Property Manager',
      'RESERVATIONS_MANAGER': 'Reservations Manager',
      'FRONT_OFFICE_MANAGER': 'Front Office Manager',
      'AUTHORIZED_REPRESENTATIVE': 'Authorized Representative',
      'OTHER': 'Other'
    };
    return map[desig] || desig;
  }

  formatBusinessStructure(structure?: string): string {
    if (!structure) return 'Not provided';
    const map: { [key: string]: string } = {
      'Proprietorship': 'Sole Proprietorship',
      'Partnership': 'Partnership Firm',
      'LLP': 'Limited Liability Partnership (LLP)',
      'Private Limited': 'Private Limited Company',
      'Public Limited': 'Public Limited Company',
      'Other': 'Other Authorized Entity',
      'PROPRIETORSHIP': 'Sole Proprietorship',
      'PARTNERSHIP': 'Partnership Firm',
      'PRIVATE_LIMITED': 'Private Limited Company',
      'PUBLIC_LIMITED': 'Public Limited Company',
      'OTHER': 'Other Authorized Entity'
    };
    return map[structure] || structure;
  }
}
