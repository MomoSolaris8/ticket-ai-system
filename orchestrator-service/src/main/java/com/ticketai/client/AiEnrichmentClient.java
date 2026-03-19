package com.ticketai.client;

import com.ticketai.dto.AiEnrichmentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class AiEnrichmentClient {

    private static final Logger log = LoggerFactory.getLogger(AiEnrichmentClient.class);

    private final RestClient restClient;

    public AiEnrichmentClient(@Value("${ai.enrichment.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public AiEnrichmentResponse enrich(UUID ticketId, String title, String description) {
        log.debug("Calling AI enrichment for ticketId={}", ticketId);

        return restClient.post()
                .uri("/enrich")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "ticket_id", ticketId.toString(),
                        "title", title,
                        "description", description != null ? description : ""
                ))
                .retrieve()
                .body(AiEnrichmentResponse.class);
    }
}
