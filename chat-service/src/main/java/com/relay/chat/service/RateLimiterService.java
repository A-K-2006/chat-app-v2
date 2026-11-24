package com.relay.chat.service;

import com.relay.chat.config.ChatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> rateLimitScript;
    private final ChatProperties properties;

    public RateLimiterService(StringRedisTemplate redis, DefaultRedisScript<Long> rateLimitScript,
                              ChatProperties properties) {
        this.redis = redis;
        this.rateLimitScript = rateLimitScript;
        this.properties = properties;
    }

    public boolean tryAcquire(UUID userId) {
        ChatProperties.RateLimit limit = properties.rateLimit();
        try {
            Long allowed = redis.execute(rateLimitScript, List.of("rl:chat:" + userId),
                    String.valueOf(limit.capacity()),
                    String.valueOf(limit.refillTokens()),
                    String.valueOf(limit.refillSeconds()));
            return allowed != null && allowed == 1L;
        } catch (Exception ex) {
            // Fail open: availability over strictness if Redis is unavailable.
            log.warn("rate limiter unavailable, failing open", ex);
            return true;
        }
    }
}
