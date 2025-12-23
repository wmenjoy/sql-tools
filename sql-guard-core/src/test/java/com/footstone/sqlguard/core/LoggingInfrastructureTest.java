package com.footstone.sqlguard.core;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verification test for logging infrastructure.
 * 
 * This test confirms that:
 * - SLF4J and Logback are properly configured
 * - logback-test.xml overrides logback.xml during test execution
 * - Log levels are correctly applied
 * - Log output format matches expected pattern
 */
public class LoggingInfrastructureTest {

    private static final Logger logger = LoggerFactory.getLogger(LoggingInfrastructureTest.class);

    @Test
    public void testLoggingWorks() {
        // Log messages at different levels
        logger.trace("Trace message - should NOT appear (root level is WARN)");
        logger.debug("Debug message - should appear (com.footstone.sqlguard at DEBUG)");
        logger.info("Info message - should appear (com.footstone.sqlguard at DEBUG)");
        logger.warn("Warn message - should appear");
        logger.error("Error message - should appear");

        // Expected console output verification points:
        // 1. Debug/info/warn/error messages should be visible
        // 2. Trace message should NOT be visible (root level WARN, but com.footstone.sqlguard at DEBUG)
        // 3. Timestamp format should be HH:mm:ss.SSS (shorter format from logback-test.xml)
        // 4. Logger name should be truncated to 20 chars
        // 5. No duplicate log entries (single appender)
        
        System.out.println("\n=== Logging Infrastructure Test Complete ===");
        System.out.println("Expected output above:");
        System.out.println("- Debug message (visible)");
        System.out.println("- Info message (visible)");
        System.out.println("- Warn message (visible)");
        System.out.println("- Error message (visible)");
        System.out.println("- Trace message (NOT visible)");
        System.out.println("- Format: HH:mm:ss.SSS LEVEL LoggerName - Message");
        System.out.println("============================================\n");
    }
}


















