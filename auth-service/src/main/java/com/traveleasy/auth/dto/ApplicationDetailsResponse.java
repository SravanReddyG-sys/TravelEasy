package com.traveleasy.auth.dto;

import com.traveleasy.auth.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDetailsResponse {
    private Long id;
    private String organizationName;
    private String legalOrganizationName;
    private String applicationType;
    private Role role;
    private LocalDateTime submittedOn;
    private LocalDateTime createdAt;
    private String applicationStatus;
    private String verificationStatus;
    private String accountStatus;
    private String status;
    private boolean verified;

    // Representative / Account Details
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phone;
    private String representativeDesignation;

    // Business Details
    private String businessStructure;
    private String licensePermitNumber;
    private String businessPan;
    private String businessPanNumber;
    private String gstin;
    private String gstinNumber;
    private String registeredAddress;
    private String businessContactEmail;
    private String businessContactPhone;
}
