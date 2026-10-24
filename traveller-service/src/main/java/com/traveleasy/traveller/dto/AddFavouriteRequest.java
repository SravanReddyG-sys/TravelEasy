package com.traveleasy.traveller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddFavouriteRequest {
    private String itemType; // BUS, HOTEL, ROUTE
    private Long itemId;
    private String title;
    private String subtitle;
    private String metadataJson;
}
