package com.footstone.sqlguard.interceptor.inner.impl;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.config.ViolationStrategy;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.parser.StatementContext;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TDD tests for SqlGuardCheckInnerInterceptor.
 *
 * <p>Note: MyBatis classes (MappedStatement, Executor, BoundSql) are final/complex and
 * cannot be mocked directly. Tests use a simplified approach focusing on the core logic
 * through the internal executeChecks method via reflection or null-safe test patterns.
 */
@DisplayName("SqlGuardCheckInnerInterceptor Tests")
public class SqlGuardCheckInnerInterceptorTest {

    private SqlGuardCheckInnerInterceptor interceptor;
    private List<RuleChecker> ruleCheckers;
    private SqlGuardConfig config;
    private JSqlParserFacade parserFacade;

    @BeforeEach
    void setUp() {
        parserFacade = mock(JSqlParserFacade.class);
        config = mock(SqlGuardConfig.class);
    }

    @AfterEach
    void tearDown() {
        StatementContext.clear();
    }

    @Nested
    @DisplayName("1. Priority Tests")
    class PriorityTests {

        @Test
        @DisplayName("testGetPriority_returns10 - priority is 10")
        public void testGetPriority_returns10() {
            // Arrange
            ruleCheckers = Collections.emptyList();
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            // Act
            int priority = interceptor.getPriority();

            // Assert
            assertEquals(10, priority, "Check interceptor priority should be 10");
        }
    }

    @Nested
    @DisplayName("2. Statement Caching Tests")
    class StatementCachingTests {

        @Test
        @DisplayName("testStatementContext_cacheHit - cache hit uses cached statement")
        public void testStatementContext_cacheHit() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt); // Pre-cache

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);
            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            // Access executeChecks via reflection to test core logic
            java.lang.reflect.Method executeChecks = SqlGuardCheckInnerInterceptor.class
                    .getDeclaredMethod("executeChecks", org.apache.ibatis.mapping.BoundSql.class, String.class);
            executeChecks.setAccessible(true);

            // Create a mock BoundSql-like object using a simple wrapper
            org.apache.ibatis.mapping.BoundSql boundSql = createMockBoundSql(sql);

            // Act - invoke via reflection
            boolean result = (boolean) executeChecks.invoke(interceptor, boundSql, "UserMapper.selectAll");

            // Assert
            assertTrue(result, "Should return true to continue chain");
            verify(parserFacade, never()).parse(anyString()); // Should NOT parse (cache hit)
            verify(mockChecker, times(1)).check(any(SqlContext.class), any(ValidationResult.class));
        }

        @Test
        @DisplayName("testStatementContext_cacheMiss - cache miss parses and caches")
        public void testStatementContext_cacheMiss() throws Exception {
            // Arrange
            String sql = "SELECT * FROM products";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);
            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            java.lang.reflect.Method executeChecks = SqlGuardCheckInnerInterceptor.class
                    .getDeclaredMethod("executeChecks", org.apache.ibatis.mapping.BoundSql.class, String.class);
            executeChecks.setAccessible(true);

            org.apache.ibatis.mapping.BoundSql boundSql = createMockBoundSql(sql);

            // Act
            boolean result = (boolean) executeChecks.invoke(interceptor, boundSql, "ProductMapper.selectAll");

            // Assert
            assertTrue(result, "Should return true to continue chain");
            verify(parserFacade, times(1)).parse(sql); // Should parse (cache miss)
            assertSame(stmt, StatementContext.get(sql), "Should cache parsed Statement");
        }

        @Test
        @DisplayName("testSqlContext_containsStatement - SqlContext contains statement")
        public void testSqlContext_containsStatement() throws Exception {
            // Arrange
            String sql = "SELECT * FROM orders";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);
            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);

            java.lang.reflect.Method executeChecks = SqlGuardCheckInnerInterceptor.class
                    .getDeclaredMethod("executeChecks", org.apache.ibatis.mapping.BoundSql.class, String.class);
            executeChecks.setAccessible(true);

            org.apache.ibatis.mapping.BoundSql boundSql = createMockBoundSql(sql);

            // Act
            executeChecks.invoke(interceptor, boundSql, "OrderMapper.selectAll");

            // Assert
            verify(mockChecker).check(contextCaptor.capture(), any(ValidationResult.class));
            SqlContext capturedContext = contextCaptor.getValue();
            assertNotNull(capturedContext.getStatement(), "SqlContext should contain Statement");
            assertSame(stmt, capturedContext.getStatement(), "SqlContext Statement should match parsed Statement");
        }
    }

    @Nested
    @DisplayName("3. RuleChecker Invocation Tests")
    class RuleCheckerInvocationTests {

        @Test
        @DisplayName("testAllEnabledCheckers_invoked - all enabled checkers invoked")
        public void testAllEnabledCheckers_invoked() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker checker1 = mock(RuleChecker.class);
            RuleChecker checker2 = mock(RuleChecker.class);
            RuleChecker checker3 = mock(RuleChecker.class);
            when(checker1.isEnabled()).thenReturn(true);
            when(checker2.isEnabled()).thenReturn(true);
            when(checker3.isEnabled()).thenReturn(true);

            ruleCheckers = Arrays.asList(checker1, checker2, checker3);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            java.lang.reflect.Method executeChecks = SqlGuardCheckInnerInterceptor.class
                    .getDeclaredMethod("executeChecks", org.apache.ibatis.mapping.BoundSql.class, String.class);
            executeChecks.setAccessible(true);

            org.apache.ibatis.mapping.BoundSql boundSql = createMockBoundSql(sql);

            // Act
            executeChecks.invoke(interceptor, boundSql, "UserMapper.selectAll");

            // Assert
            verify(checker1, times(1)).check(any(SqlContext.class), any(ValidationResult.class));
            verify(checker2, times(1)).check(any(SqlContext.class), any(ValidationResult.class));
            verify(checker3, times(1)).check(any(SqlContext.class), any(ValidationResult.class));
        }

        @Test
        @DisplayName("testDisabledCheckers_skipped - disabled checkers not invoked")
        public void testDisabledCheckers_skipped() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker checker1 = mock(RuleChecker.class);
            RuleChecker checker2 = mock(RuleChecker.class);
            when(checker1.isEnabled()).thenReturn(true);
            when(checker2.isEnabled()).thenReturn(false); // Disabled

            ruleCheckers = Arrays.asList(checker1, checker2);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            java.lang.reflect.Method executeChecks = SqlGuardCheckInnerInterceptor.class
                    .getDeclaredMethod("executeChecks", org.apache.ibatis.mapping.BoundSql.class, String.class);
            executeChecks.setAccessible(true);

            org.apache.ibatis.mapping.BoundSql boundSql = createMockBoundSql(sql);

            // Act
            executeChecks.invoke(interceptor, boundSql, "UserMapper.selectAll");

            // Assert
            verify(checker1, times(1)).check(any(SqlContext.class), any(ValidationResult.class));
            verify(checker2, never()).check(any(SqlContext.class), any(ValidationResult.class));
        }

        @Test
        @DisplayName("testRuleCheckerIntegration - RuleChecker integration works")
        public void testRuleCheckerIntegration() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);

            // Simulate violation detection using ViolationInfo
            doAnswer(invocation -> {
                ValidationResult result = invocation.getArgument(1);
                result.addViolation(RiskLevel.CRITICAL, "Missing WHERE clause", "Add WHERE condition");
                return null;
            }).when(mockChecker).check(any(SqlContext.class), any(ValidationResult.class));

            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            java.lang.reflect.Method executeChecks = SqlGuardCheckInnerInterceptor.class
                    .getDeclaredMethod("executeChecks", org.apache.ibatis.mapping.BoundSql.class, String.class);
            executeChecks.setAccessible(true);

            org.apache.ibatis.mapping.BoundSql boundSql = createMockBoundSql(sql);

            // Act
            boolean result = (boolean) executeChecks.invoke(interceptor, boundSql, "UserMapper.selectAll");

            // Assert
            assertTrue(result, "Should return true even with violations (LOG strategy)");
            verify(mockChecker, times(1)).check(any(SqlContext.class), any(ValidationResult.class));
        }
    }

    @Nested
    @DisplayName("4. Violation Handling Tests")
    class ViolationHandlingTests {

        @Test
        @DisplayName("testViolationStrategy_BLOCK_throwsSQLException - BLOCK throws SQLException")
        public void testViolationStrategy_BLOCK_throwsSQLException() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);

            // Simulate violation
            doAnswer(invocation -> {
                ValidationResult result = invocation.getArgument(1);
                result.addViolation(RiskLevel.CRITICAL, "Missing WHERE clause", "Add WHERE condition");
                return null;
            }).when(mockChecker).check(any(SqlContext.class), any(ValidationResult.class));

            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.BLOCK);

            java.lang.reflect.Method executeChecks = SqlGuardCheckInnerInterceptor.class
                    .getDeclaredMethod("executeChecks", org.apache.ibatis.mapping.BoundSql.class, String.class);
            executeChecks.setAccessible(true);

            org.apache.ibatis.mapping.BoundSql boundSql = createMockBoundSql(sql);

            // Act & Assert
            java.lang.reflect.InvocationTargetException thrown = assertThrows(
                    java.lang.reflect.InvocationTargetException.class,
                    () -> executeChecks.invoke(interceptor, boundSql, "UserMapper.selectAll"),
                    "Should throw SQLException for BLOCK strategy");

            assertTrue(thrown.getCause() instanceof SQLException,
                    "Cause should be SQLException");
            assertTrue(thrown.getCause().getMessage().contains("SQL safety violations detected"),
                    "Exception message should mention violations");
        }

        @Test
        @DisplayName("testViolationStrategy_WARN_logsWarning - WARN logs warning")
        public void testViolationStrategy_WARN_logsWarning() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);

            // Simulate violation
            doAnswer(invocation -> {
                ValidationResult result = invocation.getArgument(1);
                result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
                return null;
            }).when(mockChecker).check(any(SqlContext.class), any(ValidationResult.class));

            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.WARN);

            java.lang.reflect.Method executeChecks = SqlGuardCheckInnerInterceptor.class
                    .getDeclaredMethod("executeChecks", org.apache.ibatis.mapping.BoundSql.class, String.class);
            executeChecks.setAccessible(true);

            org.apache.ibatis.mapping.BoundSql boundSql = createMockBoundSql(sql);

            // Act
            boolean result = (boolean) executeChecks.invoke(interceptor, boundSql, "UserMapper.selectAll");

            // Assert
            assertTrue(result, "Should return true (warning only, no exception)");
            // Note: Logging verification would require LogCaptor or similar
        }

        @Test
        @DisplayName("testViolationStrategy_LOG_logsInfo - LOG logs info")
        public void testViolationStrategy_LOG_logsInfo() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);

            // Simulate violation
            doAnswer(invocation -> {
                ValidationResult result = invocation.getArgument(1);
                result.addViolation(RiskLevel.MEDIUM, "Missing WHERE clause", "Add WHERE condition");
                return null;
            }).when(mockChecker).check(any(SqlContext.class), any(ValidationResult.class));

            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            java.lang.reflect.Method executeChecks = SqlGuardCheckInnerInterceptor.class
                    .getDeclaredMethod("executeChecks", org.apache.ibatis.mapping.BoundSql.class, String.class);
            executeChecks.setAccessible(true);

            org.apache.ibatis.mapping.BoundSql boundSql = createMockBoundSql(sql);

            // Act
            boolean result = (boolean) executeChecks.invoke(interceptor, boundSql, "UserMapper.selectAll");

            // Assert
            assertTrue(result, "Should return true (log only, no exception)");
        }

        @Test
        @DisplayName("testMultipleViolations_aggregated - multiple violations aggregated")
        public void testMultipleViolations_aggregated() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker checker1 = mock(RuleChecker.class);
            RuleChecker checker2 = mock(RuleChecker.class);
            when(checker1.isEnabled()).thenReturn(true);
            when(checker2.isEnabled()).thenReturn(true);

            // Checker1 detects violation
            doAnswer(invocation -> {
                ValidationResult result = invocation.getArgument(1);
                result.addViolation(RiskLevel.CRITICAL, "Missing WHERE clause", null);
                return null;
            }).when(checker1).check(any(SqlContext.class), any(ValidationResult.class));

            // Checker2 detects violation
            doAnswer(invocation -> {
                ValidationResult result = invocation.getArgument(1);
                result.addViolation(RiskLevel.HIGH, "Missing pagination", null);
                return null;
            }).when(checker2).check(any(SqlContext.class), any(ValidationResult.class));

            ruleCheckers = Arrays.asList(checker1, checker2);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.BLOCK);

            java.lang.reflect.Method executeChecks = SqlGuardCheckInnerInterceptor.class
                    .getDeclaredMethod("executeChecks", org.apache.ibatis.mapping.BoundSql.class, String.class);
            executeChecks.setAccessible(true);

            org.apache.ibatis.mapping.BoundSql boundSql = createMockBoundSql(sql);

            // Act & Assert
            java.lang.reflect.InvocationTargetException thrown = assertThrows(
                    java.lang.reflect.InvocationTargetException.class,
                    () -> executeChecks.invoke(interceptor, boundSql, "UserMapper.selectAll"));

            String message = thrown.getCause().getMessage();
            assertTrue(message.contains("CRITICAL"), "Should contain first violation risk level");
            assertTrue(message.contains("HIGH"), "Should contain second violation risk level");
        }

        @Test
        @DisplayName("testNoViolations_continueChain - no violations continue chain")
        public void testNoViolations_continueChain() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);
            // No violations added

            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.BLOCK);

            java.lang.reflect.Method executeChecks = SqlGuardCheckInnerInterceptor.class
                    .getDeclaredMethod("executeChecks", org.apache.ibatis.mapping.BoundSql.class, String.class);
            executeChecks.setAccessible(true);

            org.apache.ibatis.mapping.BoundSql boundSql = createMockBoundSql(sql);

            // Act
            boolean result = (boolean) executeChecks.invoke(interceptor, boundSql, "UserMapper.selectById");

            // Assert
            assertTrue(result, "Should return true when no violations");
        }
    }

    @Nested
    @DisplayName("5. No-op Methods Tests")
    class NoOpMethodsTests {

        @Test
        @DisplayName("testBeforeQuery_noOp - beforeQuery is no-op")
        public void testBeforeQuery_noOp() throws Exception {
            // Arrange
            ruleCheckers = Collections.emptyList();
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            // Act - call with null parameters (safe for no-op)
            assertDoesNotThrow(() ->
                            interceptor.beforeQuery(null, null, null, null, null, null),
                    "beforeQuery should not throw exception");
        }

        @Test
        @DisplayName("testBeforeUpdate_noOp - beforeUpdate is no-op")
        public void testBeforeUpdate_noOp() throws Exception {
            // Arrange
            ruleCheckers = Collections.emptyList();
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            // Act - call with null parameters (safe for no-op)
            assertDoesNotThrow(() ->
                            interceptor.beforeUpdate(null, null, null),
                    "beforeUpdate should not throw exception");
        }
    }

    @Nested
    @DisplayName("6. Implementation Tests")
    class ImplementationTests {

        @Test
        @DisplayName("testImplementsSqlGuardInnerInterceptor - implements interface")
        public void testImplementsSqlGuardInnerInterceptor() {
            // Assert
            assertTrue(
                    com.footstone.sqlguard.interceptor.inner.SqlGuardInnerInterceptor.class
                            .isAssignableFrom(SqlGuardCheckInnerInterceptor.class),
                    "SqlGuardCheckInnerInterceptor should implement SqlGuardInnerInterceptor");
        }
    }

    /**
     * Creates a simple BoundSql-like object for testing.
     *
     * <p>Since BoundSql is a MyBatis class that cannot be easily mocked,
     * we create a real instance using reflection or a minimal constructor.
     *
     * @param sql SQL string
     * @return BoundSql instance
     */
    private org.apache.ibatis.mapping.BoundSql createMockBoundSql(String sql) {
        // Create a minimal Configuration and BoundSql
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        return new org.apache.ibatis.mapping.BoundSql(
                configuration,
                sql,
                Collections.emptyList(),
                null
        );
    }
}
