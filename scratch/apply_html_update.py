# Update admin-dashboard.component.html
new_prefix = """<div class="admin-shell">
  <!-- ==================== TOP HEADER ==================== -->
  <header class="shell-header d-flex align-items-center justify-content-between px-4">
    <!-- Left: Brand Logo & Mobile Trigger -->
    <div class="d-flex align-items-center gap-3">
      <button 
        type="button" 
        class="btn btn-sm btn-light border-0 d-md-none text-muted" 
        (click)="toggleMobileSidebar()" 
        title="Toggle Menu"
      >
        <i class="fa-solid fa-bars fs-5"></i>
      </button>
      <a class="te-brand" routerLink="/admin" role="button" title="TravelEasy Admin Dashboard">
        <span class="te-logo-icon">
          <i class="fa-solid fa-paper-plane"></i>
        </span>
        <span class="te-brand-title">Travel<span class="te-brand-blue">Easy</span></span>
      </a>
    </div>

    <!-- Right: Notifications & Administrator Profile -->
    <div class="d-flex align-items-center gap-3">
      <!-- Notification Bell & Dropdown -->
      <div class="position-relative">
        <button 
          type="button" 
          class="btn btn-light rounded-circle position-relative p-2 border-0" 
          (click)="toggleNotifications($event)" 
          title="Notifications"
          style="width: 40px; height: 40px; display: flex; align-items: center; justify-content: center;"
        >
          <i class="fa-solid fa-bell text-secondary fs-5"></i>
          <span 
            *ngIf="((unreadCount$ | async) || 0) > 0" 
            class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger border border-white" 
            style="font-size: 10px;"
          >
            {{ ((unreadCount$ | async) || 0) > 99 ? '99+' : (unreadCount$ | async) }}
          </span>
        </button>

        <!-- Notifications Dropdown -->
        <div 
          *ngIf="isNotificationOpen" 
          class="notifications-dropdown position-absolute end-0 mt-2 bg-white shadow-lg rounded-4 border p-3" 
          style="width: 320px; z-index: 1050;"
          (click)="$event.stopPropagation()"
        >
          <div class="d-flex justify-content-between align-items-center mb-2 pb-2 border-bottom">
            <span class="fw-bold text-dark">Platform Notifications</span>
            <button 
              *ngIf="((unreadCount$ | async) || 0) > 0" 
              class="btn btn-link btn-sm text-primary p-0 text-decoration-none fw-semibold" 
              (click)="markAllNotificationsRead()"
            >
              Mark all as read
            </button>
          </div>
          <div class="py-3 text-center text-muted small" *ngIf="((unreadCount$ | async) || 0) === 0">
            <i class="fa-regular fa-bell-slash fs-4 d-block mb-1 opacity-50"></i>
            No new unread notifications
          </div>
          <div class="d-grid mt-2" *ngIf="((unreadCount$ | async) || 0) > 0">
            <button class="btn btn-outline-primary btn-sm rounded-pill" (click)="markAllNotificationsRead()">
              Mark all as read
            </button>
          </div>
        </div>
      </div>

      <!-- Administrator Profile Pill & Dropdown -->
      <div class="dropdown position-relative">
        <div 
          class="d-flex align-items-center gap-2 cursor-pointer p-1 rounded-pill pe-3 hover-bg"
          id="adminUserDropdown"
          (click)="toggleProfileMenu($event)" 
          role="button"
          aria-expanded="false"
        >
          <!-- Avatar -->
          <div class="operator-avatar">
            <span>{{ getInitials() }}</span>
          </div>
          <!-- Name & Role -->
          <div class="d-none d-sm-block text-start lh-sm">
            <div class="fw-bold text-dark small">{{ currentUser?.fullName || 'Platform Administrator' }}</div>
            <div class="text-muted" style="font-size: 11px;">Platform Administrator</div>
          </div>
          <i class="fa-solid fa-chevron-down text-muted ms-1" style="font-size: 11px;"></i>
        </div>
        <ul 
          *ngIf="isProfileOpen" 
          class="dropdown-menu dropdown-menu-end shadow-sm border-0 mt-2 rounded-3 show position-absolute end-0" 
          aria-labelledby="adminUserDropdown" 
          style="display: block; min-width: 220px; z-index: 1050;"
          (click)="$event.stopPropagation()"
        >
          <li>
            <div class="px-3 py-2 border-bottom">
              <div class="fw-bold text-dark small">{{ currentUser?.fullName || 'Platform Administrator' }}</div>
              <small class="text-muted">{{ currentUser?.email || 'admin@traveleasy.com' }}</small>
            </div>
          </li>
          <li>
            <a class="dropdown-item py-2 small" (click)="onSelectNavItem('settings')" role="button">
              <i class="fa-solid fa-user-gear me-2 text-primary"></i>Profile & Account
            </a>
          </li>
          <li>
            <a class="dropdown-item py-2 small" (click)="onSelectNavItem('analytics')" role="button">
              <i class="fa-solid fa-chart-line me-2 text-info"></i>Analytics & Reports
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

  <!-- ==================== BODY (SIDEBAR + MAIN CONTENT) ==================== -->
  <div class="admin-shell-body">
    <!-- Mobile Sidebar Backdrop -->
    <div 
      *ngIf="isMobileSidebarOpen" 
      class="sidebar-backdrop" 
      (click)="toggleMobileSidebar()"
    ></div>

    <!-- Collapsible Left Sidebar -->
    <aside 
      class="admin-sidebar py-3 px-2" 
      [class.collapsed]="isSidebarCollapsed" 
      [class.mobile-open]="isMobileSidebarOpen"
    >
      <!-- Sidebar Toggle Header -->
      <div class="d-flex align-items-center mb-3 px-2" [class.justify-content-center]="isSidebarCollapsed" [class.justify-content-between]="!isSidebarCollapsed">
        <span *ngIf="!isSidebarCollapsed" class="fw-bold text-uppercase text-muted" style="font-size: 11px; letter-spacing: 0.5px;">Navigation</span>
        <button 
          type="button" 
          class="btn btn-sm btn-light border-0 rounded-circle text-muted d-none d-md-flex align-items-center justify-content-center"
          style="width: 32px; height: 32px;"
          (click)="toggleSidebarCollapse()"
          [title]="isSidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        >
          <i class="fa-solid" [class.fa-angles-right]="isSidebarCollapsed" [class.fa-angles-left]="!isSidebarCollapsed"></i>
        </button>
      </div>

      <!-- Navigation Links -->
      <nav class="nav flex-column gap-1">
        <a 
          *ngFor="let item of navItems" 
          class="nav-link-custom" 
          [class.active]="activeNavItem === item.id"
          (click)="onSelectNavItem(item.id)"
          [title]="item.label"
          role="button"
        >
          <i [class]="item.icon + ' sidebar-icon'"></i>
          <span *ngIf="!isSidebarCollapsed">{{ item.label }}</span>
        </a>
      </nav>

      <!-- Sidebar Footer -->
      <div class="sidebar-footer mt-auto pt-3 border-top">
        <a 
          class="nav-link-custom" 
          [class.active]="activeNavItem === 'settings'"
          (click)="onSelectNavItem('settings')"
          title="Settings"
          role="button"
        >
          <i class="fa-solid fa-gear sidebar-icon"></i>
          <span *ngIf="!isSidebarCollapsed">Settings</span>
        </a>
      </div>
    </aside>

    <!-- Main Content Area -->
    <main class="dashboard-content">
"""

html_path = 'frontend/src/app/components/admin-dashboard/admin-dashboard.component.html'
with open(html_path, 'r', encoding='utf-8') as f:
    content = f.read()

target = '<main class="dashboard-content">'
pos = content.find(target)
assert pos != -1, "Target not found in html"

rest_of_file = content[pos + len(target):]
final_html = new_prefix + rest_of_file

with open(html_path, 'w', encoding='utf-8') as f:
    f.write(final_html)

print("admin-dashboard.component.html updated successfully!")
