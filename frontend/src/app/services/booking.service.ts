import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class BookingService {
  private apiUrl = window.location.port === '4200' ? 'http://localhost:8080/api' : '/api';

  constructor(private http: HttpClient) {}

  createBooking(bookingData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/bookings`, bookingData);
  }

  getCustomerBookings(customerId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/bookings/customer/${customerId}`);
  }

  getBookingById(id: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/bookings/${id}`);
  }

  getBookingByRef(bookingRef: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/bookings/ref/${bookingRef}`);
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

  getPaymentConfig(): Observable<{ keyId: string; currency: string }> {
    return this.http.get<{ keyId: string; currency: string }>(`${this.apiUrl}/payments/config`);
  }

  getPaymentByBookingRef(bookingRef: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/payments/booking/${bookingRef}`);
  }

  createPaymentOrder(orderData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/payments/create-order`, orderData);
  }

  verifyPaymentSignature(verificationData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/payments/verify`, verificationData);
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
