with open('hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java', 'r', encoding='utf-8') as f:
    text = f.read()

new_test = '''
    @Test
    @DisplayName("JSON Deserialization: Accepts raw JSON with isCover, cover, and auto-heals cover")
    public void testJsonDeserialization_IsCoverProperty() {
        // 1. Raw JSON with "isCover": true
        String jsonPayload1 = "{\\n" +
                "  \\\"name\\\": \\\"Raw JSON Hotel\\\",\\n" +
                "  \\\"propertyType\\\": \\\"Hotel\\\",\\n" +
                "  \\\"propertySegment\\\": \\\"Luxury\\\",\\n" +
                "  \\\"description\\\": \\\"A lovely hotel with raw JSON serialization.\\\",\\n" +
                "  \\\"images\\\": [\\n" +
                "    {\\\"imageUrl\\\": \\\"/api/hotels/images/test1.jpg\\\", \\\"isCover\\\": true, \\\"displayOrder\\\": 0},\\n" +
                "    {\\\"imageUrl\\\": \\\"/api/hotels/images/test2.jpg\\\", \\\"isCover\\\": false, \\\"displayOrder\\\": 1}\\n" +
                "  ]\\n" +
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
        String jsonPayload2 = "{\\n" +
                "  \\\"name\\\": \\\"Alt Cover JSON Hotel\\\",\\n" +
                "  \\\"propertyType\\\": \\\"Resort\\\",\\n" +
                "  \\\"propertySegment\\\": \\\"Comfort\\\",\\n" +
                "  \\\"description\\\": \\\"Another lovely hotel testing alternate cover property.\\\",\\n" +
                "  \\\"images\\\": [\\n" +
                "    {\\\"imageUrl\\\": \\\"/api/hotels/images/alt1.jpg\\\", \\\"cover\\\": true, \\\"displayOrder\\\": 0}\\n" +
                "  ]\\n" +
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
'''

idx = text.rfind('}')
if idx != -1:
    text = text[:idx] + new_test
    with open('hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java', 'w', encoding='utf-8') as f:
        f.write(text)
    print('Added test successfully')
else:
    print('Closing brace not found')
