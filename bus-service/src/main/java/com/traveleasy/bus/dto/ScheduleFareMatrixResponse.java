package com.traveleasy.bus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleFareMatrixResponse {

    private Long scheduleId;
    private Integer totalStops;
    private Integer totalPairs;
    private List<ScheduleStopDto> stops;
    private List<FareMatrixEntryDto> matrix;
}
