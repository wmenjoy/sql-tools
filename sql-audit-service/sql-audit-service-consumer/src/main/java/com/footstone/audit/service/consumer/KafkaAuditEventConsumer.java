package com.footstone.audit.service.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footstone.audit.service.core.model.AuditProcessingResult;
import com.footstone.audit.service.core.processor.AuditEventProcessor;
import com.footstone.sqlguard.audit.AuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka审计事件消费者 - SQL审计服务的核心消费组件
 * Kafka Audit Event Consumer - Core consuming component of SQL Audit Service
 *
 * <p>架构职责 (Architectural Responsibilities):</p>
 * <ul>
 *   <li>从Kafka Topic消费SQL审计事件 (Consumes SQL audit events from Kafka topic)</li>
 *   <li>反序列化JSON消息为AuditEvent对象 (Deserializes JSON messages to AuditEvent objects)</li>
 *   <li>委托给AuditEventProcessor执行审计逻辑 (Delegates to AuditEventProcessor for audit logic execution)</li>
 *   <li>管理Kafka消息确认机制 (Manages Kafka message acknowledgement mechanism)</li>
 *   <li>集成背压控制和错误处理 (Integrates backpressure control and error handling)</li>
 *   <li>收集消费指标供监控系统使用 (Collects consumption metrics for monitoring systems)</li>
 * </ul>
 *
 * <p>消费流程 (Consumption Flow):</p>
 * <ol>
 *   <li>从Kafka拉取消息 (Pull message from Kafka)</li>
 *   <li>校验消息非空 (Validate message is not empty)</li>
 *   <li>反序列化为AuditEvent (Deserialize to AuditEvent)</li>
 *   <li>调用AuditEventProcessor.process() (Invoke AuditEventProcessor.process())</li>
 *   <li>记录处理时延和吞吐量 (Record processing latency and throughput)</li>
 *   <li>更新背压控制指标 (Update backpressure control metrics)</li>
 *   <li>手动确认消息 (Manually acknowledge message)</li>
 *   <li>异常时记录错误并触发错误处理 (Log error and trigger error handling on exception)</li>
 * </ol>
 *
 * <p>虚拟线程集成 (Virtual Thread Integration):</p>
 * 本消费者配置使用虚拟线程容器工厂 (containerFactory = "virtualThreadKafkaListenerContainerFactory"),
 * 每个消息处理在独立的虚拟线程中执行,支持高并发低资源消耗的处理模式。
 * This consumer is configured with virtual thread container factory, each message processing executes
 * in a dedicated virtual thread, enabling high-concurrency low-resource processing pattern.
 *
 * <p>背压控制集成 (Backpressure Control Integration):</p>
 * 与BackpressureHandler协同工作,监控处理时延和失败率,在系统过载时自动暂停消费,
 * 防止下游服务崩溃,确保系统稳定性。
 * Cooperates with BackpressureHandler to monitor processing latency and failure rate,
 * automatically pauses consumption when system is overloaded, preventing downstream service
 * crashes and ensuring system stability.
 *
 * <p>错误处理策略 (Error Handling Strategy):</p>
 * <ul>
 *   <li>反序列化失败: 记录错误日志,委托AuditEventErrorHandler处理,确认消息 (防止阻塞)
 *       (Deserialization failure: Log error, delegate to AuditEventErrorHandler, acknowledge message to prevent blocking)</li>
 *   <li>处理异常: 记录失败指标,抛出异常触发Kafka重试机制
 *       (Processing exception: Record failure metrics, throw exception to trigger Kafka retry mechanism)</li>
 * </ul>
 *
 * @see AuditEventProcessor Core审计处理接口 (Core audit processing interface)
 * @see AuditEventErrorHandler 错误处理组件 (Error handling component)
 * @see BackpressureHandler 背压控制组件 (Backpressure control component)
 * @see SqlAuditConsumerMetrics 消费指标收集组件 (Consumption metrics collection component)
 * @see VirtualThreadKafkaConfig 虚拟线程配置 (Virtual thread configuration)
 * @since 1.0.0
 */
@Component
@Slf4j
public class KafkaAuditEventConsumer {

    /**
     * Jackson对象映射器,用于JSON反序列化
     * Jackson ObjectMapper for JSON deserialization
     */
    private final ObjectMapper objectMapper;

    /**
     * 审计事件处理器 - 核心审计引擎接口
     * Audit event processor - Core audit engine interface
     *
     * 由sql-audit-service-core模块的DefaultAuditEngine实现,负责执行所有审计检查器,
     * 聚合风险评分,持久化审计报告。
     * Implemented by DefaultAuditEngine from sql-audit-service-core module, responsible for
     * executing all audit checkers, aggregating risk scores, persisting audit reports.
     */
    private final AuditEventProcessor auditEventProcessor;

    /**
     * 审计事件错误处理器 - 处理反序列化失败和DLQ发送
     * Audit event error handler - Handles deserialization failures and DLQ sending
     */
    private final AuditEventErrorHandler errorHandler;

    /**
     * 背压处理器 - 监控系统负载并自动暂停/恢复消费
     * Backpressure handler - Monitors system load and auto-pauses/resumes consumption
     */
    private final BackpressureHandler backpressureHandler;

    /**
     * Kafka消费指标收集器 - 记录吞吐量、处理时延、错误率
     * Kafka consumer metrics collector - Records throughput, processing latency, error rate
     */
    private final SqlAuditConsumerMetrics metrics;

    /**
     * 构造函数 - 依赖注入所有必需组件
     * Constructor - Dependency injection of all required components
     *
     * @param objectMapper Jackson对象映射器 (Jackson ObjectMapper)
     * @param auditEventProcessor 审计事件处理器 (Audit event processor)
     * @param errorHandler 错误处理器 (Error handler)
     * @param backpressureHandler 背压处理器 (Backpressure handler)
     * @param metrics 指标收集器 (Metrics collector)
     */
    public KafkaAuditEventConsumer(ObjectMapper objectMapper,
                                   AuditEventProcessor auditEventProcessor,
                                   AuditEventErrorHandler errorHandler,
                                   BackpressureHandler backpressureHandler,
                                   SqlAuditConsumerMetrics metrics) {
        this.objectMapper = objectMapper;
        this.auditEventProcessor = auditEventProcessor;
        this.errorHandler = errorHandler;
        this.backpressureHandler = backpressureHandler;
        this.metrics = metrics;

        log.info("KafkaAuditEventConsumer initialized with auditEventProcessor: {}",
                auditEventProcessor.getClass().getSimpleName());
    }

    /**
     * 消费Kafka审计事件 - 主处理方法
     * Consume Kafka audit event - Main processing method
     *
     * <p>处理流程 (Processing Flow):</p>
     * <ol>
     *   <li>记录开始时间用于时延计算 (Record start time for latency calculation)</li>
     *   <li>校验消息非空 (Validate message is not empty)</li>
     *   <li>反序列化JSON为AuditEvent (Deserialize JSON to AuditEvent)</li>
     *   <li>调用AuditEventProcessor.process()执行审计逻辑 (Invoke AuditEventProcessor.process())</li>
     *   <li>手动确认消息 (Manually acknowledge message)</li>
     *   <li>更新成功指标:吞吐量、时延、背压状态 (Update success metrics)</li>
     *   <li>异常处理:反序列化失败→DLQ,处理失败→重试 (Exception handling)</li>
     * </ol>
     *
     * <p>虚拟线程执行 (Virtual Thread Execution):</p>
     * 本方法在虚拟线程中执行,支持阻塞I/O操作而不消耗平台线程。
     * 每个消息处理在独立虚拟线程中运行,即使处理时间较长也不影响整体吞吐量。
     * This method executes in a virtual thread, supporting blocking I/O operations without
     * consuming platform threads. Each message processing runs in a dedicated virtual thread,
     * even if processing time is long, it doesn't impact overall throughput.
     *
     * <p>手动确认模式 (Manual Acknowledge Mode):</p>
     * 使用手动确认模式 (enable.auto.commit=false),只有在成功处理或明确决定跳过时才确认消息,
     * 确保消息不会在处理失败时丢失。反序列化失败的消息会被确认并发送到DLQ,防止阻塞消费。
     * Uses manual acknowledge mode (enable.auto.commit=false), only acknowledges message upon
     * successful processing or explicit skip decision, ensuring messages are not lost on failure.
     * Messages with deserialization failures are acknowledged and sent to DLQ to prevent blocking.
     *
     * @param message Kafka消息内容 (JSON格式的AuditEvent) (Kafka message content - AuditEvent in JSON)
     * @param partition Kafka分区号 (Kafka partition number)
     * @param offset Kafka偏移量 (Kafka offset)
     * @param acknowledgment Kafka手动确认器 (Kafka manual acknowledger)
     *
     * @throws Exception 处理异常时抛出,触发Kafka重试机制
     *                   (Thrown on processing exception, triggers Kafka retry mechanism)
     */
    @KafkaListener(
        topics = "${audit.kafka.topic:sql-audit-events}",
        groupId = "${audit.kafka.consumer.group-id:audit-service}",
        containerFactory = "virtualThreadKafkaListenerContainerFactory"
    )
    public void consume(
        @Payload String message,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment) {

        // 记录开始时间用于时延计算 (Record start time for latency calculation)
        long startTime = System.currentTimeMillis();

        // 空消息检查 - 确认并跳过,避免阻塞后续消息
        // Empty message check - Acknowledge and skip to avoid blocking subsequent messages
        if (message == null || message.trim().isEmpty()) {
            log.warn("Received empty or null message from partition {} offset {}", partition, offset);
            acknowledgment.acknowledge();
            return;
        }

        try {
            // 反序列化JSON消息为AuditEvent对象
            // Deserialize JSON message to AuditEvent object
            AuditEvent event = objectMapper.readValue(message, AuditEvent.class);

            // 执行审计处理 - 调用Core模块的审计引擎
            // Execute audit processing - Invoke Core module's audit engine
            // DefaultAuditEngine会执行所有启用的检查器,聚合风险评分,持久化报告
            // DefaultAuditEngine executes all enabled checkers, aggregates risk scores, persists reports
            AuditProcessingResult result = auditEventProcessor.process(event);

            // 手动确认Kafka消息 - 处理成功后移除消息
            // Manually acknowledge Kafka message - Remove message after successful processing
            acknowledgment.acknowledge();

            long duration = System.currentTimeMillis() - startTime;

            // 更新成功指标 (Update success metrics)
            metrics.incrementThroughput(); // 吞吐量计数器 (Throughput counter)
            metrics.recordProcessingTime(duration); // 处理时延 (Processing latency)
            backpressureHandler.recordLatency("audit-service", duration); // 背压时延监控 (Backpressure latency monitoring)
            backpressureHandler.recordSuccess("audit-service"); // 背压成功计数 (Backpressure success count)

            // 记录成功日志 (仅DEBUG级别避免日志过多)
            // Log success (DEBUG level only to avoid excessive logging)
            if (log.isDebugEnabled()) {
                log.debug("Successfully processed audit event from partition {} offset {} in {}ms, result: {}",
                        partition, offset, duration, result.success() ? "SUCCESS" : "FAILED");
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize message from partition {} offset {}: {}",
                    partition, offset, message, e);

            metrics.incrementErrors(); // 错误计数器 (Error counter)
            errorHandler.handleDeserializationError(message, e); // 发送到DLQ (Send to DLQ)
            acknowledgment.acknowledge(); // 确认消息防止重复处理 (Acknowledge to prevent reprocessing)

        } catch (Exception e) {
               log.error("Error processing message from partition {} offset {}", partition, offset, e);

            metrics.incrementErrors(); // 错误计数器 (Error counter)
            backpressureHandler.recordFailure("audit-service"); // 背压失败计数 (Backpressure failure count)

            throw e;
        }
    }
}
