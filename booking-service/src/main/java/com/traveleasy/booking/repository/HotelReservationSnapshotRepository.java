package com.traveleasy.booking.repository;

import com.traveleasy.booking.model.HotelReservationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HotelReservationSnapshotRepository extends JpaRepository<HotelReservationSnapshot, Long> {
    Optional<HotelReservationSnapshot> findByBookingId(Long bookingId);
    Optional<HotelReservationSnapshot> findByBookingRef(String bookingRef);
}
