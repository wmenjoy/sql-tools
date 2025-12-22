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

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for BlacklistFieldChecker migration to visitor pattern.
 *
 * <p>Validates that the migrated BlacklistFieldChecker correctly:</p>
 * <ul>
 *   <li>Uses visitSelect() instead of check()</li>
 *   <li>Uses direct JSqlParser API (plainSelect.getWhere())</li>
 *   <li>Uses local extractFieldsFromExpression() instead of utility method</li>
 *   <li>Uses SqlContext.getStatement() instead of getStatement()</li>
 *   <li>Maintains consistent blacklist detection behavior</li>
 * </ul>
 */
@DisplayName("BlacklistFieldChecker Migration Tests")
public class BlacklistFieldCheckerMigrationTest {

    private BlacklistFieldsConfig config;
    private BlacklistFieldChecker checker;

    @BeforeEach
    public void setUp() {
        config = new BlacklistFieldsConfig();
        config.setEnabled(true);

        // Setup default blacklist: deleted, status
        Set<String> blacklist = new HashSet<>();
        blacklist.add("deleted");
        blacklist.add("status");
        config.setFields(blacklist);

        checker = new BlacklistFieldChecker(config);
    }

    @Nested
    @DisplayName("1. Blacklist-Only WHERE Tests")
    class BlacklistOnlyTests {

        /**
         * Test 1: WHERE with only blacklist field should violate
         */
        @Test
        @DisplayName("visitSelect() - WHERE only blacklist field - should add HIGH violation")
        public void testVisitSelect_blacklistField_violates() throws JSQLParserException {
            // Parse SELECT with WHERE deleted=0
            String sql = "SELECT * FROM users WHERE deleted = 0";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.blacklistOnly")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("deleted"));
            assertTrue(result.getViolations().get(0).getMessage().contains("黑名单"));
        }

        /**
         * Test 2: WHERE with multiple blacklist fields should violate
         */
        @Test
        @DisplayName("visitSelect() - WHERE multiple blacklist fields - should add HIGH violation")
        public void testVisitSelect_multipleBlacklistFields_violates() throws JSQLParserException {
            // Parse SELECT with WHERE deleted=0 AND status='active'
            String sql = "SELECT * FROM users WHERE deleted = 0 AND status = 'active'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.multipleBlacklist")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("deleted"));
            assertTrue(result.getViolations().get(0).getMessage().contains("status"));
        }
    }

    @Nested
    @DisplayName("2. Mixed Fields Tests")
    class MixedFieldsTests {

        /**
         * Test 3: WHERE with non-blacklist field should pass
         */
        @Test
        @DisplayName("visitSelect() - WHERE with non-blacklist field - should pass")
        public void testVisitSelect_allowedFields_passes() throws JSQLParserException {
            // Parse SELECT with WHERE id=1
            String sql = "SELECT * FROM users WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.allowedField")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation
            assertTrue(result.isPassed(), "Should pass validation");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 4: WHERE with mixed fields (blacklist + non-blacklist) should pass
         */
        @Test
        @DisplayName("visitSelect() - WHERE mixed fields - should pass")
        public void testVisitSelect_mixedFields_passes() throws JSQLParserException {
            // Parse SELECT with WHERE id=1 AND deleted=0
            String sql = "SELECT * FROM users WHERE id = 1 AND deleted = 0";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.mixedFields")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (includes non-blacklist field 'id')
            assertTrue(result.isPassed(), "Should pass - includes non-blacklist field");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("3. Wildcard Pattern Tests")
    class WildcardPatternTests {

        /**
         * Test 5: Wildcard pattern should match prefix
         */
        @Test
        @DisplayName("visitSelect() - wildcard pattern match - should violate")
        public void testVisitSelect_wildcardPattern_violates() throws JSQLParserException {
            // Setup wildcard pattern
            Set<String> blacklist = new HashSet<>();
            blacklist.add("create_*");  // Matches create_time, create_by, etc.
            config.setFields(blacklist);

            BlacklistFieldChecker wildcardChecker = new BlacklistFieldChecker(config);

            // Parse SELECT with WHERE create_time > ?
            String sql = "SELECT * FROM users WHERE create_time > '2020-01-01'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.wildcardPattern")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            wildcardChecker.check(context, result);

            // Verify violation added (create_time matches create_*)
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertTrue(result.getViolations().get(0).getMessage().contains("create_time"));
        }
    }

    @Nested
    @DisplayName("4. Edge Cases Tests")
    class EdgeCasesTests {

        /**
         * Test 6: No WHERE clause should be skipped
         */
        @Test
        @DisplayName("visitSelect() - no WHERE clause - should be skipped")
        public void testVisitSelect_noWhere_skipped() throws JSQLParserException {
            // Parse SELECT without WHERE
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

            // Verify no violation (no WHERE clause to check)
            assertTrue(result.isPassed(), "Should skip - no WHERE clause");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 7: WHERE 1=1 (no fields) should be skipped
         */
        @Test
        @DisplayName("visitSelect() - WHERE 1=1 (dummy) - should be skipped")
        public void testVisitSelect_dummyWhere_skipped() throws JSQLParserException {
            // Parse SELECT with WHERE 1=1
            String sql = "SELECT * FROM users WHERE 1 = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.dummyWhere")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (no fields extracted from WHERE 1=1)
            assertTrue(result.isPassed(), "Should skip - no fields in WHERE");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("5. Other Statement Tests")
    class OtherStatementTests {

        /**
         * Test 8: UPDATE should be skipped (default visitUpdate is no-op)
         */
        @Test
        @DisplayName("visitUpdate() - UPDATE statement - should be skipped")
        public void testVisitUpdate_skipped() throws JSQLParserException {
            // Parse UPDATE with blacklist field
            String sql = "UPDATE users SET name = 'foo' WHERE deleted = 0";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.update")
                    .type(SqlCommandType.UPDATE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (UPDATE skipped by BlacklistFieldChecker)
            assertTrue(result.isPassed(), "UPDATE should be skipped");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 9: DELETE should be skipped (default visitDelete is no-op)
         */
        @Test
        @DisplayName("visitDelete() - DELETE statement - should be skipped")
        public void testVisitDelete_skipped() throws JSQLParserException {
            // Parse DELETE with blacklist field
            String sql = "DELETE FROM users WHERE deleted = 0";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.delete")
                    .type(SqlCommandType.DELETE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (DELETE skipped by BlacklistFieldChecker)
            assertTrue(result.isPassed(), "DELETE should be skipped");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("6. Statement Field Migration Tests")
    class StatementFieldMigrationTests {

        /**
         * Test 10: Verify statement field is used (not parsedSql)
         */
        @Test
        @DisplayName("statement field - should work correctly")
        public void testStatementField_works() throws JSQLParserException {
            // Parse SELECT with blacklist field
            String sql = "SELECT * FROM users WHERE deleted = 0";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            // Build context using statement field (NOT parsedSql)
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.statementField")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)  // NEW field
                    .build();

            // Verify statement field is accessible
            assertNotNull(context.getStatement(), "statement field should be accessible");
            assertEquals(stmt, context.getStatement(), "statement should match");

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added (blacklist-only WHERE)
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
        }
    }
}

