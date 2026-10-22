export interface Bus {
  id: number;
  busNumber: string;
  operatorName: string;
  busType: string;
  capacity: number;
  hasAc: boolean;
  isSleeper: boolean;
  amenities: string[];
}

export interface Schedule {
  id: number;
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
