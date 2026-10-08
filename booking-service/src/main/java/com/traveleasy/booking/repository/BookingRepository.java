package com.traveleasy.booking.repository;

import com.traveleasy.booking.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByBookingRef(String bookingRef);
    List<Booking> findByCustomerIdOrderByBookingDateDesc(Long customerId);
    List<Booking> findByProviderIdOrderByBookingDateDesc(Long providerId);
    List<Booking> findByServiceTypeAndProviderId(String serviceType, Long providerId);
}
