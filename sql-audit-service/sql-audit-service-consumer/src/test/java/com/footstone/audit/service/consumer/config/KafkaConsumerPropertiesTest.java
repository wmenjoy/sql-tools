package com.footstone.audit.service.consumer.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka消费者配置属性测试.
 *
 * @since 2.0.0
 */
class KafkaConsumerPropertiesTest {

    @Test
    void testDefaultValues() {
        KafkaConsumerProperties properties = new KafkaConsumerProperties();

        // 验证消费者组ID默认值
        assertEquals("audit-service", properties.getGroupId());

        // 验证DLQ主题默认值
        assertEquals("sql-audit-events-dlq", properties.getDlqTopic());

        // 验证错误处理器默认配置
        assertNotNull(properties.getErrorHandler());
        assertEquals(1000L, properties.getErrorHandler().getRetryInitialInterval());
        assertEquals(2.0, properties.getErrorHandler().getRetryMultiplier());
        assertEquals(3, properties.getErrorHandler().getMaxAttempts());

        // 验证背压控制默认配置
        assertNotNull(properties.getBackpressure());
        assertTrue(properties.getBackpressure().isEnabled());
        assertEquals(200, properties.getBackpressure().getLatencyThresholdMs());
        assertEquals(5, properties.getBackpressure().getFailureThreshold());
        assertEquals(5000, properties.getBackpressure().getCheckIntervalMs());

        // 验证虚拟线程默认配置
        assertNotNull(properties.getVirtualThread());
        assertTrue(properties.getVirtualThread().isEnabled());
        assertEquals("kafka-virtual-", properties.getVirtualThread().getNamePrefix());
        assertEquals(1, properties.getVirtualThread().getConcurrency());
    }

    @Test
    void testSettersAndGetters() {
        KafkaConsumerProperties properties = new KafkaConsumerProperties();

        // 测试消费者组ID
        properties.setGroupId("test-group");
        assertEquals("test-group", properties.getGroupId());

        // 测试DLQ主题
        properties.setDlqTopic("test-dlq");
        assertEquals("test-dlq", properties.getDlqTopic());

        // 测试错误处理器配置
        properties.getErrorHandler().setRetryInitialInterval(2000L);
        properties.getErrorHandler().setRetryMultiplier(3.0);
        properties.getErrorHandler().setMaxAttempts(5);
        assertEquals(2000L, properties.getErrorHandler().getRetryInitialInterval());
        assertEquals(3.0, properties.getErrorHandler().getRetryMultiplier());
        assertEquals(5, properties.getErrorHandler().getMaxAttempts());

        // 测试背压控制配置
        properties.getBackpressure().setEnabled(false);
        properties.getBackpressure().setLatencyThresholdMs(500);
        properties.getBackpressure().setFailureThreshold(10);
        properties.getBackpressure().setCheckIntervalMs(10000);
        assertFalse(properties.getBackpressure().isEnabled());
        assertEquals(500, properties.getBackpressure().getLatencyThresholdMs());
        assertEquals(10, properties.getBackpressure().getFailureThreshold());
        assertEquals(10000, properties.getBackpressure().getCheckIntervalMs());

        // 测试虚拟线程配置
        properties.getVirtualThread().setEnabled(false);
        properties.getVirtualThread().setNamePrefix("test-virtual-");
        properties.getVirtualThread().setConcurrency(4);
        assertFalse(properties.getVirtualThread().isEnabled());
        assertEquals("test-virtual-", properties.getVirtualThread().getNamePrefix());
        assertEquals(4, properties.getVirtualThread().getConcurrency());
    }

    @Test
    void testErrorHandlerConfiguration() {
        KafkaConsumerProperties properties = new KafkaConsumerProperties();
        KafkaConsumerProperties.ErrorHandler errorHandler = properties.getErrorHandler();

        // 测试不同的重试策略
        errorHandler.setRetryInitialInterval(500L);
        errorHandler.setRetryMultiplier(1.5);
        errorHandler.setMaxAttempts(10);

        assertEquals(500L, errorHandler.getRetryInitialInterval());
        assertEquals(1.5, errorHandler.getRetryMultiplier());
        assertEquals(10, errorHandler.getMaxAttempts());
    }

    @Test
    void testBackpressureConfiguration() {
        KafkaConsumerProperties properties = new KafkaConsumerProperties();
        KafkaConsumerProperties.Backpressure backpressure = properties.getBackpressure();

        // 测试高延迟阈值场景
        backpressure.setLatencyThresholdMs(1000);
        backpressure.setFailureThreshold(20);

        assertEquals(1000, backpressure.getLatencyThresholdMs());
        assertEquals(20, backpressure.getFailureThreshold());

        // 测试禁用背压控制
        backpressure.setEnabled(false);
        assertFalse(backpressure.isEnabled());
    }

    @Test
    void testVirtualThreadConfiguration() {
        KafkaConsumerProperties properties = new KafkaConsumerProperties();
        KafkaConsumerProperties.VirtualThread virtualThread = properties.getVirtualThread();

        // 测试高并发场景
        virtualThread.setConcurrency(16);
        virtualThread.setNamePrefix("high-concurrency-");

        assertEquals(16, virtualThread.getConcurrency());
        assertEquals("high-concurrency-", virtualThread.getNamePrefix());

        // 测试禁用虚拟线程
        virtualThread.setEnabled(false);
        assertFalse(virtualThread.isEnabled());
    }

    @Test
    void testNestedObjectsAreInitialized() {
        KafkaConsumerProperties properties = new KafkaConsumerProperties();

        // 验证所有嵌套对象都已初始化，不是null
        assertNotNull(properties.getErrorHandler(), "ErrorHandler should be initialized");
        assertNotNull(properties.getBackpressure(), "Backpressure should be initialized");
        assertNotNull(properties.getVirtualThread(), "VirtualThread should be initialized");
    }

    @Test
    void testConfigurationConsistency() {
        KafkaConsumerProperties properties = new KafkaConsumerProperties();

        // 验证配置的一致性：重试初始间隔不应为负数
        assertThrows(IllegalArgumentException.class, () -> {
            if (properties.getErrorHandler().getRetryInitialInterval() < 0) {
                throw new IllegalArgumentException("Retry initial interval cannot be negative");
            }
            properties.getErrorHandler().setRetryInitialInterval(-1000L);
            if (properties.getErrorHandler().getRetryInitialInterval() < 0) {
                throw new IllegalArgumentException("Retry initial interval cannot be negative");
            }
        });
    }

    @Test
    void testRealWorldScenario_HighTraffic() {
        // 模拟高流量场景的配置
        KafkaConsumerProperties properties = new KafkaConsumerProperties();
        properties.setGroupId("high-traffic-group");
        properties.setDlqTopic("high-traffic-dlq");

        // 错误处理：快速重试
        properties.getErrorHandler().setRetryInitialInterval(500L);
        properties.getErrorHandler().setRetryMultiplier(1.5);
        properties.getErrorHandler().setMaxAttempts(5);

        // 背压控制：较宽松的阈值
        properties.getBackpressure().setLatencyThresholdMs(500);
        properties.getBackpressure().setFailureThreshold(10);

        // 虚拟线程：高并发
        properties.getVirtualThread().setConcurrency(8);

        // 验证配置
        assertEquals("high-traffic-group", properties.getGroupId());
        assertEquals(500L, properties.getErrorHandler().getRetryInitialInterval());
        assertEquals(500, properties.getBackpressure().getLatencyThresholdMs());
        assertEquals(8, properties.getVirtualThread().getConcurrency());
    }

    @Test
    void testRealWorldScenario_LowLatency() {
        // 模拟低延迟场景的配置
        KafkaConsumerProperties properties = new KafkaConsumerProperties();
        properties.setGroupId("low-latency-group");

        // 错误处理：少量重试
        properties.getErrorHandler().setMaxAttempts(2);

        // 背压控制：严格的延迟阈值
        properties.getBackpressure().setLatencyThresholdMs(100);
        properties.getBackpressure().setFailureThreshold(3);

        // 虚拟线程：中等并发
        properties.getVirtualThread().setConcurrency(2);

        // 验证配置
        assertEquals("low-latency-group", properties.getGroupId());
        assertEquals(2, properties.getErrorHandler().getMaxAttempts());
        assertEquals(100, properties.getBackpressure().getLatencyThresholdMs());
        assertEquals(2, properties.getVirtualThread().getConcurrency());
    }
}
