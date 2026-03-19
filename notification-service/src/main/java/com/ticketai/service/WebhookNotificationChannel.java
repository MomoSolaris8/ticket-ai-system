package com.ticketai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Sends notifications via outbound webhook (Slack / PagerDuty / MS Teams).
 * URL is configurable per channel via application.yml — no hard-coded endpoints.
 */
@Component
public class WebhookNotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationChannel.class);

    private final String slackWebhookUrl;
    private final String pagerdutyWebhookUrl;
    private final RestClient restClient;

    public WebhookNotificationChannel(
            @Value("${notification.slack.webhook-url:}") String slackWebhookUrl,
            @Value("${notification.pagerduty.webhook-url:}") String pagerdutyWebhookUrl) {
        this.slackWebhookUrl = slackWebhookUrl;
        this.pagerdutyWebhookUrl = pagerdutyWebhookUrl;
        this.restClient = RestClient.create();
    }

    public void send(String channel, String message, String referenceId) {
        String url = switch (channel) {
            case "SLACK"     -> slackWebhookUrl;
            case "PAGERDUTY" -> pagerdutyWebhookUrl;
            default -> null;
        };

        if (url == null || url.isBlank()) {
            // No webhook configured — log and continue (dev/test mode)
            log.info("[MOCK] {} notification | refId={} | message={}",
                    channel, referenceId, message);
            return;
        }

        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", message, "reference_id", referenceId))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Webhook sent channel={} refId={}", channel, referenceId);

        } catch (Exception ex) {
            // Notification failure must NOT affect business flow — just log
            log.error("Webhook delivery failed channel={} refId={}", channel, referenceId, ex);
        }
    }
}
