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
public class RoutePointDto {
    private Long id;
    private Long routeId;
    private Long routeStopId;
    private String pointName;
    private String pointType; // BOARDING, DROPPING
    private String address;
    private String timeSlot;
    @Builder.Default
    private Boolean active = true;
}
