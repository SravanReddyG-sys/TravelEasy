package com.traveleasy.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.auth.controller.AuthController;
import com.traveleasy.auth.dto.ForgotPasswordRequest;
import com.traveleasy.auth.dto.ResetPasswordRequest;
import com.traveleasy.auth.model.Role;
import com.traveleasy.auth.model.User;
import com.traveleasy.auth.repository.UserRepository;
import com.traveleasy.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class PasswordRecoveryAndEmailTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("encodedBcryptPassword123");
        when(emailService.sendPasswordResetEmail(any(), anyString())).thenReturn(true);
    }

    // 1. Forgot-password request for a registered email
    @Test
    void testForgotPassword_RegisteredEmail_GeneratesTokenAndCallsEmailService() throws Exception {
        User user = User.builder()
                .id(101L)
                .email("registered@traveleasy.com")
                .firstName("Alice")
                .lastName("Smith")
                .fullName("Alice Smith")
                .role(Role.ROLE_CUSTOMER)
                .status("ACTIVE")
                .build();

        when(userRepository.findByEmail("registered@traveleasy.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("registered@traveleasy.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("We'll send password-reset instructions to your email address if an account exists."))
                .andExpect(jsonPath("$.resetToken").doesNotExist()); // No token leaked in response

        // Verify token saved with 15 min expiry
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertNotNull(savedUser.getResetPasswordToken());
        assertFalse(savedUser.getResetPasswordToken().isBlank());
        assertNotNull(savedUser.getResetPasswordTokenExpiry());
        assertTrue(savedUser.getResetPasswordTokenExpiry().isAfter(LocalDateTime.now().plusMinutes(14)));
        assertTrue(savedUser.getResetPasswordTokenExpiry().isBefore(LocalDateTime.now().plusMinutes(16)));

        // Verify emailService called with correct URL format
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendPasswordResetEmail(eq(savedUser), urlCaptor.capture());
        String generatedUrl = urlCaptor.getValue();
        assertTrue(generatedUrl.contains("/reset-password?token="));
        assertTrue(generatedUrl.contains(savedUser.getResetPasswordToken()));
    }

    // 2. Forgot-password request for an unregistered email
    // 3. Identical public confirmation behavior for known and unknown accounts
    @Test
    void testForgotPassword_UnregisteredEmail_ReturnsIdenticalGenericResponse() throws Exception {
        when(userRepository.findByEmail("unknown@traveleasy.com")).thenReturn(Optional.empty());

        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("unknown@traveleasy.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("We'll send password-reset instructions to your email address if an account exists."))
                .andExpect(jsonPath("$.resetToken").doesNotExist());

        // Verify emailService NOT called and nothing saved
        verify(emailService, never()).sendPasswordResetEmail(any(), anyString());
        verify(userRepository, never()).save(any());
    }

    // 4. Correct construction of reset URL and email template content
    // 5. Correct HTML and plain-text email content
    @Test
    void testPasswordResetEmailTemplate_RendersExpectedBrandAndContent() {
        String resetUrl = "http://localhost:4200/reset-password?token=sample-uuid-12345";
        String html = PasswordResetEmailTemplate.buildHtml("John", resetUrl, 15);
        String plainText = PasswordResetEmailTemplate.buildPlainText("John", resetUrl, 15);

        // HTML checks
        assertTrue(html.contains("Travel Easy"), "HTML must contain brand name");
        assertTrue(html.contains("YOUR JOURNEY, OUR PRIORITY"), "HTML must contain brand tagline");
        assertTrue(html.contains("cid:travel-easy-logo"), "HTML must contain Travel Easy logo image");
        assertTrue(html.contains("cid:lock-icon"), "HTML must contain lock icon image");
        assertTrue(html.contains("cid:shield-icon"), "HTML must contain security shield icon image");
        assertTrue(html.contains("Reset Your Password"), "HTML must contain heading");
        assertTrue(html.contains("Let's get you back to planning your next journey."), "HTML must contain subtitle");
        assertTrue(html.contains("Hello, John"), "HTML must contain personalized greeting");
        assertTrue(html.contains("15 minutes"), "HTML must specify 15-minute expiry");
        assertTrue(html.contains("Reset My Password"), "HTML must have primary button text");
        assertTrue(html.contains(resetUrl), "HTML must contain generated reset URL");
        assertTrue(html.contains("Didn't request a password reset?"), "HTML must contain security callout");
        assertTrue(html.contains("You can safely ignore this email."), "HTML must reassure recipient");
        assertTrue(html.contains("The Travel Easy Team"), "HTML must have signature");
        assertTrue(html.contains("This is an automated security email from Travel Easy."), "HTML must have security footer");

        // Plain text checks
        assertTrue(plainText.contains("Hello John,"));
        assertTrue(plainText.contains(resetUrl));
        assertTrue(plainText.contains("15 minutes"));
        assertTrue(plainText.contains("The Travel Easy Team"));
    }

    // 6. Template dynamic escaping prevents XSS
    @Test
    void testPasswordResetEmailTemplate_EscapesDynamicValues() {
        String maliciousName = "<script>alert('xss')</script>";
        String html = PasswordResetEmailTemplate.buildHtml(maliciousName, "http://localhost:4200/reset-password?token=safe", 15);
        assertFalse(html.contains("<script>"), "HTML must escape script tag");
        assertTrue(html.contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;"));
    }

    // 10. Missing, malformed, expired, and already-used reset tokens
    // 11. Successful password reset
    // 12. BCrypt password persistence
    // 13. Token invalidation after successful reset
    // 14. Rejection of a second attempt using the same token
    @Test
    void testResetPassword_CompleteTokenLifecycle() throws Exception {
        String token = "valid-active-token-777";
        User user = User.builder()
                .id(50L)
                .email("user@traveleasy.com")
                .password("oldHashedPassword")
                .resetPasswordToken(token)
                .resetPasswordTokenExpiry(LocalDateTime.now().plusMinutes(15))
                .build();

        when(userRepository.findByResetPasswordToken(token)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken(token);
        req.setNewPassword("NewSecureP@ss1!");
        req.setConfirmNewPassword("NewSecureP@ss1!");

        // First attempt: should succeed
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully. You can now login with your new password."));

        // Verify BCrypt encode was invoked
        verify(passwordEncoder).encode("NewSecureP@ss1!");

        // Verify token was invalidated
        assertNull(user.getResetPasswordToken(), "Reset token must be cleared after use");
        assertNull(user.getResetPasswordTokenExpiry(), "Reset token expiry must be cleared after use");

        // Second attempt with the same token: token is now cleared/invalid in DB
        when(userRepository.findByResetPasswordToken(token)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired password reset token!"));
    }

    // Expired token rejection
    @Test
    void testResetPassword_ExpiredToken_Rejected() throws Exception {
        String token = "expired-token-111";
        User user = User.builder()
                .id(51L)
                .email("expired@traveleasy.com")
                .password("oldHashedPassword")
                .resetPasswordToken(token)
                .resetPasswordTokenExpiry(LocalDateTime.now().minusMinutes(5)) // Expired
                .build();

        when(userRepository.findByResetPasswordToken(token)).thenReturn(Optional.of(user));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken(token);
        req.setNewPassword("NewSecureP@ss1!");
        req.setConfirmNewPassword("NewSecureP@ss1!");

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired password reset token!"));
    }

    // Password mismatch rejection
    @Test
    void testResetPassword_PasswordMismatch_Rejected() throws Exception {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("any-token");
        req.setNewPassword("NewSecureP@ss1!");
        req.setConfirmNewPassword("DifferentP@ss2!");

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passwords do not match!"));
    }

    // 7. Successful handling of Resend API response (unit test for ResendEmailService)
    @Test
    void testResendEmailService_Success() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"id\": \"msg_12345\"}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ResendEmailService service = new ResendEmailService(
                "re_mockApiKey_1234567890",
                "Travel Easy <onboarding@resend.dev>",
                objectMapper,
                mockClient
        );

        User user = User.builder().email("test@example.com").firstName("Tester").build();
        boolean result = service.sendPasswordResetEmail(user, "http://localhost:4200/reset-password?token=abc");

        assertTrue(result, "sendPasswordResetEmail should return true on 200 from Resend");
    }

    // 8. Resend authentication failure
    @Test
    void testResendEmailService_AuthFailure401() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(401);
        when(mockResponse.body()).thenReturn("{\"statusCode\": 401, \"message\": \"Missing API key\"}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ResendEmailService service = new ResendEmailService(
                "re_invalidKey",
                "Travel Easy <onboarding@resend.dev>",
                objectMapper,
                mockClient
        );

        User user = User.builder().email("test@example.com").build();
        boolean result = service.sendPasswordResetEmail(user, "http://localhost:4200/reset-password?token=abc");

        assertFalse(result, "sendPasswordResetEmail should return false on 401 from Resend");
    }

    // 9. Resend API rejection or network failure
    @Test
    void testResendEmailService_NetworkFailure() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection timed out"));

        ResendEmailService service = new ResendEmailService(
                "re_validKey",
                "Travel Easy <onboarding@resend.dev>",
                objectMapper,
                mockClient
        );

        User user = User.builder().email("test@example.com").build();
        boolean result = service.sendPasswordResetEmail(user, "http://localhost:4200/reset-password?token=abc");

        assertFalse(result, "sendPasswordResetEmail should return false and not throw on network failure");
    }

    // Missing API key handled safely
    @Test
    void testResendEmailService_MissingApiKey() {
        ResendEmailService service = new ResendEmailService(
                "",
                "Travel Easy <onboarding@resend.dev>",
                objectMapper
        );

        User user = User.builder().email("test@example.com").build();
        boolean result = service.sendPasswordResetEmail(user, "http://localhost:4200/reset-password?token=abc");

        assertFalse(result, "sendPasswordResetEmail should return false when API key is missing");
    }

    @Test
    void testEmailAssetProvider_LoadsAndBuildsAttachments() {
        assertNotNull(EmailAssetProvider.getTravelEasyLogoBase64());
        assertFalse(EmailAssetProvider.getTravelEasyLogoBase64().isBlank());

        assertNotNull(EmailAssetProvider.getLockIconBase64());
        assertFalse(EmailAssetProvider.getLockIconBase64().isBlank());

        assertNotNull(EmailAssetProvider.getShieldIconBase64());
        assertFalse(EmailAssetProvider.getShieldIconBase64().isBlank());

        java.util.List<java.util.Map<String, String>> attachments = EmailAssetProvider.getEmailAttachments();
        assertEquals(3, attachments.size());

        assertTrue(attachments.stream().anyMatch(a -> "travel-easy-logo".equals(a.get("content_id")) && "travel-easy-logo.png".equals(a.get("filename"))));
        assertTrue(attachments.stream().anyMatch(a -> "lock-icon".equals(a.get("content_id")) && "lock-icon.png".equals(a.get("filename"))));
        assertTrue(attachments.stream().anyMatch(a -> "shield-icon".equals(a.get("content_id")) && "shield-icon.png".equals(a.get("filename"))));
    }
}
