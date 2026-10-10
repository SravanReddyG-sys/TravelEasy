import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { User } from '../models/user.model';

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
        if (user && user.token) {
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

  registerBusOperator(userData: any): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/auth/register/bus-operator`, userData);
  }

  registerHotelPartner(userData: any): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/auth/register/hotel-partner`, userData);
  }

  forgotPassword(email: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/forgot-password`, { email });
  }

  resetPassword(data: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/reset-password`, data);
  }

  logout() {
    localStorage.removeItem('traveleasy_user');
    this.currentUserSubject.next(null);
  }

  getAllUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${this.apiUrl}/users`);
  }

  verifyProvider(userId: number, verified: boolean): Observable<any> {
    return this.http.put(`${this.apiUrl}/users/${userId}/verify?verified=${verified}`, {});
  }
}
