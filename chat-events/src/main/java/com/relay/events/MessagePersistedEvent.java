package com.relay.events;

import java.time.Instant;
import java.util.UUID;

public record MessagePersistedEvent(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        long seq,
        String body,
        Instant persistedAt,
        Instant clientSentAt) {
}
