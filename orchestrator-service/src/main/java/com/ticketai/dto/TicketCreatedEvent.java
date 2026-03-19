package com.ticketai.dto;

import java.time.Instant;
import java.util.UUID;

public record TicketCreatedEvent(
        UUID ticketId,
        String title,
        String description,
        Instant occurredAt
) {}
