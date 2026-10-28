export interface Bus {
  id: number;
  operatorId?: number;
  busNumber: string;
  operatorName: string;
  busType: string;
  capacity: number;
  hasAc: boolean;
  isSleeper: boolean;
  amenities: string[];
  status?: string;
}

export interface Schedule {
  id: number;
  operatorId?: number;
  busId: number;
  origin: string;
  destination: string;
  travelDate: string;
  departureTime: string;
  arrivalTime: string;
  fare: number;
  availableSeats: number;
  bookedSeats?: string;
  boardingPoints?: string;
  droppingPoints?: string;
  bus?: Bus;
  operatorName?: string;
  busType?: string;
  amenities?: string[];
  hasAc?: boolean;
  isSleeper?: boolean;
  status?: string;
}

export interface Seat {
  number: string;
  status: 'available' | 'booked' | 'selected';
  type?: 'seater' | 'sleeper' | 'single';
  deck?: 'lower' | 'upper';
  price?: number;
}

export interface BusSearchCriteria {
  origin: string;
  destination: string;
  journeyDate: string;
}

export interface BusFilterState {
  busTypeAc: boolean;
  busTypeNonAc: boolean;
  seatTypeSeater: boolean;
  seatTypeSleeper: boolean;
  singleSeatOnly: boolean;
  departureMorning: boolean;
  departureAfternoon: boolean;
  departureEvening: boolean;
  departureNight: boolean;
  arrivalMorning: boolean;
  arrivalAfternoon: boolean;
  arrivalEvening: boolean;
  arrivalNight: boolean;
  selectedBoardingPoints: string[];
  selectedDroppingPoints: string[];
  selectedAmenities: string[];
  minPrice: number;
  maxPrice: number;
}

export interface SeatPosition {
  positionId: string;
  seatNumber: string;
  seatType: string; // 'SEATER' | 'SLEEPER'
  deck: string;     // 'LOWER' | 'UPPER' | 'SINGLE'
  row: number;
  column: number;
  side: string;     // 'LEFT' | 'RIGHT' | 'CENTER'
  isWindow: boolean;
  isBookable: boolean;
  rowSpan?: number;
  colSpan?: number;
  price: number;
  status: string;   // 'AVAILABLE' | 'BOOKED'
  gender?: string;  // 'UNISEX' | 'MALE' | 'FEMALE'
  visualOrder?: number;
}

export interface DeckLayout {
  deck: string;
  deckName: string;
  rows: number;
  columns: number;
  inventory: number;
  availableCount: number;
  positions: SeatPosition[];
}

export interface ScheduleStop {
  id?: number;
  scheduleId?: number;
  stopOrder: number;
  stopName: string;
  stopType?: string;
  arrivalTime?: string;
  departureTime?: string;
  distanceKm?: number;
  consecutiveFare?: number;
  haltMinutes?: number;
}

export interface TripSeatLayout {
  scheduleId: number;
  busId: number;
  templateId: string;
  templateName: string;
  referenceImage: string;
  busNumber: string;
  operatorName: string;
  busType: string;
  busConfiguration: string;
  facilityType: string;
  amenities: string;
  totalInventory: number;
  availableSeats: number;
  baseFare: number;
  origin?: string;
  destination?: string;
  travelDate?: string;
  departureTime?: string;
  arrivalTime?: string;
  boardingPoints?: string;
  droppingPoints?: string;
  decks: DeckLayout[];
  fromOrder?: number;
  toOrder?: number;
  fromStopName?: string;
  toStopName?: string;
  stops?: ScheduleStop[];
}

export interface RoutePoint {
  id?: number;
  routeId?: number;
  routeStopId?: number;
  pointName: string;
  pointType: 'BOARDING' | 'DROPPING';
  address?: string;
  timeSlot?: string;
  active?: boolean;
}

export interface RouteStop {
  id?: number;
  routeId?: number;
  stopOrder: number;
  stopName: string;
  stopType: 'ORIGIN' | 'INTERMEDIATE' | 'DESTINATION';
  distanceKm?: number;
  boardingPoints: RoutePoint[];
  droppingPoints: RoutePoint[];
}

export interface Route {
  id: number;
  operatorId: number;
  routeCode: string;
  routeName: string;
  origin: string;
  destination: string;
  description?: string;
  status: 'ACTIVE' | 'INACTIVE' | 'DRAFT' | string;
  createdAt: string;
  updatedAt: string;
  stops?: RouteStop[];
  scheduleCount?: number;
}

export interface CreateRouteRequest {
  routeCode?: string;
  routeName: string;
  origin: string;
  destination: string;
  description?: string;
  status?: string;
  stops: {
    stopOrder: number;
    stopName: string;
    distanceKm?: number;
    boardingPoints: { pointName: string; address?: string; timeSlot?: string }[];
    droppingPoints: { pointName: string; address?: string; timeSlot?: string }[];
  }[];
}
