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
    private String paymentMethod; // CARD, UPI, NET_BANKING, WALLET
    private String status; // SUCCESS, FAILED, REFUNDED

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
