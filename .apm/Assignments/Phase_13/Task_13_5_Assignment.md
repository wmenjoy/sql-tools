---
task_ref: "Task 13.5 - SelectLimitInnerInterceptor Implementation"
agent_assignment: "Agent_Advanced_Interceptor"
memory_log_path: ".apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_5_SelectLimitInnerInterceptor_Implementation.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
phase: 13
task_id: 13.5
estimated_duration: 2 days
dependencies: [Task_13.1, Task_13.6]
parallel_with: [Task_13.2, Task_13.3, Task_13.4]
---

# Task 13.5 Assignment: SelectLimitInnerInterceptor Implementation

## 任务目标

实现 `SelectLimitInnerInterceptor` 扩展 JsqlParserSupport 进行自动 LIMIT 添加降级，getPriority() 返回 150（在 checks 和 rewrites 之间），processSelect() 检查 Statement 是否已有 LIMIT/OFFSET 或 RowBounds 表示分页，如果未检测到分页则使用数据库方言添加 LIMIT 子句（支持 MySQL/Oracle/SQL Server/PostgreSQL），创建 SqlGuardDialect 接口和实现，实现 DialectFactory 从 Connection 元数据自动检测数据库类型。

---

## 背景说明

### 为什么需要 SelectLimitInnerInterceptor？

**安全降级策略**:
```
业务SQL: SELECT * FROM users;  ❌ 全表扫描
           ↓ (Dangerous!)
SelectLimitInnerInterceptor 自动降级
           ↓
安全SQL: SELECT * FROM users LIMIT 1000;  ✅ 限制返回行数
```

**场景**:
1. **开发环境测试**: 防止误查询大表导致内存溢出
2. **生产环境降级**: RuleChecker 检测到危险 SQL（无 WHERE、无分页）但策略为 WARN/LOG 时，自动添加 LIMIT 作为保护措施
3. **防御性编程**: 即使 RuleChecker 未检测到问题，SelectLimitInnerInterceptor 作为最后一道防线

**Priority 150 的原因**:
- Priority 10: SqlGuardCheckInnerInterceptor (检查)
- Priority **150**: SelectLimitInnerInterceptor (降级) ⭐
- Priority 200: SqlGuardRewriteInnerInterceptor (重写)

**执行顺序**:
```
1. Check (10) → 检测违规
2. Fallback (150) → 自动降级（本任务）
3. Rewrite (200) → 自定义重写
```

---

### 多数据库方言支持

**为什么需要方言**:
不同数据库的分页语法不同：

| 数据库 | 分页语法 | 示例 |
|--------|---------|------|
| **MySQL** | `LIMIT` | `SELECT * FROM users LIMIT 1000` |
| **PostgreSQL** | `LIMIT` | `SELECT * FROM users LIMIT 1000` |
| **Oracle** | `ROWNUM` 子查询 | `SELECT * FROM (SELECT * FROM users) WHERE ROWNUM <= 1000` |
| **SQL Server** | `TOP` | `SELECT TOP 1000 * FROM users` |

**SqlGuardDialect 接口**:
```java
public interface SqlGuardDialect {
    /**
     * Applies LIMIT clause to SELECT statement.
     *
     * @param select SELECT statement to modify
     * @param limit  Maximum row count
     */
    void applyLimit(Select select, long limit);

    /**
     * Returns database type identifier (e.g., "MySQL", "Oracle").
     */
    String getDatabaseType();
}
```

---

### JsqlParserSupport 扩展

**MyBatis-Plus JsqlParserSupport**:
```java
// MyBatis-Plus provides JsqlParserSupport abstract class
public abstract class JsqlParserSupport {
    /**
     * Override this method to process SELECT statements.
     */
    protected void processSelect(Select select, int index, String sql, Object obj) {
        // Subclass implements pagination logic here
    }
}
```

**SelectLimitInnerInterceptor 扩展**:
```java
public class SelectLimitInnerInterceptor extends JsqlParserSupport
        implements SqlGuardInnerInterceptor {

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        // 1. Check existing LIMIT
        // 2. Check RowBounds
        // 3. Apply dialect if needed
    }

    @Override
    public int getPriority() {
        return 150; // Fallback interceptor
    }
}
```

---

## 实现要求

### 实现顺序（分步骤）

**Phase 1: 接口和 MySQL 方言**（优先）
1. 实现 SqlGuardDialect 接口
2. 实现 MySQLDialect（参考实现）
3. 实现 SelectLimitInnerInterceptor 核心（仅支持 MySQL）
4. 编写 MySQL 测试验证

**Phase 2: 其他数据库方言**（迭代）
5. 实现 OracleDialect
6. 实现 SQLServerDialect
7. 实现 PostgreSQLDialect
8. 编写多方言测试

**Phase 3: 自动检测**（最后）
9. 实现 DialectFactory
10. 集成 DialectFactory 到 SelectLimitInnerInterceptor

---

### 1. SqlGuardDialect 接口

**包路径**: `com.footstone.sqlguard.dialect`

**接口定义**:

```java
package com.footstone.sqlguard.dialect;

import net.sf.jsqlparser.statement.select.Select;

/**
 * Database dialect interface for applying LIMIT clauses in database-specific syntax.
 *
 * <h2>Purpose</h2>
 * <p>Abstracts database-specific pagination syntax differences, allowing
 * {@code SelectLimitInnerInterceptor} to support multiple databases without conditional logic.
 *
 * <h2>Supported Databases</h2>
 * <ul>
 *   <li>MySQL: {@code LIMIT n}</li>
 *   <li>PostgreSQL: {@code LIMIT n}</li>
 *   <li>Oracle: {@code WHERE ROWNUM <= n}</li>
 *   <li>SQL Server: {@code TOP n}</li>
 * </ul>
 *
 * @see MySQLDialect
 * @see OracleDialect
 * @see SQLServerDialect
 * @see PostgreSQLDialect
 * @since 1.1.0
 */
public interface SqlGuardDialect {

    /**
     * Applies LIMIT clause to SELECT statement using database-specific syntax.
     *
     * <p>Modifies the {@link Select} statement in-place to add pagination limiting
     * the result set to the specified row count.
     *
     * @param select SELECT statement to modify (will be modified in-place)
     * @param limit  Maximum number of rows to return
     */
    void applyLimit(Select select, long limit);

    /**
     * Returns the database type identifier for this dialect.
     *
     * <p>Used for logging and debugging. Should match database product name
     * (e.g., "MySQL", "Oracle", "PostgreSQL", "Microsoft SQL Server").
     *
     * @return Database type string (e.g., "MySQL")
     */
    String getDatabaseType();
}
```

---

### 2. Dialect 实现类

#### 2.1 MySQLDialect

**包路径**: `com.footstone.sqlguard.dialect.impl`

```java
package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

/**
 * MySQL dialect for applying LIMIT clauses.
 *
 * <h2>Syntax</h2>
 * <pre>
 * SELECT * FROM users LIMIT 1000;
 * </pre>
 *
 * @since 1.1.0
 */
public class MySQLDialect implements SqlGuardDialect {

    @Override
    public void applyLimit(Select select, long limit) {
        if (select.getSelectBody() instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

            // Create LIMIT clause
            Limit limitClause = new Limit();
            limitClause.setRowCount(new LongValue(limit));

            plainSelect.setLimit(limitClause);
        }
    }

    @Override
    public String getDatabaseType() {
        return "MySQL";
    }
}
```

#### 2.2 PostgreSQLDialect

```java
package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

/**
 * PostgreSQL dialect for applying LIMIT clauses.
 *
 * <h2>Syntax</h2>
 * <pre>
 * SELECT * FROM users LIMIT 1000;
 * </pre>
 *
 * <p>PostgreSQL uses the same syntax as MySQL for LIMIT clauses.
 *
 * @since 1.1.0
 */
public class PostgreSQLDialect implements SqlGuardDialect {

    @Override
    public void applyLimit(Select select, long limit) {
        if (select.getSelectBody() instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

            Limit limitClause = new Limit();
            limitClause.setRowCount(new LongValue(limit));

            plainSelect.setLimit(limitClause);
        }
    }

    @Override
    public String getDatabaseType() {
        return "PostgreSQL";
    }
}
```

#### 2.3 OracleDialect

```java
package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SubSelect;

/**
 * Oracle dialect for applying LIMIT clauses using ROWNUM.
 *
 * <h2>Syntax</h2>
 * <pre>
 * SELECT * FROM (
 *     SELECT * FROM users
 * ) WHERE ROWNUM <= 1000;
 * </pre>
 *
 * <p>Oracle uses ROWNUM pseudo-column for pagination, requiring subquery wrapping.
 *
 * @since 1.1.0
 */
public class OracleDialect implements SqlGuardDialect {

    @Override
    public void applyLimit(Select select, long limit) {
        if (select.getSelectBody() instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

            // Wrap original select in subquery
            SubSelect subSelect = new SubSelect();
            subSelect.setSelectBody(plainSelect);

            // Create outer select with ROWNUM condition
            PlainSelect outerSelect = new PlainSelect();
            outerSelect.setFromItem(subSelect);
            outerSelect.addSelectItems(new net.sf.jsqlparser.statement.select.AllColumns());

            // Add WHERE ROWNUM <= limit
            MinorThanEquals rownumCondition = new MinorThanEquals();
            rownumCondition.setLeftExpression(new Column("ROWNUM"));
            rownumCondition.setRightExpression(new LongValue(limit));
            outerSelect.setWhere(rownumCondition);

            // Replace original select body
            select.setSelectBody(outerSelect);
        }
    }

    @Override
    public String getDatabaseType() {
        return "Oracle";
    }
}
```

#### 2.4 SQLServerDialect

```java
package com.footstone.sqlguard.dialect.impl;

import com.footstone.sqlguard.dialect.SqlGuardDialect;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.Top;

/**
 * SQL Server dialect for applying LIMIT clauses using TOP.
 *
 * <h2>Syntax</h2>
 * <pre>
 * SELECT TOP 1000 * FROM users;
 * </pre>
 *
 * @since 1.1.0
 */
public class SQLServerDialect implements SqlGuardDialect {

    @Override
    public void applyLimit(Select select, long limit) {
        if (select.getSelectBody() instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

            // Create TOP clause
            Top top = new Top();
            top.setExpression(new LongValue(limit));

            plainSelect.setTop(top);
        }
    }

    @Override
    public String getDatabaseType() {
        return "SQL Server";
    }
}
```

---

### 3. DialectFactory

**包路径**: `com.footstone.sqlguard.dialect`

```java
package com.footstone.sqlguard.dialect;

import com.footstone.sqlguard.dialect.impl.MySQLDialect;
import com.footstone.sqlguard.dialect.impl.OracleDialect;
import com.footstone.sqlguard.dialect.impl.PostgreSQLDialect;
import com.footstone.sqlguard.dialect.impl.SQLServerDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for auto-detecting database type and returning appropriate SqlGuardDialect.
 *
 * <h2>Detection Strategy</h2>
 * <p>Reads {@code DatabaseMetaData.getDatabaseProductName()} from JDBC Connection
 * and maps to corresponding dialect implementation.
 *
 * <h2>Caching</h2>
 * <p>Detected dialects are cached per DataSource to avoid repeated metadata queries.
 *
 * @since 1.1.0
 */
public class DialectFactory {

    private static final Logger log = LoggerFactory.getLogger(DialectFactory.class);

    /**
     * Cache: DataSource → SqlGuardDialect (avoid repeated detection).
     */
    private static final ConcurrentHashMap<DataSource, SqlGuardDialect> DIALECT_CACHE =
            new ConcurrentHashMap<>();

    /**
     * Detects database type from DataSource and returns appropriate dialect.
     *
     * <p>Detection is cached to avoid repeated JDBC metadata queries.
     *
     * @param dataSource JDBC DataSource
     * @return SqlGuardDialect for detected database, or MySQLDialect as default
     */
    public static SqlGuardDialect getDialect(DataSource dataSource) {
        return DIALECT_CACHE.computeIfAbsent(dataSource, ds -> {
            try (Connection conn = ds.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                String productName = metaData.getDatabaseProductName();

                log.info("Detected database: {}", productName);

                SqlGuardDialect dialect = createDialect(productName);
                log.info("Using dialect: {}", dialect.getDatabaseType());

                return dialect;
            } catch (SQLException e) {
                log.warn("Failed to detect database type, defaulting to MySQL", e);
                return new MySQLDialect(); // Default fallback
            }
        });
    }

    /**
     * Creates dialect based on database product name.
     *
     * @param productName Database product name from metadata
     * @return SqlGuardDialect instance
     */
    private static SqlGuardDialect createDialect(String productName) {
        String lowerName = productName.toLowerCase();

        if (lowerName.contains("mysql") || lowerName.contains("mariadb")) {
            return new MySQLDialect();
        } else if (lowerName.contains("postgresql")) {
            return new PostgreSQLDialect();
        } else if (lowerName.contains("oracle")) {
            return new OracleDialect();
        } else if (lowerName.contains("sql server") || lowerName.contains("microsoft")) {
            return new SQLServerDialect();
        } else {
            log.warn("Unknown database: {}, defaulting to MySQL", productName);
            return new MySQLDialect(); // Default fallback
        }
    }

    /**
     * Clears dialect cache (for testing).
     */
    static void clearCache() {
        DIALECT_CACHE.clear();
    }
}
```

---

### 4. SelectLimitInnerInterceptor

**包路径**: `com.footstone.sqlguard.interceptor.inner.impl`

```java
package com.footstone.sqlguard.interceptor.inner.impl;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.JsqlParserSupport;
import com.footstone.sqlguard.dialect.DialectFactory;
import com.footstone.sqlguard.dialect.SqlGuardDialect;
import com.footstone.sqlguard.interceptor.inner.SqlGuardInnerInterceptor;
import com.footstone.sqlguard.parser.StatementContext;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * InnerInterceptor for automatic LIMIT addition as fallback safety mechanism.
 *
 * <h2>Purpose</h2>
 * <p>Automatically adds LIMIT clause to SELECT statements without existing pagination,
 * preventing full-table scans and large result sets. Acts as a safety fallback after
 * {@code SqlGuardCheckInnerInterceptor} (priority 10) has completed checks.
 *
 * <h2>Priority</h2>
 * <p>Priority: <b>150</b> (fallback interceptor) - runs after check interceptors (10)
 * but before rewrite interceptors (200).
 *
 * <h2>Pagination Detection</h2>
 * <p>Skips LIMIT addition if any of the following conditions are met:
 * <ul>
 *   <li>Statement already has LIMIT clause ({@code select.getLimit() != null})</li>
 *   <li>Statement has OFFSET clause ({@code select.getOffset() != null})</li>
 *   <li>RowBounds is present ({@code rowBounds != RowBounds.DEFAULT})</li>
 * </ul>
 *
 * <h2>Multi-Database Support</h2>
 * <p>Uses {@link SqlGuardDialect} abstraction to support database-specific syntax:
 * <ul>
 *   <li>MySQL/PostgreSQL: {@code LIMIT n}</li>
 *   <li>Oracle: {@code WHERE ROWNUM <= n}</li>
 *   <li>SQL Server: {@code TOP n}</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Default limit value: <b>1000</b> (configurable via constructor).
 *
 * @see SqlGuardDialect
 * @see DialectFactory
 * @since 1.1.0
 */
public class SelectLimitInnerInterceptor extends JsqlParserSupport
        implements SqlGuardInnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SelectLimitInnerInterceptor.class);

    /**
     * Default limit value if no pagination detected.
     */
    private final long defaultLimit;

    /**
     * Database dialect for applying LIMIT clauses.
     */
    private final SqlGuardDialect dialect;

    /**
     * Constructs SelectLimitInnerInterceptor with default limit 1000 and MySQL dialect.
     */
    public SelectLimitInnerInterceptor() {
        this(1000, new com.footstone.sqlguard.dialect.impl.MySQLDialect());
    }

    /**
     * Constructs SelectLimitInnerInterceptor with custom limit and dialect.
     *
     * @param defaultLimit Default limit value
     * @param dialect      Database dialect
     */
    public SelectLimitInnerInterceptor(long defaultLimit, SqlGuardDialect dialect) {
        this.defaultLimit = defaultLimit;
        this.dialect = dialect;
    }

    /**
     * Returns priority 150 (fallback interceptor).
     *
     * @return Priority value 150
     */
    @Override
    public int getPriority() {
        return 150;
    }

    /**
     * Pre-check method for query execution (no-op for fallback interceptors).
     *
     * @return {@code true} to continue chain
     */
    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms, Object parameter,
                                RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        return true; // Continue chain
    }

    /**
     * Modifies SELECT statements to add LIMIT if no pagination detected.
     *
     * @param executor      MyBatis Executor
     * @param ms            MappedStatement
     * @param parameter     SQL parameter
     * @param rowBounds     Pagination bounds
     * @param resultHandler Result handler
     * @param boundSql      BoundSql containing SQL
     */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                             RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        String sql = boundSql.getSql();

        // 1. Get Statement from ThreadLocal cache
        Statement stmt = StatementContext.get(sql);

        if (stmt == null) {
            log.trace("StatementContext cache miss, skipping LIMIT addition");
            return; // No cached Statement, cannot process
        }

        if (!(stmt instanceof Select)) {
            return; // Not a SELECT, skip
        }

        Select select = (Select) stmt;

        // 2. Check if pagination already exists
        if (hasPagination(select, rowBounds)) {
            log.debug("Pagination already exists, skipping LIMIT addition for SQL: {}", sql);
            return;
        }

        // 3. Apply LIMIT using dialect
        log.info("Adding LIMIT {} to SELECT statement (no pagination detected): {}", defaultLimit, sql);
        dialect.applyLimit(select, defaultLimit);

        // 4. Update BoundSql with modified SQL
        String modifiedSql = select.toString();
        PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
        mpBoundSql.sql(modifiedSql);

        log.debug("Modified SQL: {}", modifiedSql);
    }

    /**
     * Pre-check method for update execution (no-op for SELECT-only interceptor).
     *
     * @return {@code true} to continue chain
     */
    @Override
    public boolean willDoUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        return true;
    }

    /**
     * beforeUpdate is no-op (SELECT-only interceptor).
     */
    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        // No-op for SELECT-only interceptor
    }

    /**
     * Checks if pagination already exists in SELECT statement or RowBounds.
     *
     * @param select    SELECT statement
     * @param rowBounds MyBatis RowBounds
     * @return {@code true} if pagination exists, {@code false} otherwise
     */
    private boolean hasPagination(Select select, RowBounds rowBounds) {
        // Check RowBounds
        if (rowBounds != null && rowBounds != RowBounds.DEFAULT) {
            log.trace("RowBounds pagination detected");
            return true;
        }

        // Check LIMIT/OFFSET in Statement
        if (select.getSelectBody() instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

            if (plainSelect.getLimit() != null) {
                log.trace("LIMIT clause detected in SQL");
                return true;
            }

            if (plainSelect.getOffset() != null) {
                log.trace("OFFSET clause detected in SQL");
                return true;
            }
        }

        return false; // No pagination detected
    }

    /**
     * JsqlParserSupport template method (not used in current implementation).
     *
     * @param select SELECT statement
     * @param index  Index
     * @param sql    SQL string
     * @param obj    Object parameter
     */
    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        // Template method - not used in this implementation
        // Pagination logic is in beforeQuery() instead
    }
}
```

---

## 验收标准

### 功能验收
- [ ] SqlGuardDialect 接口创建
- [ ] MySQLDialect 实现（LIMIT n）
- [ ] PostgreSQLDialect 实现（LIMIT n）
- [ ] OracleDialect 实现（ROWNUM wrapping）
- [ ] SQLServerDialect 实现（TOP n）
- [ ] DialectFactory 实现（自动检测 + 缓存）
- [ ] SelectLimitInnerInterceptor 实现（扩展 JsqlParserSupport）
- [ ] getPriority() 返回 150
- [ ] hasPagination() 检测逻辑正确
- [ ] beforeQuery() 添加 LIMIT 正确
- [ ] 默认 limit 值为 1000

### 测试验收
- [ ] SelectLimitInnerInterceptorTest 全部通过（10 个测试）
- [ ] DialectImplementationTest 全部通过（10 个测试）
- [ ] Priority 测试通过（priority = 150）
- [ ] 分页检测测试通过（LIMIT/OFFSET/RowBounds）
- [ ] MySQL 方言测试通过
- [ ] Oracle 方言测试通过
- [ ] SQL Server 方言测试通过
- [ ] PostgreSQL 方言测试通过
- [ ] DialectFactory 检测测试通过
- [ ] DialectFactory 缓存测试通过

### 集成验收
- [ ] 与 StatementContext 集成正常
- [ ] 与 JsqlParserSupport 集成正常
- [ ] 4 种数据库方言正常工作
- [ ] DialectFactory 自动检测正常

### 代码质量验收
- [ ] Javadoc 完整（接口、类、方法）
- [ ] 包含使用示例
- [ ] SLF4J 日志记录
- [ ] 异常处理正确

### 构建验收
- [ ] 编译成功
- [ ] 测试通过（20 tests, 0 failures）
- [ ] BUILD SUCCESS

---

## 输出文件

### 新增文件 (9 个)
1. `sql-guard-core/src/main/java/com/footstone/sqlguard/dialect/SqlGuardDialect.java`
2. `sql-guard-core/src/main/java/com/footstone/sqlguard/dialect/impl/MySQLDialect.java`
3. `sql-guard-core/src/main/java/com/footstone/sqlguard/dialect/impl/PostgreSQLDialect.java`
4. `sql-guard-core/src/main/java/com/footstone/sqlguard/dialect/impl/OracleDialect.java`
5. `sql-guard-core/src/main/java/com/footstone/sqlguard/dialect/impl/SQLServerDialect.java`
6. `sql-guard-core/src/main/java/com/footstone/sqlguard/dialect/DialectFactory.java`
7. `sql-guard-mp/src/main/java/com/footstone/sqlguard/interceptor/inner/impl/SelectLimitInnerInterceptor.java`
8. `sql-guard-core/src/test/java/com/footstone/sqlguard/dialect/DialectImplementationTest.java`
9. `sql-guard-mp/src/test/java/com/footstone/sqlguard/interceptor/inner/impl/SelectLimitInnerInterceptorTest.java`

---

## 依赖与限制

### 输入依赖
- ✅ Task 13.1: SqlGuardInnerInterceptor 接口
- ✅ Task 13.6: StatementContext 类
- ✅ MyBatis-Plus JsqlParserSupport
- ✅ JSqlParser (Select, PlainSelect, Limit, Top)
- ✅ JDBC (DatabaseMetaData)

### 限制
- ⚠️ 依赖 MyBatis-Plus（JsqlParserSupport）
- ⚠️ 子查询和 UNION 不添加 LIMIT（仅顶层 SELECT）
- ⚠️ DialectFactory 缓存基于 DataSource 实例

---

## Memory Logging

Upon completion, you **MUST** log work in:
```
.apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_5_SelectLimitInnerInterceptor_Implementation.md
```

**Required Log Sections**:
- Summary: SelectLimitInnerInterceptor implementation outcome, multi-dialect support
- Details: Pagination detection, dialect implementations, DialectFactory auto-detection
- Output: List of created files (9 files), test results (20 tests)
- Issues: Any dialect compatibility issues or "None"
- Next Steps: Task 13.2 (Main Interceptor) can integrate this fallback interceptor

---

## 执行时间线

- **预计时间**: 2 工作日
  - Day 1 上午：实现 SqlGuardDialect 接口 + MySQLDialect
  - Day 1 下午：实现 SelectLimitInnerInterceptor 核心（仅 MySQL）
  - Day 2 上午：实现其他 3 个方言 + DialectFactory
  - Day 2 下午：编写 TDD 测试（20 个测试）

---

**Created**: 2025-12-22
**Phase**: 13 - InnerInterceptor Architecture
**Task ID**: 13.5
**Priority**: High (重要降级功能)
**Parallel**: Can run in parallel with Task 13.2, 13.3, 13.4 (after 13.1 + 13.6 complete)
