package com.ticketai.service;

import com.ticketai.dto.TicketEnrichedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dispatches notifications to the appropriate channel based on ticket priority.
 *
 * In a real enterprise system this would integrate with:
 *   - Email (JavaMailSender / SendGrid)
 *   - Slack / MS Teams webhook
 *   - PagerDuty (for CRITICAL)
 *   - Internal ticketing portal push notification
 *
 * Here we implement the routing logic and log the dispatch,
 * keeping external dependencies swappable via the NotificationChannel interface.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final WebhookNotificationChannel webhookChannel;
    private final MeterRegistry meterRegistry;

    public NotificationDispatcher(WebhookNotificationChannel webhookChannel,
                                  MeterRegistry meterRegistry) {
        this.webhookChannel = webhookChannel;
        this.meterRegistry = meterRegistry;
    }

    public void dispatch(TicketEnrichedEvent event) {
        String channel = resolveChannel(event.aiPriority());

        log.info("Dispatching notification ticketId={} priority={} channel={}",
                event.ticketId(), event.aiPriority(), channel);

        String message = buildMessage(event);

        switch (channel) {
            case "PAGERDUTY" -> {
                // CRITICAL: wake someone up immediately
                webhookChannel.send(channel, message, event.ticketId().toString());
                meterRegistry.counter("notification.sent", "channel", "pagerduty").increment();
            }
            case "SLACK" -> {
                // HIGH: Slack message to on-call channel
                webhookChannel.send(channel, message, event.ticketId().toString());
                meterRegistry.counter("notification.sent", "channel", "slack").increment();
            }
            default -> {
                // LOW / MEDIUM: log only (email in real system)
                log.info("Email notification queued for ticketId={} summary='{}'",
                        event.ticketId(), event.aiSummary());
                meterRegistry.counter("notification.sent", "channel", "email").increment();
            }
        }
    }

    private String resolveChannel(String priority) {
        return switch (priority) {
            case "CRITICAL" -> "PAGERDUTY";
            case "HIGH"     -> "SLACK";
            default         -> "EMAIL";
        };
    }

    private String buildMessage(TicketEnrichedEvent event) {
        return String.format(
                "[%s] Ticket %s — %s\nCategory: %s | Priority: %s\nSummary: %s",
                event.aiPriority(),
                event.ticketId(),
                event.title(),
                event.aiCategory(),
                event.aiPriority(),
                event.aiSummary()
        );
    }
}
