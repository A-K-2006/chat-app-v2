package com.relay.history.repository;

import com.relay.history.domain.MembershipProjection;
import com.relay.history.domain.MembershipProjectionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MembershipProjectionRepository extends JpaRepository<MembershipProjection, MembershipProjectionId> {
    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);
}
