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
  addressLine1?: string;
  addressLine2?: string;
  country?: string;
  state?: string;
  city?: string;
  postalCode?: string;
  landmark?: string;
  address?: string;

  // Step 3 fields
  propertyContactNumber?: string;
  alternateContactNumber?: string;
  propertyEmail?: string;
  websiteUrl?: string;
  registeredBusinessName?: string;
  businessRegistrationType?: string;
  otherBusinessRegistrationType?: string;
  businessRegistrationNumber?: string;
  registeredBusinessAddress?: string;
  isGstRegistered?: boolean;
  gstin?: string;
  gstLegalName?: string;
  gstState?: string;
  propertyManagerName?: string;
  propertyManagerDesignation?: string;
  propertyManagerContactNumber?: string;
  propertyManagerEmail?: string;
  checkInTime?: string;
  checkOutTime?: string;
  receptionAvailability?: string;
  receptionOpeningTime?: string;
  receptionClosingTime?: string;

  // Step 4 fields
  predefinedAmenities?: string[];
  customAmenities?: string[];
  childrenPolicy?: string;
  petPolicy?: string;
  smokingPolicy?: string;
  extraBedPolicy?: string;
  acceptedIdentificationTypes?: string[];
  customIdentificationType?: string;
  additionalRules?: string;

  coverImageUrl?: string;
  status: string;
  registrationStep: number;
  images: HotelImage[];
}

export interface HotelStep2Request {
  draftId?: number | null;
  addressLine1: string;
  addressLine2?: string | null;
  country: string;
  state: string;
  city: string;
  postalCode: string;
  landmark?: string | null;
}

export interface HotelStep2Response extends HotelStep1Response {}

export interface HotelStep3Request {
  draftId?: number | null;
  propertyContactNumber: string;
  alternateContactNumber?: string | null;
  propertyEmail: string;
  websiteUrl?: string | null;
  registeredBusinessName: string;
  businessRegistrationType: string;
  otherBusinessRegistrationType?: string | null;
  businessRegistrationNumber: string;
  registeredBusinessAddress?: string | null;
  isGstRegistered: boolean;
  gstin?: string | null;
  gstLegalName?: string | null;
  gstState?: string | null;
  propertyManagerName: string;
  propertyManagerDesignation: string;
  propertyManagerContactNumber: string;
  propertyManagerEmail: string;
  checkInTime: string;
  checkOutTime: string;
  receptionAvailability: string;
  receptionOpeningTime?: string | null;
  receptionClosingTime?: string | null;
}

export interface HotelStep3Response extends HotelStep1Response {}

export interface HotelStep4Request {
  draftId?: number | null;
  predefinedAmenities: string[];
  customAmenities: string[];
  childrenPolicy: string;
  petPolicy: string;
  smokingPolicy: string;
  extraBedPolicy: string;
  acceptedIdentificationTypes: string[];
  customIdentificationType?: string | null;
  additionalRules?: string | null;
}

export interface HotelStep4Response extends HotelStep1Response {}

export interface HotelStep5SubmitRequest {
  draftId?: number | null;
  confirmationAccepted: boolean;
}

export interface HotelStep5Response extends HotelStep4Response {
  submittedAt?: string;
  activatedAt?: string;
  message?: string;
}

export interface RoomTypeCreateRequest {
  roomTypeName: string;
  maxOccupancy?: number;
  totalRooms: number;
  amenities?: string;
  imageUrl?: string;
}

export interface RoomTypeDto {
  id?: number;
  hotelId?: number;
  roomTypeName: string;
  maxOccupancy: number;
  pricePerNight?: number;
  totalRooms: number;
  availableRooms?: number;
  allocatedRooms?: number;
  amenities?: string;
  imageUrl?: string;
  status?: string;
}

export interface PriceCalendarEntryDto {
  date: string;
  basePrice: number;
  overridePrice?: number | null;
  effectivePrice: number;
  isOverride: boolean;
  override?: boolean;
}

export interface PriceOverrideRequestDto {
  overrideDate: string;
  pricePerNight: number;
}

export interface PriceRangeOverrideRequestDto {
  startDate: string;
  endDate: string;
  pricePerNight: number;
}


