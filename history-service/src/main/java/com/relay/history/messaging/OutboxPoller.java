package com.relay.history.messaging;

import com.relay.history.domain.OutboxEvent;
import com.relay.history.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPoller(OutboxRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${relay.history.outbox-delay-ms:200}")
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = outbox.claimUnpublished(50);
        for (OutboxEvent event : batch) {
            try {
                kafka.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.markPublished(Instant.now());
                outbox.save(event);
            } catch (Exception ex) {
                log.warn("outbox publish failed for id={}, will retry", event.getId(), ex);
            }
        }
    }
}
