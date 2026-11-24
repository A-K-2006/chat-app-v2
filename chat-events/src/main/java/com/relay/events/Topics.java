package com.relay.events;

public final class Topics {
    public static final String MESSAGE_COMMAND = "chat.message.command";
    public static final String MESSAGE_PERSISTED = "chat.message.persisted";
    public static final String CONVERSATION_EVENTS = "chat.conversation.events";
    public static final String PRESENCE_CONNECTION = "chat.presence.connection";
    public static final String PRESENCE_CHANGED = "chat.presence.changed";
    public static final String TYPING = "chat.typing";
    public static final String RECEIPT = "chat.receipt";

    public static final int PARTITIONS = 12;

    private Topics() {
    }
}
