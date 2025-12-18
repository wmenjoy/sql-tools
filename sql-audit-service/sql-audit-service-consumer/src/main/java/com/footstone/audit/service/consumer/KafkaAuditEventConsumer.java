package com.footstone.audit.service.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footstone.sqlguard.audit.AuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KafkaAuditEventConsumer {

    private final ObjectMapper objectMapper;
    private final AuditEventProcessor auditEventProcessor;
    private final AuditEventErrorHandler errorHandler;
    private final BackpressureHandler backpressureHandler;
    private final KafkaConsumerMetrics metrics;

    public KafkaAuditEventConsumer(ObjectMapper objectMapper, 
                                   AuditEventProcessor auditEventProcessor,
                                   AuditEventErrorHandler errorHandler,
                                   BackpressureHandler backpressureHandler,
                                   KafkaConsumerMetrics metrics) {
        this.objectMapper = objectMapper;
        this.auditEventProcessor = auditEventProcessor;
        this.errorHandler = errorHandler;
        this.backpressureHandler = backpressureHandler;
        this.metrics = metrics;
    }

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
        
        long startTime = System.currentTimeMillis();
        
        if (message == null || message.trim().isEmpty()) {
            log.warn("Received empty or null message from partition {} offset {}", partition, offset);
            acknowledgment.acknowledge();
            return;
        }

        try {
            // Check backpressure status periodically or before processing
            // Ideally this is done by the container management, but we can trigger check here
            // backpressureHandler.checkStatus(); // This might be too frequent per message
            
            AuditEvent event = objectMapper.readValue(message, AuditEvent.class);
            auditEventProcessor.process(event);
            acknowledgment.acknowledge();
            
            long duration = System.currentTimeMillis() - startTime;
            metrics.incrementThroughput();
            metrics.recordProcessingTime(duration);
            backpressureHandler.recordLatency("audit-service", duration);
            backpressureHandler.recordSuccess("audit-service");
            
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize message: {}", message, e);
            metrics.incrementErrors();
            errorHandler.handleDeserializationError(message, e);
            acknowledgment.acknowledge(); 
        } catch (Exception e) {
            log.error("Error processing message from partition {} offset {}", partition, offset, e);
            metrics.incrementErrors();
            backpressureHandler.recordFailure("audit-service");
            throw e; 
        }
    }
}
