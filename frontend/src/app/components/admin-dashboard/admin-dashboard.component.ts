import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';
import { BookingService } from '../../services/booking.service';
import { User } from '../../models/user.model';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="container py-4">
      <div class="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 class="fw-bold text-dark"><i class="fa-solid fa-user-shield text-danger me-2"></i>Platform Administration Portal</h2>
          <p class="text-muted small">System monitoring, provider approval, booking oversight, and review moderation</p>
        </div>
      </div>

      <!-- Quick Metrics Cards -->
      <div class="row g-3 mb-4">
        <div class="col-md-3">
          <div class="card p-3 bg-primary text-white shadow-sm">
            <h6 class="text-uppercase small fw-bold opacity-75">Total Users</h6>
            <h2 class="fw-bold mb-0">{{ usersList.length }}</h2>
          </div>
        </div>
        <div class="col-md-3">
          <div class="card p-3 bg-success text-white shadow-sm">
            <h6 class="text-uppercase small fw-bold opacity-75">Platform Bookings</h6>
            <h2 class="fw-bold mb-0">{{ allBookings.length }}</h2>
          </div>
        </div>
        <div class="col-md-3">
          <div class="card p-3 bg-warning text-dark shadow-sm">
            <h6 class="text-uppercase small fw-bold opacity-75">Total Revenue</h6>
            <h2 class="fw-bold mb-0">₹{{ totalRevenue }}</h2>
          </div>
        </div>
        <div class="col-md-3">
          <div class="card p-3 bg-danger text-white shadow-sm">
            <h6 class="text-uppercase small fw-bold opacity-75">User Reviews</h6>
            <h2 class="fw-bold mb-0">{{ reviewsList.length }}</h2>
          </div>
        </div>
      </div>

      <!-- Navigation Tabs -->
      <ul class="nav nav-tabs mb-4">
        <li class="nav-item">
          <button class="nav-link fw-bold" [class.active]="activeTab === 'users'" (click)="activeTab = 'users'">User & Provider Verification</button>
        </li>
        <li class="nav-item">
          <button class="nav-link fw-bold" [class.active]="activeTab === 'bookings'" (click)="activeTab = 'bookings'">All System Bookings</button>
        </li>
        <li class="nav-item">
          <button class="nav-link fw-bold" [class.active]="activeTab === 'reviews'" (click)="activeTab = 'reviews'">Review Moderation</button>
        </li>
      </ul>

      <!-- TAB 1: USERS & VERIFICATION -->
      <div *ngIf="activeTab === 'users'">
        <div class="table-responsive">
          <table class="table table-hover bg-white rounded shadow-sm">
            <thead class="table-dark">
              <tr>
                <th>ID</th>
                <th>Full Name / Agency</th>
                <th>Email</th>
                <th>Role</th>
                <th>Verification Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let u of usersList">
                <td>{{ u.id }}</td>
                <td><strong>{{ u.fullName }}</strong></td>
                <td>{{ u.email }}</td>
                <td><span class="badge bg-secondary">{{ u.role }}</span></td>
                <td>
                  <span class="badge" [class.bg-success]="u.verified" [class.bg-warning]="!u.verified">
                    {{ u.verified ? 'Verified' : 'Pending Verification' }}
                  </span>
                </td>
                <td>
                  <button *ngIf="!u.verified" class="btn btn-success btn-sm rounded-pill px-3" (click)="verifyUser(u.id, true)">
                    Approve Provider
                  </button>
                  <button *ngIf="u.verified" class="btn btn-outline-danger btn-sm rounded-pill px-3" (click)="verifyUser(u.id, false)">
                    Revoke Approval
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- TAB 2: BOOKINGS -->
      <div *ngIf="activeTab === 'bookings'">
        <div class="table-responsive">
          <table class="table table-hover bg-white rounded shadow-sm">
            <thead class="table-dark">
              <tr>
                <th>Ref</th>
                <th>Customer</th>
                <th>Service Type</th>
                <th>Title</th>
                <th>Status</th>
                <th>Amount</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let b of allBookings">
                <td><strong>{{ b.bookingRef }}</strong></td>
                <td>{{ b.customerName }}</td>
                <td><span class="badge bg-info text-dark">{{ b.serviceType }}</span></td>
                <td>{{ b.serviceTitle }}</td>
                <td><span class="badge" [class.bg-success]="b.status === 'CONFIRMED'" [class.bg-danger]="b.status === 'CANCELLED'">{{ b.status }}</span></td>
                <td class="fw-bold text-success">₹{{ b.totalAmount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- TAB 3: REVIEWS MODERATION -->
      <div *ngIf="activeTab === 'reviews'">
        <div class="table-responsive">
          <table class="table table-hover bg-white rounded shadow-sm">
            <thead class="table-dark">
              <tr>
                <th>Customer</th>
                <th>Service</th>
                <th>Rating</th>
                <th>Comment</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let r of reviewsList">
                <td><strong>{{ r.customerName }}</strong></td>
                <td><span class="badge bg-secondary">{{ r.serviceType }}</span> ID: {{ r.serviceId }}</td>
                <td><span class="text-warning font-weight-bold">★ {{ r.rating }} / 5</span></td>
                <td>{{ r.comment }}</td>
                <td>
                  <button class="btn btn-danger btn-sm rounded-pill px-3" (click)="deleteReview(r.id)">
                    Delete Review
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </div>
  `
})
export class AdminDashboardComponent implements OnInit {
  activeTab = 'users';
  usersList: User[] = [];
  allBookings: any[] = [];
  reviewsList: any[] = [];
  totalRevenue = 0;

  constructor(
    private authService: AuthService,
    private bookingService: BookingService
  ) {}

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.authService.getAllUsers().subscribe({
      next: (res) => this.usersList = res
    });

    this.bookingService.getAllBookings().subscribe({
      next: (res) => {
        this.allBookings = res;
        this.totalRevenue = res.filter(b => b.status === 'CONFIRMED').reduce((acc, b) => acc + (b.totalAmount || 0), 0);
      }
    });

    this.bookingService.getAllReviews().subscribe({
      next: (res) => this.reviewsList = res
    });
  }

  verifyUser(userId: number, verified: boolean) {
    this.authService.verifyProvider(userId, verified).subscribe({
      next: () => {
        alert(`Verification updated for user #${userId}`);
        this.loadData();
      }
    });
  }

  deleteReview(reviewId: number) {
    if (confirm('Delete this review from platform?')) {
      this.bookingService.deleteReview(reviewId).subscribe({
        next: () => {
          alert('Review deleted.');
          this.loadData();
        }
      });
    }
  }
}
