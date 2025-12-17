---
task_ref: "Task 2.13 - DefaultSqlSafetyValidator Assembly"
agent_assignment: "Agent_Core_Engine_Validation"
memory_log_path: ".apm/Memory/Phase_02_Validation_Engine/Task_2_13_DefaultSqlSafetyValidator_Assembly.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: DefaultSqlSafetyValidator Assembly

## Task Reference
Implementation Plan: **Task 2.13 - DefaultSqlSafetyValidator Assembly** assigned to **Agent_Core_Engine_Validation**

## Context from Dependencies
Based on your Phase 2 completed work:

**Rule Checker Framework (Task 2.1):**
- Use `RuleCheckerOrchestrator` from `com.footstone.sqlguard.validator.rule` for checker coordination
- Interface `RuleChecker` defines the contract for all checkers
- `AbstractRuleChecker` provides shared utilities (extractWhere, extractTableName, extractFields, etc.)

**All Rule Checkers (Tasks 2.2-2.12) - 10 total checkers:**
1. `NoWhereClauseChecker` (Task 2.2) - CRITICAL violation for missing WHERE
2. `DummyConditionChecker` (Task 2.3) - HIGH violation for "1=1", "true" patterns
3. `BlacklistFieldChecker` (Task 2.4) - HIGH violation for blacklist-only WHERE
4. `WhitelistFieldChecker` (Task 2.5) - HIGH violation for missing whitelist fields
5. `LogicalPaginationChecker` (Task 2.7) - CRITICAL violation for RowBounds/IPage without plugin
6. `NoConditionPaginationChecker` (Task 2.8) - CRITICAL violation for pagination without WHERE
7. `DeepPaginationChecker` (Task 2.9) - MEDIUM violation for high OFFSET values
8. `LargePageSizeChecker` (Task 2.10) - MEDIUM violation for excessive pageSize
9. `MissingOrderByChecker` (Task 2.11) - LOW violation for pagination without ORDER BY
10. `NoPaginationChecker` (Task 2.12) - Variable risk (CRITICAL/HIGH/MEDIUM) based on WHERE clause

**JSqlParser Facade (Phase 1 Task 1.4):**
- Use `JSqlParserFacade` from `com.footstone.sqlguard.parser` for SQL parsing
- Facade provides `parse(String sql)` returning Statement AST
- LRU cache integrated for performance optimization
- Supports fail-fast and lenient error handling modes

**Critical Config Architecture Note:**
- Checker constructors require config classes from `com.footstone.sqlguard.validator.rule.impl` package (extends CheckerConfig)
- DO NOT use config classes from `com.footstone.sqlguard.config` package (those are YAML POJOs)
- See `sql-guard-core/docs/Dual-Config-Pattern.md` for architecture details

## Objective
Assemble complete validation engine coordinating all rule checkers (Tasks 2.2-2.12), implementing parse-once SQL parsing with JSqlParser facade, SQL deduplication filter preventing redundant validation, and end-to-end integration with performance benchmarking achieving <5% overhead target.

## Detailed Instructions
Complete this task in **5 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Interface Implementation TDD

**Test First:**
Write test class `DefaultSqlSafetyValidatorTest` in `com.footstone.sqlguard.validator` package with test method `testValidateInterface()` covering:
- `validate(SqlContext)` returns `ValidationResult`
- Result contains violations from enabled checkers
- `result.passed = false` when violations present
- Deduplication filter integration (covered in Step 2)

**Then Implement:**
1. Create `SqlSafetyValidator` interface in `com.footstone.sqlguard.validator` package:
   - Method: `ValidationResult validate(SqlContext context)`
   - This is the public contract used by interceptors in Phase 4

2. Create `DefaultSqlSafetyValidator` class in same package implementing `SqlSafetyValidator`:
   - Constructor parameters:
     - `JSqlParserFacade facade` (from Phase 1 Task 1.4)
     - `List<RuleChecker> checkers` (all 10 checkers from Tasks 2.2-2.12)
     - `RuleCheckerOrchestrator orchestrator` (from Task 2.1)
     - `SqlDeduplicationFilter deduplicationFilter` (implement in Step 2)
   - Store all as final fields
   - Implement `validate()` method skeleton (will complete in Steps 2-4)

**Constraints:**
- Interface provides clean API for Phase 4 interceptor integration
- Constructor injection enables flexible checker configuration
- All fields final for thread-safety

### Step 2: Deduplication Filter TDD

**Test First:**
Write test class `SqlDeduplicationFilterTest` in `com.footstone.sqlguard.validator` package with test methods:
- `testFirstCheck_shouldAllow()` - First SQL check returns true
- `testSameSQLWithinTTL_shouldSkip()` - Same SQL within 100ms TTL returns false (cached)
- `testSameSQLAfterTTL_shouldAllow()` - Same SQL after TTL expires returns true
- `testDifferentSQL_shouldAllow()` - Different SQL always returns true
- `testClearThreadCache_shouldClearCache()` - Static cleanup method works
- `testThreadIsolation_shouldIsolatePerThread()` - Different threads have separate caches

**Then Implement:**
Create `SqlDeduplicationFilter` class in `com.footstone.sqlguard.validator` package with:

1. **Field:**
   - `ThreadLocal<LRUCache<String, Long>> cache` - ThreadLocal LRU cache mapping SQL hash → last check timestamp
   - `DeduplicationConfig config` - holds cacheSize (default 1000) and ttlMs (default 100)

2. **LRU Cache Implementation:**
   - Create inner class `LRUCache<K, V>` extending `LinkedHashMap<K, V>`:
     - Constructor with `int maxSize` parameter
     - Override `removeEldestEntry()` to return `size() > maxSize`
     - Use `accessOrder=true` for LRU behavior

3. **Method: `boolean shouldCheck(String sql)`**
   - Normalize SQL: `String key = sql.trim().toLowerCase()`
   - Get cache from ThreadLocal (initialize if null)
   - Check `cache.get(key)`:
     - If null → put(key, currentTimeMillis), return true (first check)
     - If exists and `(currentTimeMillis - cachedTime) < config.getTtlMs()` → return false (skip, recently checked)
     - If exists and TTL expired → put(key, currentTimeMillis), return true (re-check)

4. **Method: `static void clearThreadCache()`**
   - Call `ThreadLocal.remove()` for cleanup (important for thread pool scenarios)

**Constraints:**
- ThreadLocal ensures thread-safety without synchronization
- LRU cache prevents unbounded memory growth
- TTL-based expiration allows periodic re-validation

### Step 3: Parse-Once Integration TDD

**Test First:**
Write test class `ParseOnceIntegrationTest` in `com.footstone.sqlguard.validator` package covering:
- `parsedSql` null in context triggers `facade.parse()` call
- `parsedSql` set in context after parse
- All checkers receive same `parsedSql` instance (verify no re-parsing)
- Parse failure with fail-fast config throws `SqlParseException`
- Parse failure with lenient config logs warning and returns pass

**Then Implement:**
Complete `validate()` method in `DefaultSqlSafetyValidator` with parse-once logic:

1. **Deduplication Check:**
   ```java
   if (!deduplicationFilter.shouldCheck(context.getSql())) {
       return ValidationResult.pass();
   }
   ```

2. **Parse-Once Logic:**
   ```java
   if (context.getParsedSql() == null) {
       try {
           Statement stmt = facade.parse(context.getSql());
           context.setParsedSql(stmt);
       } catch (SqlParseException e) {
           return handleParseFailure(context, e);
       }
   }
   ```

3. **Helper Method: `handleParseFailure(SqlContext context, SqlParseException e)`**
   - If facade is in fail-fast mode: throw exception (propagate to caller)
   - If facade is in lenient mode: log.warn() and return `ValidationResult.pass()`
   - Log message should include SQL snippet (first 100 chars) and error reason

**Constraints:**
- Parse SQL only once, all checkers reuse parsed AST from context
- Lenient mode allows validation to continue even if some SQL cannot be parsed
- Deduplication reduces overhead when same SQL validated multiple times

### Step 4: Orchestrator Integration

**Integrate RuleCheckerOrchestrator:**
Complete the `validate()` method by adding orchestrator call:

```java
ValidationResult result = ValidationResult.pass();
orchestrator.orchestrate(context, result);
return result;
```

**Checker Instantiation Example:**
Create factory method or builder pattern for assembling all 10 checkers with proper config:

```java
// Example factory method (implement based on your config approach)
public static DefaultSqlSafetyValidator create(SqlGuardConfig config) {
    JSqlParserFacade facade = new JSqlParserFacade(/*lenient mode config*/);

    // Instantiate checkers using validator/rule/impl config classes
    List<RuleChecker> checkers = Arrays.asList(
        new NoWhereClauseChecker(config.getRules().getNoWhereClause()),
        new DummyConditionChecker(config.getRules().getDummyCondition()),
        new BlacklistFieldChecker(config.getRules().getBlacklistFields()),
        // ... all 10 checkers
    );

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter(config.getDeduplication());

    return new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
}
```

**Test:**
Write integration test verifying orchestrator executes all enabled checkers and aggregates violations correctly.

**Constraints:**
- Orchestrator handles checker iteration and enabled/disabled filtering
- Violation aggregation to highest risk level handled by ValidationResult
- All 10 checkers must be properly instantiated with correct config classes

### Step 5: End-to-End Integration & Performance Testing

**Integration Test Suite:**
Write comprehensive integration test class `SqlSafetyValidatorIntegrationTest` with realistic SQL samples:

1. **Multi-Rule Violation Test:**
   - SQL: `SELECT * FROM users LIMIT 10` (no WHERE + missing ORDER BY)
   - Verify violations from: NoWhereClauseChecker (CRITICAL), MissingOrderByChecker (LOW)
   - Verify risk level aggregated to CRITICAL

2. **Clean SQL Test:**
   - SQL: `SELECT * FROM users WHERE id=? ORDER BY create_time LIMIT 10`
   - Verify `result.passed = true`, no violations

3. **Parse Failure Test:**
   - Invalid SQL syntax: `SELECT * FORM users` (typo in FROM)
   - Verify fail-fast throws exception OR lenient returns pass

4. **Deduplication Test:**
   - Validate same SQL twice within TTL
   - Verify second call returns immediately (cached)

5. **All Checkers Enabled Test:**
   - Various SQL patterns triggering different checkers
   - Verify all 10 checkers execute correctly

**JMH Performance Benchmark:**
Create `SqlValidationBenchmark` class using JMH micro-benchmark framework:

1. **Baseline Measurement:**
   - Parse SQL + extract WHERE without validation
   - Measure time per operation

2. **Full Validation Measurement:**
   - Complete `validate()` call with all 10 checkers
   - Measure time per operation

3. **Overhead Calculation:**
   - `overhead = ((validation_time - baseline_time) / baseline_time) * 100`
   - Verify overhead < 5% (target from design section 9.1)

4. **Run with Maven:**
   - Configure JMH plugin in POM: `mvn test -Pbenchmark`
   - Report results in Memory Log

**Constraints:**
- Integration tests use realistic SQL patterns from production scenarios
- Performance benchmark must demonstrate <5% overhead target
- All 437 existing tests must continue to pass

## Expected Output

**Deliverables:**
1. `SqlSafetyValidator` interface in `com.footstone.sqlguard.validator` package
2. `DefaultSqlSafetyValidator` implementation with all 10 checkers integrated
3. `SqlDeduplicationFilter` class with ThreadLocal LRU cache
4. `SqlSafetyValidatorIntegrationTest` with comprehensive test scenarios
5. `SqlValidationBenchmark` JMH micro-benchmark
6. All tests passing (437 existing + new integration tests)

**Success Criteria:**
- All 10 rule checkers correctly integrated and functioning
- Parse-once optimization working (SQL parsed only once per validation)
- Deduplication filter preventing redundant validation
- Performance benchmark shows <5% overhead
- Comprehensive integration tests covering multi-rule violations, clean SQL, parse failures, deduplication
- All existing Phase 1 and Phase 2 tests still passing

**File Locations:**
- Interface: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/SqlSafetyValidator.java`
- Implementation: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/DefaultSqlSafetyValidator.java`
- Deduplication: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/SqlDeduplicationFilter.java`
- Integration tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/SqlSafetyValidatorIntegrationTest.java`
- Benchmark: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/SqlValidationBenchmark.java`
- Unit tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/DefaultSqlSafetyValidatorTest.java`, `SqlDeduplicationFilterTest.java`, `ParseOnceIntegrationTest.java`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_02_Validation_Engine/Task_2_13_DefaultSqlSafetyValidator_Assembly.md`

Follow `.apm/guides/Memory_Log_Guide.md` instructions for proper logging format including:
- Task completion status
- All deliverables created with file paths
- Test results (total count, pass/fail)
- Performance benchmark results with overhead percentage
- Any technical decisions or challenges encountered
- Dependencies verified and properly integrated
