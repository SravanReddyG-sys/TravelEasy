controller_path = r"hotel-service/src/main/java/com/traveleasy/hotel/controller/HotelController.java"
with open(controller_path, "r", encoding="utf-8") as f:
    code = f.read()

# 1. Add imports if missing
if "import com.traveleasy.hotel.model.HotelImage;" not in code:
    code = code.replace("import com.traveleasy.hotel.model.HotelProperty;",
                        "import com.traveleasy.hotel.model.HotelProperty;\nimport com.traveleasy.hotel.model.HotelImage;")
if "import com.traveleasy.hotel.repository.HotelImageRepository;" not in code:
    code = code.replace("import com.traveleasy.hotel.repository.HotelPropertyRepository;",
                        "import com.traveleasy.hotel.repository.HotelPropertyRepository;\nimport com.traveleasy.hotel.repository.HotelImageRepository;")

# 2. Add field to HotelController
field_target = "    private final HotelImageStorageService imageStorageService;"
new_field = "    private final HotelImageStorageService imageStorageService;\n    private final HotelImageRepository hotelImageRepository;"
if "private final HotelImageRepository hotelImageRepository;" not in code:
    assert field_target in code, "field_target not found"
    code = code.replace(field_target, new_field, 1)

# 3. Add deleteDraft endpoint right under getMyDrafts
drafts_old_target = """    @GetMapping("/drafts")
    public ResponseEntity<?> getMyDrafts(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "view property registration drafts");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        List<HotelStep1ResponseDto> drafts = hotelPropertyService.getDraftsByManager(principal);
        return ResponseEntity.ok(drafts);
    }"""

drafts_new_target = drafts_old_target + """

    @DeleteMapping("/draft/{draftId}")
    public ResponseEntity<?> deleteDraft(
            @PathVariable Long draftId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "delete property draft");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        hotelPropertyService.deleteDraft(draftId, principal);
        return ResponseEntity.ok(Map.of("message", "Property draft and associated images deleted successfully", "draftId", draftId));
    }"""

if "@DeleteMapping(\"/draft/{draftId}\")" not in code:
    assert drafts_old_target in code, "drafts_old_target not found"
    code = code.replace(drafts_old_target, drafts_new_target, 1)

# 4. Update uploadImage and deleteImage methods
upload_old_start = "    @PostMapping(\"/images/upload\")"
upload_start_idx = code.find(upload_old_start)
assert upload_start_idx != -1, "upload_start_idx not found"

delete_old_end = "    // =========================================================================\n    // 1. HOTEL PROPERTY MANAGEMENT"
delete_end_idx = code.find(delete_old_end)
assert delete_end_idx != -1, "delete_end_idx not found"

new_image_endpoints = """    @PostMapping("/images/upload")
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "upload property image");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);

        try {
            // Namespace category with manager id for ownership validation on unassigned files
            String managerCategory = "m" + principal.userId() + "_" + (category != null && !category.isBlank() ? category : "gallery");
            String filename = imageStorageService.storeImage(file, managerCategory);
            String imageUrl = "/api/hotels/images/" + filename;
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "imageUrl", imageUrl,
                    "filename", filename,
                    "originalName", file.getOriginalFilename() != null ? file.getOriginalFilename() : filename,
                    "size", file.getSize()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to upload hotel image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Image upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/images/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        try {
            Resource resource = imageStorageService.loadAsResource(filename);
            String contentType = "image/jpeg";
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png")) {
                contentType = "image/png";
            } else if (lower.endsWith(".webp")) {
                contentType = "image/webp";
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(resource);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to load hotel image: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/images/{filename:.+}")
    public ResponseEntity<?> deleteImage(
            @PathVariable String filename,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "delete property image");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);

        // Security check: Verify image ownership
        List<HotelImage> matchedImages = hotelImageRepository.findByImageUrlContaining(filename);
        if (!matchedImages.isEmpty()) {
            for (HotelImage img : matchedImages) {
                HotelProperty prop = hotelRepository.findById(img.getHotelId()).orElse(null);
                if (prop != null && !principal.isAdmin() && !prop.getManagerId().equals(principal.userId())) {
                    log.warn("Security violation: Manager ID {} attempted to delete image {} belonging to property ID {} (owned by Manager ID {})",
                            principal.userId(), filename, prop.getId(), prop.getManagerId());
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Forbidden: You do not have ownership of the property associated with this image"));
                }
            }
            hotelImageRepository.deleteAll(matchedImages);
        } else {
            // Unassigned file: verify ownership via manager filename prefix if present
            if (!principal.isAdmin() && filename.startsWith("hotel_m")) {
                String expectedPrefix = "hotel_m" + principal.userId() + "_";
                if (!filename.startsWith(expectedPrefix)) {
                    log.warn("Security violation: Manager ID {} attempted to delete unassigned image {} uploaded by another manager",
                            principal.userId(), filename);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Forbidden: You did not upload this image"));
                }
            }
        }

        boolean deleted = imageStorageService.deleteImage(filename);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Image deleted successfully", "filename", filename));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Image file not found or could not be deleted"));
        }
    }

"""

code = code[:upload_start_idx] + new_image_endpoints + code[delete_end_idx:]

with open(controller_path, "w", encoding="utf-8") as f:
    f.write(code)

print("Updated HotelController.java successfully!")
