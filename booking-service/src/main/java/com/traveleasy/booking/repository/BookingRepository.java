package com.traveleasy.booking.repository;

import com.traveleasy.booking.model.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByBookingRef(String bookingRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.bookingRef = :bookingRef")
    Optional<Booking> findByBookingRefForUpdate(@Param("bookingRef") String bookingRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
    List<Booking> findByCustomerIdOrderByBookingDateDesc(Long customerId);
    List<Booking> findByCustomerIdAndStatusInOrderByBookingDateDesc(Long customerId, Collection<String> statuses);
    List<Booking> findByProviderIdOrderByBookingDateDesc(Long providerId);
    List<Booking> findByServiceTypeAndProviderId(String serviceType, Long providerId);
    List<Booking> findByServiceTypeAndServiceId(String serviceType, Long serviceId);
    List<Booking> findByServiceTypeAndServiceIdOrderByBookingDateDesc(String serviceType, Long serviceId);

    @Query("SELECT DISTINCT b.bookingRef FROM Booking b WHERE UPPER(b.serviceType) = 'HOTEL' AND b.serviceId = :hotelId AND b.bookingRef IS NOT NULL")
    List<String> findDistinctHotelBookingRefsByHotelId(@Param("hotelId") Long hotelId);
    List<Booking> findByStatusInAndBookingDateBefore(List<String> statuses, LocalDateTime dateTime);
    List<Booking> findByStatusInAndExpiresAtBefore(List<String> statuses, LocalDateTime dateTime);

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

    @Transactional
    @Modifying
    @Query("UPDATE Booking b SET b.inventoryReleaseStatus = :status, " +
           "b.inventoryReleasedAt = CASE WHEN :releasedAt IS NOT NULL THEN :releasedAt ELSE b.inventoryReleasedAt END " +
           "WHERE b.id = :bookingId")
    int updateHotelInventoryReleaseOutcome(@Param("bookingId") Long bookingId,
                                           @Param("status") String status,
                                           @Param("releasedAt") LocalDateTime releasedAt);

    @Transactional
    @Modifying
    @Query("UPDATE Booking b SET b.refundStatus = :refundStatus, " +
           "b.refundId = CASE WHEN :refundId IS NOT NULL THEN :refundId ELSE b.refundId END " +
           "WHERE b.id = :bookingId")
    int updateHotelRefundOutcome(@Param("bookingId") Long bookingId,
                                 @Param("refundStatus") String refundStatus,
                                 @Param("refundId") String refundId);
}
