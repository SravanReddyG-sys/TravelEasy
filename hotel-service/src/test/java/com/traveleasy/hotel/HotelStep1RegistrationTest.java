package com.traveleasy.hotel;

import com.traveleasy.hotel.dto.HotelImageDto;
import com.traveleasy.hotel.dto.HotelStep1RequestDto;
import com.traveleasy.hotel.dto.HotelStep1ResponseDto;
import com.traveleasy.hotel.model.HotelImage;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.repository.HotelImageRepository;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.service.HotelImageStorageService;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HotelStep1RegistrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HotelPropertyRepository hotelRepository;

    @Autowired
    private HotelImageRepository hotelImageRepository;

    @Autowired
    private HotelImageStorageService imageStorageService;

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

    // =========================================================================
    // 1. BASIC DETAILS DRAFT CREATION & RECEPTION
    // =========================================================================

    @Test
    @DisplayName("Authorized manager can save Step 1 draft with valid data")
    public void testSaveStep1Draft_Success() {
        HotelStep1RequestDto request = HotelStep1RequestDto.builder()
                .name("Sunrise Grand Hotel")
                .propertyType("Hotel")
                .propertyClassification("5_STAR")
                .propertySegment("Luxury")
                .brandName("Sunrise Hotels")
                .description("A premier luxury hotel in central Bengaluru with first-class amenities.")
                .images(List.of(
                        HotelImageDto.builder().imageUrl("/api/hotels/images/cover.jpg").originalFileName("cover.jpg").isCover(true).displayOrder(0).build(),
                        HotelImageDto.builder().imageUrl("/api/hotels/images/room1.jpg").originalFileName("room1.jpg").isCover(false).displayOrder(1).build()
                ))
                .build();

        HttpEntity<HotelStep1RequestDto> entity = new HttpEntity<>(request, createAuthHeaders(managerToken));
        ResponseEntity<HotelStep1ResponseDto> response = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", entity, HotelStep1ResponseDto.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        HotelStep1ResponseDto body = response.getBody();
        assertNotNull(body.getId());
        assertEquals("Sunrise Grand Hotel", body.getName());
        assertEquals("Hotel", body.getPropertyType());
        assertEquals("5_STAR", body.getPropertyClassification());
        assertEquals(5, body.getStarRating());
        assertEquals("LUXURY", body.getPropertySegment());
        assertEquals("Sunrise Hotels", body.getBrandName());
        assertEquals("DRAFT", body.getStatus()); // Crucial: draft must not be ACTIVE
        assertEquals(1, body.getRegistrationStep());
        assertEquals(2, body.getImages().size());
        assertEquals("/api/hotels/images/cover.jpg", body.getCoverImageUrl());
    }

    @Test
    @DisplayName("Unauthorized caller (e.g. Traveller or unauthenticated) cannot save Step 1 draft")
    public void testSaveStep1Draft_Unauthorized() {
        HotelStep1RequestDto request = HotelStep1RequestDto.builder()
                .name("Unauthorized Hotel")
                .propertyType("Hotel")
                .propertySegment("Standard")
                .description("Test description")
                .images(List.of(HotelImageDto.builder().imageUrl("/img.jpg").isCover(true).build()))
                .build();

        // No token
        HttpEntity<HotelStep1RequestDto> unauthEntity = new HttpEntity<>(request, new HttpHeaders());
        ResponseEntity<String> unauthResponse = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", unauthEntity, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthResponse.getStatusCode());

        // Traveller token
        HttpEntity<HotelStep1RequestDto> travellerEntity = new HttpEntity<>(request, createAuthHeaders(travellerToken));
        ResponseEntity<String> travellerResponse = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", travellerEntity, String.class);
        assertEquals(HttpStatus.FORBIDDEN, travellerResponse.getStatusCode());
    }

    // =========================================================================
    // 2. FIELD VALIDATIONS
    // =========================================================================

    @Test
    @DisplayName("Mandatory Property Name validation: blank or whitespace is rejected")
    public void testValidatePropertyName_Mandatory() {
        HotelStep1RequestDto request = HotelStep1RequestDto.builder()
                .name("   ")
                .propertyType("Hotel")
                .propertySegment("Standard")
                .description("Valid description")
                .images(List.of(HotelImageDto.builder().imageUrl("/cover.jpg").isCover(true).build()))
                .build();

        HttpEntity<HotelStep1RequestDto> entity = new HttpEntity<>(request, createAuthHeaders(managerToken));
        ResponseEntity<String> response = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", entity, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Property name is required") || response.getBody().contains("Property Name is mandatory"));
    }

    @Test
    @DisplayName("Mandatory Property Type validation: missing or invalid type is rejected")
    public void testValidatePropertyType_Mandatory() {
        HotelStep1RequestDto request = HotelStep1RequestDto.builder()
                .name("Test Hotel")
                .propertyType("Spaceship") // Unsupported type
                .propertySegment("Standard")
                .description("Valid description")
                .images(List.of(HotelImageDto.builder().imageUrl("/cover.jpg").isCover(true).build()))
                .build();

        HttpEntity<HotelStep1RequestDto> entity = new HttpEntity<>(request, createAuthHeaders(managerToken));
        ResponseEntity<String> response = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", entity, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Unsupported property type"));
    }

    @Test
    @DisplayName("Recognized Property Classification: Not officially classified persisted distinctly")
    public void testClassification_NotOfficiallyClassified() {
        HotelStep1RequestDto request = HotelStep1RequestDto.builder()
                .name("Boutique Haven")
                .propertyType("Boutique Hotel")
                .propertyClassification("NOT_CLASSIFIED")
                .propertySegment("Premium")
                .description("Quaint boutique stay without official government star rating.")
                .images(List.of(HotelImageDto.builder().imageUrl("/cover.jpg").isCover(true).build()))
                .build();

        HttpEntity<HotelStep1RequestDto> entity = new HttpEntity<>(request, createAuthHeaders(managerToken));
        ResponseEntity<HotelStep1ResponseDto> response = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", entity, HotelStep1ResponseDto.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("NOT_CLASSIFIED", response.getBody().getPropertyClassification());
        assertNull(response.getBody().getStarRating()); // starRating is null for unclassified
    }

    @Test
    @DisplayName("Classification: HERITAGE is rejected as direct option; must use OTHER with custom classification")
    public void testClassification_HeritageNotDirectOption() {
        // Direct HERITAGE should be rejected
        HotelStep1RequestDto reqHeritage = HotelStep1RequestDto.builder()
                .name("Heritage Palace")
                .propertyType("Hotel")
                .propertyClassification("HERITAGE")
                .propertySegment("Luxury")
                .description("Heritage property description.")
                .images(List.of(HotelImageDto.builder().imageUrl("/cover.jpg").isCover(true).build()))
                .build();

        HttpEntity<HotelStep1RequestDto> entity = new HttpEntity<>(reqHeritage, createAuthHeaders(managerToken));
        ResponseEntity<String> res = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", entity, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().contains("Heritage or other classification must be entered via 'Other recognized classification'"));

        // Entering via OTHER with customClassification succeeds
        HotelStep1RequestDto reqOtherHeritage = HotelStep1RequestDto.builder()
                .name("Heritage Palace Via Other")
                .propertyType("Hotel")
                .propertyClassification("OTHER")
                .customClassification("Heritage Grand Palace")
                .propertySegment("Luxury")
                .description("Heritage property entered via Other recognized classification.")
                .images(List.of(HotelImageDto.builder().imageUrl("/cover.jpg").isCover(true).build()))
                .build();

        ResponseEntity<HotelStep1ResponseDto> resOk = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1",
                new HttpEntity<>(reqOtherHeritage, createAuthHeaders(managerToken)), HotelStep1ResponseDto.class);
        assertEquals(HttpStatus.CREATED, resOk.getStatusCode());
        assertEquals("OTHER", resOk.getBody().getPropertyClassification());
        assertEquals("Heritage Grand Palace", resOk.getBody().getCustomClassification());
        assertNull(resOk.getBody().getStarRating());
    }

    @Test
    @DisplayName("Recognized Property Classification: 5 Star Deluxe sets starRating=5 and retains 5_STAR_DELUXE")
    public void testClassification_5StarDeluxe() {
        HotelStep1RequestDto request = HotelStep1RequestDto.builder()
                .name("The Royal Palace")
                .propertyType("Heritage Hotel")
                .propertyClassification("5_STAR_DELUXE")
                .propertySegment("Ultra Luxury")
                .description("Heritage property with 5-star deluxe recognized classification.")
                .images(List.of(HotelImageDto.builder().imageUrl("/cover.jpg").isCover(true).build()))
                .build();

        HttpEntity<HotelStep1RequestDto> entity = new HttpEntity<>(request, createAuthHeaders(managerToken));
        ResponseEntity<HotelStep1ResponseDto> response = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", entity, HotelStep1ResponseDto.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("5_STAR_DELUXE", response.getBody().getPropertyClassification());
        assertEquals(5, response.getBody().getStarRating());
    }

    @Test
    @DisplayName("Recognized Property Classification: Other requires customClassification specification")
    public void testClassification_Other_RequiresCustom() {
        // Without custom classification -> should fail
        HotelStep1RequestDto requestNoCustom = HotelStep1RequestDto.builder()
                .name("Eco Glamping")
                .propertyType("Resort")
                .propertyClassification("OTHER")
                .customClassification("")
                .propertySegment("Comfort")
                .description("Eco retreat with eco-tourism classification.")
                .images(List.of(HotelImageDto.builder().imageUrl("/cover.jpg").isCover(true).build()))
                .build();

        HttpEntity<HotelStep1RequestDto> entity1 = new HttpEntity<>(requestNoCustom, createAuthHeaders(managerToken));
        ResponseEntity<String> response1 = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", entity1, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response1.getStatusCode());
        assertTrue(response1.getBody().contains("Custom classification must be specified"));

        // With custom classification -> succeeds
        requestNoCustom.setCustomClassification("Green Leaf Certified Eco-Lodge");
        HttpEntity<HotelStep1RequestDto> entity2 = new HttpEntity<>(requestNoCustom, createAuthHeaders(managerToken));
        ResponseEntity<HotelStep1ResponseDto> response2 = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", entity2, HotelStep1ResponseDto.class);
        assertEquals(HttpStatus.CREATED, response2.getStatusCode());
        assertEquals("OTHER", response2.getBody().getPropertyClassification());
        assertEquals("Green Leaf Certified Eco-Lodge", response2.getBody().getCustomClassification());
    }

    @Test
    @DisplayName("Property Segment: Standard, Comfort, Premium, Luxury, Ultra Luxury accepted; invalid rejected")
    public void testPropertySegment_Validation() {
        // 1. Invalid segment rejected
        HotelStep1RequestDto request = HotelStep1RequestDto.builder()
                .name("Segment Test Hotel")
                .propertyType("Hotel")
                .propertySegment("InvalidSegment")
                .description("Test description")
                .images(List.of(HotelImageDto.builder().imageUrl("/cover.jpg").isCover(true).build()))
                .build();

        HttpEntity<HotelStep1RequestDto> entity = new HttpEntity<>(request, createAuthHeaders(managerToken));
        ResponseEntity<String> response = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", entity, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Approved options are: Standard, Comfort, Premium, Luxury, Ultra Luxury"));

        // 2. All 5 approved segments accepted
        List<String> approvedSegments = List.of("Standard", "Comfort", "Premium", "Luxury", "Ultra Luxury");
        for (String seg : approvedSegments) {
            HotelStep1RequestDto okReq = HotelStep1RequestDto.builder()
                    .name("Hotel " + seg)
                    .propertyType("Hotel")
                    .propertySegment(seg)
                    .description("Test description for " + seg)
                    .images(List.of(HotelImageDto.builder().imageUrl("/cover.jpg").isCover(true).build()))
                    .build();

            ResponseEntity<HotelStep1ResponseDto> resOk = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1",
                    new HttpEntity<>(okReq, createAuthHeaders(managerToken)), HotelStep1ResponseDto.class);
            assertEquals(HttpStatus.CREATED, resOk.getStatusCode());
            assertEquals(seg.trim().toUpperCase().replace(" ", "_"), resOk.getBody().getPropertySegment());
        }
    }

    @Test
    @DisplayName("Short Description: Required and strictly enforced to maximum 300 characters")
    public void testDescription_LengthConstraint() {
        String over300 = "A".repeat(301);
        HotelStep1RequestDto request = HotelStep1RequestDto.builder()
                .name("Description Test Hotel")
                .propertyType("Hotel")
                .propertySegment("Comfort")
                .description(over300)
                .images(List.of(HotelImageDto.builder().imageUrl("/cover.jpg").isCover(true).build()))
                .build();

        HttpEntity<HotelStep1RequestDto> entity = new HttpEntity<>(request, createAuthHeaders(managerToken));
        ResponseEntity<String> response = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", entity, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("cannot exceed 300 characters"));
    }

    // =========================================================================
    // 3. IMAGE MANAGEMENT & STORAGE
    // =========================================================================

    @Test
    @DisplayName("Image Gallery: Enforces 1 cover image, up to 4 images max, rejects > 4 images")
    public void testImageGalleryConstraints() {
        // Test no cover image
        HotelStep1RequestDto noCover = HotelStep1RequestDto.builder()
                .name("No Cover Hotel")
                .propertyType("Hotel")
                .propertySegment("Standard")
                .description("Test description")
                .images(List.of(
                        HotelImageDto.builder().imageUrl("/img1.jpg").isCover(false).build(),
                        HotelImageDto.builder().imageUrl("/img2.jpg").isCover(false).build()
                ))
                .build();
        ResponseEntity<String> resNoCover = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1",
                new HttpEntity<>(noCover, createAuthHeaders(managerToken)), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resNoCover.getStatusCode());
        assertTrue(resNoCover.getBody().contains("Exactly one image must be designated as the Cover Image"));

        // Test > 4 images
        HotelStep1RequestDto fiveImages = HotelStep1RequestDto.builder()
                .name("Five Images Hotel")
                .propertyType("Hotel")
                .propertySegment("Standard")
                .description("Test description")
                .images(List.of(
                        HotelImageDto.builder().imageUrl("/img1.jpg").isCover(true).build(),
                        HotelImageDto.builder().imageUrl("/img2.jpg").isCover(false).build(),
                        HotelImageDto.builder().imageUrl("/img3.jpg").isCover(false).build(),
                        HotelImageDto.builder().imageUrl("/img4.jpg").isCover(false).build(),
                        HotelImageDto.builder().imageUrl("/img5.jpg").isCover(false).build()
                ))
                .build();
        ResponseEntity<String> resFive = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1",
                new HttpEntity<>(fiveImages, createAuthHeaders(managerToken)), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resFive.getStatusCode());
        assertTrue(resFive.getBody().contains("Maximum 4 images") || resFive.getBody().contains("Between 1 and 4 images"));
    }

    @Test
    @DisplayName("Image Storage Service: Rejects file > 1 MB, validates magic bytes")
    public void testImageStorage_Validation() {
        // 1. File size > 1 MB (1,048,577 bytes)
        byte[] largeBytes = new byte[1_048_577];
        // JPEG magic bytes: FF D8 FF
        largeBytes[0] = (byte) 0xFF;
        largeBytes[1] = (byte) 0xD8;
        largeBytes[2] = (byte) 0xFF;
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.jpg", "image/jpeg", largeBytes);

        IllegalArgumentException exSize = assertThrows(IllegalArgumentException.class, () -> {
            imageStorageService.storeImage(largeFile, "cover");
        });
        assertTrue(exSize.getMessage().contains("exceeds the strict limit of 1 MB"));

        // 2. Corrupted/Fake image file (e.g., text file disguised as .png)
        byte[] fakeBytes = "Hello this is not an image".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile fakeFile = new MockMultipartFile("file", "fake.png", "image/png", fakeBytes);

        IllegalArgumentException exMagic = assertThrows(IllegalArgumentException.class, () -> {
            imageStorageService.storeImage(fakeFile, "cover");
        });
        assertTrue(exMagic.getMessage().contains("Magic byte verification failed"));

        // 3. Valid PNG image
        byte[] validPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile validFile = new MockMultipartFile("file", "valid.png", "image/png", validPng);
        String filename = imageStorageService.storeImage(validFile, "cover");
        assertNotNull(filename);
        assertTrue(filename.startsWith("hotel_cover_"));
        assertTrue(filename.endsWith(".png"));

        // Clean up
        assertTrue(imageStorageService.deleteImage(filename));
    }

    // =========================================================================
    // 4. DRAFT ISOLATION & RESUMING
    // =========================================================================

    @Test
    @DisplayName("Draft Isolation: Manager B cannot view or update Manager A's draft")
    public void testDraftIsolation_BetweenManagers() {
        // Manager 1 creates draft
        HotelStep1RequestDto request = HotelStep1RequestDto.builder()
                .name("Manager 1 Secret Draft")
                .propertyType("Villa")
                .propertySegment("Luxury")
                .description("Private luxury villa.")
                .images(List.of(HotelImageDto.builder().imageUrl("/cover.jpg").isCover(true).build()))
                .build();

        HttpEntity<HotelStep1RequestDto> entity = new HttpEntity<>(request, createAuthHeaders(managerToken));
        ResponseEntity<HotelStep1ResponseDto> response = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", entity, HotelStep1ResponseDto.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Long draftId = response.getBody().getId();

        // Manager 2 attempts to retrieve Manager 1's draft -> 403 Forbidden
        HttpEntity<Void> getEntity = new HttpEntity<>(createAuthHeaders(manager2Token));
        ResponseEntity<String> getRes = restTemplate.exchange(getBaseUrl() + "/draft/" + draftId, HttpMethod.GET, getEntity, String.class);
        assertEquals(HttpStatus.FORBIDDEN, getRes.getStatusCode());

        // Manager 2 attempts to update Manager 1's draft -> 403 Forbidden
        request.setId(draftId);
        request.setName("Hacked by Manager 2");
        HttpEntity<HotelStep1RequestDto> updateEntity = new HttpEntity<>(request, createAuthHeaders(manager2Token));
        ResponseEntity<String> updateRes = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1", updateEntity, String.class);
        assertEquals(HttpStatus.FORBIDDEN, updateRes.getStatusCode());
    }

    @Test
    @DisplayName("Resuming & Updating Draft: Updating existing draft retains ID and updates fields and gallery")
    public void testUpdateExistingDraft_Success() {
        // 1. Initial draft creation
        HotelStep1RequestDto initial = HotelStep1RequestDto.builder()
                .name("Initial Draft Name")
                .propertyType("Hotel")
                .propertySegment("Comfort")
                .description("Initial description.")
                .images(List.of(HotelImageDto.builder().imageUrl("/old_cover.jpg").isCover(true).displayOrder(0).build()))
                .build();

        ResponseEntity<HotelStep1ResponseDto> res1 = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1",
                new HttpEntity<>(initial, createAuthHeaders(managerToken)), HotelStep1ResponseDto.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());
        Long draftId = res1.getBody().getId();

        // 2. Update with new images and fields
        HotelStep1RequestDto update = HotelStep1RequestDto.builder()
                .id(draftId)
                .name("Updated Draft Name")
                .propertyType("Resort")
                .propertyClassification("4_STAR")
                .propertySegment("Premium")
                .brandName("Sunrise Luxury Resorts")
                .description("Updated description with new details.")
                .images(List.of(
                        HotelImageDto.builder().imageUrl("/new_cover.jpg").isCover(true).displayOrder(0).build(),
                        HotelImageDto.builder().imageUrl("/new_gallery1.jpg").isCover(false).displayOrder(1).build()
                ))
                .build();

        ResponseEntity<HotelStep1ResponseDto> res2 = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1",
                new HttpEntity<>(update, createAuthHeaders(managerToken)), HotelStep1ResponseDto.class);
        assertEquals(HttpStatus.CREATED, res2.getStatusCode());
        assertEquals(draftId, res2.getBody().getId());
        assertEquals("Updated Draft Name", res2.getBody().getName());
        assertEquals("Resort", res2.getBody().getPropertyType());
        assertEquals("4_STAR", res2.getBody().getPropertyClassification());
        assertEquals(4, res2.getBody().getStarRating());
        assertEquals(2, res2.getBody().getImages().size());
        assertEquals("/new_cover.jpg", res2.getBody().getCoverImageUrl());
    }

    // =========================================================================
    // 5. IMAGE SECURITY, LIFECYCLE & CLEANUP
    // =========================================================================

    @Test
    @DisplayName("Image Formats: Accepts .jpg, .jpeg, .png, .webp with valid magic bytes")
    public void testImageFormats_AllSupportedAccepted() {
        // JPEG magic bytes: FF D8 FF
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0};
        MockMultipartFile jpgFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegBytes);
        String nameJpg = imageStorageService.storeImage(jpgFile, "gallery");
        assertNotNull(nameJpg);
        assertTrue(nameJpg.endsWith(".jpg"));
        imageStorageService.deleteImage(nameJpg);

        MockMultipartFile jpegFile = new MockMultipartFile("file", "test.jpeg", "image/jpeg", jpegBytes);
        String nameJpeg = imageStorageService.storeImage(jpegFile, "gallery");
        assertNotNull(nameJpeg);
        assertTrue(nameJpeg.endsWith(".jpg"));
        imageStorageService.deleteImage(nameJpeg);

        // PNG magic bytes: 89 50 4E 47
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile pngFile = new MockMultipartFile("file", "test.png", "image/png", pngBytes);
        String namePng = imageStorageService.storeImage(pngFile, "gallery");
        assertNotNull(namePng);
        assertTrue(namePng.endsWith(".png"));
        imageStorageService.deleteImage(namePng);

        // WebP magic bytes: RIFF....WEBP
        byte[] webpBytes = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        MockMultipartFile webpFile = new MockMultipartFile("file", "test.webp", "image/webp", webpBytes);
        String nameWebp = imageStorageService.storeImage(webpFile, "gallery");
        assertNotNull(nameWebp);
        assertTrue(nameWebp.endsWith(".webp"));
        imageStorageService.deleteImage(nameWebp);
    }

    @Test
    @DisplayName("Image Security: Manager B cannot delete Manager A's draft image (403 Forbidden)")
    public void testImageSecurity_ManagerIsolation() {
        // Manager 1 uploads an image via API
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(pngBytes) {
            @Override
            public String getFilename() {
                return "manager1_photo.png";
            }
        });

        HttpHeaders headers1 = new HttpHeaders();
        headers1.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers1.set("Authorization", "Bearer " + managerToken);

        ResponseEntity<Map> uploadRes = restTemplate.postForEntity(getBaseUrl() + "/images/upload",
                new HttpEntity<>(body, headers1), Map.class);
        assertEquals(HttpStatus.CREATED, uploadRes.getStatusCode());
        String filename = (String) uploadRes.getBody().get("filename");
        assertNotNull(filename);

        // Manager 2 attempts to delete Manager 1's image -> 403 Forbidden
        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("Authorization", "Bearer " + manager2Token);
        ResponseEntity<Map> deleteRes = restTemplate.exchange(getBaseUrl() + "/images/" + filename,
                HttpMethod.DELETE, new HttpEntity<>(headers2), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, deleteRes.getStatusCode());

        // Manager 1 successfully deletes own image -> 200 OK
        ResponseEntity<Map> ownerDeleteRes = restTemplate.exchange(getBaseUrl() + "/images/" + filename,
                HttpMethod.DELETE, new HttpEntity<>(createAuthHeaders(managerToken)), Map.class);
        assertEquals(HttpStatus.OK, ownerDeleteRes.getStatusCode());
    }

    @Test
    @DisplayName("Image Lifecycle: Replaced image is automatically removed from disk on draft update")
    public void testReplacedImageCleanup_OnDraftUpdate() {
        // Upload 2 images
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile fileA = new MockMultipartFile("file", "imageA.png", "image/png", pngBytes);
        MockMultipartFile fileB = new MockMultipartFile("file", "imageB.png", "image/png", pngBytes);
        String nameA = imageStorageService.storeImage(fileA, "m1001_cover");
        String nameB = imageStorageService.storeImage(fileB, "m1001_gallery");

        // Save draft with A (cover) and B
        HotelStep1RequestDto draft1 = HotelStep1RequestDto.builder()
                .name("Disk Cleanup Test Hotel")
                .propertyType("Hotel")
                .propertySegment("Standard")
                .description("Testing disk cleanup of replaced images.")
                .images(List.of(
                        HotelImageDto.builder().imageUrl("/api/hotels/images/" + nameA).isCover(true).displayOrder(0).build(),
                        HotelImageDto.builder().imageUrl("/api/hotels/images/" + nameB).isCover(false).displayOrder(1).build()
                ))
                .build();

        ResponseEntity<HotelStep1ResponseDto> res1 = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1",
                new HttpEntity<>(draft1, createAuthHeaders(managerToken)), HotelStep1ResponseDto.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());
        Long draftId = res1.getBody().getId();

        // Verify both exist on disk
        assertNotNull(imageStorageService.loadAsResource(nameA));
        assertNotNull(imageStorageService.loadAsResource(nameB));

        // Update draft: Keep A, remove B
        HotelStep1RequestDto updateDraft = HotelStep1RequestDto.builder()
                .id(draftId)
                .name("Disk Cleanup Test Hotel Updated")
                .propertyType("Hotel")
                .propertySegment("Standard")
                .description("Updated without image B.")
                .images(List.of(
                        HotelImageDto.builder().imageUrl("/api/hotels/images/" + nameA).isCover(true).displayOrder(0).build()
                ))
                .build();

        ResponseEntity<HotelStep1ResponseDto> res2 = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1",
                new HttpEntity<>(updateDraft, createAuthHeaders(managerToken)), HotelStep1ResponseDto.class);
        assertEquals(HttpStatus.CREATED, res2.getStatusCode());

        // Verify A still exists on disk, but B was deleted from disk!
        assertNotNull(imageStorageService.loadAsResource(nameA));
        assertThrows(Exception.class, () -> imageStorageService.loadAsResource(nameB));

        // Clean up A
        imageStorageService.deleteImage(nameA);
    }

    @Test
    @DisplayName("Draft Deletion: DELETE /draft/{draftId} deletes draft and associated disk images")
    public void testDeleteDraft_CleansUpDiskAndDatabase() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile("file", "draftImg.png", "image/png", pngBytes);
        String fname = imageStorageService.storeImage(file, "m1001_cover");

        HotelStep1RequestDto draft = HotelStep1RequestDto.builder()
                .name("To Be Deleted Draft")
                .propertyType("Resort")
                .propertySegment("Premium")
                .description("Temporary draft to test deletion.")
                .images(List.of(HotelImageDto.builder().imageUrl("/api/hotels/images/" + fname).isCover(true).build()))
                .build();

        ResponseEntity<HotelStep1ResponseDto> createRes = restTemplate.postForEntity(getBaseUrl() + "/draft/step-1",
                new HttpEntity<>(draft, createAuthHeaders(managerToken)), HotelStep1ResponseDto.class);
        assertEquals(HttpStatus.CREATED, createRes.getStatusCode());
        Long draftId = createRes.getBody().getId();

        // Manager 2 cannot delete Manager 1's draft -> 403 Forbidden
        ResponseEntity<Map> del2 = restTemplate.exchange(getBaseUrl() + "/draft/" + draftId,
                HttpMethod.DELETE, new HttpEntity<>(createAuthHeaders(manager2Token)), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, del2.getStatusCode());

        // Manager 1 deletes draft -> 200 OK
        ResponseEntity<Map> del1 = restTemplate.exchange(getBaseUrl() + "/draft/" + draftId,
                HttpMethod.DELETE, new HttpEntity<>(createAuthHeaders(managerToken)), Map.class);
        assertEquals(HttpStatus.OK, del1.getStatusCode());

        // Verify draft removed from DB
        assertFalse(hotelRepository.existsById(draftId));
        // Verify image removed from disk
        assertThrows(Exception.class, () -> imageStorageService.loadAsResource(fname));
    }

    @Test
    @DisplayName("JSON Deserialization: Accepts raw JSON with isCover, cover, and auto-heals cover")
    public void testJsonDeserialization_IsCoverProperty() {
        // 1. Raw JSON with "isCover": true
        String jsonPayload1 = "{\n" +
                "  \"name\": \"Raw JSON Hotel\",\n" +
                "  \"propertyType\": \"Hotel\",\n" +
                "  \"propertySegment\": \"Luxury\",\n" +
                "  \"description\": \"A lovely hotel with raw JSON serialization.\",\n" +
                "  \"images\": [\n" +
                "    {\"imageUrl\": \"/api/hotels/images/test1.jpg\", \"isCover\": true, \"displayOrder\": 0},\n" +
                "    {\"imageUrl\": \"/api/hotels/images/test2.jpg\", \"isCover\": false, \"displayOrder\": 1}\n" +
                "  ]\n" +
                "}";

        HttpHeaders headers = createAuthHeaders(managerToken);
        ResponseEntity<HotelStep1ResponseDto> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/draft/step-1",
                new HttpEntity<>(jsonPayload1, headers),
                HotelStep1ResponseDto.class
        );
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());
        assertNotNull(res1.getBody());
        assertEquals("/api/hotels/images/test1.jpg", res1.getBody().getCoverImageUrl());
        assertTrue(res1.getBody().getImages().get(0).isCover());
        assertFalse(res1.getBody().getImages().get(1).isCover());

        // 2. Raw JSON with "cover": true
        String jsonPayload2 = "{\n" +
                "  \"name\": \"Alt Cover JSON Hotel\",\n" +
                "  \"propertyType\": \"Resort\",\n" +
                "  \"propertySegment\": \"Comfort\",\n" +
                "  \"description\": \"Another lovely hotel testing alternate cover property.\",\n" +
                "  \"images\": [\n" +
                "    {\"imageUrl\": \"/api/hotels/images/alt1.jpg\", \"cover\": true, \"displayOrder\": 0}\n" +
                "  ]\n" +
                "}";

        ResponseEntity<HotelStep1ResponseDto> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/draft/step-1",
                new HttpEntity<>(jsonPayload2, headers),
                HotelStep1ResponseDto.class
        );
        assertEquals(HttpStatus.CREATED, res2.getStatusCode());
        assertNotNull(res2.getBody());
        assertEquals("/api/hotels/images/alt1.jpg", res2.getBody().getCoverImageUrl());
        assertTrue(res2.getBody().getImages().get(0).isCover());
    }
}
