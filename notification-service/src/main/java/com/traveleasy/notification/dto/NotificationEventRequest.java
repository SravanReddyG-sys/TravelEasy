package com.traveleasy.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEventRequest {
    private String eventId;
    private String eventType;
    private String aggregateType;
    private String aggregateId;

    @Builder.Default
    private Instant timestamp = Instant.now();

    @Builder.Default
    private List<RecipientDto> recipients = new ArrayList<>();

    private EventContentDto content;
}
