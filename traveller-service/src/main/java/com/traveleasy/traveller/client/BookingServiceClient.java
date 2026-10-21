package com.traveleasy.traveller.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingServiceClient {

    private final RestTemplate restTemplate;

    @Value("${booking.service.url:http://localhost:8084}")
    private String bookingServiceUrl;

    public List<BookingSummaryDto> getCustomerBookings(Long userId, String token) {
        String url = bookingServiceUrl + "/api/bookings/customer/" + userId;
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", "Bearer " + token);
        }
        headers.set("X-Internal-Service", "traveller-service");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<BookingSummaryDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<BookingSummaryDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch customer bookings from booking-service for userId {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
