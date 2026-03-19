package com.ticketai.consumer;

import com.ticketai.client.AiEnrichmentClient;
import com.ticketai.client.TicketServiceClient;
import com.ticketai.dto.AiEnrichmentResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class TicketCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketCreatedConsumer.class);
    private static final String IDEMPOTENCY_KEY_PREFIX = "ticket:processed:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final AiEnrichmentClient aiEnrichmentClient;
    private final TicketServiceClient ticketServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    public TicketCreatedConsumer(AiEnrichmentClient aiEnrichmentClient,
                                  TicketServiceClient ticketServiceClient,
                                  StringRedisTemplate redisTemplate,
                                  MeterRegistry meterRegistry) {
        this.aiEnrichmentClient = aiEnrichmentClient;
        this.ticketServiceClient = ticketServiceClient;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    // Retry (3x, exponential backoff) + DLQ handled by KafkaConsumerConfig DefaultErrorHandler
    @KafkaListener(topics = "ticket.created", groupId = "orchestrator-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consume(@Payload Map<String, Object> payload,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {

        UUID ticketId = UUID.fromString((String) payload.get("ticketId"));
        String title = (String) payload.get("title");
        String description = (String) payload.getOrDefault("description", "");

        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + ticketId;

        // Idempotency check — prevent duplicate processing
        Boolean alreadyProcessed = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "processed", IDEMPOTENCY_TTL);

        if (Boolean.FALSE.equals(alreadyProcessed)) {
            log.warn("Duplicate event detected, skipping ticketId={} partition={} offset={}",
                    ticketId, partition, offset);
            meterRegistry.counter("ticket.enrichment.duplicate").increment();
            return;
        }

        log.info("Processing TicketCreatedEvent ticketId={} partition={} offset={}",
                ticketId, partition, offset);

        try {
            AiEnrichmentResponse enrichment = aiEnrichmentClient.enrich(ticketId, title, description);

            ticketServiceClient.applyEnrichment(ticketId, enrichment);

            meterRegistry.counter("ticket.enrichment.success").increment();
            log.info("Enrichment applied ticketId={} category={} priority={}",
                    ticketId, enrichment.category(), enrichment.priority());

        } catch (Exception ex) {
            redisTemplate.delete(idempotencyKey);
            meterRegistry.counter("ticket.enrichment.error").increment();
            log.error("Enrichment failed ticketId={}, will retry", ticketId, ex);
            throw ex;
        }
    }
}
