package com.traveleasy.review.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String bookingRef;
    private Long customerId;
    private String customerName;

    private String serviceType; // BUS, HOTEL
    private Long serviceId; // busId or hotelId
    private Integer rating; // 1 to 5

    @Column(length = 1000)
    private String comment;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
