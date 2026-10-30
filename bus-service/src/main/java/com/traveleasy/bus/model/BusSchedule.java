package com.traveleasy.bus.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "bus_schedules", indexes = {
    @Index(name = "idx_bus_schedules_operator_id", columnList = "operator_id"),
    @Index(name = "idx_bus_schedules_bus_travel", columnList = "bus_id, travel_date"),
    @Index(name = "idx_bus_schedules_route_travel", columnList = "route_id, travel_date")
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

    private LocalDate arrivalDate;
    private LocalDateTime departureDateTime;
    private LocalDateTime arrivalDateTime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private String status = "SCHEDULED";

    @Transient
    private java.util.List<com.traveleasy.bus.dto.ScheduleStopDto> stops;

    @Transient
    private java.util.List<com.traveleasy.bus.dto.FareOverrideItemDto> fareOverrides;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Helper to retrieve the effective departure date-time, computing from travelDate and departureTime if null.
     */
    public LocalDateTime getEffectiveDepartureDateTime() {
        if (departureDateTime != null) {
            return departureDateTime;
        }
        if (travelDate != null && departureTime != null && !departureTime.isBlank()) {
            try {
                return LocalDateTime.of(travelDate, LocalTime.parse(departureTime.trim()));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Helper to retrieve the effective arrival date-time, computing from arrivalDate/travelDate and arrivalTime if null.
     */
    public LocalDateTime getEffectiveArrivalDateTime() {
        if (arrivalDateTime != null) {
            return arrivalDateTime;
        }
        if (arrivalTime != null && !arrivalTime.isBlank()) {
            try {
                LocalTime arrTime = LocalTime.parse(arrivalTime.trim());
                if (arrivalDate != null) {
                    return LocalDateTime.of(arrivalDate, arrTime);
                }
                if (travelDate != null) {
                    LocalDate effArrDate = travelDate;
                    if (departureTime != null && !departureTime.isBlank()) {
                        try {
                            LocalTime depTime = LocalTime.parse(departureTime.trim());
                            if (arrTime.isBefore(depTime)) {
                                effArrDate = travelDate.plusDays(1);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    return LocalDateTime.of(effArrDate, arrTime);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
