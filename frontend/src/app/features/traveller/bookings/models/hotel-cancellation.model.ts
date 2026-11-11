export type HotelCancellationStep = 1 | 2 | 3;

export type HotelCancellationUiTone = 'success' | 'info' | 'warning' | 'danger' | 'neutral';

export interface HotelCancellationQuoteResponse {
  bookingId: number;
  bookingRef: string;
  serviceType: string;
  currentStatus: string;
  cancellable: boolean;
  alreadyCancelled: boolean;
  policyTier: string;
  totalAmount: number;
  refundPercentage: number;
  refundAmount: number;
  cancellationFee: number;
  eligibleForRefund: boolean;
  description: string;
  checkInDate: string;
  checkOutDate: string;
  scheduledCheckInTime: string;
  evaluationTime: string;
  policyTimeZone: string;
  hoursUntilCheckIn: number;
}

export interface HotelCancellationRequest {
  reason?: string;
}

export interface CancellationPolicyResultDto {
  policyTier: string;
  refundPercentage: number;
  refundAmount: number;
  cancellationFee: number;
  description?: string | null;
  evaluationTime?: string | null;
  scheduledCheckInTime?: string | null;
  hoursUntilCheckIn?: number | null;
  eligibleForRefund: boolean;
}

export interface HotelCancellationResponse {
  message: string;
  bookingId: number;
  bookingRef: string;
  serviceType: string;
  status: string;
  idempotent: boolean;
  previousStatus: string;
  cancelledAt: string | null;
  cancellationReason: string | null;
  cancellationPolicy?: CancellationPolicyResultDto | null;
  refundAmount: number | null;
  cancellationFee: number | null;
  cancellationPolicyTier: string | null;
  inventoryReleaseStatus: string | null;
  refundStatus: string | null;
  refundId: string | null;
  refundAttemptKey: string | null;
  booking?: any;
  refund?: Record<string, any> | null;
}

export interface HotelCancellationPolicyTierDisplay {
  rawTier: string;
  label: string;
  shortBadgeLabel: string;
  tone: 'full' | 'partial' | 'none';
}

export interface HotelCancellationRefundPresentation {
  normalizedStatus: string;
  badgeLabel: string;
  tone: HotelCancellationUiTone;
  iconClass: string;
  bannerTitle: string;
  bannerMessage: string;
}

export interface HotelInventoryReleasePresentation {
  normalizedStatus: string;
  label: string;
  tone: HotelCancellationUiTone;
  iconClass: string;
  bannerMessage: string | null;
}

export interface HotelCancellationResultViewModel {
  bookingId: number;
  bookingRef: string;
  bookingStatus: string;
  idempotent: boolean;
  headline: string;
  subtitle: string;
  headerIconClass: string;
  headerTone: HotelCancellationUiTone;
  cancelledAtText: string | null;
  cancellationReason: string | null;
  refundAmount: number;
  formattedRefundAmount: string;
  cancellationFee: number | null;
  formattedCancellationFee: string | null;
  policyTier: HotelCancellationPolicyTierDisplay | null;
  refundId: string | null;
  refund: HotelCancellationRefundPresentation;
  inventory: HotelInventoryReleasePresentation;
  reconciledFromServerProbe: boolean;
}

export type HotelCancellationPolicyBadgeTone = 'full' | 'partial' | 'none';
