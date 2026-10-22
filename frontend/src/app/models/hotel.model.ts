export interface HotelRoom {
  id: number;
  hotelId: number;
  roomType: string;
  pricePerNight: number;
  capacity: number;
  availableRooms: number;
  description?: string;
  amenities?: string[];
  imageUrl?: string;
}

export interface Hotel {
  id: number;
  name: string;
  city: string;
  address: string;
  starRating?: number;
  rating?: number;
  reviewCount?: number;
  pricePerNight?: number;
  amenities?: string[];
  image?: string;
  rooms?: HotelRoom[];
}

export interface HotelSearchCriteria {
  city: string;
  checkIn: string;
  checkOut: string;
  guests?: number;
  rooms?: number;
}
