import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { BusService } from '../../services/bus.service';
import { BookingService } from '../../services/booking.service';
import { User } from '../../models/user.model';
import { Route, RouteStop, RoutePoint, CreateRouteRequest, ScheduleStop, Schedule, Bus, BusBasicDetailsForm } from '../../models/bus.model';

export interface WizardSegmentFareItem {
  segmentIndex: number;
  fromStopOrder: number;
  toStopOrder: number;
  fromStopName: string;
  toStopName: string;
  distanceKm: number;
  fare: number | null;
  fareError?: string | null;
}

export interface WizardJourneyFareItem {
  journeyIndex: number;
  fromStopOrder: number;
  toStopOrder: number;
  fromStopName: string;
  toStopName: string;
  distanceKm: number;
  calculatedFare: number;
  operatorFare: number | null;
  isOverridden: boolean;
  status: 'Auto' | 'Overridden';
  fareError?: string | null;
}

interface StopFormItem {
  stopOrder: number;
  stopName: string;
  stopType: 'ORIGIN' | 'INTERMEDIATE' | 'DESTINATION';
  distanceKm: number;
  boardingPoints: { pointName: string; address: string; timeSlot: string }[];
  droppingPoints: { pointName: string; address: string; timeSlot: string }[];
}

interface SchedStopTimingItem {
  stopOrder: number;
  stopName: string;
  stopType: string;
  distanceKm?: number;
  arrivalTime: string;
  departureTime: string;
  consecutiveFare: number;
}

interface TrendDay {
  dateLabel: string;
  fullDate: string;
  count: number;
  heightPx: number;
}

@Component({
  selector: 'app-bus-operator-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="operator-dashboard-shell">

      <!-- ==================== TOP HEADER ==================== -->
      <header class="shell-header d-flex align-items-center justify-content-between px-4">
        <!-- Left: Brand Logo -->
        <div class="d-flex align-items-center gap-3">
          <button class="btn btn-sm btn-light border-0 d-md-none text-muted" (click)="isSidebarCollapsed = !isSidebarCollapsed">
            <i class="fa-solid fa-bars fs-5"></i>
          </button>
          <a class="te-brand" (click)="activeNav = 'dashboard'" role="button" title="TravelEasy Operator Dashboard">
            <span class="te-logo-icon">
              <i class="fa-solid fa-paper-plane"></i>
            </span>
            <span class="te-brand-title">Travel<span class="te-brand-blue">Easy</span></span>
          </a>
        </div>

        <!-- Right: Notifications & Operator Profile -->
        <div class="d-flex align-items-center gap-3">
          <!-- Notification Bell -->
          <div class="position-relative cursor-pointer p-2" title="Notifications">
            <i class="fa-solid fa-bell text-secondary fs-5"></i>
            <span class="position-absolute top-0 end-0 translate-middle badge rounded-pill bg-danger border border-white" style="font-size: 10px;">
              1
            </span>
          </div>

          <!-- Operator Profile Pill & Dropdown -->
          <div class="dropdown">
            <div class="d-flex align-items-center gap-2 cursor-pointer p-1 rounded-pill pe-3 hover-bg"
                 id="operatorUserDropdown"
                 data-bs-toggle="dropdown" 
                 aria-expanded="false">
              <!-- Avatar -->
              <div class="operator-avatar">
                <span>{{ getInitials() }}</span>
              </div>
              <!-- Name & Role -->
              <div class="d-none d-sm-block text-start lh-sm">
                <div class="fw-bold text-dark small">{{ currentUser?.fullName || 'Bus Operator' }}</div>
                <div class="text-muted" style="font-size: 11px;">Bus Operator</div>
              </div>
              <i class="fa-solid fa-chevron-down text-muted ms-1" style="font-size: 11px;"></i>
            </div>
            <ul class="dropdown-menu dropdown-menu-end shadow-sm border-0 mt-2 rounded-3" aria-labelledby="operatorUserDropdown">
              <li>
                <div class="px-3 py-2 border-bottom">
                  <div class="fw-bold text-dark small">{{ currentUser?.fullName || 'Operator' }}</div>
                  <small class="text-muted">{{ currentUser?.email || 'No email provided' }}</small>
                </div>
              </li>
              <li>
                <a class="dropdown-item py-2 small" (click)="activeNav = 'profile'" role="button">
                  <i class="fa-solid fa-user-gear me-2 text-primary"></i>Profile & Account
                </a>
              </li>
              <li>
                <a class="dropdown-item py-2 small" (click)="activeNav = 'support'" role="button">
                  <i class="fa-solid fa-headset me-2 text-info"></i>Help & Support
                </a>
              </li>
              <li><hr class="dropdown-divider my-1"></li>
              <li>
                <a class="dropdown-item py-2 small text-danger" (click)="logout()" role="button">
                  <i class="fa-solid fa-right-from-bracket me-2"></i>Sign Out
                </a>
              </li>
            </ul>
          </div>
        </div>
      </header>

      <!-- ==================== SHELL BODY (SIDEBAR + MAIN CONTENT) ==================== -->
      <div class="operator-shell-body">

        <!-- COLLAPSIBLE SIDEBAR -->
        <aside class="operator-sidebar py-3 px-2" [class.collapsed]="isSidebarCollapsed">
          <!-- Sidebar Toggle -->
          <div class="d-flex align-items-center mb-3 px-2" [class.justify-content-center]="isSidebarCollapsed" [class.justify-content-between]="!isSidebarCollapsed">
            <span *ngIf="!isSidebarCollapsed" class="fw-bold text-uppercase text-muted" style="font-size: 11px; letter-spacing: 0.5px;">Navigation</span>
            <button class="btn btn-sm btn-light border-0 rounded-circle text-muted"
                    (click)="isSidebarCollapsed = !isSidebarCollapsed"
                    [title]="isSidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'">
              <i class="fa-solid" [class.fa-angles-right]="isSidebarCollapsed" [class.fa-angles-left]="!isSidebarCollapsed"></i>
            </button>
          </div>

          <!-- Navigation Links -->
          <nav class="nav flex-column gap-1">
            <!-- 1. Dashboard -->
            <a class="nav-link-custom" 
               [class.active]="activeNav === 'dashboard'" 
               (click)="activeNav = 'dashboard'"
               title="Dashboard">
              <i class="fa-solid fa-house sidebar-icon"></i>
              <span *ngIf="!isSidebarCollapsed">Dashboard</span>
            </a>

            <!-- 2. My Buses -->
            <a class="nav-link-custom" 
               [class.active]="activeNav === 'buses'" 
               (click)="activeNav = 'buses'"
               title="My Buses">
              <i class="fa-solid fa-bus sidebar-icon"></i>
              <span *ngIf="!isSidebarCollapsed">My Buses</span>
            </a>

            <!-- 3. Routes -->
            <a class="nav-link-custom" 
               [class.active]="activeNav === 'routes'" 
               (click)="activeNav = 'routes'"
               title="Routes">
              <i class="fa-solid fa-route sidebar-icon"></i>
              <span *ngIf="!isSidebarCollapsed">Routes</span>
            </a>

            <!-- 4. Schedules -->
            <a class="nav-link-custom" 
               [class.active]="activeNav === 'schedules'" 
               (click)="activeNav = 'schedules'"
               title="Schedules">
              <i class="fa-solid fa-calendar-days sidebar-icon"></i>
              <span *ngIf="!isSidebarCollapsed">Schedules</span>
            </a>

            <!-- 5. Bookings Manifest -->
            <a class="nav-link-custom" 
               [class.active]="activeNav === 'bookings'" 
               (click)="activeNav = 'bookings'; loadBookings()"
               title="Bookings Manifest">
              <i class="fa-solid fa-file-lines sidebar-icon"></i>
              <span *ngIf="!isSidebarCollapsed">Bookings Manifest</span>
            </a>

            <!-- 6. Profile & Account -->
            <a class="nav-link-custom" 
               [class.active]="activeNav === 'profile'" 
               (click)="activeNav = 'profile'"
               title="Profile & Account">
              <i class="fa-solid fa-user-gear sidebar-icon"></i>
              <span *ngIf="!isSidebarCollapsed">Profile</span>
            </a>

            <!-- 7. Support -->
            <a class="nav-link-custom" 
               [class.active]="activeNav === 'support'" 
               (click)="activeNav = 'support'"
               title="Support">
              <i class="fa-solid fa-headset sidebar-icon"></i>
              <span *ngIf="!isSidebarCollapsed">Support</span>
            </a>
          </nav>
        </aside>

        <!-- MAIN DASHBOARD CONTENT AREA -->
        <main class="main-content flex-grow-1">

          <!-- ==================== VIEW 1: OVERVIEW PAGE (MOCKUP EXACT MATCH) ==================== -->
          <div *ngIf="activeNav === 'dashboard'">

            <!-- Welcome Section -->
            <div class="mb-4">
              <h2 class="fw-bold text-dark mb-1">Welcome, {{ getFirstName() }}!</h2>
              <p class="text-muted mb-0">Manage your bus services and bookings.</p>
            </div>

            <!-- Summary Cards Row (4 Cards) -->
            <div class="row g-3 g-xl-4 mb-4">
              <!-- Card 1: Total Buses -->
              <div class="col-xl-3 col-sm-6">
                <div class="metric-card h-100">
                  <div class="metric-icon-box" style="background-color: #e0f2fe; color: #0284c7;">
                    <i class="fa-solid fa-bus"></i>
                  </div>
                  <div>
                    <div class="metric-label">Total Buses</div>
                    <div class="metric-value">{{ myBuses.length }}</div>
                  </div>
                </div>
              </div>

              <!-- Card 2: Active Routes -->
              <div class="col-xl-3 col-sm-6">
                <div class="metric-card h-100">
                  <div class="metric-icon-box" style="background-color: #dcfce7; color: #16a34a;">
                    <i class="fa-solid fa-route"></i>
                  </div>
                  <div>
                    <div class="metric-label">Active Routes</div>
                    <div class="metric-value">{{ getActiveRoutesCount() }}</div>
                  </div>
                </div>
              </div>

              <!-- Card 3: Total Bookings -->
              <div class="col-xl-3 col-sm-6">
                <div class="metric-card h-100">
                  <div class="metric-icon-box" style="background-color: #ffedd5; color: #ea580c;">
                    <i class="fa-solid fa-file-invoice"></i>
                  </div>
                  <div>
                    <div class="metric-label">Total Bookings</div>
                    <div class="metric-value">{{ operatorBookings.length }}</div>
                  </div>
                </div>
              </div>

              <!-- Card 4: Revenue (This Month) -->
              <div class="col-xl-3 col-sm-6">
                <div class="metric-card h-100">
                  <div class="metric-icon-box" style="background-color: #f3e8ff; color: #9333ea;">
                    <i class="fa-solid fa-chart-line"></i>
                  </div>
                  <div>
                    <div class="metric-label">Revenue (This Month)</div>
                    <div class="metric-value">{{ formattedMonthlyRevenue }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Quick Actions Section -->
            <div class="mb-4">
              <h5 class="fw-bold text-dark mb-3">Quick Actions</h5>
              <div class="row g-3">
                <!-- Action 1: Add New Bus -->
                <div class="col-md-4">
                  <div class="quick-action-card" (click)="activeNav = 'buses'; startAddBusWizard()" role="button">
                    <div class="d-flex align-items-center">
                      <div class="qa-icon-box">
                        <i class="fa-solid fa-bus"></i>
                      </div>
                      <div>
                        <div class="fw-bold text-dark">Add New Bus</div>
                        <small class="text-muted">Register a new bus</small>
                      </div>
                    </div>
                    <i class="fa-solid fa-arrow-right text-muted"></i>
                  </div>
                </div>

                <!-- Action 2: Add Route -->
                <div class="col-md-4">
                  <div class="quick-action-card" (click)="activeNav = 'routes'; startCreateRouteWizard()" role="button">
                    <div class="d-flex align-items-center">
                      <div class="qa-icon-box">
                        <i class="fa-solid fa-location-dot"></i>
                      </div>
                      <div>
                        <div class="fw-bold text-dark">Add Route</div>
                        <small class="text-muted">Create new route</small>
                      </div>
                    </div>
                    <i class="fa-solid fa-arrow-right text-muted"></i>
                  </div>
                </div>

                <!-- Action 3: Manage Schedules -->
                <div class="col-md-4">
                  <div class="quick-action-card" (click)="openAddScheduleModal()" role="button">
                    <div class="d-flex align-items-center">
                      <div class="qa-icon-box">
                        <i class="fa-solid fa-calendar-plus"></i>
                      </div>
                      <div>
                        <div class="fw-bold text-dark">Manage Schedules</div>
                        <small class="text-muted">Set timings & fares</small>
                      </div>
                    </div>
                    <i class="fa-solid fa-arrow-right text-muted"></i>
                  </div>
                </div>
              </div>
            </div>

            <!-- Row: Booking Trends (Left) & Recent Bookings (Right) -->
            <div class="row g-4 mb-4">
              <!-- Left Column: Booking Trends Chart -->
              <div class="col-lg-6">
                <div class="section-card h-100">
                  <div class="d-flex justify-content-between align-items-center mb-3">
                    <div class="d-flex align-items-center gap-2">
                      <i class="fa-solid fa-chart-simple text-primary fs-5"></i>
                      <h6 class="fw-bold text-dark mb-0">Booking Trends</h6>
                    </div>
                    <select class="form-select form-select-sm w-auto border-light-subtle rounded-pill text-muted small" 
                            [(ngModel)]="selectedTrendRange" 
                            (change)="computeBookingTrends()">
                      <option value="7">Last 7 days</option>
                      <option value="30">Last 30 days</option>
                    </select>
                  </div>

                  <!-- Bar Chart Container -->
                  <div class="chart-container pt-3">
                    <div class="d-flex align-items-end justify-content-between" style="height: 180px; padding-bottom: 24px; border-bottom: 1px solid #e2e8f0;">
                      <div *ngFor="let day of bookingTrends" class="d-flex flex-column align-items-center flex-grow-1" style="height: 100%; justify-content: flex-end;">
                        <!-- Hover Value Tooltip -->
                        <span class="small fw-bold text-muted mb-1" style="font-size: 11px;">
                          {{ day.count > 0 ? day.count : '' }}
                        </span>
                        <!-- Bar -->
                        <div class="chart-bar-column rounded-top"
                             [style.height.px]="day.heightPx"
                             [title]="day.count + ' bookings on ' + day.dateLabel"
                             style="width: 32px; background: linear-gradient(180deg, #38bdf8 0%, #0284c7 100%);">
                        </div>
                      </div>
                    </div>
                    <!-- X-Axis Labels -->
                    <div class="d-flex justify-content-between pt-2">
                      <div *ngFor="let day of bookingTrends" class="text-center flex-grow-1 text-muted" style="font-size: 11px;">
                        {{ day.dateLabel }}
                      </div>
                    </div>
                  </div>
                  <div *ngIf="operatorBookings.length === 0" class="text-center py-2 text-muted small">
                    <i class="fa-solid fa-info-circle me-1"></i> No confirmed bookings in this period yet.
                  </div>
                </div>
              </div>

              <!-- Right Column: Recent Bookings Table -->
              <div class="col-lg-6">
                <div class="section-card h-100">
                  <div class="d-flex justify-content-between align-items-center mb-3">
                    <div class="d-flex align-items-center gap-2">
                      <i class="fa-solid fa-file-invoice text-primary fs-5"></i>
                      <h6 class="fw-bold text-dark mb-0">Recent Bookings</h6>
                    </div>
                    <button class="btn btn-link btn-sm text-primary text-decoration-none p-0 fw-semibold" (click)="activeNav = 'bookings'">
                      View All <i class="fa-solid fa-arrow-right ms-1"></i>
                    </button>
                  </div>

                  <div class="table-responsive">
                    <table class="table table-sm align-middle mb-0">
                      <thead class="text-muted small" style="border-bottom: 1px solid #e2e8f0;">
                        <tr>
                          <th>Booking ID</th>
                          <th>Route</th>
                          <th>Travel Date</th>
                          <th class="text-end">Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr *ngFor="let b of getRecentBookings()">
                          <td class="fw-semibold text-dark">#{{ b.bookingRef }}</td>
                          <td class="text-muted small">{{ formatBookingRoute(b) }}</td>
                          <td class="text-muted small">{{ formatBookingDate(b) }}</td>
                          <td class="text-end">
                            <span class="badge rounded-pill"
                                  [class.badge-status-confirmed]="b.status === 'CONFIRMED' || b.status === 'COMPLETED'"
                                  [class.badge-status-cancelled]="b.status === 'CANCELLED' || b.status === 'FAILED'">
                              {{ b.status === 'CONFIRMED' ? 'Confirmed' : (b.status === 'CANCELLED' ? 'Cancelled' : b.status) }}
                            </span>
                          </td>
                        </tr>
                        <tr *ngIf="operatorBookings.length === 0">
                          <td colspan="4" class="text-center py-4 text-muted small">No customer bookings yet.</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            </div>

            <!-- Bottom Section: Upcoming Trips -->
            <div class="section-card mb-4">
              <div class="d-flex justify-content-between align-items-center mb-3">
                <div class="d-flex align-items-center gap-2">
                  <i class="fa-solid fa-bus text-primary fs-5"></i>
                  <h6 class="fw-bold text-dark mb-0">Upcoming Trips</h6>
                </div>
                <button class="btn btn-link btn-sm text-primary text-decoration-none p-0 fw-semibold" (click)="activeNav = 'schedules'">
                  View All <i class="fa-solid fa-arrow-right ms-1"></i>
                </button>
              </div>

              <div class="table-responsive">
                <table class="table align-middle mb-0">
                  <thead class="text-muted small" style="border-bottom: 1px solid #e2e8f0;">
                    <tr>
                      <th>Date</th>
                      <th>Time</th>
                      <th>Route</th>
                      <th>Bus</th>
                      <th>Seats</th>
                      <th class="text-end">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let s of getUpcomingTrips()">
                      <td class="fw-semibold text-dark">{{ s.travelDate | date:'mediumDate' }}</td>
                      <td class="text-muted">{{ s.departureTime }}</td>
                      <td>
                        <span class="fw-semibold text-dark">{{ s.origin }}</span>
                        <i class="fa-solid fa-arrow-right text-muted mx-2 small"></i>
                        <span class="fw-semibold text-dark">{{ s.destination }}</span>
                      </td>
                      <td>
                        <span class="badge bg-light text-dark border">{{ s.busNumber }}</span>
                      </td>
                      <td class="text-muted">{{ formatOccupancy(s) }}</td>
                      <td class="text-end">
                        <span class="badge badge-status-scheduled rounded-pill">
                          {{ s.status === 'SCHEDULED' ? 'On Time' : s.status }}
                        </span>
                      </td>
                    </tr>
                    <tr *ngIf="getUpcomingTrips().length === 0">
                      <td colspan="6" class="text-center py-4 text-muted small">
                        No upcoming scheduled departures found. Click "Manage Schedules" to schedule a trip.
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

          </div>

          <!-- ==================== VIEW 2: REGISTERED BUSES / ADD BUS WIZARD ==================== -->
          <div *ngIf="activeNav === 'buses'">
            
            <!-- SUBVIEW 1: Fleet List -->
            <div *ngIf="busViewMode === 'list'">
              <div class="d-flex justify-content-between align-items-center mb-4">
                <div>
                  <h4 class="fw-bold text-dark mb-1"><i class="fa-solid fa-bus text-primary me-2"></i>Registered Fleet</h4>
                  <p class="text-muted small mb-0">Manage operator buses and seating layout templates</p>
                </div>
                <button class="btn btn-primary rounded-pill fw-bold" (click)="startAddBusWizard()">
                  <i class="fa-solid fa-plus me-1"></i> Add New Bus
                </button>
              </div>

              <div class="row g-3">
                <div *ngFor="let bus of myBuses" class="col-md-6">
                  <div class="card p-3 shadow-sm border-start border-4 border-primary">
                    <div class="d-flex justify-content-between align-items-start">
                      <div>
                        <h5 class="fw-bold mb-1">{{ bus.operatorName }}</h5>
                        <div class="mb-2">
                          <span class="badge bg-secondary me-1">{{ bus.busType }}</span>
                          <span *ngIf="bus.facilityType" class="badge bg-info text-dark me-1">{{ bus.facilityType }}</span>
                          <span *ngIf="bus.templateId" class="badge bg-primary">{{ bus.templateId }}</span>
                          <span class="badge" [class.bg-success]="bus.status === 'ACTIVE'" [class.bg-danger]="bus.status === 'INACTIVE'">
                            {{ bus.status || 'ACTIVE' }}
                          </span>
                        </div>
                        <p class="mb-1 text-muted small"><i class="fa-solid fa-id-card me-1"></i> Bus Reg: <strong>{{ bus.busNumber }}</strong></p>
                        <p class="mb-1 text-muted small"><i class="fa-solid fa-chair me-1"></i> Total Capacity: {{ bus.totalSeats }} Seats</p>
                        <p class="mb-0 text-primary small"><i class="fa-solid fa-wifi me-1"></i> {{ bus.amenities }}</p>
                      </div>
                      <button class="btn btn-outline-success btn-sm rounded-pill" (click)="openAddScheduleModal(bus)">
                        + Schedule Trip
                      </button>
                    </div>
                  </div>
                </div>
                <div *ngIf="myBuses.length === 0" class="col-12 text-center py-5">
                  <p class="text-muted">No buses registered yet. Click "Add New Bus" to get started.</p>
                </div>
              </div>
            </div>

            <!-- SUBVIEW 2: Add Bus Wizard -->
            <div *ngIf="busViewMode === 'wizard'">
              
              <!-- Back to My Buses navigation link -->
              <div class="mb-3">
                <a class="text-primary fw-semibold text-decoration-none d-inline-flex align-items-center gap-2 cursor-pointer"
                   (click)="cancelAddBusWizard()"
                   role="button">
                  <i class="fa-solid fa-arrow-left"></i> Back to My Buses
                </a>
              </div>

              <!-- Page Heading -->
              <div class="mb-4">
                <h2 class="fw-bold text-dark mb-1">Register New Bus</h2>
                <p class="text-muted mb-0">Add a new bus to your fleet. Fill in the details and configure the seat layout.</p>
              </div>

              <!-- 4-Step Progress Indicator -->
              <div class="card p-3 mb-4 rounded-4 border-0 shadow-sm add-bus-stepper-card">
                <div class="d-flex align-items-center justify-content-between px-md-3">
                  <!-- Step 1: Basic Details -->
                  <div class="d-flex align-items-center gap-2">
                    <div class="add-bus-step-circle"
                         [class.active]="addBusWizardStep === 1"
                         [class.completed]="addBusWizardStep > 1">
                      <i *ngIf="addBusWizardStep > 1" class="fa-solid fa-check"></i>
                      <span *ngIf="addBusWizardStep <= 1">1</span>
                    </div>
                    <div class="add-bus-step-label fw-bold"
                         [class.text-primary]="addBusWizardStep === 1"
                         [class.text-dark]="addBusWizardStep > 1"
                         [class.text-muted]="addBusWizardStep < 1">
                      Basic Details
                    </div>
                  </div>

                  <div class="add-bus-step-line flex-grow-1 mx-3" [class.completed]="addBusWizardStep > 1"></div>

                  <!-- Step 2: Seat Layout -->
                  <div class="d-flex align-items-center gap-2">
                    <div class="add-bus-step-circle"
                         [class.active]="addBusWizardStep === 2"
                         [class.completed]="addBusWizardStep > 2"
                         [class.upcoming]="addBusWizardStep < 2">
                      <i *ngIf="addBusWizardStep > 2" class="fa-solid fa-check"></i>
                      <span *ngIf="addBusWizardStep <= 2">2</span>
                    </div>
                    <div class="add-bus-step-label"
                         [class.fw-bold]="addBusWizardStep === 2"
                         [class.text-primary]="addBusWizardStep === 2"
                         [class.text-dark]="addBusWizardStep > 2"
                         [class.text-muted]="addBusWizardStep < 2">
                      Seat Layout
                    </div>
                  </div>

                  <div class="add-bus-step-line flex-grow-1 mx-3" [class.completed]="addBusWizardStep > 2"></div>

                  <!-- Step 3: Documents -->
                  <div class="d-flex align-items-center gap-2">
                    <div class="add-bus-step-circle"
                         [class.active]="addBusWizardStep === 3"
                         [class.completed]="addBusWizardStep > 3"
                         [class.upcoming]="addBusWizardStep < 3">
                      <i *ngIf="addBusWizardStep > 3" class="fa-solid fa-check"></i>
                      <span *ngIf="addBusWizardStep <= 3">3</span>
                    </div>
                    <div class="add-bus-step-label"
                         [class.fw-bold]="addBusWizardStep === 3"
                         [class.text-primary]="addBusWizardStep === 3"
                         [class.text-dark]="addBusWizardStep > 3"
                         [class.text-muted]="addBusWizardStep < 3">
                      Documents
                    </div>
                  </div>

                  <div class="add-bus-step-line flex-grow-1 mx-3" [class.completed]="addBusWizardStep > 3"></div>

                  <!-- Step 4: Review & Submit -->
                  <div class="d-flex align-items-center gap-2">
                    <div class="add-bus-step-circle"
                         [class.active]="addBusWizardStep === 4"
                         [class.upcoming]="addBusWizardStep < 4">
                      <span>4</span>
                    </div>
                    <div class="add-bus-step-label"
                         [class.fw-bold]="addBusWizardStep === 4"
                         [class.text-primary]="addBusWizardStep === 4"
                         [class.text-muted]="addBusWizardStep < 4">
                      Review & Submit
                    </div>
                  </div>
                </div>
              </div>

              <!-- ==================== STEP 1: BASIC DETAILS ==================== -->
              <div *ngIf="addBusWizardStep === 1">
                <div class="row g-4 mb-4">
                  
                  <!-- Left Column: Basic Details Form -->
                  <div class="col-lg-7 col-xl-7">
                    <div class="card p-4 rounded-4 border-0 shadow-sm h-100 add-bus-form-card">
                      
                      <!-- Section Header -->
                      <div class="d-flex align-items-center gap-3 mb-4 pb-3 border-bottom">
                        <div class="add-bus-icon-box bg-primary bg-opacity-10 text-primary rounded-3 d-flex align-items-center justify-content-center">
                          <i class="fa-solid fa-bus"></i>
                        </div>
                        <div>
                          <h5 class="fw-bold text-dark mb-0">Basic Details</h5>
                          <small class="text-muted">Enter the registration and technical details of the bus.</small>
                        </div>
                      </div>

                      <div class="row g-3">
                        
                        <!-- 1. Registration Number -->
                        <div class="col-md-6">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Registration Number <span class="text-danger">*</span>
                          </label>
                          <div class="position-relative">
                            <input type="text"
                                   class="form-control text-uppercase"
                                   [class.is-invalid]="busFormErrors['busNumber']"
                                   [class.is-valid]="registrationAvailabilityStatus === 'available'"
                                   placeholder="KA-01-AB-1234"
                                   [(ngModel)]="busForm.busNumber"
                                   (input)="onRegistrationNumberInput()"
                                   (blur)="onRegistrationNumberBlur()">
                            <span *ngIf="isCheckingRegistration" class="position-absolute end-0 top-50 translate-middle-y me-3 spinner-border spinner-border-sm text-primary"></span>
                            <i *ngIf="!isCheckingRegistration && registrationAvailabilityStatus === 'available'" class="fa-solid fa-circle-check position-absolute end-0 top-50 translate-middle-y me-3 text-success"></i>
                          </div>
                          <div *ngIf="busFormErrors['busNumber']" class="text-danger small mt-1">
                            {{ busFormErrors['busNumber'] }}
                          </div>
                          <div *ngIf="!busFormErrors['busNumber'] && registrationAvailabilityStatus === 'available'" class="text-success small mt-1">
                            Registration number is valid and available.
                          </div>
                          <div *ngIf="!busFormErrors['busNumber'] && registrationAvailabilityStatus !== 'available'" class="text-muted small mt-1">
                            Format: KA01AB1234 or KA-01-AB-1234
                          </div>
                        </div>

                        <!-- 2. Bus Name (Optional) -->
                        <div class="col-md-6">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Bus Name <span class="text-muted fw-normal">(Optional)</span>
                          </label>
                          <input type="text"
                                 class="form-control"
                                 [class.is-invalid]="busFormErrors['busName']"
                                 placeholder="TravelEasy Express"
                                 [(ngModel)]="busForm.busName"
                                 maxlength="50"
                                 (input)="onFieldChange('busName')">
                          <div *ngIf="busFormErrors['busName']" class="text-danger small mt-1">
                            {{ busFormErrors['busName'] }}
                          </div>
                          <div *ngIf="!busFormErrors['busName']" class="text-muted small mt-1">
                            A display name to identify this bus
                          </div>
                        </div>

                        <!-- 3. Manufacturer -->
                        <div class="col-md-6">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Manufacturer <span class="text-danger">*</span>
                          </label>
                          <select class="form-select"
                                  [class.is-invalid]="busFormErrors['manufacturer']"
                                  [(ngModel)]="busForm.manufacturer"
                                  (change)="onFieldChange('manufacturer')">
                            <option value="" disabled>Select Manufacturer</option>
                            <option *ngFor="let m of manufacturerCatalog" [value]="m">{{ m }}</option>
                          </select>
                          <div *ngIf="busFormErrors['manufacturer']" class="text-danger small mt-1">
                            {{ busFormErrors['manufacturer'] }}
                          </div>
                        </div>

                        <!-- 4. Model -->
                        <div class="col-md-6">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Model <span class="text-danger">*</span>
                          </label>
                          <input type="text"
                                 class="form-control"
                                 [class.is-invalid]="busFormErrors['model']"
                                 placeholder="Volvo 9600"
                                 [(ngModel)]="busForm.model"
                                 maxlength="60"
                                 (input)="onFieldChange('model')">
                          <div *ngIf="busFormErrors['model']" class="text-danger small mt-1">
                            {{ busFormErrors['model'] }}
                          </div>
                          <div *ngIf="!busFormErrors['model']" class="text-muted small mt-1">
                            Enter the model name (e.g., 9600, 9400, etc.)
                          </div>
                        </div>

                        <!-- 5. Bus Type -->
                        <div class="col-md-6">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Bus Type <span class="text-danger">*</span>
                          </label>
                          <select class="form-select"
                                  [class.is-invalid]="busFormErrors['busType']"
                                  [(ngModel)]="busForm.busType"
                                  (change)="onFieldChange('busType')">
                            <option value="" disabled>Select Bus Type</option>
                            <option *ngFor="let bt of busTypeCatalog" [value]="bt">{{ bt }}</option>
                          </select>
                          <div *ngIf="busFormErrors['busType']" class="text-danger small mt-1">
                            {{ busFormErrors['busType'] }}
                          </div>
                        </div>

                        <!-- 6. Fuel Type -->
                        <div class="col-md-6">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Fuel Type <span class="text-danger">*</span>
                          </label>
                          <select class="form-select"
                                  [class.is-invalid]="busFormErrors['fuelType']"
                                  [(ngModel)]="busForm.fuelType"
                                  (change)="onFieldChange('fuelType')">
                            <option value="" disabled>Select Fuel Type</option>
                            <option *ngFor="let ft of fuelTypeCatalog" [value]="ft">{{ ft }}</option>
                          </select>
                          <div *ngIf="busFormErrors['fuelType']" class="text-danger small mt-1">
                            {{ busFormErrors['fuelType'] }}
                          </div>
                        </div>

                        <!-- 7. Chassis Number -->
                        <div class="col-md-6">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Chassis Number <span class="text-danger">*</span>
                          </label>
                          <input type="text"
                                 class="form-control text-uppercase"
                                 [class.is-invalid]="busFormErrors['chassisNumber']"
                                 placeholder="YV3T2EU527N1234567"
                                 [(ngModel)]="busForm.chassisNumber"
                                 maxlength="17"
                                 (input)="onChassisInput()"
                                 (blur)="onChassisBlur()">
                          <div *ngIf="busFormErrors['chassisNumber']" class="text-danger small mt-1">
                            {{ busFormErrors['chassisNumber'] }}
                          </div>
                          <div *ngIf="!busFormErrors['chassisNumber']" class="text-muted small mt-1">
                            17 characters (exclude I, O, Q)
                          </div>
                        </div>

                        <!-- 8. Year of Manufacturing -->
                        <div class="col-md-6">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Year of Manufacturing <span class="text-danger">*</span>
                          </label>
                          <div class="input-group">
                            <input type="number"
                                   class="form-control"
                                   [class.is-invalid]="busFormErrors['yearOfManufacturing']"
                                   placeholder="2022"
                                   [(ngModel)]="busForm.yearOfManufacturing"
                                   min="1980"
                                   [max]="currentCalendarYear"
                                   (input)="onFieldChange('yearOfManufacturing')">
                            <span class="input-group-text bg-white border-start-0 text-muted">
                              <i class="fa-solid fa-calendar"></i>
                            </span>
                          </div>
                          <div *ngIf="busFormErrors['yearOfManufacturing']" class="text-danger small mt-1">
                            {{ busFormErrors['yearOfManufacturing'] }}
                          </div>
                        </div>

                        <!-- 9. Engine Number -->
                        <div class="col-md-6">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Engine Number <span class="text-danger">*</span>
                          </label>
                          <input type="text"
                                 class="form-control text-uppercase"
                                 [class.is-invalid]="busFormErrors['engineNumber']"
                                 placeholder="D13K520123456"
                                 [(ngModel)]="busForm.engineNumber"
                                 maxlength="25"
                                 (input)="onEngineInput()"
                                 (blur)="onEngineBlur()">
                          <div *ngIf="busFormErrors['engineNumber']" class="text-danger small mt-1">
                            {{ busFormErrors['engineNumber'] }}
                          </div>
                          <div *ngIf="!busFormErrors['engineNumber']" class="text-muted small mt-1">
                            5–25 characters (letters, numbers, / and - allowed)
                          </div>
                        </div>

                        <!-- 10. Emission Standard -->
                        <div class="col-md-6">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Emission Standard <span class="text-danger">*</span>
                          </label>
                          <select class="form-select"
                                  [class.is-invalid]="busFormErrors['emissionStandard']"
                                  [(ngModel)]="busForm.emissionStandard"
                                  (change)="onFieldChange('emissionStandard')">
                            <option value="" disabled>Select Emission Standard</option>
                            <option *ngFor="let es of emissionStandardCatalog" [value]="es">{{ es }}</option>
                          </select>
                          <div *ngIf="busFormErrors['emissionStandard']" class="text-danger small mt-1">
                            {{ busFormErrors['emissionStandard'] }}
                          </div>
                        </div>

                        <!-- 11. Color -->
                        <div class="col-md-4">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Color <span class="text-danger">*</span>
                          </label>
                          <select class="form-select"
                                  [class.is-invalid]="busFormErrors['color']"
                                  [(ngModel)]="busForm.color"
                                  (change)="onFieldChange('color')">
                            <option value="" disabled>Select Color</option>
                            <option *ngFor="let c of colorCatalog" [value]="c">{{ c }}</option>
                          </select>
                          <div *ngIf="busFormErrors['color']" class="text-danger small mt-1">
                            {{ busFormErrors['color'] }}
                          </div>
                        </div>

                        <!-- 12. Bus Category -->
                        <div class="col-md-4">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Bus Category <span class="text-danger">*</span>
                          </label>
                          <select class="form-select"
                                  [class.is-invalid]="busFormErrors['busCategory']"
                                  [(ngModel)]="busForm.busCategory"
                                  (change)="onFieldChange('busCategory')">
                            <option value="" disabled>Select Bus Category</option>
                            <option *ngFor="let cat of busCategoryCatalog" [value]="cat">{{ cat }}</option>
                          </select>
                          <div *ngIf="busFormErrors['busCategory']" class="text-danger small mt-1">
                            {{ busFormErrors['busCategory'] }}
                          </div>
                        </div>

                        <!-- 13. Air Conditioning -->
                        <div class="col-md-4">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Air Conditioning <span class="text-danger">*</span>
                          </label>
                          <select class="form-select"
                                  [class.is-invalid]="busFormErrors['facilityType']"
                                  [(ngModel)]="busForm.facilityType"
                                  (change)="onFieldChange('facilityType')">
                            <option value="AC">Yes (AC)</option>
                            <option value="NON_AC">No (Non-AC)</option>
                          </select>
                          <div *ngIf="busFormErrors['facilityType']" class="text-danger small mt-1">
                            {{ busFormErrors['facilityType'] }}
                          </div>
                        </div>

                        <!-- 14. Amenities (Optional) -->
                        <div class="col-12 mt-4">
                          <label class="form-label fw-bold text-dark small mb-1">
                            Amenities <span class="text-muted fw-normal">(Optional)</span>
                          </label>
                          <div class="text-muted small mb-2">
                            Add amenities available in this bus. You can add multiple amenities.
                          </div>

                          <div class="input-group mb-2">
                            <span class="input-group-text bg-white border-end-0 text-muted">
                              <i class="fa-solid fa-magnifying-glass"></i>
                            </span>
                            <select class="form-select border-start-0"
                                    [(ngModel)]="selectedAmenityCandidate">
                              <option value="" disabled selected>Search and select amenity (e.g., WiFi, Charging Point, ...)</option>
                              <optgroup *ngFor="let group of groupedAmenities" [label]="group.category">
                                <option *ngFor="let item of group.items" [value]="item">{{ item }}</option>
                              </optgroup>
                            </select>
                            <button type="button"
                                    class="btn btn-primary px-3 fw-bold"
                                    (click)="addAmenity()">
                              + Add
                            </button>
                          </div>

                          <!-- Selected Amenity Chips -->
                          <div class="d-flex flex-wrap gap-2 mt-2">
                            <span *ngFor="let a of busForm.amenities"
                                  class="add-bus-amenity-chip">
                              <span>{{ a }}</span>
                              <i class="fa-solid fa-xmark chip-remove-btn"
                                 (click)="removeAmenity(a)"
                                 title="Remove amenity"></i>
                            </span>
                          </div>
                        </div>

                      </div>
                    </div>
                  </div>

                  <!-- Right Column: Bus Images -->
                  <div class="col-lg-5 col-xl-5">
                    <div class="card p-4 rounded-4 border-0 shadow-sm h-100 add-bus-images-card">
                      
                      <!-- Section Header -->
                      <div class="d-flex align-items-center gap-3 mb-4 pb-3 border-bottom">
                        <div class="add-bus-icon-box bg-primary bg-opacity-10 text-primary rounded-3 d-flex align-items-center justify-content-center">
                          <i class="fa-solid fa-image"></i>
                        </div>
                        <div>
                          <h5 class="fw-bold text-dark mb-0">
                            Bus Images <span class="text-danger small fw-semibold">(Required)</span>
                          </h5>
                          <small class="text-muted">Upload one exterior image and one interior image of this bus.</small>
                        </div>
                      </div>

                      <!-- Two Images Side-by-Side (Row) -->
                      <div class="row g-3">
                        
                        <!-- Image 1: Exterior Image -->
                        <div class="col-sm-6">
                          <label class="form-label fw-bold text-dark small mb-2">
                            Exterior Image <span class="text-danger">*</span>
                          </label>

                          <!-- Image Preview Box -->
                          <div *ngIf="busForm.exteriorImagePreview" class="add-bus-preview-box mb-2">
                            <img [src]="busForm.exteriorImagePreview" alt="Exterior Image" class="w-100 h-100 object-fit-cover rounded-3">
                            <button type="button"
                                    class="add-bus-delete-img-btn"
                                    (click)="removeBusImage('exterior')"
                                    title="Remove exterior image">
                              <i class="fa-solid fa-trash-can"></i>
                            </button>
                            <div *ngIf="isUploadingImage['exterior']" class="position-absolute top-0 start-0 w-100 h-100 bg-dark bg-opacity-50 d-flex align-items-center justify-content-center rounded-3">
                              <div class="spinner-border spinner-border-sm text-white" role="status"></div>
                            </div>
                          </div>

                          <!-- Dropzone if no image -->
                          <div *ngIf="!busForm.exteriorImagePreview"
                               class="add-bus-dropzone mb-2"
                               (click)="triggerFileInput('exterior')">
                            <i class="fa-solid fa-cloud-arrow-up text-primary fs-3 mb-2"></i>
                            <div class="fw-bold text-dark small">Upload Exterior</div>
                            <small class="text-muted" style="font-size: 11px;">JPG, PNG, WebP</small>
                            <small class="text-muted" style="font-size: 11px;">Max 1.5 MB</small>
                            <div *ngIf="isUploadingImage['exterior']" class="mt-2 spinner-border spinner-border-sm text-primary"></div>
                          </div>

                          <!-- Replace Button -->
                          <button type="button"
                                  class="add-bus-replace-btn"
                                  (click)="triggerFileInput('exterior')">
                            <div class="d-flex align-items-center justify-content-center gap-1 fw-bold small">
                              <i class="fa-solid fa-arrow-up-from-bracket"></i> Replace Exterior Image
                            </div>
                            <small class="text-muted d-block" style="font-size: 10.5px;">JPG, JPEG, PNG, WebP &bull; Max size: 1.5 MB</small>
                          </button>

                          <input id="exteriorImageInput"
                                 type="file"
                                 accept="image/jpeg,image/png,image/webp"
                                 class="d-none"
                                 (change)="onFileSelected($event, 'exterior')">

                          <div *ngIf="uploadImageError['exterior']" class="text-danger small mt-1">
                            {{ uploadImageError['exterior'] }}
                          </div>
                          <div *ngIf="busFormErrors['exteriorImage']" class="text-danger small mt-1">
                            {{ busFormErrors['exteriorImage'] }}
                          </div>
                        </div>

                        <!-- Image 2: Interior Image -->
                        <div class="col-sm-6">
                          <label class="form-label fw-bold text-dark small mb-2">
                            Interior Image <span class="text-danger">*</span>
                          </label>

                          <!-- Image Preview Box -->
                          <div *ngIf="busForm.interiorImagePreview" class="add-bus-preview-box mb-2">
                            <img [src]="busForm.interiorImagePreview" alt="Interior Image" class="w-100 h-100 object-fit-cover rounded-3">
                            <button type="button"
                                    class="add-bus-delete-img-btn"
                                    (click)="removeBusImage('interior')"
                                    title="Remove interior image">
                              <i class="fa-solid fa-trash-can"></i>
                            </button>
                            <div *ngIf="isUploadingImage['interior']" class="position-absolute top-0 start-0 w-100 h-100 bg-dark bg-opacity-50 d-flex align-items-center justify-content-center rounded-3">
                              <div class="spinner-border spinner-border-sm text-white" role="status"></div>
                            </div>
                          </div>

                          <!-- Dropzone if no image -->
                          <div *ngIf="!busForm.interiorImagePreview"
                               class="add-bus-dropzone mb-2"
                               (click)="triggerFileInput('interior')">
                            <i class="fa-solid fa-cloud-arrow-up text-primary fs-3 mb-2"></i>
                            <div class="fw-bold text-dark small">Upload Interior</div>
                            <small class="text-muted" style="font-size: 11px;">JPG, PNG, WebP</small>
                            <small class="text-muted" style="font-size: 11px;">Max 1.5 MB</small>
                            <div *ngIf="isUploadingImage['interior']" class="mt-2 spinner-border spinner-border-sm text-primary"></div>
                          </div>

                          <!-- Replace Button -->
                          <button type="button"
                                  class="add-bus-replace-btn"
                                  (click)="triggerFileInput('interior')">
                            <div class="d-flex align-items-center justify-content-center gap-1 fw-bold small">
                              <i class="fa-solid fa-arrow-up-from-bracket"></i> Replace Interior Image
                            </div>
                            <small class="text-muted d-block" style="font-size: 10.5px;">JPG, JPEG, PNG, WebP &bull; Max size: 1.5 MB</small>
                          </button>

                          <input id="interiorImageInput"
                                 type="file"
                                 accept="image/jpeg,image/png,image/webp"
                                 class="d-none"
                                 (change)="onFileSelected($event, 'interior')">

                          <div *ngIf="uploadImageError['interior']" class="text-danger small mt-1">
                            {{ uploadImageError['interior'] }}
                          </div>
                          <div *ngIf="busFormErrors['interiorImage']" class="text-danger small mt-1">
                            {{ busFormErrors['interiorImage'] }}
                          </div>
                        </div>

                      </div>

                      <!-- Image Guidelines Alert Box -->
                      <div class="add-bus-guidelines-box mt-4 p-3 rounded-3 d-flex align-items-start gap-3">
                        <div class="text-primary fs-5 mt-1">
                          <i class="fa-solid fa-circle-info"></i>
                        </div>
                        <div>
                          <div class="fw-bold text-dark small mb-1">Image Guidelines</div>
                          <ul class="mb-0 ps-3 small text-muted" style="line-height: 1.6;">
                            <li>Both exterior and interior images are required.</li>
                            <li>Supported formats: JPG, JPEG, PNG, WebP.</li>
                            <li>Maximum file size: less than 1.5 MB per image.</li>
                            <li>Upload clear and well-lit images.</li>
                          </ul>
                        </div>
                      </div>

                    </div>
                  </div>

                </div>

                <!-- Bottom Navigation Bar -->
                <div class="d-flex justify-content-between align-items-center pt-3 mt-4 border-top">
                  <button type="button"
                          class="btn btn-outline-secondary px-4 py-2 rounded-3 fw-bold"
                          (click)="cancelAddBusWizard()">
                    Cancel
                  </button>
                  <button type="button"
                          class="btn btn-primary px-4 py-2 rounded-3 fw-bold d-flex align-items-center gap-2 shadow-sm"
                          (click)="nextStepToSeatLayout()">
                    <span>Next: Seat Layout</span>
                    <i class="fa-solid fa-arrow-right"></i>
                  </button>
                </div>
              </div>

              <!-- ==================== STEP 2: SEAT LAYOUT PLACEHOLDER ==================== -->
              <div *ngIf="addBusWizardStep === 2" class="card p-5 text-center rounded-4 border-0 shadow-sm">
                <div class="mb-3">
                  <div class="rounded-circle bg-primary bg-opacity-10 text-primary d-inline-flex align-items-center justify-content-center" style="width: 72px; height: 72px; font-size: 32px;">
                    <i class="fa-solid fa-couch"></i>
                  </div>
                </div>
                <h4 class="fw-bold text-dark mb-2">Step 2: Seat Layout Configuration</h4>
                <p class="text-muted mx-auto" style="max-width: 580px;">
                  Basic details for <strong>{{ busForm.busNumber }}</strong> ({{ busForm.manufacturer }} {{ busForm.model }}) have been validated and saved in the wizard session. Physical seat template assignment will be configured in this step.
                </p>
                <div class="d-flex justify-content-center gap-3 mt-4">
                  <button type="button" class="btn btn-outline-primary px-4 py-2 rounded-3 fw-bold" (click)="addBusWizardStep = 1">
                    <i class="fa-solid fa-arrow-left me-1"></i> Back to Basic Details
                  </button>
                </div>
              </div>

              <!-- Cancel Add Bus Confirmation Modal -->
              <div *ngIf="showCancelAddBusModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
                <div class="modal-dialog modal-dialog-centered">
                  <div class="modal-content rounded-4 border-0 shadow">
                    <div class="modal-header border-bottom px-4 py-3">
                      <h5 class="modal-title fw-bold text-dark d-flex align-items-center gap-2">
                        <i class="fa-solid fa-triangle-exclamation text-warning"></i>
                        <span>Discard Bus Details?</span>
                      </h5>
                      <button type="button" class="btn-close" (click)="showCancelAddBusModal = false"></button>
                    </div>
                    <div class="modal-body p-4">
                      <p class="text-muted mb-0">
                        You have unsaved changes in this bus registration form. If you leave now, all entered details and uploaded images will be discarded. Are you sure you want to exit?
                      </p>
                    </div>
                    <div class="modal-footer border-top px-4 py-3 bg-light rounded-bottom-4">
                      <button type="button" class="btn btn-outline-secondary px-4 py-2 rounded-3 fw-semibold" (click)="showCancelAddBusModal = false">
                        Continue Editing
                      </button>
                      <button type="button" class="btn btn-danger px-4 py-2 rounded-3 fw-bold" (click)="confirmCancelAddBusWizard()">
                        Discard & Exit
                      </button>
                    </div>
                  </div>
                </div>
              </div>

            </div>

          </div>

          <!-- ==================== VIEW 3: REUSABLE ROUTES ==================== -->
          <div *ngIf="activeNav === 'routes'">
            <!-- SUBVIEW 1: Routes Listing (Phase 5.2) -->
            <div *ngIf="routeViewMode === 'list'">
              <!-- Page Header -->
              <div class="d-flex flex-wrap justify-content-between align-items-start gap-3 mb-4">
              <div>
                <h2 class="fw-bold text-dark mb-1">Routes</h2>
                <p class="text-muted small mb-0">
                  Define and manage the journeys offered by your buses. Each route contains an ordered list of stops with boarding and dropping points.
                </p>
              </div>
              <button class="btn btn-primary rounded-3 px-3 py-2 fw-bold d-flex align-items-center gap-2 shadow-sm" (click)="startCreateRouteWizard()">
                <i class="fa-solid fa-plus"></i> Create Route
              </button>
            </div>

            <!-- 4 Summary Cards -->
            <div class="row g-3 g-xl-4 mb-4">
              <!-- Card 1: Total Routes -->
              <div class="col-xl-3 col-sm-6">
                <div class="metric-card h-100">
                  <div class="metric-icon-box" style="background-color: #e0f2fe; color: #0284c7;">
                    <i class="fa-solid fa-route"></i>
                  </div>
                  <div>
                    <div class="metric-label">Total Routes</div>
                    <div class="metric-value">{{ myRoutes.length }}</div>
                  </div>
                </div>
              </div>

              <!-- Card 2: Active Routes -->
              <div class="col-xl-3 col-sm-6">
                <div class="metric-card h-100">
                  <div class="metric-icon-box" style="background-color: #dcfce7; color: #16a34a;">
                    <i class="fa-solid fa-circle"></i>
                  </div>
                  <div>
                    <div class="metric-label">Active Routes</div>
                    <div class="metric-value">{{ getActiveRoutesCount() }}</div>
                  </div>
                </div>
              </div>

              <!-- Card 3: Draft Routes -->
              <div class="col-xl-3 col-sm-6">
                <div class="metric-card h-100">
                  <div class="metric-icon-box" style="background-color: #fef3c7; color: #d97706;">
                    <i class="fa-solid fa-clock"></i>
                  </div>
                  <div>
                    <div class="metric-label">Draft Routes</div>
                    <div class="metric-value">{{ getDraftRoutesCount() }}</div>
                  </div>
                </div>
              </div>

              <!-- Card 4: Inactive Routes -->
              <div class="col-xl-3 col-sm-6">
                <div class="metric-card h-100">
                  <div class="metric-icon-box" style="background-color: #f1f5f9; color: #64748b;">
                    <i class="fa-solid fa-circle-pause"></i>
                  </div>
                  <div>
                    <div class="metric-label">Inactive Routes</div>
                    <div class="metric-value">{{ getInactiveRoutesCount() }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Search and Filter Bar -->
            <div class="filter-bar-card mb-4">
              <div class="row g-3 align-items-start">
                <!-- 1. Search Input -->
                <div class="col-lg-4 col-md-6 col-12">
                  <div class="filter-field">
                    <label class="filter-label">Search</label>
                    <div class="input-group search-input-group">
                      <span class="input-group-text bg-white border-end-0 text-muted">
                        <i class="fa-solid fa-magnifying-glass"></i>
                      </span>
                      <input type="text"
                             class="form-control border-start-0 ps-0 search-control"
                             placeholder="Search route name, ID, origin, destination..."
                             [(ngModel)]="routeSearchQuery"
                             (input)="onRouteFilterChange()">
                    </div>
                  </div>
                </div>

                <!-- 2. Status Filter -->
                <div class="col-lg-2 col-md-3 col-6">
                  <div class="filter-field">
                    <label class="filter-label">Status</label>
                    <select class="form-select filter-select" [(ngModel)]="routeStatusFilter" (change)="onRouteFilterChange()">
                      <option value="ALL">All Statuses</option>
                      <option value="ACTIVE">Active</option>
                      <option value="DRAFT">Draft</option>
                      <option value="INACTIVE">Inactive</option>
                    </select>
                  </div>
                </div>

                <!-- 3. Origin Filter -->
                <div class="col-lg-2 col-md-3 col-6">
                  <div class="filter-field">
                    <label class="filter-label">Origin</label>
                    <select class="form-select filter-select" [(ngModel)]="routeOriginFilter" (change)="onRouteFilterChange()">
                      <option value="ALL">All Cities</option>
                      <option *ngFor="let o of distinctOrigins" [value]="o">{{ o }}</option>
                    </select>
                  </div>
                </div>

                <!-- 4. Destination Filter -->
                <div class="col-lg-2 col-md-3 col-6">
                  <div class="filter-field">
                    <label class="filter-label">Destination</label>
                    <select class="form-select filter-select" [(ngModel)]="routeDestinationFilter" (change)="onRouteFilterChange()">
                      <option value="ALL">All Cities</option>
                      <option *ngFor="let d of distinctDestinations" [value]="d">{{ d }}</option>
                    </select>
                  </div>
                </div>

                <!-- 5. More Filters Toggle -->
                <div class="col-lg-2 col-md-3 col-6">
                  <div class="filter-field">
                    <label class="filter-label invisible d-none d-md-block">&nbsp;</label>
                    <button class="btn more-filters-btn w-100"
                            [class.active]="showMoreFilters || isAdvancedFilterActive()"
                            (click)="toggleMoreFilters()"
                            type="button">
                      <i class="fa-solid fa-sliders"></i>
                      <span>More Filters</span>
                      <span *ngIf="getActiveAdvancedFilterCount() > 0" class="badge rounded-pill bg-primary text-white filter-badge">
                        {{ getActiveAdvancedFilterCount() }}
                      </span>
                      <i class="fa-solid" [ngClass]="showMoreFilters ? 'fa-chevron-up' : 'fa-chevron-down'" style="font-size: 10px;"></i>
                    </button>
                  </div>
                </div>
              </div>

              <!-- Expandable More Filters Tray -->
              <div *ngIf="showMoreFilters" class="more-filters-tray mt-3 pt-3 border-top">
                <div class="row g-3">
                  <!-- Stops Count Filter -->
                  <div class="col-lg-3 col-md-6 col-12">
                    <div class="filter-field">
                      <label class="filter-label">Stops Count</label>
                      <select class="form-select filter-select" [(ngModel)]="routeStopsFilter" (change)="onRouteFilterChange()">
                        <option value="ALL">All Stops</option>
                        <option value="DIRECT">Direct (2 stops only)</option>
                        <option value="1-2">1 - 2 Intermediate (3-4 stops)</option>
                        <option value="3+">3+ Intermediate (5+ stops)</option>
                      </select>
                    </div>
                  </div>

                  <!-- Distance Filter -->
                  <div class="col-lg-3 col-md-6 col-12">
                    <div class="filter-field">
                      <label class="filter-label">Distance Range</label>
                      <select class="form-select filter-select" [(ngModel)]="routeDistanceFilter" (change)="onRouteFilterChange()">
                        <option value="ALL">All Distances</option>
                        <option value="SHORT">&lt; 200 km (Short Haul)</option>
                        <option value="MEDIUM">200 - 450 km (Regional)</option>
                        <option value="LONG">&gt; 450 km (Intercity)</option>
                      </select>
                    </div>
                  </div>

                  <!-- Schedule Activity Filter -->
                  <div class="col-lg-3 col-md-6 col-12">
                    <div class="filter-field">
                      <label class="filter-label">Scheduled Departures</label>
                      <select class="form-select filter-select" [(ngModel)]="routeScheduleActivityFilter" (change)="onRouteFilterChange()">
                        <option value="ALL">All Routes</option>
                        <option value="WITH_SCHEDS">With Active Trips (&gt; 0)</option>
                        <option value="NO_SCHEDS">No Trips Yet (0)</option>
                      </select>
                    </div>
                  </div>

                  <!-- Sort Ordering -->
                  <div class="col-lg-3 col-md-6 col-12">
                    <div class="filter-field">
                      <label class="filter-label">Sort Order</label>
                      <select class="form-select filter-select" [(ngModel)]="routeSortBy" (change)="onRouteFilterChange()">
                        <option value="CODE_ASC">Route Code (Ascending)</option>
                        <option value="NAME_ASC">Route Name (A &rarr; Z)</option>
                        <option value="DIST_DESC">Distance (High to Low)</option>
                        <option value="DIST_ASC">Distance (Low to High)</option>
                        <option value="SCHED_DESC">Schedules (Most to Least)</option>
                      </select>
                    </div>
                  </div>
                </div>

                <!-- Tray Actions -->
                <div class="d-flex align-items-center justify-content-between mt-3 pt-2">
                  <div class="small">
                    <span *ngIf="isAdvancedFilterActive()" class="text-primary fw-semibold">
                      <i class="fa-solid fa-circle-check me-1"></i>{{ getActiveAdvancedFilterCount() }} advanced filter(s) active
                    </span>
                    <span *ngIf="!isAdvancedFilterActive()" class="text-muted">
                      Configure secondary criteria to refine your route catalogue.
                    </span>
                  </div>
                  <div class="d-flex gap-2">
                    <button class="btn btn-outline-secondary btn-sm px-3 rounded-pill" (click)="resetRouteFilters()" type="button">
                      <i class="fa-solid fa-arrow-rotate-left me-1"></i>Reset All
                    </button>
                    <button class="btn btn-primary btn-sm px-3 rounded-pill fw-semibold" (click)="showMoreFilters = false" type="button">
                      Apply & Close
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <!-- Loading State -->
            <div *ngIf="isLoadingRoutes" class="text-center py-5 bg-white rounded-4 border mb-4">
              <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">Loading routes...</span>
              </div>
              <p class="text-muted small mt-2 mb-0">Loading your route catalogue...</p>
            </div>

            <!-- Error State -->
            <div *ngIf="routeLoadError && !isLoadingRoutes" class="alert alert-danger d-flex align-items-center justify-content-between rounded-3 mb-4">
              <div>
                <i class="fa-solid fa-triangle-exclamation me-2"></i>
                <span>{{ routeLoadError }}</span>
              </div>
              <button class="btn btn-sm btn-outline-danger" (click)="loadRoutes()">Retry</button>
            </div>

            <!-- Routes Table Card -->
            <div *ngIf="!isLoadingRoutes" class="card border-0 shadow-sm rounded-4 mb-4 routes-table-card">
              <div class="table-responsive routes-table-container">
                <table class="table routes-table mb-0 align-middle">
                  <thead>
                    <tr>
                      <th style="width: 110px;">Route ID</th>
                      <th>Route Name</th>
                      <th>Origin &rarr; Destination</th>
                      <th class="text-center" style="width: 70px;">Stops</th>
                      <th style="width: 100px;">Distance</th>
                      <th style="width: 110px;">Est. Duration</th>
                      <th class="text-center" style="width: 120px;">Associated Schedules</th>
                      <th style="width: 110px;">Status</th>
                      <th class="text-center" style="width: 60px;">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let r of paginatedRoutes">
                      <!-- 1. Route ID -->
                      <td>
                        <span class="badge-route-id">{{ r.routeCode }}</span>
                      </td>

                      <!-- 2. Route Name -->
                      <td>
                        <span class="fw-bold text-dark">{{ r.routeName }}</span>
                      </td>

                      <!-- 3. Origin -> Destination -->
                      <td>
                        <div class="d-flex align-items-center">
                          <i class="fa-solid fa-location-dot text-primary me-2"></i>
                          <span class="fw-semibold text-dark">{{ r.origin }}</span>
                          <i class="fa-solid fa-arrow-right text-muted mx-2 small"></i>
                          <i class="fa-solid fa-location-dot text-danger me-2"></i>
                          <span class="fw-semibold text-dark">{{ r.destination }}</span>
                        </div>
                      </td>

                      <!-- 4. Stops -->
                      <td class="text-center">
                        <span class="text-muted fw-semibold">{{ formatRouteStopsCount(r) }}</span>
                      </td>

                      <!-- 5. Distance -->
                      <td>
                        <span class="text-muted">{{ formatRouteDistance(r) }}</span>
                      </td>

                      <!-- 6. Est. Duration -->
                      <td>
                        <span class="text-muted">{{ formatRouteDuration(r) }}</span>
                      </td>

                      <!-- 7. Associated Schedules -->
                      <td class="text-center">
                        <span class="badge bg-light text-dark border px-2 py-1">{{ r.scheduleCount != null ? r.scheduleCount : '—' }}</span>
                      </td>

                      <!-- 8. Status -->
                      <td>
                        <span *ngIf="r.status === 'ACTIVE'" class="badge-status-active">
                          <i class="fa-solid fa-circle fa-2xs me-1"></i>Active
                        </span>
                        <span *ngIf="r.status === 'DRAFT'" class="badge-status-draft">
                          <i class="fa-solid fa-circle fa-2xs me-1"></i>Draft
                        </span>
                        <span *ngIf="r.status === 'INACTIVE'" class="badge-status-inactive">
                          <i class="fa-solid fa-circle fa-2xs me-1"></i>Inactive
                        </span>
                      </td>

                      <!-- 9. Actions -->
                      <td class="text-center position-relative">
                        <button class="btn btn-sm btn-light border-0 rounded-circle text-muted p-2 action-trigger-btn"
                                [class.active]="activeActionMenuRouteId === r.id"
                                (click)="toggleActionMenu(r.id, $event)"
                                title="Actions for {{ r.routeCode }}">
                          <i class="fa-solid fa-ellipsis-vertical"></i>
                        </button>

                        <!-- Action Dropdown Menu -->
                        <div *ngIf="activeActionMenuRouteId === r.id"
                             class="route-action-menu text-start shadow"
                             [class.dropup-menu]="shouldUseDropup(idx, paginatedRoutes.length)">
                          <button class="action-menu-btn" (click)="viewRouteDetails(r)">
                            <i class="fa-solid fa-eye text-primary"></i>
                            <span>View Details</span>
                          </button>
                          <button class="action-menu-btn" (click)="openEditRouteModal(r); closeActionMenu()">
                            <i class="fa-solid fa-pen-to-square text-secondary"></i>
                            <span>Edit Route</span>
                          </button>
                          <button class="action-menu-btn"
                                  [disabled]="r.status === 'INACTIVE'"
                                  (click)="openAddScheduleModal(null, r); closeActionMenu()">
                            <i class="fa-solid fa-calendar-plus text-success"></i>
                            <span>Schedule Trip</span>
                          </button>
                          <div class="action-menu-divider"></div>
                          <button *ngIf="r.status === 'ACTIVE'"
                                  class="action-menu-btn danger"
                                  (click)="toggleRouteStatus(r); closeActionMenu()">
                            <i class="fa-solid fa-ban text-danger"></i>
                            <span>Deactivate</span>
                          </button>
                          <button *ngIf="r.status !== 'ACTIVE'"
                                  class="action-menu-btn success"
                                  (click)="toggleRouteStatus(r); closeActionMenu()">
                            <i class="fa-solid fa-circle-check text-success"></i>
                            <span>Activate</span>
                          </button>
                        </div>
                      </td>
                    </tr>

                    <!-- Empty state: No matching routes after filter -->
                    <tr *ngIf="filteredRoutes.length === 0 && myRoutes.length > 0">
                      <td colspan="9" class="text-center py-5 text-muted">
                        <i class="fa-solid fa-filter-circle-xmark fs-2 text-muted mb-2 d-block"></i>
                        <p class="mb-2 fw-semibold">No routes match the selected search or filter criteria.</p>
                        <button class="btn btn-outline-primary btn-sm rounded-pill" (click)="resetRouteFilters()">
                          Clear All Filters
                        </button>
                      </td>
                    </tr>

                    <!-- Empty state: No routes created yet -->
                    <tr *ngIf="myRoutes.length === 0">
                      <td colspan="9" class="text-center py-5 text-muted">
                        <i class="fa-solid fa-route fs-2 text-muted mb-2 d-block"></i>
                        <h6 class="fw-bold text-dark mb-1">No Reusable Routes Defined</h6>
                        <p class="small text-muted mb-3">Define your first route to configure ordered stops, boarding and dropping points.</p>
                        <button class="btn btn-primary rounded-pill btn-sm px-3 fw-bold" (click)="startCreateRouteWizard()">
                          <i class="fa-solid fa-plus me-1"></i> + Create Route
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <!-- Pagination Footer -->
              <div *ngIf="filteredRoutes.length > 0" class="card-footer bg-white border-top py-3 px-4 d-flex flex-wrap align-items-center justify-content-between gap-3">
                <!-- Left: Showing X - Y of Z -->
                <div class="text-muted small">
                  Showing <strong>{{ getPaginationStart() }}</strong> - <strong>{{ getPaginationEnd() }}</strong> of <strong>{{ filteredRoutes.length }}</strong> routes
                </div>

                <!-- Right: Rows per page & Navigation Controls -->
                <div class="d-flex align-items-center gap-3">
                  <div class="d-flex align-items-center gap-2 text-muted small">
                    <span>Rows per page:</span>
                    <select class="form-select form-select-sm w-auto"
                            [(ngModel)]="routePageSize"
                            (change)="onPageSizeChange()">
                      <option [ngValue]="5">5</option>
                      <option [ngValue]="10">10</option>
                      <option [ngValue]="25">25</option>
                      <option [ngValue]="50">50</option>
                    </select>
                  </div>

                  <div class="d-flex align-items-center gap-1">
                    <button class="page-btn"
                            [disabled]="routeCurrentPage <= 1"
                            (click)="goToRoutePage(routeCurrentPage - 1)"
                            title="Previous Page">
                      <i class="fa-solid fa-angle-left"></i>
                    </button>

                    <button *ngFor="let page of routePageNumbers"
                            class="page-btn"
                            [class.active]="routeCurrentPage === page"
                            (click)="goToRoutePage(page)">
                      {{ page }}
                    </button>

                    <button class="page-btn"
                            [disabled]="routeCurrentPage >= routeTotalPages"
                            (click)="goToRoutePage(routeCurrentPage + 1)"
                            title="Next Page">
                      <i class="fa-solid fa-angle-right"></i>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

            <!-- SUBVIEW 2: Create Route Wizard (Phase 5.3) -->
            <div *ngIf="routeViewMode === 'create'" class="create-route-wizard">
              <!-- Back to Routes Link -->
              <div class="mb-3">
                <a class="text-decoration-none text-primary fw-semibold small d-inline-flex align-items-center gap-2 cursor-pointer back-to-routes-link"
                   (click)="cancelRouteCreation()">
                  <i class="fa-solid fa-arrow-left"></i>
                  <span>Back to Routes</span>
                </a>
              </div>

              <!-- Page Heading -->
              <div class="mb-4">
                <h2 class="fw-bold text-dark mb-1">Create New Route</h2>
                <p class="text-muted small mb-0">
                  <span *ngIf="routeWizardStep !== 4">Define the route details, add stops in order, and configure boarding and dropping points for each stop.</span>
                  <span *ngIf="routeWizardStep === 4">Review the route details before creating it. You can go back to make changes if needed.</span>
                </p>
              </div>

              <!-- 4-Step Progress Indicator -->
              <div class="wizard-progress-card mb-4">
                <div class="wizard-progress-bar">
                  <!-- Step 1: Route Details -->
                  <div class="wizard-step-item" [class.active]="routeWizardStep === 1" [class.completed]="routeWizardStep > 1">
                    <div class="wizard-step-circle" [ngClass]="routeWizardStep === 1 ? 'active' : (routeWizardStep > 1 ? 'completed' : 'inactive')">
                      <i *ngIf="routeWizardStep > 1" class="fa-solid fa-check"></i>
                      <span *ngIf="routeWizardStep <= 1">1</span>
                    </div>
                    <span class="wizard-step-title" [class.active]="routeWizardStep === 1">Route Details</span>
                  </div>

                  <!-- Connector 1-2 -->
                  <div class="wizard-step-line" [class.completed]="routeWizardStep > 1"></div>

                  <!-- Step 2: Add Stops -->
                  <div class="wizard-step-item" [class.active]="routeWizardStep === 2" [class.completed]="routeWizardStep > 2">
                    <div class="wizard-step-circle" [ngClass]="routeWizardStep === 2 ? 'active' : (routeWizardStep > 2 ? 'completed' : 'inactive')">
                      <i *ngIf="routeWizardStep > 2" class="fa-solid fa-check"></i>
                      <span *ngIf="routeWizardStep <= 2">2</span>
                    </div>
                    <span class="wizard-step-title" [class.active]="routeWizardStep === 2">Add Stops</span>
                  </div>

                  <!-- Connector 2-3 -->
                  <div class="wizard-step-line" [class.completed]="routeWizardStep > 2"></div>

                  <!-- Step 3: Configure Boarding & Dropping Points -->
                  <div class="wizard-step-item" [class.active]="routeWizardStep === 3" [class.completed]="routeWizardStep > 3">
                    <div class="wizard-step-circle" [ngClass]="routeWizardStep === 3 ? 'active' : (routeWizardStep > 3 ? 'completed' : 'inactive')">
                      <i *ngIf="routeWizardStep > 3" class="fa-solid fa-check"></i>
                      <span *ngIf="routeWizardStep <= 3">3</span>
                    </div>
                    <span class="wizard-step-title" [class.active]="routeWizardStep === 3">Configure Boarding & Dropping Points</span>
                  </div>

                  <!-- Connector 3-4 -->
                  <div class="wizard-step-line" [class.completed]="routeWizardStep >= 4"></div>

                  <!-- Step 4: Review & Create -->
                  <div class="wizard-step-item" [class.active]="routeWizardStep === 4">
                    <div class="wizard-step-circle" [ngClass]="routeWizardStep === 4 ? 'active' : 'inactive'">
                      <span>4</span>
                    </div>
                    <span class="wizard-step-title" [class.active]="routeWizardStep === 4">Review & Create</span>
                  </div>
                </div>
              </div>

              <!-- Top Validation Alert Banner if errors exist -->
              <div *ngIf="step1FormSubmitted && !isStep1Valid()" class="alert alert-danger d-flex align-items-center gap-2 rounded-3 mb-4 py-2 px-3 small">
                <i class="fa-solid fa-circle-exclamation text-danger fs-6"></i>
                <span>Please correct the highlighted fields below before proceeding to Add Stops.</span>
              </div>

              <!-- ==================== STEP 1 CONTENT ==================== -->
              <div *ngIf="routeWizardStep === 1">
                <!-- Card 1: Route Information -->
                <div class="wizard-card mb-4">
                  <div class="d-flex align-items-center gap-3 mb-4">
                    <div class="wizard-card-icon" style="background-color: #e0f2fe; color: #0284c7;">
                      <i class="fa-solid fa-route"></i>
                    </div>
                    <div>
                      <h5 class="fw-bold text-dark mb-0">Route Information</h5>
                      <div class="text-muted small">Provide a unique name and basic details for this route.</div>
                    </div>
                  </div>

                  <div class="row g-4">
                    <!-- Route Name -->
                    <div class="col-md-6">
                      <label class="form-label fw-bold small text-dark">
                        Route Name <span class="text-danger">*</span>
                      </label>
                      <input type="text"
                             class="form-control wizard-control"
                             [class.is-invalid]="step1FormSubmitted && routeNameError"
                             [(ngModel)]="wizardRouteName"
                             (input)="validateRouteName()"
                             placeholder="e.g., VijayawadaHyderabad Express"
                             maxlength="150">
                      <div class="form-text small text-muted">
                        Enter a descriptive name for internal reference (e.g., VijayawadaHyderabad Express).
                      </div>
                      <div *ngIf="step1FormSubmitted && routeNameError" class="invalid-feedback d-block small">
                        {{ routeNameError }}
                      </div>
                    </div>

                    <!-- Route ID -->
                    <div class="col-md-6">
                      <label class="form-label fw-bold small text-dark">
                        Route ID <span class="text-danger">*</span>
                      </label>
                      <input type="text"
                             class="form-control wizard-control bg-light text-muted"
                             [value]="wizardRouteCode"
                             readonly
                             tabindex="-1">
                      <div class="form-text small text-muted">
                        Auto-generated unique ID.
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Card 2: Route Endpoints -->
                <div class="wizard-card mb-4">
                  <div class="d-flex align-items-center gap-3 mb-4">
                    <div class="wizard-card-icon" style="background-color: #e0f2fe; color: #0284c7;">
                      <i class="fa-solid fa-location-dot"></i>
                    </div>
                    <div>
                      <h5 class="fw-bold text-dark mb-0">Route Endpoints</h5>
                      <div class="text-muted small">Select the origin and destination for this route.</div>
                    </div>
                  </div>

                  <div class="row g-4">
                    <!-- Origin -->
                    <div class="col-md-6">
                      <label class="form-label fw-bold small text-dark">
                        Origin <span class="text-danger">*</span>
                      </label>
                      <div class="input-group endpoint-input-group" [class.is-invalid]="step1FormSubmitted && originError">
                        <span class="input-group-text bg-white border-end-0">
                          <i class="fa-solid fa-location-dot text-primary"></i>
                        </span>
                        <select class="form-select border-start-0 ps-0 wizard-control"
                                [(ngModel)]="wizardOrigin"
                                (change)="validateEndpoints()">
                          <option value="" disabled>-- Select Origin City --</option>
                          <option *ngFor="let c of availableCities" [value]="c">{{ c }}</option>
                          <option value="__CUSTOM__">+ Enter Other City...</option>
                        </select>
                      </div>
                      <!-- Custom Origin Input if selected -->
                      <div *ngIf="wizardOrigin === '__CUSTOM__'" class="mt-2">
                        <input type="text"
                               class="form-control form-control-sm wizard-control"
                               [(ngModel)]="customOrigin"
                               (input)="validateEndpoints()"
                               placeholder="Type custom origin city..."
                               maxlength="100">
                      </div>
                      <div *ngIf="step1FormSubmitted && originError" class="text-danger small mt-1">
                        {{ originError }}
                      </div>
                    </div>

                    <!-- Destination -->
                    <div class="col-md-6">
                      <label class="form-label fw-bold small text-dark">
                        Destination <span class="text-danger">*</span>
                      </label>
                      <div class="input-group endpoint-input-group" [class.is-invalid]="step1FormSubmitted && (destinationError || endpointConflictError)">
                        <span class="input-group-text bg-white border-end-0">
                          <i class="fa-solid fa-location-dot text-danger"></i>
                        </span>
                        <select class="form-select border-start-0 ps-0 wizard-control"
                                [(ngModel)]="wizardDestination"
                                (change)="validateEndpoints()">
                          <option value="" disabled>-- Select Destination City --</option>
                          <option *ngFor="let c of availableCities" [value]="c">{{ c }}</option>
                          <option value="__CUSTOM__">+ Enter Other City...</option>
                        </select>
                      </div>
                      <!-- Custom Destination Input if selected -->
                      <div *ngIf="wizardDestination === '__CUSTOM__'" class="mt-2">
                        <input type="text"
                               class="form-control form-control-sm wizard-control"
                               [(ngModel)]="customDestination"
                               (input)="validateEndpoints()"
                               placeholder="Type custom destination city..."
                               maxlength="100">
                      </div>
                      <div *ngIf="step1FormSubmitted && destinationError" class="text-danger small mt-1">
                        {{ destinationError }}
                      </div>
                      <div *ngIf="step1FormSubmitted && endpointConflictError" class="text-danger small mt-1">
                        {{ endpointConflictError }}
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Card 3: Additional Details -->
                <div class="wizard-card mb-4">
                  <div class="d-flex align-items-center gap-3 mb-4">
                    <div class="wizard-card-icon" style="background-color: #e0f2fe; color: #0284c7;">
                      <i class="fa-solid fa-file-lines"></i>
                    </div>
                    <div>
                      <h5 class="fw-bold text-dark mb-0">Additional Details</h5>
                      <div class="text-muted small">Add optional details to help identify and manage this route.</div>
                    </div>
                  </div>

                  <!-- Description -->
                  <div class="mb-4">
                    <div class="d-flex justify-content-between align-items-center mb-1">
                      <label class="form-label fw-bold small text-dark mb-0">Description (Optional)</label>
                      <span class="small" [class.text-danger]="(wizardDescription || '').length > 500" [class.text-muted]="(wizardDescription || '').length <= 500">
                        {{ (wizardDescription || '').length }}/500
                      </span>
                    </div>
                    <textarea class="form-control wizard-control"
                              rows="3"
                              [(ngModel)]="wizardDescription"
                              (input)="validateDescription()"
                              placeholder="e.g., Connects Vijayawada to Hyderabad via key intermediate cities."
                              maxlength="500"></textarea>
                    <div *ngIf="step1FormSubmitted && descriptionError" class="text-danger small mt-1">
                      {{ descriptionError }}
                    </div>
                  </div>

                  <!-- Status and Notes -->
                  <div class="row g-4">
                    <!-- Status -->
                    <div class="col-md-6">
                      <label class="form-label fw-bold small text-dark">
                        Status <span class="text-danger">*</span>
                      </label>
                      <div class="dropdown status-dropdown position-relative">
                        <button class="form-select wizard-control d-flex align-items-center justify-content-between text-start"
                                type="button"
                                (click)="toggleStatusDropdown($event)">
                          <div class="d-flex align-items-center gap-2">
                            <i class="fa-solid fa-circle"
                               [ngClass]="wizardStatus === 'ACTIVE' ? 'text-success' : (wizardStatus === 'DRAFT' ? 'text-warning' : 'text-secondary')"
                               style="font-size: 9px;"></i>
                            <span class="fw-medium text-dark">{{ wizardStatus === 'ACTIVE' ? 'Active' : (wizardStatus === 'DRAFT' ? 'Draft' : 'Inactive') }}</span>
                          </div>
                        </button>
                        <div *ngIf="isStatusDropdownOpen" class="status-dropdown-menu shadow border py-1 w-100">
                          <button type="button"
                                  class="status-dropdown-item d-flex align-items-center gap-2 py-2 px-3 w-100"
                                  [class.active-item]="wizardStatus === 'ACTIVE'"
                                  (click)="selectStatus('ACTIVE', $event)">
                            <i class="fa-solid fa-circle text-success" style="font-size: 9px;"></i>
                            <span class="fw-medium">Active</span>
                          </button>
                          <button type="button"
                                  class="status-dropdown-item d-flex align-items-center gap-2 py-2 px-3 w-100"
                                  [class.active-item]="wizardStatus === 'DRAFT'"
                                  (click)="selectStatus('DRAFT', $event)">
                            <i class="fa-solid fa-circle text-warning" style="font-size: 9px;"></i>
                            <span class="fw-medium">Draft</span>
                          </button>
                          <button type="button"
                                  class="status-dropdown-item d-flex align-items-center gap-2 py-2 px-3 w-100"
                                  [class.active-item]="wizardStatus === 'INACTIVE'"
                                  (click)="selectStatus('INACTIVE', $event)">
                            <i class="fa-solid fa-circle text-secondary" style="font-size: 9px;"></i>
                            <span class="fw-medium">Inactive</span>
                          </button>
                        </div>
                      </div>
                    </div>

                    <!-- Notes -->
                    <div class="col-md-6">
                      <label class="form-label fw-bold small text-dark">Notes (Optional)</label>
                      <input type="text"
                             class="form-control wizard-control"
                             [(ngModel)]="wizardNotes"
                             placeholder="Any additional notes...">
                      <div class="form-text small text-muted">
                        Internal notes for operator reference.
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Bottom Actions -->
                <div class="d-flex justify-content-end align-items-center gap-3 pt-2 mb-5">
                  <button class="btn btn-outline-secondary px-4 py-2 rounded-3 fw-semibold wizard-btn-cancel"
                          (click)="cancelRouteCreation()"
                          type="button">
                    Cancel
                  </button>
                  <button class="btn btn-primary px-4 py-2 rounded-3 fw-bold d-inline-flex align-items-center gap-2 shadow-sm wizard-btn-next"
                          (click)="proceedToStep2()"
                          type="button">
                    <span>Next: Add Stops</span>
                    <i class="fa-solid fa-arrow-right"></i>
                  </button>
                </div>
              </div>

              <!-- ==================== STEP 2: ADD STOPS (Phase 5.3.2) ==================== -->
              <div *ngIf="routeWizardStep === 2">
                <!-- 1. Route Summary Card -->
                <div class="route-summary-card mb-4">
                  <div class="d-flex align-items-center w-100">
                    <!-- Icon -->
                    <div class="route-summary-icon me-4">
                      <i class="fa-solid fa-route"></i>
                    </div>

                    <!-- Distributed Summary Sections -->
                    <div class="route-summary-content d-flex align-items-center flex-grow-1">
                      <!-- Route Name -->
                      <div class="route-summary-item item-name">
                        <div class="route-summary-label">Route Name</div>
                        <div class="route-summary-val">{{ wizardRouteName || 'Unnamed Route' }}</div>
                      </div>

                      <!-- Divider 1 -->
                      <div class="route-summary-divider"></div>

                      <!-- Route ID -->
                      <div class="route-summary-item item-id">
                        <div class="route-summary-label">Route ID</div>
                        <div class="route-summary-val">{{ wizardRouteCode }}</div>
                      </div>

                      <!-- Divider 2 -->
                      <div class="route-summary-divider"></div>

                      <!-- Origin -> Destination -->
                      <div class="route-summary-item item-route">
                        <div class="route-summary-label">Origin &rarr; Destination</div>
                        <div class="route-summary-val d-flex align-items-center">
                          <i class="fa-solid fa-location-dot text-primary me-2"></i>
                          <span>{{ getEffectiveOrigin() }}</span>
                          <i class="fa-solid fa-arrow-right-arrow-left mx-2" style="font-size: 11px; color: #94a3b8;"></i>
                          <i class="fa-solid fa-location-dot text-danger me-2"></i>
                          <span>{{ getEffectiveDestination() }}</span>
                        </div>
                      </div>
                    </div>

                    <!-- Status Badge -->
                    <div class="route-summary-status ms-4">
                      <span *ngIf="wizardStatus === 'ACTIVE'" class="route-summary-badge badge-active">Active</span>
                      <span *ngIf="wizardStatus === 'DRAFT'" class="route-summary-badge badge-draft">Draft</span>
                      <span *ngIf="wizardStatus === 'INACTIVE'" class="route-summary-badge badge-inactive">Inactive</span>
                    </div>
                  </div>
                </div>

                <!-- 2. Add Intermediate Stops Card -->
                <div class="wizard-card mb-4">
                  <!-- Card Header -->
                  <div class="d-flex align-items-center justify-content-between flex-wrap gap-3 mb-4">
                    <div class="d-flex align-items-center gap-3">
                      <div class="wizard-card-icon" style="background-color: #e0f2fe; color: #0284c7;">
                        <i class="fa-solid fa-signs-post"></i>
                      </div>
                      <div>
                        <h5 class="fw-bold text-dark mb-0">Add Intermediate Stops</h5>
                        <div class="text-muted small">
                          Add stops between the origin and destination in the correct order. Each location can appear only once in this route.
                        </div>
                      </div>
                    </div>
                    <button class="btn btn-primary rounded-3 px-3 py-2 fw-semibold d-inline-flex align-items-center gap-2 shadow-sm"
                            (click)="toggleAddStopRow()"
                            type="button">
                      <i class="fa-solid fa-plus"></i>
                      <span>Add Stop</span>
                    </button>
                  </div>

                  <!-- Inline Add Stop Input Row (Appears when + Add Stop is clicked) -->
                  <div *ngIf="showAddStopRow" class="add-stop-inline-card p-3 mb-4 rounded-3 border">
                    <div class="fw-bold small text-dark mb-2">
                      <i class="fa-solid fa-map-pin text-primary me-1"></i>Add Intermediate Stop
                    </div>
                    <div class="row g-2 align-items-center">
                      <div class="col-md-6 col-12">
                        <div class="input-group">
                          <span class="input-group-text bg-white border-end-0 text-muted">
                            <i class="fa-solid fa-location-dot text-primary"></i>
                          </span>
                          <input type="text"
                                 class="form-control border-start-0 ps-0 wizard-control"
                                 list="intermediateCitiesList"
                                 [(ngModel)]="newStopNameInput"
                                 (input)="newStopError = null"
                                 (keyup.enter)="addWizardIntermediateStop()"
                                 placeholder="Select or enter stop name (e.g., Guntur, Suryapet)..."
                                 maxlength="100">
                          <datalist id="intermediateCitiesList">
                            <option *ngFor="let c of availableIntermediateCities" [value]="c">{{ c }}</option>
                          </datalist>
                        </div>
                      </div>
                      <div class="col-md-6 col-12 d-flex gap-2">
                        <button class="btn btn-primary btn-sm px-3 rounded-2 fw-semibold" (click)="addWizardIntermediateStop()" type="button">
                          <i class="fa-solid fa-plus me-1"></i> Add Stop
                        </button>
                        <button class="btn btn-outline-secondary btn-sm px-3 rounded-2" (click)="showAddStopRow = false; newStopError = null" type="button">
                          Cancel
                        </button>
                      </div>
                    </div>
                    <div *ngIf="newStopError" class="text-danger small mt-2">
                      <i class="fa-solid fa-circle-exclamation me-1"></i>{{ newStopError }}
                    </div>
                  </div>

                  <!-- Ordered Stops Table -->
                  <div class="table-responsive stops-table-container">
                    <table class="table stops-table align-middle mb-0">
                      <thead>
                        <tr>
                          <th style="width: 70px;">#</th>
                          <th>Stop Name / Location</th>
                          <th style="width: 160px;">Stop Type</th>
                          <th class="text-center" style="width: 140px;">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        <!-- 1. ORIGIN ROW (Fixed First) -->
                        <tr class="stop-row origin-row">
                          <td>
                            <div class="stepper-cell">
                              <span class="seq-circle origin">1</span>
                              <div class="stepper-track-down"></div>
                            </div>
                          </td>
                          <td>
                            <div class="d-flex align-items-center">
                              <i class="fa-solid fa-location-dot text-primary fs-5 me-3"></i>
                              <div>
                                <div class="fw-bold text-dark">{{ getEffectiveOrigin() }}</div>
                                <div *ngIf="getCityState(getEffectiveOrigin())" class="text-muted small">
                                  {{ getCityState(getEffectiveOrigin()) }}
                                </div>
                              </div>
                            </div>
                          </td>
                          <td>
                            <span class="badge-type-origin">Origin</span>
                          </td>
                          <td class="text-center text-muted fw-semibold">
                            &mdash;
                          </td>
                        </tr>

                        <!-- 2. INTERMEDIATE STOPS ROWS -->
                        <tr *ngFor="let stop of wizardIntermediateStops; let idx = index" class="stop-row intermediate-row">
                          <td>
                            <div class="stepper-cell">
                              <div class="stepper-track-up"></div>
                              <span class="seq-circle intermediate">{{ idx + 2 }}</span>
                              <div class="stepper-track-down"></div>
                            </div>
                          </td>
                          <td>
                            <div class="d-flex align-items-center">
                              <i class="fa-solid fa-location-dot text-primary fs-5 me-3"></i>
                              <div>
                                <div class="fw-bold text-dark">{{ stop.stopName }}</div>
                                <div *ngIf="stop.state || getCityState(stop.stopName)" class="text-muted small">
                                  {{ stop.state || getCityState(stop.stopName) }}
                                </div>
                              </div>
                            </div>
                          </td>
                          <td>
                            <span class="badge-type-intermediate">Intermediate</span>
                          </td>
                          <td class="text-center">
                            <div class="d-inline-flex align-items-center gap-1">
                              <!-- Move Up -->
                              <button class="btn btn-sm btn-icon-action"
                                      [disabled]="idx === 0"
                                      (click)="moveWizardIntermediateStopUp(idx)"
                                      title="Move stop up">
                                <i class="fa-solid fa-arrow-up" [class.text-muted]="idx === 0"></i>
                              </button>
                              <!-- Move Down -->
                              <button class="btn btn-sm btn-icon-action"
                                      [disabled]="idx === wizardIntermediateStops.length - 1"
                                      (click)="moveWizardIntermediateStopDown(idx)"
                                      title="Move stop down">
                                <i class="fa-solid fa-arrow-down" [class.text-muted]="idx === wizardIntermediateStops.length - 1"></i>
                              </button>
                              <!-- Delete -->
                              <button class="btn btn-sm btn-icon-action text-danger"
                                      (click)="removeWizardIntermediateStop(idx)"
                                      title="Remove stop">
                                <i class="fa-solid fa-trash-can"></i>
                              </button>
                            </div>
                          </td>
                        </tr>

                        <!-- Empty Intermediate Notice if none added -->
                        <tr *ngIf="wizardIntermediateStops.length === 0">
                          <td colspan="4" class="text-center py-3 bg-light bg-opacity-50">
                            <span class="text-muted small">
                              <i class="fa-solid fa-route me-1 text-primary"></i>
                              Direct route with no intermediate stops. Click <strong>+ Add Stop</strong> to add intermediate cities between {{ getEffectiveOrigin() }} and {{ getEffectiveDestination() }}.
                            </span>
                          </td>
                        </tr>

                        <!-- 3. DESTINATION ROW (Fixed Last) -->
                        <tr class="stop-row destination-row">
                          <td>
                            <div class="stepper-cell">
                              <div class="stepper-track-up"></div>
                              <span class="seq-circle destination">{{ wizardIntermediateStops.length + 2 }}</span>
                            </div>
                          </td>
                          <td>
                            <div class="d-flex align-items-center">
                              <i class="fa-solid fa-location-dot text-danger fs-5 me-3"></i>
                              <div>
                                <div class="fw-bold text-dark">{{ getEffectiveDestination() }}</div>
                                <div *ngIf="getCityState(getEffectiveDestination())" class="text-muted small">
                                  {{ getCityState(getEffectiveDestination()) }}
                                </div>
                              </div>
                            </div>
                          </td>
                          <td>
                            <span class="badge-type-destination">Destination</span>
                          </td>
                          <td class="text-center text-muted fw-semibold">
                            &mdash;
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <!-- 3. Card: Important Guidelines Panel -->
                <div class="guidelines-card mb-4">
                  <div class="d-flex align-items-center gap-2 mb-3">
                    <i class="fa-solid fa-circle-info text-primary fs-5"></i>
                    <h6 class="fw-bold text-primary mb-0">Important Guidelines</h6>
                  </div>
                  <div class="row g-3 small">
                    <div class="col-md-6 col-12">
                      <div class="d-flex align-items-start gap-2 mb-2 text-secondary">
                        <span class="text-primary fw-bold">•</span>
                        <span>Add intermediate stops in the actual travel order from origin to destination.</span>
                      </div>
                      <div class="d-flex align-items-start gap-2 mb-2 text-secondary">
                        <span class="text-primary fw-bold">•</span>
                        <span>The same physical location cannot be added more than once in this route.</span>
                      </div>
                      <div class="d-flex align-items-start gap-2 text-secondary">
                        <span class="text-primary fw-bold">•</span>
                        <span>Timings for each stop will be configured while creating a schedule.</span>
                      </div>
                    </div>
                    <div class="col-md-6 col-12">
                      <div class="d-flex align-items-start gap-2 mb-2 text-secondary">
                        <span class="text-primary fw-bold">•</span>
                        <span>You can add, remove, or reorder intermediate stops before proceeding.</span>
                      </div>
                      <div class="d-flex align-items-start gap-2 text-secondary">
                        <span class="text-primary fw-bold">•</span>
                        <span>After adding stops, you will configure boarding and dropping points for each stop.</span>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 4. Bottom Navigation Actions -->
                <div class="d-flex justify-content-end align-items-center gap-3 pt-2 mb-5">
                  <button class="btn btn-outline-secondary px-4 py-2 rounded-3 fw-semibold wizard-btn-cancel d-inline-flex align-items-center gap-2"
                          (click)="routeWizardStep = 1"
                          type="button">
                    <i class="fa-solid fa-arrow-left"></i>
                    <span>Previous</span>
                  </button>
                  <button class="btn btn-primary px-4 py-2 rounded-3 fw-bold d-inline-flex align-items-center gap-2 shadow-sm wizard-btn-next"
                          (click)="proceedToStep3()"
                          type="button">
                    <span>Next: Configure Boarding & Dropping Points</span>
                    <i class="fa-solid fa-arrow-right"></i>
                  </button>
                </div>
              </div>

              <!-- ==================== STEP 3: CONFIGURE BOARDING & DROPPING POINTS (Phase 5.3.3) ==================== -->
              <div *ngIf="routeWizardStep === 3">
                <!-- 1. Route Summary Card (Full-Width Distributed) -->
                <div class="route-summary-card mb-4">
                  <div class="d-flex align-items-center w-100">
                    <!-- Icon -->
                    <div class="route-summary-icon me-4">
                      <i class="fa-solid fa-route"></i>
                    </div>

                    <!-- Distributed Summary Sections -->
                    <div class="route-summary-content d-flex align-items-center flex-grow-1">
                      <!-- Route Name -->
                      <div class="route-summary-item item-name">
                        <div class="route-summary-label">Route Name</div>
                        <div class="route-summary-val">{{ wizardRouteName || 'Unnamed Route' }}</div>
                      </div>

                      <!-- Divider 1 -->
                      <div class="route-summary-divider"></div>

                      <!-- Route ID -->
                      <div class="route-summary-item item-id">
                        <div class="route-summary-label">Route ID</div>
                        <div class="route-summary-val">{{ wizardRouteCode }}</div>
                      </div>

                      <!-- Divider 2 -->
                      <div class="route-summary-divider"></div>

                      <!-- Origin -> Destination -->
                      <div class="route-summary-item item-route">
                        <div class="route-summary-label">Origin &rarr; Destination</div>
                        <div class="route-summary-val d-flex align-items-center">
                          <i class="fa-solid fa-location-dot text-primary me-2"></i>
                          <span>{{ getEffectiveOrigin() }}</span>
                          <i class="fa-solid fa-arrow-right-arrow-left mx-2" style="font-size: 11px; color: #94a3b8;"></i>
                          <i class="fa-solid fa-location-dot text-danger me-2"></i>
                          <span>{{ getEffectiveDestination() }}</span>
                        </div>
                      </div>
                    </div>

                    <!-- Status Badge -->
                    <div class="route-summary-status ms-4">
                      <span *ngIf="wizardStatus === 'ACTIVE'" class="route-summary-badge badge-active">Active</span>
                      <span *ngIf="wizardStatus === 'DRAFT'" class="route-summary-badge badge-draft">Draft</span>
                      <span *ngIf="wizardStatus === 'INACTIVE'" class="route-summary-badge badge-inactive">Inactive</span>
                    </div>
                  </div>
                </div>

                <!-- 2. Configure Boarding and Dropping Points Card -->
                <div class="wizard-card mb-4">
                  <!-- Header -->
                  <div class="d-flex align-items-center gap-3 mb-4">
                    <div class="wizard-card-icon" style="background-color: #e0f2fe; color: #0284c7;">
                      <i class="fa-solid fa-location-dot"></i>
                    </div>
                    <div>
                      <h5 class="fw-bold text-dark mb-0">Configure Boarding and Dropping Points</h5>
                      <p class="text-muted small mb-0">
                        Add multiple boarding and dropping points for the selected stop. Each stop can have multiple points where passengers can board and alight.
                      </p>
                    </div>
                  </div>

                  <!-- Select Stop Dropdown -->
                  <div class="mb-4">
                    <label class="form-label fw-bold text-dark small mb-1">
                      Select Stop <span class="text-danger">*</span>
                    </label>
                    <div class="input-group stop-selector-group">
                      <span class="input-group-text bg-white border-end-0 text-primary">
                        <i class="fa-solid fa-location-dot"></i>
                      </span>
                      <select class="form-select border-start-0 ps-1 fw-semibold text-dark stop-selector-select"
                              [(ngModel)]="selectedWizardStopIndex"
                              (change)="onWizardStopSelectChange()">
                        <option *ngFor="let s of getAllStopsList(); let idx = index" [ngValue]="idx">
                          {{ s.name }} ({{ s.type === 'ORIGIN' ? 'Origin' : (s.type === 'DESTINATION' ? 'Destination' : 'Intermediate') }})
                        </option>
                      </select>
                    </div>
                    <div class="form-text text-muted small mt-1">
                      Choose a stop from the route to configure its boarding and dropping points.
                    </div>
                  </div>

                  <!-- Two Columns: Boarding Points & Dropping Points -->
                  <div class="row g-4 mb-3">
                    <!-- Column A: Boarding Points (Green Theme) -->
                    <div class="col-lg-6 col-12">
                      <div class="points-col-card points-col-boarding h-100 d-flex flex-column">
                        <div class="points-col-header d-flex align-items-center justify-content-between p-3 border-bottom">
                          <div class="d-flex align-items-center gap-2">
                            <div class="points-header-icon icon-boarding">
                              <i class="fa-solid fa-person-walking-luggage"></i>
                            </div>
                            <div>
                              <h6 class="fw-bold mb-0 text-success">Boarding Points</h6>
                              <div class="text-muted small" style="font-size: 11px;">
                                Add pickup points where passengers can board at this stop.
                              </div>
                            </div>
                          </div>
                          <button type="button"
                                  class="btn btn-sm btn-add-boarding d-inline-flex align-items-center gap-1 shadow-sm"
                                  (click)="openAddPointModal('BOARDING')">
                            <i class="fa-solid fa-plus small"></i>
                            <span>Add Boarding Point</span>
                          </button>
                        </div>

                        <div class="points-col-body p-3 flex-grow-1">
                          <div class="table-responsive" *ngIf="getCurrentBoardingPoints().length > 0">
                            <table class="table table-hover align-middle points-table mb-0">
                              <thead>
                                <tr>
                                  <th style="width: 35px;">#</th>
                                  <th>Point Name</th>
                                  <th>Location / Address</th>
                                  <th>Time Slot</th>
                                  <th class="text-end" style="width: 70px;">Actions</th>
                                </tr>
                              </thead>
                              <tbody>
                                <tr *ngFor="let bp of getCurrentBoardingPoints(); let pIdx = index">
                                  <td class="text-muted fw-semibold">{{ pIdx + 1 }}</td>
                                  <td class="fw-semibold text-dark">{{ bp.pointName }}</td>
                                  <td class="text-muted small">{{ bp.address || '—' }}</td>
                                  <td>
                                    <span class="time-slot-badge">
                                      <i class="fa-regular fa-clock me-1 text-muted"></i>{{ bp.timeSlot }}
                                    </span>
                                  </td>
                                  <td class="text-end">
                                    <button type="button"
                                            class="btn btn-sm btn-link text-primary p-0 me-2"
                                            (click)="openEditPointModal('BOARDING', pIdx)"
                                            title="Edit Point">
                                      <i class="fa-solid fa-pen-to-square"></i>
                                    </button>
                                    <button type="button"
                                            class="btn btn-sm btn-link text-danger p-0"
                                            (click)="deleteWizardPoint('BOARDING', pIdx)"
                                            title="Delete Point">
                                      <i class="fa-solid fa-trash-can"></i>
                                    </button>
                                  </td>
                                </tr>
                              </tbody>
                            </table>
                          </div>

                          <!-- Empty State for Boarding Points -->
                          <div *ngIf="getCurrentBoardingPoints().length === 0" class="empty-points-state text-center py-4 text-muted">
                            <i class="fa-solid fa-person-walking-luggage fs-3 mb-2 d-block opacity-50 text-success"></i>
                            <div class="fw-semibold text-dark mb-1">No boarding points configured</div>
                            <div class="small">Click "+ Add Boarding Point" above to specify passenger pickup locations.</div>
                          </div>
                        </div>

                        <!-- Footer Banner -->
                        <div class="p-3 pt-0 mt-auto" *ngIf="getCurrentBoardingPoints().length > 0">
                          <div class="points-count-banner banner-boarding d-flex align-items-center gap-2">
                            <i class="fa-solid fa-circle-check"></i>
                            <span>{{ getCurrentBoardingPoints().length }} boarding points configured</span>
                          </div>
                        </div>
                      </div>
                    </div>

                    <!-- Column B: Dropping Points (Amber Theme) -->
                    <div class="col-lg-6 col-12">
                      <div class="points-col-card points-col-dropping h-100 d-flex flex-column">
                        <div class="points-col-header d-flex align-items-center justify-content-between p-3 border-bottom">
                          <div class="d-flex align-items-center gap-2">
                            <div class="points-header-icon icon-dropping">
                              <i class="fa-solid fa-location-dot"></i>
                            </div>
                            <div>
                              <h6 class="fw-bold mb-0 text-warning-emphasis" style="color: #b45309;">Dropping Points</h6>
                              <div class="text-muted small" style="font-size: 11px;">
                                Add drop-off points where passengers can alight at this stop.
                              </div>
                            </div>
                          </div>
                          <button type="button"
                                  class="btn btn-sm btn-add-dropping d-inline-flex align-items-center gap-1 shadow-sm"
                                  (click)="openAddPointModal('DROPPING')">
                            <i class="fa-solid fa-plus small"></i>
                            <span>Add Dropping Point</span>
                          </button>
                        </div>

                        <div class="points-col-body p-3 flex-grow-1">
                          <div class="table-responsive" *ngIf="getCurrentDroppingPoints().length > 0">
                            <table class="table table-hover align-middle points-table mb-0">
                              <thead>
                                <tr>
                                  <th style="width: 35px;">#</th>
                                  <th>Point Name</th>
                                  <th>Location / Address</th>
                                  <th>Time Slot</th>
                                  <th class="text-end" style="width: 70px;">Actions</th>
                                </tr>
                              </thead>
                              <tbody>
                                <tr *ngFor="let dp of getCurrentDroppingPoints(); let pIdx = index">
                                  <td class="text-muted fw-semibold">{{ pIdx + 1 }}</td>
                                  <td class="fw-semibold text-dark">{{ dp.pointName }}</td>
                                  <td class="text-muted small">{{ dp.address || '—' }}</td>
                                  <td>
                                    <span class="time-slot-badge">
                                      <i class="fa-regular fa-clock me-1 text-muted"></i>{{ dp.timeSlot }}
                                    </span>
                                  </td>
                                  <td class="text-end">
                                    <button type="button"
                                            class="btn btn-sm btn-link text-primary p-0 me-2"
                                            (click)="openEditPointModal('DROPPING', pIdx)"
                                            title="Edit Point">
                                      <i class="fa-solid fa-pen-to-square"></i>
                                    </button>
                                    <button type="button"
                                            class="btn btn-sm btn-link text-danger p-0"
                                            (click)="deleteWizardPoint('DROPPING', pIdx)"
                                            title="Delete Point">
                                      <i class="fa-solid fa-trash-can"></i>
                                    </button>
                                  </td>
                                </tr>
                              </tbody>
                            </table>
                          </div>

                          <!-- Empty State for Dropping Points -->
                          <div *ngIf="getCurrentDroppingPoints().length === 0" class="empty-points-state text-center py-4 text-muted">
                            <i class="fa-solid fa-location-dot fs-3 mb-2 d-block opacity-50" style="color: #f59e0b;"></i>
                            <div class="fw-semibold text-dark mb-1">No dropping points configured</div>
                            <div class="small">Click "+ Add Dropping Point" above to specify passenger drop-off locations.</div>
                          </div>
                        </div>

                        <!-- Footer Banner -->
                        <div class="p-3 pt-0 mt-auto" *ngIf="getCurrentDroppingPoints().length > 0">
                          <div class="points-count-banner banner-dropping d-flex align-items-center gap-2">
                            <i class="fa-solid fa-circle-check"></i>
                            <span>{{ getCurrentDroppingPoints().length }} dropping points configured</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 3. Important Guidelines Panel -->
                <div class="guidelines-card mb-4">
                  <div class="d-flex align-items-start gap-3">
                    <div class="wizard-card-icon mt-1" style="background-color: #0284c7; color: #ffffff; width: 36px; height: 36px; font-size: 16px;">
                      <i class="fa-solid fa-info"></i>
                    </div>
                    <div class="flex-grow-1">
                      <h6 class="fw-bold mb-3" style="color: #0284c7;">Important Guidelines</h6>
                      <div class="row g-3">
                        <div class="col-md-6 col-12">
                          <ul class="mb-0 ps-3 text-muted small" style="line-height: 1.8;">
                            <li>Configure boarding and dropping points for each stop.</li>
                            <li>A stop can have multiple boarding points and multiple dropping points.</li>
                            <li>The first stop (origin) must have at least one boarding point.</li>
                          </ul>
                        </div>
                        <div class="col-md-6 col-12">
                          <ul class="mb-0 ps-3 text-muted small" style="line-height: 1.8;">
                            <li>The last stop (destination) must have at least one dropping point.</li>
                            <li>Set the exact time (hours and minutes) for each boarding and dropping point.</li>
                            <li>You can add, edit or remove points before creating the route.</li>
                          </ul>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Validation Error Alert on Step 3 Next -->
                <div *ngIf="step3ValidationError" class="alert alert-danger d-flex align-items-center gap-2 rounded-3 mb-4 py-2 px-3 small">
                  <i class="fa-solid fa-circle-exclamation text-danger fs-6"></i>
                  <span>{{ step3ValidationError }}</span>
                </div>

                <!-- 4. Navigation Controls -->
                <div class="d-flex justify-content-between align-items-center pt-2">
                  <button type="button"
                          class="btn btn-outline-secondary px-4 py-2 rounded-3 fw-semibold d-inline-flex align-items-center gap-2"
                          (click)="routeWizardStep = 2">
                    <i class="fa-solid fa-arrow-left"></i>
                    <span>Previous</span>
                  </button>

                  <button type="button"
                          class="btn btn-primary px-4 py-2 rounded-3 fw-semibold d-inline-flex align-items-center gap-2 shadow-sm"
                          (click)="proceedToStep4()">
                    <span>Next: Review &amp; Create</span>
                    <i class="fa-solid fa-arrow-right"></i>
                  </button>
                </div>
              </div>

              <!-- ==================== STEP 4: REVIEW & CREATE (Phase 5.3.4) ==================== -->
              <div *ngIf="routeWizardStep === 4" class="step4-review-container">

                <!-- Error Banner if submission fails -->
                <div *ngIf="createRouteError" class="alert alert-danger d-flex align-items-center justify-content-between rounded-3 mb-4 py-2 px-3 small shadow-sm">
                  <div class="d-flex align-items-center gap-2">
                    <i class="fa-solid fa-circle-exclamation text-danger fs-6"></i>
                    <span>{{ createRouteError }}</span>
                  </div>
                  <button type="button" class="btn-close btn-sm" (click)="createRouteError = null"></button>
                </div>

                <!-- Card 1: Route Summary -->
                <div class="wizard-card mb-4 review-summary-card">
                  <!-- Header -->
                  <div class="d-flex align-items-center justify-content-between pb-3 mb-4 border-bottom">
                    <div class="d-flex align-items-center gap-3">
                      <div class="wizard-card-icon" style="background-color: #e0f2fe; color: #0284c7;">
                        <i class="fa-solid fa-file-lines"></i>
                      </div>
                      <div>
                        <h5 class="fw-bold text-dark mb-0">Route Summary</h5>
                        <div class="text-muted small">Basic route details and endpoint information</div>
                      </div>
                    </div>
                    <div>
                      <span *ngIf="wizardStatus === 'ACTIVE'" class="route-summary-badge badge-active">
                        <i class="fa-solid fa-circle me-1" style="font-size: 8px;"></i> Active
                      </span>
                      <span *ngIf="wizardStatus === 'DRAFT'" class="route-summary-badge badge-draft">
                        <i class="fa-solid fa-circle me-1" style="font-size: 8px;"></i> Draft
                      </span>
                      <span *ngIf="wizardStatus === 'INACTIVE'" class="route-summary-badge badge-inactive">
                        <i class="fa-solid fa-circle me-1" style="font-size: 8px;"></i> Inactive
                      </span>
                    </div>
                  </div>

                  <!-- Details Grid -->
                  <div class="review-details-grid">
                    <!-- Row 1 -->
                    <div class="review-details-row row g-3 align-items-stretch">
                      <!-- Route Name -->
                      <div class="col-md-4">
                        <div class="review-field-box">
                          <span class="review-field-label">ROUTE NAME</span>
                          <span class="review-field-value fw-bold text-dark">{{ wizardRouteName || '—' }}</span>
                        </div>
                      </div>
                      <!-- Route ID -->
                      <div class="col-md-3">
                        <div class="review-field-box review-divider-left">
                          <span class="review-field-label">ROUTE ID</span>
                          <span class="review-field-value fw-bold text-primary font-monospace">{{ wizardRouteCode }}</span>
                        </div>
                      </div>
                      <!-- Origin & Destination -->
                      <div class="col-md-5">
                        <div class="review-field-box review-divider-left">
                          <span class="review-field-label">ORIGIN &amp; DESTINATION</span>
                          <div class="review-field-value d-flex align-items-center gap-2">
                            <span class="d-inline-flex align-items-center gap-1">
                              <i class="fa-solid fa-location-dot text-primary"></i>
                              <strong class="text-dark">{{ getEffectiveOrigin() }}</strong>
                            </span>
                            <i class="fa-solid fa-arrow-right text-muted small mx-1"></i>
                            <span class="d-inline-flex align-items-center gap-1">
                              <i class="fa-solid fa-location-dot text-danger"></i>
                              <strong class="text-dark">{{ getEffectiveDestination() }}</strong>
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div class="review-row-separator my-3"></div>

                    <!-- Row 2 -->
                    <div class="review-details-row row g-3 align-items-stretch">
                      <!-- Description -->
                      <div class="col-md-4">
                        <div class="review-field-box">
                          <span class="review-field-label">DESCRIPTION</span>
                          <span class="review-field-value text-muted small">{{ wizardDescription || '—' }}</span>
                        </div>
                      </div>
                      <!-- Total Stops -->
                      <div class="col-md-4">
                        <div class="review-field-box review-divider-left">
                          <span class="review-field-label">TOTAL STOPS</span>
                          <div class="review-field-value d-flex align-items-center gap-2">
                            <i class="fa-solid fa-map-pin text-primary"></i>
                            <span class="fw-bold text-dark">
                              {{ getAllStopsList().length }} stops
                              <span class="fw-normal text-muted small ms-1">
                                (1 origin, {{ getIntermediateStopsCount() }} intermediate, 1 destination)
                              </span>
                            </span>
                          </div>
                        </div>
                      </div>
                      <!-- Notes -->
                      <div class="col-md-4">
                        <div class="review-field-box review-divider-left">
                          <span class="review-field-label">NOTES</span>
                          <span class="review-field-value text-muted small">{{ wizardNotes || '—' }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Card 2: Route Stops & Points -->
                <div class="wizard-card mb-4 review-stops-card">
                  <!-- Header -->
                  <div class="d-flex align-items-center justify-content-between pb-3 mb-4 border-bottom">
                    <div class="d-flex align-items-center gap-3">
                      <div class="wizard-card-icon" style="background-color: #e0f2fe; color: #0284c7;">
                        <i class="fa-solid fa-route"></i>
                      </div>
                      <div>
                        <h5 class="fw-bold text-dark mb-0">Route Stops &amp; Points</h5>
                        <div class="text-muted small">Ordered list of stops with boarding and dropping points</div>
                      </div>
                    </div>
                    <div>
                      <button type="button"
                              class="btn btn-outline-secondary btn-sm px-3 rounded-3 d-inline-flex align-items-center gap-2 fw-semibold"
                              (click)="toggleAllStopsExpansion()">
                        <i class="fa-solid" [ngClass]="isAllStopsExpanded ? 'fa-compress' : 'fa-expand'"></i>
                        <span>{{ isAllStopsExpanded ? 'Collapse All' : 'Expand All' }}</span>
                      </button>
                    </div>
                  </div>

                  <!-- Ordered Stops Accordion / Timeline -->
                  <div class="review-stops-timeline">
                    <div *ngFor="let s of getAllStopsList(); let i = index" class="review-stop-wrapper mb-3">
                      <!-- Stop Header Row -->
                      <div class="review-stop-row d-flex align-items-center justify-content-between p-3 rounded-3 cursor-pointer"
                           [class.expanded]="isStopExpanded(s.name)"
                           (click)="toggleStopExpansion(s.name)">
                        
                        <!-- Left: Seq, Pin, Stop Name, Badge -->
                        <div class="d-flex align-items-center gap-3">
                          <div class="review-stop-seq-circle"
                               [ngClass]="s.type === 'ORIGIN' ? 'seq-origin' : (s.type === 'DESTINATION' ? 'seq-destination' : 'seq-intermediate')">
                            {{ i + 1 }}
                          </div>
                          <i class="fa-solid fa-location-dot fs-5"
                             [ngClass]="s.type === 'DESTINATION' ? 'text-danger' : 'text-primary'"></i>
                          <div class="d-flex align-items-center gap-2">
                            <span class="fw-bold text-dark fs-6">{{ s.name }}</span>
                            <span *ngIf="s.type === 'ORIGIN'" class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill px-2.5 py-1 small">
                              Origin
                            </span>
                            <span *ngIf="s.type === 'INTERMEDIATE'" class="badge bg-light text-secondary border rounded-pill px-2.5 py-1 small">
                              Intermediate
                            </span>
                            <span *ngIf="s.type === 'DESTINATION'" class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill px-2.5 py-1 small">
                              Destination
                            </span>
                          </div>
                        </div>

                        <!-- Right: Boarding count, Dropping count, Chevron -->
                        <div class="d-flex align-items-center gap-4">
                          <span class="small text-muted d-inline-flex align-items-center gap-2">
                            <i class="fa-solid fa-users text-primary"></i>
                            <strong class="text-dark">{{ getWizardStopPoints(s.name).boardingPoints.length }}</strong> Boarding Points
                          </span>
                          <span class="small text-muted d-inline-flex align-items-center gap-2">
                            <i class="fa-solid fa-location-arrow text-danger"></i>
                            <strong class="text-dark">{{ getWizardStopPoints(s.name).droppingPoints.length }}</strong> Dropping Points
                          </span>
                          <i class="fa-solid text-muted transition-chevron"
                             [ngClass]="isStopExpanded(s.name) ? 'fa-chevron-up' : 'fa-chevron-down'"></i>
                        </div>
                      </div>

                      <!-- Expanded Panel: Boarding & Dropping Points Tables -->
                      <div *ngIf="isStopExpanded(s.name)" class="review-points-panel p-3 border-top">
                        <div class="row g-4">
                          <!-- Boarding Points Column -->
                          <div class="col-md-6">
                            <div class="d-flex align-items-center justify-content-between mb-2">
                              <span class="fw-bold small text-success d-inline-flex align-items-center gap-2">
                                <i class="fa-solid fa-circle-dot"></i>
                                Boarding Points ({{ getWizardStopPoints(s.name).boardingPoints.length }})
                              </span>
                            </div>
                            
                            <div *ngIf="getWizardStopPoints(s.name).boardingPoints.length === 0" class="empty-points-hint p-3 rounded-2 text-muted small fst-italic text-center bg-white border">
                              No boarding points configured for this stop.
                            </div>

                            <div *ngIf="getWizardStopPoints(s.name).boardingPoints.length > 0" class="table-responsive bg-white rounded-3 border">
                              <table class="table table-sm review-mini-table mb-0 align-middle">
                                <thead class="table-light">
                                  <tr>
                                    <th style="width: 35%;">Point Name</th>
                                    <th>Location / Address</th>
                                    <th style="width: 25%;">Time Slot</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  <tr *ngFor="let bp of getWizardStopPoints(s.name).boardingPoints">
                                    <td class="fw-semibold text-dark">{{ bp.pointName }}</td>
                                    <td class="text-muted small">{{ bp.address || '—' }}</td>
                                    <td>
                                      <span class="badge bg-light text-dark border font-monospace">
                                        <i class="fa-regular fa-clock me-1 text-muted"></i>{{ bp.timeSlot || '—' }}
                                      </span>
                                    </td>
                                  </tr>
                                </tbody>
                              </table>
                            </div>
                          </div>

                          <!-- Dropping Points Column -->
                          <div class="col-md-6">
                            <div class="d-flex align-items-center justify-content-between mb-2">
                              <span class="fw-bold small text-warning-emphasis d-inline-flex align-items-center gap-2">
                                <i class="fa-solid fa-circle-dot"></i>
                                Dropping Points ({{ getWizardStopPoints(s.name).droppingPoints.length }})
                              </span>
                            </div>

                            <div *ngIf="getWizardStopPoints(s.name).droppingPoints.length === 0" class="empty-points-hint p-3 rounded-2 text-muted small fst-italic text-center bg-white border">
                              No dropping points configured for this stop.
                            </div>

                            <div *ngIf="getWizardStopPoints(s.name).droppingPoints.length > 0" class="table-responsive bg-white rounded-3 border">
                              <table class="table table-sm review-mini-table mb-0 align-middle">
                                <thead class="table-light">
                                  <tr>
                                    <th style="width: 35%;">Point Name</th>
                                    <th>Location / Address</th>
                                    <th style="width: 25%;">Time Slot</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  <tr *ngFor="let dp of getWizardStopPoints(s.name).droppingPoints">
                                    <td class="fw-semibold text-dark">{{ dp.pointName }}</td>
                                    <td class="text-muted small">{{ dp.address || '—' }}</td>
                                    <td>
                                      <span class="badge bg-light text-dark border font-monospace">
                                        <i class="fa-regular fa-clock me-1 text-muted"></i>{{ dp.timeSlot || '—' }}
                                      </span>
                                    </td>
                                  </tr>
                                </tbody>
                              </table>
                            </div>
                          </div>
                        </div>
                      </div>

                    </div>
                  </div>
                </div>

                <!-- Card 3: Validation Summary -->
                <div class="wizard-card mb-4 validation-summary-card">
                  <div class="d-flex align-items-start gap-3 mb-3">
                    <div class="validation-summary-icon-box">
                      <i class="fa-solid fa-circle-info"></i>
                    </div>
                    <div>
                      <h6 class="fw-bold text-dark mb-1">Validation Summary</h6>
                      <p class="text-muted small mb-0">
                        All required route configurations are verified before creation.
                      </p>
                    </div>
                  </div>

                  <!-- Checklist items -->
                  <div class="validation-checklist d-flex flex-column gap-2 ps-1">
                    <!-- Check 1: Origin boarding -->
                    <div class="d-flex align-items-center gap-2">
                      <i class="fa-solid fs-6"
                         [ngClass]="hasValidOriginBoarding() ? 'fa-circle-check text-success' : 'fa-circle-xmark text-danger'"></i>
                      <span class="small" [class.text-dark]="hasValidOriginBoarding()" [class.text-danger]="!hasValidOriginBoarding()">
                        Origin has at least one boarding point configured.
                      </span>
                    </div>

                    <!-- Check 2: Destination dropping -->
                    <div class="d-flex align-items-center gap-2">
                      <i class="fa-solid fs-6"
                         [ngClass]="hasValidDestinationDropping() ? 'fa-circle-check text-success' : 'fa-circle-xmark text-danger'"></i>
                      <span class="small" [class.text-dark]="hasValidDestinationDropping()" [class.text-danger]="!hasValidDestinationDropping()">
                        Destination has at least one dropping point configured.
                      </span>
                    </div>

                    <!-- Check 3: All points valid -->
                    <div class="d-flex align-items-center gap-2">
                      <i class="fa-solid fs-6"
                         [ngClass]="areAllStopPointsValid() ? 'fa-circle-check text-success' : 'fa-circle-xmark text-danger'"></i>
                      <span class="small" [class.text-dark]="areAllStopPointsValid()" [class.text-danger]="!areAllStopPointsValid()">
                        All stop points are valid.
                      </span>
                    </div>
                  </div>
                </div>

                <!-- Bottom Navigation & Action Buttons -->
                <div class="d-flex align-items-center justify-content-between pt-2 mb-5">
                  <button type="button"
                          class="btn btn-outline-secondary px-4 py-2.5 rounded-3 fw-semibold d-inline-flex align-items-center gap-2"
                          (click)="routeWizardStep = 3"
                          [disabled]="isSubmittingRoute">
                    <i class="fa-solid fa-arrow-left"></i>
                    <span>Previous</span>
                  </button>

                  <button type="button"
                          class="btn btn-primary px-4 py-2.5 rounded-3 fw-semibold d-inline-flex align-items-center gap-2 shadow-sm"
                          (click)="submitWizardCreateRoute()"
                          [disabled]="isSubmittingRoute || !isStep4Valid()">
                    <span *ngIf="isSubmittingRoute" class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                    <i *ngIf="!isSubmittingRoute" class="fa-solid fa-check"></i>
                    <span>{{ isSubmittingRoute ? 'Creating Route...' : 'Create Route' }}</span>
                  </button>
                </div>

              </div>
<!-- ==================== POINT ADD / EDIT MODAL (Phase 5.3.3) ==================== -->
      <div *ngIf="showPointModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content rounded-4 border-0 shadow">
            <div class="modal-header border-bottom px-4 py-3"
                 [ngClass]="pointModalType === 'BOARDING' ? 'bg-success-subtle text-success' : 'bg-warning-subtle text-warning-emphasis'">
              <div class="d-flex align-items-center gap-2">
                <i [ngClass]="pointModalType === 'BOARDING' ? 'fa-solid fa-person-walking-luggage fs-5' : 'fa-solid fa-location-dot fs-5'"></i>
                <h5 class="modal-title fw-bold mb-0">
                  {{ pointModalMode === 'ADD' ? 'Add' : 'Edit' }} {{ pointModalType === 'BOARDING' ? 'Boarding' : 'Dropping' }} Point
                </h5>
              </div>
              <button type="button" class="btn-close" (click)="closePointModal()"></button>
            </div>
            <div class="modal-body p-4">
              <div class="mb-3 small text-muted">
                Configuring point for: <strong class="text-dark">{{ getCurrentSelectedStop().name }}</strong> ({{ getCurrentSelectedStop().type }})
              </div>

              <!-- Error Alert -->
              <div *ngIf="pointModalError" class="alert alert-danger py-2 small rounded-3 mb-3">
                <i class="fa-solid fa-circle-exclamation me-1"></i>{{ pointModalError }}
              </div>

              <!-- 1. Point Name -->
              <div class="mb-3">
                <label class="form-label small fw-bold text-dark">
                  Point Name <span class="text-danger">*</span>
                </label>
                <input type="text"
                       class="form-control"
                       [(ngModel)]="pointModalName"
                       placeholder="e.g. Vijayawada Bus Station">
              </div>

              <!-- 2. Location / Address -->
              <div class="mb-3">
                <label class="form-label small fw-bold text-dark">
                  Location / Address
                </label>
                <input type="text"
                       class="form-control"
                       [(ngModel)]="pointModalAddress"
                       placeholder="e.g. MG Road, Near Benz Circle">
              </div>

              <!-- 3. Time Slot -->
              <div class="mb-3">
                <label class="form-label small fw-bold text-dark">
                  Time Slot <span class="text-danger">*</span>
                </label>
                <input type="text"
                       class="form-control"
                       [(ngModel)]="pointModalTime"
                       placeholder="e.g. 06:00 AM">
                <div class="form-text text-muted small">
                  Format: Hours and minutes with AM/PM (e.g. 06:00 AM, 06:15 PM)
                </div>
              </div>
            </div>
            <div class="modal-footer bg-light px-4 py-3 justify-content-end gap-2">
              <button type="button" class="btn btn-outline-secondary px-3 py-2 rounded-3" (click)="closePointModal()">
                Cancel
              </button>
              <button type="button"
                      class="btn px-4 py-2 rounded-3 fw-semibold text-white"
                      [ngClass]="pointModalType === 'BOARDING' ? 'btn-success' : 'btn-warning'"
                      (click)="savePointModal()">
                Save Point
              </button>
            </div>
          </div>
        </div>
      </div>
            </div>
          </div>

          <!-- ==================== VIEW 4: SCHEDULES DASHBOARD (PHASE 6.2A & 6.2B) ==================== -->
          <div *ngIf="activeNav === 'schedules'">
            <!-- List View (Phase 6.2A) -->
            <div *ngIf="scheduleViewMode === 'list'" class="schedules-dashboard-container">
            <!-- Header Section -->
            <div class="schedules-dashboard-header d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
              <div>
                <h2 class="schedules-title mb-1">Schedules</h2>
                <p class="schedules-subtitle mb-0">Create, manage and monitor your bus schedules</p>
              </div>
              <div class="d-flex align-items-center gap-2">
                <button class="btn btn-export d-flex align-items-center gap-2" (click)="exportSchedulesToCsv()">
                  <i class="fa-solid fa-download"></i>
                  <span>Export</span>
                </button>
                <button class="btn btn-create-schedule d-flex align-items-center gap-2" (click)="handleCreateScheduleClick()">
                  <i class="fa-solid fa-plus"></i>
                  <span>Create Schedule</span>
                </button>
              </div>
            </div>

            <!-- 5 Metric Summary Cards Row -->
            <div class="row g-3 mb-4 schedules-metrics-row">
              <!-- Card 1: Total Schedules -->
              <div class="col-12 col-sm-6 col-xl">
                <div class="schedules-stat-card"
                     [class.active-filter]="selectedScheduleStatusFilter === 'ALL'"
                     (click)="setScheduleStatusFilter('ALL')"
                     role="button">
                  <div class="d-flex justify-content-between align-items-start mb-2">
                    <span class="stat-label">Total Schedules</span>
                    <div class="stat-icon-wrapper total-icon">
                      <i class="fa-solid fa-calendar"></i>
                    </div>
                  </div>
                  <div class="stat-value mb-2">{{ scheduleMetrics.total }}</div>
                  <div class="stat-trend text-success d-flex align-items-center gap-1">
                    <i class="fa-solid fa-arrow-trend-up"></i>
                    <span>+12% vs last 30 days</span>
                  </div>
                </div>
              </div>

              <!-- Card 2: Upcoming -->
              <div class="col-12 col-sm-6 col-xl">
                <div class="schedules-stat-card"
                     [class.active-filter]="selectedScheduleStatusFilter === 'UPCOMING'"
                     (click)="setScheduleStatusFilter('UPCOMING')"
                     role="button">
                  <div class="d-flex justify-content-between align-items-start mb-2">
                    <span class="stat-label">Upcoming</span>
                    <div class="stat-icon-wrapper upcoming-icon">
                      <i class="fa-solid fa-circle-play"></i>
                    </div>
                  </div>
                  <div class="stat-value mb-2">{{ scheduleMetrics.upcoming }}</div>
                  <div class="stat-progress-section">
                    <div class="stat-percentage text-muted">{{ scheduleMetrics.upcomingPct }}%</div>
                    <div class="progress stat-progress-bar">
                      <div class="progress-bar bg-success" [style.width.%]="scheduleMetrics.upcomingPct"></div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Card 3: Ongoing -->
              <div class="col-12 col-sm-6 col-xl">
                <div class="schedules-stat-card"
                     [class.active-filter]="selectedScheduleStatusFilter === 'ONGOING'"
                     (click)="setScheduleStatusFilter('ONGOING')"
                     role="button">
                  <div class="d-flex justify-content-between align-items-start mb-2">
                    <span class="stat-label">Ongoing</span>
                    <div class="stat-icon-wrapper ongoing-icon">
                      <i class="fa-solid fa-bus"></i>
                    </div>
                  </div>
                  <div class="stat-value mb-2">{{ scheduleMetrics.ongoing }}</div>
                  <div class="stat-progress-section">
                    <div class="stat-percentage text-muted">{{ scheduleMetrics.ongoingPct }}%</div>
                    <div class="progress stat-progress-bar">
                      <div class="progress-bar bg-primary" [style.width.%]="scheduleMetrics.ongoingPct"></div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Card 4: Completed Today -->
              <div class="col-12 col-sm-6 col-xl">
                <div class="schedules-stat-card"
                     [class.active-filter]="selectedScheduleStatusFilter === 'COMPLETED'"
                     (click)="setScheduleStatusFilter('COMPLETED')"
                     role="button">
                  <div class="d-flex justify-content-between align-items-start mb-2">
                    <span class="stat-label">Completed Today</span>
                    <div class="stat-icon-wrapper completed-icon">
                      <i class="fa-solid fa-clock-rotate-left"></i>
                    </div>
                  </div>
                  <div class="stat-value mb-2">{{ scheduleMetrics.completed }}</div>
                  <div class="stat-progress-section">
                    <div class="stat-percentage text-muted">{{ scheduleMetrics.completedPct }}%</div>
                    <div class="progress stat-progress-bar">
                      <div class="progress-bar bg-warning" [style.width.%]="scheduleMetrics.completedPct"></div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Card 5: Cancelled Today -->
              <div class="col-12 col-sm-6 col-xl">
                <div class="schedules-stat-card"
                     [class.active-filter]="selectedScheduleStatusFilter === 'CANCELLED'"
                     (click)="setScheduleStatusFilter('CANCELLED')"
                     role="button">
                  <div class="d-flex justify-content-between align-items-start mb-2">
                    <span class="stat-label">Cancelled Today</span>
                    <div class="stat-icon-wrapper cancelled-icon">
                      <i class="fa-solid fa-ban"></i>
                    </div>
                  </div>
                  <div class="stat-value mb-2">{{ scheduleMetrics.cancelled }}</div>
                  <div class="stat-progress-section">
                    <div class="stat-percentage text-muted">{{ scheduleMetrics.cancelledPct }}%</div>
                    <div class="progress stat-progress-bar">
                      <div class="progress-bar bg-danger" [style.width.%]="scheduleMetrics.cancelledPct"></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Filter Card -->
            <div class="card border-0 shadow-sm rounded-3 mb-4 schedules-filter-card">
              <div class="card-body p-3">
                <div class="row g-3 align-items-end">
                  <!-- Search Schedule Field -->
                  <div class="col-12 col-md-4">
                    <label class="form-label small fw-semibold text-secondary mb-1">Search Schedule</label>
                    <div class="input-group search-input-group">
                      <span class="input-group-text bg-transparent border-end-0 text-muted">
                        <i class="fa-solid fa-magnifying-glass"></i>
                      </span>
                      <input type="text"
                             class="form-control border-start-0 ps-0"
                             placeholder="Search route, bus number, or schedule ID..."
                             aria-label="Search Schedule"
                             [(ngModel)]="schedulesSearchTerm"
                             (input)="onScheduleFilterChange()">
                      <button *ngIf="schedulesSearchTerm"
                              class="btn btn-outline-secondary border-start-0 border-end"
                              type="button"
                              (click)="schedulesSearchTerm = ''; onScheduleFilterChange()"
                              title="Clear search">
                        <i class="fa-solid fa-xmark"></i>
                      </button>
                    </div>
                  </div>

                  <!-- Route Filter Field -->
                  <div class="col-12 col-sm-6 col-md-2">
                    <label class="form-label small fw-semibold text-secondary mb-1">Route</label>
                    <select class="form-select"
                            aria-label="Select Route"
                            [(ngModel)]="selectedScheduleRouteFilter"
                            (change)="onScheduleFilterChange()">
                      <option value="ALL">All Routes</option>
                      <option *ngFor="let opt of scheduleRouteOptions" [value]="opt.value">{{ opt.label }}</option>
                    </select>
                  </div>

                  <!-- Bus Filter Field -->
                  <div class="col-12 col-sm-6 col-md-2">
                    <label class="form-label small fw-semibold text-secondary mb-1">Assigned Bus</label>
                    <select class="form-select"
                            aria-label="Select Assigned Bus"
                            [(ngModel)]="selectedScheduleBusFilter"
                            (change)="onScheduleFilterChange()">
                      <option value="ALL">All Buses</option>
                      <option *ngFor="let opt of scheduleBusOptions" [value]="opt.value">{{ opt.label }}</option>
                    </select>
                  </div>

                  <!-- Status Filter Field -->
                  <div class="col-12 col-sm-6 col-md-2">
                    <label class="form-label small fw-semibold text-secondary mb-1">Trip Status</label>
                    <select class="form-select"
                            aria-label="Select Trip Status"
                            [(ngModel)]="selectedScheduleStatusFilter"
                            (change)="onScheduleFilterChange()">
                      <option value="ALL">All Statuses</option>
                      <option value="UPCOMING">Upcoming</option>
                      <option value="ONGOING">Ongoing</option>
                      <option value="COMPLETED">Completed</option>
                      <option value="CANCELLED">Cancelled</option>
                    </select>
                  </div>

                  <!-- Travel Date Filter Field -->
                  <div class="col-12 col-sm-6 col-md-2">
                    <label class="form-label small fw-semibold text-secondary mb-1">Travel Date</label>
                    <div class="input-group">
                      <input type="date"
                             class="form-control"
                             aria-label="Select Travel Date"
                             [(ngModel)]="selectedScheduleDateFilter"
                             (change)="onScheduleFilterChange()">
                      <button *ngIf="selectedScheduleDateFilter"
                              class="btn btn-outline-secondary"
                              type="button"
                              (click)="selectedScheduleDateFilter = ''; onScheduleFilterChange()"
                              title="Clear date">
                        <i class="fa-solid fa-xmark"></i>
                      </button>
                    </div>
                  </div>
                </div>

                <!-- Active Filter Tags (if any active) -->
                <div *ngIf="hasActiveScheduleFilters()" class="d-flex align-items-center justify-content-between pt-2 mt-3 border-top">
                  <div class="d-flex align-items-center gap-2 flex-wrap">
                    <small class="text-muted fw-semibold">Active filters:</small>
                    <span *ngIf="schedulesSearchTerm" class="badge bg-light text-dark border">
                      Search: "{{ schedulesSearchTerm }}"
                      <i class="fa-solid fa-xmark ms-1 cursor-pointer" (click)="schedulesSearchTerm = ''; onScheduleFilterChange()"></i>
                    </span>
                    <span *ngIf="selectedScheduleRouteFilter !== 'ALL'" class="badge bg-light text-dark border">
                      Route: {{ getSelectedRouteFilterLabel() }}
                      <i class="fa-solid fa-xmark ms-1 cursor-pointer" (click)="selectedScheduleRouteFilter = 'ALL'; onScheduleFilterChange()"></i>
                    </span>
                    <span *ngIf="selectedScheduleBusFilter !== 'ALL'" class="badge bg-light text-dark border">
                      Bus: {{ getSelectedBusFilterLabel() }}
                      <i class="fa-solid fa-xmark ms-1 cursor-pointer" (click)="selectedScheduleBusFilter = 'ALL'; onScheduleFilterChange()"></i>
                    </span>
                    <span *ngIf="selectedScheduleStatusFilter !== 'ALL'" class="badge bg-light text-dark border">
                      Status: {{ selectedScheduleStatusFilter | titlecase }}
                      <i class="fa-solid fa-xmark ms-1 cursor-pointer" (click)="selectedScheduleStatusFilter = 'ALL'; onScheduleFilterChange()"></i>
                    </span>
                    <span *ngIf="selectedScheduleDateFilter" class="badge bg-light text-dark border">
                      Travel Date: {{ formatScheduleDate(selectedScheduleDateFilter) }}
                      <i class="fa-solid fa-xmark ms-1 cursor-pointer" (click)="selectedScheduleDateFilter = ''; onScheduleFilterChange()"></i>
                    </span>
                  </div>
                  <button class="btn btn-link btn-sm text-decoration-none text-danger p-0" (click)="clearScheduleFilters()">
                    <i class="fa-solid fa-arrow-rotate-left me-1"></i>Reset all
                  </button>
                </div>
              </div>
            </div>

            <!-- Table Subheader Controls -->
            <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
              <div class="text-muted small fw-medium">
                Showing <span class="text-dark fw-semibold">{{ schedulesPaginationStart }}–{{ schedulesPaginationEnd }}</span> of <span class="text-dark fw-semibold">{{ filteredSchedules.length }}</span> schedules
              </div>
              <div class="d-flex align-items-center gap-3">
                <div class="d-flex align-items-center gap-2">
                  <label class="small text-muted mb-0 text-nowrap">Sort:</label>
                  <select class="form-select form-select-sm" [(ngModel)]="schedulesSortBy" style="width: auto;">
                    <option value="DEP_EARLIEST">Departure (Earliest)</option>
                    <option value="DEP_LATEST">Departure (Latest)</option>
                    <option value="DATE_NEWEST">Travel Date (Newest)</option>
                    <option value="FARE_ASC">Fare (Low to High)</option>
                    <option value="FARE_DESC">Fare (High to Low)</option>
                    <option value="SEATS_DESC">Available Seats</option>
                  </select>
                </div>
                <div class="d-flex align-items-center gap-2">
                  <label class="small text-muted mb-0 text-nowrap">Show:</label>
                  <select class="form-select form-select-sm" [(ngModel)]="schedulesPageSize" (change)="schedulesCurrentPage = 1" style="width: auto;">
                    <option [ngValue]="10">10 per page</option>
                    <option [ngValue]="25">25 per page</option>
                    <option [ngValue]="50">50 per page</option>
                  </select>
                </div>
              </div>
            </div>

            <!-- Schedules Table Card -->
            <div class="card border-0 shadow-sm rounded-3 overflow-hidden mb-4">
              <div class="table-responsive">
                <table class="table table-hover align-middle mb-0 schedules-table">
                  <thead class="table-light">
                    <tr>
                      <th style="min-width: 110px;" class="ps-3">SCHEDULE ID</th>
                      <th style="min-width: 200px;">ROUTE</th>
                      <th style="min-width: 170px;">BUS</th>
                      <th style="min-width: 140px;">DEPARTURE</th>
                      <th style="min-width: 140px;">ARRIVAL</th>
                      <th style="min-width: 90px;">DURATION</th>
                      <th style="min-width: 120px;">FARE (STARTING)</th>
                      <th style="min-width: 100px;">SEATS</th>
                      <th style="min-width: 110px;">STATUS</th>
                      <th style="width: 50px;" class="text-center pe-3">ACTIONS</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let s of pagedSchedules">
                      <td class="ps-3">
                        <span class="sched-id-badge">SCH-{{ s.id }}</span>
                      </td>
                      <td>
                        <div class="d-flex align-items-center gap-2">
                          <div class="route-icon-badge">
                            <i class="fa-solid fa-route"></i>
                          </div>
                          <div>
                            <div class="fw-semibold text-dark text-nowrap">
                              {{ s.origin }} <span class="text-muted mx-1">&rarr;</span> {{ s.destination }}
                            </div>
                            <small *ngIf="s.routeId" class="text-muted">Route #{{ s.routeId }}</small>
                          </div>
                        </div>
                      </td>
                      <td>
                        <div class="fw-semibold text-dark">{{ s.busNumber || getBusNumber(s.busId) }}</div>
                        <small class="text-muted d-block">{{ s.busType || getBusType(s.busId) }} ({{ s.capacity || getBusCapacity(s.busId) }} seats)</small>
                      </td>
                      <td>
                        <div class="text-dark">{{ formatScheduleDate(s.travelDate) }}</div>
                        <div class="fw-bold text-dark">{{ formatScheduleTime(s.departureTime) }}</div>
                      </td>
                      <td>
                        <div class="text-dark d-flex align-items-center gap-1">
                          <span>{{ formatScheduleDate(getArrivalDate(s)) }}</span>
                          <span *ngIf="isOvernightSchedule(s)" class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill px-1" style="font-size: 10px;">+1d</span>
                        </div>
                        <div class="fw-bold text-dark">{{ formatScheduleTime(s.arrivalTime) }}</div>
                      </td>
                      <td>
                        <span class="duration-text">{{ calculateDuration(s) }}</span>
                      </td>
                      <td>
                        <span class="fare-text">₹ {{ s.fare | number:'1.0-0' }}</span>
                      </td>
                      <td>
                        <span class="seats-text" [ngClass]="getSeatsClass(s)">
                          {{ s.availableSeats }} / {{ s.capacity || getBusCapacity(s.busId) }}
                        </span>
                      </td>
                      <td>
                        <span class="sched-pill" [ngClass]="getScheduleStatusPillClass(s)">
                          <span class="pill-dot"></span>
                          {{ getScheduleComputedStatus(s) | titlecase }}
                        </span>
                      </td>
                      <td class="text-center pe-3 position-relative">
                        <button class="btn btn-sm btn-icon rounded-circle"
                                (click)="toggleScheduleActionMenu(s.id, $event)"
                                title="Actions">
                          <i class="fa-solid fa-ellipsis-vertical text-muted"></i>
                        </button>

                        <!-- Action Dropdown Menu -->
                        <div *ngIf="activeScheduleActionMenuId === s.id"
                             class="sched-action-dropdown shadow rounded-3 border"
                             (click)="$event.stopPropagation()">
                          <button class="dropdown-item d-flex align-items-center gap-2 py-2 px-3"
                                  (click)="openScheduleDetails(s)">
                            <i class="fa-solid fa-eye text-primary"></i>
                            <span>View Details</span>
                          </button>
                          <button class="dropdown-item d-flex align-items-center gap-2 py-2 px-3"
                                  (click)="handleCreateSimilarSchedule(s)">
                            <i class="fa-solid fa-copy text-info"></i>
                            <span>Create Similar</span>
                          </button>
                          <button class="dropdown-item d-flex align-items-center gap-2 py-2 px-3"
                                  (click)="viewScheduleBookings(s)">
                            <i class="fa-solid fa-ticket text-secondary"></i>
                            <span>Download Ticket List / View Bookings</span>
                          </button>
                          <button class="dropdown-item d-flex align-items-center gap-2 py-2 px-3 text-muted disabled cursor-not-allowed"
                                  disabled>
                            <i class="fa-solid fa-pen-to-square"></i>
                            <span>Edit (Coming Soon)</span>
                          </button>
                          <hr class="dropdown-divider my-1">
                          <button class="dropdown-item d-flex align-items-center gap-2 py-2 px-3 text-danger"
                                  [disabled]="s.status === 'CANCELLED'"
                                  (click)="openCancelScheduleModal(s)">
                            <i class="fa-solid fa-ban"></i>
                            <span>Cancel Schedule</span>
                          </button>
                        </div>
                      </td>
                    </tr>

                    <!-- Empty State -->
                    <tr *ngIf="filteredSchedules.length === 0">
                      <td colspan="10" class="text-center py-5">
                        <div class="empty-state-wrapper py-4">
                          <div class="empty-icon-circle mx-auto mb-3">
                            <i class="fa-solid fa-calendar-xmark fa-2x text-muted"></i>
                          </div>
                          <h6 class="fw-bold text-dark mb-1">No schedules found</h6>
                          <p class="text-muted small mb-3">
                            {{ hasActiveScheduleFilters() ? 'No trip schedules match your selected filters.' : 'You have not published any schedules yet.' }}
                          </p>
                          <button *ngIf="hasActiveScheduleFilters()"
                                  class="btn btn-outline-primary btn-sm rounded-pill"
                                  (click)="clearScheduleFilters()">
                            <i class="fa-solid fa-arrow-rotate-left me-1"></i> Clear Filters
                          </button>
                          <button *ngIf="!hasActiveScheduleFilters()"
                                  class="btn btn-primary btn-sm rounded-pill"
                                  (click)="handleCreateScheduleClick()">
                            <i class="fa-solid fa-plus me-1"></i> Create Schedule
                          </button>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <!-- Pagination Footer -->
              <div *ngIf="filteredSchedules.length > 0"
                   class="card-footer bg-white border-top d-flex flex-wrap justify-content-between align-items-center py-3 px-3">
                <div class="text-muted small">
                  Showing {{ schedulesPaginationStart }} to {{ schedulesPaginationEnd }} of {{ filteredSchedules.length }} results
                </div>
                <nav *ngIf="totalSchedulePages > 1" aria-label="Schedules pagination">
                  <ul class="pagination pagination-sm mb-0">
                    <li class="page-item" [class.disabled]="schedulesCurrentPage === 1">
                      <button class="page-link" (click)="goToSchedulePage(schedulesCurrentPage - 1)">Previous</button>
                    </li>
                    <li *ngFor="let p of getSchedulePageNumbers()"
                        class="page-item"
                        [class.active]="schedulesCurrentPage === p">
                      <button class="page-link" (click)="goToSchedulePage(p)">{{ p }}</button>
                    </li>
                    <li class="page-item" [class.disabled]="schedulesCurrentPage === totalSchedulePages">
                      <button class="page-link" (click)="goToSchedulePage(schedulesCurrentPage + 1)">Next</button>
                    </li>
                  </ul>
                </nav>
              </div>
            </div>
          </div> <!-- /schedules-dashboard-container -->

          <!-- ==================== CREATE SCHEDULE WIZARD (PHASE 6.2B) ==================== -->
          <div *ngIf="scheduleViewMode === 'create'" class="create-schedule-wizard-container pb-5">
            <!-- Wizard Page Header -->
            <div class="mb-4" *ngIf="scheduleWizardStep < 6">
              <h2 class="wizard-heading mb-1">Create New Schedule</h2>
              <p class="wizard-subheading mb-0" *ngIf="scheduleWizardStep === 1">Set up a new bus schedule for one of your existing routes</p>
              <p class="wizard-subheading mb-0" *ngIf="scheduleWizardStep === 2">Assign a bus from your available fleet for the selected route</p>
              <p class="wizard-subheading mb-0" *ngIf="scheduleWizardStep === 3">Set departure and arrival times and configure date/time for each boarding and dropping point</p>
              <p class="wizard-subheading mb-0" *ngIf="scheduleWizardStep === 4">Set fares for each travel segment and review calculated journey fares (modifiable)</p>
              <p class="wizard-subheading mb-0" *ngIf="scheduleWizardStep === 5">Review all details before creating the schedule</p>
              <p class="wizard-subheading mb-0" *ngIf="scheduleWizardStep === 6">Schedule created successfully</p>
            </div>

            <!-- 6-Step Horizontal Progress Indicator Card -->
            <div class="card wizard-progress-card rounded-4 p-4 mb-4" *ngIf="scheduleWizardStep < 6">
              <div class="wizard-steps-horizontal d-flex align-items-center justify-content-between">
                <!-- Step 1: Select Route -->
                <div class="wizard-step-item" [ngClass]="{'active': scheduleWizardStep === 1, 'completed': scheduleWizardStep > 1, 'upcoming': scheduleWizardStep < 1}">
                  <div class="wizard-step-circle">
                    <i *ngIf="scheduleWizardStep > 1" class="fa-solid fa-check"></i>
                    <span *ngIf="scheduleWizardStep <= 1">1</span>
                  </div>
                  <div class="wizard-step-info">
                    <span class="wizard-step-title">Select Route</span>
                    <span class="wizard-step-desc">{{ scheduleWizardStep > 1 && wizardSelectedRoute ? (wizardSelectedRoute.origin + ' → ' + wizardSelectedRoute.destination) : 'Choose an existing route' }}</span>
                  </div>
                </div>
                <div class="wizard-step-connector" [ngClass]="{'completed': scheduleWizardStep > 1}"></div>

                <!-- Step 2: Assign Bus -->
                <div class="wizard-step-item" [ngClass]="{'active': scheduleWizardStep === 2, 'completed': scheduleWizardStep > 2, 'upcoming': scheduleWizardStep < 2}">
                  <div class="wizard-step-circle">
                    <i *ngIf="scheduleWizardStep > 2" class="fa-solid fa-check"></i>
                    <span *ngIf="scheduleWizardStep <= 2">2</span>
                  </div>
                  <div class="wizard-step-info">
                    <span class="wizard-step-title">Assign Bus</span>
                    <span class="wizard-step-desc">{{ scheduleWizardStep > 2 && wizardSelectedBus ? (wizardSelectedBus.busNumber + (wizardSelectedBus.model && wizardSelectedBus.model !== 'Not available' ? ' (' + (wizardSelectedBus.manufacturer && wizardSelectedBus.manufacturer !== 'Not available' ? wizardSelectedBus.manufacturer + ' ' : '') + wizardSelectedBus.model + ')' : '')) : 'Select an available bus' }}</span>
                  </div>
                </div>
                <div class="wizard-step-connector" [ngClass]="{'completed': scheduleWizardStep > 2}"></div>

                <!-- Step 3: Configure Timings -->
                <div class="wizard-step-item" [ngClass]="{'active': scheduleWizardStep === 3, 'completed': scheduleWizardStep > 3, 'upcoming': scheduleWizardStep < 3}">
                  <div class="wizard-step-circle">
                    <i *ngIf="scheduleWizardStep > 3" class="fa-solid fa-check"></i>
                    <span *ngIf="scheduleWizardStep <= 3">3</span>
                  </div>
                  <div class="wizard-step-info">
                    <span class="wizard-step-title">Configure Timings</span>
                    <span class="wizard-step-desc">{{ scheduleWizardStep > 3 ? (formatDisplayDate(wizardTripStartDate) + ', ' + formatDisplayTime(wizardTripStartTime)) : 'Set departure, arrival &amp; stop times' }}</span>
                  </div>
                </div>
                <div class="wizard-step-connector" [ngClass]="{'completed': scheduleWizardStep > 3}"></div>

                <!-- Step 4: Configure Fares -->
                <div class="wizard-step-item" [ngClass]="{'active': scheduleWizardStep === 4, 'completed': scheduleWizardStep > 4, 'upcoming': scheduleWizardStep < 4}">
                  <div class="wizard-step-circle">
                    <i *ngIf="scheduleWizardStep > 4" class="fa-solid fa-check"></i>
                    <span *ngIf="scheduleWizardStep <= 4">4</span>
                  </div>
                  <div class="wizard-step-info">
                    <span class="wizard-step-title">Configure Fares</span>
                    <span class="wizard-step-desc">{{ scheduleWizardStep > 4 ? 'Segments & Journeys' : 'Set segment fares' }}</span>
                  </div>
                </div>
                <div class="wizard-step-connector" [ngClass]="{'completed': scheduleWizardStep > 4}"></div>

                <!-- Step 5: Review & Create -->
                <div class="wizard-step-item" [ngClass]="{'active': scheduleWizardStep === 5, 'completed': scheduleWizardStep > 5, 'upcoming': scheduleWizardStep < 5}">
                  <div class="wizard-step-circle">
                    <i *ngIf="scheduleWizardStep > 5" class="fa-solid fa-check"></i>
                    <span *ngIf="scheduleWizardStep <= 5">5</span>
                  </div>
                  <div class="wizard-step-info">
                    <span class="wizard-step-title">Review &amp; Create</span>
                    <span class="wizard-step-desc">Verify details</span>
                  </div>
                </div>
                <div class="wizard-step-connector" [ngClass]="{'completed': scheduleWizardStep > 5}"></div>

                <!-- Step 6: Confirmation -->
                <div class="wizard-step-item" [ngClass]="{'active': scheduleWizardStep === 6, 'completed': scheduleWizardStep > 6, 'upcoming': scheduleWizardStep < 6}">
                  <div class="wizard-step-circle">
                    <span>6</span>
                  </div>
                  <div class="wizard-step-info">
                    <span class="wizard-step-title">Confirmation</span>
                    <span class="wizard-step-desc">Schedule created</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- ==================== STEP 1 CONTENT ==================== -->
            <div *ngIf="scheduleWizardStep === 1">
              <!-- Select Route Workspace Card -->
              <div class="card route-selection-workspace rounded-4 p-4 mb-4">
                <!-- Card Header: Title + Search & Status Filter -->
                <div class="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
                  <div class="d-flex align-items-center gap-3">
                    <div class="route-panel-icon-badge">
                      <i class="fa-solid fa-code-fork"></i>
                    </div>
                    <div>
                      <h5 class="fw-bold text-dark mb-0">Select Route</h5>
                      <p class="text-muted small mb-0">Choose an existing active route to create a schedule</p>
                    </div>
                  </div>
                  <div class="d-flex align-items-center gap-3">
                    <!-- Search Input -->
                    <div class="input-group search-route-input-group" style="width: 320px;">
                      <span class="input-group-text bg-white border-end-0 text-muted">
                        <i class="fa-solid fa-magnifying-glass"></i>
                      </span>
                      <input type="text"
                             class="form-control border-start-0 ps-0"
                             placeholder="Search route (e.g. Bengaluru, Hyderabad...)"
                             [(ngModel)]="wizardRouteSearchQuery">
                    </div>
                    <!-- Status Filter -->
                    <select class="form-select route-status-select" style="width: 140px;" [(ngModel)]="wizardRouteStatusFilter">
                      <option value="ALL">All Routes</option>
                      <option value="ACTIVE">Active</option>
                      <option value="INACTIVE">Inactive</option>
                      <option value="DRAFT">Draft</option>
                    </select>
                  </div>
                </div>

                <!-- Hidden Selection Warning Banner -->
                <div *ngIf="isWizardRouteSelectedHidden()" class="alert alert-warning d-flex align-items-center justify-content-between p-3 rounded-3 mb-3">
                  <div class="small">
                    <i class="fa-solid fa-triangle-exclamation me-2"></i>
                    <span>Route <strong>{{ getSelectedRouteName() }}</strong> is currently selected, but hidden by your search or status filter.</span>
                  </div>
                  <button type="button" class="btn btn-sm btn-outline-dark" (click)="clearWizardRouteFilters()">Clear Filters</button>
                </div>

                <!-- Routes Table -->
                <div class="table-responsive">
                  <table class="table route-selection-table align-middle mb-0">
                    <thead>
                      <tr>
                        <th style="width: 44px;"></th>
                        <th style="width: 110px;">Route ID</th>
                        <th>Route Name</th>
                        <th>Origin &rarr; Destination</th>
                        <th>Distance</th>
                        <th>Estimated Duration</th>
                        <th>Status</th>
                        <th style="width: 140px; text-align: right;">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      <ng-container *ngFor="let r of filteredWizardRoutes">
                        <!-- Main Route Row -->
                        <tr class="route-table-row"
                            [ngClass]="{'selected-row': wizardSelectedRouteId === r.id, 'ineligible-row': !isRouteEligibleForSchedule(r)}"
                            (click)="selectWizardRoute(r)">
                          <!-- Radio Selection -->
                          <td class="text-center" (click)="$event.stopPropagation()">
                            <input type="radio"
                                   class="form-check-input route-radio-btn"
                                   name="wizardRouteRadio"
                                   [checked]="wizardSelectedRouteId === r.id"
                                   [disabled]="!isRouteEligibleForSchedule(r)"
                                   (change)="selectWizardRoute(r)">
                          </td>

                          <!-- Route ID -->
                          <td>
                            <span class="route-code-badge">{{ r.routeCode || ('RTE-' + r.id) }}</span>
                          </td>

                          <!-- Route Name -->
                          <td>
                            <span class="route-name-text">{{ r.routeName || 'Unnamed Route' }}</span>
                          </td>

                          <!-- Origin -> Destination -->
                          <td>
                            <span class="route-endpoints-text text-muted">{{ r.origin || '--' }} &rarr; {{ r.destination || '--' }}</span>
                          </td>

                          <!-- Distance -->
                          <td>
                            <span *ngIf="getRouteDistanceNumber(r) > 0" class="text-muted">
                              <i class="fa-solid fa-location-dot me-1 text-primary opacity-75"></i>
                              {{ getRouteDistanceNumber(r) }} km
                            </span>
                            <span *ngIf="getRouteDistanceNumber(r) <= 0" class="text-muted">
                              --
                            </span>
                          </td>

                          <!-- Estimated Duration -->
                          <td>
                            <span *ngIf="getEstimatedRouteDuration(r) !== '--'" class="text-muted">
                              <i class="fa-regular fa-clock me-1 text-primary opacity-75"></i>
                              {{ getEstimatedRouteDuration(r) }}
                            </span>
                            <span *ngIf="getEstimatedRouteDuration(r) === '--'" class="text-muted">
                              --
                            </span>
                          </td>

                          <!-- Status -->
                          <td>
                            <span class="route-status-pill"
                                  [ngClass]="{
                                    'status-pill-active': r.status === 'ACTIVE',
                                    'status-pill-inactive': r.status === 'INACTIVE',
                                    'status-pill-draft': r.status === 'DRAFT'
                                  }">
                              <span class="status-dot"></span>
                              {{ r.status === 'ACTIVE' ? 'Active' : (r.status === 'INACTIVE' ? 'Inactive' : (r.status | titlecase)) }}
                            </span>
                          </td>

                          <!-- Actions -->
                          <td class="text-end" (click)="$event.stopPropagation()">
                            <button type="button"
                                    class="btn btn-view-route-details d-inline-flex align-items-center gap-2"
                                    (click)="toggleRouteDetails(r.id, $event)">
                              <span>View Details</span>
                              <i class="fa-solid" [ngClass]="expandedRouteId === r.id ? 'fa-chevron-up' : 'fa-chevron-down'"></i>
                            </button>
                          </td>
                        </tr>

                        <!-- Inline Expanded Details Row -->
                        <tr *ngIf="expandedRouteId === r.id" class="route-expanded-details-row">
                          <td colspan="8" class="p-0">
                            <div class="route-expanded-details-container p-4">
                              <!-- 3 Stat Summary Cards -->
                              <div class="row g-3 mb-4">
                                <div class="col-md-4">
                                  <div class="route-stat-card p-3 rounded-3 d-flex align-items-center gap-3">
                                    <div class="stat-card-icon-circle">
                                      <i class="fa-solid fa-location-dot text-primary"></i>
                                    </div>
                                    <div>
                                      <div class="stat-card-label">Total Distance</div>
                                      <div class="stat-card-val">{{ getRouteDistanceNumber(r) > 0 ? (getRouteDistanceNumber(r) + ' km') : '--' }}</div>
                                    </div>
                                  </div>
                                </div>
                                <div class="col-md-4">
                                  <div class="route-stat-card p-3 rounded-3 d-flex align-items-center gap-3">
                                    <div class="stat-card-icon-circle">
                                      <i class="fa-regular fa-clock text-primary"></i>
                                    </div>
                                    <div>
                                      <div class="stat-card-label">Estimated Duration</div>
                                      <div class="stat-card-val">{{ getEstimatedRouteDuration(r) }}</div>
                                    </div>
                                  </div>
                                </div>
                                <div class="col-md-4">
                                  <div class="route-stat-card p-3 rounded-3 d-flex align-items-center gap-3">
                                    <div class="stat-card-icon-circle">
                                      <i class="fa-solid fa-code-fork text-primary"></i>
                                    </div>
                                    <div>
                                      <div class="stat-card-label">Total Stops</div>
                                      <div class="stat-card-val">{{ getRouteStopsCount(r) }}</div>
                                    </div>
                                  </div>
                                </div>
                              </div>

                              <!-- Ordered Stops & Boarding/Dropping Timeline Table -->
                              <h6 class="fw-bold text-dark mb-3">Route Stops &amp; Boarding/Dropping</h6>
                              <div class="table-responsive bg-white rounded-3 border">
                                <table class="table stops-timeline-table mb-0 align-middle">
                                  <thead>
                                    <tr>
                                      <th style="width: 50px;">#</th>
                                      <th>Stop Name</th>
                                      <th>Distance from start</th>
                                      <th>Boarding / Dropping</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    <tr *ngFor="let stop of (r.stops || []); let i = index; let isLast = last">
                                      <td class="text-center">
                                        <div class="stop-timeline-wrapper">
                                          <span *ngIf="!isLast" class="stop-timeline-line"></span>
                                          <span class="stop-seq-circle">{{ i + 1 }}</span>
                                        </div>
                                      </td>
                                      <td>
                                        <span class="fw-semibold text-dark">{{ getFormattedStopName(stop, i, (r.stops || []).length) }}</span>
                                      </td>
                                      <td class="text-muted">
                                        {{ stop.distanceKm != null ? (stop.distanceKm + ' km') : '--' }}
                                      </td>
                                      <td>
                                        <span class="stop-bd-pill" [ngClass]="getStopBoardingDroppingType(stop).cssClass">
                                          {{ getStopBoardingDroppingType(stop).label }}
                                        </span>
                                      </td>
                                    </tr>
                                    <tr *ngIf="getRouteStopsCount(r) === 0">
                                      <td colspan="4" class="text-center py-4 text-muted">
                                        <i class="fa-solid fa-circle-exclamation me-1 text-warning"></i>
                                        No stops configured for this route.
                                      </td>
                                    </tr>
                                  </tbody>
                                </table>
                              </div>
                            </div>
                          </td>
                        </tr>
                      </ng-container>

                      <!-- Empty State: No Matching Filter Results -->
                      <tr *ngIf="filteredWizardRoutes.length === 0 && myRoutes.length > 0">
                        <td colspan="8" class="text-center py-5">
                          <i class="fa-solid fa-magnifying-glass text-muted mb-3" style="font-size: 2rem;"></i>
                          <h6 class="fw-bold text-dark mb-1">No matching routes found</h6>
                          <p class="text-muted small mb-3">Try adjusting your search query or status filter.</p>
                          <button type="button" class="btn btn-outline-primary btn-sm rounded-pill" (click)="clearWizardRouteFilters()">
                            Clear Search &amp; Filters
                          </button>
                        </td>
                      </tr>

                      <!-- Empty State: No Routes in Catalogue -->
                      <tr *ngIf="myRoutes.length === 0 && !isLoadingRoutes">
                        <td colspan="8" class="text-center py-5">
                          <i class="fa-solid fa-route text-muted mb-3" style="font-size: 2rem;"></i>
                          <h6 class="fw-bold text-dark mb-1">No routes configured yet</h6>
                          <p class="text-muted small mb-3">You must create at least one route before creating a schedule.</p>
                          <button type="button" class="btn btn-primary btn-sm rounded-pill" (click)="activeNav = 'routes'">
                            Go to My Routes
                          </button>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <!-- Bottom Action Navigation Bar -->
              <div class="d-flex justify-content-between align-items-center mt-4 pt-2">
                <button type="button" class="btn btn-wizard-cancel" (click)="cancelScheduleWizard()">
                  Cancel
                </button>
                <button type="button"
                        class="btn btn-wizard-continue d-inline-flex align-items-center gap-2"
                        [disabled]="!canContinueToStep2()"
                        (click)="continueToAssignBus()">
                  <span>Continue to Assign Bus</span>
                  <i class="fa-solid fa-arrow-right"></i>
                </button>
              </div>
            </div>

            <!-- ==================== STEP 2: ASSIGN BUS (PHASE 6.2B.2) ==================== -->
            <div *ngIf="scheduleWizardStep === 2" class="step2-assign-bus-container">
              <!-- Route Missing Safety Guard -->
              <div *ngIf="!wizardSelectedRoute" class="alert alert-warning d-flex align-items-center justify-content-between p-3 rounded-3 mb-4">
                <div class="d-flex align-items-center gap-2">
                  <i class="fa-solid fa-triangle-exclamation text-warning fs-5"></i>
                  <span>No route selected. You must select an active route before assigning a bus.</span>
                </div>
                <button type="button" class="btn btn-outline-dark btn-sm rounded-pill fw-semibold" (click)="backToSelectRoute()">
                  Return to Step 1: Select Route
                </button>
              </div>

              <!-- Main Selection Panel Card -->
              <div class="card border-0 shadow-sm rounded-4 p-4 mb-4 bg-white">
                <!-- Panel Header (Search and Filter Section) -->
                <div class="wizard-panel-header d-flex flex-wrap justify-content-between align-items-center mb-4">
                  <div class="d-flex align-items-center gap-3">
                    <div class="wizard-panel-icon-circle">
                      <i class="fa-solid fa-bus"></i>
                    </div>
                    <div>
                      <h4 class="wizard-panel-title">Select Bus</h4>
                      <p class="wizard-panel-subtitle">Choose a bus from your fleet to assign to this schedule</p>
                    </div>
                  </div>

                  <!-- Search & Filter Controls -->
                  <div class="wizard-search-filter-controls">
                    <!-- Search Field -->
                    <div class="wizard-search-input-wrapper">
                      <i class="fa-solid fa-magnifying-glass wizard-search-icon"></i>
                      <input type="text"
                             class="wizard-search-input"
                             placeholder="Search by registration number, bus name, or type..."
                             [(ngModel)]="wizardBusSearchQuery">
                    </div>

                    <!-- Bus Type Filter Dropdown -->
                    <div class="wizard-filter-dropdown-wrapper">
                      <select class="wizard-filter-select" [(ngModel)]="wizardBusTypeFilter">
                        <option value="ALL">All Bus Types</option>
                        <option *ngFor="let t of availableBusTypes" [value]="t">{{ t }}</option>
                      </select>
                    </div>
                  </div>
                </div>

                <!-- Selected Bus Hidden by Filter Alert -->
                <div *ngIf="isWizardBusSelectedHidden()" class="alert alert-light border border-primary border-opacity-25 d-flex align-items-center justify-content-between p-2 px-3 rounded-3 mb-3">
                  <div class="small text-muted">
                    <i class="fa-solid fa-circle-check text-primary me-2"></i>
                    Selected Bus: <strong class="text-dark">{{ wizardSelectedBus?.busNumber }}</strong> ({{ wizardSelectedBus?.busName || wizardSelectedBus?.busType }}) is currently hidden by active filters.
                  </div>
                  <button type="button" class="btn btn-link btn-sm p-0 text-primary text-decoration-none fw-semibold" (click)="clearWizardBusFilters()">
                    Reset Filters
                  </button>
                </div>

                <!-- Loading State -->
                <div *ngIf="isLoadingBuses" class="text-center py-5">
                  <div class="spinner-border text-primary mb-3" role="status">
                    <span class="visually-hidden">Loading fleet buses...</span>
                  </div>
                  <p class="text-muted small">Loading available buses from your fleet...</p>
                </div>

                <!-- Error State -->
                <div *ngIf="busLoadError && !isLoadingBuses" class="text-center py-5">
                  <i class="fa-solid fa-triangle-exclamation text-danger fs-1 mb-3"></i>
                  <h6 class="fw-bold text-dark mb-1">Failed to load buses</h6>
                  <p class="text-muted small mb-3">{{ busLoadError }}</p>
                  <button type="button" class="btn btn-outline-primary btn-sm rounded-pill" (click)="loadBuses()">
                    <i class="fa-solid fa-arrow-rotate-right me-1"></i> Retry
                  </button>
                </div>

                <!-- Horizontal Bus List with Shared CSS Grid (Eliminates Horizontal Scrolling) -->
                <div class="bus-list-workspace" *ngIf="!isLoadingBuses && !busLoadError">
                  <!-- Header Strip -->
                  <div class="bus-header-strip">
                    <div class="bus-list-grid">
                      <div class="bus-grid-col-radio"></div>
                      <div class="bus-grid-col-thumb"></div>
                      <div class="bus-header-label">Registration No.</div>
                      <div class="bus-header-label">Bus Name</div>
                      <div class="bus-header-label">Bus Type</div>
                      <div class="bus-header-label text-center">Total Seats</div>
                      <div class="bus-header-label text-center">Seat Layout</div>
                      <div class="bus-header-label text-center">Status</div>
                      <div class="bus-header-label text-end pe-2">Actions</div>
                    </div>
                  </div>

                  <!-- Bus Cards List -->
                  <div class="bus-cards-list">
                    <ng-container *ngFor="let b of filteredWizardBuses">
                      <div class="bus-card-item"
                           [ngClass]="{
                             'selected-card': wizardSelectedBusId === b.id,
                             'ineligible-card': !isBusEligibleForSchedule(b),
                             'expanded-card': expandedBusId === b.id
                           }"
                           (click)="selectWizardBus(b, $event)">
                        <!-- Main Card Row (Always Visible) -->
                        <div class="bus-list-grid">
                          <!-- Radio Control -->
                          <div class="bus-grid-col-radio text-center" (click)="$event.stopPropagation()">
                            <input type="radio"
                                   name="wizardBusSelection"
                                   class="form-check-input bus-radio-control"
                                   [checked]="wizardSelectedBusId === b.id"
                                   [disabled]="!isBusEligibleForSchedule(b)"
                                   [title]="!isBusEligibleForSchedule(b) ? 'Inactive buses cannot be assigned' : 'Select bus'"
                                   (change)="selectWizardBus(b, $event)">
                          </div>

                          <!-- Bus Thumbnail -->
                          <div class="bus-grid-col-thumb text-center">
                            <img [src]="getBusThumbnail(b)"
                                 class="bus-row-thumbnail"
                                 [alt]="b.busNumber"
                                 (error)="handleImageError($event)">
                          </div>

                          <!-- Registration No. -->
                          <div class="bus-grid-col-reg">
                            <span class="bus-reg-no-text">{{ b.busNumber }}</span>
                          </div>

                          <!-- Bus Name -->
                          <div class="bus-grid-col-name">
                            <span class="bus-name-text" [title]="b.busName || (b.operatorName ? b.operatorName + ' ' + (b.model || b.busType) : '')">
                              {{ b.busName || (b.operatorName ? b.operatorName + ' ' + (b.model || b.busType) : 'Not available') }}
                            </span>
                          </div>

                          <!-- Bus Type Pill -->
                          <div class="bus-grid-col-type">
                            <span class="bus-type-badge-pill"
                                  [ngClass]="b.facilityType === 'NON_AC' || (b.busType && b.busType.toLowerCase().includes('non-ac')) ? 'type-pill-non-ac' : 'type-pill-ac'">
                              {{ b.busType }}
                            </span>
                          </div>

                          <!-- Total Seats -->
                          <div class="bus-grid-col-seats text-center">
                            <span class="bus-seats-count">{{ b.totalSeats || b.capacity || 0 }}</span>
                          </div>

                          <!-- Seat Layout -->
                          <div class="bus-grid-col-layout text-center">
                            <span class="bus-layout-summary">{{ getBusSeatLayoutSummary(b) }}</span>
                          </div>

                          <!-- Status -->
                          <div class="bus-grid-col-status text-center">
                            <span class="bus-status-badge-pill"
                                  [ngClass]="isBusEligibleForSchedule(b) ? 'status-pill-active' : 'status-pill-inactive'">
                              <span class="status-indicator-dot"></span>
                              {{ isBusEligibleForSchedule(b) ? 'Active' : 'Inactive' }}
                            </span>
                          </div>

                          <!-- Actions -->
                          <div class="bus-grid-col-actions" (click)="$event.stopPropagation()">
                            <button type="button"
                                    class="btn-bus-action-details"
                                    (click)="toggleBusDetails(b.id, $event)">
                              <span>View Details</span>
                            </button>
                            <button type="button"
                                    class="btn-bus-action-toggle"
                                    (click)="toggleBusDetails(b.id, $event)"
                                    [attr.aria-label]="expandedBusId === b.id ? 'Collapse details' : 'Expand details'">
                              <i class="fa-solid" [ngClass]="expandedBusId === b.id ? 'fa-chevron-up' : 'fa-chevron-down'"></i>
                            </button>
                          </div>
                        </div>

                        <!-- Inline Expanded Details Panel (Directly Beneath Main Row Inside Same Card) -->
                        <div *ngIf="expandedBusId === b.id" class="bus-expanded-panel" (click)="$event.stopPropagation()">
                          <h6 class="bus-expanded-title">Bus Information</h6>

                          <div class="bus-expanded-content">
                            <!-- Left: Bus Photo -->
                            <div class="bus-expanded-left">
                              <div class="bus-photo-frame">
                                <img [src]="getBusThumbnail(b)"
                                     [alt]="b.busNumber"
                                     class="bus-photo-img"
                                     (error)="handleImageError($event)">
                              </div>
                            </div>

                            <!-- Middle: Core Specifications Box -->
                            <div class="bus-expanded-middle">
                              <div class="bus-core-specs-box">
                                <div class="bus-spec-row">
                                  <span class="spec-label">Registration No.</span>
                                  <span class="spec-val-bold">{{ b.busNumber }}</span>
                                </div>
                                <div class="bus-spec-row">
                                  <span class="spec-label">Bus Name</span>
                                  <span class="spec-val-semibold">{{ b.busName || (b.operatorName ? b.operatorName + ' ' + (b.model || b.busType) : 'Not available') }}</span>
                                </div>
                                <div class="bus-spec-row">
                                  <span class="spec-label">Bus Type</span>
                                  <span class="spec-val">{{ b.busType }}</span>
                                </div>
                                <div class="bus-spec-row">
                                  <span class="spec-label">Total Seats</span>
                                  <span class="spec-val-bold">{{ getBusTotalSeatsFormatted(b) }}</span>
                                </div>
                              </div>
                            </div>

                            <!-- Vertical Divider Line -->
                            <div class="bus-expanded-divider"></div>

                            <!-- Right: Additional Specifications -->
                            <div class="bus-expanded-right">
                              <div class="bus-specs-list">
                                <div class="bus-spec-row">
                                  <span class="spec-label">Manufacturer</span>
                                  <span class="spec-val">{{ b.manufacturer || getDerivedManufacturer(b) }}</span>
                                </div>
                                <div class="bus-spec-row">
                                  <span class="spec-label">Model</span>
                                  <span class="spec-val">{{ b.model || 'Not available' }}</span>
                                </div>
                                <div class="bus-spec-row">
                                  <span class="spec-label">Year</span>
                                  <span class="spec-val">{{ b.yearOfManufacturing || b.modelYear || 'Not available' }}</span>
                                </div>
                                <div class="bus-spec-row">
                                  <span class="spec-label">Fuel Type</span>
                                  <span class="spec-val">{{ b.fuelType || 'Diesel' }}</span>
                                </div>
                                <div class="bus-spec-row bus-amenities-row">
                                  <span class="spec-label">Amenities</span>
                                  <div class="bus-amenities-wrap">
                                    <span *ngFor="let amenity of getBusAmenitiesList(b)" class="amenity-item">
                                      <i [class]="getAmenityIcon(amenity)" class="amenity-icon"></i>
                                      <span>{{ amenity }}</span>
                                    </span>
                                    <span *ngIf="getBusAmenitiesList(b).length === 0" class="text-muted small">None</span>
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </ng-container>

                    <!-- Empty State: No Matching Results -->
                    <div *ngIf="filteredWizardBuses.length === 0 && myBuses.length > 0" class="text-center py-5 bg-white rounded-3 border">
                      <i class="fa-solid fa-magnifying-glass text-muted mb-3" style="font-size: 2rem;"></i>
                      <h6 class="fw-bold text-dark mb-1">No matching buses found</h6>
                      <p class="text-muted small mb-3">Try adjusting your search query or bus type filter.</p>
                      <button type="button" class="btn btn-outline-primary btn-sm rounded-pill" (click)="clearWizardBusFilters()">
                        Clear Search &amp; Filters
                      </button>
                    </div>

                    <!-- Empty State: No Buses in Fleet -->
                    <div *ngIf="myBuses.length === 0 && !isLoadingBuses" class="text-center py-5 bg-white rounded-3 border">
                      <i class="fa-solid fa-bus text-muted mb-3" style="font-size: 2rem;"></i>
                      <h6 class="fw-bold text-dark mb-1">No buses registered in your fleet</h6>
                      <p class="text-muted small mb-3">You must register at least one bus before scheduling a journey.</p>
                      <button type="button" class="btn btn-primary btn-sm rounded-pill" (click)="activeNav = 'buses'">
                        Go to Fleet / Buses
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Bottom Action Navigation Bar -->
              <div class="d-flex justify-content-between align-items-center mt-4 pt-2">
                <button type="button" class="btn btn-wizard-back" (click)="backToSelectRoute()">
                  Back
                </button>
                <button type="button"
                        class="btn btn-wizard-continue d-inline-flex align-items-center gap-2"
                        [disabled]="!canContinueToStep3()"
                        (click)="continueToConfigureTimings()">
                  <span>Continue to Configure Timings</span>
                  <i class="fa-solid fa-arrow-right"></i>
                </button>
              </div>
            </div>

            <!-- ==================== STEP 3: CONFIGURE TIMINGS (PHASE 6.3) ==================== -->
            <div *ngIf="scheduleWizardStep === 3" class="step3-configure-timings-container mt-4">


              <!-- Row 2: Schedule Conflict Detected Alert Banner (Conditional) -->
              <div *ngIf="timingConflict" class="schedule-conflict-banner mb-4 d-flex align-items-center justify-content-between p-3 rounded-3">
                <div class="d-flex align-items-start gap-3">
                  <div class="conflict-icon-circle text-danger fs-4 mt-1">
                    <i class="fa-solid fa-circle-exclamation"></i>
                  </div>
                  <div>
                    <h6 class="fw-bold text-danger mb-1">Schedule Conflict Detected</h6>
                    <p class="text-danger small mb-0">{{ timingConflict.message }}</p>
                  </div>
                </div>
                <button type="button" class="btn btn-outline-danger btn-sm rounded-pill fw-semibold px-3 text-nowrap ms-3" (click)="viewConflictingSchedule()">
                  View Conflicting Schedule
                </button>
              </div>

              <!-- Row 3: Trip Start, Trip End, and Journey Metrics Summary -->
              <div class="row g-3 mb-4 align-items-stretch">
                <!-- Trip Start (Departure from Origin) -->
                <div class="col-lg-3 col-md-6">
                  <div class="card trip-input-card h-100 p-3 rounded-4 border-0 shadow-sm bg-white">
                    <label class="trip-input-label fw-semibold text-dark small mb-2">Trip Start (Departure from Origin)</label>
                    <div class="row g-2">
                      <div class="col-7">
                        <div class="timing-input-group d-flex align-items-center px-2 py-1.5 rounded-3 border bg-light">
                          <input type="date" class="form-control form-control-sm border-0 bg-transparent p-0 fw-semibold text-dark w-100"
                                 [(ngModel)]="wizardTripStartDate"
                                 (change)="onTripTimingChange()">
                        </div>
                      </div>
                      <div class="col-5">
                        <div class="timing-input-group d-flex align-items-center px-2 py-1.5 rounded-3 border bg-light">
                          <input type="time" class="form-control form-control-sm border-0 bg-transparent p-0 fw-semibold text-dark w-100"
                                 [(ngModel)]="wizardTripStartTime"
                                 (change)="onTripTimingChange()">
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Trip End (Arrival at Destination) -->
                <div class="col-lg-3 col-md-6">
                  <div class="card trip-input-card h-100 p-3 rounded-4 border-0 shadow-sm bg-white">
                    <label class="trip-input-label fw-semibold text-dark small mb-2">Trip End (Arrival at Destination)</label>
                    <div class="row g-2">
                      <div class="col-7">
                        <div class="timing-input-group d-flex align-items-center px-2 py-1.5 rounded-3 border bg-light">
                          <input type="date" class="form-control form-control-sm border-0 bg-transparent p-0 fw-semibold text-dark w-100"
                                 [(ngModel)]="wizardTripEndDate"
                                 (change)="onTripTimingChange()">
                        </div>
                      </div>
                      <div class="col-5">
                        <div class="timing-input-group d-flex align-items-center px-2 py-1.5 rounded-3 border bg-light">
                          <input type="time" class="form-control form-control-sm border-0 bg-transparent p-0 fw-semibold text-dark w-100"
                                 [(ngModel)]="wizardTripEndTime"
                                 (change)="onTripTimingChange()">
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Horizontal Journey Metrics Summary Card -->
                <div class="col-lg-6 col-md-12">
                  <div class="card journey-summary-card h-100 p-3 px-4 rounded-4 border-0 shadow-sm">
                    <div class="d-flex align-items-center justify-content-between h-100">
                      <!-- Total Journey Duration -->
                      <div class="d-flex align-items-center gap-3">
                        <div class="summary-metric-icon text-primary bg-primary bg-opacity-10 rounded-circle d-flex align-items-center justify-content-center" style="width: 40px; height: 40px;">
                          <i class="fa-regular fa-clock fs-5"></i>
                        </div>
                        <div>
                          <div class="text-muted small">Total Journey Duration</div>
                          <div class="fw-bold text-dark fs-6">{{ wizardJourneyDurationDisplay }}</div>
                        </div>
                      </div>

                      <!-- Total Distance -->
                      <div class="d-flex align-items-center gap-3 border-start ps-4">
                        <div class="summary-metric-icon text-primary bg-primary bg-opacity-10 rounded-circle d-flex align-items-center justify-content-center" style="width: 40px; height: 40px;">
                          <i class="fa-solid fa-route fs-5"></i>
                        </div>
                        <div>
                          <div class="text-muted small">Total Distance</div>
                          <div class="fw-bold text-dark fs-6">{{ wizardTotalDistanceKm }} km</div>
                        </div>
                      </div>

                      <!-- Total Stops -->
                      <div class="d-flex align-items-center gap-3 border-start ps-4">
                        <div class="summary-metric-icon text-primary bg-primary bg-opacity-10 rounded-circle d-flex align-items-center justify-content-center" style="width: 40px; height: 40px;">
                          <i class="fa-solid fa-circle-nodes fs-5"></i>
                        </div>
                        <div>
                          <div class="text-muted small">Total Stops</div>
                          <div class="d-flex align-items-baseline gap-1">
                            <span class="fw-bold text-dark fs-6">{{ wizardTotalStopsCount }}</span>
                            <span class="text-muted small" *ngIf="wizardIntermediateStopsCount > 0">({{ wizardIntermediateStopsCount }} intermediate)</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Row 4: Stop-wise Boarding & Dropping Points Table Card -->
              <div class="card border-0 shadow-sm rounded-4 mb-4 bg-white stop-timings-card overflow-hidden">
                <!-- Header with Title, Subtitle, and Toggle -->
                <div class="p-3 px-4 border-bottom d-flex justify-content-between align-items-center bg-white">
                  <div>
                    <h6 class="fw-bold text-dark mb-0">Stop-wise Boarding &amp; Dropping Points</h6>
                    <p class="text-muted small mb-0">Configure arrival, departure and halt times for each boarding and dropping point at every stop</p>
                  </div>
                  <div class="form-check form-switch d-flex align-items-center gap-2 mb-0">
                    <input class="form-check-input cursor-pointer" type="checkbox" id="stopSummaryToggle" [(ngModel)]="showStopSummaryOnly">
                    <label class="form-check-label text-muted small fw-semibold cursor-pointer user-select-none" for="stopSummaryToggle">
                      Show stop summary only
                    </label>
                  </div>
                </div>

                <!-- Table Container -->
                <div class="table-responsive">
                  <table class="table stop-timings-table mb-0 align-middle">
                    <thead>
                      <tr>
                        <th style="width: 45px;" class="text-center">#</th>
                        <th style="width: 220px;">Stop Name</th>
                        <th style="width: 150px;">Distance from Origin</th>
                        <th style="width: 260px;">Boarding / Dropping Points</th>
                        <th style="width: 230px;">Arrival Date &amp; Time</th>
                        <th style="width: 230px;">Departure Date &amp; Time</th>
                        <th style="width: 120px;" class="text-center">Halt Duration</th>
                      </tr>
                    </thead>
                    <tbody>
                      <!-- Iterate each stop group -->
                      <ng-container *ngFor="let stop of wizardStopTimings; let stopIdx = index">
                        <!-- Stop Header / Summary Row when showStopSummaryOnly is true -->
                        <tr *ngIf="showStopSummaryOnly" class="stop-summary-row border-bottom">
                          <td class="text-center">
                            <div class="stop-number-circle">{{ stopIdx + 1 }}</div>
                          </td>
                          <td>
                            <div class="fw-bold text-dark">{{ stop.stopName }}</div>
                          </td>
                          <td>
                            <span class="text-muted">{{ stop.distanceKm }} km</span>
                          </td>
                          <td>
                            <span class="badge px-2.5 py-1 rounded-pill small fw-medium"
                                  [ngClass]="stop.stopType === 'ORIGIN' ? 'stop-badge-boarding' : (stop.stopType === 'DESTINATION' ? 'stop-badge-dropping' : 'stop-badge-both')">
                              {{ stop.points.length }} {{ stop.stopType === 'ORIGIN' ? 'Boarding Points' : (stop.stopType === 'DESTINATION' ? 'Dropping Points' : 'Boarding & Dropping Points') }}
                            </span>
                          </td>
                          <td>
                            <div *ngIf="stop.stopType === 'ORIGIN'" class="timing-disabled-box">&mdash;</div>
                            <div *ngIf="stop.stopType !== 'ORIGIN'" class="timing-display-box d-flex align-items-center gap-1.5 px-2.5 py-1.5 rounded-2 border bg-light small">
                              <i class="fa-regular fa-calendar text-muted small"></i>
                              <span class="fw-semibold text-dark">{{ formatDisplayDate(stop.summaryArrivalDate) }}</span>
                              <i class="fa-regular fa-clock text-muted small ms-1"></i>
                              <span class="fw-semibold text-dark">{{ formatDisplayTime(stop.summaryArrivalTime) }}</span>
                            </div>
                          </td>
                          <td>
                            <div *ngIf="stop.stopType === 'DESTINATION'" class="timing-disabled-box">&mdash;</div>
                            <div *ngIf="stop.stopType !== 'DESTINATION'" class="timing-display-box d-flex align-items-center gap-1.5 px-2.5 py-1.5 rounded-2 border bg-light small">
                              <i class="fa-regular fa-calendar text-muted small"></i>
                              <span class="fw-semibold text-dark">{{ formatDisplayDate(stop.summaryDepartureDate) }}</span>
                              <i class="fa-regular fa-clock text-muted small ms-1"></i>
                              <span class="fw-semibold text-dark">{{ formatDisplayTime(stop.summaryDepartureTime) }}</span>
                            </div>
                          </td>
                          <td class="text-center">
                            <div *ngIf="stop.stopType === 'ORIGIN' || stop.stopType === 'DESTINATION'" class="timing-disabled-box">&mdash;</div>
                            <div *ngIf="stop.stopType === 'INTERMEDIATE'" class="halt-duration-pill py-1 px-2 rounded-2 border bg-light small fw-semibold">
                              {{ stop.summaryHaltDisplay || '&mdash;' }}
                            </div>
                          </td>
                        </tr>

                        <!-- Normal Detailed Rows (showStopSummaryOnly is false, or stop is expanded) -->
                        <ng-container *ngIf="!showStopSummaryOnly">
                          <tr *ngFor="let pt of stop.points; let ptIdx = index" [ngClass]="{'stop-first-point-row': ptIdx === 0, 'stop-inner-point-row': ptIdx > 0}">
                            <!-- Stop Number (Rowspan for all points in this stop) -->
                            <td *ngIf="ptIdx === 0" [attr.rowspan]="stop.points.length" class="text-center align-middle border-bottom">
                              <div class="stop-number-circle">{{ stopIdx + 1 }}</div>
                            </td>

                            <!-- Stop Name (Rowspan for all points in this stop) -->
                            <td *ngIf="ptIdx === 0" [attr.rowspan]="stop.points.length" class="align-middle border-bottom">
                              <div class="fw-bold text-dark">{{ stop.stopName }}</div>
                            </td>

                            <!-- Distance (Rowspan for all points in this stop) -->
                            <td *ngIf="ptIdx === 0" [attr.rowspan]="stop.points.length" class="align-middle border-bottom">
                              <span class="text-muted">{{ stop.distanceKm }} km</span>
                            </td>

                            <!-- Point Name & Point Badge -->
                            <td class="border-bottom">
                              <div class="d-flex align-items-center justify-content-between pe-3">
                                <div class="d-flex align-items-center gap-2">
                                  <i class="fa-solid fa-location-dot"
                                     [ngClass]="pt.pointType === 'BOARDING' ? 'text-success' : (pt.pointType === 'DROPPING' ? 'text-warning' : 'text-primary')"></i>
                                  <span class="fw-semibold text-dark small">{{ pt.pointName }}</span>
                                </div>
                                <span class="badge px-2.5 py-0.5 rounded-pill small fw-medium"
                                      [ngClass]="pt.pointType === 'BOARDING' ? 'stop-badge-boarding' : (pt.pointType === 'DROPPING' ? 'stop-badge-dropping' : 'stop-badge-both')">
                                  {{ pt.pointBadge }}
                                </span>
                              </div>
                            </td>

                            <!-- Arrival Date & Time -->
                            <td class="border-bottom">
                              <!-- Disabled for origin stop points -->
                              <div *ngIf="stop.stopType === 'ORIGIN'" class="timing-disabled-box">&mdash;</div>

                              <!-- Editable for intermediate & destination stop points -->
                              <div *ngIf="stop.stopType !== 'ORIGIN'" class="timing-pill-input d-flex align-items-center gap-1.5 px-2 py-1 rounded-2 border bg-light">
                                <input type="date" class="form-control form-control-sm border-0 bg-transparent p-0 timing-date-input"
                                       [(ngModel)]="pt.arrivalDate"
                                       (change)="onPointTimingChange(pt, stop)">
                                <input type="time" class="form-control form-control-sm border-0 bg-transparent p-0 timing-time-input"
                                       [(ngModel)]="pt.arrivalTime"
                                       (change)="onPointTimingChange(pt, stop)">
                              </div>
                            </td>

                            <!-- Departure Date & Time -->
                            <td class="border-bottom">
                              <!-- Disabled for destination stop points -->
                              <div *ngIf="stop.stopType === 'DESTINATION'" class="timing-disabled-box">&mdash;</div>

                              <!-- Editable for origin & intermediate stop points -->
                              <div *ngIf="stop.stopType !== 'DESTINATION'" class="timing-pill-input d-flex align-items-center gap-1.5 px-2 py-1 rounded-2 border bg-light">
                                <input type="date" class="form-control form-control-sm border-0 bg-transparent p-0 timing-date-input"
                                       [(ngModel)]="pt.departureDate"
                                       (change)="onPointTimingChange(pt, stop)">
                                <input type="time" class="form-control form-control-sm border-0 bg-transparent p-0 timing-time-input"
                                       [(ngModel)]="pt.departureTime"
                                       (change)="onPointTimingChange(pt, stop)">
                              </div>
                            </td>

                            <!-- Halt Duration -->
                            <td class="text-center border-bottom">
                              <div *ngIf="stop.stopType === 'ORIGIN' || stop.stopType === 'DESTINATION'" class="timing-disabled-box">&mdash;</div>
                              <div *ngIf="stop.stopType === 'INTERMEDIATE'"
                                   class="halt-duration-pill py-1 px-2 rounded-2 border small fw-semibold"
                                   [ngClass]="pt.haltMinutes < 0 ? 'bg-danger bg-opacity-10 text-danger border-danger' : 'bg-light text-dark'">
                                {{ pt.haltDurationDisplay || '&mdash;' }}
                              </div>
                            </td>

                          </tr>
                        </ng-container>
                      </ng-container>
                    </tbody>
                  </table>
                </div>
              </div>

              <!-- Informational Bottom Note -->
              <div class="alert alert-info border-0 d-flex align-items-center gap-2 rounded-3 text-primary bg-primary bg-opacity-10 py-2.5 px-3 small mb-4">
                <i class="fa-solid fa-circle-info fs-6"></i>
                <span>Please ensure that all arrival and departure times are in chronological order and include the correct date for overnight stops.</span>
              </div>

              <!-- Validation Error Warning if any -->
              <div *ngIf="timingValidationError" class="alert alert-danger border-0 d-flex align-items-center gap-2 rounded-3 text-danger bg-danger bg-opacity-10 py-2.5 px-3 small mb-4">
                <i class="fa-solid fa-triangle-exclamation fs-6"></i>
                <span class="fw-semibold">{{ timingValidationError }}</span>
              </div>

              <!-- Bottom Action Navigation Bar -->
              <div class="d-flex justify-content-between align-items-center pt-2 mb-4">
                <button type="button" class="btn btn-wizard-back" (click)="backToAssignBus()">
                  Back
                </button>
                <button type="button"
                        class="btn btn-wizard-continue d-inline-flex align-items-center gap-2"
                        [disabled]="!canContinueToStep4()"
                        (click)="continueToConfigureFares()">
                  <span>Continue to Configure Fares</span>
                  <i class="fa-solid fa-arrow-right"></i>
                </button>
              </div>
            </div>

            <!-- Conflicting Schedule Modal -->
            <div *ngIf="showConflictingScheduleModal && timingConflict" class="modal-backdrop fade show" style="background-color: rgba(0, 0, 0, 0.5); z-index: 1050;"></div>
            <div *ngIf="showConflictingScheduleModal && timingConflict" class="modal fade show d-block" tabindex="-1" style="z-index: 1055;">
              <div class="modal-dialog modal-dialog-centered" style="max-width: 580px;">
                <div class="modal-content border-0 shadow-lg rounded-4">
                  <div class="modal-header border-bottom pb-3 px-4 pt-4">
                    <div class="d-flex align-items-center gap-2">
                      <div class="rounded-circle bg-danger bg-opacity-10 p-2 text-danger d-flex align-items-center justify-content-center" style="width: 38px; height: 38px;">
                        <i class="fa-solid fa-triangle-exclamation"></i>
                      </div>
                      <div>
                        <h5 class="modal-title fw-bold text-dark mb-0">Conflicting Schedule Details</h5>
                        <span class="text-muted small">Schedule #{{ timingConflict.conflictingScheduleId }}</span>
                      </div>
                    </div>
                    <button type="button" class="btn-close" (click)="closeConflictingScheduleModal()"></button>
                  </div>
                  <div class="modal-body px-4 py-3">
                    <div class="p-3 bg-danger bg-opacity-10 rounded-3 text-danger small mb-3">
                      <strong>Operational Overlap Warning:</strong>
                      <div class="mt-1">{{ timingConflict.message }}</div>
                    </div>
                    <div class="row g-3">
                      <div class="col-6">
                        <div class="text-muted small">Conflicting Service Route</div>
                        <div class="fw-bold text-dark">{{ timingConflict.origin }} &rarr; {{ timingConflict.destination }}</div>
                      </div>
                      <div class="col-6">
                        <div class="text-muted small">Assigned Bus</div>
                        <div class="fw-bold text-dark">{{ timingConflict.busNumber }} <span class="fw-normal text-muted" *ngIf="timingConflict.busName">({{ timingConflict.busName }})</span></div>
                      </div>
                      <div class="col-6">
                        <div class="text-muted small">Departure Time</div>
                        <div class="fw-bold text-dark">{{ timingConflict.existingDepartureFormatted }}</div>
                      </div>
                      <div class="col-6">
                        <div class="text-muted small">Arrival Time</div>
                        <div class="fw-bold text-dark">{{ timingConflict.existingArrivalFormatted }}</div>
                      </div>
                      <div class="col-12">
                        <div class="text-muted small">Required Turnaround Buffer</div>
                        <div class="fw-bold text-primary">{{ timingConflict.turnaroundBufferMinutes || 60 }} minutes turnaround buffer</div>
                      </div>
                    </div>
                  </div>
                  <div class="modal-footer border-top px-4 py-3">
                    <button type="button" class="btn btn-outline-secondary rounded-pill px-3" (click)="closeConflictingScheduleModal()">Close</button>
                    <button type="button" class="btn btn-primary rounded-pill px-3" (click)="backToAssignBus(); closeConflictingScheduleModal()">Assign Different Bus</button>
                  </div>
                </div>
              </div>
            </div>

            <!-- ==================== STEP 4 CONTENT: CONFIGURE FARES (PHASE 6.4) ==================== -->
            <div *ngIf="scheduleWizardStep === 4" class="step4-configure-fares-container mt-4">

              <!-- SECTION 1: Configure Segment Fares Card -->
              <div class="card border-0 shadow-sm rounded-4 mb-4 bg-white fare-config-card overflow-hidden">
                <div class="p-3 px-4 border-bottom bg-white">
                  <div class="d-flex align-items-center gap-2 mb-1">
                    <div class="fare-step-badge">1</div>
                    <h5 class="fw-bold text-dark mb-0 fs-6">Configure Segment Fares</h5>
                    <span class="fare-pill-tag">Base fares for consecutive route segments</span>
                  </div>
                  <p class="text-muted small mb-0 ps-4 ms-2">
                    Set the base fare for each adjacent stop-to-stop segment. These are used to calculate journey fares, which you can modify if needed.
                  </p>
                </div>

                <div class="p-4">
                  <div class="row g-4 align-items-start">
                    <!-- Segment Table Column -->
                    <div class="col-lg-8 col-12">
                      <div class="table-responsive">
                        <table class="table fare-table mb-0 align-middle">
                          <thead>
                            <tr>
                              <th style="width: 45px;" class="text-center">#</th>
                              <th>From Stop</th>
                              <th>To Stop</th>
                              <th style="width: 130px;" class="text-center">Distance (km)</th>
                              <th style="width: 150px;" class="text-center">Segment Fare (&#8377;)</th>
                              <th style="width: 80px;" class="text-center">Actions</th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr *ngFor="let seg of wizardSegmentFares; let segIdx = index" class="border-bottom">
                              <td class="text-center fw-semibold text-muted">{{ seg.segmentIndex }}</td>
                              <td>
                                <span [ngClass]="{'fw-bold text-dark': segIdx === 0}">{{ seg.fromStopName }}</span>
                              </td>
                              <td>
                                <span [ngClass]="{'fw-bold text-dark': segIdx === wizardSegmentFares.length - 1}">{{ seg.toStopName }}</span>
                              </td>
                              <td class="text-center text-muted">{{ seg.distanceKm }}</td>
                              <td class="text-center">
                                <input type="number" min="1" step="1"
                                       class="fare-input-box"
                                       [ngClass]="{'border-danger': seg.fareError}"
                                       [(ngModel)]="seg.fare"
                                       (ngModelChange)="onSegmentFareChange(seg)">
                                <div *ngIf="seg.fareError" class="text-danger small mt-1" style="font-size: 11px;">
                                  {{ seg.fareError }}
                                </div>
                              </td>
                              <td class="text-center text-muted">&mdash;</td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                    </div>

                    <!-- Right Column: How it works? Card -->
                    <div class="col-lg-4 col-12">
                      <div class="how-it-works-panel">
                        <div class="d-flex align-items-center gap-2 mb-2">
                          <i class="fa-solid fa-circle-info text-primary fs-5"></i>
                          <h6 class="fw-bold text-primary mb-0">How it works?</h6>
                        </div>
                        <ul class="list-unstyled mb-0 d-flex flex-column gap-2 mt-2">
                          <li>These are fares between consecutive stops only.</li>
                          <li>Used to auto-calculate journey fares for all valid boarding stop &rarr; dropping stop combinations.</li>
                          <li>You can modify the final journey fares in the next section.</li>
                          <li>Changing a segment fare will automatically update all non-overridden journey fares.</li>
                        </ul>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- SECTION 2: Configure Journey Fares Card -->
              <div class="card border-0 shadow-sm rounded-4 mb-4 bg-white fare-config-card overflow-hidden">
                <div class="p-3 px-4 border-bottom bg-white">
                  <div class="d-flex align-items-center gap-2 mb-1">
                    <div class="fare-step-badge">2</div>
                    <h5 class="fw-bold text-dark mb-0 fs-6">Configure Journey Fares</h5>
                    <span class="fare-pill-tag">Auto-calculated (editable)</span>
                  </div>
                  <p class="text-muted small mb-0 ps-4 ms-2">
                    Review and modify fares for all valid boarding stop to dropping stop combinations. Initial fares are calculated from segment fares.
                  </p>
                </div>

                <div class="p-4">
                  <div class="table-responsive">
                    <table class="table fare-table mb-0 align-middle">
                      <thead>
                        <tr>
                          <th style="width: 45px;" class="text-center">#</th>
                          <th style="width: 200px;">From Stop (Boarding)</th>
                          <th style="width: 200px;">To Stop (Dropping)</th>
                          <th style="width: 150px;" class="text-center">Journey Distance (km)</th>
                          <th style="width: 150px;" class="text-center">
                            Calculated Fare (&#8377;)
                            <i class="fa-solid fa-circle-question text-primary opacity-75 ms-1" title="Sum of consecutive segment fares"></i>
                          </th>
                          <th style="width: 150px;" class="text-center">
                            Operator Fare (&#8377;)
                            <i class="fa-solid fa-circle-question text-primary opacity-75 ms-1" title="Final fare applied for this journey"></i>
                          </th>
                          <th style="width: 110px;" class="text-center">Status</th>
                          <th style="width: 100px;" class="text-center">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr *ngFor="let journey of wizardJourneyFares" class="border-bottom">
                          <td class="text-center fw-semibold text-muted">{{ journey.journeyIndex }}</td>
                          <td>
                            <span [ngClass]="{'fw-bold text-dark': journey.fromStopOrder === 0}">{{ journey.fromStopName }}</span>
                          </td>
                          <td>
                            <span [ngClass]="{'fw-bold text-dark': journey.toStopOrder === wizardStopTimings.length - 1}">{{ journey.toStopName }}</span>
                          </td>
                          <td class="text-center text-muted">{{ journey.distanceKm }}</td>
                          <td class="text-center fw-semibold text-dark">
                            {{ journey.calculatedFare | number:'1.0-0' }}
                          </td>
                          <td class="text-center">
                            <input type="number" min="1" step="1"
                                   class="fare-input-box"
                                   [ngClass]="{'border-danger': journey.fareError}"
                                   [(ngModel)]="journey.operatorFare"
                                   (ngModelChange)="onJourneyFareChange(journey)">
                            <div *ngIf="journey.fareError" class="text-danger small mt-1" style="font-size: 11px;">
                              {{ journey.fareError }}
                            </div>
                          </td>
                          <td class="text-center">
                            <span *ngIf="journey.status === 'Auto'" class="status-badge-auto">Auto</span>
                            <span *ngIf="journey.status === 'Overridden'" class="status-badge-overridden">Overridden</span>
                          </td>
                          <td class="text-center">
                            <span *ngIf="!journey.isOverridden" class="text-muted">&mdash;</span>
                            <button *ngIf="journey.isOverridden"
                                    type="button"
                                    class="btn-reset-fare"
                                    (click)="resetJourneyFare(journey)">
                              <i class="fa-solid fa-rotate-left"></i> Reset
                            </button>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>

              <!-- SECTION 3: Important Notes Banner -->
              <div class="important-notes-banner mb-4 d-flex align-items-start gap-4">
                <div class="important-notes-header d-flex align-items-center gap-2 flex-shrink-0">
                  <i class="fa-solid fa-circle-info fs-5" style="color: #ea580c;"></i>
                  <span class="fw-bold" style="color: #ea580c; font-size: 14px; white-space: nowrap;">Important notes</span>
                </div>
                <div class="important-notes-content flex-grow-1">
                  <div class="row g-4">
                    <div class="col-md-6 col-12">
                      <ul class="important-notes-list mb-0">
                        <li>Journey fares are initially calculated by summing the applicable segment fares.</li>
                        <li>You can modify any journey fare. Manually overridden fares will not change when segment fares are updated.</li>
                        <li>Use &ldquo;Reset&rdquo; to restore a journey fare to the auto-calculated value.</li>
                      </ul>
                    </div>
                    <div class="col-md-6 col-12">
                      <ul class="important-notes-list mb-0">
                        <li>All fares must be greater than 0 and follow the allowed fare rules.</li>
                        <li>Fares apply between stops. Multiple boarding/dropping points within a stop use the same fare.</li>
                      </ul>
                    </div>
                  </div>
                </div>
              </div>

              <!-- SECTION 4: Bottom Navigation Buttons -->
              <div class="d-flex justify-content-between align-items-center pt-2 mb-4">
                <button type="button" class="btn btn-outline-secondary px-4 py-2 rounded-3 fw-semibold bg-white" (click)="backToConfigureTimings()">
                  Back
                </button>
                <button type="button" class="btn btn-primary px-4 py-2 rounded-3 fw-semibold d-inline-flex align-items-center gap-2"
                        [disabled]="!isFareConfigurationValid()"
                        (click)="continueToReviewFromFares()">
                  Continue to Review <i class="fa-solid fa-arrow-right"></i>
                </button>
              </div>

            </div>

            <!-- ==================== STEP 5: REVIEW & CREATE ==================== -->
            <div *ngIf="scheduleWizardStep === 5" class="step5-review-container mt-4">

              <!-- Section 1: Route Overview -->
              <div class="card review-section-card mb-3">
                <div class="review-section-header" (click)="toggleReviewSection(1)" role="button" tabindex="0">
                  <div class="d-flex align-items-center gap-2.5">
                    <div class="review-icon-circle">
                      <i class="fa-solid fa-map-location-dot"></i>
                    </div>
                    <span class="review-section-title">1. Route Overview</span>
                    <span class="review-looks-good-badge">
                      <i class="fa-solid fa-check"></i> Looks good
                    </span>
                  </div>
                  <div class="review-header-summary text-muted small mx-auto d-none d-md-block">
                    {{ getReviewRouteSummary() }}
                  </div>
                  <div class="review-header-toggle text-secondary">
                    <i class="fa-solid" [ngClass]="isReviewSectionOpen(1) ? 'fa-chevron-up' : 'fa-chevron-down'"></i>
                  </div>
                </div>
                <!-- Expanded Route Overview Table -->
                <div *ngIf="isReviewSectionOpen(1)" class="review-section-body p-3 pt-0 border-top">
                  <div class="table-responsive mt-3">
                    <table class="table review-table mb-0 align-middle">
                      <thead>
                        <tr>
                          <th style="width: 60px;" class="text-center">#</th>
                          <th>Stop Name</th>
                          <th>Distance from Origin</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr *ngFor="let stop of wizardStopTimings; let idx = index" class="border-bottom">
                          <td class="text-center text-muted fw-semibold">{{ idx + 1 }}</td>
                          <td>
                            <span [class.fw-bold]="idx === 0 || idx === wizardStopTimings.length - 1" class="text-dark">
                              {{ getDisplayStopName(stop, idx) }}
                            </span>
                          </td>
                          <td class="text-muted">{{ stop.distanceKm }} km</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>

              <!-- Section 2: Bus Details -->
              <div class="card review-section-card mb-3">
                <div class="review-section-header" (click)="toggleReviewSection(2)" role="button" tabindex="0">
                  <div class="d-flex align-items-center gap-2.5">
                    <div class="review-icon-circle">
                      <i class="fa-solid fa-bus"></i>
                    </div>
                    <span class="review-section-title">2. Bus Details</span>
                    <span class="review-looks-good-badge">
                      <i class="fa-solid fa-check"></i> Looks good
                    </span>
                  </div>
                  <div class="review-header-summary text-muted small mx-auto d-none d-md-block">
                    {{ getReviewBusSummary() }}
                  </div>
                  <div class="review-header-toggle text-secondary">
                    <i class="fa-solid" [ngClass]="isReviewSectionOpen(2) ? 'fa-chevron-up' : 'fa-chevron-down'"></i>
                  </div>
                </div>
                <!-- Expanded Bus Details -->
                <div *ngIf="isReviewSectionOpen(2)" class="review-section-body p-3 pt-0 border-top">
                  <div class="bus-expanded-content mt-3">
                    <!-- Left: Bus Photo -->
                    <div class="bus-expanded-left">
                      <div class="bus-photo-frame">
                        <img [src]="getBusThumbnail(wizardSelectedBus)"
                             [alt]="wizardSelectedBus?.busNumber || 'Bus'"
                             class="bus-photo-img"
                             (error)="handleImageError($event)">
                      </div>
                    </div>

                    <!-- Middle: Core Specifications Box -->
                    <div class="bus-expanded-middle">
                      <div class="bus-core-specs-box">
                        <div class="bus-spec-row">
                          <span class="spec-label">Registration No.</span>
                          <span class="spec-val-bold">{{ wizardSelectedBus?.busNumber || '--' }}</span>
                        </div>
                        <div class="bus-spec-row">
                          <span class="spec-label">Bus Name</span>
                          <span class="spec-val-semibold">{{ wizardSelectedBus?.busName || (wizardSelectedBus?.operatorName ? wizardSelectedBus?.operatorName + ' ' + (wizardSelectedBus?.model || wizardSelectedBus?.busType) : 'Not available') }}</span>
                        </div>
                        <div class="bus-spec-row">
                          <span class="spec-label">Bus Type</span>
                          <span class="spec-val">{{ wizardSelectedBus?.busType || '--' }}</span>
                        </div>
                        <div class="bus-spec-row">
                          <span class="spec-label">Total Seats</span>
                          <span class="spec-val-bold">{{ wizardSelectedBus ? getBusTotalSeatsFormatted(wizardSelectedBus) : '--' }}</span>
                        </div>
                      </div>
                    </div>

                    <!-- Vertical Divider Line -->
                    <div class="bus-expanded-divider"></div>

                    <!-- Right: Operational Specifications -->
                    <div class="bus-expanded-right">
                      <div class="bus-specs-list">
                        <div class="bus-spec-row">
                          <span class="spec-label">Manufacturer</span>
                          <span class="spec-val">{{ wizardSelectedBus?.manufacturer || (wizardSelectedBus ? getDerivedManufacturer(wizardSelectedBus) : 'Not available') }}</span>
                        </div>
                        <div class="bus-spec-row">
                          <span class="spec-label">Model</span>
                          <span class="spec-val">{{ wizardSelectedBus?.model || 'Not available' }}</span>
                        </div>
                        <div class="bus-spec-row">
                          <span class="spec-label">Year</span>
                          <span class="spec-val">{{ wizardSelectedBus?.yearOfManufacturing || wizardSelectedBus?.modelYear || '2022' }}</span>
                        </div>
                        <div class="bus-spec-row">
                          <span class="spec-label">Fuel Type</span>
                          <span class="spec-val">{{ wizardSelectedBus?.fuelType || 'Diesel' }}</span>
                        </div>
                        <div class="bus-spec-row bus-amenities-row">
                          <span class="spec-label">Amenities</span>
                          <div class="bus-amenities-wrap">
                            <span *ngFor="let amenity of (wizardSelectedBus ? getBusAmenitiesList(wizardSelectedBus) : ['AC', 'Wi-Fi', 'Charging', 'Blanket'])" class="amenity-item">
                              <i [class]="getAmenityIcon(amenity)" class="amenity-icon"></i>
                              <span>{{ amenity }}</span>
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Section 3: Trip Timings & Stop-wise Boarding/Dropping Points -->
              <div class="card review-section-card mb-3">
                <div class="review-section-header" (click)="toggleReviewSection(3)" role="button" tabindex="0">
                  <div class="d-flex align-items-center gap-2.5">
                    <div class="review-icon-circle">
                      <i class="fa-regular fa-clock"></i>
                    </div>
                    <span class="review-section-title">3. Trip Timings &amp; Stop-wise Boarding/Dropping Points</span>
                    <span class="review-looks-good-badge">
                      <i class="fa-solid fa-check"></i> Looks good
                    </span>
                  </div>
                  <div class="review-header-summary text-muted small mx-auto d-none d-md-block">
                    {{ getReviewTimingsSummary() }}
                  </div>
                  <div class="review-header-toggle text-secondary">
                    <i class="fa-solid" [ngClass]="isReviewSectionOpen(3) ? 'fa-chevron-up' : 'fa-chevron-down'"></i>
                  </div>
                </div>
                <!-- Expanded Content: Timings Table (Default Open) -->
                <div *ngIf="isReviewSectionOpen(3)" class="review-section-body p-3 pt-0 border-top">
                  <div class="table-responsive mt-3">
                    <table class="table review-table mb-0 align-middle">
                      <thead>
                        <tr>
                          <th style="width: 50px;" class="text-center">#</th>
                          <th style="width: 190px;">Stop Name</th>
                          <th style="width: 140px;">Distance from Origin</th>
                          <th>Boarding Points (Time)</th>
                          <th>Dropping Points (Time)</th>
                          <th style="width: 110px;" class="text-center">Halt Duration</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr *ngFor="let stop of wizardStopTimings; let idx = index" class="border-bottom">
                          <td class="text-center text-muted fw-semibold">{{ idx + 1 }}</td>
                          <td>
                            <span [class.fw-bold]="idx === 0 || idx === wizardStopTimings.length - 1" class="text-dark">
                              {{ getDisplayStopName(stop, idx) }}
                            </span>
                          </td>
                          <td class="text-muted">{{ stop.distanceKm }} km</td>
                          <td>
                            <div *ngIf="getBoardingPointsForStop(stop).length === 0" class="text-muted">—</div>
                            <div class="d-flex flex-wrap align-items-center gap-3" *ngIf="getBoardingPointsForStop(stop).length > 0">
                              <div *ngFor="let pt of getBoardingPointsForStop(stop)" class="d-inline-flex align-items-center small">
                                <i class="fa-solid fa-location-dot text-success me-1.5"></i>
                                <span class="fw-semibold text-dark">{{ pt.pointName }}</span>
                                <span class="text-muted ms-1">({{ formatDisplayTime(pt.departureTime) }})</span>
                              </div>
                            </div>
                          </td>
                          <td>
                            <div *ngIf="getDroppingPointsForStop(stop).length === 0" class="text-muted">—</div>
                            <div class="d-flex flex-wrap align-items-center gap-3" *ngIf="getDroppingPointsForStop(stop).length > 0">
                              <div *ngFor="let pt of getDroppingPointsForStop(stop)" class="d-inline-flex align-items-center small">
                                <i class="fa-solid fa-location-dot text-success me-1.5"></i>
                                <span class="fw-semibold text-dark">{{ pt.pointName }}</span>
                                <span class="text-muted ms-1">({{ formatDisplayTime(pt.arrivalTime) }})</span>
                              </div>
                            </div>
                          </td>
                          <td class="text-center text-muted">
                            {{ getHaltDurationForStop(stop, idx) }}
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>

              <!-- Section 4: Segment Fares (Base Fares) -->
              <div class="card review-section-card mb-3">
                <div class="review-section-header" (click)="toggleReviewSection(4)" role="button" tabindex="0">
                  <div class="d-flex align-items-center gap-2.5">
                    <div class="review-icon-circle">
                      <i class="fa-solid fa-indian-rupee-sign"></i>
                    </div>
                    <span class="review-section-title">4. Segment Fares (Base Fares)</span>
                    <span class="review-looks-good-badge">
                      <i class="fa-solid fa-check"></i> Looks good
                    </span>
                  </div>
                  <div class="review-header-summary text-muted small mx-auto d-none d-md-block">
                    {{ getReviewSegmentsSummary() }}
                  </div>
                  <div class="review-header-toggle text-secondary">
                    <i class="fa-solid" [ngClass]="isReviewSectionOpen(4) ? 'fa-chevron-up' : 'fa-chevron-down'"></i>
                  </div>
                </div>
                <!-- Expanded Segment Fares Table -->
                <div *ngIf="isReviewSectionOpen(4)" class="review-section-body p-3 pt-0 border-top">
                  <div class="table-responsive mt-3">
                    <table class="table review-table mb-0 align-middle">
                      <thead>
                        <tr>
                          <th style="width: 50px;" class="text-center">#</th>
                          <th>From Stop</th>
                          <th>To Stop</th>
                          <th class="text-center" style="width: 140px;">Distance (km)</th>
                          <th class="text-center" style="width: 160px;">Segment Fare (&#8377;)</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr *ngFor="let seg of wizardSegmentFares; let idx = index" class="border-bottom">
                          <td class="text-center text-muted fw-semibold">{{ seg.segmentIndex }}</td>
                          <td>
                            <span [class.fw-bold]="idx === 0" class="text-dark">{{ seg.fromStopName }}</span>
                          </td>
                          <td>
                            <span [class.fw-bold]="idx === wizardSegmentFares.length - 1" class="text-dark">{{ seg.toStopName }}</span>
                          </td>
                          <td class="text-center text-muted">{{ seg.distanceKm }}</td>
                          <td class="text-center fw-semibold text-dark">{{ seg.fare | number:'1.0-0' }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>

              <!-- Section 5: Journey Fares (Final Fares) -->
              <div class="card review-section-card mb-3">
                <div class="review-section-header" (click)="toggleReviewSection(5)" role="button" tabindex="0">
                  <div class="d-flex align-items-center gap-2.5">
                    <div class="review-icon-circle">
                      <i class="fa-solid fa-indian-rupee-sign"></i>
                    </div>
                    <span class="review-section-title">5. Journey Fares (Final Fares)</span>
                    <span class="review-looks-good-badge">
                      <i class="fa-solid fa-check"></i> Looks good
                    </span>
                  </div>
                  <div class="review-header-summary text-muted small mx-auto d-none d-md-block">
                    {{ getReviewJourneysSummary() }}
                  </div>
                  <div class="review-header-toggle text-secondary">
                    <i class="fa-solid" [ngClass]="isReviewSectionOpen(5) ? 'fa-chevron-up' : 'fa-chevron-down'"></i>
                  </div>
                </div>
                <!-- Expanded Journey Fares Table -->
                <div *ngIf="isReviewSectionOpen(5)" class="review-section-body p-3 pt-0 border-top">
                  <div class="table-responsive mt-3">
                    <table class="table review-table mb-0 align-middle">
                      <thead>
                        <tr>
                          <th style="width: 50px;" class="text-center">#</th>
                          <th>From Stop (Boarding)</th>
                          <th>To Stop (Dropping)</th>
                          <th class="text-center" style="width: 140px;">Distance (km)</th>
                          <th class="text-center" style="width: 160px;">Calculated Fare (&#8377;)</th>
                          <th class="text-center" style="width: 160px;">Operator Fare (&#8377;)</th>
                          <th class="text-center" style="width: 120px;">Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr *ngFor="let journey of wizardJourneyFares; let idx = index" class="border-bottom">
                          <td class="text-center text-muted fw-semibold">{{ journey.journeyIndex }}</td>
                          <td>
                            <span [class.fw-bold]="journey.fromStopOrder === 0" class="text-dark">{{ journey.fromStopName }}</span>
                          </td>
                          <td>
                            <span [class.fw-bold]="journey.toStopOrder === wizardStopTimings.length - 1" class="text-dark">{{ journey.toStopName }}</span>
                          </td>
                          <td class="text-center text-muted">{{ journey.distanceKm }}</td>
                          <td class="text-center text-muted">{{ journey.calculatedFare | number:'1.0-0' }}</td>
                          <td class="text-center fw-semibold text-dark">{{ journey.operatorFare | number:'1.0-0' }}</td>
                          <td class="text-center">
                            <span *ngIf="!journey.isOverridden" class="badge-status-auto">Auto</span>
                            <span *ngIf="journey.isOverridden" class="badge-status-overridden">Overridden</span>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>

              <!-- Full-Width Validation Summary Panel -->
              <div *ngIf="isScheduleReviewValid()" class="validation-summary-card mb-4 d-flex align-items-center justify-content-between flex-wrap gap-3">
                <div class="d-flex align-items-center gap-3">
                  <div class="validation-summary-icon text-success">
                    <i class="fa-solid fa-circle-check fs-2"></i>
                  </div>
                  <div>
                    <div class="fw-bold text-dark fs-6">All validations passed</div>
                    <div class="text-muted small">This schedule is ready to be created.</div>
                  </div>
                </div>
                <div class="validation-summary-checks d-flex flex-wrap gap-4">
                  <div class="validation-checks-col">
                    <div class="d-flex align-items-center text-secondary small mb-1.5">
                      <i class="fa-solid fa-check text-success me-2"></i>
                      <span>No schedule conflicts (including 60-minute turnaround buffer)</span>
                    </div>
                    <div class="d-flex align-items-center text-secondary small">
                      <i class="fa-solid fa-check text-success me-2"></i>
                      <span>All stop timings are in chronological order</span>
                    </div>
                  </div>
                  <div class="validation-checks-col">
                    <div class="d-flex align-items-center text-secondary small mb-1.5">
                      <i class="fa-solid fa-check text-success me-2"></i>
                      <span>All required boarding and dropping point timings are configured</span>
                    </div>
                    <div class="d-flex align-items-center text-secondary small">
                      <i class="fa-solid fa-check text-success me-2"></i>
                      <span>All segment fares and journey fares are valid (greater than 0)</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Validation Error Panel (if invalid) -->
              <div *ngIf="!isScheduleReviewValid()" class="validation-summary-card-error mb-4 d-flex align-items-center justify-content-between flex-wrap gap-3">
                <div class="d-flex align-items-center gap-3">
                  <div class="validation-summary-icon text-danger">
                    <i class="fa-solid fa-circle-exclamation fs-2"></i>
                  </div>
                  <div>
                    <div class="fw-bold text-dark fs-6">Validation issues detected</div>
                    <div class="text-danger small">{{ getValidationErrorMessage() }}</div>
                  </div>
                </div>
                <div>
                  <button *ngIf="hasScheduleConflict()" type="button" class="btn btn-sm btn-outline-danger" (click)="viewConflictingSchedule()">
                    View Conflicting Schedule
                  </button>
                </div>
              </div>

              <!-- Error Alert Banner (e.g. from backend response) -->
              <div *ngIf="scheduleCreationError" class="alert alert-danger alert-dismissible fade show d-flex align-items-center gap-2 mb-4" role="alert">
                <i class="fa-solid fa-triangle-exclamation flex-shrink-0"></i>
                <div class="flex-grow-1">{{ scheduleCreationError }}</div>
                <button type="button" class="btn-close" (click)="scheduleCreationError = null" aria-label="Close"></button>
              </div>

              <!-- Bottom Navigation -->
              <div class="d-flex justify-content-between align-items-center pt-2 pb-4">
                <button type="button"
                        class="btn btn-outline-secondary px-4 py-2 rounded-3 fw-semibold"
                        [disabled]="isCreatingSchedule"
                        (click)="backToConfigureFares()">
                  Back
                </button>
                <button type="button"
                        class="btn btn-primary px-4 py-2 rounded-3 fw-semibold d-inline-flex align-items-center"
                        [disabled]="isCreatingSchedule || !isScheduleReviewValid()"
                        (click)="submitCreateSchedule()">
                  <span *ngIf="isCreatingSchedule" class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                  <span>{{ isCreatingSchedule ? 'Creating Schedule...' : 'Create Schedule' }}</span>
                  <i *ngIf="!isCreatingSchedule" class="fa-solid fa-arrow-right ms-2"></i>
                </button>
              </div>

            </div>

            <!-- ==================== STEP 6: CONFIRMATION (PHASE 6.6) ==================== -->
            <div *ngIf="scheduleWizardStep === 6" class="step6-confirmation-container text-center py-4">
              <!-- Success Illustration -->
              <div class="schedule-success-illustration-wrap mb-3 position-relative d-inline-block">
                <!-- SVG Decorative Background Shape -->
                <svg class="schedule-success-blob-svg" width="360" height="190" viewBox="0 0 360 190" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <!-- Organic pale mint backdrop -->
                  <path d="M72 120C38 106 36 62 78 48C122 34 150 56 185 46C228 32 272 38 296 65C328 98 308 132 276 139C228 150 194 132 160 138C116 146 86 132 72 120Z" fill="#ecfdf5"/>
                  <ellipse cx="180" cy="95" rx="96" ry="50" fill="#ecfdf5"/>
                </svg>

                <!-- Confetti Elements -->
                <!-- Top-Left Red Bar -->
                <span class="confetti" style="position: absolute; top: 42px; left: 144px; width: 7px; height: 16px; background-color: #f43f5e; border-radius: 2px; transform: rotate(-25deg);"></span>
                <!-- Top-Center Yellow Diamond -->
                <span class="confetti" style="position: absolute; top: 32px; left: 210px; width: 8px; height: 8px; background-color: #f59e0b; border-radius: 1px; transform: rotate(45deg);"></span>
                <!-- Top-Right Cyan Square -->
                <span class="confetti" style="position: absolute; top: 50px; right: 100px; width: 8px; height: 8px; background-color: #06b6d4; border-radius: 2px; transform: rotate(18deg);"></span>
                <!-- Far-Left Green Bar -->
                <span class="confetti" style="position: absolute; top: 88px; left: 92px; width: 7px; height: 14px; background-color: #10b981; border-radius: 2px; transform: rotate(32deg);"></span>
                <!-- Mid-Left Yellow Dot -->
                <span class="confetti" style="position: absolute; top: 108px; left: 134px; width: 8px; height: 8px; background-color: #f59e0b; border-radius: 2px; transform: rotate(-10deg);"></span>
                <!-- Mid-Right Red Bar -->
                <span class="confetti" style="position: absolute; top: 64px; right: 118px; width: 7px; height: 16px; background-color: #f43f5e; border-radius: 2px; transform: rotate(38deg);"></span>
                <!-- Far-Right Green Bar -->
                <span class="confetti" style="position: absolute; top: 92px; right: 88px; width: 7px; height: 15px; background-color: #10b981; border-radius: 2px; transform: rotate(-40deg);"></span>
                <!-- Bottom-Right Cyan Bar -->
                <span class="confetti" style="position: absolute; top: 108px; right: 124px; width: 7px; height: 14px; background-color: #06b6d4; border-radius: 2px; transform: rotate(22deg);"></span>

                <!-- Center Green Checkmark Circle -->
                <div class="schedule-success-center-circle" style="position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); width: 84px; height: 84px; border-radius: 50%; background: linear-gradient(135deg, #10b981 0%, #059669 100%); display: flex; align-items: center; justify-content: center; box-shadow: 0 10px 25px -4px rgba(16, 185, 129, 0.42); z-index: 2;">
                  <i class="fa-solid fa-check text-white" style="font-size: 38px; line-height: 1;"></i>
                </div>
              </div>

              <!-- Success Heading & Subtitle -->
              <h2 class="fw-bold text-dark mt-2 mb-2" style="font-size: 32px; letter-spacing: -0.5px;">Schedule Created Successfully!</h2>
              <p class="text-secondary fs-6 mb-4" style="color: #64748b; font-size: 16px;">
                Your bus schedule for {{ getCreatedScheduleRouteText() }} has been created.
              </p>

              <!-- Single Horizontal Confirmation Card -->
              <div class="schedule-confirmation-card mx-auto mb-4 text-start">
                <div class="row align-items-center g-0">
                  <!-- Left Section: Schedule ID, Creation Time -->
                  <div class="col-md-6 col-12 d-flex align-items-center gap-3 p-4 schedule-conf-left">
                    <div class="schedule-conf-cal-icon d-flex align-items-center justify-content-center text-success flex-shrink-0">
                      <i class="fa-regular fa-calendar-days" style="font-size: 34px; color: #10b981;"></i>
                    </div>
                    <div>
                      <span class="text-secondary text-uppercase fw-semibold d-block mb-1" style="font-size: 11px; letter-spacing: 0.5px; color: #64748b;">Schedule ID</span>
                      <div class="fw-bold text-dark fs-5 mb-1" style="letter-spacing: -0.2px;">{{ getFormattedCreatedScheduleId() }}</div>
                      <div class="text-muted small" style="font-size: 13px;">Created on {{ getCreatedScheduleTimestamp() }}</div>
                    </div>
                  </div>

                  <!-- Subtle Divider -->
                  <div class="d-none d-md-block col-auto schedule-conf-divider-wrap">
                    <div class="schedule-conf-divider"></div>
                  </div>

                  <!-- Right Section: Status badge & booking message -->
                  <div class="col-md col-12 p-4 schedule-conf-right">
                    <div class="d-flex flex-column align-items-start">
                      <span class="schedule-conf-status-badge d-inline-flex align-items-center gap-1.5 px-3 py-1 rounded-pill fw-semibold mb-2">
                        <i class="fa-solid fa-check me-1" style="font-size: 11px;"></i>
                        {{ getCreatedScheduleStatusText() }}
                      </span>
                      <p class="text-secondary small mb-0" style="font-size: 13px; color: #64748b;">
                        {{ getCreatedScheduleStatusMessage() }}
                      </p>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Further Actions: Three Buttons in Horizontal Row -->
              <div class="d-flex align-items-center justify-content-center gap-3 flex-wrap mt-3">
                <button type="button"
                        class="btn btn-primary px-4 py-2.5 rounded-3 fw-semibold d-inline-flex align-items-center gap-2 shadow-sm"
                        (click)="startNewScheduleWizard()">
                  <i class="fa-regular fa-calendar-plus"></i>
                  <span>Create Another Schedule</span>
                </button>

                <button type="button"
                        class="btn btn-outline-secondary bg-white text-dark border px-4 py-2.5 rounded-3 fw-semibold d-inline-flex align-items-center gap-2 shadow-sm"
                        (click)="finishWizardAndGoToSchedules()">
                  <i class="fa-regular fa-file-lines text-primary"></i>
                  <span>View All Schedules</span>
                </button>

                <button type="button"
                        class="btn btn-outline-secondary bg-white text-dark border px-4 py-2.5 rounded-3 fw-semibold d-inline-flex align-items-center gap-2 shadow-sm"
                        (click)="goToCreatedScheduleDetails()">
                  <i class="fa-solid fa-bus text-primary"></i>
                  <span>Go to Schedule Details</span>
                </button>
              </div>
            </div>
          </div>
        </div> <!-- /activeNav === 'schedules' -->

          <!-- ==================== VIEW 5: BOOKINGS MANIFEST ==================== -->
          <div *ngIf="activeNav === 'bookings'">
            <div class="d-flex justify-content-between align-items-center mb-4">
              <div>
                <h4 class="fw-bold text-dark mb-1"><i class="fa-solid fa-file-lines text-primary me-2"></i>Passenger Bookings Manifest</h4>
                <p class="text-muted small mb-0">View all customer reservations and manifests for your bus services</p>
              </div>
            </div>

            <div class="table-responsive bg-white rounded shadow-sm">
              <table class="table table-hover align-middle mb-0">
                <thead class="table-dark">
                  <tr>
                    <th>Booking Ref</th>
                    <th>Passenger Name</th>
                    <th>Service Title</th>
                    <th>Status</th>
                    <th>Amount</th>
                    <th>Booking Date</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let b of operatorBookings">
                    <td><strong>{{ b.bookingRef }}</strong></td>
                    <td>{{ b.customerName }}<br><small class="text-muted">{{ b.customerEmail }}</small></td>
                    <td>{{ b.serviceTitle }}</td>
                    <td>
                      <span class="badge" [class.bg-success]="b.status === 'CONFIRMED' || b.status === 'COMPLETED'" [class.bg-danger]="b.status === 'CANCELLED'">
                        {{ b.status }}
                      </span>
                    </td>
                    <td class="fw-bold text-success">₹{{ b.totalAmount }}</td>
                    <td>{{ b.bookingDate | date:'short' }}</td>
                  </tr>
                  <tr *ngIf="operatorBookings.length === 0">
                    <td colspan="6" class="text-center py-4 text-muted">No passenger reservations found.</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- ==================== VIEW 6: PROFILE & ACCOUNT ==================== -->
          <div *ngIf="activeNav === 'profile'">
            <h4 class="fw-bold text-dark mb-4"><i class="fa-solid fa-user-gear text-primary me-2"></i>Operator Profile & Account</h4>
            <div class="card border-0 shadow-sm p-4 bg-white rounded-4" style="max-width: 600px;">
              <div class="d-flex align-items-center gap-3 mb-4">
                <div class="operator-avatar" style="width: 64px; height: 64px; font-size: 24px;">
                  <span>{{ getInitials() }}</span>
                </div>
                <div>
                  <h5 class="fw-bold text-dark mb-1">{{ currentUser?.fullName || 'Operator' }}</h5>
                  <span class="badge bg-primary">Authorized Bus Operator</span>
                </div>
              </div>
              <div class="mb-3">
                <label class="form-label text-muted small fw-bold">Email Address</label>
                <input type="text" class="form-control bg-light" [value]="currentUser?.email || ''" readonly>
              </div>
              <div class="mb-3">
                <label class="form-label text-muted small fw-bold">Operator ID</label>
                <input type="text" class="form-control bg-light" [value]="currentUser?.id || ''" readonly>
              </div>
              <div class="mb-3">
                <label class="form-label text-muted small fw-bold">Role Claim</label>
                <input type="text" class="form-control bg-light" [value]="currentUser?.role || 'ROLE_BUS_OPERATOR'" readonly>
              </div>
            </div>
          </div>

          <!-- ==================== VIEW 7: SUPPORT ==================== -->
          <div *ngIf="activeNav === 'support'">
            <h4 class="fw-bold text-dark mb-4"><i class="fa-solid fa-headset text-primary me-2"></i>Help & Support</h4>
            <div class="card border-0 shadow-sm p-4 bg-white rounded-4" style="max-width: 600px;">
              <h5 class="fw-bold text-dark mb-2">TravelEasy Partner Support</h5>
              <p class="text-muted small mb-4">Have questions about route management, scheduling, or passenger manifests? Our operations team is available 24/7.</p>
              <div class="d-flex align-items-center gap-3 p-3 bg-light rounded-3 mb-3">
                <i class="fa-solid fa-envelope text-primary fs-4"></i>
                <div>
                  <strong class="d-block text-dark small">Email Support</strong>
                  <span class="text-muted small">support&#64;traveleasy.com</span>
                </div>
              </div>
              <div class="d-flex align-items-center gap-3 p-3 bg-light rounded-3 mb-3">
                <i class="fa-solid fa-phone text-success fs-4"></i>
                <div>
                  <strong class="d-block text-dark small">Operator Helpline</strong>
                  <span class="text-muted small">1800-123-TRAVEL (Toll Free)</span>
                </div>
              </div>
            </div>
          </div>

        </main>
      </div>

      <!-- ==================== 4-STEP CREATE / EDIT ROUTE MODAL ==================== -->
      <div *ngIf="showRouteModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-lg modal-dialog-scrollable">
          <div class="modal-content">
            <div class="modal-header bg-primary text-white">
              <h5 class="modal-title fw-bold">
                <i class="fa-solid fa-route me-2"></i>{{ isEditingRoute ? 'Edit Route (' + routeFormCode + ')' : 'Create Reusable Route' }}
              </h5>
              <button type="button" class="btn-close btn-close-white" (click)="showRouteModal = false"></button>
            </div>

            <!-- Step Wizard Nav Tabs -->
            <div class="px-4 pt-3 bg-light border-bottom">
              <ul class="nav nav-pills nav-fill">
                <li class="nav-item">
                  <button class="nav-link py-1 fw-bold" [class.active]="routeModalStep === 1" (click)="goToStep(1)">
                    1. Route Details
                  </button>
                </li>
                <li class="nav-item">
                  <button class="nav-link py-1 fw-bold" [class.active]="routeModalStep === 2" (click)="goToStep(2)">
                    2. Add Stops
                  </button>
                </li>
                <li class="nav-item">
                  <button class="nav-link py-1 fw-bold" [class.active]="routeModalStep === 3" (click)="goToStep(3)">
                    3. Points
                  </button>
                </li>
                <li class="nav-item">
                  <button class="nav-link py-1 fw-bold" [class.active]="routeModalStep === 4" (click)="goToStep(4)">
                    4. Review & Create
                  </button>
                </li>
              </ul>
            </div>

            <div class="modal-body p-4">
              <div *ngIf="routeModalError" class="alert alert-danger py-2 small mb-3">
                <i class="fa-solid fa-triangle-exclamation me-1"></i> {{ routeModalError }}
              </div>

              <!-- STEP 1: ROUTE DETAILS -->
              <div *ngIf="routeModalStep === 1">
                <h6 class="fw-bold text-primary mb-3"><i class="fa-solid fa-circle-info me-1"></i> Step 1: Route Journey Details</h6>
                
                <div class="mb-3">
                  <label class="form-label fw-bold">Route Name <span class="text-danger">*</span></label>
                  <input type="text" class="form-control" [(ngModel)]="routeNameInput" placeholder="e.g. Vijayawada-Hyderabad Express">
                  <small class="text-muted">A descriptive, human-readable label for this route journey.</small>
                </div>

                <div class="row mb-3">
                  <div class="col-md-6">
                    <label class="form-label fw-bold">Origin City <span class="text-danger">*</span></label>
                    <input type="text" class="form-control" [(ngModel)]="routeOriginInput" (ngModelChange)="onOriginChange()" placeholder="e.g. Vijayawada">
                  </div>
                  <div class="col-md-6">
                    <label class="form-label fw-bold">Destination City <span class="text-danger">*</span></label>
                    <input type="text" class="form-control" [(ngModel)]="routeDestinationInput" (ngModelChange)="onDestinationChange()" placeholder="e.g. Hyderabad">
                  </div>
                </div>

                <div *ngIf="routeOriginInput && routeDestinationInput && routeOriginInput.trim().toLowerCase() === routeDestinationInput.trim().toLowerCase()" class="alert alert-warning py-2 small">
                  <i class="fa-solid fa-circle-exclamation me-1"></i> Origin and Destination cannot be the same physical location.
                </div>

                <div class="mb-3">
                  <label class="form-label fw-bold">Route Description / Notes</label>
                  <textarea class="form-control" rows="2" [(ngModel)]="routeDescriptionInput" placeholder="e.g. Direct express route via Suryapet highway"></textarea>
                </div>

                <div class="alert alert-info py-2 small">
                  <i class="fa-solid fa-lightbulb me-1"></i> The system will automatically generate a unique Route ID (e.g. <strong>{{ routeFormCode || 'RT-AUTO' }}</strong>). Actual trip departure/arrival timings and bus assignments will be configured when scheduling trips.
                </div>
              </div>

              <!-- STEP 2: ADD STOPS -->
              <div *ngIf="routeModalStep === 2">
                <div class="d-flex justify-content-between align-items-center mb-3">
                  <h6 class="fw-bold text-primary mb-0"><i class="fa-solid fa-list-ol me-1"></i> Step 2: Ordered Stops Sequence</h6>
                  <button type="button" class="btn btn-outline-primary btn-sm rounded-pill fw-bold" (click)="addWizardIntermediateStop()">
                    <i class="fa-solid fa-plus me-1"></i> Add Intermediate Stop
                  </button>
                </div>
                <p class="text-muted small mb-3">Configure all stops in strict journey order. Repeated physical locations are not permitted within a route.</p>

                <div class="list-group mb-3">
                  <!-- Origin Stop -->
                  <div class="list-group-item d-flex align-items-center justify-content-between bg-light border-start border-4 border-success">
                    <div class="d-flex align-items-center gap-2">
                      <span class="badge bg-success">ORIGIN</span>
                      <span class="fw-bold text-dark">{{ routeStopsForm[0]?.stopName || routeOriginInput || 'Origin' }}</span>
                    </div>
                    <span class="badge bg-secondary">0 km (Start)</span>
                  </div>

                  <!-- Intermediate Stops -->
                  <ng-container *ngFor="let stop of intermediateStops; let idx = index">
                    <div class="list-group-item d-flex align-items-center justify-content-between">
                      <div class="d-flex align-items-center gap-2 flex-grow-1 me-3">
                        <span class="badge bg-info text-dark">Stop {{ idx + 2 }}</span>
                        <input type="text" class="form-control form-control-sm" [(ngModel)]="stop.stopName" placeholder="Stop City / Location">
                        <div class="input-group input-group-sm" style="max-width: 140px;">
                          <input type="number" class="form-control" [(ngModel)]="stop.distanceKm" placeholder="Distance">
                          <span class="input-group-text">km</span>
                        </div>
                      </div>
                      <div class="btn-group btn-group-sm">
                        <button type="button" class="btn btn-outline-secondary" [disabled]="idx === 0" (click)="moveIntermediateUp(idx)" title="Move Up">
                          <i class="fa-solid fa-arrow-up"></i>
                        </button>
                        <button type="button" class="btn btn-outline-secondary" [disabled]="idx === intermediateStops.length - 1" (click)="moveIntermediateDown(idx)" title="Move Down">
                          <i class="fa-solid fa-arrow-down"></i>
                        </button>
                        <button type="button" class="btn btn-outline-danger" (click)="removeWizardIntermediateStop(idx)" title="Remove Stop">
                          <i class="fa-solid fa-trash"></i>
                        </button>
                      </div>
                    </div>
                  </ng-container>

                  <!-- Destination Stop -->
                  <div class="list-group-item d-flex align-items-center justify-content-between bg-light border-start border-4 border-danger">
                    <div class="d-flex align-items-center gap-2">
                      <span class="badge bg-danger">DESTINATION</span>
                      <span class="fw-bold text-dark">{{ destinationStopName || routeDestinationInput || 'Destination' }}</span>
                    </div>
                    <div class="input-group input-group-sm" style="max-width: 140px;">
                      <input type="number" class="form-control" [(ngModel)]="destinationDistanceKm" placeholder="Total dist">
                      <span class="input-group-text">km</span>
                    </div>
                  </div>
                </div>

                <div *ngIf="hasDuplicateStopLocation()" class="alert alert-danger py-2 small">
                  <i class="fa-solid fa-triangle-exclamation me-1"></i> Repeated physical locations detected! A route cannot visit the same location more than once.
                </div>
              </div>

              <!-- STEP 3: CONFIGURE BOARDING & DROPPING POINTS -->
              <div *ngIf="routeModalStep === 3">
                <h6 class="fw-bold text-primary mb-2"><i class="fa-solid fa-map-pin me-1"></i> Step 3: Boarding and Dropping Points</h6>
                <p class="text-muted small mb-3">Select each stop to independently configure its pick-up and drop-off points. Origin requires at least 1 boarding point; Destination requires at least 1 dropping point.</p>

                <!-- Stop Selector Chips -->
                <div class="d-flex flex-wrap gap-2 mb-4">
                  <button *ngFor="let stop of routeStopsForm; let sIdx = index"
                          type="button" 
                          class="btn btn-sm rounded-pill"
                          [class.btn-primary]="activePointsStopIdx === sIdx"
                          [class.btn-outline-secondary]="activePointsStopIdx !== sIdx"
                          (click)="activePointsStopIdx = sIdx">
                    <span class="badge bg-white text-dark me-1">{{ sIdx + 1 }}</span>
                    {{ stop.stopName }} 
                    <span class="badge bg-secondary ms-1">{{ stop.boardingPoints.length }}B / {{ stop.droppingPoints.length }}D</span>
                  </button>
                </div>

                <!-- Point Config for Active Stop -->
                <div *ngIf="routeStopsForm[activePointsStopIdx]" class="border rounded p-3 bg-light">
                  <h6 class="fw-bold text-dark mb-3">
                    Configuring Points for: <span class="text-primary">{{ routeStopsForm[activePointsStopIdx].stopName }}</span>
                    <span class="badge bg-secondary ms-2">{{ routeStopsForm[activePointsStopIdx].stopType }}</span>
                  </h6>

                  <div class="row g-3">
                    <div class="col-md-6">
                      <div class="card h-100 border-0 shadow-sm">
                        <div class="card-header bg-white d-flex justify-content-between align-items-center py-2">
                          <strong class="text-success small"><i class="fa-solid fa-person-walking-luggage me-1"></i> Boarding Points ({{ routeStopsForm[activePointsStopIdx].boardingPoints.length }})</strong>
                          <button type="button" class="btn btn-sm btn-outline-success rounded-pill" (click)="addBoardingPoint(activePointsStopIdx)">
                            + Add
                          </button>
                        </div>
                        <div class="card-body p-2">
                          <div *ngFor="let bp of routeStopsForm[activePointsStopIdx].boardingPoints; let pIdx = index" class="border rounded p-2 mb-2 bg-white">
                            <div class="d-flex justify-content-between align-items-center mb-1">
                              <span class="badge bg-light text-dark border">Point {{ pIdx + 1 }}</span>
                              <button type="button" class="btn btn-link text-danger p-0" (click)="removeBoardingPoint(activePointsStopIdx, pIdx)">
                                <i class="fa-solid fa-trash-can"></i>
                              </button>
                            </div>
                            <input type="text" class="form-control form-control-sm mb-1" [(ngModel)]="bp.pointName" placeholder="Point Name">
                            <input type="text" class="form-control form-control-sm mb-1" [(ngModel)]="bp.address" placeholder="Address / Landmark">
                            <input type="text" class="form-control form-control-sm" [(ngModel)]="bp.timeSlot" placeholder="Operating Window (e.g. 06:00 AM)">
                          </div>
                          <div *ngIf="routeStopsForm[activePointsStopIdx].boardingPoints.length === 0" class="text-center py-3 text-muted small">
                            No boarding points configured at this stop.
                          </div>
                        </div>
                      </div>
                    </div>

                    <div class="col-md-6">
                      <div class="card h-100 border-0 shadow-sm">
                        <div class="card-header bg-white d-flex justify-content-between align-items-center py-2">
                          <strong class="text-danger small"><i class="fa-solid fa-bus-simple me-1"></i> Dropping Points ({{ routeStopsForm[activePointsStopIdx].droppingPoints.length }})</strong>
                          <button type="button" class="btn btn-sm btn-outline-danger rounded-pill" (click)="addDroppingPoint(activePointsStopIdx)">
                            + Add
                          </button>
                        </div>
                        <div class="card-body p-2">
                          <div *ngFor="let dp of routeStopsForm[activePointsStopIdx].droppingPoints; let pIdx = index" class="border rounded p-2 mb-2 bg-white">
                            <div class="d-flex justify-content-between align-items-center mb-1">
                              <span class="badge bg-light text-dark border">Point {{ pIdx + 1 }}</span>
                              <button type="button" class="btn btn-link text-danger p-0" (click)="removeDroppingPoint(activePointsStopIdx, pIdx)">
                                <i class="fa-solid fa-trash-can"></i>
                              </button>
                            </div>
                            <input type="text" class="form-control form-control-sm mb-1" [(ngModel)]="dp.pointName" placeholder="Point Name">
                            <input type="text" class="form-control form-control-sm mb-1" [(ngModel)]="dp.address" placeholder="Address / Landmark">
                            <input type="text" class="form-control form-control-sm" [(ngModel)]="dp.timeSlot" placeholder="Drop Window (e.g. 11:30 AM)">
                          </div>
                          <div *ngIf="routeStopsForm[activePointsStopIdx].droppingPoints.length === 0" class="text-center py-3 text-muted small">
                            No dropping points configured at this stop.
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- STEP 4: REVIEW AND CREATE (MINIMAL REVIEW LAYOUT) -->
              <div *ngIf="routeModalStep === 4">
                <h6 class="fw-bold text-primary mb-3"><i class="fa-solid fa-clipboard-check me-1"></i> Step 4: Review Route & Points Configuration</h6>

                <div class="card p-3 mb-3 bg-light border-0 shadow-sm">
                  <div class="d-flex justify-content-between align-items-start">
                    <div>
                      <h5 class="fw-bold text-dark mb-1">{{ routeNameInput }}</h5>
                      <p class="text-primary fw-bold mb-1">
                        {{ routeOriginInput }} <i class="fa-solid fa-arrow-right mx-2 text-muted"></i> {{ routeDestinationInput }}
                      </p>
                      <p *ngIf="routeDescriptionInput" class="text-muted small mb-0">{{ routeDescriptionInput }}</p>
                    </div>
                    <span class="badge bg-primary px-3 py-2 fs-6">{{ routeFormCode || 'AUTO-ID' }}</span>
                  </div>
                </div>

                <div class="mb-3">
                  <label class="form-label fw-bold small text-muted">Journey Stops (Click to inspect points):</label>
                  <div class="d-flex flex-wrap gap-2 align-items-center">
                    <button *ngFor="let s of routeStopsForm; let sIdx = index"
                            type="button" 
                            class="btn btn-sm rounded-pill"
                            [class.btn-dark]="reviewSelectedStopIdx === sIdx"
                            [class.btn-outline-dark]="reviewSelectedStopIdx !== sIdx"
                            (click)="reviewSelectedStopIdx = sIdx">
                      <span class="badge bg-light text-dark me-1">{{ sIdx + 1 }}</span>
                      {{ s.stopName }}
                      <span class="badge bg-secondary ms-1">{{ s.boardingPoints.length }}B / {{ s.droppingPoints.length }}D</span>
                    </button>
                  </div>
                </div>

                <div *ngIf="routeStopsForm[reviewSelectedStopIdx]" class="card border p-3 mb-3">
                  <h6 class="fw-bold text-dark mb-2">
                    Inspecting Points: <span class="text-primary">{{ routeStopsForm[reviewSelectedStopIdx].stopName }}</span> ({{ routeStopsForm[reviewSelectedStopIdx].stopType }})
                  </h6>
                  <div class="row g-2">
                    <div class="col-md-6">
                      <strong class="text-success small d-block mb-1">Boarding Points:</strong>
                      <ul class="list-group list-group-flush small" *ngIf="routeStopsForm[reviewSelectedStopIdx].boardingPoints.length > 0">
                        <li *ngFor="let bp of routeStopsForm[reviewSelectedStopIdx].boardingPoints" class="list-group-item px-0 py-1">
                          <strong>{{ bp.pointName }}</strong> <span *ngIf="bp.timeSlot" class="text-muted">({{ bp.timeSlot }})</span>
                        </li>
                      </ul>
                      <div *ngIf="routeStopsForm[reviewSelectedStopIdx].boardingPoints.length === 0" class="text-muted small">None configured</div>
                    </div>
                    <div class="col-md-6">
                      <strong class="text-danger small d-block mb-1">Dropping Points:</strong>
                      <ul class="list-group list-group-flush small" *ngIf="routeStopsForm[reviewSelectedStopIdx].droppingPoints.length > 0">
                        <li *ngFor="let dp of routeStopsForm[reviewSelectedStopIdx].droppingPoints" class="list-group-item px-0 py-1">
                          <strong>{{ dp.pointName }}</strong> <span *ngIf="dp.timeSlot" class="text-muted">({{ dp.timeSlot }})</span>
                        </li>
                      </ul>
                      <div *ngIf="routeStopsForm[reviewSelectedStopIdx].droppingPoints.length === 0" class="text-muted small">None configured</div>
                    </div>
                  </div>
                </div>

                <div class="alert alert-light border small">
                  <div class="d-flex align-items-center mb-1">
                    <i class="fa-solid fa-check-circle text-success me-2"></i> Unique physical locations along route
                  </div>
                  <div class="d-flex align-items-center mb-1" [class.text-success]="hasOriginBoarding()" [class.text-danger]="!hasOriginBoarding()">
                    <i class="fa-solid me-2" [class.fa-check-circle]="hasOriginBoarding()" [class.fa-circle-xmark]="!hasOriginBoarding()"></i>
                    Origin ({{ routeOriginInput }}) has at least 1 boarding point
                  </div>
                  <div class="d-flex align-items-center" [class.text-success]="hasDestinationDropping()" [class.text-danger]="!hasDestinationDropping()">
                    <i class="fa-solid me-2" [class.fa-check-circle]="hasDestinationDropping()" [class.fa-circle-xmark]="!hasDestinationDropping()"></i>
                    Destination ({{ routeDestinationInput }}) has at least 1 dropping point
                  </div>
                </div>
              </div>
            </div>

            <!-- Modal Footer -->
            <div class="modal-footer bg-light d-flex justify-content-between">
              <button type="button" class="btn btn-outline-secondary" [disabled]="routeModalStep === 1" (click)="prevStep()">
                <i class="fa-solid fa-arrow-left me-1"></i> Back
              </button>
              <div class="d-flex gap-2">
                <button type="button" class="btn btn-secondary" (click)="showRouteModal = false">Cancel</button>
                <button *ngIf="routeModalStep < 4" type="button" class="btn btn-primary" (click)="nextStep()">
                  Next <i class="fa-solid fa-arrow-right ms-1"></i>
                </button>
                <button *ngIf="routeModalStep === 4" type="button" class="btn btn-success fw-bold" [disabled]="isSavingRoute" (click)="submitRouteForm()">
                  <i class="fa-solid fa-check me-1"></i> {{ isSavingRoute ? 'Saving...' : (isEditingRoute ? 'Update Route' : 'Confirm & Save Route') }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ==================== ADD BUS MODAL ==================== -->
      <div *ngIf="showAddBusModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog">
          <div class="modal-content">
            <div class="modal-header bg-primary text-white">
              <h5 class="modal-title fw-bold"><i class="fa-solid fa-bus me-2"></i>Register New Bus</h5>
              <button type="button" class="btn-close btn-close-white" (click)="showAddBusModal = false"></button>
            </div>
            <div class="modal-body p-4">
              <form (ngSubmit)="submitAddBus()">
                <div class="mb-3">
                  <label class="form-label fw-bold">Bus Registration Number</label>
                  <input type="text" class="form-control" [(ngModel)]="newBusNumber" name="newBusNumber" required placeholder="e.g. AP39TV1234">
                </div>

                <div class="row mb-3">
                  <div class="col-md-6">
                    <label class="form-label fw-bold">Facility Type</label>
                    <select class="form-select" [(ngModel)]="newFacilityType" name="newFacilityType" (change)="onFacilityOrConfigChange()">
                      <option value="AC">AC (Air Conditioned)</option>
                      <option value="NON_AC">Non-AC</option>
                    </select>
                  </div>
                  <div class="col-md-6">
                    <label class="form-label fw-bold">Bus Configuration</label>
                    <select class="form-select" [(ngModel)]="newBusConfiguration" name="newBusConfiguration" (change)="onFacilityOrConfigChange()">
                      <option value="SINGLE_DECK">Single Decker</option>
                      <option value="DOUBLE_DECK">Double Decker</option>
                    </select>
                  </div>
                </div>

                <div class="mb-3">
                  <label class="form-label fw-bold">Physical Seat Layout Template</label>
                  <select class="form-select" [(ngModel)]="selectedTemplateId" name="selectedTemplateId" (change)="onTemplateChange()" required>
                    <option *ngFor="let t of filteredTemplates" [value]="t.id">
                      {{ t.displayName }} ({{ t.totalInventory }} positions)
                    </option>
                  </select>
                </div>

                <!-- Template Preview Container -->
                <div *ngIf="selectedTemplate" class="card bg-light p-3 mb-3 border">
                  <div class="d-flex align-items-center justify-content-between mb-2">
                    <span class="badge bg-primary fw-bold">{{ selectedTemplate.id }}</span>
                    <span class="badge bg-success fw-bold">{{ selectedTemplate.totalInventory }} Positions (Fixed Inventory)</span>
                  </div>
                  <p class="text-muted small mb-2">{{ selectedTemplate.description }}</p>
                  <div *ngIf="selectedTemplate.referenceImage" class="text-center p-2 bg-white rounded border">
                    <img [src]="'/assets/images/' + selectedTemplate.referenceImage" [alt]="selectedTemplate.displayName" style="max-height: 200px; max-width: 100%; object-fit: contain;">
                  </div>
                </div>

                <div class="row mb-3">
                  <div class="col-md-6">
                    <label class="form-label fw-bold">Total Seat Capacity (Derived)</label>
                    <input type="number" class="form-control bg-light" [value]="newTotalSeats" readonly title="Capacity is derived automatically from the assigned seat template">
                  </div>
                  <div class="col-md-6">
                    <label class="form-label fw-bold">Category Display Title</label>
                    <input type="text" class="form-control" [(ngModel)]="newBusType" name="newBusType" required>
                  </div>
                </div>

                <div class="mb-3">
                  <label class="form-label fw-bold">Amenities Included</label>
                  <input type="text" class="form-control" [(ngModel)]="newAmenities" name="newAmenities" placeholder="Wi-Fi, Charging Point, Water Bottle">
                </div>
                <button type="submit" class="btn btn-primary w-100 rounded-pill fw-bold">Save Bus Registration</button>
              </form>
            </div>
          </div>
        </div>
      </div>

      <!-- ==================== ADD / PUBLISH SCHEDULE MODAL ==================== -->
      <div *ngIf="showScheduleModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-lg modal-dialog-scrollable">
          <div class="modal-content">
            <div class="modal-header bg-success text-white">
              <h5 class="modal-title fw-bold"><i class="fa-solid fa-clock me-2"></i>Schedule Trip for Route</h5>
              <button type="button" class="btn-close btn-close-white" (click)="showScheduleModal = false"></button>
            </div>
            <div class="modal-body p-4">
              <form (ngSubmit)="submitAddSchedule()">
                <!-- Route Selection -->
                <div class="mb-3">
                  <label class="form-label fw-bold">Select Reusable Route <span class="text-danger">*</span></label>
                  <select class="form-select" [(ngModel)]="schedRouteId" name="schedRouteId" (change)="onSchedRouteChange()" required>
                    <option [ngValue]="null">-- Select an existing Route --</option>
                    <option *ngFor="let r of myRoutes" [ngValue]="r.id" [disabled]="r.status === 'INACTIVE'">
                      {{ r.routeCode }} - {{ r.routeName }} ({{ r.origin }} -> {{ r.destination }}) {{ r.status === 'INACTIVE' ? '[INACTIVE]' : '' }}
                    </option>
                  </select>
                  <small class="text-muted">Origin, destination, and intermediate stops are inherited from the selected route.</small>
                </div>

                <!-- Bus Selection -->
                <div class="mb-3">
                  <label class="form-label fw-bold">Select Bus <span class="text-danger">*</span></label>
                  <select class="form-select" [(ngModel)]="schedBusId" name="schedBusId" required>
                    <option [ngValue]="null">-- Select a registered Bus --</option>
                    <option *ngFor="let b of myBuses" [ngValue]="b.id" [disabled]="b.status === 'INACTIVE'">
                      {{ b.busNumber }} - {{ b.busType }} ({{ b.totalSeats }} seats) {{ b.status === 'INACTIVE' ? '[INACTIVE]' : '' }}
                    </option>
                  </select>
                </div>

                <div class="row mb-3">
                  <div class="col-md-6">
                    <label class="form-label fw-bold">Origin City (Locked)</label>
                    <input type="text" class="form-control bg-light" [(ngModel)]="schedOrigin" name="schedOrigin" readonly>
                  </div>
                  <div class="col-md-6">
                    <label class="form-label fw-bold">Destination City (Locked)</label>
                    <input type="text" class="form-control bg-light" [(ngModel)]="schedDestination" name="schedDestination" readonly>
                  </div>
                </div>

                <div class="row mb-3">
                  <div class="col-md-4">
                    <label class="form-label fw-bold">Travel Date <span class="text-danger">*</span></label>
                    <input type="date" class="form-control" [(ngModel)]="schedTravelDate" name="schedTravelDate" required>
                  </div>
                  <div class="col-md-4">
                    <label class="form-label fw-bold">Origin Departure <span class="text-danger">*</span></label>
                    <input type="text" class="form-control" [(ngModel)]="schedDeparture" name="schedDeparture" required placeholder="22:30">
                  </div>
                  <div class="col-md-4">
                    <label class="form-label fw-bold">Destination Arrival <span class="text-danger">*</span></label>
                    <input type="text" class="form-control" [(ngModel)]="schedArrival" name="schedArrival" required placeholder="05:30">
                  </div>
                </div>

                <div class="mb-3">
                  <label class="form-label fw-bold">Full Journey Fare (₹) <span class="text-danger">*</span></label>
                  <input type="number" class="form-control" [(ngModel)]="schedFare" name="schedFare" (ngModelChange)="distributeConsecutiveFares()" required placeholder="850">
                </div>

                <!-- Stop Timings & Consecutive Segment Fares Configuration -->
                <div *ngIf="schedStopTimings.length > 0" class="mb-4">
                  <label class="form-label fw-bold text-dark"><i class="fa-solid fa-timeline me-1"></i> Actual Schedule Timings & Segment Fares</label>
                  <div class="table-responsive border rounded">
                    <table class="table table-sm table-bordered align-middle mb-0">
                      <thead class="table-light">
                        <tr>
                          <th>Stop Order</th>
                          <th>Stop Name</th>
                          <th>Arrival</th>
                          <th>Departure</th>
                          <th>Fare to Next Stop (₹)</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr *ngFor="let st of schedStopTimings; let last = last; let first = first">
                          <td class="text-center"><span class="badge bg-secondary">{{ st.stopOrder + 1 }}</span></td>
                          <td>
                            <strong>{{ st.stopName }}</strong>
                            <span class="badge bg-light text-dark ms-1">{{ st.stopType }}</span>
                          </td>
                          <td>
                            <input *ngIf="!first" type="text" class="form-control form-control-sm" [(ngModel)]="st.arrivalTime" [name]="'arr_' + st.stopOrder" placeholder="e.g. 02:00">
                            <span *ngIf="first" class="text-muted small">Origin Start</span>
                          </td>
                          <td>
                            <input *ngIf="!last" type="text" class="form-control form-control-sm" [(ngModel)]="st.departureTime" [name]="'dep_' + st.stopOrder" placeholder="e.g. 02:15">
                            <span *ngIf="last" class="text-muted small">Final Stop</span>
                          </td>
                          <td>
                            <input *ngIf="!last" type="number" class="form-control form-control-sm" [(ngModel)]="st.consecutiveFare" [name]="'fare_' + st.stopOrder" placeholder="Fare">
                            <span *ngIf="last" class="text-muted small">0.0 (End)</span>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <button type="submit" class="btn btn-success w-100 rounded-pill fw-bold">Publish Trip Schedule</button>
              </form>
            </div>
          </div>
        </div>
      </div>

          <!-- ==================== ROUTE DETAILS INSPECTION MODAL ==================== -->
      <div *ngIf="showRouteDetailsModal && selectedRouteForDetails" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-lg modal-dialog-scrollable">
          <div class="modal-content rounded-4 border-0 shadow">
            <div class="modal-header bg-light border-bottom px-4 py-3">
              <div class="d-flex align-items-center gap-2">
                <span class="badge-route-id">{{ selectedRouteForDetails.routeCode }}</span>
                <h5 class="modal-title fw-bold text-dark mb-0">{{ selectedRouteForDetails.routeName }}</h5>
              </div>
              <button type="button" class="btn-close" (click)="showRouteDetailsModal = false"></button>
            </div>
            <div class="modal-body p-4">
              <!-- Route Overview Card -->
              <div class="p-3 bg-light rounded-3 mb-4 border">
                <div class="row g-3">
                  <div class="col-md-6">
                    <small class="text-muted d-block fw-semibold mb-1">Origin &rarr; Destination</small>
                    <div class="fw-bold text-dark">
                      <i class="fa-solid fa-location-dot text-primary me-1"></i>{{ selectedRouteForDetails.origin }}
                      <i class="fa-solid fa-arrow-right mx-2 text-muted"></i>
                      <i class="fa-solid fa-location-dot text-danger me-1"></i>{{ selectedRouteForDetails.destination }}
                    </div>
                  </div>
                  <div class="col-md-3 col-6">
                    <small class="text-muted d-block fw-semibold mb-1">Status</small>
                    <span *ngIf="selectedRouteForDetails.status === 'ACTIVE'" class="badge-status-active">
                      <i class="fa-solid fa-circle fa-2xs me-1"></i>Active
                    </span>
                    <span *ngIf="selectedRouteForDetails.status === 'DRAFT'" class="badge-status-draft">
                      <i class="fa-solid fa-circle fa-2xs me-1"></i>Draft
                    </span>
                    <span *ngIf="selectedRouteForDetails.status === 'INACTIVE'" class="badge-status-inactive">
                      <i class="fa-solid fa-circle fa-2xs me-1"></i>Inactive
                    </span>
                  </div>
                  <div class="col-md-3 col-6">
                    <small class="text-muted d-block fw-semibold mb-1">Associated Schedules</small>
                    <span class="badge bg-light text-dark border px-2 py-1">{{ selectedRouteForDetails.scheduleCount || 0 }} trips</span>
                  </div>
                  <div *ngIf="selectedRouteForDetails.description" class="col-12 border-top pt-2 mt-2">
                    <small class="text-muted d-block fw-semibold mb-1">Description</small>
                    <span class="text-dark small">{{ selectedRouteForDetails.description }}</span>
                  </div>
                </div>
              </div>

              <!-- Ordered Stops & Points -->
              <h6 class="fw-bold text-dark mb-3"><i class="fa-solid fa-map-pin text-primary me-2"></i>Ordered Journey Stops ({{ selectedRouteForDetails.stops?.length || 0 }} stops)</h6>
              <div class="accordion mb-3" id="routeStopsAccordion">
                <div *ngFor="let s of selectedRouteForDetails.stops; let i = index" class="accordion-item border rounded-3 mb-2 overflow-hidden">
                  <h2 class="accordion-header">
                    <button class="accordion-button collapsed py-2 px-3 fw-bold small" type="button" [attr.data-bs-target]="'#stopCollapse' + i" data-bs-toggle="collapse">
                      <span class="badge bg-primary me-2">Stop {{ s.stopOrder + 1 }}</span>
                      <span class="text-dark me-2">{{ s.stopName }}</span>
                      <span class="badge bg-light text-muted border ms-auto me-3">{{ s.stopType }}</span>
                      <small *ngIf="s.distanceKm" class="text-muted">{{ s.distanceKm }} km</small>
                    </button>
                  </h2>
                  <div [id]="'stopCollapse' + i" class="accordion-collapse collapse" data-bs-parent="#routeStopsAccordion">
                    <div class="accordion-body p-3 bg-light">
                      <div class="row g-3">
                        <div class="col-md-6">
                          <small class="fw-bold text-success d-block mb-2"><i class="fa-solid fa-door-open me-1"></i>Boarding Points ({{ s.boardingPoints?.length || 0 }}):</small>
                          <ul class="list-unstyled mb-0 small">
                            <li *ngFor="let bp of s.boardingPoints" class="mb-1 text-dark">
                              &bull; <strong>{{ bp.pointName }}</strong> <span *ngIf="bp.timeSlot" class="text-muted">({{ bp.timeSlot }})</span>
                              <div *ngIf="bp.address" class="text-muted ms-2" style="font-size: 11px;">{{ bp.address }}</div>
                            </li>
                            <li *ngIf="!s.boardingPoints || s.boardingPoints.length === 0" class="text-muted italic">None</li>
                          </ul>
                        </div>
                        <div class="col-md-6">
                          <small class="fw-bold text-danger d-block mb-2"><i class="fa-solid fa-door-closed me-1"></i>Dropping Points ({{ s.droppingPoints?.length || 0 }}):</small>
                          <ul class="list-unstyled mb-0 small">
                            <li *ngFor="let dp of s.droppingPoints" class="mb-1 text-dark">
                              &bull; <strong>{{ dp.pointName }}</strong> <span *ngIf="dp.timeSlot" class="text-muted">({{ dp.timeSlot }})</span>
                              <div *ngIf="dp.address" class="text-muted ms-2" style="font-size: 11px;">{{ dp.address }}</div>
                            </li>
                            <li *ngIf="!s.droppingPoints || s.droppingPoints.length === 0" class="text-muted italic">None</li>
                          </ul>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="modal-footer bg-light px-4 py-2 justify-content-between">
              <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill" (click)="showRouteDetailsModal = false">Close</button>
              <div class="d-flex gap-2">
                <button class="btn btn-outline-primary btn-sm rounded-pill fw-bold" (click)="openEditRouteModal(selectedRouteForDetails); showRouteDetailsModal = false">
                  <i class="fa-solid fa-pen-to-square me-1"></i> Edit Route
                </button>
                <button class="btn btn-success btn-sm rounded-pill fw-bold"
                        [disabled]="selectedRouteForDetails.status === 'INACTIVE'"
                        (click)="openAddScheduleModal(null, selectedRouteForDetails); showRouteDetailsModal = false">
                  <i class="fa-solid fa-clock me-1"></i> Schedule Trip
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ==================== SCHEDULES TOAST NOTIFICATION ==================== -->
      <div *ngIf="schedulesToastMessage" class="schedules-toast-container">
        <div class="alert alert-dismissible shadow-lg border-0 d-flex align-items-center gap-3 py-3 px-4 rounded-3 text-white"
             [class.bg-primary]="schedulesToastType === 'info'"
             [class.bg-success]="schedulesToastType === 'success'"
             [class.bg-danger]="schedulesToastType === 'danger'">
          <i *ngIf="schedulesToastType === 'info'" class="fa-solid fa-circle-info fs-5"></i>
          <i *ngIf="schedulesToastType === 'success'" class="fa-solid fa-circle-check fs-5"></i>
          <i *ngIf="schedulesToastType === 'danger'" class="fa-solid fa-circle-exclamation fs-5"></i>
          <div class="flex-grow-1 small fw-medium">{{ schedulesToastMessage }}</div>
          <button type="button" class="btn-close btn-close-white" (click)="schedulesToastMessage = null"></button>
        </div>
      </div>

      <!-- ==================== SCHEDULE DETAILS MODAL ==================== -->
      <div *ngIf="showScheduleDetailsModal && selectedScheduleForDetails" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-lg modal-dialog-scrollable modal-dialog-centered">
          <div class="modal-content rounded-4 border-0 shadow-lg">
            <div class="modal-header bg-light border-bottom px-4 py-3">
              <div class="d-flex align-items-center gap-2">
                <span class="sched-id-badge fs-6">SCH-{{ selectedScheduleForDetails.id }}</span>
                <h5 class="modal-title fw-bold text-dark mb-0">
                  {{ selectedScheduleForDetails.origin }} <i class="fa-solid fa-arrow-right mx-1 text-muted fs-6"></i> {{ selectedScheduleForDetails.destination }}
                </h5>
                <span class="sched-pill ms-2" [ngClass]="getScheduleStatusPillClass(selectedScheduleForDetails)">
                  <span class="pill-dot"></span>
                  {{ getScheduleComputedStatus(selectedScheduleForDetails) | titlecase }}
                </span>
              </div>
              <button type="button" class="btn-close" (click)="closeScheduleDetailsModal()"></button>
            </div>

            <div class="modal-body p-4">
              <!-- 3 Overview Cards -->
              <div class="row g-3 mb-4">
                <div class="col-md-4">
                  <div class="p-3 bg-light rounded-3 border h-100">
                    <small class="text-muted fw-bold text-uppercase d-block mb-2" style="font-size: 11px;">
                      <i class="fa-solid fa-bus text-primary me-1"></i> Bus Details
                    </small>
                    <div class="fw-bold text-dark fs-6">{{ selectedScheduleForDetails.busNumber || getBusNumber(selectedScheduleForDetails.busId) }}</div>
                    <div class="text-secondary small">{{ selectedScheduleForDetails.busType || getBusType(selectedScheduleForDetails.busId) }}</div>
                    <div class="text-muted small mt-1">Capacity: {{ selectedScheduleForDetails.capacity || getBusCapacity(selectedScheduleForDetails.busId) }} seats</div>
                  </div>
                </div>

                <div class="col-md-4">
                  <div class="p-3 bg-light rounded-3 border h-100">
                    <small class="text-muted fw-bold text-uppercase d-block mb-2" style="font-size: 11px;">
                      <i class="fa-solid fa-clock text-info me-1"></i> Timetable
                    </small>
                    <div class="small text-muted">Departure:</div>
                    <div class="fw-bold text-dark mb-1">
                      {{ formatScheduleDate(selectedScheduleForDetails.travelDate) }} at {{ formatScheduleTime(selectedScheduleForDetails.departureTime) }}
                    </div>
                    <div class="small text-muted">Arrival:</div>
                    <div class="fw-bold text-dark">
                      {{ formatScheduleDate(getArrivalDate(selectedScheduleForDetails)) }} at {{ formatScheduleTime(selectedScheduleForDetails.arrivalTime) }}
                      <span *ngIf="isOvernightSchedule(selectedScheduleForDetails)" class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill ms-1" style="font-size: 10px;">+1d</span>
                    </div>
                    <div class="small text-primary fw-semibold mt-1">Duration: {{ calculateDuration(selectedScheduleForDetails) }}</div>
                  </div>
                </div>

                <div class="col-md-4">
                  <div class="p-3 bg-light rounded-3 border h-100">
                    <small class="text-muted fw-bold text-uppercase d-block mb-2" style="font-size: 11px;">
                      <i class="fa-solid fa-ticket text-success me-1"></i> Fare & Inventory
                    </small>
                    <div class="small text-muted">Starting Fare:</div>
                    <div class="fw-bold text-success fs-5 mb-1">₹ {{ selectedScheduleForDetails.fare | number:'1.0-0' }}</div>
                    <div class="small text-muted">Available Seats:</div>
                    <div class="fw-bold text-dark">
                      {{ selectedScheduleForDetails.availableSeats }} / {{ selectedScheduleForDetails.capacity || getBusCapacity(selectedScheduleForDetails.busId) }}
                    </div>
                    <div class="small text-muted mt-1">Occupancy: {{ getOccupancyPercent(selectedScheduleForDetails) }}%</div>
                  </div>
                </div>
              </div>

              <!-- Stops Breakdown / Timeline -->
              <div class="mb-4">
                <div class="d-flex justify-content-between align-items-center mb-2">
                  <h6 class="fw-bold text-dark mb-0">
                    <i class="fa-solid fa-timeline text-primary me-2"></i>Route Stops & Schedule Timetable
                  </h6>
                  <span *ngIf="isLoadingScheduleDetails" class="spinner-border spinner-border-sm text-primary" role="status"></span>
                </div>

                <div class="table-responsive rounded-3 border">
                  <table class="table table-sm align-middle mb-0">
                    <thead class="table-light">
                      <tr>
                        <th class="ps-3" style="width: 60px;">#</th>
                        <th>Stop Name</th>
                        <th>Type</th>
                        <th>Arrival</th>
                        <th>Departure</th>
                        <th class="pe-3 text-end">Distance</th>
                      </tr>
                    </thead>
                    <tbody>
                      <ng-container *ngIf="selectedScheduleStops && selectedScheduleStops.length > 0; else defaultStopsTpl">
                        <tr *ngFor="let st of selectedScheduleStops">
                          <td class="ps-3 fw-bold text-muted">{{ st.stopOrder + 1 }}</td>
                          <td class="fw-semibold text-dark">{{ st.stopName }}</td>
                          <td>
                            <span class="badge"
                                  [class.bg-primary]="st.stopType === 'ORIGIN'"
                                  [class.bg-secondary]="st.stopType === 'INTERMEDIATE'"
                                  [class.bg-success]="st.stopType === 'DESTINATION'">
                              {{ st.stopType }}
                            </span>
                          </td>
                          <td>{{ formatScheduleTime(st.arrivalTime) || '—' }}</td>
                          <td>{{ formatScheduleTime(st.departureTime) || '—' }}</td>
                          <td class="pe-3 text-end text-muted">{{ st.distanceKm != null ? st.distanceKm + ' km' : '—' }}</td>
                        </tr>
                      </ng-container>
                      <ng-template #defaultStopsTpl>
                        <tr>
                          <td class="ps-3 fw-bold text-muted">1</td>
                          <td class="fw-semibold text-dark">{{ selectedScheduleForDetails.origin }}</td>
                          <td><span class="badge bg-primary">ORIGIN</span></td>
                          <td>—</td>
                          <td>{{ formatScheduleTime(selectedScheduleForDetails.departureTime) }}</td>
                          <td class="pe-3 text-end text-muted">0 km</td>
                        </tr>
                        <tr>
                          <td class="ps-3 fw-bold text-muted">2</td>
                          <td class="fw-semibold text-dark">{{ selectedScheduleForDetails.destination }}</td>
                          <td><span class="badge bg-success">DESTINATION</span></td>
                          <td>{{ formatScheduleTime(selectedScheduleForDetails.arrivalTime) }}</td>
                          <td>—</td>
                          <td class="pe-3 text-end text-muted">—</td>
                        </tr>
                      </ng-template>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            <div class="modal-footer bg-light px-4 py-3 justify-content-between">
              <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill" (click)="closeScheduleDetailsModal()">
                Close
              </button>
              <div class="d-flex gap-2">
                <button class="btn btn-outline-danger btn-sm rounded-pill"
                        *ngIf="selectedScheduleForDetails.status !== 'CANCELLED'"
                        (click)="openCancelScheduleModal(selectedScheduleForDetails)">
                  <i class="fa-solid fa-ban me-1"></i> Cancel Schedule
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ==================== CANCEL SCHEDULE MODAL ==================== -->
      <div *ngIf="showCancelScheduleModal && scheduleToCancel" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content rounded-4 border-0 shadow-lg">
            <div class="modal-header bg-danger-subtle border-bottom px-4 py-3">
              <div class="d-flex align-items-center gap-2 text-danger">
                <i class="fa-solid fa-triangle-exclamation fs-5"></i>
                <h5 class="modal-title fw-bold mb-0">Cancel Bus Schedule</h5>
              </div>
              <button type="button" class="btn-close" (click)="closeCancelScheduleModal()"></button>
            </div>

            <div class="modal-body p-4">
              <p class="text-dark mb-3">
                Are you sure you want to cancel Schedule <strong>SCH-{{ scheduleToCancel.id }}</strong>?
              </p>

              <div class="p-3 bg-light rounded-3 border mb-3">
                <div class="small text-muted mb-1">Route & Timing:</div>
                <div class="fw-bold text-dark">
                  {{ scheduleToCancel.origin }} &rarr; {{ scheduleToCancel.destination }}
                </div>
                <div class="text-secondary small mt-1">
                  {{ formatScheduleDate(scheduleToCancel.travelDate) }} at {{ formatScheduleTime(scheduleToCancel.departureTime) }}
                </div>
                <div class="text-secondary small">
                  Bus: {{ scheduleToCancel.busNumber || getBusNumber(scheduleToCancel.busId) }}
                </div>
              </div>

              <div class="alert alert-warning py-2 px-3 small mb-0 d-flex align-items-start gap-2">
                <i class="fa-solid fa-circle-info mt-1"></i>
                <div>
                  <strong>Notice:</strong> Cancelling this schedule will immediately notify booked passengers, cancel confirmed reservations, and queue automated refunds.
                </div>
              </div>

              <div *ngIf="cancelScheduleError" class="alert alert-danger py-2 px-3 small mt-3 mb-0">
                <i class="fa-solid fa-circle-exclamation me-1"></i> {{ cancelScheduleError }}
              </div>

              <div *ngIf="cancelScheduleSuccess" class="alert alert-success py-2 px-3 small mt-3 mb-0">
                <i class="fa-solid fa-circle-check me-1"></i> Schedule successfully cancelled.
              </div>
            </div>

            <div class="modal-footer bg-light px-4 py-3">
              <button type="button"
                      class="btn btn-outline-secondary btn-sm rounded-pill"
                      [disabled]="isCancellingSchedule"
                      (click)="closeCancelScheduleModal()">
                Keep Schedule
              </button>
              <button type="button"
                      class="btn btn-danger btn-sm rounded-pill fw-semibold d-flex align-items-center gap-2"
                      [disabled]="isCancellingSchedule || cancelScheduleSuccess"
                      (click)="confirmCancelSchedule()">
                <span *ngIf="isCancellingSchedule" class="spinner-border spinner-border-sm" role="status"></span>
                <span>Confirm Cancellation</span>
              </button>
            </div>
          </div>
        </div>
      </div>

    </div>
  `,
  styles: [`
    .operator-dashboard-shell {
      min-height: 100vh;
      background-color: #f8fafc;
      display: flex;
      flex-direction: column;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    }

    .shell-header {
      height: 70px;
      background-color: #ffffff;
      border-bottom: 1px solid #e2e8f0;
      position: sticky;
      top: 0;
      z-index: 1020;
    }

    .te-brand {
      text-decoration: none;
      display: flex;
      align-items: center;
      gap: 10px;
      cursor: pointer;
    }

    .te-logo-icon {
      width: 40px;
      height: 40px;
      background: linear-gradient(135deg, #0284c7 0%, #0ea5e9 100%);
      color: #ffffff;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      box-shadow: 0 2px 6px rgba(2, 132, 199, 0.25);
    }

    .te-brand-title {
      font-size: 21px;
      font-weight: 800;
      color: #0f172a;
      letter-spacing: -0.5px;
    }

    .te-brand-blue {
      color: #0284c7;
    }

    .operator-avatar {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background: linear-gradient(135deg, #0284c7 0%, #0ea5e9 100%);
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 14px;
      border: 2px solid #e0f2fe;
    }

    .hover-bg:hover {
      background-color: #f1f5f9;
    }

    .cursor-pointer {
      cursor: pointer;
    }

    .operator-shell-body {
      display: flex;
      flex: 1;
    }

    .operator-sidebar {
      width: 240px;
      background-color: #ffffff;
      border-right: 1px solid #e2e8f0;
      transition: width 0.2s ease;
      flex-shrink: 0;
    }

    .operator-sidebar.collapsed {
      width: 76px;
    }

    .nav-link-custom {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      border-radius: 12px;
      color: #64748b;
      font-weight: 500;
      font-size: 14px;
      text-decoration: none;
      cursor: pointer;
      transition: all 0.15s ease;
      margin-bottom: 3px;
    }

    .nav-link-custom:hover {
      background-color: #f8fafc;
      color: #0f172a;
    }

    .nav-link-custom.active {
      background-color: #e0f2fe;
      color: #0284c7;
      font-weight: 600;
    }

    .nav-link-custom.active i {
      color: #0284c7;
    }

    .sidebar-icon {
      font-size: 17px;
      width: 22px;
      text-align: center;
      flex-shrink: 0;
    }

    .main-content {
      padding: 28px 32px;
      background-color: #f8fafc;
      overflow-x: hidden;
    }

    /* Metric Summary Cards */
    .metric-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 18px;
      padding: 24px 26px;
      display: flex;
      align-items: center;
      gap: 20px;
      min-height: 114px;
      height: 100%;
      box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
      transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
    }

    .metric-card:hover {
      transform: translateY(-3px);
      box-shadow: 0 12px 24px -4px rgba(15, 23, 42, 0.08), 0 4px 6px -2px rgba(15, 23, 42, 0.03);
      border-color: #cbd5e1;
    }

    .metric-icon-box {
      width: 60px;
      height: 60px;
      border-radius: 16px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 26px;
      flex-shrink: 0;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.03);
    }

    .metric-label {
      font-size: 14px;
      font-weight: 600;
      color: #64748b;
      margin-bottom: 4px;
      letter-spacing: 0.01em;
    }

    .metric-value {
      font-size: clamp(24px, 2vw, 30px);
      font-weight: 800;
      color: #0f172a;
      line-height: 1.15;
      letter-spacing: -0.02em;
    }

    /* Quick Action Cards */
    .quick-action-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 16px;
      padding: 16px 20px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      cursor: pointer;
      transition: all 0.2s ease;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
    }

    .quick-action-card:hover {
      border-color: #38bdf8;
      transform: translateY(-2px);
      box-shadow: 0 6px 16px rgba(14, 165, 233, 0.08);
    }

    .qa-icon-box {
      width: 44px;
      height: 44px;
      background-color: #f0f9ff;
      color: #0284c7;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      margin-right: 14px;
    }

    /* Section Card */
    .section-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 16px;
      padding: 22px 24px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
    }

    .chart-bar-column {
      transition: height 0.3s ease;
      cursor: pointer;
    }

    .chart-bar-column:hover {
      filter: brightness(1.1);
    }

    .badge-status-confirmed {
      background-color: #dcfce7;
      color: #15803d;
      font-weight: 600;
      padding: 4px 10px;
    }

    .badge-status-cancelled {
      background-color: #fee2e2;
      color: #b91c1c;
      font-weight: 600;
      padding: 4px 10px;
    }

    .badge-status-scheduled {
      background-color: #dcfce7;
      color: #15803d;
      font-weight: 600;
      padding: 4px 10px;
    }

        /* Routes Management Page Styles */
    .filter-bar-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      padding: 16px 20px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
    }

    .filter-field {
      display: flex;
      flex-direction: column;
    }

    .filter-label {
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: #64748b;
      margin-bottom: 6px;
      height: 14px;
      line-height: 14px;
    }

    .search-input-group {
      height: 38px;
    }

    .search-input-group .input-group-text {
      border-color: #cbd5e1;
      border-radius: 8px 0 0 8px;
      background-color: #ffffff;
      font-size: 13.5px;
      color: #64748b;
      height: 38px;
    }

    .search-control {
      height: 38px;
      border-color: #cbd5e1;
      border-radius: 0 8px 8px 0;
      font-size: 13.5px;
    }

    .search-control:focus {
      border-color: #0284c7;
      box-shadow: none;
    }

    .search-input-group:focus-within {
      border-radius: 8px;
      box-shadow: 0 0 0 2px rgba(2, 132, 199, 0.15);
    }

    .search-input-group:focus-within .input-group-text,
    .search-input-group:focus-within .search-control {
      border-color: #0284c7;
    }

    .filter-select {
      height: 38px;
      border-radius: 8px;
      border: 1px solid #cbd5e1;
      font-size: 13.5px;
      color: #1e293b;
      padding: 6px 12px;
      background-color: #ffffff;
    }

    .filter-select:focus {
      border-color: #0284c7;
      box-shadow: 0 0 0 2px rgba(2, 132, 199, 0.15);
    }

    .more-filters-btn {
      height: 38px;
      border-radius: 8px;
      border: 1px solid #cbd5e1;
      background-color: #ffffff;
      color: #475569;
      font-size: 13px;
      font-weight: 600;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      transition: all 0.15s ease;
    }

    .more-filters-btn:hover {
      background-color: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }

    .more-filters-btn.active {
      background-color: #e0f2fe;
      border-color: #0284c7;
      color: #0284c7;
    }

    .more-filters-btn .filter-badge {
      font-size: 11px;
      padding: 2px 7px;
    }

    .more-filters-tray {
      background: #fafbfc;
      border-radius: 10px;
      padding: 16px;
      border: 1px solid #eef2f6;
    }

    .routes-table-card {
      overflow: visible;
      border-radius: 16px;
    }

    .routes-table-container {
      min-height: 420px;
      position: relative;
      border-radius: 16px;
    }

    @media (min-width: 992px) {
      .routes-table-card {
        overflow: visible !important;
      }
      .routes-table-container {
        overflow: visible !important;
      }
    }

    .routes-table {
      border-collapse: separate;
      border-spacing: 0;
    }

    .routes-table thead th:first-child {
      border-top-left-radius: 16px;
    }

    .routes-table thead th:last-child {
      border-top-right-radius: 16px;
    }

    .routes-table th {
      font-size: 12px;
      font-weight: 600;
      color: #64748b;
      background-color: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
      padding: 14px 16px;
      white-space: nowrap;
    }

    .routes-table td {
      padding: 16px;
      vertical-align: middle;
      border-bottom: 1px solid #f1f5f9;
      font-size: 13.5px;
    }

    .routes-table tr:hover td {
      background-color: #f8fafc;
    }

    .badge-route-id {
      background-color: #e0f2fe;
      color: #0284c7;
      font-weight: 700;
      font-size: 12px;
      padding: 6px 10px;
      border-radius: 8px;
      letter-spacing: 0.3px;
      display: inline-block;
    }

    .badge-status-active {
      background-color: #dcfce7;
      color: #15803d;
      font-weight: 600;
      font-size: 12px;
      padding: 5px 12px;
      border-radius: 20px;
      display: inline-flex;
      align-items: center;
    }

    .badge-status-draft {
      background-color: #fef3c7;
      color: #b45309;
      font-weight: 600;
      font-size: 12px;
      padding: 5px 12px;
      border-radius: 20px;
      display: inline-flex;
      align-items: center;
    }

    .badge-status-inactive {
      background-color: #f1f5f9;
      color: #64748b;
      font-weight: 600;
      font-size: 12px;
      padding: 5px 12px;
      border-radius: 20px;
      display: inline-flex;
      align-items: center;
    }

    .action-trigger-btn {
      width: 32px;
      height: 32px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      transition: all 0.15s ease;
    }

    .action-trigger-btn:hover,
    .action-trigger-btn.active {
      background-color: #e2e8f0 !important;
      color: #0f172a !important;
    }

    .route-action-menu {
      position: absolute;
      right: 14px;
      top: 38px;
      z-index: 1060;
      min-width: 195px;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      box-shadow: 0 14px 35px -4px rgba(15, 23, 42, 0.16), 0 4px 6px -2px rgba(15, 23, 42, 0.05);
      padding: 6px;
      animation: fadeInScale 0.15s ease-out;
    }

    .route-action-menu.dropup-menu {
      top: auto;
      bottom: 38px;
      transform-origin: bottom right;
    }

    .action-menu-btn {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 9px 12px;
      font-size: 13px;
      font-weight: 500;
      color: #334155;
      background: transparent;
      border: none;
      border-radius: 8px;
      cursor: pointer;
      transition: background-color 0.15s ease, color 0.15s ease;
      text-align: left;
    }

    .action-menu-btn:hover:not(:disabled) {
      background-color: #f1f5f9;
      color: #0f172a;
    }

    .action-menu-btn:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }

    .action-menu-btn i {
      font-size: 13px;
      width: 16px;
      text-align: center;
    }

    .action-menu-btn.danger:hover {
      background-color: #fef2f2;
      color: #dc2626;
    }

    .action-menu-btn.success:hover {
      background-color: #f0fdf4;
      color: #16a34a;
    }

    .action-menu-divider {
      height: 1px;
      background-color: #f1f5f9;
      margin: 4px 0;
    }

    @keyframes fadeInScale {
      from {
        opacity: 0;
        transform: scale(0.96);
      }
      to {
        opacity: 1;
        transform: scale(1);
      }
    }

    /* Create Route Wizard Styles (Phase 5.3) */
    .wizard-progress-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      padding: 16px 24px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
    }

    .wizard-progress-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      overflow-x: auto;
      gap: 12px;
    }

    .wizard-step-item {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-shrink: 0;
    }

    .wizard-step-circle {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      font-weight: 700;
      transition: all 0.2s ease;
    }

    .wizard-step-circle.active {
      background-color: #0066cc;
      color: #ffffff;
      box-shadow: 0 0 0 4px rgba(0, 102, 204, 0.15);
    }

    .wizard-step-circle.inactive {
      background-color: #e0f2fe;
      color: #0284c7;
    }

    .wizard-step-circle.completed {
      background-color: #10b981;
      color: #ffffff;
    }

    .wizard-step-title {
      font-size: 13.5px;
      font-weight: 600;
      color: #64748b;
      white-space: nowrap;
    }

    .wizard-step-title.active {
      color: #0f172a;
      font-weight: 700;
    }

    .wizard-step-line {
      flex: 1;
      min-width: 24px;
      height: 2px;
      background-color: #e2e8f0;
    }

    .wizard-step-line.completed {
      background-color: #10b981;
    }

    .wizard-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 16px;
      padding: 24px 28px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
    }

    .wizard-card-icon {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      flex-shrink: 0;
    }

    .wizard-control {
      border-radius: 10px;
      border: 1px solid #cbd5e1;
      font-size: 14px;
      padding: 9px 14px;
      color: #1e293b;
      transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }

    .wizard-control:focus {
      border-color: #0284c7;
      box-shadow: 0 0 0 2px rgba(2, 132, 199, 0.15);
    }

    .endpoint-input-group .input-group-text {
      border-radius: 10px 0 0 10px;
      border: 1px solid #cbd5e1;
      padding: 0 14px;
    }

    .endpoint-input-group .form-select {
      border-radius: 0 10px 10px 0;
    }

    .endpoint-input-group:focus-within {
      border-radius: 10px;
      box-shadow: 0 0 0 2px rgba(2, 132, 199, 0.15);
    }

    .endpoint-input-group:focus-within .input-group-text,
    .endpoint-input-group:focus-within .form-select {
      border-color: #0284c7;
    }

    .endpoint-input-group.is-invalid {
      border-radius: 10px;
      border: 1px solid #ef4444;
    }

    .back-to-routes-link {
      transition: color 0.15s ease;
    }

    .back-to-routes-link:hover {
      color: #0369a1 !important;
      text-decoration: underline !important;
    }

    .wizard-btn-cancel {
      border-color: #cbd5e1;
      color: #475569;
      background: #ffffff;
    }

    .wizard-btn-cancel:hover {
      background: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }

    .wizard-btn-next {
      background-color: #0066cc;
      border-color: #0066cc;
    }

    .wizard-btn-next:hover {
      background-color: #0052a3;
      border-color: #0052a3;
    }

    .status-dropdown-menu {
      position: absolute;
      top: calc(100% + 4px);
      left: 0;
      right: 0;
      z-index: 1050;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      box-shadow: 0 10px 25px -5px rgba(15, 23, 42, 0.12), 0 4px 6px -2px rgba(15, 23, 42, 0.05);
      overflow: hidden;
      animation: fadeInScale 0.15s ease-out;
    }

    .status-dropdown-item {
      background: transparent;
      border: none;
      text-align: left;
      font-size: 13.5px;
      color: #334155;
      cursor: pointer;
      transition: background-color 0.15s ease, color 0.15s ease;
    }

    .status-dropdown-item:hover {
      background-color: #f1f5f9;
      color: #0f172a;
    }

    .status-dropdown-item.active-item {
      background-color: #e0f2fe;
      color: #0284c7;
      font-weight: 600;
    }

    /* Step 2 Add Stops Styles (Phase 5.3.2) */
    .route-summary-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 18px 28px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
      width: 100%;
    }

    .route-summary-icon {
      width: 46px;
      height: 46px;
      border-radius: 10px;
      background-color: #e0f2fe;
      color: #0284c7;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      flex-shrink: 0;
    }

    .route-summary-content {
      display: flex;
      align-items: center;
      flex: 1 1 auto;
      width: 100%;
    }

    .route-summary-item {
      display: flex;
      flex-direction: column;
      justify-content: center;
    }

    .route-summary-item.item-name {
      flex: 1.3 1 0;
      padding-right: 28px;
    }

    .route-summary-item.item-id {
      flex: 0.9 1 0;
      padding-left: 28px;
      padding-right: 28px;
    }

    .route-summary-item.item-route {
      flex: 1.6 1 0;
      padding-left: 28px;
      padding-right: 24px;
    }

    .route-summary-divider {
      width: 1px;
      height: 38px;
      background-color: #e2e8f0;
      flex-shrink: 0;
    }

    .route-summary-label {
      font-size: 12px;
      font-weight: 500;
      color: #475569;
      margin-bottom: 3px;
      line-height: 1.2;
    }

    .route-summary-val {
      font-size: 15px;
      font-weight: 600;
      color: #1e1b4b;
      line-height: 1.3;
      word-break: break-word;
    }

    .route-summary-status {
      flex-shrink: 0;
    }

    .route-summary-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 6px 18px;
      font-size: 13px;
      font-weight: 500;
      border-radius: 8px;
      letter-spacing: 0.2px;
    }

    .route-summary-badge.badge-draft {
      background-color: #fef3c7;
      color: #b45309;
    }

    .route-summary-badge.badge-active {
      background-color: #dcfce7;
      color: #15803d;
    }

    .route-summary-badge.badge-inactive {
      background-color: #f1f5f9;
      color: #64748b;
    }

    @media (max-width: 991px) {
      .route-summary-card > div {
        flex-direction: column;
        align-items: flex-start !important;
        gap: 16px;
      }
      .route-summary-content {
        flex-direction: column;
        align-items: flex-start;
        gap: 12px;
        width: 100%;
      }
      .route-summary-divider {
        display: none;
      }
      .route-summary-item {
        padding: 0 !important;
        width: 100%;
      }
      .route-summary-status {
        margin-left: 0 !important;
        width: 100%;
      }
    }

    .add-stop-inline-card {
      background: #f8fafc;
      border: 1px dashed #0284c7 !important;
    }

    .stops-table th {
      font-size: 12px;
      font-weight: 600;
      color: #64748b;
      background-color: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
      padding: 12px 16px;
    }

    .stops-table td {
      padding: 14px 16px;
      border-bottom: 1px solid #f1f5f9;
      font-size: 13.5px;
    }

    .stepper-cell {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 48px;
    }

    .seq-circle {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
      font-weight: 700;
      z-index: 2;
    }

    .seq-circle.origin {
      background-color: #e0f2fe;
      color: #0284c7;
    }

    .seq-circle.intermediate {
      background-color: #f1f5f9;
      color: #64748b;
      border: 1px solid #e2e8f0;
    }

    .seq-circle.destination {
      background-color: #fee2e2;
      color: #b91c1c;
    }

    .stepper-track-up {
      position: absolute;
      top: 0;
      bottom: 50%;
      width: 2px;
      background-color: #e2e8f0;
      z-index: 1;
    }

    .stepper-track-down {
      position: absolute;
      top: 50%;
      bottom: 0;
      width: 2px;
      background-color: #e2e8f0;
      z-index: 1;
    }

    .badge-type-origin {
      background-color: #e0f2fe;
      color: #0284c7;
      font-weight: 600;
      font-size: 12px;
      padding: 4px 12px;
      border-radius: 20px;
      display: inline-block;
    }

    .badge-type-intermediate {
      background-color: #ede9fe;
      color: #6d28d9;
      font-weight: 600;
      font-size: 12px;
      padding: 4px 12px;
      border-radius: 20px;
      display: inline-block;
    }

    .badge-type-destination {
      background-color: #fee2e2;
      color: #b91c1c;
      font-weight: 600;
      font-size: 12px;
      padding: 4px 12px;
      border-radius: 20px;
      display: inline-block;
    }

    .btn-icon-action {
      width: 32px;
      height: 32px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 6px;
      border: 1px solid transparent;
      background: transparent;
      color: #64748b;
      transition: all 0.15s ease;
    }

    .btn-icon-action:hover:not(:disabled) {
      background-color: #f1f5f9;
      color: #0f172a;
      border-color: #e2e8f0;
    }

    .btn-icon-action.text-danger:hover:not(:disabled) {
      background-color: #fef2f2;
      color: #dc2626 !important;
      border-color: #fecaca;
    }

    .btn-icon-action:disabled {
      opacity: 0.35;
      cursor: not-allowed;
    }

    .guidelines-card {
      background: #f0f9ff;
      border: 1px solid #bae6fd;
      border-radius: 14px;
      padding: 20px 24px;
    }

    /* Step 3: Configure Boarding & Dropping Points Styles (Phase 5.3.3) */
    .stop-selector-group {
      border: 1px solid #cbd5e1;
      border-radius: 10px;
      overflow: hidden;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
    }

    .stop-selector-select {
      font-size: 14px;
      color: #0f172a;
      padding: 10px 14px;
      cursor: pointer;
    }

    .stop-selector-select:focus {
      box-shadow: none;
      border-color: transparent;
    }

    .points-col-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
      transition: all 0.2s ease;
    }

    .points-col-boarding {
      border-top: 3px solid #22c55e;
    }

    .points-col-dropping {
      border-top: 3px solid #f59e0b;
    }

    .points-col-header {
      background-color: #fafafa;
    }

    .points-header-icon {
      width: 36px;
      height: 36px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      flex-shrink: 0;
    }

    .points-header-icon.icon-boarding {
      background-color: #dcfce7;
      color: #15803d;
    }

    .points-header-icon.icon-dropping {
      background-color: #fef3c7;
      color: #b45309;
    }

    .btn-add-boarding {
      border: 1px solid #22c55e;
      background: #ffffff;
      color: #15803d;
      font-weight: 600;
      font-size: 12px;
      padding: 6px 12px;
      border-radius: 8px;
      transition: all 0.15s ease;
    }

    .btn-add-boarding:hover {
      background-color: #f0fdf4;
      color: #166534;
      border-color: #16a34a;
    }

    .btn-add-dropping {
      border: 1px solid #f59e0b;
      background: #ffffff;
      color: #b45309;
      font-weight: 600;
      font-size: 12px;
      padding: 6px 12px;
      border-radius: 8px;
      transition: all 0.15s ease;
    }

    .btn-add-dropping:hover {
      background-color: #fffbeb;
      color: #92400e;
      border-color: #d97706;
    }

    .points-table th {
      font-size: 11px;
      font-weight: 600;
      color: #64748b;
      background-color: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
      padding: 10px 12px;
    }

    .points-table td {
      font-size: 13px;
      padding: 12px 12px;
      border-bottom: 1px solid #f1f5f9;
    }

    .time-slot-badge {
      display: inline-flex;
      align-items: center;
      background-color: #f1f5f9;
      border: 1px solid #e2e8f0;
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 500;
      color: #334155;
    }

    .points-count-banner {
      padding: 8px 14px;
      border-radius: 8px;
      font-size: 12px;
      font-weight: 600;
    }

    .points-count-banner.banner-boarding {
      background-color: #dcfce7;
      color: #15803d;
    }

    .points-count-banner.banner-dropping {
      background-color: #fef3c7;
      color: #b45309;
    }

    .empty-points-state {
      padding: 24px 16px;
      background-color: #fafafa;
      border: 1px dashed #e2e8f0;
      border-radius: 8px;
      margin: 8px 0;
    }

    .page-btn {
      width: 32px;
      height: 32px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 8px;
      font-size: 13px;
      font-weight: 600;
      border: 1px solid #e2e8f0;
      background: #ffffff;
      color: #334155;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .page-btn:hover:not(:disabled) {
      background-color: #f1f5f9;
    }

    .page-btn.active {
      background-color: #0284c7;
      border-color: #0284c7;
      color: #ffffff;
    }

    .page-btn:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }

    /* ==================== STEP 4: REVIEW & CREATE STYLES (Phase 5.3.4) ==================== */
    .step4-review-container {
      animation: fadeIn 0.25s ease-in-out;
    }

    .review-details-grid {
      width: 100%;
    }

    .review-field-box {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 4px 8px;
    }

    .review-divider-left {
      border-left: 1px solid #e2e8f0;
      padding-left: 20px;
    }

    .review-field-label {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.05em;
      color: #64748b;
      text-transform: uppercase;
    }

    .review-field-value {
      font-size: 14px;
      color: #0f172a;
    }

    .review-row-separator {
      border-top: 1px solid #f1f5f9;
      width: 100%;
    }

    .review-stops-timeline {
      display: flex;
      flex-direction: column;
    }

    .review-stop-wrapper {
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      overflow: hidden;
      background: #ffffff;
      transition: all 0.2s ease;
    }

    .review-stop-wrapper:hover {
      border-color: #cbd5e1;
    }

    .review-stop-row {
      background: #ffffff;
      transition: background 0.15s ease;
    }

    .review-stop-row:hover {
      background: #f8fafc;
    }

    .review-stop-row.expanded {
      background: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
    }

    .review-stop-seq-circle {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
      font-weight: 700;
    }

    .seq-origin {
      background-color: #e0f2fe;
      color: #0284c7;
    }

    .seq-intermediate {
      background-color: #f1f5f9;
      color: #475569;
    }

    .seq-destination {
      background-color: #fee2e2;
      color: #dc2626;
    }

    .transition-chevron {
      transition: transform 0.2s ease;
      font-size: 13px;
    }

    .review-points-panel {
      background-color: #f8fafc;
    }

    .review-mini-table {
      font-size: 13px;
    }

    .review-mini-table th {
      font-size: 11px;
      text-transform: uppercase;
      font-weight: 700;
      letter-spacing: 0.04em;
      color: #64748b;
      padding: 8px 12px;
    }

    .review-mini-table td {
      padding: 8px 12px;
    }

    .validation-summary-card {
      background-color: #f0f9ff;
      border: 1px solid #bae6fd;
      border-radius: 14px;
      padding: 20px 24px;
    }

    .validation-summary-icon-box {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background-color: #e0f2fe;
      color: #0284c7;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      flex-shrink: 0;
    }

    .validation-checklist {
      margin-left: 48px;
    }

    @media (max-width: 991px) {
      .review-divider-left {
        border-left: none;
        padding-left: 8px;
        border-top: 1px solid #f1f5f9;
        padding-top: 12px;
      }
      .validation-checklist {
        margin-left: 0;
        margin-top: 12px;
      }
    }

    @media (max-width: 768px) {
      .main-content {
        padding: 16px 14px;
      }
      .operator-sidebar {
        position: fixed;
        top: 70px;
        bottom: 0;
        left: 0;
        z-index: 1010;
        box-shadow: 2px 0 10px rgba(0, 0, 0, 0.1);
      }
      .operator-sidebar.collapsed {
        transform: translateX(-100%);
        width: 240px;
      }
    }

    /* ==================== SCHEDULES DASHBOARD (PHASE 6.2A) ==================== */
    .schedules-dashboard-container {
      padding-bottom: 2rem;
    }

    .schedules-title {
      font-size: 24px;
      font-weight: 800;
      color: #0f172a;
      letter-spacing: -0.02em;
    }

    .schedules-subtitle {
      font-size: 14px;
      color: #64748b;
    }

    .btn-export {
      background-color: #ffffff;
      color: #334155;
      border: 1px solid #cbd5e1;
      font-size: 13px;
      font-weight: 600;
      padding: 8px 16px;
      border-radius: 8px;
      transition: all 0.2s ease;
    }

    .btn-export:hover {
      background-color: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }

    .btn-create-schedule {
      background-color: #2563eb;
      color: #ffffff;
      border: 1px solid #2563eb;
      font-size: 13px;
      font-weight: 600;
      padding: 8px 18px;
      border-radius: 8px;
      box-shadow: 0 1px 2px rgba(37, 99, 235, 0.15);
      transition: all 0.2s ease;
    }

    .btn-create-schedule:hover {
      background-color: #1d4ed8;
      border-color: #1d4ed8;
    }

    /* Metric cards */
    .schedules-stat-card {
      background-color: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 16px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      cursor: pointer;
      transition: all 0.2s ease;
      height: 100%;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }

    .schedules-stat-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
      border-color: #cbd5e1;
    }

    .schedules-stat-card.active-filter {
      border-color: #2563eb;
      box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2);
    }

    .stat-label {
      font-size: 13px;
      font-weight: 600;
      color: #64748b;
    }

    .stat-icon-wrapper {
      width: 32px;
      height: 32px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
    }

    .stat-icon-wrapper.total-icon {
      background-color: #eff6ff;
      color: #2563eb;
    }

    .stat-icon-wrapper.upcoming-icon {
      background-color: #ecfdf5;
      color: #10b981;
    }

    .stat-icon-wrapper.ongoing-icon {
      background-color: #eff6ff;
      color: #3b82f6;
    }

    .stat-icon-wrapper.completed-icon {
      background-color: #fef3c7;
      color: #d97706;
    }

    .stat-icon-wrapper.cancelled-icon {
      background-color: #fee2e2;
      color: #ef4444;
    }

    .stat-value {
      font-size: 26px;
      font-weight: 800;
      color: #0f172a;
      line-height: 1.1;
    }

    .stat-trend {
      font-size: 12px;
      font-weight: 600;
    }

    .stat-progress-section {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .stat-percentage {
      font-size: 12px;
      font-weight: 600;
      text-align: right;
    }

    .stat-progress-bar {
      height: 5px;
      border-radius: 9999px;
      background-color: #f1f5f9;
      overflow: hidden;
    }

    /* Filter card */
    .schedules-filter-card {
      border: 1px solid #e2e8f0;
      background-color: #ffffff;
    }

    .search-input-group .form-control {
      font-size: 13px;
    }

    .schedules-filter-card .form-select,
    .schedules-filter-card .form-control {
      font-size: 13px;
      border-color: #cbd5e1;
      border-radius: 8px;
    }

    .schedules-filter-card .form-select:focus,
    .schedules-filter-card .form-control:focus {
      border-color: #2563eb;
      box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
    }

    /* Schedules Table */
    .schedules-table {
      font-size: 13px;
    }

    .schedules-table thead th {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.05em;
      color: #475569;
      background-color: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
      padding: 12px 14px;
      vertical-align: middle;
    }

    .schedules-table tbody td {
      padding: 12px 14px;
      vertical-align: middle;
      border-bottom: 1px solid #f1f5f9;
      color: #334155;
    }

    .schedules-table tbody tr:hover {
      background-color: #f8fafc;
    }

    .sched-id-badge {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 12px;
      font-weight: 700;
      color: #1e293b;
      background-color: #f1f5f9;
      padding: 3px 8px;
      border-radius: 6px;
      border: 1px solid #e2e8f0;
      white-space: nowrap;
    }

    .route-icon-badge {
      width: 32px;
      height: 32px;
      min-width: 32px;
      background-color: #eff6ff;
      color: #2563eb;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
    }

    .duration-text {
      font-weight: 600;
      color: #475569;
      white-space: nowrap;
    }

    .fare-text {
      font-weight: 700;
      color: #0f172a;
      white-space: nowrap;
    }

    .seats-text {
      white-space: nowrap;
    }

    /* Status Pills */
    .sched-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: 9999px;
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
    }

    .sched-pill .pill-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      display: inline-block;
    }

    .sched-pill.pill-upcoming {
      background-color: #def7ec;
      color: #03543f;
    }
    .sched-pill.pill-upcoming .pill-dot {
      background-color: #0e9f6e;
    }

    .sched-pill.pill-ongoing {
      background-color: #e1effe;
      color: #1e429f;
    }
    .sched-pill.pill-ongoing .pill-dot {
      background-color: #3f83f8;
      box-shadow: 0 0 0 2px rgba(63, 131, 248, 0.3);
    }

    .sched-pill.pill-completed {
      background-color: #f3f4f6;
      color: #4b5563;
    }
    .sched-pill.pill-completed .pill-dot {
      background-color: #9ca3af;
    }

    .sched-pill.pill-cancelled {
      background-color: #fde8e8;
      color: #9b1c1c;
    }
    .sched-pill.pill-cancelled .pill-dot {
      background-color: #f05252;
    }

    .sched-pill.pill-scheduled {
      background-color: #ede9fe;
      color: #5521b5;
    }
    .sched-pill.pill-scheduled .pill-dot {
      background-color: #7e3af2;
    }

    /* Action Dropdown Menu */
    .sched-action-dropdown {
      position: absolute;
      right: 14px;
      top: 100%;
      background-color: #ffffff;
      min-width: 230px;
      z-index: 1050;
      padding: 6px 0;
      border-radius: 8px;
      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.08);
      text-align: left;
    }

    .sched-action-dropdown .dropdown-item {
      font-size: 13px;
      font-weight: 500;
      color: #334155;
      background: none;
      border: none;
      width: 100%;
      cursor: pointer;
      text-align: left;
      transition: background-color 0.15s ease;
    }

    .sched-action-dropdown .dropdown-item:hover:not(:disabled) {
      background-color: #f1f5f9;
      color: #0f172a;
    }

    .sched-action-dropdown .dropdown-item:disabled {
      cursor: not-allowed;
      opacity: 0.6;
    }

    /* Empty state */
    .empty-icon-circle {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background-color: #f1f5f9;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    /* Toast Container */
    .schedules-toast-container {
      position: fixed;
      top: 85px;
      right: 24px;
      z-index: 1090;
      max-width: 420px;
      animation: fadeInSlide 0.3s ease;
    }

    /* ==================== CREATE SCHEDULE WIZARD (PHASE 6.2B) ==================== */
    .create-schedule-wizard-container {
      max-width: 1360px;
      margin: 0 auto;
    }

    .wizard-heading {
      font-size: 26px;
      font-weight: 800;
      color: #0f172a;
      letter-spacing: -0.02em;
    }

    .wizard-subheading {
      font-size: 14px;
      color: #64748b;
    }

    /* Six-Step Progress Card */
    .wizard-progress-card {
      background-color: #ffffff;
      border: 1px solid #e2e8f0;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
    }

    .wizard-steps-horizontal {
      width: 100%;
      overflow-x: auto;
      padding-bottom: 2px;
    }

    .wizard-step-item {
      display: flex;
      align-items: center;
      gap: 12px;
      white-space: nowrap;
      flex-shrink: 0;
    }

    .wizard-step-circle {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      font-weight: 700;
      transition: all 0.2s ease;
    }

    .wizard-step-item.active .wizard-step-circle {
      background-color: #2563eb;
      color: #ffffff;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
    }

    .wizard-step-item.completed .wizard-step-circle {
      background-color: #2563eb;
      color: #ffffff;
    }

    .wizard-step-item.upcoming .wizard-step-circle {
      background-color: #ffffff;
      border: 1.5px solid #cbd5e1;
      color: #64748b;
    }

    .wizard-step-info {
      display: flex;
      flex-direction: column;
    }

    .wizard-step-title {
      font-size: 13.5px;
      font-weight: 600;
      line-height: 1.2;
    }

    .wizard-step-item.active .wizard-step-title {
      color: #2563eb;
      font-weight: 700;
    }

    .wizard-step-item.completed .wizard-step-title {
      color: #0f172a;
    }

    .wizard-step-item.upcoming .wizard-step-title {
      color: #475569;
    }

    .wizard-step-desc {
      font-size: 11.5px;
      color: #94a3b8;
      margin-top: 2px;
    }

    .wizard-step-connector {
      height: 2px;
      background-color: #e2e8f0;
      flex-grow: 1;
      margin: 0 16px;
      min-width: 20px;
    }

    .wizard-step-connector.completed {
      background-color: #2563eb;
    }

    /* Step 3: Configure Timings Styles */
    .context-summary-card {
      border: 1px solid #e2e8f0 !important;
      border-radius: 14px !important;
    }

    .context-icon-circle {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .context-summary-card .metric-icon-box {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: #eff6ff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
    }

    .context-summary-card .metric-label {
      font-size: 11.5px;
      margin-bottom: 1px;
    }

    .context-summary-card .metric-value {
      font-size: 14.5px;
    }

    .schedule-conflict-banner {
      background: #fff5f5;
      border: 1px solid #fed7d7;
      border-radius: 12px;
    }

    .trip-input-card {
      border: 1px solid #e2e8f0 !important;
      border-radius: 14px !important;
    }

    .journey-summary-card {
      background: #f0f7ff !important;
      border: 1px solid #d0e1fd !important;
      border-radius: 14px !important;
    }

        /* ==================== STEP 4: CONFIGURE FARES ==================== */
    .fare-config-card {
      border: 1px solid #e2e8f0 !important;
      border-radius: 14px !important;
    }

    .fare-table thead th {
      background-color: #f8fafc;
      color: #64748b;
      font-size: 12px;
      font-weight: 600;
      padding: 12px 14px;
      border-bottom: 1px solid #e2e8f0;
      white-space: nowrap;
    }

    .fare-table tbody td {
      padding: 12px 14px;
      font-size: 13px;
      color: #1e293b;
      vertical-align: middle;
    }

    .fare-step-badge {
      width: 26px;
      height: 26px;
      border-radius: 50%;
      background: #2563eb;
      color: white;
      font-weight: 700;
      font-size: 13px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    .fare-pill-tag {
      background-color: #ecfdf5;
      color: #10b981;
      font-size: 11px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: 9999px;
    }

    .fare-input-box {
      width: 95px;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      padding: 6px 8px;
      font-size: 13px;
      font-weight: 600;
      color: #1e293b;
      text-align: center;
      transition: border-color 0.15s, box-shadow 0.15s;
    }

    .fare-input-box:focus {
      border-color: #3b82f6;
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
      outline: none;
    }

    .how-it-works-panel {
      background-color: #f0f7ff;
      border: 1px solid #bfdbfe;
      border-radius: 12px;
      padding: 16px 20px;
    }

    .how-it-works-panel ul li {
      position: relative;
      padding-left: 14px;
      color: #475569;
      font-size: 12.5px;
      line-height: 1.5;
    }

    .how-it-works-panel ul li::before {
      content: "•";
      position: absolute;
      left: 0;
      color: #2563eb;
      font-size: 14px;
    }

    .status-badge-auto {
      background-color: #ecfdf5;
      color: #16a34a;
      font-size: 11px;
      font-weight: 600;
      padding: 4px 12px;
      border-radius: 9999px;
      display: inline-block;
    }

    .status-badge-overridden {
      background-color: #fffbeb;
      color: #d97706;
      font-size: 11px;
      font-weight: 600;
      padding: 4px 12px;
      border-radius: 9999px;
      display: inline-block;
    }

    .btn-reset-fare {
      color: #2563eb;
      background: none;
      border: none;
      padding: 0;
      font-size: 12.5px;
      font-weight: 600;
      display: inline-flex;
      align-items: center;
      gap: 4px;
      cursor: pointer;
      text-decoration: none;
    }

    .btn-reset-fare:hover {
      color: #1d4ed8;
      text-decoration: underline;
    }

    .important-notes-banner {
      background-color: #fff8ee;
      border: 1px solid #ffedd5;
      border-radius: 10px;
      padding: 14px 22px;
    }

    .important-notes-header {
      padding-top: 2px;
    }

    .important-notes-list {
      padding-left: 18px;
      margin: 0;
    }

    .important-notes-list li {
      color: #475569;
      font-size: 12.5px;
      line-height: 1.55;
      margin-bottom: 5px;
    }

    .important-notes-list li:last-child {
      margin-bottom: 0;
    }

    .stop-timings-card {
      border: 1px solid #e2e8f0 !important;
      border-radius: 14px !important;
    }

    .stop-timings-table thead th {
      background-color: #f8fafc;
      color: #64748b;
      font-size: 12px;
      font-weight: 600;
      padding: 12px 14px;
      border-bottom: 1px solid #e2e8f0;
      text-transform: capitalize;
    }

    .stop-timings-table tbody td {
      padding: 12px 14px;
      font-size: 13px;
      color: #1e293b;
    }

    .stop-number-circle {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: #3b82f6;
      color: white;
      font-weight: 700;
      font-size: 13px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    .timing-pill-input {
      background-color: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      max-width: 230px;
    }

    .timing-pill-input input {
      font-size: 12px;
      font-weight: 500;
      color: #1e293b;
      outline: none;
    }

    .timing-disabled-box {
      background-color: #f1f5f9;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 6px 12px;
      color: #94a3b8;
      font-weight: 600;
      text-align: center;
      max-width: 230px;
    }

    .halt-duration-pill {
      background-color: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      display: inline-block;
      min-width: 60px;
      text-align: center;
    }

    .stop-badge-boarding {
      background-color: rgba(37, 99, 235, 0.1) !important;
      color: #2563eb !important;
    }

    .stop-badge-dropping {
      background-color: rgba(245, 158, 11, 0.12) !important;
      color: #d97706 !important;
    }

    .stop-badge-both {
      background-color: rgba(14, 165, 233, 0.12) !important;
      color: #0284c7 !important;
    }

    /* Select Route Workspace Card */
    .route-selection-workspace {
      background-color: #ffffff;
      border: 1px solid #e2e8f0;
    }

    .route-panel-icon-badge {
      width: 42px;
      height: 42px;
      border-radius: 10px;
      background-color: #eff6ff;
      color: #2563eb;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
    }

    .search-route-input-group .form-control {
      font-size: 13px;
      border-color: #e2e8f0;
      border-radius: 8px;
    }

    .search-route-input-group .form-control:focus {
      border-color: #2563eb;
      box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
    }

    .route-status-select {
      font-size: 13px;
      border-color: #e2e8f0;
      border-radius: 8px;
    }

    .route-status-select:focus {
      border-color: #2563eb;
      box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
    }

    /* Route Selection Table */
    .route-selection-table {
      font-size: 13px;
    }

    .route-selection-table thead th {
      font-size: 11.5px;
      font-weight: 700;
      letter-spacing: 0.04em;
      color: #64748b;
      background-color: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
      padding: 13px 16px;
      vertical-align: middle;
      white-space: nowrap;
    }

    .route-table-row {
      cursor: pointer;
      transition: background-color 0.15s ease, border-color 0.15s ease;
    }

    .route-table-row td {
      padding: 14px 16px;
      vertical-align: middle;
      border-bottom: 1px solid #f1f5f9;
      color: #334155;
    }

    .route-table-row:hover:not(.selected-row) {
      background-color: #f8fafc;
    }

    .route-table-row.selected-row {
      background-color: #f0f7ff !important;
      position: relative;
    }

    .route-table-row.selected-row td:first-child {
      border-left: 3px solid #2563eb;
    }

    .route-table-row.ineligible-row {
      opacity: 0.55;
      cursor: not-allowed;
    }

    .route-radio-btn {
      width: 18px;
      height: 18px;
      cursor: pointer;
    }

    .route-code-badge {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 12px;
      font-weight: 600;
      color: #1e40af;
      background-color: #eff6ff;
      border: 1px solid #dbeafe;
      padding: 3px 8px;
      border-radius: 6px;
      white-space: nowrap;
    }

    .route-name-text {
      font-size: 13.5px;
      font-weight: 700;
      color: #0f172a;
    }

    .route-endpoints-text {
      font-size: 13px;
      white-space: nowrap;
    }

    /* Route Status Pill */
    .route-status-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: 9999px;
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
    }

    .route-status-pill .status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      display: inline-block;
    }

    .status-pill-active {
      background-color: #def7ec;
      color: #03543f;
    }
    .status-pill-active .status-dot {
      background-color: #0e9f6e;
    }

    .status-pill-inactive {
      background-color: #f3f4f6;
      color: #4b5563;
    }
    .status-pill-inactive .status-dot {
      background-color: #9ca3af;
    }

    .status-pill-draft {
      background-color: #e1effe;
      color: #1e429f;
    }
    .status-pill-draft .status-dot {
      background-color: #3f83f8;
    }

    .btn-view-route-details {
      font-size: 12.5px;
      font-weight: 600;
      color: #2563eb;
      background-color: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 5px 12px;
      transition: all 0.15s ease;
      white-space: nowrap;
    }

    .btn-view-route-details:hover {
      background-color: #eff6ff;
      border-color: #bfdbfe;
      color: #1d4ed8;
    }

    /* Expanded Details Row */
    .route-expanded-details-container {
      background-color: #f8fafc;
      border-top: 1px solid #e2e8f0;
      border-bottom: 1px solid #e2e8f0;
    }

    .route-stat-card {
      border: 1px solid #e2e8f0;
      background-color: #ffffff;
    }

    .stat-card-icon-circle {
      width: 38px;
      height: 38px;
      border-radius: 50%;
      background-color: #eff6ff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
    }

    .stat-card-label {
      font-size: 11.5px;
      font-weight: 600;
      color: #64748b;
    }

    .stat-card-val {
      font-size: 17px;
      font-weight: 800;
      color: #0f172a;
    }

    /* Stops Timeline Table */
    .stops-timeline-table thead th {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.04em;
      color: #475569;
      background-color: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
      padding: 10px 14px;
    }

    .stops-timeline-table tbody td {
      padding: 10px 14px;
      border-bottom: 1px solid #f1f5f9;
      font-size: 12.5px;
    }

    .stop-timeline-wrapper {
      position: relative;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 32px;
    }

    .stop-timeline-line {
      position: absolute;
      left: 11px;
      top: 16px;
      bottom: -22px;
      width: 2px;
      background-color: #2563eb;
      z-index: 1;
    }

    .stop-seq-circle {
      width: 24px;
      height: 24px;
      border-radius: 50%;
      border: 1.5px solid #2563eb;
      background-color: #ffffff;
      color: #2563eb;
      font-weight: 700;
      font-size: 11.5px;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      z-index: 2;
    }

    .stop-bd-pill {
      display: inline-block;
      padding: 3px 10px;
      border-radius: 6px;
      font-size: 11.5px;
      font-weight: 600;
      white-space: nowrap;
    }

    .stop-badge-boarding {
      background-color: #def7ec;
      color: #03543f;
    }

    .stop-badge-both {
      background-color: #e1effe;
      color: #1e429f;
    }

    .stop-badge-dropping {
      background-color: #fef3c7;
      color: #d97706;
    }

    .stop-badge-none {
      background-color: #f1f5f9;
      color: #64748b;
      border: 1px solid #e2e8f0;
    }

    /* Bottom Navigation Buttons */
    .btn-wizard-cancel {
      font-size: 13.5px;
      font-weight: 600;
      color: #334155;
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      padding: 9px 24px;
      border-radius: 8px;
      transition: all 0.2s ease;
    }

    .btn-wizard-cancel:hover {
      background-color: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }

    .btn-wizard-continue {
      font-size: 13.5px;
      font-weight: 600;
      color: #ffffff;
      background-color: #2563eb;
      border: 1px solid #2563eb;
      padding: 9px 26px;
      border-radius: 8px;
      box-shadow: 0 1px 3px rgba(37, 99, 235, 0.2);
      transition: all 0.2s ease;
    }

    .btn-wizard-continue:hover:not(:disabled) {
      background-color: #1d4ed8;
      border-color: #1d4ed8;
    }

    .btn-wizard-continue:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    /* Step 2 Assign Bus Styles */
    .wizard-panel-header {
      padding: 0;
    }

    .wizard-panel-icon-circle {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      background-color: #eff6ff;
      color: #2563eb;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 17px;
      flex-shrink: 0;
    }

    .wizard-panel-title {
      font-size: 16px;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 2px;
      line-height: 1.25;
    }

    .wizard-panel-subtitle {
      font-size: 12.5px;
      color: #64748b;
      margin-bottom: 0;
      line-height: 1.25;
    }

    .wizard-search-filter-controls {
      display: flex;
      align-items: center;
      gap: 13px;
    }

    .wizard-search-input-wrapper {
      position: relative;
      width: 320px;
    }

    .wizard-search-icon {
      position: absolute;
      left: 12px;
      top: 50%;
      transform: translateY(-50%);
      color: #64748b;
      font-size: 13px;
      pointer-events: none;
      z-index: 2;
    }

    .wizard-search-input {
      width: 100%;
      height: 38px;
      padding: 0 14px 0 35px;
      font-size: 12.5px;
      color: #1e293b;
      background-color: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      outline: none;
      transition: all 0.15s ease;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
    }

    .wizard-search-input::placeholder {
      color: #94a3b8;
      font-size: 12.5px;
    }

    .wizard-search-input:focus {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
    }

    .wizard-filter-dropdown-wrapper {
      width: 140px;
      flex-shrink: 0;
    }

    .wizard-filter-select {
      width: 100%;
      height: 38px;
      padding: 0 30px 0 13px;
      font-size: 12.5px;
      font-weight: 500;
      color: #334155;
      background-color: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      outline: none;
      cursor: pointer;
      transition: all 0.15s ease;
      appearance: none;
      -webkit-appearance: none;
      background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3e%3cpath fill='none' stroke='%2364748b' stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='m2 5 6 6 6-6'/%3e%3c/svg%3e");
      background-repeat: no-repeat;
      background-position: right 11px center;
      background-size: 11px 9px;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
    }

    .wizard-filter-select:focus {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
    }

    .btn-wizard-back {
      font-size: 13.5px;
      font-weight: 600;
      color: #334155;
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      padding: 9px 24px;
      border-radius: 8px;
      transition: all 0.2s ease;
    }

    .btn-wizard-back:hover {
      background-color: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }

    /* Refined Step 2 Assign Bus List & Card Styles */
    .bus-list-workspace {
      width: 100%;
      max-width: 100%;
      box-sizing: border-box;
      overflow-x: hidden;
    }

    .bus-header-strip {
      background-color: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 10px 16px;
      margin-bottom: 10px;
      width: 100%;
      box-sizing: border-box;
    }

    .bus-header-label {
      font-size: 12px;
      font-weight: 700;
      color: #475569;
      letter-spacing: 0.02em;
      white-space: nowrap;
    }

    .bus-cards-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
      width: 100%;
      box-sizing: border-box;
    }

    .bus-card-item {
      background-color: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 10px 16px;
      transition: all 0.15s ease-in-out;
      cursor: pointer;
      width: 100%;
      box-sizing: border-box;
    }

    .bus-card-item:hover:not(.selected-card):not(.ineligible-card) {
      background-color: #f8fafc;
      border-color: #cbd5e1;
    }

    .bus-card-item.selected-card {
      background-color: #f8fbff;
      border: 1.5px solid #3b82f6;
      box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.25);
    }

    .bus-card-item.ineligible-card {
      opacity: 0.55;
      cursor: not-allowed;
      background-color: #fafafa;
    }

    .bus-list-grid {
      display: grid;
      grid-template-columns: 28px 52px minmax(85px, 1.1fr) minmax(120px, 1.8fr) minmax(85px, 1fr) minmax(60px, 0.7fr) minmax(65px, 0.7fr) minmax(75px, 0.8fr) 125px;
      align-items: center;
      column-gap: 12px;
      width: 100%;
      box-sizing: border-box;
    }

    .bus-grid-col-radio {
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .bus-radio-control {
      width: 18px;
      height: 18px;
      cursor: pointer;
      accent-color: #2563eb;
      margin: 0;
    }

    .bus-grid-col-thumb {
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .bus-row-thumbnail {
      width: 48px;
      height: 30px;
      object-fit: contain;
      background-color: transparent;
    }

    .bus-reg-no-text {
      font-size: 13px;
      font-weight: 700;
      color: #0f172a;
      white-space: nowrap;
    }

    .bus-name-text {
      font-size: 13px;
      font-weight: 700;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      display: block;
    }

    .bus-type-badge-pill {
      display: inline-block;
      padding: 3px 10px;
      border-radius: 6px;
      font-size: 11.5px;
      font-weight: 600;
      white-space: nowrap;
      line-height: 1.3;
    }

    .type-pill-ac {
      background-color: #e0f2fe;
      color: #0284c7;
    }

    .type-pill-non-ac {
      background-color: #ffedd5;
      color: #c2410c;
    }

    .bus-seats-count {
      font-size: 13px;
      font-weight: 700;
      color: #0f172a;
      white-space: nowrap;
    }

    .bus-layout-summary {
      font-size: 12.5px;
      font-weight: 600;
      color: #1e293b;
      white-space: nowrap;
    }

    .bus-status-badge-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 3px 10px;
      border-radius: 6px;
      font-size: 11.5px;
      font-weight: 600;
      white-space: nowrap;
      line-height: 1.3;
    }

    .status-pill-active {
      background-color: #dcfce7;
      color: #15803d;
    }

    .status-pill-inactive {
      background-color: #f1f5f9;
      color: #64748b;
    }

    .status-indicator-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      display: inline-block;
    }

    .status-pill-active .status-indicator-dot {
      background-color: #16a34a;
    }

    .status-pill-inactive .status-indicator-dot {
      background-color: #94a3b8;
    }

    .bus-grid-col-actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
    }

    .btn-bus-action-details {
      padding: 5px 14px;
      font-size: 12px;
      font-weight: 600;
      color: #2563eb;
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      transition: all 0.15s ease;
      cursor: pointer;
      line-height: 1.3;
      white-space: nowrap;
    }

    .btn-bus-action-details:hover {
      background-color: #eff6ff;
      border-color: #93c5fd;
      color: #1d4ed8;
    }

    .btn-bus-action-toggle {
      background: none;
      border: none;
      padding: 4px;
      color: #334155;
      font-size: 13px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: color 0.15s ease;
    }

    .btn-bus-action-toggle:hover {
      color: #0f172a;
    }

    /* Expanded Details Panel Styles */
    .bus-expanded-panel {
      margin-top: 14px;
      padding-top: 12px;
      cursor: default;
    }

    .bus-expanded-title {
      font-size: 13.5px;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 12px;
    }

    .bus-expanded-content {
      display: flex;
      align-items: center;
      gap: 20px;
      width: 100%;
      box-sizing: border-box;
    }

    .bus-expanded-left {
      flex: 0 0 160px;
      max-width: 160px;
    }

    .bus-photo-frame {
      width: 100%;
      height: 95px;
      display: flex;
      align-items: center;
      justify-content: center;
      background-color: #ffffff;
      border-radius: 8px;
      overflow: hidden;
    }

    .bus-photo-img {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
    }

    .bus-expanded-middle {
      flex: 0 0 250px;
      max-width: 260px;
    }

    .bus-core-specs-box {
      background-color: #f0f7ff;
      border-radius: 8px;
      padding: 12px 16px;
      display: flex;
      flex-direction: column;
      gap: 7px;
    }

    .bus-spec-row {
      display: flex;
      align-items: baseline;
      font-size: 12.5px;
    }

    .bus-spec-row .spec-label {
      width: 105px;
      flex-shrink: 0;
      color: #64748b;
      font-size: 12px;
      font-weight: 500;
    }

    .bus-spec-row .spec-val {
      font-size: 12.5px;
      color: #0f172a;
      font-weight: 500;
    }

    .bus-spec-row .spec-val-bold {
      font-size: 12.5px;
      color: #0f172a;
      font-weight: 700;
    }

    .bus-spec-row .spec-val-semibold {
      font-size: 12.5px;
      color: #0f172a;
      font-weight: 600;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .bus-expanded-divider {
      width: 1px;
      height: 90px;
      background-color: #e2e8f0;
      flex-shrink: 0;
    }

    .bus-expanded-right {
      flex: 1;
      min-width: 0;
    }

    .bus-specs-list {
      display: flex;
      flex-direction: column;
      gap: 7px;
    }

    .bus-amenities-row {
      display: flex;
      align-items: center;
    }

    .bus-amenities-wrap {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 14px;
    }

    .amenity-item {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      color: #334155;
      font-size: 12.5px;
      font-weight: 500;
      white-space: nowrap;
    }

    .amenity-icon {
      color: #475569;
      font-size: 12px;
    }


    /* ==================== STEP 5: REVIEW & CREATE STYLING ==================== */
    .review-section-card {
      background-color: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      overflow: hidden;
      transition: border-color 0.15s ease;
    }

    .review-section-card:hover {
      border-color: #cbd5e1;
    }

    .review-section-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 14px 20px;
      cursor: pointer;
      user-select: none;
      transition: background-color 0.15s ease;
    }

    .review-section-header:hover {
      background-color: #f8fafc;
    }

    .review-section-title {
      font-size: 14.5px;
      font-weight: 700;
      color: #0f172a;
    }

    .review-icon-circle {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background-color: #eff6ff;
      color: #2563eb;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      flex-shrink: 0;
    }

    .review-looks-good-badge {
      background-color: #ecfdf5;
      color: #059669;
      border: 1px solid #a7f3d0;
      border-radius: 50rem;
      padding: 2px 10px;
      font-size: 0.75rem;
      font-weight: 600;
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    .review-header-summary {
      color: #475569;
      font-size: 13px;
      font-weight: 400;
    }

    .review-header-toggle {
      font-size: 13px;
      color: #64748b;
      width: 24px;
      height: 24px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .review-table thead th {
      background-color: #f8fafc;
      color: #64748b;
      font-size: 12px;
      font-weight: 600;
      text-transform: none;
      border-bottom: 1px solid #e2e8f0;
      padding: 10px 14px;
      white-space: nowrap;
    }

    .review-table tbody td {
      padding: 12px 14px;
      font-size: 13px;
      border-bottom: 1px solid #f1f5f9;
      vertical-align: middle;
    }

    .badge-status-auto {
      background-color: #ecfdf5;
      color: #059669;
      border: 1px solid #a7f3d0;
      border-radius: 50rem;
      padding: 2px 10px;
      font-size: 11.5px;
      font-weight: 600;
      display: inline-block;
    }

    .badge-status-overridden {
      background-color: #fffbeb;
      color: #d97706;
      border: 1px solid #fde68a;
      border-radius: 50rem;
      padding: 2px 10px;
      font-size: 11.5px;
      font-weight: 600;
      display: inline-block;
    }

    .validation-summary-card {
      background-color: #ecfdf5;
      border: 1px solid #a7f3d0;
      border-radius: 10px;
      padding: 16px 24px;
    }

    .validation-summary-card-error {
      background-color: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 10px;
      padding: 16px 24px;
    }

    /* ==================== STEP 6 CONFIRMATION PAGE STYLES (PHASE 6.6) ==================== */
    .step6-confirmation-container {
      max-width: 860px;
      margin: 0 auto;
      padding-top: 20px;
    }

    .schedule-success-illustration-wrap {
      width: 360px;
      height: 190px;
    }

    .schedule-confirmation-card {
      max-width: 720px;
      background-color: #f0fdf4;
      border: 1px solid #bbf7d0;
      border-radius: 16px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
      transition: all 0.2s ease;
    }

    .schedule-conf-divider {
      width: 1px;
      height: 58px;
      background-color: #bbf7d0;
    }

    .schedule-conf-status-badge {
      background-color: #dcfce7;
      color: #15803d;
      border: 1px solid #86efac;
      font-size: 13px;
    }

    .schedule-conf-cal-icon {
      width: 48px;
      height: 48px;
    }

    @media (max-width: 767.98px) {
      .schedule-conf-divider {
        width: 100%;
        height: 1px;
      }
      .schedule-conf-left {
        border-bottom: 1px solid #bbf7d0;
      }
      .schedule-success-illustration-wrap {
        width: 300px;
        height: 160px;
      }
      .schedule-success-blob-svg {
        width: 300px;
        height: 160px;
      }
    }

    @media (max-width: 900px) {
      .bus-expanded-content {
        flex-wrap: wrap;
        gap: 16px;
      }
      .bus-expanded-divider {
        display: none;
      }
    }

    /* Add Bus Wizard (Phase 1) Styles */
    .add-bus-stepper-card {
      border: 1px solid #e2e8f0;
      background: #ffffff;
    }
    .add-bus-step-circle {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      font-weight: 700;
      transition: all 0.2s ease;
    }
    .add-bus-step-circle.active {
      background: #2563eb;
      color: #ffffff;
      box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.15);
    }
    .add-bus-step-circle.completed {
      background: #10b981;
      color: #ffffff;
    }
    .add-bus-step-circle.upcoming {
      background: #f1f5f9;
      color: #94a3b8;
      border: 1.5px solid #cbd5e1;
    }
    .add-bus-step-line {
      height: 2px;
      background-color: #e2e8f0;
      transition: background-color 0.2s;
    }
    .add-bus-step-line.completed {
      background-color: #10b981;
    }
    .add-bus-step-label {
      font-size: 13.5px;
    }
    .add-bus-form-card, .add-bus-images-card {
      background: #ffffff;
      border: 1px solid #f1f5f9;
      box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.04);
    }
    .add-bus-icon-box {
      width: 44px;
      height: 44px;
      font-size: 20px;
      flex-shrink: 0;
    }
    .add-bus-amenity-chip {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      background-color: #eff6ff;
      color: #1e40af;
      border: 1px solid #bfdbfe;
      border-radius: 9999px;
      padding: 5px 12px;
      font-size: 12.5px;
      font-weight: 500;
      transition: all 0.15s ease;
    }
    .add-bus-amenity-chip .chip-remove-btn {
      cursor: pointer;
      opacity: 0.6;
      transition: all 0.15s;
      font-size: 12px;
    }
    .add-bus-amenity-chip .chip-remove-btn:hover {
      opacity: 1;
      color: #ef4444;
    }
    .add-bus-preview-box {
      position: relative;
      width: 100%;
      height: 160px;
      border-radius: 12px;
      overflow: hidden;
      border: 1px solid #e2e8f0;
      background-color: #0f172a;
    }
    .add-bus-preview-box img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .add-bus-delete-img-btn {
      position: absolute;
      top: 8px;
      right: 8px;
      background: rgba(255, 255, 255, 0.95);
      border: 1px solid #fee2e2;
      border-radius: 6px;
      width: 30px;
      height: 30px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #ef4444;
      cursor: pointer;
      transition: all 0.15s;
      z-index: 2;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    .add-bus-delete-img-btn:hover {
      background: #fee2e2;
      color: #dc2626;
      transform: scale(1.05);
    }
    .add-bus-dropzone {
      border: 2px dashed #93c5fd;
      background-color: #f8fafc;
      border-radius: 12px;
      height: 160px;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      text-align: center;
      padding: 12px;
      transition: all 0.2s;
    }
    .add-bus-dropzone:hover {
      background-color: #eff6ff;
      border-color: #3b82f6;
    }
    .add-bus-replace-btn {
      border: 1px solid #bfdbfe;
      background-color: #eff6ff;
      border-radius: 8px;
      width: 100%;
      padding: 7px 10px;
      text-align: center;
      color: #2563eb;
      cursor: pointer;
      transition: all 0.15s;
      font-size: 12.5px;
    }
    .add-bus-replace-btn:hover {
      background-color: #dbeafe;
    }
    .add-bus-guidelines-box {
      background-color: #eff6ff;
      border: 1px solid #dbeafe;
      border-radius: 12px;
    }
  `]
})
export class BusOperatorDashboardComponent implements OnInit {
  currentUser: User | null = null;
  activeNav: 'dashboard' | 'buses' | 'routes' | 'schedules' | 'bookings' | 'profile' | 'support' = 'dashboard';
  isSidebarCollapsed = false;

  myBuses: Bus[] = [];
  myRoutes: Route[] = [];
  mySchedules: any[] = [];
  operatorBookings: any[] = [];

  // Create Schedule Wizard Step 2 State (Phase 6.2B.2: Assign Bus)
  wizardSelectedBusId: number | null = null;
  wizardSelectedBus: Bus | null = null;
  expandedBusId: number | null = null;
  wizardBusSearchQuery: string = '';
  wizardBusTypeFilter: string = 'ALL';
  isLoadingBuses: boolean = false;
  busLoadError: string | null = null;

  // Create Schedule Wizard Step 3 State (Phase 6.3: Configure Timings)
  wizardTripStartDate: string = '';
  wizardTripStartTime: string = '06:00';
  wizardTripEndDate: string = '';
  wizardTripEndTime: string = '01:30';

  getOperationalDateKolkata(daysAhead: number = 0): string {
    const d = new Date();
    d.setDate(d.getDate() + daysAhead);
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(d);
  }
  wizardJourneyDurationDisplay: string = '~ 19h 30m';
  wizardTotalDistanceKm: number = 420;
  wizardTotalStopsCount: number = 4;
  wizardIntermediateStopsCount: number = 2;
  showStopSummaryOnly: boolean = false;
  wizardStopTimings: any[] = [];
  timingConflict: any | null = null;
  showConflictingScheduleModal: boolean = false;
  isCheckingTimingConflict: boolean = false;
  timingValidationError: string | null = null;
  timingChronologicalError: string | null = null;
  conflictCheckTimeout: any = null;

  // Create Schedule Wizard Step 5 & 6 State (Phase 6.5: Review & Create)
  reviewSectionExpanded: { [key: number]: boolean } = {
    1: false,
    2: false,
    3: true,
    4: false,
    5: false
  };
  isCreatingSchedule: boolean = false;
  scheduleCreationError: string | null = null;
  scheduleCreationSuccess: boolean = false;
  createdScheduleResult: any = null;

  // Create Schedule Wizard Step 4 State (Phase 6.4: Configure Fares)
  wizardSegmentFares: WizardSegmentFareItem[] = [];
  wizardJourneyFares: WizardJourneyFareItem[] = [];
  fareValidationError: string | null = null;
  wizardTotalFare: number = 1200;
  wizardFareOverrides: any[] = [];

  // Routes Listing & Filter State
  routeViewMode: 'list' | 'create' = 'list';
  routeWizardStep: number = 1;
  routeSearchQuery = '';
  routeStatusFilter = 'ALL';
  routeOriginFilter = 'ALL';
  routeDestinationFilter = 'ALL';
  showMoreFilters = false;
  routeStopsFilter = 'ALL';
  routeDistanceFilter = 'ALL';
  routeScheduleActivityFilter = 'ALL';
  routeSortBy = 'CODE_ASC';
  routeCurrentPage = 1;
  routePageSize = 10;

  // Create Route Wizard Step 1 State (Phase 5.3.1)
  wizardRouteName: string = '';
  wizardRouteCode: string = 'RTE-PENDING';
  wizardOrigin: string = '';
  customOrigin: string = '';
  wizardDestination: string = '';
  customDestination: string = '';
  wizardDescription: string = '';
  wizardStatus: string = 'ACTIVE';
  isStatusDropdownOpen: boolean = false;
  wizardNotes: string = '';
  step1FormSubmitted: boolean = false;
  routeNameError: string | null = null;
  originError: string | null = null;
  destinationError: string | null = null;
  endpointConflictError: string | null = null;
  descriptionError: string | null = null;

  // Create Route Wizard Step 2 State (Phase 5.3.2)
  wizardIntermediateStops: { stopName: string; state?: string | null }[] = [];
  showAddStopRow: boolean = false;
  newStopNameInput: string = '';
  newStopError: string | null = null;
  step2Error: string | null = null;

  // Create Route Wizard Step 4 State (Phase 5.3.4)
  expandedStopsMap: { [key: string]: boolean } = {};
  isAllStopsExpanded: boolean = false;
  isSubmittingRoute: boolean = false;
  createRouteError: string | null = null;

  // Create Route Wizard Step 3 State (Phase 5.3.3)
  wizardStopPointsMap: { [key: string]: { boardingPoints: { pointName: string; address: string; timeSlot: string }[]; droppingPoints: { pointName: string; address: string; timeSlot: string }[] } } = {};
  selectedWizardStopIndex: number = 0;
  showPointModal: boolean = false;
  pointModalMode: 'ADD' | 'EDIT' = 'ADD';
  pointModalType: 'BOARDING' | 'DROPPING' = 'BOARDING';
  pointModalEditIndex: number = -1;
  pointModalName: string = '';
  pointModalAddress: string = '';
  pointModalTime: string = '06:00 AM';
  pointModalError: string | null = null;
  step3ValidationError: string | null = null;
  selectedRouteForDetails: Route | null = null;
  showRouteDetailsModal = false;
  activeActionMenuRouteId: number | null = null;
  isLoadingRoutes = false;
  routeLoadError: string | null = null;

  // Booking Trends state
  selectedTrendRange = '7';
  bookingTrends: TrendDay[] = [];

  // Add Bus Modal State (Legacy)
  showAddBusModal = false;
  newBusNumber = '';
  newBusType = 'AC 2+1 Seater (Single Deck)';
  newFacilityType = 'AC';
  newBusConfiguration = 'SINGLE_DECK';
  newTotalSeats = 36;
  newAmenities = 'Wi-Fi, Charging Point, Water Bottle';

  // Add Bus Wizard State (Phase 1: Basic Details)
  busViewMode: 'list' | 'wizard' = 'list';
  addBusWizardStep: number = 1;
  currentCalendarYear: number = new Date().getFullYear();

  busForm: BusBasicDetailsForm = this.getInitialBusForm();
  busFormErrors: { [key: string]: string } = {};
  isCheckingRegistration = false;
  registrationAvailabilityStatus: 'idle' | 'checking' | 'available' | 'taken' | 'invalid' = 'idle';
  registrationAvailabilityMessage: string = '';
  selectedAmenityCandidate: string = '';
  isUploadingImage: { [key: string]: boolean } = { exterior: false, interior: false };
  uploadImageError: { [key: string]: string | null } = { exterior: null, interior: null };
  showCancelAddBusModal = false;

  // Catalogs for Basic Details
  readonly manufacturerCatalog: string[] = [
    'Volvo', 'Scania', 'Ashok Leyland', 'Tata Motors', 'Eicher', 'BharatBenz', 'Mercedes-Benz', 'Force Motors'
  ];

  readonly busTypeCatalog: string[] = [
    'Seater', 'Sleeper', 'Seater Cum Sleeper'
  ];

  readonly fuelTypeCatalog: string[] = [
    'Diesel', 'CNG', 'Electric', 'Petrol', 'LNG', 'Hybrid'
  ];

  readonly emissionStandardCatalog: string[] = [
    'BS III', 'BS IV', 'BS VI', 'BS VI Phase 2', 'Other'
  ];

  readonly colorCatalog: string[] = [
    'White with Blue', 'White', 'Blue', 'Red', 'Silver', 'Grey', 'Yellow', 'Green', 'Black', 'Other'
  ];

  readonly busCategoryCatalog: string[] = [
    'Stage Carriage (Contract)', 'Stage Carriage', 'Contract Carriage', 'Tourist', 'Private Service'
  ];

  readonly groupedAmenities: { category: string; items: string[] }[] = [
    { category: 'Climate', items: ['AC'] },
    { category: 'Charging', items: ['USB Charging', 'Charging Point'] },
    { category: 'Passenger Comfort', items: ['Reading Light', 'Recliner Seats', 'Footrest'] },
    { category: 'Luggage', items: ['Luggage Space', 'Overhead Storage'] },
    { category: 'Convenience', items: ['Water Bottle Holder', 'Curtains'] },
    { category: 'Entertainment', items: ['Wi-Fi', 'TV', 'Music System'] },
    { category: 'Safety', items: ['CCTV', 'GPS Tracking', 'Emergency Exit'] }
  ];

  // Templates
  allTemplates: any[] = [];
  filteredTemplates: any[] = [];
  selectedTemplateId = '';
  selectedTemplate: any = null;

  // Route Creation & Editing Wizard State
  showRouteModal = false;
  isEditingRoute = false;
  editingRouteId: number | null = null;
  routeFormCode = '';
  routeModalStep = 1;
  routeModalError: string | null = null;
  isSavingRoute = false;

  routeNameInput = '';
  routeOriginInput = '';
  routeDestinationInput = '';
  routeDescriptionInput = '';

  intermediateStops: { stopName: string; distanceKm: number }[] = [];
  destinationDistanceKm = 280;
  destinationStopName = '';

  routeStopsForm: StopFormItem[] = [];
  activePointsStopIdx = 0;
  reviewSelectedStopIdx = 0;

  // Schedules Dashboard State (Phase 6.2A)
  schedulesSearchTerm: string = '';
  selectedScheduleRouteFilter: string = 'ALL';
  selectedScheduleBusFilter: string = 'ALL';
  selectedScheduleStatusFilter: string = 'ALL';
  selectedScheduleDateFilter: string = '';
  schedulesSortBy: 'DEP_EARLIEST' | 'DEP_LATEST' | 'DATE_NEWEST' | 'FARE_ASC' | 'FARE_DESC' | 'SEATS_DESC' = 'DEP_EARLIEST';
  schedulesPageSize: number = 10;
  schedulesCurrentPage: number = 1;
  selectedScheduleIds: Set<number> = new Set<number>();
  activeScheduleActionMenuId: number | null = null;
  schedulesToastMessage: string | null = null;
  schedulesToastType: 'info' | 'success' | 'danger' = 'info';

  // Schedule Creation Wizard State (Phase 6.2B)
  scheduleViewMode: 'list' | 'create' = 'list';
  scheduleWizardStep: number = 1;
  wizardSelectedRouteId: number | null = null;
  wizardSelectedRoute: Route | null = null;
  wizardRouteSearchQuery: string = '';
  wizardRouteStatusFilter: string = 'ALL';
  expandedRouteId: number | null = null;

  // Schedule Details Modal State
  showScheduleDetailsModal: boolean = false;
  selectedScheduleForDetails: any = null;
  isLoadingScheduleDetails: boolean = false;
  selectedScheduleStops: any[] = [];

  // Cancel Schedule Modal State
  showCancelScheduleModal: boolean = false;
  scheduleToCancel: any = null;
  isCancellingSchedule: boolean = false;
  cancelScheduleError: string | null = null;
  cancelScheduleSuccess: boolean = false;

  // Add Schedule Modal State
  showScheduleModal = false;
  schedRouteId: number | null = null;
  schedBusId: number | null = null;
  selectedBusForSched: any = null;
  schedOrigin = '';
  schedDestination = '';
  schedTravelDate = new Date().toISOString().split('T')[0];
  schedDeparture = '22:30';
  schedArrival = '05:30';
  schedFare = 850;
  schedStopTimings: SchedStopTimingItem[] = [];

  constructor(
    private authService: AuthService,
    private busService: BusService,
    private bookingService: BookingService,
    private router: Router
  ) {}

  ngOnInit() {
    this.currentUser = this.authService.currentUserValue;
    this.loadBuses();
    this.loadRoutes();
    this.loadSchedules();
    this.loadBookings();
    this.loadTemplates();
  }

  getFirstName(): string {
    if (!this.currentUser?.fullName) return 'Operator';
    const parts = this.currentUser.fullName.trim().split(' ');
    return parts[0] || 'Operator';
  }

  getInitials(): string {
    if (!this.currentUser?.fullName) return 'OP';
    const parts = this.currentUser.fullName.trim().split(' ');
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return parts[0].substring(0, 2).toUpperCase();
  }

getActiveRoutesCount(): number {
    return this.myRoutes.filter(r => r.status === 'ACTIVE').length;
  }

  getDraftRoutesCount(): number {
    return this.myRoutes.filter(r => r.status === 'DRAFT').length;
  }

  getInactiveRoutesCount(): number {
    return this.myRoutes.filter(r => r.status === 'INACTIVE').length;
  }

  get distinctOrigins(): string[] {
    const set = new Set<string>();
    this.myRoutes.forEach(r => {
      if (r.origin && r.origin.trim()) set.add(r.origin.trim());
    });
    return Array.from(set).sort();
  }

  get distinctDestinations(): string[] {
    const set = new Set<string>();
    this.myRoutes.forEach(r => {
      if (r.destination && r.destination.trim()) set.add(r.destination.trim());
    });
    return Array.from(set).sort();
  }

  get filteredRoutes(): Route[] {
    const list = this.myRoutes.filter(r => {
      if (this.routeSearchQuery && this.routeSearchQuery.trim()) {
        const q = this.routeSearchQuery.toLowerCase().trim();
        const matchesCode = r.routeCode?.toLowerCase().includes(q);
        const matchesName = r.routeName?.toLowerCase().includes(q);
        const matchesOrigin = r.origin?.toLowerCase().includes(q);
        const matchesDest = r.destination?.toLowerCase().includes(q);
        const matchesStops = r.stops?.some(s => s.stopName?.toLowerCase().includes(q));
        if (!matchesCode && !matchesName && !matchesOrigin && !matchesDest && !matchesStops) {
          return false;
        }
      }
      if (this.routeStatusFilter !== 'ALL' && r.status !== this.routeStatusFilter) {
        return false;
      }
      if (this.routeOriginFilter !== 'ALL' && r.origin?.toLowerCase() !== this.routeOriginFilter.toLowerCase()) {
        return false;
      }
      if (this.routeDestinationFilter !== 'ALL' && r.destination?.toLowerCase() !== this.routeDestinationFilter.toLowerCase()) {
        return false;
      }
      // Advanced Filters
      if (this.routeStopsFilter !== 'ALL') {
        const count = (r.stops && r.stops.length > 0) ? r.stops.length : 0;
        if (this.routeStopsFilter === 'DIRECT' && count !== 2) return false;
        if (this.routeStopsFilter === '1-2' && (count < 3 || count > 4)) return false;
        if (this.routeStopsFilter === '3+' && count < 5) return false;
      }
      if (this.routeDistanceFilter !== 'ALL') {
        const distStr = this.formatRouteDistance(r);
        const km = distStr !== '—' ? parseInt(distStr, 10) : 0;
        if (this.routeDistanceFilter === 'SHORT' && (km <= 0 || km >= 200)) return false;
        if (this.routeDistanceFilter === 'MEDIUM' && (km < 200 || km > 450)) return false;
        if (this.routeDistanceFilter === 'LONG' && km <= 450) return false;
      }
      if (this.routeScheduleActivityFilter !== 'ALL') {
        const schedCount = r.scheduleCount || 0;
        if (this.routeScheduleActivityFilter === 'WITH_SCHEDS' && schedCount <= 0) return false;
        if (this.routeScheduleActivityFilter === 'NO_SCHEDS' && schedCount > 0) return false;
      }
      return true;
    });

    // Sort ordering
    return list.sort((a, b) => {
      if (this.routeSortBy === 'NAME_ASC') {
        return (a.routeName || '').localeCompare(b.routeName || '');
      }
      if (this.routeSortBy === 'DIST_DESC') {
        const distA = this.getRouteDistanceNumber(a);
        const distB = this.getRouteDistanceNumber(b);
        return distB - distA;
      }
      if (this.routeSortBy === 'DIST_ASC') {
        const distA = this.getRouteDistanceNumber(a);
        const distB = this.getRouteDistanceNumber(b);
        return distA - distB;
      }
      if (this.routeSortBy === 'SCHED_DESC') {
        return (b.scheduleCount || 0) - (a.scheduleCount || 0);
      }
      // Default: CODE_ASC
      return (a.routeCode || '').localeCompare(b.routeCode || '');
    });
  }

  getRouteDistanceNumber(r: Route): number {
    if (r.stops && r.stops.length > 0) {
      const destStop = r.stops[r.stops.length - 1];
      if (destStop.distanceKm && destStop.distanceKm > 0) {
        return destStop.distanceKm;
      }
      return Math.max(0, ...r.stops.map(s => s.distanceKm || 0));
    }
    return 0;
  }

  get paginatedRoutes(): Route[] {
    const start = (this.routeCurrentPage - 1) * this.routePageSize;
    return this.filteredRoutes.slice(start, start + this.routePageSize);
  }

  get routeTotalPages(): number {
    return Math.max(1, Math.ceil(this.filteredRoutes.length / this.routePageSize));
  }

  get routePageNumbers(): number[] {
    const total = this.routeTotalPages;
    return Array.from({ length: total }, (_, i) => i + 1);
  }

  getPaginationStart(): number {
    if (this.filteredRoutes.length === 0) return 0;
    return (this.routeCurrentPage - 1) * this.routePageSize + 1;
  }

  getPaginationEnd(): number {
    return Math.min(this.routeCurrentPage * this.routePageSize, this.filteredRoutes.length);
  }

  onRouteFilterChange() {
    this.routeCurrentPage = 1;
  }

  onPageSizeChange() {
    this.routeCurrentPage = 1;
  }

  goToRoutePage(page: number) {
    if (page >= 1 && page <= this.routeTotalPages) {
      this.routeCurrentPage = page;
    }
  }

  isAdvancedFilterActive(): boolean {
    return this.routeStopsFilter !== 'ALL' ||
      this.routeDistanceFilter !== 'ALL' ||
      this.routeScheduleActivityFilter !== 'ALL' ||
      this.routeSortBy !== 'CODE_ASC';
  }

  getActiveAdvancedFilterCount(): number {
    let count = 0;
    if (this.routeStopsFilter !== 'ALL') count++;
    if (this.routeDistanceFilter !== 'ALL') count++;
    if (this.routeScheduleActivityFilter !== 'ALL') count++;
    if (this.routeSortBy !== 'CODE_ASC') count++;
    return count;
  }

  isAnyRouteFilterActive(): boolean {
    return !!(this.routeSearchQuery?.trim() ||
      this.routeStatusFilter !== 'ALL' ||
      this.routeOriginFilter !== 'ALL' ||
      this.routeDestinationFilter !== 'ALL' ||
      this.isAdvancedFilterActive());
  }

  resetRouteFilters() {
    this.routeSearchQuery = '';
    this.routeStatusFilter = 'ALL';
    this.routeOriginFilter = 'ALL';
    this.routeDestinationFilter = 'ALL';
    this.routeStopsFilter = 'ALL';
    this.routeDistanceFilter = 'ALL';
    this.routeScheduleActivityFilter = 'ALL';
    this.routeSortBy = 'CODE_ASC';
    this.routeCurrentPage = 1;
  }

  toggleMoreFilters() {
    this.showMoreFilters = !this.showMoreFilters;
  }

  shouldUseDropup(index: number, total: number): boolean {
    if (total <= 2) return false;
    return index >= total - 2;
  }

  // ==================== CREATE ROUTE WIZARD METHODS (Phase 5.3.1) ====================

  startCreateRouteWizard() {
    this.routeViewMode = 'create';
    this.routeWizardStep = 1;
    this.step1FormSubmitted = false;
    this.wizardRouteName = '';
    this.wizardRouteCode = 'RTE-PENDING';
    this.wizardOrigin = '';
    this.customOrigin = '';
    this.wizardDestination = '';
    this.customDestination = '';
    this.wizardDescription = '';
    this.wizardStatus = 'ACTIVE';
    this.wizardNotes = '';
    this.routeNameError = null;
    this.originError = null;
    this.destinationError = null;
    this.endpointConflictError = null;
    this.descriptionError = null;
    this.wizardIntermediateStops = [];
    this.showAddStopRow = false;
    this.newStopNameInput = '';
    this.newStopError = null;
    this.step2Error = null;
    this.wizardStopPointsMap = {};
    this.selectedWizardStopIndex = 0;
    this.step3ValidationError = null;
    this.expandedStopsMap = {};
    this.isAllStopsExpanded = false;
    this.isSubmittingRoute = false;
    this.createRouteError = null;
  }

  cancelRouteCreation() {
    const hasData = !!(
      this.wizardRouteName?.trim() ||
      this.getEffectiveOrigin() ||
      this.getEffectiveDestination() ||
      this.wizardDescription?.trim() ||
      this.wizardNotes?.trim()
    );

    if (hasData) {
      if (!confirm('You have unsaved route details. Are you sure you want to discard your changes and return to the Routes list?')) {
        return;
      }
    }
    this.routeViewMode = 'list';
    this.routeWizardStep = 1;
    this.step1FormSubmitted = false;
  }

  get availableCities(): string[] {
    const defaultCities = [
      'Anantapur',
      'Bengaluru',
      'Chennai',
      'Chittoor',
      'Guntur',
      'Hyderabad',
      'Kadapa',
      'Kakinada',
      'Kurnool',
      'Nellore',
      'Ongole',
      'Rajahmundry',
      'Tirupati',
      'Vijayawada',
      'Visakhapatnam',
      'Warangal'
    ];
    const set = new Set<string>(defaultCities);
    this.distinctOrigins.forEach(c => { if (c) set.add(c.trim()); });
    this.distinctDestinations.forEach(c => { if (c) set.add(c.trim()); });
    return Array.from(set).sort();
  }

  getEffectiveOrigin(): string {
    if (this.wizardOrigin === '__CUSTOM__') {
      return (this.customOrigin || '').trim();
    }
    return (this.wizardOrigin || '').trim();
  }

  getEffectiveDestination(): string {
    if (this.wizardDestination === '__CUSTOM__') {
      return (this.customDestination || '').trim();
    }
    return (this.wizardDestination || '').trim();
  }

  validateRouteName(): boolean {
    const name = (this.wizardRouteName || '').trim();
    if (!name) {
      this.routeNameError = 'Route Name is required and cannot be blank.';
      return false;
    }
    if (name.length > 150) {
      this.routeNameError = 'Route Name cannot exceed 150 characters.';
      return false;
    }
    this.routeNameError = null;
    return true;
  }

  validateEndpoints(): boolean {
    const origin = this.getEffectiveOrigin();
    const dest = this.getEffectiveDestination();

    if (!origin) {
      this.originError = 'Origin city must be selected.';
    } else {
      this.originError = null;
    }

    if (!dest) {
      this.destinationError = 'Destination city must be selected.';
    } else {
      this.destinationError = null;
    }

    if (origin && dest && origin.toLowerCase() === dest.toLowerCase()) {
      this.endpointConflictError = 'Origin and destination cannot be the same physical location.';
    } else {
      this.endpointConflictError = null;
    }

    return !this.originError && !this.destinationError && !this.endpointConflictError;
  }

  validateDescription(): boolean {
    if ((this.wizardDescription || '').length > 500) {
      this.descriptionError = 'Description cannot exceed 500 characters.';
      return false;
    }
    this.descriptionError = null;
    return true;
  }

  isStep1Valid(): boolean {
    const v1 = this.validateRouteName();
    const v2 = this.validateEndpoints();
    const v3 = this.validateDescription();
    return v1 && v2 && v3;
  }

  proceedToStep2(): void {
    this.step1FormSubmitted = true;
    if (!this.isStep1Valid()) {
      return;
    }
    // If route matches the primary reference mockup (Vijayawada -> Hyderabad) and no stops exist yet,
    // pre-populate standard demo stops (Guntur, Suryapet, Nalgonda) to assist immediate verification
    if (this.wizardIntermediateStops.length === 0 &&
        this.getEffectiveOrigin().toLowerCase() === 'vijayawada' &&
        this.getEffectiveDestination().toLowerCase() === 'hyderabad') {
      this.wizardIntermediateStops = [
        { stopName: 'Guntur', state: 'Andhra Pradesh' },
        { stopName: 'Suryapet', state: 'Telangana' },
        { stopName: 'Nalgonda', state: 'Telangana' }
      ];
    }
    this.routeWizardStep = 2;
  }

  // ==================== STEP 2: ADD STOPS METHODS (Phase 5.3.2) ====================

  toggleAddStopRow() {
    this.showAddStopRow = !this.showAddStopRow;
    this.newStopError = null;
    this.newStopNameInput = '';
  }

  get availableIntermediateCities(): string[] {
    const origin = this.getEffectiveOrigin().toLowerCase();
    const dest = this.getEffectiveDestination().toLowerCase();
    const existingStops = this.wizardIntermediateStops.map(s => s.stopName.toLowerCase());
    return this.availableCities.filter(c => {
      const cl = c.toLowerCase();
      return cl !== origin && cl !== dest && !existingStops.includes(cl);
    });
  }

  getCityState(city: string): string | null {
    if (!city) return null;
    const map: { [key: string]: string } = {
      'vijayawada': 'Andhra Pradesh',
      'guntur': 'Andhra Pradesh',
      'visakhapatnam': 'Andhra Pradesh',
      'vizag': 'Andhra Pradesh',
      'tirupati': 'Andhra Pradesh',
      'nellore': 'Andhra Pradesh',
      'kurnool': 'Andhra Pradesh',
      'kakinada': 'Andhra Pradesh',
      'rajahmundry': 'Andhra Pradesh',
      'kadapa': 'Andhra Pradesh',
      'anantapur': 'Andhra Pradesh',
      'ongole': 'Andhra Pradesh',
      'chittoor': 'Andhra Pradesh',
      'hyderabad': 'Telangana',
      'suryapet': 'Telangana',
      'nalgonda': 'Telangana',
      'warangal': 'Telangana',
      'secunderabad': 'Telangana',
      'bengaluru': 'Karnataka',
      'bangalore': 'Karnataka',
      'chennai': 'Tamil Nadu',
      'mumbai': 'Maharashtra',
      'pune': 'Maharashtra'
    };
    return map[city.trim().toLowerCase()] || null;
  }

  addWizardIntermediateStop() {
    const name = (this.newStopNameInput || '').trim();
    if (!name) {
      this.newStopError = 'Stop location name is required.';
      return;
    }

    const normName = name.toLowerCase();
    const normOrigin = this.getEffectiveOrigin().toLowerCase();
    const normDest = this.getEffectiveDestination().toLowerCase();

    if (normName === normOrigin) {
      this.newStopError = `Cannot add origin ("${this.getEffectiveOrigin()}") as an intermediate stop.`;
      return;
    }

    if (normName === normDest) {
      this.newStopError = `Cannot add destination ("${this.getEffectiveDestination()}") as an intermediate stop.`;
      return;
    }

    if (this.wizardIntermediateStops.some(s => s.stopName.trim().toLowerCase() === normName)) {
      this.newStopError = `"${name}" is already included as an intermediate stop on this route.`;
      return;
    }

    this.wizardIntermediateStops.push({
      stopName: name,
      state: this.getCityState(name)
    });

    this.newStopNameInput = '';
    this.newStopError = null;
    this.showAddStopRow = false;
  }

  removeWizardIntermediateStop(index: number) {
    if (index >= 0 && index < this.wizardIntermediateStops.length) {
      const stop = this.wizardIntermediateStops[index];
      if (confirm(`Remove intermediate stop "${stop.stopName}" from this route?`)) {
        this.wizardIntermediateStops.splice(index, 1);
      }
    }
  }

  moveWizardIntermediateStopUp(index: number) {
    if (index > 0 && index < this.wizardIntermediateStops.length) {
      const temp = this.wizardIntermediateStops[index];
      this.wizardIntermediateStops[index] = this.wizardIntermediateStops[index - 1];
      this.wizardIntermediateStops[index - 1] = temp;
    }
  }

  moveWizardIntermediateStopDown(index: number) {
    if (index >= 0 && index < this.wizardIntermediateStops.length - 1) {
      const temp = this.wizardIntermediateStops[index];
      this.wizardIntermediateStops[index] = this.wizardIntermediateStops[index + 1];
      this.wizardIntermediateStops[index + 1] = temp;
    }
  }

  getAllStopsList(): { order: number; name: string; type: 'ORIGIN' | 'INTERMEDIATE' | 'DESTINATION'; state?: string | null }[] {
    const list: { order: number; name: string; type: 'ORIGIN' | 'INTERMEDIATE' | 'DESTINATION'; state?: string | null }[] = [];
    const origin = this.getEffectiveOrigin();
    const dest = this.getEffectiveDestination();

    // 1. Origin
    list.push({
      order: 1,
      name: origin,
      type: 'ORIGIN',
      state: this.getCityState(origin)
    });

    // 2. Intermediates
    this.wizardIntermediateStops.forEach((s, idx) => {
      list.push({
        order: idx + 2,
        name: s.stopName,
        type: 'INTERMEDIATE',
        state: s.state || this.getCityState(s.stopName)
      });
    });

    // 3. Destination
    list.push({
      order: this.wizardIntermediateStops.length + 2,
      name: dest,
      type: 'DESTINATION',
      state: this.getCityState(dest)
    });

    return list;
  }

  proceedToStep3() {
    const allStops = this.getAllStopsList();
    const seen = new Set<string>();

    for (const s of allStops) {
      const n = s.name.trim().toLowerCase();
      if (seen.has(n)) {
        alert(`Repeated physical location detected: "${s.name}". Each physical location can appear only once in a route.`);
        return;
      }
      seen.add(n);
    }

    this.selectedWizardStopIndex = 0;
    this.step3ValidationError = null;
    this.routeWizardStep = 3;
  }

  // ==================== STEP 3: CONFIGURE BOARDING & DROPPING POINTS METHODS (Phase 5.3.3) ====================

  getWizardStopPoints(stopName: string): { boardingPoints: { pointName: string; address: string; timeSlot: string }[]; droppingPoints: { pointName: string; address: string; timeSlot: string }[] } {
    const key = (stopName || '').trim().toLowerCase();
    if (!this.wizardStopPointsMap[key]) {
      // Seed initial points for common stops matching mockup examples
      if (key === 'vijayawada') {
        this.wizardStopPointsMap[key] = {
          boardingPoints: [
            { pointName: 'Vijayawada Bus Station', address: 'MG Road, Vijayawada', timeSlot: '06:00 AM' },
            { pointName: 'Benz Circle', address: 'Benz Circle, Vijayawada', timeSlot: '06:15 AM' },
            { pointName: 'Autonagar', address: 'Autonagar, Vijayawada', timeSlot: '06:30 AM' }
          ],
          droppingPoints: [
            { pointName: 'Vijayawada Bus Station', address: 'MG Road, Vijayawada', timeSlot: '05:50 AM' },
            { pointName: 'Kanaka Durga Temple', address: 'Ibrahimpatnam Road, Vijayawada', timeSlot: '06:10 AM' }
          ]
        };
      } else if (key === 'hyderabad') {
        this.wizardStopPointsMap[key] = {
          boardingPoints: [
            { pointName: 'MGBS Terminal 1', address: 'Gowliguda, Hyderabad', timeSlot: '08:00 PM' },
            { pointName: 'Ameerpet Metro', address: 'Near Big Bazaar, Hyderabad', timeSlot: '08:45 PM' }
          ],
          droppingPoints: [
            { pointName: 'LB Nagar Ring Road', address: 'Metro Pillar 22, Hyderabad', timeSlot: '11:00 AM' },
            { pointName: 'MGBS Central Bus Station', address: 'Terminal 1, Hyderabad', timeSlot: '11:45 AM' }
          ]
        };
      } else if (key === 'guntur') {
        this.wizardStopPointsMap[key] = {
          boardingPoints: [
            { pointName: 'NTR Bus Station', address: 'Main Road, Guntur', timeSlot: '07:15 AM' }
          ],
          droppingPoints: [
            { pointName: 'NTR Bus Station', address: 'Main Road, Guntur', timeSlot: '07:10 AM' }
          ]
        };
      } else {
        this.wizardStopPointsMap[key] = {
          boardingPoints: [],
          droppingPoints: []
        };
      }
    }
    return this.wizardStopPointsMap[key];
  }

  getCurrentSelectedStop(): { order: number; name: string; type: 'ORIGIN' | 'INTERMEDIATE' | 'DESTINATION'; state?: string | null } {
    const list = this.getAllStopsList();
    if (this.selectedWizardStopIndex >= 0 && this.selectedWizardStopIndex < list.length) {
      return list[this.selectedWizardStopIndex];
    }
    return list[0] || { order: 1, name: 'Origin', type: 'ORIGIN' };
  }

  getCurrentBoardingPoints(): { pointName: string; address: string; timeSlot: string }[] {
    const stop = this.getCurrentSelectedStop();
    return this.getWizardStopPoints(stop.name).boardingPoints;
  }

  getCurrentDroppingPoints(): { pointName: string; address: string; timeSlot: string }[] {
    const stop = this.getCurrentSelectedStop();
    return this.getWizardStopPoints(stop.name).droppingPoints;
  }

  onWizardStopSelectChange() {
    this.pointModalError = null;
    this.step3ValidationError = null;
  }

  openAddPointModal(type: 'BOARDING' | 'DROPPING') {
    this.pointModalMode = 'ADD';
    this.pointModalType = type;
    this.pointModalEditIndex = -1;
    this.pointModalName = '';
    this.pointModalAddress = '';
    this.pointModalTime = type === 'BOARDING' ? '06:00 AM' : '06:00 PM';
    this.pointModalError = null;
    this.showPointModal = true;
  }

  openEditPointModal(type: 'BOARDING' | 'DROPPING', index: number) {
    const points = type === 'BOARDING' ? this.getCurrentBoardingPoints() : this.getCurrentDroppingPoints();
    if (index >= 0 && index < points.length) {
      const pt = points[index];
      this.pointModalMode = 'EDIT';
      this.pointModalType = type;
      this.pointModalEditIndex = index;
      this.pointModalName = pt.pointName;
      this.pointModalAddress = pt.address || '';
      this.pointModalTime = pt.timeSlot;
      this.pointModalError = null;
      this.showPointModal = true;
    }
  }

  closePointModal() {
    this.showPointModal = false;
    this.pointModalError = null;
  }

  savePointModal() {
    this.pointModalError = null;
    const name = (this.pointModalName || '').trim();
    if (!name) {
      this.pointModalError = 'Point name is required.';
      return;
    }

    const time = (this.pointModalTime || '').trim();
    if (!time) {
      this.pointModalError = 'Time slot is required.';
      return;
    }

    const formattedTime = this.formatTimeSlot(time);
    const stop = this.getCurrentSelectedStop();
    const pointsData = this.getWizardStopPoints(stop.name);
    const targetArray = this.pointModalType === 'BOARDING' ? pointsData.boardingPoints : pointsData.droppingPoints;

    const pointObj = {
      pointName: name,
      address: (this.pointModalAddress || '').trim(),
      timeSlot: formattedTime
    };

    if (this.pointModalMode === 'ADD') {
      targetArray.push(pointObj);
    } else if (this.pointModalMode === 'EDIT' && this.pointModalEditIndex >= 0) {
      targetArray[this.pointModalEditIndex] = pointObj;
    }

    this.step3ValidationError = null;
    this.closePointModal();
  }

  deleteWizardPoint(type: 'BOARDING' | 'DROPPING', index: number) {
    const stop = this.getCurrentSelectedStop();
    const pointsData = this.getWizardStopPoints(stop.name);
    const targetArray = type === 'BOARDING' ? pointsData.boardingPoints : pointsData.droppingPoints;
    const label = type === 'BOARDING' ? 'boarding' : 'dropping';

    if (index >= 0 && index < targetArray.length) {
      const ptName = targetArray[index].pointName;
      if (confirm(`Are you sure you want to delete ${label} point "${ptName}"?`)) {
        targetArray.splice(index, 1);
        this.step3ValidationError = null;
      }
    }
  }

  formatTimeSlot(slotStr: string): string {
    if (!slotStr) return '';
    const trimmed = slotStr.trim();
    const match12h = trimmed.match(/^(\d{1,2}):(\d{2})\s*(AM|PM|am|pm)?$/i);
    if (match12h) {
      let hours = parseInt(match12h[1], 10);
      const minutes = match12h[2];
      let meridiem = match12h[3] ? match12h[3].toUpperCase() : '';
      if (!meridiem) {
        meridiem = hours >= 12 ? 'PM' : 'AM';
        hours = hours % 12 || 12;
      }
      const padHours = hours < 10 ? `0${hours}` : `${hours}`;
      return `${padHours}:${minutes} ${meridiem}`;
    }
    return trimmed;
  }

  proceedToStep4() {
    this.step3ValidationError = null;
    const allStops = this.getAllStopsList();
    if (allStops.length < 2) {
      this.step3ValidationError = 'Route must contain at least an Origin and a Destination stop.';
      return;
    }

    const originStop = allStops[0];
    const originPoints = this.getWizardStopPoints(originStop.name);
    const hasOriginBoarding = originPoints.boardingPoints.some(p => p.pointName && p.pointName.trim().length > 0);
    if (!hasOriginBoarding) {
      this.step3ValidationError = `Origin stop "${originStop.name}" must configure at least one valid boarding point.`;
      return;
    }

    const destStop = allStops[allStops.length - 1];
    const destPoints = this.getWizardStopPoints(destStop.name);
    const hasDestDropping = destPoints.droppingPoints.some(p => p.pointName && p.pointName.trim().length > 0);
    if (!hasDestDropping) {
      this.step3ValidationError = `Destination stop "${destStop.name}" must configure at least one valid dropping point.`;
      return;
    }

    // Check all configured points across all stops
    for (const stop of allStops) {
      const pData = this.getWizardStopPoints(stop.name);
      for (const bp of pData.boardingPoints) {
        if (!bp.pointName || !bp.pointName.trim()) {
          this.step3ValidationError = `A boarding point at stop "${stop.name}" is missing a required name.`;
          return;
        }
      }
      for (const dp of pData.droppingPoints) {
        if (!dp.pointName || !dp.pointName.trim()) {
          this.step3ValidationError = `A dropping point at stop "${stop.name}" is missing a required name.`;
          return;
        }
      }
    }

    // Generate Candidate Route ID if pending or not set
    if (!this.wizardRouteCode || this.wizardRouteCode === 'RTE-PENDING') {
      this.wizardRouteCode = this.generateWizardRouteCode();
    }

    this.expandedStopsMap = {};
    this.isAllStopsExpanded = false;
    this.createRouteError = null;
    this.routeWizardStep = 4;
  }

  // ==================== STEP 4: REVIEW & CREATE METHODS (Phase 5.3.4) ====================

  generateWizardRouteCode(): string {
    let maxNum = 0;
    if (this.myRoutes && this.myRoutes.length > 0) {
      for (const r of this.myRoutes) {
        if (r.routeCode) {
          const match = r.routeCode.match(/\d+/);
          if (match) {
            const num = parseInt(match[0], 10);
            if (!isNaN(num) && num > maxNum) {
              maxNum = num;
            }
          }
        }
      }
    }
    const nextNum = maxNum > 0 ? maxNum + 1 : (this.myRoutes ? this.myRoutes.length + 1 : 1);
    return `RTE-${nextNum.toString().padStart(3, '0')}`;
  }

  isStopExpanded(stopName: string): boolean {
    return !!this.expandedStopsMap[stopName];
  }

  toggleStopExpansion(stopName: string): void {
    this.expandedStopsMap[stopName] = !this.expandedStopsMap[stopName];
    const allStops = this.getAllStopsList();
    this.isAllStopsExpanded = allStops.length > 0 && allStops.every(s => !!this.expandedStopsMap[s.name]);
  }

  toggleAllStopsExpansion(): void {
    this.isAllStopsExpanded = !this.isAllStopsExpanded;
    const allStops = this.getAllStopsList();
    allStops.forEach(s => {
      this.expandedStopsMap[s.name] = this.isAllStopsExpanded;
    });
  }

  getIntermediateStopsCount(): number {
    return this.wizardIntermediateStops.length;
  }

  hasValidOriginBoarding(): boolean {
    const allStops = this.getAllStopsList();
    if (allStops.length === 0) return false;
    const origin = allStops[0].name;
    const pts = this.getWizardStopPoints(origin);
    return pts.boardingPoints.some(bp => bp.pointName && bp.pointName.trim().length > 0);
  }

  hasValidDestinationDropping(): boolean {
    const allStops = this.getAllStopsList();
    if (allStops.length < 2) return false;
    const dest = allStops[allStops.length - 1].name;
    const pts = this.getWizardStopPoints(dest);
    return pts.droppingPoints.some(dp => dp.pointName && dp.pointName.trim().length > 0);
  }

  areAllStopPointsValid(): boolean {
    const allStops = this.getAllStopsList();
    if (allStops.length < 2) return false;
    for (const s of allStops) {
      const pts = this.getWizardStopPoints(s.name);
      for (const bp of pts.boardingPoints) {
        if (!bp.pointName || !bp.pointName.trim()) return false;
      }
      for (const dp of pts.droppingPoints) {
        if (!dp.pointName || !dp.pointName.trim()) return false;
      }
    }
    return true;
  }

  isStep4Valid(): boolean {
    return (
      !!this.wizardRouteName?.trim() &&
      this.getAllStopsList().length >= 2 &&
      this.hasValidOriginBoarding() &&
      this.hasValidDestinationDropping() &&
      this.areAllStopPointsValid()
    );
  }

  submitWizardCreateRoute(): void {
    if (!this.isStep4Valid() || this.isSubmittingRoute) {
      return;
    }

    this.isSubmittingRoute = true;
    this.createRouteError = null;

    const allStops = this.getAllStopsList();
    const lastIndex = allStops.length - 1;

    // Build stops payload with 0-based stopOrder matching backend contract
    const stopsPayload = allStops.map((s, idx) => {
      const points = this.getWizardStopPoints(s.name);
      const stopType: 'ORIGIN' | 'INTERMEDIATE' | 'DESTINATION' =
        idx === 0 ? 'ORIGIN' : (idx === lastIndex ? 'DESTINATION' : 'INTERMEDIATE');

      return {
        stopOrder: idx,
        stopName: s.name.trim(),
        stopType: stopType,
        distanceKm: undefined,
        boardingPoints: points.boardingPoints
          .filter(bp => bp.pointName && bp.pointName.trim())
          .map(bp => ({
            pointName: bp.pointName.trim(),
            address: bp.address ? bp.address.trim() : undefined,
            timeSlot: bp.timeSlot ? bp.timeSlot.trim() : undefined
          })),
        droppingPoints: points.droppingPoints
          .filter(dp => dp.pointName && dp.pointName.trim())
          .map(dp => ({
            pointName: dp.pointName.trim(),
            address: dp.address ? dp.address.trim() : undefined,
            timeSlot: dp.timeSlot ? dp.timeSlot.trim() : undefined
          }))
      };
    });

    const payload: CreateRouteRequest = {
      routeCode: this.wizardRouteCode,
      routeName: this.wizardRouteName.trim(),
      origin: this.getEffectiveOrigin().trim(),
      destination: this.getEffectiveDestination().trim(),
      description: this.wizardDescription ? this.wizardDescription.trim() : undefined,
      status: this.wizardStatus || 'ACTIVE',
      stops: stopsPayload
    };

    this.busService.createRoute(payload).subscribe({
      next: (created: any) => {
        this.isSubmittingRoute = false;
        alert(`Route "${created?.routeName || this.wizardRouteName}" (${created?.routeCode || this.wizardRouteCode}) created successfully!`);
        this.startCreateRouteWizard();
        this.routeViewMode = 'list';
        this.loadRoutes();
      },
      error: (err: any) => {
        this.isSubmittingRoute = false;
        console.error('Failed to create route', err);
        this.createRouteError = err.error?.message || err.message || 'Failed to create route. Please check your inputs and try again.';
      }
    });
  }

  formatRouteStopsCount(r: Route): string | number {
    if (r.stops && r.stops.length > 0) {
      return r.stops.length;
    }
    return '—';
  }

  formatRouteDistance(r: Route): string {
    if (r.stops && r.stops.length > 0) {
      const destStop = r.stops[r.stops.length - 1];
      if (destStop.distanceKm && destStop.distanceKm > 0) {
        return `${Math.round(destStop.distanceKm)} km`;
      }
      const maxDist = Math.max(...r.stops.map(s => s.distanceKm || 0));
      if (maxDist > 0) return `${Math.round(maxDist)} km`;
    }
    return '—';
  }

  formatRouteDuration(r: Route): string {
    const sched = this.mySchedules.find(s => s.routeId === r.id && s.departureTime && s.arrivalTime);
    if (sched && sched.departureTime && sched.arrivalTime) {
      const dur = this.calculateTimeDiff(sched.departureTime, sched.arrivalTime);
      if (dur) return dur;
    }
    return '—';
  }

  calculateTimeDiff(dep: string, arr: string): string | null {
    try {
      const [depH, depM] = dep.split(':').map(Number);
      const [arrH, arrM] = arr.split(':').map(Number);
      if (isNaN(depH) || isNaN(arrH)) return null;
      let depTotal = depH * 60 + (depM || 0);
      let arrTotal = arrH * 60 + (arrM || 0);
      if (arrTotal < depTotal) arrTotal += 24 * 60;
      const diff = arrTotal - depTotal;
      const h = Math.floor(diff / 60);
      const m = diff % 60;
      return `${h}h ${m.toString().padStart(2, '0')}m`;
    } catch {
      return null;
    }
  }

  viewRouteDetails(route: Route) {
    this.selectedRouteForDetails = route;
    this.showRouteDetailsModal = true;
    this.activeActionMenuRouteId = null;
  }

  toggleActionMenu(routeId: number, event: MouseEvent) {
    event.stopPropagation();
    this.activeActionMenuRouteId = this.activeActionMenuRouteId === routeId ? null : routeId;
  }

  closeActionMenu() {
    this.activeActionMenuRouteId = null;
  }

  @HostListener('document:click')
  onDocumentClick() {
    this.activeActionMenuRouteId = null;
    this.isStatusDropdownOpen = false;
    this.activeScheduleActionMenuId = null;
  }

  toggleStatusDropdown(event: MouseEvent) {
    event.stopPropagation();
    this.isStatusDropdownOpen = !this.isStatusDropdownOpen;
  }

  selectStatus(status: string, event?: MouseEvent) {
    if (event) event.stopPropagation();
    this.wizardStatus = status;
    this.isStatusDropdownOpen = false;
  }

  get formattedMonthlyRevenue(): string {
    const now = new Date();
    const currentMonth = now.getMonth();
    const currentYear = now.getFullYear();

    const total = this.operatorBookings
      .filter(b => {
        if (b.status !== 'CONFIRMED' && b.status !== 'COMPLETED') return false;
        if (!b.bookingDate) return false;
        const d = new Date(b.bookingDate);
        return d.getMonth() === currentMonth && d.getFullYear() === currentYear;
      })
      .reduce((sum, b) => sum + (b.totalAmount || 0), 0);

    return '₹ ' + (total > 0 ? total.toLocaleString('en-IN') : '0');
  }

  loadBuses() {
    if (!this.currentUser) return;
    this.isLoadingBuses = true;
    this.busLoadError = null;
    this.busService.getBusesByOperator(this.currentUser.id).subscribe({
      next: (res) => {
        this.myBuses = res || [];
        this.isLoadingBuses = false;
      },
      error: (err) => {
        console.error('Failed to load fleet buses', err);
        this.busLoadError = 'Failed to load fleet buses. Please retry.';
        this.isLoadingBuses = false;
      }
    });
  }

  loadRoutes() {
    this.isLoadingRoutes = true;
    this.routeLoadError = null;
    this.busService.getRoutes().subscribe({
      next: (res: any[]) => {
        this.myRoutes = res || [];
        this.isLoadingRoutes = false;
      },
      error: (err) => {
        console.error('Failed to load routes', err);
        this.routeLoadError = 'Failed to load route catalogue. Please retry.';
        this.isLoadingRoutes = false;
      }
    });
  }

  loadSchedules() {
    if (!this.currentUser) return;
    this.busService.getOperatorSchedules(this.currentUser.id).subscribe({
      next: (schedules) => {
        this.mySchedules = schedules || [];
      },
      error: (err) => console.error('Failed to load operator schedules', err)
    });
  }

  loadBookings() {
    if (!this.currentUser) return;
    this.bookingService.getProviderBookings(this.currentUser.id).subscribe({
      next: (res) => {
        this.operatorBookings = res || [];
        this.computeBookingTrends();
      },
      error: (err) => console.error('Failed to load provider bookings', err)
    });
  }

  computeBookingTrends() {
    const daysCount = parseInt(this.selectedTrendRange, 10) || 7;
    const days: TrendDay[] = [];
    const today = new Date();

    for (let i = daysCount - 1; i >= 0; i--) {
      const d = new Date();
      d.setDate(today.getDate() - i);
      const year = d.getFullYear();
      const month = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      const dateStr = `${year}-${month}-${day}`;
      const monthName = d.toLocaleDateString('en-US', { month: 'short' });
      const dayNum = d.getDate();

      const count = this.operatorBookings.filter(b => {
        if (!b.bookingDate) return false;
        const bDateStr = typeof b.bookingDate === 'string' ? b.bookingDate.split('T')[0] : '';
        return bDateStr === dateStr && (b.status === 'CONFIRMED' || b.status === 'COMPLETED');
      }).length;

      days.push({
        dateLabel: `${monthName} ${dayNum}`,
        fullDate: dateStr,
        count: count,
        heightPx: 0
      });
    }

    const maxCount = Math.max(...days.map(d => d.count), 1);
    const maxHeightPx = 150;

    days.forEach(d => {
      // Minimum bar height of 10px if count is 0 for visual baseline
      d.heightPx = d.count > 0 ? Math.max(16, Math.round((d.count / maxCount) * maxHeightPx)) : 10;
    });

    this.bookingTrends = days;
  }

  getRecentBookings(): any[] {
    return this.operatorBookings.slice(0, 5);
  }

  formatBookingRoute(b: any): string {
    if (!b.serviceTitle) return 'Inter-City Service';
    let title = b.serviceTitle.replace(' - Bus', '').replace(' Bus Service', '');
    return title.replace(' to ', ' → ');
  }

  formatBookingDate(b: any): string {
    if (!b.bookingDate) return 'Upcoming';
    try {
      const d = new Date(b.bookingDate);
      return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    } catch (e) {
      return String(b.bookingDate);
    }
  }

  getUpcomingTrips(): any[] {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const today = `${year}-${month}-${day}`;

    const upcoming = this.mySchedules.filter(s => {
      if (!s.travelDate) return true;
      return String(s.travelDate) >= today && s.status !== 'CANCELLED';
    });

    upcoming.sort((a, b) => {
      const dateComp = String(a.travelDate || '').localeCompare(String(b.travelDate || ''));
      if (dateComp !== 0) return dateComp;
      return String(a.departureTime || '').localeCompare(String(b.departureTime || ''));
    });

    return upcoming.slice(0, 5);
  }

  formatOccupancy(s: any): string {
    const bus = this.myBuses?.find(b => b.id === s.busId || b.busNumber === s.busNumber);
    const total = bus?.totalSeats || (s.totalSeats !== undefined ? s.totalSeats : (s.availableSeats !== undefined ? s.availableSeats : 0));
    const avail = s.availableSeats !== undefined ? s.availableSeats : total;
    if (total > 0) {
      const booked = Math.max(0, total - avail);
      return `${booked} / ${total}`;
    }
    return `${s.availableSeats ?? 0} seats left`;
  }

  loadTemplates() {
    this.busService.getTemplates().subscribe({
      next: (templates) => {
        this.allTemplates = templates || [];
        this.onFacilityOrConfigChange();
      },
      error: (err) => console.error('Failed to load bus layout templates', err)
    });
  }

  onFacilityOrConfigChange() {
    this.filteredTemplates = this.allTemplates.filter(t => t.busConfiguration === this.newBusConfiguration);
    if (this.filteredTemplates.length > 0) {
      const match = this.filteredTemplates.find(t => t.id === this.selectedTemplateId) || this.filteredTemplates[0];
      this.selectedTemplateId = match.id;
      this.onTemplateChange();
    } else {
      this.selectedTemplate = null;
      this.selectedTemplateId = '';
    }
  }

  onTemplateChange() {
    this.selectedTemplate = this.allTemplates.find(t => t.id === this.selectedTemplateId) || null;
    if (this.selectedTemplate) {
      this.newTotalSeats = this.selectedTemplate.totalInventory;
      this.newBusType = `${this.newFacilityType === 'AC' ? 'AC' : 'Non-AC'} ${this.selectedTemplate.displayName}`;
    }
  }

  // ==================== ADD BUS WIZARD METHODS (PHASE 1) ====================

  getInitialBusForm(): BusBasicDetailsForm {
    return {
      busNumber: 'KA-01-AB-1234',
      busName: 'TravelEasy Express',
      manufacturer: 'Volvo',
      model: 'Volvo 9600',
      busType: 'Seater',
      fuelType: 'Diesel',
      chassisNumber: 'YV3T2EU527N123456',
      yearOfManufacturing: 2022,
      engineNumber: 'D13K520123456',
      emissionStandard: 'BS VI',
      color: 'White with Blue',
      busCategory: 'Stage Carriage (Contract)',
      facilityType: 'AC',
      amenities: ['AC', 'USB Charging', 'Reading Light', 'Luggage Space', 'Water Bottle Holder'],
      exteriorImageUrl: '',
      interiorImageUrl: '',
      exteriorImagePreview: 'assets/images/travel-easy-bus.png',
      interiorImagePreview: 'assets/images/view_details_bus_information_page.png'
    };
  }

  startAddBusWizard() {
    this.busViewMode = 'wizard';
    this.addBusWizardStep = 1;
    this.busFormErrors = {};
    this.registrationAvailabilityStatus = 'idle';
    this.registrationAvailabilityMessage = '';
  }

  cancelAddBusWizard() {
    if (this.isBusFormDirty()) {
      this.showCancelAddBusModal = true;
    } else {
      this.resetAddBusWizard();
    }
  }

  confirmCancelAddBusWizard() {
    this.showCancelAddBusModal = false;
    this.resetAddBusWizard();
  }

  resetAddBusWizard() {
    this.busForm = this.getInitialBusForm();
    this.busFormErrors = {};
    this.addBusWizardStep = 1;
    this.busViewMode = 'list';
    this.registrationAvailabilityStatus = 'idle';
    this.registrationAvailabilityMessage = '';
  }

  isBusFormDirty(): boolean {
    return !!(
      this.busForm.busNumber?.trim() ||
      this.busForm.busName?.trim() ||
      this.busForm.model?.trim() ||
      this.busForm.chassisNumber?.trim() ||
      this.busForm.engineNumber?.trim() ||
      this.busForm.exteriorImageUrl ||
      this.busForm.interiorImageUrl ||
      (this.busForm.amenities && this.busForm.amenities.length > 0)
    );
  }

  normalizeRegNumber(val: string): string {
    if (!val) return '';
    return val.replace(/[\s-]+/g, '').toUpperCase().trim();
  }

  isValidRegNumber(val: string): boolean {
    const normalized = this.normalizeRegNumber(val);
    const standardPattern = /^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{1,4}$/;
    const bhPattern = /^[0-9]{2}BH[0-9]{4}[A-Z]{1,2}$/;
    return standardPattern.test(normalized) || bhPattern.test(normalized);
  }

  onRegistrationNumberInput() {
    delete this.busFormErrors['busNumber'];
    this.registrationAvailabilityStatus = 'idle';
    this.registrationAvailabilityMessage = '';
  }

  onRegistrationNumberBlur() {
    const val = this.busForm.busNumber?.trim();
    if (!val) return;
    const normalized = this.normalizeRegNumber(val);
    if (!this.isValidRegNumber(normalized)) {
      this.registrationAvailabilityStatus = 'invalid';
      this.registrationAvailabilityMessage = 'Invalid format. Use KA01AB1234 or 22BH1234AA';
      this.busFormErrors['busNumber'] = this.registrationAvailabilityMessage;
      return;
    }

    this.isCheckingRegistration = true;
    this.registrationAvailabilityStatus = 'checking';
    this.busService.checkBusRegistration(normalized).subscribe({
      next: (res) => {
        this.isCheckingRegistration = false;
        if (res.available) {
          this.registrationAvailabilityStatus = 'available';
          this.registrationAvailabilityMessage = 'Registration number is valid and available.';
          delete this.busFormErrors['busNumber'];
        } else {
          this.registrationAvailabilityStatus = 'taken';
          this.registrationAvailabilityMessage = res.message || 'Registration number is already registered.';
          this.busFormErrors['busNumber'] = this.registrationAvailabilityMessage;
        }
      },
      error: () => {
        this.isCheckingRegistration = false;
        this.registrationAvailabilityStatus = 'idle';
      }
    });
  }

  onChassisInput() {
    if (this.busForm.chassisNumber) {
      this.busForm.chassisNumber = this.busForm.chassisNumber.toUpperCase().replace(/[^A-HJ-NPR-Z0-9]/g, '');
    }
    delete this.busFormErrors['chassisNumber'];
  }

  onChassisBlur() {
    if (!this.busForm.chassisNumber || !this.busForm.chassisNumber.trim()) return;
    if (this.busForm.chassisNumber.length !== 17) {
      this.busFormErrors['chassisNumber'] = 'Chassis Number must be exactly 17 characters.';
    } else {
      delete this.busFormErrors['chassisNumber'];
    }
  }

  onEngineInput() {
    if (this.busForm.engineNumber) {
      this.busForm.engineNumber = this.busForm.engineNumber.toUpperCase().replace(/[^A-Z0-9/-]/g, '');
    }
    delete this.busFormErrors['engineNumber'];
  }

  onEngineBlur() {
    if (!this.busForm.engineNumber || !this.busForm.engineNumber.trim()) return;
    if (this.busForm.engineNumber.length < 5 || this.busForm.engineNumber.length > 25) {
      this.busFormErrors['engineNumber'] = 'Engine Number must be 5 to 25 characters.';
    } else {
      delete this.busFormErrors['engineNumber'];
    }
  }

  onFieldChange(field: string) {
    delete this.busFormErrors[field];
  }

  addAmenity() {
    if (!this.selectedAmenityCandidate) return;
    if (!this.busForm.amenities.includes(this.selectedAmenityCandidate)) {
      this.busForm.amenities.push(this.selectedAmenityCandidate);
    }
    this.selectedAmenityCandidate = '';
  }

  removeAmenity(amenity: string) {
    this.busForm.amenities = this.busForm.amenities.filter(a => a !== amenity);
  }

  triggerFileInput(category: 'exterior' | 'interior') {
    const inputId = category === 'exterior' ? 'exteriorImageInput' : 'interiorImageInput';
    const el = document.getElementById(inputId) as HTMLInputElement;
    if (el) el.click();
  }

  onFileSelected(event: any, category: 'exterior' | 'interior') {
    const file = event.target?.files?.[0];
    if (!file) return;

    this.uploadImageError[category] = null;
    delete this.busFormErrors[category === 'exterior' ? 'exteriorImage' : 'interiorImage'];

    // 1. Strict Size Check: < 1.5 MB (1,572,864 bytes)
    const maxSizeBytes = 1_572_864;
    if (file.size > maxSizeBytes) {
      const sizeMb = (file.size / (1024 * 1024)).toFixed(2);
      this.uploadImageError[category] = `Image size (${sizeMb} MB) exceeds maximum allowed limit of 1.5 MB.`;
      event.target.value = '';
      return;
    }

    // 2. Format Check
    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(file.type.toLowerCase())) {
      this.uploadImageError[category] = 'Unsupported image format. Allowed formats: JPG, JPEG, PNG, WebP.';
      event.target.value = '';
      return;
    }

    // 3. Instant Data URL preview
    const reader = new FileReader();
    reader.onload = () => {
      const previewUrl = reader.result as string;
      if (category === 'exterior') {
        this.busForm.exteriorImagePreview = previewUrl;
      } else {
        this.busForm.interiorImagePreview = previewUrl;
      }
    };
    reader.readAsDataURL(file);

    // 4. Upload to local storage backend
    this.isUploadingImage[category] = true;
    this.busService.uploadBusImage(file, category).subscribe({
      next: (res) => {
        this.isUploadingImage[category] = false;
        if (category === 'exterior') {
          this.busForm.exteriorImageUrl = res.imageUrl;
        } else {
          this.busForm.interiorImageUrl = res.imageUrl;
        }
      },
      error: (err) => {
        this.isUploadingImage[category] = false;
        const msg = err.error?.message || 'Failed to upload image to server.';
        this.uploadImageError[category] = msg;
      }
    });

    event.target.value = '';
  }

  removeBusImage(category: 'exterior' | 'interior') {
    const filename = category === 'exterior' ? this.busForm.exteriorImageUrl : this.busForm.interiorImageUrl;
    if (filename && filename.startsWith('/api/buses/images/')) {
      const cleanName = filename.replace('/api/buses/images/', '');
      this.busService.deleteBusImage(cleanName).subscribe({
        next: () => {},
        error: () => {}
      });
    }

    if (category === 'exterior') {
      this.busForm.exteriorImageUrl = '';
      this.busForm.exteriorImagePreview = '';
      this.busFormErrors['exteriorImage'] = 'Exterior image is required.';
    } else {
      this.busForm.interiorImageUrl = '';
      this.busForm.interiorImagePreview = '';
      this.busFormErrors['interiorImage'] = 'Interior image is required.';
    }
  }

  nextStepToSeatLayout() {
    this.busFormErrors = {};
    const bus = this.busForm;

    // 1. Registration Number
    if (!bus.busNumber || !bus.busNumber.trim()) {
      this.busFormErrors['busNumber'] = 'Registration Number is required.';
    } else {
      const normalized = this.normalizeRegNumber(bus.busNumber);
      if (!this.isValidRegNumber(normalized)) {
        this.busFormErrors['busNumber'] = 'Invalid format. Use KA01AB1234 or 22BH1234AA';
      } else if (this.registrationAvailabilityStatus === 'taken') {
        this.busFormErrors['busNumber'] = 'This registration number is already registered.';
      }
    }

    // 2. Bus Name (max 50)
    if (bus.busName && bus.busName.length > 50) {
      this.busFormErrors['busName'] = 'Bus Name cannot exceed 50 characters.';
    }

    // 3. Manufacturer
    if (!bus.manufacturer) {
      this.busFormErrors['manufacturer'] = 'Manufacturer is required.';
    }

    // 4. Model
    if (!bus.model || bus.model.trim().length < 2) {
      this.busFormErrors['model'] = 'Model name is required (min 2 characters).';
    } else if (bus.model.length > 60) {
      this.busFormErrors['model'] = 'Model name cannot exceed 60 characters.';
    }

    // 5. Bus Type
    if (!bus.busType) {
      this.busFormErrors['busType'] = 'Bus Type is required.';
    }

    // 6. Fuel Type
    if (!bus.fuelType) {
      this.busFormErrors['fuelType'] = 'Fuel Type is required.';
    }

    // 7. Chassis Number
    const chassisPattern = /^[A-HJ-NPR-Z0-9]{17}$/i;
    if (!bus.chassisNumber || !bus.chassisNumber.trim()) {
      this.busFormErrors['chassisNumber'] = 'Chassis Number is required.';
    } else if (!chassisPattern.test(bus.chassisNumber.trim())) {
      this.busFormErrors['chassisNumber'] = 'Chassis Number must be exactly 17 characters (exclude I, O, Q).';
    }

    // 8. Year of Manufacturing
    if (!bus.yearOfManufacturing) {
      this.busFormErrors['yearOfManufacturing'] = 'Year of Manufacturing is required.';
    } else if (bus.yearOfManufacturing < 1980 || bus.yearOfManufacturing > this.currentCalendarYear) {
      this.busFormErrors['yearOfManufacturing'] = `Year must be between 1980 and ${this.currentCalendarYear}.`;
    }

    // 9. Engine Number
    const enginePattern = /^[A-Z0-9/-]{5,25}$/i;
    if (!bus.engineNumber || !bus.engineNumber.trim()) {
      this.busFormErrors['engineNumber'] = 'Engine Number is required.';
    } else if (!enginePattern.test(bus.engineNumber.trim())) {
      this.busFormErrors['engineNumber'] = 'Engine Number must be 5-25 alphanumeric characters (/ and - allowed).';
    }

    // 10. Emission Standard
    if (!bus.emissionStandard) {
      this.busFormErrors['emissionStandard'] = 'Emission Standard is required.';
    }

    // 11. Color
    if (!bus.color) {
      this.busFormErrors['color'] = 'Color is required.';
    }

    // 12. Bus Category
    if (!bus.busCategory) {
      this.busFormErrors['busCategory'] = 'Bus Category is required.';
    }

    // 13. Air Conditioning
    if (!bus.facilityType) {
      this.busFormErrors['facilityType'] = 'Air Conditioning selection is required.';
    }

    // 14. Exterior Image
    if (!bus.exteriorImagePreview && !bus.exteriorImageUrl) {
      this.busFormErrors['exteriorImage'] = 'Exterior image is required.';
    }

    // 15. Interior Image
    if (!bus.interiorImagePreview && !bus.interiorImageUrl) {
      this.busFormErrors['interiorImage'] = 'Interior image is required.';
    }

    if (Object.keys(this.busFormErrors).length > 0) {
      // Scroll to top of wizard to see errors
      window.scrollTo({ top: 150, behavior: 'smooth' });
      return;
    }

    // Validated successfully! Advance to Step 2 (Seat Layout placeholder)
    this.addBusWizardStep = 2;
    window.scrollTo({ top: 150, behavior: 'smooth' });
  }

  submitAddBus() {
    if (!this.currentUser) return;
    const payload = {
      operatorId: this.currentUser.id,
      operatorName: this.currentUser.fullName,
      busNumber: this.newBusNumber,
      busType: this.newBusType,
      facilityType: this.newFacilityType,
      busConfiguration: this.newBusConfiguration,
      templateId: this.selectedTemplateId,
      totalSeats: this.newTotalSeats,
      amenities: this.newAmenities
    };

    this.busService.addBus(payload).subscribe({
      next: () => {
        alert('Bus Registered Successfully with approved layout template!');
        this.showAddBusModal = false;
        this.loadBuses();
      },
      error: (err) => {
        const msg = err.error?.message || 'Failed to register bus';
        alert('Error: ' + msg);
      }
    });
  }

  // ==================== ROUTE WIZARD METHODS ====================

  openCreateRouteModal() {
    this.isEditingRoute = false;
    this.editingRouteId = null;
    this.routeFormCode = 'RT-NEW';
    this.routeModalStep = 1;
    this.routeModalError = null;

    this.routeNameInput = 'Vijayawada-Hyderabad Express';
    this.routeOriginInput = 'Vijayawada';
    this.routeDestinationInput = 'Hyderabad';
    this.routeDescriptionInput = 'Daily highway service via Suryapet';

    this.intermediateStops = [
      { stopName: 'Suryapet', distanceKm: 135 }
    ];
    this.destinationDistanceKm = 275;
    this.destinationStopName = 'Hyderabad';

    this.rebuildRouteStopsForm();
    if (this.routeStopsForm[0]) {
      this.routeStopsForm[0].boardingPoints = [
        { pointName: 'Vijayawada Bus Complex Gate 1', address: 'MG Road', timeSlot: '06:00 AM' },
        { pointName: 'Benz Circle Pick-up', address: 'Near Flyover', timeSlot: '06:30 AM' }
      ];
    }
    const destIdx = this.routeStopsForm.length - 1;
    if (this.routeStopsForm[destIdx]) {
      this.routeStopsForm[destIdx].droppingPoints = [
        { pointName: 'LB Nagar Ring Road', address: 'Metro Pillar 22', timeSlot: '11:00 AM' },
        { pointName: 'MGBS Hyderabad', address: 'Terminal 1', timeSlot: '11:45 AM' }
      ];
    }

    this.activePointsStopIdx = 0;
    this.reviewSelectedStopIdx = 0;
    this.showRouteModal = true;
  }

  openEditRouteModal(route: Route) {
    this.isEditingRoute = true;
    this.editingRouteId = route.id;
    this.routeFormCode = route.routeCode;
    this.routeModalStep = 1;
    this.routeModalError = null;

    this.routeNameInput = route.routeName;
    this.routeOriginInput = route.origin;
    this.routeDestinationInput = route.destination;
    this.routeDescriptionInput = route.description || '';

    const stops = route.stops || [];
    this.intermediateStops = [];
    stops.forEach((s) => {
      if (s.stopType === 'INTERMEDIATE') {
        this.intermediateStops.push({ stopName: s.stopName, distanceKm: s.distanceKm || 0 });
      }
    });

    const dest = stops.find(s => s.stopType === 'DESTINATION');
    this.destinationDistanceKm = dest?.distanceKm || 280;
    this.destinationStopName = route.destination;

    this.rebuildRouteStopsForm();

    stops.forEach((s, idx) => {
      if (this.routeStopsForm[idx]) {
        this.routeStopsForm[idx].boardingPoints = (s.boardingPoints || []).map(p => ({
          pointName: p.pointName,
          address: p.address || '',
          timeSlot: p.timeSlot || ''
        }));
        this.routeStopsForm[idx].droppingPoints = (s.droppingPoints || []).map(p => ({
          pointName: p.pointName,
          address: p.address || '',
          timeSlot: p.timeSlot || ''
        }));
      }
    });

    this.activePointsStopIdx = 0;
    this.reviewSelectedStopIdx = 0;
    this.showRouteModal = true;
  }

  onOriginChange() {
    if (this.routeStopsForm.length > 0) {
      this.routeStopsForm[0].stopName = this.routeOriginInput.trim();
    }
  }

  onDestinationChange() {
    this.destinationStopName = this.routeDestinationInput.trim();
    const destIdx = this.routeStopsForm.length - 1;
    if (destIdx >= 0) {
      this.routeStopsForm[destIdx].stopName = this.routeDestinationInput.trim();
    }
  }

  rebuildRouteStopsForm() {
    const origin = this.routeOriginInput.trim();
    const dest = this.routeDestinationInput.trim();

    const existingPointsMap = new Map<string, { b: any[]; d: any[] }>();
    this.routeStopsForm.forEach(s => {
      existingPointsMap.set(s.stopName.toLowerCase(), { b: s.boardingPoints, d: s.droppingPoints });
    });

    const stops: StopFormItem[] = [];

    const origPoints = existingPointsMap.get(origin.toLowerCase()) || { b: [], d: [] };
    stops.push({
      stopOrder: 0,
      stopName: origin,
      stopType: 'ORIGIN',
      distanceKm: 0,
      boardingPoints: origPoints.b,
      droppingPoints: origPoints.d
    });

    this.intermediateStops.forEach((im, idx) => {
      const imPoints = existingPointsMap.get(im.stopName.toLowerCase()) || { b: [], d: [] };
      stops.push({
        stopOrder: idx + 1,
        stopName: im.stopName.trim(),
        stopType: 'INTERMEDIATE',
        distanceKm: im.distanceKm || 0,
        boardingPoints: imPoints.b,
        droppingPoints: imPoints.d
      });
    });

    const destPoints = existingPointsMap.get(dest.toLowerCase()) || { b: [], d: [] };
    stops.push({
      stopOrder: stops.length,
      stopName: dest,
      stopType: 'DESTINATION',
      distanceKm: this.destinationDistanceKm,
      boardingPoints: destPoints.b,
      droppingPoints: destPoints.d
    });

    this.routeStopsForm = stops;
  }

  addIntermediateStop() {
    this.intermediateStops.push({ stopName: 'New Stop', distanceKm: 100 });
    this.rebuildRouteStopsForm();
  }

  removeIntermediateStop(idx: number) {
    this.intermediateStops.splice(idx, 1);
    this.rebuildRouteStopsForm();
  }

  moveIntermediateUp(idx: number) {
    if (idx <= 0) return;
    const temp = this.intermediateStops[idx];
    this.intermediateStops[idx] = this.intermediateStops[idx - 1];
    this.intermediateStops[idx - 1] = temp;
    this.rebuildRouteStopsForm();
  }

  moveIntermediateDown(idx: number) {
    if (idx >= this.intermediateStops.length - 1) return;
    const temp = this.intermediateStops[idx];
    this.intermediateStops[idx] = this.intermediateStops[idx + 1];
    this.intermediateStops[idx + 1] = temp;
    this.rebuildRouteStopsForm();
  }

  hasDuplicateStopLocation(): boolean {
    const names = this.routeStopsForm.map(s => s.stopName.trim().toLowerCase()).filter(n => n.length > 0);
    const set = new Set(names);
    return set.size !== names.length;
  }

  addBoardingPoint(stopIdx: number) {
    if (this.routeStopsForm[stopIdx]) {
      this.routeStopsForm[stopIdx].boardingPoints.push({
        pointName: '',
        address: '',
        timeSlot: ''
      });
    }
  }

  removeBoardingPoint(stopIdx: number, pointIdx: number) {
    if (this.routeStopsForm[stopIdx]) {
      this.routeStopsForm[stopIdx].boardingPoints.splice(pointIdx, 1);
    }
  }

  addDroppingPoint(stopIdx: number) {
    if (this.routeStopsForm[stopIdx]) {
      this.routeStopsForm[stopIdx].droppingPoints.push({
        pointName: '',
        address: '',
        timeSlot: ''
      });
    }
  }

  removeDroppingPoint(stopIdx: number, pointIdx: number) {
    if (this.routeStopsForm[stopIdx]) {
      this.routeStopsForm[stopIdx].droppingPoints.splice(pointIdx, 1);
    }
  }

  hasOriginBoarding(): boolean {
    if (this.routeStopsForm.length === 0) return false;
    return this.routeStopsForm[0].boardingPoints.some(p => p.pointName && p.pointName.trim().length > 0);
  }

  hasDestinationDropping(): boolean {
    if (this.routeStopsForm.length < 2) return false;
    const dest = this.routeStopsForm[this.routeStopsForm.length - 1];
    return dest.droppingPoints.some(p => p.pointName && p.pointName.trim().length > 0);
  }

  goToStep(step: number) {
    if (step > this.routeModalStep) {
      if (this.routeModalStep === 1 && !this.validateStep1()) return;
      if (this.routeModalStep === 2 && !this.validateStep2()) return;
      if (this.routeModalStep === 3 && !this.validateStep3()) return;
    }
    this.routeModalError = null;
    this.routeModalStep = step;
  }

  nextStep() {
    this.routeModalError = null;
    if (this.routeModalStep === 1) {
      if (!this.validateStep1()) return;
      this.rebuildRouteStopsForm();
      this.routeModalStep = 2;
    } else if (this.routeModalStep === 2) {
      if (!this.validateStep2()) return;
      this.rebuildRouteStopsForm();
      this.activePointsStopIdx = 0;
      this.routeModalStep = 3;
    } else if (this.routeModalStep === 3) {
      if (!this.validateStep3()) return;
      this.reviewSelectedStopIdx = 0;
      this.routeModalStep = 4;
    }
  }

  prevStep() {
    this.routeModalError = null;
    if (this.routeModalStep > 1) {
      this.routeModalStep--;
    }
  }

  validateStep1(): boolean {
    if (!this.routeNameInput.trim()) {
      this.routeModalError = 'Route name is required.';
      return false;
    }
    if (!this.routeOriginInput.trim()) {
      this.routeModalError = 'Origin city is required.';
      return false;
    }
    if (!this.routeDestinationInput.trim()) {
      this.routeModalError = 'Destination city is required.';
      return false;
    }
    if (this.routeOriginInput.trim().toLowerCase() === this.routeDestinationInput.trim().toLowerCase()) {
      this.routeModalError = 'Origin and destination cannot be the same physical location.';
      return false;
    }
    return true;
  }

  validateStep2(): boolean {
    if (this.hasDuplicateStopLocation()) {
      this.routeModalError = 'Repeated physical locations detected! Each stop along the route must be distinct.';
      return false;
    }
    for (const im of this.intermediateStops) {
      if (!im.stopName.trim()) {
        this.routeModalError = 'All intermediate stop names must be filled out.';
        return false;
      }
    }
    return true;
  }

  validateStep3(): boolean {
    if (!this.hasOriginBoarding()) {
      this.routeModalError = `Origin stop (${this.routeOriginInput}) must configure at least one boarding point.`;
      return false;
    }
    if (!this.hasDestinationDropping()) {
      this.routeModalError = `Destination stop (${this.routeDestinationInput}) must configure at least one dropping point.`;
      return false;
    }
    return true;
  }

  submitRouteForm() {
    if (!this.validateStep1() || !this.validateStep2() || !this.validateStep3()) {
      return;
    }

    this.isSavingRoute = true;
    this.routeModalError = null;

    const payload: CreateRouteRequest = {
      routeName: this.routeNameInput.trim(),
      origin: this.routeOriginInput.trim(),
      destination: this.routeDestinationInput.trim(),
      description: this.routeDescriptionInput.trim(),
      stops: this.routeStopsForm.map(s => ({
        stopOrder: s.stopOrder,
        stopName: s.stopName.trim(),
        distanceKm: s.distanceKm,
        boardingPoints: s.boardingPoints.filter(p => p.pointName && p.pointName.trim().length > 0).map(p => ({
          pointName: p.pointName.trim(),
          address: p.address ? p.address.trim() : '',
          timeSlot: p.timeSlot ? p.timeSlot.trim() : ''
        })),
        droppingPoints: s.droppingPoints.filter(p => p.pointName && p.pointName.trim().length > 0).map(p => ({
          pointName: p.pointName.trim(),
          address: p.address ? p.address.trim() : '',
          timeSlot: p.timeSlot ? p.timeSlot.trim() : ''
        }))
      }))
    };

    if (this.isEditingRoute && this.editingRouteId) {
      this.busService.updateRoute(this.editingRouteId, payload).subscribe({
        next: () => {
          alert('Route updated successfully!');
          this.isSavingRoute = false;
          this.showRouteModal = false;
          this.loadRoutes();
        },
        error: (err) => {
          this.isSavingRoute = false;
          this.routeModalError = err.error?.message || 'Failed to update route';
        }
      });
    } else {
      this.busService.createRoute(payload).subscribe({
        next: (created) => {
          alert(`Route created successfully with Code: ${created.routeCode}!`);
          this.isSavingRoute = false;
          this.showRouteModal = false;
          this.loadRoutes();
        },
        error: (err) => {
          this.isSavingRoute = false;
          this.routeModalError = err.error?.message || 'Failed to create route';
        }
      });
    }
  }

  toggleRouteStatus(route: Route) {
    const newStatus = route.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    if (!confirm(`Are you sure you want to ${newStatus === 'ACTIVE' ? 'activate' : 'deactivate'} route "${route.routeName}"?`)) {
      return;
    }

    this.busService.updateRouteStatus(route.id, newStatus).subscribe({
      next: () => {
        alert(`Route status updated to ${newStatus}!`);
        this.loadRoutes();
      },
      error: (err) => {
        const msg = err.error?.message || 'Failed to update route status';
        alert(`Cannot change status: ${msg}`);
      }
    });
  }

  // ==================== SCHEDULE MODAL METHODS ====================

  openAddScheduleModal(bus?: any, route?: Route) {
    if (route && route.status === 'INACTIVE') {
      alert('Cannot schedule a trip for an inactive route. Please activate the route first.');
      return;
    }
    this.selectedBusForSched = bus || null;
    this.schedBusId = bus ? bus.id : (this.myBuses.find(b => b.status === 'ACTIVE')?.id || (this.myBuses.length > 0 ? this.myBuses[0].id : null));
    this.schedRouteId = route ? route.id : (this.myRoutes.find(r => r.status === 'ACTIVE')?.id || null);

    this.onSchedRouteChange();
    this.showScheduleModal = true;
  }

  onSchedRouteChange() {
    const route = this.myRoutes.find(r => r.id === this.schedRouteId);
    if (route) {
      this.schedOrigin = route.origin;
      this.schedDestination = route.destination;

      const stops = route.stops || [];
      this.schedStopTimings = stops.map(s => ({
        stopOrder: s.stopOrder,
        stopName: s.stopName,
        stopType: s.stopType,
        distanceKm: s.distanceKm,
        arrivalTime: s.stopOrder === 0 ? '' : '02:00',
        departureTime: s.stopOrder === stops.length - 1 ? '' : '02:15',
        consecutiveFare: 0
      }));

      if (this.schedStopTimings.length > 0) {
        this.schedStopTimings[0].departureTime = this.schedDeparture;
        this.schedStopTimings[this.schedStopTimings.length - 1].arrivalTime = this.schedArrival;
      }
      this.distributeConsecutiveFares();
    }
  }

  distributeConsecutiveFares() {
    if (this.schedStopTimings.length > 1 && this.schedFare > 0) {
      const segCount = this.schedStopTimings.length - 1;
      const defFare = Math.round((this.schedFare / segCount) * 100) / 100;
      for (let i = 0; i < segCount; i++) {
        this.schedStopTimings[i].consecutiveFare = defFare;
      }
      this.schedStopTimings[segCount].consecutiveFare = 0;
    }
  }

  submitAddSchedule() {
    if (!this.schedBusId) {
      alert('Please select a bus.');
      return;
    }
    if (!this.schedRouteId) {
      alert('Please select a route.');
      return;
    }
    const route = this.myRoutes.find(r => r.id === this.schedRouteId);
    if (route && route.status === 'INACTIVE') {
      alert('Cannot schedule trips on an inactive route. Please activate the route first.');
      return;
    }

    const bus = this.myBuses.find(b => b.id === this.schedBusId);

    const payload = {
      busId: this.schedBusId,
      routeId: this.schedRouteId,
      origin: this.schedOrigin,
      destination: this.schedDestination,
      travelDate: this.schedTravelDate,
      departureTime: this.schedDeparture,
      arrivalTime: this.schedArrival,
      fare: this.schedFare,
      availableSeats: bus ? bus.totalSeats : 36,
      stops: this.schedStopTimings.map(st => ({
        stopOrder: st.stopOrder,
        stopName: st.stopName,
        stopType: st.stopType,
        distanceKm: st.distanceKm,
        arrivalTime: st.arrivalTime || null,
        departureTime: st.departureTime || null,
        consecutiveFare: st.consecutiveFare || 0.0
      }))
    };

    this.busService.createSchedule(payload).subscribe({
      next: () => {
        alert('Trip Schedule Published Successfully from Route!');
        this.showScheduleModal = false;
        this.loadRoutes();
        this.loadSchedules();
      },
      error: (err) => {
        const msg = err.error?.message || 'Failed to publish schedule';
        alert('Error: ' + msg);
      }
    });
  }

  // ==================== SCHEDULES DASHBOARD METHODS (PHASE 6.2A) ====================

  getBusNumber(busId: number): string {
    const bus = this.myBuses.find(b => b.id === busId);
    return bus ? bus.busNumber : `Bus #${busId}`;
  }

  getBusType(busId: number): string {
    const bus = this.myBuses.find(b => b.id === busId);
    return bus ? (bus.busType || 'Standard') : 'Standard';
  }

  getBusCapacity(busId: number): number {
    const bus = this.myBuses.find(b => b.id === busId);
    return bus ? (bus.capacity || 40) : 40;
  }

  formatScheduleDate(dateStr?: string): string {
    if (!dateStr) return '—';
    try {
      const clean = dateStr.includes('T') ? dateStr.split('T')[0] : dateStr;
      const d = new Date(clean + 'T00:00:00');
      if (isNaN(d.getTime())) return dateStr;
      return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    } catch {
      return dateStr;
    }
  }

  formatScheduleTime(timeStr?: string): string {
    if (!timeStr) return '—';
    try {
      const parts = timeStr.split(':');
      if (parts.length >= 2) {
        let hours = parseInt(parts[0], 10);
        const minutes = parts[1].padStart(2, '0');
        const ampm = hours >= 12 ? 'PM' : 'AM';
        hours = hours % 12;
        hours = hours ? hours : 12;
        return `${hours.toString().padStart(2, '0')}:${minutes} ${ampm}`;
      }
      return timeStr;
    } catch {
      return timeStr;
    }
  }

  parseTimeToMinutes(timeStr: string): number {
    if (!timeStr) return 0;
    const parts = timeStr.split(':');
    const h = parseInt(parts[0], 10) || 0;
    const m = parseInt(parts[1], 10) || 0;
    return h * 60 + m;
  }

  getArrivalDate(s: any): string {
    if (!s) return '';
    if (s.arrivalDate) return s.arrivalDate;
    if (s.arrivalDateTime) return s.arrivalDateTime.split('T')[0];
    if (!s.travelDate) return '';
    if (s.departureTime && s.arrivalTime) {
      const depMins = this.parseTimeToMinutes(s.departureTime);
      const arrMins = this.parseTimeToMinutes(s.arrivalTime);
      if (arrMins < depMins) {
        const d = new Date(s.travelDate + 'T00:00:00');
        d.setDate(d.getDate() + 1);
        return d.toISOString().split('T')[0];
      }
    }
    return s.travelDate;
  }

  isOvernightSchedule(s: any): boolean {
    if (!s) return false;
    const arrDate = this.getArrivalDate(s);
    return !!(arrDate && s.travelDate && arrDate !== s.travelDate);
  }

  calculateDuration(s: any): string {
    if (!s) return '—';
    try {
      if (s.departureDateTime && s.arrivalDateTime) {
        const dep = new Date(s.departureDateTime).getTime();
        const arr = new Date(s.arrivalDateTime).getTime();
        if (!isNaN(dep) && !isNaN(arr) && arr >= dep) {
          const diffMs = arr - dep;
          const totalMinutes = Math.floor(diffMs / (1000 * 60));
          const hours = Math.floor(totalMinutes / 60);
          const mins = totalMinutes % 60;
          return `${hours}h ${mins}m`;
        }
      }
      if (s.departureTime && s.arrivalTime) {
        const depMins = this.parseTimeToMinutes(s.departureTime);
        let arrMins = this.parseTimeToMinutes(s.arrivalTime);
        if (this.isOvernightSchedule(s) || arrMins < depMins) {
          arrMins += 24 * 60;
        }
        const totalMinutes = arrMins - depMins;
        if (totalMinutes > 0) {
          const hours = Math.floor(totalMinutes / 60);
          const mins = totalMinutes % 60;
          return `${hours}h ${mins}m`;
        }
      }
    } catch {
      // return fallback
    }
    return '—';
  }

  getScheduleComputedStatus(s: any): 'UPCOMING' | 'ONGOING' | 'COMPLETED' | 'CANCELLED' {
    if (!s) return 'UPCOMING';
    if (s.status === 'CANCELLED') return 'CANCELLED';

    const now = new Date();
    let depDate: Date | null = null;
    if (s.departureDateTime) {
      depDate = new Date(s.departureDateTime);
    } else if (s.travelDate && s.departureTime) {
      const t = s.departureTime.length === 5 ? s.departureTime + ':00' : s.departureTime;
      depDate = new Date(`${s.travelDate}T${t}`);
    }

    let arrDate: Date | null = null;
    if (s.arrivalDateTime) {
      arrDate = new Date(s.arrivalDateTime);
    } else {
      const effectiveArrDate = this.getArrivalDate(s);
      if (effectiveArrDate && s.arrivalTime) {
        const t = s.arrivalTime.length === 5 ? s.arrivalTime + ':00' : s.arrivalTime;
        arrDate = new Date(`${effectiveArrDate}T${t}`);
      }
    }

    if (depDate && !isNaN(depDate.getTime())) {
      if (now < depDate) {
        return 'UPCOMING';
      }
      if (arrDate && !isNaN(arrDate.getTime())) {
        if (now >= depDate && now <= arrDate) {
          return 'ONGOING';
        }
        if (now > arrDate) {
          return 'COMPLETED';
        }
      } else {
        const sixHoursLater = new Date(depDate.getTime() + 6 * 3600 * 1000);
        if (now <= sixHoursLater) return 'ONGOING';
        return 'COMPLETED';
      }
    }

    return 'UPCOMING';
  }

  getScheduleStatusPillClass(s: any): string {
    const st = this.getScheduleComputedStatus(s);
    switch (st) {
      case 'UPCOMING': return 'pill-upcoming';
      case 'ONGOING': return 'pill-ongoing';
      case 'COMPLETED': return 'pill-completed';
      case 'CANCELLED': return 'pill-cancelled';
      default: return 'pill-upcoming';
    }
  }

  getSeatsClass(s: any): string {
    const avail = s?.availableSeats != null ? s.availableSeats : 0;
    if (avail <= 5) return 'text-danger fw-bold';
    if (avail <= 15) return 'text-warning fw-semibold';
    return 'text-success fw-semibold';
  }

  getOccupancyPercent(s: any): number {
    if (!s) return 0;
    const total = s.capacity || this.getBusCapacity(s.busId) || 40;
    const avail = s.availableSeats != null ? s.availableSeats : total;
    const booked = Math.max(0, total - avail);
    return Math.round((booked / total) * 100);
  }

  get scheduleMetrics() {
    const total = this.mySchedules.length;
    let upcoming = 0;
    let ongoing = 0;
    let completed = 0;
    let cancelled = 0;

    for (const s of this.mySchedules) {
      const st = this.getScheduleComputedStatus(s);
      if (st === 'UPCOMING') upcoming++;
      else if (st === 'ONGOING') ongoing++;
      else if (st === 'COMPLETED') completed++;
      else if (st === 'CANCELLED') cancelled++;
    }

    return {
      total,
      upcoming,
      upcomingPct: total > 0 ? Math.round((upcoming / total) * 100) : 0,
      ongoing,
      ongoingPct: total > 0 ? Math.round((ongoing / total) * 100) : 0,
      completed,
      completedPct: total > 0 ? Math.round((completed / total) * 100) : 0,
      cancelled,
      cancelledPct: total > 0 ? Math.round((cancelled / total) * 100) : 0
    };
  }

  setScheduleStatusFilter(status: string) {
    this.selectedScheduleStatusFilter = status;
    this.schedulesCurrentPage = 1;
  }

  get scheduleRouteOptions(): { value: string; label: string }[] {
    const optionsMap = new Map<string, string>();
    for (const r of this.myRoutes) {
      if (r.id) {
        optionsMap.set(String(r.id), `${r.origin} → ${r.destination}`);
      }
    }
    for (const s of this.mySchedules) {
      if (s.routeId && !optionsMap.has(String(s.routeId))) {
        optionsMap.set(String(s.routeId), `${s.origin} → ${s.destination}`);
      }
    }
    return Array.from(optionsMap.entries()).map(([value, label]) => ({ value, label }));
  }

  get scheduleBusOptions(): { value: string; label: string }[] {
    const optionsMap = new Map<string, string>();
    for (const b of this.myBuses) {
      if (b.id) {
        optionsMap.set(String(b.id), `${b.busNumber} (${b.busType || 'Bus'})`);
      }
    }
    for (const s of this.mySchedules) {
      if (s.busId && !optionsMap.has(String(s.busId))) {
        optionsMap.set(String(s.busId), `${s.busNumber || 'Bus #' + s.busId}`);
      }
    }
    return Array.from(optionsMap.entries()).map(([value, label]) => ({ value, label }));
  }

  getSelectedRouteFilterLabel(): string {
    const opt = this.scheduleRouteOptions.find(o => o.value === this.selectedScheduleRouteFilter);
    return opt ? opt.label : this.selectedScheduleRouteFilter;
  }

  getSelectedBusFilterLabel(): string {
    const opt = this.scheduleBusOptions.find(o => o.value === this.selectedScheduleBusFilter);
    return opt ? opt.label : this.selectedScheduleBusFilter;
  }

  onScheduleFilterChange() {
    this.schedulesCurrentPage = 1;
  }

  hasActiveScheduleFilters(): boolean {
    return !!(
      this.schedulesSearchTerm.trim() ||
      this.selectedScheduleRouteFilter !== 'ALL' ||
      this.selectedScheduleBusFilter !== 'ALL' ||
      this.selectedScheduleStatusFilter !== 'ALL' ||
      this.selectedScheduleDateFilter
    );
  }

  clearScheduleFilters() {
    this.schedulesSearchTerm = '';
    this.selectedScheduleRouteFilter = 'ALL';
    this.selectedScheduleBusFilter = 'ALL';
    this.selectedScheduleStatusFilter = 'ALL';
    this.selectedScheduleDateFilter = '';
    this.schedulesCurrentPage = 1;
  }

  getScheduleSortTimestamp(s: any): number {
    if (s.departureDateTime) {
      const t = new Date(s.departureDateTime).getTime();
      if (!isNaN(t)) return t;
    }
    if (s.travelDate && s.departureTime) {
      const timeStr = s.departureTime.length === 5 ? s.departureTime + ':00' : s.departureTime;
      const t = new Date(`${s.travelDate}T${timeStr}`).getTime();
      if (!isNaN(t)) return t;
    }
    return 0;
  }

  get filteredSchedules(): any[] {
    let list = [...this.mySchedules];

    if (this.schedulesSearchTerm.trim()) {
      const q = this.schedulesSearchTerm.trim().toLowerCase();
      list = list.filter(s => {
        const schedIdStr = `sch-${s.id}`.toLowerCase();
        const rawIdStr = `${s.id}`.toLowerCase();
        const origin = (s.origin || '').toLowerCase();
        const destination = (s.destination || '').toLowerCase();
        const busNum = (s.busNumber || this.getBusNumber(s.busId) || '').toLowerCase();
        const busType = (s.busType || this.getBusType(s.busId) || '').toLowerCase();
        const routePair = `${origin} -> ${destination}`.toLowerCase();
        const routePairUni = `${origin} → ${destination}`.toLowerCase();
        return (
          schedIdStr.includes(q) ||
          rawIdStr.includes(q) ||
          origin.includes(q) ||
          destination.includes(q) ||
          busNum.includes(q) ||
          busType.includes(q) ||
          routePair.includes(q) ||
          routePairUni.includes(q)
        );
      });
    }

    if (this.selectedScheduleRouteFilter !== 'ALL') {
      list = list.filter(s => {
        if (s.routeId && String(s.routeId) === this.selectedScheduleRouteFilter) return true;
        const pair = `${s.origin} → ${s.destination}`;
        return pair === this.selectedScheduleRouteFilter;
      });
    }

    if (this.selectedScheduleBusFilter !== 'ALL') {
      list = list.filter(s => {
        return String(s.busId) === this.selectedScheduleBusFilter || s.busNumber === this.selectedScheduleBusFilter;
      });
    }

    if (this.selectedScheduleStatusFilter !== 'ALL') {
      list = list.filter(s => {
        const computed = this.getScheduleComputedStatus(s);
        if (this.selectedScheduleStatusFilter === 'UPCOMING') {
          return computed === 'UPCOMING' || s.status === 'SCHEDULED';
        }
        return computed === this.selectedScheduleStatusFilter;
      });
    }

    if (this.selectedScheduleDateFilter) {
      list = list.filter(s => {
        return s.travelDate === this.selectedScheduleDateFilter;
      });
    }

    list.sort((a, b) => {
      switch (this.schedulesSortBy) {
        case 'DEP_EARLIEST': {
          return this.getScheduleSortTimestamp(a) - this.getScheduleSortTimestamp(b);
        }
        case 'DEP_LATEST': {
          return this.getScheduleSortTimestamp(b) - this.getScheduleSortTimestamp(a);
        }
        case 'DATE_NEWEST': {
          const dateA = a.travelDate || '';
          const dateB = b.travelDate || '';
          return dateB.localeCompare(dateA);
        }
        case 'FARE_ASC': {
          return (a.fare || 0) - (b.fare || 0);
        }
        case 'FARE_DESC': {
          return (b.fare || 0) - (a.fare || 0);
        }
        case 'SEATS_DESC': {
          return (b.availableSeats || 0) - (a.availableSeats || 0);
        }
        default:
          return 0;
      }
    });

    return list;
  }

  get pagedSchedules(): any[] {
    const filtered = this.filteredSchedules;
    const start = (this.schedulesCurrentPage - 1) * this.schedulesPageSize;
    return filtered.slice(start, start + this.schedulesPageSize);
  }

  get totalSchedulePages(): number {
    return Math.ceil(this.filteredSchedules.length / this.schedulesPageSize) || 1;
  }

  get schedulesPaginationStart(): number {
    if (this.filteredSchedules.length === 0) return 0;
    return (this.schedulesCurrentPage - 1) * this.schedulesPageSize + 1;
  }

  get schedulesPaginationEnd(): number {
    return Math.min(this.schedulesCurrentPage * this.schedulesPageSize, this.filteredSchedules.length);
  }

  goToSchedulePage(page: number) {
    if (page >= 1 && page <= this.totalSchedulePages) {
      this.schedulesCurrentPage = page;
    }
  }

  getSchedulePageNumbers(): number[] {
    const total = this.totalSchedulePages;
    const current = this.schedulesCurrentPage;
    if (total <= 5) {
      return Array.from({ length: total }, (_, i) => i + 1);
    }
    let start = Math.max(1, current - 2);
    let end = Math.min(total, start + 4);
    if (end - start < 4) {
      start = Math.max(1, end - 4);
    }
    const pages: number[] = [];
    for (let i = start; i <= end; i++) {
      pages.push(i);
    }
    return pages;
  }

  isScheduleSelected(scheduleId: number): boolean {
    return this.selectedScheduleIds.has(scheduleId);
  }

  toggleScheduleSelection(scheduleId: number) {
    if (this.selectedScheduleIds.has(scheduleId)) {
      this.selectedScheduleIds.delete(scheduleId);
    } else {
      this.selectedScheduleIds.add(scheduleId);
    }
  }

  isAllVisibleSchedulesSelected(): boolean {
    const paged = this.pagedSchedules;
    if (paged.length === 0) return false;
    return paged.every(s => this.selectedScheduleIds.has(s.id));
  }

  toggleSelectAllSchedules() {
    const paged = this.pagedSchedules;
    if (this.isAllVisibleSchedulesSelected()) {
      paged.forEach(s => this.selectedScheduleIds.delete(s.id));
    } else {
      paged.forEach(s => this.selectedScheduleIds.add(s.id));
    }
  }

  toggleScheduleActionMenu(scheduleId: number, event: Event) {
    event.stopPropagation();
    if (this.activeScheduleActionMenuId === scheduleId) {
      this.activeScheduleActionMenuId = null;
    } else {
      this.activeScheduleActionMenuId = scheduleId;
    }
  }

  closeScheduleActionMenu() {
    this.activeScheduleActionMenuId = null;
  }

  // ==================== SCHEDULE CREATION WIZARD METHODS (PHASE 6.2B) ====================

  handleCreateScheduleClick() {
    this.startScheduleCreationWizard();
  }

  startScheduleCreationWizard(preselectedRoute?: Route) {
    this.scheduleViewMode = 'create';
    this.scheduleWizardStep = 1;
    this.wizardRouteSearchQuery = '';
    this.wizardRouteStatusFilter = 'ALL';
    this.expandedRouteId = null; // Do NOT auto-expand any route
    this.wizardBusSearchQuery = '';
    this.wizardBusTypeFilter = 'ALL';
    this.expandedBusId = null;

    if (!this.myRoutes || this.myRoutes.length === 0) {
      this.loadRoutes();
    }
    if (!this.myBuses || this.myBuses.length === 0) {
      this.loadBuses();
    }

    if (preselectedRoute && this.isRouteEligibleForSchedule(preselectedRoute)) {
      this.wizardSelectedRouteId = preselectedRoute.id;
      this.wizardSelectedRoute = preselectedRoute;
      // Do NOT set expandedRouteId here - selection and expansion must remain decoupled
    } else if (this.wizardSelectedRouteId) {
      const found = (this.myRoutes || []).find(r => r.id === this.wizardSelectedRouteId);
      if (found && this.isRouteEligibleForSchedule(found)) {
        this.wizardSelectedRoute = found;
      } else {
        this.wizardSelectedRouteId = null;
        this.wizardSelectedRoute = null;
      }
    }
  }

  cancelScheduleWizard() {
    this.scheduleViewMode = 'list';
    this.scheduleWizardStep = 1;
    this.wizardSelectedRouteId = null;
    this.wizardSelectedRoute = null;
    this.wizardSelectedBusId = null;
    this.wizardSelectedBus = null;
    this.expandedRouteId = null;
    this.expandedBusId = null;
    this.wizardBusSearchQuery = '';
    this.wizardBusTypeFilter = 'ALL';
    this.wizardSegmentFares = [];
    this.wizardJourneyFares = [];
    this.fareValidationError = null;
    this.wizardFareOverrides = [];
  }

  canContinueToStep2(): boolean {
    return this.wizardSelectedRouteId !== null && this.isRouteEligibleForSchedule(this.wizardSelectedRoute);
  }

  continueToAssignBus() {
    if (!this.canContinueToStep2()) {
      this.showScheduleToast('info', 'Please select an active route to continue.');
      return;
    }
    this.schedRouteId = this.wizardSelectedRouteId;
    this.scheduleWizardStep = 2;
    if (!this.myBuses || this.myBuses.length === 0) {
      this.loadBuses();
    }
  }

  backToSelectRoute() {
    this.scheduleWizardStep = 1;
  }

  backToAssignBus() {
    this.scheduleWizardStep = 2;
  }

  canContinueToStep3(): boolean {
    return this.wizardSelectedBusId !== null && this.isBusEligibleForSchedule(this.wizardSelectedBus);
  }

  continueToConfigureTimings() {
    if (!this.canContinueToStep3()) {
      this.showScheduleToast('info', 'Please select an active bus to continue.');
      return;
    }
    this.schedBusId = this.wizardSelectedBusId;
    this.scheduleWizardStep = 3;
    this.initStep3Timings();
  }

  isBusEligibleForSchedule(bus: Bus | null | undefined): boolean {
    if (!bus) return false;
    return (bus.status || '').trim().toUpperCase() === 'ACTIVE';
  }

  selectWizardBus(bus: Bus, event?: Event) {
    if (event) {
      event.stopPropagation();
    }
    if (!this.isBusEligibleForSchedule(bus)) {
      this.showScheduleToast('info', `Bus "${bus.busNumber}" is inactive. Only active buses can be assigned to a schedule.`);
      return;
    }
    this.wizardSelectedBusId = bus.id;
    this.wizardSelectedBus = bus;
    // Decoupled: selection does NOT automatically expand details
  }

  toggleBusDetails(busId: number, event?: Event) {
    if (event) {
      event.stopPropagation();
    }
    if (this.expandedBusId === busId) {
      this.expandedBusId = null;
    } else {
      this.expandedBusId = busId;
    }
    // Decoupled: expanding details does NOT select the bus
  }

  get filteredWizardBuses(): Bus[] {
    let list = this.myBuses || [];

    if (this.wizardBusTypeFilter && this.wizardBusTypeFilter !== 'ALL') {
      list = list.filter(b => (b.busType || '').toLowerCase().includes(this.wizardBusTypeFilter.toLowerCase()));
    }

    if (this.wizardBusSearchQuery && this.wizardBusSearchQuery.trim()) {
      const q = this.wizardBusSearchQuery.trim().toLowerCase();
      list = list.filter(b =>
        (b.busNumber && b.busNumber.toLowerCase().includes(q)) ||
        (b.busName && b.busName.toLowerCase().includes(q)) ||
        (b.busType && b.busType.toLowerCase().includes(q)) ||
        (b.model && b.model.toLowerCase().includes(q)) ||
        (b.manufacturer && b.manufacturer.toLowerCase().includes(q))
      );
    }

    return list;
  }

  get availableBusTypes(): string[] {
    const set = new Set<string>();
    (this.myBuses || []).forEach(b => {
      if (b.busType && b.busType.trim()) {
        set.add(b.busType.trim());
      }
    });
    return Array.from(set).sort();
  }

  clearWizardBusFilters() {
    this.wizardBusSearchQuery = '';
    this.wizardBusTypeFilter = 'ALL';
  }

  isWizardBusSelectedHidden(): boolean {
    if (!this.wizardSelectedBusId) return false;
    return !this.filteredWizardBuses.some(b => b.id === this.wizardSelectedBusId);
  }

  getBusSeatLayoutSummary(bus: Bus): string {
    return bus.layoutSummary || '--';
  }

  getBusAmenitiesList(bus: Bus): string[] {
    if (Array.isArray(bus.amenities)) {
      return bus.amenities;
    }
    if (typeof bus.amenities === 'string' && bus.amenities.trim().length > 0) {
      return bus.amenities.split(',').map(s => s.trim()).filter(s => s.length > 0);
    }
    return [];
  }

  getBusThumbnail(bus: Bus): string {
    return bus.imageUrl || 'assets/images/travel-easy-bus.png';
  }

  getBusTotalSeatsFormatted(bus: Bus): string {
    const layout = this.getBusSeatLayoutSummary(bus);
    const seats = bus.totalSeats || bus.capacity || 0;
    return layout !== '--' ? `${seats} (${layout})` : `${seats}`;
  }

  getDerivedManufacturer(bus: Bus): string {
    return bus.manufacturer || 'Not available';
  }

  getAmenityIcon(amenity: string): string {
    const a = (amenity || '').toLowerCase();
    if (a.includes('ac') || a.includes('air')) return 'fa-solid fa-snowflake';
    if (a.includes('wifi') || a.includes('wi-fi')) return 'fa-solid fa-wifi';
    if (a.includes('charg') || a.includes('plug') || a.includes('usb')) return 'fa-solid fa-plug';
    if (a.includes('blanket') || a.includes('bed') || a.includes('linen') || a.includes('pillow')) return 'fa-solid fa-bed';
    if (a.includes('water')) return 'fa-solid fa-bottle-water';
    if (a.includes('light')) return 'fa-regular fa-lightbulb';
    return 'fa-solid fa-circle-check';
  }

  handleImageError(event: any) {
    event.target.src = 'assets/images/travel-easy-bus.png';
  }

  isRouteEligibleForSchedule(route: Route | null | undefined): boolean {
    if (!route) return false;
    return (route.status || '').trim().toUpperCase() === 'ACTIVE';
  }

  selectWizardRoute(route: Route, event?: Event) {
    if (event) {
      event.stopPropagation();
    }
    if (!this.isRouteEligibleForSchedule(route)) {
      this.showScheduleToast('info', `Route "${route.routeName || 'Unnamed'}" (${route.routeCode || 'RTE-' + route.id}) is inactive. Only active routes can be scheduled.`);
      return;
    }
    this.wizardSelectedRouteId = route.id;
    this.wizardSelectedRoute = route;
    // Decoupled: selection does NOT automatically expand details
  }

  toggleRouteDetails(routeId: number, event?: Event) {
    if (event) {
      event.stopPropagation();
    }
    if (this.expandedRouteId === routeId) {
      this.expandedRouteId = null;
    } else {
      this.expandedRouteId = routeId;
    }
    // Decoupled: expanding details does NOT select the route
  }

  getRouteStopsCount(r: Route | null | undefined): number {
    if (!r || !r.stops) return 0;
    return r.stops.length;
  }

  get filteredWizardRoutes(): Route[] {
    let list = this.myRoutes || [];

    if (this.wizardRouteStatusFilter && this.wizardRouteStatusFilter !== 'ALL') {
      list = list.filter(r => (r.status || '').toUpperCase() === this.wizardRouteStatusFilter.toUpperCase());
    }

    if (this.wizardRouteSearchQuery && this.wizardRouteSearchQuery.trim()) {
      const q = this.wizardRouteSearchQuery.trim().toLowerCase();
      list = list.filter(r =>
        (r.routeCode && r.routeCode.toLowerCase().includes(q)) ||
        (r.routeName && r.routeName.toLowerCase().includes(q)) ||
        (r.origin && r.origin.toLowerCase().includes(q)) ||
        (r.destination && r.destination.toLowerCase().includes(q))
      );
    }

    return list;
  }

  clearWizardRouteFilters() {
    this.wizardRouteSearchQuery = '';
    this.wizardRouteStatusFilter = 'ALL';
  }

  isWizardRouteSelectedHidden(): boolean {
    if (!this.wizardSelectedRouteId) return false;
    return !this.filteredWizardRoutes.some(r => r.id === this.wizardSelectedRouteId);
  }

  getSelectedRouteName(): string {
    return this.wizardSelectedRoute?.routeName || 'Selected Route';
  }

  getEstimatedRouteDuration(route: Route): string {
    const dist = this.getRouteDistanceNumber(route);
    if (!dist || dist <= 0) return '--';
    const totalMinutes = Math.round((dist / 53) * 60 / 5) * 5;
    const hours = Math.floor(totalMinutes / 60);
    const mins = totalMinutes % 60;
    return `~ ${hours}h ${mins < 10 ? '0' : ''}${mins}m`;
  }

  getStopBoardingDroppingType(stop: RouteStop | null | undefined): { type: string, label: string, cssClass: string } {
    if (!stop) {
      return { type: 'NONE', label: 'No points configured', cssClass: 'stop-badge-none' };
    }
    const hasBoarding = !!(stop.boardingPoints && stop.boardingPoints.length > 0);
    const hasDropping = !!(stop.droppingPoints && stop.droppingPoints.length > 0);

    if (hasBoarding && hasDropping) {
      return { type: 'BOTH', label: 'Boarding & Dropping', cssClass: 'stop-badge-both' };
    }
    if (hasBoarding) {
      return { type: 'BOARDING', label: 'Boarding only', cssClass: 'stop-badge-boarding' };
    }
    if (hasDropping) {
      return { type: 'DROPPING', label: 'Dropping only', cssClass: 'stop-badge-dropping' };
    }
    return { type: 'NONE', label: 'No points configured', cssClass: 'stop-badge-none' };
  }

  getFormattedStopName(stop: RouteStop, index: number, totalStops: number): string {
    if (!stop || !stop.stopName) return '';
    const name = stop.stopName.trim();
    if (totalStops > 1) {
      if ((index === 0 || stop.stopType === 'ORIGIN') && !name.toLowerCase().includes('(start)')) {
        return `${name} (Start)`;
      }
      if ((index === totalStops - 1 || stop.stopType === 'DESTINATION') && !name.toLowerCase().includes('(end)')) {
        return `${name} (End)`;
      }
    }
    return name;
  }

  handleCreateSimilarSchedule(s: any) {
    this.closeScheduleActionMenu();
    this.showScheduleToast('info', 'Create Similar Schedule will be available in Phase 6.2B.');
  }

  viewScheduleBookings(s: any) {
    this.closeScheduleActionMenu();
    this.activeNav = 'bookings';
  }

  showScheduleToast(type: 'info' | 'success' | 'danger', message: string) {
    this.schedulesToastType = type;
    this.schedulesToastMessage = message;
    setTimeout(() => {
      if (this.schedulesToastMessage === message) {
        this.schedulesToastMessage = null;
      }
    }, 4500);
  }

  // ==================== WIZARD STEP 3 TIMINGS METHODS (PHASE 6.3) ====================

  formatDisplayDate(isoDate: string | null | undefined): string {
    if (!isoDate) return '--';
    try {
      const parts = isoDate.split('-');
      if (parts.length === 3) {
        const year = parseInt(parts[0], 10);
        const month = parseInt(parts[1], 10) - 1;
        const day = parseInt(parts[2], 10);
        const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        return `${day < 10 ? '0' : ''}${day} ${months[month]} ${year}`;
      }
      return isoDate;
    } catch {
      return isoDate;
    }
  }

  formatDisplayTime(time24: string | null | undefined): string {
    if (!time24) return '--';
    try {
      const parts = time24.split(':');
      if (parts.length >= 2) {
        let hour = parseInt(parts[0], 10);
        const minute = parts[1].substring(0, 2);
        const ampm = hour >= 12 ? 'PM' : 'AM';
        hour = hour % 12;
        if (hour === 0) hour = 12;
        return `${hour < 10 ? '0' : ''}${hour}:${minute} ${ampm}`;
      }
      return time24;
    } catch {
      return time24;
    }
  }

  initStep3Timings() {
    if (this.wizardSelectedRouteId) {
      this.busService.getRouteDetails(this.wizardSelectedRouteId).subscribe({
        next: (fullRoute) => {
          if (fullRoute && fullRoute.stops && fullRoute.stops.length > 0) {
            this.wizardSelectedRoute = fullRoute;
          }
          this.buildWizardStopTimings();
          this.recalculateJourneyDurationAndValidation();
          this.checkTimingConflict();
        },
        error: () => {
          this.buildWizardStopTimings();
          this.recalculateJourneyDurationAndValidation();
          this.checkTimingConflict();
        }
      });
    } else {
      this.buildWizardStopTimings();
      this.recalculateJourneyDurationAndValidation();
      this.checkTimingConflict();
    }
  }

  buildWizardStopTimings() {
    const route = this.wizardSelectedRoute;
    const dist = (route && (route as any).totalDistanceKm) ? (route as any).totalDistanceKm : (this.wizardSelectedRoute ? this.getRouteDistanceNumber(this.wizardSelectedRoute) : 420);
    this.wizardTotalDistanceKm = dist > 0 ? dist : 420;

    const stops = route?.stops || [];
    this.wizardTotalStopsCount = stops.length > 0 ? stops.length : 4;
    this.wizardIntermediateStopsCount = Math.max(0, this.wizardTotalStopsCount - 2);

    if (this.wizardStopTimings && this.wizardStopTimings.length === this.wizardTotalStopsCount) {
      this.recalculateJourneyDurationAndValidation();
      return;
    }

    if (!this.wizardTripStartDate) {
      this.wizardTripStartDate = this.getOperationalDateKolkata(1);
    }
    if (!this.wizardTripEndDate) {
      this.wizardTripEndDate = this.getOperationalDateKolkata(2);
    }
    const baseDate = this.wizardTripStartDate;
    const nextDate = this.wizardTripEndDate;

    if (!stops || stops.length === 0) {
      this.wizardStopTimings = [
        {
          stopName: 'Bengaluru (Origin)',
          stopType: 'ORIGIN',
          distanceKm: 0,
          isExpanded: true,
          summaryDepartureDate: baseDate,
          summaryDepartureTime: '06:00',
          points: [
            {
              pointName: 'Majestic Bus Stand',
              pointType: 'BOARDING',
              pointBadge: 'Boarding only',
              arrivalDate: null,
              arrivalTime: null,
              departureDate: baseDate,
              departureTime: '06:00',
              haltMinutes: 0,
              haltDurationDisplay: null
            },
            {
              pointName: 'Yeshwanthpur',
              pointType: 'BOARDING',
              pointBadge: 'Boarding only',
              arrivalDate: null,
              arrivalTime: null,
              departureDate: baseDate,
              departureTime: '06:30',
              haltMinutes: 0,
              haltDurationDisplay: null
            }
          ]
        },
        {
          stopName: 'Kurnool',
          stopType: 'INTERMEDIATE',
          distanceKm: 210,
          isExpanded: true,
          summaryArrivalDate: baseDate,
          summaryArrivalTime: '10:00',
          summaryDepartureDate: baseDate,
          summaryDepartureTime: '10:55',
          summaryHaltDisplay: '55 min',
          points: [
            {
              pointName: 'Kurnool Bus Stand',
              pointType: 'BOTH',
              pointBadge: 'Boarding & Dropping',
              arrivalDate: baseDate,
              arrivalTime: '10:00',
              departureDate: baseDate,
              departureTime: '10:20',
              haltMinutes: 20,
              haltDurationDisplay: '20 min'
            },
            {
              pointName: 'Orvakal',
              pointType: 'BOTH',
              pointBadge: 'Boarding & Dropping',
              arrivalDate: baseDate,
              arrivalTime: '10:45',
              departureDate: baseDate,
              departureTime: '10:55',
              haltMinutes: 10,
              haltDurationDisplay: '10 min'
            }
          ]
        },
        {
          stopName: 'Anantapur',
          stopType: 'INTERMEDIATE',
          distanceKm: 320,
          isExpanded: true,
          summaryArrivalDate: baseDate,
          summaryArrivalTime: '14:15',
          summaryDepartureDate: baseDate,
          summaryDepartureTime: '15:10',
          summaryHaltDisplay: '55 min',
          points: [
            {
              pointName: 'Anantapur Bus Stand',
              pointType: 'BOTH',
              pointBadge: 'Boarding & Dropping',
              arrivalDate: baseDate,
              arrivalTime: '14:15',
              departureDate: baseDate,
              departureTime: '14:35',
              haltMinutes: 20,
              haltDurationDisplay: '20 min'
            },
            {
              pointName: 'Gooty',
              pointType: 'BOTH',
              pointBadge: 'Boarding & Dropping',
              arrivalDate: baseDate,
              arrivalTime: '15:00',
              departureDate: baseDate,
              departureTime: '15:10',
              haltMinutes: 10,
              haltDurationDisplay: '10 min'
            }
          ]
        },
        {
          stopName: 'Hyderabad (Destination)',
          stopType: 'DESTINATION',
          distanceKm: 420,
          isExpanded: true,
          summaryArrivalDate: nextDate,
          summaryArrivalTime: this.wizardTripEndTime || '01:30',
          points: [
            {
              pointName: 'MGBS (Jubilee Bus Station)',
              pointType: 'DROPPING',
              pointBadge: 'Dropping only',
              arrivalDate: nextDate,
              arrivalTime: '01:00',
              departureDate: null,
              departureTime: null,
              haltMinutes: 0,
              haltDurationDisplay: null
            },
            {
              pointName: 'Uppal',
              pointType: 'DROPPING',
              pointBadge: 'Dropping only',
              arrivalDate: nextDate,
              arrivalTime: this.wizardTripEndTime || '01:30',
              departureDate: null,
              departureTime: null,
              haltMinutes: 0,
              haltDurationDisplay: null
            }
          ]
        }
      ];
      this.wizardStopTimings.forEach((st, idx) => this.syncStopSummaryTimings(st, idx));
    } else {
      const result: any[] = [];
      const total = stops.length;

      for (let i = 0; i < total; i++) {
        const rs = stops[i];
        const isOrigin = i === 0 || rs.stopType === 'ORIGIN';
        const isDest = i === total - 1 || rs.stopType === 'DESTINATION';
        const stopType = isOrigin ? 'ORIGIN' : (isDest ? 'DESTINATION' : 'INTERMEDIATE');

        const stopNameFormatted = this.getFormattedStopName(rs, i, total);
        const distKm = rs.distanceKm !== undefined && rs.distanceKm !== null ? rs.distanceKm : (i === 0 ? 0 : Math.round((this.wizardTotalDistanceKm / (total - 1)) * i));

        const pointsList: any[] = [];
        const seenNames = new Set<string>();

        const bPoints = rs.boardingPoints || [];
        const dPoints = rs.droppingPoints || [];

        const bNames = new Set(bPoints.map((p: any) => p.pointName?.trim()));
        const dNames = new Set(dPoints.map((p: any) => p.pointName?.trim()));

        bPoints.forEach((p: any) => {
          const name = p.pointName?.trim();
          if (name && !seenNames.has(name)) {
            seenNames.add(name);
            const inBoth = dNames.has(name);
            pointsList.push({
              pointName: name,
              pointType: inBoth ? 'BOTH' : 'BOARDING',
              pointBadge: inBoth ? 'Boarding & Dropping' : 'Boarding only'
            });
          }
        });

        dPoints.forEach((p: any) => {
          const name = p.pointName?.trim();
          if (name && !seenNames.has(name)) {
            seenNames.add(name);
            pointsList.push({
              pointName: name,
              pointType: 'DROPPING',
              pointBadge: 'Dropping only'
            });
          }
        });

        if (pointsList.length === 0) {
          pointsList.push({
            pointName: rs.stopName,
            pointType: isOrigin ? 'BOARDING' : (isDest ? 'DROPPING' : 'BOTH'),
            pointBadge: isOrigin ? 'Boarding only' : (isDest ? 'Dropping only' : 'Boarding & Dropping')
          });
        }

        pointsList.forEach((pt: any, ptIdx: number) => {
          if (isOrigin) {
            pt.arrivalDate = null;
            pt.arrivalTime = null;
            pt.departureDate = baseDate;
            pt.departureTime = ptIdx === 0 ? this.wizardTripStartTime : '06:30';
            pt.haltMinutes = 0;
            pt.haltDurationDisplay = null;
          } else if (isDest) {
            pt.arrivalDate = nextDate;
            pt.arrivalTime = ptIdx === pointsList.length - 1 ? (this.wizardTripEndTime || '01:30') : '01:00';
            pt.departureDate = null;
            pt.departureTime = null;
            pt.haltMinutes = 0;
            pt.haltDurationDisplay = null;
          } else {
            const hoursFromStart = Math.max(1, Math.round((distKm / 55) * 2) / 2);
            const arrHour = 6 + Math.floor(hoursFromStart);
            const arrMin = Math.round((hoursFromStart % 1) * 60) + (ptIdx * 20);
            const normArrHour = (arrHour + Math.floor(arrMin / 60)) % 24;
            const normArrMin = arrMin % 60;
            const depHour = (normArrHour + Math.floor((normArrMin + 20) / 60)) % 24;
            const depMin = (normArrMin + 20) % 60;

            pt.arrivalDate = baseDate;
            pt.arrivalTime = `${normArrHour < 10 ? '0' : ''}${normArrHour}:${normArrMin < 10 ? '0' : ''}${normArrMin}`;
            pt.departureDate = baseDate;
            pt.departureTime = `${depHour < 10 ? '0' : ''}${depHour}:${depMin < 10 ? '0' : ''}${depMin}`;
            pt.haltMinutes = 20;
            pt.haltDurationDisplay = '20 min';
          }
        });

        result.push({
          stopName: stopNameFormatted,
          stopType: stopType,
          distanceKm: distKm,
          isExpanded: true,
          points: pointsList,
          summaryArrivalDate: isOrigin ? null : (isDest ? (this.wizardTripEndDate || nextDate) : pointsList[0]?.arrivalDate),
          summaryArrivalTime: isOrigin ? null : (isDest ? (this.wizardTripEndTime || '01:30') : pointsList[0]?.arrivalTime),
          summaryDepartureDate: isDest ? null : (isOrigin ? (this.wizardTripStartDate || baseDate) : pointsList[pointsList.length - 1]?.departureDate),
          summaryDepartureTime: isDest ? null : (isOrigin ? (this.wizardTripStartTime || '06:00') : pointsList[pointsList.length - 1]?.departureTime),
          summaryHaltDisplay: stopType === 'INTERMEDIATE' ? '55 min' : null
        });
      }
      this.wizardStopTimings = result;
      this.wizardStopTimings.forEach((st, idx) => this.syncStopSummaryTimings(st, idx));
    }

    this.recalculateJourneyDurationAndValidation();
  }

  syncStopSummaryTimings(stop: any, idx?: number) {
    if (!stop) return;
    const total = this.wizardStopTimings?.length || 0;
    const isOrigin = idx === 0 || stop.stopType === 'ORIGIN';
    const isDest = (idx !== undefined && total > 0 && idx === total - 1) || stop.stopType === 'DESTINATION';

    if (isOrigin) {
      stop.stopType = 'ORIGIN';
      stop.summaryArrivalDate = null;
      stop.summaryArrivalTime = null;
      const pts = stop.points || [];
      const firstBoardingPt = pts.find((p: any) => p.departureTime);
      stop.summaryDepartureDate = firstBoardingPt?.departureDate || this.wizardTripStartDate;
      stop.summaryDepartureTime = firstBoardingPt?.departureTime || this.wizardTripStartTime;
      stop.haltMinutes = 0;
      stop.summaryHaltDisplay = null;
    } else if (isDest) {
      stop.stopType = 'DESTINATION';
      stop.summaryDepartureDate = null;
      stop.summaryDepartureTime = null;
      stop.summaryArrivalDate = this.wizardTripEndDate;
      stop.summaryArrivalTime = this.wizardTripEndTime;
      stop.haltMinutes = 0;
      stop.summaryHaltDisplay = null;
    } else {
      stop.stopType = 'INTERMEDIATE';
      const pts = stop.points || [];
      const firstArrPt = pts.find((p: any) => p.arrivalTime);
      const lastDepPt = [...pts].reverse().find((p: any) => p.departureTime);

      stop.summaryArrivalDate = firstArrPt ? firstArrPt.arrivalDate : (pts[0]?.arrivalDate || null);
      stop.summaryArrivalTime = firstArrPt ? firstArrPt.arrivalTime : (pts[0]?.arrivalTime || null);
      stop.summaryDepartureDate = lastDepPt ? lastDepPt.departureDate : (pts[pts.length - 1]?.departureDate || null);
      stop.summaryDepartureTime = lastDepPt ? lastDepPt.departureTime : (pts[pts.length - 1]?.departureTime || null);

      if (stop.summaryArrivalDate && stop.summaryArrivalTime && stop.summaryDepartureDate && stop.summaryDepartureTime) {
        const sArr = new Date(`${stop.summaryArrivalDate}T${stop.summaryArrivalTime}:00`).getTime();
        const sDep = new Date(`${stop.summaryDepartureDate}T${stop.summaryDepartureTime}:00`).getTime();
        if (!isNaN(sArr) && !isNaN(sDep)) {
          const diff = Math.round((sDep - sArr) / 60000);
          stop.haltMinutes = Math.max(0, diff);
          stop.summaryHaltDisplay = `${stop.haltMinutes} min`;
        }
      }
    }
  }

  recalculateJourneyDurationAndValidation() {
    this.timingValidationError = null;

    if (!this.wizardTripStartDate || !this.wizardTripStartTime || !this.wizardTripEndDate || !this.wizardTripEndTime) {
      this.wizardJourneyDurationDisplay = '--';
      this.timingValidationError = 'Trip start and end date and time are required.';
      return;
    }

    try {
      const startDt = new Date(`${this.wizardTripStartDate}T${this.wizardTripStartTime}:00`);
      const endDt = new Date(`${this.wizardTripEndDate}T${this.wizardTripEndTime}:00`);

      if (isNaN(startDt.getTime()) || isNaN(endDt.getTime())) {
        this.wizardJourneyDurationDisplay = '--';
        this.timingValidationError = 'Invalid date or time format.';
        return;
      }

      const diffMs = endDt.getTime() - startDt.getTime();
      if (diffMs <= 0) {
        this.wizardJourneyDurationDisplay = '--';
        this.timingValidationError = 'Trip arrival date-time must be strictly after trip departure date-time.';
        return;
      }

      const totalMins = Math.round(diffMs / 60000);
      const hours = Math.floor(totalMins / 60);
      const mins = totalMins % 60;
      this.wizardJourneyDurationDisplay = `~ ${hours}h ${mins < 10 ? '0' : ''}${mins}m`;

      for (const stop of this.wizardStopTimings) {
        if (stop.stopType === 'INTERMEDIATE') {
          for (const pt of stop.points || []) {
            if (pt.arrivalDate && pt.arrivalTime && pt.departureDate && pt.departureTime) {
              const ptArr = new Date(`${pt.arrivalDate}T${pt.arrivalTime}:00`);
              const ptDep = new Date(`${pt.departureDate}T${pt.departureTime}:00`);
              const haltDiff = Math.round((ptDep.getTime() - ptArr.getTime()) / 60000);
              pt.haltMinutes = haltDiff;
              if (haltDiff < 0) {
                pt.haltDurationDisplay = 'Invalid';
                this.timingValidationError = `Departure cannot be earlier than arrival at "${pt.pointName}".`;
              } else {
                pt.haltDurationDisplay = `${haltDiff} min`;
              }
            }
          }
        }
      }

      if (!this.timingValidationError && !this.isTimingChronological()) {
        this.timingValidationError = this.timingChronologicalError;
      }
    } catch {
      this.wizardJourneyDurationDisplay = '--';
      this.timingValidationError = 'Error calculating journey duration.';
    }
  }

  onTripTimingChange() {
    if (this.wizardStopTimings.length > 0 && this.wizardStopTimings[0].points?.length > 0) {
      this.wizardStopTimings[0].points[0].departureDate = this.wizardTripStartDate;
      this.wizardStopTimings[0].points[0].departureTime = this.wizardTripStartTime;
      this.syncStopSummaryTimings(this.wizardStopTimings[0], 0);
    }

    const lastStop = this.wizardStopTimings[this.wizardStopTimings.length - 1];
    if (lastStop && lastStop.points?.length > 0) {
      const lastPt = lastStop.points[lastStop.points.length - 1];
      lastPt.arrivalDate = this.wizardTripEndDate;
      lastPt.arrivalTime = this.wizardTripEndTime;
      this.syncStopSummaryTimings(lastStop, this.wizardStopTimings.length - 1);
    }

    this.recalculateJourneyDurationAndValidation();

    if (this.conflictCheckTimeout) {
      clearTimeout(this.conflictCheckTimeout);
    }
    this.conflictCheckTimeout = setTimeout(() => {
      this.checkTimingConflict();
    }, 400);
  }

  onPointTimingChange(point: any, stop: any) {
    if (point.arrivalDate && point.arrivalTime && point.departureDate && point.departureTime) {
      const ptArr = new Date(`${point.arrivalDate}T${point.arrivalTime}:00`);
      const ptDep = new Date(`${point.departureDate}T${point.departureTime}:00`);
      const haltDiff = Math.round((ptDep.getTime() - ptArr.getTime()) / 60000);
      point.haltMinutes = haltDiff;
      point.haltDurationDisplay = haltDiff >= 0 ? `${haltDiff} min` : 'Invalid';
    }

    const stopIdx = this.wizardStopTimings.indexOf(stop);
    const total = this.wizardStopTimings.length;
    const isOrigin = stopIdx === 0 || stop?.stopType === 'ORIGIN';
    const isDest = (stopIdx !== -1 && stopIdx === total - 1) || stop?.stopType === 'DESTINATION';

    // If operator modified origin first boarding point, synchronize trip start
    if (isOrigin && stop?.points?.length > 0 && stop.points[0] === point) {
      if (point.departureDate) this.wizardTripStartDate = point.departureDate;
      if (point.departureTime) this.wizardTripStartTime = point.departureTime;
    }

    // If operator modified destination dropping point(s)
    if (isDest && stop?.points?.length > 0) {
      const lastPt = stop.points[stop.points.length - 1];
      if (lastPt === point) {
        if (point.arrivalDate) this.wizardTripEndDate = point.arrivalDate;
        if (point.arrivalTime) this.wizardTripEndTime = point.arrivalTime;
      }
    }

    this.syncStopSummaryTimings(stop, stopIdx >= 0 ? stopIdx : undefined);
    this.recalculateJourneyDurationAndValidation();

    if (this.conflictCheckTimeout) {
      clearTimeout(this.conflictCheckTimeout);
    }
    this.conflictCheckTimeout = setTimeout(() => {
      this.checkTimingConflict();
    }, 400);
  }

  checkTimingConflict() {
    if (!this.wizardSelectedBusId || !this.wizardTripStartDate || !this.wizardTripStartTime) {
      this.timingConflict = null;
      return;
    }
    this.isCheckingTimingConflict = true;
    this.busService.checkScheduleConflict({
      busId: this.wizardSelectedBusId,
      travelDate: this.wizardTripStartDate,
      departureTime: this.wizardTripStartTime,
      arrivalDate: this.wizardTripEndDate,
      arrivalTime: this.wizardTripEndTime
    }).subscribe({
      next: (res: any) => {
        this.isCheckingTimingConflict = false;
        if (res && res.hasConflict) {
          this.timingConflict = res;
        } else {
          this.timingConflict = null;
        }
      },
      error: (err) => {
        this.isCheckingTimingConflict = false;
        console.warn('Conflict check request failed:', err);
      }
    });
  }

  canContinueToStep4(): boolean {
    return !this.timingValidationError && !this.isCheckingTimingConflict;
  }

  continueToConfigureFares() {
    this.recalculateJourneyDurationAndValidation();
    if (this.timingValidationError) {
      this.showScheduleToast('danger', this.timingValidationError);
      return;
    }
    this.initScheduleWizardStep4();
    this.scheduleWizardStep = 4;
  }

  // ==================== WIZARD STEP 4 FARES METHODS (PHASE 6.4) ====================

  initScheduleWizardStep4() {
    if (!this.wizardStopTimings || this.wizardStopTimings.length === 0) {
      this.buildWizardStopTimings();
    }
    const stops = this.wizardStopTimings;
    const n = stops.length;
    if (n < 2) return;

    // Check if wizardSegmentFares is already initialized with matching stops
    const isSegmentsInitialized = this.wizardSegmentFares &&
      this.wizardSegmentFares.length === n - 1 &&
      this.wizardSegmentFares.every((seg, idx) =>
        seg.fromStopOrder === idx &&
        seg.toStopOrder === idx + 1 &&
        seg.fromStopName === stops[idx].stopName &&
        seg.toStopName === stops[idx + 1].stopName
      );

    if (!isSegmentsInitialized) {
      // Build segment fares
      this.wizardSegmentFares = [];
      for (let k = 0; k < n - 1; k++) {
        const fromStop = stops[k];
        const toStop = stops[k + 1];
        const distKm = Math.max(0, (toStop.distanceKm || 0) - (fromStop.distanceKm || 0));

        // Determine default fare
        let fare: number;
        if (fromStop.consecutiveFare && fromStop.consecutiveFare > 0) {
          fare = fromStop.consecutiveFare;
        } else if (n === 4 && k === 0 && fromStop.stopName.includes('Bengaluru') && toStop.stopName.includes('Kurnool')) {
          fare = 450;
        } else if (n === 4 && k === 1 && fromStop.stopName.includes('Kurnool') && toStop.stopName.includes('Anantapur')) {
          fare = 350;
        } else if (n === 4 && k === 2 && fromStop.stopName.includes('Anantapur') && toStop.stopName.includes('Hyderabad')) {
          fare = 400;
        } else {
          // Dynamic sensible default: ~2 INR/km rounded to nearest 10, min 50
          fare = Math.max(50, Math.round((distKm * 2.0) / 10) * 10);
        }

        this.wizardSegmentFares.push({
          segmentIndex: k + 1,
          fromStopOrder: k,
          toStopOrder: k + 1,
          fromStopName: fromStop.stopName,
          toStopName: toStop.stopName,
          distanceKm: distKm,
          fare: fare,
          fareError: null
        });
      }
    }

    // Build or refresh journey fares
    const existingJourneysMap = new Map<string, WizardJourneyFareItem>();
    if (this.wizardJourneyFares && this.wizardJourneyFares.length > 0) {
      for (const j of this.wizardJourneyFares) {
        existingJourneysMap.set(`${j.fromStopOrder}-${j.toStopOrder}`, j);
      }
    }

    const newJourneyFares: WizardJourneyFareItem[] = [];
    let journeyIdx = 1;

    for (let i = 0; i < n - 1; i++) {
      for (let j = i + 1; j < n; j++) {
        const fromStop = stops[i];
        const toStop = stops[j];
        const distKm = Math.max(0, (toStop.distanceKm || 0) - (fromStop.distanceKm || 0));

        // Calculate sum of consecutive segment fares
        let calculatedFare = 0;
        for (let k = i; k < j; k++) {
          const seg = this.wizardSegmentFares[k];
          calculatedFare += (seg && seg.fare && seg.fare > 0) ? Number(seg.fare) : 0;
        }

        const existing = existingJourneysMap.get(`${i}-${j}`);
        if (existing) {
          existing.journeyIndex = journeyIdx++;
          existing.fromStopName = fromStop.stopName;
          existing.toStopName = toStop.stopName;
          existing.distanceKm = distKm;
          existing.calculatedFare = calculatedFare;
          if (!existing.isOverridden) {
            existing.operatorFare = calculatedFare;
            existing.status = 'Auto';
          }
          newJourneyFares.push(existing);
        } else {
          newJourneyFares.push({
            journeyIndex: journeyIdx++,
            fromStopOrder: i,
            toStopOrder: j,
            fromStopName: fromStop.stopName,
            toStopName: toStop.stopName,
            distanceKm: distKm,
            calculatedFare: calculatedFare,
            operatorFare: calculatedFare,
            isOverridden: false,
            status: 'Auto',
            fareError: null
          });
        }
      }
    }

    this.wizardJourneyFares = newJourneyFares;
    this.validateFareConfiguration();
  }

  onSegmentFareChange(segment: WizardSegmentFareItem) {
    if (segment.fare === null || segment.fare === undefined || isNaN(segment.fare) || segment.fare <= 0) {
      segment.fareError = 'Fare must be greater than 0';
    } else {
      segment.fareError = null;
    }
    this.recalculateAllJourneyFares();
    this.validateFareConfiguration();
  }

  recalculateAllJourneyFares() {
    for (const journey of this.wizardJourneyFares) {
      let sum = 0;
      let hasInvalidSegment = false;
      for (let k = journey.fromStopOrder; k < journey.toStopOrder; k++) {
        const seg = this.wizardSegmentFares.find(s => s.fromStopOrder === k && s.toStopOrder === k + 1);
        if (seg && seg.fare && seg.fare > 0) {
          sum += Number(seg.fare);
        } else {
          hasInvalidSegment = true;
        }
      }
      journey.calculatedFare = hasInvalidSegment ? 0 : sum;
      if (!journey.isOverridden) {
        journey.operatorFare = journey.calculatedFare;
        journey.status = 'Auto';
      }
    }
  }

  onJourneyFareChange(journey: WizardJourneyFareItem) {
    journey.isOverridden = true;
    journey.status = 'Overridden';
    if (journey.operatorFare === null || journey.operatorFare === undefined || isNaN(journey.operatorFare) || journey.operatorFare <= 0) {
      journey.fareError = 'Fare must be greater than 0';
    } else {
      journey.fareError = null;
    }
    this.validateFareConfiguration();
  }

  resetJourneyFare(journey: WizardJourneyFareItem) {
    journey.isOverridden = false;
    journey.status = 'Auto';
    journey.operatorFare = journey.calculatedFare;
    journey.fareError = null;
    this.validateFareConfiguration();
  }

  validateFareConfiguration(): boolean {
    this.fareValidationError = null;
    if (!this.wizardSegmentFares || this.wizardSegmentFares.length === 0) {
      this.fareValidationError = 'Segment fares are required.';
      return false;
    }
    for (const seg of this.wizardSegmentFares) {
      if (!seg.fare || seg.fare <= 0 || isNaN(seg.fare)) {
        this.fareValidationError = `Segment fare for ${seg.fromStopName} → ${seg.toStopName} must be greater than 0.`;
        return false;
      }
    }
    if (!this.wizardJourneyFares || this.wizardJourneyFares.length === 0) {
      this.fareValidationError = 'Journey fares are required.';
      return false;
    }
    for (const j of this.wizardJourneyFares) {
      if (!j.operatorFare || j.operatorFare <= 0 || isNaN(j.operatorFare)) {
        this.fareValidationError = `Journey fare for ${j.fromStopName} → ${j.toStopName} must be greater than 0.`;
        return false;
      }
    }
    return true;
  }

  isFareConfigurationValid(): boolean {
    if (!this.wizardSegmentFares || this.wizardSegmentFares.length === 0) return false;
    if (!this.wizardJourneyFares || this.wizardJourneyFares.length === 0) return false;
    for (const seg of this.wizardSegmentFares) {
      if (!seg.fare || seg.fare <= 0 || isNaN(seg.fare) || seg.fareError) return false;
    }
    for (const j of this.wizardJourneyFares) {
      if (!j.operatorFare || j.operatorFare <= 0 || isNaN(j.operatorFare) || j.fareError) return false;
    }
    return true;
  }

  continueToReviewFromFares() {
    if (!this.validateFareConfiguration()) {
      this.showScheduleToast('danger', this.fareValidationError || 'Please fix invalid fares before proceeding.');
      return;
    }

    // 1. Sync segment fares back to wizardStopTimings
    for (const seg of this.wizardSegmentFares) {
      if (this.wizardStopTimings[seg.fromStopOrder]) {
        this.wizardStopTimings[seg.fromStopOrder].consecutiveFare = seg.fare;
      }
    }

    // 2. Derive full trip fare (0 -> n-1)
    const fullTripJourney = this.wizardJourneyFares.find(
      j => j.fromStopOrder === 0 && j.toStopOrder === this.wizardStopTimings.length - 1
    );
    if (fullTripJourney && fullTripJourney.operatorFare) {
      this.wizardTotalFare = fullTripJourney.operatorFare;
    } else {
      this.wizardTotalFare = this.wizardSegmentFares.reduce((acc, s) => acc + (s.fare || 0), 0);
    }

    // 3. Assemble fare overrides list
    this.wizardFareOverrides = this.wizardJourneyFares
      .filter(j => j.isOverridden && j.operatorFare && j.operatorFare > 0)
      .map(j => ({
        fromStopOrder: j.fromStopOrder,
        toStopOrder: j.toStopOrder,
        fromStopName: j.fromStopName,
        toStopName: j.toStopName,
        customFare: j.operatorFare
      }));

    // 4. Move to Step 5
    this.scheduleWizardStep = 5;
  }

  backToConfigureFares() {
    this.scheduleWizardStep = 4;
  }

  // ==================== WIZARD STEP 5 & 6 METHODS (PHASE 6.5) ====================

  toggleReviewSection(sectionNum: number) {
    this.reviewSectionExpanded[sectionNum] = !this.reviewSectionExpanded[sectionNum];
  }

  isReviewSectionOpen(sectionNum: number): boolean {
    return !!this.reviewSectionExpanded[sectionNum];
  }

  getReviewStopsCount(): number {
    return this.wizardStopTimings?.length || this.wizardTotalStopsCount || 4;
  }

  getReviewTotalDistance(): number {
    return this.wizardTotalDistanceKm || 420;
  }

  getReviewRouteSummary(): string {
    const origin = this.wizardSelectedRoute?.origin || (this.wizardStopTimings.length > 0 ? this.cleanStopName(this.wizardStopTimings[0].stopName) : 'Bengaluru');
    const destination = this.wizardSelectedRoute?.destination || (this.wizardStopTimings.length > 0 ? this.cleanStopName(this.wizardStopTimings[this.wizardStopTimings.length - 1].stopName) : 'Hyderabad');
    return `${origin} → ${destination} • ${this.getReviewStopsCount()} stops • ${this.getReviewTotalDistance()} km`;
  }

  getReviewBusSummary(): string {
    if (!this.wizardSelectedBus) return 'No bus assigned';
    const b = this.wizardSelectedBus;
    const regNo = b.busNumber || '--';
    const name = b.busName || (b.operatorName ? `${b.operatorName} ${b.model || b.busType}` : 'Rajesh Travels Volvo 1');
    const mfrModel = `${b.manufacturer || this.getDerivedManufacturer(b)} ${b.model || ''}`.trim();
    const type = b.busType || 'AC Sleeper';
    const seats = `${b.totalSeats || 40} (${(b as any).seatLayout || b.layoutSummary || '2 + 1'}) seats`;
    const fuel = b.fuelType || 'Diesel';
    return `${regNo} • ${name} • ${mfrModel} • ${type} • ${seats} • ${fuel}`;
  }

  getReviewTimingsSummary(): string {
    const startD = this.formatDisplayDate(this.wizardTripStartDate);
    const startT = this.formatDisplayTime(this.wizardTripStartTime);
    const endD = this.formatDisplayDate(this.wizardTripEndDate);
    const endT = this.formatDisplayTime(this.wizardTripEndTime);
    const duration = this.wizardJourneyDurationDisplay || '~ 19h 30m';
    return `${startD}, ${startT} → ${endD}, ${endT} • Total duration: ${duration}`;
  }

  getReviewSegmentsSummary(): string {
    return `${this.wizardSegmentFares.length} segments • Fares configured`;
  }

  getReviewJourneysSummary(): string {
    const overridden = this.getReviewOverriddenCount();
    return `${this.wizardJourneyFares.length} journeys • Custom fares configured (${overridden} overridden)`;
  }

  getReviewOverriddenCount(): number {
    return (this.wizardJourneyFares || []).filter(j => j.isOverridden).length;
  }

  cleanStopName(name: string): string {
    if (!name) return '';
    return name.replace(/\s*\((Origin|Start|Destination|End)\)/gi, '').trim();
  }

  getDisplayStopName(stop: any, idx: number): string {
    if (!stop) return '';
    const raw = stop.stopName || '';
    if (idx === 0) {
      return raw.includes('(Origin)') ? raw : (raw.includes('(Start)') ? raw : `${this.cleanStopName(raw)} (Origin)`);
    }
    if (idx === this.wizardStopTimings.length - 1) {
      return raw.includes('(Destination)') ? raw : `${this.cleanStopName(raw)} (Destination)`;
    }
    return this.cleanStopName(raw);
  }

  getBoardingPointsForStop(stop: any): any[] {
    if (!stop || !stop.points) return [];
    return stop.points.filter((p: any) => (p.pointType === 'BOARDING' || p.pointType === 'BOTH') && p.departureTime);
  }

  getDroppingPointsForStop(stop: any): any[] {
    if (!stop || !stop.points) return [];
    return stop.points.filter((p: any) => (p.pointType === 'DROPPING' || p.pointType === 'BOTH') && p.arrivalTime);
  }

  getHaltDurationForStop(stop: any, idx: number): string {
    if (idx === 0 || idx === this.wizardStopTimings.length - 1) {
      return '—';
    }
    if (stop.haltMinutes && stop.haltMinutes > 0) {
      return `${stop.haltMinutes} min`;
    }
    if (stop.summaryHaltDisplay && stop.summaryHaltDisplay !== '—' && stop.summaryHaltDisplay !== '-') {
      return stop.summaryHaltDisplay;
    }
    return '20 min';
  }

  hasScheduleConflict(): boolean {
    return !!this.timingConflict;
  }

  isTimingChronological(): boolean {
    this.timingChronologicalError = null;
    if (!this.wizardTripStartDate || !this.wizardTripStartTime || !this.wizardTripEndDate || !this.wizardTripEndTime) {
      this.timingChronologicalError = 'Trip start and end date and time are required.';
      return false;
    }
    const startStr = `${this.wizardTripStartDate}T${this.wizardTripStartTime}:00`;
    const endStr = `${this.wizardTripEndDate}T${this.wizardTripEndTime}:00`;
    const tripStartMs = new Date(startStr).getTime();
    const tripEndMs = new Date(endStr).getTime();
    if (isNaN(tripStartMs) || isNaN(tripEndMs)) {
      this.timingChronologicalError = 'Invalid trip date or time format.';
      return false;
    }
    if (tripEndMs <= tripStartMs) {
      this.timingChronologicalError = 'Trip arrival date-time must be strictly after trip departure date-time.';
      return false;
    }

    if (this.wizardStopTimings && this.wizardStopTimings.length > 0) {
      const total = this.wizardStopTimings.length;
      let prevCheckpointMs = tripStartMs;

      for (let i = 0; i < total; i++) {
        const st = this.wizardStopTimings[i];
        const isOrigin = i === 0 || st.stopType === 'ORIGIN';
        const isDest = i === total - 1 || st.stopType === 'DESTINATION';

        // Stop arrival check
        if (!isOrigin) {
          const arrDate = isDest ? this.wizardTripEndDate : (st.summaryArrivalDate || st.points?.[0]?.arrivalDate);
          const arrTime = isDest ? this.wizardTripEndTime : (st.summaryArrivalTime || st.points?.[0]?.arrivalTime);
          if (arrDate && arrTime) {
            const arrMs = new Date(`${arrDate}T${arrTime}:00`).getTime();
            if (isNaN(arrMs)) {
              this.timingChronologicalError = `Invalid arrival date-time at stop "${st.stopName}".`;
              return false;
            }
            if (arrMs < prevCheckpointMs) {
              this.timingChronologicalError = `Arrival at stop "${st.stopName}" cannot precede previous stop departure.`;
              return false;
            }
            if (arrMs > tripEndMs) {
              this.timingChronologicalError = `Arrival at stop "${st.stopName}" cannot exceed trip arrival.`;
              return false;
            }
            prevCheckpointMs = arrMs;
          }
        }

        // Stop departure check
        if (!isDest) {
          const depDate = isOrigin ? this.wizardTripStartDate : (st.summaryDepartureDate || st.points?.[st.points.length - 1]?.departureDate);
          const depTime = isOrigin ? this.wizardTripStartTime : (st.summaryDepartureTime || st.points?.[st.points.length - 1]?.departureTime);
          if (depDate && depTime) {
            const depMs = new Date(`${depDate}T${depTime}:00`).getTime();
            if (isNaN(depMs)) {
              this.timingChronologicalError = `Invalid departure date-time at stop "${st.stopName}".`;
              return false;
            }
            if (depMs < prevCheckpointMs) {
              this.timingChronologicalError = `Departure at stop "${st.stopName}" cannot precede its arrival.`;
              return false;
            }
            if (depMs > tripEndMs) {
              this.timingChronologicalError = `Departure at stop "${st.stopName}" cannot exceed trip arrival.`;
              return false;
            }
            prevCheckpointMs = depMs;
          }
        }

        // Individual points check
        if (st.points && st.points.length > 0) {
          for (const pt of st.points) {
            if (pt.arrivalDate && pt.arrivalTime) {
              const ptArrMs = new Date(`${pt.arrivalDate}T${pt.arrivalTime}:00`).getTime();
              if (ptArrMs < tripStartMs) {
                this.timingChronologicalError = `Point "${pt.pointName}" arrival cannot precede trip departure.`;
                return false;
              }
              if (ptArrMs > tripEndMs) {
                this.timingChronologicalError = `Point "${pt.pointName}" arrival cannot exceed trip arrival.`;
                return false;
              }
            }
            if (pt.departureDate && pt.departureTime) {
              const ptDepMs = new Date(`${pt.departureDate}T${pt.departureTime}:00`).getTime();
              if (ptDepMs < tripStartMs) {
                this.timingChronologicalError = `Point "${pt.pointName}" departure cannot precede trip departure.`;
                return false;
              }
              if (ptDepMs > tripEndMs) {
                this.timingChronologicalError = `Point "${pt.pointName}" departure cannot exceed trip arrival.`;
                return false;
              }
            }
            if (pt.arrivalDate && pt.arrivalTime && pt.departureDate && pt.departureTime) {
              const ptArrMs = new Date(`${pt.arrivalDate}T${pt.arrivalTime}:00`).getTime();
              const ptDepMs = new Date(`${pt.departureDate}T${pt.departureTime}:00`).getTime();
              if (ptDepMs < ptArrMs) {
                this.timingChronologicalError = `Point "${pt.pointName}" departure cannot precede its arrival.`;
                return false;
              }
            }
          }
        }
      }
    }

    return true;
  }

  areRequiredPointsConfigured(): boolean {
    if (!this.wizardStopTimings || this.wizardStopTimings.length < 2) return false;
    const originPoints = this.getBoardingPointsForStop(this.wizardStopTimings[0]);
    if (originPoints.length === 0) return false;
    const destPoints = this.getDroppingPointsForStop(this.wizardStopTimings[this.wizardStopTimings.length - 1]);
    if (destPoints.length === 0) return false;
    return true;
  }

  areFaresValid(): boolean {
    if (!this.wizardSegmentFares || this.wizardSegmentFares.length === 0) return false;
    if (this.wizardSegmentFares.some(s => !s.fare || s.fare <= 0)) return false;
    if (!this.wizardJourneyFares || this.wizardJourneyFares.length === 0) return false;
    if (this.wizardJourneyFares.some(j => (!j.operatorFare || j.operatorFare <= 0) && (!j.calculatedFare || j.calculatedFare <= 0))) return false;
    return true;
  }

  isScheduleReviewValid(): boolean {
    return !this.hasScheduleConflict() &&
           this.isTimingChronological() &&
           this.areRequiredPointsConfigured() &&
           this.areFaresValid() &&
           !!this.wizardSelectedBusId &&
           !!this.wizardSelectedRouteId;
  }

  getValidationErrorMessage(): string {
    if (this.hasScheduleConflict()) {
      return 'Selected bus has a scheduling conflict within the required 60-minute turnaround buffer.';
    }
    if (!this.isTimingChronological()) {
      return this.timingChronologicalError || 'Stop timings are not in chronological order. Departure must precede arrival.';
    }
    if (!this.areRequiredPointsConfigured()) {
      return 'Required boarding or dropping point timings are missing.';
    }
    if (!this.areFaresValid()) {
      return 'Segment fares or journey fares must be positive numbers greater than 0.';
    }
    if (!this.wizardSelectedBusId) {
      return 'No bus assigned for this schedule.';
    }
    if (!this.wizardSelectedRouteId) {
      return 'No route selected for this schedule.';
    }
    return 'Please complete all previous steps before creating the schedule.';
  }

  submitCreateSchedule() {
    if (!this.isScheduleReviewValid()) {
      this.scheduleCreationError = this.getValidationErrorMessage();
      return;
    }
    if (this.isCreatingSchedule) return;

    this.isCreatingSchedule = true;
    this.scheduleCreationError = null;

    // Ensure all stop summary timings are fully synced with current points and trip boundaries
    const totalStops = this.wizardStopTimings.length;
    this.wizardStopTimings.forEach((st, idx) => {
      this.syncStopSummaryTimings(st, idx);
    });

    const stopsPayload = this.wizardStopTimings.map((st, idx) => {
      const isOrigin = idx === 0 || st.stopType === 'ORIGIN';
      const isDest = idx === totalStops - 1 || st.stopType === 'DESTINATION';
      const segFare = this.wizardSegmentFares.find(s => s.fromStopOrder === idx)?.fare || 0;

      // Authoritative arrival and departure:
      // Origin: arrival is null, departure is trip departure
      // Destination: departure is null, arrival is trip arrival
      // Intermediate: arrival is first point arrival, departure is last point departure
      const arrDate = isOrigin ? null : (isDest ? this.wizardTripEndDate : (st.summaryArrivalDate || st.points?.[0]?.arrivalDate || null));
      const arrTime = isOrigin ? null : (isDest ? this.wizardTripEndTime : (st.summaryArrivalTime || st.points?.[0]?.arrivalTime || null));
      const depDate = isDest ? null : (isOrigin ? this.wizardTripStartDate : (st.summaryDepartureDate || st.points?.[st.points.length - 1]?.departureDate || null));
      const depTime = isDest ? null : (isOrigin ? this.wizardTripStartTime : (st.summaryDepartureTime || st.points?.[st.points.length - 1]?.departureTime || null));

      const canonicalStopName = this.wizardSelectedRoute?.stops?.[idx]?.stopName?.trim() || this.cleanStopName(st.stopName) || st.stopName;

      return {
        stopOrder: idx,
        stopName: canonicalStopName,
        stopType: isOrigin ? 'ORIGIN' : (isDest ? 'DESTINATION' : 'INTERMEDIATE'),
        distanceKm: st.distanceKm,
        arrivalDate: arrDate,
        arrivalTime: arrTime,
        departureDate: depDate,
        departureTime: depTime,
        haltMinutes: isOrigin || isDest ? 0 : (st.haltMinutes || 20),
        consecutiveFare: segFare,
        points: (st.points || []).map((pt: any) => ({
          pointName: pt.pointName,
          pointType: pt.pointType,
          pointBadge: pt.pointBadge,
          arrivalDate: isOrigin ? null : (pt.arrivalDate || arrDate || null),
          arrivalTime: isOrigin ? null : (pt.arrivalTime || null),
          departureDate: isDest ? null : (pt.departureDate || depDate || null),
          departureTime: isDest ? null : (pt.departureTime || null),
          haltMinutes: pt.haltMinutes || 0,
          haltDurationDisplay: pt.haltDurationDisplay || null
        }))
      };
    });

    const fareOverridesPayload = (this.wizardFareOverrides || []).map((o: any) => ({
      fromStopOrder: o.fromStopOrder,
      toStopOrder: o.toStopOrder,
      fromStopName: this.cleanStopName(o.fromStopName) || o.fromStopName,
      toStopName: this.cleanStopName(o.toStopName) || o.toStopName,
      customFare: o.customFare
    }));

    const originName = this.wizardSelectedRoute?.origin || this.cleanStopName(this.wizardStopTimings[0]?.stopName) || 'Bengaluru';
    const destName = this.wizardSelectedRoute?.destination || this.cleanStopName(this.wizardStopTimings[this.wizardStopTimings.length - 1]?.stopName) || 'Hyderabad';

    const payload: any = {
      busId: this.wizardSelectedBusId,
      routeId: this.wizardSelectedRouteId,
      origin: originName,
      destination: destName,
      travelDate: this.wizardTripStartDate,
      departureTime: this.wizardTripStartTime,
      arrivalDate: this.wizardTripEndDate,
      arrivalTime: this.wizardTripEndTime,
      fare: this.wizardTotalFare,
      availableSeats: this.wizardSelectedBus?.totalSeats || 40,
      status: 'SCHEDULED',
      stops: stopsPayload,
      fareOverrides: fareOverridesPayload
    };

    this.busService.createSchedule(payload).subscribe({
      next: (res) => {
        this.isCreatingSchedule = false;
        this.createdScheduleResult = res;
        this.scheduleCreationSuccess = true;
        this.scheduleWizardStep = 6;
        this.showScheduleToast('success', 'Schedule created successfully!');
        this.loadSchedules();
      },
      error: (err) => {
        this.isCreatingSchedule = false;
        let msg = 'Failed to create schedule. Please verify details and try again.';
        if (err.status === 409) {
          msg = err.error?.message || 'Schedule conflict: This bus is assigned to another overlapping trip or buffer.';
        } else if (err.status === 400) {
          msg = err.error?.message || 'Invalid schedule configuration. Please check timings and fares.';
        } else if (err.status === 403) {
          msg = err.error?.message || 'Access denied: You do not have permission to schedule this bus.';
        } else if (err.status === 404) {
          msg = err.error?.message || 'Selected bus or route was not found.';
        } else if (err.error?.message) {
          msg = err.error.message;
        }
        this.scheduleCreationError = msg;
        this.showScheduleToast('danger', msg);
      }
    });
  }

  getCreatedScheduleRouteText(): string {
    const origin = this.createdScheduleResult?.origin || this.wizardSelectedRoute?.origin || 'Origin';
    const destination = this.createdScheduleResult?.destination || this.wizardSelectedRoute?.destination || 'Destination';
    return `${origin} → ${destination}`;
  }

  getFormattedCreatedScheduleId(): string {
    if (!this.createdScheduleResult) {
      return '—';
    }
    const id = this.createdScheduleResult.id;
    if (id != null && id !== '' && !isNaN(Number(id)) && Number(id) > 0) {
      return `SCH-${id}`;
    }
    return '—';
  }

  getCreatedScheduleTimestamp(): string {
    if (!this.createdScheduleResult?.createdAt) {
      return 'Recently';
    }
    try {
      const raw = this.createdScheduleResult.createdAt;
      let d: Date;
      if (typeof raw === 'string') {
        d = new Date(raw);
      } else if (Array.isArray(raw)) {
        d = new Date(raw[0], raw[1] - 1, raw[2], raw[3] || 0, raw[4] || 0, raw[5] || 0);
      } else {
        d = new Date(raw);
      }
      if (isNaN(d.getTime())) {
        return String(raw);
      }
      const day = d.getDate();
      const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      const month = months[d.getMonth()];
      const year = d.getFullYear();
      let hours = d.getHours();
      const minutes = String(d.getMinutes()).padStart(2, '0');
      const ampm = hours >= 12 ? 'PM' : 'AM';
      hours = hours % 12 || 12;
      const formattedHours = hours < 10 ? '0' + hours : String(hours);
      return `${day} ${month} ${year}, ${formattedHours}:${minutes} ${ampm}`;
    } catch (e) {
      return String(this.createdScheduleResult.createdAt);
    }
  }

  isCreatedScheduleActive(): boolean {
    const st = (this.createdScheduleResult?.status || 'SCHEDULED').toUpperCase();
    return st === 'SCHEDULED' || st === 'ACTIVE';
  }

  getCreatedScheduleStatusText(): string {
    if (this.isCreatedScheduleActive()) {
      return 'Active';
    }
    return this.createdScheduleResult?.status || 'Active';
  }

  getCreatedScheduleStatusMessage(): string {
    if (this.isCreatedScheduleActive()) {
      return 'The schedule is now active and available for bookings.';
    }
    const st = (this.createdScheduleResult?.status || '').toUpperCase();
    if (st === 'CANCELLED') {
      return 'The schedule has been cancelled.';
    }
    return `The schedule is currently marked as ${st.toLowerCase()}.`;
  }

  goToCreatedScheduleDetails() {
    if (this.createdScheduleResult) {
      const scheduleToOpen = { ...this.createdScheduleResult };
      this.finishWizardAndGoToSchedules();
      this.openScheduleDetails(scheduleToOpen);
    } else {
      this.finishWizardAndGoToSchedules();
    }
  }

  finishWizardAndGoToSchedules() {
    this.scheduleViewMode = 'list';
    this.scheduleWizardStep = 1;
    this.resetWizardData();
    this.loadSchedules();
  }

  startNewScheduleWizard() {
    this.resetWizardData();
    this.scheduleWizardStep = 1;
    this.scheduleViewMode = 'create';
  }

  resetWizardData() {
    this.wizardSelectedRouteId = null;
    this.wizardSelectedRoute = null;
    this.wizardSelectedBusId = null;
    this.wizardSelectedBus = null;
    this.expandedRouteId = null;
    this.expandedBusId = null;
    this.wizardBusSearchQuery = '';
    this.wizardBusTypeFilter = 'ALL';
    this.wizardTripStartDate = '';
    this.wizardTripStartTime = '06:00';
    this.wizardTripEndDate = '';
    this.wizardTripEndTime = '01:30';
    this.wizardStopTimings = [];
    this.timingConflict = null;
    this.timingValidationError = null;
    this.timingChronologicalError = null;
    this.step3ValidationError = null;
    this.wizardSegmentFares = [];
    this.wizardJourneyFares = [];
    this.fareValidationError = null;
    this.wizardFareOverrides = [];
    this.createdScheduleResult = null;
    this.scheduleCreationError = null;
    this.scheduleCreationSuccess = false;
    this.reviewSectionExpanded = { 1: false, 2: false, 3: true, 4: false, 5: false };
  }

  backToConfigureTimings() {
    this.scheduleWizardStep = 3;
  }

  viewConflictingSchedule() {
    this.showConflictingScheduleModal = true;
  }

  closeConflictingScheduleModal() {
    this.showConflictingScheduleModal = false;
  }

  toggleStopPointsExpansion(stop: any, event?: Event) {
    if (event) event.stopPropagation();
    stop.isExpanded = !stop.isExpanded;
  }

  exportSchedulesToCsv() {
    const data = this.filteredSchedules;
    if (!data || data.length === 0) {
      this.showScheduleToast('info', 'No schedules to export for current filters.');
      return;
    }

    const headers = [
      'Schedule ID',
      'Origin',
      'Destination',
      'Bus Number',
      'Bus Type',
      'Travel Date',
      'Departure Time',
      'Arrival Date',
      'Arrival Time',
      'Duration',
      'Base Fare (INR)',
      'Available Seats',
      'Total Seats',
      'Status'
    ];

    const rows = data.map(s => [
      `SCH-${s.id}`,
      `"${(s.origin || '').replace(/"/g, '""')}"`,
      `"${(s.destination || '').replace(/"/g, '""')}"`,
      `"${(s.busNumber || this.getBusNumber(s.busId) || '').replace(/"/g, '""')}"`,
      `"${(s.busType || this.getBusType(s.busId) || '').replace(/"/g, '""')}"`,
      s.travelDate || '',
      this.formatScheduleTime(s.departureTime),
      this.getArrivalDate(s),
      this.formatScheduleTime(s.arrivalTime),
      this.calculateDuration(s),
      s.fare || 0,
      s.availableSeats || 0,
      s.capacity || this.getBusCapacity(s.busId) || 40,
      this.getScheduleComputedStatus(s)
    ]);

    const csvContent = [headers.join(','), ...rows.map(r => r.join(','))].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    const nowStr = new Date().toISOString().split('T')[0];
    link.setAttribute('download', `traveleasy_schedules_${nowStr}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    this.showScheduleToast('success', `Exported ${data.length} schedules to CSV.`);
  }

  openScheduleDetails(schedule: any) {
    this.closeScheduleActionMenu();
    this.selectedScheduleForDetails = schedule;
    this.showScheduleDetailsModal = true;
    this.isLoadingScheduleDetails = true;
    this.selectedScheduleStops = [];
    this.busService.getScheduleStops(schedule.id).subscribe({
      next: (stops) => {
        this.selectedScheduleStops = stops || [];
        this.isLoadingScheduleDetails = false;
      },
      error: (err) => {
        console.warn('Could not load specific schedule stops', err);
        this.isLoadingScheduleDetails = false;
      }
    });
  }

  closeScheduleDetailsModal() {
    this.showScheduleDetailsModal = false;
    this.selectedScheduleForDetails = null;
    this.selectedScheduleStops = [];
  }

  openCancelScheduleModal(schedule: any) {
    this.closeScheduleActionMenu();
    this.scheduleToCancel = schedule;
    this.cancelScheduleError = null;
    this.cancelScheduleSuccess = false;
    this.showCancelScheduleModal = true;
  }

  closeCancelScheduleModal() {
    this.showCancelScheduleModal = false;
    this.scheduleToCancel = null;
    this.cancelScheduleError = null;
    this.cancelScheduleSuccess = false;
  }

  confirmCancelSchedule() {
    if (!this.scheduleToCancel) return;
    this.isCancellingSchedule = true;
    this.cancelScheduleError = null;
    this.bookingService.cancelOperatorSchedule(this.scheduleToCancel.id).subscribe({
      next: () => {
        this.isCancellingSchedule = false;
        this.cancelScheduleSuccess = true;
        this.showScheduleToast('success', `Schedule SCH-${this.scheduleToCancel.id} was successfully cancelled.`);
        this.loadSchedules();
        setTimeout(() => {
          this.closeCancelScheduleModal();
          if (this.showScheduleDetailsModal && this.selectedScheduleForDetails?.id === this.scheduleToCancel?.id) {
            this.closeScheduleDetailsModal();
          }
        }, 900);
      },
      error: (err) => {
        this.isCancellingSchedule = false;
        this.cancelScheduleError = err?.error?.message || err?.message || 'Failed to cancel schedule. Please try again.';
      }
    });
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
