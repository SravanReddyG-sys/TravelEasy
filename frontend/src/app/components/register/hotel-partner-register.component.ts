import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-hotel-partner-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="container py-5">
      <div class="row justify-content-center">
        <div class="col-lg-10">
          <div class="card shadow-lg border-0 rounded-4 p-4 p-md-5">
            <a routerLink="/register" class="text-decoration-none text-muted small fw-semibold d-inline-flex align-items-center mb-4">
              <i class="fa-solid fa-arrow-left me-2"></i> Back to account selection
            </a>

            <div class="d-flex align-items-center mb-4">
              <div class="bg-primary text-white rounded-circle d-flex align-items-center justify-content-center me-3" style="width: 50px; height: 50px;">
                <i class="fa-solid fa-hotel fs-4"></i>
              </div>
              <div>
                <h2 class="fw-bold text-dark mb-0">Hotel Partner <span class="text-primary">Registration</span></h2>
                <p class="text-muted small mb-0">Submit your hotel organization application for administrator verification.</p>
              </div>
            </div>

            <div *ngIf="errorMessage" class="alert alert-danger py-2 small mb-4">
              <i class="fa-solid fa-circle-exclamation me-1"></i> {{ errorMessage }}
            </div>

            <form (ngSubmit)="onSubmit()">
              <!-- Section 1: Account & Representative Details -->
              <div class="bg-light p-3.5 rounded-3 mb-4 border">
                <h5 class="fw-bold text-primary mb-3">
                  <i class="fa-solid fa-user-tie me-2"></i>Representative & Account Details
                </h5>
                <div class="row g-3">
                  <div class="col-md-6">
                    <label class="form-label fw-semibold text-secondary small">First Name <span class="text-danger">*</span></label>
                    <input type="text" class="form-control" [(ngModel)]="firstName" name="firstName" required placeholder="Representative First Name">
                  </div>
                  <div class="col-md-6">
                    <label class="form-label fw-semibold text-secondary small">Last Name <span class="text-danger">*</span></label>
                    <input type="text" class="form-control" [(ngModel)]="lastName" name="lastName" required placeholder="Representative Last Name">
                  </div>
                  <div class="col-md-6">
                    <label class="form-label fw-semibold text-secondary small">Account Email Address <span class="text-danger">*</span></label>
                    <input type="email" class="form-control" [(ngModel)]="email" name="email" required placeholder="manager@hotelpartner.com">
                  </div>
                  <div class="col-md-6">
                    <label class="form-label fw-semibold text-secondary small">Personal / Mobile Phone <span class="text-danger">*</span></label>
                    <input type="tel" class="form-control" [(ngModel)]="phone" name="phone" required placeholder="+91 9876543210">
                  </div>
                  <div class="col-md-6">
                    <label class="form-label fw-semibold text-secondary small">Representative Designation <span class="text-danger">*</span></label>
                    <input type="text" class="form-control" [(ngModel)]="representativeDesignation" name="representativeDesignation" required placeholder="e.g. General Manager / Hotel Owner">
                  </div>
                  <div class="col-md-3">
                    <label class="form-label fw-semibold text-secondary small">Password <span class="text-danger">*</span></label>
                    <input type="password" class="form-control" [(ngModel)]="password" name="password" required placeholder="••••••••">
                  </div>
                  <div class="col-md-3">
                    <label class="form-label fw-semibold text-secondary small">Confirm Password <span class="text-danger">*</span></label>
                    <input type="password" class="form-control" [(ngModel)]="confirmPassword" name="confirmPassword" required placeholder="••••••••">
                  </div>
                </div>
              </div>

              <!-- Section 2: Property & License Details -->
              <div class="bg-light p-3.5 rounded-3 mb-4 border">
                <h5 class="fw-bold text-primary mb-3">
                  <i class="fa-solid fa-building-circle-check me-2"></i>Organization & Property Legitimacy
                </h5>
                <div class="row g-3">
                  <div class="col-md-6">
                    <label class="form-label fw-semibold text-secondary small">Legal Organization Name <span class="text-danger">*</span></label>
                    <input type="text" class="form-control" [(ngModel)]="legalOrganizationName" name="legalOrganizationName" required placeholder="Full registered business / hotel organization name">
                  </div>
                  <div class="col-md-6">
                    <label class="form-label fw-semibold text-secondary small">Business Structure <span class="text-danger">*</span></label>
                    <select class="form-select" [(ngModel)]="businessStructure" name="businessStructure" required>
                      <option value="" disabled selected>Select business structure</option>
                      <option value="Proprietorship">Sole Proprietorship</option>
                      <option value="Partnership">Partnership Firm</option>
                      <option value="LLP">Limited Liability Partnership (LLP)</option>
                      <option value="Private Limited">Private Limited Company</option>
                      <option value="Public Limited">Public Limited Company</option>
                      <option value="Other">Other Authorized Entity</option>
                    </select>
                  </div>
                  <div class="col-md-6">
                    <label class="form-label fw-semibold text-secondary small">Company / Organization License Number (12 Digits) <span class="text-danger">*</span></label>
                    <input type="text" class="form-control" [(ngModel)]="companyLicenseNumber" name="companyLicenseNumber" required maxlength="12" placeholder="e.g. 123456789012">
                  </div>
                  <div class="col-md-3">
                    <label class="form-label fw-semibold text-secondary small">Business PAN Number <span class="text-danger">*</span></label>
                    <input type="text" class="form-control text-uppercase" [(ngModel)]="businessPanNumber" name="businessPanNumber" required maxlength="10" placeholder="ABCDE1234F">
                  </div>
                  <div class="col-md-3">
                    <label class="form-label fw-semibold text-secondary small">GSTIN Number (Optional)</label>
                    <input type="text" class="form-control text-uppercase" [(ngModel)]="gstinNumber" name="gstinNumber" maxlength="15" placeholder="22AAAAA0000A1Z5">
                  </div>
                  <div class="col-12">
                    <label class="form-label fw-semibold text-secondary small">Registered Business Address <span class="text-danger">*</span></label>
                    <textarea class="form-control" [(ngModel)]="registeredAddress" name="registeredAddress" rows="2" required placeholder="Complete registered hotel office address with postal pincode"></textarea>
                  </div>
                  <div class="col-md-6">
                    <label class="form-label fw-semibold text-secondary small">Business Contact Email <span class="text-danger">*</span></label>
                    <input type="email" class="form-control" [(ngModel)]="businessContactEmail" name="businessContactEmail" required placeholder="reservations@hotelpartner.com">
                  </div>
                  <div class="col-md-6">
                    <label class="form-label fw-semibold text-secondary small">Business Contact Phone <span class="text-danger">*</span></label>
                    <input type="tel" class="form-control" [(ngModel)]="businessContactPhone" name="businessContactPhone" required placeholder="+91 8012345678">
                  </div>
                </div>
              </div>

              <!-- Terms Acceptance -->
              <div class="form-check mb-4">
                <input class="form-check-input" type="checkbox" id="termsHotel" [(ngModel)]="termsAccepted" name="termsAccepted" required>
                <label class="form-check-label small text-muted" for="termsHotel">
                  I certify that all details supplied are accurate and agree to the Travel Easy Partner Terms & Conditions.
                </label>
              </div>

              <div class="d-flex justify-content-end">
                <button type="submit" class="btn btn-primary px-5 py-2.5 fw-bold rounded-3 shadow-sm" [disabled]="loading">
                  <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
                  Submit Application for Review
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class HotelPartnerRegisterComponent {
  firstName = '';
  lastName = '';
  email = '';
  phone = '';
  password = '';
  confirmPassword = '';
  representativeDesignation = '';
  legalOrganizationName = '';
  businessStructure = '';
  companyLicenseNumber = '';
  businessPanNumber = '';
  gstinNumber = '';
  registeredAddress = '';
  businessContactEmail = '';
  businessContactPhone = '';
  termsAccepted = false;
  loading = false;
  errorMessage = '';

  constructor(private authService: AuthService, private router: Router) {}

  onSubmit() {
    this.errorMessage = '';

    if (!this.firstName || !this.lastName || !this.email || !this.phone || !this.password) {
      this.errorMessage = 'Please complete all required representative fields.';
      return;
    }
    if (!this.representativeDesignation) {
      this.errorMessage = 'Representative designation is required.';
      return;
    }
    if (!this.legalOrganizationName || !this.businessStructure || !this.registeredAddress) {
      this.errorMessage = 'Please complete all required organization details.';
      return;
    }
    if (!/^\d{12}$/.test(this.companyLicenseNumber)) {
      this.errorMessage = 'Company / Organization License Number must be exactly 12 numeric digits.';
      return;
    }
    if (!/^[A-Z]{5}[0-9]{4}[A-Z]{1}$/i.test(this.businessPanNumber)) {
      this.errorMessage = 'Business PAN must follow standard 10-character PAN format (e.g. ABCDE1234F).';
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.errorMessage = 'Password and Confirm Password do not match.';
      return;
    }
    if (!this.termsAccepted) {
      this.errorMessage = 'Terms & Conditions must be accepted.';
      return;
    }

    this.loading = true;

    const payload = {
      firstName: this.firstName,
      lastName: this.lastName,
      email: this.email,
      phone: this.phone,
      password: this.password,
      confirmPassword: this.confirmPassword,
      representativeDesignation: this.representativeDesignation,
      legalOrganizationName: this.legalOrganizationName,
      businessStructure: this.businessStructure,
      companyLicenseNumber: this.companyLicenseNumber,
      businessPanNumber: this.businessPanNumber.toUpperCase(),
      gstinNumber: this.gstinNumber ? this.gstinNumber.toUpperCase() : '',
      registeredAddress: this.registeredAddress,
      businessContactEmail: this.businessContactEmail,
      businessContactPhone: this.businessContactPhone,
      termsAccepted: this.termsAccepted
    };

    this.authService.registerHotelPartner(payload).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigate(['/account-pending']);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || err.message || 'Application submission failed. Please check inputs.';
      }
    });
  }
}
