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
public class CreateRouteRequest {
    private String routeCode; // optional for updates, auto-generated on create if omitted
    private String routeName;
    private String origin;
    private String destination;
    private String description;
    @Builder.Default
    private String status = "ACTIVE";

    @Builder.Default
    private List<RouteStopDto> stops = new ArrayList<>();
}
