#!/bin/bash
set -euo pipefail

BOOTSTRAP="kafka:9092"
TOPICS=(
  chat.message.command
  chat.message.persisted
  chat.conversation.events
  chat.presence.connection
  chat.presence.changed
  chat.typing
  chat.receipt
)

for t in "${TOPICS[@]}"; do
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists --topic "$t" --partitions 12 --replication-factor 1
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists --topic "$t.DLT" --partitions 12 --replication-factor 1
done

echo "--- topics ---"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --list
