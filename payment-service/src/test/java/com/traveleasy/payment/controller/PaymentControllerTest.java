package com.traveleasy.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveleasy.payment.dto.*;
import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.service.PaymentService;
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
}
