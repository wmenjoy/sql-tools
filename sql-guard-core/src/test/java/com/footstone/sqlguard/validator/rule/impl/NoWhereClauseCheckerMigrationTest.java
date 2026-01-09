package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.ValidationResult;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for NoWhereClauseChecker migration to visitor pattern.
 *
 * <p>This test class validates the Phase 12 migration of NoWhereClauseChecker from
 * the old check() implementation to the new visitUpdate()/visitDelete() methods.</p>
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>Test 1-3: UPDATE statement tests (no WHERE, with WHERE, dummy WHERE)</li>
 *   <li>Test 4-6: DELETE statement tests (no WHERE, with WHERE, complex WHERE)</li>
 *   <li>Test 7-8: INSERT and SELECT skip tests</li>
 *   <li>Test 9: Disabled configuration test</li>
 *   <li>Test 10: statement field migration test</li>
 * </ul>
 *
 * @see NoWhereClauseChecker
 * @since 1.1.0
 */
@DisplayName("NoWhereClauseChecker Migration Tests")
public class NoWhereClauseCheckerMigrationTest {

    private NoWhereClauseConfig config;
    private NoWhereClauseChecker checker;

    @BeforeEach
    public void setUp() {
        config = new NoWhereClauseConfig(true); // Explicitly enable for tests
        config.setEnabled(true);
        checker = new NoWhereClauseChecker(config);
    }

    @Nested
    @DisplayName("1. UPDATE Statement Tests")
    class UpdateStatementTests {

        /**
         * Test 1: UPDATE without WHERE should violate
         */
        @Test
        @DisplayName("visitUpdate() - no WHERE clause - should add CRITICAL violation")
        public void testVisitUpdate_noWhere_violates() throws JSQLParserException {
            // Parse UPDATE without WHERE
            String sql = "UPDATE users SET name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.updateNoWhere")
                    .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)  // Use statement field (not parsedSql)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("UPDATE"));
            assertTrue(result.getViolations().get(0).getMessage().contains("WHERE"));
        }

        /**
         * Test 2: UPDATE with WHERE should pass
         */
        @Test
        @DisplayName("visitUpdate() - with WHERE clause - should pass")
        public void testVisitUpdate_withWhere_passes() throws JSQLParserException {
            // Parse UPDATE with WHERE
            String sql = "UPDATE users SET name = 'foo' WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.updateWithWhere")
                    .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation
            assertTrue(result.isPassed(), "Should pass validation");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 3: UPDATE with dummy condition should pass (handled by DummyConditionChecker)
         */
        @Test
        @DisplayName("visitUpdate() - with dummy WHERE (1=1) - should pass")
        public void testVisitUpdate_dummyWhere_passes() throws JSQLParserException {
            // Parse UPDATE with WHERE 1=1 (dummy condition)
            String sql = "UPDATE users SET name = 'foo' WHERE 1=1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.updateDummyWhere")
                    .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (dummy condition detection is handled by DummyConditionChecker)
            assertTrue(result.isPassed(), "Should pass - dummy condition handled elsewhere");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("2. DELETE Statement Tests")
    class DeleteStatementTests {

        /**
         * Test 4: DELETE without WHERE should violate
         */
        @Test
        @DisplayName("visitDelete() - no WHERE clause - should add CRITICAL violation")
        public void testVisitDelete_noWhere_violates() throws JSQLParserException {
            // Parse DELETE without WHERE
            String sql = "DELETE FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.deleteNoWhere")
                    .type(SqlCommandType.DELETE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("DELETE"));
            assertTrue(result.getViolations().get(0).getMessage().contains("WHERE"));
        }

        /**
         * Test 5: DELETE with WHERE should pass
         */
        @Test
        @DisplayName("visitDelete() - with WHERE clause - should pass")
        public void testVisitDelete_withWhere_passes() throws JSQLParserException {
            // Parse DELETE with WHERE
            String sql = "DELETE FROM users WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.deleteWithWhere")
                    .type(SqlCommandType.DELETE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation
            assertTrue(result.isPassed(), "Should pass validation");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 6: DELETE with complex WHERE should pass
         */
        @Test
        @DisplayName("visitDelete() - with complex WHERE clause - should pass")
        public void testVisitDelete_complexWhere_passes() throws JSQLParserException {
            // Parse DELETE with complex WHERE
            String sql = "DELETE FROM users WHERE id > 100 AND status = 'inactive' OR created_time < '2020-01-01'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.deleteComplexWhere")
                    .type(SqlCommandType.DELETE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation
            assertTrue(result.isPassed(), "Should pass validation");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("3. Other Statement Tests")
    class OtherStatementTests {

        /**
         * Test 7: INSERT should be skipped (default visitInsert is no-op)
         */
        @Test
        @DisplayName("visitInsert() - INSERT statement - should be skipped")
        public void testVisitInsert_skipped() throws JSQLParserException {
            // Parse INSERT
            String sql = "INSERT INTO users (id, name) VALUES (1, 'foo')";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.insert")
                    .type(SqlCommandType.INSERT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (INSERT skipped)
            assertTrue(result.isPassed(), "INSERT should be skipped");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 8: SELECT should be skipped (default visitSelect is no-op)
         */
        @Test
        @DisplayName("visitSelect() - SELECT without WHERE - should be skipped")
        public void testVisitSelect_skipped() throws JSQLParserException {
            // Parse SELECT without WHERE
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.select")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (SELECT skipped by NoWhereClauseChecker)
            // SELECT is handled by NoPaginationChecker with risk stratification
            assertTrue(result.isPassed(), "SELECT should be skipped");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("4. Configuration Tests")
    class ConfigurationTests {

        /**
         * Test 9: Disabled checker should skip validation
         */
        @Test
        @DisplayName("isEnabled=false - should skip validation")
        public void testDisabled_skipsValidation() throws JSQLParserException {
            // Disable checker
            config.setEnabled(false);
            NoWhereClauseChecker disabledChecker = new NoWhereClauseChecker(config);

            // Parse UPDATE without WHERE
            String sql = "UPDATE users SET name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.disabled")
                    .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            disabledChecker.check(context, result);

            // Verify no violation (checker disabled)
            assertTrue(result.isPassed(), "Disabled checker should skip validation");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("5. Statement Field Migration Tests")
    class StatementFieldMigrationTests {

        /**
         * Test 10: Verify statement field is used (not parsedSql)
         */
        @Test
        @DisplayName("statement field - should work correctly")
        public void testStatementField_works() throws JSQLParserException {
            // Parse UPDATE without WHERE
            String sql = "UPDATE users SET name = 'bar'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            // Build context using statement field (NOT parsedSql)
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.statementField")
                    .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)  // NEW field
                    .build();

            // Verify statement field is accessible
            assertNotNull(context.getStatement(), "statement field should be accessible");
            assertEquals(stmt, context.getStatement(), "statement should match");

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added (UPDATE without WHERE)
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
        }
    }
}

