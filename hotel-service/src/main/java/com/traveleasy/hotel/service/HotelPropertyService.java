package com.traveleasy.hotel.service;

import com.traveleasy.hotel.dto.*;
import com.traveleasy.hotel.exception.DomainValidationException;
import com.traveleasy.hotel.exception.OwnershipAccessDeniedException;
import com.traveleasy.hotel.exception.ResourceNotFoundException;
import com.traveleasy.hotel.model.HotelImage;
import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelImageRepository;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
import com.traveleasy.hotel.security.HotelSecurityService.CallerPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelPropertyService {

    private final HotelPropertyRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final HotelImageRepository hotelImageRepository;
    private final HotelImageStorageService imageStorageService;

    public static final Set<String> SUPPORTED_PROPERTY_TYPES = Set.of(
            "HOTEL", "RESORT", "BOUTIQUE HOTEL", "VILLA",
            "SERVICED APARTMENT", "HOMESTAY / BED & BREAKFAST", "HERITAGE HOTEL", "MOTEL"
    );

    public static final Set<String> SUPPORTED_CLASSIFICATIONS = Set.of(
            "NOT_CLASSIFIED", "NOT OFFICIALLY CLASSIFIED",
            "1_STAR", "1 STAR",
            "2_STAR", "2 STAR",
            "3_STAR", "3 STAR",
            "4_STAR", "4 STAR",
            "5_STAR", "5 STAR",
            "5_STAR_DELUXE", "5 STAR DELUXE",
            "OTHER", "OTHER RECOGNIZED CLASSIFICATION"
    );

    public static final Set<String> SUPPORTED_SEGMENTS = Set.of(
            "STANDARD", "COMFORT", "PREMIUM", "LUXURY", "ULTRA_LUXURY", "ULTRA LUXURY"
    );

    // =========================================================================
    // STEP 1: BASIC PROPERTY DETAILS DRAFT PERSISTENCE
    // =========================================================================

    @Transactional
    public HotelStep1ResponseDto saveStep1Draft(HotelStep1RequestDto request, CallerPrincipal principal) {
        validatePrincipal(principal);
        validateStep1Request(request);

        Long managerId = principal.userId();
        String managerName = principal.fullName();

        HotelProperty property;
        if (request.getId() != null && request.getId() > 0) {
            property = hotelRepository.findById(request.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Property draft not found with ID: " + request.getId()));
            enforcePropertyOwnership(property, principal);
        } else {
            property = new HotelProperty();
            property.setManagerId(managerId);
            property.setManagerName(managerName);
            property.setStatus("DRAFT"); // Crucial: draft property is never prematurely ACTIVE
            property.setRegistrationStep(1);
        }

        // Apply fields
        property.setName(request.getName().trim());
        property.setPropertyType(request.getPropertyType().trim());

        // Segment normalization (Standard, Comfort, Premium, Luxury, Ultra Luxury)
        String rawSeg = request.getPropertySegment().trim().toUpperCase().replace(" ", "_");
        if ("BUDGET".equals(rawSeg)) rawSeg = "STANDARD";
        else if ("MIDSCALE".equals(rawSeg)) rawSeg = "COMFORT";
        else if ("UPSCALE".equals(rawSeg)) rawSeg = "PREMIUM";
        property.setPropertySegment(rawSeg);

        property.setDescription(request.getDescription().trim());
        property.setBrandName(request.getBrandName() != null && !request.getBrandName().isBlank()
                ? request.getBrandName().trim()
                : null);

        // Classification & Backward-compatible starRating mapping
        if (request.getPropertyClassification() != null && !request.getPropertyClassification().isBlank()) {
            String normClass = request.getPropertyClassification().trim().toUpperCase().replace(" ", "_");
            if ("NOT_OFFICIALLY_CLASSIFIED".equals(normClass)) {
                normClass = "NOT_CLASSIFIED";
            } else if ("OTHER_RECOGNIZED_CLASSIFICATION".equals(normClass)) {
                normClass = "OTHER";
            }
            property.setPropertyClassification(normClass);

            if ("OTHER".equals(normClass)) {
                property.setCustomClassification(request.getCustomClassification() != null ? request.getCustomClassification().trim() : null);
                property.setStarRating(null);
            } else if ("NOT_CLASSIFIED".equals(normClass)) {
                property.setCustomClassification(null);
                property.setStarRating(null);
            } else if ("5_STAR_DELUXE".equals(normClass)) {
                property.setCustomClassification(null);
                property.setStarRating(5); // Map to 5 for legacy consumers
            } else if (normClass.endsWith("_STAR")) {
                try {
                    int stars = Integer.parseInt(normClass.substring(0, 1));
                    property.setStarRating(stars);
                    property.setCustomClassification(null);
                } catch (NumberFormatException e) {
                    property.setStarRating(null);
                }
            }
        } else {
            property.setPropertyClassification(null);
            property.setCustomClassification(null);
        }

        // Identify cover image and validate gallery
        List<HotelImageDto> imageDtos = request.getImages();
        HotelImageDto coverDto = imageDtos.stream()
                .filter(HotelImageDto::isCover)
                .findFirst()
                .orElse(imageDtos.get(0));

        property.setImageUrl(coverDto.getImageUrl()); // Set primary imageUrl for backward compatibility

        // Replaced/removed image cleanup from disk when updating an existing draft
        if (request.getId() != null && request.getId() > 0) {
            List<HotelImage> oldImages = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(request.getId());
            Set<String> newUrls = imageDtos.stream()
                    .map(HotelImageDto::getImageUrl)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .collect(Collectors.toSet());

            for (HotelImage oldImg : oldImages) {
                if (oldImg.getImageUrl() != null && !newUrls.contains(oldImg.getImageUrl().trim())) {
                    String filename = HotelImageStorageService.extractFilenameFromUrl(oldImg.getImageUrl());
                    if (filename != null) {
                        imageStorageService.deleteImage(filename);
                        log.info("Cleaned up removed/replaced hotel image file: {}", filename);
                    }
                }
            }
        }

        HotelProperty saved = hotelRepository.save(property);
        log.info("Hotel property draft ID: {} saved by manager ID: {}", saved.getId(), managerId);

        // Persist gallery images
        hotelImageRepository.deleteByHotelId(saved.getId());

        List<HotelImage> imageEntities = new ArrayList<>();
        for (int i = 0; i < imageDtos.size(); i++) {
            HotelImageDto imgDto = imageDtos.get(i);
            HotelImage img = HotelImage.builder()
                    .hotelId(saved.getId())
                    .imageUrl(imgDto.getImageUrl().trim())
                    .originalFileName(imgDto.getOriginalFileName())
                    .isCover(imgDto.isCover())
                    .displayOrder(imgDto.getDisplayOrder() > 0 ? imgDto.getDisplayOrder() : i)
                    .build();
            imageEntities.add(img);
        }
        List<HotelImage> savedImages = hotelImageRepository.saveAll(imageEntities);

        return HotelStep1ResponseDto.fromEntity(saved, savedImages);
    }

    @Transactional(readOnly = true)
    public HotelStep1ResponseDto getDraft(Long draftId, CallerPrincipal principal) {
        validatePrincipal(principal);

        HotelProperty property = hotelRepository.findById(draftId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft not found with ID: " + draftId));

        enforcePropertyOwnership(property, principal);

        List<HotelImage> images = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(draftId);
        return HotelStep1ResponseDto.fromEntity(property, images);
    }

    @Transactional(readOnly = true)
    public List<HotelStep1ResponseDto> getDraftsByManager(CallerPrincipal principal) {
        validatePrincipal(principal);

        List<HotelProperty> drafts = hotelRepository.findByManagerId(principal.userId()).stream()
                .filter(p -> "DRAFT".equalsIgnoreCase(p.getStatus()))
                .toList();

        return drafts.stream().map(d -> {
            List<HotelImage> images = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(d.getId());
            return HotelStep1ResponseDto.fromEntity(d, images);
        }).toList();
    }

    @Transactional
    public void deleteDraft(Long draftId, CallerPrincipal principal) {
        validatePrincipal(principal);

        HotelProperty property = hotelRepository.findById(draftId)
                .orElseThrow(() -> new ResourceNotFoundException("Property draft not found with ID: " + draftId));

        enforcePropertyOwnership(property, principal);

        if (!"DRAFT".equalsIgnoreCase(property.getStatus())) {
            throw new DomainValidationException("Only properties in DRAFT status can be deleted via draft removal");
        }

        List<HotelImage> images = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(draftId);
        for (HotelImage img : images) {
            String filename = HotelImageStorageService.extractFilenameFromUrl(img.getImageUrl());
            if (filename != null) {
                imageStorageService.deleteImage(filename);
            }
        }
        hotelImageRepository.deleteByHotelId(draftId);
        hotelRepository.delete(property);
        log.info("Manager ID {} deleted draft ID {} and removed associated image files", principal.userId(), draftId);
    }

    // =========================================================================
    // LEGACY & EXISTING PROPERTY METHODS
    // =========================================================================

    @Transactional
    public HotelProperty registerProperty(HotelRequestDto request, CallerPrincipal principal) {
        validatePrincipal(principal);
        validatePropertyRequest(request);

        Long managerId = principal.userId();
        String managerName = principal.fullName();

        HotelProperty property = HotelProperty.builder()
                .managerId(managerId)
                .managerName(managerName)
                .name(request.getName().trim())
                .city(request.getCity().trim())
                .address(request.getAddress().trim())
                .starRating(request.getStarRating())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .amenities(request.getAmenities() != null ? request.getAmenities().trim() : null)
                .imageUrl(request.getImageUrl() != null ? request.getImageUrl().trim() : null)
                .status("ACTIVE") // BD-03: Immediate activation
                .build();

        HotelProperty saved = hotelRepository.save(property);
        log.info("Hotel property '{}' (ID: {}) registered by manager ID: {}", saved.getName(), saved.getId(), managerId);
        return saved;
    }

    @Transactional
    public HotelProperty updateProperty(Long hotelId, HotelRequestDto request, CallerPrincipal principal) {
        validatePrincipal(principal);
        validatePropertyRequest(request);

        HotelProperty property = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel property not found with ID: " + hotelId));

        enforcePropertyOwnership(property, principal);

        property.setName(request.getName().trim());
        property.setCity(request.getCity().trim());
        property.setAddress(request.getAddress().trim());
        property.setStarRating(request.getStarRating());
        property.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        property.setAmenities(request.getAmenities() != null ? request.getAmenities().trim() : null);
        property.setImageUrl(request.getImageUrl() != null ? request.getImageUrl().trim() : null);

        HotelProperty updated = hotelRepository.save(property);
        log.info("Hotel property ID {} updated by user ID: {}", hotelId, principal.userId());
        return updated;
    }

    @Transactional(readOnly = true)
    public HotelManageDto getPropertyForManagement(Long hotelId, CallerPrincipal principal) {
        validatePrincipal(principal);

        HotelProperty property = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel property not found with ID: " + hotelId));

        enforcePropertyOwnership(property, principal);

        List<RoomType> rooms = roomTypeRepository.findByHotelId(hotelId);
        List<HotelImage> images = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(hotelId);
        return HotelManageDto.fromEntity(property, rooms, images);
    }

    @Transactional(readOnly = true)
    public List<HotelProperty> getPropertiesByManager(Long managerId, CallerPrincipal principal) {
        validatePrincipal(principal);

        if (principal.isHotelManager() && !principal.userId().equals(managerId)) {
            throw new OwnershipAccessDeniedException("Access denied: Cannot access properties belonging to another hotel manager");
        }

        return hotelRepository.findByManagerId(managerId);
    }

    @Transactional
    public HotelProperty updatePropertyStatus(Long hotelId, String status, CallerPrincipal principal) {
        validatePrincipal(principal);

        HotelProperty property = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel property not found with ID: " + hotelId));

        enforcePropertyOwnership(property, principal);

        if (status == null || status.isBlank()) {
            throw new DomainValidationException("Status parameter is required");
        }

        String normalized = status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalized) && !"INACTIVE".equals(normalized) && !"DRAFT".equals(normalized)) {
            throw new DomainValidationException("Invalid status: '" + status + "'. Must be ACTIVE, INACTIVE, or DRAFT");
        }

        property.setStatus(normalized);
        HotelProperty updated = hotelRepository.save(property);
        log.info("Hotel property ID {} status changed to '{}' by user ID: {}", hotelId, normalized, principal.userId());
        return updated;
    }

    // =========================================================================
    // VALIDATIONS & PERMISSION CHECKS
    // =========================================================================

    private void validatePrincipal(CallerPrincipal principal) {
        if (principal == null || !principal.hasManagerOrAdminAccess()) {
            throw new OwnershipAccessDeniedException("Access denied: Hotel Manager or Admin role required");
        }
        if (principal.userId() == null || principal.userId() <= 0) {
            throw new OwnershipAccessDeniedException("Access denied: Missing or invalid user identity in token");
        }
    }

    private void enforcePropertyOwnership(HotelProperty property, CallerPrincipal principal) {
        if (principal.isHotelManager() && !principal.userId().equals(property.getManagerId())) {
            throw new OwnershipAccessDeniedException("Access denied: Cannot manage a property you do not own");
        }
    }

    private void validateStep1Request(HotelStep1RequestDto request) {
        if (request == null) {
            throw new DomainValidationException("Step 1 request body cannot be null");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new DomainValidationException("Property Name is mandatory");
        }
        if (request.getName().trim().length() > 255) {
            throw new DomainValidationException("Property Name cannot exceed 255 characters");
        }

        if (request.getPropertyType() == null || request.getPropertyType().trim().isEmpty()) {
            throw new DomainValidationException("Property Type is mandatory");
        }
        String normType = request.getPropertyType().trim().toUpperCase();
        if (!SUPPORTED_PROPERTY_TYPES.contains(normType)) {
            throw new DomainValidationException("Unsupported property type: " + request.getPropertyType());
        }

        if (request.getPropertySegment() == null || request.getPropertySegment().trim().isEmpty()) {
            throw new DomainValidationException("Property Segment is mandatory");
        }
        String normSeg = request.getPropertySegment().trim().toUpperCase().replace(" ", "_");
        // Safe backward-compatible alias normalization for legacy draft values
        if ("BUDGET".equals(normSeg)) normSeg = "STANDARD";
        else if ("MIDSCALE".equals(normSeg)) normSeg = "COMFORT";
        else if ("UPSCALE".equals(normSeg)) normSeg = "PREMIUM";

        if (!SUPPORTED_SEGMENTS.contains(normSeg)) {
            throw new DomainValidationException("Unsupported property segment: " + request.getPropertySegment()
                    + ". Approved options are: Standard, Comfort, Premium, Luxury, Ultra Luxury");
        }

        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            throw new DomainValidationException("Short description is mandatory");
        }
        if (request.getDescription().trim().length() > 300) {
            throw new DomainValidationException("Short description cannot exceed 300 characters (actual: " + request.getDescription().trim().length() + ")");
        }

        if (request.getBrandName() != null && request.getBrandName().trim().length() > 150) {
            throw new DomainValidationException("Brand Name cannot exceed 150 characters");
        }

        // Classification
        if (request.getPropertyClassification() != null && !request.getPropertyClassification().isBlank()) {
            String normClass = request.getPropertyClassification().trim().toUpperCase().replace(" ", "_");
            if (!SUPPORTED_CLASSIFICATIONS.contains(normClass)) {
                throw new DomainValidationException("Unsupported property classification: " + request.getPropertyClassification()
                        + ". Note: Heritage or other classification must be entered via 'Other recognized classification'");
            }
            if ("OTHER".equals(normClass) || "OTHER_RECOGNIZED_CLASSIFICATION".equals(normClass)) {
                if (request.getCustomClassification() == null || request.getCustomClassification().trim().isEmpty()) {
                    throw new DomainValidationException("Custom classification must be specified when 'Other recognized classification' is selected");
                }
            }
        }

        // Images validation
        if (request.getImages() == null || request.getImages().isEmpty()) {
            throw new DomainValidationException("At least one image (Cover Image) is mandatory");
        }
        if (request.getImages().size() > 4) {
            throw new DomainValidationException("Maximum 4 images allowed (provided: " + request.getImages().size() + ")");
        }

        long coverCount = request.getImages().stream().filter(HotelImageDto::isCover).count();
        if (coverCount != 1) {
            throw new DomainValidationException("Exactly one image must be designated as the Cover Image (found: " + coverCount + ")");
        }

        for (HotelImageDto img : request.getImages()) {
            if (img.getImageUrl() == null || img.getImageUrl().trim().isEmpty()) {
                throw new DomainValidationException("Image URL cannot be blank");
            }
        }
    }

    private void validatePropertyRequest(HotelRequestDto request) {
        if (request == null) {
            throw new DomainValidationException("Request body cannot be null");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new DomainValidationException("Hotel property name is mandatory");
        }
        if (request.getCity() == null || request.getCity().trim().isEmpty()) {
            throw new DomainValidationException("City is mandatory");
        }
        if (request.getAddress() == null || request.getAddress().trim().isEmpty()) {
            throw new DomainValidationException("Address is mandatory");
        }
        if (request.getStarRating() != null && (request.getStarRating() < 1 || request.getStarRating() > 5)) {
            throw new DomainValidationException("Star rating must be between 1 and 5 stars");
        }
    }
}
