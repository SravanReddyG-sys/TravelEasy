import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { TripSeatLayout, ScheduleStop, Bus, ScheduleFareMatrixResponse, FareOverrideItem } from '../models/bus.model';

@Injectable({
  providedIn: 'root'
})
export class BusService {
  private apiUrl = window.location.port === '4200' ? 'http://localhost:8080/api/buses' : '/api/buses';

  constructor(private http: HttpClient) {}

  addBus(busData: any): Observable<any> {
    return this.http.post(this.apiUrl, busData);
  }

  uploadBusImage(file: File, category: string): Observable<{ filename: string; imageUrl: string; category: string; size: number }> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('category', category);
    return this.http.post<{ filename: string; imageUrl: string; category: string; size: number }>(
      `${this.apiUrl}/images/upload`,
      formData
    );
  }

  deleteBusImage(filename: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/images/${encodeURIComponent(filename)}`);
  }

  checkBusRegistration(busNumber: string, excludeBusId?: number): Observable<{ busNumber: string; normalized: string; validFormat: boolean; available: boolean; message: string }> {
    let url = `${this.apiUrl}/check-registration?busNumber=${encodeURIComponent(busNumber)}`;
    if (excludeBusId) {
      url += `&excludeBusId=${excludeBusId}`;
    }
    return this.http.get<{ busNumber: string; normalized: string; validFormat: boolean; available: boolean; message: string }>(url);
  }

  validateBusBasicDetails(busData: any, checkImages: boolean = true): Observable<any> {
    return this.http.post(`${this.apiUrl}/validate-basic-details?checkImages=${checkImages}`, busData);
  }

  getAllBuses(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl);
  }

  getBusesByOperator(operatorId: number): Observable<Bus[]> {
    return this.http.get<any[]>(`${this.apiUrl}/operator/${operatorId}`).pipe(
      map(buses => (buses || []).map(b => this.normalizeBus(b)))
    );
  }

  normalizeBus(b: any): Bus {
    const templateId = b.templateId || '';
    let layoutSummary = '--';
    if (templateId.includes('2+1')) {
      layoutSummary = '2 + 1';
    } else if (templateId.includes('2+2')) {
      layoutSummary = '2 + 2';
    } else if (templateId.includes('1+1')) {
      layoutSummary = '1 + 1';
    } else if (templateId.includes('3+2')) {
      layoutSummary = '3 + 2';
    } else if (b.busType) {
      if (b.busType.includes('2+1') || b.busType.includes('2 + 1')) layoutSummary = '2 + 1';
      else if (b.busType.includes('2+2') || b.busType.includes('2 + 2')) layoutSummary = '2 + 2';
      else if (b.busType.includes('1+1') || b.busType.includes('1 + 1')) layoutSummary = '1 + 1';
      else if (b.busType.includes('3+2') || b.busType.includes('3 + 2')) layoutSummary = '3 + 2';
    }

    let manufacturer = b.manufacturer;
    if (!manufacturer || manufacturer === 'Not available') {
      const text = `${b.busType || ''} ${b.busName || ''}`;
      if (/volvo/i.test(text)) manufacturer = 'Volvo';
      else if (/scania/i.test(text)) manufacturer = 'Scania';
      else if (/bharatbenz/i.test(text)) manufacturer = 'BharatBenz';
      else if (/ashok leyland/i.test(text)) manufacturer = 'Ashok Leyland';
      else if (/mercedes/i.test(text)) manufacturer = 'Mercedes-Benz';
      else if (/tata/i.test(text)) manufacturer = 'Tata';
      else manufacturer = 'Not available';
    }

    let model = b.model;
    if (!model || model === 'Not available') {
      const text = `${b.busType || ''} ${b.busName || ''}`;
      if (/9600/i.test(text)) model = '9600';
      else if (/b11r/i.test(text)) model = 'B11R';
      else if (/metrolink/i.test(text)) model = 'Metrolink';
      else if (/1624/i.test(text)) model = '1624';
      else if (/viking/i.test(text)) model = 'Viking';
      else if (/multi-axle/i.test(text)) model = 'Multi-Axle';
      else model = 'Not available';
    }

    let amenitiesList: string[] = [];
    if (Array.isArray(b.amenities)) {
      amenitiesList = b.amenities;
    } else if (typeof b.amenities === 'string' && b.amenities.trim().length > 0) {
      amenitiesList = b.amenities.split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
    } else {
      amenitiesList = b.facilityType === 'NON_AC' ? ['Charging', 'Water Bottle'] : ['AC', 'Wi-Fi', 'Charging', 'Blanket'];
    }

    return {
      ...b,
      capacity: b.totalSeats || b.capacity || 0,
      totalSeats: b.totalSeats || b.capacity || 0,
      status: b.status || 'ACTIVE',
      busName: b.busName || (b.operatorName ? `${b.operatorName} ${manufacturer !== 'Not available' ? manufacturer : (b.busType || 'Coach')}` : b.busType),
      manufacturer,
      model,
      yearOfManufacturing: b.yearOfManufacturing || b.modelYear || 2022,
      modelYear: b.yearOfManufacturing || b.modelYear || 2022,
      fuelType: b.fuelType || 'Diesel',
      imageUrl: b.imageUrl || b.exteriorImageUrl || 'assets/images/travel-easy-bus.png',
      exteriorImageUrl: b.exteriorImageUrl || b.imageUrl || '',
      interiorImageUrl: b.interiorImageUrl || '',
      chassisNumber: b.chassisNumber || '',
      engineNumber: b.engineNumber || '',
      emissionStandard: b.emissionStandard || '',
      color: b.color || '',
      busCategory: b.busCategory || '',
      layoutSummary,
      amenities: amenitiesList
    };
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

  checkScheduleConflict(request: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/schedules/check-conflict`, request);
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

  getScheduleFareMatrix(scheduleId: number): Observable<ScheduleFareMatrixResponse> {
    return this.http.get<ScheduleFareMatrixResponse>(`${this.apiUrl}/schedules/${scheduleId}/fare-matrix`);
  }

  previewFareMatrix(request: { stops: any[]; overrides?: any[] }): Observable<ScheduleFareMatrixResponse> {
    return this.http.post<ScheduleFareMatrixResponse>(`${this.apiUrl}/schedules/fare-matrix/preview`, request);
  }

  updateScheduleFareOverrides(scheduleId: number, overrides: FareOverrideItem[]): Observable<ScheduleFareMatrixResponse> {
    return this.http.put<ScheduleFareMatrixResponse>(`${this.apiUrl}/schedules/${scheduleId}/fare-matrix/overrides`, overrides);
  }

  clearScheduleFareOverrides(scheduleId: number): Observable<ScheduleFareMatrixResponse> {
    return this.http.delete<ScheduleFareMatrixResponse>(`${this.apiUrl}/schedules/${scheduleId}/fare-matrix/overrides`);
  }

  getSegmentFare(scheduleId: number, fromOrder?: number, toOrder?: number, fromStop?: string, toStop?: string): Observable<any> {
    let url = `${this.apiUrl}/schedules/${scheduleId}/fare?`;
    const params: string[] = [];
    if (fromOrder !== undefined && fromOrder !== null) params.push(`fromOrder=${fromOrder}`);
    if (toOrder !== undefined && toOrder !== null) params.push(`toOrder=${toOrder}`);
    if (fromStop) params.push(`fromStop=${encodeURIComponent(fromStop)}`);
    if (toStop) params.push(`toStop=${encodeURIComponent(toStop)}`);
    return this.http.get<any>(url + params.join('&'));
  }
}
