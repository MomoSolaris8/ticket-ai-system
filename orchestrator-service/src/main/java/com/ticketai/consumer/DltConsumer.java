package com.ticketai.consumer;

import com.ticketai.client.TicketServiceClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class DltConsumer {

    private static final Logger log = LoggerFactory.getLogger(DltConsumer.class);

    private final TicketServiceClient ticketServiceClient;
    private final MeterRegistry meterRegistry;

    public DltConsumer(TicketServiceClient ticketServiceClient, MeterRegistry meterRegistry) {
        this.ticketServiceClient = ticketServiceClient;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = "ticket.created.DLT", groupId = "orchestrator-dlt-group")
    public void handleDlt(@Payload Map<String, Object> payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        UUID ticketId = UUID.fromString((String) payload.get("ticketId"));
        log.error("DLT: all retries exhausted for ticketId={} topic={}", ticketId, topic);

        // Human fallback: mark ticket for manual review — system stays stable
        ticketServiceClient.markManualReview(ticketId);
        meterRegistry.counter("ticket.enrichment.dlt").increment();
    }
}
