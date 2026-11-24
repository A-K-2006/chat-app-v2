package com.relay.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "relay.chat")
public record ChatProperties(
        String instanceId,
        String jwtSecret,
        String authServiceUrl,
        int maxGroupSize,
        int maxBodyBytes,
        RateLimit rateLimit) {

    public record RateLimit(int capacity, int refillTokens, int refillSeconds) {
    }
}
