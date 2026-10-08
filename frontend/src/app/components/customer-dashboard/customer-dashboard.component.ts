import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { BusService } from '../../services/bus.service';
import { HotelService } from '../../services/hotel.service';
import { BookingService } from '../../services/booking.service';
import { User } from '../../models/user.model';

@Component({
  selector: 'app-customer-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="hero-banner mb-4">
      <div class="container text-center py-3">
        <h1 class="fw-bold mb-2"><i class="fa-solid fa-compass me-2 text-warning"></i>Explore & Book Your Next Journey</h1>
        <p class="lead opacity-90">Book luxury buses & top-rated hotels across India with instant confirmation</p>
      </div>
    </div>

    <div class="container pb-5">
      <!-- Navigation Tabs -->
      <ul class="nav nav-pills nav-fill mb-4 bg-white p-2 rounded-pill shadow-sm border">
        <li class="nav-item">
          <button class="nav-link rounded-pill fw-bold" [class.active]="activeTab === 'bus'" (click)="activeTab = 'bus'">
            <i class="fa-solid fa-bus me-2"></i> Bus Ticket Booking
          </button>
        </li>
        <li class="nav-item">
          <button class="nav-link rounded-pill fw-bold" [class.active]="activeTab === 'hotel'" (click)="activeTab = 'hotel'">
            <i class="fa-solid fa-hotel me-2"></i> Hotel Room Booking
          </button>
        </li>
        <li class="nav-item">
          <button class="nav-link rounded-pill fw-bold" [class.active]="activeTab === 'my-bookings'" (click)="loadMyBookings(); activeTab = 'my-bookings'">
            <i class="fa-solid fa-ticket me-2"></i> My Bookings & Tickets
          </button>
        </li>
      </ul>

      <!-- TAB 1: BUS BOOKING -->
      <div *ngIf="activeTab === 'bus'">
        <div class="card p-4 mb-4 shadow-sm">
          <h4 class="fw-bold text-primary mb-3"><i class="fa-solid fa-route me-2"></i>Search Buses</h4>
          <form (ngSubmit)="onSearchBuses()" class="row g-3">
            <div class="col-md-4">
              <label class="form-label fw-semibold">From (Origin City)</label>
              <input type="text" class="form-control" [(ngModel)]="busOrigin" name="busOrigin" placeholder="e.g. Vijayawada">
            </div>
            <div class="col-md-4">
              <label class="form-label fw-semibold">To (Destination City)</label>
              <input type="text" class="form-control" [(ngModel)]="busDestination" name="busDestination" placeholder="e.g. Hyderabad">
            </div>
            <div class="col-md-3">
              <label class="form-label fw-semibold">Date of Journey</label>
              <input type="date" class="form-control" [(ngModel)]="busDate" name="busDate">
            </div>
            <div class="col-md-1 d-flex align-items-end">
              <button type="submit" class="btn btn-primary w-100 fw-bold"><i class="fa-solid fa-search"></i></button>
            </div>
          </form>
        </div>

        <!-- Bus Search Results -->
        <div *ngIf="busesSearched">
          <h5 class="fw-bold mb-3">Available Buses ({{ busResults.length }})</h5>
          <div *ngIf="busResults.length === 0" class="alert alert-info">
            No buses found matching your search criteria. Try Vijayawada to Hyderabad.
          </div>

          <div *ngFor="let b of busResults" class="card mb-3 p-3 shadow-sm border-start border-4 border-primary">
            <div class="row align-items-center">
              <div class="col-md-4">
                <h5 class="fw-bold mb-1 text-dark">{{ b.operatorName }}</h5>
                <span class="badge bg-secondary mb-2">{{ b.busType }}</span>
                <p class="text-muted small mb-0"><i class="fa-solid fa-van-shuttle me-1"></i> {{ b.busNumber }}</p>
              </div>
              <div class="col-md-4 text-center">
                <div class="d-flex justify-content-around align-items-center">
                  <div>
                    <h5 class="fw-bold mb-0">{{ b.departureTime }}</h5>
                    <small class="text-muted">{{ b.origin }}</small>
                  </div>
                  <i class="fa-solid fa-arrow-right text-primary"></i>
                  <div>
                    <h5 class="fw-bold mb-0">{{ b.arrivalTime }}</h5>
                    <small class="text-muted">{{ b.destination }}</small>
                  </div>
                </div>
              </div>
              <div class="col-md-4 text-end">
                <h4 class="fw-bold text-success mb-1">₹{{ b.fare }}</h4>
                <small class="text-muted d-block mb-2">{{ b.availableSeats }} Seats Left</small>
                <button class="btn btn-outline-primary btn-sm rounded-pill px-3 fw-bold" (click)="openBusSeatMap(b)">
                  Select Seats
                </button>
              </div>
            </div>

            <!-- Seat Picker Accordion -->
            <div *ngIf="selectedSchedule?.id === b.id" class="mt-4 pt-3 border-top bg-light p-3 rounded-3">
              <h6 class="fw-bold mb-3"><i class="fa-solid fa-chair me-2"></i>Select Your Seats for {{ b.operatorName }}</h6>
              <div class="d-flex gap-3 mb-3 small">
                <span class="d-flex align-items-center"><span class="seat-box available me-1"></span> Available</span>
                <span class="d-flex align-items-center"><span class="seat-box selected me-1"></span> Selected</span>
                <span class="d-flex align-items-center"><span class="seat-box booked me-1"></span> Booked</span>
              </div>

              <div class="d-flex flex-wrap gap-2 mb-3">
                <div *ngFor="let s of seatList" 
                     class="seat-box" 
                     [class.available]="s.status === 'AVAILABLE'"
                     [class.selected]="selectedSeatNumbers.includes(s.seatNumber)"
                     [class.booked]="s.status === 'BOOKED'"
                     (click)="toggleSeat(s)">
                  {{ s.seatNumber }}
                </div>
              </div>

              <div *ngIf="selectedSeatNumbers.length > 0" class="d-flex justify-content-between align-items-center bg-white p-3 rounded-3 border">
                <div>
                  <span class="fw-bold">Selected Seats: </span> {{ selectedSeatNumbers.join(', ') }}
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

      <!-- TAB 2: HOTEL BOOKING -->
      <div *ngIf="activeTab === 'hotel'">
        <div class="card p-4 mb-4 shadow-sm">
          <h4 class="fw-bold text-primary mb-3"><i class="fa-solid fa-hotel me-2"></i>Search Hotels</h4>
          <form (ngSubmit)="onSearchHotels()" class="row g-3">
            <div class="col-md-5">
              <label class="form-label fw-semibold">City or Location</label>
              <input type="text" class="form-control" [(ngModel)]="hotelCity" name="hotelCity" placeholder="e.g. Vijayawada or Hyderabad">
            </div>
            <div class="col-md-3">
              <label class="form-label fw-semibold">Check-in Date</label>
              <input type="date" class="form-control" [(ngModel)]="checkInDate" name="checkInDate">
            </div>
            <div class="col-md-3">
              <label class="form-label fw-semibold">Check-out Date</label>
              <input type="date" class="form-control" [(ngModel)]="checkOutDate" name="checkOutDate">
            </div>
            <div class="col-md-1 d-flex align-items-end">
              <button type="submit" class="btn btn-primary w-100 fw-bold"><i class="fa-solid fa-search"></i></button>
            </div>
          </form>
        </div>

        <!-- Hotel Search Results -->
        <div *ngIf="hotelsSearched">
          <h5 class="fw-bold mb-3">Hotels Found ({{ hotelResults.length }})</h5>
          <div *ngIf="hotelResults.length === 0" class="alert alert-info">
            No hotels found matching your search city. Try Vijayawada or Hyderabad.
          </div>

          <div *ngFor="let h of hotelResults" class="card mb-3 shadow-sm overflow-hidden">
            <div class="row g-0">
              <div class="col-md-4">
                <img [src]="h.imageUrl || 'https://images.unsplash.com/photo-1566073771259-6a8506099945'" class="img-fluid h-100 object-fit-cover" alt="Hotel Photo">
              </div>
              <div class="col-md-8 p-3 d-flex flex-column justify-content-between">
                <div>
                  <div class="d-flex justify-content-between align-items-start">
                    <h5 class="fw-bold text-dark mb-1">{{ h.name }}</h5>
                    <span class="badge bg-warning text-dark"><i class="fa-solid fa-star me-1"></i>{{ h.starRating }} Star</span>
                  </div>
                  <p class="text-muted small mb-2"><i class="fa-solid fa-location-dot me-1 text-danger"></i> {{ h.address }}, {{ h.city }}</p>
                  <p class="small text-secondary mb-2">{{ h.description }}</p>
                  <p class="small text-primary mb-0"><i class="fa-solid fa-bell-concierge me-1"></i> {{ h.amenities }}</p>
                </div>

                <div class="text-end mt-3">
                  <button class="btn btn-outline-primary rounded-pill px-4 fw-bold" (click)="viewHotelRooms(h)">
                    View Available Rooms
                  </button>
                </div>
              </div>
            </div>

            <!-- Rooms Accordion -->
            <div *ngIf="selectedHotel?.id === h.id" class="p-3 bg-light border-top">
              <h6 class="fw-bold mb-3 text-primary"><i class="fa-solid fa-bed me-2"></i>Select Room Type for {{ h.name }}</h6>
              <div class="row g-3">
                <div *ngFor="let r of hotelRoomTypes" class="col-md-6">
                  <div class="card p-3 h-100 border">
                    <h6 class="fw-bold mb-1">{{ r.roomTypeName }}</h6>
                    <p class="small text-muted mb-2">Max Occupancy: {{ r.maxOccupancy }} Guests | {{ r.amenities }}</p>
                    <div class="d-flex justify-content-between align-items-center mt-2">
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

      <!-- TAB 3: MY BOOKINGS -->
      <div *ngIf="activeTab === 'my-bookings'">
        <h4 class="fw-bold text-primary mb-3"><i class="fa-solid fa-clock-rotate-left me-2"></i>Your Booking History</h4>
        
        <div *ngIf="myBookings.length === 0" class="alert alert-info">
          You have no active or completed bookings yet.
        </div>

        <div *ngFor="let b of myBookings" class="card mb-3 p-3 shadow-sm border-start border-4"
             [class.border-success]="b.status === 'CONFIRMED'"
             [class.border-danger]="b.status === 'CANCELLED'"
             [class.border-warning]="b.status === 'PENDING_PAYMENT'">
          <div class="d-flex justify-content-between align-items-center">
            <div>
              <span class="badge bg-primary me-2">{{ b.serviceType }}</span>
              <span class="fw-bold text-dark me-2">{{ b.serviceTitle }}</span>
              <span class="badge" [class.bg-success]="b.status === 'CONFIRMED'" [class.bg-danger]="b.status === 'CANCELLED'">{{ b.status }}</span>
              <p class="text-muted small mb-0 mt-1">Ref: <strong>{{ b.bookingRef }}</strong> | Date: {{ b.bookingDate | date:'medium' }}</p>
              <p class="small mb-0 text-secondary mt-1">Details: {{ b.detailsJson }}</p>
            </div>
            <div class="text-end">
              <h5 class="fw-bold text-success">₹{{ b.totalAmount }}</h5>
              <button *ngIf="b.status === 'CONFIRMED'" class="btn btn-outline-danger btn-sm rounded-pill px-3 me-2" (click)="cancelMyBooking(b.id)">
                Cancel Reservation
              </button>
              <button *ngIf="b.status === 'CONFIRMED'" class="btn btn-outline-primary btn-sm rounded-pill px-3" (click)="openReviewModal(b)">
                Rate & Review
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- CHECKOUT MODAL -->
      <div *ngIf="showCheckoutModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-header bg-primary text-white">
              <h5 class="modal-title fw-bold"><i class="fa-solid fa-credit-card me-2"></i>Confirm & Pay</h5>
              <button type="button" class="btn-close btn-close-white" (click)="showCheckoutModal = false"></button>
            </div>
            <div class="modal-body p-4">
              <h6 class="fw-bold text-dark">{{ checkoutServiceTitle }}</h6>
              <p class="small text-muted">{{ checkoutDetails }}</p>
              <hr>
              <div class="d-flex justify-content-between mb-3 fw-bold">
                <span>Total Amount Payable:</span>
                <span class="text-success fs-5">₹{{ checkoutAmount }}</span>
              </div>

              <div class="mb-3">
                <label class="form-label fw-bold small">Select Payment Method</label>
                <select class="form-select" [(ngModel)]="paymentMethod">
                  <option value="CARD">Credit / Debit Card</option>
                  <option value="UPI">UPI (Google Pay / PhonePe / Paytm)</option>
                  <option value="NET_BANKING">Net Banking</option>
                </select>
              </div>

              <button class="btn btn-success w-100 py-2 fw-bold rounded-pill" (click)="submitPayment()">
                Pay ₹{{ checkoutAmount }} & Confirm Reservation
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- REVIEW MODAL -->
      <div *ngIf="showReviewModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-header bg-success text-white">
              <h5 class="modal-title fw-bold"><i class="fa-solid fa-star me-2"></i>Rate & Review</h5>
              <button type="button" class="btn-close btn-close-white" (click)="showReviewModal = false"></button>
            </div>
            <div class="modal-body p-4">
              <h6 class="fw-bold mb-3">{{ reviewBooking?.serviceTitle }}</h6>
              <div class="mb-3">
                <label class="form-label fw-bold">Rating (1 to 5 Stars)</label>
                <select class="form-select" [(ngModel)]="reviewRating">
                  <option [value]="5">5 - Excellent</option>
                  <option [value]="4">4 - Very Good</option>
                  <option [value]="3">3 - Good</option>
                  <option [value]="2">2 - Fair</option>
                  <option [value]="1">1 - Poor</option>
                </select>
              </div>
              <div class="mb-3">
                <label class="form-label fw-bold">Your Feedback / Review</label>
                <textarea class="form-control" rows="3" [(ngModel)]="reviewComment" placeholder="Share details of your travel or stay experience..."></textarea>
              </div>
              <button class="btn btn-primary w-100 rounded-pill fw-bold" (click)="submitReview()">
                Submit Review
              </button>
            </div>
          </div>
        </div>
      </div>

    </div>
  `
})
export class CustomerDashboardComponent implements OnInit {
  currentUser: User | null = null;
  activeTab = 'bus';

  // Bus Search State
  busOrigin = 'Vijayawada';
  busDestination = 'Hyderabad';
  busDate = new Date().toISOString().split('T')[0];
  busesSearched = false;
  busResults: any[] = [];
  selectedSchedule: any = null;
  seatList: any[] = [];
  selectedSeatNumbers: string[] = [];

  // Hotel Search State
  hotelCity = 'Vijayawada';
  checkInDate = new Date().toISOString().split('T')[0];
  checkOutDate = new Date(Date.now() + 86400000).toISOString().split('T')[0];
  hotelsSearched = false;
  hotelResults: any[] = [];
  selectedHotel: any = null;
  hotelRoomTypes: any[] = [];

  // Checkout State
  showCheckoutModal = false;
  checkoutServiceType = '';
  checkoutServiceId = 0;
  checkoutProviderId = 0;
  checkoutServiceTitle = '';
  checkoutDetails = '';
  checkoutAmount = 0;
  paymentMethod = 'CARD';

  // My Bookings State
  myBookings: any[] = [];

  // Review Modal State
  showReviewModal = false;
  reviewBooking: any = null;
  reviewRating = 5;
  reviewComment = '';

  constructor(
    private authService: AuthService,
    private busService: BusService,
    private hotelService: HotelService,
    private bookingService: BookingService
  ) {}

  ngOnInit() {
    this.currentUser = this.authService.currentUserValue;
    this.onSearchBuses();
    this.onSearchHotels();
  }

  onSearchBuses() {
    this.busService.searchBuses(this.busOrigin, this.busDestination, this.busDate).subscribe({
      next: (res) => {
        this.busResults = res;
        this.busesSearched = true;
      }
    });
  }

  openBusSeatMap(schedule: any) {
    this.selectedSchedule = schedule;
    this.selectedSeatNumbers = [];
    this.busService.getScheduleDetails(schedule.id).subscribe({
      next: (res) => {
        this.seatList = res.seats || [];
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
    this.checkoutProviderId = schedule.busId;
    this.checkoutServiceTitle = `${schedule.operatorName} (${schedule.origin} to ${schedule.destination})`;
    this.checkoutDetails = `Seats: ${this.selectedSeatNumbers.join(', ')} | Journey Date: ${schedule.travelDate}`;
    this.checkoutAmount = this.selectedSeatNumbers.length * schedule.fare;
    this.showCheckoutModal = true;
  }

  onSearchHotels() {
    this.hotelService.searchHotels(this.hotelCity, this.checkInDate, this.checkOutDate).subscribe({
      next: (res) => {
        this.hotelResults = res;
        this.hotelsSearched = true;
      }
    });
  }

  viewHotelRooms(hotel: any) {
    this.selectedHotel = hotel;
    this.hotelService.getHotelDetails(hotel.id).subscribe({
      next: (res) => {
        this.hotelRoomTypes = res.rooms || [];
      }
    });
  }

  proceedToHotelCheckout(hotel: any, room: any) {
    this.checkoutServiceType = 'HOTEL';
    this.checkoutServiceId = hotel.id;
    this.checkoutProviderId = hotel.managerId || 3;
    this.checkoutServiceTitle = `${hotel.name} (${room.roomTypeName})`;
    this.checkoutDetails = `Check-in: ${this.checkInDate} | Check-out: ${this.checkOutDate}`;
    this.checkoutAmount = room.pricePerNight;
    this.showCheckoutModal = true;
  }

  submitPayment() {
    if (!this.currentUser) return;

    const bookingPayload = {
      customerId: this.currentUser.id,
      customerName: this.currentUser.fullName,
      customerEmail: this.currentUser.email,
      serviceType: this.checkoutServiceType,
      serviceId: this.checkoutServiceId,
      providerId: this.checkoutProviderId,
      serviceTitle: this.checkoutServiceTitle,
      totalAmount: this.checkoutAmount,
      detailsJson: this.checkoutDetails
    };

    this.bookingService.createBooking(bookingPayload).subscribe({
      next: (booking) => {
        // Process mock payment
        this.bookingService.processPayment({
          bookingRef: booking.bookingRef,
          customerId: this.currentUser?.id,
          amount: this.checkoutAmount,
          paymentMethod: this.paymentMethod
        }).subscribe({
          next: () => {
            this.bookingService.confirmBooking(booking.id).subscribe({
              next: () => {
                alert(`Booking Confirmed Successfully! Reference: ${booking.bookingRef}`);
                this.showCheckoutModal = false;
                this.loadMyBookings();
                this.activeTab = 'my-bookings';
              }
            });
          }
        });
      }
    });
  }

  loadMyBookings() {
    if (!this.currentUser) return;
    this.bookingService.getCustomerBookings(this.currentUser.id).subscribe({
      next: (res) => {
        this.myBookings = res;
      }
    });
  }

  cancelMyBooking(bookingId: number) {
    if (confirm('Are you sure you want to cancel this booking?')) {
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
}
