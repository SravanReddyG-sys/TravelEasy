package com.traveleasy.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_bookings_confirmed_at", columnList = "confirmedAt"),
    @Index(name = "idx_bookings_service_type", columnList = "serviceType")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String bookingRef;

    private Long customerId;
    private String customerName;
    private String customerEmail;

    private String serviceType; // BUS, HOTEL
    private Long serviceId; // scheduleId for BUS, hotelId for HOTEL
    private Long providerId; // operatorId or managerId
    private String serviceTitle; // e.g. "Vijayawada to Hyderabad - Bus" or "Novotel Vijayawada"

    private Double totalAmount;
    private String status; // INITIATED, PENDING_PAYMENT, CONFIRMED, CANCELLED, FAILED, EXPIRED

    @Column(length = 4000)
    private String detailsJson; // Seats booked or room types & stay dates

    @Builder.Default
    private LocalDateTime bookingDate = LocalDateTime.now();

    private LocalDateTime confirmedAt;

    @Builder.Default
    private LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);

    @Builder.Default
    private String refundStatus = "NOT_APPLICABLE";
    private String refundId;
    private Double refundAmount;
    private String refundAttemptKey;

    // WP-04: Hotel Stay & Operational Lifecycle Fields (BD-04, BD-09)
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private LocalDateTime checkedInAt;
    private LocalDateTime checkedOutAt;
    private LocalDateTime noShowAt;
    private LocalDateTime cancelledAt;
    private String cancellationPolicyTier;
    private Double cancellationFee;
    private String cancellationReason;
    @Column(length = 2000)
    private String cancellationPolicySnapshot;

    @Builder.Default
    @Column(length = 30)
    private String inventoryReleaseStatus = "NOT_APPLICABLE";

    private LocalDateTime inventoryReleasedAt;

    private Long cancelledByUserId;

    @Column(length = 40)
    private String cancelledByRole;

    @Transient
    private String exteriorImageUrl;

    @Transient
    private String busType;

    @Transient
    private String facilityType;

    @Transient
    private String operatorBusinessName;

    @Transient
    private java.util.List<com.traveleasy.booking.dto.ReservationAllocationItemDto> allocations;

    public boolean isDateWiseBooking() {
        if (!"HOTEL".equalsIgnoreCase(this.serviceType)) {
            return false;
        }
        return this.checkInDate != null || this.checkOutDate != null ||
                this.resolveCheckInDate() != null || this.resolveCheckOutDate() != null;
    }

    public LocalDate resolveCheckInDate() {
        if (checkInDate != null) {
            return checkInDate;
        }
        return extractDateFromDetails(detailsJson, "check-?in");
    }

    public LocalDate resolveCheckOutDate() {
        if (checkOutDate != null) {
            return checkOutDate;
        }
        return extractDateFromDetails(detailsJson, "check-?out");
    }

    private LocalDate extractDateFromDetails(String details, String keywordRegex) {
        if (details == null || details.isBlank()) {
            return null;
        }
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "(?:" + keywordRegex + ")[:\\s]*([0-9]{4}-[0-9]{2}-[0-9]{2})",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher m = p.matcher(details);
            if (m.find()) {
                return LocalDate.parse(m.group(1));
            }
        } catch (Exception ignored) {}
        return null;
    }
}
