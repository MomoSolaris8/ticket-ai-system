package com.ticketai.consumer;

import com.ticketai.dto.TicketEnrichedEvent;
import com.ticketai.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class TicketEnrichedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketEnrichedConsumer.class);

    private final NotificationDispatcher dispatcher;

    public TicketEnrichedConsumer(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(topics = "ticket.enriched", groupId = "notification-group")
    public void consume(@Payload TicketEnrichedEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received TicketEnrichedEvent ticketId={} priority={} partition={} offset={}",
                event.ticketId(), event.aiPriority(), partition, offset);

        dispatcher.dispatch(event);
    }
}
