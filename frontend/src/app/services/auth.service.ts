import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { User } from '../models/user.model';
import { ApplicationDetails } from '../models/application-details.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = window.location.port === '4200' ? 'http://localhost:8080/api' : '/api';
  private currentUserSubject = new BehaviorSubject<User | null>(this.getUserFromStorage());
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {}

  private getUserFromStorage(): User | null {
    const data = localStorage.getItem('traveleasy_user');
    return data ? JSON.parse(data) : null;
  }

  public get currentUserValue(): User | null {
    return this.currentUserSubject.value;
  }

  login(credentials: any): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/auth/login`, credentials).pipe(
      tap(user => {
        if (user) {
          localStorage.setItem('traveleasy_user', JSON.stringify(user));
          this.currentUserSubject.next(user);
        }
      })
    );
  }

  registerTraveller(userData: any): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/auth/register/traveller`, userData).pipe(
      tap(user => {
        if (user && user.token) {
          localStorage.setItem('traveleasy_user', JSON.stringify(user));
          this.currentUserSubject.next(user);
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
    const data = localStorage.getItem('traveleasy_pending_application');
    return data ? JSON.parse(data) : null;
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
    localStorage.removeItem('traveleasy_user');
    this.clearPendingApplication();
    this.currentUserSubject.next(null);
  }

  getAllUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${this.apiUrl}/users`);
  }

  verifyProvider(userId: number, verified: boolean): Observable<any> {
    return this.http.put(`${this.apiUrl}/users/${userId}/verify?verified=${verified}`, {});
  }
}
