package com.traveleasy.auth.controller;

import com.traveleasy.auth.model.Role;
import com.traveleasy.auth.model.User;
import com.traveleasy.auth.repository.UserRepository;
import com.traveleasy.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    private String adminToken;
    private String customerToken;
    private String busOperatorToken;
    private String hotelManagerToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtUtil.generateToken(1L, "admin@traveleasy.com", "ROLE_ADMIN", "Admin User");
        customerToken = jwtUtil.generateToken(101L, "customer@traveleasy.com", "ROLE_CUSTOMER", "Customer User");
        busOperatorToken = jwtUtil.generateToken(501L, "operator@traveleasy.com", "ROLE_BUS_OPERATOR", "Bus Operator");
        hotelManagerToken = jwtUtil.generateToken(601L, "manager@traveleasy.com", "ROLE_HOTEL_MANAGER", "Hotel Manager");
    }

    // ==========================================
    // GET /api/users
    // ==========================================

    @Test
    @DisplayName("GET /api/users - Without Token returns 401 Unauthorized")
    void getAllUsers_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authorization Bearer token is required"));
    }

    @Test
    @DisplayName("GET /api/users - Invalid Token returns 401 Unauthorized")
    void getAllUsers_InvalidToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired authorization token"));
    }

    @Test
    @DisplayName("GET /api/users - As ROLE_CUSTOMER returns 403 Forbidden")
    void getAllUsers_AsCustomer_Returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("GET /api/users - As ROLE_BUS_OPERATOR returns 403 Forbidden")
    void getAllUsers_AsBusOperator_Returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + busOperatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("GET /api/users - As ROLE_HOTEL_MANAGER returns 403 Forbidden")
    void getAllUsers_AsHotelManager_Returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + hotelManagerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("GET /api/users - As ROLE_ADMIN returns 200 OK")
    void getAllUsers_AsAdmin_Returns200() throws Exception {
        User u = User.builder().id(101L).email("user@test.com").fullName("Test User").role(Role.ROLE_CUSTOMER).build();
        when(userRepository.findAll()).thenReturn(List.of(u));

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("user@test.com"));
    }

    // ==========================================
    // GET /api/users/role/{role}
    // ==========================================

    @Test
    @DisplayName("GET /api/users/role/{role} - Without Token returns 401 Unauthorized")
    void getUsersByRole_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/role/ROLE_BUS_OPERATOR"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/users/role/{role} - As ROLE_CUSTOMER returns 403 Forbidden")
    void getUsersByRole_AsCustomer_Returns403() throws Exception {
        mockMvc.perform(get("/api/users/role/ROLE_BUS_OPERATOR")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/role/{role} - As ROLE_ADMIN returns 200 OK")
    void getUsersByRole_AsAdmin_Returns200() throws Exception {
        User op = User.builder().id(501L).email("op@test.com").fullName("Operator").role(Role.ROLE_BUS_OPERATOR).build();
        when(userRepository.findByRole(Role.ROLE_BUS_OPERATOR)).thenReturn(List.of(op));

        mockMvc.perform(get("/api/users/role/ROLE_BUS_OPERATOR")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(501));
    }

    // ==========================================
    // PUT /api/users/{id}/verify
    // ==========================================

    @Test
    @DisplayName("PUT /api/users/{id}/verify - Without Token returns 401 Unauthorized")
    void verifyProvider_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(put("/api/users/501/verify?verified=true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/users/{id}/verify - As Customer returns 403 Forbidden")
    void verifyProvider_AsCustomer_Returns403() throws Exception {
        mockMvc.perform(put("/api/users/501/verify?verified=true")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/users/{id}/verify - As Admin approves pending provider, sets ACTIVE and APPROVED")
    void verifyProvider_AsAdmin_ApprovePending_SetsActive() throws Exception {
        User pending = User.builder()
                .id(501L)
                .email("pending@test.com")
                .role(Role.ROLE_BUS_OPERATOR)
                .status("PENDING")
                .verificationStatus("UNDER_REVIEW")
                .verified(false)
                .build();

        when(userRepository.findById(501L)).thenReturn(Optional.of(pending));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/users/501/verify?verified=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.verified").value(true))
                .andExpect(jsonPath("$.user.verificationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("PUT /api/users/{id}/verify - As Admin approves SUSPENDED provider, preserves SUSPENDED operational status")
    void verifyProvider_AsAdmin_PreservesSuspendedStatus() throws Exception {
        User suspended = User.builder()
                .id(501L)
                .email("suspended@test.com")
                .role(Role.ROLE_BUS_OPERATOR)
                .status("SUSPENDED")
                .verificationStatus("UNDER_REVIEW")
                .verified(false)
                .build();

        when(userRepository.findById(501L)).thenReturn(Optional.of(suspended));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/users/501/verify?verified=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.verified").value(true))
                .andExpect(jsonPath("$.user.verificationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.user.status").value("SUSPENDED")); // Preserved!
    }

    // ==========================================
    // GET /api/users/{id} - Preserved Shared Lookup
    // ==========================================

    @Test
    @DisplayName("GET /api/users/{id} - Unauthenticated inter-service lookup is preserved (returns 200)")
    void getUserById_Unauthenticated_Returns200() throws Exception {
        User op = User.builder().id(501L).email("op@test.com").fullName("Operator").role(Role.ROLE_BUS_OPERATOR).build();
        when(userRepository.findById(501L)).thenReturn(Optional.of(op));

        mockMvc.perform(get("/api/users/501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(501));
    }

    // ==========================================
    // WP-02: API 1: GET /api/users/admin/stats
    // ==========================================

    @Test
    @DisplayName("WP-02: GET /api/users/admin/stats - Without Token returns 401")
    void getUserStats_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/admin/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authorization Bearer token is required"));
    }

    @Test
    @DisplayName("WP-02: GET /api/users/admin/stats - As Customer returns 403 Forbidden")
    void getUserStats_AsCustomer_Returns403() throws Exception {
        mockMvc.perform(get("/api/users/admin/stats")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("WP-02: GET /api/users/admin/stats - As Bus Operator returns 403 Forbidden")
    void getUserStats_AsBusOperator_Returns403() throws Exception {
        mockMvc.perform(get("/api/users/admin/stats")
                        .header("Authorization", "Bearer " + busOperatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WP-02: GET /api/users/admin/stats - As Admin returns correct aggregate counts")
    void getUserStats_AsAdmin_ReturnsCounts() throws Exception {
        when(userRepository.countByRole(Role.ROLE_CUSTOMER)).thenReturn(24500L);
        when(userRepository.countByRole(Role.ROLE_BUS_OPERATOR)).thenReturn(260L);
        when(userRepository.countByRole(Role.ROLE_HOTEL_MANAGER)).thenReturn(132L);

        mockMvc.perform(get("/api/users/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(24892))
                .andExpect(jsonPath("$.travellers").value(24500))
                .andExpect(jsonPath("$.busOperators").value(260))
                .andExpect(jsonPath("$.hotelOperators").value(132));

        verify(userRepository, never()).countByRole(Role.ROLE_ADMIN);
    }

    @Test
    @DisplayName("WP-02: GET /api/users/admin/stats - Counts all operational statuses and strictly excludes ROLE_ADMIN")
    void getUserStats_IncludesAllStatusesAndExcludesAdmin() throws Exception {
        // countByRole queries count all users with that role regardless of status (ACTIVE, PENDING, SUSPENDED, DEACTIVATED)
        when(userRepository.countByRole(Role.ROLE_CUSTOMER)).thenReturn(150L);
        when(userRepository.countByRole(Role.ROLE_BUS_OPERATOR)).thenReturn(20L);
        when(userRepository.countByRole(Role.ROLE_HOTEL_MANAGER)).thenReturn(10L);

        mockMvc.perform(get("/api/users/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(180))
                .andExpect(jsonPath("$.travellers").value(150))
                .andExpect(jsonPath("$.busOperators").value(20))
                .andExpect(jsonPath("$.hotelOperators").value(10));

        verify(userRepository, never()).countByRole(Role.ROLE_ADMIN);
    }

    @Test
    @DisplayName("WP-02: GET /api/users/admin/stats - Empty database returns zero counts")
    void getUserStats_EmptyDb_ReturnsZeroCounts() throws Exception {
        when(userRepository.countByRole(Role.ROLE_CUSTOMER)).thenReturn(0L);
        when(userRepository.countByRole(Role.ROLE_BUS_OPERATOR)).thenReturn(0L);
        when(userRepository.countByRole(Role.ROLE_HOTEL_MANAGER)).thenReturn(0L);

        mockMvc.perform(get("/api/users/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(0))
                .andExpect(jsonPath("$.travellers").value(0))
                .andExpect(jsonPath("$.busOperators").value(0))
                .andExpect(jsonPath("$.hotelOperators").value(0));
    }

    // ==========================================
    // WP-02: API 2: GET /api/users/admin/pending-providers
    // ==========================================

    @Test
    @DisplayName("WP-02: GET /api/users/admin/pending-providers - Without Token returns 401")
    void getPendingProviders_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/admin/pending-providers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("WP-02: GET /api/users/admin/pending-providers - As Customer returns 403")
    void getPendingProviders_AsCustomer_Returns403() throws Exception {
        mockMvc.perform(get("/api/users/admin/pending-providers")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WP-02: GET /api/users/admin/pending-providers - As Admin returns paginated list of pending providers")
    void getPendingProviders_AsAdmin_ReturnsPaginatedList() throws Exception {
        User busOp = User.builder()
                .id(501L)
                .legalOrganizationName("Sri Venkateswara Travels")
                .role(Role.ROLE_BUS_OPERATOR)
                .fullName("Srinivas Rao")
                .email("svt@test.com")
                .phone("+91 98480 12345")
                .verificationStatus("UNDER_REVIEW")
                .createdAt(LocalDateTime.of(2026, 10, 14, 9, 30))
                .build();

        User hotelOp = User.builder()
                .id(601L)
                .legalOrganizationName("Coastal Stay Hotels")
                .role(Role.ROLE_HOTEL_MANAGER)
                .fullName("Ramesh K")
                .email("coastal@test.com")
                .phone("+91 98480 54321")
                .verificationStatus("UNDER_REVIEW")
                .createdAt(LocalDateTime.of(2026, 10, 13, 10, 15))
                .build();

        PageImpl<User> pageResult = new PageImpl<>(List.of(busOp, hotelOp), PageRequest.of(0, 10), 2);
        when(userRepository.findByVerificationStatusAndRoleInOrderByCreatedAtDesc(
                eq("UNDER_REVIEW"),
                any(Collection.class),
                any(Pageable.class)
        )).thenReturn(pageResult);

        mockMvc.perform(get("/api/users/admin/pending-providers?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(501))
                .andExpect(jsonPath("$.content[0].legalOrganizationName").value("Sri Venkateswara Travels"))
                .andExpect(jsonPath("$.content[0].providerType").value("BUS_OPERATOR"))
                .andExpect(jsonPath("$.content[0].verificationStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.content[0].submittedOn").exists())
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].phone").doesNotExist())
                .andExpect(jsonPath("$.content[0].representativeName").doesNotExist())
                .andExpect(jsonPath("$.content[0].representativeDesignation").doesNotExist())
                .andExpect(jsonPath("$.content[1].id").value(601))
                .andExpect(jsonPath("$.content[1].legalOrganizationName").value("Coastal Stay Hotels"))
                .andExpect(jsonPath("$.content[1].providerType").value("HOTEL_OPERATOR"))
                .andExpect(jsonPath("$.content[1].verificationStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.content[1].email").doesNotExist())
                .andExpect(jsonPath("$.content[1].phone").doesNotExist());
    }

    // ==========================================
    // WP-02: API 3: GET /api/users/admin/providers/{id}
    // ==========================================

    @Test
    @DisplayName("WP-02: GET /api/users/admin/providers/{id} - Without Token returns 401")
    void getProviderDetails_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/admin/providers/501"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("WP-02: GET /api/users/admin/providers/{id} - As Customer returns 403")
    void getProviderDetails_AsCustomer_Returns403() throws Exception {
        mockMvc.perform(get("/api/users/admin/providers/501")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WP-02: GET /api/users/admin/providers/{id} - Non-existent ID returns 404")
    void getProviderDetails_NotFound_Returns404() throws Exception {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/admin/providers/999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("WP-02: GET /api/users/admin/providers/{id} - On Customer account returns 400 Bad Request")
    void getProviderDetails_OnCustomer_Returns400() throws Exception {
        User cust = User.builder().id(101L).role(Role.ROLE_CUSTOMER).email("c@test.com").build();
        when(userRepository.findById(101L)).thenReturn(Optional.of(cust));

        mockMvc.perform(get("/api/users/admin/providers/101")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User with ID 101 is not a registered service provider"));
    }

    @Test
    @DisplayName("WP-02: GET /api/users/admin/providers/{id} - As Admin returns full KYC details without password")
    void getProviderDetails_AsAdmin_ReturnsKycDetails() throws Exception {
        User busOp = User.builder()
                .id(501L)
                .legalOrganizationName("Sri Venkateswara Travels")
                .fullName("Srinivas Rao")
                .representativeDesignation("Transport Manager")
                .role(Role.ROLE_BUS_OPERATOR)
                .email("svt@test.com")
                .phone("+91 98480 12345")
                .businessStructure("Private Limited")
                .licensePermitNumber("AP-RTO-9981")
                .businessPan("ABCDE1234F")
                .gstin("37ABCDE1234F1Z5")
                .registeredAddress("1-2-3, MG Road, Vijayawada")
                .businessContactEmail("contact@svt.com")
                .businessContactPhone("+91 98480 99999")
                .status("PENDING")
                .verificationStatus("UNDER_REVIEW")
                .verified(false)
                .createdAt(LocalDateTime.of(2026, 10, 14, 9, 30))
                .termsAccepted(true)
                .termsAcceptedAt(LocalDateTime.of(2026, 10, 14, 9, 30))
                .build();

        when(userRepository.findById(501L)).thenReturn(Optional.of(busOp));

        mockMvc.perform(get("/api/users/admin/providers/501")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.legalOrganizationName").value("Sri Venkateswara Travels"))
                .andExpect(jsonPath("$.representativeName").value("Srinivas Rao"))
                .andExpect(jsonPath("$.representativeDesignation").value("Transport Manager"))
                .andExpect(jsonPath("$.providerType").value("BUS_OPERATOR"))
                .andExpect(jsonPath("$.businessPan").value("ABCDE1234F"))
                .andExpect(jsonPath("$.gstin").value("37ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.licensePermitNumber").value("AP-RTO-9981"))
                .andExpect(jsonPath("$.verificationStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
