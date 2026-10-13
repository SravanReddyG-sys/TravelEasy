import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="te-login-page">
      <div class="te-login-overlay"></div>

      <div class="te-login-container">
        <!-- ================= LEFT: BRANDING & HIGHLIGHTS ================= -->
        <div class="te-login-left">
          <!-- Top Logo -->
          <a class="te-brand" routerLink="/home">
            <span class="te-brand-icon">
              <i class="fa-solid fa-paper-plane"></i>
            </span>
            <span class="te-brand-text">Travel <span class="te-brand-blue">Easy</span></span>
          </a>

          <!-- Main Welcome Heading -->
          <div class="te-welcome-block">
            <div class="te-welcome-tag">WELCOME BACK TO</div>
            <h1 class="te-welcome-title">
              Travel <span class="te-welcome-title-blue">Easy</span>
            </h1>
            <p class="te-welcome-sub">Continue your journey to amazing places</p>
          </div>

          <!-- Feature Highlights -->
          <div class="te-feature-list">
            <div class="te-feature-item">
              <div class="te-feature-icon">
                <i class="fa-solid fa-bus"></i>
              </div>
              <div class="te-feature-text">
                <div class="te-feature-title">Book Buses</div>
                <div class="te-feature-desc">Comfortable and reliable travel across India</div>
              </div>
            </div>

            <div class="te-feature-item">
              <div class="te-feature-icon">
                <i class="fa-solid fa-hotel"></i>
              </div>
              <div class="te-feature-text">
                <div class="te-feature-title">Book Hotels</div>
                <div class="te-feature-desc">Stay at verified hotels and resorts</div>
              </div>
            </div>

            <div class="te-feature-item">
              <div class="te-feature-icon">
                <i class="fa-solid fa-shield-halved"></i>
              </div>
              <div class="te-feature-text">
                <div class="te-feature-title">Safe &amp; Secure</div>
                <div class="te-feature-desc">Your journey, our priority</div>
              </div>
            </div>
          </div>

          <!-- Travel Slogan (Lower-Left) -->
          <div class="te-slogan-wrap">
            <div class="te-slogan">
              <em>New journeys<br>brighter stories</em>
            </div>
          </div>
        </div>

        <!-- ================= RIGHT: LOGIN CARD ================= -->
        <div class="te-login-right">
          <div class="te-login-card">
            <!-- Card Brand Header -->
            <div class="te-card-brand">
              <span class="te-card-brand-icon">
                <i class="fa-solid fa-paper-plane"></i>
              </span>
              <span class="te-card-brand-text">Travel <span class="te-brand-blue">Easy</span></span>
            </div>

            <h2 class="te-card-title">Welcome Back</h2>
            <p class="te-card-subtitle">Login to your account and continue your journey</p>

            <!-- Error Banner -->
            <div *ngIf="errorMessage" class="te-alert-error">
              <i class="fa-solid fa-circle-exclamation me-2"></i>
              <span>{{ errorMessage }}</span>
            </div>

            <!-- Form -->
            <form (ngSubmit)="onSubmit()">
              <!-- Email Field -->
              <div class="te-form-group">
                <label class="te-label" for="loginEmail">Email Address</label>
                <div class="te-input-wrap">
                  <i class="fa-regular fa-envelope te-input-icon"></i>
                  <input
                    id="loginEmail"
                    type="email"
                    class="te-input"
                    [(ngModel)]="email"
                    name="email"
                    required
                    placeholder="Enter your email address"
                    autocomplete="email"
                  >
                </div>
              </div>

              <!-- Password Field -->
              <div class="te-form-group">
                <label class="te-label" for="loginPassword">Password</label>
                <div class="te-input-wrap">
                  <i class="fa-solid fa-lock te-input-icon"></i>
                  <input
                    id="loginPassword"
                    [type]="showPassword ? 'text' : 'password'"
                    class="te-input te-input--password"
                    [(ngModel)]="password"
                    name="password"
                    required
                    placeholder="Enter your password"
                    autocomplete="current-password"
                  >
                  <button
                    type="button"
                    class="te-eye-btn"
                    (click)="showPassword = !showPassword"
                    [title]="showPassword ? 'Hide password' : 'Show password'"
                    tabindex="-1"
                  >
                    <i class="fa-regular" [ngClass]="showPassword ? 'fa-eye-slash' : 'fa-eye'"></i>
                  </button>
                </div>
              </div>

              <!-- Remember Me & Forgot Password -->
              <div class="te-options-row">
                <label class="te-checkbox-label">
                  <input
                    type="checkbox"
                    class="te-checkbox"
                    [(ngModel)]="rememberMe"
                    name="rememberMe"
                  >
                  <span>Remember me</span>
                </label>
                <a routerLink="/forgot-password" class="te-forgot-link">Forgot password?</a>
              </div>

              <!-- Login Button -->
              <button
                type="submit"
                class="te-login-btn"
                [disabled]="loading"
              >
                <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
                <span>Login</span>
                <i class="fa-solid fa-arrow-right ms-2" *ngIf="!loading"></i>
              </button>
            </form>

            <!-- Bottom Create Account Prompt -->
            <div class="te-register-prompt">
              <span class="te-register-prompt-text">Don't have an account?</span>
              <a routerLink="/register" class="te-create-btn">
                Create New Account
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    /* ===================================================
       TRAVEL EASY LOGIN PAGE STYLES
    =================================================== */
    :host {
      display: block;
      font-family: 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
      color: #0f172a;
    }

    .te-login-page {
      min-height: 100vh;
      background-image: url('/assets/images/login-background.png');
      background-size: cover;
      background-position: center;
      background-repeat: no-repeat;
      display: flex;
      align-items: center;
      position: relative;
      padding: 36px 0;
      overflow-x: hidden;
    }

    .te-login-overlay {
      position: absolute;
      inset: 0;
      background: radial-gradient(
        ellipse 62% 62% at 26% 45%,
        rgba(255, 255, 255, 0.45) 0%,
        rgba(255, 255, 255, 0.15) 60%,
        rgba(255, 255, 255, 0) 100%
      );
      pointer-events: none;
    }

    .te-login-container {
      width: 100%;
      max-width: 1220px;
      margin: 0 auto;
      padding: 0 36px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      position: relative;
      z-index: 2;
      gap: 36px;
    }

    /* ============ LEFT SECTION ============ */
    .te-login-left {
      flex: 1;
      max-width: 580px;
      display: flex;
      flex-direction: column;
    }

    .te-brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      text-decoration: none;
      margin-bottom: 32px;
    }
    .te-brand-icon {
      width: 38px;
      height: 38px;
      background: #0d5bd9;
      border-radius: 50%;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-size: 16px;
      box-shadow: 0 2px 8px rgba(13, 91, 217, 0.25);
    }
    .te-brand-text {
      font-size: 22px;
      font-weight: 700;
      color: #0f172a;
    }
    .te-brand-blue {
      color: #0d5bd9;
    }

    .te-welcome-block {
      margin-bottom: 30px;
    }
    .te-welcome-tag {
      font-size: 13px;
      font-weight: 700;
      letter-spacing: 1.5px;
      color: #0d5bd9;
      text-transform: uppercase;
      margin-bottom: 8px;
    }
    .te-welcome-title {
      font-family: Georgia, 'Times New Roman', Times, serif;
      font-size: clamp(44px, 5vw, 62px);
      font-weight: 700;
      line-height: 1.08;
      color: #0b192c;
      margin: 0 0 10px;
    }
    .te-welcome-title-blue {
      color: #0d5bd9;
    }
    .te-welcome-sub {
      font-size: 16.5px;
      color: #0f172a;
      font-weight: 500;
      margin: 0;
    }

    /* Features List */
    .te-feature-list {
      display: flex;
      flex-direction: column;
      gap: 20px;
      margin-bottom: 36px;
    }
    .te-feature-item {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    .te-feature-icon {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      background: #e0edff;
      color: #0d5bd9;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 19px;
      flex-shrink: 0;
      box-shadow: 0 2px 6px rgba(13, 91, 217, 0.12);
    }
    .te-feature-text {
      display: flex;
      flex-direction: column;
    }
    .te-feature-title {
      font-size: 15.5px;
      font-weight: 700;
      color: #0f172a;
      line-height: 1.25;
      margin-bottom: 2px;
    }
    .te-feature-desc {
      font-size: 13.5px;
      color: #334155;
      font-weight: 400;
    }

    /* Travel Slogan */
    .te-slogan-wrap {
      margin-top: 10px;
    }
    .te-slogan {
      position: relative;
      font-size: clamp(21px, 2.5vw, 28px);
      font-weight: 600;
      color: #ffffff;
      text-shadow: 0 2px 10px rgba(0, 0, 0, 0.7);
      font-style: italic;
      line-height: 1.22;
      transform: rotate(-4deg);
      display: inline-block;
      padding-bottom: 6px;
    }
    .te-slogan::after {
      content: '';
      display: block;
      width: 74%;
      height: 3px;
      margin: 4px 0 0 6px;
      background: #facc15;
      border-radius: 4px;
    }

    /* ============ RIGHT SECTION: LOGIN CARD ============ */
    .te-login-right {
      flex: 1;
      max-width: 450px;
      display: flex;
      justify-content: flex-end;
    }

    .te-login-card {
      width: 100%;
      background: #ffffff;
      border-radius: 22px;
      box-shadow: 0 20px 50px rgba(0, 0, 0, 0.18);
      padding: 38px 34px 34px;
      position: relative;
    }

    /* Card Logo Header */
    .te-card-brand {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 10px;
      margin-bottom: 16px;
    }
    .te-card-brand-icon {
      width: 38px;
      height: 38px;
      background: #0d5bd9;
      border-radius: 50%;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-size: 16px;
    }
    .te-card-brand-text {
      font-size: 21px;
      font-weight: 700;
      color: #0f172a;
    }

    .te-card-title {
      font-size: 26px;
      font-weight: 800;
      color: #0b192c;
      text-align: center;
      margin: 0 0 6px;
    }
    .te-card-subtitle {
      font-size: 13.5px;
      color: #64748b;
      text-align: center;
      margin: 0 0 22px;
    }

    .te-alert-error {
      background: #fef2f2;
      border: 1px solid #fecaca;
      color: #b91c1c;
      font-size: 13.5px;
      padding: 10px 14px;
      border-radius: 8px;
      margin-bottom: 18px;
      display: flex;
      align-items: center;
    }

    .te-form-group {
      margin-bottom: 18px;
    }
    .te-label {
      font-size: 13px;
      font-weight: 700;
      color: #0f172a;
      display: block;
      margin-bottom: 6px;
    }

    .te-input-wrap {
      position: relative;
    }
    .te-input-icon {
      position: absolute;
      left: 14px;
      top: 50%;
      transform: translateY(-50%);
      color: #64748b;
      font-size: 14.5px;
      pointer-events: none;
    }
    .te-input {
      width: 100%;
      padding: 12px 14px 12px 42px;
      border: 1.5px solid #dbe3ee;
      border-radius: 10px;
      font-size: 14px;
      color: #0f172a;
      background: #ffffff;
      transition: border-color 0.2s, box-shadow 0.2s;
      outline: none;
    }
    .te-input:focus {
      border-color: #0d5bd9;
      box-shadow: 0 0 0 3px rgba(13, 91, 217, 0.12);
    }
    .te-input::placeholder {
      color: #94a3b8;
    }
    .te-input--password {
      padding-right: 44px;
    }

    .te-eye-btn {
      position: absolute;
      right: 10px;
      top: 50%;
      transform: translateY(-50%);
      background: none;
      border: none;
      color: #64748b;
      cursor: pointer;
      padding: 6px;
      font-size: 15px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: color 0.2s;
    }
    .te-eye-btn:hover {
      color: #0d5bd9;
    }

    /* Options Row */
    .te-options-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin: 4px 0 22px;
    }
    .te-checkbox-label {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13.5px;
      color: #334155;
      font-weight: 500;
      cursor: pointer;
      margin: 0;
      user-select: none;
    }
    .te-checkbox {
      width: 16px;
      height: 16px;
      accent-color: #0d5bd9;
      cursor: pointer;
    }
    .te-forgot-link {
      font-size: 13.5px;
      font-weight: 600;
      color: #0d5bd9;
      text-decoration: none;
      transition: color 0.2s;
    }
    .te-forgot-link:hover {
      color: #0a4bb5;
      text-decoration: underline;
    }

    /* Action Buttons */
    .te-login-btn {
      width: 100%;
      padding: 12px 20px;
      background: #0d5bd9;
      color: #ffffff;
      border: none;
      border-radius: 10px;
      font-size: 15px;
      font-weight: 700;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.2s, transform 0.1s;
      box-shadow: 0 4px 14px rgba(13, 91, 217, 0.25);
    }
    .te-login-btn:hover:not(:disabled) {
      background: #0a4bb5;
    }
    .te-login-btn:active:not(:disabled) {
      transform: translateY(1px);
    }
    .te-login-btn:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }

    .te-register-prompt {
      margin-top: 24px;
      text-align: center;
    }
    .te-register-prompt-text {
      font-size: 13.5px;
      color: #64748b;
      margin-bottom: 12px;
      display: block;
    }
    .te-create-btn {
      width: 100%;
      padding: 11px 20px;
      background: transparent;
      color: #0d5bd9;
      border: 1.5px solid #0d5bd9;
      border-radius: 10px;
      font-size: 14.5px;
      font-weight: 700;
      cursor: pointer;
      text-align: center;
      text-decoration: none;
      display: block;
      transition: all 0.2s;
    }
    .te-create-btn:hover {
      background: #f0f7ff;
      color: #0a4bb5;
      border-color: #0a4bb5;
    }

    /* ============ RESPONSIVE ============ */
    @media (max-width: 992px) {
      .te-login-container {
        flex-direction: column;
        align-items: center;
        padding: 24px 20px;
        gap: 32px;
      }
      .te-login-left {
        max-width: 500px;
        align-items: center;
        text-align: center;
      }
      .te-welcome-block {
        text-align: center;
      }
      .te-feature-list {
        align-items: flex-start;
      }
      .te-login-right {
        justify-content: center;
        width: 100%;
        max-width: 440px;
      }
    }

    @media (max-width: 576px) {
      .te-login-page {
        padding: 20px 0;
      }
      .te-login-card {
        padding: 28px 20px;
        border-radius: 18px;
      }
      .te-welcome-title {
        font-size: 38px;
      }
      .te-feature-item {
        gap: 12px;
      }
      .te-feature-icon {
        width: 42px;
        height: 42px;
        font-size: 17px;
      }
    }
  `]
})
export class LoginComponent {
  email = '';
  password = '';
  showPassword = false;
  rememberMe = true;
  loading = false;
  errorMessage = '';

  constructor(private authService: AuthService, private router: Router) {}

  onSubmit() {
    if (!this.email || !this.password) {
      this.errorMessage = 'Please enter email address and password.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    this.authService.login({ email: this.email, password: this.password }).subscribe({
      next: (user) => {
        this.loading = false;

        if (user.status === 'PENDING' || user.verificationStatus === 'UNDER_REVIEW') {
          this.router.navigate(['/account-pending']);
          return;
        }

        switch (user.role) {
          case 'ROLE_CUSTOMER': 
            this.router.navigate(['/customer']); 
            break;
          case 'ROLE_BUS_OPERATOR': 
            this.router.navigate(['/bus-operator']); 
            break;
          case 'ROLE_HOTEL_MANAGER': 
            this.router.navigate(['/hotel-manager']); 
            break;
          case 'ROLE_ADMIN': 
            this.router.navigate(['/admin']); 
            break;
          default: 
            this.router.navigate(['/']);
        }
      },
      error: (err) => {
        this.loading = false;
        const errObj = err.error;
        if (errObj && errObj.status === 'SUSPENDED') {
          this.router.navigate(['/account-suspended']);
          return;
        }
        if (errObj && errObj.status === 'REJECTED') {
          this.router.navigate(['/account-rejected']);
          return;
        }
        this.errorMessage = errObj?.message || err.message || 'Invalid email or password. Please try again.';
      }
    });
  }
}
