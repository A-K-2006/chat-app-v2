package com.relay.chat.messaging;

import com.relay.chat.dto.SendMessageRequest;
import com.relay.chat.service.MembershipService;
import com.relay.chat.service.RateLimiterService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageHandlerMetricsTest {

    @Mock
    private MessageProducer producer;
    @Mock
    private MembershipService membership;
    @Mock
    private RateLimiterService rateLimiter;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private SimpleMeterRegistry registry;
    private ChatMessageHandler handler;
    private final UUID userId = UUID.randomUUID();
    private final Principal principal = userId::toString;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        handler = new ChatMessageHandler(producer, membership, rateLimiter, messagingTemplate, registry);
    }

    private SendMessageRequest request() {
        return new SendMessageRequest(UUID.randomUUID(), UUID.randomUUID(), "hello");
    }

    @Test
    void successfulSendIncrementsSentCounter() {
        when(membership.isMember(any(), any())).thenReturn(true);
        when(rateLimiter.tryAcquire(any())).thenReturn(true);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(producer.messageCommand(any())).thenReturn(future);

        handler.send(request(), principal);

        assertThat(registry.counter("messages.sent").count()).isEqualTo(1.0);
        assertThat(registry.counter("rate.limit.rejected").count()).isEqualTo(0.0);
    }

    @Test
    void rateLimitedSendIncrementsRejectedCounter() {
        when(membership.isMember(any(), any())).thenReturn(true);
        when(rateLimiter.tryAcquire(any())).thenReturn(false);

        handler.send(request(), principal);

        assertThat(registry.counter("rate.limit.rejected").count()).isEqualTo(1.0);
        assertThat(registry.counter("messages.sent").count()).isEqualTo(0.0);
    }
}
