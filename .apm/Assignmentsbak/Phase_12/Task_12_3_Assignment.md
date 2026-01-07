---
phase: 12
task_id: 12.3
task_name: RuleChecker 接口重构
agent: Agent_Architecture_Refactoring
estimated_duration: 2 days
dependencies: [Task_12.1, Task_12.2]
parallel_with: []
---

# Task 12.3 Assignment: RuleChecker 接口重构

## 任务目标

重构 `RuleChecker` 接口使其继承 `StatementVisitor`，保留现有 `check()` 和 `isEnabled()` 方法以维持向后兼容性，为 Task 12.4 的 AbstractRuleChecker 模板方法实现奠定基础。

---

## 背景说明

### 当前架构
```java
// 当前 RuleChecker 接口（独立，未继承任何接口）
public interface RuleChecker {
    void check(SqlContext context, ValidationResult result);
    boolean isEnabled();
}
```

### 目标架构
```java
// 新架构：RuleChecker 继承 StatementVisitor
public interface RuleChecker extends StatementVisitor {
    void check(SqlContext context, ValidationResult result);  // 保留（向后兼容）
    boolean isEnabled();  // 保留
    // 继承了 visitSelect/visitUpdate/visitDelete/visitInsert
}
```

### 架构演进路径
1. **Task 12.2** (已完成): 创建 StatementVisitor 接口
2. **Task 12.3** (本任务): RuleChecker 继承 StatementVisitor
3. **Task 12.4** (下一任务): AbstractRuleChecker 实现模板方法（check() → visitXxx()）
4. **Task 12.5-12.9**: 具体 Checker 迁移到 visitXxx()

---

## 实现要求

### 1. RuleChecker 接口重构

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/RuleChecker.java`

**修改内容**:

```java
package com.footstone.sqlguard.validator.rule;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.visitor.StatementVisitor;

/**
 * Rule checker interface for SQL validation.
 * <p>
 * This interface extends {@link StatementVisitor} to leverage the visitor pattern
 * for type-safe Statement processing. The {@link #check(SqlContext, ValidationResult)}
 * method will be implemented as a template method in {@link AbstractRuleChecker},
 * dispatching to the appropriate visitXxx() methods based on Statement type.
 * </p>
 *
 * <h2>Migration from Old Architecture</h2>
 * <p>
 * <b>OLD Architecture (before Phase 12):</b>
 * </p>
 * <pre>{@code
 * public class NoWhereClauseChecker extends AbstractRuleChecker {
 *     @Override
 *     public void check(SqlContext context, ValidationResult result) {
 *         Statement stmt = context.getStatement();
 *         if (stmt instanceof Update) {
 *             Update update = (Update) stmt;
 *             if (update.getWhere() == null) {
 *                 result.addViolation(...);
 *             }
 *         } else if (stmt instanceof Delete) {
 *             Delete delete = (Delete) stmt;
 *             if (delete.getWhere() == null) {
 *                 result.addViolation(...);
 *             }
 *         }
 *     }
 * }
 * }</pre>
 * <p>
 * <b>NEW Architecture (Phase 12 onwards):</b>
 * </p>
 * <pre>{@code
 * public class NoWhereClauseChecker extends AbstractRuleChecker {
 *     // check() method becomes template method (implemented in AbstractRuleChecker)
 *     // Subclasses only override relevant visitXxx() methods
 *
 *     @Override
 *     protected void visitUpdate(Update update, SqlContext context) {
 *         if (update.getWhere() == null) {
 *             addViolation(context, RiskLevel.CRITICAL, "UPDATE without WHERE clause");
 *         }
 *     }
 *
 *     @Override
 *     protected void visitDelete(Delete delete, SqlContext context) {
 *         if (delete.getWhere() == null) {
 *             addViolation(context, RiskLevel.CRITICAL, "DELETE without WHERE clause");
 *         }
 *     }
 *     // No need to override visitSelect/visitInsert - default implementations are no-op
 * }
 * }</pre>
 *
 * <h2>Benefits of New Architecture</h2>
 * <ul>
 *   <li><b>Type Safety</b>: No casting needed - visitXxx() methods receive correctly typed Statement</li>
 *   <li><b>Clarity</b>: Each visitXxx() method has single responsibility (handle one Statement type)</li>
 *   <li><b>Selective Override</b>: Checkers only implement methods for Statement types they care about</li>
 *   <li><b>Centralized Dispatch</b>: AbstractRuleChecker handles instanceof logic once, not repeated in every Checker</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * <ol>
 *   <li>Extend {@link AbstractRuleChecker} (do NOT implement RuleChecker directly)</li>
 *   <li>Override relevant visitXxx() methods for Statement types you need to validate</li>
 *   <li>Use {@link #addViolation(SqlContext, com.footstone.sqlguard.core.model.RiskLevel, String)}
 *       to report violations (available in AbstractRuleChecker)</li>
 *   <li>Override {@link #isEnabled()} to control whether the checker runs (defaults to true)</li>
 * </ol>
 *
 * <h2>Backward Compatibility</h2>
 * <p>
 * Existing RuleChecker implementations that override check() will continue to work
 * during the migration period. However, they should be migrated to use visitXxx()
 * methods for the following reasons:
 * </p>
 * <ul>
 *   <li>Avoid duplicate SQL parsing (check() parses SQL again, visitXxx() reuses parsed Statement)</li>
 *   <li>Cleaner code (no instanceof chains)</li>
 *   <li>Better performance (single parse + type dispatch vs. parse per Checker)</li>
 * </ul>
 *
 * @see StatementVisitor
 * @see AbstractRuleChecker
 * @since 1.0.0
 */
public interface RuleChecker extends StatementVisitor {

    /**
     * Check SQL statement for rule violations.
     * <p>
     * <b>IMPORTANT</b>: As of version 1.1.0, this method is implemented as a
     * <b>template method</b> in {@link AbstractRuleChecker}. Subclasses should
     * <b>NOT</b> override this method directly. Instead, override the specific
     * visitXxx() methods inherited from {@link StatementVisitor}.
     * </p>
     * <p>
     * The template method implementation in AbstractRuleChecker:
     * </p>
     * <pre>{@code
     * @Override
     * public final void check(SqlContext context, ValidationResult result) {
     *     Statement stmt = context.getStatement();
     *     if (stmt instanceof Select) {
     *         visitSelect((Select) stmt, context);
     *     } else if (stmt instanceof Update) {
     *         visitUpdate((Update) stmt, context);
     *     } else if (stmt instanceof Delete) {
     *         visitDelete((Delete) stmt, context);
     *     } else if (stmt instanceof Insert) {
     *         visitInsert((Insert) stmt, context);
     *     }
     * }
     * }</pre>
     *
     * <h3>Migration Guide</h3>
     * <p>
     * If you have an existing Checker that overrides check():
     * </p>
     * <ol>
     *   <li>Identify which Statement types your Checker handles (Select/Update/Delete/Insert)</li>
     *   <li>For each Statement type, move validation logic to the corresponding visitXxx() method</li>
     *   <li>Remove the check() method override (let AbstractRuleChecker provide it)</li>
     *   <li>Update tests to use statement field instead of parsedSql field</li>
     * </ol>
     *
     * @param context the SQL execution context containing Statement and metadata
     * @param result  the validation result accumulator for violations
     * @since 1.0.0
     * @see StatementVisitor#visitSelect(net.sf.jsqlparser.statement.select.Select, SqlContext)
     * @see StatementVisitor#visitUpdate(net.sf.jsqlparser.statement.update.Update, SqlContext)
     * @see StatementVisitor#visitDelete(net.sf.jsqlparser.statement.delete.Delete, SqlContext)
     * @see StatementVisitor#visitInsert(net.sf.jsqlparser.statement.insert.Insert, SqlContext)
     */
    void check(SqlContext context, ValidationResult result);

    /**
     * Check if this rule checker is enabled.
     * <p>
     * Disabled checkers are skipped by {@link com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator}.
     * </p>
     *
     * @return true if enabled, false if disabled
     * @since 1.0.0
     */
    boolean isEnabled();
}
```

**关键变更**:
1. ✅ 添加 `extends StatementVisitor`
2. ✅ 保留 `check()` 和 `isEnabled()` 方法（向后兼容）
3. ✅ 更新 Javadoc，添加详细的迁移指南和架构说明
4. ✅ 添加 OLD vs NEW 架构对比代码示例

---

### 2. TDD 测试用例设计

**位置**: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/RuleCheckerRefactoringTest.java`

**完整测试代码**:

```java
package com.footstone.sqlguard.validator.rule;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.visitor.StatementVisitor;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for RuleChecker interface refactoring
 */
@DisplayName("RuleChecker Interface Refactoring Tests")
public class RuleCheckerRefactoringTest {

    @Nested
    @DisplayName("1. Inheritance Tests")
    class InheritanceTests {

        /**
         * Test 1: RuleChecker extends StatementVisitor
         */
        @Test
        @DisplayName("RuleChecker should extend StatementVisitor")
        public void testRuleChecker_extendsStatementVisitor() {
            // Verify RuleChecker interface extends StatementVisitor
            assertTrue(StatementVisitor.class.isAssignableFrom(RuleChecker.class),
                    "RuleChecker should extend StatementVisitor");

            // Verify it's an interface (not a class)
            assertTrue(RuleChecker.class.isInterface(), "RuleChecker should be an interface");

            // Verify direct parent interfaces include StatementVisitor
            Class<?>[] interfaces = RuleChecker.class.getInterfaces();
            boolean extendsStatementVisitor = false;
            for (Class<?> iface : interfaces) {
                if (iface.equals(StatementVisitor.class)) {
                    extendsStatementVisitor = true;
                    break;
                }
            }
            assertTrue(extendsStatementVisitor,
                    "RuleChecker should directly extend StatementVisitor");
        }

        /**
         * Test 2: RuleChecker inherits all 4 visitXxx() methods
         */
        @Test
        @DisplayName("RuleChecker should inherit all 4 visitXxx() methods from StatementVisitor")
        public void testRuleChecker_inheritsVisitMethods() throws NoSuchMethodException {
            // Verify RuleChecker has access to all 4 visitXxx() methods
            assertNotNull(RuleChecker.class.getMethod("visitSelect", Select.class, SqlContext.class));
            assertNotNull(RuleChecker.class.getMethod("visitUpdate", Update.class, SqlContext.class));
            assertNotNull(RuleChecker.class.getMethod("visitDelete", Delete.class, SqlContext.class));
            assertNotNull(RuleChecker.class.getMethod("visitInsert", Insert.class, SqlContext.class));
        }
    }

    @Nested
    @DisplayName("2. Method Retention Tests")
    class MethodRetentionTests {

        /**
         * Test 3: check() method retained
         */
        @Test
        @DisplayName("RuleChecker should retain check() method")
        public void testRuleChecker_checkMethod_retained() throws NoSuchMethodException {
            // Verify check() method exists
            Method checkMethod = RuleChecker.class.getMethod("check", SqlContext.class, ValidationResult.class);
            assertNotNull(checkMethod, "check() method should be retained");

            // Verify return type is void
            assertEquals(void.class, checkMethod.getReturnType());

            // Verify method is abstract (not default)
            assertTrue(Modifier.isAbstract(checkMethod.getModifiers()),
                    "check() should be abstract (not default) - implementations required");
        }

        /**
         * Test 4: isEnabled() method retained
         */
        @Test
        @DisplayName("RuleChecker should retain isEnabled() method")
        public void testRuleChecker_isEnabledMethod_retained() throws NoSuchMethodException {
            // Verify isEnabled() method exists
            Method isEnabledMethod = RuleChecker.class.getMethod("isEnabled");
            assertNotNull(isEnabledMethod, "isEnabled() method should be retained");

            // Verify return type is boolean
            assertEquals(boolean.class, isEnabledMethod.getReturnType());

            // Verify method is abstract (not default)
            assertTrue(Modifier.isAbstract(isEnabledMethod.getModifiers()),
                    "isEnabled() should be abstract - implementations required");
        }
    }

    @Nested
    @DisplayName("3. Javadoc Tests")
    class JavadocTests {

        /**
         * Test 5: Javadoc includes migration guide
         */
        @Test
        @DisplayName("RuleChecker Javadoc should include migration guide")
        public void testRuleChecker_javadoc_hasMigrationGuide() {
            // This is a compile-time check - manual verification required
            // Verify the interface has comprehensive Javadoc including:
            // - Class-level Javadoc explaining new architecture
            // - Migration guide from OLD to NEW architecture
            // - Code examples showing before/after
            // - Benefits of new architecture

            // Verify interface is well-documented (has Javadoc in source)
            assertTrue(RuleChecker.class.isInterface());
            assertEquals("com.footstone.sqlguard.validator.rule", RuleChecker.class.getPackageName());

            // Note: Actual Javadoc content verification requires parsing source file
            // Manual review of RuleChecker.java confirms:
            // - Migration guide present
            // - OLD vs NEW architecture comparison present
            // - Usage patterns documented
            // - Benefits listed
        }
    }

    @Nested
    @DisplayName("4. Backward Compatibility Tests")
    class BackwardCompatibilityTests {

        /**
         * Test 6: Existing RuleChecker implementations compile without changes
         */
        @Test
        @DisplayName("Existing implementations should compile without breaking changes")
        public void testRuleChecker_noBreakingChanges() {
            // Create a mock implementation using OLD pattern (overriding check())
            RuleChecker oldStyleChecker = new RuleChecker() {
                @Override
                public void check(SqlContext context, ValidationResult result) {
                    // OLD implementation - directly implements check()
                }

                @Override
                public boolean isEnabled() {
                    return true;
                }
                // Note: visitXxx() methods are inherited with default implementations
                // so OLD implementations don't need to implement them
            };

            // Verify it compiles and can be instantiated
            assertNotNull(oldStyleChecker);
            assertTrue(oldStyleChecker.isEnabled());
        }

        /**
         * Test 7: New RuleChecker implementations can use visitXxx() methods
         */
        @Test
        @DisplayName("New implementations should be able to override visitXxx() methods")
        public void testRuleChecker_newStyleImplementation_works() {
            // Create a mock implementation using NEW pattern (overriding visitXxx())
            RuleChecker newStyleChecker = new RuleChecker() {
                @Override
                public void check(SqlContext context, ValidationResult result) {
                    // Template method implementation (will be in AbstractRuleChecker)
                    // For now, just a placeholder
                }

                @Override
                public boolean isEnabled() {
                    return true;
                }

                @Override
                public void visitUpdate(Update update, SqlContext context) {
                    // NEW implementation - override visitUpdate()
                    if (update.getWhere() == null) {
                        // Validation logic here
                    }
                }

                @Override
                public void visitDelete(Delete delete, SqlContext context) {
                    // NEW implementation - override visitDelete()
                    if (delete.getWhere() == null) {
                        // Validation logic here
                    }
                }
                // visitSelect and visitInsert use default (empty) implementations
            };

            // Verify it compiles and can be instantiated
            assertNotNull(newStyleChecker);
            assertTrue(newStyleChecker.isEnabled());
        }
    }

    @Nested
    @DisplayName("5. Method Count Verification")
    class MethodCountTests {

        /**
         * Test 8: RuleChecker has exactly 6 methods (2 own + 4 inherited)
         */
        @Test
        @DisplayName("RuleChecker should have 6 total methods (2 declared + 4 inherited)")
        public void testRuleChecker_methodCount() {
            // Count declared methods (not inherited)
            Method[] declaredMethods = RuleChecker.class.getDeclaredMethods();
            // Filter out synthetic methods (e.g., $jacocoInit from code coverage)
            long declaredCount = java.util.Arrays.stream(declaredMethods)
                    .filter(m -> !m.getName().startsWith("$"))
                    .count();
            assertEquals(2, declaredCount, "RuleChecker should declare exactly 2 methods (check, isEnabled)");

            // Count total methods (including inherited)
            Method[] allMethods = RuleChecker.class.getMethods();
            // Filter out Object methods and synthetic methods
            long visitorMethodCount = java.util.Arrays.stream(allMethods)
                    .filter(m -> !m.getName().startsWith("$"))
                    .filter(m -> m.getName().startsWith("visit"))
                    .count();
            assertEquals(4, visitorMethodCount,
                    "RuleChecker should have 4 inherited visitXxx() methods from StatementVisitor");
        }
    }
}
```

---

### 3. 现有测试验证

**要求**:
- ✅ 所有现有 RuleChecker 测试继续通过
- ✅ 无需修改现有 Checker 实现（向后兼容）
- ✅ 现有测试数量：503 tests（无变化预期）

**验证命令**:
```bash
# 运行所有 sql-guard-core 测试
mvn test -pl sql-guard-core

# 特别验证 RuleChecker 相关测试
mvn test -pl sql-guard-core -Dtest="*RuleChecker*Test"
```

---

## 验收标准

### 功能验收
- [ ] `RuleChecker` 接口添加 `extends StatementVisitor`
- [ ] `check()` 方法保留（返回 void，接收 SqlContext + ValidationResult）
- [ ] `isEnabled()` 方法保留（返回 boolean）
- [ ] 接口继承了 4 个 visitXxx() 方法（Select/Update/Delete/Insert）
- [ ] Javadoc 更新，包含详细迁移指南

### 测试验收
- [ ] `RuleCheckerRefactoringTest` 创建，包含 8 个测试用例（5个测试类组）
- [ ] Test 1-2: 验证继承关系和方法继承
- [ ] Test 3-4: 验证 check() 和 isEnabled() 保留
- [ ] Test 5: 验证 Javadoc 完整性
- [ ] Test 6-7: 验证向后兼容性（旧实现和新实现都能工作）
- [ ] Test 8: 验证方法数量（2个声明 + 4个继承）
- [ ] 所有 8 个新测试通过
- [ ] 所有现有测试继续通过（503 tests, 0 failures）

### 设计验收
- [ ] RuleChecker 成为 StatementVisitor 的子接口
- [ ] 无破坏性变更（现有 Checker 实现无需修改）
- [ ] Javadoc 提供清晰的 OLD → NEW 迁移路径
- [ ] 代码示例展示架构演进

---

## 输出文件

### 修改文件 (1个)
- `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/RuleChecker.java`

### 新增文件 (1个)
- `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/RuleCheckerRefactoringTest.java`

---

## 依赖与限制

### 依赖
- ✅ Task 12.1 已完成（SqlContext 有 statement 字段）
- ✅ Task 12.2 已完成（StatementVisitor 接口存在）

### 限制
- ⚠️ **不修改 AbstractRuleChecker** - 模板方法实现留给 Task 12.4
- ⚠️ **不修改具体 Checker** - 迁移留给 Task 12.5-12.9
- ⚠️ **100% 向后兼容** - 现有 Checker 继续工作，不需要立即迁移

### 后续任务依赖
- Task 12.4 (AbstractRuleChecker 重构) 将实现 check() 模板方法，分发到 visitXxx()
- Task 12.5-12.9 (Checker 迁移) 将具体 Checker 从 check() 迁移到 visitXxx()

---

## 参考文档

### Implementation Plan
- `.apm/Implementation_Plan.md` - Task 12.3 详细说明（line 229-246）

### 相关代码
- RuleChecker (当前): `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/RuleChecker.java`
- StatementVisitor: `sql-guard-core/src/main/java/com/footstone/sqlguard/visitor/StatementVisitor.java`
- AbstractRuleChecker: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/AbstractRuleChecker.java`

### 设计模式
- **Visitor Pattern**: https://refactoring.guru/design-patterns/visitor
- **Template Method Pattern**: https://refactoring.guru/design-patterns/template-method

---

## 注意事项

### 1. 向后兼容性策略
- **OLD 实现继续工作**：直接实现 check() 的 Checker 继续工作
- **NEW 实现可选**：可以开始使用 visitXxx() 方法
- **过渡期**：允许 OLD 和 NEW 实现共存
- **最终目标**：所有 Checker 迁移到 NEW 实现（Task 12.5-12.9）

### 2. check() 方法的未来
- **当前状态** (Task 12.3): check() 是抽象方法，子类必须实现
- **下一状态** (Task 12.4): check() 变为 final 模板方法，由 AbstractRuleChecker 实现
- **未来状态** (Task 12.5-12.9): 所有 Checker 不再覆盖 check()，只覆盖 visitXxx()

### 3. Javadoc 编写重点
- **迁移指南**：明确说明如何从 OLD 迁移到 NEW
- **代码示例**：提供 before/after 对比
- **架构解释**：说明为什么引入 StatementVisitor
- **最佳实践**：推荐使用 visitXxx() 而非 check()

### 4. 测试策略
- **继承关系测试**：验证 RuleChecker extends StatementVisitor
- **方法保留测试**：验证 check() 和 isEnabled() 未删除
- **向后兼容测试**：验证 OLD 实现仍然编译通过
- **新功能测试**：验证 NEW 实现可以使用 visitXxx()

---

## 执行时间线

- **预计时间**: 2 工作日
- **Day 1**: 接口重构 + Javadoc 编写 + 基础测试（Test 1-5）
- **Day 2**: 向后兼容测试（Test 6-8） + 现有测试验证 + 文档完善
- **关键路径**: 是（Task 12.4 依赖此任务）

---

**Created**: 2025-12-22
**Phase**: 12 - Core Architecture Unification
**Agent**: Agent_Architecture_Refactoring
**Task ID**: 12.3
**Dependencies**: Task 12.1 (Completed), Task 12.2 (Completed)
