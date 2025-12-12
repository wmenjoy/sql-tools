package com.footstone.sqlguard.parser;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for extraction utility methods.
 */
class ExtractionUtilitiesTest {

    private JSqlParserFacade facade;

    @BeforeEach
    void setUp() {
        facade = new JSqlParserFacade(false);
    }

    @Test
    void testExtractWhereFromSelect() {
        String sql = "SELECT id, name FROM users WHERE age > 18";
        Statement statement = facade.parse(sql);
        
        Expression where = facade.extractWhere(statement);
        
        assertNotNull(where, "WHERE clause should not be null");
        assertTrue(where.toString().contains("age"), "WHERE should contain 'age'");
    }

    @Test
    void testExtractWhereFromUpdate() {
        String sql = "UPDATE users SET name = 'John' WHERE id = 1";
        Statement statement = facade.parse(sql);
        
        Expression where = facade.extractWhere(statement);
        
        assertNotNull(where, "WHERE clause should not be null");
        assertTrue(where.toString().contains("id"), "WHERE should contain 'id'");
    }

    @Test
    void testExtractWhereFromDelete() {
        String sql = "DELETE FROM users WHERE status = 'inactive'";
        Statement statement = facade.parse(sql);
        
        Expression where = facade.extractWhere(statement);
        
        assertNotNull(where, "WHERE clause should not be null");
        assertTrue(where.toString().contains("status"), "WHERE should contain 'status'");
    }

    @Test
    void testExtractWhereFromInsertReturnsNull() {
        String sql = "INSERT INTO users (id, name) VALUES (1, 'John')";
        Statement statement = facade.parse(sql);
        
        Expression where = facade.extractWhere(statement);
        
        assertNull(where, "INSERT statement should have no WHERE clause");
    }

    @Test
    void testExtractWhereWithNullStatement() {
        Expression where = facade.extractWhere(null);
        
        assertNull(where, "Null statement should return null");
    }

    @Test
    void testExtractTableNameFromSelect() {
        String sql = "SELECT id, name FROM users WHERE age > 18";
        Statement statement = facade.parse(sql);
        
        String tableName = facade.extractTableName(statement);
        
        assertNotNull(tableName, "Table name should not be null");
        assertEquals("users", tableName, "Table name should be 'users'");
    }

    @Test
    void testExtractTableNameFromUpdate() {
        String sql = "UPDATE products SET price = 100 WHERE id = 1";
        Statement statement = facade.parse(sql);
        
        String tableName = facade.extractTableName(statement);
        
        assertNotNull(tableName, "Table name should not be null");
        assertEquals("products", tableName, "Table name should be 'products'");
    }

    @Test
    void testExtractTableNameFromDelete() {
        String sql = "DELETE FROM orders WHERE status = 'cancelled'";
        Statement statement = facade.parse(sql);
        
        String tableName = facade.extractTableName(statement);
        
        assertNotNull(tableName, "Table name should not be null");
        assertEquals("orders", tableName, "Table name should be 'orders'");
    }

    @Test
    void testExtractTableNameFromJoin() {
        String sql = "SELECT u.id, o.order_id FROM users u JOIN orders o ON u.id = o.user_id";
        Statement statement = facade.parse(sql);
        
        String tableName = facade.extractTableName(statement);
        
        assertNotNull(tableName, "Table name should not be null");
        assertEquals("users", tableName, "First table name should be 'users'");
    }

    @Test
    void testExtractTableNameWithNullStatement() {
        String tableName = facade.extractTableName(null);
        
        assertNull(tableName, "Null statement should return null");
    }

    @Test
    void testExtractFieldsFromSimpleWhere() {
        String sql = "SELECT * FROM users WHERE age > 18";
        Statement statement = facade.parse(sql);
        Expression where = facade.extractWhere(statement);
        
        Set<String> fields = facade.extractFields(where);
        
        assertNotNull(fields, "Fields set should not be null");
        assertEquals(1, fields.size(), "Should have 1 field");
        assertTrue(fields.contains("age"), "Should contain 'age' field");
    }

    @Test
    void testExtractFieldsFromComplexWhere() {
        String sql = "SELECT * FROM users WHERE age > 18 AND status = 'active' AND country = 'US'";
        Statement statement = facade.parse(sql);
        Expression where = facade.extractWhere(statement);
        
        Set<String> fields = facade.extractFields(where);
        
        assertNotNull(fields, "Fields set should not be null");
        assertEquals(3, fields.size(), "Should have 3 fields");
        assertTrue(fields.contains("age"), "Should contain 'age'");
        assertTrue(fields.contains("status"), "Should contain 'status'");
        assertTrue(fields.contains("country"), "Should contain 'country'");
    }

    @Test
    void testExtractFieldsWithOrOperator() {
        String sql = "SELECT * FROM users WHERE age > 18 OR status = 'premium'";
        Statement statement = facade.parse(sql);
        Expression where = facade.extractWhere(statement);
        
        Set<String> fields = facade.extractFields(where);
        
        assertNotNull(fields, "Fields set should not be null");
        assertEquals(2, fields.size(), "Should have 2 fields");
        assertTrue(fields.contains("age"), "Should contain 'age'");
        assertTrue(fields.contains("status"), "Should contain 'status'");
    }

    @Test
    void testExtractFieldsWithNotOperator() {
        String sql = "SELECT * FROM users WHERE NOT (age < 18)";
        Statement statement = facade.parse(sql);
        Expression where = facade.extractWhere(statement);
        
        Set<String> fields = facade.extractFields(where);
        
        assertNotNull(fields, "Fields set should not be null");
        assertTrue(fields.contains("age"), "Should contain 'age'");
    }

    @Test
    void testExtractFieldsFromNestedSubquery() {
        String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders WHERE amount > 100)";
        Statement statement = facade.parse(sql);
        Expression where = facade.extractWhere(statement);
        
        Set<String> fields = facade.extractFields(where);
        
        assertNotNull(fields, "Fields set should not be null");
        assertTrue(fields.contains("id"), "Should contain 'id' from main query");
        assertTrue(fields.contains("user_id") || fields.contains("amount"), 
                "Should contain fields from subquery");
    }

    @Test
    void testExtractFieldsWithTableAlias() {
        String sql = "SELECT * FROM users u WHERE u.age > 18 AND u.status = 'active'";
        Statement statement = facade.parse(sql);
        Expression where = facade.extractWhere(statement);
        
        Set<String> fields = facade.extractFields(where);
        
        assertNotNull(fields, "Fields set should not be null");
        assertTrue(fields.contains("age"), "Should contain 'age' without alias");
        assertTrue(fields.contains("status"), "Should contain 'status' without alias");
    }

    @Test
    void testExtractFieldsWithNullExpression() {
        Set<String> fields = facade.extractFields(null);
        
        assertNotNull(fields, "Should return empty set for null expression");
        assertTrue(fields.isEmpty(), "Set should be empty");
    }
}
