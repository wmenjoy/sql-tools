# SQL Guard 架构统一方案（最终版 - 不考虑兼容）

**创建时间**: 2025-12-19
**原则**: 彻底重构，不考虑向后兼容

---

## 一、核心设计原则

### 1.1 第一性原理

```
SQL 安全检查 = 解析一次 + 应用多个规则

┌─────────────────────────────────────────┐
│  输入：SQL 字符串                        │
│    ↓                                     │
│  解析：SQL → Statement（只解析一次）     │
│    ↓                                     │
│  应用规则：                              │
│    - Checker 1: 检测 NoWhere            │
│    - Checker 2: 检测 Blacklist          │
│    - Checker 3: 检测 NoPagination       │
│    - Fallback 1: 添加 LIMIT             │
│    ↓                                     │
│  决策：PASS / WARN / BLOCK / REWRITE    │
└─────────────────────────────────────────┘
```

**关键洞察**：
- ✅ 解析是昂贵操作，**只能做一次**
- ✅ Statement 是不可变对象，**可以安全共享**
- ✅ Checker 和 InnerInterceptor 本质都是**对 Statement 的访问者**
- ✅ 应该**统一抽象**，而不是分离体系

### 1.2 统一架构图

```
┌──────────────────────────────────────────────────────────────┐
│  Interceptor Layer (拦截器层)                                │
│  - SqlGuardInterceptor: 解析 SQL → Statement                │
│  - 管理 InnerInterceptor 链                                  │
└──────────────────────────────────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────────────┐
│  InnerInterceptor (统一拦截器接口)                           │
│  - 基于 Statement 的访问者模式                               │
│  - 检测类：SqlGuardCheckInnerInterceptor                     │
│  - 兜底类：SelectLimitInnerInterceptor                       │
└──────────────────────────────────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────────────┐
│  StatementVisitor (Statement 访问者)                         │
│  - RuleChecker: 纯检测逻辑（不可变）                         │
│  - StatementRewriter: 改写逻辑（返回新 Statement）           │
└──────────────────────────────────────────────────────────────┘
```

---

## 二、核心接口重设计

### 2.1 SqlContext（简化版）

```java
/**
 * SQL 执行上下文（不可变）
 *
 * 核心字段：
 * - statement: 预解析的 Statement（必须，非空）
 * - sql: 原始 SQL 字符串
 * - metadata: 执行元数据
 */
@Value
@Builder
public class SqlContext {

    /**
     * 预解析的 Statement（必须非空）
     */
    @NonNull
    Statement statement;

    /**
     * 原始 SQL 字符串
     */
    @NonNull
    String sql;

    /**
     * SQL 命令类型
     */
    @NonNull
    SqlCommandType type;

    /**
     * Mapper ID（MyBatis 专用）
     */
    String mapperId;

    /**
     * RowBounds（MyBatis 专用）
     */
    RowBounds rowBounds;

    /**
     * 参数（可选）
     */
    Map<String, Object> params;
}
```

**关键变化**：
- ❌ 删除懒加载逻辑
- ✅ statement 是必须字段（@NonNull）
- ✅ 不可变对象（@Value）
- ✅ 强制在构建时就必须解析

### 2.2 StatementVisitor（新接口）

```java
/**
 * Statement 访问者接口
 *
 * 所有基于 Statement 的处理逻辑都实现这个接口
 */
public interface StatementVisitor {

    /**
     * 访问 SELECT 语句
     */
    default void visitSelect(Select select, SqlContext context) {
        // 默认不处理
    }

    /**
     * 访问 UPDATE 语句
     */
    default void visitUpdate(Update update, SqlContext context) {
        // 默认不处理
    }

    /**
     * 访问 DELETE 语句
     */
    default void visitDelete(Delete delete, SqlContext context) {
        // 默认不处理
    }

    /**
     * 访问 INSERT 语句
     */
    default void visitInsert(Insert insert, SqlContext context) {
        // 默认不处理
    }
}
```

### 2.3 RuleChecker（重新设计）

```java
/**
 * 规则检测器（纯检测，不修改）
 *
 * 实现 StatementVisitor，在 visitXxx 方法中添加 violation
 */
public interface RuleChecker extends StatementVisitor {

    /**
     * 检查 SQL（入口方法）
     *
     * @param context SQL 上下文（包含 Statement）
     * @param result 验证结果（通过引用传递）
     */
    void check(SqlContext context, ValidationResult result);

    /**
     * 是否启用
     */
    boolean isEnabled();
}
```

### 2.4 AbstractRuleChecker（简化版）

```java
/**
 * 抽象规则检测器
 *
 * 实现 Statement 分发逻辑
 */
public abstract class AbstractRuleChecker implements RuleChecker {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public final void check(SqlContext context, ValidationResult result) {
        Statement statement = context.getStatement();

        // 根据类型分发
        if (statement instanceof Select) {
            visitSelect((Select) statement, context);
        } else if (statement instanceof Update) {
            visitUpdate((Update) statement, context);
        } else if (statement instanceof Delete) {
            visitDelete((Delete) statement, context);
        } else if (statement instanceof Insert) {
            visitInsert((Insert) statement, context);
        }
    }

    // 子类实现具体的检测逻辑
    // 直接操作 result 添加 violation

    /**
     * 访问 SELECT 语句
     * 子类覆盖此方法实现检测逻辑
     */
    @Override
    public void visitSelect(Select select, SqlContext context) {
        // 默认不检测 SELECT
    }

    @Override
    public void visitUpdate(Update update, SqlContext context) {
        // 默认不检测 UPDATE
    }

    @Override
    public void visitDelete(Delete delete, SqlContext context) {
        // 默认不检测 DELETE
    }

    @Override
    public void visitInsert(Insert insert, SqlContext context) {
        // 默认不检测 INSERT
    }
}
```

**关键简化**：
- ❌ 删除 extractWhere/extractTableName 等工具方法
- ❌ 删除 FieldExtractorVisitor 内部类
- ✅ 纯粹的访问者模式
- ✅ 子类直接操作 Statement

### 2.5 StatementRewriter（新接口）

```java
/**
 * Statement 改写器
 *
 * 用于兜底逻辑，返回新的 Statement
 */
public interface StatementRewriter extends StatementVisitor {

    /**
     * 改写 Statement
     *
     * @param context SQL 上下文
     * @return 改写后的 Statement（如果不需要改写，返回 null）
     */
    Statement rewrite(SqlContext context);
}
```

---

## 三、具体实现示例

### 3.1 Checker 实现（纯检测）

#### NoWhereClauseChecker

```java
/**
 * NoWhereClause 检测器（重构版）
 */
public class NoWhereClauseChecker extends AbstractRuleChecker {

    private final NoWhereClauseConfig config;
    private final ValidationResult result;  // ✅ 通过构造注入

    public NoWhereClauseChecker(NoWhereClauseConfig config, ValidationResult result) {
        this.config = config;
        this.result = result;
    }

    @Override
    public void visitUpdate(Update update, SqlContext context) {
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
    public void visitDelete(Delete delete, SqlContext context) {
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

#### BlacklistFieldChecker

```java
/**
 * Blacklist 字段检测器（重构版）
 */
public class BlacklistFieldChecker extends AbstractRuleChecker {

    private final BlacklistFieldsConfig config;
    private final ValidationResult result;

    @Override
    public void visitSelect(Select select, SqlContext context) {
        // 提取所有字段
        Set<String> fields = extractFields(select);

        // 检查黑名单
        Set<String> violations = new HashSet<>(fields);
        violations.retainAll(config.getBlacklistedFields());

        if (!violations.isEmpty()) {
            result.addViolation(
                RiskLevel.MEDIUM,
                "Blacklisted fields in SELECT: " + violations,
                "Remove blacklisted fields: " + violations
            );
        }
    }

    /**
     * 从 Select 提取所有字段（直接使用 JSqlParser API）
     */
    private Set<String> extractFields(Select select) {
        Set<String> fields = new HashSet<>();
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        for (SelectItem<?> item : plainSelect.getSelectItems()) {
            if (item.getExpression() instanceof Column) {
                Column column = (Column) item.getExpression();
                fields.add(column.getColumnName());
            }
        }

        return fields;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }
}
```

### 3.2 Rewriter 实现（兜底改写）

#### SelectLimitRewriter

```java
/**
 * SELECT LIMIT 兜底改写器
 */
public class SelectLimitRewriter implements StatementRewriter {

    private final Long defaultLimit;
    private final DbType dbType;

    @Override
    public Statement rewrite(SqlContext context) {
        Statement statement = context.getStatement();

        if (!(statement instanceof Select)) {
            return null; // 不处理非 SELECT
        }

        Select select = (Select) statement;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        // 检查是否已有 LIMIT
        if (plainSelect.getLimit() != null) {
            return null; // 已有 LIMIT，不处理
        }

        // 添加 LIMIT（创建新的 Select，不修改原对象）
        Select newSelect = (Select) select.clone(); // JSqlParser 支持 clone
        PlainSelect newPlainSelect = (PlainSelect) newSelect.getSelectBody();

        Limit limit = new Limit();
        limit.setRowCount(new LongValue(defaultLimit));
        newPlainSelect.setLimit(limit);

        return newSelect;
    }

    @Override
    public void visitSelect(Select select, SqlContext context) {
        // StatementRewriter 不需要实现 visit 方法
    }
}
```

### 3.3 InnerInterceptor 实现

#### SqlGuardCheckInnerInterceptor（桥接 Checker）

```java
/**
 * SQL Guard 检测拦截器
 *
 * 桥接 RuleChecker 和 InnerInterceptor
 */
public class SqlGuardCheckInnerInterceptor implements InnerInterceptor {

    private final List<RuleChecker> checkers;
    private final ViolationStrategy strategy;

    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms,
                               Object parameter, RowBounds rowBounds,
                               ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {

        String sql = boundSql.getSql();

        // ✅ 1. 解析 SQL
        Statement statement;
        try {
            statement = JsqlParserGlobal.parse(sql);
        } catch (JSQLParserException e) {
            logger.warn("Failed to parse SQL: {}", sql, e);
            return true; // 解析失败，继续执行
        }

        // ✅ 2. 构建 SqlContext
        SqlContext context = SqlContext.builder()
            .statement(statement)
            .sql(sql)
            .type(SqlCommandType.SELECT)
            .mapperId(ms.getId())
            .rowBounds(rowBounds)
            .build();

        // ✅ 3. 执行所有 Checker
        ValidationResult result = new ValidationResult();
        for (RuleChecker checker : checkers) {
            if (checker.isEnabled()) {
                checker.check(context, result);
            }
        }

        // ✅ 4. 处理违规
        if (!result.isPassed()) {
            return handleViolation(result, strategy);
        }

        return true;
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {

        BoundSql boundSql = ms.getBoundSql(parameter);
        String sql = boundSql.getSql();

        // 同样的检测逻辑
        Statement statement;
        try {
            statement = JsqlParserGlobal.parse(sql);
        } catch (JSQLParserException e) {
            logger.warn("Failed to parse SQL: {}", sql, e);
            return;
        }

        SqlContext context = SqlContext.builder()
            .statement(statement)
            .sql(sql)
            .type(convertType(ms.getSqlCommandType()))
            .mapperId(ms.getId())
            .build();

        ValidationResult result = new ValidationResult();
        for (RuleChecker checker : checkers) {
            if (checker.isEnabled()) {
                checker.check(context, result);
            }
        }

        if (!result.isPassed()) {
            handleViolation(result, strategy);
        }
    }

    private boolean handleViolation(ValidationResult result, ViolationStrategy strategy)
            throws SQLException {
        switch (strategy) {
            case BLOCK:
                throw new SQLException("SQL Safety Violation: " + result.getMessage());
            case WARN:
                logger.warn("SQL Safety Violation: {}", result.getMessage());
                return true;
            case LOG:
                logger.info("SQL Safety Violation: {}", result.getMessage());
                return true;
            default:
                return true;
        }
    }

    @Override
    public int getPriority() {
        return 10; // 检测类优先级高
    }
}
```

#### SqlGuardRewriteInnerInterceptor（桥接 Rewriter）

```java
/**
 * SQL Guard 改写拦截器
 *
 * 桥接 StatementRewriter 和 InnerInterceptor
 */
public class SqlGuardRewriteInnerInterceptor implements InnerInterceptor {

    private final List<StatementRewriter> rewriters;

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms,
                           Object parameter, RowBounds rowBounds,
                           ResultHandler resultHandler, BoundSql boundSql) {

        String sql = boundSql.getSql();

        // ✅ 1. 解析 SQL
        Statement statement;
        try {
            statement = JsqlParserGlobal.parse(sql);
        } catch (JSQLParserException e) {
            logger.warn("Failed to parse SQL: {}", sql, e);
            return;
        }

        // ✅ 2. 构建 SqlContext
        SqlContext context = SqlContext.builder()
            .statement(statement)
            .sql(sql)
            .type(SqlCommandType.SELECT)
            .mapperId(ms.getId())
            .rowBounds(rowBounds)
            .build();

        // ✅ 3. 应用所有 Rewriter
        Statement rewrittenStatement = statement;
        for (StatementRewriter rewriter : rewriters) {
            Statement newStatement = rewriter.rewrite(context);
            if (newStatement != null) {
                rewrittenStatement = newStatement;
                // 更新 context（链式改写）
                context = context.toBuilder()
                    .statement(rewrittenStatement)
                    .sql(rewrittenStatement.toString())
                    .build();
            }
        }

        // ✅ 4. 如果有改写，替换 SQL
        if (rewrittenStatement != statement) {
            PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
            mpBs.sql(rewrittenStatement.toString());
        }
    }

    @Override
    public int getPriority() {
        return 100; // 兜底类优先级低
    }
}
```

### 3.4 Validator 简化

```java
/**
 * SQL 安全验证器（简化版）
 */
public class DefaultSqlSafetyValidator implements SqlSafetyValidator {

    private final List<RuleChecker> checkers;

    @Override
    public ValidationResult validate(SqlContext context) {
        ValidationResult result = new ValidationResult();

        // 执行所有启用的 Checker
        for (RuleChecker checker : checkers) {
            if (checker.isEnabled()) {
                checker.check(context, result);
            }
        }

        return result;
    }
}
```

**关键简化**：
- ❌ 删除 RuleCheckerOrchestrator（逻辑合并到 Validator）
- ❌ 删除复杂的缓存逻辑（交给 InnerInterceptor 层）
- ✅ 纯粹的 Checker 编排

---

## 四、完整执行流程

### 4.1 MyBatis 拦截器层

```java
/**
 * SQL Guard 主拦截器
 */
@Intercepts({...})
public class SqlGuardInterceptor implements Interceptor {

    private final List<InnerInterceptor> innerInterceptors;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        // 判断是 query 还是 update
        boolean isQuery = args.length >= 3 && args[2] instanceof RowBounds;

        if (isQuery) {
            // SELECT
            RowBounds rowBounds = (RowBounds) args[2];
            ResultHandler resultHandler = (ResultHandler) args[3];
            BoundSql boundSql = ms.getBoundSql(parameter);

            // ✅ 按优先级排序
            List<InnerInterceptor> sortedInterceptors = innerInterceptors.stream()
                .sorted(Comparator.comparingInt(InnerInterceptor::getPriority))
                .collect(Collectors.toList());

            // ✅ 1. 执行 willDoQuery
            for (InnerInterceptor interceptor : sortedInterceptors) {
                if (!interceptor.willDoQuery(executor, ms, parameter,
                                            rowBounds, resultHandler, boundSql)) {
                    return Collections.emptyList(); // 阻止执行
                }
            }

            // ✅ 2. 执行 beforeQuery
            for (InnerInterceptor interceptor : sortedInterceptors) {
                interceptor.beforeQuery(executor, ms, parameter,
                                      rowBounds, resultHandler, boundSql);
            }
        } else {
            // UPDATE/DELETE/INSERT
            List<InnerInterceptor> sortedInterceptors = innerInterceptors.stream()
                .sorted(Comparator.comparingInt(InnerInterceptor::getPriority))
                .collect(Collectors.toList());

            // ✅ 1. 执行 willDoUpdate
            for (InnerInterceptor interceptor : sortedInterceptors) {
                if (!interceptor.willDoUpdate(executor, ms, parameter)) {
                    return -1; // 阻止执行
                }
            }

            // ✅ 2. 执行 beforeUpdate
            for (InnerInterceptor interceptor : sortedInterceptors) {
                interceptor.beforeUpdate(executor, ms, parameter);
            }
        }

        return invocation.proceed();
    }
}
```

### 4.2 执行流程图

```
┌──────────────────────────────────────────────────────────────┐
│  1. SqlGuardInterceptor.intercept()                          │
│     - 获取 SQL 字符串                                         │
└──────────────────────────────────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────────────┐
│  2. SqlGuardCheckInnerInterceptor.willDoQuery()              │
│     - ✅ 解析 SQL → Statement（解析1次）                     │
│     - 构建 SqlContext（包含 Statement）                       │
│     - 执行所有 Checker：                                      │
│       • NoWhereClauseChecker.check()                          │
│         └─ visitUpdate(update, context)                       │
│       • BlacklistFieldChecker.check()                         │
│         └─ visitSelect(select, context)                       │
│       • NoPaginationChecker.check()                           │
│         └─ visitSelect(select, context)                       │
│     - 返回 true/false（是否继续执行）                         │
└──────────────────────────────────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────────────┐
│  3. SqlGuardRewriteInnerInterceptor.beforeQuery()            │
│     - ✅ 解析 SQL → Statement（解析1次）                     │
│     - 构建 SqlContext（包含 Statement）                       │
│     - 执行所有 Rewriter：                                     │
│       • SelectLimitRewriter.rewrite()                         │
│         └─ 返回新的 Statement（添加了 LIMIT）                │
│     - 替换 BoundSql 中的 SQL                                  │
└──────────────────────────────────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────────────┐
│  4. invocation.proceed()                                     │
│     - 执行数据库查询                                          │
└──────────────────────────────────────────────────────────────┘
```

**问题**：还是解析了 2 次（CheckInnerInterceptor 一次，RewriteInnerInterceptor 一次）

---

## 五、终极优化：共享 Statement

### 5.1 使用 ThreadLocal 共享

```java
/**
 * Statement 上下文（ThreadLocal）
 */
public class StatementContext {

    private static final ThreadLocal<Map<String, Statement>> CACHE =
        ThreadLocal.withInitial(HashMap::new);

    /**
     * 缓存 Statement
     */
    public static void cache(String sql, Statement statement) {
        CACHE.get().put(sql, statement);
    }

    /**
     * 获取缓存的 Statement
     */
    public static Statement get(String sql) {
        return CACHE.get().get(sql);
    }

    /**
     * 清理（必须在请求结束时调用）
     */
    public static void clear() {
        CACHE.remove();
    }
}
```

### 5.2 修改 InnerInterceptor

```java
public class SqlGuardCheckInnerInterceptor implements InnerInterceptor {

    @Override
    public boolean willDoQuery(...) {
        String sql = boundSql.getSql();

        // ✅ 1. 先从缓存获取
        Statement statement = StatementContext.get(sql);

        // ✅ 2. 如果没有，解析并缓存
        if (statement == null) {
            try {
                statement = JsqlParserGlobal.parse(sql);
                StatementContext.cache(sql, statement);  // ✅ 缓存
            } catch (JSQLParserException e) {
                logger.warn("Failed to parse SQL: {}", sql, e);
                return true;
            }
        }

        // ✅ 3. 使用 Statement
        SqlContext context = SqlContext.builder()
            .statement(statement)
            .sql(sql)
            .build();

        // ...
    }
}

public class SqlGuardRewriteInnerInterceptor implements InnerInterceptor {

    @Override
    public void beforeQuery(...) {
        String sql = boundSql.getSql();

        // ✅ 1. 从缓存获取（CheckInnerInterceptor 已经解析过）
        Statement statement = StatementContext.get(sql);

        // ✅ 2. 如果没有，解析并缓存
        if (statement == null) {
            try {
                statement = JsqlParserGlobal.parse(sql);
                StatementContext.cache(sql, statement);
            } catch (JSQLParserException e) {
                logger.warn("Failed to parse SQL: {}", sql, e);
                return;
            }
        }

        // ✅ 3. 使用 Statement
        SqlContext context = SqlContext.builder()
            .statement(statement)
            .sql(sql)
            .build();

        // ...
    }
}
```

### 5.3 在主拦截器中清理

```java
@Intercepts({...})
public class SqlGuardInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            // 执行拦截器链
            // ...
            return invocation.proceed();
        } finally {
            // ✅ 清理 ThreadLocal
            StatementContext.clear();
        }
    }
}
```

**效果**：
- ✅ CheckInnerInterceptor 解析一次并缓存
- ✅ RewriteInnerInterceptor 直接复用
- ✅ 一个请求中，同一个 SQL 只解析一次

---

## 六、模块结构

### 6.1 新的模块结构

```
sql-guard-core/
├── model/
│   ├── SqlContext.java               # 不可变上下文（包含 Statement）
│   ├── ValidationResult.java
│   └── ViolationInfo.java
├── visitor/
│   ├── StatementVisitor.java         # ✅ 新增：访问者接口
│   ├── RuleChecker.java              # ✅ 重构：纯检测逻辑
│   ├── AbstractRuleChecker.java      # ✅ 简化：模板方法
│   ├── StatementRewriter.java        # ✅ 新增：改写器接口
│   └── StatementContext.java         # ✅ 新增：ThreadLocal 共享
└── checker/
    ├── NoWhereClauseChecker.java     # ✅ 重构
    ├── BlacklistFieldChecker.java    # ✅ 重构
    ├── NoPaginationChecker.java      # ✅ 重构
    └── ...

sql-guard-mybatis/
├── SqlGuardInterceptor.java          # ✅ 主拦截器
├── inner/
│   ├── InnerInterceptor.java         # ✅ 参考 MyBatis-Plus
│   ├── SqlGuardCheckInnerInterceptor.java   # ✅ 检测类
│   └── SqlGuardRewriteInnerInterceptor.java # ✅ 兜底类
└── rewriter/
    └── SelectLimitRewriter.java      # ✅ 新增
```

### 6.2 删除的代码

```
❌ RuleCheckerOrchestrator.java       # 逻辑合并到 InnerInterceptor
❌ SqlDeduplicationFilter.java        # 逻辑合并到 InnerInterceptor
❌ AbstractRuleChecker 中的工具方法    # 简化
❌ SqlSafetyValidator 复杂逻辑        # 简化
```

---

## 七、总结

### 7.1 核心改进

| 维度 | 旧架构 | 新架构 |
|-----|--------|--------|
| **解析次数** | N 次（每 Checker + 每 InnerInterceptor） | 1 次（ThreadLocal 共享） |
| **接口数量** | 3 个（RuleChecker, InnerInterceptor, Rewriter） | 2 个（StatementVisitor, InnerInterceptor） |
| **抽象层次** | 混乱（Checker 和 InnerInterceptor 割裂） | 统一（都是 StatementVisitor） |
| **代码行数** | 多（重复逻辑多） | 少（统一抽象） |

### 7.2 架构优势

1. **彻底统一**：
   - Checker 和 Rewriter 都是 StatementVisitor
   - InnerInterceptor 是协调层
   - SqlContext 是数据载体

2. **性能最优**：
   - 同一个 SQL 只解析一次
   - ThreadLocal 缓存复用
   - 不可变对象安全共享

3. **简洁清晰**：
   - 删除冗余代码
   - 统一访问者模式
   - 职责分离明确

4. **完全对齐 MyBatis-Plus**：
   - InnerInterceptor 接口一致
   - 优先级机制一致
   - 生命周期钩子一致

---

**文档版本**: v2.0 Final
**最后更新**: 2025-12-19
