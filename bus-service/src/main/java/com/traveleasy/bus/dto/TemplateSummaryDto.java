package com.traveleasy.bus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateSummaryDto {
    private String id;
    private String displayName;
    private String description;
    private String busConfiguration; // "SINGLE_DECK", "DOUBLE_DECK"
    private Integer deckCount;
    private Integer totalInventory;
    private Integer lowerDeckInventory;
    private Integer upperDeckInventory;
    private Integer lowerRows;
    private Integer lowerColumns;
    private Integer upperRows;
    private Integer upperColumns;
    private String referenceImage;
    private Integer version;
}
