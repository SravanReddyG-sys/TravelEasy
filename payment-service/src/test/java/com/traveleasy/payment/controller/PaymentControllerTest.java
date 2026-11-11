package com.traveleasy.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.payment.dto.*;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.service.PaymentSecurityService;
import com.traveleasy.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
public class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private PaymentSecurityService securityService;

    @Test
    @DisplayName("GET /api/payments/config - Returns public Razorpay key ID")
    void testGetPublicConfig() throws Exception {
        when(paymentService.getPublicConfig()).thenReturn(Map.of(
                "keyId", "rzp_test_public_123",
                "currency", "INR",
                "configured", "true"
        ));

        mockMvc.perform(get("/api/payments/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId").value("rzp_test_public_123"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.configured").value("true"));
    }

    @Test
    @DisplayName("POST /api/payments/create-order - Returns created order details")
    void testCreateOrder() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .bookingRef("BK-CTRL-1")
                .amount(1200.0)
                .customerId(10L)
                .build();

        CreateOrderResponse response = CreateOrderResponse.builder()
                .orderId("order_ctrl_999")
                .transactionRef("TXN-CTRL-1")
                .bookingRef("BK-CTRL-1")
                .amount(1200.0)
                .amountInPaise(120000L)
                .currency("INR")
                .keyId("rzp_test_ctrl")
                .status("CREATED")
                .build();

        when(paymentService.createOrder(any(CreateOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("order_ctrl_999"))
                .andExpect(jsonPath("$.bookingRef").value("BK-CTRL-1"))
                .andExpect(jsonPath("$.amountInPaise").value(120000))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @DisplayName("POST /api/payments/verify - Returns verification result")
    void testVerifyPayment() throws Exception {
        VerifyPaymentRequest request = VerifyPaymentRequest.builder()
                .razorpayOrderId("order_ctrl_999")
                .razorpayPaymentId("pay_ctrl_999")
                .razorpaySignature("sig_ctrl_999")
                .bookingRef("BK-CTRL-1")
                .build();

        PaymentVerificationResponse response = PaymentVerificationResponse.builder()
                .verified(true)
                .status("SUCCESS")
                .bookingRef("BK-CTRL-1")
                .paymentId("pay_ctrl_999")
                .build();

        when(paymentService.verifyPayment(any(VerifyPaymentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentId").value("pay_ctrl_999"));
    }

    @Test
    @DisplayName("POST /api/payments/webhook - Delegates to processWebhook with signature header")
    void testProcessWebhook() throws Exception {
        String payload = "{\"event\":\"payment.captured\"}";
        String signature = "test_sig_header";

        doNothing().when(paymentService).processWebhook(payload, signature);

        mockMvc.perform(post("/api/payments/webhook")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));

        verify(paymentService).processWebhook(payload, signature);
    }

    @Test
    @DisplayName("POST /api/payments/refund/{transactionRef} - Processes refund")
    void testProcessRefund() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .amount(1200.0)
                .reason("Customer requested refund")
                .build();

        RefundResponse response = RefundResponse.builder()
                .refundId("rfnd_ctrl_1")
                .transactionRef("TXN-CTRL-1")
                .bookingRef("BK-CTRL-1")
                .refundAmount(1200.0)
                .refundStatus("PROCESSED")
                .build();

        when(paymentService.processRefund(eq("TXN-CTRL-1"), any(RefundRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/payments/refund/TXN-CTRL-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value("rfnd_ctrl_1"))
                .andExpect(jsonPath("$.refundStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.refundAmount").value(1200.0));
    }

    @Test
    @DisplayName("GET /api/payments/booking/{bookingRef} - Returns transaction by booking reference")
    void testGetPaymentByBookingRef() throws Exception {
        PaymentTransaction txn = PaymentTransaction.builder()
                .transactionRef("TXN-BK-1")
                .bookingRef("BK-100")
                .status("SUCCESS")
                .amount(850.0)
                .build();

        when(paymentService.getPaymentByBooking("BK-100")).thenReturn(txn);

        mockMvc.perform(get("/api/payments/booking/BK-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionRef").value("TXN-BK-1"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("Unit 3.3: POST /api/payments/refund/booking/{bookingRef} - Passes X-Idempotency-Key to service")
    void testProcessRefundByBooking_WithIdempotencyHeader() throws Exception {
        RefundRequest request = RefundRequest.builder()
                .amount(1500.0)
                .reason("Cancelled by operator")
                .build();

        RefundResponse response = RefundResponse.builder()
                .refundId("rfnd_ctrl_idemp_1")
                .transactionRef("TXN-CTRL-IDEMP")
                .bookingRef("BK-CTRL-IDEMP")
                .refundAmount(1500.0)
                .refundStatus("PROCESSED")
                .build();

        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        when(paymentService.processRefundByBooking(eq("BK-CTRL-IDEMP"), captor.capture())).thenReturn(response);

        mockMvc.perform(post("/api/payments/refund/booking/BK-CTRL-IDEMP")
                        .header("X-Idempotency-Key", "rfnd_BK-CTRL-IDEMP_99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value("rfnd_ctrl_idemp_1"))
                .andExpect(jsonPath("$.refundStatus").value("PROCESSED"));

        RefundRequest passedRequest = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("rfnd_BK-CTRL-IDEMP_99", passedRequest.getIdempotencyKey());
    }

    // ========================================================================
    // WP-04: Admin Endpoints Security & Functional Tests
    // ========================================================================

    @Test
    @DisplayName("WP-04: GET /api/payments/admin/stats - Missing Token returns 401")
    void getAdminStats_MissingToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/payments/admin/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authorization token required"));
    }

    @Test
    @DisplayName("WP-04: GET /api/payments/admin/stats - Invalid Token returns 401")
    void getAdminStats_InvalidToken_Returns401() throws Exception {
        when(securityService.validateToken("invalid.token")).thenReturn(false);

        mockMvc.perform(get("/api/payments/admin/stats")
                        .header("Authorization", "Bearer invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired authorization token"));
    }

    @Test
    @DisplayName("WP-04: GET /api/payments/admin/stats - Role Customer returns 403")
    void getAdminStats_CustomerRole_Returns403() throws Exception {
        when(securityService.validateToken("cust.token")).thenReturn(true);
        when(securityService.extractRole("cust.token")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/payments/admin/stats")
                        .header("Authorization", "Bearer cust.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Administrator authorization required"));
    }

    @Test
    @DisplayName("WP-04: GET /api/payments/admin/stats - Role Bus Operator returns 403")
    void getAdminStats_BusOperatorRole_Returns403() throws Exception {
        when(securityService.validateToken("bus.token")).thenReturn(true);
        when(securityService.extractRole("bus.token")).thenReturn("ROLE_BUS_OPERATOR");

        mockMvc.perform(get("/api/payments/admin/stats")
                        .header("Authorization", "Bearer bus.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Administrator authorization required"));
    }

    @Test
    @DisplayName("WP-04: GET /api/payments/admin/stats - Role Hotel Operator returns 403")
    void getAdminStats_HotelOperatorRole_Returns403() throws Exception {
        when(securityService.validateToken("hotel.token")).thenReturn(true);
        when(securityService.extractRole("hotel.token")).thenReturn("ROLE_HOTEL_OPERATOR");

        mockMvc.perform(get("/api/payments/admin/stats")
                        .header("Authorization", "Bearer hotel.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Administrator authorization required"));
    }

    @Test
    @DisplayName("WP-04: GET /api/payments/admin/stats - Role Admin returns 200 with stats")
    void getAdminStats_AdminRole_Returns200WithStats() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");

        PaymentStatsResponse stats = PaymentStatsResponse.builder()
                .currency("INR")
                .successfulCapturedCollections(BigDecimal.valueOf(1250000.00).setScale(2))
                .processedRefunds(BigDecimal.valueOf(85000.00).setScale(2))
                .netCollections(BigDecimal.valueOf(1165000.00).setScale(2))
                .build();

        when(paymentService.getAdminStats()).thenReturn(stats);

        mockMvc.perform(get("/api/payments/admin/stats")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.successfulCapturedCollections").value(1250000.00))
                .andExpect(jsonPath("$.processedRefunds").value(85000.00))
                .andExpect(jsonPath("$.netCollections").value(1165000.00));
    }

    @Test
    @DisplayName("WP-04: GET /api/payments/admin/revenue-trends - Missing Token returns 401")
    void getAdminRevenueTrends_MissingToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/payments/admin/revenue-trends?days=7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authorization token required"));
    }

    @Test
    @DisplayName("WP-04: GET /api/payments/admin/revenue-trends - Role Customer returns 403")
    void getAdminRevenueTrends_CustomerRole_Returns403() throws Exception {
        when(securityService.validateToken("cust.token")).thenReturn(true);
        when(securityService.extractRole("cust.token")).thenReturn("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/payments/admin/revenue-trends?days=7")
                        .header("Authorization", "Bearer cust.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Administrator authorization required"));
    }

    @Test
    @DisplayName("WP-04: GET /api/payments/admin/revenue-trends - Role Admin returns 200 with daily trends including negative net")
    void getAdminRevenueTrends_AdminRole_Returns200WithTrends() throws Exception {
        when(securityService.validateToken("admin.token")).thenReturn(true);
        when(securityService.extractRole("admin.token")).thenReturn("ROLE_ADMIN");

        List<DailyRevenueTrendResponse> trends = List.of(
                DailyRevenueTrendResponse.builder()
                        .date(LocalDate.of(2026, 11, 1))
                        .capturedCollections(BigDecimal.valueOf(10000.00).setScale(2))
                        .processedRefunds(BigDecimal.valueOf(1000.00).setScale(2))
                        .netCollections(BigDecimal.valueOf(9000.00).setScale(2))
                        .currency("INR")
                        .build(),
                DailyRevenueTrendResponse.builder()
                        .date(LocalDate.of(2026, 11, 2))
                        .capturedCollections(BigDecimal.valueOf(2000.00).setScale(2))
                        .processedRefunds(BigDecimal.valueOf(5000.00).setScale(2))
                        .netCollections(BigDecimal.valueOf(-3000.00).setScale(2)) // Negative net collections preserved!
                        .currency("INR")
                        .build()
        );

        when(paymentService.getAdminRevenueTrends(7)).thenReturn(trends);

        mockMvc.perform(get("/api/payments/admin/revenue-trends?days=7")
                        .header("Authorization", "Bearer admin.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-11-01"))
                .andExpect(jsonPath("$[0].capturedCollections").value(10000.00))
                .andExpect(jsonPath("$[0].processedRefunds").value(1000.00))
                .andExpect(jsonPath("$[0].netCollections").value(9000.00))
                .andExpect(jsonPath("$[1].date").value("2026-11-02"))
                .andExpect(jsonPath("$[1].capturedCollections").value(2000.00))
                .andExpect(jsonPath("$[1].processedRefunds").value(5000.00))
                .andExpect(jsonPath("$[1].netCollections").value(-3000.00));
    }

    @Test
    @DisplayName("GET /api/payments/refund/booking/{bookingRef}/reconcile - Rejects unauthorized and serves valid booking-service internal token")
    void testReconcileRefundByBooking_SecurityAndDelegation() throws Exception {
        when(securityService.verifyStrictInternalToken(any(), any(), any(), eq("booking-service")))
                .thenReturn(false)
                .thenReturn(true);

        mockMvc.perform(get("/api/payments/refund/booking/BK-REC-1/reconcile")
                        .header("X-Internal-Service", "bus-service"))
                .andExpect(status().isUnauthorized());

        RefundResponse reconciled = RefundResponse.builder()
                .refundId("rfnd_rec_1")
                .refundStatus("PROCESSED")
                .refundAmount(1800.0)
                .bookingRef("BK-REC-1")
                .transactionRef("TXN-REC-1")
                .build();
        when(paymentService.reconcileRefundByBooking("BK-REC-1", "htl-rfnd-BK-REC-1", 1800.0))
                .thenReturn(reconciled);

        mockMvc.perform(get("/api/payments/refund/booking/BK-REC-1/reconcile")
                        .param("idempotencyKey", "htl-rfnd-BK-REC-1")
                        .param("expectedAmount", "1800.0")
                        .header("X-Internal-Token", "valid-booking-jwt")
                        .header("X-Internal-Service", "booking-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.refundId").value("rfnd_rec_1"))
                .andExpect(jsonPath("$.refundAmount").value(1800.0));
    }
}
