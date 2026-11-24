package com.relay.chat.service;

import com.relay.chat.repository.ConversationMemberRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MembershipService {

    private final ConversationMemberRepository members;

    public MembershipService(ConversationMemberRepository members) {
        this.members = members;
    }

    public boolean isMember(UUID conversationId, UUID userId) {
        return members.existsByConversationIdAndUserId(conversationId, userId);
    }
}
