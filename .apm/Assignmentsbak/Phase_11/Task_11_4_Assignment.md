---
Task_ID: 11.4
Task_Name: HikariCP Module Separation
Assigned_Agent: Agent_Core_Engine_Foundation
Phase: Phase 11 - JDBC Module Separation
Priority: HIGH (Can run in parallel with 11.3, 11.5)
Estimated_Duration: 2 days
Dependencies: Task 11.2 (Common Module - COMPLETED)
Parallel_With: Task 11.3 (Druid), Task 11.5 (P6Spy)
Output_Location: .apm/Memory/Phase_11_JDBC_Module_Separation/Task_11_4_HikariCP_Module_Separation.md
---

# Task 11.4 – HikariCP Module Separation

## Objective

Create independent `sql-guard-jdbc-hikari` module containing ONLY HikariCP-specific implementations, depending on `sql-guard-jdbc-common` for shared abstractions, ensuring minimal dependency footprint for HikariCP users, refactoring proxy factory to compose `JdbcInterceptorBase`, supporting both HikariCP 4.x (Java 8) and 5.x (Java 11+).

**CRITICAL**: This task can execute IN PARALLEL with Tasks 11.3 (Druid) and 11.5 (P6Spy).

---

## Context

### Task 11.2 Completion
**Foundation Ready** ✅:
- `sql-guard-jdbc-common` module created with unified abstractions
- `ViolationStrategy` enum unified
- `JdbcInterceptorBase` template method pattern available
- `JdbcInterceptorConfig` interface defined
- 35 tests passing, Maven Enforcer validates dependencies

### Current State - HikariCP Implementation
**Location**: `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/hikari/`

**Files**:
- `HikariSqlSafetyProxyFactory.java` - Current proxy factory implementation
- `HikariSqlSafetyConfiguration.java` - Spring Boot auto-configuration
- `ConnectionInvocationHandler.java`, `StatementInvocationHandler.java` - Proxy handlers
- `ViolationStrategy.java` - Deprecated (now in common module)

**Existing Tests**: 10 HikariCP-specific tests (currently passing)

### Target Architecture
```
sql-guard-jdbc-hikari/
├── pom.xml (HikariCP:provided, jdbc-common:compile)
└── src/main/java/com/footstone/sqlguard/interceptor/jdbc/hikari/
    ├── HikariSqlSafetyProxyFactory.java      # Refactored to compose JdbcInterceptorBase
    ├── HikariSqlAuditProxyFactory.java       # New audit proxy factory
    ├── HikariSqlSafetyConfiguration.java
    ├── HikariInterceptorConfig.java          # Extends JdbcInterceptorConfig
    ├── HikariJdbcInterceptor.java            # JdbcInterceptorBase implementation
    ├── DataSourceProxyHandler.java           # DataSource proxy handler
    ├── ConnectionProxyHandler.java           # Connection proxy handler
    └── StatementProxyHandler.java            # Statement proxy handler
```

---

## Expected Outputs

### 1. Maven Module Structure
**Location**: `sql-guard-jdbc-hikari/pom.xml`

**POM Requirements**:
```xml
<dependencies>
    <!-- Common JDBC abstractions -->
    <dependency>
        <groupId>com.footstone</groupId>
        <artifactId>sql-guard-jdbc-common</artifactId>
    </dependency>

    <!-- HikariCP dependency in provided scope -->
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
        <scope>provided</scope>
        <!-- Version range: 4.0.3+ (Java 8) or 5.0.1+ (Java 11+) -->
    </dependency>

    <!-- NO Druid or P6Spy dependencies -->
    <!-- Maven Enforcer validates this -->
</dependencies>
```

**Maven Enforcer Configuration**:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <executions>
        <execution>
            <id>enforce-no-other-pools</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <bannedDependencies>
                        <excludes>
                            <exclude>com.alibaba:druid</exclude>
                            <exclude>p6spy:p6spy</exclude>
                        </excludes>
                    </bannedDependencies>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 2. HikariJdbcInterceptor (New)
**Location**: `sql-guard-jdbc-hikari/src/main/java/com/footstone/sqlguard/interceptor/jdbc/hikari/HikariJdbcInterceptor.java`

**Purpose**: Concrete implementation of `JdbcInterceptorBase` for HikariCP-specific logic

**Requirements**:
```java
public class HikariJdbcInterceptor extends JdbcInterceptorBase {
    private final HikariInterceptorConfig config;
    private final SqlSafetyValidator validator;

    @Override
    protected SqlContext buildSqlContext(String sql, Object... params) {
        // HikariCP-specific context building
        return SqlContextBuilder.buildContext(sql, params, "hikari");
    }

    @Override
    protected void handleViolation(ValidationResult result) {
        ViolationStrategy strategy = config.getStrategy();
        if (strategy.shouldBlock() && result.hasViolations()) {
            throw new SqlSafetyException(result);
        }
        if (strategy.shouldLog()) {
            logger.warn("SQL violation: {}", result);
        }
    }

    // Optional hooks for HikariCP-specific behavior
    @Override
    protected void beforeValidation(String sql, Object... params) {
        // HikariCP pre-validation logic
    }
}
```

### 3. Multi-Layer Proxy Chain (Refactored)
**HikariCP-Specific Pattern**: **Multi-layer JDK Dynamic Proxy**

**Three-Layer Proxy Chain**:
```
HikariDataSource (original)
    ↓ wrapped by
DataSourceProxy (Layer 1 - proxies DataSource.getConnection())
    ↓ returns
ConnectionProxy (Layer 2 - proxies Connection.prepareStatement())
    ↓ returns
StatementProxy (Layer 3 - proxies Statement.execute())
    ↓ triggers
SQL Validation (via HikariJdbcInterceptor)
```

#### Layer 1: DataSourceProxyHandler
**Location**: `sql-guard-jdbc-hikari/src/main/java/com/footstone/sqlguard/interceptor/jdbc/hikari/DataSourceProxyHandler.java`

**Purpose**: Intercept `DataSource.getConnection()` to return Connection proxy

**Implementation**:
```java
public class DataSourceProxyHandler implements InvocationHandler {
    private final DataSource target;
    private final HikariJdbcInterceptor interceptor;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result = method.invoke(target, args);

        // Wrap returned Connection with ConnectionProxy
        if (method.getName().equals("getConnection") && result instanceof Connection) {
            return Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new ConnectionProxyHandler((Connection) result, interceptor)
            );
        }

        return result;
    }
}
```

#### Layer 2: ConnectionProxyHandler
**Location**: `sql-guard-jdbc-hikari/src/main/java/com/footstone/sqlguard/interceptor/jdbc/hikari/ConnectionProxyHandler.java`

**Purpose**: Intercept `Connection.prepareStatement()` to return Statement proxy

**Implementation**:
```java
public class ConnectionProxyHandler implements InvocationHandler {
    private final Connection target;
    private final HikariJdbcInterceptor interceptor;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result = method.invoke(target, args);

        // Wrap returned Statement/PreparedStatement with StatementProxy
        if (method.getName().startsWith("prepare") && result instanceof PreparedStatement) {
            String sql = (String) args[0]; // SQL is first argument
            return Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                new StatementProxyHandler((PreparedStatement) result, sql, interceptor)
            );
        }

        if (method.getName().equals("createStatement") && result instanceof Statement) {
            return Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[] { Statement.class },
                new StatementProxyHandler((Statement) result, null, interceptor)
            );
        }

        return result;
    }
}
```

#### Layer 3: StatementProxyHandler
**Location**: `sql-guard-jdbc-hikari/src/main/java/com/footstone/sqlguard/interceptor/jdbc/hikari/StatementProxyHandler.java`

**Purpose**: Intercept `Statement.execute*()` methods to trigger SQL validation

**Implementation**:
```java
public class StatementProxyHandler implements InvocationHandler {
    private final Statement target;
    private final String sql; // For PreparedStatement, null for Statement
    private final HikariJdbcInterceptor interceptor;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // Intercept execute methods
        if (methodName.startsWith("execute")) {
            String actualSql = sql != null ? sql : (args.length > 0 ? (String) args[0] : null);

            // Trigger validation via interceptor
            interceptor.interceptSql(actualSql);
        }

        // Proceed with original method
        return method.invoke(target, args);
    }
}
```

### 4. HikariSqlSafetyProxyFactory (Refactored)
**Location**: `sql-guard-jdbc-hikari/src/main/java/com/footstone/sqlguard/interceptor/jdbc/hikari/HikariSqlSafetyProxyFactory.java`

**Factory Pattern**: Creates multi-layer proxy chain

**Implementation**:
```java
public class HikariSqlSafetyProxyFactory {
    private final HikariJdbcInterceptor interceptor;

    public DataSource wrap(DataSource originalDataSource) {
        return (DataSource) Proxy.newProxyInstance(
            DataSource.class.getClassLoader(),
            new Class<?>[] { DataSource.class },
            new DataSourceProxyHandler(originalDataSource, interceptor)
        );
    }
}
```

### 5. HikariInterceptorConfig (New)
**Location**: `sql-guard-jdbc-hikari/src/main/java/com/footstone/sqlguard/interceptor/jdbc/hikari/HikariInterceptorConfig.java`

**Interface Definition**:
```java
public interface HikariInterceptorConfig extends JdbcInterceptorConfig {
    // Inherited from JdbcInterceptorConfig:
    // - boolean isEnabled()
    // - ViolationStrategy getStrategy()
    // - boolean isAuditEnabled()
    // - List<String> getExcludePatterns()

    // HikariCP-specific properties:

    /**
     * Enable proxy-based connection interception.
     * @return true if proxy interception enabled
     */
    boolean isProxyConnectionEnabled();

    /**
     * Leak detection threshold (milliseconds).
     * Mirrors HikariCP's own leak detection mechanism.
     * @return threshold in milliseconds (0 = disabled)
     */
    long getLeakDetectionThreshold();
}
```

### 6. Version Compatibility (HikariCP 4.x and 5.x)
**Challenge**: HikariCP 5.x requires Java 11+, HikariCP 4.x supports Java 8+

**Strategy**: Reflection-based API detection

**Implementation**:
```java
public class HikariVersionDetector {
    private static final boolean IS_HIKARI_5X;

    static {
        boolean hikari5x = false;
        try {
            // HikariCP 5.x has different package structure
            Class.forName("com.zaxxer.hikari.pool.HikariPool");
            hikari5x = true;
        } catch (ClassNotFoundException e) {
            // HikariCP 4.x
        }
        IS_HIKARI_5X = hikari5x;
    }

    public static boolean isHikari5x() {
        return IS_HIKARI_5X;
    }
}
```

**Testing Both Versions**:
- Test profile for HikariCP 4.x: `<hikaricp.version>4.0.3</hikaricp.version>`
- Test profile for HikariCP 5.x: `<hikaricp.version>5.1.0</hikaricp.version>`
- Run tests with both profiles to ensure compatibility

---

## Implementation Guidance

### Step 1: TDD - Write Tests First (25+ tests)
**Reference**: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md` (HikariCP-specific tests)

**Test Classes to Implement**:

1. **HikariModuleIsolationTest** (10 tests):
   - `testHikariModule_noDruidDependency_compiles()`
   - `testHikariModule_noP6SpyDependency_compiles()`
   - `testHikariModule_onlyHikariProvided_works()`
   - `testHikariModule_commonModuleDependency_resolves()`
   - `testHikariModule_classLoading_noOtherPoolsRequired()`
   - `testHikariModule_independentJar_packages()`
   - `testHikariModule_hikari4x_compatible()`
   - `testHikariModule_hikari5x_compatible()`
   - `testHikariModule_runtimeClasspath_minimal()`
   - `testHikariModule_testClasspath_isolated()`

2. **HikariProxyRefactoringTest** (10 tests):
   - `testHikariProxy_composesJdbcInterceptorBase()`
   - `testHikariProxy_dataSourceProxy_wraps()`
   - `testHikariProxy_connectionProxy_intercepts()`
   - `testHikariProxy_statementProxy_intercepts()`
   - `testHikariProxy_preparedStatementProxy_intercepts()`
   - `testHikariProxy_callableStatementProxy_intercepts()`
   - `testHikariProxy_jdkDynamicProxy_works()`
   - `testHikariProxy_violationStrategy_usesCommon()`
   - `testHikariProxy_auditEvent_usesCommonBuilder()`
   - `testHikariProxy_configuration_extendsCommon()`

3. **HikariIntegrationTest** (5 tests):
   - `testHikari_endToEnd_interceptsQueries()`
   - `testHikari_withH2_validates()`
   - `testHikari_connectionPoolMetrics_preserves()`
   - `testHikari_springBoot_autoConfigures()`
   - `testHikari_performance_meetsBaseline()`

### Step 2: Module Isolation - HikariCP-Specific
**CRITICAL**: `sql-guard-jdbc-hikari` must have ZERO Druid or P6Spy dependencies.

**Validation Commands**:
```bash
# Build HikariCP module in isolation
cd sql-guard-jdbc-hikari
mvn clean compile -DskipTests

# Verify dependency tree
mvn dependency:tree | grep -i "druid\|p6spy"
# Expected: NO MATCHES

# Maven Enforcer validation
mvn enforcer:enforce
# Expected: BUILD SUCCESS
```

### Step 3: JDK Dynamic Proxy Architecture
**HikariCP-Specific Pattern**: Multi-layer JDK Dynamic Proxy

**Key Concepts**:
- **InvocationHandler**: JDK interface for proxy behavior
- **Proxy.newProxyInstance()**: Creates dynamic proxy at runtime
- **Three-layer chain**: DataSource → Connection → Statement proxies
- **No compile-time dependency**: Uses `provided` scope for HikariCP

**Proxy Chain Benefits**:
- **Zero compile-time coupling**: Only runtime dependency on HikariCP
- **Type safety**: Proxies implement standard JDBC interfaces
- **Performance**: JDK proxies are optimized by JVM
- **Testability**: Can test each layer independently

**Interception Points**:
```java
// Layer 1: DataSource level
DataSource.getConnection() → wrap Connection

// Layer 2: Connection level
Connection.prepareStatement(sql) → wrap Statement, capture SQL
Connection.createStatement() → wrap Statement

// Layer 3: Statement level
Statement.execute(sql) → validate SQL via interceptor
PreparedStatement.execute() → validate captured SQL
```

### Step 4: Composition Pattern Implementation
**Design Pattern**: Composition over Inheritance

**Class Relationships**:
```
HikariSqlSafetyProxyFactory
    |
    | creates proxy with
    ↓
DataSourceProxyHandler
    |
    | composes
    ↓
HikariJdbcInterceptor
    ↑
    | extends
    |
JdbcInterceptorBase (sql-guard-jdbc-common)
```

**Benefits**:
- Proxy handlers focus on interception mechanics
- `HikariJdbcInterceptor` handles validation logic
- Clear separation of concerns
- Each layer testable independently

### Step 5: Version Compatibility Strategy
**Challenge**: Support both HikariCP 4.x (Java 8) and 5.x (Java 11+)

**Solution**: Reflection-based API detection + Maven profiles

**Maven Profiles**:
```xml
<profiles>
    <profile>
        <id>hikari-4x</id>
        <properties>
            <hikaricp.version>4.0.3</hikaricp.version>
        </properties>
    </profile>
    <profile>
        <id>hikari-5x</id>
        <properties>
            <hikaricp.version>5.1.0</hikaricp.version>
        </properties>
    </profile>
</profiles>
```

**Testing Both Versions**:
```bash
# Test with HikariCP 4.x
mvn test -Phikari-4x

# Test with HikariCP 5.x
mvn test -Phikari-5x
```

### Step 6: Backward Compatibility Preservation
**100% Compatibility Requirement**: All 10 existing HikariCP tests must pass.

**Existing Tests to Preserve**:
```
sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/hikari/
├── ConnectionInvocationHandlerTest.java (must pass)
├── HikariConfigurationTest.java (must pass)
├── HikariEdgeCasesTest.java (must pass)
├── HikariIntegrationTest.java (must pass)
└── ... (6 more test files)
```

---

## Test Matrix (25+ tests)

| Test Class | Test Count | Purpose |
|------------|-----------|---------|
| HikariModuleIsolationTest | 10 tests | Verify independent compilation, no Druid/P6Spy deps, HikariCP 4.x/5.x |
| HikariProxyRefactoringTest | 10 tests | Verify composition pattern, multi-layer proxy chain |
| HikariIntegrationTest | 5 tests | End-to-end validation, Spring Boot auto-config |
| **TOTAL** | **25 tests** | **Complete TDD coverage** |

---

## Acceptance Criteria

- [ ] **Module Structure**: `sql-guard-jdbc-hikari` module created with correct POM
- [ ] **Dependency Isolation**: ZERO Druid/P6Spy dependencies (Maven Enforcer passes)
- [ ] **Composition Pattern**: Proxy handlers compose `HikariJdbcInterceptor`
- [ ] **Multi-layer Proxy**: Three-layer JDK Dynamic Proxy chain working
- [ ] **HikariInterceptorConfig**: Interface extends `JdbcInterceptorConfig` with HikariCP-specific properties
- [ ] **Version Compatibility**: HikariCP 4.x and 5.x both supported
- [ ] **Spring Boot Auto-Config**: `@ConditionalOnClass(HikariDataSource.class)` works
- [ ] **Test Coverage**: All 25+ new tests passing
- [ ] **Existing Tests**: All 10 existing HikariCP tests passing (100% backward compatibility)
- [ ] **Compilation**: Module compiles independently
- [ ] **Maven Enforcer**: Dependency validation passing

---

## Reference Documents

### Required Reading
1. **Test Design Document**: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md`
2. **Task 11.2 Memory Log**: `.apm/Memory/Phase_11_JDBC_Module_Separation/Task_11_2_Common_Module_Extraction.md`
3. **Common Module Code**: `sql-guard-jdbc-common/src/main/java/com/footstone/sqlguard/interceptor/jdbc/common/`

### Current Codebase References
- **Current HikariCP Proxy**: `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/hikari/HikariSqlSafetyProxyFactory.java`
- **Current Proxy Handlers**: `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/hikari/*ProxyHandler.java`
- **Current HikariCP Tests**: `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/hikari/`

---

## Success Metrics

- [ ] **25+ new tests** passing
- [ ] **10 existing tests** passing (backward compatibility)
- [ ] **HikariCP module** compiles independently
- [ ] **Maven Enforcer** validation passing
- [ ] **Multi-layer proxy chain** working correctly
- [ ] **HikariCP 4.x** tests passing
- [ ] **HikariCP 5.x** tests passing
- [ ] **Spring Boot auto-config** functional
- [ ] **Code review** passed

---

## Notes for Implementation Agent

**Agent_Core_Engine_Foundation**: This task runs IN PARALLEL with Tasks 11.3 (Druid) and 11.5 (P6Spy).

**Critical Requirements**:
1. **TDD Rigor**: Write tests BEFORE implementation
2. **Module Isolation**: Zero Druid/P6Spy dependencies
3. **Multi-layer Proxy**: Properly implement three-layer JDK Dynamic Proxy chain
4. **Version Compatibility**: Test with both HikariCP 4.x and 5.x
5. **Backward Compatibility**: 100% - all existing tests must pass

**Timeline**: 2 days (can execute in parallel with 11.3 and 11.5).

---

**Task Assignment Complete. Agent_Core_Engine_Foundation may begin execution in parallel with Tasks 11.3 and 11.5.**
