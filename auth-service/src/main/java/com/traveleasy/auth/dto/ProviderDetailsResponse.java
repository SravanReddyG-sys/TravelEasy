package com.traveleasy.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderDetailsResponse {
    private Long id;
    private String legalOrganizationName;
    private String representativeName;
    private String representativeDesignation;
    private String providerType; // "BUS_OPERATOR" or "HOTEL_OPERATOR"
    private String role; // "ROLE_BUS_OPERATOR" or "ROLE_HOTEL_MANAGER"
    private String email;
    private String phone;
    private String businessStructure;
    private String licensePermitNumber;
    private String businessPan;
    private String gstin;
    private String registeredAddress;
    private String businessContactEmail;
    private String businessContactPhone;
    private String status; // "ACTIVE", "PENDING", "SUSPENDED", "DEACTIVATED"
    private String verificationStatus; // "UNDER_REVIEW", "APPROVED", "REJECTED"
    private boolean verified;
    private LocalDateTime submittedOn;
    private boolean termsAccepted;
    private LocalDateTime termsAcceptedAt;
}
