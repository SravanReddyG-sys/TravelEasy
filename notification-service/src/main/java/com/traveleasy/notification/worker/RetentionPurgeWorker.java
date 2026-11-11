package com.traveleasy.notification.worker;

import com.traveleasy.notification.repository.InAppNotificationRepository;
import com.traveleasy.notification.repository.NotificationDeliveryRepository;
import com.traveleasy.notification.repository.ReceivedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionPurgeWorker {

    private final InAppNotificationRepository inAppNotificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final ReceivedEventRepository receivedEventRepository;

    /**
     * Executes tiered retention cleanup daily at 02:00 UTC.
     * - In-App Notifications: 30-day retention (expiresAt < now)
     * - Accepted/Simulated Email Records: 60-day retention (status IN ('ACCEPTED', 'SIMULATED') and acceptedAt < now - 60d)
     * - Failed Email Records: 180-day retention (status = 'FAILED' and createdAt < now - 180d)
     * - Received Events: 90-day retention (receivedAt < now - 90d)
     */
    @Scheduled(cron = "${notification.retention.cron:0 0 2 * * ?}")
    @Transactional
    public void purgeExpiredRecords() {
        Instant now = Instant.now();

        // 1. In-App Notifications: 30 days
        int inAppPurged = inAppNotificationRepository.deleteByExpiresAtBefore(now);

        // 2. Accepted & Simulated Email Records: 60 days based on accepted_at (delivered_at is reserved for webhook confirmation)
        Instant acceptedCutoff = now.minus(60, ChronoUnit.DAYS);
        int acceptedPurged = notificationDeliveryRepository.deleteByStatusAndAcceptedAtBefore("ACCEPTED", acceptedCutoff);
        int simulatedPurged = notificationDeliveryRepository.deleteByStatusAndAcceptedAtBefore("SIMULATED", acceptedCutoff);
        int totalAcceptedPurged = acceptedPurged + simulatedPurged;

        // 3. Failed Email Records: 180 days
        Instant failedCutoff = now.minus(180, ChronoUnit.DAYS);
        int failedPurged = notificationDeliveryRepository.deleteByStatusAndCreatedAtBefore("FAILED", failedCutoff);

        // 4. Received Events (Idempotency Anchors): 90 days
        Instant eventsCutoff = now.minus(90, ChronoUnit.DAYS);
        int eventsPurged = receivedEventRepository.deleteByReceivedAtBefore(eventsCutoff);

        log.info("Tiered retention purge completed: inAppPurged={}, acceptedEmailsPurged={}, failedEmailsPurged={}, receivedEventsPurged={}",
                inAppPurged, totalAcceptedPurged, failedPurged, eventsPurged);
    }
}
