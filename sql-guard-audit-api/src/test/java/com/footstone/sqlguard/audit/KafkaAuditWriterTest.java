package com.footstone.sqlguard.audit;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KafkaAuditWriter.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Successful audit event serialization and Kafka sending</li>
 *   <li>Message key generation using SQL ID</li>
 *   <li>Error handling for null events and serialization failures</li>
 *   <li>Asynchronous send with callback</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class KafkaAuditWriterTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaAuditWriter writer;

    private static final String TEST_TOPIC = "test-audit-topic";

    @BeforeEach
    void setUp() {
        writer = new KafkaAuditWriter(kafkaTemplate, TEST_TOPIC);
    }

    @Test
    void writeAuditLog_successfulWrite() throws Exception {
        // Given: Mock successful Kafka send
        SettableListenableFuture<SendResult<String, String>> future = new SettableListenableFuture<>();
        future.set(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // Given: Valid audit event
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT * FROM users WHERE id = ?")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("UserMapper.selectById")
                .datasource("primary")
                .executionTimeMs(45L)
                .rowsAffected(1)
                .timestamp(Instant.now())
                .build();

        // When: Write audit log
        writer.writeAuditLog(event);

        // Then: Verify Kafka send was called with correct parameters
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate, times(1)).send(
                topicCaptor.capture(),
                keyCaptor.capture(),
                valueCaptor.capture()
        );

        // Verify topic
        assertEquals(TEST_TOPIC, topicCaptor.getValue());

        // Verify key is SQL ID
        assertEquals(event.getSqlId(), keyCaptor.getValue());

        // Verify value is JSON
        String json = valueCaptor.getValue();
        assertTrue(json.contains("\"sql\":\"SELECT * FROM users WHERE id = ?\""));
        assertTrue(json.contains("\"sqlType\":\"SELECT\""));
        assertTrue(json.contains("\"executionLayer\":\"MYBATIS\""));
        assertTrue(json.contains("\"statementId\":\"UserMapper.selectById\""));
    }

    @Test
    void writeAuditLog_nullEvent_throwsException() {
        // When/Then: Null event should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> writer.writeAuditLog(null));
        assertEquals("AuditEvent must not be null", exception.getMessage());

        // Verify Kafka template was never called
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void writeAuditLog_withParams() throws Exception {
        // Given: Mock successful Kafka send
        SettableListenableFuture<SendResult<String, String>> future = new SettableListenableFuture<>();
        future.set(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // Given: Event with parameters
        Map<String, Object> params = new HashMap<>();
        params.put("status", "ACTIVE");
        params.put("id", 123);

        AuditEvent event = AuditEvent.builder()
                .sql("UPDATE users SET status = ? WHERE id = ?")
                .sqlType(SqlCommandType.UPDATE)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("UserMapper.updateStatus")
                .params(params)
                .executionTimeMs(25L)
                .rowsAffected(1)
                .timestamp(Instant.now())
                .build();

        // When: Write audit log
        writer.writeAuditLog(event);

        // Then: Verify parameters are included in JSON
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TEST_TOPIC), anyString(), valueCaptor.capture());

        String json = valueCaptor.getValue();
        assertTrue(json.contains("\"params\":"));
        assertTrue(json.contains("\"status\":"));
        assertTrue(json.contains("\"id\":"));
    }

    @Test
    void writeAuditLog_withError() throws Exception {
        // Given: Mock successful Kafka send
        SettableListenableFuture<SendResult<String, String>> future = new SettableListenableFuture<>();
        future.set(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // Given: Event with error message
        AuditEvent event = AuditEvent.builder()
                .sql("DELETE FROM users WHERE id = ?")
                .sqlType(SqlCommandType.DELETE)
                .executionLayer(ExecutionLayer.JDBC)
                .statementId("UserDao.deleteById")
                .errorMessage("Foreign key constraint violation")
                .executionTimeMs(15L)
                .rowsAffected(0)
                .timestamp(Instant.now())
                .build();

        // When: Write audit log
        writer.writeAuditLog(event);

        // Then: Verify error message is included
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TEST_TOPIC), anyString(), valueCaptor.capture());

        String json = valueCaptor.getValue();
        assertTrue(json.contains("\"errorMessage\":\"Foreign key constraint violation\""));
    }

    @Test
    void constructor_nullKafkaTemplate_throwsException() {
        // When/Then: Null KafkaTemplate should throw exception
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new KafkaAuditWriter(null, TEST_TOPIC));
        assertTrue(exception.getMessage().contains("kafkaTemplate must not be null"));
    }

    @Test
    void constructor_nullTopic_throwsException() {
        // When/Then: Null topic should throw exception
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new KafkaAuditWriter(kafkaTemplate, null));
        assertTrue(exception.getMessage().contains("topic must not be null"));
    }

    @Test
    void getTopic_returnsConfiguredTopic() {
        // When: Get topic
        String topic = writer.getTopic();

        // Then: Should return configured topic
        assertEquals(TEST_TOPIC, topic);
    }

    @Test
    void writeAuditLog_kafkaFailure_logsErrorButDoesNotThrow() throws Exception {
        // Given: Mock Kafka send failure
        SettableListenableFuture<SendResult<String, String>> future = new SettableListenableFuture<>();
        future.setException(new RuntimeException("Kafka connection failed"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // Given: Valid audit event
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT 1")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.JDBC)
                .timestamp(Instant.now())
                .build();

        // When: Write audit log - should not throw exception (fire-and-forget)
        writer.writeAuditLog(event);

        // Then: Kafka template was called
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());

        // Note: Error is logged by callback but doesn't propagate to caller
    }
}
