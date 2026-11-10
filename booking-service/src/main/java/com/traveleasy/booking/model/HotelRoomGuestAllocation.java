package com.traveleasy.booking.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Guest allocation record for a single physical room within an allocation snapshot (WP-HB-06.1).
 * Tracks room sequence, adult count, child count, and individual occupants.
 */
@Entity
@Table(name = "hotel_room_guest_allocations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_hrga_allocation_sequence", columnNames = {"allocation_snapshot_id", "room_sequence"})
}, indexes = {
        @Index(name = "idx_hrga_allocation_snapshot_id", columnList = "allocation_snapshot_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelRoomGuestAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_snapshot_id", nullable = false)
    @JsonBackReference
    private HotelAllocationSnapshot allocationSnapshot;

    @Column(name = "room_sequence", nullable = false)
    private Integer roomSequence;

    @Column(name = "adult_count", nullable = false)
    private Integer adultCount;

    @Column(name = "child_count", nullable = false)
    private Integer childCount;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "roomGuestAllocation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    @Builder.Default
    private List<HotelRoomGuest> occupants = new ArrayList<>();

    public void addOccupant(HotelRoomGuest occupant) {
        if (occupants == null) {
            occupants = new ArrayList<>();
        }
        occupants.add(occupant);
        occupant.setRoomGuestAllocation(this);
    }

    public Long getAllocationSnapshotId() {
        return allocationSnapshot != null ? allocationSnapshot.getId() : null;
    }
}
