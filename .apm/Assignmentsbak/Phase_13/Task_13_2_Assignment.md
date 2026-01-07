---
task_ref: "Task 13.2 - SqlGuardInterceptor Main Interceptor Implementation"
agent_assignment: "Agent_Advanced_Interceptor"
memory_log_path: ".apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_2_MainInterceptor_Implementation.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
phase: 13
task_id: 13.2
estimated_duration: 2 days
dependencies: [Task_13.1, Task_13.6]
parallel_with: [Task_13.3, Task_13.4, Task_13.5]
---

# Task 13.2 Assignment: SqlGuardInterceptor Main Interceptor Implementation

## 任务目标

实现 `SqlGuardInterceptor` 作为 MyBatis Interceptor，管理 `List<SqlGuardInnerInterceptor>` 并按优先级排序，在调用 InnerInterceptor 链之前解析 SQL 并缓存到 ThreadLocal，实现生命周期方法调用（willDoQuery → beforeQuery 用于 SELECT，willDoUpdate → beforeUpdate 用于 UPDATE/DELETE），在 finally 块中清理 ThreadLocal 防止内存泄漏。

---

## 背景说明

### 为什么需要 SqlGuardInterceptor？

在 Phase 13 的 InnerInterceptor 架构中，我们需要一个主拦截器来协调所有 InnerInterceptor 的执行：

**MyBatis 拦截器链**:
```
MyBatis Executor.query()/update()
    ↓
SqlGuardInterceptor (@Intercepts)
    ↓
InnerInterceptor Chain (按 priority 排序)
    ├─ Priority 10: SqlGuardCheckInnerInterceptor
    ├─ Priority 100: SelectLimitInnerInterceptor
    └─ Priority 200: SqlGuardRewriteInnerInterceptor
```

**SqlGuardInterceptor 职责**:
1. **拦截 MyBatis 方法**: 使用 @Intercepts 拦截 Executor.query 和 Executor.update
2. **SQL 解析**: 使用 JSqlParserFacade 解析 SQL 为 Statement
3. **ThreadLocal 缓存**: 将 Statement 缓存到 StatementContext 供所有 InnerInterceptor 复用
4. **优先级排序**: 按 getPriority() 升序排列 InnerInterceptor（10 → 100 → 200）
5. **生命周期调用**: 调用 willDoXxx() → beforeXxx() 生命周期方法
6. **短路机制**: 如果任何 willDoXxx() 返回 false，停止链执行
7. **ThreadLocal 清理**: 在 finally 块调用 StatementContext.clear() 防止内存泄漏

---

### InnerInterceptor 生命周期

**Query 生命周期** (SELECT):
```java
// Phase 1: Pre-check (willDoQuery)
for (InnerInterceptor interceptor : sortedInterceptors) {
    boolean shouldContinue = interceptor.willDoQuery(...);
    if (!shouldContinue) {
        return result;  // Short-circuit: stop chain
    }
}

// Phase 2: Modification (beforeQuery)
for (InnerInterceptor interceptor : sortedInterceptors) {
    interceptor.beforeQuery(...);  // Can modify BoundSql
}

// Phase 3: Execution
result = invocation.proceed();  // Execute original query
```

**Update 生命周期** (INSERT/UPDATE/DELETE):
```java
// Phase 1: Pre-check (willDoUpdate)
for (InnerInterceptor interceptor : sortedInterceptors) {
    boolean shouldContinue = interceptor.willDoUpdate(...);
    if (!shouldContinue) {
        return result;  // Short-circuit
    }
}

// Phase 2: Modification (beforeUpdate)
for (InnerInterceptor interceptor : sortedInterceptors) {
    interceptor.beforeUpdate(...);  // Can modify SQL
}

// Phase 3: Execution
result = invocation.proceed();  // Execute original update
```

---

### 优先级排序机制

```java
// Example InnerInterceptor priorities:
SqlGuardCheckInnerInterceptor.getPriority() → 10  (check first)
SelectLimitInnerInterceptor.getPriority() → 100    (fallback)
SqlGuardRewriteInnerInterceptor.getPriority() → 200 (rewrite last)

// Sort ascending (lower priority executes first):
List<SqlGuardInnerInterceptor> sorted = interceptors.stream()
    .sorted(Comparator.comparingInt(SqlGuardInnerInterceptor::getPriority))
    .collect(Collectors.toList());

// Execution order: 10 → 100 → 200
```

**为什么低数字优先？**
- Check 拦截器（10）必须先执行，检测违规后可能抛出异常
- Fallback 拦截器（100）在 check 通过后执行，添加 LIMIT 等降级措施
- Rewrite 拦截器（200）最后执行，修改 SQL

---

## 实现要求

### 1. SqlGuardInterceptor 类设计

**包路径**: `com.footstone.sqlguard.interceptor.mybatis`

**类名**: `SqlGuardInterceptor`

**完整实现**:

```java
package com.footstone.sqlguard.interceptor.mybatis;

import com.footstone.sqlguard.interceptor.inner.SqlGuardInnerInterceptor;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.parser.StatementContext;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Main MyBatis Interceptor orchestrating InnerInterceptor chain with priority-based execution.
 *
 * <h2>Purpose</h2>
 * <p>Manages List of {@link SqlGuardInnerInterceptor} instances, sorting by priority and invoking
 * lifecycle methods (willDoXxx → beforeXxx) for each SQL operation. Parses SQL once and caches
 * Statement in ThreadLocal for reuse across all InnerInterceptors.
 *
 * <h2>Interceptor Chain Flow</h2>
 * <pre>
 * MyBatis Executor.query/update
 *     ↓
 * SqlGuardInterceptor.intercept()
 *     ├─ Parse SQL → Statement
 *     ├─ Cache: StatementContext.cache(sql, statement)
 *     ├─ Sort InnerInterceptors by priority (10 → 100 → 200)
 *     ├─ Phase 1: Invoke willDoXxx() chain (stop if any false)
 *     ├─ Phase 2: Invoke beforeXxx() chain (modify BoundSql)
 *     ├─ Execute: invocation.proceed()
 *     └─ Finally: StatementContext.clear() (CRITICAL)
 * </pre>
 *
 * <h2>Priority Mechanism</h2>
 * <ul>
 *   <li><b>1-99:</b> Check interceptors (e.g., SqlGuardCheckInnerInterceptor = 10)</li>
 *   <li><b>100-199:</b> Fallback interceptors (e.g., SelectLimitInnerInterceptor = 100)</li>
 *   <li><b>200+:</b> Rewrite interceptors (e.g., SqlGuardRewriteInnerInterceptor = 200)</li>
 * </ul>
 * Lower priority numbers execute first.
 *
 * <h2>Short-Circuit Mechanism</h2>
 * <p>If any InnerInterceptor's {@code willDoXxx()} returns {@code false}, the chain stops
 * immediately and {@code invocation.proceed()} is skipped. This allows check interceptors
 * to block unsafe SQL operations.
 *
 * <h2>ThreadLocal Memory Leak Prevention</h2>
 * <p><b>CRITICAL:</b> {@link StatementContext#clear()} MUST be called in finally block.
 * Failure to clear ThreadLocal causes memory leaks in thread pool environments.
 *
 * <h2>Spring Integration</h2>
 * <pre>{@code
 * @Bean
 * public SqlGuardInterceptor sqlGuardInterceptor(
 *         List<SqlGuardInnerInterceptor> innerInterceptors,
 *         JSqlParserFacade parserFacade) {
 *     return new SqlGuardInterceptor(innerInterceptors, parserFacade);
 * }
 * }</pre>
 *
 * @see SqlGuardInnerInterceptor
 * @see StatementContext
 * @since 1.1.0
 */
@Intercepts({
    @Signature(
        type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
    ),
    @Signature(
        type = Executor.class,
        method = "update",
        args = {MappedStatement.class, Object.class}
    )
})
public class SqlGuardInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlGuardInterceptor.class);

    /**
     * InnerInterceptor chain sorted by priority (ascending).
     */
    private final List<SqlGuardInnerInterceptor> sortedInterceptors;

    /**
     * JSqlParser facade for parsing SQL into Statement.
     */
    private final JSqlParserFacade parserFacade;

    /**
     * Constructs SqlGuardInterceptor with InnerInterceptor list and parser facade.
     *
     * <p>InnerInterceptors are automatically sorted by priority (ascending order).
     *
     * @param innerInterceptors List of InnerInterceptor instances (unsorted)
     * @param parserFacade      JSqlParser facade for SQL parsing
     */
    public SqlGuardInterceptor(List<SqlGuardInnerInterceptor> innerInterceptors,
                                JSqlParserFacade parserFacade) {
        this.parserFacade = parserFacade;

        // Sort by priority (ascending: 10 → 100 → 200)
        this.sortedInterceptors = innerInterceptors.stream()
                .sorted(Comparator.comparingInt(SqlGuardInnerInterceptor::getPriority))
                .collect(Collectors.toList());

        log.info("SqlGuardInterceptor initialized with {} InnerInterceptors", sortedInterceptors.size());
        for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
            log.info("  - {} (priority: {})",
                    interceptor.getClass().getSimpleName(),
                    interceptor.getPriority());
        }
    }

    /**
     * Intercepts Executor.query/update methods and orchestrates InnerInterceptor chain.
     *
     * <p>Workflow:
     * <ol>
     *   <li>Extract SQL from BoundSql</li>
     *   <li>Parse SQL into Statement using JSqlParserFacade</li>
     *   <li>Cache Statement in ThreadLocal: {@code StatementContext.cache(sql, statement)}</li>
     *   <li>Invoke InnerInterceptor chain:
     *     <ul>
     *       <li>Phase 1: willDoXxx() - pre-check (stop if any false)</li>
     *       <li>Phase 2: beforeXxx() - modification</li>
     *     </ul>
     *   </li>
     *   <li>Execute original method: {@code invocation.proceed()}</li>
     *   <li>Finally: {@code StatementContext.clear()} (CRITICAL for memory leak prevention)</li>
     * </ol>
     *
     * @param invocation MyBatis Invocation wrapping Executor method call
     * @return Result from original Executor method or short-circuit result
     * @throws Throwable If SQL parsing fails, InnerInterceptor throws exception, or execution fails
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            // 1. Extract method arguments
            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object parameter = args[1];

            // 2. Extract SQL from BoundSql
            BoundSql boundSql = ms.getBoundSql(parameter);
            String sql = boundSql.getSql();

            log.debug("Intercepting SQL: {} (Statement ID: {})", sql, ms.getId());

            // 3. Parse SQL and cache to ThreadLocal
            Statement statement = parserFacade.parse(sql);
            StatementContext.cache(sql, statement);
            log.trace("Cached Statement in ThreadLocal for SQL: {}", sql);

            // 4. Determine operation type (query vs update)
            String methodName = invocation.getMethod().getName();
            boolean isQuery = "query".equals(methodName);

            // 5. Invoke InnerInterceptor chain
            if (isQuery) {
                RowBounds rowBounds = (RowBounds) args[2];
                ResultHandler resultHandler = (ResultHandler) args[3];

                if (!invokeWillDoQuery(ms, parameter, rowBounds, resultHandler, boundSql)) {
                    // Short-circuit: willDoQuery returned false
                    log.debug("InnerInterceptor chain short-circuited for query");
                    return null; // Skip execution
                }

                invokeBeforeQuery(ms, parameter, rowBounds, resultHandler, boundSql);
            } else {
                if (!invokeWillDoUpdate(ms, parameter)) {
                    // Short-circuit: willDoUpdate returned false
                    log.debug("InnerInterceptor chain short-circuited for update");
                    return 0; // Skip execution (return 0 rows affected)
                }

                invokeBeforeUpdate(ms, parameter);
            }

            // 6. Execute original method
            log.trace("Proceeding with original Executor method");
            return invocation.proceed();

        } finally {
            // 7. CRITICAL: Cleanup ThreadLocal to prevent memory leaks
            StatementContext.clear();
            log.trace("Cleared StatementContext ThreadLocal");
        }
    }

    /**
     * Invokes willDoQuery() lifecycle method on all InnerInterceptors.
     *
     * <p>Stops chain if any interceptor returns {@code false} (short-circuit).
     *
     * @return {@code true} if all interceptors returned true, {@code false} to stop chain
     */
    private boolean invokeWillDoQuery(MappedStatement ms, Object parameter,
                                       RowBounds rowBounds, ResultHandler resultHandler,
                                       BoundSql boundSql) throws Throwable {
        Executor executor = null; // MyBatis Executor not needed for phase 13

        for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
            log.trace("Invoking willDoQuery on {}", interceptor.getClass().getSimpleName());
            boolean shouldContinue = interceptor.willDoQuery(executor, ms, parameter,
                                                             rowBounds, resultHandler, boundSql);
            if (!shouldContinue) {
                log.debug("InnerInterceptor {} returned false from willDoQuery, stopping chain",
                         interceptor.getClass().getSimpleName());
                return false; // Short-circuit
            }
        }

        return true; // Continue to beforeQuery phase
    }

    /**
     * Invokes beforeQuery() lifecycle method on all InnerInterceptors.
     *
     * <p>Allows interceptors to modify BoundSql before query execution.
     */
    private void invokeBeforeQuery(MappedStatement ms, Object parameter,
                                     RowBounds rowBounds, ResultHandler resultHandler,
                                     BoundSql boundSql) throws Throwable {
        Executor executor = null;

        for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
            log.trace("Invoking beforeQuery on {}", interceptor.getClass().getSimpleName());
            interceptor.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        }
    }

    /**
     * Invokes willDoUpdate() lifecycle method on all InnerInterceptors.
     *
     * @return {@code true} if all interceptors returned true, {@code false} to stop chain
     */
    private boolean invokeWillDoUpdate(MappedStatement ms, Object parameter) throws Throwable {
        Executor executor = null;

        for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
            log.trace("Invoking willDoUpdate on {}", interceptor.getClass().getSimpleName());
            boolean shouldContinue = interceptor.willDoUpdate(executor, ms, parameter);
            if (!shouldContinue) {
                log.debug("InnerInterceptor {} returned false from willDoUpdate, stopping chain",
                         interceptor.getClass().getSimpleName());
                return false; // Short-circuit
            }
        }

        return true; // Continue to beforeUpdate phase
    }

    /**
     * Invokes beforeUpdate() lifecycle method on all InnerInterceptors.
     */
    private void invokeBeforeUpdate(MappedStatement ms, Object parameter) throws Throwable {
        Executor executor = null;

        for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
            log.trace("Invoking beforeUpdate on {}", interceptor.getClass().getSimpleName());
            interceptor.beforeUpdate(executor, ms, parameter);
        }
    }

    /**
     * Wraps target Executor for plugin chain.
     *
     * @param target Target Executor instance
     * @return Wrapped proxy
     */
    @Override
    public Object plugin(Object target) {
        if (target instanceof Executor) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    /**
     * Sets properties (currently unused).
     *
     * @param properties Configuration properties
     */
    @Override
    public void setProperties(Properties properties) {
        // No properties needed
    }

    /**
     * Returns sorted InnerInterceptor list (for testing).
     *
     * @return Sorted InnerInterceptor list
     */
    List<SqlGuardInnerInterceptor> getSortedInterceptors() {
        return sortedInterceptors;
    }
}
```

---

### 2. TDD 测试用例设计

**测试类**: `sql-guard-mybatis/src/test/java/com/footstone/sqlguard/interceptor/mybatis/SqlGuardInterceptorTest.java`

**测试用例 (12 个)**:

```java
package com.footstone.sqlguard.interceptor.mybatis;

import com.footstone.sqlguard.interceptor.inner.SqlGuardInnerInterceptor;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.parser.StatementContext;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.SQLException;
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
public class SqlGuardInterceptorTest {

    private SqlGuardInterceptor interceptor;
    private JSqlParserFacade parserFacade;
    private Executor executor;
    private MappedStatement ms;
    private BoundSql boundSql;
    private Invocation invocation;

    @BeforeEach
    void setUp() {
        executor = mock(Executor.class);
        ms = mock(MappedStatement.class);
        boundSql = mock(BoundSql.class);
        parserFacade = mock(JSqlParserFacade.class);

        when(ms.getId()).thenReturn("UserMapper.selectAll");
        when(ms.getBoundSql(any())).thenReturn(boundSql);
    }

    @AfterEach
    void tearDown() {
        StatementContext.clear();
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
            Statement stmt = CCJSqlParserUtil.parse(sql);

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

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);

            Method queryMethod = Executor.class.getMethod("query",
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
            invocation = new Invocation(executor, queryMethod,
                    new Object[]{ms, null, RowBounds.DEFAULT, null});

            // Act
            interceptor.intercept(invocation);

            // Assert - verify invocation order using inOrder
            var inOrder = inOrder(interceptor10, interceptor50, interceptor100);
            inOrder.verify(interceptor10).willDoQuery(any(), any(), any(), any(), any(), any());
            inOrder.verify(interceptor50).willDoQuery(any(), any(), any(), any(), any(), any());
            inOrder.verify(interceptor100).willDoQuery(any(), any(), any(), any(), any(), any());
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
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlGuardInnerInterceptor mockInterceptor = mock(SqlGuardInnerInterceptor.class);
            when(mockInterceptor.getPriority()).thenReturn(10);
            when(mockInterceptor.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(true);

            interceptor = new SqlGuardInterceptor(Collections.singletonList(mockInterceptor), parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);

            Method queryMethod = Executor.class.getMethod("query",
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
            invocation = new Invocation(executor, queryMethod,
                    new Object[]{ms, null, RowBounds.DEFAULT, null});

            // Act
            interceptor.intercept(invocation);

            // Assert
            verify(parserFacade, times(1)).parse(sql);
            // Note: StatementContext is cleared in finally, so we can't verify cache here
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
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlGuardInnerInterceptor mockInterceptor = mock(SqlGuardInnerInterceptor.class);
            when(mockInterceptor.getPriority()).thenReturn(10);
            when(mockInterceptor.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(true);

            interceptor = new SqlGuardInterceptor(Collections.singletonList(mockInterceptor), parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);

            Method queryMethod = Executor.class.getMethod("query",
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
            invocation = new Invocation(executor, queryMethod,
                    new Object[]{ms, null, RowBounds.DEFAULT, null});

            // Act
            interceptor.intercept(invocation);

            // Assert
            var inOrder = inOrder(mockInterceptor);
            inOrder.verify(mockInterceptor).willDoQuery(any(), any(), any(), any(), any(), any());
            inOrder.verify(mockInterceptor).beforeQuery(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("testIntercept_invokesWillDoUpdate_beforeBeforeUpdate - willDoUpdate before beforeUpdate")
        public void testIntercept_invokesWillDoUpdate_beforeBeforeUpdate() throws Throwable {
            // Arrange
            String sql = "UPDATE users SET name = 'test'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlGuardInnerInterceptor mockInterceptor = mock(SqlGuardInnerInterceptor.class);
            when(mockInterceptor.getPriority()).thenReturn(10);
            when(mockInterceptor.willDoUpdate(any(), any(), any())).thenReturn(true);

            interceptor = new SqlGuardInterceptor(Collections.singletonList(mockInterceptor), parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);

            Method updateMethod = Executor.class.getMethod("update",
                    MappedStatement.class, Object.class);
            invocation = new Invocation(executor, updateMethod,
                    new Object[]{ms, null});

            // Act
            interceptor.intercept(invocation);

            // Assert
            var inOrder = inOrder(mockInterceptor);
            inOrder.verify(mockInterceptor).willDoUpdate(any(), any(), any());
            inOrder.verify(mockInterceptor).beforeUpdate(any(), any(), any());
        }

        @Test
        @DisplayName("testMultipleInnerInterceptors_allInvoked - all interceptors invoked")
        public void testMultipleInnerInterceptors_allInvoked() throws Throwable {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

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

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);

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
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlGuardInnerInterceptor interceptor1 = mock(SqlGuardInnerInterceptor.class);
            SqlGuardInnerInterceptor interceptor2 = mock(SqlGuardInnerInterceptor.class);

            when(interceptor1.getPriority()).thenReturn(10);
            when(interceptor2.getPriority()).thenReturn(50);

            when(interceptor1.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(false); // Stop chain

            List<SqlGuardInnerInterceptor> interceptors = Arrays.asList(interceptor1, interceptor2);
            interceptor = new SqlGuardInterceptor(interceptors, parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);

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
    }

    @Nested
    @DisplayName("6. ThreadLocal Cleanup Tests")
    class ThreadLocalCleanupTests {

        @Test
        @DisplayName("testIntercept_cleansUpThreadLocal_inFinally - clears ThreadLocal")
        public void testIntercept_cleansUpThreadLocal_inFinally() throws Throwable {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlGuardInnerInterceptor mockInterceptor = mock(SqlGuardInnerInterceptor.class);
            when(mockInterceptor.getPriority()).thenReturn(10);
            when(mockInterceptor.willDoQuery(any(), any(), any(), any(), any(), any())).thenReturn(true);

            interceptor = new SqlGuardInterceptor(Collections.singletonList(mockInterceptor), parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);

            Method queryMethod = Executor.class.getMethod("query",
                    MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
            invocation = new Invocation(executor, queryMethod,
                    new Object[]{ms, null, RowBounds.DEFAULT, null});

            // Act
            interceptor.intercept(invocation);

            // Assert - StatementContext should be cleared
            assertNull(StatementContext.get(sql), "ThreadLocal should be cleared after intercept");
            assertEquals(0, StatementContext.size(), "ThreadLocal cache should be empty");
        }

        @Test
        @DisplayName("testIntercept_handlesException_cleansUpThreadLocal - clears even on exception")
        public void testIntercept_handlesException_cleansUpThreadLocal() throws Throwable {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlGuardInnerInterceptor mockInterceptor = mock(SqlGuardInnerInterceptor.class);
            when(mockInterceptor.getPriority()).thenReturn(10);
            when(mockInterceptor.willDoQuery(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new SQLException("Test exception"));

            interceptor = new SqlGuardInterceptor(Collections.singletonList(mockInterceptor), parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);

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
            assertEquals(0, StatementContext.size(), "ThreadLocal cache should be empty");
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
}
```

---

## 验收标准

### 功能验收
- [ ] SqlGuardInterceptor 类创建（`com.footstone.sqlguard.interceptor.mybatis` 包）
- [ ] 实现 org.apache.ibatis.plugin.Interceptor 接口
- [ ] @Intercepts 注解正确（拦截 Executor.query 和 update）
- [ ] InnerInterceptor 按 priority 升序排序
- [ ] intercept() 方法解析 SQL 并缓存到 StatementContext
- [ ] 生命周期调用顺序正确（willDoXxx → beforeXxx）
- [ ] 短路机制正确（willDoXxx 返回 false 停止链）
- [ ] finally 块清理 ThreadLocal（StatementContext.clear()）
- [ ] plugin() 和 setProperties() 方法实现

### 测试验收
- [ ] SqlGuardInterceptorTest 全部通过（12 个测试）
- [ ] Interceptor 接口测试通过
- [ ] 优先级排序测试通过（10 → 50 → 100）
- [ ] SQL 解析缓存测试通过
- [ ] 生命周期调用测试通过
- [ ] 短路机制测试通过
- [ ] ThreadLocal 清理测试通过（正常和异常情况）
- [ ] Spring 集成测试通过

### 集成验收
- [ ] 与 Task 13.1 SqlGuardInnerInterceptor 接口集成正常
- [ ] 与 Task 13.6 StatementContext 集成正常
- [ ] 多 InnerInterceptor 链执行正常
- [ ] MyBatis 拦截器链集成正常

### 代码质量验收
- [ ] Javadoc 完整（类级、方法级）
- [ ] @Intercepts 注解配置正确
- [ ] SLF4J 日志记录（debug/trace）
- [ ] 异常处理正确

### 构建验收
- [ ] 编译成功
- [ ] 测试通过（12 tests, 0 failures）
- [ ] BUILD SUCCESS

---

## 输出文件

### 新增文件 (2 个)
1. `sql-guard-mybatis/src/main/java/com/footstone/sqlguard/interceptor/mybatis/SqlGuardInterceptor.java`
2. `sql-guard-mybatis/src/test/java/com/footstone/sqlguard/interceptor/mybatis/SqlGuardInterceptorTest.java`

---

## 依赖与限制

### 输入依赖
- ✅ Task 13.1: SqlGuardInnerInterceptor 接口
- ✅ Task 13.6: StatementContext 类
- ✅ Phase 12: JSqlParserFacade
- ✅ MyBatis API (Interceptor, Executor, MappedStatement, BoundSql)
- ✅ JSqlParser (Statement)

### 限制
- ⚠️ 必须在 finally 块调用 StatementContext.clear()（否则内存泄漏）
- ⚠️ Executor 参数在 Phase 13 传 null（InnerInterceptor 不使用）
- ⚠️ 短路返回值：query 返回 null，update 返回 0

---

## 注意事项

### 1. ThreadLocal 清理（严重）

**内存泄漏风险**:
```java
try {
    // Parse and cache
    StatementContext.cache(sql, stmt);

    // Invoke chain
    invokeWillDoQuery(...);
    invokeBeforeQuery(...);

    // Execute
    return invocation.proceed();
} finally {
    // ⚠️ CRITICAL: Must clear ThreadLocal
    StatementContext.clear();
}
```

**为什么必须清理**:
- MyBatis 使用线程池（Tomcat/Jetty）
- 线程复用会导致 ThreadLocal 残留
- 每次请求累积 Statement，最终 OutOfMemoryError

---

### 2. 优先级排序

**升序排序（低数字优先）**:
```java
// Correct: Comparator.comparingInt (ascending)
interceptors.stream()
    .sorted(Comparator.comparingInt(SqlGuardInnerInterceptor::getPriority))
    .collect(Collectors.toList());

// Result: [10, 50, 100] ✅

// Wrong: reversed() (descending)
// Result: [100, 50, 10] ❌
```

---

### 3. 短路机制

**willDoXxx() 返回 false 停止链**:
```java
for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
    if (!interceptor.willDoQuery(...)) {
        // Stop chain
        return null; // Query: return null
        // Update: return 0
    }
}
```

**为什么需要短路**:
- Check 拦截器（priority 10）检测到违规
- 抛出 SQLException 或返回 false
- 后续 fallback/rewrite 拦截器不应执行

---

### 4. Executor 参数传 null

**Phase 13 简化**:
```java
// InnerInterceptor 不使用 Executor
interceptor.willDoQuery(null, ms, parameter, rowBounds, resultHandler, boundSql);
//                      ^^^^
```

**原因**:
- Phase 13 InnerInterceptor 只处理 SQL 层面
- 不需要访问 MyBatis Executor
- 简化实现

---

### 5. @Intercepts 注解

**必须拦截 2 个方法**:
```java
@Intercepts({
    @Signature(
        type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
    ),
    @Signature(
        type = Executor.class,
        method = "update",
        args = {MappedStatement.class, Object.class}
    )
})
```

**注意**:
- query: 4 个参数
- update: 2 个参数
- 参数类型必须完全匹配

---

### 6. 测试 Mock 策略

**需要 Mock**:
- Executor
- MappedStatement
- BoundSql
- SqlGuardInnerInterceptor
- JSqlParserFacade

**需要真实对象**:
- Statement (CCJSqlParserUtil.parse())
- Invocation (new Invocation(...))
- Method (Executor.class.getMethod(...))

---

## Memory Logging

Upon completion, you **MUST** log work in:
```
.apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_2_MainInterceptor_Implementation.md
```

Follow `.apm/guides/Memory_Log_Guide.md` instructions for proper formatting.

**Required Log Sections**:
- Summary: 1-2 sentences describing SqlGuardInterceptor implementation outcome
- Details: Priority sorting, lifecycle invocation, ThreadLocal cleanup, short-circuit mechanism
- Output: List of created files, test results (12 tests)
- Issues: Any integration issues or "None"
- Next Steps: All InnerInterceptor implementations (13.3, 13.4, 13.5) depend on this main interceptor

---

## 执行时间线

- **预计时间**: 2 工作日
  - Day 1 上午：实现 SqlGuardInterceptor 核心逻辑（优先级排序、生命周期调用）
  - Day 1 下午：实现 ThreadLocal 清理和短路机制
  - Day 2 上午：编写 TDD 测试（12 个测试）
  - Day 2 下午：集成测试和验收

---

**Created**: 2025-12-22
**Phase**: 13 - InnerInterceptor Architecture
**Task ID**: 13.2
**Priority**: High (协调所有 InnerInterceptor)
**Parallel**: Can run in parallel with Task 13.3, 13.4, 13.5 (after 13.1 + 13.6 complete)
