package com.relay.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.chat.config.ChatProperties;
import com.relay.chat.domain.Conversation;
import com.relay.chat.domain.ConversationMember;
import com.relay.chat.domain.ConversationMemberId;
import com.relay.chat.domain.ConversationType;
import com.relay.chat.domain.MemberRole;
import com.relay.chat.dto.CreateGroupRequest;
import com.relay.chat.exception.ApiException;
import com.relay.chat.repository.ConversationMemberRepository;
import com.relay.chat.repository.ConversationRepository;
import com.relay.chat.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversations;
    @Mock
    private ConversationMemberRepository members;
    @Mock
    private OutboxRepository outbox;
    @Mock
    private AuthClient authClient;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        ChatProperties props = new ChatProperties("test-instance", "secret", "http://auth-service",
                256, 4096, new ChatProperties.RateLimit(20, 20, 1));
        service = new ConversationService(conversations, members, outbox, authClient,
                new ObjectMapper().findAndRegisterModules(), props);
    }

    @Test
    void createDirectWithSelfIsRejected() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> service.createDirect(userId, userId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createDirectIsIdempotentForExistingPair() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Conversation existing = new Conversation(UUID.randomUUID(), ConversationType.DIRECT, null,
                "key", a, Instant.now());
        when(conversations.findByDirectKey(any())).thenReturn(Optional.of(existing));
        when(members.findByConversationId(existing.getId())).thenReturn(List.of());

        var response = service.createDirect(a, b);

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(conversations, never()).save(any());
        verify(authClient, never()).userExists(any());
    }

    @Test
    void createGroupRequiresAnotherMember() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> service.createGroup(userId, new CreateGroupRequest("g", List.of(userId))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void addMemberRequiresAdminOrOwner() {
        UUID actor = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        when(members.findById(new ConversationMemberId(conversationId, actor)))
                .thenReturn(Optional.of(new ConversationMember(conversationId, actor, MemberRole.MEMBER, Instant.now())));

        assertThatThrownBy(() -> service.addMember(actor, conversationId, target))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void addMemberVerifiesUserExists() {
        UUID actor = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        when(members.findById(new ConversationMemberId(conversationId, actor)))
                .thenReturn(Optional.of(new ConversationMember(conversationId, actor, MemberRole.OWNER, Instant.now())));
        when(members.existsByConversationIdAndUserId(conversationId, target)).thenReturn(false);
        when(members.countByConversationId(conversationId)).thenReturn(2L);
        when(authClient.userExists(target)).thenReturn(false);

        assertThatThrownBy(() -> service.addMember(actor, conversationId, target))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
