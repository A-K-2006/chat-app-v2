package com.relay.chat.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.events.MessageCommandEvent;
import com.relay.events.PresenceConnectionEvent;
import com.relay.events.PresenceState;
import com.relay.events.ReceiptEvent;
import com.relay.events.Topics;
import com.relay.events.TypingEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class MessageProducer {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public MessageProducer(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<SendResult<String, String>> messageCommand(MessageCommandEvent event) {
        return send(Topics.MESSAGE_COMMAND, event.conversationId().toString(), event);
    }

    public void presenceConnected(String userId, String instanceId) {
        send(Topics.PRESENCE_CONNECTION, userId,
                new PresenceConnectionEvent(UUID.fromString(userId), instanceId, PresenceState.CONNECTED, Instant.now()));
    }

    public void presenceDisconnected(String userId, String instanceId) {
        send(Topics.PRESENCE_CONNECTION, userId,
                new PresenceConnectionEvent(UUID.fromString(userId), instanceId, PresenceState.DISCONNECTED, Instant.now()));
    }

    public void presenceHeartbeat(String userId, String instanceId) {
        send(Topics.PRESENCE_CONNECTION, userId,
                new PresenceConnectionEvent(UUID.fromString(userId), instanceId, PresenceState.HEARTBEAT, Instant.now()));
    }

    public void typing(TypingEvent event) {
        send(Topics.TYPING, event.conversationId().toString(), event);
    }

    public void receipt(ReceiptEvent event) {
        send(Topics.RECEIPT, event.conversationId().toString(), event);
    }

    private CompletableFuture<SendResult<String, String>> send(String topic, String key, Object event) {
        try {
            return kafka.send(topic, key, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize event for " + topic, e);
        }
    }
}
