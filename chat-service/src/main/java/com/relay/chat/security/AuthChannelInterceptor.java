package com.relay.chat.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public AuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = resolveToken(accessor);
            if (token == null) {
                return null;
            }
            Jwt jwt = jwtDecoder.decode(token);
            accessor.setUser(new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, List.of()));
            return message;
        }

        if ((StompCommand.SEND.equals(accessor.getCommand())
                || StompCommand.SUBSCRIBE.equals(accessor.getCommand()))
                && accessor.getUser() == null) {
            return null;
        }
        return message;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String nativeAuth = accessor.getFirstNativeHeader("Authorization");
        if (nativeAuth != null && nativeAuth.startsWith("Bearer ")) {
            return nativeAuth.substring(7);
        }
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object handshakeAuth = sessionAttributes.get(JwtHandshakeInterceptor.AUTHORIZATION_ATTR);
            if (handshakeAuth instanceof String s && s.startsWith("Bearer ")) {
                return s.substring(7);
            }
        }
        return null;
    }
}
