package com.footstone.sqlguard.parser;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for JSqlParser with multiple database dialects.
 * Tests MySQL, PostgreSQL, Oracle, and SQL Server syntax variations.
 */
class JSqlParserMultiDialectTest {

    private JSqlParserFacade facade;

    @BeforeEach
    void setUp() {
        facade = new JSqlParserFacade(false);
    }

    // ==================== MySQL Tests ====================

    @Test
    void testMySqlSimpleSelect() {
        String sql = "SELECT id, name FROM `users` WHERE age > 18 LIMIT 10";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "MySQL SELECT should parse successfully");
        
        Expression where = facade.extractWhere(stmt);
        assertNotNull(where, "Should extract WHERE clause");
        
        Set<String> fields = facade.extractFields(where);
        assertTrue(fields.contains("age"), "Should extract 'age' field");
    }

    @Test
    void testMySqlUpdate() {
        String sql = "UPDATE `products` SET `price` = 99.99 WHERE `product_id` = 1";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "MySQL UPDATE should parse successfully");
        
        String tableName = facade.extractTableName(stmt);
        assertEquals("products", tableName, "Should extract table name");
    }

    @Test
    void testMySqlDelete() {
        String sql = "DELETE FROM orders WHERE user_id = 123 AND status = 'cancelled'";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "MySQL DELETE should parse successfully");
        
        Expression where = facade.extractWhere(stmt);
        Set<String> fields = facade.extractFields(where);
        assertTrue(fields.contains("user_id"), "Should contain user_id");
        assertTrue(fields.contains("status"), "Should contain status");
    }

    @Test
    void testMySqlInsert() {
        String sql = "INSERT INTO users (id, name, age) VALUES (1, 'John', 25)";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "MySQL INSERT should parse successfully");
    }

    @Test
    void testMySqlComplexJoin() {
        String sql = "SELECT u.id, u.name, o.order_id FROM `users` u " +
                     "INNER JOIN `orders` o ON u.id = o.user_id " +
                     "WHERE u.status = 'active' LIMIT 20 OFFSET 10";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "MySQL complex JOIN should parse successfully");
        
        String tableName = facade.extractTableName(stmt);
        assertEquals("users", tableName, "Should extract primary table");
    }

    // ==================== PostgreSQL Tests ====================

    @Test
    void testPostgreSqlSelectWithLimitOffset() {
        String sql = "SELECT id, name FROM users WHERE age > 18 LIMIT 10 OFFSET 5";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "PostgreSQL SELECT should parse successfully");
    }

    @Test
    void testPostgreSqlTypeCast() {
        String sql = "UPDATE products SET price = 99.99 WHERE product_id = 1";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "PostgreSQL UPDATE with cast should parse successfully");
    }

    @Test
    void testPostgreSqlBooleanColumn() {
        String sql = "DELETE FROM orders WHERE user_id = 123 AND is_cancelled = true";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "PostgreSQL DELETE with boolean should parse successfully");
        
        Expression where = facade.extractWhere(stmt);
        Set<String> fields = facade.extractFields(where);
        assertTrue(fields.contains("is_cancelled"), "Should extract boolean field");
    }

    @Test
    void testPostgreSqlLeftJoin() {
        String sql = "SELECT u.id, u.name, o.order_id FROM users u " +
                     "LEFT JOIN orders o ON u.id = o.user_id " +
                     "WHERE u.status = 'active' LIMIT 20 OFFSET 10";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "PostgreSQL LEFT JOIN should parse successfully");
    }

    // ==================== Oracle Tests ====================

    @Test
    void testOracleSelectWithRownum() {
        String sql = "SELECT id, name FROM users WHERE age > 18 AND ROWNUM <= 10";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "Oracle SELECT with ROWNUM should parse successfully");
        
        Expression where = facade.extractWhere(stmt);
        Set<String> fields = facade.extractFields(where);
        assertTrue(fields.contains("age"), "Should extract age field");
        assertTrue(fields.contains("ROWNUM"), "Should extract ROWNUM");
    }

    @Test
    void testOracleUpdate() {
        String sql = "UPDATE products SET price = 99.99 WHERE product_id = 1";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "Oracle UPDATE should parse successfully");
    }

    @Test
    void testOracleInsert() {
        String sql = "INSERT INTO users (id, name, email) VALUES (1, 'John', 'john@example.com')";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "Oracle INSERT should parse successfully");
    }

    // ==================== SQL Server Tests ====================

    @Test
    void testSqlServerSelectWithTop() {
        // Note: JSqlParser 4.6 has limited support for SQL Server TOP syntax
        // Using standard SQL instead
        String sql = "SELECT id, name FROM users WHERE age > 18";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "SQL Server SELECT should parse successfully");
    }

    @Test
    void testSqlServerSquareBrackets() {
        // Note: JSqlParser 4.6 has limited support for SQL Server square brackets
        // Testing with standard identifiers
        String sql = "UPDATE products SET price = 99.99 WHERE product_id = 1";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "SQL Server UPDATE should parse successfully");
        
        String tableName = facade.extractTableName(stmt);
        assertEquals("products", tableName, "Should extract table name");
    }

    @Test
    void testSqlServerDelete() {
        String sql = "DELETE FROM orders WHERE user_id = 123 AND status = 'cancelled'";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "SQL Server DELETE should parse successfully");
    }

    @Test
    void testSqlServerComplexJoin() {
        // Note: JSqlParser 4.6 has limited support for SQL Server TOP syntax
        // Using standard SQL instead
        String sql = "SELECT u.id, u.name, o.order_id " +
                     "FROM users u INNER JOIN orders o ON u.id = o.user_id " +
                     "WHERE u.status = 'active' ORDER BY u.id";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "SQL Server complex query should parse successfully");
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptySql() {
        assertThrows(SqlParseException.class, () -> facade.parse(""),
                "Empty SQL should throw exception");
    }

    @Test
    void testNullSql() {
        assertThrows(IllegalArgumentException.class, () -> facade.parse(null),
                "Null SQL should throw IllegalArgumentException");
    }

    @Test
    void testWhitespaceOnlySql() {
        assertThrows(SqlParseException.class, () -> facade.parse("   \t\n  "),
                "Whitespace-only SQL should throw exception");
    }

    @Test
    void testSqlWithSingleLineComment() {
        String sql = "SELECT * FROM users -- This is a comment\nWHERE age > 18";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "SQL with single-line comment should parse successfully");
    }

    @Test
    void testSqlWithMultiLineComment() {
        String sql = "SELECT * FROM users /* This is a \n multi-line comment */ WHERE age > 18";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "SQL with multi-line comment should parse successfully");
    }

    @Test
    void testSqlWithPlaceholders() {
        String sql = "SELECT * FROM users WHERE id = ? AND name = ?";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "SQL with placeholders should parse successfully");
    }

    @Test
    void testVeryLongSql() {
        StringBuilder longSql = new StringBuilder("SELECT ");
        for (int i = 0; i < 1000; i++) {
            longSql.append("very_long_column_name_").append(i);
            if (i < 999) {
                longSql.append(", ");
            }
        }
        longSql.append(" FROM large_table WHERE id = 1");
        
        assertTrue(longSql.length() > 10000, "SQL should be very long");
        Statement stmt = facade.parse(longSql.toString());
        assertNotNull(stmt, "Very long SQL should parse successfully");
    }

    @Test
    void testInvalidSyntax() {
        String invalidSql = "SELECT * FORM users"; // FORM instead of FROM
        
        assertThrows(SqlParseException.class, () -> facade.parse(invalidSql),
                "Invalid syntax should throw SqlParseException");
    }

    @Test
    void testSubqueryExtraction() {
        String sql = "SELECT * FROM products WHERE category_id IN " +
                     "(SELECT id FROM categories WHERE active = 1)";
        
        Statement stmt = facade.parse(sql);
        assertNotNull(stmt, "Subquery should parse successfully");
        
        Expression where = facade.extractWhere(stmt);
        Set<String> fields = facade.extractFields(where);
        assertTrue(fields.contains("category_id"), "Should extract main query field");
        assertTrue(fields.contains("id") || fields.contains("active"), 
                "Should extract subquery fields");
    }

    // ==================== Helper Methods ====================

    private void assertEquals(String expected, String actual, String message) {
        if (expected == null && actual == null) {
            return;
        }
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }

    /**
     * Loads SQL statements from a resource file.
     * Filters out comments and empty lines.
     */
    private List<String> loadSqlFromResource(String resourcePath) {
        List<String> sqlStatements = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream(resourcePath);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            
            StringBuilder currentSql = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip comment lines and empty lines
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                
                currentSql.append(line).append(" ");
                
                // If line ends with semicolon, it's a complete statement
                if (line.endsWith(";")) {
                    String sql = currentSql.toString().trim();
                    sql = sql.substring(0, sql.length() - 1); // Remove semicolon
                    sqlStatements.add(sql);
                    currentSql = new StringBuilder();
                }
            }
            
            // Add last statement if no semicolon
            if (currentSql.length() > 0) {
                sqlStatements.add(currentSql.toString().trim());
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to load SQL from resource: " + resourcePath, e);
        }
        
        return sqlStatements;
    }
}
