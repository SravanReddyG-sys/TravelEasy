package com.traveleasy.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelCancellationQuoteResponse {
    private Long bookingId;
    private String bookingRef;
    private String serviceType;
    private String currentStatus;
    private boolean cancellable;
    private boolean alreadyCancelled;
    private String policyTier;
    private double totalAmount;
    private double refundPercentage;
    private double refundAmount;
    private double cancellationFee;
    private boolean eligibleForRefund;
    private String description;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private LocalDateTime scheduledCheckInTime;
    private LocalDateTime evaluationTime;
    private String policyTimeZone;
    private long hoursUntilCheckIn;
}
