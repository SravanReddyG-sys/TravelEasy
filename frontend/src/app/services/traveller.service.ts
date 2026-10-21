import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class TravellerService {
  private apiUrl = window.location.port === '4200' ? 'http://localhost:8080/api/traveller' : '/api/traveller';

  constructor(private http: HttpClient) {}

  getDashboard(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/dashboard`);
  }

  getProfile(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/profile`);
  }

  updateProfile(data: any): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/profile`, data);
  }

  getPreferences(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/preferences`);
  }

  updatePreferences(data: any): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/preferences`, data);
  }

  getFavourites(type?: string): Observable<any[]> {
    const url = type ? `${this.apiUrl}/favourites?type=${type}` : `${this.apiUrl}/favourites`;
    return this.http.get<any[]>(url);
  }

  addFavourite(data: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/favourites`, data);
  }

  deleteFavourite(id: number): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/favourites/${id}`);
  }

  getSupportTickets(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/support/tickets`);
  }

  createSupportTicket(data: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/support/tickets`, data);
  }
}
