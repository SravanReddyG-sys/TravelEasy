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
        <div class="col-md-5 col-lg-4">
          <div class="card p-4 shadow">
            <div class="text-center mb-4">
              <i class="fa-solid fa-plane-departure text-primary fs-1 mb-2"></i>
              <h3 class="fw-bold">Sign In to TravelEasy</h3>
              <p class="text-muted small">Select a demo role or enter your credentials</p>
            </div>

            <!-- Demo Quick Login Buttons -->
            <div class="mb-4 bg-light p-3 rounded-3 border">
              <label class="form-label text-muted small fw-bold mb-2">Demo Quick Sign-In:</label>
              <div class="d-grid gap-2">
                <button class="btn btn-outline-primary btn-sm text-start" (click)="quickLogin('customer@traveleasy.com', 'customer123')">
                  <i class="fa-solid fa-user me-2"></i> Customer (John Doe)
                </button>
                <button class="btn btn-outline-success btn-sm text-start" (click)="quickLogin('busop@traveleasy.com', 'operator123')">
                  <i class="fa-solid fa-bus me-2"></i> Bus Operator (Express Travels)
                </button>
                <button class="btn btn-outline-warning btn-sm text-start" (click)="quickLogin('hotelmgr@traveleasy.com', 'manager123')">
                  <i class="fa-solid fa-hotel me-2"></i> Hotel Manager (Grand Residency)
                </button>
                <button class="btn btn-outline-danger btn-sm text-start" (click)="quickLogin('admin@traveleasy.com', 'admin123')">
                  <i class="fa-solid fa-user-shield me-2"></i> Administrator (Platform Admin)
                </button>
              </div>
            </div>

            <div *ngIf="errorMessage" class="alert alert-danger py-2 small mb-3">
              {{ errorMessage }}
            </div>

            <form (ngSubmit)="onSubmit()">
              <div class="mb-3">
                <label class="form-label font-weight-bold">Email Address</label>
                <input type="email" class="form-control" [(ngModel)]="email" name="email" required placeholder="name@example.com">
              </div>

              <div class="mb-3">
                <label class="form-label font-weight-bold">Password</label>
                <input type="password" class="form-control" [(ngModel)]="password" name="password" required placeholder="••••••••">
              </div>

              <button type="submit" class="btn btn-primary w-100 py-2 fw-bold rounded-pill" [disabled]="loading">
                <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
                Sign In
              </button>
            </form>

            <div class="text-center mt-3">
              <span class="small text-muted">Don't have an account? </span>
              <a routerLink="/register" class="small fw-bold text-decoration-none">Register here</a>
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
  loading = false;
  errorMessage = '';

  constructor(private authService: AuthService, private router: Router) {}

  quickLogin(e: string, p: string) {
    this.email = e;
    this.password = p;
    this.onSubmit();
  }

  onSubmit() {
    this.loading = true;
    this.errorMessage = '';

    this.authService.login({ email: this.email, password: this.password }).subscribe({
      next: (user) => {
        this.loading = false;
        switch (user.role) {
          case 'ROLE_CUSTOMER': this.router.navigate(['/customer']); break;
          case 'ROLE_BUS_OPERATOR': this.router.navigate(['/bus-operator']); break;
          case 'ROLE_HOTEL_MANAGER': this.router.navigate(['/hotel-manager']); break;
          case 'ROLE_ADMIN': this.router.navigate(['/admin']); break;
          default: this.router.navigate(['/']);
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || err.message || 'Invalid email or password. Please try again.';
      }
    });
  }
}
