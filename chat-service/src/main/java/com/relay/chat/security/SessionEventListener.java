package com.relay.chat.security;

import com.relay.chat.config.ChatProperties;
import com.relay.chat.messaging.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class SessionEventListener {

    private static final Logger log = LoggerFactory.getLogger(SessionEventListener.class);

    private final SessionRegistry registry;
    private final MessageProducer producer;
    private final ChatProperties properties;

    public SessionEventListener(SessionRegistry registry, MessageProducer producer, ChatProperties properties) {
        this.registry = registry;
        this.producer = producer;
        this.properties = properties;
    }

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        if (user != null && accessor.getSessionId() != null) {
            registry.register(user.getName(), accessor.getSessionId());
            producer.presenceConnected(user.getName(), properties.instanceId());
            log.info("ws connected user={} instance={}", user.getName(), properties.instanceId());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String userId = registry.unregister(event.getSessionId());
        if (userId != null) {
            producer.presenceDisconnected(userId, properties.instanceId());
        }
    }
}
