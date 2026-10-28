package com.traveleasy.bus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteStopDto {
    private Long id;
    private Long routeId;
    private Integer stopOrder;
    private String stopName;
    private String stopType; // ORIGIN, INTERMEDIATE, DESTINATION
    private Double distanceKm;

    @Builder.Default
    private List<RoutePointDto> boardingPoints = new ArrayList<>();

    @Builder.Default
    private List<RoutePointDto> droppingPoints = new ArrayList<>();
}
