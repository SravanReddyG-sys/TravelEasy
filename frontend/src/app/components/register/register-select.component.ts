import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-register-select',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="te-select-page">
      <!-- Panoramic scenic background image layer -->
      <div class="te-scenic-bg"></div>

      <!-- Soft header radial glow for contrast against sky -->
      <div class="te-header-glow"></div>

      <!-- Smooth curved wave shape divider at bottom half -->
      <div class="te-wave-divider">
        <svg viewBox="0 0 1440 220" fill="none" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="none" class="te-wave-svg">
          <path d="M0,60 C420,210 1020,210 1440,50 L1440,220 L0,220 Z" fill="#ffffff" />
        </svg>
        <div class="te-wave-bottom-fill"></div>
      </div>

      <!-- Soft blue ambient glows in bottom corners matching mockup -->
      <div class="te-glow te-glow-left"></div>
      <div class="te-glow te-glow-right"></div>

      <!-- Main content container -->
      <div class="te-content-container">
        <!-- Brand Logo at Top Center -->
        <a routerLink="/home" class="te-brand">
          <div class="te-brand-icon">
            <i class="fa-solid fa-paper-plane"></i>
          </div>
          <span class="te-brand-text">Travel <span class="te-brand-blue">Easy</span></span>
        </a>

        <!-- Title & Subtitle Section -->
        <div class="te-title-section">
          <h1 class="te-title">
            Create Your <span class="te-title-highlight">Account</span>
          </h1>
          <p class="te-subtitle">
            Choose the account type that best fits your needs and start your journey with Travel Easy.
          </p>
        </div>

        <!-- 3 Account Type Cards Grid -->
        <div class="te-cards-grid">
          <!-- ================= 1. TRAVELLER CARD ================= -->
          <div class="te-card">
            <div class="te-card-img-wrap">
              <img src="assets/images/traveller-card.png" alt="Traveller" class="te-card-img" />
            </div>
            <div class="te-card-body">
              <h2 class="te-card-title">Traveller</h2>
              <p class="te-card-desc">
                Discover and book buses and hotels for your next journey.
              </p>

              <ul class="te-feature-list">
                <li class="te-feature-item">
                  <div class="te-feature-icon">
                    <i class="fa-solid fa-ticket"></i>
                  </div>
                  <span class="te-feature-text">Book buses across India</span>
                </li>
                <li class="te-feature-item">
                  <div class="te-feature-icon">
                    <i class="fa-solid fa-bed"></i>
                  </div>
                  <span class="te-feature-text">Find and book verified hotels</span>
                </li>
                <li class="te-feature-item">
                  <div class="te-feature-icon">
                    <i class="fa-solid fa-star"></i>
                  </div>
                  <span class="te-feature-text">Manage your bookings easily</span>
                </li>
              </ul>

              <a routerLink="/register/traveller" class="te-card-btn">
                <span>Register as Traveller</span>
                <i class="fa-solid fa-arrow-right"></i>
              </a>
            </div>
          </div>

          <!-- ================= 2. BUS OPERATOR CARD ================= -->
          <div class="te-card">
            <div class="te-card-img-wrap">
              <img src="assets/images/bus-operator-card.png" alt="Bus Operator" class="te-card-img" />
            </div>
            <div class="te-card-body">
              <h2 class="te-card-title">Bus Operator</h2>
              <p class="te-card-desc">
                List your buses, manage routes and grow your business.
              </p>

              <ul class="te-feature-list">
                <li class="te-feature-item">
                  <div class="te-feature-icon">
                    <i class="fa-solid fa-bus"></i>
                  </div>
                  <span class="te-feature-text">Add and manage your buses</span>
                </li>
                <li class="te-feature-item">
                  <div class="te-feature-icon">
                    <i class="fa-solid fa-route"></i>
                  </div>
                  <span class="te-feature-text">Set routes and schedules</span>
                </li>
                <li class="te-feature-item">
                  <div class="te-feature-icon">
                    <i class="fa-solid fa-chart-simple"></i>
                  </div>
                  <span class="te-feature-text">Reach more travellers</span>
                </li>
              </ul>

              <a routerLink="/register/bus-operator" class="te-card-btn">
                <span>Register as Bus Operator</span>
                <i class="fa-solid fa-arrow-right"></i>
              </a>
            </div>
          </div>

          <!-- ================= 3. HOTEL PARTNER CARD ================= -->
          <div class="te-card">
            <div class="te-card-img-wrap">
              <img src="assets/images/hotel-partner-card.png" alt="Hotel Partner" class="te-card-img" />
            </div>
            <div class="te-card-body">
              <h2 class="te-card-title">Hotel Partner</h2>
              <p class="te-card-desc">
                List and manage your properties and attract more guests.
              </p>

              <ul class="te-feature-list">
                <li class="te-feature-item">
                  <div class="te-feature-icon">
                    <i class="fa-solid fa-bed"></i>
                  </div>
                  <span class="te-feature-text">List your hotel properties</span>
                </li>
                <li class="te-feature-item">
                  <div class="te-feature-icon">
                    <i class="fa-solid fa-user-group"></i>
                  </div>
                  <span class="te-feature-text">Manage room availability</span>
                </li>
                <li class="te-feature-item">
                  <div class="te-feature-icon">
                    <i class="fa-solid fa-chart-simple"></i>
                  </div>
                  <span class="te-feature-text">Grow your business with us</span>
                </li>
              </ul>

              <a routerLink="/register/hotel-partner" class="te-card-btn">
                <span>Register as Hotel Partner</span>
                <i class="fa-solid fa-arrow-right"></i>
              </a>
            </div>
          </div>
        </div>

        <!-- Bottom Login Link -->
        <div class="te-bottom-link-wrap">
          <span class="te-bottom-text">Already have an account?</span>
          <a routerLink="/login" class="te-bottom-login-btn">Log in</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    /* ================= PAGE CONTAINER & BACKGROUNDS ================= */
    .te-select-page {
      position: relative;
      flex: 1 0 auto;
      min-height: calc(100vh - 140px);
      width: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-start;
      overflow-x: hidden;
      padding: 88px 20px 32px;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    }

    .te-scenic-bg {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 60%;
      min-height: 480px;
      background-image: url('/assets/images/account-type-background.png');
      background-size: cover;
      background-position: center top;
      background-repeat: no-repeat;
      z-index: 0;
    }

    .te-header-glow {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 380px;
      background: radial-gradient(
        ellipse 70% 60% at 50% 18%,
        rgba(255, 255, 255, 0.65) 0%,
        rgba(255, 255, 255, 0.2) 65%,
        rgba(255, 255, 255, 0) 100%
      );
      pointer-events: none;
      z-index: 1;
    }

    /* Wave curve separating the scenic background from the crisp bottom area */
    .te-wave-divider {
      position: absolute;
      top: 40%;
      left: 0;
      width: 100%;
      bottom: 0;
      display: flex;
      flex-direction: column;
      pointer-events: none;
      z-index: 1;
    }

    .te-wave-svg {
      width: 100%;
      height: 140px;
      flex-shrink: 0;
      display: block;
    }

    .te-wave-bottom-fill {
      width: 100%;
      flex-grow: 1;
      background-color: #ffffff;
    }

    /* Ambient soft blue glows in bottom corners */
    .te-glow {
      position: absolute;
      bottom: 0;
      width: 380px;
      height: 320px;
      border-radius: 50%;
      pointer-events: none;
      z-index: 1;
    }
    .te-glow-left {
      left: -80px;
      background: radial-gradient(circle at 20% 90%, rgba(200, 225, 255, 0.55) 0%, rgba(255, 255, 255, 0) 70%);
    }
    .te-glow-right {
      right: -80px;
      background: radial-gradient(circle at 80% 90%, rgba(200, 225, 255, 0.55) 0%, rgba(255, 255, 255, 0) 70%);
    }

    /* ================= MAIN CONTENT WRAPPER ================= */
    .te-content-container {
      position: relative;
      z-index: 2;
      width: 100%;
      max-width: 1220px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      align-items: center;
    }

    /* ================= LOGO / BRANDING ================= */
    .te-brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      text-decoration: none;
      margin-bottom: 24px;
      transition: transform 0.2s ease;
    }
    .te-brand:hover {
      transform: scale(1.02);
    }
    .te-brand-icon {
      width: 40px;
      height: 40px;
      background-color: #0066ff;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #ffffff;
      font-size: 17px;
      box-shadow: 0 4px 12px rgba(0, 102, 255, 0.3);
    }
    .te-brand-icon i {
      transform: translate(-1px, 1px);
    }
    .te-brand-text {
      font-size: 24px;
      font-weight: 800;
      color: #0f172a;
      letter-spacing: -0.3px;
    }
    .te-brand-blue {
      color: #0066ff;
    }

    /* ================= TITLE & SUBTITLE ================= */
    .te-title-section {
      text-align: center;
      margin-bottom: 36px;
      max-width: 760px;
    }
    .te-title {
      font-size: 44px;
      font-weight: 800;
      color: #0f172a;
      letter-spacing: -0.8px;
      line-height: 1.15;
      margin-bottom: 12px;
    }
    .te-title-highlight {
      color: #0066ff;
    }
    .te-subtitle {
      font-size: 16px;
      font-weight: 500;
      color: #475569;
      line-height: 1.5;
      margin: 0;
    }

    /* ================= 3 CARDS GRID ================= */
    .te-cards-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 28px;
      width: 100%;
      max-width: 1180px;
      margin-bottom: 34px;
      align-items: stretch;
    }

    /* ================= INDIVIDUAL CARD ================= */
    .te-card {
      background: #ffffff;
      border-radius: 20px;
      padding: 18px 20px 22px;
      box-shadow: 0 12px 34px rgba(15, 23, 42, 0.08), 0 2px 6px rgba(15, 23, 42, 0.03);
      border: 1px solid rgba(226, 232, 240, 0.9);
      display: flex;
      flex-direction: column;
      transition: transform 0.25s ease, box-shadow 0.25s ease;
    }
    .te-card:hover {
      transform: translateY(-6px);
      box-shadow: 0 20px 42px rgba(15, 23, 42, 0.13), 0 4px 10px rgba(15, 23, 42, 0.04);
    }

    .te-card-img-wrap {
      width: 100%;
      height: 188px;
      border-radius: 14px;
      overflow: hidden;
      background-color: #f1f5f9;
      position: relative;
    }
    .te-card-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      object-position: center;
      display: block;
      transition: transform 0.4s ease;
    }
    .te-card:hover .te-card-img {
      transform: scale(1.03);
    }

    .te-card-body {
      display: flex;
      flex-direction: column;
      flex-grow: 1;
      padding-top: 16px;
    }

    .te-card-title {
      font-size: 22px;
      font-weight: 700;
      color: #0f172a;
      text-align: center;
      margin: 0 0 6px 0;
      letter-spacing: -0.3px;
    }

    .te-card-desc {
      font-size: 13.5px;
      color: #64748b;
      text-align: center;
      line-height: 1.45;
      margin: 0 auto 20px;
      max-width: 280px;
      min-height: 38px;
    }

    /* Features List */
    .te-feature-list {
      list-style: none;
      padding: 0 4px;
      margin: 0 0 22px 0;
      display: flex;
      flex-direction: column;
      gap: 13px;
      flex-grow: 1;
    }
    .te-feature-item {
      display: flex;
      align-items: center;
      gap: 13px;
    }
    .te-feature-icon {
      width: 36px;
      height: 36px;
      min-width: 36px;
      border-radius: 50%;
      background-color: #ebf3ff;
      color: #0066ff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
    }
    .te-feature-text {
      font-size: 13.5px;
      font-weight: 500;
      color: #334155;
      line-height: 1.35;
    }

    /* Action Button */
    .te-card-btn {
      width: 100%;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      background-color: #0066ff;
      color: #ffffff;
      padding: 13px 20px;
      border-radius: 12px;
      font-size: 14.5px;
      font-weight: 600;
      text-decoration: none;
      border: none;
      box-shadow: 0 4px 14px rgba(0, 102, 255, 0.25);
      transition: all 0.2s ease-in-out;
      cursor: pointer;
    }
    .te-card-btn:hover {
      background-color: #0052cc;
      color: #ffffff;
      box-shadow: 0 6px 18px rgba(0, 102, 255, 0.35);
      transform: translateY(-1px);
    }
    .te-card-btn i {
      font-size: 13px;
      transition: transform 0.2s ease;
    }
    .te-card-btn:hover i {
      transform: translateX(3px);
    }

    /* ================= BOTTOM LOGIN LINK ================= */
    .te-bottom-link-wrap {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      font-size: 15px;
      color: #64748b;
      margin-top: 10px;
      margin-bottom: 16px;
    }
    .te-bottom-text {
      color: #64748b;
    }
    .te-bottom-login-btn {
      color: #0066ff;
      font-weight: 600;
      text-decoration: underline;
      text-underline-offset: 3px;
      transition: color 0.2s ease;
    }
    .te-bottom-login-btn:hover {
      color: #0047b3;
    }

    /* ================= RESPONSIVE STYLING ================= */
    @media (max-width: 1024px) {
      .te-cards-grid {
        gap: 20px;
        max-width: 960px;
      }
      .te-title {
        font-size: 38px;
      }
      .te-card-img-wrap {
        height: 165px;
      }
    }

    @media (max-width: 860px) {
      .te-cards-grid {
        grid-template-columns: 1fr;
        max-width: 440px;
        margin: 0 auto 30px;
        gap: 24px;
      }
      .te-scenic-bg {
        height: 420px;
      }
      .te-wave-divider {
        top: 320px;
      }
      .te-title {
        font-size: 32px;
      }
      .te-subtitle {
        font-size: 15px;
      }
      .te-card-img-wrap {
        height: 200px;
      }
    }

    @media (max-width: 480px) {
      .te-select-page {
        padding: 24px 16px 24px;
      }
      .te-title {
        font-size: 28px;
      }
      .te-subtitle {
        font-size: 14px;
      }
      .te-brand-text {
        font-size: 21px;
      }
      .te-card {
        padding: 16px 16px 20px;
      }
    }
  `]
})
export class RegisterSelectComponent {}
