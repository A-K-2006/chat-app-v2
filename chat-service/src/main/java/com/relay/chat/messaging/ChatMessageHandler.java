package com.relay.chat.messaging;

import com.relay.chat.dto.ReceiptRequest;
import com.relay.chat.dto.SendMessageRequest;
import com.relay.chat.dto.TypingRequest;
import com.relay.chat.service.MembershipService;
import com.relay.chat.service.RateLimiterService;
import com.relay.events.MessageCommandEvent;
import com.relay.events.ReceiptEvent;
import com.relay.events.TypingEvent;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Controller
public class ChatMessageHandler {

    private final MessageProducer producer;
    private final MembershipService membership;
    private final RateLimiterService rateLimiter;
    private final SimpMessagingTemplate messagingTemplate;
    private final MeterRegistry registry;

    public ChatMessageHandler(MessageProducer producer, MembershipService membership,
                              RateLimiterService rateLimiter, SimpMessagingTemplate messagingTemplate,
                              MeterRegistry registry) {
        this.producer = producer;
        this.membership = membership;
        this.rateLimiter = rateLimiter;
        this.messagingTemplate = messagingTemplate;
        this.registry = registry;
    }

    @MessageMapping("/chat.send")
    public void send(@Valid SendMessageRequest request, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        if (!membership.isMember(request.conversationId(), userId)) {
            error(userId, "not a member of this conversation");
            return;
        }
        if (!rateLimiter.tryAcquire(userId)) {
            registry.counter("rate.limit.rejected").increment();
            error(userId, "rate limited");
            return;
        }
        MessageCommandEvent command = new MessageCommandEvent(
                request.messageId(), request.conversationId(), userId, request.body(), Instant.now());
        registry.counter("messages.sent").increment();
        producer.messageCommand(command).whenComplete((result, ex) -> {
            if (ex == null) {
                ack(userId, request.messageId());
            } else {
                error(userId, "send failed");
            }
        });
    }

    @MessageMapping("/chat.typing")
    public void typing(@Valid TypingRequest request, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        if (membership.isMember(request.conversationId(), userId)) {
            producer.typing(new TypingEvent(request.conversationId(), userId, request.typing(), Instant.now()));
        }
    }

    @MessageMapping("/chat.receipt")
    public void receipt(@Valid ReceiptRequest request, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        if (membership.isMember(request.conversationId(), userId)) {
            producer.receipt(new ReceiptEvent(request.type(), request.conversationId(), userId,
                    request.upToSeq(), Instant.now()));
        }
    }

    private void ack(UUID userId, UUID messageId) {
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/acks",
                Map.of("messageId", messageId, "status", "sent"));
    }

    private void error(UUID userId, String message) {
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/errors",
                Map.of("message", message));
    }
}
