package com.traveleasy.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String firstName;
    private String lastName;

    @Column(nullable = false)
    private String fullName;

    @Column(unique = true)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder.Default
    private boolean verified = false;

    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, PENDING, SUSPENDED, DEACTIVATED

    private String verificationStatus; // UNDER_REVIEW, APPROVED, REJECTED

    // Representative & Business Organization Fields
    private String representativeDesignation;
    private String legalOrganizationName;
    private String businessStructure;
    private String licensePermitNumber;
    private String businessPan;
    private String gstin;

    @Column(length = 500)
    private String registeredAddress;

    private String businessContactEmail;
    private String businessContactPhone;

    // Consent Audit
    @Builder.Default
    private boolean termsAccepted = false;
    private LocalDateTime termsAcceptedAt;

    // Password Reset
    private String resetPasswordToken;
    private LocalDateTime resetPasswordTokenExpiry;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
