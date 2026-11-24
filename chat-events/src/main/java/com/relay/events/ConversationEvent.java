package com.relay.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationEvent(
        ConversationEventType type,
        UUID conversationId,
        UUID actorId,
        List<UUID> memberIds,
        String name,
        Instant at) {
}
