---
Task_ID: 11.5
Task_Name: P6Spy Module Separation
Assigned_Agent: Agent_Core_Engine_Foundation
Phase: Phase 11 - JDBC Module Separation
Priority: HIGH (Can run in parallel with 11.3, 11.4)
Estimated_Duration: 2 days
Dependencies: Task 11.2 (Common Module - COMPLETED)
Parallel_With: Task 11.3 (Druid), Task 11.4 (HikariCP)
Output_Location: .apm/Memory/Phase_11_JDBC_Module_Separation/Task_11_5_P6Spy_Module_Separation.md
---

# Task 11.5 – P6Spy Module Separation

## Objective

Create independent `sql-guard-jdbc-p6spy` module containing ONLY P6Spy-specific implementations, providing universal JDBC interception fallback for any connection pool or bare JDBC usage, implementing SPI-based `P6Factory` registration for ServiceLoader discovery.

**CRITICAL**: This task can execute IN PARALLEL with Tasks 11.3 (Druid) and 11.4 (HikariCP).

---

## Context

### Task 11.2 Completion
**Foundation Ready** ✅:
- `sql-guard-jdbc-common` module created with unified abstractions
- `ViolationStrategy` enum unified
- `JdbcInterceptorBase` template method pattern available
- `JdbcInterceptorConfig` interface defined
- 35 tests passing, Maven Enforcer validates dependencies

### Current State - P6Spy Implementation
**Location**: `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/p6spy/`

**Files**:
- `P6SpySqlSafetyListener.java` - Current P6Spy listener implementation
- `P6SpySqlSafetyModule.java` - P6Factory implementation
- `ViolationStrategy.java` - Deprecated (now in common module)
- `META-INF/services/com.p6spy.engine.spy.P6Factory` - SPI registration

**Existing Tests**: 9 P6Spy-specific tests (currently passing)

### Target Architecture
```
sql-guard-jdbc-p6spy/
├── pom.xml (p6spy:provided, jdbc-common:compile)
├── src/main/java/com/footstone/sqlguard/interceptor/jdbc/p6spy/
│   ├── P6SpySqlSafetyListener.java        # Refactored to compose JdbcInterceptorBase
│   ├── P6SpySqlAuditListener.java         # New audit listener
│   ├── P6SpySqlSafetyModule.java          # P6Factory SPI implementation
│   ├── P6SpyInterceptorConfig.java        # Extends JdbcInterceptorConfig
│   └── P6SpyJdbcInterceptor.java          # JdbcInterceptorBase implementation
└── src/main/resources/
    ├── META-INF/services/com.p6spy.engine.spy.P6Factory  # SPI registration
    └── spy.properties.template                            # Configuration template
```

---

## Expected Outputs

### 1. Maven Module Structure
**Location**: `sql-guard-jdbc-p6spy/pom.xml`

**POM Requirements**:
```xml
<dependencies>
    <!-- Common JDBC abstractions -->
    <dependency>
        <groupId>com.footstone</groupId>
        <artifactId>sql-guard-jdbc-common</artifactId>
    </dependency>

    <!-- P6Spy dependency in provided scope -->
    <dependency>
        <groupId>p6spy</groupId>
        <artifactId>p6spy</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- NO Druid or HikariCP dependencies -->
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
                            <exclude>com.zaxxer:HikariCP</exclude>
                        </excludes>
                    </bannedDependencies>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 2. P6SpyJdbcInterceptor (New)
**Location**: `sql-guard-jdbc-p6spy/src/main/java/com/footstone/sqlguard/interceptor/jdbc/p6spy/P6SpyJdbcInterceptor.java`

**Purpose**: Concrete implementation of `JdbcInterceptorBase` for P6Spy-specific logic

**Requirements**:
```java
public class P6SpyJdbcInterceptor extends JdbcInterceptorBase {
    private final P6SpyInterceptorConfig config;
    private final SqlSafetyValidator validator;

    @Override
    protected SqlContext buildSqlContext(String sql, Object... params) {
        // P6Spy-specific context building
        return SqlContextBuilder.buildContext(sql, params, "p6spy");
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

    // Optional hooks for P6Spy-specific behavior
    @Override
    protected void beforeValidation(String sql, Object... params) {
        // P6Spy pre-validation logic
    }
}
```

### 3. P6SpySqlSafetyListener (Refactored)
**Location**: `sql-guard-jdbc-p6spy/src/main/java/com/footstone/sqlguard/interceptor/jdbc/p6spy/P6SpySqlSafetyListener.java`

**P6Spy-Specific Pattern**: **JdbcEventListener Integration**

**Key Architecture**:
- Extends `JdbcEventListener` (P6Spy's listener base class)
- Implements `onBeforeAnyExecute()` lifecycle hook
- Composes `P6SpyJdbcInterceptor` for validation logic
- Extracts SQL from P6Spy's prepared statement context

**Refactoring Approach**:
```java
public class P6SpySqlSafetyListener extends JdbcEventListener {
    private final P6SpyJdbcInterceptor interceptor; // Composition

    @Override
    public void onBeforeAnyExecute(StatementInformation statementInformation) {
        // Extract SQL from P6Spy context
        String sql = statementInformation.getSqlWithValues();

        // Intercept SQL before execution via composed interceptor
        interceptor.interceptSql(sql);
    }

    @Override
    public void onAfterAnyExecute(
            StatementInformation statementInformation,
            long timeElapsedNanos,
            SQLException e) {
        // Optional: Performance metrics, audit logging
    }
}
```

**P6Spy Lifecycle Hooks**:
- `onBeforeAnyExecute()` - Called before any SQL execution (critical interception point)
- `onAfterAnyExecute()` - Called after SQL execution (for metrics, audit)
- `onBeforeAddBatch()` - Called before batch operations
- `onAfterGetResultSet()` - Called after result set retrieval

### 4. P6SpySqlSafetyModule (SPI Registration)
**Location**: `sql-guard-jdbc-p6spy/src/main/java/com/footstone/sqlguard/interceptor/jdbc/p6spy/P6SpySqlSafetyModule.java`

**P6Spy-Specific Pattern**: **SPI-based ServiceLoader Discovery**

**Critical Path**: P6Spy uses Java SPI to automatically discover and load custom modules

**Implementation**:
```java
public class P6SpySqlSafetyModule implements P6Factory {

    @Override
    public P6LoadableOptions getOptions(P6OptionsSource optionsSource) {
        // P6Spy options integration (system properties)
        return new P6SpySqlGuardOptions(optionsSource);
    }

    @Override
    public ModuleFactory getModuleFactory() {
        return new ModuleFactory() {
            @Override
            public Module createModule() {
                return new Module() {
                    @Override
                    public void load(P6ModuleManager manager) {
                        // Register our listener with P6Spy
                        P6SpyJdbcInterceptor interceptor = createInterceptor();
                        P6SpySqlSafetyListener listener = new P6SpySqlSafetyListener(interceptor);
                        manager.register(listener);
                    }

                    @Override
                    public void unload() {
                        // Cleanup on shutdown
                    }
                };
            }
        };
    }
}
```

### 5. SPI Registration File (CRITICAL)
**Location**: `sql-guard-jdbc-p6spy/src/main/resources/META-INF/services/com.p6spy.engine.spy.P6Factory`

**Content**:
```
com.footstone.sqlguard.interceptor.jdbc.p6spy.P6SpySqlSafetyModule
```

**How It Works**:
1. P6Spy engine uses `ServiceLoader.load(P6Factory.class)` on startup
2. ServiceLoader reads `META-INF/services/com.p6spy.engine.spy.P6Factory`
3. Finds `P6SpySqlSafetyModule` class name
4. Instantiates and registers our module automatically
5. Our listener is now active for ALL JDBC operations

**Why This Is Critical**:
- **Zero configuration needed** - automatic discovery
- **Universal coverage** - works with any JDBC driver/pool
- **No code changes** - just add P6Spy to classpath

### 6. P6SpyInterceptorConfig (New)
**Location**: `sql-guard-jdbc-p6spy/src/main/java/com/footstone/sqlguard/interceptor/jdbc/p6spy/P6SpyInterceptorConfig.java`

**Interface Definition**:
```java
public interface P6SpyInterceptorConfig extends JdbcInterceptorConfig {
    // Inherited from JdbcInterceptorConfig:
    // - boolean isEnabled()
    // - ViolationStrategy getStrategy()
    // - boolean isAuditEnabled()
    // - List<String> getExcludePatterns()

    // P6Spy-specific properties (system property-based):

    /**
     * System property prefix for P6Spy configuration.
     * P6Spy limitation: no programmatic config API.
     * Properties must be set as system properties or in spy.properties file.
     * @return property prefix (e.g., "sqlguard.p6spy")
     */
    String getPropertyPrefix();

    /**
     * Whether to log actual SQL with parameter values substituted.
     * P6Spy feature: getSqlWithValues() vs getSql().
     * @return true to log parameterized SQL
     */
    boolean isLogParameterizedSql();
}
```

**P6Spy Configuration Limitation**:
- P6Spy does NOT support programmatic configuration
- Must use **system properties** or `spy.properties` file
- Property format: `sqlguard.p6spy.enabled=true`

### 7. spy.properties Template
**Location**: `sql-guard-jdbc-p6spy/src/main/resources/spy.properties.template`

**Template Content**:
```properties
# P6Spy Configuration Template for SQL Guard

# Module list (append our module)
modulelist=com.p6spy.engine.spy.P6SpyFactory,com.footstone.sqlguard.interceptor.jdbc.p6spy.P6SpySqlSafetyModule

# SQL Guard P6Spy Configuration
sqlguard.p6spy.enabled=true
sqlguard.p6spy.strategy=WARN
sqlguard.p6spy.audit.enabled=false
sqlguard.p6spy.logParameterizedSql=true

# P6Spy Standard Configuration
driverlist=org.h2.Driver
appender=com.p6spy.engine.spy.appender.Slf4JLogger
logMessageFormat=com.p6spy.engine.spy.appender.SingleLineFormat
```

---

## Implementation Guidance

### Step 1: TDD - Write Tests First (25+ tests)
**Reference**: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md` (P6Spy-specific tests)

**Test Classes to Implement**:

1. **P6SpyModuleIsolationTest** (10 tests):
   - `testP6SpyModule_noDruidDependency_compiles()`
   - `testP6SpyModule_noHikariDependency_compiles()`
   - `testP6SpyModule_onlyP6SpyProvided_works()`
   - `testP6SpyModule_commonModuleDependency_resolves()`
   - `testP6SpyModule_classLoading_noOtherPoolsRequired()`
   - `testP6SpyModule_independentJar_packages()`
   - `testP6SpyModule_spiRegistration_works()`
   - `testP6SpyModule_spyProperties_loads()`
   - `testP6SpyModule_runtimeClasspath_minimal()`
   - `testP6SpyModule_testClasspath_isolated()`

2. **P6SpyListenerRefactoringTest** (10 tests):
   - `testP6SpyListener_composesJdbcInterceptorBase()`
   - `testP6SpyListener_jdbcEventListener_implements()`
   - `testP6SpyListener_onBeforeAnyExecute_intercepts()`
   - `testP6SpyListener_getSqlWithValues_extracts()`
   - `testP6SpyListener_parameterSubstitution_works()`
   - `testP6SpyListener_violationStrategy_usesCommon()`
   - `testP6SpyListener_auditEvent_usesCommonBuilder()`
   - `testP6SpyListener_configuration_extendsCommon()`
   - `testP6SpyModule_serviceLoader_discovers()`
   - `testP6SpyModule_systemProperty_configures()`

3. **P6SpyIntegrationTest** (5 tests):
   - `testP6Spy_endToEnd_interceptsQueries()`
   - `testP6Spy_withBareJdbc_works()`
   - `testP6Spy_withC3P0_works()`
   - `testP6Spy_universalCoverage_handles()`
   - `testP6Spy_performance_documentsOverhead()`

### Step 2: Module Isolation - P6Spy-Specific
**CRITICAL**: `sql-guard-jdbc-p6spy` must have ZERO Druid or HikariCP dependencies.

**Validation Commands**:
```bash
# Build P6Spy module in isolation
cd sql-guard-jdbc-p6spy
mvn clean compile -DskipTests

# Verify dependency tree
mvn dependency:tree | grep -i "druid\|hikari"
# Expected: NO MATCHES

# Maven Enforcer validation
mvn enforcer:enforce
# Expected: BUILD SUCCESS
```

### Step 3: SPI Registration Architecture
**P6Spy-Specific Pattern**: Java SPI (Service Provider Interface)

**Key Concepts**:
- **ServiceLoader**: Java standard for plugin discovery
- **P6Factory**: P6Spy's SPI interface for custom modules
- **Automatic Discovery**: No configuration needed, just classpath presence
- **META-INF/services**: Standard location for SPI registration files

**SPI Registration Validation**:
```bash
# Verify SPI file exists in JAR
jar tf sql-guard-jdbc-p6spy.jar | grep META-INF/services
# Expected: META-INF/services/com.p6spy.engine.spy.P6Factory

# Verify file content
jar xf sql-guard-jdbc-p6spy.jar META-INF/services/com.p6spy.engine.spy.P6Factory
cat META-INF/services/com.p6spy.engine.spy.P6Factory
# Expected: com.footstone.sqlguard.interceptor.jdbc.p6spy.P6SpySqlSafetyModule
```

**Testing SPI Discovery**:
```java
@Test
void testP6SpyModule_serviceLoader_discovers() {
    ServiceLoader<P6Factory> loader = ServiceLoader.load(P6Factory.class);
    List<P6Factory> factories = StreamSupport.stream(loader.spliterator(), false)
        .collect(Collectors.toList());

    // Verify our module is discovered
    boolean found = factories.stream()
        .anyMatch(f -> f instanceof P6SpySqlSafetyModule);

    assertThat(found).isTrue();
}
```

### Step 4: Universal JDBC Coverage
**P6Spy Unique Value**: Works with ANY JDBC driver/connection pool

**Universal Coverage Testing** (validate with multiple pools):
1. **Bare JDBC** (DriverManager):
```java
@Test
void testP6Spy_withBareJdbc_works() {
    // Use DriverManager directly (no connection pool)
    Connection conn = DriverManager.getConnection("jdbc:p6spy:h2:mem:test");
    // SQL should be intercepted
}
```

2. **C3P0 Connection Pool**:
```java
@Test
void testP6Spy_withC3P0_works() {
    ComboPooledDataSource pool = new ComboPooledDataSource();
    pool.setJdbcUrl("jdbc:p6spy:h2:mem:test");
    // SQL should be intercepted
}
```

3. **Apache DBCP2**:
```java
@Test
void testP6Spy_withDBCP2_works() {
    BasicDataSource pool = new BasicDataSource();
    pool.setUrl("jdbc:p6spy:h2:mem:test");
    // SQL should be intercepted
}
```

**Key Insight**: P6Spy wraps the JDBC driver itself, so it works with ANY pool or no pool at all. This is the "universal fallback" pattern.

### Step 5: Performance Overhead Documentation
**Known Trade-off**: P6Spy has ~15% overhead compared to direct JDBC

**Why It's Acceptable**:
- Universal coverage > performance for many use cases
- Overhead is predictable and consistent
- Can be disabled in production if needed
- Valuable for development and testing environments

**Performance Testing**:
```java
@Test
void testP6Spy_performance_documentsOverhead() {
    // Baseline: Direct JDBC (no P6Spy)
    long baselineThroughput = measureThroughput(directDataSource);

    // With P6Spy interception
    long p6spyThroughput = measureThroughput(p6spyDataSource);

    // Calculate overhead percentage
    double overheadPercent = ((baselineThroughput - p6spyThroughput) / (double) baselineThroughput) * 100;

    // Document in test output
    logger.info("P6Spy overhead: {}%", overheadPercent);

    // Overhead should be < 20% (typically ~15%)
    assertThat(overheadPercent).isLessThan(20.0);
}
```

### Step 6: System Property Configuration
**P6Spy Limitation**: No programmatic configuration API

**Configuration Strategy**:
```java
// Set system properties before P6Spy initialization
System.setProperty("sqlguard.p6spy.enabled", "true");
System.setProperty("sqlguard.p6spy.strategy", "WARN");

// Or use spy.properties file on classpath
```

**Configuration Loading**:
```java
public class P6SpyConfigLoader {
    public static P6SpyInterceptorConfig loadConfig() {
        String enabled = System.getProperty("sqlguard.p6spy.enabled", "true");
        String strategy = System.getProperty("sqlguard.p6spy.strategy", "WARN");

        return new P6SpyInterceptorConfig() {
            @Override
            public boolean isEnabled() {
                return Boolean.parseBoolean(enabled);
            }

            @Override
            public ViolationStrategy getStrategy() {
                return ViolationStrategy.valueOf(strategy);
            }
        };
    }
}
```

### Step 7: Backward Compatibility Preservation
**100% Compatibility Requirement**: All 9 existing P6Spy tests must pass.

**Existing Tests to Preserve**:
```
sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/p6spy/
├── ModuleRegistrationTest.java (must pass)
├── OnBeforeAnyExecuteTest.java (must pass)
├── P6SpyIntegrationTest.java (must pass)
└── ... (6 more test files)
```

---

## Test Matrix (25+ tests)

| Test Class | Test Count | Purpose |
|------------|-----------|---------|
| P6SpyModuleIsolationTest | 10 tests | Verify independent compilation, no Druid/HikariCP deps, SPI registration |
| P6SpyListenerRefactoringTest | 10 tests | Verify composition pattern, JdbcEventListener integration |
| P6SpyIntegrationTest | 5 tests | Universal coverage validation (bare JDBC, C3P0, DBCP2), performance |
| **TOTAL** | **25 tests** | **Complete TDD coverage** |

---

## Acceptance Criteria

- [ ] **Module Structure**: `sql-guard-jdbc-p6spy` module created with correct POM
- [ ] **Dependency Isolation**: ZERO Druid/HikariCP dependencies (Maven Enforcer passes)
- [ ] **Composition Pattern**: `P6SpySqlSafetyListener` composes `P6SpyJdbcInterceptor`
- [ ] **SPI Registration**: `META-INF/services/com.p6spy.engine.spy.P6Factory` file present and correct
- [ ] **JdbcEventListener**: Properly implements P6Spy lifecycle hooks
- [ ] **P6SpyInterceptorConfig**: Interface extends `JdbcInterceptorConfig` with P6Spy-specific properties
- [ ] **Universal Coverage**: Tested with bare JDBC, C3P0, DBCP2
- [ ] **System Property Config**: Configuration loading from system properties works
- [ ] **Test Coverage**: All 25+ new tests passing
- [ ] **Existing Tests**: All 9 existing P6Spy tests passing (100% backward compatibility)
- [ ] **Compilation**: Module compiles independently
- [ ] **Maven Enforcer**: Dependency validation passing
- [ ] **Performance Overhead**: Documented and < 20%

---

## Reference Documents

### Required Reading
1. **Test Design Document**: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md`
2. **Task 11.2 Memory Log**: `.apm/Memory/Phase_11_JDBC_Module_Separation/Task_11_2_Common_Module_Extraction.md`
3. **Common Module Code**: `sql-guard-jdbc-common/src/main/java/com/footstone/sqlguard/interceptor/jdbc/common/`

### Current Codebase References
- **Current P6Spy Listener**: `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/p6spy/P6SpySqlSafetyListener.java`
- **Current P6Spy Module**: `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/p6spy/P6SpySqlSafetyModule.java`
- **Current SPI File**: `sql-guard-jdbc/src/main/resources/META-INF/services/com.p6spy.engine.spy.P6Factory`
- **Current P6Spy Tests**: `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/p6spy/`

### P6Spy Documentation
- P6Spy SPI mechanism: P6Factory interface
- JdbcEventListener lifecycle hooks

---

## Success Metrics

- [ ] **25+ new tests** passing
- [ ] **9 existing tests** passing (backward compatibility)
- [ ] **P6Spy module** compiles independently
- [ ] **Maven Enforcer** validation passing
- [ ] **SPI registration** working (ServiceLoader discovers module)
- [ ] **Universal coverage** validated (bare JDBC, C3P0, DBCP2)
- [ ] **Performance overhead** documented and acceptable
- [ ] **System property config** functional
- [ ] **Code review** passed

---

## Notes for Implementation Agent

**Agent_Core_Engine_Foundation**: This task runs IN PARALLEL with Tasks 11.3 (Druid) and 11.4 (HikariCP).

**Critical Requirements**:
1. **TDD Rigor**: Write tests BEFORE implementation
2. **Module Isolation**: Zero Druid/HikariCP dependencies
3. **SPI Registration**: CRITICAL - must be correct or P6Spy won't load module
4. **Universal Coverage**: Test with multiple connection pools and bare JDBC
5. **Backward Compatibility**: 100% - all existing tests must pass
6. **Performance Documentation**: Measure and document overhead

**Timeline**: 2 days (can execute in parallel with 11.3 and 11.4).

---

**Task Assignment Complete. Agent_Core_Engine_Foundation may begin execution in parallel with Tasks 11.3 and 11.4.**
