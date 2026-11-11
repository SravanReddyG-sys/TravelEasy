package com.traveleasy.bus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.bus.dto.NotificationEventRequest;
import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusOutboxEvent;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.repository.BusOutboxEventRepository;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.service.BusStateTransitionService;
import com.traveleasy.bus.service.ScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BusOutboxPersistenceTest {

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private BusScheduleRepository scheduleRepository;

    @Autowired
    private BusOutboxEventRepository outboxRepository;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private BusStateTransitionService stateTransitionService;

    @Autowired
    private ObjectMapper objectMapper;

    private Bus testBus;

    @BeforeEach
    void setUp() {
        testBus = busRepository.save(Bus.builder()
                .operatorId(501L)
                .operatorName("SRS Travels")
                .busNumber("KA04OB" + (int)(Math.random() * 9000 + 1000))
                .busType("AC Sleeper (2+1)")
                .totalSeats(30)
                .status("ACTIVE")
                .build());
    }

    @Test
    @DisplayName("Schedule creation atomically writes BUS_SCHEDULE_CREATED to outbox")
    void testScheduleCreation_WritesOutboxEvent() throws Exception {
        BusSchedule schedule = BusSchedule.builder()
                .busId(testBus.getId())
                .origin("Bangalore")
                .destination("Hyderabad")
                .travelDate(LocalDate.now().plusDays(10))
                .departureTime("21:00")
                .arrivalTime("06:00")
                .fare(850.0)
                .availableSeats(30)
                .build();

        BusSchedule created = scheduleService.createSchedule(501L, "operator501@srstravels.com", false, schedule);
        assertNotNull(created.getId());

        String expectedEventId = "BUS-EVT-SCHED-CREATED-" + created.getId();
        Optional<BusOutboxEvent> outboxOpt = outboxRepository.findByEventId(expectedEventId);
        assertTrue(outboxOpt.isPresent(), "Outbox event must be present for created schedule");

        BusOutboxEvent event = outboxOpt.get();
        assertEquals("BUS_SCHEDULE_CREATED", event.getEventType());
        assertEquals("SCHEDULE", event.getAggregateType());
        assertEquals(String.valueOf(created.getId()), event.getAggregateId());
        assertEquals("PENDING", event.getStatus());

        NotificationEventRequest payload = objectMapper.readValue(event.getPayloadJson(), NotificationEventRequest.class);
        assertEquals(expectedEventId, payload.getEventId());
        assertEquals("BUS_SCHEDULE_CREATED", payload.getEventType());
        assertFalse(payload.getRecipients().isEmpty());
        assertEquals(501L, payload.getRecipients().get(0).getUserId());
        assertEquals("bus-schedule-created", payload.getContent().getTemplate());
    }

    @Test
    @DisplayName("Schedule cancellation writes BUS_SCHEDULE_CANCELLED to outbox")
    void testScheduleCancellation_WritesOutboxEvent() throws Exception {
        BusSchedule schedule = scheduleRepository.save(BusSchedule.builder()
                .operatorId(501L)
                .busId(testBus.getId())
                .origin("Chennai")
                .destination("Coimbatore")
                .travelDate(LocalDate.now().plusDays(15))
                .departureTime("22:00")
                .arrivalTime("06:30")
                .fare(650.0)
                .status("SCHEDULED")
                .build());

        BusSchedule updated = stateTransitionService.updateScheduleStatus(
                schedule.getId(), "CANCELLED", "Operational breakdown",
                501L, "operator501@srstravels.com", false, false);

        assertEquals("CANCELLED", updated.getStatus());

        String expectedEventId = "BUS-EVT-SCHED-CANCELLED-" + schedule.getId();
        Optional<BusOutboxEvent> outboxOpt = outboxRepository.findByEventId(expectedEventId);
        assertTrue(outboxOpt.isPresent(), "Outbox event must be present for cancelled schedule");

        BusOutboxEvent event = outboxOpt.get();
        assertEquals("BUS_SCHEDULE_CANCELLED", event.getEventType());
        assertEquals("SCHEDULE", event.getAggregateType());
        assertEquals("PENDING", event.getStatus());

        NotificationEventRequest payload = objectMapper.readValue(event.getPayloadJson(), NotificationEventRequest.class);
        assertEquals("Operational breakdown", payload.getContent().getTemplateData().get("reason"));
    }

    @Test
    @DisplayName("Bus deactivation and activation write outbox events with idempotency")
    void testBusDeactivationAndActivation_WritesOutboxEvents() {
        // Deactivate
        Bus deactivated = stateTransitionService.deactivateBus(testBus.getId(), "Fleet maintenance", 501L, "op@srs.com", false);
        assertEquals("INACTIVE", deactivated.getStatus());

        long deactEventCount = outboxRepository.findAll().stream()
                .filter(e -> "BUS_DEACTIVATED".equals(e.getEventType()) && String.valueOf(testBus.getId()).equals(e.getAggregateId()))
                .count();
        assertEquals(1, deactEventCount, "Exactly 1 BUS_DEACTIVATED event must be recorded");

        // Idempotent duplicate deactivation should not write another event
        Bus reDeactivated = stateTransitionService.deactivateBus(testBus.getId(), "Fleet maintenance", 501L, "op@srs.com", false);
        assertEquals("INACTIVE", reDeactivated.getStatus());

        long deactEventCountAfter = outboxRepository.findAll().stream()
                .filter(e -> "BUS_DEACTIVATED".equals(e.getEventType()) && String.valueOf(testBus.getId()).equals(e.getAggregateId()))
                .count();
        assertEquals(1, deactEventCountAfter, "Duplicate deactivation must not write duplicate outbox event");

        // Activate
        Bus activated = stateTransitionService.activateBus(testBus.getId(), 501L, "op@srs.com", false);
        assertEquals("ACTIVE", activated.getStatus());

        long actEventCount = outboxRepository.findAll().stream()
                .filter(e -> "BUS_ACTIVATED".equals(e.getEventType()) && String.valueOf(testBus.getId()).equals(e.getAggregateId()))
                .count();
        assertEquals(1, actEventCount, "Exactly 1 BUS_ACTIVATED event must be recorded");
    }

    @Test
    @DisplayName("Repeated valid state transition cycles generate distinct event IDs while identical states remain idempotent")
    void testRepeatedBusStateTransitions_GeneratesDistinctEventIds() {
        // Initial state: ACTIVE
        assertEquals("ACTIVE", testBus.getStatus());

        // Cycle 1: Deactivate (ACTIVE -> INACTIVE)
        Bus deact1 = stateTransitionService.deactivateBus(testBus.getId(), "Routine inspection 1", 501L, "op@srs.com", false);
        assertEquals("INACTIVE", deact1.getStatus());

        // Idempotent duplicate call (INACTIVE -> INACTIVE)
        Bus deactDup = stateTransitionService.deactivateBus(testBus.getId(), "Routine inspection 1", 501L, "op@srs.com", false);
        assertEquals("INACTIVE", deactDup.getStatus());

        // Cycle 1: Activate (INACTIVE -> ACTIVE)
        Bus act1 = stateTransitionService.activateBus(testBus.getId(), 501L, "op@srs.com", false);
        assertEquals("ACTIVE", act1.getStatus());

        // Idempotent duplicate call (ACTIVE -> ACTIVE)
        Bus actDup = stateTransitionService.activateBus(testBus.getId(), 501L, "op@srs.com", false);
        assertEquals("ACTIVE", actDup.getStatus());

        // Cycle 2: Deactivate again (ACTIVE -> INACTIVE)
        Bus deact2 = stateTransitionService.deactivateBus(testBus.getId(), "Routine inspection 2", 501L, "op@srs.com", false);
        assertEquals("INACTIVE", deact2.getStatus());

        // Cycle 2: Activate again (INACTIVE -> ACTIVE)
        Bus act2 = stateTransitionService.activateBus(testBus.getId(), 501L, "op@srs.com", false);
        assertEquals("ACTIVE", act2.getStatus());

        // Assert outbox events: exactly 2 DEACTIVATED and 2 ACTIVATED events
        var deactEvents = outboxRepository.findAll().stream()
                .filter(e -> "BUS_DEACTIVATED".equals(e.getEventType()) && String.valueOf(testBus.getId()).equals(e.getAggregateId()))
                .toList();
        assertEquals(2, deactEvents.size(), "Should have exactly 2 deactivation events across 2 transition cycles");
        assertNotEquals(deactEvents.get(0).getEventId(), deactEvents.get(1).getEventId(), "Each transition must have a unique event ID");

        var actEvents = outboxRepository.findAll().stream()
                .filter(e -> "BUS_ACTIVATED".equals(e.getEventType()) && String.valueOf(testBus.getId()).equals(e.getAggregateId()))
                .toList();
        assertEquals(2, actEvents.size(), "Should have exactly 2 activation events across 2 transition cycles");
        assertNotEquals(actEvents.get(0).getEventId(), actEvents.get(1).getEventId(), "Each activation must have a unique event ID");
    }
}
