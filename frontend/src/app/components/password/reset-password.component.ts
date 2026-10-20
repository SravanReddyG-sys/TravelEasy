import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="container py-5" style="padding-top: 96px !important;">
      <div class="row justify-content-center">
        <div class="col-md-6 col-lg-5">
          <div class="card p-4 p-md-5 shadow-lg border-0 rounded-4">
            <a routerLink="/login" class="text-decoration-none text-muted small fw-semibold d-inline-flex align-items-center mb-4">
              <i class="fa-solid fa-arrow-left me-2"></i> Back to login
            </a>

            <div class="text-center mb-4">
              <div class="d-inline-flex align-items-center justify-content-center bg-primary-subtle text-primary rounded-circle mb-3" style="width: 60px; height: 60px;">
                <i class="fa-solid fa-lock fs-3 text-primary"></i>
              </div>
              <h2 class="fw-bold text-dark">Set New Password</h2>
              <p class="text-muted small">Choose a strong new password to protect your Travel Easy account.</p>
            </div>

            <!-- Success State -->
            <div *ngIf="successMessage" class="alert alert-success p-3 rounded-3 mb-4">
              <div class="d-flex align-items-start">
                <i class="fa-solid fa-circle-check fs-5 text-success me-2 mt-1"></i>
                <div>
                  <div class="fw-bold mb-1">Password Reset Successfully</div>
                  <div class="small leading-normal">{{ successMessage }}</div>
                </div>
              </div>
              <div class="mt-3 pt-2 border-top border-success-subtle">
                <a routerLink="/login" class="btn btn-sm btn-success w-100 fw-semibold py-2">
                  Proceed to Login
                </a>
              </div>
            </div>

            <!-- Expired / Invalid Token Banner with Action -->
            <div *ngIf="isTokenExpiredOrInvalid && !successMessage" class="alert alert-warning p-3 rounded-3 mb-4">
              <div class="d-flex align-items-start">
                <i class="fa-solid fa-triangle-exclamation fs-5 text-warning me-2 mt-1"></i>
                <div>
                  <div class="fw-bold text-dark mb-1">Link Invalid or Expired</div>
                  <div class="small text-secondary mb-2">
                    For your security, password reset links expire in 15 minutes and can be used only once.
                  </div>
                  <a routerLink="/forgot-password" class="btn btn-sm btn-outline-dark fw-semibold">
                    Request a New Reset Link
                  </a>
                </div>
              </div>
            </div>

            <!-- General Error Banner -->
            <div *ngIf="errorMessage && !isTokenExpiredOrInvalid && !successMessage" class="alert alert-danger py-2.5 small mb-4 d-flex align-items-center">
              <i class="fa-solid fa-circle-exclamation me-2 fs-6"></i>
              <span>{{ errorMessage }}</span>
            </div>

            <!-- Form -->
            <form *ngIf="!successMessage" (ngSubmit)="onSubmit()">
              <!-- Token input (Only displayed if no token was found in the URL) -->
              <div *ngIf="!hasUrlToken" class="mb-3">
                <label class="form-label fw-semibold text-secondary small" for="resetToken">Reset Token <span class="text-danger">*</span></label>
                <div class="input-group">
                  <span class="input-group-text bg-light text-muted border-end-0"><i class="fa-solid fa-key"></i></span>
                  <input
                    id="resetToken"
                    type="text"
                    class="form-control border-start-0"
                    [(ngModel)]="token"
                    (input)="clearError()"
                    name="token"
                    required
                    placeholder="Paste reset token from email"
                  >
                </div>
                <div class="form-text small text-muted">
                  Or click the link directly from your reset email.
                </div>
              </div>

              <!-- New Password Field -->
              <div class="mb-3">
                <label class="form-label fw-semibold text-secondary small" for="newPassword">New Password <span class="text-danger">*</span></label>
                <div class="input-group">
                  <span class="input-group-text bg-light text-muted border-end-0"><i class="fa-solid fa-lock"></i></span>
                  <input
                    id="newPassword"
                    [type]="showNewPassword ? 'text' : 'password'"
                    class="form-control border-start-0 border-end-0"
                    [(ngModel)]="newPassword"
                    (input)="clearError()"
                    name="newPassword"
                    required
                    placeholder="Enter your new password"
                    autocomplete="new-password"
                  >
                  <button
                    type="button"
                    class="input-group-text bg-light text-muted border-start-0"
                    (click)="showNewPassword = !showNewPassword"
                    [title]="showNewPassword ? 'Hide password' : 'Show password'"
                    tabindex="-1"
                  >
                    <i class="fa-regular" [ngClass]="showNewPassword ? 'fa-eye-slash' : 'fa-eye'"></i>
                  </button>
                </div>
              </div>

              <!-- Confirm New Password Field -->
              <div class="mb-4">
                <label class="form-label fw-semibold text-secondary small" for="confirmNewPassword">Confirm New Password <span class="text-danger">*</span></label>
                <div class="input-group">
                  <span class="input-group-text bg-light text-muted border-end-0"><i class="fa-solid fa-lock"></i></span>
                  <input
                    id="confirmNewPassword"
                    [type]="showConfirmPassword ? 'text' : 'password'"
                    class="form-control border-start-0 border-end-0"
                    [(ngModel)]="confirmNewPassword"
                    (input)="clearError()"
                    name="confirmNewPassword"
                    required
                    placeholder="Re-enter your new password"
                    autocomplete="new-password"
                  >
                  <button
                    type="button"
                    class="input-group-text bg-light text-muted border-start-0"
                    (click)="showConfirmPassword = !showConfirmPassword"
                    [title]="showConfirmPassword ? 'Hide password' : 'Show password'"
                    tabindex="-1"
                  >
                    <i class="fa-regular" [ngClass]="showConfirmPassword ? 'fa-eye-slash' : 'fa-eye'"></i>
                  </button>
                </div>
                <div class="form-text small text-muted mt-2" style="line-height: 1.4; color: #eca33d;">
                  Must be at least 8 characters with 1 uppercase, 1 lowercase, 1 number, and 1 special symbol.
                </div>
              </div>

              <button type="submit" class="btn btn-primary w-100 py-2.5 fw-bold rounded-3 shadow-sm" [disabled]="loading">
                <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
                <span>{{ loading ? 'Updating Password...' : 'Reset Password' }}</span>
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ResetPasswordComponent implements OnInit {
  token = '';
  hasUrlToken = false;
  newPassword = '';
  confirmNewPassword = '';
  showNewPassword = false;
  showConfirmPassword = false;
  loading = false;
  successMessage = '';
  errorMessage = '';
  isTokenExpiredOrInvalid = false;

  constructor(private route: ActivatedRoute, private authService: AuthService, private router: Router) {}

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      if (params['token']) {
        this.token = params['token'].trim();
        this.hasUrlToken = true;
      }
    });
  }

  clearError() {
    if (this.errorMessage) {
      this.errorMessage = '';
    }
    this.isTokenExpiredOrInvalid = false;
  }

  onSubmit() {
    const trimmedToken = this.token ? this.token.trim() : '';
    if (!trimmedToken) {
      this.errorMessage = 'Password reset token is missing. Please use the link sent to your email.';
      return;
    }

    if (!this.newPassword || !this.confirmNewPassword) {
      this.errorMessage = 'Please enter and confirm your new password.';
      return;
    }

    if (this.newPassword !== this.confirmNewPassword) {
      this.errorMessage = 'Passwords do not match. Please verify both fields.';
      return;
    }

    if (this.newPassword.length < 8) {
      this.errorMessage = 'Password must be at least 8 characters long.';
      return;
    }

    const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#^()_+\-=\[\]{};':"\\|,.<>\/?]).{8,}$/;
    if (!passwordRegex.test(this.newPassword)) {
      this.errorMessage = 'Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.isTokenExpiredOrInvalid = false;

    const payload = {
      token: trimmedToken,
      newPassword: this.newPassword,
      confirmNewPassword: this.confirmNewPassword
    };

    this.authService.resetPassword(payload).subscribe({
      next: (res) => {
        this.loading = false;
        this.successMessage = res.message || 'Password has been reset successfully. You can now login with your new password.';
      },
      error: (err) => {
        this.loading = false;
        const msg = err.error?.message || err.message || '';
        if (msg.toLowerCase().includes('invalid or expired') || msg.toLowerCase().includes('token')) {
          this.isTokenExpiredOrInvalid = true;
          this.errorMessage = 'This password reset link is invalid or has expired. Please request a new one.';
        } else {
          this.errorMessage = msg || 'Reset failed. Please check your connection and try again.';
        }
      }
    });
  }
}
