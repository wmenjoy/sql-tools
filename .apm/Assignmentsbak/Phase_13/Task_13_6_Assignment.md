---
task_ref: "Task 13.6 - StatementContext ThreadLocal Sharing"
agent_assignment: "Agent_Advanced_Interceptor"
memory_log_path: ".apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_6_StatementContext_Implementation.md"
execution_type: "single-step"
dependency_context: false
ad_hoc_delegation: false
phase: 13
task_id: 13.6
estimated_duration: 1 day
dependencies: []
parallel_with: [Task_13.1]
---

# Task 13.6 Assignment: StatementContext ThreadLocal Sharing

## 任务目标

创建 `StatementContext` 类，提供 ThreadLocal<Map<String, Statement>> 缓存，用于在 InnerInterceptor 链中共享已解析的 Statement，确保线程安全和内存泄漏防护，验证并发请求之间的隔离性。

---

## 背景说明

### 为什么需要 StatementContext？

在 Phase 12 完成后，SQL 解析已经从"每个 Checker 独立解析"优化为"集中解析一次"。但在 Phase 13 的 InnerInterceptor 架构中，我们需要在多个 InnerInterceptor 之间共享同一个 Statement 实例：

**问题场景**:
```java
// SqlGuardInterceptor (主拦截器)
Statement stmt = JSqlParserFacade.parse(sql);  // 解析一次

// SqlGuardCheckInnerInterceptor (优先级 10)
// 如何获取已解析的 Statement？❌ 无法传递

// SelectLimitInnerInterceptor (优先级 100)
// 如何获取已解析的 Statement？❌ 无法传递
```

**解决方案**: 使用 ThreadLocal 缓存
```java
// SqlGuardInterceptor (主拦截器)
Statement stmt = JSqlParserFacade.parse(sql);
StatementContext.cache(sql, stmt);  // ✅ 缓存

// SqlGuardCheckInnerInterceptor (优先级 10)
Statement stmt = StatementContext.get(sql);  // ✅ 获取缓存

// SelectLimitInnerInterceptor (优先级 100)
Statement stmt = StatementContext.get(sql);  // ✅ 复用缓存
```

---

### ThreadLocal 基础

**ThreadLocal 是什么**:
- 每个线程有独立的变量副本
- 线程间互不干扰
- 适合存储"线程上下文"数据

**ThreadLocal 使用模式**:
```java
private static final ThreadLocal<Map<String, Object>> CACHE =
    ThreadLocal.withInitial(HashMap::new);

// 存储
CACHE.get().put(key, value);

// 读取
Object value = CACHE.get().get(key);

// 清理（关键！防止内存泄漏）
CACHE.remove();
```

---

## 实现要求

### 1. StatementContext 类设计

**包路径**: `com.footstone.sqlguard.parser`

**类名**: `StatementContext`

**完整实现**:

```java
package com.footstone.sqlguard.parser;

import net.sf.jsqlparser.statement.Statement;

import java.util.HashMap;
import java.util.Map;

/**
 * ThreadLocal-based cache for sharing parsed SQL Statement instances across
 * the InnerInterceptor chain within a single request.
 *
 * <h2>Purpose</h2>
 * <p>Avoids redundant SQL parsing by caching the Statement instance parsed once
 * by {@code SqlGuardInterceptor} and reusing it in all downstream InnerInterceptors
 * (e.g., {@code SqlGuardCheckInnerInterceptor}, {@code SelectLimitInnerInterceptor}).
 *
 * <h2>Thread Safety</h2>
 * <p>Uses {@link ThreadLocal} to ensure thread isolation. Each thread has its own
 * independent cache, preventing cross-request interference.
 *
 * <h2>Memory Leak Prevention</h2>
 * <p><b>CRITICAL:</b> Must call {@link #clear()} in a {@code finally} block after
 * request processing completes. Failure to clear ThreadLocal can cause memory leaks
 * in thread pool environments (e.g., Tomcat, Jetty).
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // In SqlGuardInterceptor (main interceptor)
 * try {
 *     Statement stmt = JSqlParserFacade.parse(sql);
 *     StatementContext.cache(sql, stmt);  // Cache for downstream interceptors
 *
 *     // Invoke InnerInterceptor chain...
 *     for (InnerInterceptor interceptor : interceptors) {
 *         interceptor.willDoQuery(...);
 *     }
 * } finally {
 *     StatementContext.clear();  // CRITICAL: Cleanup to prevent memory leak
 * }
 *
 * // In downstream InnerInterceptor (e.g., SqlGuardCheckInnerInterceptor)
 * public boolean willDoQuery(...) {
 *     Statement stmt = StatementContext.get(sql);  // Reuse cached Statement
 *     if (stmt == null) {
 *         // Cache miss - parse and cache
 *         stmt = JSqlParserFacade.parse(sql);
 *         StatementContext.cache(sql, stmt);
 *     }
 *     // Use stmt...
 * }
 * }</pre>
 *
 * @since 1.1.0
 */
public final class StatementContext {

    /**
     * ThreadLocal cache storing SQL → Statement mappings for the current thread.
     *
     * <p>Each thread has its own independent HashMap, ensuring thread safety and
     * preventing cross-request interference.
     */
    private static final ThreadLocal<Map<String, Statement>> CACHE =
        ThreadLocal.withInitial(HashMap::new);

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private StatementContext() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Caches a parsed Statement for the given SQL string.
     *
     * <p>Stores the Statement in the current thread's ThreadLocal cache,
     * keyed by the SQL string. Downstream InnerInterceptors can retrieve
     * this Statement using {@link #get(String)} to avoid re-parsing.
     *
     * @param sql       SQL string (used as cache key)
     * @param statement Parsed Statement instance
     * @throws NullPointerException if sql or statement is null
     */
    public static void cache(String sql, Statement statement) {
        if (sql == null) {
            throw new NullPointerException("SQL cannot be null");
        }
        if (statement == null) {
            throw new NullPointerException("Statement cannot be null");
        }
        CACHE.get().put(sql, statement);
    }

    /**
     * Retrieves a cached Statement for the given SQL string.
     *
     * <p>Looks up the Statement in the current thread's ThreadLocal cache.
     * Returns {@code null} if no Statement is cached for this SQL.
     *
     * @param sql SQL string (cache key)
     * @return Cached Statement instance, or {@code null} if not found
     * @throws NullPointerException if sql is null
     */
    public static Statement get(String sql) {
        if (sql == null) {
            throw new NullPointerException("SQL cannot be null");
        }
        return CACHE.get().get(sql);
    }

    /**
     * Clears the ThreadLocal cache for the current thread.
     *
     * <p><b>CRITICAL:</b> Must be called in a {@code finally} block after request
     * processing completes to prevent memory leaks. Failure to clear ThreadLocal
     * in thread pool environments will cause memory to accumulate indefinitely.
     *
     * <p>Removes the entire ThreadLocal value, releasing all cached Statements
     * and allowing garbage collection.
     */
    public static void clear() {
        CACHE.remove();
    }

    /**
     * Returns the number of cached Statements for the current thread.
     *
     * <p>This method is primarily for testing and debugging purposes.
     *
     * @return Number of cached SQL → Statement mappings
     */
    static int size() {
        return CACHE.get().size();
    }
}
```

---

### 2. TDD 测试用例设计

**测试类**: `sql-guard-core/src/test/java/com/footstone/sqlguard/parser/StatementContextTest.java`

**测试用例 (10 个)**:

```java
package com.footstone.sqlguard.parser;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for StatementContext ThreadLocal cache.
 */
@DisplayName("StatementContext Tests")
public class StatementContextTest {

    @AfterEach
    void cleanup() {
        // Always clear ThreadLocal after each test
        StatementContext.clear();
    }

    @Nested
    @DisplayName("1. Basic Cache Operations")
    class BasicCacheOperationsTests {

        @Test
        @DisplayName("testCache_storesStatement_keyedBySql - cache stores Statement")
        public void testCache_storesStatement_keyedBySql() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            // Act
            StatementContext.cache(sql, stmt);

            // Assert
            assertEquals(1, StatementContext.size(), "Cache should contain 1 entry");
        }

        @Test
        @DisplayName("testGet_retrievesCachedStatement - get retrieves cached Statement")
        public void testGet_retrievesCachedStatement() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            // Act
            Statement retrieved = StatementContext.get(sql);

            // Assert
            assertNotNull(retrieved, "Retrieved Statement should not be null");
            assertSame(stmt, retrieved, "Should retrieve the same Statement instance");
        }

        @Test
        @DisplayName("testGet_returnsNull_ifNotCached - get returns null for uncached SQL")
        public void testGet_returnsNull_ifNotCached() {
            // Arrange
            String sql = "SELECT * FROM products";

            // Act
            Statement retrieved = StatementContext.get(sql);

            // Assert
            assertNull(retrieved, "Should return null for uncached SQL");
        }

        @Test
        @DisplayName("testClear_removesThreadLocalValue - clear removes all cached Statements")
        public void testClear_removesThreadLocalValue() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);
            assertEquals(1, StatementContext.size());

            // Act
            StatementContext.clear();

            // Assert
            assertEquals(0, StatementContext.size(), "Cache should be empty after clear");
            assertNull(StatementContext.get(sql), "Should return null after clear");
        }
    }

    @Nested
    @DisplayName("2. Thread Isolation Tests")
    class ThreadIsolationTests {

        @Test
        @DisplayName("testThreadLocalIsolation_differentThreads_independentCaches - threads have independent caches")
        public void testThreadLocalIsolation_differentThreads_independentCaches() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt1 = CCJSqlParserUtil.parse(sql);
            Statement stmt2 = CCJSqlParserUtil.parse(sql);

            CountDownLatch latch = new CountDownLatch(2);
            AtomicReference<Statement> thread1Result = new AtomicReference<>();
            AtomicReference<Statement> thread2Result = new AtomicReference<>();

            // Act - Thread 1 caches stmt1
            Thread t1 = new Thread(() -> {
                try {
                    StatementContext.cache(sql, stmt1);
                    thread1Result.set(StatementContext.get(sql));
                } finally {
                    StatementContext.clear();
                    latch.countDown();
                }
            });

            // Act - Thread 2 caches stmt2
            Thread t2 = new Thread(() -> {
                try {
                    StatementContext.cache(sql, stmt2);
                    thread2Result.set(StatementContext.get(sql));
                } finally {
                    StatementContext.clear();
                    latch.countDown();
                }
            });

            t1.start();
            t2.start();
            latch.await();

            // Assert - Each thread sees its own cached Statement
            assertSame(stmt1, thread1Result.get(), "Thread 1 should see stmt1");
            assertSame(stmt2, thread2Result.get(), "Thread 2 should see stmt2");
        }

        @Test
        @DisplayName("testConcurrentAccess_threadSafe - concurrent access is thread-safe")
        public void testConcurrentAccess_threadSafe() throws Exception {
            // Arrange
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);

            // Act - Multiple threads concurrently cache different Statements
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                new Thread(() -> {
                    try {
                        String sql = "SELECT * FROM table" + threadId;
                        Statement stmt = CCJSqlParserUtil.parse(sql);
                        StatementContext.cache(sql, stmt);

                        // Verify this thread sees its own cache
                        Statement retrieved = StatementContext.get(sql);
                        assertSame(stmt, retrieved, "Thread should see its own Statement");
                    } catch (Exception e) {
                        fail("Thread " + threadId + " threw exception: " + e);
                    } finally {
                        StatementContext.clear();
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            // Assert - Main thread cache is empty (independent from worker threads)
            assertEquals(0, StatementContext.size(), "Main thread cache should be empty");
        }
    }

    @Nested
    @DisplayName("3. Memory Leak Prevention Tests")
    class MemoryLeakPreventionTests {

        @Test
        @DisplayName("testMemoryLeakPrevention_clear_releasesMemory - clear releases memory")
        public void testMemoryLeakPrevention_clear_releasesMemory() throws Exception {
            // Arrange - Cache many Statements
            for (int i = 0; i < 1000; i++) {
                String sql = "SELECT * FROM table" + i;
                Statement stmt = CCJSqlParserUtil.parse(sql);
                StatementContext.cache(sql, stmt);
            }

            assertEquals(1000, StatementContext.size());

            // Act - Clear cache
            StatementContext.clear();

            // Assert - All Statements released
            assertEquals(0, StatementContext.size(), "All cached Statements should be released");

            // Verify cache is truly empty (get returns null)
            assertNull(StatementContext.get("SELECT * FROM table0"));
        }
    }

    @Nested
    @DisplayName("4. Multiple SQL Tests")
    class MultipleSqlTests {

        @Test
        @DisplayName("testMultipleSql_cacheIndependently - multiple SQLs cache independently")
        public void testMultipleSql_cacheIndependently() throws Exception {
            // Arrange
            String sql1 = "SELECT * FROM users";
            String sql2 = "SELECT * FROM products";
            String sql3 = "SELECT * FROM orders";

            Statement stmt1 = CCJSqlParserUtil.parse(sql1);
            Statement stmt2 = CCJSqlParserUtil.parse(sql2);
            Statement stmt3 = CCJSqlParserUtil.parse(sql3);

            // Act
            StatementContext.cache(sql1, stmt1);
            StatementContext.cache(sql2, stmt2);
            StatementContext.cache(sql3, stmt3);

            // Assert
            assertEquals(3, StatementContext.size(), "Should cache 3 different SQLs");
            assertSame(stmt1, StatementContext.get(sql1));
            assertSame(stmt2, StatementContext.get(sql2));
            assertSame(stmt3, StatementContext.get(sql3));
        }
    }

    @Nested
    @DisplayName("5. Usage Pattern Tests")
    class UsagePatternTests {

        @Test
        @DisplayName("testCacheMiss_parseAndCache_pattern - cache miss pattern works")
        public void testCacheMiss_parseAndCache_pattern() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";

            // Act - Simulate InnerInterceptor cache-miss pattern
            Statement stmt = StatementContext.get(sql);
            if (stmt == null) {
                // Cache miss - parse and cache
                stmt = CCJSqlParserUtil.parse(sql);
                StatementContext.cache(sql, stmt);
            }

            // Assert - Subsequent calls hit cache
            Statement cached = StatementContext.get(sql);
            assertSame(stmt, cached, "Subsequent get should return cached Statement");
        }

        @Test
        @DisplayName("testUsagePattern_interceptorChain_works - interceptor chain pattern works")
        public void testUsagePattern_interceptorChain_works() throws Exception {
            // Simulate SqlGuardInterceptor (main interceptor)
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            // Simulate SqlGuardCheckInnerInterceptor (priority 10)
            Statement stmt1 = StatementContext.get(sql);
            assertNotNull(stmt1, "CheckInterceptor should get cached Statement");
            assertSame(stmt, stmt1, "Should be same instance");

            // Simulate SelectLimitInnerInterceptor (priority 100)
            Statement stmt2 = StatementContext.get(sql);
            assertNotNull(stmt2, "LimitInterceptor should get cached Statement");
            assertSame(stmt, stmt2, "Should be same instance");

            // Cleanup (main interceptor's finally block)
            StatementContext.clear();

            // Verify cleaned up
            assertEquals(0, StatementContext.size());
        }
    }

    @Nested
    @DisplayName("6. Null Safety Tests")
    class NullSafetyTests {

        @Test
        @DisplayName("testCache_nullSql_throwsException - cache throws on null SQL")
        public void testCache_nullSql_throwsException() throws Exception {
            // Arrange
            Statement stmt = CCJSqlParserUtil.parse("SELECT 1");

            // Act & Assert
            assertThrows(NullPointerException.class,
                () -> StatementContext.cache(null, stmt),
                "Should throw NPE for null SQL");
        }

        @Test
        @DisplayName("testCache_nullStatement_throwsException - cache throws on null Statement")
        public void testCache_nullStatement_throwsException() {
            // Act & Assert
            assertThrows(NullPointerException.class,
                () -> StatementContext.cache("SELECT 1", null),
                "Should throw NPE for null Statement");
        }

        @Test
        @DisplayName("testGet_nullSql_throwsException - get throws on null SQL")
        public void testGet_nullSql_throwsException() {
            // Act & Assert
            assertThrows(NullPointerException.class,
                () -> StatementContext.get(null),
                "Should throw NPE for null SQL");
        }
    }
}
```

---

## 验收标准

### 功能验收
- [ ] StatementContext 类创建（`com.footstone.sqlguard.parser` 包）
- [ ] ThreadLocal<Map<String, Statement>> 缓存实现
- [ ] `cache(String sql, Statement statement)` 方法实现
- [ ] `get(String sql)` 方法实现
- [ ] `clear()` 方法实现
- [ ] `size()` 测试辅助方法实现
- [ ] Null 安全检查（抛出 NullPointerException）

### 测试验收
- [ ] StatementContextTest 全部通过（13 个测试）
- [ ] ThreadLocal 隔离验证通过（不同线程独立缓存）
- [ ] 并发访问线程安全验证通过
- [ ] 内存泄漏防护验证通过（clear 后缓存为空）
- [ ] 多 SQL 缓存验证通过

### 代码质量验收
- [ ] Javadoc 完整（类级、方法级）
- [ ] 包含使用示例
- [ ] 包含内存泄漏警告
- [ ] 私有构造器（工具类不可实例化）

### 构建验收
- [ ] 编译成功
- [ ] 测试通过（13 tests, 0 failures）
- [ ] BUILD SUCCESS

---

## 输出文件

### 新增文件 (2 个)
1. `sql-guard-core/src/main/java/com/footstone/sqlguard/parser/StatementContext.java`
2. `sql-guard-core/src/test/java/com/footstone/sqlguard/parser/StatementContextTest.java`

---

## 依赖与限制

### 依赖
- ✅ 无外部依赖（纯工具类）
- ✅ JSqlParser Statement 类（已有）

### 限制
- ⚠️ 必须在 finally 块调用 clear()（否则内存泄漏）
- ⚠️ 仅用于单次请求内缓存（不是全局缓存）
- ⚠️ SQL 字符串作为 key（大小写敏感）

---

## 注意事项

### 1. 内存泄漏风险（严重）

**问题**: ThreadLocal 在线程池环境下容易导致内存泄漏

**原因**:
```java
// Tomcat/Jetty 使用线程池，线程会复用
// 如果不清理 ThreadLocal，上一次请求的数据会残留
Thread-1: Request 1 → cache Statement → 处理完成 ❌ 未清理
Thread-1: Request 2 → cache Statement → ThreadLocal 持有 2 个 Statement
Thread-1: Request 3 → cache Statement → ThreadLocal 持有 3 个 Statement
...
Thread-1: Request 1000 → 💥 OutOfMemoryError
```

**解决方案**: 严格 finally 清理
```java
// ✅ 正确用法
try {
    StatementContext.cache(sql, stmt);
    // 处理逻辑...
} finally {
    StatementContext.clear();  // 必须清理！
}
```

---

### 2. ThreadLocal 使用最佳实践

**Best Practice 1**: 使用 `ThreadLocal.withInitial()`
```java
// ✅ 推荐：自动初始化
private static final ThreadLocal<Map<String, Statement>> CACHE =
    ThreadLocal.withInitial(HashMap::new);

// ❌ 不推荐：需要手动初始化
private static final ThreadLocal<Map<String, Statement>> CACHE =
    new ThreadLocal<>();
// 使用前需要 CACHE.set(new HashMap<>())
```

**Best Practice 2**: 使用 `remove()` 而不是 `set(null)`
```java
// ✅ 推荐：完全移除 ThreadLocal 值
CACHE.remove();

// ❌ 不推荐：只是设置为 null，ThreadLocal Entry 仍存在
CACHE.set(null);
```

**Best Practice 3**: final 修饰符
```java
// ✅ 推荐：防止重新赋值
private static final ThreadLocal<...> CACHE = ...;

// ❌ 不推荐：可能被重新赋值
private static ThreadLocal<...> CACHE = ...;
```

---

### 3. 线程隔离验证

**为什么需要测试**:
确保不同线程的缓存互不干扰

**测试模式**:
```java
Thread t1 = new Thread(() -> {
    StatementContext.cache(sql, stmt1);
    // t1 只能看到 stmt1
});

Thread t2 = new Thread(() -> {
    StatementContext.cache(sql, stmt2);
    // t2 只能看到 stmt2，看不到 stmt1
});
```

---

### 4. SQL 字符串作为 Key 的考虑

**大小写敏感**:
```java
StatementContext.cache("SELECT * FROM users", stmt1);
StatementContext.get("select * from users");  // ❌ 返回 null（大小写不同）
```

**建议**: 在缓存前统一 SQL 大小写（可选）
```java
// 可选优化：统一转大写
String normalizedSql = sql.toUpperCase();
StatementContext.cache(normalizedSql, stmt);
```

**注意**: 当前实现不做大小写标准化，保持 SQL 原样作为 key

---

### 5. 测试中的 cleanup

**为什么每个测试后都要清理**:
JUnit 在同一线程运行测试，ThreadLocal 会在测试间共享

**正确做法**:
```java
@AfterEach
void cleanup() {
    StatementContext.clear();  // 每个测试后清理
}
```

---

## Memory Logging

Upon completion, you **MUST** log work in:
```
.apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_6_StatementContext_Implementation.md
```

Follow `.apm/guides/Memory_Log_Guide.md` instructions for proper formatting.

**Required Log Sections**:
- Summary: 1-2 sentences describing StatementContext implementation outcome
- Details: ThreadLocal cache implementation, thread safety verification, memory leak prevention
- Output: List of created files (StatementContext + test), test results (13 tests)
- Issues: Any ThreadLocal issues encountered or "None"
- Next Steps: Ready for Task 13.2/13.3/13.4/13.5 (all depend on StatementContext)

---

## 执行时间线

- **预计时间**: 1 工作日
  - 上午：实现 StatementContext 类（cache/get/clear 方法）
  - 下午：编写 TDD 测试（线程隔离、内存泄漏测试）

---

**Created**: 2025-12-22
**Phase**: 13 - InnerInterceptor Architecture
**Task ID**: 13.6
**Parallel**: Can run in parallel with Task 13.1 (no dependency)
