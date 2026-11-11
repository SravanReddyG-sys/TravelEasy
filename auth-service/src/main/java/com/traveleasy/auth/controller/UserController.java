package com.traveleasy.auth.controller;

import com.traveleasy.auth.dto.PendingProviderSummaryResponse;
import com.traveleasy.auth.dto.ProviderDetailsResponse;
import com.traveleasy.auth.dto.UserStatsResponse;
import com.traveleasy.auth.model.Role;
import com.traveleasy.auth.model.User;
import com.traveleasy.auth.repository.UserRepository;
import com.traveleasy.auth.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    /**
     * Helper to validate administrator credentials from Authorization Bearer token.
     * Returns null if authorized as ROLE_ADMIN, or an appropriate 401/403 ResponseEntity.
     */
    private ResponseEntity<?> validateAdminAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authorization Bearer token is required"));
        }
        String token = authHeader.substring(7).trim();
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }
        try {
            Claims claims = jwtUtil.extractAllClaims(token);
            Object role = claims.get("role");
            String roleStr = role != null ? role.toString().trim() : "";
            if (!"ROLE_ADMIN".equalsIgnoreCase(roleStr) && !"ADMIN".equalsIgnoreCase(roleStr)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Administrator role required"));
            }
        } catch (Exception ex) {
            log.warn("Token parsing error during admin authorization: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Malformed authorization token"));
        }
        return null;
    }

    /**
     * Administrative endpoint providing lightweight aggregate user statistics.
     * Uses database-level count queries and excludes administrator accounts.
     * Requires ROLE_ADMIN.
     */
    @GetMapping("/admin/stats")
    public ResponseEntity<?> getUserStats(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }

        long travellers = userRepository.countByRole(Role.ROLE_CUSTOMER);
        long busOperators = userRepository.countByRole(Role.ROLE_BUS_OPERATOR);
        long hotelOperators = userRepository.countByRole(Role.ROLE_HOTEL_MANAGER);
        long totalUsers = travellers + busOperators + hotelOperators;

        UserStatsResponse stats = UserStatsResponse.builder()
                .totalUsers(totalUsers)
                .travellers(travellers)
                .busOperators(busOperators)
                .hotelOperators(hotelOperators)
                .build();

        return ResponseEntity.ok(stats);
    }

    /**
     * Administrative endpoint providing paginated pending provider applications
     * from both bus operators and hotel operators where verificationStatus = 'UNDER_REVIEW'.
     * Ordered newest first (createdAt DESC).
     * Requires ROLE_ADMIN.
     */
    @GetMapping("/admin/pending-providers")
    public ResponseEntity<?> getPendingProviders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }

        int pageNum = Math.max(0, page);
        int pageSize = Math.max(1, Math.min(100, size));
        Pageable pageable = PageRequest.of(pageNum, pageSize);

        Page<User> usersPage = userRepository.findByVerificationStatusAndRoleInOrderByCreatedAtDesc(
                "UNDER_REVIEW",
                List.of(Role.ROLE_BUS_OPERATOR, Role.ROLE_HOTEL_MANAGER),
                pageable
        );

        Page<PendingProviderSummaryResponse> responsePage = usersPage.map(u -> {
            String providerType = u.getRole() == Role.ROLE_BUS_OPERATOR ? "BUS_OPERATOR" : "HOTEL_OPERATOR";
            String orgName = u.getLegalOrganizationName();
            if (orgName == null || orgName.trim().isEmpty()) {
                orgName = u.getFullName();
            }
            return PendingProviderSummaryResponse.builder()
                    .id(u.getId())
                    .legalOrganizationName(orgName)
                    .providerType(providerType)
                    .submittedOn(u.getCreatedAt())
                    .verificationStatus(u.getVerificationStatus())
                    .build();
        });

        return ResponseEntity.ok(responsePage);
    }

    /**
     * Administrative endpoint to review full provider application details.
     * Excludes authentication secrets while exposing KYC and operational status fields.
     * Requires ROLE_ADMIN.
     */
    @GetMapping("/admin/providers/{id}")
    public ResponseEntity<?> getProviderDetailsForReview(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }

        return userRepository.findById(id).map(user -> {
            if (user.getRole() != Role.ROLE_BUS_OPERATOR && user.getRole() != Role.ROLE_HOTEL_MANAGER) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "User with ID " + id + " is not a registered service provider"));
            }

            String providerType = user.getRole() == Role.ROLE_BUS_OPERATOR ? "BUS_OPERATOR" : "HOTEL_OPERATOR";

            ProviderDetailsResponse response = ProviderDetailsResponse.builder()
                    .id(user.getId())
                    .legalOrganizationName(user.getLegalOrganizationName())
                    .representativeName(user.getFullName())
                    .representativeDesignation(user.getRepresentativeDesignation())
                    .providerType(providerType)
                    .role(user.getRole().name())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .businessStructure(user.getBusinessStructure())
                    .licensePermitNumber(user.getLicensePermitNumber())
                    .businessPan(user.getBusinessPan())
                    .gstin(user.getGstin())
                    .registeredAddress(user.getRegisteredAddress())
                    .businessContactEmail(user.getBusinessContactEmail())
                    .businessContactPhone(user.getBusinessContactPhone())
                    .status(user.getStatus())
                    .verificationStatus(user.getVerificationStatus())
                    .verified(user.isVerified())
                    .submittedOn(user.getCreatedAt())
                    .termsAccepted(user.isTermsAccepted())
                    .termsAcceptedAt(user.getTermsAcceptedAt())
                    .build();

            return ResponseEntity.ok(response);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Administrative endpoint to list all platform users. Requires ROLE_ADMIN.
     */
    @GetMapping
    public ResponseEntity<?> getAllUsers(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }
        return ResponseEntity.ok(userRepository.findAll());
    }

    /**
     * User profile lookup by ID.
     * Preserved as shared lookup for cross-service operator resolution (e.g. Bus Service).
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Administrative endpoint to retrieve users filtered by role. Requires ROLE_ADMIN.
     */
    @GetMapping("/role/{role}")
    public ResponseEntity<?> getUsersByRole(@PathVariable Role role,
                                            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }
        return ResponseEntity.ok(userRepository.findByRole(role));
    }

    /**
     * Administrative endpoint to approve or reject provider verification applications.
     * Requires ROLE_ADMIN.
     * Preserves independently maintained operational account statuses (e.g. SUSPENDED, DEACTIVATED).
     */
    @PutMapping("/{id}/verify")
    public ResponseEntity<?> verifyProvider(@PathVariable Long id,
                                            @RequestParam boolean verified,
                                            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }

        return userRepository.findById(id).map(user -> {
            user.setVerified(verified);
            if (verified) {
                user.setVerificationStatus("APPROVED");
                // Only activate pending/null accounts; do not overwrite SUSPENDED or DEACTIVATED statuses
                if (user.getStatus() == null || "PENDING".equalsIgnoreCase(user.getStatus())) {
                    user.setStatus("ACTIVE");
                }
            } else {
                user.setVerificationStatus("REJECTED");
            }
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "User verification status updated to " + verified, "user", user));
        }).orElse(ResponseEntity.notFound().build());
    }
}
