package com.ticketai.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;

    // AI-enriched fields
    private String aiCategory;
    private String aiPriority;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    private String assignedTo;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    // State machine transitions
    public void markEnriched(String category, String priority, String summary) {
        if (this.status != TicketStatus.OPEN) {
            throw new IllegalStateException("Cannot enrich ticket in status: " + this.status);
        }
        this.aiCategory = category;
        this.aiPriority = priority;
        this.aiSummary = summary;
        this.status = TicketStatus.ENRICHED;
    }

    public void markNeedsManualReview() {
        this.status = TicketStatus.NEEDS_MANUAL_REVIEW;
    }

    public void assign(String assignee) {
        if (this.status != TicketStatus.ENRICHED) {
            throw new IllegalStateException("Ticket must be enriched before assignment");
        }
        this.assignedTo = assignee;
        this.status = TicketStatus.ASSIGNED;
    }

    // Getters
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TicketStatus getStatus() { return status; }
    public String getAiCategory() { return aiCategory; }
    public String getAiPriority() { return aiPriority; }
    public String getAiSummary() { return aiSummary; }
    public String getAssignedTo() { return assignedTo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
}
