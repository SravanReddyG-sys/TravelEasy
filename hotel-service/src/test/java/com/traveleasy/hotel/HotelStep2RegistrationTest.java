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
public class HotelStep2RegistrationTest {

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

    private String createTestToken(Long userId, String role, String email, String fullName, long ttlMillis) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("role", role)
                .claim("fullName", fullName)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
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

    private Long createTestStep1Draft(String name, String token) {
        HotelStep1RequestDto request = HotelStep1RequestDto.builder()
                .name(name)
                .propertyType("Hotel")
                .propertyClassification("5_STAR")
                .propertySegment("Luxury")
                .brandName("Sunrise Hotels")
                .description("A premier hotel in Andhra Pradesh.")
                .images(List.of(
                        HotelImageDto.builder().imageUrl("/api/hotels/images/cover.jpg").originalFileName("cover.jpg").isCover(true).displayOrder(0).build()
                ))
                .build();

        HttpEntity<HotelStep1RequestDto> entity = new HttpEntity<>(request, createAuthHeaders(token));
        ResponseEntity<HotelStep1ResponseDto> response = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", entity, HotelStep1ResponseDto.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().getId();
    }

    // =========================================================================
    // 1. SUCCESSFUL STEP 2 PERSISTENCE & RETRIEVAL
    // =========================================================================

    @Test
    @DisplayName("Step 2: Authorized manager can successfully save structured address details")
    public void testSaveStep2Draft_Success() {
        Long draftId = createTestStep1Draft("GSR Grand", managerToken);

        HotelStep2RequestDto step2Req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("Sunrise Grand Hotel, 24 MG Road")
                .addressLine2("Ashok Nagar, Benz Circle Area")
                .country("India")
                .state("Andhra Pradesh")
                .city("Vijayawada")
                .postalCode("520010")
                .landmark("Near Benz Circle, Opposite PVR Mall")
                .build();

        ResponseEntity<HotelStep2ResponseDto> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(step2Req, createAuthHeaders(managerToken)),
                HotelStep2ResponseDto.class
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        HotelStep2ResponseDto body = res.getBody();
        assertEquals(draftId, body.getId());
        assertEquals("Sunrise Grand Hotel, 24 MG Road", body.getAddressLine1());
        assertEquals("Ashok Nagar, Benz Circle Area", body.getAddressLine2());
        assertEquals("India", body.getCountry());
        assertEquals("Andhra Pradesh", body.getState());
        assertEquals("Vijayawada", body.getCity());
        assertEquals("520010", body.getPostalCode());
        assertEquals("Near Benz Circle, Opposite PVR Mall", body.getLandmark());
        assertEquals(2, body.getRegistrationStep());
        assertEquals("DRAFT", body.getStatus());

        // Verify retrieval via GET /draft/{draftId}
        ResponseEntity<HotelStep1ResponseDto> getRes = restTemplate.exchange(
                getBaseUrl() + "/draft/" + draftId,
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders(managerToken)),
                HotelStep1ResponseDto.class
        );
        assertEquals(HttpStatus.OK, getRes.getStatusCode());
        assertNotNull(getRes.getBody());
        assertEquals("Sunrise Grand Hotel, 24 MG Road", getRes.getBody().getAddressLine1());
        assertEquals("520010", getRes.getBody().getPostalCode());
        assertEquals("Vijayawada", getRes.getBody().getCity());

        // Verify in Database Entity directly
        HotelProperty entity = hotelRepository.findById(draftId).orElseThrow();
        assertEquals("Sunrise Grand Hotel, 24 MG Road", entity.getAddressLine1());
        assertEquals("Ashok Nagar, Benz Circle Area", entity.getAddressLine2());
        assertEquals("India", entity.getCountry());
        assertEquals("Andhra Pradesh", entity.getState());
        assertEquals("Vijayawada", entity.getCity());
        assertEquals("520010", entity.getPostalCode());
        assertEquals("Near Benz Circle, Opposite PVR Mall", entity.getLandmark());
        assertNotNull(entity.getAddress());
        assertTrue(entity.getAddress().contains("24 MG Road"));
        assertTrue(entity.getAddress().contains("520010"));
        assertTrue(entity.getAddress().contains("India"));
    }

    // =========================================================================
    // 2. MANDATORY FIELD VALIDATIONS
    // =========================================================================

    @Test
    @DisplayName("Validation: Missing Address Line 1 returns 400 Bad Request")
    public void testValidateAddressLine1_Mandatory() {
        Long draftId = createTestStep1Draft("Draft A1", managerToken);

        HotelStep2RequestDto req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("") // Blank
                .country("India")
                .state("Andhra Pradesh")
                .city("Vijayawada")
                .postalCode("520010")
                .build();

        ResponseEntity<String> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                String.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().contains("Address Line 1 is required") || res.getBody().contains("Address Line 1 is mandatory"));
    }

    @Test
    @DisplayName("Validation: Missing Country returns 400 Bad Request")
    public void testValidateCountry_Mandatory() {
        Long draftId = createTestStep1Draft("Draft C1", managerToken);

        HotelStep2RequestDto req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("24 MG Road")
                .country("") // Blank
                .state("Andhra Pradesh")
                .city("Vijayawada")
                .postalCode("520010")
                .build();

        ResponseEntity<String> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                String.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().contains("Country is required") || res.getBody().contains("Country is mandatory"));
    }

    @Test
    @DisplayName("Validation: Unsupported Country returns 400 Bad Request")
    public void testValidateCountry_Unsupported() {
        Long draftId = createTestStep1Draft("Draft C2", managerToken);

        HotelStep2RequestDto req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("24 MG Road")
                .country("Atlantis")
                .state("Oceania")
                .city("Vijayawada")
                .postalCode("520010")
                .build();

        ResponseEntity<String> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                String.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().contains("Unsupported country"));
    }

    @Test
    @DisplayName("Validation: Missing State returns 400 Bad Request")
    public void testValidateState_Mandatory() {
        Long draftId = createTestStep1Draft("Draft S1", managerToken);

        HotelStep2RequestDto req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("24 MG Road")
                .country("India")
                .state("") // Blank
                .city("Vijayawada")
                .postalCode("520010")
                .build();

        ResponseEntity<String> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                String.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().contains("State is required") || res.getBody().contains("State is mandatory"));
    }

    @Test
    @DisplayName("Validation: Incompatible State for Country returns 400 Bad Request")
    public void testValidateState_CountryConsistency() {
        Long draftId = createTestStep1Draft("Draft S2", managerToken);

        // California is a US state, not valid for India
        HotelStep2RequestDto req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("24 MG Road")
                .country("India")
                .state("California")
                .city("Vijayawada")
                .postalCode("520010")
                .build();

        ResponseEntity<String> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                String.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().contains("not valid for country"));
    }

    @Test
    @DisplayName("Validation: Missing City returns 400 Bad Request")
    public void testValidateCity_Mandatory() {
        Long draftId = createTestStep1Draft("Draft Ci1", managerToken);

        HotelStep2RequestDto req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("24 MG Road")
                .country("India")
                .state("Andhra Pradesh")
                .city("") // Blank
                .postalCode("520010")
                .build();

        ResponseEntity<String> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                String.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().contains("City is required") || res.getBody().contains("City is mandatory"));
    }

    @Test
    @DisplayName("Validation: Missing Postal Code returns 400 Bad Request")
    public void testValidatePostalCode_Mandatory() {
        Long draftId = createTestStep1Draft("Draft P1", managerToken);

        HotelStep2RequestDto req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("24 MG Road")
                .country("India")
                .state("Andhra Pradesh")
                .city("Vijayawada")
                .postalCode("") // Blank
                .build();

        ResponseEntity<String> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                String.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().contains("Postal Code is required") || res.getBody().contains("Postal Code is mandatory"));
    }

    // =========================================================================
    // 3. POSTAL CODE VALIDATION RULES (INDIA PIN vs INTERNATIONAL)
    // =========================================================================

    @Test
    @DisplayName("Postal Code: Valid Indian 6-digit PIN code is accepted")
    public void testPostalCode_IndianPin_Valid() {
        Long draftId = createTestStep1Draft("Draft Pin Valid", managerToken);

        HotelStep2RequestDto req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("24 MG Road")
                .country("India")
                .state("Andhra Pradesh")
                .city("Vijayawada")
                .postalCode("520010")
                .build();

        ResponseEntity<HotelStep2ResponseDto> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                HotelStep2ResponseDto.class
        );
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("520010", res.getBody().getPostalCode());
    }

    @Test
    @DisplayName("Postal Code: Invalid Indian PIN codes (e.g., 5 digits, letters, starting with 0) are rejected")
    public void testPostalCode_IndianPin_Invalid() {
        Long draftId = createTestStep1Draft("Draft Pin Invalid", managerToken);

        // 5 digits
        HotelStep2RequestDto req5 = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("24 MG Road")
                .country("India")
                .state("Andhra Pradesh")
                .city("Vijayawada")
                .postalCode("52001")
                .build();

        ResponseEntity<String> res5 = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(req5, createAuthHeaders(managerToken)),
                String.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, res5.getStatusCode());

        // Leading 0
        HotelStep2RequestDto reqZero = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("24 MG Road")
                .country("India")
                .state("Andhra Pradesh")
                .city("Vijayawada")
                .postalCode("012345")
                .build();

        ResponseEntity<String> resZero = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(reqZero, createAuthHeaders(managerToken)),
                String.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, resZero.getStatusCode());

        // Alpha characters
        HotelStep2RequestDto reqAlpha = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("24 MG Road")
                .country("India")
                .state("Andhra Pradesh")
                .city("Vijayawada")
                .postalCode("ABCDEF")
                .build();

        ResponseEntity<String> resAlpha = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(reqAlpha, createAuthHeaders(managerToken)),
                String.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, resAlpha.getStatusCode());
    }

    @Test
    @DisplayName("Postal Code: International format accepted for non-Indian countries")
    public void testPostalCode_International_Valid() {
        Long draftId = createTestStep1Draft("US Hotel", managerToken);

        HotelStep2RequestDto reqUS = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("100 Ocean Drive")
                .country("United States")
                .state("California")
                .city("Santa Monica")
                .postalCode("90401-1234")
                .build();

        ResponseEntity<HotelStep2ResponseDto> resUS = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(reqUS, createAuthHeaders(managerToken)),
                HotelStep2ResponseDto.class
        );
        assertEquals(HttpStatus.OK, resUS.getStatusCode());
        assertEquals("90401-1234", resUS.getBody().getPostalCode());
        assertEquals("United States", resUS.getBody().getCountry());
        assertEquals("California", resUS.getBody().getState());
    }

    // =========================================================================
    // 4. OPTIONAL FIELDS & DRAFT ID REUSE
    // =========================================================================

    @Test
    @DisplayName("Optional Fields: Empty Address Line 2 and Landmark are accepted")
    public void testOptionalFields_AcceptedEmpty() {
        Long draftId = createTestStep1Draft("Minimal Address Hotel", managerToken);

        HotelStep2RequestDto req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("123 Main Road")
                .addressLine2(null)
                .country("India")
                .state("Karnataka")
                .city("Bengaluru")
                .postalCode("560001")
                .landmark("")
                .build();

        ResponseEntity<HotelStep2ResponseDto> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(req, createAuthHeaders(managerToken)),
                HotelStep2ResponseDto.class
        );
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNull(res.getBody().getAddressLine2());
        assertNull(res.getBody().getLandmark());
    }

    @Test
    @DisplayName("Draft Persistence: Step 2 updates the existing Step 1 draft without duplicating rows")
    public void testStep2_ReusesExistingDraftRecord() {
        long initialCount = hotelRepository.count();
        Long draftId = createTestStep1Draft("Single Row Draft", managerToken);
        assertEquals(initialCount + 1, hotelRepository.count());

        HotelStep2RequestDto step2Req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("Plot 55, Hitech City")
                .country("India")
                .state("Telangana")
                .city("Hyderabad")
                .postalCode("500081")
                .build();

        ResponseEntity<HotelStep2ResponseDto> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(step2Req, createAuthHeaders(managerToken)),
                HotelStep2ResponseDto.class
        );
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(draftId, res.getBody().getId());

        // Verify count did not increase
        assertEquals(initialCount + 1, hotelRepository.count());

        // Verify Step 1 fields were preserved
        assertEquals("Single Row Draft", res.getBody().getName());
        assertTrue("Luxury".equalsIgnoreCase(res.getBody().getPropertySegment()));
        assertEquals(1, res.getBody().getImages().size());
    }

    // =========================================================================
    // 5. SECURITY & AUTHORIZATION
    // =========================================================================

    @Test
    @DisplayName("Security: Manager 2 cannot update Manager 1's Step 2 draft (403 Forbidden)")
    public void testSecurity_CrossManagerAccessForbidden() {
        Long draftId = createTestStep1Draft("Manager 1 Draft", managerToken);

        HotelStep2RequestDto req = HotelStep2RequestDto.builder()
                .draftId(draftId)
                .addressLine1("Unauthorized Address Update")
                .country("India")
                .state("Andhra Pradesh")
                .city("Vijayawada")
                .postalCode("520010")
                .build();

        // Manager 2 tries to update Manager 1's draft
        ResponseEntity<Map> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/" + draftId + "/step-2",
                new HttpEntity<>(req, createAuthHeaders(manager2Token)),
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    @DisplayName("Security: Unauthenticated request to Step 2 returns 401 Unauthorized")
    public void testSecurity_UnauthenticatedRequest() {
        HotelStep2RequestDto req = HotelStep2RequestDto.builder()
                .draftId(999L)
                .addressLine1("No Auth Street")
                .country("India")
                .state("Andhra Pradesh")
                .city("Vijayawada")
                .postalCode("520010")
                .build();

        ResponseEntity<String> res = restTemplate.postForEntity(
                getBaseUrl() + "/draft/999/step-2",
                new HttpEntity<>(req, createAuthHeaders(null)),
                String.class
        );
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    // =========================================================================
    // 6. REFERENCE DATA ENDPOINTS
    // =========================================================================

    @Test
    @DisplayName("Reference Data: Returns supported countries and state lists")
    public void testReferenceData_Endpoints() {
        // Countries
        ResponseEntity<List> countriesRes = restTemplate.getForEntity(
                getBaseUrl() + "/reference/countries",
                List.class
        );
        assertEquals(HttpStatus.OK, countriesRes.getStatusCode());
        assertNotNull(countriesRes.getBody());
        assertTrue(countriesRes.getBody().contains("India"));
        assertTrue(countriesRes.getBody().contains("United States"));

        // States for India
        ResponseEntity<List> statesRes = restTemplate.getForEntity(
                getBaseUrl() + "/reference/states?country=India",
                List.class
        );
        assertEquals(HttpStatus.OK, statesRes.getStatusCode());
        assertNotNull(statesRes.getBody());
        assertTrue(statesRes.getBody().contains("Andhra Pradesh"));
        assertTrue(statesRes.getBody().contains("Telangana"));
        assertTrue(statesRes.getBody().contains("Karnataka"));
    }
}
