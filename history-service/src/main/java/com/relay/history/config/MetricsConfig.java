package com.relay.history.config;

import com.relay.history.repository.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    Gauge outboxPendingGauge(OutboxRepository outbox, MeterRegistry meterRegistry) {
        return Gauge.builder("outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull)
                .description("Unpublished outbox rows")
                .register(meterRegistry);
    }
}
