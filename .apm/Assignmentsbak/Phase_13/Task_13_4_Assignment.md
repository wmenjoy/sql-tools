---
task_ref: "Task 13.4 - SqlGuardRewriteInnerInterceptor Implementation"
agent_assignment: "Agent_Advanced_Interceptor"
memory_log_path: ".apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_4_RewriteInnerInterceptor_Implementation.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
phase: 13
task_id: 13.4
estimated_duration: 2 days
dependencies: [Task_13.1, Task_13.6]
parallel_with: [Task_13.2, Task_13.3, Task_13.5]
priority: "Optional"
---

# Task 13.4 Assignment: SqlGuardRewriteInnerInterceptor Implementation

## 任务目标

实现 `SqlGuardRewriteInnerInterceptor` 桥接 StatementRewriter 和 InnerInterceptor，getPriority() 返回 200（低优先级用于重写），在 beforeQuery 中从 StatementContext 获取 Statement（复用 CheckInnerInterceptor 的解析），执行所有 StatementRewriter.rewrite()，如果重写返回新 Statement 则使用反射替换 BoundSql 中的 SQL，支持链式重写（每次重写后更新 SqlContext 和 StatementContext 供下一个 rewriter 使用）。

---

## ⚠️ 任务优先级说明

**这是一个可选的高级功能任务**:
- Priority: 200（最低优先级）
- 可以在 Phase 13 的核心功能（Task 13.2, 13.3, 13.5）完成后实施
- 不影响 Phase 13 的核心安全检查和降级功能
- 适合作为扩展功能在后续迭代中实现

**建议**:
1. 优先完成 Task 13.2（主拦截器）和 Task 13.5（降级拦截器）
2. 如果时间充裕，再实施 Task 13.4

---

## 背景说明

### 为什么需要 SqlGuardRewriteInnerInterceptor？

**SQL 重写场景**:
```java
// 场景 1: 添加租户隔离
原始SQL: SELECT * FROM users WHERE id = 1;
重写后: SELECT * FROM users WHERE id = 1 AND tenant_id = 'xxx';

// 场景 2: 添加软删除过滤
原始SQL: SELECT * FROM users;
重写后: SELECT * FROM users WHERE deleted = 0;

// 场景 3: 强制添加 ORDER BY
原始SQL: SELECT * FROM orders;
重写后: SELECT * FROM orders ORDER BY create_time DESC;
```

**Priority 200 的原因**:
- Priority 10: SqlGuardCheckInnerInterceptor (检查 - 必须先执行)
- Priority 150: SelectLimitInnerInterceptor (降级)
- Priority **200**: SqlGuardRewriteInnerInterceptor (重写 - 最后执行) ⭐

**为什么重写拦截器优先级最低**:
1. 检查拦截器必须先执行（检测违规）
2. 降级拦截器其次（添加 LIMIT）
3. 重写拦截器最后（自定义 SQL 修改）

---

### StatementRewriter 接口

**需要先定义 StatementRewriter 接口**:

```java
package com.footstone.sqlguard.rewriter;

import com.footstone.sqlguard.core.model.SqlContext;
import net.sf.jsqlparser.statement.Statement;

/**
 * Interface for SQL statement rewriters.
 *
 * <h2>Purpose</h2>
 * <p>Allows custom SQL modifications such as:
 * <ul>
 *   <li>Adding tenant isolation WHERE clauses</li>
 *   <li>Forcing ORDER BY clauses</li>
 *   <li>Adding soft-delete filters</li>
 *   <li>Column-level access control</li>
 * </ul>
 *
 * <h2>Chain Rewrite Support</h2>
 * <p>Multiple rewriters can be chained. Each rewriter receives the Statement
 * modified by previous rewriters in the chain.
 *
 * @since 1.1.0
 */
public interface StatementRewriter {

    /**
     * Rewrites the SQL Statement.
     *
     * <p>Returns a new Statement if modification is needed, or the original
     * Statement if no rewrite is necessary.
     *
     * @param statement Original Statement (or modified by previous rewriters)
     * @param context   SqlContext containing SQL metadata
     * @return Rewritten Statement, or original if no rewrite needed
     */
    Statement rewrite(Statement statement, SqlContext context);

    /**
     * Checks if this rewriter is enabled.
     *
     * @return {@code true} if enabled, {@code false} to skip
     */
    boolean isEnabled();
}
```

---

### BoundSql 反射修改

**问题**: MyBatis BoundSql.sql 字段是 final

```java
public class BoundSql {
    private final String sql;  // ❌ final field cannot be modified directly
    // ...
}
```

**解决方案**: 使用反射

```java
Field sqlField = BoundSql.class.getDeclaredField("sql");
sqlField.setAccessible(true);
sqlField.set(boundSql, newSql);  // ✅ Bypass final modifier
```

**MyBatis 3.4.x / 3.5.x 兼容性**:
- 两个版本的 BoundSql 结构相同
- 反射代码适用于两个版本

---

### 链式重写支持

**问题**: 多个 Rewriter 需要串联执行

```java
// Rewriter 1: 添加 WHERE tenant_id = 'xxx'
SELECT * FROM users;
    ↓
SELECT * FROM users WHERE tenant_id = 'xxx';

// Rewriter 2: 添加 ORDER BY
SELECT * FROM users WHERE tenant_id = 'xxx';
    ↓
SELECT * FROM users WHERE tenant_id = 'xxx' ORDER BY create_time DESC;
```

**解决方案**: 每次重写后更新 SqlContext 和 StatementContext

```java
for (StatementRewriter rewriter : enabledRewriters) {
    Statement newStatement = rewriter.rewrite(currentStatement, context);

    if (newStatement != currentStatement) {
        // Statement changed - update for next rewriter
        currentStatement = newStatement;

        // Update SqlContext
        context = SqlContext.builder()
                .statement(currentStatement)
                .sql(currentStatement.toString())
                .build();

        // Update ThreadLocal cache
        StatementContext.cache(newSql, currentStatement);
    }
}
```

---

## 实现要求

### 1. StatementRewriter 接口（先创建）

**文件**: `sql-guard-core/src/main/java/com/footstone/sqlguard/rewriter/StatementRewriter.java`

**完整代码**:

```java
package com.footstone.sqlguard.rewriter;

import com.footstone.sqlguard.core.model.SqlContext;
import net.sf.jsqlparser.statement.Statement;

/**
 * Interface for SQL statement rewriters providing custom SQL modification capabilities.
 *
 * <h2>Purpose</h2>
 * <p>Enables custom SQL transformations such as:
 * <ul>
 *   <li><b>Tenant Isolation:</b> Adding {@code WHERE tenant_id = ?} clauses</li>
 *   <li><b>Soft Delete:</b> Adding {@code WHERE deleted = 0} filters</li>
 *   <li><b>Audit Columns:</b> Forcing {@code ORDER BY update_time DESC}</li>
 *   <li><b>Column Masking:</b> Replacing sensitive columns with masked values</li>
 * </ul>
 *
 * <h2>Chain Rewrite Support</h2>
 * <p>Multiple rewriters can be chained via {@code SqlGuardRewriteInnerInterceptor}.
 * Each rewriter receives the Statement modified by previous rewriters:
 * <pre>
 * Original SQL → Rewriter1 → Rewriter2 → Rewriter3 → Final SQL
 * </pre>
 *
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * public class TenantIsolationRewriter implements StatementRewriter {
 *     @Override
 *     public Statement rewrite(Statement statement, SqlContext context) {
 *         if (statement instanceof Select) {
 *             Select select = (Select) statement;
 *             // Add WHERE tenant_id = ? clause
 *             // ...
 *             return select; // Return modified Statement
 *         }
 *         return statement; // No modification
 *     }
 *
 *     @Override
 *     public boolean isEnabled() {
 *         return true;
 *     }
 * }
 * }</pre>
 *
 * @see SqlGuardRewriteInnerInterceptor
 * @since 1.1.0
 */
public interface StatementRewriter {

    /**
     * Rewrites the SQL Statement, returning a new Statement if modified.
     *
     * <p><b>Contract:</b>
     * <ul>
     *   <li>Return new Statement if modification is needed</li>
     *   <li>Return original Statement unchanged if no rewrite required</li>
     *   <li>Never return {@code null}</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b> Implementation must be thread-safe as rewriters
     * are shared across requests.
     *
     * @param statement Original Statement (or modified by previous rewriters in chain)
     * @param context   SqlContext containing SQL metadata (statement ID, mapperId, etc.)
     * @return Rewritten Statement (new instance if modified, or original if unchanged)
     * @throws RuntimeException If rewrite fails (will be logged and propagated)
     */
    Statement rewrite(Statement statement, SqlContext context);

    /**
     * Checks if this rewriter is enabled.
     *
     * <p>Disabled rewriters are skipped in the rewrite chain, improving performance
     * when certain rewrites are conditionally disabled (e.g., tenant isolation
     * disabled for admin users).
     *
     * @return {@code true} if rewriter should be invoked, {@code false} to skip
     */
    boolean isEnabled();
}
```

---

### 2. SqlGuardRewriteInnerInterceptor 实现

**包路径**: `com.footstone.sqlguard.interceptor.inner.impl`

**完整实现**:

```java
package com.footstone.sqlguard.interceptor.inner.impl;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.interceptor.inner.SqlGuardInnerInterceptor;
import com.footstone.sqlguard.parser.StatementContext;
import com.footstone.sqlguard.rewriter.StatementRewriter;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * InnerInterceptor for executing StatementRewriter chain with chain-rewrite support.
 *
 * <h2>Purpose</h2>
 * <p>Bridges {@link StatementRewriter} implementations with the InnerInterceptor chain,
 * allowing custom SQL modifications such as tenant isolation, soft-delete filtering,
 * and column masking.
 *
 * <h2>Priority</h2>
 * <p>Priority: <b>200</b> (rewrite interceptor) - runs AFTER check (10) and fallback (150)
 * interceptors. This ensures SQL safety checks and LIMIT fallbacks are applied before
 * custom rewrites.
 *
 * <h2>Chain Rewrite Support</h2>
 * <p>Multiple rewriters are executed sequentially, with each rewriter receiving the
 * Statement modified by previous rewriters:
 * <pre>
 * Original SQL
 *     ↓
 * Rewriter 1 (add tenant filter)
 *     ↓
 * Rewriter 2 (add soft-delete filter)
 *     ↓
 * Rewriter 3 (force ORDER BY)
 *     ↓
 * Final SQL
 * </pre>
 *
 * <p>After each rewrite, updates:
 * <ul>
 *   <li>{@code SqlContext.statement} for next rewriter</li>
 *   <li>{@code StatementContext.cache()} ThreadLocal cache</li>
 *   <li>{@code BoundSql.sql} field via reflection</li>
 * </ul>
 *
 * <h2>BoundSql Modification via Reflection</h2>
 * <p>MyBatis {@code BoundSql.sql} field is final, requiring reflection:
 * <pre>{@code
 * Field sqlField = BoundSql.class.getDeclaredField("sql");
 * sqlField.setAccessible(true);
 * sqlField.set(boundSql, newSql);
 * }</pre>
 *
 * @see StatementRewriter
 * @see StatementContext
 * @since 1.1.0
 */
public class SqlGuardRewriteInnerInterceptor implements SqlGuardInnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlGuardRewriteInnerInterceptor.class);

    /**
     * StatementRewriter instances (injected via constructor).
     */
    private final List<StatementRewriter> rewriters;

    /**
     * Cached reflection Field for BoundSql.sql (performance optimization).
     */
    private static Field boundSqlSqlField;

    static {
        try {
            boundSqlSqlField = BoundSql.class.getDeclaredField("sql");
            boundSqlSqlField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            log.error("Failed to access BoundSql.sql field via reflection", e);
        }
    }

    /**
     * Constructs SqlGuardRewriteInnerInterceptor with StatementRewriter list.
     *
     * @param rewriters List of StatementRewriter instances
     */
    public SqlGuardRewriteInnerInterceptor(List<StatementRewriter> rewriters) {
        this.rewriters = rewriters;
    }

    /**
     * Returns priority 200 (rewrite interceptor runs after checks and fallbacks).
     *
     * @return Priority value 200
     */
    @Override
    public int getPriority() {
        return 200;
    }

    /**
     * willDoQuery is no-op for rewrite interceptors (no pre-filtering).
     *
     * @return {@code true} to continue chain
     */
    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms, Object parameter,
                                RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        return true; // Rewriters don't pre-filter
    }

    /**
     * willDoUpdate is no-op for rewrite interceptors.
     *
     * @return {@code true} to continue chain
     */
    @Override
    public boolean willDoUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        return true;
    }

    /**
     * Executes StatementRewriter chain and updates BoundSql if Statement is modified.
     *
     * <p>Workflow:
     * <ol>
     *   <li>Get Statement from {@code StatementContext.get(sql)} (reuse CheckInnerInterceptor's parse)</li>
     *   <li>Build SqlContext with statement</li>
     *   <li>Iterate all enabled StatementRewriters:
     *     <ul>
     *       <li>Call {@code rewriter.rewrite(statement, context)}</li>
     *       <li>If Statement changed: update SqlContext, StatementContext, BoundSql</li>
     *     </ul>
     *   </li>
     *   <li>Replace SQL in BoundSql via reflection</li>
     * </ol>
     *
     * @param executor      MyBatis Executor
     * @param ms            MappedStatement
     * @param parameter     SQL parameter
     * @param rowBounds     Pagination bounds
     * @param resultHandler Result handler
     * @param boundSql      BoundSql containing SQL
     */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                             RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        executeRewrites(ms, boundSql);
    }

    /**
     * Executes StatementRewriter chain for update operations.
     *
     * @param executor  MyBatis Executor
     * @param ms        MappedStatement
     * @param parameter SQL parameter
     */
    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        BoundSql boundSql = ms.getBoundSql(parameter);
        executeRewrites(ms, boundSql);
    }

    /**
     * Executes all enabled StatementRewriters and updates BoundSql.
     *
     * @param ms       MappedStatement
     * @param boundSql BoundSql to modify
     */
    private void executeRewrites(MappedStatement ms, BoundSql boundSql) throws SQLException {
        String originalSql = boundSql.getSql();

        // 1. Get Statement from ThreadLocal cache
        Statement currentStatement = StatementContext.get(originalSql);

        if (currentStatement == null) {
            log.trace("StatementContext cache miss, skipping rewrites");
            return; // No cached Statement, cannot rewrite
        }

        // 2. Build SqlContext
        SqlContext context = SqlContext.builder()
                .sql(originalSql)
                .statement(currentStatement)
                .mapperId(ms.getId())
                .type(SqlCommandType.SELECT) // Simplified: assume SELECT
                .build();

        // 3. Execute rewrite chain
        List<StatementRewriter> enabledRewriters = rewriters.stream()
                .filter(StatementRewriter::isEnabled)
                .collect(Collectors.toList());

        boolean modified = false;

        for (StatementRewriter rewriter : enabledRewriters) {
            log.trace("Executing StatementRewriter: {}", rewriter.getClass().getSimpleName());

            Statement newStatement = rewriter.rewrite(currentStatement, context);

            if (newStatement != currentStatement) {
                // Statement was modified
                log.debug("Statement modified by {}", rewriter.getClass().getSimpleName());

                currentStatement = newStatement;
                String newSql = currentStatement.toString();

                // Update SqlContext for next rewriter
                context = SqlContext.builder()
                        .sql(newSql)
                        .statement(currentStatement)
                        .mapperId(ms.getId())
                        .type(SqlCommandType.SELECT)
                        .build();

                // Update ThreadLocal cache for next rewriter
                StatementContext.cache(newSql, currentStatement);

                modified = true;
            }
        }

        // 4. Replace SQL in BoundSql if modified
        if (modified) {
            String finalSql = currentStatement.toString();
            replaceBoundSqlSql(boundSql, finalSql);
            log.info("SQL rewritten: {} → {}", originalSql, finalSql);
        }
    }

    /**
     * Replaces SQL in BoundSql using reflection.
     *
     * <p>MyBatis {@code BoundSql.sql} field is final, requiring reflection to modify.
     *
     * @param boundSql BoundSql instance
     * @param newSql   New SQL string
     * @throws SQLException If reflection fails
     */
    private void replaceBoundSqlSql(BoundSql boundSql, String newSql) throws SQLException {
        try {
            if (boundSqlSqlField == null) {
                throw new SQLException("BoundSql.sql field reflection not initialized");
            }

            boundSqlSqlField.set(boundSql, newSql);
        } catch (IllegalAccessException e) {
            log.error("Failed to replace SQL in BoundSql via reflection", e);
            throw new SQLException("Failed to replace SQL in BoundSql", e);
        }
    }
}
```

---

## 验收标准

### 功能验收
- [ ] StatementRewriter 接口创建
- [ ] SqlGuardRewriteInnerInterceptor 类创建
- [ ] getPriority() 返回 200
- [ ] willDoQuery/willDoUpdate 为 no-op（返回 true）
- [ ] beforeQuery() 执行 rewrite 链
- [ ] beforeUpdate() 执行 rewrite 链
- [ ] Statement 从 StatementContext 复用
- [ ] StatementRewriter.isEnabled() 过滤正确
- [ ] 链式重写支持（每次重写后更新 SqlContext 和 StatementContext）
- [ ] BoundSql 反射修改正确

### 测试验收
- [ ] SqlGuardRewriteInnerInterceptorTest 全部通过（12 个测试）
- [ ] Priority 测试通过（priority = 200）
- [ ] willDoQuery/willDoUpdate no-op 测试通过
- [ ] Statement 缓存复用测试通过
- [ ] Rewriter 调用测试通过（enabled/disabled filtering）
- [ ] BoundSql 反射修改测试通过
- [ ] 链式重写测试通过（2 个 rewriter 串联）
- [ ] SqlContext 更新测试通过
- [ ] StatementContext 更新测试通过
- [ ] MyBatis 3.4.x / 3.5.x 兼容性测试通过

### 集成验收
- [ ] 与 StatementContext 集成正常
- [ ] 多 StatementRewriter 链式执行正常
- [ ] BoundSql 修改对 MyBatis 可见

### 代码质量验收
- [ ] Javadoc 完整（接口、类、方法）
- [ ] 包含使用示例
- [ ] SLF4J 日志记录
- [ ] 异常处理正确
- [ ] 反射代码安全（AccessibleObject.setAccessible）

### 构建验收
- [ ] 编译成功
- [ ] 测试通过（12 tests, 0 failures）
- [ ] BUILD SUCCESS

---

## 输出文件

### 新增文件 (3 个)
1. `sql-guard-core/src/main/java/com/footstone/sqlguard/rewriter/StatementRewriter.java`
2. `sql-guard-core/src/main/java/com/footstone/sqlguard/interceptor/inner/impl/SqlGuardRewriteInnerInterceptor.java`
3. `sql-guard-core/src/test/java/com/footstone/sqlguard/interceptor/inner/impl/SqlGuardRewriteInnerInterceptorTest.java`

---

## 依赖与限制

### 输入依赖
- ✅ Task 13.1: SqlGuardInnerInterceptor 接口
- ✅ Task 13.6: StatementContext 类
- ✅ Phase 12: SqlContext
- ✅ MyBatis API (BoundSql)
- ✅ JSqlParser (Statement)

### 限制
- ⚠️ 依赖反射修改 BoundSql.sql final 字段
- ⚠️ 反射可能在某些 JVM 安全配置下失败
- ⚠️ 链式重写性能开销（每次重写重新解析 SQL）

---

## 注意事项

### 1. BoundSql 反射风险

**问题**: 修改 final 字段

```java
public class BoundSql {
    private final String sql;  // final field
}
```

**解决方案**:
```java
Field sqlField = BoundSql.class.getDeclaredField("sql");
sqlField.setAccessible(true);  // Bypass access check
sqlField.set(boundSql, newSql); // Modify final field
```

**风险**:
- JDK 9+ 模块系统可能阻止反射
- SecurityManager 可能阻止 setAccessible
- 需要测试 MyBatis 3.4.x 和 3.5.x

---

### 2. 链式重写的重要性

**为什么需要链式重写**:
```java
// Rewriter 1: 添加租户过滤
SELECT * FROM users
    ↓
SELECT * FROM users WHERE tenant_id = 'xxx'

// Rewriter 2: 添加软删除过滤
SELECT * FROM users WHERE tenant_id = 'xxx'
    ↓
SELECT * FROM users WHERE tenant_id = 'xxx' AND deleted = 0

// Rewriter 3: 强制 ORDER BY
SELECT * FROM users WHERE tenant_id = 'xxx' AND deleted = 0
    ↓
SELECT * FROM users WHERE tenant_id = 'xxx' AND deleted = 0 ORDER BY create_time DESC
```

**实现**:
```java
Statement current = originalStatement;

for (StatementRewriter rewriter : rewriters) {
    Statement next = rewriter.rewrite(current, context);

    if (next != current) {
        // Update for next rewriter
        current = next;
        context = buildNewContext(next);
        StatementContext.cache(newSql, next);
    }
}
```

---

### 3. StatementRewriter 示例实现

**租户隔离 Rewriter**:
```java
public class TenantIsolationRewriter implements StatementRewriter {
    private final String tenantId;

    @Override
    public Statement rewrite(Statement statement, SqlContext context) {
        if (statement instanceof Select) {
            Select select = (Select) statement;
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

            // Add WHERE tenant_id = ?
            Expression tenantFilter = new EqualsTo()
                .withLeftExpression(new Column("tenant_id"))
                .withRightExpression(new StringValue(tenantId));

            Expression existingWhere = plainSelect.getWhere();
            if (existingWhere != null) {
                // Combine with AND
                plainSelect.setWhere(new AndExpression(existingWhere, tenantFilter));
            } else {
                plainSelect.setWhere(tenantFilter);
            }

            return select;
        }

        return statement; // No modification
    }

    @Override
    public boolean isEnabled() {
        return tenantId != null;
    }
}
```

---

### 4. 测试策略

**需要测试的场景**:
1. **单个 Rewriter**: 验证基本重写功能
2. **链式 Rewriter**: 验证多个 rewriter 串联
3. **Enabled/Disabled**: 验证 isEnabled() 过滤
4. **BoundSql 反射**: 验证 SQL 替换成功
5. **StatementContext 更新**: 验证 ThreadLocal 缓存更新
6. **SqlContext 更新**: 验证下一个 rewriter 看到修改后的 Statement

---

## Memory Logging

Upon completion, you **MUST** log work in:
```
.apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_4_RewriteInnerInterceptor_Implementation.md
```

**Required Log Sections**:
- Summary: SqlGuardRewriteInnerInterceptor implementation outcome
- Details: StatementRewriter bridge, chain rewrite support, BoundSql reflection
- Output: List of created files (3 files), test results (12 tests)
- Issues: Any reflection or chain rewrite issues or "None"
- Next Steps: Optional extension - can be implemented after Phase 13 core features

---

## 执行时间线

- **预计时间**: 2 工作日
  - Day 1 上午：定义 StatementRewriter 接口
  - Day 1 下午：实现 SqlGuardRewriteInnerInterceptor 核心逻辑
  - Day 2 上午：实现链式重写和 BoundSql 反射
  - Day 2 下午：编写 TDD 测试（12 个测试）

---

**Created**: 2025-12-22
**Phase**: 13 - InnerInterceptor Architecture
**Task ID**: 13.4
**Priority**: ⚠️ **Optional** (可选的高级功能，优先级最低)
**Parallel**: Can run in parallel with Task 13.2, 13.3, 13.5 (after 13.1 + 13.6 complete)
