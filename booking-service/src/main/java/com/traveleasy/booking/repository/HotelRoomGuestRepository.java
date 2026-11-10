package com.traveleasy.booking.repository;

import com.traveleasy.booking.model.HotelRoomGuest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotelRoomGuestRepository extends JpaRepository<HotelRoomGuest, Long> {

    @Query("SELECT g FROM HotelRoomGuest g WHERE g.roomGuestAllocation.id = :roomGuestAllocationId ORDER BY g.id ASC")
    List<HotelRoomGuest> findByRoomGuestAllocationIdOrderByIdAsc(@Param("roomGuestAllocationId") Long roomGuestAllocationId);
}
