# Phase 11: JDBC 模块拆分

**阶段目标**: 将 sql-guard-jdbc 拆分为独立的连接池专用模块，**保持现有架构不变**

**创建时间**: 2025-12-19
**预计工期**: 10 工作日
**风险等级**: 低

---

## 一、阶段原则

### 1.1 核心原则

**DO** ✅:
- 拆分模块结构
- 提取公共抽象
- 隔离连接池依赖
- 保持 100% 向后兼容

**DON'T** ❌:
- 不改 RuleChecker 接口
- 不改 SqlContext 结构
- 不引入 StatementVisitor
- 不实现 InnerInterceptor
- 不进行架构重构

### 1.2 成功标准

- [ ] 用户只引入需要的连接池模块
- [ ] 无多余依赖污染
- [ ] 现有测试 100% 通过
- [ ] 性能无回退

---

## 二、任务清单

### Task 11.1: 测试用例库设计（2 天）

**负责人**: 待分配

**目标**: 设计模块拆分相关的测试用例，**不包含架构重构测试**

**输出物**:
1. **模块隔离测试设计**:
   - 每个模块独立编译测试
   - 依赖隔离验证测试
   - ClassLoader 隔离测试

2. **向后兼容测试设计**:
   - API 兼容性测试
   - 行为一致性测试
   - 配置兼容性测试

3. **性能基准测试设计**:
   - 模块加载开销测试
   - 运行时性能对比测试
   - 内存占用对比测试

**不包含的测试**:
- ❌ RuleChecker 重构相关测试
- ❌ SqlContext 重构相关测试
- ❌ StatementVisitor 相关测试

**测试矩阵（15 tests）**:

```java
// 1. 模块隔离测试（5 tests）
@Test void testDruidModule_noDependencyOn_HikariCP() {}
@Test void testDruidModule_noDependencyOn_P6Spy() {}
@Test void testHikariModule_noDependencyOn_Druid() {}
@Test void testHikariModule_noDependencyOn_P6Spy() {}
@Test void testP6SpyModule_noDependencyOn_Druid_Hikari() {}

// 2. 向后兼容测试（5 tests）
@Test void testViolationStrategy_druid_behaviorUnchanged() {}
@Test void testViolationStrategy_hikari_behaviorUnchanged() {}
@Test void testViolationStrategy_p6spy_behaviorUnchanged() {}
@Test void testConfiguration_migration_shouldWork() {}
@Test void testAPI_deprecation_shouldCompileWithWarning() {}

// 3. 性能测试（5 tests）
@Test void testModuleLoad_overhead_shouldBeLessThan10ms() {}
@Test void testRuntime_performance_noRegression() {}
@Test void testMemory_usage_shouldNotIncrease() {}
@Test void testClassLoading_shouldBeLazy() {}
@Test void testStartup_time_shouldNotIncrease() {}
```

**验收标准**:
- [ ] 测试设计文档完整
- [ ] 测试矩阵覆盖所有场景
- [ ] 测试用例可编译
- [ ] 性能基准可测量

---

### Task 11.2: JDBC Common 模块提取（2 天）

**负责人**: 待分配

**目标**: 提取 JDBC 层公共抽象，为模块拆分做准备

**输出物**:

#### 1. sql-guard-jdbc-common 模块

**POM 结构**:
```xml
<project>
    <artifactId>sql-guard-jdbc-common</artifactId>

    <dependencies>
        <!-- 只依赖 Core 和 Audit API -->
        <dependency>
            <groupId>com.footstone</groupId>
            <artifactId>sql-guard-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.footstone</groupId>
            <artifactId>sql-guard-audit-api</artifactId>
        </dependency>

        <!-- ❌ 无连接池依赖 -->
    </dependencies>
</project>
```

#### 2. ViolationStrategy 统一枚举

**当前状态**: 三个模块各有一份重复代码

```java
// sql-guard-jdbc-druid/src/.../ViolationStrategy.java
// sql-guard-jdbc-hikari/src/.../ViolationStrategy.java
// sql-guard-jdbc-p6spy/src/.../ViolationStrategy.java
```

**目标**: 统一到 common 模块

```java
// sql-guard-jdbc-common/src/.../ViolationStrategy.java
package com.footstone.sqlguard.interceptor.jdbc.common;

/**
 * JDBC 层统一违规策略
 */
public enum ViolationStrategy {
    /**
     * 阻止执行，抛异常
     */
    BLOCK,

    /**
     * 记录 WARN 日志，继续执行
     */
    WARN,

    /**
     * 记录 INFO 日志，继续执行
     */
    LOG
}
```

#### 3. JdbcInterceptorBase 抽象基类

**模板方法模式**:

```java
package com.footstone.sqlguard.interceptor.jdbc.common;

/**
 * JDBC 拦截器抽象基类
 *
 * 提供模板方法封装公共逻辑
 */
public abstract class JdbcInterceptorBase {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final SqlSafetyValidator validator;
    protected final ViolationStrategy strategy;

    /**
     * 模板方法：拦截 SQL 执行
     */
    protected final void interceptSql(String sql, Connection connection) {
        try {
            // 1. 前置处理（子类可覆盖）
            beforeValidation(sql, connection);

            // 2. 构建上下文
            SqlContext context = buildSqlContext(sql, connection);

            // 3. 验证
            ValidationResult result = validator.validate(context);

            // 4. 处理违规
            if (!result.isPassed()) {
                handleViolation(result, sql);
            }

            // 5. 后置处理（子类可覆盖）
            afterValidation(sql, connection, result);

        } catch (Exception e) {
            handleError(sql, e);
        }
    }

    /**
     * 构建 SqlContext（公共逻辑）
     */
    protected SqlContext buildSqlContext(String sql, Connection connection) {
        return SqlContext.builder()
            .sql(sql)
            .type(detectSqlType(sql))
            .build();
    }

    /**
     * 处理违规（公共逻辑）
     */
    protected void handleViolation(ValidationResult result, String sql)
            throws SQLException {
        switch (strategy) {
            case BLOCK:
                throw new SQLException("SQL Safety Violation: " + result.getMessage());
            case WARN:
                logger.warn("SQL Safety Violation: {}", result.getMessage());
                break;
            case LOG:
                logger.info("SQL Safety Violation: {}", result.getMessage());
                break;
        }
    }

    // 扩展点（子类可覆盖）
    protected void beforeValidation(String sql, Connection connection) {}
    protected void afterValidation(String sql, Connection connection, ValidationResult result) {}
    protected void handleError(String sql, Exception e) {
        logger.error("Error intercepting SQL: {}", sql, e);
    }

    // 工具方法
    protected SqlCommandType detectSqlType(String sql) { ... }
}
```

#### 4. JdbcInterceptorConfig 接口

```java
package com.footstone.sqlguard.interceptor.jdbc.common;

/**
 * JDBC 拦截器配置接口
 *
 * 各连接池专用配置继承此接口
 */
public interface JdbcInterceptorConfig {

    /**
     * 是否启用
     */
    boolean isEnabled();

    /**
     * 违规策略
     */
    ViolationStrategy getStrategy();

    /**
     * 是否启用审计
     */
    boolean isAuditEnabled();

    /**
     * 排除的 SQL 模式（正则表达式）
     */
    List<String> getExcludePatterns();
}
```

#### 5. SqlContextBuilder 工具类

```java
package com.footstone.sqlguard.interceptor.jdbc.common;

/**
 * JDBC 层 SqlContext 构建器
 */
public class SqlContextBuilder {

    public static SqlContext build(String sql, Connection connection) {
        return SqlContext.builder()
            .sql(sql)
            .type(detectSqlType(sql))
            .build();
    }

    private static SqlCommandType detectSqlType(String sql) {
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("SELECT")) return SqlCommandType.SELECT;
        if (upper.startsWith("INSERT")) return SqlCommandType.INSERT;
        if (upper.startsWith("UPDATE")) return SqlCommandType.UPDATE;
        if (upper.startsWith("DELETE")) return SqlCommandType.DELETE;
        return SqlCommandType.UNKNOWN;
    }
}
```

**测试矩阵（12 tests）**:

```java
// 1. ViolationStrategy 测试（3 tests）
@Test void testViolationStrategy_BLOCK_shouldThrowException() {}
@Test void testViolationStrategy_WARN_shouldLogWarn() {}
@Test void testViolationStrategy_LOG_shouldLogInfo() {}

// 2. JdbcInterceptorBase 测试（6 tests）
@Test void testTemplateMethod_shouldInvokeInOrder() {}
@Test void testBeforeValidation_shouldBeInvoked() {}
@Test void testAfterValidation_shouldBeInvoked() {}
@Test void testHandleViolation_BLOCK_shouldThrow() {}
@Test void testHandleViolation_WARN_shouldLog() {}
@Test void testHandleError_shouldLog() {}

// 3. 工具类测试（3 tests）
@Test void testSqlContextBuilder_SELECT_shouldDetect() {}
@Test void testSqlContextBuilder_UPDATE_shouldDetect() {}
@Test void testSqlContextBuilder_DELETE_shouldDetect() {}
```

**验收标准**:
- [ ] sql-guard-jdbc-common 模块独立编译
- [ ] 无连接池依赖
- [ ] 所有测试通过
- [ ] Javadoc 完整

---

### Task 11.3: Druid 模块独立（2 天）

**负责人**: 待分配

**目标**: 将 Druid 相关实现拆分为独立模块

**输出物**:

#### 1. sql-guard-jdbc-druid 模块

**POM 结构**:
```xml
<project>
    <artifactId>sql-guard-jdbc-druid</artifactId>

    <dependencies>
        <!-- 依赖 Common 模块 -->
        <dependency>
            <groupId>com.footstone</groupId>
            <artifactId>sql-guard-jdbc-common</artifactId>
        </dependency>

        <!-- Druid 依赖（provided scope） -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

#### 2. DruidSqlSafetyFilter 重构

**当前实现**: 重复代码

```java
// 当前实现（sql-guard-jdbc/...）
public class DruidSqlSafetyFilter extends FilterAdapter {
    private final DefaultSqlSafetyValidator validator;
    private final ViolationStrategy strategy;  // ❌ 重复定义

    // ❌ 重复实现 handleViolation 等逻辑
}
```

**目标实现**: 继承 JdbcInterceptorBase

```java
// sql-guard-jdbc-druid/src/.../DruidSqlSafetyFilter.java
package com.footstone.sqlguard.interceptor.jdbc.druid;

/**
 * Druid Filter 实现
 *
 * 继承 JdbcInterceptorBase 复用公共逻辑
 */
public class DruidSqlSafetyFilter extends FilterAdapter {

    private final JdbcInterceptorBase interceptorBase;

    public DruidSqlSafetyFilter(DefaultSqlSafetyValidator validator,
                                ViolationStrategy strategy) {
        // 组合方式使用 JdbcInterceptorBase
        this.interceptorBase = new JdbcInterceptorBase(validator, strategy) {};
    }

    @Override
    public PreparedStatementProxy connection_prepareStatement(
            FilterChain chain, ConnectionProxy connection, String sql)
            throws SQLException {

        // 调用公共拦截逻辑
        interceptorBase.interceptSql(sql, connection);

        return super.connection_prepareStatement(chain, connection, sql);
    }

    @Override
    public ResultSetProxy statement_executeQuery(
            FilterChain chain, StatementProxy statement, String sql)
            throws SQLException {

        interceptorBase.interceptSql(sql, statement.getConnectionProxy());

        return super.statement_executeQuery(chain, statement, sql);
    }

    // 其他 Druid Filter 方法...
}
```

#### 3. DruidSqlSafetyFilterConfiguration

```java
package com.footstone.sqlguard.interceptor.jdbc.druid;

/**
 * Spring Boot 自动配置
 */
@Configuration
@ConditionalOnClass(DruidDataSource.class)
@EnableConfigurationProperties(DruidInterceptorConfig.class)
public class DruidSqlSafetyFilterConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "sqlguard.druid", name = "enabled", matchIfMissing = true)
    public DruidSqlSafetyFilter druidSqlSafetyFilter(
            DefaultSqlSafetyValidator validator,
            DruidInterceptorConfig config) {

        return new DruidSqlSafetyFilter(validator, config.getStrategy());
    }
}
```

#### 4. DruidInterceptorConfig

```java
package com.footstone.sqlguard.interceptor.jdbc.druid;

/**
 * Druid 专用配置
 */
@ConfigurationProperties(prefix = "sqlguard.druid")
public class DruidInterceptorConfig implements JdbcInterceptorConfig {

    private boolean enabled = true;
    private ViolationStrategy strategy = ViolationStrategy.WARN;
    private boolean auditEnabled = false;
    private List<String> excludePatterns = new ArrayList<>();

    // Druid 专用配置
    private int filterPosition = -1;  // Druid Filter 顺序
    private boolean connectionProxyEnabled = true;

    // Getters/Setters...
}
```

**测试矩阵（10 tests）**:

```java
// 1. 模块隔离测试（3 tests）
@Test void testDruidModule_independentCompile() {}
@Test void testDruidModule_noDependencyOn_Hikari_P6Spy() {}
@Test void testDruidModule_onlyDruidProvided() {}

// 2. Filter 重构测试（4 tests）
@Test void testDruidFilter_prepareStatement_shouldIntercept() {}
@Test void testDruidFilter_executeQuery_shouldIntercept() {}
@Test void testDruidFilter_violationStrategy_shouldUseCommon() {}
@Test void testDruidFilter_extendJdbcInterceptorBase() {}

// 3. 集成测试（3 tests）
@Test void testDruid_withH2_endToEnd() {}
@Test void testDruid_springBoot_autoConfig() {}
@Test void testDruid_performance_noRegression() {}
```

**验收标准**:
- [ ] Druid 模块独立编译
- [ ] 无 Hikari/P6Spy 依赖
- [ ] 现有 Druid 测试 100% 通过
- [ ] 性能无回退

---

### Task 11.4: HikariCP 模块独立（2 天）

**负责人**: 待分配

**目标**: 将 HikariCP 相关实现拆分为独立模块

**输出物**:

#### 1. sql-guard-jdbc-hikari 模块

**POM 结构**:
```xml
<project>
    <artifactId>sql-guard-jdbc-hikari</artifactId>

    <dependencies>
        <!-- 依赖 Common 模块 -->
        <dependency>
            <groupId>com.footstone</groupId>
            <artifactId>sql-guard-jdbc-common</artifactId>
        </dependency>

        <!-- HikariCP 依赖（provided scope） -->
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

#### 2. HikariSqlSafetyProxyFactory 重构

**当前实现**: 重复代码

```java
// 当前实现
public class HikariSqlSafetyProxyFactory {
    private final DefaultSqlSafetyValidator validator;
    private final ViolationStrategy strategy;  // ❌ 重复定义

    // ❌ 重复实现 handleViolation 等逻辑
}
```

**目标实现**: 组合 JdbcInterceptorBase

```java
// sql-guard-jdbc-hikari/src/.../HikariSqlSafetyProxyFactory.java
package com.footstone.sqlguard.interceptor.jdbc.hikari;

/**
 * HikariCP Proxy Factory
 *
 * 组合 JdbcInterceptorBase 复用公共逻辑
 */
public class HikariSqlSafetyProxyFactory {

    private final JdbcInterceptorBase interceptorBase;

    public HikariSqlSafetyProxyFactory(DefaultSqlSafetyValidator validator,
                                        ViolationStrategy strategy) {
        this.interceptorBase = new JdbcInterceptorBase(validator, strategy) {};
    }

    /**
     * 包装 HikariDataSource
     */
    public DataSource wrap(HikariDataSource dataSource) {
        return (DataSource) Proxy.newProxyInstance(
            dataSource.getClass().getClassLoader(),
            new Class<?>[] { DataSource.class },
            new DataSourceProxyHandler(dataSource, interceptorBase)
        );
    }

    /**
     * DataSource Proxy Handler
     */
    private static class DataSourceProxyHandler implements InvocationHandler {
        private final HikariDataSource target;
        private final JdbcInterceptorBase interceptorBase;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            Object result = method.invoke(target, args);

            if (method.getName().equals("getConnection") && result instanceof Connection) {
                return wrapConnection((Connection) result);
            }

            return result;
        }

        private Connection wrapConnection(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                new Class<?>[] { Connection.class },
                new ConnectionProxyHandler(connection, interceptorBase)
            );
        }
    }

    /**
     * Connection Proxy Handler
     */
    private static class ConnectionProxyHandler implements InvocationHandler {
        private final Connection target;
        private final JdbcInterceptorBase interceptorBase;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {

            // 拦截 prepareStatement
            if (method.getName().equals("prepareStatement") && args.length > 0) {
                String sql = (String) args[0];
                interceptorBase.interceptSql(sql, target);
            }

            Object result = method.invoke(target, args);

            // 包装 Statement
            if (result instanceof PreparedStatement) {
                return wrapStatement((PreparedStatement) result);
            }

            return result;
        }
    }
}
```

#### 3. HikariInterceptorConfig

```java
package com.footstone.sqlguard.interceptor.jdbc.hikari;

/**
 * HikariCP 专用配置
 */
@ConfigurationProperties(prefix = "sqlguard.hikari")
public class HikariInterceptorConfig implements JdbcInterceptorConfig {

    private boolean enabled = true;
    private ViolationStrategy strategy = ViolationStrategy.WARN;
    private boolean auditEnabled = false;
    private List<String> excludePatterns = new ArrayList<>();

    // HikariCP 专用配置
    private boolean proxyConnectionEnabled = true;
    private long leakDetectionThreshold = 0;  // HikariCP leak detection

    // Getters/Setters...
}
```

**测试矩阵（10 tests）**:

```java
// 1. 模块隔离测试（3 tests）
@Test void testHikariModule_independentCompile() {}
@Test void testHikariModule_noDependencyOn_Druid_P6Spy() {}
@Test void testHikariModule_onlyHikariProvided() {}

// 2. Proxy 重构测试（4 tests）
@Test void testHikariProxy_dataSource_shouldWrap() {}
@Test void testHikariProxy_connection_shouldIntercept() {}
@Test void testHikariProxy_prepareStatement_shouldIntercept() {}
@Test void testHikariProxy_violationStrategy_shouldUseCommon() {}

// 3. 集成测试（3 tests）
@Test void testHikari_withH2_endToEnd() {}
@Test void testHikari_springBoot_autoConfig() {}
@Test void testHikari_performance_noRegression() {}
```

**验收标准**:
- [ ] HikariCP 模块独立编译
- [ ] 无 Druid/P6Spy 依赖
- [ ] 现有 HikariCP 测试 100% 通过
- [ ] 支持 HikariCP 4.x 和 5.x

---

### Task 11.5: P6Spy 模块独立（2 天）

**负责人**: 待分配

**目标**: 将 P6Spy 相关实现拆分为独立模块

**输出物**:

#### 1. sql-guard-jdbc-p6spy 模块

**POM 结构**:
```xml
<project>
    <artifactId>sql-guard-jdbc-p6spy</artifactId>

    <dependencies>
        <!-- 依赖 Common 模块 -->
        <dependency>
            <groupId>com.footstone</groupId>
            <artifactId>sql-guard-jdbc-common</artifactId>
        </dependency>

        <!-- P6Spy 依赖（provided scope） -->
        <dependency>
            <groupId>p6spy</groupId>
            <artifactId>p6spy</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

#### 2. P6SpySqlSafetyListener 重构

**目标实现**: 组合 JdbcInterceptorBase

```java
// sql-guard-jdbc-p6spy/src/.../P6SpySqlSafetyListener.java
package com.footstone.sqlguard.interceptor.jdbc.p6spy;

/**
 * P6Spy Listener 实现
 *
 * 组合 JdbcInterceptorBase 复用公共逻辑
 */
public class P6SpySqlSafetyListener extends JdbcEventListener {

    private final JdbcInterceptorBase interceptorBase;

    public P6SpySqlSafetyListener() {
        // 从系统属性或配置文件读取配置
        DefaultSqlSafetyValidator validator = ...;
        ViolationStrategy strategy = ...;

        this.interceptorBase = new JdbcInterceptorBase(validator, strategy) {};
    }

    @Override
    public void onBeforeAnyExecute(StatementInformation statementInformation) {
        String sql = statementInformation.getSqlWithValues();
        Connection connection = statementInformation.getConnectionInformation().getConnection();

        try {
            interceptorBase.interceptSql(sql, connection);
        } catch (SQLException e) {
            throw new RuntimeException("SQL Safety Violation", e);
        }
    }
}
```

#### 3. P6SpySqlSafetyModule

```java
package com.footstone.sqlguard.interceptor.jdbc.p6spy;

/**
 * P6Spy Module 注册
 *
 * SPI 自动发现
 */
public class P6SpySqlSafetyModule implements P6Factory {

    @Override
    public void init(P6ModuleManager p6ModuleManager) {
        // 注册 P6SpySqlSafetyListener
        P6SpySqlSafetyListener listener = new P6SpySqlSafetyListener();
        p6ModuleManager.register(P6SpySqlSafetyListener.class, listener);
    }
}
```

#### 4. META-INF/services 配置

```
# src/main/resources/META-INF/services/com.p6spy.engine.spy.P6Factory
com.footstone.sqlguard.interceptor.jdbc.p6spy.P6SpySqlSafetyModule
```

#### 5. P6SpyInterceptorConfig

```java
package com.footstone.sqlguard.interceptor.jdbc.p6spy;

/**
 * P6Spy 专用配置
 *
 * 通过系统属性配置（P6Spy 限制）
 */
public class P6SpyInterceptorConfig implements JdbcInterceptorConfig {

    // 从系统属性读取
    private static final String PROP_ENABLED = "sqlguard.p6spy.enabled";
    private static final String PROP_STRATEGY = "sqlguard.p6spy.strategy";

    @Override
    public boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROP_ENABLED, "true"));
    }

    @Override
    public ViolationStrategy getStrategy() {
        String strategy = System.getProperty(PROP_STRATEGY, "WARN");
        return ViolationStrategy.valueOf(strategy);
    }

    // ...
}
```

**测试矩阵（10 tests）**:

```java
// 1. 模块隔离测试（3 tests）
@Test void testP6SpyModule_independentCompile() {}
@Test void testP6SpyModule_noDependencyOn_Druid_Hikari() {}
@Test void testP6SpyModule_onlyP6SpyProvided() {}

// 2. Listener 重构测试（4 tests）
@Test void testP6SpyListener_onBeforeAnyExecute_shouldIntercept() {}
@Test void testP6SpyListener_violationStrategy_shouldUseCommon() {}
@Test void testP6SpyModule_spiRegistration_shouldWork() {}
@Test void testP6SpyModule_systemProperty_shouldConfigure() {}

// 3. 集成测试（3 tests）
@Test void testP6Spy_withBareJdbc_endToEnd() {}
@Test void testP6Spy_withC3P0_endToEnd() {}
@Test void testP6Spy_universalCoverage_shouldWork() {}
```

**验收标准**:
- [ ] P6Spy 模块独立编译
- [ ] 无 Druid/HikariCP 依赖
- [ ] SPI 注册正常工作
- [ ] 通用 JDBC 覆盖验证

---

### Task 11.6: 集成测试（2 天）

**负责人**: 待分配

**目标**: 验证模块拆分后的集成正确性和向后兼容性

**测试矩阵（20 tests）**:

#### 1. 模块隔离验证（8 tests）

```java
@Test void testCommonModule_independentCompile_noConnectionPool() {}
@Test void testDruidModule_independentCompile_onlyDruid() {}
@Test void testHikariModule_independentCompile_onlyHikari() {}
@Test void testP6SpyModule_independentCompile_onlyP6Spy() {}
@Test void testUserProject_onlyDruid_noDependencyPollution() {}
@Test void testUserProject_onlyHikari_noDependencyPollution() {}
@Test void testUserProject_onlyP6Spy_noDependencyPollution() {}
@Test void testUserProject_allModules_shouldWork() {}
```

#### 2. 向后兼容验证（7 tests）

```java
@Test void testDruid_existingCode_shouldWork() {}
@Test void testHikari_existingCode_shouldWork() {}
@Test void testP6Spy_existingCode_shouldWork() {}
@Test void testViolationStrategy_oldImport_shouldCompileWithWarning() {}
@Test void testConfiguration_oldFormat_shouldMigrate() {}
@Test void testAPI_behavior_unchanged() {}
@Test void testTestSuite_100percent_passed() {}
```

#### 3. 性能回归测试（5 tests）

```java
@Test void testDruid_throughput_noRegression() {}
@Test void testHikari_latency_noRegression() {}
@Test void testP6Spy_overhead_documented() {}
@Test void testModuleLoad_startupTime_noIncrease() {}
@Test void testMemory_usage_noIncrease() {}
```

**验收标准**:
- [ ] 所有集成测试通过
- [ ] 模块隔离验证成功
- [ ] 向后兼容 100%
- [ ] 性能无回退

---

## 三、依赖关系

### 3.1 任务依赖图

```
11.1 测试用例库设计（2 天）
  ↓
11.2 JDBC Common 模块提取（2 天）
  ↓
┌─────────────┬──────────────┬──────────────┐
│  11.3       │   11.4       │   11.5       │
│  Druid      │   HikariCP   │   P6Spy      │  (并行)
│  2 天       │   2 天       │   2 天       │
└─────────────┴──────────────┴──────────────┘
  ↓
11.6 集成测试（2 天）
```

**关键路径**: 11.1 → 11.2 → 11.3/11.4/11.5 → 11.6

**并行机会**: Task 11.3, 11.4, 11.5 可并行执行

---

## 四、验收标准

### 4.1 功能验收

- [ ] sql-guard-jdbc-common 模块独立编译
- [ ] sql-guard-jdbc-druid/hikari/p6spy 模块独立运行
- [ ] 用户只引入需要的连接池模块
- [ ] ViolationStrategy 统一
- [ ] JdbcInterceptorBase 抽象正确

### 4.2 架构验收

- [ ] 模块依赖隔离（无多余依赖）
- [ ] 公共抽象清晰
- [ ] 代码无重复

### 4.3 性能验收

- [ ] 性能无回退（< 原版本 110%）
- [ ] 模块加载无额外开销
- [ ] 内存占用无增加

### 4.4 兼容性验收

- [ ] 现有测试 100% 通过
- [ ] 现有代码无需修改
- [ ] 配置向后兼容

---

## 五、风险管理

### 5.1 技术风险

| 风险 | 概率 | 影响 | 缓解措施 |
|-----|------|------|------------|
| **模块依赖冲突** | 低 | 中 | Maven Enforcer Plugin 验证 |
| **向后兼容破坏** | 低 | 高 | 100% 测试覆盖 |
| **性能回退** | 低 | 中 | 性能基准测试 |
| **代码迁移错误** | 低 | 中 | 逐模块迁移，增量测试 |

### 5.2 时间风险

| 风险 | 概率 | 影响 | 缓解措施 |
|-----|------|------|------------|
| **测试设计不完整** | 中 | 中 | 提前 Review |
| **并行任务冲突** | 低 | 低 | 模块独立，冲突少 |
| **集成测试耗时** | 中 | 低 | 并行测试 |

---

## 六、后续阶段

### Phase 12: 核心架构统一（15 天）

**依赖 Phase 11 输出**:
- sql-guard-jdbc-common 模块
- 独立的连接池模块

**任务**:
- RuleChecker 重构
- SqlContext 重构
- StatementVisitor 引入
- 测试迁移

### Phase 13: InnerInterceptor 架构（20 天）

**依赖 Phase 12 输出**:
- 重构后的 RuleChecker
- StatementVisitor 抽象

**任务**:
- InnerInterceptor 接口
- SelectLimitInnerInterceptor
- 多版本兼容层

---

## 七、参考文档

### 7.1 架构文档

- `.apm/Memory/Phase_11_Reference_Architecture/Architecture_Review_Report.md` - 系统性审视报告
- `.apm/Memory/Phase_11_Reference_Architecture/Implementation_Plan_Phase_11_12_13_Restructure.md` - 重构方案
- `.apm/Memory/Phase_11_Reference_Architecture/Module_Separation_And_Version_Compatibility.md` - 模块拆分方案

### 7.2 原始 Implementation Plan

- `.apm/Implementation_Plan.md` - 总体实施计划（待更新）

---

**文档版本**: v1.0
**创建日期**: 2025-12-19
**预计工期**: 10 工作日
**风险等级**: 低
