---
task_ref: "Task 12.11 - Performance Verification"
agent_assignment: "Agent_Testing_Validation"
memory_log_path: ".apm/Memory/Phase_12_Core_Architecture_Unification/Task_12_11_Performance_Verification.md"
execution_type: "multi-step"
dependency_context: false
ad_hoc_delegation: false
phase: 12
task_id: 12.11
estimated_duration: 1-2 days
dependencies: [Task_12.1, Task_12.2, Task_12.3, Task_12.4, Task_12.5, Task_12.6, Task_12.7, Task_12.8, Task_12.9, Task_12.10]
parallel_with: []
---

# Task 12.11 Assignment: Performance Verification

## 任务目标

验证 Phase 12 架构重构带来的性能提升：SQL 解析次数从 N 次（每个 Checker 独立解析）减少到 1 次（interceptor 解析一次，Checkers 复用），测量性能改进（吞吐量、延迟、内存占用），生成性能对比报告。

---

## 背景说明

### 架构重构前后对比

**旧架构 (Task 12.1 之前)**:
```java
public class OldChecker extends AbstractRuleChecker {
  @Override
  public ValidationResult check(SqlContext context) {
    // ❌ 每个 Checker 独立解析 SQL
    Statement statement = JSqlParserFacade.parse(context.getSql());

    if (statement instanceof Select) {
      Select select = (Select) statement;
      // 检查逻辑...
    }
    return ValidationResult.pass();
  }
}
```

**新架构 (Task 12.10 完成后)**:
```java
public class NewChecker extends AbstractRuleChecker {
  @Override
  protected void visitSelect(Select select, SqlContext context, ValidationResult result) {
    // ✅ Statement 已由 interceptor 解析并缓存在 SqlContext
    // Checker 直接使用 context.getStatement()，无需重复解析
    // 检查逻辑...
  }
}
```

### 性能提升预期

**解析次数减少**:
- **旧架构**: N 次解析（N = 启用的 Checker 数量，例如 10 个 Checker = 10 次解析）
- **新架构**: 1 次解析（interceptor 解析一次，所有 Checker 复用）
- **预期减少**: 90% (10 个 Checker 场景)

**总体性能提升**:
- **时间**: 30-50% 减少（解析是主要开销）
- **内存**: 减少（单一 Statement 实例复用）
- **吞吐量**: 提升 30-50%
- **延迟**: P99 延迟降低

---

## 实现要求

### 1. 解析次数验证

**测试类**: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/ParsingCountVerificationTest.java`

**测试策略**: 使用 Mockito.spy 监控 `JSqlParserFacade.parse()` 调用次数

**测试用例 (5 个)**:

```java
package com.footstone.sqlguard.validator;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.core.model.*;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TDD tests verifying parsing count reduction from N times to 1 time.
 */
@DisplayName("Parsing Count Verification Tests")
public class ParsingCountVerificationTest {

    @Nested
    @DisplayName("1. Parsing Count Tests")
    class ParsingCountTests {

        @Test
        @DisplayName("testNewArchitecture_parsesOnce_cachesInSqlContext - parse once per validation")
        public void testNewArchitecture_parsesOnce_cachesInSqlContext() {
            // Arrange
            String sql = "SELECT * FROM users WHERE age > 18";
            SqlGuardConfig config = SqlGuardConfig.builder()
                .noWhereClause(true)
                .blacklistFields(List.of("password"))
                .whitelistFields(List.of("id", "name"))
                .build();

            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(config);

            // Track parse() call count
            int[] parseCount = {0};

            try (MockedStatic<JSqlParserFacade> mockedParser = mockStatic(JSqlParserFacade.class)) {
                // Mock parse to count invocations
                mockedParser.when(() -> JSqlParserFacade.parse(anyString()))
                    .thenAnswer(invocation -> {
                        parseCount[0]++;
                        return JSqlParserFacade.parse((String) invocation.getArgument(0));
                    });

                // Act
                SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .type(SqlCommandType.SELECT)
                    .mapperId("com.example.UserMapper.selectByAge")
                    .build();

                ValidationResult result = validator.validate(context);

                // Assert - Should parse exactly once
                assertEquals(1, parseCount[0],
                    "New architecture should parse SQL exactly once");
            }
        }

        @Test
        @DisplayName("testParsingReduction_90percent_with10Checkers - 90% reduction with 10 checkers")
        public void testParsingReduction_90percent_with10Checkers() {
            // Arrange - Enable 10 checkers
            SqlGuardConfig config = SqlGuardConfig.builder()
                .noWhereClause(true)
                .blacklistFields(List.of("password"))
                .whitelistFields(List.of("id", "name"))
                .noPagination(true)
                .logicalPagination(true)
                .noConditionPagination(true)
                .deepPagination(true)
                .missingOrderBy(true)
                .largePageSize(true)
                .dummyCondition(true)
                .build();

            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(config);

            // Count enabled checkers
            int enabledCheckerCount = 10; // 10 checkers enabled

            // Act - Validate SQL
            String sql = "SELECT id, name FROM users LIMIT 10";
            SqlContext context = SqlContext.builder()
                .sql(sql)
                .type(SqlCommandType.SELECT)
                .mapperId("com.example.UserMapper.selectUsers")
                .build();

            int[] parseCount = {0};
            try (MockedStatic<JSqlParserFacade> mockedParser = mockStatic(JSqlParserFacade.class)) {
                mockedParser.when(() -> JSqlParserFacade.parse(anyString()))
                    .thenAnswer(invocation -> {
                        parseCount[0]++;
                        return JSqlParserFacade.parse((String) invocation.getArgument(0));
                    });

                validator.validate(context);
            }

            // Assert
            int expectedOldParseCount = enabledCheckerCount; // Old: N parses
            int actualNewParseCount = parseCount[0];         // New: 1 parse

            assertEquals(1, actualNewParseCount, "New architecture parses once");

            double reductionPercent = ((double)(expectedOldParseCount - actualNewParseCount) / expectedOldParseCount) * 100;
            assertTrue(reductionPercent >= 90,
                String.format("Expected 90%% reduction, got %.1f%% (%d→%d)",
                    reductionPercent, expectedOldParseCount, actualNewParseCount));
        }

        @Test
        @DisplayName("testSpyVerification_parseInvocationCount_accurate - spy accurately counts invocations")
        public void testSpyVerification_parseInvocationCount_accurate() {
            // Arrange
            String sql = "SELECT * FROM users";

            // Act - Direct parse invocations
            try (MockedStatic<JSqlParserFacade> mockedParser = mockStatic(JSqlParserFacade.class)) {
                int[] count = {0};
                mockedParser.when(() -> JSqlParserFacade.parse(anyString()))
                    .thenAnswer(invocation -> {
                        count[0]++;
                        return JSqlParserFacade.parse((String) invocation.getArgument(0));
                    });

                JSqlParserFacade.parse(sql);
                JSqlParserFacade.parse(sql);
                JSqlParserFacade.parse(sql);

                // Assert
                assertEquals(3, count[0], "Spy should accurately count 3 invocations");
            }
        }

        @Test
        @DisplayName("testCacheHit_Statement_reusedByCheckers - Statement cached and reused")
        public void testCacheHit_Statement_reusedByCheckers() {
            // Arrange
            String sql = "SELECT * FROM users WHERE age > 18";
            Statement parsedStatement = JSqlParserFacade.parse(sql);

            SqlContext context = SqlContext.builder()
                .sql(sql)
                .statement(parsedStatement)  // ✅ Statement already cached
                .type(SqlCommandType.SELECT)
                .mapperId("com.example.UserMapper.selectByAge")
                .build();

            // Assert - SqlContext should return the cached statement
            assertNotNull(context.getStatement());
            assertSame(parsedStatement, context.getStatement(),
                "Statement should be the same instance (cached and reused)");
        }

        @Test
        @DisplayName("testMultipleValidations_parsesEachUniqueSql_once - unique SQLs parsed once each")
        public void testMultipleValidations_parsesEachUniqueSql_once() {
            // Arrange
            SqlGuardConfig config = SqlGuardConfig.builder()
                .noWhereClause(true)
                .build();
            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(config);

            String sql1 = "SELECT * FROM users";
            String sql2 = "SELECT * FROM products";
            String sql3 = "SELECT * FROM users"; // Duplicate of sql1

            int[] parseCount = {0};

            try (MockedStatic<JSqlParserFacade> mockedParser = mockStatic(JSqlParserFacade.class)) {
                mockedParser.when(() -> JSqlParserFacade.parse(anyString()))
                    .thenAnswer(invocation -> {
                        parseCount[0]++;
                        return JSqlParserFacade.parse((String) invocation.getArgument(0));
                    });

                // Act - Validate 3 SQLs (2 unique)
                validator.validate(buildContext(sql1));
                validator.validate(buildContext(sql2));
                validator.validate(buildContext(sql3));

                // Assert - Should parse 3 times (each validation parses its SQL once)
                // Note: Caching is per-validation, not global
                assertEquals(3, parseCount[0], "Each validation should parse once");
            }
        }
    }

    private SqlContext buildContext(String sql) {
        return SqlContext.builder()
            .sql(sql)
            .type(SqlCommandType.SELECT)
            .mapperId("com.example.Mapper.method")
            .build();
    }
}
```

---

### 2. 性能基准测试

**测试类**: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/PerformanceBenchmarkTest.java`

**基准测试策略**:
- 准备 100 条不同复杂度的 SQL（简单、中等、复杂）
- 测量吞吐量（SQLs/second）、延迟（P50/P99）、内存占用
- 对比新旧架构性能

**测试用例 (5 个)**:

```java
package com.footstone.sqlguard.validator;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.core.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance benchmark tests comparing new vs old architecture.
 */
@DisplayName("Performance Benchmark Tests")
public class PerformanceBenchmarkTest {

    private static final int ITERATIONS = 1000;
    private static final int WARMUP_ITERATIONS = 100;

    @Nested
    @DisplayName("2. Throughput Tests")
    class ThroughputTests {

        @Test
        @DisplayName("benchmarkNewArchitecture_throughput_improved - throughput improvement 30-50%")
        public void benchmarkNewArchitecture_throughput_improved() {
            // Arrange
            SqlGuardConfig config = SqlGuardConfig.builder()
                .noWhereClause(true)
                .blacklistFields(List.of("password"))
                .whitelistFields(List.of("id", "name"))
                .noPagination(true)
                .build();

            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(config);
            List<String> testSqls = generateTestSqlSet(100);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                validator.validate(buildContext(testSqls.get(i % testSqls.size())));
            }

            // Act - Measure throughput
            long startTime = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                validator.validate(buildContext(testSqls.get(i % testSqls.size())));
            }
            long endTime = System.nanoTime();

            // Calculate throughput
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughput = ITERATIONS / durationSeconds;

            // Assert - Throughput should be reasonable (baseline for comparison)
            assertTrue(throughput > 0, "Throughput should be positive");

            System.out.printf("New Architecture Throughput: %.2f SQLs/second%n", throughput);

            // Expected: > 1000 SQLs/second (depends on hardware)
            assertTrue(throughput > 500,
                String.format("Expected throughput > 500 SQLs/sec, got %.2f", throughput));
        }

        @Test
        @DisplayName("benchmarkThroughput_simpleSql_veryFast - simple SQL validates very fast")
        public void benchmarkThroughput_simpleSql_veryFast() {
            // Arrange
            SqlGuardConfig config = SqlGuardConfig.builder()
                .noWhereClause(true)
                .build();
            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(config);
            String simpleSql = "SELECT id, name FROM users WHERE id = 1";

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                validator.validate(buildContext(simpleSql));
            }

            // Act
            long startTime = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                validator.validate(buildContext(simpleSql));
            }
            long endTime = System.nanoTime();

            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughput = ITERATIONS / durationSeconds;

            // Assert
            System.out.printf("Simple SQL Throughput: %.2f SQLs/second%n", throughput);
            assertTrue(throughput > 1000,
                String.format("Simple SQL should be very fast, got %.2f SQLs/sec", throughput));
        }
    }

    @Nested
    @DisplayName("3. Latency Tests")
    class LatencyTests {

        @Test
        @DisplayName("benchmarkNewArchitecture_latencyP99_improved - P99 latency improved")
        public void benchmarkNewArchitecture_latencyP99_improved() {
            // Arrange
            SqlGuardConfig config = SqlGuardConfig.builder()
                .noWhereClause(true)
                .blacklistFields(List.of("password"))
                .whitelistFields(List.of("id", "name"))
                .build();

            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(config);
            List<String> testSqls = generateTestSqlSet(100);
            List<Long> latencies = new ArrayList<>();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                validator.validate(buildContext(testSqls.get(i % testSqls.size())));
            }

            // Act - Measure latency per validation
            for (int i = 0; i < ITERATIONS; i++) {
                long startTime = System.nanoTime();
                validator.validate(buildContext(testSqls.get(i % testSqls.size())));
                long endTime = System.nanoTime();
                latencies.add(endTime - startTime);
            }

            // Calculate percentiles
            Collections.sort(latencies);
            long p50 = latencies.get((int) (ITERATIONS * 0.50));
            long p99 = latencies.get((int) (ITERATIONS * 0.99));

            // Assert
            System.out.printf("Latency P50: %.3f ms%n", p50 / 1_000_000.0);
            System.out.printf("Latency P99: %.3f ms%n", p99 / 1_000_000.0);

            // Expected: P99 < 10ms (reasonable performance)
            assertTrue(p99 < 10_000_000,
                String.format("P99 latency should be < 10ms, got %.3f ms", p99 / 1_000_000.0));
        }

        @Test
        @DisplayName("benchmarkLatency_complexSql_acceptable - complex SQL latency acceptable")
        public void benchmarkLatency_complexSql_acceptable() {
            // Arrange
            SqlGuardConfig config = SqlGuardConfig.builder()
                .noWhereClause(true)
                .blacklistFields(List.of("password"))
                .build();
            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(config);

            String complexSql = "SELECT u.id, u.name, o.order_id, p.product_name " +
                "FROM users u " +
                "JOIN orders o ON u.id = o.user_id " +
                "JOIN products p ON o.product_id = p.id " +
                "WHERE u.age > 18 AND o.status = 'COMPLETED' " +
                "ORDER BY o.created_at DESC LIMIT 100";

            List<Long> latencies = new ArrayList<>();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                validator.validate(buildContext(complexSql));
            }

            // Act
            for (int i = 0; i < ITERATIONS; i++) {
                long startTime = System.nanoTime();
                validator.validate(buildContext(complexSql));
                long endTime = System.nanoTime();
                latencies.add(endTime - startTime);
            }

            Collections.sort(latencies);
            long p99 = latencies.get((int) (ITERATIONS * 0.99));

            // Assert
            System.out.printf("Complex SQL P99 Latency: %.3f ms%n", p99 / 1_000_000.0);
            assertTrue(p99 < 20_000_000,
                String.format("Complex SQL P99 should be < 20ms, got %.3f ms", p99 / 1_000_000.0));
        }
    }

    @Nested
    @DisplayName("4. Memory Tests")
    class MemoryTests {

        @Test
        @DisplayName("benchmarkMemoryUsage_reduction_confirmed - memory usage reduced")
        public void benchmarkMemoryUsage_reduction_confirmed() {
            // Arrange
            SqlGuardConfig config = SqlGuardConfig.builder()
                .noWhereClause(true)
                .blacklistFields(List.of("password"))
                .build();
            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(config);
            List<String> testSqls = generateTestSqlSet(100);

            // Force GC before measurement
            System.gc();
            Runtime runtime = Runtime.getRuntime();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

            // Act - Validate many SQLs
            for (int i = 0; i < 10000; i++) {
                validator.validate(buildContext(testSqls.get(i % testSqls.size())));
            }

            // Force GC after validation
            System.gc();
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

            // Calculate memory increase
            long memoryIncrease = memoryAfter - memoryBefore;

            // Assert - Memory increase should be reasonable
            System.out.printf("Memory Before: %.2f MB%n", memoryBefore / 1_000_000.0);
            System.out.printf("Memory After: %.2f MB%n", memoryAfter / 1_000_000.0);
            System.out.printf("Memory Increase: %.2f MB%n", memoryIncrease / 1_000_000.0);

            // Expected: Memory increase < 100 MB (Statement instances are reused)
            assertTrue(memoryIncrease < 100_000_000,
                String.format("Memory increase should be < 100 MB, got %.2f MB",
                    memoryIncrease / 1_000_000.0));
        }
    }

    // Helper methods
    private List<String> generateTestSqlSet(int count) {
        List<String> sqls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i % 3 == 0) {
                // Simple SQL
                sqls.add("SELECT id, name FROM users WHERE id = " + i);
            } else if (i % 3 == 1) {
                // Medium SQL
                sqls.add("SELECT u.id, u.name, o.order_id FROM users u " +
                    "JOIN orders o ON u.id = o.user_id WHERE u.age > " + (i % 100));
            } else {
                // Complex SQL
                sqls.add("SELECT u.id, u.name, o.order_id, p.product_name " +
                    "FROM users u " +
                    "JOIN orders o ON u.id = o.user_id " +
                    "JOIN products p ON o.product_id = p.id " +
                    "WHERE u.age > " + (i % 100) + " AND o.status = 'COMPLETED' " +
                    "ORDER BY o.created_at DESC LIMIT " + ((i % 10) + 1) * 10);
            }
        }
        return sqls;
    }

    private SqlContext buildContext(String sql) {
        return SqlContext.builder()
            .sql(sql)
            .type(SqlCommandType.SELECT)
            .mapperId("com.example.TestMapper.method")
            .build();
    }
}
```

---

### 3. 性能对比报告

**报告路径**: `.apm/Assignments/Phase_12/Task_12_11_Performance_Report.md`

**报告内容结构**:

```markdown
# Task 12.11 Performance Verification Report

## 概述

本报告记录 Phase 12 架构重构的性能提升验证结果。

---

## 1. 解析次数验证

### 测试结果

| 场景 | 启用 Checker 数 | 旧架构解析次数 | 新架构解析次数 | 减少百分比 |
|------|----------------|---------------|---------------|-----------|
| 场景 1 | 1 个 Checker | 1 次 | 1 次 | 0% |
| 场景 2 | 5 个 Checker | 5 次 | 1 次 | 80% |
| 场景 3 | 10 个 Checker | 10 次 | 1 次 | 90% |

### 结论

✅ **解析次数从 N 次减少到 1 次**，符合预期。

---

## 2. 性能基准测试

### 吞吐量对比

| SQL 类型 | 新架构吞吐量 | 基线吞吐量 | 提升 |
|---------|-------------|-----------|------|
| 简单 SQL | XXX SQLs/sec | (参考) | - |
| 中等 SQL | XXX SQLs/sec | (参考) | - |
| 复杂 SQL | XXX SQLs/sec | (参考) | - |

### 延迟对比

| 指标 | 新架构 | 基线 | 改进 |
|------|-------|------|------|
| P50 延迟 | X.XX ms | (参考) | - |
| P99 延迟 | X.XX ms | (参考) | - |

### 内存占用对比

| 指标 | 新架构 | 基线 | 减少 |
|------|-------|------|------|
| 10000 次验证内存增长 | XX MB | (参考) | - |

---

## 3. 总结

### 性能提升确认

- ✅ 解析次数减少 90% (10 个 Checker 场景)
- ✅ 吞吐量提升 XX%
- ✅ P99 延迟降低 XX%
- ✅ 内存占用减少

### 架构重构收益

Phase 12 架构重构成功实现了性能优化目标，将 SQL 解析从每个 Checker 独立解析改为集中解析 + 复用，显著降低了性能开销。

---

**Created**: 2025-12-22
**Phase**: 12 - Core Architecture Unification
**Task ID**: 12.11
```

---

## 验收标准

### 解析次数验证
- [ ] ParsingCountVerificationTest 全部通过（5 个测试）
- [ ] 解析次数从 N 次减少到 1 次（90% 减少，10 个 Checker 场景）
- [ ] Statement 缓存机制工作正常
- [ ] Mockito.spy 验证准确

### 性能基准测试
- [ ] PerformanceBenchmarkTest 全部通过（5 个测试）
- [ ] 吞吐量提升 30-50%（相比旧架构基线）
- [ ] P99 延迟 < 10ms（简单/中等 SQL）
- [ ] 内存占用合理（< 100 MB 增长，10000 次验证）

### 性能报告
- [ ] 性能对比报告完整（`.apm/Assignments/Phase_12/Task_12_11_Performance_Report.md`）
- [ ] 包含解析次数对比表格
- [ ] 包含吞吐量/延迟/内存对比数据
- [ ] 结论清晰，收益量化

### 构建验证
- [ ] 所有测试通过（594 tests + 10 new = 604 tests）
- [ ] 构建成功（BUILD SUCCESS）
- [ ] 无性能回归（新架构不慢于旧架构）

---

## 输出文件

### 新增测试文件 (2 个)
1. `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/ParsingCountVerificationTest.java` (5 tests)
2. `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/PerformanceBenchmarkTest.java` (5 tests)

### 新增文档 (1 个)
- `.apm/Assignments/Phase_12/Task_12_11_Performance_Report.md`

---

## 依赖与限制

### 依赖
- ✅ Task 12.1-12.10: 所有架构迁移已完成
- ✅ SqlContext 有 statement 字段（缓存 Statement）
- ✅ 所有 Checker 已迁移到 visitor 模式

### 限制
- ⚠️ 性能测试结果受硬件影响（CPU、内存）
- ⚠️ 基准测试需要充分预热（避免 JIT 影响）
- ⚠️ 内存测试受 GC 影响（使用 System.gc() 强制回收）

---

## 注意事项

### 1. Mockito.spy 使用

**正确用法**:
```java
try (MockedStatic<JSqlParserFacade> mockedParser = mockStatic(JSqlParserFacade.class)) {
    int[] count = {0};
    mockedParser.when(() -> JSqlParserFacade.parse(anyString()))
        .thenAnswer(invocation -> {
            count[0]++;  // Count invocations
            // Call real method
            return JSqlParserFacade.parse((String) invocation.getArgument(0));
        });

    // Test code...

    assertEquals(1, count[0], "Should parse once");
}
```

**错误用法**:
```java
// ❌ Don't verify() on static mock - it doesn't work
verify(JSqlParserFacade, times(1)).parse(anyString()); // WRONG
```

### 2. 性能测试最佳实践

**预热 (Warmup)**:
```java
// JIT 需要预热才能达到最佳性能
for (int i = 0; i < WARMUP_ITERATIONS; i++) {
    validator.validate(context);
}
```

**GC 控制**:
```java
// 内存测试前强制 GC
System.gc();
long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
```

**多次测量**:
```java
// 测量多次取平均值，避免单次抖动
List<Long> latencies = new ArrayList<>();
for (int i = 0; i < ITERATIONS; i++) {
    long start = System.nanoTime();
    // test code...
    long end = System.nanoTime();
    latencies.add(end - start);
}
```

### 3. 基准测试 SQL 集合

**多样性很重要**:
- 简单 SQL: `SELECT id FROM users WHERE id = 1`
- 中等 SQL: `SELECT u.id, o.order_id FROM users u JOIN orders o ON u.id = o.user_id`
- 复杂 SQL: 多表 JOIN + WHERE + ORDER BY + LIMIT

**为什么**: 不同复杂度的 SQL 解析开销不同，需要全面测试。

---

## Memory Logging

Upon completion, you **MUST** log work in:
```
.apm/Memory/Phase_12_Core_Architecture_Unification/Task_12_11_Performance_Verification.md
```

Follow `.apm/guides/Memory_Log_Guide.md` instructions for proper formatting.

**Required Log Sections**:
- Summary: 1-2 sentences describing performance verification outcome
- Details: Parsing count reduction verified, benchmark results, test approach
- Output: List of new test files, performance report path, key metrics (parsing count, throughput, latency, memory)
- Issues: Any performance issues discovered or "None"
- Next Steps: Recommendations for Phase 13 or "Phase 12 Complete"

---

## 执行时间线

- **预计时间**: 1-2 工作日
  - 第 1 天上午：实现 ParsingCountVerificationTest，验证解析次数减少
  - 第 1 天下午：实现 PerformanceBenchmarkTest，运行基准测试
  - 第 2 天上午：收集性能数据，生成对比报告
  - 第 2 天下午：验证所有测试通过，完成 Memory Log

---

**Created**: 2025-12-22
**Phase**: 12 - Core Architecture Unification
**Task ID**: 12.11
**Sequential**: After Task 12.10, final task of Phase 12
