import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { BusService } from '../../services/bus.service';
import { HotelService } from '../../services/hotel.service';
import { BookingService } from '../../services/booking.service';
import { TravellerService } from '../../services/traveller.service';
import { User } from '../../models/user.model';

export interface DestinationAttraction {
  name: string;
  category: string;
  image: string;
}

export interface Destination {
  id: string;
  name: string;
  tags: string;
  category: string;
  description: string;
  image: string;
  heroImage: string;
  bestTime: string;
  travelOptions: string;
  attractions: DestinationAttraction[];
}

@Component({
  selector: 'app-customer-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="dashboard-wrapper d-flex flex-column min-vh-100">
      
      <!-- ==================== TOP HEADER ==================== -->
      <header class="top-header sticky-top d-flex align-items-center justify-content-between px-4 bg-white border-bottom">
        <!-- Brand Logo (Matches Home Page) -->
        <a class="te-brand" (click)="switchTab('dashboard')" role="button" title="Go to Dashboard">
          <span class="te-brand-icon">
            <i class="fa-solid fa-paper-plane"></i>
          </span>
          <span class="te-brand-text">Travel <span class="te-brand-blue">Easy</span></span>
        </a>

        <!-- Global Search Input with Auto-Suggestions -->
        <div class="header-search-box position-relative">
          <div class="search-input-pill d-flex align-items-center px-3 py-2">
            <i class="fa-solid fa-magnifying-glass text-muted me-2"></i>
            <input 
              type="text" 
              class="form-control border-0 bg-transparent p-0 shadow-none search-input" 
              placeholder="Search destinations, hotels, buses..."
              [(ngModel)]="searchQuery"
              (input)="onSearchInput()"
              (focus)="showSearchSuggestions = searchQuery.trim().length > 0"
              (keydown.enter)="executeGlobalSearch()"
            >
            <button *ngIf="searchQuery" class="btn btn-link btn-sm text-muted p-0 ms-1 border-0" (click)="clearSearch()">
              <i class="fa-solid fa-xmark"></i>
            </button>
          </div>

          <!-- Suggestions Dropdown -->
          <div *ngIf="showSearchSuggestions && filteredSuggestions.length > 0" class="search-suggestions-dropdown position-absolute bg-white shadow-lg rounded-3 border mt-1 w-100 py-2">
            <div class="px-3 py-1 text-muted small fw-semibold text-uppercase">Suggestions</div>
            <div 
              *ngFor="let s of filteredSuggestions" 
              class="suggestion-item px-3 py-2 d-flex align-items-center justify-content-between cursor-pointer"
              (click)="selectSuggestion(s)"
            >
              <div class="d-flex align-items-center gap-2">
                <i [class]="s.icon" class="text-primary fs-6"></i>
                <div>
                  <div class="fw-semibold text-dark small">{{ s.title }}</div>
                  <small class="text-muted">{{ s.subtitle }}</small>
                </div>
              </div>
              <span class="badge bg-light text-secondary border small">{{ s.type }}</span>
            </div>
          </div>
        </div>

        <!-- Right Header Actions (Notifications & User Pill) -->
        <div class="d-flex align-items-center gap-3">
          <!-- Notification Bell -->
          <div class="position-relative">
            <button class="btn btn-light rounded-circle notification-btn position-relative" (click)="toggleNotifications()" title="Notifications">
              <i class="fa-solid fa-bell text-secondary"></i>
              <span *ngIf="unreadNotificationsCount > 0" class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger">
                {{ unreadNotificationsCount }}
              </span>
            </button>

            <!-- Notifications Dropdown -->
            <div *ngIf="showNotifications" class="notifications-dropdown position-absolute end-0 mt-2 bg-white shadow-lg rounded-4 border p-3">
              <div class="d-flex justify-content-between align-items-center mb-2 pb-2 border-bottom">
                <span class="fw-bold text-dark">Notifications</span>
                <button *ngIf="unreadNotificationsCount > 0" class="btn btn-link btn-sm text-primary p-0 text-decoration-none" (click)="markNotificationsRead()">Mark all as read</button>
              </div>
              <div *ngIf="notificationList.length > 0; else emptyNotifications" class="notifications-list">
                <div *ngFor="let n of notificationList" class="notification-item p-2 rounded-3 mb-1" [class.bg-light]="!n.read">
                  <div class="d-flex gap-2">
                    <i [class]="n.icon" class="mt-1" [class.text-success]="n.type === 'SUCCESS'" [class.text-primary]="n.type === 'INFO'" [class.text-warning]="n.type === 'ALERT'"></i>
                    <div>
                      <div class="fw-semibold text-dark small">{{ n.title }}</div>
                      <div class="text-muted smaller">{{ n.message }}</div>
                      <div class="text-muted smaller opacity-75 mt-1">{{ n.time }}</div>
                    </div>
                  </div>
                </div>
              </div>
              <ng-template #emptyNotifications>
                <div class="text-center py-3 text-muted small">
                  <i class="fa-regular fa-bell-slash fs-4 mb-2 opacity-50 d-block"></i>
                  No new notifications
                </div>
              </ng-template>
            </div>
          </div>

          <!-- User Pill with Dropdown -->
          <div class="position-relative">
            <div class="user-pill d-flex align-items-center gap-2 py-1 px-2 rounded-pill border cursor-pointer bg-white" (click)="toggleUserMenu()">
              <div class="user-avatar-circle d-flex align-items-center justify-content-center">
                <span>{{ getUserInitials() }}</span>
              </div>
              <div class="user-meta d-none d-sm-block text-start">
                <div class="fw-bold text-dark lh-1 small">{{ currentUser?.fullName || 'Traveller' }}</div>
                <small class="text-muted smaller">Traveller</small>
              </div>
              <i class="fa-solid fa-chevron-down text-muted smaller ms-1"></i>
            </div>

            <!-- Profile Menu Dropdown -->
            <div *ngIf="showUserMenu" class="user-menu-dropdown position-absolute end-0 mt-2 bg-white shadow-lg rounded-4 border py-2">
              <div class="px-3 py-2 border-bottom">
                <div class="fw-bold text-dark">{{ currentUser?.fullName || 'Traveller' }}</div>
                <div class="text-muted small">{{ currentUser?.email }}</div>
              </div>
              <button class="dropdown-item py-2 px-3 d-flex align-items-center gap-2 text-dark" (click)="switchTab('profile'); showUserMenu = false;">
                <i class="fa-regular fa-user text-primary"></i> My Profile
              </button>
              <button class="dropdown-item py-2 px-3 d-flex align-items-center gap-2 text-dark" (click)="switchTab('settings'); showUserMenu = false;">
                <i class="fa-solid fa-gear text-secondary"></i> Travel Settings
              </button>
              <button class="dropdown-item py-2 px-3 d-flex align-items-center gap-2 text-dark" (click)="switchTab('support'); showUserMenu = false;">
                <i class="fa-solid fa-headset text-success"></i> Support & Help
              </button>
              <div class="dropdown-divider"></div>
              <button class="dropdown-item py-2 px-3 d-flex align-items-center gap-2 text-danger" (click)="logout()">
                <i class="fa-solid fa-arrow-right-from-bracket"></i> Sign Out
              </button>
            </div>
          </div>
        </div>
      </header>

      <!-- ==================== MAIN BODY: SIDEBAR + CONTENT ==================== -->
      <div class="dashboard-body d-flex flex-grow-1">
        
        <!-- ==================== LEFT SIDEBAR ==================== -->
        <aside class="left-sidebar bg-white border-end d-flex flex-column py-3 px-3">
          <ul class="nav flex-column gap-1">
            <li class="nav-item">
              <a 
                class="nav-link sidebar-link rounded-3" 
                [class.active]="activeTab === 'dashboard' || activeTab === 'destination-details'" 
                (click)="switchTab('dashboard')"
              >
                <i class="fa-solid fa-house me-3"></i>
                <span>Dashboard</span>
              </a>
            </li>
            <li class="nav-item">
              <a 
                class="nav-link sidebar-link rounded-3" 
                [class.active]="activeTab === 'bus'" 
                (click)="switchTab('bus')"
              >
                <i class="fa-solid fa-bus me-3"></i>
                <span>Book Buses</span>
              </a>
            </li>
            <li class="nav-item">
              <a 
                class="nav-link sidebar-link rounded-3" 
                [class.active]="activeTab === 'hotel'" 
                (click)="switchTab('hotel')"
              >
                <i class="fa-solid fa-hotel me-3"></i>
                <span>Book Hotels</span>
              </a>
            </li>
            <li class="nav-item">
              <a 
                class="nav-link sidebar-link rounded-3" 
                [class.active]="activeTab === 'my-bookings'" 
                (click)="switchTab('my-bookings')"
              >
                <i class="fa-solid fa-calendar-days me-3"></i>
                <span>My Bookings</span>
              </a>
            </li>
            <li class="nav-item">
              <a 
                class="nav-link sidebar-link rounded-3" 
                [class.active]="activeTab === 'favourites'" 
                (click)="switchTab('favourites')"
              >
                <i class="fa-regular fa-heart me-3"></i>
                <span>Saved</span>
              </a>
            </li>
            <li class="nav-item">
              <a 
                class="nav-link sidebar-link rounded-3" 
                [class.active]="activeTab === 'profile'" 
                (click)="switchTab('profile')"
              >
                <i class="fa-regular fa-user me-3"></i>
                <span>Profile</span>
              </a>
            </li>
            <li class="nav-item">
              <a 
                class="nav-link sidebar-link rounded-3" 
                [class.active]="activeTab === 'settings'" 
                (click)="switchTab('settings')"
              >
                <i class="fa-solid fa-gear me-3"></i>
                <span>Settings</span>
              </a>
            </li>
            <li class="nav-item">
              <a 
                class="nav-link sidebar-link rounded-3" 
                [class.active]="activeTab === 'support'" 
                (click)="switchTab('support')"
              >
                <i class="fa-solid fa-headphones me-3"></i>
                <span>Support</span>
              </a>
            </li>
          </ul>

          <div class="mt-auto pt-4 px-2">
            <div class="sidebar-help-card p-3 rounded-4 bg-light border text-center">
              <i class="fa-solid fa-circle-question fs-3 text-primary mb-2"></i>
              <div class="fw-semibold text-dark small">Need assistance?</div>
              <div class="text-muted smaller mb-2">Our support team is available 24/7.</div>
              <button class="btn btn-outline-primary btn-sm rounded-pill w-100 fw-semibold" (click)="switchTab('support')">
                Contact Helpdesk
              </button>
            </div>
          </div>
        </aside>

        <!-- ==================== MAIN CONTENT AREA ==================== -->
        <main class="main-content flex-grow-1 p-4" style="background-color: #F5F9FE;">

          <!-- #################### TAB 0: DASHBOARD #################### -->
          <div *ngIf="activeTab === 'dashboard'">
            <!-- Greeting Header -->
            <div class="greeting-section mb-4">
              <h1 class="greeting-title fw-bold text-navy mb-1">Hi, {{ getFirstName() }}! 👋</h1>
              <p class="greeting-subtitle text-muted mb-0">Where would you like to go next?</p>
            </div>

            <!-- Two Hero Booking Cards -->
            <div class="row g-4 mb-4">
              <!-- Book a Bus Card -->
              <div class="col-lg-6">
                <div class="hero-booking-card bus-hero-card position-relative overflow-hidden rounded-4 cursor-pointer" (click)="switchTab('bus')">
                  <div class="hero-card-overlay d-flex flex-column justify-content-between p-4 h-100">
                    <div>
                      <div class="hero-icon-pill bus-icon-pill d-inline-flex align-items-center justify-content-center mb-3">
                        <i class="fa-solid fa-bus fs-5 text-white"></i>
                      </div>
                      <h3 class="hero-card-title text-white fw-bold mb-1">Book a Bus</h3>
                      <p class="hero-card-sub text-white-50 mb-0">Explore routes and book your seats</p>
                    </div>
                    <div class="d-flex justify-content-end">
                      <div class="hero-arrow-btn d-flex align-items-center justify-content-center rounded-circle bg-white shadow">
                        <i class="fa-solid fa-arrow-right text-primary"></i>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Book a Hotel Card -->
              <div class="col-lg-6">
                <div class="hero-booking-card hotel-hero-card position-relative overflow-hidden rounded-4 cursor-pointer" (click)="switchTab('hotel')">
                  <div class="hero-card-overlay d-flex flex-column justify-content-between p-4 h-100">
                    <div>
                      <div class="hero-icon-pill hotel-icon-pill d-inline-flex align-items-center justify-content-center mb-3">
                        <i class="fa-solid fa-hotel fs-5 text-white"></i>
                      </div>
                      <h3 class="hero-card-title text-white fw-bold mb-1">Book a Hotel</h3>
                      <p class="hero-card-sub text-white-50 mb-0">Find a hotel and plan your stay</p>
                    </div>
                    <div class="d-flex justify-content-end">
                      <div class="hero-arrow-btn d-flex align-items-center justify-content-center rounded-circle bg-white shadow">
                        <i class="fa-solid fa-arrow-right text-primary"></i>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Popular Destinations Section -->
            <div class="popular-destinations-section mb-4">
              <div class="d-flex justify-content-between align-items-center mb-3">
                <h4 class="fw-bold text-navy mb-0">Popular Destinations</h4>
                <a class="text-primary text-decoration-none fw-semibold small cursor-pointer" (click)="scrollOrFocusDestinations()">
                  View All <i class="fa-solid fa-angle-right ms-1"></i>
                </a>
              </div>

              <div class="row row-cols-2 row-cols-md-3 row-cols-lg-6 g-3">
                <div *ngFor="let dest of destinations" class="col">
                  <div class="destination-card h-100 bg-white rounded-4 border overflow-hidden cursor-pointer shadow-sm position-relative" (click)="viewDestinationDetails(dest)">
                    <!-- Image container -->
                    <div class="dest-img-wrapper position-relative">
                      <img [src]="dest.image" [alt]="dest.name" class="dest-thumb w-100 object-fit-cover" (error)="onImageError($event, dest.heroImage)">
                      <!-- Heart Icon -->
                      <button 
                        class="btn-fav-circle position-absolute top-0 end-0 m-2 rounded-circle border-0 d-flex align-items-center justify-content-center shadow-sm"
                        (click)="toggleDestinationFavourite($event, dest)"
                        [title]="isDestinationFavourited(dest.name) ? 'Remove from Saved' : 'Save to Favourites'"
                      >
                        <i [class]="isDestinationFavourited(dest.name) ? 'fa-solid fa-heart text-danger' : 'fa-regular fa-heart text-muted'"></i>
                      </button>
                    </div>
                    <!-- Body -->
                    <div class="p-3">
                      <h6 class="fw-bold text-navy mb-1 text-truncate">{{ dest.name }}</h6>
                      <p class="text-muted smaller mb-0 text-truncate">{{ dest.tags }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Two-Column Bottom Section: Upcoming Bookings & Saved -->
            <div class="row g-4">
              <!-- Upcoming Bookings Panel (Left) -->
              <div class="col-lg-7">
                <div class="content-panel bg-white rounded-4 p-4 border shadow-sm h-100">
                  <div class="d-flex justify-content-between align-items-center mb-3">
                    <div class="d-flex align-items-center gap-2">
                      <div class="icon-squircle bg-soft-blue text-primary d-flex align-items-center justify-content-center">
                        <i class="fa-solid fa-calendar-days fs-5"></i>
                      </div>
                      <div>
                        <h5 class="fw-bold text-navy mb-0">Upcoming Bookings</h5>
                        <small class="text-muted">Your upcoming trips and reservations</small>
                      </div>
                    </div>
                    <a class="text-primary text-decoration-none fw-semibold small cursor-pointer" (click)="switchTab('my-bookings')">
                      View All
                    </a>
                  </div>

                  <!-- Bookings List (Strict real data) -->
                  <div *ngIf="displayBookings.length > 0; else emptyBookings">
                    <div *ngFor="let b of displayBookings" class="booking-row-card d-flex align-items-center justify-content-between p-3 rounded-4 mb-2 border cursor-pointer" (click)="switchTab('my-bookings')">
                      <div class="d-flex align-items-center gap-3">
                        <img [src]="b.thumbnail" class="booking-thumb rounded-3 object-fit-cover" alt="Booking Thumbnail">
                        <div>
                          <div class="d-flex align-items-center gap-2 mb-1">
                            <span class="badge" [class.badge-bus]="b.serviceType === 'BUS'" [class.badge-hotel]="b.serviceType === 'HOTEL'">{{ b.serviceType === 'BUS' ? 'Bus' : 'Hotel' }}</span>
                            <span class="fw-bold text-navy">{{ b.title }}</span>
                          </div>
                          <div class="text-muted smaller mb-1">
                            <i class="fa-regular fa-clock me-1"></i>{{ b.dateString }}
                          </div>
                          <div class="text-secondary smaller">
                            {{ b.metaString }}
                          </div>
                        </div>
                      </div>
                      <div class="d-flex align-items-center gap-3">
                        <span class="badge" [class.badge-confirmed]="b.status === 'CONFIRMED'" [class.badge-upcoming]="b.status === 'UPCOMING' || b.status === 'PENDING_PAYMENT'">
                          {{ b.status === 'CONFIRMED' ? 'Confirmed' : 'Upcoming' }}
                        </span>
                        <div class="chevron-circle d-flex align-items-center justify-content-center text-muted">
                          <i class="fa-solid fa-chevron-right smaller"></i>
                        </div>
                      </div>
                    </div>
                  </div>

                  <ng-template #emptyBookings>
                    <div class="text-center py-4 text-muted">
                      <i class="fa-solid fa-ticket-simple fs-1 opacity-50 mb-2"></i>
                      <p class="mb-2">No upcoming bookings found. Ready for your next journey?</p>
                      <div class="d-flex justify-content-center gap-2">
                        <button class="btn btn-primary btn-sm rounded-pill px-3" (click)="switchTab('bus')">Book a Bus</button>
                        <button class="btn btn-outline-primary btn-sm rounded-pill px-3" (click)="switchTab('hotel')">Book a Hotel</button>
                      </div>
                    </div>
                  </ng-template>
                </div>
              </div>

              <!-- Saved Panel (Right) -->
              <div class="col-lg-5">
                <div class="content-panel bg-white rounded-4 p-4 border shadow-sm h-100">
                  <div class="d-flex justify-content-between align-items-center mb-3">
                    <div class="d-flex align-items-center gap-2">
                      <div class="icon-squircle bg-soft-pink text-danger d-flex align-items-center justify-content-center">
                        <i class="fa-solid fa-heart fs-5"></i>
                      </div>
                      <div>
                        <h5 class="fw-bold text-navy mb-0">Saved</h5>
                        <small class="text-muted">Your saved hotels, buses and destinations</small>
                      </div>
                    </div>
                    <a class="text-primary text-decoration-none fw-semibold small cursor-pointer" (click)="switchTab('favourites')">
                      View All
                    </a>
                  </div>

                  <!-- Saved Grid (Strict real data) -->
                  <div *ngIf="displaySavedItems.length > 0; else emptySaved">
                    <div class="row g-3">
                      <div *ngFor="let s of displaySavedItems" class="col-sm-6">
                        <div class="saved-card h-100 bg-white rounded-4 border overflow-hidden position-relative shadow-sm cursor-pointer" (click)="openSavedItem(s)">
                          <div class="saved-img-wrapper position-relative">
                            <img [src]="s.thumbnail" class="w-100 object-fit-cover" style="height: 100px;" alt="Saved thumbnail">
                            <button class="btn-fav-circle position-absolute top-0 end-0 m-2 rounded-circle border-0 d-flex align-items-center justify-content-center shadow-sm" (click)="removeSavedItemFromPanel($event, s)">
                              <i class="fa-solid fa-heart text-danger"></i>
                            </button>
                          </div>
                          <div class="p-3">
                            <span class="badge bg-light text-secondary border small mb-1">{{ s.itemType }}</span>
                            <h6 class="fw-bold text-navy mb-1 text-truncate">{{ s.title }}</h6>
                            <p class="text-muted smaller mb-0 text-truncate">{{ s.subtitle }}</p>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <ng-template #emptySaved>
                    <div class="text-center py-4 text-muted">
                      <i class="fa-regular fa-heart fs-1 opacity-50 mb-2"></i>
                      <p class="mb-2">No saved items yet. Tap the heart icon on any bus, hotel, or destination to bookmark it!</p>
                    </div>
                  </ng-template>
                </div>
              </div>
            </div>
          </div>

          <!-- #################### TAB: DESTINATION DETAILS #################### -->
          <div *ngIf="activeTab === 'destination-details' && selectedDestination">
            <!-- Back Button -->
            <button class="btn btn-link text-decoration-none text-muted mb-3 p-0 fw-semibold d-inline-flex align-items-center gap-2" (click)="switchTab('dashboard')">
              <i class="fa-solid fa-arrow-left"></i> Back to Dashboard
            </button>

            <!-- Destination Hero Banner -->
            <div class="dest-details-hero position-relative rounded-4 overflow-hidden mb-4">
              <img [src]="selectedDestination.heroImage" [alt]="selectedDestination.name" class="w-100 h-100 object-fit-cover position-absolute top-0 start-0">
              <div class="dest-hero-overlay position-relative p-4 p-md-5 d-flex flex-column justify-content-between h-100">
                <div class="d-flex justify-content-between align-items-start">
                  <div>
                    <h1 class="display-5 fw-bold text-white mb-2">{{ selectedDestination.name }}</h1>
                    <div class="dest-tags text-white-50 fw-semibold mb-3 fs-5">{{ selectedDestination.category }}</div>
                    <p class="dest-description text-white opacity-90 max-w-600 mb-0">
                      {{ selectedDestination.description }}
                    </p>
                  </div>
                  <!-- Save Destination Heart -->
                  <button 
                    class="btn-fav-large rounded-circle bg-white border-0 shadow d-flex align-items-center justify-content-center"
                    (click)="toggleDestinationFavourite($event, selectedDestination)"
                  >
                    <i [class]="isDestinationFavourited(selectedDestination.name) ? 'fa-solid fa-heart text-danger fs-5' : 'fa-regular fa-heart text-secondary fs-5'"></i>
                  </button>
                </div>
              </div>
            </div>

            <!-- Destination Sub-Tabs -->
            <div class="dest-subtabs-nav d-flex gap-2 mb-4 overflow-x-auto pb-2">
              <button 
                *ngFor="let t of ['Overview', 'Top Attractions', 'Travel Options', 'Best Time', 'Photos']"
                class="btn rounded-pill px-4 py-2 fw-semibold text-nowrap"
                [class.btn-primary]="selectedDestTab === t"
                [class.btn-light]="selectedDestTab !== t"
                (click)="selectedDestTab = t"
              >
                {{ t }}
              </button>
            </div>

            <!-- Tab Content: Overview / Top Attractions -->
            <div class="row g-4" *ngIf="selectedDestTab === 'Overview' || selectedDestTab === 'Top Attractions'">
              <!-- Left Column: Top Attractions Cards -->
              <div class="col-lg-8">
                <div class="d-flex justify-content-between align-items-center mb-3">
                  <h4 class="fw-bold text-navy mb-0">Top Attractions</h4>
                  <span class="text-muted small">Highlights in {{ selectedDestination.name }}</span>
                </div>

                <div class="row row-cols-1 row-cols-sm-2 g-3">
                  <div *ngFor="let att of selectedDestination.attractions" class="col">
                    <div class="attraction-card bg-white rounded-4 border overflow-hidden shadow-sm h-100">
                      <div class="position-relative">
                        <img [src]="att.image" [alt]="att.name" class="w-100 object-fit-cover" style="height: 140px;">
                      </div>
                      <div class="p-3">
                        <h6 class="fw-bold text-navy mb-1">{{ att.name }}</h6>
                        <span class="badge bg-light text-secondary border small">{{ att.category }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Right Column: Plan Your Trip Action Card -->
              <div class="col-lg-4">
                <div class="plan-trip-card bg-white rounded-4 p-4 border shadow-sm sticky-top" style="top: 90px;">
                  <h5 class="fw-bold text-navy mb-4">Plan Your Trip to {{ selectedDestination.name }}</h5>

                  <!-- Find Buses Action -->
                  <div class="plan-action-box d-flex align-items-center justify-content-between p-3 rounded-4 border mb-3 cursor-pointer" (click)="findBusesToDestination(selectedDestination.name)">
                    <div class="d-flex align-items-center gap-3">
                      <div class="action-icon-squircle bg-primary text-white d-flex align-items-center justify-content-center">
                        <i class="fa-solid fa-bus fs-5"></i>
                      </div>
                      <div>
                        <div class="fw-bold text-navy">Find Buses</div>
                        <small class="text-muted">Search buses to {{ selectedDestination.name }}</small>
                      </div>
                    </div>
                    <i class="fa-solid fa-chevron-right text-muted"></i>
                  </div>

                  <!-- Find Hotels Action -->
                  <div class="plan-action-box d-flex align-items-center justify-content-between p-3 rounded-4 border cursor-pointer" (click)="findHotelsInDestination(selectedDestination.name)">
                    <div class="d-flex align-items-center gap-3">
                      <div class="action-icon-squircle bg-indigo text-white d-flex align-items-center justify-content-center">
                        <i class="fa-solid fa-hotel fs-5"></i>
                      </div>
                      <div>
                        <div class="fw-bold text-navy">Find Hotels</div>
                        <small class="text-muted">Search hotels in {{ selectedDestination.name }}</small>
                      </div>
                    </div>
                    <i class="fa-solid fa-chevron-right text-muted"></i>
                  </div>
                </div>
              </div>
            </div>

            <!-- Tab Content: Travel Options -->
            <div *ngIf="selectedDestTab === 'Travel Options'" class="card p-4 rounded-4 border bg-white shadow-sm">
              <h5 class="fw-bold text-navy mb-3"><i class="fa-solid fa-route text-primary me-2"></i>How to Reach {{ selectedDestination.name }}</h5>
              <p class="text-muted">{{ selectedDestination.travelOptions }}</p>
              <div class="d-flex gap-3 mt-3">
                <button class="btn btn-primary rounded-pill px-4 fw-bold" (click)="findBusesToDestination(selectedDestination.name)">
                  <i class="fa-solid fa-bus me-2"></i>Check Bus Schedules
                </button>
                <button class="btn btn-outline-primary rounded-pill px-4 fw-bold" (click)="findHotelsInDestination(selectedDestination.name)">
                  <i class="fa-solid fa-hotel me-2"></i>Explore Nearby Hotels
                </button>
              </div>
            </div>

            <!-- Tab Content: Best Time -->
            <div *ngIf="selectedDestTab === 'Best Time'" class="card p-4 rounded-4 border bg-white shadow-sm">
              <h5 class="fw-bold text-navy mb-3"><i class="fa-solid fa-cloud-sun text-warning me-2"></i>Best Time to Visit</h5>
              <p class="lead text-dark fw-semibold">{{ selectedDestination.bestTime }}</p>
              <p class="text-muted">Enjoy pleasant weather, outdoor excursions, sightseeing, and local cultural festivals during these peak months.</p>
            </div>

            <!-- Tab Content: Photos -->
            <div *ngIf="selectedDestTab === 'Photos'" class="row g-3">
              <div class="col-md-6">
                <img [src]="selectedDestination.heroImage" class="w-100 rounded-4 shadow-sm object-fit-cover" style="height: 300px;" alt="Photo 1">
              </div>
              <div *ngFor="let att of selectedDestination.attractions" class="col-md-3">
                <img [src]="att.image" class="w-100 rounded-4 shadow-sm object-fit-cover" style="height: 300px;" [alt]="att.name">
              </div>
            </div>
          </div>

          <!-- #################### TAB 1: BUS BOOKING #################### -->
          <div *ngIf="activeTab === 'bus'">
            <div class="search-form-card bg-white p-4 rounded-4 shadow-sm border mb-4">
              <h4 class="fw-bold text-navy mb-3"><i class="fa-solid fa-bus text-primary me-2"></i>Search Buses</h4>
              <form (ngSubmit)="onSearchBuses()" class="row g-3 align-items-end">
                <div class="col-lg-3 col-md-6">
                  <label class="form-label fw-semibold text-muted small">From</label>
                  <input type="text" class="form-control rounded-3" [(ngModel)]="busOrigin" name="busOrigin" placeholder="Enter origin city">
                </div>
                <div class="col-auto d-flex align-items-center justify-content-center pb-1">
                  <button type="button" class="btn btn-light rounded-circle border shadow-sm p-2" (click)="swapBusCities()" title="Swap Origin and Destination">
                    <i class="fa-solid fa-arrow-right-arrow-left text-primary"></i>
                  </button>
                </div>
                <div class="col-lg-3 col-md-6">
                  <label class="form-label fw-semibold text-muted small">To</label>
                  <input type="text" class="form-control rounded-3" [(ngModel)]="busDestination" name="busDestination" placeholder="Enter destination city">
                </div>
                <div class="col-lg-3 col-md-6">
                  <label class="form-label fw-semibold text-muted small">Journey Date</label>
                  <input type="date" class="form-control rounded-3" [(ngModel)]="busDate" name="busDate">
                </div>
                <div class="col-lg-2 col-md-6">
                  <button type="submit" class="btn btn-primary w-100 rounded-3 py-2 fw-bold">
                    <i class="fa-solid fa-magnifying-glass me-1"></i> Search Buses
                  </button>
                </div>
              </form>
            </div>

            <!-- Bus Search Results -->
            <div *ngIf="busesSearched">
              <div class="d-flex justify-content-between align-items-center mb-3">
                <h5 class="fw-bold text-navy mb-0">Available Buses ({{ busResults.length }})</h5>
                <div *ngIf="busResults.length > 0" class="d-flex align-items-center gap-2">
                  <span class="text-muted small">Sort by:</span>
                  <select class="form-select form-select-sm rounded-pill w-auto" [(ngModel)]="busSortBy" (change)="sortBuses()">
                    <option value="departureTime">Departure Time</option>
                    <option value="fareLow">Fare: Low to High</option>
                    <option value="fareHigh">Fare: High to Low</option>
                  </select>
                </div>
              </div>

              <!-- Strict Empty State for Buses -->
              <div *ngIf="busResults.length === 0" class="alert alert-info rounded-4 border-0 shadow-sm p-4 text-center">
                <i class="fa-solid fa-circle-info fs-3 text-info mb-2"></i>
                <h6 class="fw-bold text-navy mb-1">No buses available</h6>
                <p class="mb-0 text-muted small">No scheduled buses were found from <strong>{{ busOrigin }}</strong> to <strong>{{ busDestination }}</strong> on {{ busDate }}. Try searching for routes with active inventory (e.g. Vijayawada to Hyderabad).</p>
              </div>

              <!-- Bus Cards List (Strict real data) -->
              <div *ngFor="let b of busResults" class="bus-result-card bg-white rounded-4 p-4 border shadow-sm mb-3">
                <div class="row align-items-center g-3">
                  <div class="col-lg-3 col-md-4">
                    <div class="d-flex align-items-center gap-3">
                      <img [src]="b.thumbnail || 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=400&q=80'" class="bus-thumb rounded-3 object-fit-cover" alt="Bus photo">
                      <div>
                        <div class="d-flex align-items-center gap-2">
                          <h6 class="fw-bold text-navy mb-0">{{ b.operatorName }}</h6>
                          <button class="btn btn-link p-0 text-danger border-0" (click)="saveBusToFavourites(b)" title="Save to Favourites">
                            <i class="fa-regular fa-heart"></i>
                          </button>
                        </div>
                        <span class="badge bg-light text-secondary border small mt-1">{{ b.busType }}</span>
                        <div class="text-muted smaller mt-1"><i class="fa-solid fa-van-shuttle me-1"></i>{{ b.busNumber }}</div>
                      </div>
                    </div>
                  </div>

                  <div class="col-lg-5 col-md-5 text-center">
                    <div class="d-flex justify-content-around align-items-center">
                      <div>
                        <h5 class="fw-bold text-navy mb-0">{{ b.departureTime }}</h5>
                        <small class="text-muted">{{ b.origin }}</small>
                      </div>
                      <div class="d-flex flex-column align-items-center px-3">
                        <small class="text-muted smaller mb-1">{{ b.duration || 'Scheduled' }}</small>
                        <i class="fa-solid fa-arrow-right text-primary"></i>
                      </div>
                      <div>
                        <h5 class="fw-bold text-navy mb-0">{{ b.arrivalTime }}</h5>
                        <small class="text-muted">{{ b.destination }}</small>
                      </div>
                    </div>
                    <div class="mt-2 text-muted smaller">
                      <i class="fa-solid fa-chair text-success me-1"></i> {{ b.availableSeats }} Seats Left
                    </div>
                  </div>

                  <div class="col-lg-4 col-md-3 text-end">
                    <h4 class="fw-bold text-navy mb-1">₹{{ b.fare }}</h4>
                    <button class="btn btn-primary rounded-pill px-4 fw-bold" (click)="openBusSeatMap(b)">
                      Select Seats
                    </button>
                  </div>
                </div>

                <!-- Seat Picker Accordion -->
                <div *ngIf="selectedSchedule?.id === b.id" class="mt-4 pt-3 border-top bg-light p-4 rounded-4">
                  <h6 class="fw-bold text-navy mb-3"><i class="fa-solid fa-chair me-2 text-primary"></i>Select Your Seats for {{ b.operatorName }}</h6>
                  
                  <div *ngIf="seatList.length === 0" class="alert alert-warning small mb-0 rounded-3">
                    <i class="fa-solid fa-triangle-exclamation me-1"></i> No seat layout details returned for this bus schedule.
                  </div>

                  <div *ngIf="seatList.length > 0">
                    <div class="d-flex gap-3 mb-3 small">
                      <span class="d-flex align-items-center"><span class="seat-legend available me-1"></span> Available</span>
                      <span class="d-flex align-items-center"><span class="seat-legend selected me-1"></span> Selected</span>
                      <span class="d-flex align-items-center"><span class="seat-legend booked me-1"></span> Booked</span>
                    </div>

                    <div class="d-flex flex-wrap gap-2 mb-3">
                      <div *ngFor="let s of seatList" 
                           class="seat-box rounded-2" 
                           [class.available]="s.status === 'AVAILABLE'"
                           [class.selected]="selectedSeatNumbers.includes(s.seatNumber)"
                           [class.booked]="s.status === 'BOOKED'"
                           (click)="toggleSeat(s)">
                        {{ s.seatNumber }}
                      </div>
                    </div>

                    <div *ngIf="selectedSeatNumbers.length > 0" class="d-flex justify-content-between align-items-center bg-white p-3 rounded-4 border">
                      <div>
                        <span class="fw-bold text-navy">Selected Seats: </span> {{ selectedSeatNumbers.join(', ') }}
                        <span class="ms-3 fw-bold text-success">Total Amount: ₹{{ selectedSeatNumbers.length * b.fare }}</span>
                      </div>
                      <button class="btn btn-success fw-bold rounded-pill px-4" (click)="proceedToBusCheckout(b)">
                        Proceed to Payment
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- #################### TAB 2: HOTEL BOOKING #################### -->
          <div *ngIf="activeTab === 'hotel'">
            <div class="search-form-card bg-white p-4 rounded-4 shadow-sm border mb-4">
              <h4 class="fw-bold text-navy mb-3"><i class="fa-solid fa-hotel text-primary me-2"></i>Search Hotels</h4>
              <form (ngSubmit)="onSearchHotels()" class="row g-3 align-items-end">
                <div class="col-lg-4 col-md-6">
                  <label class="form-label fw-semibold text-muted small">Destination City</label>
                  <input type="text" class="form-control rounded-3" [(ngModel)]="hotelCity" name="hotelCity" placeholder="City or Hotel Name">
                </div>
                <div class="col-lg-3 col-md-6">
                  <label class="form-label fw-semibold text-muted small">Check-in</label>
                  <input type="date" class="form-control rounded-3" [(ngModel)]="checkInDate" name="checkInDate">
                </div>
                <div class="col-lg-3 col-md-6">
                  <label class="form-label fw-semibold text-muted small">Check-out</label>
                  <input type="date" class="form-control rounded-3" [(ngModel)]="checkOutDate" name="checkOutDate">
                </div>
                <div class="col-lg-2 col-md-6">
                  <button type="submit" class="btn btn-primary w-100 rounded-3 py-2 fw-bold">
                    <i class="fa-solid fa-magnifying-glass me-1"></i> Search Hotels
                  </button>
                </div>
              </form>
            </div>

            <!-- Hotel Search Results -->
            <div *ngIf="hotelsSearched">
              <div class="d-flex justify-content-between align-items-center mb-3">
                <h5 class="fw-bold text-navy mb-0">Hotels in {{ hotelCity }} ({{ hotelResults.length }})</h5>
                <div *ngIf="hotelResults.length > 0" class="d-flex align-items-center gap-2">
                  <span class="text-muted small">Sort by:</span>
                  <select class="form-select form-select-sm rounded-pill w-auto" [(ngModel)]="hotelSortBy" (change)="sortHotels()">
                    <option value="popularity">Popularity</option>
                    <option value="priceLow">Price: Low to High</option>
                    <option value="priceHigh">Price: High to Low</option>
                    <option value="rating">Rating</option>
                  </select>
                </div>
              </div>

              <!-- Strict Empty State for Hotels -->
              <div *ngIf="hotelResults.length === 0" class="alert alert-info rounded-4 border-0 shadow-sm p-4 text-center">
                <i class="fa-solid fa-circle-info fs-3 text-info mb-2"></i>
                <h6 class="fw-bold text-navy mb-1">No hotels found in "{{ hotelCity }}"</h6>
                <p class="mb-0 text-muted small">No active hotel properties matched your destination and dates. Try searching for cities with registered hotels (e.g. Vijayawada or Hyderabad).</p>
              </div>

              <!-- Hotel Cards List (Strict real data) -->
              <div *ngFor="let h of hotelResults" class="hotel-result-card bg-white rounded-4 border shadow-sm mb-3 overflow-hidden">
                <div class="row g-0">
                  <div class="col-md-4">
                    <img [src]="h.imageUrl || 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=600&q=80'" class="w-100 h-100 object-fit-cover" style="min-height: 200px;" alt="Hotel Photo">
                  </div>
                  <div class="col-md-8 p-4 d-flex flex-column justify-content-between">
                    <div>
                      <div class="d-flex justify-content-between align-items-start">
                        <div>
                          <div class="d-flex align-items-center gap-2">
                            <h5 class="fw-bold text-navy mb-1">{{ h.name }}</h5>
                            <button class="btn btn-link p-0 text-danger border-0" (click)="saveHotelToFavourites(h)" title="Save to Favourites">
                              <i class="fa-regular fa-heart"></i>
                            </button>
                          </div>
                          <div class="small fw-semibold text-dark mb-2">
                            <span class="text-warning me-1">★</span>{{ h.starRating || 4.5 }} Star
                            <span class="text-muted mx-2">•</span>
                            <span class="text-muted">{{ h.address }}, {{ h.city }}</span>
                          </div>
                        </div>
                      </div>
                      <p class="text-muted small mb-2">{{ h.description }}</p>
                      <div class="d-flex flex-wrap gap-1 mb-2">
                        <span *ngFor="let a of getAmenityPills(h.amenities)" class="badge bg-light text-secondary border small">{{ a }}</span>
                      </div>
                    </div>

                    <div class="d-flex justify-content-between align-items-end mt-3 pt-3 border-top">
                      <div>
                        <div *ngIf="h.pricePerNight" class="fw-bold text-navy fs-4">₹{{ h.pricePerNight }} <small class="text-muted fs-6 fw-normal">per night</small></div>
                      </div>
                      <button class="btn btn-primary rounded-pill px-4 fw-bold" (click)="viewHotelRooms(h)">
                        View Rooms
                      </button>
                    </div>
                  </div>
                </div>

                <!-- Rooms Accordion -->
                <div *ngIf="selectedHotel?.id === h.id" class="p-4 bg-light border-top">
                  <h6 class="fw-bold text-navy mb-3"><i class="fa-solid fa-bed me-2 text-primary"></i>Available Room Types for {{ h.name }}</h6>
                  
                  <div *ngIf="hotelRoomTypes.length === 0" class="alert alert-warning small mb-0 rounded-3">
                    <i class="fa-solid fa-triangle-exclamation me-1"></i> No room types are currently listed for this property.
                  </div>

                  <div *ngIf="hotelRoomTypes.length > 0" class="row g-3">
                    <div *ngFor="let r of hotelRoomTypes" class="col-md-6">
                      <div class="room-type-card bg-white p-3 rounded-4 border h-100 d-flex flex-column justify-content-between">
                        <div>
                          <h6 class="fw-bold text-navy mb-1">{{ r.roomTypeName }}</h6>
                          <p class="small text-muted mb-2">Max Occupancy: {{ r.maxOccupancy }} Guests | {{ r.amenities }}</p>
                        </div>
                        <div class="d-flex justify-content-between align-items-center pt-2 border-top">
                          <span class="fw-bold text-success fs-5">₹{{ r.pricePerNight }} <small class="fs-6 text-muted">/ night</small></span>
                          <button class="btn btn-success btn-sm rounded-pill px-3 fw-bold" (click)="proceedToHotelCheckout(h, r)">
                            Book Room
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- #################### TAB 3: MY BOOKINGS #################### -->
          <div *ngIf="activeTab === 'my-bookings'">
            <!-- Page Header Row -->
            <div class="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-3">
              <div>
                <h3 class="fw-bold text-navy mb-1">My Bookings</h3>
                <div class="text-secondary small">View and manage all your trips and reservations</div>
              </div>
              <div class="d-flex align-items-center gap-2 position-relative">
                <button class="btn btn-outline-secondary btn-sm rounded-3 px-3 py-2" (click)="loadMyBookings()" title="Refresh Bookings">
                  <i class="fa-solid fa-rotate-right" [class.fa-spin]="isLoadingBookings"></i>
                </button>
                <button class="btn btn-primary d-flex align-items-center gap-2 px-3 py-2 rounded-3 fw-semibold shadow-sm" (click)="toggleNewBookingMenu()">
                  <i class="fa-solid fa-plus"></i>
                  <span>New Booking</span>
                </button>
                <!-- New Booking Dropdown -->
                <div *ngIf="showNewBookingMenu" class="new-booking-dropdown bg-white shadow-lg rounded-3 border py-1">
                  <button class="dropdown-item py-2 px-3 d-flex align-items-center gap-2 text-dark small" (click)="switchTab('bus'); showNewBookingMenu = false;">
                    <i class="fa-solid fa-bus text-primary"></i> Book a Bus
                  </button>
                  <button class="dropdown-item py-2 px-3 d-flex align-items-center gap-2 text-dark small" (click)="switchTab('hotel'); showNewBookingMenu = false;">
                    <i class="fa-solid fa-hotel text-success"></i> Book a Hotel
                  </button>
                </div>
              </div>
            </div>

            <!-- Category Tabs (Exactly 3 tabs: All, Bus, Hotel) -->
            <div class="d-flex align-items-center gap-2 mb-4 flex-wrap">
              <button 
                class="tab-pill-btn" 
                [class.active]="bookingCategoryTab === 'ALL'" 
                (click)="setBookingCategoryTab('ALL')"
              >
                All Bookings ({{ allBookingsCount }})
              </button>
              <button 
                class="tab-pill-btn" 
                [class.active]="bookingCategoryTab === 'BUS'" 
                (click)="setBookingCategoryTab('BUS')"
              >
                Bus Bookings ({{ busBookingsCount }})
              </button>
              <button 
                class="tab-pill-btn" 
                [class.active]="bookingCategoryTab === 'HOTEL'" 
                (click)="setBookingCategoryTab('HOTEL')"
              >
                Hotel Bookings ({{ hotelBookingsCount }})
              </button>
            </div>

            <!-- Search & Filter Toolbar -->
            <div class="booking-filter-bar d-flex align-items-center gap-3 mb-4 flex-wrap">
              <!-- Search Input -->
              <div class="booking-search-box d-flex align-items-center px-3">
                <i class="fa-solid fa-magnifying-glass text-muted me-2"></i>
                <input 
                  type="text" 
                  class="form-control border-0 bg-transparent p-0 shadow-none small" 
                  placeholder="Search by booking ID, destination or hotel name..."
                  [(ngModel)]="bookingSearchQuery"
                  (input)="bookingCurrentPage = 1"
                >
                <button *ngIf="bookingSearchQuery" class="btn btn-link btn-sm text-muted p-0 ms-1 border-0" (click)="bookingSearchQuery = ''; bookingCurrentPage = 1">
                  <i class="fa-solid fa-xmark"></i>
                </button>
              </div>

              <!-- From Date Picker (Left) -->
              <div class="date-filter-box d-flex align-items-center gap-2" (click)="openDatePicker(fromDateInput)">
                <i class="fa-regular fa-calendar text-muted"></i>
                <input 
                  #fromDateInput
                  [type]="isFromDateFocused || bookingFromDate ? 'date' : 'text'" 
                  class="form-control border-0 bg-transparent p-0 shadow-none small date-input-control" 
                  placeholder="From Date"
                  title="From Date"
                  [(ngModel)]="bookingFromDate" 
                  (focus)="isFromDateFocused = true"
                  (blur)="isFromDateFocused = false"
                  (change)="onFromDateChange()"
                >
                <button *ngIf="bookingFromDate" class="btn btn-link btn-sm text-muted p-0 border-0 ms-auto" (click)="$event.stopPropagation(); bookingFromDate = ''; onFromDateChange()" title="Clear From Date">
                  <i class="fa-solid fa-xmark smaller"></i>
                </button>
              </div>

              <!-- To Date Picker (Right) -->
              <div class="date-filter-box d-flex align-items-center gap-2" (click)="openDatePicker(toDateInput)">
                <input 
                  #toDateInput
                  [type]="isToDateFocused || bookingToDate ? 'date' : 'text'" 
                  class="form-control border-0 bg-transparent p-0 shadow-none small date-input-control" 
                  placeholder="To Date"
                  title="To Date"
                  [min]="bookingFromDate"
                  [(ngModel)]="bookingToDate" 
                  (focus)="isToDateFocused = true"
                  (blur)="isToDateFocused = false"
                  (change)="onToDateChange()"
                >
                <i class="fa-regular fa-calendar text-muted"></i>
                <button *ngIf="bookingToDate" class="btn btn-link btn-sm text-muted p-0 border-0 ms-auto" (click)="$event.stopPropagation(); bookingToDate = ''; onToDateChange()" title="Clear To Date">
                  <i class="fa-solid fa-xmark smaller"></i>
                </button>
              </div>

              <!-- Status Select -->
              <div class="d-flex align-items-center gap-2">
                <select 
                  class="form-select status-filter-select" 
                  [(ngModel)]="bookingStatusFilter"
                  (change)="bookingCurrentPage = 1"
                >
                  <option value="ALL">All Statuses</option>
                  <option value="CONFIRMED">Confirmed</option>
                  <option value="UPCOMING">Upcoming</option>
                  <option value="COMPLETED">Completed</option>
                  <option value="CANCELLED">Cancelled</option>
                </select>
              </div>

              <!-- Clear Filters -->
              <button 
                *ngIf="hasActiveBookingFilters()" 
                class="btn btn-link btn-sm text-primary text-decoration-none p-0 d-flex align-items-center gap-1 ms-auto"
                (click)="clearBookingFilters()"
              >
                <i class="fa-solid fa-rotate-left"></i> Clear Filters
              </button>
            </div>

            <!-- Loading State -->
            <div *ngIf="isLoadingBookings" class="card bg-white rounded-4 border p-5 text-center my-4 shadow-sm">
              <div class="spinner-border text-primary mb-3" role="status"></div>
              <div class="fw-semibold text-dark">Loading your bookings...</div>
            </div>

            <!-- Error State -->
            <div *ngIf="bookingsError && !isLoadingBookings" class="alert alert-danger rounded-4 border-0 p-4 text-center shadow-sm my-4">
              <i class="fa-solid fa-circle-exclamation fs-3 mb-2 d-block"></i>
              <div class="fw-bold mb-1">Failed to load bookings</div>
              <p class="mb-3 small">{{ bookingsError }}</p>
              <button class="btn btn-primary btn-sm rounded-pill px-4" (click)="loadMyBookings()">Try Again</button>
            </div>

            <!-- Strict Empty State: User has 0 bookings -->
            <div *ngIf="!isLoadingBookings && !bookingsError && myBookings.length === 0" class="card bg-white rounded-4 border p-5 text-center my-4 shadow-sm">
              <div class="d-inline-flex align-items-center justify-content-center rounded-circle bg-light text-primary mx-auto mb-3" style="width: 72px; height: 72px;">
                <i class="fa-solid fa-ticket fs-2 opacity-75"></i>
              </div>
              <h5 class="fw-bold text-navy mb-2">No bookings yet</h5>
              <p class="text-secondary small max-w-600 mx-auto mb-4">
                You haven't made any bus or hotel bookings yet. Explore routes and destinations to plan your next journey!
              </p>
              <div class="d-flex justify-content-center gap-3">
                <button class="btn btn-primary rounded-pill px-4 fw-semibold" (click)="switchTab('bus')">
                  <i class="fa-solid fa-bus me-2"></i>Book Buses
                </button>
                <button class="btn btn-outline-primary rounded-pill px-4 fw-semibold" (click)="switchTab('hotel')">
                  <i class="fa-solid fa-hotel me-2"></i>Book Hotels
                </button>
              </div>
            </div>

            <!-- Strict Empty State: Filters yielded 0 results -->
            <div *ngIf="!isLoadingBookings && !bookingsError && myBookings.length > 0 && filteredBookings.length === 0" class="card bg-white rounded-4 border p-5 text-center my-4 shadow-sm">
              <div class="d-inline-flex align-items-center justify-content-center rounded-circle bg-light text-secondary mx-auto mb-3" style="width: 72px; height: 72px;">
                <i class="fa-solid fa-filter-circle-xmark fs-2 opacity-75"></i>
              </div>
              <h5 class="fw-bold text-navy mb-2">No bookings match your selected filters</h5>
              <p class="text-secondary small max-w-600 mx-auto mb-4">
                Try adjusting your search terms, date range, or status filter to find your bookings.
              </p>
              <button class="btn btn-outline-primary rounded-pill px-4 fw-semibold" (click)="clearBookingFilters()">
                <i class="fa-solid fa-rotate-left me-2"></i>Clear All Filters
              </button>
            </div>

            <!-- Booking Cards List -->
            <div *ngIf="!isLoadingBookings && !bookingsError && pagedBookings.length > 0" class="booking-cards-list">
              <div *ngFor="let b of pagedBookings" class="booking-card-item mb-3">
                <div class="d-flex align-items-center gap-3 flex-column flex-md-row">
                  <!-- Thumbnail -->
                  <img [src]="b.thumbnail" (error)="onImageError($event, 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=400&q=80')" class="booking-card-img" alt="Booking Thumbnail">
                  
                  <!-- Content Details -->
                  <div class="flex-grow-1 w-100">
                    <div class="mb-1">
                      <span [class]="b.serviceType === 'BUS' ? 'badge-type-bus' : 'badge-type-hotel'">
                        {{ b.serviceType === 'BUS' ? 'Bus' : 'Hotel' }}
                      </span>
                    </div>
                    <h5 class="fw-bold text-navy mb-1 cursor-pointer" (click)="openBookingDetails(b)">{{ b.displayTitle }}</h5>
                    <div class="text-secondary small mb-2">{{ b.displaySubtitle }}</div>
                    
                    <!-- Metadata icons row -->
                    <div class="d-flex align-items-center gap-3 text-secondary smaller flex-wrap">
                      <span *ngIf="b.dateText">
                        <i class="fa-regular fa-calendar-days text-muted me-1"></i>{{ b.dateText }}
                      </span>
                      <span *ngIf="b.serviceType === 'BUS' && b.timeText">
                        <i class="fa-regular fa-clock text-muted me-1"></i>{{ b.timeText }}
                      </span>
                      <span *ngIf="b.capacityText">
                        <i class="fa-solid fa-user-group text-muted me-1"></i>{{ b.capacityText }}
                      </span>
                      <span>
                        <i class="fa-solid fa-ticket text-muted me-1"></i>{{ b.bookingRef }}
                      </span>
                    </div>
                  </div>

                  <!-- Price, Status & Actions -->
                  <div class="d-flex flex-column align-items-md-end justify-content-between gap-3 w-100 w-md-auto text-md-end">
                    <div class="d-flex align-items-center gap-3 justify-content-between justify-content-md-end w-100">
                      <span class="badge-status-pill" [ngClass]="b.statusBadgeClass">
                        {{ b.statusDisplay }}
                      </span>
                      <div class="booking-card-price">
                        ₹{{ b.totalAmount | number }}
                      </div>
                      <i class="fa-solid fa-chevron-right text-primary cursor-pointer fs-6 d-none d-md-inline-block" (click)="openBookingDetails(b)" title="View Details"></i>
                    </div>

                    <div class="d-flex align-items-center gap-2 justify-content-end w-100">
                      <button class="btn btn-view-details" (click)="openBookingDetails(b)">
                        View Details
                      </button>
                      <button *ngIf="b.isCancellable" class="btn btn-cancel-booking" (click)="promptCancelBooking(b)">
                        Cancel Booking
                      </button>
                      <button *ngIf="b.isCompleted" class="btn btn-outline-primary btn-sm rounded-3 px-3 py-1 fw-semibold" (click)="openReviewModal(b.raw)">
                        Rate & Review
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Pagination Footer -->
              <div class="d-flex justify-content-between align-items-center pt-3 pb-4 flex-wrap gap-3">
                <div class="text-secondary small">
                  Showing {{ paginationStartItem }} – {{ paginationEndItem }} of {{ filteredBookings.length }} bookings
                </div>
                <div class="d-flex align-items-center gap-1">
                  <button class="pagination-arrow-btn" [disabled]="bookingCurrentPage === 1" (click)="prevBookingPage()" title="Previous Page">
                    <i class="fa-solid fa-chevron-left smaller"></i>
                  </button>
                  <button 
                    *ngFor="let p of paginationPagesList" 
                    class="pagination-page-btn" 
                    [class.active]="bookingCurrentPage === p" 
                    (click)="setBookingPage(p)"
                  >
                    {{ p }}
                  </button>
                  <button class="pagination-arrow-btn" [disabled]="bookingCurrentPage === totalBookingPages" (click)="nextBookingPage()" title="Next Page">
                    <i class="fa-solid fa-chevron-right smaller"></i>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- #################### TAB 4: SAVED (FAVOURITES) #################### -->
          <div *ngIf="activeTab === 'favourites'">
            <div class="d-flex justify-content-between align-items-center mb-4">
              <h4 class="fw-bold text-navy mb-0"><i class="fa-solid fa-heart text-danger me-2"></i>Saved Items</h4>
              <div class="btn-group btn-group-sm">
                <button class="btn btn-outline-secondary" [class.active]="favouriteFilter === ''" (click)="filterFavourites('')">All</button>
                <button class="btn btn-outline-secondary" [class.active]="favouriteFilter === 'DESTINATION'" (click)="filterFavourites('DESTINATION')">Destinations</button>
                <button class="btn btn-outline-secondary" [class.active]="favouriteFilter === 'BUS'" (click)="filterFavourites('BUS')">Buses</button>
                <button class="btn btn-outline-secondary" [class.active]="favouriteFilter === 'HOTEL'" (click)="filterFavourites('HOTEL')">Hotels</button>
              </div>
            </div>

            <div *ngIf="favouritesList.length === 0" class="alert alert-info rounded-4 border-0 p-4 text-center shadow-sm">
              <i class="fa-regular fa-heart fs-2 mb-2 opacity-50"></i>
              <p class="mb-0">No saved items found. Bookmark destinations, buses, or hotels to access them quickly anytime!</p>
            </div>

            <div class="row g-3">
              <div *ngFor="let f of favouritesList" class="col-md-6">
                <div class="card bg-white rounded-4 border shadow-sm p-3 h-100 d-flex flex-column justify-content-between">
                  <div class="d-flex justify-content-between align-items-start mb-2">
                    <div>
                      <span class="badge bg-light text-secondary border small mb-1">{{ f.itemType }}</span>
                      <h6 class="fw-bold text-navy mb-1">{{ f.title }}</h6>
                      <small class="text-muted">{{ f.subtitle }}</small>
                    </div>
                    <button class="btn btn-link text-danger p-0 border-0" (click)="removeFavourite(f.id)" title="Remove">
                      <i class="fa-solid fa-trash-can"></i>
                    </button>
                  </div>
                  <div class="pt-2 border-top d-flex justify-content-end">
                    <button *ngIf="f.itemType === 'BUS' || f.itemType === 'ROUTE'" class="btn btn-primary btn-sm rounded-pill px-3" (click)="switchTab('bus')">
                      Search Buses
                    </button>
                    <button *ngIf="f.itemType === 'HOTEL'" class="btn btn-primary btn-sm rounded-pill px-3" (click)="switchTab('hotel')">
                      Search Hotels
                    </button>
                    <button *ngIf="f.itemType === 'DESTINATION'" class="btn btn-primary btn-sm rounded-pill px-3" (click)="openSavedDestination(f.title)">
                      View Destination
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- #################### TAB 5: PROFILE #################### -->
          <div *ngIf="activeTab === 'profile'">
            <div class="card bg-white rounded-4 border shadow-sm p-4">
              <div class="d-flex justify-content-between align-items-center mb-4">
                <h4 class="fw-bold text-navy mb-0"><i class="fa-regular fa-user text-primary me-2"></i>Traveller Profile</h4>
                <span class="badge" [class.bg-success]="profileData?.profileComplete" [class.bg-warning]="!profileData?.profileComplete">
                  {{ profileData?.profileComplete ? 'Complete Profile' : 'Profile Incomplete' }}
                </span>
              </div>

              <form (ngSubmit)="saveProfile()" class="row g-3">
                <div class="col-md-6">
                  <label class="form-label fw-semibold small">Full Name</label>
                  <input type="text" class="form-control rounded-3" [value]="currentUser?.fullName" disabled>
                </div>
                <div class="col-md-6">
                  <label class="form-label fw-semibold small">Email Address</label>
                  <input type="email" class="form-control rounded-3" [value]="currentUser?.email" disabled>
                </div>
                <div class="col-md-6">
                  <label class="form-label fw-semibold small">Date of Birth</label>
                  <input type="date" class="form-control rounded-3" [(ngModel)]="profileModel.dateOfBirth" name="dateOfBirth">
                </div>
                <div class="col-md-6">
                  <label class="form-label fw-semibold small">Gender</label>
                  <select class="form-select rounded-3" [(ngModel)]="profileModel.gender" name="gender">
                    <option value="MALE">Male</option>
                    <option value="FEMALE">Female</option>
                    <option value="OTHER">Other</option>
                  </select>
                </div>
                <div class="col-12">
                  <label class="form-label fw-semibold small">Residential Address</label>
                  <input type="text" class="form-control rounded-3" [(ngModel)]="profileModel.address" name="address" placeholder="Flat, Street, Area">
                </div>
                <div class="col-md-4">
                  <label class="form-label fw-semibold small">City</label>
                  <input type="text" class="form-control rounded-3" [(ngModel)]="profileModel.city" name="city">
                </div>
                <div class="col-md-4">
                  <label class="form-label fw-semibold small">State</label>
                  <input type="text" class="form-control rounded-3" [(ngModel)]="profileModel.state" name="state">
                </div>
                <div class="col-md-4">
                  <label class="form-label fw-semibold small">Pincode</label>
                  <input type="text" class="form-control rounded-3" [(ngModel)]="profileModel.pincode" name="pincode">
                </div>
                <div class="col-md-6">
                  <label class="form-label fw-semibold small">Emergency Contact Name</label>
                  <input type="text" class="form-control rounded-3" [(ngModel)]="profileModel.emergencyContactName" name="emergencyContactName">
                </div>
                <div class="col-md-6">
                  <label class="form-label fw-semibold small">Emergency Contact Phone</label>
                  <input type="text" class="form-control rounded-3" [(ngModel)]="profileModel.emergencyContactPhone" name="emergencyContactPhone">
                </div>
                <div class="col-12 text-end mt-4">
                  <button type="submit" class="btn btn-primary rounded-pill px-4 fw-bold">
                    <i class="fa-solid fa-floppy-disk me-1"></i> Save Changes
                  </button>
                </div>
              </form>
            </div>
          </div>

          <!-- #################### TAB 6: SETTINGS #################### -->
          <div *ngIf="activeTab === 'settings'">
            <div class="card bg-white rounded-4 border shadow-sm p-4">
              <h4 class="fw-bold text-navy mb-4"><i class="fa-solid fa-gear text-primary me-2"></i>Preferences & Settings</h4>
              <form (ngSubmit)="savePreferences()" class="row g-3">
                <div class="col-md-6">
                  <label class="form-label fw-semibold small">Seat Preference</label>
                  <select class="form-select rounded-3" [(ngModel)]="preferencesModel.seatPreference" name="seatPreference">
                    <option value="WINDOW">Window Seat</option>
                    <option value="AISLE">Aisle Seat</option>
                    <option value="NO_PREFERENCE">No Preference</option>
                  </select>
                </div>
                <div class="col-md-6">
                  <label class="form-label fw-semibold small">Berth Preference</label>
                  <select class="form-select rounded-3" [(ngModel)]="preferencesModel.berthPreference" name="berthPreference">
                    <option value="LOWER">Lower Berth</option>
                    <option value="UPPER">Upper Berth</option>
                    <option value="NO_PREFERENCE">No Preference</option>
                  </select>
                </div>
                <div class="col-md-6">
                  <label class="form-label fw-semibold small">Meal Preference</label>
                  <select class="form-select rounded-3" [(ngModel)]="preferencesModel.mealPreference" name="mealPreference">
                    <option value="VEG">Vegetarian</option>
                    <option value="NON_VEG">Non-Vegetarian</option>
                    <option value="JAIN">Jain Meal</option>
                    <option value="ANY">Any / No Preference</option>
                  </select>
                </div>
                <div class="col-md-6">
                  <label class="form-label fw-semibold small">Preferred Currency</label>
                  <select class="form-select rounded-3" [(ngModel)]="preferencesModel.preferredCurrency" name="preferredCurrency">
                    <option value="INR">INR (₹)</option>
                    <option value="USD">USD ($)</option>
                    <option value="EUR">EUR (€)</option>
                  </select>
                </div>
                <div class="col-12 mt-4">
                  <h6 class="fw-bold text-navy mb-2">Notification Preferences</h6>
                  <div class="form-check form-switch mb-2">
                    <input class="form-check-input" type="checkbox" id="emailNotif" [(ngModel)]="preferencesModel.emailNotifications" name="emailNotifications">
                    <label class="form-check-label" for="emailNotif">Receive booking confirmations & updates via Email</label>
                  </div>
                  <div class="form-check form-switch">
                    <input class="form-check-input" type="checkbox" id="smsNotif" [(ngModel)]="preferencesModel.smsNotifications" name="smsNotifications">
                    <label class="form-check-label" for="smsNotif">Receive trip reminder notifications & alerts via SMS</label>
                  </div>
                </div>
                <div class="col-12 text-end mt-4">
                  <button type="submit" class="btn btn-primary rounded-pill px-4 fw-bold">
                    <i class="fa-solid fa-floppy-disk me-1"></i> Save Preferences
                  </button>
                </div>
              </form>
            </div>
          </div>

          <!-- #################### TAB 7: SUPPORT #################### -->
          <div *ngIf="activeTab === 'support'">
            <div class="row g-4">
              <div class="col-md-5">
                <div class="card bg-white rounded-4 border shadow-sm p-4 h-100">
                  <h5 class="fw-bold text-navy mb-3"><i class="fa-solid fa-envelope-open-text text-primary me-2"></i>Create Support Ticket</h5>
                  <form (ngSubmit)="createSupportTicket()">
                    <div class="mb-3">
                      <label class="form-label fw-semibold small">Category</label>
                      <select class="form-select rounded-3" [(ngModel)]="ticketModel.category" name="category" required>
                        <option value="BOOKING">Booking Assistance</option>
                        <option value="CANCELLATION">Cancellation & Refund</option>
                        <option value="PAYMENT">Payment Issue</option>
                        <option value="GENERAL">General Inquiry</option>
                      </select>
                    </div>
                    <div class="mb-3">
                      <label class="form-label fw-semibold small">Subject</label>
                      <input type="text" class="form-control rounded-3" [(ngModel)]="ticketModel.subject" name="subject" placeholder="Brief summary of your query" required>
                    </div>
                    <div class="mb-3">
                      <label class="form-label fw-semibold small">Message</label>
                      <textarea class="form-control rounded-3" rows="4" [(ngModel)]="ticketModel.message" name="message" placeholder="Provide full details..." required></textarea>
                    </div>
                    <button type="submit" class="btn btn-primary rounded-pill w-100 fw-bold">
                      <i class="fa-solid fa-paper-plane me-1"></i> Submit Ticket
                    </button>
                  </form>
                </div>
              </div>

              <div class="col-md-7">
                <div class="card bg-white rounded-4 border shadow-sm p-4 h-100">
                  <h5 class="fw-bold text-navy mb-3"><i class="fa-solid fa-list-check text-primary me-2"></i>Your Support Tickets</h5>
                  <div *ngIf="supportTickets.length === 0" class="text-center py-4 text-muted">
                    <i class="fa-solid fa-circle-question fs-1 mb-2 opacity-50"></i>
                    <p>No support tickets raised yet. Our team is available 24/7 if you need assistance!</p>
                  </div>
                  <div *ngFor="let t of supportTickets" class="card mb-3 p-3 border-0 bg-light rounded-4">
                    <div class="d-flex justify-content-between align-items-start">
                      <div>
                        <span class="badge bg-secondary mb-1">{{ t.category }}</span>
                        <div class="fw-bold text-dark">{{ t.subject }}</div>
                        <small class="text-muted">Ticket Ref: {{ t.ticketRef }} | {{ t.createdAt | date:'medium' }}</small>
                        <p class="small text-secondary mb-0 mt-2">{{ t.message }}</p>
                      </div>
                      <span class="badge" [class.bg-warning]="t.status === 'OPEN'" [class.bg-success]="t.status === 'RESOLVED'">{{ t.status }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

        </main>
      </div>

      <!-- ==================== CHECKOUT MODAL (COORDINATED RESERVATION) ==================== -->
      <div *ngIf="showCheckoutModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content rounded-4 border-0 shadow">
            <div class="modal-header bg-primary text-white">
              <h5 class="modal-title fw-bold"><i class="fa-solid fa-credit-card me-2"></i>Confirm & Pay</h5>
              <button type="button" class="btn-close btn-close-white" (click)="showCheckoutModal = false"></button>
            </div>
            <div class="modal-body p-4">
              <h6 class="fw-bold text-navy">{{ checkoutServiceTitle }}</h6>
              <p class="small text-muted">{{ checkoutDetails }}</p>
              <hr>
              <div class="d-flex justify-content-between mb-3 fw-bold">
                <span>Total Amount:</span>
                <span class="text-success fs-5">₹{{ checkoutAmount }}</span>
              </div>

              <div class="mb-3">
                <label class="form-label fw-bold small">Select Payment Method</label>
                <select class="form-select rounded-3" [(ngModel)]="paymentMethod">
                  <option value="CARD">Credit / Debit Card</option>
                  <option value="UPI">UPI (Google Pay / PhonePe / Paytm)</option>
                  <option value="NET_BANKING">Net Banking</option>
                </select>
              </div>

              <button class="btn btn-success w-100 py-2 fw-bold rounded-pill" [disabled]="isProcessingCheckout" (click)="submitPayment()">
                <span *ngIf="isProcessingCheckout" class="spinner-border spinner-border-sm me-2"></span>
                {{ isProcessingCheckout ? 'Reserving & Processing...' : 'Pay ₹' + checkoutAmount + ' & Confirm' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- ==================== REVIEW MODAL ==================== -->
      <div *ngIf="showReviewModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content rounded-4 border-0 shadow">
            <div class="modal-header bg-primary text-white">
              <h5 class="modal-title fw-bold"><i class="fa-solid fa-star me-2"></i>Rate & Review</h5>
              <button type="button" class="btn-close btn-close-white" (click)="showReviewModal = false"></button>
            </div>
            <div class="modal-body p-4">
              <h6 class="fw-bold mb-3">{{ reviewBooking?.serviceTitle }}</h6>
              <div class="mb-3">
                <label class="form-label fw-bold small">Rating (1 to 5 Stars)</label>
                <select class="form-select rounded-3" [(ngModel)]="reviewRating">
                  <option [value]="5">5 - Excellent</option>
                  <option [value]="4">4 - Very Good</option>
                  <option [value]="3">3 - Good</option>
                  <option [value]="2">2 - Fair</option>
                  <option [value]="1">1 - Poor</option>
                </select>
              </div>
              <div class="mb-3">
                <label class="form-label fw-bold small">Your Feedback</label>
                <textarea class="form-control rounded-3" rows="3" [(ngModel)]="reviewComment" placeholder="Share details of your experience..."></textarea>
              </div>
              <button class="btn btn-primary w-100 rounded-pill fw-bold" (click)="submitReview()">
                Submit Review
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- ==================== VIEW BOOKING DETAILS MODAL ==================== -->
      <div *ngIf="showBookingDetailsModal && selectedBookingDetails" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered modal-lg">
          <div class="modal-content rounded-4 border-0 shadow-lg">
            <div class="modal-header bg-white border-bottom px-4 py-3">
              <div class="d-flex align-items-center gap-2">
                <span [class]="selectedBookingDetails.serviceType === 'BUS' ? 'badge-type-bus' : 'badge-type-hotel'">
                  {{ selectedBookingDetails.serviceType === 'BUS' ? 'Bus' : 'Hotel' }}
                </span>
                <h5 class="modal-title fw-bold text-navy mb-0">Booking Details</h5>
              </div>
              <button type="button" class="btn-close shadow-none" (click)="closeBookingDetails()"></button>
            </div>
            <div class="modal-body p-4">
              <div class="d-flex justify-content-between align-items-start mb-4 pb-3 border-bottom flex-wrap gap-2">
                <div>
                  <h4 class="fw-bold text-navy mb-1">{{ selectedBookingDetails.displayTitle }}</h4>
                  <div class="text-secondary small mb-2">{{ selectedBookingDetails.displaySubtitle }}</div>
                  <div class="badge-status-pill" [ngClass]="selectedBookingDetails.statusBadgeClass">
                    {{ selectedBookingDetails.statusDisplay }}
                  </div>
                </div>
                <div class="text-end">
                  <div class="text-secondary small">Total Fare Paid</div>
                  <div class="fs-3 fw-bold text-primary">₹{{ selectedBookingDetails.totalAmount | number }}</div>
                </div>
              </div>

              <div class="row g-3 mb-4">
                <div class="col-sm-6">
                  <div class="p-3 bg-light rounded-3">
                    <div class="text-secondary smaller text-uppercase fw-bold mb-1">Booking Reference</div>
                    <div class="fw-bold text-dark fs-6">{{ selectedBookingDetails.bookingRef }}</div>
                  </div>
                </div>
                <div class="col-sm-6">
                  <div class="p-3 bg-light rounded-3">
                    <div class="text-secondary smaller text-uppercase fw-bold mb-1">Date & Schedule</div>
                    <div class="fw-bold text-dark fs-6">{{ selectedBookingDetails.dateText || 'Standard Booking' }}</div>
                  </div>
                </div>
                <div class="col-sm-6">
                  <div class="p-3 bg-light rounded-3">
                    <div class="text-secondary smaller text-uppercase fw-bold mb-1">Capacity / Reserved</div>
                    <div class="fw-bold text-dark fs-6">{{ selectedBookingDetails.capacityText }}</div>
                  </div>
                </div>
                <div class="col-sm-6">
                  <div class="p-3 bg-light rounded-3">
                    <div class="text-secondary smaller text-uppercase fw-bold mb-1">Booked On</div>
                    <div class="fw-bold text-dark fs-6">{{ selectedBookingDetails.rawBookingDate | date:'medium' }}</div>
                  </div>
                </div>
              </div>

              <div class="p-3 bg-light rounded-3 mb-2" *ngIf="selectedBookingDetails.detailsJson">
                <div class="text-secondary smaller text-uppercase fw-bold mb-1">Reservation Specs & Notes</div>
                <div class="text-dark small">{{ selectedBookingDetails.detailsJson }}</div>
              </div>
            </div>
            <div class="modal-footer bg-light border-top px-4 py-3 d-flex justify-content-between">
              <div>
                <button 
                  *ngIf="selectedBookingDetails.isCancellable" 
                  class="btn btn-cancel-booking"
                  (click)="promptCancelBooking(selectedBookingDetails)"
                >
                  <i class="fa-solid fa-ban me-1"></i> Cancel Booking
                </button>
                <button 
                  *ngIf="selectedBookingDetails.isCompleted" 
                  class="btn btn-outline-primary btn-sm rounded-3 px-3 py-1 fw-semibold"
                  (click)="closeBookingDetails(); openReviewModal(selectedBookingDetails.raw)"
                >
                  <i class="fa-solid fa-star me-1"></i> Rate & Review
                </button>
              </div>
              <button type="button" class="btn btn-secondary rounded-3 px-4" (click)="closeBookingDetails()">Close</button>
            </div>
          </div>
        </div>
      </div>

      <!-- ==================== CANCEL BOOKING CONFIRMATION MODAL ==================== -->
      <div *ngIf="showCancelConfirmModal && bookingToCancel" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content rounded-4 border-0 shadow-lg">
            <div class="modal-header border-0 pb-0">
              <button type="button" class="btn-close shadow-none" [disabled]="isCancellingBooking" (click)="closeCancelConfirmModal()"></button>
            </div>
            <div class="modal-body p-4 pt-0 text-center">
              <div class="d-inline-flex align-items-center justify-content-center rounded-circle bg-danger bg-opacity-10 text-danger mx-auto mb-3" style="width: 60px; height: 60px;">
                <i class="fa-solid fa-triangle-exclamation fs-3"></i>
              </div>
              <h5 class="fw-bold text-navy mb-2">Cancel This Booking?</h5>
              <p class="text-secondary small mb-3">
                Are you sure you want to cancel booking <strong>{{ bookingToCancel.bookingRef }}</strong> for <strong>{{ bookingToCancel.displayTitle || bookingToCancel.serviceTitle }}</strong>?
              </p>
              <div class="alert alert-warning py-2 px-3 rounded-3 text-start smaller text-dark mb-4">
                <i class="fa-solid fa-circle-info text-warning me-2"></i>
                This action will release your reserved seats/rooms immediately and set this booking to cancelled. This cannot be undone.
              </div>
              <div class="d-flex justify-content-center gap-3">
                <button 
                  type="button" 
                  class="btn btn-light rounded-pill px-4 fw-semibold text-secondary" 
                  [disabled]="isCancellingBooking"
                  (click)="closeCancelConfirmModal()"
                >
                  Keep Booking
                </button>
                <button 
                  type="button" 
                  class="btn btn-danger rounded-pill px-4 fw-semibold d-flex align-items-center gap-2" 
                  [disabled]="isCancellingBooking"
                  (click)="confirmCancellation()"
                >
                  <span *ngIf="isCancellingBooking" class="spinner-border spinner-border-sm"></span>
                  <span>{{ isCancellingBooking ? 'Cancelling...' : 'Yes, Cancel Booking' }}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

    </div>
  `,
  styles: [`
    :host {
      --primary-blue: #0875E1;
      --dark-navy: #102A56;
      --main-bg: #F5F9FE;
      --card-bg: #FFFFFF;
      --text-secondary: #64748B;
      --border-subtle: #E2EAF4;
      --active-pill: #DDEEFF;
      display: block;
      background-color: var(--main-bg);
      color: var(--dark-navy);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    }

    .cursor-pointer { cursor: pointer; }
    .text-navy { color: var(--dark-navy) !important; }
    .smaller { font-size: 0.78rem; }
    .max-w-600 { max-width: 600px; }

    /* Top Header */
    .top-header {
      height: 72px;
      z-index: 1020;
    }
    .te-brand {
      display: flex;
      align-items: center;
      gap: 10px;
      text-decoration: none;
      flex-shrink: 0;
      cursor: pointer;
    }
    .te-brand-icon {
      width: 38px;
      height: 38px;
      background: #0d5bd9;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-size: 16px;
    }
    .te-brand-text {
      font-size: 21px;
      font-weight: 700;
      color: #0f172a;
      letter-spacing: -0.3px;
    }
    .te-brand-blue {
      color: #0d5bd9;
    }
    .header-search-box {
      width: 440px;
      max-width: 100%;
    }
    .search-input-pill {
      background-color: #F8FAFC;
      border: 1px solid var(--border-subtle);
      border-radius: 9999px;
      height: 42px;
    }
    .search-input {
      font-size: 0.88rem;
      color: var(--dark-navy);
    }
    .search-suggestions-dropdown {
      z-index: 1050;
      max-height: 320px;
      overflow-y: auto;
    }
    .suggestion-item:hover {
      background-color: #F1F5F9;
    }
    .notification-btn {
      width: 42px;
      height: 42px;
      background-color: #F8FAFC;
      border: 1px solid var(--border-subtle);
    }
    .notifications-dropdown {
      width: 340px;
      z-index: 1050;
    }
    .user-pill {
      background-color: #FFFFFF;
      border: 1px solid var(--border-subtle);
      padding: 4px 12px 4px 6px;
      transition: all 0.2s ease;
    }
    .user-pill:hover {
      background-color: #F8FAFC;
    }
    .user-avatar-circle {
      width: 34px;
      height: 34px;
      border-radius: 50%;
      background: #0875E1;
      color: white;
      font-weight: 600;
      font-size: 0.85rem;
    }
    .user-menu-dropdown {
      width: 220px;
      z-index: 1050;
    }

    /* Left Sidebar */
    .left-sidebar {
      width: 240px;
      min-width: 240px;
      border-color: var(--border-subtle) !important;
    }
    .sidebar-link {
      color: var(--text-secondary);
      font-weight: 500;
      font-size: 0.95rem;
      padding: 11px 18px;
      display: flex;
      align-items: center;
      transition: all 0.15s ease-in-out;
      cursor: pointer;
    }
    .sidebar-link:hover {
      background-color: #F1F5F9;
      color: var(--primary-blue);
    }
    .sidebar-link.active {
      background-color: var(--active-pill);
      color: var(--primary-blue);
      font-weight: 600;
    }
    .sidebar-link i {
      width: 20px;
      font-size: 1.1rem;
    }

    /* Greeting */
    .greeting-title {
      font-size: 1.85rem;
      letter-spacing: -0.5px;
    }
    .greeting-subtitle {
      font-size: 1rem;
      color: var(--text-secondary);
    }

    /* Hero Booking Cards */
    .hero-booking-card {
      height: 220px;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }
    .hero-booking-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 10px 25px rgba(8, 117, 225, 0.15);
    }
    .bus-hero-card {
      background: linear-gradient(90deg, rgba(16, 42, 86, 0.85) 0%, rgba(16, 42, 86, 0.45) 50%, rgba(0, 0, 0, 0.15) 100%),
                  url('https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=1200&q=80') center/cover no-repeat;
    }
    .hotel-hero-card {
      background: linear-gradient(90deg, rgba(16, 42, 86, 0.85) 0%, rgba(16, 42, 86, 0.45) 50%, rgba(0, 0, 0, 0.15) 100%),
                  url('https://images.unsplash.com/photo-1571896349842-33c89424de2d?auto=format&fit=crop&w=1200&q=80') center/cover no-repeat;
    }
    .hero-icon-pill {
      width: 44px;
      height: 44px;
      border-radius: 12px;
    }
    .bus-icon-pill {
      background-color: var(--primary-blue);
    }
    .hotel-icon-pill {
      background-color: rgba(255, 255, 255, 0.25);
      backdrop-filter: blur(8px);
    }
    .hero-arrow-btn {
      width: 44px;
      height: 44px;
    }

    /* Popular Destinations */
    .destination-card {
      border-color: var(--border-subtle) !important;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }
    .destination-card:hover {
      transform: translateY(-3px);
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08) !important;
    }
    .dest-thumb {
      height: 115px;
    }
    .btn-fav-circle {
      width: 28px;
      height: 28px;
      background: rgba(255, 255, 255, 0.9);
      backdrop-filter: blur(4px);
    }

    /* Bottom Panels */
    .icon-squircle {
      width: 42px;
      height: 42px;
      border-radius: 12px;
    }
    .bg-soft-blue { background-color: #EFF6FF; }
    .bg-soft-pink { background-color: #FFF1F2; }
    .booking-thumb {
      width: 80px;
      height: 56px;
    }
    .chevron-circle {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: #F8FAFC;
    }
    .badge-bus {
      background-color: #EEF2FF;
      color: #4F46E5;
      font-size: 0.75rem;
    }
    .badge-hotel {
      background-color: #F5F3FF;
      color: #7C3AED;
      font-size: 0.75rem;
    }
    .badge-confirmed {
      background-color: #DCFCE7;
      color: #15803D;
      border-radius: 9999px;
      padding: 6px 12px;
      font-weight: 600;
    }
    .badge-upcoming {
      background-color: #FEF3C7;
      color: #B45309;
      border-radius: 9999px;
      padding: 6px 12px;
      font-weight: 600;
    }
    .badge-cancelled {
      background-color: #FEE2E2;
      color: #B91C1C;
      border-radius: 9999px;
      padding: 6px 12px;
      font-weight: 600;
    }

    /* Destination Details View */
    .dest-details-hero {
      height: 260px;
    }
    .dest-hero-overlay {
      background: linear-gradient(90deg, rgba(16, 42, 86, 0.9) 0%, rgba(16, 42, 86, 0.6) 50%, rgba(0, 0, 0, 0.15) 100%);
    }
    .btn-fav-large {
      width: 46px;
      height: 46px;
    }
    .action-icon-squircle {
      width: 44px;
      height: 44px;
      border-radius: 12px;
    }
    .bg-indigo { background-color: #6366F1; }
    .plan-action-box {
      background-color: #F8FAFC;
      border-color: var(--border-subtle) !important;
      transition: all 0.2s ease;
    }
    .plan-action-box:hover {
      background-color: #EFF6FF;
      border-color: var(--primary-blue) !important;
    }

    /* Seat Picker */
    .bus-thumb {
      width: 72px;
      height: 52px;
    }
    .seat-box {
      width: 40px;
      height: 40px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      font-weight: 600;
      font-size: 0.82rem;
      border: 2px solid #CBD5E1;
      background-color: #FFFFFF;
      transition: all 0.15s;
    }
    .seat-box.available:hover {
      border-color: var(--primary-blue);
      background-color: var(--active-pill);
    }
    .seat-box.selected {
      background-color: #22C55E !important;
      color: white !important;
      border-color: #16A34A !important;
    }
    .seat-box.booked {
      background-color: #E2E8F0 !important;
      color: #94A3B8 !important;
      cursor: not-allowed !important;
    }
    .seat-legend {
      width: 16px;
      height: 16px;
      display: inline-block;
      border-radius: 4px;
    }
    .seat-legend.available { border: 2px solid #CBD5E1; background: white; }
    .seat-legend.selected { background: #22C55E; }
    .seat-legend.booked { background: #E2E8F0; }

    /* My Bookings Refined Styles */
    .tab-pill-btn {
      padding: 8px 22px;
      border-radius: 9999px;
      font-weight: 600;
      font-size: 0.9rem;
      border: none;
      background: #F1F5F9;
      color: #475569;
      transition: all 0.2s ease;
      cursor: pointer;
    }
    .tab-pill-btn:hover {
      background: #E2E8F0;
      color: #1E293B;
    }
    .tab-pill-btn.active {
      background: #0875E1;
      color: #FFFFFF;
      box-shadow: 0 4px 12px rgba(8, 117, 225, 0.25);
    }
    .booking-filter-bar {
      background: #FFFFFF;
      border: 1px solid var(--border-subtle);
      border-radius: 14px;
      padding: 10px 16px;
    }
    .booking-search-box {
      background-color: #F8FAFC;
      border: 1px solid var(--border-subtle);
      border-radius: 10px;
      height: 42px;
      flex: 1;
      min-width: 260px;
    }
    .date-filter-box {
      background-color: #F8FAFC;
      border: 1px solid var(--border-subtle);
      border-radius: 10px;
      height: 42px;
      padding: 0 14px;
      min-width: 145px;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .date-filter-box:hover, .date-filter-box:focus-within {
      background-color: #FFFFFF;
      border-color: #CBD5E1;
    }
    .date-input-control {
      font-size: 0.88rem;
      color: var(--dark-navy);
      cursor: pointer;
      width: 110px;
    }
    .date-input-control::placeholder {
      color: #64748B;
      opacity: 0.9;
    }
    .status-filter-select {
      background-color: #F8FAFC;
      border: 1px solid var(--border-subtle);
      border-radius: 10px;
      height: 42px;
      font-size: 0.88rem;
      min-width: 140px;
    }
    .booking-card-item {
      background: #FFFFFF;
      border: 1px solid var(--border-subtle);
      border-radius: 16px;
      padding: 18px 20px;
      transition: box-shadow 0.2s ease, border-color 0.2s ease;
    }
    .booking-card-item:hover {
      border-color: #CBD5E1;
      box-shadow: 0 6px 18px rgba(16, 42, 86, 0.05);
    }
    .booking-card-img {
      width: 145px;
      height: 100px;
      border-radius: 12px;
      object-fit: cover;
      flex-shrink: 0;
    }
    .badge-type-bus {
      background-color: #E0EDFF;
      color: #0875E1;
      font-weight: 600;
      font-size: 0.72rem;
      padding: 3px 10px;
      border-radius: 6px;
      display: inline-block;
    }
    .badge-type-hotel {
      background-color: #EDE9FE;
      color: #7C3AED;
      font-weight: 600;
      font-size: 0.72rem;
      padding: 3px 10px;
      border-radius: 6px;
      display: inline-block;
    }
    .badge-status-pill {
      border-radius: 9999px;
      padding: 4px 14px;
      font-size: 0.8rem;
      font-weight: 600;
      display: inline-block;
    }
    .badge-status-pill.badge-confirmed {
      background-color: #DCFCE7;
      color: #15803D;
    }
    .badge-status-pill.badge-upcoming {
      background-color: #FEF3C7;
      color: #B45309;
    }
    .badge-status-pill.badge-completed {
      background-color: #F1F5F9;
      color: #475569;
    }
    .badge-status-pill.badge-cancelled {
      background-color: #FEE2E2;
      color: #DC2626;
    }
    .booking-card-price {
      font-size: 1.35rem;
      font-weight: 700;
      color: var(--dark-navy);
      letter-spacing: -0.5px;
    }
    .btn-view-details {
      background-color: #F0F7FF;
      color: #0875E1;
      font-weight: 600;
      border: 1px solid #D6E8FE;
      border-radius: 8px;
      padding: 6px 16px;
      font-size: 0.85rem;
      transition: all 0.15s ease;
    }
    .btn-view-details:hover {
      background-color: #E0EDFF;
      color: #065CB5;
    }
    .btn-cancel-booking {
      background-color: #FFF1F2;
      color: #E11D48;
      font-weight: 600;
      border: 1px solid #FECDD3;
      border-radius: 8px;
      padding: 6px 16px;
      font-size: 0.85rem;
      transition: all 0.15s ease;
    }
    .btn-cancel-booking:hover {
      background-color: #FFE4E6;
      color: #BE123C;
    }
    .pagination-arrow-btn {
      width: 36px;
      height: 36px;
      border-radius: 8px;
      background: #FFFFFF;
      border: 1px solid var(--border-subtle);
      color: #64748B;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.15s ease;
      cursor: pointer;
    }
    .pagination-arrow-btn:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
    .pagination-arrow-btn:not(:disabled):hover {
      background: #F1F5F9;
      color: var(--dark-navy);
    }
    .pagination-page-btn {
      width: 36px;
      height: 36px;
      border-radius: 8px;
      background: #FFFFFF;
      border: 1px solid var(--border-subtle);
      color: #64748B;
      font-weight: 600;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.15s ease;
      cursor: pointer;
    }
    .pagination-page-btn.active {
      background: #0875E1;
      color: #FFFFFF;
      border-color: #0875E1;
    }
    .pagination-page-btn:not(.active):hover {
      background: #F1F5F9;
      color: var(--dark-navy);
    }
    .new-booking-dropdown {
      position: absolute;
      right: 0;
      top: 100%;
      margin-top: 6px;
      width: 180px;
      z-index: 1040;
    }
  `]
})
export class CustomerDashboardComponent implements OnInit {
  currentUser: User | null = null;
  activeTab = 'dashboard';

  // Global Header State
  searchQuery = '';
  showSearchSuggestions = false;
  showNotifications = false;
  showUserMenu = false;
  unreadNotificationsCount = 0;
  notificationList: any[] = [];

  // Bus Search State
  busOrigin = 'Vijayawada';
  busDestination = 'Hyderabad';
  busDate = new Date().toISOString().split('T')[0];
  busesSearched = false;
  busResults: any[] = [];
  selectedSchedule: any = null;
  seatList: any[] = [];
  selectedSeatNumbers: string[] = [];
  busSortBy = 'departureTime';

  // Hotel Search State
  hotelCity = 'Vijayawada';
  checkInDate = new Date().toISOString().split('T')[0];
  checkOutDate = new Date(Date.now() + 86400000).toISOString().split('T')[0];
  hotelsSearched = false;
  hotelResults: any[] = [];
  selectedHotel: any = null;
  hotelRoomTypes: any[] = [];
  hotelSortBy = 'popularity';

  // Checkout State
  showCheckoutModal = false;
  isProcessingCheckout = false;
  checkoutServiceType = '';
  checkoutServiceId = 0;
  checkoutRoomId = 0;
  checkoutProviderId = 0;
  checkoutServiceTitle = '';
  checkoutDetails = '';
  checkoutAmount = 0;
  paymentMethod = 'CARD';

  // My Bookings Advanced State
  myBookings: any[] = [];
  bookingCategoryTab: 'ALL' | 'BUS' | 'HOTEL' = 'ALL';
  bookingSearchQuery = '';
  bookingFromDate = '';
  bookingToDate = '';
  isFromDateFocused = false;
  isToDateFocused = false;
  bookingStatusFilter = 'ALL';
  bookingCurrentPage = 1;
  bookingPageSize = 5;
  showNewBookingMenu = false;
  selectedBookingDetails: any = null;
  showBookingDetailsModal = false;
  bookingToCancel: any = null;
  showCancelConfirmModal = false;
  isCancellingBooking = false;
  isLoadingBookings = false;
  bookingsError: string | null = null;

  // Review Modal State
  showReviewModal = false;
  reviewBooking: any = null;
  reviewRating = 5;
  reviewComment = '';

  // Traveller Service State
  dashboardData: any = null;
  profileData: any = null;
  profileModel: any = {};
  preferencesModel: any = {
    seatPreference: 'WINDOW',
    berthPreference: 'LOWER',
    mealPreference: 'VEG',
    preferredCurrency: 'INR',
    emailNotifications: true,
    smsNotifications: true
  };
  favouritesList: any[] = [];
  favouriteFilter = '';
  supportTickets: any[] = [];
  ticketModel: any = { category: 'BOOKING', subject: '', message: '' };

  // Destinations Catalog (Geographic Hub Exploration Guides)
  destinations: Destination[] = [
    {
      id: 'goa',
      name: 'Goa',
      tags: 'Beaches · Nightlife',
      category: 'Beaches · Nightlife · Adventure',
      description: "Goa is India's premier beach destination, known for its stunning coastline, vibrant nightlife, water sports and Portuguese heritage.",
      image: 'assets/images/goa.png',
      heroImage: 'https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?auto=format&fit=crop&w=1400&q=80',
      bestTime: 'November to February',
      travelOptions: 'Frequent overnight luxury sleeper buses from Mumbai, Pune, Bangalore & Hyderabad; daily flights to Dabolim and Mopa airports.',
      attractions: [
        { name: 'Baga Beach', category: 'Beach', image: 'https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?auto=format&fit=crop&w=600&q=80' },
        { name: 'Fort Aguada', category: 'Historical', image: 'https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=600&q=80' },
        { name: 'Calangute Beach', category: 'Beach', image: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=80' },
        { name: 'Dudhsagar Falls', category: 'Nature', image: 'https://images.unsplash.com/photo-1546768292-fb12f6c92568?auto=format&fit=crop&w=600&q=80' }
      ]
    },
    {
      id: 'ooty',
      name: 'Ooty',
      tags: 'Hills · Nature',
      category: 'Hills · Nature · Scenic',
      description: 'Nestled in the Nilgiri Hills, Ooty is the Queen of Hill Stations, famous for emerald tea estates, mist-covered valleys and colonial charm.',
      image: 'assets/images/ooty.png',
      heroImage: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=1400&q=80',
      bestTime: 'October to June',
      travelOptions: 'Overnight Volvo and sleeper buses from Bangalore, Chennai, and Coimbatore; scenic Nilgiri Mountain Railway.',
      attractions: [
        { name: 'Ooty Lake & Boathouse', category: 'Recreation', image: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=600&q=80' },
        { name: 'Doddabetta Peak', category: 'Viewpoint', image: 'https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=600&q=80' },
        { name: 'Botanical Gardens', category: 'Nature', image: 'https://images.unsplash.com/photo-1585320806297-9794b3e4eeae?auto=format&fit=crop&w=600&q=80' },
        { name: 'Pykara Lake & Falls', category: 'Lakes', image: 'https://images.unsplash.com/photo-1432405972618-c60b0225b8f9?auto=format&fit=crop&w=600&q=80' }
      ]
    },
    {
      id: 'munnar',
      name: 'Munnar',
      tags: 'Nature · Adventure',
      category: 'Nature · Adventure · Serenity',
      description: 'A breathtaking hill retreat in Kerala surrounded by rolling tea plantations, mist-clad peaks and wildlife sanctuaries.',
      image: 'assets/images/munnar.png',
      heroImage: 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=1400&q=80',
      bestTime: 'September to March',
      travelOptions: 'Direct AC buses from Kochi, Bangalore, Madurai and Coimbatore; scenic mountain highways.',
      attractions: [
        { name: 'Eravikulam National Park', category: 'Wildlife', image: 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=600&q=80' },
        { name: 'Mattupetty Dam', category: 'Sightseeing', image: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=80' },
        { name: 'Anamudi Peak', category: 'Trekking', image: 'https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=600&q=80' },
        { name: 'Tea Museum', category: 'Heritage', image: 'https://images.unsplash.com/photo-1585320806297-9794b3e4eeae?auto=format&fit=crop&w=600&q=80' }
      ]
    },
    {
      id: 'varanasi',
      name: 'Varanasi',
      tags: 'Spiritual · Culture',
      category: 'Spiritual · Culture · Heritage',
      description: "One of the world's oldest continually inhabited cities, renowned for its sacred Ghats along the Ganges and mesmerizing evening Ganga Aarti.",
      image: 'assets/images/varanasi.png',
      heroImage: 'https://images.unsplash.com/photo-1561361513-2d000a50f0dc?auto=format&fit=crop&w=1400&q=80',
      bestTime: 'November to February',
      travelOptions: 'Direct AC buses from Lucknow, Allahabad, Patna and Delhi; Lal Bahadur Shastri International Airport.',
      attractions: [
        { name: 'Dashashwamedh Ghat', category: 'Spiritual', image: 'https://images.unsplash.com/photo-1561361513-2d000a50f0dc?auto=format&fit=crop&w=600&q=80' },
        { name: 'Kashi Vishwanath Temple', category: 'Pilgrimage', image: 'https://images.unsplash.com/photo-1548013146-72479768bada?auto=format&fit=crop&w=600&q=80' },
        { name: 'Assi Ghat', category: 'Culture', image: 'https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?auto=format&fit=crop&w=600&q=80' },
        { name: 'Sarnath Buddhist Site', category: 'Historical', image: 'https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=600&q=80' }
      ]
    },
    {
      id: 'hyderabad',
      name: 'Hyderabad',
      tags: 'History · Food',
      category: 'History · Food · Architecture',
      description: 'The City of Pearls blends royal Nizami heritage, majestic fortresses, world-famous Hyderabadi Biryani and modern tech hubs.',
      image: 'assets/images/hyderabad.png',
      heroImage: 'https://images.unsplash.com/photo-1605807923707-c87a256a472c?auto=format&fit=crop&w=1400&q=80',
      bestTime: 'October to March',
      travelOptions: 'Extensive interstate bus connectivity from Vijayawada, Bangalore, Mumbai, Chennai, Pune and Tirupati.',
      attractions: [
        { name: 'Charminar', category: 'Historical', image: 'https://images.unsplash.com/photo-1605807923707-c87a256a472c?auto=format&fit=crop&w=600&q=80' },
        { name: 'Golconda Fort', category: 'Heritage', image: 'https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=600&q=80' },
        { name: 'Hussain Sagar Lake', category: 'Lakes', image: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=600&q=80' },
        { name: 'Chowmahalla Palace', category: 'Palace', image: 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=600&q=80' }
      ]
    },
    {
      id: 'alleppey',
      name: 'Alleppey',
      tags: 'Backwaters · Relaxation',
      category: 'Backwaters · Relaxation · Nature',
      description: 'Known as the Venice of the East, Alleppey is famous for traditional thatched houseboats gliding along serene palm-fringed canals.',
      image: 'https://images.unsplash.com/photo-1602216056096-3b40cc0c9944?auto=format&fit=crop&w=800&q=80',
      heroImage: 'https://images.unsplash.com/photo-1602216056096-3b40cc0c9944?auto=format&fit=crop&w=1400&q=80',
      bestTime: 'September to March',
      travelOptions: 'Regular luxury buses from Kochi, Trivandrum, Bangalore, and Chennai; peaceful backwater ferry terminals.',
      attractions: [
        { name: 'Vembanad Lake Houseboats', category: 'Backwaters', image: 'https://images.unsplash.com/photo-1602216056096-3b40cc0c9944?auto=format&fit=crop&w=600&q=80' },
        { name: 'Alappuzha Beach', category: 'Beach', image: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=600&q=80' },
        { name: 'Marari Beach', category: 'Relaxation', image: 'https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?auto=format&fit=crop&w=600&q=80' },
        { name: 'Kuttanad Paddy Fields', category: 'Scenic', image: 'https://images.unsplash.com/photo-1585320806297-9794b3e4eeae?auto=format&fit=crop&w=600&q=80' }
      ]
    }
  ];

  selectedDestination: Destination | null = null;
  selectedDestTab = 'Overview';

  // Real Display data for Dashboard preview widgets (initialized empty)
  displayBookings: any[] = [];
  displaySavedItems: any[] = [];

  constructor(
    private authService: AuthService,
    private busService: BusService,
    private hotelService: HotelService,
    private bookingService: BookingService,
    private travellerService: TravellerService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit() {
    this.currentUser = this.authService.currentUserValue;

    // Detect path from URL
    const url = this.router.url.split('?')[0].split('#')[0];
    if (url.includes('/destinations/')) {
      const slug = url.split('/destinations/')[1];
      this.selectDestinationBySlug(slug);
    } else if (url.endsWith('/buses')) {
      this.activeTab = 'bus';
    } else if (url.endsWith('/hotels')) {
      this.activeTab = 'hotel';
    } else if (url.endsWith('/bookings')) {
      this.activeTab = 'my-bookings';
    } else if (url.endsWith('/favourites')) {
      this.activeTab = 'favourites';
    } else if (url.endsWith('/profile')) {
      this.activeTab = 'profile';
    } else if (url.endsWith('/settings')) {
      this.activeTab = 'settings';
    } else if (url.endsWith('/support')) {
      this.activeTab = 'support';
    } else {
      this.activeTab = 'dashboard';
    }

    // Query parameters & fragments
    this.route.queryParams.subscribe(params => {
      if (params['tab']) {
        this.activeTab = params['tab'];
      }
      if (params['from']) {
        this.busOrigin = params['from'];
      }
      if (params['to']) {
        this.busDestination = params['to'];
      }
      if (params['date']) {
        this.busDate = params['date'];
      }
      if (params['city']) {
        this.hotelCity = params['city'];
      }
      if (params['checkIn']) {
        this.checkInDate = params['checkIn'];
      }
      if (params['checkOut']) {
        this.checkOutDate = params['checkOut'];
      }

      if (this.activeTab === 'hotel') {
        this.onSearchHotels();
      } else if (this.activeTab === 'bus') {
        this.onSearchBuses();
      } else if (this.activeTab === 'dashboard') {
        this.loadDashboardData();
        this.loadMyBookings();
        this.loadFavourites();
      }
    });

    this.route.fragment.subscribe(fragment => {
      if (fragment === 'hotels') {
        this.activeTab = 'hotel';
        if (!this.hotelsSearched) this.onSearchHotels();
      } else if (fragment === 'buses') {
        this.activeTab = 'bus';
        if (!this.busesSearched) this.onSearchBuses();
      }
    });

    // Load initial tab data
    this.loadTabData(this.activeTab);
  }

  getFirstName(): string {
    if (!this.currentUser || !this.currentUser.fullName) return 'Traveller';
    return this.currentUser.fullName.split(' ')[0];
  }

  getUserInitials(): string {
    if (!this.currentUser || !this.currentUser.fullName) return 'TR';
    const parts = this.currentUser.fullName.trim().split(' ');
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  onImageError(event: any, fallbackSrc: string) {
    if (event.target && fallbackSrc) {
      event.target.src = fallbackSrc;
    }
  }

  // --- TAB NAVIGATION ---
  switchTab(tab: string) {
    this.activeTab = tab;
    let targetPath = '/customer';
    if (tab === 'bus') targetPath = '/customer/buses';
    else if (tab === 'hotel') targetPath = '/customer/hotels';
    else if (tab === 'my-bookings') targetPath = '/customer/bookings';
    else if (tab === 'favourites') targetPath = '/customer/favourites';
    else if (tab === 'profile') targetPath = '/customer/profile';
    else if (tab === 'settings') targetPath = '/customer/settings';
    else if (tab === 'support') targetPath = '/customer/support';

    this.router.navigate([targetPath]);
    this.loadTabData(tab);
  }

  loadTabData(tab: string) {
    if (tab === 'dashboard') {
      this.loadDashboardData();
      this.loadMyBookings();
      this.loadFavourites();
    } else if (tab === 'hotel' && !this.hotelsSearched) {
      this.onSearchHotels();
    } else if (tab === 'bus' && !this.busesSearched) {
      this.onSearchBuses();
    } else if (tab === 'my-bookings') {
      this.loadMyBookings();
    } else if (tab === 'favourites') {
      this.loadFavourites();
    } else if (tab === 'profile') {
      this.loadProfile();
    } else if (tab === 'settings') {
      this.loadPreferences();
    } else if (tab === 'support') {
      this.loadSupportTickets();
    }
  }

  // --- HEADER SEARCH & SUGGESTIONS ---
  get filteredSuggestions(): any[] {
    if (!this.searchQuery || this.searchQuery.trim().length === 0) return [];
    const q = this.searchQuery.trim().toLowerCase();
    const suggestions: any[] = [];

    // Match destinations from catalog
    for (const d of this.destinations) {
      if (d.name.toLowerCase().includes(q) || d.tags.toLowerCase().includes(q)) {
        suggestions.push({
          type: 'DESTINATION',
          title: d.name,
          subtitle: d.tags,
          icon: 'fa-solid fa-location-dot',
          dest: d
        });
      }
    }

    // Dynamic actions for current user query
    suggestions.push({
      type: 'BUS',
      title: `Search buses to "${this.searchQuery.trim()}"`,
      subtitle: `Find active bus schedules`,
      icon: 'fa-solid fa-bus',
      to: this.searchQuery.trim()
    });

    suggestions.push({
      type: 'HOTEL',
      title: `Search hotels in "${this.searchQuery.trim()}"`,
      subtitle: `Find available hotel stays`,
      icon: 'fa-solid fa-hotel',
      city: this.searchQuery.trim()
    });

    return suggestions;
  }

  onSearchInput() {
    this.showSearchSuggestions = this.searchQuery.trim().length > 0;
  }

  clearSearch() {
    this.searchQuery = '';
    this.showSearchSuggestions = false;
  }

  selectSuggestion(item: any) {
    this.showSearchSuggestions = false;
    this.searchQuery = item.title;

    if (item.type === 'DESTINATION') {
      this.viewDestinationDetails(item.dest);
    } else if (item.type === 'BUS') {
      this.busDestination = item.to;
      this.switchTab('bus');
      this.onSearchBuses();
    } else if (item.type === 'HOTEL') {
      this.hotelCity = item.city;
      this.switchTab('hotel');
      this.onSearchHotels();
    }
  }

  executeGlobalSearch() {
    this.showSearchSuggestions = false;
    if (!this.searchQuery.trim()) return;
    const q = this.searchQuery.trim().toLowerCase();

    // Check if query matches a destination
    const match = this.destinations.find(d => d.name.toLowerCase() === q || d.id === q);
    if (match) {
      this.viewDestinationDetails(match);
      return;
    }

    // Default to searching buses with query
    this.busDestination = this.searchQuery.trim();
    this.hotelCity = this.searchQuery.trim();
    this.switchTab('bus');
    this.onSearchBuses();
  }

  toggleNotifications() {
    this.showNotifications = !this.showNotifications;
    this.showUserMenu = false;
  }

  markNotificationsRead() {
    this.unreadNotificationsCount = 0;
    this.notificationList.forEach(n => n.read = true);
  }

  toggleUserMenu() {
    this.showUserMenu = !this.showUserMenu;
    this.showNotifications = false;
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/home']);
  }

  // --- DESTINATION DETAILS WORKFLOW ---
  viewDestinationDetails(dest: Destination) {
    this.selectedDestination = dest;
    this.selectedDestTab = 'Overview';
    this.activeTab = 'destination-details';
    this.router.navigate(['/customer/destinations', dest.id]);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  selectDestinationBySlug(slug: string) {
    const dest = this.destinations.find(d => d.id === slug.toLowerCase());
    if (dest) {
      this.selectedDestination = dest;
      this.selectedDestTab = 'Overview';
      this.activeTab = 'destination-details';
    } else {
      this.activeTab = 'dashboard';
    }
  }

  findBusesToDestination(destName: string) {
    this.busDestination = destName;
    this.switchTab('bus');
    this.onSearchBuses();
  }

  findHotelsInDestination(destName: string) {
    this.hotelCity = destName;
    this.switchTab('hotel');
    this.onSearchHotels();
  }

  scrollOrFocusDestinations() {
    const el = document.querySelector('.popular-destinations-section');
    if (el) el.scrollIntoView({ behavior: 'smooth' });
  }

  isDestinationFavourited(name: string): boolean {
    return this.favouritesList.some(f => f.itemType === 'DESTINATION' && f.title === name);
  }

  toggleDestinationFavourite(event: Event, dest: Destination) {
    event.stopPropagation();
    const existing = this.favouritesList.find(f => f.itemType === 'DESTINATION' && f.title === dest.name);
    if (existing) {
      this.removeFavourite(existing.id);
    } else {
      this.travellerService.addFavourite({
        itemType: 'DESTINATION',
        itemId: 0,
        title: dest.name,
        subtitle: dest.tags,
        metadataJson: JSON.stringify({ description: dest.description, image: dest.image })
      }).subscribe({
        next: () => {
          this.loadFavourites();
        }
      });
    }
  }

  openSavedDestination(destName: string) {
    const dest = this.destinations.find(d => d.name.toLowerCase() === destName.toLowerCase());
    if (dest) {
      this.viewDestinationDetails(dest);
    } else {
      this.switchTab('dashboard');
    }
  }

  openSavedItem(item: any) {
    if (item.itemType === 'HOTEL') {
      this.hotelCity = item.subtitle || 'Hyderabad';
      this.switchTab('hotel');
      this.onSearchHotels();
    } else if (item.itemType === 'BUS' || item.itemType === 'ROUTE') {
      this.switchTab('bus');
      this.onSearchBuses();
    } else if (item.itemType === 'DESTINATION') {
      this.openSavedDestination(item.title);
    }
  }

  removeSavedItemFromPanel(event: Event, item: any) {
    event.stopPropagation();
    if (item.raw && item.raw.id) {
      this.removeFavourite(item.raw.id);
    } else {
      const idx = this.displaySavedItems.indexOf(item);
      if (idx >= 0) this.displaySavedItems.splice(idx, 1);
    }
  }

  // --- BUS WORKFLOWS ---
  swapBusCities() {
    const temp = this.busOrigin;
    this.busOrigin = this.busDestination;
    this.busDestination = temp;
  }

  onSearchBuses() {
    this.busService.searchBuses(this.busOrigin, this.busDestination, this.busDate).subscribe({
      next: (res) => {
        this.busResults = res || [];
        this.busesSearched = true;
        this.sortBuses();
      },
      error: () => {
        this.busResults = [];
        this.busesSearched = true;
      }
    });
  }

  sortBuses() {
    if (this.busSortBy === 'fareLow') {
      this.busResults.sort((a, b) => a.fare - b.fare);
    } else if (this.busSortBy === 'fareHigh') {
      this.busResults.sort((a, b) => b.fare - a.fare);
    } else {
      this.busResults.sort((a, b) => (a.departureTime || '').localeCompare(b.departureTime || ''));
    }
  }

  openBusSeatMap(schedule: any) {
    this.selectedSchedule = schedule;
    this.selectedSeatNumbers = [];
    this.busService.getScheduleDetails(schedule.id).subscribe({
      next: (res) => {
        this.seatList = res?.seats || [];
      },
      error: () => {
        this.seatList = [];
      }
    });
  }

  toggleSeat(seat: any) {
    if (seat.status !== 'AVAILABLE') return;
    const idx = this.selectedSeatNumbers.indexOf(seat.seatNumber);
    if (idx >= 0) {
      this.selectedSeatNumbers.splice(idx, 1);
    } else {
      this.selectedSeatNumbers.push(seat.seatNumber);
    }
  }

  proceedToBusCheckout(schedule: any) {
    this.checkoutServiceType = 'BUS';
    this.checkoutServiceId = schedule.id;
    this.checkoutProviderId = schedule.busId || 1;
    this.checkoutServiceTitle = `${schedule.operatorName} (${schedule.origin} to ${schedule.destination})`;
    this.checkoutDetails = `Seats: ${this.selectedSeatNumbers.join(', ')} | Journey Date: ${schedule.travelDate || this.busDate}`;
    this.checkoutAmount = this.selectedSeatNumbers.length * schedule.fare;
    this.showCheckoutModal = true;
  }

  // --- HOTEL WORKFLOWS ---
  onSearchHotels() {
    this.hotelService.searchHotels(this.hotelCity, this.checkInDate, this.checkOutDate).subscribe({
      next: (res) => {
        this.hotelResults = res || [];
        this.hotelsSearched = true;
        this.sortHotels();
      },
      error: () => {
        this.hotelResults = [];
        this.hotelsSearched = true;
      }
    });
  }

  sortHotels() {
    if (this.hotelSortBy === 'priceLow') {
      this.hotelResults.sort((a, b) => (a.pricePerNight || 0) - (b.pricePerNight || 0));
    } else if (this.hotelSortBy === 'priceHigh') {
      this.hotelResults.sort((a, b) => (b.pricePerNight || 0) - (a.pricePerNight || 0));
    } else if (this.hotelSortBy === 'rating') {
      this.hotelResults.sort((a, b) => (b.starRating || 0) - (a.starRating || 0));
    }
  }

  getAmenityPills(amenities: string): string[] {
    if (!amenities) return ['WiFi', 'AC'];
    return amenities.split(',').map(s => s.trim());
  }

  viewHotelRooms(hotel: any) {
    this.selectedHotel = hotel;
    this.hotelService.getHotelDetails(hotel.id).subscribe({
      next: (res) => {
        this.hotelRoomTypes = res?.rooms || [];
      },
      error: () => {
        this.hotelRoomTypes = [];
      }
    });
  }

  proceedToHotelCheckout(hotel: any, room: any) {
    this.checkoutServiceType = 'HOTEL';
    this.checkoutServiceId = hotel.id;
    this.checkoutRoomId = room.id;
    this.checkoutProviderId = hotel.managerId || 3;
    this.checkoutServiceTitle = `${hotel.name} (${room.roomTypeName})`;
    this.checkoutDetails = `Check-in: ${this.checkInDate} | Check-out: ${this.checkOutDate}`;
    this.checkoutAmount = room.pricePerNight;
    this.showCheckoutModal = true;
  }

  // --- COORDINATED CHECKOUT WITH RESERVATION & SAGA RECOVERY ---
  submitPayment() {
    if (!this.currentUser) return;
    this.isProcessingCheckout = true;

    if (this.checkoutServiceType === 'BUS') {
      // Step 1: Pre-booking inventory hold in BUS
      this.busService.bookSeats(this.checkoutServiceId, this.selectedSeatNumbers).subscribe({
        next: () => {
          this.executeBookingAndPayment();
        },
        error: (err) => {
          this.isProcessingCheckout = false;
          alert('Selected seat(s) could not be reserved or are no longer available. Please select another seat.');
          this.openBusSeatMap(this.selectedSchedule);
          this.showCheckoutModal = false;
        }
      });
    } else if (this.checkoutServiceType === 'HOTEL') {
      // Step 1: Pre-booking room inventory hold in HTL
      this.hotelService.reserveRooms(this.checkoutRoomId, 1).subscribe({
        next: () => {
          this.executeBookingAndPayment();
        },
        error: (err) => {
          this.isProcessingCheckout = false;
          alert('Selected room type could not be reserved or is no longer available. Please select another room.');
          this.showCheckoutModal = false;
        }
      });
    } else {
      this.executeBookingAndPayment();
    }
  }

  private executeBookingAndPayment() {
    const bookingPayload = {
      customerId: this.currentUser?.id,
      customerName: this.currentUser?.fullName,
      customerEmail: this.currentUser?.email,
      serviceType: this.checkoutServiceType,
      serviceId: this.checkoutServiceId,
      providerId: this.checkoutProviderId,
      serviceTitle: this.checkoutServiceTitle,
      totalAmount: this.checkoutAmount,
      detailsJson: this.checkoutDetails
    };

    // Step 2: Create Booking record in BKG
    this.bookingService.createBooking(bookingPayload).subscribe({
      next: (booking) => {
        // Step 3: Process Payment in PAY
        this.bookingService.processPayment({
          bookingRef: booking.bookingRef,
          customerId: this.currentUser?.id,
          amount: this.checkoutAmount,
          paymentMethod: this.paymentMethod
        }).subscribe({
          next: () => {
            // Step 4: Confirm Booking in BKG
            this.bookingService.confirmBooking(booking.id).subscribe({
              next: () => {
                this.isProcessingCheckout = false;
                alert(`Booking Confirmed Successfully! Reference: ${booking.bookingRef}`);
                this.showCheckoutModal = false;
                this.switchTab('my-bookings');
              },
              error: () => {
                this.compensateFailedCheckout(booking.id);
              }
            });
          },
          error: () => {
            this.compensateFailedCheckout(booking.id);
          }
        });
      },
      error: () => {
        this.compensateInventoryHoldOnly();
      }
    });
  }

  private compensateFailedCheckout(bookingId: number) {
    this.isProcessingCheckout = false;
    if (this.checkoutServiceType === 'BUS') {
      this.busService.releaseSeats(this.checkoutServiceId, this.selectedSeatNumbers).subscribe();
    } else if (this.checkoutServiceType === 'HOTEL') {
      this.hotelService.releaseRooms(this.checkoutRoomId, 1).subscribe();
    }
    this.bookingService.cancelBooking(bookingId).subscribe();
    alert('Payment processing failed. Your reserved seats/rooms have been released.');
    this.showCheckoutModal = false;
  }

  private compensateInventoryHoldOnly() {
    this.isProcessingCheckout = false;
    if (this.checkoutServiceType === 'BUS') {
      this.busService.releaseSeats(this.checkoutServiceId, this.selectedSeatNumbers).subscribe();
    } else if (this.checkoutServiceType === 'HOTEL') {
      this.hotelService.releaseRooms(this.checkoutRoomId, 1).subscribe();
    }
    alert('Failed to initiate booking reservation. Inventory hold was released.');
    this.showCheckoutModal = false;
  }

  // --- MY BOOKINGS ---
  loadMyBookings() {
    if (!this.currentUser) return;
    this.isLoadingBookings = true;
    this.bookingsError = null;
    this.bookingService.getCustomerBookings(this.currentUser.id).subscribe({
      next: (res) => {
        this.isLoadingBookings = false;
        this.myBookings = res || [];
        const confirmed = this.myBookings.filter((b: any) => b.status === 'CONFIRMED' || b.status === 'PENDING_PAYMENT');
        if (confirmed.length > 0) {
          this.displayBookings = confirmed.slice(0, 3).map((b: any) => ({
            serviceType: b.serviceType,
            title: b.serviceTitle,
            dateString: new Date(b.bookingDate).toLocaleDateString('en-US', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }),
            metaString: b.detailsJson,
            status: b.status,
            thumbnail: b.serviceType === 'BUS'
              ? 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=300&q=80'
              : 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=300&q=80'
          }));
        } else {
          this.displayBookings = [];
        }
      },
      error: () => {
        this.isLoadingBookings = false;
        this.bookingsError = 'Unable to fetch your bookings. Please check your connection and try again.';
        this.myBookings = [];
        this.displayBookings = [];
      }
    });
  }

  get allBookingsCount(): number {
    return this.myBookings.length;
  }

  get busBookingsCount(): number {
    return this.myBookings.filter(b => b.serviceType === 'BUS').length;
  }

  get hotelBookingsCount(): number {
    return this.myBookings.filter(b => b.serviceType === 'HOTEL').length;
  }

  setBookingCategoryTab(tab: 'ALL' | 'BUS' | 'HOTEL') {
    this.bookingCategoryTab = tab;
    this.bookingCurrentPage = 1;
  }

  toggleNewBookingMenu() {
    this.showNewBookingMenu = !this.showNewBookingMenu;
  }

  openDatePicker(input: HTMLInputElement) {
    if (!input) return;
    input.focus();
    if (typeof (input as any).showPicker === 'function') {
      try {
        (input as any).showPicker();
      } catch {
        // Fallback silently if browser restrictions apply
      }
    }
  }

  onFromDateChange() {
    if (this.bookingToDate && this.bookingFromDate > this.bookingToDate) {
      this.bookingToDate = this.bookingFromDate;
    }
    this.bookingCurrentPage = 1;
  }

  onToDateChange() {
    if (this.bookingFromDate && this.bookingToDate < this.bookingFromDate) {
      this.bookingFromDate = this.bookingToDate;
    }
    this.bookingCurrentPage = 1;
  }

  clearBookingFilters() {
    this.bookingSearchQuery = '';
    this.bookingFromDate = '';
    this.bookingToDate = '';
    this.bookingStatusFilter = 'ALL';
    this.bookingCurrentPage = 1;
  }

  hasActiveBookingFilters(): boolean {
    return (
      this.bookingSearchQuery.trim().length > 0 ||
      this.bookingFromDate.length > 0 ||
      this.bookingToDate.length > 0 ||
      this.bookingStatusFilter !== 'ALL'
    );
  }

  parseBooking(b: any) {
    if (!b) return null;
    const isBus = b.serviceType === 'BUS';
    let displayTitle = b.serviceTitle || (isBus ? 'Bus Booking' : 'Hotel Booking');
    let displaySubtitle = '';
    let parsedSeatsOrGuests = '';
    let parsedDate = '';
    let parsedTime = '';

    if (isBus) {
      const match = displayTitle.match(/^(.*?)\s*\((.*?)\s+to\s+(.*?)\)$/i);
      if (match) {
        const operator = match[1].trim();
        const origin = match[2].trim();
        const dest = match[3].trim();
        displayTitle = `${origin} → ${dest}`;
        displaySubtitle = `${operator} • AC Sleeper`;
      } else if (displayTitle.toLowerCase().includes(' to ')) {
        displayTitle = displayTitle.replace(/\s+to\s+/i, ' → ');
        displaySubtitle = 'AC Sleeper';
      } else {
        displaySubtitle = 'AC Sleeper';
      }
    } else {
      const match = displayTitle.match(/^(.*?)\s*\((.*?)\)$/);
      if (match) {
        const hotelName = match[1].trim();
        const roomType = match[2].trim();
        displayTitle = hotelName;
        displaySubtitle = `${roomType} • 1 Room`;
      } else {
        displaySubtitle = 'Deluxe Room • 1 Room';
      }
    }

    if (b.detailsJson) {
      if (isBus) {
        const seatMatch = b.detailsJson.match(/Seats:\s*([^|]+)/i);
        if (seatMatch) {
          const seats = seatMatch[1].trim().split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
          parsedSeatsOrGuests = seats.length > 1 ? `${seats.length} Seats` : `${seats.length || 1} Seat`;
        }
        const dateMatch = b.detailsJson.match(/Journey Date:\s*([^|]+)/i);
        if (dateMatch) {
          try {
            const d = new Date(dateMatch[1].trim());
            if (!isNaN(d.getTime())) {
              parsedDate = d.toLocaleDateString('en-US', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' });
            } else {
              parsedDate = dateMatch[1].trim();
            }
          } catch {
            parsedDate = dateMatch[1].trim();
          }
        }
        parsedTime = '08:00 PM';
      } else {
        const checkInMatch = b.detailsJson.match(/Check-in:\s*([^|]+)/i);
        const checkOutMatch = b.detailsJson.match(/Check-out:\s*([^|]+)/i);
        if (checkInMatch && checkOutMatch) {
          try {
            const inDate = new Date(checkInMatch[1].trim());
            const outDate = new Date(checkOutMatch[1].trim());
            if (!isNaN(inDate.getTime()) && !isNaN(outDate.getTime())) {
              const inStr = inDate.toLocaleDateString('en-US', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' });
              const outStr = outDate.toLocaleDateString('en-US', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' });
              parsedDate = `${inStr} — ${outStr}`;
            } else {
              parsedDate = `${checkInMatch[1].trim()} — ${checkOutMatch[1].trim()}`;
            }
          } catch {
            parsedDate = `${checkInMatch[1].trim()} — ${checkOutMatch[1].trim()}`;
          }
        }
        parsedSeatsOrGuests = '2 Guests';
      }
    }

    if (!parsedDate && b.bookingDate) {
      const d = new Date(b.bookingDate);
      parsedDate = !isNaN(d.getTime()) ? d.toLocaleDateString('en-US', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' }) : '';
    }

    const busThumbnails = [
      'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=400&q=80',
      'https://images.unsplash.com/photo-1570125909232-eb263c188f7e?auto=format&fit=crop&w=400&q=80'
    ];
    const hotelThumbnails = [
      'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=400&q=80',
      'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=400&q=80'
    ];
    const thumb = isBus ? busThumbnails[b.id % busThumbnails.length] : hotelThumbnails[b.id % hotelThumbnails.length];

    let statusClass = 'badge-confirmed';
    let statusDisplay = 'Confirmed';
    if (b.status === 'CONFIRMED') {
      statusClass = 'badge-confirmed';
      statusDisplay = 'Confirmed';
    } else if (b.status === 'UPCOMING' || b.status === 'PENDING_PAYMENT' || b.status === 'INITIATED') {
      statusClass = 'badge-upcoming';
      statusDisplay = b.status === 'PENDING_PAYMENT' ? 'Pending Payment' : 'Upcoming';
    } else if (b.status === 'COMPLETED') {
      statusClass = 'badge-completed';
      statusDisplay = 'Completed';
    } else if (b.status === 'CANCELLED') {
      statusClass = 'badge-cancelled';
      statusDisplay = 'Cancelled';
    }

    return {
      raw: b,
      id: b.id,
      bookingRef: b.bookingRef || `TEB${b.id}`,
      serviceType: b.serviceType,
      serviceTitle: b.serviceTitle,
      displayTitle,
      displaySubtitle,
      dateText: parsedDate,
      timeText: parsedTime,
      capacityText: parsedSeatsOrGuests || (isBus ? '1 Seat' : '1 Room'),
      thumbnail: thumb,
      totalAmount: b.totalAmount,
      status: b.status,
      statusBadgeClass: statusClass,
      statusDisplay,
      isCancellable: b.status === 'CONFIRMED' || b.status === 'PENDING_PAYMENT' || b.status === 'INITIATED',
      isCompleted: b.status === 'COMPLETED',
      isConfirmed: b.status === 'CONFIRMED',
      rawBookingDate: b.bookingDate,
      detailsJson: b.detailsJson
    };
  }

  private extractBookingDate(b: any): string | null {
    if (b.detailsJson) {
      const jMatch = b.detailsJson.match(/Journey Date:\s*([0-9]{4}-[0-9]{2}-[0-9]{2})/i);
      if (jMatch) return jMatch[1];
      const cMatch = b.detailsJson.match(/Check-in:\s*([0-9]{4}-[0-9]{2}-[0-9]{2})/i);
      if (cMatch) return cMatch[1];
    }
    if (b.bookingDate) {
      return b.bookingDate.split('T')[0];
    }
    return null;
  }

  get filteredBookings(): any[] {
    let list = this.myBookings;

    if (this.bookingCategoryTab === 'BUS') {
      list = list.filter(b => b.serviceType === 'BUS');
    } else if (this.bookingCategoryTab === 'HOTEL') {
      list = list.filter(b => b.serviceType === 'HOTEL');
    }

    if (this.bookingSearchQuery.trim()) {
      const q = this.bookingSearchQuery.trim().toLowerCase();
      list = list.filter(b => {
        const ref = (b.bookingRef || '').toLowerCase();
        const title = (b.serviceTitle || '').toLowerCase();
        const details = (b.detailsJson || '').toLowerCase();
        return ref.includes(q) || title.includes(q) || details.includes(q);
      });
    }

    if (this.bookingFromDate) {
      list = list.filter(b => {
        const d = this.extractBookingDate(b);
        return d ? d >= this.bookingFromDate : true;
      });
    }
    if (this.bookingToDate) {
      list = list.filter(b => {
        const d = this.extractBookingDate(b);
        return d ? d <= this.bookingToDate : true;
      });
    }

    if (this.bookingStatusFilter !== 'ALL') {
      if (this.bookingStatusFilter === 'UPCOMING') {
        list = list.filter(b => b.status === 'UPCOMING' || b.status === 'PENDING_PAYMENT' || b.status === 'INITIATED');
      } else {
        list = list.filter(b => b.status === this.bookingStatusFilter);
      }
    }

    return list.map(b => this.parseBooking(b));
  }

  get totalBookingPages(): number {
    return Math.max(1, Math.ceil(this.filteredBookings.length / this.bookingPageSize));
  }

  get pagedBookings(): any[] {
    const start = (this.bookingCurrentPage - 1) * this.bookingPageSize;
    return this.filteredBookings.slice(start, start + this.bookingPageSize);
  }

  get paginationStartItem(): number {
    if (this.filteredBookings.length === 0) return 0;
    return (this.bookingCurrentPage - 1) * this.bookingPageSize + 1;
  }

  get paginationEndItem(): number {
    return Math.min(this.bookingCurrentPage * this.bookingPageSize, this.filteredBookings.length);
  }

  get paginationPagesList(): number[] {
    const total = this.totalBookingPages;
    const pages: number[] = [];
    for (let i = 1; i <= total; i++) {
      pages.push(i);
    }
    return pages;
  }

  setBookingPage(page: number) {
    if (page >= 1 && page <= this.totalBookingPages) {
      this.bookingCurrentPage = page;
    }
  }

  prevBookingPage() {
    if (this.bookingCurrentPage > 1) {
      this.bookingCurrentPage--;
    }
  }

  nextBookingPage() {
    if (this.bookingCurrentPage < this.totalBookingPages) {
      this.bookingCurrentPage++;
    }
  }

  openBookingDetails(b: any) {
    this.selectedBookingDetails = b;
    this.showBookingDetailsModal = true;
  }

  closeBookingDetails() {
    this.showBookingDetailsModal = false;
    this.selectedBookingDetails = null;
  }

  promptCancelBooking(booking: any) {
    this.bookingToCancel = booking;
    this.showCancelConfirmModal = true;
  }

  closeCancelConfirmModal() {
    if (this.isCancellingBooking) return;
    this.showCancelConfirmModal = false;
    this.bookingToCancel = null;
  }

  confirmCancellation() {
    if (!this.bookingToCancel) return;
    this.isCancellingBooking = true;
    const bookingId = this.bookingToCancel.id;
    this.bookingService.cancelBooking(bookingId).subscribe({
      next: () => {
        this.isCancellingBooking = false;
        this.showCancelConfirmModal = false;
        if (this.showBookingDetailsModal && this.selectedBookingDetails?.id === bookingId) {
          this.showBookingDetailsModal = false;
        }
        this.bookingToCancel = null;
        alert('Booking cancelled successfully! Reserved inventory has been released.');
        this.loadMyBookings();
      },
      error: () => {
        this.isCancellingBooking = false;
        alert('Failed to cancel booking. Please try again or contact support.');
      }
    });
  }

  cancelMyBooking(bookingId: number) {
    const found = this.myBookings.find(b => b.id === bookingId);
    if (found) {
      this.promptCancelBooking(this.parseBooking(found));
    } else {
      this.bookingService.cancelBooking(bookingId).subscribe({
        next: () => {
          alert('Booking Cancelled Successfully!');
          this.loadMyBookings();
        }
      });
    }
  }

  openReviewModal(booking: any) {
    this.reviewBooking = booking;
    this.reviewRating = 5;
    this.reviewComment = '';
    this.showReviewModal = true;
  }

  submitReview() {
    if (!this.currentUser || !this.reviewBooking) return;

    const reviewPayload = {
      bookingRef: this.reviewBooking.bookingRef,
      customerId: this.currentUser.id,
      customerName: this.currentUser.fullName,
      serviceType: this.reviewBooking.serviceType,
      serviceId: this.reviewBooking.serviceId,
      rating: this.reviewRating,
      comment: this.reviewComment
    };

    this.bookingService.submitReview(reviewPayload).subscribe({
      next: () => {
        alert('Thank you! Your review has been submitted.');
        this.showReviewModal = false;
      }
    });
  }

  // --- TRAVELLER EXTENSIONS ---
  loadDashboardData() {
    this.travellerService.getDashboard().subscribe({
      next: (data) => {
        this.dashboardData = data;
      }
    });
  }

  loadProfile() {
    this.travellerService.getProfile().subscribe({
      next: (data) => {
        this.profileData = data;
        this.profileModel = { ...data };
      }
    });
  }

  saveProfile() {
    this.travellerService.updateProfile(this.profileModel).subscribe({
      next: (saved) => {
        this.profileData = saved;
        alert('Traveller profile updated successfully!');
      }
    });
  }

  loadPreferences() {
    this.travellerService.getPreferences().subscribe({
      next: (data) => {
        this.preferencesModel = { ...data };
      }
    });
  }

  savePreferences() {
    this.travellerService.updatePreferences(this.preferencesModel).subscribe({
      next: (saved) => {
        this.preferencesModel = { ...saved };
        alert('Preferences updated successfully!');
      }
    });
  }

  loadFavourites() {
    this.travellerService.getFavourites(this.favouriteFilter).subscribe({
      next: (data) => {
        this.favouritesList = data || [];
        if (this.favouritesList.length > 0) {
          this.displaySavedItems = this.favouritesList.slice(0, 2).map((f: any) => ({
            itemType: f.itemType,
            title: f.title,
            subtitle: f.subtitle,
            thumbnail: f.itemType === 'BUS'
              ? 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=400&q=80'
              : 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=400&q=80',
            raw: f
          }));
        } else {
          this.displaySavedItems = [];
        }
      },
      error: () => {
        this.favouritesList = [];
        this.displaySavedItems = [];
      }
    });
  }

  filterFavourites(type: string) {
    this.favouriteFilter = type;
    this.loadFavourites();
  }

  saveBusToFavourites(schedule: any) {
    this.travellerService.addFavourite({
      itemType: 'BUS',
      itemId: schedule.id,
      title: schedule.operatorName,
      subtitle: `${schedule.origin} to ${schedule.destination} (${schedule.busType})`,
      metadataJson: JSON.stringify({ fare: schedule.fare, travelDate: schedule.travelDate })
    }).subscribe({
      next: () => {
        alert('Bus operator saved to favourites!');
        this.loadFavourites();
      }
    });
  }

  saveHotelToFavourites(hotel: any) {
    this.travellerService.addFavourite({
      itemType: 'HOTEL',
      itemId: hotel.id,
      title: hotel.name,
      subtitle: `${hotel.address}, ${hotel.city}`,
      metadataJson: JSON.stringify({ starRating: hotel.starRating, amenities: hotel.amenities })
    }).subscribe({
      next: () => {
        alert('Hotel saved to favourites!');
        this.loadFavourites();
      }
    });
  }

  removeFavourite(id: number) {
    this.travellerService.deleteFavourite(id).subscribe({
      next: () => {
        this.loadFavourites();
      }
    });
  }

  loadSupportTickets() {
    this.travellerService.getSupportTickets().subscribe({
      next: (data) => {
        this.supportTickets = data || [];
      },
      error: () => {
        this.supportTickets = [];
      }
    });
  }

  createSupportTicket() {
    if (!this.ticketModel.subject || !this.ticketModel.message) return;

    this.travellerService.createSupportTicket(this.ticketModel).subscribe({
      next: () => {
        alert('Support ticket created successfully! Our team will respond shortly.');
        this.ticketModel = { category: 'BOOKING', subject: '', message: '' };
        this.loadSupportTickets();
      }
    });
  }
}
