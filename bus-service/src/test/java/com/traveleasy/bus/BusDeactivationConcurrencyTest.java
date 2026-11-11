package com.traveleasy.bus;

import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.service.BusStateTransitionService;
import com.traveleasy.bus.service.ScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BusDeactivationConcurrencyTest {

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private BusScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private BusStateTransitionService stateTransitionService;

    private Bus bus;

    @BeforeEach
    void setUp() {
        bus = busRepository.save(Bus.builder()
                .operatorId(601L)
                .operatorName("Kaveri Travels")
                .busNumber("AP02CC" + (int)(Math.random() * 9000 + 1000))
                .busType("AC Sleeper (2+1)")
                .totalSeats(30)
                .status("ACTIVE")
                .build());
    }

    @Test
    @DisplayName("Active upcoming departures block deactivation with 409 CONFLICT")
    void testActiveUpcomingDepartures_BlocksDeactivation() {
        scheduleRepository.save(BusSchedule.builder()
                .operatorId(601L)
                .busId(bus.getId())
                .origin("Bangalore")
                .destination("Goa")
                .travelDate(LocalDate.now().plusDays(3))
                .departureTime("20:00")
                .arrivalTime("08:00")
                .fare(900.0)
                .status("SCHEDULED")
                .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                stateTransitionService.deactivateBus(bus.getId(), "Test deactivation", 601L, "op@kaveri.com", false));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Cannot deactivate bus"));
    }

    @Test
    @DisplayName("Deactivated bus prevents subsequent schedule creation with 400 BAD_REQUEST")
    void testDeactivatedBus_PreventsNewSchedules() {
        // Deactivate bus with no departures
        Bus deactivated = stateTransitionService.deactivateBus(bus.getId(), "Fleet check", 601L, "op@kaveri.com", false);
        assertEquals("INACTIVE", deactivated.getStatus());

        BusSchedule newSchedule = BusSchedule.builder()
                .busId(bus.getId())
                .origin("Bangalore")
                .destination("Goa")
                .travelDate(LocalDate.now().plusDays(5))
                .departureTime("21:00")
                .arrivalTime("07:00")
                .fare(900.0)
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                scheduleService.createSchedule(601L, "op@kaveri.com", false, newSchedule));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Cannot schedule trips on an inactive bus"));
    }

    @Test
    @DisplayName("Concurrent deactivation and schedule creation are safely serialized under lock")
    void testConcurrentDeactivationAndScheduleCreation() throws Exception {
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successfulSchedules = new AtomicInteger(0);
        AtomicInteger deactivationSuccesses = new AtomicInteger(0);
        AtomicInteger conflictsOrRejections = new AtomicInteger(0);

        // Task 1: Schedule creation
        Callable<Void> scheduleTask = () -> {
            startLatch.await();
            try {
                BusSchedule s = BusSchedule.builder()
                        .busId(bus.getId())
                        .origin("Bangalore")
                        .destination("Goa")
                        .travelDate(LocalDate.now().plusDays(20))
                        .departureTime("22:00")
                        .arrivalTime("08:00")
                        .fare(950.0)
                        .availableSeats(30)
                        .build();
                scheduleService.createSchedule(601L, "op@kaveri.com", false, s);
                successfulSchedules.incrementAndGet();
            } catch (Exception e) {
                conflictsOrRejections.incrementAndGet();
            }
            return null;
        };

        // Task 2: Deactivation attempt
        Callable<Void> deactivationTask = () -> {
            startLatch.await();
            try {
                stateTransitionService.deactivateBus(bus.getId(), "Concurrent test", 601L, "op@kaveri.com", false);
                deactivationSuccesses.incrementAndGet();
            } catch (Exception e) {
                conflictsOrRejections.incrementAndGet();
            }
            return null;
        };

        Future<?> f1 = executor.submit(scheduleTask);
        Future<?> f2 = executor.submit(deactivationTask);

        startLatch.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Either schedule won (and deactivation got CONFLICT 409)
        // OR deactivation won (and schedule got BAD_REQUEST 400 because bus became INACTIVE)
        // Both can NEVER succeed simultaneously!
        assertFalse(successfulSchedules.get() == 1 && deactivationSuccesses.get() == 1,
                "Invariant violation: Cannot have both an active upcoming schedule AND successful bus deactivation");
    }
}
