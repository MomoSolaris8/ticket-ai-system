package com.ticketai.event;

import java.time.Instant;
import java.util.UUID;

public record TicketCreatedEvent(
        UUID ticketId,
        String title,
        String description,
        Instant occurredAt
) {
    public static TicketCreatedEvent of(UUID ticketId, String title, String description) {
        return new TicketCreatedEvent(ticketId, title, description, Instant.now());
    }
}
