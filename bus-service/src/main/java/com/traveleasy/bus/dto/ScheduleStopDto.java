package com.traveleasy.bus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleStopDto {

    private Long id;
    private Integer stopOrder;
    private String stopName;
    private String stopType; // ORIGIN, INTERMEDIATE, DESTINATION
    private String arrivalTime;
    private String departureTime;
    private Double distanceKm;
    private Double consecutiveFare; // Elementary fare from stop k to stop k+1. 0.0 for destination.

    public Double getFareToNextStop() {
        return consecutiveFare;
    }

    public void setFareToNextStop(Double fare) {
        this.consecutiveFare = fare;
    }
}
