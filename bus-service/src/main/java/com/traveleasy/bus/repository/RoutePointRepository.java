package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.RoutePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoutePointRepository extends JpaRepository<RoutePoint, Long> {

    List<RoutePoint> findByRouteId(Long routeId);

    List<RoutePoint> findByRouteStopId(Long routeStopId);

    List<RoutePoint> findByRouteStopIdAndPointType(Long routeStopId, String pointType);

    void deleteByRouteId(Long routeId);

    void deleteByRouteStopId(Long routeStopId);
}
