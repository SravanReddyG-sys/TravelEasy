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
    <div class="wizard-page py-4 py-lg-5">
      <div class="container-fluid px-3 px-md-5 wizard-container">
        <div class="row align-items-center g-4 g-xl-5">

          <!-- Left Side Scenic / Progress Panel -->
          <div class="col-lg-4 col-xl-4 text-start scenic-panel d-flex flex-column justify-content-between">
            <div>
              <!-- Back to selection link -->
              <a routerLink="/register" class="back-link">
                <i class="fa-solid fa-arrow-left me-2"></i> Back to account selection
              </a>

              <!-- Main Heading -->
              <h1 class="wizard-main-title">Hotel Partner Registration</h1>
              <p class="wizard-main-sub">Create your hotel partner account.</p>

              <!-- Vertical Stepper Container -->
              <div class="stepper-wrap">
                <!-- Step 1 Indicator -->
                <div class="stepper-item" [class.cursor-pointer]="currentStep > 1" (click)="currentStep > 1 ? goToStep(1) : null">
                  <div class="stepper-circle" [class.active]="currentStep === 1" [class.completed]="currentStep > 1">
                    <span *ngIf="currentStep > 1"><i class="fa-solid fa-check"></i></span>
                    <span *ngIf="currentStep <= 1">1</span>
                  </div>
                  <div class="stepper-text">
                    <div class="stepper-title">Account Details</div>
                    <div class="stepper-desc">Login Information</div>
                  </div>
                </div>

                <!-- Connector 1 -->
                <div class="stepper-connector" [class.completed]="currentStep > 1"></div>

                <!-- Step 2 Indicator -->
                <div class="stepper-item" [class.cursor-pointer]="currentStep > 2" (click)="currentStep > 2 ? goToStep(2) : null">
                  <div class="stepper-circle" [class.active]="currentStep === 2" [class.completed]="currentStep > 2">
                    <span *ngIf="currentStep > 2"><i class="fa-solid fa-check"></i></span>
                    <span *ngIf="currentStep <= 2">2</span>
                  </div>
                  <div class="stepper-text">
                    <div class="stepper-title">Business Details</div>
                    <div class="stepper-desc">Organization Information</div>
                  </div>
                </div>

                <!-- Connector 2 -->
                <div class="stepper-connector" [class.completed]="currentStep > 2"></div>

                <!-- Step 3 Indicator -->
                <div class="stepper-item">
                  <div class="stepper-circle" [class.active]="currentStep === 3">
                    <span>3</span>
                  </div>
                  <div class="stepper-text">
                    <div class="stepper-title">Review & Submit</div>
                    <div class="stepper-desc">Verify and Complete</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Scenic Bottom Caption -->
            <div class="pt-4 pt-lg-5">
              <div class="scenic-caption">
                <i class="fa-solid fa-location-dot me-1"></i>
                <span>Host. Welcome. Grow-Together.</span>
              </div>
              <div class="scenic-progress-bar">
                <div class="scenic-progress-fill" [style.width.%]="(currentStep / 3) * 100"></div>
              </div>
            </div>
          </div>

          <!-- Right Side Form Card -->
          <div class="col-lg-8 col-xl-8">
            <div class="card wizard-card shadow-lg border-0">

              <!-- Global Form Error Message -->
              <div *ngIf="errorMessage" class="alert alert-danger py-2.5 px-3 small rounded-3 mb-4 d-flex align-items-center">
                <i class="fa-solid fa-circle-exclamation fs-5 me-2.5 flex-shrink-0"></i>
                <span>{{ errorMessage }}</span>
              </div>

              <!-- ======================================================== -->
              <!-- STEP 1: ACCOUNT DETAILS                                  -->
              <!-- ======================================================== -->
              <div *ngIf="currentStep === 1" class="wizard-step-content">
                <h2 class="step-heading">Account Details</h2>
                <p class="step-subheading">Enter your representative information to create your hotel partner account.</p>

                <form (ngSubmit)="nextStep1()" autocomplete="off">
                  <div class="row g-3">
                    <!-- First Name -->
                    <div class="col-md-6">
                      <label class="form-label field-label">First Name <span class="text-danger">*</span></label>
                      <div class="input-icon-group">
                        <i class="fa-regular fa-user input-icon"></i>
                        <input type="text" class="form-control" [(ngModel)]="firstName" name="firstName" required placeholder="Enter your first name">
                      </div>
                    </div>

                    <!-- Last Name -->
                    <div class="col-md-6">
                      <label class="form-label field-label">Last Name <span class="text-danger">*</span></label>
                      <div class="input-icon-group">
                        <i class="fa-regular fa-user input-icon"></i>
                        <input type="text" class="form-control" [(ngModel)]="lastName" name="lastName" required placeholder="Enter your last name">
                      </div>
                    </div>

                    <!-- Account Email Address -->
                    <div class="col-12">
                      <label class="form-label field-label">Account Email Address <span class="text-danger">*</span></label>
                      <div class="input-icon-group">
                        <i class="fa-regular fa-envelope input-icon"></i>
                        <input type="email" class="form-control" [(ngModel)]="email" name="email" required placeholder="you@example.com">
                      </div>
                    </div>

                    <!-- Personal / Mobile Phone -->
                    <div class="col-12">
                      <label class="form-label field-label">Personal / Mobile Phone <span class="text-danger">*</span></label>
                      <div class="input-group">
                        <div class="country-code-btn">
                          <svg class="flag-icon me-1" width="20" height="14" viewBox="0 0 225 150">
                            <rect width="225" height="50" fill="#FF9933"/>
                            <rect y="50" width="225" height="50" fill="#FFFFFF"/>
                            <rect y="100" width="225" height="50" fill="#128807"/>
                            <circle cx="112.5" cy="75" r="20" fill="none" stroke="#000080" stroke-width="3"/>
                            <circle cx="112.5" cy="75" r="4" fill="#000080"/>
                          </svg>
                          <span>+91</span>
                          <i class="fa-solid fa-chevron-down country-code-arrow ms-1"></i>
                        </div>
                        <div class="input-icon-group flex-grow-1">
                          <i class="fa-solid fa-phone input-icon"></i>
                          <input type="tel" class="form-control" [(ngModel)]="phone" name="phone" required placeholder="98765 43210">
                        </div>
                      </div>
                    </div>

                    <!-- Representative Designation -->
                    <div class="col-12">
                      <label class="form-label field-label">Representative Designation <span class="text-danger">*</span></label>
                      <div class="input-icon-group">
                        <i class="fa-solid fa-briefcase input-icon"></i>
                        <select class="form-select" [(ngModel)]="representativeDesignation" name="representativeDesignation" required>
                          <option value="" disabled selected>Select your designation</option>
                          <option *ngFor="let opt of designationOptions" [value]="opt.value">{{ opt.label }}</option>
                        </select>
                      </div>
                    </div>

                    <!-- Specify Designation (Conditional when Other) -->
                    <div *ngIf="representativeDesignation === 'OTHER'" class="col-12">
                      <label class="form-label field-label">Specify Designation <span class="text-danger">*</span></label>
                      <div class="input-icon-group">
                        <i class="fa-solid fa-pen-nib input-icon"></i>
                        <input type="text" class="form-control" [(ngModel)]="customDesignation" name="customDesignation" required placeholder="Specify your designation">
                      </div>
                    </div>

                    <!-- Password -->
                    <div class="col-md-6">
                      <label class="form-label field-label">Password <span class="text-danger">*</span></label>
                      <div class="input-icon-group">
                        <i class="fa-solid fa-lock input-icon"></i>
                        <input [type]="showPassword ? 'text' : 'password'" class="form-control pe-5" [(ngModel)]="password" name="password" required placeholder="Create a strong password">
                        <i class="fa-regular password-toggle" [class.fa-eye]="!showPassword" [class.fa-eye-slash]="showPassword" (click)="showPassword = !showPassword"></i>
                      </div>
                    </div>

                    <!-- Confirm Password -->
                    <div class="col-md-6">
                      <label class="form-label field-label">Confirm Password <span class="text-danger">*</span></label>
                      <div class="input-icon-group">
                        <i class="fa-solid fa-lock input-icon"></i>
                        <input [type]="showConfirmPassword ? 'text' : 'password'" class="form-control pe-5" [(ngModel)]="confirmPassword" name="confirmPassword" required placeholder="Re-enter your password">
                        <i class="fa-regular password-toggle" [class.fa-eye]="!showConfirmPassword" [class.fa-eye-slash]="showConfirmPassword" (click)="showConfirmPassword = !showConfirmPassword"></i>
                      </div>
                    </div>

                    <!-- Password Requirements Box -->
                    <div class="col-12">
                      <div class="password-info-box">
                        <i class="fa-solid fa-circle-info fs-5 flex-shrink-0"></i>
                        <span>Password must be at least 8 characters long and include at least one uppercase letter, one lowercase letter, one number and one special character.</span>
                      </div>
                    </div>

                    <!-- Terms and Conditions Checkbox -->
                    <div class="col-12 mt-3">
                      <div class="form-check d-flex align-items-center gap-2">
                        <input class="form-check-input mt-0 fs-6 cursor-pointer" type="checkbox" id="step1Terms" [(ngModel)]="step1Terms" name="step1Terms" required>
                        <label class="form-check-label small text-muted cursor-pointer" for="step1Terms">
                          I agree to the <a href="#" class="text-primary text-decoration-none fw-semibold" (click)="$event.preventDefault()">Terms and Conditions</a> and <a href="#" class="text-primary text-decoration-none fw-semibold" (click)="$event.preventDefault()">Privacy Policy</a>.
                        </label>
                      </div>
                    </div>
                  </div>

                  <!-- Next Button Row -->
                  <div class="d-flex justify-content-end align-items-center mt-4 pt-2">
                    <button type="submit" class="btn btn-primary wizard-next-btn">
                      Next: Business Details <i class="fa-solid fa-arrow-right ms-1"></i>
                    </button>
                  </div>
                </form>
              </div>

              <!-- ======================================================== -->
              <!-- STEP 2: BUSINESS DETAILS                                 -->
              <!-- ======================================================== -->
              <div *ngIf="currentStep === 2" class="wizard-step-content">
                <h2 class="step-heading">Business Details</h2>
                <p class="step-subheading">Provide your organization and hotel business information.</p>

                <form (ngSubmit)="nextStep2()" autocomplete="off">
                  <div class="row g-3">
                    <!-- Legal Organization Name -->
                    <div class="col-md-6">
                      <label class="form-label field-label">Legal Organization Name <span class="text-danger">*</span></label>
                      <div class="input-icon-group">
                        <i class="fa-regular fa-building input-icon"></i>
                        <input type="text" class="form-control" [(ngModel)]="legalOrganizationName" name="legalOrganizationName" required placeholder="Full registered business / hotel name">
                      </div>
                    </div>

                    <!-- Business Structure -->
                    <div class="col-md-6">
                      <label class="form-label field-label">Business Structure <span class="text-danger">*</span></label>
                      <div class="input-icon-group">
                        <i class="fa-solid fa-shield-halved input-icon"></i>
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
                    </div>

                    <!-- Hotel License Number -->
                    <div class="col-md-4">
                      <label class="form-label field-label">Hotel License Number (12 Digits) <span class="text-danger">*</span></label>
                      <div class="input-icon-group">
                        <i class="fa-regular fa-file-lines input-icon"></i>
                        <input type="text" class="form-control" [(ngModel)]="companyLicenseNumber" name="companyLicenseNumber" required maxlength="12" placeholder="e.g. 123456789012">
                      </div>
                    </div>

                    <!-- Business PAN Number -->
                    <div class="col-md-4">
                      <label class="form-label field-label">Business PAN Number <span class="text-danger">*</span></label>
                      <div class="input-icon-group">
                        <i class="fa-regular fa-address-card input-icon"></i>
                        <input type="text" class="form-control text-uppercase" [(ngModel)]="businessPanNumber" name="businessPanNumber" required maxlength="10" placeholder="ABCDE1234F">
                      </div>
                    </div>

                    <!-- GSTIN Number (Optional) -->
                    <div class="col-md-4">
                      <label class="form-label field-label">GSTIN Number (Optional)</label>
                      <div class="input-icon-group">
                        <i class="fa-solid fa-receipt input-icon"></i>
                        <input type="text" class="form-control text-uppercase" [(ngModel)]="gstinNumber" name="gstinNumber" maxlength="15" placeholder="22AAAAA0000A1Z5">
                      </div>
                    </div>

                    <!-- Registered Business Address Box -->
                    <div class="col-12 mt-3">
                      <div class="address-box">
                        <div class="address-box-title">
                          <i class="fa-solid fa-location-dot me-2 fs-5"></i>
                          <span>Registered Business Address</span>
                        </div>

                        <div class="row g-3">
                          <!-- Flat / Building / Office Number -->
                          <div class="col-md-6">
                            <label class="form-label field-label">Flat / Building / Office Number <span class="text-danger">*</span></label>
                            <div class="input-icon-group">
                              <i class="fa-solid fa-house-chimney input-icon"></i>
                              <input type="text" class="form-control" [(ngModel)]="flatBuildingOffice" name="flatBuildingOffice" required placeholder="e.g. No. 12, ABC Plaza">
                            </div>
                          </div>

                          <!-- Street / Road -->
                          <div class="col-md-6">
                            <label class="form-label field-label">Street / Road <span class="text-danger">*</span></label>
                            <div class="input-icon-group">
                              <i class="fa-solid fa-road input-icon"></i>
                              <input type="text" class="form-control" [(ngModel)]="streetRoad" name="streetRoad" required placeholder="e.g. MG Road">
                            </div>
                          </div>

                          <!-- Landmark -->
                          <div class="col-md-4">
                            <label class="form-label field-label">Landmark (Optional)</label>
                            <div class="input-icon-group">
                              <i class="fa-solid fa-location-crosshairs input-icon"></i>
                              <input type="text" class="form-control" [(ngModel)]="landmark" name="landmark" placeholder="e.g. Near Central Park">
                            </div>
                          </div>

                          <!-- City / Locality -->
                          <div class="col-md-4">
                            <label class="form-label field-label">City / Locality <span class="text-danger">*</span></label>
                            <div class="input-icon-group">
                              <i class="fa-solid fa-city input-icon"></i>
                              <input type="text" class="form-control" [(ngModel)]="cityLocality" name="cityLocality" required placeholder="Enter city / locality">
                            </div>
                          </div>

                          <!-- State / Union Territory -->
                          <div class="col-md-4">
                            <label class="form-label field-label">State / Union Territory <span class="text-danger">*</span></label>
                            <div class="input-icon-group">
                              <i class="fa-solid fa-map-location-dot input-icon"></i>
                              <select class="form-select" [(ngModel)]="state" name="state" required>
                                <option value="" disabled selected>Select state</option>
                                <option *ngFor="let s of indianStatesAndUTs" [value]="s">{{ s }}</option>
                              </select>
                            </div>
                          </div>

                          <!-- Country -->
                          <div class="col-md-6">
                            <label class="form-label field-label">Country <span class="text-danger">*</span></label>
                            <div class="country-select-box">
                              <svg class="flag-icon me-2 flex-shrink-0" width="22" height="15" viewBox="0 0 225 150">
                                <rect width="225" height="50" fill="#FF9933"/>
                                <rect y="50" width="225" height="50" fill="#FFFFFF"/>
                                <rect y="100" width="225" height="50" fill="#128807"/>
                                <circle cx="112.5" cy="75" r="20" fill="none" stroke="#000080" stroke-width="3"/>
                                <circle cx="112.5" cy="75" r="4" fill="#000080"/>
                              </svg>
                              <select class="form-select border-0 bg-transparent p-0 fw-semibold text-dark flex-grow-1" [(ngModel)]="country" name="country" required style="box-shadow: none;">
                                <option value="India">India</option>
                              </select>
                            </div>
                          </div>

                          <!-- PIN / Postal Code -->
                          <div class="col-md-6">
                            <label class="form-label field-label">PIN / Postal Code <span class="text-danger">*</span></label>
                            <div class="input-icon-group">
                              <i class="fa-regular fa-file-lines input-icon"></i>
                              <input type="text" class="form-control" [(ngModel)]="pinCode" name="pinCode" required maxlength="6" placeholder="e.g. 520001">
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <!-- Business Contact Email -->
                    <div class="col-md-6 mt-3">
                      <label class="form-label field-label">Business Contact Email <span class="text-danger">*</span></label>
                      <div class="input-icon-group">
                        <i class="fa-regular fa-envelope input-icon"></i>
                        <input type="email" class="form-control" [(ngModel)]="businessContactEmail" name="businessContactEmail" required placeholder="contact@hotelpartner.com">
                      </div>
                    </div>

                    <!-- Business Contact Phone -->
                    <div class="col-md-6 mt-3">
                      <label class="form-label field-label">Business Contact Phone <span class="text-danger">*</span></label>
                      <div class="input-group">
                        <div class="country-code-btn">
                          <svg class="flag-icon me-1" width="20" height="14" viewBox="0 0 225 150">
                            <rect width="225" height="50" fill="#FF9933"/>
                            <rect y="50" width="225" height="50" fill="#FFFFFF"/>
                            <rect y="100" width="225" height="50" fill="#128807"/>
                            <circle cx="112.5" cy="75" r="20" fill="none" stroke="#000080" stroke-width="3"/>
                            <circle cx="112.5" cy="75" r="4" fill="#000080"/>
                          </svg>
                          <span>+91</span>
                          <i class="fa-solid fa-chevron-down country-code-arrow ms-1"></i>
                        </div>
                        <div class="input-icon-group flex-grow-1">
                          <i class="fa-solid fa-phone input-icon"></i>
                          <input type="tel" class="form-control" [(ngModel)]="businessContactPhone" name="businessContactPhone" required placeholder="80123 45678">
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- Bottom Navigation Buttons -->
                  <div class="d-flex justify-content-between align-items-center mt-4 pt-3">
                    <button type="button" class="btn wizard-back-btn" (click)="goToStep(1)">
                      <i class="fa-solid fa-arrow-left me-1"></i> Back: Account Details
                    </button>
                    <button type="submit" class="btn btn-primary wizard-next-btn">
                      Next: Review & Submit <i class="fa-solid fa-arrow-right ms-1"></i>
                    </button>
                  </div>
                </form>
              </div>

              <!-- ======================================================== -->
              <!-- STEP 3: REVIEW & SUBMIT                                  -->
              <!-- ======================================================== -->
              <div *ngIf="currentStep === 3" class="wizard-step-content">
                <h2 class="step-heading">Review Your Information</h2>
                <p class="step-subheading">Please review all details carefully before submitting your application.</p>

                <!-- Two Summary Cards Side by Side -->
                <div class="row g-4 mb-4">
                  <!-- Representative & Account Details Card -->
                  <div class="col-lg-6">
                    <div class="summary-card">
                      <div class="summary-card-header">
                        <div class="summary-card-title">
                          <i class="fa-solid fa-circle-user fs-5 text-primary"></i>
                          <span>Representative & Account Details</span>
                        </div>
                        <button type="button" class="edit-btn" (click)="goToStep(1)">
                          <i class="fa-solid fa-pen-to-square me-1"></i> Edit
                        </button>
                      </div>

                      <div class="summary-list">
                        <div class="summary-row">
                          <span class="summary-label">First Name</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val">{{ firstName }}</span>
                        </div>
                        <div class="summary-row">
                          <span class="summary-label">Last Name</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val">{{ lastName }}</span>
                        </div>
                        <div class="summary-row">
                          <span class="summary-label">Email Address</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val">{{ email }}</span>
                        </div>
                        <div class="summary-row">
                          <span class="summary-label">Mobile Phone</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val">{{ getFormattedPhone(phone) }}</span>
                        </div>
                        <div class="summary-row">
                          <span class="summary-label">Designation</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val">{{ getDisplayDesignation() }}</span>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- Business Details Card -->
                  <div class="col-lg-6">
                    <div class="summary-card">
                      <div class="summary-card-header">
                        <div class="summary-card-title">
                          <i class="fa-solid fa-city fs-5 text-primary"></i>
                          <span>Business Details</span>
                        </div>
                        <button type="button" class="edit-btn" (click)="goToStep(2)">
                          <i class="fa-solid fa-pen-to-square me-1"></i> Edit
                        </button>
                      </div>

                      <div class="summary-list">
                        <div class="summary-row">
                          <span class="summary-label">Organization Name</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val">{{ legalOrganizationName }}</span>
                        </div>
                        <div class="summary-row">
                          <span class="summary-label">Business Structure</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val">{{ getDisplayBusinessStructure() }}</span>
                        </div>
                        <div class="summary-row">
                          <span class="summary-label">Hotel License Number</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val">{{ companyLicenseNumber }}</span>
                        </div>
                        <div class="summary-row">
                          <span class="summary-label">Business PAN Number</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val text-uppercase">{{ businessPanNumber }}</span>
                        </div>
                        <div class="summary-row">
                          <span class="summary-label">GSTIN Number</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val text-uppercase">{{ gstinNumber ? gstinNumber : '-' }}</span>
                        </div>
                        <div class="summary-row">
                          <span class="summary-label">Business Address</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val address-multiline">{{ getFormattedAddressLines() }}</span>
                        </div>
                        <div class="summary-row">
                          <span class="summary-label">Business Contact Email</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val">{{ businessContactEmail }}</span>
                        </div>
                        <div class="summary-row">
                          <span class="summary-label">Business Contact Phone</span>
                          <span class="summary-sep">:</span>
                          <span class="summary-val">{{ getFormattedPhone(businessContactPhone) }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Terms & Accuracy Certification Checkbox -->
                <div class="certification-box mb-4">
                  <div class="form-check d-flex align-items-center gap-2">
                    <input class="form-check-input mt-0 fs-6 cursor-pointer" type="checkbox" id="termsCertified" [(ngModel)]="termsCertified" name="termsCertified" required>
                    <label class="form-check-label small text-dark cursor-pointer fw-medium" for="termsCertified">
                      I certify that all details supplied are accurate and agree to the Travel Easy Partner <a href="#" class="text-primary text-decoration-none fw-semibold" (click)="$event.preventDefault()">Terms & Conditions</a>.
                    </label>
                  </div>
                </div>

                <!-- Bottom Navigation Buttons -->
                <div class="d-flex justify-content-between align-items-center mt-4 pt-2">
                  <button type="button" class="btn wizard-back-btn" (click)="goToStep(2)">
                    <i class="fa-solid fa-arrow-left me-1"></i> Back: Business Details
                  </button>
                  <button type="button" class="btn btn-primary wizard-next-btn" (click)="onSubmit()" [disabled]="loading">
                    <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
                    Submit Application for Review <i *ngIf="!loading" class="fa-solid fa-arrow-right ms-1"></i>
                  </button>
                </div>
              </div>

            </div>
          </div>

        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      font-family: 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
    }

    /* Page container with exact background image */
    .wizard-page {
      min-height: 100vh;
      background-image: url('/assets/images/hotel-partner-registration-background.png');
      background-size: cover;
      background-position: center bottom;
      background-repeat: no-repeat;
      display: flex;
      align-items: center;
      position: relative;
    }

    .wizard-container {
      width: 100%;
      max-width: 1440px;
      margin: 0 auto;
      position: relative;
      z-index: 2;
    }

    /* Left Scenic Panel */
    .scenic-panel {
      min-height: 560px;
    }

    .back-link {
      display: inline-flex;
      align-items: center;
      color: #eff0f3;
      text-decoration: none;
      font-weight: 600;
      font-size: 1.25rem;
      margin-bottom: 2rem;
      transition: color 0.2s ease;
    }
    .back-link:hover {
      color: #f6f8fc;
    }

    .wizard-main-title {
      font-size: clamp(4rem, 2.7vw, 2.5rem);
      font-weight: 800;
      color: #ebedf1;
      letter-spacing: -0.6px;
      margin-bottom: 4px;
      line-height: 1.15;
    }

    .wizard-main-sub {
      font-size: 1.5rem;
      color: #475569;
      margin-bottom: 2.5rem;
      font-weight: 500;
    }

    /* Stepper Styling with Translucent Container */
    .stepper-wrap {
      background: rgba(255, 255, 255, 0.82);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.75);
      border-radius: 16px;
      padding: 22px 20px;
      box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
      margin-bottom: 2rem;
      max-width: 380px;
    }

    @supports not (backdrop-filter: blur(12px)) {
      .stepper-wrap {
        background: rgba(255, 255, 255, 0.94);
      }
    }

    .stepper-item {
      display: flex;
      align-items: center;
      gap: 16px;
      user-select: none;
    }

    .cursor-pointer {
      cursor: pointer;
    }

    .stepper-circle {
      width: 42px;
      height: 42px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 1rem;
      background: #ffffff;
      color: #0c2044;
      border: 1.5px solid #cbd5e1;
      flex-shrink: 0;
      transition: all 0.3s ease;
    }

    .stepper-circle.active {
      background: #0d6efd;
      color: #ffffff;
      border-color: #0d6efd;
      box-shadow: 0 4px 14px rgba(13, 110, 253, 0.35);
    }

    .stepper-circle.completed {
      background: #0d6efd;
      color: #ffffff;
      border-color: #0d6efd;
    }

    .stepper-connector {
      width: 2px;
      height: 28px;
      background: #cbd5e1;
      margin-left: 20px;
      margin-top: -2px;
      margin-bottom: -2px;
      transition: background 0.3s ease;
    }

    .stepper-connector.completed {
      background: #0d6efd;
    }

    .stepper-text {
      flex: 1;
      min-width: 0;
    }

    .stepper-title {
      font-weight: 700;
      font-size: 1rem;
      color: #0c2044;
      line-height: 1.3;
      margin-bottom: 2px;
    }

    .stepper-desc {
      font-size: 0.85rem;
      color: #475569;
      font-weight: 400;
      line-height: 1.35;
    }

    /* Scenic Caption & Bar */
    .scenic-caption {
      display: flex;
      align-items: center;
      color: #ffffff;
      text-shadow: 0 2px 6px rgba(0, 0, 0, 0.85);
      font-weight: 600;
      font-size: 1.05rem;
      margin-bottom: 12px;
    }

    .scenic-caption i {
      font-size: 1.2rem;
      filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.6));
    }

    .scenic-progress-bar {
      height: 4px;
      width: 140px;
      background: rgba(255, 255, 255, 0.45);
      border-radius: 4px;
      overflow: hidden;
    }

    .scenic-progress-fill {
      height: 100%;
      background: #0d6efd;
      border-radius: 4px;
      transition: width 0.35s ease;
    }

    /* Right Form Card */
    .wizard-card {
      background: #ffffff;
      border-radius: 20px;
      padding: 2.75rem 3.25rem;
      box-shadow: 0 15px 45px rgba(0, 0, 0, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.8);
    }

    .step-heading {
      font-size: 1.85rem;
      font-weight: 800;
      color: #0f172a;
      letter-spacing: -0.5px;
      margin-bottom: 6px;
      line-height: 1.2;
    }

    .step-subheading {
      font-size: 0.95rem;
      color: #64748b;
      margin-bottom: 24px;
    }

    .field-label {
      font-size: 0.85rem;
      font-weight: 600;
      color: #1e293b;
      margin-bottom: 6px;
    }

    /* Input Icon Group */
    .input-icon-group {
      position: relative;
    }

    .input-icon-group .input-icon {
      position: absolute;
      left: 14px;
      top: 50%;
      transform: translateY(-50%);
      color: #94a3b8;
      pointer-events: none;
      z-index: 3;
      font-size: 0.95rem;
    }

    .input-icon-group .form-control,
    .input-icon-group .form-select {
      padding-left: 42px;
      height: 46px;
      border-radius: 8px;
      border: 1px solid #cbd5e1;
      font-size: 0.92rem;
      color: #0f172a;
    }

    .input-icon-group .form-control:focus,
    .input-icon-group .form-select:focus {
      border-color: #3b82f6;
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
    }

    .password-toggle {
      position: absolute;
      right: 14px;
      top: 50%;
      transform: translateY(-50%);
      color: #94a3b8;
      cursor: pointer;
      z-index: 4;
      font-size: 0.95rem;
      padding: 6px;
      transition: color 0.2s ease;
    }
    .password-toggle:hover {
      color: #475569;
    }

    /* Country code button */
    .country-code-btn {
      height: 46px;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      background: #ffffff;
      padding: 0 12px;
      display: flex;
      align-items: center;
      gap: 6px;
      font-weight: 600;
      font-size: 0.92rem;
      color: #0f172a;
      flex-shrink: 0;
    }

    .country-code-arrow {
      font-size: 0.72rem;
      color: #64748b;
    }

    /* Password Requirements Alert Box */
    .password-info-box {
      background: #e0f2fe;
      border: 1px solid #bae6fd;
      border-radius: 10px;
      padding: 12px 16px;
      color: #0369a1;
      font-size: 0.85rem;
      display: flex;
      align-items: center;
      gap: 12px;
      line-height: 1.4;
    }

    /* Registered Address Box in Step 2 */
    .address-box {
      background: #f8faff;
      border: 1px solid #bfdbfe;
      border-radius: 14px;
      padding: 22px;
    }

    .address-box-title {
      color: #0d6efd;
      font-weight: 700;
      font-size: 1rem;
      margin-bottom: 16px;
      display: flex;
      align-items: center;
    }

    .country-select-box {
      height: 46px;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      background: #ffffff;
      padding: 0 14px;
      display: flex;
      align-items: center;
    }

    /* Summary Cards in Step 3 */
    .summary-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      padding: 22px;
      height: 100%;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
    }

    .summary-card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 18px;
      padding-bottom: 10px;
      border-bottom: 1px solid #f1f5f9;
    }

    .summary-card-title {
      color: #0d6efd;
      font-weight: 700;
      font-size: 1rem;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .edit-btn {
      color: #0d6efd;
      font-weight: 600;
      font-size: 0.88rem;
      text-decoration: none;
      display: flex;
      align-items: center;
      gap: 4px;
      background: none;
      border: none;
      padding: 0;
      cursor: pointer;
      transition: color 0.2s ease;
    }
    .edit-btn:hover {
      text-decoration: underline;
      color: #0b5ed7;
    }

    .summary-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .summary-row {
      display: flex;
      font-size: 0.88rem;
      line-height: 1.45;
    }

    .summary-label {
      width: 175px;
      flex-shrink: 0;
      color: #475569;
      font-weight: 500;
    }

    .summary-sep {
      width: 16px;
      color: #64748b;
      flex-shrink: 0;
      text-align: center;
    }

    .summary-val {
      color: #0f172a;
      font-weight: 600;
      flex: 1;
      word-break: break-word;
    }

    .summary-val.address-multiline {
      font-weight: 500;
    }

    /* Certification Box */
    .certification-box {
      background: #f0f7ff;
      border: 1px solid #bfdbfe;
      border-radius: 10px;
      padding: 14px 18px;
    }

    /* Buttons */
    .wizard-next-btn {
      background: #0d6efd;
      color: #ffffff;
      border: none;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.95rem;
      padding: 12px 28px;
      box-shadow: 0 4px 14px rgba(13, 110, 253, 0.25);
      display: inline-flex;
      align-items: center;
      gap: 8px;
      transition: all 0.2s ease;
    }
    .wizard-next-btn:hover:not(:disabled) {
      background: #0b5ed7;
      box-shadow: 0 6px 18px rgba(13, 110, 253, 0.35);
      transform: translateY(-1px);
    }

    .wizard-back-btn {
      background: #ffffff;
      color: #0d6efd;
      border: 1.5px solid #0d6efd;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.95rem;
      padding: 12px 24px;
      display: inline-flex;
      align-items: center;
      gap: 8px;
      transition: all 0.2s ease;
    }
    .wizard-back-btn:hover {
      background: #eff6ff;
      border-color: #0b5ed7;
      color: #0b5ed7;
    }

    /* Responsive */
    @media (max-width: 991.98px) {
      .wizard-card {
        padding: 2rem 1.5rem;
      }
      .scenic-panel {
        min-height: auto;
        margin-bottom: 2rem;
      }
      .summary-row {
        flex-direction: column;
        margin-bottom: 12px;
      }
      .summary-label {
        width: 100%;
        color: #64748b;
        font-size: 0.8rem;
      }
      .summary-sep {
        display: none;
      }
      .summary-val {
        padding-left: 0;
      }
    }
  `]
})
export class HotelPartnerRegisterComponent {
  currentStep = 1;

  // Step 1: Representative Details
  firstName = '';
  lastName = '';
  email = '';
  phone = '';
  password = '';
  confirmPassword = '';
  representativeDesignation = '';
  customDesignation = '';
  showPassword = false;
  showConfirmPassword = false;
  step1Terms = false;

  // Step 2: Organization Details
  legalOrganizationName = '';
  businessStructure = '';
  companyLicenseNumber = '';
  businessPanNumber = '';
  gstinNumber = '';

  // Structured Registered Business Address
  flatBuildingOffice = '';
  streetRoad = '';
  landmark = '';
  cityLocality = '';
  state = '';
  country = 'India';
  pinCode = '';

  businessContactEmail = '';
  businessContactPhone = '';

  // Step 3: Certification
  termsCertified = false;

  loading = false;
  errorMessage = '';

  designationOptions = [
    { value: 'HOTEL_OWNER', label: 'Hotel Owner' },
    { value: 'MANAGING_DIRECTOR', label: 'Managing Director' },
    { value: 'GENERAL_MANAGER', label: 'General Manager' },
    { value: 'HOTEL_MANAGER', label: 'Hotel Manager' },
    { value: 'OPERATIONS_MANAGER', label: 'Operations Manager' },
    { value: 'PROPERTY_MANAGER', label: 'Property Manager' },
    { value: 'RESERVATIONS_MANAGER', label: 'Reservations Manager' },
    { value: 'FRONT_OFFICE_MANAGER', label: 'Front Office Manager' },
    { value: 'AUTHORIZED_REPRESENTATIVE', label: 'Authorized Representative' },
    { value: 'OTHER', label: 'Other' }
  ];

  indianStatesAndUTs: string[] = [
    'Andaman and Nicobar Islands',
    'Andhra Pradesh',
    'Arunachal Pradesh',
    'Assam',
    'Bihar',
    'Chandigarh',
    'Chhattisgarh',
    'Dadra and Nagar Haveli and Daman and Diu',
    'Delhi',
    'Goa',
    'Gujarat',
    'Haryana',
    'Himachal Pradesh',
    'Jammu and Kashmir',
    'Jharkhand',
    'Karnataka',
    'Kerala',
    'Ladakh',
    'Lakshadweep',
    'Madhya Pradesh',
    'Maharashtra',
    'Manipur',
    'Meghalaya',
    'Mizoram',
    'Nagaland',
    'Odisha',
    'Puducherry',
    'Punjab',
    'Rajasthan',
    'Sikkim',
    'Tamil Nadu',
    'Telangana',
    'Tripura',
    'Uttar Pradesh',
    'Uttarakhand',
    'West Bengal'
  ];

  constructor(private authService: AuthService, private router: Router) {}

  goToStep(step: number) {
    this.errorMessage = '';
    this.currentStep = step;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  nextStep1() {
    this.errorMessage = '';

    if (!this.firstName || !this.firstName.trim()) {
      this.errorMessage = 'Representative First Name is required.';
      return;
    }
    if (!this.lastName || !this.lastName.trim()) {
      this.errorMessage = 'Representative Last Name is required.';
      return;
    }
    if (!this.email || !this.email.trim()) {
      this.errorMessage = 'Account Email Address is required.';
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(this.email.trim())) {
      this.errorMessage = 'Please enter a valid email address.';
      return;
    }
    if (!this.phone || !this.phone.trim()) {
      this.errorMessage = 'Personal / Mobile Phone number is required.';
      return;
    }
    const cleanPhone = this.phone.trim().replace(/^(\+91\s*)/, '').replace(/\s+/g, '');
    if (!/^\d{10}$/.test(cleanPhone)) {
      this.errorMessage = 'Please enter a valid 10-digit mobile number.';
      return;
    }
    if (!this.representativeDesignation) {
      this.errorMessage = 'Representative Designation is required.';
      return;
    }
    if (this.representativeDesignation === 'OTHER') {
      if (!this.customDesignation || !this.customDesignation.trim()) {
        this.errorMessage = 'Please specify your designation.';
        return;
      }
      if (this.customDesignation.trim().length < 2) {
        this.errorMessage = 'Custom designation must be at least 2 characters.';
        return;
      }
    }
    if (!this.password) {
      this.errorMessage = 'Password is required.';
      return;
    }
    const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#^()_+\-=\[\]{};':"\\|,.<>\/?]).{8,}$/;
    if (!passwordPattern.test(this.password)) {
      this.errorMessage = 'Password must be at least 8 characters long and include uppercase, lowercase, number, and special character.';
      return;
    }
    if (!this.confirmPassword) {
      this.errorMessage = 'Confirm Password is required.';
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.errorMessage = 'Password and Confirm Password do not match.';
      return;
    }
    if (!this.step1Terms) {
      this.errorMessage = 'Please agree to the Terms and Conditions and Privacy Policy.';
      return;
    }

    this.currentStep = 2;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  nextStep2() {
    this.errorMessage = '';

    if (!this.legalOrganizationName || !this.legalOrganizationName.trim()) {
      this.errorMessage = 'Legal Organization Name is required.';
      return;
    }
    if (!this.businessStructure) {
      this.errorMessage = 'Business Structure selection is required.';
      return;
    }
    if (!this.companyLicenseNumber || !this.companyLicenseNumber.trim()) {
      this.errorMessage = 'Hotel License Number is required.';
      return;
    }
    if (!/^\d{12}$/.test(this.companyLicenseNumber.trim())) {
      this.errorMessage = 'Hotel License Number must be exactly 12 numeric digits.';
      return;
    }
    if (!this.businessPanNumber || !this.businessPanNumber.trim()) {
      this.errorMessage = 'Business PAN Number is required.';
      return;
    }
    if (!/^[A-Z]{5}[0-9]{4}[A-Z]{1}$/i.test(this.businessPanNumber.trim())) {
      this.errorMessage = 'Business PAN must follow standard 10-character PAN format (e.g. ABCDE1234F).';
      return;
    }
    if (this.gstinNumber && this.gstinNumber.trim()) {
      if (!/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/i.test(this.gstinNumber.trim())) {
        this.errorMessage = 'GSTIN Number must follow valid 15-character GSTIN format.';
        return;
      }
    }
    if (!this.flatBuildingOffice || !this.flatBuildingOffice.trim()) {
      this.errorMessage = 'Flat / Building / Office Number is required.';
      return;
    }
    if (!this.streetRoad || !this.streetRoad.trim()) {
      this.errorMessage = 'Street / Road is required.';
      return;
    }
    if (!this.cityLocality || !this.cityLocality.trim()) {
      this.errorMessage = 'City / Locality is required.';
      return;
    }
    if (!this.state || !this.state.trim()) {
      this.errorMessage = 'State / Union Territory selection is required.';
      return;
    }
    if (!this.country || !this.country.trim()) {
      this.errorMessage = 'Country is required.';
      return;
    }
    if (!this.pinCode || !this.pinCode.trim()) {
      this.errorMessage = 'PIN / Postal Code is required.';
      return;
    }
    if (this.country === 'India' && !/^\d{6}$/.test(this.pinCode.trim())) {
      this.errorMessage = 'PIN / Postal Code must be exactly 6 digits for India (e.g. 520001).';
      return;
    }
    if (!this.businessContactEmail || !this.businessContactEmail.trim()) {
      this.errorMessage = 'Business Contact Email is required.';
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(this.businessContactEmail.trim())) {
      this.errorMessage = 'Please enter a valid Business Contact Email.';
      return;
    }
    if (!this.businessContactPhone || !this.businessContactPhone.trim()) {
      this.errorMessage = 'Business Contact Phone is required.';
      return;
    }
    const cleanBizPhone = this.businessContactPhone.trim().replace(/^(\+91\s*)/, '').replace(/\s+/g, '');
    if (!/^\d{10}$/.test(cleanBizPhone)) {
      this.errorMessage = 'Please enter a valid 10-digit Business Contact Phone.';
      return;
    }

    this.currentStep = 3;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  getDisplayDesignation(): string {
    if (this.representativeDesignation === 'OTHER') {
      return this.customDesignation ? this.customDesignation.trim() : 'Other';
    }
    const found = this.designationOptions.find(o => o.value === this.representativeDesignation);
    return found ? found.label : (this.representativeDesignation || '-');
  }

  getDisplayBusinessStructure(): string {
    const map: Record<string, string> = {
      'Proprietorship': 'Sole Proprietorship',
      'Partnership': 'Partnership Firm',
      'LLP': 'Limited Liability Partnership (LLP)',
      'Private Limited': 'Private Limited Company',
      'Public Limited': 'Public Limited Company',
      'Other': 'Other Authorized Entity'
    };
    return map[this.businessStructure] || this.businessStructure || '-';
  }

  getFormattedPhone(raw: string): string {
    if (!raw) return '-';
    const clean = raw.trim().replace(/^(\+91\s*)/, '').replace(/\s+/g, '');
    if (clean.length === 10) {
      return '+91 ' + clean.substring(0, 5) + ' ' + clean.substring(5);
    }
    return '+91 ' + clean;
  }

  getFormattedAddressLines(): string {
    const parts = [
      this.flatBuildingOffice ? this.flatBuildingOffice.trim() : '',
      this.streetRoad ? this.streetRoad.trim() : '',
      this.landmark ? this.landmark.trim() : '',
      this.cityLocality ? this.cityLocality.trim() : '',
      [this.state ? this.state.trim() : '', this.pinCode ? this.pinCode.trim() : ''].filter(Boolean).join(' - '),
      this.country ? this.country.trim() : ''
    ].filter(Boolean);
    return parts.join(', ');
  }

  getNormalizedPhone(raw: string): string {
    const clean = raw.trim().replace(/^(\+91\s*)/, '').replace(/\s+/g, '');
    return '+91' + clean;
  }

  onSubmit() {
    this.errorMessage = '';

    if (!this.termsCertified) {
      this.errorMessage = 'Please accept the certification and Terms & Conditions before submitting.';
      return;
    }

    // Full validation check before submission
    if (!this.firstName || !this.lastName || !this.email || !this.phone || !this.password || !this.representativeDesignation) {
      this.errorMessage = 'Please complete all required representative details in Step 1.';
      this.currentStep = 1;
      return;
    }
    if (!this.legalOrganizationName || !this.businessStructure || !this.companyLicenseNumber || !this.businessPanNumber || !this.flatBuildingOffice || !this.streetRoad || !this.cityLocality || !this.state || !this.country || !this.pinCode || !this.businessContactEmail || !this.businessContactPhone) {
      this.errorMessage = 'Please complete all required organization details in Step 2.';
      this.currentStep = 2;
      return;
    }

    // Compose structured address
    const addressParts = [
      this.flatBuildingOffice.trim(),
      this.streetRoad.trim(),
      this.landmark ? this.landmark.trim() : '',
      this.cityLocality.trim(),
      this.state.trim(),
      this.country.trim(),
      this.pinCode.trim()
    ].filter(part => part && part.length > 0);

    const composedAddress = addressParts.join(', ');

    this.loading = true;

    const payload = {
      firstName: this.firstName.trim(),
      lastName: this.lastName.trim(),
      email: this.email.trim(),
      phone: this.getNormalizedPhone(this.phone),
      password: this.password,
      confirmPassword: this.confirmPassword,
      representativeDesignation: this.representativeDesignation,
      customDesignation: this.representativeDesignation === 'OTHER' ? this.customDesignation.trim() : '',
      legalOrganizationName: this.legalOrganizationName.trim(),
      businessStructure: this.businessStructure.trim(),
      companyLicenseNumber: this.companyLicenseNumber.trim(),
      businessPanNumber: this.businessPanNumber.trim().toUpperCase(),
      gstinNumber: this.gstinNumber ? this.gstinNumber.trim().toUpperCase() : '',
      registeredAddress: composedAddress,
      flatBuildingOffice: this.flatBuildingOffice.trim(),
      streetRoad: this.streetRoad.trim(),
      landmark: this.landmark ? this.landmark.trim() : '',
      cityLocality: this.cityLocality.trim(),
      state: this.state.trim(),
      country: this.country.trim(),
      pinCode: this.pinCode.trim(),
      businessContactEmail: this.businessContactEmail.trim(),
      businessContactPhone: this.getNormalizedPhone(this.businessContactPhone),
      termsAccepted: true
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
