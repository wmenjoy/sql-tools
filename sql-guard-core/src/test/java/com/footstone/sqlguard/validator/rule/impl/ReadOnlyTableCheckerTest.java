package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Test class for ReadOnlyTableChecker.
 *
 * <p>Comprehensive tests covering:</p>
 * <ul>
 *   <li>PASS tests (≥5): SELECT from readonly tables, writes to non-readonly tables</li>
 *   <li>FAIL tests (≥10): INSERT/UPDATE/DELETE on readonly tables, wildcard matching</li>
 *   <li>Edge tests (≥3): Schema-qualified names, case-insensitive matching, empty config</li>
 * </ul>
 *
 * <p>Tests cover MySQL, Oracle, and PostgreSQL syntax variations.</p>
 *
 * @see ReadOnlyTableChecker
 * @see ReadOnlyTableConfig
 */
@DisplayName("ReadOnlyTableChecker Tests")
class ReadOnlyTableCheckerTest {

    private JSqlParserFacade parser;
    private ReadOnlyTableChecker checker;
    private ReadOnlyTableConfig config;

    @BeforeEach
    void setUp() {
        parser = new JSqlParserFacade();
        config = new ReadOnlyTableConfig();
        // Default readonly tables for testing: audit_log, history_*
        config.setReadonlyTables(Arrays.asList("audit_log", "history_*"));
        checker = new ReadOnlyTableChecker(config);
    }

    // ==================== PASS Tests (≥5) ====================

    @Nested
    @DisplayName("PASS Tests - Operations that should be allowed")
    class PassTests {

        @Test
        @DisplayName("SELECT from readonly table should pass - reading is allowed")
        void testSelectFromReadonlyTable_shouldPass() throws Exception {
            String sql = "SELECT * FROM audit_log WHERE id = 1";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.SELECT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.AuditMapper.selectById")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "SELECT from readonly table should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("INSERT into non-readonly table should pass")
        void testInsertIntoNonReadonlyTable_shouldPass() throws Exception {
            String sql = "INSERT INTO users (name, email) VALUES ('John', 'john@example.com')";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.INSERT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.UserMapper.insert")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "INSERT into non-readonly table should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("UPDATE non-readonly table should pass")
        void testUpdateNonReadonlyTable_shouldPass() throws Exception {
            String sql = "UPDATE users SET status = 'active' WHERE id = 1";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.UPDATE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.UserMapper.updateStatus")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "UPDATE non-readonly table should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("DELETE from non-readonly table should pass")
        void testDeleteFromNonReadonlyTable_shouldPass() throws Exception {
            String sql = "DELETE FROM users WHERE id = 1";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.DELETE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.UserMapper.deleteById")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "DELETE from non-readonly table should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("UPDATE with readonly table in WHERE clause only should pass")
        void testUpdateWithReadonlyInWhereClause_shouldPass() throws Exception {
            // UPDATE user table, but audit_log is only in WHERE subquery - this is OK
            String sql = "UPDATE users SET status = 1 WHERE id IN (SELECT user_id FROM audit_log WHERE action = 'login')";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.UPDATE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.UserMapper.updateByAudit")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "UPDATE with readonly table only in WHERE should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("DELETE with readonly table in WHERE clause only should pass")
        void testDeleteWithReadonlyInWhereClause_shouldPass() throws Exception {
            // DELETE from user table, but audit_log is only in WHERE subquery - this is OK
            String sql = "DELETE FROM users WHERE id IN (SELECT user_id FROM audit_log)";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.DELETE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.UserMapper.deleteByAudit")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "DELETE with readonly table only in WHERE should pass");
            assertEquals(0, result.getViolations().size());
        }
    }

    // ==================== FAIL Tests (≥10) ====================

    @Nested
    @DisplayName("FAIL Tests - Write operations on readonly tables")
    class FailTests {

        @Test
        @DisplayName("INSERT into readonly table should fail")
        void testInsertIntoReadonlyTable_shouldFail() throws Exception {
            String sql = "INSERT INTO audit_log (user_id, action) VALUES (1, 'login')";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.INSERT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.AuditMapper.insert")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "INSERT into readonly table should fail");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("INSERT"));
            assertTrue(result.getViolations().get(0).getMessage().contains("audit_log"));
        }

        @Test
        @DisplayName("UPDATE readonly table should fail")
        void testUpdateReadonlyTable_shouldFail() throws Exception {
            String sql = "UPDATE audit_log SET action = 'modified' WHERE id = 1";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.UPDATE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.AuditMapper.update")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "UPDATE readonly table should fail");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("UPDATE"));
            assertTrue(result.getViolations().get(0).getMessage().contains("audit_log"));
        }

        @Test
        @DisplayName("DELETE from readonly table should fail")
        void testDeleteFromReadonlyTable_shouldFail() throws Exception {
            String sql = "DELETE FROM audit_log WHERE id = 1";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.DELETE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.AuditMapper.delete")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "DELETE from readonly table should fail");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("DELETE"));
            assertTrue(result.getViolations().get(0).getMessage().contains("audit_log"));
        }

        @Test
        @DisplayName("INSERT into wildcard-matched readonly table (history_users) should fail")
        void testInsertIntoWildcardMatchedTable_shouldFail() throws Exception {
            // history_users matches pattern history_*
            String sql = "INSERT INTO history_users (user_id, snapshot) VALUES (1, 'data')";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.INSERT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.HistoryMapper.insert")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "INSERT into wildcard-matched table should fail");
            assertEquals(1, result.getViolations().size());
            assertTrue(result.getViolations().get(0).getMessage().contains("history_users"));
        }

        @Test
        @DisplayName("UPDATE wildcard-matched readonly table (history_orders) should fail")
        void testUpdateWildcardMatchedTable_shouldFail() throws Exception {
            // history_orders matches pattern history_*
            String sql = "UPDATE history_orders SET status = 'archived' WHERE id = 1";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.UPDATE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.HistoryMapper.update")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "UPDATE wildcard-matched table should fail");
            assertEquals(1, result.getViolations().size());
            assertTrue(result.getViolations().get(0).getMessage().contains("history_orders"));
        }

        @Test
        @DisplayName("DELETE from wildcard-matched readonly table (history_payments) should fail")
        void testDeleteFromWildcardMatchedTable_shouldFail() throws Exception {
            // history_payments matches pattern history_*
            String sql = "DELETE FROM history_payments WHERE created_at < '2020-01-01'";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.DELETE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.HistoryMapper.delete")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "DELETE from wildcard-matched table should fail");
            assertEquals(1, result.getViolations().size());
            assertTrue(result.getViolations().get(0).getMessage().contains("history_payments"));
        }

        @Test
        @DisplayName("MySQL: INSERT into readonly table should fail")
        void testMySqlInsertIntoReadonlyTable_shouldFail() throws Exception {
            // MySQL syntax with backticks
            String sql = "INSERT INTO `audit_log` (`user_id`, `action`) VALUES (1, 'login')";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.INSERT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.AuditMapper.insertMySql")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "MySQL INSERT into readonly table should fail");
            assertEquals(1, result.getViolations().size());
        }

        @Test
        @DisplayName("PostgreSQL: UPDATE readonly table should fail")
        void testPostgreSqlUpdateReadonlyTable_shouldFail() throws Exception {
            // PostgreSQL syntax with double quotes
            String sql = "UPDATE \"audit_log\" SET \"action\" = 'modified' WHERE \"id\" = 1";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.UPDATE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.AuditMapper.updatePg")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "PostgreSQL UPDATE readonly table should fail");
            assertEquals(1, result.getViolations().size());
        }

        @Test
        @DisplayName("Oracle: DELETE from readonly table should fail")
        void testOracleDeleteFromReadonlyTable_shouldFail() throws Exception {
            // Oracle syntax
            String sql = "DELETE FROM AUDIT_LOG WHERE ID = 1";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.DELETE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.AuditMapper.deleteOracle")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Oracle DELETE from readonly table should fail");
            assertEquals(1, result.getViolations().size());
        }

        @Test
        @DisplayName("Multiple readonly table patterns - INSERT into audit_* should fail")
        void testInsertIntoMultiplePatternMatchedTable_shouldFail() throws Exception {
            // Configure multiple patterns
            config.setReadonlyTables(Arrays.asList("audit_*", "history_*", "archive_*"));
            checker = new ReadOnlyTableChecker(config);

            String sql = "INSERT INTO audit_events (event_type) VALUES ('user_login')";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.INSERT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.AuditMapper.insertEvent")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "INSERT into audit_* matched table should fail");
            assertEquals(1, result.getViolations().size());
        }
    }

    // ==================== Edge Tests (≥3) ====================

    @Nested
    @DisplayName("Edge Tests - Boundary conditions")
    class EdgeTests {

        @Test
        @DisplayName("Schema-qualified name (db.audit_log) should be detected as readonly")
        void testSchemaQualifiedReadonlyTable_shouldFail() throws Exception {
            String sql = "INSERT INTO mydb.audit_log (user_id, action) VALUES (1, 'login')";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.INSERT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.AuditMapper.insertWithSchema")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Schema-qualified readonly table should be detected");
            assertEquals(1, result.getViolations().size());
        }

        @Test
        @DisplayName("Case-insensitive matching - AUDIT_LOG should match audit_log")
        void testCaseInsensitiveMatching_shouldFail() throws Exception {
            String sql = "DELETE FROM AUDIT_LOG WHERE id = 1";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.DELETE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.AuditMapper.deleteUpperCase")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Case-insensitive matching should detect AUDIT_LOG");
            assertEquals(1, result.getViolations().size());
        }

        @Test
        @DisplayName("Empty readonlyTables list allows all writes")
        void testEmptyReadonlyTablesList_shouldPassAllWrites() throws Exception {
            // Configure empty readonly tables list
            config.setReadonlyTables(Collections.emptyList());
            checker = new ReadOnlyTableChecker(config);

            String sql = "DELETE FROM audit_log WHERE id = 1";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.DELETE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.AuditMapper.delete")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "Empty readonlyTables should allow all writes");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("Disabled checker should skip validation")
        void testDisabledChecker_shouldSkip() throws Exception {
            ReadOnlyTableConfig disabledConfig = new ReadOnlyTableConfig();
            disabledConfig.setEnabled(false);
            disabledConfig.setReadonlyTables(Arrays.asList("audit_log"));
            ReadOnlyTableChecker disabledChecker = new ReadOnlyTableChecker(disabledConfig);

            String sql = "DELETE FROM audit_log WHERE id = 1";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.DELETE)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.AuditMapper.delete")
                    .build();
            ValidationResult result = ValidationResult.pass();

            disabledChecker.check(context, result);

            assertTrue(result.isPassed(), "Disabled checker should skip validation");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("Wildcard at end only - history_* should not match historydata")
        void testWildcardAtEndOnly_shouldNotMatchWithoutUnderscore() throws Exception {
            // history_* should match history_users but NOT historydata
            String sql = "INSERT INTO historydata (data) VALUES ('test')";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.INSERT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.HistoryMapper.insertData")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "historydata should not match history_* pattern");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("Mixed case wildcard pattern - HISTORY_* should match history_users")
        void testMixedCaseWildcardPattern_shouldMatch() throws Exception {
            // Configure with uppercase pattern
            config.setReadonlyTables(Arrays.asList("HISTORY_*"));
            checker = new ReadOnlyTableChecker(config);

            String sql = "INSERT INTO history_users (user_id) VALUES (1)";
            Statement stmt = parser.parse(sql);
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statement(stmt)
                    .type(SqlCommandType.INSERT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.HistoryMapper.insert")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Case-insensitive wildcard should match");
            assertEquals(1, result.getViolations().size());
        }
    }
}
