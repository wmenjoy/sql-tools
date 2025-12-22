package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for DummyConditionChecker migration to visitor pattern.
 *
 * <p>This test class validates the Phase 12 migration of DummyConditionChecker from
 * the old check() implementation to the new visitSelect()/visitUpdate()/visitDelete() methods.</p>
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>Test 1: SELECT with dummy condition (1=1) - should add HIGH violation</li>
 *   <li>Test 2: UPDATE with dummy condition - should add HIGH violation</li>
 *   <li>Test 3: DELETE with dummy condition - should add HIGH violation</li>
 *   <li>Test 4: Valid WHERE condition - should pass</li>
 *   <li>Test 5: Disabled configuration - should skip</li>
 *   <li>Test 6: Custom patterns - should detect</li>
 *   <li>Test 7: No WHERE clause - should pass (nothing to check)</li>
 * </ul>
 *
 * @see DummyConditionChecker
 * @since 1.1.0
 */
@DisplayName("DummyConditionChecker Migration Tests")
public class DummyConditionCheckerMigrationTest {

    private DummyConditionConfig config;
    private DummyConditionChecker checker;

    @BeforeEach
    public void setUp() {
        config = new DummyConditionConfig();
        config.setEnabled(true);
        checker = new DummyConditionChecker(config);
    }

    @Nested
    @DisplayName("1. SELECT Statement Tests")
    class SelectStatementTests {

        /**
         * Test 1: SELECT with dummy condition (1=1) - should add HIGH violation
         */
        @Test
        @DisplayName("visitSelect() - with 1=1 dummy condition - should add HIGH violation")
        public void testVisitSelect_dummyCondition_violates() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE 1=1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.selectDummy")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("无效条件"));
        }

        /**
         * Test: SELECT with 'true' condition - should add HIGH violation
         */
        @Test
        @DisplayName("visitSelect() - with TRUE condition - should add HIGH violation")
        public void testVisitSelect_trueCondition_violates() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE true";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.selectTrue")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getRiskLevel());
        }

        /**
         * Test: SELECT with valid WHERE - should pass
         */
        @Test
        @DisplayName("visitSelect() - with valid WHERE - should pass")
        public void testVisitSelect_validWhere_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE id = 1 AND status = 'active'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.selectValid")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed(), "Should pass validation");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("2. UPDATE Statement Tests")
    class UpdateStatementTests {

        /**
         * Test 2: UPDATE with dummy condition - should add HIGH violation
         */
        @Test
        @DisplayName("visitUpdate() - with 1=1 dummy condition - should add HIGH violation")
        public void testVisitUpdate_dummyCondition_violates() throws JSQLParserException {
            String sql = "UPDATE users SET name = 'foo' WHERE 1 = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.updateDummy")
                    .type(SqlCommandType.UPDATE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getRiskLevel());
        }

        /**
         * Test: UPDATE with valid WHERE - should pass
         */
        @Test
        @DisplayName("visitUpdate() - with valid WHERE - should pass")
        public void testVisitUpdate_validWhere_passes() throws JSQLParserException {
            String sql = "UPDATE users SET name = 'foo' WHERE id = 100";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.updateValid")
                    .type(SqlCommandType.UPDATE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed(), "Should pass validation");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("3. DELETE Statement Tests")
    class DeleteStatementTests {

        /**
         * Test 3: DELETE with dummy condition - should add HIGH violation
         */
        @Test
        @DisplayName("visitDelete() - with 'a'='a' dummy condition - should add HIGH violation")
        public void testVisitDelete_dummyCondition_violates() throws JSQLParserException {
            String sql = "DELETE FROM users WHERE 'a'='a'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.deleteDummy")
                    .type(SqlCommandType.DELETE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getRiskLevel());
        }

        /**
         * Test: DELETE with valid WHERE - should pass
         */
        @Test
        @DisplayName("visitDelete() - with valid WHERE - should pass")
        public void testVisitDelete_validWhere_passes() throws JSQLParserException {
            String sql = "DELETE FROM users WHERE id = 999";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.deleteValid")
                    .type(SqlCommandType.DELETE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed(), "Should pass validation");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("4. Configuration Tests")
    class ConfigurationTests {

        /**
         * Test 5: Disabled configuration - should skip
         */
        @Test
        @DisplayName("isEnabled=false - should skip validation")
        public void testDisabled_skipsValidation() throws JSQLParserException {
            config.setEnabled(false);
            DummyConditionChecker disabledChecker = new DummyConditionChecker(config);

            String sql = "SELECT * FROM users WHERE 1=1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.disabled")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            disabledChecker.check(context, result);

            assertTrue(result.isPassed(), "Disabled checker should skip validation");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 6: Custom patterns - should detect
         */
        @Test
        @DisplayName("custom patterns - should detect '0=0'")
        public void testCustomPatterns_detected() throws JSQLParserException {
            config.setCustomPatterns(Arrays.asList("0=0", "2=2"));
            DummyConditionChecker customChecker = new DummyConditionChecker(config);

            String sql = "SELECT * FROM users WHERE 0=0";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.customPattern")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            customChecker.check(context, result);

            assertFalse(result.isPassed(), "Should detect custom pattern");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getRiskLevel());
        }
    }

    @Nested
    @DisplayName("5. No WHERE Clause Tests")
    class NoWhereClauseTests {

        /**
         * Test 7: No WHERE clause - should pass (nothing to check)
         */
        @Test
        @DisplayName("no WHERE clause - should pass (nothing to check)")
        public void testNoWhere_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.noWhere")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // No violation - DummyConditionChecker doesn't check for missing WHERE
            // That's NoWhereClauseChecker's job
            assertTrue(result.isPassed(), "Should pass - no WHERE to check");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("6. Constructor Migration Tests")
    class ConstructorMigrationTests {

        /**
         * Test: Constructor - super(config) - should work
         */
        @Test
        @DisplayName("constructor - super(config) - should work")
        public void testConstructor_superConfig_works() {
            // Verify constructor calls super(config) correctly
            DummyConditionChecker checker = new DummyConditionChecker(config);
            assertNotNull(checker);
            assertTrue(checker.isEnabled());

            // Test with disabled config
            DummyConditionConfig disabledConfig = new DummyConditionConfig(false);
            DummyConditionChecker disabledChecker = new DummyConditionChecker(disabledConfig);
            assertFalse(disabledChecker.isEnabled());
        }
    }
}

