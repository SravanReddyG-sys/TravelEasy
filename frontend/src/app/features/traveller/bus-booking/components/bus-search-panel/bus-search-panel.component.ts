import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-bus-search-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="bus-search-banner mb-4 shadow-sm">
      <div class="bus-banner-content">
        <!-- Page Title and Subtitle inside Banner -->
        <div class="bus-banner-header mb-3">
          <h3 class="bus-banner-title mb-1">Book Buses</h3>
          <p class="bus-banner-subtitle mb-0">Find and book your bus tickets for a safe and comfortable journey</p>
        </div>

        <!-- White Search Form Panel -->
        <div class="bus-search-card">
          <form (ngSubmit)="onSearchSubmit()">
            <div class="bus-search-row">
              
              <!-- From -->
              <div class="bus-control-item bus-control-from">
                <div class="bus-input-box">
                  <label for="busOriginInput"><i class="fa-solid fa-location-dot text-primary me-1"></i>From</label>
                  <div class="bus-input-inner">
                    <input 
                      id="busOriginInput" 
                      type="text" 
                      [(ngModel)]="origin" 
                      (ngModelChange)="originChange.emit(origin)"
                      name="busOrigin" 
                      placeholder="Enter origin city" 
                      required
                    >
                    <button type="button" *ngIf="origin" (click)="clearOrigin()" class="btn-clear-field" title="Clear origin" aria-label="Clear origin">
                      <i class="fa-solid fa-xmark"></i>
                    </button>
                  </div>
                </div>
              </div>
              
              <!-- Swap Origin & Destination -->
              <div class="bus-control-swap">
                <button type="button" class="btn-swap-cities" (click)="onSwapCities()" title="Swap Origin and Destination" aria-label="Swap cities">
                  <i class="fa-solid fa-arrow-right-arrow-left"></i>
                </button>
              </div>

              <!-- To -->
              <div class="bus-control-item bus-control-to">
                <div class="bus-input-box">
                  <label for="busDestinationInput"><i class="fa-solid fa-location-dot text-danger me-1"></i>To</label>
                  <div class="bus-input-inner">
                    <input 
                      id="busDestinationInput" 
                      type="text" 
                      [(ngModel)]="destination" 
                      (ngModelChange)="destinationChange.emit(destination)"
                      name="busDestination" 
                      placeholder="Enter destination city" 
                      required
                    >
                    <button type="button" *ngIf="destination" (click)="clearDestination()" class="btn-clear-field" title="Clear destination" aria-label="Clear destination">
                      <i class="fa-solid fa-xmark"></i>
                    </button>
                  </div>
                </div>
              </div>

              <!-- Journey Date -->
              <div class="bus-control-item bus-control-date">
                <div class="bus-input-box">
                  <label for="busDateInput"><i class="fa-solid fa-calendar-days text-primary me-1"></i>Journey Date</label>
                  <div class="bus-input-inner">
                    <input 
                      id="busDateInput" 
                      type="date" 
                      [(ngModel)]="journeyDate" 
                      (ngModelChange)="journeyDateChange.emit(journeyDate)"
                      name="busDate" 
                      required
                    >
                  </div>
                </div>
              </div>

              <!-- Search Button -->
              <div class="bus-control-action">
                <button type="submit" class="btn-search-buses" [disabled]="isSearching">
                  <i *ngIf="!isSearching" class="fa-solid fa-magnifying-glass"></i>
                  <span *ngIf="isSearching" class="spinner-border spinner-border-sm me-1"></span>
                  <span>{{ isSearching ? 'Searching...' : 'Search Buses' }}</span>
                </button>
              </div>
            </div>
          </form>
        </div>

        <!-- Validation Error Message -->
        <div *ngIf="searchError" class="alert alert-danger py-2 px-3 mt-3 mb-0 rounded-3 small bus-search-error-alert">
          <i class="fa-solid fa-circle-exclamation me-1"></i> {{ searchError }}
        </div>
      </div>
    </div>
  `,
  styles: [`
    .bus-search-banner {
      background-color: #F0F6FE;
      background-image: 
        linear-gradient(90deg, rgba(240, 246, 254, 0.96) 0%, rgba(240, 246, 254, 0.90) 35%, rgba(240, 246, 254, 0.35) 65%, rgba(240, 246, 254, 0) 80%),
        url('/assets/images/scenic-search-panel.png');
      background-position: right center;
      background-size: cover;
      background-repeat: no-repeat;
      border: 1px solid #D4E5F9;
      border-radius: 20px;
      position: relative;
      overflow: hidden;
      padding: 24px 30px;
      min-height: 180px;
    }
    .bus-banner-content {
      position: relative;
      z-index: 2;
    }
    .bus-banner-header {
      margin-bottom: 14px;
    }
    .bus-banner-title {
      color: #0F172A;
      font-size: 1.45rem;
      font-weight: 800;
      letter-spacing: -0.3px;
    }
    .bus-banner-subtitle {
      color: #64748B;
      font-size: 0.88rem;
      font-weight: 500;
    }
    .bus-search-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 14px;
      padding: 8px 10px;
      box-shadow: 0 4px 18px rgba(15, 23, 42, 0.07);
      max-width: 860px;
    }
    .bus-search-row {
      display: flex;
      align-items: center;
      gap: 8px;
      width: 100%;
    }
    .bus-control-item {
      flex: 1 1 0;
      min-width: 0;
    }
    .bus-control-swap {
      flex: 0 0 auto;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .bus-control-action {
      flex: 0 0 auto;
    }
    .bus-input-box {
      background: #F8FAFC;
      border: 1px solid #E2EAF4;
      border-radius: 10px;
      padding: 6px 12px;
      height: 52px;
      display: flex;
      flex-direction: column;
      justify-content: center;
      transition: border-color 0.15s ease, box-shadow 0.15s ease, background-color 0.15s ease;
    }
    .bus-input-box:focus-within {
      background: #FFFFFF;
      border-color: #0875E1;
      box-shadow: 0 0 0 3px rgba(8, 117, 225, 0.12);
    }
    .bus-input-box label {
      font-size: 0.70rem;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      font-weight: 700;
      color: #64748B;
      margin-bottom: 2px;
      display: flex;
      align-items: center;
      user-select: none;
      cursor: pointer;
    }
    .bus-input-inner {
      display: flex;
      align-items: center;
      position: relative;
    }
    .bus-input-inner input {
      border: none;
      padding: 0;
      font-size: 0.92rem;
      font-weight: 600;
      color: #0F172A;
      background: transparent;
      width: 100%;
      line-height: 1.2;
    }
    .bus-input-inner input:focus {
      outline: none;
      box-shadow: none;
    }
    .bus-input-inner input[type="date"] {
      font-family: inherit;
      cursor: pointer;
    }
    .btn-clear-field {
      background: transparent;
      border: none;
      color: #94A3B8;
      padding: 0 2px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.75rem;
      line-height: 1;
      transition: color 0.15s ease;
    }
    .btn-clear-field:hover {
      color: #334155;
    }
    .btn-swap-cities {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: #FFFFFF;
      border: 1px solid #D4E5F9;
      color: #0875E1;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.82rem;
      transition: all 0.2s ease;
      box-shadow: 0 2px 6px rgba(8, 117, 225, 0.08);
      cursor: pointer;
      padding: 0;
    }
    .btn-swap-cities:hover {
      background-color: #0875E1;
      color: #FFFFFF;
      transform: rotate(180deg);
    }
    .btn-search-buses {
      background-color: #0875E1;
      color: #FFFFFF;
      font-weight: 700;
      font-size: 0.92rem;
      border-radius: 10px;
      padding: 0 22px;
      border: none;
      height: 52px;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      white-space: nowrap;
      transition: all 0.2s ease;
      box-shadow: 0 4px 14px rgba(8, 117, 225, 0.25);
      cursor: pointer;
    }
    .btn-search-buses:hover:not(:disabled) {
      background-color: #0662be;
      box-shadow: 0 6px 18px rgba(8, 117, 225, 0.35);
      transform: translateY(-1px);
    }
    .btn-search-buses:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }
    .bus-search-error-alert {
      max-width: 860px;
    }

    @media (max-width: 991.98px) {
      .bus-search-banner {
        padding: 20px;
      }
      .bus-search-card {
        max-width: 100%;
      }
      .bus-search-row {
        flex-wrap: wrap;
      }
      .bus-control-item.bus-control-from {
        flex: 1 1 calc(50% - 30px);
      }
      .bus-control-swap {
        flex: 0 0 auto;
      }
      .bus-control-item.bus-control-to {
        flex: 1 1 calc(50% - 30px);
      }
      .bus-control-item.bus-control-date {
        flex: 1 1 calc(60% - 10px);
      }
      .bus-control-action {
        flex: 1 1 calc(40% - 10px);
      }
      .btn-search-buses {
        width: 100%;
      }
    }

    @media (max-width: 767.98px) {
      .bus-search-banner {
        padding: 16px;
      }
      .bus-banner-title {
        font-size: 1.25rem;
      }
      .bus-banner-subtitle {
        font-size: 0.82rem;
      }
      .bus-search-row {
        flex-direction: column;
        align-items: stretch;
        gap: 8px;
      }
      .bus-control-item,
      .bus-control-action {
        width: 100%;
        flex: 1 1 auto;
      }
      .bus-control-swap {
        align-self: center;
        margin: 2px 0;
      }
      .btn-search-buses {
        width: 100%;
      }
    }
  `]
})
export class BusSearchPanelComponent {
  @Input() origin = '';
  @Input() destination = '';
  @Input() journeyDate = '';
  @Input() isSearching = false;
  @Input() searchError: string | null = null;

  @Output() originChange = new EventEmitter<string>();
  @Output() destinationChange = new EventEmitter<string>();
  @Output() journeyDateChange = new EventEmitter<string>();
  @Output() swapCities = new EventEmitter<void>();
  @Output() search = new EventEmitter<void>();

  onSwapCities() {
    this.swapCities.emit();
  }

  onSearchSubmit() {
    this.search.emit();
  }

  clearOrigin() {
    this.origin = '';
    this.originChange.emit('');
  }

  clearDestination() {
    this.destination = '';
    this.destinationChange.emit('');
  }
}
