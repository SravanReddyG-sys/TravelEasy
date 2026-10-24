package com.traveleasy.notification.repository;

import com.traveleasy.notification.model.NewsletterSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NewsletterSubscriptionRepository extends JpaRepository<NewsletterSubscription, Long> {
    Optional<NewsletterSubscription> findByEmail(String email);
    boolean existsByEmail(String email);
}
