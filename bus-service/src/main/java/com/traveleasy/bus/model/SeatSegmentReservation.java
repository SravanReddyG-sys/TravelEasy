package com.traveleasy.bus.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "seat_segment_reservations",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_schedule_seat_segment", columnNames = {"schedule_id", "seat_number", "segment_index"})
    },
    indexes = {
        @Index(name = "idx_segment_res_schedule_seg", columnList = "schedule_id, segment_index"),
        @Index(name = "idx_segment_res_booking_ref", columnList = "booking_ref"),
        @Index(name = "idx_segment_res_expiry", columnList = "status, hold_expires_at")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatSegmentReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Column(name = "segment_index", nullable = false)
    private Integer segmentIndex;

    @Column(name = "from_stop_order", nullable = false)
    private Integer fromStopOrder;

    @Column(name = "to_stop_order", nullable = false)
    private Integer toStopOrder;

    @Column(name = "booking_ref", nullable = false, length = 100)
    private String bookingRef;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // HELD, BOOKED

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
