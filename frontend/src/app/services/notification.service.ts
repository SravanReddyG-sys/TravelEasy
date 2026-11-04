import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, Subscription, timer, of } from 'rxjs';
import { map, catchError, switchMap, tap, filter } from 'rxjs/operators';
import { AuthService } from './auth.service';
import {
  InAppNotification,
  UnreadCountResponse,
  MarkAllReadResponse,
  FormattedNotification
} from '../models/notification.model';

@Injectable({
  providedIn: 'root'
})
export class NotificationService implements OnDestroy {
  private apiUrl = window.location.port === '4200'
    ? 'http://localhost:8080/api/notifications/in-app'
    : '/api/notifications/in-app';

  private notificationsSubject = new BehaviorSubject<FormattedNotification[]>([]);
  public notifications$ = this.notificationsSubject.asObservable();

  private unreadCountSubject = new BehaviorSubject<number>(0);
  public unreadCount$ = this.unreadCountSubject.asObservable();

  private isLoadingSubject = new BehaviorSubject<boolean>(false);
  public isLoading$ = this.isLoadingSubject.asObservable();

  private pollingSubscription?: Subscription;
  private authSubscription?: Subscription;
  private currentUserId: number | null = null;
  private isRequestInFlight = false;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {
    this.initAuthTracking();
  }

  ngOnDestroy(): void {
    this.stopPolling();
    if (this.authSubscription) {
      this.authSubscription.unsubscribe();
    }
  }

  private initAuthTracking(): void {
    this.authSubscription = this.authService.currentUser$.subscribe(user => {
      if (user && user.token) {
        if (this.currentUserId !== user.id) {
          this.currentUserId = user.id;
          this.clearState();
          this.refresh();
          this.startPolling();
        }
      } else {
        this.currentUserId = null;
        this.stopPolling();
        this.clearState();
      }
    });
  }

  /**
   * Starts periodic polling (default 30 seconds) for unread count and latest notifications.
   */
  public startPolling(intervalMs: number = 30000): void {
    this.stopPolling();

    // Use timer with guard against overlapping HTTP requests
    this.pollingSubscription = timer(intervalMs, intervalMs).pipe(
      filter(() => !this.isRequestInFlight && !!this.currentUserId),
      switchMap(() => {
        return this.fetchUnreadCount().pipe(
          catchError(() => of(0))
        );
      })
    ).subscribe();
  }

  /**
   * Stops active polling timer.
   */
  public stopPolling(): void {
    if (this.pollingSubscription) {
      this.pollingSubscription.unsubscribe();
      this.pollingSubscription = undefined;
    }
  }

  /**
   * Fetches notification history and updates reactive state.
   */
  public fetchNotifications(limit: number = 20): Observable<FormattedNotification[]> {
    if (!this.currentUserId) {
      return of([]);
    }

    this.isLoadingSubject.next(true);
    this.isRequestInFlight = true;

    return this.http.get<InAppNotification[]>(`${this.apiUrl}?limit=${limit}`).pipe(
      map(items => (items || []).map(item => this.formatNotification(item))),
      tap(formattedItems => {
        this.notificationsSubject.next(formattedItems);
        const unreadCount = formattedItems.filter(item => !item.isRead).length;
        this.unreadCountSubject.next(unreadCount);
        this.isLoadingSubject.next(false);
        this.isRequestInFlight = false;
      }),
      catchError(err => {
        this.isLoadingSubject.next(false);
        this.isRequestInFlight = false;
        console.warn('Failed to fetch notifications:', err.message || err);
        return of(this.notificationsSubject.value);
      })
    );
  }

  /**
   * Fetches the current unread count from the backend.
   */
  public fetchUnreadCount(): Observable<number> {
    if (!this.currentUserId) {
      return of(0);
    }

    return this.http.get<UnreadCountResponse>(`${this.apiUrl}/unread-count`).pipe(
      map(res => res?.unreadCount || 0),
      tap(count => this.unreadCountSubject.next(count)),
      catchError(err => {
        console.warn('Failed to fetch unread count:', err.message || err);
        return of(this.unreadCountSubject.value);
      })
    );
  }

  /**
   * Marks a single notification as read on the backend and updates local reactive state.
   */
  public markAsRead(notificationId: number): Observable<InAppNotification | null> {
    if (!notificationId) {
      return of(null);
    }

    return this.http.put<InAppNotification>(`${this.apiUrl}/${notificationId}/read`, {}).pipe(
      tap(updatedNotif => {
        const currentItems = this.notificationsSubject.value;
        const index = currentItems.findIndex(n => n.id === notificationId);
        if (index !== -1) {
          const updatedItems = [...currentItems];
          updatedItems[index] = this.formatNotification({
            ...updatedItems[index],
            isRead: true,
            readAt: updatedNotif.readAt || new Date().toISOString()
          });
          this.notificationsSubject.next(updatedItems);
          const currentCount = this.unreadCountSubject.value;
          this.unreadCountSubject.next(Math.max(0, currentCount - 1));
        }
      }),
      catchError(err => {
        console.error(`Failed to mark notification ${notificationId} as read:`, err);
        return of(null);
      })
    );
  }

  /**
   * Marks all notifications as read for the authenticated user.
   */
  public markAllAsRead(): Observable<MarkAllReadResponse | null> {
    return this.http.put<MarkAllReadResponse>(`${this.apiUrl}/read-all`, {}).pipe(
      tap(() => {
        const currentItems = this.notificationsSubject.value;
        const nowIso = new Date().toISOString();
        const updatedItems = currentItems.map(item => this.formatNotification({
          ...item,
          isRead: true,
          readAt: item.readAt || nowIso
        }));
        this.notificationsSubject.next(updatedItems);
        this.unreadCountSubject.next(0);
      }),
      catchError(err => {
        console.error('Failed to mark all notifications as read:', err);
        return of(null);
      })
    );
  }

  /**
   * Triggers an immediate refresh of both unread count and latest notifications.
   */
  public refresh(): void {
    if (this.currentUserId) {
      this.fetchNotifications().subscribe();
    }
  }

  /**
   * Clears in-memory notification state upon user logout or session switch.
   */
  public clearState(): void {
    this.notificationsSubject.next([]);
    this.unreadCountSubject.next(0);
    this.isLoadingSubject.next(false);
  }

  /**
   * Validates whether an actionUrl is a safe application-relative route.
   */
  public isSafeActionUrl(url?: string | null): boolean {
    if (!url || typeof url !== 'string') {
      return false;
    }
    const trimmed = url.trim();
    if (!trimmed.startsWith('/') || trimmed.startsWith('//')) {
      return false;
    }
    if (trimmed.includes('://') || trimmed.toLowerCase().includes('javascript:')) {
      return false;
    }
    return true;
  }

  /**
   * Converts a raw InAppNotification into a FormattedNotification with UI presentation metadata.
   */
  public formatNotification(raw: InAppNotification): FormattedNotification {
    return {
      ...raw,
      timeAgo: this.formatTimeAgo(raw.createdAt),
      iconClass: this.resolveEventIcon(raw.eventType),
      iconColorClass: this.resolveEventColor(raw.eventType),
      badgeClass: this.resolveEventBadge(raw.eventType)
    };
  }

  /**
   * Formats an ISO UTC timestamp into a human-readable relative duration.
   */
  public formatTimeAgo(dateStr?: string | null): string {
    if (!dateStr) return '';
    try {
      const date = new Date(dateStr);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      if (diffMs < 0 || isNaN(diffMs)) return 'Just now';

      const diffSecs = Math.floor(diffMs / 1000);
      const diffMins = Math.floor(diffSecs / 60);
      const diffHours = Math.floor(diffMins / 60);
      const diffDays = Math.floor(diffHours / 24);

      if (diffSecs < 60) return 'Just now';
      if (diffMins < 60) return `${diffMins}m ago`;
      if (diffHours < 24) return `${diffHours}h ago`;
      if (diffDays === 1) return 'Yesterday';
      if (diffDays < 7) return `${diffDays}d ago`;

      return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
    } catch {
      return '';
    }
  }

  private resolveEventIcon(eventType?: string): string {
    if (!eventType) return 'fa-solid fa-bell';
    switch (eventType) {
      case 'BUS_BOOKING_CONFIRMED':
        return 'fa-solid fa-circle-check';
      case 'BUS_BOOKING_CANCELLED':
        return 'fa-solid fa-circle-xmark';
      case 'PAYMENT_FAILED':
        return 'fa-solid fa-triangle-exclamation';
      case 'PAYMENT_REFUND_INITIATED':
        return 'fa-solid fa-money-bill-transfer';
      case 'PAYMENT_REFUND_COMPLETED':
        return 'fa-solid fa-receipt';
      case 'BUS_SCHEDULE_CREATED':
        return 'fa-solid fa-calendar-plus';
      case 'BUS_SCHEDULE_CANCELLED':
        return 'fa-solid fa-calendar-xmark';
      case 'BUS_DEACTIVATED':
        return 'fa-solid fa-ban';
      case 'BUS_ACTIVATED':
        return 'fa-solid fa-bus';
      default:
        return 'fa-solid fa-bell';
    }
  }

  private resolveEventColor(eventType?: string): string {
    if (!eventType) return 'text-primary';
    switch (eventType) {
      case 'BUS_BOOKING_CONFIRMED':
      case 'BUS_ACTIVATED':
        return 'text-success';
      case 'BUS_BOOKING_CANCELLED':
      case 'PAYMENT_FAILED':
        return 'text-danger';
      case 'PAYMENT_REFUND_INITIATED':
      case 'BUS_SCHEDULE_CANCELLED':
        return 'text-warning';
      case 'PAYMENT_REFUND_COMPLETED':
      case 'BUS_SCHEDULE_CREATED':
        return 'text-primary';
      case 'BUS_DEACTIVATED':
        return 'text-secondary';
      default:
        return 'text-primary';
    }
  }

  private resolveEventBadge(eventType?: string): string {
    if (!eventType) return 'bg-primary';
    switch (eventType) {
      case 'BUS_BOOKING_CONFIRMED':
      case 'BUS_ACTIVATED':
        return 'bg-success';
      case 'BUS_BOOKING_CANCELLED':
      case 'PAYMENT_FAILED':
        return 'bg-danger';
      case 'PAYMENT_REFUND_INITIATED':
      case 'BUS_SCHEDULE_CANCELLED':
        return 'bg-warning';
      case 'PAYMENT_REFUND_COMPLETED':
      case 'BUS_SCHEDULE_CREATED':
        return 'bg-primary';
      case 'BUS_DEACTIVATED':
        return 'bg-secondary';
      default:
        return 'bg-primary';
    }
  }
}
