package com.ticketai.repository;

import com.ticketai.domain.Ticket;
import com.ticketai.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByStatus(TicketStatus status);
}
