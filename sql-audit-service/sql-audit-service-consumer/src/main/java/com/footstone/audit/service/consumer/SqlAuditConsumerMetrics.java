package com.footstone.audit.service.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka消费指标收集器 - Micrometer指标集成组件
 * Kafka Consumer Metrics Collector - Micrometer metrics integration component
 *
 * <p>职责 (Responsibilities):</p>
 * <ul>
 *   <li>收集Kafka消费吞吐量指标 (Collects Kafka consumption throughput metrics)</li>
 *   <li>记录消息处理时延 (Records message processing latency)</li>
 *   <li>统计错误率和DLQ发送次数 (Counts error rate and DLQ sends)</li>
 *   <li>监控消费延迟(Lag) (Monitors consumer lag)</li>
 *   <li>集成Prometheus/Grafana监控体系 (Integrates with Prometheus/Grafana monitoring)</li>
 * </ul>
 *
 * <p>指标类型 (Metric Types):</p>
 * <ul>
 *   <li><b>Counter计数器</b>: 吞吐量、错误数、DLQ数 - 单调递增,用于计算速率
 *       (<b>Counter</b>: Throughput, errors, DLQ - Monotonically increasing, used for rate calculation)</li>
 *   <li><b>Timer定时器</b>: 处理时延 - 记录时间分布,自动计算P50/P95/P99
 *       (<b>Timer</b>: Processing latency - Records time distribution, auto-calculates P50/P95/P99)</li>
 *   <li><b>Gauge测量值</b>: 消费延迟(Lag) - 实时快照值,非累积
 *       (<b>Gauge</b>: Consumer lag - Real-time snapshot value, non-cumulative)</li>
 * </ul>
 *
 * <p>Prometheus查询示例 (Prometheus Query Examples):</p>
 * <pre>
 * // 消费速率 (QPS) - Consumption rate (QPS)
 * rate(audit_kafka_throughput_total[1m])
 *
 * // 错误率 - Error rate
 * rate(audit_kafka_errors_total[1m]) / rate(audit_kafka_throughput_total[1m])
 *
 * // P99处理时延 - P99 processing latency
 * histogram_quantile(0.99, rate(audit_kafka_processing_time_seconds_bucket[5m]))
 *
 * // 消费延迟 - Consumer lag
 * audit_kafka_lag
 * </pre>
 *
 * <p>告警规则示例 (Alerting Rule Examples):</p>
 * <pre>
 * // 消费延迟过高 - High consumer lag
 * audit_kafka_lag > 10000
 *
 * // 错误率过高 - High error rate
 * rate(audit_kafka_errors_total[5m]) / rate(audit_kafka_throughput_total[5m]) > 0.05
 *
 * // P99时延过高 - High P99 latency
 * histogram_quantile(0.99, rate(audit_kafka_processing_time_seconds_bucket[5m])) > 0.5
 * </pre>
 *
 * @see MeterRegistry Micrometer核心注册器 (Micrometer core registry)
 * @see KafkaAuditEventConsumer 调用本组件记录指标 (Invokes this component to record metrics)
 * @since 1.0.0
 */
@Component
public class SqlAuditConsumerMetrics {

    /**
     * Micrometer指标注册器 - 由Spring Boot自动装配
     * Micrometer meter registry - Auto-configured by Spring Boot
     *
     * 默认绑定Prometheus/JMX等监控系统,指标通过/actuator/prometheus端点暴露。
     * Defaults to Prometheus/JMX monitoring systems, metrics exposed via /actuator/prometheus endpoint.
     */
    private final MeterRegistry registry;

    /**
     * 当前消费延迟(Lag) - 原子Long保证线程安全
     * Current consumer lag - Atomic Long ensures thread safety
     *
     * 消费延迟 = 最新偏移量(Log End Offset) - 当前消费偏移量(Current Offset)
     * Consumer lag = Latest offset (Log End Offset) - Current consumed offset (Current Offset)
     *
     * 高延迟表示消费速度跟不上生产速度,需要增加消费者实例或提升处理性能。
     * High lag indicates consumption speed cannot keep up with production speed, need to add
     * consumer instances or improve processing performance.
     */
    private final AtomicLong currentLag = new AtomicLong(0);

    /**
     * 构造函数 - 注册Gauge监控当前Lag
     * Constructor - Registers Gauge to monitor current lag
     *
     * Gauge是实时快照值,每次Prometheus抓取时读取currentLag.get()的当前值。
     * Gauge is a real-time snapshot value, reads currentLag.get() on each Prometheus scrape.
     *
     * @param registry Micrometer指标注册器 (Micrometer meter registry)
     */
    public SqlAuditConsumerMetrics(MeterRegistry registry) {
        this.registry = registry;

        // 注册Gauge监控Lag - 实时值,非累积
        // Register Gauge to monitor Lag - Real-time value, non-cumulative
        registry.gauge("audit.kafka.lag", currentLag);
    }

    /**
     * 递增吞吐量计数器 - 每成功处理一条消息调用一次
     * Increment throughput counter - Called once per successfully processed message
     *
     * Counter单调递增,Prometheus通过rate()函数计算QPS:
     * Counter monotonically increases, Prometheus calculates QPS via rate():
     * <pre>rate(audit_kafka_throughput_total[1m])</pre>
     *
     * 用途: 监控消费速率,容量规划,性能基线
     * Usage: Monitor consumption rate, capacity planning, performance baseline
     */
    public void incrementThroughput() {
        registry.counter("audit.kafka.throughput").increment();
    }

    /**
     * 递增错误计数器 - 反序列化失败或处理异常时调用
     * Increment error counter - Called on deserialization failure or processing exception
     *
     * 错误率计算 (Error rate calculation):
     * <pre>
     * rate(audit_kafka_errors_total[5m]) / rate(audit_kafka_throughput_total[5m])
     * </pre>
     *
     * 告警阈值: >5%表示系统异常,需要排查日志和DLQ消息
     * Alert threshold: >5% indicates system anomaly, need to investigate logs and DLQ messages
     */
    public void incrementErrors() {
        registry.counter("audit.kafka.errors").increment();
    }

    /**
     * 递增DLQ发送计数器 - 发送消息到Dead Letter Queue时调用
     * Increment DLQ counter - Called when sending message to Dead Letter Queue
     *
     * DLQ消息是无法成功处理的消息(反序列化失败、重试耗尽等),需要人工介入排查。
     * DLQ messages are those that cannot be successfully processed (deserialization failure,
     * retry exhausted, etc.), requiring manual intervention.
     *
     * 监控指标: 持续增长的DLQ表示数据质量问题或配置错误
     * Monitoring: Continuously increasing DLQ indicates data quality issues or configuration errors
     */
    public void incrementDlq() {
        registry.counter("audit.kafka.dlq").increment();
    }

    /**
     * 记录处理时延 - 每次消息处理完成时调用
     * Record processing latency - Called upon each message processing completion
     *
     * Timer自动记录时间分布,生成histogram_bucket供Prometheus聚合:
     * Timer automatically records time distribution, generates histogram_bucket for Prometheus aggregation:
     * <pre>
     * P50: histogram_quantile(0.50, rate(audit_kafka_processing_time_seconds_bucket[5m]))
     * P95: histogram_quantile(0.95, rate(audit_kafka_processing_time_seconds_bucket[5m]))
     * P99: histogram_quantile(0.99, rate(audit_kafka_processing_time_seconds_bucket[5m]))
     * </pre>
     *
     * 性能基线 (Performance baseline):
     * - P50 < 50ms: 良好 (Good)
     * - P95 < 200ms: 可接受 (Acceptable)
     * - P99 < 500ms: 需优化 (Needs optimization)
     * - P99 > 1000ms: 严重问题 (Severe issue)
     *
     * @param timeMs 处理时间(毫秒) (Processing time in milliseconds)
     */
    public void recordProcessingTime(long timeMs) {
        Timer.builder("audit.kafka.processing.time")
             .description("Time taken to process audit event")
             .register(registry)
             .record(timeMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录当前消费延迟(Lag) - 定期由外部监控任务更新
     * Record current consumer lag - Periodically updated by external monitoring task
     *
     * 消费延迟定义 (Consumer lag definition):
     * <pre>
     * Lag = Log End Offset - Current Offset
     * </pre>
     *
     * Lag监控阈值 (Lag monitoring thresholds):
     * - Lag < 1000: 健康 (Healthy)
     * - Lag 1000-10000: 警告,需关注 (Warning, needs attention)
     * - Lag > 10000: 严重,消费速度严重滞后 (Critical, consumption severely lagging)
     *
     * 高Lag解决方案 (High lag solutions):
     * 1. 增加消费者实例数(增加并发) (Increase consumer instances for concurrency)
     * 2. 优化处理逻辑降低时延 (Optimize processing logic to reduce latency)
     * 3. 启用背压控制防止崩溃 (Enable backpressure control to prevent crashes)
     * 4. 扩容数据库/ClickHouse提升写入性能 (Scale database/ClickHouse for better write performance)
     *
     * @param lag 当前延迟消息数 (Current lag message count)
     */
    public void recordLag(long lag) {
        currentLag.set(lag);
    }

    /**
     * 获取Meter注册器 - 供测试或高级用例使用
     * Get meter registry - For testing or advanced use cases
     *
     * @return Micrometer注册器 (Micrometer registry)
     */
    public MeterRegistry getRegistry() {
        return registry;
    }
}
