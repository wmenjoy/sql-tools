---
agent: Agent_Runtime_Interceptor
task_ref: Task 4.5
depends_on:
  - Task 2.13 (DefaultSqlSafetyValidator Assembly)
ad_hoc_delegation: false
---

# Task 4.5 - JDBC P6Spy Listener Implementation

## Objective
Implement universal JDBC interception via P6Spy proxy driver providing fallback SQL validation for any JDBC-compliant connection pool (C3P0, DBCP, Tomcat JDBC) or direct JDBC usage, leveraging P6Spy's JdbcEventListener for onBeforeAnyExecute callback with parameter-substituted SQL, configuring via spy.properties module registration, and documenting as framework-agnostic solution with acceptable performance trade-offs.

## Context

**Universal JDBC Interception**: P6Spy provides driver-level interception by implementing JDBC Driver interface as proxy driver, wrapping any underlying driver (MySQL, PostgreSQL, Oracle, H2).

**Framework-Agnostic**: Works with any connection pool or ORM framework - ideal fallback when pool-specific solutions (Druid filter, HikariCP proxy) unavailable.

**JdbcEventListener**: P6Spy's callback interface for SQL interception. onBeforeAnyExecute() intercepts all SQL executions.

**Performance Trade-off**: Higher overhead than native solutions (~15% vs ~5%) but acceptable for safety-critical environments. Lower setup complexity.

## Dependencies

### Input from Task 2.13 (Phase 2):
- DefaultSqlSafetyValidator (complete validation engine)
- SqlDeduplicationFilter (prevents redundant validation)
- ViolationStrategy (BLOCK/WARN/LOG)

### Independence:
- Task 4.5 is independent of Tasks 4.1-4.4
- Universal fallback solution
- Works with any JDBC driver/pool

## Implementation Steps

### Step 1: P6Spy Listener TDD
**Goal**: Create JdbcEventListener implementation

**Tasks**:
1. Add P6Spy dependency to `sql-guard-jdbc/pom.xml`:
   ```xml
   <dependency>
       <groupId>p6spy</groupId>
       <artifactId>p6spy</artifactId>
       <version>3.9.1</version>
       <scope>provided</scope>
   </dependency>
   ```

2. Create `P6SpySqlSafetyListener` class:
   ```java
   public class P6SpySqlSafetyListener extends JdbcEventListener {
       private final DefaultSqlSafetyValidator validator;
       private final ViolationStrategy strategy;

       @Override
       public void onBeforeAnyExecute(StatementInformation statementInfo) {
           // Extract SQL with parameter values substituted
           String sql = statementInfo.getSqlWithValues();

           // Validate
           validateSql(sql, statementInfo);
       }

       private void validateSql(String sql, StatementInformation statementInfo) {
           // Deduplication check
           if (!shouldValidate(sql)) {
               return;
           }

           // Detect SQL type
           SqlCommandType type = detectSqlType(sql);

           // Build SqlContext
           SqlContext context = SqlContext.builder()
               .sql(sql)
               .type(type)
               .mapperId("jdbc-p6spy")
               .build();

           // Validate
           ValidationResult result = validator.validate(context);

           // Handle violations
           if (!result.passed()) {
               handleViolation(result);
           }
       }

       private void handleViolation(ValidationResult result)
               throws SQLException {
           String violationMsg = formatViolations(result);

           switch (strategy) {
               case BLOCK:
                   logger.error("[BLOCK] SQL Safety Violation: {}", violationMsg);
                   throw new SQLException(
                       "SQL Safety Violation (BLOCK): " + violationMsg,
                       "42000"
                   );

               case WARN:
                   logger.error("[WARN] SQL Safety Violation: {}", violationMsg);
                   break;

               case LOG:
                   logger.warn("[LOG] SQL Safety Violation: {}", violationMsg);
                   break;
           }
       }
   }
   ```

**Test Requirements**:
- `P6SpySqlSafetyListenerTest.java` (10 tests):
  - testOnBeforeAnyExecute_shouldValidate()
  - testSqlWithValues_shouldExtractSubstituted()
  - testPreparedStatementExecution_shouldIntercept()
  - testStatementExecution_shouldIntercept()
  - testBatchExecution_shouldIntercept()
  - testBLOCKStrategy_shouldThrowException()
  - testWARNStrategy_shouldLogAndContinue()
  - testLOGStrategy_shouldOnlyLog()
  - testValidSql_shouldProceed()
  - testDeduplication_sameSQL_shouldSkip()

**Files to Create**:
- `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/p6spy/P6SpySqlSafetyListener.java`
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/p6spy/P6SpySqlSafetyListenerTest.java`

---

### Step 2: onBeforeAnyExecute Implementation
**Goal**: Extract and validate SQL from StatementInformation

**Tasks**:
1. Extract SQL with parameter values:
   ```java
   @Override
   public void onBeforeAnyExecute(StatementInformation statementInfo) {
       // P6Spy provides SQL with parameters substituted
       // Example: "SELECT * FROM user WHERE id=123"
       String sql = statementInfo.getSqlWithValues();

       // Skip if empty
       if (sql == null || sql.trim().isEmpty()) {
           return;
       }

       // Validate
       validateSql(sql, statementInfo);
   }
   ```

2. Understand StatementInformation:
   ```java
   // P6Spy provides rich execution context
   StatementInformation statementInfo = ...;

   // SQL with values: SELECT * FROM user WHERE id=123
   String sqlWithValues = statementInfo.getSqlWithValues();

   // SQL with placeholders: SELECT * FROM user WHERE id=?
   String sql = statementInfo.getSql();

   // Connection info
   ConnectionInformation connInfo = statementInfo.getConnectionInformation();
   String url = connInfo.getUrl();
   ```

3. Implement validateSql:
   ```java
   private void validateSql(String sql, StatementInformation statementInfo) {
       // Deduplication check
       if (!shouldValidate(sql)) {
           return;
       }

       // Detect SqlCommandType
       SqlCommandType type = detectSqlType(sql);

       // Extract datasource info (from connection URL if available)
       String datasource = extractDatasourceFromUrl(
           statementInfo.getConnectionInformation().getUrl());

       // Build SqlContext
       SqlContext context = SqlContext.builder()
           .sql(sql)
           .type(type)
           .mapperId("jdbc-p6spy:" + datasource)
           .datasource(datasource)
           .build();

       // Validate
       ValidationResult result = validator.validate(context);

       // Handle violations
       if (!result.passed()) {
           handleViolation(result);
       }
   }
   ```

**Test Requirements**:
- `OnBeforeAnyExecuteTest.java` (12 tests):
  - testExtractSqlWithValues_shouldGetSubstituted()
  - testExtractSqlWithValues_prepared_shouldSubstituteParams()
  - testExtractSqlWithValues_statement_shouldGetOriginal()
  - testDetectSqlType_SELECT_shouldReturn()
  - testDetectSqlType_UPDATE_shouldReturn()
  - testDetectSqlType_DELETE_shouldReturn()
  - testDetectSqlType_INSERT_shouldReturn()
  - testExtractDatasource_fromUrl_shouldParse()
  - testBuildSqlContext_shouldPopulateFields()
  - testHandleViolation_BLOCK_shouldThrow()
  - testHandleViolation_WARN_shouldLog()
  - testDeduplication_shouldPreventDouble()

**Files to Modify**:
- `P6SpySqlSafetyListener.java`

**Files to Create**:
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/p6spy/OnBeforeAnyExecuteTest.java`

---

### Step 3: Module Registration and Configuration
**Goal**: Register listener via P6Spy SPI

**Tasks**:
1. Create P6Spy module class:
   ```java
   public class P6SpySqlSafetyModule extends JdbcEventListener {
       private static P6SpySqlSafetyListener listener;

       static {
           // Initialize validator (use ServiceLoader or static factory)
           DefaultSqlSafetyValidator validator = loadValidator();
           ViolationStrategy strategy = loadStrategy();

           listener = new P6SpySqlSafetyListener(validator, strategy);
       }

       @Override
       public void onBeforeAnyExecute(StatementInformation statementInfo) {
           listener.onBeforeAnyExecute(statementInfo);
       }

       private static DefaultSqlSafetyValidator loadValidator() {
           // ServiceLoader approach
           ServiceLoader<SqlSafetyValidator> loader =
               ServiceLoader.load(SqlSafetyValidator.class);

           return (DefaultSqlSafetyValidator) loader.findFirst()
               .orElseThrow(() -> new IllegalStateException(
                   "SqlSafetyValidator not found"));
       }
   }
   ```

2. Create `spy.properties` configuration:
   ```properties
   # Module registration
   modulelist=com.footstone.sqlguard.interceptor.p6spy.P6SpySqlSafetyModule

   # Logging appender (SLF4J integration)
   appender=com.p6spy.engine.spy.appender.Slf4JLogger

   # Database drivers to wrap
   driverlist=com.mysql.cj.jdbc.Driver,org.postgresql.Driver,org.h2.Driver

   # Log format (optional)
   logMessageFormat=com.p6spy.engine.spy.appender.SingleLineFormat

   # Exclude system tables (optional)
   excludecategories=info,debug

   # Date format (optional)
   dateformat=yyyy-MM-dd HH:mm:ss
   ```

3. Create setup guide documentation:
   ```markdown
   # P6Spy SQL Safety Guard Setup

   ## 1. Driver Configuration

   ### Original Configuration:
   ```properties
   spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
   spring.datasource.url=jdbc:mysql://localhost:3306/mydb
   ```

   ### P6Spy Configuration:
   ```properties
   spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver
   spring.datasource.url=jdbc:p6spy:mysql://localhost:3306/mydb
   ```

   ## 2. Add spy.properties to classpath

   ## 3. Add dependencies
   ```xml
   <dependency>
       <groupId>p6spy</groupId>
       <artifactId>p6spy</artifactId>
       <version>3.9.1</version>
   </dependency>
   <dependency>
       <groupId>com.footstone</groupId>
       <artifactId>sql-guard-jdbc</artifactId>
       <version>${project.version}</version>
   </dependency>
   ```
   ```

**Test Requirements**:
- `ModuleRegistrationTest.java` (10 tests):
  - testModuleClass_shouldExtendJdbcEventListener()
  - testStaticInitialization_shouldLoadValidator()
  - testOnBeforeAnyExecute_shouldDelegateToListener()
  - testLoadValidator_serviceLoader_shouldWork()
  - testLoadStrategy_fromProperties_shouldWork()
  - testSpyProperties_modulelist_shouldRegister()
  - testSpyProperties_driverlist_shouldContainDrivers()
  - testSpyProperties_appender_shouldBeSLF4J()
  - testP6SpyDriver_shouldWrapOriginalDriver()
  - testUrlModification_shouldWork()

**Files to Create**:
- `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/p6spy/P6SpySqlSafetyModule.java`
- `sql-guard-jdbc/src/main/resources/spy.properties`
- `sql-guard-jdbc/docs/p6spy-setup.md`
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/p6spy/ModuleRegistrationTest.java`

---

### Step 4: Integration and Compatibility Testing
**Goal**: Test with multiple connection pools and drivers

**Tasks**:
1. Test with bare JDBC (DriverManager):
   ```java
   @Test
   void testBareJDBC_shouldIntercept() throws SQLException {
       // P6Spy wraps DriverManager
       Connection conn = DriverManager.getConnection(
           "jdbc:p6spy:h2:mem:test");

       Statement stmt = conn.createStatement();
       assertThrows(SQLException.class, () -> {
           stmt.executeQuery("SELECT * FROM user");
       });
   }
   ```

2. Test with C3P0:
   ```java
   @Test
   void testC3P0_shouldIntercept() throws SQLException {
       ComboPooledDataSource dataSource = new ComboPooledDataSource();
       dataSource.setDriverClass("com.p6spy.engine.spy.P6SpyDriver");
       dataSource.setJdbcUrl("jdbc:p6spy:h2:mem:test");

       try (Connection conn = dataSource.getConnection()) {
           // P6Spy intercepts SQL from C3P0 pool
           assertThrows(SQLException.class, () -> {
               Statement stmt = conn.createStatement();
               stmt.executeQuery("SELECT * FROM user");
           });
       }
   }
   ```

3. Test with Apache DBCP:
   ```java
   @Test
   void testDBCP_shouldIntercept() throws SQLException {
       BasicDataSource dataSource = new BasicDataSource();
       dataSource.setDriverClassName("com.p6spy.engine.spy.P6SpyDriver");
       dataSource.setUrl("jdbc:p6spy:h2:mem:test");

       try (Connection conn = dataSource.getConnection()) {
           // P6Spy intercepts SQL from DBCP pool
       }
   }
   ```

4. Test with Tomcat JDBC Pool:
   ```java
   @Test
   void testTomcatJDBC_shouldIntercept() {
       org.apache.tomcat.jdbc.pool.DataSource dataSource =
           new org.apache.tomcat.jdbc.pool.DataSource();
       dataSource.setDriverClassName("com.p6spy.engine.spy.P6SpyDriver");
       dataSource.setUrl("jdbc:p6spy:h2:mem:test");

       // P6Spy intercepts SQL from Tomcat JDBC pool
   }
   ```

**Test Requirements**:
- `P6SpyIntegrationTest.java` (15 tests):
  - testBareJDBC_shouldIntercept()
  - testBareJDBC_dangerous_shouldBlock()
  - testBareJDBC_safe_shouldProceed()
  - testC3P0_shouldIntercept()
  - testC3P0_pooling_shouldWork()
  - testDBCP_shouldIntercept()
  - testDBCP_pooling_shouldWork()
  - testTomcatJDBC_shouldIntercept()
  - testTomcatJDBC_pooling_shouldWork()
  - testPreparedStatement_shouldIntercept()
  - testStatement_shouldIntercept()
  - testBatchOperations_shouldValidate()
  - testCallableStatement_shouldIntercept()
  - testMultipleDrivers_shouldSupportAll()
  - testDeduplication_acrossPools_shouldWork()

**Files to Create**:
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/p6spy/P6SpyIntegrationTest.java`

---

### Step 5: Multi-Driver Compatibility Testing
**Goal**: Test with multiple JDBC drivers

**Tasks**:
1. Test with MySQL driver:
   ```java
   @Test
   void testMySQLDriver_shouldIntercept() {
       // Configure P6Spy with MySQL driver
       // jdbc:p6spy:mysql://localhost:3306/test
   }
   ```

2. Test with PostgreSQL driver:
   ```java
   @Test
   void testPostgreSQLDriver_shouldIntercept() {
       // jdbc:p6spy:postgresql://localhost:5432/test
   }
   ```

3. Test with H2 driver (in-memory):
   ```java
   @Test
   void testH2Driver_shouldIntercept() {
       // jdbc:p6spy:h2:mem:test
   }
   ```

4. Test with Oracle driver (optional):
   ```java
   @Test
   void testOracleDriver_shouldIntercept() {
       // jdbc:p6spy:oracle:thin:@localhost:1521:orcl
   }
   ```

**Test Requirements**:
- `P6SpyMultiDriverTest.java` (12 tests):
  - testMySQLDriver_shouldWrap()
  - testMySQLDriver_sqlValidation_shouldWork()
  - testPostgreSQLDriver_shouldWrap()
  - testPostgreSQLDriver_sqlValidation_shouldWork()
  - testH2Driver_shouldWrap()
  - testH2Driver_sqlValidation_shouldWork()
  - testDriverSwitching_shouldWork()
  - testMultipleDataSources_differentDrivers()
  - testUrlParsing_mysql_shouldWork()
  - testUrlParsing_postgresql_shouldWork()
  - testUrlParsing_h2_shouldWork()
  - testDriverCompatibility_allSupported()

**Files to Create**:
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/p6spy/P6SpyMultiDriverTest.java`

---

### Step 6: Performance Documentation and Setup Guide
**Goal**: Document performance trade-offs and setup process

**Tasks**:
1. Performance benchmark with JMH:
   ```java
   @State(Scope.Benchmark)
   public class P6SpyPerformanceTest {
       DataSource withoutP6Spy;
       DataSource withP6Spy;

       @Benchmark
       public void executeSQL_withoutP6Spy() throws SQLException {
           try (Connection conn = withoutP6Spy.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM user WHERE id = ?")) {
               ps.setLong(1, 1L);
               ps.executeQuery();
           }
       }

       @Benchmark
       public void executeSQL_withP6Spy() throws SQLException {
           // Same with P6Spy
       }

       // Expected: ~15% overhead
       // Compare to: Druid ~5%, HikariCP ~3%
   }
   ```

2. Create comprehensive setup guide:
   ```markdown
   # P6Spy SQL Safety Guard - Complete Setup Guide

   ## When to Use P6Spy

   **Use P6Spy when:**
   - Using C3P0, DBCP, or Tomcat JDBC Pool
   - No connection pool (bare JDBC)
   - Legacy application with minimal code changes required
   - Fallback when native integration unavailable

   **Don't use P6Spy when:**
   - Using Druid (use DruidSqlSafetyFilter instead)
   - Using HikariCP (use HikariSqlSafetyProxyFactory instead)
   - Using MyBatis/MyBatis-Plus (use interceptors instead)
   - Performance critical (15% overhead vs 5% native)

   ## Performance Trade-offs

   | Solution | Overhead | Setup Complexity | Coverage |
   |----------|----------|------------------|----------|
   | MyBatis Interceptor | <5% | Low | MyBatis only |
   | Druid Filter | ~5% | Medium | Druid pool only |
   | HikariCP Proxy | ~3% | Medium | HikariCP only |
   | **P6Spy Listener** | **~15%** | **Low** | **Universal** |

   ## Quick Start (5 steps)

   ### 1. Add dependencies
   ### 2. Modify driver configuration
   ### 3. Add spy.properties to classpath
   ### 4. Verify module registration
   ### 5. Test with sample query

   ## Deployment Scenarios

   ### Scenario 1: Quick Deployment (Legacy App)
   - Minimal code changes
   - Just configuration changes
   - Works with any JDBC driver/pool

   ### Scenario 2: Multi-Pool Environment
   - Mix of C3P0, DBCP, bare JDBC
   - Single P6Spy configuration covers all
   - Centralized SQL validation

   ### Scenario 3: Fallback Solution
   - Primary: Native integration (Druid/HikariCP)
   - Fallback: P6Spy for other pools
   - Both can coexist

   ## Troubleshooting

   ### Issue 1: Module not loaded
   ### Issue 2: Validator not found
   ### Issue 3: Double validation
   ### Issue 4: Performance degradation
   ```

**Test Requirements**:
- `P6SpyPerformanceTest.java` (JMH benchmark):
  - Baseline: SQL execution without P6Spy
  - With P6Spy: SQL execution with P6Spy wrapper
  - Measure overhead percentage
  - Expected: ~15% overhead
  - Document findings for performance-sensitive environments

**Files to Create**:
- `sql-guard-jdbc/docs/p6spy-performance-analysis.md`
- `sql-guard-jdbc/docs/p6spy-setup-guide.md`
- `sql-guard-jdbc/docs/p6spy-troubleshooting.md`
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/p6spy/P6SpyPerformanceTest.java`

---

## Expected Outcomes

### Functional Outcomes:
1. ✅ P6Spy JdbcEventListener intercepts all SQL
2. ✅ Works with any JDBC driver (MySQL, PostgreSQL, H2, Oracle)
3. ✅ Works with any connection pool (C3P0, DBCP, Tomcat JDBC, bare JDBC)
4. ✅ Parameter-substituted SQL validated
5. ✅ Module registered via SPI
6. ✅ Configuration via spy.properties

### Test Outcomes:
- Total new tests: **59 tests** + JMH benchmark
- Integration tests with 4+ connection pools
- Multi-driver compatibility verified
- Performance overhead documented (~15%)

### Architecture Outcomes:
- ✅ Universal JDBC interception (fallback solution)
- ✅ Framework-agnostic implementation
- ✅ Low setup complexity
- ✅ Acceptable performance trade-off for safety

## Validation Criteria

### Must Pass Before Completion:
1. All 59 tests passing (100% pass rate)
2. C3P0 integration working
3. DBCP integration working
4. Tomcat JDBC integration working
5. Bare JDBC working
6. MySQL driver compatibility verified
7. PostgreSQL driver compatibility verified
8. H2 driver compatibility verified
9. Performance overhead measured and documented (~15%)
10. Setup guide complete and tested

### Performance Benchmarks:
1. Overhead: ~15% (documented)
2. Comparison to native solutions: documented
3. Trade-off analysis: documented

### Code Quality:
1. Google Java Style compliance
2. Comprehensive Javadoc
3. SLF4J logging
4. Complete documentation

## Success Metrics

- ✅ 59 tests passing (100%)
- ✅ 4+ connection pools supported
- ✅ 3+ JDBC drivers verified
- ✅ Setup guide complete
- ✅ Performance documented (~15%)
- ✅ Troubleshooting guide created

## Timeline Estimate
- Step 1: 1 hour (Listener + 10 tests)
- Step 2: 1.5 hours (onBeforeAnyExecute + 12 tests)
- Step 3: 1.5 hours (Module + Configuration + 10 tests)
- Step 4: 2 hours (Integration + 15 tests)
- Step 5: 1.5 hours (Multi-driver + 12 tests)
- Step 6: 2 hours (Performance + Documentation + JMH)

**Total**: ~9.5 hours

## Definition of Done

- [ ] All 59 tests passing
- [ ] C3P0 integration working
- [ ] DBCP integration working
- [ ] Tomcat JDBC integration working
- [ ] Bare JDBC working
- [ ] MySQL driver verified
- [ ] PostgreSQL driver verified
- [ ] H2 driver verified
- [ ] spy.properties template created
- [ ] Setup guide complete
- [ ] Performance analysis documented
- [ ] Troubleshooting guide created
- [ ] Memory Log created
- [ ] Phase 4 complete (all 5 tasks done)

---

## Phase 4 Summary

With Task 4.5 completion, Phase 4 (Runtime Interception System) will be complete:

**Completed Runtime Interceptors:**
- ✅ Task 4.1 - MyBatis Interceptor (MyBatis-specific)
- ✅ Task 4.2 - MyBatis-Plus InnerInterceptor (MyBatis-Plus-specific)
- ✅ Task 4.3 - Druid Filter (Druid connection pool)
- ✅ Task 4.4 - HikariCP Proxy (HikariCP connection pool)
- ✅ Task 4.5 - P6Spy Listener (Universal fallback)

**Total Coverage:**
- ORM layer: MyBatis, MyBatis-Plus
- Connection pool layer: Druid, HikariCP, C3P0, DBCP, Tomcat JDBC
- JDBC layer: Any JDBC driver (via P6Spy)
- Universal fallback: P6Spy for unsupported pools

**Dual-Layer Defense Complete:**
- Static analysis: Phase 3 scanner (development-time)
- Runtime validation: Phase 4 interceptors (execution-time)

---

**End of Task Assignment**

P6Spy provides universal JDBC interception as fallback solution when native integrations unavailable. Higher performance overhead (~15%) but lower setup complexity and framework-agnostic coverage.
