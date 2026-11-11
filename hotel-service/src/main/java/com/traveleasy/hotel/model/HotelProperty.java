package com.traveleasy.hotel.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hotel_properties")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelProperty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long managerId;
    private String managerName;
    private String name;

    @Column(length = 100)
    private String propertyType; // Hotel, Resort, Villa, etc.

    // Structured Address Fields (WP-05 Step 2)
    @Column(length = 255)
    private String addressLine1;

    @Column(length = 255)
    private String addressLine2;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String state;

    private String city;

    @Column(length = 20)
    private String postalCode;

    @Column(length = 255)
    private String landmark;

    // Legacy address field preserved for backward compatibility
    private String address;

    private Integer starRating;

    @Column(length = 100)
    private String propertyClassification; // NOT_CLASSIFIED, 1_STAR, 5_STAR_DELUXE, OTHER, etc.

    @Column(length = 100)
    private String customClassification; // when classification is OTHER

    @Column(length = 50)
    private String propertySegment; // STANDARD, COMFORT, PREMIUM, LUXURY, ULTRA_LUXURY

    @Column(length = 150)
    private String brandName;

    @Column(length = 2000)
    private String description;
    private String amenities; // Swimming Pool, Free Wi-Fi, Restaurant, Gym, Spa
    private String imageUrl; // Primary/Cover image URL for backward compatibility

    // =========================================================================
    // WP-05 STEP 3: PROPERTY INFORMATION
    // =========================================================================

    // 1. Property Contact Details (Public contact information)
    @Column(length = 30)
    private String propertyContactNumber;

    @Column(length = 30)
    private String alternateContactNumber;

    @Column(length = 100)
    private String propertyEmail;

    @Column(length = 255)
    private String websiteUrl;

    // 2. Business Registration Details (Legal registration)
    @Column(length = 255)
    private String registeredBusinessName;

    @Column(length = 100)
    private String businessRegistrationType;

    @Column(length = 100)
    private String otherBusinessRegistrationType;

    @Column(length = 100)
    private String businessRegistrationNumber;

    @Column(columnDefinition = "TEXT")
    private String registeredBusinessAddress;

    // 3. GST Details (Optional tax information)
    @Builder.Default
    private Boolean isGstRegistered = false;

    @Column(length = 15)
    private String gstin;

    @Column(length = 255)
    private String gstLegalName;

    @Column(length = 100)
    private String gstState;

    // 4. Property Manager Details (Designated operational contact)
    @Column(length = 255)
    private String propertyManagerName;

    @Column(length = 100)
    private String propertyManagerDesignation;

    @Column(length = 30)
    private String propertyManagerContactNumber;

    @Column(length = 100)
    private String propertyManagerEmail;

    // 5. Operating Hours (Guest check-in, check-out and reception)
    @Column(length = 20)
    private String checkInTime;

    @Column(length = 20)
    private String checkOutTime;

    @Column(length = 50)
    private String receptionAvailability; // 24_HOUR, LIMITED_HOURS

    @Column(length = 20)
    private String receptionOpeningTime;

    @Column(length = 20)
    private String receptionClosingTime;

    // =========================================================================
    // WP-05 STEP 4: FACILITIES & POLICIES
    // =========================================================================

    // 1. Property Facilities & Amenities
    @Column(columnDefinition = "TEXT")
    private String predefinedAmenities; // JSON array of selected predefined amenity names

    @Column(columnDefinition = "TEXT")
    private String customAmenities; // JSON array of custom amenity names

    // 2. Guest Policies
    @Column(length = 50)
    private String childrenPolicy; // Children Allowed, Children Not Allowed

    @Column(length = 50)
    private String petPolicy; // Pets Allowed, Pets Not Allowed

    @Column(length = 50)
    private String smokingPolicy; // Smoking Prohibited, Designated Smoking Areas, Smoking Allowed

    @Column(length = 50)
    private String extraBedPolicy; // Extra Bed Available, Extra Bed Not Available

    @Column(columnDefinition = "TEXT")
    private String acceptedIdentificationTypes; // JSON array of accepted ID types

    @Column(length = 150)
    private String customIdentificationType; // Conditional when "Other" is in acceptedIdentificationTypes

    // 3. Additional Property Rules
    @Column(columnDefinition = "TEXT")
    private String additionalRules; // Property rules / guest instructions (up to 1000 chars)


    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, INACTIVE, DRAFT

    @Builder.Default
    private Integer registrationStep = 1; // Current wizard step (1 to 5)

    // WP-05 STEP 5: FINAL REGISTRATION & ACTIVATION TIMESTAMPS
    private LocalDateTime submittedAt;
    private LocalDateTime activatedAt;
}
