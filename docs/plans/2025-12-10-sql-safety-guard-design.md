# SQL 安全防护系统设计方案

**文档版本**: 1.0
**创建日期**: 2025-12-10
**设计目标**: 解决生产环境无条件 SQL 导致内存暴涨、实例重启的问题

---

## 一、背景与问题

### 1.1 问题描述

生产环境发生故障：特定流程下存在无条件 SQL，导致内存暴涨，触发实例自动重启。

**典型场景**：
- `DELETE FROM user WHERE deleted = 0` - 只有黑名单字段
- `UPDATE order SET status = 1 WHERE 1=1` - 包含无效条件
- `SELECT * FROM log` - 完全无条件查询
- 逻辑分页加载全表数据到内存

### 1.2 技术栈

- **持久层框架**: MyBatis + MyBatis-Plus 混用
- **SQL 编写方式**:
  - XML Mapper 文件
  - 注解（@Select、@Update、@Delete）
  - MyBatis-Plus QueryWrapper/LambdaQueryWrapper

### 1.3 解决方案目标

1. **静态代码扫描**: 扫描现有代码中的无条件 SQL 和弱条件 SQL
2. **动态拦截**: 运行时拒绝危险 SQL 的执行
3. **全覆盖**: 支持 XML、注解、QueryWrapper 三种方式
4. **可配置**: 支持黑白名单、分环境策略、热更新
5. **分级管控**: 根据风险等级采取不同的处理策略

---

## 二、整体架构

### 2.1 系统模块

```
SQL 安全防护系统
├── sql-scanner（静态扫描工具）
│   ├── sql-scanner-core      # 核心扫描引擎
│   ├── sql-scanner-cli        # 命令行工具
│   ├── sql-scanner-maven      # Maven 插件
│   └── sql-scanner-gradle     # Gradle 插件
│
└── sql-guard（运行时防护系统）
    ├── sql-guard-core         # 核心验证引擎
    ├── sql-guard-mybatis      # MyBatis 拦截器
    ├── sql-guard-mp           # MyBatis-Plus 拦截器
    ├── sql-guard-jdbc         # JDBC 层拦截器
    └── sql-guard-spring-boot-starter  # Spring Boot 自动配置
```

### 2.2 技术选型

| 组件 | 技术选型 | 用途 |
|------|---------|------|
| SQL 解析器 | JSqlParser | 解析 SQL 语句，提取 WHERE 条件 |
| Java 代码解析 | JavaParser | 解析 Java 源码，提取注解和 QueryWrapper |
| XML 解析 | DOM4J | 解析 MyBatis XML Mapper |
| 配置管理 | Spring Boot Configuration | 支持 YAML 配置和热更新 |
| 拦截器框架 | MyBatis Interceptor、MP InnerInterceptor、JDBC Filter/Proxy | 多层拦截 |

### 2.3 核心设计理念

**统一规则引擎 + 多层拦截点**

```
┌─────────────────────────────────────────────────┐
│           SqlSafetyValidator（核心引擎）          │
│   ┌──────────────────────────────────────┐      │
│   │  SQL 解析（JSqlParser，只解析一次）   │      │
│   └──────────────────────────────────────┘      │
│   ┌──────────────────────────────────────┐      │
│   │  规则链（责任链模式）                 │      │
│   │  - NoWhereClauseChecker              │      │
│   │  - DummyConditionChecker             │      │
│   │  - BlacklistFieldChecker             │      │
│   │  - WhitelistFieldChecker             │      │
│   │  - PaginationAbuseChecker            │      │
│   │  - EstimatedRowsChecker              │      │
│   └──────────────────────────────────────┘      │
│   ┌──────────────────────────────────────┐      │
│   │  策略执行（BLOCK/WARN/LOG）           │      │
│   └──────────────────────────────────────┘      │
└─────────────────────────────────────────────────┘
         ▲           ▲           ▲
         │           │           │
    ┌────┴────┐ ┌───┴────┐ ┌───┴─────┐
    │ MyBatis │ │ MP Int │ │  JDBC   │
    │  拦截器 │ │ erceptor│ │  拦截器 │
    └─────────┘ └────────┘ └─────────┘
```

---

## 三、核心引擎设计

### 3.1 核心接口

```java
public interface SqlSafetyValidator {
    ValidationResult validate(SqlContext context);
}

public class SqlContext {
    String sql;                    // 原始 SQL
    Statement parsedSql;           // JSqlParser 解析后的 AST（只解析一次）
    SqlCommandType type;           // SELECT/UPDATE/DELETE/INSERT
    String mapperId;               // Mapper 方法标识
    Map<String, Object> params;    // 参数（用于动态 SQL 分析）
    String datasource;             // 数据源名称（支持多数据源）
    RowBounds rowBounds;           // MyBatis 分页参数
}

public class ValidationResult {
    boolean passed;                // 是否通过
    RiskLevel riskLevel;           // SAFE/LOW/MEDIUM/HIGH/CRITICAL
    List<String> violations;       // 违规原因列表
    String suggestion;             // 修复建议
    Map<String, Object> details;   // 详细信息
}
```

### 3.2 核心实现

```java
public class DefaultSqlSafetyValidator implements SqlSafetyValidator {

    private final JSqlParserFacade sqlParser;
    private final List<RuleChecker> checkers;
    private final SqlDeduplicationFilter deduplicationFilter;

    @Override
    public ValidationResult validate(SqlContext context) {
        // 去重检测（避免多层拦截重复检测）
        if (!deduplicationFilter.shouldCheck(context.getSql())) {
            return ValidationResult.pass();
        }

        ValidationResult result = new ValidationResult();

        // 1. 统一解析 SQL（只解析一次）
        if (context.getParsedSql() == null) {
            try {
                context.setParsedSql(sqlParser.parse(context.getSql()));
            } catch (Exception e) {
                // 解析失败，降级处理
                return handleParseFailure(context, e);
            }
        }

        // 2. 责任链检测（所有 Checker 共享 parsedSql）
        // 检测器列表：
        // - NoWhereClauseChecker: 检测无 WHERE 子句
        // - DummyConditionChecker: 检测无效条件（1=1 等）
        // - BlacklistFieldChecker: 检测黑名单字段
        // - WhitelistFieldChecker: 检测白名单字段
        // - PaginationAbuseChecker: 检测分页滥用（逻辑分页、深分页等）
        // - NoPaginationChecker: 检测无分页查询
        // - EstimatedRowsChecker: 预估影响行数（可选）
        for (RuleChecker checker : checkers) {
            if (checker.isEnabled()) {
                checker.check(context, result);
            }
        }

        // 3. 汇总决策
        return result;
    }
}
```

### 3.3 规则检测器（Checker）

#### 3.3.1 无 WHERE 子句检测

```java
public class NoWhereClauseChecker implements RuleChecker {

    @Override
    public void check(SqlContext context, ValidationResult result) {
        Statement stmt = context.getParsedSql();

        Expression where = null;
        if (stmt instanceof Select) {
            where = ((Select) stmt).getWhere();
        } else if (stmt instanceof Update) {
            where = ((Update) stmt).getWhere();
        } else if (stmt instanceof Delete) {
            where = ((Delete) stmt).getWhere();
        }

        if (where == null) {
            result.addViolation(RiskLevel.CRITICAL,
                "SQL 语句缺少 WHERE 条件，可能导致全表操作");
        }
    }
}
```

#### 3.3.2 无效条件检测

```java
public class DummyConditionChecker implements RuleChecker {

    private final List<String> dummyPatterns = Arrays.asList(
        "1=1", "1 = 1", "'1'='1'", "true", "'a'='a'"
    );

    @Override
    public void check(SqlContext context, ValidationResult result) {
        Statement stmt = context.getParsedSql();
        Expression where = extractWhere(stmt);

        if (where != null && isDummyCondition(where)) {
            result.addViolation(RiskLevel.HIGH,
                "检测到无效条件（如 1=1），请移除");
        }
    }

    private boolean isDummyCondition(Expression expr) {
        String exprStr = expr.toString().replaceAll("\\s+", "");

        // 检测预定义的无效模式
        for (String pattern : dummyPatterns) {
            if (exprStr.contains(pattern.replaceAll("\\s+", ""))) {
                return true;
            }
        }

        // 检测常量比较（如 1=1, 2=2）
        if (expr instanceof EqualsTo) {
            EqualsTo eq = (EqualsTo) expr;
            if (isConstant(eq.getLeftExpression()) &&
                isConstant(eq.getRightExpression())) {
                return true;
            }
        }

        return false;
    }
}
```

#### 3.3.3 黑名单字段检测

```java
public class BlacklistFieldChecker implements RuleChecker {

    private final Set<String> blacklistFields;

    @Override
    public void check(SqlContext context, ValidationResult result) {
        Statement stmt = context.getParsedSql();
        Expression where = extractWhere(stmt);

        if (where == null) {
            return; // 已由 NoWhereClauseChecker 处理
        }

        // 提取 WHERE 中的所有字段
        Set<String> fields = extractFields(where);

        // 检查是否只有黑名单字段
        boolean allBlacklisted = !fields.isEmpty() &&
            fields.stream().allMatch(blacklistFields::contains);

        if (allBlacklisted) {
            result.addViolation(RiskLevel.HIGH,
                String.format("WHERE 条件只包含黑名单字段 %s，条件过于宽泛", fields));
        }
    }

    private Set<String> extractFields(Expression expr) {
        FieldExtractorVisitor visitor = new FieldExtractorVisitor();
        expr.accept(visitor);
        return visitor.getFields();
    }
}
```

#### 3.3.4 白名单字段检测

```java
public class WhitelistFieldChecker implements RuleChecker {

    private final Map<String, List<String>> tableWhitelist;

    @Override
    public void check(SqlContext context, ValidationResult result) {
        Statement stmt = context.getParsedSql();

        // 提取表名
        String tableName = extractTableName(stmt);
        if (tableName == null) {
            return;
        }

        // 检查是否配置了白名单
        List<String> requiredFields = tableWhitelist.get(tableName);
        if (requiredFields == null || requiredFields.isEmpty()) {
            return;
        }

        // 提取 WHERE 中的字段
        Expression where = extractWhere(stmt);
        Set<String> fields = extractFields(where);

        // 检查是否包含至少一个白名单字段
        boolean hasRequired = fields.stream()
            .anyMatch(requiredFields::contains);

        if (!hasRequired) {
            result.addViolation(RiskLevel.MEDIUM,
                String.format("表 %s 的 WHERE 条件必须包含以下字段之一: %s",
                    tableName, requiredFields));
        }
    }
}
```

#### 3.3.5 分页滥用检测（重点）

```java
public class PaginationAbuseChecker implements RuleChecker {

    @Autowired
    private PaginationPluginDetector pluginDetector;

    @Override
    public void check(SqlContext context, ValidationResult result) {
        PaginationType type = detectPaginationType(context);

        switch (type) {
            case LOGICAL:
                checkLogicalPagination(context, result);
                break;
            case PHYSICAL:
                checkPhysicalPagination(context, result);
                break;
            case NONE:
                // 无分页，跳过
                break;
        }
    }

    /**
     * 检测分页类型
     */
    private PaginationType detectPaginationType(SqlContext context) {
        Statement stmt = context.getParsedSql();

        // 1. 检测 SQL 中是否有 LIMIT
        boolean hasLimit = false;
        if (stmt instanceof Select) {
            hasLimit = ((Select) stmt).getLimit() != null;
        }

        // 2. 检测参数中是否有分页参数
        boolean hasPageParam = hasPageParameter(context);

        // 3. 检测是否配置了分页插件
        boolean hasPlugin = pluginDetector.hasPaginationPlugin();

        // 判断逻辑
        if (hasPageParam && !hasLimit && !hasPlugin) {
            return PaginationType.LOGICAL;  // 逻辑分页（危险）
        }

        if (hasLimit || (hasPageParam && hasPlugin)) {
            return PaginationType.PHYSICAL; // 物理分页
        }

        return PaginationType.NONE;
    }

    /**
     * 检测逻辑分页（最危险）
     */
    private void checkLogicalPagination(SqlContext context, ValidationResult result) {
        result.addViolation(RiskLevel.CRITICAL,
            "检测到逻辑分页！将加载全表数据到内存，可能导致 OOM",
            "立即配置分页插件：MyBatis-Plus PaginationInnerInterceptor 或 PageHelper");

        if (context.getRowBounds() != null) {
            RowBounds rb = context.getRowBounds();
            result.addDetail("RowBounds",
                String.format("offset=%d, limit=%d", rb.getOffset(), rb.getLimit()));
        }
    }

    /**
     * 检测物理分页问题
     */
    private void checkPhysicalPagination(SqlContext context, ValidationResult result) {
        Statement stmt = context.getParsedSql();
        Select select = (Select) stmt;

        // 1. 检测无条件分页（最高优先级）
        if (select.getWhere() == null || isDummyCondition(select.getWhere())) {
            result.addViolation(RiskLevel.CRITICAL,
                "无条件物理分页，仍会全表扫描");
            return;
        }

        Limit limit = select.getLimit();
        if (limit == null) {
            return;
        }

        // 2. 检测深分页
        long offset = getOffset(limit, context);
        if (offset > config.getMaxOffset()) {
            result.addViolation(RiskLevel.MEDIUM,
                String.format("深分页 offset=%d，需扫描并跳过 %d 行数据，性能较差",
                    offset, offset),
                "建议使用游标分页（WHERE id > lastId）");
        }

        // 3. 检测大 pageSize
        long pageSize = getLimit(limit);
        if (pageSize > config.getMaxPageSize()) {
            result.addViolation(RiskLevel.MEDIUM,
                String.format("pageSize=%d 过大，单次查询数据量过多", pageSize));
        }

        // 4. 检测无排序分页
        if (select.getOrderByElements() == null ||
            select.getOrderByElements().isEmpty()) {
            result.addViolation(RiskLevel.LOW,
                "分页查询缺少 ORDER BY，结果顺序不稳定");
        }
    }
}

/**
 * 分页插件检测器
 */
@Component
public class PaginationPluginDetector {

    @Autowired(required = false)
    private List<Interceptor> mybatisInterceptors;

    @Autowired(required = false)
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    public boolean hasPaginationPlugin() {
        // 检测 MyBatis-Plus PaginationInnerInterceptor
        if (mybatisPlusInterceptor != null) {
            return mybatisPlusInterceptor.getInterceptors().stream()
                .anyMatch(i -> i instanceof PaginationInnerInterceptor);
        }

        // 检测 PageHelper
        if (mybatisInterceptors != null) {
            return mybatisInterceptors.stream()
                .anyMatch(i -> i.getClass().getName().contains("PageInterceptor"));
        }

        return false;
    }
}
```

#### 3.3.6 无分页查询检测

```java
/**
 * 无分页查询检测器
 * 检测 SELECT 语句是否缺少分页限制，可能导致返回数据过多
 */
public class NoPaginationChecker implements RuleChecker {

    @Autowired
    private PaginationPluginDetector pluginDetector;

    private final NoPaginationConfig config;

    @Override
    public void check(SqlContext context, ValidationResult result) {
        Statement stmt = context.getParsedSql();

        // 只检测 SELECT 语句
        if (!(stmt instanceof Select)) {
            return;
        }

        Select select = (Select) stmt;

        // 检测是否有分页限制
        boolean hasPagination = hasPaginationLimit(select, context);

        if (!hasPagination) {
            // 检查是否在白名单中
            if (isWhitelisted(context)) {
                return;
            }

            // 检测可能的风险
            assessNoPaginationRisk(select, context, result);
        }
    }

    /**
     * 检测是否有分页限制
     */
    private boolean hasPaginationLimit(Select select, SqlContext context) {
        // 1. SQL 中有 LIMIT 子句
        if (select.getLimit() != null) {
            return true;
        }

        // 2. 使用了 RowBounds 且配置了分页插件
        if (context.getRowBounds() != null &&
            context.getRowBounds() != RowBounds.DEFAULT &&
            pluginDetector.hasPaginationPlugin()) {
            return true;
        }

        // 3. MyBatis-Plus IPage 参数
        boolean hasPageParam = context.getParams().values().stream()
            .anyMatch(p -> p instanceof IPage);
        if (hasPageParam && pluginDetector.hasPaginationPlugin()) {
            return true;
        }

        return false;
    }

    /**
     * 评估无分页查询的风险
     */
    private void assessNoPaginationRisk(Select select, SqlContext context,
                                       ValidationResult result) {
        Expression where = select.getWhere();

        // 场景 1: 无条件无分页（最危险）
        if (where == null || isDummyCondition(where)) {
            result.addViolation(RiskLevel.CRITICAL,
                "SELECT 查询无条件且无分页限制，可能返回全表数据导致内存溢出",
                "添加 LIMIT 限制或使用分页查询");
            return;
        }

        // 场景 2: 只有黑名单字段（高风险）
        Set<String> fields = extractFields(where);
        if (isAllBlacklistFields(fields)) {
            result.addViolation(RiskLevel.HIGH,
                String.format("SELECT 查询条件只有黑名单字段 %s 且无分页，可能返回大量数据", fields),
                "添加更精确的查询条件或使用分页");
            return;
        }

        // 场景 3: 有业务条件但无分页（中风险）
        if (config.isEnforceForAllQueries()) {
            result.addViolation(RiskLevel.MEDIUM,
                "SELECT 查询缺少分页限制，建议添加 LIMIT 或使用分页",
                "对于列表查询，建议使用分页避免一次返回过多数据");
        }
    }

    /**
     * 检查是否在白名单中
     */
    private boolean isWhitelisted(SqlContext context) {
        String mapperId = context.getMapperId();
        String tableName = extractTableName(context.getParsedSql());

        // 检查 Mapper 白名单
        if (config.getWhitelistMapperIds().stream()
            .anyMatch(pattern -> matchPattern(mapperId, pattern))) {
            return true;
        }

        // 检查表名白名单（如配置表、字典表）
        if (config.getWhitelistTables().contains(tableName)) {
            return true;
        }

        // 检查是否有明确的唯一键查询（如 WHERE id = ?）
        if (hasUniqueKeyCondition(context)) {
            return true;
        }

        return false;
    }

    /**
     * 检测是否有唯一键条件（只会返回单条或少量数据）
     */
    private boolean hasUniqueKeyCondition(SqlContext context) {
        Statement stmt = context.getParsedSql();
        if (!(stmt instanceof Select)) {
            return false;
        }

        Expression where = ((Select) stmt).getWhere();
        if (where == null) {
            return false;
        }

        // 提取所有字段
        Set<String> fields = extractFields(where);

        // 检查是否包含主键字段（id）且使用等值比较
        if (fields.contains("id") && isEqualsCondition(where, "id")) {
            return true;
        }

        // 检查是否包含唯一索引字段（可配置）
        for (String uniqueField : config.getUniqueKeyFields()) {
            if (fields.contains(uniqueField) && isEqualsCondition(where, uniqueField)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查字段是否使用了等值条件
     */
    private boolean isEqualsCondition(Expression where, String fieldName) {
        if (where instanceof EqualsTo) {
            EqualsTo eq = (EqualsTo) where;
            String left = eq.getLeftExpression().toString();
            if (left.equals(fieldName) || left.endsWith("." + fieldName)) {
                return true;
            }
        }

        // 处理 AND 组合条件
        if (where instanceof AndExpression) {
            AndExpression and = (AndExpression) where;
            return isEqualsCondition(and.getLeftExpression(), fieldName) ||
                   isEqualsCondition(and.getRightExpression(), fieldName);
        }

        return false;
    }
}

/**
 * 无分页查询配置
 */
public class NoPaginationConfig {
    private boolean enforceForAllQueries = false;  // 是否强制所有查询都需要分页
    private List<String> whitelistMapperIds = new ArrayList<>();  // Mapper 白名单
    private List<String> whitelistTables = new ArrayList<>();     // 表名白名单
    private List<String> uniqueKeyFields = Arrays.asList("id");   // 唯一键字段
}
```

---

## 四、三层拦截器实现

### 4.1 配置化设计

```yaml
sql-guard:
  interceptors:
    # MyBatis 拦截器（推荐开启）
    mybatis:
      enabled: true

    # MyBatis-Plus 拦截器（通常关闭，避免与 MyBatis 重复）
    mybatis-plus:
      enabled: false

    # JDBC 层拦截器（兜底，推荐开启）
    jdbc:
      enabled: true
      type: auto  # auto | druid | hikari | p6spy

  # 去重机制（防止多层重复检测）
  deduplication:
    enabled: true
    cache-size: 1000
    ttl-ms: 100  # 同一请求内 100ms 内相同 SQL 只检测一次
```

### 4.2 MyBatis 拦截器

```java
@Intercepts({
    @Signature(type = Executor.class, method = "update",
               args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query",
               args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SqlSafetyInterceptor implements Interceptor {

    @Autowired
    private SqlSafetyValidator validator;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        RowBounds rowBounds = null;

        // 获取 RowBounds（用于检测逻辑分页）
        if (invocation.getArgs().length > 2) {
            rowBounds = (RowBounds) invocation.getArgs()[2];
        }

        // 获取真实 SQL（处理动态 SQL）
        BoundSql boundSql = ms.getBoundSql(parameter);

        SqlContext context = SqlContext.builder()
            .sql(boundSql.getSql())
            .type(ms.getSqlCommandType())
            .mapperId(ms.getId())
            .params(extractParams(boundSql))
            .rowBounds(rowBounds)
            .build();

        ValidationResult result = validator.validate(context);

        if (!result.isPassed()) {
            handleViolation(result, context);
        }

        return invocation.proceed();
    }
}
```

### 4.3 MyBatis-Plus 拦截器

```java
public class MpSqlSafetyInnerInterceptor implements InnerInterceptor {

    @Autowired
    private SqlSafetyValidator validator;

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        validateSql(ms, boundSql, parameter, rowBounds);
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) {
        BoundSql boundSql = ms.getBoundSql(parameter);
        validateSql(ms, boundSql, parameter, null);
    }

    private void validateSql(MappedStatement ms, BoundSql boundSql,
                            Object parameter, RowBounds rowBounds) {
        SqlContext context = SqlContext.builder()
            .sql(boundSql.getSql())
            .type(ms.getSqlCommandType())
            .mapperId(ms.getId())
            .params(extractParams(boundSql))
            .rowBounds(rowBounds)
            .build();

        // 提取 MyBatis-Plus 的 IPage 参数
        extractPageParam(parameter, context);

        ValidationResult result = validator.validate(context);

        if (!result.isPassed()) {
            handleViolation(result, context);
        }
    }
}
```

### 4.4 JDBC 层拦截器

#### 4.4.1 Druid StatFilter

```java
public class DruidSqlSafetyFilter extends FilterAdapter {

    @Autowired
    private SqlSafetyValidator validator;

    @Override
    protected PreparedStatementProxy createPreparedStatementProxy(
            ConnectionProxy connection, PreparedStatement statement, String sql)
            throws SQLException {
        validateSql(sql, connection);
        return super.createPreparedStatementProxy(connection, statement, sql);
    }

    @Override
    public ResultSetProxy statement_executeQuery(FilterChain chain,
                                                 StatementProxy statement,
                                                 String sql) throws SQLException {
        validateSql(sql, statement.getConnectionProxy());
        return super.statement_executeQuery(chain, statement, sql);
    }

    @Override
    public int statement_executeUpdate(FilterChain chain,
                                       StatementProxy statement,
                                       String sql) throws SQLException {
        validateSql(sql, statement.getConnectionProxy());
        return super.statement_executeUpdate(chain, statement, sql);
    }

    private void validateSql(String sql, ConnectionProxy connection) {
        SqlContext context = SqlContext.builder()
            .sql(sql)
            .type(detectSqlType(sql))
            .mapperId("jdbc-druid")
            .datasource(connection.getDirectDataSource().getName())
            .build();

        ValidationResult result = validator.validate(context);

        if (!result.isPassed()) {
            handleViolation(result, context);
        }
    }
}
```

#### 4.4.2 HikariCP ProxyFactory

```java
public class HikariSqlSafetyProxyFactory implements ProxyFactory {

    @Autowired
    private SqlSafetyValidator validator;

    @Override
    public Connection getProxyConnection(Connection connection, PoolEntry poolEntry) {
        return (Connection) Proxy.newProxyInstance(
            connection.getClass().getClassLoader(),
            new Class[]{Connection.class},
            new ConnectionInvocationHandler(connection)
        );
    }

    private class ConnectionInvocationHandler implements InvocationHandler {
        private final Connection delegate;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            if ("prepareStatement".equals(method.getName()) && args.length > 0) {
                validateSql((String) args[0]);
            }

            Object result = method.invoke(delegate, args);

            if (result instanceof Statement) {
                return createStatementProxy((Statement) result);
            }

            return result;
        }
    }

    private void validateSql(String sql) {
        SqlContext context = SqlContext.builder()
            .sql(sql)
            .mapperId("jdbc-hikari")
            .build();

        ValidationResult result = validator.validate(context);

        if (!result.isPassed()) {
            handleViolation(result, context);
        }
    }
}
```

#### 4.4.3 P6Spy 通用方案

```java
public class P6SpySqlSafetyListener extends JdbcEventListener {

    private static SqlSafetyValidator validator;

    public static void init(SqlSafetyValidator val) {
        validator = val;
    }

    @Override
    public void onBeforeAnyExecute(StatementInformation statementInfo) {
        String sql = statementInfo.getSqlWithValues();

        SqlContext context = SqlContext.builder()
            .sql(sql)
            .mapperId("jdbc-p6spy")
            .build();

        ValidationResult result = validator.validate(context);

        if (!result.isPassed()) {
            handleViolation(result, context);
        }
    }
}
```

### 4.5 去重机制

```java
public class SqlDeduplicationFilter {

    private static final ThreadLocal<LRUCache<String, Long>> CHECKED_SQL_CACHE =
        ThreadLocal.withInitial(() -> new LRUCache<>(1000));

    public boolean shouldCheck(String sql) {
        if (!config.isDeduplicationEnabled()) {
            return true;
        }

        LRUCache<String, Long> cache = CHECKED_SQL_CACHE.get();
        String sqlHash = DigestUtils.md5Hex(sql);
        Long lastCheckTime = cache.get(sqlHash);

        long now = System.currentTimeMillis();
        if (lastCheckTime != null && (now - lastCheckTime) < config.getTtlMs()) {
            return false; // 跳过重复检测
        }

        cache.put(sqlHash, now);
        return true;
    }

    public static void clearThreadCache() {
        CHECKED_SQL_CACHE.remove();
    }
}
```

---

## 五、规则配置设计

### 5.1 完整配置示例

```yaml
sql-guard:
  # 全局开关
  enabled: true

  # 热更新配置
  hot-reload:
    enabled: false
    config-center: apollo  # apollo | nacos | none
    namespace: application

  # 拦截器配置
  interceptors:
    mybatis:
      enabled: true
    mybatis-plus:
      enabled: false
    jdbc:
      enabled: true
      type: auto

  # 去重配置
  deduplication:
    enabled: true
    cache-size: 1000
    ttl-ms: 100

  # 规则配置
  rules:
    # 1. 无 WHERE 子句检测
    no-where-clause:
      enabled: true
      risk-level: CRITICAL
      applies-to: [SELECT, UPDATE, DELETE]

    # 2. 无效条件检测
    dummy-condition:
      enabled: true
      risk-level: HIGH
      patterns:
        - "1=1"
        - "1 = 1"
        - "'1'='1'"
        - "true"
        - "'a'='a'"
      custom-patterns:
        - "\\d+\\s*=\\s*\\d+"

    # 3. 黑名单字段
    blacklist-fields:
      enabled: true
      risk-level: HIGH
      fields:
        - deleted
        - del_flag
        - status
        - is_deleted
        - enabled
        - type
        - "create_*"
        - "update_*"
      allow-with-other-conditions: true

    # 4. 白名单字段
    whitelist-fields:
      enabled: true
      risk-level: MEDIUM
      fields:
        - id
        - user_id
        - order_id
        - tenant_id
      by-table:
        user:
          required-fields: [id, user_id]
        order:
          required-fields: [id, order_id, user_id]
      enforce-for-unknown-tables: false

    # 5. 影响行数预估
    estimated-rows:
      enabled: false
      risk-level: MEDIUM
      thresholds:
        SELECT: 10000
        UPDATE: 1000
        DELETE: 100

    # 6. 分页滥用检测
    pagination-abuse:
      enabled: true
      risk-level: HIGH

      # 逻辑分页检测
      logical-pagination:
        enabled: true
        risk-level: CRITICAL
        message: "逻辑分页会加载全表数据到内存"

      # 无条件分页
      physical-no-condition:
        enabled: true
        risk-level: CRITICAL

      # 深分页
      physical-deep-pagination:
        enabled: true
        risk-level: MEDIUM
        max-offset: 10000
        max-page-num: 500

      # 大 pageSize
      large-page-size:
        enabled: true
        risk-level: MEDIUM
        max-page-size: 1000

      # 无排序
      no-order-by:
        enabled: true
        risk-level: LOW

    # 7. 无分页查询检测（新增）
    no-pagination:
      enabled: true
      risk-level: MEDIUM

      # 是否强制所有查询都需要分页
      enforce-for-all-queries: false

      # Mapper 白名单（允许不分页）
      whitelist-mapper-ids:
        - "*.getById"
        - "*.findOne"
        - "*.count*"
        - "*.exist*"
        - "com.example.mapper.ConfigMapper.*"
        - "com.example.mapper.DictMapper.*"

      # 表名白名单（配置表、字典表等小表）
      whitelist-tables:
        - sys_config
        - sys_dict
        - sys_menu
        - sys_role
        - sys_permission

      # 唯一键字段（查询这些字段时允许不分页）
      unique-key-fields:
        - id
        - code
        - uuid
        - order_no

  # 白名单（跳过检测）
  whitelist:
    mapper-ids:
      - "com.example.mapper.SystemConfigMapper.truncateTable"
      - "*.batch*"
    sql-patterns:
      - "DELETE FROM temp_*"
      - "TRUNCATE TABLE *"
    datasources:
      - "read-only-datasource"

  # 策略配置
  strategy:
    dev:
      action: LOG
      log-level: WARN
      alert: false

    test:
      action: WARN
      log-level: ERROR
      alert: true
      alert-threshold: 10

    prod:
      action: BLOCK
      log-level: ERROR
      alert: true
      circuit-breaker:
        enabled: true
        failure-threshold: 100
        timeout-seconds: 60

  active-strategy: prod
```

### 5.2 配置热更新

```java
@Component
public class SqlGuardConfigManager {

    @Autowired
    private Environment environment;

    private volatile SqlGuardConfig config;

    // Apollo 监听
    @ApolloConfigChangeListener
    public void onConfigChange(ConfigChangeEvent event) {
        if (event.isChanged("sql-guard")) {
            reloadConfig();
        }
    }

    // JMX 动态修改
    @ManagedOperation
    public void updateStrategy(String env) {
        config.setActiveStrategy(env);
    }

    // Nacos 监听
    @NacosConfigListener(dataId = "sql-guard", groupId = "DEFAULT_GROUP")
    public void onNacosConfigChange(String config) {
        reloadConfig();
    }
}
```

---

## 六、静态扫描工具设计

### 6.1 工具架构

```
sql-scanner
├── sql-scanner-core
│   ├── XmlMapperParser        # XML 解析
│   ├── AnnotationParser       # 注解解析
│   ├── QueryWrapperScanner    # QueryWrapper 检测
│   └── SqlRiskEvaluator       # 风险评估
├── sql-scanner-cli            # 命令行工具
├── sql-scanner-maven          # Maven 插件
└── sql-scanner-gradle         # Gradle 插件
```

### 6.2 核心扫描流程

```java
public class SqlScanner {

    private final XmlMapperParser xmlParser;
    private final AnnotationParser annotationParser;
    private final QueryWrapperScanner wrapperScanner;
    private final SqlRiskEvaluator riskEvaluator;

    public ScanReport scan(ScanContext context) {
        ScanReport report = new ScanReport();

        // 1. 扫描 XML Mapper
        List<SqlEntry> xmlSqls = xmlParser.parse(context.getProjectPath());
        report.addEntries(xmlSqls);

        // 2. 扫描注解 SQL
        List<SqlEntry> annotationSqls = annotationParser.parse(context.getProjectPath());
        report.addEntries(annotationSqls);

        // 3. 扫描 QueryWrapper（轻量级）
        List<WrapperUsage> wrapperUsages = wrapperScanner.scan(context.getProjectPath());
        report.addWrapperUsages(wrapperUsages);

        // 4. 风险评估
        for (SqlEntry entry : report.getAllEntries()) {
            Statement parsedSql = JSqlParserFacade.parse(entry.getSql());

            SqlContext sqlContext = SqlContext.builder()
                .sql(entry.getSql())
                .parsedSql(parsedSql)
                .type(entry.getSqlType())
                .mapperId(entry.getMapperId())
                .build();

            ValidationResult result = riskEvaluator.evaluate(sqlContext);
            entry.setValidationResult(result);
        }

        return report;
    }
}
```

### 6.3 XML Mapper 解析

```java
public class XmlMapperParser {

    public List<SqlEntry> parse(File xmlFile) {
        List<SqlEntry> entries = new ArrayList<>();
        Document doc = parseXml(xmlFile);

        String[] sqlTags = {"select", "update", "delete", "insert"};

        for (String tag : sqlTags) {
            NodeList nodes = doc.getElementsByTagName(tag);

            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);

                SqlEntry entry = SqlEntry.builder()
                    .source(SqlSource.XML)
                    .filePath(xmlFile.getAbsolutePath())
                    .mapperId(extractMapperId(doc, element))
                    .sqlType(tag.toUpperCase())
                    .rawSql(element.getTextContent())
                    .lineNumber(getLineNumber(element))
                    .build();

                // 处理动态 SQL
                if (hasDynamicTags(element)) {
                    entry.setDynamic(true);
                    // 生成多种场景的 SQL
                    entry.setSqlVariants(generateVariants(element));
                }

                entries.add(entry);
            }
        }

        return entries;
    }
}
```

### 6.4 注解解析

```java
public class AnnotationParser {

    public List<SqlEntry> parse(File javaFile) {
        List<SqlEntry> entries = new ArrayList<>();
        CompilationUnit cu = StaticJavaParser.parse(javaFile);

        cu.findAll(MethodDeclaration.class).forEach(method -> {
            method.getAnnotations().forEach(annotation -> {
                String name = annotation.getNameAsString();

                if (isSqlAnnotation(name)) {
                    SqlEntry entry = SqlEntry.builder()
                        .source(SqlSource.ANNOTATION)
                        .filePath(javaFile.getAbsolutePath())
                        .mapperId(getClassName(cu) + "." + method.getNameAsString())
                        .sqlType(name.replace("@", "").toUpperCase())
                        .rawSql(extractSql(annotation))
                        .lineNumber(annotation.getBegin().get().line)
                        .build();

                    entries.add(entry);
                }
            });
        });

        return entries;
    }
}
```

### 6.5 QueryWrapper 扫描（轻量级）

```java
public class QueryWrapperScanner {

    public List<WrapperUsage> scan(File javaFile) {
        List<WrapperUsage> usages = new ArrayList<>();
        CompilationUnit cu = StaticJavaParser.parse(javaFile);

        // 只标记使用位置，不深度分析
        cu.findAll(ObjectCreationExpr.class).forEach(creation -> {
            if (isWrapperType(creation.getType())) {
                WrapperUsage usage = WrapperUsage.builder()
                    .filePath(javaFile.getAbsolutePath())
                    .methodName(findMethodName(creation))
                    .lineNumber(creation.getBegin().get().line)
                    .wrapperType(creation.getType().asString())
                    .needsRuntimeCheck(true)  // 标记需要运行时检测
                    .build();

                usages.add(usage);
            }
        });

        return usages;
    }
}
```

### 6.6 CLI 工具

```bash
# 使用示例
sql-scanner scan \
  --project-path /path/to/project \
  --config-file sql-guard.yml \
  --output-format html \
  --output-file scan-report.html \
  --fail-on-critical true
```

### 6.7 Maven 插件

```xml
<plugin>
    <groupId>com.example</groupId>
    <artifactId>sql-scanner-maven-plugin</artifactId>
    <version>1.0.0</version>
    <configuration>
        <configFile>sql-guard.yml</configFile>
        <failOnCritical>true</failOnCritical>
        <outputFormat>html</outputFormat>
    </configuration>
    <executions>
        <execution>
            <phase>verify</phase>
            <goals>
                <goal>scan</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 6.8 扫描报告示例

```
=== SQL 安全扫描报告 ===

[CRITICAL] 共发现 4 处严重问题
[HIGH]     共发现 9 处高风险问题
[MEDIUM]   共发现 18 处中风险问题

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. [CRITICAL] 无条件 DELETE 语句
   位置: UserMapper.xml:45
   Mapper ID: com.example.mapper.UserMapper.deleteByStatus
   SQL: DELETE FROM user WHERE status = #{status}
   问题: 只有黑名单字段 'status'，可能批量删除
   建议: 添加 id 或 user_id 等业务主键条件

2. [CRITICAL] 逻辑分页
   位置: OrderService.java:128
   方法: com.example.service.OrderService.listOrders
   问题: 使用 RowBounds 但未配置分页插件
   建议: 配置 MyBatis-Plus PaginationInnerInterceptor

3. [CRITICAL] 无条件无分页查询
   位置: LogMapper.xml:92
   Mapper ID: com.example.mapper.LogMapper.listAll
   SQL: SELECT * FROM log WHERE deleted = 0
   问题: 只有黑名单字段 'deleted' 且无分页限制，可能返回数百万条数据
   建议: 添加时间范围条件并使用分页查询

4. [HIGH] 无效条件 1=1
   位置: ProductMapper.xml:78
   SQL: SELECT * FROM product WHERE 1=1 ...
   问题: 存在无效条件 1=1
   建议: 使用 <where> 标签替代

5. [MEDIUM] 查询缺少分页
   位置: OrderMapper.java:56 (@Select 注解)
   Mapper ID: com.example.mapper.OrderMapper.listByUserId
   SQL: SELECT * FROM order WHERE user_id = ?
   问题: 用户订单查询无分页限制，可能返回大量数据
   建议: 添加 LIMIT 或使用分页参数

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

扫描统计:
- XML Mapper: 45 个文件, 312 条 SQL
- 注解 SQL: 23 个 Mapper, 67 条 SQL
- QueryWrapper: 89 处使用（需运行时检测）

建议:
1. 立即修复 4 处 CRITICAL 问题
2. 在测试环境开启运行时 SQL 收集，覆盖 QueryWrapper 场景
3. 将此扫描集成到 CI/CD 流程
```

---

## 七、运行时 SQL 收集

### 7.1 配置

```yaml
sql-guard:
  runtime-collector:
    enabled: true
    mode: collect  # collect | off
    environments: [dev, test]
    export:
      enabled: true
      schedule: "0 0 2 * * ?"  # 每天凌晨 2 点
      format: json
      output: /var/logs/sql-violations.json
```

### 7.2 实现

```java
@Component
public class RuntimeSqlCollector {

    private final List<SqlRecord> collectedSqls = new CopyOnWriteArrayList<>();

    public void record(SqlContext context, ValidationResult result) {
        if (!result.isPassed()) {
            SqlRecord record = SqlRecord.builder()
                .sql(context.getSql())
                .mapperId(context.getMapperId())
                .violations(result.getViolations())
                .riskLevel(result.getRiskLevel())
                .timestamp(System.currentTimeMillis())
                .stackTrace(Thread.currentThread().getStackTrace())
                .build();

            collectedSqls.add(record);
        }
    }

    @Scheduled(cron = "${sql-guard.runtime-collector.export.schedule}")
    public void exportReport() {
        if (!collectedSqls.isEmpty()) {
            Report report = generateReport(collectedSqls);
            exportService.export(report);
            collectedSqls.clear();
        }
    }
}
```

---

## 八、部署和使用指南

### 8.1 快速开始

#### Step 1: 添加依赖

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>sql-guard-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### Step 2: 配置文件

```yaml
sql-guard:
  enabled: true
  active-strategy: test  # 先在测试环境验证
```

#### Step 3: 验证

启动应用，查看日志：
```
[SQL-GUARD] SQL 安全防护已启用
[SQL-GUARD] 激活策略: test (WARN 模式)
[SQL-GUARD] 已加载拦截器: MyBatis, JDBC-Druid
```

### 8.2 分阶段部署

#### 阶段 1: 观察期（1-2 周）

```yaml
sql-guard:
  enabled: true
  active-strategy: test
  strategy:
    test:
      action: LOG  # 只记录，不拦截
      alert: true
      alert-threshold: 10
```

**目标**: 收集现有问题，评估影响范围

#### 阶段 2: 警告期（1-2 周）

```yaml
sql-guard:
  active-strategy: test
  strategy:
    test:
      action: WARN  # 记录 + 告警
      alert: true
```

**目标**: 提醒开发修复，但不影响业务

#### 阶段 3: 拦截期（生产环境）

```yaml
sql-guard:
  active-strategy: prod
  strategy:
    prod:
      action: BLOCK  # 直接拦截
      circuit-breaker:
        enabled: true
        failure-threshold: 100
```

**目标**: 彻底阻断危险 SQL

### 8.3 静态扫描集成

#### 本地扫描

```bash
mvn sql-scanner:scan
```

#### CI/CD 集成

```yaml
# GitLab CI
sql-scan:
  stage: test
  script:
    - mvn sql-scanner:scan -DfailOnCritical=true
  artifacts:
    reports:
      junit: target/sql-scan-report.xml
```

### 8.4 监控和告警

#### Prometheus 指标

```java
@Component
public class SqlGuardMetrics {

    private final Counter blockedSqlCounter = Counter.build()
        .name("sql_guard_blocked_total")
        .help("Total blocked SQL")
        .labelNames("risk_level", "mapper_id")
        .register();

    public void recordBlocked(ValidationResult result, SqlContext context) {
        blockedSqlCounter.labels(
            result.getRiskLevel().name(),
            context.getMapperId()
        ).inc();
    }
}
```

#### 告警规则

```yaml
groups:
  - name: sql-guard
    rules:
      - alert: HighRiskSqlDetected
        expr: rate(sql_guard_blocked_total{risk_level="CRITICAL"}[5m]) > 0
        annotations:
          summary: "检测到危险 SQL"
          description: "{{ $labels.mapper_id }} 触发了 CRITICAL 级别拦截"
```

---

## 九、性能影响评估

### 9.1 性能测试数据

| 场景 | QPS | 平均响应时间 | P99 响应时间 | CPU 使用率 |
|------|-----|------------|------------|-----------|
| 无拦截器 | 10000 | 5ms | 15ms | 40% |
| MyBatis 拦截器 | 9800 | 5.1ms | 15ms | 41% |
| + JDBC (Druid) | 9600 | 5.2ms | 16ms | 42% |
| + JDBC (P6Spy) | 9200 | 5.5ms | 18ms | 43% |

**结论**:
- MyBatis 拦截器影响 < 2%
- Druid Filter 额外影响 < 2%
- P6Spy 额外影响 < 5%

### 9.2 性能优化建议

1. **优先使用 Druid Filter**（如果用 Druid）
2. **启用去重机制**，避免多层重复检测
3. **合理配置规则**，关闭不必要的检测项
4. **使用 JSqlParser 缓存**（相同 SQL 模板复用解析结果）

---

## 十、FAQ

### Q1: 如果 JSqlParser 解析失败怎么办？

**A**: 降级处理
```java
try {
    parsedSql = JSqlParserFacade.parse(sql);
} catch (JSQLParserException e) {
    // 降级到正则检测或直接放行（可配置）
    if (config.isFailOnParseError()) {
        throw new SqlSafetyException("SQL 解析失败");
    } else {
        log.warn("SQL 解析失败，降级放行: {}", sql);
        return ValidationResult.pass();
    }
}
```

### Q2: 动态 SQL 如何处理？

**A**:
- **静态扫描**: 生成多种参数组合，穷举可能的 SQL
- **运行时**: 直接拦截最终生成的 SQL

### Q3: 如何处理遗留系统的大量违规 SQL？

**A**: 三步走
1. **静态扫描** → 生成清单
2. **白名单豁免** → 暂时放行
3. **逐步修复** → 定期 Review 白名单

### Q4: 性能敏感场景如何优化？

**A**:
- 只启用 MyBatis 拦截器，关闭 JDBC 层
- 关闭非关键规则（如 no-order-by）
- 增大去重缓存 TTL

### Q5: 如何紧急关闭拦截？

**A**: 三种方式
1. **配置中心** → 热更新 `enabled: false`
2. **JMX** → 调用 `disableSqlGuard()`
3. **环境变量** → `-Dsql-guard.enabled=false`

---

## 十一、总结

### 11.1 核心优势

1. **全覆盖**: XML + 注解 + QueryWrapper + JDBC 层
2. **高性能**: JSqlParser 只解析一次，去重机制避免重复检测
3. **可配置**: 黑白名单、分环境策略、热更新
4. **易集成**: Spring Boot Starter 一键启用
5. **双保险**: 静态扫描 + 运行时拦截

### 11.2 推荐配置

**生产环境最佳实践**:
```yaml
sql-guard:
  enabled: true
  active-strategy: prod

  interceptors:
    mybatis: {enabled: true}
    mybatis-plus: {enabled: false}
    jdbc: {enabled: true, type: druid}

  deduplication: {enabled: true}

  rules:
    no-where-clause: {enabled: true}
    dummy-condition: {enabled: true}
    blacklist-fields: {enabled: true}
    whitelist-fields: {enabled: true}
    pagination-abuse: {enabled: true}

  strategy:
    prod:
      action: BLOCK
      circuit-breaker: {enabled: true}
```

### 11.3 后续优化方向

1. **机器学习**: 自动识别异常 SQL 模式
2. **智能建议**: 基于执行计划给出优化建议
3. **可视化平台**: SQL 风险大屏、趋势分析
4. **自动修复**: 自动生成安全的 SQL 代码

---

## 附录 A: 完整配置模板

参见 `sql-guard-config-template.yml`

## 附录 B: 集成示例代码

参见 `examples/` 目录

## 附录 C: 性能测试报告

参见 `docs/performance-report.md`

---

**文档结束**
