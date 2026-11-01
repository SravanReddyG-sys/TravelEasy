package com.traveleasy.bus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.traveleasy.bus.dto.SchedulePointDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

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

    @Column(name = "arrival_date")
    private LocalDate arrivalDate;

    @Column(name = "departure_date")
    private LocalDate departureDate;

    @Column(name = "arrival_date_time")
    private LocalDateTime arrivalDateTime;

    @Column(name = "departure_date_time")
    private LocalDateTime departureDateTime;

    @Column(name = "halt_minutes")
    private Integer haltMinutes;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "consecutive_fare")
    private Double consecutiveFare; // Elementary fare from stop k to stop k+1. 0.0 for destination.

    @Lob
    @Column(name = "points_json", columnDefinition = "TEXT")
    private String pointsJson;

    @Transient
    @Builder.Default
    private List<SchedulePointDto> points = new ArrayList<>();

    public List<SchedulePointDto> getPoints() {
        if (points != null && !points.isEmpty()) {
            return points;
        }
        if (pointsJson != null && !pointsJson.isBlank()) {
            try {
                this.points = OBJECT_MAPPER.readValue(pointsJson, new TypeReference<List<SchedulePointDto>>() {});
                return this.points;
            } catch (Exception ignored) {
            }
        }
        return points != null ? points : new ArrayList<>();
    }

    public void setPoints(List<SchedulePointDto> points) {
        this.points = points;
        if (points != null && !points.isEmpty()) {
            try {
                this.pointsJson = OBJECT_MAPPER.writeValueAsString(points);
            } catch (Exception ignored) {
            }
        } else {
            this.pointsJson = null;
        }
    }

    public Double getFareToNextStop() {
        return consecutiveFare;
    }

    public void setFareToNextStop(Double fare) {
        this.consecutiveFare = fare;
    }
}
