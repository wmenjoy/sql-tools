package com.footstone.sqlguard.parser;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for lenient mode logging behavior.
 */
class LenientModeTest {

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // Set up Logback test appender to capture log messages
        logger = (Logger) LoggerFactory.getLogger(JSqlParserFacade.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        // Clean up appender
        logger.detachAppender(listAppender);
    }

    @Test
    void testLenientModeReturnsNullForInvalidSql() {
        JSqlParserFacade facade = new JSqlParserFacade(true); // lenient mode
        String invalidSql = "SELECT * FORM users"; // FORM instead of FROM
        
        Statement result = facade.parse(invalidSql);
        
        assertNull(result, "Lenient mode should return null for invalid SQL");
    }

    @Test
    void testLenientModeLogsWarning() {
        JSqlParserFacade facade = new JSqlParserFacade(true); // lenient mode
        String invalidSql = "INVALID SQL";
        
        facade.parse(invalidSql);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.size() > 0, "Should have logged at least one message");
        
        ILoggingEvent logEvent = logsList.get(0);
        assertEquals(Level.WARN, logEvent.getLevel(), "Log level should be WARN");
        assertTrue(logEvent.getFormattedMessage().contains("Failed to parse SQL:"),
                "Log message should contain 'Failed to parse SQL:'");
    }

    @Test
    void testLenientModeLogsWarningForEmptySql() {
        JSqlParserFacade facade = new JSqlParserFacade(true); // lenient mode
        
        Statement result = facade.parse("   ");
        
        assertNull(result, "Lenient mode should return null for empty SQL");
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.size() > 0, "Should have logged a warning");
        
        ILoggingEvent logEvent = logsList.get(0);
        assertEquals(Level.WARN, logEvent.getLevel(), "Log level should be WARN");
    }

    @Test
    void testIsLenientModeGetter() {
        JSqlParserFacade failFastFacade = new JSqlParserFacade(false);
        JSqlParserFacade lenientFacade = new JSqlParserFacade(true);
        
        assertEquals(false, failFastFacade.isLenientMode(), 
                "Fail-fast facade should return false");
        assertEquals(true, lenientFacade.isLenientMode(), 
                "Lenient facade should return true");
    }
}
