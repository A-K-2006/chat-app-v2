package com.relay.events;

import java.time.Instant;
import java.util.UUID;

public record ReceiptEvent(
        ReceiptType type,
        UUID conversationId,
        UUID userId,
        long upToSeq,
        Instant at) {
}
