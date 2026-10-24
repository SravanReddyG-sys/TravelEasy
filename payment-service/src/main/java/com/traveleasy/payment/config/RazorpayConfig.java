package com.traveleasy.payment.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Getter
@Configuration
public class RazorpayConfig {

    @Value("${razorpay.key.id:}")
    private String keyId;

    @Value("${razorpay.key.secret:}")
    private String keySecret;

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    @Value("${booking.service.url:http://localhost:8084}")
    private String bookingServiceUrl;

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RazorpayClient razorpayClient() {
        if (!isConfigured()) {
            log.warn("Razorpay credentials not fully configured. API Key ID present: {}", (keyId != null && !keyId.isBlank()));
            return null;
        }
        try {
            return new RazorpayClient(keyId.trim(), keySecret.trim());
        } catch (RazorpayException e) {
            log.error("Failed to initialize RazorpayClient: {}", e.getMessage());
            return null;
        }
    }
}
