import {
  GuestAllocationJourneyState,
  GuestFormEntry,
  GuestGender,
  GuestType,
  HotelReservationAllocationItemPayload,
  HotelStaySummary,
  PersistedPhysicalRoomState,
  PhysicalRoomAllocation,
  RoomValidationResult
} from '../../models/hotel.model';

export const STAY_SUMMARY_STORAGE_KEY = 'traveleasy_stay_summary';
export const GUEST_ALLOCATION_STORAGE_KEY = 'traveleasy_guest_allocation_state';

export const ADULT_GENDER_OPTIONS: ReadonlyArray<{ value: GuestGender; label: string }> = [
  { value: 'MALE', label: 'Male' },
  { value: 'FEMALE', label: 'Female' },
  { value: 'OTHER', label: 'Other' },
  { value: 'PREFER_NOT_TO_SAY', label: 'Prefer not to say' }
];

export const VALID_GUEST_GENDERS: ReadonlySet<GuestGender> = new Set<GuestGender>([
  'MALE',
  'FEMALE',
  'OTHER',
  'PREFER_NOT_TO_SAY'
]);

export const CHILD_AGE_OPTIONS: ReadonlyArray<number> = Array.from({ length: 18 }, (_, i) => i);

let guestSequenceCounter = 0;

export function generateGuestEntryId(prefix = 'guest'): string {
  guestSequenceCounter += 1;
  return `${prefix}-${Date.now()}-${guestSequenceCounter}`;
}

export function buildPhysicalRoomsFromStaySummary(
  staySummary: HotelStaySummary,
  previousRooms?: PersistedPhysicalRoomState[] | null,
  activeExpandedRoomKey?: string | null
): PhysicalRoomAllocation[] {
  if (!staySummary || !Array.isArray(staySummary.selectedItems)) {
    return [];
  }

  const prevMap = new Map<string, PersistedPhysicalRoomState>();
  if (Array.isArray(previousRooms)) {
    for (const prev of previousRooms) {
      if (prev && prev.roomKey) {
        prevMap.set(prev.roomKey, prev);
      }
    }
  }

  const rooms: PhysicalRoomAllocation[] = [];
  let overallSeq = 0;

  for (const item of staySummary.selectedItems) {
    const qty = Math.max(0, Math.floor(item.quantity || 0));
    for (let seqInType = 1; seqInType <= qty; seqInType += 1) {
      overallSeq += 1;
      const roomKey = `${item.roomTypeId}-${seqInType}`;
      const previous = prevMap.get(roomKey);

      const restoredOccupants: GuestFormEntry[] = previous && Array.isArray(previous.occupants)
        ? previous.occupants.map((occ, idx) => sanitizeRestoredGuest(occ, `${roomKey}-g${idx + 1}`))
        : [];

      rooms.push({
        roomKey,
        roomTypeId: item.roomTypeId,
        roomTypeName: item.roomTypeName,
        roomSequenceOverall: overallSeq,
        roomSequenceInType: seqInType,
        maxOccupancy: Math.max(1, item.maxOccupancy || 1),
        nightlyPrice: item.minNightlyPrice || 0,
        stayTotalPricePerRoom: item.stayTotalPricePerRoom || 0,
        imageUrl: item.imageUrl || null,
        expanded: false,
        selectedAddGuestType: previous?.selectedAddGuestType === 'CHILD' ? 'CHILD' : 'ADULT',
        occupants: restoredOccupants
      });
    }
  }

  if (rooms.length > 0) {
    const matchingExpanded = activeExpandedRoomKey
      ? rooms.find(r => r.roomKey === activeExpandedRoomKey)
      : undefined;
    if (matchingExpanded) {
      matchingExpanded.expanded = true;
    } else {
      rooms[0].expanded = true;
    }
  }

  return rooms;
}

function sanitizeRestoredGuest(entry: Partial<GuestFormEntry>, fallbackId: string): GuestFormEntry {
  const type: GuestType = entry.type === 'CHILD' ? 'CHILD' : 'ADULT';
  const rawAge = entry.age;
  const age = rawAge !== null && rawAge !== undefined && Number.isInteger(Number(rawAge))
    ? Number(rawAge)
    : null;
  const gender: GuestGender | null =
    type === 'CHILD'
      ? null
      : (entry.gender && VALID_GUEST_GENDERS.has(entry.gender) ? entry.gender : null);

  return {
    id: entry.id || fallbackId,
    type,
    name: typeof entry.name === 'string' ? entry.name : '',
    age,
    gender
  };
}

export function createNewGuestEntry(type: GuestType, id?: string): GuestFormEntry {
  return {
    id: id || generateGuestEntryId(type === 'CHILD' ? 'child' : 'adult'),
    type: type === 'CHILD' ? 'CHILD' : 'ADULT',
    name: '',
    age: null,
    gender: null
  };
}

export function addGuestToRoom(
  rooms: PhysicalRoomAllocation[],
  roomKey: string,
  guestType?: GuestType,
  id?: string
): PhysicalRoomAllocation[] {
  return rooms.map(room => {
    if (room.roomKey !== roomKey) {
      return room;
    }
    if (room.occupants.length >= room.maxOccupancy) {
      return room;
    }
    const resolvedType: GuestType = guestType || room.selectedAddGuestType || 'ADULT';
    const nextGuest = createNewGuestEntry(resolvedType, id);
    return {
      ...room,
      expanded: true,
      occupants: [...room.occupants, nextGuest]
    };
  });
}

export function removeGuestFromRoom(
  rooms: PhysicalRoomAllocation[],
  roomKey: string,
  guestId: string
): PhysicalRoomAllocation[] {
  return rooms.map(room => {
    if (room.roomKey !== roomKey) {
      return room;
    }
    return {
      ...room,
      occupants: room.occupants.filter(occ => occ.id !== guestId)
    };
  });
}

export function validateGuestEntry(guest: GuestFormEntry): {
  name?: string;
  age?: string;
  gender?: string;
} {
  const errors: { name?: string; age?: string; gender?: string } = {};
  const trimmedName = (guest.name || '').trim();

  if (!trimmedName) {
    errors.name = 'Full name is required.';
  } else if (trimmedName.length > 255) {
    errors.name = 'Full name must not exceed 255 characters.';
  }

  if (guest.type === 'ADULT') {
    if (guest.age === null || guest.age === undefined || Number.isNaN(Number(guest.age))) {
      errors.age = 'Age is required (18 or older).';
    } else if (!Number.isInteger(Number(guest.age))) {
      errors.age = 'Age must be a whole number.';
    } else if (Number(guest.age) < 18) {
      errors.age = 'Adults must be at least 18 years old.';
    }

    if (!guest.gender || !VALID_GUEST_GENDERS.has(guest.gender)) {
      errors.gender = 'Please select a gender.';
    }
  } else {
    if (guest.age === null || guest.age === undefined || Number.isNaN(Number(guest.age))) {
      errors.age = 'Age is required (0 to 17 years).';
    } else if (!Number.isInteger(Number(guest.age))) {
      errors.age = 'Child age must be a whole number.';
    } else if (Number(guest.age) < 0 || Number(guest.age) > 17) {
      errors.age = 'Child age must be between 0 and 17 years.';
    }
  }

  return errors;
}

export function validatePhysicalRoom(room: PhysicalRoomAllocation): RoomValidationResult {
  const totalGuests = room.occupants.length;
  const adultCount = room.occupants.filter(occ => occ.type === 'ADULT').length;
  const childCount = room.occupants.filter(occ => occ.type === 'CHILD').length;
  const atMaxCapacity = totalGuests >= room.maxOccupancy;

  const roomErrors: string[] = [];
  if (totalGuests < 1) {
    roomErrors.push(`At least 1 guest must be added to Room ${room.roomSequenceOverall}.`);
  } else if (adultCount < 1) {
    roomErrors.push(`At least 1 adult (18+) is required in Room ${room.roomSequenceOverall}. Children cannot stay unaccompanied.`);
  }

  if (totalGuests > room.maxOccupancy) {
    roomErrors.push(`Maximum ${room.maxOccupancy} guests allowed in Room ${room.roomSequenceOverall}.`);
  }

  const guestErrors: Record<string, { name?: string; age?: string; gender?: string }> = {};
  for (const occ of room.occupants) {
    const fieldErr = validateGuestEntry(occ);
    if (fieldErr.name || fieldErr.age || fieldErr.gender) {
      guestErrors[occ.id] = fieldErr;
    }
  }

  return {
    roomKey: room.roomKey,
    roomLabel: `Room ${room.roomSequenceOverall} – ${room.roomTypeName}`,
    valid: roomErrors.length === 0 && Object.keys(guestErrors).length === 0,
    totalGuests,
    adultCount,
    childCount,
    atMaxCapacity,
    roomErrors,
    guestErrors
  };
}

export function validateAllRooms(rooms: PhysicalRoomAllocation[]): {
  allValid: boolean;
  results: Record<string, RoomValidationResult>;
  invalidRoomKeys: string[];
} {
  const results: Record<string, RoomValidationResult> = {};
  const invalidRoomKeys: string[] = [];

  for (const room of rooms) {
    const result = validatePhysicalRoom(room);
    results[room.roomKey] = result;
    if (!result.valid) {
      invalidRoomKeys.push(room.roomKey);
    }
  }

  return {
    allValid: rooms.length > 0 && invalidRoomKeys.length === 0,
    results,
    invalidRoomKeys
  };
}

export function isSameStayContextSignature(a: HotelStaySummary | null, b: HotelStaySummary | null): boolean {
  if (!a || !b) return false;
  return (
    a.hotelId === b.hotelId &&
    a.checkIn === b.checkIn &&
    a.checkOut === b.checkOut
  );
}

export function buildReservationAllocationsPayload(
  rooms: PhysicalRoomAllocation[]
): HotelReservationAllocationItemPayload[] {
  const grouped = new Map<number, PhysicalRoomAllocation[]>();
  for (const room of rooms) {
    const list = grouped.get(room.roomTypeId) || [];
    list.push(room);
    grouped.set(room.roomTypeId, list);
  }

  const payload: HotelReservationAllocationItemPayload[] = [];
  grouped.forEach((groupRooms, roomId) => {
    const sorted = [...groupRooms].sort((a, b) => a.roomSequenceInType - b.roomSequenceInType);
    payload.push({
      roomId,
      quantity: sorted.length,
      rooms: sorted.map(room => {
        const adults = room.occupants.filter(occ => occ.type === 'ADULT').length;
        const children = room.occupants.filter(occ => occ.type === 'CHILD').length;
        return {
          roomSequence: room.roomSequenceInType,
          adults,
          children,
          occupants: room.occupants.map(occ => ({
            name: occ.name.trim(),
            type: occ.type,
            age: occ.age !== null && occ.age !== undefined ? Number(occ.age) : null,
            gender: occ.type === 'CHILD' ? null : (occ.gender || null)
          }))
        };
      })
    });
  });

  return payload;
}

export function formatStayDisplayDate(isoDate: string): string {
  if (!isoDate) return '';
  try {
    const parts = isoDate.split('-');
    if (parts.length === 3) {
      const d = new Date(
        parseInt(parts[0], 10),
        parseInt(parts[1], 10) - 1,
        parseInt(parts[2], 10),
        12,
        0,
        0
      );
      return d.toLocaleDateString('en-GB', {
        weekday: 'short',
        day: 'numeric',
        month: 'short',
        year: 'numeric'
      });
    }
    return isoDate;
  } catch {
    return isoDate;
  }
}

export function formatStayGuestsSummary(adults: number, children: number): string {
  const safeAdults = Math.max(0, adults || 0);
  const safeChildren = Math.max(0, children || 0);
  const adultLabel = safeAdults === 1 ? '1 Adult' : `${safeAdults} Adults`;
  if (safeChildren === 0) return adultLabel;
  const childLabel = safeChildren === 1 ? '1 Child' : `${safeChildren} Children`;
  return `${adultLabel}, ${childLabel}`;
}

export function formatGenderLabel(gender: GuestGender | null | undefined): string {
  const match = ADULT_GENDER_OPTIONS.find(opt => opt.value === gender);
  return match ? match.label : '—';
}

function resolveSessionStorage(customStorage?: Storage): Storage | null {
  if (customStorage) return customStorage;
  if (typeof window !== 'undefined' && window.sessionStorage) {
    return window.sessionStorage;
  }
  return null;
}

export function saveStaySummaryToSession(summary: HotelStaySummary, customStorage?: Storage): void {
  const storage = resolveSessionStorage(customStorage);
  if (!storage) return;
  try {
    storage.setItem(STAY_SUMMARY_STORAGE_KEY, JSON.stringify(summary));
  } catch {
    // Ignore storage quota errors cleanly
  }
}

export function loadStaySummaryFromSession(customStorage?: Storage): HotelStaySummary | null {
  const storage = resolveSessionStorage(customStorage);
  if (!storage) return null;
  try {
    const raw = storage.getItem(STAY_SUMMARY_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as HotelStaySummary;
    if (!parsed || !parsed.hotelId || !Array.isArray(parsed.selectedItems) || parsed.selectedItems.length === 0) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function saveGuestAllocationState(
  staySummary: HotelStaySummary,
  rooms: PhysicalRoomAllocation[],
  customStorage?: Storage
): void {
  const storage = resolveSessionStorage(customStorage);
  if (!storage) return;
  try {
    const activeExpanded = rooms.find(r => r.expanded)?.roomKey || null;
    const state: GuestAllocationJourneyState = {
      staySummary,
      rooms: rooms.map(r => ({
        roomKey: r.roomKey,
        roomTypeId: r.roomTypeId,
        roomSequenceOverall: r.roomSequenceOverall,
        roomSequenceInType: r.roomSequenceInType,
        selectedAddGuestType: r.selectedAddGuestType,
        occupants: r.occupants.map(occ => ({
          id: occ.id,
          type: occ.type,
          name: occ.name,
          age: occ.age,
          gender: occ.type === 'CHILD' ? null : (occ.gender || null)
        }))
      })),
      activeExpandedRoomKey: activeExpanded,
      updatedAt: new Date().toISOString()
    };
    storage.setItem(GUEST_ALLOCATION_STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Ignore storage quota errors cleanly
  }
}

export function loadGuestAllocationState(customStorage?: Storage): GuestAllocationJourneyState | null {
  const storage = resolveSessionStorage(customStorage);
  if (!storage) return null;
  try {
    const raw = storage.getItem(GUEST_ALLOCATION_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as GuestAllocationJourneyState;
    if (!parsed || !parsed.staySummary || !Array.isArray(parsed.rooms)) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function clearGuestAllocationState(customStorage?: Storage): void {
  const storage = resolveSessionStorage(customStorage);
  if (!storage) return;
  try {
    storage.removeItem(GUEST_ALLOCATION_STORAGE_KEY);
  } catch {
    // Ignore
  }
}
