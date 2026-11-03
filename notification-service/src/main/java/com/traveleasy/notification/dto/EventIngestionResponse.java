package com.traveleasy.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventIngestionResponse {
    private String status; // "INGESTED", "ALREADY_INGESTED"
    private String eventId;
    private String message;

    @Builder.Default
    private Instant ingestedAt = Instant.now();
}
