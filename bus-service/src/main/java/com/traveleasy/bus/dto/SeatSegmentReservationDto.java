package com.traveleasy.bus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeatSegmentReservationDto {

    private Long id;
    private Long scheduleId;
    private String seatNumber;
    private Integer segmentIndex;
    private Integer fromStopOrder;
    private Integer toStopOrder;
    private String bookingRef;
    private String status;
    private LocalDateTime holdExpiresAt;
    private LocalDateTime createdAt;
}
