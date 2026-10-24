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
import { Destination, DestinationAttraction } from '../../models/destination.model';
import { TripSeatLayout } from '../../models/bus.model';

import { TravellerHeaderComponent } from '../../shared/components/traveller-header/traveller-header.component';
import { TravellerSidebarComponent } from '../../shared/components/traveller-sidebar/traveller-sidebar.component';
import { BusSearchPanelComponent } from '../../features/traveller/bus-booking/components/bus-search-panel/bus-search-panel.component';
import { BusFilterPanelComponent } from '../../features/traveller/bus-booking/components/bus-filter-panel/bus-filter-panel.component';
import { BusResultsListComponent } from '../../features/traveller/bus-booking/components/bus-results-list/bus-results-list.component';
import { BusSeatPickerComponent } from '../../features/traveller/bus-booking/components/bus-seat-picker/bus-seat-picker.component';
import { DestinationDetailsComponent } from '../../features/traveller/destination-details/destination-details.component';
import { HotelSearchPanelComponent } from '../../features/traveller/hotel-booking/components/hotel-search-panel/hotel-search-panel.component';
import { HotelResultCardComponent } from '../../features/traveller/hotel-booking/components/hotel-result-card/hotel-result-card.component';
import { MyBookingsListComponent } from '../../features/traveller/bookings/components/my-bookings-list/my-bookings-list.component';
import { BookingDetailsModalComponent } from '../../features/traveller/bookings/components/booking-details-modal/booking-details-modal.component';

export { Destination, DestinationAttraction };

@Component({
  selector: 'app-customer-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    TravellerHeaderComponent,
    TravellerSidebarComponent,
    BusSearchPanelComponent,
    BusFilterPanelComponent,
    BusResultsListComponent,
    BusSeatPickerComponent,
    DestinationDetailsComponent,
    HotelSearchPanelComponent,
    HotelResultCardComponent,
    MyBookingsListComponent,
    BookingDetailsModalComponent
  ],
  template: `
    <div class="dashboard-wrapper d-flex flex-column min-vh-100">
      
      <!-- ==================== TOP HEADER ==================== -->
      <app-traveller-header
        [currentUser]="currentUser"
        [(searchQuery)]="searchQuery"
        [showSearchSuggestions]="showSearchSuggestions"
        [filteredSuggestions]="filteredSuggestions"
        [notificationList]="notificationList"
        [unreadNotificationsCount]="unreadNotificationsCount"
        [showNotifications]="showNotifications"
        [showUserMenu]="showUserMenu"
        (navigateTab)="switchTab($event)"
        (searchInput)="onSearchInput()"
        (searchSubmit)="executeGlobalSearch()"
        (selectSuggestion)="selectSuggestion($event)"
        (clearSearch)="clearSearch()"
        (toggleNotifications)="toggleNotifications()"
        (markNotificationsRead)="markNotificationsRead()"
        (toggleUserMenu)="toggleUserMenu()"
        (logout)="logout()"
      ></app-traveller-header>

      <!-- ==================== MAIN BODY: SIDEBAR + CONTENT ==================== -->
      <div class="dashboard-body d-flex flex-grow-1">
        
        <!-- ==================== LEFT SIDEBAR ==================== -->
        <app-traveller-sidebar
          [activeTab]="activeTab"
          [isCollapsed]="isSidebarCollapsed"
          (switchTab)="switchTab($event)"
          (toggleCollapse)="toggleSidebar()"
        ></app-traveller-sidebar>

        <!-- ==================== MAIN CONTENT AREA ==================== -->
        <main class="main-content flex-grow-1" [ngClass]="busViewMode === 'seat-selection' ? 'p-2 px-md-3 py-md-1' : 'p-4'" style="background-color: #F5F9FE;">

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
            <app-destination-details
              [destination]="selectedDestination"
              [isFavourited]="isDestinationFavourited(selectedDestination.name)"
              (back)="switchTab('dashboard')"
              (toggleFavourite)="toggleDestinationFavourite($event, selectedDestination)"
              (findBuses)="findBusesToDestination($event)"
              (findHotels)="findHotelsInDestination($event)"
            ></app-destination-details>
          </div>

          <!-- #################### TAB 1: BUS BOOKING #################### -->
          <div *ngIf="activeTab === 'bus'">
            
            <!-- Standard Search & Results View -->
            <div *ngIf="busViewMode === 'results'">
              <!-- Bus Search Scenic Banner -->
              <app-bus-search-panel
                [origin]="busOrigin"
                [destination]="busDestination"
                [journeyDate]="busDate"
                [isSearching]="isSearchingBuses"
                [searchError]="busSearchError"
                (originChange)="busOrigin = $event"
                (destinationChange)="busDestination = $event"
                (journeyDateChange)="busDate = $event"
                (swapCities)="swapBusCities()"
                (search)="onSearchBuses()"
              ></app-bus-search-panel>

              <!-- Loading Spinner State -->
              <div *ngIf="isSearchingBuses" class="text-center py-5">
                <div class="spinner-border text-primary mb-3" role="status"></div>
                <h6 class="fw-bold text-navy">Searching buses from {{ busOrigin }} to {{ busDestination }}...</h6>
              </div>

              <!-- Search Results State -->
              <div *ngIf="!isSearchingBuses && busesSearched">
                
                <!-- Route Empty State: No schedules found -->
                <div *ngIf="busResults.length === 0" class="empty-state-card bg-white rounded-4 border p-5 text-center shadow-sm">
                  <div class="empty-icon-wrap mx-auto mb-3">
                    <i class="fa-solid fa-bus text-primary fs-2"></i>
                  </div>
                  <h5 class="fw-bold text-navy mb-2">No buses found for this route and journey date</h5>
                  <p class="text-secondary small mb-4" style="max-width: 520px; margin: 0 auto;">
                    No scheduled buses were found from <strong>{{ busOrigin }}</strong> to <strong>{{ busDestination }}</strong> on {{ formattedBusDate }}. Try selecting an active route with scheduled departures:
                  </p>
                  <div class="d-flex flex-wrap justify-content-center gap-2">
                    <button class="btn btn-outline-primary btn-sm rounded-pill px-3 py-2 fw-semibold" (click)="searchQuickRoute('Bengaluru', 'Hyderabad')">
                      <i class="fa-solid fa-route me-1"></i> Bengaluru → Hyderabad
                    </button>
                    <button class="btn btn-outline-primary btn-sm rounded-pill px-3 py-2 fw-semibold" (click)="searchQuickRoute('Vijayawada', 'Hyderabad')">
                      <i class="fa-solid fa-route me-1"></i> Vijayawada → Hyderabad
                    </button>
                    <button class="btn btn-outline-primary btn-sm rounded-pill px-3 py-2 fw-semibold" (click)="searchQuickRoute('Hyderabad', 'Bengaluru')">
                      <i class="fa-solid fa-route me-1"></i> Hyderabad → Bengaluru
                    </button>
                  </div>
                </div>

                <!-- When Buses Found: Two-Column Layout -->
                <div *ngIf="busResults.length > 0" class="row g-4 align-items-start">
                  
                  <!-- ================= LEFT COLUMN: FILTERS PANEL ================= -->
                  <div class="col-xl-3 col-lg-4">
                    <app-bus-filter-panel
                      [filterBusTypeAC]="filterBusTypeAC"
                      [filterBusTypeNonAC]="filterBusTypeNonAC"
                      [filterSeatTypeSeater]="filterSeatTypeSeater"
                      [filterSeatTypeSleeper]="filterSeatTypeSleeper"
                      [filterSingleSeatOnly]="filterSingleSeatOnly"
                      [depBands]="depBands"
                      [arrBands]="arrBands"
                      [countBusTypeAC]="countBusTypeAC"
                      [countBusTypeNonAC]="countBusTypeNonAC"
                      [countSeatTypeSeater]="countSeatTypeSeater"
                      [countSeatTypeSleeper]="countSeatTypeSleeper"
                      [countDepBefore6am]="countDepBefore6am"
                      [countDep6to12]="countDep6to12"
                      [countDep12to18]="countDep12to18"
                      [countDepAfter6pm]="countDepAfter6pm"
                      [countArrBefore6am]="countArrBefore6am"
                      [countArr6to12]="countArr6to12"
                      [countArr12to18]="countArr12to18"
                      [countArrAfter6pm]="countArrAfter6pm"
                      [availablePickupPoints]="availablePickupPoints"
                      [displayedPickupPoints]="displayedPickupPoints"
                      [selectedPickupPoints]="selectedPickupPoints"
                      [showAllPickupPoints]="showAllPickupPoints"
                      [pickupSearchQuery]="pickupSearchQuery"
                      [availableDroppingPoints]="availableDroppingPoints"
                      [displayedDroppingPoints]="displayedDroppingPoints"
                      [selectedDroppingPoints]="selectedDroppingPoints"
                      [showAllDroppingPoints]="showAllDroppingPoints"
                      [droppingSearchQuery]="droppingSearchQuery"
                      [amenityDefinitions]="amenityDefinitions"
                      [selectedAmenities]="selectedAmenities"
                      [filterMinPrice]="filterMinPrice"
                      [filterMaxPrice]="filterMaxPrice"
                      [defaultMinPrice]="defaultMinPrice"
                      [defaultMaxPrice]="defaultMaxPrice"
                      [hasActiveFilters]="hasActiveBusFilters"
                      [filterSectionsOpen]="filterSectionsOpen"
                      [amenityCounts]="amenityCounts"
                      (filterChange)="onFilterStateChanged($event)"
                      (priceChange)="onFilterPriceChanged($event)"
                      (clearAll)="clearAllBusFilters()"
                      (togglePickupPoint)="togglePickupPoint($event)"
                      (toggleDroppingPoint)="toggleDroppingPoint($event)"
                      (toggleAmenity)="toggleAmenity($event)"
                      (toggleSection)="toggleFilterSection($event)"
                    ></app-bus-filter-panel>
                  </div>

                  <!-- ================= RIGHT COLUMN: BUS RESULTS ================= -->
                  <div class="col-xl-9 col-lg-8">
                    <app-bus-results-list
                      [buses]="pagedBusList"
                      [totalFilteredBuses]="totalFilteredBuses"
                      [origin]="busOrigin"
                      [destination]="busDestination"
                      [formattedDate]="formattedBusDate"
                      [sortBy]="busSortBy"
                      [currentPage]="busCurrentPage"
                      [pageSize]="busPageSize"
                      [selectedSchedule]="selectedSchedule"
                      [selectedSeatNumbers]="selectedSeatNumbers"
                      [selectedBoardingPoint]="selectedBoardingPoint"
                      [selectedDroppingPoint]="selectedDroppingPoint"
                      [savedBusIds]="savedBusIds"
                      [seatList]="seatList"
                      [isLoadingSeats]="isLoadingSeats"
                      (sortChange)="busSortBy = $event; applyFilters()"
                      (pageChange)="setBusPage($event)"
                      (clearFilters)="clearAllBusFilters()"
                      (toggleSeats)="openBusSeatMap($event)"
                      (saveToFavourites)="saveBusToFavourites($event)"
                      (seatToggled)="toggleSeatByNumber($event)"
                      (boardingPointChanged)="selectedBoardingPoint = $event"
                      (droppingPointChanged)="selectedDroppingPoint = $event"
                      (proceedToCheckout)="proceedToBusCheckout($event)"
                    ></app-bus-results-list>
                  </div>

                </div>
              </div>
            </div>

            <!-- ================= DEDICATED SEAT SELECTION PAGE (APPROVED MOCKUP) ================= -->
            <div *ngIf="busViewMode === 'seat-selection'" class="container-fluid px-2 px-md-3">
              <app-bus-seat-picker
                [schedule]="selectedSchedule"
                [layout]="currentTripLayout"
                [isLoading]="isLoadingSeats"
                [origin]="busOrigin"
                [destination]="busDestination"
                [travelDate]="busDate"
                [selectedSeats]="selectedSeatNumbers"
                [selectedBoardingPoint]="selectedBoardingPoint"
                [selectedDroppingPoint]="selectedDroppingPoint"
                [currentUser]="currentUser"
                (backToResults)="busViewMode = 'results'"
                (seatToggled)="toggleSeatByNumber($event)"
                (seatRemoved)="removeSeatByNumber($event)"
                (seatsReset)="resetSelectedSeats()"
                (boardingPointChanged)="selectedBoardingPoint = $event"
                (droppingPointChanged)="selectedDroppingPoint = $event"
                (proceedToPayment)="onSeatPickerProceedToPayment($event)"
                (bookingConfirmed)="onSeatPickerBookingConfirmed($event)"
                (viewMyBookings)="switchTab('my-bookings')"
              ></app-bus-seat-picker>
            </div>

          </div>

          <!-- #################### TAB 2: HOTEL BOOKING #################### -->
          <div *ngIf="activeTab === 'hotel'">
            <app-hotel-search-panel
              [city]="hotelCity"
              [checkIn]="checkInDate"
              [checkOut]="checkOutDate"
              (cityChange)="hotelCity = $event"
              (checkInChange)="checkInDate = $event"
              (checkOutChange)="checkOutDate = $event"
              (search)="onSearchHotels()"
            ></app-hotel-search-panel>

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
              <div *ngFor="let h of hotelResults">
                <app-hotel-result-card
                  [hotel]="h"
                  [isSelected]="selectedHotel?.id === h.id"
                  [rooms]="hotelRoomTypes"
                  [isSaved]="isHotelSaved(h.id)"
                  (viewRooms)="viewHotelRooms(h)"
                  (bookRoom)="proceedToHotelCheckout($event.hotel, $event.room)"
                  (saveToFavourites)="saveHotelToFavourites(h)"
                ></app-hotel-result-card>
              </div>
            </div>
          </div>

          <!-- #################### TAB 3: MY BOOKINGS #################### -->
          <div *ngIf="activeTab === 'my-bookings'">
            <app-my-bookings-list
              [categoryTab]="bookingCategoryTab"
              [allBookingsCount]="allBookingsCount"
              [busBookingsCount]="busBookingsCount"
              [hotelBookingsCount]="hotelBookingsCount"
              [searchQuery]="bookingSearchQuery"
              [fromDate]="bookingFromDate"
              [toDate]="bookingToDate"
              [statusFilter]="bookingStatusFilter"
              [hasActiveFilters]="hasActiveBookingFilters()"
              [isLoading]="isLoadingBookings"
              [errorMessage]="bookingsError"
              [totalBookingsCount]="myBookings.length"
              [totalFilteredCount]="filteredBookings.length"
              [bookings]="pagedBookings"
              [currentPage]="bookingCurrentPage"
              [totalPages]="totalBookingPages"
              [paginationStartItem]="paginationStartItem"
              [paginationEndItem]="paginationEndItem"
              [pageNumbers]="paginationPagesList"
              (categoryTabChange)="setBookingCategoryTab($event)"
              (searchQueryChange)="bookingSearchQuery = $event; bookingCurrentPage = 1"
              (fromDateChange)="bookingFromDate = $event; onFromDateChange()"
              (toDateChange)="bookingToDate = $event; onToDateChange()"
              (statusFilterChange)="bookingStatusFilter = $event; bookingCurrentPage = 1"
              (clearFilters)="clearBookingFilters()"
              (pageChange)="setBookingPage($event)"
              (openDetails)="openBookingDetails($event)"
              (cancelBooking)="promptCancelBooking($event)"
              (rateReview)="openReviewModal($event)"
              (switchTab)="switchTab($event)"
              (retryLoad)="loadMyBookings()"
              (refresh)="loadMyBookings()"
              (newBooking)="switchTab($event)"
            ></app-my-bookings-list>
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

      <!-- ==================== BOOKING DETAILS & CANCEL MODALS ==================== -->
      <app-booking-details-modal
        [showDetailsModal]="showBookingDetailsModal"
        [booking]="selectedBookingDetails"
        [showCancelModal]="showCancelConfirmModal"
        [bookingToCancel]="bookingToCancel"
        [isCancelling]="isCancellingBooking"
        (closeDetails)="closeBookingDetails()"
        (promptCancel)="promptCancelBooking($event)"
        (closeCancel)="closeCancelConfirmModal()"
        (confirmCancel)="confirmCancellation()"
        (rateReview)="closeBookingDetails(); openReviewModal($event)"
      ></app-booking-details-modal>

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

    .empty-state-card {
      border-color: var(--border-subtle) !important;
    }
    .empty-icon-wrap {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: #EFF6FF;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  `]
})
export class CustomerDashboardComponent implements OnInit {
  Math = Math;
  currentUser: User | null = null;
  activeTab = 'dashboard';

  // Navigation Sidebar State (Collapsed rail by default on desktop)
  isSidebarCollapsed = true;

  // Global Header State
  searchQuery = '';
  showSearchSuggestions = false;
  showNotifications = false;
  showUserMenu = false;
  unreadNotificationsCount = 0;
  notificationList: any[] = [];

  // Bus Search & Results State
  busOrigin = 'Bengaluru';
  busDestination = 'Hyderabad';
  busDate = new Date().toISOString().split('T')[0];
  busesSearched = false;
  isSearchingBuses = false;
  busSearchError: string | null = null;
  busResults: any[] = [];
  selectedSchedule: any = null;
  seatList: any[] = [];
  selectedSeatNumbers: string[] = [];
  isLoadingSeats = false;
  busViewMode: 'results' | 'seat-selection' = 'results';
  currentTripLayout: TripSeatLayout | null = null;
  selectedBoardingPoint = '';
  selectedDroppingPoint = '';
  busFleetMap: Map<number, any> = new Map();

  // Bus Filters State
  filterBusTypeAC = false;
  filterBusTypeNonAC = false;
  filterSeatTypeSeater = false;
  filterSeatTypeSleeper = false;
  filterSingleSeatOnly = false;
  depBands: { [key: string]: boolean } = {
    before6am: false,
    from6to12: false,
    from12to18: false,
    after6pm: false
  };
  arrBands: { [key: string]: boolean } = {
    before6am: false,
    from6to12: false,
    from12to18: false,
    after6pm: false
  };
  selectedPickupPoints: string[] = [];
  pickupSearchQuery = '';
  showAllPickupPoints = false;
  selectedDroppingPoints: string[] = [];
  droppingSearchQuery = '';
  showAllDroppingPoints = false;
  selectedAmenities: string[] = [];
  defaultMinPrice = 500;
  defaultMaxPrice = 3000;
  filterMinPrice = 500;
  filterMaxPrice = 3000;

  // Bus Sorting & Pagination
  busSortBy = 'depEarliest';
  busCurrentPage = 1;
  busPageSize = 6;

  // Amenity Definitions
  amenityDefinitions = [
    { name: 'Wi-Fi', icon: 'fa-solid fa-wifi' },
    { name: 'Charging Point', icon: 'fa-solid fa-plug' },
    { name: 'Blanket', icon: 'fa-solid fa-bed' },
    { name: 'Water Bottle', icon: 'fa-solid fa-bottle-water' },
    { name: 'Reading Light', icon: 'fa-solid fa-lightbulb' },
    { name: 'GPS Tracking', icon: 'fa-solid fa-location-crosshairs' }
  ];

  // Collapsible Filter Categories
  filterSectionsOpen: { [key: string]: boolean } = {
    busType: true,
    seatType: true,
    singleSeat: true,
    depTime: true,
    arrTime: true,
    boarding: true,
    dropping: true,
    amenities: true,
    price: true
  };

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

    // Cache bus fleet amenities from BUS microservice
    this.busService.getAllBuses().subscribe({
      next: (buses) => {
        if (Array.isArray(buses)) {
          buses.forEach(b => this.busFleetMap.set(b.id, b));
        }
      },
      error: () => {}
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

  // --- NAVIGATION RAIL TOGGLE ---
  toggleSidebar() {
    this.isSidebarCollapsed = !this.isSidebarCollapsed;
  }

  toggleFilterSection(section: string) {
    this.filterSectionsOpen[section] = !this.filterSectionsOpen[section];
  }

  // --- BUS WORKFLOWS ---
  onBannerImgError(event: any) {
    if (event.target) {
      event.target.style.display = 'none';
    }
  }

  onBusImgError(event: any) {
    if (event.target) {
      event.target.src = 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=400&q=80';
    }
  }

  get formattedBusDate(): string {
    if (!this.busDate) return '';
    try {
      const d = new Date(this.busDate);
      return d.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
    } catch {
      return this.busDate;
    }
  }

  swapBusCities() {
    const temp = this.busOrigin;
    this.busOrigin = this.busDestination;
    this.busDestination = temp;
  }

  clearBusOrigin() {
    this.busOrigin = '';
  }

  clearBusDestination() {
    this.busDestination = '';
  }

  searchQuickRoute(origin: string, dest: string) {
    this.busOrigin = origin;
    this.busDestination = dest;
    this.onSearchBuses();
  }

  onSearchBuses() {
    this.busSearchError = null;
    if (!this.busOrigin || !this.busOrigin.trim()) {
      this.busSearchError = 'Please enter an origin city.';
      return;
    }
    if (!this.busDestination || !this.busDestination.trim()) {
      this.busSearchError = 'Please enter a destination city.';
      return;
    }
    if (this.busOrigin.trim().toLowerCase() === this.busDestination.trim().toLowerCase()) {
      this.busSearchError = 'Origin and destination cities cannot be the same.';
      return;
    }
    if (!this.busDate) {
      this.busSearchError = 'Please select a valid journey date.';
      return;
    }

    this.isSearchingBuses = true;
    this.busViewMode = 'results';
    this.selectedSchedule = null;
    this.selectedSeatNumbers = [];

    this.busService.searchBuses(this.busOrigin.trim(), this.busDestination.trim(), this.busDate).subscribe({
      next: (res) => {
        this.busResults = res || [];
        this.busesSearched = true;
        this.isSearchingBuses = false;
        this.initPriceRange();
        this.busCurrentPage = 1;
      },
      error: () => {
        this.busResults = [];
        this.busesSearched = true;
        this.isSearchingBuses = false;
      }
    });
  }

  initPriceRange() {
    if (this.busResults.length > 0) {
      const fares = this.busResults.map(b => b.fare || 0);
      this.defaultMinPrice = Math.min(...fares);
      this.defaultMaxPrice = Math.max(...fares);
    } else {
      this.defaultMinPrice = 500;
      this.defaultMaxPrice = 3000;
    }
    this.filterMinPrice = this.defaultMinPrice;
    this.filterMaxPrice = this.defaultMaxPrice;
  }

  applyFilters() {
    this.busCurrentPage = 1;
  }

  clearAllBusFilters() {
    this.filterBusTypeAC = false;
    this.filterBusTypeNonAC = false;
    this.filterSeatTypeSeater = false;
    this.filterSeatTypeSleeper = false;
    this.filterSingleSeatOnly = false;
    this.depBands = { before6am: false, from6to12: false, from12to18: false, after6pm: false };
    this.arrBands = { before6am: false, from6to12: false, from12to18: false, after6pm: false };
    this.selectedPickupPoints = [];
    this.selectedDroppingPoints = [];
    this.selectedAmenities = [];
    this.pickupSearchQuery = '';
    this.droppingSearchQuery = '';
    this.filterMinPrice = this.defaultMinPrice;
    this.filterMaxPrice = this.defaultMaxPrice;
    this.busCurrentPage = 1;
  }

  togglePickupPoint(point: string) {
    const idx = this.selectedPickupPoints.indexOf(point);
    if (idx >= 0) {
      this.selectedPickupPoints.splice(idx, 1);
    } else {
      this.selectedPickupPoints.push(point);
    }
    this.applyFilters();
  }

  toggleDroppingPoint(point: string) {
    const idx = this.selectedDroppingPoints.indexOf(point);
    if (idx >= 0) {
      this.selectedDroppingPoints.splice(idx, 1);
    } else {
      this.selectedDroppingPoints.push(point);
    }
    this.applyFilters();
  }

  toggleAmenity(amenity: string) {
    const idx = this.selectedAmenities.indexOf(amenity);
    if (idx >= 0) {
      this.selectedAmenities.splice(idx, 1);
    } else {
      this.selectedAmenities.push(amenity);
    }
    this.applyFilters();
  }

  isBusAC(b: any): boolean {
    if (!b.busType) return false;
    return /AC/i.test(b.busType) && !/Non-AC/i.test(b.busType);
  }

  isBusNonAC(b: any): boolean {
    if (!b.busType) return false;
    return /Non-AC/i.test(b.busType);
  }

  isBusSeater(b: any): boolean {
    if (!b.busType) return false;
    return /Seater/i.test(b.busType);
  }

  isBusSleeper(b: any): boolean {
    if (!b.busType) return false;
    return /Sleeper/i.test(b.busType);
  }

  getBusAmenities(b: any): string[] {
    const fleet = this.busFleetMap.get(b.busId);
    if (fleet && fleet.amenities) {
      return fleet.amenities.split(',').map((s: string) => s.trim());
    }
    const isAC = this.isBusAC(b);
    const isSleeper = this.isBusSleeper(b);
    const amenities = ['Charging Point', 'Water Bottle'];
    if (isAC) {
      amenities.push('Wi-Fi', 'GPS Tracking');
    }
    if (isSleeper) {
      amenities.push('Blanket', 'Reading Light');
    }
    return amenities;
  }

  getAmenityIcon(amenityName: string): string {
    const found = this.amenityDefinitions.find(a => a.name.toLowerCase() === amenityName.toLowerCase());
    return found ? found.icon : 'fa-solid fa-circle-check';
  }

  getAmenityCount(amenityName: string): number {
    return this.busResults.filter(b => this.getBusAmenities(b).includes(amenityName)).length;
  }

  getBusThumbnail(b: any): string {
    if (b.thumbnail) return b.thumbnail;
    const busPhotos = [
      'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=400&q=80',
      'https://images.unsplash.com/photo-1570125909232-eb263c188f7e?auto=format&fit=crop&w=400&q=80',
      'https://images.unsplash.com/photo-1598971861713-54ad16a7e72e?auto=format&fit=crop&w=400&q=80'
    ];
    return busPhotos[(b.id || 0) % busPhotos.length];
  }

  getBusRating(b: any): { rating: number; reviews: number } {
    const base = 4.2 + ((b.id * 7) % 7) * 0.1;
    const rating = Math.min(4.9, Math.round(base * 10) / 10);
    const reviews = 45 + ((b.id * 19) % 210);
    return { rating, reviews };
  }

  timeToMinutes(timeStr: string): number {
    if (!timeStr) return 0;
    const parts = timeStr.trim().split(':');
    const h = parseInt(parts[0], 10) || 0;
    const m = parseInt(parts[1], 10) || 0;
    return h * 60 + m;
  }

  getJourneyDuration(b: any): { durationText: string; isNextDay: boolean } {
    if (!b.departureTime || !b.arrivalTime) {
      return { durationText: 'Direct', isNextDay: false };
    }
    const depMins = this.timeToMinutes(b.departureTime);
    const arrMins = this.timeToMinutes(b.arrivalTime);
    let diff = arrMins - depMins;
    let isNextDay = false;
    if (diff < 0) {
      diff += 24 * 60;
      isNextDay = true;
    }
    const hours = Math.floor(diff / 60);
    const mins = diff % 60;
    const durationText = mins > 0 ? `${hours}h ${mins}m` : `${hours}h`;
    return { durationText, isNextDay };
  }

  getDurationMinutes(b: any): number {
    if (!b.departureTime || !b.arrivalTime) return 0;
    const depMins = this.timeToMinutes(b.departureTime);
    const arrMins = this.timeToMinutes(b.arrivalTime);
    let diff = arrMins - depMins;
    if (diff < 0) diff += 24 * 60;
    return diff;
  }

  formatTime12(timeStr: string): string {
    if (!timeStr) return '';
    const parts = timeStr.trim().split(':');
    let h = parseInt(parts[0], 10) || 0;
    const m = parts[1] || '00';
    const ampm = h >= 12 ? 'PM' : 'AM';
    h = h % 12;
    h = h ? h : 12;
    const hStr = h < 10 ? '0' + h : '' + h;
    return `${hStr}:${m} ${ampm}`;
  }

  extractPointNames(pointsStr: string): string[] {
    if (!pointsStr) return [];
    return pointsStr.split(',').map(s => s.replace(/\s*\([^)]*\)/g, '').trim()).filter(Boolean);
  }

  getFirstPoint(pointsStr: string): { first: string; countRemaining: number } {
    const pts = this.extractPointNames(pointsStr);
    if (pts.length === 0) return { first: 'City Center', countRemaining: 0 };
    return {
      first: pts[0],
      countRemaining: Math.max(0, pts.length - 1)
    };
  }

  isBusSaved(scheduleId: number): boolean {
    return this.favouritesList.some(f => f.itemType === 'BUS' && f.itemId === scheduleId);
  }

  isHotelSaved(id: number): boolean {
    return this.favouritesList.some(f => f.itemType === 'HOTEL' && f.itemId === id);
  }

  get savedBusIds(): number[] {
    return this.favouritesList
      .filter(f => f.itemType === 'BUS' || f.itemType === 'ROUTE')
      .map(f => f.itemId);
  }

  onFilterStateChanged(state: any) {
    if (state.filterBusTypeAC !== undefined) this.filterBusTypeAC = state.filterBusTypeAC;
    if (state.filterBusTypeNonAC !== undefined) this.filterBusTypeNonAC = state.filterBusTypeNonAC;
    if (state.filterSeatTypeSeater !== undefined) this.filterSeatTypeSeater = state.filterSeatTypeSeater;
    if (state.filterSeatTypeSleeper !== undefined) this.filterSeatTypeSleeper = state.filterSeatTypeSleeper;
    if (state.filterSingleSeatOnly !== undefined) this.filterSingleSeatOnly = state.filterSingleSeatOnly;
    if (state.depBands) this.depBands = state.depBands;
    if (state.arrBands) this.arrBands = state.arrBands;
    if (state.pickupSearchQuery !== undefined) this.pickupSearchQuery = state.pickupSearchQuery;
    if (state.droppingSearchQuery !== undefined) this.droppingSearchQuery = state.droppingSearchQuery;
    this.applyFilters();
  }

  onFilterPriceChanged(maxPrice: number) {
    this.filterMaxPrice = maxPrice;
    this.applyFilters();
  }

  toggleSeatByNumber(seatNumber: string) {
    const idx = this.selectedSeatNumbers.indexOf(seatNumber);
    if (idx >= 0) {
      this.selectedSeatNumbers.splice(idx, 1);
    } else {
      this.selectedSeatNumbers.push(seatNumber);
    }
  }

  removeSeatByNumber(seatNumber: string) {
    this.selectedSeatNumbers = this.selectedSeatNumbers.filter(s => s !== seatNumber);
  }

  resetSelectedSeats() {
    this.selectedSeatNumbers = [];
  }

  get amenityCounts(): Record<string, number> {
    const counts: Record<string, number> = {};
    for (const b of this.busResults) {
      if (Array.isArray(b.amenities)) {
        for (const a of b.amenities) counts[a] = (counts[a] || 0) + 1;
      }
    }
    return counts;
  }

  get availablePickupPoints(): { name: string; count: number }[] {
    const map = new Map<string, number>();
    for (const b of this.busResults) {
      const pts = this.extractPointNames(b.boardingPoints);
      for (const p of pts) {
        map.set(p, (map.get(p) || 0) + 1);
      }
    }
    return Array.from(map.entries())
      .map(([name, count]) => ({ name, count }))
      .sort((a, b) => b.count - a.count);
  }

  get displayedPickupPoints(): { name: string; count: number }[] {
    let list = this.availablePickupPoints;
    if (this.pickupSearchQuery.trim()) {
      const q = this.pickupSearchQuery.trim().toLowerCase();
      list = list.filter(p => p.name.toLowerCase().includes(q));
    }
    return this.showAllPickupPoints ? list : list.slice(0, 5);
  }

  get availableDroppingPoints(): { name: string; count: number }[] {
    const map = new Map<string, number>();
    for (const b of this.busResults) {
      const pts = this.extractPointNames(b.droppingPoints);
      for (const p of pts) {
        map.set(p, (map.get(p) || 0) + 1);
      }
    }
    return Array.from(map.entries())
      .map(([name, count]) => ({ name, count }))
      .sort((a, b) => b.count - a.count);
  }

  get displayedDroppingPoints(): { name: string; count: number }[] {
    let list = this.availableDroppingPoints;
    if (this.droppingSearchQuery.trim()) {
      const q = this.droppingSearchQuery.trim().toLowerCase();
      list = list.filter(p => p.name.toLowerCase().includes(q));
    }
    return this.showAllDroppingPoints ? list : list.slice(0, 5);
  }

  get countBusTypeAC(): number {
    return this.busResults.filter(b => this.isBusAC(b)).length;
  }

  get countBusTypeNonAC(): number {
    return this.busResults.filter(b => this.isBusNonAC(b)).length;
  }

  get countSeatTypeSeater(): number {
    return this.busResults.filter(b => this.isBusSeater(b)).length;
  }

  get countSeatTypeSleeper(): number {
    return this.busResults.filter(b => this.isBusSleeper(b)).length;
  }

  get countDepBefore6am(): number {
    return this.busResults.filter(b => this.timeToMinutes(b.departureTime) < 360).length;
  }

  get countDep6to12(): number {
    return this.busResults.filter(b => {
      const m = this.timeToMinutes(b.departureTime);
      return m >= 360 && m < 720;
    }).length;
  }

  get countDep12to18(): number {
    return this.busResults.filter(b => {
      const m = this.timeToMinutes(b.departureTime);
      return m >= 720 && m < 1080;
    }).length;
  }

  get countDepAfter6pm(): number {
    return this.busResults.filter(b => this.timeToMinutes(b.departureTime) >= 1080).length;
  }

  get countArrBefore6am(): number {
    return this.busResults.filter(b => this.timeToMinutes(b.arrivalTime) < 360).length;
  }

  get countArr6to12(): number {
    return this.busResults.filter(b => {
      const m = this.timeToMinutes(b.arrivalTime);
      return m >= 360 && m < 720;
    }).length;
  }

  get countArr12to18(): number {
    return this.busResults.filter(b => {
      const m = this.timeToMinutes(b.arrivalTime);
      return m >= 720 && m < 1080;
    }).length;
  }

  get countArrAfter6pm(): number {
    return this.busResults.filter(b => this.timeToMinutes(b.arrivalTime) >= 1080).length;
  }

  get hasActiveBusFilters(): boolean {
    return this.filterBusTypeAC ||
      this.filterBusTypeNonAC ||
      this.filterSeatTypeSeater ||
      this.filterSeatTypeSleeper ||
      this.filterSingleSeatOnly ||
      Object.values(this.depBands).some(v => v) ||
      Object.values(this.arrBands).some(v => v) ||
      this.selectedPickupPoints.length > 0 ||
      this.selectedDroppingPoints.length > 0 ||
      this.selectedAmenities.length > 0 ||
      this.filterMinPrice > this.defaultMinPrice ||
      this.filterMaxPrice < this.defaultMaxPrice;
  }

  get filteredBusList(): any[] {
    if (!this.busResults || this.busResults.length === 0) return [];

    let list = this.busResults.filter(b => {
      // 1. Bus Type
      if (this.filterBusTypeAC && !this.filterBusTypeNonAC) {
        if (!this.isBusAC(b)) return false;
      } else if (this.filterBusTypeNonAC && !this.filterBusTypeAC) {
        if (!this.isBusNonAC(b)) return false;
      }

      // 2. Seat Type
      if (this.filterSeatTypeSeater && !this.filterSeatTypeSleeper) {
        if (!this.isBusSeater(b)) return false;
      } else if (this.filterSeatTypeSleeper && !this.filterSeatTypeSeater) {
        if (!this.isBusSleeper(b)) return false;
      }

      // 3. Single Seat Only
      if (this.filterSingleSeatOnly) {
        const isSingle = (b.busType && (b.busType.includes('2+1') || b.busType.includes('Single'))) || (b.availableSeats && b.availableSeats > 0);
        if (!isSingle) return false;
      }

      // 4. Departure Time
      const anyDepSelected = Object.values(this.depBands).some(v => v);
      if (anyDepSelected) {
        const depMins = this.timeToMinutes(b.departureTime);
        const matches = 
          (this.depBands.before6am && depMins < 360) ||
          (this.depBands.from6to12 && depMins >= 360 && depMins < 720) ||
          (this.depBands.from12to18 && depMins >= 720 && depMins < 1080) ||
          (this.depBands.after6pm && depMins >= 1080);
        if (!matches) return false;
      }

      // 5. Arrival Time
      const anyArrSelected = Object.values(this.arrBands).some(v => v);
      if (anyArrSelected) {
        const arrMins = this.timeToMinutes(b.arrivalTime);
        const matches = 
          (this.arrBands.before6am && arrMins < 360) ||
          (this.arrBands.from6to12 && arrMins >= 360 && arrMins < 720) ||
          (this.arrBands.from12to18 && arrMins >= 720 && arrMins < 1080) ||
          (this.arrBands.after6pm && arrMins >= 1080);
        if (!matches) return false;
      }

      // 6. Pickup Points
      if (this.selectedPickupPoints.length > 0) {
        const busPoints = this.extractPointNames(b.boardingPoints);
        const hasPoint = this.selectedPickupPoints.some(p => busPoints.includes(p));
        if (!hasPoint) return false;
      }

      // 7. Dropping Points
      if (this.selectedDroppingPoints.length > 0) {
        const busPoints = this.extractPointNames(b.droppingPoints);
        const hasPoint = this.selectedDroppingPoints.some(p => busPoints.includes(p));
        if (!hasPoint) return false;
      }

      // 8. Amenities
      if (this.selectedAmenities.length > 0) {
        const busAmen = this.getBusAmenities(b);
        const hasAll = this.selectedAmenities.every(a => busAmen.includes(a));
        if (!hasAll) return false;
      }

      // 9. Price Range
      if (b.fare < this.filterMinPrice || b.fare > this.filterMaxPrice) {
        return false;
      }

      return true;
    });

    // Sorting
    list = [...list].sort((a, b) => {
      if (this.busSortBy === 'depLatest') {
        return (b.departureTime || '').localeCompare(a.departureTime || '');
      } else if (this.busSortBy === 'fareLow') {
        return (a.fare || 0) - (b.fare || 0);
      } else if (this.busSortBy === 'fareHigh') {
        return (b.fare || 0) - (a.fare || 0);
      } else if (this.busSortBy === 'durationShort') {
        const aDur = this.getDurationMinutes(a);
        const bDur = this.getDurationMinutes(b);
        return aDur - bDur;
      } else {
        // depEarliest
        return (a.departureTime || '').localeCompare(b.departureTime || '');
      }
    });

    return list;
  }

  get totalFilteredBuses(): number {
    return this.filteredBusList.length;
  }

  get totalBusPages(): number {
    return Math.ceil(this.totalFilteredBuses / this.busPageSize) || 1;
  }

  get pagedBusList(): any[] {
    const start = (this.busCurrentPage - 1) * this.busPageSize;
    return this.filteredBusList.slice(start, start + this.busPageSize);
  }

  getPaginationPages(): number[] {
    const pages: number[] = [];
    for (let i = 1; i <= this.totalBusPages; i++) {
      pages.push(i);
    }
    return pages;
  }

  setBusPage(page: number) {
    if (page >= 1 && page <= this.totalBusPages) {
      this.busCurrentPage = page;
    }
  }

  sortBuses() {
    this.applyFilters();
  }

  openBusSeatMap(schedule: any) {
    console.log('[DEBUG-SEAT] openBusSeatMap called for schedule:', schedule?.id, schedule?.operatorName);
    this.selectedSchedule = schedule;
    this.selectedSeatNumbers = [];
    this.isLoadingSeats = true;
    this.busViewMode = 'seat-selection';
    this.busService.getScheduleLayout(schedule.id).subscribe({
      next: (layout) => {
        console.log('[DEBUG-SEAT] layout loaded successfully:', layout?.templateName, layout?.decks?.length);
        this.currentTripLayout = layout;
        this.isLoadingSeats = false;
        if (layout?.boardingPoints) {
          const bPoints = layout.boardingPoints.split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
          if (bPoints.length > 0) this.selectedBoardingPoint = bPoints[0];
        }
        if (layout?.droppingPoints) {
          const dPoints = layout.droppingPoints.split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
          if (dPoints.length > 0) this.selectedDroppingPoint = dPoints[0];
        }
      },
      error: (err) => {
        console.error('[DEBUG-SEAT] layout load error:', err);
        this.currentTripLayout = null;
        this.isLoadingSeats = false;
      }
    });
  }

  onSeatPickerBookingConfirmed(event: any) {
    this.loadMyBookings();
  }

  onSeatPickerProceedToPayment(payload: any) {
    this.checkoutServiceType = 'BUS';
    this.checkoutServiceId = payload.schedule.id;
    this.checkoutProviderId = payload.schedule.busId || 1;
    this.checkoutServiceTitle = `${payload.schedule.operatorName} (${payload.schedule.origin || this.busOrigin} to ${payload.schedule.destination || this.busDestination})`;

    const passList = (payload.passengers || []).map((p: any) => `${p.name} (Seat ${p.seatNumber}, Age: ${p.age}, ${p.gender})`).join('; ');
    this.checkoutDetails = `Seats: ${payload.selectedSeats.join(', ')} | Boarding: ${payload.boardingPoint} | Dropping: ${payload.droppingPoint} | Passengers: ${passList} | Date: ${payload.schedule.travelDate || this.busDate}`;

    this.checkoutAmount = payload.totalAmount;
    this.showCheckoutModal = true;
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
      next: (res: any) => {
        this.isCancellingBooking = false;
        this.showCancelConfirmModal = false;
        if (this.showBookingDetailsModal && this.selectedBookingDetails?.id === bookingId) {
          this.showBookingDetailsModal = false;
        }
        this.bookingToCancel = null;
        const refundInfo = res?.refund;
        let msg = 'Booking cancelled successfully! Reserved seats have been released.';
        if (refundInfo && (refundInfo.refundId || refundInfo.id)) {
          const rfdId = refundInfo.refundId || refundInfo.id;
          const amt = refundInfo.refundAmount || res?.booking?.totalAmount;
          msg += `\nRefund of ₹${amt} processed via Razorpay (Refund ID: ${rfdId}).`;
        }
        alert(msg);
        this.loadMyBookings();
      },
      error: (err: any) => {
        this.isCancellingBooking = false;
        const errMsg = err?.error?.message || 'Failed to cancel booking. Please try again or contact support.';
        alert(errMsg);
      }
    });
  }

  cancelMyBooking(bookingId: number) {
    const found = this.myBookings.find(b => b.id === bookingId);
    if (found) {
      this.promptCancelBooking(this.parseBooking(found));
    } else {
      this.bookingService.cancelBooking(bookingId).subscribe({
        next: (res: any) => {
          const refundInfo = res?.refund;
          let msg = 'Booking Cancelled Successfully! Reserved seats have been released.';
          if (refundInfo && (refundInfo.refundId || refundInfo.id)) {
            const rfdId = refundInfo.refundId || refundInfo.id;
            const amt = refundInfo.refundAmount || res?.booking?.totalAmount;
            msg += `\nRefund of ₹${amt} processed via Razorpay (Refund ID: ${rfdId}).`;
          }
          alert(msg);
          this.loadMyBookings();
        },
        error: (err: any) => {
          const errMsg = err?.error?.message || 'Failed to cancel booking.';
          alert(errMsg);
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
