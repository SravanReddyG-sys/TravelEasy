package com.traveleasy.traveller.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "traveller_favourites")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravellerFavourite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String itemType; // BUS, HOTEL, ROUTE

    private Long itemId;

    @Column(nullable = false)
    private String title;

    private String subtitle;

    @Column(length = 2000)
    private String metadataJson;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
