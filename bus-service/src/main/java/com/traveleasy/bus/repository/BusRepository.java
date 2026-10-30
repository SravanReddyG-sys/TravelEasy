package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.Bus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BusRepository extends JpaRepository<Bus, Long> {
    List<Bus> findByOperatorId(Long operatorId);

    boolean existsByBusNumber(String busNumber);

    Optional<Bus> findByBusNumber(String busNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bus b WHERE b.id = :id")
    Optional<Bus> findByIdWithLock(@Param("id") Long id);
}
