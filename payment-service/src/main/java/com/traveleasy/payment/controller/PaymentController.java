package com.traveleasy.payment.controller;

import com.traveleasy.payment.model.PaymentTransaction;
import com.traveleasy.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;

    @PostMapping("/process")
    public ResponseEntity<PaymentTransaction> processPayment(@RequestBody PaymentTransaction transaction) {
        transaction.setTransactionRef("TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        // Simulating 95% payment gateway success rate
        transaction.setStatus("SUCCESS");
        PaymentTransaction saved = paymentRepository.save(transaction);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/booking/{bookingRef}")
    public ResponseEntity<?> getPaymentByBooking(@PathVariable String bookingRef) {
        return paymentRepository.findByBookingRef(bookingRef)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/refund/{transactionRef}")
    public ResponseEntity<?> processRefund(@PathVariable String transactionRef) {
        return paymentRepository.findByTransactionRef(transactionRef).map(txn -> {
            txn.setStatus("REFUNDED");
            paymentRepository.save(txn);
            return ResponseEntity.ok(Map.of("message", "Refund processed successfully", "transaction", txn));
        }).orElse(ResponseEntity.notFound().build());
    }
}
