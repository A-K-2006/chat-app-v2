package com.relay.history.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
@IdClass(MessageId.class)
public class Message {

    @Id
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Id
    @Column(name = "client_message_id")
    private UUID clientMessageId;

    @Column(nullable = false)
    private long seq;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false, length = 4096)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Message() {
    }

    public Message(UUID conversationId, UUID clientMessageId, long seq, UUID senderId, String body, Instant createdAt) {
        this.conversationId = conversationId;
        this.clientMessageId = clientMessageId;
        this.seq = seq;
        this.senderId = senderId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getClientMessageId() {
        return clientMessageId;
    }

    public long getSeq() {
        return seq;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
