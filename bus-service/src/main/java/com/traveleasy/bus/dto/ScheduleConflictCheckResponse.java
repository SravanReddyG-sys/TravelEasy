package com.traveleasy.bus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleConflictCheckResponse {
    private boolean hasConflict;
    private Long conflictingScheduleId;
    private Long busId;
    private String busNumber;
    private String busName;
    private String origin;
    private String destination;
    private String existingDeparture;
    private String existingArrival;
    private String existingDepartureFormatted;
    private String existingArrivalFormatted;
    private Long turnaroundBufferMinutes;
    private String message;
}
