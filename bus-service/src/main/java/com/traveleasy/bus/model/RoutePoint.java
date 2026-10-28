package com.traveleasy.bus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "route_points", indexes = {
    @Index(name = "idx_route_points_route", columnList = "route_id"),
    @Index(name = "idx_route_points_stop", columnList = "route_stop_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoutePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "route_stop_id", nullable = false)
    private Long routeStopId;

    @Column(name = "point_name", nullable = false, length = 150)
    private String pointName;

    @Column(name = "point_type", nullable = false, length = 20)
    private String pointType; // BOARDING, DROPPING

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "time_slot", length = 50)
    private String timeSlot; // Operating window / pickup window e.g. "06:00 AM - 06:15 AM"

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
