package com.relay.events;

import java.time.Instant;
import java.util.UUID;

public record MessageCommandEvent(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        String body,
        Instant clientSentAt) {
}
