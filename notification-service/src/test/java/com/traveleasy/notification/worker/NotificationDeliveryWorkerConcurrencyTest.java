package com.traveleasy.notification.worker;

import com.traveleasy.notification.model.NotificationDelivery;
import com.traveleasy.notification.model.RecipientRole;
import com.traveleasy.notification.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class NotificationDeliveryWorkerConcurrencyTest {

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    private NotificationDeliveryWorker deliveryWorker;

    @BeforeEach
    void setUp() {
        deliveryRepository.deleteAll();
    }

    @Test
    void testConcurrentWorkers_DisjointOwnership_NoDoubleClaim() throws Exception {
        int totalRecords = 12;
        int workerThreads = 4;

        // Seed 12 pending delivery records
        for (int i = 0; i < totalRecords; i++) {
            deliveryRepository.save(NotificationDelivery.builder()
                    .deliveryRef("DEL-CONC-" + i + "-" + UUID.randomUUID())
                    .eventId("EVT-CONC-" + i)
                    .channel("EMAIL")
                    .recipientEmail("user" + i + "@traveleasy.com")
                    .recipientRole(RecipientRole.TRAVELLER)
                    .subject("Concurrency Item " + i)
                    .templateName("default")
                    .templateDataJson("{}")
                    .status("PENDING")
                    .retryCount(0)
                    .maxRetries(4)
                    .createdAt(Instant.now())
                    .build());
        }

        ExecutorService executor = Executors.newFixedThreadPool(workerThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(workerThreads);

        Instant now = Instant.now();
        Instant staleThreshold = now.minus(5, ChronoUnit.MINUTES);

        List<String> workerIds = Collections.synchronizedList(new ArrayList<>());
        Map<String, Integer> claimCounts = new ConcurrentHashMap<>();

        for (int w = 0; w < workerThreads; w++) {
            final String workerId = "worker-thread-" + w + "-" + UUID.randomUUID();
            workerIds.add(workerId);
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize thread start for maximal contention
                    int claimed = 0;
                    for (int attempt = 0; attempt < 5; attempt++) {
                        try {
                            claimed = deliveryRepository.claimPendingDeliveries(workerId, Instant.now(), staleThreshold, totalRecords);
                            break;
                        } catch (org.springframework.dao.CannotAcquireLockException e) {
                            Thread.sleep(50);
                        }
                    }
                    claimCounts.put(workerId, claimed);
                } catch (Exception e) {
                    System.err.println("Worker " + workerId + " failed with: " + e.getMessage());
                    e.printStackTrace();
                    claimCounts.put(workerId, -1);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all threads simultaneously
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Workers should finish claiming within 10s");
        executor.shutdown();

        // Verify all 12 records are now IN_FLIGHT
        List<NotificationDelivery> inFlightDeliveries = deliveryRepository.findAll();
        assertEquals(totalRecords, inFlightDeliveries.size());

        Set<String> assignedWorkerIds = new HashSet<>();
        for (NotificationDelivery delivery : inFlightDeliveries) {
            assertEquals("IN_FLIGHT", delivery.getStatus(), "All claimed records must be IN_FLIGHT");
            assertNotNull(delivery.getLockedBy(), "Every claimed record must have a locked_by worker ID");
            assignedWorkerIds.add(delivery.getLockedBy());
        }

        // Verify sum of claims equals total claimed records
        int sumClaimed = claimCounts.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(totalRecords, sumClaimed, "Total claimed across all workers must equal exactly the total records");

        // Verify each claimed item belongs to exactly one worker
        Map<String, List<NotificationDelivery>> groupedByWorker = inFlightDeliveries.stream()
                .collect(Collectors.groupingBy(NotificationDelivery::getLockedBy));

        for (Map.Entry<String, List<NotificationDelivery>> entry : groupedByWorker.entrySet()) {
            int claimedByThisWorker = claimCounts.getOrDefault(entry.getKey(), 0);
            assertEquals(claimedByThisWorker, entry.getValue().size(),
                    "Grouped records for worker " + entry.getKey() + " must match its claimed return count");
        }
    }

    @Test
    void testStaleWorkerCannotOverwriteNewerWorkerLease() {
        String deliveryRef = "DEL-LEASE-RACE-" + UUID.randomUUID();
        String staleWorkerId = "stale-worker-" + UUID.randomUUID();
        String newWorkerId = "new-worker-" + UUID.randomUUID();

        // 1. Initial pending record
        NotificationDelivery delivery = deliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef(deliveryRef)
                .eventId("EVT-LEASE-1")
                .channel("EMAIL")
                .recipientEmail("race@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Lease Race Condition Test")
                .templateName("default")
                .templateDataJson("{}")
                .status("PENDING")
                .retryCount(0)
                .maxRetries(4)
                .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build());

        // 2. Stale worker claims record, but experiences network delay
        Instant initialClaimTime = Instant.now().minus(6, ChronoUnit.MINUTES);
        deliveryRepository.claimPendingDeliveries(staleWorkerId, initialClaimTime, initialClaimTime.minus(5, ChronoUnit.MINUTES), 5);

        // 3. Lease expires (> 5 minutes). New worker claims the stale record
        Instant now = Instant.now();
        Instant staleThreshold = now.minus(5, ChronoUnit.MINUTES);
        int reclaimed = deliveryRepository.claimPendingDeliveries(newWorkerId, now, staleThreshold, 5);
        assertEquals(1, reclaimed, "New worker must successfully reclaim expired lease");

        // Verify DB reflects new worker ownership
        NotificationDelivery claimedByNew = deliveryRepository.findByDeliveryRef(deliveryRef).orElseThrow();
        assertEquals("IN_FLIGHT", claimedByNew.getStatus());
        assertEquals(newWorkerId, claimedByNew.getLockedBy());

        // 4. Stale worker wakes up and tries to finalize delivery -> Must be REJECTED (0 rows updated)
        int staleUpdateCount = deliveryRepository.finalizeSuccessfulDelivery(
                delivery.getId(),
                "ACCEPTED",
                "re_stale_msg_id",
                Instant.now(),
                staleWorkerId
        );
        assertEquals(0, staleUpdateCount, "Stale worker commit MUST be rejected because it no longer holds the lease");

        // 5. New worker successfully finalizes delivery -> 1 row updated
        int newWorkerUpdateCount = deliveryRepository.finalizeSuccessfulDelivery(
                delivery.getId(),
                "ACCEPTED",
                "re_new_valid_msg_id",
                Instant.now(),
                newWorkerId
        );
        assertEquals(1, newWorkerUpdateCount, "Active lease owner must successfully finalize delivery");

        // 6. Verify final database record has new worker's message ID, NOT stale worker's ID
        NotificationDelivery finalized = deliveryRepository.findByDeliveryRef(deliveryRef).orElseThrow();
        assertEquals("ACCEPTED", finalized.getStatus());
        assertEquals("re_new_valid_msg_id", finalized.getProviderMessageId(),
                "Final record must reflect the newer worker's dispatch, never the stale worker's");
        assertNotNull(finalized.getAcceptedAt());
        assertNull(finalized.getDeliveredAt());
        assertNull(finalized.getLockedBy());
    }

    @Test
    void testExpiredLeaseRecovery_SafeTransition() {
        String deliveryRef = "DEL-RECOVER-" + UUID.randomUUID();
        String deadWorkerId = "dead-worker-" + UUID.randomUUID();
        String recoveryWorkerId = "recovery-worker-" + UUID.randomUUID();

        // Record stuck in IN_FLIGHT with last attempt 8 minutes ago (dead process)
        NotificationDelivery stuck = deliveryRepository.save(NotificationDelivery.builder()
                .deliveryRef(deliveryRef)
                .eventId("EVT-RECOVER-1")
                .channel("EMAIL")
                .recipientEmail("recover@traveleasy.com")
                .recipientRole(RecipientRole.TRAVELLER)
                .subject("Recovery Test")
                .templateName("default")
                .templateDataJson("{}")
                .status("IN_FLIGHT")
                .lockedBy(deadWorkerId)
                .lastAttemptAt(Instant.now().minus(8, ChronoUnit.MINUTES))
                .retryCount(0)
                .maxRetries(4)
                .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build());

        Instant now = Instant.now();
        Instant staleThreshold = now.minus(5, ChronoUnit.MINUTES);

        int claimed = deliveryRepository.claimPendingDeliveries(recoveryWorkerId, now, staleThreshold, 5);
        assertEquals(1, claimed, "Recovery worker must claim record whose lease is older than 5 minutes");

        NotificationDelivery recovered = deliveryRepository.findByDeliveryRef(deliveryRef).orElseThrow();
        assertEquals("IN_FLIGHT", recovered.getStatus());
        assertEquals(recoveryWorkerId, recovered.getLockedBy());

        int finalized = deliveryRepository.finalizeSuccessfulDelivery(
                stuck.getId(), "ACCEPTED", "re_recovered_msg_001", now, recoveryWorkerId);
        assertEquals(1, finalized);

        NotificationDelivery finalRecord = deliveryRepository.findByDeliveryRef(deliveryRef).orElseThrow();
        assertEquals("ACCEPTED", finalRecord.getStatus());
        assertEquals("re_recovered_msg_001", finalRecord.getProviderMessageId());
        assertNull(finalRecord.getLockedBy());
    }
}
