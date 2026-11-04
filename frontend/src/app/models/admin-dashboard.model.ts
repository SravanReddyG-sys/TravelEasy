export interface AdminUserStats {
  totalUsers: number;
  travellers: number;
  busOperators: number;
  hotelOperators: number;
}

export interface AdminBookingMetric {
  count: number;
  percentage: number;
}

export interface AdminServiceBreakdown {
  bus: AdminBookingMetric;
  hotel: AdminBookingMetric;
}

export interface AdminBookingStats {
  totalBookings: number;
  serviceBreakdown?: AdminServiceBreakdown;
}

export interface AdminBookingTrend {
  date: string;
  bookingCount: number;
}

export interface AdminRecentBooking {
  bookingRef: string;
  serviceType: string;
  customerName: string;
  totalAmount?: number;
  status: string;
  bookingDate: string;
}

export interface AdminPendingProvider {
  id: number;
  legalOrganizationName: string;
  providerType: string;
  submittedOn: string;
  verificationStatus: string;
}

export interface AdminPendingProvidersPage {
  content: AdminPendingProvider[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface AdminProviderDetails {
  id: number;
  legalOrganizationName: string;
  representativeName?: string;
  representativeDesignation?: string;
  providerType?: string;
  role?: string;
  email?: string;
  phone?: string;
  businessStructure?: string;
  licensePermitNumber?: string;
  businessPanNumber?: string;
  gstinNumber?: string;
  registeredAddress?: string;
  verificationStatus?: string;
  accountStatus?: string;
  verified?: boolean;
  createdAt?: string;
}
