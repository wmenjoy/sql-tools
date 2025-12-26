package com.footstone.audit.service.consumer;

import com.footstone.audit.service.consumer.config.KafkaConsumerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.List;

/**
 * Kafka消息消费错误处理器.
 *
 * <h2>功能特性</h2>
 * <ul>
 *   <li>支持指数退避重试策略</li>
 *   <li>重试失败后自动发送到死信队列(DLQ)</li>
 *   <li>处理反序列化错误</li>
 * </ul>
 *
 * <h2>配置说明</h2>
 * <p>所有参数可通过 audit.kafka.consumer.error-handler 配置节点调整</p>
 *
 * @see KafkaConsumerProperties.ErrorHandler
 * @since 2.0.0
 */
@Component
@Slf4j
public class AuditEventErrorHandler extends DefaultErrorHandler {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String dlqTopic;

    public AuditEventErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaConsumerProperties properties) {
        super(createExponentialBackOff(properties));
        this.kafkaTemplate = kafkaTemplate;
        this.dlqTopic = properties.getDlqTopic();
        log.info("AuditEventErrorHandler initialized with DLQ topic: {}, retry config: initialInterval={}ms, multiplier={}",
                dlqTopic,
                properties.getErrorHandler().getRetryInitialInterval(),
                properties.getErrorHandler().getRetryMultiplier());
    }

    /**
     * 创建指数退避策略.
     */
    private static ExponentialBackOff createExponentialBackOff(KafkaConsumerProperties properties) {
        KafkaConsumerProperties.ErrorHandler config = properties.getErrorHandler();
        ExponentialBackOff backOff = new ExponentialBackOff(
                config.getRetryInitialInterval(),
                config.getRetryMultiplier()
        );
        // 根据最大重试次数计算最大时间间隔，避免无限增长
        // 例如: initialInterval=1000, multiplier=2.0, maxAttempts=3
        // 重试间隔为: 1s, 2s, 4s，最大间隔设为 1000 * 2^3 = 8000ms
        long maxInterval = (long) (config.getRetryInitialInterval() * Math.pow(config.getRetryMultiplier(), config.getMaxAttempts()));
        backOff.setMaxInterval(maxInterval);
        return backOff;
    }

    @Override
    public void handleRemaining(Exception thrownException, List<ConsumerRecord<?, ?>> records, org.apache.kafka.clients.consumer.Consumer<?, ?> consumer, org.springframework.kafka.listener.MessageListenerContainer container) {
        log.error("Handling error for records: {}", records.size(), thrownException);
        // Send to DLQ
        for (ConsumerRecord<?, ?> record : records) {
            sendToDlq(record, thrownException);
        }
    }

    private void sendToDlq(ConsumerRecord<?, ?> record, Exception exception) {
        try {
            if (kafkaTemplate != null) {
                Object key = record.key();
                Object value = record.value();
                kafkaTemplate.send(dlqTopic, 
                    key instanceof String ? (String) key : String.valueOf(key), 
                    value instanceof String ? (String) value : String.valueOf(value));
            }
        } catch (Exception e) {
            log.error("Failed to send to DLQ", e);
        }
    }

    public void handleDeserializationError(String payload, Exception exception) {
        log.error("Deserialization error for payload: {}", payload, exception);
        // Manually send to DLQ if needed, or just log
        if (kafkaTemplate != null) {
            try {
                kafkaTemplate.send(dlqTopic, "DESERIALIZATION_ERROR", payload);
            } catch (Exception e) {
                log.error("Failed to send poison pill to DLQ", e);
            }
        }
    }
}
