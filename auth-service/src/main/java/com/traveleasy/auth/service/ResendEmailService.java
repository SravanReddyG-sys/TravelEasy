package com.traveleasy.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.auth.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ResendEmailService implements EmailService {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";
    private static final int EXPIRY_MINUTES = 15;

    private final String resendApiKey;
    private final String resendFrom;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ResendEmailService(
            @Value("${resend.api.key:}") String resendApiKey,
            @Value("${resend.from:Travel Easy <onboarding@resend.dev>}") String resendFrom,
            ObjectMapper objectMapper) {
        this.resendApiKey = resendApiKey != null ? resendApiKey.trim() : "";
        this.resendFrom = resendFrom != null ? resendFrom.trim() : "Travel Easy <onboarding@resend.dev>";
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // Secondary constructor for testing with custom HttpClient
    public ResendEmailService(
            String resendApiKey,
            String resendFrom,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.resendApiKey = resendApiKey != null ? resendApiKey.trim() : "";
        this.resendFrom = resendFrom != null ? resendFrom.trim() : "Travel Easy <onboarding@resend.dev>";
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public boolean sendPasswordResetEmail(User user, String resetUrl) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Cannot send password reset email: missing recipient user or email address.");
            return false;
        }

        if (resendApiKey.isBlank()) {
            log.warn("RESEND_API_KEY is not configured. Email delivery to {} will not be dispatched.", maskEmail(user.getEmail()));
            return false;
        }

        String recipientName = resolveFirstName(user);
        String htmlContent = PasswordResetEmailTemplate.buildHtml(recipientName, resetUrl, EXPIRY_MINUTES);
        String textContent = PasswordResetEmailTemplate.buildPlainText(recipientName, resetUrl, EXPIRY_MINUTES);

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("from", resendFrom);
            payload.put("to", Collections.singletonList(user.getEmail()));
            payload.put("subject", "Reset your Travel Easy password");
            payload.put("html", htmlContent);
            payload.put("text", textContent);
            payload.put("attachments", EmailAssetProvider.getEmailAttachments());

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                log.info("Password reset email successfully accepted by Resend provider for recipient {}", maskEmail(user.getEmail()));
                return true;
            } else {
                log.error("Resend API rejected password reset email for {}. HTTP status: {}", maskEmail(user.getEmail()), status);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Email delivery thread interrupted while sending password reset email to {}", maskEmail(user.getEmail()));
            return false;
        } catch (IOException e) {
            log.error("I/O error communicating with Resend API for {}: {}", maskEmail(user.getEmail()), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error dispatching password reset email for {}: {}", maskEmail(user.getEmail()), e.getMessage());
            return false;
        }
    }

    private String resolveFirstName(User user) {
        if (user.getFirstName() != null && !user.getFirstName().trim().isEmpty()) {
            return user.getFirstName().trim();
        }
        if (user.getFullName() != null && !user.getFullName().trim().isEmpty()) {
            String full = user.getFullName().trim();
            int spaceIdx = full.indexOf(' ');
            return spaceIdx > 0 ? full.substring(0, spaceIdx).trim() : full;
        }
        return null;
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int atIdx = email.indexOf('@');
        if (atIdx <= 1) {
            return "***" + (atIdx >= 0 ? email.substring(atIdx) : "");
        }
        return email.charAt(0) + "***" + email.substring(atIdx);
    }
}
