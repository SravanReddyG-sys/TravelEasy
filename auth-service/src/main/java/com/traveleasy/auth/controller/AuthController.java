package com.traveleasy.auth.controller;

import com.traveleasy.auth.dto.*;
import com.traveleasy.auth.model.Role;
import com.traveleasy.auth.model.User;
import com.traveleasy.auth.repository.UserRepository;
import com.traveleasy.auth.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

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

        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .verificationStatus(user.getVerificationStatus())
                .verified(user.isVerified())
                .build());
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

        String firstName = request.getFirstName().trim();
        String lastName = request.getLastName().trim();
        String gstin = request.getGstinNumber() != null && !request.getGstinNumber().trim().isEmpty() 
                ? request.getGstinNumber().trim().toUpperCase() : null;

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
                .representativeDesignation(request.getRepresentativeDesignation().trim())
                .legalOrganizationName(request.getLegalOrganizationName().trim())
                .businessStructure(request.getBusinessStructure().trim())
                .licensePermitNumber(request.getPassengerTransportPermitNumber().trim())
                .businessPan(request.getBusinessPanNumber().trim().toUpperCase())
                .gstin(gstin)
                .registeredAddress(request.getRegisteredAddress().trim())
                .businessContactEmail(request.getBusinessContactEmail().trim().toLowerCase())
                .businessContactPhone(request.getBusinessContactPhone().trim())
                .termsAccepted(true)
                .termsAcceptedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .verificationStatus(user.getVerificationStatus())
                .verified(user.isVerified())
                .build());
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

        String firstName = request.getFirstName().trim();
        String lastName = request.getLastName().trim();
        String gstin = request.getGstinNumber() != null && !request.getGstinNumber().trim().isEmpty()
                ? request.getGstinNumber().trim().toUpperCase() : null;

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
                .representativeDesignation(request.getRepresentativeDesignation().trim())
                .legalOrganizationName(request.getLegalOrganizationName().trim())
                .businessStructure(request.getBusinessStructure().trim())
                .licensePermitNumber(request.getCompanyLicenseNumber().trim())
                .businessPan(request.getBusinessPanNumber().trim().toUpperCase())
                .gstin(gstin)
                .registeredAddress(request.getRegisteredAddress().trim())
                .businessContactEmail(request.getBusinessContactEmail().trim().toLowerCase())
                .businessContactPhone(request.getBusinessContactPhone().trim())
                .termsAccepted(true)
                .termsAcceptedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .verificationStatus(user.getVerificationStatus())
                .verified(user.isVerified())
                .build());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid email or password"));
        }

        // Account status checks
        if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "message", "Your account has been temporarily suspended.",
                "status", "SUSPENDED"
            ));
        }

        if ("REJECTED".equalsIgnoreCase(user.getVerificationStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "message", "Unfortunately, your application has not been approved.",
                "status", "REJECTED"
            ));
        }

        if ("PENDING".equalsIgnoreCase(user.getStatus()) || "UNDER_REVIEW".equalsIgnoreCase(user.getVerificationStatus())) {
            return ResponseEntity.ok(AuthResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status("PENDING")
                .verificationStatus("UNDER_REVIEW")
                .verified(user.isVerified())
                .build());
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name(), user.getFullName());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .verificationStatus(user.getVerificationStatus())
                .verified(user.isVerified())
                .build());
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user != null) {
            String token = UUID.randomUUID().toString();
            user.setResetPasswordToken(token);
            user.setResetPasswordTokenExpiry(LocalDateTime.now().plusHours(1));
            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                "message", "Password reset instructions have been generated.",
                "resetToken", token
            ));
        }

        return ResponseEntity.ok(Map.of("message", "If your email is registered, you will receive password reset instructions."));
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
}
