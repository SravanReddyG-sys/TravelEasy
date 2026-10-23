package com.traveleasy.payment.dto;

import com.traveleasy.payment.model.PaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundResponse {
    private String message;
    private String refundId;
    private String refundStatus;
    private Double refundAmount;
    private String transactionRef;
    private String bookingRef;
    private LocalDateTime timestamp;
    private PaymentTransaction transaction;
}
