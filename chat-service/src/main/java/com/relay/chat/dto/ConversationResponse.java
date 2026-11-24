package com.relay.chat.dto;

import com.relay.chat.domain.ConversationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        ConversationType type,
        String name,
        Instant createdAt,
        List<MemberResponse> members) {
}
