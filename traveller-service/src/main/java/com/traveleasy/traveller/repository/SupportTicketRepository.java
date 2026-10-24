package com.traveleasy.traveller.repository;

import com.traveleasy.traveller.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<SupportTicket> findByIdAndUserId(Long id, Long userId);
    long countByUserIdAndStatus(Long userId, String status);
    long countByUserId(Long userId);
}
