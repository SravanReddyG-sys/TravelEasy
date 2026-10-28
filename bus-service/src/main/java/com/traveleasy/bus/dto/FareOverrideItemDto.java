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
public class FareOverrideItemDto {

    private Integer fromStopOrder;
    private Integer toStopOrder;
    private String fromStopName;
    private String toStopName;
    private Double customFare; // Set > 0 for override; null or <= 0 removes override
}
