# TravelEasy — Phase 6.3.1: Backend and Timing Logic Verification Report

**Author**: Antigravity  
**Phase**: 6.3.1 — Backend and Timing Logic Verification  
**Service**: `bus-service` & Frontend Schedule Creation Wizard  
**Verification Clock Reference**: October 30, 2026, `Asia/Kolkata`

---

## Executive Summary

Phase 6.3.1 conducted a focused verification and targeted correction pass on the timing, validation, conflict detection, and persistence logic introduced in Phase 6.3 (Step 3 — Configure Timings).

The audit verified that while backend operational times have always referenced `Asia/Kolkata` with zero hardcoded calendar dates, date defaults of `2026-10-24` and `2026-10-25` were present as frontend state in `bus-operator-dashboard.component.ts`. Furthermore, individual boarding and dropping point timestamps configured in Step 3 previously lacked a persistence mechanism on `ScheduleStop`. Security authorization was also missing on the schedule pre-check endpoint.

All identified deficiencies have been corrected with backward-compatible enhancements. The full test suite of **134 backend tests in `bus-service` passed with 0 failures and 0 errors**. Per Phase 6.3.1 instructions, no Angular production builds, frontend unit tests, or browser automation were executed.

---

## 1. Findings from the Initial Inspection

| Area | Component / File | Initial State | Audit Assessment |
|---|---|---|---|
| **Default Dates** | `bus-operator-dashboard.component.ts` (Lines 87–88) | `wizardTripStartDate = '2026-10-24'`, `wizardTripEndDate = '2026-10-25'` | **Defect (Frontend)**: Hardcoded past dates (as of Oct 30, 2026) were used for initial wizard state. |
| **Backend Clock** | `ScheduleService.java` | Injected `Clock`, `LocalDateTime.now(this.clock)` in `Asia/Kolkata` | **Verified Correct**: Backend contained zero hardcoded calendar dates. |
| **Point Timing Persistence** | `ScheduleStop.java`, `ScheduleStopDto.java` | Stored only `stop_name`, `stop_type`, `arrival_time`, `departure_time`, `consecutive_fare` | **Defect / Missing Capability**: Step 3 allowed configuring individual boarding/dropping point times, but `ScheduleStop` lacked storage for point-level timing. |
| **Chronological Validation** | `ScheduleService.java` | Basic `departureTime.isBefore(arrivalTime)` only on top-level schedule | **Defect / Missing Capability**: Did not validate intermediate stop chronological ordering, halt durations ($\ge 0$), point timestamp bounds, or destination arrival equality. |
| **Schedule Conflict Pre-check** | `BusController.java` (`/schedules/check-conflict`) | Unauthenticated endpoint, no bus ownership verification | **Defect / Security Vulnerability**: Unauthenticated users could probe bus schedules; past dates and invalid arrival times were not rejected. |
| **Turnaround Buffer Rule** | `ScheduleService.java` (`checkConflict`, `checkBusAvailability`) | Applied $[dep, arr + 60m)$ interval comparison | **Verified Correct**: Exact 60-minute turnaround buffer mathematically symmetric between pre-check and creation lock. |
| **Top-Level Fare Validation** | `ScheduleService.validateScheduleDateTimeAndFare` | Rejected payloads missing `fare > 0` before stops could supply it | **Regression Risk**: Multi-stop schedules supplying fares via `stops` failed validation before `configureRouteStops` could compute the total fare. |

---

## 2. Default-Date Behavior and Corrections

### Source of Defaults
Inspection confirmed that the hardcoded dates `2026-10-24` and `2026-10-25` existed solely as initial property assignments in the Angular component:
```typescript
// bus-operator-dashboard.component.ts (prior to fix)
wizardTripStartDate: string = '2026-10-24';
wizardTripEndDate: string = '2026-10-25';
```
The backend `ScheduleService` had zero hardcoded calendar dates and already used an injectable `Clock` evaluating against `LocalDateTime.now(this.clock)` in `Asia/Kolkata`.

### Corrections Applied
1. **Dynamic Frontend Initialization**:
   Implemented `getOperationalDateKolkata(offsetDays: number)` using `Intl.DateTimeFormat` with `timeZone: 'Asia/Kolkata'`. The wizard now initializes with `tomorrow` for trip start and `tomorrow` (or day after tomorrow for cross-midnight) for trip end:
   ```typescript
   wizardTripStartDate: string = this.getOperationalDateKolkata(1);
   wizardTripEndDate: string = this.getOperationalDateKolkata(1);
   ```
2. **Preservation of Operator Input**:
   When an operator modifies dates/times and navigates between wizard steps (e.g., Step 3 $\rightarrow$ Step 2 $\rightarrow$ Step 3), the entered values are preserved in the wizard state and not reset to defaults.
3. **Authoritative Backend Validation**:
   `ScheduleService.validateStopAndPointTimings` and `ScheduleService.checkConflict` enforce that candidate departure cannot be in the past:
   ```java
   LocalDateTime now = LocalDateTime.now(this.clock);
   if (candDep.isBefore(now)) {
       throw new IllegalArgumentException("Departure time cannot be in the past");
   }
   ```
4. **Test Verification**:
   Added `testPastDepartureRejectionAndFutureAcceptance` in `ScheduleValidationAndConflictTest`, verifying rejection of past departures ($t - 1\text{ hr}$) and acceptance of future departures ($t + 2\text{ hr}$) against a fixed reference clock.

---

## 3. Stop and Point Timing Persistence Findings

### The Gap
Step 3 allows operators to configure distinct arrival and departure timestamps for intermediate stops, as well as individual boarding and dropping points (e.g., Majestic Bus Stand at 06:00, Yeshwanthpur at 06:30 for origin stop Bengaluru).
Previously, `ScheduleStop` only persisted stop-level `LocalTime` (`arrival_time`, `departure_time`) and lacked representations for:
- Stop-level `LocalDate` or full `LocalDateTime` (critical for cross-midnight journeys).
- Calculated halt durations (`haltMinutes`).
- Point-level timestamps (`arrivalDate`, `arrivalTime`, `departureDate`, `departureTime`, `haltMinutes`).

### Persistence Solution
To guarantee full persistence without breaking existing database schemas or historical booking queries:
1. **Created `SchedulePointDto.java`**:
   Represents individual point timings:
   - `name` (String)
   - `type` (String: `BOARDING`, `DROPPING`, `BOTH`)
   - `arrivalDate` / `arrivalTime` / `arrivalDateTime`
   - `departureDate` / `departureTime` / `departureDateTime`
   - `haltMinutes` (Integer)
2. **Enhanced `ScheduleStopDto.java`**:
   Added `arrivalDate`, `departureDate`, `arrivalDateTime`, `departureDateTime`, `haltMinutes`, and `List<SchedulePointDto> points`.
3. **Enhanced `ScheduleStop.java` Entity**:
   Added columns:
   - `arrival_date` (`LocalDate`)
   - `departure_date` (`LocalDate`)
   - `arrival_date_time` (`LocalDateTime`)
   - `departure_date_time` (`LocalDateTime`)
   - `halt_minutes` (`Integer`)
   - `points_json` (`TEXT`)
   Provided `@Transient` Jackson getters/setters (`getPoints()`, `setPoints()`) for transparent serialization to/from JSON.
4. **Updated `RouteStopsService.configureRouteStops`**:
   Maps incoming stop-level datetimes, halt durations, and points JSON directly onto `ScheduleStop` entities. Deserializes existing records safely (null `points_json` returns empty list).

---

## 4. Chronological Validation Findings

Implemented `ScheduleService.validateStopAndPointTimings(...)` to enforce complete journey chronology:

1. **Trip Departure vs. Present**: Trip departure must not be in the past (`candDep.isBefore(now)` $\rightarrow$ error).
2. **Trip Arrival vs. Departure**: Authoritative trip arrival must be strictly after trip departure (`!candArr.isAfter(candDep)` $\rightarrow$ error).
3. **Intermediate Stop Chronological Order**:
   - Intermediate stop arrival must follow the previous stop's departure (`stopArr.isBefore(prevDep)` $\rightarrow$ error).
   - Intermediate stop departure cannot precede its own arrival (`stopDep.isBefore(stopArr)` $\rightarrow$ error).
   - If an intermediate stop specifies `departureTime` earlier than `arrivalTime` without an explicit date, it defaults to same-day departure to catch negative halt durations immediately rather than silently pushing to the next day.
4. **Halt Duration**: Halt minutes must be non-negative ($\ge 0$).
5. **Destination Arrival Equality**: Destination stop arrival must match the trip's authoritative arrival datetime (`!destArrival.equals(tripArrival)` $\rightarrow$ error).
6. **Point-Level Validity**:
   - `DROPPING` points require arrival time; point departure cannot precede point arrival.
   - `BOARDING` points require departure time; point arrival cannot succeed point departure.
   - Point arrival/departure must fall within stop arrival and departure bounds (or trip origin/destination bounds).
7. **Cross-Midnight Safety**: All comparisons use combined `LocalDateTime`, correctly supporting multi-day and overnight journeys (e.g., 23:00 Day 1 to 02:00 Day 2).

---

## 5. Conflict-Check Findings (Turnaround Buffer & Security)

### Turnaround Buffer
The business rule dictates a **60-minute turnaround buffer** between consecutive schedules on the same bus:
$$\text{Existing Span} = [\text{existingDep}, \text{existingArr} + 60\text{ min})$$
$$\text{Candidate Span} = [\text{candDep}, \text{candArr} + 60\text{ min})$$
Two trips overlap if and only if:
$$\text{candDep} < (\text{existingArr} + 60\text{ min}) \quad \text{AND} \quad \text{existingDep} < (\text{candArr} + 60\text{ min})$$

- **Exact Boundary Verification**:
  - Existing schedule: 10:00 to 14:00 (turnaround buffer until 15:00).
  - Candidate starting at 14:59: Overlaps with buffer $\rightarrow$ **Conflict detected** (`conflict = true`).
  - Candidate starting at 15:00: Exactly at buffer boundary ($15:00 < 15:00$ is false) $\rightarrow$ **No conflict** (`conflict = false`).
- **Cancelled Schedule Exclusion**:
  Verified that schedules with status `CANCELLED` are ignored by conflict checks.
- **Pessimistic Locking**:
  `checkConflict` is an uncommitted pre-check endpoint. The authoritative creation in `ScheduleService.createSchedule` executes `checkBusAvailability` under `PESSIMISTIC_WRITE` locking on the `Bus` entity, eliminating race conditions.

### Security and Authorization
- **Vulnerability Found**: `POST /api/buses/schedules/check-conflict` previously lacked security checks. Any unauthenticated caller could query schedule conflicts for arbitrary bus IDs.
- **Correction Applied**: Updated `BusController.checkScheduleConflict` to:
  1. Validate JWT authentication token.
  2. Enforce operator or admin role (`ROLE_BUS_OPERATOR`, `ROLE_ADMIN`).
  3. Verify bus ownership against `busRepository.findByIdAndOperatorId`. An operator cannot probe buses belonging to another operator.

---

## 6. Files Modified and Summary of Changes

| File | Change Description |
|---|---|
| [`SchedulePointDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/dto/SchedulePointDto.java) | **New file**: DTO representing point-level timings (arrival, departure, halt, type). |
| [`ScheduleStopDto.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/dto/ScheduleStopDto.java) | Added stop-level date/time fields, `haltMinutes`, and `List<SchedulePointDto> points`. |
| [`ScheduleStop.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/model/ScheduleStop.java) | Added columns `arrival_date`, `departure_date`, `arrival_date_time`, `departure_date_time`, `halt_minutes`, `points_json` with Jackson serialization. |
| [`RouteStopsService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/service/RouteStopsService.java) | Enhanced `configureRouteStops` to persist complete datetimes, halt durations, and points JSON. |
| [`ScheduleService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/service/ScheduleService.java) | Added `validateStopAndPointTimings(...)`; added fare derivation from stops payload; enhanced `checkConflict` with bus ownership and past-departure validation. |
| [`BusController.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/main/java/com/traveleasy/bus/controller/BusController.java) | Added JWT token validation, role checking (`ROLE_BUS_OPERATOR`), and caller ID extraction to `check-conflict`. |
| [`BusAssignPageContractAndModelTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/test/java/com/traveleasy/bus/BusAssignPageContractAndModelTest.java) | Updated obsolete fixed test dates (`2026-10-26`) to dynamic future dates relative to clock. |
| [`ScheduleValidationAndConflictTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/bus-service/src/test/java/com/traveleasy/bus/ScheduleValidationAndConflictTest.java) | Added 6 focused verification tests covering past departures, persistence roundtrip, chronological violations, conflict authorization, input validation, and boundary conditions. |
| [`bus-operator-dashboard.component.ts`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/frontend/src/app/components/bus-operator-dashboard/bus-operator-dashboard.component.ts) | Corrected hardcoded `2026-10-24`/`2026-10-25` to dynamic future operational dates in `Asia/Kolkata`. |

---

## 7. Tests Executed and Actual Results

### Backend Test Execution
All backend tests were executed via Maven on `bus-service`:

```bash
# 1. Full bus-service test suite
mvn test -f bus-service/pom.xml
# Result: Tests run: 134, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS (26.1s)

# 2. Focused Schedule Validation & Conflict Suite
mvn test -f bus-service/pom.xml -Dtest=ScheduleValidationAndConflictTest
# Result: Tests run: 24, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS (10.3s)

# 3. Bus Assign Page Contract Suite
mvn test -f bus-service/pom.xml -Dtest=BusAssignPageContractAndModelTest
# Result: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS (8.3s)

# 4. Route Stops & Segment Fares Suite
mvn test -f bus-service/pom.xml -Dtest=RouteStopsAndSegmentFaresTest
# Result: Tests run: 14, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS (9.1s)
```

### Breakdown of New Focused Tests in `ScheduleValidationAndConflictTest`

| Test Method | Coverage | Result |
|---|---|---|
| `testPastDepartureRejectionAndFutureAcceptance` | Injected `Clock`, past departure rejected (400), future accepted | **PASS** |
| `testStopAndPointTimingPersistenceRoundtrip` | Stop-level datetimes, halt minutes, and point-level JSON serialization roundtrip | **PASS** |
| `testChronologicalValidation_StopAndPointViolations` | Intermediate arrival before previous departure, stop departure before arrival, negative halt, destination arrival mismatch, inverted point times | **PASS** |
| `testCheckConflict_AuthorizationAndOwnership` | Unauthenticated rejection (401), foreign bus rejection (403), owned bus success (200) | **PASS** |
| `testCheckConflict_InputValidation` | Missing dates (400), past departure (400), arrival before departure (400) | **PASS** |
| `testTurnaroundBuffer_ExactBoundaryAndCancelledExclusion` | 14:59 conflict vs. 15:00 non-conflict, cancelled schedule exclusion | **PASS** |

### Explicit Test Constraints (Not Run)
Per the Phase 6.3.1 execution constraints:
- **Angular production build**: NOT RUN.
- **Frontend unit tests**: NOT RUN.
- **Browser automation / visual tests**: NOT RUN.

---

## 8. Regression Checks and Limitations

1. **Fare Derivation Bug Fixed**:
   During test suite execution, `RouteStopsAndSegmentFaresTest.testCreateSchedule_WithStopsPayload_AutoConfiguresStops` failed because `validateScheduleDateTimeAndFare` expected `fare > 0` before `configureRouteStops` could calculate cumulative fares from the segment stops. Fixed by computing total fare and route endpoints from `stops` when omitted in the parent request.
2. **Historical Bookings and Schedule Querying**:
   - `points_json`, `arrival_date`, and `departure_date` are fully nullable.
   - Existing schedules created prior to Phase 6.3 continue to load with zero errors.
   - Verified that booking cancellation, seat-hold reconciliation, and payment flows remain completely unaffected (verified via `SeatHoldReconciliationServiceTest` and `ScheduleCancellationCoordinatorServiceTest`).
3. **Turnaround Buffer Symmetry**:
   The turnaround buffer logic is identical between the pre-check endpoint and the transactional creation method. No discrepancy exists between what the UI pre-check reports and what the backend persists.

---

## 9. Items Requiring Later Verification in Final Schedule-Creation Flow

The following items are deferred to Step 4 (Configure Fares) and Step 5 (Review & Create):
1. **Fare Configuration Payload Assembly**: Ensuring segment-specific pricing from Step 4 combines seamlessly with the timing and point structures verified in this phase.
2. **Review & Create Payload Submission**: Submitting the complete payload containing route ID, bus ID, trip start/end, intermediate stop timestamps, halt durations, and points JSON to `POST /api/buses/schedules`.
3. **End-to-End Database Verification**: Running full integration tests against the live MySQL container once the complete 6-step wizard workflow is completed.
