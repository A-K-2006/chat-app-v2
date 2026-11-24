package com.relay.chat.repository;

import com.relay.chat.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    long countByPublishedAtIsNull();

    @Query(value = "select * from outbox where published_at is null "
            + "order by id limit :batch for update skip locked", nativeQuery = true)
    List<OutboxEvent> claimUnpublished(@Param("batch") int batch);
}
