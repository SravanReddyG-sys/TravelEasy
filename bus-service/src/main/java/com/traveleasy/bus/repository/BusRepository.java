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

    @Query("SELECT COUNT(b) > 0 FROM Bus b WHERE " +
           "(b.exteriorImageUrl IS NOT NULL AND (b.exteriorImageUrl = :filename OR b.exteriorImageUrl LIKE CONCAT('%/', :filename))) OR " +
           "(b.interiorImageUrl IS NOT NULL AND (b.interiorImageUrl = :filename OR b.interiorImageUrl LIKE CONCAT('%/', :filename))) OR " +
           "(b.imageUrl IS NOT NULL AND (b.imageUrl = :filename OR b.imageUrl LIKE CONCAT('%/', :filename)))")
    boolean isImageInUseByRegisteredBus(@Param("filename") String filename);
}
