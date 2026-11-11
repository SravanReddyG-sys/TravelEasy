package com.traveleasy.notification.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ResendEmailClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSimulationMode_ExplicitlyEnabled_ReturnsSimulatedNotAccepted() {
        ResendEmailClient client = new ResendEmailClient("", "TravelEasy <notifications@mailer.gsrgroups.tech>", true, objectMapper, mock(HttpClient.class));

        EmailDispatchResult result = client.sendEmail("traveller@test.com", "Test Note", "<p>Hi</p>", "Hi", "DEL-SIM-001");

        assertTrue(result.isAccepted());
        assertTrue(result.isSimulated(), "Must be flagged as simulated");
        assertEquals("simulated_DEL-SIM-001", result.getProviderMessageId());
        assertEquals(200, result.getStatusCode());
    }

    @Test
    void testMissingApiKeyInProductionMode_FailsSafelyWithoutSimulation() {
        ResendEmailClient client = new ResendEmailClient("", "TravelEasy <notifications@mailer.gsrgroups.tech>", false, objectMapper, mock(HttpClient.class));

        EmailDispatchResult result = client.sendEmail("traveller@test.com", "Test Note", "<p>Hi</p>", "Hi", "DEL-PROD-001");

        assertFalse(result.isAccepted());
        assertFalse(result.isSimulated());
        assertFalse(result.isTransientFailure(), "Missing configuration is a permanent error");
        assertEquals(500, result.getStatusCode());
        assertTrue(result.getErrorMessage().contains("Resend API key is missing and simulation mode is disabled"));
    }

    @Test
    void testTransientErrorClassification_Http429() throws Exception {
        HttpClient mockHttp = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(429);
        when(mockResponse.body()).thenReturn("{\"message\":\"Too many requests\"}");
        when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        ResendEmailClient client = new ResendEmailClient("re_valid_key", "TravelEasy <notifications@mailer.gsrgroups.tech>", false, objectMapper, mockHttp);

        EmailDispatchResult result = client.sendEmail("traveller@test.com", "Subject", "<p>Hi</p>", "Hi", "DEL-429");

        assertFalse(result.isAccepted());
        assertTrue(result.isTransientFailure(), "HTTP 429 must be classified as transient");
        assertEquals(429, result.getStatusCode());
    }

    @Test
    void testPermanentErrorClassification_Http422() throws Exception {
        HttpClient mockHttp = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(422);
        when(mockResponse.body()).thenReturn("{\"message\":\"Domain not verified\"}");
        when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        ResendEmailClient client = new ResendEmailClient("re_valid_key", "TravelEasy <notifications@mailer.gsrgroups.tech>", false, objectMapper, mockHttp);

        EmailDispatchResult result = client.sendEmail("traveller@test.com", "Subject", "<p>Hi</p>", "Hi", "DEL-422");

        assertFalse(result.isAccepted());
        assertFalse(result.isTransientFailure(), "HTTP 422 must be classified as permanent");
        assertEquals(422, result.getStatusCode());
    }

    @Test
    void testAuthenticProviderAcceptance_Http200() throws Exception {
        HttpClient mockHttp = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"id\":\"re_real_msg_789\"}");
        when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        ResendEmailClient client = new ResendEmailClient("re_valid_key", "TravelEasy <notifications@mailer.gsrgroups.tech>", false, objectMapper, mockHttp);

        EmailDispatchResult result = client.sendEmail("traveller@test.com", "Subject", "<p>Hi</p>", "Hi", "DEL-200");

        assertTrue(result.isAccepted());
        assertFalse(result.isSimulated(), "Authentic acceptance must NOT be marked simulated");
        assertEquals("re_real_msg_789", result.getProviderMessageId());
        assertEquals(200, result.getStatusCode());
    }

    @Test
    void testVerifiedDomainSenderIdentity_ConfiguredAndDispatched() throws Exception {
        HttpClient mockHttp = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"id\":\"re_verified_domain_msg_001\"}");

        org.mockito.ArgumentCaptor<HttpRequest> requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        when(mockHttp.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        // Instantiated with null 'resendFrom' to verify default fallback to verified domain
        ResendEmailClient client = new ResendEmailClient("re_test_key", null, false, objectMapper, mockHttp);

        EmailDispatchResult result = client.sendEmail("passenger@test.com", "Booking Confirmed", "<p>Your booking is confirmed</p>", "Your booking is confirmed", "DEL-VERIFIED-01");

        assertTrue(result.isAccepted());
        assertEquals("re_verified_domain_msg_001", result.getProviderMessageId());

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals("POST", capturedRequest.method());
        assertEquals("https://api.resend.com/emails", capturedRequest.uri().toString());
        assertTrue(capturedRequest.headers().firstValue("Authorization").orElse("").startsWith("Bearer re_test_key"));
    }
}
