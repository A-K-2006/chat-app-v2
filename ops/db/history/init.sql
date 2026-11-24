-- historydb schema (history-service): message system of record + read model + outbox.
CREATE TABLE messages (
  conversation_id   UUID NOT NULL,
  client_message_id UUID NOT NULL,
  seq               BIGINT NOT NULL,
  sender_id         UUID NOT NULL,
  body              VARCHAR(4096) NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (conversation_id, client_message_id),
  UNIQUE (conversation_id, seq)
) PARTITION BY HASH (conversation_id);

CREATE TABLE messages_p0 PARTITION OF messages FOR VALUES WITH (MODULUS 8, REMAINDER 0);
CREATE TABLE messages_p1 PARTITION OF messages FOR VALUES WITH (MODULUS 8, REMAINDER 1);
CREATE TABLE messages_p2 PARTITION OF messages FOR VALUES WITH (MODULUS 8, REMAINDER 2);
CREATE TABLE messages_p3 PARTITION OF messages FOR VALUES WITH (MODULUS 8, REMAINDER 3);
CREATE TABLE messages_p4 PARTITION OF messages FOR VALUES WITH (MODULUS 8, REMAINDER 4);
CREATE TABLE messages_p5 PARTITION OF messages FOR VALUES WITH (MODULUS 8, REMAINDER 5);
CREATE TABLE messages_p6 PARTITION OF messages FOR VALUES WITH (MODULUS 8, REMAINDER 6);
CREATE TABLE messages_p7 PARTITION OF messages FOR VALUES WITH (MODULUS 8, REMAINDER 7);

CREATE INDEX idx_messages_conv_seq ON messages (conversation_id, seq DESC);

CREATE TABLE conversation_state (
  conversation_id UUID PRIMARY KEY,
  last_seq        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE membership_projection (
  conversation_id UUID NOT NULL,
  user_id         UUID NOT NULL,
  PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE read_state (
  conversation_id    UUID NOT NULL,
  user_id            UUID NOT NULL,
  last_delivered_seq BIGINT NOT NULL DEFAULT 0,
  last_read_seq      BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE outbox (
  id           BIGSERIAL PRIMARY KEY,
  aggregate_id UUID NOT NULL,
  topic        VARCHAR(64) NOT NULL,
  payload      JSONB NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;
