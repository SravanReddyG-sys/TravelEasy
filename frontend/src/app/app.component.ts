import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './components/navbar/navbar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent],
  template: `
    <div class="d-flex flex-column min-vh-100">
      <app-navbar></app-navbar>
      
      <main class="flex-grow-1">
        <router-outlet></router-outlet>
      </main>

      <footer class="bg-dark text-white py-4 mt-auto border-top border-secondary">
        <div class="container text-center">
          <p class="mb-1 fw-semibold">&copy; 2026 TravelEasy Inc. All Rights Reserved.</p>
          <small class="text-muted">Software Application Architecture (SWE4007) Project | Microservices & Angular Edition</small>
        </div>
      </footer>
    </div>
  `
})
export class AppComponent {}
