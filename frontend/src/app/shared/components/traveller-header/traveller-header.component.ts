import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { User } from '../../../models/user.model';

@Component({
  selector: 'app-traveller-header',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <header class="top-header sticky-top d-flex align-items-center justify-content-between px-4 bg-white border-bottom">
      <!-- Brand Logo (Matches Home Page) -->
      <a class="te-brand" (click)="onNavigateTab('dashboard')" role="button" title="Go to Dashboard">
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
          <button *ngIf="searchQuery" class="btn btn-link btn-sm text-muted p-0 ms-1 border-0" (click)="onClearSearch()">
            <i class="fa-solid fa-xmark"></i>
          </button>
        </div>

        <!-- Suggestions Dropdown -->
        <div *ngIf="showSearchSuggestions && filteredSuggestions.length > 0" class="search-suggestions-dropdown position-absolute bg-white shadow-lg rounded-3 border mt-1 w-100 py-2">
          <div class="px-3 py-1 text-muted small fw-semibold text-uppercase">Suggestions</div>
          <div 
            *ngFor="let s of filteredSuggestions" 
            class="suggestion-item px-3 py-2 d-flex align-items-center justify-content-between cursor-pointer"
            (click)="onSelectSuggestion(s)"
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
          <button class="btn btn-light rounded-circle notification-btn position-relative" (click)="onToggleNotifications()" title="Notifications">
            <i class="fa-solid fa-bell text-secondary"></i>
            <span *ngIf="unreadNotificationsCount > 0" class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger">
              {{ unreadNotificationsCount }}
            </span>
          </button>

          <!-- Notifications Dropdown -->
          <div *ngIf="showNotifications" class="notifications-dropdown position-absolute end-0 mt-2 bg-white shadow-lg rounded-4 border p-3">
            <div class="d-flex justify-content-between align-items-center mb-2 pb-2 border-bottom">
              <span class="fw-bold text-dark">Notifications</span>
              <button *ngIf="unreadNotificationsCount > 0" class="btn btn-link btn-sm text-primary p-0 text-decoration-none" (click)="onMarkNotificationsRead()">Mark all as read</button>
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
          <div class="user-pill d-flex align-items-center gap-2 py-1 px-2 rounded-pill border cursor-pointer bg-white" (click)="onToggleUserMenu()">
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
            <button class="dropdown-item py-2 px-3 d-flex align-items-center gap-2 text-dark" (click)="onNavigateTab('profile'); showUserMenu = false;">
              <i class="fa-regular fa-user text-primary"></i> My Profile
            </button>
            <button class="dropdown-item py-2 px-3 d-flex align-items-center gap-2 text-dark" (click)="onNavigateTab('settings'); showUserMenu = false;">
              <i class="fa-solid fa-gear text-secondary"></i> Travel Settings
            </button>
            <button class="dropdown-item py-2 px-3 d-flex align-items-center gap-2 text-dark" (click)="onNavigateTab('support'); showUserMenu = false;">
              <i class="fa-solid fa-headset text-success"></i> Support & Help
            </button>
            <div class="dropdown-divider"></div>
            <button class="dropdown-item py-2 px-3 d-flex align-items-center gap-2 text-danger" (click)="onLogout()">
              <i class="fa-solid fa-arrow-right-from-bracket"></i> Sign Out
            </button>
          </div>
        </div>
      </div>
    </header>
  `,
  styles: [`
    .top-header {
      height: 72px;
      z-index: 1020;
    }
    .te-brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      text-decoration: none;
      cursor: pointer;
    }
    .te-brand-icon {
      width: 38px;
      height: 38px;
      border-radius: 10px;
      background: linear-gradient(135deg, #0875E1 0%, #0056b3 100%);
      color: #FFFFFF;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 1.05rem;
      box-shadow: 0 4px 10px rgba(8, 117, 225, 0.25);
    }
    .te-brand-text {
      font-size: 1.35rem;
      font-weight: 800;
      color: #0F172A;
      letter-spacing: -0.5px;
    }
    .te-brand-blue {
      color: #0875E1;
    }
    .header-search-box {
      width: 360px;
      max-width: 100%;
    }
    @media (max-width: 768px) {
      .header-search-box {
        display: none;
      }
    }
    .search-input-pill {
      background-color: #F1F5F9;
      border-radius: 50px;
      border: 1px solid #E2E8F0;
      transition: all 0.2s ease;
    }
    .search-input-pill:focus-within {
      background-color: #FFFFFF;
      border-color: #0875E1;
      box-shadow: 0 0 0 3px rgba(8, 117, 225, 0.15);
    }
    .search-input {
      font-size: 0.88rem;
    }
    .search-suggestions-dropdown {
      top: 100%;
      left: 0;
      z-index: 1050;
      max-height: 320px;
      overflow-y: auto;
    }
    .suggestion-item:hover {
      background-color: #F8FAFC;
    }
    .notification-btn {
      width: 42px;
      height: 42px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .notifications-dropdown {
      width: 320px;
      z-index: 1050;
    }
    .user-pill {
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
      transition: all 0.2s ease;
    }
    .user-pill:hover {
      border-color: #CBD5E1 !important;
      background-color: #F8FAFC !important;
    }
    .user-avatar-circle {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: linear-gradient(135deg, #0875E1, #0056b3);
      color: #FFFFFF;
      font-weight: 700;
      font-size: 0.8rem;
    }
    .user-menu-dropdown {
      width: 220px;
      z-index: 1050;
    }
    .smaller {
      font-size: 0.75rem;
    }
    .cursor-pointer {
      cursor: pointer;
    }
  `]
})
export class TravellerHeaderComponent {
  @Input() currentUser: User | null = null;
  @Input() searchQuery = '';
  @Input() showSearchSuggestions = false;
  @Input() filteredSuggestions: any[] = [];
  @Input() notificationList: any[] = [];
  @Input() unreadNotificationsCount = 0;
  @Input() showNotifications = false;
  @Input() showUserMenu = false;

  @Output() navigateTab = new EventEmitter<string>();
  @Output() searchQueryChange = new EventEmitter<string>();
  @Output() searchInput = new EventEmitter<string>();
  @Output() searchSubmit = new EventEmitter<string>();
  @Output() selectSuggestion = new EventEmitter<any>();
  @Output() clearSearch = new EventEmitter<void>();
  @Output() toggleNotifications = new EventEmitter<void>();
  @Output() markNotificationsRead = new EventEmitter<void>();
  @Output() toggleUserMenu = new EventEmitter<void>();
  @Output() logout = new EventEmitter<void>();

  onNavigateTab(tab: string) {
    this.navigateTab.emit(tab);
  }

  onSearchInput() {
    this.searchQueryChange.emit(this.searchQuery);
    this.searchInput.emit(this.searchQuery);
  }

  executeGlobalSearch() {
    this.searchSubmit.emit(this.searchQuery);
  }

  onSelectSuggestion(s: any) {
    this.selectSuggestion.emit(s);
  }

  onClearSearch() {
    this.searchQuery = '';
    this.searchQueryChange.emit('');
    this.clearSearch.emit();
  }

  onToggleNotifications() {
    this.toggleNotifications.emit();
  }

  onMarkNotificationsRead() {
    this.markNotificationsRead.emit();
  }

  onToggleUserMenu() {
    this.toggleUserMenu.emit();
  }

  onLogout() {
    this.logout.emit();
  }

  getUserInitials(): string {
    if (!this.currentUser?.fullName) return 'TR';
    const parts = this.currentUser.fullName.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
}
