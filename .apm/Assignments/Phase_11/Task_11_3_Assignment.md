---
Task_ID: 11.3
Task_Name: Druid Module Separation
Assigned_Agent: Agent_Core_Engine_Foundation
Phase: Phase 11 - JDBC Module Separation
Priority: HIGH (Can run in parallel with 11.4, 11.5)
Estimated_Duration: 2 days
Dependencies: Task 11.2 (Common Module - COMPLETED)
Parallel_With: Task 11.4 (HikariCP), Task 11.5 (P6Spy)
Output_Location: .apm/Memory/Phase_11_JDBC_Module_Separation/Task_11_3_Druid_Module_Separation.md
---

# Task 11.3 – Druid Module Separation

## Objective

Create independent `sql-guard-jdbc-druid` module containing ONLY Druid-specific implementations, depending on `sql-guard-jdbc-common` for shared abstractions, ensuring users who only use Druid don't pull in HikariCP or P6Spy dependencies, refactoring `DruidSqlSafetyFilter` to compose `JdbcInterceptorBase` instead of duplicating logic.

**CRITICAL**: This task can execute IN PARALLEL with Tasks 11.4 (HikariCP) and 11.5 (P6Spy).

---

## Context

### Task 11.2 Completion
**Foundation Ready** ✅:
- `sql-guard-jdbc-common` module created with unified abstractions
- `ViolationStrategy` enum unified
- `JdbcInterceptorBase` template method pattern available
- `JdbcInterceptorConfig` interface defined
- 35 tests passing, Maven Enforcer validates dependencies

### Current State - Druid Implementation
**Location**: `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/druid/`

**Files**:
- `DruidSqlSafetyFilter.java` - Current Druid Filter implementation
- `DruidSqlSafetyFilterConfiguration.java` - Spring Boot auto-configuration
- `ViolationStrategy.java` - Deprecated (now in common module)

**Existing Tests**: 12 Druid-specific tests (all currently passing)

### Target Architecture
```
sql-guard-jdbc-druid/
├── pom.xml (druid:provided, jdbc-common:compile)
└── src/main/java/com/footstone/sqlguard/interceptor/jdbc/druid/
    ├── DruidSqlSafetyFilter.java          # Refactored to compose JdbcInterceptorBase
    ├── DruidSqlAuditFilter.java           # New audit filter
    ├── DruidSqlSafetyFilterConfiguration.java
    ├── DruidInterceptorConfig.java        # Extends JdbcInterceptorConfig
    └── DruidJdbcInterceptor.java          # JdbcInterceptorBase implementation
```

---

## Expected Outputs

### 1. Maven Module Structure
**Location**: `sql-guard-jdbc-druid/pom.xml`

**POM Requirements**:
```xml
<dependencies>
    <!-- Common JDBC abstractions -->
    <dependency>
        <groupId>com.footstone</groupId>
        <artifactId>sql-guard-jdbc-common</artifactId>
    </dependency>

    <!-- Druid dependency in provided scope -->
    <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>druid</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- NO HikariCP or P6Spy dependencies -->
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
                            <exclude>com.zaxxer:HikariCP</exclude>
                            <exclude>p6spy:p6spy</exclude>
                        </excludes>
                    </bannedDependencies>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 2. DruidJdbcInterceptor (New)
**Location**: `sql-guard-jdbc-druid/src/main/java/com/footstone/sqlguard/interceptor/jdbc/druid/DruidJdbcInterceptor.java`

**Purpose**: Concrete implementation of `JdbcInterceptorBase` for Druid-specific logic

**Requirements**:
```java
public class DruidJdbcInterceptor extends JdbcInterceptorBase {
    private final DruidInterceptorConfig config;
    private final SqlSafetyValidator validator;

    @Override
    protected SqlContext buildSqlContext(String sql, Object... params) {
        // Druid-specific context building
        return SqlContextBuilder.buildContext(sql, params, "druid");
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

    // Optional hooks for Druid-specific behavior
    @Override
    protected void beforeValidation(String sql, Object... params) {
        // Druid pre-validation logic
    }
}
```

### 3. DruidSqlSafetyFilter (Refactored)
**Location**: `sql-guard-jdbc-druid/src/main/java/com/footstone/sqlguard/interceptor/jdbc/druid/DruidSqlSafetyFilter.java`

**Druid-Specific Pattern**: **Filter Chain Mechanism**

**Key Architecture**:
- Extends `FilterAdapter` (Druid's Filter base class)
- Integrates into Druid's Filter chain architecture
- Leverages Druid's connection proxy and statement proxy lifecycle events
- Uses **composition** to delegate to `DruidJdbcInterceptor` (which extends `JdbcInterceptorBase`)

**Refactoring Approach**:
```java
public class DruidSqlSafetyFilter extends FilterAdapter {
    private final DruidJdbcInterceptor interceptor; // Composition

    @Override
    public PreparedStatementProxy connection_prepareStatement(
            FilterChain chain, ConnectionProxy connection, String sql)
            throws SQLException {
        // Intercept SQL before execution
        interceptor.interceptSql(sql);
        return super.connection_prepareStatement(chain, connection, sql);
    }

    @Override
    public ResultSetProxy statement_executeQuery(
            FilterChain chain, StatementProxy statement, String sql)
            throws SQLException {
        // Intercept query execution
        interceptor.interceptSql(sql);
        return super.statement_executeQuery(chain, statement, sql);
    }

    // Similar methods for: statement_execute, statement_executeUpdate, etc.
}
```

**Druid Filter Chain Integration Points**:
- `connection_prepareStatement()` - Intercept prepared statement creation
- `statement_executeQuery()` - Intercept SELECT queries
- `statement_execute()` - Intercept generic execute
- `statement_executeUpdate()` - Intercept INSERT/UPDATE/DELETE
- `preparedStatement_execute()` - Intercept prepared statement execution

### 4. DruidSqlAuditFilter (New)
**Location**: `sql-guard-jdbc-druid/src/main/java/com/footstone/sqlguard/interceptor/jdbc/druid/DruidSqlAuditFilter.java`

**Purpose**: Audit logging for Druid SQL executions

**Requirements**:
- Extends `FilterAdapter`
- Composes `DruidJdbcInterceptor` for audit event creation
- Uses `JdbcAuditEventBuilder` from common module
- Logs to audit system (via `sql-guard-audit-api`)

### 5. DruidInterceptorConfig (New)
**Location**: `sql-guard-jdbc-druid/src/main/java/com/footstone/sqlguard/interceptor/jdbc/druid/DruidInterceptorConfig.java`

**Interface Definition**:
```java
public interface DruidInterceptorConfig extends JdbcInterceptorConfig {
    // Inherited from JdbcInterceptorConfig:
    // - boolean isEnabled()
    // - ViolationStrategy getStrategy()
    // - boolean isAuditEnabled()
    // - List<String> getExcludePatterns()

    // Druid-specific properties:

    /**
     * Filter position in Druid's Filter chain.
     * Enables placement before/after other Druid filters (StatFilter, WallFilter).
     * @return filter position (0 = first, negative = last)
     */
    int getFilterPosition();

    /**
     * Enable connection proxy interception.
     * @return true if connection-level interception enabled
     */
    boolean isConnectionProxyEnabled();
}
```

### 6. DruidSqlSafetyFilterConfiguration (Updated)
**Location**: `sql-guard-jdbc-druid/src/main/java/com/footstone/sqlguard/interceptor/jdbc/druid/DruidSqlSafetyFilterConfiguration.java`

**Spring Boot Auto-Configuration**:
```java
@Configuration
@ConditionalOnClass(DruidDataSource.class)
@ConditionalOnProperty(prefix = "sqlguard.druid", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(DruidSqlGuardProperties.class)
public class DruidSqlSafetyFilterConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DruidSqlSafetyFilter druidSqlSafetyFilter(
            DruidSqlGuardProperties properties,
            SqlSafetyValidator validator) {
        DruidInterceptorConfig config = properties; // Implements interface
        DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
        return new DruidSqlSafetyFilter(interceptor);
    }
}
```

---

## Implementation Guidance

### Step 1: TDD - Write Tests First (25+ tests)
**Reference**: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md` (Druid-specific tests)

**Test Classes to Implement**:

1. **DruidModuleIsolationTest** (10 tests):
   - `testDruidModule_noHikariDependency_compiles()`
   - `testDruidModule_noP6SpyDependency_compiles()`
   - `testDruidModule_onlyDruidProvided_works()`
   - `testDruidModule_commonModuleDependency_resolves()`
   - `testDruidModule_classLoading_noOtherPoolsRequired()`
   - `testDruidModule_independentJar_packages()`
   - `testDruidModule_transitiveDeps_verified()`
   - `testDruidModule_runtimeClasspath_minimal()`
   - `testDruidModule_testClasspath_isolated()`
   - `testDruidModule_mavenShade_excludesOthers()`

2. **DruidFilterRefactoringTest** (10 tests):
   - `testDruidFilter_composesJdbcInterceptorBase()`
   - `testDruidFilter_templateMethod_delegates()`
   - `testDruidFilter_filterAdapter_integrates()`
   - `testDruidFilter_connectionProxy_intercepts()`
   - `testDruidFilter_statementProxy_intercepts()`
   - `testDruidFilter_preparedStatement_intercepts()`
   - `testDruidFilter_callableStatement_intercepts()`
   - `testDruidFilter_violationStrategy_usesCommon()`
   - `testDruidFilter_auditEvent_usesCommonBuilder()`
   - `testDruidFilter_configuration_extendsCommon()`

3. **DruidIntegrationTest** (5 tests):
   - `testDruid_endToEnd_interceptsQueries()`
   - `testDruid_withH2_validates()`
   - `testDruid_multipleDataSources_handles()`
   - `testDruid_springBoot_autoConfigures()`
   - `testDruid_performance_meetsBaseline()`

### Step 2: Module Isolation - Druid-Specific
**CRITICAL**: `sql-guard-jdbc-druid` must have ZERO HikariCP or P6Spy dependencies.

**Validation Commands**:
```bash
# Build Druid module in isolation
cd sql-guard-jdbc-druid
mvn clean compile -DskipTests

# Verify dependency tree
mvn dependency:tree | grep -i "hikari\|p6spy"
# Expected: NO MATCHES

# Maven Enforcer validation
mvn enforcer:enforce
# Expected: BUILD SUCCESS
```

### Step 3: Druid Filter Chain Architecture
**Druid-Specific Pattern**: Filter chain mechanism with `FilterAdapter`

**Key Concepts**:
- **FilterAdapter**: Base class for Druid filters, provides lifecycle hooks
- **FilterChain**: Chain of responsibility pattern, filters execute in order
- **ConnectionProxy**: Druid's proxy for Connection objects
- **StatementProxy**: Druid's proxy for Statement objects

**Filter Position Strategy**:
```java
// DruidInterceptorConfig.getFilterPosition() enables ordering:
// - Position 0: Execute first (before StatFilter, WallFilter)
// - Position -1: Execute last (after all other filters)
// - Custom position: Fine-grained control for specific use cases
```

**Integration Points**:
```java
// Connection-level interception
connection_prepareStatement(FilterChain, ConnectionProxy, String sql)
connection_prepareCall(FilterChain, ConnectionProxy, String sql)

// Statement-level interception
statement_executeQuery(FilterChain, StatementProxy, String sql)
statement_execute(FilterChain, StatementProxy, String sql)
statement_executeUpdate(FilterChain, StatementProxy, String sql)

// PreparedStatement-level interception
preparedStatement_execute(FilterChain, PreparedStatementProxy)
preparedStatement_executeQuery(FilterChain, PreparedStatementProxy)
preparedStatement_executeUpdate(FilterChain, PreparedStatementProxy)
```

### Step 4: Composition Pattern Implementation
**Design Pattern**: Composition over Inheritance

**Benefits**:
- `DruidSqlSafetyFilter` focuses on Druid Filter chain integration
- `DruidJdbcInterceptor` handles validation logic (via `JdbcInterceptorBase`)
- Clear separation of concerns
- Testability - can test interceptor independently

**Class Relationships**:
```
FilterAdapter (Druid)
    ↑
    | extends
    |
DruidSqlSafetyFilter
    |
    | composes (has-a)
    ↓
DruidJdbcInterceptor
    ↑
    | extends
    |
JdbcInterceptorBase (sql-guard-jdbc-common)
```

### Step 5: Backward Compatibility Preservation
**100% Compatibility Requirement**: All 12 existing Druid tests must pass.

**Migration Strategy**:
- Move existing classes to new module structure
- Update package imports to `com.footstone.sqlguard.interceptor.jdbc.druid`
- Use unified `ViolationStrategy` from common module
- Preserve existing configuration properties
- Document migration path for users

**Existing Tests to Preserve**:
```
sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/druid/
├── DruidSqlSafetyFilterTest.java (must pass)
├── FilterExecutionOrderTest.java (must pass)
├── FilterRegistrationTest.java (must pass)
└── ValidateSqlMethodTest.java (must pass)
```

---

## Test Matrix (25+ tests)

| Test Class | Test Count | Purpose |
|------------|-----------|---------|
| DruidModuleIsolationTest | 10 tests | Verify independent compilation, no HikariCP/P6Spy deps |
| DruidFilterRefactoringTest | 10 tests | Verify composition pattern, Filter chain integration |
| DruidIntegrationTest | 5 tests | End-to-end validation, Spring Boot auto-config |
| **TOTAL** | **25 tests** | **Complete TDD coverage** |

---

## Acceptance Criteria

- [ ] **Module Structure**: `sql-guard-jdbc-druid` module created with correct POM
- [ ] **Dependency Isolation**: ZERO HikariCP/P6Spy dependencies (Maven Enforcer passes)
- [ ] **Composition Pattern**: `DruidSqlSafetyFilter` composes `DruidJdbcInterceptor`
- [ ] **Filter Chain Integration**: Druid FilterAdapter correctly implemented
- [ ] **DruidInterceptorConfig**: Interface extends `JdbcInterceptorConfig` with Druid-specific properties
- [ ] **Spring Boot Auto-Config**: `@ConditionalOnClass(DruidDataSource.class)` works
- [ ] **Test Coverage**: All 25+ new tests passing
- [ ] **Existing Tests**: All 12 existing Druid tests passing (100% backward compatibility)
- [ ] **Compilation**: Module compiles independently
- [ ] **Maven Enforcer**: Dependency validation passing

---

## Reference Documents

### Required Reading
1. **Test Design Document**: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md`
2. **Task 11.2 Memory Log**: `.apm/Memory/Phase_11_JDBC_Module_Separation/Task_11_2_Common_Module_Extraction.md`
3. **Common Module Code**: `sql-guard-jdbc-common/src/main/java/com/footstone/sqlguard/interceptor/jdbc/common/`

### Current Codebase References
- **Current Druid Filter**: `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/druid/DruidSqlSafetyFilter.java`
- **Current Druid Config**: `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/druid/DruidSqlSafetyFilterConfiguration.java`
- **Current Druid Tests**: `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/druid/`

### Druid Documentation
- Druid Filter API: Understanding FilterAdapter and FilterChain
- Druid Proxy mechanism: ConnectionProxy, StatementProxy

---

## Success Metrics

- [ ] **25+ new tests** passing
- [ ] **12 existing tests** passing (backward compatibility)
- [ ] **Druid module** compiles independently
- [ ] **Maven Enforcer** validation passing
- [ ] **Filter chain integration** working correctly
- [ ] **Spring Boot auto-config** functional
- [ ] **Code review** passed

---

## Notes for Implementation Agent

**Agent_Core_Engine_Foundation**: This task runs IN PARALLEL with Tasks 11.4 (HikariCP) and 11.5 (P6Spy).

**Critical Requirements**:
1. **TDD Rigor**: Write tests BEFORE implementation
2. **Module Isolation**: Zero HikariCP/P6Spy dependencies
3. **Composition Pattern**: Use composition, not inheritance duplication
4. **Druid Filter Chain**: Properly integrate with FilterAdapter lifecycle
5. **Backward Compatibility**: 100% - all existing tests must pass

**Timeline**: 2 days (can execute in parallel with 11.4 and 11.5).

---

**Task Assignment Complete. Agent_Core_Engine_Foundation may begin execution in parallel with Tasks 11.4 and 11.5.**
