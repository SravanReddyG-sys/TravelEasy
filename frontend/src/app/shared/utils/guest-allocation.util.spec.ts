import { HotelStaySummary } from '../../models/hotel.model';
import {
  addGuestToRoom,
  buildPhysicalRoomsFromStaySummary,
  buildReservationAllocationsPayload,
  loadGuestAllocationState,
  loadStaySummaryFromSession,
  removeGuestFromRoom,
  saveGuestAllocationState,
  saveStaySummaryToSession,
  validateAllRooms,
  validateGuestEntry,
  validatePhysicalRoom
} from './guest-allocation.util';

function createSampleStaySummary(): HotelStaySummary {
  return {
    hotelId: 101,
    hotelName: 'The Lakeside Grand Hotel',
    city: 'Hyderabad',
    propertyType: 'Hotel',
    address: 'Banjara Hills, Hyderabad',
    hotelImageUrl: '/uploads/hotels/lakeside.jpg',
    checkIn: '2026-12-10',
    checkOut: '2026-12-13',
    totalNights: 3,
    requestedRooms: 3,
    adults: 3,
    children: 1,
    selectedItems: [
      {
        roomTypeId: 11,
        roomTypeName: 'Deluxe Room',
        maxOccupancy: 3,
        quantity: 2,
        minNightlyPrice: 6499,
        stayTotalPricePerRoom: 19497,
        totalStayPrice: 38994,
        imageUrl: '/uploads/rooms/deluxe.jpg'
      },
      {
        roomTypeId: 12,
        roomTypeName: 'Standard Room',
        maxOccupancy: 2,
        quantity: 1,
        minNightlyPrice: 4899,
        stayTotalPricePerRoom: 14697,
        totalStayPrice: 14697,
        imageUrl: '/uploads/rooms/standard.jpg'
      }
    ],
    totalSelectedRooms: 3,
    estimatedTotalAmount: 53691
  };
}

function createMemoryStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear: () => map.clear(),
    getItem: (key: string) => (map.has(key) ? map.get(key)! : null),
    key: (index: number) => Array.from(map.keys())[index] ?? null,
    removeItem: (key: string) => {
      map.delete(key);
    },
    setItem: (key: string, value: string) => {
      map.set(key, value);
    }
  };
}

describe('guest-allocation.util (WP-HB-UI-03)', () => {
  it('generates physical room cards with zero initial occupants and only Room 1 expanded', () => {
    const rooms = buildPhysicalRoomsFromStaySummary(createSampleStaySummary());
    expect(rooms.length).toBe(3);
    expect(rooms[0].roomSequenceOverall).toBe(1);
    expect(rooms[0].roomSequenceInType).toBe(1);
    expect(rooms[0].roomTypeName).toBe('Deluxe Room');
    expect(rooms[0].expanded).toBeTrue();
    expect(rooms[0].occupants.length).toBe(0);
    expect(rooms[0].selectedAddGuestType).toBe('ADULT');

    expect(rooms[1].roomSequenceOverall).toBe(2);
    expect(rooms[1].roomSequenceInType).toBe(2);
    expect(rooms[1].expanded).toBeFalse();

    expect(rooms[2].roomSequenceOverall).toBe(3);
    expect(rooms[2].roomSequenceInType).toBe(1);
    expect(rooms[2].roomTypeName).toBe('Standard Room');
    expect(rooms[2].expanded).toBeFalse();
  });

  it('adds Adult and Child entries independently without preselected Adult gender or Child gender', () => {
    let rooms = buildPhysicalRoomsFromStaySummary(createSampleStaySummary());
    rooms = addGuestToRoom(rooms, '11-1', 'ADULT', 'g-a1');
    rooms = addGuestToRoom(rooms, '11-1', 'CHILD', 'g-c1');

    expect(rooms[0].occupants.length).toBe(2);
    expect(rooms[0].occupants[0].type).toBe('ADULT');
    expect(rooms[0].occupants[0].gender).toBeNull();
    expect(rooms[0].occupants[0].age).toBeNull();
    expect(rooms[0].occupants[1].type).toBe('CHILD');
    expect(rooms[0].occupants[1].gender).toBeNull();
    expect(rooms[1].occupants.length).toBe(0);
  });

  it('enforces maxOccupancy ceiling per room and independent guest removal', () => {
    let rooms = buildPhysicalRoomsFromStaySummary(createSampleStaySummary());
    rooms = addGuestToRoom(rooms, '12-1', 'ADULT', 's-1');
    rooms = addGuestToRoom(rooms, '12-1', 'CHILD', 's-2');
    rooms = addGuestToRoom(rooms, '12-1', 'ADULT', 's-3'); // Standard maxOccupancy is 2
    expect(rooms[2].occupants.length).toBe(2);

    rooms = removeGuestFromRoom(rooms, '12-1', 's-1');
    expect(rooms[2].occupants.length).toBe(1);
    expect(rooms[2].occupants[0].id).toBe('s-2');
  });

  it('validates Adult name, age >= 18, and gender, and Child name and age 0..17', () => {
    expect(
      validateGuestEntry({ id: '1', type: 'ADULT', name: '  ', age: 17, gender: null })
    ).toEqual({
      name: 'Full name is required.',
      age: 'Adults must be at least 18 years old.',
      gender: 'Please select a gender.'
    });

    expect(
      validateGuestEntry({ id: '2', type: 'ADULT', name: 'Rohit Sharma', age: 32, gender: 'MALE' })
    ).toEqual({});

    expect(
      validateGuestEntry({ id: '3', type: 'CHILD', name: 'Aarav Sharma', age: 8, gender: null })
    ).toEqual({});

    expect(
      validateGuestEntry({ id: '4', type: 'CHILD', name: 'Aarav Sharma', age: 18, gender: null }).age
    ).toBe('Child age must be between 0 and 17 years.');
  });

  it('rejects empty rooms and children-only rooms during room validation', () => {
    let rooms = buildPhysicalRoomsFromStaySummary(createSampleStaySummary());
    const emptyResult = validatePhysicalRoom(rooms[0]);
    expect(emptyResult.valid).toBeFalse();

    rooms = addGuestToRoom(rooms, '11-1', 'CHILD', 'c1');
    rooms[0].occupants[0] = { id: 'c1', type: 'CHILD', name: 'Minor Only', age: 7, gender: null };
    const childOnlyResult = validatePhysicalRoom(rooms[0]);
    expect(childOnlyResult.valid).toBeFalse();
    expect(childOnlyResult.roomErrors[0]).toContain('At least 1 adult');
  });

  it('persists and restores multi-room guest data via Storage and builds backend payload cleanly', () => {
    const storage = createMemoryStorage();
    const summary = createSampleStaySummary();
    let rooms = buildPhysicalRoomsFromStaySummary(summary);
    rooms = addGuestToRoom(rooms, '11-1', 'ADULT', 'g1');
    rooms[0].occupants[0] = { id: 'g1', type: 'ADULT', name: 'Rohit Sharma', age: 32, gender: 'MALE' };
    rooms = addGuestToRoom(rooms, '11-2', 'ADULT', 'g2');
    rooms[1].occupants[0] = { id: 'g2', type: 'ADULT', name: 'Priya Sharma', age: 29, gender: 'FEMALE' };
    rooms = addGuestToRoom(rooms, '12-1', 'ADULT', 'g3');
    rooms[2].occupants[0] = { id: 'g3', type: 'ADULT', name: 'Amit Verma', age: 35, gender: 'PREFER_NOT_TO_SAY' };

    saveStaySummaryToSession(summary, storage);
    saveGuestAllocationState(summary, rooms, storage);

    const loadedSummary = loadStaySummaryFromSession(storage);
    const loadedState = loadGuestAllocationState(storage);
    expect(loadedSummary?.hotelId).toBe(101);
    expect(loadedState?.rooms[1].occupants[0].name).toBe('Priya Sharma');

    const rebuilt = buildPhysicalRoomsFromStaySummary(
      summary,
      loadedState?.rooms,
      loadedState?.activeExpandedRoomKey
    );
    const validation = validateAllRooms(rebuilt);
    expect(validation.allValid).toBeTrue();

    const payload = buildReservationAllocationsPayload(rebuilt);
    expect(payload.length).toBe(2);
    expect(payload[0].quantity).toBe(2);
    expect(payload[0].rooms[1].roomSequence).toBe(2);
    expect(payload[0].rooms[1].occupants[0].gender).toBe('FEMALE');
  });
});
