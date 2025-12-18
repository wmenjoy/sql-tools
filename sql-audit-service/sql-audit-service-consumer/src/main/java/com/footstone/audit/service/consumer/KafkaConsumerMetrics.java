package com.footstone.audit.service.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class KafkaConsumerMetrics {

    private final MeterRegistry registry;
    private final AtomicLong currentLag = new AtomicLong(0);

    public KafkaConsumerMetrics(MeterRegistry registry) {
        this.registry = registry;
        
        // Register gauge for lag
        registry.gauge("audit.kafka.lag", currentLag);
    }

    public void incrementThroughput() {
        registry.counter("audit.kafka.throughput").increment();
    }

    public void incrementErrors() {
        registry.counter("audit.kafka.errors").increment();
    }
    
    public void incrementDlq() {
        registry.counter("audit.kafka.dlq").increment();
    }

    public void recordProcessingTime(long timeMs) {
        Timer.builder("audit.kafka.processing.time")
             .description("Time taken to process audit event")
             .register(registry)
             .record(timeMs, TimeUnit.MILLISECONDS);
    }

    public void recordLag(long lag) {
        currentLag.set(lag);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}
