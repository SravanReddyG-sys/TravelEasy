package com.traveleasy.payment.service;

import com.traveleasy.payment.dto.*;
import com.traveleasy.payment.model.PaymentTransaction;

import java.util.Map;

public interface PaymentService {
    CreateOrderResponse createOrder(CreateOrderRequest request);
    PaymentVerificationResponse verifyPayment(VerifyPaymentRequest request);
    void processWebhook(String rawPayload, String signatureHeader);
    RefundResponse processRefund(String transactionRef, RefundRequest request);
    RefundResponse processRefundByBooking(String bookingRef, RefundRequest request);
    PaymentTransaction getPaymentByBooking(String bookingRef);
    PaymentTransaction getPaymentByTransactionRef(String transactionRef);
    Map<String, String> getPublicConfig();
    PaymentTransaction processLegacyPayment(PaymentTransaction transaction);
}
