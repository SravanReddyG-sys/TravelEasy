package com.traveleasy.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelRevenueReportResponse {
    private Long hotelId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private double successfulPaymentsAmount;
    private int successfulPaymentsCount;
    private double processedRefundsAmount;
    private int processedRefundsCount;
    private double netRevenue;
    private List<DailyRevenueItem> dailyBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRevenueItem {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate date;
        private double paymentAmount;
        private double refundAmount;
        private double netRevenue;
    }
}
