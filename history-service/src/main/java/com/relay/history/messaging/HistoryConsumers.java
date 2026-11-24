package com.relay.history.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.events.ConversationEvent;
import com.relay.events.MessageCommandEvent;
import com.relay.events.ReceiptEvent;
import com.relay.events.ReceiptType;
import com.relay.events.Topics;
import com.relay.history.service.MessageIngestService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class HistoryConsumers {

    private final ObjectMapper objectMapper;
    private final MessageIngestService ingestService;
    private final JdbcTemplate jdbc;

    public HistoryConsumers(ObjectMapper objectMapper, MessageIngestService ingestService, JdbcTemplate jdbc) {
        this.objectMapper = objectMapper;
        this.ingestService = ingestService;
        this.jdbc = jdbc;
    }

    @KafkaListener(topics = Topics.MESSAGE_COMMAND, groupId = "history")
    public void onCommand(String payload) throws Exception {
        ingestService.ingest(objectMapper.readValue(payload, MessageCommandEvent.class));
    }

    @KafkaListener(topics = Topics.CONVERSATION_EVENTS, groupId = "history-conv")
    public void onConversationEvent(String payload) throws Exception {
        ConversationEvent event = objectMapper.readValue(payload, ConversationEvent.class);
        UUID conversationId = event.conversationId();
        switch (event.type()) {
            case CREATED, MEMBER_ADDED -> {
                for (UUID memberId : event.memberIds()) {
                    jdbc.update("insert into membership_projection (conversation_id, user_id) values (?, ?) "
                            + "on conflict (conversation_id, user_id) do nothing", conversationId, memberId);
                }
            }
            case MEMBER_REMOVED -> {
                for (UUID memberId : event.memberIds()) {
                    jdbc.update("delete from membership_projection where conversation_id = ? and user_id = ?",
                            conversationId, memberId);
                }
            }
        }
    }

    @KafkaListener(topics = Topics.RECEIPT, groupId = "history-receipt")
    public void onReceipt(String payload) throws Exception {
        ReceiptEvent event = objectMapper.readValue(payload, ReceiptEvent.class);
        long delivered = event.upToSeq();
        long read = event.type() == ReceiptType.READ ? event.upToSeq() : 0;
        jdbc.update("insert into read_state (conversation_id, user_id, last_delivered_seq, last_read_seq) "
                        + "values (?, ?, ?, ?) on conflict (conversation_id, user_id) do update set "
                        + "last_delivered_seq = greatest(read_state.last_delivered_seq, excluded.last_delivered_seq), "
                        + "last_read_seq = greatest(read_state.last_read_seq, excluded.last_read_seq)",
                event.conversationId(), event.userId(), delivered, read);
    }
}
