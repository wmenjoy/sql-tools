package com.footstone.sqlguard.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for AuditEvent JSON serialization and deserialization.
 *
 * <p>Tests Jackson configuration, ISO-8601 date formatting, and JSON schema compliance.</p>
 */
class AuditEventSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    @Test
    void testJsonSerialization_shouldProduceExpectedFormat() throws JsonProcessingException {
        // Given: A complete audit event
        Map<String, Object> params = new HashMap<>();
        params.put("id", 123);
        params.put("name", "John");

        ValidationResult violations = ValidationResult.pass();
        violations.addViolation(RiskLevel.MEDIUM, "Missing index", "Add index on user_id");

        Instant timestamp = Instant.parse("2024-01-15T10:30:45.123Z");

        AuditEvent event = AuditEvent.builder()
                .sql("SELECT * FROM users WHERE id = ?")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("com.example.UserMapper.selectById")
                .datasource("primary")
                .params(params)
                .executionTimeMs(150L)
                .rowsAffected(1)
                .errorMessage(null)
                .timestamp(timestamp)
                .violations(violations)
                .build();

        // When: Serializing to JSON
        String json = objectMapper.writeValueAsString(event);

        // Then: Should contain all fields in expected format
        assertNotNull(json);
        assertTrue(json.contains("\"sqlId\""));
        assertTrue(json.contains("\"sql\":\"SELECT * FROM users WHERE id = ?\""));
        assertTrue(json.contains("\"sqlType\":\"SELECT\""));
        assertTrue(json.contains("\"mapperId\":\"com.example.UserMapper.selectById\""));
        assertTrue(json.contains("\"datasource\":\"primary\""));
        assertTrue(json.contains("\"params\""));
        assertTrue(json.contains("\"id\":123"));
        assertTrue(json.contains("\"name\":\"John\""));
        assertTrue(json.contains("\"executionTimeMs\":150"));
        assertTrue(json.contains("\"rowsAffected\":1"));
        assertTrue(json.contains("\"errorMessage\":null"));
        assertTrue(json.contains("\"timestamp\":\"2024-01-15T10:30:45.123Z\""));
        assertTrue(json.contains("\"violations\""));
    }

    @Test
    void testJsonDeserialization_shouldRecreateEvent() throws JsonProcessingException {
        // Given: A JSON string representing an audit event
        String json = "{"
                + "\"sqlId\":\"abc123\","
                + "\"sql\":\"SELECT * FROM users WHERE id = ?\","
                + "\"sqlType\":\"SELECT\","
                + "\"mapperId\":\"UserMapper.selectById\","
                + "\"datasource\":\"primary\","
                + "\"params\":{\"id\":123},"
                + "\"executionTimeMs\":150,"
                + "\"rowsAffected\":1,"
                + "\"errorMessage\":null,"
                + "\"timestamp\":\"2024-01-15T10:30:45.123Z\","
                + "\"violations\":null"
                + "}";

        // When: Deserializing from JSON
        AuditEvent event = objectMapper.readValue(json, AuditEvent.class);

        // Then: Should recreate the event correctly
        assertNotNull(event);
        assertEquals("SELECT * FROM users WHERE id = ?", event.getSql());
        assertEquals(SqlCommandType.SELECT, event.getSqlType());
        assertEquals("UserMapper.selectById", event.getMapperId());
        assertEquals("primary", event.getDatasource());
        assertNotNull(event.getParams());
        assertEquals(123, event.getParams().get("id"));
        assertEquals(150L, event.getExecutionTimeMs());
        assertEquals(1, event.getRowsAffected());
        assertNull(event.getErrorMessage());
        assertEquals(Instant.parse("2024-01-15T10:30:45.123Z"), event.getTimestamp());
        assertNull(event.getViolations());
    }

    @Test
    void testDateTimeSerialization_shouldUseIso8601() throws JsonProcessingException {
        // Given: An event with a specific timestamp
        Instant timestamp = Instant.parse("2024-01-15T10:30:45.123Z");
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT 1")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("TestMapper.test")
                .timestamp(timestamp)
                .build();

        // When: Serializing to JSON
        String json = objectMapper.writeValueAsString(event);

        // Then: Timestamp should be in ISO-8601 format, not numeric
        assertTrue(json.contains("\"timestamp\":\"2024-01-15T10:30:45.123Z\""));
        assertFalse(json.matches(".*\"timestamp\":\\d+.*")); // Should not be a number
    }

    @Test
    void testNullFields_shouldSerializeAsNull() throws JsonProcessingException {
        // Given: An event with null optional fields
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT * FROM users")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("UserMapper.selectAll")
                .timestamp(Instant.now())
                .datasource(null)
                .params(null)
                .errorMessage(null)
                .violations(null)
                .build();

        // When: Serializing to JSON
        String json = objectMapper.writeValueAsString(event);

        // Then: Null fields should be explicitly serialized as null
        assertTrue(json.contains("\"datasource\":null"));
        assertTrue(json.contains("\"params\":null"));
        assertTrue(json.contains("\"errorMessage\":null"));
        assertTrue(json.contains("\"violations\":null"));
    }

    @Test
    void testRoundTripSerialization_shouldPreserveData() throws JsonProcessingException {
        // Given: An event with all fields populated
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 456);
        params.put("status", "active");

        ValidationResult violations = ValidationResult.pass();
        violations.addViolation(RiskLevel.HIGH, "No WHERE clause", "Add WHERE condition");

        Instant timestamp = Instant.now();

        AuditEvent original = AuditEvent.builder()
                .sql("UPDATE users SET status = ? WHERE id = ?")
                .sqlType(SqlCommandType.UPDATE)
                .mapperId("UserMapper.updateStatus")
                .datasource("secondary")
                .params(params)
                .executionTimeMs(250L)
                .rowsAffected(1)
                .errorMessage(null)
                .timestamp(timestamp)
                .violations(violations)
                .build();

        // When: Serializing and deserializing
        String json = objectMapper.writeValueAsString(original);
        AuditEvent deserialized = objectMapper.readValue(json, AuditEvent.class);

        // Then: Should preserve all data (except sqlId which is regenerated)
        assertEquals(original.getSql(), deserialized.getSql());
        assertEquals(original.getSqlType(), deserialized.getSqlType());
        assertEquals(original.getMapperId(), deserialized.getMapperId());
        assertEquals(original.getDatasource(), deserialized.getDatasource());
        assertEquals(original.getParams(), deserialized.getParams());
        assertEquals(original.getExecutionTimeMs(), deserialized.getExecutionTimeMs());
        assertEquals(original.getRowsAffected(), deserialized.getRowsAffected());
        assertEquals(original.getErrorMessage(), deserialized.getErrorMessage());
        assertEquals(original.getTimestamp(), deserialized.getTimestamp());
        
        // sqlId should be the same since SQL is the same
        assertEquals(original.getSqlId(), deserialized.getSqlId());
    }

    @Test
    void testSerializationWithError_shouldIncludeErrorMessage() throws JsonProcessingException {
        // Given: An event with an error
        AuditEvent event = AuditEvent.builder()
                .sql("DELETE FROM users WHERE id = ?")
                .sqlType(SqlCommandType.DELETE)
                .mapperId("UserMapper.deleteById")
                .timestamp(Instant.now())
                .executionTimeMs(50L)
                .rowsAffected(-1)
                .errorMessage("Foreign key constraint violation")
                .build();

        // When: Serializing to JSON
        String json = objectMapper.writeValueAsString(event);

        // Then: Should include error message
        assertTrue(json.contains("\"errorMessage\":\"Foreign key constraint violation\""));
        assertTrue(json.contains("\"rowsAffected\":-1"));
    }

    @Test
    void testObjectMapperConfiguration_shouldBeReusable() throws JsonProcessingException {
        // Given: Multiple events serialized with the same ObjectMapper
        AuditEvent event1 = AuditEvent.builder()
                .sql("SELECT * FROM users")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("UserMapper.selectAll")
                .timestamp(Instant.now())
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .sql("SELECT * FROM orders")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("OrderMapper.selectAll")
                .timestamp(Instant.now())
                .build();

        // When: Serializing multiple events
        String json1 = objectMapper.writeValueAsString(event1);
        String json2 = objectMapper.writeValueAsString(event2);

        // Then: Both should serialize correctly
        assertNotNull(json1);
        assertNotNull(json2);
        assertTrue(json1.contains("users"));
        assertTrue(json2.contains("orders"));
    }
}
