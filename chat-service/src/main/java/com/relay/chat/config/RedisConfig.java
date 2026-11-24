package com.relay.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {

    // Token bucket using Redis server TIME (single clock, no client skew). Returns 1 if allowed.
    private static final String TOKEN_BUCKET = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local refillSec = tonumber(ARGV[3])
            local t = redis.call('TIME')
            local now = tonumber(t[1]) + tonumber(t[2]) / 1000000
            local bucket = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(bucket[1])
            local ts = tonumber(bucket[2])
            if tokens == nil then tokens = capacity; ts = now end
            local delta = math.max(0, now - ts)
            tokens = math.min(capacity, tokens + delta * (refill / refillSec))
            local allowed = 0
            if tokens >= 1 then tokens = tokens - 1; allowed = 1 end
            redis.call('HSET', key, 'tokens', tokens, 'ts', now)
            redis.call('EXPIRE', key, refillSec * 2 + 10)
            return allowed
            """;

    @Bean
    DefaultRedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(TOKEN_BUCKET);
        script.setResultType(Long.class);
        return script;
    }
}
