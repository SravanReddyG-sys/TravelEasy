package com.traveleasy.booking.dto;

import com.traveleasy.booking.model.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingLifecycleOperationResponse {
    private String message;
    private String bookingRef;
    private String previousStatus;
    private String newStatus;
    private LocalDateTime timestamp;
    private Booking booking;
}
