package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.*;
import com.traveleasy.hotel.model.HotelImage;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HotelStep4RegistrationTest {

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

    private String managerToken;
    private String otherManagerToken;
    private Long testManagerId = 401L;
    private Long otherManagerId = 402L;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        managerToken = generateTestJwt("manager401", testManagerId, "ROLE_HOTEL_MANAGER");
        otherManagerToken = generateTestJwt("manager402", otherManagerId, "ROLE_HOTEL_MANAGER");
    }

    private String generateTestJwt(String username, Long userId, String role) {
        Key signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private Long createTestStep3Draft(Long managerId) {
        HotelProperty property = HotelProperty.builder()
                .name("Grand Palace")
                .propertyType("HOTEL")
                .propertyClassification("5_STAR")
                .propertySegment("LUXURY")
                .brandName("Palace Group")
                .description("Luxury hotel with world-class facilities and top hospitality.")
                .managerId(managerId)
                .managerName("Manager " + managerId)
                .status("DRAFT")
                .registrationStep(3)
                // Step 2 Address
                .addressLine1("123 Palace Road")
                .addressLine2("Opposite City Center")
                .country("India")
                .state("Maharashtra")
                .city("Mumbai")
                .postalCode("400001")
                .landmark("Near Marine Drive")
                .address("123 Palace Road, Opposite City Center, Near Marine Drive, Mumbai, Maharashtra, 400001, India")
                // Step 3 Contact
                .propertyContactNumber("+91 98765 43210")
                .alternateContactNumber("+91 91234 56789")
                .propertyEmail("bookings@grandpalace.com")
                .websiteUrl("https://www.grandpalace.com")
                // Step 3 Business
                .registeredBusinessName("Grand Palace Hospitality Pvt Ltd")
                .businessRegistrationType("Private Limited Company")
                .businessRegistrationNumber("U55101MH2020PTC123456")
                .registeredBusinessAddress("Level 5, Express Towers, Nariman Point, Mumbai")
                .isGstRegistered(true)
                .gstin("27AABCG1234A1Z5")
                .gstLegalName("Grand Palace Hospitality Private Limited")
                .gstState("Maharashtra")
                // Step 3 Manager
                .propertyManagerName("Rajesh Sharma")
                .propertyManagerDesignation("General Manager")
                .propertyManagerContactNumber("+91 98111 22334")
                .propertyManagerEmail("rajesh.sharma@grandpalace.com")
                // Step 3 Operating
                .checkInTime("14:00")
                .checkOutTime("11:00")
                .receptionAvailability("24-hour Reception")
                .build();

        HotelProperty saved = hotelRepository.save(property);

        HotelImage cover = HotelImage.builder()
                .hotelId(saved.getId())
                .imageUrl("/api/hotels/images/cover.jpg")
                .originalFileName("cover.jpg")
                .isCover(true)
                .displayOrder(0)
                .build();
        hotelImageRepository.save(cover);

        return saved.getId();
    }

    private HotelStep4RequestDto.HotelStep4RequestDtoBuilder createValidStep4Builder(Long draftId) {
        return HotelStep4RequestDto.builder()
                .draftId(draftId)
                .predefinedAmenities(List.of("Wi-Fi", "Restaurant", "Room Service", "Swimming Pool", "CCTV", "24-hour Security", "Elevator", "Laundry"))
                .customAmenities(List.of("EV Charging Station", "Rooftop Sky Lounge"))
                .childrenPolicy("Children Allowed")
                .petPolicy("Pets Allowed")
                .smokingPolicy("Designated Smoking Areas")
                .extraBedPolicy("Extra Bed Available")
                .acceptedIdentificationTypes(List.of("Passport", "Aadhaar Card", "Driving Licence"))
                .additionalRules("Quiet hours from 10 PM to 6 AM. Valid photo ID required upon check-in.");
    }

    @Test
    @DisplayName("1. Save valid Step 4 facilities and policies successfully")
    void testSaveValidStep4Draft() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId).build();

        HttpEntity<HotelStep4RequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<HotelStep4ResponseDto> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                entity,
                HotelStep4ResponseDto.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        HotelStep4ResponseDto dto = response.getBody();
        assertEquals(draftId, dto.getId());
        assertEquals("DRAFT", dto.getStatus());
        assertEquals(4, dto.getRegistrationStep());

        // Verify facilities & amenities
        assertTrue(dto.getPredefinedAmenities().contains("Wi-Fi"));
        assertTrue(dto.getPredefinedAmenities().contains("Restaurant"));
        assertTrue(dto.getPredefinedAmenities().contains("Swimming Pool"));
        assertTrue(dto.getCustomAmenities().contains("EV Charging Station"));
        assertTrue(dto.getCustomAmenities().contains("Rooftop Sky Lounge"));

        // Verify guest policies
        assertEquals("Children Allowed", dto.getChildrenPolicy());
        assertEquals("Pets Allowed", dto.getPetPolicy());
        assertEquals("Designated Smoking Areas", dto.getSmokingPolicy());
        assertEquals("Extra Bed Available", dto.getExtraBedPolicy());

        // Verify identification
        assertTrue(dto.getAcceptedIdentificationTypes().contains("Passport"));
        assertTrue(dto.getAcceptedIdentificationTypes().contains("Aadhaar Card"));
        assertNull(dto.getCustomIdentificationType());

        // Verify additional rules
        assertEquals("Quiet hours from 10 PM to 6 AM. Valid photo ID required upon check-in.", dto.getAdditionalRules());

        // Verify backward compatibility legacy amenities string
        assertNotNull(dto.getAmenities());
        assertTrue(dto.getAmenities().contains("Wi-Fi"));
        assertTrue(dto.getAmenities().contains("EV Charging Station"));

        // Verify Step 1-3 preserved
        assertEquals("Grand Palace", dto.getName());
        assertEquals("123 Palace Road", dto.getAddressLine1());
        assertEquals("+91 98765 43210", dto.getPropertyContactNumber());
        assertEquals("Rajesh Sharma", dto.getPropertyManagerName());
    }

    @Test
    @DisplayName("2. Save Step 4 with 'Other' identification type and conditional custom description")
    void testSaveStep4WithOtherIdentification() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId)
                .acceptedIdentificationTypes(List.of("Passport", "Other"))
                .customIdentificationType("Consular Card or Student ID")
                .build();

        HttpEntity<HotelStep4RequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<HotelStep4ResponseDto> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                entity,
                HotelStep4ResponseDto.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getAcceptedIdentificationTypes().contains("Other"));
        assertEquals("Consular Card or Student ID", response.getBody().getCustomIdentificationType());
    }

    @Test
    @DisplayName("3. Reject Step 4 when 'Other' ID type is selected but custom description is missing")
    void testRejectStep4MissingCustomIdentification() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId)
                .acceptedIdentificationTypes(List.of("Passport", "Other"))
                .customIdentificationType(null)
                .build();

        HttpEntity<HotelStep4RequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Custom identification"));
    }

    @Test
    @DisplayName("4. Reject Step 4 when accepted identification list is empty")
    void testRejectStep4EmptyIdentificationList() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId)
                .acceptedIdentificationTypes(new ArrayList<>())
                .build();

        HttpEntity<HotelStep4RequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("5. Reject Step 4 when custom amenity is a duplicate of a predefined amenity")
    void testRejectCustomAmenityDuplicateOfPredefined() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId)
                .customAmenities(List.of("wi-fi")) // case-insensitive match for predefined Wi-Fi
                .build();

        HttpEntity<HotelStep4RequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("already exists as a predefined facility"));
    }

    @Test
    @DisplayName("6. Reject Step 4 when custom amenity has duplicate values")
    void testRejectDuplicateCustomAmenities() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId)
                .customAmenities(List.of("EV Charger", "ev charger"))
                .build();

        HttpEntity<HotelStep4RequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Duplicate custom amenity"));
    }

    @Test
    @DisplayName("7. Reject Step 4 when custom amenity name is blank or exceeds 50 chars")
    void testRejectInvalidCustomAmenityLength() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId)
                .customAmenities(List.of("A".repeat(55)))
                .build();

        HttpEntity<HotelStep4RequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("50 characters"));
    }

    @Test
    @DisplayName("8. Reject Step 4 when unsupported predefined amenity is supplied")
    void testRejectUnsupportedPredefinedAmenity() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId)
                .predefinedAmenities(List.of("Helicopter Pad"))
                .build();

        HttpEntity<HotelStep4RequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Unsupported predefined amenity"));
    }

    @Test
    @DisplayName("9. Reject Step 4 when mandatory policy is missing or invalid")
    void testRejectInvalidPolicyOption() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId)
                .childrenPolicy("Children Sometimes Allowed")
                .build();

        HttpEntity<HotelStep4RequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("children policy"));
    }

    @Test
    @DisplayName("10. Reject Step 4 when additional rules exceed 1000 characters")
    void testRejectAdditionalRulesExceedLimit() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId)
                .additionalRules("R".repeat(1005))
                .build();

        HttpEntity<HotelStep4RequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("1000 characters"));
    }

    @Test
    @DisplayName("11. Enforce manager isolation: other manager cannot update draft (403 Forbidden)")
    void testEnforceManagerIsolation() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId).build();

        HttpEntity<HotelStep4RequestDto> entity = new HttpEntity<>(request, createHeaders(otherManagerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("12. Unauthenticated request rejected with 401 Unauthorized")
    void testUnauthenticatedRequest() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId).build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                new HttpEntity<>(request, headers),
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("13. Reopening draft via getDraft restores complete Step 4 information")
    void testGetDraftRestoresStep4Information() {
        Long draftId = createTestStep3Draft(testManagerId);
        HotelStep4RequestDto request = createValidStep4Builder(draftId).build();

        HttpEntity<HotelStep4RequestDto> postEntity = new HttpEntity<>(request, createHeaders(managerToken));
        restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/step-4",
                postEntity,
                HotelStep4ResponseDto.class
        );

        // Fetch draft
        HttpEntity<Void> getEntity = new HttpEntity<>(createHeaders(managerToken));
        ResponseEntity<HotelStep1ResponseDto> getResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId,
                HttpMethod.GET,
                getEntity,
                HotelStep1ResponseDto.class
        );

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());

        HotelStep1ResponseDto draft = getResponse.getBody();
        assertEquals(4, draft.getRegistrationStep());
        assertTrue(draft.getPredefinedAmenities().contains("Wi-Fi"));
        assertTrue(draft.getCustomAmenities().contains("EV Charging Station"));
        assertEquals("Children Allowed", draft.getChildrenPolicy());
        assertEquals("Pets Allowed", draft.getPetPolicy());
        assertEquals("Designated Smoking Areas", draft.getSmokingPolicy());
        assertEquals("Extra Bed Available", draft.getExtraBedPolicy());
        assertTrue(draft.getAcceptedIdentificationTypes().contains("Passport"));
        assertEquals("Quiet hours from 10 PM to 6 AM. Valid photo ID required upon check-in.", draft.getAdditionalRules());
    }
}
