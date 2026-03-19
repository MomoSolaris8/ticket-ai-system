package com.ticketai.dto;

public record EnrichTicketRequest(
        String category,
        String priority,
        String summary
) {}
