---
task_ref: "Task 10.2 - Kafka Consumer with Virtual Threads"
agent_assignment: "Agent_Audit_Service"
memory_log_path: ".apm/Memory/Phase_10_Audit_Service/Task_10_2_Kafka_Consumer_Virtual_Threads.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
parallel_with: "Task 10.3"
---

# APM Task Assignment: Kafka Consumer with Virtual Threads

## Task Reference
Implementation Plan: **Task 10.2 - Kafka Consumer with Virtual Threads** assigned to **Agent_Audit_Service**

## Parallel Execution Note
**此任务与 Task 10.3 并行执行。** 需要预定义共享接口以确保集成成功。

### 共享接口定义（Task 10.2 和 10.3 共用）

```java
// 放在 sql-audit-service-core 模块
package com.footstone.audit.service.core.processor;

/**
 * 审计事件处理器接口 - Task 10.2 调用, Task 10.3 实现
 */
public interface AuditEventProcessor {
    /**
     * 处理审计事件
     * @param event 审计事件
     * @return 处理结果
     */
    AuditProcessingResult process(AuditEvent event);
}
```

**Task 10.2 职责:** 定义并调用 `AuditEventProcessor` 接口，使用 Mock 实现进行测试
**Task 10.3 职责:** 实现 `AuditEventProcessor` 接口 (DefaultAuditEngine)

## Context from Dependencies

### Task 10.1 Output (Required)
This task depends on Task 10.1 completion:
- Maven multi-module project: `sql-audit-service/` with Java 21
- Sub-modules: `sql-audit-service-core/`, `sql-audit-service-web/`, `sql-audit-service-consumer/`
- Virtual Thread executor: `VirtualThreadConfig.java`
- application.yml with profiles (dev/staging/prod)

### Phase 8.1 Output (External)
- `AuditEvent` model from `sql-guard-audit-api` module
- Jackson JSON serialization with `@JsonCreator`
- Fields: sqlId, sql, sqlType, mapperId, datasource, params, executionTimeMs, rowsAffected, errorMessage, timestamp, violations

**Dependency Declaration:**
```xml
<dependency>
  <groupId>com.footstone.sqlguard</groupId>
  <artifactId>sql-guard-audit-api</artifactId>
  <version>${project.version}</version>
</dependency>
```

## Objective
Implement Kafka consumer subscribing to `sql-audit-events` topic with Virtual Thread-based message processing achieving 10,000 msg/s throughput target. Deserialize AuditEvent JSON from audit log interceptors, route to audit engine for checker execution, handle backpressure and error scenarios, and provide monitoring metrics for consumption lag and processing rates.

## Detailed Instructions

Complete in **5 exchanges**, one step per response. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Kafka Consumer Core Implementation (TDD)
**先写测试，再实现：**

1. **Write test class KafkaAuditEventConsumerTest** covering:
   - `testConsumeMessage_validAuditEvent_shouldDeserialize()`
   - `testConsumeMessage_validAuditEvent_shouldForwardToProcessor()`
   - `testConsumeMessage_invalidJson_shouldRejectToErrorHandler()`
   - `testConsumeMessage_nullMessage_shouldIgnore()`
   - `testConsumeMessage_emptyMessage_shouldIgnore()`
   - `testConsumerGroup_shouldUseConfiguredGroupId()`
   - `testTopicSubscription_shouldListenToCorrectTopic()`
   - `testAcknowledgment_manualMode_shouldCommitOnSuccess()`
   - `testAcknowledgment_manualMode_shouldNotCommitOnFailure()`
   - `testConsumer_shouldUseVirtualThreadExecutor()`

2. **Then implement KafkaAuditEventConsumer:**
   ```java
   @Component
   public class KafkaAuditEventConsumer {

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
           // Implementation
       }
   }
   ```

3. **Verify:** Unit tests pass, consumer receives messages correctly

### Step 2: Virtual Thread Kafka Integration (TDD)
**先写测试，再实现：**

1. **Write test class VirtualThreadKafkaConfigTest** covering:
   - `testKafkaListenerContainerFactory_shouldUseVirtualThreads()`
   - `testConcurrentConsumers_shouldScale()`
   - `testVirtualThread_shouldHandle10kConcurrentTasks()`
   - `testVirtualThread_shouldNotBlockOnIO()`
   - `testExecutorService_shouldUseVirtualThreadPerTask()`
   - `testKafkaConsumerConfig_shouldHaveCorrectDeserializer()`
   - `testKafkaConsumerConfig_shouldHaveAutoOffsetReset()`
   - `testKafkaConsumerConfig_shouldUseManualAck()`

2. **Then implement VirtualThreadKafkaConfig:**
   ```java
   @Configuration
   public class VirtualThreadKafkaConfig {

       @Bean
       public ConcurrentKafkaListenerContainerFactory<String, String>
           virtualThreadKafkaListenerContainerFactory(
               ConsumerFactory<String, String> consumerFactory) {
           ConcurrentKafkaListenerContainerFactory<String, String> factory =
               new ConcurrentKafkaListenerContainerFactory<>();
           factory.setConsumerFactory(consumerFactory);
           factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
           factory.getContainerProperties().setListenerTaskExecutor(
               new VirtualThreadTaskExecutor("kafka-virtual-"));
           return factory;
       }
   }
   ```

3. **Verify:** Virtual Thread integration tests pass

### Step 3: Backpressure & Error Handling (TDD)
**先写测试，再实现：**

1. **Write test class BackpressureHandlingTest** covering:
   - `testBackpressure_highLoad_shouldThrottle()`
   - `testBackpressure_normalLoad_shouldProcess()`
   - `testBackpressure_recovery_shouldResume()`
   - `testDynamicConcurrency_shouldAdjustBasedOnLatency()`
   - `testCircuitBreaker_shouldOpenOnFailures()`
   - `testCircuitBreaker_shouldCloseAfterRecovery()`

2. **Write test class ErrorHandlingTest** covering:
   - `testRetry_transientFailure_shouldRetry3Times()`
   - `testRetry_permanentFailure_shouldSendToDLQ()`
   - `testDLQ_shouldReceivePoisonMessages()`
   - `testDLQ_shouldPreserveOriginalMessage()`
   - `testErrorHandler_shouldLogError()`
   - `testErrorHandler_shouldUpdateMetrics()`

3. **Then implement:**
   - `BackpressureHandler` with dynamic concurrency adjustment
   - `AuditEventErrorHandler` with retry logic
   - Dead Letter Queue configuration

4. **Verify:** Error handling and backpressure tests pass

### Step 4: Monitoring & Metrics (TDD)
**先写测试，再实现：**

1. **Write test class KafkaMetricsTest** covering:
   - `testMetrics_throughputCounter_shouldIncrement()`
   - `testMetrics_lagGauge_shouldReportLag()`
   - `testMetrics_processingTimeHistogram_shouldRecord()`
   - `testMetrics_errorCounter_shouldTrackFailures()`
   - `testMetrics_dlqCounter_shouldTrackDLQMessages()`
   - `testPrometheusEndpoint_shouldExposeKafkaMetrics()`
   - `testMicrometerIntegration_shouldRegisterBeans()`

2. **Then implement:**
   - Micrometer metrics for throughput, lag, processing time
   - Prometheus endpoint integration
   - Custom `KafkaConsumerMetrics` class

3. **Verify:** Metrics visible at `/actuator/prometheus`

### Step 5: Performance Baseline Verification
**验证所有性能目标：**

1. **Write test class KafkaPerformanceTest** covering:
   - `testThroughput_10kMessagesPerSecond_shouldAchieve()` [核心目标]
   - `testLatency_p99_shouldBeLessThan100ms()`
   - `testLatency_p999_shouldBeLessThan200ms()`
   - `testConsumerLag_shouldBeLessThan1000Messages()`
   - `testDLQRate_shouldBeLessThan0_1Percent()`

2. Run all test classes:
   - KafkaAuditEventConsumerTest (10 tests)
   - VirtualThreadKafkaConfigTest (8 tests)
   - BackpressureHandlingTest (6 tests)
   - ErrorHandlingTest (6 tests)
   - KafkaMetricsTest (7 tests)
   - KafkaPerformanceTest (5 tests)
   - Integration tests (3 tests)

3. **Final verification:** `mvn clean test -pl sql-audit-service-consumer` - all 45+ tests pass

## Expected Output
- **Deliverables:**
  - `KafkaAuditEventConsumer.java` with @KafkaListener
  - `VirtualThreadKafkaConfig.java` for Virtual Thread integration
  - `BackpressureHandler.java` for dynamic concurrency
  - `AuditEventErrorHandler.java` with retry + DLQ
  - `KafkaConsumerMetrics.java` for Micrometer metrics
  - `application-kafka.yml` for Kafka configuration

- **Success criteria:**
  - 45+ tests passing (10+8+6+6+7+5+3)
  - `mvn clean test` succeeds
  - 10,000 msg/s throughput achieved
  - p99 latency < 100ms
  - Consumer lag < 1000 messages

- **File locations:**
  - `sql-audit-service-consumer/src/main/java/com/footstone/audit/service/consumer/`
    - `KafkaAuditEventConsumer.java`
    - `VirtualThreadKafkaConfig.java`
    - `BackpressureHandler.java`
    - `AuditEventErrorHandler.java`
    - `KafkaConsumerMetrics.java`
  - `sql-audit-service-consumer/src/main/resources/application-kafka.yml`
  - `sql-audit-service-consumer/src/test/java/...` (all test classes)

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_10_Audit_Service/Task_10_2_Kafka_Consumer_Virtual_Threads.md`
Follow .apm/guides/Memory_Log_Guide.md instructions.

## Technical Notes
1. **Spring Kafka 3.1+ 特性:**
   - 使用 `VirtualThreadTaskExecutor` 替代传统线程池
   - Manual acknowledgment mode 确保消息处理可靠性
   - `@KafkaListener` 支持 Virtual Thread 执行

2. **AuditEvent 反序列化:**
   - 使用 Jackson `ObjectMapper` 配置
   - AuditEvent 已有 `@JsonCreator` 注解支持
   - 处理反序列化异常发送到 DLQ

3. **性能目标:**
   | 指标 | 目标值 |
   |------|--------|
   | 吞吐量 | 10,000 msg/s |
   | p99 延迟 | < 100ms |
   | p99.9 延迟 | < 200ms |
   | 消费者滞后 | < 1000 messages |
   | DLQ 率 | < 0.1% |

4. **依赖版本:**
   ```xml
   <spring-kafka.version>3.1.0</spring-kafka.version>
   <micrometer.version>1.12.0</micrometer.version>
   ```

---

**Assignment Created:** 2025-12-18
**Manager Agent:** Manager_Agent_3
**Status:** Ready for Parallel Assignment with Task 10.3
**Prerequisite:** Task 10.1 Completed
**Parallel With:** Task 10.3
