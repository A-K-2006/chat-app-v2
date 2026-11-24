package com.relay.history.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "read_state")
@IdClass(ReadStateId.class)
public class ReadState {

    @Id
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "last_delivered_seq", nullable = false)
    private long lastDeliveredSeq;

    @Column(name = "last_read_seq", nullable = false)
    private long lastReadSeq;

    protected ReadState() {
    }

    public ReadState(UUID conversationId, UUID userId, long lastDeliveredSeq, long lastReadSeq) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.lastDeliveredSeq = lastDeliveredSeq;
        this.lastReadSeq = lastReadSeq;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getLastDeliveredSeq() {
        return lastDeliveredSeq;
    }

    public long getLastReadSeq() {
        return lastReadSeq;
    }
}
