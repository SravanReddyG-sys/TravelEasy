tracker_path = "HOTEL_MANAGER_PORTAL_IMPLEMENTATION_TRACKER.md"
with open(tracker_path, "r", encoding="utf-8") as f:
    text = f.read()

# 1. Update Last Updated
text = text.replace("**Last Updated:** 2026-11-05", "**Last Updated:** 2026-11-07")

# 2. Add entry to matrix in Section 1
row_wp05 = "| **WP-05** | **Hotel Manager Dashboard Overview UI** | Manager console UX matching approved visual mockup, 4 KPI cards, trend chart, occupancy donut, quick actions, alerts, reservations; Date-Range Integration (7 presets + custom range), Previous Equivalent Period comparisons, WP-03 date-wise inventory occupancy aggregation (`HotelInventorySummaryDto`), Net Revenue tracking with refunds, corrected operational alert rules | **COMPLETED & VERIFIED** | **PASS (77/77 Hotel Tests Passed, Angular Build Clean 0 Errors)** | [`WP-05_DASHBOARD_OVERVIEW_IMPLEMENTATION_REPORT.md`](file:///C:/Users/reddy/.gemini/antigravity/brain/eea23d8b-ad0e-4f6f-93fd-d2d485cfbd8d/WP-05_DASHBOARD_OVERVIEW_IMPLEMENTATION_REPORT.md) |"

row_wp05_reg = """| **WP-05-REG** | **Hotel Property Registration Wizard (Step 1 — Basic Property Details)** | Approved 5-step registration wizard UI (`TravelEasy Property Registration Form(1).png`), Step 1 basic property details, input validations, conditional custom classification, short description live counter (<=300 chars), up to 4 images with mandatory cover image tile, magic bytes and <=1 MB size enforcement, MySQL `hotel_images` and `hotel_properties` extension, draft persistence (`DRAFT` status per BD-03, multi-property per BD-02, backward-compatible `starRating` & `imageUrl`), Step 2 placeholder & navigation | **COMPLETED & VERIFIED** | **PASS (90/90 Hotel Tests Passed, 13/13 Dedicated Step 1 Tests, Angular Build Clean 0 Errors)** | [`WP-05_PROPERTY_REGISTRATION_STEP1_IMPLEMENTATION_REPORT.md`](file:///C:/Users/reddy/.gemini/antigravity/brain/eea23d8b-ad0e-4f6f-93fd-d2d485cfbd8d/WP-05_PROPERTY_REGISTRATION_STEP1_IMPLEMENTATION_REPORT.md) |"""

if row_wp05_reg not in text:
    text = text.replace(row_wp05, row_wp05 + "\n" + row_wp05_reg)

# 3. Add detailed section at the end of Section 6
section_7 = """

---

## 7. WP-05-REG: Hotel Property Registration Wizard — Step 1: Basic Property Details

- **Work Package ID:** WP-05-REG (Step 1)  
- **Title:** Hotel Property Registration Wizard — Step 1: Basic Property Details Implementation  
- **Completion Date:** 2026-11-07  
- **Status:** **COMPLETED & VERIFIED**  
- **Approved Business Decisions Addressed:**
  - `BD-02`: Multiple Hotel Properties per Manager (supports registering multiple properties and maintaining independent property drafts).
  - `BD-03`: Hotel Property Lifecycle & Activation Rules (drafts created at Step 1 strictly remain in `DRAFT` status and are never `ACTIVE` until final Step 5 submission; drafts are excluded from public search results).
  - Backward Compatibility: Derives legacy `starRating` from recognized classification (`5_STAR_DELUXE` -> 5, `NOT_CLASSIFIED` -> null); derives legacy `imageUrl` from mandatory cover image.

### Summary of Implementation:
1. **Approved UI Reproduction (`TravelEasy Property Registration Form(1).png`):**
   - 5-step progress stepper bar (Basic Details, Location & Address, Property Information, Facilities & Policies, Review & Submit).
   - Form card with section title `Step 1 of 5: Basic Property Details`.
   - Property Name and Property Type inputs with prepended icon boxes.
   - Recognized Property Classification with info tooltip and conditional Custom Classification when `OTHER` is selected.
   - Property Segment dropdown (`Budget`, `Midscale`, `Upscale`, `Luxury`, `Ultra Luxury`).
   - Brand Name (Optional) input.
   - Short Description textarea with live `X / 300` character counter.
   - Property Images section with `X / 4 Images` pill badge and 4 upload tiles (Tile 1: Cover image with dashed teal border; Tiles 2-4: Additional gallery images with dashed grey borders).
   - Real-time client-side preview via `FileReader`, async file upload, replace/remove buttons, and cover re-assignment.
   - Navigation: `[ Cancel ]` button with dirty state confirmation modal; `[ Next: Location & Address → ]` button with loading state that persists draft and advances to Step 2 placeholder.
2. **Backend Persistence & Image Storage (`hotel-service`):**
   - MySQL columns added to `hotel_properties`: `property_type`, `property_classification`, `custom_classification`, `property_segment`, `brand_name`, `registration_step`.
   - Table `hotel_images` created with foreign key to `hotel_properties`.
   - Dedicated [`HotelImageStorageService`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/main/java/com/traveleasy/hotel/service/HotelImageStorageService.java) with strict $\le 1\text{ MB}$ limit, MIME type check, and magic byte validation (JPEG `FF D8 FF`, PNG `89 50 4E 47`, WebP `RIFF...WEBP`).
   - Endpoints: `POST /api/hotels/draft/step-1`, `GET /api/hotels/draft/{draftId}`, `GET /api/hotels/drafts`, `POST /api/hotels/images/upload`, `GET /api/hotels/images/{filename}`, `DELETE /api/hotels/images/{filename}`.
3. **Verification Results:**
   - Dedicated test suite [`HotelStep1RegistrationTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java): **13/13 tests passed (100%)**.
   - Full regression suite in `hotel-service`: **90/90 tests passed (100% pass rate, 0 failures, 0 errors)**.
   - Angular build: **Clean bundle generation (0 errors)**.
- **Report Reference:** [`WP-05_PROPERTY_REGISTRATION_STEP1_IMPLEMENTATION_REPORT.md`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/WP-05_PROPERTY_REGISTRATION_STEP1_IMPLEMENTATION_REPORT.md)
"""

if "## 7. WP-05-REG" not in text:
    # insert before ## 8 or append at the end
    text = text + section_7

with open(tracker_path, "w", encoding="utf-8") as f:
    f.write(text)
print("Updated HOTEL_MANAGER_PORTAL_IMPLEMENTATION_TRACKER.md successfully!")
