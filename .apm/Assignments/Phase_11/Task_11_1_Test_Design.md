# Task 11.1 Test Design Document

## TDD Test Case Library for JDBC Module Separation

**Document Version**: 1.0  
**Created**: 2025-12-19  
**Author**: Agent_Testing_Validation  
**Phase**: Phase 11 - JDBC Module Separation  
**Status**: Design Complete

---

## 1. Introduction

### 1.1 TDD Philosophy for Phase 11

This document establishes the **Test-Driven Development (TDD)** test case library for Phase 11's JDBC module separation initiative. Following the TDD principle of "Red-Green-Refactor", all tests are designed **before** any implementation begins, ensuring:

1. **Design-First Approach**: Tests define module boundaries and contracts
2. **Acceptance Criteria Clarity**: Measurable success metrics established upfront
3. **Risk Mitigation**: Identify integration issues before coding begins
4. **Quality Baseline**: 40 tests ensure comprehensive coverage

### 1.2 Test Coverage Goals

| Category | Test Count | Purpose |
|----------|-----------|---------|
| Module Isolation | 15 tests | Verify independent compilation, dependency isolation |
| Backward Compatibility | 12 tests | Ensure existing code works unchanged |
| Performance Baselines | 13 tests | Establish performance targets, detect regression |
| **TOTAL** | **40 tests** | **Comprehensive TDD coverage** |

### 1.3 Phase 11 Scope Boundaries

**IN SCOPE** ✅:
- Module structure refactoring (extracting 4 modules from sql-guard-jdbc)
- Dependency isolation (POM configuration, provided scope, transitive deps)
- Code extraction to specialized modules (moving classes between modules)
- Backward compatibility (old APIs still work)
- Performance baselines (no regression from modularization)

**OUT OF SCOPE** ❌:
- RuleChecker refactoring (Phase 12)
- SqlContext changes (Phase 12)
- StatementVisitor introduction (Phase 12)
- InnerInterceptor implementation (Phase 13)
- Any architecture pattern changes (Phase 12)

### 1.4 Target Module Structure

```
sql-guard-jdbc-common/           # Shared abstractions module
├── ViolationStrategy.java       # Unified strategy enum (eliminate 3 duplicates)
├── JdbcInterceptorBase.java     # Abstract base class with template method
├── JdbcInterceptorConfig.java   # Configuration interface
└── SqlContextBuilder.java       # Context construction utility

sql-guard-jdbc-druid/            # Druid-specific module
├── DruidSqlSafetyFilter.java
├── DruidSqlAuditFilter.java
└── DruidSqlSafetyFilterConfiguration.java
└── pom.xml (druid:provided, jdbc-common:compile)

sql-guard-jdbc-hikari/           # HikariCP-specific module
├── HikariSqlSafetyProxyFactory.java
├── HikariSqlAuditProxyFactory.java
└── HikariSqlSafetyConfiguration.java
└── pom.xml (hikari:provided, jdbc-common:compile)

sql-guard-jdbc-p6spy/            # P6Spy universal fallback module
├── P6SpySqlSafetyListener.java
├── P6SpySqlAuditListener.java
└── P6SpySqlSafetyModule.java
└── pom.xml (p6spy:provided, jdbc-common:compile)
```

### 1.5 Test Framework Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| JUnit 5 | 5.10.x | Test framework |
| Mockito | 5.x | Mocking framework |
| AssertJ | 3.24.x | Fluent assertions |
| H2 Database | 2.2.x | In-memory database for integration tests |
| JMH | 1.37 | Performance benchmarks |
| Maven Dependency Plugin | 3.6.x | POM dependency analysis |

---

## 2. Test Fixture Design

### 2.1 AbstractJdbcModuleTest Specification

**Purpose**: Reusable test fixture base class providing common infrastructure for all JDBC module tests.

**Package**: `com.footstone.sqlguard.test.jdbc`

**Location**: `sql-guard-jdbc-common/src/test/java/com/footstone/sqlguard/test/jdbc/AbstractJdbcModuleTest.java`

#### 2.1.1 Interface Definition

```java
/**
 * Abstract base class for JDBC module testing.
 * 
 * Provides:
 * - H2 in-memory database setup/teardown
 * - Mock ConnectionPool creation for Druid, HikariCP, P6Spy
 * - SQL execution verification utilities
 * - Module isolation helpers (ClassLoader, POM parsing)
 * - Performance measurement utilities
 * 
 * @since 2.0.0
 */
public abstract class AbstractJdbcModuleTest {

    // ==================== Lifecycle Methods ====================
    
    /**
     * Sets up H2 in-memory database with unique instance per test.
     * Called automatically by JUnit 5 @BeforeEach.
     */
    protected void setupH2Database();
    
    /**
     * Tears down H2 database and cleans up resources.
     * Called automatically by JUnit 5 @AfterEach.
     */
    protected void teardownH2Database();
    
    /**
     * Creates test schema with standard tables (users, orders, products).
     * @param connection active H2 connection
     */
    protected void createTestSchema(Connection connection);
    
    /**
     * Inserts sample test data into test tables.
     * @param connection active H2 connection
     */
    protected void insertTestData(Connection connection);

    // ==================== Connection Pool Mock Creation ====================
    
    /**
     * Creates a mock DruidDataSource for testing without actual Druid dependency.
     * Uses reflection to avoid compile-time dependency.
     * @return mock DataSource or null if Druid not on classpath
     */
    protected DataSource createMockDruidDataSource();
    
    /**
     * Creates a mock HikariDataSource for testing without actual HikariCP dependency.
     * Uses reflection to avoid compile-time dependency.
     * @return mock DataSource or null if HikariCP not on classpath
     */
    protected DataSource createMockHikariDataSource();
    
    /**
     * Creates a mock P6Spy wrapped DataSource for testing.
     * Uses reflection to avoid compile-time dependency.
     * @return mock DataSource or null if P6Spy not on classpath
     */
    protected DataSource createMockP6SpyDataSource();
    
    /**
     * Creates a simple H2 DataSource without any pool wrapping.
     * For baseline comparison tests.
     * @return H2 DataSource
     */
    protected DataSource createRawH2DataSource();

    // ==================== SQL Execution Verification ====================
    
    /**
     * Executes SQL and returns result for verification.
     * @param dataSource target DataSource
     * @param sql SQL to execute
     * @return SqlExecutionResult with timing, row count, exceptions
     */
    protected SqlExecutionResult executeSql(DataSource dataSource, String sql);
    
    /**
     * Executes SQL with parameter binding.
     * @param dataSource target DataSource
     * @param sql SQL with ? placeholders
     * @param params parameter values
     * @return SqlExecutionResult
     */
    protected SqlExecutionResult executeSql(DataSource dataSource, String sql, Object... params);
    
    /**
     * Verifies SQL was intercepted by safety filter.
     * @param expectedInterceptionCount expected number of interceptions
     */
    protected void verifyInterceptionCount(int expectedInterceptionCount);
    
    /**
     * Asserts that SQL execution was blocked due to violation.
     * @param sql the SQL that should be blocked
     * @param expectedViolationType expected violation type
     */
    protected void assertSqlBlocked(String sql, String expectedViolationType);
    
    /**
     * Asserts that SQL execution was allowed to proceed.
     * @param sql the SQL that should be allowed
     */
    protected void assertSqlAllowed(String sql);

    // ==================== Module Isolation Helpers ====================
    
    /**
     * Verifies that a class is NOT loaded in current ClassLoader.
     * Used to confirm dependency isolation.
     * @param className fully qualified class name
     * @return true if class is NOT present (isolation confirmed)
     */
    protected boolean verifyClassNotLoaded(String className);
    
    /**
     * Verifies that a class IS loaded in current ClassLoader.
     * Used to confirm expected dependencies are present.
     * @param className fully qualified class name
     * @return true if class is present
     */
    protected boolean verifyClassLoaded(String className);
    
    /**
     * Parses POM file and returns declared dependencies.
     * @param pomPath path to pom.xml file
     * @return list of dependency coordinates (groupId:artifactId:scope)
     */
    protected List<String> parsePomDependencies(String pomPath);
    
    /**
     * Verifies that a dependency is declared with specific scope.
     * @param pomPath path to pom.xml
     * @param groupId expected groupId
     * @param artifactId expected artifactId
     * @param expectedScope expected scope (compile, provided, test)
     * @return true if dependency matches
     */
    protected boolean verifyDependencyScope(String pomPath, String groupId, 
                                            String artifactId, String expectedScope);
    
    /**
     * Analyzes transitive dependencies of a module.
     * Uses Maven dependency:tree analysis.
     * @param modulePath path to module root
     * @return set of transitive dependency coordinates
     */
    protected Set<String> analyzeTransitiveDependencies(String modulePath);
    
    /**
     * Verifies Maven module compiles independently.
     * Executes mvn compile in isolated environment.
     * @param modulePath path to module root
     * @return CompilationResult with success/failure and output
     */
    protected CompilationResult verifyIndependentCompilation(String modulePath);

    // ==================== Performance Measurement ====================
    
    /**
     * Measures module class loading time.
     * @param className class to load
     * @return loading time in milliseconds
     */
    protected long measureClassLoadingTime(String className);
    
    /**
     * Measures SQL execution latency with interceptor.
     * @param dataSource target DataSource
     * @param sql SQL to execute
     * @param iterations number of iterations
     * @return PerformanceMetrics with avg, p50, p99 latency
     */
    protected PerformanceMetrics measureSqlLatency(DataSource dataSource, 
                                                    String sql, int iterations);
    
    /**
     * Measures memory footprint of module.
     * @param moduleName module to measure
     * @return memory usage in bytes
     */
    protected long measureMemoryFootprint(String moduleName);
    
    /**
     * Runs JMH benchmark for specified class.
     * @param benchmarkClass JMH benchmark class
     * @return BenchmarkResult with throughput and latency
     */
    protected BenchmarkResult runJmhBenchmark(Class<?> benchmarkClass);

    // ==================== Helper Data Classes ====================
    
    /**
     * Result of SQL execution for verification.
     */
    public static class SqlExecutionResult {
        public boolean success;
        public long executionTimeMs;
        public int affectedRows;
        public SQLException exception;
        public List<Map<String, Object>> resultRows;
    }
    
    /**
     * Result of module compilation verification.
     */
    public static class CompilationResult {
        public boolean success;
        public String output;
        public List<String> errors;
        public long compilationTimeMs;
    }
    
    /**
     * Performance metrics from benchmark.
     */
    public static class PerformanceMetrics {
        public double avgLatencyMs;
        public double p50LatencyMs;
        public double p99LatencyMs;
        public double throughputOpsPerSec;
        public long memoryUsageBytes;
    }
    
    /**
     * JMH benchmark result.
     */
    public static class BenchmarkResult {
        public double throughput;
        public double avgLatency;
        public double p99Latency;
        public String benchmarkName;
    }
}
```

#### 2.1.2 Usage Examples

**Example 1: Module Isolation Test**
```java
class DruidModuleIsolationTest extends AbstractJdbcModuleTest {
    
    @Test
    void testDruidModule_compilesWithoutHikariP6Spy() {
        // Given: Druid module POM
        String pomPath = "sql-guard-jdbc-druid/pom.xml";
        
        // When: Analyze dependencies
        List<String> deps = parsePomDependencies(pomPath);
        
        // Then: No HikariCP or P6Spy dependencies
        assertThat(deps).noneMatch(d -> d.contains("HikariCP"));
        assertThat(deps).noneMatch(d -> d.contains("p6spy"));
        
        // And: Compiles independently
        CompilationResult result = verifyIndependentCompilation("sql-guard-jdbc-druid");
        assertThat(result.success).isTrue();
    }
}
```

**Example 2: Performance Baseline Test**
```java
class PerformanceBaselineTest extends AbstractJdbcModuleTest {
    
    @Test
    void testModuleLoading_druidModule_under10ms() {
        // When: Measure class loading time
        long loadTime = measureClassLoadingTime(
            "com.footstone.sqlguard.interceptor.druid.DruidSqlSafetyFilter");
        
        // Then: Loading time under 10ms
        assertThat(loadTime).isLessThan(10L);
    }
}
```

**Example 3: Backward Compatibility Test**
```java
class BackwardCompatibilityTest extends AbstractJdbcModuleTest {
    
    @Test
    void testViolationStrategy_oldDruidEnum_stillWorks() {
        // Given: Old import path
        // import com.footstone.sqlguard.interceptor.druid.ViolationStrategy;
        
        // When: Use old enum (should still compile and work)
        ViolationStrategy strategy = ViolationStrategy.BLOCK;
        
        // Then: Enum value is correct
        assertThat(strategy.name()).isEqualTo("BLOCK");
    }
}
```

### 2.2 Test Data Specifications

#### 2.2.1 H2 Test Schema

```sql
-- Standard test schema for all JDBC module tests
CREATE TABLE users (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    id INT PRIMARY KEY,
    user_id INT NOT NULL,
    amount DECIMAL(10,2),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE products (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10,2),
    category VARCHAR(50),
    stock INT DEFAULT 0
);
```

#### 2.2.2 Sample Test Data

```sql
-- Users (5 records)
INSERT INTO users VALUES (1, 'Alice', 'alice@example.com', 'ACTIVE', CURRENT_TIMESTAMP);
INSERT INTO users VALUES (2, 'Bob', 'bob@example.com', 'ACTIVE', CURRENT_TIMESTAMP);
INSERT INTO users VALUES (3, 'Charlie', 'charlie@example.com', 'INACTIVE', CURRENT_TIMESTAMP);
INSERT INTO users VALUES (4, 'Diana', 'diana@example.com', 'ACTIVE', CURRENT_TIMESTAMP);
INSERT INTO users VALUES (5, 'Eve', 'eve@example.com', 'SUSPENDED', CURRENT_TIMESTAMP);

-- Orders (10 records)
INSERT INTO orders VALUES (1, 1, 100.00, 'COMPLETED', CURRENT_TIMESTAMP);
INSERT INTO orders VALUES (2, 1, 250.50, 'PENDING', CURRENT_TIMESTAMP);
INSERT INTO orders VALUES (3, 2, 75.00, 'COMPLETED', CURRENT_TIMESTAMP);
-- ... additional records

-- Products (5 records)
INSERT INTO products VALUES (1, 'Widget', 29.99, 'Electronics', 100);
INSERT INTO products VALUES (2, 'Gadget', 49.99, 'Electronics', 50);
-- ... additional records
```

---

## 3. Module Isolation Tests (15 tests)

### 3.1 Common Module Tests

#### Test 3.1.1: testCommonModule_compilesWithoutPoolDependencies()

| Field | Value |
|-------|-------|
| **Objective** | Verify `sql-guard-jdbc-common` module compiles successfully without any connection pool dependencies (Druid, HikariCP, P6Spy) |
| **Methodology** | 1. Parse `sql-guard-jdbc-common/pom.xml` for dependencies<br>2. Execute `mvn compile -pl sql-guard-jdbc-common` in isolated environment<br>3. Verify no pool-related classes in compile classpath |
| **Success Criteria** | - POM contains NO dependencies on `com.alibaba:druid`, `com.zaxxer:HikariCP`, `p6spy:p6spy`<br>- Maven compile succeeds with exit code 0<br>- No pool-specific classes referenced in compiled bytecode |
| **Implementation Class** | `CommonModuleIsolationTest` |
| **Test Data** | N/A (POM analysis only) |
| **Acceptance Criteria Mapping** | AC: "All modules compile independently" |

#### Test 3.1.2: testCommonModule_onlyDependsOnCoreAndAuditApi()

| Field | Value |
|-------|-------|
| **Objective** | Verify `sql-guard-jdbc-common` only declares dependencies on `sql-guard-core` and `sql-guard-audit-api` |
| **Methodology** | 1. Parse `sql-guard-jdbc-common/pom.xml`<br>2. Extract all non-test scope dependencies<br>3. Whitelist check against allowed dependencies |
| **Success Criteria** | - Dependencies limited to: `sql-guard-core`, `sql-guard-audit-api`, `slf4j-api`<br>- No other runtime/compile scope dependencies<br>- Transitive dependency tree contains no pool classes |
| **Implementation Class** | `CommonModuleIsolationTest` |
| **Test Data** | N/A (POM analysis only) |
| **Acceptance Criteria Mapping** | AC: "Minimal dependency footprint" |

### 3.2 Druid Module Tests

#### Test 3.2.1: testDruidModule_compilesWithoutHikariP6Spy()

| Field | Value |
|-------|-------|
| **Objective** | Verify `sql-guard-jdbc-druid` compiles without HikariCP or P6Spy on classpath |
| **Methodology** | 1. Create Maven profile excluding HikariCP and P6Spy<br>2. Execute `mvn compile -pl sql-guard-jdbc-druid -P exclude-other-pools`<br>3. Verify successful compilation |
| **Success Criteria** | - Compilation succeeds with only Druid on classpath<br>- No `ClassNotFoundException` for HikariCP/P6Spy classes<br>- All Druid-specific tests pass |
| **Implementation Class** | `DruidModuleIsolationTest` |
| **Test Data** | N/A (compilation test) |
| **Acceptance Criteria Mapping** | AC: "Druid module independent" |

#### Test 3.2.2: testDruidModule_druidDependencyIsProvided()

| Field | Value |
|-------|-------|
| **Objective** | Verify Druid dependency is declared with `provided` scope |
| **Methodology** | 1. Parse `sql-guard-jdbc-druid/pom.xml`<br>2. Find `com.alibaba:druid` dependency<br>3. Assert scope is `provided` |
| **Success Criteria** | - `<scope>provided</scope>` for druid dependency<br>- Users must provide their own Druid version<br>- No Druid JAR bundled in module |
| **Implementation Class** | `DruidModuleIsolationTest` |
| **Test Data** | N/A (POM analysis) |
| **Acceptance Criteria Mapping** | AC: "Provided scope strategy" |

### 3.3 HikariCP Module Tests

#### Test 3.3.1: testHikariModule_compilesWithoutDruidP6Spy()

| Field | Value |
|-------|-------|
| **Objective** | Verify `sql-guard-jdbc-hikari` compiles without Druid or P6Spy on classpath |
| **Methodology** | 1. Create Maven profile excluding Druid and P6Spy<br>2. Execute `mvn compile -pl sql-guard-jdbc-hikari -P exclude-other-pools`<br>3. Verify successful compilation |
| **Success Criteria** | - Compilation succeeds with only HikariCP on classpath<br>- No `ClassNotFoundException` for Druid/P6Spy classes<br>- All HikariCP-specific tests pass |
| **Implementation Class** | `HikariModuleIsolationTest` |
| **Test Data** | N/A (compilation test) |
| **Acceptance Criteria Mapping** | AC: "HikariCP module independent" |

#### Test 3.3.2: testHikariModule_hikariDependencyIsProvided()

| Field | Value |
|-------|-------|
| **Objective** | Verify HikariCP dependency is declared with `provided` scope |
| **Methodology** | 1. Parse `sql-guard-jdbc-hikari/pom.xml`<br>2. Find `com.zaxxer:HikariCP` dependency<br>3. Assert scope is `provided` |
| **Success Criteria** | - `<scope>provided</scope>` for HikariCP dependency<br>- Users must provide their own HikariCP version<br>- No HikariCP JAR bundled in module |
| **Implementation Class** | `HikariModuleIsolationTest` |
| **Test Data** | N/A (POM analysis) |
| **Acceptance Criteria Mapping** | AC: "Provided scope strategy" |

### 3.4 P6Spy Module Tests

#### Test 3.4.1: testP6SpyModule_compilesWithoutDruidHikari()

| Field | Value |
|-------|-------|
| **Objective** | Verify `sql-guard-jdbc-p6spy` compiles without Druid or HikariCP on classpath |
| **Methodology** | 1. Create Maven profile excluding Druid and HikariCP<br>2. Execute `mvn compile -pl sql-guard-jdbc-p6spy -P exclude-other-pools`<br>3. Verify successful compilation |
| **Success Criteria** | - Compilation succeeds with only P6Spy on classpath<br>- No `ClassNotFoundException` for Druid/HikariCP classes<br>- All P6Spy-specific tests pass |
| **Implementation Class** | `P6SpyModuleIsolationTest` |
| **Test Data** | N/A (compilation test) |
| **Acceptance Criteria Mapping** | AC: "P6Spy module independent" |

#### Test 3.4.2: testP6SpyModule_p6spyDependencyIsProvided()

| Field | Value |
|-------|-------|
| **Objective** | Verify P6Spy dependency is declared with `provided` scope |
| **Methodology** | 1. Parse `sql-guard-jdbc-p6spy/pom.xml`<br>2. Find `p6spy:p6spy` dependency<br>3. Assert scope is `provided` |
| **Success Criteria** | - `<scope>provided</scope>` for P6Spy dependency<br>- Users must provide their own P6Spy version<br>- No P6Spy JAR bundled in module |
| **Implementation Class** | `P6SpyModuleIsolationTest` |
| **Test Data** | N/A (POM analysis) |
| **Acceptance Criteria Mapping** | AC: "Provided scope strategy" |

### 3.5 User Project Simulation Tests

#### Test 3.5.1: testUserProject_onlyDruidDependency_noTransitivePollution()

| Field | Value |
|-------|-------|
| **Objective** | Simulate user project that only includes Druid module, verify no transitive HikariCP/P6Spy pollution |
| **Methodology** | 1. Create test Maven project with only `sql-guard-jdbc-druid` dependency<br>2. Run `mvn dependency:tree`<br>3. Analyze transitive dependencies |
| **Success Criteria** | - Dependency tree contains NO `com.zaxxer:HikariCP`<br>- Dependency tree contains NO `p6spy:p6spy`<br>- Only expected dependencies: sql-guard-core, sql-guard-audit-api, druid |
| **Implementation Class** | `UserProjectSimulationTest` |
| **Test Data** | Synthetic test POM |
| **Acceptance Criteria Mapping** | AC: "No transitive dependency pollution" |

#### Test 3.5.2: testUserProject_onlyHikariDependency_noTransitivePollution()

| Field | Value |
|-------|-------|
| **Objective** | Simulate user project that only includes HikariCP module, verify no transitive Druid/P6Spy pollution |
| **Methodology** | 1. Create test Maven project with only `sql-guard-jdbc-hikari` dependency<br>2. Run `mvn dependency:tree`<br>3. Analyze transitive dependencies |
| **Success Criteria** | - Dependency tree contains NO `com.alibaba:druid`<br>- Dependency tree contains NO `p6spy:p6spy`<br>- Only expected dependencies: sql-guard-core, sql-guard-audit-api, HikariCP |
| **Implementation Class** | `UserProjectSimulationTest` |
| **Test Data** | Synthetic test POM |
| **Acceptance Criteria Mapping** | AC: "No transitive dependency pollution" |

#### Test 3.5.3: testUserProject_onlyP6SpyDependency_noTransitivePollution()

| Field | Value |
|-------|-------|
| **Objective** | Simulate user project that only includes P6Spy module, verify no transitive Druid/HikariCP pollution |
| **Methodology** | 1. Create test Maven project with only `sql-guard-jdbc-p6spy` dependency<br>2. Run `mvn dependency:tree`<br>3. Analyze transitive dependencies |
| **Success Criteria** | - Dependency tree contains NO `com.alibaba:druid`<br>- Dependency tree contains NO `com.zaxxer:HikariCP`<br>- Only expected dependencies: sql-guard-core, sql-guard-audit-api, p6spy |
| **Implementation Class** | `UserProjectSimulationTest` |
| **Test Data** | Synthetic test POM |
| **Acceptance Criteria Mapping** | AC: "No transitive dependency pollution" |

### 3.6 Maven Enforcer & ClassLoader Tests

#### Test 3.6.1: testMavenEnforcer_rejectsWrongDependencies()

| Field | Value |
|-------|-------|
| **Objective** | Verify Maven Enforcer plugin rejects invalid dependency combinations |
| **Methodology** | 1. Configure Maven Enforcer with banned dependencies rule<br>2. Attempt to add conflicting dependencies<br>3. Verify build failure |
| **Success Criteria** | - Enforcer fails build when Druid module declares HikariCP dependency<br>- Enforcer fails build when HikariCP module declares Druid dependency<br>- Clear error message identifying violation |
| **Implementation Class** | `MavenEnforcerTest` |
| **Test Data** | Test POM with intentional violations |
| **Acceptance Criteria Mapping** | AC: "Maven Enforcer validates dependency constraints" |

#### Test 3.6.2: testClassLoader_poolClassesNotLoaded_whenModuleNotUsed()

| Field | Value |
|-------|-------|
| **Objective** | Verify pool classes are NOT loaded into ClassLoader when their module is not used |
| **Methodology** | 1. Load only Druid module<br>2. Attempt to find HikariCP/P6Spy classes via ClassLoader<br>3. Assert ClassNotFoundException |
| **Success Criteria** | - `Class.forName("com.zaxxer.hikari.HikariDataSource")` throws `ClassNotFoundException`<br>- `Class.forName("com.p6spy.engine.spy.P6DataSource")` throws `ClassNotFoundException`<br>- Druid classes load successfully |
| **Implementation Class** | `ClassLoaderIsolationTest` |
| **Test Data** | N/A (ClassLoader inspection) |
| **Acceptance Criteria Mapping** | AC: "ClassLoader isolation verified" |

### 3.7 JAR Packaging Tests

#### Test 3.7.1: testIndependentJar_druidModulePackagesCorrectly()

| Field | Value |
|-------|-------|
| **Objective** | Verify Druid module JAR contains only Druid-specific classes |
| **Methodology** | 1. Build `sql-guard-jdbc-druid` module<br>2. Inspect JAR contents with `jar tf`<br>3. Verify class file list |
| **Success Criteria** | - JAR contains `DruidSqlSafetyFilter.class`<br>- JAR does NOT contain `HikariSqlSafetyProxyFactory.class`<br>- JAR does NOT contain `P6SpySqlSafetyListener.class`<br>- JAR size < 100KB |
| **Implementation Class** | `JarPackagingTest` |
| **Test Data** | N/A (JAR analysis) |
| **Acceptance Criteria Mapping** | AC: "Correct JAR packaging" |

#### Test 3.7.2: testIndependentJar_hikariModulePackagesCorrectly()

| Field | Value |
|-------|-------|
| **Objective** | Verify HikariCP module JAR contains only HikariCP-specific classes |
| **Methodology** | 1. Build `sql-guard-jdbc-hikari` module<br>2. Inspect JAR contents with `jar tf`<br>3. Verify class file list |
| **Success Criteria** | - JAR contains `HikariSqlSafetyProxyFactory.class`<br>- JAR does NOT contain `DruidSqlSafetyFilter.class`<br>- JAR does NOT contain `P6SpySqlSafetyListener.class`<br>- JAR size < 100KB |
| **Implementation Class** | `JarPackagingTest` |
| **Test Data** | N/A (JAR analysis) |
| **Acceptance Criteria Mapping** | AC: "Correct JAR packaging" |

---

## 4. Backward Compatibility Tests (12 tests)

### 4.1 ViolationStrategy Compatibility Tests

#### Test 4.1.1: testViolationStrategy_oldDruidEnum_stillWorks()

| Field | Value |
|-------|-------|
| **Objective** | Verify old Druid ViolationStrategy import path still compiles and works |
| **Methodology** | 1. Write test code using old import: `import com.footstone.sqlguard.interceptor.druid.ViolationStrategy;`<br>2. Compile and execute<br>3. Verify enum values work correctly |
| **Success Criteria** | - Old import path compiles (via deprecated forwarding class)<br>- `ViolationStrategy.BLOCK`, `WARN`, `LOG` all accessible<br>- Deprecation warning generated at compile time |
| **Migration Path** | Old enum class marked `@Deprecated`, internally delegates to `com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy` |
| **Implementation Class** | `ViolationStrategyCompatibilityTest` |
| **Test Data** | Legacy code samples |
| **Acceptance Criteria Mapping** | AC: "100% backward compatibility" |

#### Test 4.1.2: testViolationStrategy_oldHikariEnum_stillWorks()

| Field | Value |
|-------|-------|
| **Objective** | Verify old HikariCP ViolationStrategy import path still compiles and works |
| **Methodology** | 1. Write test code using old import: `import com.footstone.sqlguard.interceptor.hikari.ViolationStrategy;`<br>2. Compile and execute<br>3. Verify enum values work correctly |
| **Success Criteria** | - Old import path compiles (via deprecated forwarding class)<br>- `ViolationStrategy.BLOCK`, `WARN`, `LOG` all accessible<br>- Deprecation warning generated at compile time |
| **Migration Path** | Old enum class marked `@Deprecated`, internally delegates to unified enum |
| **Implementation Class** | `ViolationStrategyCompatibilityTest` |
| **Test Data** | Legacy code samples |
| **Acceptance Criteria Mapping** | AC: "100% backward compatibility" |

#### Test 4.1.3: testViolationStrategy_oldP6SpyEnum_stillWorks()

| Field | Value |
|-------|-------|
| **Objective** | Verify old P6Spy ViolationStrategy import path still compiles and works |
| **Methodology** | 1. Write test code using old import: `import com.footstone.sqlguard.interceptor.p6spy.ViolationStrategy;`<br>2. Compile and execute<br>3. Verify enum values work correctly |
| **Success Criteria** | - Old import path compiles (via deprecated forwarding class)<br>- `ViolationStrategy.BLOCK`, `WARN`, `LOG` all accessible<br>- Deprecation warning generated at compile time |
| **Migration Path** | Old enum class marked `@Deprecated`, internally delegates to unified enum |
| **Implementation Class** | `ViolationStrategyCompatibilityTest` |
| **Test Data** | Legacy code samples |
| **Acceptance Criteria Mapping** | AC: "100% backward compatibility" |

### 4.2 Filter/Interceptor API Compatibility Tests

#### Test 4.2.1: testDruidFilter_existingCode_noChangesNeeded()

| Field | Value |
|-------|-------|
| **Objective** | Verify existing DruidSqlSafetyFilter usage code works without modification |
| **Methodology** | 1. Copy existing integration test code verbatim<br>2. Compile against new module structure<br>3. Execute and verify behavior identical |
| **Success Criteria** | - Existing test `DruidIntegrationTest` passes 100%<br>- No source code changes required<br>- Same public API methods available |
| **Test Data** | Existing test code from `DruidIntegrationTest.java` |
| **Implementation Class** | `DruidApiCompatibilityTest` |
| **Acceptance Criteria Mapping** | AC: "Zero breaking changes" |

#### Test 4.2.2: testHikariProxy_existingCode_noChangesNeeded()

| Field | Value |
|-------|-------|
| **Objective** | Verify existing HikariSqlSafetyProxyFactory usage code works without modification |
| **Methodology** | 1. Copy existing integration test code verbatim<br>2. Compile against new module structure<br>3. Execute and verify behavior identical |
| **Success Criteria** | - Existing test `HikariIntegrationTest` passes 100%<br>- No source code changes required<br>- Same public API methods available |
| **Test Data** | Existing test code from `HikariIntegrationTest.java` |
| **Implementation Class** | `HikariApiCompatibilityTest` |
| **Acceptance Criteria Mapping** | AC: "Zero breaking changes" |

#### Test 4.2.3: testP6SpyListener_existingCode_noChangesNeeded()

| Field | Value |
|-------|-------|
| **Objective** | Verify existing P6SpySqlSafetyListener usage code works without modification |
| **Methodology** | 1. Copy existing integration test code verbatim<br>2. Compile against new module structure<br>3. Execute and verify behavior identical |
| **Success Criteria** | - Existing test `P6SpyIntegrationTest` passes 100%<br>- No source code changes required<br>- Same public API methods available |
| **Test Data** | Existing test code from `P6SpyIntegrationTest.java` |
| **Implementation Class** | `P6SpyApiCompatibilityTest` |
| **Acceptance Criteria Mapping** | AC: "Zero breaking changes" |

### 4.3 Configuration Compatibility Tests

#### Test 4.3.1: testConfiguration_oldYaml_parsesCorrectly()

| Field | Value |
|-------|-------|
| **Objective** | Verify old YAML configuration files parse correctly with new module structure |
| **Methodology** | 1. Load sample old YAML config<br>2. Parse with new configuration classes<br>3. Verify all properties mapped correctly |
| **Success Criteria** | - All old property names still recognized<br>- Violation strategy correctly parsed<br>- No parsing exceptions |
| **Test Data** | Sample YAML from `examples/sql-guard-demo/src/main/resources/application.yml` |
| **Implementation Class** | `ConfigurationCompatibilityTest` |
| **Acceptance Criteria Mapping** | AC: "Configuration migration" |

```yaml
# Sample old YAML config to test
sql-guard:
  druid:
    enabled: true
    violation-strategy: WARN
  hikari:
    enabled: false
```

#### Test 4.3.2: testConfiguration_oldProperties_parsesCorrectly()

| Field | Value |
|-------|-------|
| **Objective** | Verify old properties file configuration parses correctly |
| **Methodology** | 1. Load sample old .properties config<br>2. Parse with new configuration classes<br>3. Verify all properties mapped correctly |
| **Success Criteria** | - All old property names still recognized<br>- Violation strategy correctly parsed<br>- No parsing exceptions |
| **Test Data** | Sample properties file |
| **Implementation Class** | `ConfigurationCompatibilityTest` |
| **Acceptance Criteria Mapping** | AC: "Configuration migration" |

```properties
# Sample old properties config to test
sql-guard.druid.enabled=true
sql-guard.druid.violation-strategy=BLOCK
```

### 4.4 Deprecated API Tests

#### Test 4.4.1: testDeprecatedApi_compiles_withWarning()

| Field | Value |
|-------|-------|
| **Objective** | Verify deprecated APIs compile but generate warnings |
| **Methodology** | 1. Compile code using deprecated imports<br>2. Capture compiler output<br>3. Verify deprecation warnings present |
| **Success Criteria** | - Compilation succeeds<br>- Deprecation warning for each old import<br>- Warning message suggests migration path |
| **Implementation Class** | `DeprecatedApiTest` |
| **Test Data** | Legacy code samples |
| **Acceptance Criteria Mapping** | AC: "Deprecated APIs still functional" |

#### Test 4.4.2: testDeprecatedApi_behavior_unchanged()

| Field | Value |
|-------|-------|
| **Objective** | Verify deprecated APIs behave identically to new APIs |
| **Methodology** | 1. Execute same operation via old and new API<br>2. Compare results<br>3. Verify identical behavior |
| **Success Criteria** | - Old `ViolationStrategy.BLOCK` equals new `ViolationStrategy.BLOCK`<br>- Filter configuration via old path works<br>- Runtime behavior identical |
| **Implementation Class** | `DeprecatedApiTest` |
| **Test Data** | Comparison test cases |
| **Acceptance Criteria Mapping** | AC: "Behavior preservation" |

### 4.5 Test Suite Compatibility

#### Test 4.5.1: testAllExistingTests_pass100Percent()

| Field | Value |
|-------|-------|
| **Objective** | Verify ALL existing JDBC module tests pass without modification |
| **Methodology** | 1. Run full test suite for sql-guard-jdbc<br>2. Collect results<br>3. Assert 100% pass rate |
| **Success Criteria** | - All 29 existing test files pass<br>- No test modifications required<br>- Test execution time within 10% of baseline |
| **Test Data** | All existing tests in `sql-guard-jdbc/src/test/java/` |
| **Implementation Class** | `ExistingTestSuiteRunner` |
| **Acceptance Criteria Mapping** | AC: "All existing tests pass" |

**Existing Test Files (29 total)**:
- Druid (10): `DruidSqlSafetyFilterTest`, `DruidIntegrationTest`, `DruidSqlAuditFilterTest`, etc.
- HikariCP (10): `HikariSqlSafetyProxyFactoryTest`, `HikariIntegrationTest`, etc.
- P6Spy (9): `P6SpySqlSafetyListenerTest`, `P6SpyIntegrationTest`, etc.

#### Test 4.5.2: testMigrationGuide_documentsChanges()

| Field | Value |
|-------|-------|
| **Objective** | Verify migration guide documentation exists and is accurate |
| **Methodology** | 1. Read migration guide document<br>2. Follow migration steps<br>3. Verify steps work correctly |
| **Success Criteria** | - Migration guide exists at `docs/migration/jdbc-module-separation.md`<br>- All steps executable<br>- Before/after code samples compile |
| **Implementation Class** | `MigrationGuideVerificationTest` |
| **Test Data** | Migration guide document |
| **Acceptance Criteria Mapping** | AC: "Clear migration path" |

---

## 5. Performance Baseline Tests (13 tests)

### 5.1 Module Loading Performance Tests

#### Test 5.1.1: testModuleLoading_commonModule_under10ms()

| Field | Value |
|-------|-------|
| **Objective** | Verify `sql-guard-jdbc-common` module loads within 10ms |
| **Methodology** | 1. Cold start JVM<br>2. Measure time to load `ViolationStrategy` class<br>3. Repeat 10 times, calculate average |
| **Success Criteria** | - Average loading time < 10ms<br>- P99 loading time < 20ms<br>- No class loading errors |
| **Performance Threshold** | < 10ms average |
| **JMH Configuration** | `@Warmup(iterations = 3)`, `@Measurement(iterations = 10)` |
| **Implementation Class** | `ModuleLoadingPerformanceTest` |
| **Acceptance Criteria Mapping** | AC: "Module loading < 10ms overhead" |

#### Test 5.1.2: testModuleLoading_druidModule_under10ms()

| Field | Value |
|-------|-------|
| **Objective** | Verify `sql-guard-jdbc-druid` module loads within 10ms |
| **Methodology** | 1. Cold start JVM with Druid on classpath<br>2. Measure time to load `DruidSqlSafetyFilter` class<br>3. Repeat 10 times, calculate average |
| **Success Criteria** | - Average loading time < 10ms<br>- P99 loading time < 20ms<br>- No class loading errors |
| **Performance Threshold** | < 10ms average |
| **JMH Configuration** | `@Warmup(iterations = 3)`, `@Measurement(iterations = 10)` |
| **Implementation Class** | `ModuleLoadingPerformanceTest` |
| **Acceptance Criteria Mapping** | AC: "Module loading < 10ms overhead" |

#### Test 5.1.3: testModuleLoading_hikariModule_under10ms()

| Field | Value |
|-------|-------|
| **Objective** | Verify `sql-guard-jdbc-hikari` module loads within 10ms |
| **Methodology** | 1. Cold start JVM with HikariCP on classpath<br>2. Measure time to load `HikariSqlSafetyProxyFactory` class<br>3. Repeat 10 times, calculate average |
| **Success Criteria** | - Average loading time < 10ms<br>- P99 loading time < 20ms<br>- No class loading errors |
| **Performance Threshold** | < 10ms average |
| **JMH Configuration** | `@Warmup(iterations = 3)`, `@Measurement(iterations = 10)` |
| **Implementation Class** | `ModuleLoadingPerformanceTest` |
| **Acceptance Criteria Mapping** | AC: "Module loading < 10ms overhead" |

#### Test 5.1.4: testModuleLoading_p6spyModule_under10ms()

| Field | Value |
|-------|-------|
| **Objective** | Verify `sql-guard-jdbc-p6spy` module loads within 10ms |
| **Methodology** | 1. Cold start JVM with P6Spy on classpath<br>2. Measure time to load `P6SpySqlSafetyListener` class<br>3. Repeat 10 times, calculate average |
| **Success Criteria** | - Average loading time < 10ms<br>- P99 loading time < 20ms<br>- No class loading errors |
| **Performance Threshold** | < 10ms average |
| **JMH Configuration** | `@Warmup(iterations = 3)`, `@Measurement(iterations = 10)` |
| **Implementation Class** | `ModuleLoadingPerformanceTest` |
| **Acceptance Criteria Mapping** | AC: "Module loading < 10ms overhead" |

### 5.2 Runtime Performance Tests

#### Test 5.2.1: testRuntimePerformance_druid_noRegression()

| Field | Value |
|-------|-------|
| **Objective** | Verify Druid module runtime performance has no regression from modularization |
| **Methodology** | 1. Establish baseline with current monolithic module<br>2. Run same benchmark with new modular structure<br>3. Compare throughput and latency |
| **Success Criteria** | - Throughput >= 95% of baseline<br>- P99 latency <= 110% of baseline<br>- No unexpected GC pauses |
| **Baseline Establishment** | Run `DruidFilterPerformanceTest` before modularization, record metrics |
| **Performance Threshold** | < 5% throughput degradation, < 10% latency increase |
| **JMH Configuration** | `@BenchmarkMode(Mode.Throughput)`, `@Warmup(iterations = 5)`, `@Measurement(iterations = 10)` |
| **Implementation Class** | `DruidRuntimePerformanceTest` |
| **Acceptance Criteria Mapping** | AC: "No runtime regression" |

#### Test 5.2.2: testRuntimePerformance_hikari_noRegression()

| Field | Value |
|-------|-------|
| **Objective** | Verify HikariCP module runtime performance has no regression from modularization |
| **Methodology** | 1. Establish baseline with current monolithic module<br>2. Run same benchmark with new modular structure<br>3. Compare throughput and latency |
| **Success Criteria** | - Throughput >= 95% of baseline<br>- P99 latency <= 110% of baseline<br>- No unexpected GC pauses |
| **Baseline Establishment** | Run `HikariProxyPerformanceTest` before modularization, record metrics |
| **Performance Threshold** | < 5% throughput degradation, < 10% latency increase |
| **JMH Configuration** | `@BenchmarkMode(Mode.Throughput)`, `@Warmup(iterations = 5)`, `@Measurement(iterations = 10)` |
| **Implementation Class** | `HikariRuntimePerformanceTest` |
| **Acceptance Criteria Mapping** | AC: "No runtime regression" |

#### Test 5.2.3: testRuntimePerformance_p6spy_noRegression()

| Field | Value |
|-------|-------|
| **Objective** | Verify P6Spy module runtime performance has no regression from modularization |
| **Methodology** | 1. Establish baseline with current monolithic module<br>2. Run same benchmark with new modular structure<br>3. Compare throughput and latency |
| **Success Criteria** | - Throughput >= 95% of baseline<br>- P99 latency <= 110% of baseline<br>- No unexpected GC pauses |
| **Baseline Establishment** | Run `P6SpyPerformanceTest` before modularization, record metrics |
| **Performance Threshold** | < 5% throughput degradation, < 10% latency increase |
| **JMH Configuration** | `@BenchmarkMode(Mode.Throughput)`, `@Warmup(iterations = 5)`, `@Measurement(iterations = 10)` |
| **Implementation Class** | `P6SpyRuntimePerformanceTest` |
| **Acceptance Criteria Mapping** | AC: "No runtime regression" |

### 5.3 Memory Usage Tests

#### Test 5.3.1: testMemoryUsage_staticFootprint_noIncrease()

| Field | Value |
|-------|-------|
| **Objective** | Verify static memory footprint does not increase after modularization |
| **Methodology** | 1. Measure baseline heap usage with current module<br>2. Measure heap usage with new modular structure<br>3. Compare static footprint |
| **Success Criteria** | - Static heap usage <= baseline<br>- Metaspace usage <= baseline + 5%<br>- No memory leaks detected |
| **Baseline Establishment** | Measure heap after loading all pool modules, before any SQL execution |
| **Performance Threshold** | No increase in static footprint |
| **Implementation Class** | `MemoryFootprintTest` |
| **Acceptance Criteria Mapping** | AC: "No memory increase" |

#### Test 5.3.2: testMemoryUsage_runtime_noIncrease()

| Field | Value |
|-------|-------|
| **Objective** | Verify runtime memory usage does not increase after modularization |
| **Methodology** | 1. Execute 10,000 SQL validations with baseline module<br>2. Execute same with new modular structure<br>3. Compare peak heap usage |
| **Success Criteria** | - Peak heap usage <= baseline<br>- No memory leak over extended runs<br>- GC frequency not increased |
| **Baseline Establishment** | Run extended SQL execution, measure peak heap |
| **Performance Threshold** | No increase in runtime memory |
| **Implementation Class** | `MemoryFootprintTest` |
| **Acceptance Criteria Mapping** | AC: "No memory increase" |

### 5.4 Connection & Validation Performance Tests

#### Test 5.4.1: testConnectionAcquisition_speed_unchanged()

| Field | Value |
|-------|-------|
| **Objective** | Verify connection acquisition speed not impacted by modularization |
| **Methodology** | 1. Measure connection acquisition time with baseline<br>2. Measure with new modular structure<br>3. Compare average and P99 times |
| **Success Criteria** | - Average acquisition time <= baseline<br>- P99 acquisition time <= 110% of baseline<br>- No connection timeouts |
| **Baseline Establishment** | Acquire 1000 connections, measure timing distribution |
| **Performance Threshold** | < 10% increase in acquisition time |
| **JMH Configuration** | `@BenchmarkMode(Mode.AverageTime)`, `@Warmup(iterations = 5)`, `@Measurement(iterations = 10)` |
| **Implementation Class** | `ConnectionAcquisitionPerformanceTest` |
| **Acceptance Criteria Mapping** | AC: "No performance regression" |

#### Test 5.4.2: testSqlValidation_throughput_noRegression()

| Field | Value |
|-------|-------|
| **Objective** | Verify SQL validation throughput not regressed after modularization |
| **Methodology** | 1. Measure validation throughput with baseline<br>2. Measure with new modular structure<br>3. Compare ops/sec |
| **Success Criteria** | - Throughput >= 95% of baseline<br>- Stable throughput over time<br>- No degradation under load |
| **Baseline Establishment** | Run 100,000 validations, calculate ops/sec |
| **Performance Threshold** | < 5% throughput degradation |
| **JMH Configuration** | `@BenchmarkMode(Mode.Throughput)`, `@Warmup(iterations = 5)`, `@Measurement(iterations = 10)` |
| **Implementation Class** | `SqlValidationThroughputTest` |
| **Acceptance Criteria Mapping** | AC: "No performance regression" |

#### Test 5.4.3: testSqlValidation_latency_noRegression()

| Field | Value |
|-------|-------|
| **Objective** | Verify SQL validation latency not regressed after modularization |
| **Methodology** | 1. Measure validation latency with baseline<br>2. Measure with new modular structure<br>3. Compare P50, P99, P999 latencies |
| **Success Criteria** | - P50 latency <= baseline<br>- P99 latency <= 110% of baseline<br>- P999 latency <= 120% of baseline |
| **Baseline Establishment** | Run validations, record latency distribution |
| **Performance Threshold** | P99 < 110% baseline |
| **JMH Configuration** | `@BenchmarkMode(Mode.SampleTime)`, `@Warmup(iterations = 5)`, `@Measurement(iterations = 10)` |
| **Implementation Class** | `SqlValidationLatencyTest` |
| **Acceptance Criteria Mapping** | AC: "No latency regression" |

### 5.5 Concurrency Tests

#### Test 5.5.1: testConcurrentAccess_scalability_maintained()

| Field | Value |
|-------|-------|
| **Objective** | Verify concurrent access scalability maintained after modularization |
| **Methodology** | 1. Run concurrent validation with 1, 10, 50, 100 threads<br>2. Measure throughput scaling<br>3. Compare with baseline scaling curve |
| **Success Criteria** | - Linear scaling up to 10 threads<br>- No significant degradation at 50 threads<br>- No deadlocks or race conditions |
| **Baseline Establishment** | Run concurrent tests with baseline, record scaling curve |
| **Performance Threshold** | Scaling factor >= 90% of baseline at each thread count |
| **JMH Configuration** | `@Threads(10)`, `@BenchmarkMode(Mode.Throughput)` |
| **Implementation Class** | `ConcurrentAccessPerformanceTest` |
| **Acceptance Criteria Mapping** | AC: "Scalability maintained" |

---

## 6. Test Execution Strategy

### 6.1 Maven Profiles for Isolation Testing

```xml
<!-- Parent POM: sql-safety-guard-parent/pom.xml -->
<profiles>
    <!-- Test with only Druid on classpath -->
    <profile>
        <id>test-druid-only</id>
        <properties>
            <skipHikari>true</skipHikari>
            <skipP6Spy>true</skipP6Spy>
        </properties>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <classpathDependencyExcludes>
                            <classpathDependencyExclude>com.zaxxer:HikariCP</classpathDependencyExclude>
                            <classpathDependencyExclude>p6spy:p6spy</classpathDependencyExclude>
                        </classpathDependencyExcludes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>

    <!-- Test with only HikariCP on classpath -->
    <profile>
        <id>test-hikari-only</id>
        <properties>
            <skipDruid>true</skipDruid>
            <skipP6Spy>true</skipP6Spy>
        </properties>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <classpathDependencyExcludes>
                            <classpathDependencyExclude>com.alibaba:druid</classpathDependencyExclude>
                            <classpathDependencyExclude>p6spy:p6spy</classpathDependencyExclude>
                        </classpathDependencyExcludes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>

    <!-- Test with only P6Spy on classpath -->
    <profile>
        <id>test-p6spy-only</id>
        <properties>
            <skipDruid>true</skipDruid>
            <skipHikari>true</skipHikari>
        </properties>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <classpathDependencyExcludes>
                            <classpathDependencyExclude>com.alibaba:druid</classpathDependencyExclude>
                            <classpathDependencyExclude>com.zaxxer:HikariCP</classpathDependencyExclude>
                        </classpathDependencyExcludes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>

    <!-- Performance benchmark profile -->
    <profile>
        <id>performance-benchmark</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <includes>
                            <include>**/*PerformanceTest.java</include>
                        </includes>
                        <argLine>-Xms512m -Xmx512m -XX:+UseG1GC</argLine>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### 6.2 JMH Benchmark Configuration

```java
/**
 * JMH benchmark base configuration for performance tests.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms256m", "-Xmx256m", "-XX:+UseG1GC"})
public abstract class AbstractJdbcBenchmark {

    protected DataSource dataSource;
    protected DefaultSqlSafetyValidator validator;

    @Setup(Level.Trial)
    public void setupTrial() {
        // Initialize validator and datasource once per trial
    }

    @TearDown(Level.Trial)
    public void teardownTrial() {
        // Cleanup resources
    }
}

/**
 * Example benchmark for Druid filter performance.
 */
public class DruidFilterBenchmark extends AbstractJdbcBenchmark {

    @Benchmark
    public void benchmarkSqlValidation(Blackhole blackhole) {
        // Benchmark implementation
        blackhole.consume(executeSql("SELECT * FROM users WHERE id = 1"));
    }

    @Benchmark
    @Threads(10)
    public void benchmarkConcurrentValidation(Blackhole blackhole) {
        // Concurrent benchmark
        blackhole.consume(executeSql("SELECT * FROM users WHERE id = 1"));
    }
}
```

### 6.3 H2 Database Setup

```java
/**
 * H2 database configuration for tests.
 */
public class H2TestDatabaseConfig {

    /**
     * Creates unique H2 in-memory database URL.
     * Each test gets isolated database instance.
     */
    public static String createUniqueDbUrl() {
        return "jdbc:h2:mem:testdb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
    }

    /**
     * H2 connection properties.
     */
    public static Properties getConnectionProperties() {
        Properties props = new Properties();
        props.setProperty("user", "sa");
        props.setProperty("password", "");
        return props;
    }

    /**
     * Standard test schema DDL.
     */
    public static final String[] SCHEMA_DDL = {
        "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100))",
        "CREATE TABLE orders (id INT PRIMARY KEY, user_id INT, amount DECIMAL(10,2))",
        "CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2))"
    };

    /**
     * Standard test data DML.
     */
    public static final String[] TEST_DATA_DML = {
        "INSERT INTO users VALUES (1, 'Alice', 'alice@example.com')",
        "INSERT INTO users VALUES (2, 'Bob', 'bob@example.com')",
        "INSERT INTO orders VALUES (1, 1, 100.00)",
        "INSERT INTO products VALUES (1, 'Widget', 29.99)"
    };
}
```

### 6.4 Test Data Preparation

**Configuration Files for Testing**:

```yaml
# test-resources/legacy-config.yml
sql-guard:
  jdbc:
    druid:
      enabled: true
      violation-strategy: WARN
    hikari:
      enabled: false
    p6spy:
      enabled: false
```

```properties
# test-resources/legacy-config.properties
sql-guard.jdbc.druid.enabled=true
sql-guard.jdbc.druid.violation-strategy=BLOCK
sql-guard.jdbc.hikari.enabled=false
```

**Synthetic User Project POM for Dependency Testing**:

```xml
<!-- test-resources/user-project-druid-only/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>user-project-druid-only</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <dependency>
            <groupId>com.footstone</groupId>
            <artifactId>sql-guard-jdbc-druid</artifactId>
            <version>${sql-guard.version}</version>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid</artifactId>
            <version>1.2.20</version>
        </dependency>
    </dependencies>
</project>
```

### 6.5 Test Execution Commands

```bash
# Run all module isolation tests
mvn test -pl sql-guard-jdbc-common,sql-guard-jdbc-druid,sql-guard-jdbc-hikari,sql-guard-jdbc-p6spy \
    -Dtest="*IsolationTest"

# Run Druid-only isolation test
mvn test -pl sql-guard-jdbc-druid -P test-druid-only

# Run HikariCP-only isolation test
mvn test -pl sql-guard-jdbc-hikari -P test-hikari-only

# Run P6Spy-only isolation test
mvn test -pl sql-guard-jdbc-p6spy -P test-p6spy-only

# Run backward compatibility tests
mvn test -Dtest="*CompatibilityTest"

# Run performance benchmarks
mvn test -P performance-benchmark -Dtest="*PerformanceTest"

# Run JMH benchmarks
mvn exec:java -Dexec.mainClass="org.openjdk.jmh.Main" -Dexec.args=".*JdbcBenchmark.*"

# Run full test suite for acceptance
mvn verify -pl sql-guard-jdbc-common,sql-guard-jdbc-druid,sql-guard-jdbc-hikari,sql-guard-jdbc-p6spy
```

---

## 7. Acceptance Criteria Mapping

### 7.1 Traceability Matrix

| Test ID | Test Name | Acceptance Criteria | Implementation Plan Reference |
|---------|-----------|---------------------|------------------------------|
| 3.1.1 | testCommonModule_compilesWithoutPoolDependencies | All modules compile independently | Task 11.2 |
| 3.1.2 | testCommonModule_onlyDependsOnCoreAndAuditApi | Minimal dependency footprint | Task 11.2 |
| 3.2.1 | testDruidModule_compilesWithoutHikariP6Spy | Druid module independent | Task 11.3 |
| 3.2.2 | testDruidModule_druidDependencyIsProvided | Provided scope strategy | Task 11.3 |
| 3.3.1 | testHikariModule_compilesWithoutDruidP6Spy | HikariCP module independent | Task 11.4 |
| 3.3.2 | testHikariModule_hikariDependencyIsProvided | Provided scope strategy | Task 11.4 |
| 3.4.1 | testP6SpyModule_compilesWithoutDruidHikari | P6Spy module independent | Task 11.5 |
| 3.4.2 | testP6SpyModule_p6spyDependencyIsProvided | Provided scope strategy | Task 11.5 |
| 3.5.1 | testUserProject_onlyDruidDependency_noTransitivePollution | No transitive pollution | Task 11.3 |
| 3.5.2 | testUserProject_onlyHikariDependency_noTransitivePollution | No transitive pollution | Task 11.4 |
| 3.5.3 | testUserProject_onlyP6SpyDependency_noTransitivePollution | No transitive pollution | Task 11.5 |
| 3.6.1 | testMavenEnforcer_rejectsWrongDependencies | Maven Enforcer validation | Task 11.2 |
| 3.6.2 | testClassLoader_poolClassesNotLoaded_whenModuleNotUsed | ClassLoader isolation | Task 11.6 |
| 3.7.1 | testIndependentJar_druidModulePackagesCorrectly | Correct JAR packaging | Task 11.3 |
| 3.7.2 | testIndependentJar_hikariModulePackagesCorrectly | Correct JAR packaging | Task 11.4 |
| 4.1.1 | testViolationStrategy_oldDruidEnum_stillWorks | 100% backward compatibility | Task 11.2 |
| 4.1.2 | testViolationStrategy_oldHikariEnum_stillWorks | 100% backward compatibility | Task 11.2 |
| 4.1.3 | testViolationStrategy_oldP6SpyEnum_stillWorks | 100% backward compatibility | Task 11.2 |
| 4.2.1 | testDruidFilter_existingCode_noChangesNeeded | Zero breaking changes | Task 11.3 |
| 4.2.2 | testHikariProxy_existingCode_noChangesNeeded | Zero breaking changes | Task 11.4 |
| 4.2.3 | testP6SpyListener_existingCode_noChangesNeeded | Zero breaking changes | Task 11.5 |
| 4.3.1 | testConfiguration_oldYaml_parsesCorrectly | Configuration migration | Task 11.6 |
| 4.3.2 | testConfiguration_oldProperties_parsesCorrectly | Configuration migration | Task 11.6 |
| 4.4.1 | testDeprecatedApi_compiles_withWarning | Deprecated APIs functional | Task 11.2 |
| 4.4.2 | testDeprecatedApi_behavior_unchanged | Behavior preservation | Task 11.2 |
| 4.5.1 | testAllExistingTests_pass100Percent | All existing tests pass | Task 11.6 |
| 4.5.2 | testMigrationGuide_documentsChanges | Clear migration path | Task 11.6 |
| 5.1.1 | testModuleLoading_commonModule_under10ms | Module loading < 10ms | Task 11.6 |
| 5.1.2 | testModuleLoading_druidModule_under10ms | Module loading < 10ms | Task 11.6 |
| 5.1.3 | testModuleLoading_hikariModule_under10ms | Module loading < 10ms | Task 11.6 |
| 5.1.4 | testModuleLoading_p6spyModule_under10ms | Module loading < 10ms | Task 11.6 |
| 5.2.1 | testRuntimePerformance_druid_noRegression | No runtime regression | Task 11.6 |
| 5.2.2 | testRuntimePerformance_hikari_noRegression | No runtime regression | Task 11.6 |
| 5.2.3 | testRuntimePerformance_p6spy_noRegression | No runtime regression | Task 11.6 |
| 5.3.1 | testMemoryUsage_staticFootprint_noIncrease | No memory increase | Task 11.6 |
| 5.3.2 | testMemoryUsage_runtime_noIncrease | No memory increase | Task 11.6 |
| 5.4.1 | testConnectionAcquisition_speed_unchanged | No performance regression | Task 11.6 |
| 5.4.2 | testSqlValidation_throughput_noRegression | No performance regression | Task 11.6 |
| 5.4.3 | testSqlValidation_latency_noRegression | No latency regression | Task 11.6 |
| 5.5.1 | testConcurrentAccess_scalability_maintained | Scalability maintained | Task 11.6 |

### 7.2 Coverage Summary

| Category | Tests | Coverage |
|----------|-------|----------|
| Module Isolation | 15 | All 4 modules covered |
| Backward Compatibility | 12 | ViolationStrategy, APIs, Config |
| Performance Baselines | 13 | Loading, Runtime, Memory, Concurrency |
| **TOTAL** | **40** | **Comprehensive** |

---

## 8. Appendix

### 8.1 Test Naming Conventions

| Convention | Example | Purpose |
|------------|---------|---------|
| `test<Module>_<condition>_<expected>()` | `testDruidModule_compilesWithoutHikari_success()` | Standard test name |
| `*IsolationTest` | `DruidModuleIsolationTest` | Module isolation tests |
| `*CompatibilityTest` | `ViolationStrategyCompatibilityTest` | Backward compatibility tests |
| `*PerformanceTest` | `ModuleLoadingPerformanceTest` | Performance tests |
| `*Benchmark` | `DruidFilterBenchmark` | JMH benchmarks |

### 8.2 Framework Versions

| Framework | Version | Notes |
|-----------|---------|-------|
| JUnit 5 | 5.10.1 | Jupiter API and Engine |
| Mockito | 5.8.0 | Mocking framework |
| AssertJ | 3.24.2 | Fluent assertions |
| H2 | 2.2.224 | In-memory database |
| JMH | 1.37 | Microbenchmarking |
| Maven Surefire | 3.2.2 | Test execution |
| Maven Dependency Plugin | 3.6.1 | Dependency analysis |

### 8.3 Performance Measurement Methodology

#### Baseline Capture Process

1. **Environment Setup**:
   - JDK 17, G1GC, 256MB heap
   - Dedicated test machine (no other workloads)
   - H2 in-memory database

2. **Warmup Phase**:
   - 5 iterations, 1 second each
   - Discarded results (JIT compilation stabilization)

3. **Measurement Phase**:
   - 10 iterations, 1 second each
   - Record mean, P50, P99, P999

4. **Statistical Analysis**:
   - Calculate standard deviation
   - Reject outliers > 3σ
   - Report confidence interval

#### Regression Thresholds

| Metric | Threshold | Action |
|--------|-----------|--------|
| Throughput | < 95% baseline | FAIL |
| P50 Latency | > 110% baseline | WARN |
| P99 Latency | > 120% baseline | FAIL |
| Memory | > 105% baseline | WARN |
| Startup | > 110% baseline | WARN |

---

## Document Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Author | Agent_Testing_Validation | 2025-12-19 | Complete |
| Reviewer | Agent_Core_Engine_Foundation | Pending | - |
| Approver | Manager Agent | Pending | - |

---

**End of Test Design Document**

