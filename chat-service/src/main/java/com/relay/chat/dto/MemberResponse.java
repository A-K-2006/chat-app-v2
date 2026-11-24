package com.relay.chat.dto;

import com.relay.chat.domain.MemberRole;

import java.util.UUID;

public record MemberResponse(UUID userId, MemberRole role) {
}
