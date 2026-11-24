package com.relay.history.repository;

import com.relay.history.domain.ConversationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConversationStateRepository extends JpaRepository<ConversationState, UUID> {
}
