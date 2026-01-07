# Druid Wall 复用 & 测试迁移方案

**创建时间**: 2025-12-19

---

## 一、Druid Wall 复用策略

### 1.1 现状分析

#### 当前架构

```java
// Druid Filter（当前）
public class DruidSqlSafetyFilter extends FilterAdapter {
    private final DefaultSqlSafetyValidator validator;  // ✅ 用的是我们的 Validator

    @Override
    public PreparedStatementProxy connection_prepareStatement(...) {
        validateSql(sql, connection);  // ✅ 调用我们的验证逻辑
        return super.connection_prepareStatement(...);
    }
}
```

**关键发现**：
- ✅ Druid 只提供拦截点（connection_prepareStatement, statement_executeQuery 等）
- ✅ 实际验证逻辑使用的是我们的 `DefaultSqlSafetyValidator`
- ✅ Druid Wall 的检查规则**我们没有用**

#### Druid Wall 提供的功能

```
com.alibaba.druid.wall.WallFilter:
- WallConfig: 52 个配置项
- WallVisitor: SQL 访问者（基于 Druid SQL Parser）
- WallProvider: 规则检测器
- Violation: 违规记录
```

**问题**：
- ❌ Druid Wall 使用 Druid SQL Parser，我们用 JSqlParser
- ❌ AST 结构不同，无法直接复用检测逻辑
- ❌ Druid Wall 的规则太严格（IllegalSQLInnerInterceptor 被 MyBatis-Plus 废弃就是因为太严格）

### 1.2 复用方案

#### 方案 1：保持现状（推荐）✅

```
┌────────────────────────────────────────┐
│  JDBC 层（sql-guard-jdbc-druid）        │
│  - DruidSqlSafetyFilter                │
│    └─ 只用 Druid 的拦截点              │
│    └─ 验证用我们的 Validator           │
└────────────────────────────────────────┘
            ↓
┌────────────────────────────────────────┐
│  MyBatis 层（sql-guard-mybatis）        │
│  - SqlGuardInterceptor                 │
│    └─ 用 MyBatis-Plus 风格拦截器       │
│    └─ 验证用我们的 Validator           │
└────────────────────────────────────────┘
            ↓
┌────────────────────────────────────────┐
│  核心层（sql-guard-core）               │
│  - DefaultSqlSafetyValidator           │
│  - RuleChecker（基于 JSqlParser）      │
└────────────────────────────────────────┘
```

**优势**：
- ✅ Druid 提供 JDBC 层拦截能力（覆盖裸 JDBC、JdbcTemplate）
- ✅ 我们的 Validator 统一检测逻辑
- ✅ 不依赖 Druid Wall 的规则
- ✅ 清晰的职责分离

**Druid 的价值**：
1. **Filter 拦截点**：connection_prepareStatement, statement_execute
2. **多数据源支持**：DataSourceProxy.getName()
3. **性能统计**：与 StatFilter 集成
4. **线程池管理**：连接池级别的拦截

#### 方案 2：学习 Druid Wall 规则（可选）

```java
/**
 * 参考 Druid WallConfig 的配置项，补充我们的规则
 */
public class SqlGuardConfig {
    // ✅ 已有
    private boolean selectIntoOutfileAllow = false;

    // 🆕 借鉴 Druid Wall
    private boolean selectUnionCheck = true;        // 检测 UNION
    private boolean selectHavingAlwayTrueCheck = true;  // HAVING 1=1
    private boolean deleteAllow = true;              // 是否允许 DELETE
    private boolean updateAllow = true;              // 是否允许 UPDATE
    private boolean dropTableAllow = false;          // 是否允许 DROP TABLE
    private boolean truncateAllow = false;           // 是否允许 TRUNCATE
}
```

**实现新 Checker**：
```java
/**
 * 参考 Druid Wall，禁止危险操作
 */
public class DangerousOperationChecker extends AbstractRuleChecker {

    @Override
    public void visitDelete(Delete delete, SqlContext context) {
        if (!config.isDeleteAllow()) {
            result.addViolation(
                RiskLevel.CRITICAL,
                "DELETE operation is not allowed",
                "Use UPDATE to mark as deleted instead"
            );
        }
    }

    @Override
    public void visitSelect(Select select, SqlContext context) {
        // 检测 SELECT INTO OUTFILE
        if (containsIntoOutfile(select) && !config.isSelectIntoOutfileAllow()) {
            result.addViolation(
                RiskLevel.CRITICAL,
                "SELECT INTO OUTFILE is not allowed",
                "Remove INTO OUTFILE clause"
            );
        }
    }
}
```

---

## 二、测试迁移方案

### 2.1 现有测试结构分析

#### 测试数量统计

```
sql-guard-core/src/test/:
- RuleChecker 测试: 10 个
- Config 测试: 13 个
- Parser 测试: 7 个
- Validator 测试: 4 个
- Integration 测试: 5 个
- 其他: 7 个

总计: 46 个测试文件
```

#### 测试模式

```java
// 当前测试模式
@Test
void testDeleteWithoutWhere_shouldViolate() throws Exception {
    String sql = "DELETE FROM user";

    // ✅ 已经在传 parsedSql
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))  // ✅ 测试已经在用
        .type(SqlCommandType.DELETE)
        .mapperId("com.example.UserMapper.deleteAll")
        .build();

    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
}
```

**关键发现**：
- ✅ **测试已经在传 `parsedSql`**
- ✅ 使用 `JSqlParserFacade` 解析
- ✅ 测试用例结构完整

### 2.2 迁移方案

#### Step 1: SqlContext 字段重命名（0 破坏）

```java
// 旧字段名
public class SqlContext {
    private Statement parsedSql;  // ❌ 名字不准确
}

// 新字段名
public class SqlContext {
    @NonNull
    private Statement statement;  // ✅ 更准确

    // 兼容方法（过渡期）
    @Deprecated
    public Statement getParsedSql() {
        return statement;
    }
}
```

**测试修改**：
```bash
# 全局替换
find . -name "*.java" -type f -exec sed -i '' 's/parsedSql/statement/g' {} \;
```

#### Step 2: RuleChecker 接口重构

**旧接口**（测试在用）：
```java
public interface RuleChecker {
    void check(SqlContext context, ValidationResult result);
}

// 测试代码
checker.check(context, result);
```

**新接口**：
```java
public interface RuleChecker extends StatementVisitor {
    void check(SqlContext context, ValidationResult result);  // 保留

    // 新增
    void visitSelect(Select select, SqlContext context);
    void visitUpdate(Update update, SqlContext context);
    void visitDelete(Delete delete, SqlContext context);
}

// AbstractRuleChecker 实现 check() → 分发到 visitXxx()
```

**测试无需修改**：
```java
// ✅ 测试代码不变
checker.check(context, result);

// 内部自动分发到 visitDelete()
```

#### Step 3: AbstractRuleChecker 重构

**旧实现**：
```java
public abstract class AbstractRuleChecker implements RuleChecker {
    // 子类实现 check()
    @Override
    public void check(SqlContext context, ValidationResult result) {
        // 子类自己实现
    }
}
```

**新实现**：
```java
public abstract class AbstractRuleChecker implements RuleChecker {

    // ✅ check() 变成 final，自动分发
    @Override
    public final void check(SqlContext context, ValidationResult result) {
        Statement statement = context.getStatement();

        if (statement instanceof Select) {
            visitSelect((Select) statement, context);
        } else if (statement instanceof Update) {
            visitUpdate((Update) statement, context);
        } else if (statement instanceof Delete) {
            visitDelete((Delete) statement, context);
        }
    }

    // 子类实现这些方法
    protected void visitSelect(Select select, SqlContext context) {}
    protected void visitUpdate(Update update, SqlContext context) {}
    protected void visitDelete(Delete delete, SqlContext context) {}
}
```

#### Step 4: 迁移具体 Checker

**旧实现**（NoWhereClauseChecker）：
```java
public class NoWhereClauseChecker extends AbstractRuleChecker {
    @Override
    public void check(SqlContext context, ValidationResult result) {
        Statement stmt = context.getParsedSql();
        Expression where = extractWhere(stmt);
        String tableName = extractTableName(stmt);

        if (where == null) {
            result.addViolation(...);
        }
    }
}
```

**新实现**：
```java
public class NoWhereClauseChecker extends AbstractRuleChecker {

    private final ValidationResult result;  // ✅ 构造注入

    @Override
    protected void visitUpdate(Update update, SqlContext context) {
        Expression where = update.getWhere();
        String tableName = update.getTable().getName();

        if (where == null && !config.getExcludedTables().contains(tableName)) {
            result.addViolation(
                RiskLevel.CRITICAL,
                "UPDATE without WHERE clause on table: " + tableName,
                "Add WHERE condition"
            );
        }
    }

    @Override
    protected void visitDelete(Delete delete, SqlContext context) {
        Expression where = delete.getWhere();
        String tableName = delete.getTable().getName();

        if (where == null && !config.getExcludedTables().contains(tableName)) {
            result.addViolation(
                RiskLevel.CRITICAL,
                "DELETE without WHERE clause on table: " + tableName,
                "Add WHERE condition"
            );
        }
    }
}
```

#### Step 5: 测试适配

**测试无需大改**：
```java
@Test
void testDeleteWithoutWhere_shouldViolate() throws Exception {
    String sql = "DELETE FROM user";

    // ✅ 只改字段名
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(parser.parse(sql))  // parsedSql → statement
        .type(SqlCommandType.DELETE)
        .mapperId("com.example.UserMapper.deleteAll")
        .build();

    ValidationResult result = ValidationResult.pass();

    // ✅ 调用方式不变
    checker.check(context, result);

    // ✅ 断言不变
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
}
```

**需要修改的地方**：
1. ✅ `parsedSql` → `statement`（全局替换）
2. ✅ Checker 构造时传入 `ValidationResult`

**新增测试**：
```java
@Test
void testVisitUpdateDirectly() {
    // 新增：直接测试 visitUpdate
    String sql = "UPDATE user SET status=1";
    Update update = (Update) parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .statement(update)
        .sql(sql)
        .type(SqlCommandType.UPDATE)
        .build();

    ValidationResult result = ValidationResult.pass();
    checker.visitUpdate(update, context);

    assertTrue(result.isViolated());
}
```

### 2.3 迁移清单

#### Phase 1: SqlContext 重构（1 天）

- [ ] 重命名 `parsedSql` → `statement`
- [ ] 添加 `@NonNull` 注解
- [ ] 添加兼容方法 `getParsedSql()`（标记 @Deprecated）
- [ ] 全局替换测试代码：`parsedSql` → `statement`

#### Phase 2: AbstractRuleChecker 重构（1 天）

- [ ] 实现 `check()` 模板方法（final）
- [ ] 添加 `visitXxx()` 方法
- [ ] 删除工具方法（extractWhere, extractTableName）
- [ ] 单元测试

#### Phase 3: 迁移 Checker（3 天）

- [ ] NoWhereClauseChecker
- [ ] BlacklistFieldChecker
- [ ] WhitelistFieldChecker
- [ ] NoPaginationChecker
- [ ] DummyConditionChecker
- [ ] LogicalPaginationChecker
- [ ] 其他 Checker

#### Phase 4: 测试适配（2 天）

- [ ] 全局替换 `parsedSql` → `statement`
- [ ] 修改 Checker 构造（传入 ValidationResult）
- [ ] 新增 visitXxx() 直接测试
- [ ] 运行全量测试，确保通过

#### Phase 5: 集成测试（1 天）

- [ ] DefaultSqlSafetyValidatorTest
- [ ] RuleCheckerIntegrationTest
- [ ] SqlSafetyValidatorIntegrationTest
- [ ] 性能测试（对比解析次数）

---

## 三、迁移对比

### 3.1 代码变化

| 组件 | 旧代码 | 新代码 | 变化 |
|-----|-------|-------|------|
| **SqlContext** | parsedSql | statement | 重命名 |
| **RuleChecker** | check(context, result) | check() + visitXxx() | 新增方法 |
| **AbstractRuleChecker** | 子类实现 check() | check() final + 模板方法 | 重构 |
| **具体 Checker** | 自己解析 Statement | 直接使用 visitXxx() 参数 | 简化 |

### 3.2 测试变化

| 测试类型 | 变化程度 | 说明 |
|---------|---------|------|
| **单元测试** | 小（字段名替换） | parsedSql → statement |
| **集成测试** | 小（构造修改） | 传入 ValidationResult |
| **性能测试** | 无 | 保持不变 |
| **新增测试** | 中（visitXxx 测试） | 测试新方法 |

### 3.3 风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|-----|------|------|---------|
| **字段重命名遗漏** | 低 | 中 | 使用 IDE 重构功能 |
| **测试失败** | 中 | 高 | 逐个 Checker 迁移，增量测试 |
| **性能回退** | 低 | 高 | 性能基准测试 |
| **行为变化** | 低 | 高 | 对照旧测试结果 |

---

## 四、执行计划

### 4.1 迁移顺序

```
1. SqlContext 重构
   ↓
2. AbstractRuleChecker 重构
   ↓
3. NoWhereClauseChecker（最简单的）
   ↓ （验证模式）
4. BlacklistFieldChecker
   ↓
5. 其他 Checker（并行）
   ↓
6. 全量测试
```

### 4.2 验证标准

#### 功能验证
- [ ] 所有测试用例通过
- [ ] 新旧行为一致（对照测试结果）
- [ ] 边界情况覆盖

#### 性能验证
- [ ] SQL 解析次数：N 次 → 1 次
- [ ] 总耗时：< 旧版本 110%
- [ ] 内存占用：< 旧版本 110%

#### 代码质量
- [ ] 代码覆盖率：≥ 80%
- [ ] SonarQube 无新增问题
- [ ] Javadoc 完整

---

## 五、总结

### 关键决策

1. **Druid 复用策略**：
   - ✅ 保持现状：Druid 提供拦截点，我们的 Validator 做检测
   - ✅ 可选：学习 Druid Wall 规则，用 JSqlParser 实现

2. **测试迁移策略**：
   - ✅ 最小化改动：只改字段名和构造
   - ✅ 渐进式迁移：逐个 Checker 迁移
   - ✅ 测试驱动：每个 Checker 迁移后立即测试

3. **风险控制**：
   - ✅ 增量迁移：不是一次性重构所有代码
   - ✅ 对照测试：新旧结果对比
   - ✅ 性能基准：确保性能不退化

### 优势

| 维度 | 收益 |
|-----|------|
| **架构统一** | Checker 和 InnerInterceptor 共享 Statement |
| **性能提升** | SQL 解析次数：N 次 → 1 次 |
| **测试迁移** | 最小化改动，风险可控 |
| **Druid 价值** | 保留 JDBC 层拦截能力 |

---

**文档版本**: v1.0
**最后更新**: 2025-12-19
