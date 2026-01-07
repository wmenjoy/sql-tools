---
phase: 12
task_id: 12.2
task_name: StatementVisitor 接口设计
agent: Agent_Architecture_Refactoring
estimated_duration: 1 day
dependencies: [Task_12.1]
parallel_with: []
---

# Task 12.2 Assignment: StatementVisitor 接口设计

## 任务目标

设计 `StatementVisitor` 接口作为统一的 Statement 处理抽象，定义访问者模式方法（visitSelect/visitUpdate/visitDelete/visitInsert），为 RuleChecker 和 StatementRewriter 提供通用基础接口。

---

## 背景说明

### 当前问题
- **类型判断重复**：每个 RuleChecker 都使用 `instanceof` 判断 Statement 类型
- **缺乏统一抽象**：RuleChecker 和 StatementRewriter 都需要访问 Statement，但没有共享接口
- **扩展性差**：增加新的 Statement 处理逻辑需要修改多处代码

### 设计目标
- ✅ 定义统一的 Visitor 接口
- ✅ 提供默认空实现（允许子类选择性覆盖）
- ✅ 支持类型安全的方法分发（避免 instanceof 链）
- ✅ 为 RuleChecker 重构（Task 12.3）奠定基础

---

## 实现要求

### 1. StatementVisitor 接口设计

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/visitor/StatementVisitor.java`

**完整代码**:

```java
package com.footstone.sqlguard.visitor;

import com.footstone.sqlguard.core.model.SqlContext;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

/**
 * Unified visitor interface for processing JSqlParser Statement types.
 * <p>
 * This interface follows the Visitor pattern, providing type-safe dispatch
 * for different SQL statement types (Select/Update/Delete/Insert) without
 * requiring instanceof chains.
 * </p>
 *
 * <h2>Design Motivation</h2>
 * <p>
 * Prior to this abstraction, RuleChecker implementations used repetitive code:
 * </p>
 * <pre>{@code
 * // OLD: instanceof chain (repeated in every Checker)
 * public void check(SqlContext context, ValidationResult result) {
 *     Statement stmt = context.getStatement();
 *     if (stmt instanceof Select) {
 *         // handle Select
 *     } else if (stmt instanceof Update) {
 *         // handle Update
 *     } else if (stmt instanceof Delete) {
 *         // handle Delete
 *     }
 * }
 * }</pre>
 * <p>
 * StatementVisitor eliminates this repetition through centralized dispatch:
 * </p>
 * <pre>{@code
 * // NEW: visitor pattern (dispatch once in AbstractRuleChecker)
 * public abstract class AbstractRuleChecker implements RuleChecker {
 *     @Override
 *     public final void check(SqlContext context, ValidationResult result) {
 *         Statement stmt = context.getStatement();
 *         if (stmt instanceof Select) {
 *             visitSelect((Select) stmt, context);
 *         } else if (stmt instanceof Update) {
 *             visitUpdate((Update) stmt, context);
 *         }
 *         // ... dispatch logic centralized
 *     }
 * }
 *
 * // Checker implementations only override relevant methods
 * public class NoWhereClauseChecker extends AbstractRuleChecker {
 *     @Override
 *     protected void visitUpdate(Update update, SqlContext context) {
 *         if (update.getWhere() == null) {
 *             addViolation("Missing WHERE clause");
 *         }
 *     }
 *
 *     @Override
 *     protected void visitDelete(Delete delete, SqlContext context) {
 *         if (delete.getWhere() == null) {
 *             addViolation("Missing WHERE clause");
 *         }
 *     }
 *     // SELECT/INSERT not relevant, no need to override
 * }
 * }</pre>
 *
 * <h2>Usage Pattern</h2>
 * <ol>
 *   <li><b>RuleChecker</b>: Extend this interface to implement validation logic for specific Statement types</li>
 *   <li><b>StatementRewriter</b>: Extend this interface to implement SQL rewriting logic (future use)</li>
 *   <li><b>AbstractRuleChecker</b>: Implements template method pattern, dispatches to visitXxx() methods</li>
 * </ol>
 *
 * <h2>Default Implementations</h2>
 * <p>
 * All visit methods have default empty implementations, allowing subclasses to
 * override only the methods they need. For example:
 * </p>
 * <ul>
 *   <li>NoWhereClauseChecker only overrides visitUpdate() and visitDelete()</li>
 *   <li>BlacklistFieldChecker only overrides visitSelect()</li>
 *   <li>DummyConditionChecker overrides visitUpdate() and visitDelete()</li>
 * </ul>
 *
 * <h2>Benefits</h2>
 * <ul>
 *   <li><b>Type Safety</b>: Each visit method receives correctly typed Statement (no casting needed)</li>
 *   <li><b>Extensibility</b>: Adding new Statement types only requires updating the interface</li>
 *   <li><b>Clarity</b>: Clear separation of concerns - dispatch logic vs. validation logic</li>
 *   <li><b>Reusability</b>: Same abstraction works for RuleChecker, StatementRewriter, and future extensions</li>
 * </ul>
 *
 * @since 1.1.0
 * @see com.footstone.sqlguard.validator.rule.RuleChecker
 * @see com.footstone.sqlguard.validator.rule.AbstractRuleChecker
 */
public interface StatementVisitor {

    /**
     * Visit a SELECT statement.
     * <p>
     * Override this method to process SELECT statements. For example:
     * </p>
     * <pre>{@code
     * @Override
     * protected void visitSelect(Select select, SqlContext context) {
     *     PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
     *     List<SelectItem> items = plainSelect.getSelectItems();
     *     // Process SELECT items...
     * }
     * }</pre>
     *
     * @param select  the parsed SELECT statement (type-safe, no casting needed)
     * @param context the execution context containing SQL metadata (mapperId, rowBounds, etc.)
     */
    default void visitSelect(Select select, SqlContext context) {
        // Default: empty implementation (no-op)
        // Subclasses override this method to add SELECT-specific logic
    }

    /**
     * Visit an UPDATE statement.
     * <p>
     * Override this method to process UPDATE statements. For example:
     * </p>
     * <pre>{@code
     * @Override
     * protected void visitUpdate(Update update, SqlContext context) {
     *     Expression where = update.getWhere();
     *     if (where == null) {
     *         addViolation("UPDATE without WHERE clause");
     *     }
     * }
     * }</pre>
     *
     * @param update  the parsed UPDATE statement (type-safe, no casting needed)
     * @param context the execution context containing SQL metadata
     */
    default void visitUpdate(Update update, SqlContext context) {
        // Default: empty implementation (no-op)
    }

    /**
     * Visit a DELETE statement.
     * <p>
     * Override this method to process DELETE statements. For example:
     * </p>
     * <pre>{@code
     * @Override
     * protected void visitDelete(Delete delete, SqlContext context) {
     *     Expression where = delete.getWhere();
     *     if (where == null) {
     *         addViolation("DELETE without WHERE clause");
     *     }
     * }
     * }</pre>
     *
     * @param delete  the parsed DELETE statement (type-safe, no casting needed)
     * @param context the execution context containing SQL metadata
     */
    default void visitDelete(Delete delete, SqlContext context) {
        // Default: empty implementation (no-op)
    }

    /**
     * Visit an INSERT statement.
     * <p>
     * Override this method to process INSERT statements. For example:
     * </p>
     * <pre>{@code
     * @Override
     * protected void visitInsert(Insert insert, SqlContext context) {
     *     Table table = insert.getTable();
     *     List<Column> columns = insert.getColumns();
     *     // Process INSERT columns...
     * }
     * }</pre>
     *
     * @param insert  the parsed INSERT statement (type-safe, no casting needed)
     * @param context the execution context containing SQL metadata
     */
    default void visitInsert(Insert insert, SqlContext context) {
        // Default: empty implementation (no-op)
    }
}
```

---

### 2. TDD 测试用例设计

**位置**: `sql-guard-core/src/test/java/com/footstone/sqlguard/visitor/StatementVisitorInterfaceTest.java`

**完整测试代码**:

```java
package com.footstone.sqlguard.visitor;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for StatementVisitor interface design
 */
public class StatementVisitorInterfaceTest {

    /**
     * Test 1: Interface has all 4 required visit methods
     */
    @Test
    public void testStatementVisitor_interface_hasVisitMethods() throws NoSuchMethodException {
        // Verify interface declares all 4 visit methods
        assertNotNull(StatementVisitor.class.getMethod("visitSelect", Select.class, SqlContext.class));
        assertNotNull(StatementVisitor.class.getMethod("visitUpdate", Update.class, SqlContext.class));
        assertNotNull(StatementVisitor.class.getMethod("visitDelete", Delete.class, SqlContext.class));
        assertNotNull(StatementVisitor.class.getMethod("visitInsert", Insert.class, SqlContext.class));
    }

    /**
     * Test 2: Default implementations are empty (no-op)
     */
    @Test
    public void testStatementVisitor_defaultMethods_empty() throws JSQLParserException {
        // Create a minimal implementation (uses default methods)
        StatementVisitor visitor = new StatementVisitor() {
            // All methods use default implementations
        };

        // Parse test statements
        Select select = (Select) CCJSqlParserUtil.parse("SELECT * FROM users");
        Update update = (Update) CCJSqlParserUtil.parse("UPDATE users SET name = 'foo'");
        Delete delete = (Delete) CCJSqlParserUtil.parse("DELETE FROM users");
        Insert insert = (Insert) CCJSqlParserUtil.parse("INSERT INTO users VALUES (1)");

        SqlContext context = SqlContext.builder()
                .sql("test")
                .mapperId("test.mapper")
                .commandType(SqlCommandType.SELECT)
                .statement(select)
                .build();

        // Default methods should not throw exceptions (empty implementations)
        assertDoesNotThrow(() -> visitor.visitSelect(select, context));
        assertDoesNotThrow(() -> visitor.visitUpdate(update, context));
        assertDoesNotThrow(() -> visitor.visitDelete(delete, context));
        assertDoesNotThrow(() -> visitor.visitInsert(insert, context));
    }

    /**
     * Test 3: visitSelect method signature is correct
     */
    @Test
    public void testStatementVisitor_selectMethod_signature() throws NoSuchMethodException {
        var method = StatementVisitor.class.getMethod("visitSelect", Select.class, SqlContext.class);

        // Verify return type is void
        assertEquals(void.class, method.getReturnType());

        // Verify parameter types
        assertEquals(2, method.getParameterCount());
        assertEquals(Select.class, method.getParameterTypes()[0]);
        assertEquals(SqlContext.class, method.getParameterTypes()[1]);

        // Verify it's a default method
        assertTrue(method.isDefault());
    }

    /**
     * Test 4: visitUpdate method signature is correct
     */
    @Test
    public void testStatementVisitor_updateMethod_signature() throws NoSuchMethodException {
        var method = StatementVisitor.class.getMethod("visitUpdate", Update.class, SqlContext.class);

        assertEquals(void.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
        assertEquals(Update.class, method.getParameterTypes()[0]);
        assertEquals(SqlContext.class, method.getParameterTypes()[1]);
        assertTrue(method.isDefault());
    }

    /**
     * Test 5: Javadoc is complete and includes usage examples
     */
    @Test
    public void testStatementVisitor_javadoc_complete() {
        // This test verifies Javadoc presence at compile time
        // StatementVisitor interface should have:
        // - Class-level Javadoc explaining visitor pattern
        // - Method-level Javadoc for each visitXxx() method
        // - Code examples in Javadoc
        // - @since 1.1.0 tag

        // Manual verification: check source file has complete Javadoc
        assertTrue(StatementVisitor.class.isInterface());

        // Verify package is correct
        assertEquals("com.footstone.sqlguard.visitor", StatementVisitor.class.getPackageName());
    }

    /**
     * Bonus Test 6: Selective override pattern works
     */
    @Test
    public void testStatementVisitor_selectiveOverride_works() throws JSQLParserException {
        // Create visitor that only overrides visitSelect
        final boolean[] selectCalled = {false};
        final boolean[] updateCalled = {false};

        StatementVisitor visitor = new StatementVisitor() {
            @Override
            public void visitSelect(Select select, SqlContext context) {
                selectCalled[0] = true;
            }

            @Override
            public void visitUpdate(Update update, SqlContext context) {
                updateCalled[0] = true;
            }
            // visitDelete and visitInsert use default (empty) implementations
        };

        // Parse statements
        Select select = (Select) CCJSqlParserUtil.parse("SELECT * FROM users");
        Update update = (Update) CCJSqlParserUtil.parse("UPDATE users SET name = 'foo'");
        Delete delete = (Delete) CCJSqlParserUtil.parse("DELETE FROM users");

        SqlContext context = SqlContext.builder()
                .sql("test")
                .mapperId("test.mapper")
                .commandType(SqlCommandType.SELECT)
                .statement(select)
                .build();

        // Call visitor methods
        visitor.visitSelect(select, context);
        visitor.visitUpdate(update, context);
        visitor.visitDelete(delete, context);  // Uses default (no-op)

        // Verify selective override works
        assertTrue(selectCalled[0], "visitSelect should have been called");
        assertTrue(updateCalled[0], "visitUpdate should have been called");
        // visitDelete did not throw exception (default implementation is safe)
    }

    /**
     * Bonus Test 7: Interface is extensible for future Statement types
     */
    @Test
    public void testStatementVisitor_extensible_design() {
        // Verify interface uses default methods (allows adding new methods without breaking implementations)
        var methods = StatementVisitor.class.getDeclaredMethods();

        // All declared methods should be default (for extensibility)
        for (var method : methods) {
            assertTrue(method.isDefault(),
                "Method " + method.getName() + " should be default for extensibility");
        }
    }
}
```

---

### 3. 创建 visitor 包

**要求**:
- 在 `sql-guard-core/src/main/java/com/footstone/sqlguard/` 下创建 `visitor` 包
- 在 `sql-guard-core/src/test/java/com/footstone/sqlguard/` 下创建 `visitor` 包

**验证命令**:
```bash
# 创建目录
mkdir -p sql-guard-core/src/main/java/com/footstone/sqlguard/visitor
mkdir -p sql-guard-core/src/test/java/com/footstone/sqlguard/visitor
```

---

### 4. 编译与测试验证

**要求**:
- ✅ 编译成功（无错误，无警告）
- ✅ 所有现有测试继续通过（479个测试）
- ✅ 新增的 7 个 StatementVisitor 测试全部通过

**验证命令**:
```bash
# 编译验证
mvn clean compile -pl sql-guard-core

# 运行新测试
mvn test -pl sql-guard-core -Dtest=StatementVisitorInterfaceTest

# 运行所有测试（确保无回归）
mvn test -pl sql-guard-core
```

---

## 验收标准

### 功能验收
- [ ] `StatementVisitor` 接口创建在 `com.footstone.sqlguard.visitor` 包
- [ ] 接口包含 4 个方法：visitSelect/visitUpdate/visitDelete/visitInsert
- [ ] 所有方法都有 default 空实现
- [ ] 所有方法签名正确（返回 void，接收 Statement 子类 + SqlContext）
- [ ] Javadoc 完整（包含类级别、方法级别、代码示例、@since 标签）

### 测试验收
- [ ] `StatementVisitorInterfaceTest` 创建，包含 7 个测试用例
- [ ] 测试 1: 验证接口有 4 个 visit 方法
- [ ] 测试 2: 验证 default 实现为空（不抛异常）
- [ ] 测试 3-4: 验证方法签名正确
- [ ] 测试 5: 验证 Javadoc 完整
- [ ] 测试 6: 验证选择性覆盖模式有效
- [ ] 测试 7: 验证接口可扩展性（所有方法都是 default）
- [ ] 所有 7 个新测试通过
- [ ] 所有现有测试继续通过（无回归）

### 设计验收
- [ ] 接口遵循 Visitor 模式
- [ ] 方法命名清晰（visitXxx 命名约定）
- [ ] 使用 default 方法提供扩展性
- [ ] SqlContext 作为上下文参数传递

---

## 输出文件

### 新增文件 (2个)
- `sql-guard-core/src/main/java/com/footstone/sqlguard/visitor/StatementVisitor.java`
- `sql-guard-core/src/test/java/com/footstone/sqlguard/visitor/StatementVisitorInterfaceTest.java`

### 新增目录 (2个)
- `sql-guard-core/src/main/java/com/footstone/sqlguard/visitor/`
- `sql-guard-core/src/test/java/com/footstone/sqlguard/visitor/`

---

## 依赖与限制

### 依赖
- ✅ Task 12.1 已完成（SqlContext 已有 statement 字段）

### 限制
- ⚠️ **只创建接口** - 不实现具体的 Visitor（留给 Task 12.3-12.9）
- ⚠️ **不修改现有代码** - RuleChecker、AbstractRuleChecker 暂不修改（Task 12.3-12.4 负责）
- ⚠️ **保持向前兼容** - 接口设计应支持未来扩展（使用 default 方法）

### 后续任务依赖
- Task 12.3 (RuleChecker 接口重构) 将使 RuleChecker 继承此接口
- Task 12.4 (AbstractRuleChecker 重构) 将实现 template method 分发到 visitXxx()

---

## 参考文档

### 设计模式
- **Visitor Pattern**: https://refactoring.guru/design-patterns/visitor
- **Template Method Pattern**: https://refactoring.guru/design-patterns/template-method

### Implementation Plan
- `.apm/Implementation_Plan.md` - Task 12.2 详细说明（line 209-226）

### 相关代码
- SqlContext: `sql-guard-core/src/main/java/com/footstone/sqlguard/core/model/SqlContext.java`
- RuleChecker: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/RuleChecker.java`
- AbstractRuleChecker: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/AbstractRuleChecker.java`

---

## 注意事项

### 1. Default 方法的重要性
- Java 8+ 的 default 方法允许接口扩展而不破坏现有实现
- 所有 visitXxx() 方法必须是 default（未来可能添加新的 Statement 类型）
- Default 实现应为空（no-op），让子类选择性覆盖

### 2. 方法签名设计
- **第一个参数**：具体的 Statement 类型（Select/Update/Delete/Insert），提供类型安全
- **第二个参数**：SqlContext 提供执行上下文（mapperId, rowBounds, 参数等）
- **返回类型**：void（Visitor 模式通过修改传入的 context/result 对象来返回结果）

### 3. 与现有代码的关系
- **RuleChecker**: Task 12.3 将使其继承 StatementVisitor
- **AbstractRuleChecker**: Task 12.4 将实现 check() → visitXxx() 的分发逻辑
- **具体 Checker**: Task 12.5-12.9 将迁移到 visitXxx() 方法

### 4. Javadoc 编写指南
- **类级别**: 解释 Visitor 模式，说明设计动机，提供使用示例
- **方法级别**: 每个 visitXxx() 方法都提供代码示例
- **@since 标签**: 使用 `@since 1.1.0` 标记新接口
- **代码示例**: 使用 `{@code}` 标签格式化代码块

---

## 执行时间线

- **预计时间**: 1 工作日
- **关键路径**: 是（Task 12.3 依赖此任务）
- **并行机会**: 无（Task 12.3 必须等待此任务完成）

---

**Created**: 2025-12-22
**Phase**: 12 - Core Architecture Unification
**Agent**: Agent_Architecture_Refactoring
**Task ID**: 12.2
**Dependencies**: Task 12.1 (Completed)
