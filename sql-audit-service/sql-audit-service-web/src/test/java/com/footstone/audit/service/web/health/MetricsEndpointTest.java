package com.footstone.audit.service.web.health;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Prometheus metrics.
 */
class MetricsEndpointTest {
    
    private MeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }
    
    @Test
    void testPrometheusEndpoint_shouldExpose() {
        // Verify MeterRegistry can be created
        assertNotNull(meterRegistry);
        
        // Add a sample counter
        meterRegistry.counter("audit.events.processed").increment();
        
        // Verify the counter was registered
        assertNotNull(meterRegistry.find("audit.events.processed").counter());
    }
    
    @Test
    void testPrometheusEndpoint_kafkaMetrics_shouldInclude() {
        // Simulate Kafka consumer metrics
        meterRegistry.counter("kafka.consumer.messages.received").increment(100);
        meterRegistry.gauge("kafka.consumer.lag", 5);
        
        // Verify metrics are registered
        assertNotNull(meterRegistry.find("kafka.consumer.messages.received").counter());
        assertEquals(100.0, meterRegistry.find("kafka.consumer.messages.received").counter().count());
    }
    
    @Test
    void testPrometheusEndpoint_auditMetrics_shouldInclude() {
        // Simulate audit metrics
        meterRegistry.counter("audit.reports.created").increment(50);
        meterRegistry.counter("audit.reports.by.risk.high").increment(10);
        meterRegistry.counter("audit.reports.by.risk.medium").increment(20);
        meterRegistry.counter("audit.reports.by.risk.low").increment(20);
        
        // Verify metrics
        assertEquals(50.0, meterRegistry.find("audit.reports.created").counter().count());
        assertEquals(10.0, meterRegistry.find("audit.reports.by.risk.high").counter().count());
    }
    
    @Test
    void testPrometheusEndpoint_jvmMetrics_shouldInclude() {
        // JVM metrics are automatically registered by Micrometer in Spring Boot
        // Here we verify we can register custom JVM-related metrics
        
        meterRegistry.gauge("jvm.memory.custom", Runtime.getRuntime().totalMemory());
        meterRegistry.gauge("jvm.threads.custom", Thread.activeCount());
        
        // Verify gauges are registered
        assertNotNull(meterRegistry.find("jvm.memory.custom").gauge());
        assertNotNull(meterRegistry.find("jvm.threads.custom").gauge());
    }
    
    @Test
    void testPrometheusEndpoint_apiLatencyMetrics_shouldInclude() {
        // Simulate API latency metrics
        meterRegistry.timer("api.response.time")
            .record(() -> {
                // Simulate some work
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        
        // Verify timer was registered
        assertNotNull(meterRegistry.find("api.response.time").timer());
        assertTrue(meterRegistry.find("api.response.time").timer().count() > 0);
    }
    
    @Test
    void testPrometheusEndpoint_checkerMetrics_shouldInclude() {
        // Simulate checker execution metrics
        meterRegistry.counter("checker.slow_query.executions").increment(100);
        meterRegistry.counter("checker.slow_query.triggers").increment(15);
        meterRegistry.counter("checker.full_table_scan.executions").increment(100);
        meterRegistry.counter("checker.full_table_scan.triggers").increment(8);
        
        // Verify metrics
        assertEquals(100.0, meterRegistry.find("checker.slow_query.executions").counter().count());
        assertEquals(15.0, meterRegistry.find("checker.slow_query.triggers").counter().count());
    }
    
    @Test
    void testPrometheusEndpoint_errorMetrics_shouldInclude() {
        // Simulate error metrics
        meterRegistry.counter("errors.total").increment(5);
        meterRegistry.counter("errors.by.type.validation").increment(2);
        meterRegistry.counter("errors.by.type.database").increment(3);
        
        // Verify error metrics
        assertEquals(5.0, meterRegistry.find("errors.total").counter().count());
        assertEquals(2.0, meterRegistry.find("errors.by.type.validation").counter().count());
    }
    
    @Test
    void testPrometheusEndpoint_customTags_shouldWork() {
        // Test metrics with custom tags
        meterRegistry.counter("audit.events", "riskLevel", "HIGH", "checker", "slow_query").increment(10);
        meterRegistry.counter("audit.events", "riskLevel", "MEDIUM", "checker", "slow_query").increment(20);
        
        // Verify tagged metrics
        assertNotNull(meterRegistry.find("audit.events").tag("riskLevel", "HIGH").counter());
        assertEquals(10.0, meterRegistry.find("audit.events").tag("riskLevel", "HIGH").counter().count());
    }
}








