package com.traveleasy.booking.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Individual occupant of a physical room (WP-HB-06.1).
 * Strictly differentiated as ADULT or CHILD. For children, age is mandatory (0-17).
 */
@Entity
@Table(name = "hotel_room_guests", indexes = {
        @Index(name = "idx_hrg_room_guest_allocation_id", columnList = "room_guest_allocation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelRoomGuest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_guest_allocation_id", nullable = false)
    @JsonBackReference
    private HotelRoomGuestAllocation roomGuestAllocation;

    @Column(name = "guest_name", nullable = false)
    private String guestName;

    @Enumerated(EnumType.STRING)
    @Column(name = "guest_type", nullable = false, length = 20)
    private GuestType guestType;

    /**
     * Occupant age: required for ADULT (&gt;= 18) and CHILD (0 to 17).
     * May be null on historical adult records created prior to WP-HB-UI-03.
     */
    @Column(name = "age")
    private Integer age;

    /**
     * Adult occupant gender identifier; nullable to support CHILD occupants
     * and historical reservation records.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = true, length = 30)
    private GuestGender gender;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getRoomGuestAllocationId() {
        return roomGuestAllocation != null ? roomGuestAllocation.getId() : null;
    }
}
