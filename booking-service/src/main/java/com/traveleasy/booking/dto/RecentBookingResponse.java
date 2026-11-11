package com.traveleasy.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentBookingResponse {
    private String bookingRef;
    private String serviceType;
    private String customerName;
    private Double totalAmount;
    private String status;
    private LocalDateTime bookingDate;
}
