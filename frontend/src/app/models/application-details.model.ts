import { Role } from './user.model';

export interface ApplicationDetails {
  id: number;
  organizationName: string;
  applicationType: string;
  role: Role;
  submittedOn: string;
  applicationStatus: string;
  accountStatus: string;
  verified: boolean;

  // Representative / Account Details
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  representativeDesignation: string;

  // Business Details
  legalOrganizationName: string;
  businessStructure: string;
  licensePermitNumber: string; // Passenger Transport Permit Number or Hotel License Number
  businessPanNumber: string;
  gstinNumber?: string;
  registeredAddress: string;
  businessContactEmail: string;
  businessContactPhone: string;
}
