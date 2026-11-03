package com.traveleasy.notification.worker;

import com.traveleasy.notification.email.EmailDispatchResult;
import com.traveleasy.notification.email.ResendEmailClient;
import com.traveleasy.notification.model.InAppNotification;
import com.traveleasy.notification.model.NotificationDelivery;
import com.traveleasy.notification.model.RecipientRole;
import com.traveleasy.notification.repository.InAppNotificationRepository;
import com.traveleasy.notification.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
public class NotificationDeliveryWorkerTest {

    @Autowired
    private NotificationDeliveryWorker deliveryWorker;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    private InAppNotificationRepository inAppRepository;

    @MockBean
    private ResendEmailClient resendEmailClient;

    @BeforeEach
    void setUp() {
        deliveryRepository.deleteAll();
        inAppRepository.deleteAll();
    }

    @Test
    void testSuccessfulDeliveryAcceptedByProvider_LeavesInAppUntouched() {
        String eventId = "EVT-" + UUID.randomUUID();
        String deliveryRef = "DEL-" + UUID.randomUUID();

        // 1. Create In-App notification
        InAppNotification inApp = inAppRepository.save(InAppNotification.builder()
                .eventId(eventId)
                .recipientUserId(111L)
                .recipientEmail("traveller111@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .eventType("BUS_BOOKING_CONFIRMED")
                .title("Ticket Confirmed")
                .message("Seats L1, L2")
                .isRead(false)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build());

        // 2. Create pending delivery
        NotificationDelivery delivery = deliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef(deliveryRef)
                .eventId(eventId)
                .channel("EMAIL")
                .recipientEmail("traveller111@traveleasy.com")
                .recipientUserId(111L)
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Ticket Confirmed")
                .templateName("bus-booking-confirmation")
                .templateDataJson("{\"origin\":\"Kadapa\",\"destination\":\"Vijayawada\"}")
                .status("PENDING")
                .provider("RESEND")
                .retryCount(0)
                .maxRetries(4)
                .createdAt(Instant.now())
                .build());

        // Mock Resend Client returning accepted
        when(resendEmailClient.sendEmail(anyString(), anyString(), anyString(), anyString(), eq(deliveryRef)))
                .thenReturn(EmailDispatchResult.builder()
                        .accepted(true)
                        .providerMessageId("re_test_success_123")
                        .statusCode(200)
                        .build());

        // Run worker
        deliveryWorker.processPendingDeliveries();

        // Verify delivery record updated to ACCEPTED with acceptedAt set and deliveredAt NULL
        NotificationDelivery updatedDelivery = deliveryRepository.findByDeliveryRef(deliveryRef).orElseThrow();
        assertEquals("ACCEPTED", updatedDelivery.getStatus());
        assertEquals("re_test_success_123", updatedDelivery.getProviderMessageId());
        assertNotNull(updatedDelivery.getAcceptedAt(), "acceptedAt must be recorded on provider acceptance");
        assertNull(updatedDelivery.getDeliveredAt(), "deliveredAt must remain null until webhook confirmation");
        assertNull(updatedDelivery.getLockedBy());

        // CRUCIAL: Verify In-App notification is completely unaffected and independent
        InAppNotification untouchedInApp = inAppRepository.findById(inApp.getId()).orElseThrow();
        assertFalse(untouchedInApp.isRead(), "In-app notification must remain intact and independent of email worker");
        assertEquals("Ticket Confirmed", untouchedInApp.getTitle());
    }

    @Test
    void testSimulatedDelivery_MarkedAsSimulatedNotAccepted() {
        String deliveryRef = "DEL-SIM-" + UUID.randomUUID();

        NotificationDelivery delivery = deliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef(deliveryRef)
                .eventId("EVT-SIM-1")
                .channel("EMAIL")
                .recipientEmail("sim@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Simulated Note")
                .templateName("default")
                .templateDataJson("{}")
                .status("PENDING")
                .retryCount(0)
                .maxRetries(4)
                .createdAt(Instant.now())
                .build());

        when(resendEmailClient.sendEmail(anyString(), anyString(), anyString(), anyString(), eq(deliveryRef)))
                .thenReturn(EmailDispatchResult.builder()
                        .accepted(true)
                        .simulated(true)
                        .providerMessageId("simulated_" + deliveryRef)
                        .statusCode(200)
                        .build());

        deliveryWorker.processPendingDeliveries();

        NotificationDelivery updated = deliveryRepository.findByDeliveryRef(deliveryRef).orElseThrow();
        assertEquals("SIMULATED", updated.getStatus(), "Simulated dispatches must be marked SIMULATED, never ACCEPTED");
        assertEquals("simulated_" + deliveryRef, updated.getProviderMessageId());
        assertNotNull(updated.getAcceptedAt());
        assertNull(updated.getDeliveredAt());
    }

    @Test
    void testPermanentFailure_ImmediatelyMarksFailedWithoutRetry() {
        String deliveryRef = "DEL-PERM-FAIL-" + UUID.randomUUID();

        NotificationDelivery delivery = deliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef(deliveryRef)
                .eventId("EVT-PERM-1")
                .channel("EMAIL")
                .recipientEmail("invalid_domain@nonexistent.tld")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Permanent Failure Test")
                .templateName("default")
                .templateDataJson("{}")
                .status("PENDING")
                .retryCount(0)
                .maxRetries(4)
                .createdAt(Instant.now())
                .build());

        // Permanent rejection (HTTP 422 Unprocessable Entity - unverified domain)
        when(resendEmailClient.sendEmail(anyString(), anyString(), anyString(), anyString(), eq(deliveryRef)))
                .thenReturn(EmailDispatchResult.builder()
                        .accepted(false)
                        .simulated(false)
                        .transientFailure(false)
                        .statusCode(422)
                        .errorMessage("Domain not verified")
                        .build());

        deliveryWorker.processPendingDeliveries();

        NotificationDelivery updated = deliveryRepository.findByDeliveryRef(deliveryRef).orElseThrow();
        assertEquals("FAILED", updated.getStatus(), "Permanent failure must transition directly to FAILED");
        assertEquals(1, updated.getRetryCount());
        assertNull(updated.getNextRetryAt(), "No retry should be scheduled for permanent failure");
        assertTrue(updated.getLastError().contains("Domain not verified"));
        assertNull(updated.getLockedBy());
    }

    @Test
    void testTransientFailure_ReachesMaxRetries_TransitionsToFailed() {
        String deliveryRef = "DEL-CEILING-" + UUID.randomUUID();

        NotificationDelivery delivery = deliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef(deliveryRef)
                .eventId("EVT-CEILING-1")
                .channel("EMAIL")
                .recipientEmail("retrymax@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Retry Ceiling Test")
                .templateName("default")
                .templateDataJson("{}")
                .status("PENDING")
                .retryCount(3) // on 4th attempt, ceiling is reached
                .maxRetries(4)
                .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build());

        when(resendEmailClient.sendEmail(anyString(), anyString(), anyString(), anyString(), eq(deliveryRef)))
                .thenReturn(EmailDispatchResult.builder()
                        .accepted(false)
                        .simulated(false)
                        .transientFailure(true)
                        .statusCode(503)
                        .errorMessage("Service Unavailable")
                        .build());

        deliveryWorker.processPendingDeliveries();

        NotificationDelivery updated = deliveryRepository.findByDeliveryRef(deliveryRef).orElseThrow();
        assertEquals("FAILED", updated.getStatus(), "When retry count reaches maxRetries, status must be FAILED");
        assertEquals(4, updated.getRetryCount());
        assertNull(updated.getNextRetryAt(), "Next retry must be null on terminal failure");
        assertNull(updated.getLockedBy());
    }

    @Test
    void testDeliveryFailure_ExponentialBackoffAndRetries() {
        String deliveryRef = "DEL-FAIL-" + UUID.randomUUID();

        NotificationDelivery delivery = deliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef(deliveryRef)
                .eventId("EVT-FAIL-1")
                .channel("EMAIL")
                .recipientEmail("fail@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Failing Email")
                .templateName("default")
                .templateDataJson("{}")
                .status("PENDING")
                .retryCount(0)
                .maxRetries(4)
                .createdAt(Instant.now())
                .build());

        // Mock Resend returning error (e.g. rate limit 429)
        when(resendEmailClient.sendEmail(anyString(), anyString(), anyString(), anyString(), eq(deliveryRef)))
                .thenReturn(EmailDispatchResult.builder()
                        .accepted(false)
                        .statusCode(429)
                        .errorMessage("Rate limit exceeded")
                        .build());

        deliveryWorker.processPendingDeliveries();

        NotificationDelivery afterAttempt1 = deliveryRepository.findByDeliveryRef(deliveryRef).orElseThrow();
        assertEquals("PENDING", afterAttempt1.getStatus(), "Status should remain PENDING for retry");
        assertEquals(1, afterAttempt1.getRetryCount());
        assertNotNull(afterAttempt1.getNextRetryAt(), "Next retry timestamp must be scheduled in the future");
        assertTrue(afterAttempt1.getNextRetryAt().isAfter(Instant.now()));
        assertNull(afterAttempt1.getLockedBy());
    }

    @Test
    void testStaleLeaseRecovery_WorkerReclaimsAndProcesses() {
        String deliveryRef = "DEL-STALE-" + UUID.randomUUID();

        // Stale record: in-flight for 6 minutes (lease expired)
        NotificationDelivery staleDelivery = deliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef(deliveryRef)
                .eventId("EVT-STALE")
                .channel("EMAIL")
                .recipientEmail("stale@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Stale Lease Item")
                .templateName("default")
                .templateDataJson("{}")
                .status("IN_FLIGHT")
                .lockedBy("dead-worker-uuid")
                .lastAttemptAt(Instant.now().minus(6, ChronoUnit.MINUTES))
                .retryCount(0)
                .maxRetries(4)
                .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build());

        when(resendEmailClient.sendEmail(anyString(), anyString(), anyString(), anyString(), eq(deliveryRef)))
                .thenReturn(EmailDispatchResult.builder()
                        .accepted(true)
                        .providerMessageId("re_stale_recovered_999")
                        .statusCode(200)
                        .build());

        deliveryWorker.processPendingDeliveries();

        NotificationDelivery recovered = deliveryRepository.findByDeliveryRef(deliveryRef).orElseThrow();
        assertEquals("ACCEPTED", recovered.getStatus());
        assertEquals("re_stale_recovered_999", recovered.getProviderMessageId());
        assertNull(recovered.getLockedBy());
    }
}
