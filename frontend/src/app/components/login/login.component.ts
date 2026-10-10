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
    <div class="container py-5">
      <div class="row justify-content-center">
        <div class="col-md-6 col-lg-5">
          <div class="card p-4 shadow-lg border-0 rounded-4">
            <div class="text-center mb-4">
              <div class="d-inline-flex align-items-center justify-content-center bg-primary text-white rounded-circle mb-3" style="width: 56px; height: 56px;">
                <i class="fa-solid fa-plane-departure fs-4"></i>
              </div>
              <h2 class="fw-bold text-dark">Welcome Back</h2>
              <p class="text-muted small">Login to your account and continue your journey</p>
            </div>

            <div *ngIf="errorMessage" class="alert alert-danger py-2 small mb-3 d-flex align-items-center">
              <i class="fa-solid fa-circle-exclamation me-2"></i>
              <div>{{ errorMessage }}</div>
            </div>

            <form (ngSubmit)="onSubmit()">
              <div class="mb-3">
                <label class="form-label fw-semibold text-secondary">Email Address</label>
                <div class="input-group">
                  <span class="input-group-text bg-light text-muted border-end-0"><i class="fa-regular fa-envelope"></i></span>
                  <input 
                    type="email" 
                    class="form-control border-start-0 ps-0" 
                    [(ngModel)]="email" 
                    name="email" 
                    required 
                    placeholder="you@example.com"
                  >
                </div>
              </div>

              <div class="mb-3">
                <label class="form-label fw-semibold text-secondary">Password</label>
                <div class="input-group">
                  <span class="input-group-text bg-light text-muted border-end-0"><i class="fa-solid fa-lock"></i></span>
                  <input 
                    [type]="showPassword ? 'text' : 'password'" 
                    class="form-control border-start-0 border-end-0 ps-0" 
                    [(ngModel)]="password" 
                    name="password" 
                    required 
                    placeholder="Enter your password"
                  >
                  <button 
                    type="button" 
                    class="btn btn-light border border-start-0 text-muted" 
                    (click)="showPassword = !showPassword"
                    tabindex="-1"
                  >
                    <i class="fa-solid" [ngClass]="showPassword ? 'fa-eye-slash' : 'fa-eye'"></i>
                  </button>
                </div>
              </div>

              <div class="d-flex justify-content-between align-items-center mb-4">
                <div class="form-check">
                  <input class="form-check-input" type="checkbox" id="rememberMe" [(ngModel)]="rememberMe" name="rememberMe">
                  <label class="form-check-label small text-muted" for="rememberMe">Remember me</label>
                </div>
                <a routerLink="/forgot-password" class="small text-primary fw-semibold text-decoration-none">Forgot password?</a>
              </div>

              <button type="submit" class="btn btn-primary w-100 py-2.5 fw-bold rounded-3 shadow-sm d-flex align-items-center justify-content-center" [disabled]="loading">
                <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
                <span>Login</span>
                <i class="fa-solid fa-arrow-right ms-2" *ngIf="!loading"></i>
              </button>
            </form>

            <div class="text-center mt-4 pt-3 border-top">
              <span class="small text-muted">New to Travel Easy? </span>
              <a routerLink="/register" class="small fw-bold text-primary text-decoration-none">Register</a>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class LoginComponent {
  email = '';
  password = '';
  showPassword = false;
  rememberMe = false;
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
