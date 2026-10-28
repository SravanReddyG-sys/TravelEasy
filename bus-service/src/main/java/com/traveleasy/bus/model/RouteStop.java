package com.traveleasy.bus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "route_stops", indexes = {
    @Index(name = "idx_route_stops_route_id", columnList = "route_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_route_stops_order", columnNames = {"route_id", "stop_order"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder; // 0, 1, 2, ... N-1

    @Column(name = "stop_name", nullable = false, length = 100)
    private String stopName;

    @Column(name = "stop_type", nullable = false, length = 20)
    private String stopType; // ORIGIN, INTERMEDIATE, DESTINATION

    @Column(name = "distance_km")
    private Double distanceKm;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    private List<RoutePoint> boardingPoints;

    @Transient
    private List<RoutePoint> droppingPoints;
}
