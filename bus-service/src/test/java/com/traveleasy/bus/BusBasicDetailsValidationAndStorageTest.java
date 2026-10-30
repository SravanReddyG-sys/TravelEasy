package com.traveleasy.bus;

import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.service.BusImageStorageService;
import com.traveleasy.bus.service.BusValidationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Year;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BusBasicDetailsValidationAndStorageTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private BusImageStorageService imageStorageService;

    @Autowired
    private BusValidationService validationService;

    private static final String SECRET = "TravelEasySuperSecretKeyForJWTSigning12345678901234567890";

    private String createToken(Long userId, String role, String email) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(key)
                .compact();
    }

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/buses";
    }

    // ==================== IMAGE STORAGE SERVICE TESTS ====================

    @Test
    @DisplayName("Image Storage: Successfully store and load valid JPEG image")
    void testImageStorage_ValidJpeg() {
        // Valid JPEG header: FF D8 FF E0
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01};
        MockMultipartFile file = new MockMultipartFile("file", "exterior.jpg", "image/jpeg", jpegBytes);

        String filename = imageStorageService.storeImage(file, "exterior");
        assertNotNull(filename);
        assertTrue(filename.startsWith("exterior_"));
        assertTrue(filename.endsWith(".jpg"));

        Resource resource = imageStorageService.loadAsResource(filename);
        assertNotNull(resource);
        assertTrue(resource.exists());

        // Cleanup
        boolean deleted = imageStorageService.deleteImage(filename);
        assertTrue(deleted);
    }

    @Test
    @DisplayName("Image Storage: Successfully store and load valid PNG image")
    void testImageStorage_ValidPng() {
        // Valid PNG header: 89 50 4E 47 0D 0A 1A 0A
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
        MockMultipartFile file = new MockMultipartFile("file", "interior.png", "image/png", pngBytes);

        String filename = imageStorageService.storeImage(file, "interior");
        assertNotNull(filename);
        assertTrue(filename.startsWith("interior_"));
        assertTrue(filename.endsWith(".png"));

        // Cleanup
        assertTrue(imageStorageService.deleteImage(filename));
    }

    @Test
    @DisplayName("Image Storage: Reject file exceeding 1.5 MB limit")
    void testImageStorage_RejectOversized() {
        byte[] oversizedBytes = new byte[(int) (BusImageStorageService.MAX_FILE_SIZE_BYTES + 10)];
        oversizedBytes[0] = (byte) 0xFF;
        oversizedBytes[1] = (byte) 0xD8;
        oversizedBytes[2] = (byte) 0xFF;
        oversizedBytes[3] = (byte) 0xE0;
        MockMultipartFile file = new MockMultipartFile("file", "large.jpg", "image/jpeg", oversizedBytes);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                imageStorageService.storeImage(file, "exterior")
        );
        assertTrue(ex.getMessage().contains("exceeds the strict limit of 1.5 MB"));
    }

    @Test
    @DisplayName("Image Storage: Reject unsupported format and corrupted magic bytes")
    void testImageStorage_RejectCorrupted() {
        byte[] fakeBytes = "Not an image content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", fakeBytes);

        assertThrows(IllegalArgumentException.class, () ->
                imageStorageService.storeImage(file, "exterior")
        );
    }

    // ==================== VALIDATION SERVICE TESTS ====================

    @Test
    @DisplayName("Validation Service: Registration number format tests (Standard Indian and BH-series)")
    void testValidation_RegistrationFormats() {
        // Valid standard registrations
        assertTrue(validationService.isValidRegistrationNumber("KA01AB1234"));
        assertTrue(validationService.isValidRegistrationNumber("KA-01-AB-1234"));
        assertTrue(validationService.isValidRegistrationNumber("AP39TV1234"));
        assertTrue(validationService.isValidRegistrationNumber("DL1C1234"));
        assertTrue(validationService.isValidRegistrationNumber("MH02CR9999"));

        // Valid BH-series registrations
        assertTrue(validationService.isValidRegistrationNumber("22BH1234AA"));
        assertTrue(validationService.isValidRegistrationNumber("23-BH-5678-B"));

        // Invalid registrations
        assertFalse(validationService.isValidRegistrationNumber("INVALID123"));
        assertFalse(validationService.isValidRegistrationNumber("K1234"));
        assertFalse(validationService.isValidRegistrationNumber("123456789"));
        assertFalse(validationService.isValidRegistrationNumber(""));
        assertFalse(validationService.isValidRegistrationNumber(null));
    }

    @Test
    @DisplayName("Validation Service: Chassis number tests (17 chars, no I, O, Q)")
    void testValidation_ChassisNumber() {
        // Valid 17-char chassis
        assertTrue(BusValidationService.CHASSIS_NUMBER_PATTERN.matcher("YV3T2EU527N123456").matches());
        assertTrue(BusValidationService.CHASSIS_NUMBER_PATTERN.matcher("1M8GDM9A_KP042788".replace("_", "1")).matches());

        // Invalid: contains 'I'
        assertFalse(BusValidationService.CHASSIS_NUMBER_PATTERN.matcher("YV3T2EU527NI23456").matches());
        // Invalid: contains 'O'
        assertFalse(BusValidationService.CHASSIS_NUMBER_PATTERN.matcher("YV3T2EU527NO23456").matches());
        // Invalid: contains 'Q'
        assertFalse(BusValidationService.CHASSIS_NUMBER_PATTERN.matcher("YV3T2EU527NQ23456").matches());
        // Invalid: length 16
        assertFalse(BusValidationService.CHASSIS_NUMBER_PATTERN.matcher("YV3T2EU527N12345").matches());
        // Invalid: length 18
        assertFalse(BusValidationService.CHASSIS_NUMBER_PATTERN.matcher("YV3T2EU527N1234567").matches());
    }

    @Test
    @DisplayName("Validation Service: Full Basic Details validation tests")
    void testValidation_FullBasicDetails() {
        Bus validBus = Bus.builder()
                .busNumber("KA-01-AB-9876")
                .busName("TravelEasy Express")
                .manufacturer("Volvo")
                .model("Volvo 9600")
                .busType("Seater")
                .fuelType("Diesel")
                .chassisNumber("YV3T2EU527N123456")
                .yearOfManufacturing(2022)
                .engineNumber("D13K520123456")
                .emissionStandard("BS VI")
                .color("White with Blue")
                .busCategory("Stage Carriage (Contract)")
                .facilityType("AC")
                .exteriorImageUrl("/api/buses/images/ext.jpg")
                .interiorImageUrl("/api/buses/images/int.jpg")
                .build();

        Map<String, String> errors = validationService.validateBasicDetails(validBus, true);
        assertTrue(errors.isEmpty(), "Expected 0 errors for valid bus details: " + errors);

        // Test future manufacturing year rejection
        validBus.setYearOfManufacturing(Year.now().getValue() + 1);
        errors = validationService.validateBasicDetails(validBus, true);
        assertTrue(errors.containsKey("yearOfManufacturing"));

        // Test invalid manufacturer
        validBus.setYearOfManufacturing(2022);
        validBus.setManufacturer("Unknown Motors");
        errors = validationService.validateBasicDetails(validBus, true);
        assertTrue(errors.containsKey("manufacturer"));

        // Test missing exterior image
        validBus.setManufacturer("Volvo");
        validBus.setExteriorImageUrl(null);
        errors = validationService.validateBasicDetails(validBus, true);
        assertTrue(errors.containsKey("exteriorImage"));
    }

    // ==================== REST ENDPOINT INTEGRATION TESTS ====================

    @Test
    @DisplayName("API: Check Registration Number availability")
    void testApi_CheckRegistrationNumber() {
        String url = getBaseUrl() + "/check-registration?busNumber=KA01AB9999";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("validFormat"));
        assertEquals("KA01AB9999", response.getBody().get("normalized"));
    }

    @Test
    @DisplayName("API: Validate Basic Details endpoint (Valid and Invalid scenarios)")
    void testApi_ValidateBasicDetails() {
        Long operatorId = 701L;
        String token = createToken(operatorId, "ROLE_BUS_OPERATOR", "operator701@traveleasy.com");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Valid bus payload
        Bus validBus = Bus.builder()
                .busNumber("AP39TV7777")
                .busName("Orange Express")
                .manufacturer("Scania")
                .model("Metrolink HD")
                .busType("Sleeper")
                .fuelType("Diesel")
                .chassisNumber("YV3T2EU527N654321")
                .yearOfManufacturing(2023)
                .engineNumber("DC1311512345")
                .emissionStandard("BS VI")
                .color("Red")
                .busCategory("Contract Carriage")
                .facilityType("AC")
                .exteriorImageUrl("/api/buses/images/ext_test.jpg")
                .interiorImageUrl("/api/buses/images/int_test.jpg")
                .build();

        HttpEntity<Bus> entity = new HttpEntity<>(validBus, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/validate-basic-details",
                entity,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("valid"));

        // Invalid bus payload (empty chassis and future year)
        validBus.setChassisNumber("");
        validBus.setYearOfManufacturing(2099);
        HttpEntity<Bus> invalidEntity = new HttpEntity<>(validBus, headers);
        ResponseEntity<Map> invalidResponse = restTemplate.postForEntity(
                getBaseUrl() + "/validate-basic-details",
                invalidEntity,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, invalidResponse.getStatusCode());
        assertNotNull(invalidResponse.getBody());
        assertEquals(false, invalidResponse.getBody().get("valid"));
        Map errors = (Map) invalidResponse.getBody().get("errors");
        assertNotNull(errors);
        assertTrue(errors.containsKey("chassisNumber"));
        assertTrue(errors.containsKey("yearOfManufacturing"));
    }

    @Test
    @DisplayName("API: Multipart Image Upload and Download lifecycle")
    void testApi_ImageUploadAndDownload() {
        Long operatorId = 701L;
        String token = createToken(operatorId, "ROLE_BUS_OPERATOR", "operator701@traveleasy.com");

        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.set("Authorization", "Bearer " + token);
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Valid JPEG header: FF D8 FF E0
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01};

        ByteArrayResource fileResource = new ByteArrayResource(jpegBytes) {
            @Override
            public String getFilename() {
                return "bus_exterior.jpg";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("category", "exterior");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, uploadHeaders);
        ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(
                getBaseUrl() + "/images/upload",
                requestEntity,
                Map.class
        );

        assertEquals(HttpStatus.CREATED, uploadResponse.getStatusCode());
        assertNotNull(uploadResponse.getBody());
        String filename = (String) uploadResponse.getBody().get("filename");
        String imageUrl = (String) uploadResponse.getBody().get("imageUrl");
        assertNotNull(filename);
        assertNotNull(imageUrl);
        assertTrue(imageUrl.contains(filename));

        // Test GET Image
        ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
                getBaseUrl() + "/images/" + filename,
                byte[].class
        );
        assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
        assertEquals(MediaType.IMAGE_JPEG, downloadResponse.getHeaders().getContentType());
        assertNotNull(downloadResponse.getBody());
        assertEquals(jpegBytes.length, downloadResponse.getBody().length);

        // Test DELETE Image
        HttpHeaders deleteHeaders = new HttpHeaders();
        deleteHeaders.set("Authorization", "Bearer " + token);
        HttpEntity<Void> deleteEntity = new HttpEntity<>(deleteHeaders);
        ResponseEntity<Map> deleteResponse = restTemplate.exchange(
                getBaseUrl() + "/images/" + filename,
                HttpMethod.DELETE,
                deleteEntity,
                Map.class
        );
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());

        // Verify gone
        ResponseEntity<byte[]> verifyGone = restTemplate.getForEntity(
                getBaseUrl() + "/images/" + filename,
                byte[].class
        );
        assertEquals(HttpStatus.NOT_FOUND, verifyGone.getStatusCode());
    }
}
