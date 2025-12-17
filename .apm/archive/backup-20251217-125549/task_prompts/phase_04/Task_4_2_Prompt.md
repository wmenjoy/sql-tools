---
agent: Agent_Runtime_Interceptor
task_ref: Task 4.2
depends_on:
  - Task 2.13 (DefaultSqlSafetyValidator Assembly)
ad_hoc_delegation: false
---

# Task 4.2 - MyBatis-Plus InnerInterceptor Implementation

## Objective
Implement MyBatis-Plus InnerInterceptor integrating with MyBatis-Plus interceptor chain to validate SQL at runtime, detecting IPage pagination parameters for physical pagination validation, capturing QueryWrapper/LambdaQueryWrapper generated SQL flagged by static scanner, and coordinating with PaginationInnerInterceptor without conflicts.

## Context

**MyBatis-Plus Integration**: MyBatis-Plus provides InnerInterceptor interface for modular interception. Unlike MyBatis's monolithic Interceptor, InnerInterceptor enables multiple specialized interceptors to coexist in a chain.

**Key Responsibilities**:
- Detect IPage pagination (MyBatis-Plus pagination framework)
- Validate QueryWrapper/LambdaQueryWrapper generated SQL
- Coordinate with PaginationInnerInterceptor (both must work together)
- Use deduplication filter to prevent double-checking with MyBatis interceptor

**QueryWrapper Runtime Validation**: Static scanner (Task 3.4) only marks QueryWrapper usage locations. Runtime validation catches actual generated SQL from fluent API.

## Dependencies

### Input from Task 2.13 (Phase 2):
- DefaultSqlSafetyValidator (complete validation engine)
- SqlDeduplicationFilter (prevents double validation)
- ViolationStrategy (BLOCK/WARN/LOG)

### Independence:
- Task 4.2 is independent of Task 4.1
- Both can be executed in parallel
- Can coexist in same application (MyBatis + MyBatis-Plus mixed usage)

## Implementation Steps

### Step 1: InnerInterceptor TDD
**Goal**: Create MyBatis-Plus InnerInterceptor

**Tasks**:
1. Add MyBatis-Plus dependencies to `sql-guard-mp/pom.xml`:
   ```xml
   <dependency>
       <groupId>com.baomidou</groupId>
       <artifactId>mybatis-plus-core</artifactId>
       <version>3.5.3</version>
       <scope>provided</scope>
   </dependency>
   <!-- Profile for 3.4.0 testing -->
   ```

2. Create `MpSqlSafetyInnerInterceptor` class:
   ```java
   public class MpSqlSafetyInnerInterceptor implements InnerInterceptor {
       private final DefaultSqlSafetyValidator validator;
       private final ViolationStrategy strategy;

       @Override
       public void beforeQuery(Executor executor, MappedStatement ms,
                               Object parameter, RowBounds rowBounds,
                               ResultHandler resultHandler, BoundSql boundSql) {
           validateSql(ms, parameter, boundSql, rowBounds);
       }

       @Override
       public void beforeUpdate(Executor executor, MappedStatement ms,
                                Object parameter) {
           BoundSql boundSql = ms.getBoundSql(parameter);
           validateSql(ms, parameter, boundSql, null);
       }
   }
   ```

**Test Requirements**:
- `MpSqlSafetyInnerInterceptorTest.java` (10 tests):
  - testBeforeQuery_shouldValidate()
  - testBeforeUpdate_shouldValidate()
  - testIPageDetection_shouldExtract()
  - testQueryWrapperSqlCapture_shouldValidate()
  - testLambdaQueryWrapperSqlCapture_shouldValidate()
  - testViolationHandling_BLOCK_shouldThrow()
  - testViolationHandling_WARN_shouldLog()
  - testViolationHandling_LOG_shouldLog()
  - testValidSql_shouldProceed()
  - testDeduplication_shouldPreventDoubleCheck()

**Files to Create**:
- `sql-guard-mp/src/main/java/com/footstone/sqlguard/interceptor/mp/MpSqlSafetyInnerInterceptor.java`
- `sql-guard-mp/src/test/java/com/footstone/sqlguard/interceptor/mp/MpSqlSafetyInnerInterceptorTest.java`

---

### Step 2: beforeQuery/beforeUpdate Implementation
**Goal**: Extract SqlContext from MyBatis-Plus execution

**Tasks**:
1. Implement `validateSql()` method:
   ```java
   private void validateSql(MappedStatement ms, Object parameter,
                            BoundSql boundSql, RowBounds rowBounds) {
       String sql = boundSql.getSql();

       // Deduplication check
       if (!shouldValidate(sql)) {
           return;
       }

       // Build SqlContext
       SqlContext context = SqlContext.builder()
           .sql(sql)
           .type(ms.getSqlCommandType())
           .mapperId(ms.getId())
           .rowBounds(rowBounds)
           .params(extractParameters(parameter))
           .build();

       // Detect IPage
       if (hasIPageParameter(parameter)) {
           IPage<?> page = extractIPage(parameter);
           context.setPageInfo(page.getCurrent(), page.getSize());
       }

       // Detect QueryWrapper
       if (hasQueryWrapper(parameter)) {
           context.setQueryWrapperUsed(true);
       }

       // Validate
       ValidationResult result = validator.validate(context);

       // Handle violations
       if (!result.passed()) {
           handleViolation(result, ms.getId());
       }
   }
   ```

2. Implement IPage detection:
   ```java
   private boolean hasIPageParameter(Object parameter) {
       if (parameter instanceof IPage) {
           return true;
       }
       if (parameter instanceof Map) {
           Map<?, ?> paramMap = (Map<?, ?>) parameter;
           return paramMap.values().stream()
               .anyMatch(v -> v instanceof IPage);
       }
       return false;
   }

   private IPage<?> extractIPage(Object parameter) {
       if (parameter instanceof IPage) {
           return (IPage<?>) parameter;
       }
       if (parameter instanceof Map) {
           Map<?, ?> paramMap = (Map<?, ?>) parameter;
           return (IPage<?>) paramMap.values().stream()
               .filter(v -> v instanceof IPage)
               .findFirst()
               .orElse(null);
       }
       return null;
   }
   ```

3. Implement QueryWrapper detection:
   ```java
   private boolean hasQueryWrapper(Object parameter) {
       if (parameter instanceof AbstractWrapper) {
           return true;
       }
       if (parameter instanceof Map) {
           Map<?, ?> paramMap = (Map<?, ?>) parameter;
           return paramMap.values().stream()
               .anyMatch(v -> v instanceof AbstractWrapper);
       }
       return false;
   }
   ```

**Test Requirements**:
- `BeforeQueryUpdateTest.java` (12 tests):
  - testValidateSql_shouldExtractContext()
  - testIPageDetection_direct_shouldExtract()
  - testIPageDetection_inMap_shouldExtract()
  - testIPageDetails_current_shouldExtract()
  - testIPageDetails_size_shouldExtract()
  - testQueryWrapperDetection_shouldSet Flag()
  - testLambdaQueryWrapperDetection_shouldSetFlag()
  - testUpdateWrapperDetection_shouldSetFlag()
  - testNoIPage_shouldNotSetPageInfo()
  - testNoWrapper_shouldNotSetFlag()
  - testDeduplication_sameSQL_shouldSkipSecond()
  - testParameterExtraction_shouldHandleComplexTypes()

**Files to Modify**:
- `MpSqlSafetyInnerInterceptor.java`

**Files to Create**:
- `sql-guard-mp/src/test/java/com/footstone/sqlguard/interceptor/mp/BeforeQueryUpdateTest.java`

---

### Step 3: Integration with PaginationInnerInterceptor
**Goal**: Ensure both interceptors coexist correctly

**Tasks**:
1. Understand interceptor chain ordering:
   ```java
   // MybatisPlusInterceptor executes interceptors in order
   MybatisPlusInterceptor mpInterceptor = new MybatisPlusInterceptor();

   // Order matters:
   // 1. PaginationInnerInterceptor adds LIMIT clause
   mpInterceptor.addInnerInterceptor(new PaginationInnerInterceptor());

   // 2. MpSqlSafetyInnerInterceptor validates final SQL (with LIMIT)
   mpInterceptor.addInnerInterceptor(new MpSqlSafetyInnerInterceptor(validator));
   ```

2. Test coordination scenarios:
   - IPage query without WHERE → PaginationInnerInterceptor adds LIMIT → SafetyInterceptor still detects no WHERE
   - IPage query with WHERE → Both interceptors process correctly
   - Non-IPage query → PaginationInnerInterceptor skips → SafetyInterceptor validates

**Test Requirements**:
- `MpInterceptorCoordinationTest.java` (10 tests):
  - testBothInterceptors_configured_shouldExist()
  - testPaginationFirst_safetySecond_correctOrder()
  - testIPageQuery_pagination_addsLimit()
  - testIPageQuery_safety_validatesWithLimit()
  - testNoWhereIPage_pagination_addsLimit_safety_stillViolates()
  - testValidWhereIPage_bothPass()
  - testNonIPageQuery_paginationSkips_safetyValidates()
  - testQueryWrapper_empty_equivalentToNoWhere()
  - testMultipleInterceptors_allExecute()
  - testInterceptorOrder_affectsResults()

**Files to Create**:
- `sql-guard-mp/src/test/java/com/footstone/sqlguard/interceptor/mp/MpInterceptorCoordinationTest.java`

---

### Step 4: QueryWrapper SQL Validation
**Goal**: Validate fluent API generated SQL

**Tasks**:
1. Test QueryWrapper scenarios:
   ```java
   // Empty wrapper (dangerous - no WHERE)
   QueryWrapper<User> wrapper1 = new QueryWrapper<>();
   // Generates: SELECT * FROM user

   // Wrapper with conditions (safe)
   QueryWrapper<User> wrapper2 = new QueryWrapper<>();
   wrapper2.eq("id", 123);
   // Generates: SELECT * FROM user WHERE id = 123

   // LambdaQueryWrapper (type-safe)
   LambdaQueryWrapper<User> wrapper3 = new LambdaQueryWrapper<>();
   wrapper3.eq(User::getId, 123);
   // Generates: SELECT * FROM user WHERE id = 123
   ```

2. Validate generated SQL:
   - Empty wrapper triggers NoWhereClauseChecker
   - Blacklist field wrapper triggers BlacklistFieldChecker
   - Deep pagination with wrapper triggers DeepPaginationChecker

**Test Requirements**:
- `QueryWrapperValidationTest.java` (12 tests):
  - testEmptyQueryWrapper_shouldViolateNoWhere()
  - testQueryWrapperWithEq_shouldPass()
  - testQueryWrapperWithIn_shouldPass()
  - testQueryWrapperBlacklistField_shouldViolate()
  - testLambdaQueryWrapper_shouldValidate()
  - testUpdateWrapper_shouldValidate()
  - testLambdaUpdateWrapper_shouldValidate()
  - testComplexWrapper_multipleConditions_shouldPass()
  - testWrapperWithIPage_shouldDetectBoth()
  - testWrapperGeneratedSql_shouldMatchExpected()
  - testNestedWrapper_shouldValidate()
  - testDynamicWrapper_runtimeConditions_shouldCatch()

**Files to Create**:
- `sql-guard-mp/src/test/java/com/footstone/sqlguard/interceptor/mp/QueryWrapperValidationTest.java`

---

### Step 5: Edge Cases and Plugin Compatibility
**Goal**: Test with MyBatis-Plus ecosystem plugins

**Tasks**:
1. Test with OptimisticLockerInnerInterceptor:
   ```java
   MybatisPlusInterceptor mpInterceptor = new MybatisPlusInterceptor();
   mpInterceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
   mpInterceptor.addInnerInterceptor(new MpSqlSafetyInnerInterceptor(validator));

   // Update with version field
   // Optimistic lock adds: WHERE version = ? AND other_conditions
   ```

2. Test with TenantLineInnerInterceptor:
   ```java
   mpInterceptor.addInnerInterceptor(new TenantLineInnerInterceptor(...));
   mpInterceptor.addInnerInterceptor(new MpSqlSafetyInnerInterceptor(validator));

   // Tenant plugin adds: AND tenant_id = ?
   ```

3. Test deduplication with MyBatis interceptor:
   - Enable both MyBatis SqlSafetyInterceptor (Task 4.1) and MyBatis-Plus MpSqlSafetyInnerInterceptor
   - Execute SQL
   - Verify deduplication filter prevents double validation
   - Verify only one validation per SQL

**Test Requirements**:
- `MpPluginCompatibilityTest.java` (10 tests):
  - testOptimisticLock_withSafety_shouldCoexist()
  - testOptimisticLock_versionField_shouldNotInterfere()
  - testTenantLine_withSafety_shouldCoexist()
  - testTenantLine_tenantId_shouldNotInterfere()
  - testIllegalSQL_withSafety_bothPlugins_shouldWork()
  - testMultiplePlugins_executionOrder_correct()
  - testMyBatisPlusBatchOperations_shouldValidate()
  - testLogicDelete_withSafety_shouldCoexist()
  - testDynamicTableName_withSafety_shouldValidate()
  - testBlockAttackInnerInterceptor_withSafety_bothWork()

- `MyBatisPlusDeduplicationTest.java` (8 tests):
  - testBothInterceptors_enabled_shouldNotDoubleValidate()
  - testDeduplicationFilter_cacheKey_shouldMatch()
  - testDeduplicationFilter_withinTTL_shouldSkip()
  - testDeduplicationFilter_afterTTL_shouldValidate()
  - testDifferentSQL_shouldValidateBoth()
  - testSameSQL_differentContext_shouldValidateBoth()
  - testThreadLocal_cache_shouldIsolateThreads()
  - testClearCache_shouldRevalidate()

**Files to Create**:
- `sql-guard-mp/src/test/java/com/footstone/sqlguard/interceptor/mp/MpPluginCompatibilityTest.java`
- `sql-guard-mp/src/test/java/com/footstone/sqlguard/interceptor/mp/MyBatisPlusDeduplicationTest.java`

---

### Step 6: Integration Testing with Real MyBatis-Plus
**Goal**: Full integration with MyBatis-Plus BaseMapper

**Tasks**:
1. Create test entity and mapper:
   ```java
   @TableName("user")
   public class User {
       @TableId
       private Long id;
       private String name;
       private Integer age;
       @TableLogic
       private Integer deleted;
   }

   public interface UserMapper extends BaseMapper<User> {
       // BaseMapper provides: selectById, selectList, insert, update, delete
   }
   ```

2. Test BaseMapper methods with interceptor:
   ```java
   @Test
   void testSelectList_emptyWrapper_shouldViolate() {
       assertThrows(SQLException.class, () -> {
           userMapper.selectList(new QueryWrapper<>());
           // Generates: SELECT * FROM user (no WHERE)
       });
   }

   @Test
   void testSelectList_withConditions_shouldPass() {
       QueryWrapper<User> wrapper = new QueryWrapper<>();
       wrapper.eq("id", 1L);

       List<User> users = userMapper.selectList(wrapper);
       // Generates: SELECT * FROM user WHERE id = 1
   }
   ```

3. Test IPage pagination:
   ```java
   @Test
   void testSelectPage_shouldDetectIPage() {
       Page<User> page = new Page<>(1, 10);  // IPage implementation
       QueryWrapper<User> wrapper = new QueryWrapper<>();
       wrapper.gt("age", 18);

       Page<User> result = userMapper.selectPage(page, wrapper);
       // Generates: SELECT * FROM user WHERE age > 18 LIMIT 10
   }
   ```

**Test Requirements**:
- `MpSqlSafetyInnerInterceptorIntegrationTest.java` (15 tests):
  - testSetup_mybatisPlusConfig_shouldCreate()
  - testBaseMapper_selectById_shouldValidate()
  - testBaseMapper_selectList_emptyWrapper_shouldViolate()
  - testBaseMapper_selectList_withWrapper_shouldPass()
  - testBaseMapper_insert_shouldValidate()
  - testBaseMapper_update_noWrapper_shouldViolate()
  - testBaseMapper_update_withWrapper_shouldPass()
  - testBaseMapper_delete_noWrapper_shouldViolate()
  - testIPage_pagination_shouldDetect()
  - testIPage_withPaginationInterceptor_shouldAddLimit()
  - testLambdaQueryWrapper_typeSafe_shouldValidate()
  - testLogicDelete_shouldAddDeletedCondition()
  - testConcurrentExecution_shouldBeThreadSafe()
  - testBatchOperations_shouldValidateEach()
  - testComplexQuery_multiplePlugins_shouldWork()

**Files to Create**:
- `sql-guard-mp/src/test/resources/schema.sql`
- `sql-guard-mp/src/test/java/com/footstone/sqlguard/interceptor/mp/MpSqlSafetyInnerInterceptorIntegrationTest.java`
- `sql-guard-mp/src/test/java/com/footstone/sqlguard/interceptor/mp/User.java`
- `sql-guard-mp/src/test/java/com/footstone/sqlguard/interceptor/mp/UserMapper.java`

---

## Expected Outcomes

### Functional Outcomes:
1. ✅ MyBatis-Plus InnerInterceptor integrated into interceptor chain
2. ✅ IPage pagination detected for physical pagination validation
3. ✅ QueryWrapper/LambdaQueryWrapper generated SQL validated
4. ✅ Coordinates with PaginationInnerInterceptor (both coexist)
5. ✅ Deduplication prevents double validation with MyBatis interceptor
6. ✅ Compatible with MyBatis-Plus ecosystem plugins

### Test Outcomes:
- Total new tests: **77 tests** across 7 test classes
- Integration tests with BaseMapper
- Plugin compatibility verified (OptimisticLock, TenantLine, etc.)
- Deduplication with MyBatis interceptor verified

### Architecture Outcomes:
- ✅ MyBatis-Plus runtime defense layer complete
- ✅ QueryWrapper runtime validation (complements static scanner Task 3.4)
- ✅ IPage detection for physical pagination rules
- ✅ Multi-layer deduplication (MyBatis + MyBatis-Plus)

## Validation Criteria

### Must Pass Before Completion:
1. All 77 new tests passing (100% pass rate)
2. Integration with BaseMapper working
3. PaginationInnerInterceptor coordination verified
4. Deduplication with MyBatis interceptor verified
5. MyBatis-Plus 3.4.0 compatibility verified
6. MyBatis-Plus 3.5.3 compatibility verified
7. QueryWrapper empty detection working

### Performance Benchmarks:
1. Interception overhead: <5%
2. IPage detection: <1ms
3. QueryWrapper detection: <1ms

### Code Quality:
1. Google Java Style compliance
2. Comprehensive Javadoc
3. SLF4J logging

## Success Metrics

- ✅ 77 tests passing (100%)
- ✅ BaseMapper methods validated
- ✅ IPage pagination detected
- ✅ QueryWrapper SQL validated
- ✅ Plugin compatibility verified
- ✅ Deduplication working

## Timeline Estimate
- Step 1: 1 hour (InnerInterceptor setup + 10 tests)
- Step 2: 1.5 hours (beforeQuery/Update + 12 tests)
- Step 3: 1 hour (PaginationInnerInterceptor + 10 tests)
- Step 4: 1 hour (QueryWrapper validation + 12 tests)
- Step 5: 1.5 hours (Plugin compatibility + 18 tests)
- Step 6: 2 hours (Integration + 15 tests)

**Total**: ~8 hours

## Definition of Done

- [ ] All 77 tests passing
- [ ] BaseMapper integration working
- [ ] IPage detection working
- [ ] QueryWrapper validation working
- [ ] PaginationInnerInterceptor coordination verified
- [ ] Deduplication with MyBatis verified
- [ ] Plugin compatibility verified
- [ ] Memory Log created
- [ ] Ready for JDBC layer (Tasks 4.3-4.5)

---

**End of Task Assignment**
