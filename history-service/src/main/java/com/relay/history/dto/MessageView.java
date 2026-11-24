package com.relay.history.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageView(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        long seq,
        String body,
        Instant createdAt) {
}
