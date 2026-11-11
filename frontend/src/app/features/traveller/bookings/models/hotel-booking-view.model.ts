import {
  HotelDetailsResponseDto,
  HotelReservationDetailResponse,
  PaymentTransactionDto,
  PrimaryGuestDtoPayload
} from '../../../../models/hotel.model';

export type HotelModalTabId = 'details' | 'payment' | 'policy';

export interface HotelBookingRefundBadge {
  label: string;
  badgeClass: string;
  iconClass: string;
}

export interface HotelBookingCardViewModel {
  raw: any;
  id: number;
  bookingRef: string;
  serviceType: 'HOTEL';
  serviceId: number;
  hotelName: string;
  displayTitle: string;
  locationText: string | null;
  displaySubtitle: string;
  thumbnail: string;
  checkInIso: string | null;
  checkOutIso: string | null;
  stayDatesText: string;
  dateText: string;
  timeText: string;
  nightsCount: number | null;
  nightsText: string | null;
  roomTypesSummary: string | null;
  roomsCountLabel: string | null;
  guestsSummaryLabel: string | null;
  capacityText: string;
  totalAmount: number;
  currency: string;
  formattedTotalPaid: string;
  bookingDateText: string | null;
  status: string;
  statusDisplay: string;
  statusBadgeClass: string;
  refundBadge: HotelBookingRefundBadge | null;
  refundStatus: string | null;
  refundId: string | null;
  refundAmount: number | null;
  isCancellable: boolean;
  isCompleted: boolean;
  isConfirmed: boolean;
  isEnriched: boolean;
  rawBookingDate?: string | null;
  detailsJson?: string | null;
}

export interface HotelBookingReviewSummary {
  averageRating: number;
  formattedRating: string;
  reviewCount: number;
  formattedReviewCount: string;
  stars: Array<'full' | 'half' | 'empty'>;
}

export interface HotelBookingGuestRow {
  index: number;
  name: string;
  type: 'ADULT' | 'CHILD';
  isChild: boolean;
  ageDisplay: string;
  genderDisplay: string;
}

export interface HotelBookingRoomCardViewModel {
  roomSequenceOverall: number;
  roomId: number;
  roomTypeName: string;
  imageUrl: string;
  roomCountSummary: string;
  occupancySummary: string;
  adults: number;
  children: number;
  guests: HotelBookingGuestRow[];
}

export interface HotelBookingPriceLineItem {
  roomTypeName: string;
  quantity: number;
  amount: number;
  formattedAmount: string;
}

export type HotelRefundStateKey =
  | 'NONE'
  | 'NON_REFUNDABLE'
  | 'PENDING'
  | 'RETRY_QUEUED'
  | 'FAILED'
  | 'PROCESSED';

export interface HotelBookingRefundViewModel {
  stateKey: HotelRefundStateKey;
  badgeLabel: string;
  badgeClass: string;
  iconClass: string;
  headline: string;
  description: string;
  refundAmount: number | null;
  formattedRefundAmount: string | null;
  refundId: string | null;
  refundedAtText: string | null;
  cancelledAtText: string | null;
  cancellationReason: string | null;
  cancellationPolicyTier: string | null;
  cancellationFeeText: string | null;
}

export interface HotelCancellationPolicyRule {
  windowLabel: string;
  refundLabel: string;
  badgeTone: 'full' | 'partial' | 'none';
  description: string;
}

export interface HotelBookingDetailsViewModel {
  bookingId: number;
  bookingRef: string;
  bookedOnText: string | null;
  status: string;
  statusLabel: string;
  statusBadgeClass: string;
  hotelId: number;
  hotelName: string;
  locationText: string | null;
  hotelImageUrl: string;
  reviewSummary: HotelBookingReviewSummary | null;
  checkInDateText: string;
  checkInTimeNote: string;
  checkOutDateText: string;
  checkOutTimeNote: string | null;
  durationText: string;
  nightsCount: number | null;
  primaryGuest: PrimaryGuestDtoPayload | null;
  rooms: HotelBookingRoomCardViewModel[];
  isLegacyWithoutAllocations: boolean;
  legacyRoomSummary: string | null;
  currency: string;
  priceLineItems: HotelBookingPriceLineItem[];
  taxesNote: string;
  totalAmount: number;
  formattedTotalPaid: string;
  paymentStatusLabel: string;
  paymentReference: string | null;
  refund: HotelBookingRefundViewModel;
  policyRules: ReadonlyArray<HotelCancellationPolicyRule>;
  policyMetaNote: string;
}

export interface HotelBookingEnrichmentContext {
  hotelDetails?: HotelDetailsResponseDto | null;
  reservationDetails?: HotelReservationDetailResponse | null;
  paymentTransaction?: PaymentTransactionDto | null;
  reviews?: any[] | null;
}
