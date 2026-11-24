package com.relay.presence.dto;

import java.util.UUID;

public record PresenceView(UUID userId, boolean online, String instanceId) {
}
