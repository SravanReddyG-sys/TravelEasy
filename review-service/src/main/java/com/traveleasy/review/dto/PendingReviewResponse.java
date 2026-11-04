package com.traveleasy.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingReviewResponse {
    private Long id;
    private String customerName;
    private Long customerId;
    private String serviceType;
    private Long serviceId;
    private String bookingRef;
    private Integer rating;
    private String title;
    private String comment;
    private LocalDateTime createdAt;
    private String status;
}
