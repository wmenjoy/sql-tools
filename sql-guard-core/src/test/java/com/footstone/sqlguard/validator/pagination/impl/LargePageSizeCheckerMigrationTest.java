package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.PaginationType;
import com.footstone.sqlguard.validator.rule.impl.PaginationAbuseConfig;
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
 * TDD tests for LargePageSizeChecker migration to visitor pattern.
 *
 * <p>This test class validates the Phase 12 migration of LargePageSizeChecker from
 * the old check() implementation to the new visitSelect() method.</p>
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>Test 1: Large pageSize (> maxPageSize) - should add MEDIUM violation</li>
 *   <li>Test 2: Normal pageSize (< maxPageSize) - should pass</li>
 *   <li>Test 3: Not PHYSICAL pagination - should skip</li>
 *   <li>Test 4: Disabled configuration - should skip</li>
 *   <li>Test 5: Constructor with super(config) - should work</li>
 * </ul>
 *
 * @see LargePageSizeChecker
 * @since 1.1.0
 */
@DisplayName("LargePageSizeChecker Migration Tests")
public class LargePageSizeCheckerMigrationTest {

    private PaginationPluginDetector detector;
    private PaginationAbuseConfig config;
    private LargePageSizeChecker checker;

    @BeforeEach
    public void setUp() {
        detector = Mockito.mock(PaginationPluginDetector.class);
        config = new PaginationAbuseConfig(true, 10000, 1000);
        checker = new LargePageSizeChecker(detector, config);
    }

    @Nested
    @DisplayName("1. Large PageSize Detection Tests")
    class LargePageSizeDetectionTests {

        /**
         * Test 1: Large pageSize (> maxPageSize) - should add MEDIUM violation
         */
        @Test
        @DisplayName("visitSelect() - large pageSize - should add MEDIUM violation")
        public void testVisitSelect_largePageSize_violates() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE status = 'active' LIMIT 5000";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.largePageSize")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.PHYSICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.MEDIUM, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("pageSize=5000"));
        }

        /**
         * Test 2: Normal pageSize (< maxPageSize) - should pass
         */
        @Test
        @DisplayName("visitSelect() - normal pageSize - should pass")
        public void testVisitSelect_normalPageSize_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE status = 'active' LIMIT 500";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.normalPageSize")
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
         * Test: Boundary pageSize (= maxPageSize) - should pass
         */
        @Test
        @DisplayName("visitSelect() - boundary pageSize - should pass")
        public void testVisitSelect_boundaryPageSize_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE status = 'active' LIMIT 1000";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.boundaryPageSize")
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
            PaginationAbuseConfig disabledConfig = new PaginationAbuseConfig(false, 10000, 1000);
            LargePageSizeChecker disabledChecker = new LargePageSizeChecker(detector, disabledConfig);

            String sql = "SELECT * FROM users WHERE status = 'active' LIMIT 5000";
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
            LargePageSizeChecker checker = new LargePageSizeChecker(detector, config);
            assertNotNull(checker);
            assertTrue(checker.isEnabled());

            // Test with disabled config
            PaginationAbuseConfig disabledConfig = new PaginationAbuseConfig(false, 10000, 1000);
            LargePageSizeChecker disabledChecker = new LargePageSizeChecker(detector, disabledConfig);
            assertFalse(disabledChecker.isEnabled());
        }

        /**
         * Test: Constructor validation - null detector should throw
         */
        @Test
        @DisplayName("constructor - null detector - should throw IllegalArgumentException")
        public void testConstructor_nullDetector_throws() {
            assertThrows(IllegalArgumentException.class, () -> {
                new LargePageSizeChecker(null, config);
            });
        }

        /**
         * Test: Constructor validation - null config should throw
         */
        @Test
        @DisplayName("constructor - null config - should throw IllegalArgumentException")
        public void testConstructor_nullConfig_throws() {
            assertThrows(IllegalArgumentException.class, () -> {
                new LargePageSizeChecker(detector, null);
            });
        }
    }
}

