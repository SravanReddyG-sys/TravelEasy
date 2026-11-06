test_path = r"hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java"
with open(test_path, "r", encoding="utf-8") as f:
    code = f.read()

# 1. Update testClassification_HeritageNotDirectOption
old_class_heritage = """    @Test
    @DisplayName("Recognized Property Classification: 5 Star Deluxe sets starRating=5 and retains 5_STAR_DELUXE")
    public void testClassification_5StarDeluxe() {"""

new_class_heritage = """    @Test
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
    public void testClassification_5StarDeluxe() {"""

assert old_class_heritage in code, "old_class_heritage not found"
code = code.replace(old_class_heritage, new_class_heritage, 1)

# 2. Update testPropertySegment_Validation to test all 5 approved segments
old_seg_test = """    @Test
    @DisplayName("Mandatory Property Segment validation: Standard, Comfort, Premium, Luxury, Ultra Luxury accepted")
    public void testPropertySegment_Validation() {
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
        assertTrue(response.getBody().contains("Unsupported property segment"));
    }"""

new_seg_test = """    @Test
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
    }"""

assert old_seg_test in code, "old_seg_test not found"
code = code.replace(old_seg_test, new_seg_test, 1)

# 3. Add tests for Image format compatibility (.jpg, .jpeg, .png, .webp), Image Ownership security, Replaced image cleanup, and Draft deletion at end of file
new_tests_to_append = """
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
                HttpMethod.DELETE, new HttpEntity<>(headers1), Map.class);
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
}
"""

last_brace_idx = code.rfind("}")
code = code[:last_brace_idx] + new_tests_to_append

with open(test_path, "w", encoding="utf-8") as f:
    f.write(code)

print("Updated HotelStep1RegistrationTest.java successfully!")
