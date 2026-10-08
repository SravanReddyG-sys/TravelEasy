package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.BusSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface BusScheduleRepository extends JpaRepository<BusSchedule, Long> {
    List<BusSchedule> findByOriginIgnoreCaseAndDestinationIgnoreCaseAndTravelDate(String origin, String destination, LocalDate travelDate);
    List<BusSchedule> findByBusId(Long busId);
}
