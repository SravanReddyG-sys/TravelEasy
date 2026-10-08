package com.traveleasy.review.controller;

import com.traveleasy.review.model.Review;
import com.traveleasy.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewRepository reviewRepository;

    @PostMapping
    public ResponseEntity<Review> submitReview(@RequestBody Review review) {
        Review saved = reviewRepository.save(review);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/service/{serviceType}/{serviceId}")
    public ResponseEntity<List<Review>> getServiceReviews(@PathVariable String serviceType, @PathVariable Long serviceId) {
        return ResponseEntity.ok(reviewRepository.findByServiceTypeAndServiceIdOrderByCreatedAtDesc(serviceType.toUpperCase(), serviceId));
    }

    @GetMapping("/admin/all")
    public ResponseEntity<List<Review>> getAllReviews() {
        return ResponseEntity.ok(reviewRepository.findAll());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(@PathVariable Long id) {
        return reviewRepository.findById(id).map(r -> {
            reviewRepository.delete(r);
            return ResponseEntity.ok(Map.of("message", "Review deleted by moderator"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
