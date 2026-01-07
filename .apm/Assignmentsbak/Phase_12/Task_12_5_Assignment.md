---
phase: 12
task_id: 12.5
task_name: NoWhereClauseChecker Migration
agent: Agent_Architecture_Refactoring
estimated_duration: 1 day
dependencies: [Task_12.1, Task_12.2, Task_12.3, Task_12.4]
parallel_with: [Task_12.6, Task_12.7, Task_12.8]
---

# Task 12.5 Assignment: NoWhereClauseChecker Migration

## 任务目标

将 NoWhereClauseChecker 从 check() 实现迁移到 visitUpdate()/visitDelete() 方法，使用 JSqlParser API 直接访问 WHERE 子句（update.getWhere(), delete.getWhere()），删除旧的 check() 方法实现，更新测试使用 statement 字段，验证行为一致性。

---

## 背景说明

### 当前架构（Task 12.4 之前）
```java
public class NoWhereClauseChecker extends AbstractRuleChecker {
    @Override
    public void check(SqlContext context, ValidationResult result) {
        if (!isEnabled()) {
            return;
        }

        Statement statement = context.getParsedSql();

        // Skip INSERT statements
        if (statement instanceof Insert) {
            return;
        }

        // Extract WHERE using utility method
        Expression where = extractWhere(statement);  // ❌ Utility method

        if (where == null) {
            result.addViolation(
                RiskLevel.CRITICAL,
                "SQL语句缺少WHERE条件,可能导致全表操作",
                "请添加WHERE条件限制操作范围");
        }
    }
}
```

**问题**：
- check() 方法包含 instanceof 判断逻辑（已移至 AbstractRuleChecker）
- 使用 extractWhere() 工具方法（Task 12.4 已删除）
- 使用 context.getParsedSql()（已弃用，应使用 statement）
- 直接调用 result.addViolation()（应使用 addViolation() 辅助方法）

### 目标架构（Task 12.5 之后）
```java
public class NoWhereClauseChecker extends AbstractRuleChecker {
    // check() 方法已由 AbstractRuleChecker 提供（final 模板方法）

    @Override
    protected void visitUpdate(Update update, SqlContext context) {
        // 直接使用 JSqlParser API
        Expression where = update.getWhere();  // ✅ Direct API

        if (where == null) {
            addViolation(RiskLevel.CRITICAL,
                "UPDATE语句缺少WHERE条件,可能导致全表更新");
        }
    }

    @Override
    protected void visitDelete(Delete delete, SqlContext context) {
        // 直接使用 JSqlParser API
        Expression where = delete.getWhere();  // ✅ Direct API

        if (where == null) {
            addViolation(RiskLevel.CRITICAL,
                "DELETE语句缺少WHERE条件,可能导致全表删除");
        }
    }

    // No visitSelect/visitInsert - use default empty implementations
}
```

**改进**：
- ✅ 删除 check() 方法（使用模板方法）
- ✅ 类型安全：visitUpdate 接收 Update 参数，visitDelete 接收 Delete 参数
- ✅ 直接 API：update.getWhere() 而非 extractWhere(statement)
- ✅ 简洁代码：使用 addViolation() 辅助方法
- ✅ 选择性覆盖：只实现需要的 visitXxx() 方法

---

## 实现要求

### 1. NoWhereClauseChecker 迁移

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/NoWhereClauseChecker.java`

**完整迁移代码**:

```java
package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.update.Update;

/**
 * Rule checker that detects SQL statements missing WHERE clauses.
 *
 * <p>NoWhereClauseChecker validates UPDATE and DELETE statements to ensure they include
 * WHERE clauses. Missing WHERE clauses can cause catastrophic consequences:</p>
 *
 * <ul>
 *   <li><strong>DELETE without WHERE:</strong> Irreversible deletion of all table data</li>
 *   <li><strong>UPDATE without WHERE:</strong> Irreversible modification of all table rows</li>
 * </ul>
 *
 * <p><strong>Risk Level:</strong> CRITICAL - This is the most severe validation check</p>
 *
 * <p><strong>Implementation Details:</strong></p>
 * <ul>
 *   <li>Uses visitor pattern: overrides visitUpdate() and visitDelete()</li>
 *   <li>Direct API usage: update.getWhere() / delete.getWhere() instead of utility methods</li>
 *   <li>Does NOT check SELECT (handled by NoPaginationChecker with risk stratification)</li>
 *   <li>Does NOT check INSERT (INSERT has no WHERE clause by design)</li>
 * </ul>
 *
 * <p><strong>Edge Cases:</strong></p>
 * <ul>
 *   <li>INSERT statements are skipped (default visitInsert() is no-op)</li>
 *   <li>SELECT statements are skipped (default visitSelect() is no-op)</li>
 *   <li>Statements with dummy conditions like "WHERE 1=1" pass this check
 *       (handled by DummyConditionChecker)</li>
 *   <li>Complex WHERE clauses always pass regardless of effectiveness</li>
 * </ul>
 *
 * <p><strong>Migration from Phase 11:</strong></p>
 * <pre>{@code
 * // OLD (before Phase 12)
 * public void check(SqlContext context, ValidationResult result) {
 *     Statement stmt = context.getParsedSql();
 *     Expression where = extractWhere(stmt);  // Utility method
 *     if (where == null) {
 *         result.addViolation(...);
 *     }
 * }
 *
 * // NEW (Phase 12 onwards)
 * protected void visitUpdate(Update update, SqlContext context) {
 *     Expression where = update.getWhere();  // Direct API
 *     if (where == null) {
 *         addViolation(RiskLevel.CRITICAL, "UPDATE without WHERE");
 *     }
 * }
 * }</pre>
 *
 * @see AbstractRuleChecker
 * @see NoWhereClauseConfig
 * @since 1.0.0 (refactored in 1.1.0 for visitor pattern)
 */
public class NoWhereClauseChecker extends AbstractRuleChecker {

    private final NoWhereClauseConfig config;

    /**
     * Creates a NoWhereClauseChecker with the specified configuration.
     *
     * @param config the configuration for this checker
     */
    public NoWhereClauseChecker(NoWhereClauseConfig config) {
        super(config);  // Pass config to AbstractRuleChecker
        this.config = config;
    }

    /**
     * Returns whether this checker is enabled.
     *
     * @return true if enabled, false otherwise
     */
    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Validates UPDATE statements for missing WHERE clauses.
     *
     * <p>Checks if the UPDATE statement has a WHERE clause. If WHERE is null,
     * adds a CRITICAL violation.</p>
     *
     * <p><strong>Direct API Usage:</strong></p>
     * <pre>{@code
     * Expression where = update.getWhere();  // JSqlParser API
     * String tableName = update.getTable().getName();  // JSqlParser API
     * }</pre>
     *
     * @param update the UPDATE statement (type-safe, no casting needed)
     * @param context the SQL execution context
     * @since 1.1.0
     */
    @Override
    protected void visitUpdate(Update update, SqlContext context) {
        // Direct API call to get WHERE clause
        Expression where = update.getWhere();

        // If WHERE clause is missing, add CRITICAL violation
        if (where == null) {
            addViolation(
                RiskLevel.CRITICAL,
                "UPDATE语句缺少WHERE条件,可能导致全表更新");
        }
    }

    /**
     * Validates DELETE statements for missing WHERE clauses.
     *
     * <p>Checks if the DELETE statement has a WHERE clause. If WHERE is null,
     * adds a CRITICAL violation.</p>
     *
     * <p><strong>Direct API Usage:</strong></p>
     * <pre>{@code
     * Expression where = delete.getWhere();  // JSqlParser API
     * String tableName = delete.getTable().getName();  // JSqlParser API
     * }</pre>
     *
     * @param delete the DELETE statement (type-safe, no casting needed)
     * @param context the SQL execution context
     * @since 1.1.0
     */
    @Override
    protected void visitDelete(Delete delete, SqlContext context) {
        // Direct API call to get WHERE clause
        Expression where = delete.getWhere();

        // If WHERE clause is missing, add CRITICAL violation
        if (where == null) {
            addViolation(
                RiskLevel.CRITICAL,
                "DELETE语句缺少WHERE条件,可能导致全表删除");
        }
    }

    // ==================== MIGRATION NOTES ====================
    // The following methods have been REMOVED in Phase 12 migration:
    // - public void check(SqlContext context, ValidationResult result)
    //   Replaced by: AbstractRuleChecker template method calls visitUpdate/visitDelete
    //
    // - Expression extractWhere(Statement stmt)
    //   Replaced by: Direct API calls update.getWhere() / delete.getWhere()
    //
    // Benefits of new architecture:
    // 1. Type safety: No instanceof checks or casting
    // 2. Clarity: Direct API usage is self-documenting
    // 3. Performance: Single parse + type dispatch (vs. parse per Checker)
    // 4. Maintainability: Less code, fewer intermediate layers
}
```

**关键变更**：
1. ✅ 删除 `check()` 方法（AbstractRuleChecker 模板方法提供）
2. ✅ 添加 `visitUpdate()` 方法（处理 UPDATE 语句）
3. ✅ 添加 `visitDelete()` 方法（处理 DELETE 语句）
4. ✅ 直接使用 `update.getWhere()` 和 `delete.getWhere()`
5. ✅ 使用 `addViolation(RiskLevel, String)` 辅助方法
6. ✅ 构造函数传递 config 给父类：`super(config)`
7. ✅ 不覆盖 visitSelect 和 visitInsert（使用默认空实现）

---

### 2. TDD 测试用例设计

**位置**: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/NoWhereClauseCheckerMigrationTest.java`

**完整测试代码**:

```java
package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for NoWhereClauseChecker migration to visitor pattern
 */
@DisplayName("NoWhereClauseChecker Migration Tests")
public class NoWhereClauseCheckerMigrationTest {

    private NoWhereClauseConfig config;
    private NoWhereClauseChecker checker;

    @BeforeEach
    public void setUp() {
        config = new NoWhereClauseConfig();
        config.setEnabled(true);
        checker = new NoWhereClauseChecker(config);
    }

    @Nested
    @DisplayName("1. UPDATE Statement Tests")
    class UpdateStatementTests {

        /**
         * Test 1: UPDATE without WHERE should violate
         */
        @Test
        @DisplayName("visitUpdate() - no WHERE clause - should add CRITICAL violation")
        public void testVisitUpdate_noWhere_violates() throws JSQLParserException {
            // Parse UPDATE without WHERE
            String sql = "UPDATE users SET name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.updateNoWhere")
                    .type(SqlCommandType.UPDATE)
                    .statement(stmt)  // Use statement field (not parsedSql)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("UPDATE"));
            assertTrue(result.getViolations().get(0).getMessage().contains("WHERE"));
        }

        /**
         * Test 2: UPDATE with WHERE should pass
         */
        @Test
        @DisplayName("visitUpdate() - with WHERE clause - should pass")
        public void testVisitUpdate_withWhere_passes() throws JSQLParserException {
            // Parse UPDATE with WHERE
            String sql = "UPDATE users SET name = 'foo' WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.updateWithWhere")
                    .type(SqlCommandType.UPDATE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation
            assertTrue(result.isPassed(), "Should pass validation");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 3: UPDATE with dummy condition should pass (handled by DummyConditionChecker)
         */
        @Test
        @DisplayName("visitUpdate() - with dummy WHERE (1=1) - should pass")
        public void testVisitUpdate_dummyWhere_passes() throws JSQLParserException {
            // Parse UPDATE with WHERE 1=1 (dummy condition)
            String sql = "UPDATE users SET name = 'foo' WHERE 1=1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.updateDummyWhere")
                    .type(SqlCommandType.UPDATE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (dummy condition detection is handled by DummyConditionChecker)
            assertTrue(result.isPassed(), "Should pass - dummy condition handled elsewhere");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("2. DELETE Statement Tests")
    class DeleteStatementTests {

        /**
         * Test 4: DELETE without WHERE should violate
         */
        @Test
        @DisplayName("visitDelete() - no WHERE clause - should add CRITICAL violation")
        public void testVisitDelete_noWhere_violates() throws JSQLParserException {
            // Parse DELETE without WHERE
            String sql = "DELETE FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.deleteNoWhere")
                    .type(SqlCommandType.DELETE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("DELETE"));
            assertTrue(result.getViolations().get(0).getMessage().contains("WHERE"));
        }

        /**
         * Test 5: DELETE with WHERE should pass
         */
        @Test
        @DisplayName("visitDelete() - with WHERE clause - should pass")
        public void testVisitDelete_withWhere_passes() throws JSQLParserException {
            // Parse DELETE with WHERE
            String sql = "DELETE FROM users WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.deleteWithWhere")
                    .type(SqlCommandType.DELETE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation
            assertTrue(result.isPassed(), "Should pass validation");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 6: DELETE with complex WHERE should pass
         */
        @Test
        @DisplayName("visitDelete() - with complex WHERE clause - should pass")
        public void testVisitDelete_complexWhere_passes() throws JSQLParserException {
            // Parse DELETE with complex WHERE
            String sql = "DELETE FROM users WHERE id > 100 AND status = 'inactive' OR created_time < '2020-01-01'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.deleteComplexWhere")
                    .type(SqlCommandType.DELETE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation
            assertTrue(result.isPassed(), "Should pass validation");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("3. Other Statement Tests")
    class OtherStatementTests {

        /**
         * Test 7: INSERT should be skipped (default visitInsert is no-op)
         */
        @Test
        @DisplayName("visitInsert() - INSERT statement - should be skipped")
        public void testVisitInsert_skipped() throws JSQLParserException {
            // Parse INSERT
            String sql = "INSERT INTO users (id, name) VALUES (1, 'foo')";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.insert")
                    .type(SqlCommandType.INSERT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (INSERT skipped)
            assertTrue(result.isPassed(), "INSERT should be skipped");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 8: SELECT should be skipped (default visitSelect is no-op)
         */
        @Test
        @DisplayName("visitSelect() - SELECT without WHERE - should be skipped")
        public void testVisitSelect_skipped() throws JSQLParserException {
            // Parse SELECT without WHERE
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.select")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (SELECT skipped by NoWhereClauseChecker)
            // SELECT is handled by NoPaginationChecker with risk stratification
            assertTrue(result.isPassed(), "SELECT should be skipped");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("4. Configuration Tests")
    class ConfigurationTests {

        /**
         * Test 9: Disabled checker should skip validation
         */
        @Test
        @DisplayName("isEnabled=false - should skip validation")
        public void testDisabled_skipsValidation() throws JSQLParserException {
            // Disable checker
            config.setEnabled(false);
            NoWhereClauseChecker disabledChecker = new NoWhereClauseChecker(config);

            // Parse UPDATE without WHERE
            String sql = "UPDATE users SET name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.disabled")
                    .type(SqlCommandType.UPDATE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            disabledChecker.check(context, result);

            // Verify no violation (checker disabled)
            assertTrue(result.isPassed(), "Disabled checker should skip validation");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("5. Statement Field Migration Tests")
    class StatementFieldMigrationTests {

        /**
         * Test 10: Verify statement field is used (not parsedSql)
         */
        @Test
        @DisplayName("statement field - should work correctly")
        public void testStatementField_works() throws JSQLParserException {
            // Parse UPDATE without WHERE
            String sql = "UPDATE users SET name = 'bar'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            // Build context using statement field (NOT parsedSql)
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.statementField")
                    .type(SqlCommandType.UPDATE)
                    .statement(stmt)  // NEW field
                    .build();

            // Verify statement field is accessible
            assertNotNull(context.getStatement(), "statement field should be accessible");
            assertEquals(stmt, context.getStatement(), "statement should match");

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added (UPDATE without WHERE)
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
        }
    }
}
```

---

## 验收标准

### 功能验收
- [ ] NoWhereClauseChecker 迁移完成
- [ ] `check()` 方法已删除
- [ ] `visitUpdate()` 方法实现（检查 update.getWhere()）
- [ ] `visitDelete()` 方法实现（检查 delete.getWhere()）
- [ ] 直接使用 JSqlParser API（无 extractWhere 调用）
- [ ] 使用 `addViolation()` 辅助方法
- [ ] visitSelect 和 visitInsert 不覆盖（使用默认空实现）

### 测试验收
- [ ] `NoWhereClauseCheckerMigrationTest` 创建，包含 10 个测试用例（5个测试类组）
- [ ] Test 1-3: UPDATE 语句测试（无 WHERE 违规，有 WHERE 通过，dummy WHERE 通过）
- [ ] Test 4-6: DELETE 语句测试（无 WHERE 违规，有 WHERE 通过，复杂 WHERE 通过）
- [ ] Test 7-8: INSERT 和 SELECT 跳过测试
- [ ] Test 9: 禁用配置测试
- [ ] Test 10: statement 字段测试
- [ ] 所有 10 个新测试通过
- [ ] 现有 NoWhereClauseChecker 集成测试继续通过

### 行为一致性验收
- [ ] UPDATE without WHERE → CRITICAL 违规（行为不变）
- [ ] DELETE without WHERE → CRITICAL 违规（行为不变）
- [ ] UPDATE/DELETE with WHERE → 通过（行为不变）
- [ ] INSERT 跳过检查（行为不变）
- [ ] SELECT 跳过检查（行为不变）

---

## 输出文件

### 修改文件 (1个)
- `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/NoWhereClauseChecker.java`

### 新增文件 (1个)
- `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/NoWhereClauseCheckerMigrationTest.java`

---

## 依赖与限制

### 依赖
- ✅ Task 12.1 已完成（SqlContext.statement 字段）
- ✅ Task 12.2 已完成（StatementVisitor 接口）
- ✅ Task 12.3 已完成（RuleChecker 继承 StatementVisitor）
- ✅ Task 12.4 已完成（AbstractRuleChecker 模板方法，extractWhere 已删除）

### 限制
- ⚠️ **并行执行**：可与 Task 12.6-12.8 并行执行（独立 Checker，无依赖）
- ⚠️ **不修改其他 Checker**：只迁移 NoWhereClauseChecker
- ⚠️ **保持行为一致**：迁移后违规检测逻辑必须与旧实现完全一致

### 后续任务
- Task 12.9 将迁移剩余 6 个 Checker
- Task 12.10 将全局更新测试（parsedSql → statement）
- Task 12.11 将验证性能提升（N 次解析 → 1 次解析）

---

## 参考文档

### Implementation Plan
- `.apm/Implementation_Plan.md` - Task 12.5 详细说明（line 270-287）

### 相关代码
- NoWhereClauseChecker (当前): `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/NoWhereClauseChecker.java`
- AbstractRuleChecker (新模板方法): `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/AbstractRuleChecker.java`
- StatementVisitor: `sql-guard-core/src/main/java/com/footstone/sqlguard/visitor/StatementVisitor.java`

### Task 12.4 Important Findings
- ValidationResult.pass() 工厂方法（非构造函数）
- SqlContext.builder().type() 而非 .commandType()
- addViolation(RiskLevel, String) 为 2 参数方法

---

## 注意事项

### 1. 直接 API 使用模式
```java
// ❌ OLD - Utility method
Expression where = extractWhere(statement);

// ✅ NEW - Direct API
Expression where = update.getWhere();  // For UPDATE
Expression where = delete.getWhere();  // For DELETE
```

### 2. 违规消息差异化
```java
// UPDATE 消息
addViolation(RiskLevel.CRITICAL, "UPDATE语句缺少WHERE条件,可能导致全表更新");

// DELETE 消息
addViolation(RiskLevel.CRITICAL, "DELETE语句缺少WHERE条件,可能导致全表删除");
```

虽然旧实现使用统一消息，新实现建议差异化消息以提供更明确的上下文。

### 3. SELECT 语句不检查
NoWhereClauseChecker 不检查 SELECT 语句，原因：
- SELECT without WHERE 由 NoPaginationChecker 处理
- NoPaginationChecker 使用风险分层（CRITICAL/HIGH/MEDIUM）
- NoWhereClauseChecker 专注于 UPDATE/DELETE（不可逆操作）

### 4. 构造函数参数传递
```java
public NoWhereClauseChecker(NoWhereClauseConfig config) {
    super(config);  // ✅ Pass to AbstractRuleChecker
    this.config = config;
}
```

AbstractRuleChecker 现在接受 `CheckerConfig` 参数，所有子类必须传递。

### 5. 测试字段迁移
```java
// ❌ OLD
SqlContext context = SqlContext.builder()
    .parsedSql(parser.parse(sql))  // Deprecated field
    .build();

// ✅ NEW
SqlContext context = SqlContext.builder()
    .statement(parser.parse(sql))  // New field
    .build();
```

---

## 执行时间线

- **预计时间**: 1 工作日
- **上午**:
  - 迁移 NoWhereClauseChecker.java（删除 check，添加 visitUpdate/visitDelete）
  - 编写 Test 1-5（UPDATE 和 DELETE 基础测试）
- **下午**:
  - 编写 Test 6-10（其他语句、配置、字段迁移测试）
  - 运行所有测试，验证通过
  - 验证行为一致性（与旧实现对比）

---

**Created**: 2025-12-22
**Phase**: 12 - Core Architecture Unification
**Agent**: Agent_Architecture_Refactoring
**Task ID**: 12.5
**Dependencies**: Task 12.1, 12.2, 12.3, 12.4 (All Completed)
**Parallel**: Task 12.6, 12.7, 12.8 (Can execute concurrently)
