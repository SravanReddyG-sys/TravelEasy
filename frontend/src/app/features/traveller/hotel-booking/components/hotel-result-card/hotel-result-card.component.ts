import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-hotel-result-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="hotel-result-card bg-white rounded-4 border shadow-sm mb-3 overflow-hidden">
      <div class="row g-0">
        <div class="col-md-4">
          <img [src]="hotel.imageUrl || 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=600&q=80'" (error)="onImgError($event)" class="w-100 h-100 object-fit-cover" style="min-height: 200px;" alt="Hotel Photo">
        </div>
        <div class="col-md-8 p-4 d-flex flex-column justify-content-between">
          <div>
            <div class="d-flex justify-content-between align-items-start">
              <div>
                <div class="d-flex align-items-center gap-2">
                  <h5 class="fw-bold text-navy mb-1">{{ hotel.name }}</h5>
                  <button class="btn btn-link p-0 text-danger border-0" (click)="saveToFavourites.emit(hotel)" [title]="isSaved ? 'Saved' : 'Save to Favourites'">
                    <i [class]="isSaved ? 'fa-solid fa-heart' : 'fa-regular fa-heart'"></i>
                  </button>
                </div>
                <div class="small fw-semibold text-dark mb-2">
                  <span class="text-warning me-1">★</span>{{ hotel.starRating || 4.5 }} Star
                  <span class="text-muted mx-2">•</span>
                  <span class="text-muted">{{ hotel.address }}, {{ hotel.city }}</span>
                </div>
              </div>
            </div>
            <p class="text-muted small mb-2">{{ hotel.description }}</p>
            <div class="d-flex flex-wrap gap-1 mb-2">
              <span *ngFor="let a of getAmenityPills(hotel.amenities)" class="badge bg-light text-secondary border small">{{ a }}</span>
            </div>
          </div>

          <div class="d-flex justify-content-between align-items-end mt-3 pt-3 border-top">
            <div>
              <div *ngIf="hotel.pricePerNight" class="fw-bold text-navy fs-4">₹{{ hotel.pricePerNight }} <small class="text-muted fs-6 fw-normal">per night</small></div>
            </div>
            <button class="btn btn-primary rounded-pill px-4 fw-bold" (click)="viewRooms.emit(hotel)">
              {{ isSelected ? 'Hide Rooms' : 'View Rooms' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Rooms Accordion -->
      <div *ngIf="isSelected" class="p-4 bg-light border-top">
        <h6 class="fw-bold text-navy mb-3"><i class="fa-solid fa-bed me-2 text-primary"></i>Available Room Types for {{ hotel.name }}</h6>
        
        <div *ngIf="rooms.length === 0" class="alert alert-warning small mb-0 rounded-3">
          <i class="fa-solid fa-triangle-exclamation me-1"></i> No room types are currently listed for this property.
        </div>

        <div *ngIf="rooms.length > 0" class="row g-3">
          <div *ngFor="let r of rooms" class="col-md-6">
            <div class="room-type-card bg-white p-3 rounded-4 border h-100 d-flex flex-column justify-content-between">
              <div>
                <h6 class="fw-bold text-navy mb-1">{{ r.roomTypeName }}</h6>
                <div class="text-muted smaller mb-2">{{ r.description || 'Comfortable room with essential amenities' }}</div>
                <div class="d-flex flex-wrap gap-1 mb-2">
                  <span class="badge bg-light text-dark border smaller"><i class="fa-solid fa-user-group me-1 text-primary"></i>Capacity: {{ r.capacity }}</span>
                  <span class="badge bg-light text-dark border smaller"><i class="fa-solid fa-check me-1 text-success"></i>Instant Confirmation</span>
                </div>
              </div>
              <div class="d-flex justify-content-between align-items-center mt-3 pt-2 border-top">
                <div class="fw-bold text-primary fs-5">₹{{ r.pricePerNight }} <small class="text-muted fs-6 fw-normal">/ night</small></div>
                <button class="btn btn-outline-primary btn-sm rounded-pill px-3 fw-bold" (click)="bookRoom.emit({ hotel: hotel, room: r })">
                  Book Room
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .text-navy {
      color: #0F172A;
    }
    .smaller {
      font-size: 0.75rem;
    }
  `]
})
export class HotelResultCardComponent {
  @Input({ required: true }) hotel!: any;
  @Input() isSelected = false;
  @Input() rooms: any[] = [];
  @Input() isSaved = false;

  @Output() viewRooms = new EventEmitter<any>();
  @Output() saveToFavourites = new EventEmitter<any>();
  @Output() bookRoom = new EventEmitter<{ hotel: any; room: any }>();

  getAmenityPills(amenities: any): string[] {
    if (!amenities) return ['WiFi', 'Air Conditioning', 'Breakfast Included'];
    if (Array.isArray(amenities)) return amenities;
    if (typeof amenities === 'string') return amenities.split(',').map(s => s.trim());
    return ['WiFi', 'Air Conditioning'];
  }

  onImgError(e: any) {
    e.target.src = 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=600&q=80';
  }
}
