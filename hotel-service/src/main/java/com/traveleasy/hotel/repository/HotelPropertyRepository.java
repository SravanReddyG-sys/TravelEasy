package com.traveleasy.hotel.repository;

import com.traveleasy.hotel.model.HotelProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HotelPropertyRepository extends JpaRepository<HotelProperty, Long> {
    List<HotelProperty> findByCityIgnoreCase(String city);
    List<HotelProperty> findByCityIgnoreCaseAndStatus(String city, String status);
    List<HotelProperty> findByManagerId(Long managerId);
    List<HotelProperty> findByStatus(String status);
    Optional<HotelProperty> findByIdAndManagerId(Long id, Long managerId);
}
