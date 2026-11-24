package com.relay.chat.repository;

import com.relay.chat.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findByDirectKey(String directKey);

    @Query("select c from Conversation c where c.id in "
            + "(select m.conversationId from ConversationMember m where m.userId = :userId)")
    List<Conversation> findAllForMember(@Param("userId") UUID userId);
}
