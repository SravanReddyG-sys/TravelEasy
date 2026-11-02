import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Schedule } from '../../../../../models/bus.model';

@Component({
  selector: 'app-bus-result-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bus-card mb-3 shadow-sm">
      <div class="row align-items-center g-3">
        
        <!-- Col 1: Bus & Operator Details -->
        <div class="col-xl-4 col-lg-5 col-md-5">
          <div class="d-flex align-items-start gap-3">
            <img [src]="getBusThumbnail(bus)" class="bus-card-thumb shadow-sm" alt="Bus photo" (error)="onBusImgError($event)">
            <div class="flex-grow-1 min-w-0">
              <div class="d-flex align-items-center justify-content-between">
                <span class="bus-operator-name text-truncate" [title]="operatorBusinessName">{{ operatorBusinessName }}</span>
                <button class="btn btn-link p-0 border-0 ms-2" (click)="saveToFavourites.emit(bus)" [title]="isSaved ? 'Saved' : 'Save to Favourites'">
                  <i [class]="isSaved ? 'fa-solid fa-heart text-danger' : 'fa-regular fa-heart text-muted'"></i>
                </button>
              </div>
              
              <!-- Rating & Reviews -->
              <div class="d-flex align-items-center gap-2 mt-1">
                <span class="badge bg-warning bg-opacity-25 text-dark fw-bold smaller py-1 px-2 rounded-2">
                  ★ {{ busRating.rating }}
                </span>
                <span class="text-muted smaller">({{ busRating.reviews }} reviews)</span>
              </div>

              <!-- Registered Manufacturer & Model -->
              <div *ngIf="manufacturerModel" class="bus-mfr-model mt-1 text-truncate" [title]="manufacturerModel">
                <i class="fa-solid fa-bus-simple me-1 text-muted smaller"></i>{{ manufacturerModel }}
              </div>

              <!-- Tags: Bus Type, AC / Non-AC, Seat Layout -->
              <div class="d-flex flex-wrap gap-1 mt-2 align-items-center">
                <span class="bus-type-tag">{{ bus.busType || bus.bus?.busType || 'Standard' }}</span>
                <span class="badge-ac-tag" [class.is-ac]="isAc" [class.is-non-ac]="!isAc">
                  <i [class]="isAc ? 'fa-solid fa-snowflake me-1' : 'fa-solid fa-fan me-1'"></i>{{ isAc ? 'AC' : 'Non-AC' }}
                </span>
                <span class="bus-layout-tag text-truncate" style="max-width: 160px;" [title]="seatLayoutName">
                  {{ seatLayoutName }}
                </span>
              </div>

              <!-- Amenities Row (Icon + Text) -->
              <div class="d-flex flex-wrap gap-1 mt-2" *ngIf="amenities.length > 0">
                <span *ngFor="let amen of amenities.slice(0, 3)" class="amenity-tag" [title]="amen">
                  <i [class]="getAmenityIcon(amen) + ' me-1 text-primary'"></i>
                  <span>{{ amen }}</span>
                </span>
                <span *ngIf="amenities.length > 3" class="amenity-tag text-muted fw-semibold" [title]="amenities.slice(3).join(', ')">
                  +{{ amenities.length - 3 }} more
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Col 2: Departure, Duration & Arrival Timeline -->
        <div class="col-xl-5 col-lg-4 col-md-4 text-center">
          <div class="d-flex justify-content-between align-items-center px-2">
            
            <!-- Departure -->
            <div class="text-start">
              <div class="timeline-time">{{ formatTime12(bus.departureTime) }}</div>
              <div class="timeline-city text-truncate" style="max-width: 90px;" [title]="bus.origin">{{ bus.origin }}</div>
            </div>

            <!-- Duration line -->
            <div class="timeline-track-wrap flex-grow-1">
              <div class="timeline-duration">{{ durationInfo.durationText }}</div>
              <div class="timeline-line"></div>
              <div class="text-muted smaller">Direct</div>
            </div>

            <!-- Arrival -->
            <div class="text-end">
              <div class="timeline-time">
                {{ formatTime12(bus.arrivalTime) }}
                <span *ngIf="durationInfo.isNextDay" class="badge-next-day" title="Arrives on the next day">Next Day</span>
              </div>
              <div class="timeline-city text-truncate" style="max-width: 90px;" [title]="bus.destination">{{ bus.destination }}</div>
            </div>

          </div>
        </div>

        <!-- Col 3: Price & Seat Selection CTA -->
        <div class="col-xl-3 col-lg-3 col-md-3 text-end d-flex flex-column align-items-end justify-content-between">
          <div class="mb-2">
            <span class="badge-seats-available">
              <i class="fa-solid fa-chair me-1"></i>{{ bus.availableSeats }} Seats Left
            </span>
          </div>

          <div class="mb-2">
            <span class="bus-fare-label">Starts from</span>
            <div class="bus-fare-amount">₹{{ bus.fare }}</div>
          </div>

          <button class="btn btn-select-seats" 
                  (click)="selectSeats.emit(bus)">
            <span>Select Seats</span>
          </button>
        </div>

      </div>
    </div>
  `,
  styles: [`
    .bus-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 16px;
      padding: 20px;
      transition: all 0.2s ease;
    }
    .bus-card:hover {
      box-shadow: 0 6px 20px rgba(8, 117, 225, 0.08) !important;
      border-color: #CBD5E1;
    }
    .bus-card-thumb {
      width: 84px;
      height: 84px;
      border-radius: 12px;
      object-fit: cover;
      flex-shrink: 0;
      background: #F1F5F9;
      border: 1px solid #E2E8F0;
    }
    .bus-operator-name {
      font-size: 1.05rem;
      font-weight: 700;
      color: #0F172A;
    }
    .bus-mfr-model {
      font-size: 0.76rem;
      color: #475569;
      font-weight: 600;
    }
    .bus-type-tag {
      font-size: 0.70rem;
      background: #F1F5F9;
      color: #475569;
      padding: 2px 7px;
      border-radius: 6px;
      font-weight: 600;
      display: inline-block;
    }
    .badge-ac-tag {
      font-size: 0.70rem;
      padding: 2px 7px;
      border-radius: 6px;
      font-weight: 600;
      display: inline-flex;
      align-items: center;
    }
    .badge-ac-tag.is-ac {
      background: #E0F2FE;
      color: #0284C7;
      border: 1px solid #BAE6FD;
    }
    .badge-ac-tag.is-non-ac {
      background: #F1F5F9;
      color: #64748B;
      border: 1px solid #E2E8F0;
    }
    .bus-layout-tag {
      font-size: 0.70rem;
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      color: #334155;
      padding: 2px 7px;
      border-radius: 6px;
      font-weight: 600;
      display: inline-block;
    }
    .amenity-tag {
      font-size: 0.70rem;
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      color: #334155;
      padding: 2px 7px;
      border-radius: 6px;
      font-weight: 500;
      display: inline-flex;
      align-items: center;
    }
    .timeline-time {
      font-size: 1.15rem;
      font-weight: 800;
      color: #0F172A;
    }
    .timeline-city {
      font-size: 0.75rem;
      color: #64748B;
      font-weight: 600;
    }
    .timeline-track-wrap {
      padding: 0 16px;
    }
    .timeline-duration {
      font-size: 0.75rem;
      color: #64748B;
      font-weight: 600;
      margin-bottom: 2px;
    }
    .timeline-line {
      height: 2px;
      background: #CBD5E1;
      position: relative;
      margin: 4px 0;
    }
    .timeline-line::before, .timeline-line::after {
      content: '';
      position: absolute;
      top: -3px;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #0875E1;
    }
    .timeline-line::before { left: 0; }
    .timeline-line::after { right: 0; background: #64748B; }
    .badge-next-day {
      font-size: 0.65rem;
      background: #FEF3C7;
      color: #92400E;
      padding: 2px 5px;
      border-radius: 4px;
      font-weight: 700;
      margin-left: 4px;
      vertical-align: middle;
    }
    .badge-seats-available {
      font-size: 0.75rem;
      background: #ECFDF5;
      color: #059669;
      font-weight: 700;
      padding: 4px 10px;
      border-radius: 20px;
      border: 1px solid #A7F3D0;
    }
    .bus-fare-label {
      font-size: 0.70rem;
      color: #64748B;
      text-transform: uppercase;
      font-weight: 600;
      display: block;
    }
    .bus-fare-amount {
      font-size: 1.35rem;
      font-weight: 800;
      color: #0875E1;
      line-height: 1.1;
    }
    .btn-select-seats {
      background: #0875E1;
      color: #FFFFFF;
      font-weight: 700;
      font-size: 0.85rem;
      padding: 8px 18px;
      border-radius: 10px;
      border: none;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s ease;
      box-shadow: 0 2px 6px rgba(8, 117, 225, 0.2);
    }
    .btn-select-seats:hover {
      background: #0662be;
      box-shadow: 0 4px 12px rgba(8, 117, 225, 0.3);
    }
    .smaller {
      font-size: 0.75rem;
    }
    .min-w-0 {
      min-width: 0;
    }
  `]
})
export class BusResultCardComponent {
  @Input({ required: true }) bus!: any;
  @Input() isSelected = false;
  @Input() isSaved = false;

  @Output() selectSeats = new EventEmitter<any>();
  @Output() saveToFavourites = new EventEmitter<any>();

  get operatorBusinessName(): string {
    return this.bus?.businessName || this.bus?.bus?.operatorName || this.bus?.operatorName || 'TravelEasy Partner';
  }

  get manufacturerModel(): string {
    const m = this.bus?.bus?.manufacturer || this.bus?.manufacturer;
    const mdl = this.bus?.bus?.model || this.bus?.model;
    if (m && mdl && m !== 'Not available' && mdl !== 'Not available') {
      return `${m} ${mdl}`;
    }
    if (m && m !== 'Not available') return m;
    if (mdl && mdl !== 'Not available') return mdl;
    return '';
  }

  get isAc(): boolean {
    const fac = (this.bus?.bus?.facilityType || this.bus?.facilityType || '').toUpperCase();
    if (fac === 'AC') return true;
    if (fac === 'NON_AC') return false;
    if (this.bus?.hasAc !== undefined) return !!this.bus.hasAc;
    if (this.bus?.bus?.hasAc !== undefined) return !!this.bus.bus.hasAc;
    return /AC/i.test(this.bus?.busType || '') && !/Non-AC/i.test(this.bus?.busType || '');
  }

  get seatLayoutName(): string {
    const tid = this.bus?.bus?.templateId || this.bus?.templateId;
    if (tid) {
      const map: Record<string, string> = {
        '1+1 SEATER': '1 + 1 Seater',
        '2+1 HYBRID_SLEEPER-1': '2 + 1 Hybrid Sleeper-1',
        '2+1 HYBRID_SLEEPER-2': '2 + 1 Hybrid Sleeper-2',
        '2+1 SEATER': '2 + 1 Seater',
        '2+1 SEATER_CUM_SLEEPER': '2 + 1 Seater Cum Sleeper',
        '2+1 SLEEPER': '2 + 1 Sleeper',
        '2+2 SEATER': '2 + 2 Seater',
      };
      const upper = tid.toUpperCase().trim();
      if (map[upper]) return map[upper];
      return tid.replace(/_/g, ' ');
    }
    return this.bus?.busType || 'Standard Layout';
  }

  get amenities(): string[] {
    const raw = this.bus?.bus?.amenities || this.bus?.amenities;
    if (Array.isArray(raw)) {
      return raw.map((s: any) => String(s).trim()).filter(Boolean);
    }
    if (typeof raw === 'string' && raw.trim().length > 0) {
      return raw.split(',').map((s: string) => s.trim()).filter(Boolean);
    }
    return [];
  }

  get busRating(): { rating: string | number; reviews: number } {
    if (this.bus?.rating !== undefined && this.bus?.reviewCount !== undefined) {
      return { rating: this.bus.rating, reviews: this.bus.reviewCount };
    }
    const hash = Math.abs(((this.bus?.id || 1) * 31) % 100);
    const rating = (4.0 + (hash % 10) / 10).toFixed(1);
    const reviews = 45 + (hash * 3);
    return { rating, reviews };
  }

  get durationInfo(): { durationText: string; isNextDay: boolean } {
    if (!this.bus?.departureTime || !this.bus?.arrivalTime) {
      return { durationText: '8h 30m', isNextDay: false };
    }
    const [depH, depM] = this.bus.departureTime.split(':').map(Number);
    const [arrH, arrM] = this.bus.arrivalTime.split(':').map(Number);
    let diff = (arrH * 60 + arrM) - (depH * 60 + depM);
    let isNextDay = false;
    if (diff < 0) {
      diff += 24 * 60;
      isNextDay = true;
    }
    const h = Math.floor(diff / 60);
    const m = diff % 60;
    return { durationText: `${h}h ${m > 0 ? m + 'm' : ''}`, isNextDay };
  }

  formatTime12(time24: string): string {
    if (!time24) return '';
    const [h, m] = time24.split(':').map(Number);
    const period = h >= 12 ? 'PM' : 'AM';
    const hour12 = h % 12 || 12;
    return `${hour12}:${String(m).padStart(2, '0')} ${period}`;
  }

  getBusThumbnail(b: any): string {
    const img = b?.bus?.exteriorImageUrl || b?.exteriorImageUrl || b?.imageUrl;
    if (!img) {
      return 'assets/images/travel-easy-bus.png';
    }
    if (img.startsWith('http://') || img.startsWith('https://') || img.startsWith('assets/')) {
      return img;
    }
    const cleanUrl = img.startsWith('/') ? img : `/${img}`;
    const base = (typeof window !== 'undefined' && window.location && window.location.port === '4200')
      ? 'http://localhost:8080'
      : '';
    return `${base}${cleanUrl}`;
  }

  onBusImgError(e: any) {
    e.target.src = 'assets/images/travel-easy-bus.png';
  }

  getAmenityIcon(amenity: string): string {
    const map: Record<string, string> = {
      'WiFi': 'fa-solid fa-wifi',
      'Wi-Fi': 'fa-solid fa-wifi',
      'Water Bottle': 'fa-solid fa-bottle-water',
      'Blanket': 'fa-solid fa-bed',
      'Blankets': 'fa-solid fa-bed',
      'Charging Point': 'fa-solid fa-bolt',
      'USB Charging': 'fa-solid fa-plug',
      'Reading Light': 'fa-regular fa-lightbulb',
      'Track My Bus': 'fa-solid fa-location-crosshairs',
      'Live Tracking': 'fa-solid fa-location-dot',
      'GPS Tracking': 'fa-solid fa-location-arrow',
      'Snacks': 'fa-solid fa-cookie-bite',
      'Emergency Exit': 'fa-solid fa-door-open',
      'Pillow': 'fa-solid fa-mattress-pillow',
      'Footrest': 'fa-solid fa-socks',
      'Curtains': 'fa-solid fa-person-booth',
      'Luggage Space': 'fa-solid fa-suitcase-rolling',
      'AC': 'fa-solid fa-snowflake'
    };
    return map[amenity] || 'fa-solid fa-check';
  }
}
