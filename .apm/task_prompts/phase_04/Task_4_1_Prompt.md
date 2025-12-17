---
agent: Agent_Runtime_Interceptor
task_ref: Task 4.1
depends_on:
  - Task 2.13 (DefaultSqlSafetyValidator Assembly)
ad_hoc_delegation: false
---

# Task 4.1 - MyBatis Interceptor Implementation

## Objective
Implement production-ready MyBatis plugin intercepting Executor.update and Executor.query methods to validate SQL at runtime after dynamic SQL resolution, extracting final SQL from BoundSql, detecting RowBounds for logical pagination, and enforcing configured violation strategies (BLOCK/WARN/LOG) supporting both MyBatis 3.4.x and 3.5.x versions.

## Context

**Runtime Validation Layer**: This is the first runtime defense layer validating SQL after MyBatis dynamic tag resolution but before database execution. This catches dangerous patterns missed by static analysis where conditions are determined at runtime.

**Key Capabilities**:
- Intercept after dynamic SQL processing (if/where/foreach tags resolved)
- Extract final executable SQL from BoundSql
- Detect RowBounds for logical pagination validation
- Link violations to source code via MapperId
- Support multi-version MyBatis (3.4.x and 3.5.x API differences)

## Dependencies

### Input from Task 2.13 (Phase 2):
```java
// Complete validation engine with 10 rule checkers
public class DefaultSqlSafetyValidator implements SqlSafetyValidator {
    public ValidationResult validate(SqlContext context);
}

// SQL deduplication for multi-layer interception
public class SqlDeduplicationFilter {
    public boolean shouldValidate(String sql);
}

// Violation strategy configuration
public enum ViolationStrategy {
    BLOCK,  // Throw SQLException, prevent execution
    WARN,   // Log error, continue execution
    LOG     // Log warning, continue execution
}
```

## Implementation Steps

### Step 1: MyBatis Interceptor TDD
**Goal**: Create interceptor with proper @Intercepts annotations

**Tasks**:
1. Add MyBatis dependencies to `sql-guard-mybatis/pom.xml`:
   ```xml
   <dependency>
       <groupId>org.mybatis</groupId>
       <artifactId>mybatis</artifactId>
       <version>3.5.13</version>
       <scope>provided</scope>
   </dependency>
   <!-- Profile for 3.4.6 testing -->
   ```

2. Create `SqlSafetyInterceptor` class in `com.footstone.sqlguard.interceptor.mybatis` package:
   ```java
   @Intercepts({
       @Signature(
           type = Executor.class,
           method = "update",
           args = {MappedStatement.class, Object.class}
       ),
       @Signature(
           type = Executor.class,
           method = "query",
           args = {MappedStatement.class, Object.class,
                   RowBounds.class, ResultHandler.class}
       )
   })
   public class SqlSafetyInterceptor implements Interceptor {
       private final DefaultSqlSafetyValidator validator;
       private final ViolationStrategy strategy;

       // Constructor, intercept(), plugin(), setProperties()
   }
   ```

3. Implement Interceptor interface methods:
   - `intercept(Invocation invocation)` - main interception logic
   - `plugin(Object target)` - wrap executor (use Plugin.wrap())
   - `setProperties(Properties properties)` - load config

**Test Requirements**:
- `SqlSafetyInterceptorTest.java` (12 tests):
  - testQueryInterception_shouldValidate() - SELECT intercepted
  - testUpdateInterception_shouldValidate() - UPDATE/DELETE intercepted
  - testInsertInterception_shouldValidate() - INSERT intercepted
  - testRowBoundsDetection_shouldExtract() - RowBounds parameter captured
  - testBoundSqlExtraction_shouldGetResolvedSql() - Dynamic SQL resolved
  - testMapperIdExtraction_shouldGetMapperId() - MappedStatement.getId() captured
  - testBLOCKStrategy_shouldThrowException() - SQLException thrown
  - testWARNStrategy_shouldLogAndContinue() - Logs error, proceeds
  - testLOGStrategy_shouldOnlyLog() - Logs warning only
  - testValidSql_shouldProceed() - No violations, continues
  - testPluginWrap_shouldWrapExecutor() - Plugin.wrap() works
  - testSetProperties_shouldLoadConfig() - Properties loaded

**Files to Create**:
- `sql-guard-mybatis/src/main/java/com/footstone/sqlguard/interceptor/mybatis/SqlSafetyInterceptor.java`
- `sql-guard-mybatis/src/test/java/com/footstone/sqlguard/interceptor/mybatis/SqlSafetyInterceptorTest.java`

---

### Step 2: Intercept Method Implementation
**Goal**: Extract execution context from Invocation

**Tasks**:
1. Implement `intercept(Invocation invocation)` method:
   ```java
   @Override
   public Object intercept(Invocation invocation) throws Throwable {
       // 1. Extract execution context
       Object[] args = invocation.getArgs();
       MappedStatement ms = (MappedStatement) args[0];
       Object parameter = args[1];

       // 2. Get RowBounds (query only)
       RowBounds rowBounds = null;
       if (args.length >= 3 && args[2] instanceof RowBounds) {
           rowBounds = (RowBounds) args[2];
       }

       // 3. Get BoundSql (resolved dynamic SQL)
       BoundSql boundSql = ms.getBoundSql(parameter);
       String sql = boundSql.getSql();

       // 4. Build SqlContext
       SqlContext context = buildSqlContext(ms, sql, parameter, rowBounds);

       // 5. Validate
       ValidationResult result = validator.validate(context);

       // 6. Handle violations
       if (!result.passed()) {
           handleViolation(result, ms.getId());
       }

       // 7. Proceed with execution
       return invocation.proceed();
   }
   ```

2. Implement `buildSqlContext()` helper:
   ```java
   private SqlContext buildSqlContext(MappedStatement ms, String sql,
                                       Object parameter, RowBounds rowBounds) {
       return SqlContext.builder()
           .sql(sql)
           .type(ms.getSqlCommandType())
           .mapperId(ms.getId())
           .rowBounds(rowBounds)
           .params(extractParameters(parameter))
           .build();
   }
   ```

3. Implement `extractParameters()` for parameter map extraction:
   - Handle single parameter
   - Handle @Param annotated parameters (Map)
   - Handle POJO parameters

**Test Requirements**:
- `InterceptMethodTest.java` (10 tests):
  - testExtractMappedStatement_shouldGet() - MappedStatement extracted
  - testExtractParameter_shouldHandleSingle() - Single param
  - testExtractParameter_shouldHandleMap() - @Param map
  - testExtractParameter_shouldHandlePojo() - POJO param
  - testExtractRowBounds_query_shouldGet() - RowBounds from query
  - testExtractRowBounds_update_shouldBeNull() - Update has no RowBounds
  - testGetBoundSql_shouldResolveDynamicTags() - <if> tags processed
  - testBuildSqlContext_shouldPopulateAllFields() - All fields present
  - testSqlCommandType_shouldExtract() - SELECT/UPDATE/DELETE/INSERT
  - testMapperId_shouldExtract() - Full qualified mapper method ID

**Files to Modify**:
- `SqlSafetyInterceptor.java`

**Files to Create**:
- `sql-guard-mybatis/src/test/java/com/footstone/sqlguard/interceptor/mybatis/InterceptMethodTest.java`

---

### Step 3: Violation Handling Strategies
**Goal**: Implement BLOCK/WARN/LOG strategy pattern

**Tasks**:
1. Implement `handleViolation(ValidationResult result, String mapperId)`:
   ```java
   private void handleViolation(ValidationResult result, String mapperId)
           throws SQLException {
       String violationMsg = formatViolations(result, mapperId);

       switch (strategy) {
           case BLOCK:
               logger.error("[BLOCK] SQL Safety Violation: {}", violationMsg);
               throw new SQLException(
                   "SQL Safety Violation (BLOCK): " + violationMsg,
                   "42000"  // SQLState: syntax error or access rule violation
               );

           case WARN:
               logger.error("[WARN] SQL Safety Violation: {}", violationMsg);
               break;  // Continue execution

           case LOG:
               logger.warn("[LOG] SQL Safety Violation: {}", violationMsg);
               break;  // Continue execution
       }
   }
   ```

2. Implement `formatViolations()` for detailed violation reporting:
   ```java
   private String formatViolations(ValidationResult result, String mapperId) {
       StringBuilder sb = new StringBuilder();
       sb.append("MapperId: ").append(mapperId).append("\n");
       sb.append("Risk Level: ").append(result.getRiskLevel()).append("\n");
       sb.append("Violations:\n");

       for (ViolationInfo violation : result.getViolations()) {
           sb.append("  - [").append(violation.getRiskLevel()).append("] ");
           sb.append(violation.getMessage()).append("\n");
           sb.append("    Suggestion: ").append(violation.getSuggestion()).append("\n");
       }

       return sb.toString();
   }
   ```

3. Configure strategy via constructor or properties:
   - Default: WARN (safe for production rollout)
   - Configurable via SqlGuardConfig

**Test Requirements**:
- `ViolationHandlingTest.java` (12 tests):
  - testBLOCKStrategy_withViolation_shouldThrowSQLException() - Exception thrown
  - testBLOCKStrategy_sqlState_shouldBe42000() - Correct SQLState
  - testBLOCKStrategy_message_shouldContainDetails() - Detailed message
  - testWARNStrategy_withViolation_shouldLogError() - Error logged
  - testWARNStrategy_shouldNotThrow() - No exception
  - testWARNStrategy_shouldProceed() - Execution continues
  - testLOGStrategy_withViolation_shouldLogWarn() - Warning logged
  - testLOGStrategy_shouldNotThrow() - No exception
  - testFormatViolations_shouldIncludeMapperId() - MapperId in message
  - testFormatViolations_shouldIncludeRiskLevel() - Risk level shown
  - testFormatViolations_shouldIncludeSuggestions() - Suggestions shown
  - testMultipleViolations_shouldFormatAll() - All violations listed

**Files to Modify**:
- `SqlSafetyInterceptor.java`

**Files to Create**:
- `sql-guard-mybatis/src/test/java/com/footstone/sqlguard/interceptor/mybatis/ViolationHandlingTest.java`

---

### Step 4: Multi-Version MyBatis Compatibility
**Goal**: Support both MyBatis 3.4.x and 3.5.x

**Tasks**:
1. Add Maven profiles to `sql-guard-mybatis/pom.xml`:
   ```xml
   <profiles>
       <profile>
           <id>mybatis-3.4</id>
           <properties>
               <mybatis.version>3.4.6</mybatis.version>
           </properties>
       </profile>
       <profile>
           <id>mybatis-3.5</id>
           <activation>
               <activeByDefault>true</activeByDefault>
           </activation>
           <properties>
               <mybatis.version>3.5.13</mybatis.version>
           </properties>
       </profile>
   </profiles>
   ```

2. Handle API differences:
   - MyBatis 3.4.x: Some parameter handling differences
   - MyBatis 3.5.x: Enhanced BoundSql API

   Use reflection for version-specific code:
   ```java
   private boolean isMyBatis35() {
       try {
           // Check for 3.5+ specific class/method
           Class.forName("org.apache.ibatis.cursor.Cursor");
           return true;
       } catch (ClassNotFoundException e) {
           return false;
       }
   }
   ```

3. Test both versions:
   - Run `mvn test -Pmybatis-3.4`
   - Run `mvn test -Pmybatis-3.5`

**Test Requirements**:
- `MyBatisVersionCompatibilityTest.java` (8 tests):
  - testMyBatis34_interceptor_shouldWork() - 3.4.6 compatibility
  - testMyBatis34_boundSql_shouldExtract() - BoundSql works
  - testMyBatis34_parameters_shouldExtract() - Param extraction works
  - testMyBatis35_interceptor_shouldWork() - 3.5.13 compatibility
  - testMyBatis35_boundSql_shouldExtract() - Enhanced API works
  - testMyBatis35_parameters_shouldExtract() - Param extraction works
  - testVersionDetection_shouldIdentifyCorrectly() - Version detected
  - testDynamicSql_bothVersions_shouldResolve() - <if>/<foreach> work

**Files to Modify**:
- `sql-guard-mybatis/pom.xml`
- `SqlSafetyInterceptor.java` (add version detection if needed)

**Files to Create**:
- `sql-guard-mybatis/src/test/java/com/footstone/sqlguard/interceptor/mybatis/MyBatisVersionCompatibilityTest.java`

---

### Step 5: Integration and Thread-Safety Testing
**Goal**: Real MyBatis integration with SqlSessionFactory

**Tasks**:
1. Create integration test with H2 database:
   ```java
   @BeforeEach
   void setup() {
       // Create H2 datasource
       DataSource dataSource = createH2DataSource();

       // Create MyBatis configuration
       Configuration config = new Configuration();
       config.addMapper(TestMapper.class);

       // Register interceptor
       SqlSafetyInterceptor interceptor = new SqlSafetyInterceptor(
           validator, ViolationStrategy.BLOCK
       );
       config.addInterceptor(interceptor);

       // Create SqlSessionFactory
       sqlSessionFactory = new SqlSessionFactoryBuilder()
           .build(config);
   }
   ```

2. Create test mapper with dangerous SQL:
   ```java
   public interface TestMapper {
       @Select("SELECT * FROM user")  // No WHERE - should violate
       List<User> findAll();

       @Select("SELECT * FROM user WHERE id = #{id}")
       User findById(Long id);

       @Update("UPDATE user SET name = #{name}")  // No WHERE - should violate
       int updateAll(String name);
   }
   ```

3. Test interception accuracy:
   - Dangerous SQL blocked (BLOCK strategy)
   - Safe SQL proceeds
   - Violations logged correctly

4. Test thread-safety:
   ```java
   @Test
   void testConcurrentExecution_shouldBeThreadSafe() {
       ExecutorService executor = Executors.newFixedThreadPool(10);

       List<Future<?>> futures = new ArrayList<>();
       for (int i = 0; i < 100; i++) {
           futures.add(executor.submit(() -> {
               try (SqlSession session = sqlSessionFactory.openSession()) {
                   TestMapper mapper = session.getMapper(TestMapper.class);
                   mapper.findById(1L);  // Safe SQL
               }
           }));
       }

       // Verify all complete without errors
       for (Future<?> future : futures) {
           future.get();
       }
   }
   ```

**Test Requirements**:
- `SqlSafetyInterceptorIntegrationTest.java` (15 tests):
  - testSetup_sqlSessionFactory_shouldCreate() - Factory created
  - testInterceptor_registered_shouldBePresent() - Interceptor in chain
  - testSafeQuery_shouldExecute() - Safe SQL proceeds
  - testDangerousQuery_BLOCK_shouldThrowException() - Blocked
  - testDangerousQuery_BLOCK_databaseUnchanged() - DB not modified
  - testDangerousUpdate_BLOCK_shouldThrowException() - Blocked
  - testDangerousUpdate_WARN_shouldExecute() - WARN proceeds
  - testDangerousUpdate_WARN_shouldLog() - WARN logged
  - testDynamicSql_withIf_shouldResolveAndValidate() - <if> resolved
  - testDynamicSql_withForeach_shouldResolveAndValidate() - <foreach> resolved
  - testRowBounds_shouldDetect() - Logical pagination detected
  - testMultipleInterceptors_shouldCoexist() - Works with other interceptors
  - testConcurrentExecution_shouldBeThreadSafe() - Thread-safe
  - testConcurrentViolations_shouldDetectAll() - All violations caught
  - testValidatorCache_shouldPreventDuplicateValidation() - Deduplication works

**Files to Create**:
- `sql-guard-mybatis/src/test/resources/schema.sql` (H2 test schema)
- `sql-guard-mybatis/src/test/java/com/footstone/sqlguard/interceptor/mybatis/SqlSafetyInterceptorIntegrationTest.java`
- `sql-guard-mybatis/src/test/java/com/footstone/sqlguard/interceptor/mybatis/TestMapper.java`

---

## Expected Outcomes

### Functional Outcomes:
1. ✅ MyBatis Executor methods intercepted before execution
2. ✅ BoundSql provides resolved SQL (dynamic tags processed)
3. ✅ RowBounds detected for logical pagination validation
4. ✅ BLOCK strategy prevents dangerous SQL execution
5. ✅ WARN/LOG strategies log violations and continue
6. ✅ Multi-version compatibility (3.4.x and 3.5.x)

### Test Outcomes:
- Total new tests: **57 tests** across 6 test classes
- Integration tests with real SqlSessionFactory
- Thread-safety verified under concurrent load
- Both MyBatis versions tested

### Performance Outcomes:
- Validation overhead: <5% per SQL execution
- Thread-safe shared validator instance
- Deduplication prevents double validation in multi-layer setup

### Architecture Outcomes:
- ✅ Runtime validation layer complete
- ✅ Links violations to source code (MapperId)
- ✅ Strategy pattern for environment-specific enforcement
- ✅ Ready for MyBatis-Plus integration (Task 4.2)

## Validation Criteria

### Must Pass Before Completion:
1. All 57 new tests passing (100% pass rate)
2. Integration tests with real MyBatis SqlSessionFactory passing
3. Both MyBatis 3.4.6 and 3.5.13 compatibility verified
4. Thread-safety test with 100 concurrent operations passing
5. BLOCK strategy successfully prevents execution
6. WARN/LOG strategies successfully continue execution
7. RowBounds detection working for logical pagination

### Performance Benchmarks:
1. Interception overhead: <5% per SQL execution
2. 100 concurrent SQL operations: no race conditions
3. Validator shared across threads: thread-safe

### Code Quality:
1. Google Java Style compliance (Checkstyle passing)
2. No compiler warnings
3. Comprehensive Javadoc for public API
4. SLF4J logging at appropriate levels (ERROR/WARN/INFO/DEBUG)

## Dependencies Required

### From Phase 2 (Already Available):
```xml
<!-- sql-guard-core module -->
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-core</artifactId>
    <version>${project.version}</version>
</dependency>
```

### MyBatis Dependencies:
```xml
<!-- MyBatis (provided scope - user supplies) -->
<dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis</artifactId>
    <version>${mybatis.version}</version>
    <scope>provided</scope>
</dependency>

<!-- Test dependencies -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.220</version>
    <scope>test</scope>
</dependency>
```

## File Structure Summary

### New Implementation Classes (1 file):
```
sql-guard-mybatis/src/main/java/com/footstone/sqlguard/interceptor/mybatis/
└── SqlSafetyInterceptor.java
```

### New Test Classes (6 files):
```
sql-guard-mybatis/src/test/java/com/footstone/sqlguard/interceptor/mybatis/
├── SqlSafetyInterceptorTest.java (12 tests)
├── InterceptMethodTest.java (10 tests)
├── ViolationHandlingTest.java (12 tests)
├── MyBatisVersionCompatibilityTest.java (8 tests)
├── SqlSafetyInterceptorIntegrationTest.java (15 tests)
└── TestMapper.java (test interface)
```

### Test Resources (1 file):
```
sql-guard-mybatis/src/test/resources/
└── schema.sql (H2 test schema)
```

## Implementation Notes

### TDD Approach:
1. Write tests first for each step
2. Run tests (expect failures)
3. Implement minimum code to pass tests
4. Refactor while keeping tests green
5. Verify zero regressions

### Logging Strategy:
```java
private static final Logger logger = LoggerFactory.getLogger(SqlSafetyInterceptor.class);

// BLOCK strategy
logger.error("[BLOCK] SQL Safety Violation: mapperId={}, violations={}", ...);

// WARN strategy
logger.error("[WARN] SQL Safety Violation: mapperId={}, violations={}", ...);

// LOG strategy
logger.warn("[LOG] SQL Safety Violation: mapperId={}, violations={}", ...);

// Debug
logger.debug("Intercepting SQL: mapperId={}, sql={}", ...);
```

### Error Handling:
- Validator exceptions: Log and fail-open (WARN/LOG) or fail-closed (BLOCK)
- BoundSql extraction errors: Log warning, skip validation
- Parameter extraction errors: Log debug, continue with empty params

## Success Metrics

### Immediate Success (Task 4.1 Completion):
- ✅ 57 new tests passing (100%)
- ✅ MyBatis 3.4.6 compatibility verified
- ✅ MyBatis 3.5.13 compatibility verified
- ✅ Integration tests with SqlSessionFactory passing
- ✅ Thread-safety verified
- ✅ Performance targets met (<5% overhead)

### Production Readiness:
- BLOCK strategy successfully prevents dangerous SQL
- WARN strategy provides non-blocking alerting for gradual rollout
- LOG strategy enables observation mode
- MapperId links violations to source code for developer action

## Timeline Estimate
- Step 1: 1 hour (Interceptor setup + 12 tests)
- Step 2: 1.5 hours (Context extraction + 10 tests)
- Step 3: 1 hour (Violation handling + 12 tests)
- Step 4: 1 hour (Multi-version support + 8 tests)
- Step 5: 2 hours (Integration + thread-safety + 15 tests)

**Total**: ~6.5 hours estimated implementation + testing time

## Agent Responsibilities

As Agent_Runtime_Interceptor, you are responsible for:
1. **Writing tests first** (TDD) for all 5 steps
2. **Implementing interceptor** to pass tests
3. **Multi-version testing** (MyBatis 3.4.x and 3.5.x)
4. **Integration testing** with real SqlSessionFactory
5. **Thread-safety verification** under concurrent load
6. **Creating Memory Log** at `.apm/Memory/Phase_04_Runtime_Interceptors/Task_4_1_MyBatis_Interceptor.md`
7. **Reporting completion** to Manager Agent with test results

## Definition of Done

- [ ] All 57 new tests passing (100% pass rate)
- [ ] MyBatis 3.4.6 tests passing (`mvn test -Pmybatis-3.4`)
- [ ] MyBatis 3.5.13 tests passing (`mvn test -Pmybatis-3.5`)
- [ ] Integration test with H2 database passing
- [ ] Thread-safety test (100 concurrent operations) passing
- [ ] BLOCK strategy prevents SQL execution (SQLException thrown)
- [ ] WARN/LOG strategies allow execution (violations logged)
- [ ] RowBounds detection working for logical pagination
- [ ] Performance benchmark met (<5% overhead)
- [ ] Google Java Style compliance (Checkstyle passing)
- [ ] Memory Log created and comprehensive
- [ ] Ready for Task 4.2 (MyBatis-Plus integration)

---

**End of Task Assignment**

This interceptor provides the first runtime defense layer, validating SQL after MyBatis dynamic tag resolution. Combined with static scanning (Phase 3), it completes the dual-layer defense architecture.
