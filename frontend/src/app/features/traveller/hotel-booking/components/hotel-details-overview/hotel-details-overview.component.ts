import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HotelPublicDto, HotelImage } from '../../../../../models/hotel.model';
import { resolveImageUrl, DEFAULT_HOTEL_PLACEHOLDER } from '../../../../../shared/utils/image-url.util';

export interface AmenityBadge {
  label: string;
  icon: string;
}

@Component({
  selector: 'app-hotel-details-overview',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="hotel-overview-card bg-white rounded-4 border shadow-sm p-3 p-md-4 mb-4">
      <div class="row g-4 align-items-center">

        <!-- Column 1: Image Gallery (Hero + 3 Thumbnails Stack) -->
        <div class="col-xl-6 col-lg-6 col-md-12">
          <div class="gallery-wrapper d-flex gap-2">

            <!-- Hero Image Display -->
            <div class="hero-image-container position-relative flex-grow-1 overflow-hidden rounded-3">
              <img
                [src]="currentHeroImageUrl"
                (error)="onHeroImgError($event)"
                class="hero-img w-100 h-100 object-fit-cover"
                [alt]="hotel?.name || 'Hotel photo'"
              >

              <!-- Favourite Heart Button Overlay -->
              <button
                type="button"
                class="btn-fav-card shadow-sm"
                (click)="onToggleFavourite($event)"
                [title]="isSaved ? 'Remove from Saved' : 'Save to Favourites'"
                [attr.aria-label]="isSaved ? 'Remove from Saved' : 'Save to Favourites'"
              >
                <i [class]="isSaved ? 'fa-solid fa-heart text-danger' : 'fa-regular fa-heart text-secondary'"></i>
              </button>

              <!-- Left / Right Carousel Navigation Chevrons (when > 1 image) -->
              <button
                type="button"
                *ngIf="galleryImages.length > 1"
                class="gallery-nav-btn prev-btn shadow-sm"
                (click)="prevImage($event)"
                title="Previous image"
                aria-label="Previous image"
              >
                <i class="fa-solid fa-chevron-left"></i>
              </button>

              <button
                type="button"
                *ngIf="galleryImages.length > 1"
                class="gallery-nav-btn next-btn shadow-sm"
                (click)="nextImage($event)"
                title="Next image"
                aria-label="Next image"
              >
                <i class="fa-solid fa-chevron-right"></i>
              </button>

              <!-- Carousel Indicator Dots (Matching Mockup) -->
              <div *ngIf="galleryImages.length > 1" class="carousel-dots-overlay d-flex align-items-center justify-content-center gap-1">
                <span
                  *ngFor="let img of galleryImages; let i = index"
                  class="carousel-dot"
                  [class.active]="i === activeImageIndex"
                  (click)="selectImage(i, $event)"
                ></span>
              </div>
            </div>

            <!-- Vertical Stack of Up to 3 Thumbnails (Matching Mockup) -->
            <div *ngIf="sidebarThumbnails.length > 0" class="gallery-thumbs-stack d-none d-sm-flex flex-column gap-2">
              <div
                *ngFor="let thumb of sidebarThumbnails; let idx = index"
                class="thumb-item rounded-2 overflow-hidden position-relative cursor-pointer"
                [class.thumb-active]="thumb.originalIndex === activeImageIndex"
                (click)="selectImage(thumb.originalIndex)"
              >
                <img
                  [src]="thumb.url"
                  (error)="onThumbImgError($event)"
                  class="thumb-img w-100 h-100 object-fit-cover"
                  [alt]="(hotel?.name || 'Hotel') + ' thumbnail ' + (idx + 1)"
                >
              </div>
            </div>

          </div>
        </div>

        <!-- Column 2: Hotel Details & Amenities -->
        <div class="col-xl-6 col-lg-6 col-md-12 d-flex flex-column justify-content-between">
          <div>

            <!-- Header Row: Name & Property Type Badge -->
            <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
              <h3 class="hotel-title fw-bold text-navy mb-0">{{ hotel?.name || 'Hotel Details' }}</h3>
              <span class="badge property-type-badge rounded-pill fw-semibold">
                {{ hotel?.propertyType || 'Hotel' }}
              </span>
              <span *ngIf="hotel?.starRating" class="badge bg-warning-subtle text-warning-emphasis rounded-pill small fw-semibold">
                <i class="fa-solid fa-star text-warning me-1"></i>{{ hotel?.starRating }} Star
              </span>
            </div>

            <!-- Location -->
            <div class="hotel-location text-muted small mb-3 d-flex align-items-center">
              <i class="fa-solid fa-location-dot text-primary me-2 flex-shrink-0"></i>
              <span class="text-truncate" [title]="locationText">{{ locationText }}</span>
            </div>

            <!-- Description -->
            <p class="hotel-description text-secondary small mb-3">
              {{ hotel?.description || 'Modern property offering comfortable accommodations and quality amenities for travellers.' }}
            </p>

            <!-- Amenities Badges with Icons -->
            <div *ngIf="visibleAmenities.length > 0" class="d-flex flex-wrap align-items-center gap-2 mb-2">
              <div *ngFor="let am of visibleAmenities" class="amenity-badge d-flex align-items-center gap-1">
                <i [class]="am.icon"></i>
                <span>{{ am.label }}</span>
              </div>
              <span *ngIf="remainingAmenitiesCount > 0" class="badge-more-amenities">
                +{{ remainingAmenitiesCount }} more
              </span>
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

    .gallery-wrapper {
      height: 260px;
    }

    @media (max-width: 575.98px) {
      .gallery-wrapper {
        height: 210px;
      }
    }

    .hero-image-container {
      background-color: #F1F5F9;
      height: 100%;
    }

    .hero-img {
      transition: opacity 0.25s ease;
    }

    .btn-fav-card {
      position: absolute;
      top: 12px;
      right: 12px;
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: #FFFFFF;
      border: none;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: transform 0.15s ease, background-color 0.15s ease;
      z-index: 3;
    }

    .btn-fav-card:hover {
      transform: scale(1.08);
      background-color: #F8FAFC;
    }

    .gallery-nav-btn {
      position: absolute;
      top: 50%;
      transform: translateY(-50%);
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: rgba(15, 23, 42, 0.65);
      color: #FFFFFF;
      border: none;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: background-color 0.2s ease, transform 0.15s ease;
      z-index: 3;
      font-size: 0.75rem;
    }

    .gallery-nav-btn:hover {
      background: rgba(15, 23, 42, 0.9);
      transform: translateY(-50%) scale(1.08);
    }

    .prev-btn {
      left: 10px;
    }

    .next-btn {
      right: 10px;
    }

    .carousel-dots-overlay {
      position: absolute;
      bottom: 12px;
      left: 0;
      right: 0;
      pointer-events: auto;
      z-index: 3;
    }

    .carousel-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: rgba(255, 255, 255, 0.6);
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .carousel-dot.active {
      width: 8px;
      height: 8px;
      background-color: #FFFFFF;
    }

    .gallery-thumbs-stack {
      width: 95px;
      height: 100%;
    }

    .thumb-item {
      flex: 1;
      border: 2px solid transparent;
      transition: border-color 0.2s ease, transform 0.15s ease;
      background-color: #F1F5F9;
    }

    .thumb-item:hover {
      transform: scale(1.02);
    }

    .thumb-active {
      border-color: #0891B2 !important;
      box-shadow: 0 0 0 1px #0891B2;
    }

    .cursor-pointer {
      cursor: pointer;
    }

    .property-type-badge {
      background-color: #EFF6FF;
      color: #2563EB;
      font-size: 0.78rem;
      padding: 4px 10px;
      border: 1px solid #BFDBFE;
    }

    .hotel-title {
      font-size: 1.35rem;
      color: #0F172A;
    }

    .hotel-description {
      display: -webkit-box;
      -webkit-line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
      line-height: 1.5;
    }

    .amenity-badge {
      font-size: 0.78rem;
      font-weight: 500;
      color: #334155;
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      border-radius: 8px;
      padding: 4px 10px;
    }

    .amenity-badge i {
      color: #0891B2;
      font-size: 0.8rem;
    }

    .badge-more-amenities {
      font-size: 0.75rem;
      font-weight: 600;
      color: #0891B2;
      background: #ECFEFF;
      border: 1px solid #CFFAFE;
      border-radius: 8px;
      padding: 4px 10px;
    }
  `]
})
export class HotelDetailsOverviewComponent implements OnInit, OnChanges {
  @Input({ required: true }) hotel!: HotelPublicDto;
  @Input() images: HotelImage[] = [];
  @Input() isSaved = false;

  @Output() toggleFavourite = new EventEmitter<HotelPublicDto>();

  activeImageIndex = 0;
  fallbackImage = DEFAULT_HOTEL_PLACEHOLDER;
  galleryImages: string[] = [];

  ngOnInit(): void {
    this.buildGalleryImages();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['hotel'] || changes['images']) {
      this.buildGalleryImages();
    }
  }

  private buildGalleryImages(): void {
    const list: string[] = [];

    // Prioritize cover and sorted gallery images from the images collection
    if (this.images && this.images.length > 0) {
      const sorted = [...this.images].sort((a, b) => {
        if (a.isCover || a.cover) return -1;
        if (b.isCover || b.cover) return 1;
        return (a.displayOrder || 0) - (b.displayOrder || 0);
      });
      sorted.forEach(img => {
        if (img.imageUrl) list.push(resolveImageUrl(img.imageUrl));
      });
    }

    // Include primary hotel imageUrl if not already present
    if (this.hotel?.imageUrl) {
      const mainResolved = resolveImageUrl(this.hotel.imageUrl);
      if (!list.includes(mainResolved)) {
        list.unshift(mainResolved);
      }
    }

    if (list.length === 0) {
      list.push(this.fallbackImage);
    }

    this.galleryImages = list;
    if (this.activeImageIndex >= this.galleryImages.length) {
      this.activeImageIndex = 0;
    }
  }

  get currentHeroImageUrl(): string {
    return this.galleryImages[this.activeImageIndex] || this.fallbackImage;
  }

  get sidebarThumbnails(): Array<{ url: string; originalIndex: number }> {
    if (this.galleryImages.length <= 1) return [];
    // Show up to 3 thumbnails from the gallery
    return this.galleryImages.slice(0, 4).map((url, idx) => ({
      url,
      originalIndex: idx
    }));
  }

  get locationText(): string {
    if (!this.hotel) return '';
    const city = (this.hotel.city || '').trim();
    const addr = (this.hotel.address || '').trim();
    if (addr && city) {
      if (addr.toLowerCase().includes(city.toLowerCase())) {
        return addr;
      }
      return `${addr}, ${city}`;
    }
    return addr || city || 'Location details unavailable';
  }

  get parsedAmenities(): string[] {
    if (!this.hotel?.amenities || typeof this.hotel.amenities !== 'string') return [];
    const unique = new Set(
      this.hotel.amenities
        .split(',')
        .map(s => s.trim())
        .filter(s => s.length > 0)
    );
    return Array.from(unique);
  }

  get visibleAmenities(): AmenityBadge[] {
    const list = this.parsedAmenities.slice(0, 4);
    return list.map(item => ({
      label: item,
      icon: this.getAmenityIcon(item)
    }));
  }

  get remainingAmenitiesCount(): number {
    return Math.max(0, this.parsedAmenities.length - 4);
  }

  getAmenityIcon(name: string): string {
    const n = name.toLowerCase();
    if (n.includes('wi-fi') || n.includes('wifi') || n.includes('internet')) return 'fa-solid fa-wifi';
    if (n.includes('restaurant') || n.includes('dining') || n.includes('food')) return 'fa-solid fa-utensils';
    if (n.includes('pool') || n.includes('swimming')) return 'fa-solid fa-person-swimming';
    if (n.includes('spa') || n.includes('wellness') || n.includes('massage')) return 'fa-solid fa-spa';
    if (n.includes('gym') || n.includes('fitness')) return 'fa-solid fa-dumbbell';
    if (n.includes('parking')) return 'fa-solid fa-square-parking';
    if (n.includes('breakfast')) return 'fa-solid fa-mug-saucer';
    if (n.includes('ac') || n.includes('air conditioning')) return 'fa-solid fa-snowflake';
    if (n.includes('bar')) return 'fa-solid fa-martini-glass';
    return 'fa-solid fa-check';
  }

  selectImage(index: number, event?: Event): void {
    if (event) event.stopPropagation();
    if (index >= 0 && index < this.galleryImages.length) {
      this.activeImageIndex = index;
    }
  }

  prevImage(event: Event): void {
    event.stopPropagation();
    if (this.galleryImages.length > 1) {
      this.activeImageIndex = (this.activeImageIndex - 1 + this.galleryImages.length) % this.galleryImages.length;
    }
  }

  nextImage(event: Event): void {
    event.stopPropagation();
    if (this.galleryImages.length > 1) {
      this.activeImageIndex = (this.activeImageIndex + 1) % this.galleryImages.length;
    }
  }

  onHeroImgError(e: Event): void {
    const target = e.target as HTMLImageElement;
    if (target && target.src !== this.fallbackImage) {
      target.src = this.fallbackImage;
    }
  }

  onThumbImgError(e: Event): void {
    const target = e.target as HTMLImageElement;
    if (target && target.src !== this.fallbackImage) {
      target.src = this.fallbackImage;
    }
  }

  onToggleFavourite(event: Event): void {
    event.stopPropagation();
    this.toggleFavourite.emit(this.hotel);
  }
}
