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

export interface HotelReservationInitiationRequest {
  bookingRef?: string;
  customerId?: number;
  customerName?: string;
  customerEmail?: string;
  primaryGuest: PrimaryGuestDtoPayload;
  hotelId: number;
  hotelName?: string;
  checkInDate: string;
  checkOutDate: string;
  allocations: HotelReservationAllocationItemPayload[];
  guestDetailsJson?: string;
}

export interface HotelReservationAllocationSnapshotDto {
  allocationId: number;
  roomId: number;
  roomTypeName: string;
  quantity: number;
  allocationTotal: number;
  nightlyRatesJson?: string | null;
  status: string;
  rooms: HotelRoomGuestAllocationPayload[];
}

export interface HotelReservationInitiationResponse {
  bookingId: number;
  bookingRef: string;
  status: string;
  totalAmount: number;
  currency: string;
  expiresAt: string;
  hotelId: number;
  hotelName: string;
  checkInDate: string;
  checkOutDate: string;
  totalNights: number;
  idempotent?: boolean;
  primaryGuest?: PrimaryGuestDtoPayload | null;
  allocations?: HotelReservationAllocationSnapshotDto[];
}

export interface HotelReservationDetailResponse {
  bookingId: number;
  bookingRef: string;
  bookingStatus: string;
  hotel?: {
    hotelId?: number | null;
    hotelName?: string | null;
  } | null;
  checkInDate?: string | null;
  checkOutDate?: string | null;
  totalNights?: number | null;
  primaryGuest?: PrimaryGuestDtoPayload | null;
  allocations?: Array<{
    allocationId: number;
    roomId: number;
    roomTypeName: string;
    quantity: number;
    rooms: HotelRoomGuestAllocationPayload[];
    pricing?: {
      allocationTotal?: number;
      nightlyRatesJson?: string | null;
    } | null;
    status?: string;
    guestAllocationJson?: string | null;
  }>;
  totalAmount: number;
  currency: string;
  paymentStatus?: string;
  expiresAt?: string | null;
  bookingDate?: string | null;
  cancellation?: {
    cancelled?: boolean;
    cancelledAt?: string | null;
    cancellationReason?: string | null;
    refundAmount?: number | null;
    refundStatus?: string | null;
  } | null;
}

export interface PaymentConfigResponse {
  keyId: string;
  currency: string;
}

export interface PaymentCreateOrderRequest {
  bookingRef: string;
  amount: number;
  customerId?: number;
  customerName?: string;
  customerEmail?: string;
  customerPhone?: string;
  paymentMethod?: string;
}

export interface PaymentCreateOrderResponse {
  orderId: string;
  transactionRef: string;
  bookingRef: string;
  amount: number;
  amountInPaise: number;
  currency: string;
  keyId: string;
  status: string;
}

export interface VerifyPaymentRequest {
  bookingRef: string;
  razorpayOrderId: string;
  razorpayPaymentId: string;
  razorpaySignature: string;
}

export interface PaymentTransactionDto {
  id?: number;
  transactionRef?: string;
  bookingRef?: string;
  customerId?: number;
  amount?: number;
  currency?: string;
  paymentMethod?: string;
  status?: string;
  razorpayOrderId?: string;
  razorpayPaymentId?: string;
  refundId?: string | null;
  refundStatus?: string | null;
  refundAmount?: number | null;
  refundedAt?: string | null;
  errorCode?: string | null;
  errorDescription?: string | null;
  timestamp?: string | null;
}

export interface PaymentVerificationResponse {
  verified: boolean;
  message: string;
  bookingRef: string;
  transactionRef?: string | null;
  paymentId?: string | null;
  status?: string | null;
  transaction?: PaymentTransactionDto | null;
}

export interface HotelPaymentCheckoutSessionState {
  userId?: number | null;
  bookingRef: string;
  hotelId: number;
  staySignature: string;
  allocationsSignature: string;
  lastKnownOrderId?: string | null;
  updatedAt: string;
}

export interface HotelActiveReservation {
  bookingId: number;
  bookingRef: string;
  bookingStatus: string;
  totalAmount: number;
  currency: string;
  expiresAt: string | null;
  hotelId: number;
  hotelName: string;
  checkInDate: string;
  checkOutDate: string;
  totalNights: number;
  paymentStatus?: string | null;
  primaryGuest?: PrimaryGuestDtoPayload | null;
  allocationsSummary: Array<{
    roomId: number;
    roomTypeName: string;
    quantity: number;
    adultsCount?: number | null;
    childrenCount?: number | null;
  }>;
  cancellation?: HotelReservationDetailResponse['cancellation'];
}

export type HotelPaymentUiState =
  | 'INITIATING'
  | 'INITIATION_FAILED'
  | 'PENDING_PAYMENT'
  | 'PROCESSING'
  | 'CONFIRMED'
  | 'PAYMENT_FAILED'
  | 'EXPIRED'
  | 'REFUND_INITIATED'
  | 'CONFIRMATION_PENDING';





