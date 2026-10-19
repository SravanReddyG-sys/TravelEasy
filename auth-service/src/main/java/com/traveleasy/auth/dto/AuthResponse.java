package com.traveleasy.auth.dto;

import com.traveleasy.auth.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private String applicationToken;
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phone;
    private Role role;
    private String status;
    private String verificationStatus;
    private boolean verified;

    // Organization & Business fields
    private String legalOrganizationName;
    private String representativeDesignation;
    private String businessStructure;
    private String licensePermitNumber;
    private String businessPan;
    private String businessPanNumber;
    private String gstin;
    private String gstinNumber;
    private String registeredAddress;
    private String businessContactEmail;
    private String businessContactPhone;
    private LocalDateTime createdAt;
}
