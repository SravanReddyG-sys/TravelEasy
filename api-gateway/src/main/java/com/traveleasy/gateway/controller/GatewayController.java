package com.traveleasy.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Enumeration;

@RestController
public class GatewayController {

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${bus.service.url:http://localhost:8082}")
    private String busServiceUrl;

    @Value("${hotel.service.url:http://localhost:8083}")
    private String hotelServiceUrl;

    @Value("${booking.service.url:http://localhost:8084}")
    private String bookingServiceUrl;

    @Value("${payment.service.url:http://localhost:8085}")
    private String paymentServiceUrl;

    @Value("${notification.service.url:http://localhost:8086}")
    private String notificationServiceUrl;

    @Value("${review.service.url:http://localhost:8087}")
    private String reviewServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxyRequest(@RequestBody(required = false) byte[] body,
                                               HttpMethod method,
                                               HttpServletRequest request) {
        String path = request.getRequestURI();
        String query = request.getQueryString();

        String targetBaseUrl;
        if (path.startsWith("/api/auth") || path.startsWith("/api/users")) {
            targetBaseUrl = authServiceUrl;
        } else if (path.startsWith("/api/buses")) {
            targetBaseUrl = busServiceUrl;
        } else if (path.startsWith("/api/hotels")) {
            targetBaseUrl = hotelServiceUrl;
        } else if (path.startsWith("/api/bookings")) {
            targetBaseUrl = bookingServiceUrl;
        } else if (path.startsWith("/api/payments")) {
            targetBaseUrl = paymentServiceUrl;
        } else if (path.startsWith("/api/notifications")) {
            targetBaseUrl = notificationServiceUrl;
        } else if (path.startsWith("/api/reviews")) {
            targetBaseUrl = reviewServiceUrl;
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Route not found in API Gateway".getBytes());
        }

        String targetUrl = targetBaseUrl + path + (query != null ? "?" + query : "");

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length")) {
                headers.add(name, request.getHeader(name));
            }
        }

        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);

        try {
            return restTemplate.exchange(URI.create(targetUrl), method, entity, byte[].class);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).headers(e.getResponseHeaders()).body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Gateway Error: " + e.getMessage()).getBytes());
        }
    }
}
