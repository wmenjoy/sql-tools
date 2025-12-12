---
batch_id: Phase_02_Batch_03
phase: Phase_02_Validation_Engine
batch_type: Parallel Execution
task_count: 5
status: Ready for Assignment
dependencies_status: All dependencies completed
---

# Phase 2 Batch 3 - Pagination Checkers (Tasks 2.7-2.11)

## Batch Overview

**Purpose:** Implement all 5 pagination rule checkers leveraging the pagination detection infrastructure from Task 2.6.

**Execution Mode:** PARALLEL - All 5 tasks can execute concurrently
**Estimated Time:** 5 concurrent sessions vs. 15 sequential sessions (67% reduction)

## Batch Tasks

### Task 2.7 - Logical Pagination Checker ⚠️ CRITICAL

**Agent:** Agent_Core_Engine_Validation
**Risk Level:** CRITICAL
**Complexity:** Medium
**Priority:** HIGH

**Objective:** Detect RowBounds/IPage without pagination plugin (loads entire result set into memory).

**Key Deliverables:**
- LogicalPaginationChecker with CRITICAL violation for in-memory pagination
- LogicalPaginationConfig (extends CheckerConfig)
- 16+ comprehensive tests
- Violation details with offset/limit parameters

**Dependencies:**
- ✅ Task 2.6 (PaginationDetection Infrastructure)
- ✅ Task 2.1 (Rule Checker Framework)

**Prompt Location:** `.apm/task_prompts/phase_02/Task_2_7_Prompt.md`

---

### Task 2.8 - Physical Pagination No-Condition Check ⚠️ CRITICAL

**Agent:** Agent_Core_Engine_Validation
**Risk Level:** CRITICAL
**Complexity:** Medium
**Priority:** HIGH

**Objective:** Detect unconditioned LIMIT queries (e.g., "SELECT * FROM user LIMIT 100" - still full table scan).

**Key Deliverables:**
- NoConditionPaginationChecker with CRITICAL violation
- Early-return mechanism preventing lower-priority checker violations
- 14+ comprehensive tests including early-return verification

**Dependencies:**
- ✅ Task 2.6 (PaginationDetection Infrastructure)
- ✅ Task 2.1 (Rule Checker Framework)
- Runtime dependency: Must run BEFORE Tasks 2.9-2.11 (orchestrator handles)

**Prompt Location:** `.apm/task_prompts/phase_02/Task_2_8_Prompt.md`

**Special Note:** Sets early-return flag to prevent misleading violations from subsequent checkers.

---

### Task 2.9 - Physical Pagination Deep Offset Check 🟡 MEDIUM

**Agent:** Agent_Core_Engine_Validation
**Risk Level:** MEDIUM
**Complexity:** Medium
**Priority:** MEDIUM

**Objective:** Detect excessive OFFSET values (e.g., OFFSET 100000) causing performance degradation.

**Key Deliverables:**
- DeepPaginationChecker with MEDIUM violation for deep offset
- PaginationAbuseConfig with maxOffset threshold (default 10000)
- 16+ comprehensive tests including boundary conditions
- Support for both LIMIT syntaxes ("LIMIT n OFFSET m" and "LIMIT m,n")

**Dependencies:**
- ✅ Task 2.6 (PaginationDetection Infrastructure)
- Runtime dependency: Checks Task 2.8 early-return flag

**Prompt Location:** `.apm/task_prompts/phase_02/Task_2_9_Prompt.md`

---

### Task 2.10 - Physical Pagination Large PageSize Check 🟡 MEDIUM

**Agent:** Agent_Core_Engine_Validation
**Risk Level:** MEDIUM
**Complexity:** Low
**Priority:** MEDIUM

**Objective:** Detect excessive pageSize values (e.g., LIMIT 10000) returning massive datasets.

**Key Deliverables:**
- LargePageSizeChecker with MEDIUM violation for large pageSize
- Reuses PaginationAbuseConfig.maxPageSize from Task 2.9 (default 1000)
- 15+ comprehensive tests
- Support for both LIMIT syntaxes (correctly extract row count)

**Dependencies:**
- ✅ Task 2.6 (PaginationDetection Infrastructure)
- Task 2.9 (PaginationAbuseConfig - shared configuration)

**Prompt Location:** `.apm/task_prompts/phase_02/Task_2_10_Prompt.md`

**Special Note:** Independent from Task 2.9 - both can trigger on same SQL.

---

### Task 2.11 - Physical Pagination Missing ORDER BY Check 🔵 LOW

**Agent:** Agent_Core_Engine_Validation
**Risk Level:** LOW
**Complexity:** Low
**Priority:** LOW

**Objective:** Detect paginated queries without ORDER BY causing unstable result ordering.

**Key Deliverables:**
- MissingOrderByChecker with LOW violation for missing ORDER BY
- MissingOrderByConfig (extends CheckerConfig)
- 11+ comprehensive tests
- Simple presence check (ORDER BY exists or not)

**Dependencies:**
- ✅ Task 2.6 (PaginationDetection Infrastructure)

**Prompt Location:** `.apm/task_prompts/phase_02/Task_2_11_Prompt.md`

---

## Dependency Graph

```
Task 2.6 (PaginationDetection Infrastructure) ✅ COMPLETED
    ├── Task 2.7 (LogicalPaginationChecker) - PARALLEL
    ├── Task 2.8 (NoConditionPaginationChecker) - PARALLEL
    ├── Task 2.9 (DeepPaginationChecker) - PARALLEL
    ├── Task 2.10 (LargePageSizeChecker) - PARALLEL
    └── Task 2.11 (MissingOrderByChecker) - PARALLEL

Task 2.9 (PaginationAbuseConfig) ──> Task 2.10 (reuses config)
   (Note: Task 2.10 reuses config, no blocking dependency)
```

**All 5 tasks can execute in parallel** - no cross-dependencies except shared configuration which is co-created.

## Batch Statistics

**Total Test Coverage Target:** 72+ tests
- Task 2.7: 16+ tests
- Task 2.8: 14+ tests
- Task 2.9: 16+ tests
- Task 2.10: 15+ tests
- Task 2.11: 11+ tests

**Risk Level Distribution:**
- CRITICAL: 2 checkers (Tasks 2.7, 2.8)
- MEDIUM: 2 checkers (Tasks 2.9, 2.10)
- LOW: 1 checker (Task 2.11)

**Configuration Classes:**
- LogicalPaginationConfig (Task 2.7)
- PaginationAbuseConfig (Task 2.9, shared with Task 2.10)
- MissingOrderByConfig (Task 2.11)

## Key Implementation Patterns

### 1. Pagination Type Detection (All Tasks)
```java
PaginationType type = detector.detectPaginationType(context);
if (type != EXPECTED_TYPE) return; // Skip if wrong type
```

### 2. Early-Return Mechanism (Task 2.8 sets, Tasks 2.9-2.11 check)
```java
// Task 2.8 sets flag:
result.getDetails().put("earlyReturn", true);

// Tasks 2.9-2.11 check flag:
if (result.getDetails().get("earlyReturn") == Boolean.TRUE) return;
```

### 3. LIMIT Details Extraction (Tasks 2.8-2.10)
```java
SELECT select = (SELECT) context.getParsedSql();
Limit limit = select.getLimit();
if (limit != null) {
    long offset = limit.getOffset() != null ? limit.getOffset().getValue() : 0;
    long pageSize = limit.getRowCount() != null ? limit.getRowCount().getValue() : 0;
}
```

### 4. ORDER BY Detection (Task 2.11)
```java
SELECT select = (SELECT) context.getParsedSql();
List<OrderByElement> orderByElements = select.getOrderByElements();
if (orderByElements == null || orderByElements.isEmpty()) {
    // Add violation
}
```

## Testing Focus

### Unit Tests (Each Task)
- Threshold boundary tests (for Tasks 2.9-2.10)
- Pagination type filtering (LOGICAL vs PHYSICAL vs NONE)
- Configuration enabled/disabled toggle
- Violation message and suggestion verification

### Integration Tests
- Early-return mechanism (Task 2.8 with Tasks 2.9-2.11)
- Multiple violations on same SQL (Tasks 2.9 + 2.10 can both trigger)
- RuleCheckerOrchestrator integration

### Edge Cases
- NULL checks (RowBounds, Limit, OrderByElements)
- RowBounds.DEFAULT (not pagination)
- Both LIMIT syntaxes ("LIMIT n OFFSET m" and "LIMIT m,n")

## Parallel Execution Benefits

**Sequential Execution:** 5 tasks × 3 sessions each = 15 total sessions
**Parallel Execution:** max(3, 3, 3, 3, 3) = 3 sessions (all complete together)
**Time Reduction:** 67% (12 sessions saved)

**Why Parallel Works:**
- All tasks depend only on completed Task 2.6
- No cross-dependencies between Tasks 2.7-2.11
- Shared configuration (Task 2.9→2.10) is co-created, not blocking
- Early-return mechanism is runtime orchestration, not compile-time dependency

## Success Criteria

**Batch Complete When:**
1. ✅ All 5 checkers implemented with TDD
2. ✅ Total 72+ tests passing (16+14+16+15+11)
3. ✅ All checkers integrate with PaginationPluginDetector
4. ✅ Early-return mechanism working (Task 2.8 → Tasks 2.9-2.11)
5. ✅ All 3 configuration classes created
6. ✅ Google Java Style compliance verified
7. ✅ No regressions in existing 340 tests

**Integration Verification:**
- RuleCheckerOrchestrator executes all 5 checkers correctly
- Early-return prevents misleading violations
- Multiple violations can coexist (Tasks 2.9+2.10 on same SQL)
- Pagination type filtering works (LOGICAL vs PHYSICAL vs NONE)

## Next Steps After Batch 3

**Task 2.12 - NoPaginationChecker**
- Depends on Task 2.4 (BlacklistFieldChecker) + Task 2.6
- Variable risk stratification (CRITICAL/HIGH/MEDIUM)
- Whitelist exemptions for legitimate full-table queries

**Task 2.13 - DefaultSqlSafetyValidator Assembly**
- Final integration of all 11 checkers (Tasks 2.2-2.12)
- Deduplication filter implementation
- Performance benchmarking (<5% overhead target)

---

**Prepared By:** Manager Agent
**Batch Status:** Ready for parallel assignment to Agent_Core_Engine_Validation
**Expected Completion:** 3 concurrent sessions (vs 15 sequential)
