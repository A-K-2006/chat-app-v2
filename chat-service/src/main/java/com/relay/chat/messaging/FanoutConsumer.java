package com.relay.chat.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.chat.domain.ConversationMember;
import com.relay.chat.repository.ConversationMemberRepository;
import com.relay.chat.security.SessionRegistry;
import com.relay.events.ConversationEvent;
import com.relay.events.MessagePersistedEvent;
import com.relay.events.PresenceChangedEvent;
import com.relay.events.ReceiptEvent;
import com.relay.events.ReceiptType;
import com.relay.events.Topics;
import com.relay.events.TypingEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class FanoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(FanoutConsumer.class);

    private final ObjectMapper objectMapper;
    private final SessionRegistry registry;
    private final ConversationMemberRepository members;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageProducer producer;
    private final MeterRegistry meterRegistry;
    private final Timer e2eLatencyTimer;

    public FanoutConsumer(ObjectMapper objectMapper, SessionRegistry registry,
                          ConversationMemberRepository members, SimpMessagingTemplate messagingTemplate,
                          MessageProducer producer, MeterRegistry meterRegistry, Timer messageE2eLatencyTimer) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.members = members;
        this.messagingTemplate = messagingTemplate;
        this.producer = producer;
        this.meterRegistry = meterRegistry;
        this.e2eLatencyTimer = messageE2eLatencyTimer;
    }

    @KafkaListener(topics = Topics.MESSAGE_PERSISTED, groupId = "${relay.chat.fanout-group}-msg")
    public void onPersisted(String payload) throws Exception {
        MessagePersistedEvent event = objectMapper.readValue(payload, MessagePersistedEvent.class);
        for (ConversationMember member : members.findByConversationId(event.conversationId())) {
            String userId = member.getUserId().toString();
            if (registry.hasLocalSession(userId)) {
                messagingTemplate.convertAndSendToUser(userId, "/queue/messages", event);
                meterRegistry.counter("messages.delivered").increment();
                if (event.clientSentAt() != null) {
                    e2eLatencyTimer.record(Duration.between(event.clientSentAt(), Instant.now()));
                }
                if (!member.getUserId().equals(event.senderId())) {
                    producer.receipt(new ReceiptEvent(ReceiptType.DELIVERED, event.conversationId(),
                            member.getUserId(), event.seq(), Instant.now()));
                }
            }
        }
    }

    @KafkaListener(topics = Topics.CONVERSATION_EVENTS, groupId = "${relay.chat.fanout-group}-conv")
    public void onConversation(String payload) throws Exception {
        ConversationEvent event = objectMapper.readValue(payload, ConversationEvent.class);
        deliver(event.conversationId(), "/queue/conversations", event);
    }

    @KafkaListener(topics = Topics.TYPING, groupId = "${relay.chat.fanout-group}-typing")
    public void onTyping(String payload) throws Exception {
        TypingEvent event = objectMapper.readValue(payload, TypingEvent.class);
        deliver(event.conversationId(), "/queue/typing", event);
    }

    @KafkaListener(topics = Topics.RECEIPT, groupId = "${relay.chat.fanout-group}-receipt")
    public void onReceipt(String payload) throws Exception {
        ReceiptEvent event = objectMapper.readValue(payload, ReceiptEvent.class);
        deliver(event.conversationId(), "/queue/receipts", event);
    }

    @KafkaListener(topics = Topics.PRESENCE_CHANGED, groupId = "${relay.chat.fanout-group}-presence")
    public void onPresenceChanged(String payload) throws Exception {
        PresenceChangedEvent event = objectMapper.readValue(payload, PresenceChangedEvent.class);
        String userId = event.userId().toString();
        if (registry.hasLocalSession(userId)) {
            messagingTemplate.convertAndSendToUser(userId, "/queue/presence", event);
        }
    }

    private void deliver(UUID conversationId, String destination, Object payload) {
        for (ConversationMember member : members.findByConversationId(conversationId)) {
            String userId = member.getUserId().toString();
            if (registry.hasLocalSession(userId)) {
                messagingTemplate.convertAndSendToUser(userId, destination, payload);
            }
        }
    }
}
