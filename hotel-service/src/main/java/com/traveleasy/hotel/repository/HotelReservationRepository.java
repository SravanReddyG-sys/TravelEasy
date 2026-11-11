package com.traveleasy.hotel.repository;

import com.traveleasy.hotel.model.HotelReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HotelReservationRepository extends JpaRepository<HotelReservation, Long> {

    Optional<HotelReservation> findByBookingRef(String bookingRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM HotelReservation r WHERE r.bookingRef = :bookingRef")
    Optional<HotelReservation> findByBookingRefForUpdate(@Param("bookingRef") String bookingRef);

    @Query("SELECT DISTINCT r FROM HotelReservation r LEFT JOIN FETCH r.allocations WHERE r.bookingRef = :bookingRef")
    Optional<HotelReservation> findByBookingRefWithAllocations(@Param("bookingRef") String bookingRef);

    List<HotelReservation> findByHotelIdAndStatus(Long hotelId, String status);

    boolean existsByBookingRef(String bookingRef);
}
