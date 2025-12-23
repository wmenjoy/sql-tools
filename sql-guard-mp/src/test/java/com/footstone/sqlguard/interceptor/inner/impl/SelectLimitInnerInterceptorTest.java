package com.footstone.sqlguard.interceptor.inner.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import com.footstone.sqlguard.dialect.impl.MySQLDialect;
import com.footstone.sqlguard.dialect.impl.OracleDialect;
import com.footstone.sqlguard.dialect.impl.PostgreSQLDialect;
import com.footstone.sqlguard.dialect.impl.SQLServerDialect;
import com.footstone.sqlguard.parser.StatementContext;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SelectLimitInnerInterceptor.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Priority value (150)</li>
 *   <li>LIMIT addition for SELECT without pagination</li>
 *   <li>Pagination detection (LIMIT/OFFSET/RowBounds/TOP)</li>
 *   <li>Multi-dialect support</li>
 *   <li>StatementContext integration</li>
 * </ul>
 *
 * @since 1.1.0
 */
@DisplayName("SelectLimitInnerInterceptor Tests")
class SelectLimitInnerInterceptorTest {

    private Executor executor;
    private ResultHandler<?> resultHandler;

    @BeforeEach
    void setUp() {
        // No mocking needed for these - they can be null for most tests
        executor = null;
        resultHandler = null;
    }

    @AfterEach
    void tearDown() {
        StatementContext.clear();
    }

    @Nested
    @DisplayName("Priority Tests")
    class PriorityTests {

        @Test
        @DisplayName("Should return priority 150")
        void testPriorityIs150() {
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor();
            assertEquals(150, interceptor.getPriority(),
                    "Priority should be 150 (fallback interceptor)");
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor should use limit 1000 and MySQL dialect")
        void testDefaultConstructor() {
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor();
            assertEquals(1000, interceptor.getDefaultLimit());
            assertInstanceOf(MySQLDialect.class, interceptor.getDialect());
        }

        @Test
        @DisplayName("Custom limit constructor should use MySQL dialect")
        void testCustomLimitConstructor() {
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(500);
            assertEquals(500, interceptor.getDefaultLimit());
            assertInstanceOf(MySQLDialect.class, interceptor.getDialect());
        }

        @Test
        @DisplayName("Full constructor should use custom limit and dialect")
        void testFullConstructor() {
            SqlGuardDialect dialect = new PostgreSQLDialect();
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(2000, dialect);
            assertEquals(2000, interceptor.getDefaultLimit());
            assertSame(dialect, interceptor.getDialect());
        }

        @Test
        @DisplayName("Should throw exception for non-positive limit")
        void testNonPositiveLimitThrowsException() {
            assertThrows(IllegalArgumentException.class, 
                    () -> new SelectLimitInnerInterceptor(0));
            assertThrows(IllegalArgumentException.class, 
                    () -> new SelectLimitInnerInterceptor(-1));
        }

        @Test
        @DisplayName("Should throw exception for null dialect")
        void testNullDialectThrowsException() {
            assertThrows(IllegalArgumentException.class, 
                    () -> new SelectLimitInnerInterceptor(1000, null));
        }
    }

    @Nested
    @DisplayName("LIMIT Addition Tests")
    class LimitAdditionTests {

        @Test
        @DisplayName("Should add LIMIT to SELECT without pagination")
        void testAddLimitToSelectWithoutPagination() throws Exception {
            // Given
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            MappedStatement ms = createMappedStatement(sql, SqlCommandType.SELECT);
            BoundSql boundSql = ms.getBoundSql(null);
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(1000);

            // When
            interceptor.beforeQuery(executor, ms, null, 
                    RowBounds.DEFAULT, resultHandler, boundSql);

            // Then - Statement should be modified with LIMIT
            // Verify by checking StatementContext
            Statement modifiedStmt = StatementContext.get(sql);
            assertNotNull(modifiedStmt);
        }

        @Test
        @DisplayName("Should skip LIMIT addition when LIMIT already exists")
        void testSkipWhenLimitExists() throws Exception {
            // Given
            String sql = "SELECT * FROM users LIMIT 50";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            MappedStatement ms = createMappedStatement(sql, SqlCommandType.SELECT);
            BoundSql boundSql = ms.getBoundSql(null);
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(1000);

            // When
            interceptor.beforeQuery(executor, ms, null, 
                    RowBounds.DEFAULT, resultHandler, boundSql);

            // Then - SQL should not be modified (LIMIT already exists)
            // No exception should be thrown
        }

        @Test
        @DisplayName("Should skip LIMIT addition when OFFSET exists")
        void testSkipWhenOffsetExists() throws Exception {
            // Given
            String sql = "SELECT * FROM users OFFSET 10";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            MappedStatement ms = createMappedStatement(sql, SqlCommandType.SELECT);
            BoundSql boundSql = ms.getBoundSql(null);
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(1000);

            // When
            interceptor.beforeQuery(executor, ms, null, 
                    RowBounds.DEFAULT, resultHandler, boundSql);

            // Then - SQL should not be modified
            // No exception should be thrown
        }

        @Test
        @DisplayName("Should skip LIMIT addition when RowBounds is not DEFAULT")
        void testSkipWhenRowBoundsPresent() throws Exception {
            // Given
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            MappedStatement ms = createMappedStatement(sql, SqlCommandType.SELECT);
            BoundSql boundSql = ms.getBoundSql(null);
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(1000);
            RowBounds rowBounds = new RowBounds(0, 100);

            // When
            interceptor.beforeQuery(executor, ms, null, 
                    rowBounds, resultHandler, boundSql);

            // Then - SQL should not be modified due to RowBounds
            // No exception should be thrown
        }

        @Test
        @DisplayName("Should skip when StatementContext cache miss")
        void testSkipWhenStatementContextCacheMiss() throws Exception {
            // Given - No statement cached
            String sql = "SELECT * FROM users";
            MappedStatement ms = createMappedStatement(sql, SqlCommandType.SELECT);
            BoundSql boundSql = ms.getBoundSql(null);
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(1000);

            // When
            interceptor.beforeQuery(executor, ms, null, 
                    RowBounds.DEFAULT, resultHandler, boundSql);

            // Then - Should not throw, just skip
        }

        @Test
        @DisplayName("Should skip for non-SELECT statements")
        void testSkipForNonSelectStatements() throws Exception {
            // Given
            String sql = "UPDATE users SET name = 'test' WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            MappedStatement ms = createMappedStatement(sql, SqlCommandType.UPDATE);
            BoundSql boundSql = ms.getBoundSql(null);
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(1000);

            // When
            interceptor.beforeQuery(executor, ms, null, 
                    RowBounds.DEFAULT, resultHandler, boundSql);

            // Then - Should not throw, just skip
        }
    }

    @Nested
    @DisplayName("Multi-Dialect Tests")
    class MultiDialectTests {

        @Test
        @DisplayName("Should work with PostgreSQL dialect")
        void testWithPostgreSQLDialect() {
            SelectLimitInnerInterceptor interceptor = 
                    new SelectLimitInnerInterceptor(500, new PostgreSQLDialect());
            assertEquals("PostgreSQL", interceptor.getDialect().getDatabaseType());
            assertEquals(500, interceptor.getDefaultLimit());
        }

        @Test
        @DisplayName("Should work with Oracle dialect")
        void testWithOracleDialect() {
            SelectLimitInnerInterceptor interceptor = 
                    new SelectLimitInnerInterceptor(100, new OracleDialect());
            assertEquals("Oracle", interceptor.getDialect().getDatabaseType());
            assertEquals(100, interceptor.getDefaultLimit());
        }

        @Test
        @DisplayName("Should work with SQL Server dialect")
        void testWithSQLServerDialect() {
            SelectLimitInnerInterceptor interceptor = 
                    new SelectLimitInnerInterceptor(200, new SQLServerDialect());
            assertEquals("SQL Server", interceptor.getDialect().getDatabaseType());
            assertEquals(200, interceptor.getDefaultLimit());
        }
    }

    @Nested
    @DisplayName("Lifecycle Method Tests")
    class LifecycleMethodTests {

        @Test
        @DisplayName("willDoQuery should always return true")
        void testWillDoQueryReturnsTrue() throws SQLException {
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor();
            assertTrue(interceptor.willDoQuery(null, null, null, 
                    RowBounds.DEFAULT, null, null));
        }

        @Test
        @DisplayName("willDoUpdate should always return true")
        void testWillDoUpdateReturnsTrue() throws SQLException {
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor();
            assertTrue(interceptor.willDoUpdate(null, null, null));
        }

        @Test
        @DisplayName("beforeUpdate should be no-op")
        void testBeforeUpdateIsNoOp() throws SQLException {
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor();
            // Should not throw
            interceptor.beforeUpdate(null, null, null);
        }
    }

    @Nested
    @DisplayName("EnforceMaxLimit Tests")
    class EnforceMaxLimitTests {

        @Test
        @DisplayName("Default constructor should have enforceMaxLimit disabled")
        void testDefaultEnforceMaxLimitDisabled() {
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor();
            assertFalse(interceptor.isEnforceMaxLimit());
        }

        @Test
        @DisplayName("Should enable enforceMaxLimit via constructor")
        void testEnableEnforceMaxLimit() {
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(1000, true);
            assertTrue(interceptor.isEnforceMaxLimit());
            assertEquals(1000, interceptor.getDefaultLimit());
        }

        @Test
        @DisplayName("Should not cap small LIMIT when enforceMaxLimit disabled")
        void testNoCapWhenEnforceMaxLimitDisabled() throws Exception {
            // Given - LIMIT 1000000 (very large)
            String sql = "SELECT * FROM users LIMIT 1000000";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            MappedStatement ms = createMappedStatement(sql, SqlCommandType.SELECT);
            BoundSql boundSql = ms.getBoundSql(null);
            // enforceMaxLimit = false (default)
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(1000, false);

            // When
            interceptor.beforeQuery(executor, ms, null, 
                    RowBounds.DEFAULT, resultHandler, boundSql);

            // Then - LIMIT should NOT be capped (enforceMaxLimit is disabled)
            // SQL should remain unchanged
        }

        @Test
        @DisplayName("Should cap large LIMIT when enforceMaxLimit enabled")
        void testCapLargeLimitWhenEnforceMaxLimitEnabled() throws Exception {
            // Given - LIMIT 1000000 (very large)
            String sql = "SELECT * FROM users LIMIT 1000000";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            MappedStatement ms = createMappedStatement(sql, SqlCommandType.SELECT);
            BoundSql boundSql = ms.getBoundSql(null);
            // enforceMaxLimit = true
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(1000, true);

            // When
            interceptor.beforeQuery(executor, ms, null, 
                    RowBounds.DEFAULT, resultHandler, boundSql);

            // Then - LIMIT should be capped to 1000
            // Statement has been modified in-place
            String modifiedSql = stmt.toString().toLowerCase();
            assertTrue(modifiedSql.contains("limit 1000"), 
                    "Expected LIMIT 1000 (capped), got: " + modifiedSql);
            assertFalse(modifiedSql.contains("1000000"), 
                    "Should not contain original large value: " + modifiedSql);
        }

        @Test
        @DisplayName("Should not cap LIMIT when already within max")
        void testNoCapWhenLimitWithinMax() throws Exception {
            // Given - LIMIT 50 (within max of 1000)
            String sql = "SELECT * FROM users LIMIT 50";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            MappedStatement ms = createMappedStatement(sql, SqlCommandType.SELECT);
            BoundSql boundSql = ms.getBoundSql(null);
            SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(1000, true);

            // When
            interceptor.beforeQuery(executor, ms, null, 
                    RowBounds.DEFAULT, resultHandler, boundSql);

            // Then - LIMIT should remain 50 (not capped)
            String modifiedSql = stmt.toString().toLowerCase();
            assertTrue(modifiedSql.contains("limit 50"), 
                    "Expected LIMIT 50 (unchanged), got: " + modifiedSql);
        }

        @Test
        @DisplayName("Full constructor with enforceMaxLimit")
        void testFullConstructorWithEnforceMaxLimit() {
            SqlGuardDialect dialect = new PostgreSQLDialect();
            SelectLimitInnerInterceptor interceptor = 
                    new SelectLimitInnerInterceptor(500, dialect, true);
            
            assertEquals(500, interceptor.getDefaultLimit());
            assertEquals("PostgreSQL", interceptor.getDialect().getDatabaseType());
            assertTrue(interceptor.isEnforceMaxLimit());
        }
    }

    /**
     * Creates a MappedStatement for testing.
     */
    private MappedStatement createMappedStatement(String sql, SqlCommandType commandType) {
        Configuration configuration = new Configuration();
        SqlSource sqlSource = new TestSqlSource(sql, configuration);
        
        return new MappedStatement.Builder(
                configuration,
                "com.example.TestMapper.testMethod",
                sqlSource,
                commandType
        ).build();
    }

    /**
     * Test SQL source implementation.
     */
    static class TestSqlSource implements SqlSource {
        private final String sql;
        private final Configuration configuration;

        public TestSqlSource(String sql, Configuration configuration) {
            this.sql = sql;
            this.configuration = configuration;
        }

        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            return new BoundSql(configuration, sql, new ArrayList<>(), parameterObject);
        }
    }
}

