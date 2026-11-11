export type ServiceType = 'BUS' | 'HOTEL';
export type BookingStatus = 'CONFIRMED' | 'PENDING' | 'CANCELLED' | 'CHECKED_IN' | 'CHECKED_OUT' | 'NO_SHOW';

export interface Booking {
  id: number;
  userId?: number;
  serviceType: ServiceType;
  serviceId: number;
  providerId?: number;
  customerName?: string;
  customerEmail?: string;
  customerPhone?: string;
  numberOfSeatsOrRooms?: number;
  seatNumbers?: string;
  checkInDate?: string;
  checkOutDate?: string;
  totalAmount?: number;
  status: BookingStatus;
  bookingReference?: string;
  createdAt?: string;
  checkedInAt?: string;
  checkedOutAt?: string;
  noShowAt?: string;
  cancellationPolicyTier?: string;
  cancellationFee?: number;

  // Enriched presentation fields
  title?: string;
  subtitle?: string;
  dateText?: string;
  timeText?: string;
  badgeClass?: string;
  statusBadgeClass?: string;
  formattedAmount?: string;
  raw?: any;
  exteriorImageUrl?: string;
  busType?: string;
  facilityType?: string;
  operatorBusinessName?: string;
}
