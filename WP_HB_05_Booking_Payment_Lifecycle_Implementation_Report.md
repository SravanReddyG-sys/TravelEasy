# WP-HB-05 — Booking and Payment Lifecycle Integration: Implementation Report

**Work Package Reference:** WP-HB-05  
**Project:** TravelEasy Microservices Platform  
**Target Services:** `booking-service`, `hotel-service`, `payment-service`  
**Date of Completion:** 2026-11-09  
**Status:** COMPLETE & VERIFIED (479/479 Automated Tests Passing)

---

## 1. Executive Summary

Work Package **WP-HB-05 (Booking and Payment Lifecycle Integration)** delivers the authoritative multi-room, multi-allocation hotel reservation and payment lifecycle integration across TravelEasy's distributed backend architecture. Building on the foundation established in WP-HB-04 (Hotel Multi-Allocation Inventory Architecture), this work package connects the **Booking Service**, **Hotel Service**, and **Payment Service** into an atomic, transactional, and idempotent reservation pipeline.

### Core Achievements
1. **Unified Booking Reference & Consolidated Hold:** A single booking reference (`BKG-H-XXXXXXXX`) identifies the entire multi-room hotel reservation end-to-end. One consolidated call from Booking Service to Hotel Service (`POST /api/hotels/reservations`) reserves all requested room allocations atomically.
2. **Relational Snapshot Persistence:** Fully replaces legacy reliance on the fragile 4000-character `detailsJson` column for multi-room bookings by introducing relational tables `hotel_reservation_snapshots` and `hotel_allocation_snapshots`. These tables persist immutable historical pricing breakdowns, room metadata, and extendable slots for guest details (ready for WP-HB-06).
3. **Fail-Closed Payment Validation:** Strict fail-closed verification ensures that Payment Service verifies booking existence, status eligibility (rejects `CONFIRMED` with 409, `EXPIRED` with 400), deadline expiration (rejects past `expiresAt` with 400), and amounts (strictly matches requested amount against authoritative booking `totalAmount`).
4. **Razorpay Order Idempotency:** Payment Service manages Razorpay orders idempotently: reuses valid `CREATED` orders with matching amounts, rejects already-paid transactions (`SUCCESS`) with 409 Conflict, rejects `REFUNDED` transactions with 409 Conflict, and permits controlled retries for `FAILED` attempts using a per-booking synchronized mutex.
5. **Unified Success Confirmation & Compensation Refunds:** Both checkout frontend signature verification (`/verify`) and asynchronous gateway webhooks (`payment.captured`) converge on a single authoritative confirmation pathway. If a payment succeeds after reservation expiration or inventory lock expiration (late payment race condition), the system automatically initiates a gateway refund via Razorpay and coordinates booking expiry.
6. **Zero Bus Booking Regression:** Verified complete backwards compatibility and non-regression across all existing bus booking creation, confirmation, cancellation, and expiry workflows.

---

## 2. Architectural Topology and Integration Boundaries

```
[ Traveller Client (Angular Frontend) ]
                   │
                   ▼ (1) POST /api/bookings/hotel-reservations (Consolidated Initiation)
       ┌────────────────────────┐
       │     Booking Service    │
       │ (Port 8082 / Port 8080)│
       └───────────┬────────────┘
                   │
                   │ (2) POST /api/hotels/reservations
                   │     (X-Internal-Token JWT, Consolidated Hold)
                   ▼
       ┌────────────────────────┐
       │      Hotel Service     │
       │       (Port 8083)      │
       └────────────────────────┘
                   ▲
                   │ (3) Returns authoritative pricing & HELD allocation details
                   │
       ┌────────────────────────┐
       │     Booking Service    │
       │ (Persists snapshots &  │
       │  returns expiresAt)    │
       └───────────┬────────────┘
                   │
                   ▼ (4) POST /api/payments/create-order
       ┌────────────────────────┐
       │     Payment Service    │
       │       (Port 8084)      │
       └───────────┬────────────┘
                   │
                   ├─────────────────────────┐
                   │ (5) verifyBookingAmount │ (6) createOrder (Paise)
                   ▼                         ▼
       ┌────────────────────────┐   ┌────────────────────────┐
       │     Booking Service    │   │    Razorpay Gateway    │
       │   (GET /ref/{ref})     │   │     (api.razorpay)     │
       └────────────────────────┘   └────────────────────────┘
                   ▲
                   │ (7) PUT /ref/{ref}/confirm (JWT)
                   │
       ┌────────────────────────┐
       │     Payment Service    │ ◄─── (Webhook or Checkout Verify)
       └───────────┬────────────┘
                   │
                   ▼ (8) PUT /api/hotels/rooms/confirm-stay (JWT)
       ┌────────────────────────┐
       │      Hotel Service     │
       │ (Atomic CONFIRMED lock)│
       └────────────────────────┘
```

---

## 3. Service-to-Service Communication and Internal Authentication

Service-to-service communication is secured using HMAC-SHA256 JWT tokens with configuration-backed shared secrets:

- **Secret Property:** `travel-easy.security.jwt.secret` (with environment variable fallback `JWT_SECRET`).
- **Token Claims:** Includes `sub` ("internal-service"), `service` (calling service identifier), `roles` (`ROLE_INTERNAL_SERVICE`), `iat`, and `exp` (15-minute validity window).
- **HTTP Headers:** Transmitted via both `Authorization: Bearer <token>` and `X-Internal-Token: <token>`, plus caller identifier `X-Internal-Service: <service-name>`.
- **Validation:** Both `BookingController` and `HotelController` strictly validate incoming internal tokens before permitting sensitive state mutations (e.g. `confirm-stay`, `release-stay`, `PUT /ref/{bookingRef}/confirm`).

---

## 4. Relational Persistence and Snapshot Schema

### 4.1 Schema Tables
To permanently solve the limitation of storing JSON strings inside a 4000-character `detailsJson` column, two relational tables were introduced:

1. **`hotel_reservation_snapshots`**:
   - `id` (BIGINT, PK, Auto-increment)
   - `booking_id` (BIGINT, UNIQUE, Indexed)
   - `booking_ref` (VARCHAR(64), NOT NULL, Indexed)
   - `hotel_id` (BIGINT, NOT NULL, Indexed)
   - `hotel_name` (VARCHAR(255))
   - `check_in_date` (DATE, NOT NULL)
   - `check_out_date` (DATE, NOT NULL)
   - `total_nights` (INT)
   - `total_amount` (DOUBLE, NOT NULL)
   - `currency` (VARCHAR(10), NOT NULL, Default 'INR')
   - `status` (VARCHAR(30), NOT NULL, Default 'HELD')
   - `created_at` (DATETIME, NOT NULL)
   - `updated_at` (DATETIME, NOT NULL)

2. **`hotel_allocation_snapshots`**:
   - `id` (BIGINT, PK, Auto-increment)
   - `reservation_snapshot_id` (BIGINT, FK to `hotel_reservation_snapshots.id`, Indexed)
   - `room_id` (BIGINT, NOT NULL, Indexed)
   - `room_type_name` (VARCHAR(255), NOT NULL)
   - `quantity` (INT, NOT NULL)
   - `allocation_total` (DOUBLE, NOT NULL)
   - `nightly_rates_json` (TEXT) - JSON breakdown of nightly rates across the stay
   - `check_in_date` (DATE, NOT NULL)
   - `check_out_date` (DATE, NOT NULL)
   - `status` (VARCHAR(30), NOT NULL, Default 'HELD')
   - `guest_allocation_json` (TEXT) - Extensible payload allocated for WP-HB-06 per-room guest details
   - `created_at` (DATETIME, NOT NULL)

### 4.2 Migration Script
Flyway migration script created at:
[`booking-service/src/main/resources/db/migration/V5__create_hotel_reservation_snapshot_tables.sql`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/booking-service/src/main/resources/db/migration/V5__create_hotel_reservation_snapshot_tables.sql).

---

## 5. Consolidated Reservation Orchestration and Hold Lifecycle

Implemented in [`HotelBookingOrchestrationService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/booking-service/src/main/java/com/traveleasy/booking/service/HotelBookingOrchestrationService.java):

1. **Validation:** Checks hotel presence, date validity (`checkInDate.isBefore(checkOutDate)`), non-empty allocations list, and valid quantities.
2. **Initial Booking Creation:** Persists a `Booking` entity with `status = INITIATED` and authoritative `expiresAt = LocalDateTime.now().plusMinutes(15)`.
3. **Consolidated Inventory Hold:** Dispatches `POST /api/hotels/reservations` to Hotel Service with deterministic room-lock ordering. If Hotel Service rejects (e.g. 409 Conflict due to sold-out rooms), booking is updated to `FAILED` and a `ResponseStatusException` is propagated.
4. **Relational Snapshot Persistence:** Persists `HotelReservationSnapshot` and child `HotelAllocationSnapshot` records with authoritative totals and nightly price breakdowns.
5. **Compensation on Persistence Failure:** If snapshot persistence fails after Hotel Service hold succeeded, the service immediately invokes compensation release via `POST /api/hotels/rooms/release-stay` to prevent orphaned holds.
6. **Transition to PENDING_PAYMENT:** Updates parent booking to `PENDING_PAYMENT` with authoritative total and returns response to traveller with `expiresAt`.

---

## 6. Payment Service Integration and Razorpay Order Lifecycle

Implemented in [`PaymentServiceImpl.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/payment-service/src/main/java/com/traveleasy/payment/service/PaymentServiceImpl.java):

### 6.1 Fail-Closed Booking Amount Verification (`verifyBookingAmount`)
- **Booking Existence:** Queries Booking Service via internal endpoint `GET /api/bookings/ref/{bookingRef}`. If 404, throws `404 NOT_FOUND`.
- **Reachability:** If Booking Service is down, unreachable, or returns a 5xx error, throws `503 SERVICE_UNAVAILABLE`. Payment order is NOT created.
- **Eligibility:** Rejects already `CONFIRMED` bookings with `409 CONFLICT`. Rejects `EXPIRED`, `CANCELLED`, or `FAILED` bookings with `400 BAD_REQUEST`.
- **Deadline:** Parses authoritative `expiresAt` (supporting both UTC `Instant` ISO strings and `LocalDateTime`). If current time is after deadline, throws `400 BAD_REQUEST ("Booking reservation deadline has expired")`.
- **Anti-Tampering:** Verifies that requested payment amount matches authoritative booking `totalAmount` within 0.01 tolerance. Throws `400 BAD_REQUEST` on discrepancy.

### 6.2 Order Creation Idempotency & Concurrency Mutex
- Uses a synchronized mutex locked on `bookingRef` (`ConcurrentHashMap<String, Object> orderCreationLocks`).
- **Fast-path rejection:** If an existing transaction for `bookingRef` is `SUCCESS` or `REFUNDED`, throws `409 CONFLICT` immediately without network calls.
- **Order Reuse:** If an existing transaction is in `CREATED` status with a valid `razorpayOrderId` and matching amount, reuses and returns the existing order details without making a duplicate call to Razorpay.
- **Controlled Retry:** If an existing transaction is `FAILED`, allows creating a new Razorpay order provided the booking remains valid and within deadline.

---

## 7. Unified Payment Success Processing & Compensation

Implemented in `processAuthoritativePaymentSuccess`:
- Shared by checkout client verification (`verifyPayment`) and asynchronous gateway webhooks (`handlePaymentCapturedWebhook`).
- **Atomic Gateway Audit:** Saves `PaymentTransaction` with `status = SUCCESS`, recording `razorpayPaymentId`, signature, and timestamp.
- **Expiry Race / Late Payment Detection:** Inspects booking record in Booking Service. If booking has transitioned to `EXPIRED` or `CANCELLED`, or if deadline has elapsed:
  - Automatically initiates a full compensation refund via Razorpay (`processRefund`).
  - Records refund with idempotency key `LATE_PAYMENT_REFUND`.
  - Returns `PaymentConfirmationResult(confirmed=false, refunded=true)`.
- **Authoritative Confirmation:** Calls `PUT /api/bookings/ref/{bookingRef}/confirm` with internal service credentials.
- **Transient Fault Tolerance:** If Booking Service returns transient 5xx, rethrows the error without marking the webhook event `PROCESSED`, ensuring the recovery worker will retry.

---

## 8. Automated Testing Matrix and Verification Results

### 8.1 Test Execution Summary Across All Services

| Service | Test Suite Focus | Tests Run | Failures | Errors | Result |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **`hotel-service`** | Multi-allocation hold, locking, aggregate confirm/release | **260** | **0** | **0** | **PASSED** |
| **`booking-service`** | Orchestration, snapshots, 15m expiry reconciliation, bus regression | **119** | **0** | **0** | **PASSED** |
| **`payment-service`** | Fail-closed validation, order idempotency, unified verify/webhook | **100** | **0** | **0** | **PASSED** |
| **Total Platform** | **Full WP-HB-04 & WP-HB-05 Backend Integration** | **479** | **0** | **0** | **ALL PASSED** |

### 8.2 New Test Classes Added in WP-HB-05
1. [`HotelBookingOrchestrationTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/booking-service/src/test/java/com/traveleasy/booking/service/HotelBookingOrchestrationTest.java) in `booking-service`:
   - `testInitiateHotelReservation_Success`: Multi-allocation hold, snapshot persistence, and 15m `expiresAt`.
   - `testInitiateHotelReservation_HotelServiceConflict`: Proper 409 CONFLICT propagation when rooms are unavailable.
   - `testInitiateHotelReservation_CompensationReleaseOnPersistenceFailure`: Verification of compensation release via `POST /api/hotels/rooms/release-stay`.
   - `testInitiateHotelReservation_Idempotent`: Verified duplicate request returns existing booking and snapshot details without contacting Hotel Service.
   - `testInitiateHotelReservation_ValidationFailures`: Rejection of missing hotelId, invalid dates, and empty allocations.

2. [`PaymentLifecycleIntegrationTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/payment-service/src/test/java/com/traveleasy/payment/service/PaymentLifecycleIntegrationTest.java) in `payment-service`:
   - `testFailClosed_BookingNotFound`: Verified 404 NOT_FOUND.
   - `testFailClosed_BookingServiceUnreachable`: Verified 503 SERVICE_UNAVAILABLE.
   - `testFailClosed_BookingAlreadyConfirmed`: Verified 409 CONFLICT.
   - `testFailClosed_BookingExpiredStatus`: Verified 400 BAD_REQUEST.
   - `testFailClosed_BookingPastExpiresAtDeadline`: Verified 400 BAD_REQUEST on deadline expiry.
   - `testFailClosed_AmountTamperingMismatch`: Verified 400 BAD_REQUEST on amount mismatch.
   - `testOrderReuse_ExistingCreatedOrder`: Verified reuse of existing Razorpay order.
   - `testOrderCreation_FastPathRejection_AlreadyPaid`: Verified 409 CONFLICT on paid booking.
   - `testOrderCreation_FastPathRejection_AlreadyRefunded`: Verified 409 CONFLICT on refunded booking.
   - `testOrderCreation_RetryPermitted_ForFailedTransaction`: Verified controlled retry for FAILED transaction.
   - `testPaymentSuccess_ConfirmsBookingService`: Verified Booking Service confirmation call.
   - `testLatePayment_InitiatesAutomatedRefund`: Verified automated compensation refund on late payment.
   - `testBookingServiceTransientFailure_RethrowsForWebhookRetry`: Verified 5xx propagation for webhook retry.

---

## 9. Inventory of Changed and Created Files

### 9.1 Created Files
- `booking-service/src/main/java/com/traveleasy/booking/dto/HotelReservationInitiationRequest.java`
- `booking-service/src/main/java/com/traveleasy/booking/dto/HotelReservationInitiationResponse.java`
- `booking-service/src/main/java/com/traveleasy/booking/dto/ReservationAllocationItemDto.java`
- `booking-service/src/main/java/com/traveleasy/booking/model/HotelReservationSnapshot.java`
- `booking-service/src/main/java/com/traveleasy/booking/model/HotelAllocationSnapshot.java`
- `booking-service/src/main/java/com/traveleasy/booking/repository/HotelReservationSnapshotRepository.java`
- `booking-service/src/main/java/com/traveleasy/booking/repository/HotelAllocationSnapshotRepository.java`
- `booking-service/src/main/java/com/traveleasy/booking/service/HotelBookingOrchestrationService.java`
- `booking-service/src/main/resources/db/migration/V5__create_hotel_reservation_snapshot_tables.sql`
- `booking-service/src/test/java/com/traveleasy/booking/service/HotelBookingOrchestrationTest.java`
- `payment-service/src/test/java/com/traveleasy/payment/service/PaymentLifecycleIntegrationTest.java`

### 9.2 Modified Files
- `booking-service/src/main/java/com/traveleasy/booking/controller/BookingController.java` (Consolidated initiation endpoint, deadline enforcement on confirmation, snapshot GET endpoints)
- `booking-service/src/main/java/com/traveleasy/booking/model/Booking.java` (Added `expiresAt` column and index)
- `booking-service/src/main/java/com/traveleasy/booking/repository/BookingRepository.java` (Added `findByStatusInAndExpiresAtBefore`)
- `booking-service/src/main/java/com/traveleasy/booking/service/BookingExpiryReconciliationService.java` (Authoritative `expiresAt` reconciliation, snapshot status synchronization)
- `payment-service/src/main/java/com/traveleasy/payment/service/PaymentServiceImpl.java` (Fail-closed amount validation, Razorpay order reuse & fast-path rejection, unified success confirmation with late payment auto-refund)
- `payment-service/src/test/java/com/traveleasy/payment/service/PaymentServiceTest.java` (Updated with lenient stubbings for internal booking validation)

---

## 10. Readiness for WP-HB-06 (Frontend & Guest Allocations)

The backend integration is completely prepared for WP-HB-06:
1. `HotelAllocationSnapshot` contains `guestAllocationJson` for persisting individual guest names, age, and ID proofs per room.
2. `HotelReservationInitiationRequest` includes `guestDetailsJson` passed transparently to snapshots.
3. Query endpoints `GET /api/bookings/{bookingId}/snapshot` and `GET /api/bookings/ref/{bookingRef}/snapshot` allow the Angular frontend to display complete multi-room reservation breakdowns without parsing legacy text fields.
4. Clean contracts exist for `/api/bookings/hotel-reservations`, `/api/payments/create-order`, and `/api/payments/verify`.

---

## 11. Final Sign-Off & Acceptance Checklist

- [x] Single booking reference identifying the entire multi-room hotel reservation.
- [x] One consolidated call to Hotel Service reservation hold endpoint (`POST /api/hotels/reservations`).
- [x] Relational snapshot persistence (`hotel_reservation_snapshots` and `hotel_allocation_snapshots`).
- [x] Accommodation for WP-HB-06 individual guest allocations in snapshot schema.
- [x] Authoritative 15-minute reservation expiry tracked by `expiresAt` column and reconciled automatically.
- [x] Fail-closed amount validation in Payment Service against Booking Service authoritative record.
- [x] Razorpay order idempotency (order reuse on CREATED; rejection on SUCCESS/REFUNDED; retry on FAILED).
- [x] Unified payment success confirmation pathway for both checkout verify and webhooks.
- [x] Automated compensation refund on late payments / expired reservations.
- [x] Webhook transient 5xx error propagation to preserve webhook inbox retryability.
- [x] Zero regressions across bus booking and legacy hotel flows.
- [x] All 479 unit and integration tests passing across all three services.
