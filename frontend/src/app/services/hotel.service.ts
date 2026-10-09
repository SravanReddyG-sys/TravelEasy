import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class HotelService {
  private apiUrl = window.location.port === '4200' ? 'http://localhost:8080/api/hotels' : '/api/hotels';

  constructor(private http: HttpClient) {}

  addHotel(hotelData: any): Observable<any> {
    return this.http.post(this.apiUrl, hotelData);
  }

  getHotelsByManager(managerId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/manager/${managerId}`);
  }

  addRoomType(hotelId: number, roomData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/${hotelId}/rooms`, roomData);
  }

  searchHotels(city: string, checkIn?: string, checkOut?: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/search?city=${encodeURIComponent(city)}`);
  }

  getHotelDetails(hotelId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/${hotelId}`);
  }

  reserveRooms(roomId: number, count: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/rooms/${roomId}/reserve?count=${count}`, {});
  }
}
