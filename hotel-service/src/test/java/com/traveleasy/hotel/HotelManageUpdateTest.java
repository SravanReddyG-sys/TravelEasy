package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.HotelImageDto;
import com.traveleasy.hotel.dto.HotelManageDto;
import com.traveleasy.hotel.dto.HotelManageUpdateRequestDto;
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
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HotelManageUpdateTest {

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
    private Long testManagerId = 888L;
    private Long otherManagerId = 889L;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        managerToken = generateTestJwt("manager888", testManagerId, "ROLE_HOTEL_MANAGER");
        otherManagerToken = generateTestJwt("manager889", otherManagerId, "ROLE_HOTEL_MANAGER");
    }

    private String generateTestJwt(String username, Long userId, String role) {
        Key signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
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

    private HotelProperty createActiveHotel(Long managerId) {
        LocalDateTime submitted = LocalDateTime.now().minusDays(5).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        LocalDateTime activated = LocalDateTime.now().minusDays(3).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        HotelProperty property = HotelProperty.builder()
                .name("Original Grand Hotel")
                .managerId(managerId)
                .managerName("Original Manager")
                .propertyType("HOTEL")
                .propertyClassification("4_STAR")
                .propertySegment("PREMIUM")
                .brandName("Original Brand")
                .description("Original description text")
                .addressLine1("Old Plot 10")
                .addressLine2("Old Area")
                .city("Hyderabad")
                .state("Telangana")
                .country("India")
                .postalCode("500001")
                .landmark("Old Landmark")
                .address("Old Plot 10, Old Area, Hyderabad")
                .starRating(4)
                .status("ACTIVE")
                .registrationStep(5)
                .submittedAt(submitted)
                .activatedAt(activated)
                .propertyContactNumber("+91 99999 11111")
                .propertyEmail("original@hotel.com")
                .registeredBusinessName("Original Ltd")
                .businessRegistrationType("Private Limited")
                .businessRegistrationNumber("REG12345")
                .propertyManagerName("Ravi Kumar")
                .propertyManagerDesignation("Manager")
                .propertyManagerContactNumber("+91 98888 22222")
                .propertyManagerEmail("ravi@hotel.com")
                .checkInTime("14:00")
                .checkOutTime("11:00")
                .receptionAvailability("24_HOUR")
                .childrenPolicy("Children Allowed")
                .petPolicy("Pets Not Allowed")
                .smokingPolicy("Smoking Prohibited")
                .extraBedPolicy("Extra Bed Available")
                .acceptedIdentificationTypes("[\"Aadhaar\",\"Passport\"]")
                .predefinedAmenities("[\"Free Wi-Fi\",\"Swimming Pool\"]")
                .customAmenities("[\"Business Center\"]")
                .build();

        HotelProperty saved = hotelRepository.save(property);

        HotelImage img1 = HotelImage.builder()
                .hotelId(saved.getId())
                .imageUrl("/api/hotels/images/test1.jpg")
                .originalFileName("test1.jpg")
                .isCover(true)
                .displayOrder(0)
                .build();
        hotelImageRepository.save(img1);

        return saved;
    }

    @Test
    @DisplayName("PUT /api/hotels/{hotelId}/manage successfully updates all editable fields and preserves protected status and metadata")
    void testFullPropertyUpdate_Success_PreservesProtectedData() {
        HotelProperty property = createActiveHotel(testManagerId);
        Long hotelId = property.getId();
        LocalDateTime originalSubmittedAt = property.getSubmittedAt();
        LocalDateTime originalActivatedAt = property.getActivatedAt();

        HotelManageUpdateRequestDto updateReq = HotelManageUpdateRequestDto.builder()
                .name("Updated Grand Residency Hotel")
                .propertyType("RESORT")
                .propertyClassification("5_STAR")
                .propertySegment("LUXURY")
                .brandName("Residency Collection")
                .description("Updated luxury resort description")
                .images(List.of(
                        HotelImageDto.builder()
                                .imageUrl("/api/hotels/images/updated_cover.jpg")
                                .originalFileName("updated_cover.jpg")
                                .isCover(true)
                                .displayOrder(0)
                                .build(),
                        HotelImageDto.builder()
                                .imageUrl("/api/hotels/images/updated_second.jpg")
                                .originalFileName("updated_second.jpg")
                                .isCover(false)
                                .displayOrder(1)
                                .build()
                ))
                .addressLine1("New Suite 500, Tech Park")
                .addressLine2("Hitec City")
                .country("India")
                .state("Telangana")
                .city("Hyderabad")
                .postalCode("500081")
                .landmark("Near Cyber Towers")
                .propertyContactNumber("+91 91234 56789")
                .alternateContactNumber("+91 98765 43210")
                .propertyEmail("info@residencyhotel.com")
                .websiteUrl("https://residencyhotel.com")
                .registeredBusinessName("Residency Hospitality Pvt Ltd")
                .businessRegistrationType("Private Limited Company")
                .businessRegistrationNumber("CIN123456789")
                .registeredBusinessAddress("Plot 50, Hitec City, Hyderabad")
                .isGstRegistered(true)
                .gstin("36AAAAA0000A1Z5")
                .gstLegalName("Residency Hospitality Pvt Ltd")
                .gstState("Telangana")
                .propertyManagerName("Suresh Varma")
                .propertyManagerDesignation("General Manager")
                .propertyManagerContactNumber("+91 97777 88888")
                .propertyManagerEmail("suresh.varma@residencyhotel.com")
                .checkInTime("15:00")
                .checkOutTime("12:00")
                .receptionAvailability("24_HOUR")
                .predefinedAmenities(List.of("Free Wi-Fi", "Swimming Pool", "Fitness Center"))
                .customAmenities(List.of("Conference Hall", "Rooftop Lounge"))
                .childrenPolicy("Children of all ages allowed")
                .petPolicy("Pets Allowed on Request")
                .smokingPolicy("Designated Smoking Areas Only")
                .extraBedPolicy("Extra bed available for Rs 1000")
                .acceptedIdentificationTypes(List.of("Aadhaar", "Passport", "Driving License"))
                .additionalRules("Quiet hours after 10 PM. No outside catering.")
                .build();

        HttpEntity<HotelManageUpdateRequestDto> entity = new HttpEntity<>(updateReq, createHeaders(managerToken));
        ResponseEntity<HotelManageDto> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/hotels/" + hotelId + "/manage",
                HttpMethod.PUT,
                entity,
                HotelManageDto.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        HotelManageDto respDto = response.getBody();
        assertEquals("Updated Grand Residency Hotel", respDto.getName());
        assertEquals("RESORT", respDto.getPropertyType());
        assertEquals("5_STAR", respDto.getPropertyClassification());
        assertEquals(5, respDto.getStarRating());
        assertEquals("Suresh Varma", respDto.getPropertyManagerName());
        assertEquals("info@residencyhotel.com", respDto.getPropertyEmail());
        assertEquals("36AAAAA0000A1Z5", respDto.getGstin());
        assertEquals("15:00", respDto.getCheckInTime());
        assertEquals("12:00", respDto.getCheckOutTime());
        assertEquals(2, respDto.getImages().size());
        assertTrue(respDto.getImages().get(0).isCover());

        // Verify database entity directly
        HotelProperty dbProperty = hotelRepository.findById(hotelId).orElseThrow();
        assertEquals("ACTIVE", dbProperty.getStatus(), "Status MUST remain ACTIVE and not be altered!");
        assertEquals(testManagerId, dbProperty.getManagerId(), "ManagerId MUST NOT be changed!");
        assertEquals(5, dbProperty.getRegistrationStep(), "RegistrationStep MUST NOT be altered!");
        assertEquals(originalSubmittedAt, dbProperty.getSubmittedAt(), "SubmittedAt timestamp MUST be preserved!");
        assertEquals(originalActivatedAt, dbProperty.getActivatedAt(), "ActivatedAt timestamp MUST be preserved!");
        assertEquals("/api/hotels/images/updated_cover.jpg", dbProperty.getImageUrl());
    }

    @Test
    @DisplayName("PUT /api/hotels/{hotelId}/manage returns 403 Forbidden when called by unauthorized manager")
    void testFullPropertyUpdate_Forbidden_WhenNotOwner() {
        HotelProperty property = createActiveHotel(testManagerId);
        Long hotelId = property.getId();

        HotelManageUpdateRequestDto updateReq = HotelManageUpdateRequestDto.builder()
                .name("Malicious Update Attempt")
                .propertyType("HOTEL")
                .propertySegment("STANDARD")
                .description("Hacked description")
                .images(List.of(HotelImageDto.builder().imageUrl("/test.jpg").isCover(true).build()))
                .addressLine1("Some Address")
                .country("India")
                .state("Telangana")
                .city("Hyderabad")
                .postalCode("500001")
                .propertyContactNumber("+91 99999 99999")
                .propertyEmail("hack@hotel.com")
                .registeredBusinessName("Hack Ltd")
                .businessRegistrationType("Private")
                .businessRegistrationNumber("REG1")
                .propertyManagerName("Bad Actor")
                .propertyManagerDesignation("Intruder")
                .propertyManagerContactNumber("+91 99999 99999")
                .propertyManagerEmail("bad@actor.com")
                .checkInTime("14:00")
                .checkOutTime("11:00")
                .receptionAvailability("24_HOUR")
                .childrenPolicy("Allowed")
                .petPolicy("Allowed")
                .smokingPolicy("Allowed")
                .extraBedPolicy("Allowed")
                .acceptedIdentificationTypes(List.of("Passport"))
                .build();

        HttpEntity<HotelManageUpdateRequestDto> entity = new HttpEntity<>(updateReq, createHeaders(otherManagerToken));
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/hotels/" + hotelId + "/manage",
                HttpMethod.PUT,
                entity,
                String.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("PUT /api/hotels/{hotelId}/manage returns 400 Bad Request when mandatory fields are missing")
    void testFullPropertyUpdate_BadRequest_WhenNameMissing() {
        HotelProperty property = createActiveHotel(testManagerId);
        Long hotelId = property.getId();

        HotelManageUpdateRequestDto updateReq = HotelManageUpdateRequestDto.builder()
                .name("") // Blank name
                .propertyType("HOTEL")
                .propertySegment("STANDARD")
                .description("Valid description")
                .images(List.of(HotelImageDto.builder().imageUrl("/test.jpg").isCover(true).build()))
                .addressLine1("Some Address")
                .country("India")
                .state("Telangana")
                .city("Hyderabad")
                .postalCode("500001")
                .propertyContactNumber("+91 99999 99999")
                .propertyEmail("valid@hotel.com")
                .registeredBusinessName("Valid Ltd")
                .businessRegistrationType("Private")
                .businessRegistrationNumber("REG1")
                .propertyManagerName("Valid Manager")
                .propertyManagerDesignation("Manager")
                .propertyManagerContactNumber("+91 99999 99999")
                .propertyManagerEmail("manager@hotel.com")
                .checkInTime("14:00")
                .checkOutTime("11:00")
                .receptionAvailability("24_HOUR")
                .childrenPolicy("Allowed")
                .petPolicy("Allowed")
                .smokingPolicy("Allowed")
                .extraBedPolicy("Allowed")
                .acceptedIdentificationTypes(List.of("Passport"))
                .build();

        HttpEntity<HotelManageUpdateRequestDto> entity = new HttpEntity<>(updateReq, createHeaders(managerToken));
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/hotels/" + hotelId + "/manage",
                HttpMethod.PUT,
                entity,
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
