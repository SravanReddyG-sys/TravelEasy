package com.traveleasy.traveller.service;

import com.traveleasy.traveller.dto.CreateSupportTicketRequest;
import com.traveleasy.traveller.dto.SupportTicketDto;
import com.traveleasy.traveller.model.SupportTicket;
import com.traveleasy.traveller.repository.SupportTicketRepository;
import com.traveleasy.traveller.security.TravellerPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TravellerSupportService {

    private final SupportTicketRepository supportTicketRepository;

    public List<SupportTicketDto> getTickets(TravellerPrincipal principal) {
        return supportTicketRepository.findByUserIdOrderByCreatedAtDesc(principal.getUserId())
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional
    public SupportTicketDto createTicket(TravellerPrincipal principal, CreateSupportTicketRequest request) {
        String ref = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String category = request.getCategory() != null ? request.getCategory().trim().toUpperCase() : "GENERAL";

        SupportTicket ticket = SupportTicket.builder()
                .userId(principal.getUserId())
                .ticketRef(ref)
                .category(category)
                .subject(request.getSubject() != null ? request.getSubject().trim() : "Help Request")
                .message(request.getMessage() != null ? request.getMessage().trim() : "")
                .status("OPEN")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return mapToDto(supportTicketRepository.save(ticket));
    }

    private SupportTicketDto mapToDto(SupportTicket t) {
        return SupportTicketDto.builder()
                .id(t.getId())
                .userId(t.getUserId())
                .ticketRef(t.getTicketRef())
                .category(t.getCategory())
                .subject(t.getSubject())
                .message(t.getMessage())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
