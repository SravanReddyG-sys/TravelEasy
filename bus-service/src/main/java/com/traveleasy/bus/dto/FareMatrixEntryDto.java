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
public class FareMatrixEntryDto {

    private Integer fromStopOrder;
    private String fromStopName;
    private Integer toStopOrder;
    private String toStopName;
    private Double distanceKm; // Distance between fromStop and toStop
    private Double calculatedFare; // Elementary segment sum from fromStopOrder to toStopOrder-1
    private Double customFare; // Null if no manual override
    private Double effectiveFare; // customFare != null ? customFare : calculatedFare
    private Boolean isOverridden; // true if customFare != null
    private Integer segmentCount; // toStopOrder - fromStopOrder
}
