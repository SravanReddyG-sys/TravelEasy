package com.traveleasy.bus.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "bus_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long busId;
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
}
