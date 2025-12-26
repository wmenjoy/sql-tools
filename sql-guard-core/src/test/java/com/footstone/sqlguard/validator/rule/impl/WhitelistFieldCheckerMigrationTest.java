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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for WhitelistFieldChecker migration to visitor pattern.
 *
 * <p>This test class validates the Phase 12 migration of WhitelistFieldChecker from
 * the old check() implementation to the new visitSelect() method using JSqlParser API
 * directly.</p>
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>Test 1-2: Whitelist violation tests (non-whitelist fields)</li>
 *   <li>Test 3-5: Whitelist compliance tests (required fields present)</li>
 *   <li>Test 6-7: Configuration tests (unknown table handling)</li>
 *   <li>Test 8-9: Edge cases (no WHERE, UPDATE skipped)</li>
 *   <li>Test 10: statement field migration test</li>
 * </ul>
 *
 * @see WhitelistFieldChecker
 * @since 1.1.0
 */
@DisplayName("WhitelistFieldChecker Migration Tests")
public class WhitelistFieldCheckerMigrationTest {

    private WhitelistFieldsConfig config;
    private WhitelistFieldChecker checker;

    @BeforeEach
    public void setUp() {
        config = new WhitelistFieldsConfig();
        config.setEnabled(true);

        // Setup table-specific whitelist
        Map<String, List<String>> byTable = new HashMap<>();
        byTable.put("users", Arrays.asList("id", "user_id"));
        byTable.put("orders", Arrays.asList("order_id", "user_id"));
        config.setByTable(byTable);

        checker = new WhitelistFieldChecker(config);
    }

    @Nested
    @DisplayName("1. Whitelist Violation Tests")
    class WhitelistViolationTests {

        /**
         * Test 1: WHERE without required field should violate
         */
        @Test
        @DisplayName("visitSelect() - WHERE without required field - should add MEDIUM violation")
        public void testVisitSelect_nonWhitelistField_violates() throws JSQLParserException {
            // Parse SELECT with WHERE name='foo' (not in whitelist)
            String sql = "SELECT * FROM users WHERE name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.nonWhitelistField")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.MEDIUM, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("users"));
            assertTrue(result.getViolations().get(0).getMessage().contains("id"));
        }

        /**
         * Test 2: WHERE with multiple non-whitelist fields should violate
         */
        @Test
        @DisplayName("visitSelect() - WHERE multiple non-whitelist fields - should add MEDIUM violation")
        public void testVisitSelect_multipleNonWhitelistFields_violates() throws JSQLParserException {
            // Parse SELECT with WHERE name='foo' AND status='active'
            String sql = "SELECT * FROM users WHERE name = 'foo' AND status = 'active'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.multipleNonWhitelist")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("2. Whitelist Compliance Tests")
    class WhitelistComplianceTests {

        /**
         * Test 3: WHERE with required field should pass
         */
        @Test
        @DisplayName("visitSelect() - WHERE with required field - should pass")
        public void testVisitSelect_whitelistField_passes() throws JSQLParserException {
            // Parse SELECT with WHERE id=1
            String sql = "SELECT * FROM users WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.whitelistField")
                    .type(SqlCommandType.SELECT)
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
         * Test 4: WHERE with mixed fields (whitelist + non-whitelist) should pass
         */
        @Test
        @DisplayName("visitSelect() - WHERE mixed fields - should pass")
        public void testVisitSelect_mixedFields_passes() throws JSQLParserException {
            // Parse SELECT with WHERE id=1 AND name='foo'
            String sql = "SELECT * FROM users WHERE id = 1 AND name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.mixedFields")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (includes required field 'id')
            assertTrue(result.isPassed(), "Should pass - includes required field");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 5: WHERE with alternative required field should pass
         */
        @Test
        @DisplayName("visitSelect() - WHERE with alternative required field - should pass")
        public void testVisitSelect_alternativeWhitelistField_passes() throws JSQLParserException {
            // Parse SELECT with WHERE user_id=1 (alternative required field)
            String sql = "SELECT * FROM users WHERE user_id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.alternativeWhitelist")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (user_id is in whitelist)
            assertTrue(result.isPassed(), "Should pass - user_id in whitelist");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("3. Configuration Tests")
    class ConfigurationTests {

        /**
         * Test 6: Unknown table without enforceForUnknownTables should skip
         */
        @Test
        @DisplayName("visitSelect() - unknown table, enforceForUnknownTables=false - should skip")
        public void testVisitSelect_unknownTable_skipped() throws JSQLParserException {
            config.setEnforceForUnknownTables(false);
            WhitelistFieldChecker configuredChecker = new WhitelistFieldChecker(config);

            // Parse SELECT from unknown table
            String sql = "SELECT * FROM products WHERE name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.unknownTable")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            configuredChecker.check(context, result);

            // Verify no violation (unknown table skipped)
            assertTrue(result.isPassed(), "Unknown table should be skipped");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 7: Unknown table with enforceForUnknownTables should use global fields
         */
        @Test
        @DisplayName("visitSelect() - unknown table, enforceForUnknownTables=true - should use global fields")
        public void testVisitSelect_unknownTable_usesGlobalFields() throws JSQLParserException {
            config.setEnforceForUnknownTables(true);
            List<String> globalFields = new ArrayList<>();
            globalFields.add("id");
            config.setFields(globalFields);

            WhitelistFieldChecker configuredChecker = new WhitelistFieldChecker(config);

            // Parse SELECT from unknown table without 'id'
            String sql = "SELECT * FROM products WHERE name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.unknownTableGlobal")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            configuredChecker.check(context, result);

            // Verify violation (global whitelist enforced)
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("4. Edge Cases Tests")
    class EdgeCasesTests {

        /**
         * Test 8: No WHERE clause should be skipped
         */
        @Test
        @DisplayName("visitSelect() - no WHERE clause - should be skipped")
        public void testVisitSelect_noWhere_skipped() throws JSQLParserException {
            // Parse SELECT without WHERE
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.noWhere")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (no WHERE clause to check)
            assertTrue(result.isPassed(), "Should skip - no WHERE clause");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 9: UPDATE without required field should violate
         * (WhitelistFieldChecker validates UPDATE/DELETE in addition to SELECT)
         */
        @Test
        @DisplayName("visitUpdate() - UPDATE without required field - should add MEDIUM violation")
        public void testVisitUpdate_noRequiredField_violates() throws JSQLParserException {
            // Parse UPDATE without whitelist field
            String sql = "UPDATE users SET name = 'foo' WHERE status = 'active'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.update")
                    .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added (UPDATE without required field)
            assertFalse(result.isPassed(), "UPDATE without required field should violate");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.MEDIUM, result.getViolations().get(0).getRiskLevel());
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
            // Parse SELECT without required field
            String sql = "SELECT * FROM users WHERE name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            // Build context using statement field (NOT parsedSql)
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.statementField")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)  // NEW field
                    .build();

            // Verify statement field is accessible
            assertNotNull(context.getStatement(), "statement field should be accessible");
            assertEquals(stmt, context.getStatement(), "statement should match");

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added (no required field)
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
        }
    }
}

