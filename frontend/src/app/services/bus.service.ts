import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class BusService {
  private apiUrl = '/api/buses';

  constructor(private http: HttpClient) {}

  addBus(busData: any): Observable<any> {
    return this.http.post(this.apiUrl, busData);
  }

  getBusesByOperator(operatorId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/operator/${operatorId}`);
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

  bookSeats(scheduleId: number, seatNumbers: string[]): Observable<any> {
    return this.http.put(`${this.apiUrl}/schedules/${scheduleId}/book-seats`, seatNumbers);
  }
}
