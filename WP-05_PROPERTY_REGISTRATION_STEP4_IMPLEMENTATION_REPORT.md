# TravelEasy — WP-05: Hotel Property Registration Wizard
## Implementation Report: Step 4 — Facilities & Policies

**Work Package:** WP-05-REG-4  
**Date:** November 7, 2026  
**Status:** **COMPLETED & VERIFIED**  
**Authoritative Visual Mockup:** `TravelEasy Facilities & Policies Setup(1).png` (`media_1794041582166_418ee857.jpg`)  

---

## 1. Executive Summary

This report documents the end-to-end design, implementation, and verification of **Step 4 — Facilities & Policies** in the Hotel Manager Property Registration Wizard. 

Step 4 empowers hotel managers to configure property-wide amenities, define authoritative guest policies, specify accepted identification documents, and provide optional property rules or guest instructions.

The implementation faithfully reproduces the approved visual reference (`media_1794041582166_418ee857.jpg`), adhering strictly to all portal styling conventions, architectural constraints, security isolation boundaries, and business decisions (`BD-02`, `BD-03`).

### Key Accomplishments:
1. **Database Schema Evolution:** Extended `hotel_properties` in MySQL `traveleasy_hotel_db` with 9 dedicated columns (`predefined_amenities`, `custom_amenities`, `children_policy`, `pet_policy`, `smoking_policy`, `extra_bed_policy`, `accepted_identification_types`, `custom_identification_type`, `additional_rules`).
2. **Backend API & Service Integration:** Implemented `POST /api/hotels/draft/{draftId}/step-4` and `POST /api/hotels/draft/step-4` in [`HotelController.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/controller/HotelController.java) and [`HotelPropertyService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java) with comprehensive server-side validation and backward compatibility.
3. **High-Fidelity Angular UI:** Implemented the 3-section accordion UI (`Property Facilities & Amenities`, `Guest Policies`, `Additional Property Rules`), 7 predefined category cards with 18 standard amenities, dynamic custom amenity creation with duplicate detection, single-choice and multi-select policy controls, live character counters, and auto-expanding error navigation.
4. **Comprehensive Test Verification:**
   - Dedicated test suite [`HotelStep4RegistrationTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/test/java/com/traveleasy/hotel/HotelStep4RegistrationTest.java): **13/13 tests passed (100%)**.
   - Entire `hotel-service` test suite: **141/141 tests passed (0 failures, 0 errors)**.
   - Angular production build (`npm run build`): **100% clean compilation (0 errors, 1.70 MB production bundle)**.

---

## 2. Files Created and Modified

### Backend (`hotel-service`):
- [`HotelProperty.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/model/HotelProperty.java): Extended JPA entity with 9 structured Step 4 fields.
- [`HotelStep4RequestDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep4RequestDto.java) *(New)*: Request DTO with Jakarta validation annotations.
- [`HotelStep4ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep4ResponseDto.java) *(New)*: Comprehensive response DTO with JSON array parsing helpers and legacy field mapping.
- [`HotelStep1ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep1ResponseDto.java), [`HotelStep2ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep2ResponseDto.java), [`HotelStep3ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep3ResponseDto.java): Updated to serialize Step 4 fields on draft resumption.
- [`HotelPropertyService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java): Added Step 4 business constants, validation methods, legacy amenity synthesizer, and draft persistence.
- [`HotelController.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/controller/HotelController.java): Exposed `POST /draft/{draftId}/step-4` and `POST /draft/step-4`.
- [`HotelStep4RegistrationTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/test/java/com/traveleasy/hotel/HotelStep4RegistrationTest.java) *(New)*: 13 integration and validation tests.

### Frontend (`frontend`):
- [`hotel.model.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/models/hotel.model.ts): Added `HotelStep4Request`, `HotelStep4Response`, and Step 4 fields to `HotelStep1Response`.
- [`hotel.service.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/services/hotel.service.ts): Added `saveStep4Draft(draftId, step4Data)`.
- [`hotel-manager-dashboard.component.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts): Added Step 4 data model, accordion state machine, custom amenity management, validation methods, and bidirectional navigation.
- [`hotel-manager-dashboard.component.html`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html): Replaced Step 4 placeholder with the full form card matching the visual mockup and added the Step 5 placeholder.
- [`hotel-manager-dashboard.component.css`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.css): Added `.amenity-category-card`, `.custom-checkbox`, and hover states.

---

## 3. UI Implementation & Visual Accuracy

The UI faithfully implements the layout and visual styling of `media_1794041582166_418ee857.jpg`:
- **Wizard Stepper:**
  - Node 1 (Basic Details): Completed (teal circle with checkmark).
  - Node 2 (Location & Address): Completed (teal circle with checkmark).
  - Node 3 (Property Information): Completed (teal circle with checkmark).
  - Node 4 (Facilities & Policies): Active (teal circle with number "4").
  - Node 5 (Review & Submit): Pending (grey circle with number "5").
- **Header Card:**
  - Teal icon badge with building icon (`fa-solid fa-city`).
  - Title: `Step 4 of 5: Facilities & Policies`.
  - Subtitle: `Add property amenities, define guest policies and share additional property rules.`
- **Design System Fidelity:**
  - Light mode palette with clean white cards (`#ffffff`).
  - TravelEasy teal primary accent (`#0d9488`).
  - High-contrast typography and subtle borders (`#e5e7eb`).
  - Zero decorative clutter or extraneous illustrations.

---

## 4. Accordion Behavior

1. **Three Subsections:**
   - 1. `Property Facilities & Amenities` (expanded by default).
   - 2. `Guest Policies` (collapsed initially).
   - 3. `Additional Property Rules` (collapsed initially).
2. **Single-Expansion Rule:**
   - Expanding any section automatically collapses whichever section was previously open.
   - Clicking an open section header collapses it.
3. **Data Retention:**
   - Form state in Angular (`step4Data`) is decoupled from the accordion presentation state (`expandedStep4Section`).
   - Toggling sections preserves all checkbox selections, radio choices, custom inputs, and textarea text without loss.
4. **Smart Auto-Expansion on Validation Failure:**
   - When the manager clicks `Next: Review & Submit →`, all subsections are validated.
   - If validation fails, the wizard automatically expands the first offending subsection and brings the invalid input into view with actionable error feedback.

---

## 5. Predefined Amenities Implementation

The 7 predefined categories and 18 standard amenities are organized in a responsive grid:

| Category | Category Icon | Included Predefined Amenities | Grid Span |
| :--- | :--- | :--- | :--- |
| **Connectivity** | `fa-wifi` | Wi-Fi, Business Centre | 1/3 row |
| **Dining** | `fa-utensils` | Restaurant, Room Service, Complimentary Breakfast | 1/3 row |
| **Transport** | `fa-car` | Parking, Airport Shuttle | 1/3 row |
| **Recreation** | `fa-water` | Swimming Pool, Gym, Spa | 1/3 row |
| **Safety** | `fa-shield-halved` | CCTV, 24-hour Security, Fire Safety | 1/3 row |
| **Accessibility** | `fa-wheelchair` | Accessible Entrance, Elevator | 1/3 row |
| **General** | `fa-layer-group` | Laundry, Reception, Luggage Storage | Full Width |

- Each amenity has an independent checkbox with teal selection state.
- New draft registrations begin with empty selections (illustrative mockups are not hardcoded).
- Selections are retained across section toggling and saved/resumed as clean JSON arrays.

---

## 6. Custom Amenities Implementation

- **Action Button:** `+ Add Custom Amenity` button in the top-right corner of the section header.
- **Inline Lightweight Input:** Clicking displays an inline input box with input field, `Add` button, and `Cancel` button.
- **Validation Constraints:**
  - Empty or whitespace-only values are rejected.
  - Length is capped at 50 characters.
  - Case-insensitive duplicates of existing predefined amenities are prohibited (e.g. attempting to add `"wi-fi"` returns a validation error).
  - Case-insensitive duplicates among custom amenities are prohibited.
- **Presentation:**
  - Added custom amenities render in a dedicated `Custom Amenities` category card with light grey pill badges.
  - Each custom badge contains a remove button (`fa-solid fa-xmark`) allowing managers to delete custom items on the fly.

---

## 7. Guest Policies Implementation

Four mandatory single-choice policies and one multi-select identification requirement:

### A. Mandatory Single-Choice Policies:
1. **Children Policy:** `Children Allowed` | `Children Not Allowed` (Mandatory).
2. **Pet Policy:** `Pets Allowed` | `Pets Not Allowed` (Mandatory).
3. **Smoking Policy:** `Smoking Prohibited` | `Designated Smoking Areas` | `Smoking Allowed` (Mandatory).
4. **Extra Bed Policy:** `Extra Bed Available` | `Extra Bed Not Available` (Mandatory).

### B. Identification Requirements:
- At least one accepted document type is required.
- Standard options: `Passport`, `Driving Licence`, `Voter ID`, `Government-issued Photo ID`, `Aadhaar Card`, `Other`.
- **Conditional Custom ID:** When `Other` is selected, a required text input (`customIdentificationType`) appears dynamically (up to 150 characters).

---

## 8. Additional Property Rules Implementation

- **Label:** `Additional Property Rules / Guest Instructions (Optional)`.
- **Field:** Multiline textarea with 4 rows.
- **Placeholder:** `"Enter any additional property rules, guest instructions or important information that visitors should know before booking..."`.
- **Character Constraint:** Live counter displaying `X / 1000 characters`. Values exceeding 1000 characters are rejected by frontend and backend validators.
- **Policy Protection:** Property rules strictly capture property-specific guest guidance (e.g., quiet hours, pool rules) without modifying TravelEasy's centralized 14:00 cancellation cutoff.

---

## 9. Database Persistence & Backward Compatibility

### Schema Migration (MySQL `traveleasy_hotel_db`):
```sql
ALTER TABLE hotel_properties ADD COLUMN predefined_amenities TEXT AFTER description;
ALTER TABLE hotel_properties ADD COLUMN custom_amenities TEXT AFTER predefined_amenities;
ALTER TABLE hotel_properties ADD COLUMN children_policy VARCHAR(50) AFTER custom_amenities;
ALTER TABLE hotel_properties ADD COLUMN pet_policy VARCHAR(50) AFTER children_policy;
ALTER TABLE hotel_properties ADD COLUMN smoking_policy VARCHAR(50) AFTER pet_policy;
ALTER TABLE hotel_properties ADD COLUMN extra_bed_policy VARCHAR(50) AFTER smoking_policy;
ALTER TABLE hotel_properties ADD COLUMN accepted_identification_types TEXT AFTER extra_bed_policy;
ALTER TABLE hotel_properties ADD COLUMN custom_identification_type VARCHAR(150) AFTER accepted_identification_types;
ALTER TABLE hotel_properties ADD COLUMN additional_rules TEXT AFTER custom_identification_type;
```

### Backward Compatibility (Legacy `amenities` column):
To ensure existing search endpoints, consumer booking flows, and admin views continue functioning seamlessly without modifications, `HotelPropertyService` synthesizes a joined string from both predefined and custom amenities (e.g. `"Wi-Fi, Restaurant, Swimming Pool, EV Charging Station"`) and persists it to the legacy `amenities` column.

---

## 10. Security & Ownership Verification

In compliance with `BD-02` and existing security architecture:
1. **Manager Authentication:** All draft updates require a valid Bearer token (`ROLE_HOTEL_MANAGER`). Unauthenticated requests are rejected with `401 Unauthorized`.
2. **Draft Ownership Isolation:** The system verifies that the authenticated `managerId` matches the property's `managerId`. Any attempt by another manager to modify or inspect the draft is rejected with `403 Forbidden`.
3. **Draft Status Enforcement:** Only properties in `DRAFT` status can be modified via the registration endpoints.

---

## 11. Test Execution & Verification Results

### Dedicated Test Suite: `HotelStep4RegistrationTest`
Located at [`HotelStep4RegistrationTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/test/java/com/traveleasy/hotel/HotelStep4RegistrationTest.java).

| Test # | Test Name | Target Verification | Result |
| :---: | :--- | :--- | :---: |
| 1 | `testSaveValidStep4Draft` | Saves valid Step 4 draft, sets step=4, verifies all 3 subsections and legacy amenities string | **PASS** |
| 2 | `testSaveStep4WithOtherIdentification` | Persists 'Other' ID type with custom specification string | **PASS** |
| 3 | `testRejectStep4MissingCustomIdentification` | Rejects 'Other' selection when custom specification is missing (400 Bad Request) | **PASS** |
| 4 | `testRejectStep4EmptyIdentificationList` | Rejects request when accepted identification list is empty | **PASS** |
| 5 | `testRejectCustomAmenityDuplicateOfPredefined` | Rejects custom amenity if it duplicates an existing predefined amenity (case-insensitive) | **PASS** |
| 6 | `testRejectDuplicateCustomAmenities` | Rejects duplicate custom amenities in the request | **PASS** |
| 7 | `testRejectInvalidCustomAmenityLength` | Rejects custom amenity exceeding 50 characters | **PASS** |
| 8 | `testRejectUnsupportedPredefinedAmenity` | Rejects unknown predefined amenities | **PASS** |
| 9 | `testRejectInvalidPolicyOption` | Rejects invalid policy options for mandatory guest policies | **PASS** |
| 10 | `testRejectAdditionalRulesExceedLimit` | Rejects additional rules exceeding 1000 characters | **PASS** |
| 11 | `testEnforceManagerIsolation` | Rejects cross-manager draft mutations with 403 Forbidden | **PASS** |
| 12 | `testUnauthenticatedRequest` | Rejects unauthenticated draft mutations with 401 Unauthorized | **PASS** |
| 13 | `testGetDraftRestoresStep4Information` | Verifies `GET /api/hotels/draft/{draftId}` restores complete Step 4 state | **PASS** |

**Outcome:** `HotelStep4RegistrationTest`: **13/13 tests passed (100%)**.

### Full Regression Suite:
- Command: `mvn -f hotel-service/pom.xml test`
- Outcome: **141/141 tests passed (0 failures, 0 errors, 100% pass rate)**.
- Step 1 (`HotelStep1RegistrationTest`): 19/19 passed.
- Step 2 (`HotelStep2RegistrationTest`): 16/16 passed.
- Step 3 (`HotelStep3RegistrationTest`): 16/16 passed.
- Step 4 (`HotelStep4RegistrationTest`): 13/13 passed.
- Core business tests (Security, Inventory, Bookings, Dashboard): 77/77 passed.

### Frontend Compilation:
- Command: `npm --prefix frontend run build`
- Outcome: **Build Success (0 errors, 0 warnings, 1.70 MB bundle size, 18.0s)**.

---

## 12. Scope Boundary & Next Steps

This work package strictly covers **Step 4 — Facilities & Policies**.
- Navigating forward from Step 4 displays the **Step 5: Review & Submit placeholder card**.
- The property remains in `DRAFT` status and will only activate upon final submission in Step 5 in accordance with `BD-03`.
- No room-level management, pricing, inventory, or booking logic was altered.
- All code changes are verified, compiled, and ready for review.
