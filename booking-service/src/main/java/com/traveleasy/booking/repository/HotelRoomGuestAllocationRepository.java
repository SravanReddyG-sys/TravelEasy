package com.traveleasy.booking.repository;

import com.traveleasy.booking.model.HotelRoomGuestAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HotelRoomGuestAllocationRepository extends JpaRepository<HotelRoomGuestAllocation, Long> {

    @Query("SELECT r FROM HotelRoomGuestAllocation r WHERE r.allocationSnapshot.id = :allocationSnapshotId ORDER BY r.roomSequence ASC")
    List<HotelRoomGuestAllocation> findByAllocationSnapshotIdOrderByRoomSequenceAsc(@Param("allocationSnapshotId") Long allocationSnapshotId);

    @Query("SELECT r FROM HotelRoomGuestAllocation r WHERE r.allocationSnapshot.id = :allocationSnapshotId AND r.roomSequence = :roomSequence")
    Optional<HotelRoomGuestAllocation> findByAllocationSnapshotIdAndRoomSequence(@Param("allocationSnapshotId") Long allocationSnapshotId, @Param("roomSequence") Integer roomSequence);
}
