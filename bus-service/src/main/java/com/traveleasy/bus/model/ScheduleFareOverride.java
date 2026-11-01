package com.traveleasy.bus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_fare_overrides", indexes = {
    @Index(name = "idx_fare_override_schedule", columnList = "schedule_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_fare_override_pair", columnNames = {"schedule_id", "from_stop_order", "to_stop_order"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleFareOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "from_stop_order", nullable = false)
    private Integer fromStopOrder;

    @Column(name = "to_stop_order", nullable = false)
    private Integer toStopOrder;

    @Column(name = "from_stop_name", length = 100)
    private String fromStopName;

    @Column(name = "to_stop_name", length = 100)
    private String toStopName;

    @Column(name = "custom_fare", nullable = false)
    private Double customFare;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
