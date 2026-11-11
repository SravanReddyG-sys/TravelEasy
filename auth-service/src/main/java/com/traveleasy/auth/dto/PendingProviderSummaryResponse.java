package com.traveleasy.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingProviderSummaryResponse {
    private Long id;
    private String legalOrganizationName;
    private String providerType; // "BUS_OPERATOR" or "HOTEL_OPERATOR"
    private LocalDateTime submittedOn; // from createdAt
    private String verificationStatus; // "UNDER_REVIEW"
}
