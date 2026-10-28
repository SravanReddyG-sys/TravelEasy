package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

    List<RouteStop> findByRouteIdOrderByStopOrderAsc(Long routeId);

    void deleteByRouteId(Long routeId);

    long countByRouteId(Long routeId);
}
