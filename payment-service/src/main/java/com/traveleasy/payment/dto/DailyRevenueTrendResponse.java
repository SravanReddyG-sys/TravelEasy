package com.traveleasy.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyRevenueTrendResponse {
    private LocalDate date;
    private BigDecimal capturedCollections;
    private BigDecimal processedRefunds;
    private BigDecimal netCollections;
    private String currency;
}
