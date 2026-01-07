package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.PaginationType;
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
 * TDD tests for NoConditionPaginationChecker migration to visitor pattern.
 *
 * <p>This test class validates the Phase 12 migration of NoConditionPaginationChecker from
 * the old check() implementation to the new visitSelect() method.</p>
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>Test 1: LIMIT without WHERE - should add CRITICAL violation</li>
 *   <li>Test 2: LIMIT with dummy condition - should add CRITICAL violation</li>
 *   <li>Test 3: LIMIT with valid WHERE - should pass</li>
 *   <li>Test 4: Not PHYSICAL pagination - should skip</li>
 *   <li>Test 5: Disabled configuration - should skip</li>
 * </ul>
 *
 * @see NoConditionPaginationChecker
 * @since 1.1.0
 */
@DisplayName("NoConditionPaginationChecker Migration Tests")
public class NoConditionPaginationCheckerMigrationTest {

    private PaginationPluginDetector detector;
    private NoConditionPaginationConfig config;
    private NoConditionPaginationChecker checker;

    @BeforeEach
    public void setUp() {
        detector = Mockito.mock(PaginationPluginDetector.class);
        config = new NoConditionPaginationConfig();
        config.setEnabled(true);
        checker = new NoConditionPaginationChecker(config, detector);
    }

    @Nested
    @DisplayName("1. No Condition Detection Tests")
    class NoConditionDetectionTests {

        /**
         * Test 1: LIMIT without WHERE - should add CRITICAL violation
         */
        @Test
        @DisplayName("visitSelect() - LIMIT without WHERE - should add CRITICAL violation")
        public void testVisitSelect_noWhere_violates() throws JSQLParserException {
            String sql = "SELECT * FROM users LIMIT 100";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.noWhere")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.PHYSICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("无条件物理分页"));
            assertEquals(Boolean.TRUE, result.getDetails().get("earlyReturn"));
        }

        /**
         * Test 2: LIMIT with dummy condition (1=1) - should add CRITICAL violation
         */
        @Test
        @DisplayName("visitSelect() - LIMIT with 1=1 - should add CRITICAL violation")
        public void testVisitSelect_dummyWhere_violates() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE 1=1 LIMIT 100";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.dummyWhere")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.PHYSICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
        }

        /**
         * Test 3: LIMIT with valid WHERE - should pass
         */
        @Test
        @DisplayName("visitSelect() - LIMIT with valid WHERE - should pass")
        public void testVisitSelect_validWhere_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE status = 'active' LIMIT 100";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .statementId("test.validWhere")
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
         * Test 4: LOGICAL pagination - should skip
         */
        @Test
        @DisplayName("visitSelect() - LOGICAL pagination - should skip")
        public void testVisitSelect_logicalPagination_skips() throws JSQLParserException {
            String sql = "SELECT * FROM users";
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
         * Test 5: Disabled - should skip
         */
        @Test
        @DisplayName("visitSelect() - disabled - should skip")
        public void testVisitSelect_disabled_skips() throws JSQLParserException {
            config.setEnabled(false);
            NoConditionPaginationChecker disabledChecker = new NoConditionPaginationChecker(config, detector);

            String sql = "SELECT * FROM users LIMIT 100";
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
         * Test: Constructor - super(config) - should work
         */
        @Test
        @DisplayName("constructor - super(config) - should work")
        public void testConstructor_superConfig_works() {
            NoConditionPaginationChecker checker = new NoConditionPaginationChecker(config, detector);
            assertNotNull(checker);
            assertTrue(checker.isEnabled());

            // Test with disabled config
            NoConditionPaginationConfig disabledConfig = new NoConditionPaginationConfig();
            disabledConfig.setEnabled(false);
            NoConditionPaginationChecker disabledChecker = new NoConditionPaginationChecker(disabledConfig, detector);
            assertFalse(disabledChecker.isEnabled());
        }
    }
}








