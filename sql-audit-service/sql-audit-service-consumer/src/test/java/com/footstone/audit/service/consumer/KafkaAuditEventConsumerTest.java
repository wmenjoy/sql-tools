package com.footstone.audit.service.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footstone.audit.service.core.processor.AuditEventProcessor;
import com.footstone.sqlguard.audit.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaAuditEventConsumerTest {

    @Mock
    private Acknowledgment acknowledgment;

    // We will mock the processor/service that handles the event
    // For now, assuming there might be an AuditEventProcessor or similar, 
    // but the task just says "route to audit engine". 
    // I'll mock a simple processor interface or just verify logic within consumer if it calls a service.
    // The instructions say "route to audit engine for checker execution".
    // I'll assume an AuditEventProcessor dependency.
    @Mock
    private AuditEventProcessor auditEventProcessor;

    @Mock
    private AuditEventErrorHandler errorHandler;

    @Mock
    private BackpressureHandler backpressureHandler;

    @Mock
    private SqlAuditConsumerMetrics metrics;

    private KafkaAuditEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // We'll inject dependencies via constructor or setters
        consumer = new KafkaAuditEventConsumer(objectMapper, auditEventProcessor, errorHandler, backpressureHandler, metrics);
    }

    @Test
    void testConsumeMessage_validAuditEvent_shouldDeserialize() throws JsonProcessingException {
        // Arrange
        String json = "{\"sql\":\"SELECT 1\",\"sqlType\":\"SELECT\",\"mapperId\":\"TestMapper.select\",\"timestamp\":\"2023-10-01T10:00:00Z\"}";
        int partition = 0;
        long offset = 1L;

        // Act
        consumer.consume(json, partition, offset, acknowledgment);

        // Assert
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventProcessor).process(captor.capture());
        assertEquals("SELECT 1", captor.getValue().getSql());
    }

    @Test
    void testConsumeMessage_validAuditEvent_shouldForwardToProcessor() throws JsonProcessingException {
        // Arrange
        String json = "{\"sql\":\"UPDATE t SET v=1\",\"sqlType\":\"UPDATE\",\"mapperId\":\"TestMapper.update\",\"timestamp\":\"2023-10-01T10:00:00Z\"}";
        
        // Act
        consumer.consume(json, 0, 1L, acknowledgment);

        // Assert
        verify(auditEventProcessor, times(1)).process(any(AuditEvent.class));
    }

    @Test
    void testConsumeMessage_invalidJson_shouldRejectToErrorHandler() {
        // Arrange
        String invalidJson = "{invalid-json}";

        // Act
        consumer.consume(invalidJson, 0, 1L, acknowledgment);

        // Assert
        verify(auditEventProcessor, never()).process(any());
        verify(errorHandler).handleDeserializationError(eq(invalidJson), any(Exception.class));
    }

    @Test
    void testConsumeMessage_nullMessage_shouldIgnore() {
        // Act
        consumer.consume(null, 0, 1L, acknowledgment);

        // Assert
        verify(auditEventProcessor, never()).process(any());
        verify(acknowledgment).acknowledge(); // Should ack to move past null
    }

    @Test
    void testConsumeMessage_emptyMessage_shouldIgnore() {
        // Act
        consumer.consume("", 0, 1L, acknowledgment);

        // Assert
        verify(auditEventProcessor, never()).process(any());
        verify(acknowledgment).acknowledge(); // Should ack to move past empty
    }

    @Test
    void testConsumerGroup_shouldUseConfiguredGroupId() {
        // This is a configuration test, usually done with @SpringBootTest or checking annotations
        // Here we can check if the class has the annotation (reflection) or trust the integration test.
        // For unit test, we might skip this or use reflection.
        // The instruction asks to cover it.
        try {
            var method = KafkaAuditEventConsumer.class.getMethod("consume", String.class, int.class, long.class, Acknowledgment.class);
            var listener = method.getAnnotation(org.springframework.kafka.annotation.KafkaListener.class);
            assertNotNull(listener);
            assertEquals("${audit.kafka.consumer.group-id:audit-service}", listener.groupId());
        } catch (NoSuchMethodException e) {
            fail("consume method not found");
        }
    }

    @Test
    void testTopicSubscription_shouldListenToCorrectTopic() {
        try {
            var method = KafkaAuditEventConsumer.class.getMethod("consume", String.class, int.class, long.class, Acknowledgment.class);
            var listener = method.getAnnotation(org.springframework.kafka.annotation.KafkaListener.class);
            assertNotNull(listener);
            assertArrayEquals(new String[]{"${audit.kafka.topic:sql-audit-events}"}, listener.topics());
        } catch (NoSuchMethodException e) {
            fail("consume method not found");
        }
    }

    @Test
    void testAcknowledgment_manualMode_shouldCommitOnSuccess() throws JsonProcessingException {
        // Arrange
        String json = "{\"sql\":\"SELECT 1\",\"sqlType\":\"SELECT\",\"mapperId\":\"id\",\"timestamp\":\"2023-10-01T10:00:00Z\"}";

        // Act
        consumer.consume(json, 0, 1L, acknowledgment);

        // Assert
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testAcknowledgment_manualMode_shouldNotCommitOnFailure() throws JsonProcessingException {
        // Arrange
        String json = "{\"sql\":\"SELECT 1\",\"sqlType\":\"SELECT\",\"mapperId\":\"id\",\"timestamp\":\"2023-10-01T10:00:00Z\"}";
        doThrow(new RuntimeException("Processing failed")).when(auditEventProcessor).process(any());

        // Act
        try {
            consumer.consume(json, 0, 1L, acknowledgment);
        } catch (RuntimeException e) {
            // expected
        }

        // Assert
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void testConsumer_shouldUseVirtualThreadExecutor() {
        // Annotation check
        try {
            var method = KafkaAuditEventConsumer.class.getMethod("consume", String.class, int.class, long.class, Acknowledgment.class);
            var listener = method.getAnnotation(org.springframework.kafka.annotation.KafkaListener.class);
            assertNotNull(listener);
            assertEquals("virtualThreadKafkaListenerContainerFactory", listener.containerFactory());
        } catch (NoSuchMethodException e) {
            fail("consume method not found");
        }
    }
}
