package com.footstone.sqlguard.audit;

import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for AuditLogWriter interface implementation.
 * 
 * <p>Tests the contract that all AuditLogWriter implementations must follow,
 * including validation of required fields and proper error handling.</p>
 */
class AuditLogWriterTest {

    private TestAuditLogWriter writer;

    @BeforeEach
    void setUp() {
        writer = new TestAuditLogWriter();
    }

    @Test
    void testWriteAuditLog_withCompleteEvent_shouldSucceed() throws AuditLogException {
        // Given: A complete audit event with all required fields
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT * FROM users WHERE id = ?")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("com.example.UserMapper.selectById")
                .datasource("primary")
                .timestamp(Instant.now())
                .executionTimeMs(150L)
                .rowsAffected(1)
                .build();

        // When: Writing the audit log
        writer.writeAuditLog(event);

        // Then: Should succeed without throwing exception
        assertEquals(1, writer.getWrittenEvents().size());
        assertEquals(event, writer.getWrittenEvents().get(0));
    }

    @Test
    void testWriteAuditLog_withMissingRequiredFields_shouldThrowException() {
        // Given: An event missing required field 'sql' - should fail at build time
        assertThrows(IllegalArgumentException.class, () -> {
            AuditEvent.builder()
                    .sqlType(SqlCommandType.SELECT)
                    .mapperId("com.example.UserMapper.selectById")
                    .timestamp(Instant.now())
                    .build();
        });

        // Given: An event missing required field 'sqlType' - should fail at build time
        assertThrows(IllegalArgumentException.class, () -> {
            AuditEvent.builder()
                    .sql("SELECT * FROM users")
                    .mapperId("com.example.UserMapper.selectById")
                    .timestamp(Instant.now())
                    .build();
        });

        // Given: An event missing required field 'mapperId' - should fail at build time
        assertThrows(IllegalArgumentException.class, () -> {
            AuditEvent.builder()
                    .sql("SELECT * FROM users")
                    .sqlType(SqlCommandType.SELECT)
                    .timestamp(Instant.now())
                    .build();
        });

        // Given: An event missing required field 'timestamp' - should fail at build time
        assertThrows(IllegalArgumentException.class, () -> {
            AuditEvent.builder()
                    .sql("SELECT * FROM users")
                    .sqlType(SqlCommandType.SELECT)
                    .mapperId("com.example.UserMapper.selectById")
                    .build();
        });
    }

    @Test
    void testWriteAuditLog_withNullEvent_shouldThrowException() {
        // When/Then: Passing null event should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            writer.writeAuditLog(null);
        });
    }

    /**
     * Test implementation of AuditLogWriter for testing purposes.
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
            if (event.getMapperId() == null) {
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











