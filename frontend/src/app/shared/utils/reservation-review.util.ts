import {
  CountryDialCodeOption,
  HotelStaySummary,
  PersistedPhysicalRoomState,
  PhysicalRoomAllocation,
  PrimaryGuestDtoPayload,
  PrimaryGuestFormState,
  PrimaryGuestValidationErrors,
  PrimaryGuestValidationResult,
  ReservationReviewJourneyState,
  ReservationReviewSummaryMetrics
} from '../../models/hotel.model';
import {
  isSameStayContextSignature,
  validatePhysicalRoom
} from './guest-allocation.util';

export const RESERVATION_REVIEW_STORAGE_KEY = 'traveleasy_reservation_review_state';
export const DEFAULT_COUNTRY_DIAL_CODE = '+91';
export const PRIMARY_GUEST_MAX_NAME_LENGTH = 255;

// Mirrors backend HotelGuestAllocationValidator patterns for seamless pre-submit checks.
export const BACKEND_EMAIL_REGEX = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
export const BACKEND_MOBILE_REGEX = /^\+?[0-9\s\-().]{7,25}$/;
const ALLOWED_NAME_CHAR_REGEX = /^[\p{L}\p{M}\s'.-]+$/u;
const HAS_LETTER_REGEX = /\p{L}/u;
const ALLOWED_PHONE_SEPARATORS_REGEX = /^[0-9\s\-()]+$/;

export const COUNTRY_DIAL_CODE_OPTIONS: ReadonlyArray<CountryDialCodeOption> = [
  {
    code: 'IN',
    name: 'India',
    dialCode: '+91',
    minDigits: 10,
    maxDigits: 10,
    pattern: /^[6-9]\d{9}$/,
    placeholder: '98765 43210',
    formatHint: 'Enter a valid 10-digit Indian mobile number starting with 6-9.'
  },
  {
    code: 'US',
    name: 'United States / Canada',
    dialCode: '+1',
    minDigits: 10,
    maxDigits: 10,
    pattern: /^\d{10}$/,
    placeholder: '415 555 0132',
    formatHint: 'Enter a valid 10-digit US or Canada phone number.'
  },
  {
    code: 'GB',
    name: 'United Kingdom',
    dialCode: '+44',
    minDigits: 9,
    maxDigits: 10,
    pattern: /^\d{9,10}$/,
    placeholder: '7911 123456',
    formatHint: 'Enter a valid 9 or 10-digit UK mobile number (without leading 0).'
  },
  {
    code: 'AE',
    name: 'United Arab Emirates',
    dialCode: '+971',
    minDigits: 9,
    maxDigits: 9,
    pattern: /^\d{9}$/,
    placeholder: '50 123 4567',
    formatHint: 'Enter a valid 9-digit UAE mobile number.'
  },
  {
    code: 'SG',
    name: 'Singapore',
    dialCode: '+65',
    minDigits: 8,
    maxDigits: 8,
    pattern: /^\d{8}$/,
    placeholder: '9123 4567',
    formatHint: 'Enter a valid 8-digit Singapore mobile number.'
  },
  {
    code: 'AU',
    name: 'Australia',
    dialCode: '+61',
    minDigits: 9,
    maxDigits: 9,
    pattern: /^\d{9}$/,
    placeholder: '412 345 678',
    formatHint: 'Enter a valid 9-digit Australian mobile number.'
  },
  {
    code: 'DE',
    name: 'Germany',
    dialCode: '+49',
    minDigits: 10,
    maxDigits: 11,
    pattern: /^\d{10,11}$/,
    placeholder: '151 23456789',
    formatHint: 'Enter a valid 10 or 11-digit German mobile number.'
  },
  {
    code: 'FR',
    name: 'France',
    dialCode: '+33',
    minDigits: 9,
    maxDigits: 9,
    pattern: /^\d{9}$/,
    placeholder: '6 12 34 56 78',
    formatHint: 'Enter a valid 9-digit French mobile number.'
  },
  {
    code: 'JP',
    name: 'Japan',
    dialCode: '+81',
    minDigits: 10,
    maxDigits: 10,
    pattern: /^\d{10}$/,
    placeholder: '90 1234 5678',
    formatHint: 'Enter a valid 10-digit Japanese mobile number.'
  },
  {
    code: 'SA',
    name: 'Saudi Arabia',
    dialCode: '+966',
    minDigits: 9,
    maxDigits: 9,
    pattern: /^\d{9}$/,
    placeholder: '50 123 4567',
    formatHint: 'Enter a valid 9-digit Saudi mobile number.'
  },
  {
    code: 'MY',
    name: 'Malaysia',
    dialCode: '+60',
    minDigits: 9,
    maxDigits: 10,
    pattern: /^\d{9,10}$/,
    placeholder: '12 345 6789',
    formatHint: 'Enter a valid 9 or 10-digit Malaysian mobile number.'
  },
  {
    code: 'TH',
    name: 'Thailand',
    dialCode: '+66',
    minDigits: 9,
    maxDigits: 9,
    pattern: /^\d{9}$/,
    placeholder: '81 234 5678',
    formatHint: 'Enter a valid 9-digit Thai mobile number.'
  }
];

export function findCountryByDialCode(dialCode?: string | null): CountryDialCodeOption {
  const normalized = (dialCode || '').trim();
  return (
    COUNTRY_DIAL_CODE_OPTIONS.find(option => option.dialCode === normalized) ||
    COUNTRY_DIAL_CODE_OPTIONS[0]
  );
}

/**
 * Splits a stored account phone string (e.g. "+919876543210", "+91 98765 43210",
 * "+44 7911123456", or plain "9876543210") into country dial code and national digits.
 */
export function parsePhoneIntoDialCodeAndNumber(rawPhone?: string | null): {
  dialCode: string;
  nationalNumber: string;
} {
  if (!rawPhone || typeof rawPhone !== 'string') {
    return { dialCode: DEFAULT_COUNTRY_DIAL_CODE, nationalNumber: '' };
  }
  const trimmed = rawPhone.trim();
  if (!trimmed) {
    return { dialCode: DEFAULT_COUNTRY_DIAL_CODE, nationalNumber: '' };
  }

  // Sort dial codes longest-first so +971 matches before +91/+1, +44 before +4, etc.
  const sortedByDialLength = [...COUNTRY_DIAL_CODE_OPTIONS].sort(
    (a, b) => b.dialCode.length - a.dialCode.length
  );

  const normalizedForPrefix = trimmed.replace(/\s+/g, '');
  if (normalizedForPrefix.startsWith('+')) {
    for (const country of sortedByDialLength) {
      if (normalizedForPrefix.startsWith(country.dialCode)) {
        const rest = normalizedForPrefix.slice(country.dialCode.length).replace(/\D/g, '');
        return {
          dialCode: country.dialCode,
          nationalNumber: rest
        };
      }
    }
    const strippedDigits = normalizedForPrefix.slice(1).replace(/\D/g, '');
    return {
      dialCode: DEFAULT_COUNTRY_DIAL_CODE,
      nationalNumber: strippedDigits
    };
  }

  const digitsOnly = trimmed.replace(/\D/g, '');
  if (digitsOnly.length === 12 && digitsOnly.startsWith('91')) {
    return {
      dialCode: '+91',
      nationalNumber: digitsOnly.slice(2)
    };
  }
  if (digitsOnly.length === 11 && digitsOnly.startsWith('0')) {
    return {
      dialCode: DEFAULT_COUNTRY_DIAL_CODE,
      nationalNumber: digitsOnly.slice(1)
    };
  }

  return {
    dialCode: DEFAULT_COUNTRY_DIAL_CODE,
    nationalNumber: digitsOnly
  };
}

export function createInitialPrimaryGuestForm(sources?: {
  userFullName?: string | null;
  userEmail?: string | null;
  userPhone?: string | null;
}): PrimaryGuestFormState {
  const parsedPhone = parsePhoneIntoDialCodeAndNumber(sources?.userPhone);
  return {
    fullName: (sources?.userFullName || '').trim(),
    email: (sources?.userEmail || '').trim(),
    dialCode: parsedPhone.dialCode,
    nationalNumber: parsedPhone.nationalNumber
  };
}

export function validatePrimaryGuestFullName(fullName?: string | null): string | undefined {
  const trimmed = (fullName || '').trim();
  if (!trimmed) {
    return 'Full name is required.';
  }
  if (trimmed.length > PRIMARY_GUEST_MAX_NAME_LENGTH) {
    return `Full name cannot exceed ${PRIMARY_GUEST_MAX_NAME_LENGTH} characters.`;
  }
  if (!HAS_LETTER_REGEX.test(trimmed) || !ALLOWED_NAME_CHAR_REGEX.test(trimmed)) {
    return 'Enter a valid full name using letters and standard punctuation only.';
  }
  return undefined;
}

export function validatePrimaryGuestEmail(email?: string | null): string | undefined {
  const trimmed = (email || '').trim();
  if (!trimmed) {
    return 'Email address is required.';
  }
  if (trimmed.length > 255) {
    return 'Email address cannot exceed 255 characters.';
  }
  if (trimmed.includes('..') || !BACKEND_EMAIL_REGEX.test(trimmed)) {
    return 'Enter a valid email address in the format name@example.com.';
  }
  const [localPart, domainPart] = trimmed.split('@');
  if (
    !localPart ||
    !domainPart ||
    localPart.startsWith('.') ||
    localPart.endsWith('.') ||
    domainPart.startsWith('.') ||
    domainPart.startsWith('-') ||
    domainPart.endsWith('-')
  ) {
    return 'Enter a valid email address in the format name@example.com.';
  }
  return undefined;
}

export function validatePrimaryGuestMobile(
  dialCode?: string | null,
  nationalNumber?: string | null
): { dialCodeError?: string; nationalNumberError?: string; formattedMobile?: string } {
  const normalizedDial = (dialCode || '').trim();
  const country = COUNTRY_DIAL_CODE_OPTIONS.find(c => c.dialCode === normalizedDial);
  if (!country) {
    return { dialCodeError: 'Select a supported country dialing code.' };
  }

  const rawNational = (nationalNumber || '').trim();
  if (!rawNational) {
    return { nationalNumberError: 'Mobile number is required.' };
  }

  if (!ALLOWED_PHONE_SEPARATORS_REGEX.test(rawNational)) {
    return { nationalNumberError: 'Mobile number must contain digits only.' };
  }

  const digitsOnly = rawNational.replace(/\D/g, '');
  if (!digitsOnly) {
    return { nationalNumberError: 'Mobile number is required.' };
  }

  if (!country.pattern.test(digitsOnly)) {
    return { nationalNumberError: country.formatHint };
  }

  const formattedMobile = `${country.dialCode} ${digitsOnly}`;
  const totalDigits = formattedMobile.replace(/\D/g, '');
  if (
    !BACKEND_MOBILE_REGEX.test(formattedMobile) ||
    totalDigits.length < 7 ||
    totalDigits.length > 15
  ) {
    return {
      nationalNumberError: 'Enter a valid international mobile number (7 to 15 digits total).'
    };
  }

  return { formattedMobile };
}

export function formatPrimaryGuestMobileForBackend(
  dialCode?: string | null,
  nationalNumber?: string | null
): string {
  const normalizedDial = (dialCode || DEFAULT_COUNTRY_DIAL_CODE).trim();
  const digitsOnly = (nationalNumber || '').replace(/\D/g, '');
  return digitsOnly ? `${normalizedDial} ${digitsOnly}` : '';
}

export function validatePrimaryGuestForm(form: PrimaryGuestFormState): PrimaryGuestValidationResult {
  const errors: PrimaryGuestValidationErrors = {};

  const fullNameError = validatePrimaryGuestFullName(form.fullName);
  if (fullNameError) {
    errors.fullName = fullNameError;
  }

  const emailError = validatePrimaryGuestEmail(form.email);
  if (emailError) {
    errors.email = emailError;
  }

  const mobileCheck = validatePrimaryGuestMobile(form.dialCode, form.nationalNumber);
  if (mobileCheck.dialCodeError) {
    errors.dialCode = mobileCheck.dialCodeError;
  }
  if (mobileCheck.nationalNumberError) {
    errors.nationalNumber = mobileCheck.nationalNumberError;
  }

  const valid = Object.keys(errors).length === 0 && !!mobileCheck.formattedMobile;
  return {
    valid,
    errors,
    payload: valid
      ? {
          fullName: form.fullName.trim(),
          email: form.email.trim(),
          mobile: mobileCheck.formattedMobile!
        }
      : null
  };
}

/**
 * Computes Reservation Summary display metrics strictly from carried WP-HB-UI-02/03 pricing
 * and physical room guest allocations without recalculating nightly pricing or adding taxes.
 */
export function computeReservationSummaryMetrics(
  staySummary: HotelStaySummary,
  physicalRooms: PhysicalRoomAllocation[]
): ReservationReviewSummaryMetrics {
  const roomTypeRows = (staySummary.selectedItems || []).map(item => {
    const subtotal =
      typeof item.totalStayPrice === 'number' && !Number.isNaN(item.totalStayPrice)
        ? item.totalStayPrice
        : (item.stayTotalPricePerRoom || 0) * (item.quantity || 0);
    return {
      roomTypeId: item.roomTypeId,
      roomTypeName: item.roomTypeName,
      quantity: item.quantity,
      stayTotalPricePerRoom: item.stayTotalPricePerRoom,
      subtotalAmount: subtotal
    };
  });

  const sumSubtotals = roomTypeRows.reduce((sum, row) => sum + row.subtotalAmount, 0);
  const estimatedTotalAmount =
    typeof staySummary.estimatedTotalAmount === 'number' && !Number.isNaN(staySummary.estimatedTotalAmount)
      ? staySummary.estimatedTotalAmount
      : sumSubtotals;

  const pricingConsistent =
    roomTypeRows.length > 0 &&
    estimatedTotalAmount > 0 &&
    Math.abs(sumSubtotals - estimatedTotalAmount) <= 1;

  let totalAdults = 0;
  let totalChildren = 0;

  for (const room of physicalRooms) {
    for (const occ of room.occupants || []) {
      if (occ.type === 'ADULT') {
        totalAdults += 1;
      } else if (occ.type === 'CHILD') {
        totalChildren += 1;
      }
    }
  }

  return {
    roomTypeRows,
    estimatedTotalAmount,
    pricingConsistent,
    totalRooms: physicalRooms.length,
    totalAdults,
    totalChildren,
    totalGuests: totalAdults + totalChildren
  };
}

/**
 * Verifies integrity of Stage 3 room selections + physical room allocations before payment entry.
 */
export function validateGuestAllocationsForReview(
  staySummary: HotelStaySummary | null,
  physicalRooms: PhysicalRoomAllocation[]
): { valid: boolean; reason?: string } {
  if (!staySummary || !Array.isArray(staySummary.selectedItems) || staySummary.selectedItems.length === 0) {
    return { valid: false, reason: 'Room selection details are missing or expired.' };
  }
  if (!staySummary.checkIn || !staySummary.checkOut || staySummary.totalNights < 1) {
    return { valid: false, reason: 'Stay check-in/check-out dates are missing or invalid.' };
  }

  const expectedRoomCount = staySummary.selectedItems.reduce((sum, item) => sum + (item.quantity || 0), 0);
  if (expectedRoomCount <= 0 || physicalRooms.length !== expectedRoomCount) {
    return {
      valid: false,
      reason: 'Assigned guest rooms do not match the selected room count. Please review Guest Details.'
    };
  }

  const metrics = computeReservationSummaryMetrics(staySummary, physicalRooms);
  if (!metrics.pricingConsistent) {
    return {
      valid: false,
      reason: 'Selected room pricing summary appears inconsistent. Please re-verify your room selection.'
    };
  }

  for (const room of physicalRooms) {
    const check = validatePhysicalRoom(room);
    if (!check.valid) {
      return {
        valid: false,
        reason: `Room ${room.roomSequenceOverall} (${room.roomTypeName}) has incomplete or invalid guest details.`
      };
    }
  }

  return { valid: true };
}

export function getRoomGuestDisplayRole(
  room: PhysicalRoomAllocation,
  occupantIndex: number
): string {
  const target = room.occupants[occupantIndex];
  if (!target) return '';
  let seq = 0;
  for (let i = 0; i <= occupantIndex && i < room.occupants.length; i += 1) {
    if (room.occupants[i].type === target.type) {
      seq += 1;
    }
  }
  return target.type === 'ADULT' ? `Adult ${seq}` : `Child ${seq}`;
}

export function saveReservationReviewState(
  state: ReservationReviewJourneyState,
  customStorage?: Storage
): void {
  const storage = customStorage ?? (typeof sessionStorage !== 'undefined' ? sessionStorage : null);
  if (!storage) return;
  try {
    storage.setItem(RESERVATION_REVIEW_STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Ignore storage quota errors
  }
}

export function loadReservationReviewState(
  expectedStaySummary?: HotelStaySummary | null,
  customStorage?: Storage,
  expectedUserId?: number | null
): ReservationReviewJourneyState | null {
  const storage = customStorage ?? (typeof sessionStorage !== 'undefined' ? sessionStorage : null);
  if (!storage) return null;
  try {
    const raw = storage.getItem(RESERVATION_REVIEW_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as ReservationReviewJourneyState;
    if (!parsed || !parsed.staySummary || !parsed.primaryGuest) {
      return null;
    }
    if (
      expectedUserId != null &&
      parsed.userId != null &&
      parsed.userId !== expectedUserId
    ) {
      return null;
    }
    if (expectedStaySummary && !isSameStayContextSignature(parsed.staySummary, expectedStaySummary)) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function clearReservationReviewState(customStorage?: Storage): void {
  const storage = customStorage ?? (typeof sessionStorage !== 'undefined' ? sessionStorage : null);
  if (!storage) return;
  try {
    storage.removeItem(RESERVATION_REVIEW_STORAGE_KEY);
  } catch {
    // Ignore
  }
}

export function toPersistedRoomsSnapshot(
  rooms: PhysicalRoomAllocation[]
): PersistedPhysicalRoomState[] {
  return rooms.map(room => ({
    roomKey: room.roomKey,
    roomTypeId: room.roomTypeId,
    roomSequenceOverall: room.roomSequenceOverall,
    roomSequenceInType: room.roomSequenceInType,
    selectedAddGuestType: room.selectedAddGuestType,
    occupants: room.occupants.map(occ => ({
      id: occ.id,
      type: occ.type,
      name: occ.name,
      age: occ.age,
      gender: occ.type === 'ADULT' ? occ.gender : null
    }))
  }));
}
