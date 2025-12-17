---
task_id: Task_2_11
task_name: Physical Pagination - Missing ORDER BY Check
agent: Agent_Core_Engine_Validation
phase: Phase_02_Validation_Engine
dependencies:
  - Task_2_6 (PaginationDetection Infrastructure - COMPLETED)
execution_type: single_step
priority: low
estimated_complexity: low
---

# Task Assignment: Task 2.11 - Missing ORDER BY Checker Implementation

## Objective

Implement LOW-level checker detecting physical pagination queries lacking ORDER BY clause, causing unstable result ordering across pages (same query may return different row orders on different executions, pagination becomes non-deterministic).

## Context from Dependencies

### Task 2.6 - Pagination Detection Infrastructure (COMPLETED)

**Key Infrastructure:**
- `PaginationType` enum with PHYSICAL type
- `PaginationPluginDetector` with `detectPaginationType(SqlContext)` method
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/`

### Phase 1 Foundation (COMPLETED)

**Available from Task 2.1:**
- `AbstractRuleChecker` base class
- `CheckerConfig` base configuration class
- `RiskLevel` enum with LOW level
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/`

## Requirements

### 1. MissingOrderByConfig Class

**Package:** `com.footstone.sqlguard.validator.rule.impl`

**Specifications:**
- Extend `CheckerConfig` base class
- No additional configuration fields beyond inherited `enabled` toggle
- Two constructors:
  - Default constructor: `enabled = true`
  - Parameterized constructor: `MissingOrderByConfig(boolean enabled)`
- Comprehensive Javadoc explaining ORDER BY importance for pagination stability

### 2. MissingOrderByChecker Class

**Package:** `com.footstone.sqlguard.validator.pagination.impl`

**Specifications:**
- Extend `AbstractRuleChecker` from Task 2.1
- Inject `PaginationPluginDetector` via constructor
- Accept `MissingOrderByConfig` in constructor

**check() Method Logic:**
```java
1. Skip if checker disabled: if (!isEnabled()) return
2. Detect pagination type: PaginationType type = detector.detectPaginationType(context)
3. Skip if not PHYSICAL: if (type != PaginationType.PHYSICAL) return
4. Cast statement to SELECT:
   SELECT select = (SELECT) context.getParsedSql()
5. Extract ORDER BY clause:
   List<OrderByElement> orderByElements = select.getOrderByElements()
6. Check if ORDER BY missing or empty:
   if (orderByElements == null || orderByElements.isEmpty()) {
7. Add LOW violation:
   - Message: "分页查询缺少ORDER BY,结果顺序不稳定"
   - Suggestion: "添加ORDER BY子句确保分页结果顺序稳定"
```

**Key Implementation Notes:**
- LOW risk level (least severe pagination issue) - query works but results unpredictable
- Database default ordering not guaranteed stable across executions
- Critical for user-facing pagination where consistency matters
- Simple check: ORDER BY present or not (don't validate ORDER BY quality)

### 3. Comprehensive Test Coverage

**Test Class:** `MissingOrderByCheckerTest`
**Package:** `com.footstone.sqlguard.validator.pagination.impl`

**Required Test Scenarios (10+ tests):**

**Basic ORDER BY Detection Tests:**
1. `testPaginationWithOrderBy_shouldPass()`
   - SQL: "SELECT * FROM user WHERE id > 0 ORDER BY id LIMIT 10"
   - Expect no violation

2. `testPaginationWithoutOrderBy_shouldViolate()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 10"
   - Expect LOW violation

3. `testNoPagination_shouldSkip()`
   - SQL: "SELECT * FROM user WHERE id > 0" (no LIMIT, no RowBounds)
   - Expect no violation (not paginated, ORDER BY not required)

**Multiple ORDER BY Column Tests:**
4. `testMultipleOrderByColumns_shouldPass()`
   - SQL: "SELECT * FROM user WHERE id > 0 ORDER BY create_time DESC, id ASC LIMIT 10"
   - Expect no violation

5. `testSingleOrderBy_shouldPass()`
   - SQL: "SELECT * FROM user WHERE id > 0 ORDER BY id LIMIT 10"
   - Expect no violation

6. `testOrderByWithExpression_shouldPass()`
   - SQL: "SELECT * FROM user WHERE id > 0 ORDER BY LOWER(username) LIMIT 10"
   - Expect no violation

**Pagination Type Tests:**
7. `testLogicalPagination_shouldSkip()`
   - SqlContext with RowBounds without plugin (LOGICAL type)
   - Expect no violation from this checker

8. `testPhysicalPaginationMissingOrderBy_shouldViolate()`
   - SQL: "SELECT * FROM user WHERE id > 0 LIMIT 10 OFFSET 100"
   - No ORDER BY clause
   - Expect LOW violation

**Configuration Tests:**
9. `testDisabledChecker_shouldSkip()`
   - Config: enabled = false
   - SQL: paginated query without ORDER BY
   - Verify checker.isEnabled() returns false
   - Verify no violations added

**Violation Message Tests:**
10. `testViolationMessage_shouldMentionStability()`
    - SQL without ORDER BY
    - Verify message contains "顺序不稳定"

11. `testViolationSuggestion_shouldMentionOrderBy()`
    - Verify suggestion contains "添加ORDER BY子句"

## Implementation Steps

### Step 1: Test-Driven Development (TDD)

1. Create test class `MissingOrderByCheckerTest` in test directory
2. Write all 11+ test scenarios from requirements
3. Compile tests (they will fail initially)
4. Verify test compilation with proper imports

### Step 2: Configuration Class Implementation

1. Create `MissingOrderByConfig` class
2. Extend `CheckerConfig` base class
3. Implement two constructors (default and parameterized)
4. Add comprehensive Javadoc explaining ORDER BY importance
5. Run configuration tests

### Step 3: Checker Implementation

1. Create `MissingOrderByChecker` class
2. Extend `AbstractRuleChecker`
3. Inject `PaginationPluginDetector` and `MissingOrderByConfig`
4. Implement `check()` method following logic above
5. Extract ORDER BY elements from SELECT statement
6. Add LOW violation if ORDER BY missing or empty

### Step 4: Test Execution and Verification

1. Run all tests: `mvn test -Dtest=MissingOrderByCheckerTest`
2. Verify all 11+ tests pass
3. Check code coverage - aim for >95% line coverage
4. Verify Google Java Style compliance: `mvn checkstyle:check`

### Step 5: Integration Verification

1. Test with realistic paginated SQL queries
2. Run all module tests to ensure no regressions

## Output Requirements

**Created Files:**
1. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/MissingOrderByConfig.java`
2. `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/MissingOrderByChecker.java`
3. `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/pagination/impl/MissingOrderByCheckerTest.java`

**Test Results:**
```
Tests run: 11+, Failures: 0, Errors: 0, Skipped: 0
```

**Key Deliverables:**
- MissingOrderByChecker with LOW violation for paginated queries without ORDER BY
- Simple presence check (ORDER BY present or not)
- Comprehensive test coverage
- Google Java Style compliance

## Success Criteria

1. ✅ All 11+ tests pass with 100% success rate
2. ✅ LOW violation for paginated query without ORDER BY
3. ✅ No violation for paginated query with ORDER BY
4. ✅ No violation for non-paginated queries (ORDER BY not required)
5. ✅ Multiple ORDER BY columns handled correctly
6. ✅ Only PHYSICAL pagination checked (LOGICAL and NONE skipped)
7. ✅ Checker respects enabled/disabled configuration
8. ✅ No regressions in existing tests
9. ✅ Google Java Style compliance verified

## Important Notes

**Result Instability Issue:**
- Database default ordering not guaranteed stable
- Without ORDER BY, same query may return different row orders
- Problem: page 2 may show rows from page 1 on subsequent request
- Impact: confusing user experience, inconsistent pagination
- Critical for user-facing features (product listings, search results)

**Critical Design Decisions:**
1. **LOW Risk Level:** Least severe pagination issue (query works, just unpredictable)
2. **Simple Presence Check:** Only verify ORDER BY exists, don't validate quality
3. **User-Facing Focus:** Most important for UI pagination, less critical for batch processing

**ORDER BY Validation Scope:**
- This checker only verifies ORDER BY presence
- Does NOT validate ORDER BY quality (e.g., unique columns, proper indexing)
- Does NOT check if ORDER BY columns indexed
- Keep it simple: present or not

**Common Pitfalls to Avoid:**
- Do NOT trigger on non-paginated queries
- Do NOT validate ORDER BY quality (out of scope)
- Do NOT trigger on LOGICAL pagination (only PHYSICAL)
- Do NOT forget to check for null and empty ORDER BY lists

## Reference Information

**Related Design Sections:**
- Section 3.3.9: Pagination Result Stability
- Section 7.4: LOW Risk Level Violations

**Dependency Task Outputs:**
- Task 2.6 Memory Log: `/Users/liujinliang/workspace/ai/sqltools/.apm/Memory/Phase_02_Validation_Engine/Task_2_6_Pagination_Detection_Infrastructure.md`
- Task 2.1 Memory Log: `/Users/liujinliang/workspace/ai/sqltools/.apm/Memory/Phase_02_Validation_Engine/Task_2_1_Rule_Checker_Framework_Interfaces.md`

**Project Structure:**
```
sql-guard-core/
├── src/main/java/com/footstone/sqlguard/
│   ├── validator/
│   │   ├── pagination/impl/
│   │   │   ├── LogicalPaginationChecker.java (Task 2.7)
│   │   │   ├── NoConditionPaginationChecker.java (Task 2.8)
│   │   │   ├── DeepPaginationChecker.java (Task 2.9)
│   │   │   ├── LargePageSizeChecker.java (Task 2.10)
│   │   │   └── MissingOrderByChecker.java (Task 2.11 - THIS TASK)
│   │   └── rule/impl/
│   │       └── MissingOrderByConfig.java (Task 2.11 - THIS TASK)
└── src/test/java/com/footstone/sqlguard/
    └── validator/pagination/impl/
        └── MissingOrderByCheckerTest.java (Task 2.11 - THIS TASK)
```

---

**Execution Agent:** Agent_Core_Engine_Validation
**Execution Mode:** TDD with comprehensive test coverage
**Expected Duration:** Single session completion
**Priority:** LOW (stability improvement checker)
