package com.traveleasy.review.controller;

import com.traveleasy.review.model.Review;
import com.traveleasy.review.repository.ReviewRepository;
import com.traveleasy.review.service.ReviewSecurityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ReviewControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewRepository reviewRepository;

    @MockBean
    private ReviewSecurityService securityService;

    // ==========================================
    // GET /api/reviews/admin/all
    // ==========================================

    @Test
    @DisplayName("GET /api/reviews/admin/all - Without Token returns 401 Unauthorized")
    void getAllReviews_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/reviews/admin/all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authorization Bearer token is required"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/all - Invalid Token returns 401 Unauthorized")
    void getAllReviews_InvalidToken_Returns401() throws Exception {
        when(securityService.validateToken("invalid.token")).thenReturn(false);

        mockMvc.perform(get("/api/reviews/admin/all")
                        .header("Authorization", "Bearer invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired authorization token"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/all - As Customer returns 403 Forbidden")
    void getAllReviews_AsCustomer_Returns403() throws Exception {
        when(securityService.validateToken("customer.token")).thenReturn(true);
        when(securityService.extractRole("customer.token")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/reviews/admin/all")
                        .header("Authorization", "Bearer customer.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/all - As Bus Operator returns 403 Forbidden")
    void getAllReviews_AsBusOperator_Returns403() throws Exception {
        when(securityService.validateToken("operator.token")).thenReturn(true);
        when(securityService.extractRole("operator.token")).thenReturn("ROLE_BUS_OPERATOR");

        mockMvc.perform(get("/api/reviews/admin/all")
                        .header("Authorization", "Bearer operator.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/all - As Hotel Manager returns 403 Forbidden")
    void getAllReviews_AsHotelManager_Returns403() throws Exception {
        when(securityService.validateToken("manager.token")).thenReturn(true);
        when(securityService.extractRole("manager.token")).thenReturn("ROLE_HOTEL_MANAGER");

        mockMvc.perform(get("/api/reviews/admin/all")
                        .header("Authorization", "Bearer manager.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/all - As Admin returns 200 OK")
    void getAllReviews_AsAdmin_Returns200() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");

        Review r = Review.builder().id(1L).customerName("Rahul V").comment("Great service!").rating(5).build();
        when(reviewRepository.findAll()).thenReturn(List.of(r));

        mockMvc.perform(get("/api/reviews/admin/all")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerName").value("Rahul V"))
                .andExpect(jsonPath("$[0].rating").value(5));
    }

    // ==========================================
    // GET /api/reviews/admin/stats
    // ==========================================

    @Test
    @DisplayName("GET /api/reviews/admin/stats - Without Token returns 401 Unauthorized")
    void getReviewStats_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/reviews/admin/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authorization Bearer token is required"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/stats - Invalid Token returns 401 Unauthorized")
    void getReviewStats_InvalidToken_Returns401() throws Exception {
        when(securityService.validateToken("invalid.token")).thenReturn(false);

        mockMvc.perform(get("/api/reviews/admin/stats")
                        .header("Authorization", "Bearer invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired authorization token"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/stats - As Customer returns 403 Forbidden")
    void getReviewStats_AsCustomer_Returns403() throws Exception {
        when(securityService.validateToken("customer.token")).thenReturn(true);
        when(securityService.extractRole("customer.token")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/reviews/admin/stats")
                        .header("Authorization", "Bearer customer.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/stats - As Bus Operator returns 403 Forbidden")
    void getReviewStats_AsBusOperator_Returns403() throws Exception {
        when(securityService.validateToken("operator.token")).thenReturn(true);
        when(securityService.extractRole("operator.token")).thenReturn("ROLE_BUS_OPERATOR");

        mockMvc.perform(get("/api/reviews/admin/stats")
                        .header("Authorization", "Bearer operator.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/stats - As Hotel Manager returns 403 Forbidden")
    void getReviewStats_AsHotelManager_Returns403() throws Exception {
        when(securityService.validateToken("manager.token")).thenReturn(true);
        when(securityService.extractRole("manager.token")).thenReturn("ROLE_HOTEL_MANAGER");

        mockMvc.perform(get("/api/reviews/admin/stats")
                        .header("Authorization", "Bearer manager.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/stats - As Admin returns 200 OK with accurate counts")
    void getReviewStats_AsAdmin_Returns200() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");
        when(reviewRepository.count()).thenReturn(5L);

        mockMvc.perform(get("/api/reviews/admin/stats")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviews").value(5))
                .andExpect(jsonPath("$.pendingReviews").value(0))
                .andExpect(jsonPath("$.approvedReviews").value(5))
                .andExpect(jsonPath("$.rejectedReviews").value(0));
    }

    // ==========================================
    // GET /api/reviews/admin/pending
    // ==========================================

    @Test
    @DisplayName("GET /api/reviews/admin/pending - Without Token returns 401 Unauthorized")
    void getPendingReviews_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/reviews/admin/pending"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authorization Bearer token is required"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/pending - Invalid Token returns 401 Unauthorized")
    void getPendingReviews_InvalidToken_Returns401() throws Exception {
        when(securityService.validateToken("invalid.token")).thenReturn(false);

        mockMvc.perform(get("/api/reviews/admin/pending")
                        .header("Authorization", "Bearer invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired authorization token"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/pending - As Customer returns 403 Forbidden")
    void getPendingReviews_AsCustomer_Returns403() throws Exception {
        when(securityService.validateToken("customer.token")).thenReturn(true);
        when(securityService.extractRole("customer.token")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/reviews/admin/pending")
                        .header("Authorization", "Bearer customer.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/pending - As Bus Operator returns 403 Forbidden")
    void getPendingReviews_AsBusOperator_Returns403() throws Exception {
        when(securityService.validateToken("operator.token")).thenReturn(true);
        when(securityService.extractRole("operator.token")).thenReturn("ROLE_BUS_OPERATOR");

        mockMvc.perform(get("/api/reviews/admin/pending")
                        .header("Authorization", "Bearer operator.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/pending - As Hotel Manager returns 403 Forbidden")
    void getPendingReviews_AsHotelManager_Returns403() throws Exception {
        when(securityService.validateToken("manager.token")).thenReturn(true);
        when(securityService.extractRole("manager.token")).thenReturn("ROLE_HOTEL_MANAGER");

        mockMvc.perform(get("/api/reviews/admin/pending")
                        .header("Authorization", "Bearer manager.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/pending - As Admin returns 200 OK with empty paginated content")
    void getPendingReviews_AsAdmin_Returns200() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");

        mockMvc.perform(get("/api/reviews/admin/pending?page=0&size=10")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    @DisplayName("GET /api/reviews/admin/pending - Bounded pagination safely handles invalid params")
    void getPendingReviews_BoundedPagination_HandlesSafely() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");

        // Negative page (-5) clamped to 0, excessive size (500) clamped to 100
        mockMvc.perform(get("/api/reviews/admin/pending?page=-5&size=500")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.number").value(0));
    }

    // ==========================================
    // DELETE /api/reviews/{id}
    // ==========================================

    @Test
    @DisplayName("DELETE /api/reviews/{id} - Without Token returns 401 Unauthorized")
    void deleteReview_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(delete("/api/reviews/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authorization Bearer token is required"));
    }

    @Test
    @DisplayName("DELETE /api/reviews/{id} - As Customer returns 403 Forbidden")
    void deleteReview_AsCustomer_Returns403() throws Exception {
        when(securityService.validateToken("customer.token")).thenReturn(true);
        when(securityService.extractRole("customer.token")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(delete("/api/reviews/1")
                        .header("Authorization", "Bearer customer.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: Administrator role required"));
    }

    @Test
    @DisplayName("DELETE /api/reviews/{id} - As Admin deletes review and returns 200 OK")
    void deleteReview_AsAdmin_Returns200() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");

        Review r = Review.builder().id(1L).customerName("Rahul V").comment("Spam").rating(1).build();
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(r));
        doNothing().when(reviewRepository).delete(r);

        mockMvc.perform(delete("/api/reviews/1")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Review deleted by moderator"));
        verify(reviewRepository, times(1)).delete(r);
    }

    // ==========================================
    // Public / Customer Workflows Preserved
    // ==========================================

    @Test
    @DisplayName("POST /api/reviews - Customer can submit review without admin token (returns 201 Created)")
    void submitReview_Customer_Returns201() throws Exception {
        Review r = Review.builder().id(1L).serviceType("BUS").serviceId(10L).customerName("Sneha").rating(4).comment("Nice").build();
        when(reviewRepository.save(any(Review.class))).thenReturn(r);

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceType\":\"BUS\",\"serviceId\":10,\"customerName\":\"Sneha\",\"rating\":4,\"comment\":\"Nice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("GET /api/reviews/service/{type}/{id} - Customer can view reviews without token (returns 200 OK)")
    void getServiceReviews_Public_Returns200() throws Exception {
        Review r = Review.builder().id(1L).serviceType("BUS").serviceId(10L).customerName("Sneha").rating(4).comment("Nice").build();
        when(reviewRepository.findByServiceTypeAndServiceIdOrderByCreatedAtDesc("BUS", 10L)).thenReturn(List.of(r));

        mockMvc.perform(get("/api/reviews/service/bus/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceId").value(10));
    }
}
