import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  GuestFormEntry,
  GuestGender,
  GuestType,
  PhysicalRoomAllocation,
  RoomValidationResult
} from '../../../../../models/hotel.model';
import {
  ADULT_GENDER_OPTIONS,
  CHILD_AGE_OPTIONS
} from '../../../../../shared/utils/guest-allocation.util';

@Component({
  selector: 'app-physical-room-guest-card',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div
      class="room-guest-card bg-white rounded-4 border shadow-sm overflow-hidden mb-3"
      [class.border-danger]="showValidation && !validationResult.valid"
      [attr.data-room-key]="room.roomKey"
    >
      <!-- Accordion Header -->
      <button
        type="button"
        class="room-card-header w-100 border-0 d-flex flex-wrap align-items-center justify-content-between gap-2 px-4 py-3 text-start"
        [class.is-expanded]="room.expanded"
        (click)="onToggleExpand()"
        [attr.aria-expanded]="room.expanded"
      >
        <div class="d-flex align-items-center gap-2 flex-wrap">
          <span class="room-icon-wrap d-inline-flex align-items-center justify-content-center">
            <i class="fa-solid fa-bed text-teal"></i>
          </span>
          <span class="fw-bold text-navy fs-6">
            Room {{ room.roomSequenceOverall }} – {{ room.roomTypeName }}
          </span>
          <span class="text-muted small fw-medium">
            (Up to {{ room.maxOccupancy }} {{ room.maxOccupancy === 1 ? 'guest' : 'guests' }})
          </span>
        </div>

        <div class="d-flex align-items-center gap-3 ms-auto">
          <span
            class="guest-count-pill px-3 py-1 rounded-pill fw-semibold small"
            [ngClass]="statusPillClass"
          >
            {{ room.occupants.length }} / {{ room.maxOccupancy }} Guests Added
          </span>
          <i
            class="fa-solid text-navy small"
            [class.fa-chevron-up]="room.expanded"
            [class.fa-chevron-down]="!room.expanded"
          ></i>
        </div>
      </button>

      <!-- Accordion Body -->
      <div *ngIf="room.expanded" class="room-card-body p-4 border-top bg-white">
        <div class="row g-4 align-items-start">

          <!-- Left Column: Add Guest Controls -->
          <div class="col-lg-3 col-md-4 border-end-md pe-md-4">
            <div class="fw-bold text-navy small mb-2">Add Guest</div>

            <div class="d-flex flex-row flex-md-column flex-xl-row gap-3 mb-3">
              <label class="d-inline-flex align-items-center gap-2 cursor-pointer small fw-medium text-navy mb-0">
                <input
                  type="radio"
                  class="form-check-input mt-0 guest-type-radio"
                  [name]="'addType-' + room.roomKey"
                  value="ADULT"
                  [checked]="room.selectedAddGuestType === 'ADULT'"
                  (change)="onAddGuestTypeChange('ADULT')"
                />
                <span>Adult</span>
              </label>

              <label class="d-inline-flex align-items-center gap-2 cursor-pointer small fw-medium text-navy mb-0">
                <input
                  type="radio"
                  class="form-check-input mt-0 guest-type-radio"
                  [name]="'addType-' + room.roomKey"
                  value="CHILD"
                  [checked]="room.selectedAddGuestType === 'CHILD'"
                  (change)="onAddGuestTypeChange('CHILD')"
                />
                <span>Child (Age 0 – 17)</span>
              </label>
            </div>

            <button
              type="button"
              class="btn btn-add-guest w-100 rounded-3 fw-semibold py-2 px-3 d-inline-flex align-items-center justify-content-center gap-2"
              [disabled]="isAtMaxCapacity"
              (click)="onAddGuest()"
              [attr.data-testid]="'add-guest-' + room.roomKey"
            >
              <i class="fa-solid fa-plus small"></i>
              <span>Add Guest</span>
            </button>

            <div *ngIf="isAtMaxCapacity" class="text-amber small fw-medium mt-2 d-flex align-items-start gap-1">
              <i class="fa-solid fa-circle-info mt-1"></i>
              <span>Maximum room capacity ({{ room.maxOccupancy }}) reached.</span>
            </div>
          </div>

          <!-- Right Column: Guests for this Room -->
          <div class="col-lg-9 col-md-8">
            <div class="fw-bold text-navy small mb-2">
              Guests for this Room ({{ room.occupants.length }})
            </div>

            <!-- Empty Occupants Prompt -->
            <div
              *ngIf="room.occupants.length === 0"
              class="empty-guests-box rounded-3 border border-dashed p-4 text-center text-muted small"
            >
              <i class="fa-regular fa-user fs-5 mb-2 d-block text-teal"></i>
              No guests added for Room {{ room.roomSequenceOverall }} yet. Select <strong>Adult</strong> or
              <strong>Child (Age 0 – 17)</strong> on the left and click <strong>+ Add Guest</strong>.
            </div>

            <!-- Dynamic Occupant Rows -->
            <div class="d-flex flex-column gap-2" *ngIf="room.occupants.length > 0">
              <div
                *ngFor="let guest of room.occupants; let idx = index; trackBy: trackByGuestId"
                class="guest-entry-row rounded-3 border p-3 bg-white"
                [class.border-danger-subtle]="hasGuestError(guest.id)"
                [attr.data-guest-row]="guest.id"
              >
                <div class="row g-2 align-items-start">

                  <!-- Guest Role Label (Adult 1, Child 1, etc.) -->
                  <div class="col-12 col-xl-2 d-flex align-items-center pt-xl-3">
                    <span class="d-inline-flex align-items-center gap-2 fw-bold text-navy small text-nowrap">
                      <i
                        class="fa-regular fa-user"
                        [class.text-navy]="guest.type === 'ADULT'"
                        [class.text-teal]="guest.type === 'CHILD'"
                      ></i>
                      <span>{{ getGuestTypeLabel(guest, idx) }}</span>
                    </span>
                  </div>

                  <!-- Full Name -->
                  <div class="col-12" [ngClass]="guest.type === 'ADULT' ? 'col-md-5 col-xl-4' : 'col-md-7 col-xl-6'">
                    <label class="form-label smaller fw-semibold text-navy mb-1">
                      Full Name <span class="text-danger">*</span>
                    </label>
                    <input
                      type="text"
                      class="form-control form-control-sm"
                      [class.is-invalid]="shouldShowFieldError(guest.id, 'name')"
                      placeholder="Enter full name"
                      maxlength="255"
                      [ngModel]="guest.name"
                      (ngModelChange)="onGuestNameChange(guest, $event)"
                      (blur)="markGuestFieldTouched(guest.id, 'name')"
                      [attr.aria-label]="getGuestTypeLabel(guest, idx) + ' Full Name'"
                    />
                    <div *ngIf="shouldShowFieldError(guest.id, 'name')" class="invalid-feedback d-block smaller">
                      {{ getFieldError(guest.id, 'name') }}
                    </div>
                  </div>

                  <!-- Age (Adult numeric >= 18 OR Child dropdown 0..17) -->
                  <div class="col-6 col-md-3 col-xl-2">
                    <label class="form-label smaller fw-semibold text-navy mb-1">
                      Age <span class="text-danger">*</span>
                    </label>

                    <ng-container *ngIf="guest.type === 'ADULT'; else childAgeDropdown">
                      <input
                        type="number"
                        class="form-control form-control-sm"
                        [class.is-invalid]="shouldShowFieldError(guest.id, 'age')"
                        min="18"
                        max="120"
                        step="1"
                        placeholder="18+"
                        [ngModel]="guest.age"
                        (ngModelChange)="onGuestAgeChange(guest, $event)"
                        (blur)="markGuestFieldTouched(guest.id, 'age')"
                        [attr.aria-label]="getGuestTypeLabel(guest, idx) + ' Age'"
                      />
                    </ng-container>

                    <ng-template #childAgeDropdown>
                      <select
                        class="form-select form-select-sm"
                        [class.is-invalid]="shouldShowFieldError(guest.id, 'age')"
                        [ngModel]="guest.age"
                        (ngModelChange)="onChildAgeSelectChange(guest, $event)"
                        (blur)="markGuestFieldTouched(guest.id, 'age')"
                        [attr.aria-label]="getGuestTypeLabel(guest, idx) + ' Age'"
                      >
                        <option [ngValue]="null" disabled>Select age</option>
                        <option *ngFor="let cAge of childAgeOptions" [ngValue]="cAge">
                          {{ cAge }} {{ cAge === 1 ? 'year' : 'years' }}
                        </option>
                      </select>
                    </ng-template>

                    <div *ngIf="shouldShowFieldError(guest.id, 'age')" class="invalid-feedback d-block smaller">
                      {{ getFieldError(guest.id, 'age') }}
                    </div>
                  </div>

                  <!-- Gender (ONLY rendered for Adults) -->
                  <div *ngIf="guest.type === 'ADULT'" class="col-6 col-md-3 col-xl-3">
                    <label class="form-label smaller fw-semibold text-navy mb-1">
                      Gender <span class="text-danger">*</span>
                    </label>
                    <select
                      class="form-select form-select-sm"
                      [class.is-invalid]="shouldShowFieldError(guest.id, 'gender')"
                      [ngModel]="guest.gender"
                      (ngModelChange)="onAdultGenderChange(guest, $event)"
                      (blur)="markGuestFieldTouched(guest.id, 'gender')"
                      [attr.aria-label]="getGuestTypeLabel(guest, idx) + ' Gender'"
                    >
                      <option [ngValue]="null" disabled>Select gender</option>
                      <option *ngFor="let gOpt of genderOptions" [ngValue]="gOpt.value">
                        {{ gOpt.label }}
                      </option>
                    </select>
                    <div *ngIf="shouldShowFieldError(guest.id, 'gender')" class="invalid-feedback d-block smaller">
                      {{ getFieldError(guest.id, 'gender') }}
                    </div>
                  </div>

                  <!-- Spacer to align delete button on Child rows -->
                  <div *ngIf="guest.type === 'CHILD'" class="d-none d-xl-block col-xl-1"></div>

                  <!-- Remove Guest Button -->
                  <div class="col-auto ms-auto d-flex align-items-end pt-md-3 mt-md-1">
                    <button
                      type="button"
                      class="btn btn-remove-guest btn-sm rounded-3 d-inline-flex align-items-center justify-content-center"
                      (click)="onRemoveGuest(guest.id)"
                      [title]="'Remove ' + getGuestTypeLabel(guest, idx)"
                      [attr.aria-label]="'Remove ' + getGuestTypeLabel(guest, idx)"
                    >
                      <i class="fa-regular fa-trash-can"></i>
                    </button>
                  </div>

                </div>
              </div>
            </div>

          </div>
        </div>

        <!-- Room-Level Validation Alert (shown on Submit or validation trigger) -->
        <div
          *ngIf="showValidation && validationResult.roomErrors.length > 0"
          class="alert alert-danger py-2 px-3 rounded-3 mt-3 mb-0 small d-flex align-items-center gap-2"
        >
          <i class="fa-solid fa-triangle-exclamation"></i>
          <span>{{ validationResult.roomErrors[0] }}</span>
        </div>

        <!-- Room Requirements Information Strip -->
        <div class="room-requirements-strip rounded-3 px-3 py-2 mt-3 small d-flex align-items-center gap-2">
          <i class="fa-solid fa-circle-info text-teal"></i>
          <span>
            Room requirements: At least 1 adult, maximum {{ room.maxOccupancy }}
            {{ room.maxOccupancy === 1 ? 'guest' : 'guests' }}. Adult age is required (18+). Child age must be between 0 and 17 years.
          </span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .text-navy {
      color: #0F172A;
    }
    .text-teal {
      color: #0891B2;
    }
    .text-amber {
      color: #B45309;
    }
    .smaller {
      font-size: 0.76rem;
    }
    .cursor-pointer {
      cursor: pointer;
    }
    .room-card-header {
      background-color: #F8FAFC;
      transition: background-color 0.15s ease;
    }
    .room-card-header.is-expanded {
      background-color: #EEF6FF;
    }
    .room-card-header:hover {
      background-color: #EBF4FF;
    }
    .pill-ready {
      background-color: #DCFCE7;
      color: #166534;
    }
    .pill-pending {
      background-color: #FEF3C7;
      color: #92400E;
    }
    .pill-invalid {
      background-color: #FEE2E2;
      color: #991B1B;
    }
    .btn-add-guest {
      color: #0891B2;
      background-color: #FFFFFF;
      border: 1.5px solid #0891B2;
      transition: all 0.15s ease;
    }
    .btn-add-guest:hover:not(:disabled) {
      background-color: #0891B2;
      color: #FFFFFF;
    }
    .btn-add-guest:disabled {
      opacity: 0.55;
      cursor: not-allowed;
    }
    .guest-type-radio:checked {
      background-color: #0891B2;
      border-color: #0891B2;
    }
    .empty-guests-box {
      background-color: #F8FAFC;
      border-style: dashed !important;
    }
    .guest-entry-row {
      border-color: #E2E8F0 !important;
    }
    .btn-remove-guest {
      width: 34px;
      height: 32px;
      background-color: #FEF2F2;
      color: #DC2626;
      border: 1px solid #FECACA;
    }
    .btn-remove-guest:hover {
      background-color: #FEE2E2;
      color: #B91C1C;
    }
    .room-requirements-strip {
      background-color: #EFF6FF;
      color: #1E3A8A;
      border: 1px solid #DBEAFE;
    }
    @media (min-width: 768px) {
      .border-end-md {
        border-right: 1px solid #E2E8F0;
      }
    }
  `]
})
export class PhysicalRoomGuestCardComponent {
  @Input({ required: true }) room!: PhysicalRoomAllocation;
  @Input({ required: true }) validationResult!: RoomValidationResult;
  @Input() showValidation = false;

  @Output() toggleExpand = new EventEmitter<string>();
  @Output() addGuestTypeChange = new EventEmitter<{ roomKey: string; type: GuestType }>();
  @Output() addGuest = new EventEmitter<string>();
  @Output() removeGuest = new EventEmitter<{ roomKey: string; guestId: string }>();
  @Output() guestChange = new EventEmitter<{ roomKey: string; guest: GuestFormEntry }>();

  readonly genderOptions = ADULT_GENDER_OPTIONS;
  readonly childAgeOptions = CHILD_AGE_OPTIONS;

  private touchedFields = new Set<string>();

  get isAtMaxCapacity(): boolean {
    return this.room.occupants.length >= this.room.maxOccupancy;
  }

  get statusPillClass(): string {
    if (this.showValidation && !this.validationResult.valid) {
      return 'pill-invalid';
    }
    if (this.validationResult.valid && this.room.occupants.length > 0) {
      return 'pill-ready';
    }
    return 'pill-pending';
  }

  trackByGuestId(_index: number, guest: GuestFormEntry): string {
    return guest.id;
  }

  getGuestTypeLabel(guest: GuestFormEntry, overallIndex: number): string {
    let typeCount = 0;
    for (let i = 0; i <= overallIndex && i < this.room.occupants.length; i += 1) {
      if (this.room.occupants[i].type === guest.type) {
        typeCount += 1;
      }
    }
    const seq = Math.max(1, typeCount);
    return guest.type === 'ADULT' ? `Adult ${seq}` : `Child ${seq}`;
  }

  onToggleExpand(): void {
    this.toggleExpand.emit(this.room.roomKey);
  }

  onAddGuestTypeChange(type: GuestType): void {
    this.addGuestTypeChange.emit({ roomKey: this.room.roomKey, type });
  }

  onAddGuest(): void {
    if (this.isAtMaxCapacity) return;
    this.addGuest.emit(this.room.roomKey);
  }

  onRemoveGuest(guestId: string): void {
    this.touchedFields.delete(`${guestId}:name`);
    this.touchedFields.delete(`${guestId}:age`);
    this.touchedFields.delete(`${guestId}:gender`);
    this.removeGuest.emit({ roomKey: this.room.roomKey, guestId });
  }

  onGuestNameChange(guest: GuestFormEntry, value: string): void {
    this.touchedFields.add(`${guest.id}:name`);
    this.guestChange.emit({
      roomKey: this.room.roomKey,
      guest: { ...guest, name: value ?? '' }
    });
  }

  onGuestAgeChange(guest: GuestFormEntry, value: unknown): void {
    this.touchedFields.add(`${guest.id}:age`);
    let parsedAge: number | null = null;
    if (value !== null && value !== undefined && value !== '') {
      const numeric = Number(value);
      parsedAge = Number.isNaN(numeric) ? null : numeric;
    }
    this.guestChange.emit({
      roomKey: this.room.roomKey,
      guest: { ...guest, age: parsedAge }
    });
  }

  onChildAgeSelectChange(guest: GuestFormEntry, value: unknown): void {
    this.touchedFields.add(`${guest.id}:age`);
    const parsedAge = value === null || value === undefined || value === '' ? null : Number(value);
    this.guestChange.emit({
      roomKey: this.room.roomKey,
      guest: { ...guest, age: parsedAge, gender: null }
    });
  }

  onAdultGenderChange(guest: GuestFormEntry, value: GuestGender | null): void {
    this.touchedFields.add(`${guest.id}:gender`);
    this.guestChange.emit({
      roomKey: this.room.roomKey,
      guest: { ...guest, gender: value }
    });
  }

  markGuestFieldTouched(guestId: string, field: 'name' | 'age' | 'gender'): void {
    this.touchedFields.add(`${guestId}:${field}`);
  }

  shouldShowFieldError(guestId: string, field: 'name' | 'age' | 'gender'): boolean {
    const err = this.getFieldError(guestId, field);
    if (!err) return false;
    return this.showValidation || this.touchedFields.has(`${guestId}:${field}`);
  }

  hasGuestError(guestId: string): boolean {
    if (!this.showValidation) return false;
    const errs = this.validationResult?.guestErrors?.[guestId];
    return !!(errs && (errs.name || errs.age || errs.gender));
  }

  getFieldError(guestId: string, field: 'name' | 'age' | 'gender'): string {
    return this.validationResult?.guestErrors?.[guestId]?.[field] || '';
  }
}
