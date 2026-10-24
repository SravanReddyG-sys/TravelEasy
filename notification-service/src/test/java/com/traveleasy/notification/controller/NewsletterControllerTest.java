package com.traveleasy.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.notification.model.NewsletterSubscription;
import com.traveleasy.notification.repository.NewsletterSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class NewsletterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NewsletterSubscriptionRepository subscriptionRepository;

    @Test
    void testSubscribe_ValidEmail_Success() throws Exception {
        when(subscriptionRepository.findByEmail("traveller@traveleasy.com")).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(NewsletterSubscription.class))).thenAnswer(inv -> {
            NewsletterSubscription s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        mockMvc.perform(post("/api/newsletter/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "  traveller@traveleasy.com  "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("You're subscribed to Travel Easy updates!"))
                .andExpect(jsonPath("$.email").value("traveller@traveleasy.com"));
    }

    @Test
    void testSubscribe_AlreadySubscribed() throws Exception {
        NewsletterSubscription existing = NewsletterSubscription.builder()
                .id(2L)
                .email("existing@traveleasy.com")
                .status("ACTIVE")
                .subscribedAt(LocalDateTime.now())
                .build();

        when(subscriptionRepository.findByEmail("existing@traveleasy.com")).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/api/newsletter/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "existing@traveleasy.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_SUBSCRIBED"))
                .andExpect(jsonPath("$.message").value("This email is already subscribed."));
    }

    @Test
    void testSubscribe_PreviouslyUnsubscribed() throws Exception {
        NewsletterSubscription unsubscribed = NewsletterSubscription.builder()
                .id(3L)
                .email("unsub@traveleasy.com")
                .status("UNSUBSCRIBED")
                .subscribedAt(LocalDateTime.now().minusDays(30))
                .unsubscribedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(subscriptionRepository.findByEmail("unsub@traveleasy.com")).thenReturn(Optional.of(unsubscribed));

        mockMvc.perform(post("/api/newsletter/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "unsub@traveleasy.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("UNSUBSCRIBED"))
                .andExpect(jsonPath("$.message").value("This email was previously unsubscribed. Please contact support to reactivate your subscription."));
    }

    @Test
    void testSubscribe_InvalidEmail() throws Exception {
        mockMvc.perform(post("/api/newsletter/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("Please enter a valid email address."));
    }

    @Test
    void testSubscribe_EmptyEmail() throws Exception {
        mockMvc.perform(post("/api/newsletter/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("Please enter a valid email address."));
    }

    @Test
    void testSubscribe_ConcurrentDuplicateProtection() throws Exception {
        when(subscriptionRepository.findByEmail("concurrent@traveleasy.com")).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(NewsletterSubscription.class))).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        mockMvc.perform(post("/api/newsletter/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "concurrent@traveleasy.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_SUBSCRIBED"))
                .andExpect(jsonPath("$.message").value("This email is already subscribed."));
    }

    @Test
    void testGetSubscriptionStatus_Found() throws Exception {
        NewsletterSubscription sub = NewsletterSubscription.builder()
                .id(10L)
                .email("subscriber@traveleasy.com")
                .status("ACTIVE")
                .subscribedAt(LocalDateTime.now())
                .build();

        when(subscriptionRepository.findByEmail("subscriber@traveleasy.com")).thenReturn(Optional.of(sub));

        mockMvc.perform(get("/api/newsletter/status?email=subscriber@traveleasy.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("subscriber@traveleasy.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
