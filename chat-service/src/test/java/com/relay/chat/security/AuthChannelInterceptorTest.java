package com.relay.chat.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthChannelInterceptorTest {

    private JwtDecoder jwtDecoder;
    private AuthChannelInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        interceptor = new AuthChannelInterceptor(jwtDecoder);
    }

    private Message<?> message(StompCommand command, String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connectWithValidTokenSetsUser() {
        Jwt jwt = Jwt.withTokenValue("good").header("alg", "HS256")
                .subject("user-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        when(jwtDecoder.decode("good")).thenReturn(jwt);

        Message<?> result = interceptor.preSend(message(StompCommand.CONNECT, "Bearer good"), channel);

        assertThat(result).isNotNull();
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor.getUser().getName()).isEqualTo("user-1");
    }

    @Test
    void connectWithoutTokenIsRejected() {
        Message<?> result = interceptor.preSend(message(StompCommand.CONNECT, null), channel);
        assertThat(result).isNull();
    }

    @Test
    void sendWithoutAuthenticatedUserIsRejected() {
        Message<?> result = interceptor.preSend(message(StompCommand.SEND, null), channel);
        assertThat(result).isNull();
    }
}
