import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-traveller-sidebar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <aside class="left-sidebar bg-white border-end d-flex flex-column py-3" [class.collapsed]="isCollapsed">
      <!-- Sidebar Toggle Button -->
      <div class="d-flex align-items-center mb-3 px-3" [class.justify-content-center]="isCollapsed" [class.justify-content-between]="!isCollapsed">
        <span *ngIf="!isCollapsed" class="fw-bold text-navy small text-uppercase tracking-wider">Navigation</span>
        <button class="btn btn-sm btn-light border-0 sidebar-toggle-btn rounded-circle text-muted" 
                (click)="onToggleCollapse()" 
                [title]="isCollapsed ? 'Expand navigation' : 'Collapse navigation'"
                [attr.aria-label]="isCollapsed ? 'Expand navigation rail' : 'Collapse navigation rail'">
          <i class="fa-solid" [class.fa-angles-right]="isCollapsed" [class.fa-angles-left]="!isCollapsed"></i>
        </button>
      </div>

      <ul class="nav flex-column gap-1 px-2">
        <li class="nav-item">
          <a 
            class="nav-link sidebar-link rounded-3" 
            [class.active]="activeTab === 'dashboard' || activeTab === 'destination-details'" 
            (click)="onSwitchTab('dashboard')"
            [title]="'Dashboard'"
            [attr.aria-label]="'Dashboard'"
          >
            <i class="fa-solid fa-house" [class.me-3]="!isCollapsed"></i>
            <span *ngIf="!isCollapsed">Dashboard</span>
          </a>
        </li>
        <li class="nav-item">
          <a 
            class="nav-link sidebar-link rounded-3" 
            [class.active]="activeTab === 'bus'" 
            (click)="onSwitchTab('bus')"
            [title]="'Book Buses'"
            [attr.aria-label]="'Book Buses'"
          >
            <i class="fa-solid fa-bus" [class.me-3]="!isCollapsed"></i>
            <span *ngIf="!isCollapsed">Book Buses</span>
          </a>
        </li>
        <li class="nav-item">
          <a 
            class="nav-link sidebar-link rounded-3" 
            [class.active]="activeTab === 'hotel'" 
            (click)="onSwitchTab('hotel')"
            [title]="'Book Hotels'"
            [attr.aria-label]="'Book Hotels'"
          >
            <i class="fa-solid fa-hotel" [class.me-3]="!isCollapsed"></i>
            <span *ngIf="!isCollapsed">Book Hotels</span>
          </a>
        </li>
        <li class="nav-item">
          <a 
            class="nav-link sidebar-link rounded-3" 
            [class.active]="activeTab === 'my-bookings'" 
            (click)="onSwitchTab('my-bookings')"
            [title]="'My Bookings'"
            [attr.aria-label]="'My Bookings'"
          >
            <i class="fa-solid fa-calendar-days" [class.me-3]="!isCollapsed"></i>
            <span *ngIf="!isCollapsed">My Bookings</span>
          </a>
        </li>
        <li class="nav-item">
          <a 
            class="nav-link sidebar-link rounded-3" 
            [class.active]="activeTab === 'favourites'" 
            (click)="onSwitchTab('favourites')"
            [title]="'Saved'"
            [attr.aria-label]="'Saved'"
          >
            <i class="fa-regular fa-heart" [class.me-3]="!isCollapsed"></i>
            <span *ngIf="!isCollapsed">Saved</span>
          </a>
        </li>
        <li class="nav-item">
          <a 
            class="nav-link sidebar-link rounded-3" 
            [class.active]="activeTab === 'profile'" 
            (click)="onSwitchTab('profile')"
            [title]="'Profile'"
            [attr.aria-label]="'Profile'"
          >
            <i class="fa-regular fa-user" [class.me-3]="!isCollapsed"></i>
            <span *ngIf="!isCollapsed">Profile</span>
          </a>
        </li>
        <li class="nav-item">
          <a 
            class="nav-link sidebar-link rounded-3" 
            [class.active]="activeTab === 'settings'" 
            (click)="onSwitchTab('settings')"
            [title]="'Settings'"
            [attr.aria-label]="'Settings'"
          >
            <i class="fa-solid fa-gear" [class.me-3]="!isCollapsed"></i>
            <span *ngIf="!isCollapsed">Settings</span>
          </a>
        </li>
        <li class="nav-item">
          <a 
            class="nav-link sidebar-link rounded-3" 
            [class.active]="activeTab === 'support'" 
            (click)="onSwitchTab('support')"
            [title]="'Support'"
            [attr.aria-label]="'Support'"
          >
            <i class="fa-solid fa-headphones" [class.me-3]="!isCollapsed"></i>
            <span *ngIf="!isCollapsed">Support</span>
          </a>
        </li>
      </ul>
    </aside>
  `,
  styles: [`
    .left-sidebar {
      width: 240px;
      min-width: 240px;
      transition: all 0.25s ease-in-out;
      background-color: #FFFFFF;
    }
    .left-sidebar.collapsed {
      width: 72px;
      min-width: 72px;
    }
    .left-sidebar.collapsed .sidebar-toggle-btn {
      margin: 0 auto;
    }
    .left-sidebar.collapsed .sidebar-link {
      justify-content: center;
      padding: 12px 0;
    }
    .left-sidebar.collapsed .sidebar-link i {
      margin: 0 !important;
      font-size: 1.25rem;
    }
    .sidebar-toggle-btn {
      width: 32px;
      height: 32px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      transition: background 0.15s ease;
    }
    .sidebar-toggle-btn:hover {
      background-color: #E2E8F0;
      color: #0F172A;
    }
    .sidebar-link {
      color: #64748B;
      font-weight: 600;
      font-size: 0.92rem;
      padding: 10px 16px;
      display: flex;
      align-items: center;
      transition: all 0.15s ease;
      cursor: pointer;
      text-decoration: none;
    }
    .sidebar-link:hover {
      color: #0875E1;
      background-color: #F0F6FE;
    }
    .sidebar-link.active {
      color: #0875E1;
      background-color: #E6F0FD;
      font-weight: 700;
    }
    .sidebar-link i {
      font-size: 1.1rem;
      width: 20px;
      text-align: center;
    }
    .text-navy {
      color: #0F172A;
    }
    .tracking-wider {
      letter-spacing: 0.05em;
    }
  `]
})
export class TravellerSidebarComponent {
  @Input() activeTab = 'dashboard';
  @Input() isCollapsed = true;

  @Output() switchTab = new EventEmitter<string>();
  @Output() toggleCollapse = new EventEmitter<void>();

  onSwitchTab(tab: string) {
    this.switchTab.emit(tab);
  }

  onToggleCollapse() {
    this.toggleCollapse.emit();
  }
}
