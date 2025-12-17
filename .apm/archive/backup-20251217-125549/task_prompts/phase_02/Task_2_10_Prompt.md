---
task_id: Task_2_10
task_name: Physical Pagination - Large PageSize Check
agent: Agent_Core_Engine_Validation
phase: Phase_02_Validation_Engine
dependencies:
  - Task_2_6 (PaginationDetection Infrastructure - COMPLETED)
  - Task_2_9 (PaginationAbuseConfig - shared configuration)
execution_type: single_step
priority: medium
estimated_complexity: low
---

# Task Assignment: Task 2.10 - Large PageSize Checker Implementation

## Objective

Implement MEDIUM-level checker detecting excessively large pageSize values in LIMIT queries (e.g., LIMIT 10000) causing single query to return massive datasets potentially overwhelming application memory or network bandwidth.

## Context from Dependencies

### Task 2.6 - Pagination Detection Infrastructure (COMPLETED)

**Key Infrastructure:**
- `PaginationType` enum with PHYSICAL type
- `PaginationPluginDetector` with `detectPaginationType(SqlContext)` method
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/`

### Task 2.9 - PaginationAbuseConfig (Shared Configuration)

**Configuration Class Available:**
- `PaginationAbuseConfig` extends `CheckerConfig`
- Contains `maxPageSize` field (default 1000)
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/PaginationAbuseConfig.java`
- **Reuse this configuration class, do NOT create new one**

### Phase 1 Foundation (COMPLETED)

**Available from Task 2.1:**
- `AbstractRuleChecker` base class
- `RiskLevel` enum
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/`

## Requirements

### 1. LargePageSizeChecker Class

**Package:** `com.footstone.sqlguard.validator.pagination.impl`

**Specifications:**
- Extend `AbstractRuleChecker` from Task 2.1
- Inject `PaginationPluginDetector` via constructor
- Accept `PaginationAbuseConfig` in constructor (reuse from Task 2.9)

**check() Method Logic:**
```java
1. Skip if checker disabled: if (!isEnabled()) return
2. Detect pagination type: PaginationType type = detector.detectPaginationType(context)
3. Skip if not PHYSICAL: if (type != PaginationType.PHYSICAL) return
4. Extract Limit from SELECT statement:
   SELECT select = (SELECT) context.getParsedSql()
   Limit limit = select.getLimit()
   if (limit == null) return // No LIMIT clause
5. Calculate pageSize from LIMIT clause:
   long pageSize = 0;
   if (limit.getRowCount() != null) {
       pageSize = limit.getRowCount().getValue();
       // Works for both "LIMIT n" and "LIMIT m,n" syntaxes
       // getRowCount() returns the row count portion
   }
6. Compare against threshold:
   if (pageSize > config.getMaxPageSize()) {
       Add MEDIUM violation:
       - Message: "pageSize=" + pageSize + "过大,单次查询数据量过多"
       - Suggestion: "建议降低pageSize到" + config.getMaxPageSize() + "以内,避免单次返回过多数据"
   }
```

**Key Implementation Notes:**
- MEDIUM risk level (less severe than CRITICAL but still significant)
- PageSize is the row count portion of LIMIT clause
- Works for both syntaxes: "LIMIT 500" and "LIMIT 100,500" (both have pageSize=500)
- Threshold configurable via `maxPageSize` (default 1000)
- Independent check from deep offset (Task 2.9) - both can trigger on same SQL

### 2. Comprehensive Test Coverage

**Test Class:** `LargePageSizeCheckerTest`
**Package:** `com.footstone.sqlguard.validator.pagination.impl`

**Required Test Scenarios (12+ tests):**

**Threshold Boundary Tests:**
1. `testPageSizeBelowThreshold_shouldPass()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 999"
   - Config: maxPageSize = 1000
   - Expect no violation

2. `testPageSizeAboveThreshold_shouldViolate()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 1001"
   - Config: maxPageSize = 1000
   - Expect MEDIUM violation

3. `testPageSizeEqualsThreshold_shouldPass()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 1000"
   - Config: maxPageSize = 1000
   - Expect no violation (boundary inclusive on pass side)

4. `testPageSizeMaxMinusOne_shouldPass()`
   - pageSize = maxPageSize - 1
   - Expect no violation

5. `testPageSizeMaxPlusOne_shouldViolate()`
   - pageSize = maxPageSize + 1
   - Expect MEDIUM violation

**LIMIT Syntax Tests:**
6. `testLimitOnlySyntax_shouldExtractCorrectly()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 500"
   - Verify pageSize=500 extracted correctly

7. `testLimitCommaSyntax_shouldExtractRowCount()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 100,500" (MySQL syntax)
   - Verify pageSize=500 extracted (not 100, which is offset)

8. `testLimitOffsetKeywordSyntax_shouldExtractRowCount()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 500 OFFSET 100"
   - Verify pageSize=500 extracted correctly

**Pagination Type Tests:**
9. `testLogicalPagination_shouldSkip()`
   - SqlContext with RowBounds (LOGICAL type)
   - Expect no violation from this checker

10. `testNoPagination_shouldSkip()`
    - Plain query without LIMIT (NONE type)
    - Expect no violation

**Configuration Tests:**
11. `testCustomMaxPageSize_shouldRespect()`
    - Config: maxPageSize = 500
    - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 600"
    - Expect MEDIUM violation

12. `testDisabledChecker_shouldSkip()`
    - Config: enabled = false
    - SQL with large pageSize
    - Verify checker.isEnabled() returns false
    - Verify no violations added

**Violation Message Tests:**
13. `testViolationMessage_shouldContainPageSizeValue()`
    - SQL with LIMIT 5000
    - Verify message contains "pageSize=5000"
    - Verify message contains "单次查询数据量过多"

14. `testViolationSuggestion_shouldMentionThreshold()`
    - Config: maxPageSize = 1000
    - Verify suggestion contains "降低pageSize到1000以内"

**Edge Case Tests:**
15. `testVeryLargePageSize_shouldViolate()`
    - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 100000"
    - Config: maxPageSize = 1000
    - Expect MEDIUM violation with appropriate message

## Implementation Steps

### Step 1: Test-Driven Development (TDD)

1. Create test class `LargePageSizeCheckerTest` in test directory
2. Write all 15+ test scenarios from requirements
3. Compile tests (they will fail initially)
4. Verify test compilation with proper imports

### Step 2: Checker Implementation

1. Create `LargePageSizeChecker` class
2. Extend `AbstractRuleChecker`
3. Inject `PaginationPluginDetector` and `PaginationAbuseConfig` (reuse from Task 2.9)
4. Implement `check()` method following logic above
5. Extract pageSize from `limit.getRowCount()` (works for both syntaxes)
6. Add MEDIUM violation when pageSize > maxPageSize

### Step 3: Test Execution and Verification

1. Run all tests: `mvn test -Dtest=LargePageSizeCheckerTest`
2. Verify all 15+ tests pass
3. Check code coverage - aim for >95% line coverage
4. Verify Google Java Style compliance: `mvn checkstyle:check`

### Step 4: Integration Verification

1. Test with realistic SQL queries
2. Verify interaction with DeepPaginationChecker (both can trigger on same SQL)
3. Run all module tests to ensure no regressions

## Output Requirements

**Created Files:**
1. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/LargePageSizeChecker.java`
2. `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/pagination/impl/LargePageSizeCheckerTest.java`

**Test Results:**
```
Tests run: 15+, Failures: 0, Errors: 0, Skipped: 0
```

**Key Deliverables:**
- LargePageSizeChecker with MEDIUM violation for excessive pageSize values
- Reuse PaginationAbuseConfig.maxPageSize from Task 2.9 (default 1000)
- Support for both LIMIT syntaxes (correctly extract row count)
- Comprehensive test coverage including boundary conditions
- Google Java Style compliance

## Success Criteria

1. ✅ All 15+ tests pass with 100% success rate
2. ✅ MEDIUM violation when pageSize > maxPageSize
3. ✅ No violation when pageSize <= maxPageSize
4. ✅ Boundary condition handling correct (threshold inclusive on pass side)
5. ✅ Both LIMIT syntaxes supported (correctly extract row count, not offset)
6. ✅ Reuses PaginationAbuseConfig from Task 2.9
7. ✅ Independent from DeepPaginationChecker (both can trigger on same SQL)
8. ✅ Checker respects enabled/disabled configuration
9. ✅ Custom maxPageSize configuration respected
10. ✅ No regressions in existing tests
11. ✅ Google Java Style compliance verified

## Important Notes

**Memory and Bandwidth Impact:**
- Large pageSize returns massive datasets in single query
- Example: LIMIT 5000 returns 5000 rows consuming significant memory
- Network bandwidth consumed proportionally
- JVM memory pressure from large result sets

**Critical Design Decisions:**
1. **MEDIUM Risk Level:** Less severe than CRITICAL but still significant resource issue
2. **Configuration Reuse:** Use PaginationAbuseConfig.maxPageSize from Task 2.9
3. **Independent Check:** Can trigger alongside DeepPaginationChecker on same SQL
4. **Both LIMIT Syntaxes:** Extract row count correctly regardless of syntax

**PageSize vs Offset:**
- PageSize = number of rows to return (limit.getRowCount())
- Offset = number of rows to skip before returning
- LIMIT 100,500: offset=100, pageSize=500
- LIMIT 500 OFFSET 100: offset=100, pageSize=500
- LIMIT 500: offset=0, pageSize=500

**Common Pitfalls to Avoid:**
- Do NOT confuse pageSize with offset
- Do NOT extract wrong value from "LIMIT m,n" syntax (n is pageSize, not m)
- Do NOT use strict equality for threshold (pageSize == maxPageSize should pass)
- Do NOT trigger on LOGICAL pagination (only PHYSICAL)
- Do NOT create new config class (reuse PaginationAbuseConfig from Task 2.9)

## Reference Information

**Related Design Sections:**
- Section 3.3.8: Large PageSize Memory Impact
- Section 7.3: MEDIUM Risk Level Violations

**Dependency Task Outputs:**
- Task 2.6 Memory Log: `/Users/liujinliang/workspace/ai/sqltools/.apm/Memory/Phase_02_Validation_Engine/Task_2_6_Pagination_Detection_Infrastructure.md`
- Task 2.9: PaginationAbuseConfig class (shared configuration)

**Project Structure:**
```
sql-guard-core/
├── src/main/java/com/footstone/sqlguard/
│   ├── validator/
│   │   ├── pagination/impl/
│   │   │   ├── LogicalPaginationChecker.java (Task 2.7)
│   │   │   ├── NoConditionPaginationChecker.java (Task 2.8)
│   │   │   ├── DeepPaginationChecker.java (Task 2.9)
│   │   │   └── LargePageSizeChecker.java (Task 2.10 - THIS TASK)
│   │   └── rule/impl/
│   │       └── PaginationAbuseConfig.java (Task 2.9 - REUSE)
└── src/test/java/com/footstone/sqlguard/
    └── validator/pagination/impl/
        └── LargePageSizeCheckerTest.java (Task 2.10 - THIS TASK)
```

---

**Execution Agent:** Agent_Core_Engine_Validation
**Execution Mode:** TDD with comprehensive test coverage
**Expected Duration:** Single session completion
**Priority:** MEDIUM (resource management checker)
