import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminBookingStats, AdminBookingTrend, AdminRecentBooking } from '../models/admin-dashboard.model';

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

  cancelOperatorSchedule(scheduleId: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/bookings/operator/schedules/${scheduleId}/cancel`, {});
  }

  getOperatorScheduleCancellationStatus(scheduleId: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/bookings/operator/schedules/${scheduleId}/cancellation-status`);
  }

  getAdminBookingStats(): Observable<AdminBookingStats> {
    return this.http.get<AdminBookingStats>(this.apiUrl + '/bookings/admin/stats');
  }

  getAdminBookingTrends(days = 7): Observable<AdminBookingTrend[]> {
    return this.http.get<AdminBookingTrend[]>(this.apiUrl + '/bookings/admin/trends?days=' + days);
  }

  getAdminRecentBookings(limit = 5): Observable<AdminRecentBooking[]> {
    return this.http.get<AdminRecentBooking[]>(this.apiUrl + '/bookings/admin/recent?limit=' + limit);
  }

  getAdminPaymentStats(): Observable<AdminPaymentStats> {
    return this.http.get<AdminPaymentStats>(this.apiUrl + '/payments/admin/stats');
  }

  getAdminRevenueTrends(days = 7): Observable<AdminDailyRevenueTrend[]> {
    return this.http.get<AdminDailyRevenueTrend[]>(this.apiUrl + '/payments/admin/revenue-trends?days=' + days);
  }

  getAdminReviewStats(): Observable<AdminReviewStats> {
    return this.http.get<AdminReviewStats>(this.apiUrl + '/reviews/admin/stats');
  }

  getAdminPendingReviews(page = 0, size = 10): Observable<AdminPendingReviewsPage> {
    return this.http.get<AdminPendingReviewsPage>(this.apiUrl + '/reviews/admin/pending?page=' + page + '&size=' + size);
  }
}


export interface AdminPaymentStats {
  currency: string;
  successfulCapturedCollections: number;
  processedRefunds: number;
  netCollections: number;
}

export interface AdminDailyRevenueTrend {
  date: string;
  capturedCollections: number;
  processedRefunds: number;
  netCollections: number;
  currency: string;
}


export interface AdminReviewStats {
  totalReviews: number;
  pendingReviews: number;
  approvedReviews: number;
  rejectedReviews: number;
}

export interface AdminPendingReview {
  id: number;
  customerName: string;
  customerId: number;
  serviceType: string;
  serviceId: number;
  bookingRef: string;
  rating: number;
  title: string | null;
  comment: string;
  createdAt: string;
  status: string | null;
}

export interface AdminPendingReviewsPage {
  content: AdminPendingReview[];
  pageable?: any;
  totalPages: number;
  totalElements: number;
  last: boolean;
  size: number;
  number: number;
  sort?: any;
  numberOfElements: number;
  first: boolean;
  empty: boolean;
}
