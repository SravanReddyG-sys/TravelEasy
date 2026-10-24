export interface TravellerProfile {
  id?: number;
  userId?: number;
  firstName?: string;
  lastName?: string;
  fullName?: string;
  email?: string;
  phone?: string;
  city?: string;
  state?: string;
  address?: string;
  pincode?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
}

export interface TravellerPreferences {
  preferredSeat?: string;
  mealPreference?: string;
  preferredLanguage?: string;
  preferredCurrency?: string;
  emailNotifications?: boolean;
  smsNotifications?: boolean;
}

export interface FavouriteItem {
  id?: number;
  itemType: 'BUS' | 'HOTEL' | 'ROUTE' | 'DESTINATION';
  itemId?: string | number;
  title: string;
  subtitle?: string;
  icon?: string;
  route?: string;
  raw?: any;
}

export interface SupportTicket {
  id?: number;
  ticketNumber?: string;
  category: string;
  subject: string;
  message: string;
  status?: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
  createdAt?: string;
}
