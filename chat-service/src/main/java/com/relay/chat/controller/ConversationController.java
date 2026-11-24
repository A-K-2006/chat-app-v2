package com.relay.chat.controller;

import com.relay.chat.dto.AddMemberRequest;
import com.relay.chat.dto.ConversationResponse;
import com.relay.chat.dto.CreateDirectRequest;
import com.relay.chat.dto.CreateGroupRequest;
import com.relay.chat.dto.MemberResponse;
import com.relay.chat.exception.ApiException;
import com.relay.chat.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/direct")
    ResponseEntity<ConversationResponse> createDirect(@Valid @RequestBody CreateDirectRequest request,
                                                      @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.createDirect(userId(userId), request.peerId()));
    }

    @PostMapping("/group")
    ResponseEntity<ConversationResponse> createGroup(@Valid @RequestBody CreateGroupRequest request,
                                                     @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.createGroup(userId(userId), request));
    }

    @PostMapping("/{id}/members")
    ResponseEntity<Void> addMember(@PathVariable UUID id, @Valid @RequestBody AddMemberRequest request,
                                   @RequestHeader(value = "X-User-Id", required = false) String userId) {
        conversationService.addMember(userId(userId), id, request.userId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/members/{targetId}")
    ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID targetId,
                                      @RequestHeader(value = "X-User-Id", required = false) String userId) {
        conversationService.removeMember(userId(userId), id, targetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    List<ConversationResponse> list(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return conversationService.listForUser(userId(userId));
    }

    @GetMapping("/{id}/members")
    List<MemberResponse> members(@PathVariable UUID id,
                                 @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return conversationService.listMembers(userId(userId), id);
    }

    private UUID userId(String header) {
        if (header == null || header.isBlank()) {
            throw ApiException.forbidden("missing user context");
        }
        return UUID.fromString(header);
    }
}
