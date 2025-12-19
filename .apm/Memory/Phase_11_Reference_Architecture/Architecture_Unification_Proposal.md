# 架构统一方案：解决 Checker Core 与拦截器割裂问题

**创建时间**: 2025-12-19
**问题**: Checker Core、Druid 实现、MyBatis 实现之间存在架构割裂

---

## 一、当前架构问题分析

### 1.1 现状梳理

#### Checker Core 层（sql-guard-core）

```java
// RuleChecker 接口
public interface RuleChecker {
    void check(SqlContext context, ValidationResult result);
    boolean isEnabled();
}

// SqlContext（当前）
public class SqlContext {
    private String sql;                    // ✅ 有原始 SQL
    private SqlCommandType type;
    private String mapperId;
    private RowBounds rowBounds;
    private Map<String, Object> params;
    // ❌ 缺少：预解析的 Statement
}
```

**问题**：
- ❌ 每个 Checker 内部都要自己解析 SQL（重复解析）
- ❌ SqlContext 不包含预解析的 AST

#### MyBatis 拦截器层（sql-guard-mybatis）

```java
@Override
public Object intercept(Invocation invocation) throws Throwable {
    BoundSql boundSql = ms.getBoundSql(parameter);
    String sql = boundSql.getSql();

    // 构建 SqlContext（只有 SQL 字符串）
    SqlContext context = buildSqlContext(ms, sql, parameter, rowBounds);

    // 验证（Validator 内部会重复解析）
    ValidationResult result = validator.validate(context);

    // ...
}
```

**问题**：
- ❌ SqlSafetyInterceptor 没有解析 SQL
- ❌ 直接把 SQL 字符串传给 Validator
- ❌ Validator → RuleCheckerOrchestrator → 每个 Checker 都要解析

#### 新架构 InnerInterceptor（计划实现）

```java
public class SelectLimitInnerInterceptor extends JsqlParserSupport {
    @Override
    public void beforeQuery(...) {
        String sql = boundSql.getSql();

        // 又要解析一次
        Statement statement = JsqlParserGlobal.parse(sql);

        // ...
    }
}
```

**问题**：
- ❌ 又要解析一次 SQL
- ❌ 无法复用其他拦截器的解析结果

### 1.2 问题根源

```
问题1：SQL 解析重复
┌────────────────────────────────────────────┐
│  MyBatis Interceptor                       │
│  - 不解析，直接传 SQL 字符串                │
│    ↓                                        │
│  SqlSafetyValidator                        │
│  - 不解析，传给 Orchestrator                │
│    ↓                                        │
│  RuleCheckerOrchestrator                   │
│  - 不解析，传给各个 Checker                 │
│    ↓                                        │
│  NoWhereClauseChecker  (解析1)             │
│  BlacklistFieldChecker (解析2)             │
│  NoPaginationChecker   (解析3)             │
│  ...                                        │
└────────────────────────────────────────────┘

问题2：InnerInterceptor 无法复用
┌────────────────────────────────────────────┐
│  SqlGuardCheckInnerInterceptor  (解析4)    │
│  SelectLimitInnerInterceptor    (解析5)    │
└────────────────────────────────────────────┘

同一个 SQL，解析了 5 次！
```

---

## 二、第一性原理分析

### 2.1 SQL 安全检查的本质流程

```
1. 输入：SQL 字符串
   ↓
2. 解析：SQL → AST（Statement）
   ↓
3. 分析：遍历 AST，应用规则
   ↓
4. 决策：PASS / WARN / BLOCK / REWRITE
   ↓
5. 执行：记录日志 / 抛异常 / 改写 SQL
```

**核心洞察**：
- **解析（Parse）只需要一次**
- **多个规则可以共享同一个 AST**
- **检测和兜底都基于同一个 AST**

### 2.2 最优架构应该是什么？

参考 MyBatis-Plus 的 JsqlParserSupport 模式：

```java
// MyBatis-Plus 的模式
public abstract class JsqlParserSupport {
    // 解析一次
    protected String parserSingle(String sql, Object obj) {
        Statement statement = JsqlParserGlobal.parse(sql);
        return processParser(statement, 0, sql, obj);
    }

    // 子类实现处理逻辑
    protected abstract void processSelect(Select select, ...);
    protected abstract void processUpdate(Update update, ...);
}

// 具体拦截器
public class PaginationInnerInterceptor extends JsqlParserSupport {
    @Override
    protected void processSelect(Select select, ...) {
        // 直接使用 select，不需要重新解析
    }
}
```

**优点**：
- ✅ 解析一次，多次使用
- ✅ 统一的解析入口
- ✅ 清晰的职责分离

---

## 三、统一架构方案

### 3.1 核心设计：三层架构

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: SQL Parsing (统一解析层)                          │
│  - ParsedSqlContext：封装 SQL + Statement                   │
│  - SqlContextFactory：统一解析入口                           │
│  - ThreadLocal：在拦截器链中共享                             │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  Layer 2: Rule Checking (规则检测层)                        │
│  - RuleChecker：接收 ParsedSqlContext                       │
│  - AbstractRuleChecker：继承 JsqlParserSupport              │
│  - 各种 Checker：在 processXxx 中实现检测逻辑               │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  Layer 3: Interception (拦截执行层)                         │
│  - SqlGuardInnerInterceptor：协调 Checker 和 Fallback       │
│  - CheckerInnerInterceptor：适配 Checker                    │
│  - FallbackInnerInterceptor：SQL 改写                       │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 具体实现

#### Step 1: 增强 SqlContext

```java
/**
 * 增强的 SqlContext，包含预解析的 Statement
 */
public class SqlContext {
    private String sql;
    private SqlCommandType type;
    private String mapperId;
    private RowBounds rowBounds;
    private Map<String, Object> params;

    // ✅ 新增：预解析的 Statement
    private Statement parsedStatement;

    // ✅ 新增：懒加载解析（首次访问时解析）
    public Statement getParsedStatement() {
        if (parsedStatement == null && sql != null) {
            try {
                parsedStatement = JsqlParserGlobal.parse(sql);
            } catch (JSQLParserException e) {
                // 解析失败，返回 null（Checker 需要处理 null）
                logger.warn("Failed to parse SQL: {}", sql, e);
            }
        }
        return parsedStatement;
    }

    // ✅ 新增：预设已解析的 Statement（避免重复解析）
    public void setParsedStatement(Statement statement) {
        this.parsedStatement = statement;
    }
}
```

**优点**：
- ✅ 向后兼容：不破坏现有代码
- ✅ 懒加载：只在需要时解析
- ✅ 可预设：拦截器可以预先解析并设置

#### Step 2: 升级 RuleChecker 接口

```java
/**
 * 升级的 RuleChecker 接口
 */
public interface RuleChecker {
    /**
     * 检查 SQL（新接口）
     * @param context 包含预解析 Statement 的上下文
     * @param result 验证结果
     */
    void check(SqlContext context, ValidationResult result);

    boolean isEnabled();
}
```

**不需要改接口签名**！
- SqlContext 已经包含了 `getParsedStatement()`
- Checker 可以通过 `context.getParsedStatement()` 获取
- 完全向后兼容

#### Step 3: 重构 AbstractRuleChecker

```java
/**
 * 重构后的 AbstractRuleChecker
 * 参考 MyBatis-Plus JsqlParserSupport
 */
public abstract class AbstractRuleChecker implements RuleChecker {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public final void check(SqlContext context, ValidationResult result) {
        // 1. 获取预解析的 Statement（如果拦截器已解析）
        Statement statement = context.getParsedStatement();

        // 2. 如果解析失败，跳过检查（降级）
        if (statement == null) {
            logger.warn("Cannot check: SQL parsing failed for {}", context.getMapperId());
            return;
        }

        // 3. 根据 Statement 类型分发
        try {
            if (statement instanceof Select) {
                checkSelect((Select) statement, context, result);
            } else if (statement instanceof Update) {
                checkUpdate((Update) statement, context, result);
            } else if (statement instanceof Delete) {
                checkDelete((Delete) statement, context, result);
            } else if (statement instanceof Insert) {
                checkInsert((Insert) statement, context, result);
            }
        } catch (Exception e) {
            logger.error("Error checking SQL: {}", context.getSql(), e);
            // 检查失败，不影响执行（降级）
        }
    }

    // 子类实现这些方法（模板方法模式）
    protected void checkSelect(Select select, SqlContext context, ValidationResult result) {
        // 默认不检查 SELECT
    }

    protected void checkUpdate(Update update, SqlContext context, ValidationResult result) {
        // 默认不检查 UPDATE
    }

    protected void checkDelete(Delete delete, SqlContext context, ValidationResult result) {
        // 默认不检查 DELETE
    }

    protected void checkInsert(Insert insert, SqlContext context, ValidationResult result) {
        // 默认不检查 INSERT
    }

    // 保留原有的工具方法
    protected Expression extractWhere(Statement stmt) { ... }
    protected String extractTableName(Statement stmt) { ... }
    protected Set<String> extractFields(Expression expr) { ... }
}
```

#### Step 4: 迁移现有 Checker

**示例：NoWhereClauseChecker**

```java
/**
 * 迁移后的 NoWhereClauseChecker
 */
public class NoWhereClauseChecker extends AbstractRuleChecker {

    private final NoWhereClauseConfig config;

    @Override
    protected void checkUpdate(Update update, SqlContext context, ValidationResult result) {
        Expression where = update.getWhere();
        String tableName = update.getTable().getName();

        if (where == null && !config.getExcludedTables().contains(tableName)) {
            result.addViolation(
                RiskLevel.HIGH,
                "UPDATE without WHERE clause on table: " + tableName,
                "Add WHERE condition to limit update scope"
            );
        }
    }

    @Override
    protected void checkDelete(Delete delete, SqlContext context, ValidationResult result) {
        Expression where = delete.getWhere();
        String tableName = delete.getTable().getName();

        if (where == null && !config.getExcludedTables().contains(tableName)) {
            result.addViolation(
                RiskLevel.HIGH,
                "DELETE without WHERE clause on table: " + tableName,
                "Add WHERE condition to limit delete scope"
            );
        }
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }
}
```

**对比旧实现**：
```java
// 旧实现（需要自己解析）
public class NoWhereClauseChecker extends AbstractRuleChecker {
    @Override
    public void check(SqlContext context, ValidationResult result) {
        // ❌ 自己解析
        Statement stmt = JSqlParser.parse(context.getSql());
        Expression where = extractWhere(stmt);
        // ...
    }
}

// 新实现（使用预解析的 Statement）
public class NoWhereClauseChecker extends AbstractRuleChecker {
    @Override
    protected void checkUpdate(Update update, SqlContext context, ValidationResult result) {
        // ✅ 直接使用 update，不需要解析
        Expression where = update.getWhere();
        // ...
    }
}
```

#### Step 5: 升级 MyBatis 拦截器

```java
/**
 * 升级后的 SqlSafetyInterceptor
 */
@Intercepts({...})
public class SqlSafetyInterceptor implements Interceptor {

    private final SqlSafetyValidator validator;
    private final ViolationStrategy strategy;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        BoundSql boundSql = ms.getBoundSql(parameter);
        String sql = boundSql.getSql();

        // ✅ 1. 预先解析 SQL
        Statement parsedStatement = null;
        try {
            parsedStatement = JsqlParserGlobal.parse(sql);
        } catch (JSQLParserException e) {
            logger.warn("Failed to parse SQL: {}", sql, e);
            // 解析失败，继续执行（降级）
        }

        // ✅ 2. 构建 SqlContext，包含预解析的 Statement
        SqlContext context = buildSqlContext(ms, sql, parameter, rowBounds);
        context.setParsedStatement(parsedStatement);

        // ✅ 3. 验证（Checker 不需要重新解析）
        ValidationResult result = validator.validate(context);

        // 4. 处理违规
        if (!result.isPassed()) {
            handleViolation(result, ms.getId());
        }

        return invocation.proceed();
    }
}
```

**关键改进**：
- ✅ 拦截器层解析一次
- ✅ 所有 Checker 共享解析结果
- ✅ 解析失败时降级（不影响执行）

#### Step 6: InnerInterceptor 复用解析结果

```java
/**
 * CheckerInnerInterceptor - 桥接 Checker 和 InnerInterceptor
 */
public class SqlGuardCheckInnerInterceptor implements InnerInterceptor {

    private final List<RuleChecker> checkers;
    private final ViolationStrategy strategy;

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms,
                           Object parameter, RowBounds rowBounds,
                           ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {

        String sql = boundSql.getSql();

        // ✅ 1. 解析 SQL（只解析一次）
        Statement statement = null;
        try {
            statement = JsqlParserGlobal.parse(sql);
        } catch (JSQLParserException e) {
            logger.warn("Failed to parse SQL: {}", sql, e);
            return; // 解析失败，跳过检查
        }

        // ✅ 2. 构建 SqlContext，包含预解析的 Statement
        SqlContext context = SqlContext.builder()
            .sql(sql)
            .type(SqlCommandType.SELECT)
            .mapperId(ms.getId())
            .rowBounds(rowBounds)
            .parsedStatement(statement)  // ✅ 预设
            .build();

        // ✅ 3. 验证（所有 Checker 共享 statement）
        ValidationResult result = new ValidationResult();
        for (RuleChecker checker : checkers) {
            if (checker.isEnabled()) {
                checker.check(context, result);
            }
        }

        // ✅ 4. 处理违规
        if (!result.isPassed()) {
            handleViolation(result, strategy);
        }
    }

    @Override
    public int getPriority() {
        return 10; // 检测类拦截器优先级较高
    }
}
```

```java
/**
 * SelectLimitInnerInterceptor - 兜底拦截器
 */
public class SelectLimitInnerInterceptor extends JsqlParserSupport
        implements InnerInterceptor {

    private Long defaultLimit = 1000L;

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms,
                           Object parameter, RowBounds rowBounds,
                           ResultHandler resultHandler, BoundSql boundSql) {

        String sql = boundSql.getSql();

        // ✅ 使用 JsqlParserSupport 的解析能力
        String newSql = parserSingle(sql, null);

        // 替换 SQL
        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        mpBs.sql(newSql);
    }

    @Override
    protected String processSelect(Select select, int index, String sql, Object obj) {
        // ✅ 检测是否有分页
        if (hasPagination(select)) {
            return sql; // 已有分页，不处理
        }

        // ✅ 添加 LIMIT
        PlainSelect plainSelect = (PlainSelect) select;
        Limit limit = new Limit();
        limit.setRowCount(new LongValue(defaultLimit));
        plainSelect.setLimit(limit);

        return select.toString();
    }

    @Override
    public int getPriority() {
        return 100; // 兜底类拦截器优先级较低
    }
}
```

---

## 四、迁移路线图

### Phase 1: 增强 SqlContext（1 天）

- [ ] 添加 `parsedStatement` 字段
- [ ] 添加 `getParsedStatement()` 懒加载方法
- [ ] 添加 `setParsedStatement()` 预设方法
- [ ] 单元测试

### Phase 2: 重构 AbstractRuleChecker（2 天）

- [ ] 实现模板方法模式（checkSelect/checkUpdate/checkDelete）
- [ ] 在 `check()` 中统一处理 Statement 分发
- [ ] 保留工具方法向后兼容
- [ ] 单元测试

### Phase 3: 迁移现有 Checker（3 天）

- [ ] NoWhereClauseChecker
- [ ] BlacklistFieldChecker
- [ ] WhitelistFieldChecker
- [ ] NoPaginationChecker
- [ ] 其他 Checker
- [ ] 集成测试

### Phase 4: 升级 MyBatis 拦截器（1 天）

- [ ] SqlSafetyInterceptor 添加预解析逻辑
- [ ] SqlAuditInterceptor 添加预解析逻辑
- [ ] 集成测试

### Phase 5: 实现 InnerInterceptor 桥接（2 天）

- [ ] SqlGuardCheckInnerInterceptor
- [ ] 与 SelectLimitInnerInterceptor 集成
- [ ] 验证解析只执行一次
- [ ] 性能测试

---

## 五、方案优势

### 5.1 解决的问题

| 问题 | 现状 | 方案后 |
|-----|------|--------|
| **SQL 解析次数** | 每个 Checker 解析一次（N 次） | 拦截器解析一次（1 次） |
| **架构割裂** | Checker、Druid、MyBatis 三套体系 | 统一的 SqlContext + Statement |
| **代码复用** | 无法复用解析结果 | 所有组件共享 Statement |
| **性能开销** | 重复解析，开销大 | 单次解析，开销小 |

### 5.2 架构优势

1. **清晰分层**：
   - 解析层：SqlContext（统一）
   - 检测层：RuleChecker（纯逻辑）
   - 拦截层：InnerInterceptor（协调）

2. **向后兼容**：
   - SqlContext 新增字段不破坏现有代码
   - RuleChecker 接口不变
   - 现有 Checker 逐步迁移

3. **性能优化**：
   - SQL 只解析一次
   - 多个 Checker 共享 AST
   - 降级机制保证可用性

4. **可扩展性**：
   - 新 Checker 继承 AbstractRuleChecker
   - 实现 checkSelect/checkUpdate 即可
   - 不需要关心解析

### 5.3 与 MyBatis-Plus 对齐

```
MyBatis-Plus 模式：
  JsqlParserSupport（解析一次）
    ↓
  processSelect/processUpdate（处理多次）

SQL Guard 模式：
  SqlContext（解析一次）
    ↓
  checkSelect/checkUpdate（检查多次）
```

完全一致的设计模式！

---

## 六、示例对比

### 6.1 旧架构（重复解析）

```java
// MyBatis 拦截器
SqlContext context = new SqlContext(sql, type, mapperId);
validator.validate(context);  // 传递 SQL 字符串

// Validator
for (RuleChecker checker : checkers) {
    checker.check(context, result);
}

// NoWhereClauseChecker
Statement stmt = JSqlParser.parse(context.getSql());  // 解析1
Expression where = extractWhere(stmt);

// BlacklistFieldChecker
Statement stmt = JSqlParser.parse(context.getSql());  // 解析2
Set<String> fields = extractFields(stmt);

// SelectLimitInnerInterceptor
Statement stmt = JSqlParser.parse(sql);  // 解析3
```

**问题**：同一个 SQL 解析了 3+ 次！

### 6.2 新架构（解析一次）

```java
// MyBatis 拦截器
Statement stmt = JSqlParser.parse(sql);  // ✅ 解析1次
SqlContext context = new SqlContext(sql, type, mapperId);
context.setParsedStatement(stmt);  // ✅ 预设
validator.validate(context);

// Validator
for (RuleChecker checker : checkers) {
    checker.check(context, result);
}

// NoWhereClauseChecker
Statement stmt = context.getParsedStatement();  // ✅ 复用
Expression where = extractWhere(stmt);

// BlacklistFieldChecker
Statement stmt = context.getParsedStatement();  // ✅ 复用
Set<String> fields = extractFields(stmt);

// SelectLimitInnerInterceptor
// 通过 JsqlParserSupport 的 parserSingle 处理
```

**优势**：解析只执行一次！

---

## 七、总结

### 核心思想

**统一解析，共享 AST**

- 在拦截器层解析一次
- 通过 SqlContext 传递 Statement
- 所有 Checker 和 InnerInterceptor 复用

### 关键设计

1. **SqlContext 增强**：添加 `parsedStatement` 字段
2. **AbstractRuleChecker 重构**：模板方法模式
3. **拦截器预解析**：SqlSafetyInterceptor 预先解析
4. **InnerInterceptor 桥接**：CheckerInnerInterceptor 适配

### 优势

- ✅ 性能优化：解析次数从 N 次降到 1 次
- ✅ 架构统一：Checker 和 InnerInterceptor 共享基础设施
- ✅ 向后兼容：不破坏现有代码
- ✅ 对齐业界：与 MyBatis-Plus 模式一致

---

**文档版本**: v1.0
**最后更新**: 2025-12-19
