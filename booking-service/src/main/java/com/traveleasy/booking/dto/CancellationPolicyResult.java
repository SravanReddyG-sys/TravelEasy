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
public class CancellationPolicyResult {
    private String policyTier; // FREE_CANCELLATION_48H, PARTIAL_REFUND_50_24H_48H, NON_REFUNDABLE_LESS_THAN_24H, PAST_CHECKIN_NON_REFUNDABLE
    private double refundPercentage; // 100.0, 50.0, 0.0
    private double refundAmount;
    private double cancellationFee;
    private String description;
    private LocalDateTime evaluationTime;
    private LocalDateTime scheduledCheckInTime;
    private long hoursUntilCheckIn;
    private boolean eligibleForRefund;
}
