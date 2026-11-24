package com.relay.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendMessageRequest(
        @NotNull UUID conversationId,
        @NotNull UUID messageId,
        @NotBlank @Size(max = 4096) String body) {
}
