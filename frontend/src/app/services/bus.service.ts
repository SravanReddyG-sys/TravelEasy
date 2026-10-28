import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TripSeatLayout, ScheduleStop } from '../models/bus.model';

@Injectable({
  providedIn: 'root'
})
export class BusService {
  private apiUrl = window.location.port === '4200' ? 'http://localhost:8080/api/buses' : '/api/buses';

  constructor(private http: HttpClient) {}

  addBus(busData: any): Observable<any> {
    return this.http.post(this.apiUrl, busData);
  }

  getAllBuses(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl);
  }

  getBusesByOperator(operatorId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/operator/${operatorId}`);
  }

  getOperatorSchedules(operatorId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/operator/${operatorId}/schedules`);
  }

  deactivateBus(busId: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/${busId}/deactivate`, {});
  }

  activateBus(busId: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/${busId}/activate`, {});
  }

  getTemplates(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/templates`);
  }

  getTemplatesByConfiguration(config: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/templates/by-configuration/${config}`);
  }

  getScheduleLayout(scheduleId: number, fromOrder?: number | null, toOrder?: number | null): Observable<TripSeatLayout> {
    let url = `${this.apiUrl}/schedules/${scheduleId}/layout`;
    const params: string[] = [];
    if (fromOrder !== undefined && fromOrder !== null) params.push(`fromOrder=${fromOrder}`);
    if (toOrder !== undefined && toOrder !== null) params.push(`toOrder=${toOrder}`);
    if (params.length > 0) url += `?${params.join('&')}`;
    return this.http.get<TripSeatLayout>(url);
  }

  getScheduleStops(scheduleId: number): Observable<ScheduleStop[]> {
    return this.http.get<ScheduleStop[]>(`${this.apiUrl}/schedules/${scheduleId}/stops`);
  }

  createSchedule(scheduleData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/schedules`, scheduleData);
  }

  searchBuses(origin: string, destination: string, date: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/search?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}&date=${date}`);
  }

  getScheduleDetails(scheduleId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/schedules/${scheduleId}`);
  }

  bookSeats(scheduleId: number, seatNumbers: string[], bookingRef?: string, fromOrder?: number | null, toOrder?: number | null): Observable<any> {
    let url = `${this.apiUrl}/schedules/${scheduleId}/book-seats`;
    const params: string[] = [];
    if (bookingRef) params.push(`bookingRef=${encodeURIComponent(bookingRef)}`);
    if (fromOrder !== undefined && fromOrder !== null) params.push(`fromOrder=${fromOrder}`);
    if (toOrder !== undefined && toOrder !== null) params.push(`toOrder=${toOrder}`);
    if (params.length > 0) url += `?${params.join('&')}`;
    return this.http.put(url, seatNumbers);
  }

  confirmSeats(scheduleId: number, bookingRef?: string, seatNumbers?: string[]): Observable<any> {
    let url = `${this.apiUrl}/schedules/${scheduleId}/confirm-seats`;
    if (bookingRef) url += `?bookingRef=${encodeURIComponent(bookingRef)}`;
    return this.http.put(url, seatNumbers || []);
  }

  releaseSeats(scheduleId: number, seatNumbers?: string[], bookingRef?: string): Observable<any> {
    let url = `${this.apiUrl}/schedules/${scheduleId}/release-seats`;
    if (bookingRef) url += `?bookingRef=${encodeURIComponent(bookingRef)}`;
    return this.http.put(url, seatNumbers || []);
  }

  // ==================== ROUTE MANAGEMENT ====================

  createRoute(routeData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/routes`, routeData);
  }

  getRoutes(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/routes`);
  }

  getRouteDetails(routeId: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/routes/${routeId}`);
  }

  updateRoute(routeId: number, routeData: any): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/routes/${routeId}`, routeData);
  }

  updateRouteStatus(routeId: number, status: string): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/routes/${routeId}/status`, { status });
  }

  getRouteSchedules(routeId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/routes/${routeId}/schedules`);
  }
}
