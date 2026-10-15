package com.traveleasy.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.auth.dto.*;
import com.traveleasy.auth.model.Role;
import com.traveleasy.auth.model.User;
import com.traveleasy.auth.repository.UserRepository;
import com.traveleasy.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword123");
        when(jwtUtil.generateToken(any(), anyString(), anyString(), anyString())).thenReturn("mockJwtToken");
    }

    @Test
    void testRegisterTraveller_Success() throws Exception {
        TravellerRegisterRequest req = new TravellerRegisterRequest();
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setEmail("john.doe@example.com");
        req.setPhone("+919876543210");
        req.setPassword("Password123!");
        req.setConfirmPassword("Password123!");
        req.setTermsAccepted(true);

        when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("+919876543210")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        mockMvc.perform(post("/api/auth/register/traveller")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.token").value("mockJwtToken"));
    }

    @Test
    void testRegisterTraveller_PasswordMismatch() throws Exception {
        TravellerRegisterRequest req = new TravellerRegisterRequest();
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setEmail("john.mismatch@example.com");
        req.setPhone("+919876543211");
        req.setPassword("Password123!");
        req.setConfirmPassword("DifferentPassword123!");
        req.setTermsAccepted(true);

        mockMvc.perform(post("/api/auth/register/traveller")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password and Confirm Password do not match!"));
    }

    @Test
    void testRegisterBusOperator_Success() throws Exception {
        BusOperatorRegisterRequest req = new BusOperatorRegisterRequest();
        req.setFirstName("Jane");
        req.setLastName("Smith");
        req.setEmail("jane.operator@bus.com");
        req.setPhone("+919876543212");
        req.setPassword("Password123!");
        req.setConfirmPassword("Password123!");
        req.setRepresentativeDesignation("Transport Manager");
        req.setLegalOrganizationName("Express Bus Travels Pvt Ltd");
        req.setBusinessStructure("Private Limited");
        req.setPassengerTransportPermitNumber("123456789012");
        req.setBusinessPanNumber("ABCDE1234F");
        req.setRegisteredAddress("123 Transport Hub, Bengaluru");
        req.setBusinessContactEmail("contact@bus.com");
        req.setBusinessContactPhone("+918012345678");
        req.setTermsAccepted(true);

        when(userRepository.existsByEmail("jane.operator@bus.com")).thenReturn(false);
        when(userRepository.existsByPhone("+919876543212")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });

        mockMvc.perform(post("/api/auth/register/bus-operator")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_BUS_OPERATOR"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.verificationStatus").value("UNDER_REVIEW"));
    }

    @Test
    void testRegisterBusOperator_InvalidPermitNumber() throws Exception {
        BusOperatorRegisterRequest req = new BusOperatorRegisterRequest();
        req.setFirstName("Jane");
        req.setLastName("Smith");
        req.setEmail("jane.invalid@bus.com");
        req.setPhone("+919876543213");
        req.setPassword("Password123!");
        req.setConfirmPassword("Password123!");
        req.setRepresentativeDesignation("Transport Manager");
        req.setLegalOrganizationName("Express Bus Travels");
        req.setBusinessStructure("Proprietorship");
        req.setPassengerTransportPermitNumber("12345"); // Invalid length
        req.setBusinessPanNumber("ABCDE1234F");
        req.setRegisteredAddress("123 Address");
        req.setBusinessContactEmail("contact@bus.com");
        req.setBusinessContactPhone("+918012345678");
        req.setTermsAccepted(true);

        mockMvc.perform(post("/api/auth/register/bus-operator")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRegisterBusOperator_EnumDesignation_Success() throws Exception {
        BusOperatorRegisterRequest req = new BusOperatorRegisterRequest();
        req.setFirstName("Jane");
        req.setLastName("Smith");
        req.setEmail("jane.owner@bus.com");
        req.setPhone("+919876543220");
        req.setPassword("Password123!");
        req.setConfirmPassword("Password123!");
        req.setRepresentativeDesignation("OWNER_PROPRIETOR");
        req.setLegalOrganizationName("Express Bus Travels Pvt Ltd");
        req.setBusinessStructure("Private Limited");
        req.setPassengerTransportPermitNumber("123456789012");
        req.setBusinessPanNumber("ABCDE1234F");
        req.setRegisteredAddress("Flat 101, MG Road, Bengaluru, Karnataka, India, 560001");
        req.setBusinessContactEmail("owner@bus.com");
        req.setBusinessContactPhone("+918012345678");
        req.setTermsAccepted(true);

        when(userRepository.existsByEmail("jane.owner@bus.com")).thenReturn(false);
        when(userRepository.existsByPhone("+919876543220")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(21L);
            return u;
        });

        mockMvc.perform(post("/api/auth/register/bus-operator")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_BUS_OPERATOR"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void testRegisterBusOperator_OtherDesignation_Success() throws Exception {
        BusOperatorRegisterRequest req = new BusOperatorRegisterRequest();
        req.setFirstName("Jane");
        req.setLastName("Smith");
        req.setEmail("jane.custom@bus.com");
        req.setPhone("+919876543221");
        req.setPassword("Password123!");
        req.setConfirmPassword("Password123!");
        req.setRepresentativeDesignation("OTHER");
        req.setCustomDesignation("Fleet Operations Head");
        req.setLegalOrganizationName("Express Bus Travels Pvt Ltd");
        req.setBusinessStructure("Private Limited");
        req.setPassengerTransportPermitNumber("123456789012");
        req.setBusinessPanNumber("ABCDE1234F");
        req.setRegisteredAddress("Flat 101, MG Road, Near Metro, Bengaluru, Karnataka, India, 560001");
        req.setBusinessContactEmail("fleet@bus.com");
        req.setBusinessContactPhone("+918012345678");
        req.setTermsAccepted(true);

        when(userRepository.existsByEmail("jane.custom@bus.com")).thenReturn(false);
        when(userRepository.existsByPhone("+919876543221")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(22L);
            return u;
        });

        mockMvc.perform(post("/api/auth/register/bus-operator")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_BUS_OPERATOR"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void testRegisterBusOperator_OtherDesignation_MissingCustom_BadRequest() throws Exception {
        BusOperatorRegisterRequest req = new BusOperatorRegisterRequest();
        req.setFirstName("Jane");
        req.setLastName("Smith");
        req.setEmail("jane.nocustom@bus.com");
        req.setPhone("+919876543222");
        req.setPassword("Password123!");
        req.setConfirmPassword("Password123!");
        req.setRepresentativeDesignation("OTHER");
        req.setCustomDesignation(""); // Missing custom designation
        req.setLegalOrganizationName("Express Bus Travels");
        req.setBusinessStructure("Proprietorship");
        req.setPassengerTransportPermitNumber("123456789012");
        req.setBusinessPanNumber("ABCDE1234F");
        req.setRegisteredAddress("123 Address");
        req.setBusinessContactEmail("contact@bus.com");
        req.setBusinessContactPhone("+918012345678");
        req.setTermsAccepted(true);

        mockMvc.perform(post("/api/auth/register/bus-operator")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please specify a valid custom designation between 2 and 100 characters"));
    }

    @Test
    void testRegisterBusOperator_InvalidDesignation_BadRequest() throws Exception {
        BusOperatorRegisterRequest req = new BusOperatorRegisterRequest();
        req.setFirstName("Jane");
        req.setLastName("Smith");
        req.setEmail("jane.badrole@bus.com");
        req.setPhone("+919876543223");
        req.setPassword("Password123!");
        req.setConfirmPassword("Password123!");
        req.setRepresentativeDesignation("INVALID_DESIGNATION");
        req.setLegalOrganizationName("Express Bus Travels");
        req.setBusinessStructure("Proprietorship");
        req.setPassengerTransportPermitNumber("123456789012");
        req.setBusinessPanNumber("ABCDE1234F");
        req.setRegisteredAddress("123 Address");
        req.setBusinessContactEmail("contact@bus.com");
        req.setBusinessContactPhone("+918012345678");
        req.setTermsAccepted(true);

        mockMvc.perform(post("/api/auth/register/bus-operator")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid representative designation selected"));
    }

    @Test
    void testRegisterHotelPartner_Success() throws Exception {
        HotelPartnerRegisterRequest req = new HotelPartnerRegisterRequest();
        req.setFirstName("Robert");
        req.setLastName("Brown");
        req.setEmail("robert.mgr@hotel.com");
        req.setPhone("+919876543214");
        req.setPassword("Password123!");
        req.setConfirmPassword("Password123!");
        req.setRepresentativeDesignation("General Manager");
        req.setLegalOrganizationName("Grand Regency Hotels");
        req.setBusinessStructure("LLP");
        req.setCompanyLicenseNumber("987654321098");
        req.setBusinessPanNumber("XYZAB5678G");
        req.setRegisteredAddress("45 Beach Road, Goa");
        req.setBusinessContactEmail("reservations@hotel.com");
        req.setBusinessContactPhone("+918321234567");
        req.setTermsAccepted(true);

        when(userRepository.existsByEmail("robert.mgr@hotel.com")).thenReturn(false);
        when(userRepository.existsByPhone("+919876543214")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(3L);
            return u;
        });

        mockMvc.perform(post("/api/auth/register/hotel-partner")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_HOTEL_MANAGER"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.verificationStatus").value("UNDER_REVIEW"));
    }

    @Test
    void testRegisterHotelPartner_OtherDesignation_RequiresCustomDesignation() throws Exception {
        HotelPartnerRegisterRequest req = new HotelPartnerRegisterRequest();
        req.setFirstName("Robert");
        req.setLastName("Brown");
        req.setEmail("robert.other@hotel.com");
        req.setPhone("+919876543290");
        req.setPassword("Password123!");
        req.setConfirmPassword("Password123!");
        req.setRepresentativeDesignation("OTHER");
        req.setCustomDesignation(""); // Missing custom designation
        req.setLegalOrganizationName("Grand Regency Hotels");
        req.setBusinessStructure("LLP");
        req.setCompanyLicenseNumber("987654321098");
        req.setBusinessPanNumber("XYZAB5678G");
        req.setRegisteredAddress("45 Beach Road, Goa");
        req.setBusinessContactEmail("reservations@hotel.com");
        req.setBusinessContactPhone("+918321234567");
        req.setTermsAccepted(true);

        mockMvc.perform(post("/api/auth/register/hotel-partner")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please specify a valid custom designation between 2 and 100 characters"));
    }

    @Test
    void testRegisterHotelPartner_InvalidDesignation_BadRequest() throws Exception {
        HotelPartnerRegisterRequest req = new HotelPartnerRegisterRequest();
        req.setFirstName("Robert");
        req.setLastName("Brown");
        req.setEmail("robert.invalid@hotel.com");
        req.setPhone("+919876543291");
        req.setPassword("Password123!");
        req.setConfirmPassword("Password123!");
        req.setRepresentativeDesignation("ASTRONAUT");
        req.setLegalOrganizationName("Grand Regency Hotels");
        req.setBusinessStructure("LLP");
        req.setCompanyLicenseNumber("987654321098");
        req.setBusinessPanNumber("XYZAB5678G");
        req.setRegisteredAddress("45 Beach Road, Goa");
        req.setBusinessContactEmail("reservations@hotel.com");
        req.setBusinessContactPhone("+918321234567");
        req.setTermsAccepted(true);

        mockMvc.perform(post("/api/auth/register/hotel-partner")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid representative designation selected"));
    }

    @Test
    void testRegisterHotelPartner_WithCustomOtherDesignation_Success() throws Exception {
        HotelPartnerRegisterRequest req = new HotelPartnerRegisterRequest();
        req.setFirstName("Robert");
        req.setLastName("Brown");
        req.setEmail("robert.custom@hotel.com");
        req.setPhone("+919876543292");
        req.setPassword("Password123!");
        req.setConfirmPassword("Password123!");
        req.setRepresentativeDesignation("OTHER");
        req.setCustomDesignation("Chief Hospitality Officer");
        req.setLegalOrganizationName("Grand Regency Hotels");
        req.setBusinessStructure("LLP");
        req.setCompanyLicenseNumber("987654321098");
        req.setBusinessPanNumber("XYZAB5678G");
        req.setRegisteredAddress("Suite 101, Beach Resort, Goa, 403001");
        req.setFlatBuildingOffice("Suite 101");
        req.setStreetRoad("Beach Road");
        req.setCityLocality("Panaji");
        req.setState("Goa");
        req.setCountry("India");
        req.setPinCode("403001");
        req.setBusinessContactEmail("reservations@hotel.com");
        req.setBusinessContactPhone("+918321234567");
        req.setTermsAccepted(true);

        when(userRepository.existsByEmail("robert.custom@hotel.com")).thenReturn(false);
        when(userRepository.existsByPhone("+919876543292")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(4L);
            return u;
        });

        mockMvc.perform(post("/api/auth/register/hotel-partner")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_HOTEL_MANAGER"));
    }

    @Test
    void testLogin_SuspendedAccount() throws Exception {
        User suspendedUser = User.builder()
                .id(10L)
                .email("suspended@example.com")
                .password("encodedPassword123")
                .fullName("Suspended User")
                .role(Role.ROLE_CUSTOMER)
                .status("SUSPENDED")
                .build();

        when(userRepository.findByEmail("suspended@example.com")).thenReturn(Optional.of(suspendedUser));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("suspended@example.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void testLogin_RejectedAccount() throws Exception {
        User rejectedUser = User.builder()
                .id(11L)
                .email("rejected@example.com")
                .password("encodedPassword123")
                .fullName("Rejected Operator")
                .role(Role.ROLE_BUS_OPERATOR)
                .status("PENDING")
                .verificationStatus("REJECTED")
                .build();

        when(userRepository.findByEmail("rejected@example.com")).thenReturn(Optional.of(rejectedUser));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("rejected@example.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void testForgotPasswordAndResetPassword() throws Exception {
        User user = User.builder()
                .id(20L)
                .email("recovery@example.com")
                .password("oldPasswordHash")
                .fullName("Recovery User")
                .role(Role.ROLE_CUSTOMER)
                .build();

        when(userRepository.findByEmail("recovery@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByResetPasswordToken("validToken123")).thenReturn(Optional.of(user));
        user.setResetPasswordTokenExpiry(LocalDateTime.now().plusHours(1));

        // Forgot password request
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest();
        forgotReq.setEmail("recovery@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk());

        // Reset password request
        ResetPasswordRequest resetReq = new ResetPasswordRequest();
        resetReq.setToken("validToken123");
        resetReq.setNewPassword("NewPassword123!");
        resetReq.setConfirmNewPassword("NewPassword123!");

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully. You can now login with your new password."));
    }
}
