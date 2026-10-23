package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.SeatLayoutTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatLayoutTemplateRepository extends JpaRepository<SeatLayoutTemplate, String> {
    List<SeatLayoutTemplate> findByBusConfiguration(String busConfiguration);
}
