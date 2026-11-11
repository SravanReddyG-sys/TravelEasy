package com.traveleasy.payment.service;

import com.traveleasy.payment.config.RazorpayConfig;
import com.traveleasy.payment.dto.RecipientDto;
import com.traveleasy.payment.dto.RecipientRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecipientResolutionService {

    private final RazorpayConfig razorpayConfig;
    private final RestTemplate restTemplate;

    /**
     * Resolves the traveller recipient for notification events.
     * Looks up customer details from Booking Service. If Booking Service is unavailable,
     * gracefully falls back to available local details without failing or rolling back.
     *
     * @param bookingRef the booking reference
     * @param fallbackCustomerId the customer ID stored on the payment transaction
     * @return list containing the traveller recipient DTO
     */
    public List<RecipientDto> resolveTravellerRecipient(String bookingRef, Long fallbackCustomerId) {
        Long resolvedCustomerId = fallbackCustomerId;
        String customerName = "Traveller";
        String customerEmail = null;

        if (bookingRef != null && !bookingRef.isBlank()) {
            try {
                String url = razorpayConfig.getBookingServiceUrl() + "/api/bookings/ref/" + bookingRef.trim();
                ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<?, ?> body = response.getBody();

                    Object custIdObj = body.get("customerId");
                    if (custIdObj != null) {
                        try {
                            resolvedCustomerId = Long.parseLong(custIdObj.toString());
                        } catch (Exception ignored) {
                        }
                    }

                    Object nameObj = body.get("customerName");
                    if (nameObj != null && !nameObj.toString().isBlank()) {
                        customerName = nameObj.toString().trim();
                    }

                    Object emailObj = body.get("customerEmail");
                    if (emailObj != null && !emailObj.toString().isBlank()) {
                        customerEmail = emailObj.toString().trim();
                    }
                }
            } catch (Exception e) {
                log.warn("Could not retrieve customer details from Booking Service for bookingRef {}: {}. Using local fallback.",
                        bookingRef, e.getMessage());
            }
        }

        List<String> channels = new ArrayList<>();
        if (resolvedCustomerId != null && resolvedCustomerId > 0) {
            channels.add("IN_APP");
        }
        if (customerEmail != null && !customerEmail.isBlank()) {
            channels.add("EMAIL");
        }

        // If no channels resolved (e.g. unknown customer ID and no email), default to in-app if customer ID available
        if (channels.isEmpty() && resolvedCustomerId != null) {
            channels.add("IN_APP");
        }

        RecipientDto recipient = RecipientDto.builder()
                .role(RecipientRole.TRAVELLER)
                .userId(resolvedCustomerId)
                .name(customerName)
                .email(customerEmail)
                .channels(channels)
                .build();

        return List.of(recipient);
    }
}
