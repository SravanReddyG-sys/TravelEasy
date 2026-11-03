package com.traveleasy.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventContentDto {
    private String title;
    private String summary;
    private String actionUrl;
    private String template;

    @Builder.Default
    private Map<String, Object> templateData = new HashMap<>();
}
