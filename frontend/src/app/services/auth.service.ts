import { Injectable, Injector } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, take, tap } from 'rxjs';
import { User } from '../models/user.model';
import { ApplicationDetails } from '../models/application-details.model';
import { AdminUserStats, AdminPendingProvidersPage, AdminProviderDetails } from '../models/admin-dashboard.model';
import {
  AUTH_USER_STORAGE_KEY,
  readAndValidateStoredSession,
  resolveDashboardRoute,
  resolvePostLoginRoute,
  resolveRevalidatedSessionUser,
  validateStoredUserSession
} from '../shared/utils/auth-session.util';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = typeof window !== 'undefined' && window.location.port === '4200'
    ? 'http://localhost:8080/api'
    : '/api';
  private currentUserSubject = new BehaviorSubject<User | null>(this.getUserFromStorage());
  public currentUser$ = this.currentUserSubject.asObservable();
  private startupValidationStarted = false;

  constructor(private http: HttpClient, private injector: Injector) {
    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('storage', (event: StorageEvent) => {
        if (event.key === null || event.key === AUTH_USER_STORAGE_KEY) {
          this.restoreSession();
        }
      });
    }
  }

  private getStorage(): Storage | null {
    try {
      return typeof localStorage !== 'undefined' ? localStorage : null;
    } catch {
      return null;
    }
  }

  private getUserFromStorage(): User | null {
    return readAndValidateStoredSession(this.getStorage());
  }

  public get currentUserValue(): User | null {
    return this.currentUserSubject.value;
  }

  restoreSession(): User | null {
    const validatedUser = this.getUserFromStorage();
    const current = this.currentUserSubject.value;
    const sameUser =
      (!current && !validatedUser) ||
      (!!current &&
        !!validatedUser &&
        current.id === validatedUser.id &&
        current.role === validatedUser.role &&
        current.token === validatedUser.token &&
        current.status === validatedUser.status &&
        current.verificationStatus === validatedUser.verificationStatus);

    if (!sameUser) {
      this.currentUserSubject.next(validatedUser);
    }

    return validatedUser;
  }

  initializeSession(): User | null {
    const restoredUser = this.restoreSession();
    if (restoredUser && !this.startupValidationStarted) {
      this.startupValidationStarted = true;
      this.verifyRestoredSessionWithBackend(restoredUser);
    }
    return restoredUser;
  }

  getDashboardRoute(user: User | null | undefined = this.currentUserValue): string | null {
    return resolveDashboardRoute(user);
  }

  getPostLoginRoute(user: User, returnUrl?: string | null): string {
    return resolvePostLoginRoute(user, returnUrl);
  }

  private persistUserSession(user: User): void {
    const validated = validateStoredUserSession(user);
    const userToStore = validated.valid && validated.user ? validated.user : user;
    const storage = this.getStorage();
    if (storage) {
      storage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify(userToStore));
    }
    this.currentUserSubject.next(userToStore);
  }

  private clearStoredUserSession(): void {
    const storage = this.getStorage();
    if (storage) {
      storage.removeItem(AUTH_USER_STORAGE_KEY);
    }
    if (this.currentUserSubject.value !== null) {
      this.currentUserSubject.next(null);
    }
  }

  private verifyRestoredSessionWithBackend(restoredUser: User): void {
    if (!restoredUser.token) {
      return;
    }

    this.http.get<ApplicationDetails>(`${this.apiUrl}/auth/application-details`, {
      headers: {
        Authorization: `Bearer ${restoredUser.token}`
      }
    }).pipe(take(1)).subscribe({
      next: (details) => {
        if (this.currentUserSubject.value?.token !== restoredUser.token) {
          return;
        }
        const check = resolveRevalidatedSessionUser(restoredUser, details);
        if (!check.valid) {
          this.logout();
          const router = this.injector.get(Router);
          if (check.redirectTo && router.url !== check.redirectTo) {
            router.navigate([check.redirectTo]);
          }
          return;
        }
        if (check.updatedUser) {
          const nextUser = check.updatedUser;
          const metadataChanged =
            nextUser.fullName !== restoredUser.fullName ||
            nextUser.status !== restoredUser.status ||
            nextUser.verified !== restoredUser.verified ||
            nextUser.verificationStatus !== restoredUser.verificationStatus;
          if (metadataChanged) {
            this.persistUserSession(nextUser);
          }
        }
      },
      error: (err) => {
        if (this.currentUserSubject.value?.token !== restoredUser.token) {
          return;
        }
        if (err?.status === 401 || err?.status === 403 || err?.status === 404) {
          const errStatus = String(err?.error?.status || '').toUpperCase();
          this.logout();
          const router = this.injector.get(Router);
          if (errStatus === 'SUSPENDED') {
            router.navigate(['/account-suspended']);
          } else if (errStatus === 'REJECTED') {
            router.navigate(['/account-rejected']);
          } else {
            const currentPath = router.url.split('?')[0];
            if (currentPath !== '/login' && currentPath !== '/home') {
              router.navigate(['/login']);
            }
          }
        }
      }
    });
  }

  login(credentials: any): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/auth/login`, credentials).pipe(
      tap(user => {
        if (user && user.token && user.status !== 'PENDING' && user.verificationStatus !== 'UNDER_REVIEW') {
          this.persistUserSession(user);
        }
      })
    );
  }

  registerTraveller(userData: any): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/auth/register/traveller`, userData).pipe(
      tap(user => {
        if (user && user.token) {
          this.persistUserSession(user);
        }
      })
    );
  }

  registerBusOperator(userData: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/register/bus-operator`, userData).pipe(
      tap(res => {
        if (res) {
          if (res.applicationToken) {
            this.setPendingApplicationToken(res.applicationToken);
          }
          this.setPendingApplicationDetails(this.mapAuthResponseToApplicationDetails(res));
        }
      })
    );
  }

  registerHotelPartner(userData: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/register/hotel-partner`, userData).pipe(
      tap(res => {
        if (res) {
          if (res.applicationToken) {
            this.setPendingApplicationToken(res.applicationToken);
          }
          this.setPendingApplicationDetails(this.mapAuthResponseToApplicationDetails(res));
        }
      })
    );
  }

  setPendingApplicationToken(token: string): void {
    if (token) {
      localStorage.setItem('traveleasy_pending_token', token);
    }
  }

  getPendingApplicationToken(): string | null {
    return localStorage.getItem('traveleasy_pending_token');
  }

  setPendingApplicationDetails(details: ApplicationDetails): void {
    if (details) {
      localStorage.setItem('traveleasy_pending_application', JSON.stringify(details));
    }
  }

  getPendingApplicationDetails(): ApplicationDetails | null {
    const storage = this.getStorage();
    const data = storage?.getItem('traveleasy_pending_application');
    if (!data) {
      return null;
    }
    try {
      return JSON.parse(data);
    } catch {
      storage?.removeItem('traveleasy_pending_application');
      return null;
    }
  }

  clearPendingApplication(): void {
    localStorage.removeItem('traveleasy_pending_token');
    localStorage.removeItem('traveleasy_pending_application');
  }

  mapAuthResponseToApplicationDetails(res: any): ApplicationDetails {
    const orgName = res.legalOrganizationName || res.fullName || 'Not provided';
    const appType = res.role === 'ROLE_BUS_OPERATOR'
      ? 'New Bus Service Registration'
      : (res.role === 'ROLE_HOTEL_MANAGER' ? 'New Hotel Partner Registration' : 'Partner Registration');

    return {
      id: res.id,
      organizationName: orgName,
      legalOrganizationName: res.legalOrganizationName || '',
      applicationType: appType,
      role: res.role,
      submittedOn: res.createdAt || new Date().toISOString(),
      applicationStatus: res.verificationStatus || 'UNDER_REVIEW',
      accountStatus: res.status || 'PENDING',
      verified: !!res.verified,
      firstName: res.firstName || '',
      lastName: res.lastName || '',
      email: res.email || '',
      phone: res.phone || '',
      representativeDesignation: res.representativeDesignation || '',
      businessStructure: res.businessStructure || '',
      licensePermitNumber: res.licensePermitNumber || '',
      businessPanNumber: res.businessPanNumber || res.businessPan || '',
      gstinNumber: res.gstinNumber || res.gstin || '',
      registeredAddress: res.registeredAddress || '',
      businessContactEmail: res.businessContactEmail || '',
      businessContactPhone: res.businessContactPhone || ''
    };
  }

  getApplicationDetails(): Observable<ApplicationDetails> {
    const user = this.currentUserValue;
    const pendingToken = this.getPendingApplicationToken();
    const token = user?.token || pendingToken;

    const headers: { [key: string]: string } = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
      headers['X-Application-Token'] = token;
    }
    return this.http.get<ApplicationDetails>(
      `${this.apiUrl}/auth/application-details`,
      { headers }
    ).pipe(
      tap(details => {
        if (details) {
          this.setPendingApplicationDetails(details);
        }
      })
    );
  }

  forgotPassword(email: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/forgot-password`, { email });
  }

  resetPassword(data: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/reset-password`, data);
  }

  logout() {
    this.startupValidationStarted = false;
    this.clearStoredUserSession();
    this.clearPendingApplication();
  }

  getAllUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${this.apiUrl}/users`);
  }

  verifyProvider(userId: number, verified: boolean): Observable<any> {
    return this.http.put(`${this.apiUrl}/users/${userId}/verify?verified=${verified}`, {});
  }

  getUserStats(): Observable<AdminUserStats> {
    return this.http.get<AdminUserStats>(this.apiUrl + '/users/admin/stats');
  }

  getPendingProviders(page = 0, size = 10): Observable<AdminPendingProvidersPage> {
    return this.http.get<AdminPendingProvidersPage>(this.apiUrl + '/users/admin/pending-providers?page=' + page + '&size=' + size);
  }

  getProviderDetails(id: number): Observable<AdminProviderDetails> {
    return this.http.get<AdminProviderDetails>(this.apiUrl + '/users/admin/providers/' + id);
  }
}
