package com.relay.history.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class MessageId implements Serializable {

    private UUID conversationId;
    private UUID clientMessageId;

    public MessageId() {
    }

    public MessageId(UUID conversationId, UUID clientMessageId) {
        this.conversationId = conversationId;
        this.clientMessageId = clientMessageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MessageId that)) {
            return false;
        }
        return Objects.equals(conversationId, that.conversationId)
                && Objects.equals(clientMessageId, that.clientMessageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, clientMessageId);
    }
}
