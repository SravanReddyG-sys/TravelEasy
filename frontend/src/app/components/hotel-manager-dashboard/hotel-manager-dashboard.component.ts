import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { HotelService } from '../../services/hotel.service';
import { BookingService } from '../../services/booking.service';
import { User } from '../../models/user.model';

@Component({
  selector: 'app-hotel-manager-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container py-4">
      <div class="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 class="fw-bold text-dark"><i class="fa-solid fa-hotel text-warning me-2"></i>Hotel Partner Management Console</h2>
          <p class="text-muted small">Manage hotel properties, room categories, night rates, and guest reservations</p>
        </div>
        <button class="btn btn-warning rounded-pill fw-bold text-dark" (click)="showAddHotelModal = true">
          <i class="fa-solid fa-plus me-1"></i> Add Hotel Property
        </button>
      </div>

      <!-- Navigation Pills -->
      <ul class="nav nav-tabs mb-4">
        <li class="nav-item">
          <button class="nav-link fw-bold" [class.active]="activeTab === 'properties'" (click)="activeTab = 'properties'">Hotel Properties</button>
        </li>
        <li class="nav-item">
          <button class="nav-link fw-bold" [class.active]="activeTab === 'reservations'" (click)="loadReservations(); activeTab = 'reservations'">Guest Reservations</button>
        </li>
      </ul>

      <!-- TAB 1: PROPERTIES -->
      <div *ngIf="activeTab === 'properties'">
        <div class="row g-4">
          <div *ngFor="let h of myHotels" class="col-md-6">
            <div class="card shadow-sm h-100 overflow-hidden">
              <img [src]="h.imageUrl || 'https://images.unsplash.com/photo-1566073771259-6a8506099945'" class="card-img-top" style="height: 180px; object-fit: cover;">
              <div class="card-body">
                <div class="d-flex justify-content-between align-items-start">
                  <h5 class="fw-bold mb-1">{{ h.name }}</h5>
                  <span class="badge bg-warning text-dark"><i class="fa-solid fa-star me-1"></i>{{ h.starRating }} Star</span>
                </div>
                <p class="text-muted small mb-2"><i class="fa-solid fa-location-dot me-1 text-danger"></i> {{ h.address }}, {{ h.city }}</p>
                <p class="small text-secondary mb-3">{{ h.description }}</p>
                <button class="btn btn-outline-primary btn-sm rounded-pill w-100 fw-bold" (click)="openAddRoomModal(h)">
                  + Add Room Type & Pricing
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- TAB 2: RESERVATIONS -->
      <div *ngIf="activeTab === 'reservations'">
        <h5 class="fw-bold mb-3">Hotel Room Reservations List</h5>
        <div class="table-responsive">
          <table class="table table-hover bg-white rounded shadow-sm">
            <thead class="table-dark">
              <tr>
                <th>Booking Ref</th>
                <th>Guest Name</th>
                <th>Property & Room Details</th>
                <th>Status</th>
                <th>Total Paid</th>
                <th>Booking Date</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let res of managerReservations">
                <td><strong>{{ res.bookingRef }}</strong></td>
                <td>{{ res.customerName }}<br><small class="text-muted">{{ res.customerEmail }}</small></td>
                <td>{{ res.serviceTitle }}<br><small class="text-muted">{{ res.detailsJson }}</small></td>
                <td><span class="badge" [class.bg-success]="res.status === 'CONFIRMED'" [class.bg-danger]="res.status === 'CANCELLED'">{{ res.status }}</span></td>
                <td class="fw-bold text-success">₹{{ res.totalAmount }}</td>
                <td>{{ res.bookingDate | date:'short' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ADD HOTEL MODAL -->
      <div *ngIf="showAddHotelModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog">
          <div class="modal-content">
            <div class="modal-header bg-warning text-dark">
              <h5 class="modal-title fw-bold"><i class="fa-solid fa-building me-2"></i>Register Hotel Property</h5>
              <button type="button" class="btn-close" (click)="showAddHotelModal = false"></button>
            </div>
            <div class="modal-body p-4">
              <form (ngSubmit)="submitAddHotel()">
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Hotel Name</label>
                  <input type="text" class="form-control" [(ngModel)]="hotelName" name="hotelName" required placeholder="e.g. Novotel Vijayawada Varun">
                </div>
                <div class="mb-3">
                  <label class="form-label font-weight-bold">City</label>
                  <input type="text" class="form-control" [(ngModel)]="hotelCity" name="hotelCity" required placeholder="Vijayawada">
                </div>
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Full Address</label>
                  <input type="text" class="form-control" [(ngModel)]="hotelAddress" name="hotelAddress" required placeholder="Bharathi Nagar">
                </div>
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Star Rating (1 to 5)</label>
                  <input type="number" class="form-control" [(ngModel)]="hotelStarRating" name="hotelStarRating" min="1" max="5">
                </div>
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Description</label>
                  <textarea class="form-control" [(ngModel)]="hotelDescription" name="hotelDescription" rows="2"></textarea>
                </div>
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Amenities</label>
                  <input type="text" class="form-control" [(ngModel)]="hotelAmenities" name="hotelAmenities" placeholder="Swimming Pool, Free Wi-Fi, Spa">
                </div>
                <button type="submit" class="btn btn-warning w-100 rounded-pill fw-bold text-dark">Save Hotel Property</button>
              </form>
            </div>
          </div>
        </div>
      </div>

      <!-- ADD ROOM TYPE MODAL -->
      <div *ngIf="showAddRoomModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog">
          <div class="modal-content">
            <div class="modal-header bg-primary text-white">
              <h5 class="modal-title fw-bold"><i class="fa-solid fa-bed me-2"></i>Add Room Category</h5>
              <button type="button" class="btn-close btn-close-white" (click)="showAddRoomModal = false"></button>
            </div>
            <div class="modal-body p-4">
              <form (ngSubmit)="submitAddRoomType()">
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Room Category Name</label>
                  <input type="text" class="form-control" [(ngModel)]="roomTypeName" name="roomTypeName" required placeholder="e.g. Deluxe King Suite">
                </div>
                <div class="row mb-3">
                  <div class="col">
                    <label class="form-label font-weight-bold">Max Guests</label>
                    <input type="number" class="form-control" [(ngModel)]="roomMaxOccupancy" name="roomMaxOccupancy" placeholder="2">
                  </div>
                  <div class="col">
                    <label class="form-label font-weight-bold">Price / Night (₹)</label>
                    <input type="number" class="form-control" [(ngModel)]="roomPricePerNight" name="roomPricePerNight" required placeholder="4500">
                  </div>
                </div>
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Total Rooms Inventory</label>
                  <input type="number" class="form-control" [(ngModel)]="roomTotalCount" name="roomTotalCount" placeholder="15">
                </div>
                <button type="submit" class="btn btn-primary w-100 rounded-pill fw-bold">Save Room Type</button>
              </form>
            </div>
          </div>
        </div>
      </div>

    </div>
  `
})
export class HotelManagerDashboardComponent implements OnInit {
  currentUser: User | null = null;
  activeTab = 'properties';
  myHotels: any[] = [];
  managerReservations: any[] = [];

  // Add Hotel Modal State
  showAddHotelModal = false;
  hotelName = '';
  hotelCity = 'Vijayawada';
  hotelAddress = '';
  hotelStarRating = 5;
  hotelDescription = '';
  hotelAmenities = 'Swimming Pool, Free Wi-Fi, Gym, Spa';

  // Add Room Modal State
  showAddRoomModal = false;
  selectedHotelForRoom: any = null;
  roomTypeName = 'Deluxe Suite';
  roomMaxOccupancy = 2;
  roomPricePerNight = 4500;
  roomTotalCount = 10;

  constructor(
    private authService: AuthService,
    private hotelService: HotelService,
    private bookingService: BookingService
  ) {}

  ngOnInit() {
    this.currentUser = this.authService.currentUserValue;
    this.loadHotels();
  }

  loadHotels() {
    if (!this.currentUser) return;
    this.hotelService.getHotelsByManager(this.currentUser.id).subscribe({
      next: (res) => this.myHotels = res
    });
  }

  loadReservations() {
    if (!this.currentUser) return;
    this.bookingService.getProviderBookings(this.currentUser.id).subscribe({
      next: (res) => this.managerReservations = res
    });
  }

  submitAddHotel() {
    if (!this.currentUser) return;
    const payload = {
      managerId: this.currentUser.id,
      managerName: this.currentUser.fullName,
      name: this.hotelName,
      city: this.hotelCity,
      address: this.hotelAddress,
      starRating: this.hotelStarRating,
      description: this.hotelDescription,
      amenities: this.hotelAmenities,
      imageUrl: 'https://images.unsplash.com/photo-1566073771259-6a8506099945'
    };

    this.hotelService.addHotel(payload).subscribe({
      next: () => {
        alert('Hotel Property Registered Successfully!');
        this.showAddHotelModal = false;
        this.loadHotels();
      }
    });
  }

  openAddRoomModal(hotel: any) {
    this.selectedHotelForRoom = hotel;
    this.showAddRoomModal = true;
  }

  submitAddRoomType() {
    if (!this.selectedHotelForRoom) return;
    const payload = {
      roomTypeName: this.roomTypeName,
      maxOccupancy: this.roomMaxOccupancy,
      pricePerNight: this.roomPricePerNight,
      totalRooms: this.roomTotalCount,
      availableRooms: this.roomTotalCount,
      amenities: 'King Bed, AC, City View, Free Breakfast'
    };

    this.hotelService.addRoomType(this.selectedHotelForRoom.id, payload).subscribe({
      next: () => {
        alert('Room Type Added Successfully!');
        this.showAddRoomModal = false;
      }
    });
  }
}
