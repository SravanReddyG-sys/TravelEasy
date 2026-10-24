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
public class DeckLayoutDto {
    private String deck;     // "LOWER", "UPPER", "SINGLE"
    private String deckName; // "Lower Deck", "Upper Deck", "Main Deck"
    private Integer rows;
    private Integer columns;
    private Integer inventory;
    private Integer availableCount;

    @Builder.Default
    private List<SeatPositionDto> positions = new ArrayList<>();
}
