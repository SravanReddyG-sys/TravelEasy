package com.traveleasy.hotel.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelStep5SubmitRequestDto {

    private Long draftId;

    @Builder.Default
    @AssertTrue(message = "You must confirm that the information provided is accurate and complete and agree to comply with TravelEasy's Partner Terms & Conditions")
    private Boolean confirmationAccepted = true;
}
