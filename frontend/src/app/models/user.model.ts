export type Role = 'ROLE_CUSTOMER' | 'ROLE_BUS_OPERATOR' | 'ROLE_HOTEL_MANAGER' | 'ROLE_ADMIN';

export interface User {
  id: number;
  email: string;
  firstName?: string;
  lastName?: string;
  fullName: string;
  phone?: string;
  role: Role;
  status?: string;
  verificationStatus?: string;
  verified?: boolean;

  // Organization & Business fields
  legalOrganizationName?: string;
  representativeDesignation?: string;
  businessStructure?: string;
  licensePermitNumber?: string;
  businessPan?: string;
  businessPanNumber?: string;
  gstin?: string;
  gstinNumber?: string;
  registeredAddress?: string;
  businessContactEmail?: string;
  businessContactPhone?: string;
  createdAt?: string;

  token?: string;
}
