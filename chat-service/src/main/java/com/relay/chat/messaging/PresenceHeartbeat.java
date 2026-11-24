package com.relay.chat.messaging;

import com.relay.chat.config.ChatProperties;
import com.relay.chat.security.SessionRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PresenceHeartbeat {

    private final SessionRegistry registry;
    private final MessageProducer producer;
    private final ChatProperties properties;

    public PresenceHeartbeat(SessionRegistry registry, MessageProducer producer, ChatProperties properties) {
        this.registry = registry;
        this.producer = producer;
        this.properties = properties;
    }

    // Refreshes presence TTLs so long-lived connections are not shown offline.
    @Scheduled(fixedDelay = 10000)
    public void beat() {
        for (String userId : registry.localUsers()) {
            producer.presenceHeartbeat(userId, properties.instanceId());
        }
    }
}
