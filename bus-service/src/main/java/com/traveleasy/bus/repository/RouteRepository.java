package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByOperatorIdOrderByCreatedAtDesc(Long operatorId);

    List<Route> findByOperatorIdAndStatusOrderByCreatedAtDesc(Long operatorId, String status);

    Optional<Route> findByRouteCode(String routeCode);

    boolean existsByRouteCode(String routeCode);

    boolean existsByRouteCodeAndIdNot(String routeCode, Long id);
}
