package com.traveleasy.payment.repository;

import com.traveleasy.payment.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByBookingRef(String bookingRef);
    Optional<PaymentTransaction> findByTransactionRef(String transactionRef);
}
