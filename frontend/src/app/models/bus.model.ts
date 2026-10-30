export interface Bus {
  id: number;
  operatorId?: number;
  busNumber: string;
  operatorName: string;
  busType: string;
  capacity?: number;
  totalSeats?: number;
  hasAc?: boolean;
  isSleeper?: boolean;
  amenities?: string | string[];
  facilityType?: string;
  busConfiguration?: string;
  templateId?: string;
  status?: string;
  busName?: string;
  manufacturer?: string;
  model?: string;
  yearOfManufacturing?: number;
  modelYear?: number;
  fuelType?: string;
  imageUrl?: string;
  exteriorImageUrl?: string;
  interiorImageUrl?: string;
  chassisNumber?: string;
  engineNumber?: string;
  emissionStandard?: string;
  color?: string;
  busCategory?: string;
  busImages?: string[];
  layoutSummary?: string;
}

export interface BusBasicDetailsForm {
  busNumber: string;
  busName: string;
  manufacturer: string;
  model: string;
  busType: string;
  fuelType: string;
  chassisNumber: string;
  yearOfManufacturing: number | null;
  engineNumber: string;
  emissionStandard: string;
  color: string;
  busCategory: string;
  facilityType: string; // 'AC' or 'NON_AC'
  amenities: string[];
  exteriorImageUrl?: string;
  interiorImageUrl?: string;
  exteriorImagePreview?: string;
  interiorImagePreview?: string;
}

export interface Schedule {
  id: number;
  operatorId?: number;
  busId: number;
  routeId?: number;
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
  busNumber?: string;
  amenities?: string[];
  hasAc?: boolean;
  isSleeper?: boolean;
  arrivalDate?: string;
  departureDateTime?: string;
  arrivalDateTime?: string;
  capacity?: number;
  totalSeats?: number;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
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

export interface FareMatrixEntry {
  fromStopOrder: number;
  fromStopName: string;
  toStopOrder: number;
  toStopName: string;
  distanceKm?: number;
  calculatedFare: number;
  customFare?: number | null;
  effectiveFare: number;
  isOverridden: boolean;
  segmentCount?: number;
}

export interface FareOverrideItem {
  fromStopOrder: number;
  toStopOrder: number;
  fromStopName?: string;
  toStopName?: string;
  customFare: number | null;
}

export interface ScheduleFareMatrixResponse {
  scheduleId?: number | null;
  totalStops: number;
  totalPairs: number;
  stops: ScheduleStop[];
  matrix: FareMatrixEntry[];
}

