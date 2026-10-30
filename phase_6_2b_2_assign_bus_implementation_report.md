# TravelEasy — Phase 6.2B.2: Step 2 — Assign Bus Implementation Report

**Author / Role:** Antigravity (Google DeepMind Advanced Agentic Coding)  
**Date:** October 29, 2026  
**Phase:** 6.2B.2 — Implement Assign Bus Page in Schedule Creation Wizard  
**Primary Visual Specification:** `TravelEasy Bus Assignment Dashboard(3).png`  
**Status:** Complete & Verified  

---

## 1. Implementation Summary

In this phase, we implemented **Step 2 — Assign Bus** of the Schedule Creation Wizard in the TravelEasy Bus Operator Portal. This page enables bus operators to assign an active bus from their registered fleet to a planned schedule for the route selected in Step 1.

Key achievements:
1. **Faithful Visual Replication**: Implemented the full UI matching `TravelEasy Bus Assignment Dashboard(3).png`, including the progress stepper header with completed Step 1 route summary (`Bengaluru → Hyderabad`), active Step 2 badge, horizontal fleet table, single-selection radio button, bus thumbnails, registration numbers, bus names, bus type pills (`AC Sleeper`, `AC Seater`, `Non-AC Seater`), total seats count with layout summary (`40 (2 + 1)`), status pills (`Active` / `Inactive`), and collapsible `View Details` action.
2. **Inline Bus Information Expansion**: Implemented an inline expandable card displaying a 3-column layout:
   - **Left**: Bus thumbnail photo.
   - **Middle**: Registration No., Bus Name, Bus Type, Total Seats with seat layout summary.
   - **Right**: Manufacturer, Model, Year, Fuel Type, and Amenities with visual icons (❄️ AC, 📶 Wi-Fi, 🔌 Charging, 🛏️ Blanket).
3. **Decoupled Selection and Expansion**: Expanding a bus row to inspect its specifications does not select it; selecting a radio button does not force the row to expand. Row expansion and selection have independent state management with event propagation safeguards (`event.stopPropagation()`).
4. **Backend Fleet Model Non-Breaking Extension**: Extended `Bus.java` with 6 nullable fields (`busName`, `manufacturer`, `model`, `yearOfManufacturing`, `fuelType`, `imageUrl`), automatically updated via JPA `ddl-auto=update` without any data loss or disruption to existing records.
5. **Real Mockup Seed Fleet**: Populated 5 real buses for Operator 2 (`AP39AB1234`, `AP39CD5678`, `AP39EF9012`, `AP39GH3456`, `AP39IJ7890`) in MySQL matching the visual specification and amenities.
6. **Frontend Normalization & Search/Filter**: Extended `bus.model.ts` and `bus.service.ts` to normalize backend DTOs into standard UI shapes with case-insensitive search and bus-type dropdown filtering.
7. **Strict Phase Boundaries**: Preserved Step 1 route context across all navigation; enabled "Back" navigation to Step 1; provided "Continue to Configure Timings" button navigating to the Step 3 boundary placeholder without implementing Step 3–6 business logic.
8. **Comprehensive Automated Verification**: 4 new targeted contract/integration tests in `BusAssignPageContractAndModelTest` passed (4/4). Full regression suite of 44 backend tests passed (44/44). Angular production build (`ng build`) succeeded with exit code 0.

---

## 2. Files Created and Modified

### Backend (`bus-service`)
- [`Bus.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/model/Bus.java): Extended with 6 backward-compatible nullable attributes (`busName`, `manufacturer`, `model`, `yearOfManufacturing`, `fuelType`, `imageUrl`).
- [`BusAssignPageContractAndModelTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/test/java/com/traveleasy/bus/BusAssignPageContractAndModelTest.java): Created 4 comprehensive tests validating extended attribute persistence, operator API response shape, inactive bus schedule creation prevention, and physical seat inventory generation.

### Frontend (`frontend`)
- [`bus.model.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/models/bus.model.ts): Extended `Bus` interface with `totalSeats`, `capacity`, `busName`, `manufacturer`, `model`, `yearOfManufacturing`, `modelYear`, `fuelType`, `imageUrl`, `layoutSummary`, and flexible `amenities` typing (`string | string[]`).
- [`bus.service.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/services/bus.service.ts): Added `normalizeBus(b)` mapper to resolve manufacturer, model, model year, fuel type, layout summary, and amenities array.
- [`bus-operator-dashboard.component.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/bus-operator-dashboard/bus-operator-dashboard.component.ts):
  - Updated wizard stepper header (Step 1 completed with route summary `Bengaluru → Hyderabad`, Step 2 active).
  - Added Step 2 Assign Bus panel with search bar, bus-type filter, fleet table, radio selection, inline expandable Bus Information card, empty/loading/error states, and sticky selection banner.
  - Added Step 3 boundary placeholder with preserved route and assigned bus context and back navigation.
  - Added wizard state variables and methods (`wizardSelectedBusId`, `wizardSelectedBus`, `expandedBusId`, `wizardBusSearchQuery`, `wizardBusTypeFilter`, `selectWizardBus`, `toggleBusDetails`, `filteredWizardBuses`, `availableBusTypes`, `continueToConfigureTimings`, `backToSelectRoute`, `backToAssignBus`, `getBusAmenitiesList`, `getBusThumbnail`, `getBusTotalSeatsFormatted`).
  - Added complete responsive styling matching `TravelEasy Bus Assignment Dashboard(3).png`.

---

## 3. APIs and DTOs Reused

1. **`GET /api/buses/operator/{operatorId}`**:
   - Reused the existing operator fleet retrieval endpoint in `BusController.java`.
   - Returns a `List<Bus>` JSON array.
   - Now includes the extended nullable fields (`busName`, `manufacturer`, `model`, `yearOfManufacturing`, `fuelType`, `imageUrl`) serialized transparently by Jackson.
2. **`GET /api/routes/operator/{operatorId}`**:
   - Reused from Step 1 to load and verify the operator's routes and preserve the selected route across wizard navigation.
3. **`GET /api/schedules/operator/{operatorId}`**:
   - Reused in Schedules shell list for existing schedule overview.
4. **`POST /api/schedules` / `ScheduleService.createSchedule`**:
   - Reused for validating that inactive buses cannot be scheduled (`BUS-ASSIGN-03`) and that assigning an active bus generates physical trip seat inventory (`BUS-ASSIGN-04`).

---

## 4. Frontend Model and Mapping Corrections

The frontend `Bus` interface and `BusService` were updated to resolve discrepancies identified during the readiness audit:

1. **Seat Capacity Attribute Standardization**:
   - Backend historically used `totalSeats` on `Bus` entity and sometimes `capacity` in older DTOs.
   - Added both `totalSeats?: number` and `capacity?: number` to `Bus` interface.
   - Normalizer sets `totalSeats = b.totalSeats ?? b.capacity ?? 40`.
2. **Seat Layout Summary Mapping**:
   - The UI table requires displaying layout summaries such as `2 + 1`, `2 + 2`, `3 + 2`.
   - Normalizer extracts this from `templateId` (e.g. `2_PLUS_1_SLEEPER` → `2 + 1`, `2_PLUS_2_SEATER` → `2 + 2`, `3_PLUS_2_SEATER` → `3 + 2`) or falls back to template metadata.
3. **Amenities Representation**:
   - Stored in MySQL as a comma-separated string (e.g. `"AC, Wi-Fi, Charging, Blanket"`).
   - Normalizer maps this into an array `amenities: ['AC', 'Wi-Fi', 'Charging', 'Blanket']` for easy iteration with icon matching in the template.
4. **Model Year Normalization**:
   - Normalizer maps `b.yearOfManufacturing ?? b.modelYear` to ensure template compatibility regardless of naming conventions.
5. **Bus Thumbnail Fallback**:
   - Normalizer maps `imageUrl` or falls back to standard asset `assets/images/travel-easy-bus.png`.

---

## 5. Backend/Schema Changes and Justification

### Entity Update: `Bus.java`
Added 6 nullable columns:
```java
@Column(name = "bus_name")
private String busName;

@Column(name = "manufacturer")
private String manufacturer;

@Column(name = "model")
private String model;

@Column(name = "year_of_manufacturing")
private Integer yearOfManufacturing;

@Column(name = "fuel_type")
private String fuelType;

@Column(name = "image_url")
private String imageUrl;
```

### Justification
1. **Visual Truth Requirement**: The approved mockup `TravelEasy Bus Assignment Dashboard(3).png` explicitly displays bus photo, bus name, manufacturer, model, manufacturing year, and fuel type.
2. **Clean Persistence**: Persisting these directly on `Bus` avoids hardcoded mock data, brittle UI heuristics, or simulated client-side attributes.
3. **Zero Breaking Changes**: All fields are nullable (`nullable = true` default) and cleanly mapped to MySQL columns via JPA `hibernate.ddl-auto=update`. Existing buses without these fields continue to function without any regressions.

---

## 6. Retrieval and Display of Bus Specifications

| Field | Source in Database / API | Display Location | UI Value Example |
| :--- | :--- | :--- | :--- |
| **Bus Photo / Thumbnail** | `imageUrl` (or fallback) | Table thumbnail column & expanded card left panel | Bus image preview |
| **Registration Number** | `busNumber` | Table column 2 & expanded card middle panel | `AP39AB1234` |
| **Bus Name** | `busName` | Table column 3 & expanded card middle panel | `Rajesh Travels Volvo 1` |
| **Bus Type** | `busType` | Table column 4 (pill) & expanded card middle panel | `AC Sleeper` |
| **Total Seats & Layout** | `totalSeats` + `templateId` | Table column 5 & 6, expanded card middle panel | `40 (2 + 1)` |
| **Manufacturer** | `manufacturer` | Expanded card right panel | `Volvo` |
| **Model** | `model` | Expanded card right panel | `9600` |
| **Year** | `yearOfManufacturing` | Expanded card right panel | `2022` |
| **Fuel Type** | `fuelType` | Expanded card right panel | `Diesel` |
| **Amenities** | `amenities` (comma-separated string parsed to array) | Expanded card right panel (chips with icons) | ❄️ AC, 📶 Wi-Fi, 🔌 Charging, 🛏️ Blanket |

---

## 7. Selection, Filtering, Expansion, and Navigation Behavior

1. **Selection (`selectWizardBus`)**:
   - Radio button in each row. Only eligible (Active) buses can be selected.
   - Clicking an active row or radio button updates `wizardSelectedBusId` and `wizardSelectedBus`.
   - Selecting a row **does not** automatically expand its details.
   - Clicking an inactive row displays an alert explaining that inactive buses cannot be assigned to schedules.
2. **Expansion (`toggleBusDetails`)**:
   - Triggered by clicking the `View Details` button or caret toggle (`event.stopPropagation()`).
   - Toggles `expandedBusId = (expandedBusId === b.id ? null : b.id)`.
   - Expanding details **does not** select the bus.
3. **Search Filtering (`wizardBusSearchQuery`)**:
   - Real-time client-side filter searching across registration number, bus name, bus type, and manufacturer.
   - Case-insensitive matching.
4. **Bus Type Filtering (`wizardBusTypeFilter`)**:
   - Dropdown dynamically populated with unique available bus types (`All Bus Types`, `AC Sleeper`, `AC Seater`, `Non-AC Seater`).
5. **Preserved Selection Banner**:
   - If an operator selects a bus and then searches for a different query that filters out the selected bus, a persistent banner appears at the top:
     *"Selected bus: AP39AB1234 - Rajesh Travels Volvo 1 (hidden by current search/filter). [View Selected / Clear Filters]"*.
6. **Navigation**:
   - **Back**: Returns to Step 1 (`scheduleWizardStep = 1`) while keeping the selected route intact.
   - **Continue to Configure Timings**: Enabled only when `canContinueToStep3` is true (`wizardSelectedBusId != null` and `isBusEligibleForSchedule(wizardSelectedBus)`). Advances to `scheduleWizardStep = 3` (boundary placeholder).
   - **Step 3 Boundary**: Shows summary banner with preserved route (`Bengaluru → Hyderabad`) and assigned bus (`AP39AB1234 - Rajesh Travels Volvo 1`), with "Back to Step 2: Assign Bus" button.

---

## 8. Loading, Empty, Error, and Eligibility Handling

1. **Loading State**: Displays an animated spinner and text: *"Loading available fleet..."* while the API request is in-flight.
2. **Error State**: If the backend is unreachable or returns an error, displays an error message with a *"Retry Loading Buses"* button. Zero fake fallback data is displayed.
3. **Empty State**:
   - If the operator has zero registered buses: *"No buses found in your fleet. Please register a bus under Fleet / Buses first."*
   - If filters return zero matches: *"No buses match your search criteria. [Clear Filters]"*.
4. **Eligibility Handling**:
   - Inactive buses display an `Inactive` gray pill, disabled radio button, and muted styling.
   - Clicking an inactive bus displays a clear error alert: *"Cannot assign an inactive bus. Please activate this bus in Fleet / Buses first."*

---

## 9. Test Commands and Actual Results

### Test Suite: `BusAssignPageContractAndModelTest`
Command:
```powershell
cd bus-service; mvn test "-Dtest=BusAssignPageContractAndModelTest"
```
**Results:**
```text
[INFO] Running com.traveleasy.bus.BusAssignPageContractAndModelTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 15.77 s -- in com.traveleasy.bus.BusAssignPageContractAndModelTest
[INFO] Results:
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Detailed Test Assertions
1. `testExtendedBusAttributesPersistence`: Confirms that `Bus` entity with `busName`, `manufacturer`, `model`, `yearOfManufacturing`, `fuelType`, `amenities`, and `imageUrl` is successfully saved and read back from MySQL.
2. `testOperatorEndpointReturnsExtendedAttributes`: Authenticates via JWT as operator 2002 and performs `GET /api/buses/operator/2002`, verifying that Jackson serializes all extended fields.
3. `testInactiveBusCannotBeScheduled`: Verifies that `ScheduleService.createSchedule` throws `ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot schedule trips on an inactive bus")` when an inactive bus is submitted.
4. `testActiveBusScheduleGeneratesTripInventory`: Verifies that scheduling an active 36-seater bus automatically generates 36 physical `bus_seats` with upper and lower deck allocations according to the assigned seat template.

---

## 10. Angular Production Build Result

Command:
```powershell
cd frontend; npm.cmd run build
```
**Results:**
```text
> traveleasy-frontend@1.0.0 build
> ng build

- Building...

Initial chunk files   | Names         | Raw size | Estimated transfer size
main-5BG6Z5HY.js      | main          |  1.12 MB |               211.25 kB
polyfills-FFHMD2TL.js | polyfills     | 33.71 kB |                11.02 kB
styles-CU5B4YZD.css   | styles        |  1.16 kB |               472 bytes

                      | Initial total |  1.15 MB |               222.72 kB

Output location: C:\Users\reddy\Documents\Materials_and_Assignments\FALL SEM(26-27)\SAA\SAA_Project\frontend\dist\traveleasy-frontend

Application bundle generation complete. [7.759 seconds]
Command exited with code 0.
```

---

## 11. Visual Verification Status

The implementation was checked against `TravelEasy Bus Assignment Dashboard(3).png`:

| Visual Component | Approved Mockup Requirement | Implementation Status |
| :--- | :--- | :--- |
| **Progress Stepper** | Step 1 completed with checkmark & `Bengaluru → Hyderabad`; Step 2 active "Assign Bus"; Steps 3–6 upcoming | **Verified** |
| **Page Title & Subtitle** | "Create New Schedule" / "Assign a bus from your available fleet for the selected route" | **Verified** |
| **Section Header** | Blue bus icon, "Select Bus", "Choose a bus from your fleet to assign to this schedule" | **Verified** |
| **Search & Filter Bar** | Search box with search icon and bus-type dropdown filter (`All Bus Types`) | **Verified** |
| **Table Columns** | Radio, Thumbnail, Registration No., Bus Name, Bus Type, Total Seats, Seat Layout, Status, Actions | **Verified** |
| **Type Pills** | Light blue background for AC, light orange for Non-AC | **Verified** |
| **Status Pills** | Green dot + "Active" pill for active buses | **Verified** |
| **Expanded Details** | 3-column card: Photo on left; Core specs in middle; Tech specs + amenity chips on right | **Verified** |
| **Navigation Footer** | "Back" button on bottom left; "Continue to Configure Timings →" button on bottom right | **Verified** |

---

## 12. Regression Results

Full regression suite executed across bus management, ownership security, trip inventory, deactivation, seat layouts, and schedule creation:
```powershell
cd bus-service; mvn test "-Dtest=BusDeactivationTest,BusOwnershipSecurityTest,BusRegistrationAndTripInventoryTest,SeatLayoutCatalogueTest,BusAssignPageContractAndModelTest"
```
**Results:**
```text
[INFO] Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] Total time:  30.786 s
[INFO] ------------------------------------------------------------------------
```
All 44 tests passed with zero failures and zero regressions.

---

## 13. Deferred My Buses Improvements

The following improvements are deferred to the dedicated Fleet / Buses module (Phase 7):
1. **Full Bus Registration Wizard Updates**: Exposing input controls for `manufacturer`, `model`, `yearOfManufacturing`, `fuelType`, and `imageUrl` inside the `Fleet / Buses` new bus registration form.
2. **Bus Image Upload Service**: Adding a dedicated file upload multipart endpoint for custom operator bus photos rather than static URL paths.
3. **Detailed Bus Maintenance / Document Logs**: Exposing insurance, fitness certificate, and permit validity directly on the bus entity.

---

## 14. Known Limitations and Remaining Risks

1. **Schedule Conflict Checking Timing**:
   - As per phase scope rules, bus schedule conflict validation (verifying whether the assigned bus is already booked on overlapping dates/times) occurs during Step 3 (Configure Timings) and backend creation, not during Step 2. Step 2 validates fleet assignment eligibility (Active status and ownership).
2. **Static Asset Fallback for Bus Photos**:
   - If a bus record does not have a custom `imageUrl`, the UI displays `assets/images/travel-easy-bus.png`. This ensures clean visual presentation without broken image icons.
3. **Turnaround Time Validation**:
   - The 60-minute turnaround buffer between consecutive schedules for the same bus will be enforced when timings are provided in Step 3.

---

## 15. Conclusion & Next Phase Readiness

Phase 6.2B.2 is complete and verified. The Schedule Creation Wizard now seamlessly guides the operator through:
- **Step 1**: Selecting an active route with stops and distance details.
- **Step 2**: Selecting an active bus from the fleet with detailed specifications and amenities.
- **Boundary to Step 3**: Cleanly passing the preserved route and bus context into Step 3 (Configure Timings).
