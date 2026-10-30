# TravelEasy — Phase 6.4: Configure Fares Implementation Report

**Author**: Antigravity  
**Phase**: 6.4 — Configure Fares (Step 4 of Schedule Creation Wizard)  
**Date**: October 30, 2026  
**Reference Mockup**: `b4962e60-01c7-4561-81e3-6125bc5d5651.png` (`media_1793336123343_f821970c.jpg`)  
**Status**: Verified & Production-Ready

---

## 1. Executive Summary

Phase 6.4 implemented **Step 4 — Configure Fares** in the TravelEasy Bus Operator Portal schedule creation wizard. Following the approved UI reference, the implementation enables bus operators to:
1. Configure base consecutive stop-to-stop segment fares.
2. Review automatically calculated Origin-Destination (OD) journey fares for all valid $N(N-1)/2$ combinations.
3. Manually override individual journey fares independently while preserving overrides across segment fare adjustments.
4. Reset overridden journey fares back to dynamic auto-calculated values.
5. Safely preserve all wizard state (selected route, assigned bus, operational timings, configured stop/point timestamps, segment fares, journey fares, and override definitions) across bidirectional navigation between wizard steps.
6. Enforce rigorous monetary and chronological validation both on the client and authoritatively on the backend.

All changes were cleanly scoped to fare configuration and its integration points. The full test suites passed with **0 errors and 0 failures** across both `bus-service` (137 tests) and `booking-service` (48 tests). The Angular production build completed in **6.89 seconds with 0 errors**.

---

## 2. Initial Codebase Inspection Findings

| Module / Component | Inspected Artifact | Findings |
|---|---|---|
| **Fare Override Entity** | `ScheduleFareOverride.java` | Already existed with columns `schedule_id`, `from_stop_order`, `to_stop_order`, `custom_fare`. |
| **Elementary Segment Storage** | `ScheduleStop.java` | Column `consecutive_fare` persisted on intermediate and origin stops. |
| **Fare Matrix Engine** | `FareMatrixService.java` | Supported calculating $N(N-1)/2$ pairs and saving manual overrides for existing schedule IDs. Lacked an in-memory preview calculation method for uncommitted schedules. |
| **Schedule Creation Contract** | `BusSchedule.java` / `ScheduleService.java` | Did not expose a transient `fareOverrides` collection; manual overrides could previously only be applied after schedule creation. |
| **Frontend Step 4 Wizard** | `bus-operator-dashboard.component.ts` | Contained a static placeholder card at Step 4; had no navigation to Step 5 (Review & Create). |
| **Booking Search Integration** | `BusController.searchBuses` | Already leveraged `fareMatrixService.getEffectiveFare` to compute sub-route fares dynamically based on passenger origin and destination. |

---

## 3. Files Created and Modified

### Backend (`bus-service`)
1. **[`FareMatrixPreviewRequest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/dto/FareMatrixPreviewRequest.java)** *(Created)*:
   - DTO containing `List<ScheduleStopDto> stops` and `List<FareOverrideItemDto> overrides` for in-memory preview calculation.
2. **[`BusSchedule.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/model/BusSchedule.java)** *(Modified)*:
   - Added `@Transient private List<FareOverrideItemDto> fareOverrides;` to receive manual overrides atomically on creation.
3. **[`FareMatrixService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/service/FareMatrixService.java)** *(Modified)*:
   - Implemented `public ScheduleFareMatrixResponse previewFareMatrix(List<ScheduleStopDto> stops, List<FareOverrideItemDto> overrides)`.
4. **[`ScheduleService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/service/ScheduleService.java)** *(Modified)*:
   - Injected `FareMatrixService`.
   - In `createSchedule`, atomically persists `fareOverrides` via `fareMatrixService.saveFareOverrides`.
   - In `validateScheduleDateTimeAndFare`, validates that override stop orders are strictly increasing and custom fares are positive.
5. **[`BusController.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/controller/BusController.java)** *(Modified)*:
   - Exposed `POST /api/buses/schedules/fare-matrix/preview`.
6. **[`FareMatrixEngineTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/test/java/com/traveleasy/bus/FareMatrixEngineTest.java)** *(Modified)*:
   - Added 3 comprehensive tests (`testPreviewFareMatrix_DynamicPairsAndOverrides`, `testCreateSchedule_WithFareOverrides_PersistsAndSynchronizes`, `testCreateSchedule_WithInvalidFareOverrides_Rejection`).

### Frontend (`frontend`)
1. **[`bus.model.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/models/bus.model.ts)** *(Modified)*:
   - Exported `FareMatrixEntry`, `FareOverrideItem`, and `ScheduleFareMatrixResponse` interfaces.
2. **[`bus.service.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/services/bus.service.ts)** *(Modified)*:
   - Added `previewFareMatrix`, `getScheduleFareMatrix`, `updateScheduleFareOverrides`, `clearScheduleFareOverrides`, and `getSegmentFare`.
3. **[`bus-operator-dashboard.component.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/bus-operator-dashboard/bus-operator-dashboard.component.ts)** *(Modified)*:
   - Added interfaces `WizardSegmentFareItem` and `WizardJourneyFareItem`.
   - Added Step 4 state properties (`wizardSegmentFares`, `wizardJourneyFares`, `fareValidationError`, `wizardTotalFare`, `wizardFareOverrides`).
   - Implemented Step 4 UI: Configure Segment Fares Card, How it works? panel, Configure Journey Fares Card, Auto/Overridden status pills, Reset action, Important notes banner, and bottom navigation.
   - Implemented Step 5 placeholder boundary container.
   - Added calculation algorithms, override preservation, validation, and step transition methods.

---

## 4. Frontend Implementation Details

### UI Structure & Alignment
1. **Header & Progress Indicator**:
   - Subtitle dynamically set to: `Set fares for each travel segment and review calculated journey fares (modifiable)`.
   - Progress indicator displays Step 1 (`Bengaluru → Hyderabad`), Step 2 (`AP39AB1234 (Volvo 9600)`), Step 3 (`24 Oct 2026, 06:00 AM`), and Step 4 active (`Set segment fares`).
2. **Section 1 — Configure Segment Fares**:
   - Left: Compact table with columns `#`, `From Stop`, `To Stop`, `Distance (km)`, `Segment Fare (₹)`, `Actions` (`—`).
   - Right: Light-blue `#f0f7ff` "How it works?" panel detailing consecutive stop calculation principles.
3. **Section 2 — Configure Journey Fares**:
   - Full-width table with columns `#`, `From Stop (Boarding)`, `To Stop (Dropping)`, `Journey Distance (km)`, `Calculated Fare (₹)`, `Operator Fare (₹)`, `Status`, `Actions`.
   - Calculated fare uses standard currency formatting (e.g. `1,200`).
   - Green `Auto` badge and amber `Overridden` badge.
   - `Reset` button (with `fa-rotate-left` icon) appears only for overridden journeys.
4. **Section 3 — Important Notes Panel**:
   - Amber banner (`#fffbeb`) with warning icon and two-column layout highlighting override rules and stop-level applicability.
5. **Section 4 — Bottom Navigation**:
   - `Back`: Returns to Step 3, preserving all timings, stops, and entered fare data.
   - `Continue to Review →`: Validates all inputs, updates wizard state, and advances to Step 5.

---

## 5. Backend Implementation Details

1. **In-Memory Matrix Preview**:
   `FareMatrixService.previewFareMatrix` takes `List<ScheduleStopDto>` and optional overrides, computes elementary running sums for all $N(N-1)/2$ combinations, checks for active manual overrides, and returns the complete `ScheduleFareMatrixResponse`.
2. **Atomic Creation Synchronization**:
   When `ScheduleService.createSchedule` is executed:
   - Segment fares are stored in `schedule_stops.consecutive_fare`.
   - Overrides in `schedule.getFareOverrides()` are persisted into `schedule_fare_overrides`.
   - Parent `BusSchedule.fare` is synchronized to the effective full-trip fare.
3. **Pessimistic Concurrency & Bus Locking**:
   The bus entity is locked using `PESSIMISTIC_WRITE` during schedule creation, preventing concurrent conflicting schedules and ensuring atomic fare initialization.

---

## 6. Segment Fare Generation

- For an ordered sequence of $N$ stops, exactly $N - 1$ consecutive segments are created.
- Stop order and cumulative distances are obtained from `wizardStopTimings`.
- Segment distance is computed as:
  $$\Delta d_k = d_{k+1} - d_k$$
- Example for Bengaluru $\rightarrow$ Hyderabad route:
  1. Bengaluru (Origin) $\rightarrow$ Kurnool: $210 - 0 = 210\text{ km}$ (default ₹450)
  2. Kurnool $\rightarrow$ Anantapur: $320 - 210 = 110\text{ km}$ (default ₹350)
  3. Anantapur $\rightarrow$ Hyderabad (Destination): $420 - 320 = 100\text{ km}$ (default ₹400)
- Inputs require positive numeric values $> 0$ with field-level validation errors.

---

## 7. Journey Combination Generation

- For $N$ ordered stops, exactly $\frac{N(N - 1)}{2}$ unique forward combinations are generated:
  $$\{(i, j) \mid 0 \le i < j \le N - 1\}$$
- For 4 stops, 6 ordered combinations are generated dynamically:
  1. Bengaluru $\rightarrow$ Kurnool
  2. Bengaluru $\rightarrow$ Anantapur
  3. Bengaluru $\rightarrow$ Hyderabad
  4. Kurnool $\rightarrow$ Anantapur
  5. Kurnool $\rightarrow$ Hyderabad
  6. Anantapur $\rightarrow$ Hyderabad
- Same-stop journeys ($i = j$) and reverse journeys ($i > j$) are never generated.
- Journey distances are derived directly from route coordinates:
  $$D_{i, j} = d_j - d_i = \sum_{k=i}^{j-1} \Delta d_k$$

---

## 8. Fare Calculation Algorithm

The calculated fare is the sum of consecutive segment fares:
$$\text{CalculatedFare}(i, j) = \sum_{k=i}^{j-1} \text{SegmentFare}(k)$$

- **Single-segment journeys** ($j = i + 1$): Matches the corresponding segment fare.
- **Multi-segment journeys**: Evaluates running sums across intermediate stops.
- Whenever any segment fare changes:
  1. Every journey spanning that segment has its `calculatedFare` recomputed immediately.
  2. If the journey is in `Auto` status (`isOverridden == false`), its `operatorFare` updates to match `calculatedFare`.
  3. If the journey has an active manual override (`isOverridden == true`), its `operatorFare` is strictly preserved.

---

## 9. Manual Override and Reset Implementation

1. **Explicit Override State**:
   - When an operator changes `operatorFare`, `isOverridden` is set to `true`, and status changes to `Overridden`.
   - Per requirements, even if the entered value matches `calculatedFare`, `isOverridden` remains `true` (explicit override policy).
2. **Override Preservation**:
   - Modifying a segment fare updates `calculatedFare` for all spanning journeys, but never overwrites an operator's custom fare.
3. **Reset Behavior**:
   - Clicking `Reset`:
     - Sets `isOverridden = false`.
     - Sets `status = 'Auto'`.
     - Restores `operatorFare = calculatedFare`.
     - Re-engages automatic updates for future segment changes.

---

## 10. Multiple Boarding/Drop-Off Point Mapping

- As established in Phase 6.3, stops can contain multiple physical boarding and dropping points (e.g. Majestic Bus Stand & Yeshwanthpur for Bengaluru; Kurnool Bus Stand & Orvakal for Kurnool).
- Fares are strictly configured at the **route-stop level** using stable stop order indices ($0, 1, \dots, N-1$).
- All physical points belonging to a route stop share the same stop-to-stop fare mapping, preventing redundant combinatorial explosion.

---

## 11. API and DTO Changes

### New DTO
- [`FareMatrixPreviewRequest`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/dto/FareMatrixPreviewRequest.java):
  ```java
  public class FareMatrixPreviewRequest {
      private List<ScheduleStopDto> stops;
      private List<FareOverrideItemDto> overrides;
  }
  ```

### New Endpoint
- `POST /api/buses/schedules/fare-matrix/preview`:
  - Request: `FareMatrixPreviewRequest`
  - Response: `ScheduleFareMatrixResponse`

### Enhanced Entity
- [`BusSchedule.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/model/BusSchedule.java):
  - `@Transient private List<FareOverrideItemDto> fareOverrides;`

---

## 12. Database Changes and Migrations

- **Zero Schema Migrations Required**: The database already contains the required tables (`schedule_stops` with `consecutive_fare` and `schedule_fare_overrides` with composite unique constraint `(schedule_id, from_stop_order, to_stop_order)`).
- All changes were achieved using backward-compatible JPA annotations and transient DTO mapping.

---

## 13. Wizard-State Integration

The wizard state in `BusOperatorDashboardComponent` maintains complete synchronization:
- `wizardSelectedRoute` & `wizardSelectedRouteId` (Step 1)
- `wizardSelectedBus` & `wizardSelectedBusId` (Step 2)
- `wizardTripStartDate`, `wizardTripStartTime`, `wizardTripEndDate`, `wizardTripEndTime`, `wizardStopTimings` (Step 3)
- `wizardSegmentFares`, `wizardJourneyFares`, `wizardTotalFare`, `wizardFareOverrides` (Step 4)
- Back navigation from Step 4 to Step 3 restores timings and point configurations without losing fare values.
- Re-entering Step 4 restores existing segment fares and manual overrides.

---

## 14. Validation Implementation

1. **Client-Side Validation**:
   - `validateFareConfiguration()`:
     - Verifies every segment has a fare $> 0$.
     - Verifies every journey has an operator fare $> 0$.
     - Prevents submission and displays actionable error messages.
   - `isFareConfigurationValid()`:
     - Disables `Continue to Review →` if any fare is empty, zero, negative, or NaN.
2. **Backend Validation**:
   - `validateScheduleDateTimeAndFare(schedule)`:
     - Rejects any override where `fromStopOrder >= toStopOrder` (400 Bad Request).
     - Rejects non-positive overrides (`customFare <= 0`).
     - Derives full-trip fare automatically from segment fares if omitted.

---

## 15. Test Commands and Actual Test Results

### 1. `bus-service` Tests
```bash
mvn test -f bus-service/pom.xml -Dtest=FareMatrixEngineTest
```
**Result**:
- Tests run: **11**, Failures: **0**, Errors: **0**, Skipped: **0**
- Time elapsed: **7.97 s** — `BUILD SUCCESS`

```bash
mvn test -f bus-service/pom.xml
```
**Result**:
- Tests run: **137**, Failures: **0**, Errors: **0**, Skipped: **0**
- Time elapsed: **27.57 s** — `BUILD SUCCESS`

### 2. `booking-service` Regression Tests
```bash
mvn test -f booking-service/pom.xml
```
**Result**:
- Tests run: **48**, Failures: **0**, Errors: **0**, Skipped: **0**
- Time elapsed: **7.52 s** — `BUILD SUCCESS`

---

## 16. Angular Production-Build Result

```bash
node "frontend/node_modules/@angular/cli/bin/ng.js" build --configuration production
```
**Result**:
- `Application bundle generation complete. [6.885 seconds]`
- Initial total: **1.21 MB** (main: 1.17 MB, polyfills: 33.71 kB, styles: 1.16 kB)
- Exit code: **0** (Zero compilation or bundling errors)

---

## 17. Browser Verification Result

- Verified that layout matches reference mockup `b4962e60-01c7-4561-81e3-6125bc5d5651.png` and targeted screenshot refinement `media_1793339812590_9dae62f0.png`.
- Segment fares table aligns horizontally with the "How it works?" card.
- Table headers and cells match column proportions.
- Green `Auto` pills and amber `Overridden` pills match design tokens.
- Reset button behaves reactively and clears overrides.
- Important notes banner displays as a sleek horizontal callout card with `[ (i) Important notes ]` on the left and two side-by-side bullet columns with neutral slate text.

---

## 18. Regression Verification

- **Step 1 (Select Route)**: Intact and functional.
- **Step 2 (Assign Bus)**: Intact, filters/search and bus details expansion preserved.
- **Step 3 (Configure Timings)**: Intact, stop and point timings preserved; actions column removal maintained.
- **Booking Service & Seat Holds**: 15-minute seat-hold reconciliation and booking cancellation batching verified via green tests in `booking-service`.
- **Sub-Route Booking**: Verified that `searchBuses` and `getSegmentFare` correctly calculate fares using the OD matrix.

---

## 19. Remaining Issues and Limitations

- None identified for Step 4. All business rules and UI requirements are fulfilled.

---

## 20. Recommended Next Phase

- **Phase 6.5 — Step 5: Review & Create**:
  - Implement the full summary review page compiling Route, Bus, Timings, and Fare matrix details.
  - Submit the consolidated payload to `POST /api/buses/schedules` to persist the schedule, generate seat inventory, and transition to Step 6 (Confirmation).
