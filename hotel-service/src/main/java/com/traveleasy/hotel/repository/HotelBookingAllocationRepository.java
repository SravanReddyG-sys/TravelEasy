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

    List<HotelBookingAllocation> findByReservationIdOrderByIdAsc(Long reservationId);

    List<HotelBookingAllocation> findByReservationBookingRefOrderByIdAsc(String bookingRef);

    List<HotelBookingAllocation> findByReservationBookingRef(String bookingRef);

    List<HotelBookingAllocation> findByReservationIdAndStatus(Long reservationId, String status);

    List<HotelBookingAllocation> findByRoomIdAndStatus(Long roomId, String status);

    List<HotelBookingAllocation> findByRoomIdAndStatusIn(Long roomId, List<String> statuses);

    List<HotelBookingAllocation> findByRoomIdAndCheckOutDateGreaterThanEqualAndCheckInDateLessThanEqual(
            Long roomId, LocalDate fromDate, LocalDate toDate);
}
