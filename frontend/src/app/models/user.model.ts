export type Role = 'ROLE_CUSTOMER' | 'ROLE_BUS_OPERATOR' | 'ROLE_HOTEL_MANAGER' | 'ROLE_ADMIN';

export interface User {
  id: number;
  email: string;
  fullName: string;
  phone?: string;
  role: Role;
  status?: string;
  verificationStatus?: string;
  verified?: boolean;
  token?: string;
}
