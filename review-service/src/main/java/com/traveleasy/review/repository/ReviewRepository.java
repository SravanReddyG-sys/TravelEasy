package com.traveleasy.review.repository;

import com.traveleasy.review.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByServiceTypeAndServiceIdOrderByCreatedAtDesc(String serviceType, Long serviceId);
    List<Review> findByCustomerId(Long customerId);
}
