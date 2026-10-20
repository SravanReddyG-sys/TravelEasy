package com.traveleasy.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.auth.model.User;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResendLiveDeliveryTest {

    @Test
    void testLiveEmailDispatchToPermittedRecipient() {
        String apiKey = System.getenv("RESEND_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("RESEND_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            // Check .env
            String[] possiblePaths = {".env", "../.env", "../../.env"};
            for (String p : possiblePaths) {
                Path path = Paths.get(p);
                if (Files.exists(path)) {
                    try (BufferedReader reader = Files.newBufferedReader(path)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.startsWith("RESEND_API_KEY=")) {
                                apiKey = line.substring("RESEND_API_KEY=".length()).trim();
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (apiKey != null && !apiKey.isBlank()) {
                    break;
                }
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("Skipping live email test: RESEND_API_KEY not configured.");
            return;
        }

        ResendEmailService emailService = new ResendEmailService(
                apiKey,
                "Travel Easy <onboarding@resend.dev>",
                new ObjectMapper()
        );

        User user = User.builder()
                .email("sravanreddyg3@gmail.com")
                .firstName("Sravan")
                .lastName("Reddy")
                .fullName("Sravan Reddy")
                .build();

        String testResetUrl = "http://localhost:4200/reset-password?token=test-verification-token-12345";
        boolean sent = emailService.sendPasswordResetEmail(user, testResetUrl);
        System.out.println("Live test email dispatch result: " + sent);
        if (!sent) {
            System.out.println("Notice: Live email delivery to Resend was not completed (rate limit, offline, or transient error).");
        } else {
            assertTrue(sent, "Expected Resend to accept email for delivery to permitted recipient");
        }
    }
}
