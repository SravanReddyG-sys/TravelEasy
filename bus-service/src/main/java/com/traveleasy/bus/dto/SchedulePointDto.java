package com.traveleasy.bus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchedulePointDto {
    private Long id;
    private String pointName;
    private String pointType; // BOARDING, DROPPING, BOTH
    private String pointBadge;
    private String address;
    private LocalDate arrivalDate;
    private String arrivalTime;
    private LocalDateTime arrivalDateTime;
    private LocalDate departureDate;
    private String departureTime;
    private LocalDateTime departureDateTime;
    private Integer haltMinutes;
    private String haltDurationDisplay;
}
