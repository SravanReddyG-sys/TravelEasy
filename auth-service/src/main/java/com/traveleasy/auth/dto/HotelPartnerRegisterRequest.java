package com.traveleasy.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class HotelPartnerRegisterRequest {
    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 50, message = "First name cannot exceed 50 characters")
    @Pattern(regexp = "^[\\p{L} '\\-]+$", message = "First name can only contain letters, spaces, apostrophes and hyphens")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 50, message = "Last name cannot exceed 50 characters")
    @Pattern(regexp = "^[\\p{L} '\\-]+$", message = "Last name can only contain letters, spaces, apostrophes and hyphens")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters long")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character"
    )
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    @NotBlank(message = "Representative designation is required")
    @Size(min = 2, max = 100, message = "Representative designation must be between 2 and 100 characters")
    private String representativeDesignation;

    @NotBlank(message = "Legal organization name is required")
    @Size(min = 2, max = 200, message = "Legal organization name must be between 2 and 200 characters")
    private String legalOrganizationName;

    @NotBlank(message = "Business structure selection is required")
    private String businessStructure;

    @NotBlank(message = "Company/Organization License Number is required")
    @Pattern(regexp = "^\\d{12}$", message = "Company/Organization License Number must be exactly 12 numeric digits")
    private String companyLicenseNumber;

    @NotBlank(message = "Business PAN Number is required")
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Business PAN must follow standard 10-character uppercase PAN format (e.g. ABCDE1234F)")
    private String businessPanNumber;

    @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$", message = "GSTIN must follow valid 15-character GSTIN format")
    private String gstinNumber;

    @NotBlank(message = "Registered address is required")
    private String registeredAddress;

    @NotBlank(message = "Business contact email is required")
    @Email(message = "Invalid business contact email format")
    private String businessContactEmail;

    @NotBlank(message = "Business contact phone is required")
    private String businessContactPhone;

    @AssertTrue(message = "Terms and Conditions must be accepted")
    private boolean termsAccepted;
}
