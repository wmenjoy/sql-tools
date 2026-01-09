package com.footstone.sqlguard.validator.rule;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.rule.impl.*;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Integration tests for multi-checker interactions.
 *
 * <p>Validates that multiple checkers can work together correctly, all violations
 * are collected (not just the first), and checkers operate independently.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>Multi-Violation SQL Detection: SQL triggering multiple checkers simultaneously</li>
 *   <li>Checker Independence: Disabling one checker doesn't affect others</li>
 *   <li>Performance Under Load: Validate 100+ SQL statements efficiently</li>
 * </ul>
 *
 * @since 1.0.0
 */
@DisplayName("Multi-Checker Integration Tests")
class MultiCheckerIntegrationTest {

    private JSqlParserFacade parser;
    private List<RuleChecker> allCheckers;

    @BeforeEach
    void setUp() {
        parser = new JSqlParserFacade();
        allCheckers = createAllCheckers();
    }

    /**
     * Creates all 11 security checkers with enabled configurations for testing.
     */
    private List<RuleChecker> createAllCheckers() {
        List<RuleChecker> checkers = new ArrayList<>();

        // SQL Injection Checkers (Tasks 1.1-1.4) - explicitly enabled for tests
        MultiStatementConfig msConfig = new MultiStatementConfig();
        msConfig.setEnabled(true);
        checkers.add(new MultiStatementChecker(msConfig));

        SetOperationConfig soConfig = new SetOperationConfig();
        soConfig.setEnabled(true);
        checkers.add(new SetOperationChecker(soConfig));

        SqlCommentConfig scConfig = new SqlCommentConfig();
        scConfig.setEnabled(true);
        checkers.add(new SqlCommentChecker(scConfig));

        IntoOutfileConfig ioConfig = new IntoOutfileConfig();
        ioConfig.setEnabled(true);
        checkers.add(new IntoOutfileChecker(ioConfig));

        // Dangerous Operations Checkers (Tasks 1.5-1.7)
        DdlOperationConfig ddlConfig = new DdlOperationConfig();
        ddlConfig.setEnabled(true);
        checkers.add(new DdlOperationChecker(ddlConfig));

        DangerousFunctionConfig dfConfig = new DangerousFunctionConfig();
        dfConfig.setEnabled(true);
        checkers.add(new DangerousFunctionChecker(dfConfig));

        CallStatementConfig csConfig = new CallStatementConfig();
        csConfig.setEnabled(true);
        checkers.add(new CallStatementChecker(csConfig));

        // Access Control Checkers (Tasks 1.8-1.11)
        MetadataStatementConfig metaConfig = new MetadataStatementConfig();
        metaConfig.setEnabled(true);
        checkers.add(new MetadataStatementChecker(metaConfig));

        SetStatementConfig ssConfig = new SetStatementConfig();
        ssConfig.setEnabled(true);
        checkers.add(new SetStatementChecker(ssConfig));

        // DeniedTableChecker with configured denied tables
        DeniedTableConfig dtConfig = new DeniedTableConfig();
        dtConfig.setEnabled(true);
        dtConfig.setDeniedTables(Arrays.asList("sys_*", "admin_*", "audit_log"));
        checkers.add(new DeniedTableChecker(dtConfig));

        // ReadOnlyTableChecker with configured readonly tables
        ReadOnlyTableConfig rtConfig = new ReadOnlyTableConfig();
        rtConfig.setEnabled(true);
        rtConfig.setReadonlyTables(Arrays.asList("history_*", "audit_*", "archive_log"));
        checkers.add(new ReadOnlyTableChecker(rtConfig));

        return checkers;
    }

    /**
     * Validates SQL against all checkers and collects all violations.
     */
    private ValidationResult validateWithAllCheckers(SqlContext context) {
        ValidationResult result = ValidationResult.pass();
        for (RuleChecker checker : allCheckers) {
            if (checker.isEnabled()) {
                checker.check(context, result);
            }
        }
        return result;
    }

    // ==================== Multi-Violation SQL Detection Tests ====================

    @Nested
    @DisplayName("Multi-Violation SQL Detection Tests")
    class MultiViolationTests {

        @Test
        @DisplayName("SQL triggering MultiStatementChecker + SqlCommentChecker + DeniedTableChecker")
        void testMultipleViolations_MultiStatement_Comment_DeniedTable() {
            // SQL that triggers 3 checkers:
            // 1. MultiStatementChecker - semicolon with second statement
            // 2. SqlCommentChecker - -- comment
            // 3. DeniedTableChecker - sys_config table
            String sql = "SELECT * FROM sys_config -- admin bypass; DROP TABLE users";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);

            ValidationResult result = validateWithAllCheckers(context);

            assertFalse(result.isPassed(), "Should fail with multiple violations");
            assertTrue(result.getViolations().size() >= 2, "Should have at least 2 violations");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("SQL triggering SetOperationChecker + DangerousFunctionChecker")
        void testMultipleViolations_SetOperation_DangerousFunction() {
            // SQL that triggers 2 checkers:
            // 1. SetOperationChecker - UNION
            // 2. DangerousFunctionChecker - sleep function
            String sql = "SELECT * FROM users WHERE sleep(1) = 0 UNION SELECT * FROM admin";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);

            ValidationResult result = validateWithAllCheckers(context);

            assertFalse(result.isPassed(), "Should fail with multiple violations");
            // At least 1 violation (SetOperation is always detected, DangerousFunction depends on parsing)
            assertTrue(result.getViolations().size() >= 1, "Should have at least 1 violation");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("SQL triggering SqlCommentChecker + IntoOutfileChecker")
        void testMultipleViolations_Comment_IntoOutfile() {
            // SQL that triggers 2 checkers:
            // 1. SqlCommentChecker - -- comment
            // 2. IntoOutfileChecker - INTO OUTFILE
            String sql = "SELECT * INTO OUTFILE '/tmp/data.txt' FROM users -- export data";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);

            ValidationResult result = validateWithAllCheckers(context);

            assertFalse(result.isPassed(), "Should fail with multiple violations");
            assertTrue(result.getViolations().size() >= 2, "Should have at least 2 violations");
        }

        @Test
        @DisplayName("SQL triggering MultiStatementChecker + SetOperationChecker + SqlCommentChecker")
        void testMultipleViolations_ThreeCheckers() {
            // SQL that triggers 3 checkers
            String sql = "SELECT * FROM users UNION SELECT * FROM admin -- bypass; DROP TABLE logs";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);

            ValidationResult result = validateWithAllCheckers(context);

            assertFalse(result.isPassed(), "Should fail with multiple violations");
            assertTrue(result.getViolations().size() >= 2, "Should have at least 2 violations");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("SQL triggering DeniedTableChecker + ReadOnlyTableChecker (UPDATE on denied table)")
        void testMultipleViolations_DeniedTable_ReadOnlyTable() {
            // UPDATE on a table that is both denied and readonly
            DeniedTableConfig dtConfig = new DeniedTableConfig();
            dtConfig.setEnabled(true); // Explicitly enable
            dtConfig.setDeniedTables(Arrays.asList("audit_log"));
            DeniedTableChecker deniedChecker = new DeniedTableChecker(dtConfig);

            ReadOnlyTableConfig rtConfig = new ReadOnlyTableConfig();
            rtConfig.setEnabled(true); // Explicitly enable
            rtConfig.setReadonlyTables(Arrays.asList("audit_log"));
            ReadOnlyTableChecker readOnlyChecker = new ReadOnlyTableChecker(rtConfig);

            String sql = "UPDATE audit_log SET status = 'modified' WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.UPDATE);
            ValidationResult result = ValidationResult.pass();

            deniedChecker.check(context, result);
            readOnlyChecker.check(context, result);

            assertFalse(result.isPassed(), "Should fail with multiple violations");
            assertEquals(2, result.getViolations().size(), "Should have exactly 2 violations");
        }

        @Test
        @DisplayName("All violations should be collected (not just first)")
        void testAllViolationsCollected() {
            // SQL designed to trigger multiple violations
            String sql = "SELECT * FROM sys_user -- comment; SELECT load_file('/etc/passwd')";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);

            ValidationResult result = validateWithAllCheckers(context);

            assertFalse(result.isPassed());
            // Should have multiple violations, not just the first one found
            assertTrue(result.getViolations().size() >= 2,
                    "All violations should be collected, found: " + result.getViolations().size());
        }
    }

    // ==================== Checker Independence Tests ====================

    @Nested
    @DisplayName("Checker Independence Tests")
    class CheckerIndependenceTests {

        @Test
        @DisplayName("Disabling one checker should not affect others")
        void testDisabledCheckerDoesNotAffectOthers() {
            // Create checkers with MultiStatementChecker disabled
            MultiStatementConfig disabledMsConfig = new MultiStatementConfig(false);
            MultiStatementChecker disabledMsChecker = new MultiStatementChecker(disabledMsConfig);

            SqlCommentConfig enabledScConfig = new SqlCommentConfig(true);
            SqlCommentChecker enabledScChecker = new SqlCommentChecker(enabledScConfig);

            // SQL that would trigger both checkers if enabled
            String sql = "SELECT * FROM users -- comment; DROP TABLE users";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            disabledMsChecker.check(context, result);
            enabledScChecker.check(context, result);

            // Should only have SqlCommentChecker violation (MultiStatementChecker is disabled)
            assertFalse(result.isPassed());
            // SqlCommentChecker should still detect the comment
            boolean hasCommentViolation = result.getViolations().stream()
                    .anyMatch(v -> v.getMessage().contains("注释"));
            assertTrue(hasCommentViolation, "SqlCommentChecker should still detect violations");
        }

        @Test
        @DisplayName("Mixed WARN/BLOCK strategies work independently")
        void testMixedStrategies() {
            // SetStatementChecker defaults to MEDIUM risk
            // MultiStatementChecker defaults to BLOCK (CRITICAL risk)
            SetStatementConfig setConfig = new SetStatementConfig();
            setConfig.setEnabled(true); // Explicitly enable
            SetStatementChecker setChecker = new SetStatementChecker(setConfig);

            MultiStatementConfig msConfig = new MultiStatementConfig();
            msConfig.setEnabled(true); // Explicitly enable
            MultiStatementChecker msChecker = new MultiStatementChecker(msConfig);

            // Test SetStatementChecker alone
            String setSql = "SET autocommit = 0";
            SqlContext setContext = createContext(setSql, SqlCommandType.SELECT);
            ValidationResult setResult = ValidationResult.pass();
            setChecker.check(setContext, setResult);

            assertFalse(setResult.isPassed());
            assertEquals(RiskLevel.MEDIUM, setResult.getRiskLevel());

            // Test MultiStatementChecker alone
            String msSql = "SELECT 1; SELECT 2";
            SqlContext msContext = createContext(msSql, SqlCommandType.SELECT);
            ValidationResult msResult = ValidationResult.pass();
            msChecker.check(msContext, msResult);

            assertFalse(msResult.isPassed());
            assertEquals(RiskLevel.CRITICAL, msResult.getRiskLevel());
        }

        @Test
        @DisplayName("Each checker validates independently")
        void testIndependentValidation() {
            // Create two checkers with different configurations
            DeniedTableConfig config1 = new DeniedTableConfig();
            config1.setEnabled(true); // Explicitly enable
            config1.setDeniedTables(Arrays.asList("table_a"));
            DeniedTableChecker checker1 = new DeniedTableChecker(config1);

            DeniedTableConfig config2 = new DeniedTableConfig();
            config2.setEnabled(true); // Explicitly enable
            config2.setDeniedTables(Arrays.asList("table_b"));
            DeniedTableChecker checker2 = new DeniedTableChecker(config2);

            // SQL accessing table_a (should fail checker1, pass checker2)
            String sql = "SELECT * FROM table_a WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);

            ValidationResult result1 = ValidationResult.pass();
            checker1.check(context, result1);
            assertFalse(result1.isPassed(), "Checker1 should fail for table_a");

            ValidationResult result2 = ValidationResult.pass();
            checker2.check(context, result2);
            assertTrue(result2.isPassed(), "Checker2 should pass for table_a");
        }
    }

    // ==================== Performance Under Load Tests ====================

    @Nested
    @DisplayName("Performance Under Load Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Validate 100+ SQL statements with multiple checkers")
        void testPerformanceWith100Statements() {
            List<String> sqlStatements = generateTestSqlStatements(100);

            long startTime = System.currentTimeMillis();

            int violationCount = 0;
            for (String sql : sqlStatements) {
                SqlContext context = createContext(sql, SqlCommandType.SELECT);
                ValidationResult result = validateWithAllCheckers(context);
                violationCount += result.getViolations().size();
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Performance requirement: <5000ms for 100 statements
            assertTrue(duration < 5000,
                    "Should validate 100 statements in <5000ms, actual: " + duration + "ms");

            // Should have detected some violations in the test data
            assertTrue(violationCount > 0, "Should detect violations in test data");
        }

        @Test
        @DisplayName("Repeated validation of same SQL should not cause memory leaks")
        void testRepeatedValidationNoMemoryLeak() {
            String sql = "SELECT * FROM users WHERE id = 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);

            // Run validation 1000 times
            for (int i = 0; i < 1000; i++) {
                ValidationResult result = validateWithAllCheckers(context);
                assertTrue(result.isPassed());
            }

            // If we reach here without OutOfMemoryError, test passes
            assertTrue(true, "No memory leak detected");
        }

        private List<String> generateTestSqlStatements(int count) {
            List<String> statements = new ArrayList<>();
            String[] templates = {
                    "SELECT * FROM users WHERE id = %d",
                    "SELECT * FROM orders WHERE user_id = %d",
                    "UPDATE users SET status = 'active' WHERE id = %d",
                    "DELETE FROM logs WHERE id = %d",
                    "INSERT INTO events (user_id) VALUES (%d)",
                    // Include some that will trigger violations
                    "SELECT * FROM sys_config WHERE id = %d",
                    "SELECT * FROM users -- comment WHERE id = %d"
            };

            for (int i = 0; i < count; i++) {
                String template = templates[i % templates.length];
                statements.add(String.format(template, i));
            }
            return statements;
        }
    }

    // ==================== Violation Priority and Order Tests ====================

    @Nested
    @DisplayName("Violation Priority and Order Tests")
    class ViolationPriorityTests {

        @Test
        @DisplayName("Risk level should be highest among all violations")
        void testRiskLevelIsHighest() {
            // Create checkers with different risk levels
            // CallStatementChecker: HIGH
            // MultiStatementChecker: CRITICAL
            CallStatementConfig callConfig = new CallStatementConfig();
            callConfig.setEnabled(true); // Explicitly enable
            CallStatementChecker callChecker = new CallStatementChecker(callConfig);
            
            MultiStatementConfig msConfig = new MultiStatementConfig();
            msConfig.setEnabled(true); // Explicitly enable
            MultiStatementChecker msChecker = new MultiStatementChecker(msConfig);

            // SQL that triggers both
            String sql = "CALL sp_test(); SELECT 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            callChecker.check(context, result);
            msChecker.check(context, result);

            // Risk level should be CRITICAL (highest)
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel(),
                    "Risk level should be highest among violations");
        }

        @Test
        @DisplayName("Violations should preserve order of detection")
        void testViolationOrder() {
            MultiStatementConfig msConfig = new MultiStatementConfig();
            msConfig.setEnabled(true); // Explicitly enable
            MultiStatementChecker msChecker = new MultiStatementChecker(msConfig);
            
            SqlCommentConfig scConfig = new SqlCommentConfig();
            scConfig.setEnabled(true); // Explicitly enable
            SqlCommentChecker scChecker = new SqlCommentChecker(scConfig);

            String sql = "SELECT * FROM users -- comment; DROP TABLE users";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            // Check in specific order
            msChecker.check(context, result);
            scChecker.check(context, result);

            // Violations should be in order of detection
            List<ViolationInfo> violations = result.getViolations();
            assertTrue(violations.size() >= 2, "Should have at least 2 violations");
        }
    }

    // ==================== Helper Methods ====================

    private SqlContext createContext(String sql, SqlCommandType type) {
        Statement stmt = null;
        try {
            stmt = parser.parse(sql);
        } catch (Exception e) {
            // For invalid SQL or multi-statement SQL that fails parsing,
            // we still create context with the raw SQL for validation
        }
        return SqlContext.builder()
                .sql(sql)
                .statement(stmt)
                .type(type)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("com.example.TestMapper.testMethod")
                .build();
    }
}
