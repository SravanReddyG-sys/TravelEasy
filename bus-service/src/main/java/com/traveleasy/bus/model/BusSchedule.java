package com.traveleasy.bus.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "bus_schedules", indexes = {
    @Index(name = "idx_bus_schedules_operator_id", columnList = "operator_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long operatorId;
    private Long busId;
    private Long routeId;
    private String operatorName;
    private String busNumber;
    private String busType;

    private String origin;
    private String destination;
    private LocalDate travelDate;
    private String departureTime;
    private String arrivalTime;
    private Double fare;
    private Integer availableSeats;
    private String boardingPoints;
    private String droppingPoints;

    @Builder.Default
    private String status = "SCHEDULED";

    @Transient
    private java.util.List<com.traveleasy.bus.dto.ScheduleStopDto> stops;
}
