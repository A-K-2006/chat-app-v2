package com.relay.chat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateDirectRequest(@NotNull UUID peerId) {
}
