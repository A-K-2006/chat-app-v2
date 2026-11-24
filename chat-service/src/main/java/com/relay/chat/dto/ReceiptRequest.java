package com.relay.chat.dto;

import com.relay.events.ReceiptType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReceiptRequest(
        @NotNull UUID conversationId,
        @NotNull ReceiptType type,
        long upToSeq) {
}
