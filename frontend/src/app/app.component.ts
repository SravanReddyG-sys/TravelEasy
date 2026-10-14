import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterOutlet, NavigationEnd } from '@angular/router';
import { NavbarComponent } from './components/navbar/navbar.component';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent],
  template: `
    <div class="d-flex flex-column min-vh-100">
      <!-- Global dark navbar: hidden on the /home landing page (which has its own navbar) -->
      <app-navbar *ngIf="!isHomePage"></app-navbar>

      <main class="flex-grow-1">
        <router-outlet></router-outlet>
      </main>

      <!-- Global footer: hidden on /home (which has its own footer) -->
      <footer *ngIf="!isHomePage" class="bg-dark text-white py-4 mt-auto border-top border-secondary">
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

  constructor(private router: Router) {
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      const url: string = event.urlAfterRedirects || event.url || '';
      const path = url.split('?')[0].replace(/\/+$/, '');
      this.isHomePage = path === '' || path === '/home' || path === '/login' || path === '/register';
    });
  }
}
