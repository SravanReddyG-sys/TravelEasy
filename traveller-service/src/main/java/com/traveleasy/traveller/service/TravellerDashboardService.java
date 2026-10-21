package com.traveleasy.traveller.service;

import com.traveleasy.traveller.client.BookingServiceClient;
import com.traveleasy.traveller.client.BookingSummaryDto;
import com.traveleasy.traveller.dto.TravellerDashboardResponse;
import com.traveleasy.traveller.dto.TravellerFavouriteDto;
import com.traveleasy.traveller.dto.TravellerPreferenceDto;
import com.traveleasy.traveller.model.TravellerProfile;
import com.traveleasy.traveller.repository.SupportTicketRepository;
import com.traveleasy.traveller.repository.TravellerFavouriteRepository;
import com.traveleasy.traveller.repository.TravellerProfileRepository;
import com.traveleasy.traveller.security.TravellerPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TravellerDashboardService {

    private final TravellerProfileRepository profileRepository;
    private final TravellerProfileService profileService;
    private final TravellerPreferenceService preferenceService;
    private final TravellerFavouriteService favouriteService;
    private final TravellerFavouriteRepository favouriteRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final BookingServiceClient bookingServiceClient;

    public TravellerDashboardResponse getDashboard(TravellerPrincipal principal) {
        // 1. Profile completeness
        TravellerProfile profile = profileRepository.findByUserId(principal.getUserId()).orElse(null);
        int completionPercentage = profileService.calculateCompletionPercentage(profile);

        // 2. Preferences
        TravellerPreferenceDto preferences = preferenceService.getPreferences(principal);

        // 3. Favourites
        long favCount = favouriteRepository.countByUserId(principal.getUserId());
        List<TravellerFavouriteDto> recentFavourites = favouriteService.getFavourites(principal, null);

        // 4. Open tickets count
        long openTickets = supportTicketRepository.countByUserIdAndStatus(principal.getUserId(), "OPEN");

        // 5. Bookings from booking-service via token propagation
        List<BookingSummaryDto> bookings = bookingServiceClient.getCustomerBookings(principal.getUserId(), principal.getToken());
        int totalBookings = bookings.size();
        int confirmed = (int) bookings.stream().filter(b -> "CONFIRMED".equalsIgnoreCase(b.getStatus())).count();
        int cancelled = (int) bookings.stream().filter(b -> "CANCELLED".equalsIgnoreCase(b.getStatus())).count();

        // Take the latest 5 bookings
        List<BookingSummaryDto> recent = bookings.size() > 5 ? bookings.subList(0, 5) : bookings;

        return TravellerDashboardResponse.builder()
                .userId(principal.getUserId())
                .fullName(principal.getFullName())
                .email(principal.getEmail())
                .profileComplete(completionPercentage >= 80)
                .profileCompletionPercentage(completionPercentage)
                .totalBookings(totalBookings)
                .confirmedBookings(confirmed)
                .cancelledBookings(cancelled)
                .favouritesCount(favCount)
                .openTicketsCount(openTickets)
                .recentBookings(recent)
                .favouriteItems(recentFavourites)
                .preferences(preferences)
                .build();
    }
}
