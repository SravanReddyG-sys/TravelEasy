package com.traveleasy.bus.service;

import com.traveleasy.bus.model.BusSchedule;
import com.traveleasy.bus.model.ScheduleStop;
import com.traveleasy.bus.model.Seat;
import com.traveleasy.bus.model.SeatSegmentReservation;
import com.traveleasy.bus.repository.BusScheduleRepository;
import com.traveleasy.bus.repository.SeatRepository;
import com.traveleasy.bus.repository.SeatSegmentReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatSegmentReservationService {

    private final SeatSegmentReservationRepository reservationRepository;
    private final BusScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final RouteStopsService routeStopsService;

    /**
     * Reserves physical seats across a range of consecutive route segments [fromStopOrder, toStopOrder - 1].
     * Implements strict deterministic locking order (Schedule -> Seats -> Segment Reservations).
     */
    @Transactional
    public List<SeatSegmentReservation> reserveSeatsForSegments(
            Long scheduleId,
            List<String> seatNumbers,
            Integer fromStopOrder,
            Integer toStopOrder,
            String bookingRef,
            int holdDurationMinutes) {

        // 1. Lock schedule (Lock Order 1)
        BusSchedule schedule = scheduleRepository.findByIdWithLock(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found with id: " + scheduleId));

        if ("CANCELLED".equalsIgnoreCase(schedule.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot reserve seats on a cancelled schedule.");
        }

        if (seatNumbers == null || seatNumbers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No seats provided for reservation");
        }

        // 2. Resolve and validate segment boundaries
        List<ScheduleStop> stops = routeStopsService.getStopsForSchedule(scheduleId);
        int totalStops = (stops != null && stops.size() >= 2) ? stops.size() : 2;

        int fromOrder = (fromStopOrder != null) ? fromStopOrder : 0;
        int toOrder = (toStopOrder != null) ? toStopOrder : (totalStops - 1);

        if (fromOrder < 0 || toOrder >= totalStops || fromOrder >= toOrder) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Invalid journey segment range [%d -> %d]. Allowed range is [0 -> %d]", fromOrder, toOrder, totalStops - 1));
        }

        // 3. Sort requested seats to guarantee deterministic physical seat lock ordering (Lock Order 2)
        List<String> sortedSeats = seatNumbers.stream().distinct().sorted().toList();

        for (String seatNum : sortedSeats) {
            Seat seat = seatRepository.findByScheduleIdAndSeatNumber(scheduleId, seatNum).orElse(null);
            if (seat == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat " + seatNum + " does not exist for schedule " + scheduleId);
            }
            if (Boolean.FALSE.equals(seat.getIsBookable())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat " + seatNum + " is a non-bookable physical space");
            }
        }

        // 4. Query conflicting segment reservations with PESSIMISTIC_WRITE lock (Lock Order 3)
        LocalDateTime now = LocalDateTime.now();
        List<SeatSegmentReservation> conflicts = reservationRepository.findConflictingReservations(
                scheduleId, sortedSeats, fromOrder, toOrder, now);

        if (!conflicts.isEmpty()) {
            List<String> conflictingSeatNumbers = conflicts.stream()
                    .map(SeatSegmentReservation::getSeatNumber)
                    .distinct()
                    .sorted()
                    .toList();
            log.warn("Segment reservation conflict on schedule {} for seats {} in range [{} -> {}]",
                    scheduleId, conflictingSeatNumbers, fromOrder, toOrder);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The following seats are already reserved and not available for the requested journey segment: " + conflictingSeatNumbers);
        }

        // 5. Create new segment reservations
        int duration = holdDurationMinutes > 0 ? holdDurationMinutes : 15;
        LocalDateTime holdExpiry = now.plusMinutes(duration);
        String finalBookingRef = (bookingRef != null && !bookingRef.isBlank())
                ? bookingRef
                : "HOLD-" + scheduleId + "-" + System.currentTimeMillis();

        List<SeatSegmentReservation> reservations = new ArrayList<>();
        for (String seatNum : sortedSeats) {
            for (int seg = fromOrder; seg < toOrder; seg++) {
                reservations.add(SeatSegmentReservation.builder()
                        .scheduleId(scheduleId)
                        .seatNumber(seatNum)
                        .segmentIndex(seg)
                        .fromStopOrder(fromOrder)
                        .toStopOrder(toOrder)
                        .bookingRef(finalBookingRef)
                        .status("HELD")
                        .holdExpiresAt(holdExpiry)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
        }

        List<SeatSegmentReservation> savedReservations = reservationRepository.saveAll(reservations);
        reservationRepository.flush();

        // 6. Update physical seat entity metadata
        for (String seatNum : sortedSeats) {
            Seat seat = seatRepository.findByScheduleIdAndSeatNumber(scheduleId, seatNum).orElse(null);
            if (seat != null) {
                // If it's a full trip, mark physical seat as LOCKED
                if (fromOrder == 0 && toOrder == totalStops - 1) {
                    seat.setStatus("LOCKED");
                }
                seat.setHoldExpiresAt(holdExpiry);
                seat.setHoldBookingRef(finalBookingRef);
                seatRepository.save(seat);
            }
        }

        // Recalculate schedule available seats based on minimum available across all segments
        updateScheduleAvailableSeats(schedule, totalStops);

        log.info("Successfully held {} segment reservations for seats {} on schedule {} for bookingRef: {}",
                savedReservations.size(), sortedSeats, scheduleId, finalBookingRef);

        return savedReservations;
    }

    /**
     * Confirms seat segment reservations permanently upon successful payment.
     */
    @Transactional
    public void confirmSeatsForSegments(Long scheduleId, List<String> seatNumbers, String bookingRef) {
        BusSchedule schedule = scheduleRepository.findByIdWithLock(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found with id: " + scheduleId));

        if ("CANCELLED".equalsIgnoreCase(schedule.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot confirm seats on a cancelled schedule.");
        }

        List<SeatSegmentReservation> reservations = reservationRepository.findByScheduleIdAndBookingRef(scheduleId, bookingRef);
        if (reservations.isEmpty() && seatNumbers != null && !seatNumbers.isEmpty()) {
            // Fallback: lookup by scheduleId and seatNumbers
            List<ScheduleStop> stops = routeStopsService.getStopsForSchedule(scheduleId);
            int totalStops = (stops != null && stops.size() >= 2) ? stops.size() : 2;
            reservations = reservationRepository.findActiveByScheduleAndSegmentRange(
                    scheduleId, 0, totalStops - 1, LocalDateTime.now()).stream()
                    .filter(r -> seatNumbers.contains(r.getSeatNumber()))
                    .toList();
        }

        LocalDateTime now = LocalDateTime.now();
        for (SeatSegmentReservation r : reservations) {
            r.setStatus("BOOKED");
            r.setHoldExpiresAt(null);
            r.setUpdatedAt(now);
            if (bookingRef != null && !bookingRef.isBlank()) {
                r.setBookingRef(bookingRef);
            }
        }
        reservationRepository.saveAll(reservations);
        reservationRepository.flush();

        // Update physical seats
        Set<String> distinctSeats = reservations.stream()
                .map(SeatSegmentReservation::getSeatNumber)
                .collect(Collectors.toSet());

        if (seatNumbers != null) {
            distinctSeats.addAll(seatNumbers);
        }

        for (String seatNum : distinctSeats) {
            Seat seat = seatRepository.findByScheduleIdAndSeatNumber(scheduleId, seatNum).orElse(null);
            if (seat != null) {
                seat.setStatus("BOOKED");
                seat.setHoldExpiresAt(null);
                if (bookingRef != null && !bookingRef.isBlank()) {
                    seat.setHoldBookingRef(bookingRef);
                }
                seatRepository.save(seat);
            }
        }

        List<ScheduleStop> stops = routeStopsService.getStopsForSchedule(scheduleId);
        int totalStops = (stops != null && stops.size() >= 2) ? stops.size() : 2;
        updateScheduleAvailableSeats(schedule, totalStops);

        log.info("Confirmed {} segment reservations for bookingRef {} on schedule {}", reservations.size(), bookingRef, scheduleId);
    }

    /**
     * Releases seat segment reservations for a booking reference or specific seat numbers.
     */
    @Transactional
    public int releaseSeatsForSegments(Long scheduleId, List<String> seatNumbers, String bookingRef) {
        BusSchedule schedule = scheduleRepository.findByIdWithLock(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found with id: " + scheduleId));

        int releasedCount = 0;
        if (bookingRef != null && !bookingRef.isBlank()) {
            List<SeatSegmentReservation> reservations = reservationRepository.findByScheduleIdAndBookingRef(scheduleId, bookingRef);
            releasedCount = (int) reservations.stream().map(SeatSegmentReservation::getSeatNumber).distinct().count();
            reservationRepository.deleteByScheduleIdAndBookingRef(scheduleId, bookingRef);
            reservationRepository.flush();

            // Release physical seats associated with this bookingRef
            List<Seat> scheduleSeats = seatRepository.findByScheduleId(scheduleId);
            for (Seat seat : scheduleSeats) {
                if (bookingRef.equalsIgnoreCase(seat.getHoldBookingRef())) {
                    seat.setStatus("AVAILABLE");
                    seat.setHoldExpiresAt(null);
                    seat.setHoldBookingRef(null);
                    seatRepository.save(seat);
                }
            }
        } else if (seatNumbers != null && !seatNumbers.isEmpty()) {
            for (String seatNum : seatNumbers) {
                Optional<Seat> optSeat = seatRepository.findByScheduleIdAndSeatNumber(scheduleId, seatNum);
                if (optSeat.isPresent()) {
                    Seat seat = optSeat.get();
                    seat.setStatus("AVAILABLE");
                    seat.setHoldExpiresAt(null);
                    seat.setHoldBookingRef(null);
                    seatRepository.save(seat);
                    releasedCount++;
                }
            }
        }

        List<ScheduleStop> stops = routeStopsService.getStopsForSchedule(scheduleId);
        int totalStops = (stops != null && stops.size() >= 2) ? stops.size() : 2;
        updateScheduleAvailableSeats(schedule, totalStops);

        log.info("Released segment reservations for schedule {}, bookingRef: {}, seats: {}", scheduleId, bookingRef, seatNumbers);
        return releasedCount;
    }

    /**
     * Releases all segment reservations for a schedule (e.g. on schedule cancellation).
     */
    @Transactional
    public void releaseAllSegmentsForSchedule(Long scheduleId) {
        reservationRepository.deleteByScheduleId(scheduleId);
        reservationRepository.flush();
        log.info("Purged all seat segment reservations for schedule {}", scheduleId);
    }

    /**
     * Returns the set of unavailable seat numbers for a specific journey segment [fromOrder, toOrder).
     */
    public Set<String> getActiveUnavailableSeats(Long scheduleId, Integer fromOrder, Integer toOrder) {
        if (fromOrder == null || toOrder == null || fromOrder >= toOrder) {
            return Collections.emptySet();
        }

        List<SeatSegmentReservation> activeReservations = reservationRepository.findActiveByScheduleAndSegmentRange(
                scheduleId, fromOrder, toOrder, LocalDateTime.now());

        return activeReservations.stream()
                .map(SeatSegmentReservation::getSeatNumber)
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves all active and held segment reservations for a schedule.
     */
    public List<SeatSegmentReservation> getScheduleReservations(Long scheduleId) {
        return reservationRepository.findByScheduleIdOrderBySeatNumberAscSegmentIndexAsc(scheduleId);
    }

    /**
     * Periodically reaps expired HELD segment reservations.
     */
    @Transactional
    public List<Long> reconcileExpiredSegmentHolds(LocalDateTime now) {
        List<SeatSegmentReservation> expired = reservationRepository.findExpiredHolds(now);
        if (expired.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> expiredIds = expired.stream().map(SeatSegmentReservation::getId).toList();
        reservationRepository.deleteByIds(expiredIds);
        reservationRepository.flush();

        log.info("Reaped {} expired seat segment reservations", expiredIds.size());
        return expiredIds;
    }

    /**
     * Recalculates available seats on parent BusSchedule.
     * Computes the minimum available seats across all consecutive segments.
     */
    private void updateScheduleAvailableSeats(BusSchedule schedule, int totalStops) {
        List<Seat> physicalSeats = seatRepository.findByScheduleId(schedule.getId());
        long totalBookable = physicalSeats.stream().filter(s -> Boolean.TRUE.equals(s.getIsBookable())).count();
        if (totalBookable == 0) totalBookable = physicalSeats.size();

        LocalDateTime now = LocalDateTime.now();
        long minAvailable = totalBookable;

        // Check each consecutive elementary segment k in [0, totalStops - 2]
        for (int seg = 0; seg < totalStops - 1; seg++) {
            List<SeatSegmentReservation> activeSeg = reservationRepository.findActiveByScheduleAndSegmentRange(
                    schedule.getId(), seg, seg + 1, now);
            long bookedCount = activeSeg.stream().map(SeatSegmentReservation::getSeatNumber).distinct().count();
            long segAvailable = Math.max(0, totalBookable - bookedCount);
            if (segAvailable < minAvailable) {
                minAvailable = segAvailable;
            }
        }

        schedule.setAvailableSeats((int) minAvailable);
        scheduleRepository.save(schedule);
    }
}
