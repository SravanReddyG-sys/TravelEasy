package com.traveleasy.booking.repository;

import com.traveleasy.booking.model.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByBookingRef(String bookingRef);
    List<Booking> findByCustomerIdOrderByBookingDateDesc(Long customerId);
    List<Booking> findByCustomerIdAndStatusInOrderByBookingDateDesc(Long customerId, Collection<String> statuses);
    List<Booking> findByProviderIdOrderByBookingDateDesc(Long providerId);
    List<Booking> findByServiceTypeAndProviderId(String serviceType, Long providerId);
    List<Booking> findByServiceTypeAndServiceId(String serviceType, Long serviceId);
    List<Booking> findByStatusInAndBookingDateBefore(List<String> statuses, LocalDateTime dateTime);

    // ==========================================
    // WP-03 Administrative Queries
    // ==========================================

    @Query("SELECT UPPER(b.serviceType), COUNT(b) FROM Booking b " +
           "WHERE b.confirmedAt IS NOT NULL " +
           "   OR b.status IN ('CONFIRMED', 'COMPLETED') " +
           "   OR (b.status = 'CANCELLED' AND (b.refundStatus IN ('PROCESSED', 'PENDING', 'FAILED_RETRYABLE') OR b.refundId IS NOT NULL)) " +
           "GROUP BY UPPER(b.serviceType)")
    List<Object[]> countConfirmedBookingsByServiceType();

    @Query(value = "SELECT CAST(COALESCE(b.confirmed_at, b.booking_date) AS DATE) AS dt, COUNT(*) " +
           "FROM bookings b " +
           "WHERE (b.confirmed_at IS NOT NULL " +
           "   OR b.status IN ('CONFIRMED', 'COMPLETED') " +
           "   OR (b.status = 'CANCELLED' AND (b.refund_status IN ('PROCESSED', 'PENDING', 'FAILED_RETRYABLE') OR b.refund_id IS NOT NULL))) " +
           "  AND COALESCE(b.confirmed_at, b.booking_date) >= :startDateTime " +
           "  AND COALESCE(b.confirmed_at, b.booking_date) <= :endDateTime " +
           "GROUP BY CAST(COALESCE(b.confirmed_at, b.booking_date) AS DATE) " +
           "ORDER BY dt ASC", nativeQuery = true)
    List<Object[]> countConfirmedBookingsByDateNative(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    @Query("SELECT b FROM Booking b " +
           "WHERE b.confirmedAt IS NOT NULL " +
           "   OR b.status IN ('CONFIRMED', 'COMPLETED') " +
           "   OR (b.status = 'CANCELLED' AND (b.refundStatus IN ('PROCESSED', 'PENDING', 'FAILED_RETRYABLE') OR b.refundId IS NOT NULL)) " +
           "ORDER BY COALESCE(b.confirmedAt, b.bookingDate) DESC")
    Page<Booking> findRecentConfirmedBookings(Pageable pageable);
}
