package com.traveleasy.traveller.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSummaryDto {
    private Long id;
    private String bookingRef;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String serviceType;
    private Long serviceId;
    private Long providerId;
    private String serviceTitle;
    private Double totalAmount;
    private String status;
    private String detailsJson;
    private LocalDateTime bookingDate;
}
