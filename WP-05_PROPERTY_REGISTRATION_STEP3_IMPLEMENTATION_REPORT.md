# TravelEasy — WP-05: Hotel Property Registration Wizard
## Implementation Report: Step 3 — Property Information

**Document ID:** TE-WP05-STEP3-REPORT-01  
**Author:** Antigravity AI Pair Programmer  
**Date:** 2026-11-07  
**Status:** **COMPLETED & VERIFIED (128/128 Backend Tests Passing, Angular Build 0 Errors)**  
**Visual Reference:** `media_1794031767598_f92c3ff7.jpg` ([View Screenshot](file:///C:/Users/reddy/.gemini/antigravity/brain/eea23d8b-ad0e-4f6f-93fd-d2d485cfbd8d/.user_uploaded/media_1794031767598_f92c3ff7.jpg))  

---

## 1. Executive Summary

This report documents the design, implementation, and automated verification of **Step 3 — Property Information** of the Hotel Manager Property Registration Wizard for TravelEasy. 

Step 3 captures the business, public contact, tax, designated operational manager, and operating hours information for newly registered properties. The implementation delivers high-fidelity visual parity with the approved UI mockup (`media_1794031767598_f92c3ff7.jpg`), provides 5 horizontal collapsible subsections with accordion mechanics, conditional field behaviors, strict server-side and client-side validation, full draft persistence across wizard navigation steps, and manager security isolation.

### Key Deliverables Completed:
1. **Database Schema Evolution:** Expanded MySQL `hotel_properties` table with 22 dedicated columns spanning all 5 subsections.
2. **Interactive 5-Subsection Accordion:** One-section-at-a-time expansion behavior, Property Contact Details open by default, smooth toggle transitions, active teal accent styling, and state retention across collapse/expand.
3. **Structured Domain Fields & Validation:**
   - **Subsection 1: Property Contact Details:** Public international contact phone, optional alternate phone, property email, and optional website URL.
   - **Subsection 2: Business Registration Details (Mandatory):** Registered business name, business registration type dropdown, conditional custom type input when `Other` is selected, registration number, and optional registered address.
   - **Subsection 3: GST Details (Optional):** Dynamic `Yes`/`No` registration toggle; conditional 15-character GSTIN (`^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$`), legal entity name, and Indian state selection when `Yes`; automatic clearing of stale data when `No`.
   - **Subsection 4: Property Manager Details:** Designated property operational contact (name, designation, phone, email) cleanly isolated from authenticated personal account credentials.
   - **Subsection 5: Operating Hours:** Check-in time, check-out time, reception availability (`24-hour Reception` vs. `Limited Operating Hours`), and conditional opening/closing times.
4. **Intelligent Form Validation & Navigation:** Clicking `Next: Facilities & Policies →` validates all 5 subsections and automatically expands the first invalid subsection with targeted feedback; draft updates in-place without duplicating rows, advances `registrationStep` to 3, and transitions to the Step 4 placeholder.
5. **Security & Manager Isolation:** Strict ownership validation ensures managers can only update their own drafts (`403 Forbidden` on cross-manager attempts; `401 Unauthorized` for unauthenticated requests).
6. **Automated Verification:** Created [`HotelStep3RegistrationTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/test/java/com/traveleasy/hotel/HotelStep3RegistrationTest.java) with 16 automated tests. All 128 `hotel-service` tests and Angular production build passed with zero errors.

---

## 2. Architecture & Database Persistence

### 2.1 Database Schema Migration

To store all property information as first-class structured attributes rather than unstructured JSON or text blocks, 22 columns were migrated to `hotel_properties` in `traveleasy_hotel_db`:

```sql
ALTER TABLE hotel_properties 
  -- 1. Contact
  ADD COLUMN property_contact_number VARCHAR(30) NULL,
  ADD COLUMN alternate_contact_number VARCHAR(30) NULL,
  ADD COLUMN property_email VARCHAR(100) NULL,
  ADD COLUMN website_url VARCHAR(255) NULL,
  -- 2. Business Registration
  ADD COLUMN registered_business_name VARCHAR(255) NULL,
  ADD COLUMN business_registration_type VARCHAR(100) NULL,
  ADD COLUMN other_business_registration_type VARCHAR(100) NULL,
  ADD COLUMN business_registration_number VARCHAR(100) NULL,
  ADD COLUMN registered_business_address TEXT NULL,
  -- 3. GST Details
  ADD COLUMN is_gst_registered BOOLEAN DEFAULT FALSE,
  ADD COLUMN gstin VARCHAR(15) NULL,
  ADD COLUMN gst_legal_name VARCHAR(255) NULL,
  ADD COLUMN gst_state VARCHAR(100) NULL,
  -- 4. Property Manager Details
  ADD COLUMN property_manager_name VARCHAR(255) NULL,
  ADD COLUMN property_manager_designation VARCHAR(100) NULL,
  ADD COLUMN property_manager_contact_number VARCHAR(30) NULL,
  ADD COLUMN property_manager_email VARCHAR(100) NULL,
  -- 5. Operating Hours
  ADD COLUMN check_in_time VARCHAR(20) NULL,
  ADD COLUMN check_out_time VARCHAR(20) NULL,
  ADD COLUMN reception_availability VARCHAR(50) NULL,
  ADD COLUMN reception_opening_time VARCHAR(20) NULL,
  ADD COLUMN reception_closing_time VARCHAR(20) NULL;
```

### 2.2 Entity Mapping

In [`HotelProperty.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/model/HotelProperty.java), the JPA entity was enhanced with typed attributes, proper column lengths, and defaults:
- `isGstRegistered` defaults to `false`.
- `registrationStep` increments to `3` upon saving Step 3.
- `status` remains strictly `"DRAFT"` per BD-03.

---

## 3. Backend Implementation & APIs

### 3.1 DTOs

- [`HotelStep3RequestDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep3RequestDto.java): Accepts all 5 subsection groups with Jakarta constraints (`@NotBlank`, `@Size`, `@Email`).
- [`HotelStep3ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep3ResponseDto.java): Returns complete property state across Step 1, Step 2, and Step 3 with `registrationStep = 3`.
- [`HotelStep1ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep1ResponseDto.java) & [`HotelStep2ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep2ResponseDto.java): Updated so draft restoration endpoints (`GET /api/hotels/draft/{draftId}`) return full Step 3 data when resuming saved drafts.

### 3.2 Service Layer Logic & Validation Rules

In [`HotelPropertyService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java):
1. **Draft Reuse & Status Guard:** Verifies draft exists and has `status = "DRAFT"`. Active properties cannot be altered via wizard endpoints.
2. **Manager Ownership Enforcement:** Compares `property.getManagerId()` with caller's authenticated principal ID, rejecting unauthorized attempts with `OwnershipAccessDeniedException` (HTTP 403).
3. **Contact Validation:**
   - Phone numbers (mandatory primary, optional alternate) validated against `^[+]?[0-9\s\-()]{7,20}$`.
   - Email validated against standard regex `^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`.
   - Website URL validated to ensure `http://` or `https://` prefix if supplied.
4. **Business Registration Validation:**
   - Mandatory business name and registration type.
   - If `businessRegistrationType == "OTHER"`, verifies `otherBusinessRegistrationType` is provided.
   - Mandatory registration number (alphanumeric).
5. **Conditional GST Validation:**
   - When `isGstRegistered == true`: validates 15-character GSTIN regex `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$`, legal entity name, and validates that `gstState` is a recognized Indian state from `AddressReferenceData.INDIAN_STATES_AND_UTS`.
   - When `isGstRegistered == false`: nulls out `gstin`, `gstLegalName`, and `gstState` to eliminate stale data.
6. **Operating Hours Validation:**
   - Mandatory check-in and check-out times.
   - When `receptionAvailability == "LIMITED_HOURS"`: enforces mandatory `receptionOpeningTime` and `receptionClosingTime`.
   - When `24-HOUR RECEPTION`: clears reception opening and closing times.

### 3.3 Endpoints Implemented

In [`HotelController.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/controller/HotelController.java):
- `POST /api/hotels/draft/{draftId}/step-3`: Primary path-variable endpoint.
- `POST /api/hotels/draft/step-3`: Payload-driven endpoint supporting draft ID in request body.

---

## 4. Frontend Implementation

### 4.1 UI Layout & Design

Constructed in [`hotel-manager-dashboard.component.html`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html) and styled in [`hotel-manager-dashboard.component.css`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.css):

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│  ← Back to My Properties                                                               │
│  Register New Property                                                                 │
│  Add a new hotel property to your portfolio. Complete the following steps to...        │
├────────────────────────────────────────────────────────────────────────────────────────┤
│    [✓] Basic Details ──── [✓] Location & Address ──── (3) Property Information ────... │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  ┌───┐  Step 3 of 5: Property Information                                              │
│  │ 📄│  Provide the business, contact and operational details of your property.        │
│  └───┘                                                                                 │
│                                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │ 📞 Property Contact Details | Public contact information for your property.   ▲ │  │
│  ├──────────────────────────────────────────────────────────────────────────────────┤  │
│  │ Property Contact Number *                Alternate Contact Number (Optional)     │  │
│  │ [📞  e.g., +91 98765 43210            ]  [📞  e.g., +91 91234 56789            ] │  │
│  │ Property Email Address *                 Website URL (Optional)                  │  │
│  │ [✉  e.g., bookings@sunrisegrand.com   ]  [🔗  e.g., https://www.sunrisegrand.com ] │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │ 💼 Business Registration Details | Legal registration details of your business. ▼ │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │ 🧾 GST Details | Provide GST information if your property is GST registered.   ▼ │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │ 👤 Property Manager Details | Designated operational contact for this property. ▼ │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │ 🕒 Operating Hours | Guest check-in, check-out and reception availability.     ▼ │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                        │
│                                    [← Previous: Location & Address] [Cancel] [Next: →] │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Accordion & Wizard Behaviors

- **One-at-a-Time Accordion Expansion:** Clicking any subsection header toggles it open and automatically closes any previously expanded subsection.
- **Initial Expanded State:** Subsection 1 (`Property Contact Details`) is open by default on first load; Subsections 2–5 are collapsed.
- **Auto-Expansion on Validation Failure:** If the user clicks `Next: Facilities & Policies →` while required fields in a collapsed section are missing or invalid, the wizard identifies the first invalid section, auto-expands it, highlights the invalid fields, and displays an error banner.
- **Bidirectional Data Preservation:**
  - `← Previous: Location & Address`: Navigates to Step 2 while retaining all Step 3 form values in memory.
  - `Next: Facilities & Policies →`: Validates, saves draft via API, and advances to Step 4 placeholder.
  - `Cancel`: Prompts confirmation modal if any step is dirty; redirects cleanly to My Properties.
- **Draft Resumption:** Reopening an existing draft from My Properties restores all Step 1, Step 2, and Step 3 fields.

---

## 5. Automated Verification & Test Results

### 5.1 Dedicated Step 3 Test Suite (`HotelStep3RegistrationTest.java`)

A suite of 16 automated integration tests was executed against a live Spring Boot test server:

| Test Method | Description | Result |
|---|---|---|
| `testSaveStep3Draft_Success_WithGst` | Full payload with GST enabled and 24-hr reception | **PASS** |
| `testSaveStep3Draft_Success_WithoutGst_LimitedHours` | Payload with GST disabled and limited reception hours | **PASS** |
| `testValidateContact_MissingContactNumber` | Missing mandatory property contact number | **PASS** (400) |
| `testValidateContact_InvalidContactNumber` | Malformed property contact number | **PASS** (400) |
| `testValidateContact_InvalidEmail` | Malformed property email address | **PASS** (400) |
| `testValidateContact_InvalidWebsiteUrl` | Website URL missing protocol (`http://` / `https://`) | **PASS** (400) |
| `testValidateBusiness_OtherRequiresCustomType` | Business registration type "Other" without custom specification | **PASS** (400) |
| `testValidateBusiness_OtherWithValidCustomType` | Business registration type "Other" with valid custom type | **PASS** (200) |
| `testValidateGst_MissingGstin` | GST enabled without mandatory GSTIN | **PASS** (400) |
| `testValidateGst_InvalidGstin` | GSTIN not matching 15-character standard pattern | **PASS** (400) |
| `testValidateGst_InvalidGstState` | Non-Indian state specified for GST | **PASS** (400) |
| `testValidateManager_MissingDesignation` | Missing mandatory property manager designation | **PASS** (400) |
| `testValidateOperatingHours_LimitedHoursMissingTimes` | Limited reception hours missing opening/closing times | **PASS** (400) |
| `testStep3_ReusesExistingDraftRecord` | Single-row draft persistence: reuses draft ID, preserves Steps 1 & 2 | **PASS** |
| `testSecurity_CrossManagerAccessForbidden` | Manager B attempts to update Manager A's draft | **PASS** (403) |
| `testSecurity_UnauthenticatedRequest` | Unauthenticated request without JWT token | **PASS** (401) |

### 5.2 Full Test Suite Results

- **`HotelStep3RegistrationTest`:** 16 / 16 PASSED (100% pass rate).
- **Full `hotel-service` Maven Test Suite:** **128 / 128 PASSED (0 Failures, 0 Errors)**:
  - `HotelDomainAndBusinessLogicTest`: 20 / 20 PASSED
  - `HotelInventoryPricingAndBlockingTest`: 26 / 26 PASSED
  - `HotelSecurityAndOwnershipTest`: 31 / 31 PASSED
  - `HotelStep1RegistrationTest`: 19 / 19 PASSED
  - `HotelStep2RegistrationTest`: 16 / 16 PASSED
  - `HotelStep3RegistrationTest`: 16 / 16 PASSED
- **Angular Production Build:** `ng build` completed cleanly in 18.4 seconds with **0 compilation errors** (1.65 MB production bundle).

---

## 6. Manifest of Changes

| Layer / File | Path | Summary of Changes |
|---|---|---|
| **Database** | `traveleasy_hotel_db.hotel_properties` | Migrated 22 columns across contact, business, GST, manager, and operating hours |
| **Backend Model** | [`HotelProperty.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/model/HotelProperty.java) | Added JPA entity fields and builder defaults for Step 3 |
| **Backend DTO** | [`HotelStep3RequestDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep3RequestDto.java) | Request DTO with Jakarta validation annotations |
| **Backend DTO** | [`HotelStep3ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep3ResponseDto.java) | Response DTO mapping all 3 steps |
| **Backend DTO** | [`HotelStep1ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep1ResponseDto.java) | Updated to include Step 3 fields for draft restoration |
| **Backend DTO** | [`HotelStep2ResponseDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelStep2ResponseDto.java) | Updated to include Step 3 fields for draft restoration |
| **Backend Service** | [`HotelPropertyService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java) | Implemented `saveStep3Draft()`, `validateStep3Request()`, constants, and normalization |
| **Backend Service** | [`AddressReferenceData.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/AddressReferenceData.java) | Exposed `INDIAN_STATES_AND_UTS` for GST state validation |
| **Backend Controller** | [`HotelController.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/controller/HotelController.java) | Added `POST /draft/{draftId}/step-3` and `POST /draft/step-3` endpoints |
| **Backend Tests** | [`HotelStep3RegistrationTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/test/java/com/traveleasy/hotel/HotelStep3RegistrationTest.java) | 16 dedicated integration tests |
| **Frontend Model** | [`hotel.model.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/models/hotel.model.ts) | Added `HotelStep3Request` and `HotelStep3Response` interfaces |
| **Frontend Service** | [`hotel.service.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/services/hotel.service.ts) | Added `saveStep3Draft(draftId, step3Data)` method |
| **Frontend Component** | [`hotel-manager-dashboard.component.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts) | Step 3 state, accordion mechanics, field validations, auto-expansion, draft resumption |
| **Frontend Template** | [`hotel-manager-dashboard.component.html`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html) | High-fidelity form card with 5 subsections and Step 4 placeholder |
| **Frontend Styles** | [`hotel-manager-dashboard.component.css`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.css) | Custom styling for accordion headers, active teal tint, and transitions |

---

## 7. Scope Boundaries & Next Steps

This concludes **Step 3 (Property Information)** of WP-05. Per explicit scope instructions, Steps 4 and 5 were not implemented; when the manager advances from Step 3, the wizard navigates to a Step 4 placeholder.

Upon user instruction, the project is ready to proceed to:
- **WP-05 Step 4 — Facilities & Policies:** Amenities checklist (Wi-Fi, parking, pool, gym, etc.), property rules, and check-in / stay policies.
