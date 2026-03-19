package com.ticketai.controller;

import com.ticketai.domain.Ticket;
import com.ticketai.dto.CreateTicketRequest;
import com.ticketai.dto.EnrichTicketRequest;
import com.ticketai.service.TicketNotFoundException;
import com.ticketai.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Ticket> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        Ticket ticket = ticketService.createTicket(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'AGENT', 'ADMIN')")
    public ResponseEntity<Ticket> getTicket(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<List<Ticket>> listTickets() {
        return ResponseEntity.ok(ticketService.findAll());
    }

    // Internal endpoint — called by orchestrator-service only
    @PatchMapping("/{id}/enrich")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<Ticket> enrichTicket(@PathVariable UUID id,
                                               @RequestBody EnrichTicketRequest request) {
        return ResponseEntity.ok(ticketService.applyEnrichment(id, request));
    }

    @PatchMapping("/{id}/manual-review")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<Ticket> markManualReview(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.markManualReview(id));
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<String> handleNotFound(TicketNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
