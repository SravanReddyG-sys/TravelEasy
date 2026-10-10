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
    <div class="container py-5">
      <div class="row justify-content-center">
        <div class="col-md-6 col-lg-5">
          <div class="card p-4 p-md-5 shadow-lg border-0 rounded-4">
            <div class="text-center mb-4">
              <div class="d-inline-flex align-items-center justify-content-center bg-primary-subtle text-primary rounded-circle mb-3" style="width: 60px; height: 60px;">
                <i class="fa-solid fa-lock fs-3 text-primary"></i>
              </div>
              <h2 class="fw-bold text-dark">Set New Password</h2>
              <p class="text-muted small">Enter your password reset token and choose a strong new password.</p>
            </div>

            <div *ngIf="successMessage" class="alert alert-success py-2 small mb-4">
              <i class="fa-solid fa-circle-check me-1"></i> {{ successMessage }}
              <div class="mt-2">
                <a routerLink="/login" class="btn btn-sm btn-primary">Proceed to Login</a>
              </div>
            </div>

            <div *ngIf="errorMessage" class="alert alert-danger py-2 small mb-4">
              <i class="fa-solid fa-circle-exclamation me-1"></i> {{ errorMessage }}
            </div>

            <form *ngIf="!successMessage" (ngSubmit)="onSubmit()">
              <div class="mb-3">
                <label class="form-label fw-semibold text-secondary small">Reset Token</label>
                <input type="text" class="form-control" [(ngModel)]="token" name="token" required placeholder="Paste your reset token">
              </div>

              <div class="mb-3">
                <label class="form-label fw-semibold text-secondary small">New Password</label>
                <input type="password" class="form-control" [(ngModel)]="newPassword" name="newPassword" required placeholder="Minimum 8 characters">
              </div>

              <div class="mb-4">
                <label class="form-label fw-semibold text-secondary small">Confirm New Password</label>
                <input type="password" class="form-control" [(ngModel)]="confirmNewPassword" name="confirmNewPassword" required placeholder="Re-enter new password">
              </div>

              <button type="submit" class="btn btn-primary w-100 py-2.5 fw-bold rounded-3 shadow-sm" [disabled]="loading">
                <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
                Reset Password
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
  newPassword = '';
  confirmNewPassword = '';
  loading = false;
  successMessage = '';
  errorMessage = '';

  constructor(private route: ActivatedRoute, private authService: AuthService, private router: Router) {}

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      if (params['token']) {
        this.token = params['token'];
      }
    });
  }

  onSubmit() {
    if (!this.token || !this.newPassword || !this.confirmNewPassword) {
      this.errorMessage = 'Please complete all fields.';
      return;
    }

    if (this.newPassword !== this.confirmNewPassword) {
      this.errorMessage = 'New password and confirm password do not match.';
      return;
    }

    if (this.newPassword.length < 8) {
      this.errorMessage = 'Password must be at least 8 characters long.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    const payload = {
      token: this.token,
      newPassword: this.newPassword,
      confirmNewPassword: this.confirmNewPassword
    };

    this.authService.resetPassword(payload).subscribe({
      next: (res) => {
        this.loading = false;
        this.successMessage = res.message || 'Password reset successfully.';
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || err.message || 'Reset failed. Token may be invalid or expired.';
      }
    });
  }
}
