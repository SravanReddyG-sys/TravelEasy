package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BusRepository extends JpaRepository<Bus, Long> {
    List<Bus> findByOperatorId(Long operatorId);
}
