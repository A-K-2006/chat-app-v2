package com.relay.chat.config;

import com.relay.chat.repository.OutboxRepository;
import com.relay.chat.security.SessionRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    Gauge wsConnectionsActiveGauge(SessionRegistry registry, MeterRegistry meterRegistry) {
        return Gauge.builder("ws.connections.active", registry, SessionRegistry::activeConnections)
                .description("Active WebSocket sessions on this instance")
                .register(meterRegistry);
    }

    @Bean
    Gauge outboxPendingGauge(OutboxRepository outbox, MeterRegistry meterRegistry) {
        return Gauge.builder("outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull)
                .description("Unpublished outbox rows")
                .register(meterRegistry);
    }

    @Bean
    Timer messageE2eLatencyTimer(MeterRegistry meterRegistry) {
        return Timer.builder("message.e2e.latency")
                .description("Client send to recipient delivery latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }
}
