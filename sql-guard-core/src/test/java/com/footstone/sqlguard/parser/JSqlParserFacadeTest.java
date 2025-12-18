package com.footstone.sqlguard.parser;

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for JSqlParserFacade basic parsing functionality.
 */
class JSqlParserFacadeTest {

    private JSqlParserFacade facade;

    @BeforeEach
    void setUp() {
        // Default to fail-fast mode (lenientMode = false)
        facade = new JSqlParserFacade(false);
    }

    @Test
    void testParseValidSelectStatement() {
        String sql = "SELECT id, name FROM users WHERE age > 18";
        Statement statement = facade.parse(sql);
        
        assertNotNull(statement, "Parsed statement should not be null");
        assertTrue(statement instanceof Select, "Statement should be a SELECT");
    }

    @Test
    void testParseValidUpdateStatement() {
        String sql = "UPDATE users SET name = 'John' WHERE id = 1";
        Statement statement = facade.parse(sql);
        
        assertNotNull(statement, "Parsed statement should not be null");
        assertTrue(statement instanceof Update, "Statement should be an UPDATE");
    }

    @Test
    void testParseValidDeleteStatement() {
        String sql = "DELETE FROM users WHERE id = 1";
        Statement statement = facade.parse(sql);
        
        assertNotNull(statement, "Parsed statement should not be null");
        assertTrue(statement instanceof Delete, "Statement should be a DELETE");
    }

    @Test
    void testParseValidInsertStatement() {
        String sql = "INSERT INTO users (id, name, age) VALUES (1, 'John', 25)";
        Statement statement = facade.parse(sql);
        
        assertNotNull(statement, "Parsed statement should not be null");
        assertTrue(statement instanceof Insert, "Statement should be an INSERT");
    }

    @Test
    void testParseInvalidSqlSyntaxThrowsException() {
        String invalidSql = "SELECT * FORM users"; // FORM instead of FROM
        
        assertThrows(SqlParseException.class, () -> facade.parse(invalidSql),
                "Invalid SQL syntax should throw SqlParseException");
    }

    @Test
    void testParseNullSqlThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> facade.parse(null),
                "Null SQL should throw IllegalArgumentException");
    }

    @Test
    void testParseEmptySqlThrowsException() {
        assertThrows(SqlParseException.class, () -> facade.parse(""),
                "Empty SQL should throw SqlParseException");
    }

    @Test
    void testParseWhitespaceOnlySqlThrowsException() {
        assertThrows(SqlParseException.class, () -> facade.parse("   \t\n  "),
                "Whitespace-only SQL should throw SqlParseException");
    }
}








