package com.ticketai.dto;

import java.time.Instant;
import java.util.UUID;

public record TicketEnrichedEvent(
        UUID ticketId,
        String title,
        String aiCategory,
        String aiPriority,
        String aiSummary,
        double confidenceScore,
        Instant occurredAt
) {}
