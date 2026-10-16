import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface NewsletterResponse {
  status: 'SUCCESS' | 'ALREADY_SUBSCRIBED' | 'INVALID_EMAIL' | 'UNSUBSCRIBED';
  message: string;
  email?: string;
}

@Injectable({
  providedIn: 'root'
})
export class NewsletterService {
  private apiUrl = window.location.port === '4200' ? 'http://localhost:8080/api/newsletter' : '/api/newsletter';

  constructor(private http: HttpClient) {}

  subscribe(email: string): Observable<NewsletterResponse> {
    return this.http.post<NewsletterResponse>(`${this.apiUrl}/subscribe`, { email });
  }
}
