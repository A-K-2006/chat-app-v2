package com.relay.events;

import java.time.Instant;
import java.util.UUID;

public record PresenceChangedEvent(
        UUID userId,
        boolean online,
        Instant at) {
}
