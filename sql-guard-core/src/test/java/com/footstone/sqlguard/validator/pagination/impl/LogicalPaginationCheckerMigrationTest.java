package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.PaginationType;
import com.footstone.sqlguard.validator.rule.impl.LogicalPaginationConfig;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TDD tests for LogicalPaginationChecker migration to new architecture.
 *
 * <p>This test class validates the Phase 12 migration of LogicalPaginationChecker.
 * Unlike other checkers, LogicalPaginationChecker doesn't use visitor pattern
 * as it checks RowBounds/IPage parameters, not SQL AST.</p>
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>Test 1: LOGICAL pagination detection - should add CRITICAL violation</li>
 *   <li>Test 2: PHYSICAL pagination - should pass</li>
 *   <li>Test 3: NONE pagination - should pass</li>
 *   <li>Test 4: Disabled configuration - should skip</li>
 *   <li>Test 5: Constructor with super(config) - should work</li>
 * </ul>
 *
 * @see LogicalPaginationChecker
 * @since 1.1.0
 */
@DisplayName("LogicalPaginationChecker Migration Tests")
public class LogicalPaginationCheckerMigrationTest {

    private PaginationPluginDetector detector;
    private LogicalPaginationConfig config;
    private LogicalPaginationChecker checker;

    @BeforeEach
    public void setUp() {
        detector = Mockito.mock(PaginationPluginDetector.class);
        config = new LogicalPaginationConfig();
        config.setEnabled(true);
        checker = new LogicalPaginationChecker(detector, config);
    }

    @Nested
    @DisplayName("1. Logical Pagination Detection Tests")
    class LogicalPaginationDetectionTests {

        /**
         * Test 1: LOGICAL pagination - should add CRITICAL violation
         */
        @Test
        @DisplayName("visitSelect() - LOGICAL pagination - should add CRITICAL violation")
        public void testVisitSelect_logicalPagination_violates() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE id > 0";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.logical")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .rowBounds(new RowBounds(0, 20))  // RowBounds without plugin
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.LOGICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("逻辑分页"));
            assertEquals(0, result.getDetails().get("offset"));
            assertEquals(20, result.getDetails().get("limit"));
            assertEquals("LOGICAL", result.getDetails().get("paginationType"));
        }

        /**
         * Test 2: PHYSICAL pagination - should pass
         */
        @Test
        @DisplayName("visitSelect() - PHYSICAL pagination - should pass")
        public void testVisitSelect_physicalPagination_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users LIMIT 20";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.physical")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.PHYSICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed());
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 3: NONE pagination - should pass
         */
        @Test
        @DisplayName("visitSelect() - NONE pagination - should pass")
        public void testVisitSelect_nonePagination_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.none")
                    .type(SqlCommandType.SELECT)
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
    @DisplayName("2. Configuration Tests")
    class ConfigurationTests {

        /**
         * Test 4: Disabled - should skip
         */
        @Test
        @DisplayName("visitSelect() - disabled - should skip")
        public void testVisitSelect_disabled_skips() throws JSQLParserException {
            config.setEnabled(false);
            LogicalPaginationChecker disabledChecker = new LogicalPaginationChecker(detector, config);

            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.disabled")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .rowBounds(new RowBounds(0, 20))
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.LOGICAL);

            ValidationResult result = ValidationResult.pass();
            disabledChecker.check(context, result);

            assertTrue(result.isPassed());
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("3. Constructor Migration Tests")
    class ConstructorMigrationTests {

        /**
         * Test 5: Constructor - super(config) - should work
         */
        @Test
        @DisplayName("constructor - super(config) - should work")
        public void testConstructor_superConfig_works() {
            // Verify constructor calls super(config) correctly
            LogicalPaginationChecker checker = new LogicalPaginationChecker(detector, config);
            assertNotNull(checker);
            assertTrue(checker.isEnabled());

            // Test with disabled config
            LogicalPaginationConfig disabledConfig = new LogicalPaginationConfig(false);
            LogicalPaginationChecker disabledChecker = new LogicalPaginationChecker(detector, disabledConfig);
            assertFalse(disabledChecker.isEnabled());
        }

        /**
         * Test: Constructor validation - null detector should throw
         */
        @Test
        @DisplayName("constructor - null detector - should throw IllegalArgumentException")
        public void testConstructor_nullDetector_throws() {
            assertThrows(IllegalArgumentException.class, () -> {
                new LogicalPaginationChecker(null, config);
            });
        }

        /**
         * Test: Constructor validation - null config should throw
         */
        @Test
        @DisplayName("constructor - null config - should throw IllegalArgumentException")
        public void testConstructor_nullConfig_throws() {
            assertThrows(IllegalArgumentException.class, () -> {
                new LogicalPaginationChecker(detector, null);
            });
        }
    }
}

