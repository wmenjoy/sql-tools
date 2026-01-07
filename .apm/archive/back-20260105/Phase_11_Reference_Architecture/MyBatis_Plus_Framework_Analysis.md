# MyBatis-Plus 框架设计分析

**创建时间**: 2025-12-19
**分析目标**: 理解 MyBatis-Plus 的插件架构，为 SQL Guard 提供设计参考

---

## 一、核心架构设计

### 1.1 三层插件架构

```
┌─────────────────────────────────────────────────────────┐
│          MybatisPlusInterceptor                         │
│          (MyBatis Plugin 实现)                          │
│  @Intercepts: Executor, StatementHandler                │
└────────────────┬────────────────────────────────────────┘
                 │
                 │ 管理多个
                 ▼
┌─────────────────────────────────────────────────────────┐
│          InnerInterceptor (接口)                        │
│  - willDoQuery/willDoUpdate                             │
│  - beforeQuery/beforeUpdate                             │
│  - beforePrepare/beforeGetBoundSql                      │
└────────────────┬────────────────────────────────────────┘
                 │
                 │ 多种实现
                 ▼
┌──────────────────────────────────────────────────────────┐
│  PaginationInnerInterceptor  (分页)                     │
│  TenantLineInnerInterceptor  (多租户)                   │
│  DataPermissionInterceptor   (数据权限)                 │
│  BlockAttackInnerInterceptor (防全表更新/删除)           │
│  IllegalSQLInnerInterceptor  (非法SQL检测-已废弃)        │
└──────────────────────────────────────────────────────────┘
```

### 1.2 InnerInterceptor 接口设计

```java
public interface InnerInterceptor {
    // 1. 判断阶段 - 是否执行 query
    default boolean willDoQuery(Executor executor, MappedStatement ms,
        Object parameter, RowBounds rowBounds, ResultHandler resultHandler,
        BoundSql boundSql) throws SQLException {
        return true; // false = 不执行query，直接返回空集合
    }

    // 2. 预处理阶段 - 在 query 执行前修改 SQL
    default void beforeQuery(Executor executor, MappedStatement ms,
        Object parameter, RowBounds rowBounds, ResultHandler resultHandler,
        BoundSql boundSql) throws SQLException {
        // 可以修改 boundSql 中的 SQL
    }

    // 3. 判断阶段 - 是否执行 update
    default boolean willDoUpdate(Executor executor, MappedStatement ms,
        Object parameter) throws SQLException {
        return true; // false = 不执行update，返回 -1
    }

    // 4. 预处理阶段 - 在 update 执行前修改 SQL
    default void beforeUpdate(Executor executor, MappedStatement ms,
        Object parameter) throws SQLException {
        // 可以修改 SQL
    }

    // 5. Statement 准备阶段
    default void beforePrepare(StatementHandler sh, Connection connection,
        Integer transactionTimeout) {
        // 在 Statement 准备时拦截
    }

    // 6. 获取 BoundSql 阶段
    default void beforeGetBoundSql(StatementHandler sh) {
        // 仅 BatchExecutor 和 ReuseExecutor 会调用
    }

    // 7. 属性配置
    default void setProperties(Properties properties) {
        // 支持 Properties 配置
    }
}
```

**关键特性**:
- **判断 + 预处理** 两阶段设计：先判断是否执行，再修改 SQL
- **SELECT 和 UPDATE/DELETE/INSERT** 分别处理
- **多个拦截点**：Executor 层面 + StatementHandler 层面
- **全部都是 default 方法**：子类按需实现

---

## 二、分页插件实现分析

### 2.1 PaginationInnerInterceptor 核心逻辑

#### 配置属性

```java
public class PaginationInnerInterceptor implements InnerInterceptor {
    protected boolean overflow = false;        // 溢出总页数后是否处理
    protected Long maxLimit;                   // 单页分页条数限制
    private DbType dbType;                     // 数据库类型
    private IDialect dialect;                  // 方言实现类
    protected boolean optimizeJoin = true;     // count SQL 优化掉 join
}
```

#### 执行流程

```
1. willDoQuery()
   ├── 提取 IPage 参数
   ├── 如果需要 count: 生成 count SQL
   │   ├── 优化 count SQL（移除 ORDER BY, 优化 LEFT JOIN）
   │   └── 执行 count 查询，设置 page.total
   └── 判断是否继续执行 query
       └── total = 0 或超出页数 → 返回 false（不执行query）

2. beforeQuery()
   ├── 提取 IPage 参数
   ├── 拼接 ORDER BY（如果 page 中有排序）
   ├── 获取 IDialect 方言
   ├── dialect.buildPaginationSql()
   │   ├── MySQL:  原SQL + LIMIT offset, limit
   │   ├── Oracle: 嵌套 ROWNUM 查询
   │   └── SQL Server: 原SQL + OFFSET offset ROWS FETCH NEXT limit ROWS ONLY
   └── 替换 boundSql.sql
```

### 2.2 数据库方言设计

#### IDialect 接口

```java
public interface IDialect {
    /**
     * 组装分页语句
     * @param originalSql 原始语句
     * @param offset      偏移量
     * @param limit       界限
     * @return 分页模型
     */
    DialectModel buildPaginationSql(String originalSql, long offset, long limit);
}
```

#### 典型实现

**MySQL**:
```java
public class MySqlDialect implements IDialect {
    @Override
    public DialectModel buildPaginationSql(String originalSql, long offset, long limit) {
        StringBuilder sql = new StringBuilder(originalSql).append(" LIMIT ").append(FIRST_MARK);
        if (offset != 0L) {
            sql.append(StringPool.COMMA).append(SECOND_MARK);
            return new DialectModel(sql.toString(), offset, limit).setConsumerChain();
        } else {
            return new DialectModel(sql.toString(), limit).setConsumer(true);
        }
    }
}
// 结果: SELECT * FROM user LIMIT ?, ?
```

**Oracle**:
```java
public class OracleDialect implements IDialect {
    @Override
    public DialectModel buildPaginationSql(String originalSql, long offset, long limit) {
        limit = (offset >= 1) ? (offset + limit) : limit;
        String sql = "SELECT * FROM ( SELECT TMP.*, ROWNUM ROW_ID FROM ( " +
            originalSql + " ) TMP WHERE ROWNUM <=" + FIRST_MARK + ") WHERE ROW_ID > " + SECOND_MARK;
        return new DialectModel(sql, limit, offset).setConsumerChain();
    }
}
// 结果: SELECT * FROM (SELECT TMP.*, ROWNUM ROW_ID FROM (原SQL) TMP WHERE ROWNUM <= ?) WHERE ROW_ID > ?
```

**SQL Server / Oracle 12c**:
```java
public class Oracle12cDialect implements IDialect {
    @Override
    public DialectModel buildPaginationSql(String originalSql, long offset, long limit) {
        String sql = originalSql + " OFFSET " + FIRST_MARK + " ROWS FETCH NEXT " + SECOND_MARK + " ROWS ONLY";
        return new DialectModel(sql, offset, limit).setConsumerChain();
    }
}
// 结果: SELECT * FROM user OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
```

#### 方言工厂

```java
public class DialectFactory {
    private static final Map<DbType, IDialect> DIALECT_ENUM_MAP = new EnumMap<>(DbType.class);

    public static IDialect getDialect(DbType dbType) {
        // 1. 缓存中查找
        // 2. 根据 dbType 选择方言实现
        //    - MySQL 及同类型 (MariaDB, TiDB等) → MySqlDialect
        //    - Oracle 及同类型 (DM达梦, GaussDB等) → OracleDialect
        //    - PostgreSQL 及同类型 (KingBase人大金仓等) → PostgreDialect
        //    - SQL Server / Oracle 12c / Firebird / Derby → Oracle12cDialect
        // 3. 缓存方言实例
    }
}
```

### 2.3 Count SQL 优化

**优化策略**:

```java
public String autoCountSql(IPage<?> page, String sql) {
    // 1. 使用 JSqlParser 解析 SQL
    Select select = (Select) JsqlParserGlobal.parse(sql);
    PlainSelect plainSelect = (PlainSelect) select;

    // 2. 移除 ORDER BY（非分组情况下count不需要排序）
    plainSelect.setOrderByElements(null);

    // 3. 包含 DISTINCT 或 GROUP BY → 不优化，使用低级别 count
    if (distinct != null || groupBy != null) {
        return lowLevelCountSql(sql); // SELECT COUNT(*) FROM (原SQL) tmp
    }

    // 4. 优化 LEFT JOIN
    if (optimizeJoin) {
        // 如果 LEFT JOIN 的表在 WHERE 条件中未使用 → 移除 join
        // 检查逻辑：
        // - 必须是 LEFT JOIN（INNER JOIN不能移除）
        // - join 的表别名不能出现在 WHERE 条件中
        // - join ON 条件中不能有参数（?）
        if (canRemoveJoin) {
            plainSelect.setJoins(null);
        }
    }

    // 5. 替换 SELECT 列为 COUNT(*)
    plainSelect.setSelectItems(COUNT_SELECT_ITEM); // COUNT(*) AS total

    return select.toString();
}
```

**降级策略**:
```java
protected String lowLevelCountSql(String originalSql) {
    return "SELECT COUNT(*) FROM (" + originalSql + ") TOTAL";
}
```

---

## 三、危险 SQL 拦截实现

### 3.1 BlockAttackInnerInterceptor

**功能**: 防止全表更新和删除

```java
public class BlockAttackInnerInterceptor extends JsqlParserSupport implements InnerInterceptor {
    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        // 仅拦截 UPDATE 和 DELETE
        if (sct == SqlCommandType.UPDATE || sct == SqlCommandType.DELETE) {
            // 解析并检查 WHERE 条件
            parserMulti(boundSql.getSql(), null);
        }
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        checkWhere(delete.getTable().getName(), delete.getWhere(), "Prohibition of full table deletion");
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        checkWhere(update.getTable().getName(), update.getWhere(), "Prohibition of table update operation");
    }

    protected void checkWhere(String tableName, Expression where, String ex) {
        // 如果 fullMatch(where) 返回 true → 抛出异常
        Assert.isFalse(this.fullMatch(where, logicField), ex);
    }

    private boolean fullMatch(Expression where, String logicField) {
        if (where == null) return true; // 没有 WHERE → 全表操作

        // 检查常见的无效 WHERE 条件
        if (where instanceof EqualsTo) {
            // 1=1
            return leftExpression.equals(rightExpression);
        } else if (where instanceof NotEqualsTo) {
            // 1!=2
            return !leftExpression.equals(rightExpression);
        } else if (where instanceof OrExpression) {
            // WHERE a=1 OR b=2 → 递归检查
            return fullMatch(left, logicField) || fullMatch(right, logicField);
        } else if (where instanceof AndExpression) {
            // WHERE a=1 AND b=2 → 递归检查
            return fullMatch(left, logicField) && fullMatch(right, logicField);
        }

        return false; // 其他情况认为有效
    }
}
```

### 3.2 IllegalSQLInnerInterceptor（已废弃）

**功能**: 拦截垃圾 SQL（不使用索引、使用 OR、使用函数等）

**检查项**:
1. WHERE 条件必须存在
2. 不能使用 OR 关键字
3. 不能使用 != 关键字
4. 不能使用 NOT 关键字
5. WHERE 字段必须使用索引
6. 不能在字段上使用函数
7. 不能使用子查询

**废弃原因**（v3.5.10）:
- 实用性不高
- 语法分析太差（依赖 JDBC DatabaseMetaData 获取索引信息）
- 性能开销大

---

## 四、JSqlParser 支持基类

### 4.1 JsqlParserSupport 设计

```java
public abstract class JsqlParserSupport {
    // 解析单个 SQL 语句
    public String parserSingle(String sql, Object obj) {
        Statement statement = JsqlParserGlobal.parse(sql);
        return processParser(statement, 0, sql, obj);
    }

    // 解析多个 SQL 语句（分号分隔）
    public String parserMulti(String sql, Object obj) {
        Statements statements = JsqlParserGlobal.parseStatements(sql);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Statement statement : statements) {
            if (i > 0) sb.append(";");
            sb.append(processParser(statement, i, sql, obj));
            i++;
        }
        return sb.toString();
    }

    protected String processParser(Statement statement, int index, String sql, Object obj) {
        if (statement instanceof Insert) {
            this.processInsert((Insert) statement, index, sql, obj);
        } else if (statement instanceof Select) {
            this.processSelect((Select) statement, index, sql, obj);
        } else if (statement instanceof Update) {
            this.processUpdate((Update) statement, index, sql, obj);
        } else if (statement instanceof Delete) {
            this.processDelete((Delete) statement, index, sql, obj);
        }
        return statement.toString(); // 修改后的SQL
    }

    // 子类实现这些方法来处理不同类型的SQL
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        throw new UnsupportedOperationException();
    }
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        throw new UnsupportedOperationException();
    }
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        throw new UnsupportedOperationException();
    }
    protected void processSelect(Select select, int index, String sql, Object obj) {
        throw new UnsupportedOperationException();
    }
}
```

### 4.2 BaseMultiTableInnerInterceptor

**用途**: 处理多表 SQL（JOIN, 子查询等）

```java
public abstract class BaseMultiTableInnerInterceptor extends JsqlParserSupport {
    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        // 处理 PlainSelect
        PlainSelect plainSelect = (PlainSelect) select;

        // 1. 处理主表
        FromItem fromItem = plainSelect.getFromItem();
        processTable(fromItem, ...);

        // 2. 处理 JOIN 的表
        List<Join> joins = plainSelect.getJoins();
        for (Join join : joins) {
            processTable(join.getRightItem(), ...);
        }

        // 3. 处理子查询
        if (fromItem instanceof ParenthesedSelect) {
            processSelectBody(((ParenthesedSelect) fromItem).getSelect(), ...);
        }

        // 4. 处理 WITH 子句
        List<WithItem<?>> withItemsList = select.getWithItemsList();
        withItemsList.forEach(withItem -> processSelectBody(withItem.getSelect(), ...));
    }

    protected abstract void processTable(Table table, ...);
}
```

---

## 五、SQL Guard 设计建议

### 5.1 参考 MyBatis-Plus 的架构模式

#### ✅ 应该采用的设计

1. **三层架构**:
   ```
   SqlGuardInterceptor (统一入口)
     ↓
   SqlGuardInnerInterceptor (抽象接口)
     ↓
   具体检查器实现
   ```

2. **InnerInterceptor 接口设计**:
   ```java
   public interface SqlGuardInnerInterceptor {
       // 1. 预检查阶段（可以阻止执行）
       default boolean willDoQuery(...) { return true; }
       default boolean willDoUpdate(...) { return true; }

       // 2. SQL 改写阶段（可以修改SQL，如添加LIMIT）
       default void beforeQuery(...) {}
       default void beforeUpdate(...) {}

       // 3. 属性配置
       default void setProperties(Properties properties) {}
   }
   ```

3. **JsqlParser 支持基类**:
   ```java
   public abstract class SqlGuardJsqlParserSupport {
       protected abstract void processSelect(Select select, ...);
       protected abstract void processUpdate(Update update, ...);
       protected abstract void processDelete(Delete delete, ...);
   }
   ```

4. **数据库方言支持**:
   ```java
   public interface SqlDialect {
       DialectModel addLimit(String sql, long limit);
       DialectModel rewritePagination(String sql, long offset, long limit);
   }
   ```

#### ❌ 不应该采用的设计

1. **不要使用 IllegalSQLInnerInterceptor 的方式**:
   - ❌ 通过 JDBC DatabaseMetaData 获取索引信息（性能差）
   - ❌ 强制要求必须使用索引（过于严格，不实用）
   - ❌ 禁止 OR、函数等（限制太多）

2. **不要过度优化**:
   - ❌ 不要像 PaginationInnerInterceptor 那样优化 COUNT SQL
   - ✅ SQL Guard 的目标是安全检测，不是性能优化

### 5.2 具体实现建议

#### 模块结构

```
sql-guard-mybatis-plus/
├── SqlGuardInterceptor.java              # 统一入口
├── inner/
│   ├── SqlGuardInnerInterceptor.java     # 接口定义
│   ├── NoWhereClauseInnerInterceptor.java
│   ├── NoPaginationInnerInterceptor.java
│   ├── BlacklistFieldInnerInterceptor.java
│   ├── LargePageSizeInnerInterceptor.java
│   └── SelectLimitInnerInterceptor.java  # 新增：自动添加LIMIT
├── support/
│   └── SqlGuardJsqlParserSupport.java    # JSqlParser 基类
└── dialect/
    ├── SqlDialect.java                    # 方言接口
    ├── MySqlDialect.java
    ├── OracleDialect.java
    └── SqlServerDialect.java
```

#### SelectLimitInnerInterceptor 实现

```java
/**
 * 自动为 SELECT 添加 LIMIT 的拦截器
 * 参考 PaginationInnerInterceptor 的实现
 */
public class SelectLimitInnerInterceptor extends SqlGuardJsqlParserSupport
        implements SqlGuardInnerInterceptor {

    private Long defaultLimit = 1000L; // 默认限制
    private DbType dbType;
    private SqlDialect dialect;

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        String sql = boundSql.getSql();

        // 1. 检查是否已有分页
        if (hasPagination(sql)) {
            return; // 已有分页，不处理
        }

        // 2. 获取方言
        SqlDialect dialect = findDialect(executor);

        // 3. 添加 LIMIT
        DialectModel model = dialect.addLimit(sql, defaultLimit);

        // 4. 替换 SQL
        PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
        mpBoundSql.sql(model.getDialectSql());

        // 5. 添加参数
        model.consumers(mpBoundSql.parameterMappings(), ...);
    }

    private boolean hasPagination(String sql) {
        try {
            Select select = (Select) JsqlParserGlobal.parse(sql);
            PlainSelect plainSelect = (PlainSelect) select;

            // 检查各种分页语法
            // - MySQL: LIMIT
            // - Oracle: ROWNUM
            // - SQL Server: OFFSET...FETCH
            // - 通用: 检查 SQL 字符串

            return hasLimit || hasRownum || hasOffset;
        } catch (Exception e) {
            // 解析失败，假设没有分页
            return false;
        }
    }
}
```

#### 方言实现

```java
public class MySqlDialect implements SqlDialect {
    @Override
    public DialectModel addLimit(String sql, long limit) {
        // 简单追加 LIMIT
        String newSql = sql + " LIMIT " + LIMIT_PLACEHOLDER;
        return new DialectModel(newSql, limit);
    }

    @Override
    public DialectModel rewritePagination(String sql, long offset, long limit) {
        // 已有分页，重写为安全的分页
        // 例如：LIMIT 1000000 → LIMIT 1000
        // ...
    }
}

public class OracleDialect implements SqlDialect {
    @Override
    public DialectModel addLimit(String sql, long limit) {
        // Oracle 11g: 使用 ROWNUM
        String newSql = "SELECT * FROM (" + sql + ") WHERE ROWNUM <= " + LIMIT_PLACEHOLDER;
        return new DialectModel(newSql, limit);
    }
}

public class Oracle12cDialect implements SqlDialect {
    @Override
    public DialectModel addLimit(String sql, long limit) {
        // Oracle 12c+: 使用 FETCH FIRST
        String newSql = sql + " FETCH FIRST " + LIMIT_PLACEHOLDER + " ROWS ONLY";
        return new DialectModel(newSql, limit);
    }
}
```

### 5.3 配置示例

#### Spring Boot 配置

```java
@Configuration
public class SqlGuardConfig {

    @Bean
    public SqlGuardInterceptor sqlGuardInterceptor() {
        SqlGuardInterceptor interceptor = new SqlGuardInterceptor();

        // 1. 检测类拦截器
        interceptor.addInnerInterceptor(new NoWhereClauseInnerInterceptor());
        interceptor.addInnerInterceptor(new BlacklistFieldInnerInterceptor());
        interceptor.addInnerInterceptor(new NoPaginationInnerInterceptor());

        // 2. 兜底类拦截器（SQL 改写）
        SelectLimitInnerInterceptor selectLimit = new SelectLimitInnerInterceptor(DbType.MYSQL);
        selectLimit.setDefaultLimit(1000L);
        interceptor.addInnerInterceptor(selectLimit);

        return interceptor;
    }
}
```

#### XML 配置

```xml
<bean id="sqlGuardInterceptor" class="com.footstone.sqlguard.interceptor.mp.SqlGuardInterceptor">
    <property name="interceptors">
        <list>
            <bean class="...NoWhereClauseInnerInterceptor"/>
            <bean class="...SelectLimitInnerInterceptor">
                <property name="dbType" value="MYSQL"/>
                <property name="defaultLimit" value="1000"/>
            </bean>
        </list>
    </property>
</bean>
```

---

## 六、关键技术要点总结

### 6.1 MyBatis-Plus 的优秀设计

| 设计点 | 说明 | SQL Guard 应用 |
|-------|------|---------------|
| **InnerInterceptor 接口** | 统一的拦截器抽象 | ✅ 直接参考 |
| **JsqlParserSupport** | SQL 解析基类 | ✅ 直接参考 |
| **IDialect + DialectFactory** | 数据库方言支持 | ✅ 用于 selectLimit |
| **两阶段设计** | willDo + before | ✅ 检测 + 兜底 |
| **DialectModel** | 参数化 SQL 模型 | ✅ 用于 SQL 改写 |

### 6.2 JSqlParser 使用模式

```java
// 1. 解析 SQL
Select select = (Select) JsqlParserGlobal.parse(sql);
PlainSelect plainSelect = (PlainSelect) select;

// 2. 分析 SQL 结构
Expression where = plainSelect.getWhere();
List<OrderByElement> orderBy = plainSelect.getOrderByElements();
List<Join> joins = plainSelect.getJoins();

// 3. 修改 SQL
plainSelect.setWhere(newWhere);
plainSelect.setOrderByElements(newOrderBy);

// 4. 生成新 SQL
String newSql = select.toString();
```

### 6.3 BoundSql 修改模式

```java
// 获取 MPBoundSql
PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);

// 修改 SQL
mpBoundSql.sql(newSql);

// 添加参数
List<ParameterMapping> mappings = mpBoundSql.parameterMappings();
mappings.add(new ParameterMapping.Builder(configuration, "limit", Long.class).build());

// 添加参数值
Map<String, Object> additionalParameters = mpBoundSql.additionalParameters();
additionalParameters.put("limit", 1000L);
```

### 6.4 数据库类型检测

```java
// 方式1：从配置获取
DbType dbType = this.dbType;

// 方式2：从 JDBC URL 检测
DbType dbType = JdbcUtils.getDbType(executor);

// 方式3：从 DatabaseMetaData 检测
DatabaseMetaData metaData = connection.getMetaData();
String databaseProductName = metaData.getDatabaseProductName();
```

---

## 七、实施路线图

### Phase 1: 基础框架（1-2天）

- [ ] 创建 SqlGuardInterceptor
- [ ] 创建 SqlGuardInnerInterceptor 接口
- [ ] 创建 SqlGuardJsqlParserSupport 基类
- [ ] 单元测试

### Phase 2: 检测类拦截器迁移（2-3天）

- [ ] NoWhereClauseInnerInterceptor
- [ ] NoPaginationInnerInterceptor
- [ ] BlacklistFieldInnerInterceptor
- [ ] 集成测试

### Phase 3: 方言支持（2-3天）

- [ ] SqlDialect 接口
- [ ] MySqlDialect / OracleDialect / SqlServerDialect
- [ ] DialectFactory
- [ ] 方言测试

### Phase 4: SelectLimit 实现（3-4天）

- [ ] SelectLimitInnerInterceptor
- [ ] 分页检测逻辑
- [ ] SQL 改写逻辑
- [ ] 参数绑定
- [ ] 集成测试

### Phase 5: 文档和示例（1-2天）

- [ ] 使用文档
- [ ] 配置示例
- [ ] Demo 项目

**预计总时间**: 10-15 工作日

---

## 八、注意事项

### 8.1 与现有代码的兼容性

1. **保留现有 RuleChecker 体系**:
   - 现有的 `AbstractRuleChecker` 和 `RuleCheckerOrchestrator` 继续保留
   - `SqlGuardInnerInterceptor` 内部可以调用现有的 RuleChecker
   - 逐步迁移，不破坏现有功能

2. **配置兼容性**:
   - 支持 Spring Boot 自动配置
   - 支持传统 XML 配置
   - 支持 Properties 文件配置

### 8.2 性能考虑

1. **SQL 解析缓存**:
   ```java
   private static final Map<String, Boolean> PAGINATION_CACHE = new ConcurrentHashMap<>();
   ```

2. **方言缓存**:
   ```java
   private static final Map<DbType, SqlDialect> DIALECT_CACHE = new EnumMap<>(DbType.class);
   ```

3. **避免重复解析**:
   - 多个 InnerInterceptor 如果都需要解析，共享解析结果

### 8.3 异常处理

参考 MyBatis-Plus 的方式：

```java
try {
    Statement statement = JsqlParserGlobal.parse(sql);
    // ...
} catch (JSQLParserException e) {
    // 解析失败，降级处理
    logger.warn("Failed to parse SQL: " + sql, e);
    return; // 或者使用简单的字符串匹配
}
```

---

## 九、参考资料

1. **MyBatis-Plus 官方文档**:
   - [分页插件](https://baomidou.com/plugins/pagination/)
   - [插件核心](https://baomidou.com/plugins/)

2. **MyBatis-Plus 源码**:
   - [GitHub - baomidou/mybatis-plus](https://github.com/baomidou/mybatis-plus)
   - 版本: 3.0 branch

3. **JSqlParser 文档**:
   - [GitHub - JSQLParser/JSqlParser](https://github.com/JSQLParser/JSqlParser)

4. **相关文章**:
   - [Introduction to MyBatis-Plus | Baeldung](https://www.baeldung.com/mybatis-plus-introduction)

---

**文档版本**: v1.0
**最后更新**: 2025-12-19
