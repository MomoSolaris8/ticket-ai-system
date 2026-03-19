package com.ticketai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description
) {}
