package com.ticketai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiEnrichmentResponse(
        String category,
        String priority,
        String summary,
        @JsonProperty("confidence_score") double confidenceScore
) {}
