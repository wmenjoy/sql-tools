---
task_id: Task_2_9
task_name: Physical Pagination - Deep Offset Check
agent: Agent_Core_Engine_Validation
phase: Phase_02_Validation_Engine
dependencies:
  - Task_2_6 (PaginationDetection Infrastructure - COMPLETED)
  - Task_2_8 (NoConditionPaginationChecker - runtime priority, must run before this checker)
execution_type: single_step
priority: medium
estimated_complexity: medium
---

# Task Assignment: Task 2.9 - Deep Pagination Checker Implementation

## Objective

Implement MEDIUM-level checker detecting deep pagination (high OFFSET values in LIMIT queries) causing database to scan and skip large row counts before returning results, degrading performance significantly as offset increases.

## Context from Dependencies

### Task 2.6 - Pagination Detection Infrastructure (COMPLETED)

**Key Infrastructure:**
- `PaginationType` enum with PHYSICAL type indicating LIMIT-based pagination
- `PaginationPluginDetector` with `detectPaginationType(SqlContext)` method
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/`

### Task 2.8 - NoConditionPaginationChecker (Runtime Dependency)

**Early-Return Mechanism:**
- Task 2.8 sets `result.getDetails().put("earlyReturn", true)` when no-condition violation found
- **This checker (Task 2.9) MUST check for early-return flag and skip if present**
- Rationale: Deep offset irrelevant if query performs full table scan anyway

### Phase 1 Foundation (COMPLETED)

**Available from Task 2.1:**
- `AbstractRuleChecker` base class
- `CheckerConfig` base configuration class
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/`

## Requirements

### 1. PaginationAbuseConfig Class

**Package:** `com.footstone.sqlguard.validator.rule.impl`

**Specifications:**
- Extend `CheckerConfig` base class
- Additional configuration fields:
  - `int maxOffset = 10000` (default threshold)
  - `int maxPageSize = 1000` (default, used by Task 2.10)
- Two constructors:
  - Default constructor: `enabled = true, maxOffset = 10000, maxPageSize = 1000`
  - Full constructor: `PaginationAbuseConfig(boolean enabled, int maxOffset, int maxPageSize)`
- Getters and setters for all fields
- Comprehensive Javadoc explaining deep pagination performance impact

### 2. DeepPaginationChecker Class

**Package:** `com.footstone.sqlguard.validator.pagination.impl`

**Specifications:**
- Extend `AbstractRuleChecker` from Task 2.1
- Inject `PaginationPluginDetector` via constructor
- Accept `PaginationAbuseConfig` in constructor

**check() Method Logic:**
```java
1. Skip if checker disabled: if (!isEnabled()) return
2. Detect pagination type: PaginationType type = detector.detectPaginationType(context)
3. Skip if not PHYSICAL: if (type != PaginationType.PHYSICAL) return
4. Check for early-return flag from Task 2.8:
   if (result.getDetails().containsKey("earlyReturn") &&
       result.getDetails().get("earlyReturn") == Boolean.TRUE) {
       return; // Skip, no-condition checker already violated
   }
5. Extract Limit from SELECT statement:
   SELECT select = (SELECT) context.getParsedSql()
   Limit limit = select.getLimit()
   if (limit == null) return // No LIMIT clause
6. Calculate offset supporting multiple LIMIT syntaxes:
   long offset = 0;
   if (limit.getOffset() != null) {
       offset = limit.getOffset().getValue(); // "LIMIT n OFFSET m" syntax
   } else if (limit.getOffsetJdbcParameter() != null) {
       // MySQL "LIMIT offset,rowCount" syntax
       offset = limit.getOffsetJdbcParameter().getValue();
   }
7. Compare against threshold:
   if (offset > config.getMaxOffset()) {
       Add MEDIUM violation:
       - Message: "深分页offset=" + offset + ",需扫描并跳过" + offset + "行数据,性能较差"
       - Suggestion: "建议使用游标分页(WHERE id > lastId)避免深度offset"
   }
```

**Key Implementation Notes:**
- MEDIUM risk level (less severe than CRITICAL no-condition, but still performance issue)
- Supports two LIMIT syntaxes: "LIMIT n OFFSET m" and "LIMIT m,n" (MySQL)
- Threshold configurable via `maxOffset` (default 10000)
- Must check early-return flag to avoid misleading violations

### 3. Comprehensive Test Coverage

**Test Class:** `DeepPaginationCheckerTest`
**Package:** `com.footstone.sqlguard.validator.pagination.impl`

**Required Test Scenarios (15+ tests):**

**Threshold Boundary Tests:**
1. `testOffsetBelowThreshold_shouldPass()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 9999"
   - Config: maxOffset = 10000
   - Expect no violation

2. `testOffsetAboveThreshold_shouldViolate()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 10001"
   - Config: maxOffset = 10000
   - Expect MEDIUM violation

3. `testOffsetEqualsThreshold_shouldPass()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 10000"
   - Config: maxOffset = 10000
   - Expect no violation (boundary inclusive on pass side)

4. `testOffsetMaxMinusOne_shouldPass()`
   - offset = maxOffset - 1
   - Expect no violation

5. `testOffsetMaxPlusOne_shouldViolate()`
   - offset = maxOffset + 1
   - Expect MEDIUM violation

**LIMIT Syntax Tests:**
6. `testLimitOffsetSyntax_shouldCalculateCorrectly()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 5000"
   - Verify offset extracted correctly

7. `testLimitCommaSyntax_shouldCalculateCorrectly()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 5000,20" (MySQL syntax)
   - Verify offset=5000 extracted correctly

8. `testOnlyLimitNoOffset_shouldPassWithZeroOffset()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 100"
   - Verify offset=0, no violation

**Pagination Type Tests:**
9. `testLogicalPagination_shouldSkip()`
   - SqlContext with RowBounds (LOGICAL type)
   - Expect no violation from this checker

10. `testNoPagination_shouldSkip()`
    - Plain query without LIMIT (NONE type)
    - Expect no violation

**Early-Return Tests:**
11. `testNoConditionEarlyReturn_shouldSkipThisChecker()`
    - Create ValidationResult with earlyReturn=true in details
    - SQL: "SELECT * FROM user LIMIT 20 OFFSET 50000" (deep offset)
    - Expect no violation from DeepPaginationChecker (Task 2.8 already violated)

12. `testIntegrationWithNoConditionChecker()`
    - Use RuleCheckerOrchestrator with both checkers
    - SQL: "SELECT * FROM user LIMIT 10000" (no WHERE, deep limit)
    - Verify only NoConditionPaginationChecker violation present
    - Verify DeepPaginationChecker did NOT add violation

**Configuration Tests:**
13. `testCustomMaxOffset_shouldRespect()`
    - Config: maxOffset = 5000
    - SQL with OFFSET 6000
    - Expect MEDIUM violation

14. `testDisabledChecker_shouldSkip()`
    - Config: enabled = false
    - SQL with deep offset
    - Verify checker.isEnabled() returns false
    - Verify no violations added

**Violation Message Tests:**
15. `testViolationMessage_shouldContainOffsetValue()`
    - SQL with OFFSET 15000
    - Verify message contains "offset=15000"
    - Verify message contains "扫描并跳过"

16. `testViolationSuggestion_shouldMentionCursorPagination()`
    - Verify suggestion contains "游标分页" or "WHERE id > lastId"

## Implementation Steps

### Step 1: Test-Driven Development (TDD)

1. Create test class `DeepPaginationCheckerTest` in test directory
2. Write all 16+ test scenarios from requirements
3. Compile tests (they will fail initially)
4. Verify test compilation with proper imports

### Step 2: Configuration Class Implementation

1. Create `PaginationAbuseConfig` class
2. Extend `CheckerConfig` base class
3. Add `maxOffset` and `maxPageSize` fields with defaults
4. Implement constructors and getters/setters
5. Add comprehensive Javadoc
6. Run configuration tests

### Step 3: Checker Implementation

1. Create `DeepPaginationChecker` class
2. Extend `AbstractRuleChecker`
3. Inject `PaginationPluginDetector` and `PaginationAbuseConfig`
4. Implement `check()` method following logic above
5. Handle both LIMIT syntaxes (OFFSET keyword and MySQL comma)
6. Check early-return flag before adding violations
7. Add MEDIUM violation when offset > maxOffset

### Step 4: Test Execution and Verification

1. Run all tests: `mvn test -Dtest=DeepPaginationCheckerTest`
2. Verify all 16+ tests pass
3. Check code coverage - aim for >95% line coverage
4. Verify Google Java Style compliance: `mvn checkstyle:check`

### Step 5: Integration Verification

1. Test with RuleCheckerOrchestrator including Task 2.8 checker
2. Verify early-return mechanism works correctly
3. Run all module tests to ensure no regressions

## Output Requirements

**Created Files:**
1. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/PaginationAbuseConfig.java`
2. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/DeepPaginationChecker.java`
3. `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/pagination/impl/DeepPaginationCheckerTest.java`

**Test Results:**
```
Tests run: 16+, Failures: 0, Errors: 0, Skipped: 0
```

**Key Deliverables:**
- DeepPaginationChecker with MEDIUM violation for excessive OFFSET values
- PaginationAbuseConfig with configurable maxOffset threshold (default 10000)
- Support for both LIMIT syntaxes ("LIMIT n OFFSET m" and "LIMIT m,n")
- Early-return flag checking to prevent misleading violations
- Comprehensive test coverage including boundary conditions
- Google Java Style compliance

## Success Criteria

1. ✅ All 16+ tests pass with 100% success rate
2. ✅ MEDIUM violation when offset > maxOffset
3. ✅ No violation when offset <= maxOffset
4. ✅ Boundary condition handling correct (threshold inclusive on pass side)
5. ✅ Both LIMIT syntaxes supported correctly
6. ✅ Early-return flag checked (skips if Task 2.8 violated)
7. ✅ Integration test with NoConditionPaginationChecker works
8. ✅ Checker respects enabled/disabled configuration
9. ✅ Custom maxOffset configuration respected
10. ✅ No regressions in existing tests
11. ✅ Google Java Style compliance verified

## Important Notes

**Performance Impact:**
- Deep pagination requires database to scan offset+limit rows
- Example: OFFSET 100000 LIMIT 20 → scans 100020 rows, returns 20
- Performance degrades linearly with offset
- Solution: cursor-based pagination (WHERE id > lastId)

**Critical Design Decisions:**
1. **MEDIUM Risk Level:** Less severe than no-condition but still significant performance issue
2. **Early-Return Awareness:** Must check Task 2.8 flag to avoid misleading violations
3. **Configurable Threshold:** Default 10000, but teams can adjust based on data size
4. **Both LIMIT Syntaxes:** Support standard and MySQL-specific syntax

**Common Pitfalls to Avoid:**
- Do NOT forget early-return flag check (critical for integration with Task 2.8)
- Do NOT use strict equality for threshold (offset == maxOffset should pass)
- Do NOT assume only one LIMIT syntax (support both)
- Do NOT trigger on LOGICAL pagination (only PHYSICAL)

## Reference Information

**Related Design Sections:**
- Section 3.3.7: Deep Pagination Performance Impact
- Section 7.3: MEDIUM Risk Level Violations
- Section 8.1: Checker Orchestration and Priority

**Dependency Task Outputs:**
- Task 2.6 Memory Log: `/Users/liujinliang/workspace/ai/sqltools/.apm/Memory/Phase_02_Validation_Engine/Task_2_6_Pagination_Detection_Infrastructure.md`
- Task 2.8 (Runtime): Will set early-return flag during orchestration

---

**Execution Agent:** Agent_Core_Engine_Validation
**Execution Mode:** TDD with comprehensive test coverage
**Expected Duration:** Single session completion
**Priority:** MEDIUM (performance optimization checker)
