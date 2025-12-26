package com.footstone.sqlguard.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for AuditLogWriter with complete end-to-end scenarios.
 *
 * <p>Tests realistic audit logging scenarios including successful executions,
 * failures, and events with pre-execution violations. Verifies JSON round-trip
 * serialization for each scenario.</p>
 */
class AuditLogWriterIntegrationTest {

    private ObjectMapper objectMapper;
    private TestAuditLogWriter writer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
        writer = new TestAuditLogWriter();
    }

    @Test
    void testSuccessfulSelect_withExecutionTimeAndNoRowsAffected() throws AuditLogException, JsonProcessingException {
        // Given: A successful SELECT query
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 123);

        AuditEvent event = AuditEvent.builder()
                .sql("SELECT id, name, email FROM users WHERE id = ?")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("com.example.UserMapper.selectById")
                .datasource("primary")
                .params(params)
                .executionTimeMs(85L)
                .rowsAffected(0) // SELECT doesn't affect rows
                .timestamp(Instant.now())
                .build();

        // When: Writing audit log
        writer.writeAuditLog(event);

        // Then: Should be written successfully
        assertEquals(1, writer.getWrittenEvents().size());
        AuditEvent written = writer.getWrittenEvents().get(0);
        assertEquals(event.getSql(), written.getSql());
        assertEquals(SqlCommandType.SELECT, written.getSqlType());
        assertEquals(85L, written.getExecutionTimeMs());
        assertEquals(0, written.getRowsAffected());

        // Verify JSON round-trip
        String json = objectMapper.writeValueAsString(written);
        AuditEvent deserialized = objectMapper.readValue(json, AuditEvent.class);
        assertEquals(written.getSql(), deserialized.getSql());
        assertEquals(written.getSqlType(), deserialized.getSqlType());
        assertEquals(written.getExecutionTimeMs(), deserialized.getExecutionTimeMs());
    }

    @Test
    void testSuccessfulUpdate_withRowsAffected() throws AuditLogException, JsonProcessingException {
        // Given: A successful UPDATE statement
        Map<String, Object> params = new HashMap<>();
        params.put("status", "active");
        params.put("userId", 456);

        AuditEvent event = AuditEvent.builder()
                .sql("UPDATE users SET status = ? WHERE id = ?")
                .sqlType(SqlCommandType.UPDATE)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("com.example.UserMapper.updateStatus")
                .datasource("primary")
                .params(params)
                .executionTimeMs(120L)
                .rowsAffected(1)
                .timestamp(Instant.now())
                .build();

        // When: Writing audit log
        writer.writeAuditLog(event);

        // Then: Should capture rows affected
        assertEquals(1, writer.getWrittenEvents().size());
        AuditEvent written = writer.getWrittenEvents().get(0);
        assertEquals(SqlCommandType.UPDATE, written.getSqlType());
        assertEquals(1, written.getRowsAffected());
        assertEquals(120L, written.getExecutionTimeMs());

        // Verify JSON round-trip
        String json = objectMapper.writeValueAsString(written);
        AuditEvent deserialized = objectMapper.readValue(json, AuditEvent.class);
        assertEquals(1, deserialized.getRowsAffected());
        assertEquals(120L, deserialized.getExecutionTimeMs());
    }

    @Test
    void testFailedExecution_withErrorMessage() throws AuditLogException, JsonProcessingException {
        // Given: A failed DELETE operation
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", 789);

        AuditEvent event = AuditEvent.builder()
                .sql("DELETE FROM orders WHERE id = ?")
                .sqlType(SqlCommandType.DELETE)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("com.example.OrderMapper.deleteById")
                .datasource("primary")
                .params(params)
                .executionTimeMs(45L)
                .rowsAffected(-1) // Not applicable due to error
                .errorMessage("Foreign key constraint violation: order has related items")
                .timestamp(Instant.now())
                .build();

        // When: Writing audit log
        writer.writeAuditLog(event);

        // Then: Should capture error details
        assertEquals(1, writer.getWrittenEvents().size());
        AuditEvent written = writer.getWrittenEvents().get(0);
        assertEquals(SqlCommandType.DELETE, written.getSqlType());
        assertEquals(-1, written.getRowsAffected());
        assertNotNull(written.getErrorMessage());
        assertTrue(written.getErrorMessage().contains("Foreign key constraint"));

        // Verify JSON round-trip
        String json = objectMapper.writeValueAsString(written);
        AuditEvent deserialized = objectMapper.readValue(json, AuditEvent.class);
        assertEquals(-1, deserialized.getRowsAffected());
        assertEquals(written.getErrorMessage(), deserialized.getErrorMessage());
    }

    @Test
    void testEventWithPreExecutionViolations() throws AuditLogException, JsonProcessingException {
        // Given: An event with pre-execution validation violations
        ValidationResult violations = ValidationResult.pass();
        violations.addViolation(
                RiskLevel.HIGH,
                "Missing WHERE clause in UPDATE statement",
                "Add WHERE condition to limit affected rows"
        );
        violations.addViolation(
                RiskLevel.MEDIUM,
                "Missing index on user_id column",
                "Add index: CREATE INDEX idx_user_id ON users(user_id)"
        );

        AuditEvent event = AuditEvent.builder()
                .sql("UPDATE users SET last_login = NOW()")
                .sqlType(SqlCommandType.UPDATE)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("com.example.UserMapper.updateLastLogin")
                .datasource("primary")
                .executionTimeMs(350L)
                .rowsAffected(1500) // Updated many rows
                .timestamp(Instant.now())
                .violations(violations)
                .build();

        // When: Writing audit log
        writer.writeAuditLog(event);

        // Then: Should capture violations
        assertEquals(1, writer.getWrittenEvents().size());
        AuditEvent written = writer.getWrittenEvents().get(0);
        assertNotNull(written.getViolations());
        assertFalse(written.getViolations().isPassed());
        assertEquals(RiskLevel.HIGH, written.getViolations().getRiskLevel());
        assertEquals(2, written.getViolations().getViolations().size());

        // Verify JSON round-trip
        String json = objectMapper.writeValueAsString(written);
        AuditEvent deserialized = objectMapper.readValue(json, AuditEvent.class);
        assertNotNull(deserialized.getViolations());
        assertFalse(deserialized.getViolations().isPassed());
        assertEquals(2, deserialized.getViolations().getViolations().size());
    }

    @Test
    void testMultipleEvents_withDifferentScenarios() throws AuditLogException {
        // Given: Multiple audit events of different types
        AuditEvent selectEvent = AuditEvent.builder()
                .sql("SELECT * FROM products WHERE category = ?")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("ProductMapper.selectByCategory")
                .datasource("primary")
                .executionTimeMs(95L)
                .rowsAffected(0)
                .timestamp(Instant.now())
                .build();

        AuditEvent insertEvent = AuditEvent.builder()
                .sql("INSERT INTO orders (user_id, total) VALUES (?, ?)")
                .sqlType(SqlCommandType.INSERT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("OrderMapper.insert")
                .datasource("primary")
                .executionTimeMs(75L)
                .rowsAffected(1)
                .timestamp(Instant.now())
                .build();

        AuditEvent deleteEvent = AuditEvent.builder()
                .sql("DELETE FROM temp_data WHERE created_at < ?")
                .sqlType(SqlCommandType.DELETE)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("TempDataMapper.cleanup")
                .datasource("secondary")
                .executionTimeMs(200L)
                .rowsAffected(150)
                .timestamp(Instant.now())
                .build();

        // When: Writing multiple events
        writer.writeAuditLog(selectEvent);
        writer.writeAuditLog(insertEvent);
        writer.writeAuditLog(deleteEvent);

        // Then: All should be written successfully
        assertEquals(3, writer.getWrittenEvents().size());
        assertEquals(SqlCommandType.SELECT, writer.getWrittenEvents().get(0).getSqlType());
        assertEquals(SqlCommandType.INSERT, writer.getWrittenEvents().get(1).getSqlType());
        assertEquals(SqlCommandType.DELETE, writer.getWrittenEvents().get(2).getSqlType());
    }

    @Test
    void testSqlIdConsistency_acrossMultipleEvents() throws AuditLogException {
        // Given: Multiple events with the same SQL
        String sql = "SELECT * FROM users WHERE id = ?";

        AuditEvent event1 = AuditEvent.builder()
                .sql(sql)
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("UserMapper.selectById")
                .timestamp(Instant.now())
                .executionTimeMs(100L)
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .sql(sql)
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("UserMapper.selectById")
                .timestamp(Instant.now().plusSeconds(5))
                .executionTimeMs(150L)
                .build();

        // When: Writing both events
        writer.writeAuditLog(event1);
        writer.writeAuditLog(event2);

        // Then: sqlId should be consistent for deduplication
        assertEquals(2, writer.getWrittenEvents().size());
        assertEquals(
                writer.getWrittenEvents().get(0).getSqlId(),
                writer.getWrittenEvents().get(1).getSqlId()
        );
    }

    @Test
    void testCompleteJsonSchema_withAllFields() throws JsonProcessingException {
        // Given: An event with all possible fields populated
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 999);
        params.put("status", "premium");
        params.put("limit", 100);

        ValidationResult violations = ValidationResult.pass();
        violations.addViolation(
                RiskLevel.CRITICAL,
                "SQL injection risk detected",
                "Use parameterized queries"
        );

        AuditEvent event = AuditEvent.builder()
                .sql("SELECT * FROM users WHERE status = ? LIMIT ?")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("com.example.UserMapper.selectByStatus")
                .datasource("analytics")
                .params(params)
                .executionTimeMs(275L)
                .rowsAffected(0)
                .errorMessage(null)
                .timestamp(Instant.parse("2024-01-15T10:30:45.123Z"))
                .violations(violations)
                .build();

        // When: Serializing to JSON
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);

        // Then: Should produce valid JSON matching schema
        assertNotNull(json);
        assertTrue(json.contains("\"sqlId\""));
        assertTrue(json.contains("\"sql\""));
        assertTrue(json.contains("\"sqlType\""));
        assertTrue(json.contains("\"statementId\""));
        assertTrue(json.contains("\"datasource\""));
        assertTrue(json.contains("\"params\""));
        assertTrue(json.contains("\"executionTimeMs\""));
        assertTrue(json.contains("\"rowsAffected\""));
        assertTrue(json.contains("\"errorMessage\""));
        assertTrue(json.contains("\"timestamp\""));
        assertTrue(json.contains("\"violations\""));

        // Verify deserialization
        AuditEvent deserialized = objectMapper.readValue(json, AuditEvent.class);
        assertEquals(event.getSql(), deserialized.getSql());
        assertEquals(event.getSqlType(), deserialized.getSqlType());
        assertEquals(event.getStatementId(), deserialized.getStatementId());
        assertEquals(event.getDatasource(), deserialized.getDatasource());
        assertEquals(event.getExecutionTimeMs(), deserialized.getExecutionTimeMs());
        assertEquals(event.getRowsAffected(), deserialized.getRowsAffected());
    }

    /**
     * Test implementation of AuditLogWriter for integration testing.
     */
    private static class TestAuditLogWriter implements AuditLogWriter {
        private final java.util.List<AuditEvent> writtenEvents = new java.util.ArrayList<>();

        @Override
        public void writeAuditLog(AuditEvent event) throws AuditLogException {
            if (event == null) {
                throw new IllegalArgumentException("AuditEvent cannot be null");
            }
            if (event.getSql() == null) {
                throw new IllegalArgumentException("sql field is required");
            }
            if (event.getSqlType() == null) {
                throw new IllegalArgumentException("sqlType field is required");
            }
            if (event.getStatementId() == null) {
                throw new IllegalArgumentException("mapperId field is required");
            }
            if (event.getTimestamp() == null) {
                throw new IllegalArgumentException("timestamp field is required");
            }
            writtenEvents.add(event);
        }

        public java.util.List<AuditEvent> getWrittenEvents() {
            return writtenEvents;
        }
    }
}
