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
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HotelStep5RegistrationTest {

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
    private Long testManagerId = 501L;
    private Long otherManagerId = 502L;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        managerToken = generateTestJwt("manager501", testManagerId, "ROLE_HOTEL_MANAGER");
        otherManagerToken = generateTestJwt("manager502", otherManagerId, "ROLE_HOTEL_MANAGER");
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

    private Long createCompleteStep4Draft(Long managerId) {
        HotelProperty property = HotelProperty.builder()
                // Step 1: Basic Details
                .name("Sunrise Grand Hotel")
                .propertyType("HOTEL")
                .propertyClassification("4_STAR")
                .propertySegment("PREMIUM")
                .brandName("Sunrise Hotels")
                .description("A modern hotel offering comfortable accommodation, exceptional hospitality, and premium amenities.")
                .managerId(managerId)
                .managerName("Manager " + managerId)
                .status("DRAFT")
                .registrationStep(4)
                // Step 2: Location & Address
                .addressLine1("24 MG Road, Benz Circle")
                .addressLine2("Opposite PVR Mall")
                .country("India")
                .state("Andhra Pradesh")
                .city("Vijayawada")
                .postalCode("520010")
                .landmark("Near Benz Circle Area")
                .address("24 MG Road, Benz Circle, Opposite PVR Mall, Near Benz Circle Area, Vijayawada, Andhra Pradesh, 520010, India")
                // Step 3: Contact Details
                .propertyContactNumber("+91 98765 43210")
                .alternateContactNumber("+91 91234 56789")
                .propertyEmail("bookings@sunrisegrand.com")
                .websiteUrl("https://www.sunrisegrand.com")
                // Step 3: Business Registration
                .registeredBusinessName("Sunrise Grand Hospitality Pvt Ltd")
                .businessRegistrationType("Private Limited Company")
                .businessRegistrationNumber("U55101AP2021PTC123456")
                .registeredBusinessAddress("Plot 24, MG Road, Vijayawada, Andhra Pradesh")
                .isGstRegistered(true)
                .gstin("37AABCS1234F1Z5")
                .gstLegalName("Sunrise Grand Hospitality Private Limited")
                .gstState("Andhra Pradesh")
                // Step 3: Property Manager
                .propertyManagerName("Rajesh Kumar")
                .propertyManagerDesignation("Property Manager")
                .propertyManagerContactNumber("+91 98765 43210")
                .propertyManagerEmail("manager@sunrisegrand.com")
                // Step 3: Operating Hours
                .checkInTime("14:00")
                .checkOutTime("11:00")
                .receptionAvailability("24-hour Reception")
                // Step 4: Facilities & Policies
                .predefinedAmenities("[\"Wi-Fi\",\"Restaurant\",\"Room Service\",\"Parking\",\"CCTV\",\"24-hour Security\",\"Laundry\",\"Reception\"]")
                .customAmenities("[\"EV Charging Station\"]")
                .amenities("Wi-Fi, Restaurant, Room Service, Parking, CCTV, 24-hour Security, Laundry, Reception, EV Charging Station")
                .childrenPolicy("Children Allowed")
                .petPolicy("Pets Not Allowed")
                .smokingPolicy("Designated Smoking Areas")
                .extraBedPolicy("Extra Bed Available")
                .acceptedIdentificationTypes("[\"Passport\",\"Aadhaar Card\",\"Driving Licence\"]")
                .additionalRules("Maintain quiet hours from 10:00 PM to 6:00 AM. Valid photo ID required at check-in.")
                .build();

        HotelProperty saved = hotelRepository.save(property);

        // Save cover image
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

    @Test
    @DisplayName("1. Submit valid property draft successfully -> Transitions to ACTIVE (BD-03)")
    void testSubmitValidPropertyDraftSuccess() {
        Long draftId = createCompleteStep4Draft(testManagerId);
        HotelStep5SubmitRequestDto request = HotelStep5SubmitRequestDto.builder()
                .draftId(draftId)
                .confirmationAccepted(true)
                .build();

        HttpEntity<HotelStep5SubmitRequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<HotelStep5ResponseDto> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/submit",
                entity,
                HotelStep5ResponseDto.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        HotelStep5ResponseDto dto = response.getBody();
        assertEquals(draftId, dto.getId());
        assertEquals("ACTIVE", dto.getStatus()); // Activated immediately per BD-03
        assertEquals(5, dto.getRegistrationStep());
        assertNotNull(dto.getSubmittedAt());
        assertNotNull(dto.getActivatedAt());

        // Verify summary fields
        assertEquals("Sunrise Grand Hotel", dto.getName());
        assertEquals("4_STAR", dto.getPropertyClassification());
        assertEquals("PREMIUM", dto.getPropertySegment());
        assertEquals("Vijayawada", dto.getCity());
        assertEquals("+91 98765 43210", dto.getPropertyContactNumber());
        assertEquals("Sunrise Grand Hospitality Pvt Ltd", dto.getRegisteredBusinessName());
        assertEquals("37AABCS1234F1Z5", dto.getGstin());
        assertEquals("Rajesh Kumar", dto.getPropertyManagerName());
        assertEquals("Children Allowed", dto.getChildrenPolicy());
        assertTrue(dto.getPredefinedAmenities().contains("Wi-Fi"));
        assertTrue(dto.getCustomAmenities().contains("EV Charging Station"));

        // Verify persisted state in database
        HotelProperty inDb = hotelRepository.findById(draftId).orElseThrow();
        assertEquals("ACTIVE", inDb.getStatus());
        assertEquals(5, inDb.getRegistrationStep());
        assertNotNull(inDb.getActivatedAt());
    }

    @Test
    @DisplayName("2. Idempotent submission: Submitting an already ACTIVE property returns active status without error")
    void testIdempotentSubmission() {
        Long draftId = createCompleteStep4Draft(testManagerId);
        HotelStep5SubmitRequestDto request = HotelStep5SubmitRequestDto.builder()
                .draftId(draftId)
                .confirmationAccepted(true)
                .build();

        HttpEntity<HotelStep5SubmitRequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));

        // First submission
        ResponseEntity<HotelStep5ResponseDto> firstResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/submit",
                entity,
                HotelStep5ResponseDto.class
        );
        assertEquals(HttpStatus.OK, firstResponse.getStatusCode());
        assertEquals("ACTIVE", firstResponse.getBody().getStatus());

        // Second submission (idempotent retry)
        ResponseEntity<HotelStep5ResponseDto> secondResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/submit",
                entity,
                HotelStep5ResponseDto.class
        );
        assertEquals(HttpStatus.OK, secondResponse.getStatusCode());
        assertEquals("ACTIVE", secondResponse.getBody().getStatus());
        assertEquals(draftId, secondResponse.getBody().getId());
    }

    @Test
    @DisplayName("3. Reject submission when confirmation checkbox is false")
    void testRejectSubmissionConfirmationNotAccepted() {
        Long draftId = createCompleteStep4Draft(testManagerId);
        HotelStep5SubmitRequestDto request = HotelStep5SubmitRequestDto.builder()
                .draftId(draftId)
                .confirmationAccepted(false)
                .build();

        HttpEntity<HotelStep5SubmitRequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/submit",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("4. Reject submission when draft is missing mandatory Step 1 cover image")
    void testRejectSubmissionMissingCoverImage() {
        Long draftId = createCompleteStep4Draft(testManagerId);
        // Delete the cover image safely
        List<HotelImage> images = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(draftId);
        hotelImageRepository.deleteAll(images);

        HotelStep5SubmitRequestDto request = HotelStep5SubmitRequestDto.builder()
                .draftId(draftId)
                .confirmationAccepted(true)
                .build();

        HttpEntity<HotelStep5SubmitRequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/submit",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Step 1 Incomplete"));
    }

    @Test
    @DisplayName("5. Reject submission when draft has invalid state for country (Step 2)")
    void testRejectSubmissionInvalidState() {
        Long draftId = createCompleteStep4Draft(testManagerId);
        HotelProperty prop = hotelRepository.findById(draftId).orElseThrow();
        prop.setState("California"); // invalid state for India
        hotelRepository.save(prop);

        HotelStep5SubmitRequestDto request = HotelStep5SubmitRequestDto.builder()
                .draftId(draftId)
                .confirmationAccepted(true)
                .build();

        HttpEntity<HotelStep5SubmitRequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/submit",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Step 2 Incomplete"));
    }

    @Test
    @DisplayName("6. Reject submission when draft has missing business registration number (Step 3)")
    void testRejectSubmissionMissingBusinessRegNumber() {
        Long draftId = createCompleteStep4Draft(testManagerId);
        HotelProperty prop = hotelRepository.findById(draftId).orElseThrow();
        prop.setBusinessRegistrationNumber(null);
        hotelRepository.save(prop);

        HotelStep5SubmitRequestDto request = HotelStep5SubmitRequestDto.builder()
                .draftId(draftId)
                .confirmationAccepted(true)
                .build();

        HttpEntity<HotelStep5SubmitRequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/submit",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Step 3 Incomplete"));
    }

    @Test
    @DisplayName("7. Reject submission when draft is missing mandatory guest policy (Step 4)")
    void testRejectSubmissionMissingGuestPolicy() {
        Long draftId = createCompleteStep4Draft(testManagerId);
        HotelProperty prop = hotelRepository.findById(draftId).orElseThrow();
        prop.setChildrenPolicy(null);
        hotelRepository.save(prop);

        HotelStep5SubmitRequestDto request = HotelStep5SubmitRequestDto.builder()
                .draftId(draftId)
                .confirmationAccepted(true)
                .build();

        HttpEntity<HotelStep5SubmitRequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/submit",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Step 4 Incomplete"));
    }

    @Test
    @DisplayName("8. Enforce manager ownership: other manager cannot submit draft (403 Forbidden)")
    void testEnforceManagerOwnershipOnSubmit() {
        Long draftId = createCompleteStep4Draft(testManagerId);
        HotelStep5SubmitRequestDto request = HotelStep5SubmitRequestDto.builder()
                .draftId(draftId)
                .confirmationAccepted(true)
                .build();

        HttpEntity<HotelStep5SubmitRequestDto> entity = new HttpEntity<>(request, createHeaders(otherManagerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/submit",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("9. Unauthenticated submission returns 401 Unauthorized")
    void testUnauthenticatedSubmission() {
        Long draftId = createCompleteStep4Draft(testManagerId);
        HotelStep5SubmitRequestDto request = HotelStep5SubmitRequestDto.builder()
                .draftId(draftId)
                .confirmationAccepted(true)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/submit",
                new HttpEntity<>(request, headers),
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("10. Non-existent draft ID returns 404 Not Found")
    void testNonExistentDraftId() {
        HotelStep5SubmitRequestDto request = HotelStep5SubmitRequestDto.builder()
                .draftId(999999L)
                .confirmationAccepted(true)
                .build();

        HttpEntity<HotelStep5SubmitRequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/999999/submit",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("11. Verified newly activated property appears in manager's properties")
    void testActivatedPropertyAppearsInManagerProperties() {
        Long draftId = createCompleteStep4Draft(testManagerId);
        HotelStep5SubmitRequestDto request = HotelStep5SubmitRequestDto.builder()
                .draftId(draftId)
                .confirmationAccepted(true)
                .build();

        HttpEntity<HotelStep5SubmitRequestDto> entity = new HttpEntity<>(request, createHeaders(managerToken));
        restTemplate.postForEntity(
                "http://localhost:" + port + "/api/hotels/draft/" + draftId + "/submit",
                entity,
                HotelStep5ResponseDto.class
        );

        // Query manager's properties
        HttpEntity<Void> getEntity = new HttpEntity<>(createHeaders(managerToken));
        ResponseEntity<List> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/hotels/manager/" + testManagerId,
                HttpMethod.GET,
                getEntity,
                List.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() >= 1);
    }
}
