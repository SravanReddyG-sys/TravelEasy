# TravelEasy — WP-05: Hotel Property Registration Wizard
## Implementation Report: Step 2 — Location & Address

**Document ID:** TE-WP05-STEP2-REPORT-01  
**Author:** Antigravity AI Pair Programmer  
**Date:** 2026-11-07  
**Status:** **COMPLETED & VERIFIED (112/112 Backend Tests Passing, Angular Build 0 Errors)**  
**Visual Reference:** `TravelEasy Location & Address Portal(1).png` ([View Screenshot](file:///C:/Users/reddy/.gemini/antigravity/brain/eea23d8b-ad0e-4f6f-93fd-d2d485cfbd8d/.user_uploaded/media_1794028783032_0549f050.jpg))  

---

## 1. Executive Summary

This report documents the design, implementation, and verification of **Step 2 — Location & Address** of the Hotel Manager Property Registration Wizard for TravelEasy. 

Following the completion and hardening of Step 1 (Basic Property Details), Step 2 enables hotel managers to capture and persist comprehensive structured location data for new properties. The implementation establishes high-fidelity UI parity with the approved visual reference (`TravelEasy Location & Address Portal(1).png`), provides robust backend persistence and validation, maintains full backward compatibility with legacy address consumers, enforces strict manager ownership isolation, and enables seamless bidirectional navigation between wizard steps without data loss.

### Key Deliverables Completed:
1. **Database Schema Evolution:** Expanded MySQL `hotel_properties` table with 6 structured columns (`address_line1`, `address_line2`, `country`, `state`, `postal_code`, `landmark`) alongside existing `city` and `address`.
2. **Backward-Compatible Address Synchronization:** Automatic derivation and persistence of the legacy concatenated `address` field ensuring all existing public search, booking, and administrative APIs continue functioning without breaking changes.
3. **Reference Data Architecture:** Standardized country and state reference datasets covering India (28 states + 8 UTs) and 18 international territories with dedicated caching endpoints (`/api/hotels/reference/countries` and `/api/hotels/reference/states`).
4. **Context-Aware Address Validation:** Comprehensive validation rules including mandatory constraints, country-state relationship checks, and country-specific postal code validation (strict 6-digit Indian PIN code `^[1-9][0-9]{5}$` vs. international formats).
5. **Draft Persistence Lifecycle:** Step 2 updates the exact same property draft record initiated in Step 1, incrementing `registrationStep` to `2` without creating duplicate rows or altering `DRAFT` status.
6. **High-Fidelity Angular UI:** Pixel-perfect implementation of the approved 2-column layout with custom teal icon containers, form field icons, live validation feedback, and full wizard action buttons.
7. **Verification & Regression Testing:** Created [`HotelStep2RegistrationTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/test/java/com/traveleasy/hotel/HotelStep2RegistrationTest.java) with 16 automated integration tests. All 112 `hotel-service` tests and Angular production build passed with zero errors.

---

## 2. Architecture & Database Persistence

### 2.1 Database Schema Migration

To avoid fragile string parsing of legacy single-line addresses and support structured geocoding/filtering, six dedicated columns were added to the `hotel_properties` table in `traveleasy_hotel_db`:

```sql
ALTER TABLE hotel_properties 
  ADD COLUMN address_line1 VARCHAR(255) NULL AFTER address,
  ADD COLUMN address_line2 VARCHAR(255) NULL AFTER address_line1,
  ADD COLUMN country VARCHAR(100) NULL AFTER address_line2,
  ADD COLUMN state VARCHAR(100) NULL AFTER country,
  ADD COLUMN postal_code VARCHAR(20) NULL AFTER state,
  ADD COLUMN landmark VARCHAR(255) NULL AFTER postal_code;
```

### 2.2 Entity Definition

In [`HotelProperty.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/model/HotelProperty.java):

```java
@Column(name = "address_line1")
private String addressLine1;

@Column(name = "address_line2")
private String addressLine2;

@Column(name = "country", length = 100)
private String country;

@Column(name = "state", length = 100)
private String state;

@Column(name = "postal_code", length = 20)
private String postalCode;

@Column(name = "landmark")
private String landmark;
```

### 2.3 Legacy Field Synchronization Strategy

To preserve complete backward compatibility across TravelEasy microservices (such as `booking-service` and consumer search components) that expect scalar `city` and `address` fields:
1. `property.setCity(request.getCity().trim())` synchronizes the primary city.
2. The legacy `address` column is synthesized dynamically using the helper `buildLegacyAddress()`:

```java
private String buildLegacyAddress(HotelProperty property) {
    List<String> parts = new ArrayList<>();
    if (property.getAddressLine1() != null && !property.getAddressLine1().isBlank()) {
        parts.add(property.getAddressLine1().trim());
    }
    if (property.getAddressLine2() != null && !property.getAddressLine2().isBlank()) {
        parts.add(property.getAddressLine2().trim());
    }
    if (property.getLandmark() != null && !property.getLandmark().isBlank()) {
        parts.add("Near " + property.getLandmark().trim());
    }
    if (property.getCity() != null && !property.getCity().isBlank()) {
        parts.add(property.getCity().trim());
    }
    if (property.getState() != null && !property.getState().isBlank()) {
        parts.add(property.getState().trim());
    }
    if (property.getPostalCode() != null && !property.getPostalCode().isBlank()) {
        parts.add(property.getPostalCode().trim());
    }
    if (property.getCountry() != null && !property.getCountry().isBlank()) {
        parts.add(property.getCountry().trim());
    }
    return String.join(", ", parts);
}
```

---

## 3. Backend Implementation & APIs

### 3.1 DTOs & Validation Models

- [`HotelStep2RequestDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep2RequestDto.java):
  - `draftId`: Optional in body if specified in URL path.
  - `addressLine1`: Mandatory (`@NotBlank`, `@Size(max = 255)`).
  - `addressLine2`: Optional (`@Size(max = 255)`).
  - `country`: Mandatory (`@NotBlank`, `@Size(max = 100)`).
  - `state`: Mandatory (`@NotBlank`, `@Size(max = 100)`).
  - `city`: Mandatory (`@NotBlank`, `@Size(max = 255)`).
  - `postalCode`: Mandatory (`@NotBlank`, `@Size(max = 20)`).
  - `landmark`: Optional (`@Size(max = 255)`).
- [`HotelStep2ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep2ResponseDto.java):
  - Returns complete property state including Step 1 fields (`name`, `propertyType`, `propertyClassification`, `propertySegment`, `images`, `brandName`, `shortDescription`) and Step 2 structured address fields, current `registrationStep = 2`, and `status = "DRAFT"`.
- [`HotelStep1ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep1ResponseDto.java) & [`HotelManageDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelManageDto.java):
  - Updated to expose all 6 structured address fields so managers can inspect and resume saved drafts at any time.

### 3.2 Reference Data Management

The [`AddressReferenceData.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/AddressReferenceData.java) utility centralizes reference data:
- Supported Countries: India (default), United States, United Kingdom, United Arab Emirates, Australia, Canada, Singapore, Germany, France, Italy, Spain, Netherlands, Switzerland, Japan, Thailand, Malaysia, Indonesia, Maldives, Sri Lanka.
- Indian States & UTs: Complete authoritative list of all 28 states and 8 union territories (Andhra Pradesh, Telangana, Karnataka, Tamil Nadu, Maharashtra, Delhi, Ladakh, etc.).
- Regional Data: US States (50 states + DC), UK constituent countries, UAE Emirates, Australian states, Canadian provinces.
- Postal Code Validation Rules:
  - India: Must be exactly 6 digits not starting with 0 (`^[1-9][0-9]{5}$`).
  - International: Alphanumeric format with optional space/hyphen between 3 and 10 characters (`^[a-zA-Z0-9\s-]{3,10}$`).

### 3.3 Endpoints Implemented

1. `POST /api/hotels/draft/{draftId}/step-2`: Authoritative endpoint to persist Step 2 details for an existing draft.
2. `POST /api/hotels/draft/step-2`: Flexible fallback accepting `draftId` in payload.
3. `GET /api/hotels/reference/countries`: Returns sorted list of supported countries.
4. `GET /api/hotels/reference/states?country={country}`: Returns dependent states/provinces for the requested country.

### 3.4 Business Rules & Security Enforcements

- **Draft State Verification:** Only records with `status = "DRAFT"` can be updated via the wizard. Attempting to modify an active hotel triggers `DomainValidationException`.
- **Manager Ownership Isolation:** Validates caller's JWT `userId`. If manager A attempts to save address details for manager B's draft, the system aborts with `OwnershipAccessDeniedException` (HTTP 403 Forbidden).
- **Single-Row Idempotency:** Repeated submissions for the same draft update the existing database row rather than creating new entries.
- **Validation Consistency:** Verifies that the selected `state` belongs to the selected `country`.

---

## 4. Frontend Wizard Implementation

### 4.1 UI Layout & Design Matching

The frontend was constructed in [`hotel-manager-dashboard.component.html`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html) and styled in [`hotel-manager-dashboard.component.css`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.css) to achieve exact visual parity with `media_1794028783032_0549f050.jpg`:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│  ← Back to My Properties                                                               │
│  Register New Property                                                                 │
│  Add a new hotel property to your portfolio. Complete the following steps to...        │
├────────────────────────────────────────────────────────────────────────────────────────┤
│    [✓] Basic Details ──── (2) Location & Address ──── (3) ──── (4) ──── (5)            │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  ┌───┐  Step 2 of 5: Location & Address                                                │
│  │ 📍│  Provide the complete address of your property for accurate listing...          │
│  └───┘                                                                                 │
│                                                                                        │
│  Address Line 1 *                           Address Line 2 (Optional)                  │
│  [🏢  e.g., Sunrise Grand Hotel, 24 MG Rd]  [🏢  e.g., Ashok Nagar, Benz Circle Area]  │
│                                                                                        │
│  Country *                                  State *                                    │
│  [🌐  India                           ▼]   [◇  Andhra Pradesh                    ▼]   │
│                                                                                        │
│  City *                                     Postal Code *                              │
│  [🏢  e.g., Vijayawada                   ]  [✉  e.g., 520010                         ] │
│                                                                                        │
│  Landmark (Optional)                                                                   │
│  [📍  e.g., Near Benz Circle, Opposite PVR Mall                                      ] │
│                                                                                        │
│                                    [← Previous: Basic Details] [Cancel] [Next: ... →]  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Key Frontend Behaviors

- **Dynamic State Cascading:** Changing the Country dropdown automatically clears the State selection, re-evaluates the state list, and adjusts postal code validation feedback.
- **Strict Indian PIN Code Helper:** For India, the postal code input restricts characters to numeric digits and checks the `^[1-9][0-9]{5}$` format with live visual feedback.
- **Bi-directional Step Navigation:**
  - `← Previous: Basic Details`: Switches wizard back to Step 1 while retaining all Step 2 form values in memory and state.
  - `Next: Property Information →`: Validates all Step 2 inputs, submits data to `/api/hotels/draft/{draftId}/step-2`, updates `currentWizardStep = 3`, and shows the Step 3 placeholder.
  - `Cancel`: Prompts confirmation if form has unsaved modifications; redirects cleanly to the properties list.
- **Draft Resumption Support:** When opening an existing draft from My Properties, all Step 1 and Step 2 values are populated seamlessly.

---

## 5. Automated Verification & Testing

### 5.1 Dedicated Step 2 Test Suite (`HotelStep2RegistrationTest.java`)

A comprehensive integration test suite with 16 automated tests was executed against a live test context:

| Test Case | Scenario / Condition Tested | Expected Outcome | Status |
|---|---|---|---|
| `testSaveStep2Draft_Success` | Valid address payload for India with landmark and addressLine2 | HTTP 200, structured address persisted, legacy address synchronized | **PASS** |
| `testSaveStep2Draft_WithoutOptionalFields` | Valid address without addressLine2 and landmark | HTTP 200, optional fields null, mandatory fields stored | **PASS** |
| `testPostalCode_IndianPin_Valid` | Valid 6-digit Indian PIN (`520010`, `500081`, `560001`) | HTTP 200, successfully accepted | **PASS** |
| `testPostalCode_IndianPin_InvalidFormat` | Letters, 5 digits, 7 digits, or starting with 0 | HTTP 400 Bad Request, domain validation error | **PASS** |
| `testPostalCode_International_Valid` | Valid US ZIP code `94043` or UK postcode `SW1A 1AA` | HTTP 200, successfully accepted | **PASS** |
| `testPostalCode_International_Invalid` | International postal codes with invalid characters | HTTP 400 Bad Request | **PASS** |
| `testValidateAddressLine1_Mandatory` | Missing or blank `addressLine1` | HTTP 400 Bad Request | **PASS** |
| `testValidateCountry_Mandatory` | Missing or blank `country` | HTTP 400 Bad Request | **PASS** |
| `testValidateCountry_Unsupported` | Unrecognized / unsupported country name | HTTP 400 Bad Request | **PASS** |
| `testValidateState_Mandatory` | Missing or blank `state` | HTTP 400 Bad Request | **PASS** |
| `testValidateState_CountryConsistency` | State not matching Country (e.g. California for India) | HTTP 400 Bad Request | **PASS** |
| `testValidateCity_Mandatory` | Missing or blank `city` | HTTP 400 Bad Request | **PASS** |
| `testStep2_ReusesExistingDraftRecord` | Saving Step 2 on existing Step 1 draft | Draft record updated in-place without duplicating rows | **PASS** |
| `testSecurity_CrossManagerAccessForbidden` | Manager B attempts to update Manager A's draft | HTTP 403 Forbidden, ownership enforced | **PASS** |
| `testSecurity_UnauthenticatedRequest` | Step 2 request without JWT Authorization header | HTTP 401 Unauthorized | **PASS** |
| `testReferenceData_Endpoints` | Country and dependent state REST endpoints | HTTP 200, returns full list of countries and states | **PASS** |

### 5.2 Full Test Suite & Build Results

1. **`HotelStep2RegistrationTest`:** 16/16 Passed (0 Failures, 0 Errors, 7.8s elapsed).
2. **`HotelStep1RegistrationTest`:** 19/19 Passed (0 Failures, 0 Errors, 8.5s elapsed).
3. **Full `hotel-service` Maven Test Suite:** **112/112 Tests Passed** across all domain, security, inventory, and registration modules.
4. **Angular Production Build:** `ng build` completed cleanly in 10.4 seconds with **0 compilation errors** and generated the optimized bundle (1.61 MB main bundle).

---

## 6. Artifact & File Change Manifest

| Module / Layer | File Path | Primary Modifications |
|---|---|---|
| **Database** | `hotel_properties` | Added 6 structured columns (`address_line1`, `address_line2`, `country`, `state`, `postal_code`, `landmark`) |
| **Backend Model** | [`HotelProperty.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/model/HotelProperty.java) | Added JPA entity fields for structured address |
| **Backend Service** | [`AddressReferenceData.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/AddressReferenceData.java) | Countries, states, validation rules, normalization logic |
| **Backend DTOs** | [`HotelStep2RequestDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep2RequestDto.java) | Request validation annotations and builder |
| **Backend DTOs** | [`HotelStep2ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep2ResponseDto.java) | Complete registration draft response representation |
| **Backend DTOs** | [`HotelStep1ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep1ResponseDto.java) | Added address fields for draft round-trips |
| **Backend DTOs** | [`HotelManageDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelManageDto.java) | Exposed structured address fields in management view |
| **Backend Service** | [`HotelPropertyService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java) | Implemented `saveStep2Draft()`, `buildLegacyAddress()`, and `validateStep2Request()` |
| **Backend Controller** | [`HotelController.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/controller/HotelController.java) | Added Step 2 persistence endpoints and reference data endpoints |
| **Backend Tests** | [`HotelStep2RegistrationTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/test/java/com/traveleasy/hotel/HotelStep2RegistrationTest.java) | 16 dedicated unit and integration tests |
| **Frontend Model** | [`hotel.model.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/models/hotel.model.ts) | Added TypeScript interfaces `HotelStep2Request`, `HotelStep2Response` |
| **Frontend Service** | [`hotel.service.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/services/hotel.service.ts) | Added `saveStep2Draft()` and reference data lookup methods |
| **Frontend Component** | [`hotel-manager-dashboard.component.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts) | State management, field validation, country-state cascading, draft navigation |
| **Frontend Template** | [`hotel-manager-dashboard.component.html`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html) | High-fidelity form card matching screenshot and Step 3 placeholder |
| **Frontend Styling** | [`hotel-manager-dashboard.component.css`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.css) | Custom styles for `.step-icon-box`, `.btn-wizard-prev`, input icons |

---

## 7. Conclusion & Next Steps

WP-05 Step 2 (Location & Address) is completely implemented, verified, and hardened. It seamlessly integrates with Step 1, maintains database integrity, prevents security violations across managers, and provides an intuitive, high-quality user experience.

Upon user instruction, the project is ready to proceed to:
- **WP-05 Step 3 — Property Information:** Tax identifiers (GSTIN/PAN), check-in / check-out policies, room inventory summary, and operational contacts.
