package com.traveleasy.traveller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.traveller.client.BookingServiceClient;
import com.traveleasy.traveller.client.BookingSummaryDto;
import com.traveleasy.traveller.dto.AddFavouriteRequest;
import com.traveleasy.traveller.dto.CreateSupportTicketRequest;
import com.traveleasy.traveller.dto.UpdatePreferenceRequest;
import com.traveleasy.traveller.dto.UpdateProfileRequest;
import com.traveleasy.traveller.model.TravellerProfile;
import com.traveleasy.traveller.repository.SupportTicketRepository;
import com.traveleasy.traveller.repository.TravellerFavouriteRepository;
import com.traveleasy.traveller.repository.TravellerPreferenceRepository;
import com.traveleasy.traveller.repository.TravellerProfileRepository;
import com.traveleasy.traveller.security.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:travellertestdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=TravelEasySuperSecretKeyForJWTSigning12345678901234567890"
})
public class TravellerSecurityAndEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingServiceClient bookingServiceClient;

    @Autowired
    private TravellerProfileRepository profileRepository;

    @Autowired
    private TravellerPreferenceRepository preferenceRepository;

    @Autowired
    private TravellerFavouriteRepository favouriteRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    private final String SECRET = "TravelEasySuperSecretKeyForJWTSigning12345678901234567890";

    private String generateToken(Long userId, String email, String role, String fullName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("fullName", fullName);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    @BeforeEach
    void setup() {
        favouriteRepository.deleteAll();
        supportTicketRepository.deleteAll();
        preferenceRepository.deleteAll();
        profileRepository.deleteAll();
    }

    @Test
    void testPublicHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/traveller/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("traveller-service"));
    }

    @Test
    void testUnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/traveller/profile"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/traveller/dashboard"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/traveller/favourites"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testNonCustomerRoleForbidden() throws Exception {
        String operatorToken = generateToken(2L, "operator@traveleasy.com", "ROLE_BUS_OPERATOR", "Bus Operator");

        mockMvc.perform(get("/api/traveller/profile")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testProfileLifecycle() throws Exception {
        String token = generateToken(4L, "customer@traveleasy.com", "ROLE_CUSTOMER", "John Doe");

        // 1. Initial profile fetch
        mockMvc.perform(get("/api/traveller/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(4))
                .andExpect(jsonPath("$.email").value("customer@traveleasy.com"))
                .andExpect(jsonPath("$.fullName").value("John Doe"));

        // 2. Update profile
        UpdateProfileRequest updateReq = UpdateProfileRequest.builder()
                .city("Vijayawada")
                .state("Andhra Pradesh")
                .address("12-34 MG Road")
                .pincode("520001")
                .emergencyContactName("Jane Doe")
                .emergencyContactPhone("+919876543210")
                .dateOfBirth(LocalDate.of(1995, 5, 20))
                .gender("MALE")
                .build();

        mockMvc.perform(put("/api/traveller/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Vijayawada"))
                .andExpect(jsonPath("$.emergencyContactName").value("Jane Doe"))
                .andExpect(jsonPath("$.profileComplete").value(true));
    }

    @Test
    void testPreferencesLifecycle() throws Exception {
        String token = generateToken(4L, "customer@traveleasy.com", "ROLE_CUSTOMER", "John Doe");

        // 1. Fetch initial preferences
        mockMvc.perform(get("/api/traveller/preferences")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatPreference").value("WINDOW"));

        // 2. Update preferences
        UpdatePreferenceRequest updateReq = UpdatePreferenceRequest.builder()
                .seatPreference("AISLE")
                .berthPreference("UPPER")
                .mealPreference("VEG")
                .emailNotifications(true)
                .smsNotifications(false)
                .build();

        mockMvc.perform(put("/api/traveller/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatPreference").value("AISLE"))
                .andExpect(jsonPath("$.berthPreference").value("UPPER"))
                .andExpect(jsonPath("$.smsNotifications").value(false));
    }

    @Test
    void testFavouritesLifecycle() throws Exception {
        String token = generateToken(4L, "customer@traveleasy.com", "ROLE_CUSTOMER", "John Doe");

        // 1. Add favourite
        AddFavouriteRequest addReq = AddFavouriteRequest.builder()
                .itemType("BUS")
                .itemId(101L)
                .title("GSR Travels")
                .subtitle("Vijayawada to Hyderabad")
                .metadataJson("{\"fare\":650}")
                .build();

        String res = mockMvc.perform(post("/api/traveller/favourites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("GSR Travels"))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> favMap = objectMapper.readValue(res, Map.class);
        Long favId = Long.valueOf(favMap.get("id").toString());

        // 2. List favourites
        mockMvc.perform(get("/api/traveller/favourites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // 3. Delete favourite
        mockMvc.perform(delete("/api/traveller/favourites/" + favId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify empty list
        mockMvc.perform(get("/api/traveller/favourites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testSupportTicketCreation() throws Exception {
        String token = generateToken(4L, "customer@traveleasy.com", "ROLE_CUSTOMER", "John Doe");

        CreateSupportTicketRequest ticketReq = CreateSupportTicketRequest.builder()
                .category("BOOKING")
                .subject("Need change in boarding point")
                .message("Please change boarding point to Benz Circle")
                .build();

        mockMvc.perform(post("/api/traveller/support/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ticketReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketRef").isNotEmpty())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(get("/api/traveller/support/tickets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void testDashboardAggregation() throws Exception {
        String token = generateToken(4L, "customer@traveleasy.com", "ROLE_CUSTOMER", "John Doe");

        List<BookingSummaryDto> mockBookings = List.of(
                BookingSummaryDto.builder()
                        .id(1L)
                        .bookingRef("BKG-1111")
                        .customerId(4L)
                        .serviceType("BUS")
                        .serviceTitle("GSR Travels")
                        .status("CONFIRMED")
                        .totalAmount(650.0)
                        .build(),
                BookingSummaryDto.builder()
                        .id(2L)
                        .bookingRef("BKG-2222")
                        .customerId(4L)
                        .serviceType("HOTEL")
                        .serviceTitle("Novotel")
                        .status("CANCELLED")
                        .totalAmount(3500.0)
                        .build()
        );

        when(bookingServiceClient.getCustomerBookings(eq(4L), any())).thenReturn(mockBookings);

        mockMvc.perform(get("/api/traveller/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(4))
                .andExpect(jsonPath("$.totalBookings").value(2))
                .andExpect(jsonPath("$.confirmedBookings").value(1))
                .andExpect(jsonPath("$.cancelledBookings").value(1))
                .andExpect(jsonPath("$.recentBookings.length()").value(2));
    }
}
