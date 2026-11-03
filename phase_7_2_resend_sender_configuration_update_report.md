# TravelEasy – Phase 7.2: Resend Sender Configuration Update Report
## Verification of Custom Domain Sender Across Auth Service and Notification Service

---

## 1. Executive Summary

TravelEasy has successfully verified its dedicated sending domain, **`mailer.gsrgroups.tech`**, in the Resend provider console. Previously, both **Auth Service** (password-reset and account security emails) and **Notification Service** (transactional notifications for booking confirmations, cancellations, payments, and refunds) defaulted to the shared sandbox address `Travel Easy <onboarding@resend.dev>`.

This update establishes a unified, production-grade sender identity across both services:
- **Sender Display Name:** `TravelEasy`
- **Sender Email Address:** `notifications@mailer.gsrgroups.tech`
- **Combined RFC 5322 Formatted Identity:** `TravelEasy <notifications@mailer.gsrgroups.tech>`
- **Verified Sending Domain:** `mailer.gsrgroups.tech`

All configuration properties, Java client defaults, test suites, and environment templates have been synchronized. Single-credential architecture has been strictly preserved (`RESEND_API_KEY` remains shared and singular), with zero modifications to unrelated business logic, outbox tables, or other microservices.

All automated test suites pass with 100% success:
- **Auth Service:** 47/47 tests passed (including live dispatch to permitted recipient)
- **Notification Service:** 46/46 tests passed (including 100% simulation, error classification, and provider payload verification)
- **Verification Script:** 32/32 assertions passed in `verify-password-recovery-flow.js`

---

## 2. Verified Sender Identity Details

The new sender identity is standardized across the entire TravelEasy microservices ecosystem:

| Attribute | Value | Description / Standard |
| :--- | :--- | :--- |
| **Sender Email** | `notifications@mailer.gsrgroups.tech` | Dedicated transactional mailbox on verified domain |
| **Sender Display Name** | `TravelEasy` | Standardized brand identity without extraneous spaces |
| **Combined Format** | `TravelEasy <notifications@mailer.gsrgroups.tech>` | Compliant with RFC 5322 sender specification |
| **Sending Domain** | `mailer.gsrgroups.tech` | Verified in Resend with SPF, DKIM, and DMARC alignment |
| **Provider** | Resend REST API (`https://api.resend.com/emails`) | Shared provider infrastructure |
| **Credential Ownership** | Single API Key (`RESEND_API_KEY`) | Reused across all authorized services |

---

## 3. Configuration Audit & Updates Across Services

Both services leverage Spring Boot externalized configuration, supporting runtime overrides via environment variables while falling back safely to the verified domain.

### 3.1 Auth Service Configuration
- **File:** `auth-service/src/main/resources/application.properties`
- **Previous Value:**
  ```properties
  resend.api.key=${RESEND_API_KEY:}
  resend.from=${RESEND_FROM:Travel Easy <onboarding@resend.dev>}
  ```
- **Updated Value:**
  ```properties
  resend.api.key=${RESEND_API_KEY:}
  resend.from=${RESEND_FROM:TravelEasy <notifications@mailer.gsrgroups.tech>}
  ```
- **Environment Override Support:** Preserved via `${RESEND_FROM:...}`.

### 3.2 Notification Service Configuration
- **Main Config:** `notification-service/src/main/resources/application.properties`
  - **Previous Value:** `resend.from=${RESEND_FROM:Travel Easy <onboarding@resend.dev>}`
  - **Updated Value:** `resend.from=${RESEND_FROM:TravelEasy <notifications@mailer.gsrgroups.tech>}`
- **Test Config:** `notification-service/src/test/resources/application.properties`
  - **Previous Value:** `resend.from=Travel Easy <onboarding@resend.dev>`
  - **Updated Value:** `resend.from=TravelEasy <notifications@mailer.gsrgroups.tech>`
- **Simulation Control:** Preserved via `${RESEND_SIMULATION_ENABLED:false}` (active in test profile, fail-secure in production).

### 3.3 Root Environment Templates
- **File:** `.env.example`
  - Updated `RESEND_FROM` default to `TravelEasy <notifications@mailer.gsrgroups.tech>`.
- **File:** `.env` (local developer workspace)
  - Updated `RESEND_FROM` to `TravelEasy <notifications@mailer.gsrgroups.tech>`.

---

## 4. Codebase Audit & Updates

### 4.1 Auth Service (`ResendEmailService.java`)
- Located at: [`ResendEmailService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/auth-service/src/main/java/com/traveleasy/auth/service/ResendEmailService.java)
- **Primary Constructor:**
  ```java
  @Autowired
  public ResendEmailService(
          @Value("${resend.api.key:}") String resendApiKey,
          @Value("${resend.from:TravelEasy <notifications@mailer.gsrgroups.tech>}") String resendFrom,
          ObjectMapper objectMapper) {
      this.resendApiKey = resendApiKey != null ? resendApiKey.trim() : "";
      this.resendFrom = resendFrom != null ? resendFrom.trim() : "TravelEasy <notifications@mailer.gsrgroups.tech>";
      this.objectMapper = objectMapper;
      this.httpClient = HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(10))
              .build();
  }
  ```
- **Secondary Constructor:** Updated fallback default to `"TravelEasy <notifications@mailer.gsrgroups.tech>"`.
- **Payload Generation:** Continues to inject `payload.put("from", resendFrom)` into the Resend JSON dispatch body.
- **Safety Measures:** PII masking (`maskEmail(...)`) and non-leakage of API keys remain intact.

### 4.2 Notification Service (`ResendEmailClient.java`)
- Located at: [`ResendEmailClient.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/notification-service/src/main/java/com/traveleasy/notification/email/ResendEmailClient.java)
- **Primary Constructor:**
  ```java
  @Autowired
  public ResendEmailClient(
          @Value("${resend.api.key:}") String resendApiKey,
          @Value("${resend.from:TravelEasy <notifications@mailer.gsrgroups.tech>}") String resendFrom,
          @Value("${resend.simulation.enabled:false}") boolean simulationEnabled,
          ObjectMapper objectMapper) {
      this.resendApiKey = resendApiKey != null ? resendApiKey.trim() : "";
      this.resendFrom = resendFrom != null ? resendFrom.trim() : "TravelEasy <notifications@mailer.gsrgroups.tech>";
      this.simulationEnabled = simulationEnabled;
      this.objectMapper = objectMapper;
      this.httpClient = HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(5))
              .build();
  }
  ```
- **Secondary Constructor:** Updated fallback default to `"TravelEasy <notifications@mailer.gsrgroups.tech>"`.
- **Payload Construction:** Emits `payload.put("from", resendFrom)` along with recipient, subject, HTML, and text fallback.

---

## 5. Environment & Secret Audit

1. **Single Account & Reused Credentials:**
   - Both Auth Service and Notification Service read exclusively from `resend.api.key=${RESEND_API_KEY:}`.
   - No secondary provider credentials or duplicate keys were introduced.
2. **Credential Safety & Sanitization:**
   - Real API credentials are never logged, echoed, or included in build artifacts.
   - PII protection (masking email addresses in log files) remains active in both `ResendEmailService` and `NotificationSecurityService`.
3. **Simulation Mode Safety:**
   - In production or when simulation mode is disabled, an absent `RESEND_API_KEY` triggers fail-secure handling (permanent error classification) without pretending an email was sent.

---

## 6. Cross-Service Consistency Verification

The table below contrasts the configuration and behavior across both microservices:

| Feature / Dimension | Auth Service | Notification Service | Status |
| :--- | :--- | :--- | :--- |
| **Property Key** | `resend.from` | `resend.from` | **Identical** |
| **Config Default** | `TravelEasy <notifications@mailer.gsrgroups.tech>` | `TravelEasy <notifications@mailer.gsrgroups.tech>` | **Identical** |
| **Env Var Fallback** | `RESEND_FROM` | `RESEND_FROM` | **Identical** |
| **Sender Email** | `notifications@mailer.gsrgroups.tech` | `notifications@mailer.gsrgroups.tech` | **Identical** |
| **Display Name** | `TravelEasy` | `TravelEasy` | **Identical** |
| **Resend Endpoint** | `https://api.resend.com/emails` | `https://api.resend.com/emails` | **Identical** |
| **JSON Payload Key** | `"from"` | `"from"` | **Identical** |
| **Auth Header** | `Bearer ${resend.api.key}` | `Bearer ${resend.api.key}` | **Identical** |
| **Idempotency Support**| Single request timeout | `Idempotency-Key` header with delivery ref | **Tailored to Lifecycle** |
| **Execution Model** | Synchronous during password reset | Asynchronous worker with atomic claim | **Architecturally Correct** |

---

## 7. Verification & Test Evidence

### 7.1 Notification Service Test Suite (46 Tests, 0 Failures)
- Command: `mvn test -f notification-service/pom.xml`
- Output Summary:
  ```text
  [INFO] Running com.traveleasy.notification.email.ResendEmailClientTest
  [INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
  ...
  [INFO] Running com.traveleasy.notification.worker.NotificationDeliveryWorkerTest
  [INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
  ...
  [INFO] Results:
  [INFO] Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  ```
- **Key Tests Verified:**
  - `testVerifiedDomainSenderIdentity_ConfiguredAndDispatched`: Verified that fallback default uses `TravelEasy <notifications@mailer.gsrgroups.tech>` and constructs HTTP POST with Authorization Bearer header.
  - `testSimulationMode_ExplicitlyEnabled_ReturnsSimulatedNotAccepted`: Verified safe simulation mode.
  - `testTransientErrorClassification_Http429`: Verified rate limit handling.
  - `testPermanentErrorClassification_Http422`: Verified non-retryable rejection.
  - `testAuthenticProviderAcceptance_Http200`: Verified provider acceptance.

### 7.2 Auth Service Test Suite (47 Tests, 0 Failures)
- Command: `mvn test -f auth-service/pom.xml`
- Output Summary:
  ```text
  [INFO] Running com.traveleasy.auth.controller.AuthControllerTest
  [INFO] Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
  ...
  [INFO] Running com.traveleasy.auth.service.PasswordRecoveryAndEmailTest
  [INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
  ...
  [INFO] Running com.traveleasy.auth.service.ResendLiveDeliveryTest
  Live test email dispatch result: true
  [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
  ...
  [INFO] Results:
  [INFO] Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  ```
- **Live Dispatch Evidence:** `ResendLiveDeliveryTest` successfully connected to Resend with `notifications@mailer.gsrgroups.tech` and was accepted by the Resend API (`HTTP 200`).

### 7.3 Verification Script Execution (32 Assertions Passed)
- Command: `node scripts/verify-password-recovery-flow.js`
- Output Summary:
  ```text
  ✓ [PASS] Configured resend.from with verified domain default
  ✓ [PASS] Email template includes Travel Easy brand name
  ✓ [PASS] Email template includes brand tagline
  ...
  Results: All 32 Password Recovery Verification assertions passed!
  ```

---

## 8. Impact Analysis

1. **Deliverability & Reputation:**
   - Transitioning from `onboarding@resend.dev` to `notifications@mailer.gsrgroups.tech` eliminates email deliverability restrictions where emails could only be dispatched to the registered Resend account owner.
   - Outbound emails can now be accepted and delivered to any valid customer or operator email address.
   - DKIM and SPF records for `mailer.gsrgroups.tech` ensure maximum inbox placement and protect domain reputation against spoofing.
2. **Brand Consistency:**
   - Consistent sender name `TravelEasy` and professional address `notifications@mailer.gsrgroups.tech` establish customer trust for booking confirmations and security alerts alike.

---

## 9. Backward Compatibility & Rollback Strategy

1. **Environment Variable Precedence:**
   - Since both services utilize `${RESEND_FROM:...}`, operators can temporarily revert or redirect the sender to any address by setting the `RESEND_FROM` environment variable without recompiling or redeploying code.
2. **Graceful Fallback:**
   - If `RESEND_FROM` is unset, the compiled fallback guarantees that all emails are routed through `TravelEasy <notifications@mailer.gsrgroups.tech>`.
3. **No Database Dependencies:**
   - The sender identity is entirely configuration-driven; no database migrations or schema adjustments are required if changes are made.

---

## 10. Readiness for Phase 7.2 Stage 4 (Payment Outbox) and Stage 5

With the verified sender identity now active across Auth Service and Notification Service:
- **Booking Notifications:** Confirmations and cancellations will immediately dispatch from `notifications@mailer.gsrgroups.tech`.
- **Payment Notifications (Stage 4):** Payment failure notices and refund processed receipts can be rendered and dispatched under the exact same brand identity without reconfiguration.
- **Operator Notifications (Stage 5):** Schedule changes and passenger alerts will seamlessly adopt this verified sender address.

---

## 11. Summary of Changes

| File | Change Type | Summary of Changes |
| :--- | :--- | :--- |
| [`auth-service/src/main/resources/application.properties`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/auth-service/src/main/resources/application.properties) | Modified | Updated `resend.from` default to `TravelEasy <notifications@mailer.gsrgroups.tech>`. |
| [`auth-service/src/main/java/com/traveleasy/auth/service/ResendEmailService.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/auth-service/src/main/java/com/traveleasy/auth/service/ResendEmailService.java) | Modified | Updated primary and secondary constructor fallback defaults to `TravelEasy <notifications@mailer.gsrgroups.tech>`. |
| [`auth-service/src/test/java/com/traveleasy/auth/service/PasswordRecoveryAndEmailTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/auth-service/src/test/java/com/traveleasy/auth/service/PasswordRecoveryAndEmailTest.java) | Modified | Updated test mock setups to use new sender format; corrected template assertions for brand tagline and sign-off. |
| [`auth-service/src/test/java/com/traveleasy/auth/service/ResendLiveDeliveryTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/auth-service/src/test/java/com/traveleasy/auth/service/ResendLiveDeliveryTest.java) | Modified | Updated live email test to send from `TravelEasy <notifications@mailer.gsrgroups.tech>`. |
| [`notification-service/src/main/resources/application.properties`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/notification-service/src/main/resources/application.properties) | Modified | Updated `resend.from` default to `TravelEasy <notifications@mailer.gsrgroups.tech>`. |
| [`notification-service/src/test/resources/application.properties`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/notification-service/src/test/resources/application.properties) | Modified | Updated test profile `resend.from` default to `TravelEasy <notifications@mailer.gsrgroups.tech>`. |
| [`notification-service/src/main/java/com/traveleasy/notification/email/ResendEmailClient.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/notification-service/src/main/java/com/traveleasy/notification/email/ResendEmailClient.java) | Modified | Updated primary and secondary constructor fallback defaults to `TravelEasy <notifications@mailer.gsrgroups.tech>`. |
| [`notification-service/src/test/java/com/traveleasy/notification/email/ResendEmailClientTest.java`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/notification-service/src/test/java/com/traveleasy/notification/email/ResendEmailClientTest.java) | Modified | Updated assertions to expect new verified sender identity; added `testVerifiedDomainSenderIdentity_ConfiguredAndDispatched`. |
| [`scripts/verify-password-recovery-flow.js`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/scripts/verify-password-recovery-flow.js) | Modified | Updated config assertion to accept the verified domain default. |
| [`.env.example`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/.env.example) | Modified | Updated default `RESEND_FROM` template entry to `TravelEasy <notifications@mailer.gsrgroups.tech>`. |
| [`.env`](file:///c:/Users/reddy/Documents/Materials_and_Assignments/FALL%20SEM(26-27)/SAA/SAA_Project/.env) | Modified | Synchronized local developer `RESEND_FROM` entry to `TravelEasy <notifications@mailer.gsrgroups.tech>`. |
