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
import static org.mockito.Mockito.*;
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
        when(jwtUtil.generateApplicationToken(any(), anyString(), anyString(), anyString())).thenReturn("mockAppToken");
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
    void testLogin_ValidTraveller_Success() throws Exception {
        User traveller = User.builder()
                .id(101L)
                .email("traveller@traveleasy.com")
                .password("encodedPassword123")
                .fullName("John Traveller")
                .role(Role.ROLE_CUSTOMER)
                .status("ACTIVE")
                .verificationStatus("APPROVED")
                .verified(true)
                .build();

        when(userRepository.findByEmail("traveller@traveleasy.com")).thenReturn(Optional.of(traveller));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("traveller@traveleasy.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mockJwtToken"))
                .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.email").value("traveller@traveleasy.com"));
    }

    @Test
    void testLogin_PendingBusOperator_UnderReview_Rejected() throws Exception {
        User pendingOperator = User.builder()
                .id(102L)
                .email("pending.bus@traveleasy.com")
                .password("encodedPassword123")
                .fullName("Pending Bus Operator")
                .role(Role.ROLE_BUS_OPERATOR)
                .status("PENDING")
                .verificationStatus("UNDER_REVIEW")
                .verified(false)
                .build();

        when(userRepository.findByEmail("pending.bus@traveleasy.com")).thenReturn(Optional.of(pendingOperator));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("pending.bus@traveleasy.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.message").value("Your application is currently under review."))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void testLogin_PendingHotelPartner_UnderReview_Rejected() throws Exception {
        User pendingHotel = User.builder()
                .id(103L)
                .email("pending.hotel@traveleasy.com")
                .password("encodedPassword123")
                .fullName("Pending Hotel Manager")
                .role(Role.ROLE_HOTEL_MANAGER)
                .status("PENDING")
                .verificationStatus("UNDER_REVIEW")
                .verified(false)
                .build();

        when(userRepository.findByEmail("pending.hotel@traveleasy.com")).thenReturn(Optional.of(pendingHotel));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("pending.hotel@traveleasy.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.message").value("Your application is currently under review."))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void testLogin_BusOperator_RejectedVerification() throws Exception {
        User rejectedOperator = User.builder()
                .id(104L)
                .email("rejected.bus@traveleasy.com")
                .password("encodedPassword123")
                .fullName("Rejected Bus Operator")
                .role(Role.ROLE_BUS_OPERATOR)
                .status("PENDING")
                .verificationStatus("REJECTED")
                .verified(false)
                .build();

        when(userRepository.findByEmail("rejected.bus@traveleasy.com")).thenReturn(Optional.of(rejectedOperator));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("rejected.bus@traveleasy.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("Unfortunately, your application has not been approved."))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void testLogin_HotelPartner_RejectedVerification() throws Exception {
        User rejectedHotel = User.builder()
                .id(105L)
                .email("rejected.hotel@traveleasy.com")
                .password("encodedPassword123")
                .fullName("Rejected Hotel Manager")
                .role(Role.ROLE_HOTEL_MANAGER)
                .status("PENDING")
                .verificationStatus("REJECTED")
                .verified(false)
                .build();

        when(userRepository.findByEmail("rejected.hotel@traveleasy.com")).thenReturn(Optional.of(rejectedHotel));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("rejected.hotel@traveleasy.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("Unfortunately, your application has not been approved."))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void testLogin_SuspendedAccount() throws Exception {
        User suspendedUser = User.builder()
                .id(106L)
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
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.message").value("Your account has been temporarily suspended."))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void testLogin_DeactivatedAccount() throws Exception {
        User deactivatedUser = User.builder()
                .id(107L)
                .email("deactivated@traveleasy.com")
                .password("encodedPassword123")
                .fullName("Deactivated User")
                .role(Role.ROLE_CUSTOMER)
                .status("DEACTIVATED")
                .build();

        when(userRepository.findByEmail("deactivated@traveleasy.com")).thenReturn(Optional.of(deactivatedUser));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("deactivated@traveleasy.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"))
                .andExpect(jsonPath("$.message").value("Your account has been deactivated. Please contact support."))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void testLogin_ApprovedActiveBusOperator_Success() throws Exception {
        User approvedOperator = User.builder()
                .id(108L)
                .email("approved.bus@traveleasy.com")
                .password("encodedPassword123")
                .fullName("Approved Bus Operator")
                .role(Role.ROLE_BUS_OPERATOR)
                .status("ACTIVE")
                .verificationStatus("APPROVED")
                .verified(true)
                .build();

        when(userRepository.findByEmail("approved.bus@traveleasy.com")).thenReturn(Optional.of(approvedOperator));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("approved.bus@traveleasy.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mockJwtToken"))
                .andExpect(jsonPath("$.role").value("ROLE_BUS_OPERATOR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.verificationStatus").value("APPROVED"));
    }

    @Test
    void testLogin_ApprovedActiveHotelPartner_Success() throws Exception {
        User approvedHotel = User.builder()
                .id(109L)
                .email("approved.hotel@traveleasy.com")
                .password("encodedPassword123")
                .fullName("Approved Hotel Partner")
                .role(Role.ROLE_HOTEL_MANAGER)
                .status("ACTIVE")
                .verificationStatus("APPROVED")
                .verified(true)
                .build();

        when(userRepository.findByEmail("approved.hotel@traveleasy.com")).thenReturn(Optional.of(approvedHotel));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("approved.hotel@traveleasy.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mockJwtToken"))
                .andExpect(jsonPath("$.role").value("ROLE_HOTEL_MANAGER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.verificationStatus").value("APPROVED"));
    }

    @Test
    void testLogin_InvalidPassword_Rejected() throws Exception {
        User user = User.builder()
                .id(110L)
                .email("user@traveleasy.com")
                .password("encodedPassword123")
                .fullName("Valid User")
                .role(Role.ROLE_CUSTOMER)
                .status("ACTIVE")
                .verificationStatus("APPROVED")
                .verified(true)
                .build();

        when(userRepository.findByEmail("user@traveleasy.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword!", "encodedPassword123")).thenReturn(false);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("user@traveleasy.com");
        loginReq.setPassword("WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void testLogin_IneligibleAttempts_DoNotInvokeJwtGeneration() throws Exception {
        org.mockito.Mockito.clearInvocations(jwtUtil);

        User pendingOperator = User.builder()
                .id(111L)
                .email("ineligible.operator@traveleasy.com")
                .password("encodedPassword123")
                .fullName("Ineligible Operator")
                .role(Role.ROLE_BUS_OPERATOR)
                .status("PENDING")
                .verificationStatus("UNDER_REVIEW")
                .verified(false)
                .build();

        when(userRepository.findByEmail("ineligible.operator@traveleasy.com")).thenReturn(Optional.of(pendingOperator));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("ineligible.operator@traveleasy.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.token").doesNotExist());

        verify(jwtUtil, never()).generateToken(any(), anyString(), anyString(), anyString());
    }

    @Test
    void testLogin_IneligibleAttempts_DoNotCreateAuthenticatedSession() throws Exception {
        User suspendedUser = User.builder()
                .id(112L)
                .email("suspended.session@traveleasy.com")
                .password("encodedPassword123")
                .fullName("Suspended Session User")
                .role(Role.ROLE_HOTEL_MANAGER)
                .status("SUSPENDED")
                .build();

        when(userRepository.findByEmail("suspended.session@traveleasy.com")).thenReturn(Optional.of(suspendedUser));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("suspended.session@traveleasy.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void testLogin_RoleBasedDashboardRouting_Admin_Success() throws Exception {
        User adminUser = User.builder()
                .id(113L)
                .email("admin.valid@traveleasy.com")
                .password("encodedPassword123")
                .fullName("Platform Administrator")
                .role(Role.ROLE_ADMIN)
                .status("ACTIVE")
                .verified(true)
                .build();

        when(userRepository.findByEmail("admin.valid@traveleasy.com")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("AdminPassword123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("admin.valid@traveleasy.com");
        loginReq.setPassword("AdminPassword123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.token").value("mockJwtToken"));
    }

    @Test
    void testProtectedEndpoint_SuspendedUserToken_Rejected() throws Exception {
        User suspendedUser = User.builder()
                .id(114L)
                .email("suspended.token@traveleasy.com")
                .status("SUSPENDED")
                .build();

        when(jwtUtil.validateToken("suspendedToken")).thenReturn(true);
        when(jwtUtil.extractEmail("suspendedToken")).thenReturn("suspended.token@traveleasy.com");
        when(userRepository.findByEmail("suspended.token@traveleasy.com")).thenReturn(Optional.of(suspendedUser));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/application-details")
                .header("Authorization", "Bearer suspendedToken"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void testProtectedEndpoint_DeactivatedUserToken_Rejected() throws Exception {
        User deactivatedUser = User.builder()
                .id(115L)
                .email("deactivated.token@traveleasy.com")
                .status("DEACTIVATED")
                .build();

        when(jwtUtil.validateToken("deactivatedToken")).thenReturn(true);
        when(jwtUtil.extractEmail("deactivatedToken")).thenReturn("deactivated.token@traveleasy.com");
        when(userRepository.findByEmail("deactivated.token@traveleasy.com")).thenReturn(Optional.of(deactivatedUser));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/application-details")
                .header("Authorization", "Bearer deactivatedToken"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));
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

    @Test
    void testGetApplicationDetails_Success_BusOperator() throws Exception {
        User operator = User.builder()
                .id(50L)
                .email("operator@traveleasy.com")
                .firstName("John")
                .lastName("Doe")
                .fullName("John Doe")
                .phone("+919876543210")
                .role(Role.ROLE_BUS_OPERATOR)
                .status("PENDING")
                .verificationStatus("UNDER_REVIEW")
                .representativeDesignation("Operations Manager")
                .legalOrganizationName("ABC Grand Pvt. Ltd.")
                .businessStructure("Private Limited")
                .licensePermitNumber("123456789012")
                .businessPan("ABCDE1234F")
                .gstin("22AAAAA0000A1Z5")
                .registeredAddress("No. 12, ABC Plaza, MG Road, Vijayawada, Andhra Pradesh - 520001, India")
                .businessContactEmail("contact@abcgrand.com")
                .businessContactPhone("+918012345678")
                .createdAt(LocalDateTime.of(2026, 10, 14, 9, 45))
                .build();

        when(jwtUtil.validateToken("validPartnerToken")).thenReturn(true);
        when(jwtUtil.extractEmail("validPartnerToken")).thenReturn("operator@traveleasy.com");
        when(userRepository.findByEmail("operator@traveleasy.com")).thenReturn(Optional.of(operator));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/application-details")
                .header("Authorization", "Bearer validPartnerToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value("ABC Grand Pvt. Ltd."))
                .andExpect(jsonPath("$.applicationType").value("New Bus Service Registration"))
                .andExpect(jsonPath("$.applicationStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.representativeDesignation").value("Operations Manager"))
                .andExpect(jsonPath("$.licensePermitNumber").value("123456789012"))
                .andExpect(jsonPath("$.businessPanNumber").value("ABCDE1234F"))
                .andExpect(jsonPath("$.gstinNumber").value("22AAAAA0000A1Z5"));
    }

    @Test
    void testGetApplicationDetails_Success_HotelPartner() throws Exception {
        User hotelUser = User.builder()
                .id(51L)
                .email("hotel@traveleasy.com")
                .firstName("Sarah")
                .lastName("Connor")
                .fullName("Sarah Connor")
                .phone("+919876543299")
                .role(Role.ROLE_HOTEL_MANAGER)
                .status("PENDING")
                .verificationStatus("UNDER_REVIEW")
                .representativeDesignation("General Manager")
                .legalOrganizationName("Grand Palm Resort Ltd.")
                .businessStructure("Private Limited")
                .licensePermitNumber("HOTEL-998877")
                .businessPan("XYZDE1234F")
                .registeredAddress("45 Beach Road, Goa - 403001, India")
                .businessContactEmail("info@grandpalm.com")
                .businessContactPhone("+918099887766")
                .createdAt(LocalDateTime.of(2026, 10, 14, 10, 30))
                .build();

        when(jwtUtil.validateToken("validHotelToken")).thenReturn(true);
        when(jwtUtil.extractEmail("validHotelToken")).thenReturn("hotel@traveleasy.com");
        when(userRepository.findByEmail("hotel@traveleasy.com")).thenReturn(Optional.of(hotelUser));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/application-details")
                .header("Authorization", "Bearer validHotelToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value("Grand Palm Resort Ltd."))
                .andExpect(jsonPath("$.applicationType").value("New Hotel Partner Registration"))
                .andExpect(jsonPath("$.applicationStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.representativeDesignation").value("General Manager"))
                .andExpect(jsonPath("$.licensePermitNumber").value("HOTEL-998877"));
    }

    @Test
    void testGetApplicationDetails_Unauthorized() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/application-details"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetApplicationDetails_WithXApplicationToken_Success() throws Exception {
        User operator = User.builder()
                .id(21L)
                .email("scoped.operator@traveleasy.com")
                .firstName("John")
                .lastName("Doe")
                .fullName("John Doe")
                .phone("+919876543210")
                .role(Role.ROLE_BUS_OPERATOR)
                .status("PENDING")
                .verificationStatus("UNDER_REVIEW")
                .representativeDesignation("Operations Manager")
                .legalOrganizationName("ABC Grand Pvt. Ltd.")
                .businessStructure("Private Limited")
                .licensePermitNumber("123456789012")
                .businessPan("ABCDE1234F")
                .gstin("22AAAAA0000A1Z5")
                .registeredAddress("No. 12, ABC Plaza, MG Road, Vijayawada, Andhra Pradesh - 520001, India")
                .businessContactEmail("contact@abcgrand.com")
                .businessContactPhone("+918012345678")
                .createdAt(LocalDateTime.of(2026, 10, 14, 9, 45))
                .build();

        when(jwtUtil.validateToken("scopedAppToken")).thenReturn(true);
        when(jwtUtil.extractEmail("scopedAppToken")).thenReturn("scoped.operator@traveleasy.com");
        when(userRepository.findByEmail("scoped.operator@traveleasy.com")).thenReturn(Optional.of(operator));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/application-details")
                .header("X-Application-Token", "scopedAppToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value("ABC Grand Pvt. Ltd."))
                .andExpect(jsonPath("$.applicationType").value("New Bus Service Registration"))
                .andExpect(jsonPath("$.applicationStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.licensePermitNumber").value("123456789012"));
    }

    @Test
    void testRegisterBusOperator_NoLoginToken_IssuesAppToken() throws Exception {
        BusOperatorRegisterRequest req = new BusOperatorRegisterRequest();
        req.setFirstName("Alex");
        req.setLastName("Bus");
        req.setEmail("alex.bus@example.com");
        req.setPhone("+919876543295");
        req.setPassword("Password123!");
        req.setConfirmPassword("Password123!");
        req.setRepresentativeDesignation("OPERATIONS_MANAGER");
        req.setLegalOrganizationName("Express Travels");
        req.setBusinessStructure("Private Limited");
        req.setPassengerTransportPermitNumber("123456789012");
        req.setBusinessPanNumber("ABCDE1234F");
        req.setRegisteredAddress("123 City Road, Bengaluru");
        req.setBusinessContactEmail("alex@express.com");
        req.setBusinessContactPhone("+918012345678");
        req.setTermsAccepted(true);

        when(userRepository.existsByEmail("alex.bus@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("+919876543295")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(99L);
            return u;
        });

        mockMvc.perform(post("/api/auth/register/bus-operator")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_BUS_OPERATOR"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.verificationStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.applicationToken").value("mockAppToken"));
    }

    @Test
    void testLogin_PendingOperator_Returns403WithAppToken() throws Exception {
        User pendingOperator = User.builder()
                .id(102L)
                .email("pending.bus@traveleasy.com")
                .password("encodedPassword123")
                .fullName("Pending Operator")
                .role(Role.ROLE_BUS_OPERATOR)
                .status("PENDING")
                .verificationStatus("UNDER_REVIEW")
                .verified(false)
                .legalOrganizationName("Pending Bus Co.")
                .licensePermitNumber("123456789012")
                .build();

        when(userRepository.findByEmail("pending.bus@traveleasy.com")).thenReturn(Optional.of(pendingOperator));
        when(passwordEncoder.matches("Password123!", "encodedPassword123")).thenReturn(true);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("pending.bus@traveleasy.com");
        loginReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.message").value("Your application is currently under review."))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.applicationToken").value("mockAppToken"))
                .andExpect(jsonPath("$.applicationDetails.organizationName").value("Pending Bus Co."));
    }
}
