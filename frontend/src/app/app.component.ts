import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterOutlet, NavigationEnd } from '@angular/router';
import { NavbarComponent } from './components/navbar/navbar.component';
import { HeaderComponent } from './components/header/header.component';
import { FooterComponent } from './components/footer/footer.component';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent, HeaderComponent, FooterComponent],
  template: `
    <div class="d-flex flex-column min-vh-100 position-relative">
      <!-- Standard Travel Easy Header on all public-facing pages -->
      <app-header *ngIf="isPublicPage" [transparent]="true"></app-header>

      <!-- Role Console Navbar on authenticated dashboards -->
      <app-navbar *ngIf="isDashboardPage"></app-navbar>

      <main class="flex-grow-1 d-flex flex-column">
        <router-outlet></router-outlet>
      </main>

      <!-- Standard Travel Easy Footer on all public-facing pages -->
      <app-footer *ngIf="isPublicPage"></app-footer>

      <!-- Role Console Footer on authenticated dashboards -->
      <footer *ngIf="isDashboardPage" class="bg-dark text-white py-4 mt-auto border-top border-secondary">
        <div class="container text-center">
          <p class="mb-1 fw-semibold">&copy; 2026 TravelEasy Inc. All Rights Reserved.</p>
          <small class="text-muted">Software Application Architecture (SWE4007) Project | Microservices &amp; Angular Edition</small>
        </div>
      </footer>
    </div>
  `
})
export class AppComponent {
  isHomePage = true;
  isDashboardPage = false;
  isPublicPage = true;

  constructor(private router: Router) {
    this.updateLayout(this.router.url);

    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      const url: string = event.urlAfterRedirects || event.url || '';
      this.updateLayout(url);
    });
  }

  private updateLayout(url: string) {
    const path = url.split('?')[0].split('#')[0].replace(/\/+$/, '');
    this.isDashboardPage = path === '/customer' || path === '/bus-operator' || path === '/hotel-manager' || path === '/admin';
    this.isPublicPage = !this.isDashboardPage;
    this.isHomePage = path === '' || path === '/home';
  }
}
