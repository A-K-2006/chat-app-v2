package com.relay.presence.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.events.PresenceChangedEvent;
import com.relay.events.PresenceConnectionEvent;
import com.relay.events.Topics;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class PresenceConsumer {

    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public PresenceConsumer(StringRedisTemplate redis, KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.redis = redis;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.PRESENCE_CONNECTION, groupId = "presence")
    public void onConnection(String payload) throws Exception {
        PresenceConnectionEvent event = objectMapper.readValue(payload, PresenceConnectionEvent.class);
        String userId = event.userId().toString();
        String key = "presence:" + userId;

        switch (event.state()) {
            case CONNECTED -> {
                boolean existed = Boolean.TRUE.equals(redis.hasKey(key));
                redis.opsForValue().set(key, event.instanceId(), TTL);
                if (!existed) {
                    publishChange(userId, true);
                }
            }
            case HEARTBEAT -> {
                if (Boolean.TRUE.equals(redis.hasKey(key))) {
                    redis.expire(key, TTL);
                } else {
                    redis.opsForValue().set(key, event.instanceId(), TTL);
                    publishChange(userId, true);
                }
            }
            case DISCONNECTED -> {
                boolean existed = Boolean.TRUE.equals(redis.hasKey(key));
                redis.delete(key);
                if (existed) {
                    publishChange(userId, false);
                }
            }
        }
    }

    private void publishChange(String userId, boolean online) {
        PresenceChangedEvent changed = new PresenceChangedEvent(
                java.util.UUID.fromString(userId), online, Instant.now());
        try {
            kafka.send(Topics.PRESENCE_CHANGED, userId, objectMapper.writeValueAsString(changed));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize presence change", e);
        }
    }
}
