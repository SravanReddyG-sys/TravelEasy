package com.traveleasy.hotel.service;

import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.time.LocalDateTime;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // WP-05 STEP 4: PREDEFINED AMENITIES (7 categories, 18 amenities)
    public static final Set<String> SUPPORTED_PREDEFINED_AMENITIES = Set.of(
            // Connectivity
            "Wi-Fi", "Business Centre",
            // Dining
            "Restaurant", "Room Service", "Complimentary Breakfast",
            // Transport
            "Parking", "Airport Shuttle",
            // Recreation
            "Swimming Pool", "Gym", "Spa",
            // Safety
            "CCTV", "24-hour Security", "Fire Safety",
            // Accessibility
            "Accessible Entrance", "Elevator",
            // General
            "Laundry", "Reception", "Luggage Storage"
    );

    // WP-05 STEP 4: GUEST POLICIES
    public static final Set<String> SUPPORTED_CHILDREN_POLICIES = Set.of(
            "Children Allowed", "Children Not Allowed"
    );

    public static final Set<String> SUPPORTED_PET_POLICIES = Set.of(
            "Pets Allowed", "Pets Not Allowed"
    );

    public static final Set<String> SUPPORTED_SMOKING_POLICIES = Set.of(
            "Smoking Prohibited", "Designated Smoking Areas", "Smoking Allowed"
    );

    public static final Set<String> SUPPORTED_EXTRA_BED_POLICIES = Set.of(
            "Extra Bed Available", "Extra Bed Not Available"
    );

    // WP-05 STEP 4: ACCEPTED IDENTIFICATION TYPES
    public static final Set<String> SUPPORTED_ID_TYPES = Set.of(
            "Passport", "Driving Licence", "Voter ID", "Government-issued Photo ID", "Aadhaar Card", "Other"
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

    public static final Set<String> SUPPORTED_BUSINESS_REGISTRATION_TYPES = Set.of(
            "PRIVATE LIMITED COMPANY",
            "PUBLIC LIMITED COMPANY",
            "PARTNERSHIP",
            "LIMITED LIABILITY PARTNERSHIP (LLP)",
            "LIMITED LIABILITY PARTNERSHIP",
            "LLP",
            "SOLE PROPRIETORSHIP",
            "OTHER"
    );

    public static final Set<String> SUPPORTED_RECEPTION_AVAILABILITIES = Set.of(
            "24-HOUR RECEPTION", "24_HOUR", "24_HOUR_RECEPTION",
            "LIMITED OPERATING HOURS", "LIMITED_HOURS", "LIMITED_OPERATING_HOURS"
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
    // =========================================================================
    // WP-05 STEP 4: FACILITIES & POLICIES DRAFT
    // =========================================================================

    @Transactional
    public HotelStep4ResponseDto saveStep4Draft(Long draftId, HotelStep4RequestDto request, CallerPrincipal principal) {
        validatePrincipal(principal);
        final Long effectiveDraftId = (draftId != null && draftId > 0) ? draftId : request.getDraftId();
        if (effectiveDraftId == null || effectiveDraftId <= 0) {
            throw new DomainValidationException("Valid draft ID is mandatory to save Step 4 facilities and policies");
        }

        HotelProperty property = hotelRepository.findById(effectiveDraftId)
                .orElseThrow(() -> new ResourceNotFoundException("Property draft not found with ID: " + effectiveDraftId));

        enforcePropertyOwnership(property, principal);

        if (!"DRAFT".equalsIgnoreCase(property.getStatus())) {
            throw new DomainValidationException("Only property drafts in DRAFT status can be modified in the registration wizard");
        }

        validateStep4Request(request);

        // Normalize and extract predefined amenities
        List<String> normalizedPredefined = new ArrayList<>();
        if (request.getPredefinedAmenities() != null) {
            for (String item : request.getPredefinedAmenities()) {
                if (item != null && !item.trim().isEmpty()) {
                    String norm = normalizePredefinedAmenity(item.trim());
                    if (!normalizedPredefined.contains(norm)) {
                        normalizedPredefined.add(norm);
                    }
                }
            }
        }

        // Normalize and extract custom amenities
        List<String> normalizedCustom = new ArrayList<>();
        if (request.getCustomAmenities() != null) {
            for (String item : request.getCustomAmenities()) {
                if (item != null && !item.trim().isEmpty()) {
                    String trimmed = item.trim();
                    boolean exists = normalizedCustom.stream().anyMatch(c -> c.equalsIgnoreCase(trimmed));
                    if (!exists) {
                        normalizedCustom.add(trimmed);
                    }
                }
            }
        }

        // Guest Policies
        String childrenPolicy = normalizePolicyOption(request.getChildrenPolicy(), SUPPORTED_CHILDREN_POLICIES, "Children policy");
        String petPolicy = normalizePolicyOption(request.getPetPolicy(), SUPPORTED_PET_POLICIES, "Pet policy");
        String smokingPolicy = normalizePolicyOption(request.getSmokingPolicy(), SUPPORTED_SMOKING_POLICIES, "Smoking policy");
        String extraBedPolicy = normalizePolicyOption(request.getExtraBedPolicy(), SUPPORTED_EXTRA_BED_POLICIES, "Extra bed policy");

        // Identification Requirements
        List<String> normalizedIdTypes = new ArrayList<>();
        for (String idType : request.getAcceptedIdentificationTypes()) {
            if (idType != null && !idType.trim().isEmpty()) {
                String norm = normalizeIdType(idType.trim());
                if (!normalizedIdTypes.contains(norm)) {
                    normalizedIdTypes.add(norm);
                }
            }
        }

        String customIdType = null;
        if (normalizedIdTypes.contains("Other")) {
            customIdType = request.getCustomIdentificationType() != null ? request.getCustomIdentificationType().trim() : null;
        }

        // Additional Rules
        String additionalRules = (request.getAdditionalRules() != null && !request.getAdditionalRules().trim().isEmpty())
                ? request.getAdditionalRules().trim() : null;

        // Serialize to JSON
        try {
            property.setPredefinedAmenities(OBJECT_MAPPER.writeValueAsString(normalizedPredefined));
            property.setCustomAmenities(OBJECT_MAPPER.writeValueAsString(normalizedCustom));
            property.setAcceptedIdentificationTypes(OBJECT_MAPPER.writeValueAsString(normalizedIdTypes));
        } catch (Exception e) {
            log.error("Failed to serialize Step 4 lists to JSON: {}", e.getMessage());
            throw new DomainValidationException("Failed to serialize facilities or identification requirements");
        }

        property.setChildrenPolicy(childrenPolicy);
        property.setPetPolicy(petPolicy);
        property.setSmokingPolicy(smokingPolicy);
        property.setExtraBedPolicy(extraBedPolicy);
        property.setCustomIdentificationType(customIdType);
        property.setAdditionalRules(additionalRules);

        // Update legacy amenities column (backward compatibility)
        List<String> allAmenities = new ArrayList<>(normalizedPredefined);
        allAmenities.addAll(normalizedCustom);
        if (!allAmenities.isEmpty()) {
            String combined = String.join(", ", allAmenities);
            if (combined.length() > 255) {
                combined = combined.substring(0, 252) + "...";
            }
            property.setAmenities(combined);
        } else {
            property.setAmenities(null);
        }

        // Advance registration step to at least 4
        if (property.getRegistrationStep() == null || property.getRegistrationStep() < 4) {
            property.setRegistrationStep(4);
        }

        HotelProperty saved = hotelRepository.save(property);
        log.info("Hotel property draft ID: {} (Step 4: Facilities & Policies) saved by manager ID: {}", saved.getId(), principal.userId());

        List<HotelImage> images = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(saved.getId());
        return HotelStep4ResponseDto.fromEntity(saved, images);
    }

    private void validateStep4Request(HotelStep4RequestDto req) {
        if (req == null) {
            throw new DomainValidationException("Step 4 facilities and policies payload cannot be null");
        }

        // 1. Predefined amenities validation
        if (req.getPredefinedAmenities() != null) {
            for (String item : req.getPredefinedAmenities()) {
                if (item != null && !item.trim().isEmpty()) {
                    normalizePredefinedAmenity(item.trim());
                }
            }
        }

        // 2. Custom amenities validation
        if (req.getCustomAmenities() != null) {
            Set<String> seenCustom = new HashSet<>();
            for (String custom : req.getCustomAmenities()) {
                if (custom == null || custom.trim().isEmpty()) {
                    throw new DomainValidationException("Custom amenity name cannot be empty or whitespace");
                }
                String trimmed = custom.trim();
                if (trimmed.length() > 50) {
                    throw new DomainValidationException("Custom amenity name must not exceed 50 characters: " + trimmed);
                }
                // Prohibit duplicates of predefined amenities
                for (String predefined : SUPPORTED_PREDEFINED_AMENITIES) {
                    if (predefined.equalsIgnoreCase(trimmed)) {
                        throw new DomainValidationException("Custom amenity '" + trimmed + "' already exists as a predefined facility");
                    }
                }
                // Prohibit duplicate custom amenities
                String lower = trimmed.toLowerCase();
                if (seenCustom.contains(lower)) {
                    throw new DomainValidationException("Duplicate custom amenity: " + trimmed);
                }
                seenCustom.add(lower);
            }
        }

        // 3. Guest Policies Validation
        if (req.getChildrenPolicy() == null || req.getChildrenPolicy().trim().isEmpty()) {
            throw new DomainValidationException("Children policy is mandatory");
        }
        normalizePolicyOption(req.getChildrenPolicy(), SUPPORTED_CHILDREN_POLICIES, "Children policy");

        if (req.getPetPolicy() == null || req.getPetPolicy().trim().isEmpty()) {
            throw new DomainValidationException("Pet policy is mandatory");
        }
        normalizePolicyOption(req.getPetPolicy(), SUPPORTED_PET_POLICIES, "Pet policy");

        if (req.getSmokingPolicy() == null || req.getSmokingPolicy().trim().isEmpty()) {
            throw new DomainValidationException("Smoking policy is mandatory");
        }
        normalizePolicyOption(req.getSmokingPolicy(), SUPPORTED_SMOKING_POLICIES, "Smoking policy");

        if (req.getExtraBedPolicy() == null || req.getExtraBedPolicy().trim().isEmpty()) {
            throw new DomainValidationException("Extra bed policy is mandatory");
        }
        normalizePolicyOption(req.getExtraBedPolicy(), SUPPORTED_EXTRA_BED_POLICIES, "Extra bed policy");

        // 4. Identification Requirements Validation
        if (req.getAcceptedIdentificationTypes() == null || req.getAcceptedIdentificationTypes().isEmpty()) {
            throw new DomainValidationException("At least one accepted identification type is required");
        }
        boolean hasOther = false;
        Set<String> seenIds = new HashSet<>();
        for (String idType : req.getAcceptedIdentificationTypes()) {
            if (idType == null || idType.trim().isEmpty()) {
                throw new DomainValidationException("Identification type cannot be empty");
            }
            String norm = normalizeIdType(idType.trim());
            if (seenIds.contains(norm)) {
                throw new DomainValidationException("Duplicate identification type: " + norm);
            }
            seenIds.add(norm);
            if ("Other".equalsIgnoreCase(norm)) {
                hasOther = true;
            }
        }

        if (hasOther) {
            if (req.getCustomIdentificationType() == null || req.getCustomIdentificationType().trim().isEmpty()) {
                throw new DomainValidationException("Custom identification specification is required when 'Other' is selected");
            }
            if (req.getCustomIdentificationType().trim().length() > 150) {
                throw new DomainValidationException("Custom identification requirement must not exceed 150 characters");
            }
        }

        // 5. Additional Property Rules Validation
        if (req.getAdditionalRules() != null && req.getAdditionalRules().trim().length() > 1000) {
            throw new DomainValidationException("Additional property rules must not exceed 1000 characters");
        }
    }

    private String normalizePredefinedAmenity(String name) {
        for (String supported : SUPPORTED_PREDEFINED_AMENITIES) {
            if (supported.equalsIgnoreCase(name)) {
                return supported;
            }
        }
        throw new DomainValidationException("Unsupported predefined amenity: " + name);
    }

    private String normalizePolicyOption(String input, Set<String> allowedOptions, String fieldName) {
        if (input == null || input.trim().isEmpty()) {
            throw new DomainValidationException(fieldName + " is mandatory");
        }
        String clean = input.trim();
        for (String opt : allowedOptions) {
            if (opt.equalsIgnoreCase(clean) || opt.replace(" ", "_").equalsIgnoreCase(clean)) {
                return opt;
            }
        }
        throw new DomainValidationException("Invalid " + fieldName.toLowerCase() + ": '" + input + "'. Supported options: " + allowedOptions);
    }

    private String normalizeIdType(String input) {
        String clean = input.trim();
        for (String opt : SUPPORTED_ID_TYPES) {
            if (opt.equalsIgnoreCase(clean) || opt.replace(" ", "_").equalsIgnoreCase(clean)) {
                return opt;
            }
        }
        throw new DomainValidationException("Unsupported identification type: '" + input + "'. Supported options: " + SUPPORTED_ID_TYPES);
    }



    // =========================================================================
    // WP-05 STEP 5: FINAL REGISTRATION SUBMISSION & ACTIVATION (BD-03)
    // =========================================================================

    @Transactional
    public HotelStep5ResponseDto submitPropertyRegistration(Long draftId, HotelStep5SubmitRequestDto request, CallerPrincipal principal) {
        validatePrincipal(principal);
        final Long effectiveDraftId = (draftId != null && draftId > 0) ? draftId : (request != null ? request.getDraftId() : null);
        if (effectiveDraftId == null || effectiveDraftId <= 0) {
            throw new DomainValidationException("Valid draft ID is mandatory to finalize property registration");
        }

        HotelProperty property = hotelRepository.findById(effectiveDraftId)
                .orElseThrow(() -> new ResourceNotFoundException("Property draft not found with ID: " + effectiveDraftId));

        enforcePropertyOwnership(property, principal);

        // Idempotency: If already ACTIVE, return existing active property details safely
        if ("ACTIVE".equalsIgnoreCase(property.getStatus()) && property.getRegistrationStep() != null && property.getRegistrationStep() == 5) {
            log.info("Property ID: {} is already ACTIVE. Returning finalized property details idempotently.", property.getId());
            List<HotelImage> images = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(property.getId());
            return HotelStep5ResponseDto.fromEntity(property, images, "Property is already active in your portfolio.");
        }

        if (!"DRAFT".equalsIgnoreCase(property.getStatus())) {
            throw new DomainValidationException("Only properties in DRAFT status can be submitted for final registration");
        }

        // Validate accuracy confirmation
        if (request != null && Boolean.FALSE.equals(request.getConfirmationAccepted())) {
            throw new DomainValidationException("You must confirm that the information provided is accurate and complete and agree to comply with TravelEasy's Partner Terms & Conditions");
        }

        // Validate complete draft data across all 4 steps
        validateCompleteDraftForSubmission(property);

        // Transition from DRAFT to ACTIVE immediately per BD-03
        LocalDateTime now = LocalDateTime.now();
        property.setStatus("ACTIVE");
        property.setRegistrationStep(5);
        property.setSubmittedAt(now);
        property.setActivatedAt(now);

        HotelProperty saved = hotelRepository.save(property);
        log.info("Hotel property ID: {} successfully registered and activated by manager ID: {}", saved.getId(), principal.userId());

        List<HotelImage> images = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(saved.getId());
        return HotelStep5ResponseDto.fromEntity(saved, images, "Property registration submitted and activated successfully.");
    }

    private void validateCompleteDraftForSubmission(HotelProperty property) {
        if (property == null) {
            throw new DomainValidationException("Property draft cannot be null for submission");
        }

        // --- STEP 1 VALIDATIONS ---
        if (property.getName() == null || property.getName().trim().isEmpty()) {
            throw new DomainValidationException("Step 1 Incomplete: Property name is mandatory");
        }
        if (property.getPropertyType() == null || property.getPropertyType().trim().isEmpty()) {
            throw new DomainValidationException("Step 1 Incomplete: Property type is mandatory");
        }
        if (property.getPropertyClassification() == null || property.getPropertyClassification().trim().isEmpty()) {
            throw new DomainValidationException("Step 1 Incomplete: Recognized property classification is mandatory");
        }
        if ("OTHER".equalsIgnoreCase(property.getPropertyClassification()) &&
                (property.getCustomClassification() == null || property.getCustomClassification().trim().isEmpty())) {
            throw new DomainValidationException("Step 1 Incomplete: Custom classification is mandatory when 'Other' is selected");
        }
        if (property.getPropertySegment() == null || property.getPropertySegment().trim().isEmpty()) {
            throw new DomainValidationException("Step 1 Incomplete: Property segment is mandatory");
        }
        if (property.getDescription() == null || property.getDescription().trim().isEmpty()) {
            throw new DomainValidationException("Step 1 Incomplete: Short description is mandatory");
        }
        if (property.getDescription().trim().length() > 300) {
            throw new DomainValidationException("Step 1 Incomplete: Short description must not exceed 300 characters");
        }
        List<HotelImage> images = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(property.getId());
        if (images == null || images.isEmpty()) {
            throw new DomainValidationException("Step 1 Incomplete: At least one property image is required");
        }
        boolean hasCover = images.stream().anyMatch(HotelImage::isCover);
        if (!hasCover) {
            throw new DomainValidationException("Step 1 Incomplete: Exactly one image must be designated as the Cover Image");
        }

        // --- STEP 2 VALIDATIONS ---
        if (property.getAddressLine1() == null || property.getAddressLine1().trim().isEmpty()) {
            throw new DomainValidationException("Step 2 Incomplete: Address Line 1 is mandatory");
        }
        if (property.getCountry() == null || property.getCountry().trim().isEmpty()) {
            throw new DomainValidationException("Step 2 Incomplete: Country is mandatory");
        }
        if (!AddressReferenceData.isCountrySupported(property.getCountry())) {
            throw new DomainValidationException("Step 2 Incomplete: Country '" + property.getCountry() + "' is not supported");
        }
        if (property.getState() == null || property.getState().trim().isEmpty()) {
            throw new DomainValidationException("Step 2 Incomplete: State is mandatory");
        }
        if (!AddressReferenceData.isStateValidForCountry(property.getCountry(), property.getState())) {
            throw new DomainValidationException("Step 2 Incomplete: State '" + property.getState() + "' is not valid for country " + property.getCountry());
        }
        if (property.getCity() == null || property.getCity().trim().isEmpty()) {
            throw new DomainValidationException("Step 2 Incomplete: City is mandatory");
        }
        if (property.getPostalCode() == null || property.getPostalCode().trim().isEmpty()) {
            throw new DomainValidationException("Step 2 Incomplete: Postal code is mandatory");
        }
        if (!AddressReferenceData.isValidPostalCode(property.getCountry(), property.getPostalCode())) {
            throw new DomainValidationException("Step 2 Incomplete: Postal code format is invalid for " + property.getCountry());
        }

        // --- STEP 3 VALIDATIONS ---
        if (property.getPropertyContactNumber() == null || property.getPropertyContactNumber().trim().isEmpty()) {
            throw new DomainValidationException("Step 3 Incomplete: Property contact number is mandatory");
        }
        if (property.getPropertyEmail() == null || property.getPropertyEmail().trim().isEmpty()) {
            throw new DomainValidationException("Step 3 Incomplete: Property email address is mandatory");
        }
        if (property.getRegisteredBusinessName() == null || property.getRegisteredBusinessName().trim().isEmpty()) {
            throw new DomainValidationException("Step 3 Incomplete: Registered business name is mandatory");
        }
        if (property.getBusinessRegistrationType() == null || property.getBusinessRegistrationType().trim().isEmpty()) {
            throw new DomainValidationException("Step 3 Incomplete: Business registration type is mandatory");
        }
        if ("OTHER".equalsIgnoreCase(normalizeBusinessRegistrationType(property.getBusinessRegistrationType())) &&
                (property.getOtherBusinessRegistrationType() == null || property.getOtherBusinessRegistrationType().trim().isEmpty())) {
            throw new DomainValidationException("Step 3 Incomplete: Custom business registration type is mandatory when 'Other' is selected");
        }
        if (property.getBusinessRegistrationNumber() == null || property.getBusinessRegistrationNumber().trim().isEmpty()) {
            throw new DomainValidationException("Step 3 Incomplete: Business registration number is mandatory");
        }
        if (Boolean.TRUE.equals(property.getIsGstRegistered())) {
            if (property.getGstin() == null || property.getGstin().trim().isEmpty()) {
                throw new DomainValidationException("Step 3 Incomplete: GSTIN is mandatory when GST registered");
            }
            if (!property.getGstin().trim().matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$")) {
                throw new DomainValidationException("Step 3 Incomplete: Invalid GSTIN format");
            }
            if (property.getGstLegalName() == null || property.getGstLegalName().trim().isEmpty()) {
                throw new DomainValidationException("Step 3 Incomplete: GST legal name is mandatory when GST registered");
            }
            if (property.getGstState() == null || property.getGstState().trim().isEmpty()) {
                throw new DomainValidationException("Step 3 Incomplete: GST state is mandatory when GST registered");
            }
        }
        if (property.getPropertyManagerName() == null || property.getPropertyManagerName().trim().isEmpty() ||
                property.getPropertyManagerDesignation() == null || property.getPropertyManagerDesignation().trim().isEmpty() ||
                property.getPropertyManagerContactNumber() == null || property.getPropertyManagerContactNumber().trim().isEmpty() ||
                property.getPropertyManagerEmail() == null || property.getPropertyManagerEmail().trim().isEmpty()) {
            throw new DomainValidationException("Step 3 Incomplete: Property operational manager details are mandatory");
        }
        if (property.getCheckInTime() == null || property.getCheckInTime().trim().isEmpty() ||
                property.getCheckOutTime() == null || property.getCheckOutTime().trim().isEmpty() ||
                property.getReceptionAvailability() == null || property.getReceptionAvailability().trim().isEmpty()) {
            throw new DomainValidationException("Step 3 Incomplete: Operating hours and reception availability are mandatory");
        }
        if (property.getReceptionAvailability().toLowerCase().contains("limited") &&
                (property.getReceptionOpeningTime() == null || property.getReceptionOpeningTime().trim().isEmpty() ||
                 property.getReceptionClosingTime() == null || property.getReceptionClosingTime().trim().isEmpty())) {
            throw new DomainValidationException("Step 3 Incomplete: Limited reception opening and closing times are mandatory");
        }

        // --- STEP 4 VALIDATIONS ---
        if (property.getChildrenPolicy() == null || property.getChildrenPolicy().trim().isEmpty()) {
            throw new DomainValidationException("Step 4 Incomplete: Children policy is mandatory");
        }
        if (property.getPetPolicy() == null || property.getPetPolicy().trim().isEmpty()) {
            throw new DomainValidationException("Step 4 Incomplete: Pet policy is mandatory");
        }
        if (property.getSmokingPolicy() == null || property.getSmokingPolicy().trim().isEmpty()) {
            throw new DomainValidationException("Step 4 Incomplete: Smoking policy is mandatory");
        }
        if (property.getExtraBedPolicy() == null || property.getExtraBedPolicy().trim().isEmpty()) {
            throw new DomainValidationException("Step 4 Incomplete: Extra bed policy is mandatory");
        }
        List<String> idTypes = HotelStep4ResponseDto.parseJsonList(property.getAcceptedIdentificationTypes());
        if (idTypes == null || idTypes.isEmpty()) {
            throw new DomainValidationException("Step 4 Incomplete: At least one accepted identification requirement is mandatory");
        }
        if (idTypes.contains("Other") && (property.getCustomIdentificationType() == null || property.getCustomIdentificationType().trim().isEmpty())) {
            throw new DomainValidationException("Step 4 Incomplete: Custom identification specification is mandatory when 'Other' is selected");
        }
        if (property.getAdditionalRules() != null && property.getAdditionalRules().trim().length() > 1000) {
            throw new DomainValidationException("Step 4 Incomplete: Additional property rules must not exceed 1000 characters");
        }
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
    // 1B. PROPERTY REGISTRATION WIZARD (WP-05 STEP 2: LOCATION & ADDRESS)
    // =========================================================================

    @Transactional
    public HotelStep2ResponseDto saveStep2Draft(Long draftId, HotelStep2RequestDto request, CallerPrincipal principal) {
        validatePrincipal(principal);
        final Long effectiveDraftId = (draftId != null && draftId > 0) ? draftId : request.getDraftId();
        if (effectiveDraftId == null || effectiveDraftId <= 0) {
            throw new DomainValidationException("Valid draft ID is mandatory to save Step 2 address details");
        }

        HotelProperty property = hotelRepository.findById(effectiveDraftId)
                .orElseThrow(() -> new ResourceNotFoundException("Property draft not found with ID: " + effectiveDraftId));

        enforcePropertyOwnership(property, principal);

        if (!"DRAFT".equalsIgnoreCase(property.getStatus())) {
            throw new DomainValidationException("Only property drafts in DRAFT status can be modified in the registration wizard");
        }

        validateStep2Request(request);

        // Update structured address fields
        property.setAddressLine1(request.getAddressLine1().trim());
        property.setAddressLine2(request.getAddressLine2() != null && !request.getAddressLine2().trim().isEmpty()
                ? request.getAddressLine2().trim() : null);
        property.setCountry(AddressReferenceData.normalizeCountry(request.getCountry()));
        property.setState(AddressReferenceData.normalizeState(request.getCountry(), request.getState()));
        property.setCity(request.getCity().trim());
        property.setPostalCode(request.getPostalCode().trim());
        property.setLandmark(request.getLandmark() != null && !request.getLandmark().trim().isEmpty()
                ? request.getLandmark().trim() : null);

        // Synchronize legacy address field for backward compatibility
        String legacyAddress = buildLegacyAddress(property);
        property.setAddress(legacyAddress);

        // Advance registration step to at least 2
        if (property.getRegistrationStep() == null || property.getRegistrationStep() < 2) {
            property.setRegistrationStep(2);
        }

        HotelProperty saved = hotelRepository.save(property);
        log.info("Hotel property draft ID: {} (Step 2: Location & Address) saved by manager ID: {}", saved.getId(), principal.userId());

        List<HotelImage> images = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(saved.getId());
        return HotelStep2ResponseDto.fromEntity(saved, images);
    }

    private String buildLegacyAddress(HotelProperty property) {
        StringBuilder sb = new StringBuilder();
        if (property.getAddressLine1() != null && !property.getAddressLine1().isBlank()) {
            sb.append(property.getAddressLine1().trim());
        }
        if (property.getAddressLine2() != null && !property.getAddressLine2().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(property.getAddressLine2().trim());
        }
        if (property.getLandmark() != null && !property.getLandmark().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(property.getLandmark().trim());
        }
        if (property.getCity() != null && !property.getCity().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(property.getCity().trim());
        }
        if (property.getState() != null && !property.getState().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(property.getState().trim());
        }
        if (property.getPostalCode() != null && !property.getPostalCode().isBlank()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(property.getPostalCode().trim());
        }
        if (property.getCountry() != null && !property.getCountry().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(property.getCountry().trim());
        }
        return sb.toString();
    }

    // =========================================================================
    // 1C. PROPERTY REGISTRATION WIZARD (WP-05 STEP 3: PROPERTY INFORMATION)
    // =========================================================================

    @Transactional
    public HotelStep3ResponseDto saveStep3Draft(Long draftId, HotelStep3RequestDto request, CallerPrincipal principal) {
        validatePrincipal(principal);
        final Long effectiveDraftId = (draftId != null && draftId > 0) ? draftId : request.getDraftId();
        if (effectiveDraftId == null || effectiveDraftId <= 0) {
            throw new DomainValidationException("Valid draft ID is mandatory to save Step 3 property information");
        }

        HotelProperty property = hotelRepository.findById(effectiveDraftId)
                .orElseThrow(() -> new ResourceNotFoundException("Property draft not found with ID: " + effectiveDraftId));

        enforcePropertyOwnership(property, principal);

        if (!"DRAFT".equalsIgnoreCase(property.getStatus())) {
            throw new DomainValidationException("Only property drafts in DRAFT status can be modified in the registration wizard");
        }

        validateStep3Request(request);

        // 1. Property Contact
        property.setPropertyContactNumber(request.getPropertyContactNumber().trim());
        property.setAlternateContactNumber(request.getAlternateContactNumber() != null && !request.getAlternateContactNumber().trim().isEmpty()
                ? request.getAlternateContactNumber().trim() : null);
        property.setPropertyEmail(request.getPropertyEmail().trim().toLowerCase());
        property.setWebsiteUrl(request.getWebsiteUrl() != null && !request.getWebsiteUrl().trim().isEmpty()
                ? request.getWebsiteUrl().trim() : null);

        // 2. Business Registration
        property.setRegisteredBusinessName(request.getRegisteredBusinessName().trim());
        String regType = normalizeBusinessRegistrationType(request.getBusinessRegistrationType());
        property.setBusinessRegistrationType(regType);
        if ("OTHER".equalsIgnoreCase(regType)) {
            property.setOtherBusinessRegistrationType(request.getOtherBusinessRegistrationType().trim());
        } else {
            property.setOtherBusinessRegistrationType(null);
        }
        property.setBusinessRegistrationNumber(request.getBusinessRegistrationNumber().trim());
        property.setRegisteredBusinessAddress(request.getRegisteredBusinessAddress() != null && !request.getRegisteredBusinessAddress().trim().isEmpty()
                ? request.getRegisteredBusinessAddress().trim() : null);

        // 3. GST Details
        boolean isGst = Boolean.TRUE.equals(request.getIsGstRegistered());
        property.setIsGstRegistered(isGst);
        if (isGst) {
            property.setGstin(request.getGstin().trim().toUpperCase());
            property.setGstLegalName(request.getGstLegalName().trim());
            property.setGstState(AddressReferenceData.normalizeState("India", request.getGstState()));
        } else {
            property.setGstin(null);
            property.setGstLegalName(null);
            property.setGstState(null);
        }

        // 4. Property Manager Details
        property.setPropertyManagerName(request.getPropertyManagerName().trim());
        property.setPropertyManagerDesignation(request.getPropertyManagerDesignation().trim());
        property.setPropertyManagerContactNumber(request.getPropertyManagerContactNumber().trim());
        property.setPropertyManagerEmail(request.getPropertyManagerEmail().trim().toLowerCase());

        // 5. Operating Hours
        property.setCheckInTime(request.getCheckInTime().trim());
        property.setCheckOutTime(request.getCheckOutTime().trim());
        String recAvail = normalizeReceptionAvailability(request.getReceptionAvailability());
        property.setReceptionAvailability(recAvail);
        if ("LIMITED_HOURS".equalsIgnoreCase(recAvail) || "LIMITED OPERATING HOURS".equalsIgnoreCase(recAvail)) {
            property.setReceptionOpeningTime(request.getReceptionOpeningTime() != null ? request.getReceptionOpeningTime().trim() : null);
            property.setReceptionClosingTime(request.getReceptionClosingTime() != null ? request.getReceptionClosingTime().trim() : null);
        } else {
            property.setReceptionOpeningTime(null);
            property.setReceptionClosingTime(null);
        }

        // Advance registration step to at least 3
        if (property.getRegistrationStep() == null || property.getRegistrationStep() < 3) {
            property.setRegistrationStep(3);
        }

        HotelProperty saved = hotelRepository.save(property);
        log.info("Hotel property draft ID: {} (Step 3: Property Information) saved by manager ID: {}", saved.getId(), principal.userId());

        List<HotelImage> images = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(saved.getId());
        return HotelStep3ResponseDto.fromEntity(saved, images);
    }

    private void validateStep3Request(HotelStep3RequestDto req) {
        if (req == null) {
            throw new DomainValidationException("Step 3 property information request payload cannot be null");
        }

        // 1. Contact Validation
        if (req.getPropertyContactNumber() == null || req.getPropertyContactNumber().trim().isEmpty()) {
            throw new DomainValidationException("Property contact number is mandatory");
        }
        String cleanPhone = req.getPropertyContactNumber().trim();
        if (!cleanPhone.matches("^[+]?[0-9\\s\\-\\(\\)]{7,20}$")) {
            throw new DomainValidationException("Property contact number format is invalid. Must be 7-20 digits with optional calling code");
        }
        if (req.getAlternateContactNumber() != null && !req.getAlternateContactNumber().trim().isEmpty()) {
            String altPhone = req.getAlternateContactNumber().trim();
            if (!altPhone.matches("^[+]?[0-9\\s\\-\\(\\)]{7,20}$")) {
                throw new DomainValidationException("Alternate contact number format is invalid. Must be 7-20 digits with optional calling code");
            }
        }
        if (req.getPropertyEmail() == null || req.getPropertyEmail().trim().isEmpty()) {
            throw new DomainValidationException("Property email address is mandatory");
        }
        if (!req.getPropertyEmail().trim().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new DomainValidationException("Property email address format is invalid");
        }
        if (req.getWebsiteUrl() != null && !req.getWebsiteUrl().trim().isEmpty()) {
            String url = req.getWebsiteUrl().trim().toLowerCase();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new DomainValidationException("Website URL must start with http:// or https://");
            }
        }

        // 2. Business Registration Validation
        if (req.getRegisteredBusinessName() == null || req.getRegisteredBusinessName().trim().isEmpty()) {
            throw new DomainValidationException("Registered business name is mandatory");
        }
        if (req.getBusinessRegistrationType() == null || req.getBusinessRegistrationType().trim().isEmpty()) {
            throw new DomainValidationException("Business registration type is mandatory");
        }
        String normalizedType = normalizeBusinessRegistrationType(req.getBusinessRegistrationType());
        if ("OTHER".equalsIgnoreCase(normalizedType)) {
            if (req.getOtherBusinessRegistrationType() == null || req.getOtherBusinessRegistrationType().trim().isEmpty()) {
                throw new DomainValidationException("Please specify the custom business registration type when 'Other' is selected");
            }
        }
        if (req.getBusinessRegistrationNumber() == null || req.getBusinessRegistrationNumber().trim().isEmpty()) {
            throw new DomainValidationException("Business registration number is mandatory");
        }

        // 3. GST Validation
        if (Boolean.TRUE.equals(req.getIsGstRegistered())) {
            if (req.getGstin() == null || req.getGstin().trim().isEmpty()) {
                throw new DomainValidationException("GSTIN is mandatory when business is GST registered");
            }
            String gstin = req.getGstin().trim().toUpperCase();
            if (!gstin.matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$")) {
                throw new DomainValidationException("GSTIN format is invalid. Must follow standard 15-character Indian GST format (e.g., 36AAAAA0000A1Z5)");
            }
            if (req.getGstLegalName() == null || req.getGstLegalName().trim().isEmpty()) {
                throw new DomainValidationException("GST legal name is mandatory when business is GST registered");
            }
            if (req.getGstState() == null || req.getGstState().trim().isEmpty()) {
                throw new DomainValidationException("GST state is mandatory when business is GST registered");
            }
            if (!AddressReferenceData.INDIAN_STATES_AND_UTS.contains(req.getGstState().trim())) {
                throw new DomainValidationException("GST state must be a valid Indian state or union territory");
            }
        }

        // 4. Property Manager Validation
        if (req.getPropertyManagerName() == null || req.getPropertyManagerName().trim().isEmpty()) {
            throw new DomainValidationException("Property manager name is mandatory");
        }
        if (req.getPropertyManagerDesignation() == null || req.getPropertyManagerDesignation().trim().isEmpty()) {
            throw new DomainValidationException("Property manager designation is mandatory");
        }
        if (req.getPropertyManagerContactNumber() == null || req.getPropertyManagerContactNumber().trim().isEmpty()) {
            throw new DomainValidationException("Property manager contact number is mandatory");
        }
        String mgrPhone = req.getPropertyManagerContactNumber().trim();
        if (!mgrPhone.matches("^[+]?[0-9\\s\\-\\(\\)]{7,20}$")) {
            throw new DomainValidationException("Property manager contact number format is invalid. Must be 7-20 digits with optional calling code");
        }
        if (req.getPropertyManagerEmail() == null || req.getPropertyManagerEmail().trim().isEmpty()) {
            throw new DomainValidationException("Property manager email address is mandatory");
        }
        if (!req.getPropertyManagerEmail().trim().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new DomainValidationException("Property manager email address format is invalid");
        }

        // 5. Operating Hours Validation
        if (req.getCheckInTime() == null || req.getCheckInTime().trim().isEmpty()) {
            throw new DomainValidationException("Check-in time is mandatory");
        }
        if (req.getCheckOutTime() == null || req.getCheckOutTime().trim().isEmpty()) {
            throw new DomainValidationException("Check-out time is mandatory");
        }
        if (req.getReceptionAvailability() == null || req.getReceptionAvailability().trim().isEmpty()) {
            throw new DomainValidationException("Reception availability selection is mandatory");
        }
        String recAvail = normalizeReceptionAvailability(req.getReceptionAvailability());
        if ("LIMITED_HOURS".equalsIgnoreCase(recAvail) || "LIMITED OPERATING HOURS".equalsIgnoreCase(recAvail)) {
            if (req.getReceptionOpeningTime() == null || req.getReceptionOpeningTime().trim().isEmpty()) {
                throw new DomainValidationException("Reception opening time is mandatory for limited operating hours");
            }
            if (req.getReceptionClosingTime() == null || req.getReceptionClosingTime().trim().isEmpty()) {
                throw new DomainValidationException("Reception closing time is mandatory for limited operating hours");
            }
        }
    }

    private String normalizeBusinessRegistrationType(String input) {
        if (input == null) return "OTHER";
        String normalized = input.trim().toUpperCase();
        for (String type : SUPPORTED_BUSINESS_REGISTRATION_TYPES) {
            if (type.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return input.trim();
    }

    private String normalizeReceptionAvailability(String input) {
        if (input == null) return "24_HOUR";
        String normalized = input.trim().toUpperCase();
        if (normalized.contains("24")) {
            return "24_HOUR";
        }
        if (normalized.contains("LIMITED")) {
            return "LIMITED_HOURS";
        }
        return input.trim();
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

    @Transactional
    public HotelManageDto updatePropertyFull(Long hotelId, HotelManageUpdateRequestDto request, CallerPrincipal principal) {
        validatePrincipal(principal);
        if (request == null) {
            throw new DomainValidationException("Property update payload cannot be null");
        }

        HotelProperty property = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel property not found with ID: " + hotelId));

        enforcePropertyOwnership(property, principal);

        // 1. Validate Step 1 fields
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new DomainValidationException("Property name is required");
        }
        if (request.getPropertyType() == null || request.getPropertyType().trim().isEmpty()) {
            throw new DomainValidationException("Property type is required");
        }
        if (request.getPropertySegment() == null || request.getPropertySegment().trim().isEmpty()) {
            throw new DomainValidationException("Property segment is required");
        }
        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            throw new DomainValidationException("Property description is required");
        }

        // Validate images (between 1 and 4 images, exactly one cover)
        List<HotelImageDto> imageDtos = request.getImages() != null ? request.getImages() : Collections.emptyList();
        if (imageDtos.isEmpty() || imageDtos.size() > 4) {
            throw new DomainValidationException("Between 1 and 4 images must be provided");
        }
        for (HotelImageDto img : imageDtos) {
            if (img.getImageUrl() == null || img.getImageUrl().trim().isEmpty()) {
                throw new DomainValidationException("Each image must have a valid image URL");
            }
        }
        long coverCount = imageDtos.stream().filter(HotelImageDto::isCover).count();
        if (coverCount == 0) {
            imageDtos.get(0).setCover(true);
        } else if (coverCount > 1) {
            boolean firstCoverSet = false;
            for (HotelImageDto img : imageDtos) {
                if (img.isCover()) {
                    if (!firstCoverSet) {
                        firstCoverSet = true;
                    } else {
                        img.setCover(false);
                    }
                }
            }
        }

        // 2. Validate Step 2 fields
        if (request.getAddressLine1() == null || request.getAddressLine1().trim().isEmpty()) {
            throw new DomainValidationException("Address Line 1 is required");
        }
        if (request.getCountry() == null || request.getCountry().trim().isEmpty()) {
            throw new DomainValidationException("Country is required");
        }
        if (request.getState() == null || request.getState().trim().isEmpty()) {
            throw new DomainValidationException("State is required");
        }
        if (request.getCity() == null || request.getCity().trim().isEmpty()) {
            throw new DomainValidationException("City is required");
        }
        if (request.getPostalCode() == null || request.getPostalCode().trim().isEmpty()) {
            throw new DomainValidationException("Postal Code is required");
        }

        // 3. Validate Step 3 fields
        if (request.getPropertyContactNumber() == null || request.getPropertyContactNumber().trim().isEmpty()) {
            throw new DomainValidationException("Property Contact Number is required");
        }
        if (request.getPropertyEmail() == null || request.getPropertyEmail().trim().isEmpty()) {
            throw new DomainValidationException("Property Email Address is required");
        }
        if (request.getRegisteredBusinessName() == null || request.getRegisteredBusinessName().trim().isEmpty()) {
            throw new DomainValidationException("Registered Business Name is required");
        }
        if (request.getBusinessRegistrationType() == null || request.getBusinessRegistrationType().trim().isEmpty()) {
            throw new DomainValidationException("Business Registration Type is required");
        }
        if (request.getBusinessRegistrationNumber() == null || request.getBusinessRegistrationNumber().trim().isEmpty()) {
            throw new DomainValidationException("Business Registration Number is required");
        }
        if (request.getPropertyManagerName() == null || request.getPropertyManagerName().trim().isEmpty()) {
            throw new DomainValidationException("Property Manager Full Name is required");
        }
        if (request.getPropertyManagerDesignation() == null || request.getPropertyManagerDesignation().trim().isEmpty()) {
            throw new DomainValidationException("Property Manager Designation is required");
        }
        if (request.getPropertyManagerContactNumber() == null || request.getPropertyManagerContactNumber().trim().isEmpty()) {
            throw new DomainValidationException("Property Manager Contact Number is required");
        }
        if (request.getPropertyManagerEmail() == null || request.getPropertyManagerEmail().trim().isEmpty()) {
            throw new DomainValidationException("Property Manager Email Address is required");
        }
        if (request.getCheckInTime() == null || request.getCheckInTime().trim().isEmpty()) {
            throw new DomainValidationException("Check-in Time is required");
        }
        if (request.getCheckOutTime() == null || request.getCheckOutTime().trim().isEmpty()) {
            throw new DomainValidationException("Check-out Time is required");
        }
        if (request.getReceptionAvailability() == null || request.getReceptionAvailability().trim().isEmpty()) {
            throw new DomainValidationException("Reception Availability is required");
        }

        // 4. Validate Step 4 fields
        if (request.getChildrenPolicy() == null || request.getChildrenPolicy().trim().isEmpty()) {
            throw new DomainValidationException("Children policy is required");
        }
        if (request.getPetPolicy() == null || request.getPetPolicy().trim().isEmpty()) {
            throw new DomainValidationException("Pet policy is required");
        }
        if (request.getSmokingPolicy() == null || request.getSmokingPolicy().trim().isEmpty()) {
            throw new DomainValidationException("Smoking policy is required");
        }
        if (request.getExtraBedPolicy() == null || request.getExtraBedPolicy().trim().isEmpty()) {
            throw new DomainValidationException("Extra bed policy is required");
        }
        if (request.getAcceptedIdentificationTypes() == null || request.getAcceptedIdentificationTypes().isEmpty()) {
            throw new DomainValidationException("At least one accepted identification type is required");
        }

        // Apply Step 1 updates
        property.setName(request.getName().trim());
        property.setPropertyType(request.getPropertyType().trim());
        property.setPropertyClassification(request.getPropertyClassification() != null ? request.getPropertyClassification().trim() : null);
        property.setCustomClassification(request.getCustomClassification() != null ? request.getCustomClassification().trim() : null);
        property.setPropertySegment(request.getPropertySegment().trim());
        property.setBrandName(request.getBrandName() != null ? request.getBrandName().trim() : null);
        property.setDescription(request.getDescription().trim());

        // Derive starRating if applicable
        if (request.getPropertyClassification() != null) {
            String pc = request.getPropertyClassification().toUpperCase();
            if (pc.contains("5_STAR")) property.setStarRating(5);
            else if (pc.contains("4_STAR")) property.setStarRating(4);
            else if (pc.contains("3_STAR")) property.setStarRating(3);
            else if (pc.contains("2_STAR")) property.setStarRating(2);
            else if (pc.contains("1_STAR")) property.setStarRating(1);
        }

        // Apply Step 2 updates
        property.setAddressLine1(request.getAddressLine1().trim());
        property.setAddressLine2(request.getAddressLine2() != null ? request.getAddressLine2().trim() : null);
        property.setCountry(request.getCountry().trim());
        property.setState(request.getState().trim());
        property.setCity(request.getCity().trim());
        property.setPostalCode(request.getPostalCode().trim());
        property.setLandmark(request.getLandmark() != null ? request.getLandmark().trim() : null);
        property.setAddress(buildLegacyAddress(property));

        // Apply Step 3 updates
        property.setPropertyContactNumber(request.getPropertyContactNumber().trim());
        property.setAlternateContactNumber(request.getAlternateContactNumber() != null ? request.getAlternateContactNumber().trim() : null);
        property.setPropertyEmail(request.getPropertyEmail().trim().toLowerCase());
        property.setWebsiteUrl(request.getWebsiteUrl() != null ? request.getWebsiteUrl().trim() : null);
        property.setRegisteredBusinessName(request.getRegisteredBusinessName().trim());
        property.setBusinessRegistrationType(request.getBusinessRegistrationType().trim());
        property.setOtherBusinessRegistrationType(request.getOtherBusinessRegistrationType() != null ? request.getOtherBusinessRegistrationType().trim() : null);
        property.setBusinessRegistrationNumber(request.getBusinessRegistrationNumber().trim());
        property.setRegisteredBusinessAddress(request.getRegisteredBusinessAddress() != null ? request.getRegisteredBusinessAddress().trim() : null);
        property.setIsGstRegistered(Boolean.TRUE.equals(request.getIsGstRegistered()));
        property.setGstin(request.getGstin() != null ? request.getGstin().trim() : null);
        property.setGstLegalName(request.getGstLegalName() != null ? request.getGstLegalName().trim() : null);
        property.setGstState(request.getGstState() != null ? request.getGstState().trim() : null);

        property.setPropertyManagerName(request.getPropertyManagerName().trim());
        property.setPropertyManagerDesignation(request.getPropertyManagerDesignation().trim());
        property.setPropertyManagerContactNumber(request.getPropertyManagerContactNumber().trim());
        property.setPropertyManagerEmail(request.getPropertyManagerEmail().trim().toLowerCase());

        property.setCheckInTime(request.getCheckInTime().trim());
        property.setCheckOutTime(request.getCheckOutTime().trim());
        property.setReceptionAvailability(request.getReceptionAvailability().trim());
        property.setReceptionOpeningTime(request.getReceptionOpeningTime() != null ? request.getReceptionOpeningTime().trim() : null);
        property.setReceptionClosingTime(request.getReceptionClosingTime() != null ? request.getReceptionClosingTime().trim() : null);

        // Apply Step 4 updates
        try {
            property.setPredefinedAmenities(OBJECT_MAPPER.writeValueAsString(request.getPredefinedAmenities() != null ? request.getPredefinedAmenities() : Collections.emptyList()));
            property.setCustomAmenities(OBJECT_MAPPER.writeValueAsString(request.getCustomAmenities() != null ? request.getCustomAmenities() : Collections.emptyList()));
            property.setAcceptedIdentificationTypes(OBJECT_MAPPER.writeValueAsString(request.getAcceptedIdentificationTypes() != null ? request.getAcceptedIdentificationTypes() : Collections.emptyList()));
        } catch (Exception e) {
            log.error("Failed to serialize policies or amenities to JSON for hotel ID: {}", hotelId, e);
        }
        property.setChildrenPolicy(request.getChildrenPolicy().trim());
        property.setPetPolicy(request.getPetPolicy().trim());
        property.setSmokingPolicy(request.getSmokingPolicy().trim());
        property.setExtraBedPolicy(request.getExtraBedPolicy().trim());
        property.setCustomIdentificationType(request.getCustomIdentificationType() != null ? request.getCustomIdentificationType().trim() : null);
        property.setAdditionalRules(request.getAdditionalRules() != null ? request.getAdditionalRules().trim() : null);

        // Update legacy amenities comma-separated string for backward compatibility
        List<String> combinedAmenities = new ArrayList<>();
        if (request.getPredefinedAmenities() != null) combinedAmenities.addAll(request.getPredefinedAmenities());
        if (request.getCustomAmenities() != null) combinedAmenities.addAll(request.getCustomAmenities());
        if (!combinedAmenities.isEmpty()) {
            property.setAmenities(String.join(", ", combinedAmenities));
        }

        // Set cover image URL on property
        HotelImageDto coverDto = imageDtos.stream()
                .filter(HotelImageDto::isCover)
                .findFirst()
                .orElse(imageDtos.get(0));
        property.setImageUrl(coverDto.getImageUrl());

        // Process image changes and file cleanup
        List<HotelImage> oldImages = hotelImageRepository.findByHotelIdOrderByDisplayOrderAsc(hotelId);
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
                    log.info("Cleaned up removed hotel image file: {}", filename);
                }
            }
        }

        // Delete previous image records and re-persist the updated collection
        hotelImageRepository.deleteByHotelId(hotelId);

        List<HotelImage> imageEntities = new ArrayList<>();
        for (int i = 0; i < imageDtos.size(); i++) {
            HotelImageDto imgDto = imageDtos.get(i);
            HotelImage img = HotelImage.builder()
                    .hotelId(hotelId)
                    .imageUrl(imgDto.getImageUrl().trim())
                    .originalFileName(imgDto.getOriginalFileName())
                    .isCover(imgDto.isCover())
                    .displayOrder(imgDto.getDisplayOrder() >= 0 ? imgDto.getDisplayOrder() : i)
                    .build();
            imageEntities.add(img);
        }
        List<HotelImage> savedImages = hotelImageRepository.saveAll(imageEntities);

        // Save property - protected data (status, managerId, registrationStep, submittedAt, activatedAt) are preserved!
        HotelProperty savedProperty = hotelRepository.save(property);
        log.info("Full property management update completed for hotel ID: {} by user ID: {}", hotelId, principal.userId());

        List<RoomType> rooms = roomTypeRepository.findByHotelId(hotelId);
        return HotelManageDto.fromEntity(savedProperty, rooms, savedImages);
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

    
    private void validateStep2Request(HotelStep2RequestDto request) {
        if (request == null) {
            throw new DomainValidationException("Step 2 request body cannot be null");
        }

        // 1. Address Line 1
        if (request.getAddressLine1() == null || request.getAddressLine1().trim().isEmpty()) {
            throw new DomainValidationException("Address Line 1 is mandatory");
        }
        if (request.getAddressLine1().trim().length() > 255) {
            throw new DomainValidationException("Address Line 1 cannot exceed 255 characters");
        }

        // 2. Address Line 2 (Optional)
        if (request.getAddressLine2() != null && request.getAddressLine2().trim().length() > 255) {
            throw new DomainValidationException("Address Line 2 cannot exceed 255 characters");
        }

        // 3. Country
        if (request.getCountry() == null || request.getCountry().trim().isEmpty()) {
            throw new DomainValidationException("Country is mandatory");
        }
        if (request.getCountry().trim().length() > 100) {
            throw new DomainValidationException("Country cannot exceed 100 characters");
        }
        if (!AddressReferenceData.isCountrySupported(request.getCountry())) {
            throw new DomainValidationException("Unsupported country: " + request.getCountry());
        }

        // 4. State
        if (request.getState() == null || request.getState().trim().isEmpty()) {
            throw new DomainValidationException("State is mandatory");
        }
        if (request.getState().trim().length() > 100) {
            throw new DomainValidationException("State cannot exceed 100 characters");
        }
        if (!AddressReferenceData.isStateValidForCountry(request.getCountry(), request.getState())) {
            throw new DomainValidationException("State '" + request.getState() + "' is not valid for country '" + request.getCountry() + "'");
        }

        // 5. City
        if (request.getCity() == null || request.getCity().trim().isEmpty()) {
            throw new DomainValidationException("City is mandatory");
        }
        if (request.getCity().trim().length() > 255) {
            throw new DomainValidationException("City cannot exceed 255 characters");
        }

        // 6. Postal Code
        if (request.getPostalCode() == null || request.getPostalCode().trim().isEmpty()) {
            throw new DomainValidationException("Postal Code is mandatory");
        }
        if (request.getPostalCode().trim().length() > 20) {
            throw new DomainValidationException("Postal Code cannot exceed 20 characters");
        }
        if (!AddressReferenceData.isValidPostalCode(request.getCountry(), request.getPostalCode())) {
            String normCountry = AddressReferenceData.normalizeCountry(request.getCountry());
            if ("India".equalsIgnoreCase(normCountry)) {
                throw new DomainValidationException("Invalid postal code for India. Please enter a valid 6-digit PIN code (e.g., 520010)");
            } else {
                throw new DomainValidationException("Invalid postal code format for " + normCountry);
            }
        }

        // 7. Landmark (Optional)
        if (request.getLandmark() != null && request.getLandmark().trim().length() > 255) {
            throw new DomainValidationException("Landmark cannot exceed 255 characters");
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
