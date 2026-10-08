import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="container py-5">
      <div class="row justify-content-center">
        <div class="col-md-6 col-lg-5">
          <div class="card p-4 shadow">
            <div class="text-center mb-4">
              <i class="fa-solid fa-user-plus text-primary fs-1 mb-2"></i>
              <h3 class="fw-bold">Create TravelEasy Account</h3>
              <p class="text-muted small">Register as a Traveller or Service Partner</p>
            </div>

            <div *ngIf="errorMessage" class="alert alert-danger py-2 small mb-3">
              {{ errorMessage }}
            </div>

            <form (ngSubmit)="onSubmit()">
              <div class="mb-3">
                <label class="form-label font-weight-bold">Select Account Role</label>
                <select class="form-select" [(ngModel)]="role" name="role" required>
                  <option value="ROLE_CUSTOMER">Traveller (Customer)</option>
                  <option value="ROLE_BUS_OPERATOR">Bus Operator Partner</option>
                  <option value="ROLE_HOTEL_MANAGER">Hotel Manager Partner</option>
                </select>
              </div>

              <div class="mb-3">
                <label class="form-label font-weight-bold">Full Name / Agency Name</label>
                <input type="text" class="form-control" [(ngModel)]="fullName" name="fullName" required placeholder="e.g. John Doe / Express Travels">
              </div>

              <div class="mb-3">
                <label class="form-label font-weight-bold">Email Address</label>
                <input type="email" class="form-control" [(ngModel)]="email" name="email" required placeholder="name@example.com">
              </div>

              <div class="mb-3">
                <label class="form-label font-weight-bold">Phone Number</label>
                <input type="text" class="form-control" [(ngModel)]="phone" name="phone" required placeholder="+1234567890">
              </div>

              <div class="mb-3">
                <label class="form-label font-weight-bold">Password</label>
                <input type="password" class="form-control" [(ngModel)]="password" name="password" required placeholder="••••••••">
              </div>

              <button type="submit" class="btn btn-primary w-100 py-2 fw-bold rounded-pill" [disabled]="loading">
                <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
                Complete Registration
              </button>
            </form>

            <div class="text-center mt-3">
              <span class="small text-muted">Already registered? </span>
              <a routerLink="/login" class="small fw-bold text-decoration-none">Sign In</a>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class RegisterComponent {
  fullName = '';
  email = '';
  phone = '';
  password = '';
  role = 'ROLE_CUSTOMER';
  loading = false;
  errorMessage = '';

  constructor(private authService: AuthService, private router: Router) {}

  onSubmit() {
    this.loading = true;
    this.errorMessage = '';

    const payload = {
      fullName: this.fullName,
      email: this.email,
      phone: this.phone,
      password: this.password,
      role: this.role
    };

    this.authService.register(payload).subscribe({
      next: (user) => {
        this.loading = false;
        switch (user.role) {
          case 'ROLE_CUSTOMER': this.router.navigate(['/customer']); break;
          case 'ROLE_BUS_OPERATOR': this.router.navigate(['/bus-operator']); break;
          case 'ROLE_HOTEL_MANAGER': this.router.navigate(['/hotel-manager']); break;
          default: this.router.navigate(['/']);
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Registration failed. Please check inputs.';
      }
    });
  }
}
