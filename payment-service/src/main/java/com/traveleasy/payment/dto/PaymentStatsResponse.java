package com.traveleasy.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatsResponse {
    private String currency;
    private BigDecimal successfulCapturedCollections;
    private BigDecimal processedRefunds;
    private BigDecimal netCollections;
}
