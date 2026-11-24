package com.relay.history.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ReadStateId implements Serializable {

    private UUID conversationId;
    private UUID userId;

    public ReadStateId() {
    }

    public ReadStateId(UUID conversationId, UUID userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReadStateId that)) {
            return false;
        }
        return Objects.equals(conversationId, that.conversationId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, userId);
    }
}
