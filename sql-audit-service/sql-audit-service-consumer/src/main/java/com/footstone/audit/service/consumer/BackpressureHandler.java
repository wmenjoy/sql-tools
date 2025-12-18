package com.footstone.audit.service.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class BackpressureHandler {

    private final KafkaListenerEndpointRegistry registry;
    private final AtomicLong maxLatencyMs = new AtomicLong(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private static final long LATENCY_THRESHOLD_MS = 200;
    private static final int FAILURE_THRESHOLD = 5;

    private boolean paused = false;

    public BackpressureHandler(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    public void recordLatency(String serviceName, long latencyMs) {
        maxLatencyMs.accumulateAndGet(latencyMs, Math::max);
    }

    public void recordSuccess(String serviceName) {
        failureCount.set(0);
        // Reset latency slightly to allow recovery check
        // In real world, we'd use a sliding window or decay
        maxLatencyMs.set(0); 
    }

    public void recordFailure(String serviceName) {
        failureCount.incrementAndGet();
    }

    public void checkStatus() {
        long currentLatency = maxLatencyMs.get();
        int failures = failureCount.get();

        if (!paused) {
            if (currentLatency > LATENCY_THRESHOLD_MS || failures > FAILURE_THRESHOLD) {
                log.warn("High load/failures detected (latency={}ms, failures={}). Pausing consumers.", currentLatency, failures);
                pauseConsumers();
                paused = true;
            }
        } else {
            if (currentLatency < LATENCY_THRESHOLD_MS && failures == 0) {
                log.info("Load recovered. Resuming consumers.");
                resumeConsumers();
                paused = false;
            }
        }
        
        // Decay latency for next check if not updated
        maxLatencyMs.set(0); 
    }

    private void pauseConsumers() {
        if (registry != null) {
            for (MessageListenerContainer container : registry.getAllListenerContainers()) {
                if (container.isRunning()) {
                    container.pause();
                }
            }
        }
    }

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
