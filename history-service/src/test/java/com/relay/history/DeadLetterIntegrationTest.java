package com.relay.history;

import com.relay.events.Topics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "relay.history.outbox-delay-ms=1000000"
})
@EmbeddedKafka(partitions = 12, topics = {Topics.MESSAGE_COMMAND, Topics.MESSAGE_COMMAND + ".DLT"})
@Testcontainers
class DeadLetterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private KafkaTemplate<String, String> template;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void poisonCommandIsRoutedToDeadLetterTopic() throws Exception {
        String key = UUID.randomUUID().toString();
        template.send(Topics.MESSAGE_COMMAND, key, "this-is-not-json").get(10, java.util.concurrent.TimeUnit.SECONDS);

        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-test-" + UUID.randomUUID(), "true", broker);
        try (Consumer<String, String> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
                props, new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer())) {
            consumer.subscribe(java.util.List.of(Topics.MESSAGE_COMMAND + ".DLT"));
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, Topics.MESSAGE_COMMAND + ".DLT", java.time.Duration.ofSeconds(25));
            assertThat(record.value()).contains("this-is-not-json");
            assertThat(record.key()).isEqualTo(key);
        }
    }
}
