package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Test class for DeniedTableChecker.
 *
 * <p>Verifies that DeniedTableChecker correctly detects access to denied tables
 * with wildcard pattern support. Tests cover table extraction from all SQL locations
 * (FROM, JOIN, subqueries, CTEs) and wildcard matching semantics.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>PASS tests (≥5): Tables not in denied list, allowed tables, queries without denied tables</li>
 *   <li>FAIL tests (≥10): Exact match, wildcard match, JOIN, subquery, CTE, multiple violations</li>
 *   <li>边界 tests (≥3): Alias handling, empty deniedTables, case-insensitive matching</li>
 * </ul>
 *
 * <p><strong>Multi-Dialect Coverage:</strong></p>
 * <ul>
 *   <li>MySQL: Standard syntax, backtick identifiers</li>
 *   <li>Oracle: Schema-qualified tables, subqueries</li>
 *   <li>PostgreSQL: CTE (WITH clause), LATERAL joins</li>
 * </ul>
 *
 * @see DeniedTableChecker
 * @see DeniedTableConfig
 */
@DisplayName("DeniedTableChecker Tests")
class DeniedTableCheckerTest {

    private DeniedTableChecker checker;
    private DeniedTableConfig config;

    @BeforeEach
    void setUp() {
        config = new DeniedTableConfig(true); // Explicitly enable for tests
        config.setDeniedTables(Arrays.asList("sys_*", "admin_*", "audit_log", "sensitive_data"));
        checker = new DeniedTableChecker(config);
    }

    // ==================== PASS Tests (≥5) ====================

    @Nested
    @DisplayName("PASS Tests - Tables not in denied list")
    class PassTests {

        @Test
        @DisplayName("Simple SELECT from allowed table should pass")
        void testSimpleSelect_allowedTable_shouldPass() {
            String sql = "SELECT * FROM users WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "SELECT from allowed table should pass");
            assertEquals(RiskLevel.SAFE, result.getRiskLevel());
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("SELECT with JOIN on allowed tables should pass")
        void testSelectWithJoin_allowedTables_shouldPass() {
            String sql = "SELECT u.*, o.* FROM users u LEFT JOIN orders o ON u.id = o.user_id";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "SELECT with JOIN on allowed tables should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("SELECT with subquery on allowed tables should pass")
        void testSelectWithSubquery_allowedTables_shouldPass() {
            String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders WHERE status = 'active')";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "SELECT with subquery on allowed tables should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("UPDATE on allowed table should pass")
        void testUpdate_allowedTable_shouldPass() {
            String sql = "UPDATE users SET status = 'inactive' WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.UPDATE);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "UPDATE on allowed table should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("DELETE from allowed table should pass")
        void testDelete_allowedTable_shouldPass() {
            String sql = "DELETE FROM orders WHERE status = 'cancelled'";
            SqlContext context = createContext(sql, SqlCommandType.DELETE);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "DELETE from allowed table should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("INSERT into allowed table should pass")
        void testInsert_allowedTable_shouldPass() {
            String sql = "INSERT INTO users (name, email) VALUES ('John', 'john@example.com')";
            SqlContext context = createContext(sql, SqlCommandType.INSERT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "INSERT into allowed table should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("Table 'system' should NOT match sys_* pattern (critical boundary test)")
        void testSystemTable_shouldNotMatchSysPattern_shouldPass() {
            // CRITICAL: 'system' does NOT match 'sys_*' because there's no underscore
            String sql = "SELECT * FROM system WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "'system' should NOT match 'sys_*' pattern");
            assertEquals(0, result.getViolations().size());
        }
    }

    // ==================== FAIL Tests (≥10) ====================

    @Nested
    @DisplayName("FAIL Tests - Denied table access detected")
    class FailTests {

        @Test
        @DisplayName("Exact match: audit_log in denied list should fail")
        void testExactMatch_auditLog_shouldFail() {
            String sql = "SELECT * FROM audit_log WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Access to exact match denied table should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("audit_log"));
        }

        @Test
        @DisplayName("Wildcard match: sys_user matches sys_* pattern should fail")
        void testWildcardMatch_sysUser_shouldFail() {
            String sql = "SELECT * FROM sys_user WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "sys_user should match sys_* pattern");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("sys_user"));
        }

        @Test
        @DisplayName("Wildcard match: sys_config matches sys_* pattern should fail")
        void testWildcardMatch_sysConfig_shouldFail() {
            String sql = "SELECT * FROM sys_config WHERE key = 'app.name'";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "sys_config should match sys_* pattern");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("sys_config"));
        }

        @Test
        @DisplayName("Wildcard match: admin_users matches admin_* pattern should fail")
        void testWildcardMatch_adminUsers_shouldFail() {
            String sql = "SELECT * FROM admin_users WHERE role = 'superadmin'";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "admin_users should match admin_* pattern");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("admin_users"));
        }

        @Test
        @DisplayName("Denied table in JOIN clause should fail")
        void testDeniedTableInJoin_shouldFail() {
            String sql = "SELECT u.* FROM users u JOIN sys_role r ON u.role_id = r.id";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Denied table in JOIN should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("sys_role"));
        }

        @Test
        @DisplayName("Denied table in LEFT JOIN clause should fail")
        void testDeniedTableInLeftJoin_shouldFail() {
            String sql = "SELECT u.*, p.* FROM users u LEFT JOIN sys_permission p ON u.id = p.user_id";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Denied table in LEFT JOIN should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("sys_permission"));
        }

        @Test
        @DisplayName("Denied table in subquery should fail")
        void testDeniedTableInSubquery_shouldFail() {
            String sql = "SELECT * FROM users WHERE role_id IN (SELECT id FROM sys_role WHERE name = 'admin')";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Denied table in subquery should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("sys_role"));
        }

        @Test
        @DisplayName("Denied table in FROM subquery should fail")
        void testDeniedTableInFromSubquery_shouldFail() {
            String sql = "SELECT * FROM (SELECT * FROM sys_config WHERE active = 1) t WHERE t.key LIKE 'db.%'";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Denied table in FROM subquery should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("sys_config"));
        }

        @Test
        @DisplayName("Denied table in CTE (WITH clause) should fail")
        void testDeniedTableInCTE_shouldFail() {
            String sql = "WITH admin_cte AS (SELECT * FROM admin_config WHERE enabled = 1) SELECT * FROM admin_cte";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Denied table in CTE should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("admin_config"));
        }

        @Test
        @DisplayName("Multiple denied tables in same query should fail with all violations")
        void testMultipleDeniedTables_shouldFail() {
            String sql = "SELECT * FROM sys_user u JOIN audit_log a ON u.id = a.user_id";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Multiple denied tables should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            String message = result.getViolations().get(0).getMessage();
            assertTrue(message.contains("sys_user") || message.contains("audit_log"),
                    "Message should contain at least one denied table name");
        }

        @Test
        @DisplayName("UPDATE on denied table should fail")
        void testUpdate_deniedTable_shouldFail() {
            String sql = "UPDATE sys_config SET value = 'test' WHERE key = 'app.name'";
            SqlContext context = createContext(sql, SqlCommandType.UPDATE);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "UPDATE on denied table should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("sys_config"));
        }

        @Test
        @DisplayName("DELETE from denied table should fail")
        void testDelete_deniedTable_shouldFail() {
            String sql = "DELETE FROM audit_log WHERE created_at < '2024-01-01'";
            SqlContext context = createContext(sql, SqlCommandType.DELETE);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "DELETE from denied table should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("audit_log"));
        }

        @Test
        @DisplayName("INSERT into denied table should fail")
        void testInsert_deniedTable_shouldFail() {
            String sql = "INSERT INTO sys_user (name, email) VALUES ('hacker', 'hacker@evil.com')";
            SqlContext context = createContext(sql, SqlCommandType.INSERT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "INSERT into denied table should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("sys_user"));
        }

        @Test
        @DisplayName("Schema-qualified denied table should fail")
        void testSchemaQualifiedDeniedTable_shouldFail() {
            String sql = "SELECT * FROM mydb.sys_user WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Schema-qualified denied table should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("sys_user"));
        }
    }

    // ==================== 边界 Tests (≥3) ====================

    @Nested
    @DisplayName("边界 Tests - Edge cases and boundary conditions")
    class BoundaryTests {

        @Test
        @DisplayName("Table alias should not affect detection (extract actual name, not alias)")
        void testTableAlias_shouldExtractActualName() {
            // 'sys_user AS u' should detect 'sys_user', not 'u'
            String sql = "SELECT u.* FROM sys_user AS u WHERE u.id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Should detect actual table name, not alias");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("sys_user"));
        }

        @Test
        @DisplayName("Empty deniedTables list should allow all tables")
        void testEmptyDeniedTables_shouldAllowAll() {
            // Create checker with empty denied list
            DeniedTableConfig emptyConfig = new DeniedTableConfig();
            emptyConfig.setDeniedTables(Collections.emptyList());
            DeniedTableChecker emptyChecker = new DeniedTableChecker(emptyConfig);

            String sql = "SELECT * FROM sys_user WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            emptyChecker.check(context, result);

            assertTrue(result.isPassed(), "Empty denied list should allow all tables");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("Case-insensitive matching should work")
        void testCaseInsensitiveMatching() {
            // 'SYS_USER' should match 'sys_*' pattern (case-insensitive)
            String sql = "SELECT * FROM SYS_USER WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Case-insensitive matching should detect SYS_USER");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("Disabled checker should skip validation")
        void testDisabledChecker_shouldSkip() {
            DeniedTableConfig disabledConfig = new DeniedTableConfig(false);
            disabledConfig.setDeniedTables(Arrays.asList("sys_*"));
            DeniedTableChecker disabledChecker = new DeniedTableChecker(disabledConfig);

            String sql = "SELECT * FROM sys_user WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            disabledChecker.check(context, result);

            assertTrue(result.isPassed(), "Disabled checker should skip validation");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("Wildcard non-match: sys_user_detail should NOT match sys_* (extra underscore)")
        void testWildcardNonMatch_extraUnderscore_shouldPass() {
            // CRITICAL: 'sys_user_detail' should NOT match 'sys_*' because of extra underscore
            String sql = "SELECT * FROM sys_user_detail WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "'sys_user_detail' should NOT match 'sys_*' pattern (extra underscore)");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("Table 'administrator' should NOT match admin_* pattern")
        void testAdministrator_shouldNotMatchAdminPattern_shouldPass() {
            // 'administrator' does NOT match 'admin_*' because there's no underscore
            String sql = "SELECT * FROM administrator WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "'administrator' should NOT match 'admin_*' pattern");
            assertEquals(0, result.getViolations().size());
        }
    }

    // ==================== Multi-Dialect Tests ====================

    @Nested
    @DisplayName("Multi-Dialect Tests - MySQL, Oracle, PostgreSQL")
    class MultiDialectTests {

        @Test
        @DisplayName("MySQL: Backtick quoted table should be detected")
        void testMySql_backtickQuotedTable_shouldFail() {
            String sql = "SELECT * FROM `sys_user` WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "MySQL backtick quoted denied table should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("Oracle: Double-quoted table should be detected")
        void testOracle_doubleQuotedTable_shouldFail() {
            String sql = "SELECT * FROM \"SYS_USER\" WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Oracle double-quoted denied table should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("PostgreSQL: CTE with multiple tables should detect denied tables")
        void testPostgreSql_cteWithMultipleTables_shouldFail() {
            String sql = "WITH user_cte AS (SELECT * FROM users), " +
                    "config_cte AS (SELECT * FROM sys_config) " +
                    "SELECT * FROM user_cte u JOIN config_cte c ON u.config_id = c.id";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "CTE containing denied table should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("sys_config"));
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a SqlContext with parsed statement for testing.
     */
    private SqlContext createContext(String sql, SqlCommandType type) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            return SqlContext.builder()
                    .sql(sql)
                    .statement(statement)
                    .type(type)
                    .statementId("TestMapper.testMethod")
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SQL: " + sql, e);
        }
    }
}
