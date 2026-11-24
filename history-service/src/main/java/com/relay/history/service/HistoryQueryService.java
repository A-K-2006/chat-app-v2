package com.relay.history.service;

import com.relay.history.domain.ReadState;
import com.relay.history.domain.ReadStateId;
import com.relay.history.dto.MessageView;
import com.relay.history.dto.ReadStateView;
import com.relay.history.exception.ApiException;
import com.relay.history.repository.MembershipProjectionRepository;
import com.relay.history.repository.MessageRepository;
import com.relay.history.repository.ReadStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class HistoryQueryService {

    private final MessageRepository messages;
    private final MembershipProjectionRepository membership;
    private final ReadStateRepository readStates;

    public HistoryQueryService(MessageRepository messages, MembershipProjectionRepository membership,
                               ReadStateRepository readStates) {
        this.messages = messages;
        this.membership = membership;
        this.readStates = readStates;
    }

    @Transactional(readOnly = true)
    public List<MessageView> messages(UUID userId, UUID conversationId, long afterSeq, int limit) {
        requireMember(userId, conversationId);
        int bounded = Math.min(Math.max(limit, 1), 200);
        return messages.pageAfter(conversationId, afterSeq, bounded).stream()
                .map(m -> new MessageView(m.getClientMessageId(), m.getConversationId(), m.getSenderId(),
                        m.getSeq(), m.getBody(), m.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReadStateView state(UUID userId, UUID conversationId) {
        requireMember(userId, conversationId);
        ReadState readState = readStates.findById(new ReadStateId(conversationId, userId))
                .orElse(new ReadState(conversationId, userId, 0, 0));
        return new ReadStateView(conversationId, userId, readState.getLastDeliveredSeq(), readState.getLastReadSeq());
    }

    private void requireMember(UUID userId, UUID conversationId) {
        if (!membership.existsByConversationIdAndUserId(conversationId, userId)) {
            throw ApiException.forbidden("not a member of this conversation");
        }
    }
}
