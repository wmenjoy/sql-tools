---
task_ref: "Task 13.1 - SqlGuardInnerInterceptor Interface Design"
agent_assignment: "Agent_Advanced_Interceptor"
memory_log_path: ".apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_1_InnerInterceptor_Interface_Design.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
phase: 13
task_id: 13.1
estimated_duration: 1 day
dependencies: [Phase_12_Complete]
parallel_with: []
---

# Task 13.1 Assignment: SqlGuardInnerInterceptor Interface Design

## 任务目标

设计 `SqlGuardInnerInterceptor` 接口，参考 MyBatis-Plus InnerInterceptor 模式，定义拦截器生命周期方法（willDoQuery/beforeQuery/willDoUpdate/beforeUpdate）和优先级机制（getPriority），提供完整的 Javadoc 和集成示例。

---

## 背景说明

### MyBatis-Plus InnerInterceptor 模式

MyBatis-Plus 提供了 `InnerInterceptor` 接口，允许在 MyBatis 拦截器链中插入多个子拦截器，每个子拦截器负责特定功能（如分页、多租户、SQL 审计等）。

**核心特性**:
1. **生命周期方法**: `willDoQuery()` / `beforeQuery()` / `willDoUpdate()` / `beforeUpdate()`
2. **优先级控制**: `default int getPriority() { return 50; }`，数字越小优先级越高
3. **链式调用**: 所有 InnerInterceptor 按优先级顺序执行

**MyBatis-Plus InnerInterceptor 示例**:
```java
public interface InnerInterceptor {

    /**
     * 查询前置处理
     * @return false: 跳过后续拦截器和查询执行
     */
    default boolean willDoQuery(Executor executor, MappedStatement ms,
                                Object parameter, RowBounds rowBounds,
                                ResultHandler resultHandler, BoundSql boundSql) {
        return true;
    }

    /**
     * 查询后置处理（修改 SQL）
     */
    default void beforeQuery(Executor executor, MappedStatement ms,
                            Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql) {
        // SQL modification logic
    }

    /**
     * 更新前置处理
     * @return false: 跳过后续拦截器和更新执行
     */
    default boolean willDoUpdate(Executor executor, MappedStatement ms,
                                 Object parameter) {
        return true;
    }

    /**
     * 更新后置处理（修改 SQL）
     */
    default void beforeUpdate(Executor executor, MappedStatement ms,
                             Object parameter) {
        // SQL modification logic
    }

    /**
     * 获取拦截器优先级
     * @return 优先级数字，越小越优先
     */
    default int getPriority() {
        return 50;
    }
}
```

---

## 实现要求

### 1. 接口设计

**包路径**: `com.footstone.sqlguard.interceptor.inner`

**接口名**: `SqlGuardInnerInterceptor`

**方法签名**:

```java
package com.footstone.sqlguard.interceptor.inner;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;

/**
 * Inner interceptor interface for SQL Guard.
 *
 * <p>This interface follows the MyBatis-Plus InnerInterceptor pattern, allowing
 * multiple interceptors to be chained together with priority-based execution order.
 *
 * <h2>Lifecycle Methods</h2>
 *
 * <p>Each interceptor can implement lifecycle methods for different SQL operations:
 * <ul>
 *   <li><b>willDoQuery()</b>: Called before query execution. Return false to skip query.</li>
 *   <li><b>beforeQuery()</b>: Called after willDoQuery(). Can modify BoundSql.</li>
 *   <li><b>willDoUpdate()</b>: Called before INSERT/UPDATE/DELETE execution. Return false to skip.</li>
 *   <li><b>beforeUpdate()</b>: Called after willDoUpdate(). Can modify BoundSql.</li>
 * </ul>
 *
 * <h2>Priority Mechanism</h2>
 *
 * <p>Interceptors are executed in priority order (lower number = higher priority):
 * <ul>
 *   <li><b>1-99</b>: Check interceptors (e.g., SqlGuardCheckInnerInterceptor priority = 10)</li>
 *   <li><b>100-199</b>: Fallback interceptors (e.g., SelectLimitInnerInterceptor priority = 100)</li>
 *   <li><b>200+</b>: Rewrite interceptors (custom SQL rewriters)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * public class MyCustomInterceptor implements SqlGuardInnerInterceptor {
 *
 *     @Override
 *     public boolean willDoQuery(Executor executor, MappedStatement ms,
 *                               Object parameter, RowBounds rowBounds,
 *                               ResultHandler resultHandler, BoundSql boundSql)
 *             throws SQLException {
 *         // Check if query should be allowed
 *         if (shouldBlock(boundSql.getSql())) {
 *             throw new SQLException("Query blocked by custom interceptor");
 *         }
 *         return true;  // Continue to next interceptor
 *     }
 *
 *     @Override
 *     public void beforeQuery(Executor executor, MappedStatement ms,
 *                            Object parameter, RowBounds rowBounds,
 *                            ResultHandler resultHandler, BoundSql boundSql)
 *             throws SQLException {
 *         // Modify SQL or log query
 *         String sql = boundSql.getSql();
 *         System.out.println("Executing query: " + sql);
 *     }
 *
 *     @Override
 *     public int getPriority() {
 *         return 20;  // Execute after SqlGuardCheckInnerInterceptor (priority 10)
 *     }
 * }
 * }</pre>
 *
 * <h2>Integration with SqlGuardInterceptor</h2>
 *
 * <p>The main {@code SqlGuardInterceptor} orchestrates all registered InnerInterceptors:
 * <ol>
 *   <li>Sort InnerInterceptors by priority (ascending order)</li>
 *   <li>For query operations:
 *     <ul>
 *       <li>Invoke {@code willDoQuery()} on each interceptor (stop if any returns false)</li>
 *       <li>Invoke {@code beforeQuery()} on each interceptor (modifies BoundSql if needed)</li>
 *     </ul>
 *   </li>
 *   <li>For update operations (INSERT/UPDATE/DELETE):
 *     <ul>
 *       <li>Invoke {@code willDoUpdate()} on each interceptor (stop if any returns false)</li>
 *       <li>Invoke {@code beforeUpdate()} on each interceptor (modifies BoundSql if needed)</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * @since 1.1.0
 */
public interface SqlGuardInnerInterceptor {

    /**
     * Determines whether to proceed with query execution.
     *
     * <p>This method is called before {@link #beforeQuery(Executor, MappedStatement, Object, RowBounds, ResultHandler, BoundSql)}.
     * If this method returns {@code false}, the query execution will be skipped and no further
     * interceptors will be invoked.
     *
     * @param executor      MyBatis Executor
     * @param ms            MappedStatement
     * @param parameter     Query parameter
     * @param rowBounds     RowBounds for pagination
     * @param resultHandler Result handler
     * @param boundSql      BoundSql containing SQL and parameter mappings
     * @return {@code true} to continue query execution, {@code false} to skip
     * @throws SQLException if an error occurs during pre-check
     */
    default boolean willDoQuery(Executor executor, MappedStatement ms,
                               Object parameter, RowBounds rowBounds,
                               ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        return true;
    }

    /**
     * Modifies or validates the query before execution.
     *
     * <p>This method is called after {@link #willDoQuery(Executor, MappedStatement, Object, RowBounds, ResultHandler, BoundSql)}.
     * Interceptors can modify the SQL by updating the BoundSql object or perform additional validation.
     *
     * @param executor      MyBatis Executor
     * @param ms            MappedStatement
     * @param parameter     Query parameter
     * @param rowBounds     RowBounds for pagination
     * @param resultHandler Result handler
     * @param boundSql      BoundSql containing SQL and parameter mappings
     * @throws SQLException if validation fails or an error occurs
     */
    default void beforeQuery(Executor executor, MappedStatement ms,
                            Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        // Default: no-op
    }

    /**
     * Determines whether to proceed with update execution (INSERT/UPDATE/DELETE).
     *
     * <p>This method is called before {@link #beforeUpdate(Executor, MappedStatement, Object)}.
     * If this method returns {@code false}, the update execution will be skipped and no further
     * interceptors will be invoked.
     *
     * @param executor  MyBatis Executor
     * @param ms        MappedStatement
     * @param parameter Update parameter
     * @return {@code true} to continue update execution, {@code false} to skip
     * @throws SQLException if an error occurs during pre-check
     */
    default boolean willDoUpdate(Executor executor, MappedStatement ms,
                                 Object parameter)
            throws SQLException {
        return true;
    }

    /**
     * Modifies or validates the update SQL before execution.
     *
     * <p>This method is called after {@link #willDoUpdate(Executor, MappedStatement, Object)}.
     * Interceptors can modify the SQL or perform additional validation.
     *
     * @param executor  MyBatis Executor
     * @param ms        MappedStatement
     * @param parameter Update parameter
     * @throws SQLException if validation fails or an error occurs
     */
    default void beforeUpdate(Executor executor, MappedStatement ms,
                             Object parameter)
            throws SQLException {
        // Default: no-op
    }

    /**
     * Returns the priority of this interceptor.
     *
     * <p>Lower priority number means higher execution priority (executes first).
     *
     * <h3>Priority Ranges:</h3>
     * <ul>
     *   <li><b>1-99</b>: Check interceptors (e.g., SqlGuardCheckInnerInterceptor = 10)</li>
     *   <li><b>100-199</b>: Fallback interceptors (e.g., SelectLimitInnerInterceptor = 100)</li>
     *   <li><b>200+</b>: Rewrite interceptors (custom SQL rewriters)</li>
     * </ul>
     *
     * @return priority number (default: 50)
     */
    default int getPriority() {
        return 50;
    }
}
```

---

### 2. TDD 测试用例设计

**测试类**: `sql-guard-core/src/test/java/com/footstone/sqlguard/interceptor/inner/InnerInterceptorInterfaceDesignTest.java`

**测试用例 (8 个)**:

```java
package com.footstone.sqlguard.interceptor.inner;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests verifying SqlGuardInnerInterceptor interface design.
 */
@DisplayName("InnerInterceptor Interface Design Tests")
public class InnerInterceptorInterfaceDesignTest {

    @Nested
    @DisplayName("1. Interface Method Existence Tests")
    class InterfaceMethodExistenceTests {

        @Test
        @DisplayName("testInterface_hasWillDoQueryMethod - willDoQuery method exists")
        public void testInterface_hasWillDoQueryMethod() throws NoSuchMethodException {
            // Verify willDoQuery method signature
            Method method = SqlGuardInnerInterceptor.class.getMethod(
                "willDoQuery",
                Executor.class, MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, BoundSql.class
            );

            assertNotNull(method, "willDoQuery method should exist");
            assertEquals(boolean.class, method.getReturnType(),
                "willDoQuery should return boolean");
        }

        @Test
        @DisplayName("testInterface_hasBeforeQueryMethod - beforeQuery method exists")
        public void testInterface_hasBeforeQueryMethod() throws NoSuchMethodException {
            // Verify beforeQuery method signature
            Method method = SqlGuardInnerInterceptor.class.getMethod(
                "beforeQuery",
                Executor.class, MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, BoundSql.class
            );

            assertNotNull(method, "beforeQuery method should exist");
            assertEquals(void.class, method.getReturnType(),
                "beforeQuery should return void");
        }

        @Test
        @DisplayName("testInterface_hasWillDoUpdateMethod - willDoUpdate method exists")
        public void testInterface_hasWillDoUpdateMethod() throws NoSuchMethodException {
            // Verify willDoUpdate method signature
            Method method = SqlGuardInnerInterceptor.class.getMethod(
                "willDoUpdate",
                Executor.class, MappedStatement.class, Object.class
            );

            assertNotNull(method, "willDoUpdate method should exist");
            assertEquals(boolean.class, method.getReturnType(),
                "willDoUpdate should return boolean");
        }

        @Test
        @DisplayName("testInterface_hasBeforeUpdateMethod - beforeUpdate method exists")
        public void testInterface_hasBeforeUpdateMethod() throws NoSuchMethodException {
            // Verify beforeUpdate method signature
            Method method = SqlGuardInnerInterceptor.class.getMethod(
                "beforeUpdate",
                Executor.class, MappedStatement.class, Object.class
            );

            assertNotNull(method, "beforeUpdate method should exist");
            assertEquals(void.class, method.getReturnType(),
                "beforeUpdate should return void");
        }

        @Test
        @DisplayName("testInterface_hasGetPriorityMethod - getPriority method exists")
        public void testInterface_hasGetPriorityMethod() throws NoSuchMethodException {
            // Verify getPriority method signature
            Method method = SqlGuardInnerInterceptor.class.getMethod("getPriority");

            assertNotNull(method, "getPriority method should exist");
            assertEquals(int.class, method.getReturnType(),
                "getPriority should return int");
        }
    }

    @Nested
    @DisplayName("2. Method Signature Tests")
    class MethodSignatureTests {

        @Test
        @DisplayName("testMethodSignatures_matchMyBatisPlusPattern - match MyBatis-Plus pattern")
        public void testMethodSignatures_matchMyBatisPlusPattern() throws NoSuchMethodException {
            // Verify willDoQuery throws SQLException
            Method willDoQuery = SqlGuardInnerInterceptor.class.getMethod(
                "willDoQuery",
                Executor.class, MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, BoundSql.class
            );

            Class<?>[] exceptions = willDoQuery.getExceptionTypes();
            assertEquals(1, exceptions.length, "willDoQuery should declare SQLException");
            assertEquals(SQLException.class, exceptions[0],
                "willDoQuery should throw SQLException");

            // Verify beforeQuery throws SQLException
            Method beforeQuery = SqlGuardInnerInterceptor.class.getMethod(
                "beforeQuery",
                Executor.class, MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, BoundSql.class
            );

            exceptions = beforeQuery.getExceptionTypes();
            assertEquals(1, exceptions.length, "beforeQuery should declare SQLException");
            assertEquals(SQLException.class, exceptions[0],
                "beforeQuery should throw SQLException");
        }
    }

    @Nested
    @DisplayName("3. Default Implementation Tests")
    class DefaultImplementationTests {

        @Test
        @DisplayName("testDefaultMethods_providedForOptionalOverride - default implementations work")
        public void testDefaultMethods_providedForOptionalOverride() throws SQLException {
            // Create minimal implementation
            SqlGuardInnerInterceptor interceptor = new SqlGuardInnerInterceptor() {
                // No methods overridden - using all defaults
            };

            // Test default willDoQuery returns true
            boolean willDoQueryResult = interceptor.willDoQuery(
                null, null, null, null, null, null
            );
            assertTrue(willDoQueryResult, "Default willDoQuery should return true");

            // Test default beforeQuery does nothing (no exception)
            assertDoesNotThrow(() ->
                interceptor.beforeQuery(null, null, null, null, null, null),
                "Default beforeQuery should not throw exception"
            );

            // Test default willDoUpdate returns true
            boolean willDoUpdateResult = interceptor.willDoUpdate(null, null, null);
            assertTrue(willDoUpdateResult, "Default willDoUpdate should return true");

            // Test default beforeUpdate does nothing (no exception)
            assertDoesNotThrow(() ->
                interceptor.beforeUpdate(null, null, null),
                "Default beforeUpdate should not throw exception"
            );

            // Test default getPriority returns 50
            int priority = interceptor.getPriority();
            assertEquals(50, priority, "Default priority should be 50");
        }

        @Test
        @DisplayName("testCustomPriority_canBeOverridden - custom priority works")
        public void testCustomPriority_canBeOverridden() {
            // Create implementation with custom priority
            SqlGuardInnerInterceptor highPriority = new SqlGuardInnerInterceptor() {
                @Override
                public int getPriority() {
                    return 10;  // High priority (check interceptor)
                }
            };

            SqlGuardInnerInterceptor lowPriority = new SqlGuardInnerInterceptor() {
                @Override
                public int getPriority() {
                    return 100;  // Low priority (fallback interceptor)
                }
            };

            assertEquals(10, highPriority.getPriority(),
                "Custom high priority should be 10");
            assertEquals(100, lowPriority.getPriority(),
                "Custom low priority should be 100");
        }
    }

    @Nested
    @DisplayName("4. Javadoc Completeness Tests")
    class JavadocCompletenessTests {

        @Test
        @DisplayName("testJavadoc_complete_withExamples - Javadoc contains examples")
        public void testJavadoc_complete_withExamples() {
            // This test verifies that Javadoc exists by checking the interface is documented
            // In practice, this would use a documentation analyzer tool

            // Verify interface is accessible (documented)
            assertNotNull(SqlGuardInnerInterceptor.class,
                "Interface should be accessible and documented");

            // Verify priority ranges are clearly defined (implicit in default method)
            SqlGuardInnerInterceptor testInterceptor = new SqlGuardInnerInterceptor() {};
            int defaultPriority = testInterceptor.getPriority();

            // Default priority 50 should be in the middle of range 1-99
            assertTrue(defaultPriority >= 1 && defaultPriority <= 199,
                "Default priority should be in documented range");
        }
    }
}
```

---

## 验收标准

### 接口设计验收
- [ ] SqlGuardInnerInterceptor 接口创建（`com.footstone.sqlguard.interceptor.inner` 包）
- [ ] 包含 5 个方法：willDoQuery, beforeQuery, willDoUpdate, beforeUpdate, getPriority
- [ ] 所有方法签名匹配 MyBatis-Plus InnerInterceptor 模式
- [ ] 所有方法提供 default 实现（可选覆盖）

### Javadoc 验收
- [ ] 接口级 Javadoc 完整（描述用途、生命周期、优先级机制）
- [ ] 每个方法 Javadoc 完整（参数说明、返回值说明、异常说明）
- [ ] 包含 Usage Example（完整的代码示例）
- [ ] 包含 Integration Example（与 SqlGuardInterceptor 集成说明）

### 测试验收
- [ ] InnerInterceptorInterfaceDesignTest 全部通过（8 个测试）
- [ ] 接口方法存在性验证（5 个方法）
- [ ] 方法签名验证（throws SQLException）
- [ ] Default 实现验证（willDoQuery/willDoUpdate 返回 true，beforeXxx 无操作）
- [ ] 自定义优先级验证

### 构建验收
- [ ] 编译成功（接口定义正确）
- [ ] 测试通过（8 tests, 0 failures）
- [ ] BUILD SUCCESS

---

## 输出文件

### 新增文件 (2 个)
1. `sql-guard-core/src/main/java/com/footstone/sqlguard/interceptor/inner/SqlGuardInnerInterceptor.java`
2. `sql-guard-core/src/test/java/com/footstone/sqlguard/interceptor/inner/InnerInterceptorInterfaceDesignTest.java`

---

## 依赖与限制

### 依赖
- ✅ Phase 12 Complete: SqlContext 有 statement 字段
- ✅ MyBatis 3.4.x/3.5.x: Executor, MappedStatement, BoundSql 等类
- ✅ JSqlParser: Statement 解析能力

### 限制
- ⚠️ 仅定义接口，不包含实现（Task 13.2/13.3 负责实现）
- ⚠️ 优先级范围建议（1-99, 100-199, 200+），非强制
- ⚠️ BoundSql 修改需谨慎（可能影响参数映射）

---

## 注意事项

### 1. 优先级分配最佳实践

**推荐的优先级范围**:
- **1-99**: Check interceptors（检查拦截器）
  - 10: SqlGuardCheckInnerInterceptor（SQL 安全检查）
  - 20-50: 其他自定义检查拦截器
- **100-199**: Fallback interceptors（降级拦截器）
  - 100: SelectLimitInnerInterceptor（自动 LIMIT 降级）
  - 110-150: 其他降级拦截器
- **200+**: Rewrite interceptors（SQL 重写拦截器）
  - 200-299: 自定义 SQL 重写逻辑

**为什么**: 检查优先于降级，降级优先于重写，确保安全检查先执行。

---

### 2. 生命周期方法调用顺序

**Query 操作**:
```
1. willDoQuery() on Interceptor 1 (priority 10) → true
2. willDoQuery() on Interceptor 2 (priority 20) → true
3. beforeQuery() on Interceptor 1 (priority 10)
4. beforeQuery() on Interceptor 2 (priority 20)
5. Execute query
```

**Update 操作**:
```
1. willDoUpdate() on Interceptor 1 (priority 10) → true
2. willDoUpdate() on Interceptor 2 (priority 20) → true
3. beforeUpdate() on Interceptor 1 (priority 10)
4. beforeUpdate() on Interceptor 2 (priority 20)
5. Execute update
```

**短路机制**: 如果任何 `willDoXxx()` 返回 `false`，后续拦截器和操作都会被跳过。

---

### 3. BoundSql 修改注意事项

**可修改内容**:
- SQL 字符串（通过反射修改 `BoundSql.sql` 字段）
- 附加参数（通过 `BoundSql.setAdditionalParameter()`）

**不可修改**:
- ParameterMappings（参数映射，修改可能导致参数绑定错误）
- ParameterObject（参数对象，只读）

**示例**:
```java
@Override
public void beforeQuery(Executor executor, MappedStatement ms,
                       Object parameter, RowBounds rowBounds,
                       ResultHandler resultHandler, BoundSql boundSql)
        throws SQLException {
    // ✅ 修改 SQL（通过反射）
    String originalSql = boundSql.getSql();
    String modifiedSql = originalSql + " LIMIT 1000";

    // 使用反射修改 BoundSql.sql 字段
    try {
        Field sqlField = BoundSql.class.getDeclaredField("sql");
        sqlField.setAccessible(true);
        sqlField.set(boundSql, modifiedSql);
    } catch (Exception e) {
        throw new SQLException("Failed to modify SQL", e);
    }
}
```

---

## Memory Logging

Upon completion, you **MUST** log work in:
```
.apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_1_InnerInterceptor_Interface_Design.md
```

Follow `.apm/guides/Memory_Log_Guide.md` instructions for proper formatting.

**Required Log Sections**:
- Summary: 1-2 sentences describing interface design outcome
- Details: Interface methods designed, Javadoc completeness, MyBatis-Plus pattern adherence
- Output: List of created files (interface + test), test results
- Issues: Any design challenges or "None"
- Next Steps: Recommendations for Task 13.2 (SqlGuardInterceptor implementation)

---

## 执行时间线

- **预计时间**: 1 工作日
  - 上午：设计接口，编写 Javadoc
  - 下午：编写 TDD 测试，验证接口设计

---

**Created**: 2025-12-22
**Phase**: 13 - InnerInterceptor Architecture
**Task ID**: 13.1
**Sequential**: First task of Phase 13
