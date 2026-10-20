import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-forgot-password',
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
                <i class="fa-solid fa-key fs-3 text-primary"></i>
              </div>
              <h2 class="fw-bold text-dark">Forgot Password?</h2>
              <p class="text-muted small">Enter your registered email address and we'll send reset instructions.</p>
            </div>

            <!-- Success Confirmation State (Generic to prevent account enumeration) -->
            <div *ngIf="successMessage" class="alert alert-success p-3 rounded-3 mb-4">
              <div class="d-flex align-items-start">
                <i class="fa-solid fa-circle-check fs-5 text-success me-2 mt-1"></i>
                <div>
                  <div class="fw-bold mb-1">Check Your Email</div>
                  <div class="small leading-normal">{{ successMessage }}</div>
                  <div class="small text-muted mt-2">
                    For your security, the reset link expires in <strong>15 minutes</strong> and can be used only once.
                  </div>
                </div>
              </div>
              <div class="mt-3 pt-2 border-top border-success-subtle d-flex gap-2">
                <button type="button" class="btn btn-sm btn-outline-success" (click)="resetForm()">
                  Send to another email
                </button>
                <a routerLink="/login" class="btn btn-sm btn-success">
                  Return to Login
                </a>
              </div>
            </div>

            <!-- Error Banner -->
            <div *ngIf="errorMessage" class="alert alert-danger py-2.5 small mb-4 d-flex align-items-center">
              <i class="fa-solid fa-circle-exclamation me-2 fs-6"></i>
              <span>{{ errorMessage }}</span>
            </div>

            <!-- Request Form -->
            <form *ngIf="!successMessage" (ngSubmit)="onSubmit()">
              <div class="mb-4">
                <label class="form-label fw-semibold text-secondary small" for="forgotEmail">Email Address</label>
                <div class="input-group">
                  <span class="input-group-text bg-light text-muted border-end-0"><i class="fa-regular fa-envelope"></i></span>
                  <input
                    id="forgotEmail"
                    type="email"
                    class="form-control border-start-0"
                    [(ngModel)]="email"
                    (input)="clearError()"
                    name="email"
                    required
                    placeholder="you@example.com"
                    autocomplete="email"
                  >
                </div>
              </div>

              <button type="submit" class="btn btn-primary w-100 py-2.5 fw-bold rounded-3 shadow-sm" [disabled]="loading">
                <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
                <span>{{ loading ? 'Sending Instructions...' : 'Send Reset Instructions' }}</span>
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ForgotPasswordComponent {
  email = '';
  loading = false;
  successMessage = '';
  errorMessage = '';

  constructor(private authService: AuthService) {}

  clearError() {
    if (this.errorMessage) {
      this.errorMessage = '';
    }
  }

  resetForm() {
    this.email = '';
    this.successMessage = '';
    this.errorMessage = '';
  }

  onSubmit() {
    const trimmedEmail = this.email ? this.email.trim().toLowerCase() : '';
    if (!trimmedEmail) {
      this.errorMessage = 'Please enter your email address.';
      return;
    }

    if (!trimmedEmail.includes('@') || !trimmedEmail.includes('.')) {
      this.errorMessage = 'Please enter a valid email address.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.authService.forgotPassword(trimmedEmail).subscribe({
      next: (res) => {
        this.loading = false;
        this.successMessage = res.message || "We'll send password-reset instructions to your email address if an account exists.";
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || err.message || 'Unable to process your request. Please check your connection and try again.';
      }
    });
  }
}
