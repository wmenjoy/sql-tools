---
phase: 12
task_id: 12.9
task_name: Remaining Checker Migration (Batch 2)
agent: Agent_Architecture_Refactoring
estimated_duration: 3 days
dependencies: [Task_12.1, Task_12.2, Task_12.3, Task_12.4]
parallel_with: []
---

# Task 12.9 Assignment: Remaining Checker Migration (Batch 2)

## 任务目标

将剩余 6 个 Checker 从 check() 实现迁移到 visitor pattern，删除对已移除工具方法的依赖（extractWhere, isDummyCondition），更新测试使用 statement 字段，验证所有 476 个测试通过。

---

## 背景说明

### 当前状态

**已完成的迁移** (Task 12.5-12.8):
- ✅ NoWhereClauseChecker
- ✅ BlacklistFieldChecker
- ✅ WhitelistFieldChecker
- ✅ NoPaginationChecker

**待迁移的 6 个 Checker** (Task 12.9):
1. LogicalPaginationChecker
2. DummyConditionChecker
3. NoConditionPaginationChecker
4. DeepPaginationChecker
5. MissingOrderByChecker
6. LargePageSizeChecker

### 编译错误现状

```
[ERROR] Tests run: 476, Failures: 0, Errors: 32, Skipped: 0
```

32 个错误都是由于这 6 个 Checker 尝试覆盖 final 的 check() 方法导致：
```
java.lang.IncompatibleClassChangeError: class X overrides final method
com.footstone.sqlguard.validator.rule.AbstractRuleChecker.check(...)
```

---

## 架构迁移对照

### 旧架构（Task 12.4 之前）
```java
public class SomeChecker extends AbstractRuleChecker {
    @Override
    public void check(SqlContext context, ValidationResult result) {
        if (!isEnabled()) {
            return;
        }

        Statement stmt = context.getParsedSql();  // ❌ 已废弃
        Expression where = extractWhere(stmt);     // ❌ 工具方法已删除
        if (isDummyCondition(where)) {             // ❌ 工具方法已删除
            result.addViolation(...);
        }
    }
}
```

### 新架构（Task 12.9 之后）
```java
public class SomeChecker extends AbstractRuleChecker {
    public SomeChecker(SomeConfig config) {
        super(config);  // ✅ 必须调用 super(config)
    }

    @Override
    protected void visitSelect(Select select, SqlContext context) {
        if (!isEnabled()) {  // ✅ 必须手动检查
            return;
        }

        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        Expression where = plainSelect.getWhere();  // ✅ 直接 API 调用

        if (where == null || isDummyConditionExpression(where)) {  // ✅ 本地方法
            addViolation(RiskLevel.CRITICAL, "...");  // ✅ 辅助方法
        }
    }

    // 本地实现替代工具方法
    private boolean isDummyConditionExpression(Expression where) { ... }
}
```

---

## 实现要求

### 1. LogicalPaginationChecker 迁移

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/LogicalPaginationChecker.java`

**特殊说明**: 此 Checker 不依赖 Statement 类型（仅检查 RowBounds/IPage），**不需要** visitor pattern，只需修改 check() 方法为 non-override。

**迁移步骤**:
1. ✅ 移除 `@Override` 注解（保留 check() 方法）
2. ✅ 添加 `super(config)` 到构造函数
3. ✅ 保持现有逻辑不变

**完整代码**:
```java
package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.PaginationType;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import com.footstone.sqlguard.validator.rule.impl.LogicalPaginationConfig;
import org.apache.ibatis.session.RowBounds;

/**
 * Checker that detects dangerous logical pagination patterns.
 * (Javadoc unchanged)
 */
public class LogicalPaginationChecker extends AbstractRuleChecker {

  private final PaginationPluginDetector detector;
  private final LogicalPaginationConfig config;

  public LogicalPaginationChecker(PaginationPluginDetector detector,
      LogicalPaginationConfig config) {
    super(config);  // ✅ NEW: Pass config to AbstractRuleChecker
    if (detector == null) {
      throw new IllegalArgumentException("PaginationPluginDetector cannot be null");
    }
    if (config == null) {
      throw new IllegalArgumentException("LogicalPaginationConfig cannot be null");
    }
    this.detector = detector;
    this.config = config;
  }

  /**
   * Checks for logical pagination violations.
   *
   * NOTE: No @Override annotation - this is a public method but not overriding
   * AbstractRuleChecker.check() which is now final.
   */
  public void check(SqlContext context, ValidationResult result) {
    // Existing logic unchanged
    if (!isEnabled()) {
      return;
    }

    PaginationType type = detector.detectPaginationType(context);
    if (type != PaginationType.LOGICAL) {
      return;
    }

    Object rowBoundsObj = context.getRowBounds();
    int offset = 0;
    int limit = 0;

    if (rowBoundsObj instanceof RowBounds) {
      RowBounds rowBounds = (RowBounds) rowBoundsObj;
      offset = rowBounds.getOffset();
      limit = rowBounds.getLimit();
    }

    String message = "检测到逻辑分页!将加载全表数据到内存,可能导致OOM";
    String suggestion = "立即配置分页插件:MyBatis-Plus PaginationInnerInterceptor或PageHelper";
    result.addViolation(RiskLevel.CRITICAL, message, suggestion);

    result.getDetails().put("offset", offset);
    result.getDetails().put("limit", limit);
    result.getDetails().put("paginationType", "LOGICAL");
  }

  @Override
  public boolean isEnabled() {
    return config.isEnabled();
  }
}
```

**TDD 测试**: `LogicalPaginationCheckerMigrationTest.java`
```java
package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.PaginationType;
import com.footstone.sqlguard.validator.rule.impl.LogicalPaginationConfig;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("LogicalPaginationChecker Migration Tests")
public class LogicalPaginationCheckerMigrationTest {

    private PaginationPluginDetector detector;
    private LogicalPaginationConfig config;
    private LogicalPaginationChecker checker;

    @BeforeEach
    public void setUp() {
        detector = Mockito.mock(PaginationPluginDetector.class);
        config = new LogicalPaginationConfig();
        config.setEnabled(true);
        checker = new LogicalPaginationChecker(detector, config);
    }

    @Nested
    @DisplayName("1. Logical Pagination Detection Tests")
    class LogicalPaginationDetectionTests {

        @Test
        @DisplayName("check() - LOGICAL pagination - should add CRITICAL violation")
        public void testCheck_logicalPagination_violates() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE id > 0";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.logical")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .rowBounds(new RowBounds(0, 20))  // RowBounds without plugin
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.LOGICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("逻辑分页"));
            assertEquals(0, result.getDetails().get("offset"));
            assertEquals(20, result.getDetails().get("limit"));
        }

        @Test
        @DisplayName("check() - PHYSICAL pagination - should pass")
        public void testCheck_physicalPagination_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users LIMIT 20";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.physical")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.PHYSICAL);

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed());
        }
    }

    @Nested
    @DisplayName("2. Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("check() - disabled - should skip")
        public void testCheck_disabled_skips() throws JSQLParserException {
            config.setEnabled(false);
            LogicalPaginationChecker disabledChecker = new LogicalPaginationChecker(detector, config);

            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.disabled")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .rowBounds(new RowBounds(0, 20))
                    .build();

            when(detector.detectPaginationType(any())).thenReturn(PaginationType.LOGICAL);

            ValidationResult result = ValidationResult.pass();
            disabledChecker.check(context, result);

            assertTrue(result.isPassed());
        }
    }

    @Nested
    @DisplayName("3. Constructor Migration Tests")
    class ConstructorMigrationTests {

        @Test
        @DisplayName("constructor - super(config) - should work")
        public void testConstructor_superConfig_works() {
            // Verify constructor calls super(config) correctly
            LogicalPaginationChecker checker = new LogicalPaginationChecker(detector, config);
            assertNotNull(checker);
            assertTrue(checker.isEnabled());
        }
    }
}
```

---

### 2. DummyConditionChecker 迁移

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DummyConditionChecker.java`

**Statement 类型**: UPDATE, DELETE, SELECT (所有带 WHERE 子句的语句)

**迁移步骤**:
1. ✅ 删除 check() 方法
2. ✅ 添加 visitSelect(), visitUpdate(), visitDelete() 方法
3. ✅ 添加 super(config) 到构造函数
4. ✅ 本地实现：isDummyConditionExpression(), validateDummyCondition()
5. ✅ 替换 extractWhere() 为直接 API 调用
6. ✅ 使用 addViolation() 辅助方法

**完整代码**:
```java
package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

/**
 * Checker that detects invalid/dummy WHERE conditions.
 * (Javadoc unchanged)
 */
public class DummyConditionChecker extends AbstractRuleChecker {

  private final DummyConditionConfig config;

  public DummyConditionChecker(DummyConditionConfig config) {
    super(config);  // ✅ NEW: Pass config to AbstractRuleChecker
    this.config = config;
  }

  @Override
  protected void visitSelect(Select select, SqlContext context) {
    if (!isEnabled()) {
      return;
    }

    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
    Expression where = plainSelect.getWhere();
    validateDummyCondition(where);
  }

  @Override
  protected void visitUpdate(Update update, SqlContext context) {
    if (!isEnabled()) {
      return;
    }

    Expression where = update.getWhere();
    validateDummyCondition(where);
  }

  @Override
  protected void visitDelete(Delete delete, SqlContext context) {
    if (!isEnabled()) {
      return;
    }

    Expression where = delete.getWhere();
    validateDummyCondition(where);
  }

  /**
   * Validates WHERE expression for dummy conditions.
   */
  private void validateDummyCondition(Expression where) {
    if (where == null) {
      return;
    }

    boolean isDummy = false;

    // Pattern-based detection
    String normalizedWhere = where.toString().toLowerCase().replaceAll("\\s+", " ");
    for (String pattern : config.getPatterns()) {
      String normalizedPattern = pattern.toLowerCase().replaceAll("\\s+", " ");
      if (normalizedWhere.contains(normalizedPattern)) {
        isDummy = true;
        break;
      }
    }

    // Custom pattern detection
    if (!isDummy) {
      for (String pattern : config.getCustomPatterns()) {
        String normalizedPattern = pattern.toLowerCase().replaceAll("\\s+", " ");
        if (normalizedWhere.contains(normalizedPattern)) {
          isDummy = true;
          break;
        }
      }
    }

    // AST-based detection
    if (!isDummy && isDummyConditionExpression(where)) {
      isDummy = true;
    }

    if (isDummy) {
      addViolation(
          RiskLevel.HIGH,
          "检测到无效条件(如 1=1),请移除"
      );
    }
  }

  /**
   * Checks if expression is a dummy condition using AST analysis.
   * Replaces AbstractRuleChecker.isDummyCondition() which was removed.
   */
  private boolean isDummyConditionExpression(Expression where) {
    if (where == null) {
      return false;
    }

    // String-based patterns
    String whereStr = where.toString().trim().toUpperCase();
    if (whereStr.equals("1=1") || whereStr.equals("1 = 1") ||
        whereStr.equals("TRUE") || whereStr.equals("'1'='1'")) {
      return true;
    }

    // AST-based: Check for constant equality (e.g., 1=1, 'a'='a')
    if (where instanceof EqualsTo) {
      EqualsTo equals = (EqualsTo) where;
      Expression left = equals.getLeftExpression();
      Expression right = equals.getRightExpression();

      // Both sides are constants
      if (isConstant(left) && isConstant(right)) {
        // Same constant value (e.g., 1=1, 'a'='a')
        if (left.toString().equals(right.toString())) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Checks if expression is a constant value.
   */
  private boolean isConstant(Expression expr) {
    return expr instanceof LongValue || expr instanceof StringValue;
  }

  @Override
  public boolean isEnabled() {
    return config.isEnabled();
  }
}
```

**TDD 测试**: `DummyConditionCheckerMigrationTest.java` (5 tests)

---

### 3. NoConditionPaginationChecker 迁移

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/NoConditionPaginationChecker.java`

**Statement 类型**: SELECT only (只有 SELECT 有 LIMIT)

**迁移步骤**:
1. ✅ 删除 check() 方法
2. ✅ 添加 visitSelect() 方法
3. ✅ 添加 super(config) 到构造函数
4. ✅ 本地实现：isDummyConditionExpression()
5. ✅ 替换 extractWhere() 为 plainSelect.getWhere()
6. ✅ 保留 extractLimitDetails() 逻辑
7. ✅ 使用 addViolation() 辅助方法

**完整代码**: (省略，结构类似 visitSelect 模式)

**TDD 测试**: `NoConditionPaginationCheckerMigrationTest.java` (5 tests)

---

### 4. DeepPaginationChecker 迁移

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/DeepPaginationChecker.java`

**Statement 类型**: SELECT only

**迁移步骤**:
1. ✅ 删除 check() 方法
2. ✅ 添加 visitSelect() 方法
3. ✅ 添加 super(config) 到构造函数
4. ✅ 保留 offset 提取逻辑（PlainSelect.getOffset(), Limit.getOffset()）
5. ✅ 保留 earlyReturn 检查逻辑
6. ✅ 使用 addViolation() 辅助方法

**完整代码**: (省略，结构类似 visitSelect 模式)

**TDD 测试**: `DeepPaginationCheckerMigrationTest.java` (5 tests)

---

### 5. MissingOrderByChecker 迁移

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/MissingOrderByChecker.java`

**Statement 类型**: SELECT only

**迁移步骤**:
1. ✅ 删除 check() 方法
2. ✅ 添加 visitSelect() 方法
3. ✅ 添加 super(config) 到构造函数
4. ✅ 保留 ORDER BY 检查逻辑
5. ✅ 使用 addViolation() 辅助方法

**完整代码**: (省略，结构类似 visitSelect 模式)

**TDD 测试**: `MissingOrderByCheckerMigrationTest.java` (5 tests)

---

### 6. LargePageSizeChecker 迁移

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/LargePageSizeChecker.java`

**Statement 类型**: SELECT only

**迁移步骤**:
1. ✅ 删除 check() 方法
2. ✅ 添加 visitSelect() 方法
3. ✅ 添加 super(config) 到构造函数
4. ✅ 保留 pageSize 提取逻辑
5. ✅ 使用 addViolation() 辅助方法

**完整代码**: (省略，结构类似 visitSelect 模式)

**TDD 测试**: `LargePageSizeCheckerMigrationTest.java` (5 tests)

---

## 验收标准

### 功能验收
- [ ] 6 个 Checker 迁移完成
- [ ] 所有 check() 方法已删除（除 LogicalPaginationChecker）
- [ ] 所有构造函数调用 super(config)
- [ ] 本地实现所有依赖的工具方法
- [ ] 使用 addViolation() 辅助方法

### 测试验收
- [ ] 30 个新 TDD 测试通过（每个 Checker 5 个）
- [ ] 所有现有测试更新并通过
- [ ] 总测试数：476 tests, 0 errors, 0 failures

### 行为一致性验收
- [ ] LogicalPaginationChecker: LOGICAL 分页 → CRITICAL 违规
- [ ] DummyConditionChecker: 1=1, true → HIGH 违规
- [ ] NoConditionPaginationChecker: 无条件 LIMIT → CRITICAL 违规
- [ ] DeepPaginationChecker: 深分页 → MEDIUM 违规
- [ ] MissingOrderByChecker: 缺少 ORDER BY → LOW 违规
- [ ] LargePageSizeChecker: 大 pageSize → MEDIUM 违规

---

## 输出文件

### 修改文件 (6个)
1. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/LogicalPaginationChecker.java`
2. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DummyConditionChecker.java`
3. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/NoConditionPaginationChecker.java`
4. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/DeepPaginationChecker.java`
5. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/MissingOrderByChecker.java`
6. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/LargePageSizeChecker.java`

### 新增文件 (6个)
1. `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/pagination/impl/LogicalPaginationCheckerMigrationTest.java`
2. `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/DummyConditionCheckerMigrationTest.java`
3. `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/pagination/impl/NoConditionPaginationCheckerMigrationTest.java`
4. `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/pagination/impl/DeepPaginationCheckerMigrationTest.java`
5. `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/pagination/impl/MissingOrderByCheckerMigrationTest.java`
6. `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/pagination/impl/LargePageSizeCheckerMigrationTest.java`

---

## 依赖与限制

### 依赖
- ✅ Task 12.1-12.4 已完成
- ✅ Task 12.5-12.8 已完成

### 限制
- ⚠️ **串行执行**：Task 12.9 完成后才能执行 Task 12.10
- ⚠️ **测试覆盖**：必须保证 476 tests 全部通过

---

## 注意事项

### 1. LogicalPaginationChecker 特殊处理

LogicalPaginationChecker **不需要** visitor pattern，因为它不检查 SQL 语法，只检查 RowBounds/IPage 参数。

**正确做法**:
```java
// ❌ WRONG: Don't add visitor methods
@Override
protected void visitSelect(Select select, SqlContext context) { ... }

// ✅ CORRECT: Keep check() method but remove @Override
public void check(SqlContext context, ValidationResult result) { ... }
```

### 2. Statement 类型映射

| Checker | Statement Types | Visitor Methods |
|---------|----------------|-----------------|
| LogicalPaginationChecker | N/A | None (keep check()) |
| DummyConditionChecker | SELECT, UPDATE, DELETE | visitSelect, visitUpdate, visitDelete |
| NoConditionPaginationChecker | SELECT | visitSelect |
| DeepPaginationChecker | SELECT | visitSelect |
| MissingOrderByChecker | SELECT | visitSelect |
| LargePageSizeChecker | SELECT | visitSelect |

### 3. 本地方法实现指南

**isDummyConditionExpression()**:
```java
private boolean isDummyConditionExpression(Expression where) {
    if (where == null) {
        return false;
    }

    String whereStr = where.toString().toUpperCase();
    return whereStr.equals("1=1") || whereStr.equals("TRUE");
}
```

### 4. earlyReturn 机制

NoConditionPaginationChecker 设置 earlyReturn 标志，DeepPaginationChecker 检查此标志：
```java
// NoConditionPaginationChecker
result.getDetails().put("earlyReturn", true);

// DeepPaginationChecker
if (result.getDetails().containsKey("earlyReturn") &&
    result.getDetails().get("earlyReturn") == Boolean.TRUE) {
    return;  // Skip deep offset check
}
```

---

## 执行时间线

- **预计时间**: 3 工作日
  - Day 1: LogicalPaginationChecker, DummyConditionChecker (2 checkers)
  - Day 2: NoConditionPaginationChecker, DeepPaginationChecker (2 checkers)
  - Day 3: MissingOrderByChecker, LargePageSizeChecker, 集成测试 (2 checkers + verification)

---

**Created**: 2025-12-22
**Phase**: 12 - Core Architecture Unification
**Task ID**: 12.9
**Sequential**: After Task 12.5-12.8, before Task 12.10
