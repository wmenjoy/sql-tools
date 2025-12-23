package com.footstone.sqlguard.dialect;

import com.footstone.sqlguard.dialect.impl.MySQLDialect;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for complex SQL handling by SqlGuardDialect implementations.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Subqueries in WHERE clause</li>
 *   <li>Subqueries in FROM clause (derived tables)</li>
 *   <li>UNION/UNION ALL operations</li>
 *   <li>JOINs</li>
 *   <li>CTEs (Common Table Expressions)</li>
 * </ul>
 *
 * @since 1.1.0
 */
@DisplayName("Complex SQL Dialect Tests")
class ComplexSqlDialectTest {

    private MySQLDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new MySQLDialect();
    }

    @Nested
    @DisplayName("Simple SELECT Tests")
    class SimpleSelectTests {

        @Test
        @DisplayName("Should handle simple SELECT")
        void testSimpleSelect() throws Exception {
            String sql = "SELECT * FROM users";
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            
            assertInstanceOf(PlainSelect.class, select.getSelectBody());
            
            dialect.applyLimit(select, 1000);
            
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("limit 1000"), "Expected LIMIT 1000, got: " + result);
        }

        @Test
        @DisplayName("Should handle SELECT with JOIN")
        void testSelectWithJoin() throws Exception {
            String sql = "SELECT u.*, o.* FROM users u LEFT JOIN orders o ON u.id = o.user_id";
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            
            assertInstanceOf(PlainSelect.class, select.getSelectBody());
            
            dialect.applyLimit(select, 1000);
            
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("limit 1000"), "Expected LIMIT 1000, got: " + result);
            assertTrue(result.contains("left join"), "Should preserve JOIN: " + result);
        }
    }

    @Nested
    @DisplayName("Subquery Tests")
    class SubqueryTests {

        @Test
        @DisplayName("Should handle subquery in WHERE clause")
        void testSubqueryInWhere() throws Exception {
            String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders)";
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            
            assertInstanceOf(PlainSelect.class, select.getSelectBody());
            
            dialect.applyLimit(select, 1000);
            
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("limit 1000"), "Expected LIMIT 1000, got: " + result);
            // Subquery should NOT have LIMIT added (only outer query)
            assertTrue(result.contains("select user_id from orders"), 
                    "Subquery should be preserved: " + result);
        }

        @Test
        @DisplayName("Should handle derived table (subquery in FROM)")
        void testDerivedTable() throws Exception {
            String sql = "SELECT * FROM (SELECT * FROM users WHERE active = 1) t";
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            
            assertInstanceOf(PlainSelect.class, select.getSelectBody());
            
            dialect.applyLimit(select, 1000);
            
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("limit 1000"), "Expected LIMIT 1000, got: " + result);
        }
    }

    @Nested
    @DisplayName("UNION Tests")
    class UnionTests {

        @Test
        @DisplayName("Should detect UNION as SetOperationList")
        void testUnionDetection() throws Exception {
            String sql = "SELECT id, name FROM users UNION SELECT id, name FROM admins";
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            
            // UNION produces SetOperationList, not PlainSelect
            assertInstanceOf(SetOperationList.class, select.getSelectBody(),
                    "UNION should produce SetOperationList");
        }

        @Test
        @DisplayName("Should detect UNION ALL as SetOperationList")
        void testUnionAllDetection() throws Exception {
            String sql = "SELECT * FROM users UNION ALL SELECT * FROM admins";
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            
            assertInstanceOf(SetOperationList.class, select.getSelectBody(),
                    "UNION ALL should produce SetOperationList");
        }

        @Test
        @DisplayName("Current dialect implementation skips UNION (PlainSelect only)")
        void testDialectSkipsUnion() throws Exception {
            String sql = "SELECT id, name FROM users UNION SELECT id, name FROM admins";
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            
            String originalSql = select.toString();
            
            // Current implementation only handles PlainSelect
            // UNION (SetOperationList) is skipped - no modification
            dialect.applyLimit(select, 1000);
            
            String afterSql = select.toString();
            
            // SQL should be unchanged (UNION not modified)
            assertEquals(originalSql, afterSql, 
                    "UNION query should be unchanged (current implementation skips it)");
        }
    }

    @Nested
    @DisplayName("CTE (WITH clause) Tests")
    class CteTests {

        @Test
        @DisplayName("Should handle CTE with PlainSelect main query")
        void testCteWithPlainSelect() throws Exception {
            String sql = "WITH cte AS (SELECT * FROM users) SELECT * FROM cte";
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            
            // Main query is PlainSelect
            assertInstanceOf(PlainSelect.class, select.getSelectBody());
            
            dialect.applyLimit(select, 1000);
            
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("limit 1000"), "Expected LIMIT 1000, got: " + result);
            assertTrue(result.contains("with cte as"), "CTE should be preserved: " + result);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle nested subqueries")
        void testNestedSubqueries() throws Exception {
            String sql = "SELECT * FROM users WHERE id IN " +
                    "(SELECT user_id FROM orders WHERE product_id IN " +
                    "(SELECT id FROM products WHERE category = 'electronics'))";
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            
            assertInstanceOf(PlainSelect.class, select.getSelectBody());
            
            dialect.applyLimit(select, 1000);
            
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("limit 1000"), "Expected LIMIT 1000, got: " + result);
        }

        @Test
        @DisplayName("Should handle complex WHERE with multiple conditions")
        void testComplexWhere() throws Exception {
            String sql = "SELECT * FROM users WHERE " +
                    "status = 'active' AND " +
                    "(role = 'admin' OR role = 'moderator') AND " +
                    "created_at > '2023-01-01'";
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            
            dialect.applyLimit(select, 1000);
            
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("limit 1000"), "Expected LIMIT 1000, got: " + result);
            assertTrue(result.contains("status = 'active'"), "WHERE should be preserved: " + result);
        }
    }
}

