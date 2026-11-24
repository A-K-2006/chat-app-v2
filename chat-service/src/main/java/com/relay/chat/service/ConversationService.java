package com.relay.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.chat.config.ChatProperties;
import com.relay.chat.domain.Conversation;
import com.relay.chat.domain.ConversationMember;
import com.relay.chat.domain.ConversationMemberId;
import com.relay.chat.domain.ConversationType;
import com.relay.chat.domain.MemberRole;
import com.relay.chat.domain.OutboxEvent;
import com.relay.chat.dto.ConversationResponse;
import com.relay.chat.dto.CreateGroupRequest;
import com.relay.chat.dto.MemberResponse;
import com.relay.chat.exception.ApiException;
import com.relay.chat.repository.ConversationMemberRepository;
import com.relay.chat.repository.ConversationRepository;
import com.relay.chat.repository.OutboxRepository;
import com.relay.events.ConversationEvent;
import com.relay.events.ConversationEventType;
import com.relay.events.Topics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final OutboxRepository outbox;
    private final AuthClient authClient;
    private final ObjectMapper objectMapper;
    private final ChatProperties properties;

    public ConversationService(ConversationRepository conversations, ConversationMemberRepository members,
                               OutboxRepository outbox, AuthClient authClient, ObjectMapper objectMapper,
                               ChatProperties properties) {
        this.conversations = conversations;
        this.members = members;
        this.outbox = outbox;
        this.authClient = authClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public ConversationResponse createDirect(UUID userId, UUID peerId) {
        if (userId.equals(peerId)) {
            throw ApiException.badRequest("cannot create a direct chat with yourself");
        }
        String key = directKey(userId, peerId);
        return conversations.findByDirectKey(key)
                .map(this::toResponse)
                .orElseGet(() -> {
                    requireUserExists(peerId);
                    Instant now = Instant.now();
                    Conversation conversation = new Conversation(
                            UUID.randomUUID(), ConversationType.DIRECT, null, key, userId, now);
                    conversations.save(conversation);
                    members.save(new ConversationMember(conversation.getId(), userId, MemberRole.MEMBER, now));
                    members.save(new ConversationMember(conversation.getId(), peerId, MemberRole.MEMBER, now));
                    publishConversationEvent(conversation.getId(), ConversationEventType.CREATED, userId,
                            List.of(userId, peerId), null);
                    return toResponse(conversation);
                });
    }

    @Transactional
    public ConversationResponse createGroup(UUID userId, CreateGroupRequest request) {
        List<UUID> invitees = request.memberIds().stream().distinct().filter(id -> !id.equals(userId)).toList();
        if (invitees.isEmpty()) {
            throw ApiException.badRequest("group needs at least one other member");
        }
        if (invitees.size() + 1 > properties.maxGroupSize()) {
            throw ApiException.badRequest("group exceeds max size");
        }
        invitees.forEach(this::requireUserExists);

        Instant now = Instant.now();
        Conversation conversation = new Conversation(
                UUID.randomUUID(), ConversationType.GROUP, request.name(), null, userId, now);
        conversations.save(conversation);
        members.save(new ConversationMember(conversation.getId(), userId, MemberRole.OWNER, now));
        List<UUID> allMembers = new ArrayList<>();
        allMembers.add(userId);
        for (UUID invitee : invitees) {
            members.save(new ConversationMember(conversation.getId(), invitee, MemberRole.MEMBER, now));
            allMembers.add(invitee);
        }
        publishConversationEvent(conversation.getId(), ConversationEventType.CREATED, userId,
                allMembers, request.name());
        return toResponse(conversation);
    }

    @Transactional
    public void addMember(UUID actorId, UUID conversationId, UUID newUserId) {
        requireAdminOrOwner(actorId, conversationId);
        if (members.existsByConversationIdAndUserId(conversationId, newUserId)) {
            throw ApiException.conflict("user already a member");
        }
        if (members.countByConversationId(conversationId) >= properties.maxGroupSize()) {
            throw ApiException.badRequest("group exceeds max size");
        }
        requireUserExists(newUserId);
        members.save(new ConversationMember(conversationId, newUserId, MemberRole.MEMBER, Instant.now()));
        publishConversationEvent(conversationId, ConversationEventType.MEMBER_ADDED, actorId,
                List.of(newUserId), null);
    }

    @Transactional
    public void removeMember(UUID actorId, UUID conversationId, UUID targetId) {
        if (!actorId.equals(targetId)) {
            requireAdminOrOwner(actorId, conversationId);
        } else {
            requireMember(actorId, conversationId);
        }
        if (!members.existsByConversationIdAndUserId(conversationId, targetId)) {
            throw ApiException.notFound("user is not a member");
        }
        members.deleteByConversationIdAndUserId(conversationId, targetId);
        publishConversationEvent(conversationId, ConversationEventType.MEMBER_REMOVED, actorId,
                List.of(targetId), null);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listForUser(UUID userId) {
        return conversations.findAllForMember(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID userId, UUID conversationId) {
        requireMember(userId, conversationId);
        return members.findByConversationId(conversationId).stream()
                .map(m -> new MemberResponse(m.getUserId(), m.getRole()))
                .toList();
    }

    private void requireUserExists(UUID userId) {
        if (!authClient.userExists(userId)) {
            throw ApiException.notFound("user " + userId + " not found");
        }
    }

    private void requireMember(UUID userId, UUID conversationId) {
        if (!members.existsByConversationIdAndUserId(conversationId, userId)) {
            throw ApiException.forbidden("not a member of this conversation");
        }
    }

    private void requireAdminOrOwner(UUID userId, UUID conversationId) {
        ConversationMember member = members.findById(new ConversationMemberId(conversationId, userId))
                .orElseThrow(() -> ApiException.forbidden("not a member of this conversation"));
        if (member.getRole() != MemberRole.OWNER && member.getRole() != MemberRole.ADMIN) {
            throw ApiException.forbidden("requires owner or admin role");
        }
    }

    private String directKey(UUID a, UUID b) {
        String sa = a.toString();
        String sb = b.toString();
        return sa.compareTo(sb) <= 0 ? sa + ":" + sb : sb + ":" + sa;
    }

    private void publishConversationEvent(UUID conversationId, ConversationEventType type, UUID actorId,
                                          List<UUID> memberIds, String name) {
        ConversationEvent event = new ConversationEvent(type, conversationId, actorId, memberIds, name, Instant.now());
        try {
            String payload = objectMapper.writeValueAsString(event);
            outbox.save(new OutboxEvent(conversationId, Topics.CONVERSATION_EVENTS, payload, Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize conversation event", e);
        }
    }

    private ConversationResponse toResponse(Conversation conversation) {
        List<MemberResponse> memberResponses = members.findByConversationId(conversation.getId()).stream()
                .map(m -> new MemberResponse(m.getUserId(), m.getRole()))
                .toList();
        return new ConversationResponse(conversation.getId(), conversation.getType(), conversation.getName(),
                conversation.getCreatedAt(), memberResponses);
    }
}
