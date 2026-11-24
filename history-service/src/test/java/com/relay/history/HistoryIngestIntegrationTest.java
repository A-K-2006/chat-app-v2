package com.relay.history;

import com.relay.events.MessageCommandEvent;
import com.relay.history.domain.Message;
import com.relay.history.repository.MessageRepository;
import com.relay.history.repository.OutboxRepository;
import com.relay.history.service.MessageIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.listener.auto-startup=false",
        "relay.history.outbox-delay-ms=1000000"
})
@Testcontainers
class HistoryIngestIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MessageIngestService ingestService;
    @Autowired
    private MessageRepository messages;
    @Autowired
    private OutboxRepository outbox;

    @Test
    void dedupIsIdempotentAndSeqIsMonotonic() {
        UUID conversationId = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        ingestService.ingest(new MessageCommandEvent(first, conversationId, sender, "hello", Instant.now()));
        ingestService.ingest(new MessageCommandEvent(first, conversationId, sender, "hello", Instant.now()));
        ingestService.ingest(new MessageCommandEvent(second, conversationId, sender, "world", Instant.now()));

        assertThat(messages.pageAfter(conversationId, 0, 100)).hasSize(2);
        assertThat(messages.pageAfter(conversationId, 0, 10))
                .extracting(Message::getSeq)
                .containsExactly(1L, 2L);
        assertThat(messages.pageAfter(conversationId, 1, 10))
                .extracting(Message::getBody)
                .containsExactly("world");
        // duplicate produced no extra outbox event
        assertThat(outbox.countByAggregateId(conversationId)).isEqualTo(2);
    }

    @Test
    void seqIsPerConversation() {
        UUID sender = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        ingestService.ingest(new MessageCommandEvent(UUID.randomUUID(), a, sender, "a1", Instant.now()));
        ingestService.ingest(new MessageCommandEvent(UUID.randomUUID(), b, sender, "b1", Instant.now()));
        ingestService.ingest(new MessageCommandEvent(UUID.randomUUID(), a, sender, "a2", Instant.now()));

        assertThat(messages.pageAfter(a, 0, 10)).extracting(Message::getSeq).containsExactly(1L, 2L);
        assertThat(messages.pageAfter(b, 0, 10)).extracting(Message::getSeq).containsExactly(1L);
    }
}
