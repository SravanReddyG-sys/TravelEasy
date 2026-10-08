package com.traveleasy.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String bookingRef;

    private Long customerId;
    private String customerName;
    private String customerEmail;

    private String serviceType; // BUS, HOTEL
    private Long serviceId; // scheduleId for BUS, hotelId for HOTEL
    private Long providerId; // operatorId or managerId
    private String serviceTitle; // e.g. "Vijayawada to Hyderabad - Bus" or "Novotel Vijayawada"

    private Double totalAmount;
    private String status; // INITIATED, PENDING_PAYMENT, CONFIRMED, CANCELLED, FAILED

    @Column(length = 4000)
    private String detailsJson; // Seats booked or room types & stay dates

    @Builder.Default
    private LocalDateTime bookingDate = LocalDateTime.now();
}
