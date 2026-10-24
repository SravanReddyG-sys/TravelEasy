package com.traveleasy.traveller.dto;

import com.traveleasy.traveller.client.BookingSummaryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravellerDashboardResponse {
    private Long userId;
    private String fullName;
    private String email;
    private boolean profileComplete;
    private int profileCompletionPercentage;
    private int totalBookings;
    private int confirmedBookings;
    private int cancelledBookings;
    private long favouritesCount;
    private long openTicketsCount;
    private List<BookingSummaryDto> recentBookings;
    private List<TravellerFavouriteDto> favouriteItems;
    private TravellerPreferenceDto preferences;
}
