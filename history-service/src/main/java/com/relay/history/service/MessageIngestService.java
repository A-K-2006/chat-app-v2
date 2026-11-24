package com.relay.history.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.events.MessageCommandEvent;
import com.relay.events.MessagePersistedEvent;
import com.relay.events.Topics;
import com.relay.history.domain.OutboxEvent;
import com.relay.history.repository.OutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class MessageIngestService {

    private final JdbcTemplate jdbc;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final MeterRegistry registry;

    public MessageIngestService(JdbcTemplate jdbc, OutboxRepository outbox,
                                ObjectMapper objectMapper, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    // Single writer per conversation (Kafka partition key = conversationId, one consumer
    // per partition in group "history"), so check-then-increment is race-free.
    @Transactional
    public void ingest(MessageCommandEvent command) {
        UUID conversationId = command.conversationId();
        UUID messageId = command.messageId();

        Integer existing = jdbc.queryForObject(
                "select count(*) from messages where conversation_id = ? and client_message_id = ?",
                Integer.class, conversationId, messageId);
        if (existing != null && existing > 0) {
            registry.counter("history.dedup.hits").increment();
            return;
        }

        jdbc.update("insert into conversation_state (conversation_id, last_seq) values (?, 0) "
                + "on conflict (conversation_id) do nothing", conversationId);
        Long seq = jdbc.queryForObject(
                "update conversation_state set last_seq = last_seq + 1 where conversation_id = ? returning last_seq",
                Long.class, conversationId);

        Instant now = Instant.now();
        jdbc.update("insert into messages (conversation_id, client_message_id, seq, sender_id, body, created_at) "
                        + "values (?, ?, ?, ?, ?, ?) on conflict (conversation_id, client_message_id) do nothing",
                conversationId, messageId, seq, command.senderId(), command.body(), Timestamp.from(now));

        MessagePersistedEvent persisted = new MessagePersistedEvent(
                messageId, conversationId, command.senderId(), seq, command.body(), now, command.clientSentAt());
        try {
            outbox.save(new OutboxEvent(conversationId, Topics.MESSAGE_PERSISTED,
                    objectMapper.writeValueAsString(persisted), now));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize persisted event", e);
        }
        registry.counter("history.messages.persisted").increment();
    }
}
