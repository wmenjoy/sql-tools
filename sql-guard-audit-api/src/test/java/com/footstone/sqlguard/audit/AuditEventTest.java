package com.footstone.sqlguard.audit;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ValidationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for AuditEvent data model.
 *
 * <p>Tests the builder pattern, immutability, validation, and equality semantics
 * of the AuditEvent class.</p>
 */
class AuditEventTest {

    @Test
    void testBuilder_withAllFields_shouldConstruct() {
        // Given: All fields including optional ones
        String sql = "SELECT * FROM users WHERE id = ?";
        SqlCommandType sqlType = SqlCommandType.SELECT;
        ExecutionLayer executionLayer = ExecutionLayer.MYBATIS;
        String statementId = "com.example.UserMapper.selectById";
        String datasource = "primary";
        Map<String, Object> params = new HashMap<>();
        params.put("id", 123);
        long executionTimeMs = 150L;
        int rowsAffected = 1;
        String errorMessage = null;
        Instant timestamp = Instant.now();
        ValidationResult violations = ValidationResult.pass();

        // When: Building the event
        AuditEvent event = AuditEvent.builder()
                .sql(sql)
                .sqlType(sqlType)
                .executionLayer(executionLayer)
                .statementId(statementId)
                .datasource(datasource)
                .params(params)
                .executionTimeMs(executionTimeMs)
                .rowsAffected(rowsAffected)
                .errorMessage(errorMessage)
                .timestamp(timestamp)
                .violations(violations)
                .build();

        // Then: All fields should be set correctly
        assertNotNull(event);
        assertNotNull(event.getSqlId()); // sqlId should be auto-generated
        assertEquals(sql, event.getSql());
        assertEquals(sqlType, event.getSqlType());
        assertEquals(executionLayer, event.getExecutionLayer());
        assertEquals(statementId, event.getStatementId());
        assertEquals(datasource, event.getDatasource());
        assertEquals(params, event.getParams());
        assertEquals(executionTimeMs, event.getExecutionTimeMs());
        assertEquals(rowsAffected, event.getRowsAffected());
        assertEquals(errorMessage, event.getErrorMessage());
        assertEquals(timestamp, event.getTimestamp());
        assertEquals(violations, event.getViolations());
    }

    @Test
    void testBuilder_withMinimalRequiredFields_shouldConstruct() {
        // Given: Only required fields (executionLayer is required, statementId is optional for JDBC)
        String sql = "SELECT * FROM users";
        SqlCommandType sqlType = SqlCommandType.SELECT;
        ExecutionLayer executionLayer = ExecutionLayer.MYBATIS;
        String statementId = "UserMapper.selectAll";
        Instant timestamp = Instant.now();

        // When: Building with minimal fields
        AuditEvent event = AuditEvent.builder()
                .sql(sql)
                .sqlType(sqlType)
                .executionLayer(executionLayer)
                .statementId(statementId)
                .timestamp(timestamp)
                .build();

        // Then: Required fields set, optional fields null/default
        assertNotNull(event);
        assertNotNull(event.getSqlId());
        assertEquals(sql, event.getSql());
        assertEquals(sqlType, event.getSqlType());
        assertEquals(executionLayer, event.getExecutionLayer());
        assertEquals(statementId, event.getStatementId());
        assertEquals(timestamp, event.getTimestamp());
        assertNull(event.getDatasource());
        assertNull(event.getParams());
        assertEquals(0L, event.getExecutionTimeMs()); // default value
        assertEquals(-1, event.getRowsAffected()); // default value
        assertNull(event.getErrorMessage());
        assertNull(event.getViolations());
    }

    @Test
    void testBuilder_withNullStatementId_JDBC_shouldConstruct() {
        // Given: JDBC layer with null statementId (common when stack trace is disabled)
        String sql = "SELECT COUNT(*) FROM orders";
        SqlCommandType sqlType = SqlCommandType.SELECT;
        ExecutionLayer executionLayer = ExecutionLayer.JDBC;
        String datasource = "slave-db";
        Instant timestamp = Instant.now();

        // When: Building with null statementId
        AuditEvent event = AuditEvent.builder()
                .sql(sql)
                .sqlType(sqlType)
                .executionLayer(executionLayer)
                .statementId(null)  // Null is allowed for JDBC
                .datasource(datasource)
                .timestamp(timestamp)
                .build();

        // Then: Should construct successfully
        assertNotNull(event);
        assertEquals(ExecutionLayer.JDBC, event.getExecutionLayer());
        assertNull(event.getStatementId());
        assertEquals(datasource, event.getDatasource());
    }

    @Test
    void testBuilder_withMissingRequired_shouldThrowException() {
        // When/Then: Missing 'sql' should throw
        assertThrows(IllegalArgumentException.class, () -> {
            AuditEvent.builder()
                    .sqlType(SqlCommandType.SELECT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("UserMapper.select")
                    .timestamp(Instant.now())
                    .build();
        });

        // When/Then: Missing 'sqlType' should throw
        assertThrows(IllegalArgumentException.class, () -> {
            AuditEvent.builder()
                    .sql("SELECT * FROM users")
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("UserMapper.select")
                    .timestamp(Instant.now())
                    .build();
        });

        // When/Then: Missing 'executionLayer' should throw
        assertThrows(IllegalArgumentException.class, () -> {
            AuditEvent.builder()
                    .sql("SELECT * FROM users")
                    .sqlType(SqlCommandType.SELECT)
                    .statementId("UserMapper.select")
                    .timestamp(Instant.now())
                    .build();
        });

        // When/Then: Missing 'timestamp' should throw
        assertThrows(IllegalArgumentException.class, () -> {
            AuditEvent.builder()
                    .sql("SELECT * FROM users")
                    .sqlType(SqlCommandType.SELECT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("UserMapper.select")
                    .build();
        });
    }

    @Test
    void testBuilder_withMissingStatementId_shouldAllowNull() {
        // When/Then: Missing statementId (null) should be allowed for JDBC
        assertDoesNotThrow(() -> {
            AuditEvent.builder()
                    .sql("SELECT * FROM users")
                    .sqlType(SqlCommandType.SELECT)
                    .executionLayer(ExecutionLayer.JDBC)
                    // statementId not set, defaults to null
                    .timestamp(Instant.now())
                    .build();
        });
    }

    @Test
    void testEquals_withSameContent_shouldBeEqual() {
        // Given: Two events with identical content
        Instant timestamp = Instant.now();
        AuditEvent event1 = AuditEvent.builder()
                .sql("SELECT * FROM users WHERE id = ?")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("UserMapper.selectById")
                .timestamp(timestamp)
                .executionTimeMs(100L)
                .rowsAffected(1)
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .sql("SELECT * FROM users WHERE id = ?")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("UserMapper.selectById")
                .timestamp(timestamp)
                .executionTimeMs(100L)
                .rowsAffected(1)
                .build();

        // When/Then: Should be equal and have same hashCode
        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    void testEquals_withDifferentContent_shouldNotBeEqual() {
        // Given: Two events with different SQL
        Instant timestamp = Instant.now();
        AuditEvent event1 = AuditEvent.builder()
                .sql("SELECT * FROM users")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("UserMapper.selectAll")
                .timestamp(timestamp)
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .sql("SELECT * FROM orders")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("OrderMapper.selectAll")
                .timestamp(timestamp)
                .build();

        // When/Then: Should not be equal
        assertNotEquals(event1, event2);
    }

    @Test
    void testToString_shouldContainAllFields() {
        // Given: An event with various fields
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT * FROM users WHERE id = ?")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("UserMapper.selectById")
                .datasource("primary")
                .timestamp(Instant.now())
                .executionTimeMs(150L)
                .rowsAffected(1)
                .build();

        // When: Converting to string
        String result = event.toString();

        // Then: Should contain key information
        assertNotNull(result);
        assertTrue(result.contains("sqlId"));
        assertTrue(result.contains("SELECT"));
        assertTrue(result.contains("UserMapper"));
        assertTrue(result.contains("primary"));
        assertTrue(result.contains("MYBATIS"));
    }

    @Test
    void testSqlId_shouldBeConsistentForSameSql() {
        // Given: Two events with identical SQL but different executionLayer and statementId
        String sql = "SELECT * FROM users WHERE id = ?";
        AuditEvent event1 = AuditEvent.builder()
                .sql(sql)
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("UserMapper.selectById")
                .timestamp(Instant.now())
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .sql(sql)
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.JDBC)
                .statementId(null)  // Different statementId
                .timestamp(Instant.now().plusSeconds(1))
                .build();

        // When/Then: sqlId should be the same (MD5 hash of SQL only)
        assertEquals(event1.getSqlId(), event2.getSqlId());
    }

    @Test
    void testSqlId_shouldBeDifferentForDifferentSql() {
        // Given: Two events with different SQL
        AuditEvent event1 = AuditEvent.builder()
                .sql("SELECT * FROM users")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("UserMapper.selectAll")
                .timestamp(Instant.now())
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .sql("SELECT * FROM orders")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("OrderMapper.selectAll")
                .timestamp(Instant.now())
                .build();

        // When/Then: sqlId should be different
        assertNotEquals(event1.getSqlId(), event2.getSqlId());
    }

    @Test
    void testValidation_executionTimeMs_shouldBeNonNegative() {
        // When/Then: Negative executionTimeMs should throw
        assertThrows(IllegalArgumentException.class, () -> {
            AuditEvent.builder()
                    .sql("SELECT * FROM users")
                    .sqlType(SqlCommandType.SELECT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("UserMapper.select")
                    .timestamp(Instant.now())
                    .executionTimeMs(-1L)
                    .build();
        });
    }

    @Test
    void testValidation_rowsAffected_shouldBeAtLeastMinusOne() {
        // When/Then: rowsAffected < -1 should throw
        assertThrows(IllegalArgumentException.class, () -> {
            AuditEvent.builder()
                    .sql("SELECT * FROM users")
                    .sqlType(SqlCommandType.SELECT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("UserMapper.select")
                    .timestamp(Instant.now())
                    .rowsAffected(-2)
                    .build();
        });

        // When/Then: rowsAffected = -1 should be allowed (not applicable)
        assertDoesNotThrow(() -> {
            AuditEvent.builder()
                    .sql("SELECT * FROM users")
                    .sqlType(SqlCommandType.SELECT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("UserMapper.select")
                    .timestamp(Instant.now())
                    .rowsAffected(-1)
                    .build();
        });
    }

    @Test
    void testValidation_timestamp_shouldNotBeInFuture() {
        // Given: A timestamp 10 seconds in the future (beyond tolerance)
        Instant futureTimestamp = Instant.now().plusSeconds(10);

        // When/Then: Future timestamp should throw
        assertThrows(IllegalArgumentException.class, () -> {
            AuditEvent.builder()
                    .sql("SELECT * FROM users")
                    .sqlType(SqlCommandType.SELECT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("UserMapper.select")
                    .timestamp(futureTimestamp)
                    .build();
        });

        // When/Then: Current timestamp should be allowed
        assertDoesNotThrow(() -> {
            AuditEvent.builder()
                    .sql("SELECT * FROM users")
                    .sqlType(SqlCommandType.SELECT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("UserMapper.select")
                    .timestamp(Instant.now())
                    .build();
        });
    }
}












