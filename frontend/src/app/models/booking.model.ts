export type ServiceType = 'BUS' | 'HOTEL';
export type BookingStatus = 'CONFIRMED' | 'PENDING' | 'CANCELLED';

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

  // Enriched presentation fields
  title?: string;
  subtitle?: string;
  dateText?: string;
  timeText?: string;
  badgeClass?: string;
  statusBadgeClass?: string;
  formattedAmount?: string;
  raw?: any;
}
