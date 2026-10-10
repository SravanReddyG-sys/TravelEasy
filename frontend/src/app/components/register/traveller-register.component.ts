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
    <div class="container py-5">
      <div class="row justify-content-center">
        <div class="col-lg-10">
          <div class="card shadow-lg border-0 rounded-4 overflow-hidden">
            <div class="row g-0">
              <!-- Form Section -->
              <div class="col-lg-7 p-4 p-md-5">
                <a routerLink="/register" class="text-decoration-none text-muted small fw-semibold d-inline-flex align-items-center mb-4">
                  <i class="fa-solid fa-arrow-left me-2"></i> Back to account selection
                </a>

                <h2 class="fw-bold text-dark mb-1">Traveller <span class="text-primary">Registration</span></h2>
                <p class="text-muted small mb-4">Create your account to start booking.</p>

                <div *ngIf="errorMessage" class="alert alert-danger py-2 small mb-4">
                  <i class="fa-solid fa-circle-exclamation me-1"></i> {{ errorMessage }}
                </div>

                <form (ngSubmit)="onSubmit()">
                  <div class="row g-3">
                    <div class="col-md-6">
                      <label class="form-label fw-semibold text-secondary small">First Name <span class="text-danger">*</span></label>
                      <div class="input-group">
                        <span class="input-group-text bg-light text-muted border-end-0"><i class="fa-regular fa-user"></i></span>
                        <input type="text" class="form-control border-start-0" [(ngModel)]="firstName" name="firstName" required placeholder="Enter your first name">
                      </div>
                    </div>

                    <div class="col-md-6">
                      <label class="form-label fw-semibold text-secondary small">Last Name <span class="text-danger">*</span></label>
                      <div class="input-group">
                        <span class="input-group-text bg-light text-muted border-end-0"><i class="fa-regular fa-user"></i></span>
                        <input type="text" class="form-control border-start-0" [(ngModel)]="lastName" name="lastName" required placeholder="Enter your last name">
                      </div>
                    </div>

                    <div class="col-12">
                      <label class="form-label fw-semibold text-secondary small">Email Address <span class="text-danger">*</span></label>
                      <div class="input-group">
                        <span class="input-group-text bg-light text-muted border-end-0"><i class="fa-regular fa-envelope"></i></span>
                        <input type="email" class="form-control border-start-0" [(ngModel)]="email" name="email" required placeholder="you@example.com">
                      </div>
                    </div>

                    <div class="col-12">
                      <label class="form-label fw-semibold text-secondary small">Phone Number <span class="text-danger">*</span></label>
                      <div class="input-group">
                        <span class="input-group-text bg-light border-end-0">
                          <span class="me-1">🇮🇳</span> +91
                        </span>
                        <input type="tel" class="form-control" [(ngModel)]="phone" name="phone" required placeholder="9876543210">
                      </div>
                    </div>

                    <div class="col-md-6">
                      <label class="form-label fw-semibold text-secondary small">Password <span class="text-danger">*</span></label>
                      <div class="input-group">
                        <span class="input-group-text bg-light text-muted border-end-0"><i class="fa-solid fa-lock"></i></span>
                        <input [type]="showPassword ? 'text' : 'password'" class="form-control border-start-0 border-end-0" [(ngModel)]="password" name="password" required placeholder="Create a strong password">
                        <button type="button" class="btn btn-light border border-start-0 text-muted" (click)="showPassword = !showPassword" tabindex="-1">
                          <i class="fa-solid" [ngClass]="showPassword ? 'fa-eye-slash' : 'fa-eye'"></i>
                        </button>
                      </div>
                    </div>

                    <div class="col-md-6">
                      <label class="form-label fw-semibold text-secondary small">Confirm Password <span class="text-danger">*</span></label>
                      <div class="input-group">
                        <span class="input-group-text bg-light text-muted border-end-0"><i class="fa-solid fa-lock"></i></span>
                        <input [type]="showPassword ? 'text' : 'password'" class="form-control border-start-0 border-end-0" [(ngModel)]="confirmPassword" name="confirmPassword" required placeholder="Re-enter your password">
                        <button type="button" class="btn btn-light border border-start-0 text-muted" (click)="showPassword = !showPassword" tabindex="-1">
                          <i class="fa-solid" [ngClass]="showPassword ? 'fa-eye-slash' : 'fa-eye'"></i>
                        </button>
                      </div>
                    </div>

                    <div class="col-12 my-3">
                      <div class="form-check">
                        <input class="form-check-input" type="checkbox" id="terms" [(ngModel)]="termsAccepted" name="termsAccepted" required>
                        <label class="form-check-label small text-muted" for="terms">
                          I agree to the <a href="#" class="text-primary text-decoration-none">Terms and Conditions</a> and <a href="#" class="text-primary text-decoration-none">Privacy Policy</a>.
                        </label>
                      </div>
                    </div>

                    <div class="col-12">
                      <button type="submit" class="btn btn-primary w-100 py-2.5 fw-bold rounded-3 shadow-sm d-flex align-items-center justify-content-center" [disabled]="loading">
                        <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
                        <span>Create Account</span>
                        <i class="fa-solid fa-arrow-right ms-2" *ngIf="!loading"></i>
                      </button>
                    </div>
                  </div>
                </form>
              </div>

              <!-- Visual Banner Section -->
              <div class="col-lg-5 bg-primary text-white d-none d-lg-flex flex-column justify-content-between p-5 position-relative overflow-hidden" style="background: linear-gradient(135deg, #0d6efd 0%, #0a58ca 100%);">
                <div class="position-relative z-1">
                  <span class="badge bg-white text-primary fw-bold mb-3 px-3 py-2 rounded-pill">Traveler Portal</span>
                  <h3 class="display-6 fw-bold mb-3">New journeys, brighter stories</h3>
                  <p class="opacity-75">Join thousands of travelers who book hassle-free bus tickets and verified hotel stays with Travel Easy.</p>
                </div>
                <div class="position-relative z-1">
                  <div class="p-3 rounded-3 bg-white bg-opacity-10 backdrop-blur">
                    <div class="d-flex align-items-center">
                      <i class="fa-solid fa-shield-halved fs-3 me-3 text-warning"></i>
                      <div>
                        <div class="fw-bold small">Instant Verification</div>
                        <div class="small opacity-75">Your account is ready immediately after signup.</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
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

    const formattedPhone = this.phone.startsWith('+') ? this.phone : `+91${this.phone}`;

    const payload = {
      firstName: this.firstName,
      lastName: this.lastName,
      email: this.email,
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
