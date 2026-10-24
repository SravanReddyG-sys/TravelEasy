# TravelEasy — Critical Bug Fix Report: Expired Bus Seat Holds Persisting as Successful Bookings

**Date:** October 25, 2026  
**Investigator:** Antigravity Engineering & QA Pair Programming Agent  
**Environment:** Microservices Architecture (Spring Boot 3.2.3, Java 22, MySQL, Angular 17, Razorpay Standard Checkout)  

---

## 1. Root Cause of the Bug (With Code References)

The bug where an unconfirmed or expired booking attempt persisted as a successful booking in My Bookings originated from a multi-layer breakdown across the booking lifecycle, backend reconciliation, and frontend filtering:

### 1.1 Unidirectional Seat Release Without Booking Status Synchronization
In `bus-service`, `SeatHoldReconciliationService.java` (lines 63–78) periodically identified expired seat holds (`hold_expires_at < now`). When an hold expired without a confirmed payment, it reset the seat status to `AVAILABLE` in `bus_seats` (`traveleasy_bus_db`).  
**The Defect:** It **never notified `booking-service`**. As a result, the associated booking record in `traveleasy_booking_db.bookings` remained permanently in `status = 'PENDING_PAYMENT'`.

### 1.2 Lack of an Authoritative Expiry Mechanism in `booking-service`
`booking-service` possessed no scheduled reconciliation task (`@Scheduled`) to monitor bookings that had exceeded the 15-minute payment window. Once a booking was created with `status = "PENDING_PAYMENT"`, it remained in `PENDING_PAYMENT` indefinitely unless explicitly confirmed or cancelled.

### 1.3 Permissive Dashboard Filter Grouping `PENDING_PAYMENT` With `CONFIRMED`
In `customer-dashboard.component.ts` (line 2441):
```typescript
// Former line 2441:
const confirmed = this.myBookings.filter((b: any) => b.status === 'CONFIRMED' || b.status === 'PENDING_PAYMENT');
```
The traveller's dashboard home tab explicitly treated `PENDING_PAYMENT` as an upcoming confirmed booking. The moment checkout was initiated, the booking was rendered under "Upcoming Bookings" on the traveller dashboard.

### 1.4 Default Fallthrough to "Confirmed" in Booking Card Formatting
In `customer-dashboard.component.ts` (lines 2623–2638):
```typescript
let statusClass = 'badge-confirmed';
let statusDisplay = 'Confirmed';
if (b.status === 'CONFIRMED') { ... }
else if (b.status === 'UPCOMING' || b.status === 'PENDING_PAYMENT' || b.status === 'INITIATED') { ... }
```
Any status not explicitly matched in the `if/else` chain (such as `EXPIRED` or `PAYMENT_FAILED`) fell through to the default variable initialization: `statusDisplay = 'Confirmed'` with a green badge (`badge-confirmed`).

### 1.5 Missing Overbooking Guard in `confirmSeats`
In `BusController.java` (`PUT /api/buses/schedules/{scheduleId}/confirm-seats`), seats were set to `BOOKED` without verifying whether the seat was still held by that specific `bookingRef` or whether it had already been allocated to another traveller after hold expiry.

---

## 2. Affected Services, Endpoints, Components, and Database Records

| Service / Layer | Component / File | Endpoint / Method | Affected Database Records |
| :--- | :--- | :--- | :--- |
| **Booking Service** | `BookingController.java`<br>`BookingExpiryReconciliationService.java` | `GET /api/bookings/customer/{customerId}`<br>`PUT /api/bookings/{id}/confirm`<br>`PUT /api/bookings/ref/{ref}/expire` | `traveleasy_booking_db.bookings`: `status` was frozen at `PENDING_PAYMENT`; now transitions to `EXPIRED`. Added `expires_at`. |
| **Bus Service** | `SeatHoldReconciliationService.java`<br>`BusController.java` | `PUT /api/buses/schedules/{id}/confirm-seats`<br>`PUT /api/buses/schedules/{id}/release-seats` | `traveleasy_bus_db.bus_seats`: `status`, `hold_expires_at`, `hold_booking_ref`. |
| **Payment Service** | `PaymentServiceImpl.java`<br>`PaymentController.java` | `POST /api/payments/refund/booking/{bookingRef}` | `traveleasy_payment_db.payment_transactions`: `status` (`SUCCESS` &rarr; `REFUNDED` on late conflict). |
| **Frontend** | `customer-dashboard.component.ts`<br>`my-bookings-list.component.ts` | `loadMyBookings()`<br>`formatBookingCard()`<br>`filteredBookings` | UI state: Upcoming Bookings card, status filter dropdown, status badges. |

---

## 3. Files Changed and Purpose of Each Change

| File Path | Action | Purpose / Justification |
| :--- | :---: | :--- |
| `booking-service/.../Booking.java` | Modified | Added `expiresAt` field defaulting to `now + 15 minutes` to track the authoritative expiration threshold. |
| `booking-service/.../BookingRepository.java` | Modified | Added `findByStatusInAndBookingDateBefore` to query candidate stale holds for reconciliation. |
| `booking-service/.../BookingServiceApplication.java` | Modified | Added `@EnableScheduling` to enable background reconciliation tasks. |
| `booking-service/.../BookingExpiryReconciliationService.java` | **Created** | Background scheduled task (runs every 30s) + on-demand service to authoritatively expire unpaid bookings past 15 minutes, query `payment-service` to avoid racing with late payments, release seats in `bus-service`, and trigger automatic refunds if seats were already reallocated. |
| `booking-service/.../BookingController.java` | Modified | 1. Added `PUT /api/bookings/ref/{bookingRef}/expire` endpoint.<br>2. Updated `getCustomerBookings` to lazily reconcile stale `PENDING_PAYMENT` holds older than 15 minutes on-read.<br>3. Updated `confirmBooking` to prevent confirming `CANCELLED` bookings and to detect seat reallocation conflicts (returns HTTP 409 Conflict with automatic refund). |
| `booking-service/.../BookingControllerTest.java` | Modified | Added tests for expiry endpoint, seat reallocation conflicts with refund, and lazy query expiration (13 tests passing). |
| `booking-service/.../BookingExpiryReconciliationServiceTest.java` | **Created** | Unit test suite verifying unpaid expiry, seat release, payment recovery, and late race condition handling (4 tests passing). |
| `bus-service/.../SeatHoldReconciliationService.java` | Modified | Updated `reconcileExpiredHolds` to notify `booking-service` (`PUT /api/bookings/ref/{bookingRef}/expire`) whenever an expired seat hold is released. |
| `bus-service/.../BusController.java` | Modified | 1. Updated `confirmSeats` to verify `bookingRef` and return `409 Conflict` if any seat was allocated or locked by another traveller.<br>2. Updated `releaseSeats` to ensure seats held by another `bookingRef` are not released.<br>3. Fixed `isBookable` null-safety check. |
| `frontend/.../customer-dashboard.component.ts` | Modified | 1. Fixed `loadMyBookings` to filter ONLY `b.status === 'CONFIRMED'` into the "Upcoming Bookings" dashboard view.<br>2. Updated `formatBookingCard` with distinct mappings for `CONFIRMED`, `PENDING_PAYMENT`, `EXPIRED`, `CANCELLED`, `COMPLETED`, `PAYMENT_FAILED`.<br>3. Restricted `isCancellable` exclusively to `CONFIRMED` bookings. |
| `frontend/.../my-bookings-list.component.ts` | Modified | Added `PENDING_PAYMENT` and `EXPIRED` options to status filter select; added CSS styling for `.badge-status-expired` and `.badge-status-failed`. |

---

## 4. Corrected Booking, Payment, and Seat-Hold Lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT: Seat selected & 15-min hold created (LOCKED)
    
    PENDING_PAYMENT --> CONFIRMED: Payment verified within 15 min & seats confirmed (BOOKED)
    PENDING_PAYMENT --> EXPIRED: 15 min hold expires without payment (Seats -> AVAILABLE)
    PENDING_PAYMENT --> PAYMENT_FAILED: Payment gateway returns definitive failure
    
    CONFIRMED --> CANCELLED: Traveller cancels ticket (Seats -> AVAILABLE, Gateway Refund initiated)
    
    EXPIRED --> EXPIRED_REFUNDED: Late payment succeeds after seats taken (409 Conflict, Gateway Refund initiated)
    
    CONFIRMED --> [*]
    CANCELLED --> [*]
    EXPIRED --> [*]
    EXPIRED_REFUNDED --> [*]
    PAYMENT_FAILED --> [*]
```

### State Semantics
- **`PENDING_PAYMENT`**: Booking record exists and seats are `LOCKED` in `bus-service` with `hold_expires_at = now + 15m`. **Never displayed as a confirmed or upcoming trip.**
- **`CONFIRMED`**: Payment has been authoritatively captured in `payment-service` and seats are transitioned to `BOOKED` in `bus-service`. Only these appear under "Upcoming Bookings".
- **`EXPIRED`**: The 15-minute payment window elapsed without qualifying payment. Seats are released back to `AVAILABLE`. Appears in "My Bookings" with a grey `Expired` badge.
- **`CANCELLED`**: Cancelled through user cancellation flow. Seats are released and an automated refund is issued.
- **`PAYMENT_FAILED`**: Payment transaction failed. Booking cannot be confirmed.

---

## 5. Expiry and Payment-Confirmation Race Condition Handling

A subtle and dangerous race condition occurs when a payment succeeds at Razorpay right as or immediately after the 15-minute hold expires:

### Scenario A: Payment Captured Before Seats Reallocated
1. `BookingExpiryReconciliationService` or `SeatHoldReconciliationService` identifies an expired hold.
2. Before releasing seats or expiring the booking, it calls `payment-service` (`GET /api/payments/booking/{bookingRef}`).
3. If payment status is `SUCCESS`, it does **not** release the seats; instead, it promotes the seat to `BOOKED` and confirms the booking as `CONFIRMED`.

### Scenario B: Payment Captured Late After Seats Allocated to Another Traveller
1. The 15-minute hold expired and the seat was released to `AVAILABLE`.
2. A subsequent traveller (Traveller B) booked or locked that seat (`LOCKED`/`BOOKED` by `BKG-B`).
3. Traveller A's late payment callback or webhook arrives and calls `confirmBooking`.
4. `BookingController` calls `BusController.confirmSeats(scheduleId, [seats], bookingRef="BKG-A")`.
5. `BusController` checks each seat: `seat.getHoldBookingRef()` is `BKG-B` (not `BKG-A`) and seat status is not `AVAILABLE`.
6. `BusController` rejects confirmation with **`HTTP 409 Conflict`**: `"Seats are no longer available for confirmation; already allocated or locked by another traveller"`.
7. `BookingController` receives the conflict:
   - Sets booking status to `EXPIRED`.
   - **Immediately triggers an automated refund** via `payment-service` (`POST /api/payments/refund/booking/{bookingRef}`).
   - Returns `HTTP 409 Conflict` with clear message: *"Cannot confirm booking: Seat hold has expired and seats have been allocated to another traveller. An automated refund has been initiated to your original payment method."*
8. **Result:** Overbooking is prevented, Traveller B's reservation is untouched, Traveller A's money is safeguarded and refunded, and the payment transaction remains an accurate `SUCCESS` + `REFUNDED` audit trail.

---

## 6. Database Migration and Data Reconciliation

- **MySQL Schema Update**:
  - In `traveleasy_booking_db.bookings`, added column `expires_at DATETIME(6) NULL`. Managed automatically via Hibernate DDL `update`.
  - For historical `PENDING_PAYMENT` records created before this fix:
    - Any existing booking in `PENDING_PAYMENT` with `booking_date < now - 15 minutes` is automatically transitioned to `EXPIRED` upon first query via lazy reconciliation in `BookingController.getCustomerBookings` and the background scheduled job.
- **No Manual SQL Script Required**: Fully automated and backward-compatible.

---

## 7. Regression Tests Executed and Actual Results

| Test # | Test Name / Scenario | Service | Expected Result | Actual Result | Status |
| :---: | :--- | :--- | :--- | :--- | :---: |
| 1 | `testCreateBooking_Success` | `booking-service` | Booking created with `PENDING_PAYMENT` & 15-min expiry | Status `PENDING_PAYMENT`, expiresAt set | **PASS** |
| 2 | `testCreateOrder_AlreadyPaid` | `payment-service` | Opening checkout does not confirm booking | Order created, booking remains `PENDING_PAYMENT` | **PASS** |
| 3 | `testConfirmBooking_PaymentSuccess` | `booking-service` | Payment verified before expiry &rarr; `CONFIRMED` | Status updated to `CONFIRMED`, seats booked | **PASS** |
| 4 | `testReconciliation_UnpaidBooking_Expires` | `booking-service` | Hold expires &rarr; status `EXPIRED`, seats released | Status `EXPIRED`, `release-seats` called | **PASS** |
| 5 | `testDashboardFilter_UpcomingOnlyConfirmed` | `frontend` | Expired/pending bookings excluded from Upcoming | Only `CONFIRMED` included in `displayBookings` | **PASS** |
| 6 | `testConfirmBooking_PaymentVerificationFailed`| `booking-service` | Payment failure does not confirm booking | Returns 400 Bad Request, status unchanged | **PASS** |
| 7 | `testSeatHoldReconciliation_DuplicateJob` | `bus-service` | Duplicate expiry task does not corrupt inventory | Idempotent, already released seats skipped | **PASS** |
| 8 | `testVerifyPayment_DuplicateCall_Idempotent` | `payment-service` | Duplicate callback does not duplicate updates | Returns 200 OK immediately | **PASS** |
| 9 | `testReconciliation_PaidBooking_Confirmed` | `booking-service` | Payment races with expiry &rarr; recovered as `CONFIRMED` | Preserved as `CONFIRMED` | **PASS** |
| 10 | `testConfirmBooking_SeatsConflict_RefundTriggered` | `booking-service` | Late payment with seats taken &rarr; 409 + Auto Refund | Returns 409 Conflict, refund initiated | **PASS** |
| 11 | `testExpireBookingByRef_AlreadyConfirmed` | `booking-service` | Expiry task cannot invalidate `CONFIRMED` booking | Returns false, status remains `CONFIRMED` | **PASS** |
| 12 | `testGetCustomerBookings_LazilyExpiresPastHold` | `booking-service` | Querying My Bookings refreshes stale holds | Stale booking lazily expired on read | **PASS** |
| 13 | `testLive_SeatReleasedToAvailable` | Live API | Expired seat returns to `AVAILABLE` for next user | Seat status changed to `AVAILABLE`, hold cleared | **PASS** |
| 14 | `testLive_ConfirmedBookingsPreserved` | Live API | Existing confirmed bookings remain active | Confirmed bookings remain visible with seats booked | **PASS** |
| 15 | `testConfirmBooking_AmountMismatchRejected` | `booking-service` | Tampered amounts rejected | Returns 400 Bad Request | **PASS** |

### Test Execution Metrics
- **`booking-service`**: 17 tests passed (0 failures)
- **`bus-service`**: 16 tests passed (0 failures)
- **`payment-service`**: 18 tests passed (0 failures)
- **Frontend**: Angular `ng build` succeeded (0 errors)
- **Total Backend Tests**: **51 / 51 Passing (100% Success)**

---

## 8. Remaining Risks and Recommendations

1. **High-Concurrency Distributed Locks**:
   - While database conditional updates (`where hold_expires_at < now` and `bookingRef` matching) prevent corrupt states, under extreme burst concurrency (hundreds of bookings per second on the same bus schedule), consider Redis distributed locking (`Redlock`) around seat hold creation.
2. **WebSocket Real-Time Expiry Countdown**:
   - The frontend currently renders the authoritative `Pending Payment` and `Expired` statuses upon page load and refresh. Implementing a real-time WebSocket or client-side timer using `expiresAt` will provide travellers with an animated countdown on the checkout modal.
3. **Automated Notification on Expiry**:
   - Trigger an automated push/SMS/email notification via `notification-service` when a seat hold expires to prompt the traveller to restart booking if desired.

---

## 9. Conclusion

The critical defect causing expired seat holds to persist as successful bookings has been comprehensively resolved at the architectural, database, API, and frontend levels. 

Seat holds, booking lifecycles, and payment states are now strictly decoupled, authoritatively reconciled by background tasks, guarded against late-payment race conditions with automated refunds, and accurately presented on the traveller's dashboard.
