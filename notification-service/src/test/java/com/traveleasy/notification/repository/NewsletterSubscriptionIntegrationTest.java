package com.traveleasy.notification.repository;

import com.traveleasy.notification.model.NewsletterSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class NewsletterSubscriptionIntegrationTest {

    @Autowired
    private NewsletterSubscriptionRepository repository;

    private final String testEmail = "persist_test_" + System.currentTimeMillis() + "@traveleasy.com";

    @BeforeEach
    void setUp() {
        repository.findByEmail(testEmail).ifPresent(repository::delete);
    }

    @Test
    void testSaveAndRetrieveSubscription() {
        NewsletterSubscription sub = NewsletterSubscription.builder()
                .email(testEmail)
                .status("ACTIVE")
                .subscribedAt(LocalDateTime.now())
                .build();

        NewsletterSubscription saved = repository.save(sub);
        assertNotNull(saved.getId(), "Generated ID should not be null");
        assertEquals(testEmail, saved.getEmail());
        assertEquals("ACTIVE", saved.getStatus());
        assertNotNull(saved.getSubscribedAt());

        // Retrieve from database
        Optional<NewsletterSubscription> found = repository.findByEmail(testEmail);
        assertTrue(found.isPresent(), "Subscription should be found in MySQL database");
        assertEquals("ACTIVE", found.get().getStatus());
    }

    @Test
    void testUniqueEmailConstraintEnforced() {
        String uniqueTestEmail = "duplicate_test_" + System.currentTimeMillis() + "@traveleasy.com";

        NewsletterSubscription sub1 = NewsletterSubscription.builder()
                .email(uniqueTestEmail)
                .status("ACTIVE")
                .subscribedAt(LocalDateTime.now())
                .build();
        repository.save(sub1);

        NewsletterSubscription sub2 = NewsletterSubscription.builder()
                .email(uniqueTestEmail)
                .status("ACTIVE")
                .subscribedAt(LocalDateTime.now())
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.saveAndFlush(sub2);
        }, "Database must reject duplicate emails via unique constraint");
    }
}
