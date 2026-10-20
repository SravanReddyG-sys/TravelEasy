package com.traveleasy.auth.controller;

import com.traveleasy.auth.dto.*;
import com.traveleasy.auth.model.Role;
import com.traveleasy.auth.model.User;
import com.traveleasy.auth.repository.UserRepository;
import com.traveleasy.auth.security.JwtUtil;
import com.traveleasy.auth.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String appFrontendUrl;

    @PostMapping("/register/traveller")
    public ResponseEntity<?> registerTraveller(@Valid @RequestBody TravellerRegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password and Confirm Password do not match!"));
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String normalizedPhone = request.getPhone().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email address is already registered!"));
        }
        if (userRepository.existsByPhone(normalizedPhone)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phone number is already registered!"));
        }

        String firstName = request.getFirstName().trim();
        String lastName = request.getLastName().trim();

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .fullName(firstName + " " + lastName)
                .email(normalizedEmail)
                .phone(normalizedPhone)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_CUSTOMER)
                .verified(true)
                .status("ACTIVE")
                .verificationStatus("APPROVED")
                .termsAccepted(true)
                .termsAcceptedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name(), user.getFullName());

        return ResponseEntity.status(HttpStatus.CREATED).body(buildAuthResponse(user, token));
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        if ((firstName == null || firstName.trim().isEmpty()) && (lastName == null || lastName.trim().isEmpty())) {
            if (user.getFullName() != null && !user.getFullName().trim().isEmpty()) {
                String fullName = user.getFullName().trim();
                int spaceIdx = fullName.indexOf(' ');
                if (spaceIdx > 0) {
                    firstName = fullName.substring(0, spaceIdx).trim();
                    lastName = fullName.substring(spaceIdx + 1).trim();
                } else {
                    firstName = fullName;
                    lastName = "";
                }
            }
        }

        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .email(user.getEmail())
                .firstName(firstName)
                .lastName(lastName)
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .verificationStatus(user.getVerificationStatus())
                .verified(user.isVerified())
                .legalOrganizationName(user.getLegalOrganizationName())
                .representativeDesignation(user.getRepresentativeDesignation())
                .businessStructure(user.getBusinessStructure())
                .licensePermitNumber(user.getLicensePermitNumber())
                .businessPan(user.getBusinessPan())
                .businessPanNumber(user.getBusinessPan())
                .gstin(user.getGstin())
                .gstinNumber(user.getGstin())
                .registeredAddress(user.getRegisteredAddress())
                .businessContactEmail(user.getBusinessContactEmail())
                .businessContactPhone(user.getBusinessContactPhone())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private static final java.util.Set<String> ALLOWED_BUS_OPERATOR_DESIGNATIONS = java.util.Set.of(
        "OWNER_PROPRIETOR", "Owner / Proprietor",
        "MANAGING_DIRECTOR", "Managing Director",
        "DIRECTOR", "Director",
        "TRANSPORT_MANAGER", "Transport Manager",
        "OPERATIONS_MANAGER", "Operations Manager",
        "GENERAL_MANAGER", "General Manager",
        "AUTHORIZED_REPRESENTATIVE", "Authorized Representative",
        "OTHER", "Other"
    );

    private static final java.util.Set<String> ALLOWED_HOTEL_PARTNER_DESIGNATIONS = java.util.Set.of(
        "HOTEL_OWNER", "Hotel Owner",
        "MANAGING_DIRECTOR", "Managing Director",
        "GENERAL_MANAGER", "General Manager",
        "HOTEL_MANAGER", "Hotel Manager",
        "OPERATIONS_MANAGER", "Operations Manager",
        "PROPERTY_MANAGER", "Property Manager",
        "RESERVATIONS_MANAGER", "Reservations Manager",
        "FRONT_OFFICE_MANAGER", "Front Office Manager",
        "AUTHORIZED_REPRESENTATIVE", "Authorized Representative",
        "OTHER", "Other"
    );

    private boolean isValidBusOperatorDesignation(String designation) {
        if (designation == null || designation.trim().isEmpty()) {
            return false;
        }
        for (String allowed : ALLOWED_BUS_OPERATOR_DESIGNATIONS) {
            if (allowed.equalsIgnoreCase(designation.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidHotelPartnerDesignation(String designation) {
        if (designation == null || designation.trim().isEmpty()) {
            return false;
        }
        for (String allowed : ALLOWED_HOTEL_PARTNER_DESIGNATIONS) {
            if (allowed.equalsIgnoreCase(designation.trim())) {
                return true;
            }
        }
        return false;
    }

    @PostMapping("/register/bus-operator")
    public ResponseEntity<?> registerBusOperator(@Valid @RequestBody BusOperatorRegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password and Confirm Password do not match!"));
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String normalizedPhone = request.getPhone().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email address is already registered!"));
        }
        if (userRepository.existsByPhone(normalizedPhone)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phone number is already registered!"));
        }

        // Validate representative designation
        String rawDesignation = request.getRepresentativeDesignation().trim();
        String savedDesignation;
        if ("OTHER".equalsIgnoreCase(rawDesignation) || "Other".equalsIgnoreCase(rawDesignation)) {
            if (request.getCustomDesignation() == null || request.getCustomDesignation().trim().length() < 2 || request.getCustomDesignation().trim().length() > 100) {
                return ResponseEntity.badRequest().body(Map.of("message", "Please specify a valid custom designation between 2 and 100 characters"));
            }
            savedDesignation = request.getCustomDesignation().trim();
        } else if (!isValidBusOperatorDesignation(rawDesignation)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid representative designation selected"));
        } else {
            savedDesignation = rawDesignation;
        }

        String firstName = request.getFirstName().trim();
        String lastName = request.getLastName().trim();
        String gstin = request.getGstinNumber() != null && !request.getGstinNumber().trim().isEmpty() 
                ? request.getGstinNumber().trim().toUpperCase() : null;

        String resolvedAddress = request.getRegisteredAddress() != null ? request.getRegisteredAddress().trim() : "";
        if (resolvedAddress.isEmpty() && request.getFlatBuildingOffice() != null) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (request.getFlatBuildingOffice() != null && !request.getFlatBuildingOffice().trim().isEmpty()) parts.add(request.getFlatBuildingOffice().trim());
            if (request.getStreetRoad() != null && !request.getStreetRoad().trim().isEmpty()) parts.add(request.getStreetRoad().trim());
            if (request.getLandmark() != null && !request.getLandmark().trim().isEmpty()) parts.add(request.getLandmark().trim());
            if (request.getCityLocality() != null && !request.getCityLocality().trim().isEmpty()) parts.add(request.getCityLocality().trim());
            if (request.getState() != null && !request.getState().trim().isEmpty()) parts.add(request.getState().trim());
            if (request.getCountry() != null && !request.getCountry().trim().isEmpty()) parts.add(request.getCountry().trim());
            if (request.getPinCode() != null && !request.getPinCode().trim().isEmpty()) parts.add(request.getPinCode().trim());
            resolvedAddress = String.join(", ", parts);
        }

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .fullName(firstName + " " + lastName)
                .email(normalizedEmail)
                .phone(normalizedPhone)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_BUS_OPERATOR)
                .verified(false)
                .status("PENDING")
                .verificationStatus("UNDER_REVIEW")
                .representativeDesignation(savedDesignation)
                .legalOrganizationName(request.getLegalOrganizationName().trim())
                .businessStructure(request.getBusinessStructure().trim())
                .licensePermitNumber(request.getPassengerTransportPermitNumber().trim())
                .businessPan(request.getBusinessPanNumber().trim().toUpperCase())
                .gstin(gstin)
                .registeredAddress(resolvedAddress)
                .businessContactEmail(request.getBusinessContactEmail().trim().toLowerCase())
                .businessContactPhone(request.getBusinessContactPhone().trim())
                .termsAccepted(true)
                .termsAcceptedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        String appToken = jwtUtil.generateApplicationToken(user.getId(), user.getEmail(), user.getRole().name(), user.getFullName());
        AuthResponse response = buildAuthResponse(user, null);
        response.setApplicationToken(appToken);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/register/hotel-partner")
    public ResponseEntity<?> registerHotelPartner(@Valid @RequestBody HotelPartnerRegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password and Confirm Password do not match!"));
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String normalizedPhone = request.getPhone().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email address is already registered!"));
        }
        if (userRepository.existsByPhone(normalizedPhone)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phone number is already registered!"));
        }

        // Validate representative designation
        String rawDesignation = request.getRepresentativeDesignation().trim();
        String savedDesignation;
        if ("OTHER".equalsIgnoreCase(rawDesignation) || "Other".equalsIgnoreCase(rawDesignation)) {
            if (request.getCustomDesignation() == null || request.getCustomDesignation().trim().length() < 2 || request.getCustomDesignation().trim().length() > 100) {
                return ResponseEntity.badRequest().body(Map.of("message", "Please specify a valid custom designation between 2 and 100 characters"));
            }
            savedDesignation = request.getCustomDesignation().trim();
        } else if (!isValidHotelPartnerDesignation(rawDesignation)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid representative designation selected"));
        } else {
            savedDesignation = rawDesignation;
        }

        String firstName = request.getFirstName().trim();
        String lastName = request.getLastName().trim();
        String gstin = request.getGstinNumber() != null && !request.getGstinNumber().trim().isEmpty()
                ? request.getGstinNumber().trim().toUpperCase() : null;

        String resolvedAddress = request.getRegisteredAddress() != null ? request.getRegisteredAddress().trim() : "";
        if (resolvedAddress.isEmpty() && request.getFlatBuildingOffice() != null) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (request.getFlatBuildingOffice() != null && !request.getFlatBuildingOffice().trim().isEmpty()) parts.add(request.getFlatBuildingOffice().trim());
            if (request.getStreetRoad() != null && !request.getStreetRoad().trim().isEmpty()) parts.add(request.getStreetRoad().trim());
            if (request.getLandmark() != null && !request.getLandmark().trim().isEmpty()) parts.add(request.getLandmark().trim());
            if (request.getCityLocality() != null && !request.getCityLocality().trim().isEmpty()) parts.add(request.getCityLocality().trim());
            if (request.getState() != null && !request.getState().trim().isEmpty()) parts.add(request.getState().trim());
            if (request.getCountry() != null && !request.getCountry().trim().isEmpty()) parts.add(request.getCountry().trim());
            if (request.getPinCode() != null && !request.getPinCode().trim().isEmpty()) parts.add(request.getPinCode().trim());
            resolvedAddress = String.join(", ", parts);
        }

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .fullName(firstName + " " + lastName)
                .email(normalizedEmail)
                .phone(normalizedPhone)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_HOTEL_MANAGER)
                .verified(false)
                .status("PENDING")
                .verificationStatus("UNDER_REVIEW")
                .representativeDesignation(savedDesignation)
                .legalOrganizationName(request.getLegalOrganizationName().trim())
                .businessStructure(request.getBusinessStructure().trim())
                .licensePermitNumber(request.getCompanyLicenseNumber().trim())
                .businessPan(request.getBusinessPanNumber().trim().toUpperCase())
                .gstin(gstin)
                .registeredAddress(resolvedAddress)
                .businessContactEmail(request.getBusinessContactEmail().trim().toLowerCase())
                .businessContactPhone(request.getBusinessContactPhone().trim())
                .termsAccepted(true)
                .termsAcceptedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        String appToken = jwtUtil.generateApplicationToken(user.getId(), user.getEmail(), user.getRole().name(), user.getFullName());
        AuthResponse response = buildAuthResponse(user, null);
        response.setApplicationToken(appToken);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request == null || request.getEmail() == null || request.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid email or password"));
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid email or password"));
        }

        // 1. Account status check: Suspended accounts
        if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "message", "Your account has been temporarily suspended.",
                "status", "SUSPENDED"
            ));
        }

        // 2. Verification status check: Rejected accounts
        if ("REJECTED".equalsIgnoreCase(user.getVerificationStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "message", "Unfortunately, your application has not been approved.",
                "status", "REJECTED"
            ));
        }

        // 3. Account status check: Deactivated accounts
        if ("DEACTIVATED".equalsIgnoreCase(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "message", "Your account has been deactivated. Please contact support.",
                "status", "DEACTIVATED"
            ));
        }

        // 4. Role-specific account status and verification checks
        if (user.getRole() == Role.ROLE_BUS_OPERATOR || user.getRole() == Role.ROLE_HOTEL_MANAGER) {
            if ("PENDING".equalsIgnoreCase(user.getStatus()) || "UNDER_REVIEW".equalsIgnoreCase(user.getVerificationStatus())) {
                String appToken = jwtUtil.generateApplicationToken(user.getId(), user.getEmail(), user.getRole().name(), user.getFullName());
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("message", "Your application is currently under review.");
                responseBody.put("status", "UNDER_REVIEW");
                responseBody.put("applicationToken", appToken);
                responseBody.put("applicationDetails", buildApplicationDetailsResponse(user));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseBody);
            }

            boolean isApprovedPartner = "ACTIVE".equalsIgnoreCase(user.getStatus()) &&
                    ("APPROVED".equalsIgnoreCase(user.getVerificationStatus()) || user.isVerified());

            if (!isApprovedPartner) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "message", "Access denied. Your partner account is not approved to log in.",
                    "status", "FORBIDDEN"
                ));
            }
        } else if (user.getRole() == Role.ROLE_CUSTOMER || user.getRole() == Role.ROLE_ADMIN) {
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "message", "Access denied. Your account is not active.",
                    "status", "FORBIDDEN"
                ));
            }
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "message", "Access denied. Your account is not authorized to log in.",
                "status", "FORBIDDEN"
            ));
        }

        // 5. Generate token and authenticated session ONLY after all checks succeed
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name(), user.getFullName());

        return ResponseEntity.ok(buildAuthResponse(user, token));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user != null) {
            String token = UUID.randomUUID().toString();
            user.setResetPasswordToken(token);
            user.setResetPasswordTokenExpiry(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);

            String baseUrl = (appFrontendUrl != null && !appFrontendUrl.isBlank())
                    ? appFrontendUrl.replaceAll("/+$", "")
                    : "http://localhost:4200";
            String resetUrl = baseUrl + "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

            try {
                emailService.sendPasswordResetEmail(user, resetUrl);
            } catch (Exception ignored) {
            }
        }

        return ResponseEntity.ok(Map.of(
            "message", "We'll send password-reset instructions to your email address if an account exists."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Passwords do not match!"));
        }

        User user = userRepository.findByResetPasswordToken(request.getToken()).orElse(null);
        if (user == null || user.getResetPasswordTokenExpiry() == null || user.getResetPasswordTokenExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired password reset token!"));
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully. You can now login with your new password."));
    }

    // Legacy fallback for backwards compatibility
    @PostMapping("/register")
    public ResponseEntity<?> registerLegacy(@RequestBody RegisterRequest request) {
        Role userRole = request.getRole() != null ? request.getRole() : Role.ROLE_CUSTOMER;
        if (userRole == Role.ROLE_BUS_OPERATOR || userRole == Role.ROLE_HOTEL_MANAGER) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", "Please use the dedicated partner registration forms for Bus Operator and Hotel Partner applications."
            ));
        }
        TravellerRegisterRequest travellerReq = new TravellerRegisterRequest();
        travellerReq.setFirstName(request.getFullName() != null ? request.getFullName().split(" ")[0] : "User");
        travellerReq.setLastName(request.getFullName() != null && request.getFullName().contains(" ") ? request.getFullName().substring(request.getFullName().indexOf(" ") + 1) : "Traveller");
        travellerReq.setEmail(request.getEmail());
        travellerReq.setPhone(request.getPhone() != null ? request.getPhone() : "+919876543210");
        travellerReq.setPassword(request.getPassword());
        travellerReq.setConfirmPassword(request.getPassword());
        travellerReq.setTermsAccepted(true);

        return registerTraveller(travellerReq);
    }

    @GetMapping("/application-details")
    public ResponseEntity<?> getApplicationDetails(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Application-Token", required = false) String appTokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {

        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (appTokenHeader != null && !appTokenHeader.trim().isEmpty()) {
            token = appTokenHeader.trim();
        } else if (tokenParam != null && !tokenParam.trim().isEmpty()) {
            token = tokenParam.trim();
        }

        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authorization Bearer token is required"));
        }

        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid or expired token"));
        }

        String authenticatedEmail = jwtUtil.extractEmail(token);
        if (authenticatedEmail == null || authenticatedEmail.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Token contains no valid email subject"));
        }

        User user = userRepository.findByEmail(authenticatedEmail.trim().toLowerCase()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User record not found for authenticated email"));
        }

        // Account status check for protected endpoint
        if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "message", "Your account has been temporarily suspended.",
                "status", "SUSPENDED"
            ));
        }

        if ("DEACTIVATED".equalsIgnoreCase(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "message", "Your account has been deactivated. Please contact support.",
                "status", "DEACTIVATED"
            ));
        }

        return ResponseEntity.ok(buildApplicationDetailsResponse(user));
    }

    private ApplicationDetailsResponse buildApplicationDetailsResponse(User user) {
        // Resolving names: use first_name and last_name, falling back to full_name if absent
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        if ((firstName == null || firstName.trim().isEmpty()) && (lastName == null || lastName.trim().isEmpty())) {
            if (user.getFullName() != null && !user.getFullName().trim().isEmpty()) {
                String fullName = user.getFullName().trim();
                int spaceIdx = fullName.indexOf(' ');
                if (spaceIdx > 0) {
                    firstName = fullName.substring(0, spaceIdx).trim();
                    lastName = fullName.substring(spaceIdx + 1).trim();
                } else {
                    firstName = fullName;
                    lastName = "";
                }
            }
        }

        // Role to application type mapping
        String applicationType;
        if (user.getRole() == Role.ROLE_BUS_OPERATOR) {
            applicationType = "New Bus Service Registration";
        } else if (user.getRole() == Role.ROLE_HOTEL_MANAGER) {
            applicationType = "New Hotel Partner Registration";
        } else {
            applicationType = user.getRole() != null ? user.getRole().name() : "Partner Registration";
        }

        // Organization name from legal_organization_name, falling back to full_name
        String orgName = user.getLegalOrganizationName() != null && !user.getLegalOrganizationName().trim().isEmpty()
                ? user.getLegalOrganizationName().trim()
                : user.getFullName();

        return ApplicationDetailsResponse.builder()
                .id(user.getId())
                .organizationName(orgName)
                .legalOrganizationName(user.getLegalOrganizationName())
                .applicationType(applicationType)
                .role(user.getRole())
                .submittedOn(user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now())
                .createdAt(user.getCreatedAt())
                .applicationStatus(user.getVerificationStatus() != null ? user.getVerificationStatus() : "UNDER_REVIEW")
                .verificationStatus(user.getVerificationStatus() != null ? user.getVerificationStatus() : "UNDER_REVIEW")
                .accountStatus(user.getStatus() != null ? user.getStatus() : "PENDING")
                .status(user.getStatus() != null ? user.getStatus() : "PENDING")
                .verified(user.isVerified())
                .firstName(firstName)
                .lastName(lastName)
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .representativeDesignation(user.getRepresentativeDesignation())
                .businessStructure(user.getBusinessStructure())
                .licensePermitNumber(user.getLicensePermitNumber())
                .businessPan(user.getBusinessPan())
                .businessPanNumber(user.getBusinessPan())
                .gstin(user.getGstin())
                .gstinNumber(user.getGstin())
                .registeredAddress(user.getRegisteredAddress())
                .businessContactEmail(user.getBusinessContactEmail())
                .businessContactPhone(user.getBusinessContactPhone())
                .build();
    }
}
