---
phase: 12
task_id: 12.1
task_name: SqlContext 重构
agent: Agent_Core_Engine_Foundation
estimated_duration: 1 day
dependencies: []
parallel_with: []
---

# Task 12.1 Assignment: SqlContext 重构

## 任务目标

重构 `SqlContext` 类，添加新的 `statement` 字段以支持统一的 StatementVisitor 抽象，同时保持向后兼容性，为 Phase 12 核心架构统一奠定基础。

---

## 背景说明

### 当前问题
- **重复解析**：每个 RuleChecker 内部都调用 `JSqlParser.parse()` 重复解析 SQL
- **字段命名不一致**：`parsedSql` 字段名不直观（已解析的SQL应该是 Statement 对象）
- **缺乏统一抽象**：没有 StatementVisitor 模式，各 Checker 自行处理不同 SQL 类型

### 重构目标
- ✅ 添加 `statement` 字段作为新的权威字段
- ✅ 保留 `parsedSql` 字段，标记为 `@Deprecated`
- ✅ 添加兼容方法确保现有代码继续工作
- ✅ 100% 向后兼容，不破坏现有测试

---

## 实现要求

### 1. SqlContext 类修改

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/core/model/SqlContext.java`

**修改内容**:

```java
package com.footstone.sqlguard.core.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import net.sf.jsqlparser.statement.Statement;

@Getter
@Builder(toBuilder = true)
public class SqlContext {
    @NonNull
    private final String sql;

    @NonNull
    private final String mapperId;

    @NonNull
    private final SqlCommandType commandType;

    // ==================== NEW: 新字段 ====================
    /**
     * Parsed SQL statement (统一字段名)
     * <p>
     * This field replaces the deprecated {@link #parsedSql} field.
     * All new code should use this field.
     * </p>
     * @since 1.1.0
     */
    @NonNull
    private final Statement statement;

    // ==================== OLD: 已弃用字段 ====================
    /**
     * Parsed SQL statement (deprecated, use {@link #statement} instead)
     * @deprecated Use {@link #statement} instead. This field will be removed in version 2.0.0.
     * @since 1.0.0
     */
    @Deprecated
    private final Statement parsedSql;

    // ==================== 兼容方法 ====================
    /**
     * Get parsed SQL statement (deprecated method)
     * <p>
     * This method provides backward compatibility. It returns:
     * <ul>
     *   <li>{@link #statement} if set (new code)</li>
     *   <li>{@link #parsedSql} if set (old code)</li>
     * </ul>
     * </p>
     * @return the parsed SQL statement
     * @deprecated Use {@link #getStatement()} instead
     */
    @Deprecated
    public Statement getParsedSql() {
        return statement != null ? statement : parsedSql;
    }

    /**
     * Get parsed SQL statement (new method)
     * @return the parsed SQL statement
     * @since 1.1.0
     */
    public Statement getStatement() {
        return statement != null ? statement : parsedSql;
    }

    // ==================== Builder 兼容性 ====================
    /**
     * Custom builder to ensure backward compatibility
     */
    public static class SqlContextBuilder {
        /**
         * Set statement using deprecated field name (backward compatibility)
         * @deprecated Use {@link #statement(Statement)} instead
         */
        @Deprecated
        public SqlContextBuilder parsedSql(Statement parsedSql) {
            this.parsedSql = parsedSql;
            // Auto-sync to new field
            if (this.statement == null) {
                this.statement = parsedSql;
            }
            return this;
        }

        /**
         * Set statement using new field name (recommended)
         */
        public SqlContextBuilder statement(Statement statement) {
            this.statement = statement;
            // Auto-sync to old field for compatibility
            if (this.parsedSql == null) {
                this.parsedSql = statement;
            }
            return this;
        }
    }
}
```

**关键设计点**:
1. **双字段策略**: `statement` (新) + `parsedSql` (旧, deprecated)
2. **自动同步**: Builder 方法自动同步两个字段
3. **兼容方法**: `getParsedSql()` 优先返回 `statement`，fallback 到 `parsedSql`
4. **@NonNull 约束**: 新字段使用 `@NonNull` 确保非空（通过 Builder 自动同步保证）

### 2. 测试修改

**要求**:
- ✅ 现有所有测试必须继续通过（100% pass rate）
- ✅ 不修改现有测试代码（向后兼容验证）
- ✅ 添加新测试验证双字段同步机制

**新增测试文件**: `sql-guard-core/src/test/java/com/footstone/sqlguard/core/model/SqlContextBackwardCompatibilityTest.java`

```java
package com.footstone.sqlguard.core.model;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Backward compatibility tests for SqlContext refactoring
 */
public class SqlContextBackwardCompatibilityTest {

    @Test
    public void testOldApi_parsedSql_stillWorks() throws JSQLParserException {
        // Given: old code using parsedSql() builder method
        String sql = "SELECT * FROM users";
        Statement stmt = CCJSqlParserUtil.parse(sql);

        // When: build using deprecated parsedSql() method
        SqlContext context = SqlContext.builder()
                .sql(sql)
                .mapperId("test.mapper")
                .commandType(SqlCommandType.SELECT)
                .parsedSql(stmt)  // ✅ Old API
                .build();

        // Then: both getters work
        assertNotNull(context.getParsedSql());  // Old getter
        assertNotNull(context.getStatement());  // New getter
        assertSame(stmt, context.getParsedSql());
        assertSame(stmt, context.getStatement());
    }

    @Test
    public void testNewApi_statement_works() throws JSQLParserException {
        // Given: new code using statement() builder method
        String sql = "UPDATE users SET name = 'foo'";
        Statement stmt = CCJSqlParserUtil.parse(sql);

        // When: build using new statement() method
        SqlContext context = SqlContext.builder()
                .sql(sql)
                .mapperId("test.mapper")
                .commandType(SqlCommandType.UPDATE)
                .statement(stmt)  // ✅ New API
                .build();

        // Then: both getters work
        assertNotNull(context.getStatement());  // New getter
        assertNotNull(context.getParsedSql());  // Old getter (compatibility)
        assertSame(stmt, context.getStatement());
        assertSame(stmt, context.getParsedSql());
    }

    @Test
    public void testAutoSync_oldToNew() throws JSQLParserException {
        // Given: old code sets parsedSql
        Statement stmt = CCJSqlParserUtil.parse("DELETE FROM users");

        // When: only set parsedSql (old field)
        SqlContext context = SqlContext.builder()
                .sql("DELETE FROM users")
                .mapperId("test.mapper")
                .commandType(SqlCommandType.DELETE)
                .parsedSql(stmt)
                .build();

        // Then: statement (new field) is auto-synced
        assertSame(stmt, context.getStatement());
    }

    @Test
    public void testAutoSync_newToOld() throws JSQLParserException {
        // Given: new code sets statement
        Statement stmt = CCJSqlParserUtil.parse("INSERT INTO users VALUES (1)");

        // When: only set statement (new field)
        SqlContext context = SqlContext.builder()
                .sql("INSERT INTO users VALUES (1)")
                .mapperId("test.mapper")
                .commandType(SqlCommandType.INSERT)
                .statement(stmt)
                .build();

        // Then: parsedSql (old field) is auto-synced
        assertSame(stmt, context.getParsedSql());
    }

    @Test
    public void testPriority_statementOverParsedSql() throws JSQLParserException {
        // Given: both fields are set (edge case)
        Statement stmt1 = CCJSqlParserUtil.parse("SELECT 1");
        Statement stmt2 = CCJSqlParserUtil.parse("SELECT 2");

        // When: set both (should not happen in practice, but handle it)
        SqlContext.SqlContextBuilder builder = SqlContext.builder()
                .sql("SELECT 1")
                .mapperId("test.mapper")
                .commandType(SqlCommandType.SELECT)
                .parsedSql(stmt1)
                .statement(stmt2);  // statement() called after parsedSql()

        SqlContext context = builder.build();

        // Then: statement (new field) takes precedence
        assertSame(stmt2, context.getStatement());
        assertSame(stmt2, context.getParsedSql());  // Auto-synced
    }
}
```

### 3. 编译验证

**要求**:
- ✅ 所有模块编译成功
- ✅ 无 deprecation 警告（除了 `SqlContext.parsedSql` 字段本身）
- ✅ Lombok Builder 正常工作

**验证命令**:
```bash
mvn clean compile -DskipTests
```

### 4. 测试验证

**要求**:
- ✅ 现有所有测试通过（100% pass rate）
- ✅ 新增 5 个向后兼容测试全部通过

**验证命令**:
```bash
# 运行 sql-guard-core 模块所有测试
mvn test -pl sql-guard-core

# 运行新增的向后兼容测试
mvn test -pl sql-guard-core -Dtest=SqlContextBackwardCompatibilityTest
```

---

## 验收标准

### 功能验收
- [ ] `SqlContext` 类添加 `statement` 字段（`@NonNull`）
- [ ] `parsedSql` 字段标记为 `@Deprecated`
- [ ] `getParsedSql()` 方法正确委托到 `getStatement()`
- [ ] Builder 自动同步 `parsedSql` 和 `statement` 字段
- [ ] 5 个向后兼容测试全部通过

### 兼容性验收
- [ ] 现有所有测试继续通过（100% pass rate）
- [ ] 现有代码使用 `parsedSql()` builder 方法仍然工作
- [ ] 现有代码使用 `getParsedSql()` getter 仍然工作
- [ ] 新代码使用 `statement()` builder 方法正常工作
- [ ] 新代码使用 `getStatement()` getter 正常工作

### 编译验证
- [ ] 所有模块编译成功（无错误）
- [ ] 无 deprecation 警告（除了 `SqlContext.parsedSql` 字段）

---

## 输出文件

### 修改文件 (1个)
- `sql-guard-core/src/main/java/com/footstone/sqlguard/core/model/SqlContext.java`

### 新增文件 (1个)
- `sql-guard-core/src/test/java/com/footstone/sqlguard/core/model/SqlContextBackwardCompatibilityTest.java`

---

## 依赖与限制

### 依赖
- ✅ **无前置依赖** - 可立即开始
- ✅ Phase 11 已完成（JDBC 模块拆分完成）

### 限制
- ⚠️ **不修改现有测试** - 向后兼容性要求，所有现有测试代码保持不变
- ⚠️ **不删除旧字段** - `parsedSql` 字段保留到下个大版本（2.0.0）
- ⚠️ **不影响其他模块** - 此任务仅修改 `sql-guard-core` 模块

### 后续任务依赖
- Task 12.2 (StatementVisitor 接口设计) 依赖此任务完成

---

## 参考文档

### 架构设计
- Implementation Plan Phase 12: `.apm/Memory/Phase_11_Reference_Architecture/Implementation_Plan_Phase_11_12_13_Restructure.md`
- Section: **Task 12.1: SqlContext 重构**

### 相关代码
- Current SqlContext: `sql-guard-core/src/main/java/com/footstone/sqlguard/core/model/SqlContext.java`
- Existing Tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/core/model/ValidationResultTest.java`

---

## 注意事项

### 1. Lombok Builder 兼容性
- Lombok 的 `@Builder` 会自动生成 `SqlContextBuilder` 类
- 我们通过静态内部类扩展 Builder，添加自定义逻辑
- 确保 Lombok 版本 >= 1.18.x（支持 Builder 自定义）

### 2. 双字段同步策略
- **Builder 方法自动同步**：调用 `parsedSql()` 或 `statement()` 任一方法，都会同步另一个字段
- **优先级**：如果两个方法都调用，后调用的生效
- **Getter 优先级**：`getStatement()` 和 `getParsedSql()` 都优先返回 `statement` 字段

### 3. Migration Path (后续阶段)
- **Phase 12.1 (本任务)**：添加双字段，自动同步
- **Phase 12.5-12.9**：逐步迁移 Checker 代码，从 `parsedSql` 改为 `statement`
- **Phase 12.10**：迁移测试代码
- **Version 2.0.0**：删除 `parsedSql` 字段和 `getParsedSql()` 方法

---

## 执行时间线

- **预计时间**: 1 工作日
- **并行机会**: 无（串行任务）
- **关键路径**: 是（Phase 12 第一个任务）

---

**Created**: 2025-12-22
**Phase**: 12 - Core Architecture Unification
**Agent**: Agent_Core_Engine_Foundation
**Task ID**: 12.1
