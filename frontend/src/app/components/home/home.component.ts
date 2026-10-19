import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { User } from '../../models/user.model';
import { NewsletterService } from '../../services/newsletter.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <!-- ===================== HERO ===================== -->
    <section class="te-hero">
      <div class="te-hero-overlay"></div>

      <div class="te-hero-content">
        <!-- Left content -->
        <div class="te-hero-left">
          <h1 class="te-hero-title">
            Explore<br>
            <span class="te-hero-title-blue">Travel Easy</span>
          </h1>
          <p class="te-hero-subtitle">Book buses and hotels across amazing destinations</p>

          <div class="te-hero-badges">
            <div class="te-badge">
              <span class="te-badge-icon-wrap">
                <i class="fa-solid fa-map-location-dot te-badge-icon"></i>
              </span>
              <div>
                <div class="te-badge-title">Wide Network</div>
                <div class="te-badge-sub">Across India</div>
              </div>
            </div>
            <div class="te-badge">
              <span class="te-badge-icon-wrap">
                <i class="fa-solid fa-shield-halved te-badge-icon"></i>
              </span>
              <div>
                <div class="te-badge-title">Trusted Operators</div>
                <div class="te-badge-sub">Safe &amp; Reliable</div>
              </div>
            </div>
            <div class="te-badge">
              <span class="te-badge-icon-wrap">
                <i class="fa-solid fa-hotel te-badge-icon"></i>
              </span>
              <div>
                <div class="te-badge-title">Great Stays</div>
                <div class="te-badge-sub">Verified Hotels</div>
              </div>
            </div>
            <div class="te-badge">
              <span class="te-badge-icon-wrap">
                <i class="fa-solid fa-bolt te-badge-icon"></i>
              </span>
              <div>
                <div class="te-badge-title">Easy Booking</div>
                <div class="te-badge-sub">Fast &amp; Secure</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Right tagline -->
        <div class="te-hero-right">
          <div class="te-hero-tagline">
            <em>New journeys<br>brighter stories</em>
          </div>
        </div>
      </div>

      <!-- ===================== SEARCH PANEL (OVERLAPPING HERO BOTTOM) ===================== -->
      <div class="te-search-wrapper">
        <div class="te-search-panel">
          <!-- Tabs -->
          <div class="te-search-tabs">
            <button type="button" class="te-tab" [class.te-tab--active]="searchTab === 'bus'" (click)="setTab('bus')">
              <i class="fa-solid fa-bus me-2"></i>Buses
            </button>
            <button type="button" class="te-tab" [class.te-tab--active]="searchTab === 'hotel'" (click)="setTab('hotel')">
              <i class="fa-solid fa-bed me-2"></i>Hotels
            </button>
          </div>

          <!-- Bus Search -->
          <div class="te-search-form" *ngIf="searchTab === 'bus'">
            <div *ngIf="busErrorMessage" class="te-search-error">
              <i class="fa-solid fa-circle-exclamation me-2"></i>{{ busErrorMessage }}
            </div>

            <div class="te-search-fields">
              <div class="te-field">
                <label class="te-field-label">From</label>
                <div class="te-input-wrap">
                  <i class="fa-solid fa-location-dot te-input-icon"></i>
                  <input type="text" class="te-input" [class.te-input--error]="busErrors.from" [(ngModel)]="busFrom" (input)="clearBusError('from')" placeholder="Enter source city" list="tePopularCities">
                </div>
              </div>

              <button type="button" class="te-swap-btn" (click)="swapCities()" title="Swap source and destination">
                <i class="fa-solid fa-arrow-right-arrow-left"></i>
              </button>

              <div class="te-field">
                <label class="te-field-label">To</label>
                <div class="te-input-wrap">
                  <i class="fa-solid fa-location-dot te-input-icon"></i>
                  <input type="text" class="te-input" [class.te-input--error]="busErrors.to" [(ngModel)]="busTo" (input)="clearBusError('to')" placeholder="Enter destination city" list="tePopularCities">
                </div>
              </div>

              <div class="te-field">
                <label class="te-field-label">Travel Date</label>
                <div class="te-input-wrap">
                  <i class="fa-regular fa-calendar te-input-icon"></i>
                  <input type="date" class="te-input" [class.te-input--error]="busErrors.date" [(ngModel)]="busDate" (input)="clearBusError('date')" [min]="minDate">
                </div>
              </div>

              <button type="button" class="te-search-btn" [disabled]="isSearchingBus" (click)="searchBuses()">
                <i class="fa-solid fa-magnifying-glass me-2"></i>
                <span>{{ isSearchingBus ? 'Searching...' : 'Search Buses' }}</span>
              </button>
            </div>
          </div>

          <!-- Hotel Search -->
          <div class="te-search-form" *ngIf="searchTab === 'hotel'">
            <div *ngIf="hotelErrorMessage" class="te-search-error">
              <i class="fa-solid fa-circle-exclamation me-2"></i>{{ hotelErrorMessage }}
            </div>

            <div class="te-search-fields">
              <div class="te-field te-field--wide">
                <label class="te-field-label">City / Destination</label>
                <div class="te-input-wrap">
                  <i class="fa-solid fa-location-dot te-input-icon"></i>
                  <input type="text" class="te-input" [class.te-input--error]="hotelErrors.city" [(ngModel)]="hotelCity" (input)="clearHotelError('city')" placeholder="Enter city or destination" list="tePopularCities">
                </div>
              </div>

              <div class="te-field">
                <label class="te-field-label">Check-in Date</label>
                <div class="te-input-wrap">
                  <i class="fa-regular fa-calendar te-input-icon"></i>
                  <input type="date" class="te-input" [class.te-input--error]="hotelErrors.checkIn" [(ngModel)]="hotelCheckIn" (input)="onCheckInChange()" [min]="minDate">
                </div>
              </div>

              <div class="te-field">
                <label class="te-field-label">Check-out Date</label>
                <div class="te-input-wrap">
                  <i class="fa-regular fa-calendar te-input-icon"></i>
                  <input type="date" class="te-input" [class.te-input--error]="hotelErrors.checkOut" [(ngModel)]="hotelCheckOut" (input)="clearHotelError('checkOut')" [min]="minCheckOutDate">
                </div>
              </div>

              <button type="button" class="te-search-btn" [disabled]="isSearchingHotel" (click)="searchHotels()">
                <i class="fa-solid fa-magnifying-glass me-2"></i>
                <span>{{ isSearchingHotel ? 'Searching...' : 'Search Hotels' }}</span>
              </button>
            </div>
          </div>

          <!-- Shared Datalist for Popular Indian Destinations -->
          <datalist id="tePopularCities">
            <option *ngFor="let city of popularCities" [value]="city"></option>
          </datalist>
        </div>
      </div>
    </section>

    <!-- ===================== DESTINATIONS ===================== -->
    <section class="te-destinations" id="destinations">
      <div class="te-container">
        <div class="te-section-header">
          <div class="te-section-left">
            <div class="te-section-label">POPULAR DESTINATIONS</div>
            <h2 class="te-section-title">Discover Amazing Places</h2>
            <p class="te-section-sub">Popular routes and destinations for your next journey</p>
          </div>
          <a class="te-view-all" routerLink="/customer">
            View All Destinations <i class="fa-solid fa-arrow-right ms-1"></i>
          </a>
        </div>

        <div class="te-dest-grid">
          <div class="te-dest-card" *ngFor="let dest of destinations">
            <div class="te-dest-img-wrap">
              <img [src]="dest.image" [alt]="dest.name" class="te-dest-img">
            </div>
            <div class="te-dest-info">
              <div class="te-dest-text">
                <div class="te-dest-name">{{ dest.name }}</div>
                <div class="te-dest-sub">{{ dest.subtitle }}</div>
              </div>
              <button class="te-dest-arrow" (click)="goSearch(dest.name)">
                <i class="fa-solid fa-chevron-right"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===================== TRAVEL MADE SIMPLER ===================== -->
    <section class="te-simpler" id="about">
      <div class="te-container">
        <div class="te-simpler-grid">
          <!-- Left -->
          <div class="te-simpler-left">
            <div class="te-section-label te-section-label--blue">TRAVEL MADE SIMPLER</div>
            <h2 class="te-simpler-title">Comfortable Journeys,<br>Memorable Experiences</h2>
            <p class="te-simpler-desc">
              Whether it's a quick weekend trip or a long holiday, Travel Easy helps you plan, book, and travel with confidence. Choose from a wide range of bus services and handpicked hotels, all in one place.
            </p>

            <div class="te-stats-grid">
              <div class="te-stat-card">
                <div class="te-stat-icon te-stat-icon--blue">
                  <i class="fa-solid fa-users"></i>
                </div>
                <div>
                  <div class="te-stat-number">10,000+</div>
                  <div class="te-stat-label">Happy Travellers</div>
                </div>
              </div>
              <div class="te-stat-card">
                <div class="te-stat-icon te-stat-icon--green">
                  <i class="fa-solid fa-bus"></i>
                </div>
                <div>
                  <div class="te-stat-number">500+</div>
                  <div class="te-stat-label">Bus Operators</div>
                </div>
              </div>
              <div class="te-stat-card">
                <div class="te-stat-icon te-stat-icon--orange">
                  <i class="fa-solid fa-building"></i>
                </div>
                <div>
                  <div class="te-stat-number">2,000+</div>
                  <div class="te-stat-label">Hotels &amp; Stays</div>
                </div>
              </div>
              <div class="te-stat-card">
                <div class="te-stat-icon te-stat-icon--purple">
                  <i class="fa-solid fa-location-dot"></i>
                </div>
                <div>
                  <div class="te-stat-number">1,000+</div>
                  <div class="te-stat-label">Destinations</div>
                </div>
              </div>
            </div>
          </div>

          <!-- Right image -->
          <div class="te-simpler-right">
            <div class="te-simpler-img-wrap">
              <img src="assets/images/travel-experience.png" alt="Travel Experience" class="te-simpler-img">
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===================== TESTIMONIALS ===================== -->
    <section class="te-testimonials">
      <div class="te-container">
        <div class="te-section-header">
          <div class="te-section-left">
            <div class="te-section-label te-section-label--blue">WHAT OUR TRAVELLERS SAY</div>
            <h2 class="te-section-title">Real Experiences, Real Journeys</h2>
            <p class="te-section-sub">Hear from travellers who have explored India with Travel Easy.</p>
          </div>
          <div class="te-testi-nav">
            <button class="te-testi-nav-btn" (click)="prevTestimonial()">
              <i class="fa-solid fa-chevron-left"></i>
            </button>
            <button class="te-testi-nav-btn" (click)="nextTestimonial()">
              <i class="fa-solid fa-chevron-right"></i>
            </button>
          </div>
        </div>

        <div class="te-testi-grid">
          <div class="te-testi-card" *ngFor="let t of visibleTestimonials">
            <div class="te-stars">
              <i class="fa-solid fa-star" *ngFor="let s of [1,2,3,4,5]"></i>
            </div>
            <p class="te-testi-text">"{{ t.review }}"</p>
            <div class="te-testi-author">
              <div class="te-testi-avatar">
                <i class="fa-solid fa-user"></i>
              </div>
              <div>
                <div class="te-testi-name">{{ t.name }}</div>
                <div class="te-testi-role">Traveller</div>
              </div>
            </div>
          </div>
        </div>

        <div class="te-testi-dots">
          <span class="te-dot" [class.te-dot--active]="testimonialPage === i"
            *ngFor="let t of testimonials; let i = index" (click)="goToTestimonialPage(i)"></span>
        </div>
      </div>
    </section>
  `,
  styles: [`
    /* ===================================================
       TRAVEL EASY LANDING PAGE STYLES
       All classes prefixed with "te-" to avoid conflicts
    =================================================== */

    /* ---------- RESET for home page ---------- */
    :host {
      display: block;
      font-family: 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
      color: #1e293b;
    }

    /* ============ NAVBAR ============ */
    .te-navbar {
      position: absolute;
      top: 0; left: 0; right: 0;
      z-index: 100;
      padding: 0;
    }
    .te-navbar-inner {
      display: flex;
      align-items: center;
      gap: 0;
      padding: 16px 32px;
      max-width: 1200px;
      margin: 0 auto;
    }
    .te-brand {
      display: flex;
      align-items: center;
      gap: 10px;
      text-decoration: none;
      flex-shrink: 0;
    }
    .te-brand-icon {
      width: 38px; height: 38px;
      background: #0d5bd9;
      border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
      color: white;
      font-size: 16px;
    }
    .te-brand-text {
      font-size: 21px;
      font-weight: 700;
      color: #0f172a;
    }
    .te-brand-blue { color: #0d5bd9; }

    .te-nav-links {
      display: flex;
      list-style: none;
      margin: 0 0 0 44px;
      padding: 0;
      gap: 30px;
      flex: 1;
    }
    .te-nav-link {
      text-decoration: none;
      color: #0f172a;
      font-weight: 600;
      font-size: 14.5px;
      padding: 5px 0;
      border-bottom: 2.5px solid transparent;
      transition: color 0.2s, border-color 0.2s;
    }
    .te-nav-link:hover { color: #0d5bd9; }
    .te-nav-link--active {
      color: #0d5bd9;
      border-bottom-color: #0d5bd9;
    }

    .te-nav-actions {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-shrink: 0;
    }
    .te-nav-username {
      font-size: 14px;
      color: #0f172a;
      font-weight: 600;
    }
    .te-btn {
      display: inline-flex; align-items: center; justify-content: center;
      padding: 8px 24px;
      border-radius: 8px;
      font-size: 14.5px;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      border: 2px solid transparent;
      transition: all 0.2s;
    }
    .te-btn-white {
      background: #ffffff;
      color: #0d5bd9;
      border-color: #ffffff;
      box-shadow: 0 2px 8px rgba(0,0,0,0.08);
    }
    .te-btn-white:hover {
      background: #f8fafc;
      color: #0a4bb5;
    }
    .te-btn-outline {
      border-color: #0d5bd9;
      color: #0d5bd9;
      background: #ffffff;
    }
    .te-btn-outline:hover {
      background: #0d5bd9;
      color: white;
    }
    .te-btn-primary {
      background: #0d5bd9;
      color: white;
      border-color: #0d5bd9;
    }
    .te-btn-primary:hover {
      background: #0a4bb5;
    }
    .te-hamburger {
      display: none;
      flex-direction: column;
      gap: 5px;
      background: none;
      border: none;
      cursor: pointer;
      padding: 4px;
      margin-left: auto;
    }
    .te-hamburger span {
      display: block;
      width: 24px; height: 2px;
      background: #1e293b;
      border-radius: 2px;
    }

    /* ============ HERO ============ */
    .te-hero {
      position: relative;
      min-height: 550px;
      background-image: url('/assets/images/travel-hero.png');
      background-size: cover;
      background-position: center bottom;
      background-repeat: no-repeat;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: 92px 0 22px;
      overflow: hidden;
    }
    .te-hero-overlay {
      position: absolute;
      inset: 0;
      background: radial-gradient(
        ellipse 52% 48% at 24% 38%,
        rgba(255, 255, 255, 0.42) 0%,
        rgba(255, 255, 255, 0.12) 65%,
        rgba(255, 255, 255, 0) 100%
      );
      pointer-events: none;
    }
    .te-hero-content {
      position: relative;
      z-index: 2;
      width: 100%;
      max-width: 1200px;
      margin: 0 auto;
      padding: 12px 32px 26px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .te-hero-left {
      max-width: 650px;
    }
    .te-hero-title {
      font-family: Georgia, 'Times New Roman', Times, serif;
      font-size: clamp(40px, 4.8vw, 58px);
      font-weight: 700;
      line-height: 1.08;
      color: #0b192c;
      margin-bottom: 12px;
    }
    .te-hero-title-blue { color: #0d5bd9; }
    .te-hero-subtitle {
      font-size: 16.5px;
      color: #0f172a;
      margin-bottom: 24px;
      font-weight: 500;
    }
    .te-hero-badges {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 22px;
    }
    .te-badge {
      display: flex;
      align-items: center;
      gap: 10px;
      background: transparent;
      padding: 0;
      border: none;
      box-shadow: none;
    }
    .te-badge-icon-wrap {
      width: 42px;
      height: 42px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.92);
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
      flex-shrink: 0;
    }
    .te-badge-icon {
      font-size: 17px;
      color: #0f172a;
      flex-shrink: 0;
    }
    .te-badge-title {
      font-size: 13.5px;
      font-weight: 700;
      color: #0f172a;
      line-height: 1.2;
    }
    .te-badge-sub {
      font-size: 12px;
      color: #1e293b;
      font-weight: 500;
    }
    .te-hero-right {
      flex-shrink: 0;
      align-self: flex-start;
      margin-top: 32px;
    }
    .te-hero-tagline {
      position: relative;
      font-size: clamp(20px, 2.4vw, 27px);
      font-weight: 600;
      color: #ffffff;
      text-align: center;
      text-shadow: 0 2px 10px rgba(0, 0, 0, 0.65);
      font-style: italic;
      line-height: 1.25;
      transform: rotate(-4deg);
      padding-bottom: 8px;
    }
    .te-hero-tagline::after {
      content: '';
      display: block;
      width: 72%;
      height: 3px;
      margin: 4px auto 0;
      background: #facc15;
      border-radius: 4px;
    }

    /* ============ SEARCH PANEL (OVERLAPPING HERO BOTTOM) ============ */
    .te-search-wrapper {
      display: flex;
      justify-content: center;
      position: relative;
      z-index: 10;
      width: 100%;
      max-width: 1200px;
      margin: 0 auto;
      padding: 0 32px;
    }
    .te-search-panel {
      background: transparent;
      border-radius: 0;
      box-shadow: none;
      padding: 0;
      width: 100%;
      overflow: visible;
    }
    .te-search-tabs {
      display: flex;
      align-items: stretch;
      gap: 4px;
      border-bottom: none;
    }
    .te-tab {
      position: relative;
      padding: 13px 34px;
      border: none;
      border-radius: 12px 12px 0 0;
      background: rgba(255, 255, 255, 0.92);
      font-size: 15px;
      font-weight: 700;
      color: #0f172a;
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      justify-content: center;
      backdrop-filter: blur(4px);
    }
    .te-tab:hover { color: #0d5bd9; }
    .te-tab--active {
      color: #ffffff;
      background: #0d5bd9;
    }
    .te-tab--active:hover { color: #ffffff; }
    .te-tab--active::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 50%;
      transform: translateX(-50%);
      border-left: 7px solid transparent;
      border-right: 7px solid transparent;
      border-bottom: 7px solid #ffffff;
    }
    .te-search-form {
      background: #ffffff;
      border-radius: 10px 14px 14px 14px;
      box-shadow: 0 10px 36px rgba(0, 0, 0, 0.18);
      padding: 18px 26px 22px;
    }
    .te-search-fields {
      display: flex;
      align-items: flex-end;
      gap: 14px;
      flex-wrap: wrap;
    }
    .te-field {
      display: flex;
      flex-direction: column;
      gap: 6px;
      flex: 1;
      min-width: 140px;
    }
    .te-field--wide { flex: 2; min-width: 200px; }
    .te-field-label {
      font-size: 13px;
      font-weight: 700;
      color: #0f172a;
      letter-spacing: 0;
    }
    .te-input-wrap {
      position: relative;
    }
    .te-input-icon {
      position: absolute;
      left: 13px;
      top: 50%;
      transform: translateY(-50%);
      color: #334155;
      font-size: 14px;
      pointer-events: none;
    }
    .te-input {
      width: 100%;
      padding: 11px 12px 11px 36px;
      border: 1.5px solid #dbe3ee;
      border-radius: 8px;
      font-size: 14px;
      color: #0f172a;
      background: #ffffff;
      transition: border-color 0.2s;
      outline: none;
    }
    .te-input:focus { border-color: #0d5bd9; background: white; }
    .te-input::placeholder { color: #94a3b8; }
    .te-swap-btn {
      width: 38px; height: 38px;
      border-radius: 50%;
      border: 1px solid #e2e8f0;
      background: #f8fafc;
      color: #0f172a;
      cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0;
      margin-bottom: 3px;
      transition: all 0.2s;
    }
    .te-swap-btn:hover {
      background: #eff6ff;
      color: #0d5bd9;
      border-color: #0d5bd9;
    }
    .te-search-btn {
      padding: 11px 30px;
      background: #0d5bd9;
      color: white;
      border: none;
      border-radius: 8px;
      font-size: 14.5px;
      font-weight: 600;
      cursor: pointer;
      white-space: nowrap;
      display: flex; align-items: center;
      transition: background 0.2s;
      flex-shrink: 0;
    }
    .te-search-btn:hover { background: #0a4bb5; }
    .te-search-btn:disabled {
      background: #93c5fd;
      cursor: not-allowed;
    }
    .te-search-error {
      background: #fef2f2;
      border: 1px solid #fecaca;
      color: #b91c1c;
      padding: 9px 14px;
      border-radius: 8px;
      font-size: 13px;
      font-weight: 600;
      margin-bottom: 14px;
      display: flex;
      align-items: center;
    }
    .te-input--error {
      border-color: #ef4444 !important;
      background-color: #fff8f8;
    }

    /* ============ COMMON CONTAINER ============ */
    .te-container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 0 32px;
    }
    .te-section-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      margin-bottom: 28px;
    }
    .te-section-label {
      font-size: 12px;
      font-weight: 700;
      color: #0d5bd9;
      letter-spacing: 1.2px;
      text-transform: uppercase;
      margin-bottom: 6px;
    }
    .te-section-label--blue { color: #0d5bd9; }
    .te-section-title {
      font-size: clamp(22px, 3vw, 32px);
      font-weight: 800;
      color: #0f172a;
      margin: 0 0 6px;
      line-height: 1.2;
    }
    .te-section-sub {
      font-size: 14px;
      color: #64748b;
      margin: 0;
    }
    .te-section-left { flex: 1; }

    /* ============ DESTINATIONS ============ */
    .te-destinations {
      background: #f0f7ff;
      padding: 48px 0 60px;
    }
    .te-view-all {
      display: inline-flex;
      align-items: center;
      padding: 9px 18px;
      border: 1.5px solid #1a56db;
      border-radius: 24px;
      color: #1a56db;
      font-size: 13px;
      font-weight: 600;
      text-decoration: none;
      white-space: nowrap;
      transition: all 0.2s;
      flex-shrink: 0;
      margin-left: 20px;
    }
    .te-view-all:hover {
      background: #1a56db;
      color: white;
    }
    .te-dest-grid {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 16px;
    }
    .te-dest-card {
      background: white;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 2px 12px rgba(0,0,0,0.07);
      transition: transform 0.2s, box-shadow 0.2s;
      cursor: pointer;
    }
    .te-dest-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 8px 24px rgba(0,0,0,0.12);
    }
    .te-dest-img-wrap {
      width: 100%;
      height: 140px;
      overflow: hidden;
    }
    .te-dest-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
      transition: transform 0.3s;
    }
    .te-dest-card:hover .te-dest-img { transform: scale(1.05); }
    .te-dest-info {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 12px 12px 14px;
    }
    .te-dest-name {
      font-size: 14px;
      font-weight: 700;
      color: #0f172a;
      line-height: 1.2;
    }
    .te-dest-sub {
      font-size: 11.5px;
      color: #64748b;
      margin-top: 2px;
    }
    .te-dest-arrow {
      width: 28px; height: 28px;
      border-radius: 50%;
      border: 1.5px solid #e2e8f0;
      background: white;
      color: #64748b;
      display: flex; align-items: center; justify-content: center;
      cursor: pointer;
      font-size: 11px;
      flex-shrink: 0;
      transition: all 0.2s;
    }
    .te-dest-arrow:hover {
      background: #1a56db;
      border-color: #1a56db;
      color: white;
    }

    /* ============ TRAVEL MADE SIMPLER ============ */
    .te-simpler {
      padding: 64px 0;
      background: white;
    }
    .te-simpler-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 60px;
      align-items: center;
    }
    .te-simpler-title {
      font-size: clamp(24px, 2.8vw, 36px);
      font-weight: 800;
      color: #0f172a;
      line-height: 1.2;
      margin: 10px 0 16px;
    }
    .te-simpler-desc {
      font-size: 15px;
      color: #475569;
      line-height: 1.7;
      margin-bottom: 32px;
    }
    .te-stats-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
    .te-stat-card {
      display: flex;
      align-items: center;
      gap: 14px;
      background: #f8fafc;
      border-radius: 12px;
      padding: 18px 20px;
      border: 1px solid #e2e8f0;
    }
    .te-stat-icon {
      width: 48px; height: 48px;
      border-radius: 12px;
      display: flex; align-items: center; justify-content: center;
      font-size: 20px;
      flex-shrink: 0;
    }
    .te-stat-icon--blue   { background: #eff6ff; color: #1a56db; }
    .te-stat-icon--green  { background: #f0fdf4; color: #16a34a; }
    .te-stat-icon--orange { background: #fff7ed; color: #ea580c; }
    .te-stat-icon--purple { background: #faf5ff; color: #9333ea; }
    .te-stat-number {
      font-size: 22px;
      font-weight: 800;
      color: #0f172a;
      line-height: 1;
    }
    .te-stat-label {
      font-size: 13px;
      color: #64748b;
      margin-top: 3px;
    }
    .te-simpler-right {
      display: flex;
      justify-content: center;
    }
    .te-simpler-img-wrap {
      border-radius: 20px;
      overflow: hidden;
      box-shadow: 0 16px 48px rgba(0,0,0,0.14);
      width: 100%;
      max-width: 800px;
    }
    .te-simpler-img {
      width: 100%;
      height: 500px;
      object-fit: cover;
      display: block;
    }

    /* ============ TESTIMONIALS ============ */
    .te-testimonials {
      background: #f8fafc;
      padding: 64px 0;
    }
    .te-testi-nav {
      display: flex;
      gap: 10px;
    }
    .te-testi-nav-btn {
      width: 38px; height: 38px;
      border-radius: 50%;
      border: 1.5px solid #e2e8f0;
      background: white;
      color: #64748b;
      display: flex; align-items: center; justify-content: center;
      cursor: pointer;
      font-size: 13px;
      transition: all 0.2s;
    }
    .te-testi-nav-btn:hover {
      background: #1a56db;
      border-color: #1a56db;
      color: white;
    }
    .te-testi-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 20px;
    }
    .te-testi-card {
      background: white;
      border-radius: 14px;
      padding: 24px;
      border: 1.5px solid #e2e8f0;
      box-shadow: 0 2px 12px rgba(0,0,0,0.05);
    }
    .te-stars {
      color: #f59e0b;
      font-size: 14px;
      margin-bottom: 12px;
      display: flex;
      gap: 3px;
    }
    .te-testi-text {
      font-size: 14px;
      color: #374151;
      line-height: 1.65;
      margin-bottom: 20px;
    }
    .te-testi-author {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .te-testi-avatar {
      width: 44px; height: 44px;
      border-radius: 50%;
      background: #e2e8f0;
      display: flex; align-items: center; justify-content: center;
      color: #64748b;
      font-size: 18px;
      flex-shrink: 0;
    }
    .te-testi-name {
      font-size: 14px;
      font-weight: 700;
      color: #0f172a;
    }
    .te-testi-role {
      font-size: 12px;
      color: #94a3b8;
    }
    .te-testi-dots {
      display: flex;
      justify-content: center;
      gap: 8px;
      margin-top: 28px;
    }
    .te-dot {
      width: 10px; height: 10px;
      border-radius: 50%;
      background: #cbd5e1;
      cursor: pointer;
      transition: background 0.2s;
    }
    .te-dot--active {
      background: #1a56db;
      width: 24px;
      border-radius: 5px;
    }

    /* ============ FOOTER ============ */
    .te-footer {
      background: #f0f7ff;
      padding: 56px 0 0;
    }
    .te-footer-grid {
      display: grid;
      grid-template-columns: 1.4fr 1fr 1fr 1fr 1.6fr;
      gap: 32px;
      padding-bottom: 40px;
    }
    .te-footer-brand {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 10px;
    }
    .te-brand-icon--sm {
      width: 30px; height: 30px;
      font-size: 13px;
    }
    .te-brand-text-footer {
      font-size: 17px;
      font-weight: 700;
      color: #0f172a;
    }
    .te-footer-brand-desc {
      font-size: 13px;
      color: #64748b;
      margin: 0;
      line-height: 1.5;
    }
    .te-footer-heading {
      font-size: 13.5px;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 14px;
    }
    .te-footer-links {
      list-style: none;
      padding: 0; margin: 0;
      display: flex; flex-direction: column;
      gap: 8px;
    }
    .te-footer-links a {
      text-decoration: none;
      font-size: 13.5px;
      color: #475569;
      transition: color 0.2s;
    }
    .te-footer-links a:hover { color: #1a56db; }
    .te-social-icons {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
    }
    .te-social-icon {
      width: 36px; height: 36px;
      border-radius: 50%;
      background: white;
      border: 1.5px solid #e2e8f0;
      display: flex; align-items: center; justify-content: center;
      color: #374151;
      font-size: 14px;
      text-decoration: none;
      transition: all 0.2s;
    }
    .te-social-icon:hover {
      background: #1a56db;
      border-color: #1a56db;
      color: white;
    }
    .te-footer-newsletter-sub {
      font-size: 12.5px;
      color: #64748b;
      margin-bottom: 12px;
    }
    .te-newsletter-form {
      display: flex;
      gap: 8px;
    }
    .te-newsletter-input-wrap {
      position: relative;
      flex: 1;
    }
    .te-newsletter-icon {
      position: absolute;
      left: 10px; top: 50%;
      transform: translateY(-50%);
      color: #94a3b8;
      font-size: 13px;
      pointer-events: none;
    }
    .te-newsletter-input {
      width: 100%;
      padding: 9px 10px 9px 32px;
      border: 1.5px solid #e2e8f0;
      border-radius: 8px;
      font-size: 13px;
      background: white;
      outline: none;
    }
    .te-newsletter-input:focus { border-color: #1a56db; }
    .te-newsletter-input--error { border-color: #ef4444 !important; }
    .te-newsletter-btn {
      padding: 9px 16px;
      background: #1a56db;
      color: white;
      border: none;
      border-radius: 8px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      white-space: nowrap;
      transition: background 0.2s;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .te-newsletter-btn:hover:not(:disabled) { background: #1648c0; }
    .te-newsletter-btn:disabled { opacity: 0.7; cursor: not-allowed; }

    .te-newsletter-feedback {
      margin-top: 10px;
      font-size: 12.5px;
      padding: 7px 10px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      gap: 7px;
      line-height: 1.35;
      word-break: break-word;
    }
    .te-newsletter-feedback--success {
      background: #ecfdf5;
      color: #065f46;
      border: 1px solid #a7f3d0;
    }
    .te-newsletter-feedback--info {
      background: #eff6ff;
      color: #1e40af;
      border: 1px solid #bfdbfe;
    }
    .te-newsletter-feedback--error {
      background: #fef2f2;
      color: #991b1b;
      border: 1px solid #fecaca;
    }
    .te-footer-bottom {
      border-top: 1px solid #e2e8f0;
      padding: 18px 0;
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 13px;
      color: #64748b;
    }
    .te-heart { color: #ef4444; }

    /* ============ RESPONSIVE ============ */
    @media (max-width: 1100px) {
      .te-dest-grid { grid-template-columns: repeat(3, 1fr); }
      .te-footer-grid { grid-template-columns: 1fr 1fr; }
    }
    @media (max-width: 900px) {
      .te-simpler-grid { grid-template-columns: 1fr; gap: 36px; }
      .te-simpler-right { order: -1; }
      .te-simpler-img { height: 280px; }
      .te-testi-grid { grid-template-columns: 1fr 1fr; }
    }
    @media (max-width: 768px) {
      .te-navbar-inner { padding: 12px 20px; }
      .te-nav-links { display: none; }
      .te-hamburger { display: flex; }
      .te-nav-actions { display: none; }
      .te-nav-links.te-nav-open {
        display: flex;
        flex-direction: column;
        position: absolute;
        top: 60px; left: 0; right: 0;
        background: white;
        padding: 16px 20px;
        border-bottom: 1px solid #e2e8f0;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        z-index: 200;
      }
      .te-hero { height: auto; min-height: auto; padding: 76px 0 24px; }
      .te-hero-content { flex-direction: column; align-items: flex-start; padding: 16px 20px 24px; }
      .te-hero-right { display: none; }
      .te-hero-badges { gap: 14px; }
      .te-badge { min-width: auto; flex: 1 0 calc(50% - 7px); }
      .te-search-wrapper { margin-top: 0; padding: 0 16px; }
      .te-search-fields { flex-direction: column; }
      .te-swap-btn { display: flex; align-self: center; margin: 2px 0; transform: rotate(90deg); }
      .te-field { min-width: 100%; }
      .te-search-btn { width: 100%; justify-content: center; }
      .te-dest-grid { grid-template-columns: repeat(2, 1fr); }
      .te-testi-grid { grid-template-columns: 1fr; }
      .te-footer-grid { grid-template-columns: 1fr; gap: 24px; }
      .te-footer-bottom { flex-direction: column; gap: 8px; text-align: center; }
      .te-container { padding: 0 16px; }
      .te-section-header { flex-direction: column; align-items: flex-start; gap: 12px; }
    }
    @media (max-width: 480px) {
      .te-dest-grid { grid-template-columns: 1fr 1fr; gap: 10px; }
      .te-stats-grid { grid-template-columns: 1fr 1fr; }
    }
  `]
})
export class HomeComponent {
  searchTab: 'bus' | 'hotel' = 'bus';
  busFrom = '';
  busTo = '';
  busDate = '';
  hotelCity = '';
  hotelCheckIn = '';
  hotelCheckOut = '';
  newsletterEmail = '';
  newsletterLoading = false;
  newsletterFeedback: { type: 'success' | 'info' | 'error'; message: string } | null = null;
  testimonialPage = 0;
  mobileMenuOpen = false;

  currentUser: User | null = null;

  popularCities: string[] = [
    'Bengaluru',
    'Hyderabad',
    'Vijayawada',
    'Chennai',
    'Goa',
    'Ooty',
    'Munnar',
    'Varanasi'
  ];

  busErrorMessage = '';
  hotelErrorMessage = '';
  busErrors = { from: false, to: false, date: false };
  hotelErrors = { city: false, checkIn: false, checkOut: false };
  isSearchingBus = false;
  isSearchingHotel = false;

  get minDate(): string {
    return this.getTodayDate();
  }

  get minCheckOutDate(): string {
    return this.hotelCheckIn ? this.getTomorrowDate(this.hotelCheckIn) : this.getTomorrowDate();
  }

  getTodayDate(): string {
    const d = new Date();
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  getTomorrowDate(baseDateStr?: string): string {
    const d = baseDateStr ? new Date(baseDateStr) : new Date();
    d.setDate(d.getDate() + 1);
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  setTab(tab: 'bus' | 'hotel') {
    this.searchTab = tab;
    this.busErrorMessage = '';
    this.hotelErrorMessage = '';
  }

  clearBusError(field: 'from' | 'to' | 'date') {
    this.busErrors[field] = false;
    if (!this.busErrors.from && !this.busErrors.to && !this.busErrors.date) {
      this.busErrorMessage = '';
    }
  }

  clearHotelError(field: 'city' | 'checkIn' | 'checkOut') {
    this.hotelErrors[field] = false;
    if (!this.hotelErrors.city && !this.hotelErrors.checkIn && !this.hotelErrors.checkOut) {
      this.hotelErrorMessage = '';
    }
  }

  onCheckInChange() {
    this.clearHotelError('checkIn');
    if (this.hotelCheckIn && this.hotelCheckOut && this.hotelCheckOut <= this.hotelCheckIn) {
      this.hotelCheckOut = this.getTomorrowDate(this.hotelCheckIn);
    }
  }

  destinations = [
    { name: 'Goa',       subtitle: 'Beaches, Sun & Fun',     image: 'assets/images/goa.png' },
    { name: 'Ooty',      subtitle: 'Hills & Nature',          image: 'assets/images/ooty.png' },
    { name: 'Munnar',    subtitle: 'Green Escapes',           image: 'assets/images/munnar.png' },
    { name: 'Varanasi',  subtitle: 'Spiritual & Cultural',    image: 'assets/images/varanasi.png' },
    { name: 'Bengaluru', subtitle: 'City Life & Beyond',      image: 'assets/images/bengaluru.png' },
    { name: 'Chennai',   subtitle: 'Heritage & Beaches',      image: 'assets/images/chennai.png' },
  ];

  testimonials = [
    {
      name: 'Arjun Mehta',
      review: 'Booking was super easy and the service was excellent. Had a wonderful trip to Goa!',
      rating: 5
    },
    {
      name: 'Sneha Iyer',
      review: 'Great hotel options and seamless booking experience. Highly recommended!',
      rating: 5
    },
    {
      name: 'Rohit Verma',
      review: 'Reliable service, competitive prices and amazing support throughout the journey.',
      rating: 5
    },
  ];

  get visibleTestimonials() {
    return this.testimonials;
  }

  constructor(
    private router: Router,
    private authService: AuthService,
    private newsletterService: NewsletterService
  ) {
    this.busDate = this.getTodayDate();
    this.hotelCheckIn = this.getTodayDate();
    this.hotelCheckOut = this.getTomorrowDate();

    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
    });
  }

  toggleMobileMenu() {
    this.mobileMenuOpen = !this.mobileMenuOpen;
    const links = document.getElementById('teNavLinks');
    if (links) {
      if (this.mobileMenuOpen) {
        links.classList.add('te-nav-open');
      } else {
        links.classList.remove('te-nav-open');
      }
    }
  }

  swapCities() {
    const tmp = this.busFrom;
    this.busFrom = this.busTo;
    this.busTo = tmp;
    this.clearBusError('from');
    this.clearBusError('to');
  }

  searchBuses() {
    this.busErrorMessage = '';
    this.busErrors = { from: false, to: false, date: false };

    const from = this.busFrom ? this.busFrom.trim() : '';
    const to = this.busTo ? this.busTo.trim() : '';
    const date = this.busDate ? this.busDate.trim() : '';

    if (!from) {
      this.busErrorMessage = 'Please enter a source city.';
      this.busErrors.from = true;
      return;
    }
    if (!to) {
      this.busErrorMessage = 'Please enter a destination city.';
      this.busErrors.to = true;
      return;
    }
    if (from.toLowerCase() === to.toLowerCase()) {
      this.busErrorMessage = 'Source and destination cities cannot be the same.';
      this.busErrors.from = true;
      this.busErrors.to = true;
      return;
    }
    if (!date) {
      this.busErrorMessage = 'Please select a travel date.';
      this.busErrors.date = true;
      return;
    }
    if (date < this.minDate) {
      this.busErrorMessage = 'Travel date cannot be in the past.';
      this.busErrors.date = true;
      return;
    }

    if (this.isSearchingBus) return;
    this.isSearchingBus = true;

    this.router.navigate(['/customer'], {
      queryParams: {
        tab: 'bus',
        from: from,
        to: to,
        date: date
      }
    }).finally(() => {
      this.isSearchingBus = false;
    });
  }

  searchHotels() {
    this.hotelErrorMessage = '';
    this.hotelErrors = { city: false, checkIn: false, checkOut: false };

    const city = this.hotelCity ? this.hotelCity.trim() : '';
    const checkIn = this.hotelCheckIn ? this.hotelCheckIn.trim() : '';
    const checkOut = this.hotelCheckOut ? this.hotelCheckOut.trim() : '';

    if (!city) {
      this.hotelErrorMessage = 'Please enter a city or destination.';
      this.hotelErrors.city = true;
      return;
    }
    if (!checkIn) {
      this.hotelErrorMessage = 'Please select a check-in date.';
      this.hotelErrors.checkIn = true;
      return;
    }
    if (checkIn < this.minDate) {
      this.hotelErrorMessage = 'Check-in date cannot be in the past.';
      this.hotelErrors.checkIn = true;
      return;
    }
    if (!checkOut) {
      this.hotelErrorMessage = 'Please select a check-out date.';
      this.hotelErrors.checkOut = true;
      return;
    }
    if (checkOut <= checkIn) {
      this.hotelErrorMessage = 'Check-out date must be later than check-in date.';
      this.hotelErrors.checkOut = true;
      return;
    }

    if (this.isSearchingHotel) return;
    this.isSearchingHotel = true;

    this.router.navigate(['/customer'], {
      queryParams: {
        tab: 'hotel',
        city: city,
        checkIn: checkIn,
        checkOut: checkOut
      }
    }).finally(() => {
      this.isSearchingHotel = false;
    });
  }

  goSearch(destination: string) {
    this.router.navigate(['/customer'], {
      queryParams: { tab: 'bus', to: destination }
    });
  }

  prevTestimonial() {
    this.testimonialPage = Math.max(0, this.testimonialPage - 1);
  }

  nextTestimonial() {
    this.testimonialPage = Math.min(this.testimonials.length - 1, this.testimonialPage + 1);
  }

  goToTestimonialPage(index: number) {
    this.testimonialPage = index;
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  onNewsletterInput() {
    if (this.newsletterFeedback?.type === 'error') {
      this.newsletterFeedback = null;
    }
  }

  subscribeNewsletter() {
    if (this.newsletterLoading) return;

    const email = this.newsletterEmail ? this.newsletterEmail.trim() : '';

    if (!email) {
      this.newsletterFeedback = {
        type: 'error',
        message: 'Please enter a valid email address.'
      };
      return;
    }

    const emailRegex = /^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,7}$/;
    if (!emailRegex.test(email) || email.length > 150) {
      this.newsletterFeedback = {
        type: 'error',
        message: 'Please enter a valid email address.'
      };
      return;
    }

    this.newsletterLoading = true;
    this.newsletterFeedback = null;

    this.newsletterService.subscribe(email.toLowerCase()).subscribe({
      next: (res) => {
        this.newsletterLoading = false;
        if (res.status === 'SUCCESS') {
          this.newsletterFeedback = {
            type: 'success',
            message: res.message || "You're subscribed to Travel Easy updates!"
          };
          this.newsletterEmail = '';
        } else if (res.status === 'ALREADY_SUBSCRIBED') {
          this.newsletterFeedback = {
            type: 'info',
            message: res.message || 'This email is already subscribed.'
          };
        } else {
          this.newsletterFeedback = {
            type: 'info',
            message: res.message || 'Subscription processed.'
          };
        }
      },
      error: (err) => {
        this.newsletterLoading = false;
        const errObj = err.error;
        if (err.status === 400) {
          const msg = (typeof errObj === 'object' && errObj?.message)
            ? errObj.message
            : 'Please enter a valid email address.';
          this.newsletterFeedback = {
            type: 'error',
            message: msg
          };
        } else if (err.status === 409 || (errObj && errObj.status === 'ALREADY_SUBSCRIBED')) {
          this.newsletterFeedback = {
            type: 'info',
            message: (typeof errObj === 'object' && errObj?.message)
              ? errObj.message
              : 'This email is already subscribed.'
          };
        } else if (err.status === 0) {
          this.newsletterFeedback = {
            type: 'error',
            message: "We couldn't complete your subscription. Please check your connection and try again."
          };
        } else {
          this.newsletterFeedback = {
            type: 'error',
            message: "We couldn't complete your subscription. Please try again."
          };
        }
      }
    });
  }
}
