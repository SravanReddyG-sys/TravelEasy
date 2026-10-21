package com.traveleasy.traveller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravellerProfileDto {
    private Long id;
    private Long userId;
    private String fullName;
    private String email;
    private LocalDate dateOfBirth;
    private String gender;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String idProofType;
    private String idProofNumber;
    private boolean profileComplete;
    private LocalDateTime updatedAt;
}
