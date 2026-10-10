import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-account-pending',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="container py-5">
      <div class="row justify-content-center">
        <div class="col-md-8 col-lg-6 text-center">
          <div class="card p-5 shadow-lg border-0 rounded-4">
            <div class="mb-4">
              <div class="d-inline-flex align-items-center justify-content-center bg-warning-subtle text-warning rounded-circle p-4" style="width: 100px; height: 100px;">
                <i class="fa-solid fa-clock-rotate-left display-4 text-warning"></i>
              </div>
            </div>

            <h2 class="fw-bold text-dark mb-3">Your application is under review.</h2>
            <p class="text-muted leading-relaxed mb-4">
              Thank you for registering with Travel Easy. Our verification team is currently reviewing your business information and supporting documentation. We will notify you via email once a decision has been made.
            </p>

            <div class="p-3 bg-light rounded-3 border mb-4 text-start small">
              <div class="d-flex align-items-center mb-2">
                <i class="fa-solid fa-circle-info text-primary me-2"></i>
                <span class="fw-bold">What happens next?</span>
              </div>
              <ul class="mb-0 ps-3 text-secondary">
                <li>Administrator verification usually takes 1-2 business days.</li>
                <li>You cannot list buses/hotels or publish schedules until approved.</li>
              </ul>
            </div>

            <div class="d-flex gap-2 justify-content-center">
              <button class="btn btn-outline-secondary px-4 py-2 rounded-pill fw-semibold" (click)="logout()">
                <i class="fa-solid fa-right-from-bracket me-1"></i> Sign Out
              </button>
              <a routerLink="/" class="btn btn-primary px-4 py-2 rounded-pill fw-semibold">
                <i class="fa-solid fa-house me-1"></i> Return Home
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AccountPendingComponent {
  constructor(private authService: AuthService, private router: Router) {}

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
