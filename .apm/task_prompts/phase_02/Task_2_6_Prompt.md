---
task_ref: "Task 2.6 - Pagination Detection Infrastructure"
agent_assignment: "Agent_Core_Engine_Validation"
memory_log_path: ".apm/Memory/Phase_02_Validation_Engine/Task_2_6_Pagination_Detection_Infrastructure.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Pagination Detection Infrastructure

## Task Reference
Implementation Plan: **Task 2.6 - Pagination Detection Infrastructure** assigned to **Agent_Core_Engine_Validation**

## Context from Dependencies
Based on Task 2.1 completed work and Phase 1:
- Use SqlContext from Task 1.2 to access RowBounds and IPage parameters
- Use JSqlParser Statement types to detect LIMIT clauses
- Build infrastructure for Tasks 2.7-2.12 pagination checkers
- Follow TDD with comprehensive pagination scenario coverage

## Objective
Build shared pagination detection infrastructure distinguishing between LOGICAL (dangerous in-memory pagination), PHYSICAL (SQL-level LIMIT pagination), and NONE types, with plugin detection for MyBatis PageHelper and MyBatis-Plus PaginationInnerInterceptor to enable accurate pagination abuse checking.

## Detailed Instructions
Complete this task in **4 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: PaginationType Enum TDD
**Test First:**
Write test class `PaginationDetectionTest` with test method:
- `testPaginationTypeEnum()` - verifying enum has 3 constants: LOGICAL, PHYSICAL, NONE

**Then Implement:**
Create `PaginationType` enum in `com.footstone.sqlguard.validator.pagination` package with:
- Constants: LOGICAL, PHYSICAL, NONE
- Comprehensive Javadoc explaining each type:
  - **LOGICAL:** RowBounds/IPage without pagination plugin, loads entire result set into memory then skips in-memory (OOM risk)
  - **PHYSICAL:** LIMIT clause in SQL or pagination plugin enabled, database performs row filtering (safe)
  - **NONE:** No pagination detected

### Step 2: Plugin Detection TDD
**Test First:**
Write test class `PaginationPluginDetectorTest` with test methods:
- `testNoPaginationPlugin_shouldReturnFalse()` - no plugins configured
- `testPageHelper_shouldReturnTrue()` - MyBatis interceptor list contains PageHelper
- `testMpPaginationInnerInterceptor_shouldReturnTrue()` - MybatisPlusInterceptor contains PaginationInnerInterceptor

**Then Implement:**
Create `PaginationPluginDetector` class in `com.footstone.sqlguard.validator.pagination` package with:

**Constructor:**
- Accepts optional `List<Interceptor> mybatisInterceptors` (nullable)
- Accepts optional `MybatisPlusInterceptor mybatisPlusInterceptor` (nullable)

**Method: `boolean hasPaginationPlugin()`**
- If mybatisPlusInterceptor not null:
  - Call `getInterceptors()` and check `stream().anyMatch(i -> i instanceof PaginationInnerInterceptor)`
- If mybatisInterceptors not null:
  - Check `stream().anyMatch(i -> i.getClass().getName().contains("PageInterceptor"))`
- Return true if either detection succeeds, false otherwise

**Note:** Use class name matching for PageHelper to avoid direct dependency on PageHelper library

### Step 3: Pagination Type Detection TDD
**Test First:**
Write test class `PaginationTypeDetectionTest` with comprehensive test methods:
- `testRowBoundsWithoutPlugin_shouldBeLogical()` - SqlContext with RowBounds, no plugin
- `testRowBoundsWithPageHelper_shouldBePhysical()` - RowBounds + plugin
- `testIPageWithMpPlugin_shouldBePhysical()` - IPage parameter + PaginationInnerInterceptor
- `testLimitInSql_shouldBePhysical()` - SQL contains LIMIT clause
- `testPlainQuery_shouldBeNone()` - no LIMIT, no RowBounds, no IPage

**Then Implement:**
Add method `PaginationType detectPaginationType(SqlContext context)` to PaginationPluginDetector:

**Detection Logic:**
1. Extract Statement from `context.getParsedSql()`
2. Check `hasLimit = (stmt instanceof Select && ((Select)stmt).getLimit() != null)`
3. Check `hasPageParam`:
   - `context.getRowBounds() != null && context.getRowBounds() != RowBounds.DEFAULT`
   - OR `hasIPageParameter(context.getParams())`
4. Call `hasPlugin = hasPaginationPlugin()`
5. Apply logic per design 3.3.5:
   - If `(hasPageParam && !hasLimit && !hasPlugin)` return LOGICAL
   - If `(hasLimit || (hasPageParam && hasPlugin))` return PHYSICAL
   - Else return NONE

**Helper Method:**
Implement `hasIPageParameter(Map<String, Object> params)`:
- Return false if params is null or empty
- Check if any param value `instanceof IPage` (MyBatis-Plus pagination parameter)

### Step 4: Comprehensive Scenario Testing
**Write integration test `PaginationScenarioIntegrationTest`** with all combinations:

**Test Scenarios:**
1. RowBounds(offset=0, limit=10) without plugin → expect LOGICAL
2. RowBounds with PageHelper interceptor → expect PHYSICAL
3. IPage parameter with MybatisPlusInterceptor + PaginationInnerInterceptor → expect PHYSICAL
4. SQL "SELECT * FROM user LIMIT 10" → expect PHYSICAL
5. SQL "SELECT * FROM user LIMIT 10 OFFSET 5" → expect PHYSICAL
6. Plain SQL "SELECT * FROM user WHERE id=?" with no pagination params → expect NONE
7. RowBounds.DEFAULT (infinite bounds) → expect NONE (not pagination)

**Verification:**
- All 7 scenarios detect correct PaginationType
- Detection accuracy 100% across all combinations
- Run `mvn test` ensuring all pagination detection tests pass
- Verify Google Java Style compliance

**Constraints:**
- This infrastructure is foundational for Tasks 2.7-2.12 (all pagination checkers)
- LOGICAL pagination (RowBounds without plugin) is most dangerous - loads entire result set into memory
- PHYSICAL pagination (LIMIT or plugin-assisted) performs database-level filtering
- Detection must handle both MyBatis PageHelper and MyBatis-Plus PaginationInnerInterceptor
- RowBounds.DEFAULT is infinite bounds, not pagination (should be NONE not LOGICAL)

## Expected Output
- **PaginationType enum:** With 3 constants (LOGICAL, PHYSICAL, NONE)
- **PaginationPluginDetector class:** With plugin detection and type detection methods
- **Comprehensive tests:** Covering all pagination scenarios
- **Success Criteria:** All tests pass, 100% detection accuracy, infrastructure ready for pagination checkers

**File Locations:**
- Enum/Classes: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/`
- Tests: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/test/java/com/footstone/sqlguard/validator/pagination/`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_02_Validation_Engine/Task_2_6_Pagination_Detection_Infrastructure.md`

Follow .apm/guides/Memory_Log_Guide.md instructions.
