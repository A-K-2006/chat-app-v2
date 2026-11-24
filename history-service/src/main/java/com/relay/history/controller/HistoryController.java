package com.relay.history.controller;

import com.relay.history.dto.MessageView;
import com.relay.history.dto.ReadStateView;
import com.relay.history.exception.ApiException;
import com.relay.history.service.HistoryQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/history")
public class HistoryController {

    private final HistoryQueryService queryService;

    public HistoryController(HistoryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/conversations/{id}/messages")
    List<MessageView> messages(@PathVariable UUID id,
                               @RequestParam(defaultValue = "0") long afterSeq,
                               @RequestParam(defaultValue = "50") int limit,
                               @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return queryService.messages(userId(userId), id, afterSeq, limit);
    }

    @GetMapping("/conversations/{id}/state")
    ReadStateView state(@PathVariable UUID id,
                        @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return queryService.state(userId(userId), id);
    }

    private UUID userId(String header) {
        if (header == null || header.isBlank()) {
            throw ApiException.forbidden("missing user context");
        }
        return UUID.fromString(header);
    }
}
