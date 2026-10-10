import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-account-suspended',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="container py-5">
      <div class="row justify-content-center">
        <div class="col-md-8 col-lg-6 text-center">
          <div class="card p-5 shadow-lg border-0 rounded-4">
            <div class="mb-4">
              <div class="d-inline-flex align-items-center justify-content-center bg-danger-subtle text-danger rounded-circle p-4" style="width: 100px; height: 100px;">
                <i class="fa-solid fa-user-lock display-4 text-danger"></i>
              </div>
            </div>

            <h2 class="fw-bold text-dark mb-3">Your account has been temporarily suspended.</h2>
            <p class="text-muted leading-relaxed mb-4">
              Access to your Travel Easy account has been restricted by platform administration. Please review the information provided or contact support for assistance with account status inquiries.
            </p>

            <div class="d-flex gap-2 justify-content-center">
              <button class="btn btn-outline-secondary px-4 py-2 rounded-pill fw-semibold" (click)="logout()">
                <i class="fa-solid fa-right-from-bracket me-1"></i> Sign Out
              </button>
              <a href="mailto:support@traveleasy.com" class="btn btn-dark px-4 py-2 rounded-pill fw-semibold">
                <i class="fa-solid fa-headset me-1"></i> Contact Support
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AccountSuspendedComponent {
  constructor(private authService: AuthService, private router: Router) {}

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
