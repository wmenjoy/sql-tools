package com.footstone.sqlguard.interceptor.mybatis;

import com.footstone.sqlguard.interceptor.inner.SqlGuardInnerInterceptor;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.parser.StatementContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD tests for SqlGuardInterceptor.
 */
@DisplayName("SqlGuardInterceptor Tests")
@ExtendWith(MockitoExtension.class)
public class SqlGuardInterceptorTest {

    private SqlGuardInterceptor interceptor;

    // Use real JSqlParserFacade instead of mock (final class cannot be mocked)
    private JSqlParserFacade parserFacade;

    @Mock
    private Executor executor;

    @Mock
    private ResultHandler<?> resultHandler;

    private Configuration configuration;
    private Invocation invocation;

    @BeforeEach
    void setUp() {
        configuration = new Configuration();
        // Use real JSqlParserFacade
        parserFacade = new JSqlParserFacade();
    }

    @AfterEach
    void tearDown() {
        StatementContext.clear();
    }

    /**
     * Helper method to create MappedStatement.
     */
    private MappedStatement createMappedStatement(String sql, String mapperId, SqlCommandType commandType) {
        SqlSource sqlSource = new SqlSource() {
            @Override
            public BoundSql getBoundSql(Object parameterObject) {
                return new BoundSql(configuration, sql, new ArrayList<>(), parameterObject);
            }
        };

        MappedStatement.Builder builder = new MappedStatement.Builder(
                configuration,
                mapperId,
                sqlSource,
                commandType
        );

        return builder.build();
    }

    @Nested
    @DisplayName("1. Interceptor Interface Tests")
    class InterceptorInterfaceTests {

        @Test
        @DisplayName("testInterceptor_implementsMyBatisInterceptor - implements Interceptor")
        public void testInterceptor_implementsMyBatisInterceptor() {
            // Arrange
            List<SqlGuardInnerInterceptor> innerInterceptors = Collections.emptyList();
            interceptor = new SqlGuardInterceptor(innerInterceptors, parserFacade);

            // Assert
            assertTrue(interceptor instanceof org.apache.ibatis.plugin.Interceptor,
                    "Should implement MyBatis Interceptor interface");
        }

        @Test
        @DisplayName("testAnnotation_interceptsExecutorMethods - @Intercepts annotation present")
        public void testAnnotation_interceptsExecutorMethods() {
            // Assert
            assertTrue(SqlGuardInterceptor.class.isAnnotationPresent(Intercepts.class),
                    "Should have @Intercepts annotation");

            Intercepts intercepts = SqlGuardInterceptor.class.getAnnotation(Intercepts.class);
            Signature[] signatures = intercepts.value();

            assertEquals(2, signatures.length, "Should intercept 2 methods");

            // Verify query signature
            boolean hasQuerySignature = Arrays.stream(signatures)
                    .anyMatch(sig -> "query".equals(sig.method()) && sig.type() == Executor.class);
            assertTrue(hasQuerySignature, "Should intercept Executor.query");

            // Verify update signature
            boolean hasUpdateSignature = Arrays.stream(signatures)
                    .anyMatch(sig -> "update".equals(sig.method()) && sig.type() == Executor.class);
            assertTrue(hasUpdateSignature, "Should intercept Executor.update");
        }
    }

    @Nested
    @DisplayName("2. Priority Sorting Tests")
    class PrioritySortingTests {

        @Test
        @DisplayName("testInterceptor_sortsInnerInterceptors_byPriority - sorts by priority ascending")
        public void testInterceptor_sortsInnerInterceptors_byPriority() {
            // Arrange
            SqlGuardInnerInterceptor interceptor1 = mock(SqlGuardInnerInterceptor.class);
            SqlGuardInnerInterceptor interceptor2 = mock(SqlGuardInnerInterceptor.class);
            SqlGuardInnerInterceptor interceptor3 = mock(SqlGuardInnerInterceptor.class);

            when(interceptor1.getPriority()).thenReturn(50);
            when(interceptor2.getPriority()).thenReturn(10);
            when(interceptor3.getPriority()).thenReturn(100);

            List<SqlGuardInnerInterceptor> unsorted = Arrays.asList(interceptor1, interceptor2, interceptor3);

            // Act
            interceptor = new SqlGuardInterceptor(unsorted, parserFacade);
            List<SqlGuardInnerInterceptor> sorted = interceptor.getSortedInterceptors();

            // Assert
            assertEquals(3, sorted.size(), "Should have 3 interceptors");
            assertEquals(10, sorted.get(0).getPriority(), "First should be priority 10");
            assertEquals(50, sorted.get(1).getPriority(), "Second should be priority 50");
            assertEquals(100, sorted.get(2).getPriority(), "Third should be priority 100");
        }

        @Test
        @DisplayName("testPriorityOrder_10_50_100_executesCorrectly - execution order matches priority")
        public void testPriorityOrder_10_50_100_executesCorrectly() throws Throwable {
            // Arrange
            String sql = "SELECT * FROM users";

            SqlGuardInnerInterceptor interceptor10 = mock(SqlGuardInnerInterceptor.class);
            SqlGuardInnerInterceptor interceptor50 = mock(SqlGuardInnerInterceptor.class);
            SqlGuardInnerInterceptor interceptor100 = mock(SqlGuardInnerInterceptor.class);

            when(interceptor10.getPriority()).thenReturn(10);
            when(interceptor50.getPriority()).thenReturn(50);
            when(interceptor100.getPriority()).thenReturn(100);

            when(interceptor10.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(true);
            when(interceptor50.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(true);
            when(interceptor100.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(true);

            List<SqlGuardInnerInterceptor> unsorted = Arrays.asList(interceptor50, interceptor100, interceptor10);
            interceptor = new SqlGuardInterceptor(unsorted, parserFacade);

            MappedStatement ms = createMappedStatement(sql, "UserMapper.selectAll", SqlCommandType.SELECT);

            Method queryMethod = Executor.class.getMethod("query",
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
            invocation = new Invocation(executor, queryMethod,
                    new Object[]{ms, null, RowBounds.DEFAULT, null});

            // Act
            interceptor.intercept(invocation);

            // Assert - verify invocation order using inOrder
            InOrder inOrderVerifier = inOrder(interceptor10, interceptor50, interceptor100);
            inOrderVerifier.verify(interceptor10).willDoQuery(any(), any(), any(), any(), any(), any());
            inOrderVerifier.verify(interceptor50).willDoQuery(any(), any(), any(), any(), any(), any());
            inOrderVerifier.verify(interceptor100).willDoQuery(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("3. SQL Parsing and Caching Tests")
    class SqlParsingCachingTests {

        @Test
        @DisplayName("testIntercept_parsesSQL_cachesToThreadLocal - parses and caches SQL")
        public void testIntercept_parsesSQL_cachesToThreadLocal() throws Throwable {
            // Arrange
            String sql = "SELECT * FROM users";

            // Create a test interceptor that verifies StatementContext has the cached Statement
            SqlGuardInnerInterceptor verifyingInterceptor = new SqlGuardInnerInterceptor() {
                @Override
                public boolean willDoQuery(Executor executor, MappedStatement ms,
                                          Object parameter, RowBounds rowBounds,
                                          ResultHandler resultHandler, BoundSql boundSql) {
                    // Verify Statement is cached in ThreadLocal during interceptor chain execution
                    assertNotNull(StatementContext.get(sql), 
                            "Statement should be cached in ThreadLocal during execution");
                    return true;
                }

                @Override
                public int getPriority() {
                    return 10;
                }
            };

            interceptor = new SqlGuardInterceptor(Collections.singletonList(verifyingInterceptor), parserFacade);

            MappedStatement ms = createMappedStatement(sql, "UserMapper.selectAll", SqlCommandType.SELECT);

            Method queryMethod = Executor.class.getMethod("query",
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
            invocation = new Invocation(executor, queryMethod,
                    new Object[]{ms, null, RowBounds.DEFAULT, null});

            // Act
            interceptor.intercept(invocation);

            // Assert - after intercept, cache should be cleared
            assertNull(StatementContext.get(sql), "ThreadLocal should be cleared after intercept");
        }
    }

    @Nested
    @DisplayName("4. Lifecycle Method Invocation Tests")
    class LifecycleInvocationTests {

        @Test
        @DisplayName("testIntercept_invokesWillDoQuery_beforeBeforeQuery - willDoQuery before beforeQuery")
        public void testIntercept_invokesWillDoQuery_beforeBeforeQuery() throws Throwable {
            // Arrange
            String sql = "SELECT * FROM users";

            SqlGuardInnerInterceptor mockInterceptor = mock(SqlGuardInnerInterceptor.class);
            when(mockInterceptor.getPriority()).thenReturn(10);
            when(mockInterceptor.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(true);

            interceptor = new SqlGuardInterceptor(Collections.singletonList(mockInterceptor), parserFacade);

            MappedStatement ms = createMappedStatement(sql, "UserMapper.selectAll", SqlCommandType.SELECT);

            Method queryMethod = Executor.class.getMethod("query",
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
            invocation = new Invocation(executor, queryMethod,
                    new Object[]{ms, null, RowBounds.DEFAULT, null});

            // Act
            interceptor.intercept(invocation);

            // Assert
            InOrder inOrderVerifier = inOrder(mockInterceptor);
            inOrderVerifier.verify(mockInterceptor).willDoQuery(any(), any(), any(), any(), any(), any());
            inOrderVerifier.verify(mockInterceptor).beforeQuery(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("testIntercept_invokesWillDoUpdate_beforeBeforeUpdate - willDoUpdate before beforeUpdate")
        public void testIntercept_invokesWillDoUpdate_beforeBeforeUpdate() throws Throwable {
            // Arrange
            String sql = "UPDATE users SET name = 'test'";

            SqlGuardInnerInterceptor mockInterceptor = mock(SqlGuardInnerInterceptor.class);
            when(mockInterceptor.getPriority()).thenReturn(10);
            when(mockInterceptor.willDoUpdate(any(), any(), any())).thenReturn(true);

            interceptor = new SqlGuardInterceptor(Collections.singletonList(mockInterceptor), parserFacade);

            MappedStatement ms = createMappedStatement(sql, "UserMapper.updateUser", SqlCommandType.UPDATE);

            Method updateMethod = Executor.class.getMethod("update",
                    MappedStatement.class, Object.class);
            invocation = new Invocation(executor, updateMethod,
                    new Object[]{ms, null});

            // Act
            interceptor.intercept(invocation);

            // Assert
            InOrder inOrderVerifier = inOrder(mockInterceptor);
            inOrderVerifier.verify(mockInterceptor).willDoUpdate(any(), any(), any());
            inOrderVerifier.verify(mockInterceptor).beforeUpdate(any(), any(), any());
        }

        @Test
        @DisplayName("testMultipleInnerInterceptors_allInvoked - all interceptors invoked")
        public void testMultipleInnerInterceptors_allInvoked() throws Throwable {
            // Arrange
            String sql = "SELECT * FROM users";

            SqlGuardInnerInterceptor interceptor1 = mock(SqlGuardInnerInterceptor.class);
            SqlGuardInnerInterceptor interceptor2 = mock(SqlGuardInnerInterceptor.class);
            SqlGuardInnerInterceptor interceptor3 = mock(SqlGuardInnerInterceptor.class);

            when(interceptor1.getPriority()).thenReturn(10);
            when(interceptor2.getPriority()).thenReturn(50);
            when(interceptor3.getPriority()).thenReturn(100);

            when(interceptor1.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(true);
            when(interceptor2.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(true);
            when(interceptor3.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(true);

            List<SqlGuardInnerInterceptor> interceptors = Arrays.asList(interceptor1, interceptor2, interceptor3);
            interceptor = new SqlGuardInterceptor(interceptors, parserFacade);

            MappedStatement ms = createMappedStatement(sql, "UserMapper.selectAll", SqlCommandType.SELECT);

            Method queryMethod = Executor.class.getMethod("query",
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
            invocation = new Invocation(executor, queryMethod,
                    new Object[]{ms, null, RowBounds.DEFAULT, null});

            // Act
            interceptor.intercept(invocation);

            // Assert
            verify(interceptor1, times(1)).willDoQuery(any(), any(), any(), any(), any(), any());
            verify(interceptor1, times(1)).beforeQuery(any(), any(), any(), any(), any(), any());
            verify(interceptor2, times(1)).willDoQuery(any(), any(), any(), any(), any(), any());
            verify(interceptor2, times(1)).beforeQuery(any(), any(), any(), any(), any(), any());
            verify(interceptor3, times(1)).willDoQuery(any(), any(), any(), any(), any(), any());
            verify(interceptor3, times(1)).beforeQuery(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("5. Short-Circuit Tests")
    class ShortCircuitTests {

        @Test
        @DisplayName("testIntercept_stopsChain_ifWillDoXxxReturnsFalse - stops if willDoQuery returns false")
        public void testIntercept_stopsChain_ifWillDoXxxReturnsFalse() throws Throwable {
            // Arrange
            String sql = "SELECT * FROM users";

            SqlGuardInnerInterceptor interceptor1 = mock(SqlGuardInnerInterceptor.class);
            SqlGuardInnerInterceptor interceptor2 = mock(SqlGuardInnerInterceptor.class);

            when(interceptor1.getPriority()).thenReturn(10);
            when(interceptor2.getPriority()).thenReturn(50);

            when(interceptor1.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(false); // Stop chain

            List<SqlGuardInnerInterceptor> interceptors = Arrays.asList(interceptor1, interceptor2);
            interceptor = new SqlGuardInterceptor(interceptors, parserFacade);

            MappedStatement ms = createMappedStatement(sql, "UserMapper.selectAll", SqlCommandType.SELECT);

            Method queryMethod = Executor.class.getMethod("query",
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
            invocation = new Invocation(executor, queryMethod,
                    new Object[]{ms, null, RowBounds.DEFAULT, null});

            // Act
            Object result = interceptor.intercept(invocation);

            // Assert
            assertNull(result, "Should return null when short-circuited");
            verify(interceptor1, times(1)).willDoQuery(any(), any(), any(), any(), any(), any());
            verify(interceptor2, never()).willDoQuery(any(), any(), any(), any(), any(), any()); // Not invoked
            verify(interceptor1, never()).beforeQuery(any(), any(), any(), any(), any(), any()); // Phase 2 skipped
        }

        @Test
        @DisplayName("testIntercept_stopsChain_ifWillDoUpdateReturnsFalse - stops if willDoUpdate returns false")
        public void testIntercept_stopsChain_ifWillDoUpdateReturnsFalse() throws Throwable {
            // Arrange
            String sql = "UPDATE users SET name = 'test'";

            SqlGuardInnerInterceptor interceptor1 = mock(SqlGuardInnerInterceptor.class);
            SqlGuardInnerInterceptor interceptor2 = mock(SqlGuardInnerInterceptor.class);

            when(interceptor1.getPriority()).thenReturn(10);
            when(interceptor2.getPriority()).thenReturn(50);

            when(interceptor1.willDoUpdate(any(), any(), any())).thenReturn(false); // Stop chain

            List<SqlGuardInnerInterceptor> interceptors = Arrays.asList(interceptor1, interceptor2);
            interceptor = new SqlGuardInterceptor(interceptors, parserFacade);

            MappedStatement ms = createMappedStatement(sql, "UserMapper.updateUser", SqlCommandType.UPDATE);

            Method updateMethod = Executor.class.getMethod("update",
                    MappedStatement.class, Object.class);
            invocation = new Invocation(executor, updateMethod,
                    new Object[]{ms, null});

            // Act
            Object result = interceptor.intercept(invocation);

            // Assert
            assertEquals(0, result, "Should return 0 when short-circuited for update");
            verify(interceptor1, times(1)).willDoUpdate(any(), any(), any());
            verify(interceptor2, never()).willDoUpdate(any(), any(), any()); // Not invoked
            verify(interceptor1, never()).beforeUpdate(any(), any(), any()); // Phase 2 skipped
        }
    }

    @Nested
    @DisplayName("6. ThreadLocal Cleanup Tests")
    class ThreadLocalCleanupTests {

        @Test
        @DisplayName("testIntercept_cleansUpThreadLocal_inFinally - clears ThreadLocal")
        public void testIntercept_cleansUpThreadLocal_inFinally() throws Throwable {
            // Arrange
            String sql = "SELECT * FROM users";

            SqlGuardInnerInterceptor mockInterceptor = mock(SqlGuardInnerInterceptor.class);
            when(mockInterceptor.getPriority()).thenReturn(10);
            when(mockInterceptor.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(true);

            interceptor = new SqlGuardInterceptor(Collections.singletonList(mockInterceptor), parserFacade);

            MappedStatement ms = createMappedStatement(sql, "UserMapper.selectAll", SqlCommandType.SELECT);

            Method queryMethod = Executor.class.getMethod("query",
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
            invocation = new Invocation(executor, queryMethod,
                    new Object[]{ms, null, RowBounds.DEFAULT, null});

            // Act
            interceptor.intercept(invocation);

            // Assert - StatementContext should be cleared
            assertNull(StatementContext.get(sql), "ThreadLocal should be cleared after intercept");
        }

        @Test
        @DisplayName("testIntercept_handlesException_cleansUpThreadLocal - clears even on exception")
        public void testIntercept_handlesException_cleansUpThreadLocal() throws Throwable {
            // Arrange
            String sql = "SELECT * FROM users";

            SqlGuardInnerInterceptor mockInterceptor = mock(SqlGuardInnerInterceptor.class);
            when(mockInterceptor.getPriority()).thenReturn(10);
            when(mockInterceptor.willDoQuery(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new SQLException("Test exception"));

            interceptor = new SqlGuardInterceptor(Collections.singletonList(mockInterceptor), parserFacade);

            MappedStatement ms = createMappedStatement(sql, "UserMapper.selectAll", SqlCommandType.SELECT);

            Method queryMethod = Executor.class.getMethod("query",
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
            invocation = new Invocation(executor, queryMethod,
                    new Object[]{ms, null, RowBounds.DEFAULT, null});

            // Act & Assert
            assertThrows(Throwable.class, () -> {
                interceptor.intercept(invocation);
            }, "Should propagate exception");

            // ThreadLocal should still be cleared (finally block)
            assertNull(StatementContext.get(sql), "ThreadLocal should be cleared even on exception");
        }
    }

    @Nested
    @DisplayName("7. Spring Integration Tests")
    class SpringIntegrationTests {

        @Test
        @DisplayName("testSpringIntegration_beanFactory_works - bean factory pattern works")
        public void testSpringIntegration_beanFactory_works() {
            // Arrange
            SqlGuardInnerInterceptor mockInterceptor1 = mock(SqlGuardInnerInterceptor.class);
            SqlGuardInnerInterceptor mockInterceptor2 = mock(SqlGuardInnerInterceptor.class);

            when(mockInterceptor1.getPriority()).thenReturn(10);
            when(mockInterceptor2.getPriority()).thenReturn(100);

            List<SqlGuardInnerInterceptor> innerInterceptors = Arrays.asList(mockInterceptor1, mockInterceptor2);

            // Act - simulate Spring @Bean method
            SqlGuardInterceptor bean = new SqlGuardInterceptor(innerInterceptors, parserFacade);

            // Assert
            assertNotNull(bean, "Bean should be created");
            assertEquals(2, bean.getSortedInterceptors().size(), "Should have 2 interceptors");
        }
    }

    @Nested
    @DisplayName("8. Plugin Method Tests")
    class PluginMethodTests {

        @Test
        @DisplayName("testPlugin_wrapsExecutor - wraps Executor target")
        public void testPlugin_wrapsExecutor() {
            // Arrange
            List<SqlGuardInnerInterceptor> innerInterceptors = Collections.emptyList();
            interceptor = new SqlGuardInterceptor(innerInterceptors, parserFacade);

            // Act
            Object result = interceptor.plugin(executor);

            // Assert
            assertNotNull(result, "Should return wrapped proxy");
            assertNotSame(executor, result, "Should return different object (proxy)");
        }

        @Test
        @DisplayName("testPlugin_returnsNonExecutorAsIs - returns non-Executor unchanged")
        public void testPlugin_returnsNonExecutorAsIs() {
            // Arrange
            List<SqlGuardInnerInterceptor> innerInterceptors = Collections.emptyList();
            interceptor = new SqlGuardInterceptor(innerInterceptors, parserFacade);
            Object nonExecutor = new Object();

            // Act
            Object result = interceptor.plugin(nonExecutor);

            // Assert
            assertSame(nonExecutor, result, "Should return non-Executor unchanged");
        }
    }
}
