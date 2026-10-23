package com.traveleasy.bus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatPositionDto {
    private String positionId;
    private String seatNumber;
    private String seatType; // "SEATER" or "SLEEPER"
    private String deck;     // "LOWER", "UPPER", "SINGLE"
    private Integer row;
    private Integer column;
    private String side;     // "LEFT", "RIGHT", "CENTER"
    private Boolean isWindow;
    private Boolean isBookable;
    private Integer rowSpan;
    private Integer colSpan;
    private Double price;
    private String status;   // "AVAILABLE", "BOOKED"
    private String gender;   // "UNISEX", "MALE", "FEMALE"
    private Integer visualOrder;
}
