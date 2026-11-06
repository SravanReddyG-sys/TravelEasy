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
  status?: string;
  propertyType?: string;
  propertyClassification?: string;
  customClassification?: string;
  propertySegment?: string;
  brandName?: string;
}

export interface HotelSearchCriteria {
  city: string;
  checkIn: string;
  checkOut: string;
  guests?: number;
  rooms?: number;
}

// --- WP-05: PROPERTY REGISTRATION WIZARD (STEP 1) ---

export interface HotelImage {
  id?: number;
  imageUrl: string;
  originalFileName?: string;
  isCover: boolean;
  cover?: boolean;
  displayOrder: number;
  previewUrl?: string; // Client-side preview data URL
  file?: File;         // Local selected file before upload
  uploading?: boolean;
  error?: string;
}

export interface HotelStep1Request {
  id?: number | null;
  name: string;
  propertyType: string;
  propertyClassification?: string | null;
  customClassification?: string | null;
  propertySegment: string;
  brandName?: string | null;
  description: string;
  images: HotelImage[];
}

export interface HotelStep1Response {
  id: number;
  managerId: number;
  managerName: string;
  name: string;
  propertyType: string;
  propertyClassification?: string;
  customClassification?: string;
  starRating?: number;
  propertySegment: string;
  brandName?: string;
  description: string;
  coverImageUrl?: string;
  status: string;
  registrationStep: number;
  images: HotelImage[];
}
