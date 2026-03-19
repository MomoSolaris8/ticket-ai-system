package com.ticketai.service;

import com.ticketai.domain.Ticket;
import com.ticketai.dto.CreateTicketRequest;
import com.ticketai.dto.EnrichTicketRequest;
import com.ticketai.event.TicketCreatedEvent;
import com.ticketai.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final String TOPIC_TICKET_CREATED = "ticket.created";

    private final TicketRepository ticketRepository;
    private final KafkaTemplate<String, TicketCreatedEvent> kafkaTemplate;

    public TicketService(TicketRepository ticketRepository,
                         KafkaTemplate<String, TicketCreatedEvent> kafkaTemplate) {
        this.ticketRepository = ticketRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public Ticket createTicket(CreateTicketRequest request) {
        Ticket ticket = new Ticket();
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        final Ticket savedTicket = ticketRepository.save(ticket);

        // Publish domain event after successful DB write
        TicketCreatedEvent event = TicketCreatedEvent.of(
                savedTicket.getId(), savedTicket.getTitle(), savedTicket.getDescription()
        );
        kafkaTemplate.send(TOPIC_TICKET_CREATED, savedTicket.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish TicketCreatedEvent for ticketId={}", savedTicket.getId(), ex);
                    } else {
                        log.info("Published TicketCreatedEvent ticketId={} offset={}",
                                savedTicket.getId(), result.getRecordMetadata().offset());
                    }
                });

        return savedTicket;
    }

    @Transactional
    public Ticket applyEnrichment(UUID ticketId, EnrichTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        ticket.markEnriched(request.category(), request.priority(), request.summary());
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket markManualReview(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        ticket.markNeedsManualReview();
        return ticketRepository.save(ticket);
    }

    public Ticket findById(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    public List<Ticket> findAll() {
        return ticketRepository.findAll();
    }
}
