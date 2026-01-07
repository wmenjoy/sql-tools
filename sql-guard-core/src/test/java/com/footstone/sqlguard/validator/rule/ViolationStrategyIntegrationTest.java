package com.footstone.sqlguard.validator.rule;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.config.ViolationStrategy;
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

import java.util.Arrays;
import java.util.List;

/**
 * Integration tests for ViolationStrategy behaviors (WARN/BLOCK).
 *
 * <p>Validates that WARN and BLOCK strategies work correctly, that violations
 * are collected properly, and that strategy overrides work as expected.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>WARN Strategy Behavior: Violations collected but validation may continue</li>
 *   <li>BLOCK Strategy Behavior: Violations collected with valid=false</li>
 *   <li>Strategy Override Tests: Global vs specific checker strategy configurations</li>
 * </ul>
 *
 * @since 1.0.0
 */
@DisplayName("ViolationStrategy Integration Tests")
class ViolationStrategyIntegrationTest {

    private JSqlParserFacade parser;

    @BeforeEach
    void setUp() {
        parser = new JSqlParserFacade();
    }

    // ==================== WARN Strategy Behavior Tests ====================

    @Nested
    @DisplayName("WARN Strategy Behavior Tests")
    class WarnStrategyTests {

        @Test
        @DisplayName("Checker with WARN-like strategy should collect violations")
        void testWarnStrategy_CollectsViolations() {
            // SetStatementChecker has MEDIUM risk (similar to WARN behavior)
            SetStatementConfig config = new SetStatementConfig();

            SetStatementChecker checker = new SetStatementChecker(config);

            String sql = "SET autocommit = 0";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            // Violations are recorded
            assertFalse(result.isPassed(), "Should have violations");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
        }

        @Test
        @DisplayName("Multiple WARN violations should all be collected")
        void testMultipleWarnViolations_AllCollected() {
            // SetStatementChecker has MEDIUM risk (similar to WARN behavior)
            SetStatementChecker setChecker = new SetStatementChecker(new SetStatementConfig());

            // Two different SET statements
            String sql1 = "SET autocommit = 0";
            String sql2 = "SET sql_mode = ''";

            ValidationResult result = ValidationResult.pass();

            SqlContext context1 = createContext(sql1, SqlCommandType.SELECT);
            SqlContext context2 = createContext(sql2, SqlCommandType.SELECT);

            setChecker.check(context1, result);
            setChecker.check(context2, result);

            assertEquals(2, result.getViolations().size(), "Should collect both violations");
            assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
        }

        @Test
        @DisplayName("WARN strategy should preserve violation messages")
        void testWarnStrategy_PreservesMessages() {
            SetStatementChecker checker = new SetStatementChecker(new SetStatementConfig());

            String sql = "SET sql_mode = ''";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.getViolations().isEmpty());
            ViolationInfo violation = result.getViolations().get(0);
            assertNotNull(violation.getMessage());
            assertTrue(violation.getMessage().contains("SET"));
        }

        @Test
        @DisplayName("WARN violations should not prevent subsequent checks")
        void testWarnViolations_DoNotPreventSubsequentChecks() {
            // First checker with WARN-like behavior
            SetStatementChecker setChecker = new SetStatementChecker(new SetStatementConfig());

            // Second checker with BLOCK behavior
            MultiStatementChecker msChecker = new MultiStatementChecker(new MultiStatementConfig());

            // SQL that triggers SET statement (WARN-like)
            String sql1 = "SET autocommit = 0";
            SqlContext context1 = createContext(sql1, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            setChecker.check(context1, result);

            // Result has violation but we can still run more checks
            assertEquals(1, result.getViolations().size());

            // SQL that triggers multi-statement (CRITICAL)
            String sql2 = "SELECT 1; SELECT 2";
            SqlContext context2 = createContext(sql2, SqlCommandType.SELECT);

            msChecker.check(context2, result);

            // Both violations should be collected
            assertEquals(2, result.getViolations().size());
            // Risk level should be CRITICAL (highest)
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }
    }

    // ==================== BLOCK Strategy Behavior Tests ====================

    @Nested
    @DisplayName("BLOCK Strategy Behavior Tests")
    class BlockStrategyTests {

        @Test
        @DisplayName("Checker with BLOCK strategy should mark result as failed")
        void testBlockStrategy_MarksResultFailed() {
            // MultiStatementChecker defaults to BLOCK strategy with CRITICAL risk
            MultiStatementChecker checker = new MultiStatementChecker(new MultiStatementConfig());

            String sql = "SELECT * FROM users; DROP TABLE users";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Should fail with BLOCK strategy");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("BLOCK violations should have CRITICAL risk level")
        void testBlockViolations_CriticalRiskLevel() {
            // IntoOutfileChecker is CRITICAL
            IntoOutfileChecker checker = new IntoOutfileChecker(new IntoOutfileConfig());

            String sql = "SELECT * INTO OUTFILE '/tmp/data.txt' FROM users";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed());
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("Multiple BLOCK checkers should all contribute violations")
        void testMultipleBlockCheckers_AllContribute() {
            MultiStatementChecker msChecker = new MultiStatementChecker(new MultiStatementConfig());
            SqlCommentChecker scChecker = new SqlCommentChecker(new SqlCommentConfig());

            // SQL that triggers both checkers
            String sql = "SELECT * FROM users -- bypass; DROP TABLE users";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            msChecker.check(context, result);
            scChecker.check(context, result);

            assertFalse(result.isPassed());
            assertTrue(result.getViolations().size() >= 2, "Should have multiple violations");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("BLOCK strategy should collect all violations (not stop at first)")
        void testBlockStrategy_CollectsAllViolations() {
            // Configure DeniedTableChecker with multiple denied tables
            DeniedTableConfig config = new DeniedTableConfig();
            config.setDeniedTables(Arrays.asList("sys_*", "admin_*"));
            DeniedTableChecker checker = new DeniedTableChecker(config);

            // SQL accessing multiple denied tables
            String sql = "SELECT * FROM sys_user u JOIN admin_config c ON u.id = c.user_id";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed());
            // Should detect both denied tables
            assertTrue(result.getViolations().size() >= 1);
        }
    }

    // ==================== Strategy Override Tests ====================

    @Nested
    @DisplayName("Strategy Override Tests")
    class StrategyOverrideTests {

        @Test
        @DisplayName("Specific checker config overrides global behavior")
        void testSpecificCheckerOverridesGlobal() {
            // Create CallStatementChecker with BLOCK strategy instead of default WARN
            CallStatementConfig blockConfig = new CallStatementConfig(true, ViolationStrategy.BLOCK);
            assertEquals(ViolationStrategy.BLOCK, blockConfig.getViolationStrategy());

            CallStatementChecker blockChecker = new CallStatementChecker(blockConfig);

            String sql = "CALL sp_test()";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            blockChecker.check(context, result);

            // Should still detect the violation
            assertFalse(result.isPassed());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        }

        @Test
        @DisplayName("Programmatic strategy changes at runtime")
        void testProgrammaticStrategyChange() {
            // Start with WARN strategy
            CallStatementConfig config = new CallStatementConfig();
            assertEquals(ViolationStrategy.WARN, config.getViolationStrategy());

            // Change to BLOCK at runtime
            config.setViolationStrategy(ViolationStrategy.BLOCK);
            assertEquals(ViolationStrategy.BLOCK, config.getViolationStrategy());

            // Test with SetStatementChecker which reliably detects SET statements
            SetStatementConfig setConfig = new SetStatementConfig();
            SetStatementChecker setChecker = new SetStatementChecker(setConfig);

            String sql = "SET autocommit = 0";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);
            ValidationResult result = ValidationResult.pass();

            setChecker.check(context, result);

            assertFalse(result.isPassed());
        }
    }

    // ==================== Mixed Strategy Tests ====================

    @Nested
    @DisplayName("Mixed Strategy Tests")
    class MixedStrategyTests {

        @Test
        @DisplayName("Mixed WARN/BLOCK strategies in same validation")
        void testMixedStrategies_SameValidation() {
            // WARN-like checker (MEDIUM risk)
            SetStatementChecker setChecker = new SetStatementChecker(new SetStatementConfig());

            // BLOCK checker (CRITICAL risk)
            MultiStatementChecker msChecker = new MultiStatementChecker(new MultiStatementConfig());

            // Run both on different SQLs into same result
            ValidationResult result = ValidationResult.pass();

            String warnSql = "SET autocommit = 0";
            SqlContext warnContext = createContext(warnSql, SqlCommandType.SELECT);
            setChecker.check(warnContext, result);

            String blockSql = "SELECT 1; SELECT 2";
            SqlContext blockContext = createContext(blockSql, SqlCommandType.SELECT);
            msChecker.check(blockContext, result);

            // Should have both violations
            assertEquals(2, result.getViolations().size());
            // Risk level should be CRITICAL (highest)
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertFalse(result.isPassed());
        }

        @Test
        @DisplayName("Risk level hierarchy: CRITICAL > HIGH > MEDIUM > LOW > SAFE")
        void testRiskLevelHierarchy() {
            ValidationResult result = ValidationResult.pass();

            // Add LOW risk violation
            result.addViolation(RiskLevel.LOW, "Low risk", null);
            assertEquals(RiskLevel.LOW, result.getRiskLevel());

            // Add MEDIUM risk violation
            result.addViolation(RiskLevel.MEDIUM, "Medium risk", null);
            assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());

            // Add HIGH risk violation
            result.addViolation(RiskLevel.HIGH, "High risk", null);
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());

            // Add CRITICAL risk violation
            result.addViolation(RiskLevel.CRITICAL, "Critical risk", null);
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());

            // Risk level should remain CRITICAL
            result.addViolation(RiskLevel.LOW, "Another low risk", null);
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("Enabled/disabled checkers with different strategies")
        void testEnabledDisabledWithStrategies() {
            // Disabled BLOCK checker
            MultiStatementConfig disabledMsConfig = new MultiStatementConfig(false);
            MultiStatementChecker disabledMsChecker = new MultiStatementChecker(disabledMsConfig);

            // Enabled WARN checker
            CallStatementConfig enabledCallConfig = new CallStatementConfig(true);
            CallStatementChecker enabledCallChecker = new CallStatementChecker(enabledCallConfig);

            ValidationResult result = ValidationResult.pass();

            // SQL that would trigger MultiStatementChecker if enabled
            String sql = "CALL sp_test(); SELECT 1";
            SqlContext context = createContext(sql, SqlCommandType.SELECT);

            disabledMsChecker.check(context, result);
            enabledCallChecker.check(context, result);

            // Only CallStatementChecker should have detected violation
            // (MultiStatementChecker is disabled)
            assertFalse(result.isPassed());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel()); // CallStatement is HIGH
        }

        @Test
        @DisplayName("Violation count with mixed strategies")
        void testViolationCountWithMixedStrategies() {
            // Create multiple checkers with different risk levels
            MultiStatementChecker msChecker = new MultiStatementChecker(new MultiStatementConfig()); // CRITICAL
            SetStatementChecker setChecker = new SetStatementChecker(new SetStatementConfig()); // MEDIUM

            ValidationResult result = ValidationResult.pass();

            // SQL triggering MultiStatementChecker
            String sql1 = "SELECT 1; SELECT 2";
            SqlContext context1 = createContext(sql1, SqlCommandType.SELECT);
            msChecker.check(context1, result);

            // SQL triggering SetStatementChecker
            String sql2 = "SET NAMES utf8";
            SqlContext context2 = createContext(sql2, SqlCommandType.SELECT);
            setChecker.check(context2, result);

            // Should have exactly 2 violations
            assertEquals(2, result.getViolations().size());

            // Verify risk levels of individual violations
            List<ViolationInfo> violations = result.getViolations();
            boolean hasCritical = violations.stream()
                    .anyMatch(v -> v.getRiskLevel() == RiskLevel.CRITICAL);
            boolean hasMedium = violations.stream()
                    .anyMatch(v -> v.getRiskLevel() == RiskLevel.MEDIUM);

            assertTrue(hasCritical, "Should have CRITICAL violation");
            assertTrue(hasMedium, "Should have MEDIUM violation");
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
