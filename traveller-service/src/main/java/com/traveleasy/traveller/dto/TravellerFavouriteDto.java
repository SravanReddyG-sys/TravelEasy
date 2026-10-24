package com.traveleasy.traveller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravellerFavouriteDto {
    private Long id;
    private Long userId;
    private String itemType;
    private Long itemId;
    private String title;
    private String subtitle;
    private String metadataJson;
    private LocalDateTime createdAt;
}
