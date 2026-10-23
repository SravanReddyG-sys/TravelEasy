package com.traveleasy.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderResponse {
    private String orderId;
    private String transactionRef;
    private String bookingRef;
    private Double amount;
    private Long amountInPaise;
    private String currency;
    private String keyId;
    private String status;
}
