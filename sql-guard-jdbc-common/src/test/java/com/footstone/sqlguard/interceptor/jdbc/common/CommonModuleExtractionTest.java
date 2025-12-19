package com.footstone.sqlguard.interceptor.jdbc.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.ViolationInfo;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * TDD Test Class for Common Module Extraction (12 tests).
 * 
 * <p>Tests verify:
 * <ul>
 *   <li>ViolationStrategy enum unification</li>
 *   <li>JdbcInterceptorBase template method pattern</li>
 *   <li>JdbcInterceptorConfig interface contract</li>
 *   <li>SqlContextBuilder utility</li>
 *   <li>JdbcAuditEventBuilder utility</li>
 *   <li>Package structure correctness</li>
 * </ul>
 * 
 * <p><strong>TDD Process:</strong> RED -> GREEN -> REFACTOR
 * 
 * @see ViolationStrategy
 * @see JdbcInterceptorBase
 * @see JdbcInterceptorConfig
 * @see SqlContextBuilder
 * @see JdbcAuditEventBuilder
 */
@DisplayName("Common Module Extraction Tests")
class CommonModuleExtractionTest {

    // ========== ViolationStrategy Tests (4 tests) ==========

    @Nested
    @DisplayName("ViolationStrategy Enum Tests")
    class ViolationStrategyTests {

        @Test
        @DisplayName("3.1.1 - ViolationStrategy unified enum has all three values")
        void testViolationStrategy_unified_hasAllThreeValues() {
            // When: Get all enum values
            ViolationStrategy[] values = ViolationStrategy.values();

            // Then: Should have exactly 3 values
            assertThat(values).hasSize(3);

            // And: Should contain BLOCK, WARN, LOG
            assertThat(values)
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("BLOCK", "WARN", "LOG");
        }

        @Test
        @DisplayName("3.1.2 - ViolationStrategy.BLOCK behavior matches legacy")
        void testViolationStrategy_BLOCK_behavior_matchesLegacy() {
            // Given: BLOCK strategy
            ViolationStrategy strategy = ViolationStrategy.BLOCK;

            // Then: Should indicate blocking behavior
            assertThat(strategy.shouldBlock()).isTrue();
            assertThat(strategy.shouldLog()).isTrue();
            assertThat(strategy.getLogLevel()).isEqualTo("ERROR");
            assertThat(strategy.name()).isEqualTo("BLOCK");
            assertThat(strategy.ordinal()).isEqualTo(0);
        }

        @Test
        @DisplayName("3.1.3 - ViolationStrategy.WARN behavior matches legacy")
        void testViolationStrategy_WARN_behavior_matchesLegacy() {
            // Given: WARN strategy
            ViolationStrategy strategy = ViolationStrategy.WARN;

            // Then: Should indicate warning behavior
            assertThat(strategy.shouldBlock()).isFalse();
            assertThat(strategy.shouldLog()).isTrue();
            assertThat(strategy.getLogLevel()).isEqualTo("ERROR");
            assertThat(strategy.name()).isEqualTo("WARN");
            assertThat(strategy.ordinal()).isEqualTo(1);
        }

        @Test
        @DisplayName("3.1.4 - ViolationStrategy.LOG behavior matches legacy")
        void testViolationStrategy_LOG_behavior_matchesLegacy() {
            // Given: LOG strategy
            ViolationStrategy strategy = ViolationStrategy.LOG;

            // Then: Should indicate logging-only behavior
            assertThat(strategy.shouldBlock()).isFalse();
            assertThat(strategy.shouldLog()).isTrue();
            assertThat(strategy.getLogLevel()).isEqualTo("WARN");
            assertThat(strategy.name()).isEqualTo("LOG");
            assertThat(strategy.ordinal()).isEqualTo(2);
        }
    }

    // ========== JdbcInterceptorBase Tests (8 tests) ==========

    @Nested
    @DisplayName("JdbcInterceptorBase Template Method Tests")
    class JdbcInterceptorBaseTests {

        @Test
        @DisplayName("3.1.5 - JdbcInterceptorBase template method invokes hooks in order")
        void testJdbcInterceptorBase_templateMethod_invokesInOrder() {
            // Given: A test interceptor that records hook invocations
            TestJdbcInterceptor interceptor = new TestJdbcInterceptor();
            String testSql = "SELECT * FROM users WHERE id = 1";

            // When: Intercept SQL
            interceptor.interceptSql(testSql);

            // Then: Hooks should be invoked in correct order
            assertThat(interceptor.getInvocationOrder())
                .containsExactly(
                    "beforeValidation",
                    "buildSqlContext",
                    "validate",
                    "handleViolation",
                    "afterValidation"
                );
        }

        @Test
        @DisplayName("3.1.6 - JdbcInterceptorBase beforeValidation hook is called")
        void testJdbcInterceptorBase_beforeValidation_hookCalled() {
            // Given: Test interceptor
            TestJdbcInterceptor interceptor = new TestJdbcInterceptor();
            String testSql = "SELECT * FROM users";

            // When: Intercept SQL
            interceptor.interceptSql(testSql);

            // Then: beforeValidation should have been called with correct SQL
            assertThat(interceptor.isBeforeValidationCalled()).isTrue();
            assertThat(interceptor.getLastSql()).isEqualTo(testSql);
        }

        @Test
        @DisplayName("3.1.7 - JdbcInterceptorBase afterValidation hook is called")
        void testJdbcInterceptorBase_afterValidation_hookCalled() {
            // Given: Test interceptor
            TestJdbcInterceptor interceptor = new TestJdbcInterceptor();
            String testSql = "SELECT * FROM users";

            // When: Intercept SQL
            interceptor.interceptSql(testSql);

            // Then: afterValidation should have been called with result
            assertThat(interceptor.isAfterValidationCalled()).isTrue();
            assertThat(interceptor.getLastValidationResult()).isNotNull();
        }

        @Test
        @DisplayName("3.1.8 - JdbcInterceptorBase onError hook is called on exception")
        void testJdbcInterceptorBase_onError_hookCalled() {
            // Given: Interceptor configured to throw exception during validation
            TestJdbcInterceptor interceptor = new TestJdbcInterceptor();
            interceptor.setThrowOnValidation(true);
            String testSql = "SELECT * FROM users";

            // When: Intercept SQL (should catch exception internally)
            interceptor.interceptSql(testSql);

            // Then: onError should have been called
            assertThat(interceptor.isOnErrorCalled()).isTrue();
            assertThat(interceptor.getLastException()).isNotNull();
        }

        @Test
        @DisplayName("3.1.9 - JdbcInterceptorBase buildSqlContext creates correct context")
        void testJdbcInterceptorBase_buildSqlContext_createsCorrectContext() {
            // Given: Test interceptor
            TestJdbcInterceptor interceptor = new TestJdbcInterceptor();
            String testSql = "DELETE FROM users WHERE id = 1";
            Object[] params = new Object[]{1};

            // When: Intercept SQL with params
            interceptor.interceptSql(testSql, params);

            // Then: SqlContext should be correctly built
            SqlContext context = interceptor.getLastSqlContext();
            assertThat(context).isNotNull();
            assertThat(context.getSql()).isEqualTo(testSql);
        }

        @Test
        @DisplayName("3.1.10 - JdbcInterceptorBase is thread-safe (stateless)")
        void testJdbcInterceptorBase_isThreadSafe() throws InterruptedException {
            // Given: Shared interceptor instance
            TestJdbcInterceptor interceptor = new TestJdbcInterceptor();
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            boolean[] results = new boolean[threadCount];

            // When: Multiple threads use the interceptor concurrently
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> {
                    String sql = "SELECT * FROM users WHERE id = " + idx;
                    interceptor.interceptSql(sql);
                    results[idx] = true;
                });
            }

            for (Thread t : threads) {
                t.start();
            }

            for (Thread t : threads) {
                t.join(5000); // Wait max 5 seconds
            }

            // Then: All threads should complete successfully
            for (int i = 0; i < threadCount; i++) {
                assertThat(results[i])
                    .as("Thread %d should complete", i)
                    .isTrue();
            }
        }

        @Test
        @DisplayName("3.1.11 - JdbcInterceptorBase handles null SQL gracefully")
        void testJdbcInterceptorBase_handlesNullSql() {
            // Given: Test interceptor
            TestJdbcInterceptor interceptor = new TestJdbcInterceptor();

            // When: Intercept null SQL
            interceptor.interceptSql(null);

            // Then: Should not throw, onError may be called
            assertThat(interceptor.isOnErrorCalled() || interceptor.isBeforeValidationCalled())
                .isTrue();
        }

        @Test
        @DisplayName("3.1.12 - JdbcInterceptorBase handles empty SQL gracefully")
        void testJdbcInterceptorBase_handlesEmptySql() {
            // Given: Test interceptor
            TestJdbcInterceptor interceptor = new TestJdbcInterceptor();

            // When: Intercept empty SQL
            interceptor.interceptSql("");

            // Then: Should not throw
            assertThat(interceptor.isBeforeValidationCalled()).isTrue();
        }
    }

    // ========== JdbcInterceptorConfig Tests (1 test) ==========

    @Nested
    @DisplayName("JdbcInterceptorConfig Interface Tests")
    class JdbcInterceptorConfigTests {

        @Test
        @DisplayName("3.1.9 - JdbcInterceptorConfig interface defines all required properties")
        void testJdbcInterceptorConfig_interface_definesAllProperties() {
            // Given: A test implementation of the config interface
            JdbcInterceptorConfig config = new TestJdbcInterceptorConfig();

            // Then: All interface methods should be accessible
            assertThat(config.isEnabled()).isIn(true, false);
            assertThat(config.getStrategy()).isNotNull();
            assertThat(config.isAuditEnabled()).isIn(true, false);
            assertThat(config.getExcludePatterns()).isNotNull();
            
            // Verify interface defines expected methods via reflection
            Class<JdbcInterceptorConfig> configClass = JdbcInterceptorConfig.class;
            assertThat(configClass.isInterface()).isTrue();
            assertThat(configClass.getDeclaredMethods()).hasSizeGreaterThanOrEqualTo(4);
        }
    }

    // ========== SqlContextBuilder Tests (1 test) ==========

    @Nested
    @DisplayName("SqlContextBuilder Utility Tests")
    class SqlContextBuilderTests {

        @Test
        @DisplayName("3.1.10 - SqlContextBuilder builds context correctly for JDBC")
        void testSqlContextBuilder_jdbc_buildsCorrectly() {
            // Given: JDBC metadata
            String sql = "UPDATE users SET name = ? WHERE id = ?";
            Object[] params = new Object[]{"John", 1};
            String datasourceName = "masterDB";

            // When: Build context using builder
            SqlContext context = SqlContextBuilder.buildContext(sql, params, datasourceName);

            // Then: Context should be correctly populated
            assertThat(context).isNotNull();
            assertThat(context.getSql()).isEqualTo(sql);
            assertThat(context.getDatasource()).isEqualTo(datasourceName);
            assertThat(context.getType()).isEqualTo(SqlCommandType.UPDATE);
        }
    }

    // ========== JdbcAuditEventBuilder Tests (1 test) ==========

    @Nested
    @DisplayName("JdbcAuditEventBuilder Utility Tests")
    class JdbcAuditEventBuilderTests {

        @Test
        @DisplayName("3.1.11 - JdbcAuditEventBuilder creates event correctly")
        void testJdbcAuditEventBuilder_createsEventCorrectly() {
            // Given: SqlContext and ValidationResult
            SqlContext context = SqlContext.builder()
                .sql("SELECT * FROM users")
                .type(SqlCommandType.SELECT)
                .mapperId("jdbc.test:default")
                .datasource("default")
                .build();

            ValidationResult result = ValidationResult.pass();

            // When: Create audit event
            com.footstone.sqlguard.audit.AuditEvent event = JdbcAuditEventBuilder.createEvent(context, result);

            // Then: Event should be correctly populated
            assertThat(event).isNotNull();
            assertThat(event.getSql()).isEqualTo(context.getSql());
            assertThat(event.getMapperId()).isEqualTo(context.getMapperId());
            assertThat(event.getDatasource()).isEqualTo(context.getDatasource());
            assertThat(event.getTimestamp()).isNotNull();
            assertThat(event.getSqlType()).isEqualTo(SqlCommandType.SELECT);
        }
    }

    // ========== Package Structure Tests (1 test) ==========

    @Nested
    @DisplayName("Package Structure Tests")
    class PackageStructureTests {

        @Test
        @DisplayName("3.1.12 - Package structure is correct")
        void testPackageStructure_common_isCorrect() {
            // Verify ViolationStrategy is in correct package
            String violationStrategyPackage = ViolationStrategy.class.getPackage().getName();
            assertThat(violationStrategyPackage)
                .isEqualTo("com.footstone.sqlguard.interceptor.jdbc.common");

            // Verify JdbcInterceptorBase is in correct package
            String interceptorBasePackage = JdbcInterceptorBase.class.getPackage().getName();
            assertThat(interceptorBasePackage)
                .isEqualTo("com.footstone.sqlguard.interceptor.jdbc.common");

            // Verify JdbcInterceptorConfig is in correct package
            String interceptorConfigPackage = JdbcInterceptorConfig.class.getPackage().getName();
            assertThat(interceptorConfigPackage)
                .isEqualTo("com.footstone.sqlguard.interceptor.jdbc.common");

            // Verify SqlContextBuilder is in correct package
            String contextBuilderPackage = SqlContextBuilder.class.getPackage().getName();
            assertThat(contextBuilderPackage)
                .isEqualTo("com.footstone.sqlguard.interceptor.jdbc.common");

            // Verify JdbcAuditEventBuilder is in correct package
            String auditEventBuilderPackage = JdbcAuditEventBuilder.class.getPackage().getName();
            assertThat(auditEventBuilderPackage)
                .isEqualTo("com.footstone.sqlguard.interceptor.jdbc.common");
        }
    }

    // ========== Test Helper Classes ==========

    /**
     * Test implementation of JdbcInterceptorBase for testing template method pattern.
     */
    private static class TestJdbcInterceptor extends JdbcInterceptorBase {
        
        private final java.util.List<String> invocationOrder = new java.util.ArrayList<>();
        private boolean beforeValidationCalled = false;
        private boolean afterValidationCalled = false;
        private boolean onErrorCalled = false;
        private String lastSql;
        private SqlContext lastSqlContext;
        private ValidationResult lastValidationResult;
        private Exception lastException;
        private boolean throwOnValidation = false;

        @Override
        protected SqlContext buildSqlContext(String sql, Object... params) {
            invocationOrder.add("buildSqlContext");
            // Handle null/empty SQL gracefully for testing
            if (sql == null || sql.trim().isEmpty()) {
                lastSqlContext = SqlContext.builder()
                    .sql("UNKNOWN")
                    .type(SqlCommandType.UNKNOWN)
                    .mapperId("test.method")
                    .datasource("testdb")
                    .build();
            } else {
                lastSqlContext = SqlContext.builder()
                    .sql(sql)
                    .type(SqlCommandType.SELECT)
                    .mapperId("test.method")
                    .datasource("testdb")
                    .build();
            }
            return lastSqlContext;
        }

        @Override
        protected ValidationResult validate(SqlContext context) {
            invocationOrder.add("validate");
            if (throwOnValidation) {
                throw new RuntimeException("Test validation exception");
            }
            lastValidationResult = ValidationResult.pass();
            return lastValidationResult;
        }

        @Override
        protected void handleViolation(ValidationResult result) {
            invocationOrder.add("handleViolation");
        }

        @Override
        protected void beforeValidation(String sql, Object... params) {
            invocationOrder.add("beforeValidation");
            beforeValidationCalled = true;
            lastSql = sql;
        }

        @Override
        protected void afterValidation(ValidationResult result) {
            invocationOrder.add("afterValidation");
            afterValidationCalled = true;
            lastValidationResult = result;
        }

        @Override
        protected void onError(Exception e) {
            onErrorCalled = true;
            lastException = e;
        }

        // Getters for test assertions
        public java.util.List<String> getInvocationOrder() { return invocationOrder; }
        public boolean isBeforeValidationCalled() { return beforeValidationCalled; }
        public boolean isAfterValidationCalled() { return afterValidationCalled; }
        public boolean isOnErrorCalled() { return onErrorCalled; }
        public String getLastSql() { return lastSql; }
        public SqlContext getLastSqlContext() { return lastSqlContext; }
        public ValidationResult getLastValidationResult() { return lastValidationResult; }
        public Exception getLastException() { return lastException; }
        public void setThrowOnValidation(boolean throwOnValidation) { this.throwOnValidation = throwOnValidation; }
    }

    /**
     * Test implementation of JdbcInterceptorConfig for testing interface contract.
     */
    private static class TestJdbcInterceptorConfig implements JdbcInterceptorConfig {

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public ViolationStrategy getStrategy() {
            return ViolationStrategy.WARN;
        }

        @Override
        public boolean isAuditEnabled() {
            return true;
        }

        @Override
        public java.util.List<String> getExcludePatterns() {
            return Collections.emptyList();
        }
    }
}
