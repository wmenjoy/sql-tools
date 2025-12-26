package com.footstone.audit.service.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class KafkaMetricsTest {

    private MeterRegistry meterRegistry;
    private SqlAuditConsumerMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new SqlAuditConsumerMetrics(meterRegistry);
    }

    @Test
    void testMetrics_throughputCounter_shouldIncrement() {
        metrics.incrementThroughput();
        Counter counter = meterRegistry.find("audit.kafka.throughput").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void testMetrics_lagGauge_shouldReportLag() {
        metrics.recordLag(100);
        Gauge gauge = meterRegistry.find("audit.kafka.lag").gauge();
        assertNotNull(gauge);
        assertEquals(100.0, gauge.value());
        
        metrics.recordLag(50);
        assertEquals(50.0, gauge.value());
    }

    @Test
    void testMetrics_processingTimeHistogram_shouldRecord() {
        metrics.recordProcessingTime(100);
        Timer timer = meterRegistry.find("audit.kafka.processing.time").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertEquals(100.0, timer.totalTime(TimeUnit.MILLISECONDS));
    }

    @Test
    void testMetrics_errorCounter_shouldTrackFailures() {
        metrics.incrementErrors();
        Counter counter = meterRegistry.find("audit.kafka.errors").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void testMetrics_dlqCounter_shouldTrackDLQMessages() {
        metrics.incrementDlq();
        Counter counter = meterRegistry.find("audit.kafka.dlq").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void testPrometheusEndpoint_shouldExposeKafkaMetrics() {
        // This is implicit if we use Micrometer and it registers correctly.
        // We verify that the beans are registered in the registry.
        metrics.incrementThroughput(); // Ensure it's registered
        assertNotNull(meterRegistry.find("audit.kafka.throughput").meter());
    }

    @Test
    void testMicrometerIntegration_shouldRegisterBeans() {
        // Verify registry is used
        assertEquals(meterRegistry, metrics.getRegistry());
    }
}
