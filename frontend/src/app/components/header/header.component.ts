import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule, NavigationEnd } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { User } from '../../models/user.model';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <header class="te-header" [class.te-header--transparent]="transparent" [class.te-header--solid]="!transparent">
      <nav class="te-navbar" [class.te-navbar--transparent]="transparent" [class.te-navbar--solid]="!transparent">
        <div class="te-navbar-inner">
          <!-- Brand -->
          <a class="te-brand" routerLink="/home" (click)="closeMobileMenu()">
            <span class="te-brand-icon">
              <i class="fa-solid fa-paper-plane"></i>
            </span>
            <span class="te-brand-text">Travel <span class="te-brand-blue">Easy</span></span>
          </a>

          <!-- Nav links -->
          <ul class="te-nav-links" id="teNavLinks" [class.te-nav-open]="mobileMenuOpen">
            <li>
              <a class="te-nav-link" routerLink="/home" routerLinkActive="te-nav-link--active" [routerLinkActiveOptions]="{exact: true}" (click)="closeMobileMenu()">Home</a>
            </li>
            <li>
              <a class="te-nav-link" routerLink="/customer" fragment="buses" (click)="closeMobileMenu()">Buses</a>
            </li>
            <li>
              <a class="te-nav-link" routerLink="/customer" fragment="hotels" (click)="closeMobileMenu()">Hotels</a>
            </li>
            <li>
              <a class="te-nav-link" (click)="goToAbout($event)" href="javascript:void(0)">About Us</a>
            </li>
            <li>
              <a class="te-nav-link" (click)="goToSupport($event)" href="javascript:void(0)">Support</a>
            </li>
            <!-- Mobile-only auth action row inside menu drawer -->
            <li class="te-nav-mobile-auth d-md-none">
              <ng-container *ngIf="currentUser; else mobileGuestButtons">
                <div class="te-mobile-user-row">
                  <span class="te-nav-username">
                    <i class="fa-regular fa-user me-1"></i>
                    {{ currentUser.fullName }}
                  </span>
                  <button class="te-btn te-btn-outline w-100 mt-2" (click)="logout()">Logout</button>
                </div>
              </ng-container>
              <ng-template #mobileGuestButtons>
                <div class="d-flex flex-column gap-2 mt-2">
                  <a routerLink="/login" class="te-btn te-btn-outline text-center" (click)="closeMobileMenu()">Login</a>
                  <a routerLink="/register" class="te-btn te-btn-primary text-center" (click)="closeMobileMenu()">Register</a>
                </div>
              </ng-template>
            </li>
          </ul>

          <!-- Auth buttons (desktop) -->
          <div class="te-nav-actions">
            <ng-container *ngIf="currentUser; else guestButtons">
              <span class="te-nav-username">
                <i class="fa-regular fa-user me-1"></i>
                {{ currentUser.fullName }}
              </span>
              <button class="te-btn te-btn-outline" (click)="logout()">Logout</button>
            </ng-container>
            <ng-template #guestButtons>
              <a routerLink="/login" class="te-btn te-btn-white">Login</a>
              <a routerLink="/register" class="te-btn te-btn-primary">Register</a>
            </ng-template>
          </div>

          <!-- Mobile hamburger -->
          <button class="te-hamburger" id="teHamburger" (click)="toggleMobileMenu()" aria-label="Toggle navigation">
            <span></span><span></span><span></span>
          </button>
        </div>
      </nav>
    </header>
  `,
  styles: [`
    :host {
      display: block;
      font-family: 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
    }

    .te-header {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      z-index: 1000;
      width: 100%;
      background: transparent;
      border: none;
      box-shadow: none;
      transition: background-color 0.2s ease, box-shadow 0.2s ease;
    }

    .te-header--transparent {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      z-index: 1000;
      background: transparent;
      border: none;
      box-shadow: none;
    }

    .te-header--solid {
      position: sticky;
      top: 0;
      z-index: 1000;
      background: #ffffff;
      border-bottom: 1px solid #e2e8f0;
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
    }

    .te-navbar {
      width: 100%;
      padding: 0;
      background: transparent;
      border: none;
      box-shadow: none;
    }

    .te-navbar--transparent {
      background: transparent;
      border: none;
      box-shadow: none;
    }

    .te-navbar--solid {
      background: #ffffff;
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
      width: 38px;
      height: 38px;
      background: #0d5bd9;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-size: 16px;
    }

    .te-brand-text {
      font-size: 21px;
      font-weight: 700;
      color: #0f172a;
    }

    .te-brand-blue {
      color: #0d5bd9;
    }

    .te-nav-links {
      display: flex;
      list-style: none;
      margin: 0 0 0 44px;
      padding: 0;
      gap: 30px;
      flex: 1;
      align-items: center;
    }

    .te-nav-link {
      text-decoration: none;
      color: #0f172a;
      font-weight: 600;
      font-size: 14.5px;
      padding: 5px 0;
      border-bottom: 2.5px solid transparent;
      transition: color 0.2s, border-color 0.2s;
      cursor: pointer;
    }

    .te-nav-link:hover {
      color: #0d5bd9;
    }

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
      display: inline-flex;
      align-items: center;
      justify-content: center;
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
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
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
      width: 24px;
      height: 2px;
      background: #1e293b;
      border-radius: 2px;
    }

    .te-nav-mobile-auth {
      display: none;
    }

    @media (max-width: 768px) {
      .te-navbar-inner {
        padding: 12px 20px;
      }
      .te-nav-links {
        display: none;
      }
      .te-hamburger {
        display: flex;
      }
      .te-nav-actions {
        display: none;
      }
      .te-nav-links.te-nav-open {
        display: flex;
        flex-direction: column;
        align-items: stretch;
        position: absolute;
        top: 60px;
        left: 0;
        right: 0;
        background: white;
        padding: 16px 20px;
        border-bottom: 1px solid #e2e8f0;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        z-index: 200;
        margin: 0;
        gap: 16px;
      }
      .te-nav-mobile-auth {
        display: block;
        border-top: 1px solid #e2e8f0;
        padding-top: 12px;
      }
    }
  `]
})
export class HeaderComponent implements OnInit, OnDestroy {
  @Input() transparent: boolean = true;

  currentUser: User | null = null;
  mobileMenuOpen: boolean = false;
  private authSub?: Subscription;

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit() {
    this.authSub = this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
    });
  }

  ngOnDestroy() {
    this.authSub?.unsubscribe();
  }

  toggleMobileMenu() {
    this.mobileMenuOpen = !this.mobileMenuOpen;
  }

  closeMobileMenu() {
    this.mobileMenuOpen = false;
  }

  goToAbout(event: Event) {
    event.preventDefault();
    this.closeMobileMenu();
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

  goToSupport(event: Event) {
    event.preventDefault();
    this.closeMobileMenu();
    const el = document.getElementById('footer');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    } else {
      this.router.navigate(['/home'], { fragment: 'footer' });
    }
  }

  logout() {
    this.closeMobileMenu();
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
