---
task_ref: "Task 13.3 - SqlGuardCheckInnerInterceptor Implementation"
agent_assignment: "Agent_Advanced_Interceptor"
memory_log_path: ".apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_3_CheckInnerInterceptor_Implementation.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
phase: 13
task_id: 13.3
estimated_duration: 2 days
dependencies: [Task_13.1, Task_13.6]
parallel_with: [Task_13.2, Task_13.4, Task_13.5]
---

# Task 13.3 Assignment: SqlGuardCheckInnerInterceptor Implementation

## 任务目标

实现 `SqlGuardCheckInnerInterceptor` 作为 RuleChecker 和 InnerInterceptor 之间的桥接器，优先级设为 10（高优先级检查拦截器），在 willDoQuery/willDoUpdate 中从 StatementContext 获取已缓存的 Statement（缓存命中则复用，未命中则解析并缓存），构建包含 Statement 的 SqlContext，执行所有启用的 RuleChecker.check()，根据 ViolationStrategy 处理违规（BLOCK 抛出 SQLException，WARN/LOG 记录日志）。

---

## 背景说明

### 为什么需要 SqlGuardCheckInnerInterceptor？

在 Phase 13 的 InnerInterceptor 架构中，我们需要桥接 Phase 12 的 RuleChecker 系统：

**Phase 12 架构**:
```
SqlSafetyValidator → RuleCheckerOrchestrator → 7 RuleChecker 实现
```

**Phase 13 架构**:
```
SqlGuardInterceptor → InnerInterceptor Chain → SqlGuardCheckInnerInterceptor
```

**桥接需求**:
- **输入**: MyBatis 的 Executor、MappedStatement、BoundSql
- **转换**: 提取 SQL → 解析 Statement → 构建 SqlContext
- **执行**: 调用所有 RuleChecker.check(context, result)
- **处理**: 根据 ViolationStrategy 处理违规结果

---

### InnerInterceptor 链中的位置

```
┌─────────────────────────────────────────────────────────┐
│              SqlGuardInterceptor Chain                   │
├─────────────────────────────────────────────────────────┤
│ Priority 10: SqlGuardCheckInnerInterceptor ⭐ 最先执行  │
│              → Bridge to RuleChecker                     │
│              → BLOCK violations throw SQLException       │
├─────────────────────────────────────────────────────────┤
│ Priority 100: SelectLimitInnerInterceptor (fallback)    │
│               → Add LIMIT if checks passed               │
├─────────────────────────────────────────────────────────┤
│ Priority 200: SqlGuardRewriteInnerInterceptor (rewrite) │
│               → Modify SQL if checks passed              │
└─────────────────────────────────────────────────────────┘
```

**优先级为 10 的原因**:
- 检查拦截器必须最先执行（在 fallback 和 rewrite 之前）
- 如果检查失败（ViolationStrategy.BLOCK），直接抛出异常，跳过后续拦截器
- 保证违规 SQL 不会被 fallback/rewrite 处理

---

### StatementContext 缓存复用

**问题**:
- Task 13.2 (SqlGuardInterceptor) 已解析 SQL 并缓存到 StatementContext
- Task 13.3 (CheckInnerInterceptor) 可能是第一个 InnerInterceptor
- 如何复用已解析的 Statement？

**解决方案**:
```java
// SqlGuardCheckInnerInterceptor.willDoQuery()
String sql = boundSql.getSql();

// 1. 尝试从 ThreadLocal 获取缓存
Statement stmt = StatementContext.get(sql);

if (stmt == null) {
    // 2. 缓存未命中 - 解析并缓存
    stmt = JSqlParserFacade.parse(sql);
    StatementContext.cache(sql, stmt);
}

// 3. 构建 SqlContext（包含 statement）
SqlContext context = SqlContext.builder()
    .sql(sql)
    .statement(stmt)
    .build();

// 4. 执行所有 RuleChecker
for (RuleChecker checker : enabledCheckers) {
    checker.check(context, result);
}
```

---

## 实现要求

### 1. SqlGuardCheckInnerInterceptor 类设计

**包路径**: `com.footstone.sqlguard.interceptor.inner.impl`

**类名**: `SqlGuardCheckInnerInterceptor`

**完整实现**:

```java
package com.footstone.sqlguard.interceptor.inner.impl;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.config.ViolationStrategy;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.interceptor.inner.SqlGuardInnerInterceptor;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.parser.StatementContext;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * InnerInterceptor implementation bridging RuleChecker system with MyBatis-Plus InnerInterceptor pattern.
 *
 * <h2>Purpose</h2>
 * <p>Executes SQL safety checks from Phase 12's RuleChecker system within the InnerInterceptor chain.
 * Reuses parsed Statement from StatementContext (cache hit) or parses and caches (cache miss).
 *
 * <h2>Priority</h2>
 * <p>Priority: <b>10</b> (high priority) - Check interceptors run BEFORE fallback and rewrite interceptors.
 * If violations are detected with {@code ViolationStrategy.BLOCK}, throws SQLException preventing
 * downstream interceptors from executing.
 *
 * <h2>Workflow</h2>
 * <pre>
 * 1. Extract SQL from BoundSql
 * 2. Attempt StatementContext.get(sql) - reuse if cached
 * 3. If cache miss: parse using JSqlParserFacade and cache via StatementContext.cache()
 * 4. Build SqlContext with statement field
 * 5. Execute all enabled RuleCheckers: checker.check(context, result)
 * 6. Handle violations based on ViolationStrategy:
 *    - BLOCK: throw SQLException wrapping violations
 *    - WARN: log.warn(violations)
 *    - LOG: log.info(violations)
 * 7. Return true to continue interceptor chain (or throw if BLOCK)
 * </pre>
 *
 * <h2>RuleChecker Integration</h2>
 * <p>Bridges Phase 12 RuleChecker system:
 * <ul>
 *   <li>{@code NoWhereClauseChecker}</li>
 *   <li>{@code BlacklistFieldChecker}</li>
 *   <li>{@code WhitelistFieldChecker}</li>
 *   <li>{@code LogicalPaginationChecker}</li>
 *   <li>{@code NoConditionPaginationChecker}</li>
 *   <li>{@code DeepPaginationChecker}</li>
 *   <li>{@code NoPaginationChecker}</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Uses ThreadLocal-based {@link StatementContext} ensuring thread isolation.
 * Multiple concurrent requests do not interfere with each other.
 *
 * @see SqlGuardInnerInterceptor
 * @see RuleChecker
 * @see StatementContext
 * @since 1.1.0
 */
public class SqlGuardCheckInnerInterceptor implements SqlGuardInnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlGuardCheckInnerInterceptor.class);

    /**
     * Phase 12 RuleChecker instances (injected via constructor).
     */
    private final List<RuleChecker> ruleCheckers;

    /**
     * Configuration containing ViolationStrategy and checker enable/disable flags.
     */
    private final SqlGuardConfig config;

    /**
     * JSqlParser facade for parsing SQL into Statement.
     */
    private final JSqlParserFacade parserFacade;

    /**
     * Constructs SqlGuardCheckInnerInterceptor with RuleChecker list and configuration.
     *
     * @param ruleCheckers List of RuleChecker instances from Phase 12
     * @param config       SqlGuardConfig containing ViolationStrategy
     * @param parserFacade JSqlParser facade for SQL parsing
     */
    public SqlGuardCheckInnerInterceptor(List<RuleChecker> ruleCheckers,
                                          SqlGuardConfig config,
                                          JSqlParserFacade parserFacade) {
        this.ruleCheckers = ruleCheckers;
        this.config = config;
        this.parserFacade = parserFacade;
    }

    /**
     * Returns priority 10 (high priority for check interceptors).
     *
     * <p>Check interceptors run BEFORE fallback (100) and rewrite (200) interceptors.
     *
     * @return Priority value 10
     */
    @Override
    public int getPriority() {
        return 10;
    }

    /**
     * Pre-check method for query execution.
     *
     * <p>Workflow:
     * <ol>
     *   <li>Extract SQL from BoundSql</li>
     *   <li>Attempt cache retrieval: {@code StatementContext.get(sql)}</li>
     *   <li>If cache miss: parse and cache {@code JSqlParserFacade.parse(sql)}</li>
     *   <li>Build SqlContext with statement field</li>
     *   <li>Execute all enabled RuleCheckers</li>
     *   <li>Handle violations based on ViolationStrategy</li>
     * </ol>
     *
     * @param executor      MyBatis Executor
     * @param ms            MappedStatement containing SQL metadata
     * @param parameter     SQL parameter object
     * @param rowBounds     Pagination bounds
     * @param resultHandler Result handler
     * @param boundSql      BoundSql containing SQL string
     * @return {@code true} to continue chain (if no BLOCK violations)
     * @throws SQLException If ViolationStrategy.BLOCK and violations detected
     */
    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms, Object parameter,
                                RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        return executeChecks(boundSql, ms.getId());
    }

    /**
     * Pre-check method for update/insert/delete execution.
     *
     * <p>Similar workflow to {@link #willDoQuery} but for DML operations.
     *
     * @param executor  MyBatis Executor
     * @param ms        MappedStatement containing SQL metadata
     * @param parameter SQL parameter object
     * @return {@code true} to continue chain (if no BLOCK violations)
     * @throws SQLException If ViolationStrategy.BLOCK and violations detected
     */
    @Override
    public boolean willDoUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        BoundSql boundSql = ms.getBoundSql(parameter);
        return executeChecks(boundSql, ms.getId());
    }

    /**
     * beforeQuery is no-op for check interceptors (checks don't modify SQL).
     */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                             RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        // Check interceptors don't modify SQL
    }

    /**
     * beforeUpdate is no-op for check interceptors (checks don't modify SQL).
     */
    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        // Check interceptors don't modify SQL
    }

    /**
     * Executes all enabled RuleCheckers and handles violations.
     *
     * @param boundSql      BoundSql containing SQL string
     * @param statementId   MyBatis statement ID (e.g., "UserMapper.selectAll")
     * @return {@code true} to continue chain
     * @throws SQLException If ViolationStrategy.BLOCK and violations detected
     */
    private boolean executeChecks(BoundSql boundSql, String statementId) throws SQLException {
        String sql = boundSql.getSql();

        // 1. Attempt cache retrieval
        Statement stmt = StatementContext.get(sql);

        if (stmt == null) {
            // 2. Cache miss - parse and cache
            log.debug("StatementContext cache miss for SQL: {}", sql);
            stmt = parserFacade.parse(sql);
            StatementContext.cache(sql, stmt);
        } else {
            log.debug("StatementContext cache hit for SQL: {}", sql);
        }

        // 3. Build SqlContext with statement field
        SqlContext context = SqlContext.builder()
                .sql(sql)
                .statement(stmt)
                .statementId(statementId)
                .build();

        // 4. Prepare ValidationResult
        ValidationResult result = new ValidationResult(sql);

        // 5. Execute all enabled RuleCheckers
        List<RuleChecker> enabledCheckers = ruleCheckers.stream()
                .filter(RuleChecker::isEnabled)
                .collect(Collectors.toList());

        for (RuleChecker checker : enabledCheckers) {
            log.trace("Executing RuleChecker: {}", checker.getClass().getSimpleName());
            checker.check(context, result);
        }

        // 6. Handle violations based on ViolationStrategy
        if (result.hasViolations()) {
            handleViolations(result);
        }

        // 7. Continue chain if no BLOCK violations thrown
        return true;
    }

    /**
     * Handles violations based on configured ViolationStrategy.
     *
     * @param result ValidationResult containing violations
     * @throws SQLException If ViolationStrategy.BLOCK
     */
    private void handleViolations(ValidationResult result) throws SQLException {
        ViolationStrategy strategy = config.getViolationStrategy();

        switch (strategy) {
            case BLOCK:
                // Throw SQLException wrapping violations
                String errorMsg = buildViolationMessage(result);
                log.error("SQL safety violations detected (BLOCK): {}", errorMsg);
                throw new SQLException("SQL safety violations detected: " + errorMsg);

            case WARN:
                // Log as warning
                log.warn("SQL safety violations detected (WARN): {}", buildViolationMessage(result));
                break;

            case LOG:
                // Log as info
                log.info("SQL safety violations detected (LOG): {}", buildViolationMessage(result));
                break;

            default:
                // Unknown strategy - log as warning
                log.warn("Unknown ViolationStrategy: {}, treating as LOG", strategy);
                log.info("SQL safety violations detected: {}", buildViolationMessage(result));
        }
    }

    /**
     * Builds violation message from ValidationResult.
     *
     * @param result ValidationResult containing violations
     * @return Formatted violation message
     */
    private String buildViolationMessage(ValidationResult result) {
        return result.getViolations().stream()
                .map(v -> String.format("[%s] %s", v.getRuleName(), v.getMessage()))
                .collect(Collectors.joining("; "));
    }
}
```

---

### 2. TDD 测试用例设计

**测试类**: `sql-guard-core/src/test/java/com/footstone/sqlguard/interceptor/inner/impl/SqlGuardCheckInnerInterceptorTest.java`

**测试用例 (15 个)**:

```java
package com.footstone.sqlguard.interceptor.inner.impl;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.config.ViolationStrategy;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.Violation;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.parser.StatementContext;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
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
import static org.mockito.Mockito.*;

/**
 * TDD tests for SqlGuardCheckInnerInterceptor.
 */
@DisplayName("SqlGuardCheckInnerInterceptor Tests")
public class SqlGuardCheckInnerInterceptorTest {

    private SqlGuardCheckInnerInterceptor interceptor;
    private List<RuleChecker> ruleCheckers;
    private SqlGuardConfig config;
    private JSqlParserFacade parserFacade;
    private Executor executor;
    private MappedStatement ms;
    private BoundSql boundSql;

    @BeforeEach
    void setUp() {
        executor = mock(Executor.class);
        ms = mock(MappedStatement.class);
        boundSql = mock(BoundSql.class);
        parserFacade = mock(JSqlParserFacade.class);
        config = mock(SqlGuardConfig.class);

        when(ms.getId()).thenReturn("UserMapper.selectAll");
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
        @DisplayName("testWillDoQuery_getsStatementFromCache_ifExists - cache hit")
        public void testWillDoQuery_getsStatementFromCache_ifExists() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt); // Pre-cache

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);
            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            // Act
            boolean result = interceptor.willDoQuery(executor, ms, null, RowBounds.DEFAULT, null, boundSql);

            // Assert
            assertTrue(result, "Should return true to continue chain");
            verify(parserFacade, never()).parse(anyString()); // Should NOT parse (cache hit)
            verify(mockChecker, times(1)).check(any(SqlContext.class), any(ValidationResult.class));
        }

        @Test
        @DisplayName("testWillDoQuery_parsesAndCaches_ifCacheMiss - cache miss")
        public void testWillDoQuery_parsesAndCaches_ifCacheMiss() throws Exception {
            // Arrange
            String sql = "SELECT * FROM products";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);
            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            // Act
            boolean result = interceptor.willDoQuery(executor, ms, null, RowBounds.DEFAULT, null, boundSql);

            // Assert
            assertTrue(result, "Should return true to continue chain");
            verify(parserFacade, times(1)).parse(sql); // Should parse (cache miss)
            assertSame(stmt, StatementContext.get(sql), "Should cache parsed Statement");
        }

        @Test
        @DisplayName("testWillDoQuery_buildsSqlContext_withStatement - SqlContext contains statement")
        public void testWillDoQuery_buildsSqlContext_withStatement() throws Exception {
            // Arrange
            String sql = "SELECT * FROM orders";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);
            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);

            // Act
            interceptor.willDoQuery(executor, ms, null, RowBounds.DEFAULT, null, boundSql);

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
        @DisplayName("testWillDoQuery_invokesAllEnabledCheckers - all enabled checkers invoked")
        public void testWillDoQuery_invokesAllEnabledCheckers() throws Exception {
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

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            // Act
            interceptor.willDoQuery(executor, ms, null, RowBounds.DEFAULT, null, boundSql);

            // Assert
            verify(checker1, times(1)).check(any(SqlContext.class), any(ValidationResult.class));
            verify(checker2, times(1)).check(any(SqlContext.class), any(ValidationResult.class));
            verify(checker3, times(1)).check(any(SqlContext.class), any(ValidationResult.class));
        }

        @Test
        @DisplayName("testWillDoQuery_skipsDisabledCheckers - disabled checkers not invoked")
        public void testWillDoQuery_skipsDisabledCheckers() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker checker1 = mock(RuleChecker.class);
            RuleChecker checker2 = mock(RuleChecker.class);
            when(checker1.isEnabled()).thenReturn(true);
            when(checker2.isEnabled()).thenReturn(false); // Disabled

            ruleCheckers = Arrays.asList(checker1, checker2);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            // Act
            interceptor.willDoQuery(executor, ms, null, RowBounds.DEFAULT, null, boundSql);

            // Assert
            verify(checker1, times(1)).check(any(SqlContext.class), any(ValidationResult.class));
            verify(checker2, never()).check(any(SqlContext.class), any(ValidationResult.class));
        }

        @Test
        @DisplayName("testRuleCheckerIntegration_works - RuleChecker integration works")
        public void testRuleCheckerIntegration_works() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);

            // Simulate violation detection
            doAnswer(invocation -> {
                ValidationResult result = invocation.getArgument(1);
                result.addViolation(new Violation("NO_WHERE_CLAUSE", "Missing WHERE clause"));
                return null;
            }).when(mockChecker).check(any(SqlContext.class), any(ValidationResult.class));

            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            // Act
            boolean result = interceptor.willDoQuery(executor, ms, null, RowBounds.DEFAULT, null, boundSql);

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
                result.addViolation(new Violation("NO_WHERE_CLAUSE", "Missing WHERE clause"));
                return null;
            }).when(mockChecker).check(any(SqlContext.class), any(ValidationResult.class));

            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.BLOCK);

            // Act & Assert
            SQLException exception = assertThrows(SQLException.class, () -> {
                interceptor.willDoQuery(executor, ms, null, RowBounds.DEFAULT, null, boundSql);
            }, "Should throw SQLException for BLOCK strategy");

            assertTrue(exception.getMessage().contains("SQL safety violations detected"),
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
                result.addViolation(new Violation("NO_WHERE_CLAUSE", "Missing WHERE clause"));
                return null;
            }).when(mockChecker).check(any(SqlContext.class), any(ValidationResult.class));

            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.WARN);

            // Act
            boolean result = interceptor.willDoQuery(executor, ms, null, RowBounds.DEFAULT, null, boundSql);

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
                result.addViolation(new Violation("NO_WHERE_CLAUSE", "Missing WHERE clause"));
                return null;
            }).when(mockChecker).check(any(SqlContext.class), any(ValidationResult.class));

            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            // Act
            boolean result = interceptor.willDoQuery(executor, ms, null, RowBounds.DEFAULT, null, boundSql);

            // Assert
            assertTrue(result, "Should return true (log only, no exception)");
        }

        @Test
        @DisplayName("testMultipleViolations_aggregatedInResult - multiple violations aggregated")
        public void testMultipleViolations_aggregatedInResult() throws Exception {
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
                result.addViolation(new Violation("NO_WHERE_CLAUSE", "Missing WHERE clause"));
                return null;
            }).when(checker1).check(any(SqlContext.class), any(ValidationResult.class));

            // Checker2 detects violation
            doAnswer(invocation -> {
                ValidationResult result = invocation.getArgument(1);
                result.addViolation(new Violation("NO_PAGINATION", "Missing pagination"));
                return null;
            }).when(checker2).check(any(SqlContext.class), any(ValidationResult.class));

            ruleCheckers = Arrays.asList(checker1, checker2);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.BLOCK);

            // Act & Assert
            SQLException exception = assertThrows(SQLException.class, () -> {
                interceptor.willDoQuery(executor, ms, null, RowBounds.DEFAULT, null, boundSql);
            });

            String message = exception.getMessage();
            assertTrue(message.contains("NO_WHERE_CLAUSE"), "Should contain first violation");
            assertTrue(message.contains("NO_PAGINATION"), "Should contain second violation");
        }

        @Test
        @DisplayName("testNoViolations_returnTrue_continueChain - no violations continue chain")
        public void testNoViolations_returnTrue_continueChain() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);
            // No violations added

            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.BLOCK);

            // Act
            boolean result = interceptor.willDoQuery(executor, ms, null, RowBounds.DEFAULT, null, boundSql);

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

            // Act
            interceptor.beforeQuery(executor, ms, null, RowBounds.DEFAULT, null, boundSql);

            // Assert - no exception thrown, method completes
            // Check interceptors don't modify SQL
        }

        @Test
        @DisplayName("testBeforeUpdate_noOp - beforeUpdate is no-op")
        public void testBeforeUpdate_noOp() throws Exception {
            // Arrange
            ruleCheckers = Collections.emptyList();
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            // Act
            interceptor.beforeUpdate(executor, ms, null);

            // Assert - no exception thrown, method completes
        }
    }

    @Nested
    @DisplayName("6. willDoUpdate Tests")
    class WillDoUpdateTests {

        @Test
        @DisplayName("testWillDoUpdate_similarBehavior - willDoUpdate similar to willDoQuery")
        public void testWillDoUpdate_similarBehavior() throws Exception {
            // Arrange
            String sql = "UPDATE users SET name = 'test'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            RuleChecker mockChecker = mock(RuleChecker.class);
            when(mockChecker.isEnabled()).thenReturn(true);
            ruleCheckers = Collections.singletonList(mockChecker);
            interceptor = new SqlGuardCheckInnerInterceptor(ruleCheckers, config, parserFacade);

            when(ms.getBoundSql(any())).thenReturn(boundSql);
            when(boundSql.getSql()).thenReturn(sql);
            when(parserFacade.parse(sql)).thenReturn(stmt);
            when(config.getViolationStrategy()).thenReturn(ViolationStrategy.LOG);

            // Act
            boolean result = interceptor.willDoUpdate(executor, ms, null);

            // Assert
            assertTrue(result, "Should return true to continue chain");
            verify(mockChecker, times(1)).check(any(SqlContext.class), any(ValidationResult.class));
        }
    }
}
```

---

## 验收标准

### 功能验收
- [ ] SqlGuardCheckInnerInterceptor 类创建（`com.footstone.sqlguard.interceptor.inner.impl` 包）
- [ ] 实现 SqlGuardInnerInterceptor 接口
- [ ] getPriority() 返回 10
- [ ] willDoQuery() 实现 Statement 缓存复用逻辑
- [ ] willDoUpdate() 实现 Statement 缓存复用逻辑
- [ ] beforeQuery() 为 no-op
- [ ] beforeUpdate() 为 no-op
- [ ] RuleChecker 桥接逻辑（过滤 enabled，执行 check()）
- [ ] ViolationStrategy 处理（BLOCK/WARN/LOG）

### 测试验收
- [ ] SqlGuardCheckInnerInterceptorTest 全部通过（15 个测试）
- [ ] Priority 测试通过（priority = 10）
- [ ] Statement 缓存测试通过（cache hit/miss）
- [ ] RuleChecker 调用测试通过（enabled/disabled filtering）
- [ ] Violation 处理测试通过（BLOCK throws/WARN logs/LOG logs）
- [ ] No-op 方法测试通过
- [ ] willDoUpdate 测试通过

### 集成验收
- [ ] 与 Phase 12 RuleChecker 系统集成正常
- [ ] StatementContext 缓存复用正常
- [ ] 多 RuleChecker 并发执行正常
- [ ] Violation 聚合正确

### 代码质量验收
- [ ] Javadoc 完整（类级、方法级）
- [ ] 包含使用示例
- [ ] SLF4J 日志记录（debug/info/warn/error）
- [ ] 异常处理正确（SQLException wrapping）

### 构建验收
- [ ] 编译成功
- [ ] 测试通过（15 tests, 0 failures）
- [ ] BUILD SUCCESS

---

## 输出文件

### 新增文件 (2 个)
1. `sql-guard-core/src/main/java/com/footstone/sqlguard/interceptor/inner/impl/SqlGuardCheckInnerInterceptor.java`
2. `sql-guard-core/src/test/java/com/footstone/sqlguard/interceptor/inner/impl/SqlGuardCheckInnerInterceptorTest.java`

---

## 依赖与限制

### 输入依赖
- ✅ Task 13.1: SqlGuardInnerInterceptor 接口
- ✅ Task 13.6: StatementContext 类
- ✅ Phase 12: RuleChecker 接口和所有实现
- ✅ Phase 12: ValidationResult, Violation, SqlContext
- ✅ Phase 12: JSqlParserFacade
- ✅ SqlGuardConfig (ViolationStrategy)
- ✅ MyBatis API (Executor, MappedStatement, BoundSql)

### 限制
- ⚠️ 必须在 SqlGuardInterceptor 之后调用（依赖 StatementContext 初始化）
- ⚠️ ViolationStrategy.BLOCK 会抛出 SQLException，阻止后续拦截器执行
- ⚠️ 依赖 RuleChecker.isEnabled() 正确实现

---

## 注意事项

### 1. ViolationStrategy 处理

**BLOCK vs WARN vs LOG**:
```java
// BLOCK: 抛出 SQLException
if (strategy == BLOCK && hasViolations) {
    throw new SQLException("SQL safety violations: " + message);
}

// WARN: 日志警告，继续执行
if (strategy == WARN && hasViolations) {
    log.warn("SQL safety violations: {}", message);
    return true;  // Continue chain
}

// LOG: 日志信息，继续执行
if (strategy == LOG && hasViolations) {
    log.info("SQL safety violations: {}", message);
    return true;  // Continue chain
}
```

---

### 2. StatementContext 缓存复用

**Cache Hit vs Cache Miss**:
```java
// Try cache first
Statement stmt = StatementContext.get(sql);

if (stmt == null) {
    // Cache miss - parse and cache
    log.debug("Cache miss for SQL: {}", sql);
    stmt = parserFacade.parse(sql);
    StatementContext.cache(sql, stmt);
} else {
    // Cache hit - reuse
    log.debug("Cache hit for SQL: {}", sql);
}
```

**为什么需要缓存复用**:
- SqlGuardInterceptor (主拦截器) 可能已解析并缓存
- SqlGuardCheckInnerInterceptor 可能是第一个 InnerInterceptor
- 避免重复解析（性能优化）

---

### 3. RuleChecker 启用/禁用过滤

```java
// Filter enabled checkers
List<RuleChecker> enabledCheckers = ruleCheckers.stream()
    .filter(RuleChecker::isEnabled)
    .collect(Collectors.toList());

// Execute only enabled checkers
for (RuleChecker checker : enabledCheckers) {
    checker.check(context, result);
}
```

---

### 4. Violation 聚合

**多个 RuleChecker 可能检测多个违规**:
```java
ValidationResult result = new ValidationResult(sql);

// Checker 1 adds violation
checker1.check(context, result);  // result.addViolation(...)

// Checker 2 adds violation
checker2.check(context, result);  // result.addViolation(...)

// Aggregate all violations in result
if (result.hasViolations()) {
    // Handle all violations at once
}
```

---

### 5. SqlContext 构建

**必须包含 statement 字段**:
```java
SqlContext context = SqlContext.builder()
    .sql(sql)
    .statement(stmt)  // ⚠️ Phase 12 要求
    .statementId(ms.getId())
    .build();
```

**Phase 12 的 RuleChecker 依赖 statement 字段**:
```java
// NoWhereClauseChecker.check()
Statement stmt = context.getStatement();  // Phase 12 使用 statement
if (stmt instanceof Select) {
    // ...
}
```

---

### 6. 测试 Mock 策略

**需要 Mock 的对象**:
- Executor (MyBatis)
- MappedStatement (MyBatis)
- BoundSql (MyBatis)
- RuleChecker (Phase 12)
- SqlGuardConfig
- JSqlParserFacade

**需要真实对象**:
- Statement (JSqlParser) - 使用 CCJSqlParserUtil.parse()
- ValidationResult
- Violation

---

## Memory Logging

Upon completion, you **MUST** log work in:
```
.apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_3_CheckInnerInterceptor_Implementation.md
```

Follow `.apm/guides/Memory_Log_Guide.md` instructions for proper formatting.

**Required Log Sections**:
- Summary: 1-2 sentences describing SqlGuardCheckInnerInterceptor implementation outcome
- Details: Priority (10), RuleChecker bridging, ViolationStrategy handling, StatementContext caching
- Output: List of created files, test results (15 tests)
- Issues: Any RuleChecker integration issues or "None"
- Next Steps: Task 13.2 (Main Interceptor) or Task 13.5 (Limit Interceptor) can proceed

---

## 执行时间线

- **预计时间**: 2 工作日
  - Day 1 上午：实现 SqlGuardCheckInnerInterceptor 核心逻辑
  - Day 1 下午：实现 ViolationStrategy 处理和 RuleChecker 桥接
  - Day 2 上午：编写 TDD 测试（15 个测试）
  - Day 2 下午：集成测试和验收

---

**Created**: 2025-12-22
**Phase**: 13 - InnerInterceptor Architecture
**Task ID**: 13.3
**Priority**: ⭐ Highest (最重要的 InnerInterceptor)
**Parallel**: Can run in parallel with Task 13.2, 13.4, 13.5 (after 13.1 + 13.6 complete)
