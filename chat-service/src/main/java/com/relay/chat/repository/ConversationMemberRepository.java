package com.relay.chat.repository;

import com.relay.chat.domain.ConversationMember;
import com.relay.chat.domain.ConversationMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {
    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    List<ConversationMember> findByConversationId(UUID conversationId);

    long countByConversationId(UUID conversationId);

    void deleteByConversationIdAndUserId(UUID conversationId, UUID userId);
}
