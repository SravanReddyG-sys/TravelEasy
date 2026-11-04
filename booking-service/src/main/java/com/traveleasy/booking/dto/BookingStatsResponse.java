package com.traveleasy.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingStatsResponse {
    private long totalBookings;
    private ServiceBreakdown serviceBreakdown;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ServiceBreakdown {
        private ServiceMetric bus;
        private ServiceMetric hotel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ServiceMetric {
        private long count;
        private double percentage;
    }
}
