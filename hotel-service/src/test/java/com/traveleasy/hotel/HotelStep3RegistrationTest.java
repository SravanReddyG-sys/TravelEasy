package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.*;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.repository.HotelImageRepository;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HotelStep3RegistrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HotelPropertyRepository hotelRepository;

    @Autowired
    private HotelImageRepository hotelImageRepository;

    @Value("${jwt.secret:TravelEasySuperSecretKeyForJWTSigning12345678901234567890}")
    private String jwtSecret;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/hotels";
    }

    private String managerToken;
    private String manager2Token;
    private String travellerToken;

    @BeforeEach
    public void setup() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        managerToken = createTestToken(1001L, "ROLE_HOTEL_MANAGER", "manager1@traveleasy.com", "Manager One", 3600000);
        manager2Token = createTestToken(1002L, "ROLE_HOTEL_MANAGER", "manager2@traveleasy.com", "Manager Two", 3600000);
        travellerToken = createTestToken(2001L, "ROLE_TRAVELLER", "traveller@traveleasy.com", "Traveller", 3600000);
    }

    private String createTestToken(Long userId, String role, String email, String fullName, long ttl) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("role", role)
                .claim("fullName", fullName)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttl))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return headers;
    }

    private Long createTestStep2Draft(String name, String token) {
        // Step 1
        HotelStep1RequestDto step1Req = HotelStep1RequestDto.builder()
                .name(name)
                .propertyType("Hotel")
                .propertyClassification("5_STAR")
                .propertySegment("LUXURY")
                .brandName("Sunrise Group")
                .description("A luxury boutique property.")
                .images(List.of(
                        HotelImageDto.builder()
                                .imageUrl("/api/hotels/images/cover.jpg")
                                .originalFileName("cover.jpg")
                                .isCover(true)
                                .displayOrder(0)
                                .build()
                ))
                .build();

        ResponseEntity<HotelStep1ResponseDto> step1Res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/step-1",
                new HttpEntity<>(step1Req, createAuthHeaders(token)),
                HotelStep1ResponseDto.class
        );
        assertEquals(HttpStatus.CREATED, step1Res.getStatusCode());
        assertNotNull(step1Res.getBody());
        Long draftId = step1Res.getBody().getId();

        // Step 2
        HotelStep2RequestDto step2Req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("24 MG Road")
                .country("India")
                .state("Andhra Pradesh")
                .city("Vijayawada")
                .postalCode("520010")
                .landmark("Near Benz Circle")
                .build();

        ResponseEntity<HotelStep2ResponseDto> step2Res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(step2Req, createAuthHeaders(token)),
                HotelStep2ResponseDto.class
        );
        assertEquals(HttpStatus.OK, step2Res.getStatusCode());
        return draftId;
    }

    private HotelStep3RequestDto.HotelStep3RequestDtoBuilder getValidStep3Builder(Long draftId) {
        return HotelStep3RequestDto.builder()
                .draftId(draftId)
                // 1. Contact
                .propertyContactNumber("+91 98765 43210")
                .alternateContactNumber("+91 91234 56789")
                .propertyEmail("bookings@sunrisegrand.com")
                .websiteUrl("https://www.sunrisegrand.com")
                // 2. Business Registration
                .registeredBusinessName("Sunrise Hospitality Private Limited")
                .businessRegistrationType("Private Limited Company")
                .businessRegistrationNumber("U55101TG2020PTC123456")
                .registeredBusinessAddress("Plot 42, Hitech City, Hyderabad, 500081")
                // 3. GST Details
                .isGstRegistered(true)
                .gstin("36AAAAA0000A1Z5")
                .gstLegalName("Sunrise Hospitality Private Limited")
                .gstState("Telangana")
                // 4. Property Manager Details
                .propertyManagerName("Rajesh Sharma")
                .propertyManagerDesignation("General Manager")
                .propertyManagerContactNumber("+91 98480 12345")
                .propertyManagerEmail("rajesh.sharma@sunrisegrand.com")
                // 5. Operating Hours
                .checkInTime("14:00")
                .checkOutTime("11:00")
                .receptionAvailability("24-hour Reception");
    }

    // =========================================================================
    // 1. HAPPY PATH TESTS
    // =========================================================================

    @Test
    @DisplayName("Step 3 Happy Path: Successfully saves Step 3 with GST and 24-hr reception")
    public void testSaveStep3Draft_Success_WithGst() {
        Long draftId = createTestStep2Draft("Sunrise Grand Vijayawada", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId).build();

        ResponseEntity<HotelStep3ResponseDto> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                HotelStep3ResponseDto.class
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(draftId, res.getBody().getId());
        assertEquals(3, res.getBody().getRegistrationStep());
        assertEquals("DRAFT", res.getBody().getStatus());

        // Contact
        assertEquals("+91 98765 43210", res.getBody().getPropertyContactNumber());
        assertEquals("+91 91234 56789", res.getBody().getAlternateContactNumber());
        assertEquals("bookings@sunrisegrand.com", res.getBody().getPropertyEmail());
        assertEquals("https://www.sunrisegrand.com", res.getBody().getWebsiteUrl());

        // Business
        assertEquals("Sunrise Hospitality Private Limited", res.getBody().getRegisteredBusinessName());
        assertTrue(res.getBody().getBusinessRegistrationType().contains("PRIVATE LIMITED"));
        assertEquals("U55101TG2020PTC123456", res.getBody().getBusinessRegistrationNumber());

        // GST
        assertTrue(res.getBody().getIsGstRegistered());
        assertEquals("36AAAAA0000A1Z5", res.getBody().getGstin());
        assertEquals("Telangana", res.getBody().getGstState());

        // Manager
        assertEquals("Rajesh Sharma", res.getBody().getPropertyManagerName());
        assertEquals("General Manager", res.getBody().getPropertyManagerDesignation());
        assertEquals("+91 98480 12345", res.getBody().getPropertyManagerContactNumber());
        assertEquals("rajesh.sharma@sunrisegrand.com", res.getBody().getPropertyManagerEmail());

        // Operating Hours
        assertEquals("14:00", res.getBody().getCheckInTime());
        assertEquals("11:00", res.getBody().getCheckOutTime());
        assertEquals("24_HOUR", res.getBody().getReceptionAvailability());
    }

    @Test
    @DisplayName("Step 3 Happy Path: Successfully saves Step 3 without GST and with Limited Operating Hours")
    public void testSaveStep3Draft_Success_WithoutGst_LimitedHours() {
        Long draftId = createTestStep2Draft("Cozy Homestay", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .isGstRegistered(false)
                .gstin(null)
                .gstLegalName(null)
                .gstState(null)
                .receptionAvailability("Limited Operating Hours")
                .receptionOpeningTime("06:00")
                .receptionClosingTime("23:00")
                .build();

        ResponseEntity<HotelStep3ResponseDto> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                HotelStep3ResponseDto.class
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertFalse(res.getBody().getIsGstRegistered());
        assertNull(res.getBody().getGstin());
        assertEquals("LIMITED_HOURS", res.getBody().getReceptionAvailability());
        assertEquals("06:00", res.getBody().getReceptionOpeningTime());
        assertEquals("23:00", res.getBody().getReceptionClosingTime());
    }

    // =========================================================================
    // 2. CONTACT DETAILS VALIDATION
    // =========================================================================

    @Test
    @DisplayName("Contact Validation: Missing property contact number returns 400 Bad Request")
    public void testValidateContact_MissingContactNumber() {
        Long draftId = createTestStep2Draft("Validation Draft", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .propertyContactNumber("")
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    @DisplayName("Contact Validation: Invalid property contact number returns 400 Bad Request")
    public void testValidateContact_InvalidContactNumber() {
        Long draftId = createTestStep2Draft("Validation Draft", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .propertyContactNumber("ABC-NOT-A-PHONE")
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    @DisplayName("Contact Validation: Invalid email address returns 400 Bad Request")
    public void testValidateContact_InvalidEmail() {
        Long draftId = createTestStep2Draft("Validation Draft", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .propertyEmail("invalid-email-address")
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    @DisplayName("Contact Validation: Invalid website URL without protocol returns 400 Bad Request")
    public void testValidateContact_InvalidWebsiteUrl() {
        Long draftId = createTestStep2Draft("Validation Draft", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .websiteUrl("ftp://sunrisegrand.com")
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    // =========================================================================
    // 3. BUSINESS REGISTRATION VALIDATION
    // =========================================================================

    @Test
    @DisplayName("Business Validation: 'Other' registration type requires custom specification")
    public void testValidateBusiness_OtherRequiresCustomType() {
        Long draftId = createTestStep2Draft("Validation Draft", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .businessRegistrationType("Other")
                .otherBusinessRegistrationType("")
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    @DisplayName("Business Validation: 'Other' registration type succeeds when custom type provided")
    public void testValidateBusiness_OtherWithValidCustomType() {
        Long draftId = createTestStep2Draft("Validation Draft", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .businessRegistrationType("Other")
                .otherBusinessRegistrationType("Cooperative Society")
                .build();

        ResponseEntity<HotelStep3ResponseDto> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                HotelStep3ResponseDto.class
        );
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("OTHER", res.getBody().getBusinessRegistrationType());
        assertEquals("Cooperative Society", res.getBody().getOtherBusinessRegistrationType());
    }

    // =========================================================================
    // 4. GST DETAILS VALIDATION
    // =========================================================================

    @Test
    @DisplayName("GST Validation: GST registered without GSTIN returns 400 Bad Request")
    public void testValidateGst_MissingGstin() {
        Long draftId = createTestStep2Draft("Validation Draft", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .isGstRegistered(true)
                .gstin("")
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    @DisplayName("GST Validation: Invalid GSTIN pattern returns 400 Bad Request")
    public void testValidateGst_InvalidGstin() {
        Long draftId = createTestStep2Draft("Validation Draft", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .isGstRegistered(true)
                .gstin("INVALIDGSTIN123")
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    @DisplayName("GST State Validation: Invalid Indian state for GST returns 400 Bad Request")
    public void testValidateGst_InvalidGstState() {
        Long draftId = createTestStep2Draft("Validation Draft", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .isGstRegistered(true)
                .gstState("California") // Not an Indian state
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    // =========================================================================
    // 5. PROPERTY MANAGER & OPERATING HOURS VALIDATION
    // =========================================================================

    @Test
    @DisplayName("Manager Validation: Missing manager designation returns 400 Bad Request")
    public void testValidateManager_MissingDesignation() {
        Long draftId = createTestStep2Draft("Validation Draft", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .propertyManagerDesignation("")
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    @DisplayName("Operating Hours: Limited reception hours requires opening and closing times")
    public void testValidateOperatingHours_LimitedHoursMissingTimes() {
        Long draftId = createTestStep2Draft("Validation Draft", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .receptionAvailability("Limited Operating Hours")
                .receptionOpeningTime("")
                .receptionClosingTime("")
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    // =========================================================================
    // 6. DRAFT PERSISTENCE & DATA RETENTION
    // =========================================================================

    @Test
    @DisplayName("Draft Persistence: Step 3 updates existing draft without duplicating rows and preserves Step 1 & 2")
    public void testStep3_ReusesExistingDraftRecord() {
        long initialCount = hotelRepository.count();
        Long draftId = createTestStep2Draft("Draft Persistence Hotel", managerToken);
        assertEquals(initialCount + 1, hotelRepository.count());

        HotelStep3RequestDto step3Req = getValidStep3Builder(draftId)
                .registeredBusinessName("Persisted Holdings Ltd")
                .build();

        ResponseEntity<HotelStep3ResponseDto> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(step3Req, createAuthHeaders(managerToken)),
                HotelStep3ResponseDto.class
        );
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(draftId, res.getBody().getId());

        // Count should not increase
        assertEquals(initialCount + 1, hotelRepository.count());

        // Verify Step 1 fields preserved
        assertEquals("Draft Persistence Hotel", res.getBody().getName());
        assertEquals(1, res.getBody().getImages().size());

        // Verify Step 2 fields preserved
        assertEquals("24 MG Road", res.getBody().getAddressLine1());
        assertEquals("Vijayawada", res.getBody().getCity());
        assertEquals("520010", res.getBody().getPostalCode());

        // Verify Step 3 fields stored
        assertEquals("Persisted Holdings Ltd", res.getBody().getRegisteredBusinessName());
        assertEquals(3, res.getBody().getRegistrationStep());
    }

    // =========================================================================
    // 7. SECURITY & AUTHORIZATION
    // =========================================================================

    @Test
    @DisplayName("Security: Manager 2 cannot update Manager 1's Step 3 draft (403 Forbidden)")
    public void testSecurity_CrossManagerAccessForbidden() {
        Long draftId = createTestStep2Draft("Manager 1 Draft", managerToken);

        HotelStep3RequestDto req = getValidStep3Builder(draftId)
                .propertyContactNumber("+91 99999 88888")
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-3",
                new HttpEntity<>(req, createAuthHeaders(manager2Token)),
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    @DisplayName("Security: Unauthenticated request to Step 3 returns 401 Unauthorized")
    public void testSecurity_UnauthenticatedRequest() {
        HotelStep3RequestDto req = getValidStep3Builder(999L).build();

        ResponseEntity<String> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/999/step-3",
                new HttpEntity<>(req, createAuthHeaders(null)),
                String.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }
}
