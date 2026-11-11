package com.traveleasy.notification.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.notification.security.NotificationSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ResendEmailClient {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final String resendApiKey;
    private final String resendFrom;
    private final boolean simulationEnabled;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ResendEmailClient(
            @Value("${resend.api.key:}") String resendApiKey,
            @Value("${resend.from:TravelEasy <notifications@mailer.gsrgroups.tech>}") String resendFrom,
            @Value("${resend.simulation.enabled:false}") boolean simulationEnabled,
            ObjectMapper objectMapper) {
        this.resendApiKey = resendApiKey != null ? resendApiKey.trim() : "";
        this.resendFrom = resendFrom != null ? resendFrom.trim() : "TravelEasy <notifications@mailer.gsrgroups.tech>";
        this.simulationEnabled = simulationEnabled;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    // Secondary constructor for custom HttpClient injection and testing
    public ResendEmailClient(
            String resendApiKey,
            String resendFrom,
            boolean simulationEnabled,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.resendApiKey = resendApiKey != null ? resendApiKey.trim() : "";
        this.resendFrom = resendFrom != null ? resendFrom.trim() : "TravelEasy <notifications@mailer.gsrgroups.tech>";
        this.simulationEnabled = simulationEnabled;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean isSimulationEnabled() {
        return simulationEnabled;
    }

    /**
     * Dispatches an email to Resend with idempotency key support.
     *
     * @param toEmail recipient email address
     * @param subject email subject line
     * @param htmlContent rendered HTML body
     * @param textContent rendered plain text fallback
     * @param idempotencyKey unique delivery reference for deduplication
     * @return EmailDispatchResult with acceptance status and provider ID
     */
    public EmailDispatchResult sendEmail(String toEmail, String subject, String htmlContent, String textContent, String idempotencyKey) {
        if (toEmail == null || toEmail.isBlank()) {
            return EmailDispatchResult.builder()
                    .accepted(false)
                    .statusCode(400)
                    .errorMessage("Recipient email is missing or blank.")
                    .build();
        }

        // 1. Simulation Mode: Explicitly configured for dev and test
        if (simulationEnabled) {
            log.info("Simulation mode active (resend.simulation.enabled=true). Simulating email dispatch to {} with idempotency key {}",
                    NotificationSecurityService.maskEmail(toEmail), idempotencyKey);
            return EmailDispatchResult.builder()
                    .accepted(true)
                    .simulated(true)
                    .providerMessageId("simulated_" + idempotencyKey)
                    .statusCode(200)
                    .build();
        }

        // 2. Production / Non-Simulation Mode: Must have API key configured
        if (resendApiKey.isBlank()) {
            log.error("RESEND_API_KEY is not configured and simulation mode is disabled (resend.simulation.enabled=false). Failing email dispatch safely.");
            return EmailDispatchResult.builder()
                    .accepted(false)
                    .simulated(false)
                    .transientFailure(false) // Permanent configuration failure
                    .statusCode(500)
                    .errorMessage("Resend API key is missing and simulation mode is disabled.")
                    .build();
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("from", resendFrom);
            payload.put("to", Collections.singletonList(toEmail.trim().toLowerCase()));
            payload.put("subject", subject);
            payload.put("html", htmlContent);
            payload.put("text", textContent);

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                reqBuilder.header("Idempotency-Key", idempotencyKey);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String responseBody = response.body();

            if (status >= 200 && status < 300) {
                String providerId = null;
                try {
                    JsonNode root = objectMapper.readTree(responseBody);
                    if (root.has("id")) {
                        providerId = root.get("id").asText();
                    }
                } catch (Exception ex) {
                    log.warn("Could not parse provider message ID from Resend response: {}", ex.getMessage());
                }

                log.info("Email accepted by Resend for recipient {} (providerId: {}, status: {})",
                        NotificationSecurityService.maskEmail(toEmail), providerId, status);
                return EmailDispatchResult.builder()
                        .accepted(true)
                        .simulated(false)
                        .providerMessageId(providerId)
                        .statusCode(status)
                        .build();
            } else {
                boolean transientError = (status == 429 || (status >= 500 && status <= 599));
                log.warn("Resend rejected email for {} (HTTP status: {}, transient: {}, response: {})",
                        NotificationSecurityService.maskEmail(toEmail), status, transientError, responseBody);
                return EmailDispatchResult.builder()
                        .accepted(false)
                        .simulated(false)
                        .transientFailure(transientError)
                        .statusCode(status)
                        .errorMessage("Resend HTTP " + status + ": " + responseBody)
                        .build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Email delivery interrupted for recipient {}: {}", NotificationSecurityService.maskEmail(toEmail), e.getMessage());
            return EmailDispatchResult.builder()
                    .accepted(false)
                    .simulated(false)
                    .transientFailure(true)
                    .statusCode(0)
                    .errorMessage("Email dispatch interrupted: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Error dispatching email to {}: {}", NotificationSecurityService.maskEmail(toEmail), e.getMessage());
            return EmailDispatchResult.builder()
                    .accepted(false)
                    .simulated(false)
                    .transientFailure(true) // network / connection timeouts are transient
                    .statusCode(0)
                    .errorMessage(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}
