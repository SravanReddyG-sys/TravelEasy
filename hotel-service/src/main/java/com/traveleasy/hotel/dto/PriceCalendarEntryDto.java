package com.traveleasy.hotel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceCalendarEntryDto {
    private LocalDate date;
    private Double basePrice;
    private Double overridePrice;
    private Double effectivePrice;

    @JsonProperty("isOverride")
    private boolean isOverride;

    @JsonProperty("isOverride")
    public boolean isOverride() {
        return isOverride;
    }

    @JsonProperty("isOverride")
    public void setOverride(boolean isOverride) {
        this.isOverride = isOverride;
    }
}
