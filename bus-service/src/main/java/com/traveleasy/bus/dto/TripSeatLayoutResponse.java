package com.traveleasy.bus.dto;

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
public class TripSeatLayoutResponse {
    private Long scheduleId;
    private Long busId;
    private String templateId;
    private String templateName;
    private String referenceImage;
    private String busNumber;
    private String operatorName;
    private String busType;
    private String busConfiguration; // "SINGLE_DECK", "DOUBLE_DECK"
    private String facilityType;     // "AC", "NON_AC"
    private String amenities;
    private Integer totalInventory;
    private Integer availableSeats;
    private Double baseFare;
    private String origin;
    private String destination;
    private String travelDate;
    private String departureTime;
    private String arrivalTime;
    private String boardingPoints;
    private String droppingPoints;

    @Builder.Default
    private List<DeckLayoutDto> decks = new ArrayList<>();
}
