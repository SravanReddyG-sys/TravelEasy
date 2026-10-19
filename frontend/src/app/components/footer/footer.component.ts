import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { NewsletterService } from '../../services/newsletter.service';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <!-- ===================== FOOTER ===================== -->
    <footer class="te-footer" id="footer">
      <div class="te-container">
        <div class="te-footer-grid">
          <!-- Brand col -->
          <div class="te-footer-brand-col">
            <div class="te-footer-brand">
              <span class="te-brand-icon te-brand-icon--sm">
                <i class="fa-solid fa-paper-plane"></i>
              </span>
              <span class="te-brand-text-footer">Travel <span class="te-brand-blue">Easy</span></span>
            </div>
            <p class="te-footer-brand-desc">Making Travel Simple for Everyone</p>
          </div>

          <!-- Quick links -->
          <div class="te-footer-col">
            <h4 class="te-footer-heading">Quick Links</h4>
            <ul class="te-footer-links">
              <li><a routerLink="/home">Home</a></li>
              <li><a routerLink="/customer" fragment="buses">Buses</a></li>
              <li><a routerLink="/customer" fragment="hotels">Hotels</a></li>
              <li><a (click)="goToAbout($event)" href="javascript:void(0)">About Us</a></li>
            </ul>
          </div>

          <!-- Support -->
          <div class="te-footer-col">
            <h4 class="te-footer-heading">Support</h4>
            <ul class="te-footer-links">
              <li><a href="javascript:void(0)">Help Center</a></li>
              <li><a href="javascript:void(0)">Contact Us</a></li>
              <li><a href="javascript:void(0)">FAQs</a></li>
              <li><a href="javascript:void(0)">Terms &amp; Conditions</a></li>
              <li><a href="javascript:void(0)">Privacy Policy</a></li>
            </ul>
          </div>

          <!-- Follow us -->
          <div class="te-footer-col">
            <h4 class="te-footer-heading">Follow Us</h4>
            <div class="te-social-icons">
              <a class="te-social-icon" href="https://www.facebook.com/" target="_blank" rel="noopener noreferrer" aria-label="Facebook"><i class="fa-brands fa-facebook-f"></i></a>
              <a class="te-social-icon" href="https://www.instagram.com/" target="_blank" rel="noopener noreferrer" aria-label="Instagram"><i class="fa-brands fa-instagram"></i></a>
              <a class="te-social-icon" href="https://x.com/" target="_blank" rel="noopener noreferrer" aria-label="Twitter"><i class="fa-brands fa-x-twitter"></i></a>
              <a class="te-social-icon" href="https://www.youtube.com/" target="_blank" rel="noopener noreferrer" aria-label="YouTube"><i class="fa-brands fa-youtube"></i></a>
              <a class="te-social-icon" href="https://www.linkedin.com/in/sravan-reddy-g-3546242b4/?isSelfProfile=true" target="_blank" rel="noopener noreferrer" aria-label="LinkedIn"><i class="fa-brands fa-linkedin-in"></i></a>
            </div>
          </div>

          <!-- Newsletter -->
          <div class="te-footer-col">
            <h4 class="te-footer-heading">Subscribe to Our Newsletter</h4>
            <p class="te-footer-newsletter-sub">Get the latest updates and travel deals</p>
            <form (ngSubmit)="subscribeNewsletter()" class="te-newsletter-form">
              <div class="te-newsletter-input-wrap">
                <i class="fa-regular fa-envelope te-newsletter-icon"></i>
                <input
                  type="email"
                  class="te-newsletter-input"
                  [class.te-newsletter-input--error]="newsletterFeedback?.type === 'error'"
                  [(ngModel)]="newsletterEmail"
                  (input)="onNewsletterInput()"
                  name="newsletterEmail"
                  placeholder="Enter your email"
                  required
                >
              </div>
              <button
                type="submit"
                class="te-newsletter-btn"
                [disabled]="newsletterLoading"
              >
                <span *ngIf="newsletterLoading" class="spinner-border spinner-border-sm me-1"></span>
                <span>{{ newsletterLoading ? 'Subscribing...' : 'Subscribe' }}</span>
              </button>
            </form>
            <div *ngIf="newsletterFeedback" class="te-newsletter-feedback" [ngClass]="'te-newsletter-feedback--' + newsletterFeedback.type">
              <i class="fa-solid" [ngClass]="{
                'fa-circle-check': newsletterFeedback.type === 'success',
                'fa-circle-info': newsletterFeedback.type === 'info',
                'fa-circle-exclamation': newsletterFeedback.type === 'error'
              }"></i>
              <span>{{ newsletterFeedback.message }}</span>
            </div>
          </div>
        </div>

        <div class="te-footer-bottom">
          <span>&copy; 2026 Travel Easy. All rights reserved.</span>
          <span>Made with <i class="fa-solid fa-heart te-heart"></i> for travellers across India</span>
        </div>
      </div>
    </footer>
  `,
  styles: [`
    :host {
      display: block;
      font-family: 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
      color: #1e293b;
    }

    .te-container {
      width: 100%;
      max-width: 1200px;
      margin: 0 auto;
      padding: 0 32px;
    }

    /* ============ FOOTER ============ */
    .te-footer {
      background: #f8fafc;
      border-top: 1px solid #e2e8f0;
      padding: 48px 0 0;
      margin-top: auto;
    }
    .te-footer-grid {
      display: grid;
      grid-template-columns: 1.2fr 0.9fr 1fr 1fr 1.6fr;
      gap: 32px;
      margin-bottom: 40px;
    }
    .te-footer-brand {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;
    }
    .te-brand-icon--sm {
      width: 30px; height: 30px;
      font-size: 13px;
      background: #0d5bd9;
      border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
      color: white;
    }
    .te-brand-text-footer {
      font-size: 18px;
      font-weight: 700;
      color: #0f172a;
    }
    .te-brand-blue { color: #0d5bd9; }
    .te-footer-brand-desc {
      font-size: 13px;
      color: #64748b;
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
      cursor: pointer;
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
      .te-footer-grid { grid-template-columns: 1fr 1fr; }
    }
    @media (max-width: 768px) {
      .te-footer-grid { grid-template-columns: 1fr; gap: 24px; }
      .te-footer-bottom { flex-direction: column; gap: 8px; text-align: center; }
      .te-container { padding: 0 16px; }
    }
  `]
})
export class FooterComponent {
  newsletterEmail = '';
  newsletterLoading = false;
  newsletterFeedback: { type: 'success' | 'info' | 'error'; message: string } | null = null;

  constructor(private newsletterService: NewsletterService, private router: Router) {}

  onNewsletterInput() {
    if (this.newsletterFeedback?.type === 'error') {
      this.newsletterFeedback = null;
    }
  }

  goToAbout(event: Event) {
    event.preventDefault();
    const currentPath = this.router.url.split('?')[0].split('#')[0];
    if (currentPath === '/home' || currentPath === '') {
      const el = document.getElementById('about');
      if (el) {
        el.scrollIntoView({ behavior: 'smooth' });
      }
    } else {
      this.router.navigate(['/home'], { fragment: 'about' });
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
