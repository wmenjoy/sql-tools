---
agent: Agent_Runtime_Interceptor
task_ref: Task 4.4
depends_on:
  - Task 2.13 (DefaultSqlSafetyValidator Assembly)
ad_hoc_delegation: false
---

# Task 4.4 - JDBC HikariCP Proxy Implementation

## Objective
Implement HikariCP ProxyFactory providing Connection dynamic proxy for SQL interception at JDBC layer, capturing PreparedStatement and Statement SQL via nested invocation handlers, validating before actual statement creation/execution, and integrating seamlessly with HikariCP's high-performance connection pooling without disrupting leak detection or pool lifecycle.

## Context

**HikariCP ProxyFactory**: HikariCP provides ProxyFactory interface for custom connection proxying, enabling SQL interception without filter API (unlike Druid).

**Dynamic Proxy Pattern**: JDK dynamic proxy wraps Connection objects returned from pool, intercepting method calls.

**Two-Layer Proxying**:
- Connection proxy intercepts prepareStatement(sql) - SQL at prepare time
- Statement proxy intercepts execute(sql) - SQL at execute time

**Performance Critical**: HikariCP targets microsecond-level connection overhead - proxy must add minimal latency.

## Dependencies

### Input from Task 2.13 (Phase 2):
- DefaultSqlSafetyValidator (complete validation engine)
- SqlDeduplicationFilter (prevents redundant validation)
- ViolationStrategy (BLOCK/WARN/LOG)

### Independence:
- Task 4.4 is independent of Tasks 4.1-4.3
- HikariCP-specific implementation
- Can coexist with other interceptors

## Implementation Steps

### Step 1: ProxyFactory TDD
**Goal**: Implement HikariCP ProxyFactory interface

**Tasks**:
1. Add HikariCP dependency to `sql-guard-jdbc/pom.xml`:
   ```xml
   <dependency>
       <groupId>com.zaxxer</groupId>
       <artifactId>HikariCP</artifactId>
       <version>5.0.1</version>
       <scope>provided</scope>
   </dependency>
   ```

2. Create `HikariSqlSafetyProxyFactory` class:
   ```java
   public class HikariSqlSafetyProxyFactory implements ProxyFactory {
       private final DefaultSqlSafetyValidator validator;
       private final ViolationStrategy strategy;

       @Override
       public Connection getProxyConnection(
           PoolEntry poolEntry, Connection connection,
           FastList<Statement> openStatements, boolean isReadOnly,
           boolean autoCommit, int transactionIsolation,
           int networkTimeout) {

           return (Connection) Proxy.newProxyInstance(
               connection.getClass().getClassLoader(),
               new Class<?>[] { Connection.class },
               new ConnectionInvocationHandler(
                   connection, poolEntry, validator, strategy)
           );
       }
   }
   ```

3. Implement ConnectionInvocationHandler:
   ```java
   private static class ConnectionInvocationHandler
       implements InvocationHandler {

       private final Connection delegate;
       private final PoolEntry poolEntry;
       private final DefaultSqlSafetyValidator validator;
       private final ViolationStrategy strategy;

       @Override
       public Object invoke(Object proxy, Method method, Object[] args)
           throws Throwable {
           String methodName = method.getName();

           // Intercept prepareStatement
           if ("prepareStatement".equals(methodName) && args.length > 0) {
               String sql = (String) args[0];
               validateSql(sql, poolEntry);

               // Create PreparedStatement and wrap in proxy
               PreparedStatement ps =
                   (PreparedStatement) method.invoke(delegate, args);
               return wrapStatement(ps);
           }

           // Intercept createStatement
           if ("createStatement".equals(methodName)) {
               Statement stmt =
                   (Statement) method.invoke(delegate, args);
               return wrapStatement(stmt);
           }

           // Delegate other methods
           return method.invoke(delegate, args);
       }
   }
   ```

**Test Requirements**:
- `HikariSqlSafetyProxyFactoryTest.java` (10 tests):
  - testGetProxyConnection_shouldReturnProxy()
  - testProxyConnection_isProxy_shouldBeTrue()
  - testPrepareStatement_shouldIntercept()
  - testCreateStatement_shouldReturnProxy()
  - testDelegation_otherMethods_shouldWork()
  - testClose_shouldDelegateCorrectly()
  - testGetMetaData_shouldDelegate()
  - testProxyHashCode_shouldWork()
  - testProxyEquals_shouldWork()
  - testProxyToString_shouldWork()

**Files to Create**:
- `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/hikari/HikariSqlSafetyProxyFactory.java`
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/hikari/HikariSqlSafetyProxyFactoryTest.java`

---

### Step 2: ConnectionInvocationHandler Implementation
**Goal**: Intercept Connection methods

**Tasks**:
1. Implement prepareStatement interception:
   ```java
   private Object handlePrepareStatement(Method method, Object[] args)
       throws Throwable {
       String sql = (String) args[0];

       // Validate at prepare time
       validateSql(sql, poolEntry);

       // Create actual PreparedStatement
       PreparedStatement ps =
           (PreparedStatement) method.invoke(delegate, args);

       // Wrap in proxy for execution interception
       return wrapPreparedStatement(ps, sql);
   }
   ```

2. Implement createStatement interception:
   ```java
   private Object handleCreateStatement(Method method, Object[] args)
       throws Throwable {
       // Create actual Statement
       Statement stmt = (Statement) method.invoke(delegate, args);

       // Wrap in proxy (no SQL yet, will intercept execute(sql))
       return wrapStatement(stmt);
   }
   ```

3. Implement validateSql:
   ```java
   private void validateSql(String sql, PoolEntry poolEntry)
       throws SQLException {
       // Deduplication check
       if (!shouldValidate(sql)) {
           return;
       }

       // Detect SQL type
       SqlCommandType type = detectSqlType(sql);

       // Extract datasource info
       String datasourceName = extractDatasourceName(poolEntry);

       // Build SqlContext
       SqlContext context = SqlContext.builder()
           .sql(sql)
           .type(type)
           .mapperId("jdbc-hikari:" + datasourceName)
           .datasource(datasourceName)
           .build();

       // Validate
       ValidationResult result = validator.validate(context);

       // Handle violations
       if (!result.passed()) {
           handleViolation(result, datasourceName);
       }
   }
   ```

**Test Requirements**:
- `ConnectionInvocationHandlerTest.java` (12 tests):
  - testPrepareStatement_shouldValidateSql()
  - testPrepareStatement_dangerous_shouldBlock()
  - testPrepareStatement_safe_shouldProceed()
  - testCreateStatement_shouldReturnProxy()
  - testClose_shouldDelegateToConnection()
  - testCommit_shouldDelegate()
  - testRollback_shouldDelegate()
  - testSetAutoCommit_shouldDelegate()
  - testGetMetaData_shouldDelegate()
  - testIsClosed_shouldDelegate()
  - testDatasourceExtraction_shouldWork()
  - testDeduplication_sameSQL_shouldSkip()

**Files to Modify**:
- `HikariSqlSafetyProxyFactory.java`

**Files to Create**:
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/hikari/ConnectionInvocationHandlerTest.java`

---

### Step 3: StatementInvocationHandler Implementation
**Goal**: Intercept Statement execution methods

**Tasks**:
1. Create StatementInvocationHandler for PreparedStatement proxy:
   ```java
   private static class PreparedStatementInvocationHandler
       implements InvocationHandler {

       private final PreparedStatement delegate;
       private final String sql;  // Already validated at prepare time

       @Override
       public Object invoke(Object proxy, Method method, Object[] args)
           throws Throwable {
           String methodName = method.getName();

           // PreparedStatement.execute() - no SQL parameter
           // Already validated at prepareStatement time
           // Just delegate

           return method.invoke(delegate, args);
       }
   }
   ```

2. Create StatementInvocationHandler for Statement proxy:
   ```java
   private static class StatementInvocationHandler
       implements InvocationHandler {

       private final Statement delegate;
       private final DefaultSqlSafetyValidator validator;
       private final ViolationStrategy strategy;
       private final PoolEntry poolEntry;

       @Override
       public Object invoke(Object proxy, Method method, Object[] args)
           throws Throwable {
           String methodName = method.getName();

           // Intercept execute(String sql)
           if ("execute".equals(methodName) && args.length > 0) {
               String sql = (String) args[0];
               validateSql(sql, poolEntry);
           }

           // Intercept executeQuery(String sql)
           if ("executeQuery".equals(methodName) && args.length > 0) {
               String sql = (String) args[0];
               validateSql(sql, poolEntry);
           }

           // Intercept executeUpdate(String sql)
           if ("executeUpdate".equals(methodName) && args.length > 0) {
               String sql = (String) args[0];
               validateSql(sql, poolEntry);
           }

           // Intercept addBatch(String sql) for Statement
           if ("addBatch".equals(methodName) && args.length > 0) {
               String sql = (String) args[0];
               validateSql(sql, poolEntry);
           }

           // Delegate
           return method.invoke(delegate, args);
       }
   }
   ```

3. Handle batch operations:
   - PreparedStatement.addBatch() - no SQL parameter, validated at prepare
   - Statement.addBatch(String sql) - validate each SQL
   - executeBatch() - just delegate, individual SQLs already validated

**Test Requirements**:
- `StatementInvocationHandlerTest.java` (15 tests):
  - testPreparedStatement_execute_shouldDelegate()
  - testPreparedStatement_executeQuery_shouldDelegate()
  - testPreparedStatement_executeUpdate_shouldDelegate()
  - testPreparedStatement_addBatch_shouldDelegate()
  - testPreparedStatement_executeBatch_shouldDelegate()
  - testStatement_execute_shouldValidate()
  - testStatement_executeQuery_shouldValidate()
  - testStatement_executeUpdate_shouldValidate()
  - testStatement_addBatch_shouldValidate()
  - testStatement_executeBatch_shouldDelegate()
  - testStatement_dangerous_shouldBlock()
  - testStatement_safe_shouldProceed()
  - testBatchOperations_multipleSQL_shouldValidateEach()
  - testClose_shouldDelegate()
  - testGetResultSet_shouldDelegate()

**Files to Modify**:
- `HikariSqlSafetyProxyFactory.java`

**Files to Create**:
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/hikari/StatementInvocationHandlerTest.java`

---

### Step 4: HikariCP Configuration and Integration
**Goal**: Integrate with HikariConfig

**Tasks**:
1. Configure HikariCP to use custom ProxyFactory:
   ```java
   public class HikariSqlSafetyConfiguration {
       public static void configureHikariCP(
           HikariConfig config,
           DefaultSqlSafetyValidator validator,
           ViolationStrategy strategy) {

           HikariSqlSafetyProxyFactory proxyFactory =
               new HikariSqlSafetyProxyFactory(validator, strategy);

           config.setProxyFactory(proxyFactory);
       }
   }
   ```

2. Spring Boot auto-configuration:
   ```java
   @Bean
   @ConditionalOnClass(HikariDataSource.class)
   public HikariSqlSafetyProxyFactory hikariProxyFactory(
       DefaultSqlSafetyValidator validator) {
       return new HikariSqlSafetyProxyFactory(
           validator, ViolationStrategy.WARN);
   }

   @Bean
   public BeanPostProcessor hikariConfigPostProcessor(
       HikariSqlSafetyProxyFactory proxyFactory) {
       return new BeanPostProcessor() {
           @Override
           public Object postProcessBeforeInitialization(
               Object bean, String beanName) {
               if (bean instanceof HikariConfig) {
                   HikariConfig config = (HikariConfig) bean;
                   config.setProxyFactory(proxyFactory);
               }
               return bean;
           }
       };
   }
   ```

3. Test leak detection compatibility:
   - HikariCP tracks connection usage via ProxyConnection
   - Custom proxy must preserve leak detection
   - Test: intentionally leak connection, verify HikariCP detects

**Test Requirements**:
- `HikariConfigurationTest.java` (10 tests):
  - testSetProxyFactory_shouldRegister()
  - testGetConnection_shouldReturnProxy()
  - testProxyFactory_applied_shouldValidate()
  - testSpringBoot_autoConfiguration_shouldWork()
  - testBeanPostProcessor_shouldApplyProxy()
  - testMultipleDatasources_shouldConfigureEach()
  - testLeakDetection_shouldStillWork()
  - testLeakDetection_intentionalLeak_shouldDetect()
  - testConnectionMetrics_shouldWork()
  - testHealthCheck_shouldWork()

**Files to Create**:
- `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/hikari/HikariSqlSafetyConfiguration.java`
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/hikari/HikariConfigurationTest.java`

---

### Step 5: Performance and Edge Case Testing
**Goal**: Verify minimal overhead and edge cases

**Tasks**:
1. Performance benchmark with JMH:
   ```java
   @State(Scope.Benchmark)
   public class HikariProxyPerformanceTest {
       HikariDataSource withoutProxy;
       HikariDataSource withProxy;

       @Benchmark
       public void getConnection_withoutProxy() throws SQLException {
           try (Connection conn = withoutProxy.getConnection()) {
               // Connection acquisition overhead
           }
       }

       @Benchmark
       public void getConnection_withProxy() throws SQLException {
           try (Connection conn = withProxy.getConnection()) {
               // Connection acquisition with proxy overhead
           }
       }

       @Benchmark
       public void prepareAndExecute_withoutProxy() throws SQLException {
           try (Connection conn = withoutProxy.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM user WHERE id = ?")) {
               ps.setLong(1, 1L);
               ps.executeQuery();
           }
       }

       @Benchmark
       public void prepareAndExecute_withProxy() throws SQLException {
           // Same with proxy
       }
   }
   ```

2. Edge case testing:
   ```java
   @Test
   void testConnectionClose_multipleTimes_shouldBeIdempotent() {
       Connection conn = dataSource.getConnection();
       conn.close();
       conn.close();  // Should not throw
       conn.close();
   }

   @Test
   void testPreparedStatement_reuse_shouldNotRevalidate() {
       try (Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM user WHERE id = ?")) {

           // Execute multiple times with different params
           for (int i = 0; i < 10; i++) {
               ps.setLong(1, i);
               ps.executeQuery();
           }
           // SQL validated once at prepare, not on each execute
       }
   }

   @Test
   void testCallableStatement_shouldIntercept() {
       try (Connection conn = dataSource.getConnection();
            CallableStatement cs = conn.prepareCall(
                "{call dangerous_proc()}")) {
           cs.execute();
       }
   }
   ```

3. HikariCP metrics integration:
   ```java
   @Test
   void testMetrics_violations_shouldNotAffect() {
       HikariPoolMXBean poolMBean = dataSource.getHikariPoolMXBean();

       int activeConnections = poolMBean.getActiveConnections();
       int idleConnections = poolMBean.getIdleConnections();

       // Execute SQL with violations
       try {
           // ...
       } catch (SQLException e) {
           // Blocked by safety validator
       }

       // Metrics should still be accurate
       assertEquals(activeConnections,
           poolMBean.getActiveConnections());
   }
   ```

**Test Requirements**:
- `HikariEdgeCasesTest.java` (12 tests):
  - testConnectionClose_multipleTimes_idempotent()
  - testPreparedStatement_reuse_shouldNotRevalidate()
  - testPreparedStatement_parameterBinding_shouldWork()
  - testCallableStatement_shouldIntercept()
  - testBatchOperations_shouldValidate()
  - testTransactions_commit_shouldWork()
  - testTransactions_rollback_shouldWork()
  - testSavepoint_shouldWork()
  - testConnectionPooling_borrowReturn_correct()
  - testMetrics_violations_shouldNotAffect()
  - testHealthCheck_violations_shouldNotAffect()
  - testMaxLifetime_shouldWork()

- `HikariProxyPerformanceTest.java` (JMH benchmark):
  - Connection acquisition overhead: <1%
  - SQL execution overhead: <5%
  - Target: Microsecond-level proxy overhead

**Files to Create**:
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/hikari/HikariEdgeCasesTest.java`
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/hikari/HikariProxyPerformanceTest.java`

---

### Step 6: Integration Testing with Real HikariDataSource
**Goal**: Full integration test

**Tasks**:
1. Create integration test:
   ```java
   @BeforeEach
   void setup() {
       HikariConfig config = new HikariConfig();
       config.setJdbcUrl("jdbc:h2:mem:test");
       config.setDriverClassName("org.h2.Driver");
       config.setMaximumPoolSize(10);

       // Configure proxy factory
       HikariSqlSafetyConfiguration.configureHikariCP(
           config, validator, ViolationStrategy.BLOCK);

       dataSource = new HikariDataSource(config);
   }
   ```

2. Test dangerous SQL blocked:
   ```java
   @Test
   void testDangerousSQL_shouldBlock() {
       assertThrows(SQLException.class, () -> {
           try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
               stmt.executeQuery("SELECT * FROM user");
           }
       });
   }
   ```

3. Test safe SQL proceeds:
   ```java
   @Test
   void testSafeSQL_shouldProceed() throws SQLException {
       try (Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM user WHERE id = ?")) {
           ps.setLong(1, 1L);
           ps.executeQuery();
       }
   }
   ```

**Test Requirements**:
- `HikariIntegrationTest.java` (15 tests):
  - testSetup_hikariDataSource_shouldCreate()
  - testProxyFactory_configured_shouldExist()
  - testGetConnection_shouldReturnProxy()
  - testPreparedStatement_safe_shouldExecute()
  - testPreparedStatement_dangerous_shouldBlock()
  - testStatement_dangerous_shouldBlock()
  - testStatement_safe_shouldExecute()
  - testBatchOperations_shouldValidate()
  - testConnectionPool_multipleConnections()
  - testConcurrent_100threads_shouldBeThreadSafe()
  - testLeakDetection_intentionalLeak_shouldDetect()
  - testWARNStrategy_shouldLogAndContinue()
  - testLOGStrategy_shouldOnlyLog()
  - testDeduplication_shouldWork()
  - testMetrics_shouldBeAccurate()

**Files to Create**:
- `sql-guard-jdbc/src/test/resources/schema.sql`
- `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/hikari/HikariIntegrationTest.java`

---

## Expected Outcomes

### Functional Outcomes:
1. ✅ HikariCP ProxyFactory intercepts SQL at JDBC layer
2. ✅ PreparedStatement validated at prepare time
3. ✅ Statement validated at execute time
4. ✅ Two-layer proxy (Connection + Statement)
5. ✅ Leak detection preserved
6. ✅ HikariCP metrics unaffected

### Test Outcomes:
- Total new tests: **74 tests** + JMH benchmark
- Integration tests with HikariDataSource
- Performance overhead measured
- Edge cases covered

### Architecture Outcomes:
- ✅ High-performance JDBC interception
- ✅ Connection pool lifecycle compatibility
- ✅ Minimal overhead (<1% connection, <5% execution)

## Validation Criteria

### Must Pass Before Completion:
1. All 74 tests passing (100% pass rate)
2. HikariDataSource integration working
3. Leak detection working with proxy
4. Performance benchmark <1% connection, <5% execution
5. PreparedStatement interception working
6. Statement interception working
7. Batch operations validated

### Performance Benchmarks:
1. Connection acquisition: <1% overhead
2. SQL execution: <5% overhead
3. Target: Microsecond-level proxy latency

### Code Quality:
1. Google Java Style compliance
2. Comprehensive Javadoc
3. SLF4J logging

## Success Metrics

- ✅ 74 tests passing (100%)
- ✅ HikariDataSource integration working
- ✅ Performance target met (<1% connection, <5% execution)
- ✅ Leak detection preserved
- ✅ Metrics accurate

## Timeline Estimate
- Step 1: 1 hour (ProxyFactory + 10 tests)
- Step 2: 1.5 hours (ConnectionInvocationHandler + 12 tests)
- Step 3: 1.5 hours (StatementInvocationHandler + 15 tests)
- Step 4: 1 hour (Configuration + 10 tests)
- Step 5: 2 hours (Edge cases + Performance + 12 tests + JMH)
- Step 6: 2 hours (Integration + 15 tests)

**Total**: ~9 hours

## Definition of Done

- [ ] All 74 tests passing
- [ ] HikariDataSource integration working
- [ ] ProxyFactory configured correctly
- [ ] Connection proxy working
- [ ] Statement proxy working
- [ ] Leak detection preserved
- [ ] Performance <1% connection, <5% execution
- [ ] Batch operations validated
- [ ] Memory Log created
- [ ] Ready for P6Spy (Task 4.5)

---

**End of Task Assignment**

HikariCP proxy provides high-performance JDBC-layer interception with minimal overhead, preserving HikariCP's microsecond-level connection acquisition performance.
