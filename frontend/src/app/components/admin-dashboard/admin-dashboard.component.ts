import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Observable, Subscription, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../../services/auth.service';
import { BookingService, AdminPaymentStats, AdminDailyRevenueTrend, AdminReviewStats } from '../../services/booking.service';
import { NotificationService } from '../../services/notification.service';
import { User } from '../../models/user.model';
import {
  AdminUserStats,
  AdminBookingStats,
  AdminBookingTrend,
  AdminRecentBooking,
  AdminPendingProvider,
  AdminProviderDetails
} from '../../models/admin-dashboard.model';

interface ChartPoint {
  x: number;
  y: number;
  value: number;
  dateLabel: string;
  fullDate: string;
}

interface GridLine {
  y: number;
  label: string;
}

interface NavItem {
  id: string;
  label: string;
  icon: string;
  route?: string;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit, OnDestroy {
  // Navigation & Shell State
  isSidebarCollapsed = false;
  isMobileSidebarOpen = false;
  isNotificationOpen = false;
  isProfileOpen = false;
  activeNavItem = 'dashboard';
  currentUser: User | null = null;
  unreadCount$: Observable<number>;

  navItems: NavItem[] = [
    { id: 'dashboard', label: 'Dashboard', icon: 'fa-solid fa-house' },
    { id: 'users', label: 'User Management', icon: 'fa-solid fa-users' },
    { id: 'providers', label: 'Provider Management', icon: 'fa-solid fa-building' },
    { id: 'bookings', label: 'Booking Management', icon: 'fa-solid fa-ticket' },
    { id: 'payments', label: 'Payments & Refunds', icon: 'fa-solid fa-credit-card' },
    { id: 'reviews', label: 'Review Moderation', icon: 'fa-solid fa-star' },
    { id: 'buses', label: 'Bus Management', icon: 'fa-solid fa-bus', route: '/bus-operator' },
    { id: 'hotels', label: 'Hotel Management', icon: 'fa-solid fa-bed', route: '/hotel-manager' },
    { id: 'analytics', label: 'Analytics & Reports', icon: 'fa-solid fa-chart-column' },
  ];

  // Feedback Alerts
  actionFeedbackMessage: string | null = null;
  isActionSuccess = true;

  // KPI 1: Users
  userStats: AdminUserStats | null = null;
  loadingUsers = false;
  userError: string | null = null;

  // KPI 2: Bookings & Donut Breakdown
  bookingStats: AdminBookingStats | null = null;
  loadingBookings = false;
  bookingError: string | null = null;
  donutBusDashArray = '0 364.42';

  // KPI 3: Revenue
  paymentStats: AdminPaymentStats | null = null;
  loadingPayments = false;
  paymentError: string | null = null;

  // KPI 4: Reviews
  reviewStats: AdminReviewStats | null = null;
  loadingReviews = false;
  reviewError: string | null = null;

  // Booking Trends Line Chart
  trendMode: 'bookings' | 'revenue' = 'bookings';
  bookingTrends: AdminBookingTrend[] = [];
  revenueTrends: AdminDailyRevenueTrend[] = [];
  loadingTrends = false;
  trendsError: string | null = null;
  chartPoints: ChartPoint[] = [];
  gridLines: GridLine[] = [];
  svgLinePath = '';
  svgAreaPath = '';
  hoveredTrendIndex: number | null = null;

  // Pending Provider Verifications
  pendingProviders: AdminPendingProvider[] = [];
  loadingProviders = false;
  providersError: string | null = null;

  // Provider Detail Review Modal
  showProviderModal = false;
  selectedProvider: AdminProviderDetails | null = null;
  loadingProviderDetails = false;

  // Recent Bookings
  recentBookings: AdminRecentBooking[] = [];
  loadingRecentBookings = false;
  recentBookingsError: string | null = null;

  private userSub?: Subscription;

  constructor(
    private authService: AuthService,
    private bookingService: BookingService,
    private notificationService: NotificationService,
    private router: Router
  ) {
    this.unreadCount$ = this.notificationService.unreadCount$;
  }

  ngOnInit(): void {
    this.userSub = this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
    });

    this.loadAllDashboardData();
  }

  ngOnDestroy(): void {
    if (this.userSub) {
      this.userSub.unsubscribe();
    }
  }

  /**
   * Orchestrates independent parallel loading of all overview components.
   * If any single endpoint fails, the remainder of the dashboard continues to render cleanly.
   */
  loadAllDashboardData(): void {
    this.loadUserStats();
    this.loadBookingStats();
    this.loadPaymentStats();
    this.loadReviewStats();
    this.loadTrends();
    this.loadPendingProviders();
    this.loadRecentBookings();
  }

  // ==========================================
  // KPI Loaders
  // ==========================================

  loadUserStats(): void {
    this.loadingUsers = true;
    this.userError = null;
    this.authService.getUserStats().pipe(
      catchError(err => {
        this.userError = 'Unable to fetch user statistics';
        return of(null);
      })
    ).subscribe(res => {
      this.loadingUsers = false;
      if (res) {
        this.userStats = res;
      }
    });
  }

  loadBookingStats(): void {
    this.loadingBookings = true;
    this.bookingError = null;
    this.bookingService.getAdminBookingStats().pipe(
      catchError(err => {
        this.bookingError = 'Unable to fetch booking statistics';
        return of(null);
      })
    ).subscribe(res => {
      this.loadingBookings = false;
      if (res) {
        this.bookingStats = res;
        this.calculateDonutDash();
      }
    });
  }

  loadPaymentStats(): void {
    this.loadingPayments = true;
    this.paymentError = null;
    this.bookingService.getAdminPaymentStats().pipe(
      catchError(err => {
        this.paymentError = 'Unable to fetch payment statistics';
        return of(null);
      })
    ).subscribe(res => {
      this.loadingPayments = false;
      if (res) {
        this.paymentStats = res;
      }
    });
  }

  loadReviewStats(): void {
    this.loadingReviews = true;
    this.reviewError = null;
    this.bookingService.getAdminReviewStats().pipe(
      catchError(err => {
        this.reviewError = 'Unable to fetch review statistics';
        return of(null);
      })
    ).subscribe(res => {
      this.loadingReviews = false;
      if (res) {
        this.reviewStats = res;
      }
    });
  }

  // ==========================================
  // Booking Trends Chart
  // ==========================================

  loadTrends(): void {
    this.loadingTrends = true;
    this.trendsError = null;

    if (this.trendMode === 'bookings') {
      this.bookingService.getAdminBookingTrends(7).pipe(
        catchError(err => {
          this.trendsError = 'Unable to load booking trends';
          return of([]);
        })
      ).subscribe(res => {
        this.loadingTrends = false;
        this.bookingTrends = res || [];
        this.renderChart();
      });
    } else {
      this.bookingService.getAdminRevenueTrends(7).pipe(
        catchError(err => {
          this.trendsError = 'Unable to load revenue trends';
          return of([]);
        })
      ).subscribe(res => {
        this.loadingTrends = false;
        this.revenueTrends = res || [];
        this.renderChart();
      });
    }
  }

  setTrendMode(mode: 'bookings' | 'revenue'): void {
    if (this.trendMode === mode) return;
    this.trendMode = mode;
    this.loadTrends();
  }

  private renderChart(): void {
    this.chartPoints = [];
    this.gridLines = [];
    this.svgLinePath = '';
    this.svgAreaPath = '';

    const items = this.trendMode === 'bookings'
      ? this.bookingTrends.map(t => ({ date: t.date, val: t.bookingCount || 0 }))
      : this.revenueTrends.map(t => ({ date: t.date, val: t.netCollections || 0 }));

    if (items.length === 0) {
      return;
    }

    const padLeft = 60;
    const padRight = 560;
    const chartWidth = padRight - padLeft;
    const topY = 30;
    const botY = 195;
    const chartHeight = botY - topY;

    // Determine min and max scale
    const rawMax = Math.max(...items.map(i => i.val));
    const maxVal = rawMax <= 0 ? (this.trendMode === 'bookings' ? 10 : 1000) : rawMax * 1.15;
    const minVal = 0;

    // Create 4 Horizontal Gridlines & Y-axis labels
    const steps = 4;
    for (let s = 0; s <= steps; s++) {
      const stepVal = minVal + (maxVal - minVal) * (s / steps);
      const y = botY - (s / steps) * chartHeight;
      const label = this.trendMode === 'bookings'
        ? Math.round(stepVal).toString()
        : this.formatCompactINR(stepVal);
      this.gridLines.push({ y, label });
    }

    // Map each item to SVG coordinate
    const count = items.length;
    this.chartPoints = items.map((item, idx) => {
      const x = count === 1 ? (padLeft + chartWidth / 2) : padLeft + (idx / (count - 1)) * chartWidth;
      const normalized = Math.max(0, (item.val - minVal) / (maxVal - minVal));
      const y = botY - normalized * chartHeight;

      return {
        x,
        y,
        value: item.val,
        dateLabel: this.formatShortDate(item.date),
        fullDate: this.formatDisplayDate(item.date)
      };
    });

    if (this.chartPoints.length < 2) {
      if (this.chartPoints.length === 1) {
        const pt = this.chartPoints[0];
        this.svgLinePath = `M ${pt.x} ${pt.y}`;
      }
      return;
    }

    // Generate smooth Catmull-Rom or Cubic Spline path
    let lineD = `M ${this.chartPoints[0].x} ${this.chartPoints[0].y}`;
    for (let i = 0; i < this.chartPoints.length - 1; i++) {
      const p0 = i > 0 ? this.chartPoints[i - 1] : this.chartPoints[i];
      const p1 = this.chartPoints[i];
      const p2 = this.chartPoints[i + 1];
      const p3 = i < this.chartPoints.length - 2 ? this.chartPoints[i + 2] : p2;

      const cp1x = p1.x + (p2.x - p0.x) / 6;
      const cp1y = p1.y + (p2.y - p0.y) / 6;
      const cp2x = p2.x - (p3.x - p1.x) / 6;
      const cp2y = p2.y - (p3.y - p1.y) / 6;

      lineD += ` C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${p2.x} ${p2.y}`;
    }

    this.svgLinePath = lineD;

    // Generate gradient area path below the line
    const first = this.chartPoints[0];
    const last = this.chartPoints[this.chartPoints.length - 1];
    this.svgAreaPath = `${lineD} L ${last.x} ${botY} L ${first.x} ${botY} Z`;
  }

  getTooltipX(pointX: number): number {
    return Math.max(10, Math.min(480, pointX - 55));
  }

  getTooltipY(pointY: number): number {
    return Math.max(10, pointY - 55);
  }

  // ==========================================
  // Donut Calculation
  // ==========================================

  private calculateDonutDash(): void {
    const C = 2 * Math.PI * 58; // 364.42
    const busPct = this.bookingStats?.serviceBreakdown?.bus?.percentage || 0;
    const busDash = (busPct / 100) * C;
    this.donutBusDashArray = `${busDash} ${C}`;
  }

  // ==========================================
  // Pending Provider Verifications
  // ==========================================

  loadPendingProviders(): void {
    this.loadingProviders = true;
    this.providersError = null;
    this.authService.getPendingProviders(0, 5).pipe(
      catchError(err => {
        this.providersError = 'Unable to fetch pending provider verifications';
        return of({ content: [] });
      })
    ).subscribe(res => {
      this.loadingProviders = false;
      this.pendingProviders = res?.content || [];
    });
  }

  refreshProviders(): void {
    this.loadPendingProviders();
  }

  openProviderDetailsModal(providerId: number): void {
    this.showProviderModal = true;
    this.loadingProviderDetails = true;
    this.selectedProvider = null;

    this.authService.getProviderDetails(providerId).pipe(
      catchError(err => {
        this.actionFeedbackMessage = 'Failed to load provider application details.';
        this.isActionSuccess = false;
        this.showProviderModal = false;
        return of(null);
      })
    ).subscribe(details => {
      this.loadingProviderDetails = false;
      if (details) {
        this.selectedProvider = details;
      }
    });
  }

  closeProviderModal(): void {
    this.showProviderModal = false;
    this.selectedProvider = null;
  }

  executeModalVerification(approve: boolean): void {
    if (!this.selectedProvider) return;
    const id = this.selectedProvider.id;
    const name = this.selectedProvider.legalOrganizationName;

    this.authService.verifyProvider(id, approve).subscribe({
      next: () => {
        this.isActionSuccess = true;
        this.actionFeedbackMessage = approve
          ? `Provider "${name}" was successfully verified and approved.`
          : `Provider "${name}" verification was marked as rejected.`;
        this.closeProviderModal();
        this.loadPendingProviders();
        this.loadUserStats();
      },
      error: () => {
        this.isActionSuccess = false;
        this.actionFeedbackMessage = `Failed to update verification for provider "${name}".`;
      }
    });
  }

  approveProviderQuick(provider: AdminPendingProvider): void {
    if (confirm(`Approve verification for provider "${provider.legalOrganizationName}"?`)) {
      this.authService.verifyProvider(provider.id, true).subscribe({
        next: () => {
          this.isActionSuccess = true;
          this.actionFeedbackMessage = `Provider "${provider.legalOrganizationName}" has been successfully approved!`;
          this.loadPendingProviders();
          this.loadUserStats();
        },
        error: () => {
          this.isActionSuccess = false;
          this.actionFeedbackMessage = `Failed to approve provider "${provider.legalOrganizationName}".`;
        }
      });
    }
  }

  // ==========================================
  // Recent Bookings
  // ==========================================

  loadRecentBookings(): void {
    this.loadingRecentBookings = true;
    this.recentBookingsError = null;

    this.bookingService.getAdminRecentBookings(5).pipe(
      catchError(err => {
        this.recentBookingsError = 'Unable to fetch recent platform bookings';
        return of([]);
      })
    ).subscribe(res => {
      this.loadingRecentBookings = false;
      this.recentBookings = res || [];
    });
  }

  refreshRecentBookings(): void {
    this.loadRecentBookings();
  }

  // ==========================================
  // Navigation & UI Helpers
  // ==========================================

  toggleSidebarCollapse(): void {
    this.isSidebarCollapsed = !this.isSidebarCollapsed;
  }

  toggleMobileSidebar(): void {
    this.isMobileSidebarOpen = !this.isMobileSidebarOpen;
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    this.isNotificationOpen = false;
    this.isProfileOpen = false;
  }

  getInitials(): string {
    if (!this.currentUser?.fullName) return 'PA';
    const parts = this.currentUser.fullName.trim().split(' ');
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return parts[0].substring(0, 2).toUpperCase();
  }

  toggleNotifications(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.isNotificationOpen = !this.isNotificationOpen;
    if (this.isNotificationOpen) {
      this.isProfileOpen = false;
    }
  }

  toggleProfileMenu(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.isProfileOpen = !this.isProfileOpen;
    if (this.isProfileOpen) {
      this.isNotificationOpen = false;
    }
  }

  onSelectNavItem(id: string): void {
    this.activeNavItem = id;
    if (this.isMobileSidebarOpen) {
      this.isMobileSidebarOpen = false;
    }

    const item = this.navItems.find(n => n.id === id);
    if (item?.route) {
      this.router.navigate([item.route]);
    } else if (id === 'providers') {
      this.scrollToSection('pending-providers-section');
    } else if (id === 'bookings') {
      this.scrollToSection('recent-bookings-section');
    }
  }

  scrollToSection(elementId: string): void {
    const el = document.getElementById(elementId);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  navigateOrScroll(route: string): void {
    this.router.navigate([route]);
  }

  markAllNotificationsRead(): void {
    this.notificationService.markAllAsRead().subscribe();
    this.isNotificationOpen = false;
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  // ==========================================
  // Value Formatting
  // ==========================================

  formatINR(amount: number): string {
    return '₹' + Number(amount || 0).toLocaleString('en-IN', {
      maximumFractionDigits: 0
    });
  }

  formatCompactINR(val: number): string {
    if (val >= 10000000) {
      return '₹' + (val / 10000000).toFixed(1) + 'Cr';
    }
    if (val >= 100000) {
      return '₹' + (val / 100000).toFixed(1) + 'L';
    }
    if (val >= 1000) {
      return '₹' + (val / 1000).toFixed(0) + 'k';
    }
    return '₹' + Math.round(val);
  }

  formatShortDate(d: string | null | undefined): string {
    if (!d) return '';
    try {
      const dt = new Date(d);
      return dt.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    } catch {
      return String(d);
    }
  }

  formatDisplayDate(d: string | null | undefined): string {
    if (!d) return 'N/A';
    try {
      const dt = new Date(d);
      return dt.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    } catch {
      return String(d);
    }
  }
}
