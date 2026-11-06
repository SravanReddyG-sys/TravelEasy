# TravelEasy — WP-05: Hotel Property Registration Wizard
## Step 1 — Basic Property Details Implementation & Targeted Corrections Report

**Document ID:** `WP-05-REG-STEP1`  
**Execution Date:** November 7, 2026  
**Module:** Hotel Manager Portal (`hotel-service` & `frontend`)  
**Visual Reference:** `TravelEasy Property Registration Form(1).png` (`media_1793991267793_af3aabe3.jpg`)  
**Scope Boundary:** **Step 1 Only** (Basic Property Details). Steps 2 through 5 remain represented via the 5-step stepper bar and a verified Step 2 placeholder.  
**Build & Test Status:**  
- **Targeted Backend Tests:** **19/19 Passed (100%)** in [`HotelStep1RegistrationTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java)  
- **Frontend Build:** **Clean Build (0 Errors)** via Angular CLI (`ng build`)  

---

## 1. Executive Summary & Targeted Corrections Overview

Following the review of the initial Step 1 registration implementation, targeted corrections were applied to resolve identified discrepancies while maintaining the approved visual direction and project constraints.

### Key Corrections Addressed:
1. **Property Segment Options Corrected:**
   - Replaced previously used options (`Budget`, `Midscale`, `Upscale`) with the approved set:
     - `Standard`
     - `Comfort`
     - `Premium`
     - `Luxury`
     - `Ultra Luxury`
   - Updated frontend dropdown, backend validation (`SUPPORTED_SEGMENTS`), and DTO mappings. Added safe alias normalization (`BUDGET` $\to$ `STANDARD`, `MIDSCALE` $\to$ `COMFORT`, `UPSCALE` $\to$ `PREMIUM`) to ensure existing drafts and test fixtures continue to load without errors.
2. **Recognized Property Classification Corrected:**
   - Aligned UI dropdown options and labels with the approved list:
     - `Not officially classified` (`NOT_CLASSIFIED`)
     - `1 Star` (`1_STAR`)
     - `2 Star` (`2_STAR`)
     - `3 Star` (`3_STAR`)
     - `4 Star` (`4_STAR`)
     - `5 Star` (`5_STAR`)
     - `5 Star Deluxe` (`5_STAR_DELUXE`)
     - `Other recognized classification` (`OTHER`)
   - Removed `HERITAGE` as an independent option. Heritage and other classifications are now entered through `Other recognized classification` and the conditional custom classification text field.
   - Guaranteed that `propertyClassification` remains the authoritative field and is never replaced or corrupted by the legacy `starRating` helper.
3. **Image Format Compatibility (.jpg, .jpeg, .png, .webp):**
   - Verified that both `.jpg` and `.jpeg` are recognized identically alongside `.png` and `.webp`.
   - Guaranteed complete alignment between:
     - Frontend `accept` and extension checks (`.jpg`, `.jpeg`, `.png`, `.webp`).
     - Frontend MIME checks (`image/jpeg`, `image/jpg`, `image/pjpeg`, `image/png`, `image/webp`).
     - Backend MIME checks (`image/jpeg`, `image/jpg`, `image/pjpeg`, `image/png`, `image/webp` with extension fallback).
     - Backend magic-byte verification (JPEG `FF D8 FF`, PNG `89 50 4E 47`, WebP `RIFF...WEBP`).
   - The strict 1.0 MB size limit and 4-image maximum remain fully enforced.
4. **Image Security & Lifecycle Enforcement:**
   - **Manager Isolation:** Generated file names are namespaced with the authenticated manager ID (`hotel_m{managerId}_...`).
   - **Ownership Validation:** `DELETE /api/hotels/images/{filename}` verifies that the caller owns the associated hotel property or draft, or uploaded the unassociated image. Unauthorized attempts return `403 FORBIDDEN`.
   - **Disk Cleanup on Draft Update:** When updating an existing draft, previously associated image files that were replaced or removed are automatically deleted from local storage.
   - **Draft Deletion:** Added `DELETE /api/hotels/draft/{draftId}` to cleanly remove abandoned drafts, their database records, and their image files from disk.
5. **Backward Compatibility:**
   - Verified that existing properties remain readable and editable.
   - Verified that `HotelProperty.imageUrl` receives the mandatory cover image URL for legacy compatibility.
   - Verified that `starRating` reflects integer values (1 to 5) for star classifications and `null` for `NOT_CLASSIFIED` and `OTHER`.

---

## 2. Detailed Technical Corrections

### 2.1 Backend Changes (`hotel-service`)

#### 1. Segment Validation & Normalization
- File: [`HotelPropertyService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java)
- Defined `SUPPORTED_SEGMENTS = Set.of("STANDARD", "COMFORT", "PREMIUM", "LUXURY", "ULTRA_LUXURY", "ULTRA LUXURY")`.
- In `validateStep1Request`: validates that the segment matches one of the approved options. Safely normalizes `BUDGET` $\to$ `STANDARD`, `MIDSCALE` $\to$ `COMFORT`, and `UPSCALE` $\to$ `PREMIUM`.

#### 2. Classification Validation & Authoritative Persistence
- File: [`HotelPropertyService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java)
- Defined `SUPPORTED_CLASSIFICATIONS` without `HERITAGE`.
- In `validateStep1Request`: direct submission of `HERITAGE` returns `400 BAD_REQUEST`, requiring selection of `OTHER` (`Other recognized classification`) with a non-empty `customClassification`.
- In `saveStep1Draft`: sets `propertyClassification` to the canonical string (`5_STAR_DELUXE`, `NOT_CLASSIFIED`, `OTHER`, or `1_STAR`..`5_STAR`). Derives `starRating` as a secondary helper (`5` for `5_STAR_DELUXE` / `5_STAR`, `null` for `NOT_CLASSIFIED` / `OTHER`).

#### 3. Image Magic Bytes & Compatibility
- File: [`HotelImageStorageService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/HotelImageStorageService.java)
- Extended `ALLOWED_CONTENT_TYPES` and `ALLOWED_EXTENSIONS` to support `image/jpeg`, `image/jpg`, `image/pjpeg`, `image/png`, `image/webp`, `.jpg`, and `.jpeg`.
- Added `determineExtension` ensuring both `.jpg` and `.jpeg` are recognized.
- Added `extractFilenameFromUrl` helper.
- Added `cleanupOrphanedFiles` routine for abandoned images older than 24 hours.

#### 4. Image Ownership Security & Draft Deletion
- File: [`HotelController.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/controller/HotelController.java)
- In `POST /api/hotels/images/upload`: tags the authenticated manager ID into the generated filename prefix (`hotel_m{managerId}_...`).
- In `DELETE /api/hotels/images/{filename}`: checks ownership against `hotel_images` and against filename manager prefix. Returns `403 FORBIDDEN` if another manager attempts deletion.
- Added `DELETE /api/hotels/draft/{draftId}`: deletes draft from `hotel_properties`, removes `hotel_images` records, and removes all associated files from disk.

#### 5. Image Lifecycle Cleanup on Draft Update
- In `saveStep1Draft`: compares existing image records against updated request URLs. Any previously saved image file that is no longer in the updated request is deleted from disk.

---

### 2.2 Frontend Changes (`frontend`)

#### 1. Property Segment Dropdown
- File: [`hotel-manager-dashboard.component.html`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html)
- Options updated to:
  ```html
  <option value="Standard">Standard</option>
  <option value="Comfort">Comfort</option>
  <option value="Premium">Premium</option>
  <option value="Luxury">Luxury</option>
  <option value="Ultra Luxury">Ultra Luxury</option>
  ```

#### 2. Recognized Property Classification Dropdown
- File: [`hotel-manager-dashboard.component.html`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html)
- Options updated to:
  ```html
  <option value="NOT_CLASSIFIED">Not officially classified</option>
  <option value="1_STAR">1 Star</option>
  <option value="2_STAR">2 Star</option>
  <option value="3_STAR">3 Star</option>
  <option value="4_STAR">4 Star</option>
  <option value="5_STAR">5 Star</option>
  <option value="5_STAR_DELUXE">5 Star Deluxe</option>
  <option value="OTHER">Other recognized classification</option>
  ```
- Removed `Heritage` option. Updated custom classification placeholder to `"Specify other recognized classification (e.g. Heritage Hotel, Eco Resort)"`.

#### 3. Image Selection Validation & Normalization
- File: [`hotel-manager-dashboard.component.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts)
- In `onImageFileSelected`: validates MIME type and file extensions including `.jpg` and `.jpeg`.
- In `startPropertyRegistration`: normalizes incoming draft segment and classification values to match the dropdown items.
- File: [`hotel.service.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/services/hotel.service.ts)
- Added `deleteDraft(draftId: number)`.

#### 4. Short Description Two-Way Binding & Validation Fix
- **Issue:** Text typed into the short description textarea was not propagating to `step1Data.description` due to one-way binding (`[ngModel]`) and a conditional branch in `onDescriptionInput` that only set the property when length exceeded 300 characters. Consequently, the live counter showed `0/300`, and clicking "Next" flagged an erroneous validation error ("Short description is required").
- **Fix in `hotel-manager-dashboard.component.html`:** Switched to standard two-way data binding `[(ngModel)]="step1Data.description"`.
- **Fix in `hotel-manager-dashboard.component.ts`:** Updated `onDescriptionInput(event)` to unconditionally update `this.step1Data.description = val.length > 300 ? val.substring(0, 300) : val;` and clear any previous field error message. Live character counter `{{ step1Data.description?.length || 0 }}/300` and `isDescriptionValid()` now react synchronously to all user input.

#### 5. Image Cover Property Deserialization Fix ("found: 0" error)
- **Issue:** When submitting Step 1 with a designated cover image, the backend threw `DomainValidationException("Exactly one image must be designated as the Cover Image (found: 0)")`.
- **Root Cause:** Due to JavaBeans naming conventions and Lombok code generation, `boolean isCover` generated getter `isCover()` and setter `setCover(boolean)`, which Jackson inferred as property `"cover"` rather than `"isCover"`. When the Angular frontend transmitted JSON with `"isCover": true`, Jackson failed to bind the property and left `isCover` defaulted to `false`.
- **Backend Fix in `HotelImageDto.java`:**
  - Added `@JsonProperty("isCover")` and `@JsonAlias({"cover", "is_cover"})` on field `isCover`.
  - Added explicit getter `isCover()` with `@JsonProperty("isCover")` and explicit setter `setCover(boolean)` with `@JsonProperty("isCover")` and `@JsonAlias({"cover", "is_cover"})`.
  - Suppressed conflicting Lombok accessor generation via `@Getter(AccessLevel.NONE)` and `@Setter(AccessLevel.NONE)`.
- **Frontend Fix in `hotel-manager-dashboard.component.ts`:**
  - In `proceedToStep2()`: explicitly maps images with both `isCover: !!img!.isCover` and `cover: !!img!.isCover`.
  - In `startPropertyRegistration()`: checks both `img.isCover` and `img.cover` when loading drafts.

---

## 3. Targeted Verification Results

### 3.1 Backend Test Execution (`HotelStep1RegistrationTest.java`)
Executed targeted command:
`mvn -f hotel-service/pom.xml test -Dtest=HotelStep1RegistrationTest`

```
[INFO] Running com.traveleasy.hotel.HotelStep1RegistrationTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.01 s
[INFO] BUILD SUCCESS
```

| # | Test Method Name | Verification Objective | Result |
| :- | :--- | :--- | :---: |
| 1 | `testSaveStep1Draft_Success` | Basic draft creation with `Standard`, `Comfort`, `Premium`, `Luxury`, `Ultra Luxury`. | **PASS** |
| 2 | `testSaveStep1Draft_Unauthorized` | Unauthorized requests without valid JWT return 401. | **PASS** |
| 3 | `testValidatePropertyName_Mandatory` | Missing or blank property name returns 400. | **PASS** |
| 4 | `testValidatePropertyType_Mandatory` | Missing property type returns 400. | **PASS** |
| 5 | `testClassification_NotOfficiallyClassified` | `NOT_CLASSIFIED` sets `starRating = null` and retains classification. | **PASS** |
| 6 | `testClassification_HeritageNotDirectOption` | Direct `HERITAGE` returns 400; `OTHER` with custom "Heritage Hotel" succeeds. | **PASS** |
| 7 | `testClassification_5StarDeluxe` | `5_STAR_DELUXE` sets `starRating = 5` and retains authoritative classification. | **PASS** |
| 8 | `testClassification_Other_RequiresCustom` | `OTHER` without custom classification returns 400. | **PASS** |
| 9 | `testPropertySegment_Validation` | Invalid segment rejected; all 5 approved segments (`Standard`, `Comfort`, `Premium`, `Luxury`, `Ultra Luxury`) succeed. | **PASS** |
| 10 | `testDescription_LengthConstraint` | $> 300$ characters description returns 400. | **PASS** |
| 11 | `testImageGalleryConstraints` | $> 4$ images or 0 cover images return 400. | **PASS** |
| 12 | `testImageStorage_Validation` | Files $> 1\text{ MB}$ or invalid magic bytes are rejected. | **PASS** |
| 13 | `testImageFormats_AllSupportedAccepted` | Confirms `.jpg`, `.jpeg`, `.png`, and `.webp` with valid magic bytes are accepted. | **PASS** |
| 14 | `testImageSecurity_ManagerIsolation` | Manager 2 attempting to delete Manager 1's draft image returns `403 FORBIDDEN`. | **PASS** |
| 15 | `testReplacedImageCleanup_OnDraftUpdate` | Replaced image is automatically deleted from disk when updating draft. | **PASS** |
| 16 | `testDeleteDraft_CleansUpDiskAndDatabase` | `DELETE /draft/{draftId}` removes draft record and deletes disk files. | **PASS** |
| 17 | `testDraftIsolation_BetweenManagers` | Manager 2 cannot read or modify Manager 1's draft. | **PASS** |
| 18 | `testUpdateExistingDraft_Success` | Updating draft preserves ID, updates fields, and maintains `DRAFT` status. | **PASS** |
| 19 | `testJsonDeserialization_IsCoverProperty` | Validates raw JSON deserialization for both `"isCover": true` and `"cover": true` aliases. | **PASS** |

### 3.2 Frontend Production Build
Executed command:
`cmd /c "cd frontend && npm run build"`

```
Initial chunk files   | Names         | Raw size | Estimated transfer size
main-BNPGYJE3.js      | main          |  1.60 MB |               282.02 kB
polyfills-FFHMD2TL.js | polyfills     | 33.71 kB |                11.02 kB
styles-CU5B4YZD.css   | styles        |  1.16 kB |               472 bytes
                      | Initial total |  1.63 MB |               293.49 kB

Application bundle generation complete. [10.919 seconds]
```
- **Exit Code:** 0 (Clean build, zero compilation errors).

---

## 4. Modified Files Summary

| Component | File Path |
| :--- | :--- |
| **Backend Service** | [`HotelPropertyService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java) |
| **Backend Storage** | [`HotelImageStorageService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/HotelImageStorageService.java) |
| **Backend Controller** | [`HotelController.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/controller/HotelController.java) |
| **Backend Repository** | [`HotelImageRepository.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/repository/HotelImageRepository.java) |
| **Backend Tests** | [`HotelStep1RegistrationTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java) |
| **Frontend HTML** | [`hotel-manager-dashboard.component.html`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html) |
| **Frontend Component** | [`hotel-manager-dashboard.component.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts) |
| **Frontend Service** | [`hotel.service.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/services/hotel.service.ts) |
| **Living Tracker** | [`HOTEL_MANAGER_PORTAL_IMPLEMENTATION_TRACKER.md`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/HOTEL_MANAGER_PORTAL_IMPLEMENTATION_TRACKER.md) |

---

## 5. Scope & Remaining Limitations

- **Step 1 Boundary Upheld:** Steps 2 through 5 remain as planned future work packages and are represented via the 5-step stepper bar and verified Step 2 placeholder.
- **Draft Isolation:** Properties created during Step 1 are persisted strictly with `status = 'DRAFT'` and remain invisible to public hotel search queries until final submission at Step 5.
