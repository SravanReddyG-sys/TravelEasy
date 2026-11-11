package com.traveleasy.booking.dto;

import com.traveleasy.booking.model.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelCancellationResponse {
    private String message;
    private Long bookingId;
    private String bookingRef;
    private String serviceType;
    private String status;
    private boolean idempotent;
    private String previousStatus;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    private CancellationPolicyResult cancellationPolicy;
    private Double refundAmount;
    private Double cancellationFee;
    private String cancellationPolicyTier;
    private String inventoryReleaseStatus;
    private String refundStatus;
    private String refundId;
    private String refundAttemptKey;
    private Booking booking;
    private Map<String, Object> refund;
}
