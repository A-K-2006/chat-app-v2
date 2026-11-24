package com.relay.history.repository;

import com.relay.history.domain.Message;
import com.relay.history.domain.MessageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, MessageId> {

    boolean existsByConversationIdAndClientMessageId(UUID conversationId, UUID clientMessageId);

    @Query(value = "select * from messages where conversation_id = :cid and seq > :after "
            + "order by seq asc limit :limit", nativeQuery = true)
    List<Message> pageAfter(@Param("cid") UUID conversationId, @Param("after") long afterSeq, @Param("limit") int limit);
}
