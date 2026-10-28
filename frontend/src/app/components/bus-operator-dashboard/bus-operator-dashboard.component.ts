import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { BusService } from '../../services/bus.service';
import { BookingService } from '../../services/booking.service';
import { User } from '../../models/user.model';
import { Route, RouteStop, RoutePoint, CreateRouteRequest, ScheduleStop } from '../../models/bus.model';

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
            <div class="row g-3 mb-4">
              <!-- Card 1: Total Buses -->
              <div class="col-xl-3 col-sm-6">
                <div class="metric-card">
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
                <div class="metric-card">
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
                <div class="metric-card">
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
                <div class="metric-card">
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
                  <div class="quick-action-card" (click)="showAddBusModal = true" role="button">
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

          <!-- ==================== VIEW 2: REGISTERED BUSES ==================== -->
          <div *ngIf="activeNav === 'buses'">
            <div class="d-flex justify-content-between align-items-center mb-4">
              <div>
                <h4 class="fw-bold text-dark mb-1"><i class="fa-solid fa-bus text-primary me-2"></i>Registered Fleet</h4>
                <p class="text-muted small mb-0">Manage operator buses and seating layout templates</p>
              </div>
              <button class="btn btn-primary rounded-pill fw-bold" (click)="showAddBusModal = true">
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
            <div class="row g-3 mb-4">
              <!-- Card 1: Total Routes -->
              <div class="col-xl-3 col-sm-6">
                <div class="metric-card">
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
                <div class="metric-card">
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
                <div class="metric-card">
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
                <div class="metric-card">
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

          <!-- ==================== VIEW 4: SCHEDULES ==================== -->
          <div *ngIf="activeNav === 'schedules'">
            <div class="d-flex justify-content-between align-items-center mb-4">
              <div>
                <h4 class="fw-bold text-dark mb-1"><i class="fa-solid fa-calendar-days text-primary me-2"></i>Trip Schedules</h4>
                <p class="text-muted small mb-0">Active and upcoming departures published for passenger booking</p>
              </div>
              <button class="btn btn-success rounded-pill fw-bold" (click)="openAddScheduleModal()">
                <i class="fa-solid fa-plus me-1"></i> + Schedule New Trip
              </button>
            </div>

            <div class="table-responsive bg-white rounded shadow-sm">
              <table class="table table-hover align-middle mb-0">
                <thead class="table-dark">
                  <tr>
                    <th>Sched ID</th>
                    <th>Route / Journey</th>
                    <th>Bus Number</th>
                    <th>Travel Date</th>
                    <th>Departure - Arrival</th>
                    <th>Base Fare</th>
                    <th>Available Seats</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let s of mySchedules">
                    <td><strong>#{{ s.id }}</strong></td>
                    <td>
                      <span *ngIf="s.routeId" class="badge bg-info text-dark me-1">Route #{{ s.routeId }}</span>
                      <strong>{{ s.origin }}</strong> <i class="fa-solid fa-arrow-right mx-1 text-muted small"></i> <strong>{{ s.destination }}</strong>
                    </td>
                    <td><span class="badge bg-secondary">{{ s.busNumber }}</span></td>
                    <td>{{ s.travelDate }}</td>
                    <td>{{ s.departureTime }} - {{ s.arrivalTime }}</td>
                    <td class="fw-bold text-success">₹{{ s.fare }}</td>
                    <td>{{ s.availableSeats }}</td>
                    <td>
                      <span class="badge" [class.bg-success]="s.status === 'SCHEDULED'" [class.bg-danger]="s.status === 'CANCELLED'">
                        {{ s.status }}
                      </span>
                    </td>
                  </tr>
                  <tr *ngIf="mySchedules.length === 0">
                    <td colspan="8" class="text-center py-4 text-muted">No trip schedules published yet.</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

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
      border-radius: 16px;
      padding: 20px 22px;
      display: flex;
      align-items: center;
      gap: 16px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }

    .metric-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.05);
    }

    .metric-icon-box {
      width: 52px;
      height: 52px;
      border-radius: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      flex-shrink: 0;
    }

    .metric-label {
      font-size: 13px;
      font-weight: 500;
      color: #64748b;
      margin-bottom: 2px;
    }

    .metric-value {
      font-size: 24px;
      font-weight: 800;
      color: #0f172a;
      line-height: 1.1;
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
  `]
})
export class BusOperatorDashboardComponent implements OnInit {
  currentUser: User | null = null;
  activeNav: 'dashboard' | 'buses' | 'routes' | 'schedules' | 'bookings' | 'profile' | 'support' = 'dashboard';
  isSidebarCollapsed = false;

  myBuses: any[] = [];
  myRoutes: Route[] = [];
  mySchedules: any[] = [];
  operatorBookings: any[] = [];

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

  // Add Bus Modal State
  showAddBusModal = false;
  newBusNumber = '';
  newBusType = 'AC 2+1 Seater (Single Deck)';
  newFacilityType = 'AC';
  newBusConfiguration = 'SINGLE_DECK';
  newTotalSeats = 36;
  newAmenities = 'Wi-Fi, Charging Point, Water Bottle';

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
    this.busService.getBusesByOperator(this.currentUser.id).subscribe({
      next: (res) => this.myBuses = res || []
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

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
