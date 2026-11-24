-- chatdb schema (chat-service): conversation aggregate + outbox. No messages here.
CREATE TABLE conversations (
  id          UUID PRIMARY KEY,
  type        VARCHAR(8) NOT NULL CHECK (type IN ('DIRECT','GROUP')),
  name        VARCHAR(128),
  direct_key  VARCHAR(80) UNIQUE,
  created_by  UUID NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_members (
  conversation_id UUID NOT NULL REFERENCES conversations(id),
  user_id         UUID NOT NULL,
  role            VARCHAR(8) NOT NULL DEFAULT 'MEMBER',
  joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX idx_members_user ON conversation_members(user_id);

CREATE TABLE outbox (
  id           BIGSERIAL PRIMARY KEY,
  aggregate_id UUID NOT NULL,
  topic        VARCHAR(64) NOT NULL,
  payload      JSONB NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;
