import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { User } from '../../models/user.model';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <nav class="navbar navbar-expand-lg navbar-dark bg-dark sticky-top shadow-sm py-2">
      <div class="container">
        <a class="navbar-brand d-flex align-items-center fw-bold text-white fs-4" routerLink="/">
          <i class="fa-solid fa-bus-simple text-danger me-2"></i> Travel<span class="text-primary">Easy</span>
        </a>
        
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav">
          <span class="navbar-toggler-icon"></span>
        </button>
        
        <div class="collapse navbar-collapse" id="navbarNav">
          <ul class="navbar-nav me-auto ms-lg-4">
            <li class="nav-item me-2" *ngIf="currentUser?.role === 'ROLE_CUSTOMER'">
              <a class="nav-link active" routerLink="/customer"><i class="fa-solid fa-magnifying-glass me-1"></i> Search & Book</a>
            </li>
            <li class="nav-item me-2" *ngIf="currentUser?.role === 'ROLE_BUS_OPERATOR'">
              <a class="nav-link active" routerLink="/bus-operator"><i class="fa-solid fa-bus me-1"></i> Operator Console</a>
            </li>
            <li class="nav-item me-2" *ngIf="currentUser?.role === 'ROLE_HOTEL_MANAGER'">
              <a class="nav-link active" routerLink="/hotel-manager"><i class="fa-solid fa-hotel me-1"></i> Hotel Console</a>
            </li>
            <li class="nav-item me-2" *ngIf="currentUser?.role === 'ROLE_ADMIN'">
              <a class="nav-link active" routerLink="/admin"><i class="fa-solid fa-user-shield me-1"></i> Admin Portal</a>
            </li>
          </ul>

          <div class="d-flex align-items-center gap-2">
            <ng-container *ngIf="currentUser; else authLinks">
              <span class="badge bg-secondary badge-role me-2">
                {{ getRoleLabel(currentUser.role) }}
              </span>
              <span class="text-white me-3 fw-semibold">
                <i class="fa-regular fa-user me-1"></i> {{ currentUser.fullName }}
              </span>
              <button class="btn btn-outline-light btn-sm rounded-pill px-3" (click)="logout()">
                <i class="fa-solid fa-right-from-bracket me-1"></i> Logout
              </button>
            </ng-container>

            <ng-template #authLinks>
              <a routerLink="/login" class="btn btn-outline-light btn-sm rounded-pill px-3 me-2">Login</a>
              <a routerLink="/register" class="btn btn-primary btn-sm rounded-pill px-3">Register</a>
            </ng-template>
          </div>
        </div>
      </div>
    </nav>
  `
})
export class NavbarComponent {
  currentUser: User | null = null;

  constructor(private authService: AuthService, private router: Router) {
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
    });
  }

  getRoleLabel(role: string): string {
    switch(role) {
      case 'ROLE_CUSTOMER': return 'Traveller';
      case 'ROLE_BUS_OPERATOR': return 'Bus Operator';
      case 'ROLE_HOTEL_MANAGER': return 'Hotel Manager';
      case 'ROLE_ADMIN': return 'Administrator';
      default: return role;
    }
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
