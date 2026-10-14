import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-traveller-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="te-reg-page">
      <!-- ================= LEFT SCENIC PANEL ================= -->
      <div class="te-left-panel">
        <div class="te-left-overlay-top"></div>
        <div class="te-left-overlay-bottom"></div>

        <!-- Top Branding & Heading Area -->
        <div class="te-left-top">
          <!-- Travel Easy Brand Logo -->
          <a routerLink="/home" class="te-brand">
            <span class="te-brand-icon">
              <i class="fa-solid fa-paper-plane"></i>
            </span>
            <span class="te-brand-text">Travel <span class="te-blue">Easy</span></span>
          </a>

          <!-- Hero Heading -->
          <h1 class="te-hero-title">
            Explore<br />
            More with<br />
            <span class="te-blue">Travel Easy</span>
          </h1>

          <!-- Supporting Text -->
          <p class="te-hero-sub">
            Discover amazing destinations,<br />
            book buses and hotels, and create<br />
            unforgettable journeys.
          </p>
        </div>

        <!-- Bottom Travel Slogan -->
        <div class="te-left-bottom">
          <div class="te-slogan-wrap">
            <i class="fa-solid fa-location-dot te-slogan-pin"></i>
            <span class="te-slogan-text">Explore. Book. Travel. Repeat.</span>
          </div>
          <div class="te-slogan-line"></div>
        </div>
      </div>

      <!-- ================= RIGHT REGISTRATION FORM PANEL ================= -->
      <div class="te-right-panel">
        <!-- Soft bubble-like decorative background shapes -->
        <div class="te-bubbles-layer" aria-hidden="true">
          <div class="te-bubble te-bubble-tr"></div>
          <div class="te-bubble te-bubble-br"></div>
          <div class="te-bubble te-bubble-bl"></div>
        </div>

        <div class="te-reg-card">
          <!-- Back Link -->
          <a routerLink="/register" class="te-back-link">
            <i class="fa-solid fa-chevron-left me-1"></i> Back to account selection
          </a>

          <!-- Card Title & Subtitle -->
          <h2 class="te-card-title">Traveller <span class="te-blue">Registration</span></h2>
          <p class="te-card-subtitle">Create your account to start booking.</p>

          <!-- Error Alert Banner -->
          <div *ngIf="errorMessage" class="te-alert-error">
            <i class="fa-solid fa-circle-exclamation me-2"></i>
            <span>{{ errorMessage }}</span>
          </div>

          <!-- Registration Form -->
          <form (ngSubmit)="onSubmit()">
            <!-- Row 1: First Name & Last Name -->
            <div class="te-form-row">
              <div class="te-form-group">
                <label class="te-label" for="firstName">First Name <span class="te-req">*</span></label>
                <div class="te-input-wrap">
                  <i class="fa-regular fa-user te-field-icon"></i>
                  <input
                    id="firstName"
                    type="text"
                    class="te-input"
                    [(ngModel)]="firstName"
                    name="firstName"
                    required
                    placeholder="Enter your first name"
                  />
                </div>
              </div>

              <div class="te-form-group">
                <label class="te-label" for="lastName">Last Name <span class="te-req">*</span></label>
                <div class="te-input-wrap">
                  <i class="fa-regular fa-user te-field-icon"></i>
                  <input
                    id="lastName"
                    type="text"
                    class="te-input"
                    [(ngModel)]="lastName"
                    name="lastName"
                    required
                    placeholder="Enter your last name"
                  />
                </div>
              </div>
            </div>

            <!-- Row 2: Email Address -->
            <div class="te-form-group">
              <label class="te-label" for="email">Email Address <span class="te-req">*</span></label>
              <div class="te-input-wrap">
                <i class="fa-regular fa-envelope te-field-icon"></i>
                <input
                  id="email"
                  type="email"
                  class="te-input"
                  [(ngModel)]="email"
                  name="email"
                  required
                  placeholder="you@example.com"
                />
              </div>
            </div>

            <!-- Row 3: Phone Number -->
            <div class="te-form-group">
              <label class="te-label" for="phone">Phone Number <span class="te-req">*</span></label>
              <div class="te-phone-group">
                <div class="te-country-select">
                  <span class="te-flag">🇮🇳</span>
                  <span class="te-dial-code">+91</span>
                  <i class="fa-solid fa-chevron-down te-chevron"></i>
                </div>
                <div class="te-input-wrap te-phone-input-wrap">
                  <i class="fa-solid fa-phone te-field-icon"></i>
                  <input
                    id="phone"
                    type="tel"
                    class="te-input"
                    [(ngModel)]="phone"
                    name="phone"
                    required
                    placeholder="98765 43210"
                  />
                </div>
              </div>
            </div>

            <!-- Row 4: Password & Confirm Password -->
            <div class="te-form-row">
              <div class="te-form-group">
                <label class="te-label" for="password">Password <span class="te-req">*</span></label>
                <div class="te-input-wrap">
                  <i class="fa-solid fa-lock te-field-icon"></i>
                  <input
                    id="password"
                    [type]="showPassword ? 'text' : 'password'"
                    class="te-input te-input-has-eye"
                    [(ngModel)]="password"
                    name="password"
                    required
                    placeholder="Create a strong password"
                  />
                  <button
                    type="button"
                    class="te-eye-btn"
                    (click)="showPassword = !showPassword"
                    [attr.aria-label]="showPassword ? 'Hide password' : 'Show password'"
                    tabindex="-1"
                  >
                    <i class="fa-regular" [ngClass]="showPassword ? 'fa-eye-slash' : 'fa-eye'"></i>
                  </button>
                </div>
              </div>

              <div class="te-form-group">
                <label class="te-label" for="confirmPassword">Confirm Password <span class="te-req">*</span></label>
                <div class="te-input-wrap">
                  <i class="fa-solid fa-lock te-field-icon"></i>
                  <input
                    id="confirmPassword"
                    [type]="showConfirmPassword ? 'text' : 'password'"
                    class="te-input te-input-has-eye"
                    [(ngModel)]="confirmPassword"
                    name="confirmPassword"
                    required
                    placeholder="Re-enter your password"
                  />
                  <button
                    type="button"
                    class="te-eye-btn"
                    (click)="showConfirmPassword = !showConfirmPassword"
                    [attr.aria-label]="showConfirmPassword ? 'Hide confirm password' : 'Show confirm password'"
                    tabindex="-1"
                  >
                    <i class="fa-regular" [ngClass]="showConfirmPassword ? 'fa-eye-slash' : 'fa-eye'"></i>
                  </button>
                </div>
              </div>
            </div>

            <!-- Row 5: Terms and Conditions Checkbox -->
            <div class="te-terms-group">
              <label class="te-terms-label">
                <input
                  type="checkbox"
                  class="te-checkbox"
                  [(ngModel)]="termsAccepted"
                  name="termsAccepted"
                  required
                />
                <span class="te-terms-text">
                  I agree to the
                  <a href="javascript:void(0)" class="te-link">Terms and Conditions</a>
                  and
                  <a href="javascript:void(0)" class="te-link">Privacy Policy</a>.
                </span>
              </label>
            </div>

            <!-- Row 6: Submit Button -->
            <button type="submit" class="te-submit-btn" [disabled]="loading">
              <span *ngIf="loading" class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
              <span>Create Account</span>
              <i class="fa-solid fa-arrow-right ms-2" *ngIf="!loading"></i>
            </button>
          </form>

          <!-- Row 7: Login Divider & Link -->
          <div class="te-bottom-login">
            <span class="te-bottom-line"></span>
            <div class="te-bottom-login-text">
              Already have an account?
              <a routerLink="/login" class="te-login-link">Log in</a>
            </div>
            <span class="te-bottom-line"></span>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    /* ================= PAGE CONTAINER ================= */
    .te-reg-page {
      display: flex;
      min-height: 100vh;
      width: 100%;
      background-color: #f1f6fd;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      overflow-x: hidden;
    }

    /* Common Blue Accent */
    .te-blue {
      color: #0066ff;
    }

    /* ================= LEFT VISUAL SCENIC PANEL ================= */
    .te-left-panel {
      position: relative;
      width: 48%;
      min-height: 100vh;
      background-image: url('/assets/images/traveller-card.png');
      background-size: cover;
      background-position: center bottom;
      background-repeat: no-repeat;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: 44px 52px;
      overflow: hidden;
      flex-shrink: 0;
    }

    /* Top soft highlight overlay to make text pop against sky */
    .te-left-overlay-top {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 380px;
      background: linear-gradient(
        to bottom,
        rgba(255, 255, 255, 0.45) 0%,
        rgba(255, 255, 255, 0.2) 50%,
        rgba(255, 255, 255, 0) 100%
      );
      pointer-events: none;
      z-index: 1;
    }

    /* Bottom subtle shadow overlay for slogan readability */
    .te-left-overlay-bottom {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 160px;
      background: linear-gradient(
        to top,
        rgba(15, 23, 42, 0.5) 0%,
        rgba(15, 23, 42, 0) 100%
      );
      pointer-events: none;
      z-index: 1;
    }

    .te-left-top {
      position: relative;
      z-index: 2;
    }

    /* Brand Logo */
    .te-brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      text-decoration: none;
      margin-bottom: 34px;
    }
    .te-brand-icon {
      width: 40px;
      height: 40px;
      background-color: #0066ff;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #ffffff;
      font-size: 17px;
      box-shadow: 0 4px 12px rgba(0, 102, 255, 0.3);
    }
    .te-brand-icon i {
      transform: translate(-1px, 1px);
    }
    .te-brand-text {
      font-size: 24px;
      font-weight: 800;
      color: #0f172a;
      letter-spacing: -0.3px;
    }

    /* Hero Heading */
    .te-hero-title {
      font-size: 46px;
      font-weight: 800;
      color: #0f172a;
      line-height: 1.15;
      letter-spacing: -1px;
      margin-bottom: 16px;
    }

    /* Hero Subtitle */
    .te-hero-sub {
      font-size: 16px;
      line-height: 1.55;
      color: #334155;
      font-weight: 500;
      max-width: 440px;
      margin: 0;
    }

    /* Bottom Travel Slogan */
    .te-left-bottom {
      position: relative;
      z-index: 2;
    }
    .te-slogan-wrap {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #ffffff;
      font-size: 15px;
      font-weight: 600;
      text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
    }
    .te-slogan-pin {
      color: #ffffff;
      font-size: 15px;
    }
    .te-slogan-line {
      width: 48px;
      height: 3px;
      background-color: #ffffff;
      border-radius: 2px;
      margin-top: 8px;
      opacity: 0.9;
    }

    /* ================= RIGHT REGISTRATION FORM PANEL ================= */
    .te-right-panel {
      flex: 1;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 36px 36px;
      background-color: #f1f6fd;
      position: relative;
      z-index: 2;
    }

    /* Soft bubble-like decorative background layer */
    .te-bubbles-layer {
      position: absolute;
      inset: 0;
      overflow: hidden;
      pointer-events: none;
      z-index: 1;
    }

    .te-bubble {
      position: absolute;
      border-radius: 50%;
      pointer-events: none;
    }

    /* Oversized pale-blue circular shape near upper-right */
    .te-bubble-tr {
      top: -120px;
      right: -100px;
      width: 520px;
      height: 520px;
      background: radial-gradient(
        circle,
        rgba(219, 234, 254, 0.85) 0%,
        rgba(224, 242, 254, 0.5) 45%,
        rgba(241, 246, 253, 0) 72%
      );
    }

    /* Subtle large circular shape near lower-right */
    .te-bubble-br {
      bottom: -140px;
      right: -80px;
      width: 560px;
      height: 560px;
      background: radial-gradient(
        circle,
        rgba(219, 234, 254, 0.8) 0%,
        rgba(224, 242, 254, 0.45) 50%,
        rgba(241, 246, 253, 0) 75%
      );
    }

    /* Faint transition bubble near bottom-left */
    .te-bubble-bl {
      bottom: -40px;
      left: -60px;
      width: 340px;
      height: 340px;
      background: radial-gradient(
        circle,
        rgba(224, 242, 254, 0.55) 0%,
        rgba(241, 246, 253, 0) 70%
      );
    }

    /* Large White Card with controlled overlap over left scenic image */
    .te-reg-card {
      position: relative;
      z-index: 10;
      width: 100%;
      max-width: 630px;
      background: #ffffff;
      border-radius: 24px;
      padding: 36px 42px 32px;
      box-shadow: 0 18px 48px rgba(15, 23, 42, 0.1), 0 4px 14px rgba(15, 23, 42, 0.04);
      border: 1px solid rgba(226, 232, 240, 0.85);
      margin-left: -52px;
    }

    /* Back Link */
    .te-back-link {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 14px;
      font-weight: 600;
      color: #0066ff;
      text-decoration: none;
      margin-bottom: 20px;
      cursor: pointer;
      transition: color 0.2s ease;
    }
    .te-back-link:hover {
      color: #0047b3;
    }

    /* Card Title & Subtitle */
    .te-card-title {
      font-size: 32px;
      font-weight: 800;
      color: #0f172a;
      letter-spacing: -0.6px;
      margin: 0 0 6px 0;
    }
    .te-card-subtitle {
      font-size: 14.5px;
      color: #64748b;
      margin: 0 0 24px 0;
    }

    /* Alert Banner */
    .te-alert-error {
      background-color: #fef2f2;
      border: 1px solid #fecaca;
      color: #b91c1c;
      border-radius: 12px;
      padding: 10px 14px;
      font-size: 13.5px;
      margin-bottom: 20px;
      display: flex;
      align-items: center;
    }

    /* Form Layout */
    .te-form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }

    .te-form-group {
      display: flex;
      flex-direction: column;
      margin-bottom: 16px;
    }

    .te-label {
      font-size: 13.5px;
      font-weight: 600;
      color: #1e293b;
      margin-bottom: 6px;
    }

    .te-req {
      color: #ef4444;
      margin-left: 2px;
      font-weight: 700;
    }

    .te-input-wrap {
      position: relative;
      width: 100%;
    }

    .te-field-icon {
      position: absolute;
      left: 14px;
      top: 50%;
      transform: translateY(-50%);
      color: #64748b;
      font-size: 15px;
      pointer-events: none;
      z-index: 1;
    }

    .te-input {
      width: 100%;
      height: 46px;
      background-color: #ffffff;
      border: 1.5px solid #e2e8f0;
      border-radius: 12px;
      padding: 0 14px 0 42px;
      font-size: 14px;
      color: #0f172a;
      transition: all 0.2s ease;
      outline: none;
    }
    .te-input:focus {
      border-color: #0066ff;
      box-shadow: 0 0 0 3px rgba(0, 102, 255, 0.12);
    }
    .te-input::placeholder {
      color: #94a3b8;
    }

    .te-input-has-eye {
      padding-right: 44px;
    }

    .te-eye-btn {
      position: absolute;
      right: 12px;
      top: 50%;
      transform: translateY(-50%);
      background: none;
      border: none;
      color: #64748b;
      cursor: pointer;
      padding: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 15px;
      transition: color 0.2s;
    }
    .te-eye-btn:hover {
      color: #0f172a;
    }

    /* Phone Country Code + Number Group */
    .te-phone-group {
      display: flex;
      gap: 12px;
      width: 100%;
    }
    .te-country-select {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      height: 46px;
      padding: 0 12px;
      border: 1.5px solid #e2e8f0;
      border-radius: 12px;
      background: #ffffff;
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
      flex-shrink: 0;
      user-select: none;
    }
    .te-flag {
      font-size: 16px;
    }
    .te-dial-code {
      font-size: 13.5px;
      font-weight: 600;
      color: #1e293b;
    }
    .te-chevron {
      font-size: 11px;
      color: #64748b;
      margin-left: 2px;
    }
    .te-phone-input-wrap {
      flex-grow: 1;
    }

    /* Terms Checkbox */
    .te-terms-group {
      margin: 6px 0 20px 0;
    }
    .te-terms-label {
      display: flex;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      user-select: none;
    }
    .te-checkbox {
      width: 17px;
      height: 17px;
      accent-color: #0066ff;
      border-radius: 4px;
      cursor: pointer;
      flex-shrink: 0;
    }
    .te-terms-text {
      font-size: 13px;
      color: #475569;
    }
    .te-link {
      color: #0066ff;
      font-weight: 600;
      text-decoration: none;
    }
    .te-link:hover {
      text-decoration: underline;
    }

    /* Submit Button */
    .te-submit-btn {
      width: 100%;
      height: 48px;
      background-color: #0066ff;
      color: #ffffff;
      border: none;
      border-radius: 12px;
      font-size: 15px;
      font-weight: 600;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 14px rgba(0, 102, 255, 0.25);
      cursor: pointer;
      transition: all 0.2s ease-in-out;
    }
    .te-submit-btn:hover:not(:disabled) {
      background-color: #0052cc;
      box-shadow: 0 6px 18px rgba(0, 102, 255, 0.35);
      transform: translateY(-1px);
    }
    .te-submit-btn:disabled {
      opacity: 0.65;
      cursor: not-allowed;
    }

    /* Bottom Login Link with Dividers */
    .te-bottom-login {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      margin-top: 24px;
      font-size: 13.5px;
      color: #64748b;
    }
    .te-bottom-line {
      flex: 1;
      height: 1px;
      background-color: #e2e8f0;
    }
    .te-bottom-login-text {
      white-space: nowrap;
    }
    .te-login-link {
      color: #0066ff;
      font-weight: 600;
      text-decoration: underline;
      text-underline-offset: 3px;
      margin-left: 4px;
      transition: color 0.2s ease;
    }
    .te-login-link:hover {
      color: #0047b3;
    }

    /* ================= RESPONSIVE ADAPTATIONS ================= */
    @media (max-width: 1080px) {
      .te-left-panel {
        padding: 36px 36px;
      }
      .te-hero-title {
        font-size: 38px;
      }
      .te-reg-card {
        padding: 32px 30px;
      }
    }

    @media (max-width: 900px) {
      .te-reg-page {
        flex-direction: column;
      }
      .te-left-panel {
        width: 100%;
        min-height: 300px;
        padding: 32px 24px;
      }
      .te-right-panel {
        width: 100%;
        padding: 24px 16px;
      }
      .te-reg-card {
        max-width: 520px;
        padding: 28px 20px;
        margin-left: 0;
      }
      .te-hero-title {
        font-size: 32px;
      }
      .te-form-row {
        grid-template-columns: 1fr;
        gap: 0;
      }
    }

    @media (max-width: 480px) {
      .te-reg-card {
        padding: 24px 16px;
        border-radius: 18px;
      }
      .te-card-title {
        font-size: 26px;
      }
      .te-phone-group {
        flex-direction: column;
        gap: 8px;
      }
      .te-country-select {
        width: 100%;
        justify-content: flex-start;
      }
    }
  `]
})
export class TravellerRegisterComponent {
  firstName = '';
  lastName = '';
  email = '';
  phone = '';
  password = '';
  confirmPassword = '';
  termsAccepted = false;
  showPassword = false;
  showConfirmPassword = false;
  loading = false;
  errorMessage = '';

  constructor(private authService: AuthService, private router: Router) {}

  onSubmit() {
    this.errorMessage = '';

    const nameRegex = /^[\p{L} '\-]+$/u;
    if (!this.firstName || !nameRegex.test(this.firstName) || this.firstName.length > 30) {
      this.errorMessage = 'Please enter a valid First Name (1-30 characters, letters only).';
      return;
    }
    if (!this.lastName || !nameRegex.test(this.lastName) || this.lastName.length > 30) {
      this.errorMessage = 'Please enter a valid Last Name (1-30 characters, letters only).';
      return;
    }
    if (!this.email || !this.email.includes('@')) {
      this.errorMessage = 'Please enter a valid email address.';
      return;
    }
    if (!this.phone) {
      this.errorMessage = 'Please enter a valid phone number.';
      return;
    }
    if (this.password.length < 8) {
      this.errorMessage = 'Password must be at least 8 characters long.';
      return;
    }
    const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#^()_+\-=\[\]{};':"\\|,.<>\/?]).{8,}$/;
    if (!passwordRegex.test(this.password)) {
      this.errorMessage = 'Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character.';
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.errorMessage = 'Password and Confirm Password do not match.';
      return;
    }
    if (!this.termsAccepted) {
      this.errorMessage = 'You must agree to the Terms and Conditions and Privacy Policy.';
      return;
    }

    this.loading = true;

    // Normalizing phone number: strip spaces/dashes, ensure +91 prefix
    const cleanedDigits = this.phone.replace(/[\s\-()]/g, '');
    const formattedPhone = cleanedDigits.startsWith('+') ? cleanedDigits : `+91${cleanedDigits}`;

    const payload = {
      firstName: this.firstName.trim(),
      lastName: this.lastName.trim(),
      email: this.email.trim(),
      phone: formattedPhone,
      password: this.password,
      confirmPassword: this.confirmPassword,
      termsAccepted: this.termsAccepted
    };

    this.authService.registerTraveller(payload).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigate(['/customer']);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || err.message || 'Registration failed. Please check your details.';
      }
    });
  }
}
