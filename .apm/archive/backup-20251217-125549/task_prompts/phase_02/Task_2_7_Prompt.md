---
task_id: Task_2_7
task_name: Logical Pagination Checker
agent: Agent_Core_Engine_Validation
phase: Phase_02_Validation_Engine
dependencies:
  - Task_2_6 (PaginationDetection Infrastructure - COMPLETED)
execution_type: single_step
priority: high
estimated_complexity: medium
---

# Task Assignment: Task 2.7 - Logical Pagination Checker Implementation

## Objective

Implement CRITICAL-level checker detecting logical pagination (RowBounds/IPage without pagination plugin), the most dangerous pagination pattern causing entire result sets to load into memory before in-memory row skipping, frequently causing production OOM crashes.

## Context from Dependencies

### Task 2.6 - Pagination Detection Infrastructure (COMPLETED)

**Key Infrastructure Available:**
- `PaginationType` enum: LOGICAL (dangerous in-memory), PHYSICAL (safe database-level), NONE
- `PaginationPluginDetector` class:
  - `detectPaginationType(SqlContext)` method implementing three-tier detection
  - `hasPaginationPlugin()` checks for MyBatis PageHelper and MyBatis-Plus PaginationInnerInterceptor
  - 100% detection accuracy across all scenarios
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/`

**Detection Logic (from Task 2.6):**
```
if (hasPageParam && !hasLimit && !hasPlugin) → LOGICAL
if (hasLimit || (hasPageParam && hasPlugin)) → PHYSICAL
else → NONE
```

**Critical Distinction:**
- **LOGICAL pagination** (RowBounds/IPage without plugin): Loads entire result set into memory, then performs offset/limit in-memory → OOM risk
- **PHYSICAL pagination** (LIMIT clause or plugin-enabled): Database performs row filtering → safe

### Phase 1 Foundation (COMPLETED)

**Available from Task 2.1 (Rule Checker Framework):**
- `AbstractRuleChecker` base class with utility methods
- `RuleChecker` interface with `check(SqlContext, ValidationResult)` method
- `CheckerConfig` base configuration class
- `RiskLevel` enum: CRITICAL, HIGH, MEDIUM, LOW, SAFE
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/`

**Available from Task 1.2 (Core Data Models):**
- `SqlContext` class with builder pattern
- `ValidationResult` class with violation aggregation
- `RiskLevel` enum
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/core/model/`

## Requirements

### 1. LogicalPaginationConfig Class

**Package:** `com.footstone.sqlguard.validator.rule.impl`

**Specifications:**
- Extend `CheckerConfig` base class
- No additional configuration fields beyond inherited `enabled` toggle
- Two constructors:
  - Default constructor: `enabled = true`
  - Parameterized constructor: `LogicalPaginationConfig(boolean enabled)`
- Comprehensive Javadoc explaining LOGICAL pagination risk

### 2. LogicalPaginationChecker Class

**Package:** `com.footstone.sqlguard.validator.pagination.impl`

**Specifications:**
- Extend `AbstractRuleChecker` from Task 2.1
- Inject `PaginationPluginDetector` via constructor for type detection
- Accept `LogicalPaginationConfig` in constructor

**check() Method Logic:**
```java
1. Skip if checker disabled: if (!isEnabled()) return
2. Detect pagination type: PaginationType type = detector.detectPaginationType(context)
3. Skip if not LOGICAL: if (type != PaginationType.LOGICAL) return
4. Extract pagination parameters:
   - RowBounds rowBounds = context.getRowBounds()
   - int offset = rowBounds.getOffset()
   - int limit = rowBounds.getLimit()
5. Add CRITICAL violation:
   - Message: "检测到逻辑分页!将加载全表数据到内存,可能导致OOM"
   - Suggestion: "立即配置分页插件:MyBatis-Plus PaginationInnerInterceptor或PageHelper"
6. Add pagination parameters to violation details:
   - details.put("offset", offset)
   - details.put("limit", limit)
   - details.put("paginationType", "LOGICAL")
```

**Key Implementation Notes:**
- CRITICAL risk level (highest severity) - this is a production OOM risk
- Violation message must be urgent and actionable
- Violation details include offset/limit for debugging
- Reuse `PaginationPluginDetector` from Task 2.6 (do NOT reimplement detection)

### 3. Comprehensive Test Coverage

**Test Class:** `LogicalPaginationCheckerTest`
**Package:** `com.footstone.sqlguard.validator.pagination.impl`

**Required Test Scenarios (15+ tests):**

**Basic Detection Tests:**
1. `testRowBoundsWithoutPlugin_shouldViolate()`
   - Create SqlContext with RowBounds(100, 20), no plugin
   - Expect CRITICAL violation
   - Verify violation message contains "逻辑分页" and "OOM"

2. `testRowBoundsWithPageHelper_shouldPass()`
   - Create SqlContext with RowBounds + PageHelper plugin configured
   - Expect no violation (PHYSICAL pagination)

3. `testNoRowBounds_shouldPass()`
   - Plain query without RowBounds parameter
   - Expect no violation

4. `testRowBoundsDefault_shouldPass()`
   - SqlContext with RowBounds.DEFAULT (infinite bounds)
   - Expect no violation (not pagination)

**Pagination Parameter Tests:**
5. `testSmallOffset_shouldViolateWithDetails()`
   - RowBounds(0, 10) without plugin
   - Verify violation details contain offset=0, limit=10

6. `testLargeOffset_shouldViolateWithDetails()`
   - RowBounds(10000, 100) without plugin
   - Verify violation details contain offset=10000, limit=100

7. `testVeryLargeLimit_shouldViolate()`
   - RowBounds(0, 50000) without plugin
   - Verify CRITICAL violation with large limit warning

**IPage Parameter Tests (MyBatis-Plus):**
8. `testIPageWithoutPlugin_shouldViolate()`
   - SqlContext with IPage parameter, no PaginationInnerInterceptor
   - Expect CRITICAL violation

9. `testIPageWithMpPlugin_shouldPass()`
   - SqlContext with IPage + MybatisPlusInterceptor configured
   - Expect no violation (PHYSICAL)

**Configuration Tests:**
10. `testDisabledChecker_shouldSkip()`
    - LogicalPaginationConfig with enabled=false
    - Verify checker.isEnabled() returns false
    - Verify no violations added

**Integration Tests:**
11. `testIntegrationWithRealRowBounds()`
    - Use actual RowBounds instances from MyBatis
    - Test various offset/limit combinations
    - Verify violation detail extraction

12. `testIntegrationWithPageHelperPlugin()`
    - Configure actual PageHelper interceptor
    - Verify detection recognizes plugin presence

13. `testIntegrationWithMybatisPlusPlugin()`
    - Configure actual MybatisPlusInterceptor with PaginationInnerInterceptor
    - Verify PHYSICAL type detection

**Edge Case Tests:**
14. `testNullRowBounds_shouldNotViolate()`
    - SqlContext with rowBounds = null
    - Verify no NPE, no violation

15. `testMultipleValidations_shouldDetectEachTime()`
    - Validate same SQL with RowBounds multiple times
    - Verify each validation detects violation independently

**Violation Detail Verification:**
16. `testViolationDetails_shouldContainAllInfo()`
    - Verify violation details Map contains:
      - "offset" key with correct value
      - "limit" key with correct value
      - "paginationType" key with "LOGICAL"

## Implementation Steps

### Step 1: Test-Driven Development (TDD)

1. Create test class `LogicalPaginationCheckerTest` in test directory
2. Write all 16+ test scenarios from requirements
3. Compile tests (they will fail initially)
4. Verify test compilation with proper imports

### Step 2: Configuration Class Implementation

1. Create `LogicalPaginationConfig` class
2. Extend `CheckerConfig` base class
3. Implement two constructors (default and parameterized)
4. Add comprehensive Javadoc explaining LOGICAL pagination risk
5. Run tests - configuration tests should pass

### Step 3: Checker Implementation

1. Create `LogicalPaginationChecker` class in `validator.pagination.impl` package
2. Extend `AbstractRuleChecker` base class
3. Inject `PaginationPluginDetector` via constructor
4. Implement `check(SqlContext, ValidationResult)` method following logic above
5. Handle all edge cases (null checks, RowBounds.DEFAULT)

### Step 4: Test Execution and Verification

1. Run all tests: `mvn test -Dtest=LogicalPaginationCheckerTest`
2. Verify all 16+ tests pass
3. Check code coverage - aim for >95% line coverage
4. Verify Google Java Style compliance: `mvn checkstyle:check`

### Step 5: Integration Verification

1. Run all module tests to ensure no regressions
2. Verify integration with Task 2.6 infrastructure
3. Test with realistic SqlContext scenarios

## Output Requirements

**Created Files:**
1. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/LogicalPaginationConfig.java`
2. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/LogicalPaginationChecker.java`
3. `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/pagination/impl/LogicalPaginationCheckerTest.java`

**Test Results:**
```
Tests run: 16+, Failures: 0, Errors: 0, Skipped: 0
```

**Key Deliverables:**
- LogicalPaginationChecker with CRITICAL violation for RowBounds/IPage without plugin
- Comprehensive test coverage validating detection accuracy
- Violation details including offset/limit parameters
- Integration with Task 2.6 PaginationDetection infrastructure
- Google Java Style compliance
- Javadoc on all public APIs

## Success Criteria

1. ✅ All 16+ tests pass with 100% success rate
2. ✅ CRITICAL violation correctly triggered for LOGICAL pagination
3. ✅ No violation for PHYSICAL pagination (with plugin)
4. ✅ Violation details contain offset/limit parameters
5. ✅ Checker respects enabled/disabled configuration
6. ✅ Integration with PaginationPluginDetector from Task 2.6
7. ✅ No regressions in existing tests (340 tests still passing)
8. ✅ Google Java Style compliance verified
9. ✅ Comprehensive Javadoc on all public classes/methods

## Important Notes

**Critical Design Decisions:**
1. **CRITICAL Risk Level:** Logical pagination is most dangerous (production OOM risk)
2. **Reuse Task 2.6 Infrastructure:** Use PaginationPluginDetector, do NOT reimplement detection
3. **Actionable Violation Message:** Must direct developers to immediate fix (configure plugin)
4. **Violation Details:** Include offset/limit for debugging context

**Common Pitfalls to Avoid:**
- Do NOT reimagine pagination detection - reuse Task 2.6 infrastructure
- Do NOT treat RowBounds.DEFAULT as pagination (it's infinite bounds)
- Do NOT trigger violation for PHYSICAL pagination (plugin-enabled)
- Do NOT forget null checks for RowBounds parameter

**Testing Focus:**
- Comprehensive coverage of RowBounds and IPage scenarios
- Both MyBatis PageHelper and MyBatis-Plus PaginationInnerInterceptor
- Boundary conditions (RowBounds.DEFAULT, null, disabled checker)
- Violation detail extraction verification

## Reference Information

**Related Design Sections:**
- Section 3.3.5: Pagination Detection Logic
- Section 3.3.6: Logical Pagination Risk Analysis
- Section 7.2: CRITICAL Risk Level Violations

**Dependency Task Outputs:**
- Task 2.6 Memory Log: `/Users/liujinliang/workspace/ai/sqltools/.apm/Memory/Phase_02_Validation_Engine/Task_2_6_Pagination_Detection_Infrastructure.md`
- Task 2.1 Memory Log: `/Users/liujinliang/workspace/ai/sqltools/.apm/Memory/Phase_02_Validation_Engine/Task_2_1_Rule_Checker_Framework_Interfaces.md`

**Project Structure:**
```
sql-guard-core/
├── src/main/java/com/footstone/sqlguard/
│   ├── validator/
│   │   ├── pagination/
│   │   │   ├── PaginationType.java (Task 2.6 ✓)
│   │   │   ├── PaginationPluginDetector.java (Task 2.6 ✓)
│   │   │   └── impl/
│   │   │       └── LogicalPaginationChecker.java (Task 2.7 - THIS TASK)
│   │   └── rule/
│   │       ├── RuleChecker.java (Task 2.1 ✓)
│   │       ├── AbstractRuleChecker.java (Task 2.1 ✓)
│   │       └── impl/
│   │           └── LogicalPaginationConfig.java (Task 2.7 - THIS TASK)
└── src/test/java/com/footstone/sqlguard/
    └── validator/pagination/impl/
        └── LogicalPaginationCheckerTest.java (Task 2.7 - THIS TASK)
```

---

**Execution Agent:** Agent_Core_Engine_Validation
**Execution Mode:** TDD with comprehensive test coverage
**Expected Duration:** Single session completion
**Priority:** HIGH (CRITICAL risk checker)
