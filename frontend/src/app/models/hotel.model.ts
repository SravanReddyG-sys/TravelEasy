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
  adults?: number;
  children?: number;
}

export interface HotelPublicDto {
  id: number;
  name: string;
  city: string;
  address: string;
  starRating?: number;
  description?: string;
  amenities?: string;
  imageUrl?: string;
  minPrice?: number;
  minTotalPricePerRoomForStay?: number;
  selectedRoomTypeId?: number;
  propertyType?: string;
}

export interface HotelFilterCriteria {
  minPrice: number | null;
  maxPrice: number | null;
  propertyTypes: string[];
  amenities: string[];
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
  isNew?: boolean;
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

// --- WP-HB-UI-02: DATE-AWARE ROOM DISCOVERY & HOTEL DETAILS ---

export interface DateAwareNightlyRateDto {
  date: string;
  price: number;
  availableRooms: number;
  override?: boolean;
  isOverride?: boolean;
}

export interface DateAwareRoomTypeDto {
  id: number;
  hotelId: number;
  roomTypeName: string;
  maxOccupancy: number;
  basePricePerNight?: number | null;
  amenities?: string;
  imageUrl?: string;
  status: string;
  availableAcrossStay: boolean;
  availableRoomsAcrossStay: number;
  minNightlyPrice?: number | null;
  totalPriceForStay?: number | null;
  nightlyBreakdown?: DateAwareNightlyRateDto[];
}

export interface HotelDetailsResponseDto {
  hotel: HotelPublicDto;
  rooms: DateAwareRoomTypeDto[];
  images?: HotelImage[];
}

export interface SelectedRoomItem {
  roomTypeId: number;
  roomTypeName: string;
  maxOccupancy: number;
  quantity: number;
  minNightlyPrice: number;
  stayTotalPricePerRoom: number;
  totalStayPrice: number;
  imageUrl?: string | null;
}

export interface HotelStaySummary {
  hotelId: number;
  hotelName: string;
  city: string;
  propertyType?: string | null;
  address?: string | null;
  hotelImageUrl?: string | null;
  checkIn: string;
  checkOut: string;
  totalNights: number;
  requestedRooms: number;
  adults: number;
  children: number;
  selectedItems: SelectedRoomItem[];
  totalSelectedRooms: number;
  estimatedTotalAmount: number;
}

export type GuestType = 'ADULT' | 'CHILD';

export type GuestGender = 'MALE' | 'FEMALE' | 'OTHER' | 'PREFER_NOT_TO_SAY';

export interface GuestFormEntry {
  id: string;
  type: GuestType;
  name: string;
  age: number | null;
  gender: GuestGender | null;
}

export interface RoomValidationResult {
  roomKey: string;
  roomLabel: string;
  valid: boolean;
  totalGuests: number;
  adultCount: number;
  childCount: number;
  atMaxCapacity: boolean;
  roomErrors: string[];
  guestErrors: Record<string, { name?: string; age?: string; gender?: string }>;
}

export interface PhysicalRoomAllocation {
  roomKey: string;
  roomTypeId: number;
  roomTypeName: string;
  roomSequenceOverall: number;
  roomSequenceInType: number;
  maxOccupancy: number;
  nightlyPrice: number;
  stayTotalPricePerRoom: number;
  imageUrl?: string | null;
  expanded: boolean;
  selectedAddGuestType: GuestType;
  occupants: GuestFormEntry[];
}

export interface PersistedGuestEntry {
  id: string;
  type: GuestType;
  name: string;
  age: number | null;
  gender: GuestGender | null;
}

export interface PersistedPhysicalRoomState {
  roomKey: string;
  roomTypeId: number;
  roomSequenceOverall: number;
  roomSequenceInType: number;
  selectedAddGuestType: GuestType;
  occupants: PersistedGuestEntry[];
}

export interface GuestAllocationJourneyState {
  staySummary: HotelStaySummary;
  rooms: PersistedPhysicalRoomState[];
  activeExpandedRoomKey: string | null;
  updatedAt: string;
}

export interface HotelRoomOccupantPayload {
  name: string;
  type: GuestType;
  age: number | null;
  gender: GuestGender | null;
}

export interface HotelRoomGuestAllocationPayload {
  roomSequence: number;
  adults: number;
  children: number;
  occupants: HotelRoomOccupantPayload[];
}

export interface HotelReservationAllocationItemPayload {
  roomId: number;
  quantity: number;
  rooms: HotelRoomGuestAllocationPayload[];
}

export interface HotelDetailsCriteria {
  hotelId: number;
  city: string;
  checkIn: string;
  checkOut: string;
  rooms: number;
  adults: number;
  children: number;
}

export interface CountryDialCodeOption {
  code: string;
  name: string;
  dialCode: string;
  minDigits: number;
  maxDigits: number;
  pattern: RegExp;
  placeholder: string;
  formatHint: string;
}

export interface PrimaryGuestFormState {
  fullName: string;
  email: string;
  dialCode: string;
  nationalNumber: string;
}

export interface PrimaryGuestDtoPayload {
  fullName: string;
  email: string;
  mobile: string;
}

export interface PrimaryGuestValidationErrors {
  fullName?: string;
  email?: string;
  dialCode?: string;
  nationalNumber?: string;
}

export interface PrimaryGuestValidationResult {
  valid: boolean;
  errors: PrimaryGuestValidationErrors;
  payload: PrimaryGuestDtoPayload | null;
}

export interface ReservationReviewSummaryMetrics {
  roomTypeRows: Array<{
    roomTypeId: number;
    roomTypeName: string;
    quantity: number;
    stayTotalPricePerRoom: number;
    subtotalAmount: number;
  }>;
  estimatedTotalAmount: number;
  pricingConsistent: boolean;
  totalRooms: number;
  totalAdults: number;
  totalChildren: number;
  totalGuests: number;
}

export interface ReservationReviewJourneyState {
  userId?: number | null;
  staySummary: HotelStaySummary;
  rooms: PersistedPhysicalRoomState[];
  primaryGuest: PrimaryGuestFormState;
  primaryGuestPayload: PrimaryGuestDtoPayload;
  summaryMetrics: ReservationReviewSummaryMetrics;
  updatedAt: string;
}




