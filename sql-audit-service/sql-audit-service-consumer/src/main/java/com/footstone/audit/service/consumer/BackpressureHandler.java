package com.footstone.audit.service.consumer;

import com.footstone.audit.service.consumer.config.KafkaConsumerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka消费者背压控制器.
 *
 * <h2>功能说明</h2>
 * <p>监控消息处理延迟和失败率，当系统负载过高时自动暂停消费，负载恢复后自动恢复消费</p>
 *
 * <h2>工作机制</h2>
 * <ul>
 *   <li>定期检查处理延迟和失败次数</li>
 *   <li>延迟超过阈值或失败次数超过阈值时，暂停所有Kafka消费者</li>
 *   <li>系统恢复正常后，自动恢复消费</li>
 * </ul>
 *
 * <h2>配置说明</h2>
 * <p>所有参数可通过 audit.kafka.consumer.backpressure 配置节点调整</p>
 *
 * @see KafkaConsumerProperties.Backpressure
 * @since 2.0.0
 */
@Component
@ConditionalOnProperty(
    prefix = "audit.kafka.consumer.backpressure",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@Slf4j
public class BackpressureHandler {

    private final KafkaListenerEndpointRegistry registry;
    private final AtomicLong maxLatencyMs = new AtomicLong(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final long latencyThresholdMs;
    private final int failureThreshold;

    private boolean paused = false;

    public BackpressureHandler(
            KafkaListenerEndpointRegistry registry,
            KafkaConsumerProperties properties) {
        this.registry = registry;
        this.latencyThresholdMs = properties.getBackpressure().getLatencyThresholdMs();
        this.failureThreshold = properties.getBackpressure().getFailureThreshold();
        log.info("BackpressureHandler initialized with latencyThreshold={}ms, failureThreshold={}",
                latencyThresholdMs, failureThreshold);
    }

    /**
     * 记录消息处理延迟.
     *
     * @param serviceName 服务名称
     * @param latencyMs 延迟时间(毫秒)
     */
    public void recordLatency(String serviceName, long latencyMs) {
        maxLatencyMs.accumulateAndGet(latencyMs, Math::max);
    }

    /**
     * 记录处理成功.
     * <p>重置失败计数器和延迟统计</p>
     *
     * @param serviceName 服务名称
     */
    public void recordSuccess(String serviceName) {
        failureCount.set(0);
        // Reset latency slightly to allow recovery check
        // In real world, we'd use a sliding window or decay
        maxLatencyMs.set(0);
    }

    /**
     * 记录处理失败.
     * <p>增加失败计数器</p>
     *
     * @param serviceName 服务名称
     */
    public void recordFailure(String serviceName) {
        failureCount.incrementAndGet();
    }

    /**
     * 定期检查系统状态并执行背压控制.
     * <p>检查间隔通过 audit.kafka.consumer.backpressure.check-interval-ms 配置</p>
     */
    @Scheduled(fixedDelayString = "${audit.kafka.consumer.backpressure.check-interval-ms:5000}")
    public void checkStatus() {
        long currentLatency = maxLatencyMs.get();
        int failures = failureCount.get();

        if (!paused) {
            if (currentLatency > latencyThresholdMs || failures > failureThreshold) {
                log.warn("High load/failures detected (latency={}ms, failures={}). Pausing consumers.", currentLatency, failures);
                pauseConsumers();
                paused = true;
            }
        } else {
            if (currentLatency < latencyThresholdMs && failures == 0) {
                log.info("Load recovered. Resuming consumers.");
                resumeConsumers();
                paused = false;
            }
        }

        // Decay latency for next check if not updated
        maxLatencyMs.set(0);
    }

    /**
     * 暂停所有Kafka消费者.
     */
    private void pauseConsumers() {
        if (registry != null) {
            for (MessageListenerContainer container : registry.getAllListenerContainers()) {
                if (container.isRunning()) {
                    container.pause();
                }
            }
        }
    }

    /**
     * 恢复所有Kafka消费者.
     */
    private void resumeConsumers() {
        if (registry != null) {
            for (MessageListenerContainer container : registry.getAllListenerContainers()) {
                if (container.isContainerPaused()) {
                    container.resume();
                }
            }
        }
    }
}
