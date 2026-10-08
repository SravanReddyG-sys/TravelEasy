import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { BusService } from '../../services/bus.service';
import { BookingService } from '../../services/booking.service';
import { User } from '../../models/user.model';

@Component({
  selector: 'app-bus-operator-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container py-4">
      <div class="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 class="fw-bold text-dark"><i class="fa-solid fa-bus text-primary me-2"></i>Bus Operator Management Console</h2>
          <p class="text-muted small">Manage buses, schedules, routes, fares, and view passenger manifests</p>
        </div>
        <button class="btn btn-primary rounded-pill fw-bold" (click)="showAddBusModal = true">
          <i class="fa-solid fa-plus me-1"></i> Add New Bus
        </button>
      </div>

      <!-- Navigation Pills -->
      <ul class="nav nav-tabs mb-4">
        <li class="nav-item">
          <button class="nav-link fw-bold" [class.active]="activeTab === 'buses'" (click)="activeTab = 'buses'">Registered Buses</button>
        </li>
        <li class="nav-item">
          <button class="nav-link fw-bold" [class.active]="activeTab === 'schedules'" (click)="activeTab = 'schedules'">Schedule & Route Manager</button>
        </li>
        <li class="nav-item">
          <button class="nav-link fw-bold" [class.active]="activeTab === 'bookings'" (click)="loadBookings(); activeTab = 'bookings'">Passenger Bookings</button>
        </li>
      </ul>

      <!-- TAB 1: REGISTERED BUSES -->
      <div *ngIf="activeTab === 'buses'">
        <div class="row g-3">
          <div *ngFor="let bus of myBuses" class="col-md-6">
            <div class="card p-3 shadow-sm border-start border-4 border-primary">
              <div class="d-flex justify-content-between align-items-start">
                <div>
                  <h5 class="fw-bold mb-1">{{ bus.operatorName }}</h5>
                  <span class="badge bg-secondary mb-2">{{ bus.busType }}</span>
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
        </div>
      </div>

      <!-- TAB 2: SCHEDULE MANAGER -->
      <div *ngIf="activeTab === 'schedules'">
        <div class="alert alert-info">
          Select a bus from the "Registered Buses" tab to publish new trip schedules.
        </div>
      </div>

      <!-- TAB 3: BOOKINGS -->
      <div *ngIf="activeTab === 'bookings'">
        <h5 class="fw-bold mb-3">Passenger Reservations Manifest</h5>
        <div class="table-responsive">
          <table class="table table-hover bg-white rounded shadow-sm">
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
                <td><span class="badge" [class.bg-success]="b.status === 'CONFIRMED'" [class.bg-danger]="b.status === 'CANCELLED'">{{ b.status }}</span></td>
                <td class="fw-bold text-success">₹{{ b.totalAmount }}</td>
                <td>{{ b.bookingDate | date:'short' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ADD BUS MODAL -->
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
                  <label class="form-label font-weight-bold">Bus Registration Number</label>
                  <input type="text" class="form-control" [(ngModel)]="newBusNumber" name="newBusNumber" required placeholder="e.g. AP39TV1234">
                </div>
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Bus Category / Type</label>
                  <select class="form-select" [(ngModel)]="newBusType" name="newBusType">
                    <option value="AC Sleeper (2+1)">AC Sleeper (2+1)</option>
                    <option value="Volvo Multi-Axle AC Semi-Sleeper">Volvo Multi-Axle AC Semi-Sleeper</option>
                    <option value="Non-AC Seater (2+2)">Non-AC Seater (2+2)</option>
                  </select>
                </div>
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Total Seat Capacity</label>
                  <input type="number" class="form-control" [(ngModel)]="newTotalSeats" name="newTotalSeats" required placeholder="30">
                </div>
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Amenities Included</label>
                  <input type="text" class="form-control" [(ngModel)]="newAmenities" name="newAmenities" placeholder="Wi-Fi, Charging Point, Water Bottle">
                </div>
                <button type="submit" class="btn btn-primary w-100 rounded-pill fw-bold">Save Bus Registration</button>
              </form>
            </div>
          </div>
        </div>
      </div>

      <!-- ADD SCHEDULE MODAL -->
      <div *ngIf="showScheduleModal" class="modal fade show d-block bg-dark bg-opacity-50" tabindex="-1">
        <div class="modal-dialog">
          <div class="modal-content">
            <div class="modal-header bg-success text-white">
              <h5 class="modal-title fw-bold"><i class="fa-solid fa-clock me-2"></i>Schedule Trip Route</h5>
              <button type="button" class="btn-close btn-close-white" (click)="showScheduleModal = false"></button>
            </div>
            <div class="modal-body p-4">
              <form (ngSubmit)="submitAddSchedule()">
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Origin City</label>
                  <input type="text" class="form-control" [(ngModel)]="schedOrigin" name="schedOrigin" required placeholder="Vijayawada">
                </div>
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Destination City</label>
                  <input type="text" class="form-control" [(ngModel)]="schedDestination" name="schedDestination" required placeholder="Hyderabad">
                </div>
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Travel Date</label>
                  <input type="date" class="form-control" [(ngModel)]="schedTravelDate" name="schedTravelDate" required>
                </div>
                <div class="row mb-3">
                  <div class="col">
                    <label class="form-label font-weight-bold">Departure</label>
                    <input type="text" class="form-control" [(ngModel)]="schedDeparture" name="schedDeparture" placeholder="22:30">
                  </div>
                  <div class="col">
                    <label class="form-label font-weight-bold">Arrival</label>
                    <input type="text" class="form-control" [(ngModel)]="schedArrival" name="schedArrival" placeholder="05:30">
                  </div>
                </div>
                <div class="mb-3">
                  <label class="form-label font-weight-bold">Seat Fare (₹)</label>
                  <input type="number" class="form-control" [(ngModel)]="schedFare" name="schedFare" required placeholder="850">
                </div>
                <button type="submit" class="btn btn-success w-100 rounded-pill fw-bold">Publish Trip Schedule</button>
              </form>
            </div>
          </div>
        </div>
      </div>

    </div>
  `
})
export class BusOperatorDashboardComponent implements OnInit {
  currentUser: User | null = null;
  activeTab = 'buses';
  myBuses: any[] = [];
  operatorBookings: any[] = [];

  // Add Bus Modal State
  showAddBusModal = false;
  newBusNumber = '';
  newBusType = 'AC Sleeper (2+1)';
  newTotalSeats = 30;
  newAmenities = 'Wi-Fi, Charging Point, Water Bottle';

  // Add Schedule Modal State
  showScheduleModal = false;
  selectedBusForSched: any = null;
  schedOrigin = 'Vijayawada';
  schedDestination = 'Hyderabad';
  schedTravelDate = new Date().toISOString().split('T')[0];
  schedDeparture = '22:30';
  schedArrival = '05:30';
  schedFare = 850;

  constructor(
    private authService: AuthService,
    private busService: BusService,
    private bookingService: BookingService
  ) {}

  ngOnInit() {
    this.currentUser = this.authService.currentUserValue;
    this.loadBuses();
  }

  loadBuses() {
    if (!this.currentUser) return;
    this.busService.getBusesByOperator(this.currentUser.id).subscribe({
      next: (res) => this.myBuses = res
    });
  }

  loadBookings() {
    if (!this.currentUser) return;
    this.bookingService.getProviderBookings(this.currentUser.id).subscribe({
      next: (res) => this.operatorBookings = res
    });
  }

  submitAddBus() {
    if (!this.currentUser) return;
    const payload = {
      operatorId: this.currentUser.id,
      operatorName: this.currentUser.fullName,
      busNumber: this.newBusNumber,
      busType: this.newBusType,
      totalSeats: this.newTotalSeats,
      amenities: this.newAmenities
    };

    this.busService.addBus(payload).subscribe({
      next: () => {
        alert('Bus Registered Successfully!');
        this.showAddBusModal = false;
        this.loadBuses();
      }
    });
  }

  openAddScheduleModal(bus: any) {
    this.selectedBusForSched = bus;
    this.showScheduleModal = true;
  }

  submitAddSchedule() {
    if (!this.selectedBusForSched) return;
    const payload = {
      busId: this.selectedBusForSched.id,
      origin: this.schedOrigin,
      destination: this.schedDestination,
      travelDate: this.schedTravelDate,
      departureTime: this.schedDeparture,
      arrivalTime: this.schedArrival,
      fare: this.schedFare,
      availableSeats: this.selectedBusForSched.totalSeats
    };

    this.busService.createSchedule(payload).subscribe({
      next: () => {
        alert('Trip Schedule Published Successfully!');
        this.showScheduleModal = false;
      }
    });
  }
}
