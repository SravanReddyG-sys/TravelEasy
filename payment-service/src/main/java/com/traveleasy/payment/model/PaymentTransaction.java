package com.traveleasy.payment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String transactionRef;

    private String bookingRef;
    private Long customerId;
    private Double amount;
    
    @Builder.Default
    private String currency = "INR";
    
    private String paymentMethod; // CARD, UPI, NET_BANKING, WALLET, GATEWAY
    private String status; // CREATED, INITIATED, SUCCESS, FAILED, REFUND_INITIATED, REFUNDED

    // Razorpay Integration Attributes
    @Column(length = 100)
    private String razorpayOrderId;

    @Column(length = 100)
    private String razorpayPaymentId;

    @Column(length = 255)
    private String razorpaySignature;

    // Refund Attributes
    @Column(length = 100)
    private String refundId;

    @Column(length = 50)
    private String refundStatus;

    private Double refundAmount;
    private LocalDateTime refundedAt;

    // Idempotency Key
    @Column(length = 100)
    private String idempotencyKey;

    // Error Auditing
    @Column(length = 100)
    private String errorCode;

    @Column(length = 500)
    private String errorDescription;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
