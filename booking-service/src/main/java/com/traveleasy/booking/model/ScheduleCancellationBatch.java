package com.traveleasy.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_cancellation_batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleCancellationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long scheduleId;

    @Column(nullable = false)
    private Long operatorId;

    @Column(nullable = false, length = 30)
    private String batchStatus; // CLAIMED, INVENTORY_LOCKED, PROCESSING_REFUNDS, COMPLETED, PARTIALLY_FAILED

    @Builder.Default
    private Integer totalBookings = 0;

    @Builder.Default
    private Integer refundsProcessed = 0;

    @Builder.Default
    private Integer refundsFailed = 0;

    @Builder.Default
    private Integer unpaidCancelled = 0;

    @Builder.Default
    private Double totalRefundAmount = 0.0;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    @Builder.Default
    private Integer version = 0;
}
