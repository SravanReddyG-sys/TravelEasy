package com.traveleasy.notification.worker;

import com.traveleasy.notification.model.InAppNotification;
import com.traveleasy.notification.model.NotificationDelivery;
import com.traveleasy.notification.model.ReceivedEvent;
import com.traveleasy.notification.model.RecipientRole;
import com.traveleasy.notification.repository.InAppNotificationRepository;
import com.traveleasy.notification.repository.NotificationDeliveryRepository;
import com.traveleasy.notification.repository.ReceivedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RetentionPurgeWorkerTest {

    @Autowired
    private RetentionPurgeWorker retentionPurgeWorker;

    @Autowired
    private InAppNotificationRepository inAppNotificationRepository;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Autowired
    private ReceivedEventRepository receivedEventRepository;

    @Test
    void testTieredRetentionPurge_RemovesExpiredAndPreservesFresh() {
        Instant now = Instant.now();

        // 1. In-App: Expired (> 30 days old) vs Active
        InAppNotification expiredInApp = inAppNotificationRepository.save(InAppNotification.builder()
                .eventId("EVT-PURGE-INAPP-OLD")
                .recipientUserId(901L)
                .recipientEmail("user901_exp@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .eventType("BUS_BOOKING_CONFIRMED")
                .title("Old Expired In-App")
                .message("Expired test")
                .createdAt(now.minus(35, ChronoUnit.DAYS))
                .expiresAt(now.minus(5, ChronoUnit.DAYS)) // expired 5 days ago
                .build());

        InAppNotification activeInApp = inAppNotificationRepository.save(InAppNotification.builder()
                .eventId("EVT-PURGE-INAPP-NEW")
                .recipientUserId(901L)
                .recipientEmail("user901_act@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .eventType("BUS_BOOKING_CONFIRMED")
                .title("Fresh In-App")
                .message("Active test")
                .createdAt(now.minus(2, ChronoUnit.DAYS))
                .expiresAt(now.plus(28, ChronoUnit.DAYS)) // expires in 28 days
                .build());

        // 2. Email Deliveries (ACCEPTED & SIMULATED): Older than 60 days vs Recent (acceptedAt populated, deliveredAt NULL)
        NotificationDelivery expiredAcceptedEmail = notificationDeliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef("DEL-PURGE-ACC-OLD-" + UUID.randomUUID())
                .eventId("EVT-PURGE-EMAIL-ACC-OLD")
                .channel("EMAIL")
                .recipientEmail("old_acc@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Old Accepted")
                .templateName("default")
                .templateDataJson("{}")
                .status("ACCEPTED")
                .createdAt(now.minus(65, ChronoUnit.DAYS))
                .acceptedAt(now.minus(62, ChronoUnit.DAYS))
                .deliveredAt(null) // delivered_at is null as webhook is not yet received
                .build());

        NotificationDelivery freshAcceptedEmail = notificationDeliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef("DEL-PURGE-ACC-NEW-" + UUID.randomUUID())
                .eventId("EVT-PURGE-EMAIL-ACC-NEW")
                .channel("EMAIL")
                .recipientEmail("new_acc@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Fresh Accepted")
                .templateName("default")
                .templateDataJson("{}")
                .status("ACCEPTED")
                .createdAt(now.minus(10, ChronoUnit.DAYS))
                .acceptedAt(now.minus(10, ChronoUnit.DAYS))
                .deliveredAt(null)
                .build());

        NotificationDelivery expiredSimulatedEmail = notificationDeliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef("DEL-PURGE-SIM-OLD-" + UUID.randomUUID())
                .eventId("EVT-PURGE-EMAIL-SIM-OLD")
                .channel("EMAIL")
                .recipientEmail("old_sim@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Old Simulated")
                .templateName("default")
                .templateDataJson("{}")
                .status("SIMULATED")
                .createdAt(now.minus(65, ChronoUnit.DAYS))
                .acceptedAt(now.minus(62, ChronoUnit.DAYS))
                .deliveredAt(null)
                .build());

        NotificationDelivery freshSimulatedEmail = notificationDeliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef("DEL-PURGE-SIM-NEW-" + UUID.randomUUID())
                .eventId("EVT-PURGE-EMAIL-SIM-NEW")
                .channel("EMAIL")
                .recipientEmail("new_sim@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Fresh Simulated")
                .templateName("default")
                .templateDataJson("{}")
                .status("SIMULATED")
                .createdAt(now.minus(5, ChronoUnit.DAYS))
                .acceptedAt(now.minus(5, ChronoUnit.DAYS))
                .deliveredAt(null)
                .build());

        // 3. Email Deliveries (FAILED): Older than 180 days vs Recent
        NotificationDelivery expiredFailedEmail = notificationDeliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef("DEL-PURGE-FAIL-OLD-" + UUID.randomUUID())
                .eventId("EVT-PURGE-EMAIL-FAIL-OLD")
                .channel("EMAIL")
                .recipientEmail("old_fail@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Old Failed")
                .templateName("default")
                .templateDataJson("{}")
                .status("FAILED")
                .createdAt(now.minus(190, ChronoUnit.DAYS))
                .build());

        NotificationDelivery freshFailedEmail = notificationDeliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef("DEL-PURGE-FAIL-NEW-" + UUID.randomUUID())
                .eventId("EVT-PURGE-EMAIL-FAIL-NEW")
                .channel("EMAIL")
                .recipientEmail("new_fail@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Fresh Failed")
                .templateName("default")
                .templateDataJson("{}")
                .status("FAILED")
                .createdAt(now.minus(30, ChronoUnit.DAYS))
                .build());

        // 4. Received Events (Idempotency Anchor): Older than 90 days vs Recent
        ReceivedEvent expiredEvent = receivedEventRepository.save(ReceivedEvent.builder()
                .eventId("EVT-ANCHOR-OLD-" + UUID.randomUUID())
                .sourceService("booking-service")
                .eventType("BUS_BOOKING_CONFIRMED")
                .aggregateId("AGG-OLD-1")
                .sanitizedPayloadJson("{}")
                .status("PROCESSED")
                .receivedAt(now.minus(95, ChronoUnit.DAYS))
                .build());

        ReceivedEvent freshEvent = receivedEventRepository.save(ReceivedEvent.builder()
                .eventId("EVT-ANCHOR-NEW-" + UUID.randomUUID())
                .sourceService("booking-service")
                .eventType("BUS_BOOKING_CONFIRMED")
                .aggregateId("AGG-NEW-1")
                .sanitizedPayloadJson("{}")
                .status("PROCESSED")
                .receivedAt(now.minus(5, ChronoUnit.DAYS))
                .build());

        // Execute purge worker
        retentionPurgeWorker.purgeExpiredRecords();

        // Assertions: Expired records must be deleted
        assertFalse(inAppNotificationRepository.findById(expiredInApp.getId()).isPresent(),
                "Expired in-app notification should be deleted");
        assertFalse(notificationDeliveryRepository.findById(expiredAcceptedEmail.getId()).isPresent(),
                "Accepted email older than 60 days should be deleted");
        assertFalse(notificationDeliveryRepository.findById(expiredSimulatedEmail.getId()).isPresent(),
                "Simulated email older than 60 days should be deleted");
        assertFalse(notificationDeliveryRepository.findById(expiredFailedEmail.getId()).isPresent(),
                "Failed email older than 180 days should be deleted");
        assertFalse(receivedEventRepository.findById(expiredEvent.getId()).isPresent(),
                "Received event older than 90 days should be deleted");

        // Assertions: Fresh records must be preserved intact
        assertTrue(inAppNotificationRepository.findById(activeInApp.getId()).isPresent(),
                "Fresh in-app notification should be preserved");
        
        NotificationDelivery preservedAccepted = notificationDeliveryRepository.findById(freshAcceptedEmail.getId()).orElseThrow();
        assertEquals("ACCEPTED", preservedAccepted.getStatus());
        assertNotNull(preservedAccepted.getAcceptedAt());
        assertNull(preservedAccepted.getDeliveredAt(), "deliveredAt must remain null until webhook confirmation");

        NotificationDelivery preservedSimulated = notificationDeliveryRepository.findById(freshSimulatedEmail.getId()).orElseThrow();
        assertEquals("SIMULATED", preservedSimulated.getStatus());
        assertNotNull(preservedSimulated.getAcceptedAt());
        assertNull(preservedSimulated.getDeliveredAt());

        assertTrue(notificationDeliveryRepository.findById(freshFailedEmail.getId()).isPresent(),
                "Fresh failed email should be preserved");
        assertTrue(receivedEventRepository.findById(freshEvent.getId()).isPresent(),
                "Fresh received event should be preserved");
    }
}
