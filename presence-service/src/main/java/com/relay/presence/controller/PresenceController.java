package com.relay.presence.controller;

import com.relay.presence.dto.PresenceView;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/presence")
public class PresenceController {

    private final StringRedisTemplate redis;

    public PresenceController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @GetMapping("/{userId}")
    PresenceView get(@PathVariable UUID userId) {
        return view(userId);
    }

    @GetMapping
    List<PresenceView> batch(@RequestParam List<UUID> ids) {
        return ids.stream().map(this::view).toList();
    }

    private PresenceView view(UUID userId) {
        String instanceId = redis.opsForValue().get("presence:" + userId);
        return new PresenceView(userId, instanceId != null, instanceId);
    }
}
