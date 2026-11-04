package com.traveleasy.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewStatsResponse {
    private long totalReviews;
    private long pendingReviews;
    private long approvedReviews;
    private long rejectedReviews;
}
