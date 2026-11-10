# WP-HB-05 — Targeted Corrections Implementation Report
## Booking–Hotel Expiry Synchronization and Durable Refund Recovery

---

### 1. Executive Summary of the Two Identified Issues

Following the initial implementation of **WP-HB-05 (Booking and Payment Lifecycle Integration)**, two critical operational concerns were identified that required targeted corrections:

1. **Concern 1 — Expiry Synchronization Between Booking Service and Hotel Service (Part A):**
   - Booking Service established an authoritative 15-minute reservation deadline (`expiresAt = booking creation time + 15 minutes`), but this deadline was neither propagated to nor persisted within Hotel Service.
   - Hotel Service held room inventory indefinitely or relied on internal defaults without enforcing the parent booking's authoritative deadline during confirmation or aggregate release.
   - Consequently, a late payment or confirmation attempt could confirm inventory after Booking Service had already expired the reservation, creating split-brain aggregate state between the microservices.

2. **Concern 2 — Durable Refund Recovery and Retryability in Payment Service (Part B):**
   - When an automated compensation refund was triggered (due to a late payment after expiry or a room hold conflict / 409 Conflict), the immediate refund call to Razorpay was vulnerable to network timeouts, gateway outages, or application restarts.
   - If Razorpay failed transiently, the failure was merely logged; the compensation task was not durably persisted for background retry.
   - Duplicate events (e.g. concurrent user checkout verification and asynchronous Razorpay `payment.captured` webhooks) risked creating duplicate refund attempts or losing pending compensation work upon container restarts.

Both concerns have now been resolved across `booking-service`, `hotel-service`, and `payment-service` without altering approved architectural baselines.

---

### 2. Root Cause Analysis

#### 2.1 Root Cause of Expiry Asynchrony
- In `HotelBookingOrchestrationService.java`, `callHotelServiceConsolidatedReservation` sent `bookingRef`, `hotelId`, `checkInDate`, `checkOutDate`, and `allocations`, but omitted the `expiresAt` timestamp.
- In `hotel-service`, neither `ConsolidatedReservationRequestDto`, `HotelReservation` entity, nor `HotelReservationResponseDto` contained an `expiresAt` property.
- In `HotelReservationService.confirmReservation`, only the parent status (`HELD`) was checked; the elapsed time against the authoritative deadline was never evaluated, allowing late confirmations to succeed.
- In `booking-service`, `BookingController.confirmBookingByRef` and `BookingExpiryReconciliationService.expireSingleBooking` used simple non-locking reads (`findByBookingRef`), creating a concurrency race between parallel expiry reconciliation and customer payment confirmation.

#### 2.2 Root Cause of Compensation Refund Fragility
- In `PaymentServiceImpl.processAuthoritativePaymentSuccess`, automated compensation refunds for late payments or inventory conflicts were executed via an inline call to `processRefund(...)` wrapped in a simple `try-catch` block.
- There was no database-backed task entity to track pending, executing, or retryable compensation refunds across service restarts.
- Static idempotency keys (`"LATE_PAYMENT_REFUND"`, `"INVENTORY_UNAVAILABLE_REFUND"`) were passed instead of per-booking deterministic keys, preventing proper deduplication across concurrent checkout verification and webhook delivery.
- No automated background reconciliation worker existed to lease and retry failed compensation tasks with exponential backoff.

---

### 3. Files Created and Modified

#### 3.1 `hotel-service`
- **Modified:**
  - `hotel-service/src/main/java/com/traveleasy/hotel/dto/ConsolidatedReservationRequestDto.java` — Added `private LocalDateTime expiresAt`.
  - `hotel-service/src/main/java/com/traveleasy/hotel/model/HotelReservation.java` — Added `@Column(name = "expires_at") private LocalDateTime expiresAt`.
  - `hotel-service/src/main/java/com/traveleasy/hotel/dto/HotelReservationResponseDto.java` — Added `private LocalDateTime expiresAt`.
  - `hotel-service/src/main/java/com/traveleasy/hotel/service/HotelReservationService.java` — Persisted supplied `expiresAt`, enforced deadline in `confirmReservation`, auto-released expired holds on late confirmation attempts, annotated with `@Transactional(noRollbackFor = DomainValidationException.class)`, serialized `expiresAt` in response DTO.
  - `hotel-service/src/main/java/com/traveleasy/hotel/service/HotelReservationSchemaInitializer.java` — Added Step 4 to ensure `expires_at` column exists on `hotel_reservations`.
  - `hotel-service/src/test/java/com/traveleasy/hotel/HotelMultiAllocationInventoryTest.java` — Added 4 new integration tests covering expiry persistence, late confirmation rejection, auto-release, revival prevention, and release idempotency.

#### 3.2 `booking-service`
- **Modified:**
  - `booking-service/src/main/java/com/traveleasy/booking/service/HotelBookingOrchestrationService.java` — Passed authoritative `expiresAt` to Hotel Service in the consolidated reservation payload.
  - `booking-service/src/main/java/com/traveleasy/booking/repository/BookingRepository.java` — Added `@Lock(LockModeType.PESSIMISTIC_WRITE) findByBookingRefForUpdate` and `findByIdForUpdate`.
  - `booking-service/src/main/java/com/traveleasy/booking/controller/BookingController.java` — Secured confirmation endpoints with `@Transactional` and pessimistic locking (`findByIdForUpdate` / `findByBookingRefForUpdate` with fallback). Added missing `java.util.Optional` import.
  - `booking-service/src/main/java/com/traveleasy/booking/service/BookingExpiryReconciliationService.java` — Secured `expireSingleBooking` with pessimistic row locking to prevent interleaving with confirmation. Added missing `java.util.Optional` import.
  - `booking-service/src/test/java/com/traveleasy/booking/service/HotelBookingOrchestrationTest.java` — Added verification that `expiresAt` is present in the payload passed to Hotel Service.

#### 3.3 `payment-service`
- **Created:**
  - `payment-service/src/main/java/com/traveleasy/payment/model/PaymentRefundTask.java` — Persisted durable refund task entity with full lifecycle states (`PENDING`, `PROCESSING`, `INITIATED`, `FAILED_RETRYABLE`, `PROCESSED`, `FAILED_FINAL`), lease-locking fields (`lockedBy`, `lockedUntil`), retry counters, and timestamps.
  - `payment-service/src/main/java/com/traveleasy/payment/repository/PaymentRefundTaskRepository.java` — JPA repository with atomic lease-claiming queries (`claimRefundTask`), status transitions (`markProcessed`, `markInitiated`, `markFailedRetryable`, `markFailedFinal`), and retryable candidate lookups (`findTasksForRetry`).
  - `payment-service/src/main/java/com/traveleasy/payment/worker/PaymentRefundReconciliationWorker.java` — Periodic background worker (`@Scheduled(fixedDelay = 15000)`) with lease-locking to reconcile and retry pending compensation tasks.
  - `payment-service/src/test/java/com/traveleasy/payment/worker/PaymentRefundReconciliationWorkerTest.java` — Unit tests verifying lease claiming, error isolation, and disabled toggles.
- **Modified:**
  - `payment-service/src/main/java/com/traveleasy/payment/service/PaymentServiceImpl.java` — Integrated `PaymentRefundTaskRepository`, implemented `recordOrGetCompensationRefundTask` and `reconcileAndExecuteRefundTask`, added gateway pre-check reconciliation (`fetchAllRefunds`), updated `processAuthoritativePaymentSuccess` for durable compensation tracking, preserved backward-compatible constructors.
  - `payment-service/src/main/java/com/traveleasy/payment/service/PaymentStateTransitionService.java` — Added null-safety check (`if (saved == null) saved = txn;`) in `recordRefundInitiated` and `recordRefundCompleted`.
  - `payment-service/src/test/java/com/traveleasy/payment/service/PaymentLifecycleIntegrationTest.java` — Added 6 comprehensive tests for durable refund creation, transient failure backoff, gateway reconciliation deduplication, and max attempts exhaustion.

---

### 4. Booking-to-Hotel Expiry Synchronization Changes

1. **Authoritative Ownership:**
   - Booking Service remains the **sole expiry authority**.
   - Upon reservation initiation, `HotelBookingOrchestrationService` calculates `expiresAt = LocalDateTime.now().plusMinutes(15)` and stores it on `Booking`.
   - The exact deadline is serialized as an ISO-8601 string (`expiresAt.toString()`) and transmitted in the HTTP request payload to `POST /api/hotels/reservations`.

2. **Persistence in Hotel Service:**
   - `HotelReservationService.executeReservationTransaction` unpacks `dto.getExpiresAt()`.
   - The timestamp is persisted directly into `HotelReservation.expiresAt`.
   - Hotel Service **does not recalculate or extend** this window; all child room allocations remain governed by the parent's `expiresAt`.
   - The response DTO serializes `expiresAt` back to Booking Service and API clients.

---

### 5. Persisted Expiry Handling in Hotel Service

1. **Validation on Confirmation (`confirmReservation`):**
   - When `confirmReservation` is called (via `/api/hotels/rooms/confirm-stay`), the parent aggregate is locked using `findByBookingRefForUpdate`.
   - If `parent.getExpiresAt() != null && !LocalDateTime.now().isBefore(parent.getExpiresAt())`:
     - The confirmation is authoritatively **rejected**.
     - An automatic release routine (`releaseHeldAllocationsOnExpiry`) is invoked immediately within the transaction:
       - All child allocations in `HELD` status are transitioned to `RELEASED`.
       - Corresponding daily inventory `heldRooms` counters are decremented.
       - Parent aggregate status is updated to `RELEASED`.
       - Legacy availability snapshots are updated.
     - A `DomainValidationException` is thrown: `"Reservation deadline has expired at <expiresAt> and cannot be confirmed: <bookingRef>"`.
     - The method is annotated with `@Transactional(noRollbackFor = DomainValidationException.class)` so that the transition to `RELEASED` is safely committed, ensuring the expired reservation cannot remain eligible for confirmation.

2. **Revival Prevention and Release Idempotency:**
   - Any subsequent confirmation attempt on an expired/released reservation hits `if ("RELEASED".equalsIgnoreCase(parent.getStatus())) throw new DomainValidationException(...)`, permanently preventing revival.
   - When Booking Service expiry reconciliation calls `releaseReservation` (`POST /api/hotels/rooms/release-stay?bookingRef=...`), Hotel Service sees `parent.getStatus().equalsIgnoreCase("RELEASED")` and immediately returns `{ "status": "RELEASED", "idempotent": true }`, preserving complete idempotency.

---

### 6. Confirmation and Expiry Concurrency Protection

To guarantee that concurrent payment confirmation and background expiry reconciliation can never produce split-brain or conflicting aggregate states:

1. **Pessimistic Locking in Booking Service:**
   - Added `findByBookingRefForUpdate(bookingRef)` and `findByIdForUpdate(id)` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` to `BookingRepository`.
   - Both `BookingController.confirmBooking` / `confirmBookingByRef` and `BookingExpiryReconciliationService.expireSingleBooking` execute within `@Transactional` boundaries and acquire a pessimistic write lock on the `Booking` database row.

2. **Race Resolution Dynamics:**
   - **Case A — Payment Confirmation Acquires Lock First:**
     - Checks if `now() > expiresAt`. If valid, confirms with Bus and Hotel services, and transitions booking status to `CONFIRMED`.
     - When background expiry reconciliation wakes up and acquires the lock, it checks `if ("CONFIRMED".equalsIgnoreCase(lockedBooking.getStatus())) return false;` and exits cleanly without modifying the confirmed booking.
   - **Case B — Expiry Reconciliation Acquires Lock First:**
     - Verifies payment status with Payment Service. If unpaid, transitions booking status to `EXPIRED` and calls `releaseRoomsInHotelService(booking)` (which sets Hotel Service parent and child allocations to `RELEASED`).
     - When payment confirmation acquires the lock next, it checks `if ("EXPIRED".equalsIgnoreCase(bkg.getStatus())) return 400 Bad Request;`.
     - If payment was captured in Payment Service, it triggers compensation refund.
   - **Case C — Borderline Race at Gateway / Hotel Service:**
     - If payment verification passes but Hotel Service reservation deadline has just lapsed, Hotel Service rejects confirmation with 400 / Conflict.
     - Booking Service catches the failure, transitions the booking to `EXPIRED`, and invokes Payment Service to issue an automated refund.

---

### 7. Refund Persistence and Reconciliation Design

#### 7.1 Entity Model (`PaymentRefundTask`)
```
+-----------------------------------------------------------+
|                    payment_refund_tasks                   |
+-----------------------------------------------------------+
| id                  BIGINT PK AUTO_INCREMENT              |
| booking_ref         VARCHAR(255) NOT NULL                 |
| transaction_ref     VARCHAR(255) NOT NULL                 |
| razorpay_payment_id VARCHAR(255)                          |
| amount              DOUBLE NOT NULL                       |
| reason              VARCHAR(255)                          |
| idempotency_key     VARCHAR(255) UNIQUE NOT NULL          |
| status              VARCHAR(32) NOT NULL                  |
| razorpay_refund_id  VARCHAR(255)                          |
| created_at          TIMESTAMP NOT NULL                    |
| updated_at          TIMESTAMP                             |
| last_attempt_at     TIMESTAMP                             |
| next_retry_at       TIMESTAMP                             |
| attempt_count       INT NOT NULL DEFAULT 0                |
| max_attempts        INT NOT NULL DEFAULT 5                |
| last_error          VARCHAR(2000)                         |
| locked_by           VARCHAR(255)                          |
| locked_until        TIMESTAMP                             |
+-----------------------------------------------------------+
```

#### 7.2 Background Worker (`PaymentRefundReconciliationWorker`)
- Operates on a fixed delay (`15000ms`, configurable via `payment.refund.reconciliation.fixed-delay`).
- Uses atomic database leasing via `claimRefundTask`:
  ```sql
  UPDATE payment_refund_tasks
  SET status = 'PROCESSING', locked_by = :workerId, locked_until = :leaseExpiry,
      last_attempt_at = :now, attempt_count = attempt_count + 1
  WHERE id = :id AND (status IN ('PENDING', 'FAILED_RETRYABLE') OR (status = 'PROCESSING' AND locked_until < :now))
  ```
- Evaluates eligible tasks with exponential backoff (`15s * 2^(attempts-1)`).
- Reconciles state against Razorpay API before issuing any gateway requests.
- Upon successful execution or reconciliation, updates status to `PROCESSED` with `razorpayRefundId`.
- If maximum attempts (`5`) are exhausted, marks status as `FAILED_FINAL` for administrative review.

---

### 8. Refund Idempotency and Provider Safety Safeguards

To prevent duplicate refunds under network timeouts or concurrent triggers:

1. **Deterministic Idempotency Key:**
   - Both late payments and inventory conflict compensations use the deterministic key:
     `REFUND-COMP-<bookingRef>`
   - Any subsequent call (e.g. customer return URL verification + asynchronous webhook delivery) queries `findByIdempotencyKey` and resolves to the exact same task.

2. **Pre-Call Gateway Reconciliation (`fetchAllRefunds`):**
   - Before invoking `razorpayClient.payments.refund(...)`, the service calls `razorpayClient.payments.fetchAllRefunds(paymentId)`.
   - Inspects existing refunds on Razorpay's ledger. If a refund matching the target amount (in paise) is found:
     - Extracts the existing `refundId` and status (`processed` or `initiated`).
     - Updates the local transaction (`recordRefundCompleted` or `recordRefundInitiated`).
     - Marks the `PaymentRefundTask` as `PROCESSED`.
     - Exits without making an additional refund request.

3. **Post-Exception Gateway Reconciliation:**
   - If `razorpayClient.payments.refund(...)` throws an exception (e.g., HTTP read timeout after Razorpay already created the refund), the catch block queries `fetchAllRefunds(paymentId)`.
   - If the refund was registered by Razorpay despite the client-side timeout, the task is immediately reconciled to `PROCESSED` rather than scheduled for an erroneous second refund.

---

### 9. Webhook Processing Changes

1. **Durability First:**
   - In `PaymentServiceImpl.processAuthoritativePaymentSuccess`, when a webhook arrives for an expired booking or an inventory conflict occurs, `recordOrGetCompensationRefundTask` durably saves the `PaymentRefundTask` to MySQL *before* attempting immediate gateway communication.
   - If Razorpay is temporarily unavailable, the task remains safely recorded in `FAILED_RETRYABLE` state with `nextRetryAt`.
   - The webhook returns success knowing that background workers will retry compensation.

2. **Transient Error Handling:**
   - If Booking Service returns transient 5xx during confirmation, the exception is rethrown, allowing `PaymentWebhookRecoveryWorker` to re-execute the webhook event under its own lease lock.

---

### 10. Database Migration Instructions

- **Hotel Service Database (`traveleasy_hotel_db`):**
  - Under `spring.jpa.hibernate.ddl-auto=update`, Hibernate automatically detects the new field `expiresAt` and executes:
    ```sql
    ALTER TABLE hotel_reservations ADD COLUMN expires_at DATETIME NULL;
    ```
  - For operational safety and backward compatibility, `HotelReservationSchemaInitializer` explicitly verifies `INFORMATION_SCHEMA.COLUMNS` on startup and executes this `ALTER TABLE` if missing.
  - No existing data is corrupted or dropped. Historical reservations without an explicit `expires_at` have `NULL` (unrestricted).

- **Payment Service Database (`traveleasy_payment_db`):**
  - Under `spring.jpa.hibernate.ddl-auto=update`, Hibernate automatically provisions:
    ```sql
    CREATE TABLE payment_refund_tasks (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        booking_ref VARCHAR(255) NOT NULL,
        transaction_ref VARCHAR(255) NOT NULL,
        razorpay_payment_id VARCHAR(255) NULL,
        amount DOUBLE NOT NULL,
        reason VARCHAR(255) NULL,
        idempotency_key VARCHAR(255) NOT NULL UNIQUE,
        status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
        razorpay_refund_id VARCHAR(255) NULL,
        created_at TIMESTAMP NULL,
        updated_at TIMESTAMP NULL,
        last_attempt_at TIMESTAMP NULL,
        next_retry_at TIMESTAMP NULL,
        attempt_count INT NOT NULL DEFAULT 0,
        max_attempts INT NOT NULL DEFAULT 5,
        last_error VARCHAR(2000) NULL,
        locked_by VARCHAR(255) NULL,
        locked_until TIMESTAMP NULL,
        INDEX idx_refund_task_booking (booking_ref),
        INDEX idx_refund_task_txn (transaction_ref),
        INDEX idx_refund_task_status_retry (status, next_retry_at),
        INDEX idx_refund_task_lease (status, locked_until)
    );
    ```

---

### 11. Tests Executed and Results

All unit and integration tests were executed cleanly against the actual codebase using Maven Surefire:

#### 11.1 `hotel-service` Test Suite
- **Command:** `mvn -f hotel-service/pom.xml test`
- **Result:** **264 Tests Run, 0 Failures, 0 Errors, 0 Skipped — BUILD SUCCESS**
- **Targeted WP-HB-05 Tests in `HotelMultiAllocationInventoryTest`:**
  - `testSuppliedExpiresAt_PersistedAndReturnedInResponse` — Passed.
  - `testConfirmation_AfterExpiresAtDeadline_RejectedAndAutoReleasesHeldRooms` — Passed.
  - `testConfirmation_AfterExpiryRelease_CannotBeRevived` — Passed.
  - `testRelease_ExpiredReservation_IsIdempotent` — Passed.

#### 11.2 `booking-service` Test Suite
- **Command:** `mvn -f booking-service/pom.xml test`
- **Result:** **119 Tests Run, 0 Failures, 0 Errors, 0 Skipped — BUILD SUCCESS**
- **Targeted WP-HB-05 Tests in `HotelBookingOrchestrationTest` & `BookingExpiryReconciliationServiceTest`:**
  - `testInitiateHotelReservation_Success` (verifies `expiresAt` in payload) — Passed.
  - `testReconciliation_UnpaidBooking_ExpiresAndReleasesSeats` — Passed.
  - `testReconciliation_PaidBooking_Confirmed` — Passed.
  - `testReconciliation_LatePayment_SeatsConflict_RefundTriggered` — Passed.
  - `testReconciliation_PaidDateWiseHotel_HoldExpired_TriggersRefundAndExpires` — Passed.
  - `testExpireBookingByRef_AlreadyConfirmed_ReturnsFalse` — Passed.

#### 11.3 `payment-service` Test Suite
- **Command:** `mvn -f payment-service/pom.xml test`
- **Result:** **109 Tests Run, 0 Failures, 0 Errors, 0 Skipped — BUILD SUCCESS**
- **Targeted WP-HB-05 Tests in `PaymentLifecycleIntegrationTest` & `PaymentRefundReconciliationWorkerTest`:**
  - `testLatePayment_CreatesDurableCompensationTask` — Passed.
  - `testInventoryConflict_CreatesDurableCompensationTask` — Passed.
  - `testTransientRazorpayFailure_LeavesTaskRetryable` — Passed.
  - `testReconciliationWorker_ReconcilesExistingRefundWithoutDuplicateCharge` — Passed.
  - `testDuplicateCheckoutAndWebhook_ReuseSameDurableRefundTask` — Passed.
  - `testReconciliationWorker_ExhaustsMaxAttempts_MarksFailedFinal` — Passed.
  - `testReconciliationDisabled_NoActions` — Passed.
  - `testReconciliation_ClaimsAndExecutesTasks` — Passed.
  - `testReconciliation_IsolatesTaskErrors` — Passed.

---

### 12. Build Results for Affected Services

| Microservice | Build Tool | Compilation | Test Execution | Packaging Status | Total Tests |
|---|---|---|---|---|---|
| `hotel-service` | Apache Maven 3.x / Java 21 | SUCCESS | SUCCESS (0 failures, 0 errors) | BUILD SUCCESS | 264 |
| `booking-service` | Apache Maven 3.x / Java 21 | SUCCESS | SUCCESS (0 failures, 0 errors) | BUILD SUCCESS | 119 |
| `payment-service` | Apache Maven 3.x / Java 21 | SUCCESS | SUCCESS (0 failures, 0 errors) | BUILD SUCCESS | 109 |
| **Platform Total** | — | **SUCCESS** | **SUCCESS (0 failures, 0 errors)** | **BUILD SUCCESS** | **492** |

---

### 13. Regression Verification

- **Bus Booking Flow:** Unaffected. Bus schedule queries, seat locks, payment verification, and seat confirmation verified across all 119 `booking-service` tests.
- **Hotel Manager Portal:** Unaffected. Property management, room blocks, daily pricing overrides, base rates, and inventory calendars verified across all 264 `hotel-service` tests.
- **Legacy Single-Room Reservations:** Unaffected. Legacy `/api/hotels/rooms/reserve-stay` and backward-compatible single allocation delegates verified in both `HotelMultiAllocationInventoryTest` and `HotelBookingFlowAndLegacyFallbackVerificationTest`.
- **Payment Outbox & Webhook Inbox:** Unaffected. Transaction outbox publisher and webhook recovery workers verified across all 109 `payment-service` tests.

---

### 14. Remaining Limitations and Unresolved Concerns

1. **Real Razorpay Gateway Credentials:**
   - In automated test environments, external Razorpay API endpoints are mocked using Mockito. Verification against live Razorpay sandbox endpoints requires real API keys configured via environment variables (`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`).
2. **Clock Drift Between Hosts:**
   - In multi-node production deployments across distributed servers, NTP synchronization is recommended so that `LocalDateTime.now()` comparisons between Booking Service, Hotel Service, and Payment Service do not diverge by more than a few seconds.

---

### 15. Manual Verification Instructions

To manually verify the targeted corrections end-to-end:

1. **Start Services:**
   ```bash
   mvn -f hotel-service/pom.xml spring-boot:run
   mvn -f booking-service/pom.xml spring-boot:run
   mvn -f payment-service/pom.xml spring-boot:run
   ```

2. **Verify Expiry Synchronization:**
   - Initiate a hotel booking:
     ```bash
     curl -X POST http://localhost:8084/api/bookings/hotel/initiate \
       -H "Content-Type: application/json" \
       -d '{"hotelId": 1, "checkInDate": "2026-12-01", "checkOutDate": "2026-12-03", "allocations": [{"roomId": 1, "quantity": 1}]}'
     ```
   - Note the returned `bookingRef` and `expiresAt`.
   - Query Hotel Service directly:
     ```bash
     curl http://localhost:8083/api/hotels/reservations/{bookingRef} -H "X-Internal-Token: ..."
     ```
   - Confirm that the `expiresAt` in Hotel Service matches the exact timestamp returned by Booking Service.

3. **Verify Late Confirmation Rejection:**
   - Wait 15 minutes (or simulate expiry by fast-forwarding the database timestamp on `hotel_reservations.expires_at`).
   - Attempt to call confirm:
     ```bash
     curl -X PUT "http://localhost:8083/api/hotels/rooms/confirm-stay?bookingRef={bookingRef}" -H "X-Internal-Token: ..."
     ```
   - Verify HTTP `400 Bad Request` with message indicating deadline has expired.
   - Inspect database: verify `hotel_reservations.status = 'RELEASED'` and daily inventory `held_rooms` decremented.

4. **Verify Durable Compensation Refund:**
   - Simulate a late payment capture in Payment Service for an expired booking.
   - Query `payment_refund_tasks` table in MySQL:
     ```sql
     SELECT id, booking_ref, transaction_ref, amount, idempotency_key, status, razorpay_refund_id, attempt_count FROM payment_refund_tasks;
     ```
   - Verify task is created with `idempotency_key = 'REFUND-COMP-{bookingRef}'` and status `PROCESSED` (or `FAILED_RETRYABLE` if gateway simulated down).

---

### 16. Final Acceptance Checklist

#### Expiry Synchronization
- [x] Booking Service remains the sole expiry authority.
- [x] Original `expiresAt` is passed to Hotel Service.
- [x] Hotel Service persists and respects that exact deadline.
- [x] Hotel Service does not extend the deadline independently.
- [x] Confirmation after expiry is rejected.
- [x] Released reservations cannot be revived.
- [x] Expiry reconciliation and confirmation are concurrency-safe.
- [x] Aggregate release remains idempotent.

#### Refund Recovery
- [x] Late successful payments create durable compensation work.
- [x] Definitive inventory confirmation failures trigger compensation.
- [x] Pending refunds survive application restarts.
- [x] Transient Razorpay failures are retryable.
- [x] Duplicate events and retries cannot create duplicate refunds.
- [x] Uncertain provider outcomes are reconciled before another refund is initiated.
- [x] Webhook processing does not lose outstanding compensation.
- [x] Refund completion is recorded only after authoritative provider confirmation.
- [x] Unresolved refunds remain traceable and diagnosable.
