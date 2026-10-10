import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-register-select',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="container py-5">
      <div class="text-center mb-5">
        <h1 class="fw-bold text-dark display-5">Create Your <span class="text-primary">Account</span></h1>
        <p class="lead text-muted">Choose the account type that best fits you</p>
      </div>

      <div class="row g-4 justify-content-center">
        <!-- Traveller Card -->
        <div class="col-md-4 col-lg-4">
          <div class="card h-100 shadow-sm border-0 rounded-4 text-center p-4 hover-shadow transition-all">
            <div class="my-3">
              <div class="d-inline-flex align-items-center justify-content-center bg-primary-subtle text-primary rounded-circle p-4 mb-2" style="width: 100px; height: 100px;">
                <i class="fa-solid fa-user-gear fs-1 text-primary"></i>
              </div>
            </div>
            <div class="card-body d-flex flex-column p-0">
              <h3 class="fw-bold text-dark mb-2">Traveller</h3>
              <p class="text-muted small mb-4 flex-grow-1">
                Book buses and hotels for your trips across India with instant confirmations.
              </p>
              <a routerLink="/register/traveller" class="btn btn-primary btn-lg rounded-3 fw-bold w-100 py-2.5">
                Register as Traveller
              </a>
            </div>
          </div>
        </div>

        <!-- Bus Operator Card -->
        <div class="col-md-4 col-lg-4">
          <div class="card h-100 shadow-sm border-0 rounded-4 text-center p-4 hover-shadow transition-all">
            <div class="my-3">
              <div class="d-inline-flex align-items-center justify-content-center bg-primary-subtle text-primary rounded-circle p-4 mb-2" style="width: 100px; height: 100px;">
                <i class="fa-solid fa-bus fs-1 text-primary"></i>
              </div>
            </div>
            <div class="card-body d-flex flex-column p-0">
              <h3 class="fw-bold text-dark mb-2">Bus Operator</h3>
              <p class="text-muted small mb-4 flex-grow-1">
                List and manage your bus fleet, routes, schedules, fares, and bookings.
              </p>
              <a routerLink="/register/bus-operator" class="btn btn-primary btn-lg rounded-3 fw-bold w-100 py-2.5">
                Register as Bus Operator
              </a>
            </div>
          </div>
        </div>

        <!-- Hotel Partner Card -->
        <div class="col-md-4 col-lg-4">
          <div class="card h-100 shadow-sm border-0 rounded-4 text-center p-4 hover-shadow transition-all">
            <div class="my-3">
              <div class="d-inline-flex align-items-center justify-content-center bg-primary-subtle text-primary rounded-circle p-4 mb-2" style="width: 100px; height: 100px;">
                <i class="fa-solid fa-hotel fs-1 text-primary"></i>
              </div>
            </div>
            <div class="card-body d-flex flex-column p-0">
              <h3 class="fw-bold text-dark mb-2">Hotel Partner</h3>
              <p class="text-muted small mb-4 flex-grow-1">
                List and manage your hotel property, rooms, rates, and guest reservations.
              </p>
              <a routerLink="/register/hotel-partner" class="btn btn-primary btn-lg rounded-3 fw-bold w-100 py-2.5">
                Register as Hotel Partner
              </a>
            </div>
          </div>
        </div>
      </div>

      <div class="text-center mt-5">
        <span class="text-muted">Already have an account? </span>
        <a routerLink="/login" class="fw-bold text-primary text-decoration-none">Login</a>
      </div>
    </div>
  `,
  styles: [`
    .hover-shadow:hover {
      transform: translateY(-5px);
      box-shadow: 0 1rem 3rem rgba(0,0,0,.125)!important;
    }
    .transition-all {
      transition: all 0.25s ease-in-out;
    }
  `]
})
export class RegisterSelectComponent {}
