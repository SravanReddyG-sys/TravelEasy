package com.traveleasy.traveller.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "traveller_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravellerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    private LocalDate dateOfBirth;
    private String gender;

    @Column(length = 500)
    private String address;
    private String city;
    private String state;
    private String pincode;

    private String emergencyContactName;
    private String emergencyContactPhone;

    private String idProofType;
    private String idProofNumber;

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
