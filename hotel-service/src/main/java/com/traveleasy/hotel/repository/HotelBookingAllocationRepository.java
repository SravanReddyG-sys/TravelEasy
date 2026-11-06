package com.traveleasy.hotel.repository;

import com.traveleasy.hotel.model.HotelBookingAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HotelBookingAllocationRepository extends JpaRepository<HotelBookingAllocation, Long> {

    Optional<HotelBookingAllocation> findByBookingRef(String bookingRef);

    List<HotelBookingAllocation> findByRoomIdAndStatus(Long roomId, String status);

    List<HotelBookingAllocation> findByRoomIdAndCheckOutDateGreaterThanEqualAndCheckInDateLessThanEqual(
            Long roomId, LocalDate fromDate, LocalDate toDate);
}
