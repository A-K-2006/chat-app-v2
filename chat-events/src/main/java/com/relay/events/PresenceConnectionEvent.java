package com.relay.events;

import java.time.Instant;
import java.util.UUID;

public record PresenceConnectionEvent(
        UUID userId,
        String instanceId,
        PresenceState state,
        Instant at) {
}
