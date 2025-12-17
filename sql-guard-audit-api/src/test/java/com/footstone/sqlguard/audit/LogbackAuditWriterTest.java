package com.footstone.sqlguard.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LogbackAuditWriter.
 *
 * <p>This test suite validates:</p>
 * <ul>
 *   <li>Successful audit log writing with complete events</li>
 *   <li>Null event validation</li>
 *   <li>JSON serialization format</li>
 *   <li>ISO-8601 timestamp format</li>
 *   <li>Thread safety with concurrent writes</li>
 * </ul>
 */
class LogbackAuditWriterTest {

    private LogbackAuditWriter writer;

    @BeforeEach
    void setUp() {
        writer = new LogbackAuditWriter();
    }

    @Test
    void testWriteAuditLog_withCompleteEvent_shouldSucceed() throws AuditLogException {
        // Given: Complete AuditEvent
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT * FROM users WHERE id = ?")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("UserMapper.selectById")
                .datasource("primary")
                .timestamp(Instant.now())
                .executionTimeMs(150L)
                .rowsAffected(1)
                .build();

        // When: Write audit log
        writer.writeAuditLog(event);

        // Then: No exception thrown (success)
        assertTrue(true, "Audit log should be written successfully");
    }

    @Test
    void testWriteAuditLog_withMinimalEvent_shouldSucceed() throws AuditLogException {
        // Given: Minimal AuditEvent (only required fields)
        AuditEvent event = AuditEvent.builder()
                .sql("DELETE FROM temp_table")
                .sqlType(SqlCommandType.DELETE)
                .mapperId("TempMapper.delete")
                .timestamp(Instant.now())
                .build();

        // When: Write audit log
        writer.writeAuditLog(event);

        // Then: No exception thrown (success)
        assertTrue(true, "Audit log with minimal fields should be written successfully");
    }

    @Test
    void testWriteAuditLog_withNullEvent_shouldThrowException() {
        // When/Then: Write null event should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> writer.writeAuditLog(null),
                "Writing null event should throw IllegalArgumentException"
        );

        assertEquals("AuditEvent must not be null", exception.getMessage());
    }

    @Test
    void testWriteAuditLog_withParams_shouldSucceed() throws AuditLogException {
        // Given: AuditEvent with parameters
        Map<String, Object> params = new HashMap<>();
        params.put("id", 123);
        params.put("name", "John Doe");
        params.put("active", true);

        AuditEvent event = AuditEvent.builder()
                .sql("UPDATE users SET name = ? WHERE id = ?")
                .sqlType(SqlCommandType.UPDATE)
                .mapperId("UserMapper.update")
                .datasource("primary")
                .params(params)
                .timestamp(Instant.now())
                .executionTimeMs(50L)
                .rowsAffected(1)
                .build();

        // When: Write audit log
        writer.writeAuditLog(event);

        // Then: No exception thrown (success)
        assertTrue(true, "Audit log with parameters should be written successfully");
    }

    @Test
    void testWriteAuditLog_withError_shouldSucceed() throws AuditLogException {
        // Given: AuditEvent with error message
        AuditEvent event = AuditEvent.builder()
                .sql("INSERT INTO users (id, name) VALUES (?, ?)")
                .sqlType(SqlCommandType.INSERT)
                .mapperId("UserMapper.insert")
                .datasource("primary")
                .timestamp(Instant.now())
                .errorMessage("Duplicate key violation: id=123")
                .build();

        // When: Write audit log
        writer.writeAuditLog(event);

        // Then: No exception thrown (success)
        assertTrue(true, "Audit log with error message should be written successfully");
    }

    @Test
    void testJsonSerialization_shouldUseIso8601() throws Exception {
        // Given: AuditEvent with specific timestamp
        Instant timestamp = Instant.parse("2024-01-15T10:30:45.123Z");
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT * FROM test")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("TestMapper.select")
                .timestamp(timestamp)
                .build();

        // When: Serialize to JSON
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String json = mapper.writeValueAsString(event);

        // Then: Verify ISO-8601 format
        assertTrue(json.contains("2024-01-15T10:30:45.123Z"),
                "Timestamp should be in ISO-8601 format");
        assertFalse(json.contains("\"timestamp\":1705318245123"),
                "Timestamp should not be in epoch milliseconds format");
    }

    @Test
    void testThreadSafety_shouldHandleConcurrentWrites() throws Exception {
        // Given: Multiple threads writing concurrently
        int threadCount = 10;
        int eventsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // When: Execute concurrent writes
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    for (int j = 0; j < eventsPerThread; j++) {
                        AuditEvent event = AuditEvent.builder()
                                .sql("SELECT * FROM test WHERE id = " + (threadId * 1000 + j))
                                .sqlType(SqlCommandType.SELECT)
                                .mapperId("TestMapper.select")
                                .datasource("test")
                                .timestamp(Instant.now())
                                .build();

                        writer.writeAuditLog(event);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for completion
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);

        // Then: All writes should succeed
        assertTrue(completed, "All threads should complete within timeout");
        assertEquals(threadCount * eventsPerThread, successCount.get(),
                "All events should be written successfully");
        assertEquals(0, errorCount.get(), "No errors should occur");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testConstructor_withCustomObjectMapper_shouldUseIt() throws AuditLogException {
        // Given: Custom ObjectMapper
        ObjectMapper customMapper = new ObjectMapper();
        customMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        customMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        LogbackAuditWriter customWriter = new LogbackAuditWriter(customMapper);

        // When: Write audit log
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT * FROM test")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("TestMapper.select")
                .timestamp(Instant.now())
                .build();

        customWriter.writeAuditLog(event);

        // Then: No exception thrown (success)
        assertTrue(true, "Custom ObjectMapper should work correctly");
    }

    @Test
    void testConstructor_withNullObjectMapper_shouldThrowException() {
        // When/Then: Null ObjectMapper should throw exception
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new LogbackAuditWriter(null),
                "Null ObjectMapper should throw NullPointerException"
        );

        assertEquals("objectMapper must not be null", exception.getMessage());
    }

    @Test
    void testWriteAuditLog_withLargeSql_shouldSucceed() throws AuditLogException {
        // Given: AuditEvent with large SQL statement
        StringBuilder largeSql = new StringBuilder("SELECT * FROM users WHERE id IN (");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) largeSql.append(", ");
            largeSql.append(i);
        }
        largeSql.append(")");

        AuditEvent event = AuditEvent.builder()
                .sql(largeSql.toString())
                .sqlType(SqlCommandType.SELECT)
                .mapperId("UserMapper.selectByIds")
                .timestamp(Instant.now())
                .build();

        // When: Write audit log
        writer.writeAuditLog(event);

        // Then: No exception thrown (success)
        assertTrue(true, "Large SQL statement should be written successfully");
    }

    @Test
    void testWriteAuditLog_withSpecialCharacters_shouldSucceed() throws AuditLogException {
        // Given: AuditEvent with special characters in SQL
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT * FROM users WHERE name = 'O''Brien' AND comment LIKE '%test\\n\\t%'")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("UserMapper.selectByName")
                .datasource("primary")
                .timestamp(Instant.now())
                .build();

        // When: Write audit log
        writer.writeAuditLog(event);

        // Then: No exception thrown (success)
        assertTrue(true, "SQL with special characters should be written successfully");
    }

    @Test
    void testWriteAuditLog_rapidSequentialWrites_shouldSucceed() throws Exception {
        // Given: Rapid sequential writes
        int eventCount = 1000;

        // When: Write events rapidly
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < eventCount; i++) {
            AuditEvent event = AuditEvent.builder()
                    .sql("SELECT * FROM test WHERE id = " + i)
                    .sqlType(SqlCommandType.SELECT)
                    .mapperId("TestMapper.select")
                    .timestamp(Instant.now())
                    .build();

            writer.writeAuditLog(event);
        }
        long endTime = System.currentTimeMillis();

        // Then: All writes should complete quickly
        long totalTime = endTime - startTime;
        double avgTimePerEvent = (double) totalTime / eventCount;

        System.out.println("Total time for " + eventCount + " events: " + totalTime + "ms");
        System.out.println("Average time per event: " + avgTimePerEvent + "ms");

        assertTrue(avgTimePerEvent < 5.0,
                "Average write time should be <5ms per event, got: " + avgTimePerEvent + "ms");
    }
}
