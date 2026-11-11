package com.traveleasy.review.controller;

import com.traveleasy.review.dto.PendingReviewResponse;
import com.traveleasy.review.dto.ReviewStatsResponse;
import com.traveleasy.review.model.Review;
import com.traveleasy.review.repository.ReviewRepository;
import com.traveleasy.review.service.ReviewSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ReviewSecurityService securityService;

    /**
     * Helper to validate administrator credentials from Authorization Bearer token.
     * Returns null if authorized as ROLE_ADMIN, or an appropriate 401/403 ResponseEntity.
     */
    private ResponseEntity<?> validateAdminAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authorization Bearer token is required"));
        }
        String token = authHeader.substring(7).trim();
        if (!securityService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired authorization token"));
        }
        try {
            String role = securityService.extractRole(token);
            if (!"ROLE_ADMIN".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied: Administrator role required"));
            }
        } catch (Exception ex) {
            log.warn("Token parsing error during review admin authorization: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Malformed authorization token"));
        }
        return null;
    }

    /**
     * Public / Customer endpoint to submit a review.
     */
    @PostMapping
    public ResponseEntity<Review> submitReview(@RequestBody Review review) {
        Review saved = reviewRepository.save(review);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Public / Customer endpoint to retrieve reviews for a specific bus schedule or hotel property.
     */
    @GetMapping("/service/{serviceType}/{serviceId}")
    public ResponseEntity<List<Review>> getServiceReviews(@PathVariable String serviceType, @PathVariable Long serviceId) {
        return ResponseEntity.ok(reviewRepository.findByServiceTypeAndServiceIdOrderByCreatedAtDesc(serviceType.toUpperCase(), serviceId));
    }

    /**
     * Administrative endpoint to list all platform reviews. Requires ROLE_ADMIN.
     */
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllReviews(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }
        return ResponseEntity.ok(reviewRepository.findAll());
    }

    /**
     * Administrative endpoint to retrieve platform-wide review statistics.
     * Requires ROLE_ADMIN.
     * Uses database-level count queries and accurately reflects the reactive moderation model.
     */
    @GetMapping("/admin/stats")
    public ResponseEntity<?> getReviewStats(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }

        long total = reviewRepository.count();
        // In TravelEasy's reactive moderation model:
        // - All persisted reviews are live/approved (approvedReviews = totalReviews)
        // - There is no pre-publication queue (pendingReviews = 0)
        // - Rejected reviews are permanently deleted by moderators (rejectedReviews = 0)
        ReviewStatsResponse stats = ReviewStatsResponse.builder()
                .totalReviews(total)
                .pendingReviews(0L)
                .approvedReviews(total)
                .rejectedReviews(0L)
                .build();

        return ResponseEntity.ok(stats);
    }

    /**
     * Administrative endpoint to retrieve pending reviews awaiting moderation.
     * Requires ROLE_ADMIN.
     * In the existing reactive moderation model, reviews are published upon submission.
     * A pre-publication queue does not exist, so an empty paginated result is returned
     * with validated and bounded pagination parameters.
     */
    @GetMapping("/admin/pending")
    public ResponseEntity<?> getPendingReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }

        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PendingReviewResponse> emptyPendingPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        return ResponseEntity.ok(emptyPendingPage);
    }

    private PendingReviewResponse mapToPendingResponse(Review r) {
        return PendingReviewResponse.builder()
                .id(r.getId())
                .customerName(r.getCustomerName())
                .customerId(r.getCustomerId())
                .serviceType(r.getServiceType())
                .serviceId(r.getServiceId())
                .bookingRef(r.getBookingRef())
                .rating(r.getRating())
                .title(null) // Review title is not supported in the existing model
                .comment(r.getComment())
                .createdAt(r.getCreatedAt())
                .status(null) // No pre-publication moderation status in reactive moderation
                .build();
    }

    /**
     * Administrative endpoint to delete/moderate a review. Requires ROLE_ADMIN.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(@PathVariable Long id,
                                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> authError = validateAdminAuth(authHeader);
        if (authError != null) {
            return authError;
        }

        return reviewRepository.findById(id).map(r -> {
            reviewRepository.delete(r);
            return ResponseEntity.ok(Map.of("message", "Review deleted by moderator"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
