package com.traveleasy.bus.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bus_seats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long scheduleId;
    private String seatNumber; // e.g., "L1", "L2", "U1", "1A"
    private String seatType; // Sleeper, Seater
    private Double price;

    @Builder.Default
    private String status = "AVAILABLE"; // AVAILABLE, BOOKED, LOCKED

    private java.time.LocalDateTime holdExpiresAt;
    private String holdBookingRef;

    private String deck; // "LOWER", "UPPER", "SINGLE"

    @Column(name = "seat_row")
    private Integer rowNumber;

    @Column(name = "seat_col")
    private Integer columnNumber;

    private String side; // "LEFT", "RIGHT", "CENTER"

    @Builder.Default
    private Boolean isBookable = true;

    @Builder.Default
    private Integer rowSpan = 1;

    @Builder.Default
    private Integer colSpan = 1;

    private Integer visualOrder;
}
