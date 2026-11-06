package com.traveleasy.hotel.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "room_daily_inventory", uniqueConstraints = {
        @UniqueConstraint(name = "uk_room_date", columnNames = {"roomId", "inventoryDate"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomDailyInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long hotelId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private LocalDate inventoryDate;

    @Column(nullable = false)
    private Integer totalRooms;

    @Column(nullable = false)
    @Builder.Default
    private Integer bookedRooms = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer heldRooms = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer blockedRooms = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public int getAvailableRooms() {
        int total = totalRooms != null ? totalRooms : 0;
        int booked = bookedRooms != null ? bookedRooms : 0;
        int held = heldRooms != null ? heldRooms : 0;
        int blocked = blockedRooms != null ? blockedRooms : 0;
        return Math.max(0, total - booked - held - blocked);
    }

    public int getTotalAllocated() {
        int booked = bookedRooms != null ? bookedRooms : 0;
        int held = heldRooms != null ? heldRooms : 0;
        int blocked = blockedRooms != null ? blockedRooms : 0;
        return booked + held + blocked;
    }

    public boolean canAllocate(int requestedRooms) {
        return requestedRooms > 0 && getAvailableRooms() >= requestedRooms;
    }
}
