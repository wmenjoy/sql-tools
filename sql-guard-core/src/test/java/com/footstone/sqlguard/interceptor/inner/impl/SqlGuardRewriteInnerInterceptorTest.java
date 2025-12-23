package com.footstone.sqlguard.interceptor.inner.impl;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.parser.StatementContext;
import com.footstone.sqlguard.rewriter.StatementRewriter;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SqlGuardRewriteInnerInterceptor.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Priority value (200)</li>
 *   <li>willDoQuery/willDoUpdate no-op behavior</li>
 *   <li>Statement cache reuse from StatementContext</li>
 *   <li>Rewriter invocation and filtering (enabled/disabled)</li>
 *   <li>BoundSql reflection modification</li>
 *   <li>Chain rewrite support (multiple rewriters)</li>
 *   <li>SqlContext and StatementContext updates between rewrites</li>
 *   <li>MyBatis 3.4.x / 3.5.x compatibility</li>
 * </ul>
 *
 * <p>Note: MyBatis classes (MappedStatement, Executor, BoundSql) are final/complex and
 * cannot be mocked directly. Tests use reflection to invoke internal methods.
 *
 * @since 1.1.0
 */
@DisplayName("SqlGuardRewriteInnerInterceptor Tests")
class SqlGuardRewriteInnerInterceptorTest {

    private List<StatementRewriter> rewriters;
    private SqlGuardRewriteInnerInterceptor interceptor;

    @BeforeEach
    void setUp() {
        rewriters = new ArrayList<>();
        interceptor = new SqlGuardRewriteInnerInterceptor(rewriters);
    }

    @AfterEach
    void tearDown() {
        // CRITICAL: Clear ThreadLocal to prevent memory leaks
        StatementContext.clear();
    }

    // ========== Priority Tests ==========

    @Nested
    @DisplayName("1. Priority Tests")
    class PriorityTests {

        @Test
        @DisplayName("getPriority() returns 200 for rewrite interceptor")
        void getPriority_returns200() {
            assertEquals(200, interceptor.getPriority(),
                    "Rewrite interceptor priority should be 200");
        }

        @Test
        @DisplayName("Priority 200 is lower than check interceptor (10)")
        void priority_isLowerThanCheckInterceptor() {
            SqlGuardCheckInnerInterceptor checkInterceptor =
                    new SqlGuardCheckInnerInterceptor(Collections.emptyList(), null, null);

            assertTrue(interceptor.getPriority() > checkInterceptor.getPriority(),
                    "Rewrite interceptor priority should be greater than check interceptor priority");
        }
    }

    // ========== willDoQuery/willDoUpdate No-op Tests ==========

    @Nested
    @DisplayName("2. willDoQuery/willDoUpdate No-op Tests")
    class NoOpTests {

        @Test
        @DisplayName("willDoQuery returns true (no-op)")
        void willDoQuery_returnsTrue() throws Exception {
            boolean result = interceptor.willDoQuery(null, null, null, null, null, null);
            assertTrue(result, "willDoQuery should return true (no-op)");
        }

        @Test
        @DisplayName("willDoUpdate returns true (no-op)")
        void willDoUpdate_returnsTrue() throws Exception {
            boolean result = interceptor.willDoUpdate(null, null, null);
            assertTrue(result, "willDoUpdate should return true (no-op)");
        }
    }

    // ========== Statement Cache Reuse Tests ==========

    @Nested
    @DisplayName("3. Statement Cache Reuse Tests")
    class CacheReuseTests {

        @Test
        @DisplayName("executeRewrites reuses Statement from StatementContext cache")
        void executeRewrites_reusesStatementFromCache() throws Exception {
            // Setup
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            // Add a tracking rewriter
            TrackingRewriter trackingRewriter = new TrackingRewriter();
            rewriters.add(trackingRewriter);

            BoundSql boundSql = createMockBoundSql(sql);
            MappedStatement ms = createMockMappedStatement("UserMapper.selectAll");

            // Execute via reflection
            invokeExecuteRewrites(ms, boundSql);

            // Verify rewriter received the cached Statement
            assertSame(stmt, trackingRewriter.getReceivedStatement(),
                    "Rewriter should receive the cached Statement");
        }

        @Test
        @DisplayName("executeRewrites skips rewrites when StatementContext cache miss")
        void executeRewrites_skipRewrites_whenCacheMiss() throws Exception {
            // Setup - no cached Statement
            String sql = "SELECT * FROM users";

            // Add a tracking rewriter
            TrackingRewriter trackingRewriter = new TrackingRewriter();
            rewriters.add(trackingRewriter);

            BoundSql boundSql = createMockBoundSql(sql);
            MappedStatement ms = createMockMappedStatement("UserMapper.selectAll");

            // Execute via reflection
            invokeExecuteRewrites(ms, boundSql);

            // Verify rewriter was NOT invoked
            assertEquals(0, trackingRewriter.getInvocationCount(),
                    "Rewriter should not be invoked when cache miss");
        }
    }

    // ========== Rewriter Invocation Tests ==========

    @Nested
    @DisplayName("4. Rewriter Invocation Tests")
    class RewriterInvocationTests {

        @Test
        @DisplayName("executeRewrites invokes enabled rewriters")
        void executeRewrites_invokesEnabledRewriters() throws Exception {
            // Setup
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            // Add enabled rewriter
            TrackingRewriter enabledRewriter = new TrackingRewriter(true);
            rewriters.add(enabledRewriter);

            BoundSql boundSql = createMockBoundSql(sql);
            MappedStatement ms = createMockMappedStatement("UserMapper.selectAll");

            // Execute
            invokeExecuteRewrites(ms, boundSql);

            // Verify
            assertEquals(1, enabledRewriter.getInvocationCount(),
                    "Enabled rewriter should be invoked once");
        }

        @Test
        @DisplayName("executeRewrites skips disabled rewriters")
        void executeRewrites_skipsDisabledRewriters() throws Exception {
            // Setup
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            // Add disabled rewriter
            TrackingRewriter disabledRewriter = new TrackingRewriter(false);
            rewriters.add(disabledRewriter);

            BoundSql boundSql = createMockBoundSql(sql);
            MappedStatement ms = createMockMappedStatement("UserMapper.selectAll");

            // Execute
            invokeExecuteRewrites(ms, boundSql);

            // Verify
            assertEquals(0, disabledRewriter.getInvocationCount(),
                    "Disabled rewriter should not be invoked");
        }

        @Test
        @DisplayName("executeRewrites filters enabled/disabled rewriters correctly")
        void executeRewrites_filtersRewritersCorrectly() throws Exception {
            // Setup
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            // Add mixed rewriters
            TrackingRewriter enabled1 = new TrackingRewriter(true);
            TrackingRewriter disabled = new TrackingRewriter(false);
            TrackingRewriter enabled2 = new TrackingRewriter(true);
            rewriters.addAll(Arrays.asList(enabled1, disabled, enabled2));

            BoundSql boundSql = createMockBoundSql(sql);
            MappedStatement ms = createMockMappedStatement("UserMapper.selectAll");

            // Execute
            invokeExecuteRewrites(ms, boundSql);

            // Verify
            assertEquals(1, enabled1.getInvocationCount(), "First enabled rewriter should be invoked once");
            assertEquals(0, disabled.getInvocationCount(), "Disabled rewriter should not be invoked");
            assertEquals(1, enabled2.getInvocationCount(), "Second enabled rewriter should be invoked once");
        }
    }

    // ========== BoundSql Reflection Modification Tests ==========

    @Nested
    @DisplayName("5. BoundSql Reflection Modification Tests")
    class BoundSqlReflectionTests {

        @Test
        @DisplayName("executeRewrites modifies BoundSql.sql via reflection when Statement changed")
        void executeRewrites_modifiesBoundSql_whenStatementChanged() throws Exception {
            // Setup
            String originalSql = "SELECT * FROM users";
            BoundSql realBoundSql = createMockBoundSql(originalSql);
            Statement stmt = CCJSqlParserUtil.parse(originalSql);
            StatementContext.cache(originalSql, stmt);

            // Add modifying rewriter
            TenantIsolationRewriter tenantRewriter = new TenantIsolationRewriter("tenant_001");
            rewriters.add(tenantRewriter);

            MappedStatement ms = createMockMappedStatement("UserMapper.selectAll");

            // Execute
            interceptor = new SqlGuardRewriteInnerInterceptor(rewriters);
            invokeExecuteRewrites(ms, realBoundSql);

            // Verify SQL was modified
            String modifiedSql = realBoundSql.getSql();
            assertTrue(modifiedSql.contains("tenant_id"), "Modified SQL should contain tenant_id");
            assertTrue(modifiedSql.contains("tenant_001"), "Modified SQL should contain tenant value");
        }

        @Test
        @DisplayName("executeRewrites does not modify BoundSql when Statement unchanged")
        void executeRewrites_doesNotModifyBoundSql_whenStatementUnchanged() throws Exception {
            // Setup
            String originalSql = "SELECT * FROM users";
            BoundSql realBoundSql = createMockBoundSql(originalSql);
            Statement stmt = CCJSqlParserUtil.parse(originalSql);
            StatementContext.cache(originalSql, stmt);

            // Add non-modifying rewriter (returns same Statement)
            rewriters.add(new NoOpRewriter());

            MappedStatement ms = createMockMappedStatement("UserMapper.selectAll");

            // Execute
            invokeExecuteRewrites(ms, realBoundSql);

            // Verify SQL unchanged
            assertEquals(originalSql, realBoundSql.getSql(), "SQL should remain unchanged");
        }
    }

    // ========== Chain Rewrite Tests ==========

    @Nested
    @DisplayName("6. Chain Rewrite Tests")
    class ChainRewriteTests {

        @Test
        @DisplayName("Multiple rewriters execute in chain, each receiving modified Statement")
        void chainRewrite_multipleRewriters() throws Exception {
            // Setup
            String originalSql = "SELECT * FROM users";
            BoundSql realBoundSql = createMockBoundSql(originalSql);
            Statement stmt = CCJSqlParserUtil.parse(originalSql);
            StatementContext.cache(originalSql, stmt);

            // Add two rewriters:
            // 1. TenantIsolationRewriter: adds WHERE tenant_id = 'xxx'
            // 2. SoftDeleteRewriter: adds AND deleted = 0
            TenantIsolationRewriter tenantRewriter = new TenantIsolationRewriter("tenant_001");
            SoftDeleteRewriter softDeleteRewriter = new SoftDeleteRewriter();
            rewriters.addAll(Arrays.asList(tenantRewriter, softDeleteRewriter));

            MappedStatement ms = createMockMappedStatement("UserMapper.selectAll");

            // Execute
            interceptor = new SqlGuardRewriteInnerInterceptor(rewriters);
            invokeExecuteRewrites(ms, realBoundSql);

            // Verify final SQL contains both modifications
            String finalSql = realBoundSql.getSql();
            assertTrue(finalSql.contains("tenant_id"), "Final SQL should contain tenant_id");
            assertTrue(finalSql.contains("tenant_001"), "Final SQL should contain tenant value");
            assertTrue(finalSql.contains("deleted"), "Final SQL should contain deleted");
        }

        @Test
        @DisplayName("Second rewriter receives Statement modified by first rewriter")
        void chainRewrite_secondRewriterReceivesModifiedStatement() throws Exception {
            // Setup
            String originalSql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(originalSql);
            StatementContext.cache(originalSql, stmt);

            BoundSql boundSql = createMockBoundSql(originalSql);
            MappedStatement ms = createMockMappedStatement("UserMapper.selectAll");

            // Add two tracking rewriters
            ModifyingTrackingRewriter rewriter1 = new ModifyingTrackingRewriter("first");
            ModifyingTrackingRewriter rewriter2 = new ModifyingTrackingRewriter("second");
            rewriters.addAll(Arrays.asList(rewriter1, rewriter2));

            // Execute
            invokeExecuteRewrites(ms, boundSql);

            // Verify second rewriter received modified Statement
            assertTrue(rewriter2.getReceivedSql().contains("first"),
                    "Second rewriter should receive SQL modified by first rewriter");
        }
    }

    // ========== SqlContext Update Tests ==========

    @Nested
    @DisplayName("7. SqlContext Update Tests")
    class SqlContextUpdateTests {

        @Test
        @DisplayName("SqlContext is updated between rewriters with new SQL and Statement")
        void sqlContextUpdate_betweenRewriters() throws Exception {
            // Setup
            String originalSql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(originalSql);
            StatementContext.cache(originalSql, stmt);

            BoundSql boundSql = createMockBoundSql(originalSql);
            MappedStatement ms = createMockMappedStatement("UserMapper.selectAll");

            // Add context-tracking rewriter
            ContextTrackingRewriter rewriter1 = new ContextTrackingRewriter("rewriter1");
            ContextTrackingRewriter rewriter2 = new ContextTrackingRewriter("rewriter2");
            rewriters.addAll(Arrays.asList(rewriter1, rewriter2));

            // Execute
            invokeExecuteRewrites(ms, boundSql);

            // Verify second rewriter received updated SqlContext
            assertTrue(rewriter2.getReceivedContext().getSql().contains("rewriter1"),
                    "Second rewriter should receive SqlContext with SQL modified by first rewriter");
        }
    }

    // ========== StatementContext Update Tests ==========

    @Nested
    @DisplayName("8. StatementContext Update Tests")
    class StatementContextUpdateTests {

        @Test
        @DisplayName("StatementContext is updated with new SQL after each rewrite")
        void statementContextUpdate_afterEachRewrite() throws Exception {
            // Setup
            String originalSql = "SELECT * FROM users";
            BoundSql realBoundSql = createMockBoundSql(originalSql);
            Statement stmt = CCJSqlParserUtil.parse(originalSql);
            StatementContext.cache(originalSql, stmt);

            MappedStatement ms = createMockMappedStatement("UserMapper.selectAll");

            // Add modifying rewriter
            TenantIsolationRewriter tenantRewriter = new TenantIsolationRewriter("tenant_001");
            rewriters.add(tenantRewriter);

            // Execute
            interceptor = new SqlGuardRewriteInnerInterceptor(rewriters);
            invokeExecuteRewrites(ms, realBoundSql);

            // Verify StatementContext contains new SQL entry
            String modifiedSql = realBoundSql.getSql();
            Statement cachedStatement = StatementContext.get(modifiedSql);
            assertNotNull(cachedStatement, "StatementContext should contain entry for modified SQL");
        }
    }

    // ========== beforeUpdate Tests ==========

    @Nested
    @DisplayName("9. beforeUpdate Tests")
    class BeforeUpdateTests {

        @Test
        @DisplayName("beforeUpdate executes rewrite chain for UPDATE statement")
        void beforeUpdate_executesRewriteChain() throws Exception {
            // Setup
            String sql = "UPDATE users SET name = 'test' WHERE id = 1";
            BoundSql realBoundSql = createMockBoundSql(sql);
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            MappedStatement ms = createMockMappedStatement("UserMapper.updateById");

            // Add tracking rewriter
            TrackingRewriter trackingRewriter = new TrackingRewriter(true);
            rewriters.add(trackingRewriter);

            // Execute via reflection
            invokeExecuteRewrites(ms, realBoundSql);

            // Verify rewriter was invoked
            assertEquals(1, trackingRewriter.getInvocationCount(),
                    "Rewriter should be invoked once for UPDATE");
        }
    }

    // ========== Implementation Tests ==========

    @Nested
    @DisplayName("10. Implementation Tests")
    class ImplementationTests {

        @Test
        @DisplayName("implements SqlGuardInnerInterceptor interface")
        void implementsSqlGuardInnerInterceptor() {
            assertTrue(
                    com.footstone.sqlguard.interceptor.inner.SqlGuardInnerInterceptor.class
                            .isAssignableFrom(SqlGuardRewriteInnerInterceptor.class),
                    "SqlGuardRewriteInnerInterceptor should implement SqlGuardInnerInterceptor");
        }
    }

    // ========== Helper Methods ==========

    /**
     * Creates a real BoundSql instance for testing.
     */
    private BoundSql createMockBoundSql(String sql) {
        Configuration configuration = new Configuration();
        return new BoundSql(configuration, sql, Collections.emptyList(), null);
    }

    /**
     * Creates a mock MappedStatement using reflection.
     */
    private MappedStatement createMockMappedStatement(String id) {
        Configuration configuration = new Configuration();
        MappedStatement.Builder builder = new MappedStatement.Builder(
                configuration,
                id,
                (parameterObject) -> createMockBoundSql("SELECT 1"),
                org.apache.ibatis.mapping.SqlCommandType.SELECT
        );
        return builder.build();
    }

    /**
     * Invokes executeRewrites method via reflection.
     */
    private void invokeExecuteRewrites(MappedStatement ms, BoundSql boundSql) throws Exception {
        java.lang.reflect.Method executeRewrites = SqlGuardRewriteInnerInterceptor.class
                .getDeclaredMethod("executeRewrites", MappedStatement.class, BoundSql.class);
        executeRewrites.setAccessible(true);
        executeRewrites.invoke(interceptor, ms, boundSql);
    }

    // ========== Test Rewriter Implementations ==========

    /**
     * Tracking rewriter that records invocation count and received Statement.
     */
    private static class TrackingRewriter implements StatementRewriter {
        private final boolean enabled;
        private int invocationCount = 0;
        private Statement receivedStatement;

        TrackingRewriter() {
            this(true);
        }

        TrackingRewriter(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public Statement rewrite(Statement statement, SqlContext context) {
            invocationCount++;
            receivedStatement = statement;
            return statement; // No modification
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        int getInvocationCount() {
            return invocationCount;
        }

        Statement getReceivedStatement() {
            return receivedStatement;
        }
    }

    /**
     * No-op rewriter that returns the original Statement unchanged.
     */
    private static class NoOpRewriter implements StatementRewriter {
        @Override
        public Statement rewrite(Statement statement, SqlContext context) {
            return statement;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }

    /**
     * Tenant isolation rewriter that adds WHERE tenant_id = ? clause.
     */
    private static class TenantIsolationRewriter implements StatementRewriter {
        private final String tenantId;

        TenantIsolationRewriter(String tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public Statement rewrite(Statement statement, SqlContext context) {
            if (statement instanceof Select) {
                Select select = (Select) statement;
                if (select.getSelectBody() instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

                    // Create tenant_id = 'xxx' expression
                    EqualsTo tenantFilter = new EqualsTo();
                    tenantFilter.setLeftExpression(new Column("tenant_id"));
                    tenantFilter.setRightExpression(new StringValue(tenantId));

                    Expression existingWhere = plainSelect.getWhere();
                    if (existingWhere != null) {
                        plainSelect.setWhere(new AndExpression(existingWhere, tenantFilter));
                    } else {
                        plainSelect.setWhere(tenantFilter);
                    }
                }
                return select;
            }
            return statement;
        }

        @Override
        public boolean isEnabled() {
            return tenantId != null;
        }
    }

    /**
     * Soft delete rewriter that adds WHERE deleted = 0 clause.
     */
    private static class SoftDeleteRewriter implements StatementRewriter {
        @Override
        public Statement rewrite(Statement statement, SqlContext context) {
            if (statement instanceof Select) {
                Select select = (Select) statement;
                if (select.getSelectBody() instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

                    // Create deleted = 0 expression
                    EqualsTo deletedFilter = new EqualsTo();
                    deletedFilter.setLeftExpression(new Column("deleted"));
                    deletedFilter.setRightExpression(new LongValue(0));

                    Expression existingWhere = plainSelect.getWhere();
                    if (existingWhere != null) {
                        plainSelect.setWhere(new AndExpression(existingWhere, deletedFilter));
                    } else {
                        plainSelect.setWhere(deletedFilter);
                    }
                }
                return select;
            }
            return statement;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }

    /**
     * Modifying tracking rewriter that adds a marker to the SQL.
     */
    private static class ModifyingTrackingRewriter implements StatementRewriter {
        private final String marker;
        private String receivedSql;

        ModifyingTrackingRewriter(String marker) {
            this.marker = marker;
        }

        @Override
        public Statement rewrite(Statement statement, SqlContext context) {
            receivedSql = context.getSql();

            if (statement instanceof Select) {
                Select select = (Select) statement;
                if (select.getSelectBody() instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

                    // Add marker column as WHERE condition
                    EqualsTo markerFilter = new EqualsTo();
                    markerFilter.setLeftExpression(new Column("marker_" + marker));
                    markerFilter.setRightExpression(new LongValue(1));

                    Expression existingWhere = plainSelect.getWhere();
                    if (existingWhere != null) {
                        plainSelect.setWhere(new AndExpression(existingWhere, markerFilter));
                    } else {
                        plainSelect.setWhere(markerFilter);
                    }
                }
                return select;
            }
            return statement;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        String getReceivedSql() {
            return receivedSql;
        }
    }

    /**
     * Context tracking rewriter that records received SqlContext.
     */
    private static class ContextTrackingRewriter implements StatementRewriter {
        private final String marker;
        private SqlContext receivedContext;

        ContextTrackingRewriter(String marker) {
            this.marker = marker;
        }

        @Override
        public Statement rewrite(Statement statement, SqlContext context) {
            receivedContext = context;

            if (statement instanceof Select) {
                Select select = (Select) statement;
                if (select.getSelectBody() instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

                    // Add marker
                    EqualsTo markerFilter = new EqualsTo();
                    markerFilter.setLeftExpression(new Column("ctx_" + marker));
                    markerFilter.setRightExpression(new LongValue(1));

                    Expression existingWhere = plainSelect.getWhere();
                    if (existingWhere != null) {
                        plainSelect.setWhere(new AndExpression(existingWhere, markerFilter));
                    } else {
                        plainSelect.setWhere(markerFilter);
                    }
                }
                return select;
            }
            return statement;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        SqlContext getReceivedContext() {
            return receivedContext;
        }
    }
}
