package com.traveleasy.traveller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSupportTicketRequest {
    private String category; // BOOKING, CANCELLATION, REFUND, GENERAL
    private String subject;
    private String message;
}
