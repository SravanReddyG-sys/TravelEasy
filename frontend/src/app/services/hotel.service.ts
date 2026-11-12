import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { HotelPublicDto, HotelDetailsResponseDto } from '../models/hotel.model';

@Injectable({
  providedIn: 'root'
})
export class HotelService {
  private apiUrl = window.location.port === '4200' ? 'http://localhost:8080/api/hotels' : '/api/hotels';

  constructor(private http: HttpClient) {}

  addHotel(hotelData: any): Observable<any> {
    return this.http.post(this.apiUrl, hotelData);
  }

  updateHotel(hotelId: number, hotelData: any): Observable<any> {
    return this.http.put(`${this.apiUrl}/${hotelId}`, hotelData);
  }

  updateHotelFull(hotelId: number, updateData: any): Observable<any> {
    return this.http.put(`${this.apiUrl}/${hotelId}/manage`, updateData);
  }

  getHotelForManagement(hotelId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/manage/${hotelId}`);
  }

  getMyHotels(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/my-hotels`);
  }

  getHotelsByManager(managerId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/manager/${managerId}`);
  }

  updateHotelStatus(hotelId: number, status: string): Observable<any> {
    return this.http.put(`${this.apiUrl}/${hotelId}/status?status=${encodeURIComponent(status)}`, {});
  }

  addRoomType(hotelId: number, roomData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/${hotelId}/rooms`, roomData);
  }

  getRoomTypes(hotelId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/${hotelId}/rooms`);
  }

  getRoomType(roomId: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/rooms/${roomId}`);
  }

  updateRoomType(roomId: number, roomData: any): Observable<any> {
    return this.http.put(`${this.apiUrl}/rooms/${roomId}`, roomData);
  }

  archiveRoomType(roomId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/rooms/${roomId}`);
  }

  restoreRoomType(roomId: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/rooms/${roomId}/restore`, {});
  }

  searchHotels(city: string, checkIn?: string, checkOut?: string, rooms: number = 1, guests?: number): Observable<HotelPublicDto[]> {
    let url = `${this.apiUrl}/search?city=${encodeURIComponent(city)}`;
    if (checkIn && checkOut) {
      url += `&checkIn=${encodeURIComponent(checkIn)}&checkOut=${encodeURIComponent(checkOut)}&rooms=${rooms}`;
    }
    if (guests !== undefined && guests !== null && guests > 0) {
      url += `&guests=${guests}`;
    }
    return this.http.get<HotelPublicDto[]>(url);
  }

  getHotelDetails(hotelId: number, checkIn?: string, checkOut?: string, rooms: number = 1): Observable<HotelDetailsResponseDto> {
    let url = `${this.apiUrl}/${hotelId}`;
    if (checkIn && checkOut) {
      url += `?checkIn=${encodeURIComponent(checkIn)}&checkOut=${encodeURIComponent(checkOut)}&rooms=${rooms}`;
    }
    return this.http.get<HotelDetailsResponseDto>(url);
  }

  reserveRooms(roomId: number, count: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/rooms/${roomId}/reserve?count=${count}`, {});
  }

  releaseRooms(roomId: number, count: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/rooms/${roomId}/release?count=${count}`, {});
  }

  // --- WP-03: DATE-WISE INVENTORY, PRICING & AVAILABILITY ---

  reserveStay(roomId: number, reserveData: { bookingRef: string; checkInDate: string; checkOutDate: string; count?: number }): Observable<any> {
    return this.http.post(`${this.apiUrl}/rooms/${roomId}/reserve-stay`, reserveData);
  }

  releaseStay(bookingRef: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/rooms/release-stay?bookingRef=${encodeURIComponent(bookingRef)}`, {});
  }

  confirmStay(bookingRef: string): Observable<any> {
    return this.http.put(`${this.apiUrl}/rooms/confirm-stay?bookingRef=${encodeURIComponent(bookingRef)}`, {});
  }

  checkStayAvailability(hotelId: number, checkIn: string, checkOut: string, rooms: number = 1): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/${hotelId}/availability?checkIn=${encodeURIComponent(checkIn)}&checkOut=${encodeURIComponent(checkOut)}&rooms=${rooms}`);
  }

  checkRoomStayAvailability(roomId: number, checkIn: string, checkOut: string, rooms: number = 1): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/rooms/${roomId}/availability?checkIn=${encodeURIComponent(checkIn)}&checkOut=${encodeURIComponent(checkOut)}&rooms=${rooms}`);
  }

  updateBasePrice(roomId: number, basePrice: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/rooms/${roomId}/base-price`, { basePrice });
  }

  setPriceOverride(roomId: number, overrideData: { overrideDate: string; pricePerNight: number }): Observable<any> {
    return this.http.post(`${this.apiUrl}/rooms/${roomId}/pricing/override`, overrideData);
  }

  setPriceOverrideRange(roomId: number, rangeData: { startDate: string; endDate: string; pricePerNight: number }): Observable<any> {
    return this.http.post(`${this.apiUrl}/rooms/${roomId}/pricing/override-range`, rangeData);
  }

  deletePriceOverride(roomId: number, date: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/rooms/${roomId}/pricing/override?date=${encodeURIComponent(date)}`);
  }

  getPriceCalendar(roomId: number, startDate?: string, endDate?: string): Observable<any[]> {
    let url = `${this.apiUrl}/rooms/${roomId}/pricing/calendar`;
    const params: string[] = [];
    if (startDate) params.push(`startDate=${encodeURIComponent(startDate)}`);
    if (endDate) params.push(`endDate=${encodeURIComponent(endDate)}`);
    if (params.length > 0) url += `?${params.join('&')}`;
    return this.http.get<any[]>(url);
  }

  getInventoryCalendar(roomId: number, startDate?: string, endDate?: string): Observable<any[]> {
    let url = `${this.apiUrl}/rooms/${roomId}/inventory/calendar`;
    const params: string[] = [];
    if (startDate) params.push(`startDate=${encodeURIComponent(startDate)}`);
    if (endDate) params.push(`endDate=${encodeURIComponent(endDate)}`);
    if (params.length > 0) url += `?${params.join('&')}`;
    return this.http.get<any[]>(url);
  }

  createRoomBlock(hotelId: number, roomId: number, blockData: { blockedCount: number; startDate: string; endDate: string; reason: string; notes?: string }): Observable<any> {
    return this.http.post(`${this.apiUrl}/${hotelId}/rooms/${roomId}/blocks`, blockData);
  }

  releaseRoomBlock(blockId: number): Observable<any> {
    return this.http.put(`${this.apiUrl}/blocks/${blockId}/release`, {});
  }

  getRoomBlocks(hotelId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/${hotelId}/blocks`);
  }

  getRoomBlocksByRoom(roomId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/rooms/${roomId}/blocks`);
  }

  getHotelInventorySummary(hotelId: number, startDate?: string, endDate?: string): Observable<HotelInventorySummary> {
    let url = `${this.apiUrl}/${hotelId}/inventory/summary`;
    const params: string[] = [];
    if (startDate) params.push(`startDate=${encodeURIComponent(startDate)}`);
    if (endDate) params.push(`endDate=${encodeURIComponent(endDate)}`);
    if (params.length > 0) url += `?${params.join('&')}`;
    return this.http.get<HotelInventorySummary>(url);
  }

  // --- WP-05: PROPERTY REGISTRATION WIZARD (STEP 1) ---

  uploadHotelImage(file: File, category: string = 'gallery'): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('category', category);
    return this.http.post(`${this.apiUrl}/images/upload`, formData);
  }

  deleteHotelImage(filename: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/images/${encodeURIComponent(filename)}`);
  }

  saveStep1Draft(step1Data: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/draft/step-1`, step1Data);
  }

  saveStep2Draft(draftId: number, step2Data: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/draft/${draftId}/step-2`, step2Data);
  }

  saveStep3Draft(draftId: number, step3Data: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/draft/${draftId}/step-3`, step3Data);
  }

  saveStep4Draft(draftId: number, step4Data: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/draft/${draftId}/step-4`, step4Data);
  }

  submitPropertyRegistration(draftId: number, submitData: any = { confirmationAccepted: true }): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/draft/${draftId}/submit`, submitData);
  }

  getDraft(draftId: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/draft/${draftId}`);
  }

  getManagerDrafts(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/drafts`);
  }

  deleteDraft(draftId: number): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/draft/${draftId}`);
  }
}

export interface HotelLowAvailabilityRoom {
  roomTypeId: number;
  roomTypeName: string;
  totalRooms: number;
  minAvailableRooms: number;
  exhausted: boolean;
  worstDate?: string | null;
}

export interface HotelInventorySummary {
  hotelId: number;
  startDate: string;
  endDate: string;
  totalDays: number;
  totalPhysicalRoomNights: number;
  occupiedRoomNights: number;
  heldRoomNights: number;
  blockedRoomNights: number;
  applicableRoomNightCapacity: number;
  availableRoomNights: number;
  occupancyRate: number;
  activeRoomTypesCount?: number;
  activeRoomBlocksCount?: number;
  lowAvailabilityRoomsCount?: number;
  exhaustedRoomsCount?: number;
  lowAvailabilityRooms?: HotelLowAvailabilityRoom[];
  dailySummaries?: any[];
}
