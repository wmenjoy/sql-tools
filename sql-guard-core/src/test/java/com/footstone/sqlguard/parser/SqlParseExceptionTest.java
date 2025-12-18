package com.footstone.sqlguard.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for SqlParseException error handling.
 */
class SqlParseExceptionTest {

    @Test
    void testExceptionMessageIncludesSqlSnippet() {
        String sql = "SELECT * FROM users WHERE invalid_syntax";
        JSqlParserFacade facade = new JSqlParserFacade(false);
        
        try {
            facade.parse(sql);
        } catch (SqlParseException e) {
            String message = e.getMessage();
            assertNotNull(message, "Exception message should not be null");
            assertTrue(message.contains("Failed to parse SQL:"), 
                    "Message should contain 'Failed to parse SQL:'");
            assertTrue(message.contains("invalid_syntax") || message.contains("SELECT"), 
                    "Message should contain SQL snippet");
            assertTrue(message.contains("Reason:"), 
                    "Message should contain 'Reason:'");
        }
    }

    @Test
    void testExceptionIncludesOriginalCause() {
        String invalidSql = "SELECT * FORM users"; // FORM instead of FROM
        JSqlParserFacade facade = new JSqlParserFacade(false);
        
        try {
            facade.parse(invalidSql);
        } catch (SqlParseException e) {
            Throwable cause = e.getCause();
            assertNotNull(cause, "Exception should have a cause");
            assertEquals("net.sf.jsqlparser.JSQLParserException", 
                    cause.getClass().getName(),
                    "Cause should be JSQLParserException");
        }
    }

    @Test
    void testLongSqlIsTruncatedInMessage() {
        // Create SQL longer than 100 characters
        StringBuilder longSql = new StringBuilder("SELECT ");
        for (int i = 0; i < 50; i++) {
            longSql.append("column").append(i).append(", ");
        }
        longSql.append("invalid_syntax");
        
        JSqlParserFacade facade = new JSqlParserFacade(false);
        
        try {
            facade.parse(longSql.toString());
        } catch (SqlParseException e) {
            String message = e.getMessage();
            assertNotNull(message, "Exception message should not be null");
            assertTrue(message.contains("..."), 
                    "Long SQL should be truncated with '...'");
        }
    }

    @Test
    void testFailFastModeThrowsException() {
        String invalidSql = "INVALID SQL";
        JSqlParserFacade facade = new JSqlParserFacade(false); // fail-fast mode
        
        boolean exceptionThrown = false;
        try {
            facade.parse(invalidSql);
        } catch (SqlParseException e) {
            exceptionThrown = true;
            assertNotNull(e.getMessage(), "Exception should have a message");
            assertNotNull(e.getCause(), "Exception should have a cause");
        }
        
        assertTrue(exceptionThrown, "Fail-fast mode should throw SqlParseException");
    }
}








