package com.footstone.audit.service.consumer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka消费者配置属性.
 *
 * <h2>配置示例</h2>
 * <pre>
 * audit:
 *   kafka:
 *     topic: sql-audit-events
 *     consumer:
 *       group-id: audit-service
 *       dlq-topic: sql-audit-events-dlq
 *       error-handler:
 *         retry-initial-interval: 1000
 *         retry-multiplier: 2.0
 *         max-attempts: 3
 *       backpressure:
 *         enabled: true
 *         latency-threshold-ms: 200
 *         failure-threshold: 5
 *         check-interval-ms: 5000
 *       virtual-thread:
 *         enabled: true
 *         name-prefix: kafka-virtual-
 * </pre>
 *
 * @since 2.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "audit.kafka.consumer")
@Data
public class KafkaConsumerProperties {

    /**
     * 消费者组ID.
     * <p>用于标识Kafka消费者组，同一组内的消费者会分担消息消费负载</p>
     * <p>默认值: audit-service</p>
     */
    private String groupId = "audit-service";

    /**
     * 死信队列(DLQ)主题名称.
     * <p>当消息处理失败且重试次数超过限制后，消息将被发送到此主题</p>
     * <p>默认值: sql-audit-events-dlq</p>
     */
    private String dlqTopic = "sql-audit-events-dlq";

    /**
     * 错误处理器配置.
     */
    private ErrorHandler errorHandler = new ErrorHandler();

    /**
     * 背压控制配置.
     * <p>用于在系统负载过高时自动暂停消费，避免系统崩溃</p>
     */
    private Backpressure backpressure = new Backpressure();

    /**
     * 虚拟线程配置.
     * <p>启用虚拟线程可以提高并发处理能力，降低线程切换开销</p>
     */
    private VirtualThread virtualThread = new VirtualThread();

    /**
     * 错误处理器配置.
     */
    @Data
    public static class ErrorHandler {
        /**
         * 重试初始间隔时间(毫秒).
         * <p>第一次重试前的等待时间</p>
         * <p>默认值: 1000ms (1秒)</p>
         */
        private long retryInitialInterval = 1000L;

        /**
         * 重试间隔倍数.
         * <p>每次重试后，等待时间会乘以此倍数(指数退避)</p>
         * <p>例如: initialInterval=1000, multiplier=2.0, 则重试间隔为: 1s, 2s, 4s, 8s...</p>
         * <p>默认值: 2.0</p>
         */
        private double retryMultiplier = 2.0;

        /**
         * 最大重试次数.
         * <p>超过此次数后，消息将被发送到DLQ</p>
         * <p>默认值: 3次</p>
         */
        private int maxAttempts = 3;
    }

    /**
     * 背压控制配置.
     */
    @Data
    public static class Backpressure {
        /**
         * 是否启用背压控制.
         * <p>启用后会监控处理延迟和失败率，在系统负载过高时自动暂停消费</p>
         * <p>默认值: true</p>
         */
        private boolean enabled = true;

        /**
         * 延迟阈值(毫秒).
         * <p>当消息处理延迟超过此阈值时，会触发背压机制暂停消费</p>
         * <p>建议值: 对于实时系统设为100-200ms，对于批处理系统可设为500-1000ms</p>
         * <p>默认值: 200ms</p>
         */
        private long latencyThresholdMs = 200;

        /**
         * 失败次数阈值.
         * <p>当连续失败次数超过此阈值时，会触发背压机制暂停消费</p>
         * <p>建议值: 3-10次，取决于业务容错能力</p>
         * <p>默认值: 5次</p>
         */
        private int failureThreshold = 5;

        /**
         * 背压检查间隔(毫秒).
         * <p>定期检查系统状态，决定是否需要暂停/恢复消费</p>
         * <p>默认值: 5000ms (5秒)</p>
         */
        private long checkIntervalMs = 5000;
    }

    /**
     * 虚拟线程配置.
     */
    @Data
    public static class VirtualThread {
        /**
         * 是否启用虚拟线程.
         * <p>需要JDK 21+支持</p>
         * <p>默认值: true</p>
         */
        private boolean enabled = true;

        /**
         * 虚拟线程名称前缀.
         * <p>用于日志和监控中识别线程来源</p>
         * <p>默认值: kafka-virtual-</p>
         */
        private String namePrefix = "kafka-virtual-";

        /**
         * 并发消费者数量.
         * <p>Kafka监听器容器的并发数，每个并发实例会处理不同的分区</p>
         * <p>建议值: 与Kafka主题分区数相同或略小</p>
         * <p>默认值: 1</p>
         */
        private int concurrency = 1;
    }
}
