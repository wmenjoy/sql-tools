package com.footstone.sqlguard.validator.pagination.impl;

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
 * TDD tests for DeepPaginationChecker migration to visitor pattern.
 *
 * <p>This test class validates the Phase 12 migration of DeepPaginationChecker from
 * the old check() implementation to the new visitSelect() method.</p>
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>Test 1: Deep offset (> maxOffset) - should add MEDIUM violation</li>
 *   <li>Test 2: Normal offset (< maxOffset) - should pass</li>
 *   <li>Test 3: Early-return flag set - should skip</li>
 *   <li>Test 4: Not PHYSICAL pagination - should skip</li>
 *   <li>Test 5: Disabled configuration - should skip</li>
 * </ul>
 *
 * @see DeepPaginationChecker
 * @since 1.1.0
 */
@DisplayName("DeepPaginationChecker Migration Tests")
public class DeepPaginationCheckerMigrationTest {

    private PaginationPluginDetector detector;
    private PaginationAbuseConfig config;
    private DeepPaginationChecker checker;

    @BeforeEach
    public void setUp() {
        detector = Mockito.mock(PaginationPluginDetector.class);
        config = new PaginationAbuseConfig(true, 10000, 1000);
        checker = new DeepPaginationChecker(config, detector);
    }

    @Nested
    @DisplayName("1. Deep Offset Detection Tests")
    class DeepOffsetDetectionTests {

        /**
         * Test 1: Deep offset (> maxOffset) - should add MEDIUM violation
         */
        @Test
        @DisplayName("visitSelect() - deep offset - should add MEDIUM violation")
        public void testVisitSelect_deepOffset_violates() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE id > 0 LIMIT 20 OFFSET 50000";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.deepOffset")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.PHYSICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.MEDIUM, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("深分页"));
            assertTrue(result.getViolations().get(0).getMessage().contains("50000"));
        }

        /**
         * Test 2: Normal offset (< maxOffset) - should pass
         */
        @Test
        @DisplayName("visitSelect() - normal offset - should pass")
        public void testVisitSelect_normalOffset_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE id > 0 LIMIT 20 OFFSET 100";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.normalOffset")
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
         * Test: No offset - should pass
         */
        @Test
        @DisplayName("visitSelect() - no offset - should pass")
        public void testVisitSelect_noOffset_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE id > 0 LIMIT 20";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.noOffset")
                    .type(SqlCommandType.SELECT)
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
    @DisplayName("2. Early-Return Tests")
    class EarlyReturnTests {

        /**
         * Test 3: Early-return flag set - should skip
         */
        @Test
        @DisplayName("visitSelect() - early-return flag - should skip")
        public void testVisitSelect_earlyReturn_skips() throws JSQLParserException {
            String sql = "SELECT * FROM users LIMIT 20 OFFSET 50000";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.earlyReturn")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.PHYSICAL);

            ValidationResult result = ValidationResult.pass();
            // Simulate NoConditionPaginationChecker already set early-return
            result.getDetails().put("earlyReturn", true);
            
            checker.check(context, result);

            // Should still pass (no new violation added)
            assertTrue(result.isPassed());
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("3. Pagination Type Tests")
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
                    .mapperId("test.logical")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.LOGICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed());
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("4. Configuration Tests")
    class ConfigurationTests {

        /**
         * Test 5: Disabled - should skip
         */
        @Test
        @DisplayName("visitSelect() - disabled - should skip")
        public void testVisitSelect_disabled_skips() throws JSQLParserException {
            PaginationAbuseConfig disabledConfig = new PaginationAbuseConfig(false, 10000, 1000);
            DeepPaginationChecker disabledChecker = new DeepPaginationChecker(disabledConfig, detector);

            String sql = "SELECT * FROM users LIMIT 20 OFFSET 50000";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.disabled")
                    .type(SqlCommandType.SELECT)
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
    @DisplayName("5. Constructor Migration Tests")
    class ConstructorMigrationTests {

        /**
         * Test: Constructor - super(config) - should work
         */
        @Test
        @DisplayName("constructor - super(config) - should work")
        public void testConstructor_superConfig_works() {
            DeepPaginationChecker checker = new DeepPaginationChecker(config, detector);
            assertNotNull(checker);
            assertTrue(checker.isEnabled());

            // Test with disabled config
            PaginationAbuseConfig disabledConfig = new PaginationAbuseConfig(false, 10000, 1000);
            DeepPaginationChecker disabledChecker = new DeepPaginationChecker(disabledConfig, detector);
            assertFalse(disabledChecker.isEnabled());
        }
    }
}

