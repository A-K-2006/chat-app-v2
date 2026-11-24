package com.relay.history.repository;

import com.relay.history.domain.ReadState;
import com.relay.history.domain.ReadStateId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReadStateRepository extends JpaRepository<ReadState, ReadStateId> {
}
