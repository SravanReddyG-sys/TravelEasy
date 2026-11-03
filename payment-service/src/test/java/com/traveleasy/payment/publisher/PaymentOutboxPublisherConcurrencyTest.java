package com.traveleasy.payment.publisher;

import com.traveleasy.payment.model.PaymentOutboxEvent;
import com.traveleasy.payment.repository.PaymentOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PaymentOutboxPublisherConcurrencyTest {

    @Autowired
    private PaymentOutboxEventRepository outboxRepository;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("Concurrent workers claim disjoint records without duplicate claims")
    void testConcurrentWorkers_DisjointOwnership_NoDoubleClaim() throws Exception {
        int totalRecords = 12;
        int workerThreads = 4;

        // Seed 12 pending outbox records
        for (int i = 0; i < totalRecords; i++) {
            outboxRepository.save(PaymentOutboxEvent.builder()
                    .eventId("PAY-EVT-CONC-" + i + "-" + UUID.randomUUID())
                    .eventType("PAYMENT_FAILED")
                    .aggregateType("PAYMENT")
                    .aggregateId("TXN-CONC-" + i)
                    .payloadJson("{\"item\":" + i + "}")
                    .status("PENDING")
                    .retryCount(0)
                    .maxRetries(5)
                    .createdAt(Instant.now())
                    .build());
        }

        ExecutorService executor = Executors.newFixedThreadPool(workerThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(workerThreads);

        Map<String, Integer> claimCounts = new ConcurrentHashMap<>();

        for (int w = 0; w < workerThreads; w++) {
            final String workerId = "payment-worker-thread-" + w + "-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize thread start for maximum contention
                    Instant now = Instant.now();
                    Instant leaseExpiry = now.plusSeconds(60);
                    int claimed = outboxRepository.claimPendingEvents(workerId, now, leaseExpiry, totalRecords);
                    claimCounts.put(workerId, claimed);
                } catch (Exception e) {
                    claimCounts.put(workerId, -1);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Workers should finish claiming within 10s");
        executor.shutdown();

        // Verify all 12 records are now IN_FLIGHT
        List<PaymentOutboxEvent> inFlightEvents = outboxRepository.findAll();
        assertEquals(totalRecords, inFlightEvents.size());

        Set<String> assignedWorkerIds = new HashSet<>();
        for (PaymentOutboxEvent event : inFlightEvents) {
            assertEquals("IN_FLIGHT", event.getStatus(), "All claimed records must be IN_FLIGHT");
            assertNotNull(event.getLockedBy(), "Every claimed record must have a locked_by worker ID");
            assignedWorkerIds.add(event.getLockedBy());
        }

        // Verify total sum of claimed counts equals totalRecords
        int sumClaimed = claimCounts.values().stream().filter(c -> c > 0).mapToInt(Integer::intValue).sum();
        assertEquals(totalRecords, sumClaimed, "Total claimed records across all workers must equal total records");

        // Verify each worker that claimed rows has distinct rows
        for (String workerId : assignedWorkerIds) {
            List<PaymentOutboxEvent> workerEvents = outboxRepository.findByLockedByAndStatus(workerId, "IN_FLIGHT");
            assertTrue(workerEvents.size() > 0);
            for (PaymentOutboxEvent e : workerEvents) {
                assertEquals(workerId, e.getLockedBy());
            }
        }
    }

    @Test
    @DisplayName("Stale in-flight lease is recovered by new worker run")
    void testConcurrentWorkers_StaleLeaseRecovery() {
        Instant staleLease = Instant.now().minusSeconds(120);
        PaymentOutboxEvent staleEvent = outboxRepository.save(PaymentOutboxEvent.builder()
                .eventId("PAY-EVT-STALE-" + UUID.randomUUID())
                .eventType("PAYMENT_REFUND_INITIATED")
                .aggregateType("REFUND")
                .aggregateId("rfnd_stale_1")
                .payloadJson("{\"stale\":true}")
                .status("IN_FLIGHT")
                .lockedBy("crashed-payment-worker-old")
                .lockedUntil(staleLease)
                .createdAt(Instant.now().minusSeconds(300))
                .build());

        String newWorkerId = "recovery-payment-worker-" + UUID.randomUUID();
        Instant now = Instant.now();
        Instant newLease = now.plusSeconds(60);

        int claimed = outboxRepository.claimPendingEvents(newWorkerId, now, newLease, 10);
        assertEquals(1, claimed, "New worker should claim the stale event");

        PaymentOutboxEvent recovered = outboxRepository.findById(staleEvent.getId()).orElseThrow();
        assertEquals("IN_FLIGHT", recovered.getStatus());
        assertEquals(newWorkerId, recovered.getLockedBy());
        assertTrue(recovered.getLockedUntil().isAfter(now));
    }

    @Test
    @DisplayName("Published events are never reclaimed by workers")
    void testConcurrentWorkers_PublishedEventsNotReclaimed() {
        outboxRepository.save(PaymentOutboxEvent.builder()
                .eventId("PAY-EVT-PUB-" + UUID.randomUUID())
                .eventType("PAYMENT_FAILED")
                .aggregateType("PAYMENT")
                .aggregateId("TXN-PUB-1")
                .payloadJson("{\"published\":true}")
                .status("PUBLISHED")
                .publishedAt(Instant.now())
                .createdAt(Instant.now().minusSeconds(100))
                .build());

        String workerId = "test-worker-" + UUID.randomUUID();
        int claimed = outboxRepository.claimPendingEvents(workerId, Instant.now(), Instant.now().plusSeconds(60), 10);
        assertEquals(0, claimed, "PUBLISHED events must not be claimed");
    }
}
