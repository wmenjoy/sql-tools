package com.footstone.audit.service.consumer;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.List;

@Component
@Slf4j
public class AuditEventErrorHandler extends DefaultErrorHandler {

    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Setter
    private String dlqTopic = "sql-audit-events-dlq";

    public AuditEventErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        super(new ExponentialBackOff(1000L, 2.0)); // Retry with backoff
        this.kafkaTemplate = kafkaTemplate;
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
