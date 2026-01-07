# SQL Guard 架构重设计方案

**基于**: MyBatis-Plus 3.x 插件架构
**创建时间**: 2025-12-19
**目标**: 统一 MyBatis/MyBatis-Plus 拦截器架构，支持 SQL 检测和兜底改写

---

## 一、设计目标

### 1.1 核心原则

1. **参考 MyBatis-Plus，但不照搬**：
   - ✅ 借鉴 InnerInterceptor 的设计模式
   - ✅ 借鉴 JsqlParserSupport 的解析架构
   - ✅ 借鉴 Dialect 的多数据库支持
   - ❌ 不需要 Count SQL 优化（非性能优化目标）
   - ❌ 不需要 IllegalSQLInnerInterceptor（过于严格）

2. **与现有代码兼容**：
   - 保留现有 `RuleChecker` 接口和实现
   - 新架构内部调用现有 Checker
   - 逐步迁移，不破坏现有功能

3. **支持检测 + 兜底**：
   - **检测类拦截器**：检测危险 SQL，根据配置 WARN/BLOCK
   - **兜底类拦截器**：自动改写 SQL，添加安全限制（如 LIMIT）

### 1.2 功能范围

| 功能类型 | 现有实现 | 新架构实现 | 说明 |
|---------|---------|-----------|-----|
| **检测类** | RuleChecker | InnerInterceptor | 迁移到新架构 |
| - NoWhereClause | ✅ | SqlGuardNoWhereInnerInterceptor | |
| - Blacklist/Whitelist | ✅ | SqlGuardFieldInnerInterceptor | |
| - NoPagination | ✅ | SqlGuardPaginationInnerInterceptor | |
| - DeepPagination | ✅ | SqlGuardPaginationInnerInterceptor | |
| - DummyCondition | ✅ | SqlGuardConditionInnerInterceptor | |
| **兜底类** | ❌ 无 | SelectLimitInnerInterceptor | **新增** |
| - Auto LIMIT | ❌ | ✅ | 自动添加 LIMIT |
| - Rewrite Pagination | ❌ | ✅ | 改写超大分页 |

---

## 二、新架构设计

### 2.1 模块结构

```
sql-guard-mp/                               # MyBatis-Plus 风格拦截器
├── pom.xml
├── src/main/java/com/footstone/sqlguard/interceptor/mp/
│   ├── SqlGuardInterceptor.java            # 主拦截器（类似 MybatisPlusInterceptor）
│   ├── inner/
│   │   ├── SqlGuardInnerInterceptor.java   # 拦截器接口
│   │   ├── SqlGuardNoWhereInnerInterceptor.java
│   │   ├── SqlGuardFieldInnerInterceptor.java
│   │   ├── SqlGuardPaginationInnerInterceptor.java
│   │   ├── SqlGuardConditionInnerInterceptor.java
│   │   └── SelectLimitInnerInterceptor.java  # 新增：兜底LIMIT
│   ├── support/
│   │   └── SqlGuardJsqlParserSupport.java  # JSqlParser基类
│   └── dialect/
│       ├── SqlGuardDialect.java            # 方言接口
│       ├── MySqlGuardDialect.java
│       ├── OracleGuardDialect.java
│       ├── SqlServerGuardDialect.java
│       ├── PostgreSqlGuardDialect.java
│       └── DialectFactory.java
└── src/test/java/...
```

### 2.2 接口设计

#### SqlGuardInnerInterceptor

```java
/**
 * SQL Guard 内部拦截器接口
 * 参考 MyBatis-Plus InnerInterceptor 设计
 */
public interface SqlGuardInnerInterceptor {

    /**
     * 判断是否执行 query
     * @return false 表示阻止执行，直接返回空集合
     */
    default boolean willDoQuery(Executor executor, MappedStatement ms,
                                Object parameter, RowBounds rowBounds,
                                ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        return true;
    }

    /**
     * query 执行前处理（可以修改 SQL）
     */
    default void beforeQuery(Executor executor, MappedStatement ms,
                            Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        // 默认什么都不做
    }

    /**
     * 判断是否执行 update/delete/insert
     * @return false 表示阻止执行，返回 -1
     */
    default boolean willDoUpdate(Executor executor, MappedStatement ms,
                                 Object parameter)
            throws SQLException {
        return true;
    }

    /**
     * update/delete/insert 执行前处理
     */
    default void beforeUpdate(Executor executor, MappedStatement ms,
                             Object parameter)
            throws SQLException {
        // 默认什么都不做
    }

    /**
     * Statement 准备阶段（可以在这里拦截）
     */
    default void beforePrepare(StatementHandler sh, Connection connection,
                              Integer transactionTimeout) {
        // 默认什么都不做
    }

    /**
     * 属性配置
     */
    default void setProperties(Properties properties) {
        // 默认什么都不做
    }

    /**
     * 获取拦截器优先级（数字越小越优先）
     * 检测类拦截器：1-99
     * 兜底类拦截器：100-199
     */
    default int getPriority() {
        return 100;
    }
}
```

#### SqlGuardDialect

```java
/**
 * SQL Guard 数据库方言接口
 */
public interface SqlGuardDialect {

    /**
     * 为 SELECT 添加 LIMIT
     * @param sql 原始 SQL
     * @param limit 限制行数
     * @return 改写后的 SQL 和参数
     */
    DialectModel addLimit(String sql, long limit);

    /**
     * 改写分页参数（防止深度分页）
     * @param sql 原始 SQL
     * @param offset 原始 offset
     * @param limit 原始 limit
     * @param maxOffset 最大允许的 offset
     * @param maxLimit 最大允许的 limit
     * @return 改写后的 SQL 和参数
     */
    DialectModel rewritePagination(String sql, long offset, long limit,
                                   long maxOffset, long maxLimit);

    /**
     * 检测 SQL 是否已有分页
     * @param sql SQL 语句
     * @return true 表示已有分页
     */
    boolean hasPagination(String sql);

    /**
     * 提取现有的分页参数
     * @param sql SQL 语句
     * @return PaginationInfo{offset, limit, type}
     */
    PaginationInfo extractPagination(String sql);
}
```

### 2.3 核心类设计

#### SqlGuardInterceptor

```java
/**
 * SQL Guard 主拦截器
 * 参考 MybatisPlusInterceptor 实现
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare",
               args = {Connection.class, Integer.class}),
    @Signature(type = Executor.class, method = "update",
               args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query",
               args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "query",
               args = {MappedStatement.class, Object.class, RowBounds.class,
                      ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class SqlGuardInterceptor implements Interceptor {

    @Setter
    private List<SqlGuardInnerInterceptor> interceptors = new ArrayList<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();
        Object[] args = invocation.getArgs();

        if (target instanceof Executor) {
            Executor executor = (Executor) target;
            Object parameter = args[1];
            boolean isUpdate = args.length == 2;
            MappedStatement ms = (MappedStatement) args[0];

            if (!isUpdate && ms.getSqlCommandType() == SqlCommandType.SELECT) {
                // SELECT 查询
                RowBounds rowBounds = (RowBounds) args[2];
                ResultHandler resultHandler = (ResultHandler) args[3];
                BoundSql boundSql;
                if (args.length == 4) {
                    boundSql = ms.getBoundSql(parameter);
                } else {
                    boundSql = (BoundSql) args[5];
                }

                // 按优先级排序
                List<SqlGuardInnerInterceptor> sortedInterceptors = getSortedInterceptors();

                // 1. 执行 willDoQuery
                for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
                    if (!interceptor.willDoQuery(executor, ms, parameter,
                                                rowBounds, resultHandler, boundSql)) {
                        // 阻止执行
                        return Collections.emptyList();
                    }
                }

                // 2. 执行 beforeQuery
                for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
                    interceptor.beforeQuery(executor, ms, parameter,
                                          rowBounds, resultHandler, boundSql);
                }

                CacheKey cacheKey = executor.createCacheKey(ms, parameter, rowBounds, boundSql);
                return executor.query(ms, parameter, rowBounds, resultHandler, cacheKey, boundSql);

            } else if (isUpdate) {
                // UPDATE/DELETE/INSERT
                List<SqlGuardInnerInterceptor> sortedInterceptors = getSortedInterceptors();

                // 1. 执行 willDoUpdate
                for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
                    if (!interceptor.willDoUpdate(executor, ms, parameter)) {
                        return -1; // 阻止执行
                    }
                }

                // 2. 执行 beforeUpdate
                for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
                    interceptor.beforeUpdate(executor, ms, parameter);
                }
            }
        } else {
            // StatementHandler
            StatementHandler sh = (StatementHandler) target;
            Connection connection = (Connection) args[0];
            Integer transactionTimeout = (Integer) args[1];

            for (SqlGuardInnerInterceptor interceptor : getSortedInterceptors()) {
                interceptor.beforePrepare(sh, connection, transactionTimeout);
            }
        }

        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof Executor || target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    public void addInnerInterceptor(SqlGuardInnerInterceptor interceptor) {
        this.interceptors.add(interceptor);
    }

    /**
     * 按优先级排序拦截器
     */
    private List<SqlGuardInnerInterceptor> getSortedInterceptors() {
        return interceptors.stream()
            .sorted(Comparator.comparingInt(SqlGuardInnerInterceptor::getPriority))
            .collect(Collectors.toList());
    }
}
```

#### SqlGuardJsqlParserSupport

```java
/**
 * SQL Guard JSqlParser 支持基类
 * 参考 MyBatis-Plus JsqlParserSupport
 */
public abstract class SqlGuardJsqlParserSupport {
    protected final Log logger = LogFactory.getLog(this.getClass());

    // 复用现有的 Validator 和 Checker
    protected final SqlSafetyValidator validator;

    public SqlGuardJsqlParserSupport(SqlSafetyValidator validator) {
        this.validator = validator;
    }

    /**
     * 解析单个 SQL 语句
     */
    protected String parserSingle(String sql, Object obj) {
        try {
            Statement statement = JsqlParserGlobal.parse(sql);
            return processParser(statement, 0, sql, obj);
        } catch (JSQLParserException e) {
            logger.warn("Failed to parse SQL: " + sql, e);
            // 降级：使用字符串匹配
            return processFallback(sql, obj);
        }
    }

    /**
     * 处理解析后的 SQL
     */
    protected String processParser(Statement statement, int index, String sql, Object obj) {
        if (statement instanceof Select) {
            return processSelect((Select) statement, index, sql, obj);
        } else if (statement instanceof Update) {
            return processUpdate((Update) statement, index, sql, obj);
        } else if (statement instanceof Delete) {
            return processDelete((Delete) statement, index, sql, obj);
        } else if (statement instanceof Insert) {
            return processInsert((Insert) statement, index, sql, obj);
        }
        return sql;
    }

    // 子类实现这些方法
    protected abstract String processSelect(Select select, int index, String sql, Object obj);
    protected abstract String processUpdate(Update update, int index, String sql, Object obj);
    protected abstract String processDelete(Delete delete, int index, String sql, Object obj);
    protected abstract String processInsert(Insert insert, int index, String sql, Object obj);

    /**
     * 降级处理（解析失败时）
     */
    protected String processFallback(String sql, Object obj) {
        // 使用简单的字符串匹配
        return sql;
    }
}
```

---

## 三、具体实现示例

### 3.1 检测类拦截器：SqlGuardNoWhereInnerInterceptor

```java
/**
 * NoWhereClause 检测拦截器
 * 复用现有的 NoWhereClauseChecker
 */
public class SqlGuardNoWhereInnerInterceptor extends SqlGuardJsqlParserSupport
        implements SqlGuardInnerInterceptor {

    private final NoWhereClauseChecker checker;
    private ViolationStrategy strategy = ViolationStrategy.WARN;

    public SqlGuardNoWhereInnerInterceptor(NoWhereClauseChecker checker) {
        super(null);
        this.checker = checker;
    }

    @Override
    public int getPriority() {
        return 10; // 检测类拦截器优先级较高
    }

    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms,
                               Object parameter, RowBounds rowBounds,
                               ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        return checkSql(boundSql.getSql(), ms.getId(), SqlCommandType.SELECT);
    }

    @Override
    public boolean willDoUpdate(Executor executor, MappedStatement ms,
                               Object parameter) throws SQLException {
        BoundSql boundSql = ms.getBoundSql(parameter);
        SqlCommandType commandType = ms.getSqlCommandType();
        return checkSql(boundSql.getSql(), ms.getId(), commandType);
    }

    private boolean checkSql(String sql, String statementId, SqlCommandType commandType) {
        try {
            // 只检测 UPDATE 和 DELETE
            if (commandType != SqlCommandType.UPDATE && commandType != SqlCommandType.DELETE) {
                return true;
            }

            Statement statement = JsqlParserGlobal.parse(sql);
            Expression where = null;

            if (statement instanceof Update) {
                where = ((Update) statement).getWhere();
            } else if (statement instanceof Delete) {
                where = ((Delete) statement).getWhere();
            }

            // 调用现有的 Checker
            SqlContext context = new SqlContext(sql, statementId, commandType);
            CheckResult result = checker.check(where, context);

            if (result.isViolated()) {
                logger.warn("NoWhereClause violation detected: " + statementId);

                if (strategy == ViolationStrategy.BLOCK) {
                    throw new SqlSafetyException("Blocked: " + result.getMessage());
                }
                // WARN 模式继续执行
            }

            return true;

        } catch (JSQLParserException e) {
            logger.warn("Failed to parse SQL: " + sql, e);
            // 解析失败，假设安全
            return true;
        }
    }

    @Override
    protected String processSelect(Select select, int index, String sql, Object obj) {
        return sql; // 检测类不修改 SQL
    }

    @Override
    protected String processUpdate(Update update, int index, String sql, Object obj) {
        return sql;
    }

    @Override
    protected String processDelete(Delete delete, int index, String sql, Object obj) {
        return sql;
    }

    @Override
    protected String processInsert(Insert insert, int index, String sql, Object obj) {
        return sql;
    }
}
```

### 3.2 兜底类拦截器：SelectLimitInnerInterceptor

```java
/**
 * 自动为 SELECT 添加 LIMIT 的兜底拦截器
 * 这是新增功能，参考 MyBatis-Plus PaginationInnerInterceptor
 */
public class SelectLimitInnerInterceptor extends SqlGuardJsqlParserSupport
        implements SqlGuardInnerInterceptor {

    private Long defaultLimit = 1000L;
    private DbType dbType;
    private SqlGuardDialect dialect;

    public SelectLimitInnerInterceptor(DbType dbType) {
        super(null);
        this.dbType = dbType;
    }

    @Override
    public int getPriority() {
        return 100; // 兜底类拦截器优先级较低
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms,
                           Object parameter, RowBounds rowBounds,
                           ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        String sql = boundSql.getSql();

        // 1. 获取方言
        SqlGuardDialect dialect = findDialect(executor);

        // 2. 检查是否已有分页
        if (dialect.hasPagination(sql)) {
            logger.debug("SQL already has pagination: " + ms.getId());
            return; // 已有分页，不处理
        }

        // 3. 添加 LIMIT
        logger.info("Adding LIMIT to SQL: " + ms.getId());
        DialectModel model = dialect.addLimit(sql, defaultLimit);

        // 4. 替换 SQL
        PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
        mpBoundSql.sql(model.getDialectSql());

        // 5. 添加参数
        List<ParameterMapping> mappings = mpBoundSql.parameterMappings();
        Map<String, Object> additionalParameters = mpBoundSql.additionalParameters();
        model.consumers(mappings, ms.getConfiguration(), additionalParameters);
    }

    protected SqlGuardDialect findDialect(Executor executor) {
        if (dialect != null) {
            return dialect;
        }
        if (dbType != null) {
            dialect = DialectFactory.getDialect(dbType);
            return dialect;
        }
        // 从 JDBC URL 自动检测
        dialect = DialectFactory.getDialect(JdbcUtils.getDbType(executor));
        return dialect;
    }

    @Override
    protected String processSelect(Select select, int index, String sql, Object obj) {
        return sql; // 在 beforeQuery 中处理
    }

    @Override
    protected String processUpdate(Update update, int index, String sql, Object obj) {
        return sql;
    }

    @Override
    protected String processDelete(Delete delete, int index, String sql, Object obj) {
        return sql;
    }

    @Override
    protected String processInsert(Insert insert, int index, String sql, Object obj) {
        return sql;
    }
}
```

### 3.3 方言实现：MySqlGuardDialect

```java
/**
 * MySQL 方言实现
 */
public class MySqlGuardDialect implements SqlGuardDialect {

    @Override
    public DialectModel addLimit(String sql, long limit) {
        // MySQL: 简单追加 LIMIT
        String newSql = sql + " LIMIT ?";
        return new DialectModel(newSql, limit).setConsumer(true);
    }

    @Override
    public DialectModel rewritePagination(String sql, long offset, long limit,
                                         long maxOffset, long maxLimit) {
        // 检查并限制 offset 和 limit
        long safeOffset = Math.min(offset, maxOffset);
        long safeLimit = Math.min(limit, maxLimit);

        // 改写 SQL
        try {
            Select select = (Select) JsqlParserGlobal.parse(sql);
            PlainSelect plainSelect = (PlainSelect) select;

            // 移除现有的 LIMIT
            Limit existingLimit = plainSelect.getLimit();
            if (existingLimit != null) {
                plainSelect.setLimit(null);
            }

            // 添加新的安全 LIMIT
            String newSql = plainSelect.toString() + " LIMIT ?, ?";
            return new DialectModel(newSql, safeOffset, safeLimit).setConsumerChain();

        } catch (JSQLParserException e) {
            // 降级：使用正则替换
            String newSql = sql.replaceAll("LIMIT\\s+\\d+\\s*,\\s*\\d+", "LIMIT ?, ?");
            return new DialectModel(newSql, safeOffset, safeLimit).setConsumerChain();
        }
    }

    @Override
    public boolean hasPagination(String sql) {
        // 方式1：JSqlParser 解析
        try {
            Select select = (Select) JsqlParserGlobal.parse(sql);
            if (select instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) select;
                return plainSelect.getLimit() != null;
            }
        } catch (Exception e) {
            // 忽略解析错误
        }

        // 方式2：字符串匹配（降级）
        String upperSql = sql.toUpperCase();
        return upperSql.contains("LIMIT");
    }

    @Override
    public PaginationInfo extractPagination(String sql) {
        try {
            Select select = (Select) JsqlParserGlobal.parse(sql);
            if (select instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) select;
                Limit limit = plainSelect.getLimit();
                if (limit != null) {
                    long offset = limit.getOffset() != null ?
                        limit.getOffset().getValue() : 0L;
                    long rowCount = limit.getRowCount().getValue();
                    return new PaginationInfo(offset, rowCount, PaginationType.PHYSICAL);
                }
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return null;
    }
}
```

---

## 四、配置示例

### 4.1 Spring Boot 配置

```java
@Configuration
@ConditionalOnProperty(prefix = "sql-guard", name = "enabled", havingValue = "true")
public class SqlGuardAutoConfiguration {

    @Bean
    public SqlGuardInterceptor sqlGuardInterceptor(
            SqlGuardProperties properties,
            SqlSafetyValidator validator) {

        SqlGuardInterceptor interceptor = new SqlGuardInterceptor();

        // 1. 检测类拦截器（优先级 1-99）
        if (properties.getRules().isNoWhereClause()) {
            NoWhereClauseChecker checker = new NoWhereClauseChecker(...);
            SqlGuardNoWhereInnerInterceptor inner =
                new SqlGuardNoWhereInnerInterceptor(checker);
            inner.setStrategy(properties.getStrategy());
            interceptor.addInnerInterceptor(inner);
        }

        if (properties.getRules().isBlacklist()) {
            BlacklistFieldChecker checker = new BlacklistFieldChecker(...);
            SqlGuardFieldInnerInterceptor inner =
                new SqlGuardFieldInnerInterceptor(checker);
            interceptor.addInnerInterceptor(inner);
        }

        // ... 其他检测类拦截器

        // 2. 兜底类拦截器（优先级 100-199）
        if (properties.getFallback().isSelectLimit()) {
            SelectLimitInnerInterceptor selectLimit =
                new SelectLimitInnerInterceptor(properties.getDbType());
            selectLimit.setDefaultLimit(properties.getFallback().getDefaultLimit());
            interceptor.addInnerInterceptor(selectLimit);
        }

        return interceptor;
    }
}
```

### 4.2 application.yml 配置

```yaml
sql-guard:
  enabled: true
  db-type: MYSQL
  strategy: WARN  # WARN 或 BLOCK

  # 检测规则
  rules:
    no-where-clause: true
    blacklist: true
    whitelist: false
    no-pagination: true
    deep-pagination: true
    dummy-condition: true

  # 兜底策略
  fallback:
    select-limit: true
    default-limit: 1000
    rewrite-deep-pagination: true
    max-offset: 10000
```

### 4.3 XML 配置（传统 Spring）

```xml
<bean id="sqlGuardInterceptor"
      class="com.footstone.sqlguard.interceptor.mp.SqlGuardInterceptor">
    <property name="interceptors">
        <list>
            <!-- 检测类拦截器 -->
            <bean class="com.footstone.sqlguard.interceptor.mp.inner.SqlGuardNoWhereInnerInterceptor">
                <constructor-arg ref="noWhereClauseChecker"/>
                <property name="strategy" value="WARN"/>
            </bean>

            <!-- 兜底类拦截器 -->
            <bean class="com.footstone.sqlguard.interceptor.mp.inner.SelectLimitInnerInterceptor">
                <constructor-arg value="MYSQL"/>
                <property name="defaultLimit" value="1000"/>
            </bean>
        </list>
    </property>
</bean>

<!-- 注入到 SqlSessionFactory -->
<bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
    <property name="dataSource" ref="dataSource"/>
    <property name="plugins">
        <array>
            <ref bean="sqlGuardInterceptor"/>
        </array>
    </property>
</bean>
```

---

## 五、迁移路线图

### Phase 1: 基础框架（2-3天）

**目标**: 搭建新架构骨架

- [x] 创建 `sql-guard-mp` 模块
- [ ] 实现 `SqlGuardInterceptor`
- [ ] 实现 `SqlGuardInnerInterceptor` 接口
- [ ] 实现 `SqlGuardJsqlParserSupport` 基类
- [ ] 单元测试

**验收标准**:
- SqlGuardInterceptor 可以正确拦截 Executor 和 StatementHandler
- InnerInterceptor 的 willDo 和 before 方法都能被调用
- 优先级排序正常工作

### Phase 2: 方言支持（2-3天）

**目标**: 实现多数据库方言

- [ ] 实现 `SqlGuardDialect` 接口
- [ ] 实现 `MySqlGuardDialect`
- [ ] 实现 `OracleGuardDialect`
- [ ] 实现 `SqlServerGuardDialect`
- [ ] 实现 `PostgreSqlGuardDialect`
- [ ] 实现 `DialectFactory`
- [ ] 方言单元测试

**验收标准**:
- 每种方言的 addLimit 能正确生成 SQL
- hasPagination 能正确检测各种分页语法
- extractPagination 能正确提取分页参数

### Phase 3: SelectLimit 兜底拦截器（3-4天）

**目标**: 实现自动添加 LIMIT 功能

- [ ] 实现 `SelectLimitInnerInterceptor`
- [ ] 分页检测逻辑
- [ ] SQL 改写逻辑
- [ ] 参数绑定
- [ ] 集成测试（MySQL, Oracle, SQL Server）
- [ ] 性能测试

**验收标准**:
- 无分页的 SELECT 自动添加 LIMIT
- 已有分页的 SELECT 不修改
- 不同数据库生成正确的分页语法
- 参数正确绑定到 BoundSql

### Phase 4: 检测类拦截器迁移（3-4天）

**目标**: 将现有 Checker 迁移到新架构

- [ ] 实现 `SqlGuardNoWhereInnerInterceptor`
- [ ] 实现 `SqlGuardFieldInnerInterceptor` (黑白名单)
- [ ] 实现 `SqlGuardPaginationInnerInterceptor` (NoPagination + DeepPagination)
- [ ] 实现 `SqlGuardConditionInnerInterceptor` (DummyCondition)
- [ ] 集成测试

**验收标准**:
- 所有现有检测功能保持不变
- WARN 和 BLOCK 模式正常工作
- 错误信息准确

### Phase 5: Spring Boot 集成（2-3天）

**目标**: 提供自动配置

- [ ] 实现 `SqlGuardAutoConfiguration`
- [ ] 实现 `SqlGuardProperties`
- [ ] 创建 `spring.factories`
- [ ] 编写配置文档
- [ ] Spring Boot Starter 测试

**验收标准**:
- 能通过 application.yml 配置
- 自动注入到 MyBatis
- 不同配置组合都能正常工作

### Phase 6: 文档和示例（2-3天）

**目标**: 完善文档

- [ ] 架构设计文档
- [ ] 使用指南
- [ ] 配置参考
- [ ] 迁移指南
- [ ] Demo 项目
- [ ] FAQ

### Phase 7: 兼容性测试（2-3天）

**目标**: 确保兼容性

- [ ] MyBatis 3.4.x 测试
- [ ] MyBatis 3.5.x 测试
- [ ] MyBatis-Plus 3.x 测试
- [ ] 不同数据库测试（MySQL, Oracle, SQL Server, PostgreSQL）
- [ ] 不同连接池测试（HikariCP, Druid, C3P0）
- [ ] 压力测试

**预计总时间**: 16-24 工作日（3-5周）

---

## 六、风险评估

### 6.1 技术风险

| 风险 | 概率 | 影响 | 缓解措施 |
|-----|------|------|---------|
| JSqlParser 解析失败 | 中 | 中 | 提供降级方案（字符串匹配） |
| 性能影响 | 低 | 高 | 缓存解析结果，性能测试 |
| 方言实现不完整 | 中 | 中 | 优先支持主流数据库 |
| 与现有拦截器冲突 | 低 | 高 | 优先级控制，充分测试 |

### 6.2 兼容性风险

| 风险 | 概率 | 影响 | 缓解措施 |
|-----|------|------|---------|
| MyBatis 版本兼容 | 低 | 高 | 支持 3.4.x 和 3.5.x |
| MyBatis-Plus 兼容 | 低 | 中 | 与 MybatisPlusInterceptor 共存测试 |
| 数据库方言差异 | 中 | 中 | 充分的集成测试 |

---

## 七、后续优化方向

### 7.1 短期优化（3个月内）

1. **性能优化**:
   - SQL 解析结果缓存
   - 方言实例缓存
   - 避免重复解析

2. **功能增强**:
   - 支持更多数据库方言（达梦、人大金仓等）
   - 深度分页改写（自动改写 LIMIT 1000000 → LIMIT 1000）
   - 自定义兜底策略

### 7.2 长期优化（6-12个月）

1. **监控和统计**:
   - SQL 执行统计
   - 违规 SQL Top 10
   - 兜底改写统计

2. **动态配置**:
   - 支持配置中心（Apollo, Nacos）
   - 运行时动态调整规则
   - 按应用/环境不同配置

---

## 八、总结

### 8.1 核心优势

1. **统一架构**: 参考成熟的 MyBatis-Plus 插件设计
2. **检测 + 兜底**: 既能检测危险 SQL，又能自动添加安全限制
3. **可扩展**: InnerInterceptor 设计便于扩展
4. **多数据库**: 通过 Dialect 支持多种数据库
5. **向后兼容**: 复用现有 RuleChecker，不破坏现有功能

### 8.2 关键创新

1. **SelectLimitInnerInterceptor**: 自动为 SELECT 添加 LIMIT（业界首创）
2. **优先级控制**: 检测类优先，兜底类最后
3. **降级机制**: JSqlParser 失败时使用字符串匹配

---

**文档版本**: v1.0
**最后更新**: 2025-12-19
