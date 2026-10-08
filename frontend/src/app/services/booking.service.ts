import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class BookingService {
  private apiUrl = '/api';

  constructor(private http: HttpClient) {}

  createBooking(bookingData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/bookings`, bookingData);
  }

  getCustomerBookings(customerId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/bookings/customer/${customerId}`);
  }

  getProviderBookings(providerId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/bookings/provider/${providerId}`);
  }

  getAllBookings(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/bookings/admin/all`);
  }

  confirmBooking(bookingId: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/bookings/${bookingId}/confirm`, {});
  }

  cancelBooking(bookingId: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/bookings/${bookingId}/cancel`, {});
  }

  processPayment(paymentData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/payments/process`, paymentData);
  }

  submitReview(reviewData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/reviews`, reviewData);
  }

  getReviews(serviceType: string, serviceId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/reviews/service/${serviceType}/${serviceId}`);
  }

  getAllReviews(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/reviews/admin/all`);
  }

  deleteReview(reviewId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/reviews/${reviewId}`);
  }
}
