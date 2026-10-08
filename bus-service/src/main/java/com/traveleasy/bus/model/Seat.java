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
}
