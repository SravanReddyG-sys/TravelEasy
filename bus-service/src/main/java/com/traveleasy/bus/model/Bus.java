package com.traveleasy.bus.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "buses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long operatorId;
    private String operatorName;
    private String busNumber;
    private String busType; // AC Sleeper, Volvo AC Multi-Axle, Non-AC Seater
    private Integer totalSeats;
    private String amenities; // Wi-Fi, Charging Point, Water Bottle, Blanket
}
