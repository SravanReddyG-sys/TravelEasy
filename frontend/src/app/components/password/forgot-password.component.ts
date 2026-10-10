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
    <div class="container py-5">
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

            <div *ngIf="successMessage" class="alert alert-success py-2 small mb-4">
              <i class="fa-solid fa-circle-check me-1"></i> {{ successMessage }}
              <div *ngIf="resetToken" class="mt-2 p-2 bg-light rounded text-break font-monospace small">
                <strong>Reset Token:</strong> {{ resetToken }}<br>
                <a [routerLink]="['/reset-password']" [queryParams]="{token: resetToken}" class="btn btn-sm btn-primary mt-2">Proceed to Reset Password</a>
              </div>
            </div>

            <div *ngIf="errorMessage" class="alert alert-danger py-2 small mb-4">
              <i class="fa-solid fa-circle-exclamation me-1"></i> {{ errorMessage }}
            </div>

            <form *ngIf="!successMessage" (ngSubmit)="onSubmit()">
              <div class="mb-4">
                <label class="form-label fw-semibold text-secondary small">Email Address</label>
                <div class="input-group">
                  <span class="input-group-text bg-light text-muted border-end-0"><i class="fa-regular fa-envelope"></i></span>
                  <input type="email" class="form-control border-start-0" [(ngModel)]="email" name="email" required placeholder="you@example.com">
                </div>
              </div>

              <button type="submit" class="btn btn-primary w-100 py-2.5 fw-bold rounded-3 shadow-sm" [disabled]="loading">
                <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
                Send Reset Token
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
  resetToken = '';

  constructor(private authService: AuthService) {}

  onSubmit() {
    if (!this.email) {
      this.errorMessage = 'Please enter your email address.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.authService.forgotPassword(this.email).subscribe({
      next: (res) => {
        this.loading = false;
        this.successMessage = res.message || 'Password reset request generated.';
        if (res.resetToken) {
          this.resetToken = res.resetToken;
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || err.message || 'Request failed. Please try again.';
      }
    });
  }
}
