package com.traveleasy.bus.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "buses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long operatorId;
    private String operatorName;
    private String busNumber;
    private String busType; // AC Sleeper, Volvo AC Multi-Axle, Non-AC Seater
    private Integer totalSeats;
    private String amenities; // Wi-Fi, Charging Point, Water Bottle, Blanket

    private String facilityType; // "AC", "NON_AC"
    private String busConfiguration; // "SINGLE_DECK", "DOUBLE_DECK"
    private String templateId; // references SeatLayoutTemplate.id
    @Builder.Default
    private String status = "ACTIVE";

    private String busName;
    private String manufacturer;
    private String model;
    private Integer yearOfManufacturing;
    private String fuelType;
    private String imageUrl;

    private String chassisNumber;
    private String engineNumber;
    private String emissionStandard;
    private String color;
    private String busCategory;
    private String exteriorImageUrl;
    private String interiorImageUrl;

    // Document Information Metadata (Add Bus Wizard Step 3)
    private String rcIssuingAuthority;
    private String rcIssueDate;
    private String rcValidTill;

    private String permitNumber;
    private String permitType;
    private String permitIssueDate;
    private String permitValidTill;

    private String insurancePolicyNumber;
    private String insuranceProvider;
    private String insuranceIssueDate;
    private String insuranceValidTill;

    private String pucNumber;
    private String pucTestingCenter;
    private String pucIssueDate;
    private String pucValidTill;

    private String fitnessCertificateNumber;
    private String fitnessIssuingAuthority;
    private String fitnessIssueDate;
    private String fitnessValidTill;

    @Column(columnDefinition = "TEXT")
    private String documentsJson;
}
