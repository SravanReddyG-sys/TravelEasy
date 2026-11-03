package com.traveleasy.bus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.bus.dto.EventContentDto;
import com.traveleasy.bus.dto.NotificationEventRequest;
import com.traveleasy.bus.dto.RecipientDto;
import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.BusOutboxEvent;
import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.repository.BusOutboxEventRepository;
import com.traveleasy.bus.repository.BusRepository;
import com.traveleasy.bus.repository.BusScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusStateTransitionService {

    private final BusRepository busRepository;
    private final BusScheduleRepository scheduleRepository;
    private final BusOutboxEventRepository outboxEventRepository;
    private final BusRecipientResolutionService recipientResolutionService;
    private final ObjectMapper objectMapper;

    /**
     * Records a BUS_SCHEDULE_CREATED event in the transactional outbox.
     * Called within the schedule creation transaction boundary.
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordScheduleCreated(BusSchedule schedule, Long callerUserId, String callerEmail, boolean isAdmin) {
        if (schedule == null || schedule.getId() == null) {
            log.warn("Cannot record schedule created event for null schedule or id");
            return;
        }

        String eventId = "BUS-EVT-SCHED-CREATED-" + schedule.getId();
        if (outboxEventRepository.existsByEventId(eventId)) {
            log.info("Schedule created outbox event {} already exists. Skipping duplicate generation.", eventId);
            return;
        }

        Long operatorId = schedule.getOperatorId();
        if ((operatorId == null || operatorId <= 0) && schedule.getBusId() != null) {
            Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);
            if (bus != null) {
                operatorId = bus.getOperatorId();
            }
        }

        RecipientDto operatorRecipient = recipientResolutionService.resolveOperatorRecipient(
                operatorId, callerUserId, callerEmail, isAdmin);

        if (operatorRecipient == null) {
            log.warn("Cannot determine valid operator recipient for schedule #{}. Skipping outbox notification.", schedule.getId());
            return;
        }

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("scheduleId", String.valueOf(schedule.getId()));
        templateData.put("busRegistrationNumber", schedule.getBusNumber() != null ? schedule.getBusNumber() : "");
        templateData.put("origin", schedule.getOrigin() != null ? schedule.getOrigin() : "");
        templateData.put("destination", schedule.getDestination() != null ? schedule.getDestination() : "");
        templateData.put("travelDate", schedule.getTravelDate() != null ? schedule.getTravelDate().toString() : "");
        templateData.put("departureTime", schedule.getDepartureTime() != null ? schedule.getDepartureTime() : "");
        templateData.put("arrivalTime", schedule.getArrivalTime() != null ? schedule.getArrivalTime() : "");
        templateData.put("baseFare", schedule.getFare() != null ? schedule.getFare().toString() : "");
        templateData.put("totalSeats", schedule.getAvailableSeats() != null ? schedule.getAvailableSeats().toString() : "");
        templateData.put("operatorName", operatorRecipient.getName() != null ? operatorRecipient.getName()
                : (schedule.getOperatorName() != null ? schedule.getOperatorName() : "Bus Operator"));

        String title = String.format("Schedule Created: %s to %s on %s",
                schedule.getOrigin(), schedule.getDestination(), schedule.getTravelDate());
        String summary = String.format("Schedule #%d from %s to %s on %s at %s has been published.",
                schedule.getId(), schedule.getOrigin(), schedule.getDestination(),
                schedule.getTravelDate(), schedule.getDepartureTime());

        NotificationEventRequest eventRequest = NotificationEventRequest.builder()
                .eventId(eventId)
                .eventType("BUS_SCHEDULE_CREATED")
                .aggregateType("SCHEDULE")
                .aggregateId(String.valueOf(schedule.getId()))
                .timestamp(Instant.now())
                .recipients(List.of(operatorRecipient))
                .content(EventContentDto.builder()
                        .title(title)
                        .summary(summary)
                        .actionUrl("/operator/schedules/" + schedule.getId())
                        .template("bus-schedule-created")
                        .templateData(templateData)
                        .build())
                .build();

        persistOutboxEvent(eventId, "BUS_SCHEDULE_CREATED", "SCHEDULE", String.valueOf(schedule.getId()), eventRequest);
        log.info("Created outbox event {} for schedule #{}", eventId, schedule.getId());
    }

    /**
     * Records a BUS_SCHEDULE_CANCELLED event in the transactional outbox.
     * Only notifies the operator; traveller notifications are owned by booking-service.
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordScheduleCancelled(BusSchedule schedule, String reason, Long callerUserId, String callerEmail, boolean isAdmin) {
        if (schedule == null || schedule.getId() == null) {
            log.warn("Cannot record schedule cancelled event for null schedule or id");
            return;
        }

        String eventId = "BUS-EVT-SCHED-CANCELLED-" + schedule.getId();
        if (outboxEventRepository.existsByEventId(eventId)) {
            log.info("Schedule cancelled outbox event {} already exists. Skipping duplicate generation.", eventId);
            return;
        }

        Long operatorId = schedule.getOperatorId();
        if ((operatorId == null || operatorId <= 0) && schedule.getBusId() != null) {
            Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);
            if (bus != null) {
                operatorId = bus.getOperatorId();
            }
        }

        RecipientDto operatorRecipient = recipientResolutionService.resolveOperatorRecipient(
                operatorId, callerUserId, callerEmail, isAdmin);

        if (operatorRecipient == null) {
            log.warn("Cannot determine valid operator recipient for cancelled schedule #{}. Skipping outbox notification.", schedule.getId());
            return;
        }

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("scheduleId", String.valueOf(schedule.getId()));
        templateData.put("busRegistrationNumber", schedule.getBusNumber() != null ? schedule.getBusNumber() : "");
        templateData.put("origin", schedule.getOrigin() != null ? schedule.getOrigin() : "");
        templateData.put("destination", schedule.getDestination() != null ? schedule.getDestination() : "");
        templateData.put("travelDate", schedule.getTravelDate() != null ? schedule.getTravelDate().toString() : "");
        templateData.put("departureTime", schedule.getDepartureTime() != null ? schedule.getDepartureTime() : "");
        templateData.put("reason", reason != null && !reason.isBlank() ? reason.trim() : "Operational cancellation");
        templateData.put("operatorName", operatorRecipient.getName() != null ? operatorRecipient.getName()
                : (schedule.getOperatorName() != null ? schedule.getOperatorName() : "Bus Operator"));

        String title = String.format("Schedule Cancelled: %s to %s on %s",
                schedule.getOrigin(), schedule.getDestination(), schedule.getTravelDate());
        String summary = String.format("Schedule #%d from %s to %s on %s has been cancelled.",
                schedule.getId(), schedule.getOrigin(), schedule.getDestination(), schedule.getTravelDate());

        NotificationEventRequest eventRequest = NotificationEventRequest.builder()
                .eventId(eventId)
                .eventType("BUS_SCHEDULE_CANCELLED")
                .aggregateType("SCHEDULE")
                .aggregateId(String.valueOf(schedule.getId()))
                .timestamp(Instant.now())
                .recipients(List.of(operatorRecipient))
                .content(EventContentDto.builder()
                        .title(title)
                        .summary(summary)
                        .actionUrl("/operator/schedules/" + schedule.getId())
                        .template("bus-schedule-cancelled")
                        .templateData(templateData)
                        .build())
                .build();

        persistOutboxEvent(eventId, "BUS_SCHEDULE_CANCELLED", "SCHEDULE", String.valueOf(schedule.getId()), eventRequest);
        log.info("Created outbox event {} for cancelled schedule #{}", eventId, schedule.getId());
    }

    /**
     * Deactivates a bus with pessimistic write locking, upcoming trip guard,
     * ownership validation, and atomic outbox event creation.
     */
    @Transactional(rollbackFor = Exception.class)
    public Bus deactivateBus(Long busId, String reason, Long callerUserId, String callerEmail, boolean isAdmin) {
        // 1. Acquire pessimistic write lock on the bus record
        Bus bus = busRepository.findByIdWithLock(busId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bus not found with id: " + busId));

        // 2. Ownership verification
        if (!isAdmin && (bus.getOperatorId() == null || !bus.getOperatorId().equals(callerUserId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to modify this bus.");
        }

        // 3. Idempotent state check
        if ("INACTIVE".equalsIgnoreCase(bus.getStatus())) {
            log.info("Bus #{} is already INACTIVE. No state change or notification needed.", busId);
            return bus;
        }

        // 4. Upcoming departure guard (evaluated under the lock)
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        List<BusSchedule> candidates = scheduleRepository.findActiveUpcomingSchedulesByBusId(busId, today);
        List<BusSchedule> activeUpcoming = candidates.stream().filter(s -> {
            if (s.getTravelDate().isAfter(today)) return true;
            if (s.getTravelDate().isEqual(today)) {
                if (s.getDepartureTime() != null) {
                    try {
                        LocalTime depTime = LocalTime.parse(s.getDepartureTime());
                        return depTime.isAfter(now);
                    } catch (Exception e) {
                        return true;
                    }
                }
                return true;
            }
            return false;
        }).toList();

        if (!activeUpcoming.isEmpty()) {
            int count = activeUpcoming.size();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot deactivate bus: " + count + (count == 1 ? " active upcoming departure exists." : " active upcoming departures exist."));
        }

        // 5. Update bus status
        bus.setStatus("INACTIVE");
        Bus savedBus = busRepository.save(bus);

        // 6. Record BUS_DEACTIVATED outbox event
        String eventId = "BUS-EVT-BUS-DEACT-" + busId + "-" + UUID.randomUUID();
        RecipientDto operatorRecipient = recipientResolutionService.resolveOperatorRecipient(
                savedBus.getOperatorId(), callerUserId, callerEmail, isAdmin);

        if (operatorRecipient == null) {
            log.warn("Cannot determine valid operator recipient for deactivated bus #{}. Skipping outbox notification.", busId);
            return savedBus;
        }

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("busId", String.valueOf(savedBus.getId()));
        templateData.put("busRegistrationNumber", savedBus.getBusNumber() != null ? savedBus.getBusNumber() : "");
        templateData.put("busType", savedBus.getBusType() != null ? savedBus.getBusType() : "");
        templateData.put("reason", reason != null && !reason.isBlank() ? reason.trim() : "Deactivated by operator/admin");
        templateData.put("operatorName", operatorRecipient.getName() != null ? operatorRecipient.getName()
                : (savedBus.getOperatorName() != null ? savedBus.getOperatorName() : "Bus Operator"));

        String title = "Bus Deactivated: " + savedBus.getBusNumber();
        String summary = String.format("Bus %s has been deactivated and will not be available for new departures.", savedBus.getBusNumber());

        NotificationEventRequest eventRequest = NotificationEventRequest.builder()
                .eventId(eventId)
                .eventType("BUS_DEACTIVATED")
                .aggregateType("BUS")
                .aggregateId(String.valueOf(savedBus.getId()))
                .timestamp(Instant.now())
                .recipients(List.of(operatorRecipient))
                .content(EventContentDto.builder()
                        .title(title)
                        .summary(summary)
                        .actionUrl("/operator/buses/" + savedBus.getId())
                        .template("bus-deactivated")
                        .templateData(templateData)
                        .build())
                .build();

        persistOutboxEvent(eventId, "BUS_DEACTIVATED", "BUS", String.valueOf(savedBus.getId()), eventRequest);
        log.info("Deactivated bus #{} and queued outbox event {}", busId, eventId);

        return savedBus;
    }

    /**
     * Activates a bus with pessimistic write locking, ownership validation,
     * and atomic outbox event creation.
     */
    @Transactional(rollbackFor = Exception.class)
    public Bus activateBus(Long busId, Long callerUserId, String callerEmail, boolean isAdmin) {
        // 1. Acquire pessimistic write lock
        Bus bus = busRepository.findByIdWithLock(busId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bus not found with id: " + busId));

        // 2. Ownership verification
        if (!isAdmin && (bus.getOperatorId() == null || !bus.getOperatorId().equals(callerUserId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to modify this bus.");
        }

        // 3. Idempotent state check
        if ("ACTIVE".equalsIgnoreCase(bus.getStatus())) {
            log.info("Bus #{} is already ACTIVE. No state change or notification needed.", busId);
            return bus;
        }

        // 4. Update status
        bus.setStatus("ACTIVE");
        Bus savedBus = busRepository.save(bus);

        // 5. Record BUS_ACTIVATED outbox event
        String eventId = "BUS-EVT-BUS-ACT-" + busId + "-" + UUID.randomUUID();
        RecipientDto operatorRecipient = recipientResolutionService.resolveOperatorRecipient(
                savedBus.getOperatorId(), callerUserId, callerEmail, isAdmin);

        if (operatorRecipient == null) {
            log.warn("Cannot determine valid operator recipient for activated bus #{}. Skipping outbox notification.", busId);
            return savedBus;
        }

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("busId", String.valueOf(savedBus.getId()));
        templateData.put("busRegistrationNumber", savedBus.getBusNumber() != null ? savedBus.getBusNumber() : "");
        templateData.put("busType", savedBus.getBusType() != null ? savedBus.getBusType() : "");
        templateData.put("operatorName", operatorRecipient.getName() != null ? operatorRecipient.getName()
                : (savedBus.getOperatorName() != null ? savedBus.getOperatorName() : "Bus Operator"));

        String title = "Bus Activated: " + savedBus.getBusNumber();
        String summary = String.format("Bus %s is now active and ready for scheduling.", savedBus.getBusNumber());

        NotificationEventRequest eventRequest = NotificationEventRequest.builder()
                .eventId(eventId)
                .eventType("BUS_ACTIVATED")
                .aggregateType("BUS")
                .aggregateId(String.valueOf(savedBus.getId()))
                .timestamp(Instant.now())
                .recipients(List.of(operatorRecipient))
                .content(EventContentDto.builder()
                        .title(title)
                        .summary(summary)
                        .actionUrl("/operator/buses/" + savedBus.getId())
                        .template("bus-activated")
                        .templateData(templateData)
                        .build())
                .build();

        persistOutboxEvent(eventId, "BUS_ACTIVATED", "BUS", String.valueOf(savedBus.getId()), eventRequest);
        log.info("Activated bus #{} and queued outbox event {}", busId, eventId);

        return savedBus;
    }

    /**
     * Updates schedule status with pessimistic write locking, ownership check,
     * and event emission if status transitions to CANCELLED.
     */
    @Transactional(rollbackFor = Exception.class)
    public BusSchedule updateScheduleStatus(Long scheduleId, String newStatus, String reason,
                                           Long callerUserId, String callerEmail, boolean isAdmin, boolean isInternal) {
        BusSchedule schedule = scheduleRepository.findByIdWithLock(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found with id: " + scheduleId));

        if (!isInternal) {
            if (!isAdmin) {
                Long ownerId = schedule.getOperatorId();
                if (ownerId == null && schedule.getBusId() != null) {
                    Bus bus = busRepository.findById(schedule.getBusId()).orElse(null);
                    if (bus != null) ownerId = bus.getOperatorId();
                }
                if (ownerId == null || !ownerId.equals(callerUserId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to modify this schedule.");
                }
            }
        }

        String normalizedStatus = newStatus != null ? newStatus.toUpperCase().trim() : "";
        String previousStatus = schedule.getStatus() != null ? schedule.getStatus().toUpperCase().trim() : "";

        if (normalizedStatus.equals("CANCELLED") && !"CANCELLED".equals(previousStatus)) {
            schedule.setStatus("CANCELLED");
            BusSchedule saved = scheduleRepository.save(schedule);
            recordScheduleCancelled(saved, reason, callerUserId, callerEmail, isAdmin);
            return saved;
        } else {
            schedule.setStatus(normalizedStatus);
            return scheduleRepository.save(schedule);
        }
    }

    private void persistOutboxEvent(String eventId, String eventType, String aggregateType, String aggregateId, NotificationEventRequest request) {
        try {
            String payloadJson = objectMapper.writeValueAsString(request);
            BusOutboxEvent outboxEvent = BusOutboxEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .payloadJson(payloadJson)
                    .status("PENDING")
                    .maxRetries(5)
                    .createdAt(Instant.now())
                    .build();

            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to serialize and persist outbox event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Failed to persist outbox event for " + eventType, e);
        }
    }
}
