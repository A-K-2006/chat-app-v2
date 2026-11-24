package com.relay.history.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "conversation_state")
public class ConversationState {

    @Id
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    protected ConversationState() {
    }

    public ConversationState(UUID conversationId, long lastSeq) {
        this.conversationId = conversationId;
        this.lastSeq = lastSeq;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public long getLastSeq() {
        return lastSeq;
    }
}
