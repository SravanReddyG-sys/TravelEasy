package com.traveleasy.bus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "schedule_stops", indexes = {
    @Index(name = "idx_schedule_stops_schedule_id", columnList = "schedule_id"),
    @Index(name = "idx_schedule_stops_order", columnList = "schedule_id, stop_order")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_schedule_stops_order", columnNames = {"schedule_id", "stop_order"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder; // 0, 1, 2, ... N-1

    @Column(name = "stop_name", nullable = false, length = 100)
    private String stopName;

    @Column(name = "stop_type", length = 20)
    private String stopType; // ORIGIN, INTERMEDIATE, DESTINATION

    @Column(name = "arrival_time", length = 10)
    private String arrivalTime; // e.g. "21:30" (null for ORIGIN)

    @Column(name = "departure_time", length = 10)
    private String departureTime; // e.g. "21:45" (null for DESTINATION)

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "consecutive_fare")
    private Double consecutiveFare; // Elementary fare from stop k to stop k+1. 0.0 for destination.

    public Double getFareToNextStop() {
        return consecutiveFare;
    }

    public void setFareToNextStop(Double fare) {
        this.consecutiveFare = fare;
    }
}
