package com.ticketai.client;

import com.ticketai.dto.AiEnrichmentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class TicketServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TicketServiceClient.class);

    private final RestClient restClient;

    public TicketServiceClient(@Value("${ticket.service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public void applyEnrichment(UUID ticketId, AiEnrichmentResponse enrichment) {
        restClient.patch()
                .uri("/api/tickets/{id}/enrich", ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(enrichment)
                .retrieve()
                .toBodilessEntity();

        log.info("Enrichment written back to ticket-service for ticketId={}", ticketId);
    }

    public void markManualReview(UUID ticketId) {
        restClient.patch()
                .uri("/api/tickets/{id}/manual-review", ticketId)
                .retrieve()
                .toBodilessEntity();

        log.info("Ticket marked for manual review ticketId={}", ticketId);
    }
}
