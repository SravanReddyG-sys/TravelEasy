package com.traveleasy.hotel.controller;

import com.traveleasy.hotel.service.AddressReferenceData;

import com.traveleasy.hotel.dto.*;
import com.traveleasy.hotel.dto.ConsolidatedReservationRequestDto;
import com.traveleasy.hotel.dto.HotelReservationResponseDto;
import com.traveleasy.hotel.service.HotelReservationService;

import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.HotelImage;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.HotelImageRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
import com.traveleasy.hotel.security.HotelSecurityService;
import com.traveleasy.hotel.security.HotelSecurityService.CallerPrincipal;
import com.traveleasy.hotel.service.HotelInventoryService;
import com.traveleasy.hotel.service.HotelPricingService;
import com.traveleasy.hotel.service.HotelPropertyService;
import com.traveleasy.hotel.service.RoomBlockService;
import com.traveleasy.hotel.service.RoomTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import jakarta.validation.Valid;
import com.traveleasy.hotel.service.HotelImageStorageService;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelPropertyRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final HotelSecurityService securityService;
    private final HotelPropertyService hotelPropertyService;
    private final RoomTypeService roomTypeService;
    private final HotelInventoryService inventoryService;
    private final HotelPricingService pricingService;
    private final RoomBlockService roomBlockService;
    private final HotelImageStorageService imageStorageService;
    private final HotelImageRepository hotelImageRepository;
    private final HotelReservationService reservationService;

    // =========================================================================
    // 1A. PROPERTY REGISTRATION WIZARD DRAFTS (WP-05 STEP 1)
    // =========================================================================

    @PostMapping("/draft/step-1")
    public ResponseEntity<?> saveStep1Draft(
            @Valid @RequestBody HotelStep1RequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "save property registration draft");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelStep1ResponseDto response = hotelPropertyService.saveStep1Draft(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/draft/{draftId}")
    public ResponseEntity<?> getDraft(
            @PathVariable Long draftId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "retrieve property registration draft");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelStep1ResponseDto response = hotelPropertyService.getDraft(draftId, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/drafts")
    public ResponseEntity<?> getMyDrafts(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "view property registration drafts");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        List<HotelStep1ResponseDto> drafts = hotelPropertyService.getDraftsByManager(principal);
        return ResponseEntity.ok(drafts);
    }

    @DeleteMapping("/draft/{draftId}")
    public ResponseEntity<?> deleteDraft(
            @PathVariable Long draftId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "delete property draft");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        hotelPropertyService.deleteDraft(draftId, principal);
        return ResponseEntity.ok(Map.of("message", "Property draft and associated images deleted successfully", "draftId", draftId));
    }

    // =========================================================================
    // 1B. PROPERTY REGISTRATION WIZARD (WP-05 STEP 2: LOCATION & ADDRESS)
    // =========================================================================

    @PostMapping("/draft/{draftId}/step-2")
    public ResponseEntity<?> saveStep2DraftWithId(
            @PathVariable Long draftId,
            @Valid @RequestBody HotelStep2RequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "save property location and address draft");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelStep2ResponseDto response = hotelPropertyService.saveStep2Draft(draftId, request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/draft/step-2")
    public ResponseEntity<?> saveStep2Draft(
            @Valid @RequestBody HotelStep2RequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "save property location and address draft");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelStep2ResponseDto response = hotelPropertyService.saveStep2Draft(request.getDraftId(), request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/draft/{draftId}/step-3")
    public ResponseEntity<?> saveStep3Draft(
            @PathVariable Long draftId,
            @Valid @RequestBody HotelStep3RequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "save property information draft");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelStep3ResponseDto response = hotelPropertyService.saveStep3Draft(draftId, request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/draft/step-3")
    public ResponseEntity<?> saveStep3Draft(
            @Valid @RequestBody HotelStep3RequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "save property information draft");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelStep3ResponseDto response = hotelPropertyService.saveStep3Draft(request.getDraftId(), request, principal);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 1D. PROPERTY REGISTRATION WIZARD: STEP 4 (FACILITIES & POLICIES)
    // =========================================================================

    @PostMapping("/draft/{draftId}/step-4")
    public ResponseEntity<?> saveStep4DraftWithId(
            @PathVariable Long draftId,
            @Valid @RequestBody HotelStep4RequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "save facilities and policies draft");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelStep4ResponseDto response = hotelPropertyService.saveStep4Draft(draftId, request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/draft/step-4")
    public ResponseEntity<?> saveStep4Draft(
            @Valid @RequestBody HotelStep4RequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "save facilities and policies draft");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelStep4ResponseDto response = hotelPropertyService.saveStep4Draft(request.getDraftId(), request, principal);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 1E. PROPERTY REGISTRATION WIZARD: STEP 5 (REVIEW & SUBMIT / ACTIVATION)
    // =========================================================================

    @PostMapping("/draft/{draftId}/submit")
    public ResponseEntity<?> submitDraftWithId(
            @PathVariable Long draftId,
            @Valid @RequestBody(required = false) HotelStep5SubmitRequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "submit property registration");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelStep5ResponseDto response = hotelPropertyService.submitPropertyRegistration(draftId, request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/draft/submit")
    public ResponseEntity<?> submitDraft(
            @Valid @RequestBody HotelStep5SubmitRequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "submit property registration");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelStep5ResponseDto response = hotelPropertyService.submitPropertyRegistration(request.getDraftId(), request, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reference/countries")
    public ResponseEntity<List<String>> getSupportedCountries() {
        return ResponseEntity.ok(AddressReferenceData.getSupportedCountries());
    }

    @GetMapping("/reference/states")
    public ResponseEntity<List<String>> getStatesForCountry(@RequestParam(defaultValue = "India") String country) {
        return ResponseEntity.ok(AddressReferenceData.getStatesForCountry(country));
    }


    // =========================================================================
    // 1B. PROPERTY IMAGE MANAGEMENT & SERVING
    // =========================================================================

    @PostMapping("/images/upload")
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

    // =========================================================================
    // 1. HOTEL PROPERTY MANAGEMENT
    // =========================================================================

    @PostMapping
    public ResponseEntity<?> registerHotel(
            @RequestBody HotelRequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "register hotel property");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelProperty saved = hotelPropertyService.registerProperty(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{hotelId}")
    public ResponseEntity<?> updateHotel(
            @PathVariable Long hotelId,
            @RequestBody HotelRequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "update hotel property");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelProperty updated = hotelPropertyService.updateProperty(hotelId, request, principal);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{hotelId}/manage")
    public ResponseEntity<?> updatePropertyManage(
            @PathVariable Long hotelId,
            @Valid @RequestBody HotelManageUpdateRequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "update complete property details");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelManageDto dto = hotelPropertyService.updatePropertyFull(hotelId, request, principal);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/manage/{hotelId}")
    public ResponseEntity<?> getHotelForManagement(
            @PathVariable Long hotelId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "view property management details");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelManageDto dto = hotelPropertyService.getPropertyForManagement(hotelId, principal);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/my-hotels")
    public ResponseEntity<?> getMyHotels(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "view manager properties");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        List<HotelProperty> properties = hotelPropertyService.getPropertiesByManager(principal.userId(), principal);
        return ResponseEntity.ok(properties);
    }

    @GetMapping("/manager/{managerId}")
    public ResponseEntity<?> getHotelsByManager(
            @PathVariable Long managerId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "view manager properties");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        List<HotelProperty> properties = hotelPropertyService.getPropertiesByManager(managerId, principal);
        return ResponseEntity.ok(properties);
    }

    @PutMapping("/{hotelId}/status")
    public ResponseEntity<?> updateHotelStatus(
            @PathVariable Long hotelId,
            @RequestParam String status,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "update property status");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelProperty updated = hotelPropertyService.updatePropertyStatus(hotelId, status, principal);
        return ResponseEntity.ok(updated);
    }

    // =========================================================================
    // 2. ROOM CATEGORY MANAGEMENT (WP-02)
    // =========================================================================

    @PostMapping("/{hotelId}/rooms")
    public ResponseEntity<?> addRoomType(
            @PathVariable Long hotelId,
            @Valid @RequestBody RoomTypeRequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "add room category");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        RoomType saved = roomTypeService.createRoomType(hotelId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/rooms/{roomId}")
    public ResponseEntity<?> updateRoomType(
            @PathVariable Long roomId,
            @Valid @RequestBody RoomTypeRequestDto request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "update room category");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        RoomType updated = roomTypeService.updateRoomType(roomId, request, principal);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<?> archiveRoomType(
            @PathVariable Long roomId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "archive room category");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        RoomType archived = roomTypeService.archiveRoomType(roomId, principal);
        return ResponseEntity.ok(Map.of(
                "message", "Room category archived successfully",
                "roomId", roomId,
                "status", archived.getStatus()
        ));
    }

    @RequestMapping(value = "/rooms/{roomId}/restore", method = {RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<?> restoreRoomType(
            @PathVariable Long roomId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "restore room category");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        RoomType restored = roomTypeService.restoreRoomType(roomId, principal);
        return ResponseEntity.ok(Map.of(
                "message", "Room category restored successfully",
                "roomId", roomId,
                "status", restored.getStatus(),
                "roomTypeName", restored.getRoomTypeName()
        ));
    }

    @GetMapping("/{hotelId}/rooms")
    public ResponseEntity<List<RoomType>> getRoomTypes(
            @PathVariable Long hotelId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        CallerPrincipal principal = authHeader != null ? securityService.resolvePrincipal(authHeader) : null;
        List<RoomType> rooms = roomTypeService.getRoomTypesByHotel(hotelId, principal);
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<RoomType> getRoomType(@PathVariable Long roomId) {
        RoomType roomType = roomTypeService.getRoomTypeById(roomId);
        return ResponseEntity.ok(roomType);
    }

    // =========================================================================
    // 3. PUBLIC TRAVELLER DISCOVERY & AVAILABILITY ENDPOINTS
    // =========================================================================

    @GetMapping
    public ResponseEntity<List<HotelPublicDto>> getAllHotels() {
        List<HotelProperty> hotels = hotelRepository.findByStatus("ACTIVE");
        List<HotelPublicDto> dtoList = hotels.stream()
                .map(HotelPublicDto::fromEntity)
                .toList();
        return ResponseEntity.ok(dtoList);
    }

    /**
     * Public hotel search by destination city with optional date-wise availability filtering (BD-01).
     */
    @GetMapping("/search")
    public ResponseEntity<List<HotelPublicDto>> searchHotels(
            @RequestParam String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false, defaultValue = "1") Integer rooms,
            @RequestParam(required = false, defaultValue = "1") Integer guests) {

        List<HotelProperty> hotels = hotelRepository.findByCityIgnoreCaseAndStatus(city, "ACTIVE");

        // If stay dates are supplied, filter hotels having at least one room available for all nights
        if (checkIn != null && checkOut != null && checkOut.isAfter(checkIn)) {
            int neededRooms = (rooms != null && rooms > 0) ? rooms : 1;
            List<HotelPublicDto> availableHotels = new ArrayList<>();

            for (HotelProperty h : hotels) {
                List<RoomType> activeRooms = roomTypeRepository.findByHotelIdAndStatus(h.getId(), "ACTIVE");
                double minStartingPrice = Double.MAX_VALUE;
                boolean hasAvailableRoom = false;

                for (RoomType rt : activeRooms) {
                    StayAvailabilityResponseDto stay = inventoryService.checkStayAvailability(
                            rt.getId(), checkIn, checkOut, neededRooms);
                    if (stay.isAvailable()) {
                        hasAvailableRoom = true;
                        if (stay.getMinNightlyPrice() != null && stay.getMinNightlyPrice() < minStartingPrice) {
                            minStartingPrice = stay.getMinNightlyPrice();
                        }
                    }
                }

                if (hasAvailableRoom) {
                    HotelPublicDto dto = HotelPublicDto.fromEntity(h);
                    if (minStartingPrice != Double.MAX_VALUE) {
                        dto.setMinPrice(minStartingPrice);
                    }
                    availableHotels.add(dto);
                }
            }
            return ResponseEntity.ok(availableHotels);
        }

        List<HotelPublicDto> dtoList = hotels.stream()
                .map(HotelPublicDto::fromEntity)
                .toList();
        return ResponseEntity.ok(dtoList);
    }

    /**
     * Public hotel details lookup including active room categories and optional stay availability.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getHotelDetails(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false, defaultValue = "1") Integer rooms) {

        HotelProperty hotel = hotelRepository.findById(id).orElse(null);
        if (hotel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Hotel property not found"));
        }

        List<RoomType> activeRooms = roomTypeRepository.findByHotelIdAndStatus(id, "ACTIVE");

        if (checkIn != null && checkOut != null && checkOut.isAfter(checkIn)) {
            int neededRooms = (rooms != null && rooms > 0) ? rooms : 1;
            List<Map<String, Object>> roomsWithAvailability = new ArrayList<>();

            for (RoomType rt : activeRooms) {
                StayAvailabilityResponseDto stay = inventoryService.checkStayAvailability(
                        rt.getId(), checkIn, checkOut, neededRooms);
                Map<String, Object> roomMap = new LinkedHashMap<>();
                roomMap.put("id", rt.getId());
                roomMap.put("hotelId", rt.getHotelId());
                roomMap.put("roomTypeName", rt.getRoomTypeName());
                roomMap.put("maxOccupancy", rt.getMaxOccupancy());
                roomMap.put("basePricePerNight", rt.getPricePerNight());
                roomMap.put("amenities", rt.getAmenities());
                roomMap.put("status", rt.getStatus());
                roomMap.put("availableAcrossStay", stay.isAvailable());
                roomMap.put("availableRoomsAcrossStay", stay.getAvailableRoomsAcrossStay());
                roomMap.put("minNightlyPrice", stay.getMinNightlyPrice());
                roomMap.put("totalPriceForStay", stay.getTotalPrice());
                roomMap.put("nightlyBreakdown", stay.getNightlyBreakdown());
                roomsWithAvailability.add(roomMap);
            }

            return ResponseEntity.ok(Map.of(
                    "hotel", HotelPublicDto.fromEntity(hotel),
                    "rooms", roomsWithAvailability
            ));
        }

        return ResponseEntity.ok(Map.of(
                "hotel", HotelPublicDto.fromEntity(hotel),
                "rooms", activeRooms
        ));
    }

    /**
     * Multi-night availability calculation for all active room types in a hotel.
     */
    @GetMapping("/{id}/availability")
    public ResponseEntity<?> getHotelAvailability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false, defaultValue = "1") Integer rooms) {

        HotelProperty hotel = hotelRepository.findById(id).orElse(null);
        if (hotel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Hotel property not found"));
        }

        List<RoomType> activeRooms = roomTypeRepository.findByHotelIdAndStatus(id, "ACTIVE");
        List<StayAvailabilityResponseDto> availabilityList = new ArrayList<>();
        for (RoomType rt : activeRooms) {
            availabilityList.add(inventoryService.checkStayAvailability(rt.getId(), checkIn, checkOut, rooms));
        }
        return ResponseEntity.ok(availabilityList);
    }

    /**
     * Multi-night availability calculation for a specific room type.
     */
    @GetMapping("/rooms/{roomId}/availability")
    public ResponseEntity<?> getRoomAvailability(
            @PathVariable Long roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false, defaultValue = "1") Integer rooms) {

        StayAvailabilityResponseDto availability = inventoryService.checkStayAvailability(
                roomId, checkIn, checkOut, rooms);
        return ResponseEntity.ok(availability);
    }

    /**
     * Internal hotel details lookup for trusted microservices.
     */
    @GetMapping("/internal/{id}")
    public ResponseEntity<?> getHotelInternalDetails(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        if ((authHeader == null || authHeader.isBlank()) && (internalToken == null || internalToken.isBlank())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required for internal endpoints"));
        }

        boolean isInternal = securityService.authenticateInternalService(authHeader, internalToken, internalService);
        if (!isInternal) {
            CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
            if (principal == null || !principal.hasManagerOrAdminAccess()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Internal service authentication required"));
            }
        }

        HotelProperty hotel = hotelRepository.findById(id).orElse(null);
        if (hotel == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Hotel property not found"));
        }
        return ResponseEntity.ok(hotel);
    }

    // =========================================================================
    // 3B. CONSOLIDATED MULTI-ALLOCATION RESERVATION (WP-HB-04)
    // =========================================================================

    /**
     * Consolidated multi-allocation hotel reservation endpoint (WP-HB-04).
     * Atomically reserves all requested room allocations under a parent HotelReservation aggregate.
     * Restricted to trusted internal-service requests.
     */
    @PostMapping("/reservations")
    public ResponseEntity<?> createConsolidatedReservation(
            @Valid @RequestBody ConsolidatedReservationRequestDto dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        boolean isInternal = securityService.authenticateInternalService(authHeader, internalToken, internalService);
        if (!isInternal) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Consolidated reservation endpoint is restricted to trusted internal services"));
        }

        HotelReservationResponseDto response;
        try {
            response = reservationService.createConsolidatedReservation(dto, isInternal);
        } catch (org.springframework.dao.DataIntegrityViolationException raceEx) {
            log.warn("Database unique constraint arbitrated concurrent reservation race for bookingRef: {}", dto.getBookingRef());
            response = reservationService.handleConcurrentReservationRace(dto);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves full consolidated reservation details by booking reference (WP-HB-04).
     */
    @GetMapping("/reservations/{bookingRef}")
    public ResponseEntity<?> getReservation(
            @PathVariable String bookingRef,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        boolean isInternal = securityService.authenticateInternalService(authHeader, internalToken, internalService);
        CallerPrincipal principal = !isInternal && authHeader != null ? securityService.resolvePrincipal(authHeader) : null;

        if (!isInternal && (principal == null || !principal.hasManagerOrAdminAccess())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Internal service, admin, or manager authentication required"));
        }

        HotelReservationResponseDto response = reservationService.getReservation(bookingRef);
        return ResponseEntity.ok(response);
    }


    // =========================================================================
    // 4. DATE-WISE INVENTORY RESERVATION & RELEASE (WP-03)
    // =========================================================================

    /**
     * Date-wise multi-night stay reservation endpoint (BD-01, BD-06).
     * Atomically reserves inventory across all nights of the requested stay.
     */
    @PostMapping("/rooms/{roomId}/reserve-stay")
    public ResponseEntity<?> reserveStay(
            @PathVariable Long roomId,
            @RequestBody DateWiseReserveRequestDto dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        boolean isInternal = securityService.authenticateInternalService(authHeader, internalToken, internalService);
        CallerPrincipal principal = null;

        if (!isInternal) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication required to reserve room inventory"));
            }
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }
            principal = securityService.resolvePrincipal(authHeader);
        }

        Map<String, Object> result = inventoryService.reserveStayInventory(roomId, dto, principal, isInternal);
        return ResponseEntity.ok(result);
    }

    /**
     * Authoritative and idempotent date-wise release endpoint (WP-03).
     * Reverts held or booked inventory by bookingRef across all stay dates.
     */
    @PostMapping("/rooms/release-stay")
    public ResponseEntity<?> releaseStay(
            @RequestBody(required = false) DateWiseReleaseRequestDto dto,
            @RequestParam(required = false) String bookingRef,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        boolean isInternal = securityService.authenticateInternalService(authHeader, internalToken, internalService);
        CallerPrincipal principal = null;

        if (!isInternal) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication required to release room inventory"));
            }
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }
            principal = securityService.resolvePrincipal(authHeader);
        }

        String ref = (dto != null && dto.getBookingRef() != null && !dto.getBookingRef().isBlank())
                ? dto.getBookingRef() : bookingRef;

        if (ref == null || ref.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Booking reference is mandatory for release"));
        }

        Map<String, Object> result = reservationService.releaseReservation(ref, principal, isInternal);
        return ResponseEntity.ok(result);
    }

    /**
     * Confirms stay inventory upon successful payment (HELD -> CONFIRMED).
     */
    @PutMapping("/rooms/confirm-stay")
    public ResponseEntity<?> confirmStay(
            @RequestParam String bookingRef,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        boolean isInternal = securityService.authenticateInternalService(authHeader, internalToken, internalService);
        CallerPrincipal principal = null;

        if (!isInternal) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication required to confirm room inventory"));
            }
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }
            principal = securityService.resolvePrincipal(authHeader);
            if (principal == null || (!principal.isAdmin() && !principal.isHotelManager())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Only trusted internal services, admins, or the owning manager can confirm inventory"));
            }
        }

        Map<String, Object> result = reservationService.confirmReservation(bookingRef, principal, isInternal);
        return ResponseEntity.ok(result);
    }

    /**
     * Legacy reserve rooms endpoint (maintained for backward compatibility).
     */
    @PutMapping("/rooms/{roomId}/reserve")
    public ResponseEntity<?> reserveRooms(
            @PathVariable Long roomId,
            @RequestParam Integer count,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        boolean isInternal = securityService.authenticateInternalService(authHeader, internalToken, internalService);

        if (!isInternal) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication required to reserve room inventory"));
            }
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }
        }

        if (count == null || count <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Room reservation count must be at least 1"));
        }

        RoomType roomType = roomTypeRepository.findById(roomId).orElse(null);
        if (roomType == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Room category not found"));
        }

        if (!"ACTIVE".equalsIgnoreCase(roomType.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cannot reserve rooms for an archived or inactive room category"));
        }

        Optional<HotelProperty> hotelOpt = hotelRepository.findById(roomType.getHotelId());
        if (hotelOpt.isPresent() && !"ACTIVE".equalsIgnoreCase(hotelOpt.get().getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cannot reserve rooms for an inactive hotel property"));
        }

        int current = roomType.getAvailableRooms() != null ? roomType.getAvailableRooms() : 0;
        if (current < count) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Insufficient room inventory available",
                    "roomId", roomId,
                    "availableRooms", current,
                    "requestedCount", count
            ));
        }

        int updated = current - count;
        roomType.setAvailableRooms(updated);
        roomTypeRepository.save(roomType);

        log.info("Reserved {} room(s) for roomId {}. Remaining available: {}", count, roomId, updated);
        return ResponseEntity.ok(Map.of(
                "message", "Rooms reserved successfully",
                "roomId", roomId,
                "availableRooms", updated,
                "remainingAvailable", updated
        ));
    }

    /**
     * Legacy release rooms endpoint (maintained for backward compatibility).
     */
    @PutMapping("/rooms/{roomId}/release")
    public ResponseEntity<?> releaseRooms(
            @PathVariable Long roomId,
            @RequestParam Integer count,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService) {

        boolean isInternal = securityService.authenticateInternalService(authHeader, internalToken, internalService);

        if (!isInternal) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication required to release room inventory"));
            }
            String token = authHeader.substring(7).trim();
            if (!securityService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired authorization token"));
            }

            CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
            if (principal == null || (!principal.isHotelManager() && !principal.isAdmin())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Only trusted internal services, admins, or the owning hotel manager can release inventory"));
            }

            if (principal.isHotelManager()) {
                RoomType roomType = roomTypeRepository.findById(roomId).orElse(null);
                if (roomType == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Room category not found"));
                }
                Optional<HotelProperty> hotelOpt = hotelRepository.findById(roomType.getHotelId());
                if (hotelOpt.isEmpty() || !principal.userId().equals(hotelOpt.get().getManagerId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Access denied: Cannot release inventory for a property you do not own"));
                }
            }
        }

        if (count == null || count <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Release count must be at least 1"));
        }

        RoomType roomType = roomTypeRepository.findById(roomId).orElse(null);
        if (roomType == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Room category not found"));
        }

        int current = roomType.getAvailableRooms() != null ? roomType.getAvailableRooms() : 0;
        int maxTotal = roomType.getTotalRooms() != null ? roomType.getTotalRooms() : Integer.MAX_VALUE;

        if (current >= maxTotal) {
            return ResponseEntity.ok(Map.of(
                    "message", "Room inventory is already at maximum capacity",
                    "roomId", roomId,
                    "availableRooms", current
            ));
        }

        int restored = Math.min(maxTotal, current + count);
        roomType.setAvailableRooms(restored);
        roomTypeRepository.save(roomType);

        log.info("Released {} room(s) for roomId {}. Restored available inventory to: {}", count, roomId, restored);
        return ResponseEntity.ok(Map.of(
                "message", "Rooms released successfully",
                "roomId", roomId,
                "availableRooms", restored
        ));
    }

    // =========================================================================
    // 5. DATE-SPECIFIC PRICING & BASE PRICE ENDPOINTS (WP-03 / WP-07.2)
    // =========================================================================

    @PutMapping("/rooms/{roomId}/base-price")
    public ResponseEntity<?> updateBasePrice(
            @PathVariable Long roomId,
            @Valid @RequestBody BasePriceUpdateRequestDto dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "update room base price");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        RoomTypeDto result = pricingService.updateBasePrice(roomId, dto, principal);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/rooms/{roomId}/pricing/override")
    public ResponseEntity<?> setPriceOverride(
            @PathVariable Long roomId,
            @RequestBody PriceOverrideRequestDto dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "set price override");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        PriceCalendarEntryDto result = pricingService.setPriceOverride(roomId, dto, principal);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/rooms/{roomId}/pricing/override-range")
    public ResponseEntity<?> setPriceOverrideRange(
            @PathVariable Long roomId,
            @RequestBody PriceRangeOverrideRequestDto dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "set range price overrides");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        List<PriceCalendarEntryDto> results = pricingService.setRangePriceOverrides(roomId, dto, principal);
        return ResponseEntity.ok(results);
    }

    @DeleteMapping("/rooms/{roomId}/pricing/override")
    public ResponseEntity<?> deletePriceOverride(
            @PathVariable Long roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "remove price override");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        Map<String, Object> result = pricingService.deletePriceOverride(roomId, date, principal);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/rooms/{roomId}/pricing/calendar")
    public ResponseEntity<?> getPriceCalendar(
            @PathVariable Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "view price calendar");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        List<PriceCalendarEntryDto> calendar = pricingService.getPriceCalendar(roomId, startDate, endDate, principal);
        return ResponseEntity.ok(calendar);
    }

    // =========================================================================
    // 6. OPERATIONAL ROOM BLOCKING ENDPOINTS (BD-10 / WP-03)
    // =========================================================================

    @PostMapping("/{hotelId}/rooms/{roomId}/blocks")
    public ResponseEntity<?> createRoomBlock(
            @PathVariable Long hotelId,
            @PathVariable Long roomId,
            @RequestBody RoomBlockRequestDto dto,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "create operational room block");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        RoomBlockResponseDto block = roomBlockService.createBlock(hotelId, roomId, dto, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(block);
    }

    @RequestMapping(value = "/blocks/{blockId}/release", method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<?> releaseRoomBlock(
            @PathVariable Long blockId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "release operational room block");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        RoomBlockResponseDto block = roomBlockService.releaseBlock(blockId, principal);
        return ResponseEntity.ok(block);
    }

    @GetMapping("/blocks/{blockId}")
    public ResponseEntity<?> getBlockById(
            @PathVariable Long blockId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "view operational room block details");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        RoomBlockResponseDto block = roomBlockService.getBlockById(blockId, principal);
        return ResponseEntity.ok(block);
    }

    @GetMapping("/{hotelId}/blocks")
    public ResponseEntity<?> getBlocksByHotel(
            @PathVariable Long hotelId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "view operational room blocks");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        List<RoomBlockResponseDto> blocks = roomBlockService.getBlocksByHotel(hotelId, principal);
        return ResponseEntity.ok(blocks);
    }

    @GetMapping("/rooms/{roomId}/blocks")
    public ResponseEntity<?> getBlocksByRoom(
            @PathVariable Long roomId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "view room category blocks");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        List<RoomBlockResponseDto> blocks = roomBlockService.getBlocksByRoom(roomId, principal);
        return ResponseEntity.ok(blocks);
    }

    // =========================================================================
    // 7. DATE-WISE INVENTORY CALENDAR (WP-03)
    // =========================================================================

    @GetMapping("/rooms/{roomId}/inventory/calendar")
    public ResponseEntity<?> getDailyInventoryCalendar(
            @PathVariable Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "view inventory calendar");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        List<DailyInventoryDto> calendar = inventoryService.getDailyInventoryCalendar(
                roomId, startDate, endDate, principal);
        return ResponseEntity.ok(calendar);
    }

    @GetMapping("/{hotelId}/inventory/summary")
    public ResponseEntity<?> getHotelInventorySummary(
            @PathVariable Long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        ResponseEntity<?> authError = validateManagerOrAdminAuth(authHeader, "view hotel inventory summary");
        if (authError != null) return authError;

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        HotelInventorySummaryDto summary = inventoryService.getHotelInventorySummary(
                hotelId, startDate, endDate, principal);
        return ResponseEntity.ok(summary);
    }

    // =========================================================================
    // 8. AUTHENTICATION & AUTHORIZATION HELPERS
    // =========================================================================

    private ResponseEntity<?> validateManagerOrAdminAuth(String authHeader, String action) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required to " + action));
        }

        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }

        CallerPrincipal principal = securityService.resolvePrincipal(authHeader);
        if (principal == null || !principal.hasManagerOrAdminAccess()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Access denied: Hotel Manager or Admin role required"));
        }

        return null;
    }
}
