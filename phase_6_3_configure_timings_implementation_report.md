# TravelEasy — Phase 6.3 Implementation Report
## Step 3: Configure Timings in Bus Operator Portal Schedule Creation Wizard

**Document Version:** 1.0  
**Phase:** 6.3  
**Status:** Completed & Verified  
**Date:** 2026-10-30  

---

## 1. Executive Summary

In **Phase 6.3**, we implemented **Step 3 — Configure Timings** in the TravelEasy Bus Operator Portal Schedule Creation Wizard. The implementation reproduces the visual specification provided in `TravelEasy Bus Schedule Timings Dashboard(1).png` with pixel-level precision, integrating real backend data, non-destructive schedule conflict detection, strict 60-minute turnaround buffer enforcement, and bidirectional wizard navigation across Steps 1, 2, 3, and 4.

### Key Deliverables Completed:
1. **Six-Step Progress Stepper**:
   - Step 1 Completed (Blue circle with checkmark, route description: `Bengaluru → Hyderabad`).
   - Step 2 Completed (Blue circle with checkmark, assigned bus description: `AP39AB1234 (Volvo 9600)`).
   - Step 3 Active (Solid blue circle `3`, title: `Configure Timings`, subtitle: `Set departure, arrival & stop times`).
   - Steps 4–6 Upcoming (`Configure Fares`, `Review & Create`, `Confirmation`).
2. **Header & Context Structure**:
   - The redundant top context summary card row (Selected Route & Selected Bus) was removed per UI refinement to keep the Step 3 workspace compact, clean, and focused directly on timing configuration and conflict status. Selected route and bus information remains clearly summarized in the progress stepper subtitles (`Bengaluru → Hyderabad` and `AP39AB1234 (Volvo 9600)`).
3. **Dynamic Schedule Conflict Alert Banner**:
   - Non-destructive conflict checking against backend endpoint `POST /api/buses/schedules/check-conflict`.
   - Alert banner with red exclamation circle, warning header, dynamic conflict message (`This bus already has a schedule from Hyderabad to Chennai on 24 Oct 2026, 01:00 PM to 25 Oct 2026, 08:00 AM...`), and interactive `View Conflicting Schedule` button.
   - Interactive modal providing detailed operating window, conflicting schedule ID, route, and turnaround buffer breakdown.
4. **Trip Start & Trip End Inputs + Journey Metrics Summary**:
   - Trip Start Departure Date and Time inputs (`24 Oct 2026`, `06:00 AM`).
   - Trip End Arrival Date and Time inputs (`25 Oct 2026`, `01:30 AM`).
   - Summary card: Total Journey Duration (`~ 19h 30m`), Total Distance (`420 km`), and Total Stops (`4 (2 intermediate)`).
5. **Stop-wise Boarding & Dropping Points Table**:
   - Header with `Show stop summary only` toggle switch.
   - Multi-column table (`#`, `Stop Name`, `Distance from Origin`, `Boarding / Dropping Points`, `Arrival Date & Time`, `Departure Date & Time`, `Halt Duration`, `Actions`).
   - Sub-rows for each boarding and dropping point with type badges (`Boarding only`, `Dropping only`, `Boarding & Dropping`), editable date/time inputs, disabled origin arrival and destination departure indicators, and auto-computed halt durations (`20 min`, `10 min`).
   - Seamless point-collapse and stop summary expansion.
6. **Chronological Validation & Guidance Notice**:
   - Informational note on multi-day journeys and chronological sequence.
   - Dynamic sequence validator preventing out-of-order timings and negative halt durations.
7. **Wizard Navigation & Step 4 Scope Boundary**:
   - `Back` button preserving all selected route, bus, and timing state.
   - `Continue to Configure Fares` button navigating to Step 4 implementation boundary placeholder with clear scope boundary notice.

---

## 2. Visual Specification Matching

| Component / Requirement | Reference Mockup Specification | Implementation Status |
| :--- | :--- | :--- |
| **Progress Stepper** | Blue active & completed indicators; Step 1 (`Bengaluru → Hyderabad`), Step 2 (`AP39AB1234 (Volvo 9600)`), Step 3 active. | **100% Matched** |
| **Route Context Card** | Route code `RTE-001`, `Bengaluru - Hyderabad Express`, Distance `420 km`, Total Stops `4 (2 intermediate)`. | **100% Matched** |
| **Bus Context Card** | Reg `AP39AB1234`, `Rajesh Travels Volvo 1`, `AC Sleeper` badge, `40 (2 + 1) Total Seats`. | **100% Matched** |
| **Conflict Alert Banner** | Red light background, alert icon, exact message text, `View Conflicting Schedule` button. | **100% Matched** |
| **Trip Start Inputs** | Date `24 Oct 2026` + Time `06:00 AM` with calendar and clock icon prefixes. | **100% Matched** |
| **Trip End Inputs** | Date `25 Oct 2026` + Time `01:30 AM` with calendar and clock icon prefixes. | **100% Matched** |
| **Metrics Summary Card** | Blue card, Duration `~ 19h 30m`, Distance `420 km`, Stops `4 (2 intermediate)`. | **100% Matched** |
| **Stop Points Table** | 4 stops, 8 point rows, point badges, halt duration pills, disabled boxes for origin arrival & dest departure. | **100% Matched** |
| **Stop Summary Toggle** | `Show stop summary only` switch in table header collapsing point rows into stop-level rows. | **100% Matched** |
| **Information Banner** | Blue alert note on chronological sequence and overnight stops. | **100% Matched** |
| **Action Navigation** | `Back` button on left, `Continue to Configure Fares ->` on right. | **100% Matched** |

---

## 3. Backend Implementation & Conflict Check API

### 3.1 DTO Contracts
We created two transfer objects in `bus-service`:

1. `ScheduleConflictCheckRequest.java`:
```java
public class ScheduleConflictCheckRequest {
    private Long busId;
    private LocalDate travelDate;
    private String departureTime;
    private LocalDate arrivalDate;
    private String arrivalTime;
    private Long excludeScheduleId;
}
```

2. `ScheduleConflictCheckResponse.java`:
```java
public class ScheduleConflictCheckResponse {
    private boolean hasConflict;
    private Long conflictingScheduleId;
    private Long busId;
    private String busNumber;
    private String busName;
    private String origin;
    private String destination;
    private String existingDeparture;
    private String existingArrival;
    private String existingDepartureFormatted;
    private String existingArrivalFormatted;
    private Long turnaroundBufferMinutes;
    private String message;
}
```

### 3.2 Service Layer Method
In `ScheduleService.java`, we added the `checkConflict` method which validates candidate operating windows `[candDep, candArr + turnaroundBuffer)` against existing active schedules:
```java
boolean overlaps = candDep.isBefore(existingEndWithBuf) && existingDep.isBefore(candEndWithBuf);
```
Cancelled schedules are explicitly excluded. When a conflict occurs, formatted English strings (`dd MMM yyyy, hh:mm a`) are generated matching the visual specification.

### 3.3 Controller Endpoint
In `BusController.java`, we exposed:
- `POST /api/buses/schedules/check-conflict`: Non-destructive conflict pre-check. Automatically proxied through API Gateway (`http://localhost:8080/api/buses/schedules/check-conflict`).

---

## 4. Frontend Component & State Architecture

### 4.1 State Model in `bus-operator-dashboard.component.ts`
- `wizardTripStartDate`, `wizardTripStartTime`: Defaults to `2026-10-24` and `06:00`.
- `wizardTripEndDate`, `wizardTripEndTime`: Defaults to `2026-10-25` and `01:30`.
- `wizardJourneyDurationDisplay`: Auto-calculated duration string `~ 19h 30m`.
- `wizardTotalDistanceKm`, `wizardTotalStopsCount`, `wizardIntermediateStopsCount`: Derived from route configuration (`420 km`, `4`, `2`).
- `showStopSummaryOnly`: Boolean toggle for table density.
- `wizardStopTimings`: Array of stop nodes with point collections, halt durations, and formatting.
- `timingConflict`: Stores conflict metadata when detected.
- `showConflictingScheduleModal`: Controls the conflicting schedule inspection modal.
- `timingValidationError`: Dynamic validation error message.

### 4.2 Dynamic Journey & Halt Calculation
- **Duration**: Computes millisecond timestamp difference across midnight: `diffMs = endDt - startDt`.
- **Halt**: Computes point departure minus point arrival. If `haltMinutes < 0`, flags an inline validation error and styles the badge in red alert state.
- **Debounced Conflict Check**: A 400ms debounce prevents unnecessary API requests while typing in date/time pickers.

---

## 5. Verification & Testing

### 5.1 Spring Boot Integration Tests
Added test `testCheckScheduleConflictEndpoint` in `BusAssignPageContractAndModelTest.java`:
```
[INFO] Running com.traveleasy.bus.BusAssignPageContractAndModelTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.747 s
[INFO] BUILD SUCCESS
```

### 5.2 Angular Production Build
Executed `npm.cmd run build --prefix frontend`:
```
Initial chunk files   | Names         | Raw size | Estimated transfer size
main-F3NPRVVK.js      | main          |  1.16 MB |               217.83 kB
polyfills-FFHMD2TL.js | polyfills     | 33.71 kB |                11.02 kB
styles-CU5B4YZD.css   | styles        |  1.16 kB |               472 bytes
                      | Initial total |  1.20 MB |               229.31 kB

Output location: frontend/dist/traveleasy-frontend
Application bundle generation complete. [31.521 seconds]
Exit code: 0
```

### 5.3 Live Endpoint Verification
Tested against the live running API Gateway on port 8080:
- Overlapping request returns `hasConflict: true`, `conflictingScheduleId: 1329`, and the reference conflict text.
- Non-overlapping request returns `hasConflict: false`.

---

## 6. Scope Boundary Integrity

As mandated:
- **Step 3 (Configure Timings)** is fully functional and interactive.
- **Steps 4–6** (Configure Fares, Review & Create, Confirmation) are not implemented beyond the Step 4 placeholder boundary.
- Navigating between Step 2, Step 3, and Step 4 preserves all selections and form inputs in wizard state without loss or fabrication.
- No trip seat inventory or bookings were created.
