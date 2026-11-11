package com.traveleasy.booking.repository;

import com.traveleasy.booking.model.HotelAllocationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotelAllocationSnapshotRepository extends JpaRepository<HotelAllocationSnapshot, Long> {
    List<HotelAllocationSnapshot> findByReservationSnapshotId(Long reservationSnapshotId);
}
