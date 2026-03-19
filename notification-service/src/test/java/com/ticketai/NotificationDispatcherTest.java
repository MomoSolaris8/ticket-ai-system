package com.ticketai;

import com.ticketai.dto.TicketEnrichedEvent;
import com.ticketai.service.NotificationDispatcher;
import com.ticketai.service.WebhookNotificationChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private WebhookNotificationChannel webhookChannel;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(webhookChannel, new SimpleMeterRegistry());
    }

    @Test
    void criticalTicket_shouldDispatchToPagerDuty() {
        TicketEnrichedEvent event = enrichedEvent("CRITICAL");

        dispatcher.dispatch(event);

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        verify(webhookChannel).send(channelCaptor.capture(), anyString(), anyString());
        assertThat(channelCaptor.getValue()).isEqualTo("PAGERDUTY");
    }

    @Test
    void highTicket_shouldDispatchToSlack() {
        TicketEnrichedEvent event = enrichedEvent("HIGH");

        dispatcher.dispatch(event);

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        verify(webhookChannel).send(channelCaptor.capture(), anyString(), anyString());
        assertThat(channelCaptor.getValue()).isEqualTo("SLACK");
    }

    @Test
    void lowTicket_shouldNotCallWebhook() {
        TicketEnrichedEvent event = enrichedEvent("LOW");

        dispatcher.dispatch(event);

        verifyNoInteractions(webhookChannel);
    }

    private TicketEnrichedEvent enrichedEvent(String priority) {
        return new TicketEnrichedEvent(
                UUID.randomUUID(),
                "Test ticket",
                "BILLING",
                priority,
                "User cannot access billing page.",
                0.92,
                Instant.now()
        );
    }
}
