package com.relay.history.dto;

import java.util.UUID;

public record ReadStateView(
        UUID conversationId,
        UUID userId,
        long lastDeliveredSeq,
        long lastReadSeq) {
}
