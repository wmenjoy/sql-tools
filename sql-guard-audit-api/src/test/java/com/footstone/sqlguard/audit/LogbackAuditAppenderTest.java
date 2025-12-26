package com.footstone.sqlguard.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Logback AsyncAppender configuration and behavior.
 *
 * <p>This test suite validates:</p>
 * <ul>
 *   <li>AsyncAppender non-blocking write behavior</li>
 *   <li>Audit logger isolation from root logger</li>
 *   <li>Rolling policy date directory structure</li>
 *   <li>Queue saturation handling</li>
 * </ul>
 */
class LogbackAuditAppenderTest {

    private static final String AUDIT_LOGGER_NAME = "com.footstone.sqlguard.audit.AUDIT";
    
    @TempDir
    Path tempDir;
    
    private Logger auditLogger;
    private Logger rootLogger;
    private ListAppender<ILoggingEvent> listAppender;
    
    @BeforeEach
    void setUp() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        auditLogger = context.getLogger(AUDIT_LOGGER_NAME);
        rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        
        // Add list appender to capture log events for testing
        listAppender = new ListAppender<>();
        listAppender.setContext(context);
        listAppender.start();
    }
    
    @AfterEach
    void tearDown() {
        if (listAppender != null) {
            listAppender.stop();
        }
    }

    @Test
    void testAsyncAppender_shouldNotBlock() throws Exception {
        // Given: LogbackAuditWriter with async appender
        LogbackAuditWriter writer = new LogbackAuditWriter();
        
        // When: Write 1000 events and measure latency
        int eventCount = 1000;
        List<Long> latencies = new ArrayList<>();
        
        for (int i = 0; i < eventCount; i++) {
            AuditEvent event = createSampleEvent("SELECT * FROM users WHERE id = " + i);
            
            long startNanos = System.nanoTime();
            writer.writeAuditLog(event);
            long endNanos = System.nanoTime();
            
            latencies.add(endNanos - startNanos);
        }
        
        // Wait for async processing
        Thread.sleep(100);
        
        // Then: Calculate p99 latency
        latencies.sort(Long::compareTo);
        long p99Index = (long) (eventCount * 0.99);
        long p99LatencyNanos = latencies.get((int) p99Index);
        double p99LatencyMs = p99LatencyNanos / 1_000_000.0;
        
        System.out.println("P99 write latency: " + p99LatencyMs + "ms");
        
        // Verify: <2ms p99 latency (async should be very fast)
        // Note: Target is <1ms but allowing 2ms for test environment variability
        assertTrue(p99LatencyMs < 2.0, 
                "P99 latency should be <2ms, got: " + p99LatencyMs + "ms");
    }

    @Test
    void testAuditLogger_shouldIsolateFromRoot() {
        // Given: List appender attached to root logger
        rootLogger.addAppender(listAppender);
        
        // When: Write audit event
        auditLogger.info("Test audit event");
        
        // Then: Event should NOT propagate to root logger (additivity=false)
        assertEquals(0, listAppender.list.size(), 
                "Audit events should not propagate to root logger");
        
        // Cleanup
        rootLogger.detachAppender(listAppender);
    }

    @Test
    void testAuditLogger_shouldWriteToAuditAppender() {
        // Given: List appender attached to audit logger
        auditLogger.addAppender(listAppender);
        
        // When: Write audit event
        String testMessage = "Test audit event";
        auditLogger.info(testMessage);
        
        // Then: Event should appear in audit logger's appender
        assertEquals(1, listAppender.list.size(), 
                "Audit event should be captured by audit logger");
        assertEquals(testMessage, listAppender.list.get(0).getMessage(),
                "Message should match");
        
        // Cleanup
        auditLogger.detachAppender(listAppender);
    }

    @Test
    void testRollingPolicy_shouldCreateDateDirectories() throws IOException {
        // Given: RollingFileAppender configured with date pattern
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(AUDIT_LOGGER_NAME);
        
        // Find the RollingFileAppender
        RollingFileAppender<?> rollingAppender = findRollingFileAppender(logger);
        
        // Then: Verify rolling policy pattern includes date directories
        if (rollingAppender != null) {
            String fileNamePattern = rollingAppender.getRollingPolicy().toString();
            assertTrue(fileNamePattern.contains("yyyy-MM-dd") || 
                      fileNamePattern.contains("%d{yyyy-MM-dd}"),
                    "Rolling policy should include date-based directory structure");
        }
    }

    @Test
    void testQueueFull_shouldBlock() throws Exception {
        // Given: LogbackAuditWriter with async appender (discardingThreshold=0)
        LogbackAuditWriter writer = new LogbackAuditWriter();
        
        // When: Write events rapidly to potentially fill queue
        int eventCount = 10000;
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        executor.submit(() -> {
            try {
                for (int i = 0; i < eventCount; i++) {
                    AuditEvent event = createSampleEvent("SELECT * FROM test WHERE id = " + i);
                    writer.writeAuditLog(event);
                }
            } catch (AuditLogException e) {
                throw new RuntimeException(e);
            }
            latch.countDown();
        });
        
        // Then: Should complete without throwing exceptions (blocking behavior)
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "All events should be written (with blocking if queue full)");
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testAsyncAppender_shouldHaveCorrectQueueSize() {
        // Given: Audit logger with async appender
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(AUDIT_LOGGER_NAME);
        
        // When: Find async appender
        ch.qos.logback.classic.AsyncAppender asyncAppender = findAsyncAppender(logger);
        
        // Then: Verify queue size configuration
        if (asyncAppender != null) {
            int queueSize = asyncAppender.getQueueSize();
            assertTrue(queueSize >= 8192, 
                    "Queue size should be >= 8192, got: " + queueSize);
        }
    }

    @Test
    void testAsyncAppender_shouldNotDiscardEvents() {
        // Given: Audit logger with async appender
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(AUDIT_LOGGER_NAME);
        
        // When: Find async appender
        ch.qos.logback.classic.AsyncAppender asyncAppender = findAsyncAppender(logger);
        
        // Then: Verify discardingThreshold=0 (no event loss)
        if (asyncAppender != null) {
            int discardingThreshold = asyncAppender.getDiscardingThreshold();
            assertEquals(0, discardingThreshold, 
                    "Discarding threshold should be 0 to prevent audit loss");
        }
    }

    @Test
    void testAsyncAppender_shouldBlockWhenFull() {
        // Given: Audit logger with async appender
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(AUDIT_LOGGER_NAME);
        
        // When: Find async appender
        ch.qos.logback.classic.AsyncAppender asyncAppender = findAsyncAppender(logger);
        
        // Then: Verify neverBlock=false (blocking enabled)
        if (asyncAppender != null) {
            boolean neverBlock = asyncAppender.isNeverBlock();
            assertFalse(neverBlock, 
                    "neverBlock should be false to prevent audit loss");
        }
    }

    // Helper methods

    private AuditEvent createSampleEvent(String sql) {
        return AuditEvent.builder()
                .sql(sql)
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("TestMapper.select")
                .datasource("test")
                .timestamp(Instant.now())
                .executionTimeMs(10L)
                .rowsAffected(1)
                .build();
    }

    private ch.qos.logback.classic.AsyncAppender findAsyncAppender(Logger logger) {
        Iterator<Appender<ILoggingEvent>> iter = logger.iteratorForAppenders();
        while (iter.hasNext()) {
            Appender<ILoggingEvent> appender = iter.next();
            if (appender instanceof ch.qos.logback.classic.AsyncAppender) {
                return (ch.qos.logback.classic.AsyncAppender) appender;
            }
        }
        return null;
    }

    private RollingFileAppender<?> findRollingFileAppender(Logger logger) {
        Iterator<Appender<ILoggingEvent>> iter = logger.iteratorForAppenders();
        while (iter.hasNext()) {
            Appender<ILoggingEvent> appender = iter.next();
            if (appender instanceof ch.qos.logback.classic.AsyncAppender) {
                ch.qos.logback.classic.AsyncAppender asyncAppender = 
                        (ch.qos.logback.classic.AsyncAppender) appender;
                Iterator<Appender<ILoggingEvent>> asyncIter = asyncAppender.iteratorForAppenders();
                while (asyncIter.hasNext()) {
                    Appender<ILoggingEvent> nested = asyncIter.next();
                    if (nested instanceof RollingFileAppender) {
                        return (RollingFileAppender<?>) nested;
                    }
                }
            }
        }
        return null;
    }
}
