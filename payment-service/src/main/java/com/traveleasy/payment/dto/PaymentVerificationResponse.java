package com.traveleasy.payment.dto;

import com.traveleasy.payment.model.PaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentVerificationResponse {
    private boolean verified;
    private String message;
    private String bookingRef;
    private String transactionRef;
    private String paymentId;
    private String status;
    private PaymentTransaction transaction;
}
