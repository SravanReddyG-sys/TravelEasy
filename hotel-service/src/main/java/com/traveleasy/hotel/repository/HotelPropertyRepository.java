package com.traveleasy.hotel.repository;

import com.traveleasy.hotel.model.HotelProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HotelPropertyRepository extends JpaRepository<HotelProperty, Long> {
    List<HotelProperty> findByCityIgnoreCase(String city);
    List<HotelProperty> findByManagerId(Long managerId);
}
