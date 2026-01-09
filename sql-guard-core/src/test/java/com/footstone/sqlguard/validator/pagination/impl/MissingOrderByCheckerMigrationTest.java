package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.PaginationType;
import com.footstone.sqlguard.validator.rule.impl.MissingOrderByConfig;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TDD tests for MissingOrderByChecker migration to visitor pattern.
 *
 * <p>This test class validates the Phase 12 migration of MissingOrderByChecker from
 * the old check() implementation to the new visitSelect() method.</p>
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>Test 1: LIMIT without ORDER BY - should add LOW violation</li>
 *   <li>Test 2: LIMIT with ORDER BY - should pass</li>
 *   <li>Test 3: Not PHYSICAL pagination - should skip</li>
 *   <li>Test 4: Disabled configuration - should skip</li>
 *   <li>Test 5: Constructor with super(config) - should work</li>
 * </ul>
 *
 * @see MissingOrderByChecker
 * @since 1.1.0
 */
@DisplayName("MissingOrderByChecker Migration Tests")
public class MissingOrderByCheckerMigrationTest {

    private PaginationPluginDetector detector;
    private MissingOrderByConfig config;
    private MissingOrderByChecker checker;

    @BeforeEach
    public void setUp() {
        detector = Mockito.mock(PaginationPluginDetector.class);
        config = new MissingOrderByConfig(true); // Explicitly enable for tests
        config.setEnabled(true);
        checker = new MissingOrderByChecker(detector, config);
    }

    @Nested
    @DisplayName("1. Missing ORDER BY Detection Tests")
    class MissingOrderByDetectionTests {

        /**
         * Test 1: LIMIT without ORDER BY - should add LOW violation
         */
        @Test
        @DisplayName("visitSelect() - LIMIT without ORDER BY - should add LOW violation")
        public void testVisitSelect_missingOrderBy_violates() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE status = 'active' LIMIT 10";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.missingOrderBy")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.PHYSICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.LOW, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("ORDER BY"));
        }

        /**
         * Test 2: LIMIT with ORDER BY - should pass
         */
        @Test
        @DisplayName("visitSelect() - LIMIT with ORDER BY - should pass")
        public void testVisitSelect_withOrderBy_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE status = 'active' ORDER BY id LIMIT 10";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.withOrderBy")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.PHYSICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed());
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test: LIMIT with multiple ORDER BY columns - should pass
         */
        @Test
        @DisplayName("visitSelect() - LIMIT with multiple ORDER BY columns - should pass")
        public void testVisitSelect_multipleOrderBy_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE status = 'active' ORDER BY category, id LIMIT 10";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.multipleOrderBy")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.PHYSICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed());
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("2. Pagination Type Tests")
    class PaginationTypeTests {

        /**
         * Test 3: LOGICAL pagination - should skip
         */
        @Test
        @DisplayName("visitSelect() - LOGICAL pagination - should skip")
        public void testVisitSelect_logicalPagination_skips() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE status = 'active'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.logical")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.LOGICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed());
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test: NONE pagination - should skip
         */
        @Test
        @DisplayName("visitSelect() - NONE pagination - should skip")
        public void testVisitSelect_nonePagination_skips() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.none")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.NONE);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed());
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("3. Configuration Tests")
    class ConfigurationTests {

        /**
         * Test 4: Disabled - should skip
         */
        @Test
        @DisplayName("visitSelect() - disabled - should skip")
        public void testVisitSelect_disabled_skips() throws JSQLParserException {
            config.setEnabled(false);
            MissingOrderByChecker disabledChecker = new MissingOrderByChecker(detector, config);

            String sql = "SELECT * FROM users WHERE status = 'active' LIMIT 10";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.disabled")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.PHYSICAL);

            ValidationResult result = ValidationResult.pass();
            disabledChecker.check(context, result);

            assertTrue(result.isPassed());
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("4. Constructor Migration Tests")
    class ConstructorMigrationTests {

        /**
         * Test 5: Constructor - super(config) - should work
         */
        @Test
        @DisplayName("constructor - super(config) - should work")
        public void testConstructor_superConfig_works() {
            MissingOrderByChecker checker = new MissingOrderByChecker(detector, config);
            assertNotNull(checker);
            assertTrue(checker.isEnabled());

            // Test with disabled config
            MissingOrderByConfig disabledConfig = new MissingOrderByConfig();
            disabledConfig.setEnabled(false);
            MissingOrderByChecker disabledChecker = new MissingOrderByChecker(detector, disabledConfig);
            assertFalse(disabledChecker.isEnabled());
        }
    }
}

