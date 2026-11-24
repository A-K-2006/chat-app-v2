package com.relay.history.repository;

import com.relay.history.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    long countByAggregateId(UUID aggregateId);

    long countByPublishedAtIsNull();

    @Query(value = "select * from outbox where published_at is null "
            + "order by id limit :batch for update skip locked", nativeQuery = true)
    List<OutboxEvent> claimUnpublished(@Param("batch") int batch);
}
